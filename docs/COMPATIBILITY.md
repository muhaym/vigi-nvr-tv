# Compatibility

## Tested hardware

| Component | Evidence |
| --- | --- |
| VIGI NVR4032H(UN) v1.0, firmware 1.7.0 Build 260716 Rel.7097n | Live, groups, playback / scrubbing, USB talk, two-way conversation, manual siren and light confirmed by the owner |
| Haier MatrixTV BE, Android 14, 32-bit ARM | Existing installation and remote navigation tested; beta fresh-install onboarding is tracked separately in release validation |
| Jabra SPEAK 510 over USB | Speech recording and camera-speaker output tested |
| Fixed and optical-lens cameras behind the recorder | Digital zoom and optical control directions confirmed; no motor pan / tilt claim |

These results apply to the tested development line. A core refactor passing JVM tests does not replace fresh-install or physical-device validation.

## Broader VIGI NVR support

The data model and protocol channel validation support **1–96** channels. Automated tests exercise 4, 8, 16, 32, 64 and 96-channel discovery / profiles and routing boundaries. Ports are configurable; the defaults are HTTPS 443, RTSP 554 and native talk 8443. Other model / firmware / account combinations are **untested**, even when they expose similar interfaces. Different certificates per HTTPS / talk service are not currently supported.

Live and playback use the documented VIGI NVR RTSP path family. Web discovery / lens / manual alarms and native talk depend on the implemented firmware interfaces and permissions. The web login currently requires the advertised RSA/SHA256 method; it deliberately does not guess passwords or fall back to older encryption. OpenAPI is not required by this implementation.

The recorder must expose the requested camera as a channel. Compatible VIGI, Tapo or third-party cameras added to a VIGI recorder may be viewable through that channel; this is not certification of every attached camera or a promise of all its controls. Standalone cameras and other NVR brands are outside the current connection flow.

## Feature conditions

| Feature | Required support |
| --- | --- |
| Live video | NVR RTSP enabled, compatible authentication and codec, account permission |
| Playback | Available recordings, permission and matching NVR replay path |
| Listen | Camera audio stream enabled and available through NVR |
| Talk / conversation | Camera speaker, compatible native NVR talk service and a usable USB microphone |
| Optical zoom | Motorised optical lens reported by recorder; fixed lenses only get digital zoom |
| Siren / light | Recorder reports the camera's corresponding manual outputs |
| 8 / 16-camera layouts | Enabled recorder Channel-Zero preview and explicit app mapping |

Four individual streams are a resource ceiling, not a guarantee of four hardware decoders on every TV. Large recorders are paged; the app does not decode 96 camera streams simultaneously. Combined preview currently accepts up to an 8 × 8 source mapping, and requires a mapped cell for every configured camera. Larger installations can use individual-stream pages; larger composite mappings need separate support.

## Documented limitations and sources

TP-Link documents that SRTP-enabled streams cannot be consumed by third-party clients; this app does not implement that proprietary encrypted video path. Talk here uses a tested native TLS protocol, not the different RTSP talk workflow documented for OpenAPI. Do not assume those are interchangeable.

- [Official VIGI NVR RTSP setup and stream formats](https://www.tp-link.com/uk/support/faq/4677/)
- [Official NVR OpenAPI stream / talk documentation](https://www.tp-link.com/uk/support/faq/5223/)
- [Official Tapo RTSP / ONVIF support and model restrictions](https://www.tp-link.com/us/support/faq/2680/)
- [TP-Link VIGI product guide, including 64 / 96-channel families](https://static.tp-link.com/upload/smb/2024/202401/20240124/2024%20VIGI%20Product%20Guide_English.pdf)

To report a new compatible device, use the repository's compatibility report template with model, hardware revision, firmware, TV / Android version, role and separately observed feature results. Never include credentials, camera IPs, images or unredacted logs.
