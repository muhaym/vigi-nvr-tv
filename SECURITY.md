# Security and privacy

This app connects to a user-selected recorder. It never connects to camera IP addresses, changes router settings, or uploads audio / video / credentials to a cloud service. There are no analytics or crash-upload SDKs.

## Trust and authentication

Recorder setup reads a public HTTPS certificate with a deliberately rejected inspection handshake. No password is sent during inspection. The owner must compare and explicitly accept a new or changed fingerprint. Subsequent control connections require the exact accepted certificate, valid dates and the configured host. Do not blindly approve a changed fingerprint. HTTPS control and native talk default to ports 443 and 8443 (configurable in setup) and the same accepted certificate; recorders with different certificates per service need additional support.

The account is AES-GCM encrypted with a device-local Android Keystore key. It is not exported, and Android backup is disabled. Certificate pins and camera labels are local app data. Uninstalling or clearing storage removes the setup and may require reconnection.

**Video uses RTSP over TCP on port 554 by default (configurable); it is not encrypted video transport.** The Android cleartext-HTTP restriction does not make native LibVLC RTSP encrypted. Use a trusted LAN or VPN. Credentials are passed to LibVLC in memory; do not collect unredacted native-library diagnostics or memory dumps. RTSPS remains future work.

## Microphone

Only an explicitly selected Start / held Talk action opens a USB input. Opening the panel does not record. Local mic tests send no network audio. Talk stops on dismissal, app departure, window-focus loss, invalid authorization or USB route change, with session-duration limits. The remote's voice-search microphone is not promised to ordinary apps; a working USB microphone is required.

The pattern is a local viewing deterrent, not protection from a privileged device administrator, rooted TV or authorized ADB operator. Hardware speech and echo behavior depends on both recorder and audio hardware. No microphone bypass or privileged remote-mic access is attempted.

## Manual camera alarms

Opening Alarm only reads the selected camera's capabilities. An explicit, confirmed Start sends one request through the pinned recorder connection; it is never automatically retried. A stop is requested after ten seconds, on Stop, or when leaving the panel / app. Stop requests get up to three attempts. The selected channel is captured for the entire operation. This changes manual output only, not automatic detection schedules or recording.

The ten-second timer runs in the app, not as a proven firmware-enforced timeout. Network failure, process termination or TV power loss can prevent delivery of Stop. A pending-stop flag is saved before sending Start and survives restart. Unconfirmed stops block further app alarm starts and surface a warning; open the affected camera's Alarm panel and choose Stop, or stop it through the recorder. Reopening a panel never automatically starts or stops another operator's alarm.

## Reporting

Do not post credentials, camera images, office layouts, certificates identifying private sites, private keys, or unredacted logs in public issues. Report vulnerabilities privately using [GitHub security advisories](https://github.com/muhaym/vigi-nvr-tv/security/advisories/new). Do not disclose exploitable details or private recorder information in public issues.
