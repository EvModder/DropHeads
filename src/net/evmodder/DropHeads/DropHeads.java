/*
 * DropHeds - a Bukkit plugin for naturally dropping mob heads
 *
 * Copyright (C) 2017 - 2019 Nathan / EvModder
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
import net.evmodder.EvLib.Updater;

//TODO:
// * Adjust drop rate based on getTicksLived()
// * Log of head drop events
// * fancy stray skull
// * display head info when clicked
// * move textures from head-textures.txt to DropHeads/textures/MOB_NAME.txt => "SHEEP|RED: value \n SHEEP|BLUE: value ..."
// * using above, inside /textures/MOB_NAME.txt, set 'drop-rate: x' to modify chance for that sub-type only
// * Multiple possible behead messages, with one picked randomly EG:["$ was beheaded", "$ lost their head", "$ got decapitated"]
public final class DropHeads extends EvPlugin{
	private static DropHeads instance; public static DropHeads getPlugin(){return instance;}
	private HeadAPI api;
	public HeadAPI getAPI(){return api;}

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
		if(config.getBoolean("drop-for-indirect-kills", false)){
			getServer().getPluginManager().registerEvents(new EntityDamageListener(), this);
		}
		if(config.getBoolean("refresh-textures", false)){
			getServer().getPluginManager().registerEvents(new ItemDropListener(), this);
//			getServer().getPluginManager().registerEvents(new BlockBreakListener(), this);
		}
		if(config.getBoolean("head-click-listener", true)){
			getServer().getPluginManager().registerEvents(new BlockClickListener(), this);
		}
		if(config.getBoolean("save-custom-lore", true)){
			getServer().getPluginManager().registerEvents(new BlockPlaceListener(), this);
		}

		new CommandSpawnHead(this);
		new Commanddebug_all_heads(this);
	}
}