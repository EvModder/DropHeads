package net.evmodder.DropHeads.commands;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import com.mojang.authlib.GameProfile;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.JunkUtils;
import net.evmodder.DropHeads.TextureKeyLookup;
import net.evmodder.EvLib.EvCommand;
import net.evmodder.EvLib.extras.EntityUtils;
import net.evmodder.EvLib.extras.HeadUtils;
import net.evmodder.EvLib.extras.TellrawUtils.Component;
import net.evmodder.EvLib.extras.TellrawUtils.ListComponent;
import net.evmodder.EvLib.extras.TellrawUtils.RawTextComponent;
import net.evmodder.EvLib.extras.TellrawUtils.SelectorComponent;
import net.evmodder.EvLib.extras.TellrawUtils.TranslationComponent;
import net.evmodder.EvLib.extras.TextUtils;
import net.evmodder.EvLib.extras.WebUtils;
import net.evmodder.EvLib.extras.SelectorUtils.Selector;
import net.evmodder.EvLib.extras.TellrawUtils;

public class CommandSpawnHead extends EvCommand{
	final private DropHeads pl;
	final boolean SHOW_SUBTYPE_IN_TAB_COMPLETE_FOR_BAR = true;
	final boolean USE_PLAYER_DISPLAYNAMES = false;//TODO: move to config, when possible
	final boolean ENABLE_LOG;
	final String LOG_FORMAT;
	final int MAX_IDS_SHOWN = 200;
	final int MAX_ENTITIES_SELECTED = 100;

	// TODO: Move this to a localization file, and maybe re-add aliases (code:,url:,value:)
	final String MOB_PREFIX = "mob:", PLAYER_PREFIX = "player:", HDB_PREFIX = "hdb:", SELF_PREFIX = "self:", CODE_PREFIX = "code:";
	final String AMT_PREFIX = "amount:", GIVETO_PREFIX = "giveto:", SLOT_PREFIX = "slot:";

