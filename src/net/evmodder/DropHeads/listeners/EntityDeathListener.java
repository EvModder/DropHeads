package net.evmodder.DropHeads.listeners;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Rotatable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.JunkUtils;
import net.evmodder.DropHeads.TextureKeyLookup;
import net.evmodder.DropHeads.events.EntityBeheadEvent;
import net.evmodder.DropHeads.events.HeadRollEvent;
import net.evmodder.EvLib.EvUtils;
import net.evmodder.EvLib.FileIO;
import net.evmodder.EvLib.extras.TellrawUtils.Component;
import net.evmodder.EvLib.extras.TellrawUtils.SelectorComponent;
import net.evmodder.EvLib.extras.TellrawUtils.ListComponent;
import net.evmodder.EvLib.extras.HeadUtils;
import net.evmodder.EvLib.extras.TellrawUtils;
import net.evmodder.EvLib.extras.TextUtils;

public class EntityDeathListener implements Listener{
	public enum AnnounceMode {GLOBAL, LOCAL, DIRECT, OFF};
	final AnnounceMode DEFAULT_ANNOUNCE;
	final EventPriority PRIORITY;
	enum DropMode {EVENT, SPAWN,
		PLACE, PLACE_IF_PERM/*DEPRECATED*/, PLACE_BY_KILLER, PLACE_BY_VICTIM,
		GIVE, GIVE_NEVER_DROP/*DEPRECATED*/};
	final ArrayList<DropMode> DROP_MODES;//TODO: final HashMap<EntityType, DropMode> mobDropModes

	final boolean ALLOW_NON_PLAYER_KILLS, ALLOW_INDIRECT_KILLS, ALLOW_PROJECTILE_KILLS;
	final boolean PLAYER_HEADS_ONLY, CHARGED_CREEPER_DROPS, REPLACE_DEATH_MESSAGE, VANILLA_WSKELE_HANDLING, VANILLA_WSKELE_LOOTING;
	final double DEFAULT_CHANCE, LOOTING_ADD, LOOTING_MULT;
	final boolean DEBUG_MODE, LOG_PLAYER_BEHEAD, LOG_MOB_BEHEAD;
	final String LOG_MOB_FORMAT, LOG_PLAYER_FORMAT;
	final String[] MSG_BEHEAD, MSH_BEHEAD_BY, MSH_BEHEAD_BY_WITH, MSH_BEHEAD_BY_WITH_NAMED;
	final long INDIRECT_KILL_THRESHOLD_MILLIS = 30*1000;//TODO: move to config
	final boolean USE_PLAYER_DISPLAYNAMES = false;//TODO: move to config, when possible
	final boolean CROSS_DIMENSIONAL_BROADCAST = true;//TODO: move to config
	final int LOCAL_RANGE = 200;//TODO: move to config
	public final int JSON_LIMIT = 15000;//TODO: move to config
	final BlockFace[] possibleHeadRotations = new BlockFace[]{
			BlockFace.NORTH, BlockFace.NORTH_EAST, BlockFace.NORTH_WEST,
			BlockFace.SOUTH, BlockFace.SOUTH_EAST, BlockFace.SOUTH_WEST,
			BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_NORTH_WEST,
			BlockFace.SOUTH_SOUTH_EAST, BlockFace.SOUTH_SOUTH_WEST
	};

	final DropHeads pl;
	final Random rand;
	final HashSet<Material> headOverwriteBlocks;
	public final Set<Material> mustUseTools; // TODO: remove public
	final HashSet<EntityType> noLootingEffectMobs;
	final HashMap<EntityType, Double> mobChances;
	final HashMap<EntityType, HashMap<String, Double>> subtypeMobChances;
	final HashMap<EntityType, AnnounceMode> mobAnnounceModes;
	final HashMap<Material, Double> toolBonuses;
	final HashMap<String, Double> droprateMultiplierPerms;
	final TreeMap<Long, Double> timeAliveBonuses;
	final HashSet<UUID> explodingChargedCreepers, recentlyBeheadedEntities;

