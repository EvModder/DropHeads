package net.evmodder.DropHeads;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.bukkit.DyeColor;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Ocelot;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TropicalFish;
import net.evmodder.EvLib.bukkit.EntityUtils;
import net.evmodder.EvLib.util.ReflectionUtils;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.ZombieVillager;

/** Utility for getting a TextureKey from an Entity based on its sub-type, attributes, state, etc.
 */
public final class TextureKeyLookup{
	/** Default constructor, unused. Reason it's here:
	 * 1) Javadoc complains if a class doesn't have a constructor
	 * 2) I cannot convert this class to an interface since Java<9 does not allow private interface methods
	 */
	private TextureKeyLookup(){}

	@SuppressWarnings("deprecation")
	private static String getTropicalFishKey(TropicalFish fish){
		int pccInt = EntityUtils.getPCCInt(fish);
		Integer id = EntityUtils.getCommonTropicalFishId(pccInt);
		if(id != null){
//			System.out.println("name: "+EntityUtils.getTropicalFishEnglishName(ccp).toUpperCase().replace(' ', '_'));
			return "TROPICAL_FISH|"+EntityUtils.getTropicalFishEnglishName(pccInt).toUpperCase().replace(' ', '_');
		}
		else{
//			System.out.println("fallback name: "+new StringBuilder("TROPICAL_FISH|")
//					.append(fish.getBodyColor().name()).append('|').append(fish.getPatternColor().name())
//					.append('|').append(fish.getPattern().name()).toString());
			return new StringBuilder("TROPICAL_FISH|")
					.append(fish.getBodyColor().name()).append('|').append(fish.getPatternColor().name())
					.append('|').append(fish.getPattern().name()).toString();
		}
	}
	private static BlockFace turnLeft(BlockFace face){
		switch(face){
			case NORTH: return BlockFace.WEST;
			case WEST: return BlockFace.SOUTH;
			case SOUTH: return BlockFace.EAST;
			case EAST: return BlockFace.NORTH;
			default: return null;
		}
	}
	private static BlockFace getNearest6dBlockFace(Location from, Location to){
		final double diffX = Math.abs(to.getX() - from.getX());
		final double diffY = Math.abs(to.getY() - from.getY());
		final double diffZ = Math.abs(to.getZ() - from.getZ());
		if(diffX > diffY && diffX > diffZ){
			return to.getX() > from.getX() ? BlockFace.EAST : BlockFace.WEST;
		}
		if(diffZ > diffY){
			return to.getZ() > from.getZ() ? BlockFace.SOUTH : BlockFace.NORTH;
		}
		return to.getY() > from.getY() ? BlockFace.UP : BlockFace.DOWN;
	}
	private static String getShulkerKey(Shulker shulker){
		final DyeColor color = shulker.getColor();
		final String shulkerAndColorKey = color == null ? "SHULKER" : "SHULKER|"+color.name();
		//org.bukkit.Bukkit.getLogger().info("shulker facing: "+shulker.getFacing());

		if(!ReflectionUtils.isAtLeastVersion("v1_16_4")) return shulkerAndColorKey;
		if(mShulkerGetAttachedFace == null){
			try{
				final Class<?> classShulker = ReflectionUtils.getClass("org.bukkit.entity.Shulker");
				mShulkerGetPeek = ReflectionUtils.getMethod(classShulker, "getPeek");
				mShulkerGetAttachedFace = ReflectionUtils.getMethod(classShulker, "getAttachedFace");
			}
			catch(RuntimeException ex){return shulkerAndColorKey;}
		}
		float peek = (float)ReflectionUtils.call(mShulkerGetPeek, shulker);
		//TODO: Add textures for "peeking" shulkers
		final String peekState = peek == 0 ? "|CLOSED" : peek == 1 ? "" : ""/*"|PEEKING"*/;
		final BlockFace attachedFace = (BlockFace)ReflectionUtils.call(mShulkerGetAttachedFace, shulker);
		final String rotation;
		switch(attachedFace){
			case UP:
				rotation = "|GRUMM"; break;
			case NORTH: case SOUTH: case EAST: case WEST:
				if(peekState.equals("|CLOSED")) rotation = "|SIDEWAYS";
//				else if(shulker.getFacing() == BlockFace.UP) rotation = "|SIDE_UP";
//				else if(shulker.getFacing() == BlockFace.DOWN) rotation = "|SIDE_DOWN";
//				else if(shulker.getFacing() == turnLeft(attachedFace)) rotation = "|SIDE_LEFT";
//				else/*if(shulker.getFacing() == turnRight(attachedFace))*/rotation = "|SIDE_RIGHT";
				//error("shulker facing: "+shulker.getFacing().name()); // TODO: Spigot bug report (always SOUTH for sideways shulkers)!
				else if(shulker.getTarget() != null && shulker.getTarget().getEyeLocation() != null){
					BlockFace realFacing = getNearest6dBlockFace(shulker.getEyeLocation(), shulker.getTarget().getEyeLocation());
					if(realFacing == BlockFace.UP) rotation = "|SIDE_UP";
					else if(realFacing == BlockFace.DOWN) rotation = "|SIDE_DOWN";
					else if(realFacing == turnLeft(attachedFace)) rotation = "|SIDE_LEFT";
					else/*if(realFacing == turnRight(attachedFace))*/rotation = "|SIDE_RIGHT";
				}
				else rotation = "";
				break;
			default: case DOWN: rotation = "";
		}
		return shulkerAndColorKey + peekState + rotation;
	}

