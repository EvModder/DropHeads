package net.evmodder.DropHeads.commands;

import java.text.DecimalFormat;
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
import net.evmodder.DropHeads.DropChanceAPI;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.JunkUtils;
import net.evmodder.EvLib.EvCommand;
import net.evmodder.EvLib.FileIO;
import net.evmodder.EvLib.extras.TellrawUtils.Component;
import net.evmodder.EvLib.extras.TellrawUtils.ListComponent;
import net.evmodder.EvLib.extras.TellrawUtils.TranslationComponent;
import net.evmodder.EvLib.extras.TellrawUtils.RawTextComponent;
import net.evmodder.EvLib.extras.TellrawUtils.SelectorComponent;

public class CommandDropRate extends EvCommand{
	final private DropHeads pl;
	final private DropChanceAPI dropChanceAPI;
	final boolean ONLY_SHOW_VALID_ENTITIES = true;
	final boolean USING_SPAWN_MODIFIERS, NEED_CERTAIN_WEAPONS, USING_LOOTING_MODIFIERS, USING_TIME_ALIVE_MODIFIERS, USING_WEAPON_MODIFIERS;
	final boolean VANILLA_WSKELE_HANDLING;
	final HashSet<String> entityNames;
	final double DEFAULT_DROP_CHANCE;
	final int JSON_LIMIT;
	
	final String DROP_CHANCE_FOR_NOT_FOUND;
	final String RAW_DROP_CHANCE_FOR;
	final String FINAL_DROP_CHANCE;

	final String RESTRICTIONS_HEADER;
	final String VICTIM_MUST_HAVE_PERM;
	final String KILLER_MUST_HAVE_PERM;
	final String ALWAYS_BEHEAD_PERM;
	final Component REQUIRED_WEAPONS;
	final String MULTIPLIERS_HEADER;
	final String SPAWN_REASON;
	final String TIME_ALIVE;
	final String WEAPON_TYPE;
	final String PERMS;
	final TranslationComponent LOOTING_COMP;
	final String VANILLA_WSKELE_BEHAVIOR_ALERT;

