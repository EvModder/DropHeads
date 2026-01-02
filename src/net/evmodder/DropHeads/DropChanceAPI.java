package net.evmodder.DropHeads;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Rotatable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.evmodder.DropHeads.commands.CommandDropRate;
import net.evmodder.DropHeads.datatypes.AnnounceMode;
import net.evmodder.DropHeads.datatypes.DropMode;
import net.evmodder.DropHeads.datatypes.EntitySetting;
import net.evmodder.DropHeads.events.BeheadMessageEvent;
import net.evmodder.DropHeads.events.EntityBeheadEvent;
import net.evmodder.DropHeads.listeners.DeathMessagePacketIntercepter;
import net.evmodder.DropHeads.listeners.EntityDeathListener;
import net.evmodder.EvLib.bukkit.EvUtils;
import net.evmodder.EvLib.util.FileIO;
import net.evmodder.EvLib.bukkit.TellrawUtils;
import net.evmodder.EvLib.TextUtils;
import net.evmodder.EvLib.bukkit.TellrawUtils.Component;
import net.evmodder.EvLib.bukkit.TellrawUtils.ListComponent;
import net.evmodder.EvLib.util.ReflectionUtils;
import net.evmodder.EvLib.bukkit.YetAnotherProfile;

/** Public API for head drop chance logic loaded from DropHeads configs.
 * Warning: Functions may change or disappear in future releases
 */
public final class DropChanceAPI{
	private final AnnounceMode DEFAULT_ANNOUNCE, SILENT_ANNOUNCE;

	private final boolean REPLACE_PLAYER_DEATH_EVT_MESSAGE, REPLACE_PET_DEATH_MESSAGE, REPLACE_PLAYER_DEATH_MESSAGE/*, USE_TRANSLATE_FALLBACKS*/;
	private final boolean VANILLA_WSKELE_HANDLING;
	private final boolean DEBUG_MODE, LOG_PLAYER_BEHEAD, LOG_MOB_BEHEAD;
	private final String LOG_MOB_FORMAT, LOG_PLAYER_FORMAT;
	private final String[] MSG_BEHEAD, MSH_BEHEAD_BY, MSH_BEHEAD_BY_WITH, MSH_BEHEAD_BY_WITH_NAMED;
	private final boolean CROSS_DIMENSIONAL_BROADCAST;
	private final int LOCAL_RANGE;
	private final int JSON_LIMIT;

	private final DropHeads pl;
	private final DeathMessagePacketIntercepter deathMessageBlocker;
	private final Random rand;
	private final HashSet<Material> headOverwriteBlocks;
	private final LRUCache<ItemStack, Component> weaponCompCache;
	private final int WEAPON_COMP_CACHE_SIZE;

	private final EntitySetting<Double> lootingLevelMult, lootingLevelAdd;
	private final EntitySetting<Set<Material>> requiredWeapons;
	private final EntitySetting<Double> dropChances;
	private final EntitySetting<List<DropMode>> dropModes;
	private final HashMap<EntityType, AnnounceMode> mobAnnounceModes;
	private final EntitySetting<Map<String, Double>> permissionMults;
	private final EntitySetting<Map<Material, Double>> weaponMults;
	private final EntitySetting<TreeMap</*timeAlive=*/Integer, Double>> timeAliveMults;// Note: Bukkit's e.getTicksLived() returns an int.

	private final Method methodDeserialize, methodGson, methodSendMessage;

//	int numMobBeheads, numPlayerBeheads;

	private AnnounceMode parseAnnounceMode(@Nonnull String value, AnnounceMode defaultMode){
		value = value.toUpperCase();
		if(value.equals("FALSE")) return AnnounceMode.OFF;
		if(value.equals("TRUE")) return AnnounceMode.GLOBAL;
		try{return AnnounceMode.valueOf(value);}
		catch(IllegalArgumentException ex){
			DropHeads.getPlugin().getLogger().severe("Unknown announcement mode: '"+value+"'");
			DropHeads.getPlugin().getLogger().warning("Please use one of the available modes: [GLOBAL, LOCAL, OFF]");
			return defaultMode;
		}
	}

	/*private static String[] parseStringOrStringList(String key, String defaultMsg, Configuration... configs){
		for(Configuration config : configs){
			List<String> strList = null;
			if(config.isList(key) && (strList=config.getStringList(key)) != null && !strList.isEmpty())
				return strList.stream().map(msg -> TextUtils.translateAlternateColorCodes('&', msg)).toArray(size -> new String[size]);
			if(config.isString(key) && !defaultMsg.equals(config.getString(key)))
				return new String[]{TextUtils.translateAlternateColorCodes('&', config.getString(key))};
		}
		return new String[]{TextUtils.translateAlternateColorCodes('&', defaultMsg)};
	}*/
	private String[] parseStringOrStringList(String key, String defaultMsg){
		List<String> strList = null;
		if(pl.getConfig().isList(key) && (strList=pl.getConfig().getStringList(key)) != null && !strList.isEmpty())
			return strList.stream().map(msg -> TextUtils.translateAlternateColorCodes('&', msg)).toArray(size -> new String[size]);
		if(pl.getConfig().isString(key) && !defaultMsg.equals(pl.getConfig().getString(key)))
			return new String[]{TextUtils.translateAlternateColorCodes('&', pl.getConfig().getString(key))};
		return new String[]{TextUtils.translateAlternateColorCodes('&', defaultMsg)};
	}

