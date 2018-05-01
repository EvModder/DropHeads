package EvLibD;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class FileIO {
	public static final String DIR = "./plugins/EvFolder/";
	public static String loadFile(String filename, InputStream defaultValue) {
		BufferedReader reader = null;
		try{reader = new BufferedReader(new FileReader(DIR+filename));}
		catch(FileNotFoundException e){
			if(defaultValue == null) return null;
			
			//Create Directory
			File dir = new File(DIR);
			if(!dir.exists())dir.mkdir();
			
			//Create the file
			File conf = new File(DIR+filename);
			StringBuilder builder = new StringBuilder();
			String content = null;
			try{
				conf.createNewFile();
				reader = new BufferedReader(new InputStreamReader(defaultValue));
				
				String line = reader.readLine();
				builder.append(line);
				while(line != null){
					builder.append('\n');
					builder.append(line);
					line = reader.readLine();
				}
				reader.close();
				
				BufferedWriter writer = new BufferedWriter(new FileWriter(conf));
				writer.write(content = builder.toString()); writer.close();
			}
			catch(IOException e1){e1.printStackTrace();}
			return content;
		}
		StringBuilder file = new StringBuilder();
		if(reader != null){
			try{
				String line = reader.readLine();
				
				while(line != null){
					line = line.replace("//", "#").trim();
					if(!line.startsWith("#")){
						file.append(line.split("#")[0].trim());
						file.append('\n');
					}
					line = reader.readLine();
				}
				reader.close();
			}catch(IOException e){}
		}
		if(file.length() > 0) file.substring(0, file.length()-1);
		return file.toString();
	}

	public static String loadFile(String filename, String defaultContent) {
		BufferedReader reader = null;
		try{reader = new BufferedReader(new FileReader(DIR+filename));}
		catch(FileNotFoundException e){
			if(defaultContent == null || defaultContent.isEmpty()) return defaultContent;
			
			//Create Directory
			File dir = new File(DIR);
			if(!dir.exists())dir.mkdir();
			
			//Create the file
			File conf = new File(DIR+filename);
			try{
				conf.createNewFile();
				BufferedWriter writer = new BufferedWriter(new FileWriter(conf));
				writer.write(defaultContent);
				writer.close();
			}
			catch(IOException e1){e1.printStackTrace();}
			return defaultContent;
		}
		StringBuilder file = new StringBuilder();
		if(reader != null){
			try{
				String line = reader.readLine();
				
				while(line != null){
					line = line.replace("//", "#").trim();
					if(!line.startsWith("#")){
						file.append(line.split("#")[0].trim());
						file.append('\n');
					}
					line = reader.readLine();
				}
				reader.close();
			}catch(IOException e){}
		}
		if(file.length() > 0) file.substring(0, file.length()-1);
		return file.toString();
	}

	public static boolean saveFile(String filename, String content) {
		try{
			BufferedWriter writer = new BufferedWriter(new FileWriter(DIR+filename));
			writer.write(content); writer.close();
			return true;
		}
		catch(IOException e){return false;}
	}

	public static YamlConfiguration loadConfig(JavaPlugin pl, String configName, InputStream defaultConfig){
		if(!configName.endsWith(".yml")){
			pl.getLogger().severe("Invalid config file!");
			pl.getLogger().severe("Configuation files must end in .yml");
			return null;
		}
		File file = new File(DIR+configName);
		if(!file.exists() && defaultConfig != null){
			try{
				//Create Directory
				File dir = new File(DIR);
				if(!dir.exists())dir.mkdir();
				
				//Create config file from default
				BufferedReader reader = new BufferedReader(new InputStreamReader(defaultConfig));
				
				String line = reader.readLine();
				StringBuilder builder = new StringBuilder(line);
				
				while((line = reader.readLine()) != null){
					builder.append('\n').append(line);
				}
				reader.close();
				
				BufferedWriter writer = new BufferedWriter(new FileWriter(file));
				writer.write(builder.toString()); writer.close();
			}
			catch(IOException ex){
				pl.getLogger().severe(ex.getStackTrace().toString());
				pl.getLogger().severe("Unable to locate a default config!");
				pl.getLogger().severe("Could not find /config.yml in plugin's .jar");
			}
			pl.getLogger().info("Could not locate configuration file!");
			pl.getLogger().info("Generating a new one with default settings.");
		}
		return YamlConfiguration.loadConfiguration(file);
	}

	public static String loadResource(Object pl, String filename){
		try{
			BufferedReader reader = new BufferedReader(new InputStreamReader(pl.getClass().getResourceAsStream("/"+filename)));
		
			StringBuilder file = new StringBuilder();
			String line = reader.readLine();
			while(line != null){
				line = line.replaceFirst("//", "#").trim();
				if(!line.startsWith("#")) file.append(line.split("#")[0].trim()).append('\n');
				line = reader.readLine();
			}
			reader.close();
			return file.toString();
		}
		catch(IOException ex){ex.printStackTrace();}
		return "";
	}

	public static YamlConfiguration loadYaml(String filename, String defaultContent){
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(DIR+filename));
		if(yaml == null){
			if(defaultContent == null || defaultContent.isEmpty()) return null;
			
			//Create Directory and file
			File dir = new File(DIR);
			if(!dir.exists()) dir.mkdir();
			File file = new File(DIR+filename);
			try{
				file.createNewFile();
				BufferedWriter writer = new BufferedWriter(new FileWriter(file));
				writer.write(defaultContent);
				writer.close();
			}
			catch(IOException e){e.printStackTrace();}
			return YamlConfiguration.loadConfiguration(file);
		}
		return yaml;
	}
	
	public static boolean saveYaml(String filename, YamlConfiguration content){
		try{content.save(DIR+filename);}
		catch(IOException e){return false;}
		return true;
	}
}