	public EntityDeathListener(){
		pl = DropHeads.getPlugin();
		rand = new Random();
		ALLOW_NON_PLAYER_KILLS = pl.getConfig().getBoolean("drop-for-nonplayer-kills", !pl.getConfig().getBoolean("player-kills-only", true));
		ALLOW_INDIRECT_KILLS = pl.getConfig().getBoolean("drop-for-indirect-kills", false);
		ALLOW_PROJECTILE_KILLS = pl.getConfig().getBoolean("drop-for-ranged-kills", false);
		PLAYER_HEADS_ONLY = pl.getConfig().getBoolean("player-heads-only", false);
		CHARGED_CREEPER_DROPS = pl.getConfig().getBoolean("charged-creeper-drops", true);
		VANILLA_WSKELE_HANDLING = pl.getConfig().getBoolean("vanilla-wither-skeleton-skulls", false);
		VANILLA_WSKELE_LOOTING = pl.getConfig().getBoolean("vanilla-wither-skeleton-looting-behavior", false);
		LOOTING_ADD = pl.getConfig().getDouble("looting-addition", 0D);
		LOOTING_MULT = pl.getConfig().getDouble("looting-multiplier", pl.getConfig().getDouble("looting-mutliplier", 1.01D));
		if(LOOTING_ADD >= 1) pl.getLogger().warning("looting-addition is set to 1.0 or greater, this means heads will ALWAYS drop when looting is used!");
		if(LOOTING_MULT < 1) pl.getLogger().warning("looting-multiplier is set below 1.0, this means looting will DECREASE the chance of head drops!");
		REPLACE_DEATH_MESSAGE = pl.getConfig().getBoolean("behead-announcement-replaces-player-death-message",
				pl.getConfig().getBoolean("behead-announcement-replaces-death-message", true));
		PRIORITY = JunkUtils.parseEnumOrDefault(pl.getConfig().getString("death-listener-priority", "LOW"), EventPriority.LOW);
		DEBUG_MODE = pl.getConfig().getBoolean("debug-messages", true);
		final boolean ENABLE_LOG = pl.getConfig().getBoolean("log.enable", false);
		LOG_MOB_BEHEAD = ENABLE_LOG && pl.getConfig().getBoolean("log.log-mob-behead", false);
		LOG_PLAYER_BEHEAD = ENABLE_LOG && pl.getConfig().getBoolean("log.log-player-behead", false);
		LOG_MOB_FORMAT = LOG_MOB_BEHEAD ? pl.getConfig().getString("log.log-mob-behead-format",
				"${TIMESTAMP},mob decapitated,${VICTIM},${KILLER},${ITEM}") : null;
		LOG_PLAYER_FORMAT = LOG_PLAYER_BEHEAD ? pl.getConfig().getString("log.log-player-behead-format",
				"${TIMESTAMP},player decapitated,${VICTIM},${KILLER},${ITEM}") : null;

		MSG_BEHEAD = JunkUtils.parseStringOrStringList(pl.getConfig(), "message-beheaded", "${VICTIM} was beheaded");
		MSH_BEHEAD_BY = JunkUtils.parseStringOrStringList(pl.getConfig(), "message-beheaded-by-entity", "${VICTIM}&r was beheaded by ${KILLER}&r");
		MSH_BEHEAD_BY_WITH = JunkUtils.parseStringOrStringList(pl.getConfig(), "message-beheaded-by-entity-with-item",
				"${VICTIM}&r was beheaded by ${KILLER}&r using ${ITEM}&r");
		MSH_BEHEAD_BY_WITH_NAMED = JunkUtils.parseStringOrStringList(pl.getConfig(), "message-beheaded-by-entity-with-item-named",
				"${VICTIM}&r was beheaded by ${KILLER}&r using ${ITEM}&r");

//		USE_PLAYER_DISPLAYNAMES = pl.getConfig().getBoolean("message-beheaded-use-player-displaynames", true);//TODO

		DROP_MODES = new ArrayList<>();
		if(pl.getConfig().contains("head-item-drop-mode"))
		for(String dropModeName : pl.getConfig().getStringList("head-item-drop-mode")){
			try{DROP_MODES.add(DropMode.valueOf(dropModeName.toUpperCase()));}
			catch(IllegalArgumentException ex){pl.getLogger().severe("Unknown head DropMode: "+dropModeName);}
		}
		if(DROP_MODES.isEmpty()) DROP_MODES.add(DropMode.EVENT);

		headOverwriteBlocks = new HashSet<>();
		if(pl.getConfig().contains("head-place-overwrite-blocks"))
		for(String matName : pl.getConfig().getStringList("head-place-overwrite-blocks")){
			try{headOverwriteBlocks.add(Material.valueOf(matName.toUpperCase()));}
			catch(IllegalArgumentException ex){pl.getLogger().severe("Unknown material in 'head-place-overwrite-blocks': "+matName);}
		}
		else headOverwriteBlocks.add(Material.AIR);

		mobAnnounceModes = new HashMap<>();
		mobAnnounceModes.put(EntityType.UNKNOWN, JunkUtils.parseAnnounceMode(
				pl.getConfig().getString("behead-announcement-mobs", "LOCAL"), AnnounceMode.LOCAL));
		mobAnnounceModes.put(EntityType.PLAYER, JunkUtils.parseAnnounceMode(
				pl.getConfig().getString("behead-announcement-players", "GLOBAL"), AnnounceMode.GLOBAL));
		ConfigurationSection announceModes = pl.getConfig().getConfigurationSection("behead-announcement");
		AnnounceMode tempDefaultAnnounce = mobAnnounceModes.get(EntityType.UNKNOWN);
		if(announceModes != null) for(String mobName : announceModes.getKeys(false)){
			try{
				EntityType eType = EntityType.valueOf(mobName.toUpperCase().replace("DEFAULT", "UNKNOWN"));
				mobAnnounceModes.put(eType, JunkUtils.parseAnnounceMode(announceModes.getString(mobName), tempDefaultAnnounce));
			}
			catch(IllegalArgumentException ex){pl.getLogger().severe("Unknown entity type in 'behead-announce': "+mobName);}
		}
		DEFAULT_ANNOUNCE = mobAnnounceModes.get(EntityType.UNKNOWN);

		if(pl.getConfig().getBoolean("must-use-axe")){
			mustUseTools = Arrays.stream(Material.values()).filter(mat -> mat.name().endsWith("_AXE")).collect(Collectors.toSet());
		}
		else{
			(mustUseTools = pl.getConfig().getStringList("must-use").stream().map(toolName -> {
				if(toolName.isEmpty()) return null;
				Material mat = Material.getMaterial(toolName.toUpperCase());
				if(mat == null) pl.getLogger().warning("Unknown Tool \""+toolName+"\"!");
				return mat;
			}).collect(Collectors.toSet())).remove(null);
		}

		toolBonuses = new HashMap<Material, Double>();
		ConfigurationSection specificToolModifiers = pl.getConfig().getConfigurationSection("specific-tool-modifiers");
		if(specificToolModifiers != null) for(String toolName : specificToolModifiers.getKeys(false)){
			Material mat = Material.getMaterial(toolName.toUpperCase());
			if(mat != null) toolBonuses.put(mat, specificToolModifiers.getDouble(toolName));
		}

		timeAliveBonuses = new TreeMap<>();
		timeAliveBonuses.put(-1L, 0D); // Ensure there is always a lower entry, and it defaults to 0
		ConfigurationSection specificTimeAliveModifiers = pl.getConfig().getConfigurationSection("time-alive-modifiers");
		if(specificTimeAliveModifiers != null) for(String formattedTime : specificTimeAliveModifiers.getKeys(false)){
			try{
				long time = TextUtils.parseTime(formattedTime);
				timeAliveBonuses.put(time, specificTimeAliveModifiers.getDouble(formattedTime));
			}
			catch(NumberFormatException ex){pl.getLogger().severe("Error parsing time string: \""+formattedTime+'"');}
		}

		droprateMultiplierPerms = new HashMap<String, Double>();
		ConfigurationSection customDropratePerms = pl.getConfig().getConfigurationSection("custom-droprate-multiplier-permissions");
		if(customDropratePerms != null) for(String perm : customDropratePerms.getKeys(false)){
			try{droprateMultiplierPerms.put(perm, customDropratePerms.getDouble(perm));}
			catch(NumberFormatException ex){pl.getLogger().severe("Error parsing droprate multiplier for perm: \""+perm+'"');}
		}

		//Load individual mobs' drop chances
		mobChances = new HashMap<EntityType, Double>();
		subtypeMobChances = new HashMap<EntityType, HashMap<String, Double>>();
		noLootingEffectMobs = new HashSet<EntityType>();
		//double chanceForUnknown = 0D;
		if(PLAYER_HEADS_ONLY){
			pl.getServer().getPluginManager().registerEvent(PlayerDeathEvent.class, this, PRIORITY, new DeathEventExecutor(), pl);
			DEFAULT_CHANCE = 0D;
			String defaultChances = FileIO.loadResource(pl, "head-drop-rates.txt");
			String chances = FileIO.loadFile("head-drop-rates.txt", defaultChances);
			for(final String line : chances.split("\n")){
				final String[] parts = line.split(":");
				if(parts.length < 2 || !parts[0].trim().toUpperCase().equals("PLAYER")) continue;
				final String value = parts[1].trim();
				try{
					double dropChance = Double.parseDouble(value);
					if(dropChance < 0D || dropChance > 1D){
						pl.getLogger().warning("Invalid value: "+value);
						pl.getLogger().warning("Drop chance should be a decimal between 0 and 1");
						if(dropChance > 1D && dropChance <= 100D) dropChance /= 100D;
						else continue;
					}
					mobChances.put(EntityType.PLAYER, dropChance);
				}
				catch(NumberFormatException ex){pl.getLogger().severe("Invalid value: "+value);}
			}
		}
		else{
			String defaultChances = FileIO.loadResource(pl, "head-drop-rates.txt");
			HashSet<String> defaultConfigMobs = new HashSet<>();
			for(String line2 : defaultChances.split("\n")){
				String[] parts2 = line2.replace(" ", "").replace("\t", "").toUpperCase().split(":");
				if(parts2.length < 2) continue;
				defaultConfigMobs.add(parts2[0]);
			}
			String chances = FileIO.loadFile("head-drop-rates.txt", defaultChances);
			for(String line : chances.split("\n")){
				String[] parts = line.replace(" ", "").replace("\t", "").toUpperCase().split(":");
				if(parts.length < 2) continue;
				int dataTagSep = parts[0].indexOf('|');
				String eName = dataTagSep == -1 ? parts[0] : parts[0].substring(0, dataTagSep);
				try{
					double dropChance = Double.parseDouble(parts[1]);
					if(dropChance < 0D || dropChance > 1D){
						pl.getLogger().warning("Invalid value: "+parts[1]);
						pl.getLogger().warning("Drop chance should be a decimal between 0 and 1");
						if(dropChance > 1D && dropChance <= 100D) dropChance /= 100D;
						else continue;
					}
					EntityType eType = EntityType.valueOf(eName.replace("DEFAULT", "UNKNOWN"));
					if(parts.length > 2 && parts[2].equals("NOLOOTING")) noLootingEffectMobs.add(eType);
					if(dataTagSep == -1) mobChances.put(eType, dropChance);
					else if(pl.getAPI().textureExists(parts[0])){
						HashMap<String, Double> eTypeChances = subtypeMobChances.getOrDefault(eType, new HashMap<String, Double>());
						eTypeChances.put(parts[0], dropChance);
						subtypeMobChances.put(eType, eTypeChances);
					}
					else{
						pl.getLogger().severe("Unknown entity sub-type: "+parts[0]);
					}
				}
				catch(NumberFormatException ex){pl.getLogger().severe("Invalid value: "+parts[1]);}
				catch(IllegalArgumentException ex){
					// Only throw an error for mobs that aren't defined in the default config (which may be from future/past versions)
					if(!defaultConfigMobs.contains(eName)) pl.getLogger().severe("Unknown entity type: "+eName);
				}
			}
			if(VANILLA_WSKELE_HANDLING && mobChances.getOrDefault(EntityType.WITHER_SKELETON, 0.025D) != 0.025D){
				pl.getLogger().warning("Wither Skeleton Skull drop chance has been modified in 'head-drop-rates.txt', "
						+ "but this value will be ignored because 'vanilla-wither-skeleton-skulls' is set to true.");
			}
			// No need storing 0-chance mobs if the default drop chance is 0
			DEFAULT_CHANCE = mobChances.getOrDefault(EntityType.UNKNOWN, 0D);
			if(DEFAULT_CHANCE == 0D) mobChances.entrySet().removeIf(entry -> entry.getValue() == 0D);

			boolean entityHeads = DEFAULT_CHANCE > 0D || mobChances.entrySet().stream().anyMatch(  // All non-Player living entities
					entry -> entry.getKey().isAlive() && entry.getKey() != EntityType.PLAYER && entry.getValue() > 0D);
			if(entityHeads){
				pl.getServer().getPluginManager().registerEvent(EntityDeathEvent.class, this, PRIORITY, new DeathEventExecutor(), pl);
			}
			else if(mobChances.getOrDefault(EntityType.PLAYER, 0D) > 0D){
				pl.getServer().getPluginManager().registerEvent(PlayerDeathEvent.class, this, PRIORITY, new DeathEventExecutor(), pl);
			}
			boolean nonLivingVehicleHeads = DEFAULT_CHANCE > 0D || mobChances.entrySet().stream().anyMatch(  // Boat, Minecart
					entry -> !entry.getKey().isAlive() && entry.getValue() > 0D &&
					entry.getKey().getEntityClass() != null && Vehicle.class.isAssignableFrom(entry.getKey().getEntityClass()));
			if(nonLivingVehicleHeads){
				pl.getServer().getPluginManager().registerEvent(VehicleDestroyEvent.class, this, PRIORITY, new DeathEventExecutor(), pl);
			}
			boolean nonLivingHangingHeads = DEFAULT_CHANCE > 0D || mobChances.entrySet().stream().anyMatch(  // Painting, LeashHitch, ItemFrame
					entry -> !entry.getKey().isAlive() && entry.getValue() > 0D &&
					entry.getKey().getEntityClass() != null && Hanging.class.isAssignableFrom(entry.getKey().getEntityClass()));
			if(nonLivingHangingHeads){
				pl.getServer().getPluginManager().registerEvent(HangingBreakByEntityEvent.class, this, PRIORITY, new DeathEventExecutor(), pl);
			}
		}  // if(!PLAYER_HEADS_ONLY)
		explodingChargedCreepers = new HashSet<UUID>();
		recentlyBeheadedEntities = new HashSet<UUID>();

		if(REPLACE_DEATH_MESSAGE && PRIORITY != EventPriority.MONITOR){
			EventPriority replacePriority = (PRIORITY == EventPriority.HIGHEST ? EventPriority.MONITOR : EventPriority.HIGHEST);
			pl.getServer().getPluginManager().registerEvent(PlayerDeathEvent.class, this, replacePriority, new EventExecutor(){
				@Override public void execute(Listener listener, Event originalEvent){
					if(originalEvent instanceof PlayerDeathEvent == false) return;
					PlayerDeathEvent evt = (PlayerDeathEvent) originalEvent;
					if(recentlyBeheadedEntities.remove(evt.getEntity().getUniqueId())) evt.setDeathMessage("");
				}
			}, pl);
		}
		// Dynamically add all the children perms of "dropheads.alywaysbehead.<entity>"
		Permission alwaysBeheadPerm = pl.getServer().getPluginManager().getPermission("dropheads.alwaysbehead");
		if(alwaysBeheadPerm != null) try{
			for(EntityType entity : EntityType.values()){
				Permission alwaysBeheadPermForEntity = new Permission(
						alwaysBeheadPerm.getName()+"."+entity.name().toLowerCase(),
						"This entity will get a head 100% of the time when killing a "+entity.name().toLowerCase(),
						PermissionDefault.FALSE);
				alwaysBeheadPermForEntity.addParent(alwaysBeheadPerm, true);
				pl.getServer().getPluginManager().addPermission(alwaysBeheadPermForEntity);
			}
			alwaysBeheadPerm.recalculatePermissibles();
		}
		catch(IllegalArgumentException ex){/*The permissions are already defined; perhaps this is just a plugin or server reload*/}
	}

