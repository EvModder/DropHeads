package net.evmodder.DropHeads.commands;

import java.util.ArrayList;
import java.util.Arrays;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import me.arcaniax.hdb.api.DatabaseLoadEvent;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.JunkUtils;
import net.evmodder.EvLib.EvCommand;
import net.evmodder.EvLib.extras.HeadUtils;
import net.evmodder.EvLib.extras.TextUtils;
import net.evmodder.EvLib.extras.WebUtils;

public class CommandSpawnHead extends EvCommand{
	final private DropHeads pl;
	final boolean SHOW_GRUMM_IN_TAB_COMPLETE;
	final boolean SHOW_GRUMM_IN_TAB_COMPLETE_FOR_BAR = true;
	final boolean ENABLE_LOG;
	final String LOG_FORMAT;
	private HeadDatabaseAPI api = null;
	private int MAX_HDB_ID = -1;
	final int MAX_IDS_SHOWN = 200;

	public CommandSpawnHead(DropHeads plugin) {
		super(plugin);
		pl = plugin;
		SHOW_GRUMM_IN_TAB_COMPLETE = pl.getConfig().getBoolean("show-grumm-in-tab-complete", false);
		ENABLE_LOG = pl.getConfig().getBoolean("log.enable", false) && pl.getConfig().getBoolean("log.log-head-command", false);
		LOG_FORMAT = ENABLE_LOG ? pl.getConfig().getString("log.log-head-command-format", "${TIMESTAMP},gethead command,${SENDER},${HEAD}") : null;

		boolean hdbInstalled = true;
		try{Class.forName("me.arcaniax.hdb.api.DatabaseLoadEvent");}
		catch(ClassNotFoundException ex){hdbInstalled = false;}

		if(hdbInstalled) pl.getServer().getPluginManager().registerEvents(new Listener(){
			@EventHandler public void onDatabaseLoad(DatabaseLoadEvent e){
				api = new HeadDatabaseAPI();
				MAX_HDB_ID = JunkUtils.exponentialSearch(
					id -> {
						try{return api.getItemHead(""+id) != null;}
						catch(NullPointerException nullpointer){return false;}
					},
					20000
				);
			}
		}, pl);
	}

