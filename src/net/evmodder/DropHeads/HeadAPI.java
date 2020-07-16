package net.evmodder.DropHeads;

import java.util.ArrayList;
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
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.evmodder.EvLib.FileIO;
import net.evmodder.EvLib.extras.HeadUtils;

public class HeadAPI {
	final private DropHeads pl;
	final boolean grummEnabled, updateOldPlayerHeads, updateZombiePigmenHeads;//, saveCustomLore;
	final TreeMap<String, String> textures; // Key="ENTITY_NAME|DATA", Value="eyJ0ZXh0dXJl..."

	HeadAPI(){
		textures = new TreeMap<String, String>();
		pl = DropHeads.getPlugin();
		grummEnabled = pl.getConfig().getBoolean("drop-grumm-heads", true);
		updateOldPlayerHeads = pl.getConfig().getBoolean("update-on-skin-change", true);
		boolean zombifiedPiglensExist = false;
		try{EntityType.valueOf("ZOMBIFIED_PIGLIN"); zombifiedPiglensExist = true;} catch(IllegalArgumentException ex){}
		updateZombiePigmenHeads = zombifiedPiglensExist && pl.getConfig().getBoolean("update-zombie-pigman-heads", true);
//		saveCustomLore = pl.getConfig().getBoolean("save-custom-lore", false);

		String hardcodedList = FileIO.loadResource(pl, "head-textures.txt");
		loadTextures(hardcodedList, /*logMissingEntities=*/true, /*logUnknownEntities=*/false);
//		String localList = FileIO.loadFile("head-textures.txt", pl.getClass().getResourceAsStream("/head-textures.txt"));
		String localList = FileIO.loadFile("head-textures.txt", hardcodedList);
		loadTextures(localList, /*logMissingEntities=*/false, /*logUnknownEntities=*/true);

		// This could be optimized by passing 'simple-mob-heads-only' to loadTextures to skip adding any textures with '|'
		if(pl.getConfig().getBoolean("simple-mob-heads-only", false)){
			ArrayList<String> allKeys = new ArrayList<>(textures.keySet());
			for(String key : allKeys) if(key.indexOf('|') != -1) textures.remove(key);
		}
	}

