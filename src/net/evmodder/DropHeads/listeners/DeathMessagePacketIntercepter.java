package net.evmodder.DropHeads.listeners;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.EvLib.bukkit.PacketUtils;
import net.evmodder.EvLib.util.ReflectionUtils;

public class DeathMessagePacketIntercepter implements Listener{
	private final Plugin pl;
	private final boolean REPLACE_PLAYER_DEATH_MSG, REPLACE_PET_DEATH_MSG;
	private final HashSet<UUID> unblockedDeathBroadcasts;
	private final HashSet<String> unblockedSpecificDeathMsgs;
	private final HashSet<String> blockedSpecificMsgs;

	private final Class<?> outboundChatPacketClazz = ReflectionUtils.getClass(
			"{nms}.PacketPlayOutChat", "{nm}.network.protocol.game.PacketPlayOutChat", "{nm}.network.protocol.game.ClientboundSystemChatPacket");
	private final Class<?> chatBaseCompClazz = ReflectionUtils.getClass(
			"{nms}.IChatBaseComponent", "{nm}.network.chat.IChatBaseComponent");
	private final Class<?> chatSerializerClazz = ReflectionUtils.getClass(
			"{nms}.IChatBaseComponent$ChatSerializer", "{nm}.network.chat.IChatBaseComponent$ChatSerializer", "{nm}.network.chat.ComponentSerialization");
	private final Field chatBaseCompField;
	private final Method getChatBaseComp;
	private final Method getJsonKyori; private final Object jsonSerializerKyori;
	private final Method toJsonMethod; private final Object registryAccessObj;//class: IRegistryCustom.Dimension
	private final Pattern uuidPattern1 = Pattern.compile("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
	private final Pattern uuidPattern2 = Pattern.compile("\\[I?;?\\s*(-?[0-9]+),\\s*(-?[0-9]+),\\s*(-?[0-9]+),\\s*(-?[0-9]+)\\s*\\]");

	public DeathMessagePacketIntercepter(boolean replacePlayerDeathMsg, boolean replacePetDeathMsg){
		pl = DropHeads.getPlugin();
		REPLACE_PLAYER_DEATH_MSG = replacePlayerDeathMsg;
		REPLACE_PET_DEATH_MSG = replacePetDeathMsg;
		unblockedDeathBroadcasts = new HashSet<>();
		unblockedSpecificDeathMsgs = new HashSet<>();
		blockedSpecificMsgs = new HashSet<>();

		Field field = null;
		Method method = null, kyoriMethod = null; Object kyoriObj = null;
		try{field = ReflectionUtils.findField(outboundChatPacketClazz, chatBaseCompClazz);}
		catch(RuntimeException e1){
			try{
				method = ReflectionUtils.getMethod(outboundChatPacketClazz, "adventure$content");
				Class<?> classJSONComponentSerializer = ReflectionUtils.getClass("net.kyori.adventure.text.serializer.json.JSONComponentSerializer");
				Method method_JSONComponentSerializer_json = ReflectionUtils.getMethod(classJSONComponentSerializer, "json");
				kyoriObj = ReflectionUtils.callStatic(method_JSONComponentSerializer_json);
				Class<?> classComponentSerializer = ReflectionUtils.getClass("net.kyori.adventure.text.serializer.ComponentSerializer");
				kyoriMethod = ReflectionUtils.findMethodByName(classComponentSerializer, "serialize");
			}
			catch(RuntimeException e2){method = ReflectionUtils.getMethod(outboundChatPacketClazz, "content");}
		}
		finally{
			chatBaseCompField = field;
			getChatBaseComp = method;
			getJsonKyori = kyoriMethod;
			jsonSerializerKyori = kyoriObj;
		}
		Method toJsonMethodTemp; Object registryAccessObjTemp = null;
		try{//1.20.5+
			if(ReflectionUtils.isAtLeastVersion("v1_21_6")){
				Class<?> classCursed_1_21_6_stuff = ReflectionUtils.getClass("net.evmodder.DropHeads.Cursed_1_21_6_stuff");
				toJsonMethodTemp = ReflectionUtils.getMethod(classCursed_1_21_6_stuff, "chatComponentToJson", Object.class);
			}
			else toJsonMethodTemp = ReflectionUtils.findMethod(chatSerializerClazz, /*isStatic=*/true, String.class, chatBaseCompClazz,
					ReflectionUtils.getClass("{nm}.core.HolderLookup$Provider", "{nm}.core.HolderLookup$a"));
			// If above succeeds:
			try{
				Class<?> classCraftServer = ReflectionUtils.getClass("{cb}.CraftServer");
				Method method_CraftServer_getServer = ReflectionUtils.getMethod(classCraftServer, "getServer");
				Object nmsServerObj = ReflectionUtils.call(method_CraftServer_getServer, Bukkit.getServer());
				//registryAccessObjTemp = ReflectionUtils.getRefClass("{nm}.server.MinecraftServer").getMethod("registryAccess").of(nmsServerObj).call();
				Class<?> classRegistryAccess = ReflectionUtils.getClass("net.minecraft.core.IRegistryCustom$Dimension");
				Class<?> classMinecraftServer = ReflectionUtils.getClass("{nm}.server.MinecraftServer");
				Method method_MinecraftServer_getRegistryAccess = ReflectionUtils.findMethod(classMinecraftServer, /*isStatic=*/false, classRegistryAccess);
				registryAccessObjTemp = ReflectionUtils.call(method_MinecraftServer_getRegistryAccess, nmsServerObj);
			}
			catch(RuntimeException ex){ex.printStackTrace();}
		}
		catch(RuntimeException e){
			try{toJsonMethodTemp = ReflectionUtils.findMethod(chatSerializerClazz, /*isStatic=*/true, String.class, chatBaseCompClazz);}
			catch(RuntimeException re){toJsonMethodTemp = null; re.printStackTrace();}
		}
		toJsonMethod = toJsonMethodTemp;
		registryAccessObj = registryAccessObjTemp;

		if(toJsonMethod != null){
			pl.getServer().getPluginManager().registerEvents(this, pl);
			for(Player p : pl.getServer().getOnlinePlayers()) injectPlayer(p);
		}
	}

	public boolean hasDeathMessage(Entity e){
		//net.minecraft.network.protocol.game.ClientboundSystemChatPacket ee; ee.content();
		return e instanceof Player || (
				e instanceof Tameable &&
				((Tameable)e).getOwner() != null &&
				pl.getServer().getEntity(((Tameable)e).getOwner().getUniqueId()) != null
		);
	}

	private final class CustomPacketHandler extends ChannelDuplexHandler{
		private final Player player;
		CustomPacketHandler(Player p){player=p;}

		private UUID parseUUIDFromFourIntStrings(String s1, String s2, String s3, String s4){
			final Integer i1 = Integer.parseInt(s1), i2 = Integer.parseInt(s2), i3 = Integer.parseInt(s3), i4 = Integer.parseInt(s4);
			return new UUID((long)i1 << 32 | i2 & 0xFFFFFFFFL, (long)i3 << 32 | i4 & 0xFFFFFFFFL);
		}

		@Override public void write(ChannelHandlerContext context, Object packet, ChannelPromise promise) throws Exception {
			if(!outboundChatPacketClazz.isInstance(packet)){ // Not a chat packet
				super.write(context, packet, promise);
				return;
			}
//			pl.getLogger().info("chat packet:\n"+packet+"\n");
			final Object chatBaseComp = chatBaseCompField == null ? packet : ReflectionUtils.get(chatBaseCompField, packet);
			if(chatBaseComp == null){ // Chat packet does not have a comp field/method (pre-1.19)
				super.write(context, packet, promise);
				return;
			}
//			if(chatBaseCompField != null) pl.getLogger().info("chat packet base comp:\n"+chatBaseComp+"\n");
			final String jsonMsg = ReflectionUtils.isAtLeastVersion("v1_21_6")
				? (String)ReflectionUtils.callStatic(toJsonMethod, chatBaseComp) :
				(String)(chatBaseCompField != null ? (registryAccessObj != null
					? ReflectionUtils.callStatic(toJsonMethod, chatBaseComp, registryAccessObj)
					: ReflectionUtils.callStatic(toJsonMethod, chatBaseComp)) :
					getJsonKyori == null
						? ReflectionUtils.call(getChatBaseComp, packet)
						: ReflectionUtils.call(getJsonKyori, jsonSerializerKyori, ReflectionUtils.call(getChatBaseComp, packet))
				);
			if(jsonMsg == null){ // Chat comp is not a json object
				super.write(context, packet, promise);
				return;
			}
//			pl.getLogger().info("chat packet json:\n"+jsonMsg+"\n");
			if(blockedSpecificMsgs.contains(jsonMsg)){ // Note: This is also used for plaintext death messages sent by DropChanceAPI
				return;
			}
//			pl.getLogger().info("chat packet isn't blocked");
			if(!jsonMsg.startsWith("{\"translate\":\"death.") || unblockedSpecificDeathMsgs.remove(jsonMsg)){
				super.write(context, packet, promise);
				return;
			}
//			pl.getLogger().info("detected death msg:\n"+jsonMsg);
			final UUID uuid; // uuid of entity that died
			final Matcher matcher2 = uuidPattern2.matcher(jsonMsg);
			if(matcher2.find()) uuid = parseUUIDFromFourIntStrings(matcher2.group(1), matcher2.group(2), matcher2.group(3), matcher2.group(4));
			else{
				final Matcher matcher1 = uuidPattern1.matcher(jsonMsg);
				if(matcher1.find()) uuid = UUID.fromString(matcher1.group());
				else{
					pl.getLogger().warning("Unable to find UUID from death message: "+jsonMsg);
					pl.getLogger().warning("This is probably caused by another plugin destructively modifying the selector");
					super.write(context, packet, promise);
					return;
				}
			}
			if(unblockedDeathBroadcasts.contains(uuid)){
				super.write(context, packet, promise);
				return;
			}
			// server.getEntity(uuid) needs to be called synchronously
			pl.getServer().getScheduler().runTask(pl, ()->{
				if(unblockedDeathBroadcasts.contains(uuid)){
					PacketUtils.sendPacket(player, packet); // If this death msg has been unblocked
					return;
				}
				final Entity victim = pl.getServer().getEntity(uuid);
				if(victim == null){
					pl.getLogger().warning("Unable to find death-message entity by UUID: "+uuid);
					PacketUtils.sendPacket(player, packet);
					return;
				}
				if(!hasDeathMessage(victim)) pl.getLogger().warning("Detected abnormal death message for non-player entity: "+jsonMsg);

				final boolean shouldReplaceDeathMsg = victim instanceof Player ? REPLACE_PLAYER_DEATH_MSG : REPLACE_PET_DEATH_MSG;
				if(!shouldReplaceDeathMsg){
					unblockedSpecificDeathMsgs.add(jsonMsg);
					PacketUtils.sendPacket(player, packet);
					return;
				}
				pl.getServer().getScheduler().runTaskLater(pl, ()->{
					// Check again 1gt later to double-ensure not unblocked
					if(unblockedDeathBroadcasts.contains(uuid)) PacketUtils.sendPacket(player, packet);
					else pl.getLogger().fine("blocked death msg for: "+uuid);
				}, 1);
			});
		}
	}
	private void injectPlayer(Player player){
		if(toJsonMethod != null)
		PacketUtils.getPlayerChannel(player).pipeline().addBefore("packet_handler", "replace_death_with_behead_msg", new CustomPacketHandler(player));
	}
	private void removePlayer(Player player){
		final Channel channel = PacketUtils.getPlayerChannel(player);
		channel.eventLoop().submit(()->{
			channel.pipeline().remove("replace_death_with_behead_msg");
			return null;
		});
	}

	@EventHandler private void onJoin(PlayerJoinEvent evt){
		try{injectPlayer(evt.getPlayer());}
		catch(Exception e){/*NoSuchElementException (wrapped) happens if they disconnect before login is complete*/}
	}
	@EventHandler private void onQuit(PlayerQuitEvent evt){removePlayer(evt.getPlayer());}

	public void unblockDeathMessage(Entity entity){
		if(hasDeathMessage(entity)){
//			pl.getLogger().info("unblocked death msg called");
			unblockedDeathBroadcasts.add(entity.getUniqueId());
			pl.getServer().getScheduler().runTaskLater(pl, ()->unblockedDeathBroadcasts.remove(entity.getUniqueId()), 5);
		}
	}

	public void blockSpeficicMessage(String message, long ticksBlockedFor){
		blockedSpecificMsgs.add(message);
		pl.getServer().getScheduler().runTaskLater(pl, ()->blockedSpecificMsgs.remove(message), ticksBlockedFor);
	}

	public void unregisterAll(){
		for(Player player : pl.getServer().getOnlinePlayers()) removePlayer(player);
	}
}