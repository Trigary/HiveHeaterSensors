package hu.trigary.hiveheatersensors.sensorhub;

import hu.trigary.hiveheatersensors.sensorhub.entities.GeneralSensorConfig;
import hu.trigary.hiveheatersensors.sensorhub.entities.SensorConfig;
import hu.trigary.hiveheatersensors.sensorhub.sensor.Sensor;
import hu.trigary.hiveheatersensors.sensorhub.utils.NetworkUtils;
import hu.trigary.hiveheatersensors.sensorhub.utils.SimpleLogger;
import hu.trigary.hiveheatersensors.sensorhub.utils.YmlConfig;

import java.util.*;
import java.util.stream.Collectors;

public class SensorHub {
	private static SimpleLogger LOGGER;
	
	public static void main(String[] args) throws InterruptedException {
		Set<String> arguments = Arrays.stream(args).map(String::toLowerCase).collect(Collectors.toSet());
		LOGGER = SimpleLogger.initializeAndGet(arguments.contains("debug"), SensorHub.class.getSimpleName());
		
		SensorHub sensorHub = new SensorHub();
		sensorHub.start();
		
		Scanner inputScanner = new Scanner(System.in);
		while (true) {
			String line = inputScanner.nextLine();
			if (line.equals("stop")) {
				break;
			} else {
				System.out.println("Invalid command.");
			}
		}
		
		sensorHub.stop();
	}
	
	public SensorHub() {
		LOGGER.info("Initializing...");
		
		YmlConfig config = new YmlConfig("config");
		NetworkUtils.setLinuxWifiInterfaceName(config.getString("linux-wifi-interface-name"));
		
		GeneralSensorConfig generalSensorConfig = new GeneralSensorConfig(config.getInteger("connectionTimeout") * 1000);
		boolean defaultHeatingEnabled = config.getBoolean("defaultHeatingEnabled");
		float defaultTargetTemperature = config.getFloat("defaultTargetTemperature");
		
		Map<String, String> identifierMacPairs = config.getStringStringMap("hives")
				.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey))
				.collect(Collectors.toMap(Map.Entry::getKey, (v) -> NetworkUtils.formatMac(v.getValue()), (e1, e2) -> e1, LinkedHashMap::new));
		
		for (Map.Entry<String, String> entry : identifierMacPairs.entrySet()) {
			SensorConfig sensorConfig = SensorConfig.loadOrDefault(entry.getKey(), defaultHeatingEnabled, defaultTargetTemperature);
			sensors.put(entry.getKey(), Sensor.create(entry.getKey(), generalSensorConfig, sensorConfig, entry.getValue()));
		}
		
		scheduledConfigExecutor = new ScheduledConfigExecutor(this);
		dataLogger = new DataLogger(this,
				config.getInteger("minSecondsBetweenSamplings") * 1000,
				config.getInteger("fallBehindThreshold"),
				config.getInteger("saveIfHasSeconds") * 1000,
				config.getInteger("saveEveryXCycles")
		);
		webServer = new WebServer(this);
	}
	
	private final Map<String, Sensor> sensors = new LinkedHashMap<>();
	private final ScheduledConfigExecutor scheduledConfigExecutor;
	private final DataLogger dataLogger;
	private final WebServer webServer;
	
	
	
	public void start() {
		LOGGER.info("Starting...");
		scheduledConfigExecutor.start();
		dataLogger.start();
		webServer.start();
	}
	
	public void stop() throws InterruptedException {
		LOGGER.info("Stopping...");
		scheduledConfigExecutor.stop();
		dataLogger.stop();
		webServer.stop();
	}
	
	
	
	public Sensor getSensor(String identifier) {
		return sensors.get(identifier);
	}
	
	public Collection<Sensor> getSensors() {
		return sensors.values();
	}
	
	public Set<String> getSensorIdentifiers() {
		return sensors.keySet();
	}
	
	
	
	public ScheduledConfigExecutor getScheduledConfigExecutor() {
		return scheduledConfigExecutor;
	}
	
	public DataLogger getDataLogger() {
		return dataLogger;
	}
}
