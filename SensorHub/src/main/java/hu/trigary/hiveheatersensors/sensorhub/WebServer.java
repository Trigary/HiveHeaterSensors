package hu.trigary.hiveheatersensors.sensorhub;

import com.google.gson.Gson;
import hu.trigary.hiveheatersensors.sensorhub.sensor.Sensor;
import hu.trigary.hiveheatersensors.sensorhub.utils.PromUtils;
import hu.trigary.hiveheatersensors.sensorhub.utils.SimpleLogger;
import hu.trigary.hiveheatersensors.sensorhub.utils.WebUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class WebServer {
	private static final SimpleLogger LOGGER = new SimpleLogger(WebServer.class.getSimpleName());
	private static final DateFormat INPUT_DATETIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
	
	public static final String MSG_INVALID_HIVE = "The specified hive doesn't exist.";
	public static final String MSG_INVALID_PARAMS = "Invalid parameter(s).";
	public static final String MSG_SENSOR_FAIL = "Failure, the sensor is probably unavailable.";
	public static final String MSG_SUCCESS_REDIRECT = "Success, redirecting...";
	public static final String TEXT_SENSOR_UNREACHABLE = "Sensor unreachable";
	
	public WebServer(SensorHub sensorHub) {
		this.sensorHub = sensorHub;
		utils = new WebUtils(sensorHub);
	}
	
	private final SensorHub sensorHub;
	private final WebUtils utils;
	
	
	
	public void start() {
		Velocity.setProperty("resource.loader", "class");
		Velocity.setProperty("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
		Velocity.init();
		
		Spark.port(80);
		Spark.staticFileLocation("/web/static");
		Spark.staticFiles.expireTime(900);
		
		Gson gson = new Gson();
		Spark.exception(Exception.class, (e, request, response) -> LOGGER.error("Exception while processing %s request from %s to %s with query: %s", e,
				request.requestMethod(), request.ip(), request.url(), gson.toJson(request.queryMap().toMap())));
		Spark.before("*", (request, response) -> LOGGER.debug("New %s request from %s to %s with query: %s",
				request.requestMethod(), request.ip(), request.url(), gson.toJson(request.queryMap().toMap())));
		
		Spark.get("/", this::handleRootGet);
		Spark.get("/hives/:hive", this::handleHiveGet);
		Spark.get("/logs", this::handleLogsGet);
		Spark.get("/config", this::handleConfigGet);
		
		Spark.post("/hives/:hive/config", this::handleHiveConfigSet);
		Spark.post("/hives/:hive/prom", this::handleHivePromSet);
		Spark.post("/hives/:hive/scheduled", this::handleHiveScheduledAdd);
		Spark.post("/hives/:hive/scheduled/:timestamp", this::handleHiveScheduledDelete);
	}
	
	public void stop() {
		Spark.stop();
	}
	
	
	
	private String handleRootGet(Request request, Response response) {
		VelocityContext context = new VelocityContext();
		context.put("sensors", sensorHub.getSensors());
		context.put("scheduledConfigs", utils.getScheduledConfigMap());
		context.put("logMap", utils.getLogMap());
		context.put("sensorUnreachable", TEXT_SENSOR_UNREACHABLE);
		return utils.renderPage("root", context);
	}
	
	private String handleHiveGet(Request request, Response response) {
		Sensor sensor = utils.processHive(request, response);
		if (sensor == null) {
			return MSG_INVALID_HIVE;
		}
		
		Integer prom = sensor.getProm();
		
		VelocityContext context = new VelocityContext();
		context.put("sensorUnreachable", TEXT_SENSOR_UNREACHABLE);
		context.put("minTemperature", Sensor.MIN_TARGET_TEMPERATURE);
		context.put("maxTemperature", Sensor.MAX_TARGET_TEMPERATURE);
		
		context.put("sensor", sensor);
		context.put("logEntries", sensorHub.getDataLogger().getLogEntries(sensor));
		context.put("formattedProm", PromUtils.getFormatted(prom));
		context.put("scheduledConfigs", sensorHub.getScheduledConfigExecutor().getConfigs(sensor));
		
		context.put("currentConfig", utils.renderSensorConfig(sensor, "config", "Current Configuration", sensor.getConfig().isHeatingEnabled(), sensor.getConfig().getTargetTemperature(), 0.25f, "Set", ""));
		context.put("promConfig", utils.renderSensorConfig(sensor, "prom", "Default Configuration", PromUtils.isHeatingEnabled(prom), PromUtils.getTargetTemperature(prom), 1, "Set", ""));
		
		VelocityContext scheduledTimeInputContext = new VelocityContext();
		scheduledTimeInputContext.put("type", "scheduled");
		scheduledTimeInputContext.put("currentDatetimeLocal", INPUT_DATETIME_FORMAT.format(new Date(System.currentTimeMillis() + 60000)));
		context.put("scheduledForm", utils.renderSensorConfig(sensor, "scheduled", "Scheduled Configuration", sensor.getConfig().isHeatingEnabled(), sensor.getConfig().getTargetTemperature(), 0.25f,
				"Create", utils.renderFile("scheduledTimeInput", scheduledTimeInputContext)));
		
		return utils.renderPage("hive", context);
	}
	
	private String handleLogsGet(Request request, Response response) {
		File file = new File("logs.txt");
		String lastLines = utils.readLastLines(file, 50);
		if (lastLines == null) {
			response.status(500);
			return "Couldn't open the log file.";
		}
		
		VelocityContext context = new VelocityContext();
		long kilobytes = file.length() / 1024;
		context.put("fileSize", kilobytes < 1024 ? kilobytes + " KB" : (kilobytes / 1024) + " MB");
		context.put("rawLogsFile", lastLines);
		return utils.renderPage("logs", context);
	}
	
	private String handleConfigGet(Request request, Response response) {
		try {
			VelocityContext context = new VelocityContext();
			context.put("rawConfigFile", StringEscapeUtils.escapeHtml(IOUtils.toString(new InputStreamReader(new FileInputStream("config.yml")))));
			return utils.renderPage("config", context);
		} catch (IOException e) {
			LOGGER.error("Couldn't open the config file: ", e);
			response.status(500);
			return "Couldn't open the config file.";
		}
	}
	
	
	
	private String handleHiveConfigSet(Request request, Response response) {
		Sensor sensor = utils.processHive(request, response);
		if (sensor == null) {
			return MSG_INVALID_HIVE;
		}
		
		Boolean heatingEnabled = utils.processHeatingEnabled(request, response);
		Float targetTemperature = utils.processTargetTemperature(request, response);
		if (heatingEnabled == null || targetTemperature == null) {
			return MSG_INVALID_PARAMS;
		}
		
		if (sensor.setConfig(heatingEnabled, targetTemperature)) {
			response.redirect("/hives/" + sensor.getIdentifier());
			return MSG_SUCCESS_REDIRECT;
		} else {
			response.status(500);
			return MSG_SENSOR_FAIL;
		}
	}
	
	private String handleHivePromSet(Request request, Response response) {
		Sensor sensor = utils.processHive(request, response);
		if (sensor == null) {
			return MSG_INVALID_HIVE;
		}
		
		Boolean heatingEnabled = utils.processHeatingEnabled(request, response);
		Float targetTemperature = utils.processTargetTemperature(request, response);
		if (heatingEnabled == null || targetTemperature == null) {
			return MSG_INVALID_PARAMS;
		}
		
		if (sensor.setProm(PromUtils.getValue(heatingEnabled, targetTemperature))) {
			response.redirect("/hives/" + sensor.getIdentifier());
			return MSG_SUCCESS_REDIRECT;
		} else {
			response.status(500);
			return MSG_SENSOR_FAIL;
		}
	}
	
	private String handleHiveScheduledAdd(Request request, Response response) {
		Sensor sensor = utils.processHive(request, response);
		if (sensor == null) {
			return MSG_INVALID_HIVE;
		}
		
		long timestamp;
		try {
			timestamp = INPUT_DATETIME_FORMAT.parse(request.queryParams("timestamp")).getTime();
		} catch (ParseException | NullPointerException e) {
			response.status(400);
			return MSG_INVALID_PARAMS;
		}
		
		Boolean heatingEnabled = utils.processHeatingEnabled(request, response);
		Float targetTemperature = utils.processTargetTemperature(request, response);
		if (heatingEnabled == null || targetTemperature == null) {
			return MSG_INVALID_PARAMS;
		}
		
		if (sensorHub.getScheduledConfigExecutor().createConfig(sensor, timestamp, heatingEnabled, targetTemperature)) {
			response.redirect("/hives/" + sensor.getIdentifier());
			return MSG_SUCCESS_REDIRECT;
		} else {
			response.status(400);
			return "A config is already scheduled at the specified timestamp.";
		}
	}
	
	private String handleHiveScheduledDelete(Request request, Response response) {
		Sensor sensor = utils.processHive(request, response);
		if (sensor == null) {
			return MSG_INVALID_HIVE;
		}
		
		long scheduled;
		try {
			scheduled = Long.valueOf(request.params("timestamp"));
		} catch (NumberFormatException | NullPointerException e) {
			response.status(400);
			return MSG_INVALID_PARAMS;
		}
		
		if (sensorHub.getScheduledConfigExecutor().cancelConfig(sensor, scheduled)) {
			response.redirect("/hives/" + sensor.getIdentifier());
			return MSG_SUCCESS_REDIRECT;
		} else {
			response.status(404);
			return "The specified scheduled config doesn't exist.";
		}
	}
}
