package net.evmodder.DropHeads;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;

// A trashy place to dump stuff that I should probably move to EvLib after ensure cross-version safety
public class JunkUtils{
	public static ChatColor getRarityColor(ItemStack item, boolean checkCustomName){
		if(checkCustomName && item.hasItemMeta() && item.getItemMeta().hasDisplayName()){
			String displayName = item.getItemMeta().getDisplayName();
			ChatColor color = null;
			for(int i=0; i+1 < displayName.length() && displayName.charAt(i) == ChatColor.COLOR_CHAR; i+=2)
				color = ChatColor.getByChar(displayName.charAt(i+1));//TODO: currently matches formats as well
			if(color != null) return color;
		}
		switch(item.getType().name()){
			// EPIC:
			case "DRAGON_EGG":
			case "ENCHANTED_GOLDEN_APPLE":
			case "MOJANG_BANNER_PATTERN":
			case "COMMAND_BLOCK": case "CHAIN_COMMAND_BLOCK": case "REPEATING_COMMAND_BLOCK":
			case "JIGSAW": case "STRUCTURE_BLOCK":
				return ChatColor.LIGHT_PURPLE;
			// RARE:
			case "BEACON":
			case "CONDUIT":
			case "END_CRYSTAL":
			case "GOLDEN_APPLE":
			case "MUSIC_DISC_11": case "MUSIC_DISC_13": case "MUSIC_DISC_BLOCKS": case "MUSIC_DISC_CAT":
			case "MUSIC_DISC_CHIRP": case "MUSIC_DISC_FAR": case "MUSIC_DISC_MALL": case "MUSIC_DISC_MELLOHI":
			case "MUSIC_DISC_STAL": case "MUSIC_DISC_WAIT": case "MUSIC_DISC_WARD":
				return ChatColor.AQUA;
			// UNCOMMON:
			case "CREEPER_BANNER_PATTERN":
			case "SKULL_BANNER_PATTERN":
			case "EXPERIENCE_BOTTLE":
			case "DRAGON_BREATH":
			case "ELYTRA":
			case "ENCHANTED_BOOK":
			case "PLAYER_HEAD": case "CREEPER_HEAD": case "ZOMBIE_HEAD": case "DRAGON_HEAD":
			case "SKELETON_SKULL": case "WITHER_SKELETON_SKULL":
			case "HEART_OF_THE_SEA":
			case "NETHER_STAR":
			case "TOTEM_OF_UNDYING":
				return item.hasItemMeta() && item.getItemMeta().hasEnchants() ? ChatColor.AQUA : ChatColor.YELLOW;
			// COMMON:
			default:
				return item.hasItemMeta() && item.getItemMeta().hasEnchants() ? ChatColor.AQUA : ChatColor.WHITE;
		}
	}
}
