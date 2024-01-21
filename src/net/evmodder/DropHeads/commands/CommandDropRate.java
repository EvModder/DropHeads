package net.evmodder.DropHeads.commands;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.ChatColor;
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
import net.evmodder.DropHeads.InternalAPI;
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
	final private String CMD_TRANSLATE_PATH = "commands.droprate.";
	final private DropChanceAPI dropChanceAPI;
	final private boolean ONLY_SHOW_VALID_ENTITIES = true;
	final private boolean USING_SPAWN_MULTS, NEED_CERTAIN_WEAPONS, USING_LOOTING_MULTS, USING_TIME_ALIVE_MULTS, USING_WEAPON_MULTS;
	final private boolean VANILLA_WSKELE_HANDLING;
	final private HashSet<String> entityNames;
	final private int JSON_LIMIT;

	final private Component REQUIRED_WEAPONS;
	final private TranslationComponent LOOTING_COMP; //TODO: Use comp (instead of String) for other translations as well
	private final HashMap<String, String> translations;
	private String translate(String key){
		String value = translations.get(key);
		if(value == null) translations.put(key, value = pl.getInternalAPI().loadTranslationStr(CMD_TRANSLATE_PATH + key)
				.replaceAll("%%?(?!s)", "%%")); // Allow a bit more flexibility with using %
		return value;
	}

	public CommandDropRate(DropHeads plugin) {
		super(plugin);
		pl = plugin;
		dropChanceAPI = pl.getDropChanceAPI();
		JSON_LIMIT = pl.getConfig().getInt("message-json-limit", 15000);

		translations = new HashMap<>();
		final InternalAPI api = pl.getInternalAPI();
		LOOTING_COMP = api.loadTranslationComp(CMD_TRANSLATE_PATH+"multipliers.looting");

		if(NEED_CERTAIN_WEAPONS = !dropChanceAPI.getRequiredWeapons().isEmpty()){
			ListComponent requiredWeapons = new ListComponent();
			boolean isFirstElement = true;
			for(Material mat : dropChanceAPI.getRequiredWeapons()){
				if(!isFirstElement) requiredWeapons.addComponent(new RawTextComponent("§7, §f"/*TODO: translations.yml*/));
				else isFirstElement = false;
				requiredWeapons.addComponent(new TranslationComponent("item.minecraft."+mat.name().toLowerCase()));
			}
			REQUIRED_WEAPONS = new TranslationComponent(
					api.loadTranslationStr(CMD_TRANSLATE_PATH+"restrictions.required-weapons"),
				requiredWeapons
			);
		}
		else REQUIRED_WEAPONS = null;
		ConfigurationSection specificToolMults = pl.getConfig().getConfigurationSection("specific-tool-multipliers");
		if(specificToolMults == null) specificToolMults = pl.getConfig().getConfigurationSection("specific-tool-modifiers");
		USING_WEAPON_MULTS = specificToolMults != null && specificToolMults.getKeys(false).stream()
				.anyMatch(toolName -> Material.getMaterial(toolName.toUpperCase()) != null);
		USING_SPAWN_MULTS = pl.getConfig().getBoolean("track-mob-spawns", true);
		USING_LOOTING_MULTS = dropChanceAPI.getLootingMult() != 1D || dropChanceAPI.getLootingAdd() != 0D;
		ConfigurationSection timeAliveMults = pl.getConfig().getConfigurationSection("time-alive-multipliers");
		if(timeAliveMults == null) timeAliveMults = pl.getConfig().getConfigurationSection("time-alive-modifiers");
		USING_TIME_ALIVE_MULTS = timeAliveMults != null && !timeAliveMults.getKeys(false).isEmpty();
		VANILLA_WSKELE_HANDLING = pl.getConfig().getBoolean("vanilla-wither-skeleton-skulls", true);

		//String defaultChances = FileIO.loadResource(pl, "head-drop-rates.txt"); // Already done in EntityDeathListener
		String chances = FileIO.loadFile("head-drop-rates.txt", "");
		entityNames = new HashSet<String>();
		for(String line : chances.split("\n")){
			String[] parts = line.replace(" ", "").replace("\t", "").toUpperCase().split(":");
			if(parts.length < 2) continue;
			parts[0] = parts[0].replace("DEFAULT", "UNKNOWN");
			if(ONLY_SHOW_VALID_ENTITIES){
				final int dataTagSep = parts[0].indexOf('|');
				final String eName = dataTagSep == -1 ? parts[0] : parts[0].substring(0, dataTagSep);
				try{EntityType.valueOf(eName);}
				catch(IllegalArgumentException ex){continue;}
			}
			entityNames.add(parts[0]);
		}
	}

	@Override public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args){
		if(args.length == 1){
			args[0] = args[0].toUpperCase();
			return entityNames.stream().filter(name -> name.startsWith(args[0])).collect(Collectors.toList());
		}
		if(args.length == 2) return Arrays.asList();
		if(args.length == 3) return Arrays.asList("true");
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

	private String formatDroprate(double rate){
		if(rate <= 0) return "0.0";
		double d = 10; int n=1;
		while(d*rate < 1D){d *= 10; ++n;}
		return new DecimalFormat("0."+StringUtils.repeat('0', n)+"####").format(rate);
	}

	@SuppressWarnings("deprecation") @Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
		//DecimalFormat df = new DecimalFormat("0.0####");
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
						translate("raw-drop-rate-for"), new SelectorComponent(entity.getUniqueId()),
						new RawTextComponent("0"/*0 in translations.yml?*/)).toString());
			}
			else{
				rawChance = dropChanceAPI.getRawDropChance(entity);
				sendTellraw(sender.getName(), new TranslationComponent(
						translate("raw-drop-rate-for"), new SelectorComponent(entity.getUniqueId()),
						new RawTextComponent(formatDroprate(rawChance*100D))).toString());
			}
		}
		else if(args.length > 1){
			if(!args[1].matches("[0-9]*.?[0-9]+")) return false;

			final int keyDataTagIdx = target.indexOf('|');
			final String entityName = keyDataTagIdx == -1 ? target : target.substring(0, keyDataTagIdx);
			try{EntityType.valueOf(entityName.toUpperCase());}
			catch(IllegalArgumentException ex){
				sender.sendMessage(String.format(translate("unknown-entity"), target));
				return true;
			}
			if(keyDataTagIdx != -1 && !pl.getAPI().textureExists(target)){
				sender.sendMessage(String.format(translate("unknown-entity"), target));
				return true;
			}

			final double newChance = Double.parseDouble(args[1]);
			final Double oldChance = dropChanceAPI.getRawDropChance(target, /*useDefault=*/false);
			final boolean updateFile = args.length == 3 && args[2].toLowerCase().equals("true");

			if(newChance > 1) sender.sendMessage(translate("input-rate-invalid"));
			else if(newChance == oldChance) sender.sendMessage(String.format(translate("rate-not-changed"), target, formatDroprate(oldChance*100D)));
			else{
				if(dropChanceAPI.setRawDropChance(target, newChance, updateFile))
					sender.sendMessage(String.format(translate("rate-changed"), target,
							formatDroprate(oldChance*100D), formatDroprate(newChance*100D)));
				else{
					sender.sendMessage(ChatColor.RED+"Unknown error occurred attempting to set drop chance");
					pl.getLogger().severe("Unknown error occurred attempting to set drop chance");
				}
			}
			return true;
		}
		else{
			rawChance = dropChanceAPI.getRawDropChance(target, /*useDefault=*/false);
			if(rawChance != null){
				sender.sendMessage(String.format(translate("raw-drop-rate-for"), target, formatDroprate(rawChance*100D)));
			}
			else{
				sender.sendMessage(String.format(translate("not-found"), target));
				//TODO: if default-chance != 0 && target is valid entity, tell them what default chance is.
				return false;
			}
		}

		ListComponent droprateDetails = new ListComponent();
		//final boolean victimCantLostHead = entity == null && target.equals("PLAYER");
		final boolean victimCantLoseHead = entity != null && !entity.hasPermission("dropheads.canlosehead");
		final boolean senderCantBehead = !sender.hasPermission("dropheads.canbehead."+target.toLowerCase());
		final boolean senderAlwaysBeheads = sender.hasPermission("dropheads.alwaysbehead."+target.toLowerCase());
		final boolean notUsingRequiredWeapon = NEED_CERTAIN_WEAPONS && (weapon == null || !dropChanceAPI.getRequiredWeapons().contains(weapon.getType()));
		if(victimCantLoseHead || senderCantBehead || senderAlwaysBeheads || notUsingRequiredWeapon){
			droprateDetails.addComponent(translate("restrictions.header"));
			if(victimCantLoseHead){rawChance=-1D;droprateDetails.addComponent(translate("restrictions.victim-must-have-perm"));}
			if(senderCantBehead){rawChance=-1D;droprateDetails.addComponent(translate("restrictions.killer-must-have-perm"));}
			if(senderAlwaysBeheads){rawChance=-1D;droprateDetails.addComponent(translate("restrictions.always-behead-perm"));}
			if(notUsingRequiredWeapon){rawChance=-1D;droprateDetails.addComponent(REQUIRED_WEAPONS);}
		}

		// Multipliers:
		final double weaponMult = weapon == null ? 1D : dropChanceAPI.getWeaponMult(weapon.getType());
		final int lootingLevel = weapon == null ? 0 : weapon.getEnchantmentLevel(Enchantment.LOOT_BONUS_MOBS);
		final double lootingMult = lootingLevel == 0 ? 1D
				: Math.min(Math.pow(dropChanceAPI.getLootingMult(), lootingLevel), dropChanceAPI.getLootingMult()*lootingLevel);
		final double lootingAdd = dropChanceAPI.getLootingAdd()*lootingLevel;
		final double timeAliveMult = entity == null ? 1D : dropChanceAPI.getTimeAliveMult(entity);
		final double spawnCauseMult = entity == null ? 1D : JunkUtils.getSpawnCauseMult(entity);
		final double permMult = dropChanceAPI.getPermsBasedMult(sender);
		final double finalDropChance = Math.min(1D, rawChance*spawnCauseMult*timeAliveMult*weaponMult*lootingMult*permMult + lootingAdd);
		DecimalFormat multFormatter = new DecimalFormat("0.##");
		
		if(VANILLA_WSKELE_HANDLING && target.equals("WITHER_SKELETON")){
			if(!droprateDetails.isEmpty()) droprateDetails.addComponent("\n");
			droprateDetails.addComponent(translate("vanilla-wither-skeleton-handling-alert"));
		}
		else{
			final ListComponent droprateMultipliers = new ListComponent();
			final String LIST_SEP = "§7, ";//TODO: translations.yml
			if(USING_SPAWN_MULTS){
				if(entity == null){
					droprateMultipliers.addComponent(translate("multipliers.spawn-reason"));
					droprateMultipliers.addComponent(LIST_SEP);
				}
				else if(Math.abs(1D-spawnCauseMult) > 0.001D){
					droprateMultipliers.addComponent(translate("multipliers.spawn-reason"));
					droprateMultipliers.addComponent(":§6x"+multFormatter.format(spawnCauseMult)+LIST_SEP);
				}
			}
			if(USING_TIME_ALIVE_MULTS){
				if(entity == null){
					droprateMultipliers.addComponent(translate("multipliers.time-alive"));
					droprateMultipliers.addComponent(LIST_SEP);
				}
				else if(Math.abs(1D-timeAliveMult) > 0.001D){
					droprateMultipliers.addComponent(translate("multipliers.time-alive"));
					droprateMultipliers.addComponent(":§6x"+multFormatter.format(timeAliveMult)+LIST_SEP);
				}
			}
			if(USING_WEAPON_MULTS){
				if(entity == null){
					droprateMultipliers.addComponent(translate("multipliers.weapon-type"));
					droprateMultipliers.addComponent(LIST_SEP);
				}
				else if(weapon != null && Math.abs(1D-weaponMult) > 0.001D){
					droprateMultipliers.addComponent(translate("multipliers.weapon-type"));
					droprateMultipliers.addComponent(JunkUtils.getMurderItemComponent(weapon, JSON_LIMIT));
					droprateMultipliers.addComponent(":§6x"+multFormatter.format(weaponMult)+LIST_SEP);
				}
			}
			if(Math.abs(1D-permMult) > 0.001D){
				droprateMultipliers.addComponent(translate("multipliers.perms"));
				droprateMultipliers.addComponent(":§6x"+multFormatter.format(permMult)+LIST_SEP);
			}
			if(USING_LOOTING_MULTS){
				if(entity == null){
					droprateMultipliers.addComponent(LOOTING_COMP);
				}
				else if(Math.abs(1D-lootingMult) > 0.001D || Math.abs(lootingAdd) > 0.001D){
					droprateMultipliers.addComponent(LOOTING_COMP);
					String lootingMsg = lootingLevel+":";
					if(Math.abs(1D-lootingMult) > 0.001D) lootingMsg += "§6x"+multFormatter.format(lootingMult);
					if(Math.abs(lootingAdd) > 0.001D) lootingMsg += "§e"+(lootingAdd > 0 ? '+' : '-')+multFormatter.format(lootingAdd*100)+"%";
					droprateMultipliers.addComponent(lootingMsg);
				}
				//else TODO: potential trailing "&7, " at end of list
			}
			if(!droprateMultipliers.isEmpty()){
				if(!droprateDetails.isEmpty()) droprateDetails.addComponent("\n");
				droprateDetails.addComponent(translate("multipliers.header"));
				droprateDetails.addComponent(droprateMultipliers);
			}
			if(entity != null && rawChance > 0D && Math.abs(finalDropChance-rawChance) > 0.001D){
				if(!droprateDetails.isEmpty()) droprateDetails.addComponent("\n");
				droprateDetails.addComponent(String.format(translate("final-drop-rate"), formatDroprate(finalDropChance*100D)));
			}
		} // end else (not a wither_skeleton)
		if(!droprateDetails.isEmpty()) sendTellraw(sender.getName(), droprateDetails.toString());
		return true;
	}
}