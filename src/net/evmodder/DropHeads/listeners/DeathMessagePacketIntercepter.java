package net.evmodder.DropHeads.listeners;

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
import org.bukkit.scheduler.BukkitRunnable;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.EvLib.extras.PacketUtils;
import net.evmodder.EvLib.extras.ReflectionUtils;
import net.evmodder.EvLib.extras.ReflectionUtils.RefClass;
import net.evmodder.EvLib.extras.ReflectionUtils.RefField;
import net.evmodder.EvLib.extras.ReflectionUtils.RefMethod;

public class DeathMessagePacketIntercepter{
	private final Plugin pl;
	private final boolean REPLACE_PLAYER_DEATH_MSG, REPLACE_PET_DEATH_MSG;
	private final HashSet<UUID> unblockedDeathBroadcasts;
	private final HashSet<String> unblockedSpecificDeathMsgs;
	private final HashSet<String> blockedSpecificMsgs;

	private final RefClass outboundChatPacketClazz = ReflectionUtils.getRefClass(
			"{nms}.PacketPlayOutChat", "{nm}.network.protocol.game.PacketPlayOutChat", "{nm}.network.protocol.game.ClientboundSystemChatPacket");
	private final RefClass chatBaseCompClazz = ReflectionUtils.getRefClass(
			"{nms}.IChatBaseComponent", "{nm}.network.chat.IChatBaseComponent");
	private final RefClass chatSerializerClazz = ReflectionUtils.getRefClass(
			"{nms}.IChatBaseComponent$ChatSerializer", "{nm}.network.chat.IChatBaseComponent$ChatSerializer");
	private final RefField chatBaseCompField;
	private final RefMethod getChatBaseComp;
	private final RefMethod getJsonKyori; private final Object jsonSerializerKyori;
	private final RefMethod toJsonMethod; private final Object registryAccessObj;//class: IRegistryCustom.Dimension
	private final Pattern uuidPattern1 = Pattern.compile("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
	private final Pattern uuidPattern2 = Pattern.compile("\\[I?;?\\s*(-?[0-9]+),\\s*(-?[0-9]+),\\s*(-?[0-9]+),\\s*(-?[0-9]+)\\s*\\]");

	public DeathMessagePacketIntercepter(boolean replacePlayerDeathMsg, boolean replacePetDeathMsg){
		pl = DropHeads.getPlugin();
		REPLACE_PLAYER_DEATH_MSG = replacePlayerDeathMsg;
		REPLACE_PET_DEATH_MSG = replacePetDeathMsg;
		unblockedDeathBroadcasts = new HashSet<>();
		unblockedSpecificDeathMsgs = new HashSet<>();
		blockedSpecificMsgs = new HashSet<>();

		RefField field = null;
		RefMethod method = null, kyoriMethod = null; Object kyoriObj = null;
		try{field = outboundChatPacketClazz.findField(chatBaseCompClazz);}
		catch(RuntimeException e1){
			try{
				method = outboundChatPacketClazz.getMethod("adventure$content");
				kyoriObj = ReflectionUtils.getRefClass("net.kyori.adventure.text.serializer.json.JSONComponentSerializer").getMethod("json").call();
				kyoriMethod = ReflectionUtils.getRefClass("net.kyori.adventure.text.serializer.ComponentSerializer").findMethodByName("serialize");
			}
			catch(RuntimeException e2){method = outboundChatPacketClazz.getMethod("content");}
		}
		finally{
			chatBaseCompField = field;
			getChatBaseComp = method;
			getJsonKyori = kyoriMethod;
			jsonSerializerKyori = kyoriObj;
		}
		RefMethod toJsonMethodTemp; Object registryAccessObjTemp = null;
		try{//1.20.5+
			toJsonMethodTemp = chatSerializerClazz.findMethod(/*isStatic=*/true, String.class, chatBaseCompClazz,
					ReflectionUtils.getRefClass("{nm}.core.HolderLookup$Provider", "{nm}.core.HolderLookup$a"));
			// If above succeeds:
			try{
				Object nmsServerObj = ReflectionUtils.getRefClass("{cb}.CraftServer").getMethod("getServer").of(Bukkit.getServer()).call();
				//registryAccessObjTemp = ReflectionUtils.getRefClass("{nm}.server.MinecraftServer").getMethod("registryAccess").of(nmsServerObj).call();
				RefClass registryAccessClazz = ReflectionUtils.getRefClass("net.minecraft.core.IRegistryCustom$Dimension");
				registryAccessObjTemp = ReflectionUtils.getRefClass("{nm}.server.MinecraftServer")
						.findMethod(/*isStatic=*/false, registryAccessClazz).of(nmsServerObj).call();
			}
			catch(RuntimeException ex){ex.printStackTrace();}
		}
		catch(RuntimeException e){
			toJsonMethodTemp = chatSerializerClazz.findMethod(/*isStatic=*/true, String.class, chatBaseCompClazz);
		}
		toJsonMethod = toJsonMethodTemp;
		registryAccessObj = registryAccessObjTemp;

		pl.getServer().getPluginManager().registerEvents(new Listener(){
			@EventHandler public void onJoin(PlayerJoinEvent evt){
				try{injectPlayer(evt.getPlayer());}
				catch(Exception e){/*NoSuchElementException (wrapped) happens if they disconnect before login is complete*/}
			}
			@EventHandler public void onQuit(PlayerQuitEvent evt){removePlayer(evt.getPlayer());}
		}, pl);
		for(Player p : pl.getServer().getOnlinePlayers()) injectPlayer(p);
	}

	public boolean hasDeathMessage(Entity e){
		//net.minecraft.network.protocol.game.ClientboundSystemChatPacket ee; ee.content();
		return e instanceof Player || (
				e instanceof Tameable &&
				((Tameable)e).getOwner() != null &&
				pl.getServer().getEntity(((Tameable)e).getOwner().getUniqueId()) != null
		);
	}

	private UUID parseUUIDFromFourIntStrings(String s1, String s2, String s3, String s4){
		final Integer i1 = Integer.parseInt(s1), i2 = Integer.parseInt(s2), i3 = Integer.parseInt(s3), i4 = Integer.parseInt(s4); 
		return new UUID((long)i1 << 32 | i2 & 0xFFFFFFFFL, (long)i3 << 32 | i4 & 0xFFFFFFFFL);
	}

	private void injectPlayer(Player player){
		PacketUtils.getPlayerChannel(player).pipeline().addBefore("packet_handler", "replace_death_with_behead_msg", new ChannelDuplexHandler(){
			@Override public void write(ChannelHandlerContext context, Object packet, ChannelPromise promise) throws Exception {
				if(!outboundChatPacketClazz.isInstance(packet)){ // Not a chat packet
					super.write(context, packet, promise);
					return;
				}
//				pl.getLogger().info("chat packet:\n"+packet+"\n");
				final Object chatBaseComp = chatBaseCompField == null ? packet : chatBaseCompField.of(packet).get();
				if(chatBaseComp == null){ // Chat packet does not have a comp field/method (pre-1.19)
					super.write(context, packet, promise);
					return;
				}
//				if(chatBaseCompField != null) pl.getLogger().info("chat packet base comp:\n"+chatBaseComp+"\n");
				final String jsonMsg = (String)(chatBaseCompField != null ?
					(registryAccessObj != null ? toJsonMethod.call(chatBaseComp, registryAccessObj) : toJsonMethod.call(chatBaseComp)) :
					getJsonKyori == null ? getChatBaseComp.of(packet).call() : getJsonKyori.of(jsonSerializerKyori).call(getChatBaseComp.of(packet).call())
				);
				if(jsonMsg == null){ // Chat comp is not a json object
					super.write(context, packet, promise);
					return;
				}
//				pl.getLogger().info("chat packet json:\n"+jsonMsg+"\n");
				if(blockedSpecificMsgs.contains(jsonMsg)){ // Note: This is also used for plaintext death messages sent by DropChanceAPI
					return;
				}
//				pl.getLogger().info("chat packet isn't blocked");
				if(!jsonMsg.startsWith("{\"translate\":\"death.") || unblockedSpecificDeathMsgs.remove(jsonMsg)){
					super.write(context, packet, promise);
					return;
				}
//				pl.getLogger().info("detected death msg:\n"+jsonMsg);
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
				new BukkitRunnable(){@Override public void run(){ // server.getEntity(uuid) needs to be called synchronously
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
					new BukkitRunnable(){@Override public void run(){
						// check again if unblocked 1 tick later
						if(unblockedDeathBroadcasts.contains(uuid)) PacketUtils.sendPacket(player, packet);
						else pl.getLogger().fine("blocked death msg for: "+uuid);
					}}.runTaskLater(pl, 1);
				}}.runTask(pl);
			}
		});
	}
	private void removePlayer(Player player){
		final Channel channel = PacketUtils.getPlayerChannel(player);
		channel.eventLoop().submit(()->{
			channel.pipeline().remove("replace_death_with_behead_msg");
			return null;
		});
	}

	public void unblockDeathMessage(Entity entity){
		if(hasDeathMessage(entity)){
//			pl.getLogger().info("unblocked death msg called");
			unblockedDeathBroadcasts.add(entity.getUniqueId());
			new BukkitRunnable(){@Override public void run(){unblockedDeathBroadcasts.remove(entity.getUniqueId());}}.runTaskLater(pl, 5);
		}
	}

	public void blockSpeficicMessage(String message, long ticksBlockedFor){
		blockedSpecificMsgs.add(message);
		new BukkitRunnable(){@Override public void run(){blockedSpecificMsgs.remove(message);}}.runTaskLater(pl, ticksBlockedFor);
	}

	public void unregisterAll(){
		for(Player player : pl.getServer().getOnlinePlayers()) removePlayer(player);
	}
}
