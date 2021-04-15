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
import net.evmodder.DropHeads.JunkUtils;
import net.evmodder.DropHeads.listeners.EntityDeathListener;
import net.evmodder.EvLib.EvCommand;
import net.evmodder.EvLib.FileIO;
import net.evmodder.EvLib.extras.TellrawUtils.ListComponent;
import net.evmodder.EvLib.extras.TellrawUtils.TranslationComponent;

public class CommandDropRate extends EvCommand{
	final private DropHeads pl;
	EntityDeathListener deathListener;
	final DecimalFormat df;
	final boolean ONLY_SHOW_VALID_ENTITIES = true;
	final boolean USING_SPAWN_MODIFIERS, USING_LOOTING_MODIFIERS, USING_TIME_ALIVE_MODIFIERS, USING_TOOL_MODIFIERS,
			VANILLA_WITHER_SKELETON_LOOTING;
	final ListComponent REQUIRED_TOOLS;
	final HashMap<String, Double> dropChances;
	final double DEFAULT_DROP_CHANCE;
	
	final String DROP_CHANCE_FOR = "§6Drop chance for §e%s§6: §b%s%";
	final String DROP_CHANCE_FOR_NOT_FOUND = "§6Drop chance for \"§c%s\"§6 not found! §7(unknown mobs default to §b0%§7)";
	final String OTHER_THINGS_THAT_AFFECT_DROPRATE = "§7Other things that can affect droprate: ";
	final String VICTIM_MUST_HAVE_PERM = "Victim must have '§fdropheads.canlosehead§7' perm (default=true)";
	final String KILLER_MUST_HAVE_PERM = "You must have the '§fdropheads.canbehead§7' perm";
	final String ALWAYS_BEHEAD_ALERT = "Perm '§fdropheads.alwaysbehead§7' raises rate to 100%";
	final String SPECIFIC_WEAPONS_ALERT = "Specific murder weapons are required {%s}";
	final String RATE_MODIFIER_HEADER = "Rate modifiers: ";
	final String SPAWN_REASON = "§fSpawnReason§7, ", TIME_ALIVE = "§fTimeAlive§7, ", WEAPON_TYPE = "§fWeaponType§7, ", LOOTING = "§fLooting§7";
	final String VANILLA_WITHER_SKELETON_BEHAVIOR_ALERT = "Vanilla wither_skeleton looting rate is enabled";

	public CommandDropRate(DropHeads plugin, EntityDeathListener deathListener) {
		super(plugin);
		pl = plugin;
		this.deathListener = deathListener;
		REQUIRED_TOOLS = new ListComponent();
		deathListener.mustUseTools.stream().forEach(mat -> REQUIRED_TOOLS.addComponent(new TranslationComponent("item.minecraft."+mat.name().toLowerCase())));
		USING_SPAWN_MODIFIERS = pl.getConfig().getBoolean("track-mob-spawns", true);
		USING_LOOTING_MODIFIERS = pl.getConfig().getDouble("looting-mutliplier") != 1.0 || pl.getConfig().getDouble("looting-addition") != 0;
		USING_TIME_ALIVE_MODIFIERS = pl.getConfig().isConfigurationSection("time-alive-modifiers")
				&& !pl.getConfig().getConfigurationSection("time-alive-modifiers").getKeys(false).isEmpty();
		USING_TOOL_MODIFIERS = pl.getConfig().isConfigurationSection("specific-tool-modifiers")
				&& !pl.getConfig().getConfigurationSection("specific-tool-modifiers").getKeys(false).isEmpty();
		VANILLA_WITHER_SKELETON_LOOTING = pl.getConfig().getBoolean("vanilla-wither-skeleton-looting-behavior", true);

		//String defaultChances = FileIO.loadResource(pl, "head-drop-rates.txt"); // Already done in EntityDeathListener
		String chances = FileIO.loadFile("head-drop-rates.txt", "");
		dropChances = new HashMap<String, Double>();
		double chanceForUnknown = 0D;
		for(String line : chances.split("\n")){
			String[] parts = line.replace(" ", "").replace("\t", "").toUpperCase().split(":");
			if(parts.length < 2) continue;
			parts[0] = parts[0].replace("DEFAULT", "UNKNOWN");
			try{
				double dropChance = Double.parseDouble(parts[1]);
				if(parts[0].equals("UNKNOWN")) chanceForUnknown = dropChance;
				if(ONLY_SHOW_VALID_ENTITIES){
					int dataTagSep = parts[0].indexOf('|');
					String eName = dataTagSep == -1 ? parts[0] : parts[0].substring(0, dataTagSep);
					EntityType.valueOf(eName); // If entity does not exist, this drops to the catch below
				}
				dropChances.put(parts[0], dropChance);
			}
			catch(NumberFormatException ex){}
			catch(IllegalArgumentException ex){}
		}
		DEFAULT_DROP_CHANCE = chanceForUnknown;
		df = new DecimalFormat("0.0###");
	}

