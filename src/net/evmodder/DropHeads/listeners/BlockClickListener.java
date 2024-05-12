package net.evmodder.DropHeads.listeners;

import java.util.Base64;
import java.util.Collection;
import org.bukkit.ChatColor;
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
import net.evmodder.DropHeads.JunkUtils;
import net.evmodder.EvLib.extras.HeadUtils;
import net.evmodder.EvLib.extras.TellrawUtils;
import net.evmodder.EvLib.extras.TextUtils;
import net.evmodder.EvLib.extras.HeadUtils.HeadType;
import net.evmodder.EvLib.extras.TellrawUtils.RawTextComponent;
import net.evmodder.EvLib.extras.TellrawUtils.TextClickAction;
import net.evmodder.EvLib.extras.TellrawUtils.ClickEvent;
import net.evmodder.EvLib.extras.TellrawUtils.Component;
import net.evmodder.EvLib.extras.TellrawUtils.ListComponent;
import net.evmodder.EvLib.extras.TellrawUtils.TranslationComponent;

public class BlockClickListener implements Listener{
	private final DropHeads pl;
	private final boolean SHOW_CLICK_INFO, REPAIR_IRON_GOLEM_HEADS, UPDATE_PLAYER_HEADS, ASYNC_PROFILE_REQUESTS;
	private final String HEAD_DISPLAY_PLAYERS, HEAD_DISPLAY_MOBS, HEAD_DISPLAY_HDB, HEAD_DISPLAY_UNKNOWN, MOB_SUBTYPES_SEPARATOR;
	private final int CLICK_MSG_DELAY_TICKS; // So they dont spam themselves
	private boolean infoClickInCooldown = false;

	public BlockClickListener(Configuration translations){
		pl = DropHeads.getPlugin();
		REPAIR_IRON_GOLEM_HEADS = pl.getConfig().getBoolean("cracked-iron-golem-heads", false);
		if(SHOW_CLICK_INFO = pl.getConfig().getBoolean("head-click-listener", true)){
			CLICK_MSG_DELAY_TICKS = pl.getConfig().getInt("head-click-max-rate-in-ticks", 10);
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
			ASYNC_PROFILE_REQUESTS = pl.getConfig().getBoolean("async-offline-profile-requests", true) ||
					!pl.getConfig().getBoolean("head-click-enable-profile-requests", true);
		}
		else{
			CLICK_MSG_DELAY_TICKS = 5;
			HEAD_DISPLAY_PLAYERS = HEAD_DISPLAY_MOBS = HEAD_DISPLAY_HDB = HEAD_DISPLAY_UNKNOWN = MOB_SUBTYPES_SEPARATOR = null;
			ASYNC_PROFILE_REQUESTS = UPDATE_PLAYER_HEADS = false;
		}
	}

