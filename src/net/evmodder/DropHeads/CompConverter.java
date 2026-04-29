package net.evmodder.DropHeads;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import com.google.gson.JsonElement;
import net.evmodder.EvLib.util.ReflectionUtils;

public class CompConverter{
	// Pre 1.21.6
	private static Object registryAccessObj;//class: IRegistryCustom.Dimension
	private static Method toStrMethod, toCompMethod;

	// 1.21.6+
	// ComponentSerialization, IRegistryCustom, RegistryAccess, JsonOps
	private static Object objCODEC, objIRegistryCustom, objJsonOps;

	private static Method method_IRegistryCustom_createSerializationContext;
	private static Method method_Encoder_encodeStart, method_Decoder_parse, method_DataResult_result;
	static{
		try{
			if(ReflectionUtils.isAtLeastVersion("v1_21_6")){
				Class<?> classJsonOps = ReflectionUtils.getClass("com.mojang.serialization.JsonOps");
				Class<?> classDynamicOps = ReflectionUtils.getClass("com.mojang.serialization.DynamicOps");
				Class<?> classRegistryOps = ReflectionUtils.getClass("net.minecraft.resources.RegistryOps");
				Class<?> classDataResult = ReflectionUtils.getClass("com.mojang.serialization.DataResult");
				Class<?> classCodec = ReflectionUtils.getClass("com.mojang.serialization.Codec");
				Class<?> classComponentSerialization = ReflectionUtils.getClass("net.minecraft.network.chat.ComponentSerialization");
				Class<?> classCraftRegistry = ReflectionUtils.getClass("{cb}.CraftRegistry");
				Class<?> classIRegistryCustom = ReflectionUtils.getClass("{nm}.core.IRegistryCustom", "{nm}.core.RegistryAccess", "{nm}.core.HolderLookup$Provider");
//				Class<?> clazzIChatBaseComponent = Class.forName("net.minecraft.network.chat.IChatBaseComponent");

				objCODEC = ReflectionUtils.getStatic(ReflectionUtils.findField(classComponentSerialization, classCodec));
				objIRegistryCustom = ReflectionUtils.callStatic(ReflectionUtils.getMethod(classCraftRegistry, "getMinecraftRegistry"));
				objJsonOps = ReflectionUtils.getStatic(ReflectionUtils.getField(classJsonOps, "INSTANCE"));

				method_DataResult_result = ReflectionUtils.getMethod(classDataResult, "result");
				method_IRegistryCustom_createSerializationContext = ReflectionUtils.findMethod(classIRegistryCustom,
						/*isStatic=*/false, classRegistryOps, classDynamicOps);
				method_Encoder_encodeStart = ReflectionUtils.getMethod(ReflectionUtils.getClass("com.mojang.serialization.Encoder"), "encodeStart", classDynamicOps, Object.class);
				method_Decoder_parse = ReflectionUtils.getMethod(ReflectionUtils.getClass("com.mojang.serialization.Decoder"), "parse", classDynamicOps, Object.class);
			}
			else{
				if(ReflectionUtils.isAtLeastVersion("v1_20_5")){
					Class<?> classCraftServer = ReflectionUtils.getClass("{cb}.CraftServer");
					Method method_CraftServer_getServer = ReflectionUtils.getMethod(classCraftServer, "getServer");
					final Object nmsServerObj = ReflectionUtils.call(method_CraftServer_getServer, Bukkit.getServer());
					Class<?> classMinecraftServer = ReflectionUtils.getClass("{nm}.server.MinecraftServer");
					Method method_MinecraftServer_getRegistryAccess = ReflectionUtils.findMethod(
						classMinecraftServer, /*isStatic=*/false, ReflectionUtils.getClass("net.minecraft.core.IRegistryCustom$Dimension", "{nm}.core.RegistryAccess$Frozen"));
					registryAccessObj = ReflectionUtils.call(method_MinecraftServer_getRegistryAccess, nmsServerObj);
				}
				final Class<?> iChatBaseComponentClass = ReflectionUtils.getClass("{nm}.network.chat.IChatBaseComponent", "{nm}.network.chat.Component");
				final Class<?> chatSerializerClass = ReflectionUtils.getClass("{nm}.network.chat.IChatBaseComponent$ChatSerializer",
						"{nm}.network.chat.ComponentSerialization");
				final Class<?> classIChatMutableComponent = ReflectionUtils.getClass("{nm}.network.chat.IChatMutableComponent", "{nm}.network.chat.MutableComponent");
				final Class<?> holderLookupProviderClass = ReflectionUtils.getClass("{nm}.core.HolderLookup$Provider", "{nm}.core.HolderLookup$a");
//				fromJsonMethod = chatSerializerClass.getMethod("fromJson", String.class, holderLookupProviderClass);
				toCompMethod = ReflectionUtils.findMethod(chatSerializerClass, /*isStatic=*/true, classIChatMutableComponent, String.class, holderLookupProviderClass);
//				toJsonMethod = chatSerializerClass.getMethod("toJson", iChatBaseComponentClass, holderLookupProviderClass);
				toStrMethod = ReflectionUtils.findMethod(chatSerializerClass, /*isStatic=*/true, String.class, iChatBaseComponentClass, holderLookupProviderClass);
			}
		}
		catch(RuntimeException e){e.printStackTrace();}
	}

