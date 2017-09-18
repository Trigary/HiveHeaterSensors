package hu.trigary.hiveheatersensors.sensorhub.utils;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;

public class SimpleLogger {
	private static final SimpleFormatter SIMPLE_FORMATTER = new SimpleFormatter();
	private static final ConsoleHandler CONSOLE_HANDLER = new ConsoleHandler();
	
	static {
		CONSOLE_HANDLER.setFormatter(SIMPLE_FORMATTER);
		CONSOLE_HANDLER.setLevel(Level.INFO);
	}
	
	public SimpleLogger(String name) {
		logger = Logger.getLogger(name);
		logger.setUseParentHandlers(false);
		logger.setLevel(debug ? Level.ALL : Level.INFO);
		
		logger.addHandler(CONSOLE_HANDLER);
		if (fileHandler != null) {
			logger.addHandler(fileHandler);
		}
	}
	
	private final Logger logger;
	private static boolean debug = false;
	private static FileHandler fileHandler = null;
	
	
	
	public static SimpleLogger initializeAndGet(boolean debug, String name) {
		if (debug) {
			SimpleLogger.debug = true;
			CONSOLE_HANDLER.setLevel(Level.ALL);
		}
		
		try {
			fileHandler = new FileHandler("logs.txt", true);
			fileHandler.setFormatter(SIMPLE_FORMATTER);
			fileHandler.setLevel(debug ? Level.ALL : Level.INFO);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return new SimpleLogger(name);
	}
	
	
	
	public void error(String msg) {
		logger.severe(msg);
	}
	
	public void error(String msg, Throwable throwable) {
		logger.log(Level.SEVERE, msg, throwable);
	}
	
	public void error(String format, Object... args) {
		error(String.format(format, args));
	}
	
	public void error(String format, Throwable throwable, Object... args) {
		error(String.format(format, args), throwable);
	}
	
	public void info(String msg) {
		logger.info(msg);
	}
	
	public void info(String format, Object... args) {
		info(String.format(format, args));
	}
	
	public void debug(String msg) {
		logger.fine(msg);
	}
	
	public void debug(String format, Object... args) {
		debug(String.format(format, args));
	}
	
	
	
	private static class SimpleFormatter extends Formatter {
		private static final DateFormat DATE_FORMAT = new SimpleDateFormat("YY/MM/dd HH:mm:ss");
		private static final String LINE_SEPARATOR = System.getProperty("line.separator");
		
		@Override
		public String format(LogRecord record) {
			StringBuilder out = new StringBuilder();
			out.append("[");
			out.append(DATE_FORMAT.format(new Date(record.getMillis())));
			out.append("] [");
			
			if (record.getLevel() == Level.SEVERE) {
				out.append("ERROR");
			} else if (record.getLevel() == Level.INFO) {
				out.append("INFO");
			} else if (record.getLevel() == Level.FINE) {
				out.append("DEBUG");
			} else {
				out.append(record.getLevel().getLocalizedName());
			}
			
			out.append("] [");
			out.append(record.getLoggerName());
			out.append("]: ");
			out.append(formatMessage(record));
			out.append(LINE_SEPARATOR);
			
			if (record.getThrown() != null) {
				try {
					StringWriter stringWriter = new StringWriter();
					PrintWriter printWriter = new PrintWriter(stringWriter);
					record.getThrown().printStackTrace(printWriter);
					printWriter.close();
					out.append(stringWriter.toString());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
			return out.toString();
		}
	}
}
