package com.fantacode.vigitv;

import android.content.Context;
import android.util.Base64;
import java.io.*;
import java.security.cert.*;
import org.json.JSONObject;

/** Public certificates and camera labels are device-local. Passwords stay in SavedAccount. */
final class ProfileStore {
  static RecorderProfile read(Context context) {
    try {
      RecorderProfile profile =
          RecorderProfile.parse(
              new JSONObject(
                  context.getSharedPreferences("native", 0).getString("recorder_profile", "")));
      if (profile.overviewNeedsReview)
        android.util.Log.i(
            "RecorderProfile",
            "Recovered camera list; overview needs review; cameras=" + profile.cameras.size());
      return profile;
    } catch (Exception absent) {
      return new RecorderProfile();
    }
  }

  static void save(Context context, RecorderProfile profile) throws Exception {
    profile.repairOverview();
    RecorderProfile.parse(profile.json());
    if (!context
        .getSharedPreferences("native", 0)
        .edit()
        .putString("recorder_profile", profile.json().toString())
        .commit()) throw new IOException("Could not save recorder profile");
  }

  static X509Certificate certificate(Context context, String host) throws Exception {
    RecorderProfile profile = read(context);
    if (!profile.host.equals(RecorderProfile.validHost(host)) || profile.certificate.isEmpty())
      throw new CertificateException("Trust this recorder in Recorder setup first");
    X509Certificate certificate =
        (X509Certificate)
            CertificateFactory.getInstance("X.509")
                .generateCertificate(
                    new ByteArrayInputStream(Base64.decode(profile.certificate, Base64.NO_WRAP)));
    certificate.checkValidity();
    return certificate;
  }
}
