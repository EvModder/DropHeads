package net.evmodder.DropHeads;

import java.util.HashMap;
import org.bukkit.DyeColor;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Ocelot;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.TropicalFish;
import org.bukkit.entity.TropicalFish.Pattern;
import net.evmodder.EvLib.extras.ReflectionUtils;
import net.evmodder.EvLib.extras.ReflectionUtils.RefClass;
import net.evmodder.EvLib.extras.ReflectionUtils.RefMethod;
import net.evmodder.EvLib.extras.TextUtils;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.ZombieVillager;

public class TextureKeyLookup{
	static class CCP{
		DyeColor bodyColor, patternColor;
		Pattern pattern;
		CCP(DyeColor color, DyeColor pColor, Pattern p){bodyColor = color; patternColor = pColor; pattern = p;}
		@Override public boolean equals(Object o){
			if(o == this) return true;
			if(o == null || o.getClass() != getClass()) return false;
			CCP ccp = (CCP)o;
			return ccp.bodyColor == bodyColor && ccp.patternColor == patternColor && ccp.pattern == pattern;
		}
		@Override public int hashCode(){
			return bodyColor.hashCode() + 16*(patternColor.hashCode() + 16*pattern.hashCode());
		}
	}
	public static final HashMap<CCP, String> tropicalFishNames;//Names for the 22 common tropical fish
	public static final HashMap<DyeColor, String> fishColorNames;//Names assigned by color
	static{
		tropicalFishNames = new HashMap<CCP, String>();
		tropicalFishNames.put(new CCP(DyeColor.ORANGE, DyeColor.GRAY, Pattern.STRIPEY), "Anemone");
		tropicalFishNames.put(new CCP(DyeColor.GRAY, DyeColor.GRAY, Pattern.FLOPPER), "Black Tang");
		tropicalFishNames.put(new CCP(DyeColor.GRAY, DyeColor.BLUE, Pattern.FLOPPER), "Blue Dory");
		tropicalFishNames.put(new CCP(DyeColor.WHITE, DyeColor.GRAY, Pattern.BRINELY), "Butterflyfish");
		tropicalFishNames.put(new CCP(DyeColor.BLUE, DyeColor.GRAY, Pattern.SUNSTREAK), "Cichlid");
		tropicalFishNames.put(new CCP(DyeColor.ORANGE, DyeColor.WHITE, Pattern.KOB), "Clownfish");
		tropicalFishNames.put(new CCP(DyeColor.PINK, DyeColor.LIGHT_BLUE, Pattern.SPOTTY), "Cotton Candy Betta");
		tropicalFishNames.put(new CCP(DyeColor.PURPLE, DyeColor.YELLOW, Pattern.BLOCKFISH), "Dottyback");
		tropicalFishNames.put(new CCP(DyeColor.WHITE, DyeColor.RED, Pattern.CLAYFISH), "Red Emperor");
		tropicalFishNames.put(new CCP(DyeColor.WHITE, DyeColor.YELLOW, Pattern.SPOTTY), "Goatfish");
		tropicalFishNames.put(new CCP(DyeColor.WHITE, DyeColor.GRAY, Pattern.GLITTER), "Moorish Idol");
		tropicalFishNames.put(new CCP(DyeColor.WHITE, DyeColor.ORANGE, Pattern.CLAYFISH), "Ornate Butterflyfish");
		tropicalFishNames.put(new CCP(DyeColor.CYAN, DyeColor.PINK, Pattern.DASHER), "Parrotfish");
		tropicalFishNames.put(new CCP(DyeColor.LIME, DyeColor.LIGHT_BLUE, Pattern.BRINELY), "Queen Angelfish");
		tropicalFishNames.put(new CCP(DyeColor.RED, DyeColor.WHITE, Pattern.BETTY), "Red Cichlid");
		tropicalFishNames.put(new CCP(DyeColor.GRAY, DyeColor.RED, Pattern.SNOOPER), "Red Lipped Blenny");
		tropicalFishNames.put(new CCP(DyeColor.RED, DyeColor.WHITE, Pattern.BLOCKFISH), "Red Snapper");
		tropicalFishNames.put(new CCP(DyeColor.WHITE, DyeColor.YELLOW, Pattern.FLOPPER), "Threadfin");
		tropicalFishNames.put(new CCP(DyeColor.RED, DyeColor.WHITE, Pattern.KOB), "Tomato Clownfish");
		tropicalFishNames.put(new CCP(DyeColor.GRAY, DyeColor.WHITE, Pattern.SUNSTREAK), "Triggerfish");
		tropicalFishNames.put(new CCP(DyeColor.CYAN, DyeColor.YELLOW, Pattern.DASHER), "Yellowtail Parrotfish");
		tropicalFishNames.put(new CCP(DyeColor.YELLOW, DyeColor.YELLOW, Pattern.STRIPEY), "Yellow Tang");
	}
	static{
		fishColorNames = new HashMap<DyeColor, String>();//TODO: use Color instead of DyeColor
		fishColorNames.put(DyeColor.BLACK, "Black");
		fishColorNames.put(DyeColor.BLUE, "Blue");
		fishColorNames.put(DyeColor.BROWN, "Brown");
		fishColorNames.put(DyeColor.CYAN, "Teal");
		fishColorNames.put(DyeColor.GRAY, "Gray");
		fishColorNames.put(DyeColor.GREEN, "Green");
		fishColorNames.put(DyeColor.LIGHT_BLUE, "Sky");
		fishColorNames.put(DyeColor.LIGHT_GRAY, "Silver");
		fishColorNames.put(DyeColor.LIME, "Lime");
		fishColorNames.put(DyeColor.MAGENTA, "Magenta");
		fishColorNames.put(DyeColor.ORANGE, "Orange");
		fishColorNames.put(DyeColor.PINK, "Rose");
		fishColorNames.put(DyeColor.PURPLE, "Plum");
		fishColorNames.put(DyeColor.RED, "Red");
		fishColorNames.put(DyeColor.WHITE, "White");
		fishColorNames.put(DyeColor.YELLOW, "Yellow");
	}

