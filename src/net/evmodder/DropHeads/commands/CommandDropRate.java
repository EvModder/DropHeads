package net.evmodder.DropHeads.commands;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.EvLib.EvCommand;
import net.evmodder.EvLib.FileIO;

public class CommandDropRate extends EvCommand{
	final private DropHeads pl;
	final boolean ONLY_SHOW_VALID_ENTITIES = true;
	final boolean USING_SPAWN_MODIFIERS, USING_LOOTING_MODIFIERS, USING_TIME_ALIVE_MODIFIERS, USING_TOOL_MODIFIERS,
			USING_REQUIRED_TOOLS, VANILLA_WITHER_SKELETON_LOOTING;
	final HashMap<String, Double> dropChances;
	final double DEFAULT_DROP_CHANCE;

	public CommandDropRate(DropHeads plugin) {
		super(plugin);
		pl = plugin;
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
	}

	@Override public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args){
		if(args.length == 1){
			args[0] = args[0].toLowerCase();
			return dropChances.keySet().stream().filter(name -> name.startsWith(args[0])).collect(Collectors.toList());
		}
		return null;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
		String target = args[0].toLowerCase();
		DecimalFormat df = new DecimalFormat("0.0###");

		@SuppressWarnings("deprecation")
		Player player = pl.getServer().getPlayer(target);
		if(player != null){
			if(!player.hasPermission("dropheads.canlosehead")){
				sender.sendMessage(ChatColor.GOLD+"Drop chance for \""+player.getName()+"\": 0% "
					+ChatColor.GRAY+"(dropheads.canlosehead="+ChatColor.RED+"false"+ChatColor.GRAY+")");
			}
			double rawChance = dropChances.getOrDefault("PLAYER", DEFAULT_DROP_CHANCE);
			sender.sendMessage(ChatColor.GOLD+"Drop chance for \""+player.getName()+"\": "+df.format(rawChance*100D));
			return true;
		}

		Double rawChance = dropChances.get(target);
		if(rawChance != null) sender.sendMessage(ChatColor.GOLD+"Drop chance for "+target+": "+df.format(rawChance*100D));
		else sender.sendMessage(ChatColor.RED+"Drop chance for \""+target+"\" not found! (unknown mobs default to 0%)");

		StringBuilder builder = new StringBuilder(ChatColor.GRAY+"Other things that can affect droprate: ");
		if(target.equals("player")) builder.append("\nVictim must have the 'dropheads.canlosehead' permission (default=true)");
		builder.append("\nKiller must have the 'dropheads.canbehead' permission (default=true)");
		builder.append("\nIf killer has 'dropheads.alwaysbehead', chance is raised to 100%");
		if(USING_REQUIRED_TOOLS) builder.append("\nSpecific murder weapons are required"/* {IRON_AXE, ...}*/);
		builder.append("\nOther modifiers: ");
		if(USING_SPAWN_MODIFIERS && !target.equals("player")) builder.append("Spawn-reason, "/* <red>-XX% to <green>+YY% */);
		if(USING_TIME_ALIVE_MODIFIERS) builder.append("Time-alive, "/* <red>-XX% to <green>+YY% */);
		if(USING_TOOL_MODIFIERS) builder.append("Weapon-used, "/* <red>-XX% to <green>+YY% */);
		if(VANILLA_WITHER_SKELETON_LOOTING && target.equals("wither_skeleton")) builder.append("\nVanilla wither_skeleton looting rate is enabled");
		else if(USING_LOOTING_MODIFIERS) builder.append("Looting"/* (level 3: <green>+XX%) */);
		return true;
	}
}