	@Override public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args){
		if(args.length == 1){
			args[0] = args[0].toUpperCase();
			return dropChances.keySet().stream().filter(name -> name.startsWith(args[0])).collect(Collectors.toList());
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
		Entity entity = null;
		if(args.length == 0){
			if(sender instanceof Player) entity = getTargetEntity((Player)sender, /*range=*/10);
			if(entity == null) return false;
		}
		final String target = entity == null ? args[0].toUpperCase() : entity.getType().name().toUpperCase();

		if(entity == null) entity = pl.getServer().getPlayer(target);
		if(entity != null){
			if(!entity.hasPermission("dropheads.canlosehead")){
				sender.sendMessage(String.format(DROP_CHANCE_FOR, entity instanceof Player ? entity.getName() : target, 0)
						+" §7(dropheads.canlosehead=§cfalse§7)");
			}
			else{
				double dropChance = deathListener.getRawDropChance(entity);
				if(USING_SPAWN_MODIFIERS) dropChance *= JunkUtils.getSpawnCauseModifier(entity);
				if(USING_TIME_ALIVE_MODIFIERS) dropChance *= (1D + deathListener.getTimeAliveBonus(entity));
				sender.sendMessage(String.format(DROP_CHANCE_FOR, entity instanceof Player ? entity.getName() : target, df.format(dropChance*100D)));
			}
		}
		else{
			Double rawChance = dropChances.get(target);
			if(rawChance != null) sender.sendMessage(String.format(DROP_CHANCE_FOR, target, df.format(rawChance*100D), df.format(rawChance*100D)));
			else{
				sender.sendMessage(String.format(DROP_CHANCE_FOR_NOT_FOUND, target));
				return false;
			}
		}

		StringBuilder builder = new StringBuilder(OTHER_THINGS_THAT_AFFECT_DROPRATE);
		if(entity == null && target.equals("PLAYER")) builder.append('\n').append(VICTIM_MUST_HAVE_PERM);
		if(!sender.hasPermission("dropheads.canbehead")) builder.append('\n').append(KILLER_MUST_HAVE_PERM);// (true/false for you)
		if(sender.hasPermission("dropheads.alwaysbehead")) builder.append('\n').append(ALWAYS_BEHEAD_ALERT);// (true/false for you)
		if(!REQUIRED_TOOLS.getComponents().isEmpty()) builder.append('\n').append(String.format(SPECIFIC_WEAPONS_ALERT, "${REQUIRED_TOOLS}"));
		builder.append('\n').append(RATE_MODIFIER_HEADER);
		if(USING_SPAWN_MODIFIERS && !target.equals("PLAYER") &&
				(entity == null || Math.abs(1F - JunkUtils.getSpawnCauseModifier(entity)) > 0.001F))
			builder.append(SPAWN_REASON/* <red>-XX% to <green>+YY% */);
		if(USING_TIME_ALIVE_MODIFIERS && (entity == null || Math.abs(deathListener.getTimeAliveBonus(entity)) > 0.001))
			builder.append(TIME_ALIVE/* <red>-XX% to <green>+YY% */);
		if(USING_TOOL_MODIFIERS) builder.append(WEAPON_TYPE/* <red>-XX% to <green>+YY% */);
		if(VANILLA_WITHER_SKELETON_LOOTING && target.equals("WITHER_SKELETON")) builder.append('\n').append(VANILLA_WITHER_SKELETON_BEHAVIOR_ALERT);
		else if(USING_LOOTING_MODIFIERS) builder.append(LOOTING/* (level 3: <green>+XX%) */);
		sender.sendMessage(builder.toString());
		return true;
	}
}