	public CommandDropRate(DropHeads plugin) {
		super(plugin);
		pl = plugin;
		DROP_CHANCE_FOR_NOT_FOUND = pl.getAPI().loadTranslationStr("commands.droprate.not-found").replaceAll("%%?(?!s)", "%%");
		RAW_DROP_CHANCE_FOR = pl.getAPI().loadTranslationStr("commands.droprate.raw-drop-rate-for").replaceAll("%%?(?!s)", "%%");
		FINAL_DROP_CHANCE = pl.getAPI().loadTranslationStr("commands.droprate.final-drop-rate").replaceAll("%%?(?!s)", "%%");

		RESTRICTIONS_HEADER = pl.getAPI().loadTranslationStr("commands.droprate.restrictions.header");
		VICTIM_MUST_HAVE_PERM = pl.getAPI().loadTranslationStr("commands.droprate.restrictions.victim-must-have-perm");
		KILLER_MUST_HAVE_PERM = pl.getAPI().loadTranslationStr("commands.droprate.restrictions.killer-must-have-perm");
		ALWAYS_BEHEAD_PERM = pl.getAPI().loadTranslationStr("commands.droprate.restrictions.always-behead-perm");

		MULTIPLIERS_HEADER = pl.getAPI().loadTranslationStr("commands.droprate.multipliers.header");
		SPAWN_REASON = pl.getAPI().loadTranslationStr("commands.droprate.multipliers.spawn-reason");
		TIME_ALIVE = pl.getAPI().loadTranslationStr("commands.droprate.multipliers.time-alive");
		WEAPON_TYPE = pl.getAPI().loadTranslationStr("commands.droprate.multipliers.weapon-type");
		PERMS = pl.getAPI().loadTranslationStr("commands.droprate.multipliers.perms");
		//TODO: Should we do it this way for all the other msgs as well?
		LOOTING_COMP = pl.getAPI().loadTranslationComp("commands.droprate.multipliers.looting");

		VANILLA_WSKELE_BEHAVIOR_ALERT = pl.getAPI().loadTranslationStr("commands.droprate.vanilla-wither-skeleton-handling-alert");

		dropChanceAPI = pl.getDropChanceAPI();
		JSON_LIMIT = pl.getConfig().getInt("message-json-limit", 15000);

		if(NEED_CERTAIN_WEAPONS = !dropChanceAPI.getRequiredWeapons().isEmpty()){
			ListComponent requiredWeapons = new ListComponent();
			boolean isFirstElement = true;
			for(Material mat : dropChanceAPI.getRequiredWeapons()){
				if(!isFirstElement) requiredWeapons.addComponent(new RawTextComponent("§7, §f"/*TODO: translations.yml*/));
				else isFirstElement = false;
				requiredWeapons.addComponent(new TranslationComponent("item.minecraft."+mat.name().toLowerCase()));
			}
			REQUIRED_WEAPONS = new TranslationComponent(
				pl.getAPI().loadTranslationStr("commands.droprate.restrictions.required-weapons"),
				requiredWeapons
			);
		}
		else REQUIRED_WEAPONS = null;
		ConfigurationSection specificToolModifiers = pl.getConfig().getConfigurationSection("specific-tool-modifiers");
		USING_WEAPON_MODIFIERS = specificToolModifiers != null && specificToolModifiers.getKeys(false)
				.stream().anyMatch(toolName -> Material.getMaterial(toolName.toUpperCase()) != null);
		USING_SPAWN_MODIFIERS = pl.getConfig().getBoolean("track-mob-spawns", true);
		USING_LOOTING_MODIFIERS = dropChanceAPI.getLootingMult() != 1D || dropChanceAPI.getLootingAdd() != 0D;
		USING_TIME_ALIVE_MODIFIERS = pl.getConfig().isConfigurationSection("time-alive-modifiers")
				&& !pl.getConfig().getConfigurationSection("time-alive-modifiers").getKeys(false).isEmpty();
		VANILLA_WSKELE_HANDLING = pl.getConfig().getBoolean("vanilla-wither-skeleton-skulls", true);

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

	private void sendTellraw(String target, String message){
		pl.getServer().dispatchCommand(pl.getServer().getConsoleSender(), "minecraft:tellraw "+target+" "+message);
	}

	@SuppressWarnings("deprecation") @Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
		DecimalFormat df = new DecimalFormat("0.0####");
		ItemStack weapon = sender instanceof Player ? ((Player)sender).getInventory().getItemInMainHand() : null;
		if(weapon.getType() == Material.AIR) weapon = null;
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
				sendTellraw(sender.getName(), new TranslationComponent(
						RAW_DROP_CHANCE_FOR, new SelectorComponent(entity.getUniqueId()), new RawTextComponent("0"/*0 in translations.yml?*/)).toString());
			}
			else{
				rawChance = dropChanceAPI.getRawDropChance(entity);
				sendTellraw(sender.getName(), new TranslationComponent(
						RAW_DROP_CHANCE_FOR, new SelectorComponent(entity.getUniqueId()), new RawTextComponent(df.format(rawChance*100D))).toString());
			}
		}
		else{
			rawChance = dropChanceAPI.getRawDropChance(target);
			if(rawChance != DEFAULT_DROP_CHANCE){
				sender.sendMessage(String.format(RAW_DROP_CHANCE_FOR, target, df.format(rawChance*100D), df.format(rawChance*100D)));
			}
			else{//TODO: configured drop chance of 0 (e.g., armor_stand) gives not found error
				sender.sendMessage(String.format(DROP_CHANCE_FOR_NOT_FOUND, target));
				return false;
			}
		}

		ListComponent droprateDetails = new ListComponent();
		//final boolean victimCantLostHead = entity == null && target.equals("PLAYER");
		final boolean victimCantLoseHead = entity != null && !entity.hasPermission("dropheads.canlosehead");
		final boolean senderCantBehead = !sender.hasPermission("dropheads.canbehead");
		final boolean senderAlwaysBeheads = sender.hasPermission("dropheads.alwaysbehead");
		final boolean notUsingRequiredWeapon = NEED_CERTAIN_WEAPONS && (weapon == null || !dropChanceAPI.getRequiredWeapons().contains(weapon.getType()));
		if(victimCantLoseHead || senderCantBehead || senderAlwaysBeheads || notUsingRequiredWeapon){
			droprateDetails.addComponent(RESTRICTIONS_HEADER);
			if(victimCantLoseHead){rawChance=-1D;droprateDetails.addComponent(VICTIM_MUST_HAVE_PERM);}
			if(senderCantBehead){rawChance=-1D;droprateDetails.addComponent(KILLER_MUST_HAVE_PERM);}
			if(senderAlwaysBeheads){rawChance=-1D;droprateDetails.addComponent(ALWAYS_BEHEAD_PERM);}
			if(notUsingRequiredWeapon){rawChance=-1D;droprateDetails.addComponent(REQUIRED_WEAPONS);}
		}

		// Multipliers:
		final double weaponMod = weapon == null ? 1D : 1D+dropChanceAPI.getWeaponModifier(weapon.getType());
		final int lootingLevel = weapon == null ? 0 : weapon.getEnchantmentLevel(Enchantment.LOOT_BONUS_MOBS);
		final double lootingMod = lootingLevel == 0 ? 1D
				: Math.min(Math.pow(dropChanceAPI.getLootingMult(), lootingLevel), dropChanceAPI.getLootingMult()*lootingLevel);
		final double lootingAdd = dropChanceAPI.getLootingAdd()*lootingLevel;
		final double timeAliveMod = entity == null ? 1D : 1D + dropChanceAPI.getTimeAliveModifier(entity);
		final double spawnCauseMod = entity == null ? 1D : JunkUtils.getSpawnCauseModifier(entity);
		final double permMod = dropChanceAPI.getPermsBasedDropRateModifier(sender);
		final double finalDropChance = rawChance*spawnCauseMod*timeAliveMod*weaponMod*lootingMod*permMod + lootingAdd;
		df = new DecimalFormat("0.##");
		
		if(VANILLA_WSKELE_HANDLING && target.equals("WITHER_SKELETON")){
			if(!droprateDetails.isEmpty()) droprateDetails.addComponent("\n");
			droprateDetails.addComponent(VANILLA_WSKELE_BEHAVIOR_ALERT);
		}
		else{
			ListComponent droprateMultipliers = new ListComponent();
			if(USING_SPAWN_MODIFIERS){
				if(entity == null){
					droprateMultipliers.addComponent(SPAWN_REASON);
					droprateMultipliers.addComponent("§7, "/*TODO: translations.yml*/);
				}
				else if(Math.abs(1D-spawnCauseMod) > 0.001D){
					droprateMultipliers.addComponent(SPAWN_REASON);
					droprateMultipliers.addComponent(":§6x"+df.format(spawnCauseMod)+"§7, "/*TODO: translations.yml*/);
				}
			}
			if(USING_TIME_ALIVE_MODIFIERS){
				if(entity == null){
					droprateMultipliers.addComponent(TIME_ALIVE);
					droprateMultipliers.addComponent("§7, "/*TODO: translations.yml*/);
				}
				else if(Math.abs(1D-timeAliveMod) > 0.001D){
					droprateMultipliers.addComponent(TIME_ALIVE);
					droprateMultipliers.addComponent(":§6x"+df.format(timeAliveMod)+"§7, "/*TODO: translations.yml*/);
				}
			}
			if(USING_WEAPON_MODIFIERS){
				if(entity == null){
					droprateMultipliers.addComponent(WEAPON_TYPE);
					droprateMultipliers.addComponent("§7, "/*TODO: translations.yml*/);
				}
				else if(weapon != null && Math.abs(1D-weaponMod) > 0.001D){
					droprateMultipliers.addComponent(WEAPON_TYPE);
					droprateMultipliers.addComponent(JunkUtils.getMurderItemComponent(weapon, JSON_LIMIT));
					droprateMultipliers.addComponent(":§6x"+df.format(weaponMod));
					droprateMultipliers.addComponent("§7, "/*TODO: translations.yml*/);
				}
			}
			if(Math.abs(1D-permMod) > 0.001D){
				droprateMultipliers.addComponent(PERMS);
				droprateMultipliers.addComponent(":§6x"+df.format(permMod)+"§7, "/*TODO: translations.yml*/);
			}
			if(USING_LOOTING_MODIFIERS){
				if(entity == null){
					droprateMultipliers.addComponent(LOOTING_COMP);
				}
				else if(Math.abs(1D-lootingMod) > 0.001D || Math.abs(lootingAdd) > 0.001D){
					droprateMultipliers.addComponent(LOOTING_COMP);
					String lootingMsg = lootingLevel+":";
					if(Math.abs(1D-lootingMod) > 0.001D) lootingMsg += "§6x"+df.format(lootingMod);
					if(Math.abs(lootingAdd) > 0.001D) lootingMsg += "§e"+(lootingAdd > 0 ? '+' : '-')+df.format(lootingAdd*100)+"%";
					droprateMultipliers.addComponent(lootingMsg);
				}
				//else TODO: potential trailing "&7, " at end of list (when looting is not included in modifiers list)
			}
			if(!droprateMultipliers.isEmpty()){
				if(!droprateDetails.isEmpty()) droprateDetails.addComponent("\n");
				droprateDetails.addComponent(MULTIPLIERS_HEADER);
				droprateDetails.addComponent(droprateMultipliers);
			}
			if(entity != null && rawChance > 0D && Math.abs(finalDropChance-rawChance) > 0.001D){
				df = new DecimalFormat("0.0####");
				if(!droprateDetails.isEmpty()) droprateDetails.addComponent("\n");
				droprateDetails.addComponent(String.format(FINAL_DROP_CHANCE, df.format(finalDropChance*100D)));
			}
		} // end else (not a wither_skeleton)
		if(!droprateDetails.isEmpty()) sendTellraw(sender.getName(), droprateDetails.toString());
		return true;
	}
}