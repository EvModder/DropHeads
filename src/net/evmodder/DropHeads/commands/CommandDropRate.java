package net.evmodder.DropHeads.commands;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.JunkUtils;
import net.evmodder.DropHeads.listeners.EntityDeathListener;
import net.evmodder.EvLib.EvCommand;
import net.evmodder.EvLib.FileIO;
import net.evmodder.EvLib.extras.TellrawUtils.Component;
import net.evmodder.EvLib.extras.TellrawUtils.ListComponent;
import net.evmodder.EvLib.extras.TellrawUtils.TranslationComponent;
import net.evmodder.EvLib.extras.TellrawUtils.RawTextComponent;

public class CommandDropRate extends EvCommand{
	final private DropHeads pl;
	EntityDeathListener deathListener;
	final boolean ONLY_SHOW_VALID_ENTITIES = true;
	final boolean USING_SPAWN_MODIFIERS, USING_REQUIRED_WEAPONS, USING_LOOTING_MODIFIERS, USING_TIME_ALIVE_MODIFIERS, VANILLA_WITHER_SKELETON_LOOTING;
	final HashSet<String> entityNames;
	final HashMap<Material, Double> weaponBonuses;
	final double DEFAULT_DROP_CHANCE;
	final double LOOTING_ADD, LOOTING_MULT;
	
	final String RAW_DROP_CHANCE_FOR = "§6Drop chance for §e%s§6: §b%s%%";
	final String DROP_CHANCE_FOR_NOT_FOUND = "§6Drop chance for \"§c%s§6\" not found! §7(defaults to §b0%%§7)";
	final String FINAL_DROP_CHANCE = "\n§6Final drop chance: §b%s%%";

	final Component OTHER_THINGS_THAT_AFFECT_DROPRATE = new RawTextComponent("§7Other things affecting the droprate:");
	final Component VICTIM_MUST_HAVE_PERM = new RawTextComponent("\n§c§l>§7 Victim must have the '§cdropheads.canlosehead§7' perm");
	final Component KILLER_MUST_HAVE_PERM = new RawTextComponent("\n§c§l>§7 You must have the '§cdropheads.canbehead§7' perm");
	final Component ALWAYS_BEHEAD_PERM = new RawTextComponent("\n§c§l>§7 You have '§cdropheads.alwaysbehead§7' so droprate is §b100%");
	final String SPECIFIC_WEAPONS_TEXT = "\n§c§l>§7 Weapons that can behead: [§f%s§7]";
	Component SPECIFIC_WEAPONS;
	final Component RATE_MULTIPLIERS_HEADER = new RawTextComponent("\n§7Multipliers: ");
	final Component SPAWN_REASON = new RawTextComponent("§fSpawnReason");
	final Component TIME_ALIVE = new RawTextComponent("§fTimeAlive");
	final Component WEAPON_TYPE = new RawTextComponent("§fWeapon");
	final Component PERMS = new RawTextComponent("§fPerms");
	final TranslationComponent LOOTING_COMP = new TranslationComponent("enchantment.minecraft.looting");
	final Component VANILLA_WITHER_SKELETON_BEHAVIOR_ALERT = new RawTextComponent("\n§7Vanilla wither_skeleton looting rate is enabled");

