package net.evmodder.DropHeads.listeners;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.datatypes.EntitySetting;

//Only enabled if drop-for-indirect-kills:TRUE && drop-for-nonplayer-kills:FALSE
public class EntityDamageListener implements Listener{
	private final DropHeads pl;
	private final EntitySetting<Boolean> allowProjectileKills;

	public EntityDamageListener(EntitySetting<Boolean> allowProjectileKills){
		pl = DropHeads.getPlugin();
		this.allowProjectileKills = allowProjectileKills;
	}
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void entityDamageEvent(EntityDamageByEntityEvent evt){
		if(evt.getDamager() instanceof Player ||
				(allowProjectileKills.get(evt.getEntity()) && evt.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player)
		){
			pl.getLogger().warning("entity dmged by player: "+evt.getEntityType());
			evt.getEntity().setMetadata("PlayerDamage", new FixedMetadataValue(pl, System.currentTimeMillis()));
		}
	}
}