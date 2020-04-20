package net.evmodder.DropHeads.listeners;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
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
import org.bukkit.scheduler.BukkitRunnable;
import com.sun.istack.internal.NotNull;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.JunkUtils;
import net.evmodder.EvLib.EvUtils;
import net.evmodder.EvLib.FileIO;
import net.evmodder.EvLib.extras.TellrawUtils.HoverEvent;
import net.evmodder.EvLib.extras.TellrawUtils.ActionComponent;
import net.evmodder.EvLib.extras.TellrawUtils.SelectorComponent;
import net.evmodder.EvLib.extras.TellrawUtils.TellrawBlob;
import net.evmodder.EvLib.extras.HeadUtils;
import net.evmodder.EvLib.extras.TextUtils;

public class EntityDeathListener implements Listener{
	final DropHeads pl;
	final boolean allowNonPlayerKills, allowIndirectKills, allowProjectileKills; 
	final boolean PLAYER_HEADS_ONLY, CHARGED_CREEPER_DROPS, REPLACE_DEATH_MESSAGE;
	final HashSet<Material> mustUseTools;
	final HashSet<EntityType> noLootingEffectMobs;
	final HashMap<EntityType, Double> mobChances;
	final HashMap<Material, Double> toolBonuses;
	final HashSet<UUID> explodingChargedCreepers, recentlyBeheadedPlayers;
	final double DEFAULT_CHANCE;
	final boolean DEBUG_MODE;
	final Random rand;
	enum AnnounceMode {GLOBAL, LOCAL, DIRECT, OFF};
	final AnnounceMode ANNOUNCE_PLAYERS, ANNOUNCE_MOBS;
	final String MSG_BEHEAD, MSH_BEHEAD_BY, MSH_BEHEAD_BY_WITH;
//	final String ITEM_DISPLAY_FORMAT = TextUtils.translateAlternateColorCodes('&', "&r[&b${NAME}&r]");//TODO: move to config
	final String ITEM_DISPLAY_FORMAT = "${NAME}";
	final int LOCAL_RANGE = 150;//TODO: move to config
	final int JSON_LIMIT = 15000;//TODO: move to config

