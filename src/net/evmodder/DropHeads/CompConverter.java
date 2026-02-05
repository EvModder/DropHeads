package net.evmodder.DropHeads;

import java.lang.reflect.Method;
import java.util.Optional;
import org.bukkit.Bukkit;
import com.google.gson.JsonElement;
import net.evmodder.EvLib.util.ReflectionUtils;

public class CompConverter{
	// Pre 1.21.6
	private static Object registryAccessObj;//class: IRegistryCustom.Dimension
	private static Method toStrMethod, toCompMethod;

	// 1.21.6+
	// ComponentSerialization, IRegistryCustom, JsonOps
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
				Class<?> classIRegistryCustom = ReflectionUtils.getClass("net.minecraft.core.IRegistryCustom");
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
							classMinecraftServer, /*isStatic=*/false, ReflectionUtils.getClass("net.minecraft.core.IRegistryCustom$Dimension"));
					registryAccessObj = ReflectionUtils.call(method_MinecraftServer_getRegistryAccess, nmsServerObj);
				}
				final Class<?> iChatBaseComponentClass = ReflectionUtils.getClass("{nm}.network.chat.IChatBaseComponent");
				final Class<?> chatSerializerClass = ReflectionUtils.getClass("{nm}.network.chat.IChatBaseComponent$ChatSerializer",
						"{nm}.network.chat.ComponentSerialization");
				final Class<?> classIChatMutableComponent = ReflectionUtils.getClass("{nm}.network.chat.IChatMutableComponent");
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
}