package net.evmodder.DropHeads;

import org.bukkit.DyeColor;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Ocelot;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TropicalFish;
import net.evmodder.EvLib.extras.EntityUtils;
import net.evmodder.EvLib.extras.EntityUtils.CCP;
import net.evmodder.EvLib.extras.ReflectionUtils;
import net.evmodder.EvLib.extras.ReflectionUtils.RefClass;
import net.evmodder.EvLib.extras.ReflectionUtils.RefMethod;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.ZombieVillager;

public class TextureKeyLookup{
	@SuppressWarnings("deprecation")
	static String getTropicalFishKey(CCP ccp){
		if(EntityUtils.getCommonTropicalFishId(ccp) != null){
			return EntityUtils.getTropicalFishEnglishName(ccp).toUpperCase().replace(' ', '_');
		}
		else{
			return new StringBuilder(ccp.bodyColor.name()).append('|').append(ccp.patternColor.name())
					.append('|').append(ccp.pattern.name()).toString();
		}
	}

	static RefMethod mVillagerType, mZombieVillagerType;
	static RefMethod mCatGetType, mCatGetCollarColor;
	static RefMethod mFoxGetType, mFoxIsSleeping;
	static RefMethod mMushroomCowGetVariant;
	static RefMethod mPandaGetMainGene, mPandaGetHiddenGene;
	static RefMethod mTraderLlamaGetColor;
	static RefMethod mVexIsCharging;
	static RefMethod mStriderIsShivering, mStriderHasSaddle;
	static RefMethod mShulkerGetPeek;
	static RefMethod mGetHandle, mGetDataWatcher, mGet_FromDataWatcher;
	static java.lang.reflect.Field ghastIsAttackingField;

	@SuppressWarnings("rawtypes")
	public static String getTextureKey(Entity entity){
		switch(entity.getType().name()){
			case "CREEPER":
				if(((Creeper)entity).isPowered()) return "CREEPER|CHARGED";
				else return "CREEPER";
			case "WOLF":
				if(((Wolf)entity).isTamed()) return "WOLF|"+((Wolf)entity).getCollarColor().name()+"_COLLARED";
				if(((Wolf)entity).isAngry()) return "WOLF|ANGRY";
				return "WOLF";
			case "HORSE":
				//TODO: isSaddled
				return "HORSE|"+((Horse)entity).getColor().name();
			case "DONKEY": case "MULE": case "PIG":
				//TODO: isSaddled
				return entity.getType().name();
			case "LLAMA":
				//TODO: getCarpetColor
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
				String shulkerAndColorKey = color == null ? "SHULKER" : "SHULKER|"+color.name();
				if(ReflectionUtils.getServerVersionString().compareTo("v1_16_R3") < 0) return shulkerAndColorKey;
				if(mShulkerGetPeek == null){
					try{mShulkerGetPeek = ReflectionUtils.getRefClass("org.bukkit.entity.Shulker").getMethod("getPeek");}
					catch(RuntimeException ex){return shulkerAndColorKey;}
				}
				float peek = (float)mShulkerGetPeek.of(entity).call();
				String peekState = peek == 0 ? "|CLOSED" : peek == 1 ? "" : "|PEEKING";
				return shulkerAndColorKey + peekState;
			case "TROPICAL_FISH":
				return "TROPICAL_FISH|"+getTropicalFishKey(EntityUtils.getCCP((TropicalFish)entity));
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
				String catType = ((Ocelot)entity).getCatType().name();
				return catType.equals("WILD_OCELOT") ? "OCELOT" : "OCELOT|"+catType;
			case "CAT":
				if(mCatGetType == null) mCatGetType = ReflectionUtils.getRefClass("org.bukkit.entity.Cat").getMethod("getCatType");
				catType = ((Enum)mCatGetType.of(entity).call()).name();
				if(catType.equals("RED")) catType = "GINGER";
				if(catType.equals("BLACK")) catType = "TUXEDO";
				if(catType.equals("ALL_BLACK")) catType = "BLACK";
				if(((Tameable)entity).isTamed()){
					if(mCatGetCollarColor == null) mCatGetCollarColor = ReflectionUtils.getRefClass("org.bukkit.entity.Cat").getMethod("getCollarColor");
					return "CAT|"+catType+"|"+((DyeColor)mCatGetCollarColor.of(entity).call()).name()+"_COLLARED";
				}
				return "CAT|"+catType;
//			case "IRON_GOLEM":
//				// DONE:TEST (in HeadAPI): Drop varying-crackiness iron golem heads
//				return "IRON_GOLEM";
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
				return "PANDA|"+EntityUtils.getPandaTrait(mainGene, hiddenGene);
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
				if(mStriderIsShivering == null){
					RefClass classStrider = ReflectionUtils.getRefClass("org.bukkit.entity.Strider");
					mStriderIsShivering = classStrider.getMethod("isShivering");
					mStriderHasSaddle = classStrider.getMethod("hasSaddle");
				}
				boolean isShivering = mStriderIsShivering.of(entity).call().equals(true);
				boolean hasSaddle = mStriderHasSaddle.of(entity).call().equals(true);
				return "STRIDER|"+(isShivering ? "COLD" : "WARM")+(hasSaddle ? "|SADDLED" : "");
			case "PIG_ZOMBIE":
				if(((Zombie)entity).isBaby()) return "PIG_ZOMBIE|BABY";
				else return "PIG_ZOMBIE";
			case "PLAYER":
				/* hmm */
			default:
				return entity.getType().name();
		}
	}
}