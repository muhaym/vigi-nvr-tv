package com.fantacode.vigitv;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

public final class NativeNvrSessionSelfTest {
  static void check(boolean value, String reason) {
    if (!value) throw new AssertionError(reason);
  }

  static void write(OutputStream out, String text) throws IOException {
    out.write(text.getBytes(StandardCharsets.UTF_8));
    out.flush();
  }

  static void run(SSLContext tls, X509Certificate certificate, int code, boolean audio)
      throws Exception {
    run(tls, certificate, code, audio, "half_duplex");
  }

  static void run(SSLContext tls, X509Certificate certificate, int code, boolean audio, String mode)
      throws Exception {
    ExecutorService worker = Executors.newSingleThreadExecutor();
    try (SSLServerSocket server =
        (SSLServerSocket)
            tls.getServerSocketFactory()
                .createServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
      Future<?> done =
          worker.submit(
              () -> {
                try (SSLSocket client = (SSLSocket) server.accept()) {
                  client.setSoTimeout(5000);
                  InputStream in = client.getInputStream();
                  OutputStream out = client.getOutputStream();
                  check(
                      NativeNvrSession.line(in, 256).equals("POST /stream HTTP/1.1"),
                      "initial request");
                  Map<String, String> first = NativeNvrSession.readHeaders(in);
                  check(!first.containsKey("authorization"), "initial credentials absent");
                  write(
                      out,
                      "HTTP/1.0 200 OK\r\n"
                          + "Content-Length: 0\r\n"
                          + "WWW-Authenticate: Digest realm=\"TP-Link\", algorithm=MD5,"
                          + " qop=\"auth\", nonce=\"test-nonce\", opaque=\"test-opaque\"\r\n\r\n");
                  check(
                      NativeNvrSession.line(in, 256).equals("POST /stream HTTP/1.1"),
                      "authenticated request");
                  Map<String, String> second = NativeNvrSession.readHeaders(in);
                  String auth = second.get("authorization");
                  String cn = DigestAuth.parameter(auth, "cnonce");
                  String
                      a1 =
                          DigestAuth.hash(
                              "admin:TP-Link:" + VigiNativeAuth.encode("test-only-pass"), "MD5"),
                      a2 = DigestAuth.hash("POST:/stream", "MD5");
                  String digest =
                      DigestAuth.hash(a1 + ":test-nonce:00000001:" + cn + ":auth:" + a2, "MD5");
                  check(
                      digest.equals(DigestAuth.parameter(auth, "response")),
                      "native digest password transform");
                  write(
                      out,
                      "HTTP/1.0 200 OK\r\n"
                          + "Content-Type:"
                          + " multipart/mixed;boundary=--device-stream-boundary--\r\n\r\n");
                  check(
                      NativeNvrSession.line(in, 256).equals("----client-stream-boundary--"),
                      "client multipart boundary");
                  Map<String, String> headers = NativeNvrSession.readHeaders(in);
                  String json =
                      new String(NativeNvrSession.body(in, headers), StandardCharsets.UTF_8);
                  check(
                      json.contains("\"method\":\"get\"")
                          && json.contains("\"channel\":3")
                          && json.contains("\"mode\":\"" + mode + "\""),
                      "channel-aware native talk JSON");
                  check(NativeNvrSession.line(in, 256).isEmpty(), "JSON part terminator");
                  String response =
                      "{\"type\":\"response\",\"seq\":1,\"params\":{\"error_code\":"
                          + code
                          + (code == 0 ? ",\"session_id\":\"test-session\"" : "")
                          + "}}";
                  write(
                      out,
                      "----device-stream-boundary--\r\n"
                          + "Content-Type: application/json\r\n"
                          + "Content-Length: "
                          + response.length()
                          + "\r\n\r\n"
                          + response
                          + "\r\n");
                  if (audio) {
                    check(
                        NativeNvrSession.line(in, 256).equals("----client-stream-boundary--"),
                        "media boundary");
                    Map<String, String> media = NativeNvrSession.readHeaders(in);
                    check(
                        "audio/mp2t".equals(media.get("content-type")),
                        "native MPEG-TS media type");
                    check(
                        "test-session".equals(media.get("x-session-id")),
                        "allocated session header");
                    check(
                        "0".equals(media.get("x-if-encrypt")),
                        "media inside pinned TLS without extra AES");
                    byte[] ts = NativeNvrSession.body(in, media);
                    check(ts.length % 188 == 0 && ts[0] == 0x47, "TS body, no RTSP interleaving");
                    check(NativeNvrSession.line(in, 256).isEmpty(), "media terminator");
                  }
                  check(
                      NativeNvrSession.line(in, 256).equals("----client-stream-boundary----"),
                      "stop is multipart end");
                } catch (Exception error) {
                  throw new RuntimeException(error);
                }
              });
      try (NativeNvrSession probe =
          new NativeNvrSession("127.0.0.1", server.getLocalPort(), certificate)) {
        probe.login("admin", "test-only-pass");
        if (code == 0) {
          check(probe.allocateTalk(4, mode).equals("test-session"), "session response");
          try {
            probe.sendAudio(TalkCodec.frame(new short[640], 640));
            throw new AssertionError("raw RTSP frame accepted by native transport");
          } catch (IOException expected) {
          }
          if (audio) probe.sendAudio(new NativeTalkMux().frame(new short[640], 640));
        } else
          try {
            probe.allocateTalk(4, mode);
            throw new AssertionError("refusal accepted");
          } catch (IOException expected) {
            check(expected.getMessage().contains(Integer.toString(code)), "sanitized refusal code");
          }
      }
      done.get(8, TimeUnit.SECONDS);
    } finally {
      worker.shutdownNow();
    }
  }

