package EvLibD;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;

public class Extras {
	public static String executePost(String post){
		URLConnection connection = null;
		try{
			connection = new URL(post).openConnection();
			connection.setUseCaches(false);
			connection.setDoOutput(true);

			// Get response
//			Scanner s = new Scanner(connection.getInputStream()).useDelimiter("\\A");
//			String response = s.hasNext() ? s.next() : null;
//			s.close();
//			return response;
			BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			String line = rd.readLine();
			rd.close();
			return line;
		}
		catch(IOException e){
			System.out.println(e.getStackTrace());
			return null;
		}
	}
	
	static HashMap<String, Boolean> exists = new HashMap<String, Boolean>();
	public static boolean checkExists(String player){
		if(!exists.containsKey(player)){
			//Sample data (braces included): {"id":"34471e8dd0c547b9b8e1b5b9472affa4","name":"EvDoc"}
			String data = executePost("https://api.mojang.com/users/profiles/minecraft/"+player);
			exists.put(player, data != null);
		}
		return exists.get(player);
	}
}