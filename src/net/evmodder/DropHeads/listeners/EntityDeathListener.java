package net.evmodder.DropHeads.listeners;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import com.sun.istack.internal.NotNull;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.JunkUtils;
import net.evmodder.DropHeads.TextureKeyLookup;
import net.evmodder.EvLib.EvUtils;
import net.evmodder.EvLib.FileIO;
import net.evmodder.EvLib.extras.TellrawUtils.HoverEvent;
import net.evmodder.EvLib.extras.TellrawUtils.ActionComponent;
import net.evmodder.EvLib.extras.TellrawUtils.Component;
import net.evmodder.EvLib.extras.TellrawUtils.SelectorComponent;
import net.evmodder.EvLib.extras.TellrawUtils.RawTextComponent;
import net.evmodder.EvLib.extras.TellrawUtils.TellrawBlob;
import net.evmodder.EvLib.extras.HeadUtils;
import net.evmodder.EvLib.extras.TextUtils;

public class EntityDeathListener implements Listener{
	public enum AnnounceMode {GLOBAL, LOCAL, DIRECT, OFF};
	final AnnounceMode DEFAULT_ANNOUNCE;
	final EventPriority PRIORITY;

	final boolean ALLOW_NON_PLAYER_KILLS, ALLOW_INDIRECT_KILLS, ALLOW_PROJECTILE_KILLS;
	final boolean PLAYER_HEADS_ONLY, CHARGED_CREEPER_DROPS, REPLACE_DEATH_MESSAGE, VANILLA_WSKELE_LOOTING;
	final double DEFAULT_CHANCE, LOOTING_ADD, LOOTING_MULT;
	final boolean DEBUG_MODE, LOG_PLAYER_BEHEAD, LOG_MOB_BEHEAD;
	final String LOG_MOB_FORMAT, LOG_PLAYER_FORMAT;
	final String MSG_BEHEAD, MSH_BEHEAD_BY, MSH_BEHEAD_BY_WITH, MSH_BEHEAD_BY_WITH_NAMED;
	final String ITEM_DISPLAY_FORMAT;
	final long INDIRECT_KILL_THRESHOLD_MILLIS = 30*1000;//TODO: move to config
	final boolean USE_PLAYER_DISPLAYNAMES = false;//TODO: move to config, when possible
	final boolean CROSS_DIMENSIONAL_BROADCAST = true;//TODO: move to config
	final int LOCAL_RANGE = 200;//TODO: move to config
	final int JSON_LIMIT = 15000;//TODO: move to config

	final DropHeads pl;
	final Random rand;
	final HashSet<Material> mustUseTools;
	final HashSet<EntityType> noLootingEffectMobs;
	final HashMap<EntityType, Double> mobChances;
	final HashMap<EntityType, AnnounceMode> mobAnnounceModes;
	final HashMap<Material, Double> toolBonuses;
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
		VANILLA_WSKELE_LOOTING = pl.getConfig().getBoolean("vanilla-wither-skeleton-looting-behavior", true);
		LOOTING_ADD = pl.getConfig().getDouble("looting-addition", 0.01D);
		LOOTING_MULT = pl.getConfig().getDouble("looting-mutliplier", 1D);
		if(LOOTING_ADD >= 1) pl.getLogger().warning("looting-addition is set to 1.0 or greater. This means heads will always drop when looting is used!");
		if(LOOTING_MULT < 1) pl.getLogger().warning("looting-multiplier is set below 1.0, this means looting will DECREASe the chance of head drops!");
		REPLACE_DEATH_MESSAGE = pl.getConfig().getBoolean("behead-announcement-replaces-death-message", true);
		PRIORITY = JunkUtils.parseEventPriority(pl.getConfig().getString("death-listener-priority", "LOW"), EventPriority.LOW);
		DEBUG_MODE = pl.getConfig().getBoolean("debug-messages", true);
		final boolean ENABLE_LOG = pl.getConfig().getBoolean("log.enable", false);
		LOG_MOB_BEHEAD = ENABLE_LOG && pl.getConfig().getBoolean("log.log-mob-behead", false);
		LOG_PLAYER_BEHEAD = ENABLE_LOG && pl.getConfig().getBoolean("log.log-player-behead", false);
		LOG_MOB_FORMAT = LOG_MOB_BEHEAD ? pl.getConfig().getString("log.log-mob-behead-format",
				"${TIMESTAMP},mob decapitated,${VICTIM},${KILLER},${ITEM}") : null;
		LOG_PLAYER_FORMAT = LOG_PLAYER_BEHEAD ? pl.getConfig().getString("log.log-player-behead-format",
				"${TIMESTAMP},player decapitated,${VICTIM},${KILLER},${ITEM}") : null;

