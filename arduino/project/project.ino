#include <WiFi.h>
#include <WebServer.h>
#include <DHT.h> 

const char* ssid = "Nikoleta’s iPhone ";
const char* password = "12345678";

#define DHT22_PIN 21 
#define MQ2_ANALOG_PIN 34 


const float TEMPERATURE_THRESHOLD_NORMAL_MAX = 30.0;  
const float TEMPERATURE_THRESHOLD_WARNING_MAX = 45.0; 
const float TEMPERATURE_THRESHOLD_CRITICAL = 55.0;    
 
const int MQ2_THRESHOLD_NORMAL_MAX = 800;    
const int MQ2_THRESHOLD_WARNING_MAX = 1500; 
const int MQ2_THRESHOLD_CRITICAL = 2500;      
 
const float HUMIDITY_THRESHOLD_NORMAL_MIN = 30.0; 
const float HUMIDITY_THRESHOLD_NORMAL_MAX = 70.0; 
const float HUMIDITY_THRESHOLD_LOW_CRITICAL = 20.0; 
const float HUMIDITY_THRESHOLD_HIGH_CRITICAL = 85.0; 

DHT dht22(DHT22_PIN, DHT22);
WebServer server(80);

float temperature = 0.0;
float humidity = 0.0;
int mq2Value = 0;
String systemStatusMessage = "Няма данни."; 

String getSensorStatusHTML();
void handleRoot();
void readAndProcessSensors();
void updateSystemStatus();

