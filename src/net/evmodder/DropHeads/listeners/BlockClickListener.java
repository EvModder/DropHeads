package net.evmodder.DropHeads.listeners;

import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.configuration.Configuration;
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
import com.mojang.authlib.properties.Property;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.EvLib.extras.HeadUtils;
import net.evmodder.EvLib.extras.TellrawUtils;
import net.evmodder.EvLib.extras.TextUtils;
import net.evmodder.EvLib.extras.WebUtils;
import net.evmodder.EvLib.extras.HeadUtils.HeadType;
import net.evmodder.EvLib.extras.TellrawUtils.RawTextComponent;
import net.evmodder.EvLib.extras.TellrawUtils.Component;
import net.evmodder.EvLib.extras.TellrawUtils.ListComponent;
import net.evmodder.EvLib.extras.TellrawUtils.TranslationComponent;

public class BlockClickListener implements Listener{
	final private DropHeads pl;
	final boolean SHOW_CLICK_INFO, REPAIR_IRON_GOLEM_HEADS, UPDATE_PLAYER_HEADS;
	final String HEAD_DISPLAY_PLAYERS, HEAD_DISPLAY_MOBS, HEAD_DISPLAY_HDB, HEAD_DISPLAY_UNKNOWN, MOB_SUBTYPES_SEPARATOR;
	final long clickMessageDelayTicks = 10; // So they dont spam themselves TODO: move to config
	final HashSet<UUID> recentClickers;

	public BlockClickListener(Configuration translations){
		pl = DropHeads.getPlugin();
		REPAIR_IRON_GOLEM_HEADS = pl.getConfig().getBoolean("cracked-iron-golem-heads", false);
		if(SHOW_CLICK_INFO = pl.getConfig().getBoolean("head-click-listener", true)){
			// That's <a> <Swamp Amorsmith Zombie Villager> <Head>
			// That's <EvDoc>'s Head
			HEAD_DISPLAY_PLAYERS = TextUtils.translateAlternateColorCodes('&',
					translations.getString("head-click-format-players",
							pl.getConfig().getString("head-click-format-players", "&7[&6DropHeads&7]&f That's ${NAME}'s Head")));
			HEAD_DISPLAY_MOBS = TextUtils.translateAlternateColorCodes('&',
					translations.getString("head-click-format-mobs",
							pl.getConfig().getString("head-click-format-mobs", "&7[&6DropHeads&7]&f That's ${A} ${MOB_TYPE} ${HEAD_TYPE}")));
			HEAD_DISPLAY_HDB = TextUtils.translateAlternateColorCodes('&',
					translations.getString("head-click-format-hdb",
							pl.getConfig().getString("head-click-format-hdb", "&7[&6DropHeads&7]&f That's ${A} ${NAME} Head")));
			HEAD_DISPLAY_UNKNOWN = TextUtils.translateAlternateColorCodes('&',
					translations.getString("head-click-format-unknown",
							pl.getConfig().getString("head-click-format-unknown", "&7[&6DropHeads&7]&f That's ${A} ${NAME} Head")));
			MOB_SUBTYPES_SEPARATOR = translations.getString("mob-subtype-separator", " ");
			UPDATE_PLAYER_HEADS = pl.getConfig().getBoolean("update-on-skin-change", true);
			recentClickers = new HashSet<UUID>();
		}
		else{
			HEAD_DISPLAY_PLAYERS = HEAD_DISPLAY_MOBS = HEAD_DISPLAY_HDB = HEAD_DISPLAY_UNKNOWN = MOB_SUBTYPES_SEPARATOR = null;
			UPDATE_PLAYER_HEADS = false;
			recentClickers = null;
		}
	}

