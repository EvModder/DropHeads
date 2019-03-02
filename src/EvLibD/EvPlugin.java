package EvLibD;

import java.io.InputStream;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public abstract class EvPlugin extends JavaPlugin{
	public static final String EvLib_ver = "1.2";

	protected FileConfiguration config;
	@Override public FileConfiguration getConfig(){return config;}
	@Override public void saveConfig(){
		if(config != null && !FileIO.saveConfig("config-"+getName()+".yml", config)){
			getLogger().severe("Error while saving plugin configuration file!");
		}
	}
	
	@Override public final void onEnable(){
//		getLogger().info("Loading " + getDescription().getFullName());
		InputStream defaultConfig = getClass().getResourceAsStream("/config.yml");
		if(defaultConfig != null){
			FileIO.verifyDir(this);
			config = FileIO.loadConfig(this, "config-"+getName()+".yml", defaultConfig);
		}
		onEvEnable();
	}
	
	@Override public final void onDisable(){
		onEvDisable();
	}
	
	public void onEvEnable(){}
	public void onEvDisable(){}
}
