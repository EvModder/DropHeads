package net.evmodder.DropHeads;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import me.arcaniax.hdb.api.DatabaseLoadEvent;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import net.evmodder.EvLib.FileIO;
import net.evmodder.EvLib.extras.EntityUtils;
import net.evmodder.EvLib.extras.HeadUtils;
import net.evmodder.EvLib.extras.TellrawUtils;
import net.evmodder.EvLib.extras.HeadUtils.HeadType;
import net.evmodder.EvLib.extras.TellrawUtils.Component;
import net.evmodder.EvLib.extras.TellrawUtils.Format;
import net.evmodder.EvLib.extras.TellrawUtils.FormatFlag;
import net.evmodder.EvLib.extras.TellrawUtils.RawTextComponent;
import net.evmodder.EvLib.extras.TellrawUtils.ListComponent;
import net.evmodder.EvLib.extras.TellrawUtils.TranslationComponent;
import net.evmodder.EvLib.extras.TextUtils;
import net.evmodder.EvLib.extras.EntityUtils.CCP;

public class HeadAPI {
	final private DropHeads pl;
	private HeadDatabaseAPI hdbAPI = null;
//	private int MAX_HDB_ID = -1;
	final boolean GRUM_ENABLED, SADDLES_ENABLED, HOLLOW_SKULLS_ENABLED, CRACKED_IRON_GOLEMS_ENABLED;
	final boolean UPDATE_PLAYER_HEADS, UPDATE_ZOMBIE_PIGMEN_HEADS/*, SAVE_CUSTOM_LORE*/, SAVE_TYPE_IN_LORE, MAKE_UNSTACKABLE, PREFER_VANILLA_HEADS;
	final TranslationComponent LOCAL_HEAD, LOCAL_SKULL, LOCAL_TOE;
	final HashMap<EntityType, String> headNameFormats;
	final String DEFAULT_HEAD_NAME_FORMAT;
	final TreeMap<String, String> textures; // Key="ENTITY_NAME|DATA", Value="eyJ0ZXh0dXJl..."
	final HashMap<String, TranslationComponent> entitySubtypeNames;

	// TODO: Move these to a localization file
	final String MOB_PREFIX = "mob:", PLAYER_PREFIX = "player:", MHF_PREFIX = "player:", HDB_PREFIX = "hdb:", CODE_PREFIX = "code:";

