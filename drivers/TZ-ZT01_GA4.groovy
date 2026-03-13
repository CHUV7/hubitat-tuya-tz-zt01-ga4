/**
 *  Tuya TZ-ZT01_GA4 - Temperature & Humidity Sensor with External Probe
 *  Manufacturer: _TZE284_8se38w3c
 *
 *  DP map confirmed from live device logs (2026-03-12):
 *    - temperature       -> DP 1  (ambient, scaled /10)
 *    - humidity          -> DP 2  (raw integer, e.g. 39 = 39%)
 *    - battery_state     -> DP 3  (enum: 0=low, 1=medium, 2=high)
 *    - temperature_probe -> DP 38 (external probe, scaled /10)
 *
 *  Child devices (optional, each toggleable):
 *    - [name] - Ambient Temp  -> Generic Component Temperature Sensor
 *    - [name] - Probe Temp    -> Generic Component Temperature Sensor
 *    - [name] - Humidity      -> Generic Component Humidity Sensor
 *
 *  Author: Claude / Anthropic (custom driver)
 *  Version: 1.3.0
 *  Date: 2026-03-12
 */

import groovy.transform.Field

metadata {
    definition(
        name: "Tuya TZ-ZT01_GA4 Temp/Humidity + Probe",
        namespace: "custom",
        author: "Custom",
        importUrl: "https://raw.githubusercontent.com/CHUV7/hubitat-tuya-tz-zt01-ga4/refs/heads/main/drivers/TZ-ZT01_GA4.groovy"
    ) {
        capability "TemperatureMeasurement"
        capability "RelativeHumidityMeasurement"
        capability "Battery"
        capability "Refresh"
        capability "Sensor"
        capability "Configuration"

        attribute "temperatureProbe", "number"
        attribute "batteryState", "string"

        fingerprint profileId: "0104", endpointId: "01", inClusters: "0000,0004,0005,EF00",
                    outClusters: "0019,000A", model: "TS0601", manufacturer: "_TZE284_8se38w3c"
    }

    preferences {
        input name: "tempOffset",     type: "decimal", title: "Ambient Temperature Offset (°C)", defaultValue: 0.0
        input name: "probeOffset",    type: "decimal", title: "Probe Temperature Offset (°C)",   defaultValue: 0.0
        input name: "humidityOffset", type: "decimal", title: "Humidity Offset (%)",             defaultValue: 0.0
        input name: "tempUnit",       type: "enum",    title: "Temperature Unit",
                                      options: ["Celsius", "Fahrenheit"],                        defaultValue: "Celsius"
        input name: "createTempChild",  type: "bool", title: "Create child device for Ambient Temperature", defaultValue: true
        input name: "createProbeChild", type: "bool", title: "Create child device for Probe Temperature",   defaultValue: true
        input name: "createHumidChild", type: "bool", title: "Create child device for Humidity",            defaultValue: true
        input name: "logEnable", type: "bool", title: "Enable Debug Logging",           defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable Description Text Logging", defaultValue: true
    }
}

// ── Tuya DP map ──────────────────────────────────────────────────────────────
@Field static final Map TUYA_DP_MAP = [
    1 : "temperature",
    2 : "humidity",
    3 : "batteryState",
    38: "temperatureProbe"
]

@Field static final Map BATTERY_STATE_MAP = [
    0: "low",
    1: "medium",
    2: "high"
]

@Field static final Map BATTERY_PCT_MAP = [
    0: 10,
    1: 50,
    2: 100
]

// Child device suffixes -> Hubitat component driver and display label
@Field static final Map CHILD_CONFIG = [
    "temp"  : [driver: "Generic Component Temperature Sensor", label: "Ambient Temp"],
    "probe" : [driver: "Generic Component Temperature Sensor", label: "Probe Temp"],
    "humid" : [driver: "Generic Component Humidity Sensor",    label: "Humidity"]
]

// ── Lifecycle ────────────────────────────────────────────────────────────────
def installed() {
    log.info "${device.displayName} installed"
    initialize()
}

def updated() {
    log.info "${device.displayName} preferences updated"
    if (logEnable) runIn(86400, "logsOff")
    initialize()
    manageChildren()
}

def configure() {
    log.info "${device.displayName} configure()"
    manageChildren()
    return [zigbee.command(0xEF00, 0x03, "")]
}

def initialize() {
    sendEvent(name: "batteryState", value: "unknown")
}

