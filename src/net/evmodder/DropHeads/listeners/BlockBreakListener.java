package net.evmodder.DropHeads.listeners;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Skull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import com.mojang.authlib.GameProfile;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.EvLib.extras.HeadUtils;

public class BlockBreakListener implements Listener{
	@EventHandler(priority = EventPriority.MONITOR)
	public void onBlockBreakEvent(BlockBreakEvent evt){
		if(evt.isCancelled() || evt.getPlayer().getGameMode() == GameMode.CREATIVE || !HeadUtils.isPlayerHead(evt.getBlock().getType())) return;

		Skull skull = (Skull)evt.getBlock().getState();
		GameProfile profile = HeadUtils.getGameProfile(skull);
		if(profile != null){
			evt.setCancelled(true);
			evt.getBlock().setType(Material.AIR);
			evt.getBlock().getWorld().dropItemNaturally(skull.getLocation(), DropHeads.getPlugin().getAPI().getHead(profile));
		}
	}
}