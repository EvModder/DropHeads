package net.evmodder.DropHeads;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import javax.annotation.Nonnull;
import net.evmodder.EvLib.FileIO;
import net.evmodder.EvLib.extras.NBTTagUtils;
import net.evmodder.EvLib.extras.NBTTagUtils.RefNBTTagString;
import net.evmodder.EvLib.extras.NBTTagUtils.RefNBTTagCompound;
import net.evmodder.EvLib.extras.NBTTagUtils.RefNBTTagList;
import net.evmodder.EvLib.extras.ReflectionUtils;
import net.evmodder.EvLib.extras.TellrawUtils;
import net.evmodder.EvLib.extras.TextUtils;
import net.evmodder.EvLib.extras.WebUtils;
import net.evmodder.EvLib.extras.ReflectionUtils.RefClass;
import net.evmodder.EvLib.extras.ReflectionUtils.RefField;
import net.evmodder.EvLib.extras.ReflectionUtils.RefMethod;
import net.evmodder.EvLib.extras.TellrawUtils.ClickEvent;
import net.evmodder.EvLib.extras.TellrawUtils.Component;
import net.evmodder.EvLib.extras.TellrawUtils.Format;
import net.evmodder.EvLib.extras.TellrawUtils.HoverEvent;
import net.evmodder.EvLib.extras.TellrawUtils.ListComponent;
import net.evmodder.EvLib.extras.TellrawUtils.RawTextComponent;
import net.evmodder.EvLib.extras.TellrawUtils.SelectorComponent;
import net.evmodder.EvLib.extras.TellrawUtils.TextClickAction;
import net.evmodder.EvLib.extras.TellrawUtils.TextHoverAction;

// A trashy place to dump stuff that I should probably move to EvLib after ensure cross-version safety
public final class JunkUtils{
	public final static String SPAWN_CAUSE_MULTIPLIER_KEY = "SRM";
	public final static String TXTR_KEY_PREFIX = "dropheads:";

	enum NoteblockMode{OFF, FALSE, LISTENER, ITEM_META};

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

	public final static HashMap<String, Sound> getNoteblockSounds(){
		HashMap<String, Sound> nbSounds = new HashMap</*txtrKey, Sound*/>();

		String sounds = FileIO.loadFile("noteblock-sounds.txt", (String)null);
		if(sounds == null){
			DropHeads.getPlugin().getLogger().info("Downloading noteblock sound config...");
			sounds = WebUtils.getReadURL("https://raw.githubusercontent.com/EvModder/DropHeads/master/noteblock-sounds.txt");
			sounds = FileIO.loadFile("noteblock-sounds.txt", sounds);
			if(sounds == null){DropHeads.getPlugin().getLogger().severe("Request to download noteblock-sounds.txt failed"); return null;}
		}
		for(String line : sounds.split("\n")){
			line = line.replace(" ", "").replace("\t", "").toUpperCase();
			final int i = line.indexOf(":");
			if(i == -1) continue;
			final String key = line.substring(0, i);
			final String sound = line.substring(i+1);
			try{nbSounds.put(key, Sound.valueOf(sound));}
			catch(IllegalArgumentException e1){
				final int endIdx = key.indexOf('|');
				try{
					final EntityType eType = EntityType.valueOf(endIdx == -1 ? key : key.substring(endIdx));
					DropHeads.getPlugin().getLogger().warning("Unknown sound for "+eType+" in noteblock-sounds.txt: "+sound);
				}
				// Don't give warning for unknown sound if EntityType is also unknown (likely future version)
				catch(IllegalArgumentException e2){}
				continue;
			}
		}
		return nbSounds;
	}

	public final static long timeSinceLastPlayerDamage(Entity entity){
		long lastDamage = entity.hasMetadata("PlayerDamage") ? entity.getMetadata("PlayerDamage").get(0).asLong() : 0;
		return System.currentTimeMillis() - lastDamage;
	}

	public final static double getSpawnCauseMult(Entity e){
		if(e.hasMetadata(SPAWN_CAUSE_MULTIPLIER_KEY)) return e.getMetadata(SPAWN_CAUSE_MULTIPLIER_KEY).get(0).asDouble();
		for(String tag : e.getScoreboardTags()) if(tag.startsWith(SPAWN_CAUSE_MULTIPLIER_KEY)) return Float.parseFloat(
				tag.substring(SPAWN_CAUSE_MULTIPLIER_KEY.length()+1/*+1 because of '_'*/));
		return 1D;
	}

