package net.evmodder.DropHeads;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Skull;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.evmodder.EvLib.EvUtils;

public class HeadUtils {
	public static final String[] MHF_Heads = new String[]{//Standard, Mojang-Provided MHF Heads
		"MHF_Alex", "MHF_Blaze", "MHF_CaveSpider", "MHF_Chicken", "MHF_Cow", "MHF_Creeper", "MHF_Enderman", "MHF_Ghast",
		"MHF_Golem", "MHF_Herobrine", "MHF_LavaSlime", "MHF_MushroomCow", "MHF_Ocelot", "MHF_Pig", "MHF_PigZombie",
		"MHF_Sheep", "MHF_Skeleton", "MHF_Slime", "MHF_Spider", "MHF_Squid", "MHF_Steve", "MHF_Villager",
		"MHF_Witch", "MHF_Wither", "MHF_WSkeleton", "MHF_Zombie",
		
		"MHF_Cactus", "MHF_Cake", "MHF_Chest", "MHF_CoconutB", "MHF_CoconutG", "MHF_Melon", "MHF_OakLog",
		"MHF_Present1","MHF_Present2", "MHF_Pumpkin", "MHF_TNT", "MHF_TNT2",
		
		"MHF_ArrowUp", "MHF_ArrowDown", "MHF_ArrowLeft", "MHF_ArrowRight", "MHF_Exclamation", "MHF_Question",
	};
	public static final Map<String, String> MHF_Lookup =
			Stream.of(MHF_Heads).collect(Collectors.toMap(h -> h.toUpperCase(), h -> h));

	public static final HashMap<EntityType, String> customHeads;//People who have set their skin to an Entity's head
	static{
		customHeads = new HashMap<EntityType, String>();
		customHeads.put(EntityType.BAT, "ManBatPlaysMC");
		customHeads.put(EntityType.ELDER_GUARDIAN, "MHF_EGuardian");//made by player
		customHeads.put(EntityType.ENDERMITE, "MHF_Endermites");//made by player
		customHeads.put(EntityType.EVOKER, "MHF_Evoker");//made by player
		customHeads.put(EntityType.GUARDIAN, "MHF_Guardian");//made by player
		customHeads.put(EntityType.HORSE, "gavertoso");
		customHeads.put(EntityType.PARROT, "MHF_Parrot");//made by player
		customHeads.put(EntityType.POLAR_BEAR, "NiXWorld");
		customHeads.put(EntityType.RABBIT, "MHF_Rabbit");//made by player
		customHeads.put(EntityType.SHULKER, "MHF_Shulker");//made by player
		customHeads.put(EntityType.SILVERFISH, "MHF_Silverfish");//made by player
		customHeads.put(EntityType.VEX, "MHF_Vex");//made by player
		customHeads.put(EntityType.VINDICATOR, "Vindicator");
		customHeads.put(EntityType.SNOWMAN, "MHF_SnowGolem");//made by player
		customHeads.put(EntityType.WITCH, "MHF_Witch");//made by player
		customHeads.put(EntityType.WOLF, "MHF_Wolf");//made by player
		customHeads.put(EntityType.ZOMBIE_VILLAGER, "scraftbrothers11");
	}