	public static final String jsonStrFromChatComp(/*IChatBaseComponent*/Object chatComp){
		if(chatComp == null) return null;
		try{
			if(toStrMethod != null) return (String)ReflectionUtils.callStatic(toStrMethod, chatComp, registryAccessObj);

			// Last checked: 1.21.8
//			net.minecraft.network.chat.ComponentSerialization.CODEC.encodeStart(
//					CraftRegistry.getMinecraftRegistry().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),
//					(net.minecraft.network.chat.IChatBaseComponent)chatComponent);

			// 1.21.6+
			Object objDynamicOps = ReflectionUtils.call(method_IRegistryCustom_createSerializationContext, objIRegistryCustom, objJsonOps);
			Object objDataResult = ReflectionUtils.call(method_Encoder_encodeStart, objCODEC, objDynamicOps, chatComp);
			Optional<?> objOptional = (Optional<?>)ReflectionUtils.call(method_DataResult_result, objDataResult);
			Object objJsonElement = objOptional.orElse(null);
			if(objJsonElement == null || !(objJsonElement instanceof JsonElement je)) return "{}";
			return je.toString();
		}
		catch(RuntimeException e){
			e.printStackTrace();
			return null;
		}
	}

	public static final Object chatCompFromJsonStr(String jsonStr){
		if(jsonStr == null) return null;
		try{
			if(toCompMethod != null) return (String)ReflectionUtils.callStatic(toCompMethod, jsonStr, registryAccessObj);

			// 1.21.6+
			Class<?> classJsonParser = ReflectionUtils.getClass("com.google.gson.JsonParser");
			Method method_JsonParser_fromString = ReflectionUtils.findMethod(classJsonParser, /*isStatic=*/true, JsonElement.class, String.class);
			JsonElement je = (JsonElement)ReflectionUtils.callStatic(method_JsonParser_fromString, jsonStr);
			if(je == null) return null;
	
			Object objDynamicOps = ReflectionUtils.call(method_IRegistryCustom_createSerializationContext, objIRegistryCustom, objJsonOps);
			Object objDataResult = ReflectionUtils.call(method_Decoder_parse, objCODEC, objDynamicOps, je);
			Optional<?> objOptional = (Optional<?>)ReflectionUtils.call(method_DataResult_result, objDataResult);
			return objOptional.orElse(null);
		}
		catch(RuntimeException e){
			e.printStackTrace();
			return null;
		}
	}