  public static void main(String[] args) throws Exception {
    KeyStore keys = KeyStore.getInstance("PKCS12");
    try (InputStream input = new FileInputStream(args[0])) {
      keys.load(input, "test-only".toCharArray());
    }
    KeyManagerFactory km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    km.init(keys, "test-only".toCharArray());
    SSLContext tls = SSLContext.getInstance("TLS");
    tls.init(km.getKeyManagers(), null, null);
    X509Certificate cert = (X509Certificate) keys.getCertificate("probe");
    for (int camera = 1; camera <= 96; camera++)
      check(NativeNvrSession.nativeChannel(camera) == camera - 1, "native talk index conversion");
    check(
        NativeNvrSession.nativeChannel(3) == 2 && NativeNvrSession.nativeChannel(4) == 3,
        "adjacent camera native indices");
    for (int invalid : new int[] {0, 97, -1})
      try {
        NativeNvrSession.nativeChannel(invalid);
        throw new AssertionError("invalid camera accepted");
      } catch (IllegalArgumentException expected) {
      }
    final boolean[] aborted = {false};
    NativeNvrSession cancellation = new NativeNvrSession("127.0.0.1", 1, cert);
    cancellation.socket =
        new java.net.Socket() {
          public void close() {
            throw new AssertionError("UI cancellation must not invoke TLS socket close");
          }
        };
    cancellation.plainSocket =
        new java.net.Socket() {
          public void close() {
            aborted[0] = true;
          }
        };
    cancellation.cancel();
    cancellation.cancel();
    check(aborted[0] && cancellation.closed, "cancellation aborts TCP only, idempotently");
    check(
        NativeNvrSession.boundary("multipart/mixed;boundary=--device-stream-boundary--")
            .equals("--device-stream-boundary--"),
        "compact MIME parameter");
    check(
        NativeNvrSession.boundary("multipart/mixed; boundary=\"--device-stream-boundary--\"")
            .equals("--device-stream-boundary--"),
        "quoted MIME parameter");
    run(tls, cert, 0, false);
    run(tls, cert, -52411, false);
    run(tls, cert, 0, true);
    run(tls, cert, 0, true, "aec");
    run(tls, cert, -52411, false, "aec");
    try {
      new NativeNvrSession("127.0.0.1", 1, cert).allocateTalk(4, "bogus");
      throw new AssertionError("invalid mode accepted");
    } catch (IllegalArgumentException expected) {
    }
    byte[] bad = "Content-Length: 70000\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
    Map<String, String> headers = NativeNvrSession.readHeaders(new ByteArrayInputStream(bad));
    try {
      NativeNvrSession.body(new ByteArrayInputStream(new byte[0]), headers);
      throw new AssertionError("oversize response accepted");
    } catch (IOException expected) {
    }
    try {
      NativeNvrSession.readHeaders(
          new ByteArrayInputStream(
              "Content-Length: 0\r\nContent-Length: 4\r\n\r\n"
                  .getBytes(StandardCharsets.US_ASCII)));
      throw new AssertionError("duplicate headers accepted");
    } catch (IOException expected) {
    }
    System.out.println(
        "PASS: pinned TLS, native Digest transform, native get/half_duplex/channel request, bounded"
            + " multipart parsing, refused-session handling, and end-boundary cleanup. Synthetic"
            + " media verified against local fake recorder only; no microphone or office"
            + " connection.");
  }
}
