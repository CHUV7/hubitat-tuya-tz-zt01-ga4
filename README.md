# Hubitat Driver — Tuya TZ-ZT01_GA4 Temperature & Humidity Sensor with External Probe

A custom Hubitat driver for the cheap AliExpress **Tuya TZ-ZT01_GA4** Zigbee temperature and humidity sensor with an external temperature probe (https://www.zigbee2mqtt.io/devices/TZ-ZT01_GA4.html). 
Most generic drivers only expose the ambient temperature senso. This driver exposes all four data points the device reports, including the external probe. It's also setup to provide child devices for each sensor so they can be used in Hubitat's Safety Monitor. I'm using these probes for to monitor freezers and provide warnings if the temp is above 0.

---

## Supported Device

| Field | Value |
|---|---|
| Model | TZ-ZT01_GA4 |
| Vendor | Tuya |
| Manufacturer string | `_TZE284_8se38w3c` |
| Protocol | Zigbee (Tuya EF00 cluster) |
| Connection | Directly paired to Hubitat's built-in Zigbee radio |

---

## Features

- **Ambient temperature** — onboard sensor
- **Probe temperature** — external wired probe sensor
- **Humidity** — onboard humidity sensor
- **Battery state** — low / medium / high (plus approximate % for the Battery capability)
- **Optional child devices** for each sensor so all three are available in **Hubitat Safety Monitor (HSM)** and other apps that only see standard capabilities
- **Per-sensor offsets** for calibration (ambient temp, probe temp, humidity)
- **°C / °F toggle**
- **Configure button** to request a fresh reading from the device
- Debug and description text logging with auto-off after 24 hours

---

## DP Map

Confirmed from live device logs:

| DP | Attribute | Scaling |
|---|---|---|
| 1 | Ambient temperature | raw / 10 → °C |
| 2 | Humidity | raw integer → % |
| 3 | Battery state | enum: 0=low, 1=medium, 2=high |
| 38 | Probe temperature | raw / 10 → °C |

---

## Installation

### Option A — Import URL (easiest)

1. In Hubitat, go to **Drivers Code** → **+ New Driver** → click **Import**
2. Paste the following URL and click **Import**:
   ```
   https://raw.githubusercontent.com/YOUR_USERNAME/YOUR_REPO/main/drivers/TZ-ZT01_GA4.groovy
   ```
3. Click **Save**

### Option B — Manual

1. In Hubitat, go to **Drivers Code** → **+ New Driver**
2. Copy the contents of [`drivers/TZ-ZT01_GA4.groovy`](drivers/TZ-ZT01_GA4.groovy) and paste into the editor
3. Click **Save**

---

## Setup

1. Pair your TZ-ZT01_GA4 to Hubitat's Zigbee radio as normal
2. On the device page, set the **Type** to `Tuya TZ-ZT01_GA4 Temp/Humidity + Probe`
3. Click **Save Device**
4. Click **Configure** — this creates child devices and requests an initial reading
5. Optionally rename the child devices (e.g. "Freezer Ambient", "Freezer Probe", "Freezer Humidity")
6. All three sensors are now available in HSM, dashboards, and Rule Machine

---

## Preferences

| Preference | Description |
|---|---|
| Ambient Temperature Offset | Calibration offset in °C applied to the onboard sensor |
| Probe Temperature Offset | Calibration offset in °C applied to the probe sensor |
| Humidity Offset | Calibration offset in % applied to humidity |
| Temperature Unit | Celsius or Fahrenheit — applies to both sensors and their child devices |
| Create child device for Ambient Temperature | Toggle to create/remove the ambient temp child device |
| Create child device for Probe Temperature | Toggle to create/remove the probe child device |
| Create child device for Humidity | Toggle to create/remove the humidity child device |
| Enable Debug Logging | Verbose logging, auto-disables after 24 hours |
| Enable Description Text Logging | Info-level event logging |

---

## Credits

Inspired by the excellent community work by **kkossev** on the Hubitat forums, I created and tested this driver using ClaudeAI. 

> [RELEASE] Tuya Temperature Humidity Illuminance LCD Display with a Clock (w/ healthStatus)
> https://community.hubitat.com/t/release-tuya-temperature-humidity-illuminance-lcd-display-with-a-clock-w-healthstatus/88093

If your device has a different manufacturer string or the readings look wrong, enable debug logging and check the logs for `NOT PROCESSED dp=X` lines — these reveal any unmapped data points. 

---

## License

MIT License — free to use, modify, and share.
