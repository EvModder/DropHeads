package net.evmodder.DropHeads.listeners;

import org.bukkit.block.Skull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import com.mojang.authlib.GameProfile;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.EvLib.extras.HeadUtils;

public class BlockClickListener implements Listener{
	final DropHeads plugin;
	final String headDisplayStrMob, headDisplayStrPlayer;
//	final long clickMessageDelay;//so they dont spam themselves

	public BlockClickListener(){
		plugin = DropHeads.getPlugin();
		headDisplayStrMob = plugin.getConfig().getString("head-click-format-mobs", "That's a %headname% %headtype%");
		headDisplayStrPlayer = plugin.getConfig().getString("head-click-format-players", "That's %headname%'s Head");
		// That's a <Swamp Amorsmith Zombie Villager> <Head>
		// That's <EvDoc>'s Head
	}

	@EventHandler
	public void onBlockBreakEvent(PlayerInteractEvent evt){
		if(/*evt.isCancelled() || */evt.useInteractedBlock() == Result.DENY || evt.getAction() != Action.RIGHT_CLICK_BLOCK
			|| evt.getPlayer().isSneaking() || !HeadUtils.isHead(evt.getClickedBlock().getType())) return;
		Skull skull = (Skull) evt.getClickedBlock().getState();
		GameProfile profile = HeadUtils.getGameProfile(skull);
		if(profile == null || profile.getName() == null) return;

		String headName = plugin.getAPI().getHeadName(profile);
		int idx = headName.lastIndexOf(' ');
		String entityName = headName.substring(0, idx);
		String headTypeName = headName.substring(idx+1);
		String aOrAn = entityName.matches("[aeiouAEIOU].*") ? "an" : "a"; // Yes, an imperfect solution, I know. :l

		// This is how we check if this is a mob head (for now)
		String displayStr = plugin.getAPI().textureExists(profile.getName()) ? headDisplayStrMob : headDisplayStrPlayer;
		displayStr = displayStr.replace("%headname%", entityName).replace("%headtype%", headTypeName).replace("%a%", aOrAn);
		evt.setCancelled(true);
	}
}