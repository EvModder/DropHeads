package EvLibD;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import EvLibD.FileIO;

public abstract class EvPlugin extends JavaPlugin{
	protected FileConfiguration config;
	@Override public FileConfiguration getConfig(){return config;}
	@Override public void saveConfig(){
		if(config != null)
		try{config.save(new File("./plugins/EvFolder/config-"+getName()+".yml"));}
		catch(IOException ex){ex.printStackTrace();}
	}
	
	@Override public final void onEnable(){
//		getLogger().info("Loading " + getDescription().getFullName());
		InputStream defaultConfig = getClass().getResourceAsStream("/config.yml");
		if(defaultConfig != null) config = FileIO.loadConfig(this, "config-"+getName()+".yml", defaultConfig);
		onEvEnable();
	}
	
	@Override public final void onDisable(){
		onEvDisable();
	}
	
	public void onEvEnable(){}
	public void onEvDisable(){}
}