String getSensorStatusHTML() {
  String html = F("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
  html += F("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
  html += F("<meta http-equiv='refresh' content='5'>"); 
  html += F("<title>Мониторинг на Пожароизвестяване</title>");
  html += F("<style>");
  html += F("body { font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 20px; color: #333; }");
  html += F(".container { background-color: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1); max-width: 600px; margin: 20px auto; }");
  html += F("h2 { color: #0056b3; text-align: center; margin-bottom: 20px; }");
  html += F("p { font-size: 1.1em; margin: 10px 0; }");
  html += F("b { color: #555; }");
  html += F(".status-normal { color: green; font-weight: bold; }");  
  html += F(".status-warning { color: orange; font-weight: bold; }");  
  html += F(".status-critical { color: red; font-weight: bold; }");   
  html += F("</style>");
  html += F("</head><body><div class='container'>");
  html += F("<h2>Състояние на сензорите</h2>");
  html += F("<p><b>Температура:</b> "); html += String(temperature, 1); html += F(" °C</p>");
  html += F("<p><b>Влажност:</b> "); html += String(humidity, 1); html += F(" %</p>");
  html += F("<p><b>Газ/Дим (MQ-2):</b> "); html += String(mq2Value); html += F(" / 4095</p>");
  html += F("<p><b>Състояние:</b> <span class='");

  bool isAnyCritical = (temperature >= TEMPERATURE_THRESHOLD_CRITICAL) ||
                       (mq2Value >= MQ2_THRESHOLD_CRITICAL) ||
                       (humidity <= HUMIDITY_THRESHOLD_LOW_CRITICAL && humidity != -999.0) ||
                       (humidity >= HUMIDITY_THRESHOLD_HIGH_CRITICAL && humidity != -999.0);

  bool isAnyWarning = (temperature >= TEMPERATURE_THRESHOLD_WARNING_MAX && temperature < TEMPERATURE_THRESHOLD_CRITICAL) ||
                      (mq2Value >= MQ2_THRESHOLD_WARNING_MAX && mq2Value < MQ2_THRESHOLD_CRITICAL) ||
                      (humidity < HUMIDITY_THRESHOLD_NORMAL_MIN && humidity > HUMIDITY_THRESHOLD_LOW_CRITICAL && humidity != -999.0) ||
                      (humidity > HUMIDITY_THRESHOLD_NORMAL_MAX && humidity < HUMIDITY_THRESHOLD_HIGH_CRITICAL && humidity != -999.0) ||
                      (systemStatusMessage.indexOf(F("неопределено")) != -1); 

  if (isAnyCritical) {
    html += F("status-critical"); 
  } else if (isAnyWarning) {
    html += F("status-warning");  
  } else {
    html += F("status-normal");   
  }
  

  html += F("'>");
  html += systemStatusMessage; 
  html += F("</span></p>");
  html += F("<p style='text-align: center; font-size: 0.9em; color: #777;'>Обновяване на всеки 5 секунди</p>");
  html += F("</div></body></html>");
  return html;
}


void handleRoot() {
  server.send(200, "text/html", getSensorStatusHTML()); // Изпраща HTML страницата към клиента
}


void setup() {
  Serial.begin(115200); 
  delay(100); 

  dht22.begin(); 

  Serial.println(F("\nСвързване с Wi-Fi..."));
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, password);

  unsigned long startAttemptTime = millis();
  
  while (WiFi.status() != WL_CONNECTED && millis() - startAttemptTime < 20000) {
    Serial.print(F(".")); 
    delay(500); 
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println(F("\nWi-Fi връзката е успешна!"));
    Serial.print(F("IP адрес: "));
    Serial.println(WiFi.localIP()); 
  } else {
    Serial.println(F("\nГрешка при свързване с Wi-Fi. Моля, проверете SSID/паролата и рестартирайте."));
  }


  server.on("/", handleRoot); 
  server.begin(); 
  Serial.println(F("Уеб сървърът е стартиран на порт 80"));
}


void loop() {
  server.handleClient();       
  readAndProcessSensors();  
  updateSystemStatus();     

  delay(1000); 
}

void readAndProcessSensors() {
  
  float h = dht22.readHumidity();
  float t = dht22.readTemperature();


  if (isnan(h) || isnan(t)) {
    Serial.println(F("Грешка при четене от DHT22 сензора! Пропускане на актуализация на температура и влажност."));
    
  } else {
    temperature = t; 
    humidity = h;    
    Serial.print(F("DHT22 - Влажност: "));
    Serial.print(humidity, 1); 
    Serial.print(F("% | Температура: "));
    Serial.print(temperature, 1);
    Serial.println(F("°C"));
  }


  mq2Value = analogRead(MQ2_ANALOG_PIN);
  Serial.print(F("MQ-2 - Стойност: "));
  Serial.print(mq2Value);
  Serial.println(F(" / 4095"));
}


void updateSystemStatus() {
  
  bool isTempCritical = (temperature >= TEMPERATURE_THRESHOLD_CRITICAL);
  bool isMq2Critical = (mq2Value >= MQ2_THRESHOLD_CRITICAL);
  bool isHumidityCriticalLow = (humidity <= HUMIDITY_THRESHOLD_LOW_CRITICAL && humidity != -999.0); 
  bool isHumidityCriticalHigh = (humidity >= HUMIDITY_THRESHOLD_HIGH_CRITICAL && humidity != -999.0);

  bool isTempWarning = (temperature >= TEMPERATURE_THRESHOLD_WARNING_MAX && !isTempCritical);
  bool isMq2Warning = (mq2Value >= MQ2_THRESHOLD_WARNING_MAX && !isMq2Critical);
  bool isHumidityWarningLow = (humidity < HUMIDITY_THRESHOLD_NORMAL_MIN && humidity > HUMIDITY_THRESHOLD_LOW_CRITICAL && humidity != -999.0);
  bool isHumidityWarningHigh = (humidity > HUMIDITY_THRESHOLD_NORMAL_MAX && humidity < HUMIDITY_THRESHOLD_HIGH_CRITICAL && humidity != -999.0);

  if (isTempCritical && isMq2Critical) {
    systemStatusMessage = "ВНИМАНИЕ! КРИТИЧНО СЪСТОЯНИЕ - ВЕРОЯТЕН ПОЖАР!";
  } else if (isTempCritical) {
    systemStatusMessage = "Критично висока температура! Възможен проблем.";
  } else if (isMq2Critical) {
    systemStatusMessage = "Критично високи нива на газ/дим! Възможен пожар.";
  } else if (isHumidityCriticalLow) {
    systemStatusMessage = "ВНИМАНИЕ: Изключително ниска влажност! Повишен риск от бързо разпространение на огън.";
  } else if (isHumidityCriticalHigh) {
    systemStatusMessage = "ВНИМАНИЕ: Изключително висока влажност! Възможен теч или конденз.";
  }
  
  else if (isTempWarning || isMq2Warning || isHumidityWarningLow || isHumidityWarningHigh) {
    String warningMessage = "Внимание: ";
    if (isTempWarning) warningMessage += "Повишена температура! ";
    if (isMq2Warning) warningMessage += "Повишено задимяване! ";
    if (isHumidityWarningLow) warningMessage += "Ниска влажност! ";
    if (isHumidityWarningHigh) warningMessage += "Висока влажност! ";
    systemStatusMessage = warningMessage;
  }
  
  else if (temperature <= TEMPERATURE_THRESHOLD_NORMAL_MAX && mq2Value <= MQ2_THRESHOLD_NORMAL_MAX &&
           humidity >= HUMIDITY_THRESHOLD_NORMAL_MIN && humidity <= HUMIDITY_THRESHOLD_NORMAL_MAX) {
    systemStatusMessage = "Системата е в нормално състояние.";
  }
  
  else {
    systemStatusMessage = "Състоянието е неопределено или гранично.";
  }

  Serial.println("Текущо състояние: " + systemStatusMessage); 
}