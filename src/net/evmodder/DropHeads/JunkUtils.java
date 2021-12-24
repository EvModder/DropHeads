package net.evmodder.DropHeads;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import javax.annotation.Nonnull;
import net.evmodder.DropHeads.listeners.EntityDeathListener.AnnounceMode;
import net.evmodder.EvLib.extras.NBTTagUtils;
import net.evmodder.EvLib.extras.NBTTagUtils.RefNBTTagString;
import net.evmodder.EvLib.extras.NBTTagUtils.RefNBTTagCompound;
import net.evmodder.EvLib.extras.NBTTagUtils.RefNBTTagList;
import net.evmodder.EvLib.extras.ReflectionUtils;
import net.evmodder.EvLib.extras.TellrawUtils;
import net.evmodder.EvLib.extras.TextUtils;
import net.evmodder.EvLib.extras.TypeUtils;
import net.evmodder.EvLib.extras.ReflectionUtils.RefClass;
import net.evmodder.EvLib.extras.ReflectionUtils.RefMethod;
import net.evmodder.EvLib.extras.TellrawUtils.Component;
import net.evmodder.EvLib.extras.TellrawUtils.Format;
import net.evmodder.EvLib.extras.TellrawUtils.FormatFlag;
import net.evmodder.EvLib.extras.TellrawUtils.HoverEvent;
import net.evmodder.EvLib.extras.TellrawUtils.ListComponent;
import net.evmodder.EvLib.extras.TellrawUtils.RawTextComponent;
import net.evmodder.EvLib.extras.TellrawUtils.TextHoverAction;

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
//		if(e == null) return 1D;
		if(e.hasMetadata("SpawnReason")) return e.getMetadata("SpawnReason").get(0).asDouble();
		for(String tag : e.getScoreboardTags()) if(tag.startsWith("SpawnReasonModifier:")) return Float.parseFloat(tag.substring(20));
		return 1D;
	}

	public final static ItemStack setDisplayName(@Nonnull ItemStack item, @Nonnull Component name){
		RefNBTTagCompound tag = NBTTagUtils.getTag(item);
		RefNBTTagCompound display = tag.hasKey("display") ? (RefNBTTagCompound)tag.get("display") : new RefNBTTagCompound();
		display.setString("Name", name.toString());
		tag.set("display", display);
		return NBTTagUtils.setTag(item, tag);
	}

	public final static String getDisplayName(@Nonnull ItemStack item){
		RefNBTTagCompound tag = NBTTagUtils.getTag(item);
		if(tag == null) return null;
		RefNBTTagCompound display = tag.hasKey("display") ? (RefNBTTagCompound)tag.get("display") : new RefNBTTagCompound();
		return display.hasKey("Name") ? display.getString("Name") : null;
	}

	public final static ItemStack setLore(@Nonnull ItemStack item, @Nonnull Component... lore){
		RefNBTTagCompound tag = NBTTagUtils.getTag(item);
		RefNBTTagCompound display = tag.hasKey("display") ? (RefNBTTagCompound)tag.get("display") : new RefNBTTagCompound();
		RefNBTTagList loreList = new RefNBTTagList();
		for(Component loreLine : lore){
			RefNBTTagString refString = new RefNBTTagString(loreLine.toString());
			loreList.add(refString);
		}
		display.set("Lore", loreList);
		tag.set("display", display);
		return NBTTagUtils.setTag(item, tag);
	}

	public final static ArrayList<String> getLore(@Nonnull ItemStack item){
		RefNBTTagCompound tag = NBTTagUtils.getTag(item);
		RefNBTTagCompound display = tag.hasKey("display") ? (RefNBTTagCompound)tag.get("display") : new RefNBTTagCompound();
		if(!display.hasKey("Lore")) return null;
		RefNBTTagList loreTag = (RefNBTTagList)display.get("Lore");
		RefNBTTagString loreLine = (RefNBTTagString)loreTag.get(0);
		ArrayList<String> loreCompStrs = new ArrayList<>();
		try{for(int i=0; loreLine!=null; loreLine=(RefNBTTagString)loreTag.get(++i)){
			String loreStr = loreLine.toString();
			if(loreStr.startsWith("\"'") && loreStr.endsWith("'\"")){
				if(loreStr.startsWith("\"'{") && loreStr.endsWith("}'\"")) loreStr = TextUtils.unescapeString(loreStr.substring(2, loreStr.length()-2));
				else loreStr = TextUtils.unescapeString(loreStr.substring(2, loreStr.length()-2));
			}
			loreCompStrs.add(loreStr);
		}}
		catch(RuntimeException e){/*caused by IndexOutOfBoundsException*/}
		return loreCompStrs;
	}

	// ItemStack methods to get a net.minecraft.server.ItemStack object for serialization
	final static RefClass craftItemStackClazz = ReflectionUtils.getRefClass("{cb}.inventory.CraftItemStack");
	final static RefMethod asNMSCopyMethod = craftItemStackClazz.getMethod("asNMSCopy", ItemStack.class);

	// NMS Method to serialize a net.minecraft.server.vX_X.ItemStack to a valid JSON string
	final static RefClass nmsItemStackClazz = ReflectionUtils.getRefClass("{nms}.ItemStack", "{nm}.world.item.ItemStack");
	final static RefClass nbtTagCompoundClazz = ReflectionUtils.getRefClass("{nms}.NBTTagCompound", "{nm}.nbt.NBTTagCompound");
	final static RefMethod saveNmsItemStackMethod = nmsItemStackClazz.findMethod(/*isStatic=*/false, nbtTagCompoundClazz, nbtTagCompoundClazz);

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

	public final static Component getItemDisplayNameComponent(@Nonnull ItemStack item){
		String rarityColor = TypeUtils.getRarityColor(item).name().toLowerCase();
		String rawDisplayName = JunkUtils.getDisplayName(item);
		if(rawDisplayName != null){
			FormatFlag[] formats = new FormatFlag[]{new FormatFlag(Format.ITALIC, true)};
			return new ListComponent(
					new RawTextComponent(/*text=*/"", /*insert=*/null, /*click=*/null, /*hover=*/null, /*color=*/rarityColor, /*formats=*/formats),
					TellrawUtils.parseComponentFromString(rawDisplayName)
			);
		}
		else{
			return new ListComponent(
					new RawTextComponent(/*text=*/"", /*insert=*/null, /*click=*/null, /*hover=*/null, /*color=*/rarityColor, /*formats=*/null),
					TellrawUtils.getLocalizedDisplayName(item));
		}
	}
	public final static Component getMurderItemComponent(@Nonnull ItemStack item, int JSON_LIMIT){
		TextHoverAction hoverAction = new TextHoverAction(HoverEvent.SHOW_ITEM, JunkUtils.convertItemStackToJson(item, JSON_LIMIT));
		String rarityColor = TypeUtils.getRarityColor(item).name().toLowerCase();
		String rawDisplayName = JunkUtils.getDisplayName(item);
		if(rawDisplayName != null){
			FormatFlag[] formats = new FormatFlag[]{new FormatFlag(Format.ITALIC, true)};
			return new ListComponent(
					new RawTextComponent(/*text=*/"[", /*insert=*/null, /*click=*/null, hoverAction, /*color=*/rarityColor, /*formats=*/null),
					new ListComponent(
							new RawTextComponent(/*text=*/"", /*insert=*/null, /*click=*/null, null, /*color=*/null, /*formats=*/formats),
							TellrawUtils.parseComponentFromString(rawDisplayName)
					),
					new RawTextComponent(/*text=*/"]"));
		}
		else{
			return new ListComponent(
					new RawTextComponent(/*text=*/"[", /*insert=*/null, /*click=*/null, hoverAction, /*color=*/rarityColor, /*formats=*/null),
					TellrawUtils.getLocalizedDisplayName(item),
					new RawTextComponent(/*text=*/"]"));
		}
	}

	// Similar as above, but for Entity instead of ItemStack
	final static RefClass craftEntityClazz = ReflectionUtils.getRefClass("{cb}.entity.CraftEntity");
	final static RefMethod entityGetHandleMethod = craftEntityClazz.getMethod("getHandle");
	final static RefClass nmsEntityClazz = ReflectionUtils.getRefClass("{nms}.Entity", "{nm}.world.entity.Entity");
	final static RefMethod saveNmsEntityMethod = nmsEntityClazz.findMethod(/*isStatic=*/false, nbtTagCompoundClazz, nbtTagCompoundClazz);
	public final static String convertEntityToJson(Entity entity){//TODO: not currently used
		
		Object nmsNbtTagCompoundObj = nbtTagCompoundClazz.getConstructor().create();
		Object nmsEntityObj = entityGetHandleMethod.of(entity).call();
		Object entityAsJsonObject = saveNmsEntityMethod.of(nmsEntityObj).call(nmsNbtTagCompoundObj);
		return entityAsJsonObject.toString();
	}