		String msg = pl.getConfig().getString("message-beheaded", "${VICTIM} was beheaded");
		String msgBy = pl.getConfig().getString("message-beheaded-by-entity", "${VICTIM}&r was beheaded by ${KILLER}&r");
		String msgByWith = pl.getConfig().getString("message-beheaded-by-entity-with-item", "${VICTIM}&r was beheaded by ${KILLER}&r using ${ITEM}&r");
		String msgByWithNamed = pl.getConfig().getString("message-beheaded-by-entity-with-item-named",
				"${VICTIM}&r was beheaded by ${KILLER}&r using ${ITEM}&r");
		String itemDisplayFormat = pl.getConfig().getString("message-beheaded-item-display-format", "${RARITY}[${NAME}${RARITY}]&r");
		MSG_BEHEAD = TextUtils.translateAlternateColorCodes('&', msg);
		MSH_BEHEAD_BY = TextUtils.translateAlternateColorCodes('&', msgBy);
		MSH_BEHEAD_BY_WITH = TextUtils.translateAlternateColorCodes('&', msgByWith);
		MSH_BEHEAD_BY_WITH_NAMED = TextUtils.translateAlternateColorCodes('&', msgByWithNamed);
		ITEM_DISPLAY_FORMAT = TextUtils.translateAlternateColorCodes('&', itemDisplayFormat);
//		USE_PLAYER_DISPLAYNAMES = pl.getConfig().getBoolean("message-beheaded-use-player-displaynames", false);

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

