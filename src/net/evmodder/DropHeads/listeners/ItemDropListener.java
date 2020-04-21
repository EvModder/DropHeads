package net.evmodder.DropHeads.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import com.mojang.authlib.GameProfile;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.EvLib.extras.HeadUtils;

public class ItemDropListener implements Listener{
	final DropHeads plugin;
	final boolean FORCE_RENAME;

	public ItemDropListener(){
		plugin = DropHeads.getPlugin();
		FORCE_RENAME = plugin.getConfig().getBoolean("refresh-item-names", false);
	}

	@EventHandler
	public void onBarf(ItemSpawnEvent evt){
		if(evt.isCancelled() || !HeadUtils.isPlayerHead(evt.getEntity().getItemStack().getType()) || !evt.getEntity().getItemStack().hasItemMeta()) return;

		ItemStack originalItem = evt.getEntity().getItemStack();
		SkullMeta meta = (SkullMeta) originalItem.getItemMeta();
		String name = !FORCE_RENAME && meta.hasDisplayName() ? meta.getDisplayName() : null;
		GameProfile profile = HeadUtils.getGameProfile(meta);
		if(profile == null) return;
		ItemStack refreshedItem = plugin.getAPI().getHead(profile);
		if(refreshedItem == null) return;
		ItemMeta refreshedItemMeta = refreshedItem.getItemMeta();
		if(name != null) refreshedItemMeta.setDisplayName(name);
		originalItem.setItemMeta(refreshedItemMeta);
		evt.getEntity().setItemStack(originalItem);
	}
}