def logsOff() {
    log.warn "${device.displayName} debug logging disabled after 24h"
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

// ── Child device management ───────────────────────────────────────────────────
private void manageChildren() {
    manageChild("temp",  createTempChild  != false)
    manageChild("probe", createProbeChild != false)
    manageChild("humid", createHumidChild != false)
}

private void manageChild(String suffix, boolean shouldExist) {
    def childDni = "${device.deviceNetworkId}-${suffix}"
    def existing = getChildDevice(childDni)
    def cfg      = CHILD_CONFIG[suffix]

    if (shouldExist && !existing) {
        log.info "${device.displayName} creating child device: ${cfg.label}"
        addChildDevice(
            "hubitat", cfg.driver, childDni,
            [name: "${device.displayName} - ${cfg.label}", isComponent: false]
        )
    } else if (!shouldExist && existing) {
        log.info "${device.displayName} removing child device: ${cfg.label}"
        deleteChildDevice(childDni)
    }
}

private void updateChild(String suffix, String eventName, def value, String unit, String descText) {
    def child = getChildDevice("${device.deviceNetworkId}-${suffix}")
    if (child) {
        child.sendEvent(name: eventName, value: value, unit: unit, descriptionText: descText)
    }
}

// ── Refresh ───────────────────────────────────────────────────────────────────
def refresh() {
    if (logEnable) log.debug "${device.displayName} refresh() - sending Tuya query command"
    return [zigbee.command(0xEF00, 0x03, "")]
}

// Called when Refresh is pressed on any child device
void componentRefresh(cd) {
    if (logEnable) log.debug "${device.displayName} componentRefresh() requested by ${cd?.displayName}"
    refresh()
}

// ── Parse ─────────────────────────────────────────────────────────────────────
def parse(String description) {
    if (logEnable) log.debug "${device.displayName} parse() raw: ${description}"

    def descMap = zigbee.parseDescriptionAsMap(description)

    if (descMap?.clusterInt != 0xEF00) {
        if (logEnable) log.debug "${device.displayName} ignoring non-EF00 cluster: ${descMap?.cluster}"
        return
    }

    if (descMap?.command != "01" && descMap?.command != "02") {
        if (logEnable) log.debug "${device.displayName} ignoring EF00 command: ${descMap?.command}"
        return
    }

    def data = descMap?.data
    if (!data || data.size() < 7) {
        if (logEnable) log.debug "${device.displayName} insufficient data length: ${data}"
        return
    }

    // Tuya EF00 frame: [seq_hi, seq_lo, dp, dp_type, len_hi, len_lo, value...]
    def dp     = Integer.parseInt(data[2], 16)
    def fncmd  = getTuyaAttributeValue(data, 4)
    def dpName = TUYA_DP_MAP[dp]

    if (logEnable) log.debug "${device.displayName} dp=${dp} value=${fncmd} -> ${dpName ?: 'UNKNOWN'}"

    switch (dpName) {
        case "temperature":
            handleTemperature(fncmd, "temperature", "Ambient temperature", "temp")
            break
        case "temperatureProbe":
            handleTemperature(fncmd, "temperatureProbe", "Probe temperature", "probe")
            break
        case "humidity":
            handleHumidity(fncmd)
            break
        case "batteryState":
            handleBatteryState(fncmd)
            break
        default:
            log.warn "${device.displayName} NOT PROCESSED dp=${dp} value=${fncmd} data=${data}"
            break
    }
}

// ── Handlers ──────────────────────────────────────────────────────────────────
private void handleTemperature(int raw, String attribute, String label, String childSuffix) {
    def offset      = (attribute == "temperatureProbe") ? (probeOffset ?: 0.0) : (tempOffset ?: 0.0)
    def tempC       = (raw / 10.0) + offset
    def displayTemp
    def unit

    if (tempUnit == "Fahrenheit") {
        displayTemp = Math.round((tempC * 9.0 / 5.0 + 32.0) * 10) / 10.0
        unit = "°F"
    } else {
        displayTemp = Math.round(tempC * 10) / 10.0
        unit = "°C"
    }

    def descText = "${label} is ${displayTemp}${unit}"
    if (txtEnable) log.info "${device.displayName} ${descText}"

    sendEvent(name: attribute, value: displayTemp, unit: unit, descriptionText: descText)
    updateChild(childSuffix, "temperature", displayTemp, unit, descText)
}

private void handleHumidity(int raw) {
    def offset   = humidityOffset ?: 0.0
    // DP 2 sends raw integer percentage (e.g. 39 = 39%), no /10 scaling needed
    def humidity = Math.round((raw + offset) * 10) / 10.0
    humidity     = Math.max(0.0, Math.min(100.0, humidity))

    def descText = "Humidity is ${humidity}%"
    if (txtEnable) log.info "${device.displayName} ${descText}"

    sendEvent(name: "humidity", value: humidity, unit: "%", descriptionText: descText)
    updateChild("humid", "humidity", humidity, "%", descText)
}

private void handleBatteryState(int raw) {
    def stateStr = BATTERY_STATE_MAP[raw] ?: "unknown"
    def pct      = BATTERY_PCT_MAP[raw]   ?: 0

    if (txtEnable) log.info "${device.displayName} battery state is ${stateStr} (~${pct}%)"
    sendEvent(name: "batteryState", value: stateStr, descriptionText: "Battery state is ${stateStr}")
    sendEvent(name: "battery",      value: pct,      unit: "%", descriptionText: "Battery is ~${pct}%")
}

// ── Helpers ───────────────────────────────────────────────────────────────────
private int getTuyaAttributeValue(List<String> data, int startIndex) {
    int lenHi  = Integer.parseInt(data[startIndex],     16)
    int lenLo  = Integer.parseInt(data[startIndex + 1], 16)
    int length = (lenHi << 8) | lenLo

    int value = 0
    for (int i = 0; i < length; i++) {
        value = (value << 8) | Integer.parseInt(data[startIndex + 2 + i], 16)
    }
    return value
}