	DropChanceAPI(final boolean replacePlayerDeathMsg, final boolean replacePetDeathMsg, DeathMessagePacketIntercepter deathMessageBlocker){
		pl = DropHeads.getPlugin();
		this.deathMessageBlocker = deathMessageBlocker;
		rand = new Random();
		VANILLA_WSKELE_HANDLING = pl.getConfig().getBoolean("vanilla-wither-skeleton-skulls", false);
		REPLACE_PLAYER_DEATH_MESSAGE = replacePlayerDeathMsg;
		REPLACE_PLAYER_DEATH_EVT_MESSAGE = pl.getConfig().getBoolean("behead-announcement-replaces-player-death-event-message", false);
		REPLACE_PET_DEATH_MESSAGE = replacePetDeathMsg;
		DEBUG_MODE = pl.getConfig().getBoolean("debug-messages", true);
		final boolean ENABLE_LOG = pl.getConfig().getBoolean("log.enable", false);
		LOG_MOB_BEHEAD = ENABLE_LOG && pl.getConfig().getBoolean("log.log-mob-behead", false);
		LOG_PLAYER_BEHEAD = ENABLE_LOG && pl.getConfig().getBoolean("log.log-player-behead", false);
		LOG_MOB_FORMAT = LOG_MOB_BEHEAD ? pl.getConfig().getString("log.log-mob-behead-format",
				"${TIMESTAMP},mob decapitated,${VICTIM},${KILLER},${ITEM}") : null;
		LOG_PLAYER_FORMAT = LOG_PLAYER_BEHEAD ? pl.getConfig().getString("log.log-player-behead-format",
				"${TIMESTAMP},player decapitated,${VICTIM},${KILLER},${ITEM}") : null;
		WEAPON_COMP_CACHE_SIZE = pl.getConfig().getInt("weapon-tellraw-component-cache-size", 0);
		weaponCompCache = WEAPON_COMP_CACHE_SIZE > 0 ? new LRUCache<>(WEAPON_COMP_CACHE_SIZE) : null;

		JSON_LIMIT = pl.getConfig().getInt("message-json-limit", 15000);
		MSG_BEHEAD = parseStringOrStringList("message-beheaded", "&6${VICTIM}&r was decapitated");
		MSH_BEHEAD_BY = parseStringOrStringList("message-beheaded-by-entity", "${VICTIM} was beheaded by ${KILLER}");
		MSH_BEHEAD_BY_WITH = parseStringOrStringList("message-beheaded-by-entity-with-item", "${VICTIM} was beheaded by ${KILLER}");
		MSH_BEHEAD_BY_WITH_NAMED = parseStringOrStringList("message-beheaded-by-entity-with-item-named", "${KILLER} beheaded ${VICTIM} using ${ITEM}");
//		USE_TRANSLATE_FALLBACKS = pl.getAPI().translationsFile.getBoolean("use-translation-fallbacks", false)
//				&& Bukkit.getBukkitVersion().compareTo("1.19.4") >= 0;

		dropModes = EntitySetting.fromConfig(pl, "head-item-drop-mode", List.of(DropMode.EVENT, DropMode.SPAWN_RANDOM), (k,v)->{
			final boolean isList = v instanceof List;
			final List<?> vs = isList ? (List<?>)v : List.of(v);
			final List<DropMode> modes = new ArrayList<>(/*capacity=*/vs.size());
			for(Object o : isList ? (List<?>)v : List.of(v)){
				if(o instanceof String s){
					try{modes.add(DropMode.valueOf(s.toUpperCase()));}
					catch(IllegalArgumentException ex){pl.getLogger().severe("Unknown head DropMode: "+s);}
				}
				else{
					pl.getLogger().warning("Invalid entity-setting (expected DropMode) for "+k+": "+v);
					return null;
				}
			}
			if(modes.isEmpty()){
				pl.getLogger().severe("No DropMode(s) specified for "+k+"! Heads will not be dropped!");
				return List.of(DropMode.EVENT, DropMode.SPAWN_RANDOM);
			}
			return modes;
		});
		boolean anyPlacementMode = Stream.concat( // TODO: there's gotta be a better shorthand, right?
				dropModes.globalDefault().stream(),
				Stream.concat(
					dropModes.typeSettings() == null ? Stream.of() : dropModes.typeSettings().values().stream().flatMap(List::stream),
					dropModes.subtypeSettings() == null ? Stream.of() : dropModes.subtypeSettings().values().stream().flatMap(List::stream)
				)
			).anyMatch(mode -> mode.name().startsWith("PLACE"));

		if(!anyPlacementMode) headOverwriteBlocks = null;
		else{
			List<String> matNames = pl.getConfig().getStringList("head-place-overwrite-blocks");
			headOverwriteBlocks = new HashSet<>(matNames.size());
			for(String matName : matNames){
				try{headOverwriteBlocks.add(Material.valueOf(matName.toUpperCase()));}
				catch(IllegalArgumentException ex){pl.getLogger().severe("Unknown material in 'head-place-overwrite-blocks': "+matName);}
			}
		}

		SILENT_ANNOUNCE = parseAnnounceMode(pl.getConfig().getString("silentbehead-announcement", "OFF"), AnnounceMode.OFF);
		mobAnnounceModes = new HashMap<>();
		// Legacy config settings 'behead-announcement-mobs' and 'behead-announcement-players'
		mobAnnounceModes.put(EntityType.UNKNOWN, parseAnnounceMode(pl.getConfig().getString("behead-announcement-mobs", "LOCAL"), AnnounceMode.LOCAL));
		mobAnnounceModes.put(EntityType.PLAYER, parseAnnounceMode(pl.getConfig().getString("behead-announcement-players", "GLOBAL"), AnnounceMode.GLOBAL));
		ConfigurationSection announceModes = pl.getConfig().getConfigurationSection("behead-announcement");
		AnnounceMode tempDefaultAnnounce = mobAnnounceModes.get(EntityType.UNKNOWN);
		if(announceModes != null) for(String mobName : announceModes.getKeys(false)){
			try{
				EntityType eType = EntityType.valueOf(mobName.toUpperCase().replace("DEFAULT", "UNKNOWN"));
				mobAnnounceModes.put(eType, parseAnnounceMode(announceModes.getString(mobName), tempDefaultAnnounce));
			}
			catch(IllegalArgumentException ex){pl.getLogger().severe("Unknown entity type in 'behead-announce': "+mobName);}
		}
		DEFAULT_ANNOUNCE = mobAnnounceModes.get(EntityType.UNKNOWN);
		LOCAL_RANGE = pl.getConfig().getInt("local-broadcast-distance", 200);
		CROSS_DIMENSIONAL_BROADCAST = pl.getConfig().getBoolean("local-broadcast-cross-dimension", true);

		lootingLevelMult = EntitySetting.fromConfig(pl, "looting-multiplier", 1.01d, (k,v)->{
			double d = v instanceof Number n ? n.doubleValue() : Double.parseDouble(v.toString());
			if(d < 1) pl.getLogger().warning("looting-multiplier is set below 1.0, this means looting will DECREASE the chance of head drops!");
			return d;
		});
		lootingLevelAdd = EntitySetting.fromConfig(pl, "looting-addition", 0d, (k,v)->{
			double d = v instanceof Number n ? n.doubleValue() : Double.parseDouble(v.toString());
			if(d >= 1) pl.getLogger().warning("looting-addition is set to 1.0 or greater, this means heads will ALWAYS drop when looting is used!");
			return d;
		});

		requiredWeapons = EntitySetting.fromConfig(pl, "require-weapon", Set.of(), (k,v)->{
			// Parse List<String> -> Set<Material>
			if(v instanceof List){
				return Collections.unmodifiableSet(((List<?>)v).stream().map(n -> n.toString()).map(matName -> {
					if(matName.isEmpty()){
						pl.getLogger().warning("Empty weapon name! (should be unreachable, please report this to the developer)");
						return (Material)null;
					}
					final Material mat = Material.getMaterial(matName.toUpperCase());
					if(mat == null) pl.getLogger().warning("Unknown weapon: \""+matName+"\"!");
					return mat;
				})
				.filter(mat -> mat != null)
				.collect(Collectors.toSet()));
			}
			pl.getLogger().warning("Invalid entity-setting (expected weapon list) for "+k+": "+v);
			return null;
		});

		//weaponMults = new HashMap<Material, Double>();
		weaponMults = EntitySetting.fromConfig(pl, "weapon-multipliers", Map.of(), (k,v)->{
			if(v instanceof ConfigurationSection cs){
				HashMap<Material, Double> specificWeaponMults = new HashMap<>();
				for(String matName : cs.getKeys(/*deep=*/false)){
					final Material mat = Material.getMaterial(matName.toUpperCase());
					if(mat != null) specificWeaponMults.put(mat, cs.getDouble(matName));
					else pl.getLogger().warning("Invalid weapon: \""+matName+"\"!");
				}
				return specificWeaponMults;
			}
			pl.getLogger().warning("Invalid entity-setting section (expected weapon:number list) for "+k+": "+v);
			return null;
		});

		final TreeMap<Integer, Double> defaultTimeAliveMults = new TreeMap<>();
		defaultTimeAliveMults.put(Integer.MIN_VALUE, 1d);
		timeAliveMults = EntitySetting.fromConfig(pl, "time-alive-multipliers", defaultTimeAliveMults, (k,v) -> {
			if(v instanceof ConfigurationSection cs){
				@SuppressWarnings("unchecked")
				final TreeMap<Integer, Double> timeAliveMults = (TreeMap<Integer, Double>)defaultTimeAliveMults.clone();
				for(String formattedTime : cs.getKeys(/*deep=*/false)){
					try{
						final int timeInTicks = (int)(TextUtils.parseTimeInMillis(formattedTime, /*default unit=millis-per-tick=*/50)/50L);
						timeAliveMults.put(timeInTicks, cs.getDouble(formattedTime));
					}
					catch(NumberFormatException ex){
						pl.getLogger().severe("Error parsing time string for 'time-alive-multipliers' of "+k+": \""+formattedTime+'"');
					}
				}
				if(timeAliveMults.size() > 1) return timeAliveMults;
			}
			pl.getLogger().severe("time-alive-multipliers for "+k+" is incorrectly defined");
			return null;
		});

		permissionMults = EntitySetting.fromConfig(pl, "permission-multipliers", Map.of(), (k,v) -> {
			if(v instanceof ConfigurationSection cs){
				HashMap<String, Double> specificPermMults = new HashMap<>();
				for(String perm : cs.getKeys(/*deep=*/true)){
					if(cs.isConfigurationSection(perm)) continue; //ignore ['dropheads', 'dropheads.group'] for 'dropheads.group.2x'
					try{specificPermMults.put(perm, cs.getDouble(perm, 1D));}
					catch(NumberFormatException ex){pl.getLogger().severe("Error parsing droprate multiplier for permission: \""+perm+'"');}
				}
				return specificPermMults;
			}
			pl.getLogger().severe("permission-multipliers for "+k+" is incorrectly defined");
			return null;
		});

		final BiFunction<String, String, Double> parseDropChance = (k,v) -> {
			try{
				final double dropChance = Double.parseDouble(v);
				if(dropChance < 0d || dropChance > 1d){
					pl.getLogger().severe("Invalid value for "+k+" in 'head-drop-rates.txt': "+v);
					pl.getLogger().warning("Drop chance should be a decimal between 0 and 1");
					return (dropChance > 1d && dropChance <= 100d) ? dropChance/100d : null;
				}
				return dropChance;
			}
			catch(NumberFormatException e){
				pl.getLogger().severe("Invalid value for "+k+" in 'head-drop-rates.txt': "+v);
				return null;
			}
		};
		//Load individual mobs' drop chances
		dropChances = EntitySetting.fromTextFile(pl, "head-drop-rates.txt", "configs/head-drop-rates.txt", 0d, parseDropChance);
		if(VANILLA_WSKELE_HANDLING && dropChances.get(EntityType.WITHER_SKELETON, 0.025d) != 0.025d){
			pl.getLogger().warning("Wither Skeleton Skull drop chance has been modified in 'head-drop-rates.txt'"
					+ " (0.025->"+dropChances.get(EntityType.WITHER_SKELETON)+"), "
					+ "but this value will be ignored because 'vanilla-wither-skeleton-skulls' is set to true.");
		}
//		dropChances.entrySet().removeIf(entry -> entry.getValue() == DEFAULT_CHANCE);

		// Dynamically generate all the children perms of "dropheads.alwaysbehead.<entity>" and "dropheads.canbehead.<entity>"
		final Permission alwaysBeheadPerm = pl.getServer().getPluginManager().getPermission("dropheads.alwaysbehead");
		final Permission canBeheadPerm = pl.getServer().getPluginManager().getPermission("dropheads.canbehead");
		if(alwaysBeheadPerm != null) try{
			for(EntityType entity : EntityType.values()){
				final String entityName = entity.name().toLowerCase();
				Permission alwaysBeheadPermForEntity = new Permission(
						alwaysBeheadPerm.getName()+"."+entityName,
						"This entity will get a head 100% of the time when killing a "+entityName,
						PermissionDefault.FALSE);
				alwaysBeheadPermForEntity.addParent(alwaysBeheadPerm, true);
				pl.getServer().getPluginManager().addPermission(alwaysBeheadPermForEntity);

				Permission canBeheadPermForEntity = new Permission(
						canBeheadPerm.getName()+"."+entityName,
						"This entity will be able to get heads when killing a "+entityName,
						PermissionDefault.FALSE);
				canBeheadPermForEntity.addParent(canBeheadPerm, true);
				pl.getServer().getPluginManager().addPermission(canBeheadPermForEntity);
			}
			//alwaysBeheadPerm.recalculatePermissibles();
			//canBeheadPerm.recalculatePermissibles();
		}
		catch(IllegalArgumentException ex){/*The permissions are already defined; perhaps this is just a plugin or server reload*/}

		Method tempGson, tempDeserialize, tempSendMessage;
		try{// Paper/Purpur servers
			Class<?> classGson = ReflectionUtils.getClass("net.kyori.adventure.text.serializer.gson.GsonComponentSerializer");
			Class<?> classAudience = ReflectionUtils.getClass("net.kyori.adventure.audience.Audience");
			Class<?> classComponent = ReflectionUtils.getClass("net.kyori.adventure.text.Component");
			tempGson = ReflectionUtils.findMethodByName(classGson, "gson");
			tempDeserialize = ReflectionUtils.findMethodByReturnType(classGson, classComponent);
//			tempDeserialize = classGson.getMethod("deserializeFromTree​", JsonElement.class);
			tempSendMessage = ReflectionUtils.getMethod(classAudience, "sendMessage", classComponent);
		}
		catch(RuntimeException e){// Craftbukkit/Spigot servers
//			e.printStackTrace();
			tempGson = tempDeserialize = tempSendMessage = null;
		}
		methodGson = tempGson;
		methodDeserialize = tempDeserialize;
		methodSendMessage = tempSendMessage;
	}

