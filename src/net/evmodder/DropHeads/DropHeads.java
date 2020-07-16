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
// * /droprate - check or edit per mob (& cmd for spawn modifiers)
// * mob type in item lore
// * attempt-place-head-block, attempt-place-overwrite-liquids, facing-direction, place-as: KILLER/VICTIM/SERVER, what to do if blockplaceevent fails
// * overwrite blocks: ['AIR', 'WATER, 'GRASS']
// * fancy stray skull
// * jeb_ sheep head animated phasing through colors (like the jeb_ sheep)
// * if mob has custom name, use it in head name (configurable)
// * move textures from head-textures.txt to DropHeads/textures/MOB_NAME.txt => "SHEEP|RED: value \n SHEEP|BLUE: value ..."
// * using above, inside /textures/MOB_NAME.txt, set 'drop-rate: x' to modify chance for that sub-type only
// * Multiple possible behead messages, with one picked randomly EG:["$ was beheaded", "$ lost their head", "$ got decapitated"]
// * cancel behead message broadcast if death message gets changed by another plugin (check in playerdeathevent with priority monitor?)
//TEST:
//* /gethead player:ShEeP, /gethead mob:SHEEP, /gethead hdb:334
/*
 * log:
 *   enable: true
 *   filename: 'dropheads-log.txt'
 *   log-mob-behead: true
 *   log-player-behead: true
 *   log-head-command: true
 *   format-mob-behead: '${timestamp},mob decapitated,${victim},${killer},${item}'
 *   format-player-behead: '${timestamp},player decapitated,${victim},${killer},${item}'
 *   format-head-command: '${timestamp},gethead command,${sender},${head}'
 */

public final class DropHeads extends EvPlugin{
	private static DropHeads instance; public static DropHeads getPlugin(){return instance;}
	private HeadAPI api;
	public HeadAPI getAPI(){return api;}
	private boolean LOGFILE_ENABLED;
	private String LOGFILE_NAME;

	@Override public void onEvEnable(){
		if(config.getBoolean("update-plugin", true)){
			new Updater(/*plugin=*/this, /*id=*/274151, getFile(), Updater.UpdateType.DEFAULT, /*announce=*/true);
		}
		instance = this;
		api = new HeadAPI();
		getServer().getPluginManager().registerEvents(new EntityDeathListener(), this);
		if(config.getBoolean("track-mob-spawns", true)){
			getServer().getPluginManager().registerEvents(new EntitySpawnListener(), this);
		}
		if(config.getBoolean("drop-for-ranged-kills", false)){
			getServer().getPluginManager().registerEvents(new ProjectileFireListener(), this);
		}
		if(config.getBoolean("drop-for-indirect-kills", false)){
			getServer().getPluginManager().registerEvents(new EntityDamageListener(), this);
		}
		if(config.getBoolean("refresh-textures", false)){
			getServer().getPluginManager().registerEvents(new ItemDropListener(), this);
		}
		if(config.getBoolean("head-click-listener", true)){
			getServer().getPluginManager().registerEvents(new BlockClickListener(), this);
		}
		if(config.getBoolean("save-custom-lore", true)){
			getServer().getPluginManager().registerEvents(new BlockPlaceListener(), this);
			getServer().getPluginManager().registerEvents(new BlockBreakListener(), this);
		}

		new CommandSpawnHead(this);
		new Commanddebug_all_heads(this);

		LOGFILE_ENABLED = config.getBoolean("log.enable", false);
		if(LOGFILE_ENABLED) LOGFILE_NAME = FileIO.DIR+config.getString("log.filename", "log.txt");
	}

	public boolean writeToLogFile(String line){
		if(!LOGFILE_ENABLED) return false;
		// Write to log
		line = line.replace("\n", "")+"\n";
		return FileIO.saveFile(LOGFILE_NAME, line, /*append=*/true);
	}
}