	public CommandDropRate(DropHeads plugin, EntityDeathListener deathListener) {
		super(plugin);
		pl = plugin;
		this.deathListener = deathListener;
		if(USING_REQUIRED_WEAPONS = !deathListener.mustUseTools.isEmpty()){
			ListComponent requiredWeapons = new ListComponent();
			boolean isFirstElement = true;
			for(Material mat : deathListener.mustUseTools){
				if(!isFirstElement) requiredWeapons.addComponent(new RawTextComponent("§7, §f"));
				else isFirstElement = false;
				requiredWeapons.addComponent(new TranslationComponent("item.minecraft."+mat.name().toLowerCase()));
			}
			SPECIFIC_WEAPONS = new TranslationComponent(SPECIFIC_WEAPONS_TEXT, requiredWeapons);
		}
		USING_SPAWN_MODIFIERS = pl.getConfig().getBoolean("track-mob-spawns", true);
		LOOTING_ADD = pl.getConfig().getDouble("looting-addition", 0.01D);
		LOOTING_MULT = pl.getConfig().getDouble("looting-multiplier", pl.getConfig().getDouble("looting-mutliplier", 1D));
		USING_LOOTING_MODIFIERS = LOOTING_MULT != 1D || LOOTING_ADD != 0D;
		USING_TIME_ALIVE_MODIFIERS = pl.getConfig().isConfigurationSection("time-alive-modifiers")
				&& !pl.getConfig().getConfigurationSection("time-alive-modifiers").getKeys(false).isEmpty();
		VANILLA_WITHER_SKELETON_LOOTING = pl.getConfig().getBoolean("vanilla-wither-skeleton-looting-behavior", true);

		//String defaultChances = FileIO.loadResource(pl, "head-drop-rates.txt"); // Already done in EntityDeathListener
		String chances = FileIO.loadFile("head-drop-rates.txt", "");
		entityNames = new HashSet<String>();
		double chanceForUnknown = 0D;
		for(String line : chances.split("\n")){
			String[] parts = line.replace(" ", "").replace("\t", "").toUpperCase().split(":");
			if(parts.length < 2) continue;
			parts[0] = parts[0].replace("DEFAULT", "UNKNOWN");
			try{
				final double dropChance = Double.parseDouble(parts[1]);
				if(parts[0].equals("UNKNOWN")) chanceForUnknown = dropChance;
				if(ONLY_SHOW_VALID_ENTITIES){
					int dataTagSep = parts[0].indexOf('|');
					String eName = dataTagSep == -1 ? parts[0] : parts[0].substring(0, dataTagSep);
					EntityType.valueOf(eName); // If entity does not exist, this drops to the catch below
				}
				entityNames.add(parts[0]);
			}
			catch(NumberFormatException ex){}
			catch(IllegalArgumentException ex){}
		}
		DEFAULT_DROP_CHANCE = chanceForUnknown;

		weaponBonuses = new HashMap<Material, Double>();
		ConfigurationSection specificToolModifiers = pl.getConfig().getConfigurationSection("specific-tool-modifiers");
		if(specificToolModifiers != null) for(String toolName : specificToolModifiers.getKeys(false)){
			Material mat = Material.getMaterial(toolName.toUpperCase());
			if(mat != null) weaponBonuses.put(mat, specificToolModifiers.getDouble(toolName));
		}
	}