	private final static RefField displayNameField = ReflectionUtils.getRefClass("{cb}.inventory.CraftMetaItem").getField("displayName");
	private final static RefField loreField = ReflectionUtils.getRefClass("{cb}.inventory.CraftMetaItem").getField("lore");
	private final static RefMethod fromJsonMethod, toJsonMethod;
	private final static Object registryAccessObj;//class: IRegistryCustom.Dimension
	static{
		if(ReflectionUtils.getServerVersionString().compareTo("v1_20_5") >= 0){
			final RefClass iChatBaseComponentClass = ReflectionUtils.getRefClass("{nm}.network.chat.IChatBaseComponent");
			final RefClass chatSerializerClass = ReflectionUtils.getRefClass("{nm}.network.chat.IChatBaseComponent$ChatSerializer");
			final RefClass holderLookupProviderClass = ReflectionUtils.getRefClass("{nm}.core.HolderLookup$Provider");
			fromJsonMethod = chatSerializerClass.getMethod("fromJson", String.class, holderLookupProviderClass);
			toJsonMethod = chatSerializerClass.getMethod("toJson", iChatBaseComponentClass, holderLookupProviderClass);

			final Object nmsServerObj = ReflectionUtils.getRefClass("{cb}.CraftServer").getMethod("getServer").of(Bukkit.getServer()).call();
			registryAccessObj = ReflectionUtils.getRefClass("{nm}.server.MinecraftServer").getMethod("registryAccess").of(nmsServerObj).call();
		}
		else registryAccessObj = fromJsonMethod = toJsonMethod = null;
	}
	public final static ItemStack setDisplayName(@Nonnull ItemStack item, @Nonnull Component name){
		if(fromJsonMethod != null){
			final ItemMeta meta = item.getItemMeta();
			displayNameField.of(meta).set(fromJsonMethod.call(name.toString(), registryAccessObj));
			item.setItemMeta(meta);
			return item;
		}
		else{
			RefNBTTagCompound tag = NBTTagUtils.getTag(item);
			RefNBTTagCompound display = tag.hasKey("display") ? (RefNBTTagCompound)tag.get("display") : new RefNBTTagCompound();
			display.setString("Name", name.toString());
			tag.set("display", display);
			return NBTTagUtils.setTag(item, tag);
		}
	}
	public final static String getDisplayName(@Nonnull ItemStack item){
		if(toJsonMethod != null){
			if(!item.hasItemMeta()) return null;
			return (String)toJsonMethod.call(displayNameField.of(item.getItemMeta()).get(), registryAccessObj);
		}
		else{
			RefNBTTagCompound tag = NBTTagUtils.getTag(item);
			RefNBTTagCompound display = tag.hasKey("display") ? (RefNBTTagCompound)tag.get("display") : new RefNBTTagCompound();
			return display.hasKey("Name") ? display.getString("Name") : null;
		}
	}

	public final static ItemStack setLore(@Nonnull ItemStack item, @Nonnull Component... lore){
		if(fromJsonMethod != null){
			Object lines = Stream.of(lore).map(line -> fromJsonMethod.call(line.toString(), registryAccessObj)).collect(Collectors.toList());
			ItemMeta meta = item.getItemMeta();
			loreField.of(meta).set(lines);
			item.setItemMeta(meta);
			return item;
		}
		else{
			RefNBTTagCompound tag = NBTTagUtils.getTag(item);
			RefNBTTagCompound display = tag.hasKey("display") ? (RefNBTTagCompound)tag.get("display") : new RefNBTTagCompound();
			RefNBTTagList loreList = new RefNBTTagList();
			for(Component loreLine : lore) loreList.add(new RefNBTTagString(loreLine.toString()));
			display.set("Lore", loreList);
			tag.set("display", display);
			return NBTTagUtils.setTag(item, tag);
		}
	}

