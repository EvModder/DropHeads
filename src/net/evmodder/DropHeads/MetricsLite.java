/*
 * Copyright 2011 Tyler Blair. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 *
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ''AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those of the
 * authors and contributors and should not be interpreted as representing official policies,
 * either expressed or implied, of anybody else.
 */

package net.evmodder.DropHeads;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.zip.GZIPOutputStream;

/**
 * bStats collects some data for plugin authors.
 */
public class MetricsLite{
	public static final int B_STATS_VERSION = 1; // The version of this bStats class
	private static final String URL = "https://bStats.org/submitData/bukkit"; // The url to which the data is sent

	private final boolean enabled; // Is bStats enabled on this server?
	private static boolean logFailedRequests; // Should failed requests be logged?
	private static boolean logSentData; // Should the sent data be logged?
	private static boolean logResponseStatusText; // Should the response text be logged?
	private static String serverUUID; // The uuid of the server
	private final Plugin plugin; // The plugin
	private final int pluginId; // The plugin id

	/**
	 * Class constructor.
	 *
	 * @param plugin The plugin which stats should be submitted.
	 * @param pluginId The id of the plugin. It can be found at <a href="https://bstats.org/what-is-my-plugin-id">What is my plugin id?</a>
	 */
	public MetricsLite(Plugin plugin, int pluginId){
		if(plugin == null) throw new IllegalArgumentException("Plugin cannot be null!");
		this.plugin = plugin;
		this.pluginId = pluginId;

		// Get the config file
		final File bStatsFolder = new File(plugin.getDataFolder().getParentFile(), "bStats");
		if(!bStatsFolder.exists()){enabled=false; return;}
		final File configFile = new File(bStatsFolder, "config.yml");
		if(!configFile.exists()){enabled=false; return;}
		final YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

		// Check if the config file exists
		enabled = config.getBoolean("enabled", false);
		if(!enabled || !config.isSet("serverUuid")) return;

		// Load the data
		serverUUID = config.getString("serverUuid");
		logFailedRequests = config.getBoolean("logFailedRequests", false);
		logSentData = config.getBoolean("logSentData", false);
		logResponseStatusText = config.getBoolean("logResponseStatusText", false);
		boolean found = false;
		// Search for all other bStats Metrics classes to see if we are the first one
		for(Class<?> service : Bukkit.getServicesManager().getKnownServices()){
			try{
				service.getField("B_STATS_VERSION"); // Our identifier :)
				found = true; // We aren't the first
				break;
			}
			catch(NoSuchFieldException ignored){}
		}
		// Register our service
		Bukkit.getServicesManager().register(MetricsLite.class, this, plugin, ServicePriority.Normal);
		if(!found) startSubmitting(); // We are the first!
	}

	/**
	 * Checks if bStats is enabled.
	 * 
	 * @return Whether bStats is enabled or not.
	 */
	public boolean isEnabled(){return enabled;}

	/**
	 * Starts the Scheduler which submits our data every 30 minutes.
	 */
	private void startSubmitting(){
		final Timer timer = new Timer(true); // We use a timer since BukkitScheduler is affected by server lag
		timer.scheduleAtFixedRate(new TimerTask(){
			@Override public void run(){
				if(!plugin.isEnabled()){ // Plugin was disabled
					timer.cancel();
					return;
				}
				// Nevertheless we want our code to run in the Bukkit main thread, so we have to use BukkitScheduler
				// Don't be afraid! The connection to the bStats server is still sync, only the stats collection is sync ;)
				Bukkit.getScheduler().runTask(plugin, () -> submitData());
			}
		}, 1000 * 60 * 5, 1000 * 60 * 30);
		// Submit the data every 30 minutes, first time after 5 minutes to give other plugins enough time to start
		// WARNING: Changing the frequency has no effect but your plugin WILL be blocked/deleted!
		// WARNING: Just don't do it!
	}

	/**
	 * Gets the plugin specific data.
	 * This method is called using Reflection.
	 *
	 * @return The plugin specific data.
	 */
	public JsonObject getPluginData(){
		final JsonObject data = new JsonObject();
		data.addProperty("pluginName", plugin.getDescription().getName()); // Append the name of the plugin
		data.addProperty("id", pluginId); // Append the id of the plugin
		data.addProperty("pluginVersion", plugin.getDescription().getVersion()); // Append the version of the plugin
		data.add("customCharts", new JsonArray());
		return data;
	}

