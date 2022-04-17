package net.evmodder.DropHeads.listeners;

import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.EvLib.extras.WebUtils;

public class GiveCommandPreprocessListener implements Listener{
	final private DropHeads pl;

	class CustomHeadItemStack extends ItemStack{
		CustomHeadItemStack(ItemStack item){
			super(item);
		}
	}

	public GiveCommandPreprocessListener(){
		pl = DropHeads.getPlugin();
		//registerCustomHeadItemStack();
		new NamespacedKey(pl, "vex_head");
	}

	@EventHandler
	public void onGiveCommandPreprocess(PlayerCommandPreprocessEvent evt){
		String cmd = evt.getMessage().toLowerCase();
		if(cmd.startsWith("/give ") || cmd.startsWith("/minecraft:give ")){
			UUID uuid = UUID.nameUUIDFromBytes("VEX".getBytes());// Stable UUID for this textureKey
			String newCmd = evt.getMessage().replaceAll("(?i) "+pl.getAPI().dropheadsNamespaceKey+"vex_head ", " minecraft:player_head{"
					//TODO: proper display name (translated bits) and lore (&8mob:<type>)
					+ "display:{Name:\"{\\\\\"text\\\\\":\\\\\"Vex Head\\\\\",\\\\\"italic\\\\\":false}\"},"
					+ "SkullOwner:{Id:"+WebUtils.convertUUIDToIntArray(uuid)+","
					//+ "SkullOwner:{Id:[I;-130297919,34818391,-2003905566,-85519869],"
					+ "Name:\"VEX\","
					+ "Properties:{textures:[{Value:\""+DropHeads.getPlugin().getAPI().getTextures().get("VEX")+"\"}]}}}"
					+ " "
			);
			if(!newCmd.equals(evt.getMessage())){
				evt.setMessage(newCmd);
			}
		}
	}
}