	class HeadNameData{
		// Only 1 of these 3 is ever defined.
		public String hdbId, textureKey;
		public OfflinePlayer player;
		// All 3 of these are always defined.
		public Component[] entityTypeNames;
		public Component profileName;
		public HeadType headType;
	}
	HeadNameData getHeadNameData(GameProfile profile){
		HeadNameData data = new HeadNameData();
		if(profile == null){
			data.textureKey = EntityType.PLAYER.name();  // This is considered a mob head
			data.headType = HeadUtils.getDroppedHeadType(EntityType.PLAYER);  // "Head"
			data.entityTypeNames = pl.getAPI().getEntityTypeAndSubtypeNamesFromKey(EntityType.PLAYER.name());  // "Player"
			data.profileName = data.entityTypeNames[0];
			return data;
		}
		String fullProfileName = profile.getName();
		if(fullProfileName != null/* && SAVE_CUSTOM_LORE*/){
			int idx = fullProfileName.indexOf('>');
			if(idx != -1) profile = new GameProfile(profile.getId(), fullProfileName.substring(0, idx));
		}
		//hdb
		final HeadDatabaseAPI hdbAPI = pl.getAPI().getHeadDatabaseAPI();
		if(hdbAPI != null && (data.hdbId = hdbAPI.getItemID(HeadUtils.getPlayerHead(profile))) != null && hdbAPI.isHead(data.hdbId)){
			data.headType = HeadUtils.getDroppedHeadType(EntityType.UNKNOWN);  // "Head"
			data.entityTypeNames = pl.getAPI().getEntityTypeAndSubtypeNamesFromKey(EntityType.UNKNOWN.name());  // "Unknown"
			String hdbHeadName = hdbAPI.getItemHead(data.hdbId).getItemMeta().getDisplayName();
			int idx = hdbHeadName.lastIndexOf(' ');
			data.profileName = new RawTextComponent(idx == -1 ? hdbHeadName : hdbHeadName.substring(0, idx));
		}
		//player
		else if(profile.getId() != null && (data.player = pl.getServer().getOfflinePlayer(profile.getId())) != null
				&& (data.player.hasPlayedBefore() || WebUtils.checkPlayerExists(profile.getId().toString()))){
			data.headType = HeadUtils.getDroppedHeadType(EntityType.PLAYER);  // "Head"
			data.entityTypeNames = pl.getAPI().getEntityTypeAndSubtypeNamesFromKey(EntityType.PLAYER.name());  // "Player"
			data.profileName = new RawTextComponent(UPDATE_PLAYER_HEADS || profile.getName() == null ? data.player.getName() : profile.getName());
		}
		//mob
		else if(profile.getName() != null && pl.getAPI().textureExists(profile.getName())){
			data.textureKey = profile.getName();
			int idx = data.textureKey.indexOf('|');
			String eTypeName = (idx == -1 ? data.textureKey : data.textureKey.substring(0, idx)).toUpperCase();
			try{data.headType = HeadUtils.getDroppedHeadType(EntityType.valueOf(eTypeName));}
			catch(IllegalArgumentException ex){data.headType = HeadUtils.getDroppedHeadType(EntityType.UNKNOWN);}  // "Head"
			data.entityTypeNames = pl.getAPI().getEntityTypeAndSubtypeNamesFromKey(data.textureKey);
			data.profileName = new RawTextComponent(profile.getName());
		}
		//unknown
		else{
			data.headType = HeadUtils.getDroppedHeadType(EntityType.UNKNOWN);  // "Head"
			data.entityTypeNames = pl.getAPI().getEntityTypeAndSubtypeNamesFromKey(EntityType.UNKNOWN.name());  // "Unknown"
			data.profileName = profile.getName() != null ? new RawTextComponent(profile.getName()) : data.entityTypeNames[0];
		}
		if(data.hdbId != null && !hdbAPI.isHead(data.hdbId)) data.hdbId = null;
		if(data.player != null && data.player.getName() == null) data.player = null;
		return data;
	}
	HeadNameData getHeadNameData(BlockState skull){
		if(HeadUtils.isPlayerHead(skull.getType())){
			return getHeadNameData(HeadUtils.getGameProfile((Skull)skull));
		}
		//else: creeper, zombie, dragon, (wither)skeleton
		EntityType entityType = HeadUtils.getEntityFromHead(skull.getType());
		HeadNameData data = new HeadNameData();
		data.textureKey = entityType.name();
		data.headType = HeadUtils.getDroppedHeadType(entityType);
		data.entityTypeNames = pl.getAPI().getEntityTypeAndSubtypeNamesFromKey(entityType.name());
		data.profileName = data.entityTypeNames[0];
		return data;
	}