	/** Get the default head drop chance for an entity when a drop chance chance is specified in the config.
	 * @return The default drop chance [0, 1]
	 */
	public double getDefaultDropChance(){return dropChances.globalDefault();}

//	/** Get the set of weapons that are allowed to cause a head drop; will be <code>null</code> if no specific weapon(s) are required.
//	 * @return An unmodifiable set of Material types, or <code>null</code>
//	 */
//	public Set<Material> getRequiredWeapons(Entity entity){return requiredWeapons.get(entity);}
	/** Check whether the given weapon is allowed to cause a head drop; will always be <code>true</code> if no specific weapon(s) are required.
	 * @param entity The entity that was killed
	 * @param weapon The weapon <code>Material</code> that was used
	 * @return Boolean value
	 */
	public boolean isWeaponAbleToBehead(Entity entity, Material weapon){
		final Set<Material> weapons = requiredWeapons.get(entity);
		return weapons.isEmpty() || weapons.contains(weapon);
	}
	/** Get the raw drop chance (ignore all multipliers) of a head for a specific texture key. Only called by CommandDropRate currently.
	 * @param textureKey The target texture key
	 * @param useDefault Whether to return the default-chance if key is not found, otherwise will return null
	 * @return The drop chance [0, 1] or null
	 */
	public Double getRawDropChanceOrDefaultFromTextureKey(String textureKey, boolean useDefault){
		return dropChances.get(textureKey, useDefault ? dropChances.globalDefault() : null);
	}
	/** Get the raw drop chance (ignore all multipliers) of a head for a specific entity.
	 * @param entity The target entity
	 * @return The drop chance [0, 1]
	 */
	public double getRawDropChance(Entity entity){return dropChances.get(entity);}
	/** Get the percent chance added to the drop chance (per looting level).
	 * @return The drop chance increase amount
	 */
	public double getLootingAdd(Entity entity){return lootingLevelAdd.get(entity);}
	/** Get the drop chance multiplier applied (per looting level).
	 * @return The drop chance multiplier
	 */
	public double getLootingMult(Entity entity){return lootingLevelMult.get(entity);}
	/** Get the drop chance multiplier applied based on Material of the weapon used.
	 * @param weapon The type of weapon used
	 * @return The weapon-type multiplier
	 */
	public double getWeaponMult(Entity entity, Material weapon){return weaponMults.get(entity).getOrDefault(weapon, 1D);}
	/** Get the drop chance multiplier applied based on how many ticks an entity has been alive.
	 * @param entity The entity to check the lifetime of
	 * @return The time-alive multiplier
	 */
	public double getTimeAliveMult(Entity entity){
		return timeAliveMults.get(entity).floorEntry(
				entity.getType() == EntityType.PLAYER
					? ((Player)entity).getStatistic(Statistic.TIME_SINCE_DEATH)
					: entity.getTicksLived()
		).getValue();
	}
	/** Get the drop chance multipliers applied based on permissions of the killer.
	 * @param killer The entity to check for drop chance multiplier permissions
	 * @return The aggregate multiplier from any relevent permission nodes
	 */
	public double getPermissionsMult(Entity victim, Permissible killer){
		if(killer == null) return 1D;
		return permissionMults.get(victim).entrySet().stream().parallel() //TOOD: parallel worth it?
				.filter(e -> killer.hasPermission(e.getKey()))
				.map(e -> e.getValue())
				.reduce(1D, (a, b) -> a * b);
	}

