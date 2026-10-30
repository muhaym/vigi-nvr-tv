package com.fantacode.vigitv;

import android.app.*;
import android.graphics.Color;
import android.util.Base64;
import android.view.*;
import android.widget.*;
import java.security.cert.X509Certificate;
import java.util.*;

/** Explicit certificate trust followed by one login attempt. Never changes recorder settings. */
final class RecorderSetup {
  interface Result {
    boolean connected(RecorderProfile profile, String username, String password);
  }

  final Activity owner;
  final Result result;
  final RecorderProfile previous;
  EditText host, label, user, password, count, https, rtsp, talk;
  TextView status;
  AlertDialog dialog, trustDialog;
  boolean busy;

  RecorderSetup(Activity activity, RecorderProfile profile, String username, Result callback) {
    owner = activity;
    previous = profile;
    result = callback;
    LinearLayout form = new LinearLayout(owner);
    form.setOrientation(LinearLayout.VERTICAL);
    form.setPadding(dp(24), dp(8), dp(24), dp(16));
    TextView intro = new TextView(owner);
    intro.setText(
        "Connect to your recorder on the local network. Camera connections stay behind the"
            + " recorder.");
    intro.setTextColor(Color.LTGRAY);
    form.addView(intro);
    label = field(form, "Recorder name", profile.label, false);
    host = field(form, "Recorder IP or hostname", profile.host, false);
    user = field(form, "Username", username, false);
    password = field(form, "Password", "", true);
    count = field(form, "Camera count (manual fallback, 1–96)", "", false);
    count.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    TextView advanced = new TextView(owner);
    advanced.setText("Advanced connection options");
    advanced.setTextColor(Color.LTGRAY);
    advanced.setPadding(0, dp(12), 0, dp(12));
    advanced.setFocusable(true);
    form.addView(advanced);
    LinearLayout ports = new LinearLayout(owner);
    form.addView(ports);
    https = port(ports, "HTTPS", profile.ports.https);
    rtsp = port(ports, "Video RTSP", profile.ports.rtsp);
    talk = port(ports, "Native talk", profile.ports.talk);
    ports.setVisibility(
        profile.ports.https == 443 && profile.ports.rtsp == 554 && profile.ports.talk == 8443
            ? View.GONE
            : View.VISIBLE);
    advanced.setOnClickListener(
        v -> ports.setVisibility(ports.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
    status = new TextView(owner);
    status.setTextColor(Color.LTGRAY);
    form.addView(status);
    ScrollView scroll = new ScrollView(owner);
    scroll.setFillViewport(true);
    scroll.addView(form);
    dialog =
        new AlertDialog.Builder(owner)
            .setTitle("Recorder setup")
            .setView(scroll)
            .setPositiveButton("Connect", null)
            .setNegativeButton("Cancel", null)
            .create();
    dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> inspect()));
    dialog.show();
  }

  void close() {
    password.setText("");
    if (trustDialog != null) trustDialog.dismiss();
    dialog.dismiss();
  }

  int dp(int n) {
    return Math.round(n * owner.getResources().getDisplayMetrics().density);
  }

  EditText field(LinearLayout parent, String hint, String initial, boolean secret) {
    EditText view = new EditText(owner);
    view.setHint(hint);
    view.setText(initial);
    view.setSingleLine();
    view.setInputType(secret ? 129 : 1);
    parent.addView(view);
    return view;
  }

  EditText port(LinearLayout row, String name, int value) {
    LinearLayout column = new LinearLayout(owner);
    column.setOrientation(LinearLayout.VERTICAL);
    row.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
    TextView label = new TextView(owner);
    label.setText(name);
    label.setTextColor(Color.LTGRAY);
    column.addView(label);
    EditText field = new EditText(owner);
    field.setContentDescription(name + " port");
    field.setText(Integer.toString(value));
    field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    field.setSingleLine();
    column.addView(field);
    return field;
  }

  boolean alive() {
    return !owner.isFinishing()
        && !owner.isDestroyed()
        && dialog.isShowing()
        && AppLock.allowed(owner);
  }

  void busy(boolean value, String text) {
    busy = value;
    if (dialog.isShowing()) {
      dialog.getButton(-1).setEnabled(!value);
      status.setText(text);
    }
  }

