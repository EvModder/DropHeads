package net.evmodder.EvLib2;

import java.util.Vector;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Nameable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Panda.Gene;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class EvUtils{
	public static Vector<String> installedEvPlugins(){
		Vector<String> evPlugins = new Vector<String>();
		for(Plugin pl : Bukkit.getServer().getPluginManager().getPlugins()){
			try{
				@SuppressWarnings("unused")
				String ver = pl.getClass().getField("EvLib_ver").get(null).toString();
				evPlugins.add(pl.getName());
				//TODO: potentially return list of different EvLib versions being used
			}
			catch(IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e){}
		}
		return evPlugins;
	}

	public static String capitalizeAndSpacify(String str, char toSpace){
		StringBuilder builder = new StringBuilder("");
		boolean lower = false;
		for(char ch : str.toCharArray()){
			if(ch == toSpace){builder.append(' '); lower=false;}
			else if(lower) builder.append(Character.toLowerCase(ch));
			else{builder.append(Character.toUpperCase(ch)); lower = true;}
		}
		return builder.toString();
	}

	public static Gene getPandaTrait(Panda panda){
		if(panda.getMainGene() == panda.getHiddenGene()) return panda.getMainGene();
		switch(panda.getMainGene()){
			case BROWN:
			case WEAK:
				return Gene.NORMAL;
			default:
				return panda.getMainGene();
		}
	}

	public static boolean isHead(Material type){
		switch(type){
			case CREEPER_HEAD:
			case CREEPER_WALL_HEAD:
			case DRAGON_HEAD:
			case DRAGON_WALL_HEAD:
			case PLAYER_HEAD:
			case PLAYER_WALL_HEAD:
			case ZOMBIE_HEAD:
			case ZOMBIE_WALL_HEAD:
			case SKELETON_SKULL:
			case SKELETON_WALL_SKULL:
			case WITHER_SKELETON_SKULL:
			case WITHER_SKELETON_WALL_SKULL:
				return true;
			default:
				return false;
		}
	}
	public static boolean isPlayerHead(Material type){
		return type == Material.PLAYER_HEAD || type == Material.PLAYER_WALL_HEAD;
	}

	public static boolean hasGrummName(Nameable e){
		return e.getCustomName() != null && !(e instanceof Player) &&
				(e.getCustomName().equals("Dinnerbone") || e.getCustomName().equals("Grumm"));
	}

	public static EntityType getEntityByName(String name){
		//TODO: improve this algorithm / test for errors
		if(name.toUpperCase().startsWith("MHF_")) name = normalizedNameFromMHFName(name);
		name = name.toUpperCase().replace(' ', '_');

		try{EntityType type = EntityType.valueOf(name.toUpperCase()); return type;}
		catch(IllegalArgumentException ex){}
		name = name.replace("_", "");
		for(EntityType t : EntityType.values()) if(t.name().replace("_", "").equals(name)) return t;
		if(name.equals("ZOMBIEPIGMAN")) return EntityType.PIG_ZOMBIE;
		else if(name.equals("MOOSHROOM")) return EntityType.MUSHROOM_COW;
		return EntityType.UNKNOWN;
	}
	public static String getMHFHeadName(String eType){
		switch(eType){
		case "MAGMA_CUBE":
			return "MHF_LavaSlime";
		case "IRON_GOLEM":
			return "MHF_Golem";
		case "MOOSHROOM":
			return "MHF_MushroomCow";
		case "WITHER_SKELETON":
			return "MHF_Wither";
		default:
			StringBuilder builder = new StringBuilder("MHF_");
			boolean lower = false;
			for(char ch : eType.toCharArray()){
				if(ch == '_') lower = false;
				else if(lower) builder.append(Character.toLowerCase(ch));
				else{builder.append(Character.toUpperCase(ch)); lower = true;}
			}
			return builder.toString();
		}
	}
	public static String getNormalizedName(String eType){
		//TODO: improve this algorithm / test for errors
		switch(eType){
		case "PIG_ZOMBIE":
			return "Zombie Pigman";
		case "MUSHROOM_COW":
			return "Mooshroom";
		case "TROPICAL_FISH"://TODO: 22 varieties, e.g. Clownfish
		default:
			return capitalizeAndSpacify(eType, '_');
		}
	}
	public static String normalizedNameFromMHFName(String mhfName){
		mhfName = mhfName.substring(4);
		String mhfCompact = mhfName.replace("_", "").replace(" ", "").toLowerCase();
		if(mhfCompact.equals("lavaslime")) return "Magma Cube";
		else if(mhfCompact.equals("golem")) return "Iron Golem";
		else if(mhfCompact.equals("pigzombie")) return "Zombie Pigman";
		else if(mhfCompact.equals("mushroomcow")) return "Mooshroom";
		else{
			char[] chars = mhfName.toCharArray();
			StringBuilder name = new StringBuilder("").append(chars[0]);
			for(int i=1; i<chars.length; ++i){
				if(Character.isUpperCase(chars[i]) && chars[i-1] != ' ') name.append(' ');
				name.append(chars[i]);
			}
			return name.toString();
		}
	}
}