	public double getRawDropChance(Entity e){
		HashMap<String, Double> eSubtypeChances = subtypeMobChances.get(e.getType());
		if(eSubtypeChances != null){
			String textureKey = TextureKeyLookup.getTextureKey(e);
			int keyDataTagIdx = textureKey.lastIndexOf('|');
			Double subtypeChance = null;
			while(keyDataTagIdx != -1 && (subtypeChance=eSubtypeChances.get(textureKey)) == null){
				textureKey = textureKey.substring(0, keyDataTagIdx);
				keyDataTagIdx = textureKey.lastIndexOf('|');
			}
			if(subtypeChance != null) return subtypeChance;
		}
		return mobChances.getOrDefault(e.getType(), DEFAULT_CHANCE);
	}
	public double getTimeAliveBonus(Entity e){
		long millisecondsLived = e.getTicksLived()*50L;
		return timeAliveBonuses.floorEntry(millisecondsLived).getValue();
	}
	ItemStack getWeaponFromKiller(Entity killer){
		return killer != null ?
					killer instanceof LivingEntity ? ((LivingEntity)killer).getEquipment().getItemInMainHand() :
					killer instanceof Projectile && killer.hasMetadata("ShotUsing") ? (ItemStack)killer.getMetadata("ShotUsing").get(0).value() :
					null
				: null;
	}
	public double getPermsBasedDropRateModifier(Permissible killer){
		if(killer == null) return 1D;
		return droprateMultiplierPerms.entrySet().stream().parallel()
				.filter(e -> killer.hasPermission(e.getKey()))
				.map(e -> e.getValue())
				.reduce(1D, (a, b) -> a * b);
	}
	void sendTellraw(String target, String message){
		pl.getServer().dispatchCommand(pl.getServer().getConsoleSender(), "minecraft:tellraw "+target+" "+message);
	}
	void attemptHeadDropEvent(Entity entity, Event evt, Entity killer, ItemStack weapon){
		ItemStack headItem = pl.getAPI().getHead(entity);
		EntityBeheadEvent beheadEvent = new EntityBeheadEvent(killer, entity, evt, headItem);
		pl.getServer().getPluginManager().callEvent(beheadEvent);
		if(beheadEvent.isCancelled()) return;

		for(DropMode mode : DROP_MODES){
			if(headItem == null) break;
			switch(mode){
				case EVENT:
					if(evt instanceof EntityDeathEvent) ((EntityDeathEvent)evt).getDrops().add(headItem);
					else entity.getWorld().dropItemNaturally(entity.getLocation(), headItem);
					headItem = null;
					break;
				case PLACE_IF_PERM:
				case PLACE_BY_KILLER:
				case PLACE_BY_VICTIM:
				case PLACE:
					Block headBlock = EvUtils.getClosestBlock(entity.getLocation(), 5, b -> headOverwriteBlocks.contains(b.getType())).getBlock();
					BlockState state = headBlock.getState();
					state.setType(headItem.getType());
					Vector facingVector = entity.getLocation().getDirection(); facingVector.setY(0);  // loc.setPitch(0F)
					Rotatable data = (Rotatable)headBlock.getBlockData();
					data.setRotation(JunkUtils.getClosestBlockFace(facingVector, possibleHeadRotations).getOppositeFace());
					state.setBlockData(data);
					if(headItem.getType() == Material.PLAYER_HEAD){
						HeadUtils.setGameProfile((Skull)state, HeadUtils.getGameProfile((SkullMeta)headItem.getItemMeta()));
					}
					if(mode != DropMode.PLACE){
						Entity entityToCheck = (killer == null ||
								(mode == DropMode.PLACE_BY_VICTIM && (entity instanceof Player || killer instanceof Player == false)))
								? entity : killer;
						Event testPermsEvent;
						if(entityToCheck instanceof Player){
							testPermsEvent = new BlockPlaceEvent(headBlock, state,
								headBlock.getRelative(BlockFace.DOWN), headItem, (Player)entityToCheck, /*canBuild=*/true, EquipmentSlot.HAND);
						}
						else{
							testPermsEvent = new EntityBlockFormEvent(entityToCheck, headBlock, state);
						}
						pl.getServer().getPluginManager().callEvent(testPermsEvent);
						if(((Cancellable)testPermsEvent).isCancelled()){
							pl.getLogger().info("Head placement failed, permission-lacking player: "+entityToCheck.getName());
							break;
						}
					}
					state.update(/*force=*/true);
					headItem = null;
					break;
				case GIVE_NEVER_DROP:  // Deprecated
				case GIVE:
					headItem = JunkUtils.giveItemToEntity(entity, headItem);
					break;
				case SPAWN:
					entity.getWorld().dropItemNaturally(entity.getLocation(), headItem);
					headItem = null;
					break;
			}//switch(mode)
		}//for(DROP_MODES)
		recentlyBeheadedEntities.add(entity.getUniqueId());

		ListComponent message = new ListComponent();
		Component killerComp = null, itemComp = null;
		if(killer != null){
			killerComp = new SelectorComponent(killer.getUniqueId(), USE_PLAYER_DISPLAYNAMES);
			if(weapon != null && weapon.getType() != Material.AIR){
				itemComp = JunkUtils.getMurderItemComponent(weapon, JSON_LIMIT);
			}
			if(killer instanceof Projectile){
				if(weapon == null) itemComp = new SelectorComponent(killer.getUniqueId(), USE_PLAYER_DISPLAYNAMES);
				ProjectileSource shooter = ((Projectile)killer).getShooter();
				if(shooter instanceof Entity) killerComp = new SelectorComponent(((Entity)shooter).getUniqueId(), USE_PLAYER_DISPLAYNAMES);
				else if(shooter instanceof BlockProjectileSource){
					killerComp = TellrawUtils.getLocalizedDisplayName(((BlockProjectileSource)shooter).getBlock().getState());
				}
			}
			if(itemComp != null){
				boolean hasCustomName = weapon.hasItemMeta() && weapon.getItemMeta().hasDisplayName();
				message.addComponent(hasCustomName
						? MSH_BEHEAD_BY_WITH_NAMED[rand.nextInt(MSH_BEHEAD_BY_WITH_NAMED.length)]
						: MSH_BEHEAD_BY_WITH[rand.nextInt(MSH_BEHEAD_BY_WITH.length)]);
				message.replaceRawDisplayTextWithComponent("${ITEM}", itemComp);
			}
			else message.addComponent(MSH_BEHEAD_BY[rand.nextInt(MSH_BEHEAD_BY.length)]);
			//if(USE_PLAYER_DISPLAYNAMES) message.replaceRawTextWithComponent("${KILLER}", ...);
			message.replaceRawDisplayTextWithComponent("${KILLER}", killerComp);
		}
		else message.addComponent(MSG_BEHEAD[rand.nextInt(MSG_BEHEAD.length)]);
		Component victimComp = new SelectorComponent(entity.getUniqueId(), USE_PLAYER_DISPLAYNAMES);
		message.replaceRawDisplayTextWithComponent("${VICTIM}", victimComp);

		if(!message.toPlainText().replaceAll(" ", "").isEmpty()){
			if(DEBUG_MODE) pl.getLogger().info(/*"Tellraw message: "+*/message.toPlainText());

			AnnounceMode mode = mobAnnounceModes.getOrDefault(entity.getType(), DEFAULT_ANNOUNCE);
			if(mode != AnnounceMode.OFF && mode != AnnounceMode.DIRECT && (
				killer.hasPermission("dropheads.silentbehead") ||
				(killer.hasPermission("dropheads.silentbehead.invisible")
					&& killer instanceof LivingEntity && ((LivingEntity)killer).hasPotionEffect(PotionEffectType.INVISIBILITY)
				) ||
				(killer.hasPermission("dropheads.silentbehead.vanished")
					&& killer instanceof Player && JunkUtils.isVanished((Player)killer)
				)
			)) mode = AnnounceMode.DIRECT;
			switch(mode){
				case GLOBAL:
					if(entity instanceof Player && REPLACE_DEATH_MESSAGE && evt != null && PRIORITY != EventPriority.MONITOR){
						((PlayerDeathEvent)evt).setDeathMessage(message.toPlainText());  // is cleared later
					}
					sendTellraw("@a", message.toString());
					break;
				case LOCAL:
					for(Player p : EvUtils.getNearbyPlayers(entity.getLocation(), LOCAL_RANGE, CROSS_DIMENSIONAL_BROADCAST)){
						sendTellraw(p.getName(), message.toString());
					}
					break;
				case DIRECT:
					if(killer instanceof Player) sendTellraw(killer.getName(), message.toString());
					break;
				case OFF:
					break;
			}
		}

		if(entity instanceof Player ? LOG_PLAYER_BEHEAD : LOG_MOB_BEHEAD){
			String logEntry = (entity instanceof Player ? LOG_PLAYER_FORMAT : LOG_MOB_FORMAT)
					.replaceAll("(?i)\\$\\{VICTIM\\}", victimComp.toPlainText())
					.replaceAll("(?i)\\$\\{KILLER\\}", killerComp == null ? "" : killerComp.toPlainText())
					.replaceAll("(?i)\\$\\{ITEM\\}", itemComp == null ? "" : itemComp.toPlainText())
					.replaceAll("(?i)\\$\\{TIMESTAMP\\}", ""+System.currentTimeMillis());
			pl.writeToLogFile(logEntry);
		}
	}


