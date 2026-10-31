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
  TextView intro, advanced;
  LinearLayout addressPage, loginPage, options;
  AlertDialog dialog, trustDialog;
  boolean busy;
  X509Certificate accepted;
  String checkedAddress;
  RecorderPorts checkedPorts;
  int checkedManual;

  RecorderSetup(Activity activity, RecorderProfile profile, String username, Result callback) {
    owner = activity;
    previous = profile;
    result = callback;
    LinearLayout form = new LinearLayout(owner);
    form.setOrientation(LinearLayout.VERTICAL);
    form.setPadding(dp(24), dp(8), dp(24), dp(16));
    intro = new TextView(owner);
    intro.setTextColor(Color.LTGRAY);
    form.addView(intro);
    intro.setPadding(0, 0, 0, dp(12));
    addressPage = column(form);
    host = field(addressPage, "Recorder IP or hostname", profile.host, false);
    host.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
    host.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
    host.setOnEditorActionListener(
        (v, action, event) -> {
          if (action == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT) {
            submit();
            return true;
          }
          return false;
        });
    loginPage = column(form);
    user = field(loginPage, "Recorder username", username, false);
    password = field(loginPage, "Recorder password", "", true);
    password.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
    password.setOnEditorActionListener(
        (v, action, event) -> {
          if (action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
            submit();
            return true;
          }
          return false;
        });
    advanced = new TextView(owner);
    advanced.setTextColor(Color.LTGRAY);
    advanced.setPadding(0, dp(12), 0, dp(12));
    advanced.setMinHeight(dp(44));
    advanced.setFocusable(true);
    advanced.setFocusableInTouchMode(true);
    ControlTray.focus(advanced, false);
    advanced.setOnFocusChangeListener((v, focused) -> ControlTray.focus(advanced, focused));
    form.addView(advanced);
    options = column(form);
    label = field(options, "Recorder name (optional)", profile.label, false);
    count = field(options, "Camera count — only if discovery fails (1–96)", "", false);
    count.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    LinearLayout ports = new LinearLayout(owner);
    options.addView(ports);
    https = port(ports, "HTTPS", profile.ports.https);
    rtsp = port(ports, "Video RTSP", profile.ports.rtsp);
    talk = port(ports, "Native talk", profile.ports.talk);
    options.setVisibility(
        profile.ports.https == 443 && profile.ports.rtsp == 554 && profile.ports.talk == 8443
            ? View.GONE
            : View.VISIBLE);
    advanced.setOnClickListener(
        v -> {
          options.setVisibility(options.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
          advanced.setText(
              options.getVisibility() == View.VISIBLE
                  ? "Hide advanced options"
                  : "Advanced options");
        });
    status = new TextView(owner);
    status.setTextColor(Color.LTGRAY);
    form.addView(status);
    ScrollView scroll = new ScrollView(owner);
    scroll.setFillViewport(true);
    scroll.addView(form);
    dialog =
        new AlertDialog.Builder(owner)
            .setTitle("Connect your recorder")
            .setView(scroll)
            .setPositiveButton("Continue", null)
            .setNegativeButton("Cancel", null)
            .create();
    dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    dialog.setOnShowListener(
        d -> {
          dialog.getButton(-1).setOnClickListener(v -> submit());
          dialog
              .getButton(-2)
              .setOnClickListener(
                  v -> {
                    if (accepted == null) close();
                    else showAddress();
                  });
          showAddress();
        });
    dialog.setOnDismissListener(
        d -> {
          password.setText("");
          if (trustDialog != null) trustDialog.dismiss();
        });
    dialog.setOnKeyListener(
        (d, key, event) -> {
          if (key == KeyEvent.KEYCODE_BACK && accepted != null && !busy) {
            if (event.getAction() == KeyEvent.ACTION_UP) showAddress();
            return true;
          }
          return false;
        });
    dialog.show();
    dialog.getWindow().setLayout(dp(620), -2);
  }

  LinearLayout column(LinearLayout parent) {
    LinearLayout view = new LinearLayout(owner);
    view.setOrientation(LinearLayout.VERTICAL);
    parent.addView(view);
    return view;
  }

  void showAddress() {
    accepted = null;
    password.setText("");
    addressPage.setVisibility(View.VISIBLE);
    loginPage.setVisibility(View.GONE);
    advanced.setVisibility(View.VISIBLE);
    advanced.setText(
        options.getVisibility() == View.VISIBLE ? "Hide advanced options" : "Advanced options");
    intro.setText(
        "1 of 2 · Find your recorder\nUse its address on the same network or through a VPN.");
    dialog.getButton(-1).setText("Continue");
    dialog.getButton(-2).setText("Cancel");
    busy(false, "");
    host.requestFocus();
  }

  void showLogin(String address, X509Certificate certificate, int manual, RecorderPorts ports) {
    checkedAddress = address;
    checkedManual = manual;
    checkedPorts = ports;
    accepted = certificate;
    addressPage.setVisibility(View.GONE);
    options.setVisibility(View.GONE);
    advanced.setVisibility(View.GONE);
    loginPage.setVisibility(View.VISIBLE);
    intro.setText(
        "2 of 2 · Sign in to "
            + address
            + "\nUse your recorder account, not your TP-Link ID. Cameras are found automatically.");
    dialog.getButton(-1).setText("Connect");
    dialog.getButton(-2).setText("Back");
    busy(false, "Recorder identity verified");
    (user.getText().toString().isEmpty() ? user : password).requestFocus();
  }

  void submit() {
    if (busy) return;
    ((android.view.inputmethod.InputMethodManager)
            owner.getSystemService(Activity.INPUT_METHOD_SERVICE))
        .hideSoftInputFromWindow(password.getWindowToken(), 0);
    if (accepted == null) inspect();
    else if (user.getText().toString().trim().isEmpty()
        || password.getText().toString().isEmpty()) {
      status.setText("Enter your recorder username and password");
      (user.getText().toString().trim().isEmpty() ? user : password).requestFocus();
    } else connect(checkedAddress, accepted, checkedManual, checkedPorts);
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
    view.setContentDescription(hint);
    view.setText(initial);
    view.setSingleLine();
    view.setMinHeight(dp(48));
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
      dialog.getButton(-2).setEnabled(!value);
      for (EditText input : new EditText[] {host, label, user, password, count, https, rtsp, talk})
        input.setEnabled(!value);
      advanced.setEnabled(!value);
      status.setText(text);
    }
  }

  void inspect() {
    if (busy) return;
    final String address;
    final int manual;
    final RecorderPorts ports;
    try {
      RecorderAddress parsed =
          RecorderAddress.parse(
              host.getText().toString(), Integer.parseInt(https.getText().toString()));
      address = parsed.host;
      ports =
          new RecorderPorts(
              parsed.https,
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
                        showLogin(address, certificate, manual, ports);
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
                                  "Trust recorder",
                                  (d, w) -> {
                                    if (alive()) showLogin(address, certificate, manual, ports);
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
