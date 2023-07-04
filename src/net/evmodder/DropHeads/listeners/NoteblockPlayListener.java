package net.evmodder.DropHeads.listeners;

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
import net.evmodder.EvLib.extras.HeadUtils;

public class NoteblockPlayListener implements Listener{
	final private String dhNamespaceKey;
	public NoteblockPlayListener(){
		dhNamespaceKey = DropHeads.getPlugin().getAPI().getDropHeadsNamespacedKey();
	}

	@EventHandler(ignoreCancelled = true)
	public void onNoteblockPlay(NotePlayEvent evt){
		final Block nb = evt.getBlock();
		if(nb.getY() >= nb.getWorld().getMaxHeight() || nb.getRelative(BlockFace.UP).getType() != Material.PLAYER_HEAD) return;
		final Skull skull = (Skull) nb.getRelative(BlockFace.UP).getState();
		final GameProfile profile = HeadUtils.getGameProfile(skull);
		if(!profile.getName().startsWith(dhNamespaceKey)) return;
		int endIdx = profile.getName().indexOf('|');
		if(endIdx == -1) endIdx = profile.getName().indexOf('>');
		final String entityName = endIdx == -1 ? profile.getName() : profile.getName().substring(dhNamespaceKey.length(), endIdx);
		final String soundName = "ENTITY_"+entityName+"_AMBIENT";
		try{
			nb.getWorld().playSound(nb.getLocation(), Sound.valueOf(soundName), SoundCategory.RECORDS, /*volume=*/1f, /*pitch=*/1f);
		}
		catch(IllegalArgumentException e){
			DropHeads.getPlugin().getLogger().warning("Unable to find sound effect: "+soundName);
		}
	}
}