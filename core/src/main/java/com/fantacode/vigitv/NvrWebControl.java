package com.fantacode.vigitv;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import javax.crypto.Cipher;
import org.json.*;

/** Native client for the recorder's existing web protocol. No WebView or direct-camera access. */
final class NvrWebControl implements AutoCloseable {
  final PinnedRecorderHttp http;
  String user, password, stok = "";
  long expires;

  NvrWebControl(String host, int port, String username, String secret, X509Certificate certificate)
      throws Exception {
    http = new PinnedRecorderHttp(host, port, certificate);
    user = username;
    password = secret;
  }

  static String decode(String value) throws Exception {
    return URLDecoder.decode(value.replace("+", "%2B"), "UTF-8");
  }

  static String encode(String value) throws Exception {
    return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
  }

  static String encryptedPassword(String password, String nonce, String pem) throws Exception {
    String hash = DigestAuth.hash(password, "SHA-256").toUpperCase(Locale.US);
    String key =
        decode(decode(pem))
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
    PublicKey publicKey =
        KeyFactory.getInstance("RSA")
            .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key)));
    Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
    cipher.init(Cipher.ENCRYPT_MODE, publicKey);
    return encode(
        Base64.getEncoder()
            .encodeToString(cipher.doFinal((hash + ":" + nonce).getBytes(StandardCharsets.UTF_8))));
  }

  JSONObject anonymousInfo() throws Exception {
    return new JSONObject(
        http.post(
            "/",
            new JSONObject()
                .put("method", "do")
                .put("user_management", new JSONObject().put("get_encrypt_info", JSONObject.NULL))
                .toString()));
  }

  synchronized void login() throws Exception {
    if (!stok.isEmpty() && System.currentTimeMillis() < expires) return;
    if (password.isEmpty()) throw new IOException("Private recorder login is needed");
    JSONObject challenge = anonymousInfo(), info = challenge.optJSONObject("data");
    if (info == null) info = challenge;
    if (info.optInt("sec_left", 0) > 0)
      throw new IOException("Recorder account is temporarily locked");
    String type = info.optString("current_encrypt_type", "");
    JSONArray types = info.optJSONArray("encrypt_type");
    if (type.isEmpty() && types != null)
      for (int i = 0; i < types.length(); i++)
        if (types.optString(i).equals("2")) {
          type = "2";
          break;
        }
    // Only the RSA challenge used by the current recorder UI is supported. No legacy fallback
    // attempts.
    if (!type.equals("2")) throw new IOException("Recorder web login encryption needs validation");
    String key2 = info.optString("key_2", "");
    String key = key2.isEmpty() ? info.optString("key", "") : key2,
        nonce = info.optString("nonce", "");
    if (key.isEmpty() || nonce.isEmpty())
      throw new IOException("Recorder web login challenge incomplete");
    JSONObject credentials =
        new JSONObject()
            .put("username", user)
            .put("password", encryptedPassword(password, nonce, key))
            .put("encrypt_type", "2")
            .put("passwdType", "sha256");
    if (!key2.isEmpty()) credentials.put("keyType", "1");
    JSONObject result =
        new JSONObject(
            http.post(
                "/", new JSONObject().put("method", "do").put("login", credentials).toString()));
    requireSuccess(result);
    String session = decode(result.optString("stok", ""));
    if (!session.matches("[A-Za-z0-9_-]{1,256}"))
      throw new IOException("Recorder web session missing");
    stok = session;
    expires = System.currentTimeMillis() + 8 * 60 * 1000;
  }

  static void requireSuccess(JSONObject result) throws Exception {
    if (!result.has("error_code")) throw new IOException("Recorder control response unrecognized");
    int code = result.getInt("error_code");
    if (code != 0) throw new IOException("Recorder declined web control (code " + code + ")");
  }

  synchronized JSONObject command(JSONObject ptz) throws Exception {
    login();
    JSONObject result =
        new JSONObject(
            http.post(
                "/stok=" + stok + "/ds",
                new JSONObject()
                    .put("method", "do")
                    .put("ptz", ptz)
                    .put("forward", true)
                    .toString()));
    if (result.optInt("error_code", 0) == -40401) {
      stok = "";
      throw new IOException("Recorder control session expired");
    }
    requireSuccess(result);
    return result;
  }

  synchronized JSONObject cameraList() throws Exception {
    login();
    JSONObject request =
        new JSONObject()
            .put("method", "get")
            .put("chm", new JSONObject().put("table", new JSONArray().put("added_dev")));
    JSONObject result = new JSONObject(http.post("/stok=" + stok + "/ds", request.toString()));
    requireSuccess(result);
    return result;
  }

  private synchronized JSONObject alarmRequest(JSONObject request) throws Exception {
    login();
    JSONObject result = new JSONObject(http.post("/stok=" + stok + "/ds", request.toString()));
    if (result.optInt("error_code", 0) == -40401) {
      stok = "";
      throw new IOException("Recorder control session expired");
    }
    requireSuccess(result);
    return result;
  }

  synchronized AlarmProtocol.Capabilities alarmCapabilities(int channel) throws Exception {
    return AlarmProtocol.parse(alarmRequest(AlarmProtocol.capabilities(channel)));
  }

  synchronized void manualAlarm(int channel, boolean start) throws Exception {
    alarmRequest(AlarmProtocol.command(channel, start));
  }

  static void validateChannel(int channel) {
    if (channel < 1 || channel > RecorderProfile.MAX_CHANNELS)
      throw new IllegalArgumentException("Invalid recorder camera number");
  }

  static java.util.List<String> keys(JSONObject object) {
    java.util.List<String> result = new java.util.ArrayList<>();
    java.util.Iterator<String> iterator = object.keys();
    while (iterator.hasNext()) result.add(iterator.next());
    return result;
  }

  synchronized JSONObject capability(int channel) throws Exception {
    validateChannel(channel);
    JSONObject result =
        command(
            new JSONObject()
                .put(
                    "get_ptz_capability",
                    new JSONObject().put("chn_id", Integer.toString(channel))));
    JSONObject channelInfo = result.optJSONObject("chn" + channel + "_ptz_capability");
    if (channelInfo == null && result.optJSONObject("ptz") != null)
      channelInfo = result.getJSONObject("ptz").optJSONObject("chn" + channel + "_ptz_capability");
    if (channelInfo == null) {
      StringBuilder shape = new StringBuilder("Recorder capability keys: " + keys(result));
      for (String key : keys(result)) {
        Object value = result.opt(key);
        if (value instanceof JSONObject)
          shape.append("; ").append(key).append(" keys=").append(keys((JSONObject) value));
        else if (value instanceof JSONArray)
          shape
              .append("; ")
              .append(key)
              .append(" array length=")
              .append(((JSONArray) value).length());
      }
      throw new IOException(shape.toString());
    }
    JSONObject capability = channelInfo.optJSONObject("ptz_capability");
    if (capability == null) throw new IOException("Recorder lens capability missing");
    return capability;
  }

  synchronized void move(int channel, int direction) throws Exception {
    validateChannel(channel);
    if (direction != 0 && direction != 10 && direction != 11)
      throw new IllegalArgumentException("Only lens zoom and stop supported");
    command(
        new JSONObject()
            .put(
                "move",
                new JSONObject()
                    .put("chn_id", Integer.toString(channel))
                    .put("direction", direction)
                    .put("speed", "0.2")));
  }

  synchronized void forgetPassword() {
    password = "";
  }

  public synchronized void close() {
    stok = "";
    password = "";
    user = "";
    expires = 0;
    http.close();
  }
}
