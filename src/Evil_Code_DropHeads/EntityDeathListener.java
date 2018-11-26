package Evil_Code_DropHeads;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
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

import EvLibD.FileIO;
import Evil_Code_EvKits.EvKits;

public class EntityDeathListener implements Listener{
	private DropHeads plugin;
	private boolean playerKillsOnly, playerHeadsOnly, useTaylorModifiers;
	private Set<Material> mustUseTools = new HashSet<Material>();
	private Set<EntityType> noLootingEffectMobs = new HashSet<EntityType>();
	private Map<EntityType, Float> mobChances = new HashMap<EntityType, Float>();
	private Map<Material, Float> toolBonuses = new HashMap<Material, Float>();
	private double lootingBonus = 0.4;
	Random rand = new Random();

	public EntityDeathListener(){
		plugin = DropHeads.getPlugin();
		playerKillsOnly = plugin.getConfig().getBoolean("player-kills-only", true);
		playerHeadsOnly = plugin.getConfig().getBoolean("player-heads-only", false);
		useTaylorModifiers = plugin.getConfig().getBoolean("use-taylor-modifiers", true);
		lootingBonus = plugin.getConfig().getDouble("looting-bonus", 0.4);

		if(plugin.getConfig().getBoolean("must-use-axe")){
			mustUseTools.add(Material.DIAMOND_AXE);
			mustUseTools.add(Material.IRON_AXE);
			mustUseTools.add(Material.GOLD_AXE);
			mustUseTools.add(Material.STONE_AXE);
			mustUseTools.add(Material.WOOD_AXE);
		}
		else for(String toolName : plugin.getConfig().getStringList("must-use")){
			if(toolName.isEmpty()) continue;
			Material mat = Material.getMaterial(toolName.toUpperCase());
			if(mat != null) mustUseTools.add(mat);
			else plugin.getLogger().warning("Unknown Tool \""+toolName+"\"!");
		}

		ConfigurationSection specificModifiers = plugin.getConfig().getConfigurationSection("specific-tool-modifiers");
		if(specificModifiers != null) for(String toolName : specificModifiers.getKeys(false)){
			Material mat = Material.getMaterial(toolName.toUpperCase());
			if(mat != null) toolBonuses.put(mat, (float) specificModifiers.getDouble(toolName));
		}

		//Load individual mobs' drop chances
		String chances = FileIO.loadFile("drop chances.txt", plugin.getClass().getResourceAsStream("/default chances.txt"));
//		String chances = FileIO.loadFile("drop chances.txt", "");
//		if(chances.isEmpty()){
//			chances = FileIO.loadResource(plugin, "default chances.txt");
//			FileIO.saveFile("drop chances.txt", chances);
//		}
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
					// Allow >1 because Spawn-Reason modifiers are added after this
					/*if(dropChance > 1F){
						plugin.getLogger().severe("Invalid value: "+parts[1]);
						plugin.getLogger().severe("Drop chance must be between 0 and 1");
						mobChances.put(eType, Math.min(dropChance/10F, 1F));
					}*/
				}
				catch(NumberFormatException ex){plugin.getLogger().severe("Invalid value: "+parts[1]);}
				catch(IllegalArgumentException ex){plugin.getLogger().severe("Unknown entity type: "+parts[0]);}
			}
		}
	}

	@EventHandler
	public void entityDeathEvent(EntityDeathEvent evt){
		//If in an arena, do not drop heads //TODO: NOTE: Ev-specific arena
		EvKits evKitPVP = (EvKits) plugin.getServer().getPluginManager().getPlugin("EvKitPvP");
		if(evKitPVP != null && evKitPVP.isEnabled() && evKitPVP.isInArena(evt.getEntity().getLocation(), false) != null) return;
		//================================================================

		double rawDropChance, dropChance;
		double lootBonus = 0, toolBonus = 0;
		double spawnCauseModifier = evt.getEntity().hasMetadata("SpawnReason") ?
				evt.getEntity().getMetadata("SpawnReason").get(0).asDouble() : 1D;

		if(playerHeadsOnly && evt.getEntity() instanceof Player == false) return;

		Entity killer = null;
		EntityDamageEvent lastDamage = evt.getEntity().getLastDamageCause();
		if(lastDamage != null && lastDamage instanceof EntityDamageByEntityEvent){
			killer = ((EntityDamageByEntityEvent)lastDamage).getDamager();
			if(playerKillsOnly && killer instanceof Player == false) return;
			if(!killer.hasPermission("evp.dropheads.canbehead")) return;

			ItemStack heldItem = null;
			if(killer instanceof LivingEntity){
				heldItem = ((LivingEntity)killer).getEquipment().getItemInMainHand();
				lootBonus = heldItem.getEnchantmentLevel(Enchantment.LOOT_BONUS_MOBS)*lootingBonus;
				if(toolBonuses.containsKey(heldItem.getType())) toolBonus = toolBonuses.get(heldItem.getType());
			}
			if(!mustUseTools.isEmpty() && (heldItem == null || !mustUseTools.contains(heldItem.getType()))) return;

			if(killer.hasPermission("evp.dropheads.alwaysbehead")) rawDropChance = dropChance = 1;
			else dropChance = mobChances.containsKey(evt.getEntityType()) ? mobChances.get(evt.getEntityType()) : 0D;
		}
		else if(playerKillsOnly) return;
		else dropChance = mobChances.containsKey(evt.getEntityType()) ? mobChances.get(evt.getEntityType()) : 0D;

		rawDropChance = (dropChance *= spawnCauseModifier);

		if(useTaylorModifiers){
			toolBonus = Math.pow(2, toolBonus*dropChance)-1;
			lootBonus = Math.pow(2, lootBonus*dropChance)-1;
			dropChance += (lootBonus == 0 ? 0 : (lootBonus*(1-dropChance))/(lootBonus+1));//apply modifiers
			dropChance += (toolBonus == 0 ? 0 : (toolBonus*(1-dropChance))/(toolBonus+1));
		}
		else dropChance += lootBonus*dropChance + toolBonus*dropChance;

		if(rand.nextDouble() < dropChance){
			evt.getEntity().getWorld().dropItem(evt.getEntity().getLocation(), Utils.getHead(evt.getEntity()));

			plugin.getLogger().info("Head dropped!\nDrop chance before modifiers: "+rawDropChance+
									"\nDrop chance after modifiers: "+dropChance+
									"\nMob killed: "+evt.getEntityType().name());
		}
	}
}