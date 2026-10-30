package com.fantacode.vigitv;

import android.content.Context;
import java.io.IOException;

/**
 * Resolves configured ports and trust for the one selected recorder, consistently across features.
 */
final class RecorderServices {
  static RecorderProfile profile(Context context, String host) throws IOException {
    RecorderProfile profile = ProfileStore.read(context);
    if (!profile.host.equals(RecorderProfile.validHost(host)))
      throw new IOException("Configure and trust this recorder first");
    return profile;
  }

  static NvrWebControl web(Context context, String host, String user, String password)
      throws Exception {
    return new NvrWebControl(
        host,
        profile(context, host).ports.https,
        user,
        password,
        ProfileStore.certificate(context, host));
  }

  static NativeNvrSession talk(Context context, String host) throws Exception {
    return new NativeNvrSession(
        host, profile(context, host).ports.talk, ProfileStore.certificate(context, host));
  }
}