	void onEntityDeath(@Nonnull final Entity victim, final Entity killer, final Event evt){
		if(killer != null){
			if(!killer.hasPermission("dropheads.canbehead")){
				if(DEBUG_MODE) pl.getLogger().info("dropheads.canbehead=false: "+killer.getName());
				return;
			}
			if(killer instanceof Creeper && ((Creeper)killer).isPowered()){
				if(CHARGED_CREEPER_DROPS){
					if(!victim.hasPermission("dropheads.canlosehead")){
						if(DEBUG_MODE) pl.getLogger().info("dropheads.canlosehead=false: "+victim.getName());
						return;
					}
					// Limit to 1 head per charged creeper explosion (mimics vanilla)
					final UUID creeperUUID = killer.getUniqueId();
					if(explodingChargedCreepers.add(creeperUUID)){
						if(DEBUG_MODE) pl.getLogger().info("Killed by charged creeper: "+victim.getType());
						attemptHeadDropEvent(victim, evt, killer, null);
						// Free up memory after a tick (optional)
						new BukkitRunnable(){@Override public void run(){explodingChargedCreepers.remove(creeperUUID);}}.runTaskLater(pl, 1);
						return;
					}
				}
			}
			if(killer.hasPermission("dropheads.alwaysbehead."+victim.getType().name().toLowerCase())){
				if(DEBUG_MODE) pl.getLogger().info("dropheads.alwaysbehead=true: "+killer.getName());
				if(!victim.hasPermission("dropheads.canlosehead")){
					if(DEBUG_MODE) pl.getLogger().info("dropheads.canlosehead=false: "+victim.getName());
					return;
				}
				attemptHeadDropEvent(victim, evt, killer,
						killer instanceof LivingEntity ? ((LivingEntity)killer).getEquipment().getItemInMainHand() : null);
				return;
			}
		}
		// Check if killer qualifies to trigger a behead.
		if((!ALLOW_INDIRECT_KILLS && killer == null
				// Note: Won't use timeSinceLastEntityDamage()... it would be expensive to keep track of
				&& JunkUtils.timeSinceLastPlayerDamage(victim) > INDIRECT_KILL_THRESHOLD_MILLIS) ||
			(!ALLOW_PROJECTILE_KILLS && killer != null && killer instanceof Projectile) ||
			(!ALLOW_NON_PLAYER_KILLS && (killer != null ? (
				killer instanceof Player == false &&
				(
					!ALLOW_PROJECTILE_KILLS ||
					killer instanceof Projectile == false ||
					((Projectile)killer).getShooter() instanceof Player == false
				)
			) : JunkUtils.timeSinceLastPlayerDamage(victim) > INDIRECT_KILL_THRESHOLD_MILLIS))
		) return;

		final ItemStack murderWeapon = getWeaponFromKiller(killer);

		if(!mustUseTools.isEmpty() && (murderWeapon == null || !mustUseTools.contains(murderWeapon.getType()))) return;

		final double toolBonus = murderWeapon == null ? 0D : toolBonuses.getOrDefault(murderWeapon.getType(), 0D);
		final int lootingLevel = murderWeapon == null ? 0 : murderWeapon.getEnchantmentLevel(Enchantment.LOOT_BONUS_MOBS);
		final boolean VANILLA_LOOTING = victim.getType() == EntityType.WITHER_SKELETON && VANILLA_WSKELE_LOOTING;
		final double lootingMod = (lootingLevel == 0 || VANILLA_LOOTING)
				? 1D : Math.min(Math.pow(LOOTING_MULT, lootingLevel), LOOTING_MULT*lootingLevel);
		final double lootingAdd = (VANILLA_LOOTING ? 0.01D : LOOTING_ADD)*lootingLevel;
		final double weaponMod = 1D + toolBonus;
		final double timeAliveMod = 1D + getTimeAliveBonus(victim);
		final double spawnCauseMod = JunkUtils.getSpawnCauseModifier(victim);
		final double rawDropChance = getRawDropChance(victim);
		final double permMod = getPermsBasedDropRateModifier(killer);
		final double dropChance = rawDropChance*spawnCauseMod*timeAliveMod*weaponMod*lootingMod*permMod + lootingAdd;

		final double dropRoll = rand.nextDouble();
		HeadRollEvent rollEvent = new HeadRollEvent(killer, victim, dropChance, dropRoll, dropRoll < dropChance);
		pl.getServer().getPluginManager().callEvent(rollEvent);
		if(rollEvent.getDropSuccess()){
			if(!victim.hasPermission("dropheads.canlosehead")){
				if(DEBUG_MODE) pl.getLogger().info("dropheads.canlosehead=false: "+victim.getName());
				return;
			}
			if(!recentlyBeheadedEntities.add(victim.getUniqueId())){
				if(DEBUG_MODE) pl.getLogger().info("Entity behead triggered twice: "+evt.getEventName());
				return;
			}
			// Clear old entries (100 is a very low threshold, but still relatively spam-proof)
			if(recentlyBeheadedEntities.size() > 100){
				final HashSet<UUID> notRecentEntities = new HashSet<UUID>();
				notRecentEntities.addAll(recentlyBeheadedEntities);
				new BukkitRunnable(){@Override public void run(){recentlyBeheadedEntities.removeAll(notRecentEntities);}}.runTaskLater(pl, 2);
			}
			attemptHeadDropEvent(victim, evt, killer, murderWeapon);
			if(DEBUG_MODE){
				DecimalFormat df = new DecimalFormat("0.0###");
				pl.getLogger().info("Dropping Head: "+TextureKeyLookup.getTextureKey(victim)
					+"\nKiller: "+(killer != null ? killer.getType() : "none")
					+", Weapon: "+(murderWeapon != null ? murderWeapon.getType() : "none")
					+"\nRaw chance: "+df.format(rawDropChance*100D)+"%\nMultipliers >> "+
					(spawnCauseMod != 1 ? "SpawnReason: "+df.format((spawnCauseMod-1D)*100D)+"%, " : "") +
					(timeAliveMod != 1 ? "TimeAlive: "+df.format((timeAliveMod-1D)*100D)+"%, " : "") +
					(weaponMod != 1 ? "Weapon: "+df.format((weaponMod-1D)*100D)+"%, " : "") +
					(lootingMod != 1 ? "Looting: "+df.format((lootingMod-1D)*100D)+"%, " : "") +
					(lootingAdd != 0 ? "Looting (Addition): "+df.format(lootingAdd*100D)+"%, " : "") +
					"\nFinal drop chance: "+df.format(dropChance*100D)+"%");
			}
		}
	}

