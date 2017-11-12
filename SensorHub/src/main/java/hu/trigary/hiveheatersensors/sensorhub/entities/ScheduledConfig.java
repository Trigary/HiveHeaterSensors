package hu.trigary.hiveheatersensors.sensorhub.entities;

import hu.trigary.hiveheatersensors.sensorhub.sensor.Sensor;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ScheduledConfig {
	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("YY/MM/dd HH:mm");
	
	public ScheduledConfig(Sensor sensor, long timestamp, boolean heatingEnabled, float targetTemperature) {
		this.sensor = sensor;
		this.timestamp = timestamp;
		this.heatingEnabled = heatingEnabled;
		this.targetTemperature = targetTemperature;
	}
	
	private final Sensor sensor;
	private volatile long timestamp;
	private final boolean heatingEnabled;
	private final float targetTemperature;
	
	
	
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
	
	
	
	public Sensor getSensor() {
		return sensor;
	}
	
	public long getTimestamp() {
		return timestamp;
	}
	
	public String getFormattedTime() {
		return DATE_FORMAT.format(new Date(timestamp));
	}
	
	public boolean isHeatingEnabled() {
		return heatingEnabled;
	}
	
	public float getTargetTemperature() {
		return targetTemperature;
	}
	
	public String getFormattedValue() {
		return SensorConfig.getFormatted(heatingEnabled, targetTemperature);
	}
	
	
	
	public Map<String, Object> serialize() {
		Map<String, Object> map = new HashMap<>();
		map.put("timestamp", timestamp);
		map.put("heatingEnabled", heatingEnabled);
		map.put("targetTemperature", targetTemperature);
		return map;
	}
	
	public static ScheduledConfig deserialize(Sensor sensor, Map<String, Object> map) {
		return new ScheduledConfig(sensor, ((Number)map.get("timestamp")).longValue(), (boolean)map.get("heatingEnabled"), ((Number)map.get("targetTemperature")).floatValue());
	}
	
	
	
	@Override
	public String toString() {
		return sensor.getIdentifier() + "@" + timestamp + "[" + heatingEnabled + "|" + targetTemperature + "]";
	}
}
