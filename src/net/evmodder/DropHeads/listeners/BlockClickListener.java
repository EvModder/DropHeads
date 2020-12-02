package net.evmodder.DropHeads.listeners;

import java.util.HashSet;
import java.util.UUID;
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
import net.evmodder.DropHeads.HeadAPI.HeadNameData;
import net.evmodder.EvLib.extras.HeadUtils;
import net.evmodder.EvLib.extras.TextUtils;

public class BlockClickListener implements Listener{
	final DropHeads plugin;
	final String HEAD_DISPLAY_PLAYERS, HEAD_DISPLAY_MOBS, HEAD_DISPLAY_HDB, HEAD_DISPLAY_UNKNOWN;
	final long clickMessageDelayTicks = 10; // So they dont spam themselves
	final HashSet<UUID> recentClickers;

	public BlockClickListener(){
		plugin = DropHeads.getPlugin();
		HEAD_DISPLAY_PLAYERS = TextUtils.translateAlternateColorCodes('&',
				plugin.getConfig().getString("head-click-format-players", "&7[&6DropHeads&7]&f That's ${NAME}'s Head"));
		HEAD_DISPLAY_MOBS = TextUtils.translateAlternateColorCodes('&',
				plugin.getConfig().getString("head-click-format-mobs", "&7[&6DropHeads&7]&f That's ${A} ${MOB_TYPE} ${HEAD_TYPE}"));
		HEAD_DISPLAY_HDB = TextUtils.translateAlternateColorCodes('&',
				plugin.getConfig().getString("head-click-format-hdb", "&7[&6DropHeads&7]&f That's ${A} ${NAME} Head"));
		HEAD_DISPLAY_UNKNOWN = TextUtils.translateAlternateColorCodes('&',
				plugin.getConfig().getString("head-click-format-unknown", "&7[&6DropHeads&7]&f That's ${A} ${NAME} Head"));
		// That's a <Swamp Amorsmith Zombie Villager> <Head>
		// That's <EvDoc>'s Head
		recentClickers = new HashSet<>();
	}

	@EventHandler(ignoreCancelled = true)
	public void onBlockClickEvent(PlayerInteractEvent evt){
		if(evt.useInteractedBlock() == Result.DENY || evt.getAction() != Action.RIGHT_CLICK_BLOCK
			|| evt.getPlayer().isSneaking() || !HeadUtils.isHead(evt.getClickedBlock().getType())
			|| !evt.getPlayer().hasPermission("dropheads.clickinfo")) return;

		if(!recentClickers.add(evt.getPlayer().getUniqueId())) return;
		final UUID uuid = evt.getPlayer().getUniqueId();
		new BukkitRunnable(){@Override public void run(){recentClickers.remove(uuid);}}.runTaskLater(plugin, clickMessageDelayTicks);

		final HeadNameData data;
		if(HeadUtils.isPlayerHead(evt.getClickedBlock().getType())){
			Skull skull = (Skull) evt.getClickedBlock().getState();
			GameProfile profile = HeadUtils.getGameProfile(skull);
			data = plugin.getAPI().GetHeadNameData(profile);
		}
		else{
			EntityType entityType = HeadUtils.getEntityFromHead(evt.getClickedBlock().getType());
			data = plugin.getAPI().GetHeadNameData(null);
			data.textureKey = entityType.name();
			data.headTypeName = HeadUtils.getDroppedHeadTypeName(entityType);
			data.entityName = data.headName = TextUtils.getNormalizedEntityName(entityType.name());
		}
		final String aOrAn = data.headName.matches("[aeiouAEIOU].*") ? "an" : "a"; // Yes, an imperfect solution, I know. :/

		final String HEAD_DISPLAY;
		if(data.player != null){if(!evt.getPlayer().hasPermission("dropheads.clickinfo.players")) return; HEAD_DISPLAY = HEAD_DISPLAY_PLAYERS;}
		else if(data.textureKey != null){if(!evt.getPlayer().hasPermission("dropheads.clickinfo.mobs")) return; HEAD_DISPLAY = HEAD_DISPLAY_MOBS;}
		else if(data.hdbId != null){if(!evt.getPlayer().hasPermission("dropheads.clickinfo.hdb")) return; HEAD_DISPLAY = HEAD_DISPLAY_HDB;}
		else{if(!evt.getPlayer().hasPermission("dropheads.clickinfo.unknown")) return; HEAD_DISPLAY = HEAD_DISPLAY_UNKNOWN;}

		evt.setCancelled(true);
		evt.getPlayer().sendMessage(
				HEAD_DISPLAY
				.replace("${NAME}", data.headName)
				.replace("${MOB_TYPE}", data.entityName)
				.replace("${TYPE}", data.headTypeName).replace("${HEAD_TYPE}", data.headTypeName).replace("${SKULL_TYPE}", data.headTypeName)
				.replace("${A}", aOrAn));
	}
}