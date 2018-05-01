package Evil_Code_DropHeads;

import net.md_5.bungee.api.ChatColor;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import com.mojang.authlib.GameProfile;

public class BarfItemListener implements Listener{
	DropHeads pl;
	BarfItemListener(){
		pl = DropHeads.getPlugin();
	}

	@EventHandler
	public void onBard(PlayerDropItemEvent evt){
		if(evt.getItemDrop().getItemStack().getType() == Material.SKULL_ITEM){
			ItemStack skullItem = evt.getItemDrop().getItemStack();
			if(skullItem.hasItemMeta() && skullItem.getItemMeta() instanceof SkullMeta){
				SkullMeta meta = (SkullMeta) skullItem.getItemMeta();
				
				GameProfile profile = Utils.getGameProfile(meta);
				if(profile != null){
					int idx = profile.getName().indexOf("|");
					if(idx != -1){
						EntityType type = EntityType.valueOf(profile.getName().substring(0, idx));
						evt.getItemDrop().setItemStack(Utils.makeTextureSkull(type, profile.getName().substring(idx+1)));
					}
					else{
						OfflinePlayer p;
						if(profile.getId() != null && (p = pl.getServer().getOfflinePlayer(profile.getId())) != null
								&& !p.getName().equals(profile.getName())){
							evt.getItemDrop().setItemStack(Utils.getPlayerHead(p.getUniqueId(), p.getName()));
						}
						else{
							meta.setOwner(profile.getName());
							meta.setDisplayName(ChatColor.WHITE+profile.getName()
									+ (profile.getName().startsWith("MHF_") ? "" : " Head"));
							skullItem.setItemMeta(meta);
							evt.getItemDrop().setItemStack(skullItem);
						}
					}
				}//if(profile != null)
			}//if(skull has meta)
		}//if(drop == skull)
	}//evt
}