	HeadAPI(){
		textures = new TreeMap<String, String>();
		pl = DropHeads.getPlugin();
		GRUM_ENABLED = pl.getConfig().getBoolean("drop-grumm-heads", true);
		SADDLES_ENABLED = pl.getConfig().getBoolean("drop-saddled-heads", true);
		HOLLOW_SKULLS_ENABLED = pl.getConfig().getBoolean("hollow-skeletal-skulls", false);
		CRACKED_IRON_GOLEMS_ENABLED = pl.getConfig().getBoolean("cracked-iron-golem-heads", false);
		UPDATE_PLAYER_HEADS = pl.getConfig().getBoolean("update-on-skin-change", true);
		boolean zombifiedPiglensExist = false;
		try{EntityType.valueOf("ZOMBIFIED_PIGLIN"); zombifiedPiglensExist = true;} catch(IllegalArgumentException ex){}
		UPDATE_ZOMBIE_PIGMEN_HEADS = zombifiedPiglensExist &&
				pl.getConfig().getBoolean("update-zombie-pigman-heads",
				pl.getConfig().getBoolean("update-zombie-pigmen-heads", true));
//		SAVE_CUSTOM_LORE = pl.getConfig().getBoolean("save-custom-lore", false);
		SAVE_TYPE_IN_LORE = pl.getConfig().getBoolean("show-head-type-in-lore", false);
		MAKE_UNSTACKABLE = pl.getConfig().getBoolean("make-heads-unstackable", false);
		PREFER_VANILLA_HEADS = pl.getConfig().getBoolean("prefer-vanilla-heads", true);

		//---------- <Load translations> ----------------------------------------------------------------------
		YamlConfiguration translationsFile = FileIO.loadConfig(pl, "translations.yml",
				getClass().getResourceAsStream("/translations.yml"), /*notifyIfNew=*/false);
		YamlConfiguration embeddedTranslationsFile = FileIO.loadConfig(pl, "translations-temp-DELETE.yml",
				getClass().getResourceAsStream("/translations.yml"), false);
		translationsFile.setDefaults(embeddedTranslationsFile);
		FileIO.deleteFile("translations-temp-DELETE.yml");
		LOCAL_HEAD = new TranslationComponent(translationsFile.getString("head-type-names.head", "Head"));
		LOCAL_SKULL = new TranslationComponent(translationsFile.getString("head-type-names.skull", "Skull"));
		LOCAL_TOE = new TranslationComponent(translationsFile.getString("head-type-names.toe", "Toe"));

		headNameFormats = new HashMap<EntityType, String>();
		headNameFormats.put(EntityType.UNKNOWN, "${MOB_SUBTYPES_DESC}${MOB_TYPE} ${HEAD_TYPE}"); // Default for mobs
		headNameFormats.put(EntityType.PLAYER, "${NAME} Head"); // Default for players
		ConfigurationSection entityHeadFormatsConfig = translationsFile.getConfigurationSection("head-name-format");
		if(entityHeadFormatsConfig != null) entityHeadFormatsConfig.getValues(/*deep=*/false)
			.forEach((entityName, entityHeadNameFormat) -> {
				if(entityHeadNameFormat instanceof String == false){
					pl.getLogger().severe("Invalid (non-enclosed-String) value for "+entityName+" in translations.yml: "+entityHeadNameFormat);
				}
				try{
					EntityType eType = EntityType.valueOf(entityName.toUpperCase().replace("DEFAULT", "UNKNOWN"));
					headNameFormats.put(eType, (String)entityHeadNameFormat);
				}
				catch(IllegalArgumentException ex){pl.getLogger().severe("Unknown entity type in 'head-name-format': "+entityName);}
			});
		DEFAULT_HEAD_NAME_FORMAT = headNameFormats.get(EntityType.UNKNOWN);

		entitySubtypeNames = new HashMap<String, TranslationComponent>();
		ConfigurationSection entitySubtypeNamesConfig = translationsFile.getConfigurationSection("entity-subtype-names");
		if(entitySubtypeNamesConfig != null) entitySubtypeNamesConfig.getValues(/*deep=*/false)
			.forEach((subtypeName, localSubtypeName) -> {
			if(localSubtypeName instanceof String == false){
				pl.getLogger().severe("Invalid (non-enclosed-String) value for "+subtypeName+" in translations.yml: "+localSubtypeName);
			}
			entitySubtypeNames.put(subtypeName.toUpperCase(), new TranslationComponent((String)localSubtypeName));
		});
		//---------- </Load translations> ---------------------------------------------------------------------

		String hardcodedList = FileIO.loadResource(pl, "head-textures.txt");
		loadTextures(hardcodedList, /*logMissingEntities=*/true, /*logUnknownEntities=*/false);
//		String localList = FileIO.loadFile("head-textures.txt", hardcodedList);  // This version does not preserve comments
		String localList = FileIO.loadFile("head-textures.txt", getClass().getResourceAsStream("/head-textures.txt"));
		loadTextures(localList, /*logMissingEntities=*/false, /*logUnknownEntities=*/true);

		//TODO: decide whether this feature is worth keeping
		if(pl.getConfig().getBoolean("update-textures", false) && hardcodedList.length() > localList.length()){
			long oldTextureTime = new File(FileIO.DIR+"/head-textures.txt").lastModified();
			long newTextureTime = 0;
			try{
				java.lang.reflect.Method getFileMethod = JavaPlugin.class.getDeclaredMethod("getFile");
				getFileMethod.setAccessible(true);
				newTextureTime = ((File)getFileMethod.invoke(pl)).lastModified();
			}
			catch(NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e1){}
			if(newTextureTime > oldTextureTime) FileIO.saveFile("head-textures.txt", hardcodedList);
		}

		// This could be optimized by passing 'simple-mob-heads-only' to loadTextures to skip adding any textures with '|'
		if(pl.getConfig().getBoolean("simple-mob-heads-only", false)){
			ArrayList<String> allKeys = new ArrayList<>(textures.keySet());
			for(String key : allKeys) if(key.indexOf('|') != -1) textures.remove(key);
		}

		boolean hdbInstalled = true;
		try{Class.forName("me.arcaniax.hdb.api.DatabaseLoadEvent");}
		catch(ClassNotFoundException ex){hdbInstalled = false;}

		if(hdbInstalled) pl.getServer().getPluginManager().registerEvents(new Listener(){
			@EventHandler public void onDatabaseLoad(DatabaseLoadEvent e){
				HandlerList.unregisterAll(this);
				hdbAPI = new HeadDatabaseAPI();
				/*MAX_HDB_ID = JunkUtils.binarySearch(
					id -> {
						try{return api.isHead(""+id);}
						catch(NullPointerException nullpointer){return false;}
					},
					0, Integer.MAX_VALUE
				);*/
			}
		}, pl);
	}

