package net.evmodder.DropHeads;

import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
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
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import me.arcaniax.hdb.api.DatabaseLoadEvent;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import net.evmodder.DropHeads.JunkUtils.NoteblockMode;
import net.evmodder.EvLib.FileIO;
import net.evmodder.EvLib.extras.EntityUtils;
import net.evmodder.EvLib.extras.HeadUtils;
import net.evmodder.EvLib.extras.TellrawUtils;
import net.evmodder.EvLib.extras.HeadUtils.HeadType;
import net.evmodder.EvLib.extras.TellrawUtils.Component;
import net.evmodder.EvLib.extras.TellrawUtils.Format;
import net.evmodder.EvLib.extras.TellrawUtils.RawTextComponent;
import net.evmodder.EvLib.extras.TellrawUtils.TranslationComponent;
import net.evmodder.EvLib.extras.TextUtils;
import net.evmodder.EvLib.extras.WebUtils;

/** Public API for general DropHeads features.
 * Warning: Functions may change or disappear in future releases
 */
public class HeadAPI{
	private final DropHeads pl;
	protected HeadDatabaseAPI hdbAPI = null;
	protected final Configuration translationsFile;
//	private final int MAX_HDB_ID = -1;
	private final boolean GRUMM_ENABLED, SIDEWAYS_SHULKERS_ENABLED, COLORED_COLLARS_ENABLED, SADDLES_ENABLED; // may trigger additional file downloads.
	private final boolean HOLLOW_SKULLS_ENABLED, TRANSPARENT_SLIME_ENABLED, CRACKED_IRON_GOLEMS_ENABLED, USE_PRE_JAPPA, USE_OLD_VEX, USE_OLD_BAT;
	private final boolean LOCK_PLAYER_SKINS/*, SAVE_CUSTOM_LORE*/, SAVE_TYPE_IN_LORE, MAKE_UNSTACKABLE, PREFER_VANILLA_HEADS, USE_TRANSLATE_FALLBACKS;
	private final boolean ASYNC_PROFILE_REQUESTS;
	private final String UNKNOWN_TEXTURE_CODE;
	private final TranslationComponent LOCAL_HEAD, LOCAL_SKULL, LOCAL_TOE;
	private final HashMap<EntityType, /*headNameFormat=*/String> headNameFormats;
	private final HashMap</*textureKey=*/String, /*headNameFormat=*/String> exactTextureKeyHeadNameFormats;
	private final String DEFAULT_HEAD_NAME_FORMAT, MOB_SUBTYPES_SEPARATOR;
	private final TreeMap<String, String> textures; // Key="ENTITY_NAME|DATA", Value="eyJ0ZXh0dXJl..."
	private final HashMap<String, TranslationComponent> entitySubtypeNames;
	private final HashMap</*textureKey=*/String, Sound> nbSounds;
	private final HashMap<String, String> replaceHeadsFromTo; // key & value are textureKeys
	private final Material PIGLIN_HEAD_TYPE;
	private final static String TEXTURE_DL_URL = "https://raw.githubusercontent.com/EvModder/DropHeads/master/extra-textures/";
	private final static String DH_TEXTURE_KEY = "dh_key";
	private final static String DH_RANDOM_UUID = "random_uuid";

	private final String PLAYER_PREFIX, MOB_PREFIX, MHF_PREFIX, HDB_PREFIX, CODE_PREFIX;

	void loadTextures(String headsList, boolean logMissingEntities, boolean logUnknownEntities){
		final HashSet<EntityType> missingHeads = new HashSet<EntityType>();
		final HashSet<String> unknownHeads = new HashSet<String>();
		if(logMissingEntities) missingHeads.addAll(Arrays.asList(EntityType.values()).stream()
				.filter(x -> x.isAlive()/* && x.isMeow() */).collect(Collectors.toList()));
		missingHeads.remove(EntityType.PLAYER);
		missingHeads.remove(EntityType.ARMOR_STAND); // These 2 are 'alive', but aren't in head-textures.txt
		for(String head : headsList.split("\n")){
			head = head.replaceAll(" ", "");
			final int i = head.indexOf(":");
			if(i != -1){
				String texture = head.substring(i+1).trim();
				if(texture.replace("xxx", "").isEmpty()) continue; //TODO: remove the xxx's
				if(texture.length() > 50 && texture.length() < 80 //TODO: usually exactly 64
						&& texture.chars().allMatch(ch -> (ch >= 'a' && ch <= 'f') || (ch >= '0' && ch <= '9'))){
					// Convert from Mojang server texture id to Base64-encoded json
					texture = Base64.getEncoder().encodeToString(
							("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/"+texture+"\"}}}")
								.getBytes(StandardCharsets.ISO_8859_1));
				}

				final String key = head.substring(0, i).toUpperCase();
				//TODO: duplicate texture warning?
				if(textures.put(key, texture) != null) continue; // Don't bother checking EntityType if this head has already been added

				final int j = key.indexOf('|');
				final String eTypeName = (j == -1 ? key : key.substring(0, j)).toUpperCase();
				try{
					missingHeads.remove(EntityType.valueOf(eTypeName));
				}
				catch(IllegalArgumentException e1){
					try{
						// Currently only applies for PIG_ZOMBIE
						missingHeads.remove(EntityType.valueOf(replaceHeadsFromTo.getOrDefault(eTypeName, eTypeName)));
					}
					catch(IllegalArgumentException e2){
						if(unknownHeads.add(eTypeName)){
							if(logUnknownEntities) pl.getLogger().warning("Unknown EntityType in 'head-textures.txt': "+eTypeName);
						}
					}
				}
			}
		}
		if(logMissingEntities){
			for(EntityType type : missingHeads){
				pl.getLogger().warning("Missing head texture(s) for "+type);
			}
			if(!missingHeads.isEmpty()){
				pl.getLogger().warning("To fix missing textures, update the plugin then delete the old 'head-textures.txt' file");
			}
		}
		// Sometimes a texture value is just a reference to a different texture key
		Iterator<Entry<String, String>> it = textures.entrySet().iterator();
		while(it.hasNext()){
			Entry<String, String> e = it.next();
			String redirect = e.getValue();
			for(int i=0; i<5 && (redirect=textures.get(redirect)) != null; ++i) e.setValue(redirect);
		}
	}