	private static Field fieldProfileItem, fieldProfileBlock;
//	private static RefClass classCraftWorld = ReflectionUtils.getRefClass("{cb}.CraftWorld");
//	private static RefClass classBlockPosition = ReflectionUtils.getRefClass("{nms}.BlockPosition");
//	private static RefClass classTileEntitySkull = ReflectionUtils.getRefClass("{nms}.TileEntitySkull");
//	private static RefClass classWorldServer = ReflectionUtils.getRefClass("{nms}.WorldServer");
//	private static RefMethod methodGetHandle = classCraftWorld.getMethod("getHandle");
//	private static RefMethod methodGetTileEntity = classWorldServer.getMethod("getTileEntity");
//	private static RefMethod methodGetGameProfile = classTileEntitySkull.getMethod("getGameProfile");
//	private static RefConstructor cBlockPosition = classBlockPosition.getConstructor(int.class, int.class, int.class);
	public static void setGameProfile(SkullMeta meta, GameProfile profile){
		try{
			if(fieldProfileItem == null) fieldProfileItem = meta.getClass().getDeclaredField("profile");
			fieldProfileItem.setAccessible(true);
			fieldProfileItem.set(meta, profile);
		}
		catch(NoSuchFieldException e){e.printStackTrace();}
		catch(SecurityException e){e.printStackTrace();}
		catch(IllegalArgumentException e){e.printStackTrace();}
		catch(IllegalAccessException e){e.printStackTrace();}
	}
	public static void setGameProfile(Skull bState, GameProfile profile){
		try{
			if(fieldProfileBlock == null) fieldProfileBlock = bState.getClass().getDeclaredField("profile");
			fieldProfileBlock.setAccessible(true);
			fieldProfileBlock.set(bState, profile);
		}
		catch(NoSuchFieldException e){e.printStackTrace();}
		catch(SecurityException e){e.printStackTrace();}
		catch(IllegalArgumentException e){e.printStackTrace();}
		catch(IllegalAccessException e){e.printStackTrace();}
	}
	public static GameProfile getGameProfile(SkullMeta meta){
		try{
			if(fieldProfileItem == null) fieldProfileItem = meta.getClass().getDeclaredField("profile");
			fieldProfileItem.setAccessible(true);
			return (GameProfile) fieldProfileItem.get(meta);
		}
		catch(NoSuchFieldException e){e.printStackTrace();}
		catch(SecurityException e){e.printStackTrace();}
		catch(IllegalArgumentException e){e.printStackTrace();}
		catch(IllegalAccessException e){e.printStackTrace();}
		return null;
	}
	public static GameProfile getGameProfile(Skull bState){
		try{
			if(fieldProfileBlock == null) fieldProfileBlock = bState.getClass().getDeclaredField("profile");
			fieldProfileBlock.setAccessible(true);
			return (GameProfile) fieldProfileBlock.get(bState);
		}
		catch(NoSuchFieldException e){e.printStackTrace();}
		catch(SecurityException e){e.printStackTrace();}
		catch(IllegalArgumentException e){e.printStackTrace();}
		catch(IllegalAccessException e){e.printStackTrace();}
		return null;
		/*return (GameProfile)methodGetGameProfile.of(//TileEntitySkull
				methodGetTileEntity.of(//WorldServer
						methodGetHandle.of(bState.getWorld()).call())//CraftWorld
				.call(cBlockPosition.create(bState.getX(), bState.getY(), bState.getZ())))
			.call();*/
	}

	public static ItemStack makeSkull(String textureCode){
		return makeSkull(textureCode, ChatColor.YELLOW+"UNKNOWN Head");
	}
	public static ItemStack makeSkull(String textureCode, String headName){
		ItemStack item = new ItemStack(Material.PLAYER_HEAD);
		if(textureCode == null) return item;
		SkullMeta meta = (SkullMeta) item.getItemMeta();

		GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(textureCode.getBytes()), textureCode);
		profile.getProperties().put("textures", new Property("textures", textureCode));
		setGameProfile(meta, profile);

		meta.setDisplayName(headName);
		item.setItemMeta(meta);
		return item;
	}

	static boolean isSkeletal(EntityType type){
		switch(type){
			case SKELETON:
			case SKELETON_HORSE:
			case WITHER_SKELETON:
			case STRAY:
				return true;
			default:
				return false;
		}
	}

	public static boolean dropsHeadFromChargedCreeper(EntityType eType){// In vanilla.
		switch(eType){
			case ZOMBIE:
			case CREEPER:
			case SKELETON:
			case WITHER_SKELETON:
			//case ZOMBIE_VILLAGER: // Surprisingly not, actually.
				return true;
			default:
				return false;
		}
	}

	@SuppressWarnings("deprecation")
	static ItemStack makeSkull(EntityType entity){
		ItemStack head = new ItemStack(Material.PLAYER_HEAD);
		SkullMeta meta = (SkullMeta) head.getItemMeta();

		String MHFName = EvUtils.getMHFHeadName(entity.name());
		if(MHF_Lookup.containsKey(MHFName.toUpperCase())){
			meta.setOwningPlayer(org.bukkit.Bukkit.getOfflinePlayer(MHFName));
			meta.setDisplayName(ChatColor.YELLOW+MHFName);
		}
		else{
			GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(entity.name().getBytes()), entity.name());
			HeadUtils.setGameProfile(meta, profile);
			meta.setDisplayName(ChatColor.YELLOW+EvUtils.getNormalizedName(entity.name())+
					(isSkeletal(entity) ? " Skull" : " Head"));
		}
		head.setItemMeta(meta);
		return head;
	}

	public static ItemStack getPlayerHead(GameProfile profile){
		ItemStack head = new ItemStack(Material.PLAYER_HEAD);
		SkullMeta meta = (SkullMeta) head.getItemMeta();
		setGameProfile(meta, profile);
		if(profile.getName() != null){
			if(profile.getName().startsWith("MHF_")) meta.setDisplayName(ChatColor.YELLOW+profile.getName());
			else meta.setDisplayName(ChatColor.YELLOW+profile.getName()+" Head");
		}
		head.setItemMeta(meta);
		return head;
	}
	public static ItemStack getPlayerHead(OfflinePlayer player){
		GameProfile profile = new GameProfile(player.getUniqueId(), player.getName());
		return getPlayerHead(profile);
	}
}