	void loadTextures(String headsList, boolean logMissingEntities, boolean logUnknownEntities){
		HashSet<EntityType> missingHeads = new HashSet<EntityType>();
		HashSet<String> unknownHeads = new HashSet<String>();
		if(logMissingEntities) missingHeads.addAll(Arrays.asList(EntityType.values()).stream()
				.filter(x -> x.isAlive()/* && x.isMeow() */).collect(Collectors.toList()));
		missingHeads.remove(EntityType.PLAYER);
		missingHeads.remove(EntityType.ARMOR_STAND); // These 2 are 'alive', but aren't in head-textures.txt
		for(String head : headsList.split("\n")){
			head = head.replaceAll(" ", "");
			int i = head.indexOf(":");
			if(i != -1){
				String texture = head.substring(i+1).trim();
				if(texture.replace("xxx", "").trim().isEmpty()) continue; //TODO: remove the xxx's

				String key = head.substring(0, i).toUpperCase();
				if(textures.put(key, texture) != null) continue; // Don't bother checking EntityType if this head has already been added

				int j = key.indexOf('|');
				String typeName = (j == -1 ? key : key.substring(0, j));
				try{
					EntityType type = EntityType.valueOf(typeName);
					missingHeads.remove(type);
				}
				catch(IllegalArgumentException ex){
					if(unknownHeads.add(typeName)){
						if(logUnknownEntities) pl.getLogger().warning("Unknown entity '"+typeName+"' in head-textures.txt");
					}
				}
			}
		}
		if(logMissingEntities){
			for(EntityType type : missingHeads){
				pl.getLogger().warning("Missing head texture(s) for "+type);
			}
			if(!missingHeads.isEmpty()){
				pl.getLogger().warning("To fix missing textures, try updating the plugin and then deleting the old head-textures.txt file");
			}
		}
		// Sometimes a texture value is just a reference to a different texture key
		Iterator<Entry<String, String>> it = textures.entrySet().iterator();
		while(it.hasNext()){
			Entry<String, String> e = it.next();
			String redirect = e.getValue();
			while((redirect=textures.get(redirect)) != null) e.setValue(redirect);
		}
	}

	public boolean textureExists(String textureKey){return textures.containsKey(textureKey);}
	public Map<String, String> getTextures(){return Collections.unmodifiableMap(textures);}
	public HeadDatabaseAPI getHeadDatabaseAPI(){return hdbAPI;}//TODO: prefer avoiding public

