package evmodder.DropHeads;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Skull;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import com.mojang.authlib.GameProfile;
import EvLib2.EvUtils;

public class BlockBreakListener implements Listener{
	DropHeads pl;
	BlockBreakListener(){
		pl = DropHeads.getPlugin();
	}

	@SuppressWarnings("deprecation") @EventHandler(priority = EventPriority.MONITOR)
	public void onBlockBreakEvent(BlockBreakEvent evt){
		if(!evt.isCancelled() && evt.getPlayer().getGameMode() != GameMode.CREATIVE
				&& EvUtils.isPlayerHead(evt.getBlock().getType())){
			Skull skull = (Skull) evt.getBlock().getState();

			GameProfile profile = Utils.getGameProfile(skull);
			if(profile != null && profile.getName() != null){
				//
				ItemStack skullItem;
				int idx = profile.getName().indexOf("|");
				if(idx != -1){
					EntityType type = EntityType.valueOf(profile.getName().substring(0, idx));
					skullItem = Utils.makeTextureSkull(type, profile.getName().substring(idx+1));
				}
				else{
					OfflinePlayer p;
					if(profile.getId() != null && (p = pl.getServer().getOfflinePlayer(profile.getId())) != null
							&& p.getName() != null && !p.getName().equals(profile.getName())){
						skullItem = Utils.getPlayerHead(p);
					}
					else{
						skullItem = new ItemStack(Material.PLAYER_HEAD);
						SkullMeta meta = (SkullMeta) skullItem.getItemMeta();
						meta.setOwner(profile.getName());
						meta.setDisplayName(ChatColor.WHITE+profile.getName()
								+ (profile.getName().startsWith("MHF_") ? "" : " Head"));
						skullItem.setItemMeta(meta);
					}
				}
				evt.setCancelled(true);
				evt.getBlock().setType(Material.AIR);
				evt.getBlock().getWorld().dropItemNaturally(skull.getLocation(), skullItem);
			}
		}
	}
}