	/**
	 * Gets the server specific data.
	 *
	 * @return The server specific data.
	 */
	private JsonObject getServerData(){
		// Minecraft specific data
		int playerAmount;
		try{
			// Around MC 1.8 the return type was changed to a collection from an array,
			// This fixes java.lang.NoSuchMethodError: org.bukkit.Bukkit.getOnlinePlayers()Ljava/util/Collection;
			final Method onlinePlayersMethod = Class.forName("org.bukkit.Server").getMethod("getOnlinePlayers");
			playerAmount = onlinePlayersMethod.getReturnType().equals(Collection.class)
					? ((Collection<?>)onlinePlayersMethod.invoke(Bukkit.getServer())).size()
					: ((Player[])onlinePlayersMethod.invoke(Bukkit.getServer())).length;
		}
		catch(Exception e){playerAmount = Bukkit.getOnlinePlayers().size();} // Just use the new method if the Reflection failed
		final int onlineMode = Bukkit.getOnlineMode() ? 1 : 0;
		final String bukkitVersion = Bukkit.getVersion();
		final String bukkitName = Bukkit.getName();

		// OS/Java specific data
		final String javaVersion = System.getProperty("java.version");
		final String osName = System.getProperty("os.name");
		final String osArch = System.getProperty("os.arch");
		final String osVersion = System.getProperty("os.version");
		final int coreCount = Runtime.getRuntime().availableProcessors();

		final JsonObject data = new JsonObject();

		data.addProperty("serverUUID", serverUUID);

		data.addProperty("playerAmount", playerAmount);
		data.addProperty("onlineMode", onlineMode);
		data.addProperty("bukkitVersion", bukkitVersion);
		data.addProperty("bukkitName", bukkitName);

		data.addProperty("javaVersion", javaVersion);
		data.addProperty("osName", osName);
		data.addProperty("osArch", osArch);
		data.addProperty("osVersion", osVersion);
		data.addProperty("coreCount", coreCount);

		return data;
	}

	/**
	 * Collects the data and sends it afterwards.
	 */
	private void submitData(){
		final JsonObject data = getServerData();
		final JsonArray pluginData = new JsonArray();
		// Search for all other bStats Metrics classes to get their plugin data
		for(Class<?> service : Bukkit.getServicesManager().getKnownServices()){
			try{
				service.getField("B_STATS_VERSION"); // Our identifier :)

				for(RegisteredServiceProvider<?> provider : Bukkit.getServicesManager().getRegistrations(service)){
					try{
						final Object plugin = provider.getService().getMethod("getPluginData").invoke(provider.getProvider());
						if(plugin instanceof JsonObject){
							pluginData.add((JsonObject)plugin);
						}
						else{ // old bstats version compatibility
							try{
								final Class<?> jsonObjectJsonSimple = Class.forName("org.json.simple.JSONObject");
								if(plugin.getClass().isAssignableFrom(jsonObjectJsonSimple)){
									Method jsonStringGetter = jsonObjectJsonSimple.getDeclaredMethod("toJSONString");
									jsonStringGetter.setAccessible(true);
									String jsonString = (String)jsonStringGetter.invoke(plugin);
									JsonObject object = new JsonParser().parse(jsonString).getAsJsonObject();
									pluginData.add(object);
								}
							}
							catch(ClassNotFoundException e){
								// minecraft version 1.14+
								if(logFailedRequests){
									this.plugin.getLogger().log(Level.SEVERE, "Encountered unexpected exception ", e);
								}
							}
						}
					}catch(NullPointerException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored){}
				}
			}catch(NoSuchFieldException ignored){}
		}

		data.add("plugins", pluginData);

		// Create a new thread for the connection to the bStats server
		new Thread(() -> {
			try{sendData(plugin, data);}
			catch(Exception e){
				if(logFailedRequests){
					plugin.getLogger().log(Level.WARNING, "Could not submit plugin stats of " + plugin.getName(), e);
				}
			}
		}).start();
	}

	/**
	 * Sends the data to the bStats server.
	 *
	 * @param plugin Any plugin. It's just used to get a logger instance.
	 * @param data The data to send.
	 * @throws Exception If the request failed.
	 */
	private static void sendData(Plugin plugin, JsonObject data) throws Exception{
		if(data == null){
			throw new IllegalArgumentException("Data cannot be null!");
		}
		if(Bukkit.isPrimaryThread()){
			throw new IllegalAccessException("This method must not be called from the main thread!");
		}
		if(logSentData){
			plugin.getLogger().info("Sending data to bStats: " + data);
		}
		final HttpsURLConnection connection = (HttpsURLConnection)new URL(URL).openConnection();

		// Compress the data to save bandwidth
		final byte[] compressedData = compress(data.toString());

		// Add headers
		connection.setRequestMethod("POST");
		connection.addRequestProperty("Accept", "application/json");
		connection.addRequestProperty("Connection", "close");
		connection.addRequestProperty("Content-Encoding", "gzip"); // We gzip our request
		connection.addRequestProperty("Content-Length", String.valueOf(compressedData.length));
		connection.setRequestProperty("Content-Type", "application/json"); // We send our data in JSON format
		connection.setRequestProperty("User-Agent", "MC-Server/" + B_STATS_VERSION);

		// Send data
		connection.setDoOutput(true);
		try(DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())){
			outputStream.write(compressedData);
		}

		final StringBuilder builder = new StringBuilder();
		try(BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()))){
			String line;
			while((line = bufferedReader.readLine()) != null){
				builder.append(line);
			}
		}

		if(logResponseStatusText){
			plugin.getLogger().info("Sent data to bStats and received response: " + builder);
		}
	}

	/**
	 * Gzips the given String.
	 *
	 * @param str The string to gzip.
	 * @return The gzipped String.
	 * @throws IOException If the compression failed.
	 */
	private static byte[] compress(final String str) throws IOException{
		if(str == null) return null;

		final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try(GZIPOutputStream gzip = new GZIPOutputStream(outputStream)){
			gzip.write(str.getBytes(StandardCharsets.UTF_8));
		}
		return outputStream.toByteArray();
	}
}