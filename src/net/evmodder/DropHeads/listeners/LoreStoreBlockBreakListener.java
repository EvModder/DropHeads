package net.evmodder.DropHeads.listeners;

import java.util.Arrays;
import java.util.List;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import com.mojang.authlib.GameProfile;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.EvLib.extras.HeadUtils;

public class LoreStoreBlockBreakListener implements Listener{
	// This listener is only registered when 'save-custom-lore' = true

	// Returns null unless the block is a skull that contains encodes lore text
	static ItemStack getItemWithLore(Block block){
		if(!HeadUtils.isPlayerHead(block.getType())) return null;

		Skull skull = (Skull) block.getState();
		GameProfile profile = HeadUtils.getGameProfile(skull);
		if(profile == null || profile.getName() == null) return null;

		int endCustomName = profile.getName().lastIndexOf('"');
		int loreStart = profile.getName().indexOf('>', endCustomName + 1);
		if(loreStart == -1) return null;

		List<String> lore = Arrays.asList(profile.getName().substring(loreStart + 1).split("\\n", -1));
		String textureKey = profile.getName().substring(0, loreStart);

		GameProfile profileWithoutLore = new GameProfile(profile.getId(), textureKey);
		ItemStack headItem = DropHeads.getPlugin().getAPI().getHead(profileWithoutLore);
		if(lore.size() > 1 || (lore.size() == 1 && !lore.get(0).isEmpty())){
			ItemMeta meta = headItem.getItemMeta();
			meta.setLore(lore);
			headItem.setItemMeta(meta);
		}
		return headItem;
	}
	// Monitor priority since there is no way for us to replace the dropped item without cancelling and dropping manually
	// TODO: Switch to BlockDropItemEvent once it is fully supported.
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockBreakByPlayerEvent(BlockBreakEvent evt){
		if(evt.getPlayer().getGameMode() == GameMode.CREATIVE) return;
		ItemStack itemWithAddedLore = getItemWithLore(evt.getBlock());
		if(itemWithAddedLore != null){
			evt.setCancelled(true);
			evt.getBlock().setType(Material.AIR);
			evt.getBlock().getWorld().dropItemNaturally(evt.getBlock().getLocation(), itemWithAddedLore);
		}
	}
	/*@EventHandler(priority = EventPriority.MONITOR)
	public void onBlockExplodeEvent(BlockExplodeEvent evt){
		for(Block exploded : evt.blockList()){
			DropHeads.getPlugin().getLogger().info("block exploded: "+exploded.getType());
			ItemStack itemWithAddedLore = getItemWithLore(exploded);
			if(itemWithAddedLore != null){
				evt.setCancelled(true);
				evt.getBlock().setType(Material.AIR);
				evt.getBlock().getWorld().dropItemNaturally(evt.getBlock().getLocation(), itemWithAddedLore);
			}
		}
	}*/
	// TODO: Does not check for blocked broken by an extending piston.
	// Listening to that event would be expensive.
}