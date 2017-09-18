package hu.trigary.hiveheatersensors.sensorhub.entities;

public class GeneralSensorConfig {
	public GeneralSensorConfig(int connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
	}
	
	private final int connectionTimeout;
	
	
	
	public int getConnectionTimeout() {
		return connectionTimeout;
	}
}
