package net.evmodder.DropHeads.listeners;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;
import net.evmodder.DropHeads.DropHeads;

public class EntityDamageListener implements Listener{
	final DropHeads pl;
	final boolean allowProjectileKills;

	// Only enabled if drop-for-indirect-kills is TRUE
	public EntityDamageListener(){
		pl = DropHeads.getPlugin();
		allowProjectileKills = pl.getConfig().getBoolean("drop-for-ranged-kills", false);
	}
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void entityDamageEvent(EntityDamageByEntityEvent evt){
		if(evt.getDamager() instanceof Player || (allowProjectileKills && evt.getDamager() instanceof Projectile
				&& ((Projectile)evt.getDamager()).getShooter() instanceof Player)){
			evt.getEntity().setMetadata("PlayerDamage", new FixedMetadataValue(pl, System.currentTimeMillis()));
		}
	}
}