	void loadTextures(String headsList, boolean logMissingEntities, boolean logUnknownEntities){
		HashSet<EntityType> missingHeads = new HashSet<EntityType>();
		HashSet<String> unknownHeads = new HashSet<String>();
		if(logMissingEntities) missingHeads.addAll(Arrays.asList(EntityType.values()).stream()
				.filter(x -> x.isAlive()/* && x.isMeow() */).collect(Collectors.toList()));
		missingHeads.remove(EntityType.PLAYER);
		missingHeads.remove(EntityType.ARMOR_STAND); // These 2 are 'alive', but aren't in head-textures.txt
		for(String head : headsList.split("\n")){
			head = head.replaceAll(" ", "");
			int i = head.indexOf(":");
			if(i != -1){
				String texture = head.substring(i+1).trim();
				if(texture.isEmpty() || texture.equals("xxx")) continue; //TODO: remove the xxx

				String key = head.substring(0, i).toUpperCase();
				if(textures.put(key, texture) != null) continue; // Don't bother checking EntityType if this head has already been added

				int j = key.indexOf('|');
				String typeName = (j == -1 ? key : key.substring(0, j));
				try{
					EntityType type = EntityType.valueOf(typeName);
					missingHeads.remove(type);
				}
				catch(IllegalArgumentException ex){
					if(unknownHeads.add(typeName)){
						if(logUnknownEntities) pl.getLogger().warning("Unknown entity '"+typeName+"' in head-textures.txt");
					}
				}
			}
		}
		if(logMissingEntities){
			for(EntityType type : missingHeads){
				pl.getLogger().warning("Missing head texture(s) for "+type);
			}
			if(!missingHeads.isEmpty()){
				pl.getLogger().warning("To fix missing textures, try updating the plugin and then deleting the old head-textures.txt file");
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

	public String getHeadNameFromKey(String textureKey){
		// Attempt to parse out an EntityType
		EntityType eType;
		int j = textureKey.indexOf('|');
		String nameStr = (j == -1 ? textureKey : textureKey.substring(0, j)).toUpperCase();
		try{eType = EntityType.valueOf(nameStr);}
		catch(IllegalArgumentException ex){
			pl.getLogger().warning("Unknown EntityType: "+nameStr+"!"); // TODO: config option to hide this? (eg: PIG_ZOMBIE)
			eType = null;// We will just use textureKey[0]
		}

		// Actual getHeadName
		String entityName = TextureKeyLookup.getNameFromKey(/*eType, */textureKey);
		String headTypeName = eType == null ? "Head" : HeadUtils.getDroppedHeadTypeName(eType);
		return ChatColor.YELLOW + entityName + " " + headTypeName;
	}

	public ItemStack makeTextureSkull(String textureKey){
		ItemStack item = new ItemStack(Material.PLAYER_HEAD);
		SkullMeta meta = (SkullMeta) item.getItemMeta();

		UUID uuid = UUID.nameUUIDFromBytes(textureKey.getBytes());// Stable UUID for this textureKey
		GameProfile profile = new GameProfile(uuid, textureKey);// Initialized with UUID and name
		String code = textures.get(textureKey);
		if(code != null) profile.getProperties().put("textures", new Property("textures", code));
		HeadUtils.setGameProfile(meta, profile);

		meta.setDisplayName(getHeadNameFromKey(textureKey));
		item.setItemMeta(meta);
		return item;
	}

	@SuppressWarnings("deprecation")
	public ItemStack getHead(EntityType eType, String textureKey){
		// If there is extra "texture metadata" (aka '|') we should return the custom skull
		if(textureKey != null){
			// Try successively smaller texture keys until we find one that exists
			int keyDataTagIdx=textureKey.lastIndexOf('|');
			while(keyDataTagIdx != -1 && !textures.containsKey(textureKey)){
				textureKey = textureKey.substring(0, keyDataTagIdx);
				keyDataTagIdx=textureKey.lastIndexOf('|');
			}
			// If this is a custom data head (still contains a '|') or eType is null AND the key exists, use it
			if((keyDataTagIdx != -1 || eType == null || eType == EntityType.UNKNOWN) && textures.containsKey(textureKey)){
				return makeTextureSkull(textureKey);
			}
		}
		if(eType == null) return null;
		// Otherwise, favor vanilla skulls
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

	public ItemStack getHead(Entity entity){
		if(entity.getType() == EntityType.PLAYER) return HeadUtils.getPlayerHead((OfflinePlayer)entity);
		String textureKey = TextureKeyLookup.getTextureKey(entity);
		if(grummEnabled && HeadUtils.hasGrummName(entity) && textures.containsKey(textureKey+"|GRUMM")) 
			return makeTextureSkull(textureKey+"|GRUMM");
		return getHead(entity.getType(), textureKey);
	}

	public ItemStack getHead(GameProfile profile){
		if(profile == null || profile.getName() == null) return null;
		String profileName = profile.getName();
		/*if(saveCustomLore){*/int idx = profileName.indexOf('>'); if(idx != -1) profileName = profileName.substring(0, idx);/*}*/
		if(textureExists(profileName)){//Refresh this EntityHead texture
			if(updateZombiePigmenHeads && profileName.startsWith("PIG_ZOMBIE")){
				profileName = profileName.replace("PIG_ZOMBIE", "ZOMBIFIED_PIGLIN");
			}
			return makeTextureSkull(profileName);
		}
		else{//Looks like a Player's Head
			OfflinePlayer p = profile.getId() == null ? null : pl.getServer().getOfflinePlayer(profile.getId());
			if(p != null && p.getName() != null){
				if(updateOldPlayerHeads || p.getName().equals(profileName)) return HeadUtils.getPlayerHead(p);
			}
			return HeadUtils.getPlayerHead(profile);
		}
	}

	public String getHeadName(GameProfile profile){
		if(profile == null || profile.getName() == null) return null;
		String profileName = profile.getName();
		/*if(saveCustomLore){*/int idx = profileName.indexOf('>'); if(idx != -1) profileName = profileName.substring(0, idx);/*}*/
		if(textureExists(profileName)){
			return getHeadNameFromKey(profileName);
		}
		else{//Looks like a Player's Head
			if(updateOldPlayerHeads && profile.getId() != null){
				OfflinePlayer p = pl.getServer().getOfflinePlayer(profile.getId());
				if(p != null) HeadUtils.getPlayerHeadName(p.getName());
			}
			return HeadUtils.getPlayerHeadName(profileName);
		}
	}
}