	HeadAPI(NoteblockMode m){
		textures = new TreeMap<String, String>();
		pl = DropHeads.getPlugin();
		GRUMM_ENABLED = pl.getConfig().getBoolean("drop-grumm-heads", false);
		SIDEWAYS_SHULKERS_ENABLED = pl.getConfig().getBoolean("drop-sideways-shulker-heads", true);
		COLORED_COLLARS_ENABLED = pl.getConfig().getBoolean("drop-collared-heads", true);
		SADDLES_ENABLED = pl.getConfig().getBoolean("drop-saddled-heads", true);
		HOLLOW_SKULLS_ENABLED = pl.getConfig().getBoolean("hollow-skeletal-skulls", false);
		TRANSPARENT_SLIME_ENABLED = pl.getConfig().getBoolean("transparent-slime-heads", false);
		CRACKED_IRON_GOLEMS_ENABLED = pl.getConfig().getBoolean("cracked-iron-golem-heads", false);
		USE_PRE_JAPPA = pl.getConfig().getBoolean("use-legacy-head-textures", false) || Bukkit.getBukkitVersion().compareTo("1.14") < 0; // v<1.14
		USE_OLD_VEX = pl.getConfig().getBoolean("use-1.19.2-vex-head-textures", false) || Bukkit.getBukkitVersion().compareTo("1.19.3") < 0; // v<1.19.3
		USE_OLD_BAT = pl.getConfig().getBoolean("use-1.20.2-bat-head-textures", false) || Bukkit.getBukkitVersion().compareTo("1.20.3") < 0; // v<1.20.3
		LOCK_PLAYER_SKINS = !pl.getConfig().getBoolean("update-on-skin-change", true);
		ASYNC_PROFILE_REQUESTS = pl.getConfig().getBoolean("async-offline-profile-requests", false);
		{
			Material piglinHeadType = null;
			if(pl.getConfig().getBoolean("update-piglin-heads", true)){
				try{piglinHeadType = Material.valueOf("PIGLIN_HEAD");} catch(IllegalArgumentException ex){}
			}
			PIGLIN_HEAD_TYPE = piglinHeadType;
		}
		boolean zombifiedPiglensExist = false;
		try{EntityType.valueOf("ZOMBIFIED_PIGLIN"); zombifiedPiglensExist = true;} catch(IllegalArgumentException ex){}
		final boolean UPDATE_ZOMBIE_PIGMEN_HEADS = zombifiedPiglensExist &&
				pl.getConfig().getBoolean("update-zombie-pigman-heads",
				pl.getConfig().getBoolean("update-zombie-pigmen-heads", false));
		replaceHeadsFromTo = new HashMap<String, String>();
		replaceHeadsFromTo.put("OCELOT|WILD_OCELOT", "OCELOT");
		if(UPDATE_ZOMBIE_PIGMEN_HEADS){
			replaceHeadsFromTo.put("PIG_ZOMBIE", "ZOMBIFIED_PIGLIN");
			replaceHeadsFromTo.put("PIG_ZOMBIE|BABY", "ZOMBIFIED_PIGLIN");
			replaceHeadsFromTo.put("PIG_ZOMBIE|PRE_JAPPA", "ZOMBIFIED_PIGLIN");
			replaceHeadsFromTo.put("PIG_ZOMBIE|GRUMM", "ZOMBIFIED_PIGLIN|GRUMM");
			replaceHeadsFromTo.put("PIG_ZOMBIE|BABY|GRUMM", "ZOMBIFIED_PIGLIN|GRUMM");
			replaceHeadsFromTo.put("PIG_ZOMBIE|PRE_JAPPA|GRUMM", "ZOMBIFIED_PIGLIN|GRUMM");
		}
		ConfigurationSection replaceHeadsList = pl.getConfig().getConfigurationSection("replace-heads");
		if(replaceHeadsList != null) for(String fromHead : replaceHeadsList.getKeys(false)){
			replaceHeadsFromTo.put(fromHead, /*toHead=*/replaceHeadsList.getString(fromHead));
		}
//		SAVE_CUSTOM_LORE = pl.getConfig().getBoolean("save-custom-lore", false);
		SAVE_TYPE_IN_LORE = pl.getConfig().getBoolean("show-head-type-in-lore", false);
		MAKE_UNSTACKABLE = pl.getConfig().getBoolean("make-heads-unstackable", false);
		PREFER_VANILLA_HEADS = pl.getConfig().getBoolean("prefer-vanilla-heads", true);

		//---------- <Load translations> ----------------------------------------------------------------------
		translationsFile = FileIO.loadConfig(pl, "translations.yml",
				getClass().getResourceAsStream("/translations.yml"), /*notifyIfNew=*/false);
		Configuration embeddedTranslationsFile = YamlConfiguration.loadConfiguration(
				new InputStreamReader(getClass().getResourceAsStream("/translations.yml")));
		translationsFile.setDefaults(embeddedTranslationsFile);
		// See if we can use the 1.19.4+ "fallback" feature
		USE_TRANSLATE_FALLBACKS = translationsFile.getBoolean("use-translation-fallbacks", false) && Bukkit.getBukkitVersion().compareTo("1.19.4") >= 0;

		if(USE_TRANSLATE_FALLBACKS){
			LOCAL_HEAD = new TranslationComponent("head_type.head", translationsFile.getString("head-type-names.head"));
			LOCAL_SKULL = new TranslationComponent("head_type.skull", translationsFile.getString("head-type-names.skull", "Skull"));
			LOCAL_TOE = new TranslationComponent("head_type.toe", translationsFile.getString("head-type-names.toe", "Toe"));
		}
		else{
			//RawText?
			LOCAL_HEAD = new TranslationComponent(translationsFile.getString("head-type-names.head"));
			LOCAL_SKULL = new TranslationComponent(translationsFile.getString("head-type-names.skull", "Skull"));
			LOCAL_TOE = new TranslationComponent(translationsFile.getString("head-type-names.toe", "Toe"));
		}

		MOB_SUBTYPES_SEPARATOR = translationsFile.getString("mob-subtype-separator", " ");
		exactTextureKeyHeadNameFormats = new HashMap<String, String>();
		ConfigurationSection textureKeyHeadFormatsConfig = translationsFile.getConfigurationSection("texturekey-head-name-format");
		if(textureKeyHeadFormatsConfig != null) textureKeyHeadFormatsConfig.getValues(/*deep=*/false)
			.forEach((textureKey, textureKeyHeadNameFormat) -> {
				if(textureKeyHeadNameFormat instanceof String == false){
					pl.getLogger().severe("Invalid (non-enclosed-String) value for "+textureKey+" in 'translations.yml': "+textureKeyHeadNameFormat);
				}
				exactTextureKeyHeadNameFormats.put(textureKey.toUpperCase(), (String)textureKeyHeadNameFormat);
			});
		headNameFormats = new HashMap<EntityType, String>();
		headNameFormats.put(EntityType.UNKNOWN, "${MOB_SUBTYPES_DESC}${MOB_TYPE} ${HEAD_TYPE}"); // Default for mobs
		headNameFormats.put(EntityType.PLAYER, "block.minecraft.player_head.named"); // Default for players
		ConfigurationSection entityHeadFormatsConfig = translationsFile.getConfigurationSection("head-name-format");
		if(entityHeadFormatsConfig != null) entityHeadFormatsConfig.getValues(/*deep=*/false)
			.forEach((entityName, entityHeadNameFormat) -> {
				if(entityHeadNameFormat instanceof String == false){
					pl.getLogger().severe("Invalid (non-enclosed-String) value for "+entityName+" in 'translations.yml': "+entityHeadNameFormat);
				}
				try{
					EntityType eType = EntityType.valueOf(entityName.toUpperCase().replace("DEFAULT", "UNKNOWN"));
					headNameFormats.put(eType, (String)entityHeadNameFormat);
				}
				catch(IllegalArgumentException ex){pl.getLogger().severe("Unknown EntityType in 'translations.yml' | head-name-format: "+entityName);}
			});
		DEFAULT_HEAD_NAME_FORMAT = headNameFormats.get(EntityType.UNKNOWN);

		entitySubtypeNames = new HashMap<String, TranslationComponent>();
		ConfigurationSection entitySubtypeNamesConfig = translationsFile.getConfigurationSection("entity-subtype-names");
		if(entitySubtypeNamesConfig != null) entitySubtypeNamesConfig.getValues(/*deep=*/false)
			.forEach((subtypeName, localSubtypeName) -> {
			if(localSubtypeName instanceof String == false){
				pl.getLogger().severe("Invalid (non-enclosed-String) value for "+subtypeName+" in 'translations.yml': "+localSubtypeName);
			}
			TranslationComponent comp = new TranslationComponent(/*jsonKey=*/(String)localSubtypeName);
			if(USE_TRANSLATE_FALLBACKS && comp.toPlainText().equals((String)localSubtypeName)){
				comp = new TranslationComponent(/*jsonKey=*/"entity_subtype."+subtypeName, /*fallback=*/(String)localSubtypeName);
			}//RawText if fallbacks==false && equals==true?
			entitySubtypeNames.put(subtypeName.toUpperCase(), comp);
		});
		if(SAVE_TYPE_IN_LORE){
			PLAYER_PREFIX = translationsFile.getString("head-type-in-lore.player");
			MOB_PREFIX = translationsFile.getString("head-type-in-lore.mob");
			MHF_PREFIX = translationsFile.getString("head-type-in-lore.mhf");
			HDB_PREFIX = translationsFile.getString("head-type-in-lore.hdb");
			CODE_PREFIX = translationsFile.getString("head-type-in-lore.code");
		}
		else PLAYER_PREFIX = MOB_PREFIX = MHF_PREFIX = HDB_PREFIX = CODE_PREFIX = null;
		//---------- </Load translations> ---------------------------------------------------------------------

		//---------- <Load Textures> ----------------------------------------------------------------------
		final String hardcodedList = FileIO.loadResource(pl, "head-textures.txt", /*defaultContent=*/"");
//		String localList = FileIO.loadFile("head-textures.txt", hardcodedList);  // This does not preserve comments
		String localList = FileIO.loadFile("head-textures.txt", getClass().getResourceAsStream("/head-textures.txt"));
		UNKNOWN_TEXTURE_CODE = textures.getOrDefault(EntityType.UNKNOWN.name(), "5c90ca5073c49b898a6f8cdbc72e6aca0a425ec83bc4355e3b834fd859282bdd");

		boolean writeTextureFile = false, updateTextures = false;
		if(pl.getConfig().getBoolean("update-textures", false)){
			long oldTextureTime = new File(FileIO.DIR+"/head-textures.txt").lastModified();
			long newTextureTime = 0;
			try{
				java.lang.reflect.Method getFileMethod = JavaPlugin.class.getDeclaredMethod("getFile");
				getFileMethod.setAccessible(true);
				newTextureTime = ((File)getFileMethod.invoke(pl)).lastModified();
			}
			catch(NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e1){}
			if(newTextureTime > oldTextureTime){
				localList=hardcodedList;
				writeTextureFile = true;
				updateTextures = true;
			}
		}
		if(SIDEWAYS_SHULKERS_ENABLED && (!localList.contains("SHULKER|SIDE_LEFT") || updateTextures)){
			pl.getLogger().info("Downloading sideways-shulker head textures...");
			final String shulkers = WebUtils.getReadURL(TEXTURE_DL_URL+"sideways-shulker-head-textures.txt");
			localList += "\n" + shulkers;//JunkUtils.stripComments(shulkers);
			writeTextureFile = true;
			pl.getLogger().info("Finished downloading sideways-shulker head textures");
		}
		if(COLORED_COLLARS_ENABLED && (!localList.contains("|RED_COLLARED:") || updateTextures)){
			pl.getLogger().info("Downloading colored-collar head textures...");
			final String collared = WebUtils.getReadURL(TEXTURE_DL_URL+"colored-collar-head-textures.txt");
			localList += "\n" + collared;//JunkUtils.stripComments(collared);
			writeTextureFile = true;
			pl.getLogger().info("Finished downloading colored-collar head textures");
		}
		if(USE_PRE_JAPPA && (!localList.contains("|PRE_JAPPA:") || updateTextures)){
			pl.getLogger().info("Downloading pre-JAPPA head textures...");
			final String preJappas = WebUtils.getReadURL(TEXTURE_DL_URL+"pre-jappa-head-textures.txt");
			localList += "\n" + preJappas;//JunkUtils.stripComments(preJappas);
			writeTextureFile = true;
			pl.getLogger().info("Finished downloading pre-JAPPA head textures");
		}
		if(GRUMM_ENABLED && (!localList.contains("|GRUMM:") || updateTextures)){
			pl.getLogger().info("Downloading Grumm head textures...");
			final String grumms = WebUtils.getReadURL(TEXTURE_DL_URL+"grumm-head-textures.txt");
			localList += "\n" + grumms;//JunkUtils.stripComments(grumms);
			writeTextureFile = true;
			pl.getLogger().info("Finished downloading Grumm head textures");
		}
		if(writeTextureFile) FileIO.saveFile("head-textures.txt", localList);
		if(!hardcodedList.isEmpty()) loadTextures(hardcodedList, /*logMissingEntities=*/true, /*logUnknownEntities=*/false);
		loadTextures(localList, /*logMissingEntities=*/hardcodedList.isEmpty(), /*logUnknownEntities=*/true);
		//---------- </Load Textures> ---------------------------------------------------------------------

		// This could be optimized by passing 'simple-mob-heads-only' to loadTextures to skip adding any textures with '|'
		if(pl.getConfig().getBoolean("simple-mob-heads-only", false)){
			ArrayList<String> allKeys = new ArrayList<>(textures.keySet());
			for(String key : allKeys) if(key.indexOf('|') != -1) textures.remove(key);
		}

		if(m == NoteblockMode.ITEM_META && Bukkit.getBukkitVersion().compareTo("1.19.4") >= 0){
			nbSounds = JunkUtils.getNoteblockSounds();
		}
		else nbSounds = null;

		boolean hdbInstalled = true;
		try{Class.forName("me.arcaniax.hdb.api.DatabaseLoadEvent");}
		catch(Exception ex){hdbInstalled = false;}

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

	/** Check if a texture exists for the given key.
	 * @param textureKey The texture key to check for
	 * @return Whether the texture exists
	 */
	public boolean textureExists(String textureKey){return textures.containsKey(textureKey);}
	/** Get a map of all existing textures.
	 * @return An unmodifiable map (textureKey => Base64 encoded texture URL)
	 */
	public Map<String, String> getTextures(){return Collections.unmodifiableMap(textures);}

	/** Extract the textureKey from a DropHeads mob head.
	 * @param profile the GameProfile of a head
	 * @return a String representing the textureKey or <code>null</code> if not a DH mob head
	 */
	public String getTextureKey(GameProfile profile){
		if(profile == null) return null;
		if(profile.getProperties() != null && profile.getProperties().containsKey(DH_TEXTURE_KEY)){
			final Collection<Property> props = profile.getProperties().get(DH_TEXTURE_KEY);
			if(props != null && !props.isEmpty()){
				if(props.size() != 1) pl.getLogger().warning("Multiple texture keys on a single head profile in getTextureKey()");
				return JunkUtils.getPropertyValue(props.iterator().next());
			}
		}
		if(profile.getName() != null){
			String name = profile.getName();
			int startIdx = name.startsWith(JunkUtils.TXT_KEY_PROFILE_NAME_PREFIX) ? JunkUtils.TXT_KEY_PROFILE_NAME_PREFIX.length() : 0;
			int endIdx = name.indexOf('>');
			name = name.substring(startIdx, endIdx == -1 ? name.length() : endIdx);
			if(textureExists(name)) return name;
		}
		return null;
	}

	private String trimTextureKeyUntilFound(String textureKey){
		textureKey = replaceHeadsFromTo.getOrDefault(textureKey, textureKey);

		// Try successively smaller texture keys until we find one that exists
		int keyDataTagIdx=textureKey.lastIndexOf('|');
		final boolean wasGrumm = textureKey.endsWith("|GRUMM");//TODO: remove this hacky workaround once the 3,584 trop fish are added
		while(keyDataTagIdx != -1 && !textures.containsKey(textureKey)){
			/*if(DEBUG_MODE) */pl.getLogger().warning("Unable to find key in 'head-textures.txt': "+textureKey);
			textureKey = textureKey.substring(0, keyDataTagIdx);
			keyDataTagIdx=textureKey.lastIndexOf('|');
		}
		if(wasGrumm && textures.containsKey(textureKey+"|GRUMM")) textureKey += "|GRUMM";//TODO: remove once the 3,584 trop fish are added
		return textureKey;
	}

	/** Returns a localized TranslationComponent name for a given HeadType.
	 * @param headType one of: <code>{HEAD, SKULL, TOE}</code>
	 * @return a localized translation component
	 */
	public TranslationComponent getHeadTypeName(HeadType headType){ //only used by BlockClickListener
		if(headType == null) return LOCAL_HEAD;
		switch(headType){
			case SKULL: return LOCAL_SKULL;
			case TOE: return LOCAL_TOE;
			case HEAD: default: return LOCAL_HEAD;
		}
	}

	//only used by BlockClickListener
	/** Returns a localized Component[] where the first element is the entity's type and subsequent elements describe the sub-types.
	 * The ordering of sub-types is the same as their corresponding names in <code>textureKey.split("|")</code>.
	 * @param textureKey a texture key describing the entity
	 * @return localized components for type name and sub-type names
	 */
	public Component[] getEntityTypeAndSubtypeNamesFromKey(/*EntityType entity, */String textureKey){
		if(textureKey.equals("PLAYER|GRUMM")) return new Component[]{new RawTextComponent("Grumm")};
		String[] dataFlags = textureKey.split("\\|");
		switch(/*entity != null ? entity.name() : */dataFlags[0]){
			case "TROPICAL_FISH":
				if(dataFlags.length == 2 && !dataFlags[1].equals("GRUMM")){
					pl.getLogger().info("pccInt for red lipped blenny: "+
				EntityUtils.intFromPCC(org.bukkit.entity.TropicalFish.Pattern.SNOOPER, DyeColor.GRAY, DyeColor.RED));
//					String name = TextUtils.capitalizeAndSpacify(dataFlags[1], '_');
					return new Component[]{new TranslationComponent(
							"entity.minecraft.tropical_fish.predefined."+EntityUtils.getTropicalFishId(/*common_name=*/dataFlags[1]))};
				}
				else if(dataFlags.length > 2) try{
					DyeColor bodyColor = DyeColor.valueOf(dataFlags[1]);
					DyeColor patternColor = dataFlags.length == 3 ? bodyColor : DyeColor.valueOf(dataFlags[2]);
					org.bukkit.entity.TropicalFish.Pattern pattern = org.bukkit.entity.TropicalFish.Pattern
							.valueOf(dataFlags[dataFlags.length == 3 ? 2 : 3]);
					return new Component[]{TellrawUtils.getLocalizedDisplayNameForTropicalFish(EntityUtils.intFromPCC(pattern, bodyColor, patternColor))};
				}
				catch(IllegalArgumentException e){}
				break;
			case "VILLAGER": case "ZOMBIE_VILLAGER":
				if(textureKey.contains("|NONE")){
					textureKey = textureKey.replace("|NONE", "");
					dataFlags = textureKey.split("\\|");
				}
				break;
//			case "SHEEP":
//				if(textureKey.contains("|WHITE")){
//					textureKey = textureKey.replace("|WHITE", "");
//					dataFlags = textureKey.split("\\|");
//				}
//				break;
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
//			case "PANDA":
//				if(textureKey.contains("|NORMAL")){
//					textureKey = textureKey.replace("|NORMAL", "");
//					dataFlags = textureKey.split("\\|");
//				}
//				break;
			case "SKELETON": case "WITHER_SKELETON": case "SKELETON_HORSE": case "STRAY":
				if(textureKey.contains("|HOLLOW")){
					textureKey = textureKey.replace("|HOLLOW", "");
					dataFlags = textureKey.split("\\|");
				}
				break;
			case "SLIME":
				if(textureKey.contains("|TRANSPARENT")){
					textureKey = textureKey.replace("|TRANSPARENT", "");
					dataFlags = textureKey.split("\\|");
				}
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
				if(textureKey.contains("RABBIT|THE_KILLER_BUNNY")){
					textureKey = textureKey.replace("RABBIT|THE_KILLER_BUNNY", "entity.minecraft.killer_bunny");
					dataFlags = textureKey.split("\\|");
				}
				break;
			case "BOAT":
				if(dataFlags.length > 1 && !dataFlags[1].equals("GRUMM")){
					if(dataFlags[1].equals("BAMBOO")) textureKey = textureKey.replace("BOAT|BAMBOO", "item.minecraft.bamboo_raft");
					else textureKey = textureKey.replaceAll("BOAT\\|(\\w+)", "item.minecraft.$1_boat").toLowerCase();
					dataFlags = textureKey.split("\\|");
				}
				break;
			case "CHEST_BOAT":// TODO: "Acacia Boat with Chest" "Head" looks weird in English
				if(dataFlags.length > 1 && !dataFlags[1].equals("GRUMM")){
					if(dataFlags[1].equals("BAMBOO")) textureKey = textureKey.replace("CHEST_BOAT|BAMBOO", "item.minecraft.bamboo_chest_raft");
					else textureKey = textureKey.replaceAll("CHEST_BOAT\\|(\\w+)", "item.minecraft.$1_chest_boat").toLowerCase();
					dataFlags = textureKey.split("\\|");
				}
				break;
			case "SHULKER":
				textureKey = textureKey
						.replace("|SIDE_UP", "|SIDEWAYS")
						.replace("|SIDE_DOWN", "|SIDEWAYS")
						.replace("|SIDE_LEFT", "|SIDEWAYS")
						.replace("|SIDE_RIGHT", "|SIDEWAYS")
						.replace("|GRUMM", "|UPSIDE_DOWN");
				dataFlags = textureKey.split("\\|");
				break;
			case "UNKNOWN":
				dataFlags = (textureKey = "PLAYER"+textureKey.substring(7)).split("\\|");
				break;
		}
		ArrayList<Component> components = new ArrayList<>();//[dataFlags.length];
		try{
			components.add(TellrawUtils.getBestGuessLocalizedDisplayName(EntityType.valueOf(dataFlags[0])));
		}
		catch(IllegalArgumentException ex){ // Unable to parse EntityType
			if(dataFlags[0].indexOf('.') != -1) components.add(new TranslationComponent(dataFlags[0]));
			else components.add(new TranslationComponent(TextUtils.capitalizeAndSpacify(EntityUtils.getNormalizedEntityName(dataFlags[0]), '_')));
		}
		for(int i=1; i<dataFlags.length; ++i){
			TranslationComponent subtypeName = entitySubtypeNames.get(dataFlags[i]);
			Component comp = subtypeName != null ? subtypeName :
					new RawTextComponent(TextUtils.capitalizeAndSpacify(dataFlags[i], /*toSpace=*/'_'));
			if(!comp.toPlainText().isEmpty()) components.add(comp);
		}
		return components.toArray(new Component[0]);
	}

	Component getFullHeadNameFromKey(@Nonnull String textureKey, @Nonnull String customName){
		// Attempt to parse out an EntityType
		EntityType eType;
		final int i = textureKey.indexOf('|');
		final String nameStr = (i == -1 ? textureKey : textureKey.substring(0, i)).toUpperCase();
		try{eType = EntityType.valueOf(nameStr);}
		catch(IllegalArgumentException ex){
			if(!textures.containsKey(textureKey)){ // If preloaded in textures, it is probably a mob from a different MC version
				pl.getLogger().warning("Unknown EntityType: "+nameStr+"!");
			}
			eType = null;// We will just use textureKey[0]
		}

		// Call the actual getNameFromKey()
		final Component[] entityTypeNames = getEntityTypeAndSubtypeNamesFromKey(/*eType, */textureKey);

		final String headNameFormat = exactTextureKeyHeadNameFormats.getOrDefault(textureKey,
				headNameFormats.getOrDefault(eType, DEFAULT_HEAD_NAME_FORMAT));

		// A bit of a hacky shortcut..
		if(headNameFormat.equals("block.minecraft.player_head.named")) return new TranslationComponent(
				headNameFormat, new Component[]{nameStr.equals("PLAYER") ? new RawTextComponent(customName) : entityTypeNames[0]},
				/*insert=*/null, /*click=*/null, /*hover=*/null, /*color=*/null, /*formats=*/Collections.singletonMap(Format.ITALIC, false));

		final Pattern pattern = Pattern.compile("\\$\\{(NAME|HEAD_TYPE|MOB_TYPE|MOB_SUBTYPES_ASC|MOB_SUBTYPES_DESC)\\}");
		final Matcher matcher = pattern.matcher(headNameFormat);
		String translatedHeadNameFormat = headNameFormat;
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
					case "MOB_SUBTYPES_ASC":
						translatedHeadNameFormat = translatedHeadNameFormat.replace("${MOB_SUBTYPES_ASC}",
								StringUtils.repeat("%s"+MOB_SUBTYPES_SEPARATOR, entityTypeNames.length-1));
						for(int j=1; j<entityTypeNames.length; ++j) withComps.add(entityTypeNames[j]);
						break;
					case "MOB_SUBTYPES_DESC":
						translatedHeadNameFormat = translatedHeadNameFormat.replace("${MOB_SUBTYPES_DESC}",
								StringUtils.repeat("%s"+MOB_SUBTYPES_SEPARATOR, entityTypeNames.length-1));
						for(int j=entityTypeNames.length-1; j>0; --j) withComps.add(entityTypeNames[j]);
						break;
				}//switch (matcher.group)
			}//else (!="NAME")
		}//while (matcher.find)
		return containsTranslation
			? new TranslationComponent(pattern.matcher(translatedHeadNameFormat).replaceAll("%s"), withComps.toArray(new Component[0]),
				/*insert=*/null, /*click=*/null, /*hover=*/null, /*color=*/null, /*formats=*/Collections.singletonMap(Format.ITALIC, false))
			: new RawTextComponent(headNameFormat.replace("${NAME}", customName),
				/*insert=*/null, /*click=*/null, /*hover=*/null, /*color=*/null, /*formats=*/Collections.singletonMap(Format.ITALIC, false));
	}

	private ItemStack makeHeadFromTexture(String textureKey/*, boolean saveTypeInLore, boolean unstackable*/){
		final String code = textures.getOrDefault(textureKey, UNKNOWN_TEXTURE_CODE);
//		if(code == null || code.isEmpty()) return null;
		ItemStack head = new ItemStack(Material.PLAYER_HEAD);
		head = JunkUtils.setDisplayName(head, getFullHeadNameFromKey(textureKey, /*customName=*/""));
		final int i = textureKey.indexOf('|');
		String eTypeName = i==-1 ? textureKey : textureKey.substring(0, i);
		if(SAVE_TYPE_IN_LORE){
			head = JunkUtils.setLore(head, new RawTextComponent(
					MOB_PREFIX + eTypeName.toLowerCase(),
					/*insert=*/null, /*click=*/null, /*hover=*/null,
					/*color=*/"dark_gray", /*formats=*/Collections.singletonMap(Format.ITALIC, false)));
		}
		UUID uuid = UUID.nameUUIDFromBytes(textureKey.getBytes());// Stable UUID for this textureKey
		if(eTypeName.length() > 16) eTypeName = eTypeName.substring(0, 16);
		GameProfile profile = new GameProfile(uuid, /*name=*/eTypeName);// Initialized with UUID and name
		profile.getProperties().put("textures", new Property("textures", code));
		profile.getProperties().put(DH_TEXTURE_KEY, new Property(DH_TEXTURE_KEY, textureKey));
		if(MAKE_UNSTACKABLE) profile.getProperties().put(DH_RANDOM_UUID, new Property(DH_RANDOM_UUID, UUID.randomUUID().toString()));

		SkullMeta meta = (SkullMeta) head.getItemMeta();
		if(nbSounds != null){
			Sound sound;
			int endIdx;
			while((sound=nbSounds.get(textureKey)) == null && (endIdx=textureKey.lastIndexOf('|')) != -1) textureKey = textureKey.substring(0, endIdx);
			if(sound != null) try{SkullMeta.class.getMethod("setNoteBlockSound", NamespacedKey.class)
				.invoke(meta, Sound.class.getMethod("getKey").invoke(sound));}
			catch(Exception e){e.printStackTrace();}
		}
		HeadUtils.setGameProfile(meta, profile);
		head.setItemMeta(meta);
		return head;
	}
	private ItemStack hdb_getItemHead_wrapper(String hdbId){// Calling hdbAPI.getItemHead(id) directly is bad.
		ItemStack hdbHead = hdbAPI.getItemHead(hdbId);
		GameProfile profile = HeadUtils.getGameProfile((SkullMeta)hdbHead.getItemMeta());
		ItemStack head = HeadUtils.makeCustomHead(profile, /*setOwner=*/false);
		if(SAVE_TYPE_IN_LORE){
			head = JunkUtils.setLore(head, new RawTextComponent(
					HDB_PREFIX + hdbId,
					/*insert=*/null, /*click=*/null, /*hover=*/null,
					/*color=*/"dark_gray", /*formats=*/Collections.singletonMap(Format.ITALIC, false)));
		}
		SkullMeta meta = (SkullMeta)head.getItemMeta();
		meta.setDisplayName(hdbHead.getItemMeta().getDisplayName());
		if(MAKE_UNSTACKABLE){
			profile.getProperties().put(DH_RANDOM_UUID, new Property(DH_RANDOM_UUID, UUID.randomUUID().toString()));
			HeadUtils.setGameProfile(meta, profile);
		}
		head.setItemMeta(meta);
		return head;
	}

	private String minimizeTextureCode(final String base64){
		final String json = new String(Base64.getDecoder().decode(base64)).replaceAll("\\s", "").toLowerCase();
//		pl.getLogger().info("skin json: "+json);
		final String URL_START_MATCH = "\"url\":\"http://textures.minecraft.net/texture/";
		final int startIdx = json.indexOf(URL_START_MATCH)+URL_START_MATCH.length();
		final int endIdx = json.indexOf('"', startIdx);
		if(startIdx == -1 || endIdx <= startIdx){
			pl.getLogger().severe("Unable to decode texture URL from JSON:\n"+json+"\n");
			return base64;
		}
		final String urlCode = json.substring(startIdx, endIdx);
//		pl.getLogger().info("url code: "+urlCode);
		//TODO: Also keep timestamp (of behead) from json? json.indexOf("\"timestamp\":\"");
		return Base64.getEncoder().encodeToString(
				("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/"+urlCode+"\"}}}")
					.getBytes(StandardCharsets.ISO_8859_1));
	}

	/** Get a custom head from a Base64 code.
	 * @param code The Base64 encoded skin texture URL
	 * @return The result head ItemStack
	 */
	public ItemStack getHead(byte[] code){
		String strCode = new String(code);
		ItemStack head = new ItemStack(Material.PLAYER_HEAD);
		head = JunkUtils.setDisplayName(head, pl.getAPI().getFullHeadNameFromKey(/*textureKey=*/"UNKNOWN|CUSTOM", /*customName=*/strCode));
		GameProfile profile = new GameProfile(/*uuid=*/UUID.nameUUIDFromBytes(code), /*name=*/null/*strCode*/);
		profile.getProperties().put("textures", new Property("textures", strCode));
		if(MAKE_UNSTACKABLE) profile.getProperties().put(DH_RANDOM_UUID, new Property(DH_RANDOM_UUID, UUID.randomUUID().toString()));
		if(SAVE_TYPE_IN_LORE){
			head = JunkUtils.setLore(head, new RawTextComponent(
					CODE_PREFIX + (strCode.length() > 18 ? strCode.substring(0, 16)+"..." : strCode),
					/*insert=*/null, /*click=*/null, /*hover=*/null,
					/*color=*/"dark_gray", /*formats=*/Collections.singletonMap(Format.ITALIC, false)));
		}
		SkullMeta meta = (SkullMeta)head.getItemMeta();
		HeadUtils.setGameProfile(meta, profile);
		head.setItemMeta(meta);
		return head;
	}

	/** Get a custom head from an entity type or texture key (e.g., <code>FOX|SNOW|SLEEPING</code>).
	 * @param type The entity type for the head, can be null
	 * @param textureKey The texture key for the head
	 * @return The result head ItemStack
	 */
	public ItemStack getHead(EntityType type, String textureKey/*, boolean saveTypeInLore, boolean unstackable*/){
		// If there is extra "texture metadata" (aka '|') we should return the custom skull
		if(textureKey != null){
			textureKey = trimTextureKeyUntilFound(textureKey);

			// If this is a custom data head (still contains a '|') or eType is null AND the key exists, use it
			final boolean hasCustomData = textureKey.replace("|HOLLOW", "").indexOf('|') != -1;
			if((hasCustomData || type == null || type == EntityType.UNKNOWN || !PREFER_VANILLA_HEADS) && textures.containsKey(textureKey)){
				return makeHeadFromTexture(textureKey/*, saveTypeInLore*/);
			}
		}
		if(type == null) return null;
		switch(type){
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
				if(PIGLIN_HEAD_TYPE != null && type.name().equals("PIGLIN")) return new ItemStack(PIGLIN_HEAD_TYPE);
				return makeHeadFromTexture(type.name());
			}
	}

	/** Get a custom head from a GameProfile.
	 * @param profile The profile information to create a head
	 * @return The result head ItemStack
	 */
	public ItemStack getHead(GameProfile profile/*, boolean saveTypeInLore, boolean unstackable*/){
		if(profile == null) return null;
		//-------------------- Handle Entities with textureKey
		String textureKey = getTextureKey(profile);
		if(textureKey != null){
			textureKey = trimTextureKeyUntilFound(textureKey);
			if(textures.containsKey(textureKey)){
				if(textureKey.equals("PIGLIN") && PIGLIN_HEAD_TYPE != null && PREFER_VANILLA_HEADS) return new ItemStack(PIGLIN_HEAD_TYPE);
				return makeHeadFromTexture(textureKey);
			}
		}
		//-------------------- Handle HeadDatabase
		ItemStack head = HeadUtils.makeCustomHead(profile, /*setOwner=*/!LOCK_PLAYER_SKINS);
		if(hdbAPI != null){
			String id = hdbAPI.getItemID(head);
			if(id != null && hdbAPI.isHead(id)) return hdb_getItemHead_wrapper(id);
		}
		//-------------------- Handle players
		final boolean updateSkin = !profile.getProperties().containsKey("textures") || !LOCK_PLAYER_SKINS;
		if(profile.getId() != null){
			final String profileName;
			final GameProfile freshProfile = JunkUtils.getGameProfile(profile.getId().toString(), updateSkin, ASYNC_PROFILE_REQUESTS ? pl : null);
			if(freshProfile != null){
				if(updateSkin) profile = freshProfile;
				profileName = freshProfile.getName();

				if(LOCK_PLAYER_SKINS){
					Collection<Property> textures = profile.getProperties().get("textures");
					if(textures == null || textures.isEmpty() || textures.size() > 1){
						pl.getLogger().warning("Unable to find skin for player: "+profileName);
						pl.getLogger().warning("num textures: "+textures.size());
						head = new ItemStack(Material.PLAYER_HEAD);
						final SkullMeta meta = (SkullMeta) head.getItemMeta();
						meta.setOwningPlayer(Bukkit.getOfflinePlayer(profile.getId()));
						head.setItemMeta(meta);
					}
					else{
						final String minCode0 = minimizeTextureCode(JunkUtils.getPropertyValue(textures.iterator().next()));
						profile.getProperties().clear();
						profile.getProperties().put("textures", new Property("textures", minCode0));
						head = HeadUtils.makeCustomHead(profile, /*setOwner=*/!LOCK_PLAYER_SKINS);
					}
				}
			}
			else profileName = profile.getName();
			final boolean isMHF = profileName != null && profileName.startsWith("MHF_");
			if(profileName != null){
				head = JunkUtils.setDisplayName(head, isMHF
					? new RawTextComponent(ChatColor.YELLOW+profileName, /*insert=*/null, /*click=*/null, /*hover=*/null,
							/*color=*/null, /*formats=*/Collections.singletonMap(Format.ITALIC, false))
					: getFullHeadNameFromKey("PLAYER", /*customName=*/profileName));
			}
			if(SAVE_TYPE_IN_LORE){
				head = JunkUtils.setLore(head/*TODO: need to clone for hasPlayedBefore???*/, new RawTextComponent(
						(isMHF ? MHF_PREFIX : PLAYER_PREFIX) + profileName,
						/*insert=*/null, /*click=*/null, /*hover=*/null,
						/*color=*/"dark_gray", /*formats=*/Collections.singletonMap(Format.ITALIC, false)));
			}
		}
		//-------------------- Handle raw textures
		else if(profile.getProperties().containsKey("textures")){
			final Collection<Property> textures = profile.getProperties().get("textures");
			if(textures != null && !textures.isEmpty()){
				if(textures.size() > 1) pl.getLogger().warning("Multiple textures in getHead() request: "+profile.getName());
				final String code0 = JunkUtils.getPropertyValue(textures.iterator().next());
				return getHead(code0.getBytes());
			}
		}
		if(MAKE_UNSTACKABLE){
			profile.getProperties().put(DH_RANDOM_UUID, new Property(DH_RANDOM_UUID, UUID.randomUUID().toString()));
			final SkullMeta meta = (SkullMeta)head.getItemMeta();
			HeadUtils.setGameProfile(meta, profile);
			head.setItemMeta(meta);
		}
		return head;
	}

	/** Get a custom head from an Entity.
	 * @param entity The entity for which to to create a head
	 * @return The result head ItemStack
	 */
	public ItemStack getHead(Entity entity/*, boolean saveTypeInLore, boolean unstackable*/){
		if(entity.getType() == EntityType.PLAYER){
			return getHead(new GameProfile(entity.getUniqueId(), entity.getName()));
		}
		String textureKey = TextureKeyLookup.getTextureKey(entity);
		if(!SADDLES_ENABLED && textureKey.endsWith("|SADDLED")) textureKey = textureKey.substring(0, textureKey.length()-8);
		if(!SIDEWAYS_SHULKERS_ENABLED && textureKey.startsWith("SHULKER|") &&
			(textureKey.endsWith("SIDE_UP") || textureKey.endsWith("SIDE_DOWN") ||
					textureKey.endsWith("SIDE_LEFT") || textureKey.endsWith("SIDE_RIGHT") || textureKey.endsWith("GRUMM"))
		) textureKey = textureKey.substring(0, textureKey.lastIndexOf('|'));
		if(CRACKED_IRON_GOLEMS_ENABLED && entity.getType() == EntityType.IRON_GOLEM) textureKey += "|HIGH_CRACKINESS";
		if(HOLLOW_SKULLS_ENABLED && EntityUtils.isSkeletal(entity.getType())) textureKey += "|HOLLOW";
		if(TRANSPARENT_SLIME_ENABLED && entity.getType() == EntityType.SLIME) textureKey += "|TRANSPARENT";
		if(USE_PRE_JAPPA) textureKey += "|PRE_JAPPA";
		else if(USE_OLD_VEX && textureKey.startsWith("VEX")) textureKey += "|PRE_1_20";
		else if(USE_OLD_BAT && textureKey.startsWith("BAT")) textureKey += "|PRE_1_21";
		if(GRUMM_ENABLED && HeadUtils.hasGrummName(entity)){
			if(entity.getType() == EntityType.SHULKER) textureKey = textureKey
					.replace("|SIDE_UP", "").replace("|SIDE_DOWN", "").replace("|SIDE_LEFT", "").replace("|SIDE_RIGHT", "");
			textureKey += "|GRUMM";
		}
		ItemStack head = getHead(entity.getType(), textureKey);
//		if(head.getDisplayName().contains("${NAME}")){
//			replace ${NAME} with entity.getCustomName() in item display name
//		}
		return head;
	}
}