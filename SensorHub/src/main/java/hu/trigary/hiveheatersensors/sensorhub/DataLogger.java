package hu.trigary.hiveheatersensors.sensorhub;

import com.google.gson.reflect.TypeToken;
import hu.trigary.hiveheatersensors.sensorhub.entities.LogEntry;
import hu.trigary.hiveheatersensors.sensorhub.sensor.Sensor;
import hu.trigary.hiveheatersensors.sensorhub.utils.SerializationUtils;
import hu.trigary.hiveheatersensors.sensorhub.utils.SimpleLogger;

import java.util.*;
import java.util.stream.Collectors;

public class DataLogger {
	private static final SimpleLogger LOGGER = new SimpleLogger(DataLogger.class.getSimpleName());
	private final SensorHub sensorHub;
	private final int minWait;
	private final int fallBehindThreshold;
	private final int saveTime;
	private final int saveCycles;
	private final Map<String, List<LogEntry>> logEntries = new HashMap<>();
	private final Thread thread = new Thread(this::loop);
	
	public DataLogger(SensorHub sensorHub, int minWait, int fallBehindThreshold, int saveTime, int saveCycles) {
		this.sensorHub = sensorHub;
		this.minWait = minWait;
		this.fallBehindThreshold = fallBehindThreshold;
		this.saveTime = saveTime;
		this.saveCycles = saveCycles;
		
		for (String identifier : sensorHub.getSensorIdentifiers()) {
			logEntries.put(identifier, new LinkedList<>());
		}
		
		Map<String, List<String>> serialized = SerializationUtils.loadJson("temperature-logs", new TypeToken<Map<String, List<String>>>() {}.getType());
		if (serialized != null) {
			int amount = 0;
			for (Map.Entry<String, List<String>> entry : serialized.entrySet()) {
				List<LogEntry> list = logEntries.get(entry.getKey());
				if (list == null) {
					LOGGER.info("Ignoring the logs from a no longer valid hive: " + entry.getKey());
					continue;
				}
				
				for (String string : entry.getValue()) {
					list.add(LogEntry.deserialize(string));
				}
				amount += list.size();
			}
			
			LOGGER.info("Loaded %s temperature logs (%s entry/hive on average)", amount, Math.round((((float)amount) / logEntries.size()) * 10) / 10);
		} else {
			LOGGER.info("No temperature logs were found");
		}
	}
	
	
	
	public void start() {
		thread.start();
	}
	
	public void stop() throws InterruptedException {
		thread.interrupt();
		thread.join();
	}
	
	public LogEntry[] getLogEntries(Sensor sensor) {
		synchronized (logEntries) {
			return logEntries.get(sensor.getIdentifier()).toArray(new LogEntry[0]);
		}
	}
	
	
	
	private void loop() {
		LOGGER.info("Starting samplings");
		int lastSave = 0;
		
		while (true) {
			long lastTimestamp = System.currentTimeMillis();
			for (Sensor sensor : sensorHub.getSensors()) {
				tryLog(sensor);
			}
			
			long toSleep;
			lastSave++;
			if (lastSave >= saveCycles) {
				LOGGER.debug("Saving due to enough passed cycles");
				save();
				lastSave = 0;
				toSleep = lastTimestamp + minWait - System.currentTimeMillis();
			} else {
				toSleep = lastTimestamp + minWait - System.currentTimeMillis();
				if (toSleep > saveTime) {
					LOGGER.debug("%s seconds remaining until next cycle, saving data", toSleep / 1000);
					save();
					lastSave = 0;
					toSleep = lastTimestamp + minWait - System.currentTimeMillis();
				}
			}
			
			if (toSleep > 0) {
				try {
					LOGGER.debug("Waiting %s seconds for next sampling cycles", toSleep / 1000);
					Thread.sleep(toSleep);
				} catch (InterruptedException e) {
					LOGGER.debug("Interrupted, stopping");
					break;
				}
			} else {
				toSleep = -toSleep / 1000;
				if (toSleep > fallBehindThreshold) {
					LOGGER.info("Fell behind of deadline by %s seconds", toSleep);
				} else {
					LOGGER.debug("Fell behind of deadline by %s seconds", toSleep);
				}
				
				if (Thread.interrupted()) {
					LOGGER.debug("Interrupted, stopping");
					break;
				}
			}
		}
	}
	
	private void tryLog(Sensor sensor) {
		Float temperature = sensor.getTemperature();
		if (temperature == null) {
			LOGGER.debug("Couldn't log from sensor: " + sensor.getIdentifier());
		}
		
		synchronized (logEntries) {
			LogEntry newEntry = new LogEntry(temperature);
			List<LogEntry> list = logEntries.get(sensor.getIdentifier());
			if (list.size() < 2) {
				list.add(newEntry);
				return;
			}
			
			ListIterator<LogEntry> iterator = list.listIterator(list.size());
			LogEntry lastEntry = iterator.previous();
			if (!lastEntry.contentEquals(newEntry)) {
				list.add(newEntry);
				return;
			}
			
			LogEntry beforeLastEntry = iterator.previous();
			if (beforeLastEntry.contentEquals(newEntry)) {
				lastEntry.update();
			} else {
				list.add(newEntry);
			}
		}
	}
	
	private void save() {
		SerializationUtils.saveJson("temperature-logs", logEntries.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, (e) -> e.getValue().stream()
								.map(LogEntry::serialize)
								.collect(Collectors.toList())))
		);
	}
}
