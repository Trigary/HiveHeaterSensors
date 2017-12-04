package hu.trigary.hiveheatersensors.sensorhub.entities;

import com.google.gson.reflect.TypeToken;
import hu.trigary.hiveheatersensors.sensorhub.sensor.Sensor;
import hu.trigary.hiveheatersensors.sensorhub.utils.SerializationUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class SensorConfig {
	private final String saveFileName;
	private boolean heatingEnabled;
	private float targetTemperature;
	
	public SensorConfig(String identifier, boolean heatingEnabled, float targetTemperature) {
		saveFileName = "sensor-configs" + File.separator + identifier;
		this.heatingEnabled = heatingEnabled;
		this.targetTemperature = targetTemperature;
	}
	
	
	
	public synchronized void set(boolean heatingEnabled, float targetTemperature) {
		this.heatingEnabled = heatingEnabled;
		this.targetTemperature = targetTemperature;
		
		Map<String, Object> map = new HashMap<>();
		map.put("heatingEnabled", heatingEnabled);
		map.put("targetTemperature", targetTemperature);
		SerializationUtils.saveJson(saveFileName, map);
	}
	
	public synchronized boolean isHeatingEnabled() {
		return heatingEnabled;
	}
	
	public synchronized float getTargetTemperature() {
		return targetTemperature;
	}
	
	public synchronized float getValue() {
		return getValue(heatingEnabled, targetTemperature);
	}
	
	public synchronized String getFormatted() {
		return getFormatted(heatingEnabled, targetTemperature);
	}
	
	
	
	public static float getValue(boolean heatingEnabled, float targetTemperature) {
		if (heatingEnabled) {
			return targetTemperature - Sensor.MIN_TARGET_TEMPERATURE;
		} else {
			return 255;
		}
	}
	
	public static String getFormatted(boolean heatingEnabled, float targetTemperature) {
		if (heatingEnabled) {
			return "Heating enabled, target temperature: " + targetTemperature;
		} else {
			return "Heating disabled";
		}
	}
	
	public static SensorConfig loadOrDefault(String identifier, boolean defaultHeatingEnabled, float defaultTargetTemperature) {
		Map<String, Object> map = SerializationUtils.loadJson("sensor-configs" + File.separator + identifier, new TypeToken<Map<String, Object>>() {}.getType());
		if (map != null) {
			return new SensorConfig(identifier, (boolean)map.get("heatingEnabled"), ((Number)map.get("targetTemperature")).floatValue());
		} else {
			return new SensorConfig(identifier, defaultHeatingEnabled, defaultTargetTemperature);
		}
	}
}
