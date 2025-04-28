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
	private final EntitySetting<Boolean> allowNonPlayerKills, allowIndirectPlayerKills, allowProjectileKills;

	public EntityDamageListener(
			EntitySetting<Boolean> allowNonPlayerKills, EntitySetting<Boolean> allowIndirectPlayerKills, EntitySetting<Boolean> allowProjectileKills){
		this.allowNonPlayerKills = allowNonPlayerKills;
		this.allowIndirectPlayerKills = allowIndirectPlayerKills;
		this.allowProjectileKills = allowProjectileKills;
		pl = DropHeads.getPlugin();
	}
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void entityDamageEvent(EntityDamageByEntityEvent evt){
		if(allowNonPlayerKills.get(evt.getEntity()) || !allowIndirectPlayerKills.get(evt.getEntity())) return;
		if(evt.getDamager() instanceof Player == false &&
				!(allowProjectileKills.get(evt.getEntity()) && evt.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player)) return;

		pl.getLogger().warning("entity dmged by player: "+evt.getEntityType());
		evt.getEntity().setMetadata("PlayerDamage", new FixedMetadataValue(pl, System.currentTimeMillis()));
	}
}