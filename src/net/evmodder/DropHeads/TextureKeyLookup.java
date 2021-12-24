package net.evmodder.DropHeads;

import org.bukkit.DyeColor;
import org.bukkit.block.BlockFace;
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
			System.out.println("name: "+EntityUtils.getTropicalFishEnglishName(ccp).toUpperCase().replace(' ', '_'));
			return "TROPICAL_FISH|"+EntityUtils.getTropicalFishEnglishName(ccp).toUpperCase().replace(' ', '_');
		}
		else{
			System.out.println("fallback name: "+new StringBuilder("TROPICAL_FISH|")
					.append(ccp.bodyColor.name()).append('|').append(ccp.patternColor.name())
					.append('|').append(ccp.pattern.name()).toString());
			return new StringBuilder("TROPICAL_FISH|")
					.append(ccp.bodyColor.name()).append('|').append(ccp.patternColor.name())
					.append('|').append(ccp.pattern.name()).toString();
		}
	}
	static BlockFace turnLeft(BlockFace face){
		switch(face){
			case NORTH: return BlockFace.WEST;
			case WEST: return BlockFace.SOUTH;
			case SOUTH: return BlockFace.EAST;
			case EAST: return BlockFace.NORTH;
			default: return null;
		}
	}
	static String getShulkerKey(Shulker shulker){
		DyeColor color = shulker.getColor();
		String shulkerAndColorKey = color == null ? "SHULKER" : "SHULKER|"+color.name();
		//org.bukkit.Bukkit.getLogger().info("shulker facing: "+shulker.getFacing());

		if(ReflectionUtils.getServerVersionString().compareTo("v1_16_R3") < 0) return shulkerAndColorKey;
		if(mShulkerGetAttachedFace == null){
			try{
				RefClass classShulker = ReflectionUtils.getRefClass("org.bukkit.entity.Shulker");
				mShulkerGetPeek = classShulker.getMethod("getPeek");
				mShulkerGetAttachedFace = classShulker.getMethod("getAttachedFace");
			}
			catch(RuntimeException ex){return shulkerAndColorKey;}
		}
		float peek = (float)mShulkerGetPeek.of(shulker).call();
		String peekState = peek == 0 ? "|CLOSED" : peek == 1 ? "" : "|PEEKING";
		BlockFace attachedFace = (BlockFace)mShulkerGetAttachedFace.of(shulker).call();
		String rotation;
		switch(attachedFace){
			case UP:
				rotation = "|GRUMM"; break;
			case NORTH: case SOUTH: case EAST: case WEST:
				if(peekState.equals("|CLOSED")) rotation = "|SIDEWAYS";
				else if(shulker.getFacing() == BlockFace.UP) rotation = "|SIDE_UP";
				else if(shulker.getFacing() == BlockFace.DOWN) rotation = "|SIDE_DOWN";
				else if(shulker.getFacing() == turnLeft(attachedFace)) rotation = "|SIDE_LEFT";
				else/*if(shulker.getFacing() == turnRight(attachedFace))*/rotation = "|SIDE_RIGHT";
				break;
			default: case DOWN: rotation = "";
		}
		return shulkerAndColorKey + peekState + rotation;
	}

	static RefMethod mGetVillagerType, mGetZombieVillagerType;
	static RefMethod mBeeHasNectar, mBeeGetAnger, mBeeHasStung;
	static RefMethod mCatGetType, mCatGetCollarColor;
	static RefMethod mFoxGetType, mFoxIsSleeping;
	static RefMethod mMushroomCowGetVariant;
	static RefMethod mPandaGetMainGene, mPandaGetHiddenGene;
	static RefMethod mTraderLlamaGetColor;
	static RefMethod mAxolotlGetVariant;
	static RefMethod mVexIsCharging;
	static RefMethod mGoatIsScreaming;
	static RefMethod mStriderIsShivering, mStriderHasSaddle;
	static RefMethod mShulkerGetPeek, mShulkerGetAttachedFace;
	static RefMethod mGetHandle, mGetDataWatcher, mGet_FromDataWatcher;
	static java.lang.reflect.Field ghastIsAttackingField;
	static RefMethod mGhastIsCharging;

	@SuppressWarnings("rawtypes")
	public static String getTextureKey(Entity entity){
		switch(entity.getType().name()){
			case "CREEPER":
				return ((Creeper)entity).isPowered() ? "CREEPER|CHARGED" : "CREEPER";
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
				if(entity.getCustomName() != null && entity.getCustomName().equals("Toast")) return "RABBIT|TOAST";
				return "RABBIT|"+((Rabbit)entity).getRabbitType().name();
			case "SHEEP":
				if(entity.getCustomName() != null && entity.getCustomName().equals("jeb_")) return "SHEEP|JEB";
				else return "SHEEP|"+((Sheep)entity).getColor().name();
			case "SHULKER":
				return getShulkerKey((Shulker)entity);
			case "TROPICAL_FISH":
				return getTropicalFishKey(EntityUtils.getCCP((TropicalFish)entity));
				/*CCP fishData = new CCP(f.getBodyColor(), f.getPatternColor(), f.getPattern());
				String name = HeadUtils.tropicalFishNames.get(fishData);
				if(name == null) name = EvUtils.getNormalizedName(entity.getType());
				String code = "wow i need to figure this out huh";
				return HeadUtils.makeSkull(code, name);*/
			case "BEE":
				if(mBeeHasNectar == null){
					RefClass classBee = ReflectionUtils.getRefClass("org.bukkit.entity.Bee");
					mBeeHasNectar = classBee.getMethod("hasNectar");
					mBeeHasStung = classBee.getMethod("hasStung");
					mBeeGetAnger = classBee.getMethod("getAnger");
				}
				String pollinated = mBeeHasNectar.of(entity).call().equals(true) ? "|POLLINATED" : "";
				String angry = ((int)mBeeGetAnger.of(entity).call()) > 0 ? "|ANGRY" : "";
				String usedSting = mBeeHasStung.of(entity).call().equals(true) ? "|STUNG" : "";
				return "BEE" + pollinated + angry + usedSting;
			case "VEX":
				if(ReflectionUtils.getServerVersionString().compareTo("v1_13_R3") < 0) return "VEX";
				if(mVexIsCharging == null) mVexIsCharging = ReflectionUtils.getRefClass("org.bukkit.entity.Vex").getMethod("isCharging");
				if(mVexIsCharging.of(entity).call().equals(true)) return "VEX|CHARGING";
				else return "VEX";
			case "VILLAGER":
				String villagerProfession = ((Villager)entity).getProfession().name();
				if(mGetVillagerType == null){
					try{mGetVillagerType = ReflectionUtils.getRefClass("org.bukkit.entity.Villager").getMethod("getVillagerType");}
					catch(RuntimeException ex){return "VILLAGER|"+villagerProfession;}
				}
				return "VILLAGER|"+villagerProfession+"|"+((Enum)mGetVillagerType.of(entity).call()).name();
			case "ZOMBIE_VILLAGER":
				String zombieVillagerProfession = ((ZombieVillager)entity).getVillagerProfession().name();
				if(mGetZombieVillagerType == null){
					try{mGetZombieVillagerType = ReflectionUtils.getRefClass("org.bukkit.entity.ZombieVillager").getMethod("getVillagerType");}
					catch(RuntimeException ex){return "ZOMBIE_VILLAGER|"+zombieVillagerProfession;}
				}
				return "ZOMBIE_VILLAGER|"+zombieVillagerProfession+"|"+((Enum)mGetZombieVillagerType.of(entity).call()).name();
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
					if(mCatGetCollarColor == null) mCatGetCollarColor =
							ReflectionUtils.getRefClass("org.bukkit.entity.Cat").getMethod("getCollarColor");
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
			case "AXOLOTL":
				if(mAxolotlGetVariant == null) mAxolotlGetVariant =
				ReflectionUtils.getRefClass("org.bukkit.entity.Axolotl").getMethod("getVariant");
				return "AXOLOTL|"+((Enum)mAxolotlGetVariant.of(entity).call()).name();
			case "GHAST":
				//https://wiki.vg/Entity_metadata#Ghast => isAttacking=15="" (1.13 - 1.15)
				boolean isScreaming = false;
				if(ghastIsAttackingField == null){
					mGetHandle = ReflectionUtils.getRefClass("{cb}.entity.CraftEntity").getMethod("getHandle");
					RefClass classEntityGhast = ReflectionUtils.getRefClass("{nms}.EntityGhast", "{nm}.world.entity.monster.EntityGhast");
					RefClass classDataWatcher = ReflectionUtils.getRefClass("{nms}.DataWatcher", "{nm}.network.syncher.DataWatcher");
					RefClass classDataWatcherObject = ReflectionUtils.getRefClass("{nms}.DataWatcherObject","{nm}.network.syncher.DataWatcherObject");
					// Only one field with this type: BuildTools/work/decompile-ee3ecae0/net/minecraft/world/entity/monster/EntityGhast.java
					ghastIsAttackingField = classEntityGhast.findField(classDataWatcherObject).getRealField();
					ghastIsAttackingField.setAccessible(true);
					mGetDataWatcher = classEntityGhast.findMethod(/*isStatic=*/false, classDataWatcher);//.getMethod("getDataWatcher");
					mGet_FromDataWatcher = classDataWatcher.findMethod(/*isStatic=*/false, 
							/*returnType=*/Object.class,/*classDataWatcherObject.getRealClass().getTypeParameters()[0]*/
							/*argType(s)=*/classDataWatcherObject);//.getMethod("get", classDataWatcherObject);
				}
				try{
					Object datawatcherobject = ghastIsAttackingField.get(null);
					Object entityGhast = mGetHandle.of(entity).call();
					Object dataWatcher = mGetDataWatcher.of(entityGhast).call();
					isScreaming = mGet_FromDataWatcher.of(dataWatcher).call(datawatcherobject).equals(true);
//					org.bukkit.Bukkit.getLogger().info("Ghast isCharging: "+mGet_FromDataWatcher.of(dataWatcher).call(datawatcherobject).toString());
				}
				catch(IllegalArgumentException | IllegalAccessException e){}
				if(isScreaming) return "GHAST|SCREAMING";//TODO: Add this to the Bukkit API
				else return "GHAST";
			case "GOAT":
				if(ReflectionUtils.getServerVersionString().compareTo("v1_17_??") < 0) return "GOAT";
				if(mGoatIsScreaming == null) mGoatIsScreaming = ReflectionUtils.getRefClass("org.bukkit.entity.Goat").getMethod("isScreaming");
				if(mGoatIsScreaming.of(entity).call().equals(true)) return "GOAT|SCREAMING";
				else return "GOAT";
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