	final String CMD_MUST_BE_RUN_BY_A_PLAYER = ChatColor.RED + "This command can only be run by in-game players!";
	final String NO_PERMISSION_TO_SPAWN_MOB_HEADS = ChatColor.RED + "You do not have permission to spawn mob heads";
	final String NO_PERMISSION_TO_SPAWN_HDB_HEADS = ChatColor.RED + "You do not have permission to spawn HeadDatabase heads";
	final String NO_PERMISSION_TO_SPAWN_CODE_HEADS = ChatColor.RED + "You do not have permission to spawn heads with custom texture codes";
	final String NO_PERMISSION_TO_SPAWN_PLAYER_HEADS = ChatColor.RED + "You do not have permission to spawn player heads";
	final String ERROR_UNKNOWN_MOB_TEXTURE = ChatColor.RED + "Unable to find head texture for "+MOB_PREFIX+"%s";
	final String GETTING_HEAD_WITH_DATA_VALUE = ChatColor.GRAY + "Getting entity head with data value: %s";
	final String ERROR_UNKNOWN_DATA_VALUE = ChatColor.RED + "Unknown data value for %s: " + ChatColor.YELLOW + "%s";
	final String ERROR_HDB_NOT_INSTALLED = ChatColor.RED + "HeadDatabase plugin needs to be installed to enable ID lookup";
	final String ERROR_HDB_HEAD_NOT_FOUND = ChatColor.RED + "Could not find head with HDB id: %s";
	final String ERROR_UNKNOWN_RAW_TEXTURE = ChatColor.RED + "Unable to find texture for url/code: %s";
	final String ERROR_HEAD_NOT_FOUND = ChatColor.RED + "Head \"%s%s\" not found";
	final String ERROR_GIVETO_NOT_FOUND = ChatColor.RED + "Could not find head recipient: %s";
	final String ERROR_INVALID_SLOT = ChatColor.RED + "Invalid inventory slot: %s";
	final String ERROR_NOT_ENOUGH_INV_SPACE_FOR_SELF = ChatColor.RED + "You do not have enough inventory space";
	final String ERROR_NOT_ENOUGH_INV_SPACE_FOR_TARGET = ChatColor.RED + "%s does not have enough inventory space";
	final String ERROR_SLOT_OCCUPIED_FOR_SELF = ChatColor.RED + "You do not have slot:%s available";
	final String ERROR_SLOT_OCCUPIED_FOR_TARGET = ChatColor.RED + "%s does not have slot:%s available";
	final String ERROR_TOO_MANY_ENTITIES = ChatColor.RED + "Too many entities selected (" + ChatColor.GOLD + "%s" + ChatColor.RED + ")";
	final String ERROR_NO_MATCHING_ENTITIES = ChatColor.RED + "Unable to find entity from selector: %s";
	final String ERROR_MULTIPLE_HEADS_SINGLE_SLOT = ChatColor.RED + "When setting a slot, you can only select a single entity head";
	final String ITEM_WITH_AMOUNT_FORMAT = "%s" + ChatColor.WHITE + "x%s";
	final String SUCCESSFULLY_SPAWNED_HEAD = ChatColor.GREEN + "Spawned Head: " + ChatColor.YELLOW + "%s";
	final String SUCCESSFULLY_SPAWNED_HEADS = ChatColor.GREEN + "Spawned Heads: [" + ChatColor.YELLOW + "%s" + ChatColor.GREEN + "]";
	final String SUCCESSFULLY_GAVE_HEAD = ChatColor.GREEN + "Gave " + ChatColor.YELLOW + "%s" + ChatColor.GREEN + " to " + ChatColor.GRAY + "%s";
	final String SUCCESSFULLY_GAVE_HEADS = ChatColor.GREEN + "Gave: [" + ChatColor.YELLOW + "%s" + ChatColor.GREEN + "] to " + ChatColor.GRAY + "%s";
	final String SUCCESSFULLY_MULTI_GAVE_HEAD =
			ChatColor.GREEN + "Gave " + ChatColor.YELLOW + "%s" + ChatColor.GREEN + " to [" + ChatColor.GRAY + "%s" + ChatColor.GREEN + "]";
	final String SUCCESSFULLY_MULTI_GAVE_HEADS =
			ChatColor.GREEN + "Gave: [" + ChatColor.YELLOW + "%s" + ChatColor.GREEN + "] to [" + ChatColor.GRAY + "%s" + ChatColor.GREEN + "]";

	public CommandSpawnHead(DropHeads plugin){
		super(plugin);
		pl = plugin;
		ENABLE_LOG = pl.getConfig().getBoolean("log.enable", false) && pl.getConfig().getBoolean("log.log-head-command", false);
		LOG_FORMAT = ENABLE_LOG ? pl.getConfig().getString("log.log-head-command-format", "${TIMESTAMP},gethead command,${SENDER},${HEAD}") : null;
	}

