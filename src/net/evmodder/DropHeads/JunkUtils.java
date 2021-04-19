package net.evmodder.DropHeads;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import javax.annotation.Nonnull;
import net.evmodder.DropHeads.listeners.EntityDeathListener.AnnounceMode;
import net.evmodder.EvLib.extras.NBTTagUtils;
import net.evmodder.EvLib.extras.NBTTagUtils.RefNBTTag;
import net.evmodder.EvLib.extras.ReflectionUtils;
import net.evmodder.EvLib.extras.TextUtils;
import net.evmodder.EvLib.extras.ReflectionUtils.RefClass;
import net.evmodder.EvLib.extras.ReflectionUtils.RefMethod;
import net.evmodder.EvLib.extras.TellrawUtils.Component;

// A trashy place to dump stuff that I should probably move to EvLib after ensure cross-version safety
public class JunkUtils{
	public final static String[] parseStringOrStringList(FileConfiguration config, String key, String defaultMsg){
		List<String> strList = null;
		return config.isList(key) && (strList=config.getStringList(key)) != null && !strList.isEmpty()
				? strList.stream().map(
					msg -> TextUtils.translateAlternateColorCodes('&', msg)).toArray(size -> new String[size])
				: new String[]{TextUtils.translateAlternateColorCodes('&', config.getString(key, defaultMsg))};
	}
	public final static AnnounceMode parseAnnounceMode(@Nonnull String value, AnnounceMode defaultMode){
		value = value.toUpperCase();
		if(value.equals("FALSE")) return AnnounceMode.OFF;
		if(value.equals("TRUE")) return AnnounceMode.GLOBAL;
		try{return AnnounceMode.valueOf(value);}
		catch(IllegalArgumentException ex){
			DropHeads.getPlugin().getLogger().severe("Unknown announcement mode: '"+value+"'");
			DropHeads.getPlugin().getLogger().warning("Please use one of the available modes: [GLOBAL, LOCAL, OFF]");
			return defaultMode;
		}
	}
	public final static <E extends Enum<E>> E parseEnumOrDefault(@Nonnull String stringValue, E defaultValue){
		Class<E> enumClass = defaultValue.getDeclaringClass();
		stringValue = stringValue.toUpperCase();
		try{return Enum.valueOf(enumClass, stringValue);}
		catch(IllegalArgumentException ex){
			DropHeads.getPlugin().getLogger().severe("Unknown "+enumClass.getSimpleName()+": '"+stringValue+"'");
			DropHeads.getPlugin().getLogger().warning("Please use one of: "+String.join(", ",
					Arrays.asList(enumClass.getEnumConstants()).stream().map(v -> v.name()).collect(Collectors.toList())));
			return defaultValue;
		}
	}
	public final static long timeSinceLastPlayerDamage(Entity entity){
		long lastDamage = entity.hasMetadata("PlayerDamage") ? entity.getMetadata("PlayerDamage").get(0).asLong() : 0;
		return System.currentTimeMillis() - lastDamage;
	}
	public final static double getSpawnCauseModifier(Entity e){
		//return e.hasMetadata("SpawnReason") ? e.getMetadata("SpawnReason").get(0).asDouble() : 1D;
		if(e.hasMetadata("SpawnReason")) return e.getMetadata("SpawnReason").get(0).asDouble();
		for(String tag : e.getScoreboardTags()) if(tag.startsWith("SpawnReasonModifier:")) return Float.parseFloat(tag.substring(20));
		return 1D;
	}

	public final static ItemStack setDisplayName(@Nonnull ItemStack item, @Nonnull Component name){
		RefNBTTag tag = NBTTagUtils.getTag(item);
		RefNBTTag display = tag.hasKey("display") ? (RefNBTTag)tag.get("display") : new RefNBTTag();
		display.setString("Name", name.toString());
		tag.set("display", display);
		return NBTTagUtils.setTag(item, tag);
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
	public final static String convertItemStackToJson(ItemStack item, int JSON_LIMIT){
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
	public final static String convertEntityToJson(Entity entity){//TODO: not currently used
		Object nmsNbtTagCompoundObj = nbtTagCompoundClazz.getConstructor().create();
		Object nmsEntityObj = getHandleMethod.of(entity).call();
		Object entityAsJsonObject = saveNmsEntityMethod.of(nmsEntityObj).call(nmsNbtTagCompoundObj);
		return entityAsJsonObject.toString();
	}

	public final static ItemStack giveItemToEntity(Entity entity, ItemStack item){
		if(entity instanceof InventoryHolder){
			Collection<ItemStack> leftovers = ((InventoryHolder)entity).getInventory().addItem(item).values();
			return leftovers.isEmpty() ? null : leftovers.iterator().next();
		}
		if(entity instanceof LivingEntity){
			EntityEquipment equipment = ((LivingEntity)entity).getEquipment();
			if(equipment.getItemInMainHandDropChance() == 0F) equipment.setItemInMainHand(item);
			else if(equipment.getItemInOffHandDropChance() == 0F) equipment.setItemInOffHand(item);
			else return item;
			return null;
		}
		return item;
	}

	public final static BlockFace getClosestBlockFace(Vector vec, BlockFace... blockFaces){
		if(!vec.isNormalized()) vec = vec.normalize();
		BlockFace nearestFace = blockFaces[0];
		double minAngle = Double.MAX_VALUE;
		for(BlockFace face : blockFaces){
			double angle = Math.acos(face.getDirection().dot(vec));
			if(angle < minAngle){minAngle = angle; nearestFace = face;}
		}
		return nearestFace;
	}

	public interface TestFunc{boolean test(int num);}
	public final static int binarySearch(TestFunc f, int a, int b){
		while(a < b-1){
			int x = (b + a)/2;
			if(f.test(x)) a = x;
			else b = x;
		}
		return a;
	}
	public final static int exponentialSearch(TestFunc f, int a){
		a = f.test(a) ? 2*a : 1;
		while(f.test(a)) a *= 2;
		return binarySearch(f, a/2, a);
	}
}
