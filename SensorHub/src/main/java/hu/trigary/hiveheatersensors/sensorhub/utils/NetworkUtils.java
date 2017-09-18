package hu.trigary.hiveheatersensors.sensorhub.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class NetworkUtils {
	private static final SimpleLogger LOGGER = new SimpleLogger(NetworkUtils.class.getSimpleName());
	private static String linuxWifiInterfaceName = null;
	
	public static void setLinuxWifiInterfaceName(String name) {
		linuxWifiInterfaceName = name;
	}
	
	
	
	public static Map<String, String> getMacIpPairs() {
		Map<String, String> macIpMap = new HashMap<>();
		
		try {
			Process process = Runtime.getRuntime().exec("arp -a");
			
			String errorString;
			BufferedReader errorStream = new BufferedReader(new InputStreamReader(process.getErrorStream()));
			while ((errorString = errorStream.readLine()) != null) {
				LOGGER.error("arp command returned error: " + errorString);
			}
			errorStream.close();
			
			BufferedReader inputStream = new BufferedReader(new InputStreamReader(process.getInputStream()));
			if (System.getProperty("os.name").startsWith("Windows")) {
				getWindowsMacIpPairs(macIpMap, inputStream);
			} else {
				getLinuxMacIpPairs(macIpMap, inputStream);
			}
			inputStream.close();
		} catch (IOException e) {
			LOGGER.error("Error while getting MAC-IP map: ", e);
		}
		
		return macIpMap;
	}
	
	private static void getWindowsMacIpPairs(Map<String, String> macIpMap, BufferedReader inputStream) throws IOException {
		String string;
		while ((string = inputStream.readLine()) != null) {
			string = string.trim();
			if (!string.endsWith("dynamic")) {
				continue;
			}
			
			String[] split = string.split("\\s+");
			if (split[0].endsWith("1")) {
				continue;
			}
			
			split[1] = formatMac(split[1]);
			LOGGER.debug("Found pair: MAC: %s | IP: %s", split[1], split[0]);
			macIpMap.put(split[1], split[0]);
		}
	}
	
	private static void getLinuxMacIpPairs(Map<String, String> macIpMap, BufferedReader inputStream) throws IOException {
		String string;
		while ((string = inputStream.readLine()) != null) {
			if (linuxWifiInterfaceName != null && !string.endsWith(linuxWifiInterfaceName)) {
				continue;
			}
			
			int index;
			StringBuilder ipBuilder = new StringBuilder();
			boolean writing = false;
			for (index = 2; index < string.length(); index++) {
				char character = string.charAt(index);
				if (writing) {
					if (character == ')') {
						break;
					} else {
						ipBuilder.append(character);
					}
				} else if (character == '(') {
					writing = true;
				}
			}
			
			String ip = ipBuilder.toString();
			index += 5;
			String mac = formatMac(string.substring(index, index + 17));
			LOGGER.debug("Found pair: MAC: %s | IP: %s", mac, ip);
			macIpMap.put(mac, ip);
		}
	}
	
	
	
	public static String formatMac(String mac) {
		return mac.replaceAll("[^a-fA-F0-9:]", ":").toUpperCase();
	}
}
