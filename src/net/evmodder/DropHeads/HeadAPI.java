package net.evmodder.DropHeads;

import java.util.TreeMap;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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
import org.bukkit.entity.Vex;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.evmodder.DropHeads.HeadUtils.CCP;
import net.evmodder.EvLib.EvUtils;
import net.evmodder.EvLib.FileIO;
import net.minecraft.server.v1_14_R1.EntityFox;

public class HeadAPI {
	final private DropHeads pl;
	final boolean grummEnabled;
	final TreeMap<String, String> textures;

	HeadAPI(){
		textures = new TreeMap<String, String>();
		pl = DropHeads.getPlugin();
		grummEnabled = pl.getConfig().getBoolean("grumm-heads", true);

		String pluginHeadList = FileIO.loadResource(pl, "head-list.txt");
		String localHeadList = FileIO.loadFile("head-list.txt", "");
		String headsList = pluginHeadList.concat("\n"+localHeadList);
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
					textures.put(key, texture);
					//textures.put(type.name()+key.substring(j), texture);//identical
					pl.getLogger().fine("Loaded: "+type.name()+" - "+key.substring(j+1));
				}
			}
			catch(IllegalArgumentException ex){
				pl.getLogger().warning("Invalid entity name '"+head.substring(0, i)+"' from head-list.txt file!");
			}
		}
	}

	public boolean textureExists(String textureKey){return textures.containsKey(textureKey);}
	public TreeMap<String, String> getTextures(){return textures;}

	public ItemStack makeTextureSkull(EntityType entity, String textureKey){
		ItemStack item = new ItemStack(Material.PLAYER_HEAD);
		String code = textures.get(textureKey);
		if(code == null) return item;
		SkullMeta meta = (SkullMeta) item.getItemMeta();

		GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(code.getBytes()), entity.name()+"|"+textureKey);
		profile.getProperties().put("textures", new Property("textures", code));
		HeadUtils.setGameProfile(meta, profile);

		meta.setDisplayName(ChatColor.YELLOW+EvUtils.getNormalizedName(entity)+" Head");
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack getHead(EntityType entity, String data){
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
				if(textures.containsKey(textureKey)) return makeTextureSkull(entity, textureKey);
				else return HeadUtils.makeSkull(entity);
			}
	}

	public ItemStack getHead(LivingEntity entity){
		String textureKey = "";
		switch(entity.getType()){
			case PLAYER:
				return HeadUtils.getPlayerHead((OfflinePlayer)entity);
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
				CCP fishData = new CCP(f.getBodyColor(), f.getPatternColor(), f.getPattern());
				String name = HeadUtils.tropicalFishNames.get(fishData);
				if(name == null) name = EvUtils.getNormalizedName(entity.getType());
				String code = "wow i need to figure this out huh";
				return HeadUtils.makeSkull(code, name);
			case VEX:
				textureKey = "VEX"+(((Vex)entity).isCharging() ? "|CHARGING" : "");
				break;
			case ZOMBIE_VILLAGER:
				textureKey = "ZOMBIE_VILLAGER|"+((ZombieVillager)entity).getVillagerProfession().name();
				break;
			case VILLAGER:
				textureKey = "VILLAGER|"+((Villager)entity).getProfession().name();
				break;
			case OCELOT:
				textureKey = "OCELOT|"+((Ocelot)entity).getCatType().name();
				break;
			case CAT:
				textureKey = "CAT|"+((Cat)entity).getCatType().name();
				break;
			case MUSHROOM_COW:
				textureKey = "MUSHROOM_COW|"+((MushroomCow)entity).getVariant().name();
				break;
			case FOX:
				EntityFox fox = ((CraftFox)entity).getHandle();
				textureKey = "FOX|RED";//TODO: "FOX|SNOW"
				if(fox.isSleeping()) textureKey += "|SLEEPING";
				break;
			case PANDA:
				textureKey = "PANDA|"+EvUtils.getPandaTrait((Panda)entity);
				break;
			case TRADER_LLAMA:
				textureKey = "TRADER_LLAMA|"+((TraderLlama)entity).getColor().name();
				break;
			default:
				textureKey = entity.getType().name();
		}
		if(grummEnabled && EvUtils.hasGrummName(entity) && textures.containsKey(textureKey+"|GRUMM")) 
			return makeTextureSkull(entity.getType(), textureKey+"|GRUMM");

		else if(textures.containsKey(textureKey) || textures.containsKey(textureKey=entity.getType().name()))
			return makeTextureSkull(entity.getType(), textureKey);
		else
			return HeadUtils.makeSkull(entity.getType());
	}
}