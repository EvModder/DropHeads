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
	private final Map<SpawnReason, Float> spawnMultipliers;
	private final boolean WARN_FOR_UNKNOWN_SPAWN_REASON = false;
	
	public EntitySpawnListener(){
		pl = DropHeads.getPlugin();
		spawnMultipliers = new HashMap<>();

		final InputStream defaultSpawnMults = pl.getClass().getResourceAsStream("/spawn-cause-multipliers.txt");
		FileIO.moveFile("spawn-cause-modifiers.txt", "spawn-cause-multipliers.txt");//TODO: delete, someday
		final String multipliers = FileIO.loadFile("spawn-cause-multipliers.txt", defaultSpawnMults);
		for(String line : multipliers.split("\n")){
			line = line.replace(" ", "").replace("\t", "").toUpperCase();
			final int i = line.indexOf(":");
			if(i != -1){
				try{
					final SpawnReason reason = SpawnReason.valueOf(line.substring(0, i));
					final Float mult = Float.parseFloat(line.substring(i+1));
					if(Math.abs(1F - mult) > 0.0001) spawnMultipliers.put(reason, mult);
				}
				catch(IllegalArgumentException ex){
					if(WARN_FOR_UNKNOWN_SPAWN_REASON) pl.getLogger().warning("Unknown SpawnReason: '"+line+"' in config file!");
				}
			}
		}
		if(spawnMultipliers.isEmpty()) HandlerList.unregisterAll(this);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void creatureSpawnEvent(CreatureSpawnEvent evt){
		if(evt.getSpawnReason() == null) return;

		final Float mult = spawnMultipliers.get(evt.getSpawnReason());
		if(mult == null) return;

		evt.getEntity().setMetadata(JunkUtils.SPAWN_CAUSE_MULTIPLIER_KEY, new FixedMetadataValue(pl, mult));
		evt.getEntity().addScoreboardTag(JunkUtils.SPAWN_CAUSE_MULTIPLIER_KEY+'_'+mult);
	}
}
