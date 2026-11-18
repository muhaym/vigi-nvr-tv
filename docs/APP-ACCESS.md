# Offline authenticator access

App access is independent of the recorder login. It needs no account service, hosted web page, companion app or network connection. A standard TOTP authenticator generates six-digit codes for this TV. This is code entry, not scanning a fresh QR for each unlock or using a passkey.

## Existing pattern users

1. On the pattern screen, select **Switch to authenticator**, then verify your existing private pattern. Alternatively, open **Workspace → App access · authenticator** after unlocking.
2. Make sure the room is private. In your authenticator, add an account by scanning the QR. **Show setup key** provides the same secret for manual entry. Never post either in an issue, chat or screenshot.
3. Choose **Enter code**, then enter the six-digit code from the new account. The sixth digit submits automatically. Entry is masked; use the remote keypad or hardware number keys. Delete corrects input and Back clears it.
4. Save the displayed recovery code in a password manager or another private place away from the TV, then choose **Saved · Enable authenticator**. Only this final step replaces your pattern. Recorder credentials, camera groups and overview mapping are unchanged.

Fresh installations start directly at authenticator setup. There is no preset or public setup secret. Each attempt generates a random key; restarting an unfinished setup means removing its unused account from the phone and scanning the new QR. Leaving the app discards unfinished setup. After five minutes setup expires and existing users must verify access again.

## Daily use

Open your authenticator and enter the current code for this TV. A successfully used time window is consumed; if you immediately lock and reopen, wait for the next code. Codes are not a reusable PIN. Keep automatic time enabled on phone and TV. There is a small tolerance for clock differences, not an unlimited correction window.

After five failed attempts, both normal codes and recovery share a delay, increasing from 30 seconds to at most 15 minutes. Closing and reopening the app does not reset it. App backgrounding and screen-off still relock the camera view.

## Replacing or losing a phone

**Workspace → App access · authenticator** requires a fresh code before displaying a replacement setup. Replacement invalidates all copies of the previous authenticator key and the old recovery code. There is no pattern fallback or way to redisplay the current secret.

If the phone is lost, choose **Use recovery code** on the lock screen. Enter the saved 24-character recovery code; spaces, hyphens and letter case are ignored. Recovery leads to replacement setup, not directly to cameras. It is retired when the replacement is completed; interruption preserves the old recovery path. Save the new recovery code before activating the replacement.

If neither method is available, Android TV settings can clear this app's storage. This also removes recorder sign-in, groups and mappings; the recorder credentials are needed to set up again. Encryption-key damage may require the same reset.

## Sharing access and limits

Multiple approved people may scan the initial QR privately. They share one secret and cannot be revoked individually: replace enrollment to revoke all copies, then privately enroll the intended users again. There are no individual roles or access records in this local implementation.

The setup QR is a permanent secret until replaced; observing or copying it compromises access. Ordinary codes expire and consumed counters are rejected, but codes are not phishing-resistant and live observation/racing is still possible. This local lock does not protect against a privileged TV administrator, root access or authorized debugging. See [security details](../SECURITY.md).
