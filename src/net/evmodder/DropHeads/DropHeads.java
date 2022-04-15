/*
 * DropHeads - a Bukkit plugin for naturally dropping mob heads
 *
 * Copyright (C) 2017 - 2020 Nathan / EvModder
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

import net.evmodder.DropHeads.commands.*;
import net.evmodder.DropHeads.listeners.*;
import net.evmodder.EvLib.EvPlugin;
import net.evmodder.EvLib.FileIO;
import net.evmodder.EvLib.Updater;

//TODO:
// * /dropheads reload
// * Refactor CommandSpawnHead (split it up!)
// * improve the bad textures listed at the bottom of head-textures.txt
// * move textures to DropHeads/textures/MOB_NAME.txt => "SHEEP|RED: value \n SHEEP|BLUE: value ..."
// * for non-living (Vehicles, Hanging), cancel self-drop if head drop is triggered (configurable)
// * un-dye heads (sheep,shulker) with cauldron (gimmick)
// * jeb_ sheep head animated phase through colors (gimmick)
// * ALL player-visible msgs in plugin translated in translations.yml
// * full /minecraft:give command support
//TEST:
// * replace death message for pets and players with behead message
// * Trophies/Luck attribute
// * hide behead msgs for vanished players
// * Multiple possible behead messages, with one picked randomly EG:["$ was beheaded", "$ lost their head", "$ got decapitated"]
// * place-head-block, overwrite-blocks, facing-direction, place-as: KILLER/VICTIM/SERVER
// * middle-click copy with correct item name // WORKS well enough; can still bug out if u middle-click twice
// * head-item-drop-mode
// * hollow skeletal skulls
// * /droprate - check (TODO: or edit) per mob (& cmd for spawn modifiers)
// * prevent placing heads
// * update-textures=true (head-textures.txt file overwritten when plugin is updated)

public final class DropHeads extends EvPlugin{
	private static DropHeads instance; public static DropHeads getPlugin(){return instance;}
	private HeadAPI api; public HeadAPI getAPI(){return api;}
	private DropChanceAPI dropChanceAPI; public DropChanceAPI getDropChanceAPI(){return dropChanceAPI;}
	private DeathMessagePacketIntercepter deathMessageBlocker;
	private boolean LOGFILE_ENABLED;
	private String LOGFILE_NAME;

	@Override public void onEvEnable(){
		if(config.getBoolean("update-plugin", false)){
			new Updater(/*plugin=*/this, /*id=*/274151, getFile(), Updater.UpdateType.DEFAULT, /*announce=*/true);
			//todo: if 'update-textures', trigger overwrite in HeadAPI
		}
		instance = this;
		api = new HeadAPI();
		final boolean GLOBAL_PLAYER_BEHEAD_MSG = config.getString("behead-announcement.player",
				config.getString("behead-announcement.default", "GLOBAL")).toUpperCase().equals("GLOBAL");
		final boolean WANT_TO_REPLACE_PLAYER_DEATH_MSG = config.getBoolean("behead-announcement-replaces-player-death-message",
				config.getBoolean("behead-announcement-replaces-death-message", true));
		if(WANT_TO_REPLACE_PLAYER_DEATH_MSG && !GLOBAL_PLAYER_BEHEAD_MSG){
			getLogger().warning("behead-announcement-replaces-player-death-message is true, but behead-announcement.player is not GLOBAL");
		}
		final boolean REPLACE_PLAYER_DEATH_MSG = WANT_TO_REPLACE_PLAYER_DEATH_MSG && GLOBAL_PLAYER_BEHEAD_MSG;
		final boolean REPLACE_PET_DEATH_MSG = config.getBoolean("behead-message-replaces-pet-death-message", true);
		if(REPLACE_PLAYER_DEATH_MSG || REPLACE_PET_DEATH_MSG){
			deathMessageBlocker = new DeathMessagePacketIntercepter(REPLACE_PLAYER_DEATH_MSG, REPLACE_PET_DEATH_MSG);
		}
		dropChanceAPI = new DropChanceAPI(REPLACE_PLAYER_DEATH_MSG, REPLACE_PET_DEATH_MSG);
		new EntityDeathListener(deathMessageBlocker);

		if(config.getBoolean("track-mob-spawns", true)){
			getServer().getPluginManager().registerEvents(new EntitySpawnListener(), this);
		}
		if(config.getBoolean("drop-for-ranged-kills", false)){
			getServer().getPluginManager().registerEvents(new ProjectileFireListener(), this);
		}
		if(config.getBoolean("drop-for-indirect-kills", false) && !config.getBoolean("drop-for-nonplayer-kills", false)){
			getServer().getPluginManager().registerEvents(new EntityDamageListener(), this);
		}
		if(config.getBoolean("refresh-textures", false)){
			getServer().getPluginManager().registerEvents(new ItemDropListener(), this);
		}
		if(config.getBoolean("head-click-listener", true) || config.getBoolean("cracked-iron-golem-heads", false)){
			getServer().getPluginManager().registerEvents(new BlockClickListener(api.translationsFile), this);
		}
		if(config.getBoolean("save-custom-lore", true)){
			getServer().getPluginManager().registerEvents(new LoreStoreBlockPlaceListener(), this);
			getServer().getPluginManager().registerEvents(new LoreStoreBlockBreakListener(), this);
		}
		if(config.getBoolean("fix-creative-nbt-copy", true)){
			getServer().getPluginManager().registerEvents(new CreativeMiddleClickListener(), this);
		}
		if(config.getBoolean("prevent-head-placement", false)){
			getServer().getPluginManager().registerEvents(new PreventBlockPlaceListener(), this);
		}
		if(config.getBoolean("substitute-dropheads-in-give-command", false)){
			getServer().getPluginManager().registerEvents(new GiveCommandPreprocessListener(), this);
		}
		if(!config.getStringList("endermen-camouflage-heads").isEmpty()){
			getServer().getPluginManager().registerEvents(new EndermanProvokeListener(), this);
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