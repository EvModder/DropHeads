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

//public record EntitySettings(
//	Map<String, EntitySettings> subtypeSettings,
//	String textureURL, Double droprate, DropMode dropMode, AnnounceMode announceMode,
//	DroprateMultiplier<String> multPermission, DroprateMultiplier<Material> multWeapon, DroprateMultiplier<Integer> multTimeAlive){
//}
public record EntitySettings<T>(T globalDefault, Map<EntityType, T> typeSettings, Map<String, T> subtypeSettings){
	public T get(EntityType type, T orDefault){
		return typeSettings != null ? typeSettings.getOrDefault(type, orDefault) : orDefault;
	}
	public T get(EntityType type){return get(type, globalDefault);}
	public T get(String key, T orDefault){
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
	public static <T> EntitySettings<T> fromConfigFile(@Nonnull final DropHeads pl, @Nonnull final String filename, @Nonnull final String resourceFilename,
			@Nonnull final T defaultValue, @Nonnull final BiFunction<String, String, T> valueParser)
	{
		final String defaultSettings = FileIO.loadResource(pl, resourceFilename, /*defaultContent=*/"");
		final HashSet<String> defaultRecognizedTypes = new HashSet<>();
		for(String line : defaultSettings.split("\n")){
			final String[] parts = line.replace(" ", "").replace("\t", "").toUpperCase().split(":");
			if(parts.length < 2) continue;
			defaultRecognizedTypes.add(parts[0]);
		}
		final String settings = FileIO.loadFile(filename, defaultSettings);

		final HashMap<EntityType, T> typeSettings = new HashMap<>();
		final HashMap<String, T> subtypeSettings = new HashMap<>();

		final BiFunction<String, Object, T> internalValueParser = (k, o)->valueParser.apply(k, (String)o);
		for(String line : settings.split("\n")){
			final String[] parts = line.replace(" ", "").replace("\t", "").toUpperCase().split(":");
			if(parts.length < 2) continue;
			parseTypeAndValue(pl, filename, parts[0], parts[1], defaultRecognizedTypes, typeSettings, subtypeSettings, internalValueParser);
		}
		//return new EntitySettings<T>(defaultValue, !typeSettings.isEmpty() ? typeSettings : null, !subtypeSettings.isEmpty() ? subtypeSettings : null);
		return new EntitySettings<T>(defaultValue, typeSettings, subtypeSettings);
	}
	public static <T> EntitySettings<T> fromConfigFile(@Nonnull final DropHeads pl, @Nonnull final String filename,
			@Nonnull final T defaultValue, @Nonnull final BiFunction<String, String, T> valueParser){
		return fromConfigFile(pl, filename, filename, defaultValue, valueParser);
	}

	@SuppressWarnings("unchecked")
	public static <T> EntitySettings<T> fromConfigSection(@Nonnull final DropHeads pl, @Nonnull final ConfigurationSection cs,
			boolean deep,
			@Nonnull final T defaultValue, final BiFunction<String, Object, T> valueParser)
	{
		if(cs == null) return new EntitySettings<T>(defaultValue, null, null);

		final HashMap<EntityType, T> typeSettings = new HashMap<>();
		final HashMap<String, T> subtypeSettings = new HashMap<>();

		final BiFunction<String, Object, T> internalValueParser;
		if(valueParser != null) internalValueParser = valueParser;
//		else internalValueParser = (k,v)->defaultValue.getClass().isInstance(v) ? (T)v : null;
		else internalValueParser = (k,v)->{
			if(defaultValue.getClass().isInstance(v)) return (T)v;
			pl.getLogger().severe("Invalid value for "+k+" in '"+cs.getCurrentPath()+"': "+v);
			return null;
		};
		cs.getValues(deep).forEach((k, v) ->
			parseTypeAndValue(pl, cs.getCurrentPath(), k, v, null, typeSettings, subtypeSettings, internalValueParser)
		);
		return new EntitySettings<T>(defaultValue, typeSettings, subtypeSettings);
		//return new EntitySettings<T>(defaultValue, !typeSettings.isEmpty() ? typeSettings : null, !subtypeSettings.isEmpty() ? subtypeSettings : null);
	}
};