	private GameProfile stripCustomLoreAndNamespace(GameProfile profile){//TODO: delete soon(tm) since lore isn't stored in profile name since v3.9.4
		if(profile != null && profile.getName() != null){
			String name = profile.getName();
			int startIdx = name.startsWith(JunkUtils.TXT_KEY_PROFILE_NAME_PREFIX) ? JunkUtils.TXT_KEY_PROFILE_NAME_PREFIX.length() : 0;
			int endIdx = name.indexOf('>');
			name = name.substring(startIdx, endIdx == -1 ? name.length() : endIdx);
			if(!name.equals(profile.getName())) return new GameProfile(profile.getId(), name);
		}
		return profile;
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
	private HeadNameData getHeadNameData(GameProfile profile){
		HeadNameData data = new HeadNameData();
		GameProfile tempProfile = null;
		if(profile == null){
			data.textureKey = EntityType.PLAYER.name();  // This is considered a mob head
			data.headType = HeadUtils.getDroppedHeadType(EntityType.PLAYER);  // "Head"
			data.entityTypeNames = pl.getAPI().getEntityTypeAndSubtypeNamesFromKey(EntityType.PLAYER.name());  // "Player"
			data.profileName = data.entityTypeNames[0];
			return data;
		}
		profile = stripCustomLoreAndNamespace(profile);
		//hdb
		final HeadDatabaseAPI hdbAPI = pl.getInternalAPI().getHeadDatabaseAPI();
		if(hdbAPI != null && (data.hdbId = hdbAPI.getItemID(HeadUtils.makeCustomHead(profile, /*setOwner=*/false))) != null && hdbAPI.isHead(data.hdbId)){
			data.headType = HeadUtils.getDroppedHeadType(EntityType.UNKNOWN);  // "Head"
			data.entityTypeNames = pl.getAPI().getEntityTypeAndSubtypeNamesFromKey(EntityType.UNKNOWN.name());  // "Unknown"
			String hdbHeadName = hdbAPI.getItemHead(data.hdbId).getItemMeta().getDisplayName();
			int idx = hdbHeadName.lastIndexOf(' ');
			data.profileName = new RawTextComponent(idx == -1 ? hdbHeadName : hdbHeadName.substring(0, idx));
		}
		//mob
		else if((data.textureKey=pl.getAPI().getTextureKey(profile)) != null){
			final int idx = data.textureKey.indexOf('|');
			final String eTypeName = (idx == -1 ? data.textureKey : data.textureKey.substring(0, idx)).toUpperCase();
			try{data.headType = HeadUtils.getDroppedHeadType(EntityType.valueOf(eTypeName));}
			catch(IllegalArgumentException ex){data.headType = HeadUtils.getDroppedHeadType(EntityType.UNKNOWN);}  // "Head"
			data.entityTypeNames = pl.getAPI().getEntityTypeAndSubtypeNamesFromKey(data.textureKey);
			data.profileName = profile.getName() != null ? new RawTextComponent(profile.getName()) : null;//data.entityTypeNames[0];
		}
		//player
		else if(profile.getId() != null && (data.player=pl.getServer().getOfflinePlayer(profile.getId())) != null
				&& (data.player.hasPlayedBefore()
					|| (tempProfile=JunkUtils.getGameProfile(profile.getId().toString(), /*fetchSkin=*/false, ASYNC_PROFILE_REQUESTS ? pl : null)) != null)
		){
			data.headType = HeadUtils.getDroppedHeadType(EntityType.PLAYER);  // "Head"
			data.entityTypeNames = pl.getAPI().getEntityTypeAndSubtypeNamesFromKey(EntityType.PLAYER.name());  // "Player"
			data.profileName = new RawTextComponent(UPDATE_PLAYER_HEADS || profile.getName() == null 
					? tempProfile == null ? data.player.getName() : tempProfile.getName()
					: profile.getName());
		}
		//unknown
		else{
			data.headType = HeadUtils.getDroppedHeadType(EntityType.UNKNOWN);  // "Head"
			data.entityTypeNames = pl.getAPI().getEntityTypeAndSubtypeNamesFromKey(EntityType.UNKNOWN.name());  // "Unknown"
			data.profileName = profile.getName() != null ? new RawTextComponent(profile.getName()) : null;//data.entityTypeNames[0];
		}
		if(data.hdbId != null && !hdbAPI.isHead(data.hdbId)) data.hdbId = null;
		if(data.player != null && data.player.getName() == null) data.player = null;
		return data;
	}
	@SuppressWarnings("deprecation")
	private HeadNameData getHeadNameData(BlockState skull){
		if(HeadUtils.isPlayerHead(skull.getType())){
			HeadNameData headData = getHeadNameData(HeadUtils.getGameProfile((Skull)skull));
			if(((Skull)skull).hasOwner() && headData.player == null){
				OfflinePlayer player = ((Skull)skull).getOwningPlayer();
				GameProfile profile = JunkUtils.getGameProfile(player.getUniqueId().toString(), /*fetchSkin=*/false, ASYNC_PROFILE_REQUESTS ? pl : null);
				if(player.hasPlayedBefore() || profile != null){
					headData.player = player;
					if(player.getName() != null) headData.profileName = new RawTextComponent(player.getName());
					else if(profile != null && profile.getName() != null) headData.profileName = new RawTextComponent(profile.getName());
					else if(((Skull)skull).getOwner() != null) headData.profileName = new RawTextComponent(((Skull)skull).getOwner());
					//else headData.profileName = new RawTextComponent(EntityType.UNKNOWN.name());
				}
			}
			return headData;
		}
		//else: creeper, zombie, dragon, (wither)skeleton
		EntityType entityType = HeadUtils.getEntityFromHead(skull.getType());
		HeadNameData data = new HeadNameData();
		data.textureKey = entityType.name();
		data.headType = HeadUtils.getDroppedHeadType(entityType);
		data.entityTypeNames = pl.getAPI().getEntityTypeAndSubtypeNamesFromKey(entityType.name());
		data.profileName = null;//data.entityTypeNames[0];
		return data;
	}

	private boolean isVowel(char ch){
		switch(Character.toLowerCase(ch)){
			case 'a':
			case 'e':
			case 'i':
			case 'o':
			case 'u':
				return true;
			default:
				return false;
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onBlockClickEvent(PlayerInteractEvent evt){
		if(evt.useInteractedBlock() == Result.DENY || evt.getAction() != Action.RIGHT_CLICK_BLOCK
			|| evt.getPlayer().isSneaking() || !HeadUtils.isHead(evt.getClickedBlock().getType())) return;

		final boolean isPlayerHead = HeadUtils.isPlayerHead(evt.getClickedBlock().getType());

		if(REPAIR_IRON_GOLEM_HEADS && isPlayerHead
				&& evt.getPlayer().getInventory().getItemInMainHand() != null
				&& evt.getPlayer().getInventory().getItemInMainHand().getType() == Material.IRON_INGOT){
			final Skull skull = (Skull) evt.getClickedBlock().getState();
			final String textureKey = pl.getAPI().getTextureKey(HeadUtils.getGameProfile(skull));
			if(textureKey != null && textureKey.startsWith("IRON_GOLEM|")){
				ItemStack newHeadItem = null;
				if(textureKey.contains("|HIGH_CRACKINESS")){
					newHeadItem = pl.getAPI().getHead(EntityType.IRON_GOLEM, textureKey.replace("|HIGH_CRACKINESS", "|MEDIUM_CRACKINESS"));
				}
				else if(textureKey.contains("|MEDIUM_CRACKINESS")){
					newHeadItem = pl.getAPI().getHead(EntityType.IRON_GOLEM, textureKey.replace("|MEDIUM_CRACKINESS", "|LOW_CRACKINESS"));
				}
				else if(textureKey.contains("|LOW_CRACKINESS")){
					newHeadItem = pl.getAPI().getHead(EntityType.IRON_GOLEM, textureKey.replace("|LOW_CRACKINESS", "|FULL_HEALTH"));
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

		if(CLICK_MSG_DELAY_TICKS > 0){
			// Note: Unfortunately we need to cancel the click before knowing if they have perms to see the click msg
			if(infoClickInCooldown){evt.setCancelled(true); return;}
			infoClickInCooldown = true;
			new BukkitRunnable(){@Override public void run(){infoClickInCooldown = false;}}.runTaskLater(pl, CLICK_MSG_DELAY_TICKS);
		}

		final HeadNameData data = getHeadNameData(evt.getClickedBlock().getState());

		final String HEAD_DISPLAY;
		if(data.textureKey != null){if(!evt.getPlayer().hasPermission("dropheads.clickinfo.mobs")) return; HEAD_DISPLAY = HEAD_DISPLAY_MOBS;}
		else if(data.player != null){if(!evt.getPlayer().hasPermission("dropheads.clickinfo.players")) return; HEAD_DISPLAY = HEAD_DISPLAY_PLAYERS;}
		else if(data.hdbId != null){if(!evt.getPlayer().hasPermission("dropheads.clickinfo.hdb")) return; HEAD_DISPLAY = HEAD_DISPLAY_HDB;}
		else{if(!evt.getPlayer().hasPermission("dropheads.clickinfo.unknown")) return; HEAD_DISPLAY = HEAD_DISPLAY_UNKNOWN;}
		evt.setCancelled(true); // No more return statements. They will be shown an info message, so cancel the vanilla click

		final TranslationComponent headTypeName = pl.getAPI().getHeadTypeName(data.headType);
		final String englishName = ChatColor.stripColor(
				data.textureKey != null ? pl.getInternalAPI().getFullHeadNameFromKey(data.textureKey, /*customName=*/"").toPlainText() :
					data.player != null && data.player.getName() != null ? data.player.getName() :
					data.profileName != null ? data.profileName.toPlainText() : "<n/a>"
				);
		final String aOrAn = isVowel(englishName.charAt(0)) ? "an" : "a"; // Yes, an imperfect solution, I know. :/

		final ListComponent blob = TellrawUtils.convertHexColorsToComponentsWithReset(HEAD_DISPLAY);
		blob.replaceRawTextWithComponent("${TYPE}", headTypeName);//these 3 are aliases of eachother
		blob.replaceRawTextWithComponent("${HEAD_TYPE}", headTypeName);
		blob.replaceRawTextWithComponent("${SKULL_TYPE}", headTypeName);

		blob.replaceRawTextWithComponent("${A}", new RawTextComponent(aOrAn));
		blob.replaceRawTextWithComponent("${NAME}", data.profileName);
		blob.replaceRawTextWithComponent("${MOB_TYPE}", data.entityTypeNames[0]);
		if(HEAD_DISPLAY.contains("${TEXTURE}") || HEAD_DISPLAY.contains("${BASE64}") || HEAD_DISPLAY.contains("${URL}")){
			String code0 = "";
			if(isPlayerHead){
				GameProfile profile = HeadUtils.getGameProfile((Skull)evt.getClickedBlock().getState());
				Collection<Property> textures = profile.getProperties().get("textures");
				if(textures != null && !textures.isEmpty()) code0 = JunkUtils.getPropertyValue(profile.getProperties().get("textures").iterator().next());
			}
			blob.replaceRawTextWithComponent("${TEXTURE}", new RawTextComponent(code0));
			blob.replaceRawTextWithComponent("${BASE64}", new RawTextComponent(code0));
			if(HEAD_DISPLAY.contains("${URL}") && !code0.isEmpty()){
				String json = new String(Base64.getDecoder().decode(code0))
						.replace(" ", "").replace("\n", "").replace("\r", "").replace("\t", "");
				String url = json.substring(json.indexOf("\"url\":")+7, json.lastIndexOf('"')).trim();
				blob.replaceRawTextWithComponent("${URL}", new RawTextComponent(url, new TextClickAction(ClickEvent.OPEN_URL, url)));
			}
		}

		if(HEAD_DISPLAY.contains("${MOB_SUBTYPES_ASC}")){
			ListComponent subtypeNamesAsc = new ListComponent();
			for(int i=1; i<data.entityTypeNames.length; ++i){
				subtypeNamesAsc.addComponent(data.entityTypeNames[i]);
				/*if(i != data.entityTypeNames.length-1) */subtypeNamesAsc.addComponent(MOB_SUBTYPES_SEPARATOR);
			}
			blob.replaceRawTextWithComponent("${MOB_SUBTYPES_ASC}", subtypeNamesAsc);
		}
		if(HEAD_DISPLAY.contains("${MOB_SUBTYPES_DESC}")){
			ListComponent subtypeNamesDesc = new ListComponent();
			for(int i=data.entityTypeNames.length-1; i>0; --i){
				subtypeNamesDesc.addComponent(data.entityTypeNames[i]);
				/*if(i != 1) */subtypeNamesDesc.addComponent(MOB_SUBTYPES_SEPARATOR);
			}
			blob.replaceRawTextWithComponent("${MOB_SUBTYPES_DESC}", subtypeNamesDesc);
		}
		pl.getServer().dispatchCommand(pl.getServer().getConsoleSender(), "minecraft:tellraw "+evt.getPlayer().getName()+" "+blob.toString());
	}
}