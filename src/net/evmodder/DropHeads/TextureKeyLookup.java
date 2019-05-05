package net.evmodder.DropHeads;

import org.bukkit.entity.Cat;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Ocelot;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.TraderLlama;
import org.bukkit.entity.TropicalFish;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.ZombieVillager;
import net.evmodder.EvLib2.EvUtils;

public class TextureKeyEntityLookup{
	static String getTextureKey(LivingEntity entity){
		switch(entity.getType()){
			case CREEPER:
				if(((Creeper)entity).isPowered()) return "CREEPER|CHARGED";
				else return "CREEPER";
			case WOLF:
				if(((Wolf)entity).isAngry()) return "WOLF|ANGRY";
				else return "WOLF";
			case HORSE:
				return "HORSE|"+((Horse)entity).getColor().name();
			case LLAMA:
				return "LLAMA|"+((Llama)entity).getColor().name();
			case PARROT:
				return "PARROT|"+((Parrot)entity).getVariant().name();
			case RABBIT:
				return "RABBIT|"+((Rabbit)entity).getRabbitType().name();
			case SHEEP:
				if(entity.getCustomName() != null && entity.getCustomName().equals("jeb_")) return "SHEEP|JEB";
				else return "SHEEP|"+((Sheep)entity).getColor().name();
			case SHULKER:
				return "SHULKER|"+((Shulker)entity).getColor().name();
			case TROPICAL_FISH:
				TropicalFish f = (TropicalFish)entity;
				return "TROPICAL_FISH|"+f.getBodyColor()+"|"+f.getPatternColor()+"|"+f.getPattern();
				/*CCP fishData = new CCP(f.getBodyColor(), f.getPatternColor(), f.getPattern());
				String name = HeadUtils.tropicalFishNames.get(fishData);
				if(name == null) name = EvUtils.getNormalizedName(entity.getType());
				String code = "wow i need to figure this out huh";
				return HeadUtils.makeSkull(code, name);*/
			case VEX:
				if(((Vex)entity).isCharging()) return "VEX|CHARGING";
				else return "VEX";
			case ZOMBIE_VILLAGER:
				return "ZOMBIE_VILLAGER|"+((ZombieVillager)entity).getVillagerProfession().name();
			case VILLAGER:
				return "VILLAGER|"+((Villager)entity).getProfession().name();
			case OCELOT:
				return "OCELOT|"+((Ocelot)entity).getCatType().name();
			case CAT:
				return "CAT|"+((Cat)entity).getCatType().name();
			case MUSHROOM_COW:
				return "MUSHROOM_COW|"+((MushroomCow)entity).getVariant().name();
			case FOX:
				if(((Fox)entity).isSleeping()) return "FOX|"+((Fox)entity).getFoxType().name()+"|SLEEPING";
				else return "FOX|"+((Fox)entity).getFoxType().name();
			case PANDA:
				return "PANDA|"+EvUtils.getPandaTrait((Panda)entity);
			case TRADER_LLAMA:
				return "TRADER_LLAMA|"+((TraderLlama)entity).getColor().name();
			default:
				return entity.getType().name();
		}
	}
}