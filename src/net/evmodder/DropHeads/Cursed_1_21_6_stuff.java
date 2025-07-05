package net.evmodder.DropHeads;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import net.evmodder.EvLib.extras.ReflectionUtils;
import net.evmodder.EvLib.extras.ReflectionUtils.RefClass;
import net.evmodder.EvLib.extras.ReflectionUtils.RefMethod;

public class Cursed_1_21_6_stuff{
	public static final String chatComponentToJson(/*IChatBaseComponent*/Object chatComponent){
		try{
			// Serialize into JsonElement
			Class<?> clazzJsonOps = Class.forName("com.mojang.serialization.JsonOps");
			Class<?> clazzDynamicOps = Class.forName("com.mojang.serialization.DynamicOps");
			Class<?> clazzRegistryOps = Class.forName("net.minecraft.resources.RegistryOps");
			Class<?> clazzDataResult = Class.forName("com.mojang.serialization.DataResult");
			RefClass classCodec = ReflectionUtils.getRefClass("com.mojang.serialization.Codec");
			RefClass classComponentSerialization = ReflectionUtils.getRefClass("net.minecraft.network.chat.ComponentSerialization");
			RefClass classCraftRegistry = ReflectionUtils.getRefClass("{cb}.CraftRegistry");
			RefClass classIRegistryCustom = ReflectionUtils.getRefClass("net.minecraft.core.IRegistryCustom");

			Object objCODEC = classComponentSerialization.findField(classCodec).of(null).get();
			Object objIRegistryCustom = classCraftRegistry.getMethod("getMinecraftRegistry").call();
			RefMethod methodCreateSerializationContext = classIRegistryCustom.findMethod(/*isStatic=*/false, clazzRegistryOps, clazzDynamicOps);
			Object objJsonOps = clazzJsonOps.getField("INSTANCE").get(null);
			Object objDynamicOps = methodCreateSerializationContext.of(objIRegistryCustom).call(objJsonOps);

			Method methodEncodeStart = Class.forName("com.mojang.serialization.Encoder").getMethod("encodeStart", clazzDynamicOps, Object.class);
//			RefMethod methodEncodeStart = classCodec.findMethod(/*isStatic=*/false, /*return=*/DataResult.class, clazzDynamicOps, Object.class);
			Object objDataResult = methodEncodeStart.invoke(objCODEC, objDynamicOps, chatComponent);
//			Object objDataResult = methodEncodeStart.of(objCODEC).call(objDynamicOps, chatComponent);

			Optional<?> objOptional = (Optional<?>)clazzDataResult.getMethod("result").invoke(objDataResult);
			Object objJsonElement = objOptional.orElse(null);
			if(objJsonElement == null || !(objJsonElement instanceof JsonElement je)) return "{}";
			return je.toString();

			// Serialize into JsonElement
//			JsonElement jsonElement = (JsonElement) ComponentSerialization.CODEC.encodeStart(
//					org.bukkit.craftbukkit.v1_21_R5.CraftRegistry.getMinecraftRegistry().createSerializationContext(
//							com.mojang.serialization.JsonOps.INSTANCE),
//					chatComponent
//					)
//					.result().orElse(null);
//			if(jsonElement == null) return "{}";
			// Stringify JsonElement using GSON
			// There is no easily accessible GSON constant that sets disableHtmlEscaping()
			// We call into ByteBufCodecs.lenientJson() as an alternative
	//		io.netty.buffer.ByteBuf tmpByteBuf = io.netty.buffer.Unpooled.buffer(1024);
	//		net.minecraft.network.codec.ByteBufCodecs.lenientJson(Integer.MAX_VALUE).encode(tmpByteBuf, jsonElement);
	//		return net.minecraft.network.Utf8String.read(tmpByteBuf, Integer.MAX_VALUE);
		}
		catch(IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException
				| ClassNotFoundException | InvocationTargetException | NoSuchMethodException e){
			e.printStackTrace();
			return null;
		}
	}

	public static final Object jsonToChatComponent(String jsonString){
//		#if version >= 1.21.6
		try{
			// Decode json string into JsonElement
			RefClass classJsonParser = ReflectionUtils.getRefClass("com.google.gson.JsonParser");
			JsonElement je = (JsonElement)classJsonParser.findMethod(/*isStatic=*/true, JsonElement.class, String.class).call(jsonString);
			if(je == null) return null;
	
			Class<?> clazzJsonOps = Class.forName("com.mojang.serialization.JsonOps");
			Class<?> clazzDynamicOps = Class.forName("com.mojang.serialization.DynamicOps");
			Class<?> clazzRegistryOps = Class.forName("net.minecraft.resources.RegistryOps");
			Class<?> clazzDataResult = Class.forName("com.mojang.serialization.DataResult");
			RefClass classCodec = ReflectionUtils.getRefClass("com.mojang.serialization.Codec");
			RefClass classComponentSerialization = ReflectionUtils.getRefClass("net.minecraft.network.chat.ComponentSerialization");
			RefClass classCraftRegistry = ReflectionUtils.getRefClass("{cb}.CraftRegistry");
			RefClass classIRegistryCustom = ReflectionUtils.getRefClass("net.minecraft.core.IRegistryCustom");

			Object objIRegistryCustom = classCraftRegistry.getMethod("getMinecraftRegistry").call();
			RefMethod methodCreateSerializationContext = classIRegistryCustom.findMethod(/*isStatic=*/false, clazzRegistryOps, clazzDynamicOps);
			Object objJsonOps = clazzJsonOps.getField("INSTANCE").get(null);
			Object objDynamicOps = methodCreateSerializationContext.of(objIRegistryCustom).call(objJsonOps);
			Object objCODEC = classComponentSerialization.findField(classCodec).of(null).get();
			Method methodParse = Class.forName("com.mojang.serialization.Decoder").getMethod("parse", clazzDynamicOps, Object.class);

			Object objDataResult = methodParse.invoke(objCODEC, objDynamicOps, je);
			Optional<?> objOptional = (Optional<?>)clazzDataResult.getMethod("result").invoke(objDataResult);
			return objOptional.orElse(null);
		}
		catch(IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException
				| ClassNotFoundException | NoSuchMethodException | InvocationTargetException e){
			e.printStackTrace();
			return null;
		}

//		#elseif version >= 1.21.5
//				return IChatBaseComponent$ChatSerializer.fromJson(jsonString,
//						org.bukkit.craftbukkit.CraftRegistry.getMinecraftRegistry());
//
//		#elseif version >= 1.20.5
//				return IChatBaseComponent$ChatSerializer.fromJson(jsonString,
//						net.minecraft.server.MinecraftServer.getDefaultRegistryAccess());
//		#else
//				return IChatBaseComponent$ChatSerializer.fromJson(jsonString);
	}
}
