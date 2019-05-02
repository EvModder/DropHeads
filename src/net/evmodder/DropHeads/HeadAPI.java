package net.evmodder.DropHeads;

import java.util.TreeMap;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.evmodder.EvLib2.EvUtils;
import net.evmodder.EvLib2.FileIO;

public class HeadAPI {
	final private DropHeads pl;
	final boolean grummEnabled;
	final TreeMap<String, String> textures;

	HeadAPI(){
		textures = new TreeMap<String, String>();
		pl = DropHeads.getPlugin();
		grummEnabled = pl.getConfig().getBoolean("grumm-heads", true);

		String pluginHeadList = FileIO.loadResource(pl, "head-textures.txt");
		String localHeadList = FileIO.loadFile("head-textures.txt",
				pl.getClass().getResourceAsStream("/head-textures.txt"));
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
		String textureKey = TextureKeyEntityLookup.getTextureKey(entity);
		if(grummEnabled && EvUtils.hasGrummName(entity) && textures.containsKey(textureKey+"|GRUMM")) 
			return makeTextureSkull(entity.getType(), textureKey+"|GRUMM");

		// If there is no special "texture metadata"
		if(textureKey.indexOf('|') == -1)
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
				return new ItemStack(Material.CREEPER_HEAD);
			case ENDER_DRAGON:
				return new ItemStack(Material.DRAGON_HEAD);
			default:
				if(textures.containsKey(textureKey)) return makeTextureSkull(entity.getType(), textureKey);
				else return HeadUtils.makeSkull(entity.getType());
		}
		else if(textures.containsKey(textureKey) || textures.containsKey(textureKey=entity.getType().name()))
			return makeTextureSkull(entity.getType(), textureKey);
		else return HeadUtils.makeSkull(entity.getType());
	}
}