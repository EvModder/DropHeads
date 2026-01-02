/*
 * DropHeads - a Bukkit plugin for naturally dropping mob heads
 *
 * Copyright (C) 2017 - 2022 Nathan / EvModder
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.evmodder.DropHeads;

import org.bukkit.configuration.Configuration;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Stream;
import org.bukkit.configuration.file.YamlConfiguration;
import net.evmodder.DropHeads.commands.*;
import net.evmodder.DropHeads.datatypes.EntitySetting;
import net.evmodder.DropHeads.datatypes.NoteblockMode;
import net.evmodder.DropHeads.listeners.*;
import net.evmodder.EvLib.bukkit.EvPlugin;
import net.evmodder.EvLib.bukkit.ConfigUtils;
import net.evmodder.EvLib.bukkit.Updater;
import net.evmodder.EvLib.util.FileIO;

//TODO:
// * /dropheads reload
// * rotate painting heads ('face' = painting)
// pkillonly,ranged-kills,indirect-kill-threshold,+ config per-mob?
// * creaking head obt
// * improve textures listed at the bottom of head-textures.txt
// * for non-living (Vehicles, Hanging), cancel self-drop if head drop is triggered (configurable)
// * un-dye heads (sheep,shulker) with cauldron (gimmick). Note that washing banners only removes pattern, not color
// * jeb_ sheep head animated phase through colors (gimmick)
// * full /minecraft:give command support (namespaced custom heads in /give)
// * use 'fallback' in TextUtils for head-type, etc.
// * img.shields/io/bukkit/downloads/id ? other badges on GitHub?
// * mob: prefix for /droprate
// * create tester tool to call `getHead(entity)` for every supported variant
// * maybe add another 0 to the threshold for final drop chance vs raw drop chance for deciding to print it in /droprate
// * Evlib-TellrawUtils: make ListComponent immutable (gotta pass Component[] to constructor, and it can optimize/flatten them
// as much as it likes, cuz u can't do .add() later)
//TEST:
// * /droprate edit
// * head-noteblock-sound in ItemMeta
// * Trophies/Luck attribute
// * place-head-block, overwrite-blocks, facing-direction, place-as: KILLER/VICTIM/SERVER
// * middle-click copy with correct item name // WORKS well enough; can still bug out if u middle-click twice
// * update-textures=true (head-textures.txt file overwritten when plugin is updated)
//Export -> JAVADOC -> javadoc sources:
// * EvLib: https://evmodder.github.io/EvLib/
// * HeadDatabaseAPI: https://javadoc.io/doc/com.arcaniax/HeadDatabase-API/1.3.1/
// * Bukkit-1.13: https://hub.spigotmc.org/javadocs/bukkit/
// Search > File... > containing text X > replace Y
// ` TellrawUtils.` -> ` `, `(TellrawUtils.` -> `(`, `>TellrawUtils.` -> `>`, `(HeadUtils.` -> `(`

public final class DropHeads extends EvPlugin{
	private static DropHeads instance; public static DropHeads getPlugin(){return instance;}
	private InternalAPI api; public HeadAPI getAPI(){return api;} public InternalAPI getInternalAPI(){return api;}
	private DropChanceAPI dropChanceAPI; public DropChanceAPI getDropChanceAPI(){return dropChanceAPI;}
	private EntityDeathListener entityDeathListener; public EntityDeathListener getEntityDeathListener(){return entityDeathListener;}
	private DeathMessagePacketIntercepter deathMessageBlocker;
	private boolean LOGFILE_ENABLED;
	private String LOGFILE_NAME;

	@Override public void reloadConfig(){
		InputStream defaultConfig = getClass().getResourceAsStream("/configs/config.yml");
		ConfigUtils.verifyDir(this);
		config = ConfigUtils.loadConfig(this, "config-"+getName()+".yml", defaultConfig, /*notifyIfNew=*/true);
	}

	@Override public void onEvEnable(){
		if(config.getBoolean("update-plugin", false)){
			new Updater(/*plugin=*/this, /*id=*/274151, getFile(), Updater.UpdateType.DEFAULT, /*callback=*/null, /*announce=*/true);
		}
		if(config.getBoolean("bstats-enabled", false) && !config.getBoolean("new")){
//			MetricsLite metrics =
					new MetricsLite(this, /*bStats id=*/20140);
//			metrics.addCustomChart(new MetricsLite.SimplePie("player_heads_only", ()->""+config.getBoolean("player-heads-only", false)));
//			metrics.addCustomChart(new MetricsLite.SimplePie("simple_mob_heads_only", ()->""+config.getBoolean("simple-mob-heads-only", false)));
//			metrics.addCustomChart(new MetricsLite.MultiLineChart("behead_events", new Callable<Map<String, Integer>>(){
//				@Override public Map<String, Integer> call() throws Exception{
//					Map<String, Integer> valueMap = new HashMap<>();
//					valueMap.put("mobs", dropChanceAPI.numMobBeheads);
//					valueMap.put("players", dropChanceAPI.numPlayerBeheads);
//					dropChanceAPI.numMobBeheads = dropChanceAPI.numPlayerBeheads = 0;
//					return valueMap;
//				}
//			}));
		}
//		getLogger().warning("server version: "+ReflectionUtils.getServerVersionString());
		instance = this;
		final NoteblockMode m = MiscUtils.parseEnumOrDefault(config.getString("noteblock-mob-sounds", "OFF"), NoteblockMode.OFF);
		final boolean CRACKED_IRON_GOLEMS_ENABLED = config.getBoolean("cracked-iron-golem-heads", false);

		// Load translations
		final InputStream translationsIS = getClass().getResourceAsStream("/configs/translations.yml");
		final Configuration translations = ConfigUtils.loadConfig(this, "translations.yml", translationsIS, /*notifyIfNew=*/false);
		// No need to assign defaults if new (because it was just copied. Also the InputStream will be invalid)
		if(!translations.getBoolean("new")) translations.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(translationsIS)));
		//config.addDefaults(translations); // Caller-supplied defaults will always overrided these, so I can't use it :(
		for(String key : translations.getKeys(true)) if(!config.isSet(key)) config.set(key, translations.get(key));

		// Load entity-settings
		final Configuration entitySettings = ConfigUtils.loadConfig(this, "entity-settings.yml",
				getClass().getResourceAsStream("/configs/entity-settings.yml"), /*notifyIfNew=*/false);
		for(String key : entitySettings.getKeys(true)) if(!config.isSet(key)) config.set(key, entitySettings.get(key));

		api = new InternalAPI(m, CRACKED_IRON_GOLEMS_ENABLED);
		final boolean WANT_TO_REPLACE_PLAYER_DEATH_MSG = config.getBoolean("behead-announcement-replaces-player-death-message", true);
		final boolean GLOBAL_PLAYER_BEHEAD_MSG = config.getString("behead-announcement.player",
				config.getString("behead-announcement.default", "GLOBAL")).toUpperCase().equals("GLOBAL");
		if(WANT_TO_REPLACE_PLAYER_DEATH_MSG && !GLOBAL_PLAYER_BEHEAD_MSG){
			getLogger().warning("behead-announcement-replaces-player-death-message is true, but behead-announcement.player is not GLOBAL");
		}
		final boolean REPLACE_PLAYER_DEATH_MSG = WANT_TO_REPLACE_PLAYER_DEATH_MSG && GLOBAL_PLAYER_BEHEAD_MSG;
		final boolean REPLACE_PET_DEATH_MSG = config.getBoolean("behead-message-replaces-pet-death-message", true);
		if(REPLACE_PLAYER_DEATH_MSG || REPLACE_PET_DEATH_MSG){
			deathMessageBlocker = new DeathMessagePacketIntercepter(REPLACE_PLAYER_DEATH_MSG, REPLACE_PET_DEATH_MSG);
		}
		dropChanceAPI = new DropChanceAPI(REPLACE_PLAYER_DEATH_MSG, REPLACE_PET_DEATH_MSG, deathMessageBlocker);

		
		final EntitySetting<Boolean> allowNonPlayerKills = EntitySetting.fromConfig(this, "drop-for-nonplayer-kills", false, null);
		final EntitySetting<Boolean> allowIndirectPlayerKills = EntitySetting.fromConfig(this, "drop-for-indirect-player-kills", false, null);
		Stream.concat(
			allowIndirectPlayerKills.typeSettings() == null ? Stream.of() :
			allowIndirectPlayerKills.typeSettings().entrySet().stream()
				.filter(e -> e.getValue() && allowNonPlayerKills.get(e.getKey())).map(e -> e.getKey().name()),
			allowIndirectPlayerKills.subtypeSettings() == null ? Stream.of() :
			allowIndirectPlayerKills.subtypeSettings().entrySet().stream()
				.filter(e -> e.getValue() && allowNonPlayerKills.get(e.getKey())).map(e -> e.getKey())
		).forEach(e ->{
			getLogger().warning("drop-for-indirect-player-kills is true for '"+e+"', which is unnecessary because this mob does not require a player to kill");
		});
		final EntitySetting<Boolean> allowProjectileKills = EntitySetting.fromConfig(this, "drop-for-ranged-kills", false, null);
		final boolean TRACK_RANGED_WEAPON_FOR_LOOTING = allowProjectileKills.hasAnyValue() && config.getBoolean("use-ranged-weapon-for-looting", true);
		new EntityDeathListener(deathMessageBlocker, allowNonPlayerKills, allowIndirectPlayerKills, allowProjectileKills, TRACK_RANGED_WEAPON_FOR_LOOTING);

		if(config.getBoolean("track-mob-spawns", true)){
			getServer().getPluginManager().registerEvents(new EntitySpawnListener(), this);
		}
		if(TRACK_RANGED_WEAPON_FOR_LOOTING){
			// TODO: (intersection of allowNonPlayerKills && allowProjectileKills).hasAnyValue()
			getServer().getPluginManager().registerEvents(new ProjectileFireListener(allowNonPlayerKills.hasAnyValue()), this);
		}
		if(allowIndirectPlayerKills.hasAnyValue()){
			getServer().getPluginManager().registerEvents(new EntityDamageListener(allowNonPlayerKills, allowIndirectPlayerKills, allowProjectileKills), this);
		}
		if(config.getBoolean("refresh-textures", false)){
			getServer().getPluginManager().registerEvents(new ItemDropListener(), this);
		}
		if(config.getBoolean("head-click-listener", true) || CRACKED_IRON_GOLEMS_ENABLED){
			getServer().getPluginManager().registerEvents(new BlockClickListener(), this);
		}
		if(config.getBoolean("save-custom-lore", true)){
			getServer().getPluginManager().registerEvents(new LoreStoreBlockPlaceListener(), this);
			getServer().getPluginManager().registerEvents(new LoreStoreBlockBreakListener(), this);
		}
		if(config.getBoolean("fix-creative-nbt-copy", true)){
			getServer().getPluginManager().registerEvents(new CreativeMiddleClickListener(), this);
		}
		//TODO: Wait for Minecraft to support custom-namespaced items in /give
//		if(config.getBoolean("substitute-dropheads-in-give-command", false)){
//			getServer().getPluginManager().registerEvents(new GiveCommandPreprocessListener(), this);
//		}
		if(!config.getStringList("endermen-camouflage-heads").isEmpty()){
			getServer().getPluginManager().registerEvents(new EndermanProvokeListener(), this);
		}
		if(m == NoteblockMode.LISTENER){
			getServer().getPluginManager().registerEvents(new NoteblockPlayListener(), this);
		}

		new CommandSpawnHead(this);
		new CommandDropRate(this);
		new Commanddebug_all_heads(this);

		LOGFILE_ENABLED = config.getBoolean("log.enable", false);
		if(LOGFILE_ENABLED) LOGFILE_NAME = config.getString("log.filename", "log.txt");
	}

	@Override public void onEvDisable(){if(deathMessageBlocker != null) deathMessageBlocker.unregisterAll();}

	public boolean writeToLogFile(String line){
		if(!LOGFILE_ENABLED) return false;
		// Write to log
		line = line.replace("\n", "")+"\n";
		getLogger().fine("Writing line to logfile: "+line);
		return FileIO.saveFile(LOGFILE_NAME, line, /*append=*/true);
	}
}