import com.fazecast.jSerialComm.SerialPort;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * USB Serial → Backend bridge for TAPP battery voltage data.
 *
 * Reads voltage measurements from the Arduino over USB serial,
 * then POSTs them to the backend API which pairs each reading
 * with the current NFC ID (set by the NFC reader on /api/nfc).
 *
 * Config via environment variables:
 *   COM_PORT    - serial port name (default: COM6)
 *   BAUD_RATE   - baud rate       (default: 115200)
 *   API_URL     - backend base URL (default: http://localhost:8080)
 */
public class Main {

    private static final String COM_PORT  = System.getenv().getOrDefault("COM_PORT",  "COM6");
    private static final int    BAUD_RATE = Integer.parseInt(System.getenv().getOrDefault("BAUD_RATE", "115200"));
    private static final String API_URL   = System.getenv().getOrDefault("API_URL",   "http://localhost:8080");

    private static final float V_MAX = 3.60f;
    private static final float V_MIN = 2.40f;

    public static void main(String[] args) {
        SerialPort port = SerialPort.getCommPort(COM_PORT);
        port.setBaudRate(BAUD_RATE);

        if (!port.openPort()) {
            System.out.println("Failed to open port: " + COM_PORT);
            return;
        }

        System.out.println("Listening on " + COM_PORT + " at " + BAUD_RATE + " baud");
        System.out.println("Sending measurements to " + API_URL);

        // Prevent DTR signal from resetting the ESP8266 on port open
        port.setDTR(false);
        port.setRTS(false);
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        System.out.println("Ready — waiting for Arduino data...");

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        int slotCounter = 1;

        // Wait indefinitely for data — no timeout crash between Arduino measurements
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 0, 0);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(port.getInputStream()))) {
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                System.out.println("[SERIAL] " + line);

                // ReadVoltageSerial.ino (ESP8266) outputs a bare number: "3.142"
                // ReadVoltage.ino (Arduino UNO) outputs: "Median Voltage: 3.142 V"
                float voltage = parseVoltage(line);
                if (voltage < 0) {
                    continue;
                }

                int percent   = voltageToPercent(voltage);
                String slotId = "SLOT-" + slotCounter;

                System.out.printf("Parsed → slot: %s | voltage: %.3f V | percent: %d%%%n",
                        slotId, voltage, percent);

                boolean sent = postMeasurement(http, slotId, voltage, percent);

                if (sent) {
                    slotCounter++;
                }
            }

        } catch (Exception e) {
            System.out.println("Error reading serial: " + e.getMessage());
            e.printStackTrace();
        } finally {
            port.closePort();
        }
    }

    /**
     * Parses voltage from either:
     *   "3.142"                  (ReadVoltageSerial.ino — ESP8266)
     *   "Median Voltage: 3.142 V" (ReadVoltage.ino — Arduino UNO)
     * Returns -1 if the line cannot be parsed as a voltage.
     */
    private static float parseVoltage(String line) {
        try {
            String number = line
                    .replace("Median Voltage:", "")
                    .replace("V", "")
                    .trim();
            float v = Float.parseFloat(number);
            // Sanity check — ignore noise/debug lines that happen to parse as numbers
            if (v < 0 || v > 5) return -1;
            return v;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Maps voltage to 0-100% using the same thresholds as the backend.
     */
    private static int voltageToPercent(float voltage) {
        if (voltage >= V_MAX) return 100;
        if (voltage <= V_MIN) return 0;
        return Math.round((voltage - V_MIN) / (V_MAX - V_MIN) * 100);
    }

    /**
     * POSTs a measurement to POST /api/battery.
     * The backend pairs it with the current NFC ID stored in its memory.
     * Returns true if the server accepted the measurement.
     */
    private static boolean postMeasurement(HttpClient http, String slotId, float voltage, int percent) {
        String body = String.format(
                "{\"slot_id\":\"%s\",\"voltage\":%.3f,\"percent\":%d}",
                slotId, voltage, percent
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + "/api/battery"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("Stored OK: " + response.body());
                return true;
            } else {
                System.out.println("Backend rejected [" + response.statusCode() + "]: " + response.body());
                return false;
            }

        } catch (Exception e) {
            System.out.println("Failed to reach backend: " + e.getMessage());
            return false;
        }
    }
}
