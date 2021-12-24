package net.evmodder.DropHeads.listeners;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.ItemStack;
import com.mojang.authlib.GameProfile;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.EvLib.extras.HeadUtils;

public class CreativeMiddleClickListener implements Listener{
	// This listener is only registered when 'fix-creative-nbt-copy' = true

	@SuppressWarnings("deprecation")
	@EventHandler(ignoreCancelled = true)
	public void onInventoryCreativeEvent(InventoryClickEvent evt){
		if(evt.getAction() == InventoryAction.PLACE_ALL && evt.getClick() == ClickType.CREATIVE && evt.getSlotType() == SlotType.QUICKBAR
				&& evt.getCursor() != null && HeadUtils.isPlayerHead(evt.getCursor().getType())){

			Player player = Bukkit.getPlayer(evt.getWhoClicked().getUniqueId());
			Block headBlock = player.getTargetBlockExact(10);
			if(HeadUtils.isPlayerHead(headBlock.getType())){
				ItemStack itemWithAddedLore = LoreStoreBlockBreakListener.getItemWithLore(headBlock);
				if(itemWithAddedLore != null){  // Only used when 'save-custom-lore' = true
					evt.setCursor(itemWithAddedLore);
				}
				else{
					GameProfile profile = HeadUtils.getGameProfile((Skull)headBlock.getState());
					ItemStack headItem = DropHeads.getPlugin().getAPI().getHead(profile);
					if(headItem != null) evt.setCursor(headItem);
				}
			}
		}
	}
}