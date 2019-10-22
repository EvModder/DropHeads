package net.evmodder.DropHeads.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import com.mojang.authlib.GameProfile;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.HeadUtils;
import net.evmodder.EvLib.EvUtils;

public class ItemDropListener implements Listener{
	final boolean FORCE_RENAME;
	public ItemDropListener(){
		FORCE_RENAME = DropHeads.getPlugin().getConfig().getBoolean("refresh-item-names", false);
	}

	@EventHandler
	public void onBarf(ItemSpawnEvent evt){
		if(evt.isCancelled() || !EvUtils.isPlayerHead(evt.getEntity().getItemStack().getType())
				|| !evt.getEntity().getItemStack().hasItemMeta()) return;

		SkullMeta meta = (SkullMeta) evt.getEntity().getItemStack().getItemMeta();
		String name = !FORCE_RENAME && meta.hasDisplayName() ? meta.getDisplayName() : null;
		GameProfile profile = HeadUtils.getGameProfile(meta);
		if(profile == null) return;
		ItemStack refreshedItem = DropHeads.getPlugin().getAPI().getHead(profile);
		if(refreshedItem == null) return;
		if(name != null){
			ItemMeta newMeta = refreshedItem.getItemMeta();
			newMeta.setDisplayName(name);
			refreshedItem.setItemMeta(newMeta);
		}
		evt.getEntity().setItemStack(refreshedItem);
	}
}