package evmodder.DropHeads;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.metadata.FixedMetadataValue;
import EvLib2.FileIO;

public class EntitySpawnListener implements Listener{
	private Map<SpawnReason, Float> spawnModifiers = new HashMap<SpawnReason, Float>();
	private DropHeads plugin;
	
	public EntitySpawnListener(){
		plugin = DropHeads.getPlugin();

		//load spawn cause modifiers
		InputStream defaultModifiers = plugin.getClass().getResourceAsStream("/spawn-cause modifiers.txt");
		String modifiers = FileIO.loadFile("spawn-cause modifiers.txt", defaultModifiers);
		for(String line : modifiers.split("\n")){
			line = line.replace(" ", "").replace("\t", "").toUpperCase();
			int i = line.indexOf(":");
			if(i != -1){
				try{spawnModifiers.put(
						SpawnReason.valueOf(line.substring(0, i)),
						Float.parseFloat(line.substring(i+1)));}
				catch(IllegalArgumentException ex){
					plugin.getLogger().severe("Invalid SpawnReason: '"+line+"' in config file!");
				}
			}
		}
	}

	@EventHandler
	public void entitySpawnEvent(CreatureSpawnEvent evt){
		if(evt.getSpawnReason() != null){
			Float modifier = spawnModifiers.get(evt.getSpawnReason());
			if(modifier != null && modifier != 1){
				evt.getEntity().setMetadata("SpawnReason", new FixedMetadataValue(plugin, modifier));
			}
		}
	}
}
