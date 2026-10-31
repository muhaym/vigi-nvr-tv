# TP-Link account and cloud access

Status reviewed 31 October 2026. Cloud support is not implemented in this app.

## What works now

A recorder bound to a TP-Link ID can still be connected by its reachable address using its recorder account, on LAN or VPN. Binding does not turn that address into a cloud endpoint. The current app does not receive or store TP-Link account credentials.

## Official sources

- [NVR remote management](https://www.tp-link.com/us/user-guides/vigi-network-video-recorder/chapter-7-network-management.html) documents TP-Link ID binding and remote use in the official VIGI app / Security Manager.
- [NVR OpenAPI guide](https://www.tp-link.com/us/support/faq/4797/) documents authentication against the recorder's own IP and OpenAPI port. It does not document account-bound device listing or a cloud relay integration.
- [Cloud VMS FAQ](https://www.tp-link.com/nl/support/faq/4176/) documents TP-Link ID sign-in and cloud relay viewing, including a five-minute idle viewing limit. Cloud VMS is a separate product surface; do not assume its limits describe every official mobile-app connection.

The public documentation reviewed did not establish a supported third-party account authentication, device listing and cloud media interface. This is a research finding, not proof that TP-Link has no partner SDK.

## Implementation prerequisites

Obtain the applicable TP-Link integration documentation / SDK and redistribution terms, covering account login and regional endpoints, MFA / refresh / revocation, owned and shared bound devices, and authenticated media transport (direct traversal or relay). A successful account login alone is insufficient.

A cloud provider would need a distinct session / transport implementation. The current recorder-address certificate pinning and RTSP URL builder cannot simply be pointed at a TP-Link account endpoint. Keep account secrets out of logs, camera URLs and exported profiles; prefer delegated browser or device-code sign-in if TP-Link supports it. No client secret from an official app should be embedded in this open-source project.

Verify live video, recordings, reconnect / expiry, permission revocation, unavailable devices and supported controls separately. Cloud viewing limits must be surfaced rather than silently bypassed. Until this is implemented and tested, onboarding offers the working LAN / VPN connection path and makes no cloud-login claim.
