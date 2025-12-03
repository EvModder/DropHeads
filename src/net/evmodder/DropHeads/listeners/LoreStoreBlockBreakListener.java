package net.evmodder.DropHeads.listeners;

import java.util.Arrays;
import java.util.Collection;
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
import com.mojang.authlib.properties.Property;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.MiscUtils;
import net.evmodder.EvLib.bukkit.HeadUtils;
import net.evmodder.EvLib.bukkit.TellrawUtils;
import net.evmodder.EvLib.bukkit.TellrawUtils.Component;
import net.evmodder.EvLib.bukkit.YetAnotherProfile;

public class LoreStoreBlockBreakListener implements Listener{
	// This listener is only registered when 'save-custom-lore' = true
	// data merge entity @e[type=item,distance=..5,limit=1] {Item:{tag:{display:{Lore:['{"text":"mob:cave_spider","color":"dark_gray","italic":false}','"e"']}}}}

	// Returns null unless the block is a skull that contains encodes lore text
	static ItemStack getItemWithLore(Block block){
		if(!HeadUtils.isPlayerHead(block.getType())) return null;

		final Skull skull = (Skull)block.getState();
		final YetAnotherProfile profile = YetAnotherProfile.fromSkull(skull);
		if(profile == null) return null;

		List<String> lore = null;
		YetAnotherProfile profileWithoutLore = null;
		if(profile.properties() != null && profile.properties().containsKey(MiscUtils.DH_LORE_KEY)){
			final Collection<Property> props = profile.properties().get(MiscUtils.DH_LORE_KEY);
			if(props != null && !props.isEmpty()){
				if(props.size() != 1) DropHeads.getPlugin().getLogger().warning("Multiple lore keys on a single head profile in getItemWithLore()");
				lore = Arrays.asList(MiscUtils.getPropertyValue(props.iterator().next()).split("\\n"));
				profileWithoutLore = new YetAnotherProfile(profile.id(), profile.name());
				profileWithoutLore.properties().putAll(profile.properties());
				profileWithoutLore.properties().removeAll(MiscUtils.DH_LORE_KEY);
			}
		}
		if(lore == null){//TODO: remove this at some point in the future
			if(profile.name() == null) return null;
			final int loreStart = profile.name().indexOf('>');
			if(loreStart == -1) return null;
			lore = Arrays.asList(profile.name().substring(loreStart + 1).split("\\n", -1));
			profileWithoutLore = new YetAnotherProfile(profile.id(), profile.name().substring(0, loreStart), profile.properties());
		}
		ItemStack headItem = DropHeads.getPlugin().getAPI().getHead(profileWithoutLore);
		if(lore.size() > 1 || (lore.size() == 1 && !lore.get(0).isEmpty())){
			final Component[] loreComps = new Component[lore.size()];
			for(int i=0; i<lore.size(); ++i) loreComps[i] = TellrawUtils.parseComponentFromString(lore.get(i));
			headItem = MiscUtils.setLore(headItem, loreComps);
		}
		return headItem;
	}
	// Monitor priority since there is no way for us to replace the dropped item without cancelling and dropping manually
	// TODO: Switch to BlockDropItemEvent once it is fully supported.
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockBreakByPlayerEvent(BlockBreakEvent evt){
		if(evt.getPlayer().getGameMode() == GameMode.CREATIVE) return;
		final ItemStack itemWithAddedLore = getItemWithLore(evt.getBlock());
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
	// TODO: Does not check for blocks broken by an extending piston.
	// Listening to that event would be expensive.
}