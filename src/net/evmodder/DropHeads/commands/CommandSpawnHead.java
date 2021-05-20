package net.evmodder.DropHeads.commands;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import com.mojang.authlib.GameProfile;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.JunkUtils;
import net.evmodder.EvLib.EvCommand;
import net.evmodder.EvLib.extras.EntityUtils;
import net.evmodder.EvLib.extras.HeadUtils;
import net.evmodder.EvLib.extras.TellrawUtils.ListComponent;
import net.evmodder.EvLib.extras.TextUtils;
import net.evmodder.EvLib.extras.WebUtils;

public class CommandSpawnHead extends EvCommand{
	final private DropHeads pl;
	final boolean SHOW_GRUMM_IN_TAB_COMPLETE;
	final boolean SHOW_GRUMM_IN_TAB_COMPLETE_FOR_BAR = true;
	final boolean ENABLE_LOG;
	final String LOG_FORMAT;
	final int MAX_IDS_SHOWN = 200;
	final int JSON_LIMIT = 15000;

	// TODO: Move this to a localization file, and maybe re-add aliases (code:,url:,value:)
	final String MOB_PREFIX = "mob:", PLAYER_PREFIX = "player:", HDB_PREFIX = "hdb:", SELF_PREFIX = "self:", CODE_PREFIX = "code:";

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
	final String ERROR_NOT_ENOUGH_INV_SPACE = ChatColor.RED + "Not enough inventory space";
	final String SUCCESSFULLY_SPAWNED_HEAD = ChatColor.GREEN + "Spawned Head: " + ChatColor.YELLOW + "%s";//todo: %s => "x64" for amount?

	public CommandSpawnHead(DropHeads plugin){
		super(plugin);
		pl = plugin;
		SHOW_GRUMM_IN_TAB_COMPLETE = pl.getConfig().getBoolean("show-grumm-in-tab-complete", false);
		ENABLE_LOG = pl.getConfig().getBoolean("log.enable", false) && pl.getConfig().getBoolean("log.log-head-command", false);
		LOG_FORMAT = ENABLE_LOG ? pl.getConfig().getString("log.log-head-command-format", "${TIMESTAMP},gethead command,${SENDER},${HEAD}") : null;
	}

