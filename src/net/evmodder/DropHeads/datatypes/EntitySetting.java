package net.evmodder.DropHeads.datatypes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.BiFunction;
import javax.annotation.Nonnull;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.TextureKeyLookup;
import net.evmodder.EvLib.FileIO;

//public record EntitySetting(
//	Map<String, EntitySetting> subtypeSettings,
//	String textureURL, Double droprate, DropMode dropMode, AnnounceMode announceMode,
//	DroprateMultiplier<String> multPermission, DroprateMultiplier<Material> multWeapon, DroprateMultiplier<Integer> multTimeAlive){
//}
public record EntitySetting<T>(T globalDefault, Map<EntityType, T> typeSettings, Map<String, T> subtypeSettings){
	public T get(EntityType type, T orDefault){
		return typeSettings != null && type != null ? typeSettings.getOrDefault(type, orDefault) : orDefault;
	}
	public T get(EntityType type){return get(type, globalDefault);}
	public T get(String key, T orDefault){
		if(key == null) return orDefault;
		if(subtypeSettings != null){
			int lastSep = key.lastIndexOf('|');
			T subtypeValue = null;
			while(lastSep != -1 && (subtypeValue=subtypeSettings.get(key)) == null){
				key = key.substring(0, lastSep);
				lastSep = key.lastIndexOf('|');
			}
			if(subtypeValue != null) return subtypeValue;
		}
		final int firstSep = key.indexOf('|');
		final String eName = firstSep == -1 ? key : key.substring(0, firstSep);
		try{return get(EntityType.valueOf(eName), orDefault);}
		catch(IllegalArgumentException ex){return orDefault;}
	}
	public T get(String key){return get(key, globalDefault);}
	public T get(Entity entity, T orDefault){
		if(entity == null) return orDefault;
		if(subtypeSettings != null){
			String textureKey = TextureKeyLookup.getTextureKey(entity);
			int keyDataTagIdx = textureKey.lastIndexOf('|');
			T subtypeValue = null;
			while(keyDataTagIdx != -1 && (subtypeValue=subtypeSettings.get(textureKey)) == null){
				textureKey = textureKey.substring(0, keyDataTagIdx);
				keyDataTagIdx = textureKey.lastIndexOf('|');
			}
			if(subtypeValue != null) return subtypeValue;
		}
		return get(entity.getType(), orDefault);
	}
	public T get(Entity entity){return get(entity, globalDefault);}
	public boolean hasAnyValue(){return (typeSettings != null && !typeSettings.isEmpty()) || (subtypeSettings != null && !subtypeSettings.isEmpty());}

	private static boolean isEntityType(String key){
		final int dataTagSep = key.indexOf('|');
		final String eName = (dataTagSep == -1 ? key : key.substring(0, dataTagSep)).toUpperCase();
		if(eName.equals("DEFAULT")) return true;
		try{EntityType.valueOf(eName); return true;}
		catch(IllegalArgumentException ex){return false;}
	}

	/** Parses {EntityType,T} from {String,Object} using @valueParser and stores in @typeSettings or @subtypeSettings
	 * @param pl DropHeads plugin
	 * @param source Where the data is from (only used for error logging)
	 * @param key String to parse to EntityType
	 * @param value Object to parse to T
	 * @param typeSettings Map<EntityType, T> to store in
	 * @param subtypeSettings Map<TextureKey(String), T> to store in
	 * @param valueParser apply for Object->T
	 * @return Whether the texture exists
	 */
	private static <T> void parseTypeAndValue(@Nonnull final DropHeads pl, @Nonnull final String source,
			@Nonnull final String key, @Nonnull final Object value,
			final HashSet<String> defaultRecognizedTypes,
			@Nonnull final HashMap<EntityType, T> typeSettings, @Nonnull final HashMap<String, T> subtypeSettings,
			@Nonnull final BiFunction<String, Object, T> valueParser)
	{
		final int dataTagSep = key.indexOf('|');
		final String eName = dataTagSep == -1 ? key : key.substring(0, dataTagSep);
		final EntityType type;
		try{type = EntityType.valueOf(eName.replace("DEFAULT", "UNKNOWN"));}
		catch(IllegalArgumentException ex){
			// Only throw an error for mobs that aren't defined in the default config (which may be from past/future MC versions)
			if(defaultRecognizedTypes != null && !defaultRecognizedTypes.contains(eName)){
				pl.getLogger().severe("Unknown EntityType in '"+source+"': "+eName);
			}
			return;
		}
		final T t = valueParser.apply(key, value);
		if(t == null){
//			pl.getLogger().severe("Invalid value for "+key+" in '"+source+"': "+value);
			return;
		}
		if(dataTagSep == -1) typeSettings.put(type, t);
		else if(!pl.getAPI().textureExists(key)) pl.getLogger().severe("Unknown entity sub-type in '"+source+"': "+key);
		else subtypeSettings.put(key, t);
	}

