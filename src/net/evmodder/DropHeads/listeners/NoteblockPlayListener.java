package net.evmodder.DropHeads.listeners;

import java.util.HashMap;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.NotePlayEvent;
import com.mojang.authlib.GameProfile;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.JunkUtils;
import net.evmodder.EvLib.FileIO;
import net.evmodder.EvLib.extras.HeadUtils;
import net.evmodder.EvLib.extras.WebUtils;

public class NoteblockPlayListener implements Listener{
	private final int DH_MOB_PROFILE_PREFIX_LENGTH;
	private final HashMap<String, Sound> nbSounds;

	public NoteblockPlayListener(){
		final DropHeads pl = DropHeads.getPlugin();
		DH_MOB_PROFILE_PREFIX_LENGTH = JunkUtils.TXTR_KEY_PREFIX.length();
		nbSounds = new HashMap</*txtrKey, Sound*/>();

		String sounds = FileIO.loadFile("noteblock-sounds.txt", (String)null);
		if(sounds == null){
			pl.getLogger().info("Downloading noteblock sound config...");
			sounds = WebUtils.getReadURL("https://raw.githubusercontent.com/EvModder/DropHeads/master/noteblock-sounds.txt");
			sounds = FileIO.loadFile("noteblock-sounds.txt", sounds);
			if(sounds == null){pl.getLogger().severe("Request to download noteblock-sounds.txt failed"); return;}
		}
		for(String line : sounds.split("\n")){
			line = line.replace(" ", "").replace("\t", "").toUpperCase();
			final int i = line.indexOf(":");
			if(i == -1) continue;
			final String key = line.substring(0, i);
			if(!pl.getAPI().textureExists(key)){pl.getLogger().warning("Unknown textureKey in noteblock-sounds.txt: "+key); continue;}
			final String sound = line.substring(i+1);
			try{nbSounds.put(key, Sound.valueOf(sound));}
			catch(IllegalArgumentException e1){
				final int endIdx = key.indexOf('|');
				try{
					final EntityType eType = EntityType.valueOf(endIdx == -1 ? key : key.substring(endIdx));
					pl.getLogger().warning("Unknown sound for "+eType+" in noteblock-sounds.txt: "+sound);
				}
				// Don't give warning for unknown sound if EntityType is also unknown (likely future version)
				catch(IllegalArgumentException e2){}
				continue;
			}
		}
	}

	public Sound getAmbientSound(String eType){
		switch(eType){
			case "ALLAY":
				return  Sound.valueOf("ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM"); //TODO: with item?
			case "ARMOR_STAND":
				return Sound.ENTITY_ARMOR_STAND_PLACE;
			case "LEASH_HITCH":
				return Sound.ENTITY_LEASH_KNOT_PLACE;
			case "GIANT":
				return Sound.ENTITY_ZOMBIE_AMBIENT; //TODO: play at 2x volume?
			case "COD":
				return Sound.ENTITY_COD_FLOP;
			case "SALMON":
				return Sound.ENTITY_SALMON_FLOP;
			case "PUFFERFISH":
				return Sound.ENTITY_PUFFER_FISH_FLOP;
			case "TROPICAL_FISH":
				return Sound.ENTITY_TROPICAL_FISH_FLOP;
			case "MINECART":
			case "MINECART_CHEST":
			case "MINECART_COMMAND":
			case "MINECART_FURNACE":
			case "MINECART_HOPPER":
			case "MINECART_MOB_SPAWNER":
			case "MINECART_TNT":
				return Sound.ENTITY_MINECART_RIDING;
			case "SNIFFER":
				return Sound.valueOf("ENTITY_SNIFFER_IDLE");
			case "TADPOLE":
				return Sound.valueOf("ENTITY_TADPOLE_FLOP");
			case "TURTLE":
				return Sound.ENTITY_TURTLE_AMBIENT_LAND;
			case "CAVE_SPIDER":
				return Sound.ENTITY_SPIDER_AMBIENT;
			case "CREEPER":
				return Sound.ENTITY_CREEPER_PRIMED;
			case "SLIME":
				return Sound.ENTITY_SLIME_HURT;
			case "SNOWMAN":
				return Sound.ENTITY_SNOW_GOLEM_AMBIENT;
			case "MAGMA_CUBE":
				return Sound.ENTITY_MAGMA_CUBE_HURT;
			case "MUSHROOM_COW":
				return Sound.ENTITY_COW_AMBIENT;
			case "AXOLOTL":
				return Sound.valueOf("ENTITY_AXOLOTL_IDLE_AIR"); //TODO: ENTITY_AXOLOTL_IDLE_WATER?
			case "TRADER_LLAMA":
				return Sound.ENTITY_LLAMA_AMBIENT;
			case "IRON_GOLEM":
				return Sound.ENTITY_IRON_GOLEM_HURT;
			case "BEE":
				return Sound.valueOf("ENTITY_BEE_LOOP");
			case "BEE|ANGRY":
				return Sound.valueOf("ENTITY_BEE_LOOP_AGGRESSIVE");
			case "BOAT":
			case "CHEST_BOAT":
				return Sound.ENTITY_BOAT_PADDLE_WATER;
			case "UNKNOWN":
				return Sound.AMBIENT_CAVE;
			default:
				return Sound.valueOf("ENTITY_"+eType+"_AMBIENT");
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onNoteblockPlay(NotePlayEvent evt){
		final Block nb = evt.getBlock();
//		if(nb.getY() >= nb.getWorld().getMaxHeight() || nb.getRelative(BlockFace.UP).getType() != Material.PLAYER_HEAD) return;
		if(nb.getY() >= nb.getWorld().getMaxHeight() || !HeadUtils.isHead(nb.getRelative(BlockFace.UP).getType())) return;
		final Skull skull = (Skull) nb.getRelative(BlockFace.UP).getState();
		if(skull.getType().name().endsWith("_WALL_HEAD")) return;

		String textureKey;
		if(skull.getType() == Material.PLAYER_HEAD){
			final GameProfile profile = HeadUtils.getGameProfile(skull);
			if(!profile.getName().startsWith(JunkUtils.TXTR_KEY_PREFIX)) return;
			final int endIdx = profile.getName().indexOf('>');
			textureKey = profile.getName().substring(DH_MOB_PROFILE_PREFIX_LENGTH, endIdx != -1 ? endIdx : profile.getName().length());
		}
		else textureKey = HeadUtils.getEntityFromHead(skull.getType()).name();
		Sound sound;
		int endIdx;
		while((sound=nbSounds.get(textureKey)) == null && (endIdx=textureKey.lastIndexOf('|')) != -1) textureKey = textureKey.substring(0, endIdx);
		if(sound == null){
			try{sound = Sound.valueOf("ENTITY_"+textureKey+"_AMBIENT");}
			catch(IllegalArgumentException ex){
				DropHeads.getPlugin().getLogger().warning("Unable to find noteblock sound for entity: "+textureKey);
				if((sound=nbSounds.get("UNKNOWN")) == null) return;
			}
		}
		// Got a sound, now play it
		nb.getWorld().playSound(nb.getLocation(), sound, SoundCategory.RECORDS, /*volume=*/1f, /*pitch=*/1f);
		evt.setCancelled(true);
	}
}