	Component[] getTypeAndSubtypeNamesFromKey(/*EntityType entity, */String textureKey){
		if(textureKey.equals("PLAYER|GRUMM")) return new Component[]{new RawTextComponent("Grumm")};
		String[] dataFlags = textureKey.split("\\|");
		switch(/*entity != null ? entity.name() : */dataFlags[0]){
			case "TROPICAL_FISH":
				if(dataFlags.length == 2){
					CCP ccp = EntityUtils.getCCP(dataFlags[1]);
//					String name = TextUtils.capitalizeAndSpacify(dataFlags[1], '_');
					return new Component[]{new TranslationComponent(
							"entity.minecraft.tropical_fish.predefined."+EntityUtils.getCommonTropicalFishId(ccp))};
				}
				else if(dataFlags.length > 2) try{
					DyeColor bodyColor = DyeColor.valueOf(dataFlags[1]);
					DyeColor patternColor = dataFlags.length == 3 ? bodyColor : DyeColor.valueOf(dataFlags[2]);
					org.bukkit.entity.TropicalFish.Pattern pattern = org.bukkit.entity.TropicalFish.Pattern
							.valueOf(dataFlags[dataFlags.length == 3 ? 2 : 3]);
					return new Component[]{TellrawUtils.getLocalizedDisplayName(new CCP(bodyColor, patternColor, pattern))};
				}
				catch(IllegalArgumentException e){}
				break;
			case "VILLAGER": case "ZOMBIE_VILLAGER":
				if(textureKey.contains("|NONE")){
					textureKey = textureKey.replace("|NONE", "");
					dataFlags = textureKey.split("\\|");
				}
				break;
			case "SHEEP":
				if(textureKey.contains("|WHITE")){
					textureKey = textureKey.replace("|WHITE", "");
					dataFlags = textureKey.split("\\|");
				}
				break;
			case "OCELOT":
				if(dataFlags.length == 2){
					switch(dataFlags[1]){
						case "WILD_OCELOT": textureKey = "OCELOT"; break;
						case "BLACK_CAT":textureKey = "CAT|BLACK"; break;
						case "RED_CAT": textureKey = "CAT|RED"; break;
						case "SIAMESE_CAT": textureKey = "CAT|SIAMESE"; break;
					}
					dataFlags = textureKey.split("\\|");
				}
				break;
			case "PANDA":
				if(textureKey.contains("|NORMAL")){
					textureKey = textureKey.replace("|NORMAL", "");
					dataFlags = textureKey.split("\\|");
				}
				break;
			case "SKELETON": case "WITHER_SKELETON": case "SKELETON_HORSE": case "STRAY":
				if(textureKey.contains("|HOLLOW")){
					textureKey = textureKey.replace("|HOLLOW", "");
					dataFlags = textureKey.split("\\|");
				}
				break;
			case "IRON_GOLEM":
				if(dataFlags.length == 2){
					textureKey = textureKey
							.replace("|FULL_HEALTH", "")
							.replace("|LOW_CRACKINESS", "|SLIGHTLY_DAMAGED")
							.replace("|MEDIUM_CRACKINESS", "|DAMAGED")
							.replace("|HIGH_CRACKINESS", "|VERY_DAMAGED");
					dataFlags = textureKey.split("\\|");
				}
				break;
			case "RABBIT":
				if(textureKey.equals("RABBIT|THE_KILLER_BUNNY")) textureKey = "THE_KILLER_BUNNY";
				dataFlags = textureKey.split("\\|");
				break;
		}
		Component[] components = new Component[dataFlags.length];
		components[0] = new TranslationComponent("entity.minecraft."+dataFlags[0].replace("UNKNOWN", "PLAYER").toUpperCase());
		for(int i=1; i<dataFlags.length; ++i){
			TranslationComponent subtypeName = entitySubtypeNames.get(dataFlags[i]);
			components[i] = subtypeName != null ? subtypeName : new RawTextComponent(TextUtils.capitalizeAndSpacify(dataFlags[i], /*toSpace=*/'_'));
		}
		return components;
	}

