package net.evmodder.DropHeads.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import com.mojang.authlib.GameProfile;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.JunkUtils;
import net.evmodder.EvLib.extras.HeadUtils;
import net.evmodder.EvLib.extras.TellrawUtils;

public class ItemDropListener implements Listener{
	final private DropHeads pl;
	final boolean FORCE_NAME_UPDATE, FORCE_LORE_UPDATE;

	public ItemDropListener(){
		pl = DropHeads.getPlugin();
		FORCE_NAME_UPDATE = pl.getConfig().getBoolean("refresh-item-names", false);
		FORCE_LORE_UPDATE = pl.getConfig().getBoolean("refresh-item-lores", false);
	}

	@EventHandler(ignoreCancelled = true)
	public void onBarf(ItemSpawnEvent evt){
		if(!HeadUtils.isPlayerHead(evt.getEntity().getItemStack().getType()) || !evt.getEntity().getItemStack().hasItemMeta()
				|| pl.getAPI().isHeadDatabaseHead(evt.getEntity().getItemStack())) return;

		ItemStack originalItem = evt.getEntity().getItemStack();
		SkullMeta originalMeta = (SkullMeta) originalItem.getItemMeta();
		GameProfile originalProfile = HeadUtils.getGameProfile(originalMeta);
		if(originalProfile == null) return;
		ItemStack refreshedItem = pl.getAPI().getHead(originalProfile); // Gets a refreshed texture by textureKey (profile name)
		if(refreshedItem == null) return;
		GameProfile refreshedProfile = HeadUtils.getGameProfile((SkullMeta)refreshedItem.getItemMeta());
		HeadUtils.setGameProfile(originalMeta, refreshedProfile); // This refreshes the texture

//		if(!originalMeta.hasDisplayName() || FORCE_NAME_UPDATE) originalMeta.setDisplayName(refreshedItem.getItemMeta().getDisplayName());
		if(!originalMeta.hasLore() || FORCE_LORE_UPDATE) originalMeta.setLore(refreshedItem.getItemMeta().getLore());
		originalItem.setItemMeta(originalMeta);

		if((!originalMeta.hasDisplayName() || FORCE_NAME_UPDATE) && refreshedItem.getItemMeta().hasDisplayName())
			originalItem = JunkUtils.setDisplayName(originalItem,
					TellrawUtils.parseComponentFromString(JunkUtils.getDisplayName(refreshedItem)));

		evt.getEntity().setItemStack(originalItem);
	}
}