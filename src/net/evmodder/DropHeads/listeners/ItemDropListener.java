package net.evmodder.DropHeads.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.meta.SkullMeta;
import com.mojang.authlib.GameProfile;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.HeadUtils;
import net.evmodder.EvLib.EvUtils;

public class ItemDropListener implements Listener{
	@EventHandler
	public void onBarf(ItemSpawnEvent evt){
		if(evt.isCancelled() || !EvUtils.isPlayerHead(evt.getEntity().getItemStack().getType())
				|| !evt.getEntity().getItemStack().hasItemMeta()) return;

		SkullMeta meta = (SkullMeta) evt.getEntity().getItemStack().getItemMeta();
		GameProfile profile = HeadUtils.getGameProfile(meta);
		if(profile != null){
			evt.getEntity().setItemStack(DropHeads.getPlugin().getAPI().getHead(profile));
		}
	}
}