	/**
	 * Checks for wither skeletons dropping skulls they were wearing in the helmet slot,
	 * handles the case where killed by a charged creeper, 
	 * @param victim The WitherSkeleton that was killed
	 * @param killer The entity that did the killing
	 * @param evt The parent EntityDeathEvent that was triggered
	 * @return True if no further handling is necessary, False if we should still call onEntityDeath()
	 */
	boolean handleWitherSkeltonDeathEvent(Entity victim, Entity killer, EntityDeathEvent evt){
		int newSkullsDropped = 0;
		Iterator<ItemStack> it = evt.getDrops().iterator();
		ArrayList<ItemStack> removedSkulls = new ArrayList<>();//TODO: remove this hacky fix once Bukkit/Spigot gets their shit sorted
		// Remove vanilla-dropped wither skeleton skulls so they aren't dropped twice.
		while(it.hasNext()){
			ItemStack next = it.next();
			if(next.getType() == Material.WITHER_SKELETON_SKULL){
				it.remove();
				++newSkullsDropped;
				//TODO: remove this hacky fix once Bukkit/Spigot gets their shit sorted
				if(!next.equals(new ItemStack(Material.WITHER_SKELETON_SKULL))) removedSkulls.add(next);
			}
		}
		// However, if it is wearing a head in an armor slot, don't remove the drop.
		for(ItemStack i : EvUtils.getEquipmentGuaranteedToDrop(evt.getEntity())){
			if(i != null && i.getType() == Material.WITHER_SKELETON_SKULL){evt.getDrops().add(i); --newSkullsDropped;}
			//TODO: remove this hacky fix below once Bukkit/Spigot gets their shit sorted
			if(i != null && i.getType() == Material.AIR && newSkullsDropped > 1){
				evt.getDrops().add(removedSkulls.isEmpty()
						? new ItemStack(Material.WITHER_SKELETON_SKULL)
						: removedSkulls.remove(removedSkulls.size()-1));
				--newSkullsDropped;
			}
		}
		if(newSkullsDropped > 1 && DEBUG_MODE) pl.getLogger().warning("Multiple non-DropHeads wither skull drops detected!");
		if(VANILLA_WSKELE_HANDLING || mobChances.getOrDefault(EntityType.WITHER_SKELETON, 0.025D) == 0.025D){
			// newSkullsDropped should always be 0 or 1 by this point
			if((newSkullsDropped == 1 || (killer != null && killer.hasPermission("dropheads.alwaysbehead.wither_skeleton")))
					&& victim.hasPermission("dropheads.canlosehead") && (killer == null || killer.hasPermission("dropheads.canbehead"))){
				// Don't drop the skull if another skull drop has already caused by the same charged creeper.
				if(killer != null && killer instanceof Creeper && ((Creeper)killer).isPowered() && CHARGED_CREEPER_DROPS &&
					!explodingChargedCreepers.add(killer.getUniqueId()))
				{
					return true;
				}
				for(int i=0; i<newSkullsDropped; ++i) attemptHeadDropEvent(victim, evt, killer, getWeaponFromKiller(killer));
			}
			return true;
		}
		return false;
	}

