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
import com.mojang.authlib.properties.Property;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.MiscUtils;
import net.evmodder.EvLib.bukkit.HeadUtils;
import net.evmodder.EvLib.bukkit.YetAnotherProfile;

public class LoreStoreBlockPlaceListener implements Listener{
	private final boolean HAS_DEFAULT_LORE;

	public LoreStoreBlockPlaceListener(){
		HAS_DEFAULT_LORE = DropHeads.getPlugin().getConfig().getBoolean("show-head-type-in-lore", false);
	}

	// This listener is only registered when 'save-custom-lore' = true
	// Monitor priority since there is no way for us to replace the placed block without cancelling and setting manually
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockPlaceEvent(BlockPlaceEvent evt){
		if(!HeadUtils.isPlayerHead(evt.getBlockPlaced().getType())) return;
		final ItemStack headItem = evt.getHand() == EquipmentSlot.HAND
				? evt.getPlayer().getInventory().getItemInMainHand()
				: evt.getPlayer().getInventory().getItemInOffHand();

		if(!headItem.hasItemMeta() || !headItem.getItemMeta().hasLore()) return; // Nothing to save!
		final SkullMeta meta = (SkullMeta)headItem.getItemMeta();
		final YetAnotherProfile profile = YetAnotherProfile.fromSkullMeta(meta);
		if(profile == null) return; // We can't append lore to an invalid GameProfile...

		final List<String> customLore = MiscUtils.getLore(headItem);
		if(customLore == null) return;
		if(HAS_DEFAULT_LORE){
			final List<String> defaultLore = MiscUtils.getLore(DropHeads.getPlugin().getAPI().getHead(profile));
			if(customLore.equals(defaultLore)) return; // Nothing to save!
		}
		final String combinedLore = String.join("\n", customLore);

		profile.properties().put(MiscUtils.DH_LORE_KEY, new Property(MiscUtils.DH_LORE_KEY, combinedLore));
		final Skull skull = (Skull)evt.getBlockPlaced().getState();
		profile.set(skull);
		skull.update(true);
	}
}