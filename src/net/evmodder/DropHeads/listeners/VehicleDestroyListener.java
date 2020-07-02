package net.evmodder.DropHeads.listeners;

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

		final ItemStack itemInHand = (attacker instanceof LivingEntity
				? ((LivingEntity)attacker).getEquipment().getItemInMainHand() : null);
		if(!deathListener.mustUseTools.isEmpty() &&
				(itemInHand == null || !deathListener.mustUseTools.contains(itemInHand.getType()))) return;
		final int lootingLevel = itemInHand == null ? 0 : itemInHand.getEnchantmentLevel(Enchantment.LOOT_BONUS_MOBS);
		final double toolBonus = itemInHand == null ? 0D : deathListener.toolBonuses.getOrDefault(itemInHand.getType(), 0D);
		final double lootingMod = 1D + lootingLevel*0.01D;
		final double weaponMod = 1D + toolBonus;
		final double spawnCauseMod = EntityDeathListener.getSpawnCauseModifier(vehicle);
		final double rawDropChance = deathListener.mobChances.getOrDefault(vehicle.getType(), deathListener.DEFAULT_CHANCE);
		final double dropChance = rawDropChance*spawnCauseMod*lootingMod*weaponMod;

		if(deathListener.rand.nextDouble() < dropChance){
			vehicle.getWorld().dropItemNaturally(vehicle.getLocation(), pl.getAPI().getHead(vehicle));
			if(DEBUG_MODE) pl.getLogger().info("Dropped Head: "+vehicle.getType()+"\n"
					+"Raw chance: "+rawDropChance*100D+"%, "
					+"SpawnReason Bonus: "+(spawnCauseMod-1D)*100D+"%, "
					+"Looting Bonus: "+(lootingMod-1D)*100D+"%, "
					+"Weapon Bonus: "+(weaponMod-1D)*100D+"%, "
					+"Final drop chance: "+dropChance*100D+"%");
		}
	}
}