	/** Set the raw drop chance of a head for a specific texture key.
	 * @param textureKey The target texture key
	 * @param newChance The new drop chance to use for the entity
	 * @param updateFile Whether to also update <code>plugins/DropHeads/head-drop-rates.txt</code> file (permanently change value)
	 * @return Whether the change took place
	 */
	public boolean setRawDropChance(String textureKey, double newChance, boolean updateFile){
		if(dropChances.get(textureKey, null) == newChance) return false;
		final int firstSep = textureKey.indexOf('|');
		final String eName = firstSep == -1 ? textureKey : textureKey.substring(0, firstSep);
		final EntityType eType;
		try{eType = EntityType.valueOf(eName);}
		catch(IllegalArgumentException ex){return false;}
		if(firstSep != -1){
			if(!pl.getAPI().textureExists(textureKey)) return false;
			dropChances.subtypeSettings().put(textureKey, newChance);
		}
		else dropChances.typeSettings().put(eType, newChance);
		if(!updateFile) return true;

		String spaces = StringUtils.repeat(' ', 19-textureKey.length()); // TODO: alternative to this hacky spacing nonsense?
		String insertLine = textureKey + ':' + spaces + newChance;
		String content;
		try{content = new String(Files.readAllBytes(Paths.get(FileIO.DIR+"head-drop-rates.txt")));}
		catch(IOException e){e.printStackTrace(); return false;}

		String updated = content.replaceAll(
				"(?m)^"+textureKey.replace("|", "\\|")+":\\s*\\d*\\.?\\d+(\n?)",
				(newChance == dropChances.globalDefault()) ? "" : insertLine+"$1");
		if(updated.equals(content)) updated += "\n"+insertLine;
		return FileIO.saveFile("head-drop-rates.txt", updated);
	}

