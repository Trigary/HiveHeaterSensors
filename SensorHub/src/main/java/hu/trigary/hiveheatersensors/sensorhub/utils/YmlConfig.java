package hu.trigary.hiveheatersensors.sensorhub.utils;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class YmlConfig {
	private static final SimpleLogger LOGGER = new SimpleLogger(YmlConfig.class.getSimpleName());
	private Map<String, Object> contents;
	
	public YmlConfig(String fileName) {
		Yaml yaml = new Yaml();
		File file = new File(fileName + ".yml");
		
		if (!file.exists()) {
			InputStream inputStream = YmlConfig.class.getResourceAsStream("/" + fileName + ".yml");
			if (inputStream == null) {
				LOGGER.debug("A default resource wasn't provided for the config file: " + fileName);
			} else {
				try {
					Files.copy(inputStream, file.toPath());
					inputStream.close();
				} catch (IOException e) {
					LOGGER.error("Error while copying resource stream of: " + fileName, e);
				}
			}
		}
		
		try (InputStream inputStream = new FileInputStream(file)) {
			contents = (Map<String, Object>)yaml.load(inputStream);
		} catch (IOException e) {
			LOGGER.error("Error while loading the config: " + fileName, e);
		}
		
		if (contents == null) {
			contents = new HashMap<>();
		}
	}
	
	
	
	public Object get(String key) {
		return contents.get(key);
	}
	
	public boolean getBoolean(String key) {
		return (boolean)get(key);
	}
	
	public int getInteger(String key) {
		return ((Number)get(key)).intValue();
	}
	
	public float getFloat(String key) {
		return ((Number)get(key)).floatValue();
	}
	
	public String getString(String key) {
		return (String)get(key);
	}
	
	public Map<String, String> getStringStringMap(String key) {
		return (Map<String, String>)get(key);
	}
}
