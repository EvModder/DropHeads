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
// * improve the bad textures listed at the bottom of head-textures.txt
// * Trophies/Luck attribute
// * move textures to DropHeads/textures/MOB_NAME.txt => "SHEEP|RED: value \n SHEEP|BLUE: value ..."
// * for non-living (Vehicles, Hanging), cancel self-drop if head drop is triggered (configurable)
// * BUG: Charged creeper can cause 2 heads to drop (1 vanilla and 1 non-vanilla head), and the vanilla charged creeper behead bypasses logs
// * BUG: to fix above bug, cancel vanilla charge creeper head drops
// * un-dye heads (sheep,shulker) with cauldron (gimmick)
// * jeb_ sheep head animated phase through colors (gimmick)
// * if mob has custom name, use it in head name (config option)
//TEST:
// * wearing enderman head to look at endermen safely
// * all player-visible msgs in plugin are translated using translations.yml
// * hide behead msgs for vanished players
// * Multiple possible behead messages, with one picked randomly EG:["$ was beheaded", "$ lost their head", "$ got decapitated"]
// * Cracked iron golem head / repair with ingot
// * place-head-block, overwrite-blocks, facing-direction, place-as: KILLER/VICTIM/SERVER
// * middle-click copy with correct item name // WORKS well enough; can still bug out if u middle-click twice
// * head-item-drop-mode
// * hollow skeletal skulls
// * /droprate - check (TODO: or edit) per mob (& cmd for spawn modifiers)
// * prevent placing heads
// * update-textures=true (head-textures.txt file overwritten when plugin is updated)
// * new permissions for head click-info

public final class DropHeads extends EvPlugin{
	private static DropHeads instance; public static DropHeads getPlugin(){return instance;}
	private HeadAPI api; public HeadAPI getAPI(){return api;}
	private boolean LOGFILE_ENABLED;
	private String LOGFILE_NAME;

	@Override public void onEvEnable(){
		if(config.getBoolean("update-plugin", false)){
			new Updater(/*plugin=*/this, /*id=*/274151, getFile(), Updater.UpdateType.DEFAULT, /*announce=*/true);
			//todo: if 'update-textures', trigger overwrite in HeadAPI
		}
		instance = this;
		api = new HeadAPI();
		EntityDeathListener deathListener = new EntityDeathListener();
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
		if(!config.getStringList("endermen-camouflage-heads").isEmpty()){
			getServer().getPluginManager().registerEvents(new EndermanProvokeListener(), this);
		}

		new CommandSpawnHead(this);
		new CommandDropRate(this, deathListener);
		new Commanddebug_all_heads(this);

		LOGFILE_ENABLED = config.getBoolean("log.enable", false);
		if(LOGFILE_ENABLED) LOGFILE_NAME = config.getString("log.filename", "log.txt");
	}

	public boolean writeToLogFile(String line){
		if(!LOGFILE_ENABLED) return false;
		// Write to log
		line = line.replace("\n", "")+"\n";
		getLogger().fine("Writing line to logfile: "+line);
		return FileIO.saveFile(LOGFILE_NAME, line, /*append=*/true);
	}
}