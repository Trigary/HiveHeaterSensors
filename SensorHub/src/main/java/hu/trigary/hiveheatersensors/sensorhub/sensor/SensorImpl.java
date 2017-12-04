package hu.trigary.hiveheatersensors.sensorhub.sensor;

import hu.trigary.hiveheatersensors.sensorhub.entities.GeneralSensorConfig;
import hu.trigary.hiveheatersensors.sensorhub.entities.SensorConfig;
import hu.trigary.hiveheatersensors.sensorhub.utils.NetworkUtils;
import hu.trigary.hiveheatersensors.sensorhub.utils.SimpleLogger;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;

public class SensorImpl implements Sensor {
	private final SimpleLogger logger;
	private final GeneralSensorConfig generalConfig;
	private final String identifier;
	private final SensorConfig config;
	private final String macAddress; //Also connection lock
	private volatile URL baseUrl = null;
	
	SensorImpl(String identifier, GeneralSensorConfig generalConfig, SensorConfig config, String macAddress) {
		logger = new SimpleLogger(Sensor.class.getSimpleName() + "@" + identifier);
		this.generalConfig = generalConfig;
		this.identifier = identifier;
		this.config = config;
		this.macAddress = macAddress;
		logger.info("Got registered with MAC: " + macAddress);
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
		String content = getConnectionContent("temperature");
		if (content == null) {
			return null;
		}
		
		try {
			return Float.valueOf(content);
		} catch (NumberFormatException e) {
			return null;
		}
	}
	
	@Override
	public boolean setConfig(boolean heatingEnabled, float targetTemperature) {
		if (getConnectionResult("?v=" + SensorConfig.getValue(heatingEnabled, targetTemperature))) {
			config.set(heatingEnabled, targetTemperature);
			logger.info("Set config to heating enabled: %s, target temperature: %s");
			return true;
		} else {
			return false;
		}
	}
	
	@Override
	public Integer getProm() {
		String content = getConnectionContent("prom");
		if (content == null) {
			return null;
		}
		
		try {
			return Integer.valueOf(content);
		} catch (NumberFormatException e) {
			return null;
		}
	}
	
	@Override
	public boolean setProm(int unsignedByteRanged) {
		if (getConnectionResult("prom?v=" + unsignedByteRanged)) {
			logger.info("Set prom to: " + unsignedByteRanged);
			return true;
		} else {
			return false;
		}
	}
	
	
	
	private boolean getConnectionResult(String subUrl) {
		try {
			HttpURLConnection connection;
			synchronized (macAddress) {
				connection = getConnection(subUrl);
				if (connection == null) {
					return false;
				}
				
				if (!tryConnect(connection)) {
					return false;
				}
			}
			
			boolean output = connection.getResponseCode() == HttpURLConnection.HTTP_OK;
			connection.disconnect();
			return output;
		} catch (IOException e) {
			logger.error("Error while getting connection result: ", e);
			return false;
		}
	}
	
	private String getConnectionContent(String subUrl) {
		try {
			HttpURLConnection connection;
			synchronized (macAddress) {
				connection = getConnection(subUrl);
				if (connection == null) {
					return null;
				}
				
				if (!tryConnect(connection)) {
					return null;
				}
			}
			
			String output = connection.getResponseCode() == HttpURLConnection.HTTP_OK ? IOUtils.toString(connection.getInputStream(), "UTF-8") : null;
			connection.disconnect();
			return output;
		} catch (IOException e) {
			logger.error("Error while getting connection result: ", e);
			return null;
		}
	}
	
	
	
	private HttpURLConnection getConnection(String subUrl) {
		try {
			if (baseUrl == null) {
				String ip = NetworkUtils.getMacIpPairs().get(macAddress);
				if (ip == null) {
					return null;
				}
				
				String url = "http://" + ip + "/";
				try {
					baseUrl = new URL(url);
				} catch (MalformedURLException e) {
					baseUrl = null;
					logger.error("Malformed URL from IP: " + url, e);
					return null;
				}
				
				if (getConnectionResult("?v=" + config.getValue())) {
					logger.info("IP successfully set: " + ip);
				} else {
					logger.debug("Successfully found the IP %s but failed to send the config", ip);
					return null;
				}
			}
			
			return (HttpURLConnection)new URL(baseUrl, subUrl).openConnection();
		} catch (IOException e) {
			logger.error("Error while opening connection: ", e);
			return null;
		}
	}
	
	private boolean tryConnect(HttpURLConnection connection) throws IOException {
		connection.setConnectTimeout(generalConfig.getConnectionTimeout());
		try {
			connection.connect();
			return true;
		} catch (SocketTimeoutException e) {
			baseUrl = null;
			logger.info("Couldn't reach the sensor, assuming no longer valid IP");
			return false;
		}
	}
}
