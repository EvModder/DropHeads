package Evil_Code_DropHeads;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.metadata.FixedMetadataValue;

import EvLibD.FileIO;

public class EntitySpawnListener implements Listener{
	private Map<SpawnReason, Float> spawnModifiers = new HashMap<SpawnReason, Float>();
	private DropHeads plugin;
	
	public EntitySpawnListener(){
		plugin = DropHeads.getPlugin();

		//load spawn cause modifiers
		String modifiers = FileIO.loadResource(plugin, "spawn-cause modifiers.txt");
		for(String line : modifiers.split("\n")){
			line = line.replace(" ", "").replace("\t", "").toUpperCase();
			int i = line.indexOf(":");
			if(i != -1){
				try{spawnModifiers.put(SpawnReason.valueOf(line.substring(0, i)), Float.parseFloat(line.substring(i+1)));}
				catch(IllegalArgumentException ex){
					plugin.getLogger().severe("Invalid SpawnReason: '"+line+"' in config file!");
				}
			}
		}
	}

	@EventHandler
	public void entitySpawnEvent(CreatureSpawnEvent evt){
		float modifier = spawnModifiers.get(evt.getSpawnReason());
		if(modifier != 1){
			evt.getEntity().setMetadata("SpawnReason", new FixedMetadataValue(plugin, modifier));
		}
	}
}
