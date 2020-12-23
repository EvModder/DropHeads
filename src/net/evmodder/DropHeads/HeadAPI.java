package net.evmodder.DropHeads;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import me.arcaniax.hdb.api.DatabaseLoadEvent;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import net.evmodder.EvLib.FileIO;
import net.evmodder.EvLib.extras.HeadUtils;

public class HeadAPI {
	final private DropHeads pl;
	private HeadDatabaseAPI hdbAPI = null;
//	private int MAX_HDB_ID = -1;
	final boolean GRUM_ENABLED, SADDLES_ENABLED, HOLLOW_SKULLS_ENABLED;
	final boolean UPDATE_PLAYER_HEADS, UPDATE_ZOMBIE_PIGMEN_HEADS/*, SAVE_CUSTOM_LORE*/, SAVE_TYPE_IN_LORE, MAKE_UNSTACKABLE, PREFER_VANILLA_HEADS;
	final TreeMap<String, String> textures; // Key="ENTITY_NAME|DATA", Value="eyJ0ZXh0dXJl..."

	HeadAPI(){
		textures = new TreeMap<String, String>();
		pl = DropHeads.getPlugin();
		GRUM_ENABLED = pl.getConfig().getBoolean("drop-grumm-heads", true);
		SADDLES_ENABLED = pl.getConfig().getBoolean("drop-saddled-heads", true);
		HOLLOW_SKULLS_ENABLED = pl.getConfig().getBoolean("hollow-skeletal-skulls", false);
		UPDATE_PLAYER_HEADS = pl.getConfig().getBoolean("update-on-skin-change", true);
		boolean zombifiedPiglensExist = false;
		try{EntityType.valueOf("ZOMBIFIED_PIGLIN"); zombifiedPiglensExist = true;} catch(IllegalArgumentException ex){}
		UPDATE_ZOMBIE_PIGMEN_HEADS = zombifiedPiglensExist &&
				pl.getConfig().getBoolean("update-zombie-pigman-heads",
				pl.getConfig().getBoolean("update-zombie-pigmen-heads", true));
//		SAVE_CUSTOM_LORE = pl.getConfig().getBoolean("save-custom-lore", false);
		SAVE_TYPE_IN_LORE = pl.getConfig().getBoolean("show-head-type-in-lore", false);
		MAKE_UNSTACKABLE = pl.getConfig().getBoolean("make-heads-unstackable", false);
		PREFER_VANILLA_HEADS = pl.getConfig().getBoolean("prefer-vanilla-heads", true);

		String hardcodedList = FileIO.loadResource(pl, "head-textures.txt");
		loadTextures(hardcodedList, /*logMissingEntities=*/true, /*logUnknownEntities=*/false);
//		String localList = FileIO.loadFile("head-textures.txt", hardcodedList);  // This version does not preserve comments
		String localList = FileIO.loadFile("head-textures.txt", getClass().getResourceAsStream("/head-textures.txt"));
		loadTextures(localList, /*logMissingEntities=*/false, /*logUnknownEntities=*/true);

		//TODO: decide whether this feature is worth keeping
		if(pl.getConfig().getBoolean("update-textures", false) && hardcodedList.length() > localList.length()){
			long oldTextureTime = new File(FileIO.DIR+"/head-textures.txt").lastModified();
			long newTextureTime = 0;
			try{
				java.lang.reflect.Method getFileMethod = JavaPlugin.class.getDeclaredMethod("getFile");
				getFileMethod.setAccessible(true);
				newTextureTime = ((File)getFileMethod.invoke(pl)).lastModified();
			}
			catch(NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e1){}
			if(newTextureTime > oldTextureTime) FileIO.saveFile("head-textures.txt", hardcodedList);
		}

		// This could be optimized by passing 'simple-mob-heads-only' to loadTextures to skip adding any textures with '|'
		if(pl.getConfig().getBoolean("simple-mob-heads-only", false)){
			ArrayList<String> allKeys = new ArrayList<>(textures.keySet());
			for(String key : allKeys) if(key.indexOf('|') != -1) textures.remove(key);
		}

		boolean hdbInstalled = true;
		try{Class.forName("me.arcaniax.hdb.api.DatabaseLoadEvent");}
		catch(ClassNotFoundException ex){hdbInstalled = false;}

		if(hdbInstalled) pl.getServer().getPluginManager().registerEvents(new Listener(){
			@EventHandler public void onDatabaseLoad(DatabaseLoadEvent e){
				HandlerList.unregisterAll(this);
				hdbAPI = new HeadDatabaseAPI();
				/*MAX_HDB_ID = JunkUtils.binarySearch(
					id -> {
						try{return api.isHead(""+id);}
						catch(NullPointerException nullpointer){return false;}
					},
					0, Integer.MAX_VALUE
				);*/
			}
		}, pl);
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
				if(texture.replace("xxx", "").trim().isEmpty()) continue; //TODO: remove the xxx's

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
	public TreeMap<String, String> getTextures(){return textures;}//TODO: remove public (add CommandSpawnHead as friend?)
	public HeadDatabaseAPI getHeadDatabaseAPI(){return hdbAPI;}//TODO: prefer this not by public

	public String getHeadNameFromKey(String textureKey){
		// Attempt to parse out an EntityType
		EntityType eType;
		int i = textureKey.indexOf('|');
		String nameStr = (i == -1 ? textureKey : textureKey.substring(0, i)).toUpperCase();
		try{eType = EntityType.valueOf(nameStr);}
		catch(IllegalArgumentException ex){
			if(!textures.containsKey(textureKey)){ // If preloaded in textures, it is probably a mob from a different MC version
				pl.getLogger().warning("Unknown EntityType: "+nameStr+"!");
			}
			eType = null;// We will just use textureKey[0]
		}

		// Call the actual getNameFromKey()
		String entityName = TextureKeyLookup.getNameFromKey(/*eType, */textureKey);
		String headTypeName = eType == null ? "Head" : HeadUtils.getDroppedHeadTypeName(eType);
		return ChatColor.YELLOW + entityName + " " + headTypeName;
	}

	// TODO: Redo this part of the API (head naming)!
	public class HeadNameData{
		public String hdbId, textureKey;
		public OfflinePlayer player;
		public String entityName, headName, headTypeName;
	}
	public HeadNameData getHeadNameData(GameProfile profile){
		HeadNameData data = new HeadNameData();
		if(profile == null){
			data.textureKey = EntityType.PLAYER.name();  // This is considered a mob head
			data.headTypeName = HeadUtils.getDroppedHeadTypeName(EntityType.PLAYER);  // "Head"
			data.entityName = data.headName = TextureKeyLookup.getNameFromKey(EntityType.PLAYER.name());  // "Player"
			return data;
		}
		String fullProfileName = profile.getName();
		if(fullProfileName != null/* && SAVE_CUSTOM_LORE*/){
			int idx = fullProfileName.indexOf('>');
			if(idx != -1) profile = new GameProfile(profile.getId(), fullProfileName.substring(0, idx));
		}
		if(hdbAPI != null && (data.hdbId = hdbAPI.getItemID(HeadUtils.getPlayerHead(profile))) != null && hdbAPI.isHead(data.hdbId)){
			data.headTypeName = HeadUtils.getDroppedHeadTypeName(EntityType.UNKNOWN);  // "Head"
			data.entityName = TextureKeyLookup.getNameFromKey(EntityType.UNKNOWN.name());  // "Unknown"
			String hdbHeadName = hdbAPI.getItemHead(data.hdbId).getItemMeta().getDisplayName();
			int idx = hdbHeadName.lastIndexOf(' ');
			data.headName = idx == -1 ? hdbHeadName : hdbHeadName.substring(0, idx);
		}
		else if(profile.getId() != null && (data.player = pl.getServer().getOfflinePlayer(profile.getId())) != null && data.player.getName() != null){
			data.headTypeName = HeadUtils.getDroppedHeadTypeName(EntityType.PLAYER);  // "Head"
			data.entityName = TextureKeyLookup.getNameFromKey(EntityType.PLAYER.name());  // "Player"
			data.headName = UPDATE_PLAYER_HEADS || profile.getName() == null ? data.player.getName() : profile.getName();
		}
		else if(textures.containsKey(profile.getName())){
			data.textureKey = profile.getName();
			int idx = data.textureKey.indexOf('|');
			String eTypeName = (idx == -1 ? data.textureKey : data.textureKey.substring(0, idx)).toUpperCase();
			try{data.headTypeName = HeadUtils.getDroppedHeadTypeName(EntityType.valueOf(eTypeName));}
			catch(IllegalArgumentException ex){data.headTypeName = HeadUtils.getDroppedHeadTypeName(EntityType.UNKNOWN);}  // "Head"
			data.entityName = TextureKeyLookup.getNameFromKey(data.textureKey);
			data.headName = profile.getName();
		}
		else{
			data.headTypeName = HeadUtils.getDroppedHeadTypeName(EntityType.UNKNOWN);  // "Head"
			data.entityName = TextureKeyLookup.getNameFromKey(EntityType.UNKNOWN.name());  // "Unknown"
			data.headName = profile.getName() != null ? profile.getName() : data.entityName;
		}
		if(data.hdbId != null && !hdbAPI.isHead(data.hdbId)) data.hdbId = null;
		if(data.player != null && data.player.getName() == null) data.player = null;
		return data;
	}

	ItemStack makeTextureSkull(String textureKey/*, boolean saveTypeInLore, boolean unstackable*/){
		ItemStack item = new ItemStack(Material.PLAYER_HEAD);
		SkullMeta meta = (SkullMeta) item.getItemMeta();

		UUID uuid = UUID.nameUUIDFromBytes(textureKey.getBytes());// Stable UUID for this textureKey
		GameProfile profile = new GameProfile(uuid, textureKey);// Initialized with UUID and name
		String code = textures.get(textureKey);
		if(code != null) profile.getProperties().put("textures", new Property("textures", code));
		if(MAKE_UNSTACKABLE) profile.getProperties().put("random_uuid", new Property("random_uuid", UUID.randomUUID().toString()));
		HeadUtils.setGameProfile(meta, profile);

		meta.setDisplayName(getHeadNameFromKey(textureKey));
		if(SAVE_TYPE_IN_LORE){
			int i = textureKey.indexOf('|');
			String entityName = (i == -1 ? textureKey : textureKey.substring(0, i)).toLowerCase();
			meta.setLore(Arrays.asList(ChatColor.DARK_GRAY + "mob:" + entityName));
		}
		item.setItemMeta(meta);
		return item;
	}

	@SuppressWarnings("deprecation")
	ItemStack makeSkull_wrapper(EntityType eType){
		ItemStack head = HeadUtils.makeSkull(eType);
		if(SAVE_TYPE_IN_LORE){
			SkullMeta meta = (SkullMeta) head.getItemMeta();
			meta.setLore(Arrays.asList(ChatColor.DARK_GRAY + "mob:" + eType.name().toLowerCase()));
			head.setItemMeta(meta);
		}
		if(MAKE_UNSTACKABLE){
			SkullMeta meta = (SkullMeta) head.getItemMeta();
			GameProfile profile = HeadUtils.getGameProfile(meta);
			profile.getProperties().put("random_uuid", new Property("random_uuid", UUID.randomUUID().toString()));
			HeadUtils.setGameProfile(meta, profile);
			head.setItemMeta(meta);
		}
		return head;
	}
	public ItemStack makeSkull_wrapper(String textureCode, String headName){
		ItemStack head = HeadUtils.makeSkull(textureCode, headName);
		if(SAVE_TYPE_IN_LORE){
			ItemMeta meta = head.getItemMeta();
			meta.setLore(Arrays.asList(ChatColor.DARK_GRAY+"code:"+textureCode));
			head.setItemMeta(meta);
		}
		if(MAKE_UNSTACKABLE){
			SkullMeta meta = (SkullMeta) head.getItemMeta();
			GameProfile profile = HeadUtils.getGameProfile(meta);
			profile.getProperties().put("random_uuid", new Property("random_uuid", UUID.randomUUID().toString()));
			HeadUtils.setGameProfile(meta, profile);
			head.setItemMeta(meta);
		}
		return head;
	}
	public ItemStack getPlayerHead_wrapper(OfflinePlayer player){
		ItemStack head = HeadUtils.getPlayerHead(player);
		if(SAVE_TYPE_IN_LORE){
			SkullMeta meta = (SkullMeta) head.getItemMeta();
			meta.setLore(Arrays.asList(ChatColor.DARK_GRAY + "player:" + player.getName()));
			head.setItemMeta(meta);
		}
		if(MAKE_UNSTACKABLE){
			SkullMeta meta = (SkullMeta) head.getItemMeta();
			GameProfile profile = HeadUtils.getGameProfile(meta);
			profile.getProperties().put("random_uuid", new Property("random_uuid", UUID.randomUUID().toString()));
			HeadUtils.setGameProfile(meta, profile);
			head.setItemMeta(meta);
		}
		return head;
	}
	ItemStack getPlayerHead_wrapper(GameProfile profile){
		ItemStack head = HeadUtils.getPlayerHead(profile);
		if(SAVE_TYPE_IN_LORE){
			SkullMeta meta = (SkullMeta) head.getItemMeta();
			meta.setLore(Arrays.asList(ChatColor.DARK_GRAY + "player:" + profile.getName()));
			head.setItemMeta(meta);
		}
		if(MAKE_UNSTACKABLE){
			profile.getProperties().put("random_uuid", new Property("random_uuid", UUID.randomUUID().toString()));
			SkullMeta meta = (SkullMeta) head.getItemMeta();
			HeadUtils.setGameProfile(meta, profile);
			head.setItemMeta(meta);
		}
		return head;
	}
	public ItemStack getItemHead_wrapper(String id){
		ItemStack hdbHead = hdbAPI.getItemHead(id);
		GameProfile profile = HeadUtils.getGameProfile((SkullMeta)hdbHead.getItemMeta());
		ItemStack head = HeadUtils.getPlayerHead(profile);
		SkullMeta meta = (SkullMeta)head.getItemMeta();
		meta.setDisplayName(hdbHead.getItemMeta().getDisplayName());
		if(SAVE_TYPE_IN_LORE){
			meta.setLore(Arrays.asList(ChatColor.DARK_GRAY + "hdb:" + id));
		}
		if(MAKE_UNSTACKABLE){
			profile.getProperties().put("random_uuid", new Property("random_uuid", UUID.randomUUID().toString()));
			HeadUtils.setGameProfile(meta, profile);
		}
		head.setItemMeta(meta);
		return head;
	}

	public ItemStack getHead(EntityType eType, String textureKey/*, boolean saveTypeInLore, boolean unstackable*/){
		// If there is extra "texture metadata" (aka '|') we should return the custom skull
		if(textureKey != null){
			// Try successively smaller texture keys until we find one that exists
			int keyDataTagIdx=textureKey.lastIndexOf('|');
			while(keyDataTagIdx != -1 && !textures.containsKey(textureKey)){
				textureKey = textureKey.substring(0, keyDataTagIdx);
				keyDataTagIdx=textureKey.lastIndexOf('|');
			}
			// If this is a custom data head (still contains a '|') or eType is null AND the key exists, use it
			boolean hasCustomData = textureKey.replace("|HOLLOW", "").indexOf('|') != -1;
			if((hasCustomData || eType == null || eType == EntityType.UNKNOWN || !PREFER_VANILLA_HEADS) && textures.containsKey(textureKey)){
				return makeTextureSkull(textureKey/*, saveTypeInLore*/);
			}
		}
		if(eType == null) return null;
		// Otherwise, favor vanilla skulls
		if(!PREFER_VANILLA_HEADS && eType != EntityType.PLAYER) return null;
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
				return makeSkull_wrapper(eType);
			}
	}

