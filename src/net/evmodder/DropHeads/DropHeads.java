package net.evmodder.DropHeads;

import net.evmodder.DropHeads.commands.*;
import net.evmodder.DropHeads.listeners.*;
import net.evmodder.EvLib.EvPlugin;
import net.evmodder.EvLib.Updater;

public final class DropHeads extends EvPlugin {
	private static DropHeads instance; public static DropHeads getPlugin(){return instance;}

	@Override public void onEvEnable(){
		if(config.getBoolean("update-plugin", true)){
			new Updater(this, 274151, getFile(), Updater.UpdateType.DEFAULT, false);
		}
		instance = this;
		new Utils();
		getServer().getPluginManager().registerEvents(new EntitySpawnListener(), this);
		getServer().getPluginManager().registerEvents(new EntityDeathListener(), this);
		getServer().getPluginManager().registerEvents(new ItemDropListener(), this);//To fix stacking
		//getServer().getPluginManager().registerEvents(new BlockBreakListener(), this);//To fix stacking

		new CommandSpawnHead(this);
		new Commanddebug_all_heads(this);
	}
}