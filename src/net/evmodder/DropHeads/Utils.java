package net.evmodder.DropHeads;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Skull;
import org.bukkit.craftbukkit.v1_14_R1.entity.CraftFox;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Ocelot;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.TraderLlama;
import org.bukkit.entity.TropicalFish;
import org.bukkit.entity.TropicalFish.Pattern;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.evmodder.EvLib.EvUtils;
import net.evmodder.EvLib.FileIO;
import net.minecraft.server.v1_14_R1.EntityFox;

public class Utils {
	static class EntityData{
		EntityType type;
		String data;
		EntityData(EntityType t, String d){type=t; data=d;}
	}

	private static boolean useCustomHeads, grummEnabled;
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

	static HashMap<String, String> textures = new HashMap<String, String>();
	public static final HashMap<EntityType, String> customHeads;//People who have set their skins to an Entity's head
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
		/*Need: DONKEY, LLAMA, MULE, SKELETON_HORSE ZOMBIE_HORSE, STRAY, ILLUSIONER */
	}
	static class CCP{
		DyeColor bodyColor, patternColor;
		Pattern pattern;
		CCP(DyeColor color, DyeColor pColor, Pattern p){bodyColor = color; patternColor = pColor; pattern = p;}
	}
	public static final HashMap<CCP, String> tropicalFishNames;//Names for the 22 common tropical fish
	static{
		tropicalFishNames = new HashMap<CCP, String>();
		tropicalFishNames.put(new CCP(DyeColor.ORANGE, DyeColor.GRAY, Pattern.STRIPEY), "Anemone");
	}

	public Utils(){
		DropHeads pl = DropHeads.getPlugin();
		useCustomHeads = pl.getConfig().getBoolean("custom-skins-for-missing-heads", true);
		grummEnabled = pl.getConfig().getBoolean("grumm-heads", true);

		String headsList = FileIO.loadFile("head-list.txt", pl.getClass().getResourceAsStream("/head-list.txt"));
		for(String head : headsList.split("\n")){
			head = head.replaceAll(" ", "");
			int i = head.indexOf(":");
			if(i != -1) try{
				String texture = head.substring(i+1);
				if(texture.isEmpty()) continue;

				String key = head.substring(0, i);
				int j = key.indexOf('|');
				if(j == -1){
					EntityType type = EntityType.valueOf(key.toUpperCase());
					textures.put(type.name(), texture);
				}
				else/* if(grummEnabled || !key.endsWith("|GRUMM"))*/{
					EntityType type = EntityType.valueOf(key.substring(0, j).toUpperCase());
					textures.put(type.name()+key.substring(j), texture);
					pl.getLogger().fine("Loaded: "+type.name()+" - "+key.substring(j+1));
				}
			}
			catch(IllegalArgumentException ex){
				pl.getLogger().warning("Invalid entity name '"+head.substring(0, i)+"' from head-list.txt file!");
			}
		}
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

	public static ItemStack makeTextureSkull(String code){
		return makeTextureSkull(code, ChatColor.YELLOW+"UNKNOWN Head");
	}
	public static ItemStack makeTextureSkull(String code, String headName){
		ItemStack item = new ItemStack(Material.PLAYER_HEAD);
		if(code == null) return item;
		SkullMeta meta = (SkullMeta) item.getItemMeta();

		GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(code.getBytes()), code);
		profile.getProperties().put("textures", new Property("textures", code));
		setGameProfile(meta, profile);

		meta.setDisplayName(headName);
		item.setItemMeta(meta);
		return item;
	}

	public static ItemStack makeTextureSkull(EntityType entity, String textureKey){
		ItemStack item = new ItemStack(Material.PLAYER_HEAD);
		String code = textures.get(textureKey);
		if(code == null) return item;
		SkullMeta meta = (SkullMeta) item.getItemMeta();

		GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(code.getBytes()), entity.name()+"|"+textureKey);
		profile.getProperties().put("textures", new Property("textures", code));
		setGameProfile(meta, profile);

		meta.setDisplayName(ChatColor.YELLOW+EvUtils.getNormalizedName(entity)+" Head");
		item.setItemMeta(meta);
		return item;
	}

	@SuppressWarnings("deprecation")
	private static ItemStack makeNonTextureSkull(EntityType entity){
		ItemStack head = new ItemStack(Material.PLAYER_HEAD);
		SkullMeta meta = (SkullMeta) head.getItemMeta();

		String normalName = ChatColor.YELLOW+EvUtils.getNormalizedName(entity);
		String MHF_Name = "MHF_"+EvUtils.getMHFHeadName(entity);

		if(MHF_Lookup.containsKey(MHF_Name.toUpperCase())) meta.setOwner(MHF_Name);
		else if(useCustomHeads && customHeads.containsKey(entity)) meta.setOwner(customHeads.get(entity));
		else meta.setOwner(normalName);
		meta.setDisplayName(normalName + (meta.getOwner().startsWith("MHF_") ? "" : " Head"));
		head.setItemMeta(meta);
		return head;
	}

	public static ItemStack getHead(EntityType entity, String data){
		if(data != null && data.isEmpty()) data = null;
		switch(entity){
			case WITHER_SKELETON:
				return new ItemStack(Material.WITHER_SKELETON_SKULL);
			case SKELETON:
				return new ItemStack(Material.SKELETON_SKULL);
			case ENDER_DRAGON:
				return new ItemStack(Material.DRAGON_HEAD);
			case ZOMBIE:
				if(data == null) return new ItemStack(Material.ZOMBIE_HEAD);
			case CREEPER:
				if(data == null) return new ItemStack(Material.CREEPER_HEAD);
			default:
				String textureKey = entity.name() + (data == null ? "" : "|"+data);
				if(textures.containsKey(textureKey)) return Utils.makeTextureSkull(entity, textureKey);
				else return makeNonTextureSkull(entity);
			}
	}

	public static ItemStack getHead(LivingEntity entity){
		String textureKey = "";
		switch(entity.getType()){
			case PLAYER:
				return getPlayerHead((OfflinePlayer)entity);
			case WITHER_SKELETON:
				return new ItemStack(Material.WITHER_SKELETON_SKULL);
			case SKELETON:
				return new ItemStack(Material.SKELETON_SKULL);
			case ZOMBIE:
				return new ItemStack(Material.ZOMBIE_HEAD);
			case CREEPER:
				if(((Creeper)entity).isPowered()) textureKey = "CREEPER|CHARGED";
				else return new ItemStack(Material.CREEPER_HEAD);
				break;
			case ENDER_DRAGON:
				return new ItemStack(Material.DRAGON_HEAD);
			case WOLF:
				textureKey = entity.getType().name();
				if(((Wolf)entity).isAngry()) textureKey += "|ANGRY";
				break;
			case HORSE:
				textureKey = "HORSE|"+((Horse)entity).getColor().name();
				break;
			case LLAMA:
				textureKey = "LLAMA|"+((Llama)entity).getColor().name();
				break;
			case PARROT:
				textureKey = "PARROT|"+((Parrot)entity).getVariant().name();
				break;
			case RABBIT:
				textureKey = "RABBIT|"+((Rabbit)entity).getRabbitType().name();
				break;
			case SHEEP:
				textureKey = "SHEEP|";
				if(entity.getCustomName() != null && entity.getCustomName().equals("jeb_")) textureKey += "JEB";
				else textureKey += ((Sheep)entity).getColor().name();
				break;
			case SHULKER:
				textureKey = "SHULKER|"+((Shulker)entity).getColor().name();
				break;
			case TROPICAL_FISH:
				TropicalFish f = (TropicalFish)entity;
				String name = tropicalFishNames.get(new CCP(f.getBodyColor(), f.getPatternColor(), f.getPattern()));
				if(name == null) name = EvUtils.getNormalizedName(entity.getType());
				String code = "wow i need to figure this out huh";
				return makeTextureSkull(code, name);
			case VEX:
				textureKey = "VEX"+(((Vex)entity).isCharging() ? "|CHARGING" : "");
				break;
			case ZOMBIE_VILLAGER:
				textureKey = "ZOMBIE_VILLAGER|"+((ZombieVillager)entity).getVillagerProfession().name();
				break;
			case OCELOT:
				textureKey = "OCELOT|"+((Ocelot)entity).getCatType().name();
			case CAT:
				textureKey = "OCELOT|"+((Cat)entity).getCatType().name();
				break;
			case MUSHROOM_COW:
				textureKey = "MUSHROOM_COW|"+((MushroomCow)entity).getVariant().name();
			case FOX:
				EntityFox fox = ((CraftFox)entity).getHandle();
				textureKey = "FOX|RED";//TODO: "FOX|SNOW"
				if(fox.isSleeping()) textureKey += "|SLEEPING";
			case PANDA:
				textureKey = "PANDA|"+EvUtils.getPandaTrait((Panda)entity);
			case TRADER_LLAMA:
				textureKey = "TRADER_LLAMA|"+((TraderLlama)entity).getColor().name();
			default:
				textureKey = entity.getType().name();
		}
		if(grummEnabled && entity.getCustomName() != null
				&& (entity.getCustomName().equals("Dinnerbone") || entity.getCustomName().equals("Grumm"))
				&& textures.containsKey(textureKey+"|GRUMM")) 
			return Utils.makeTextureSkull(entity.getType(), textureKey+"|GRUMM");
				
		if(textures.containsKey(textureKey) || textures.containsKey(textureKey = entity.getType().name()))
			return makeTextureSkull(entity.getType(), textureKey);
		else
			return makeNonTextureSkull(entity.getType());
	}

	public static ItemStack getPlayerHead(OfflinePlayer player){
		ItemStack head = new ItemStack(Material.PLAYER_HEAD);
		SkullMeta meta = (SkullMeta) head.getItemMeta();
		GameProfile profile = new GameProfile(player.getUniqueId(), player.getName());
		setGameProfile(meta, profile);
		meta.setOwningPlayer(player);
		meta.setDisplayName(ChatColor.YELLOW+player.getName()+" Head");
		head.setItemMeta(meta);
		return head;
	}
}