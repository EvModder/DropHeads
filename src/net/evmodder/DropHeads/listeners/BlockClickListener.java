package net.evmodder.DropHeads.listeners;

import java.util.HashSet;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Skull;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import com.mojang.authlib.GameProfile;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.HeadAPI.HeadNameData;
import net.evmodder.EvLib.extras.HeadUtils;
import net.evmodder.EvLib.extras.TextUtils;

public class BlockClickListener implements Listener{
	final private DropHeads pl;
	final boolean SHOW_CLICK_INFO, REPAIR_IRON_GOLEM_HEADS;
	final String HEAD_DISPLAY_PLAYERS, HEAD_DISPLAY_MOBS, HEAD_DISPLAY_HDB, HEAD_DISPLAY_UNKNOWN;
	final long clickMessageDelayTicks = 10; // So they dont spam themselves
	final HashSet<UUID> recentClickers;

	public BlockClickListener(){
		pl = DropHeads.getPlugin();
		REPAIR_IRON_GOLEM_HEADS = pl.getConfig().getBoolean("cracked-iron-golem-heads", false);
		if(SHOW_CLICK_INFO = pl.getConfig().getBoolean("head-click-listener", true)){
			// That's <a> <Swamp Amorsmith Zombie Villager> <Head>
			// That's <EvDoc>'s Head
			HEAD_DISPLAY_PLAYERS = TextUtils.translateAlternateColorCodes('&',
					pl.getConfig().getString("head-click-format-players", "&7[&6DropHeads&7]&f That's ${NAME}'s Head"));
			HEAD_DISPLAY_MOBS = TextUtils.translateAlternateColorCodes('&',
					pl.getConfig().getString("head-click-format-mobs", "&7[&6DropHeads&7]&f That's ${A} ${MOB_TYPE} ${HEAD_TYPE}"));
			HEAD_DISPLAY_HDB = TextUtils.translateAlternateColorCodes('&',
					pl.getConfig().getString("head-click-format-hdb", "&7[&6DropHeads&7]&f That's ${A} ${NAME} Head"));
			HEAD_DISPLAY_UNKNOWN = TextUtils.translateAlternateColorCodes('&',
					pl.getConfig().getString("head-click-format-unknown", "&7[&6DropHeads&7]&f That's ${A} ${NAME} Head"));
			recentClickers = new HashSet<>();
		}
		else{
			HEAD_DISPLAY_PLAYERS = HEAD_DISPLAY_MOBS = HEAD_DISPLAY_HDB = HEAD_DISPLAY_UNKNOWN = null;
			recentClickers = null;
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onBlockClickEvent(PlayerInteractEvent evt){
		if(evt.useInteractedBlock() == Result.DENY || evt.getAction() != Action.RIGHT_CLICK_BLOCK
			|| evt.getPlayer().isSneaking() || !HeadUtils.isHead(evt.getClickedBlock().getType())) return;

		if(REPAIR_IRON_GOLEM_HEADS && HeadUtils.isPlayerHead(evt.getClickedBlock().getType())
				&& evt.getPlayer().getInventory().getItemInMainHand() != null
				&& evt.getPlayer().getInventory().getItemInMainHand().getType() == Material.IRON_INGOT){
			Skull skull = (Skull) evt.getClickedBlock().getState();
			GameProfile profile = HeadUtils.getGameProfile(skull);
			if(profile != null && profile.getName() != null && profile.getName().startsWith("IRON_GOLEM|")){
				ItemStack newHeadItem = null;
				if(profile.getName().contains("|HIGH_CRACKINESS")){
					newHeadItem = pl.getAPI().getHead(EntityType.IRON_GOLEM, profile.getName().replace("|HIGH_CRACKINESS", "|MEDIUM_CRACKINESS"));
				}
				else if(profile.getName().contains("|MEDIUM_CRACKINESS")){
					newHeadItem = pl.getAPI().getHead(EntityType.IRON_GOLEM, profile.getName().replace("|MEDIUM_CRACKINESS", "|LOW_CRACKINESS"));
				}
				else if(profile.getName().contains("|LOW_CRACKINESS")){
					newHeadItem = pl.getAPI().getHead(EntityType.IRON_GOLEM, profile.getName().replace("|LOW_CRACKINESS", "|FULL_HEALTH"));
				}
				if(newHeadItem != null){
					HeadUtils.setGameProfile(skull, HeadUtils.getGameProfile((SkullMeta)newHeadItem.getItemMeta()));
					skull.update(/*force=*/true);
					if(evt.getPlayer().getGameMode() != GameMode.CREATIVE){
						int newIngotAmt = evt.getPlayer().getInventory().getItemInMainHand().getAmount() - 1;
						if(newIngotAmt <= 0) evt.getPlayer().getInventory().setItemInMainHand(new ItemStack(Material.AIR));
						else evt.getPlayer().getInventory().getItemInMainHand().setAmount(newIngotAmt);
					}
					return;
				}
			}
		}

		if(!SHOW_CLICK_INFO || !evt.getPlayer().hasPermission("dropheads.clickinfo")
				|| !recentClickers.add(evt.getPlayer().getUniqueId())) return;
		final UUID uuid = evt.getPlayer().getUniqueId();
		new BukkitRunnable(){@Override public void run(){recentClickers.remove(uuid);}}.runTaskLater(pl, clickMessageDelayTicks);

		final HeadNameData data;
		if(HeadUtils.isPlayerHead(evt.getClickedBlock().getType())){
			Skull skull = (Skull) evt.getClickedBlock().getState();
			GameProfile profile = HeadUtils.getGameProfile(skull);
			data = pl.getAPI().getHeadNameData(profile);
		}
		else{
			EntityType entityType = HeadUtils.getEntityFromHead(evt.getClickedBlock().getType());
			data = pl.getAPI().getHeadNameData(null);
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