package net.evmodder.EvLib2;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.commons.lang.StringUtils;
import org.bukkit.entity.EntityType;
import com.sun.org.apache.xml.internal.security.exceptions.Base64DecodingException;
import com.sun.org.apache.xml.internal.security.utils.Base64;

public class Extras {
	private final static String authserver = "https://authserver.mojang.com";

	public static String putReadURL(String payload, String url){
		try{
			HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
			conn.setDoInput(true);
			conn.setDoOutput(true);
			conn.setRequestMethod("PUT");
			OutputStream out = conn.getOutputStream();
			out.write(payload.getBytes("UTF-8")); out.close();

			BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			StringBuilder resp = new StringBuilder(in.readLine());
			String line = null;
			while ((line=in.readLine()) != null) resp.append('\n').append(line);
			in.close();
	
			return resp.toString();
		}
		catch(IOException e){e.printStackTrace(); return null;}
	}

	public static String postReadURL(String payload, String url){
		try{
			HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setDoInput(true);
			conn.setDoOutput(true);
	
			OutputStream out = conn.getOutputStream();
			out.write(payload.getBytes("UTF-8")); out.close();

			BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			StringBuilder resp = new StringBuilder(in.readLine());
			String line = null;
			while ((line=in.readLine()) != null) resp.append('\n').append(line);
			in.close();

			return resp.toString();
		}
		catch(IOException e){e.printStackTrace(); return null;}
	}

	public static String getReadURL(String url){
		try{
			HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
			//conn.setRequestMethod("GET");
			conn.setDoInput(true);
			BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			StringBuilder resp = new StringBuilder();
			String line = null;
			while ((line=in.readLine()) != null) resp.append('\n').append(line);
			in.close();

			return resp.length() > 0 ? resp.substring(1) : null;
		}
		catch(IOException e){e.printStackTrace(); return null;}
	}

	private static String getStringBetween(String base, String begin, String end) {
		int resbeg = 0, resend = base.length()-1;

		Pattern patbeg = Pattern.compile(Pattern.quote(begin));
		Matcher matbeg = patbeg.matcher(base);
		if(matbeg.find()) resbeg = matbeg.end();

		Pattern patend = Pattern.compile(Pattern.quote(end));
		Matcher matend = patend.matcher(base);
		if(matend.find()) resend = matend.start();

		return base.substring(resbeg, resend);
	}

	public static String authenticate(String username, String password){
		String genClientToken = UUID.randomUUID().toString();

		// Setting up json POST request
		String payload = "{\"agent\": {\"name\": \"Minecraft\",\"version\": 1},\"username\": \"" + username
				+ "\",\"password\": \"" + password + "\",\"clientToken\": \"" + genClientToken + "\"}";

		String output = postReadURL(payload, authserver + "/authenticate");

		// Setting up patterns
		String authBeg = "{\"accessToken\":\"";
		String authEnd = "\",\"clientToken\":\"";

		return getStringBetween(output, authBeg, authEnd);
	}

	//Names are [3,16] characters from [abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_]
	static HashMap<String, Boolean> exists = new HashMap<String, Boolean>();
	public static boolean checkExists(String player){
		Boolean b = exists.get(player);
		if(b == null){
			//Sample data: {"id":"34471e8dd0c547b9b8e1b5b9472affa4","name":"EvDoc"}
			String data = getReadURL("https://api.mojang.com/users/profiles/minecraft/"+player);
			exists.put(player, b = (data != null));
		}
		return b;
	}

