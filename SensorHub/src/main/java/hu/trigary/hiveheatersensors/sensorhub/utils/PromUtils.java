package hu.trigary.hiveheatersensors.sensorhub.utils;

import hu.trigary.hiveheatersensors.sensorhub.WebServer;
import hu.trigary.hiveheatersensors.sensorhub.sensor.Sensor;

public class PromUtils {
	public static int getValue(boolean heatingEnabled, float targetTemperature) {
		return heatingEnabled ? ((int)targetTemperature) - Sensor.MIN_TARGET_TEMPERATURE : 255;
	}
	
	public static String getFormatted(Integer value) {
		if (value == null) {
			return WebServer.TEXT_SENSOR_UNREACHABLE;
		}
		
		value += Sensor.MIN_TARGET_TEMPERATURE;
		if (value > Sensor.MAX_TARGET_TEMPERATURE) {
			return "Heating disabled";
		} else {
			return  "Heating enabled, target temperature: " + value;
		}
	}
	
	public static boolean isHeatingEnabled(Integer value) {
		return value != null && value + Sensor.MIN_TARGET_TEMPERATURE <= Sensor.MAX_TARGET_TEMPERATURE;
	}
	
	public static int getTargetTemperature(Integer value) {
		if (value == null) {
			return 0;
		}
		
		value += Sensor.MIN_TARGET_TEMPERATURE;
		if (value > Sensor.MAX_TARGET_TEMPERATURE) {
			return 0;
		} else {
			return value;
		}
	}
}
