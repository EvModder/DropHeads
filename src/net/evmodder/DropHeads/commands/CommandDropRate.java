package net.evmodder.DropHeads.commands;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import net.evmodder.DropHeads.DropChanceAPI;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.InternalAPI;
import net.evmodder.DropHeads.MiscUtils;
import net.evmodder.DropHeads.datatypes.EntitySetting;
import net.evmodder.EvLib.EvCommand;
import net.evmodder.EvLib.FileIO;
import net.evmodder.EvLib.extras.TellrawUtils.ListComponent;
import net.evmodder.EvLib.extras.TellrawUtils.TranslationComponent;
import net.evmodder.EvLib.extras.TellrawUtils.RawTextComponent;
import net.evmodder.EvLib.extras.TellrawUtils.SelectorComponent;

public class CommandDropRate extends EvCommand{
	private final DropHeads pl;
	private final String CMD_TRANSLATE_PATH = "commands.droprate.";
	private final DropChanceAPI dropChanceAPI;
	private final boolean USING_SPAWN_MULTS, USING_LOOTING_MULTS, USING_TIME_ALIVE_MULTS, USING_WEAPON_MULTS;
	private final boolean VANILLA_WSKELE_HANDLING;
	private final boolean ONLY_SHOW_VALID_ENTITIES = true;
	private final HashSet<String> entityNames;
	private final int JSON_LIMIT;
	private final String LIST_SEP;
	private final String MOB_PREFIX;
	///tellraw EvDoc [{"text":"a","color":"green"},"§7b","c"]

	public static final class Friend{private Friend(){}}
	private static final Friend friend = new Friend();

	final private TranslationComponent LOOTING_COMP; //TODO: Use comp (instead of String) for other translations as well
	private final HashMap<String, String> translations;
	private String translate(String key){
		String value = translations.get(key);
		if(value == null) translations.put(key, value = pl.getInternalAPI().loadTranslationStr(CMD_TRANSLATE_PATH + key)
				.replaceAll("%%?(?!s)", "%%")); // Allow a bit more flexibility with using %
		return value;
	}

	private HashSet<String> loadEntitiesWithNonZeroDropChance(){
		//String defaultChances = FileIO.loadResource(pl, "configs/head-drop-rates.txt"); // Already done in EntityDeathListener
		String chances = FileIO.loadFile("head-drop-rates.txt", "");
		HashSet<String> entityNames = new HashSet<>();
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
		return entityNames;
	}