	@SuppressWarnings("rawtypes")
	private static String getEnumNameOrKeyedName(Entity entity, Method mGetType){
		Object type = ReflectionUtils.call(mGetType, entity);
		try{return ((Keyed)type).getKey().getKey().toUpperCase();}//1.21+
		catch(ClassCastException e){return ((Enum)type).name();}//pre 1.21
	}

	private static Method mGetVillagerType, mGetZombieVillagerType;
	private static Method mBoggedIsSheared;
	private static Method mBeeHasNectar, mBeeGetAnger, mBeeHasStung;
	private static Method mCatGetType, mCatGetCollarColor;
	private static Method mCopperGolem_getWeatherState;
	private static Method mFoxGetType, mFoxIsSleeping;
	private static Method mPandaGetMainGene, mPandaGetHiddenGene;
	private static Method mTraderLlamaGetColor;
	private static Method mAxolotlGetVariant, mCowGetVariant, mChickenGetVariant, mFrogGetVariant, mPigGetVariant, mWolfGetVariant, mMushroomCowGetVariant;
	private static Method mVexIsCharging;
	private static Method mChestBoatGetType;
	private static Method mGoatIsScreaming, mGoatHasLeftHorn, mGoatHasRightHorn;
	private static Method mStriderIsShivering, mStriderHasSaddle;
	private static Method mShulkerGetPeek, mShulkerGetAttachedFace;
	private static Method mGetHandle, mGetDataWatcher, mGet_FromDataWatcher;
	private static Method mHappyGhast_isAdult;
	private static Field ghastIsAttackingField;
	private static Method mGhastIsCharging;

