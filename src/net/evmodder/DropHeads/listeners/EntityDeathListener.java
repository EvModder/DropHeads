package net.evmodder.DropHeads.listeners;

import java.io.InputStream;
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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
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
	final DropHeads pl;
	final boolean allowNonPlayerKills, allowIndirectKills, allowProjectileKills;
	final boolean PLAYER_HEADS_ONLY, CHARGED_CREEPER_DROPS, REPLACE_DEATH_MESSAGE, VANILLA_WSKELE_LOOTING;
	final HashSet<Material> mustUseTools;
	final HashSet<EntityType> noLootingEffectMobs;
	final HashMap<EntityType, Double> mobChances;
	final HashMap<Material, Double> toolBonuses;
	final TreeMap<Long, Double> timeAliveBonuses;
	final HashSet<UUID> explodingChargedCreepers, recentlyBeheadedPlayers;
	final double DEFAULT_CHANCE, LOOTING_ADD, LOOTING_MULT;
	final boolean DEBUG_MODE;
	final Random rand;
	enum AnnounceMode {GLOBAL, LOCAL, DIRECT, OFF};
	final AnnounceMode ANNOUNCE_PLAYERS, ANNOUNCE_MOBS;
	final String MSG_BEHEAD, MSH_BEHEAD_BY, MSH_BEHEAD_BY_WITH;
	final String ITEM_DISPLAY_FORMAT;
	final boolean USE_PLAYER_DISPLAYNAMES = false;//TODO: move to config, when possible
	final int LOCAL_RANGE = 200;//TODO: move to config
	final int JSON_LIMIT = 15000;//TODO: move to config

	AnnounceMode parseAnnounceMode(@NotNull String value, AnnounceMode defaultMode){
		value = value.toUpperCase();
		if(value.equals("FALSE")) return AnnounceMode.OFF;
		if(value.equals("TRUE")) return AnnounceMode.GLOBAL;
		try{return AnnounceMode.valueOf(value);}
		catch(IllegalArgumentException ex){
			pl.getLogger().severe("Unknown announcement mode: '"+value+"'");
			pl.getLogger().warning("Please use one of the available modes: [GLOBAL, LOCAL, OFF]");
			return defaultMode;
		}
	}
	public EntityDeathListener(){
		pl = DropHeads.getPlugin();
		allowNonPlayerKills = pl.getConfig().getBoolean("drop-for-nonplayer-kills", false);
		allowIndirectKills = pl.getConfig().getBoolean("drop-for-indirect-kills", false);
		allowProjectileKills = pl.getConfig().getBoolean("drop-for-ranged-kills", false);
		PLAYER_HEADS_ONLY = pl.getConfig().getBoolean("player-heads-only", false);
		CHARGED_CREEPER_DROPS = pl.getConfig().getBoolean("charged-creeper-drops", true);
		VANILLA_WSKELE_LOOTING = pl.getConfig().getBoolean("vanilla-wither-skeleton-looting-behavior", true);
		LOOTING_ADD = pl.getConfig().getDouble("looting-mutliplier", 0.01D);
		LOOTING_MULT = pl.getConfig().getDouble("looting-mutliplier", 1D);
		if(LOOTING_MULT < 1) pl.getLogger().warning("looting-multiplier is set below 1.0, this means looting will DECREASe the chance of head drops!");
		if(LOOTING_ADD >= 1) pl.getLogger().warning("looting-addition is set to 1.0 or greater. This means heads will always drop when looting is used!");
		REPLACE_DEATH_MESSAGE = pl.getConfig().getBoolean("behead-announcement-replaces-death-message", true);
		DEBUG_MODE = pl.getConfig().getBoolean("debug-messages", true);
		ANNOUNCE_MOBS = parseAnnounceMode(pl.getConfig().getString("behead-announcement-mobs", "LOCAL"), AnnounceMode.LOCAL);
		ANNOUNCE_PLAYERS = parseAnnounceMode(pl.getConfig().getString("behead-announcement-players", "GLOBAL"), AnnounceMode.GLOBAL);
		String msg = pl.getConfig().getString("message-beheaded", "${VICTIM} was beheaded");
		String msgBy = pl.getConfig().getString("message-beheaded-by-entity", "${VICTIM}&r was beheaded by ${KILLER}&r");
		String msgByWith = pl.getConfig().getString("message-beheaded-by-entity-with-item", "${VICTIM}&r was beheaded by ${KILLER}&r using ${ITEM}&r");
		String itemDisplayFormat = pl.getConfig().getString("message-beheaded-item-display-format", "${RARITY}[${NAME}${RARITY}]&r");
		MSG_BEHEAD = TextUtils.translateAlternateColorCodes('&', msg);
		MSH_BEHEAD_BY = TextUtils.translateAlternateColorCodes('&', msgBy);
		MSH_BEHEAD_BY_WITH = TextUtils.translateAlternateColorCodes('&', msgByWith);
		ITEM_DISPLAY_FORMAT = TextUtils.translateAlternateColorCodes('&', itemDisplayFormat);
//		USE_PLAYER_DISPLAYNAMES = pl.getConfig().getBoolean("message-beheaded-use-player-displaynames", false);
		rand = new Random();

		mustUseTools = new HashSet<Material>();
		if(pl.getConfig().getBoolean("must-use-axe")){
			for(Material mat : Material.values()) if(mat.name().endsWith("_AXE")) mustUseTools.add(mat);
			/*mustUseTools.add(Material.DIAMOND_AXE);
			mustUseTools.add(Material.IRON_AXE);
			mustUseTools.add(Material.GOLDEN_AXE);
			mustUseTools.add(Material.STONE_AXE);
			mustUseTools.add(Material.WOODEN_AXE);*/
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
		InputStream defaultChances = pl.getClass().getResourceAsStream("/head-drop-rates.txt");
		String chances = FileIO.loadFile("head-drop-rates.txt", defaultChances);

		mobChances = new HashMap<EntityType, Double>();
		noLootingEffectMobs = new HashSet<EntityType>();
		double chanceForUnknown = 0D;
		for(String line : chances.split("\n")){
			String[] parts = line.replace(" ", "").replace("\t", "").toUpperCase().split(":");
			if(parts.length < 2) continue;
			try{
				double dropChance = Double.parseDouble(parts[1]);
				if(parts[0].equals("UNKNOWN")){
					chanceForUnknown = dropChance;
					continue;
				}
				EntityType eType = EntityType.valueOf(parts[0]);
				mobChances.put(eType, dropChance);
				if(parts.length > 2 && parts[2].equals("NOLOOTING")) noLootingEffectMobs.add(eType);
				if(dropChance > 1F){
					pl.getLogger().warning("Invalid value: "+parts[1]);
					pl.getLogger().warning("Drop chance should be a decimal between 0 and 1");
					mobChances.put(eType, Math.min(dropChance/10F, 1F));
				}
			}
			catch(NumberFormatException ex){pl.getLogger().severe("Invalid value: "+parts[1]);}
			catch(IllegalArgumentException ex){pl.getLogger().severe("Unknown entity type: "+parts[0]);}
		}
		DEFAULT_CHANCE = chanceForUnknown;
		explodingChargedCreepers = new HashSet<UUID>();

		// VehicleDestroyListener can handle minecarts and boats
		boolean nonLivingEntitiesCanDropHeads = mobChances.entrySet().stream()
				.anyMatch(entry -> !entry.getKey().isAlive() && entry.getValue() > 0);
		if(nonLivingEntitiesCanDropHeads){
			pl.getServer().getPluginManager().registerEvents(new VehicleDestroyListener(this), pl);
		}
		if(REPLACE_DEATH_MESSAGE){
			recentlyBeheadedPlayers = new HashSet<UUID>();
			pl.getServer().getPluginManager().registerEvents(new Listener(){
				@EventHandler(priority = EventPriority.HIGHEST)
				public void playerDeathEvent(PlayerDeathEvent evt){
					if(recentlyBeheadedPlayers.remove(evt.getEntity().getUniqueId())) evt.setDeathMessage("");
				}
			}, pl);
		}
		else recentlyBeheadedPlayers = null;
	}

	String getItemDisplay(ItemStack item){
		String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
				? ChatColor.ITALIC+item.getItemMeta().getDisplayName() : TextUtils.getNormalizedName(item.getType());
		ChatColor rarityColor = JunkUtils.getRarityColor(item, true);
		return ITEM_DISPLAY_FORMAT
				.replace("${NAME}", itemName)
				.replace("${RARITY}", ""+rarityColor)
				.replace("${AMOUNT}", ""+item.getAmount());
	}
	void sendTellraw(String target, String message){
		pl.getServer().dispatchCommand(pl.getServer().getConsoleSender(), "minecraft:tellraw "+target+" "+message);
	}
	void dropHead(Entity entity, EntityDeathEvent evt, Entity killer, ItemStack weapon){
		if(evt == null) entity.getWorld().dropItemNaturally(entity.getLocation(), pl.getAPI().getHead(entity));
		else evt.getDrops().add(pl.getAPI().getHead(entity));
		TellrawBlob message = new TellrawBlob();
		if(killer != null){
			Component killerComp = new SelectorComponent(killer.getUniqueId(), USE_PLAYER_DISPLAYNAMES);
			Component itemComp = null;
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
				message.addComponent(MSH_BEHEAD_BY_WITH);
				message.replaceRawTextWithComponent("${ITEM}", itemComp);
			}
			else message.addComponent(MSH_BEHEAD_BY);
			if(USE_PLAYER_DISPLAYNAMES) message.replaceRawTextWithComponent("${KILLER}", new RawTextComponent("${KILLER}"));
			message.replaceRawTextWithComponent("${KILLER}", killerComp);
		}
		else message.addComponent(MSG_BEHEAD);
		message.replaceRawTextWithComponent("${VICTIM}", new SelectorComponent(entity.getUniqueId(), USE_PLAYER_DISPLAYNAMES));

//		if(DEBUG_MODE) pl.getLogger().info("Tellraw message: "+message);
		if(DEBUG_MODE) pl.getLogger().info(/*"Tellraw message: "+*/message.toPlainText());
//		if(DEBUG_MODE) pl.getLogger().info("Announce Mode: "+(entity instanceof Player ? ANNOUNCE_PLAYERS : ANNOUNCE_MOBS));

		switch(entity instanceof Player ? ANNOUNCE_PLAYERS : ANNOUNCE_MOBS){
			case GLOBAL:
				if(entity instanceof Player && REPLACE_DEATH_MESSAGE){
					recentlyBeheadedPlayers.add(entity.getUniqueId());
					((PlayerDeathEvent)evt).setDeathMessage(message.toPlainText());
				}
				sendTellraw("@a", message.toString());
				break;
			case LOCAL:
				for(Player p : EvUtils.getNearbyPlayers(entity.getLocation(), LOCAL_RANGE)) sendTellraw(p.getName(), message.toString());
				break;
			case DIRECT:
				if(killer instanceof Player) sendTellraw(killer.getName(), message.toString());
				break;
			case OFF:
				//sendAnnouncement(message, Arrays.asList());
				break;
		}
	}

	static long timeSinceLastPlayerDamage(Entity entity){
		long lastDamage = entity.hasMetadata("PlayerDamage") ? entity.getMetadata("PlayerDamage").get(0).asLong() : 0;
		return System.currentTimeMillis() - lastDamage;
	}
	static double getSpawnCauseModifier(Entity e){
		return e.hasMetadata("SpawnReason") ? e.getMetadata("SpawnReason").get(0).asDouble() : 1D;
	}
	double getTimeAliveBonus(Entity e){
		long millisecondsLived = e.getTicksLived()*50L;
		return timeAliveBonuses.floorEntry(millisecondsLived).getValue();
	}

	@EventHandler(priority = EventPriority.LOW)
	public void entityDeathEvent(EntityDeathEvent evt){
		final LivingEntity victim = evt.getEntity();
		if(PLAYER_HEADS_ONLY && victim instanceof Player == false) return;
		Entity killer = null;
		if(victim.getLastDamageCause() != null && victim.getLastDamageCause() instanceof EntityDamageByEntityEvent){
			killer = ((EntityDamageByEntityEvent)victim.getLastDamageCause()).getDamager();
			if(!killer.hasPermission("dropheads.canbehead")) return;
			if(killer instanceof Creeper && ((Creeper)killer).isPowered()){
				if(CHARGED_CREEPER_DROPS && !HeadUtils.dropsHeadFromChargedCreeper(victim.getType())
						&& explodingChargedCreepers.add(killer.getUniqueId())){
					if(DEBUG_MODE) pl.getLogger().info("Killed by charged creeper: "+victim.getType());
					dropHead(victim, evt, killer, null);
					// Cleanup memory after a tick (optional)
					final UUID creeperUUID = killer.getUniqueId();
					new BukkitRunnable(){@Override public void run(){
						explodingChargedCreepers.remove(creeperUUID);
					}}.runTaskLater(pl, 1);
				}
				return;
			}
			if(killer.hasPermission("dropheads.alwaysbehead")){
				if(DEBUG_MODE) pl.getLogger().info("dropheads.alwaysbehead perm: "+killer.getCustomName());
				dropHead(victim, evt, killer, killer instanceof LivingEntity ? ((LivingEntity)killer).getEquipment().getItemInMainHand() : null);
				return;
			}
		}
		// Check if killer is not a player.
		if(!allowNonPlayerKills && (killer == null ||
			(killer instanceof Player == false &&
				(
					!allowProjectileKills ||
					killer instanceof Projectile == false ||
					((Projectile)killer).getShooter() instanceof Player == false
				) && (
					!allowIndirectKills ||
					timeSinceLastPlayerDamage(victim) > /*seconds=30*/30*1000
				)
			)
		)) return;

		final ItemStack murderWeapon = 
			killer != null ?
				killer instanceof LivingEntity ? ((LivingEntity)killer).getEquipment().getItemInMainHand() :
				killer instanceof Projectile && killer.hasMetadata("ShotUsing") ? (ItemStack)killer.getMetadata("ShotUsing").get(0).value() :
				null
			: null;

		if(!mustUseTools.isEmpty() && (murderWeapon == null || !mustUseTools.contains(murderWeapon.getType()))) return;
		final int lootingLevel = murderWeapon == null ? 0 : murderWeapon.getEnchantmentLevel(Enchantment.LOOT_BONUS_MOBS);
		final double toolBonus = murderWeapon == null ? 0D : toolBonuses.getOrDefault(murderWeapon.getType(), 0D);
		final double lootingMod = Math.pow(LOOTING_MULT, lootingLevel);
		final double lootingAdd = LOOTING_ADD*lootingLevel;
		final double weaponMod = 1D + toolBonus;
		final double timeAliveMod = 1D + getTimeAliveBonus(victim);
		final double spawnCauseMod = getSpawnCauseModifier(victim);
		final double rawDropChance = mobChances.getOrDefault(victim.getType(), DEFAULT_CHANCE);
		double dropChance = rawDropChance*spawnCauseMod*weaponMod + lootingAdd;

		// Remove vanilla-dropped wither skeleton skulls so they aren't dropped twice.
		if(evt.getEntityType() == EntityType.WITHER_SKELETON){
			if(VANILLA_WSKELE_LOOTING) dropChance = rawDropChance*spawnCauseMod*weaponMod + 0.01D*lootingLevel;
			Iterator<ItemStack> it = evt.getDrops().iterator();
			while(it.hasNext()) if(it.next().getType() == Material.WITHER_SKELETON_SKULL) it.remove();
			// However, if it is wearing a head in its helmet slot, don't remove the drop.
			// Note-to-self: Be careful with entity_equipment, heads on armor stands, etc.
			for(ItemStack i : EvUtils.getEquipmentGuaranteedToDrop(evt.getEntity())){
				if(i != null && i.getType() == Material.WITHER_SKELETON_SKULL) evt.getDrops().add(i);
			}
		}

		if(rand.nextDouble() < dropChance){
			dropHead(victim, evt, killer, murderWeapon);
			if(DEBUG_MODE){
				DecimalFormat df = new DecimalFormat("0.0###");
				pl.getLogger().info("Dropped Head: "+TextureKeyLookup.getTextureKey(victim)+"\n"
					+"Raw chance: "+df.format(rawDropChance*100D)+"%, "
					+"SpawnReason Bonus: "+df.format((spawnCauseMod-1D)*100D)+"%, "
					+"TimeAlive Bonus: "+df.format((timeAliveMod-1D)*100D)+"%, "
					+"Looting Bonus: "+df.format((lootingMod-1D)*100D)+"%, "
					+"Weapon Bonus: "+df.format((weaponMod-1D)*100D)+"%, "
					+"Final drop chance: "+df.format(dropChance*100D)+"%");
			}
		}
	}
}