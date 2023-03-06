package net.evmodder.DropHeads;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import io.netty.channel.Channel;
import javax.annotation.Nonnull;
import net.evmodder.EvLib.extras.NBTTagUtils;
import net.evmodder.EvLib.extras.NBTTagUtils.RefNBTTagString;
import net.evmodder.EvLib.extras.NBTTagUtils.RefNBTTagCompound;
import net.evmodder.EvLib.extras.NBTTagUtils.RefNBTTagList;
import net.evmodder.EvLib.extras.ReflectionUtils;
import net.evmodder.EvLib.extras.TellrawUtils;
import net.evmodder.EvLib.extras.TextUtils;
import net.evmodder.EvLib.extras.TypeUtils;
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
public class JunkUtils{
	public final static String SPAWN_CAUSE_MULTIPLIER_KEY = "SRM";
	public final static String DISABLE_LOCAL_BEHEAD_MESSAGES_KEY = "DisableLocalBeheadMessages";

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

	public final static double getSpawnCauseMult(Entity e){
		if(e.hasMetadata(SPAWN_CAUSE_MULTIPLIER_KEY)) return e.getMetadata(SPAWN_CAUSE_MULTIPLIER_KEY).get(0).asDouble();
		for(String tag : e.getScoreboardTags()) if(tag.startsWith(SPAWN_CAUSE_MULTIPLIER_KEY)) return Float.parseFloat(
				tag.substring(SPAWN_CAUSE_MULTIPLIER_KEY.length()+1/*+1 because of '_'*/));
		return 1D;
	}

	public final static boolean hasLocalBeheadMessagedEnabled(Entity e){
		return !e.hasMetadata(DISABLE_LOCAL_BEHEAD_MESSAGES_KEY) && !e.getScoreboardTags().contains(DISABLE_LOCAL_BEHEAD_MESSAGES_KEY);
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
	private final static RefClass craftItemStackClazz = ReflectionUtils.getRefClass("{cb}.inventory.CraftItemStack");
	private final static RefMethod asNMSCopyMethod = craftItemStackClazz.getMethod("asNMSCopy", ItemStack.class);

	// NMS Method to serialize a net.minecraft.server.vX_X.ItemStack to a valid JSON string
	private final static RefClass nmsItemStackClazz = ReflectionUtils.getRefClass("{nms}.ItemStack", "{nm}.world.item.ItemStack");
	private final static RefClass nbtTagCompoundClazz = ReflectionUtils.getRefClass("{nms}.NBTTagCompound", "{nm}.nbt.NBTTagCompound");
	private final static RefMethod saveNmsItemStackMethod = nmsItemStackClazz.findMethod(/*isStatic=*/false, nbtTagCompoundClazz, nbtTagCompoundClazz);

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
		String rarityColor = TypeUtils.getRarityColor(item).name().toLowerCase();
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

	private final static RefClass craftPlayerClazz = ReflectionUtils.getRefClass("{cb}.entity.CraftPlayer");
	private final static RefMethod playerGetHandleMethod = craftPlayerClazz.getMethod("getHandle");
	private final static RefClass entityPlayerClazz = ReflectionUtils.getRefClass("{nms}.EntityPlayer", "{nm}.server.level.EntityPlayer");
	private final static RefClass playerConnectionClazz = ReflectionUtils.getRefClass("{nms}.PlayerConnection", "{nm}.server.network.PlayerConnection");
	private final static RefField playerConnectionField = entityPlayerClazz.findField(playerConnectionClazz);
	private final static RefClass networkManagerClazz = ReflectionUtils.getRefClass("{nms}.NetworkManager", "{nm}.network.NetworkManager");
	private final static RefField networkManagerField = playerConnectionClazz.findField(networkManagerClazz);
	private final static RefField channelField = networkManagerClazz.findField(Channel.class);
	public static Channel getPlayerChannel(Player player){
		final Object playerEntity = playerGetHandleMethod.of(player).call();
		final Object playerConnection = playerConnectionField.of(playerEntity).get();
		final Object networkManager = networkManagerField.of(playerConnection).get();
		return (Channel)channelField.of(networkManager).get();
	}
	private final static RefClass classPacket = ReflectionUtils.getRefClass("{nms}.Packet", "{nm}.network.protocol.Packet");
	private final static RefMethod sendPacketMethod = playerConnectionClazz.findMethod(/*isStatic=*/false, Void.TYPE, classPacket);
	public static void sendPacket(Player player, Object packet){
		Object entityPlayer = playerGetHandleMethod.of(player).call();
		Object playerConnection = playerConnectionField.of(entityPlayer).get();
		Object castPacket = classPacket.getRealClass().cast(packet);
		sendPacketMethod.of(playerConnection).call(castPacket);
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

	private final static RefMethod playerGetProfileMethod = craftPlayerClazz.getMethod("getProfile");
	private final static GameProfile getGameProfile(Player player){return (GameProfile)playerGetProfileMethod.of(player).call();}

	@SuppressWarnings("deprecation")
	public final static GameProfile getGameProfile(String nameOrUUID, boolean fetchSkin){
		Player player;
		try{player = Bukkit.getServer().getPlayer(UUID.fromString(nameOrUUID));}
		catch(java.lang.IllegalArgumentException e){player = null;/*thrown by UUID.fromString*/}
		if(player == null) Bukkit.getServer().getPlayer(nameOrUUID);
		if(player != null){
			GameProfile profile = new GameProfile(player.getUniqueId(), player.getName());
			if(fetchSkin){
				GameProfile rawProfile = getGameProfile(player);
				if(rawProfile.getProperties() != null && rawProfile.getProperties().containsKey("textures")){
					final Collection<Property> textures = rawProfile.getProperties().get("textures");
					if(textures != null && !textures.isEmpty()){
						final String code0 = textures.iterator().next().getValue();
						profile.getProperties().put("textures", new Property("textures", code0));
					}
				}
			}
			WebUtils.addGameProfileToCache(nameOrUUID, profile);
			return profile;
		}
		return WebUtils.getGameProfile(nameOrUUID, fetchSkin);
	}

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
