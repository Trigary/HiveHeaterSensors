#include <ESP8266HTTPUpdateServer.h>
#include <EEPROM\EEPROM.h>
#include <ESP8266WebServer.h>
#include <DallasTemperature.h>
#include <OneWire.h>
#include <WiFiUdp.h>
#include <WiFiServer.h>
#include <WiFiClientSecure.h>
#include <WiFiClient.h>
#include <ESP8266WiFiType.h>
#include <ESP8266WiFiSTA.h>
#include <ESP8266WiFiScan.h>
#include <ESP8266WiFiMulti.h>
#include <ESP8266WiFiGeneric.h>
#include <ESP8266WiFiAP.h>
#include <ESP8266WiFi.h>

#define SENSOR_PIN 5
#define HEAT_PIN 4

#define WIFI_SSID "ssid"
#define WIFI_PW "password"
#define WIFI_LOST_ACTIVATE_DEFAULTS_MILLIS 300000

#define MEASUREMENT_TIMER_SECONDS 3
#define MIN_TEMPERATURE -30 //Temperature offset as well
#define MAX_TEMPERATURE 36

OneWire sensorWire(SENSOR_PIN);
DallasTemperature sensors(&sensorWire);
DeviceAddress temperatureDevice;
volatile bool timerTicked(false);

float targetTemperature;
bool heatingEnabled;
bool heatingNow;

ESP8266WebServer server(80);
ESP8266HTTPUpdateServer updateServer(false);
float actualTemperature;
volatile unsigned long wifiDisconnectMillis;



void setup() {
	Serial.begin(115200);
	serialLog("Initializing...");

	serialLog("Min target temperature: ", String(MIN_TEMPERATURE));
	serialLog("Max target temperature: ", String(MAX_TEMPERATURE));
	EEPROM.begin(512);

	pinMode(HEAT_PIN, OUTPUT);
	digitalWrite(HEAT_PIN, LOW);
	heatingNow = false;
	
	sensors.begin();
	sensors.setResolution(10);
	sensors.setWaitForConversion(false);
	sensors.getAddress(temperatureDevice, 0);
	sensors.requestTemperaturesByAddress(temperatureDevice);

	WiFi.mode(WIFI_STA);
	WiFi.onStationModeConnected([](WiFiEventStationModeConnected connected) {
		serialLog("Wifi connected");
		wifiDisconnectMillis = 0;
	});
	WiFi.onStationModeDisconnected([](WiFiEventStationModeDisconnected disconnect) {
		serialLog("Wifi disconnected");
		wifiDisconnectMillis = millis();
	});
	WiFi.begin(WIFI_SSID, WIFI_PW);
	while (WiFi.status() != WL_CONNECTED) {
		delay(500);
	}

	server.onNotFound(handleNotFound);
	server.on("/", handleRoot);
	server.on("/temperature", handleTemperature);
	server.on("/prom", handleProm);
	updateServer.setup(&server);
	server.begin();

	timer1_isr_init();
	timer1_attachInterrupt([] {
		timerTicked = true;
	});
	timer1_enable(TIM_DIV265, TIM_EDGE, TIM_LOOP);
	timer1_write(F_CPU / 256 * MEASUREMENT_TIMER_SECONDS);

	serialLog("Initialization complete");
}

void loop() {
	if (timerTicked) {
		timerTicked = false;
		actualTemperature = sensors.getTempC(temperatureDevice);
		serialLog("Timer ticked, read temperature: ", String(actualTemperature));
		sensors.requestTemperaturesByAddress(temperatureDevice);

		if (heatingEnabled) {
			if (heatingNow) {
				if (actualTemperature >= targetTemperature) {
					digitalWrite(HEAT_PIN, LOW);
					heatingNow = false;
					serialLog("Heater pin off");
				}
			} else {
				if (actualTemperature < targetTemperature) {
					digitalWrite(HEAT_PIN, HIGH);
					heatingNow = true;
					serialLog("Heater pin on");
				}
			}
		}

		if (wifiDisconnectMillis != 0 && wifiDisconnectMillis + WIFI_LOST_ACTIVATE_DEFAULTS_MILLIS > millis()) {
			serialLog("WiFi connection lost for too long time, activating default config");
			loadPromConfig();
		}
	}

	server.handleClient();
}



void handleNotFound() {
	serialLogWeb("Page doesn't exist - ");
	server.send(404, "text/plain", "The specified page doesn't exist.");
}

void handleRoot() {
	if (server.hasArg("v")) {
		String argV = server.arg("v");
		float value = argV.toFloat();
		if ((value == 0 && !argV.equals("0") && !argV.equals("0.0")) || value < 0 || value > 255) {
			serialLogWeb("Invalid value for argument 'v' - ");
			server.send(400, "text/plain", "Invalid value for argument: v");
			return;
		}

		serialAppend("Web: ");
		changeCurrentConfig(value);
	}

	String text = "Actual temperature: ";
	text += actualTemperature;
	text += "\r\n\r\nHeating enabled: ";
	text += heatingEnabled ? "yes" : "no";
	text += "\r\nTarget temperature: ";
	text += String(targetTemperature);
	text += "\r\n\r\nPROM value: ";
	text += EEPROM.read(0);
	text += "\r\nMin target temperature: ";
	text += MIN_TEMPERATURE;
	text += "\r\nMax target temperature: ";
	text += MAX_TEMPERATURE;
	text += "\r\nAll values above the max value are handled as 'heating off'.";

	serialLogWeb("Sending root page - ");
	server.send(200, "text/plain", text);
}

void handleTemperature() {
	serialLogWeb("Sending temperature page - ");
	server.send(200, "text/plain", String(actualTemperature));
}

void handleProm() {
	if (server.hasArg("v")) {
		String argV = server.arg("v");
		long value = argV.toInt();
		if ((value == 0 && !argV.equals("0")) || value < 0 || value > 255) {
			serialLogWeb("Invalid value for argument 'v' - ");
			server.send(400, "text/plain", "Invalid value for argument: v");
			return;
		}

		serialLog("Writing to prom: ", String(value));
		EEPROM.write(0, value);
		EEPROM.commit();
	}

	serialLogWeb("Sending prom page - ");
	server.send(200, "text/plain", String(EEPROM.read(0)));
}



void loadPromConfig() {
	int16_t value = EEPROM.read(0);
	serialAppend("Prom: ");
	changeCurrentConfig(value);
}

void changeCurrentConfig(float value) {
	value += MIN_TEMPERATURE;
	if (value > MAX_TEMPERATURE) {
		targetTemperature = 0;
		heatingEnabled = false;
		serialLog("heating disabled");
	} else {
		targetTemperature = value;
		heatingEnabled = true;
		serialLog("heating enabled, target temperature: ", String(value));
	}
}



void serialLog(const char* text) {
	Serial.println(text);
}

void serialLog(const char* text, String value) {
	Serial.print(text);
	Serial.println(value);
}

void serialLogWeb(const char* text) {
	Serial.print(text);
	Serial.println(server.client().remoteIP().toString());
}

void serialAppend(const char* text) {
	Serial.print(text);
}
