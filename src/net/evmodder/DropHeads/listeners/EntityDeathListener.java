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
import org.bukkit.enchantments.Enchantment;
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
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.scheduler.BukkitRunnable;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.JunkUtils;
import net.evmodder.DropHeads.TextureKeyLookup;
import net.evmodder.DropHeads.events.HeadRollEvent;
import net.evmodder.EvLib.EvUtils;
import net.evmodder.EvLib.extras.HeadUtils;

public class EntityDeathListener implements Listener{
	private final DropHeads pl;
	private final DeathMessagePacketIntercepter deathMessageBlocker;
	private final Random rand;
	private final HashSet<UUID> explodingChargedCreepers;
	private final EventPriority PRIORITY;
	private final boolean ALLOW_NON_PLAYER_KILLS, ALLOW_INDIRECT_KILLS, ALLOW_PROJECTILE_KILLS;
	private final boolean PLAYER_HEADS_ONLY, CHARGED_CREEPER_DROPS, VANILLA_WSKELE_HANDLING;
	private final boolean DEBUG_MODE;
	private final long INDIRECT_KILL_THRESHOLD_MILLIS = 30*1000;//TODO: move to config

	public EntityDeathListener(DeathMessagePacketIntercepter deathMessageBlocker){
		pl = DropHeads.getPlugin();
		this.deathMessageBlocker = deathMessageBlocker;
		rand = new Random();
		ALLOW_NON_PLAYER_KILLS = pl.getConfig().getBoolean("drop-for-nonplayer-kills", !pl.getConfig().getBoolean("player-kills-only", true));
		ALLOW_INDIRECT_KILLS = pl.getConfig().getBoolean("drop-for-indirect-kills", false);
		ALLOW_PROJECTILE_KILLS = pl.getConfig().getBoolean("drop-for-ranged-kills", false);
		PLAYER_HEADS_ONLY = pl.getConfig().getBoolean("player-heads-only", false);
		CHARGED_CREEPER_DROPS = pl.getConfig().getBoolean("charged-creeper-drops", true);
		VANILLA_WSKELE_HANDLING = pl.getConfig().getBoolean("vanilla-wither-skeleton-skulls", false);
		PRIORITY = JunkUtils.parseEnumOrDefault(pl.getConfig().getString("death-listener-priority", "LOW"), EventPriority.LOW);
		DEBUG_MODE = pl.getConfig().getBoolean("debug-messages", true);

		if(PLAYER_HEADS_ONLY){
			pl.getServer().getPluginManager().registerEvent(PlayerDeathEvent.class, this, PRIORITY, new DeathEventExecutor(), pl);
		}
		else{
			final Map<EntityType, Double> mobChances = pl.getDropChanceAPI().mobChances;
			final double DEFAULT_CHANCE = pl.getDropChanceAPI().getDefaultDropChance();

			final boolean entityHeads = DEFAULT_CHANCE > 0D || mobChances.entrySet().stream().anyMatch(  // All non-Player living entities
					entry -> entry.getKey().isAlive() && entry.getKey() != EntityType.PLAYER && entry.getValue() > 0D);
			if(entityHeads){
				pl.getServer().getPluginManager().registerEvent(EntityDeathEvent.class, this, PRIORITY, new DeathEventExecutor(), pl);
			}
			else if(mobChances.getOrDefault(EntityType.PLAYER, 0D) > 0D){
				pl.getServer().getPluginManager().registerEvent(PlayerDeathEvent.class, this, PRIORITY, new DeathEventExecutor(), pl);
			}
			boolean nonLivingVehicleHeads = DEFAULT_CHANCE > 0D || mobChances.entrySet().stream().anyMatch(  // Boat, Minecart
					entry -> !entry.getKey().isAlive() && entry.getValue() > 0D &&
					entry.getKey().getEntityClass() != null && Vehicle.class.isAssignableFrom(entry.getKey().getEntityClass()));
			if(nonLivingVehicleHeads){
				pl.getServer().getPluginManager().registerEvent(VehicleDestroyEvent.class, this, PRIORITY, new DeathEventExecutor(), pl);
			}
			boolean nonLivingHangingHeads = DEFAULT_CHANCE > 0D || mobChances.entrySet().stream().anyMatch(  // Painting, LeashHitch, ItemFrame
					entry -> !entry.getKey().isAlive() && entry.getValue() > 0D &&
					entry.getKey().getEntityClass() != null && Hanging.class.isAssignableFrom(entry.getKey().getEntityClass()));
			if(nonLivingHangingHeads){
				pl.getServer().getPluginManager().registerEvent(HangingBreakByEntityEvent.class, this, PRIORITY, new DeathEventExecutor(), pl);
			}
		}  // if(!PLAYER_HEADS_ONLY)
		explodingChargedCreepers = new HashSet<UUID>();

		// Dynamically add all the children perms of "dropheads.alywaysbehead.<entity>"
		Permission alwaysBeheadPerm = pl.getServer().getPluginManager().getPermission("dropheads.alwaysbehead");
		if(alwaysBeheadPerm != null) try{
			for(EntityType entity : EntityType.values()){
				Permission alwaysBeheadPermForEntity = new Permission(
						alwaysBeheadPerm.getName()+"."+entity.name().toLowerCase(),
						"This entity will get a head 100% of the time when killing a "+entity.name().toLowerCase(),
						PermissionDefault.FALSE);
				alwaysBeheadPermForEntity.addParent(alwaysBeheadPerm, true);
				pl.getServer().getPluginManager().addPermission(alwaysBeheadPermForEntity);
			}
			alwaysBeheadPerm.recalculatePermissibles();
		}
		catch(IllegalArgumentException ex){/*The permissions are already defined; perhaps this is just a plugin or server reload*/}
	}

