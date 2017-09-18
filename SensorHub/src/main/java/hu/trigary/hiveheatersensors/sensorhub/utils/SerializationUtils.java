package hu.trigary.hiveheatersensors.sensorhub.utils;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;

public class SerializationUtils {
	private static final SimpleLogger LOGGER = new SimpleLogger(SerializationUtils.class.getName());
	
	public static <T> T loadJson(String fileName, Type type) {
		File file = new File(fileName + ".json");
		if (file.exists() && file.length() > 0) {
			try {
				FileReader reader = new FileReader(file);
				T output = new Gson().fromJson(reader, type);
				reader.close();
				return output;
			} catch (IOException e) {
				LOGGER.error("Failed to load JSON file %s: ", e, fileName);
			}
		}
		return null;
	}
	
	public static void saveJson(String fileName, Object serializable) {
		File file = new File(fileName + ".json");
		if (!file.exists()) {
			File folder = file.getParentFile();
			if (folder != null) {
				if (!file.getParentFile().mkdirs()) {
					LOGGER.error("Unable to create the parent directory of the file: " + fileName);
				}
			}
		}
		
		try {
			FileWriter writer = new FileWriter(file);
			writer.write(new Gson().toJson(serializable));
			writer.flush();
			writer.close();
		} catch (IOException e) {
			LOGGER.error("Failed to save JSON file %s: ", e, fileName);
		}
	}
}