	@Override public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args){
		if(args.length == 1 && sender instanceof Player){
			final List<String> tabCompletes = new ArrayList<String>();
			for(String prefix : Arrays.asList("player:", "mob:", "hdb:")){
				if(prefix.startsWith(args[0])) tabCompletes.add(prefix);
			}
			int prefixEnd = args[0].indexOf(':');
			if(prefixEnd == -1 && !tabCompletes.isEmpty()){
				if(tabCompletes.size() == 1) args[0] = tabCompletes.get(0);
				else return tabCompletes;
			}
			tabCompletes.clear();

			String prefix = prefixEnd == -1 ? "mob:" : args[0].substring(0, prefixEnd + 1).toLowerCase();
			String target = args[0].substring(prefixEnd + 1).toUpperCase();

			if(prefix.equals("mob:")){
				for(String key : pl.getAPI().getTextures().keySet()){
					if(key.startsWith(target) &&
							(SHOW_GRUMM_IN_TAB_COMPLETE || !key.endsWith("|GRUMM") ||
									(SHOW_GRUMM_IN_TAB_COMPLETE_FOR_BAR && target.contains(key.substring(0, key.length()-5))))
					) tabCompletes.add(prefix+key);
				}
			}
			else if(prefix.equals("player:")){
				tabCompletes.addAll(
						pl.getServer().getOnlinePlayers().stream()
						.filter(p -> p.getName().toUpperCase().startsWith(target))
						.map(p -> prefix+p.getName())
						.collect(Collectors.toList())
				);
			}
			else if(prefix.equals("hdb:")){
				if(MAX_HDB_ID == -1) return null;
				try{if(Integer.parseInt(target) > MAX_HDB_ID) return null;}
				catch(NumberFormatException ex){return null;}
				return Arrays.asList(prefix+target);
//				if(MAX_HDB_ID != -1){
//					int numResults = ((""+MAX_HDB_ID).length() - target.length())*11;
//					tabCompletes.addAll(
//						generateNumericCompletions(/*id_prefix=*/target, /*highest_id=*/MAX_HDB_ID,
//								/*max_ids_shown=*/MAX_IDS_SHOWN).stream().map(id -> prefix+id).collect(Collectors.toList())
//					);
//				}
			}
			return tabCompletes;
		}
		return null;
	}

	private OfflinePlayer searchForPlayer(String target){
		@SuppressWarnings("deprecation")
		OfflinePlayer p = pl.getServer().getOfflinePlayer(target);
		if(p.hasPlayedBefore() || WebUtils.checkExists(p.getName())) return p;
		try{
			p = pl.getServer().getOfflinePlayer(UUID.fromString(target));
			if(p.hasPlayedBefore() || WebUtils.checkExists(p.getName())) return p;
		}
		catch(IllegalArgumentException ex){}
		return null;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
		if(sender instanceof Player == false){
			sender.sendMessage(ChatColor.RED+"This command can only be run by in-game players!");
			return true;
		}

		String fullTarget = args.length == 0 ? "player:"+sender.getName() : String.join("_", args);
		int prefixEnd = fullTarget.indexOf(':');
		String prefix = prefixEnd == -1 ? "" : fullTarget.substring(0, prefixEnd + 1).toLowerCase();
		String textureKey = fullTarget.substring(prefixEnd + 1);

		String target, extraData;
		int i = textureKey.indexOf('|');
		if(i != -1){
			target = textureKey.substring(0, i);
			extraData = textureKey.substring(i+1).toUpperCase();
		}
		else{
			target = textureKey;
			extraData = null;
		}
		ItemStack head = null;

		if(prefix.equals("mob:") || prefix.equals("key:") || (prefix.isEmpty() && pl.getAPI().textureExists(target.toUpperCase()))){
			target = target.toUpperCase();
			if(!pl.getAPI().textureExists(target)){
				sender.sendMessage(ChatColor.RED+"Unable to find head texture for mob: "+target);
				return false;
			}
			EntityType eType = JunkUtils.getEntityByName(target);
			if(eType != null && eType != EntityType.UNKNOWN){
				textureKey = eType.name() + (extraData == null ? "" : "|"+extraData);
			}
			if(extraData != null){
				if(pl.getAPI().textureExists(textureKey))
					sender.sendMessage(ChatColor.GRAY+"Getting entity head with data value: "+extraData);
				else
					sender.sendMessage(ChatColor.RED+"Unknown data value for "+eType+": "+ChatColor.YELLOW+extraData);
			}
			head = pl.getAPI().getHead(eType, textureKey);
		}
		else if(prefix.equals("hdb:")){
			if(api == null){
				sender.sendMessage(ChatColor.RED+"HeadDatabase plugin needs to be installed to enable ID lookup");
				return true;
			}
			try{head = api.getItemHead(target);}
			catch(NullPointerException nullpointer){
//				pl.getLogger().info(ChatColor.RED+"Could not find head with id "+target);
//				return true;
			}
		}
		else if(prefix.equals("code:") || (prefix.isEmpty() && target.length() > TextUtils.MAX_PLAYERNAME_MONO_WIDTH && searchForPlayer(target) == null)){
			String headName = ChatColor.YELLOW+"UNKNOWN Head";
			for(Entry<String, String> entry : pl.getAPI().getTextures().entrySet()){
				if(entry.getValue().equals(target)) headName = pl.getAPI().getHeadNameFromKey(entry.getKey());
			}
			head = HeadUtils.makeSkull(target, headName);
		}
		else if(prefix.equals("player:") || (prefix.isEmpty()/* && ...*/)){
			OfflinePlayer p = searchForPlayer(target);
			if(p != null){
				target = p.getName();
				head = HeadUtils.getPlayerHead(p);
			}
		}

		// Give head item
		if(head == null) sender.sendMessage(ChatColor.RED+"Head \""+prefix+target+"\" not found");
		else{
			String headName = head.hasItemMeta() && head.getItemMeta().hasDisplayName()
					? head.getItemMeta().getDisplayName() : TextUtils.getNormalizedName(head.getType());
			((Player)sender).getInventory().addItem(head);
			sender.sendMessage(ChatColor.GREEN+"Spawned Head: " + ChatColor.YELLOW + headName);
			if(ENABLE_LOG){
				
				String logEntry = LOG_FORMAT
						.replaceAll("(?i)${HEAD}", target)
						.replaceAll("(?i)${SENDER}", sender.getName())
						.replaceAll("(?i)${TIMESTAMP}", ""+System.currentTimeMillis());
				pl.writeToLogFile(logEntry);
			}
		}
		return true;
	}
}