	/** Drop a head item for an entity using the appropriate <code>DropMode</code> setting.
	 * @param headItem The head item which will be dropped
	 * @param entity The entity from which the head item came
	 * @param killer The entity responsible for causing the head item to drop
	 * @param evt The <code>Entity*Event</code> from which this function is being called
	 */
	@SuppressWarnings("deprecation")
	public void dropHeadItem(ItemStack headItem, Entity entity, Entity killer, Event evt){
		for(DropMode mode : dropModes.get(entity)){
			if(headItem == null) break;
			switch(mode){
				case EVENT:
					if(evt instanceof EntityDeathEvent ede){ede.getDrops().add(headItem); headItem = null;}
					break;
				case PLACE_BY_KILLER:
				case PLACE_BY_VICTIM:
				case PLACE: {
					Block headBlock = EvUtils.getClosestBlock(entity.getLocation(), 5, b -> headOverwriteBlocks.contains(b.getType())).getBlock();
					BlockState state = headBlock.getState();
					state.setType(headItem.getType());
					Vector facingVector = entity.getLocation().getDirection(); facingVector.setY(0);  // loc.setPitch(0F)
					BlockFace blockRotation = MiscUtils.getHeadPlacementDirection(facingVector);
					try{
						Rotatable data = (Rotatable)headBlock.getBlockData();
						data.setRotation(blockRotation);
						state.setBlockData(data);
					}
					catch(ClassCastException ex){ // Work-around for really dumb Spigot 1.21 change (BlockData for skulls no longer Rotatable)
						((Skull)state).setRotation(blockRotation);
					}
					if(headItem.getType() == Material.PLAYER_HEAD){
						YetAnotherProfile.fromSkullMeta((SkullMeta)headItem.getItemMeta()).set((Skull)state);
					}
					if(mode != DropMode.PLACE){
						Entity entityToCheck = (killer == null ||
								(mode == DropMode.PLACE_BY_VICTIM && (entity instanceof Player || killer instanceof Player == false)))
								? entity : killer;
						Event testPermsEvent;
						if(entityToCheck instanceof Player){
							testPermsEvent = new BlockPlaceEvent(headBlock, state,
								headBlock.getRelative(BlockFace.DOWN), headItem, (Player)entityToCheck, /*canBuild=*/true, EquipmentSlot.HAND);
						}
						else{
							testPermsEvent = new EntityBlockFormEvent(entityToCheck, headBlock, state);
						}
						pl.getServer().getPluginManager().callEvent(testPermsEvent);
						if(((Cancellable)testPermsEvent).isCancelled()){
							pl.getLogger().fine("Head placement failed, permission-lacking player: "+entityToCheck.getName());
							break;
						}
					}
					state.update(/*force=*/true);
					headItem = null;
					break;
				}
				case GIVE:
					headItem = MiscUtils.giveItemToEntity(killer, headItem);
					break;
				case SPAWN_RANDOM:
					EvUtils.dropItemNaturally(entity.getLocation(), headItem, rand);
					headItem = null;
					break;
				case SPAWN:
					entity.getWorld().dropItem(entity.getLocation(), headItem);
					headItem = null;
					break;
			}//switch(mode)
		}//for(DROP_MODES)
	}