		mustUseTools = new HashSet<Material>();
		if(pl.getConfig().getBoolean("must-use-axe")){
			for(Material mat : Material.values()) if(mat.name().endsWith("_AXE")) mustUseTools.add(mat);
//			mustUseTools.addAll(Arrays.asList(Material.NETHERITE_AXE, Material.DIAMOND_AXE, Material.IRON_AXE,
//								Material.IRON_AXE, Material.GOLDEN_AXE, Material.STONE_AXE, Material.WOODEN_AXE));
		}
		else for(String toolName : pl.getConfig().getStringList("must-use")){
			if(toolName.isEmpty()) continue;
			Material mat = Material.getMaterial(toolName.toUpperCase());
			if(mat != null) mustUseTools.add(mat);
			else pl.getLogger().warning("Unknown Tool \""+toolName+"\"!");
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

		//Load individual mobs' drop chances
		mobChances = new HashMap<EntityType, Double>();
		noLootingEffectMobs = new HashSet<EntityType>();
		//double chanceForUnknown = 0D;
		if(PLAYER_HEADS_ONLY){
			pl.getServer().getPluginManager().registerEvent(PlayerDeathEvent.class, this, PRIORITY, new DeathEventExecutor(), pl);
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
				try{
					double dropChance = Double.parseDouble(parts[1]);
					EntityType eType = EntityType.valueOf(parts[0]);
					mobChances.put(eType, dropChance);
					if(parts.length > 2 && parts[2].equals("NOLOOTING")) noLootingEffectMobs.add(eType);
					if(dropChance < 0D || dropChance > 1D){
						pl.getLogger().warning("Invalid value: "+parts[1]);
						pl.getLogger().warning("Drop chance should be a decimal between 0 and 1");
						if(dropChance > 0D && dropChance <= 100D) mobChances.put(eType, dropChance/100D);
					}
				}
				catch(NumberFormatException ex){pl.getLogger().severe("Invalid value: "+parts[1]);}
				catch(IllegalArgumentException ex){
					// Only throw an error for mobs that aren't defined in the default config (which may be from future/past versions)
					if(!defaultConfigMobs.contains(parts[0])) pl.getLogger().severe("Unknown entity type: "+parts[0]);
				}
			}
			// No need storing 0-chance mobs if the default drop chance is 0
			if(mobChances.getOrDefault(EntityType.UNKNOWN, 0D) == 0D) mobChances.entrySet().removeIf(entry -> entry.getValue() == 0D);

			boolean entityHeads = mobChances.entrySet().stream().anyMatch(
					entry -> entry.getKey().isAlive() && entry.getKey() != EntityType.PLAYER && entry.getValue() > 0D);
			if(entityHeads){
				pl.getServer().getPluginManager().registerEvent(EntityDeathEvent.class, this, PRIORITY, new DeathEventExecutor(), pl);
			}
			boolean nonLivingVehicleHeads = mobChances.entrySet().stream().anyMatch(  // Applies for: Boat, Minecart
					entry -> !entry.getKey().isAlive() && entry.getValue() > 0D && Vehicle.class.isAssignableFrom(entry.getKey().getEntityClass()));
			if(nonLivingVehicleHeads){
				pl.getServer().getPluginManager().registerEvent(VehicleDestroyEvent.class, this, PRIORITY, new DeathEventExecutor(), pl);
			}
			boolean nonLivingHangingHeads = mobChances.entrySet().stream().anyMatch(  // Applies for: Painting, LeashHitch, ItemFrame
					entry -> !entry.getKey().isAlive() && entry.getValue() > 0D && Hanging.class.isAssignableFrom(entry.getKey().getEntityClass()));
			if(nonLivingHangingHeads){
				pl.getServer().getPluginManager().registerEvent(HangingBreakByEntityEvent.class, this, PRIORITY, new DeathEventExecutor(), pl);
			}
		}  // if(!PLAYER_HEADS_ONLY)
		DEFAULT_CHANCE = mobChances.getOrDefault(EntityType.UNKNOWN, 0D);
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
	}


	public double getTimeAliveBonus(Entity e){
		long millisecondsLived = e.getTicksLived()*50L;
		return timeAliveBonuses.floorEntry(millisecondsLived).getValue();
	}
	String getItemDisplay(ItemStack item){
		String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
				? ChatColor.ITALIC+item.getItemMeta().getDisplayName() : TextUtils.getNormalizedName(item.getType());
		ChatColor rarityColor = JunkUtils.getRarityColor(item, /*checkCustomName=*/false);
		return ITEM_DISPLAY_FORMAT
				.replaceAll("(?i)\\$\\{NAME\\}", itemName)
				.replaceAll("(?i)\\$\\{RARITY\\}", ""+rarityColor)
				.replaceAll("(?i)\\$\\{AMOUNT\\}", ""+item.getAmount());
	}
	void sendTellraw(String target, String message){
		pl.getServer().dispatchCommand(pl.getServer().getConsoleSender(), "minecraft:tellraw "+target+" "+message);
	}
	void dropHead(Entity entity, Event evt, Entity killer, ItemStack weapon){
		if(evt instanceof EntityDeathEvent) ((EntityDeathEvent)evt).getDrops().add(pl.getAPI().getHead(entity));
		else entity.getWorld().dropItemNaturally(entity.getLocation(), pl.getAPI().getHead(entity));
		recentlyBeheadedEntities.add(entity.getUniqueId());

		TellrawBlob message = new TellrawBlob();
		Component killerComp = null, itemComp = null;
		if(killer != null){
			killerComp = new SelectorComponent(killer.getUniqueId(), USE_PLAYER_DISPLAYNAMES);
			if(weapon != null && weapon.getType() != Material.AIR){
				String itemDisplay = getItemDisplay(weapon);
				String jsonData = JunkUtils.convertItemStackToJson(weapon, JSON_LIMIT);
				itemComp = new ActionComponent(itemDisplay, HoverEvent.SHOW_ITEM, jsonData);
			}
			if(killer instanceof Projectile){
				if(weapon == null) itemComp = new SelectorComponent(killer.getUniqueId(), USE_PLAYER_DISPLAYNAMES);
				ProjectileSource shooter = ((Projectile)killer).getShooter();
				if(shooter instanceof Entity) killerComp = new SelectorComponent(((Entity)shooter).getUniqueId(), USE_PLAYER_DISPLAYNAMES);
				else if(shooter instanceof BlockProjectileSource){
					String blockName = TextUtils.getNormalizedName(((BlockProjectileSource)shooter).getBlock().getType());
					killerComp = new RawTextComponent(blockName);
				}
			}
			if(itemComp != null){
				boolean hasCustomName = weapon.hasItemMeta() && weapon.getItemMeta().hasDisplayName();
				message.addComponent(hasCustomName ? MSH_BEHEAD_BY_WITH_NAMED : MSH_BEHEAD_BY_WITH);
				message.replaceRawTextWithComponent("${ITEM}", itemComp);
			}
			else message.addComponent(MSH_BEHEAD_BY);
			//if(USE_PLAYER_DISPLAYNAMES) message.replaceRawTextWithComponent("${KILLER}", ...);
			message.replaceRawTextWithComponent("${KILLER}", killerComp);
		}
		else message.addComponent(MSG_BEHEAD);
		Component victimComp = new SelectorComponent(entity.getUniqueId(), USE_PLAYER_DISPLAYNAMES);
		message.replaceRawTextWithComponent("${VICTIM}", victimComp);

		if(DEBUG_MODE) pl.getLogger().info(/*"Tellraw message: "+*/message.toPlainText());

		switch(mobAnnounceModes.getOrDefault(entity.getType(), DEFAULT_ANNOUNCE)){
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

		if(entity instanceof Player ? LOG_PLAYER_BEHEAD : LOG_MOB_BEHEAD){
			String logEntry = (entity instanceof Player ? LOG_PLAYER_FORMAT : LOG_MOB_FORMAT)
					.replaceAll("(?i)\\$\\{VICTIM\\}", victimComp.toPlainText())
					.replaceAll("(?i)\\$\\{KILLER\\}", killerComp == null ? "" : killerComp.toPlainText())
					.replaceAll("(?i)\\$\\{ITEM\\}", itemComp == null ? "" : itemComp.toPlainText())
					.replaceAll("(?i)\\$\\{TIMESTAMP\\}", ""+System.currentTimeMillis());
			pl.writeToLogFile(logEntry);
		}
	}


	void onEntityDeath(@NotNull final Entity victim, final Entity killer, final Event evt){
		if(PLAYER_HEADS_ONLY && victim instanceof Player == false) return;
		if(killer != null){
			if(!killer.hasPermission("dropheads.canbehead")){
				if(DEBUG_MODE) pl.getLogger().info("dropheads.canbehead=false: "+killer.getCustomName());
				return;
			}
			if(!killer.hasPermission("dropheads.canbehead")) return;
			if(killer instanceof Creeper && ((Creeper)killer).isPowered()){
				if(CHARGED_CREEPER_DROPS){
					if(!victim.hasPermission("dropheads.canlosehead")){
						if(DEBUG_MODE) pl.getLogger().info("dropheads.canlosehead=false: "+victim.getCustomName());
						return;
					}
					// Limit to 1 head per charged creeper explosion (mimics vanilla)
					final UUID creeperUUID = killer.getUniqueId();
					if(explodingChargedCreepers.add(creeperUUID) && !HeadUtils.dropsHeadFromChargedCreeper(victim.getType())){
						if(DEBUG_MODE) pl.getLogger().info("Killed by charged creeper: "+victim.getType());
						dropHead(victim, evt, killer, null);
						// Free up memory after a tick (optional)
						new BukkitRunnable(){@Override public void run(){explodingChargedCreepers.remove(creeperUUID);}}.runTaskLater(pl, 1);
						return;
					}
				}
			}
			if(killer.hasPermission("dropheads.alwaysbehead")){
				if(DEBUG_MODE) pl.getLogger().info("dropheads.alwaysbehead=true: "+killer.getCustomName());
				if(!victim.hasPermission("dropheads.canlosehead")){
					if(DEBUG_MODE) pl.getLogger().info("dropheads.canlosehead=false: "+victim.getCustomName());
					return;
				}
				dropHead(victim, evt, killer, killer instanceof LivingEntity ? ((LivingEntity)killer).getEquipment().getItemInMainHand() : null);
				return;
			}
		}
		// Check if killer qualifies to trigger a behead.
		if((!ALLOW_INDIRECT_KILLS && killer == null
				// Note: Can't use timeSinceLastEntityDamage()... it would be expensive to keep track of
				&& JunkUtils.timeSinceLastPlayerDamage(victim) > INDIRECT_KILL_THRESHOLD_MILLIS) ||
			(!ALLOW_PROJECTILE_KILLS && killer != null && killer instanceof Projectile) ||
			(!ALLOW_NON_PLAYER_KILLS && killer != null ? (
				killer instanceof Player == false &&
				(
					!ALLOW_PROJECTILE_KILLS ||
					killer instanceof Projectile == false ||
					((Projectile)killer).getShooter() instanceof Player == false
				)
				
			) : JunkUtils.timeSinceLastPlayerDamage(victim) > INDIRECT_KILL_THRESHOLD_MILLIS)
		) return;

		final ItemStack murderWeapon = 
			killer != null ?
				killer instanceof LivingEntity ? ((LivingEntity)killer).getEquipment().getItemInMainHand() :
				killer instanceof Projectile && killer.hasMetadata("ShotUsing") ? (ItemStack)killer.getMetadata("ShotUsing").get(0).value() :
				null
			: null;

		if(!mustUseTools.isEmpty() && (murderWeapon == null || !mustUseTools.contains(murderWeapon.getType()))) return;

		final double toolBonus = murderWeapon == null ? 0D : toolBonuses.getOrDefault(murderWeapon.getType(), 0D);
		final int lootingLevel = murderWeapon == null ? 0 : murderWeapon.getEnchantmentLevel(Enchantment.LOOT_BONUS_MOBS);
		final boolean VANILLA_LOOTING = victim.getType() == EntityType.WITHER_SKELETON && VANILLA_WSKELE_LOOTING;
		final double lootingMod = (lootingLevel == 0 || VANILLA_LOOTING) ? 1D : Math.min(Math.pow(LOOTING_MULT, lootingLevel), LOOTING_MULT*lootingLevel);
		final double lootingAdd = (VANILLA_LOOTING ? 0.01D : LOOTING_ADD)*lootingLevel;
		final double weaponMod = 1D + toolBonus;
		final double timeAliveMod = 1D + getTimeAliveBonus(victim);
		final double spawnCauseMod = JunkUtils.getSpawnCauseModifier(victim);
		final double rawDropChance = mobChances.getOrDefault(victim.getType(), DEFAULT_CHANCE);
		final double dropChance = rawDropChance*spawnCauseMod*timeAliveMod*weaponMod*lootingMod + lootingAdd;

		if(rand.nextDouble() < dropChance){
			if(!victim.hasPermission("dropheads.canlosehead")){
				if(DEBUG_MODE) pl.getLogger().info("dropheads.canlosehead=false: "+victim.getCustomName());
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
			dropHead(victim, evt, killer, murderWeapon);
			if(DEBUG_MODE){
				DecimalFormat df = new DecimalFormat("0.0###");
				pl.getLogger().info("Dropped Head: "+TextureKeyLookup.getTextureKey(victim)
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

	class DeathEventExecutor implements EventExecutor{
		@Override public void execute(Listener listener, Event originalEvent){
			if(originalEvent instanceof EntityDeathEvent){
				final EntityDeathEvent evt = (EntityDeathEvent) originalEvent;
				final LivingEntity victim = evt.getEntity();
				final Entity killer = victim.getLastDamageCause() != null && victim.getLastDamageCause() instanceof EntityDamageByEntityEvent
						? ((EntityDamageByEntityEvent)victim.getLastDamageCause()).getDamager()
						: null;

				// Remove vanilla-dropped wither skeleton skulls so they aren't dropped twice.
				if(victim.getType() == EntityType.WITHER_SKELETON){
					Iterator<ItemStack> it = evt.getDrops().iterator();
					while(it.hasNext()) if(it.next().getType() == Material.WITHER_SKELETON_SKULL) it.remove();
					// However, if it is wearing a head in its helmet slot, don't remove the drop.
					// Note-to-self: Be careful with entity_equipment, heads on armor stands, etc.
					for(ItemStack i : EvUtils.getEquipmentGuaranteedToDrop(evt.getEntity())){
						if(i != null && i.getType() == Material.WITHER_SKELETON_SKULL) evt.getDrops().add(i);
					}
				}
				onEntityDeath(victim, killer, evt);
			}
			if(originalEvent instanceof VehicleDestroyEvent){
				final VehicleDestroyEvent evt = (VehicleDestroyEvent) originalEvent;
				onEntityDeath(evt.getVehicle(), evt.getAttacker(), evt);
			}
			if(originalEvent instanceof HangingBreakByEntityEvent){
				final HangingBreakByEntityEvent evt = (HangingBreakByEntityEvent) originalEvent;
				onEntityDeath(evt.getEntity(), evt.getRemover(), evt);
			}
		}
	}
}