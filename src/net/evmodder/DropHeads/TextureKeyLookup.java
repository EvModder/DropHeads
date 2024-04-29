package net.evmodder.DropHeads;

import org.bukkit.DyeColor;
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
import net.evmodder.EvLib.extras.EntityUtils;
import net.evmodder.EvLib.extras.ReflectionUtils;
import net.evmodder.EvLib.extras.ReflectionUtils.RefClass;
import net.evmodder.EvLib.extras.ReflectionUtils.RefMethod;
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

		if(ReflectionUtils.getServerVersionString().compareTo("v1_16_R3") < 0) return shulkerAndColorKey;
		if(mShulkerGetAttachedFace == null){
			try{
				final RefClass classShulker = ReflectionUtils.getRefClass("org.bukkit.entity.Shulker");
				mShulkerGetPeek = classShulker.getMethod("getPeek");
				mShulkerGetAttachedFace = classShulker.getMethod("getAttachedFace");
			}
			catch(RuntimeException ex){return shulkerAndColorKey;}
		}
		float peek = (float)mShulkerGetPeek.of(shulker).call();
		//TODO: Add textures for "peeking" shulkers
		final String peekState = peek == 0 ? "|CLOSED" : peek == 1 ? "" : ""/*"|PEEKING"*/;
		final BlockFace attachedFace = (BlockFace)mShulkerGetAttachedFace.of(shulker).call();
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

	private static RefMethod mGetVillagerType, mGetZombieVillagerType;
	private static RefMethod mBeeHasNectar, mBeeGetAnger, mBeeHasStung;
	private static RefMethod mCatGetType, mCatGetCollarColor;
	private static RefMethod mFoxGetType, mFoxIsSleeping;
	private static RefMethod mPandaGetMainGene, mPandaGetHiddenGene;
	private static RefMethod mTraderLlamaGetColor;
	private static RefMethod mAxolotlGetVariant, mFrogGetVariant, mWolfGetVariant, mMushroomCowGetVariant;
	private static RefMethod mVexIsCharging;
	private static RefMethod mChestBoatGetType;
	private static RefMethod mGoatIsScreaming, mGoatHasLeftHorn, mGoatHasRightHorn;
	private static RefMethod mStriderIsShivering, mStriderHasSaddle;
	private static RefMethod mShulkerGetPeek, mShulkerGetAttachedFace;
	private static RefMethod mGetHandle, mGetDataWatcher, mGet_FromDataWatcher;
	private static java.lang.reflect.Field ghastIsAttackingField;
	private static RefMethod mGhastIsCharging;

	/** Find the most specific available head texture for a given entity.
	 * @param entity The entity for which we want a texture key
	 * @return The String key used to identify the target texture
	 */
	@SuppressWarnings("rawtypes")
	public static String getTextureKey(Entity entity){
		switch(entity.getType().name()){
			case "CREEPER":
				return ((Creeper)entity).isPowered() ? "CREEPER|CHARGED" : "CREEPER";
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
					final RefClass classBee = ReflectionUtils.getRefClass("org.bukkit.entity.Bee");
					mBeeHasNectar = classBee.getMethod("hasNectar");
					mBeeHasStung = classBee.getMethod("hasStung");
					mBeeGetAnger = classBee.getMethod("getAnger");
				}
				final String pollinated = mBeeHasNectar.of(entity).call().equals(true) ? "|POLLINATED" : "";
				final String angry = ((int)mBeeGetAnger.of(entity).call()) > 0 ? "|ANGRY" : "";
				final String usedSting = mBeeHasStung.of(entity).call().equals(true) ? "|STUNG" : "";
				return "BEE" + pollinated + angry + usedSting;
			}
			case "WOLF": {
				final String tameAndCollarOrAngry =
						((Wolf)entity).isTamed() ? "|TAME|"+((Wolf)entity).getCollarColor().name()+"_COLLARED" :
						((Wolf)entity).isAngry() ? "|ANGRY" : "";
				if(ReflectionUtils.getServerVersionString().compareTo("v1_20_R4") < 0) return "WOLF"+tameAndCollarOrAngry;
				if(mWolfGetVariant == null) mWolfGetVariant = ReflectionUtils.getRefClass("org.bukkit.entity.Wolf").getMethod("getVariant");
				return "WOLF|"+((Enum)mWolfGetVariant.of(entity).call()).name()+tameAndCollarOrAngry;
			}
			case "CHEST_BOAT":
				if(mChestBoatGetType == null) mChestBoatGetType = ReflectionUtils.getRefClass("org.bukkit.entity.ChestBoat").getMethod("getBoatType");
				return "CHEST_BOAT|"+((Enum)mChestBoatGetType.of(entity).call()).name();
			case "VEX":
				if(ReflectionUtils.getServerVersionString().compareTo("v1_13_R3") < 0) return "VEX";
				if(mVexIsCharging == null) mVexIsCharging = ReflectionUtils.getRefClass("org.bukkit.entity.Vex").getMethod("isCharging");
				if(mVexIsCharging.of(entity).call().equals(true)) return "VEX|CHARGING";
				else return "VEX";
			case "VILLAGER": {
				final String villagerProfession = ((Villager)entity).getProfession().name();
				if(mGetVillagerType == null){
					try{mGetVillagerType = ReflectionUtils.getRefClass("org.bukkit.entity.Villager").getMethod("getVillagerType");}
					catch(RuntimeException ex){return "VILLAGER|"+villagerProfession;}
				}
				return "VILLAGER|"+villagerProfession+"|"+((Enum)mGetVillagerType.of(entity).call()).name();
			}
			case "ZOMBIE_VILLAGER": {
				final String zombieVillagerProfession = ((ZombieVillager)entity).getVillagerProfession().name();
				if(mGetZombieVillagerType == null){
					try{mGetZombieVillagerType = ReflectionUtils.getRefClass("org.bukkit.entity.ZombieVillager").getMethod("getVillagerType");}
					catch(RuntimeException ex){return "ZOMBIE_VILLAGER|"+zombieVillagerProfession;}
				}
				return "ZOMBIE_VILLAGER|"+zombieVillagerProfession+"|"+((Enum)mGetZombieVillagerType.of(entity).call()).name();
			}
			case "OCELOT": {
				final String catType = ((Ocelot)entity).getCatType().name();
				return catType.equals("WILD_OCELOT") ? "OCELOT" : "OCELOT|"+catType;
			}
			case "CAT": {
				if(mCatGetType == null) mCatGetType = ReflectionUtils.getRefClass("org.bukkit.entity.Cat").getMethod("getCatType");
				String catType = ((Enum)mCatGetType.of(entity).call()).name();
				if(catType.equals("RED")) catType = "GINGER";
				if(catType.equals("BLACK")) catType = "TUXEDO";
				if(catType.equals("ALL_BLACK")) catType = "BLACK";
				if(((Tameable)entity).isTamed()){
					if(mCatGetCollarColor == null) mCatGetCollarColor =
							ReflectionUtils.getRefClass("org.bukkit.entity.Cat").getMethod("getCollarColor");
					return "CAT|"+catType+"|"+((DyeColor)mCatGetCollarColor.of(entity).call()).name()+"_COLLARED";
				}
				return "CAT|"+catType;
			}