	private Component getVictimComponent(Entity entity){
		//Arrays.stream(entities).map(e -> JunkUtils.getDisplayNameSelectorComponent(entity)).collect(Collectors.joining(ChatColor.GRAY+", "+ChatColor.RESET));
		return MiscUtils.getDisplayNameSelectorComponent(entity, true);
	}
	private Component getKillerComponent(Entity killer){
		if(killer == null) return null;
		if(killer instanceof Projectile){
			ProjectileSource shooter = ((Projectile)killer).getShooter();
			if(shooter instanceof Entity) return MiscUtils.getDisplayNameSelectorComponent((Entity)shooter, true);
			else if(shooter instanceof BlockProjectileSource){
				return TellrawUtils.getLocalizedDisplayName(((BlockProjectileSource)shooter).getBlock().getState());
			}
			// In theory should never happen:
			else pl.getLogger().warning("Unrecognized projectile source: "+shooter.getClass().getName());
		}
		return MiscUtils.getDisplayNameSelectorComponent(killer, true);
	}
	private Component getWeaponComponent(Entity killer, ItemStack weapon){
		if(weapon != null && weapon.getType() != Material.AIR){
			if(weaponCompCache == null) return MiscUtils.getMurderItemComponent(weapon, JSON_LIMIT);
			Component weaponComp = weaponCompCache.get(weapon);
			if(weaponComp == null){
				weaponComp = MiscUtils.getMurderItemComponent(weapon, JSON_LIMIT);
				weaponCompCache.put(weapon, weaponComp);
			}
			return weaponComp;
		}
		if(killer != null && killer instanceof Projectile) return MiscUtils.getDisplayNameSelectorComponent(killer, true);
		return null;
	}

	/** Generate a behead message component.
	 * @param entity The entity that was beheaded
	 * @param killer The entity that did the beheading
	 * @param weapon The weapon item used to do the beheading
	 * @return The behead message component
	 */
	public ListComponent getBeheadMessage(Entity entity, Entity killer, ItemStack weapon){
		final ListComponent message = new ListComponent();
		final Component killerComp = getKillerComponent(killer);
		final Component itemComp = getWeaponComponent(killer, weapon);
		if(killerComp != null){
			if(itemComp != null){
				//TODO: if(pl.getAPI().USE_TRANSLATE_FALLBACKS)
				//death.attack.behead, death.attack.behead.player, death.attack.behead.player.item
				final boolean hasCustomName = weapon != null && weapon.hasItemMeta() && weapon.getItemMeta().hasDisplayName();
				message.addComponent(hasCustomName
						? MSH_BEHEAD_BY_WITH_NAMED[rand.nextInt(MSH_BEHEAD_BY_WITH_NAMED.length)]
						: MSH_BEHEAD_BY_WITH[rand.nextInt(MSH_BEHEAD_BY_WITH.length)]);
				message.replaceRawTextWithComponent("${ITEM}", itemComp);
			}
			else message.addComponent(MSH_BEHEAD_BY[rand.nextInt(MSH_BEHEAD_BY.length)]);
			message.replaceRawTextWithComponent("${KILLER}", killerComp);
		}
		else message.addComponent(MSG_BEHEAD[rand.nextInt(MSG_BEHEAD.length)]);
		message.replaceRawTextWithComponent("${VICTIM}", getVictimComponent(entity));
		//TODO: If(USE_TRANSLATE_FALLBACKS) return TranslateComp({key}, {with}, fallback=message)
		return message;
	}

