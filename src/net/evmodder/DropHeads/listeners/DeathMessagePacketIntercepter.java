package net.evmodder.DropHeads.listeners;

import java.util.HashSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import net.evmodder.DropHeads.JunkUtils;
import net.evmodder.EvLib.extras.ReflectionUtils;
import net.evmodder.EvLib.extras.ReflectionUtils.RefClass;
import net.evmodder.EvLib.extras.ReflectionUtils.RefField;
import net.evmodder.EvLib.extras.ReflectionUtils.RefMethod;

public class DeathMessagePacketIntercepter{
	final Plugin pl;
	final boolean REPLACE_PLAYER_DEATH_MSG, REPLACE_PET_DEATH_MSG;
	final HashSet<UUID> unblockedDeathMsgs;

	final RefClass packetPlayOutChatClazz = ReflectionUtils.getRefClass(
			"{nms}.PacketPlayOutChat", "{nm}.network.protocol.game.PacketPlayOutChat");
	final RefClass chatBaseCompClazz = ReflectionUtils.getRefClass(
			"{nms}.IChatBaseComponent", "{nm}.network.chat.IChatBaseComponent");
	final RefClass chatSerializerClazz = ReflectionUtils.getRefClass(
			"{nms}.IChatBaseComponent$ChatSerializer", "{nm}.network.chat.IChatBaseComponent$ChatSerializer");
	final RefField chatBaseCompField = packetPlayOutChatClazz.findField(chatBaseCompClazz);
	final RefMethod toJsonMethod = chatSerializerClazz.findMethod(/*isStatic=*/true, String.class, chatBaseCompClazz);
	final Pattern uuidPattern = Pattern.compile("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");

	private void injectPlayer(Player player){
		JunkUtils.getPlayerChannel(player).pipeline().addBefore("packet_handler", player.getName(), new ChannelDuplexHandler(){
			@Override public void write(ChannelHandlerContext context, Object packet, ChannelPromise promise) throws Exception {
				if(packetPlayOutChatClazz.isInstance(packet)){
					final Object chatBaseComp = chatBaseCompField.of(packet).get();
					if(chatBaseComp != null){
						final String jsonMsg = (String)toJsonMethod.call(chatBaseComp);
						if(jsonMsg.startsWith("{\"translate\":\"death.")){ // TODO: Possibly make this less hacky?
//							pl.getLogger().info(jsonMsg);
							Matcher matcher = uuidPattern.matcher(jsonMsg);
							if(!matcher.find()) pl.getLogger().severe("Unable to parse death message: "+jsonMsg);
							final UUID uuid = UUID.fromString(matcher.group());
							if(!unblockedDeathMsgs.remove(uuid)){ // If this death msg is blocked
								new BukkitRunnable(){@Override public void run(){ // server.getEntity(uuid) needs to be called synchronously
									if(unblockedDeathMsgs.contains(uuid)) JunkUtils.sendPacket(player, packet); // If this death msg has been unblocked
									else{
										final Entity entity = pl.getServer().getEntity(uuid);
										if(entity instanceof Player ? REPLACE_PLAYER_DEATH_MSG : REPLACE_PET_DEATH_MSG){
//											pl.getLogger().info("blocked entity death msgs for: "+entity.getType());
										}
										else{
											unblockedDeathMsgs.add(uuid);
											JunkUtils.sendPacket(player, packet);
										}
									}
								}}.runTaskLater(pl, 1);//.runTask(pl);
								return;
							}
						}
					}
				}
				super.write(context, packet, promise);
			}
		});
	}
	private void removePlayer(Player player){
		Channel channel = JunkUtils.getPlayerChannel(player);
		channel.eventLoop().submit(()->{
			channel.pipeline().remove(player.getName());
			return null;
		});
	}

	public DeathMessagePacketIntercepter(boolean replacePlayerDeathMsg, boolean replacePetDeathMsg){
		pl = DropHeads.getPlugin();
		REPLACE_PLAYER_DEATH_MSG = replacePlayerDeathMsg;
		REPLACE_PET_DEATH_MSG = replacePetDeathMsg;
		unblockedDeathMsgs = new HashSet<>();

		pl.getServer().getPluginManager().registerEvents(new Listener(){
			@EventHandler public void onJoin(PlayerJoinEvent evt){injectPlayer(evt.getPlayer());}
			@EventHandler public void onQuit(PlayerQuitEvent evt){removePlayer(evt.getPlayer());}
		}, pl);
	}

	public void unblockDeathMessage(Entity entity){
		if(entity instanceof Player || (entity instanceof Tameable && ((Tameable)entity).getOwner() != null
				&& pl.getServer().getEntity(((Tameable)entity).getOwner().getUniqueId()) != null)){
//			pl.getLogger().info("unblocked death msg");
			unblockedDeathMsgs.add(entity.getUniqueId());

			// If death messages are disabled (e.g., by some other plugin), we should clean up this set periodically
			if(unblockedDeathMsgs.size() > 100){
				//TODO: a more correct solution
				new BukkitRunnable(){@Override public void run(){unblockedDeathMsgs.clear();}}.runTaskLater(pl, 2);
			}
		}
	}

	public void unregisterAll(){
		for(Player player : pl.getServer().getOnlinePlayers()) removePlayer(player);
	}
}
