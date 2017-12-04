package hu.trigary.hiveheatersensors.sensorhub.sensor;

import hu.trigary.hiveheatersensors.sensorhub.entities.GeneralSensorConfig;
import hu.trigary.hiveheatersensors.sensorhub.entities.SensorConfig;

public class SensorFactory {
	private final boolean mockingMode;
	private final GeneralSensorConfig generalConfig;
	
	public SensorFactory(boolean mockingMode, GeneralSensorConfig generalConfig) {
		this.mockingMode = mockingMode;
		this.generalConfig = generalConfig;
	}
	
	
	
	public Sensor create(String identifier, SensorConfig config, String macAddress) {
		if (mockingMode) {
			return new SensorMock(identifier, generalConfig, config);
		} else {
			return new SensorImpl(identifier, generalConfig, config, macAddress);
		}
	}
}
