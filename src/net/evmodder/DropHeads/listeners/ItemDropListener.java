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
import net.evmodder.DropHeads.MiscUtils;
import net.evmodder.EvLib.bukkit.HeadUtils;
import net.evmodder.EvLib.bukkit.TellrawUtils;
import net.evmodder.EvLib.bukkit.TellrawUtils.Component;

public class ItemDropListener implements Listener{
	private final DropHeads pl;
	private final boolean /*TEXTURE_UPDATE, */FORCE_NAME_UPDATE, FORCE_LORE_UPDATE, TYPE_UPDATE;
	private final boolean SAVE_TYPE_IN_LORE;

	public ItemDropListener(){
		pl = DropHeads.getPlugin();
		//FORCE_TEXTURE_UPDATE = pl.getConfig().getBoolean("refresh-textures", false);
		FORCE_NAME_UPDATE = pl.getConfig().getBoolean("refresh-item-names", false);
		FORCE_LORE_UPDATE = pl.getConfig().getBoolean("refresh-item-lores", false) && !pl.getConfig().getBoolean("save-custom-lore", true);
		TYPE_UPDATE = pl.getConfig().getBoolean("update-piglin-heads", true);
		SAVE_TYPE_IN_LORE = pl.getConfig().getBoolean("show-head-type-in-lore", false);
	}

	private boolean hasCustomLore(ItemMeta meta){
		if(!meta.hasLore()) return false;
		if(!SAVE_TYPE_IN_LORE) return true;
		if(meta.getLore().size() == 1 && ChatColor.stripColor(meta.getLore().get(0)).matches("\\w+:\\w+")) return false;
		return true;
	}

	private boolean hasCustomName(ItemMeta meta, GameProfile profile){
		if(!meta.hasDisplayName()) return false;
		if(profile.getName() == null) return true; // why return true?

		String textureKey = pl.getAPI().getTextureKey(profile);
		final String customName;
		if(textureKey == null){textureKey = "PLAYER"; customName = profile.getName();}
		else customName = "";//todo?

		final String expectedName = ChatColor.stripColor(pl.getInternalAPI().getFullHeadNameFromKey(textureKey, customName).toPlainText());
		final String actualName = ChatColor.stripColor(meta.getDisplayName());
		return !expectedName.equals(actualName);
	}

	@EventHandler(ignoreCancelled = true)
	public void onBarf(ItemSpawnEvent evt){
		if(!HeadUtils.isPlayerHead(evt.getEntity().getItemStack().getType()) || !evt.getEntity().getItemStack().hasItemMeta()
				|| pl.getInternalAPI().isHeadDatabaseHead(evt.getEntity().getItemStack())) return;

		ItemStack originalItem = evt.getEntity().getItemStack();
		final SkullMeta originalMeta = (SkullMeta) originalItem.getItemMeta();
		final GameProfile originalProfile = HeadUtils.getGameProfile(originalMeta);
		if(originalProfile == null) return;
		final ItemStack refreshedItem = pl.getAPI().getHead(originalProfile); // Gets a refreshed texture by textureKey (profile name)
		if(refreshedItem == null) return;
		//if(FORCE_TEXTURE_UPDATE){
			final GameProfile refreshedProfile = HeadUtils.getGameProfile((SkullMeta)refreshedItem.getItemMeta());
			HeadUtils.setGameProfile(originalMeta, refreshedProfile); // This refreshes the texture
		//}
		if(TYPE_UPDATE && originalItem.getType() != refreshedItem.getType()) originalItem.setType(refreshedItem.getType());

//		if(!originalMeta.hasDisplayName() || FORCE_NAME_UPDATE) originalMeta.setDisplayName(refreshedItem.getItemMeta().getDisplayName());
//		if(!hasCustomLore(originalMeta) || FORCE_LORE_UPDATE) originalMeta.setLore(refreshedItem.getItemMeta().getLore());
		originalItem.setItemMeta(originalMeta);

		if(!hasCustomName(originalMeta, originalProfile) || FORCE_NAME_UPDATE){
			if(refreshedItem.getItemMeta().hasDisplayName()){
				originalItem = MiscUtils.setDisplayName(originalItem, TellrawUtils.parseComponentFromString(MiscUtils.getDisplayName(refreshedItem)));
			}
			else{
				final ItemMeta meta = originalItem.getItemMeta();
				meta.setDisplayName(null);
				originalItem.setItemMeta(meta);
			}
		}
		if(!hasCustomLore(originalMeta) || FORCE_LORE_UPDATE){
			if(refreshedItem.getItemMeta().hasLore()){
				final List<String> lores = MiscUtils.getLore(refreshedItem);
				final Component[] loreComps = new Component[lores.size()];
				for(int i=0; i<lores.size(); ++i) loreComps[i] = TellrawUtils.parseComponentFromString(lores.get(i));
				originalItem = MiscUtils.setLore(originalItem, loreComps);
			}
			else{
				final ItemMeta meta = originalItem.getItemMeta();
				meta.setLore(null);
				originalItem.setItemMeta(meta);
			}
		}
		evt.getEntity().setItemStack(originalItem);
	}
}