	//TODO: move to TextUtils or EntityUtils?
	static String getTropicalFishName(CCP ccp){
		String name = tropicalFishNames.get(ccp);
		if(name == null){
			StringBuilder builder = new StringBuilder(fishColorNames.get(ccp.bodyColor));
			if(ccp.bodyColor != ccp.patternColor) builder.append('-').append(fishColorNames.get(ccp.patternColor));
			builder.append(' ').append(TextUtils.capitalizeAndSpacify(ccp.pattern.name(), '_'));
			name = builder.toString();
			tropicalFishNames.put(ccp, name); // Cache result. Size can reach up to 2700 varieties (15*15*12)
		}
		return name;
	}
	static String getTropicalFishName(TropicalFish fish){
		return getTropicalFishName(new CCP(fish.getBodyColor(), fish.getPatternColor(), fish.getPattern()));
	}

	//TODO: move to EntityUtils in EvLib
	/*public static Gene getPandaTrait(Panda panda){
		if(panda.getMainGene() == panda.getHiddenGene()) return panda.getMainGene();
		switch(panda.getMainGene()){
			case BROWN:
			case WEAK:
				return Gene.NORMAL;
			default:
				return panda.getMainGene();
		}
	}*/
	public static String getPandaTrait(String mainGene, String hiddenGene){
		if(mainGene.equals(hiddenGene)) return mainGene;
		switch(mainGene){
			case "BROWN":
			case "WEAK":
				return "NORMAL";
			default:
				return mainGene;
		}
	}

	static String getNameFromKey(EntityType entity, String textureKey){
		if(textureKey.equals("PLAYER|GRUMM")) return "Grumm";
		String[] dataFlags = textureKey.split("\\|");
		String entityName = entity == null ? dataFlags[0] : entity.name();
		switch(entityName){
			case "TROPICAL_FISH":
				if(dataFlags.length == 2) return TextUtils.capitalizeAndSpacify(dataFlags[1], '_');// 22 common
				try{
					DyeColor bodyColor = DyeColor.valueOf(dataFlags[1]);
					DyeColor patternColor = dataFlags.length == 3 ? bodyColor : DyeColor.valueOf(dataFlags[2]);
					Pattern pattern = Pattern.valueOf(dataFlags[dataFlags.length == 3 ? 2 : 3]);
					return getTropicalFishName(new CCP(bodyColor, patternColor, pattern));// C-C-P
				}
				catch(IllegalArgumentException e){}
				break;
			case "VILLAGER": case "ZOMBIE_VILLAGER":
				if(textureKey.contains("|NONE")){
					textureKey = textureKey.replace("|NONE", "");
					dataFlags = textureKey.split("\\|");
				}
		}
		StringBuilder builder = new StringBuilder("");
		for(int i=dataFlags.length-1; i>0; --i){
			String dataStr = TextUtils.capitalizeAndSpacify(dataFlags[i], '_');
			builder.append(dataStr).append(' ');
		}
		builder.append(TextUtils.getNormalizedEntityName(dataFlags[0]));
		return builder.toString();
	}


