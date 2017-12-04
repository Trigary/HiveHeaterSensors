package hu.trigary.hiveheatersensors.sensorhub.sensor;

import hu.trigary.hiveheatersensors.sensorhub.entities.GeneralSensorConfig;
import hu.trigary.hiveheatersensors.sensorhub.entities.SensorConfig;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class SensorMock implements Sensor {
	private static final double CONNECTION_CHANCE = 0.5;
	private final String identifier;
	private final SensorConfig config;
	private volatile int prom;
	
	SensorMock(String identifier, GeneralSensorConfig generalConfig, SensorConfig config) {
		this.identifier = identifier;
		this.config = config;
		prom = 255;
	}
	
	
	
	@Override
	public String getIdentifier() {
		return identifier;
	}
	
	@Override
	public SensorConfig getConfig() {
		return config;
	}
	
	
	
	@Override
	public Float getTemperature() {
		if (couldConnect()) {
			Random random = ThreadLocalRandom.current();
			return (random.nextInt(9) * 0.25f) + 28; //Random number between 28 and 30 (both inclusive) with 0.25 precision (example: 29.75)
		} else {
			return null;
		}
	}
	
	@Override
	public boolean setConfig(boolean heatingEnabled, float targetTemperature) {
		if (couldConnect()) {
			config.set(heatingEnabled, targetTemperature);
			return true;
		} else {
			return false;
		}
	}
	
	@Override
	public Integer getProm() {
		if (couldConnect()) {
			return prom;
		} else {
			return null;
		}
	}
	
	@Override
	public boolean setProm(int unsignedByteRanged) {
		if (couldConnect()) {
			prom = unsignedByteRanged;
			return false;
		} else {
			return false;
		}
	}
	
	
	
	private boolean couldConnect() {
		return ThreadLocalRandom.current().nextDouble() < CONNECTION_CHANCE;
	}
}