	ItemStack getWeaponFromKiller(Entity killer){
		return killer != null ?
					killer instanceof LivingEntity ? ((LivingEntity)killer).getEquipment().getItemInMainHand() :
					killer instanceof Projectile && killer.hasMetadata("ShotUsing") ? (ItemStack)killer.getMetadata("ShotUsing").get(0).value() :
					null
				: null;
	}

	// Returns true if behead occurred
	boolean onEntityDeath(@Nonnull final Entity victim, final Entity killer, final Event evt){
		if(killer != null){
			if(!killer.hasPermission("dropheads.canbehead")){
				if(DEBUG_MODE) pl.getLogger().info("dropheads.canbehead=false: "+killer.getName());
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
						new BukkitRunnable(){@Override public void run(){explodingChargedCreepers.remove(creeperUUID);}}.runTaskLater(pl, 1);
						// Do the head drop
						return pl.getDropChanceAPI().triggerHeadDropEvent(victim, killer, evt, /*weapon=*/null);
					}
				}
			}
			if(killer.hasPermission("dropheads.alwaysbehead."+victim.getType().name().toLowerCase())){
				if(DEBUG_MODE) pl.getLogger().info("dropheads.alwaysbehead=true: "+killer.getName());
				if(!victim.hasPermission("dropheads.canlosehead")){
					if(DEBUG_MODE) pl.getLogger().info("dropheads.canlosehead=false: "+victim.getName());
					return false;
				}
				return pl.getDropChanceAPI().triggerHeadDropEvent(victim, killer, evt,
						killer instanceof LivingEntity ? ((LivingEntity)killer).getEquipment().getItemInMainHand() : null);
			}
		}
		// Check if killer qualifies to trigger a behead.
		if((!ALLOW_INDIRECT_KILLS && killer == null
				// Note: Won't use timeSinceLastEntityDamage()... it would be expensive to keep track of
				&& JunkUtils.timeSinceLastPlayerDamage(victim) > INDIRECT_KILL_THRESHOLD_MILLIS) ||
			(!ALLOW_PROJECTILE_KILLS && killer != null && killer instanceof Projectile) ||
			(!ALLOW_NON_PLAYER_KILLS && (killer != null ? (
				killer instanceof Player == false &&
				(
					!ALLOW_PROJECTILE_KILLS ||
					killer instanceof Projectile == false ||
					((Projectile)killer).getShooter() instanceof Player == false
				)
			) : JunkUtils.timeSinceLastPlayerDamage(victim) > INDIRECT_KILL_THRESHOLD_MILLIS))
		) return false;

		final ItemStack murderWeapon = getWeaponFromKiller(killer);

		if(!pl.getDropChanceAPI().mustUseTools.isEmpty() &&
				(murderWeapon == null || !pl.getDropChanceAPI().mustUseTools.contains(murderWeapon.getType()))) return false;

		final double weaponBonus = murderWeapon == null ? 0D : pl.getDropChanceAPI().weaponBonuses.getOrDefault(murderWeapon.getType(), 0D);
		final int lootingLevel = murderWeapon == null ? 0 : murderWeapon.getEnchantmentLevel(Enchantment.LOOT_BONUS_MOBS);
		final double lootingMod = lootingLevel == 0 ? 1D : Math.pow(pl.getDropChanceAPI().LOOTING_MULT, lootingLevel);
		final double lootingAdd = pl.getDropChanceAPI().LOOTING_ADD*lootingLevel;
		final double weaponMod = 1D + weaponBonus;
		final double timeAliveMod = 1D + pl.getDropChanceAPI().getTimeAliveBonus(victim);
		final double spawnCauseMod = JunkUtils.getSpawnCauseModifier(victim);
		final double rawDropChance = pl.getDropChanceAPI().getRawDropChance(victim);
		final double permMod = pl.getDropChanceAPI().getPermsBasedDropRateModifier(killer);
		final double dropChance = rawDropChance*spawnCauseMod*timeAliveMod*weaponMod*lootingMod*permMod + lootingAdd;

		final double dropRoll = rand.nextDouble();
		HeadRollEvent rollEvent = new HeadRollEvent(killer, victim, dropChance, dropRoll, dropRoll < dropChance);
		pl.getServer().getPluginManager().callEvent(rollEvent);
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
						+", Weapon: "+(murderWeapon != null ? murderWeapon.getType() : "none")
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
				//TODO: remove this hacky fix once Bukkit/Spigot gets their shit sorted
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
		if(VANILLA_WSKELE_HANDLING || pl.getDropChanceAPI().mobChances.getOrDefault(EntityType.WITHER_SKELETON, 0.025D) == 0.025D){
			// newSkullsDropped should always be 0 or 1 by this point
			if((newSkullsDropped == 1 || (killer != null && killer.hasPermission("dropheads.alwaysbehead.wither_skeleton")))
					&& victim.hasPermission("dropheads.canlosehead") && (killer == null || killer.hasPermission("dropheads.canbehead"))){
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
				if(!onEntityDeath(victim, killer, evt)) deathMessageBlocker.unblockDeathMessage(victim);
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