	public CommandDropRate(DropHeads plugin) {
		super(plugin);
		pl = plugin;
		dropChanceAPI = pl.getDropChanceAPI();
		JSON_LIMIT = pl.getConfig().getInt("message-json-limit", 15000);

		translations = new HashMap<>();
		final InternalAPI api = pl.getInternalAPI();
		LOOTING_COMP = api.loadTranslationComp(CMD_TRANSLATE_PATH+"multipliers.looting");
		MOB_PREFIX = api.loadTranslationStr("commands.spawnhead.prefixes.mob");
		LIST_SEP = api.loadTranslationStr(CMD_TRANSLATE_PATH+"list-sep");

		USING_SPAWN_MULTS = pl.getConfig().getBoolean("track-mob-spawns", true);
		USING_TIME_ALIVE_MULTS = dropChanceAPI.hasTimeAliveMults(friend);
		USING_WEAPON_MULTS = dropChanceAPI.hasWeaponMults(friend);
		USING_LOOTING_MULTS = dropChanceAPI.hasLootingMults(friend);

		VANILLA_WSKELE_HANDLING = pl.getConfig().getBoolean("vanilla-wither-skeleton-skulls", true);

		entityNames = loadEntitiesWithNonZeroDropChance();
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
		if(weapon != null && weapon.getType() == Material.AIR) weapon = null;
		Entity entity = null;
		Double rawChance = 0D;
		if(args.length == 0){
			if(sender instanceof Player){
				entity = MiscUtils.getTargetEntity((Player)sender, /*range=*/10);
			}
			if(entity == null) return false;
		}
		else if(args[0].startsWith(MOB_PREFIX)) args[0] = args[0].substring(MOB_PREFIX.length());
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
		else if(args.length == 1){
			rawChance = dropChanceAPI.getRawDropChanceOrDefaultFromTextureKey(target, /*useDefault=*/false);
			if(rawChance != null){
				sender.sendMessage(String.format(translate("raw-drop-rate-for"), target, formatDroprate(rawChance*100D)));
			}
			else{
				sender.sendMessage(String.format(translate("not-found"), target, dropChanceAPI.getRawDropChanceOrDefaultFromTextureKey(target, /*useDefault=*/true)));
				return false;
			}
		}
		else{
			if(!args[1].matches("[0-9]*.?[0-9]+")) return false;
			if(!sender.hasPermission("dropheads.droprate.edit")) return false;

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
			final Double oldChance = dropChanceAPI.getRawDropChanceOrDefaultFromTextureKey(target, /*useDefault=*/false);
			final boolean updateFile = args.length == 3 && args[2].toLowerCase().equals("true") && sender.hasPermission("dropheads.droprate.edit.file");

			if(newChance > 1) sender.sendMessage(translate("input-rate-invalid"));
			else if(newChance == oldChance) sender.sendMessage(String.format(translate("rate-not-changed"), target, formatDroprate(oldChance*100D)));
			else{
				if(dropChanceAPI.setRawDropChance(target, newChance, updateFile)){
					sender.sendMessage(String.format(translate("rate-changed"), target,
							oldChance == null ? "null" : formatDroprate(oldChance*100D),
							formatDroprate(newChance*100D)));
					if(sender.hasPermission("dropheads.droprate.edit.file"))
						sender.sendMessage(String.format(translate("file-updated"), updateFile));
				}
				else{
					// Should be unreachable, barring FileIO errors
					sender.sendMessage(ChatColor.RED+"Unknown error occurred attempting to set drop chance");
					pl.getLogger().severe("Unknown error occurred attempting to set drop chance");
				}
			}
			return true;
		}

		ListComponent droprateDetails = new ListComponent();
		//final boolean victimCantLostHead = entity == null && target.equals("PLAYER");
		final boolean victimCantLoseHead = entity != null && !entity.hasPermission("dropheads.canlosehead");
		final boolean senderCantBehead = !sender.hasPermission("dropheads.canbehead."+target.toLowerCase());
		final boolean senderAlwaysBeheads = sender.hasPermission("dropheads.alwaysbehead."+target.toLowerCase());
		final boolean notUsingRequiredWeapon = !dropChanceAPI.isWeaponAbleToBehead(entity, weapon != null ? weapon.getType() : Material.AIR);
		if(victimCantLoseHead || senderCantBehead || senderAlwaysBeheads || notUsingRequiredWeapon){
			droprateDetails.addComponent(translate("restrictions.header"));
			if(victimCantLoseHead){rawChance=-1D;droprateDetails.addComponent(translate("restrictions.victim-must-have-perm"));}
			if(senderCantBehead){rawChance=-1D;droprateDetails.addComponent(translate("restrictions.killer-must-have-perm"));}
			if(senderAlwaysBeheads){rawChance=-1D;droprateDetails.addComponent(translate("restrictions.always-behead-perm"));}
			if(notUsingRequiredWeapon){
				rawChance=-1D;
				final EntitySetting<Set<Material>> requiredWeapons = dropChanceAPI.getRequiredWeapons(friend);
				Set<Material> weapons = entity != null ? requiredWeapons.get(entity) : requiredWeapons.get(EntityType.UNKNOWN);
				//TODO: wow can future me please make a proper joiner/collector for ListComponent
				ListComponent weaponsComp = new ListComponent();
				boolean firstElement = true;
				for(Material mat : weapons){
					if(!firstElement) weaponsComp.addComponent(LIST_SEP);
					else firstElement = false;
					weaponsComp.addComponent(new TranslationComponent("item.minecraft."+mat.name().toLowerCase()));
				}
				droprateDetails.addComponent(new TranslationComponent(
						pl.getInternalAPI().loadTranslationStr(CMD_TRANSLATE_PATH+"restrictions.required-weapons"), weaponsComp));
			}
		}

		// Multipliers:
		final double weaponMult = weapon == null ? 1D : dropChanceAPI.getWeaponMult(entity, weapon.getType());
		final int lootingLevel = MiscUtils.getLootingLevel(weapon);
		final double lootingMult = lootingLevel == 0 ? 1d
				: Math.min(Math.pow(dropChanceAPI.getLootingMult(entity), lootingLevel), dropChanceAPI.getLootingMult(entity)*lootingLevel);
		final double lootingAdd = dropChanceAPI.getLootingAdd(entity)*lootingLevel;
		final double timeAliveMult = entity == null ? 1d : dropChanceAPI.getTimeAliveMult(entity);
		final double spawnCauseMult = entity == null ? 1d : MiscUtils.getSpawnCauseMult(entity);
		final double permMult = dropChanceAPI.getPermissionsMult(entity, sender);
		final double finalDropChance = Math.min(1D, rawChance*spawnCauseMult*timeAliveMult*weaponMult*lootingMult*permMult + lootingAdd);
		DecimalFormat multFormatter = new DecimalFormat("0.##");

		if(VANILLA_WSKELE_HANDLING && target.equals("WITHER_SKELETON")){
			if(!droprateDetails.isEmpty()) droprateDetails.addComponent("\n");
			droprateDetails.addComponent(translate("vanilla-wither-skeleton-handling-alert"));
		}
		else{
			final ListComponent droprateMultipliers = new ListComponent();
			if(USING_SPAWN_MULTS && entity == null){
				//if(!droprateMultipliers.isEmpty()) droprateMultipliers.addComponent(LIST_SEP);
				droprateMultipliers.addComponent(translate("multipliers.spawn-reason"));
			}
			else if(Math.abs(1D-spawnCauseMult) > 0.001D){
				//if(!droprateMultipliers.isEmpty()) droprateMultipliers.addComponent(LIST_SEP);
				droprateMultipliers.addComponent(translate("multipliers.spawn-reason"));
				droprateMultipliers.addComponent(":§6x"+multFormatter.format(spawnCauseMult));
			}
			if(USING_TIME_ALIVE_MULTS && entity == null){
				if(!droprateMultipliers.isEmpty()) droprateMultipliers.addComponent(LIST_SEP);
				droprateMultipliers.addComponent(translate("multipliers.time-alive"));
			}
			else if(Math.abs(1D-timeAliveMult) > 0.001D){
				if(!droprateMultipliers.isEmpty()) droprateMultipliers.addComponent(LIST_SEP);
				droprateMultipliers.addComponent(translate("multipliers.time-alive"));
				droprateMultipliers.addComponent(":§6x"+multFormatter.format(timeAliveMult));
			}
			if(USING_WEAPON_MULTS && entity == null){
				if(!droprateMultipliers.isEmpty()) droprateMultipliers.addComponent(LIST_SEP);
				droprateMultipliers.addComponent(translate("multipliers.weapon-type"));
			}
			else if(weapon != null && Math.abs(1D-weaponMult) > 0.001D){
				if(!droprateMultipliers.isEmpty()) droprateMultipliers.addComponent(LIST_SEP);
				droprateMultipliers.addComponent(translate("multipliers.weapon-type"));
				droprateMultipliers.addComponent(MiscUtils.getMurderItemComponent(weapon, JSON_LIMIT));
				droprateMultipliers.addComponent(":§6x"+multFormatter.format(weaponMult));
			}
			if(Math.abs(1D-permMult) > 0.001D){
				if(!droprateMultipliers.isEmpty()) droprateMultipliers.addComponent(LIST_SEP);
				droprateMultipliers.addComponent(translate("multipliers.perms"));
				droprateMultipliers.addComponent(":§6x"+multFormatter.format(permMult));
			}
			if(USING_LOOTING_MULTS && entity == null){
				if(!droprateMultipliers.isEmpty()) droprateMultipliers.addComponent(LIST_SEP);
				droprateMultipliers.addComponent(LOOTING_COMP);
			}
			else if(Math.abs(1D-lootingMult) > 0.001D || Math.abs(lootingAdd) > 0.001D){
				if(!droprateMultipliers.isEmpty()) droprateMultipliers.addComponent(LIST_SEP);
				droprateMultipliers.addComponent(LOOTING_COMP);
				String lootingMsg = lootingLevel+":";
				if(Math.abs(1D-lootingMult) > 0.001D) lootingMsg += "§6x"+multFormatter.format(lootingMult);
				if(Math.abs(lootingAdd) > 0.001D) lootingMsg += "§e"+(lootingAdd > 0 ? '+' : '-')+multFormatter.format(lootingAdd*100)+"%";
				droprateMultipliers.addComponent(lootingMsg);
			}
			if(!droprateMultipliers.isEmpty()){
				if(!droprateDetails.isEmpty()) droprateDetails.addComponent("\n");
				droprateDetails.addComponent(translate("multipliers.header"));
				droprateDetails.addComponent(droprateMultipliers);
			}
			if(entity != null && rawChance > 0D && Math.abs(finalDropChance-rawChance) > 0.0001D){//.01%
				if(!droprateDetails.isEmpty()) droprateDetails.addComponent("\n");
				droprateDetails.addComponent(String.format(translate("final-drop-rate"), formatDroprate(finalDropChance*100D)));
			}
		} // end else (not a wither_skeleton)
		if(!droprateDetails.isEmpty()){
			if(sender instanceof Player) sendTellraw(sender.getName(), droprateDetails.toString());
			else sender.sendMessage(droprateDetails.toPlainText());
		}
		return true;
	}
}