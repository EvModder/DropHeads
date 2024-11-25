package net.evmodder.DropHeads.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.EvLib.EvCommand;
import net.evmodder.EvLib.extras.HeadUtils;

public class Commanddebug_all_heads extends EvCommand{
	final private DropHeads pl;
	final boolean SHOW_PLAIN_IF_HAS_CHILDREN = false;
	final boolean REMOVE_DUPLICATE_TEXTURES = true;
	enum OrderBy{ALPHABETICAL, NUM_SUBKEYS_ASC}
	final OrderBy orderBy = OrderBy.NUM_SUBKEYS_ASC;

	public Commanddebug_all_heads(DropHeads plugin){
		super(plugin);
		pl = plugin;
	}

	@SuppressWarnings("deprecation")
	private void setHead(Location loc, String textureKey, BlockFace facing){
		ItemStack skullItem = pl.getAPI().getHead((EntityType)null, textureKey);
		loc.getBlock().setType(facing == null ? Material.PLAYER_HEAD : Material.PLAYER_WALL_HEAD);
		Skull blockState = (Skull) loc.getBlock().getState();
		blockState.setType(facing == null ? Material.PLAYER_HEAD : Material.PLAYER_WALL_HEAD);
		if(facing != null) blockState.setRotation(facing);
		//blockState.setData(skull.getData());
		HeadUtils.setGameProfile(blockState, HeadUtils.getGameProfile((SkullMeta)skullItem.getItemMeta()));
		blockState.update();
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args){return null;}

