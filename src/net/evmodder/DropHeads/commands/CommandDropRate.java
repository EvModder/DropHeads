package net.evmodder.DropHeads.commands;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.listeners.EntityDeathListener;
import net.evmodder.EvLib.EvCommand;
import net.evmodder.EvLib.FileIO;

public class CommandDropRate extends EvCommand{
	final DropHeads pl;
	EntityDeathListener deathListener;
	final DecimalFormat df;
	final boolean ONLY_SHOW_VALID_ENTITIES = true;
	final boolean USING_SPAWN_MODIFIERS, USING_LOOTING_MODIFIERS, USING_TIME_ALIVE_MODIFIERS, USING_TOOL_MODIFIERS,
			USING_REQUIRED_TOOLS, VANILLA_WITHER_SKELETON_LOOTING;
	final HashMap<String, Double> dropChances;
	final double DEFAULT_DROP_CHANCE;

	public CommandDropRate(DropHeads plugin, EntityDeathListener deathListener) {
		super(plugin);
		pl = plugin;
		this.deathListener = deathListener;
		USING_SPAWN_MODIFIERS = pl.getConfig().getBoolean("track-mob-spawns", true);
		USING_LOOTING_MODIFIERS = pl.getConfig().getDouble("looting-mutliplier") != 1.0 || pl.getConfig().getDouble("looting-addition") != 0;
		USING_TIME_ALIVE_MODIFIERS = !pl.getConfig().getConfigurationSection("time-alive-modifiers").getKeys(false).isEmpty();
		USING_TOOL_MODIFIERS = !pl.getConfig().getConfigurationSection("specific-tool-modifiers").getKeys(false).isEmpty();
		USING_REQUIRED_TOOLS = !pl.getConfig().getStringList("must-use").isEmpty();
		VANILLA_WITHER_SKELETON_LOOTING = pl.getConfig().getBoolean("vanilla-wither-skeleton-looting-behavior", true);

		//String defaultChances = FileIO.loadResource(pl, "head-drop-rates.txt"); // Already done in EntityDeathListener
		String chances = FileIO.loadFile("head-drop-rates.txt", "");
		dropChances = new HashMap<String, Double>();
		double chanceForUnknown = 0D;
		for(String line : chances.split("\n")){
			String[] parts = line.replace(" ", "").replace("\t", "").toUpperCase().split(":");
			if(parts.length < 2) continue;
			try{
				double dropChance = Double.parseDouble(parts[1]);
				if(parts[0].equals("UNKNOWN")) chanceForUnknown = dropChance;
				if(ONLY_SHOW_VALID_ENTITIES) EntityType.valueOf(parts[0]); // If entity does not exist, this drops to the catch below
				dropChances.put(parts[0].toLowerCase(), dropChance);
			}
			catch(NumberFormatException ex){}
			catch(IllegalArgumentException ex){}
		}
		DEFAULT_DROP_CHANCE = chanceForUnknown;
		df = new DecimalFormat("0.0###");
	}

	@Override public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args){
		if(args.length == 1){
			args[0] = args[0].toLowerCase();
			return dropChances.keySet().stream().filter(name -> name.startsWith(args[0])).collect(Collectors.toList());
		}
		return null;
	}

	Entity getTargetEntity(Entity looker, int range){
		Entity closestEntity = null;
		double closestDistSq = Double.MAX_VALUE;
		final double threshold = 1;
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
		Entity entity = null;
		if(args.length == 0){
			if(sender instanceof Player) entity = getTargetEntity((Player)sender, /*range=*/10);
			if(entity == null) return false;
		}
		String target = args[0].toLowerCase();

		if(entity == null) entity = pl.getServer().getPlayer(target);
		if(entity != null){
			target = entity.getType().name().toLowerCase();
			if(!entity.hasPermission("dropheads.canlosehead")){
				sender.sendMessage("§6Drop chance for "
						+(entity instanceof Player ? "\"§7"+entity.getName()+"§6\"" : "§e"+target+"§6")
						+": §b0% §7(dropheads.canlosehead=§cfalse§7)");
			}
			else{
				double dropChance = dropChances.getOrDefault(target, DEFAULT_DROP_CHANCE);
				if(USING_SPAWN_MODIFIERS) dropChance *= EntityDeathListener.getSpawnCauseModifier(entity);
				if(USING_TIME_ALIVE_MODIFIERS) dropChance *= (1D + deathListener.getTimeAliveBonus(entity));
				sender.sendMessage("§6Drop chance for "
						+(entity instanceof Player ? "\"§7"+entity.getName()+"§6\"" : "§e"+target+"§6")
						+": §b"+df.format(dropChance*100D)+"%");
			}
		}
		else{
			Double rawChance = dropChances.get(target);
			if(rawChance != null) sender.sendMessage("§6Drop chance for §e"+target+"§6: §b"+df.format(rawChance*100D)+"%");
			else{
				sender.sendMessage("§6Drop chance for \"§c"+target+"\"§6 not found! §7(unknown mobs default to §b0%§7)");
				return false;
			}
		}

		StringBuilder builder = new StringBuilder("§7Other things that can affect droprate: ");
		if(entity == null && target.equals("player")) builder.append("\nVictim must have 'dropheads.canlosehead' perm (default=true)");
		builder.append("\nKiller must have 'dropheads.canbehead' perm (default=true)");// (true/fase for you)
		builder.append("\nIf killer has 'dropheads.alwaysbehead', rate is raised to 100%");// (true/false for you)
		if(USING_REQUIRED_TOOLS) builder.append("\nSpecific murder weapons are required"/* {IRON_AXE, ...}*/);
		builder.append("\nRate modifiers: ");
		if(USING_SPAWN_MODIFIERS && entity == null && !target.equals("player")) builder.append("SpawnReason, "/* <red>-XX% to <green>+YY% */);
		if(USING_TIME_ALIVE_MODIFIERS && entity == null) builder.append("TimeAlive, "/* <red>-XX% to <green>+YY% */);
		if(USING_TOOL_MODIFIERS) builder.append("WeaponType, "/* <red>-XX% to <green>+YY% */);
		if(VANILLA_WITHER_SKELETON_LOOTING && target.equals("wither_skeleton")) builder.append("\nVanilla wither_skeleton looting rate is enabled");
		else if(USING_LOOTING_MODIFIERS) builder.append("Looting"/* (level 3: <green>+XX%) */);
		sender.sendMessage(builder.toString());
		return true;
	}
}