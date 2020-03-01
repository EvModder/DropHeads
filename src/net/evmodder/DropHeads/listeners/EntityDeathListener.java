package net.evmodder.DropHeads.listeners;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.UUID;
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
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.HeadUtils;
import net.evmodder.EvLib.EvUtils;
import net.evmodder.EvLib.FileIO;

public class EntityDeathListener implements Listener{
	final DropHeads pl;
	final boolean playerKillsOnly, allowIndirectKills, allowProjectileKills; 
	final boolean playerHeadsOnly, useTaylorModifiers, CHARGED_CREEPER_DROPS;
	final HashSet<Material> mustUseTools;
	final HashSet<EntityType> noLootingEffectMobs;
	final HashMap<EntityType, Double> mobChances;
	final HashMap<Material, Double> toolBonuses;
	final HashSet<UUID> explodingChargedCreepers;
	double DEFAULT_CHANCE;
	final Random rand;
	final boolean DEBUG_MODE;

	public EntityDeathListener(){
		pl = DropHeads.getPlugin();
		playerKillsOnly = pl.getConfig().getBoolean("player-kills-only", true);
		allowIndirectKills = pl.getConfig().getBoolean("drop-for-indirect-kills", false);
		allowProjectileKills = pl.getConfig().getBoolean("drop-for-ranged-kills", false);
		playerHeadsOnly = pl.getConfig().getBoolean("player-heads-only", false);
		useTaylorModifiers = pl.getConfig().getBoolean("use-taylor-modifiers", true);
		CHARGED_CREEPER_DROPS = pl.getConfig().getBoolean("charged-creeper-drops", true);
		DEBUG_MODE = pl.getConfig().getBoolean("debug-messages", true);
		rand = new Random();

		mustUseTools = new HashSet<Material>();
		if(pl.getConfig().getBoolean("must-use-axe")){
			mustUseTools.add(Material.DIAMOND_AXE);
			mustUseTools.add(Material.IRON_AXE);
			mustUseTools.add(Material.GOLDEN_AXE);
			mustUseTools.add(Material.STONE_AXE);
			mustUseTools.add(Material.WOODEN_AXE);
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
		for(String line : chances.split("\n")){
			String[] parts = line.replace(" ", "").replace("\t", "").toUpperCase().split(":");
			if(parts.length < 2) continue;
			try{
				if(parts[0].equals("UNKNOWN")){
					DEFAULT_CHANCE = Float.parseFloat(parts[1]);
					continue;
				}
				EntityType eType = EntityType.valueOf(parts[0]);
				double dropChance = Double.parseDouble(parts[1]);
				mobChances.put(eType, dropChance);
				if(parts.length > 2 && parts[2].equals("NOLOOTING")) noLootingEffectMobs.add(eType);
				// Commented out to allow >1 because Spawn-Reason modifiers are added after this
				/*if(dropChance > 1F){
					pl.getLogger().severe("Invalid value: "+parts[1]);
					pl.getLogger().severe("Drop chance must be between 0 and 1");
					mobChances.put(eType, Math.min(dropChance/10F, 1F));
				}*/
			}
			catch(NumberFormatException ex){pl.getLogger().severe("Invalid value: "+parts[1]);}
			catch(IllegalArgumentException ex){
				pl.getLogger().severe("Unknown entity type: "+parts[0]);
			}
		}
		explodingChargedCreepers = new HashSet<UUID>();
	}

	static long timeSinceLastPlayerDamage(Entity entity){
		long lastDamage = entity.hasMetadata("PlayerDamage") ? entity.getMetadata("PlayerDamage").get(0).asLong() : 0;
		return System.currentTimeMillis() - lastDamage;
	}
	static double getSpawnCauseModifier(Entity e){
		return e.hasMetadata("SpawnReason") ? e.getMetadata("SpawnReason").get(0).asDouble() : 1D;
	}

	void doHeadDrop(EntityDeathEvent evt){
		if(DEBUG_MODE) pl.getLogger().info("Dropped Head: "+evt.getEntity().getType());
		//entity.getWorld().dropItem(entity).getLocation(), pl.getAPI().getHead(entity));
		evt.getDrops().add(pl.getAPI().getHead(evt.getEntity()));
	}

	@EventHandler
	public void entityDeathEvent(EntityDeathEvent evt){
		final LivingEntity victim = evt.getEntity();
		if(playerHeadsOnly && victim instanceof Player == false) return;
		Entity killer = null;
		if(victim.getLastDamageCause() != null && victim.getLastDamageCause() instanceof EntityDamageByEntityEvent){
			killer = ((EntityDamageByEntityEvent)victim.getLastDamageCause()).getDamager();
			if(!killer.hasPermission("dropheads.canbehead")) return;
			if(killer instanceof Creeper && ((Creeper)killer).isPowered()){
				if(CHARGED_CREEPER_DROPS && !HeadUtils.dropsHeadFromChargedCreeper(victim.getType())
						&& explodingChargedCreepers.add(killer.getUniqueId())){
					if(DEBUG_MODE) pl.getLogger().info("Killed by charged creeper: "+victim.getType());
					doHeadDrop(evt);
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
				doHeadDrop(evt);
				return;
			}
		}
		// Check if killer is not a player.
		if(playerKillsOnly && (killer == null ||
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
			while(it.hasNext()) if(EvUtils.isHead(it.next().getType())) it.remove();
			// However, if it is wearing a head in its helmet slot, don't remove the drop.
			// Note-to-self: Be careful with entity_equipment, heads on armor stands, etc.
			for(ItemStack i : EvUtils.getEquipmentGuaranteedToDrop(evt.getEntity())){
				if(i != null && EvUtils.isHead(i.getType())) evt.getDrops().add(i);
			}
		}
		if(rand.nextDouble() < dropChance){
			doHeadDrop(evt);
			if(DEBUG_MODE) pl.getLogger().info("Dropped Head: "+victim.getType().name()+"\n"
					+"Raw chance: "+rawDropChance*100D+"%, "
					+"SpawnReason Bonus: "+(spawnCauseMod-1D)*100D+"%, "
					+"Looting Bonus: "+(lootingMod-1D)*100D+"%, "
					+"Weapon Bonus: "+(weaponMod-1D)*100D+"%, "
					+"Final drop chance: "+dropChance*100D+"%");
		}
	}
}