	@SuppressWarnings("deprecation")
	private void sendComponent(Player target, Component component){
		if(methodSendMessage == null){ // Craftbukkit/Spigot
			pl.getServer().dispatchCommand(pl.getServer().getConsoleSender(), "minecraft:tellraw "+target.getName()+" "+component.toString());
		}
		else try{ // Paper/Purpur
			JsonElement json = new JsonParser().parse(component.toString());
			Object chatComp = ReflectionUtils.call(methodDeserialize, ReflectionUtils.callStatic(methodGson), json);
			ReflectionUtils.call(methodSendMessage, target, chatComp);
		}
		catch(JsonSyntaxException e){e.printStackTrace();}
	}
	private void sendBeheadMessage(Player target, Entity victim, Entity killer, Component message, boolean isGlobal, boolean isPet){
		BeheadMessageEvent event = new BeheadMessageEvent(target, victim, killer, message, isGlobal, isPet);
		pl.getServer().getPluginManager().callEvent(event);
		if(!event.isCancelled()) sendComponent(target, event.getMessage());
		else if(DEBUG_MODE) pl.getLogger().info("BeheadMessageEvent was cancelled");
	}
	/** Send a head drop announcement message for an entity with recipients are based on:<br>
	 * * The <code>AnnounceMode</code> setting for the entity's type<br>
	 * * The permissions of the killer<br>
	 * * The pet-owner of the entity, if exists<br>
	 * * Specific broadcast settings from the DropHeads config<br>
	 * @param message The behead announcement message
	 * @param entity The entity to announce as beheaded
	 * @param killer The entity to announce as the killer
	 * @param evt The <code>Entity*Event</code> from which this function is being called
	 */
	public void announceHeadDrop(@Nonnull Component message, Entity entity, Entity killer, Event evt){
		if(message.toPlainText().replaceAll(" ", "").isEmpty()) return;
		if(DEBUG_MODE) pl.getLogger().info("Behead message json: "+message.toString());

		AnnounceMode mode = mobAnnounceModes.getOrDefault(entity.getType(), DEFAULT_ANNOUNCE);
		if(mode != AnnounceMode.OFF && mode != SILENT_ANNOUNCE && killer != null && (
			killer.hasPermission("dropheads.silentbehead") ||
			(killer.hasPermission("dropheads.silentbehead.invisible")
				&& killer instanceof LivingEntity && ((LivingEntity)killer).hasPotionEffect(PotionEffectType.INVISIBILITY)
			) ||
			(killer.hasPermission("dropheads.silentbehead.vanished")
				&& killer instanceof Player && MiscUtils.isVanished((Player)killer)
			)
		)) mode = SILENT_ANNOUNCE;

		final Entity petOwnerToMsg = REPLACE_PET_DEATH_MESSAGE && entity instanceof Tameable && ((Tameable)entity).getOwner() != null
				? pl.getServer().getEntity(((Tameable)entity).getOwner().getUniqueId()) : null;
		final UUID petOwnerToMsgUUID = petOwnerToMsg == null ? null : petOwnerToMsg.getUniqueId();

		switch(mode){
			case GLOBAL:
				if(entity instanceof Player && REPLACE_PLAYER_DEATH_MESSAGE && evt != null){
					// Set the behead death message so that other plugins will see it (it will still get cleared by the intercepter)
					// In order to do this, we need to cancel the extra plaintext message in DeathMessagePacketIntercepter
					if(REPLACE_PLAYER_DEATH_EVT_MESSAGE){
						final String plainDeathMsg = message.toPlainText();
						((PlayerDeathEvent)evt).setDeathMessage(plainDeathMsg);
						deathMessageBlocker.blockSpeficicMessage(plainDeathMsg, /*ticksBlockedFor=*/5);
					}
				}
				for(Player p : pl.getServer().getOnlinePlayers()){
					sendBeheadMessage(p, entity, killer, message, /*isGlobal=*/true, /*isPetDeathMsg=*/p.getUniqueId() == petOwnerToMsgUUID);
				}
				break;
			case LOCAL:
				HashSet<UUID> recipients = new HashSet<UUID>();
				for(Player p : EvUtils.getNearbyPlayers(entity.getLocation(), LOCAL_RANGE, CROSS_DIMENSIONAL_BROADCAST)){
					sendBeheadMessage(p, entity, killer, message, /*isGlobal=*/false, /*isPetDeathMsg=*/p.getUniqueId() == petOwnerToMsgUUID);
					recipients.add(p.getUniqueId());
				}
				if(petOwnerToMsgUUID != null && !recipients.contains(petOwnerToMsg.getUniqueId())){
					if(petOwnerToMsg instanceof Player == false) pl.getLogger().warning("Non-player pet owner: "+petOwnerToMsg.getName());
					else sendBeheadMessage((Player)petOwnerToMsg, entity, killer, message, /*isGlobal=*/false, /*isPetDeathMsg=*/true);
				}
				break;
			case DIRECT:
				if(killer instanceof Player){
					sendBeheadMessage((Player)killer, entity, killer, message, /*isGlobal=*/false, /*isPetDeathMsg=*/killer.getUniqueId() == petOwnerToMsgUUID);
				}
				if(petOwnerToMsgUUID != null && !killer.getUniqueId().equals(petOwnerToMsg.getUniqueId())){
					if(petOwnerToMsg instanceof Player == false) pl.getLogger().warning("Non-player pet owner: "+petOwnerToMsg.getName());
					else sendBeheadMessage((Player)petOwnerToMsg, entity, killer, message, /*isGlobal=*/false, /*isPetDeathMsg=*/true);
				}
				break;
			case OFF:
				break;
		}
	}

