package hu.trigary.hiveheatersensors.sensorhub.sensor;

import hu.trigary.hiveheatersensors.sensorhub.entities.GeneralSensorConfig;
import hu.trigary.hiveheatersensors.sensorhub.entities.SensorConfig;

public interface Sensor {
	int MIN_TARGET_TEMPERATURE = -30;
	int MAX_TARGET_TEMPERATURE = 36;
	
	static Sensor create(String identifier, GeneralSensorConfig generalConfig, SensorConfig config, String macAddress) {
		return new SensorImpl(identifier, generalConfig, config, macAddress);
		//return new SensorMock(identifier, generalConfig, config);
	}
	
	String getIdentifier();
	SensorConfig getConfig();
	
	Float getTemperature();
	boolean setConfig(boolean heatingEnabled, float targetTemperature);
	Integer getProm();
	boolean setProm(int unsignedByteRanged);
}