	AnnounceMode parseAnnounceMode(@NotNull String value, AnnounceMode defaultMode){
		try{return AnnounceMode.valueOf(value.toUpperCase());}
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
		REPLACE_DEATH_MESSAGE = pl.getConfig().getBoolean("behead-announcement-replaces-death-message", true);
		DEBUG_MODE = pl.getConfig().getBoolean("debug-messages", true);
		ANNOUNCE_MOBS = parseAnnounceMode(pl.getConfig().getString("behead-announcement-mobs", "LOCAL"), AnnounceMode.LOCAL);
		ANNOUNCE_PLAYERS = parseAnnounceMode(pl.getConfig().getString("behead-announcement-players", "GLOBAL"), AnnounceMode.GLOBAL);
		String msg = pl.getConfig().getString("message-beheaded", "${VICTIM} was beheaded");
		String msgBy = pl.getConfig().getString("message-beheaded-by-entity", "${VICTIM} was beheaded by ${KILLER}");
		String msgByWith = pl.getConfig().getString("message-beheaded-by-entity-with-item", "${VICTIM} was beheaded by ${KILLER} using ${ITEM}");
		MSG_BEHEAD = TextUtils.translateAlternateColorCodes('&', msg);
		MSH_BEHEAD_BY = TextUtils.translateAlternateColorCodes('&', msgBy);
		MSH_BEHEAD_BY_WITH = TextUtils.translateAlternateColorCodes('&', msgByWith);
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
		ConfigurationSection specificModifiers = pl.getConfig().getConfigurationSection("specific-tool-modifiers");
		if(specificModifiers != null) for(String toolName : specificModifiers.getKeys(false)){
			Material mat = Material.getMaterial(toolName.toUpperCase());
			if(mat != null) toolBonuses.put(mat, specificModifiers.getDouble(toolName));
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

	String getNormalizedName(Entity entity){
		if(entity instanceof Player) return ((Player)entity).getDisplayName();
		return entity.getName() != null ? entity.getName() : TextUtils.getNormalizedName(entity.getType());
	}
	String getItemDisplay(ItemStack item){
		String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
				? item.getItemMeta().getDisplayName() : TextUtils.getNormalizedName(item.getType());
		itemName = JunkUtils.getRarityColor(item, true) + itemName + ChatColor.RESET;
		return ITEM_DISPLAY_FORMAT.replace("${NAME}", itemName).replace("${AMOUNT}", ""+item.getAmount());
	}
	void sendTellraw(String target, String message){
		pl.getServer().dispatchCommand(pl.getServer().getConsoleSender(), "minecraft:tellraw "+target+" "+message);
	}
	void dropHead(Entity entity, EntityDeathEvent evt, Entity killer, ItemStack weapon){
		if(evt == null) entity.getWorld().dropItemNaturally(entity.getLocation(), pl.getAPI().getHead(entity));
		else evt.getDrops().add(pl.getAPI().getHead(entity));
		TellrawBlob message = new TellrawBlob();
		if(killer != null){
			if(weapon != null && weapon.getType() != Material.AIR){
				message.addComponent(MSH_BEHEAD_BY_WITH);
				String itemDisplay = getItemDisplay(weapon);
				String jsonData = JunkUtils.convertItemStackToJson(weapon, JSON_LIMIT);
				ActionComponent comp = new ActionComponent(itemDisplay, HoverEvent.SHOW_ITEM, jsonData);
				message.replaceRawTextWithComponent("${ITEM}", comp);
			}
			else message.addComponent(MSH_BEHEAD_BY);
			message.replaceRawTextWithComponent("${KILLER}", new SelectorComponent(killer.getUniqueId()));
		}
		else message.addComponent(MSG_BEHEAD);
		message.replaceRawTextWithComponent("${VICTIM}", new SelectorComponent(entity.getUniqueId()));

//		if(DEBUG_MODE) pl.getLogger().info("Tellraw message: "+message);
		if(DEBUG_MODE) pl.getLogger().info("Announce Mode: "+(entity instanceof Player ? ANNOUNCE_PLAYERS : ANNOUNCE_MOBS));

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

	@EventHandler(priority = EventPriority.LOWEST)
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
					timeSinceLastPlayerDamage(victim) > 60*1000
				)
			)
		)) return;

		final ItemStack itemInHand = (killer != null && killer instanceof LivingEntity
				? ((LivingEntity)killer).getEquipment().getItemInMainHand() : null);
		if(!mustUseTools.isEmpty() && (itemInHand == null || !mustUseTools.contains(itemInHand.getType()))) return;
		final int lootingLevel = itemInHand == null ? 0 : itemInHand.getEnchantmentLevel(Enchantment.LOOT_BONUS_MOBS);
		final double toolBonus = itemInHand == null ? 0D : toolBonuses.getOrDefault(itemInHand.getType(), 0D);
		final double lootingMod = 1D + lootingLevel*0.01D;
		final double weaponMod = 1D + toolBonus;
		final double spawnCauseMod = getSpawnCauseModifier(victim);
		final double rawDropChance = mobChances.getOrDefault(victim.getType(), DEFAULT_CHANCE);
		final double dropChance = rawDropChance*spawnCauseMod*lootingMod*weaponMod;

		// Remove vanilla-dropped wither skeleton skulls so they aren't dropped twice.
		if(evt.getEntityType() == EntityType.WITHER_SKELETON){
			Iterator<ItemStack> it = evt.getDrops().iterator();
			while(it.hasNext()) if(HeadUtils.isHead(it.next().getType())) it.remove();
			// However, if it is wearing a head in its helmet slot, don't remove the drop.
			// Note-to-self: Be careful with entity_equipment, heads on armor stands, etc.
			for(ItemStack i : EvUtils.getEquipmentGuaranteedToDrop(evt.getEntity())){
				if(i != null && HeadUtils.isHead(i.getType())) evt.getDrops().add(i);
			}
		}
		if(rand.nextDouble() < dropChance){
			dropHead(victim, evt, killer, itemInHand);
			if(DEBUG_MODE) pl.getLogger().info("Dropped Head: "+victim.getType().name()+"\n"
					+"Raw chance: "+rawDropChance*100D+"%, "
					+"SpawnReason Bonus: "+(spawnCauseMod-1D)*100D+"%, "
					+"Looting Bonus: "+(lootingMod-1D)*100D+"%, "
					+"Weapon Bonus: "+(weaponMod-1D)*100D+"%, "
					+"Final drop chance: "+dropChance*100D+"%");
		}
	}
}