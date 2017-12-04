package hu.trigary.hiveheatersensors.sensorhub.entities;

public class GeneralSensorConfig {
	private final int connectionTimeout;
	
	public GeneralSensorConfig(int connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
	}
	
	
	
	public int getConnectionTimeout() {
		return connectionTimeout;
	}
}
