package net.evmodder.DropHeads.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
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
		SkullMeta originalMeta = (SkullMeta) originalItem.getItemMeta();
		GameProfile originalProfile = HeadUtils.getGameProfile(originalMeta);
		if(originalProfile == null) return;
		ItemStack refreshedItem = plugin.getAPI().getHead(originalProfile); // Gets a refreshed texture by textureKey (profile name)
		if(refreshedItem == null) return;
		GameProfile refreshedProfile = HeadUtils.getGameProfile((SkullMeta)refreshedItem.getItemMeta());
		HeadUtils.setGameProfile(originalMeta, refreshedProfile); // This is what actually refreshes the texture

		if(!originalMeta.hasDisplayName() || FORCE_RENAME) originalMeta.setDisplayName(refreshedItem.getItemMeta().getDisplayName());
		originalMeta.setLore(refreshedItem.getItemMeta().getLore()); // Only does anything if 'show-head-type-in-lore' is true

		originalItem.setItemMeta(originalMeta);
		evt.getEntity().setItemStack(originalItem); // TODO: not sure if this is necessary
	}
}