package com.fantacode.vigitv;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Same-app account reuse for the same pinned local recorder. */
final class SavedAccount {
  static JSONObject read(Context context) {
    try {
      SharedPreferences p = context.getSharedPreferences("native", 0);
      String value = p.getString("account", "");
      if (value.isEmpty()) return null;
      KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
      ks.load(null);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          (SecretKey) ks.getKey("vigi-tv-account", null),
          new GCMParameterSpec(128, Base64.decode(p.getString("iv", ""), 2)));
      return new JSONObject(
          new String(cipher.doFinal(Base64.decode(value, 2)), StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      return null;
    }
  }
}