	String getRootParentKey(String key){
		int i = key.indexOf('|');
		return i == -1 ? key : key.substring(0, i);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String args[]){
		if(sender instanceof Player == false){
			sender.sendMessage(ChatColor.RED+"This command can only be run by in-game players!");
			return true;
		}
		final String matchText;
		if(args.length > 0 && !args[0].toLowerCase().matches("true|false")){
			matchText = args[0]; args = Arrays.copyOfRange(args, 1, args.length);
		}
		else matchText = null;

		final boolean noGrumm = args.length == 0 || !args[args.length-1].toLowerCase().equals("true");
		final boolean noPreJappa = args.length < 2 || !args[args.length-2].toLowerCase().equals("true");
		final boolean noSubtypes = args.length >= 3 && args[args.length-3].toLowerCase().equals("true");
		sender.sendMessage("Skipping Grumm heads: "+noGrumm);
		sender.sendMessage("Skipping Pre-JAPPA heads: "+noPreJappa);
		sender.sendMessage("Skipping entity subtypes: "+noSubtypes);

		// Get & filter head keys
		Collection<String> textureKeys = new ArrayList<>();
		textureKeys.addAll(pl.getAPI().getTextures().keySet());
		sender.sendMessage("Total # texture keys: "+textureKeys.size());

		if(matchText != null) textureKeys.removeIf(key -> !key.contains(matchText));
		if(noSubtypes) textureKeys.removeIf(key -> key.contains("|"));
		if(noGrumm) textureKeys.removeIf(key -> key.endsWith("|GRUMM") && !key.startsWith("SHULKER|"));
		if(noPreJappa) textureKeys.removeIf(key -> key.contains("|PRE_JAPPA"));
		if(!SHOW_PLAIN_IF_HAS_CHILDREN){
			// Don't show "FOX" if we have "FOX|RED", or "SHEEP" if we have "SHEEP|WHITE" etc
			HashSet<String> hasSubKey = new HashSet<>();
			HashSet<String> hasMultipleSubKeys = new HashSet<>();
			hasMultipleSubKeys.add("CAT");
			for(String key : textureKeys){
				int i = key.lastIndexOf('|');
				if(i != -1){
					String badKey;
					if(key.endsWith("|GRUMM")){
						if((i = key.substring(0, i).lastIndexOf('|')) == -1) continue;
						badKey = key.substring(0, i) + "|GRUMM";
					}
					else badKey = key.substring(0, i);
					// Only remove if we have 2+ sub categories for a key
					if(!hasSubKey.add(badKey)){
						// Exceptions we want to keep:
						if(key.startsWith("SHULKER") || key.startsWith("BEE") || key.startsWith("WOLF") || key.startsWith("CAT")) continue;
						hasMultipleSubKeys.add(badKey);
					}
				}
			}
			textureKeys.removeAll(hasMultipleSubKeys);
		}
		if(REMOVE_DUPLICATE_TEXTURES){
			Map<String, String> textures = pl.getAPI().getTextures();
			HashMap<String, String> textureToKey = new HashMap<>();
			for(String key : textureKeys){
				String texture = textures.get(key);
				String oldKey = textureToKey.get(texture);
				if(oldKey == null || oldKey.length() < key.length()) textureToKey.put(texture, key);
			}
			textureKeys = textureToKey.values();
		}

		// Sort head keys
		HashMap<String, Integer> numSubKeys = new HashMap<>();
		pl.getAPI().getTextures().keySet().forEach(key -> {
			String rootKey = getRootParentKey(key);
			numSubKeys.put(rootKey, numSubKeys.getOrDefault(rootKey, 0) + 1);
		});
		textureKeys = textureKeys.stream()
			.sorted((s1, s2) -> {
				String root1 = getRootParentKey(s1), root2 = getRootParentKey(s2);
				switch(orderBy){
					case NUM_SUBKEYS_ASC:
						if(numSubKeys.get(root1) < numSubKeys.get(root2)) return -2;
						if(numSubKeys.get(root1) > numSubKeys.get(root2)) return +2;
					case ALPHABETICAL:
					default:
						if(root1.compareTo(root2) != 0) return root1.compareTo(root2);
						return s1.compareTo(s2);
				}
			})
			.collect(Collectors.toList());

		// Place heads
		final long numHeads = textureKeys.size();
		sender.sendMessage("Placing "+numHeads+" heads...");

		final Location loc = ((Player)sender).getLocation();
//		if(DISPLAY_3D){
//			int dimX, dimY, dimZ;
//			dimX = dimY = dimZ = (int)Math.floor(Math.cbrt(numHeads));
//			if(dimX*dimY*dimZ < numHeads) if((++dimX)*dimY*dimZ < numHeads) if(dimX*dimY*(++dimZ) < numHeads) ++dimY;
//			final int dX = dimX, dY = dimY, dZ = dimZ;
//	
//			for(int x=0; x<dX; ++x) for(int z=0; z<dZ; ++z) for(int y=0; y<dY; ++y){
//				if(!loc.clone().add(((dX/2)-x)*2, ((dY/2)-y)*2, ((dZ/2)-z)*2).getBlock().isEmpty()){
//					sender.sendMessage(ChatColor.RED+"Error: Not enough space around the player to place the heads!");
//					return true;
//				}
//			}
//			sender.sendMessage("Dimensions: "+dX+","+dY+","+dZ);
//			Iterator<String> it = textureKeys.iterator();
//			//pl.getServer().getScheduler()(pl, new Runnable(){public void run(){
//				for(int z=dZ; z>0 && it.hasNext(); --z)
//				for(int y=dY; y>0 && it.hasNext(); --y)
//				for(int x=dX; x>0 && it.hasNext(); --x){
//					Location hLoc = loc.clone().add(((dX/2)-x)*2, ((dY/2)-y)*2, ((dZ/2)-z)*2);
//					String key = it.next();
//					/*if(noGrumm){
//						while(key.endsWith("|GRUMM") && it.hasNext()) key = it.next();
//						if(key.endsWith("|GRUMM")) break;
//					}*/
//					setHead(hLoc, key, null);
//				}
//			//}});
//		}
//		else{
			int dimX, dimY;
			if(args.length > 0 && args[0].matches("^[1-9][0-9]*$")){
				dimX = Integer.parseInt(args[0]);
				dimY = (int)Math.ceil(numHeads/(double)dimX);
			}
			else{
				dimX = dimY = (int)Math.sqrt(numHeads);
				if(dimX*dimY < numHeads) if((++dimX)*dimY < numHeads) ++dimY;
			}
			final int dX = dimX, dY = dimY;
	
			for(int x=0; x<dX; ++x) for(int y=0; y<dY; ++y){
				if(!loc.clone().add(x, y, 0).getBlock().isEmpty()){
					sender.sendMessage(ChatColor.RED+"Error: Not enough space around the player to place the heads!");
					return true;
				}
			}
			sender.sendMessage("Dimensions: "+dX+"x"+dY);
			Iterator<String> it = textureKeys.iterator();
			//pl.getServer().getScheduler()(pl, new Runnable(){public void run(){
				for(int y=dY; y>0 && it.hasNext(); --y) for(int x=dX; x>0 && it.hasNext(); --x){
					String key = it.next();
					/*if(noGrumm){
						while(key.endsWith("|GRUMM") && it.hasNext()) key = it.next();
						if(key.endsWith("|GRUMM")) break;
					}*/
					setHead(loc.clone().add(x, y, 0), key, BlockFace.NORTH);
				}
			//}});
//		}
		sender.sendMessage("Finished placing "+numHeads+" heads");

		return true;
	}
}