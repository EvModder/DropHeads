package Evil_Code_DropHeads;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import EvLib2.CommandBase2;
import EvLib2.Extras;

public class CommandSpawnHead extends CommandBase2{
	final private DropHeads pl;

	public CommandSpawnHead(DropHeads plugin) {
		super(plugin);
		pl = plugin;
	}

	@SuppressWarnings("deprecation") @Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
		if(sender instanceof Player == false){
			sender.sendMessage(ChatColor.RED+"This command can only be run by in-game players!");
			return true;
		}

		String target, data, str = args.length == 0 ? sender.getName(): Extras.getPlayerName(args);
		int i = str.indexOf(':');
		if(i != -1){
			target = str.substring(0, i);
			data = str.substring(i+1).toUpperCase();
		}
		else{
			target = str;
			data = null;
		}
		ItemStack head = null;
		OfflinePlayer p = null;

		if(target.toUpperCase().startsWith("MHF_")){
			head = new ItemStack(Material.PLAYER_HEAD);
			SkullMeta meta = (SkullMeta) head.getItemMeta();
			meta.setOwner(target);
			EntityType type = Utils.getEntityByName(target);
			meta.setDisplayName(ChatColor.WHITE+(type != null ? Utils.getNormalizedName(type)+" Head" : target));
			head.setItemMeta(meta);
		}
		else if(Utils.getEntityByName(target) != null){
			sender.sendMessage("Getting entity head with data value: "+data);
			head = Utils.getHead(Utils.getEntityByName(target), data);
		}
		else if((p = pl.getServer().getOfflinePlayer(target)) != null
				&& (p.hasPlayedBefore() || Extras.checkExists(p.getName()))){
			head = Utils.getPlayerHead(p.getUniqueId(), p.getName());
		}
		else if(target.length() > 50){
			head = Utils.makeTextureSkull(target);
		}
		if(head != null){
			((Player)sender).getInventory().addItem(head);
			sender.sendMessage(ChatColor.GREEN+"Spawned Head: " + ChatColor.GOLD +
					(head.hasItemMeta() && head.getItemMeta().hasDisplayName()
							? head.getItemMeta().getDisplayName()//.substring(2)//TODO: strip leading color?
							: head.getType().toString()));
		}
		else sender.sendMessage(ChatColor.RED+"Head \""+target+"\" not found");
		return true;
	}
}