package net.evmodder.DropHeads.listeners;

import java.util.List;
import org.bukkit.block.Skull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import com.mojang.authlib.GameProfile;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.JunkUtils;
import net.evmodder.EvLib.extras.HeadUtils;

public class LoreStoreBlockPlaceListener implements Listener{
	// This listener is only registered when 'save-custom-lore' = true
	// Monitor priority since there is no way for us to replace the placed block without cancelling and setting manually
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockPlaceEvent(BlockPlaceEvent evt){
		if(!HeadUtils.isPlayerHead(evt.getBlockPlaced().getType())) return;
		final ItemStack headItem = evt.getHand() == EquipmentSlot.HAND
				? evt.getPlayer().getInventory().getItemInMainHand()
				: evt.getPlayer().getInventory().getItemInOffHand();

		if(!headItem.hasItemMeta() || !headItem.getItemMeta().hasLore()) return; // Nothing to save!
		final SkullMeta meta = (SkullMeta) headItem.getItemMeta();
		final GameProfile profile = HeadUtils.getGameProfile(meta);
		if(profile == null || profile.getName() == null) return; // We can't append lore to an invalid GameProfile...

		final List<String> customLore = JunkUtils.getLore(headItem);
		final List<String> defaultLore = JunkUtils.getLore(DropHeads.getPlugin().getAPI().getHead(profile));
		if(defaultLore.equals(customLore)) return; // Nothing to save!

		final String texturekey = profile.getName();
		final String combinedLore = String.join("\n", customLore);
		final GameProfile profileWithLore = new GameProfile(profile.getId(), texturekey+">"+combinedLore);
		profileWithLore.getProperties().putAll("textures", profile.getProperties().get("textures"));

		final Skull skull = (Skull)evt.getBlockPlaced().getState();
		HeadUtils.setGameProfile(skull, profileWithLore);
		skull.update(true);
	}
}