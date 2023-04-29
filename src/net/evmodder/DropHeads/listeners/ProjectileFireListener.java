package net.evmodder.DropHeads.listeners;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import net.evmodder.DropHeads.DropHeads;

public class ProjectileFireListener implements Listener{
	private final DropHeads pl;
	private final boolean ALLOW_NON_PLAYER_KILLS;
	
	public ProjectileFireListener(){
		pl = DropHeads.getPlugin();
		ALLOW_NON_PLAYER_KILLS = pl.getConfig().getBoolean("drop-for-nonplayer-kills", false);
	}

	private boolean canShootProjectiles(Material type){
		switch(type.name()){
			// TODO: This list is incomplete, but should include all projectiles that trigger an EntityDamageEvent
			case "BOW":
			case "CROSS_BOW":
			case "TRIDENT":
			case "FIRE_CHARGE":
			case "SNOWBALL":
			case "SPLASH_POTION":
			case "LINGERING_POTION":
			case "FISHING_ROD":
			case "EGG":
				return true;
			default:
				return false;
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onProjectileLaunch(ProjectileLaunchEvent evt){
		// Skip if already has metadata (defers to EntityShootBowEvent)
		if(evt.getEntity().hasMetadata("ShotUsing") || evt.getEntity().getShooter() instanceof LivingEntity == false
				|| (!ALLOW_NON_PLAYER_KILLS && evt.getEntity().getShooter() instanceof Player == false)) return;

		final LivingEntity shooter = (LivingEntity)evt.getEntity().getShooter();
		final ItemStack mainHandItem = shooter.getEquipment().getItemInMainHand();
		final ItemStack offHandItem  = shooter.getEquipment().getItemInOffHand();
		if((mainHandItem == null || !canShootProjectiles(mainHandItem.getType())) && offHandItem != null && canShootProjectiles(offHandItem.getType())){
			evt.getEntity().setMetadata("ShotUsing", new FixedMetadataValue(pl, offHandItem));
		}
		else if(mainHandItem != null){
			evt.getEntity().setMetadata("ShotUsing", new FixedMetadataValue(pl, mainHandItem));
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEntityShootBow(EntityShootBowEvent evt){
		if(!ALLOW_NON_PLAYER_KILLS && evt.getEntity() instanceof Player == false) return;
		// This event is more reliable than ProjectileLaunchEvent, so override any existing metadata
		evt.getProjectile().removeMetadata("ShotUsing", pl);
		evt.getProjectile().setMetadata("ShotUsing", new FixedMetadataValue(pl, evt.getBow()));
	}
}