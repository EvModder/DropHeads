package net.evmodder.DropHeads.listeners;

import java.util.HashMap;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.NotePlayEvent;
import com.mojang.authlib.GameProfile;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.JunkUtils;
import net.evmodder.EvLib.extras.HeadUtils;

public class NoteblockPlayListener implements Listener{
	private final HashMap<String, Sound> nbSounds;

	public NoteblockPlayListener(){
		nbSounds = JunkUtils.getNoteblockSounds();
	}

	/*public Sound getAmbientSound(String eType){
		switch(eType){
			case "ALLAY":
				return  Sound.valueOf("ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM");
			case "ARMOR_STAND":
				return Sound.ENTITY_ARMOR_STAND_PLACE;
			case "LEASH_HITCH":
				return Sound.ENTITY_LEASH_KNOT_PLACE;
			case "GIANT":
				return Sound.ENTITY_ZOMBIE_AMBIENT;
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
	}*/

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
			textureKey = DropHeads.getPlugin().getAPI().getTextureKey(profile);
			if(textureKey == null) return;
		}
		else textureKey = HeadUtils.getEntityFromHead(skull.getType()).name();
		Sound sound;
		int endIdx;
		while((sound=nbSounds.get(textureKey)) == null && (endIdx=textureKey.lastIndexOf('|')) != -1) textureKey = textureKey.substring(0, endIdx);
		if(sound == null){
			try{
				//sound = getAmbientSound(textureKey);
				sound = Sound.valueOf("ENTITY_"+textureKey+"_AMBIENT");
			}
			catch(IllegalArgumentException ex){
				DropHeads.getPlugin().getLogger().warning("Unable to find noteblock sound for entity: "+textureKey);
				if((sound=nbSounds.get("UNKNOWN")) == null) return;
			}
		}
		// Got a sound, now play it
		final float volume = (textureKey.equals("GIANT") && sound.name().equals("ENTITY_ZOMBIE_AMBIENT")) ? 3f : 1f;
		nb.getWorld().playSound(nb.getLocation(), sound, SoundCategory.RECORDS, volume, /*pitch=*/1f);
		// Cancel the sound that would have played
		evt.setCancelled(true);
	}
}