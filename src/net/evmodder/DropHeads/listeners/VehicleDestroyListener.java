package net.evmodder.DropHeads.listeners;

import java.text.DecimalFormat;
import java.util.UUID;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.TextureKeyLookup;
import net.evmodder.EvLib.extras.HeadUtils;

public class VehicleDestroyListener implements Listener{
	final DropHeads pl;
	final EntityDeathListener deathListener;
	final boolean DEBUG_MODE;

	public VehicleDestroyListener(EntityDeathListener mainListener){
		deathListener = mainListener;
		pl = mainListener.pl;
		DEBUG_MODE = mainListener.DEBUG_MODE;
	}

	@EventHandler
	public void onVehicleDestroy(VehicleDestroyEvent evt){
		final Entity vehicle = evt.getVehicle();
		Entity attacker = evt.getAttacker();
		if(!attacker.hasPermission("dropheads.canbehead")) return;
		if(attacker instanceof Creeper && ((Creeper)attacker).isPowered()){
			if(deathListener.CHARGED_CREEPER_DROPS && !HeadUtils.dropsHeadFromChargedCreeper(vehicle.getType()) 
					&& deathListener.explodingChargedCreepers.add(attacker.getUniqueId())){
				if(DEBUG_MODE) pl.getLogger().info("Killed by charged creeper: "+vehicle.getType());
				vehicle.getWorld().dropItemNaturally(vehicle.getLocation(), pl.getAPI().getHead(vehicle));
				// Cleanup memory after a tick (optional)
				final UUID creeperUUID = attacker.getUniqueId();
				new BukkitRunnable(){@Override public void run(){
					deathListener.explodingChargedCreepers.remove(creeperUUID);
				}}.runTaskLater(pl, 1);
			}
			return;
		}
		if(attacker.hasPermission("dropheads.alwaysbehead")){
			if(DEBUG_MODE) pl.getLogger().info("dropheads.alwaysbehead perm: "+attacker.getCustomName());
			vehicle.getWorld().dropItemNaturally(vehicle.getLocation(), pl.getAPI().getHead(vehicle));
			return;
		}
		// Check if killer is not a player.
		if(!deathListener.ALLOW_NON_PLAYER_KILLS && attacker instanceof Player == false &&
			(
				!deathListener.ALLOW_PROJECTILE_KILLS ||
				attacker instanceof Projectile == false ||
				((Projectile)attacker).getShooter() instanceof Player == false
			) && (
				!deathListener.ALLOW_INDIRECT_KILLS ||
				EntityDeathListener.timeSinceLastPlayerDamage(vehicle) > 60*1000
			)
		) return;

		final ItemStack murderWeapon = 
			attacker != null ?
				attacker instanceof LivingEntity ? ((LivingEntity)attacker).getEquipment().getItemInMainHand() :
				attacker instanceof Projectile && attacker.hasMetadata("ShotUsing") ? (ItemStack)attacker.getMetadata("ShotUsing").get(0).value() :
				null
			: null;

		if(!deathListener.mustUseTools.isEmpty() &&
				(murderWeapon == null || !deathListener.mustUseTools.contains(murderWeapon.getType()))) return;
		final int lootingLevel = murderWeapon == null ? 0 : murderWeapon.getEnchantmentLevel(Enchantment.LOOT_BONUS_MOBS);
		final double toolBonus = murderWeapon == null ? 0D : deathListener.toolBonuses.getOrDefault(murderWeapon.getType(), 0D);
		final double lootingMod = Math.min(Math.pow(deathListener.LOOTING_MULT, lootingLevel), deathListener.LOOTING_MULT*lootingLevel);
		final double lootingAdd = deathListener.LOOTING_ADD*lootingLevel;
		final double weaponMod = 1D + toolBonus;
		final double timeAliveMod = 1D + deathListener.getTimeAliveBonus(vehicle);
		final double spawnCauseMod = EntityDeathListener.getSpawnCauseModifier(vehicle);
		final double rawDropChance = deathListener.mobChances.getOrDefault(vehicle.getType(), deathListener.DEFAULT_CHANCE);
		double dropChance = rawDropChance*spawnCauseMod*timeAliveMod*weaponMod*lootingMod + lootingAdd;

		if(deathListener.rand.nextDouble() < dropChance){
			deathListener.dropHead(vehicle, /*EntityDeathEvent=*/null, attacker, murderWeapon);
			if(DEBUG_MODE){
				DecimalFormat df = new DecimalFormat("0.0###");
				pl.getLogger().info("Dropped Head: "+TextureKeyLookup.getTextureKey(vehicle)+"\n"
					+"Raw chance: "+df.format(rawDropChance*100D)+"%\nMultipliers"+
					(spawnCauseMod != 1 ? "SpawnReason: "+df.format((spawnCauseMod-1D)*100D)+"%, " : "") +
					(timeAliveMod != 1 ? "TimeAlive: "+df.format((timeAliveMod-1D)*100D)+"%, " : "") +
					(weaponMod != 1 ? "Weapon: "+df.format((weaponMod-1D)*100D)+"%, " : "") +
					(lootingMod != 1 ? "Looting: "+df.format((lootingMod-1D)*100D)+"%, " : "") +
					(lootingAdd != 0 ? "Looting (Addition): "+df.format(lootingAdd*100D)+"%, " : "") +
					"\nFinal drop chance: "+df.format(dropChance*100D)+"%");
			}
		}
	}
}