	// Note: currently this is only called by DropChanceAPI, and only for one variable: mobChances
	/** Parses EntitySetting<T> from a raw text file
	 * @param pl DropHeads plugin
	 * @param filename File to read from
	 * @param resourceFilename Default file contents packaged in the jar
	 * @param defaultValue Default value for T
	 * @param valueParser
	 * @param subtypeSettings Map<TextureKey(String), T> to store in
	 * @param valueParser for (String key, String value)->T
	 * @return EntitySetting<T>
	 */
	public static <T> EntitySetting<T> fromTextFile(@Nonnull final DropHeads pl, @Nonnull final String filename, @Nonnull final String resourceFilename,
			@Nonnull final T defaultValue, @Nonnull final BiFunction<String, String, T> valueParser)
	{
		final String defaultSettings = FileIO.loadResource(pl, resourceFilename, /*defaultContent=*/"");
		final String settings = FileIO.loadFile(filename, defaultSettings);
		if(settings.isBlank()) return new EntitySetting<T>(defaultValue, null, null);

		final HashSet<String> defaultRecognizedTypes = new HashSet<>();
		for(String line : defaultSettings.split("\n")){
			final String[] parts = line.replace(" ", "").replace("\t", "").toUpperCase().split(":");
			if(parts.length < 2) continue;
			defaultRecognizedTypes.add(parts[0]);
		}

		final HashMap<EntityType, T> typeSettings = new HashMap<>();
		final HashMap<String, T> subtypeSettings = new HashMap<>();

		final BiFunction<String, Object, T> internalValueParser = (k, o)->valueParser.apply(k, (String)o);
		for(String line : settings.split("\n")){
			final String[] parts = line.replace(" ", "").replace("\t", "").toUpperCase().split(":");
			if(parts.length < 2) continue;
			parseTypeAndValue(pl, filename, parts[0], parts[1], defaultRecognizedTypes, typeSettings, subtypeSettings, internalValueParser);
		}
		final T globalDefault = typeSettings.getOrDefault(EntityType.UNKNOWN, defaultValue);
		return new EntitySetting<T>(globalDefault, !typeSettings.isEmpty() ? typeSettings : null, !subtypeSettings.isEmpty() ? subtypeSettings : null);
	}
//	public static <T> EntitySetting<T> fromFile(@Nonnull final DropHeads pl, @Nonnull final String filename,
//			@Nonnull final T defaultValue, @Nonnull final BiFunction<String, String, T> valueParser){
//		return fromConfigFile(pl, filename, "/configs/"+filename, defaultValue, valueParser);
//	}

	/** Parses EntitySetting<T> from YML ConfigurationSection
	 * @param pl DropHeads plugin
	 * @param cs ConfigurationSection
	 * @param valueParser (String key, Object value)->T
	 * @return EntitySetting<T>
	 */
	@SuppressWarnings("unchecked")
	public static <T> EntitySetting<T> fromConfig(@Nonnull final DropHeads pl, @Nonnull final String path,
			//boolean deep,
			@Nonnull final T defaultValue, final BiFunction<String, Object, T> valueParser)
	{
		if(!pl.getConfig().contains(path)){
			pl.getLogger().warning("Value is missing from config/entity-settings: "+path);
			return new EntitySetting<T>(defaultValue, null, null);
		}

		final HashMap<EntityType, T> typeSettings = new HashMap<>();
		final HashMap<String, T> subtypeSettings = new HashMap<>();

		final BiFunction<String, Object, T> internalValueParser;
		if(valueParser != null) internalValueParser = valueParser;
//		else internalValueParser = (k,v)->defaultValue.getClass().isInstance(v) ? (T)v : null;
		else internalValueParser = (k,v)->{
			if(defaultValue.getClass().isInstance(v)) return (T)v;
			pl.getLogger().severe("Invalid entity-setting in "+path+" for '"+k+"': "+v);
			return null;
		};
		ConfigurationSection cs = pl.getConfig().isConfigurationSection(path) ? pl.getConfig().getConfigurationSection(path) : null;
		// No ConfigurationSection indicates no per-EntityType details, so just attempt to parse as default (for all entities)
		if(cs == null){
			pl.getLogger().fine("EntitySetting for "+path+" is not a ConfigSection, parsing it as a default");
			final T t = internalValueParser.apply("DEFAULT", pl.getConfig().get(path));
			return t == null ? null : new EntitySetting<T>(t, /*typeSettings=*/null, /*subtypeSettings=*/null);
		}
		Map<String, Object> values = cs.getValues(/*deep=*/false);
		// ConfigurationSection detected, but the keys are not EntityTypes/textureKeys, so again just attempt to parse as a single default value
		if(!values.isEmpty() && values.keySet().stream().noneMatch(EntitySetting::isEntityType)){
			final T t = valueParser.apply("DEFAULT", cs);
			return t == null ? null : new EntitySetting<T>(t, /*typeSettings=*/null, /*subtypeSettings=*/null);
		}
		values.forEach((k, v) ->
			parseTypeAndValue(pl, cs.getCurrentPath(), k.toUpperCase(), v, /*defaultRecognizedTypes=*/null, typeSettings, subtypeSettings, internalValueParser)
		);
		final T globalDefault = typeSettings.getOrDefault(EntityType.UNKNOWN, defaultValue);
		return new EntitySetting<T>(globalDefault, !typeSettings.isEmpty() ? typeSettings : null, !subtypeSettings.isEmpty() ? subtypeSettings : null);
	}
};