//			case "IRON_GOLEM":
//				// DONE:TEST (in HeadAPI): Drop varying-crackiness iron golem heads
//				return "IRON_GOLEM";
			case "MUSHROOM_COW":
				if(ReflectionUtils.getServerVersionString().compareTo("v1_14_R0") < 0) return "MUSHROOM_COW";
				if(mMushroomCowGetVariant == null) mMushroomCowGetVariant =
					ReflectionUtils.getRefClass("org.bukkit.entity.MushroomCow").getMethod("getVariant");
				return "MUSHROOM_COW|"+((Enum)mMushroomCowGetVariant.of(entity).call()).name();
			case "FOX": {
				if(mFoxGetType == null){
					final RefClass classFox = ReflectionUtils.getRefClass("org.bukkit.entity.Fox");
					mFoxGetType = classFox.getMethod("getFoxType");
					mFoxIsSleeping = classFox.getMethod("isSleeping");
				}
				final String foxType = ((Enum)mFoxGetType.of(entity).call()).name();
				if(mFoxIsSleeping.of(entity).call().equals(true)) return "FOX|"+foxType+"|SLEEPING";
				else return "FOX|"+foxType;
			}
			case "FROG":
				if(mFrogGetVariant == null) mFrogGetVariant =
					ReflectionUtils.getRefClass("org.bukkit.entity.Frog").getMethod("getVariant");
				return "FROG|"+((Enum)mFrogGetVariant.of(entity).call()).name();
			case "PANDA": {
				if(mPandaGetMainGene == null){
					final RefClass classPanda = ReflectionUtils.getRefClass("org.bukkit.entity.Panda");
					mPandaGetMainGene = classPanda.getMethod("getMainGene");
					mPandaGetHiddenGene = classPanda.getMethod("getHiddenGene");
				}
				final String mainGene = ((Enum)mPandaGetMainGene.of(entity).call()).name();
				final String hiddenGene = ((Enum)mPandaGetHiddenGene.of(entity).call()).name();
				return "PANDA|"+EntityUtils.getPandaTrait(mainGene, hiddenGene);
			}
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
				if(mGhastIsCharging == null && ghastIsAttackingField == null){
					try{mGhastIsCharging = ReflectionUtils.getRefClass("org.bukkit.entity.Ghast").getMethod("isCharging");}
					catch(RuntimeException ex){}
					
					mGetHandle = ReflectionUtils.getRefClass("{cb}.entity.CraftEntity").getMethod("getHandle");
					final RefClass classEntityGhast = ReflectionUtils.getRefClass("{nms}.EntityGhast", "{nm}.world.entity.monster.EntityGhast");
					final RefClass classDataWatcher = ReflectionUtils.getRefClass("{nms}.DataWatcher", "{nm}.network.syncher.DataWatcher");
					final RefClass classDataWatcherObject =
							ReflectionUtils.getRefClass("{nms}.DataWatcherObject", "{nm}.network.syncher.DataWatcherObject");
					// Only one field with this type: BuildTools/work/decompile-ee3ecae0/net/minecraft/world/entity/monster/EntityGhast.java
					ghastIsAttackingField = classEntityGhast.findField(classDataWatcherObject).getRealField();
					ghastIsAttackingField.setAccessible(true);
					mGetDataWatcher = classEntityGhast.findMethod(/*isStatic=*/false, classDataWatcher);//.getMethod("getDataWatcher");
					mGet_FromDataWatcher = classDataWatcher.findMethod(/*isStatic=*/false, 
							/*returnType=*/Object.class,/*classDataWatcherObject.getRealClass().getTypeParameters()[0]*/
							/*argType(s)=*/classDataWatcherObject);//.getMethod("get", classDataWatcherObject);
				}
				if(mGhastIsCharging != null){
					// TODO: Since v?.?? Does this even work? Consider deleting old DataWatcher code approach?
					isScreaming = mGhastIsCharging.of(entity).call().equals(true);
				}
				else try{
					final Object datawatcherobject = ghastIsAttackingField.get(null);
					final Object entityGhast = mGetHandle.of(entity).call();
					final Object dataWatcher = mGetDataWatcher.of(entityGhast).call();
					isScreaming = mGet_FromDataWatcher.of(dataWatcher).call(datawatcherobject).equals(true);
//					org.bukkit.Bukkit.getLogger().info("Ghast isCharging: "+mGet_FromDataWatcher.of(dataWatcher).call(datawatcherobject).toString());
				}
				catch(IllegalArgumentException | IllegalAccessException e){}
				if(isScreaming) return "GHAST|SCREAMING";
				else return "GHAST";
			case "GOAT":
				if(ReflectionUtils.getServerVersionString().compareTo("v1_17_??") < 0) return "GOAT";
				if(mGoatHasLeftHorn == null){
					mGoatHasLeftHorn = ReflectionUtils.getRefClass("org.bukkit.entity.Goat").getMethod("hasLeftHorn");
					mGoatHasRightHorn = ReflectionUtils.getRefClass("org.bukkit.entity.Goat").getMethod("hasRightHorn");
					mGoatIsScreaming = ReflectionUtils.getRefClass("org.bukkit.entity.Goat").getMethod("isScreaming");
				}
				return "GOAT"
						+ (!mGoatHasLeftHorn.of(entity).call().equals(true) ? "|NO_LEFT_HORN" : "")
						+ (!mGoatHasRightHorn.of(entity).call().equals(true) ? "|NO_RIGHT_HORN" : "")
						+ (mGoatIsScreaming.of(entity).call().equals(true) ? "|SCREAMING" : "");
			case "STRIDER": {
				if(mStriderIsShivering == null){
					final RefClass classStrider = ReflectionUtils.getRefClass("org.bukkit.entity.Strider");
					mStriderIsShivering = classStrider.getMethod("isShivering");
					mStriderHasSaddle = classStrider.getMethod("hasSaddle");
				}
				final boolean isShivering = mStriderIsShivering.of(entity).call().equals(true);
				final boolean hasSaddle = mStriderHasSaddle.of(entity).call().equals(true);
				return "STRIDER|"+(isShivering ? "COLD" : "WARM")+(hasSaddle ? "|SADDLED" : "");
			}
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