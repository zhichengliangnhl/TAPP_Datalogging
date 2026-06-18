#include <ESP8266WiFi.h>
#include <ESP8266HTTPClient.h>

// ── Calibration ──────────────────────────────
const float V_SCALE = 4.069f;
const float V_MAX   = 3.60f;
const float V_MIN   = 2.40f;
const float V_CHIP_PRESENT = 1.0f;

// ── Chip tracking ────────────────────────────
int  chipCount   = 0;
bool chipPresent = false;

// ── Helpers ──────────────────────────────────
float readVoltage() {
    long sum = 0;
    for (int i = 0; i < 16; i++) {
        sum += analogRead(A0);
        delay(2);
    }
    return (sum / 16.0f) / 1023.0f * V_SCALE;
}

uint8_t voltageToPercent(float v) {
    if (v >= V_MAX) return 100;
    if (v <= V_MIN) return 0;
    return (uint8_t)((v - V_MIN) / (V_MAX - V_MIN) * 100.0f + 0.5f);
}

// ── Setup ────────────────────────────────────
void setup() {
    Serial.begin(9600);
    delay(200);
}

// ── Loop ─────────────────────────────────────
unsigned long lastPrint = 0;

void loop() {

    static bool chipLocked = false;

    float rawVoltage = readVoltage();
    bool present = rawVoltage >= V_CHIP_PRESENT;

    // If chip removed → reset
    if (!present && chipLocked) {
        chipLocked = false;
        //Serial.println("Chip removed. Ready for next chip.");
        delay(300);
        return;
    }

    // No chip present
    if (!present) {
        delay(300);
        return;
    }

    // Already processed this chip
    if (chipLocked) {
        delay(300);
        return;
    }

    // ── Take 10 readings ──
    float readings[10];

    //Serial.println("Measuring voltage...");

    for (int i = 0; i < 10; i++) {
        readings[i] = readVoltage();
        //Serial.printf("Reading %d: %.3f V\n", i + 1, readings[i]);
        delay(100);
    }

    // ── Sort readings (simple bubble sort) ──
    for (int i = 0; i < 9; i++) {
        for (int j = i + 1; j < 10; j++) {
            if (readings[j] < readings[i]) {
                float temp = readings[i];
                readings[i] = readings[j];
                readings[j] = temp;
            }
        }
    }

    // ── Median (average of middle two values) ──
    float medianVoltage = (readings[4] + readings[5]) / 2.0;

    uint8_t percent = voltageToPercent(medianVoltage);

    chipCount++;

    Serial.println(medianVoltage);

    // dbSend(chipCount, medianVoltage, percent);

    //Serial.println("REMOVE CHIP");

    chipLocked = true;

    delay(300);
}