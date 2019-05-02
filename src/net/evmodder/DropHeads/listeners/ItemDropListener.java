package net.evmodder.DropHeads.listeners;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import com.mojang.authlib.GameProfile;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.HeadUtils;
import net.evmodder.EvLib2.EvUtils;

public class ItemDropListener implements Listener{
	final DropHeads pl;
	public ItemDropListener(){pl = DropHeads.getPlugin();}

	@SuppressWarnings("deprecation") @EventHandler
	public void onBarf(ItemSpawnEvent evt){
		if(EvUtils.isPlayerHead(evt.getEntity().getItemStack().getType())){
			ItemStack skullItem = evt.getEntity().getItemStack();
			if(skullItem.hasItemMeta() && skullItem.getItemMeta() instanceof SkullMeta){
				SkullMeta meta = (SkullMeta) skullItem.getItemMeta();

				GameProfile profile = HeadUtils.getGameProfile(meta);
				if(profile != null){
					int idx = profile.getName().indexOf("|");
					if(idx != -1){
						//Refresh entity head texture
						EntityType type = EntityType.valueOf(profile.getName().substring(0, idx));
						evt.getEntity().setItemStack(pl.getAPI().makeTextureSkull(type, profile.getName().substring(idx+1)));
					}
					else{//This is a player head
						OfflinePlayer p;
						if(profile.getId() != null && (p = pl.getServer().getOfflinePlayer(profile.getId())) != null
								&& p.getName() != null && !p.getName().equals(profile.getName())){
							//If they have changed their name, create a new skullItem with an updated GameProfile
							evt.getEntity().setItemStack(HeadUtils.getPlayerHead(p));
						}
						else{
							//Properly name and drop the Player (or MHF) head
							meta.setOwner(profile.getName());
							meta.setDisplayName(ChatColor.WHITE+profile.getName()
									+ (profile.getName().startsWith("MHF_") ? "" : " Head"));
							skullItem.setItemMeta(meta);
							evt.getEntity().setItemStack(skullItem);
						}
					}
				}//if(profile != null)
			}//if(skull has meta)
		}//if(drop == player_skull)
	}//evt
}