	@Override public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args){
		if(args.length != 1 || sender instanceof Player == false) return null;

		// Check what types of heads they have permission to spawn
		final List<String> availablePrefixes = new ArrayList<String>();
		if(sender.hasPermission("dropheads.spawn.mobs")) availablePrefixes.add(MOB_PREFIX);
		if(sender.hasPermission("dropheads.spawn.players")) availablePrefixes.add(PLAYER_PREFIX);
		if(sender.hasPermission("dropheads.spawn.hdb")) availablePrefixes.add(HDB_PREFIX);
		if(availablePrefixes.isEmpty()) return sender.hasPermission("dropheads.spawn.self") ? Arrays.asList(sender.getName()) : null;

		// Tab completions of prefixes
		final List<String> tabCompletes = availablePrefixes.stream().filter(prefix -> prefix.startsWith(args[0])).collect(Collectors.toList());

		int prefixEnd = args[0].indexOf(':');
		if(prefixEnd == -1 && !tabCompletes.isEmpty()){
			if(tabCompletes.size() == 1){
				// If there is only 1 matching prefix, show suggestions given
				// that prefix
				args[0] = tabCompletes.get(0);
				prefixEnd = args[0].length() - 1;
			}
			else return tabCompletes; // Otherwise, return the matching prefixes
		}
		tabCompletes.clear();

		String prefix = prefixEnd == -1 ? availablePrefixes.get(0) : args[0].substring(0, prefixEnd + 1).toLowerCase();
		String target = args[0].substring(prefixEnd + 1).toUpperCase();

		if(prefix.equals(MOB_PREFIX)){
			for(String key : pl.getAPI().getTextures().keySet()){
				if(key.startsWith(target) && (SHOW_GRUMM_IN_TAB_COMPLETE || !key.endsWith("|GRUMM")
						|| (SHOW_GRUMM_IN_TAB_COMPLETE_FOR_BAR && target.contains(key.substring(0, key.length() - 5)))))
					tabCompletes.add(prefix + key);
			}
		}
		else if(prefix.equals(PLAYER_PREFIX)){
			return pl.getServer().getOnlinePlayers().stream().filter(p -> p.getName().toUpperCase().startsWith(target)).map(p -> prefix + p.getName())
					.collect(Collectors.toList());
		}
		else if(prefix.equals(HDB_PREFIX)){
			if(pl.getAPI().getHeadDatabaseAPI() == null/* MAX_HDB_ID == -1 */) return null;
			// try{if(Integer.parseInt(target) > MAX_HDB_ID) return null;}
			// catch(NumberFormatException ex){return null;}
			return Arrays.asList(prefix + target);
			// if(MAX_HDB_ID != -1){
			// int numResults = ((""+MAX_HDB_ID).length() - target.length())*11;
			// tabCompletes.addAll(
			// generateNumericCompletions(/*id_prefix=*/target,
			// /*highest_id=*/MAX_HDB_ID,
			// /*max_ids_shown=*/MAX_IDS_SHOWN).stream().map(id ->
			// prefix+id).collect(Collectors.toList())
			// );
			// }
		}
		else if(prefix.equals(SELF_PREFIX)) tabCompletes.add(sender.getName());
		return tabCompletes;
	}

	private OfflinePlayer searchForPlayer(String target){
		if(!target.matches("[a-zA-Z0-9_]+")) return null;
		@SuppressWarnings("deprecation")
		OfflinePlayer p = pl.getServer().getOfflinePlayer(target);
		if(p.hasPlayedBefore() || WebUtils.checkPlayerExists(p.getName())) return p;
		try{
			p = pl.getServer().getOfflinePlayer(UUID.fromString(target));
			if(p.hasPlayedBefore() || WebUtils.checkPlayerExists(p.getName())) return p;
		}catch(IllegalArgumentException ex){}
		return null;
	}

	@Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
		if(sender instanceof Player == false){
			sender.sendMessage(CMD_MUST_BE_RUN_BY_A_PLAYER);
			return true;
		}

		int amount = 1;
		if(args.length > 0 && args[args.length - 1].matches("[0-9]+")){
			amount = Integer.parseInt(args[args.length - 1]);
			args = Arrays.copyOfRange(args, 0, args.length - 1);
		}

		String fullTarget = args.length == 0 ? PLAYER_PREFIX + sender.getName() : String.join("_", args);
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
				return true;
			}
			if(!pl.getAPI().textureExists(target) && !pl.getAPI().textureExists(textureKey)){
				sender.sendMessage(String.format(ERROR_UNKNOWN_MOB_TEXTURE, target));
				return false;
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
				return true;
			}
			if(pl.getAPI().getHeadDatabaseAPI() == null){
				sender.sendMessage(ERROR_HDB_NOT_INSTALLED);
				return true;
			}
			if(!pl.getAPI().getHeadDatabaseAPI().isHead(target)){
				sender.sendMessage(String.format(ERROR_HDB_HEAD_NOT_FOUND, target));
				return true;
			}
			ItemStack unwrappedHDBhead = pl.getAPI().getHeadDatabaseAPI().getItemHead(target);
			head = pl.getAPI().getHead(HeadUtils.getGameProfile((SkullMeta)unwrappedHDBhead.getItemMeta()));
		}
		else if(prefix.equals(CODE_PREFIX) || (prefix.isEmpty() && target.length() > TextUtils.MAX_PLAYERNAME_MONO_WIDTH
				&& searchForPlayer(target) == null)){
			if(!sender.hasPermission("dropheads.spawn.code")){
				sender.sendMessage(NO_PERMISSION_TO_SPAWN_CODE_HEADS);
				return true;
			}
			String url = WebUtils.getTextureURL(target);
			if(url == null){
				sender.sendMessage(String.format(ERROR_UNKNOWN_RAW_TEXTURE, target));
				return false;
			}
			target = Base64.getEncoder().encodeToString(
					("{\"textures\":{\"SKIN\":{\"url\":\""+url+"\"}}}").getBytes(StandardCharsets.ISO_8859_1));
			System.out.println("recovered url: "+WebUtils.getTextureURL(new String(target.getBytes())));
			for(Entry<String, String> entry : pl.getAPI().getTextures().entrySet()){
				if(entry.getValue().equals(target)){
					head = pl.getAPI().getHead((EntityType)null, entry.getKey());
					break;
				}
			}
			if(head == null){
				head = pl.getAPI().getHead(target.getBytes());
			}
		}
		else if(prefix.equals(PLAYER_PREFIX) || (prefix.isEmpty()/* && ... */)){
			OfflinePlayer p = searchForPlayer(target);
			if(p != null){
				if(!sender.hasPermission("dropheads.spawn.players")
						&& (!p.getName().equals(sender.getName()) || !sender.hasPermission("dropheads.spawn.self"))){
					sender.sendMessage(NO_PERMISSION_TO_SPAWN_PLAYER_HEADS);
					return true;
				}
				target = p.getName();
				head = pl.getAPI().getHead(new GameProfile(p.getUniqueId(), p.getName()));
			}
		}

		// Give head item
		if(head == null) sender.sendMessage(String.format(ERROR_HEAD_NOT_FOUND, prefix, target));
		else{
			ListComponent successMessage = new ListComponent();
			successMessage.addComponent(SUCCESSFULLY_SPAWNED_HEAD);
			successMessage.replaceRawDisplayTextWithComponent("%s", JunkUtils.getItemDisplayNameComponent(head, JSON_LIMIT));
			head.setAmount(amount);
			HashMap<Integer, ItemStack> leftovers = ((Player)sender).getInventory().addItem(head);
			if(!leftovers.isEmpty()){
				sender.sendMessage(ERROR_NOT_ENOUGH_INV_SPACE);
				if(leftovers.values().iterator().next().getAmount() == amount) return true;
			}
			pl.getServer().dispatchCommand(pl.getServer().getConsoleSender(), "minecraft:tellraw "+sender.getName()+" "+successMessage.toString());
			if(ENABLE_LOG){
				String logEntry = LOG_FORMAT.replaceAll("(?i)\\$\\{HEAD\\}", target).replaceAll("(?i)\\$\\{SENDER\\}", sender.getName())
						.replaceAll("(?i)\\$\\{TIMESTAMP\\}", "" + System.currentTimeMillis());
				pl.writeToLogFile(logEntry);
			}
		}
		return true;
	}
}