	public ItemStack getHead(Entity entity/*, boolean saveTypeInLore, boolean unstackable*/){
		if(entity.getType() == EntityType.PLAYER){
			return getPlayerHead_wrapper((OfflinePlayer)entity);
		}
		String textureKey = TextureKeyLookup.getTextureKey(entity);
		if(!SADDLES_ENABLED && textureKey.endsWith("|SADDLED")) textureKey = textureKey.substring(0, textureKey.length()-8);
		if(HOLLOW_SKULLS_ENABLED && JunkUtils.isSkeletal(entity.getType())) textureKey += "|HOLLOW";
		if(GRUM_ENABLED && HeadUtils.hasGrummName(entity)) textureKey += "|GRUMM";
		return getHead(entity.getType(), textureKey);
	}

	public ItemStack getHead(GameProfile profile/*, boolean saveTypeInLore, boolean unstackable*/){
		if(profile == null) return null;
		String profileName = profile.getName();
		if(profileName != null){
			/*if(SAVE_CUSTOM_LORE){*/int idx = profileName.indexOf('>'); if(idx != -1) profileName = profileName.substring(0, idx);/*}*/
			if(textures.containsKey(profileName)){//Refresh this EntityHead texture
				if(UPDATE_ZOMBIE_PIGMEN_HEADS && profileName.startsWith("PIG_ZOMBIE")){
					profileName = /*profileName.replace("PIG_ZOMBIE", */"ZOMBIFIED_PIGLIN"/*)*/;
				}
				if(profileName.equals("OCELOT|WILD_OCELOT")) profileName = "OCELOT";
				return makeTextureSkull(profileName);
			}
		}
		if(UPDATE_PLAYER_HEADS && profile.getId() != null){
			OfflinePlayer p = pl.getServer().getOfflinePlayer(profile.getId());
			if(p != null && p.getName() != null) return getPlayerHead_wrapper(p);
		}
		if(hdbAPI != null){
			String id = hdbAPI.getItemID(HeadUtils.getPlayerHead(profile));
			if(id != null && hdbAPI.isHead(id)) return getItemHead_wrapper(id);
		}
		return getPlayerHead_wrapper(profile);
	}
}