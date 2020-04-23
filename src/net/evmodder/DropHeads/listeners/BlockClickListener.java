package net.evmodder.DropHeads.listeners;

import java.util.HashSet;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.block.Skull;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;
import com.mojang.authlib.GameProfile;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.EvLib.extras.HeadUtils;
import net.evmodder.EvLib.extras.TextUtils;

public class BlockClickListener implements Listener{
	final DropHeads plugin;
	final String headDisplayStrMob, headDisplayStrPlayer;
	final long clickMessageDelayTicks = 10; // So they dont spam themselves
	final HashSet<UUID> recentClickers;

	public BlockClickListener(){
		plugin = DropHeads.getPlugin();
		headDisplayStrMob = TextUtils.translateAlternateColorCodes('&',
				plugin.getConfig().getString("head-click-format-mobs", "That's ${A} ${NAME} ${TYPE}"));
		headDisplayStrPlayer = TextUtils.translateAlternateColorCodes('&',
				plugin.getConfig().getString("head-click-format-players", "That's ${NAME}'s Head"));
		// That's a <Swamp Amorsmith Zombie Villager> <Head>
		// That's <EvDoc>'s Head
		recentClickers = new HashSet<>();
	}

	@EventHandler
	public void onBlockClickEvent(PlayerInteractEvent evt){
		if(evt.isCancelled() || evt.useInteractedBlock() == Result.DENY || evt.getAction() != Action.RIGHT_CLICK_BLOCK
			|| evt.getPlayer().isSneaking() || !HeadUtils.isHead(evt.getClickedBlock().getType())) return;
		evt.setCancelled(true);

		if(!recentClickers.add(evt.getPlayer().getUniqueId())) return;
		final UUID uuid = evt.getPlayer().getUniqueId();
		new BukkitRunnable(){@Override public void run(){recentClickers.remove(uuid);}}.runTaskLater(plugin, clickMessageDelayTicks);

		boolean isMobHead = false;
		final String headName;

		if(HeadUtils.isPlayerHead(evt.getClickedBlock().getType())){
			Skull skull = (Skull) evt.getClickedBlock().getState();
			GameProfile profile = HeadUtils.getGameProfile(skull);
			if(profile == null || profile.getName() == null){
				isMobHead = true;
				headName = plugin.getAPI().getHeadNameFromKey(EntityType.PLAYER.name());
			}
			else{
				String profileName = profile.getName();
				int savedLoreStart = profileName.indexOf('>');
				if(savedLoreStart != -1) profileName = profileName.substring(0, savedLoreStart);
				isMobHead = plugin.getAPI().textureExists(profileName);
				headName = plugin.getAPI().getHeadName(profile);
			}
		}
		else{
			isMobHead = true;
			EntityType entity = HeadUtils.getEntityFromHead(evt.getClickedBlock().getType());
			headName = plugin.getAPI().getHeadNameFromKey(entity.name());
		}
		int idx = headName.lastIndexOf(' ');
		final String entityName = ChatColor.stripColor(headName.substring(0, idx));
		final String headTypeName = ChatColor.stripColor(headName.substring(idx+1));
		final String aOrAn = entityName.matches("[aeiouAEIOU].*") ? "an" : "a"; // Yes, an imperfect solution, I know. :l

		final String displayStr = (isMobHead ? headDisplayStrMob : headDisplayStrPlayer)
								.replace("${NAME}", entityName).replace("${TYPE}", headTypeName).replace("${A}", aOrAn);
		evt.getPlayer().sendMessage(displayStr);
	}
}