	/** Logs a behead event to the DropHeads log file.
	 * @param entity The entity that was beheaded
	 * @param killer The entity that did the beheading
	 * @param weapon The weapon item used to do the beheading
	 */
	public void logHeadDrop(Entity entity, Entity killer, ItemStack weapon){
		pl.writeToLogFile(
				(entity instanceof Player ? LOG_PLAYER_FORMAT : LOG_MOB_FORMAT)
				.replaceAll("(?i)\\$\\{(VICTIM|BEHEADED)_UUID\\}", ""+entity.getUniqueId())
				.replaceAll("(?i)\\$\\{(KILLER|BEHEADER)_UUID\\}", killer == null ? "" : ""+killer.getUniqueId())
				.replaceAll("(?i)\\$\\{(VICTIM|BEHEADED)(_NAME)?\\}", Matcher.quoteReplacement(getVictimComponent(entity).toPlainText()))
				.replaceAll("(?i)\\$\\{(KILLER|BEHEADER)(_NAME)?\\}", killer == null ? "" : Matcher.quoteReplacement(getKillerComponent(killer).toPlainText()))
				.replaceAll("(?i)\\$\\{(ITEM|WEAPON)\\}", weapon == null ? "" : Matcher.quoteReplacement(getWeaponComponent(killer, weapon).toPlainText()))
				.replaceAll("(?i)\\$\\{TIMESTAMP\\}", ""+System.currentTimeMillis())
		);
	}

	// NOTE: "public" -- used by PeacefulPetHeads
	/** Attempt to drop a head item for an Entity with a custom behead message.
	 * @param entity The entity for which to to create a head
	 * @param killer The entity which did the killing, or null
	 * @param evt The <code>Entity*Event</code> from which this function is being called
	 * @param weapon The weapon used to kill, or null
	 * @param getBeheadMessage A supplier for a the behead message that will be broadcasted
	 * @return Whether the head drop was successful
	 */
	public boolean triggerHeadDropEvent(Entity entity, Entity killer, Event evt, ItemStack weapon, Supplier<Component> getBeheadMessage){
//		pl.getLogger().info("trigger head drop called");
		ItemStack headItem = pl.getAPI().getHead(entity);
		EntityBeheadEvent beheadEvent = new EntityBeheadEvent(entity, killer, evt, headItem);
		pl.getServer().getPluginManager().callEvent(beheadEvent);
		if(beheadEvent.isCancelled()){
			if(DEBUG_MODE) pl.getLogger().info("EntityBeheadEvent was cancelled");
			return false;
		}

		dropHeadItem(headItem, entity, killer, evt);
		if(weapon != null && weapon.getType() == Material.AIR) weapon = null;
		announceHeadDrop(getBeheadMessage.get(), entity, killer, evt);
		if(entity instanceof Player ? LOG_PLAYER_BEHEAD : LOG_MOB_BEHEAD) logHeadDrop(entity, killer, weapon);
		return true;
	}
	/** Attempt to drop a head item for an Entity with the regular behead message.
	 * @param entity The entity for which to to create a head
	 * @param killer The entity which did the killing, or null
	 * @param evt The <code>Entity*Event</code> from which this function is being called
	 * @param weapon The weapon used to kill, or null
	 * @return Whether the head drop was completed successfully
	 */
	public boolean triggerHeadDropEvent(Entity entity, Entity killer, Event evt, ItemStack weapon){
		return triggerHeadDropEvent(entity, killer, evt, weapon, ()->getBeheadMessage(entity, killer, weapon));
	}

	//========== Friend EntityDeathListener
	/** Get raw drop chances (EntityType -> Double).
	 * @return An unmodifiable map (EntityType => drop chance)
	 */
	public Map<EntityType, Double> getEntityDropChances(EntityDeathListener.Friend f){
		Objects.requireNonNull(f);
		return dropChances.typeSettings();
	}

	//========== Friend CommandDropRate
	public boolean hasTimeAliveMults(CommandDropRate.Friend f){
		Objects.requireNonNull(f);
		return timeAliveMults.hasAnyValue();
	}
	public boolean hasWeaponMults(CommandDropRate.Friend f){
		Objects.requireNonNull(f);
		return weaponMults.hasAnyValue();
	}
	public boolean hasLootingMults(CommandDropRate.Friend f){
		Objects.requireNonNull(f);
		return lootingLevelAdd.hasAnyValue() || lootingLevelMult.hasAnyValue();
	}
	public EntitySetting<Set<Material>> getRequiredWeapons(CommandDropRate.Friend f){
		Objects.requireNonNull(f);
		return requiredWeapons;
	}
}