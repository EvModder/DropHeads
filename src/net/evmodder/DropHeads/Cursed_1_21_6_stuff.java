package net.evmodder.DropHeads;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import com.google.gson.JsonElement;
import net.evmodder.EvLib.util.ReflectionUtils;

public class Cursed_1_21_6_stuff{
	private static Class<?> clazzJsonOps, clazzDynamicOps, clazzRegistryOps, clazzDataResult;
	private static Class<?> classCodec, classComponentSerialization, classCraftRegistry, classIRegistryCustom;
	private static Object objCODEC, objIRegistryCustom, objJsonOps;
	private static Method methodCreateSerializationContext;
	private static Method methodEncodeStart, methodParse;
	static{
		try{
			clazzJsonOps = ReflectionUtils.getClass("com.mojang.serialization.JsonOps");
			clazzDynamicOps = ReflectionUtils.getClass("com.mojang.serialization.DynamicOps");
			clazzRegistryOps = ReflectionUtils.getClass("net.minecraft.resources.RegistryOps");
			clazzDataResult = ReflectionUtils.getClass("com.mojang.serialization.DataResult");
			classCodec = ReflectionUtils.getClass("com.mojang.serialization.Codec");
			classComponentSerialization = ReflectionUtils.getClass("net.minecraft.network.chat.ComponentSerialization");
			classCraftRegistry = ReflectionUtils.getClass("{cb}.CraftRegistry");
			classIRegistryCustom = ReflectionUtils.getClass("net.minecraft.core.IRegistryCustom");
			objCODEC = ReflectionUtils.getStatic(ReflectionUtils.findField(classComponentSerialization, classCodec));
			objIRegistryCustom = ReflectionUtils.callStatic(ReflectionUtils.getMethod(classCraftRegistry, "getMinecraftRegistry"));
			methodCreateSerializationContext = ReflectionUtils.findMethod(classIRegistryCustom, /*isStatic=*/false, clazzRegistryOps, clazzDynamicOps);
			objJsonOps = ReflectionUtils.getStatic(ReflectionUtils.getField(clazzJsonOps, "INSTANCE"));

//			Class<?> clazzIChatBaseComponent = Class.forName("net.minecraft.network.chat.IChatBaseComponent");
			methodEncodeStart = ReflectionUtils.getMethod(ReflectionUtils.getClass("com.mojang.serialization.Encoder"), "encodeStart", clazzDynamicOps, Object.class);
			methodParse = ReflectionUtils.getMethod(ReflectionUtils.getClass("com.mojang.serialization.Decoder"), "parse", clazzDynamicOps, Object.class);
		}
		catch(RuntimeException e){
			e.printStackTrace();
		}
	}
	public static final String chatComponentToJson(/*IChatBaseComponent*/Object chatComponent){
		try{
			Object objDynamicOps = ReflectionUtils.call(methodCreateSerializationContext, objIRegistryCustom, objJsonOps);
			Object objDataResult = methodEncodeStart.invoke(objCODEC, objDynamicOps, chatComponent);

			// Last checked: 1.21.8
//			net.minecraft.network.chat.ComponentSerialization.CODEC.encodeStart(
//					CraftRegistry.getMinecraftRegistry().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),
//					(net.minecraft.network.chat.IChatBaseComponent)chatComponent);

			Optional<?> objOptional = (Optional<?>)clazzDataResult.getMethod("result").invoke(objDataResult);
			Object objJsonElement = objOptional.orElse(null);
			if(objJsonElement == null || !(objJsonElement instanceof JsonElement je)) return "{}";
			return je.toString();
		}
		catch(IllegalArgumentException | IllegalAccessException | SecurityException | InvocationTargetException | NoSuchMethodException e){
			e.printStackTrace();
			return null;
		}
	}

	public static final Object jsonToChatComponent(String jsonString){
		try{
			Class<?> classJsonParser = ReflectionUtils.getClass("com.google.gson.JsonParser");
			Method method_JsonParser_fromString = ReflectionUtils.findMethod(classJsonParser, /*isStatic=*/true, JsonElement.class, String.class);
			JsonElement je = (JsonElement)ReflectionUtils.callStatic(method_JsonParser_fromString, jsonString);
			if(je == null) return null;
	
			Object objDynamicOps = ReflectionUtils.call(methodCreateSerializationContext, objIRegistryCustom, objJsonOps);
			Object objDataResult = methodParse.invoke(objCODEC, objDynamicOps, je);
			Optional<?> objOptional = (Optional<?>)clazzDataResult.getMethod("result").invoke(objDataResult);
			return objOptional.orElse(null);
		}
		catch(IllegalArgumentException | IllegalAccessException | SecurityException | NoSuchMethodException | InvocationTargetException e){
			e.printStackTrace();
			return null;
		}
	}
}