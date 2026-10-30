package com.fantacode.vigitv;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.regex.*;
import javax.net.ssl.*;

/**
 * Pinned native NVR session. Audio is accepted only as complete MPEG-TS packets after allocation.
 */
final class NativeNvrSession implements AutoCloseable {
  static final String CLIENT_BOUNDARY = "--client-stream-boundary--";

  static String boundary(String content) {
    Matcher match =
        Pattern.compile("(?i)(?:^|;)\\s*boundary\\s*=\\s*(?:\"([^\"]*)\"|([^;\\s]+))")
            .matcher(content);
    return match.find() ? (match.group(1) != null ? match.group(1) : match.group(2)) : "";
  }

  final String host;
  final int port;
  final X509Certificate certificate;
  volatile Socket socket, plainSocket;
  volatile boolean closed;
  volatile String stage = "not started";
  InputStream input;
  OutputStream output;
  String deviceBoundary, sessionId;
  boolean multipart;

  NativeNvrSession(String h, int p, X509Certificate cert) {
    host = h;
    port = p;
    certificate = cert;
  }

  static String line(InputStream in, int limit) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    while (out.size() < limit) {
      int c = in.read();
      if (c < 0) throw new EOFException("Recorder closed the connection");
      if (c == 10) {
        byte[] bytes = out.toByteArray();
        int size = bytes.length;
        if (size > 0 && bytes[size - 1] == 13) size--;
        return new String(bytes, 0, size, StandardCharsets.UTF_8);
      }
      out.write(c);
    }
    throw new IOException("Recorder header exceeded limit");
  }

  static Map<String, String> readHeaders(InputStream in) throws IOException {
    Map<String, String> values = new HashMap<>();
    int total = 0;
    for (int i = 0; i < 64; i++) {
      String line = line(in, 4096);
      total += line.length();
      if (total > 16384) throw new IOException("Recorder headers exceeded limit");
      if (line.isEmpty()) return values;
      int split = line.indexOf(':');
      if (split <= 0) throw new IOException("Malformed recorder header");
      String key = line.substring(0, split).trim().toLowerCase(Locale.US);
      if (values.put(key, line.substring(split + 1).trim()) != null)
        throw new IOException("Duplicate recorder header");
    }
    throw new IOException("Too many recorder headers");
  }

  static byte[] body(InputStream in, Map<String, String> headers) throws IOException {
    String length = headers.get("content-length");
    if (length == null) throw new IOException("Recorder body length is missing");
    int size;
    try {
      size = Integer.parseInt(length);
    } catch (NumberFormatException error) {
      throw new IOException("Invalid recorder body length");
    }
    if (size < 0 || size > 65536) throw new IOException("Recorder body exceeded limit");
    byte[] result = new byte[size];
    int offset = 0;
    while (offset < size) {
      int got = in.read(result, offset, size - offset);
      if (got < 0) throw new EOFException("Recorder response was truncated");
      offset += got;
    }
    return result;
  }

  Map<String, String> httpResponse() throws IOException {
    String first = line(input, 256);
    if (!first.matches("HTTP/1\\.[01] [0-9]{3}.*"))
      throw new IOException("Unexpected recorder protocol");
    Map<String, String> result = readHeaders(input);
    result.put(":status", first.split(" ", 3)[1]);
    return result;
  }

  void connected() throws IOException {
    if (closed) throw new IOException("Check cancelled");
  }

  void login(String user, String password) throws Exception {
    if (user.isEmpty()) throw new IOException("Recorder username is required");
    stage = "secure recorder connection";
    KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
    trust.load(null);
    trust.setCertificateEntry("recorder", certificate);
    TrustManagerFactory tm =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tm.init(trust);
    SSLContext tls = SSLContext.getInstance("TLS");
    tls.init(null, tm.getTrustManagers(), null);
    connected();
    Socket plain = new Socket();
    plainSocket = plain;
    socket = plain;
    connected();
    plain.connect(new InetSocketAddress(host, port), 3000);
    plain.setSoTimeout(5000);
    connected();
    SSLSocket secure = (SSLSocket) tls.getSocketFactory().createSocket(plain, host, port, true);
    socket = secure;
    connected();
    secure.setSoTimeout(5000);
    secure.startHandshake();
    if (!MessageDigest.isEqual(
        certificate.getEncoded(), secure.getSession().getPeerCertificates()[0].getEncoded()))
      throw new IOException("Recorder certificate changed");
    input = secure.getInputStream();
    output = secure.getOutputStream();
    String base =
        "POST /stream HTTP/1.1\r\nHost: "
            + host
            + ":"
            + port
            + "\r\nContent-Type: multipart/mixed; boundary="
            + CLIENT_BOUNDARY
            + "\r\nContent-Length: 0\r\n";
    stage = "native login challenge";
    output.write((base + "\r\n").getBytes(StandardCharsets.UTF_8));
    output.flush();
    Map<String, String> challengeHeaders = httpResponse();
    String challenge = challengeHeaders.get("www-authenticate");
    if (challenge == null || !challenge.startsWith("Digest"))
      throw new IOException("Recorder did not provide a Digest challenge");
    body(input, challengeHeaders);
    String algorithm = DigestAuth.parameter(challenge, "algorithm"),
        qop = DigestAuth.parameter(challenge, "qop");
    if (!algorithm.equalsIgnoreCase("MD5") || !qop.equals("auth"))
      throw new IOException("Native login variant is unsupported");
    String realm = DigestAuth.parameter(challenge, "realm"),
        nonce = DigestAuth.parameter(challenge, "nonce"),
        opaque = DigestAuth.parameter(challenge, "opaque"),
        cn = UUID.randomUUID().toString().replace("-", "");
    if (nonce.isEmpty()) throw new IOException("Recorder challenge nonce is missing");
    String a1 = DigestAuth.hash(user + ":" + realm + ":" + VigiNativeAuth.encode(password), "MD5"),
        a2 = DigestAuth.hash("POST:/stream", "MD5"),
        digest = DigestAuth.hash(a1 + ":" + nonce + ":00000001:" + cn + ":auth:" + a2, "MD5");
    String authorization =
        "Authorization: Digest username=\""
            + DigestAuth.quoted(user)
            + "\", realm=\""
            + DigestAuth.quoted(realm)
            + "\", nonce=\""
            + DigestAuth.quoted(nonce)
            + "\", uri=\"/stream\", response=\""
            + digest
            + "\", algorithm=MD5, qop=auth, nc=00000001, cnonce=\""
            + cn
            + "\""
            + (opaque.isEmpty() ? "" : ", opaque=\"" + DigestAuth.quoted(opaque) + "\"")
            + "\r\n";
    stage = "native login response";
    connected();
    output.write((base + authorization + "\r\n").getBytes(StandardCharsets.UTF_8));
    output.flush();
    Map<String, String> accepted = httpResponse();
    if (!"200".equals(accepted.get(":status")) || accepted.containsKey("www-authenticate"))
      throw new IOException("Recorder did not accept the native login");
    String content = accepted.get("content-type");
    if (content == null || !content.toLowerCase(Locale.US).startsWith("multipart/"))
      throw new IOException("Recorder did not open a multipart stream");
    deviceBoundary = boundary(content);
    if (deviceBoundary.isEmpty()
        || deviceBoundary.length() > 120
        || deviceBoundary.indexOf('\r') >= 0
        || deviceBoundary.indexOf('\n') >= 0)
      throw new IOException("Invalid recorder multipart boundary");
    multipart = true;
    stage = "native login accepted";
  }

  String allocateTalk(int channel) throws Exception {
    return allocateTalk(channel, "half_duplex");
  }

  String allocateTalk(int channel, String mode) throws Exception {
    if (!"half_duplex".equals(mode) && !"aec".equals(mode))
      throw new IllegalArgumentException("Unsupported talk mode");
    if (channel < 1 || channel > RecorderProfile.MAX_CHANNELS)
      throw new IllegalArgumentException("Invalid NVR channel");
    connected();
    if (!multipart) throw new IOException("Recorder login is required");
    stage = "channel talk request";
    // App/RTSP camera numbers are 1..96; the native talk service indexes them 0..95.
    // Keep this conversion here so every caller, including diagnostics, shares it.
    int nativeChannel = nativeChannel(channel);
    String json =
        "{\"type\":\"request\",\"seq\":1,\"params\":{\"method\":\"get\",\"talk\":{\"mode\":\""
            + mode
            + "\",\"channel\":"
            + nativeChannel
            + "}}}";
    byte[] payload = json.getBytes(StandardCharsets.UTF_8);
    String part =
        "--"
            + CLIENT_BOUNDARY
            + "\r\nContent-Type: application/json\r\nContent-Length: "
            + payload.length
            + "\r\n\r\n";
    output.write(part.getBytes(StandardCharsets.US_ASCII));
    output.write(payload);
    output.write(new byte[] {13, 10});
    output.flush();
    stage = "channel talk response";
    String expected = "--" + deviceBoundary;
    boolean found = false;
    for (int i = 0; i < 16; i++) {
      String next = line(input, 1024);
      if (next.equals(expected)) {
        found = true;
        break;
      }
      if (!next.isEmpty()) throw new IOException("Unexpected recorder multipart preamble");
    }
    if (!found) throw new IOException("Recorder multipart response not found");
    Map<String, String> headers = readHeaders(input);
    String type = headers.get("content-type");
    if (type == null || !type.toLowerCase(Locale.US).startsWith("application/json"))
      throw new IOException("Recorder returned a non-JSON talk response");
    String result = new String(body(input, headers), StandardCharsets.UTF_8);
    Matcher error = Pattern.compile("\"error_code\"\\s*:\\s*(-?\\d+)").matcher(result);
    if (!error.find()) throw new IOException("Recorder talk status is missing");
    int code = Integer.parseInt(error.group(1));
    if (code != 0) throw new IOException("Recorder declined channel talk (code " + code + ")");
    Matcher id =
        Pattern.compile("\"session_id\"\\s*:\\s*(?:\"([^\"\\r\\n]{1,128})\"|(\\d{1,20}))")
            .matcher(result);
    if (!id.find()) throw new IOException("Recorder talk session identifier is missing");
    sessionId = id.group(1) != null ? id.group(1) : id.group(2);
    if (!sessionId.matches("[A-Za-z0-9_.-]{1,128}"))
      throw new IOException("Recorder talk session identifier is invalid");
    stage = "channel talk session accepted";
    return sessionId;
  }

  static int nativeChannel(int cameraNumber) {
    if (cameraNumber < 1 || cameraNumber > RecorderProfile.MAX_CHANNELS)
      throw new IllegalArgumentException("Invalid NVR camera number");
    return cameraNumber - 1;
  }

  void sendAudio(byte[] ts) throws IOException {
    connected();
    if (sessionId == null) throw new IOException("Recorder talk session is required");
    if (ts.length == 0 || ts.length % 188 != 0 || ts.length > 188 * 32)
      throw new IOException("Invalid native audio packet");
    for (int i = 0; i < ts.length; i += 188)
      if (ts[i] != 0x47) throw new IOException("Invalid native audio packet");
    String headers =
        "--"
            + CLIENT_BOUNDARY
            + "\r\nContent-Type: audio/mp2t\r\nX-Session-Id: "
            + sessionId
            + "\r\nX-If-Encrypt: 0\r\nContent-Length: "
            + ts.length
            + "\r\n\r\n";
    output.write(headers.getBytes(StandardCharsets.US_ASCII));
    output.write(ts);
    output.write(new byte[] {13, 10});
    output.flush();
  }

  // SSLSocket.close may send TLS close_notify. Abort its underlying TCP socket instead;
  // this unblocks the worker immediately without doing TLS writes on the UI thread.
  void cancel() {
    closed = true;
    Socket active = plainSocket;
    if (active != null)
      try {
        active.close();
      } catch (IOException ignored) {
      }
  }

  @Override
  public void close() {
    closed = true;
    Socket active = socket;
    if (active != null) {
      if (multipart && output != null)
        try {
          output.write(("--" + CLIENT_BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII));
          output.flush();
        } catch (IOException ignored) {
        }
      try {
        active.close();
      } catch (IOException ignored) {
      }
    }
    socket = null;
  }
}
