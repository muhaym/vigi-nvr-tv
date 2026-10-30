package com.fantacode.vigitv;

import android.content.Context;
import java.io.*;
import java.security.cert.*;

/** Login-only recorder check. Never allocates talk or captures audio. */
final class NvrStreamCheck implements AutoCloseable {
  final Context context;
  volatile NativeNvrSession session;
  volatile boolean cancelled;
  volatile String stage = "preparing";

  NvrStreamCheck(Context c) {
    context = c;
  }

  String check(String host, String user, String password) throws Exception {
    NativeNvrSession active = RecorderServices.talk(context, host);
    session = active;
    try {
      if (cancelled) throw new IOException("Check cancelled");
      stage = "secure recorder login";
      active.login(user, password);
      if (cancelled) throw new IOException("Check cancelled");
      stage = "read-only camera discovery";
      try (NvrWebControl web = RecorderServices.web(context, host, user, password)) {
        int count = CameraDiscovery.parse(web.cameraList()).size();
        return "Secure recorder login accepted. Discovered "
            + count
            + " cameras. No microphone or camera speaker was opened.";
      }
    } finally {
      active.close();
    }
  }

  public void close() {
    cancelled = true;
    NativeNvrSession active = session;
    if (active != null) active.cancel();
  }
}