	public TranslationComponent getHeadTypeName(HeadType headType){
		if(headType == null) return LOCAL_HEAD;
		switch(headType){
			case SKULL: return LOCAL_SKULL;
			case TOE: return LOCAL_TOE;
			case HEAD: default: return LOCAL_HEAD;
		}
	}
	public Component getHeadNameFromKey(@Nonnull String textureKey, @Nonnull String customName){
		// Attempt to parse out an EntityType
		EntityType eType;
		int i = textureKey.indexOf('|');
		String nameStr = (i == -1 ? textureKey : textureKey.substring(0, i)).toUpperCase();
		try{eType = EntityType.valueOf(nameStr);}
		catch(IllegalArgumentException ex){
			if(!textures.containsKey(textureKey)){ // If preloaded in textures, it is probably a mob from a different MC version
				pl.getLogger().warning("Unknown EntityType: "+nameStr+"!");
			}
			eType = null;// We will just use textureKey[0]
		}

		// Call the actual getNameFromKey()
		Component[] entityTypeNames = getTypeAndSubtypeNamesFromKey(/*eType, */textureKey);

		String headNameFormat = headNameFormats.getOrDefault(eType, DEFAULT_HEAD_NAME_FORMAT);
		Pattern pattern = Pattern.compile("\\$\\{(NAME|HEAD_TYPE|MOB_TYPE|MOB_SUBTYPES_ASC|MOB_SUBTYPES_DESC)\\}");
		Matcher matcher = pattern.matcher(headNameFormat);
		ArrayList<Component> withComps = new ArrayList<>();
		boolean containsTranslation = false;
		while(matcher.find()){
			if(matcher.group(1).equals("NAME")){
				withComps.add(new RawTextComponent(customName));
				break;
			}
			else{
				containsTranslation = true;
				switch(matcher.group(1)){
					case "HEAD_TYPE":
						withComps.add(getHeadTypeName(HeadUtils.getDroppedHeadType(eType)));
						break;
					case "MOB_TYPE":
						withComps.add(entityTypeNames[0]);
						break;
					case "MOB_SUBTYPES_ASC": {
						ListComponent subtypeNamesAsc = new ListComponent();
						for(int j=1; j<entityTypeNames.length; ++j){
							subtypeNamesAsc.addComponent(entityTypeNames[j]);
							/*if(j != entityTypeNames.length-1) */subtypeNamesAsc.addComponent(" ");
						}
						withComps.add(subtypeNamesAsc);
						break;
					}
					case "MOB_SUBTYPES_DESC": {
						ListComponent subtypeNamesDesc = new ListComponent();
						for(int j=entityTypeNames.length-1; j>0; --j){
							subtypeNamesDesc.addComponent(entityTypeNames[j]);
							/*if(j != 1) */subtypeNamesDesc.addComponent(" ");
						}
						withComps.add(subtypeNamesDesc);
						break;
					}
				}//switch (matcher.group)
			}//else (!="NAME")
		}//while (matcher.find)
		return containsTranslation
			? new TranslationComponent(matcher.replaceAll("%s"), withComps.toArray(new Component[0]),
				/*insert=*/null, /*click=*/null, /*hover=*/null, /*color=*/null, /*formats=*/new FormatFlag(Format.ITALIC, false))
			: new RawTextComponent(headNameFormat.replace("${NAME}", customName),
				/*insert=*/null, /*click=*/null, /*hover=*/null, /*color=*/null, /*formats=*/new FormatFlag(Format.ITALIC, false));
	}