//	final static RefClass craftLivingEntityClazz = ReflectionUtils.getRefClass("{cb}.entity.CraftLivingEntity");
//	final static RefMethod livingEntityGetHandleMethod = craftLivingEntityClazz.getMethod("getHandle");
//	final static RefClass nmsEntityLivingClazz = ReflectionUtils.getRefClass("{nms}.EntityLiving");
//	final static RefClass nmsEnumItemSlotClazz = ReflectionUtils.getRefClass("{nms}.EnumItemSlot");
//	final static Object nmsEnumItemSlotHead = nmsEnumItemSlotClazz.getMethod("valueOf", String.class).call("HEAD");
//	final static RefMethod entityLivingGetEquipmentMethod = nmsEntityLivingClazz.getMethod("getEquipment", nmsEnumItemSlotClazz);
//	final static RefConstructor craftItemStackCnstr = craftItemStackClazz.getConstructor(nmsItemStackClazz);
//	public static Collection<ItemStack> getEquipmentGuaranteedToDrop(LivingEntity entity){//TODO: move to EntityUtils
//		ArrayList<ItemStack> itemsThatWillDrop = new ArrayList<>();
//		EntityEquipment equipment = entity.getEquipment();
//		if(equipment.getItemInMainHandDropChance() >= 1f) itemsThatWillDrop.add(equipment.getItemInMainHand());
//		if(equipment.getItemInOffHandDropChance() >= 1f) itemsThatWillDrop.add(equipment.getItemInOffHand());
//		if(equipment.getChestplateDropChance() >= 1f) itemsThatWillDrop.add(equipment.getChestplate());
//		if(equipment.getLeggingsDropChance() >= 1f) itemsThatWillDrop.add(equipment.getLeggings());
//		Bukkit.getLogger().info("helmet drop chance: "+equipment.getHelmetDropChance());
//		if(equipment.getHelmetDropChance() >= 1f) itemsThatWillDrop.add(
//				(ItemStack)craftItemStackCnstr
//				.create(entityLivingGetEquipmentMethod.of(
//						livingEntityGetHandleMethod
//						.of(entity).call())
//						.call(nmsEnumItemSlotHead))
//		);
//		if(equipment.getBootsDropChance() >= 1f) itemsThatWillDrop.add(equipment.getBoots());
//		return itemsThatWillDrop;
//	}

	public final static boolean setIfEmpty(@Nonnull EntityEquipment equipment, @Nonnull ItemStack item, @Nonnull EquipmentSlot slot){
		switch(slot){
			case HAND:
				if(equipment.getItemInMainHandDropChance() >= 1f && equipment.getItemInMainHand() != null) return false;
				equipment.setItemInMainHand(item);
				return true;
			case OFF_HAND:
				if(equipment.getItemInOffHandDropChance() >= 1f && equipment.getItemInOffHand() != null) return false;
				equipment.setItemInOffHand(item);
				return true;
			case HEAD:
				if(equipment.getHelmetDropChance() >= 1f && equipment.getHelmet() != null) return false;
				equipment.setHelmet(item);
				return true;
			case CHEST:
				if(equipment.getChestplateDropChance() >= 1f && equipment.getChestplate() != null) return false;
				equipment.setChestplate(item);
				return true;
			case LEGS:
				if(equipment.getLeggingsDropChance() >= 1f && equipment.getLeggings() != null) return false;
				equipment.setLeggings(item);
				return true;
			case FEET:
				if(equipment.getBootsDropChance() >= 1f && equipment.getBoots() != null) return false;
				equipment.setBoots(item);
				return true;
			default:
				return false;
		}
	}

	static RefMethod essMethodGetUser, essMethodIsVanished;
	static RefMethod vanishMethodGetManager, vanishMethodIsVanished;
	public final static boolean isVanished(Player p){
		Plugin essPlugin = p.getServer().getPluginManager().getPlugin("Essentials");
		if(essPlugin != null){
			if(essMethodGetUser == null){
				essMethodGetUser = ReflectionUtils.getRefClass("com.earth2me.essentials.Essentials").getMethod("getUser", Player.class);
				essMethodIsVanished = ReflectionUtils.getRefClass("com.earth2me.essentials.User").getMethod("isVanished");
			}
			return (boolean)essMethodIsVanished.of(essMethodGetUser.of(essPlugin).call(p)).call();
		}
		Plugin vanishPlugin = p.getServer().getPluginManager().getPlugin("VanishNoPacket");
		if(vanishPlugin != null){
			if(vanishMethodGetManager == null){
				vanishMethodGetManager = ReflectionUtils.getRefClass("org.kitteh.vanish.VanishPlugin").getMethod("getManager");
				vanishMethodIsVanished = ReflectionUtils.getRefClass("org.kitteh.vanish.VanishManager").getMethod("isVanished", Player.class);
			}
			return (boolean)vanishMethodIsVanished.of(vanishMethodGetManager.of(vanishPlugin).call()).call(p);
		}
		return p.getServer().getOnlinePlayers().stream().allMatch(p2 -> p2.isOp() || p2.getUniqueId().equals(p.getUniqueId()) || !p2.canSee(p));
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

	public final static String repeat(String str, int n){
		StringBuilder b = new StringBuilder();
		for(int i=0; i<n; ++i) b.append(str);
		return b.toString();
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