	class DeathEventExecutor implements EventExecutor{
		@Override public void execute(Listener listener, Event originalEvent){
			if(originalEvent instanceof EntityDeathEvent){
				final EntityDeathEvent evt = (EntityDeathEvent) originalEvent;
				final LivingEntity victim = evt.getEntity();
				final Entity killer = victim.getLastDamageCause() != null && victim.getLastDamageCause() instanceof EntityDamageByEntityEvent
						? ((EntityDamageByEntityEvent)victim.getLastDamageCause()).getDamager()
						: null;

				if(victim.getType() == EntityType.WITHER_SKELETON && handleWitherSkeltonDeathEvent(victim, killer, evt)){
					return;
				}
				// Remove vanilla-dropped heads from charged creeper kills
				if(CHARGED_CREEPER_DROPS && HeadUtils.dropsHeadFromChargedCreeper(victim.getType())
						&& killer != null && killer instanceof Creeper && ((Creeper)killer).isPowered()){
					Iterator<ItemStack> it = evt.getDrops().iterator();
					while(it.hasNext()){
						Material headType = it.next().getType();
						try{if(HeadUtils.getEntityFromHead(headType) == victim.getType()){it.remove(); break;}}
						catch(IllegalArgumentException ex){}
					}
				}
				onEntityDeath(victim, killer, evt);
			}
			else if(originalEvent instanceof VehicleDestroyEvent){
				final VehicleDestroyEvent evt = (VehicleDestroyEvent) originalEvent;
				onEntityDeath(/*victim=*/evt.getVehicle(), /*killer=*/evt.getAttacker(), evt);
			}
			else if(originalEvent instanceof HangingBreakByEntityEvent){
				final HangingBreakByEntityEvent evt = (HangingBreakByEntityEvent) originalEvent;
				onEntityDeath(/*victim=*/evt.getEntity(), /*killer=*/evt.getRemover(), evt);
			}
		}
	}
}