  void inspect() {
    if (busy) return;
    final String address;
    final int manual;
    final RecorderPorts ports;
    try {
      address = RecorderProfile.validHost(host.getText().toString());
      ports =
          new RecorderPorts(
              Integer.parseInt(https.getText().toString()),
              Integer.parseInt(rtsp.getText().toString()),
              Integer.parseInt(talk.getText().toString()));
      String entered = count.getText().toString();
      manual = entered.isEmpty() ? 0 : Integer.parseInt(entered);
      if ((!entered.isEmpty() && manual < 1) || manual > RecorderProfile.MAX_CHANNELS)
        throw new IllegalArgumentException("Camera count must be 1–96");
    } catch (Exception bad) {
      status.setText(bad.getMessage());
      return;
    }
    if (user.getText().toString().trim().isEmpty() || password.getText().toString().isEmpty()) {
      status.setText("Enter your recorder username and password");
      return;
    }
    busy(true, "Checking recorder identity…");
    new Thread(
            () -> {
              try {
                X509Certificate certificate = RecorderIdentity.inspect(address, ports.https);
                String encoded = Base64.encodeToString(certificate.getEncoded(), Base64.NO_WRAP),
                    fingerprint = RecorderIdentity.fingerprint(certificate);
                owner.runOnUiThread(
                    () -> {
                      if (!alive()) return;
                      if (address.equals(previous.host) && encoded.equals(previous.certificate)) {
                        connect(address, certificate, manual, ports);
                        return;
                      }
                      busy(false, "");
                      AlertDialog trust =
                          new AlertDialog.Builder(owner)
                              .setTitle(
                                  previous.host.equals(address) && !previous.certificate.isEmpty()
                                      ? "Recorder identity changed"
                                      : "Trust this recorder?")
                              .setMessage(
                                  address
                                      + "\n\nSHA-256 certificate fingerprint\n"
                                      + fingerprint
                                      + "\n\n"
                                      + "Compare this fingerprint with your recorder administrator"
                                      + " before continuing. Credentials are sent only after you"
                                      + " choose Trust.")
                              .setPositiveButton(
                                  "Trust and connect",
                                  (d, w) -> {
                                    if (alive()) connect(address, certificate, manual, ports);
                                  })
                              .setNegativeButton("Cancel", null)
                              .create();
                      trustDialog = trust;
                      trust.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                      trust.show();
                    });
              } catch (Exception error) {
                owner.runOnUiThread(
                    () -> {
                      if (alive())
                        busy(
                            false,
                            "Could not verify recorder identity. Check its address, HTTPS service"
                                + " and certificate dates.");
                    });
              }
            },
            "recorder-identity")
        .start();
  }

  void connect(String address, X509Certificate certificate, int manual, RecorderPorts ports) {
    if (!alive()) return;
    final String username = user.getText().toString().trim(),
        secret = password.getText().toString(),
        name = label.getText().toString().trim();
    busy(true, "Signing in and finding cameras…");
    new Thread(
            () -> {
              try (NvrWebControl api =
                  new NvrWebControl(address, ports.https, username, secret, certificate)) {
                api.login();
                RecorderProfile profile = new RecorderProfile();
                profile.host = address;
                profile.ports = ports;
                profile.label = name.isEmpty() ? "Recorder" : name;
                profile.certificate =
                    Base64.encodeToString(certificate.getEncoded(), Base64.NO_WRAP);
                try {
                  profile.cameras.putAll(CameraDiscovery.parse(api.cameraList()));
                } catch (Exception unsupported) {
                  if (manual == 0) throw unsupported;
                  for (int id = 1; id <= manual; id++)
                    profile.cameras.put(
                        id, new RecorderProfile.Camera(id, "Camera " + id, -1, false));
                }
                if (address.equals(previous.host)) {
                  profile.combined = previous.combined;
                  profile.rows = previous.rows;
                  profile.columns = previous.columns;
                  for (RecorderProfile.Camera camera : profile.cameras.values()) {
                    RecorderProfile.Camera old = previous.cameras.get(camera.channel);
                    if (old != null) {
                      camera.cell = old.cell;
                      camera.lens = old.lens;
                    }
                  }
                }
                owner.runOnUiThread(
                    () -> {
                      if (!alive()) return;
                      if (result.connected(profile, username, secret)) {
                        password.setText("");
                        dialog.dismiss();
                      } else {
                        busy(
                            false,
                            "Could not securely save the connection. Your previous setup was"
                                + " preserved.");
                      }
                    });
              } catch (Exception error) {
                owner.runOnUiThread(
                    () -> {
                      if (alive())
                        busy(
                            false,
                            error instanceof java.io.IOException
                                ? error.getMessage()
                                : "Recorder setup failed. Check the account and supported"
                                    + " firmware.");
                    });
              }
            },
            "recorder-setup")
        .start();
  }
}
