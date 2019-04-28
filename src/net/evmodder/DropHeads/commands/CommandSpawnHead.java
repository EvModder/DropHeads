package net.evmodder.DropHeads.commands;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.HeadUtils;
import net.evmodder.EvLib.CommandBase;
import net.evmodder.EvLib.EvUtils;
import net.evmodder.EvLib.Extras;

public class CommandSpawnHead extends CommandBase{
	final private DropHeads pl;

	public CommandSpawnHead(DropHeads plugin) {
		super(plugin);
		pl = plugin;
	}

	String formatMaterialName(Material type){
		StringBuilder builder = new StringBuilder("");
		boolean lower = false;
		for(char c : type.toString().toCharArray()){
			if(c == '_'){builder.append(' '); lower = false;}
			else if(lower){builder.append(Character.toLowerCase(c));}
			else{builder.append(c); lower = true;}
		}
		return builder.toString();
	}

	@SuppressWarnings("deprecation") @Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
		if(sender instanceof Player == false){
			sender.sendMessage(ChatColor.RED+"This command can only be run by in-game players!");
			return true;
		}

		String headStr = args.length == 0 ? sender.getName() : String.join("_", args).replace(':', '|');
		String target, textureKey;
		int i = headStr.indexOf('|');
		if(i != -1){
			target = headStr.substring(0, i);
			textureKey = headStr.substring(i+1).toUpperCase();
		}
		else{
			target = headStr;
			textureKey = null;
		}
		ItemStack head = null;

		EntityType eType = EvUtils.getEntityByName(target.toUpperCase());
		if(eType != null){
			if(textureKey != null){
				if(pl.getAPI().textureExists(eType.name()+"|"+textureKey))
					sender.sendMessage(ChatColor.GRAY+"Getting entity head with data value: "+textureKey);
				else
					sender.sendMessage(ChatColor.RED+"Unknown data value for "+eType+": "+ChatColor.YELLOW+textureKey);
			}
			head = pl.getAPI().getHead(eType, textureKey);
		}
		else{
			OfflinePlayer p = pl.getServer().getOfflinePlayer(target);
			if(p.hasPlayedBefore() || Extras.checkExists(p.getName())){
				//sender.sendMessage("Getting head for player: "+target);
				head = HeadUtils.getPlayerHead(p);
			}
			else if(target.startsWith("MHF_") && HeadUtils.MHF_Lookup.containsKey(target.toUpperCase())){
				head = new ItemStack(Material.PLAYER_HEAD);
				SkullMeta meta = (SkullMeta) head.getItemMeta();
				meta.setOwner(target);
				meta.setDisplayName(ChatColor.YELLOW+HeadUtils.MHF_Lookup.get(target.toUpperCase()));
				head.setItemMeta(meta);
			}
			else if(target.length() > 100){
				head = HeadUtils.makeSkull(target);
			}
		}
		if(head != null){
			String headName = head.hasItemMeta() && head.getItemMeta().hasDisplayName()
					? head.getItemMeta().getDisplayName() : formatMaterialName(head.getType());
			((Player)sender).getInventory().addItem(head);
			sender.sendMessage(ChatColor.GREEN+"Spawned Head: " + ChatColor.GOLD + headName);
		}
		else sender.sendMessage(ChatColor.RED+"Head \""+target+"\" not found");
		return true;
	}
}