	static RefMethod mVillagerType, mZombieVillagerType;
	static RefMethod mCatGetType;
	static RefMethod mFoxGetType, mFoxIsSleeping;
	static RefMethod mMushroomCowGetVariant;
	static RefMethod mPandaGetMainGene, mPandaGetHiddenGene;
	static RefMethod mTraderLlamaGetColor;
	static RefMethod mVexIsCharging;
	static RefMethod mGetHandle, mGetDataWatcher, mGet_FromDataWatcher;
	static java.lang.reflect.Field ghastIsAttackingField;

	@SuppressWarnings("rawtypes")
	static String getTextureKey(Entity entity){
		switch(entity.getType().name()){
			case "CREEPER":
				if(((Creeper)entity).isPowered()) return "CREEPER|CHARGED";
				else return "CREEPER";
			case "WOLF":
				if(((Wolf)entity).isAngry()) return "WOLF|ANGRY";
				else return "WOLF";
			case "HORSE":
				return "HORSE|"+((Horse)entity).getColor().name();
			case "LLAMA":
				return "LLAMA|"+((Llama)entity).getColor().name();
			case "PARROT":
				return "PARROT|"+((Parrot)entity).getVariant().name();
			case "RABBIT":
				return "RABBIT|"+((Rabbit)entity).getRabbitType().name();
			case "SHEEP":
				if(entity.getCustomName() != null && entity.getCustomName().equals("jeb_")) return "SHEEP|JEB";
				else return "SHEEP|"+((Sheep)entity).getColor().name();
			case "SHULKER":
				DyeColor color = ((Shulker)entity).getColor();
				return color == null ? "SHULKER" : "SHULKER|"+color.name();
			case "TROPICAL_FISH":
				TropicalFish f = (TropicalFish)entity;
				return "TROPICAL_FISH|"+f.getBodyColor()+"|"+f.getPatternColor()+"|"+f.getPattern();
				/*CCP fishData = new CCP(f.getBodyColor(), f.getPatternColor(), f.getPattern());
				String name = HeadUtils.tropicalFishNames.get(fishData);
				if(name == null) name = EvUtils.getNormalizedName(entity.getType());
				String code = "wow i need to figure this out huh";
				return HeadUtils.makeSkull(code, name);*/
			case "VEX":
				if(ReflectionUtils.getServerVersionString().compareTo("v1_13_R3") < 0) return "VEX";
				if(mVexIsCharging == null) mVexIsCharging = ReflectionUtils.getRefClass("org.bukkit.entity.Vex").getMethod("isCharging");
				if(mVexIsCharging.of(entity).call().equals(true)) return "VEX|CHARGING";
				else return "VEX";
			case "VILLAGER":
				String villagerProfession = ((Villager)entity).getProfession().name();
				if(mVillagerType == null){
					try{mVillagerType = ReflectionUtils.getRefClass("org.bukkit.entity.Villager").getMethod("getVillagerType");}
					catch(RuntimeException ex){return "VILLAGER|"+villagerProfession;}
				}
				return "VILLAGER|"+villagerProfession+"|"+((Enum)mVillagerType.of(entity).call()).name();
			case "ZOMBIE_VILLAGER":
				String zombieVillagerProfession = ((ZombieVillager)entity).getVillagerProfession().name();
				if(mZombieVillagerType == null){
					try{mZombieVillagerType = ReflectionUtils.getRefClass("org.bukkit.entity.ZombieVillager").getMethod("getVillagerType");}
					catch(RuntimeException ex){return "ZOMBIE_VILLAGER|"+zombieVillagerProfession;}
				}
				return "ZOMBIE_VILLAGER|"+zombieVillagerProfession+"|"+((Enum)mZombieVillagerType.of(entity).call()).name();
			case "OCELOT":
				return "OCELOT|"+((Ocelot)entity).getCatType().name();
			case "CAT":
				if(mCatGetType == null) mCatGetType = ReflectionUtils.getRefClass("org.bukkit.entity.Cat").getMethod("getCatType");
				String catType = ((Enum)mCatGetType.of(entity).call()).name();
				switch(catType){
					case "ALL_BLACK": return "CAT|BLACK";
					case "BLACK": return "CAT|TUXEDO";
					default: return "CAT|"+catType;
				}
			case "MUSHROOM_COW":
				if(ReflectionUtils.getServerVersionString().compareTo("v1_14_R0") < 0) return "MUSHROOM_COW";
				if(mMushroomCowGetVariant == null) mMushroomCowGetVariant =
					ReflectionUtils.getRefClass("org.bukkit.entity.MushroomCow").getMethod("getVariant");
				return "MUSHROOM_COW|"+((Enum)mMushroomCowGetVariant.of(entity).call()).name();
			case "FOX":
				if(mFoxGetType == null){
					RefClass classFox = ReflectionUtils.getRefClass("org.bukkit.entity.Fox");
					mFoxGetType = classFox.getMethod("getFoxType");
					mFoxIsSleeping = classFox.getMethod("isSleeping");
				}
				String foxType = ((Enum)mFoxGetType.of(entity).call()).name();
				if(mFoxIsSleeping.of(entity).call().equals(true)) return "FOX|"+foxType+"|SLEEPING";
				else return "FOX|"+foxType;
			case "PANDA":
				if(mPandaGetMainGene == null){
					RefClass classPanda = ReflectionUtils.getRefClass("org.bukkit.entity.Panda");
					mPandaGetMainGene = classPanda.getMethod("getMainGene");
					mPandaGetHiddenGene = classPanda.getMethod("getHiddenGene");
				}
				String mainGene = ((Enum)mPandaGetMainGene.of(entity).call()).name();
				String hiddenGene = ((Enum)mPandaGetHiddenGene.of(entity).call()).name();
				return "PANDA|"+TextureKeyLookup.getPandaTrait(mainGene, hiddenGene);
			case "TRADER_LLAMA":
				if(mTraderLlamaGetColor == null) mTraderLlamaGetColor =
						ReflectionUtils.getRefClass("org.bukkit.entity.TraderLlama").getMethod("getColor");
				return "TRADER_LLAMA|"+((Enum)mTraderLlamaGetColor.of(entity).call()).name();
			case "GHAST":
				//https://wiki.vg/Entity_metadata#Ghast => isAttacking=15="" (1.13 - 1.15)
				boolean isScreaming = false;
				if(ghastIsAttackingField == null){
					mGetHandle = ReflectionUtils.getRefClass("{cb}.entity.CraftEntity").getMethod("getHandle");
					RefClass classEntityGhast = ReflectionUtils.getRefClass("{nms}.EntityGhast");
					RefClass classDataWatcher = ReflectionUtils.getRefClass("{nms}.DataWatcher");
					RefClass classDataWatcherObject = ReflectionUtils.getRefClass("{nms}.DataWatcherObject");
					mGetDataWatcher = classEntityGhast.getMethod("getDataWatcher");
					mGet_FromDataWatcher = classDataWatcher.getMethod("get", classDataWatcherObject);
					//TODO: monitor for change: BuildTools/work/decompile-ee3ecae0/net/minecraft/server/EntityGhast.java
					ghastIsAttackingField = classEntityGhast.getField("b").getRealField();
					ghastIsAttackingField.setAccessible(true);
				}
				try{
					Object datawatcherobject = ghastIsAttackingField.get(null);
					Object entityGhast = mGetHandle.of(entity).call();
					Object dataWatcher = mGetDataWatcher.of(entityGhast).call();
					isScreaming = mGet_FromDataWatcher.of(dataWatcher).call(datawatcherobject).equals(true);
					org.bukkit.Bukkit.getLogger().info("Ghast isAttacking: "+isScreaming);
				}
				catch(IllegalArgumentException | IllegalAccessException e){}
				if(isScreaming) return "GHAST|SCREAMING";//TODO: Add this to the Bukkit API
				else return "GHAST";
			case "STRIDER":
				//TODO: Cold vs. Hot
			case "PLAYER":
				/* hmm */
			default:
				return entity.getType().name();
		}
	}
}