	@EventHandler(ignoreCancelled = true)
	public void onBlockClickEvent(PlayerInteractEvent evt){
		if(evt.useInteractedBlock() == Result.DENY || evt.getAction() != Action.RIGHT_CLICK_BLOCK
			|| evt.getPlayer().isSneaking() || !HeadUtils.isHead(evt.getClickedBlock().getType())
			|| !recentClickers.add(evt.getPlayer().getUniqueId())) return;
		final UUID uuid = evt.getPlayer().getUniqueId();
		new BukkitRunnable(){@Override public void run(){recentClickers.remove(uuid);}}.runTaskLater(pl, clickMessageDelayTicks);

		final boolean isPlayerHead = HeadUtils.isPlayerHead(evt.getClickedBlock().getType());

		if(REPAIR_IRON_GOLEM_HEADS && isPlayerHead
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
		}//if REPAIR_IRON_GOLEM_HEADS

		if(!SHOW_CLICK_INFO || !evt.getPlayer().hasPermission("dropheads.clickinfo")) return;

		final HeadNameData data = getHeadNameData(evt.getClickedBlock().getState());

		final String HEAD_DISPLAY;
		if(data.player != null){if(!evt.getPlayer().hasPermission("dropheads.clickinfo.players")) return; HEAD_DISPLAY = HEAD_DISPLAY_PLAYERS;}
		else if(data.textureKey != null){if(!evt.getPlayer().hasPermission("dropheads.clickinfo.mobs")) return; HEAD_DISPLAY = HEAD_DISPLAY_MOBS;}
		else if(data.hdbId != null){if(!evt.getPlayer().hasPermission("dropheads.clickinfo.hdb")) return; HEAD_DISPLAY = HEAD_DISPLAY_HDB;}
		else{if(!evt.getPlayer().hasPermission("dropheads.clickinfo.unknown")) return; HEAD_DISPLAY = HEAD_DISPLAY_UNKNOWN;}

		final TranslationComponent headTypeName = pl.getAPI().getHeadTypeName(data.headType);
		final String aOrAn =
				(data.textureKey != null ? pl.getAPI().getHeadNameFromKey(data.textureKey, /*customName=*/"").toPlainText()
					: data.player != null ? data.player.getName() : data.profileName.toPlainText())
				.matches("[aeiouAEIOU].*") ? "an" : "a"; // Yes, an imperfect solution, I know. :/

		evt.setCancelled(true);
		ListComponent blob = TellrawUtils.convertHexColorsToComponents(HEAD_DISPLAY);
		blob.replaceRawDisplayTextWithComponent("${TYPE}", headTypeName);//these 3 are aliases of eachother
		blob.replaceRawDisplayTextWithComponent("${HEAD_TYPE}", headTypeName);
		blob.replaceRawDisplayTextWithComponent("${SKULL_TYPE}", headTypeName);

		blob.replaceRawDisplayTextWithComponent("${A}", new RawTextComponent(aOrAn));
		blob.replaceRawDisplayTextWithComponent("${NAME}", data.profileName);
		blob.replaceRawDisplayTextWithComponent("${MOB_TYPE}", data.entityTypeNames[0]);
		if(HEAD_DISPLAY.contains("${TEXTURE}")){
			String code0 = "";
			if(isPlayerHead){
				GameProfile profile = HeadUtils.getGameProfile((Skull)evt.getClickedBlock().getState());
				Collection<Property> textures = profile.getProperties().get("textures");
				if(textures != null && !textures.isEmpty()) code0 = profile.getProperties().get("textures").iterator().next().getValue();
			}
			blob.replaceRawDisplayTextWithComponent("${TEXTURE}", new RawTextComponent(code0));
		}

		if(HEAD_DISPLAY.contains("${MOB_SUBTYPES_ASC}")){
			ListComponent subtypeNamesAsc = new ListComponent();
			for(int i=1; i<data.entityTypeNames.length; ++i){
				subtypeNamesAsc.addComponent(data.entityTypeNames[i]);
				/*if(i != data.entityTypeNames.length-1) */subtypeNamesAsc.addComponent(MOB_SUBTYPES_SEPARATOR);
			}
			blob.replaceRawDisplayTextWithComponent("${MOB_SUBTYPES_ASC}", subtypeNamesAsc);
		}
		if(HEAD_DISPLAY.contains("${MOB_SUBTYPES_DESC}")){
			ListComponent subtypeNamesDesc = new ListComponent();
			for(int i=data.entityTypeNames.length-1; i>0; --i){
				subtypeNamesDesc.addComponent(data.entityTypeNames[i]);
				/*if(i != 1) */subtypeNamesDesc.addComponent(MOB_SUBTYPES_SEPARATOR);
			}
			blob.replaceRawDisplayTextWithComponent("${MOB_SUBTYPES_DESC}", subtypeNamesDesc);
		}
		pl.getServer().dispatchCommand(pl.getServer().getConsoleSender(), "minecraft:tellraw "+evt.getPlayer().getName()+" "+blob.toString());
	}
}