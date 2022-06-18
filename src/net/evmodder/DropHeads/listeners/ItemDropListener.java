package net.evmodder.DropHeads.listeners;

import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import com.mojang.authlib.GameProfile;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.JunkUtils;
import net.evmodder.EvLib.extras.HeadUtils;
import net.evmodder.EvLib.extras.TellrawUtils;
import net.evmodder.EvLib.extras.TellrawUtils.Component;

public class ItemDropListener implements Listener{
	private final DropHeads pl;
	private final boolean FORCE_NAME_UPDATE, FORCE_LORE_UPDATE;
	private final boolean SAVE_TYPE_IN_LORE;

	public ItemDropListener(){
		pl = DropHeads.getPlugin();
		FORCE_NAME_UPDATE = pl.getConfig().getBoolean("refresh-item-names", false);
		FORCE_LORE_UPDATE = pl.getConfig().getBoolean("refresh-item-lores", false) && !pl.getConfig().getBoolean("save-custom-lore", true);
		SAVE_TYPE_IN_LORE = pl.getConfig().getBoolean("show-head-type-in-lore", false);
	}

	private boolean hasCustomLore(ItemMeta meta){
		if(!meta.hasLore()) return false;
		if(!SAVE_TYPE_IN_LORE) return true;
		if(meta.getLore().size() == 1 && ChatColor.stripColor(meta.getLore().get(0)).matches("\\w+:\\w+")) return false;
		return true;
	}

	@EventHandler(ignoreCancelled = true)
	public void onBarf(ItemSpawnEvent evt){
		if(!HeadUtils.isPlayerHead(evt.getEntity().getItemStack().getType()) || !evt.getEntity().getItemStack().hasItemMeta()
				|| pl.getAPI().isHeadDatabaseHead(evt.getEntity().getItemStack())) return;

		ItemStack originalItem = evt.getEntity().getItemStack();
		final SkullMeta originalMeta = (SkullMeta) originalItem.getItemMeta();
		final GameProfile originalProfile = HeadUtils.getGameProfile(originalMeta);
		if(originalProfile == null) return;
		final ItemStack refreshedItem = pl.getAPI().getHead(originalProfile); // Gets a refreshed texture by textureKey (profile name)
		if(refreshedItem == null) return;
		final GameProfile refreshedProfile = HeadUtils.getGameProfile((SkullMeta)refreshedItem.getItemMeta());
		HeadUtils.setGameProfile(originalMeta, refreshedProfile); // This refreshes the texture

//		if(!originalMeta.hasDisplayName() || FORCE_NAME_UPDATE) originalMeta.setDisplayName(refreshedItem.getItemMeta().getDisplayName());
//		if(!hasCustomLore(originalMeta) || FORCE_LORE_UPDATE) originalMeta.setLore(refreshedItem.getItemMeta().getLore());
		originalItem.setItemMeta(originalMeta);

		if((!originalMeta.hasDisplayName() || FORCE_NAME_UPDATE) && refreshedItem.getItemMeta().hasDisplayName()){
			originalItem = JunkUtils.setDisplayName(originalItem,
					TellrawUtils.parseComponentFromString(JunkUtils.getDisplayName(refreshedItem)));
		}
		if((!hasCustomLore(originalMeta) || FORCE_LORE_UPDATE) && refreshedItem.getItemMeta().hasLore()){
			List<String> lores = JunkUtils.getLore(refreshedItem);
			Component[] loreComps = new Component[lores.size()];
			for(int i=0; i<lores.size(); ++i) loreComps[i] = TellrawUtils.parseComponentFromString(lores.get(i));
			originalItem = JunkUtils.setLore(originalItem, loreComps);
		}
		evt.getEntity().setItemStack(originalItem);
	}
}