	// New non-NMS way (1.20.6+?)
	private static final Method mItemMetaGetAsString;
	// Old NMS way
	private static final Method asNMSCopyMethod;
	private static final Method saveNmsItemStackMethod;
	private static final Class<?> nbtTagCompoundClazz;
	private static final Constructor<?> cnstr_NBTTagCompound;;
	static{
		Method mItemMetaGetAsStringTemp;
		try{mItemMetaGetAsStringTemp = ReflectionUtils.getMethod(ItemMeta.class, "getAsString");}
		catch(RuntimeException e1){mItemMetaGetAsStringTemp = null;}
		if(mItemMetaGetAsStringTemp != null){
			// Non-NMS method
			mItemMetaGetAsString = mItemMetaGetAsStringTemp;
			asNMSCopyMethod = null;
			saveNmsItemStackMethod = null;
			nbtTagCompoundClazz = null;
			cnstr_NBTTagCompound = null;
		}
		else{
			mItemMetaGetAsString = null;

			// NMS to convert a net.minecraft.server.vX_X.ItemStack to a valid JSON string
			final Class<?> craftItemStackClazz = ReflectionUtils.getClass("{cb}.inventory.CraftItemStack");
			asNMSCopyMethod = ReflectionUtils.getMethod(craftItemStackClazz, "asNMSCopy", ItemStack.class);
			final Class<?> nmsItemStackClazz = ReflectionUtils.getClass("{nms}.ItemStack", "{nm}.world.item.ItemStack");

			nbtTagCompoundClazz = ReflectionUtils.getClass("{nms}.NBTTagCompound", "{nm}.nbt.NBTTagCompound", "{nm}.nbt.CompoundTag");
			cnstr_NBTTagCompound = ReflectionUtils.getConstructor(nbtTagCompoundClazz);
			Method saveNmsItemStackMethodTemp;
			try{
				Class<?> classHolderLookupProvider = ReflectionUtils.getClass("{nm}.core.HolderLookup$Provider");//1.20.5+
				Class<?> classNBTBase = ReflectionUtils.getClass("{nm}.nbt.NBTBase", "{nm}.nbt.Tag");
				saveNmsItemStackMethodTemp = ReflectionUtils.findMethod(nmsItemStackClazz, /*isStatic=*/false, classNBTBase, classHolderLookupProvider);
			}
			catch(RuntimeException e2){
				// TODO: seems we can still get a 3rd RuntimeException here in 1.20.6
				saveNmsItemStackMethodTemp = ReflectionUtils.findMethod(nmsItemStackClazz, /*isStatic=*/false, nbtTagCompoundClazz, nbtTagCompoundClazz);
			}
			saveNmsItemStackMethod = saveNmsItemStackMethodTemp;
		}
	}

	//TODO: Reduce item json data in a less destructive way
	//reduceItemData() -> clear book pages, clear hidden NBT, call recursively for containers
	private static final String convertItemStackToJsonSimple(ItemStack item){
		return "{id:\""+item.getType().getKey().getKey()+"\",count:"+item.getAmount()+"}";
	}

	// https://www.spigotmc.org/threads/tut-item-tooltips-with-the-chatcomponent-api.65964/
	/**
	 * Converts an {@link org.bukkit.inventory.ItemStack} to a JSON string
	 * for sending with TextAction.ITEM
	 *
	 * @param itemStack the item to convert
	 * @return the JSON string representation of the item
	 */
	public static final String convertItemStackToJson(ItemStack item, int JSON_LIMIT){
		final String jsonString;
		if(mItemMetaGetAsString != null){
			if(!item.hasItemMeta()) return convertItemStackToJsonSimple(item);
			jsonString = "{id:\""+item.getType().getKey().getKey()+"\",count:"+item.getAmount()+",components:"
					+ReflectionUtils.call(mItemMetaGetAsString, item.getItemMeta())+"}";
		}
		else{
			Object nmsItemStackObj = ReflectionUtils.callStatic(asNMSCopyMethod, item);
			Object newTagOrRegistryAccess = registryAccessObj != null ? registryAccessObj : ReflectionUtils.construct(cnstr_NBTTagCompound);
			Object itemAsJsonObject = ReflectionUtils.call(saveNmsItemStackMethod, nmsItemStackObj, newTagOrRegistryAccess);
			jsonString = itemAsJsonObject.toString();
		}
		if(jsonString.length() <= JSON_LIMIT) return jsonString;
		return convertItemStackToJsonSimple(item);
	}
	//tellraw @p {"text":"Test","hoverEvent":{"action":"show_item","value":"{\"id\":\"bow\",\"count\":1,\"components\":{\"Unbreakable\":\"1\"}}"}}

//	// Similar as above, but for Entity instead of ItemStack
//	final static RefClass craftEntityClazz = ReflectionUtils.getRefClass("{cb}.entity.CraftEntity");
//	final static RefMethod entityGetHandleMethod = craftEntityClazz.getMethod("getHandle");
//	final static RefClass nmsEntityClazz = ReflectionUtils.getRefClass("{nms}.Entity", "{nm}.world.entity.Entity");
//	final static RefMethod saveNmsEntityMethod = nmsEntityClazz.findMethod(/*isStatic=*/false, nbtTagCompoundClazz, nbtTagCompoundClazz);

//	public final static String convertEntityToJson(Entity entity){
//		Object nmsNbtTagCompoundObj = nbtTagCompoundClazz.getConstructor().create();
//		Object nmsEntityObj = entityGetHandleMethod.of(entity).call();
//		Object entityAsJsonObject = saveNmsEntityMethod.of(nmsEntityObj).call(nmsNbtTagCompoundObj);
//		return entityAsJsonObject.toString();
//	}
}