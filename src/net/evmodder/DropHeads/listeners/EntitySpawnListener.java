package net.evmodder.DropHeads.listeners;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.metadata.FixedMetadataValue;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.JunkUtils;
import net.evmodder.EvLib.FileIO;

public class EntitySpawnListener implements Listener{
	private final DropHeads pl;
	private final Map<SpawnReason, Float> spawnModifiers = new HashMap<SpawnReason, Float>();
	private final boolean WARN_FOR_UNKNOWN_SPAWN_REASON = false;
	
	public EntitySpawnListener(){
		pl = DropHeads.getPlugin();

		//load spawn cause modifiers
		final InputStream defaultModifiers = pl.getClass().getResourceAsStream("/spawn-cause-modifiers.txt");
		final String modifiers = FileIO.loadFile("spawn-cause-modifiers.txt", defaultModifiers);
		for(String line : modifiers.split("\n")){
			line = line.replace(" ", "").replace("\t", "").toUpperCase();
			final int i = line.indexOf(":");
			if(i != -1){
				try{
					SpawnReason reason = SpawnReason.valueOf(line.substring(0, i));
					Float modifier = Float.parseFloat(line.substring(i+1));
					if(Math.abs(1F - modifier) > 0.0001) spawnModifiers.put(reason, modifier);
				}
				catch(IllegalArgumentException ex){
					if(WARN_FOR_UNKNOWN_SPAWN_REASON) pl.getLogger().warning("Unknown SpawnReason: '"+line+"' in config file!");
				}
			}
		}
		if(spawnModifiers.isEmpty()){
			HandlerList.unregisterAll(this);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void entitySpawnEvent(CreatureSpawnEvent evt){
		if(evt.getSpawnReason() != null){
			final Float modifier = spawnModifiers.get(evt.getSpawnReason());
			if(modifier != null){
				evt.getEntity().setMetadata(JunkUtils.SPAWN_CAUSE_MULTIPLIER_KEY, new FixedMetadataValue(pl, modifier));
				evt.getEntity().addScoreboardTag(JunkUtils.SPAWN_CAUSE_MULTIPLIER_KEY+'_'+modifier);
			}
		}
	}
}