	public final static List<String> getLore(@Nonnull ItemStack item){
		if(toJsonMethod != null){
			if(!item.hasItemMeta()) return null;
			//TODO: do we need to unescape anything?
			return ((List<?>)loreField.of(item.getItemMeta()).get()).stream().map(l -> (String)toJsonMethod.call(l, registryAccessObj))
					.collect(Collectors.toList());
		}
		else{
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
	}

	// ItemStack methods to get a net.minecraft.server.ItemStack object for serialization
	private final static RefClass craftItemStackClazz = ReflectionUtils.getRefClass("{cb}.inventory.CraftItemStack");
	private final static RefMethod asNMSCopyMethod = craftItemStackClazz.getMethod("asNMSCopy", ItemStack.class);

	// NMS Method to serialize a net.minecraft.server.vX_X.ItemStack to a valid JSON string
	private final static RefClass nmsItemStackClazz = ReflectionUtils.getRefClass("{nms}.ItemStack", "{nm}.world.item.ItemStack");
	private final static RefClass nbtTagCompoundClazz = ReflectionUtils.getRefClass("{nms}.NBTTagCompound", "{nm}.nbt.NBTTagCompound");
	private final static RefMethod saveNmsItemStackMethod;
	static{
		final RefClass nbtTagCompoundClazz = ReflectionUtils.getRefClass("{nms}.NBTTagCompound", "{nm}.nbt.NBTTagCompound");
		RefMethod saveNmsItemStackMethodTemp;
		try{
			RefClass classHolderLookupProvider = ReflectionUtils.getRefClass("{nm}.core.HolderLookup$Provider");//1.20.5+
			RefClass classNBTBase = ReflectionUtils.getRefClass("{nm}.nbt.NBTBase");
			saveNmsItemStackMethodTemp = nmsItemStackClazz.findMethod(/*isStatic=*/false, classNBTBase, classHolderLookupProvider);
		}
		catch(RuntimeException ex){
			saveNmsItemStackMethodTemp = nmsItemStackClazz.findMethod(/*isStatic=*/false, nbtTagCompoundClazz, nbtTagCompoundClazz);
		}
		saveNmsItemStackMethod = saveNmsItemStackMethodTemp;
	}

	// https://www.spigotmc.org/threads/tut-item-tooltips-with-the-chatcomponent-api.65964/
	/**
	 * Converts an {@link org.bukkit.inventory.ItemStack} to a JSON string
	 * for sending with TextAction.ITEM
	 *
	 * @param itemStack the item to convert
	 * @return the JSON string representation of the item
	 */
	public final static String convertItemStackToJson(ItemStack item, int JSON_LIMIT){
		Object nmsItemStackObj = asNMSCopyMethod.call(item);
		Object newTagOrRegistryAccess = registryAccessObj != null ? registryAccessObj : nbtTagCompoundClazz.getConstructor().create();
		Object itemAsJsonObject = saveNmsItemStackMethod.of(nmsItemStackObj).call(newTagOrRegistryAccess);
		String jsonString = itemAsJsonObject.toString();
		if(jsonString.length() > JSON_LIMIT){
			item = new ItemStack(item.getType(), item.getAmount());//TODO: Reduce item json data in a less destructive way-
			//reduceItemData() -> clear book pages, clear hidden NBT, call recursively for containers
			nmsItemStackObj = asNMSCopyMethod.call(item);
			newTagOrRegistryAccess = registryAccessObj != null ? registryAccessObj : nbtTagCompoundClazz.getConstructor().create();
			itemAsJsonObject = saveNmsItemStackMethod.of(nmsItemStackObj).call(newTagOrRegistryAccess);
			jsonString = itemAsJsonObject.toString();
		}
		return itemAsJsonObject.toString();
	}

	public final static Component getItemDisplayNameComponent(@Nonnull ItemStack item){
		String rarityColor = TextUtils.getRarityColor(item).name().toLowerCase();
		String rawDisplayName = JunkUtils.getDisplayName(item);
		if(rawDisplayName != null){
			return new ListComponent(
					new RawTextComponent(/*text=*/"", /*insert=*/null, /*click=*/null, /*hover=*/null,
							/*color=*/rarityColor, /*formats=*/Collections.singletonMap(Format.ITALIC, true)),
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
		String rarityColor = TextUtils.getRarityColor(item).name().toLowerCase();
		String rawDisplayName = JunkUtils.getDisplayName(item);
		if(rawDisplayName != null){
			return new ListComponent(
					new RawTextComponent(/*text=*/"[", /*insert=*/null, /*click=*/null, hoverAction, /*color=*/rarityColor, /*formats=*/null),
					new ListComponent(
							new RawTextComponent(/*text=*/"", /*insert=*/null, /*click=*/null, null,
									/*color=*/null, /*formats=*/Collections.singletonMap(Format.ITALIC, true)),
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

	public final static Component getDisplayNameSelectorComponent(Entity entity){
		if(entity instanceof Player && !((Player)entity).getDisplayName().equals(entity.getName())){
			final String selectorHoverText = entity.getName()+"\nType: Player\n"+entity.getUniqueId();
			final String selectorClickSuggestText = "/tell "+entity.getName()+" ";
			return new ListComponent(
					new RawTextComponent(
						/*text=*/"", /*insert=*/null,
						new TextClickAction(ClickEvent.SUGGEST_COMMAND, selectorClickSuggestText),
						new TextHoverAction(HoverEvent.SHOW_TEXT, selectorHoverText),
						/*color=*/null, /*formats=*/null
					),
					TellrawUtils.convertHexColorsToComponentsWithReset(((Player)entity).getDisplayName())
			);
		}
		else{
			return new SelectorComponent(entity.getUniqueId());
		}
	}

// not currently used
//	// Similar as above, but for Entity instead of ItemStack
//	final static RefClass craftEntityClazz = ReflectionUtils.getRefClass("{cb}.entity.CraftEntity");
//	final static RefMethod entityGetHandleMethod = craftEntityClazz.getMethod("getHandle");
//	final static RefClass nmsEntityClazz = ReflectionUtils.getRefClass("{nms}.Entity", "{nm}.world.entity.Entity");
//	final static RefMethod saveNmsEntityMethod = nmsEntityClazz.findMethod(/*isStatic=*/false, nbtTagCompoundClazz, nbtTagCompoundClazz);
//	public final static String convertEntityToJson(Entity entity){
//		Object nmsNbtTagCompoundObj = nbtTagCompoundClazz.getConstructor().create();
//		Object nmsEntityObj = entityGetHandleMethod.of(entity).call();
//		Object entityAsJsonObject = saveNmsEntityMethod.of(nmsEntityObj).call(nmsNbtTagCompoundObj);
//		return entityAsJsonObject.toString();
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

	private static RefMethod essMethodGetUser, essMethodIsVanished;
	private static RefMethod vanishMethodGetManager, vanishMethodIsVanished;
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
		return p.getServer().getOnlinePlayers().stream().allMatch(p2 -> !p2.canSee(p) || p2.isOp() || p2.getUniqueId().equals(p.getUniqueId()))
			&& p.getServer().getOnlinePlayers().stream().anyMatch(p2 -> !p2.canSee(p));
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

	private final static RefClass craftPlayerClazz = ReflectionUtils.getRefClass("{cb}.entity.CraftPlayer");
	private final static RefMethod playerGetProfileMethod = craftPlayerClazz.getMethod("getProfile");
	private final static GameProfile getGameProfile(Player player){return (GameProfile)playerGetProfileMethod.of(player).call();}

	private final static RefMethod propertyGetValueMethod = ReflectionUtils.getRefClass(Property.class).findMethodByName("getValue", "value");
	public final static String getPropertyValue(Property p){return (String)propertyGetValueMethod.of(p).call();}

	@SuppressWarnings("deprecation")
	public final static GameProfile getGameProfile(String nameOrUUID, boolean fetchSkin, Plugin nullForSync){
		Player player;
		try{player = Bukkit.getServer().getPlayer(UUID.fromString(nameOrUUID));}
		catch(java.lang.IllegalArgumentException e){player = null;/*thrown by UUID.fromString*/}
		if(player == null) player = Bukkit.getServer().getPlayer(nameOrUUID);
		if(player != null){
			final GameProfile profile = new GameProfile(player.getUniqueId(), player.getName());
			if(fetchSkin){
				final GameProfile rawProfile = getGameProfile(player);
				if(rawProfile.getProperties() != null && rawProfile.getProperties().containsKey("textures")){
					final Collection<Property> textures = rawProfile.getProperties().get("textures");
					if(textures != null && !textures.isEmpty()){
						final String code0 = getPropertyValue(textures.iterator().next());
						profile.getProperties().put("textures", new Property("textures", code0));
					}
				}
			}
			WebUtils.addGameProfileToCache(nameOrUUID, profile);
			return profile;
		}
		return WebUtils.getGameProfile(nameOrUUID, fetchSkin, nullForSync);
	}

// not currently used
//	// Called by HeadAPI for downloaded texture files
//	final static String stripComments(String fileContent){ // Copied roughly from FileIO
//		StringBuilder output = new StringBuilder();
//		for(String line : fileContent.split("\n")){
//			line = line.trim().replace("//", "#");
//			final int cut = line.indexOf('#');
//			if(cut == -1) output.append('\n').append(line);
//			else if(cut > 0) output.append('\n').append(line.substring(0, cut).trim());
//		}
//		return output.length() == 0 ? "" : output.substring(1);
//	}

// not currently used
//	public interface TestFunc{boolean test(int num);}
//	public final static int binarySearch(TestFunc f, int a, int b){
//		while(a < b-1){
//			int x = (b + a)/2;
//			if(f.test(x)) a = x;
//			else b = x;
//		}
//		return a;
//	}

// not currently used
//	public final static int exponentialSearch(TestFunc f, int a){
//		a = f.test(a) ? 2*a : 1;
//		while(f.test(a)) a *= 2;
//		return binarySearch(f, a/2, a);
//	}
}
