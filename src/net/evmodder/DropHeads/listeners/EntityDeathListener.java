package net.evmodder.DropHeads.listeners;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.bukkit.Material;
import org.bukkit.Nameable;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.EventExecutor;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.JunkUtils;
import net.evmodder.DropHeads.TextureKeyLookup;
import net.evmodder.DropHeads.datatypes.EntitySetting;
import net.evmodder.DropHeads.events.HeadRollEvent;
import net.evmodder.EvLib.EvUtils;
import net.evmodder.EvLib.extras.HeadUtils;
import net.evmodder.EvLib.extras.TextUtils;

public class EntityDeathListener implements Listener{
	private final DropHeads pl;
	private final DeathMessagePacketIntercepter deathMessageBlocker;
	private final Random rand;
	private final HashSet<UUID> explodingChargedCreepers;
	private final EventPriority PRIORITY;
	private final boolean USE_RANGED_WEAPON_FOR_LOOTING;
	private final EntitySetting<Boolean> allowNonPlayerKills, allowIndirectKills, allowProjectileKills;//, useRangedWeaponForLooting;
	//TODO: pkillonly config per-mob?
	private final boolean CHARGED_CREEPER_DROPS, VANILLA_WSKELE_HANDLING;
	private final long INDIRECT_KILL_THRESHOLD_MILLIS;
	private final boolean DEBUG_MODE;

	public static final class Friend{private Friend(){}}

	public EntityDeathListener(DeathMessagePacketIntercepter deathMessageBlocker){
		pl = DropHeads.getPlugin();
		this.deathMessageBlocker = deathMessageBlocker;
		rand = new Random();
		USE_RANGED_WEAPON_FOR_LOOTING = pl.getConfig().getBoolean("use-ranged-weapon-for-looting", true);
		allowNonPlayerKills = EntitySetting.fromConfig(pl, "drop-for-nonplayer-kills", false, null);
		allowIndirectKills = EntitySetting.fromConfig(pl, "drop-for-indirect-kills", false, null);
		allowProjectileKills = EntitySetting.fromConfig(pl, "drop-for-ranged-kills", false, null);

		CHARGED_CREEPER_DROPS = pl.getConfig().getBoolean("charged-creeper-drops", true);
		VANILLA_WSKELE_HANDLING = pl.getConfig().getBoolean("vanilla-wither-skeleton-skulls", false);
		PRIORITY = JunkUtils.parseEnumOrDefault(pl.getConfig().getString("death-listener-priority", "LOW"), EventPriority.LOW);
		INDIRECT_KILL_THRESHOLD_MILLIS = TextUtils.parseTimeInMillis(pl.getConfig().getString("indirect-kill-threshold", "30s"));
		DEBUG_MODE = pl.getConfig().getBoolean("debug-messages", true);

		final Map<EntityType, Double> mobChances = pl.getDropChanceAPI().getEntityDropChances(new Friend());
		final double DEFAULT_CHANCE = pl.getDropChanceAPI().getDefaultDropChance();

		final boolean entityHeads = DEFAULT_CHANCE > 0D || mobChances.entrySet().stream().anyMatch(  // All non-Player living entities
				entry -> entry.getKey().isAlive() && entry.getKey() != EntityType.PLAYER && entry.getValue() > 0D);
		if(entityHeads){
			pl.getServer().getPluginManager().registerEvent(EntityDeathEvent.class, this, PRIORITY, new DeathEventExecutor(), pl);
		}
		else if(mobChances.getOrDefault(EntityType.PLAYER, 0D) > 0D){
			pl.getServer().getPluginManager().registerEvent(PlayerDeathEvent.class, this, PRIORITY, new DeathEventExecutor(), pl);
		}
		final boolean nonLivingVehicleHeads = DEFAULT_CHANCE > 0D || mobChances.entrySet().stream().anyMatch(  // Boat, Minecart
				entry -> !entry.getKey().isAlive() && entry.getValue() > 0D &&
				entry.getKey().getEntityClass() != null && Vehicle.class.isAssignableFrom(entry.getKey().getEntityClass()));
		if(nonLivingVehicleHeads){
			pl.getServer().getPluginManager().registerEvent(VehicleDestroyEvent.class, this, PRIORITY, new DeathEventExecutor(), pl);
		}
		final boolean nonLivingHangingHeads = DEFAULT_CHANCE > 0D || mobChances.entrySet().stream().anyMatch(  // Painting, LeashHitch, ItemFrame
				entry -> !entry.getKey().isAlive() && entry.getValue() > 0D &&
				entry.getKey().getEntityClass() != null && Hanging.class.isAssignableFrom(entry.getKey().getEntityClass()));
		if(nonLivingHangingHeads){
			pl.getServer().getPluginManager().registerEvent(HangingBreakByEntityEvent.class, this, PRIORITY, new DeathEventExecutor(), pl);
		}
		explodingChargedCreepers = CHARGED_CREEPER_DROPS ? new HashSet<UUID>() : null;
	}