	@Override public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args){
		if(args.length == 1){
			args[0] = args[0].toUpperCase();
			return entityNames.stream().filter(name -> name.startsWith(args[0])).collect(Collectors.toList());
		}
		return null;
	}

	Entity getTargetEntity(Entity looker, int range){
		Entity closestEntity = null;
		double closestDistSq = Double.MAX_VALUE;
		final double threshold = 10;
		for(Entity entity : looker.getNearbyEntities(range, range, range)){
			Vector n = entity.getLocation().toVector().subtract(looker.getLocation().toVector());
			if(entity.getLocation().getDirection().normalize().crossProduct(n).lengthSquared() < threshold
					&& n.normalize().dot(looker.getLocation().getDirection().normalize()) >= 0){
				double distSq = entity.getLocation().distanceSquared(looker.getLocation());
				if(distSq < closestDistSq){
					closestDistSq = distSq;
					closestEntity = entity;
				}
			}
		}
		return closestEntity;
	}

	@SuppressWarnings("deprecation") @Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
		DecimalFormat df = new DecimalFormat("0.0####");
		ItemStack weapon = sender instanceof Player ? ((Player)sender).getInventory().getItemInMainHand() : null;
		Entity entity = null;
		Double rawChance = 0D;
		if(args.length == 0){
			if(sender instanceof Player){
				entity = getTargetEntity((Player)sender, /*range=*/10);
			}
			if(entity == null) return false;
		}
		final String target = entity == null ? args[0].toUpperCase() : entity.getType().name().toUpperCase();

		if(entity == null) entity = pl.getServer().getPlayer(target);
		if(entity != null){
			if(!entity.hasPermission("dropheads.canlosehead")){
				sender.sendMessage(String.format(RAW_DROP_CHANCE_FOR, entity instanceof Player ? entity.getName() : target, 0)
						+" §7(dropheads.canlosehead=§cfalse§7)");
			}
			else{
				rawChance = deathListener.getRawDropChance(entity);
				sender.sendMessage(String.format(RAW_DROP_CHANCE_FOR, entity instanceof Player ? entity.getName() : target, df.format(rawChance*100D)));
			}
		}
		else{
			rawChance = deathListener.getRawDropChance(target);
			if(rawChance != DEFAULT_DROP_CHANCE){
				sender.sendMessage(String.format(RAW_DROP_CHANCE_FOR, target, df.format(rawChance*100D), df.format(rawChance*100D)));
			}
			else{
				sender.sendMessage(String.format(DROP_CHANCE_FOR_NOT_FOUND, target));
				return false;
			}
		}

		ListComponent droprateDetails = new ListComponent(OTHER_THINGS_THAT_AFFECT_DROPRATE);
		//if(entity == null && target.equals("PLAYER")) builder.append("§7").append(VICTIM_MUST_HAVE_PERM);
		if(entity != null && !entity.hasPermission("dropheads.canlosehead")){rawChance=-1D;droprateDetails.addComponent(VICTIM_MUST_HAVE_PERM);}
		if(!sender.hasPermission("dropheads.canbehead")){rawChance=-1D;droprateDetails.addComponent(KILLER_MUST_HAVE_PERM);}
		if(sender.hasPermission("dropheads.alwaysbehead")){rawChance=-1D;droprateDetails.addComponent(ALWAYS_BEHEAD_PERM);}
		if(USING_REQUIRED_WEAPONS && (weapon == null || !deathListener.mustUseTools.contains(weapon.getType()))){
			rawChance=-1D;droprateDetails.addComponent(SPECIFIC_WEAPONS);
		}

		// Multipliers:
		final double weaponMod = weapon == null ? 1D : 1D+weaponBonuses.getOrDefault(weapon.getType(), 0D);
		final int lootingLevel = weapon == null ? 0 : weapon.getEnchantmentLevel(Enchantment.LOOT_BONUS_MOBS);
		final boolean VANILLA_LOOTING = target.equals("WITHER_SKELETON") && VANILLA_WITHER_SKELETON_LOOTING;
		final double lootingMod = (lootingLevel == 0 || VANILLA_LOOTING) ? 1D : Math.min(Math.pow(LOOTING_MULT, lootingLevel), LOOTING_MULT*lootingLevel);
		final double lootingAdd = (VANILLA_LOOTING ? 0.01D : LOOTING_ADD)*lootingLevel;
		final double timeAliveMod = entity == null ? 1D : 1D + deathListener.getTimeAliveBonus(entity);
		final double spawnCauseMod = entity == null ? 1D : JunkUtils.getSpawnCauseModifier(entity);
		final double permMod = deathListener.getPermsBasedDropRateModifier(sender);
		final double finalDropChance = rawChance*spawnCauseMod*timeAliveMod*weaponMod*lootingMod*permMod + lootingAdd;
		df = new DecimalFormat("0.##");
		ListComponent droprateMultipliers = new ListComponent();
		if(USING_SPAWN_MODIFIERS){
			if(entity == null){
				droprateMultipliers.addComponent(SPAWN_REASON);
				droprateMultipliers.addComponent("§7, ");
			}
			else if(Math.abs(1D-spawnCauseMod) > 0.001D){
				droprateMultipliers.addComponent(SPAWN_REASON);
				droprateMultipliers.addComponent(":§6x"+df.format(spawnCauseMod)+"§7, ");
			}
		}
		if(USING_TIME_ALIVE_MODIFIERS){
			if(entity == null){
				droprateMultipliers.addComponent(TIME_ALIVE);
				droprateMultipliers.addComponent("§7, ");
			}
			else if(Math.abs(1D-timeAliveMod) > 0.001D){
				droprateMultipliers.addComponent(TIME_ALIVE);
				droprateMultipliers.addComponent(":§6x"+df.format(timeAliveMod)+"§7, ");
			}
		}
		if(!weaponBonuses.isEmpty()){
			droprateMultipliers.addComponent(WEAPON_TYPE);
			if(Math.abs(1D-weaponMod) > 0.001D){
				droprateMultipliers.addComponent(JunkUtils.getMurderItemComponent(weapon, deathListener.JSON_LIMIT));
				droprateMultipliers.addComponent(":§6x"+df.format(weaponMod));
			}
			droprateMultipliers.addComponent("§7, ");
		}
		if(Math.abs(1D-permMod) > 0.001D){
			droprateMultipliers.addComponent(PERMS);
			droprateMultipliers.addComponent(":§6x"+df.format(permMod)+"§7, ");
		}
		if(VANILLA_WITHER_SKELETON_LOOTING && target.equals("WITHER_SKELETON")){
			droprateMultipliers.addComponent(VANILLA_WITHER_SKELETON_BEHAVIOR_ALERT);
		}
		else if(USING_LOOTING_MODIFIERS){
			droprateMultipliers.addComponent(LOOTING_COMP);
			if(lootingLevel > 0){
				String lootingMsg = lootingLevel+":";
				if(Math.abs(1D-lootingMod) > 0.001D) lootingMsg += "§6x"+df.format(lootingMod);
				if(Math.abs(lootingAdd) > 0.001D) lootingMsg += "§e"+(lootingAdd > 0 ? '+' : '-')+df.format(lootingAdd*100)+"%";
				droprateMultipliers.addComponent(lootingMsg);
			}
		}
		if(!droprateMultipliers.isEmpty()){
			droprateDetails.addComponent(RATE_MULTIPLIERS_HEADER);
			droprateDetails.addComponent(droprateMultipliers);
		}
		if(entity != null && rawChance > 0D && Math.abs(finalDropChance-rawChance) > 0.001D){
			df = new DecimalFormat("0.0####");
			droprateDetails.addComponent(String.format(FINAL_DROP_CHANCE, df.format(finalDropChance*100D)));
		}
		pl.getServer().dispatchCommand(pl.getServer().getConsoleSender(), "minecraft:tellraw "+sender.getName()+" "+droprateDetails.toString());
		return true;
	}
}