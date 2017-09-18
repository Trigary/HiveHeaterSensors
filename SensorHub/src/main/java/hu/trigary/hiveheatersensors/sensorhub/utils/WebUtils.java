package hu.trigary.hiveheatersensors.sensorhub.utils;

import hu.trigary.hiveheatersensors.sensorhub.SensorHub;
import hu.trigary.hiveheatersensors.sensorhub.entities.LogEntry;
import hu.trigary.hiveheatersensors.sensorhub.entities.ScheduledConfig;
import hu.trigary.hiveheatersensors.sensorhub.sensor.Sensor;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import spark.Request;
import spark.Response;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class WebUtils {
	private final SimpleLogger LOGGER = new SimpleLogger(WebUtils.class.getSimpleName());
	
	public WebUtils(SensorHub sensorHub) {
		this.sensorHub = sensorHub;
		sensorArray = sensorHub.getSensors().toArray(new Sensor[0]);
	}
	
	private final SensorHub sensorHub;
	private final Sensor[] sensorArray;
	
	
	
	
	public String renderPage(String contentName, VelocityContext context) {
		VelocityContext templateContext = new VelocityContext();
		templateContext.put("contentId", contentName);
		templateContext.put("content", renderFile(contentName, context));
		templateContext.put("sensors", sensorHub.getSensorIdentifiers());
		Object sensor = context.get("sensor");
		if (sensor != null && sensor instanceof Sensor) {
			templateContext.put("currentSensor", ((Sensor)sensor).getIdentifier());
		}
		return renderFile("template", templateContext);
	}
	
	public String renderSensorConfig(Sensor sensor, String type, String legend, Boolean heating, Number temperature, float step, String submit, String otherInputs) {
		VelocityContext context = new VelocityContext();
		context.put("sensor", sensor.getIdentifier());
		context.put("type", type);
		context.put("legend", legend);
		context.put("heating", heating == null ? false : heating);
		context.put("temperature", temperature.floatValue());
		context.put("step", step);
		context.put("submit", submit);
		context.put("otherInputs", otherInputs);
		return renderFile("sensorConfig", context);
	}
	
	public String renderFile(String fileName, VelocityContext context) {
		StringWriter writer = new StringWriter();
		Velocity.mergeTemplate("web" + File.separator + "templates" + File.separator + fileName + ".vm", "UTF-8", context, writer);
		return writer.toString();
	}
	
	
	
	public Sensor processHive(Request request, Response response) {
		Sensor sensor = sensorHub.getSensor(request.params("hive"));
		if (sensor != null) {
			return sensor;
		} else {
			response.status(404);
			return null;
		}
	}
	
	public Boolean processHeatingEnabled(Request request, Response response) {
		String paramHeating = request.queryParams("heating");
		if (paramHeating == null) {
			response.status(400);
			return null;
		}
		
		switch (paramHeating) {
			case "on":
				return true;
			case "off":
				return false;
			default:
				response.status(400);
				return null;
		}
	}
	
	public Float processTargetTemperature(Request request, Response response) {
		String paramTemperature = request.queryParams("temperature");
		if (paramTemperature == null) {
			response.status(400);
			return null;
		}
		
		float temperature;
		try {
			temperature = Float.valueOf(paramTemperature);
		} catch (NumberFormatException e) {
			response.status(400);
			return null;
		}
		
		if (temperature > Sensor.MAX_TARGET_TEMPERATURE || temperature < Sensor.MIN_TARGET_TEMPERATURE) {
			response.status(400);
			return null;
		} else {
			return temperature;
		}
	}
	
	
	
	public Map<String, Collection<ScheduledConfig>> getScheduledConfigMap() {
		Map<String, Collection<ScheduledConfig>> configMap = new HashMap<>();
		for (Sensor sensor : sensorHub.getSensors()) {
			configMap.put(sensor.getIdentifier(), sensorHub.getScheduledConfigExecutor().getConfigs(sensor));
		}
		return configMap;
	}
	
	public Map<Long, String[]> getLogMap() {
		Map<Long, String[]> logMap = new HashMap<>();
		for (int i = 0; i < sensorArray.length; i++) {
			for (LogEntry logEntry : sensorHub.getDataLogger().getLogEntries(sensorArray[i])) {
				String[] value = logMap.computeIfAbsent(logEntry.getTimestamp(), (k) -> {
					String[] array = new String[sensorArray.length];
					Arrays.fill(array, "undefined");
					return array;
				});
				
				if (logEntry.getTemperature() != null) {
					value[i] = logEntry.getTemperature().toString();
				}
			}
		}
		
		return logMap;
	}
	
	public String readLastLines(File file, int amount) {
		try (ReversedLinesFileReader fileReader = new ReversedLinesFileReader(file, Charset.forName("UTF-8"))) {
			String[] lines = new String[amount];
			int counter = lines.length;
			
			String line;
			while (counter-- > 0) {
				line = fileReader.readLine();
				if (line == null) {
					break;
				}
				
				lines[counter] = line;
			}
			
			StringBuilder builder = new StringBuilder();
			line = lines[0];
			String lastLine;
			for (counter = 1; ; counter++) {
				if (line != null) {
					builder.append(line);
				}
				
				if (counter >= lines.length) {
					break;
				}
				
				lastLine = line;
				line = lines[counter];
				if (line != null && lastLine != null) {
					builder.append('\n');
				}
			}
			
			return StringEscapeUtils.escapeHtml(builder.toString());
		} catch (IOException e) {
			LOGGER.error("Couldn't open the log file: ", e);
			return null;
		}
	}
}
