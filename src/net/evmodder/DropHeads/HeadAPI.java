package net.evmodder.DropHeads;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.evmodder.EvLib.EvUtils;
import net.evmodder.EvLib.FileIO;

public class HeadAPI {
	final private DropHeads pl;
	final boolean grummEnabled, updateOldPlayerHeads;
	final TreeMap<String, String> textures;

	HeadAPI(){
		textures = new TreeMap<String, String>();
		pl = DropHeads.getPlugin();
		grummEnabled = pl.getConfig().getBoolean("grumm-heads", true);
		updateOldPlayerHeads = pl.getConfig().getBoolean("update-on-skin-change", true);

		String hardcodedList = FileIO.loadResource(pl, "head-textures.txt");
		loadTextures(hardcodedList, false);
//		String localList = FileIO.loadFile("head-textures.txt", pl.getClass().getResourceAsStream("/head-textures.txt"));
		String localList = FileIO.loadFile("head-textures.txt", hardcodedList);
		loadTextures(localList, true);
	}

	void loadTextures(String headsList, boolean log){
		HashSet<EntityType> missingHeads = new HashSet<EntityType>();
		missingHeads.addAll(Arrays.asList(EntityType.values()).stream()
				.filter(x -> x.isAlive()/* && x.isMeow() */).collect(Collectors.toList()));
		missingHeads.remove(EntityType.PLAYER);
		missingHeads.remove(EntityType.ARMOR_STAND); // These 2 are 'alive', but don't have heads
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
					missingHeads.remove(type);
				}
			}
			catch(IllegalArgumentException ex){
				//pl.getLogger().warning("Invalid entity name '"+head.substring(0, i)+"' from head-list.txt file!");
			}
		}
		if(log){
			for(EntityType type : missingHeads){
				pl.getLogger().warning("Missing head texture for "+type+" from head-list.txt");
			}
			if(!missingHeads.isEmpty()){
				pl.getLogger().info("To update missing textures, try updating the plugin"
						+ " and then deleting the old head-list.txt file");
			}
		}
		// Sometimes a texture value is just a reference to a different texture key
		Iterator<Entry<String, String>> it = textures.entrySet().iterator();
		while(it.hasNext()){
			Entry<String, String> e = it.next();
			String redirect = e.getValue();
			while((redirect=textures.get(redirect)) != null) e.setValue(redirect);
		}
	}

	public boolean textureExists(String textureKey){return textures.containsKey(textureKey);}
	public TreeMap<String, String> getTextures(){return textures;}

	public ItemStack makeTextureSkull(String textureKey){
		// Attempt to parse out an EntityType
		EntityType eType;
		int j = textureKey.indexOf('|');
		String nameStr = (j == -1 ? textureKey : textureKey.substring(0, j)).toUpperCase();
		try{eType = EntityType.valueOf(nameStr);}
		catch(IllegalArgumentException ex){
			pl.getLogger().warning("Unknown EntityType: "+nameStr+"!");
			eType = null;// This is OK; we'll just use textureKey[0]
		}
		ItemStack item = new ItemStack(Material.PLAYER_HEAD);
		SkullMeta meta = (SkullMeta) item.getItemMeta();

		UUID uuid = UUID.nameUUIDFromBytes(textureKey.getBytes());// Stable UUID for this textureKey
		GameProfile profile = new GameProfile(uuid, textureKey);// Initialized with UUID and name
		String code = textures.get(textureKey);
		if(code != null) profile.getProperties().put("textures", new Property("textures", code));
		HeadUtils.setGameProfile(meta, profile);

		meta.setDisplayName(ChatColor.YELLOW+TextureKeyLookup.getNameFromKey(eType, textureKey)+" Head");
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack getHead(EntityType eType, String textureKey){
		// If there is extra "texture metadata" we should return the custom
		// skull instead of a just, say, a basic creeper head
		if(textureKey != null && textureKey.indexOf('|') != -1 && textures.containsKey(textureKey)){
			return makeTextureSkull(textureKey);
		}
		switch(eType){
			case PLAYER:
				return new ItemStack(Material.PLAYER_HEAD);
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
				if(textures.containsKey(eType.name())) return makeTextureSkull(eType.name());
				else return HeadUtils.makeSkull(eType);
			}
	}

	public ItemStack getHead(LivingEntity entity){
		if(entity.getType() == EntityType.PLAYER) return HeadUtils.getPlayerHead((OfflinePlayer)entity);
		String textureKey = TextureKeyLookup.getTextureKey(entity);
		if(grummEnabled && EvUtils.hasGrummName(entity) && textures.containsKey(textureKey+"|GRUMM")) 
			return makeTextureSkull(textureKey+"|GRUMM");
		return getHead(entity.getType(), textureKey);
	}

	public ItemStack getHead(GameProfile profile){
		if(profile == null || profile.getName() == null) return null;
		if(pl.getAPI().textureExists(profile.getName())){//Refresh this EntityHead texture
			return makeTextureSkull(profile.getName());
		}
		else{//Looks like a PlayerHead
			OfflinePlayer p = profile.getId() == null ? null : pl.getServer().getOfflinePlayer(profile.getId());
			if(p != null && p.getName() != null){
				if(updateOldPlayerHeads || p.getName().equals(profile.getName())) return HeadUtils.getPlayerHead(p);
			}
		}
		return HeadUtils.getPlayerHead(profile);
	}
}