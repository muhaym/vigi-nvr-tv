package com.fantacode.vigitv;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Cipher;
import javax.net.ssl.*;
import org.json.*;

public final class NvrWebControlSelfTest {
  static void check(boolean value, String name) {
    if (!value) throw new AssertionError(name);
  }

  static void reply(OutputStream out, String body, boolean cookie) throws Exception {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    out.write(
        ("HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Type: application/json\r\n"
                + (cookie ? "Set-Cookie: challenge=test-cookie; Path=/; Secure\r\n" : "")
                + "Content-Length: "
                + bytes.length
                + "\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII));
    out.write(bytes);
    out.flush();
  }

  public static void main(String[] args) throws Exception {
    KeyStore keys = KeyStore.getInstance("PKCS12");
    try (InputStream file = new FileInputStream(args[0])) {
      keys.load(file, "test-only".toCharArray());
    }
    KeyManagerFactory keyManagers =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagers.init(keys, "test-only".toCharArray());
    SSLContext tls = SSLContext.getInstance("TLS");
    tls.init(keyManagers.getKeyManagers(), null, null);
    X509Certificate cert = (X509Certificate) keys.getCertificate("probe");
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair rsa = generator.generateKeyPair();
    String pem =
        "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getEncoder().encodeToString(rsa.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
    ExecutorService work = Executors.newSingleThreadExecutor();
    try (SSLServerSocket server =
        (SSLServerSocket)
            tls.getServerSocketFactory()
                .createServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      Future<?> served =
          work.submit(
              () -> {
                try {
                  for (int index = 0; index < 8; index++)
                    try (SSLSocket peer = (SSLSocket) server.accept()) {
                      peer.setSoTimeout(5000);
                      InputStream in = peer.getInputStream();
                      OutputStream out = peer.getOutputStream();
                      String start = NativeNvrSession.line(in, 512);
                      Map<String, String> headers = NativeNvrSession.readHeaders(in);
                      JSONObject request =
                          new JSONObject(
                              new String(
                                  NativeNvrSession.body(in, headers), StandardCharsets.UTF_8));
                      check(
                          "vigi_web".equals(headers.get("sourcetype")),
                          "recorder web source header");
                      check(request.getString("method").equals("do"), "JSON method");
                      if (index == 0) {
                        check(start.equals("POST / HTTP/1.1"), "public challenge endpoint");
                        check(
                            request.getJSONObject("user_management").has("get_encrypt_info"),
                            "challenge request");
                        reply(
                            out,
                            new JSONObject()
                                .put("error_code", -40401)
                                .put(
                                    "data",
                                    new JSONObject()
                                        .put("encrypt_type", new JSONArray().put("1").put("2"))
                                        .put("key_2", NvrWebControl.encode(pem))
                                        .put("nonce", "12345678"))
                                .toString(),
                            true);
                      } else if (index == 1) {
                        check(
                            headers.get("cookie").contains("challenge=test-cookie"),
                            "challenge cookie retained only for recorder");
                        JSONObject credentials = request.getJSONObject("login");
                        check(
                            credentials.getString("username").equals("admin")
                                && credentials.getString("encrypt_type").equals("2")
                                && credentials.getString("passwdType").equals("sha256")
                                && credentials.getString("keyType").equals("1"),
                            "single advertised RSA/SHA256 login");
                        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                        cipher.init(Cipher.DECRYPT_MODE, rsa.getPrivate());
                        String clear =
                            new String(
                                cipher.doFinal(
                                    Base64.getDecoder()
                                        .decode(
                                            NvrWebControl.decode(
                                                credentials.getString("password")))),
                                StandardCharsets.UTF_8);
                        check(
                            clear.equals(
                                DigestAuth.hash("test-pass", "SHA-256").toUpperCase(Locale.US)
                                    + ":12345678"),
                            "RSA encrypted hash and fresh nonce");
                        reply(out, "{\"error_code\":0,\"stok\":\"test_session_123\"}", false);
                      } else {
                        check(
                            start.equals("POST /stok=test_session_123/ds HTTP/1.1"),
                            "recorder-only session path");
                        if (index >= 5) {
                          check(
                              !request.has("forward") && !request.has("ptz"),
                              "manual alarm has no PTZ forwarding");
                          JSONObject chm = request.getJSONObject("chm");
                          if (index == 5) {
                            Object id = chm.getJSONObject("chm_get_msg_alarm_list").get("chn_id");
                            check(
                                id instanceof String && id.equals("2"),
                                "alarm capability zero-based string");
                            reply(
                                out,
                                "{\"error_code\":0,\"msg_alarm_list\":{\"sound\":1,\"light\":1}}",
                                false);
                          } else {
                            JSONObject alarm = chm.getJSONObject("manual_msg_alarm");
                            check(
                                alarm.get("chn_id") instanceof Integer
                                    && alarm.getInt("chn_id") == 2,
                                "alarm zero-based numeric channel");
                            check(
                                alarm.getString("action").equals(index == 6 ? "start" : "stop"),
                                "alarm start/stop");
                            reply(out, "{\"error_code\":0}", false);
                          }
                          continue;
                        }
                        check(request.getBoolean("forward"), "NVR forward flag");
                        JSONObject ptz = request.getJSONObject("ptz");
                        if (index == 2) {
                          check(
                              ptz.getJSONObject("get_ptz_capability")
                                  .getString("chn_id")
                                  .equals("1"),
                              "web capability keeps one-based channel");
                          reply(
                              out,
                              "{\"error_code\":0,\"chn1_ptz_capability\":{\"ptz_capability\":{\"zoom_supported\":\"1\",\"pan_tilt_supported\":\"0\"}}}",
                              false);
                        } else {
                          JSONObject move = ptz.getJSONObject("move");
                          check(
                              move.getString("chn_id").equals("1")
                                  && move.getInt("direction") == (index == 3 ? 10 : 0)
                                  && move.getString("speed").equals("0.2"),
                              "exact lens zoom and stop");
                          reply(out, "{\"error_code\":0}", false);
                        }
                      }
                    }
                } catch (Exception error) {
                  throw new RuntimeException(error);
                }
              });
      try (NvrWebControl client =
          new NvrWebControl("127.0.0.1", server.getLocalPort(), "admin", "test-pass", cert)) {
        client.login();
        client.forgetPassword();
        check(
            client.capability(1).getString("zoom_supported").equals("1"),
            "capability normalized using token without password retention");
        try {
          client.move(1, 1);
          throw new AssertionError("pan command accepted");
        } catch (IllegalArgumentException expected) {
        }
        try {
          client.capability(0);
          throw new AssertionError("invalid channel accepted");
        } catch (IllegalArgumentException expected) {
        }
        client.move(1, 10);
        client.move(1, 0);
        check(client.alarmCapabilities(3).supported(), "alarm capability response");
        client.manualAlarm(3, true);
        client.manualAlarm(3, false);
      }
      served.get(10, TimeUnit.SECONDS);
    } finally {
      work.shutdownNow();
    }
    System.out.println(
        "PASS: pinned web HTTPS, RSA/SHA256 nonce login, cookie isolation, private session, NVR"
            + " forward flag, channel mapping, lens-only zoom/stop and password-free token reuse."
            + " Local fake recorder only.");
  }
}
