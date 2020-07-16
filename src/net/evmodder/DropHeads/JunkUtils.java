package net.evmodder.DropHeads;

import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import net.evmodder.EvLib.extras.HeadUtils;
import net.evmodder.EvLib.extras.ReflectionUtils;
import net.evmodder.EvLib.extras.ReflectionUtils.RefClass;
import net.evmodder.EvLib.extras.ReflectionUtils.RefMethod;

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


	// ItemStack methods to get a net.minecraft.server.ItemStack object for serialization
	final static RefClass craftItemStackClazz = ReflectionUtils.getRefClass("{cb}.inventory.CraftItemStack");
	final static RefMethod asNMSCopyMethod = craftItemStackClazz.getMethod("asNMSCopy", ItemStack.class);

	// NMS Method to serialize a net.minecraft.server.vX_X.ItemStack to a valid JSON string
	final static RefClass nmsItemStackClazz = ReflectionUtils.getRefClass("{nms}.ItemStack");
	final static RefClass nbtTagCompoundClazz = ReflectionUtils.getRefClass("{nms}.NBTTagCompound");
	final static RefMethod saveNmsItemStackMethod = nmsItemStackClazz.getMethod("save", nbtTagCompoundClazz);

	// https://www.spigotmc.org/threads/tut-item-tooltips-with-the-chatcomponent-api.65964/
	/**
	 * Converts an {@link org.bukkit.inventory.ItemStack} to a JSON string
	 * for sending with TextAction.ITEM
	 *
	 * @param itemStack the item to convert
	 * @return the JSON string representation of the item
	 */
	public static String convertItemStackToJson(ItemStack item, int JSON_LIMIT){
		Object nmsNbtTagCompoundObj = nbtTagCompoundClazz.getConstructor().create();
		Object nmsItemStackObj = asNMSCopyMethod.of(null).call(item);
		Object itemAsJsonObject = saveNmsItemStackMethod.of(nmsItemStackObj).call(nmsNbtTagCompoundObj);
		String jsonString = itemAsJsonObject.toString();
		if(jsonString.length() > JSON_LIMIT){
			item = new ItemStack(item.getType(), item.getAmount());//TODO: Reduce item json data in a less destructive way-
			//reduceItemData() -> clear book pages, clear hidden NBT, call recursively for containers
			nmsNbtTagCompoundObj = nbtTagCompoundClazz.getConstructor().create();
			nmsItemStackObj = asNMSCopyMethod.of(null).call(item);
			itemAsJsonObject = saveNmsItemStackMethod.of(nmsItemStackObj).call(nmsNbtTagCompoundObj);
			jsonString = itemAsJsonObject.toString();
		}
		return itemAsJsonObject.toString();
	}

	// Similar as above, but for Entity instead of ItemStack
	final static RefClass craftEntityClazz = ReflectionUtils.getRefClass("{cb}.entity.CraftEntity");
	final static RefMethod getHandleMethod = craftEntityClazz.getMethod("getHandle");
	final static RefClass nmsEntityClazz = ReflectionUtils.getRefClass("{nms}.Entity");
	final static RefMethod saveNmsEntityMethod = nmsEntityClazz.getMethod("save", nbtTagCompoundClazz);
	public static String convertEntityToJson(Entity entity){//TODO: not currently used
		Object nmsNbtTagCompoundObj = nbtTagCompoundClazz.getConstructor().create();
		Object nmsEntityObj = getHandleMethod.of(entity).call();
		Object entityAsJsonObject = saveNmsEntityMethod.of(nmsEntityObj).call(nmsNbtTagCompoundObj);
		return entityAsJsonObject.toString();
	}

	//TODO: move to EntityUtils?
	public final static EntityType getEntityByName(String name){
		//TODO: improve this function / test for errors
		if(name.toUpperCase().startsWith("MHF_")) name = HeadUtils.normalizedNameFromMHFName(name);
		name = name.toUpperCase().replace(' ', '_');
		String noUnderscoresName = name.replace("_", "");
		if(noUnderscoresName.equals("ZOMBIEPIGMAN")) name = "PIG_ZOMBIE";
		else if(noUnderscoresName.equals("MOOSHROOM")) name = "MUSHROOM_COW";

		try{EntityType type = EntityType.valueOf(name); return type;}
		catch(IllegalArgumentException ex){}
		for(EntityType t : EntityType.values()) if(t.name().replace("_", "").equals(noUnderscoresName)) return t;
		return EntityType.UNKNOWN;
	}

	public interface TestFunc{boolean test(int num);}
	public final static int binarySearch(TestFunc f, int a, int b){
		while(a < b-1){
			int x = (b + a)/2;
			if(f.test(x)) a = x;
			else b = x + 1;
		}
		return a;
	}
	public final static int exponentialSearch(TestFunc f, int a){
		a = f.test(a) ? 2*a : 1;
		while(f.test(a)) a *= 2;
		return binarySearch(f, a/2, a);
	}
}