	private ItemStack getWeaponFromKiller(Entity killer){
		return
			killer == null ? null :
			killer instanceof LivingEntity le ? le.getEquipment().getItemInMainHand() :
			!USE_RANGED_WEAPON_FOR_LOOTING ? null :
			killer instanceof Projectile == false ? null :
			killer.hasMetadata("ShotUsing") ? (ItemStack)killer.getMetadata("ShotUsing").get(0).value() :
			((Projectile)killer).getShooter() instanceof LivingEntity le ? le.getEquipment().getItemInMainHand() :
			null;
	}

	private String getName(Permissible killer){
		return
			killer instanceof Nameable n && n.getCustomName() != null ? n.getCustomName() :
			killer instanceof CommandSender cs ? cs.getName() :
			killer.getClass().getSimpleName();
	}

	/**
	 * Determine whether a head should drop in the given conditions, and if so, drop it.
	 * @param victim The entity that was killed
	 * @param killer The entity that did the killing
	 * @param evt The parent EntityDeathEvent that was triggered
	 * @return True if a behead event occurs
	 */
	boolean onEntityDeath(@Nonnull final Entity victim, final Entity killer, final Event evt){
		Permissible killerPermCheck = killer;
		if(killer != null){
			if(allowProjectileKills.get(victim) && killer instanceof Projectile && ((Projectile)killer).getShooter() instanceof Permissible){
				killerPermCheck = (Permissible)((Projectile)killer).getShooter();
			}
			if(!killerPermCheck.hasPermission("dropheads.canbehead."+victim.getType().name().toLowerCase())){
				if(DEBUG_MODE) pl.getLogger().info("dropheads.canbehead.<type>=false: "+getName(killerPermCheck));
				return false;
			}
			if(killer instanceof Creeper && ((Creeper)killer).isPowered()){
				if(CHARGED_CREEPER_DROPS){
					if(!victim.hasPermission("dropheads.canlosehead")){
						if(DEBUG_MODE) pl.getLogger().info("dropheads.canlosehead=false: "+victim.getName());
						return false;
					}
					// Limit to 1 head per charged creeper explosion (mimics vanilla)
					final UUID creeperUUID = killer.getUniqueId();
					if(explodingChargedCreepers.add(creeperUUID)){
						if(DEBUG_MODE) pl.getLogger().info("Killed by charged creeper: "+victim.getType());
						// Free up memory after a tick (optional)
						pl.getServer().getScheduler().runTaskLater(pl, ()->explodingChargedCreepers.remove(creeperUUID), 1);
						// Do the head drop
						return pl.getDropChanceAPI().triggerHeadDropEvent(victim, killer, evt, /*weapon=*/null);
					}
				}
			}
			if(killerPermCheck.hasPermission("dropheads.alwaysbehead."+victim.getType().name().toLowerCase())){
				if(DEBUG_MODE) pl.getLogger().info("dropheads.alwaysbehead=true: "+getName(killerPermCheck));
				if(!victim.hasPermission("dropheads.canlosehead")){
					if(DEBUG_MODE) pl.getLogger().info("dropheads.canlosehead=false: "+victim.getName());
					return false;
				}
				return pl.getDropChanceAPI().triggerHeadDropEvent(victim, killer, evt, getWeaponFromKiller(killer));
			}
		}
		// Check if killer qualifies to trigger a behead.
		if((!allowIndirectKills.get(victim) && killer == null
				// Note: Won't use timeSinceLastEntityDamage()... it would be expensive to keep track of
				&& JunkUtils.timeSinceLastPlayerDamage(victim) > INDIRECT_KILL_THRESHOLD_MILLIS) ||
			(!allowProjectileKills.get(victim) && killer != null && killer instanceof Projectile) ||
			(!allowNonPlayerKills.get(victim) && (killer != null ? (
				killer instanceof Player == false &&
				(
					!allowProjectileKills.get(victim) ||
					killer instanceof Projectile == false ||
					((Projectile)killer).getShooter() instanceof Player == false
				)
			) : JunkUtils.timeSinceLastPlayerDamage(victim) > INDIRECT_KILL_THRESHOLD_MILLIS))
		) return false;

		final ItemStack murderWeapon = getWeaponFromKiller(killer);
		final Material murderWeaponType = murderWeapon == null ? Material.AIR : murderWeapon.getType();

		if(!pl.getDropChanceAPI().isWeaponAbleToBehead(victim, murderWeaponType)) return false;

		final int lootingLevel = JunkUtils.getLootingLevel(murderWeapon);
		final double lootingMod = lootingLevel == 0 ? 1D : Math.pow(pl.getDropChanceAPI().getLootingMult(victim), lootingLevel);
		final double lootingAdd = pl.getDropChanceAPI().getLootingAdd(victim)*lootingLevel;
		final double weaponMod = pl.getDropChanceAPI().getWeaponMult(victim, murderWeaponType);
		final double timeAliveMod = pl.getDropChanceAPI().getTimeAliveMult(victim);
		final double rawDropChance = pl.getDropChanceAPI().getRawDropChance(victim);
		final double permsMod = pl.getDropChanceAPI().getPermissionsMult(victim, killerPermCheck);
		final double spawnCauseMod = JunkUtils.getSpawnCauseMult(victim);
		final double dropChance = rawDropChance*lootingMod*weaponMod*timeAliveMod*permsMod*spawnCauseMod + lootingAdd;

		final double dropRoll = rand.nextDouble();
		final HeadRollEvent rollEvent = new HeadRollEvent(killer, victim, dropChance, dropRoll, dropRoll < dropChance);
		pl.getServer().getPluginManager().callEvent(rollEvent);
		if(DEBUG_MODE && dropRoll < dropChance && !rollEvent.getDropSuccess()) pl.getLogger().info("HeadRollEvent success was changed to false");
		if(rollEvent.getDropSuccess()){
			if(!victim.hasPermission("dropheads.canlosehead")){
				if(DEBUG_MODE) pl.getLogger().info("dropheads.canlosehead=false: "+victim.getName());
				return false;
			}
			if(pl.getDropChanceAPI().triggerHeadDropEvent(victim, killer, evt, murderWeapon)){
				if(DEBUG_MODE){
					DecimalFormat df = new DecimalFormat("0.0###");
					pl.getLogger().info("Dropping Head: "+TextureKeyLookup.getTextureKey(victim)
						+"\nKiller: "+(killer != null ? killer.getType() : "none")
						+", Weapon: "+murderWeaponType
						+"\nRaw chance: "+df.format(rawDropChance*100D)+"%\nMultipliers >> "+
						(spawnCauseMod != 1 ? "SpawnReason: "+df.format((spawnCauseMod-1D)*100D)+"%, " : "") +
						(timeAliveMod != 1 ? "TimeAlive: "+df.format((timeAliveMod-1D)*100D)+"%, " : "") +
						(weaponMod != 1 ? "Weapon: "+df.format((weaponMod-1D)*100D)+"%, " : "") +
						(lootingMod != 1 ? "Looting: "+df.format((lootingMod-1D)*100D)+"%, " : "") +
						(lootingAdd != 0 ? "Looting (Addition): "+df.format(lootingAdd*100D)+"%, " : "") +
						"\nFinal drop chance: "+df.format(dropChance*100D)+"%");
				}
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks for wither skeletons dropping skulls they were wearing in the helmet slot,
	 * handles the case where killed by a charged creeper, 
	 * @param victim The WitherSkeleton that was killed
	 * @param killer The entity that did the killing
	 * @param evt The parent EntityDeathEvent that was triggered
	 * @return True if no further handling is necessary, false if we should still call onEntityDeath()
	 */
	boolean handleWitherSkeltonDeathEvent(Entity victim, Entity killer, EntityDeathEvent evt){
		int newSkullsDropped = 0;
		Iterator<ItemStack> it = evt.getDrops().iterator();
		ArrayList<ItemStack> removedSkulls = new ArrayList<>();//TODO: remove this hacky fix once Bukkit/Spigot gets their shit sorted
		// Remove vanilla-dropped wither skeleton skulls so they aren't dropped twice.
		while(it.hasNext()){
			ItemStack next = it.next();
			if(next.getType() == Material.WITHER_SKELETON_SKULL){
				it.remove();
				++newSkullsDropped;
				if(!next.equals(new ItemStack(Material.WITHER_SKELETON_SKULL))) removedSkulls.add(next);
			}
		}
		// However, if it is wearing a head in an armor slot, don't remove the drop.
		for(ItemStack i : EvUtils.getEquipmentGuaranteedToDrop(evt.getEntity())){
			if(i != null && i.getType() == Material.WITHER_SKELETON_SKULL){evt.getDrops().add(i); --newSkullsDropped;}
			//TODO: remove this hacky fix below once Bukkit/Spigot gets their shit sorted
			if(i != null && i.getType() == Material.AIR && newSkullsDropped > 1){
				evt.getDrops().add(removedSkulls.isEmpty()
						? new ItemStack(Material.WITHER_SKELETON_SKULL)
						: removedSkulls.remove(removedSkulls.size()-1));
				--newSkullsDropped;
			}
		}
		if(newSkullsDropped > 1 && DEBUG_MODE) pl.getLogger().warning("Multiple non-DropHeads wither skull drops detected!");
		if(VANILLA_WSKELE_HANDLING || pl.getDropChanceAPI().getRawDropChance(victim) == 0.025D){
			// newSkullsDropped should always be 0 or 1 by this point
			if((newSkullsDropped == 1 || (killer != null && killer.hasPermission("dropheads.alwaysbehead.wither_skeleton")))
					&& victim.hasPermission("dropheads.canlosehead") && (killer == null || killer.hasPermission("dropheads.canbehead.wither_skeleton"))){
				// Don't drop the skull if another skull drop has already been caused by the same charged creeper.
				if(killer != null && killer instanceof Creeper && ((Creeper)killer).isPowered() && CHARGED_CREEPER_DROPS &&
					!explodingChargedCreepers.add(killer.getUniqueId()))
				{
					return true;
				}
				for(int i=0; i<newSkullsDropped; ++i) pl.getDropChanceAPI().triggerHeadDropEvent(victim, killer, evt, getWeaponFromKiller(killer));
			}
			return true;
		}
		return false;
	}

	class DeathEventExecutor implements EventExecutor{
		@Override public void execute(Listener listener, Event originalEvent){
			if(originalEvent instanceof EntityDeathEvent){
				final EntityDeathEvent evt = (EntityDeathEvent) originalEvent;
				final LivingEntity victim = evt.getEntity();
				final Entity killer = victim.getLastDamageCause() != null && victim.getLastDamageCause() instanceof EntityDamageByEntityEvent
						? ((EntityDamageByEntityEvent)victim.getLastDamageCause()).getDamager()
						: null;

				if(victim.getType() == EntityType.WITHER_SKELETON && handleWitherSkeltonDeathEvent(victim, killer, evt)){
					return;
				}
				// Remove vanilla-dropped heads from charged creeper kills
				if(CHARGED_CREEPER_DROPS && HeadUtils.dropsHeadFromChargedCreeper(victim.getType())
						&& killer != null && killer instanceof Creeper && ((Creeper)killer).isPowered()){
					Iterator<ItemStack> it = evt.getDrops().iterator();
					while(it.hasNext()){
						Material headType = it.next().getType();
						try{if(HeadUtils.getEntityFromHead(headType) == victim.getType()){it.remove(); break;}}
						catch(IllegalArgumentException ex){}
					}
				}
				// Unblock death messages when behead does not occur
				if(!onEntityDeath(victim, killer, evt) && deathMessageBlocker != null) deathMessageBlocker.unblockDeathMessage(victim);
			}
			else if(originalEvent instanceof VehicleDestroyEvent){
				final VehicleDestroyEvent evt = (VehicleDestroyEvent) originalEvent;
				onEntityDeath(/*victim=*/evt.getVehicle(), /*killer=*/evt.getAttacker(), evt);
			}
			else if(originalEvent instanceof HangingBreakByEntityEvent){
				final HangingBreakByEntityEvent evt = (HangingBreakByEntityEvent) originalEvent;
				onEntityDeath(/*victim=*/evt.getEntity(), /*killer=*/evt.getRemover(), evt);
			}
		}
	}
}