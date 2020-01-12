package net.evmodder.DropHeads.listeners;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import net.evmodder.DropHeads.DropHeads;
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
	}

	static long timeSinceLastPlayerDamage(Entity entity){
		long lastDamage = entity.hasMetadata("PlayerDamage") ? entity.getMetadata("PlayerDamage").get(0).asLong() : 0;
		return System.currentTimeMillis() - lastDamage;
	}
	static double getSpawnCauseModifier(Entity e){
		return e.hasMetadata("SpawnReason") ? e.getMetadata("SpawnReason").get(0).asDouble() : 1D;
	}

	@EventHandler
	public void entityDeathEvent(EntityDeathEvent evt){
		LivingEntity victim = evt.getEntity();
		if(playerHeadsOnly && victim instanceof Player == false) return;
		Entity killer = null;
		boolean killedByChargedCreeper = false, alwaysBeheadPerm = false;
		double lootingBonus = 0D, weaponBonus = 0D;
		EntityDamageEvent lastDamage = victim.getLastDamageCause();
		if(lastDamage != null && lastDamage instanceof EntityDamageByEntityEvent){
			killer = ((EntityDamageByEntityEvent)lastDamage).getDamager();
			if(killer instanceof Creeper && ((Creeper)killer).isPowered()) killedByChargedCreeper = true;
			//"else if" - Overrides "player-kills-only" - intentional design
			else if(playerKillsOnly && killer instanceof Player == false){
				if(allowProjectileKills && killer instanceof Projectile
						&& ((Projectile)killer).getShooter() instanceof Player);
				else if(allowIndirectKills && timeSinceLastPlayerDamage(victim) < 60*1000);
				else return;
			}
			if(!killer.hasPermission("dropheads.canbehead")) return;
			alwaysBeheadPerm = killer.hasPermission("dropheads.alwaysbehead");

			ItemStack itemInHand = null;
			if(killer instanceof LivingEntity){
				itemInHand = ((LivingEntity)killer).getEquipment().getItemInMainHand();
				if(itemInHand != null && !noLootingEffectMobs.contains(victim.getType())){
					lootingBonus = itemInHand.getEnchantmentLevel(Enchantment.LOOT_BONUS_MOBS)*0.01D;
					weaponBonus = toolBonuses.getOrDefault(itemInHand.getType(), 0D);
				}
			}
			if(!mustUseTools.isEmpty() && (itemInHand == null || !mustUseTools.contains(itemInHand.getType()))) return;
		}
		else if(playerKillsOnly) return; // Not damaged by EntityDamageByEntityEvent, so not considered a by-player kill 

		double rawDropChance = mobChances.getOrDefault(victim.getType(), DEFAULT_CHANCE);
		double spawnCauseMod = getSpawnCauseModifier(victim);
		double dropChanceWithSpawnMod =
				((CHARGED_CREEPER_DROPS && killedByChargedCreeper) || alwaysBeheadPerm) ? 1D :
				Math.min(rawDropChance*spawnCauseMod, 1D);

		double dropChance = dropChanceWithSpawnMod;
		if(useTaylorModifiers){
			lootingBonus = Math.pow(2D, 40*lootingBonus*dropChance)-1D;
			if(lootingBonus > 0D) dropChance += (lootingBonus*(1D-dropChance))/(lootingBonus+1D);//apply modifiers
		}
		else dropChance += lootingBonus*dropChance;
		dropChance += weaponBonus*dropChance;
		dropChance = Math.min(dropChance, 1D);

		//Remove vanilla charged creeper head drops & wskulls (TODO: test this)
		if(killedByChargedCreeper || evt.getEntityType() == EntityType.WITHER_SKELETON){
			Iterator<ItemStack> it = evt.getDrops().iterator();
			while(it.hasNext()) if(EvUtils.isHead(it.next().getType())) it.remove();
			for(ItemStack i : EvUtils.getEquipmentGuaranteedToDrop(evt.getEntity())){
				if(i != null && EvUtils.isHead(i.getType())) evt.getDrops().add(i);
			}
		}

		if(rand.nextDouble() < dropChance){
			victim.getWorld().dropItem(victim.getLocation(), pl.getAPI().getHead(victim));
			if(DEBUG_MODE){
				pl.getLogger().info("Dropped Head: "+victim.getType().name()
					+"\nRaw chance: "+rawDropChance
					+", SpawnReason Modifier: "+spawnCauseMod
					+", Looting Bonus: "+lootingBonus+", Weapon Bonus: "+weaponBonus
					+", Charged Creeper: "+killedByChargedCreeper+", Always-Behead Perm: "+alwaysBeheadPerm
					+", Final drop chance: "+dropChance);
			}
		}
	}
}