	public static BufferedImage upsideDownHead(BufferedImage img){
		AffineTransform at = new AffineTransform();
		at.concatenate(AffineTransform.getScaleInstance(1, -1));
		at.concatenate(AffineTransform.getTranslateInstance(0, -img.getHeight()));
		BufferedImage newImg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = newImg.createGraphics();
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC));
		g.drawImage(img, 0, 0, null);
		g.drawImage(img.getSubimage(8, 0, 8, 8), 16, 0, null);
		g.drawImage(img.getSubimage(16, 0, 8, 8), 8, 0, null);
		g.drawImage(img.getSubimage(40, 0, 8, 8), 48, 0, null);
		g.drawImage(img.getSubimage(48, 0, 8, 8), 40, 0, null);
		g.transform(at);
		g.drawImage(img.getSubimage(0, 8, 64, 8), 0, img.getHeight()-16, null);
		g.dispose();
		return newImg;
	}

	public static void makeUpsideDownCopies(String[] heads, Map<String, String> newHeads,
			String outfolder, String uuid, String token){
		for(String line : heads){
			int idx = line.indexOf(':');
			if(idx > -1 && idx < line.length()-1){
				String name = line.substring(0, idx).trim();
				String val = line.substring(idx+1).trim();
				if(name.endsWith("GRUMM") || val.isEmpty()) continue;
				String json = "no workee";
				try{json = new String(Base64.decode(val));}
				catch(Base64DecodingException e){e.printStackTrace();}
				String url = json.substring(json.indexOf("\"url\":")+7, json.lastIndexOf('"')).trim();
				//String textureId = url.substring(url.lastIndexOf('/')+1);
				System.out.println("1. Got texture url from Base64 val");
				String filename = outfolder+"/"+name+"|GRUMM.png";
				File outfile = new File(filename);
				//===========================================================================================
				try{Thread.sleep(2000);}catch(InterruptedException e1){e1.printStackTrace();}//2s
				HttpURLConnection conn;
				try{
					conn = (HttpURLConnection)new URL(url).openConnection();
					conn.setUseCaches(false);
					conn.setDoOutput(true);
					conn.setDoInput(true);

					BufferedInputStream inImg = new BufferedInputStream(conn.getInputStream());
					BufferedImage image = upsideDownHead(ImageIO.read(inImg));

					ImageIO.write(image, "png", outfile);
					System.out.println("2. Saved upside down: "+url+" ("+name+")");

					//===========================================================================================
					try{Thread.sleep(8000);}catch(InterruptedException e1){e1.printStackTrace();}//8s
					conn = (HttpURLConnection)new URL("https://api.mojang.com/user/profile/" + uuid + "/skin")
							.openConnection();
					conn.setRequestProperty("Authorization", "Bearer "+token);
					conn.setRequestProperty("Content-Length", "6000");
					String boundary = "someArbitraryText";
					conn.setRequestProperty("Content-Type", "multipart/form-data; boundary="+boundary);
					conn.setDoInput(true);
					conn.setDoOutput(true);
					conn.setRequestMethod("PUT");
					OutputStream conn_out = conn.getOutputStream();
					BufferedWriter conn_out_writer = new BufferedWriter(new OutputStreamWriter(conn_out));

					conn_out_writer.write("\r\n--"+boundary+"\r\n");
					conn_out_writer.write("Content-Disposition: form-data; name=\"model\"\r\n\r\nclassic");
					conn_out_writer.write("\r\n--"+boundary+"\r\n");
					conn_out_writer.write("Content-Disposition: form-data; name=\"file\"; filename=\"yeet.png"+"\"\r\n");
					conn_out_writer.write("Content-Type: image/png\r\n\r\n");
					conn_out_writer.flush();

					FileInputStream ifstream = new FileInputStream(outfile);
					int newBytes;
					byte[] buffer = new byte[1024];
					while((newBytes=ifstream.read(buffer)) != -1) conn_out.write(buffer, 0, newBytes);
					ifstream.close();
					conn_out.flush();

					conn_out_writer.write("\r\n--"+boundary+"--\r\n");
					conn_out_writer.flush();
					conn_out_writer.close();
					conn_out.close();

					conn.getInputStream();
					System.out.println("3. Skin uploaded");
					try{Thread.sleep(25000);}catch(InterruptedException e1){e1.printStackTrace();}//25s
					//===========================================================================================
					System.out.println("4. Getting new texture url");
					conn = (HttpURLConnection)new URL(
							"https://sessionserver.mojang.com/session/minecraft/profile/"+uuid).openConnection();
					conn.setDoOutput(true);
					BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
					String resp = in.readLine();
					String textureBeg = "\"textures\",\"value\":\"";
					String textureEnd = "\"}]}";
					String newVal = resp.substring(resp.indexOf(textureBeg)+textureBeg.length());
					newVal = newVal.substring(0, newVal.indexOf(textureEnd));
					//System.out.println("new val: "+newVal);

					String newJson = "no workee";
					try{newJson = new String(Base64.decode(newVal));}
					catch(Base64DecodingException e){e.printStackTrace();}
					//System.out.println("New Json: " + newJson);
					//===========================================================================================

					String textureVal = newJson.substring(newJson.lastIndexOf("texture/")+8);
					textureVal = textureVal.substring(0, textureVal.indexOf('"')).trim();
					String shortJson = "{\"textures\":{\"SKIN\":{\"url\":"
							+ "\"http://textures.minecraft.net/texture/"
							+ textureVal + "\"}}}";
					//System.out.println("Short Json: " + shortJson);
					String newBase64Val = Base64.encode(shortJson.getBytes(), 0);
					System.out.println("5. New Base64 val: " + newBase64Val);
					newHeads.put(name+"|GRUMM", newBase64Val);
					//newHeads.put(name, val);
					try{Thread.sleep(5000);}catch(InterruptedException e1){e1.printStackTrace();}//5s
				}
				catch(IOException e){e.printStackTrace();}
			}
		}
	}

	public static void runGrumm(){
		String token = authenticate("mc.alternatecraft@gmail.com", "getskinz!");
		String uuid = "cf49ab9298164c7fa698f84514dd7f74";
		System.out.println("token = "+token);

		TreeMap<String, String> newHeads = new TreeMap<String, String>();
		ArrayList<String> heads = new ArrayList<String>();
		String[] targetHeads = new String[]{"CREEPER"};
		for(String headData : FileIO.loadFile("head-list.txt", "").split("\n")){
			for(String target : targetHeads) if(headData.startsWith(target) && !headData.contains("GRUMM"))
				heads.add(headData);
		}
		System.out.println(StringUtils.join(heads, "\n"));
		System.out.println("Beginning conversion...");
		System.out.println("Approximate duration in minutes: "+(40*heads.size()/60));// 40s/head
		makeUpsideDownCopies(heads.toArray(new String[heads.size()]), newHeads, "textures", uuid, token);

		System.out.println("Results: ");
		for(String e : newHeads.keySet()){
			System.out.println(e + ": " + newHeads.get(e));
		}
	}

	public static void checkMissing(){
		TreeSet<String> missingTxr = new TreeSet<String>(), extraTxr = new TreeSet<String>();
		TreeSet<String> missingDrpC = new TreeSet<String>(), extraDrpC = new TreeSet<String>();
		for(EntityType type : EntityType.values()){
			if(type.isAlive()){missingTxr.add(type.name()); missingDrpC.add(type.name());}
		}
		for(String headData : FileIO.loadFile("head-textures.txt", "").split("\n")){
			int i = headData.indexOf(':'), j = headData.indexOf('|');
			if(i != -1 && j == -1){
				if(!missingTxr.remove(headData.substring(0, i))) extraTxr.add(headData.substring(0, i));
			}
		}
		System.out.println("Missing textures for: "+missingTxr);
		System.out.println("Extra textures for: "+extraTxr);

		for(String headData : FileIO.loadFile("head-drop-rates.txt", "").split("\n")){
			int i = headData.indexOf(':');
			if(i != -1){
				if(!missingDrpC.remove(headData.substring(0, i))) extraDrpC.add(headData.substring(0, i));
			}
		}
		System.out.println("Missing drop rates for: "+missingDrpC);
		System.out.println("Extra drop rates for: "+extraDrpC);
	}

	public static void main(String... args){
		com.sun.org.apache.xml.internal.security.Init.init();
		FileIO.DIR = "./";
		checkMissing();
		//runGrumm();
	}
}