	@Override public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args){
		if(args.length == 0 || args.length > 4) return Arrays.asList();
		final String lastArg = args[args.length - 1];

		// Check what types of heads they have permission to spawn
		final List<String> availablePrefixes = new ArrayList<String>();
		final boolean permMobs = sender.hasPermission("dropheads.spawn.mobs");
		final boolean permPlayers = sender.hasPermission("dropheads.spawn.players");
		final boolean permSelf = sender.hasPermission("dropheads.spawn.self");
		final boolean permGiveTo = sender.hasPermission("dropheads.spawn.give");
		if(permMobs || permPlayers) availablePrefixes.add("@");
		if(permMobs) availablePrefixes.add(MOB_PREFIX);
		if(permPlayers) availablePrefixes.add(PLAYER_PREFIX);
		if(sender.hasPermission("dropheads.spawn.hdb")) availablePrefixes.add(HDB_PREFIX);
		if(availablePrefixes.isEmpty()) return permSelf ?
				(permGiveTo ? Arrays.asList(SLOT_PREFIX, AMT_PREFIX, GIVETO_PREFIX) : Arrays.asList(SLOT_PREFIX, AMT_PREFIX)) : null;
		if(permGiveTo) availablePrefixes.add(GIVETO_PREFIX);
		availablePrefixes.add(AMT_PREFIX);
		availablePrefixes.add(SLOT_PREFIX);

		// Remove already-used prefixes
		boolean headNotYetSpecified = true;
		for(int i = 0; i < args.length-1; ++i){
			final int prefixEnd = args[i].indexOf(':');
			final String prefix = args[i].substring(0, prefixEnd + 1).toLowerCase();
			if(prefix.equals(AMT_PREFIX)) availablePrefixes.remove(AMT_PREFIX);
			else if(prefix.equals(GIVETO_PREFIX)){
				availablePrefixes.remove(GIVETO_PREFIX);
				if(args[0].indexOf(':') == -1){
					availablePrefixes.remove(MOB_PREFIX);
					availablePrefixes.remove(PLAYER_PREFIX);
					availablePrefixes.remove(HDB_PREFIX);
					availablePrefixes.remove("@");
				}
			}
			else if(prefixEnd == -1 && i == 0);
			else{
				// These are all mutually-exclusive (they specify the target head)
				availablePrefixes.remove(MOB_PREFIX);
				availablePrefixes.remove(PLAYER_PREFIX);
				availablePrefixes.remove(HDB_PREFIX);
				availablePrefixes.remove("@");
				headNotYetSpecified = false;
			}
		}
		if(headNotYetSpecified){
			availablePrefixes.remove(GIVETO_PREFIX);
			availablePrefixes.remove(AMT_PREFIX);
			availablePrefixes.remove(SLOT_PREFIX);
		}

		// Tab completions of prefixes
		List<String> tabCompletes = availablePrefixes.stream().filter(prefix -> prefix.startsWith(lastArg)).collect(Collectors.toList());

		int prefixEnd = lastArg.indexOf(':');
		String argWithCompletePrefix = lastArg;
		if(prefixEnd == -1 && !tabCompletes.isEmpty()){
			if(tabCompletes.size() == 1){
				// If there is only 1 matching prefix, show suggestions given that prefix
				argWithCompletePrefix = tabCompletes.get(0);
				prefixEnd = argWithCompletePrefix.length() - 1;
			}
			else return tabCompletes; // Otherwise, return the matching prefixes
		}
		tabCompletes.clear();

		final String prefix = prefixEnd == -1 ? availablePrefixes.get(0) : argWithCompletePrefix.substring(0, prefixEnd + 1).toLowerCase();
		final String target = argWithCompletePrefix.substring(prefixEnd + 1);
		final String targetUppercase = target.toUpperCase();

		if(prefix.equals(MOB_PREFIX)){
			for(String key : pl.getAPI().getTextures().keySet()){
				if(key.startsWith(targetUppercase) && key.lastIndexOf('|') <= target.length()) tabCompletes.add(prefix + key);
			}
		}
		else if(prefix.equals(PLAYER_PREFIX)){
			return pl.getServer().getOnlinePlayers().stream()
					.filter(p -> p.getName().toUpperCase().startsWith(targetUppercase)).map(p -> prefix + p.getName())
					.collect(Collectors.toList());
		}
		else if(prefix.equals(HDB_PREFIX)){
			if(pl.getAPI().getHeadDatabaseAPI() == null/* MAX_HDB_ID == -1 */) return null;
//			try{if(Integer.parseInt(target) > MAX_HDB_ID) return null;}
//			catch(NumberFormatException ex){return null;}
			return Arrays.asList(prefix + target);
//			if(MAX_HDB_ID != -1){
//				int numResults = ((""+MAX_HDB_ID).length() - target.length())*11;
//				tabCompletes.addAll(
//						generateNumericCompletions(/*id_prefix=*/target, /*highest_id=*/MAX_HDB_ID, /*max_ids_shown=*/MAX_IDS_SHOWN)
//						.stream().map(id -> prefix+id).collect(Collectors.toList())
//				);
//			}
		}
		else if(prefix.equals(SELF_PREFIX)) tabCompletes.add(sender.getName());
		else if(prefix.equals(AMT_PREFIX)){
			//return Stream.of("64", "2304").filter(amt -> amt.startsWith(target)).map(amt -> prefix + amt).collect(Collectors.toList());
			return Arrays.asList(prefix + target);
		}
		else if(prefix.equals(SLOT_PREFIX)){
			return Stream.concat(IntStream.range(0, target.matches("[0-9]+") ? 36 : 9).mapToObj(i -> String.valueOf(i)),
					Arrays.stream(EquipmentSlot.values()).map(e -> e.name().toLowerCase())).filter(s -> s.startsWith(target))
					.map(s -> prefix+s).collect(Collectors.toList());
		}
		else if(prefix.equals(GIVETO_PREFIX)){
			return Selector.getTabComplete(sender, target).stream().map(s -> prefix+s).collect(Collectors.toList());
		}
		else if(prefix.equals("@")){
			if(!permMobs && !permPlayers) return permSelf ? Arrays.asList("@s") : null;
			tabCompletes = Selector.getTabComplete(sender, target);
			if(!permMobs) tabCompletes.removeIf(s -> s.startsWith("@e"));
			if(!permPlayers) tabCompletes.removeIf(s -> s.startsWith("@a") || s.startsWith("@p") || s.startsWith("@r"));
			if(!permSelf) tabCompletes.removeIf(s -> s.startsWith("@s"));
		}
		return tabCompletes;
	}

	private GameProfile searchForPlayer(String target){
		if(!target.matches("[a-zA-Z0-9_-]+")) return null;
//		@SuppressWarnings("deprecation")
//		OfflinePlayer p = pl.getServer().getOfflinePlayer(target);
//		if(p != null && p.hasPlayedBefore()) return new GameProfile(p.getUniqueId(), p.getName());
//		try{
//			p = pl.getServer().getOfflinePlayer(UUID.fromString(target));
//			if(p != null && p.hasPlayedBefore()) return new GameProfile(p.getUniqueId(), p.getName());
//		}catch(IllegalArgumentException ex){}
		return WebUtils.getGameProfile(target);
	}

	private static class HeadFromString{
		public final ItemStack head;
		public final boolean noFurtherError;
		public final String target;
		public HeadFromString(ItemStack h, boolean u, String t){head=h; noFurtherError=u; target=t;}
	}
	private HeadFromString getHeadFromTargetString(String fullTarget, CommandSender sender){
		int prefixEnd = fullTarget.indexOf(':');
		String prefix = prefixEnd == -1 ? "" : fullTarget.substring(0, prefixEnd + 1).toLowerCase();
		if(prefix.equals("http:") || prefix.equals("https:")){prefix = CODE_PREFIX; prefixEnd = -1;}
		String textureKey = fullTarget.substring(prefixEnd + 1);

		String target, extraData;
		int i = textureKey.indexOf('|');
		if(i != -1){
			target = textureKey.substring(0, i);
			extraData = textureKey.substring(i + 1).toUpperCase();
		}
		else{
			target = textureKey;
			extraData = null;
		}
		ItemStack head = null;

		if(prefix.equals(MOB_PREFIX) || prefix.equals("key:") || (prefix.isEmpty() && pl.getAPI().textureExists(target.toUpperCase()))){
			target = target.toUpperCase();
			if(!sender.hasPermission("dropheads.spawn.mobs")){
				sender.sendMessage(NO_PERMISSION_TO_SPAWN_MOB_HEADS);
				return new HeadFromString(null, true, null);
			}
			if(!pl.getAPI().textureExists(target) && !pl.getAPI().textureExists(textureKey)){
				sender.sendMessage(String.format(ERROR_UNKNOWN_MOB_TEXTURE, target));
				return new HeadFromString(null, false, null);
			}
			EntityType eType = EntityUtils.getEntityByName(target);
			if(eType != null && eType != EntityType.UNKNOWN){
				textureKey = eType.name() + (extraData == null ? "" : "|" + extraData);
			}
			if(extraData != null){
				if(pl.getAPI().textureExists(textureKey)) sender.sendMessage(String.format(GETTING_HEAD_WITH_DATA_VALUE, extraData));
				else sender.sendMessage(String.format(ERROR_UNKNOWN_DATA_VALUE, eType, extraData));
			}
			head = pl.getAPI().getHead(eType, textureKey);
		}
		else if(prefix.equals(HDB_PREFIX)){
			if(!sender.hasPermission("dropheads.spawn.hdb")){
				sender.sendMessage(NO_PERMISSION_TO_SPAWN_HDB_HEADS);
				return new HeadFromString(null, true, null);
			}
			if(pl.getAPI().getHeadDatabaseAPI() == null){
				sender.sendMessage(ERROR_HDB_NOT_INSTALLED);
				return new HeadFromString(null, true, null);
			}
			if(!pl.getAPI().getHeadDatabaseAPI().isHead(target)){
				sender.sendMessage(String.format(ERROR_HDB_HEAD_NOT_FOUND, target));
				return new HeadFromString(null, true, null);
			}
			ItemStack unwrappedHDBhead = pl.getAPI().getHeadDatabaseAPI().getItemHead(target);
			head = pl.getAPI().getHead(HeadUtils.getGameProfile((SkullMeta)unwrappedHDBhead.getItemMeta()));
		}
		else if(prefix.equals(CODE_PREFIX) || (prefix.isEmpty() && target.length() > TextUtils.MAX_PLAYERNAME_MONO_WIDTH
				&& searchForPlayer(target) == null)){
			if(!sender.hasPermission("dropheads.spawn.code")){
				sender.sendMessage(NO_PERMISSION_TO_SPAWN_CODE_HEADS);
				return new HeadFromString(null, true, null);
			}
			String url = WebUtils.getTextureURL(target, /*verify=*/true);
			if(url == null){
				sender.sendMessage(String.format(ERROR_UNKNOWN_RAW_TEXTURE, target));
				return new HeadFromString(null, false, null);
			}
			target = Base64.getEncoder().encodeToString(
					("{\"textures\":{\"SKIN\":{\"url\":\""+url+"\"}}}").getBytes(StandardCharsets.ISO_8859_1));
//			pl.getLogger().fine("recovered url: "+WebUtils.getTextureURL(new String(target.getBytes()), /*verify=*/true));
			String longestMatchingKey = "";
			for(Entry<String, String> entry : pl.getAPI().getTextures().entrySet()){
				if(entry.getValue().equals(target) && entry.getKey().length() > longestMatchingKey.length()){
					longestMatchingKey = entry.getKey();
					break;
				}
			}
			if(!longestMatchingKey.isEmpty()) {
				head = pl.getAPI().getHead((EntityType)null, longestMatchingKey);
			}
			if(head == null){
				head = pl.getAPI().getHead(target.getBytes());
			}
		}
		else if(prefix.equals(PLAYER_PREFIX) || (prefix.isEmpty()/* && ... */)){
			GameProfile profile = searchForPlayer(target);
			if(profile != null){
				if(!sender.hasPermission("dropheads.spawn.players")
						&& (!profile.getName().equals(sender.getName()) || !sender.hasPermission("dropheads.spawn.self"))){
					sender.sendMessage(NO_PERMISSION_TO_SPAWN_PLAYER_HEADS);
					return new HeadFromString(null, true, null);
				}
				target = profile.getName();
				head = pl.getAPI().getHead(profile);
			}
		}
		if(head == null) sender.sendMessage(String.format(ERROR_HEAD_NOT_FOUND, prefix, target));
		return new HeadFromString(head, true, target);
	}

	private boolean giveHeadItem(Entity entity, ItemStack item, String slot){
		if(slot == null || slot.matches("[0-9]+")){
			// This should be unreachable due to earlier checks, but still nice to have in case code breaks.
			if(!(entity instanceof InventoryHolder)) pl.getLogger().severe("Cannot give item to non-InventoryHolder!");
			Inventory inv = ((InventoryHolder)entity).getInventory();
			if(slot == null) return inv.addItem(item).isEmpty();
			final int intSlot = Integer.parseInt(slot);
			final ItemStack oldItem = inv.getItem(intSlot);
			final boolean canReplace = oldItem == null || oldItem.getType() == Material.AIR;
			if(canReplace) inv.setItem(intSlot, item);
			return canReplace;
		}
		// This should be unreachable due to earlier checks, but still nice to have in case code breaks.
		if(!(entity instanceof LivingEntity)) pl.getLogger().severe("Cannot give item to non-LivingEntity!");
		return JunkUtils.setIfEmpty(((LivingEntity)entity).getEquipment(), item, EquipmentSlot.valueOf(slot.toUpperCase()));
	}

	private void logGiveHeadCommand(String headName, String senderName, int amount, String recipients){
		pl.writeToLogFile(LOG_FORMAT
				.replaceAll("(?i)\\$\\{HEAD\\}", headName)
				.replaceAll("(?i)\\$\\{SENDER\\}", senderName)
				.replaceAll("(?i)\\$\\{TIMESTAMP\\}", "" + System.currentTimeMillis())
				.replaceAll("(?i)\\$\\{AMOUNT\\}", "" + amount)
				.replaceAll("(?i)\\$\\{RECIPIENT\\}", recipients));
	}

	@Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
		// Parse arguments
		int amount = -1;
		for(int i=0; i<args.length; ++i){
			if(args[i].matches("(?i:"+AMT_PREFIX+")[0-9]+")){
				amount = Integer.parseInt(args[i].substring(AMT_PREFIX.length()));
				args = ArrayUtils.remove(args, i);
				break;
			}
		}
		String slot = null;
		for(int i=0; i<args.length; ++i){
			if(args[i].matches("(?i:"+SLOT_PREFIX+").*")){
				slot = args[i].substring(SLOT_PREFIX.length());
				if(!slot.matches("[1-2]?[0-9]|3[0-5]")){ // Valid number slots are 0 to 35
					try{EquipmentSlot.valueOf(slot.toUpperCase());}
					catch(IllegalArgumentException ex){
						sender.sendMessage(String.format(ERROR_INVALID_SLOT, slot));
						return true;
					}
				}
				args = ArrayUtils.remove(args, i);
				break;
			}
		}
		String giveTo = null;
		for(int i=0; i<args.length; ++i){
			if(args[i].matches("(?i:"+GIVETO_PREFIX+").*")){
				giveTo = args[i].substring(GIVETO_PREFIX.length());
				args = ArrayUtils.remove(args, i);
				break;
			}
		}
		final boolean permGive = sender.hasPermission("dropheads.spawn.give");
		if(permGive && giveTo == null && args.length > 1){
			giveTo = args[0];
			args = Arrays.copyOfRange(args, 1, args.length);
		}
		final String fullTarget = args.length == 0 ? PLAYER_PREFIX + sender.getName() : String.join("_", args);

		// Get head receipient(s)
		ArrayList<Entity> giveTargets = new ArrayList<>();
		if(giveTo != null && permGive){
			try{
				Collection<Entity> selected = Selector.fromString(sender, giveTo).resolve();
				if(selected != null){
					if(selected.size() > MAX_ENTITIES_SELECTED){
						sender.sendMessage(String.format(ERROR_TOO_MANY_ENTITIES, selected.size()));
						return true;
					}
					if(selected.size() == 0){
						sender.sendMessage(String.format(ERROR_NO_MATCHING_ENTITIES, giveTo));
						return true;
					}
					final boolean isEquipmentSlot = slot != null && !slot.matches("[0-9]+");
					for(Entity e : selected){
						if(!isEquipmentSlot && e instanceof InventoryHolder) giveTargets.add(e);
						else if(isEquipmentSlot && e instanceof LivingEntity) giveTargets.add(e);
					}
					args = Arrays.copyOfRange(args, 1, args.length);
				}
			}
			catch(IllegalArgumentException ex){}
		}
		if(giveTargets.isEmpty()){
			if(giveTo != null){
				sender.sendMessage(String.format(ERROR_GIVETO_NOT_FOUND, giveTo));
				return true;
			}
			if(sender instanceof Player) giveTargets.add((Player)sender);
			else{
				sender.sendMessage(CMD_MUST_BE_RUN_BY_A_PLAYER);
				return true;
			}
		}

		// Get head item(s)
		ArrayList<ItemStack> headItems = new ArrayList<>();
		try{
			Collection<Entity> selected = Selector.fromString(sender, fullTarget).resolve();
			if(selected == null) throw new IllegalArgumentException("null");
			if(selected.size() > MAX_ENTITIES_SELECTED){
				sender.sendMessage(String.format(ERROR_TOO_MANY_ENTITIES, selected.size()));
				return true;
			}
			final boolean permMobs = sender.hasPermission("dropheads.spawn.mobs");
			final boolean permPlayers = sender.hasPermission("dropheads.spawn.players");
			for(Entity e : selected){
				if(!permPlayers && e instanceof Player) continue;
				if(!permMobs && !(e instanceof Player)) continue;
				ItemStack head = pl.getAPI().getHead(e);
				if(head == null) continue;
				if(amount > 0) head.setAmount(amount);
				headItems.add(head);
				if(ENABLE_LOG) logGiveHeadCommand(
						e instanceof Player ? e.getName() : TextureKeyLookup.getTextureKey(e),
						sender.getName(), head.getAmount(), giveTargets.toString());
			}
			if(headItems.isEmpty()){
				sender.sendMessage(String.format(ERROR_HEAD_NOT_FOUND, /*prefix=*/"", fullTarget));
				return false;
			}
			if(headItems.size() > 1 && slot != null && !slot.matches("[0-9]+")){
				sender.sendMessage(ERROR_MULTIPLE_HEADS_SINGLE_SLOT);
				return true;
			}
		}
		catch(IllegalArgumentException ex){
			HeadFromString headFromString = getHeadFromTargetString(fullTarget, sender);
			ItemStack head = headFromString.head;
			if(head == null) return headFromString.noFurtherError;
			if(amount > 0) head.setAmount(amount);
			headItems.add(head);
			if(ENABLE_LOG) logGiveHeadCommand(headFromString.target, sender.getName(), head.getAmount(), giveTargets.toString());
		}

		// Give the head item(s)
		HashMap<String, Integer> amtOfEachHead = new HashMap<>();
		HashSet<UUID> notEnoughInvSpaceWarned = new HashSet<>();
		ArrayList<Component> headNameComps = new ArrayList<>();
		ArrayList<Component> recipientComps = new ArrayList<>();
		boolean firstHeadSoAddRecipients = true;
		for(ItemStack head : headItems){
			Component headNameComp = JunkUtils.getItemDisplayNameComponent(head);
			String headNameStr = headNameComp.toString();
			int amtSum = amtOfEachHead.getOrDefault(headNameStr, 0);
			if(amtSum == 0) headNameComps.add(headNameComp);
			else if(amount > 0) continue;  // Don't give heads for multiple mobs of the same type if 'amount' is specified
			amtOfEachHead.put(headNameStr, amtSum + head.getAmount());
			for(Entity giveTarget : giveTargets) {
				final boolean isSelf = giveTarget instanceof Player && sender.getName().equals(((Entity)giveTarget).getName());
				if(!giveHeadItem(giveTarget, head, slot)){
					if(notEnoughInvSpaceWarned.add(((Entity)giveTarget).getUniqueId())){
						if(isSelf) sender.sendMessage(slot != null ? String.format(ERROR_SLOT_OCCUPIED_FOR_SELF, slot)
								: ERROR_NOT_ENOUGH_INV_SPACE_FOR_SELF);
						else sender.sendMessage(slot != null ? String.format(ERROR_SLOT_OCCUPIED_FOR_TARGET, ((Entity)giveTarget).getName(), slot)
								: String.format(ERROR_NOT_ENOUGH_INV_SPACE_FOR_TARGET, ((Entity)giveTarget).getName()));
					}
					if(notEnoughInvSpaceWarned.size() == giveTargets.size()) return true;
				}
				else if(!isSelf && firstHeadSoAddRecipients){
					recipientComps.add(new SelectorComponent(giveTarget.getUniqueId(), USE_PLAYER_DISPLAYNAMES));
				}
			}
			firstHeadSoAddRecipients = false;
		}

		// Send success message
		final String messageFormatStr =
				recipientComps.size() == 0 ? (amtOfEachHead.size() > 1 ? SUCCESSFULLY_SPAWNED_HEADS : SUCCESSFULLY_SPAWNED_HEAD)
				: recipientComps.size() == 1 ? (amtOfEachHead.size() > 1 ? SUCCESSFULLY_GAVE_HEADS : SUCCESSFULLY_GAVE_HEAD)
				: (amtOfEachHead.size() > 1 ? SUCCESSFULLY_MULTI_GAVE_HEADS : SUCCESSFULLY_MULTI_GAVE_HEAD);

		// TODO: This assumes the message has a default color & formats when it actually might not.
		final Component msgColorAndFormats = TellrawUtils.getCurrentColorAndFormatProperties(messageFormatStr);

		ListComponent recipientListComp = new ListComponent();
		ListComponent headItemListComp = new ListComponent();
		if(recipientComps.size() == 1) recipientListComp.addComponent(recipientComps.get(0));
		else if(recipientComps.size() > 1){
			// TODO: line below assumes [recipient-list] is the LAST "%", which might not be a good assumption.
			final Component recipientColorAndFormats = TellrawUtils.getCurrentColorAndFormatProperties(
					messageFormatStr.substring(0, messageFormatStr.lastIndexOf('%')));
			if(recipientColorAndFormats != null) recipientListComp.addComponent(recipientColorAndFormats);
			for(int i=0; i<recipientComps.size(); ++i){
//				recipientListComp.addComponent(recipientColor);
				recipientListComp.addComponent(recipientComps.get(i));
				if(i != recipientComps.size()-1){
					if(!recipientColorAndFormats.samePropertiesAs(msgColorAndFormats)) recipientListComp.addComponent(msgColorAndFormats);
					recipientListComp.addComponent(", ");
				}
			}
		}
		if(amtOfEachHead.size() == 1){
			final Integer amt = amtOfEachHead.values().iterator().next();
			headItemListComp.addComponent(amt == 1 ? headNameComps.get(0) : new TranslationComponent(
					ITEM_WITH_AMOUNT_FORMAT, headNameComps.get(0), new RawTextComponent(amt.toString())));
		}
		else{
			// TODO: line below assumes [head-list] is the FIRST "%", which might not be a good assumption.
			final Component headColorAndFormats = TellrawUtils.getCurrentColorAndFormatProperties(
					messageFormatStr.substring(0, messageFormatStr.indexOf('%')));
			final Component amtColorAndFormats = TellrawUtils.getCurrentColorAndFormatProperties(ITEM_WITH_AMOUNT_FORMAT);
			if(headColorAndFormats != null) headItemListComp.addComponent(headColorAndFormats);
			for(int i=0; i<headNameComps.size(); ++i){
				Integer amt = amtOfEachHead.get(headNameComps.get(i).toString());
				if(amt > 1){
					headItemListComp.addComponent(new TranslationComponent(ITEM_WITH_AMOUNT_FORMAT,
							headNameComps.get(i), new RawTextComponent(amt.toString())));
					if(i != headNameComps.size()-1){
						if(!amtColorAndFormats.samePropertiesAs(msgColorAndFormats)) headItemListComp.addComponent(msgColorAndFormats);
						headItemListComp.addComponent(", ");
					}
				}
				else{
					headItemListComp.addComponent(headNameComps.get(i));
					if(i != headNameComps.size()-1){
						if(!headColorAndFormats.samePropertiesAs(msgColorAndFormats)) headItemListComp.addComponent(msgColorAndFormats);
						headItemListComp.addComponent(", ");
					}
				}
			}
		}
		final TranslationComponent successMessage = recipientListComp.isEmpty()
				? new TranslationComponent(messageFormatStr, headItemListComp)
				: new TranslationComponent(messageFormatStr, headItemListComp, recipientListComp);
		pl.getLogger().info("tellraw toString: "+successMessage.toString());
		pl.getServer().dispatchCommand(pl.getServer().getConsoleSender(), "minecraft:tellraw "+sender.getName()+" "+successMessage.toString());
		return true;
	}
}