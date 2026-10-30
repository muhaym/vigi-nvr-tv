package com.fantacode.vigitv;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import javax.net.ssl.*;

/** HTTPS transport to one pinned recorder. Session cookies stay in memory. */
final class PinnedRecorderHttp implements AutoCloseable {
  final String host;
  final int port;
  final SSLSocketFactory tls;
  final byte[] pin;
  final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
  boolean closed;

  PinnedRecorderHttp(String h, int p, X509Certificate certificate) throws Exception {
    if (!h.matches("[A-Za-z0-9.-]+") || p < 1 || p > 65535) throw new IllegalArgumentException();
    host = h;
    port = p;
    certificate.checkValidity();
    pin = certificate.getEncoded();
    KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
    store.load(null);
    store.setCertificateEntry("recorder", certificate);
    TrustManagerFactory factory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    factory.init(store);
    X509TrustManager found = null;
    for (TrustManager value : factory.getTrustManagers())
      if (value instanceof X509TrustManager) found = (X509TrustManager) value;
    if (found == null) throw new CertificateException("Recorder trust unavailable");
    final X509TrustManager standard = found;
    X509TrustManager exact =
        new X509TrustManager() {
          public X509Certificate[] getAcceptedIssuers() {
            return standard.getAcceptedIssuers();
          }

          public void checkClientTrusted(X509Certificate[] chain, String auth)
              throws CertificateException {
            throw new CertificateException("No client listener");
          }

          public void checkServerTrusted(X509Certificate[] chain, String auth)
              throws CertificateException {
            standard.checkServerTrusted(chain, auth);
            if (chain.length == 0) throw new CertificateException("Recorder certificate missing");
            chain[0].checkValidity();
            if (!MessageDigest.isEqual(pin, chain[0].getEncoded()))
              throw new CertificateException("Recorder certificate changed");
          }
        };
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(null, new TrustManager[] {exact}, null);
    tls = context.getSocketFactory();
  }

  synchronized String post(String path, String json) throws IOException {
    if (closed) throw new IOException("Recorder connection closed");
    if (!path.equals("/") && !path.matches("/stok=[A-Za-z0-9_-]{1,256}/ds"))
      throw new IOException("Invalid recorder control path");
    URL url = new URL("https", host, port, path);
    HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
    connection.setSSLSocketFactory(tls);
    connection.setHostnameVerifier(
        (name, session) -> {
          if (!host.equalsIgnoreCase(name)) return false;
          try {
            return MessageDigest.isEqual(pin, session.getPeerCertificates()[0].getEncoded());
          } catch (Exception invalid) {
            return false;
          }
        });
    connection.setInstanceFollowRedirects(false);
    connection.setConnectTimeout(2000);
    connection.setReadTimeout(2000);
    connection.setRequestMethod("POST");
    connection.setRequestProperty("Content-Type", "application/json;charset=utf-8");
    connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");
    connection.setRequestProperty("SourceType", "vigi_web");
    connection.setRequestProperty("Cache-Control", "no-cache");
    try {
      URI uri = url.toURI();
      for (Map.Entry<String, List<String>> header :
          cookies.get(uri, Collections.emptyMap()).entrySet())
        connection.setRequestProperty(header.getKey(), String.join("; ", header.getValue()));
      byte[] payload = json.getBytes(StandardCharsets.UTF_8);
      connection.setDoOutput(true);
      connection.setFixedLengthStreamingMode(payload.length);
      try (OutputStream output = connection.getOutputStream()) {
        output.write(payload);
      }
      int status = connection.getResponseCode();
      cookies.put(uri, connection.getHeaderFields());
      if (status != 200)
        throw new IOException("Recorder web control unavailable (HTTP " + status + ")");
      ByteArrayOutputStream body = new ByteArrayOutputStream();
      try (InputStream input = connection.getInputStream()) {
        byte[] buffer = new byte[4096];
        for (int got; (got = input.read(buffer)) != -1; ) {
          if (body.size() + got > 65536) throw new IOException("Recorder response exceeded limit");
          body.write(buffer, 0, got);
        }
      }
      return new String(body.toByteArray(), StandardCharsets.UTF_8);
    } catch (URISyntaxException invalid) {
      throw new IOException("Invalid recorder address");
    } finally {
      connection.disconnect();
    }
  }

  public synchronized void close() {
    closed = true;
    cookies.getCookieStore().removeAll();
  }
}
