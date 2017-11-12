package hu.trigary.hiveheatersensors.sensorhub.entities;

import java.util.Objects;

public class LogEntry {
	public LogEntry(Float temperature) {
		this.temperature = temperature;
		update();
	}
	
	private final Float temperature;
	private volatile long timestamp;
	
	
	
	public long getTimestamp() {
		return timestamp;
	}
	
	public Float getTemperature() {
		return temperature;
	}
	
	
	public boolean contentEquals(LogEntry newEntry) {
		return Objects.equals(temperature, newEntry.temperature);
	}
	
	public void update() {
		timestamp = (System.currentTimeMillis() / 1000) * 1000;
	}
	
	
	
	public String serialize() {
		return timestamp + " " + temperature;
	}
	
	public static LogEntry deserialize(String string) {
		String[] split = string.split(" ");
		return new LogEntry(split[1].equals("null") ? null : Float.valueOf(split[1]), Long.valueOf(split[0]));
	}
	
	private LogEntry(Float temperature, long timestamp) {
		this.temperature = temperature;
		this.timestamp = timestamp;
	}
}
