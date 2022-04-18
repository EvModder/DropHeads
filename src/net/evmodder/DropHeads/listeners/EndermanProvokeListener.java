package net.evmodder.DropHeads.listeners;

import java.util.HashSet;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent.TargetReason;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import com.mojang.authlib.GameProfile;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.EvLib.extras.HeadUtils;

public class EndermanProvokeListener implements Listener{
	final private DropHeads pl;
	final private HashSet<EntityType> camouflageHeads;
	final boolean ALL_HEADS_ARE_CAMOFLAGE;
	
	public EndermanProvokeListener(){
		pl = DropHeads.getPlugin();
		camouflageHeads = new HashSet<EntityType>();
		pl.getConfig().getStringList("endermen-camouflage-heads").forEach(eName -> {
			try{
				camouflageHeads.add(EntityType.valueOf(eName.toUpperCase().replace("ALL", "UNKNOWN").replace("DEFAULT", "UNKNOWN")
						.replace("ENDERMEN", "ENDERMAN")));
			}
			catch(IllegalArgumentException ex){pl.getLogger().severe("Unknown entity type in 'endermen-camouflage-heads': "+eName);}
		});
		ALL_HEADS_ARE_CAMOFLAGE = camouflageHeads.contains(EntityType.UNKNOWN);
	}

	EntityType getEntityTypeFromHead(ItemStack head){
		if(head == null || !HeadUtils.isHead(head.getType())) return null;
		if(!HeadUtils.isPlayerHead(head.getType())) return HeadUtils.getEntityFromHead(head.getType());
		GameProfile profile = HeadUtils.getGameProfile((SkullMeta)head.getItemMeta());
		if(profile != null && profile.getName() != null){
			String profileName = profile.getName().startsWith(pl.getAPI().getDropHeadsNamespacedKey()) ? profile.getName().substring(10) : profile.getName();
			if(pl.getAPI().textureExists(profileName)){
				int idx = profileName.indexOf('|');
				String eTypeName = (idx == -1 ? profileName : profileName.substring(0, idx)).toUpperCase();
				try{return EntityType.valueOf(eTypeName);} catch(IllegalArgumentException ex){}
			}
		}
		return null;
	}

	@EventHandler
	public void entityTargetEntityEvent(EntityTargetLivingEntityEvent evt){
		if(evt.getEntityType() == EntityType.ENDERMAN && evt.getTarget() != null && evt.getTarget().getType() == EntityType.PLAYER
			&& evt.getReason() == TargetReason.CLOSEST_PLAYER
			&& (evt.getEntity().getLastDamageCause() == null
				|| !(evt.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent
				|| !((EntityDamageByEntityEvent)evt.getEntity().getLastDamageCause()).getDamager().getUniqueId().equals(evt.getTarget().getUniqueId()))
			) && (ALL_HEADS_ARE_CAMOFLAGE || camouflageHeads.contains(getEntityTypeFromHead(evt.getTarget().getEquipment().getHelmet())))
		){
			evt.setCancelled(true);
		}
	}
}