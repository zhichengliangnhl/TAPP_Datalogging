# TAPP Datalogging

A battery monitoring and datalogging system for TAPP ink chips. This project uses an ESP8266 microcontroller to read battery voltage, calculate percentage, and send data to a Node.js backend API, which stores measurements in a PostgreSQL database.

## How It Works

The system consists of two main components:

1. **Firmware (ESP8266)**: Continuously monitors analog voltage from a connected sensor. Detects chip insertion, stabilizes readings, calculates battery percentage, and sends data to the backend via HTTP POST.
2. **Backend (Node.js/Fastify)**: Provides a REST API for receiving and retrieving battery measurements. Stores data in PostgreSQL.

### Data Flow
- ESP8266 detects chip insertion (voltage > 1.0V).
- Waits for stable voltage readings (5 consecutive identical values).
- Calculates percentage based on calibrated min/max voltages.
- Sends JSON payload to backend API.
- Backend inserts data into database.
- API endpoints allow fetching logged measurements.

## Prerequisites

- **Node.js** (v14+): [Download here](https://nodejs.org/)
- **PostgreSQL**: [Download here](https://www.postgresql.org/download/)
- **Arduino IDE**: [Download here](https://www.arduino.cc/en/software)
- **ESP8266 Board** (e.g., ESP-12 or NodeMCU) with voltage sensor connected to A0 pin
- Same WiFi network for ESP8266 and server

## Installation

1. Clone or download the repository:
   ```
   git clone https://github.com/zhichengliangnhl/TAPP_Datalogging.git
   cd TAPP_Datalogging
   ```

2. Install backend dependencies:
   ```
   npm install
   ```

3. Set up PostgreSQL:
   - Create a database (e.g., `tapp_db`).
   - Note your connection details (host, user, password, port).

4. Create a `.env` file in the project root:
   ```
   # =========================
   # APP
   # =========================
   PORT=8080
   NODE_ENV=development
   
   # =========================
   # DATABASE (used by Node)
   # =========================
   DB_HOST=localhost
   DB_PORT=5432
   DB_USER=your_db_username
   DB_PASS=your_db_password
   DB_NAME=tapp_battery
   ```

5. Install ESP8266 support in Arduino IDE (for ESP-12 board):

   ### How to Install the Correct Board
   1. Open Arduino IDE
   2. Go to: File → Preferences
   3. Add the ESP8266 boards URL: In "Additional Boards Manager URLs" paste: `http://arduino.esp8266.com/stable/package_esp8266com_index.json` (If there are already URLs there, separate them with a comma.)
   4. Install the board package: Go to Tools → Board → Boards Manager, search for "ESP8266", and install "ESP8266 by ESP8266 Community".
   5. Select your board: After installing, go to Tools → Board → ESP8266 Boards. Common choices for ESP-12 modules: NodeMCU 1.0 (ESP-12E Module) ← most common, or Generic ESP8266 Module ← safest if unsure.

## Running the Program

### Backend Server

1. Ensure PostgreSQL is running.
2. Start the server:
   ```
   node backend/server.js
   ```
   - Server runs on `http://localhost:8080` (or configured port).
   - Initializes database table on startup.

### Firmware (ESP8266)

This project uses an Arduino ESP-12 (ESP8266-based) module.

1. Open `firmware/ReadVoltage/ReadVoltage.ino` in Arduino IDE.
2. Update WiFi credentials:
   ```cpp
   const char* SSID     = "Your_WiFi_SSID";
   const char* PASSWORD = "Your_WiFi_Password";
   ```
3. Update server URL (replace with your server's IP):
   ```cpp
   String serverUrl = "http://YOUR_SERVER_IP:8080/api/battery";
   ```
4. Select board: **Tools > Board > NodeMCU 1.0 (ESP-12E Module)**
5. Select port: **Tools > Port > [Your ESP8266 COM Port]**
6. Upload: Click the upload button.
7. Monitor: **Tools > Serial Monitor** (115200 baud) for debug output.

### Testing

- Insert a chip into the sensor.
- Check Serial Monitor for readings and "STORED" messages.
- Query API: `curl http://localhost:8080/api/battery` to fetch data.

## API Documentation

- **GET /api/health**: Health check (returns `{"status": 200}`)
- **POST /api/battery**: Log measurement
  - Body: `{"device_id": "string", "chip_number": int, "voltage": float, "percent": int}`
  - Response: `{"id": int, "created_at": "timestamp"}`
- **GET /api/battery**: Get last 50 measurements (ordered by newest)

## Configuration

- **Voltage Calibration**: Adjust `V_SCALE`, `V_MAX`, `V_MIN` in firmware for accurate readings.
- **Chip Detection**: `V_CHIP_PRESENT` threshold for insertion/removal.
- **Database**: Modify `backend/init-db.js` for schema changes.

## Troubleshooting

- **DB Connection Error**: Check `.env` and PostgreSQL status.
- **WiFi Issues**: Verify credentials and network.
- **No Data Sent**: Ensure server is running and URL is correct.
- **Voltage Off**: Calibrate `V_SCALE` with a multimeter.

## License

ISC License. See repository for details.
