package net.evmodder.DropHeads;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import com.google.gson.JsonElement;
import net.evmodder.EvLib.extras.ReflectionUtils;
import net.evmodder.EvLib.extras.ReflectionUtils.RefClass;
import net.evmodder.EvLib.extras.ReflectionUtils.RefMethod;

public class Cursed_1_21_6_stuff{
	private static Class<?> clazzJsonOps, clazzDynamicOps, clazzRegistryOps, clazzDataResult;
	private static RefClass classCodec, classComponentSerialization, classCraftRegistry, classIRegistryCustom;
	private static Object objCODEC, objIRegistryCustom, objJsonOps;
	private static RefMethod methodCreateSerializationContext;
	private static Method methodEncodeStart, methodParse;
	static{
		try{
			clazzJsonOps = Class.forName("com.mojang.serialization.JsonOps");
			clazzDynamicOps = Class.forName("com.mojang.serialization.DynamicOps");
			clazzRegistryOps = Class.forName("net.minecraft.resources.RegistryOps");
			clazzDataResult = Class.forName("com.mojang.serialization.DataResult");
			classCodec = ReflectionUtils.getRefClass("com.mojang.serialization.Codec");
			classComponentSerialization = ReflectionUtils.getRefClass("net.minecraft.network.chat.ComponentSerialization");
			classCraftRegistry = ReflectionUtils.getRefClass("{cb}.CraftRegistry");
			classIRegistryCustom = ReflectionUtils.getRefClass("net.minecraft.core.IRegistryCustom");
			objCODEC = classComponentSerialization.findField(classCodec).of(null).get();
			objIRegistryCustom = classCraftRegistry.getMethod("getMinecraftRegistry").call();
			methodCreateSerializationContext = classIRegistryCustom.findMethod(/*isStatic=*/false, clazzRegistryOps, clazzDynamicOps);
			objJsonOps = clazzJsonOps.getField("INSTANCE").get(null);

//			Class<?> clazzIChatBaseComponent = Class.forName("net.minecraft.network.chat.IChatBaseComponent");
			methodEncodeStart = Class.forName("com.mojang.serialization.Encoder").getMethod("encodeStart", clazzDynamicOps, Object.class);
			methodParse = Class.forName("com.mojang.serialization.Decoder").getMethod("parse", clazzDynamicOps, Object.class);
		}
		catch(IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException
				| ClassNotFoundException | NoSuchMethodException e){
			e.printStackTrace();
		}
	}
	static final String chatComponentToJson(/*IChatBaseComponent*/Object chatComponent){
		try{
			Object objDynamicOps = methodCreateSerializationContext.of(objIRegistryCustom).call(objJsonOps);
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

	static final Object jsonToChatComponent(String jsonString){
		try{
			RefClass classJsonParser = ReflectionUtils.getRefClass("com.google.gson.JsonParser");
			JsonElement je = (JsonElement)classJsonParser.findMethod(/*isStatic=*/true, JsonElement.class, String.class).call(jsonString);
			if(je == null) return null;
	
			Object objDynamicOps = methodCreateSerializationContext.of(objIRegistryCustom).call(objJsonOps);
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