	// TODO: Hide this part of the API (head naming), it is only used in the BlockClickListener.
	public class HeadNameData{
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
			data.entityTypeNames = getTypeAndSubtypeNamesFromKey(EntityType.PLAYER.name());  // "Player"
			data.profileName = data.entityTypeNames[0];
			return data;
		}
		String fullProfileName = profile.getName();
		if(fullProfileName != null/* && SAVE_CUSTOM_LORE*/){
			int idx = fullProfileName.indexOf('>');
			if(idx != -1) profile = new GameProfile(profile.getId(), fullProfileName.substring(0, idx));
		}
		//hdb
		if(hdbAPI != null && (data.hdbId = hdbAPI.getItemID(HeadUtils.getPlayerHead(profile))) != null && hdbAPI.isHead(data.hdbId)){
			data.headType = HeadUtils.getDroppedHeadType(EntityType.UNKNOWN);  // "Head"
			data.entityTypeNames = getTypeAndSubtypeNamesFromKey(EntityType.UNKNOWN.name());  // "Unknown"
			String hdbHeadName = hdbAPI.getItemHead(data.hdbId).getItemMeta().getDisplayName();
			int idx = hdbHeadName.lastIndexOf(' ');
			data.profileName = new RawTextComponent(idx == -1 ? hdbHeadName : hdbHeadName.substring(0, idx));
		}
		//player
		else if(profile.getId() != null && (data.player = pl.getServer().getOfflinePlayer(profile.getId())) != null && data.player.getName() != null){
			data.headType = HeadUtils.getDroppedHeadType(EntityType.PLAYER);  // "Head"
			data.entityTypeNames = getTypeAndSubtypeNamesFromKey(EntityType.PLAYER.name());  // "Player"
			data.profileName = new RawTextComponent(UPDATE_PLAYER_HEADS || profile.getName() == null ? data.player.getName() : profile.getName());
		}
		//mob
		else if(profile.getName() != null && textures.containsKey(profile.getName())){
			data.textureKey = profile.getName();
			int idx = data.textureKey.indexOf('|');
			String eTypeName = (idx == -1 ? data.textureKey : data.textureKey.substring(0, idx)).toUpperCase();
			try{data.headType = HeadUtils.getDroppedHeadType(EntityType.valueOf(eTypeName));}
			catch(IllegalArgumentException ex){data.headType = HeadUtils.getDroppedHeadType(EntityType.UNKNOWN);}  // "Head"
			data.entityTypeNames = getTypeAndSubtypeNamesFromKey(data.textureKey);
			data.profileName = new RawTextComponent(profile.getName());
		}
		//unknown
		else{
			data.headType = HeadUtils.getDroppedHeadType(EntityType.UNKNOWN);  // "Head"
			data.entityTypeNames = getTypeAndSubtypeNamesFromKey(EntityType.UNKNOWN.name());  // "Unknown"
			data.profileName = profile.getName() != null ? new RawTextComponent(profile.getName()) : data.entityTypeNames[0];
		}
		if(data.hdbId != null && !hdbAPI.isHead(data.hdbId)) data.hdbId = null;
		if(data.player != null && data.player.getName() == null) data.player = null;
		return data;
	}
	public HeadNameData getHeadNameData(BlockState skull){
		if(HeadUtils.isPlayerHead(skull.getType())){
			return getHeadNameData(HeadUtils.getGameProfile((Skull)skull));
		}
		//else: creeper, zombie, dragon, (wither)skeleton
		EntityType entityType = HeadUtils.getEntityFromHead(skull.getType());
		HeadNameData data = new HeadNameData();
		data.textureKey = entityType.name();
		data.headType = HeadUtils.getDroppedHeadType(entityType);
		data.entityTypeNames = getTypeAndSubtypeNamesFromKey(entityType.name());
		data.profileName = data.entityTypeNames[0];
		return data;
	}

	ItemStack makeTextureSkull(String textureKey/*, boolean saveTypeInLore, boolean unstackable*/){
		ItemStack item = new ItemStack(Material.PLAYER_HEAD);
		item = JunkUtils.setDisplayName(item, getHeadNameFromKey(textureKey, /*customName=*/""));
		SkullMeta meta = (SkullMeta) item.getItemMeta();

		UUID uuid = UUID.nameUUIDFromBytes(textureKey.getBytes());// Stable UUID for this textureKey
		GameProfile profile = new GameProfile(uuid, textureKey);// Initialized with UUID and name
		String code = textures.get(textureKey);
		if(code != null) profile.getProperties().put("textures", new Property("textures", code));
		if(MAKE_UNSTACKABLE) profile.getProperties().put("random_uuid", new Property("random_uuid", UUID.randomUUID().toString()));
		HeadUtils.setGameProfile(meta, profile);

		if(SAVE_TYPE_IN_LORE){
			int i = textureKey.indexOf('|');
			String entityTypeName = (i == -1 ? textureKey : textureKey.substring(0, i)).toLowerCase();
			meta.setLore(Arrays.asList(ChatColor.DARK_GRAY + MOB_PREFIX + entityTypeName));
		}
		item.setItemMeta(meta);
		return item;
	}

	// Calling HeadUtils.makeSkull(eType) directly is bad.
	@SuppressWarnings("deprecation")
	ItemStack makeSkull_wrapper(EntityType eType){
		ItemStack head = HeadUtils.makeSkull(eType);
		JunkUtils.setDisplayName(head, getHeadNameFromKey(eType.name(), /*customName=*/""));
		if(SAVE_TYPE_IN_LORE){
			SkullMeta meta = (SkullMeta) head.getItemMeta();
			meta.setLore(Arrays.asList(ChatColor.DARK_GRAY + MOB_PREFIX + eType.name().toLowerCase()));
			head.setItemMeta(meta);
		}
		if(MAKE_UNSTACKABLE){
			SkullMeta meta = (SkullMeta) head.getItemMeta();
			GameProfile profile = HeadUtils.getGameProfile(meta);
			profile.getProperties().put("random_uuid", new Property("random_uuid", UUID.randomUUID().toString()));
			HeadUtils.setGameProfile(meta, profile);
			head.setItemMeta(meta);
		}
		return head;
	}
	// Calling hdbAPI.getItemHead(id) directly is bad.
	ItemStack getItemHead_wrapper(String hdbId){
		ItemStack hdbHead = hdbAPI.getItemHead(hdbId);
		GameProfile profile = HeadUtils.getGameProfile((SkullMeta)hdbHead.getItemMeta());
		ItemStack head = HeadUtils.getPlayerHead(profile);
		SkullMeta meta = (SkullMeta)head.getItemMeta();
		meta.setDisplayName(hdbHead.getItemMeta().getDisplayName());
		if(SAVE_TYPE_IN_LORE){
			meta.setLore(Arrays.asList(ChatColor.DARK_GRAY + HDB_PREFIX + hdbId));
		}
		if(MAKE_UNSTACKABLE){
			profile.getProperties().put("random_uuid", new Property("random_uuid", UUID.randomUUID().toString()));
			HeadUtils.setGameProfile(meta, profile);
		}
		head.setItemMeta(meta);
		return head;
	}

	/**
	 * Get a custom head from an entity type and texture key (e.g., FOX|SNOW|SLEEPING)
	 * @param type The entity type for the head
	 * @param textureKey The texture key for the head
	 * @return The result head ItemStack
	 */
	public ItemStack getHead(EntityType eType, String textureKey/*, boolean saveTypeInLore, boolean unstackable*/){
		// If there is extra "texture metadata" (aka '|') we should return the custom skull
		if(textureKey != null){
			// Try successively smaller texture keys until we find one that exists
			int keyDataTagIdx=textureKey.lastIndexOf('|');
			while(keyDataTagIdx != -1 && !textures.containsKey(textureKey)){
				textureKey = textureKey.substring(0, keyDataTagIdx);
				keyDataTagIdx=textureKey.lastIndexOf('|');
			}
			// If this is a custom data head (still contains a '|') or eType is null AND the key exists, use it
			boolean hasCustomData = textureKey.replace("|HOLLOW", "").indexOf('|') != -1;
			if((hasCustomData || eType == null || eType == EntityType.UNKNOWN || !PREFER_VANILLA_HEADS) && textures.containsKey(textureKey)){
				return makeTextureSkull(textureKey/*, saveTypeInLore*/);
			}
		}
		if(eType == null) return null;
		// Otherwise, favor vanilla skulls
		if(!PREFER_VANILLA_HEADS && eType != EntityType.PLAYER) return null;
		switch(eType){
			case PLAYER:
				return new ItemStack(Material.PLAYER_HEAD);
			case WITHER_SKELETON:
				return new ItemStack(Material.WITHER_SKELETON_SKULL);
			case SKELETON:
				return new ItemStack(Material.SKELETON_SKULL);
			case ZOMBIE:
				return new ItemStack(Material.ZOMBIE_HEAD);
			case CREEPER:
				return new ItemStack(Material.CREEPER_HEAD);
			case ENDER_DRAGON:
				return new ItemStack(Material.DRAGON_HEAD);
			default:
				if(textures.containsKey(eType.name())) return makeTextureSkull(eType.name());
				return makeSkull_wrapper(eType);
			}
	}

	/**
	 * Get a custom head from an Entity
	 * @param entity The entity for which to to create a head
	 * @return The result head ItemStack
	 */
	public ItemStack getHead(Entity entity/*, boolean saveTypeInLore, boolean unstackable*/){
		if(entity.getType() == EntityType.PLAYER){
			OfflinePlayer player = (OfflinePlayer)entity;
			return getHead(new GameProfile(player.getUniqueId(), player.getName()));
		}
		String textureKey = TextureKeyLookup.getTextureKey(entity);
		if(!SADDLES_ENABLED && textureKey.endsWith("|SADDLED")) textureKey = textureKey.substring(0, textureKey.length()-8);
		if(CRACKED_IRON_GOLEMS_ENABLED && entity.getType() == EntityType.IRON_GOLEM) textureKey += "|HIGH_CRACKINESS";
		if(HOLLOW_SKULLS_ENABLED && EntityUtils.isSkeletal(entity.getType())) textureKey += "|HOLLOW";
		if(GRUM_ENABLED && HeadUtils.hasGrummName(entity)) textureKey += "|GRUMM";
		ItemStack head = getHead(entity.getType(), textureKey);
//		if(head.getDisplayName().contains("${NAME}")){
//			replace ${NAME} with entity.getCustomName() in item display name
//		}
		return head;
	}

	/**
	 * Get a custom head from a GameProfile
	 * @param profile The profile information to create a head
	 * @return The result head ItemStack
	 */
	public ItemStack getHead(GameProfile profile/*, boolean saveTypeInLore, boolean unstackable*/){
		if(profile == null) return null;
		String profileName = profile.getName();
		if(profileName != null){
			/*if(SAVE_CUSTOM_LORE){*/int idx = profileName.indexOf('>'); if(idx != -1) profileName = profileName.substring(0, idx);/*}*/
			if(textures.containsKey(profileName)){//Refresh this EntityHead texture
				if(UPDATE_ZOMBIE_PIGMEN_HEADS && profileName.startsWith("PIG_ZOMBIE")){
					profileName = /*profileName.replace("PIG_ZOMBIE", */"ZOMBIFIED_PIGLIN"/*)*/;
				}
				if(profileName.equals("OCELOT|WILD_OCELOT")) profileName = "OCELOT";
				return makeTextureSkull(profileName);
			}
		}
		if(UPDATE_PLAYER_HEADS && profile.getId() != null){
			OfflinePlayer p = pl.getServer().getOfflinePlayer(profile.getId());
			if(p != null && p.getName() != null) profileName = p.getName();
		}
		ItemStack head = HeadUtils.getPlayerHead(profile);
		if(hdbAPI != null){
			String id = hdbAPI.getItemID(head);
			if(id != null && hdbAPI.isHead(id)) return getItemHead_wrapper(id);
		}
		SkullMeta meta = (SkullMeta)head.getItemMeta();
		if(profileName != null){
			if(profileName.startsWith("MHF_")) meta.setDisplayName(ChatColor.YELLOW+profileName);
			else{
				head = JunkUtils.setDisplayName(head, getHeadNameFromKey("PLAYER", /*customName=*/profileName));
				meta = (SkullMeta)head.getItemMeta();
			}
			if(SAVE_TYPE_IN_LORE) meta.setLore(Arrays.asList(
					ChatColor.DARK_GRAY + (profileName.startsWith("MHF_") ? MHF_PREFIX : PLAYER_PREFIX) + profileName));
			head.setItemMeta(meta);
		}
		if(MAKE_UNSTACKABLE){
			profile.getProperties().put("random_uuid", new Property("random_uuid", UUID.randomUUID().toString()));
			HeadUtils.setGameProfile(meta, profile);
			head.setItemMeta(meta);
		}
		return head;
	}

	/**
	 * Get a custom head from a Base64 code and display name
	 * @param code The Base64 encoded skin texture URL
	 * @param name The display name to use on the result head ItemStack
	 * @return The result head ItemStack
	 */
	public ItemStack getHead(byte[] code, String name){
		ItemStack head = HeadUtils.makeSkull(new String(code), name);
		if(SAVE_TYPE_IN_LORE){
			ItemMeta meta = head.getItemMeta();
			meta.setLore(Arrays.asList(ChatColor.DARK_GRAY+CODE_PREFIX+code));
			head.setItemMeta(meta);
		}
		if(MAKE_UNSTACKABLE){
			SkullMeta meta = (SkullMeta) head.getItemMeta();
			GameProfile profile = HeadUtils.getGameProfile(meta);
			profile.getProperties().put("random_uuid", new Property("random_uuid", UUID.randomUUID().toString()));
			HeadUtils.setGameProfile(meta, profile);
			head.setItemMeta(meta);
		}
		return head;
	}
}