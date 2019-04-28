package net.evmodder.DropHeads.listeners;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import Evil_Code_EvKits.EvKits;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.EvLib.FileIO;

public class EntityDeathListener implements Listener{
	final DropHeads pl;
	final boolean playerKillsOnly, playerHeadsOnly, useTaylorModifiers, chargedCreepers;
	final Set<Material> mustUseTools = new HashSet<Material>();
	final Set<EntityType> noLootingEffectMobs = new HashSet<EntityType>();
	final Map<EntityType, Float> mobChances = new HashMap<EntityType, Float>();
	final Map<Material, Float> toolBonuses = new HashMap<Material, Float>();
	final double lootingBonus;
	final Random rand;

	public EntityDeathListener(){
		pl = DropHeads.getPlugin();
		playerKillsOnly = pl.getConfig().getBoolean("player-kills-only", true);
		playerHeadsOnly = pl.getConfig().getBoolean("player-heads-only", false);
		useTaylorModifiers = pl.getConfig().getBoolean("use-taylor-modifiers", true);
		chargedCreepers = pl.getConfig().getBoolean("charged-creeper-drops", true);
		lootingBonus = pl.getConfig().getDouble("looting-bonus", 0.4);
		rand = new Random();

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

		ConfigurationSection specificModifiers = pl.getConfig().getConfigurationSection("specific-tool-modifiers");
		if(specificModifiers != null) for(String toolName : specificModifiers.getKeys(false)){
			Material mat = Material.getMaterial(toolName.toUpperCase());
			if(mat != null) toolBonuses.put(mat, (float) specificModifiers.getDouble(toolName));
		}

		//Load individual mobs' drop chances
		InputStream defaultChances = pl.getClass().getResourceAsStream("/default chances.txt");
		String chances = FileIO.loadFile("drop chances.txt", defaultChances);

		for(String line : chances.split("\n")){
			line = line.replace(" ", "").replace("\t", "").replace("//", "#").split("#")[0].toUpperCase();
			String[] parts = line.split(":");
			if(parts.length > 1){
				try{
					EntityType eType = EntityType.valueOf(parts[0]);
					float dropChance = Float.parseFloat(parts[1]);
					mobChances.put(eType, dropChance);
					if(parts.length > 2){
						if(parts[2].equalsIgnoreCase("NoLooting")) noLootingEffectMobs.add(eType);
					}
					// Commented out to allow >1 because Spawn-Reason modifiers are added after this
					/*if(dropChance > 1F){
						pl.getLogger().severe("Invalid value: "+parts[1]);
						pl.getLogger().severe("Drop chance must be between 0 and 1");
						mobChances.put(eType, Math.min(dropChance/10F, 1F));
					}*/
				}
				catch(NumberFormatException ex){pl.getLogger().severe("Invalid value: "+parts[1]);}
				catch(IllegalArgumentException ex){pl.getLogger().severe("Unknown entity type: "+parts[0]);}
			}
		}
	}

	@EventHandler
	public void entityDeathEvent(EntityDeathEvent evt){
		//If in an arena, do not drop heads //TODO: NOTE: Ev-specific arena
		EvKits evKitPVP = (EvKits) pl.getServer().getPluginManager().getPlugin("EvKitPvP");
		if(evKitPVP != null && evKitPVP.isEnabled()
				&& evKitPVP.isInArena(evt.getEntity().getLocation(), false) != null) return;
		//================================================================

		double rawDropChance, dropChance;
		double lootBonus = 0D, toolBonus = 0D;
		double spawnCauseModifier = evt.getEntity().hasMetadata("SpawnReason") ?
				evt.getEntity().getMetadata("SpawnReason").get(0).asDouble() : 1D;

		if(playerHeadsOnly && evt.getEntity() instanceof Player == false) return;

		Entity killer = null;
		EntityDamageEvent lastDamage = evt.getEntity().getLastDamageCause();
		if(lastDamage != null && lastDamage instanceof EntityDamageByEntityEvent){
			killer = ((EntityDamageByEntityEvent)lastDamage).getDamager();

			if(chargedCreepers && killer instanceof Creeper && ((Creeper)killer).isPowered()) dropChance = 1D;
			else if(playerKillsOnly && killer instanceof Player == false) return;
			else if(!killer.hasPermission("evp.dropheads.canbehead")) return;
			else{
				ItemStack heldItem = null;
				if(killer instanceof LivingEntity){
					heldItem = ((LivingEntity)killer).getEquipment().getItemInMainHand();
					if(heldItem != null){
						lootBonus = heldItem.getEnchantmentLevel(Enchantment.LOOT_BONUS_MOBS)*lootingBonus;
						if(toolBonuses.containsKey(heldItem.getType())) toolBonus = toolBonuses.get(heldItem.getType());
					}
				}
				if(!mustUseTools.isEmpty() && (heldItem == null || !mustUseTools.contains(heldItem.getType()))) return;

				if(killer.hasPermission("evp.dropheads.alwaysbehead")) dropChance = spawnCauseModifier = 1D;
				else dropChance = mobChances.containsKey(evt.getEntityType()) ? mobChances.get(evt.getEntityType()) : 0D;
			}
		}
		else if(playerKillsOnly) return;
		else dropChance = mobChances.containsKey(evt.getEntityType()) ? mobChances.get(evt.getEntityType()) : 0D;

		rawDropChance = (dropChance *= spawnCauseModifier);

		if(useTaylorModifiers){
			toolBonus = Math.pow(2D, toolBonus*dropChance)-1D;
			lootBonus = Math.pow(2D, lootBonus*dropChance)-1D;
			dropChance += (lootBonus == 0D ? 0D : (lootBonus*(1D-dropChance))/(lootBonus+1D));//apply modifiers
			dropChance += (toolBonus == 0D ? 0D : (toolBonus*(1D-dropChance))/(toolBonus+1D));
		}
		else dropChance += lootBonus*dropChance + toolBonus*dropChance;

		if(rand.nextDouble() < dropChance){
			evt.getEntity().getWorld().dropItem(evt.getEntity().getLocation(), pl.getAPI().getHead(evt.getEntity()));

			pl.getLogger().info("Head dropped!\nDrop chance before tool modifiers: "+rawDropChance+
									"\nDrop chance after tool modifiers: "+dropChance+
									"\nMob killed: "+evt.getEntityType().name());
		}
	}
}