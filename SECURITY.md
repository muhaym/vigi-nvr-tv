# Security and privacy

This app connects to a user-selected recorder. It never connects to camera IP addresses, changes router settings, or uploads audio / video / credentials to a cloud service. There are no analytics or crash-upload SDKs.

## Trust and authentication

Recorder setup reads a public HTTPS certificate with a deliberately rejected inspection handshake. No password is sent during inspection. The owner must compare and explicitly accept a new or changed fingerprint. Subsequent control connections require the exact accepted certificate, valid dates and the configured host. Do not blindly approve a changed fingerprint. HTTPS control and native talk default to ports 443 and 8443 (configurable in setup) and the same accepted certificate; recorders with different certificates per service need additional support.

The account is AES-GCM encrypted with a device-local Android Keystore key. It is not exported, and Android backup is disabled. Certificate pins and camera labels are local app data. Uninstalling or clearing storage removes the setup and may require reconnection.

**Video uses RTSP over TCP on port 554 by default (configurable); it is not encrypted video transport.** The Android cleartext-HTTP restriction does not make native LibVLC RTSP encrypted. Use a trusted LAN or VPN. Credentials are passed to LibVLC in memory; do not collect unredacted native-library diagnostics or memory dumps. RTSPS remains future work.

## Microphone

Only an explicitly selected Start / held Talk action opens a USB input. Opening the panel does not record. Local mic tests send no network audio. Talk stops on dismissal, app departure, window-focus loss, invalid authorization or USB route change, with session-duration limits. The remote's voice-search microphone is not promised to ordinary apps; a working USB microphone is required.

App access is a local viewing deterrent, not protection from a privileged device administrator, rooted TV or authorized ADB operator. Hardware speech and echo behavior depends on both recorder and audio hardware. No microphone bypass or privileged remote-mic access is attempted.

## Authenticator access

Fresh installations enroll an RFC 6238 authenticator (HMAC-SHA1, six digits, 30-second period, random 160-bit secret) entirely offline. Existing patterns remain in place until the owner verifies current access, verifies a code from the new authenticator and acknowledges saving a random 120-bit recovery code. Activation atomically replaces the secret, recovery verifier and consumed counter and removes the old pattern verifier. There is no pattern fallback after activation.

The authenticator secret is AES-GCM encrypted with a non-exportable Android Keystore key and authenticated context. Recovery is stored only as a Keystore-keyed HMAC verifier. Enrollment material is never logged, exported in intents, backed up or sent over a network. Pending enrollment is discarded on pause or after five minutes. Setup and recovery screens block screenshots. Anyone who scans the setup QR or obtains the manual setup key can generate codes; enroll privately.

Verification accepts the current counter and one adjacent counter in either direction, requires a counter newer than the persisted last successful counter, and records consumption before unlocking. Five failed OTP/recovery attempts cause a 30-second delay, increasing to at most 15 minutes for repeated batches; both paths share persistent throttling. Leaving the app, screen-off and background locking preserve their existing behavior. Keep phone and TV clocks automatic: a rollback cannot reopen consumed time windows and may require clock correction or recovery. Clock manipulation or preference rollback by a privileged operator is outside this local lock's threat model.

Recovery authorizes replacement enrollment, never direct camera access. The old recovery code is retired only when the replacement is committed, so interrupted setup does not destroy the remaining recovery path. If both the authenticator and recovery code are lost, clear app storage and set up the recorder again; this removes saved recorder credentials and groups. A damaged encryption key fails closed and may also require this reset.

Authenticator codes are a single possession factor, not passkeys or phishing-resistant MFA. Masked entry and consumed-counter rejection reduce shoulder-surfing reuse but do not prevent live observation/racing or compromise of the phone. Multiple people sharing the same authenticator seed cannot be revoked individually: replace the setup to revoke all copies. There is no per-person access audit or account service.

## Manual camera alarms

Opening Alarm only reads the selected camera's capabilities. An explicit, confirmed Start sends one request through the pinned recorder connection; it is never automatically retried. A stop is requested after ten seconds, on Stop, or when leaving the panel / app. Stop requests get up to three attempts. The selected channel is captured for the entire operation. This changes manual output only, not automatic detection schedules or recording.

The ten-second timer runs in the app, not as a proven firmware-enforced timeout. Network failure, process termination or TV power loss can prevent delivery of Stop. A pending-stop flag is saved before sending Start and survives restart. Unconfirmed stops block further app alarm starts and surface a warning; open the affected camera's Alarm panel and choose Stop, or stop it through the recorder. Reopening a panel never automatically starts or stops another operator's alarm.

## Screenshots

Camera views and control panels allow screenshots and screen sharing. Authenticator enrollment / entry / recovery, the legacy pattern and recorder-credential screens retain Android screenshot blocking. Treat any camera screenshots as private footage; never commit them or include them in public issues.

## Reporting

Do not post credentials, camera images, office layouts, certificates identifying private sites, private keys, or unredacted logs in public issues. Report vulnerabilities privately using [GitHub security advisories](https://github.com/muhaym/vigi-nvr-tv/security/advisories/new). Do not disclose exploitable details or private recorder information in public issues.
