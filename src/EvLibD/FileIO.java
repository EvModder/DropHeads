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
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class FileIO{
	static String DIR = "./plugins/EvFolder/";
	@Deprecated public FileIO(String dir, boolean force){
		updateDir(dir, force || !new File(DIR).exists());
	}
	@Deprecated public FileIO(String dir){
		updateDir(dir, !new File(DIR).exists());
	}
	public FileIO(JavaPlugin evPl, boolean force){
		updateDir("./plugins/"+evPl.getName()+"/", force || !new File(DIR).exists());
	}
	public FileIO(JavaPlugin evPl){
		updateDir("./plugins/"+evPl.getName()+"/", !new File(DIR).exists());
	}
	static void updateDir(String dir, boolean replaceOrMerge){
		if(replaceOrMerge) DIR = dir;
		else if(new File(dir).exists()){
			Bukkit.getLogger().warning("Relocating data in "+dir+", this might take a minute..");
			try{
				FileUtils.copyDirectory(new File(dir), new File(DIR));
				FileUtils.deleteDirectory(new File(dir));
			}
			catch(IOException e){e.printStackTrace();}
		}
	}

	public static String loadFile(String filename, InputStream defaultFile){
		BufferedReader reader = null;
		try{reader = new BufferedReader(new FileReader(DIR+filename));}
		catch(FileNotFoundException e){
			if(defaultFile == null) return null;

			//Create Directory
			File dir = new File(DIR);
			if(!dir.exists())dir.mkdir();

			//Create the file
			File conf = new File(DIR+filename);
			StringBuilder builder = new StringBuilder();
			String content = null;
			try{
				conf.createNewFile();
				reader = new BufferedReader(new InputStreamReader(defaultFile));

				String line = reader.readLine();
				builder.append(line);
				while(line != null){
					builder.append('\n').append(line);
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
					line = line.trim().replace("//", "#");
					int cut = line.indexOf('#');
					if(cut == -1) file.append('\n').append(line);
					else if(cut > 0) file.append('\n').append(line.substring(0, cut).trim());
					line = reader.readLine();
				}
				reader.close();
			}catch(IOException e){}
		}
		return file.length() == 0 ? "" : file.substring(1);
	}

	public static String loadFile(String filename, String defaultContent){
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
				String line;
				while((line = reader.readLine()) != null){
					line = line.trim().replace("//", "#");
					int cut = line.indexOf('#');
					if(cut == -1) file.append('\n').append(line);
					else if(cut > 0) file.append('\n').append(line.substring(0, cut).trim());
				}
				reader.close();
			}catch(IOException e){}
		}
		return file.length() == 0 ? "" : file.substring(1);
	}

	public static boolean saveFile(String filename, String content){
		if(content == null || content.isEmpty()) return new File(DIR+filename).delete();
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

				//Read contents of defaultConfig
				BufferedReader reader = new BufferedReader(new InputStreamReader(defaultConfig));
				String line = reader.readLine();
				StringBuilder builder = new StringBuilder(line);
				while((line = reader.readLine()) != null) builder.append('\n').append(line);
				reader.close();

				//Create new config from contents of defaultConfig
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
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(pl.getClass().getResourceAsStream("/"+filename)));

			StringBuilder file = new StringBuilder();
			String line;
			while((line = reader.readLine()) != null){
				line = line.trim().replace("//", "#");
				int cut = line.indexOf('#');
				if(cut == -1) file.append('\n').append(line);
				else if(cut > 0) file.append('\n').append(line.substring(0, cut).trim());
			}
			reader.close();
			return file.substring(1);
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