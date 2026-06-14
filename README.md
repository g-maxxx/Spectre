![Spectre Banner](banner.png)

Spectre shows the radio environment around you in one place: the cellular towers your phone can see, nearby WiFi access points, Bluetooth LE devices, and the GNSS satellites overhead. It also computes a single live "RF exposure" figure by power-summing everything it receives, all on-device.

## Installation

Requires **Android 12** (API 31) or later.

1. Download the APK from the [github release](https://github.com/thomasbuilds/Spectre/releases/latest).
2. Optionally verify the APK's signing certificate against the [`CERT.sha256`](CERT.sha256) fingerprint on the release.
3. Open the file on your phone and allow installation from unknown sources when Android prompts you.
4. Launch Spectre and grant the permissions it requests.

## Features

- **Live RF exposure gauge.** A single number for the total received RF power across cellular, WiFi, and Bluetooth, colored by intensity.
- **Cellular.** 5G, 4G, 3G, and 2G cells with operator, cell identifiers, bands, reference measurements (RSRP/RSRQ/SINR and the 3G/2G equivalents), and a timing-advance distance estimate where available. Dual-SIM aware.
- **WiFi.** Access points across the 2.4, 5, and 6 GHz bands with security (WPA, WPA2, WPA3, OWE, WEP, Open), WPS and management-frame-protection state, channel and width, and a distance estimate. Live signal strength for the network you are connected to, and true ranging over 802.11mc FTM where the hardware supports it.
- **Bluetooth LE.** Advertising devices with name, address, signal strength, address type, PHY, service UUIDs, and decoded manufacturer data (Apple, Google, Microsoft, and others), including iBeacon fields. A GATT inspector can connect to a device, enumerate its services and characteristics, read their values, and write to writable ones.
- **GNSS.** Multi-constellation tracking (GPS, GLONASS, Galileo, BeiDou, QZSS, NavIC, SBAS) with dual-frequency support, carrier-to-noise, elevation and azimuth, used-in-fix status, and a computed sub-satellite ground point for each satellite.
- **Local-network recon.** Host discovery through TCP-connect probes on common ports, with banner grabbing and reverse DNS, alongside mDNS / DNS-SD service discovery and SSDP / UPnP discovery, all limited to the LAN you are connected to.
- **Active BLE tooling.** An iBeacon broadcaster with configurable UUID, major, minor, and measured power, plus the GATT read/write inspector above. These are intended for your own devices and authorized testing.

## Android limitations

**Signal strength is a reference measurement, not total power.** Modems report RSRP (4G/5G) or RSCP (3G), the power of a single reference element, not the power of the whole channel. Spectre reconstructs a wideband estimate per network type. LTE uses the modem's own RSSI when it exposes one, otherwise a bandwidth-derived offset. NR (5G) uses a fixed offset because Android does not expose the NR channel bandwidth at all. WCDMA derives it from Ec/No, and GSM is already a total-power figure. The result is usually within a few dB of the true value.

**Powers cannot be added in dBm.** dBm is logarithmic, so per-emitter strengths cannot meaningfully be summed. Spectre converts each to linear power (milliwatts), adds them, and converts back, so the exposure figure is dominated by the strongest emitters, which is the physically correct result for total received power. Cellular readings are first normalized to the wideband-equivalent figure above, so they are comparable to WiFi and Bluetooth.

**5G NSA is invisible.** On most 5G networks the phone rides on a 4G anchor cell and adds a 5G carrier on top, and Android exposes only the 4G anchor to apps. The strength shown for a connected 5G NSA cell is therefore the anchor's, not the 5G signal carrying the data. Only standalone 5G (5G SA) returns a real 5G reading. This cannot be worked around, so it is disclosed clearly in the app.

**Only your carrier's cells appear.** The modem decodes only the spectrum your SIM's network and its roaming partners use, so cells from other carriers never show up. Android's telephony APIs also report a single subscription by default, so Spectre monitors every active SIM to cover both carriers on a dual-SIM phone.

**WiFi scan throttling.** Android limits an app to roughly four WiFi scans every two minutes. Spectre paces itself to about one scan every 30 seconds while throttling is on, and faster once it is disabled in Developer options. It also consumes the results of system-initiated scans, so the list keeps updating even when its own scans are throttled.

**Distance is not handed to you.** Android gives no distance to most emitters. Spectre uses true ranging where it exists (WiFi 802.11mc FTM, cellular timing advance) and a calibrated path-loss model otherwise, drawing on the iBeacon measured-power field when present, and labels every estimate with its confidence so you know which is which.

## Sponsor

Spectre is free and ad-free. Donations toward its continued development are very welcome.

- Bitcoin: `bc1qphgd8leqsf06qlm6jjxpuw2rtq9f9hdjnhfluh`
- Monero: `85fzziWM1pH77HWHNhE6aAN9uaHrLL7CMFA72rVecmMDR1fUjfv9YmS6GGeiV3hDEn7e9d8v4hfMSRmmEp171fpR4nipNfZ`
