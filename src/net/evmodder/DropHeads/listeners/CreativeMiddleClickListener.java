package net.evmodder.DropHeads.listeners;

import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.ItemStack;
import com.mojang.authlib.GameProfile;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.EvLib.bukkit.HeadUtils;

public class CreativeMiddleClickListener implements Listener{
	// This listener is only registered when 'fix-creative-nbt-copy' = true

	@SuppressWarnings("deprecation")
	@EventHandler(ignoreCancelled = true)
	public void onInventoryCreativeEvent(InventoryClickEvent evt){
		if(evt.getAction() == InventoryAction.PLACE_ALL && evt.getClick() == ClickType.CREATIVE && evt.getSlotType() == SlotType.QUICKBAR
				&& evt.getCursor() != null && HeadUtils.isPlayerHead(evt.getCursor().getType())){

			final Block headBlock = evt.getWhoClicked().getTargetBlockExact(10);
			if(headBlock != null && HeadUtils.isPlayerHead(headBlock.getType())){
				final ItemStack itemWithAddedLore = LoreStoreBlockBreakListener.getItemWithLore(headBlock);
				if(itemWithAddedLore != null){  // Only used when 'save-custom-lore' = true
					evt.setCursor(itemWithAddedLore);
				}
				else{
					final GameProfile profile = HeadUtils.getGameProfile((Skull)headBlock.getState());
					final ItemStack headItem = DropHeads.getPlugin().getAPI().getHead(profile);
					if(headItem != null) evt.setCursor(headItem);
				}
			}
		}
	}
}