	/** Find the most specific available head texture for a given entity.
	 * @param entity The entity for which we want a texture key
	 * @return The String key used to identify the target texture
	 */
	@SuppressWarnings({ "rawtypes", "deprecation" })
	public static String getTextureKey(Entity entity){
		switch(entity.getType().name()){
			case "BOGGED":
				if(mBoggedIsSheared == null) mBoggedIsSheared = ReflectionUtils.getMethod(ReflectionUtils.getClass("org.bukkit.entity.Bogged"), "isSheared");
				return ReflectionUtils.call(mBoggedIsSheared, entity).equals(true) ? "BOGGED|SHEARED" : "BOGGED";
			case "CREEPER":
				return ((Creeper)entity).isPowered() ? "CREEPER|CHARGED" : "CREEPER";
			case "HORSE":
				//TODO: isSaddled
				return "HORSE|"+((Horse)entity).getColor().name();
			case "PIG": //TODO: isSaddled
				if(!ReflectionUtils.isAtLeastVersion("v1_21_5")) return "PIG";
				if(mPigGetVariant == null) mPigGetVariant = ReflectionUtils.getMethod(ReflectionUtils.getClass("org.bukkit.entity.Pig"), "getVariant");
				return "PIG|"+getEnumNameOrKeyedName(entity, mPigGetVariant);
			case "CHICKEN": //TODO: isSaddled
				if(!ReflectionUtils.isAtLeastVersion("v1_21_5")) return "CHICKEN";
				if(mChickenGetVariant == null) mChickenGetVariant = ReflectionUtils.getMethod(ReflectionUtils.getClass("org.bukkit.entity.Chicken"), "getVariant");
				return "CHICKEN|"+getEnumNameOrKeyedName(entity, mChickenGetVariant);
			case "DONKEY": case "MULE": //TODO: isSaddled
				return entity.getType().name();
			case "LLAMA":
				//TODO: getCarpetColor
				return "LLAMA|"+((Llama)entity).getColor().name();
			case "PARROT":
				return "PARROT|"+((Parrot)entity).getVariant().name();
			case "PAINTING":
				return "PAINTING|"+((Painting)entity).getArt().name();
			case "RABBIT":
				if(entity.getCustomName() != null && entity.getCustomName().equals("Toast")) return "RABBIT|TOAST";
				return "RABBIT|"+((Rabbit)entity).getRabbitType().name();
			case "SHEEP":
				if(entity.getCustomName() != null && entity.getCustomName().equals("jeb_")) return "SHEEP|JEB";
				else return "SHEEP|"+((Sheep)entity).getColor().name();
			case "SHULKER":
				return getShulkerKey((Shulker)entity);
			case "TROPICAL_FISH":
				return getTropicalFishKey((TropicalFish)entity);
				/*CCP fishData = new CCP(f.getBodyColor(), f.getPatternColor(), f.getPattern());
				String name = HeadUtils.tropicalFishNames.get(fishData);
				if(name == null) name = EvUtils.getNormalizedName(entity.getType());
				String code = "wow i need to figure this out huh";
				return HeadUtils.makeSkull(code, name);*/
			case "BEE": {
				if(mBeeHasNectar == null){
					final Class<?> classBee = ReflectionUtils.getClass("org.bukkit.entity.Bee");
					mBeeHasNectar = ReflectionUtils.getMethod(classBee, "hasNectar");
					mBeeHasStung = ReflectionUtils.getMethod(classBee, "hasStung");
					mBeeGetAnger = ReflectionUtils.getMethod(classBee, "getAnger");
				}
				final String pollinated = ReflectionUtils.call(mBeeHasNectar, entity).equals(true) ? "|POLLINATED" : "";
				final String angry = ((int)ReflectionUtils.call(mBeeGetAnger, entity)) > 0 ? "|ANGRY" : "";
				final String usedSting = ReflectionUtils.call(mBeeHasStung, entity).equals(true) ? "|STUNG" : "";
				return "BEE" + pollinated + angry + usedSting;
			}
			case "WOLF": {
				final String tameAndCollarOrAngry =
						((Wolf)entity).isTamed() ? "|TAME|"+((Wolf)entity).getCollarColor().name()+"_COLLARED" :
						((Wolf)entity).isAngry() ? "|ANGRY" : "";
				if(!ReflectionUtils.isAtLeastVersion("v1_20_5")) return "WOLF"+tameAndCollarOrAngry;
				if(mWolfGetVariant == null) mWolfGetVariant = ReflectionUtils.getMethod(ReflectionUtils.getClass("org.bukkit.entity.Wolf"), "getVariant");
				return "WOLF|"+((Keyed)ReflectionUtils.call(mWolfGetVariant, entity)).getKey().getKey().toUpperCase()+tameAndCollarOrAngry;
			}
			case "CHEST_BOAT":
				if(mChestBoatGetType == null) mChestBoatGetType = ReflectionUtils.getMethod(ReflectionUtils.getClass("org.bukkit.entity.ChestBoat"), "getBoatType");
				return "CHEST_BOAT|"+((Enum)ReflectionUtils.call(mChestBoatGetType, entity)).name();
			case "VEX":
				if(!ReflectionUtils.isAtLeastVersion("v1_13_2")) return "VEX";
				if(mVexIsCharging == null) mVexIsCharging = ReflectionUtils.getMethod(ReflectionUtils.getClass("org.bukkit.entity.Vex"), "isCharging");
				if(ReflectionUtils.call(mVexIsCharging, entity).equals(true)) return "VEX|CHARGING";
				else return "VEX";
			case "VILLAGER": {
				final String villagerProfession = ((Villager)entity).getProfession().name();
				if(mGetVillagerType == null){
					try{mGetVillagerType = ReflectionUtils.getMethod(ReflectionUtils.getClass("org.bukkit.entity.Villager"), "getVillagerType");}
					catch(RuntimeException ex){return "VILLAGER|"+villagerProfession;}
				}
				return "VILLAGER|"+villagerProfession+"|"+getEnumNameOrKeyedName(entity, mGetVillagerType);
			}
			case "ZOMBIE_VILLAGER": {
				final String zombieVillagerProfession = ((ZombieVillager)entity).getVillagerProfession().name();
				if(mGetZombieVillagerType == null){
					try{mGetZombieVillagerType = ReflectionUtils.getMethod(ReflectionUtils.getClass("org.bukkit.entity.ZombieVillager"), "getVillagerType");}
					catch(RuntimeException ex){return "ZOMBIE_VILLAGER|"+zombieVillagerProfession;}
				}
				return "ZOMBIE_VILLAGER|"+zombieVillagerProfession+"|"+getEnumNameOrKeyedName(entity, mGetZombieVillagerType);
			}
			case "OCELOT": {
				final String catType = ((Ocelot)entity).getCatType().name();
				return catType.equals("WILD_OCELOT") ? "OCELOT" : "OCELOT|"+catType;
			}
			case "CAT": {
				if(mCatGetType == null) mCatGetType = ReflectionUtils.getMethod(ReflectionUtils.getClass("org.bukkit.entity.Cat"), "getCatType");
				String catType = getEnumNameOrKeyedName(entity, mCatGetType);
				if(catType.equals("RED")) catType = "GINGER";
				if(catType.equals("BLACK")) catType = "TUXEDO";
				if(catType.equals("ALL_BLACK")) catType = "BLACK";
				if(((Tameable)entity).isTamed()){
					if(mCatGetCollarColor == null) mCatGetCollarColor =
							ReflectionUtils.getMethod(ReflectionUtils.getClass("org.bukkit.entity.Cat"), "getCollarColor");
					return "CAT|"+catType+"|"+((DyeColor)ReflectionUtils.call(mCatGetCollarColor, entity)).name()+"_COLLARED";
				}
				return "CAT|"+catType;
			}
			case "COPPER_GOLEM":
				if(mCopperGolem_getWeatherState == null) mCopperGolem_getWeatherState = ReflectionUtils.getMethod(
						ReflectionUtils.getClass("org.bukkit.entity.CopperGolem"), "getWeatherState");
				return "COPPER_GOLEM|"+getEnumNameOrKeyedName(entity, mCopperGolem_getWeatherState);
			case "COW":
				if(!ReflectionUtils.isAtLeastVersion("v1_21_5")) return "COW";
				if(mCowGetVariant == null) mCowGetVariant = ReflectionUtils.getMethod(ReflectionUtils.getClass("org.bukkit.entity.Cow"), "getVariant");
				return "COW|"+getEnumNameOrKeyedName(entity, mCowGetVariant);
			case "MUSHROOM_COW":
				if(!ReflectionUtils.isAtLeastVersion("v1_14")) return "MUSHROOM_COW";
				if(mMushroomCowGetVariant == null) mMushroomCowGetVariant =
						ReflectionUtils.getMethod(ReflectionUtils.getClass("org.bukkit.entity.MushroomCow"), "getVariant");
				return "MUSHROOM_COW|"+((Enum)ReflectionUtils.call(mMushroomCowGetVariant, entity)).name();
			case "FOX": {
				if(mFoxGetType == null){
					final Class<?> classFox = ReflectionUtils.getClass("org.bukkit.entity.Fox");
					mFoxGetType = ReflectionUtils.getMethod(classFox, "getFoxType");
					mFoxIsSleeping = ReflectionUtils.getMethod(classFox, "isSleeping");
				}
				final String foxType = ((Enum)ReflectionUtils.call(mFoxGetType, entity)).name();
				if(ReflectionUtils.call(mFoxIsSleeping, entity).equals(true)) return "FOX|"+foxType+"|SLEEPING";
				else return "FOX|"+foxType;
			}
			case "FROG":
				if(mFrogGetVariant == null) mFrogGetVariant =
						ReflectionUtils.getMethod(ReflectionUtils.getClass("org.bukkit.entity.Frog"), "getVariant");
				return "FROG|"+getEnumNameOrKeyedName(entity, mFrogGetVariant);
			case "PANDA": {
				if(mPandaGetMainGene == null){
					final Class<?> classPanda = ReflectionUtils.getClass("org.bukkit.entity.Panda");
					mPandaGetMainGene = ReflectionUtils.getMethod(classPanda, "getMainGene");
					mPandaGetHiddenGene = ReflectionUtils.getMethod(classPanda, "getHiddenGene");
				}
				final String mainGene = ((Enum)ReflectionUtils.call(mPandaGetMainGene, entity)).name();
				final String hiddenGene = ((Enum)ReflectionUtils.call(mPandaGetHiddenGene, entity)).name();
				return "PANDA|"+EntityUtils.getPandaTrait(mainGene, hiddenGene);
			}
			case "TRADER_LLAMA":
				if(mTraderLlamaGetColor == null) mTraderLlamaGetColor =
						ReflectionUtils.getMethod(ReflectionUtils.getClass("org.bukkit.entity.TraderLlama"), "getColor");
				return "TRADER_LLAMA|"+((Enum)ReflectionUtils.call(mTraderLlamaGetColor, entity)).name();
			case "AXOLOTL":
				if(mAxolotlGetVariant == null) mAxolotlGetVariant =
						ReflectionUtils.getMethod(ReflectionUtils.getClass("org.bukkit.entity.Axolotl"), "getVariant");
				return "AXOLOTL|"+((Enum)ReflectionUtils.call(mAxolotlGetVariant, entity)).name();
			case "GHAST":
				//https://wiki.vg/Entity_metadata#Ghast => isAttacking=15="" (1.13 - 1.15)
				boolean isScreaming = false;
				if(mGhastIsCharging == null && ghastIsAttackingField == null){
					try{mGhastIsCharging = ReflectionUtils.getMethod(ReflectionUtils.getClass("org.bukkit.entity.Ghast"), "isCharging");}
					catch(RuntimeException ex){}
					
					mGetHandle = ReflectionUtils.getMethod(ReflectionUtils.getClass("{cb}.entity.CraftEntity"), "getHandle");
					final Class<?> classEntityGhast = ReflectionUtils.getClass("{nms}.EntityGhast", "{nm}.world.entity.monster.EntityGhast");
					final Class<?> classDataWatcher = ReflectionUtils.getClass("{nms}.DataWatcher", "{nm}.network.syncher.DataWatcher");
					final Class<?> classDataWatcherObject = ReflectionUtils.getClass("{nms}.DataWatcherObject", "{nm}.network.syncher.DataWatcherObject");
					// Only one field with this type: BuildTools/work/decompile-ee3ecae0/net/minecraft/world/entity/monster/EntityGhast.java
					ghastIsAttackingField = ReflectionUtils.findField(classEntityGhast, classDataWatcherObject);
					ghastIsAttackingField.setAccessible(true);
					mGetDataWatcher = ReflectionUtils.findMethod(classEntityGhast, /*isStatic=*/false, classDataWatcher);//.getMethod("getDataWatcher");
					mGet_FromDataWatcher = ReflectionUtils.findMethod(classDataWatcher, /*isStatic=*/false, 
							/*returnType=*/Object.class,/*classDataWatcherObject.getRealClass().getTypeParameters()[0]*/
							/*argType(s)=*/classDataWatcherObject);//.getMethod("get", classDataWatcherObject);
				}
				if(mGhastIsCharging != null){
					// TODO: Since v?.?? Does this even work? Consider deleting old DataWatcher code approach?
					isScreaming = ReflectionUtils.call(mGhastIsCharging, entity).equals(true);
				}
				else try{
					final Object datawatcherobject = ghastIsAttackingField.get(null);
					final Object entityGhast = ReflectionUtils.call(mGetHandle, entity);
					final Object dataWatcher = ReflectionUtils.call(mGetDataWatcher, entityGhast);
					isScreaming = ReflectionUtils.call(mGet_FromDataWatcher, dataWatcher, datawatcherobject).equals(true);
//					org.bukkit.Bukkit.getLogger().info("Ghast isCharging: "+mGet_FromDataWatcher.of(dataWatcher).call(datawatcherobject).toString());
				}
				catch(IllegalArgumentException | IllegalAccessException e){}
				if(isScreaming) return "GHAST|SCREAMING";
				else return "GHAST";
			case "GOAT":
				if(!ReflectionUtils.isAtLeastVersion("v1_17")) return "GOAT";
				if(mGoatHasLeftHorn == null){
					Class<?> classGoat = ReflectionUtils.getClass("org.bukkit.entity.Goat");
					mGoatHasLeftHorn = ReflectionUtils.getMethod(classGoat, "hasLeftHorn");
					mGoatHasRightHorn = ReflectionUtils.getMethod(classGoat, "hasRightHorn");
					mGoatIsScreaming = ReflectionUtils.getMethod(classGoat, "isScreaming");
				}
				return "GOAT"
						+ (!ReflectionUtils.call(mGoatHasLeftHorn, entity).equals(true) ? "|NO_LEFT_HORN" : "")
						+ (!ReflectionUtils.call(mGoatHasRightHorn, entity).equals(true) ? "|NO_RIGHT_HORN" : "")
						+ (ReflectionUtils.call(mGoatIsScreaming, entity).equals(true) ? "|SCREAMING" : "");
			case "HAPPY_GHAST":
				if(mHappyGhast_isAdult == null) mHappyGhast_isAdult = ReflectionUtils.getMethod(ReflectionUtils.getClass("org.bukkit.entity.HappyGhast"), "isAdult");
				return "HAPPY_GHAST" + (ReflectionUtils.call(mHappyGhast_isAdult, entity).equals(true) ? "" : "|BABY");
			case "STRIDER": {
				if(mStriderIsShivering == null){
					final Class<?> classStrider = ReflectionUtils.getClass("org.bukkit.entity.Strider");
					mStriderIsShivering = ReflectionUtils.getMethod(classStrider, "isShivering");
					mStriderHasSaddle = ReflectionUtils.getMethod(classStrider, "hasSaddle");
				}
				final boolean isShivering = ReflectionUtils.call(mStriderIsShivering, entity).equals(true);
				final boolean hasSaddle = ReflectionUtils.call(mStriderHasSaddle, entity).equals(true);
				return "STRIDER|"+(isShivering ? "COLD" : "WARM")+(hasSaddle ? "|SADDLED" : "");
			}
			case "PIG_ZOMBIE":
				if(((Zombie)entity).isBaby()) return "PIG_ZOMBIE|BABY";
				return "PIG_ZOMBIE";
			case "PLAYER":
				/* hmm */
			default:
				return entity.getType().name();
		}
	}
}