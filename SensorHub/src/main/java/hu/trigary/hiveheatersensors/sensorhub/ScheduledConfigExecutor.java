package hu.trigary.hiveheatersensors.sensorhub;

import com.google.gson.reflect.TypeToken;
import hu.trigary.hiveheatersensors.sensorhub.entities.ScheduledConfig;
import hu.trigary.hiveheatersensors.sensorhub.sensor.Sensor;
import hu.trigary.hiveheatersensors.sensorhub.utils.SerializationUtils;
import hu.trigary.hiveheatersensors.sensorhub.utils.SimpleLogger;
import org.apache.commons.lang3.time.DurationFormatUtils;

import java.util.*;
import java.util.stream.Collectors;

public class ScheduledConfigExecutor {
	private static final SimpleLogger LOGGER = new SimpleLogger(ScheduledConfigExecutor.class.getSimpleName());
	
	public ScheduledConfigExecutor(SensorHub sensorHub) {
		for (String sensor : sensorHub.getSensorIdentifiers()) {
			scheduledConfigs.put(sensor, new TreeMap<>());
		}
		
		Map<String, Map<Long, Map<String, Object>>> serialized = SerializationUtils.loadJson("scheduled-configs", new TypeToken<Map<String, Map<Long, Map<String, Object>>>>() {}.getType());
		if (serialized != null) {
			int amount = 0;
			for (Map.Entry<String, Map<Long, Map<String, Object>>> mapEntry : serialized.entrySet()) {
				NavigableMap<Long, ScheduledConfig> sensorMap = scheduledConfigs.get(mapEntry.getKey());
				if (sensorMap == null) {
					LOGGER.info("Ignoring the scheduled configs from a no longer valid hive: " + mapEntry.getKey());
					continue;
				}
				
				Sensor sensor = sensorHub.getSensor(mapEntry.getKey());
				for (Map.Entry<Long, Map<String, Object>> entry : mapEntry.getValue().entrySet()) {
					sensorMap.put(entry.getKey(), ScheduledConfig.deserialize(sensor, entry.getValue()));
				}
				amount += sensorMap.size();
			}
			
			LOGGER.info("Loaded %s scheduled configs", amount);
		} else {
			LOGGER.info("No saved scheduled configs were found");
		}
	}
	
	private final Map<String, NavigableMap<Long, ScheduledConfig>> scheduledConfigs = new HashMap<>();
	private final Thread thread = new Thread(this::loop);
	
	
	
	public void start() {
		thread.start();
	}
	
	public void stop() throws InterruptedException {
		thread.interrupt();
		thread.join();
	}
	
	
	
	public synchronized boolean createConfig(Sensor sensor, long timestamp, boolean heatingEnabled, float targetTemperature) {
		NavigableMap<Long, ScheduledConfig> sensorMap = scheduledConfigs.get(sensor.getIdentifier());
		if (sensorMap.containsKey(timestamp)) {
			return false;
		} else {
			sensorMap.put(timestamp, new ScheduledConfig(sensor, timestamp, heatingEnabled, targetTemperature));
			notify();
			return true;
		}
	}
	
	public synchronized boolean cancelConfig(Sensor sensor, long timestamp) {
		return scheduledConfigs.get(sensor.getIdentifier()).remove(timestamp) != null;
	}
	
	public synchronized Collection<ScheduledConfig> getConfigs(Sensor sensor) {
		return scheduledConfigs.get(sensor.getIdentifier()).values();
	}
	
	
	
	private synchronized void loop() {
		Set<Long> toRemove = new HashSet<>();
		Set<ScheduledConfig> toAdd = new HashSet<>();
		
		while (true) {
			for (NavigableMap<Long, ScheduledConfig> sensorMap : scheduledConfigs.values()) {
				findAndExecute(sensorMap, toRemove, toAdd);
				
				for (Long key : toRemove) {
					sensorMap.remove(key);
				}
				toRemove.clear();
				
				for (ScheduledConfig config : toAdd) {
					sensorMap.put(config.getTimestamp(), config);
				}
				toAdd.clear();
			}
			
			save();
			
			long nearestConfig = 0;
			for (NavigableMap<Long, ScheduledConfig> sensorMap : scheduledConfigs.values()) {
				if (sensorMap.isEmpty()) {
					continue;
				}
				
				Long key = sensorMap.firstKey();
				if (nearestConfig == 0 || key < nearestConfig) {
					nearestConfig = key;
				}
			}
			
			try {
				if (nearestConfig == 0) {
					LOGGER.debug("No scheduled configs, waiting until one is added");
					wait();
				} else {
					long time = nearestConfig - System.currentTimeMillis();
					if (time > 0) {
						LOGGER.debug("Found scheduled config, waiting for HMS: " + DurationFormatUtils.formatDuration(time, "HH:mm:ss"));
						wait(time);
					} else {
						LOGGER.debug("Found scheduled config; its timestamp has passed");
					}
				}
			} catch (InterruptedException e) {
				LOGGER.debug("Interrupted, stopping");
				break;
			}
		}
	}
	
	private void findAndExecute(NavigableMap<Long, ScheduledConfig> sensorMap, Set<Long> toRemove, Set<ScheduledConfig> toAdd) {
		ScheduledConfig[] configs = sensorMap.values().toArray(new ScheduledConfig[0]);
		for (int i = 0; i < configs.length; i++) {
			ScheduledConfig config = configs[i];
			if (config.getTimestamp() <= System.currentTimeMillis()) {
				toRemove.add(config.getTimestamp());
				
				if (config.getSensor().setConfig(config.isHeatingEnabled(), config.getTargetTemperature())) {
					LOGGER.info("Successfully executed config: " + config.toString());
				} else {
					long newTimestamp = config.getTimestamp() + 60000;
					if (i + 1 < configs.length) {
						ScheduledConfig nextConfig = configs[i + 1];
						if (newTimestamp < nextConfig.getTimestamp()) {
							LOGGER.info("Couldn't execute but could delay (enough gap) config: " + config.toString());
						} else {
							LOGGER.info("Couldn't execute nor delay (no gap) config: " + config.toString());
							continue;
						}
					} else {
						LOGGER.info("Couldn't execute config but could delay (no next) config: " + config.toString());
					}
					
					config.setTimestamp(newTimestamp);
					toAdd.add(config);
				}
			}
		}
	}
	
	
	
	private void save() {
		SerializationUtils.saveJson("scheduled-configs", scheduledConfigs.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, (e) -> e.getValue().entrySet().stream()
						.collect(Collectors.toMap(Map.Entry::getKey, (entry) -> entry.getValue().serialize())))
		));
	}
}
