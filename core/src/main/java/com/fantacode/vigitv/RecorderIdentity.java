package com.fantacode.vigitv;

import java.io.IOException;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;

/** Reads a public TLS identity with no credentials; the handshake is deliberately rejected. */
final class RecorderIdentity {
  static X509Certificate inspect(String host) throws Exception {
    return inspect(host, 443);
  }

  static X509Certificate inspect(String host, int port) throws Exception {
    RecorderPorts.valid(port);
    final X509Certificate[] observed = new X509Certificate[1];
    X509TrustManager reader =
        new X509TrustManager() {
          public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
          }

          public void checkClientTrusted(X509Certificate[] chain, String auth)
              throws CertificateException {
            throw new CertificateException("No client certificates");
          }

          public void checkServerTrusted(X509Certificate[] chain, String auth)
              throws CertificateException {
            if (chain.length > 0) observed[0] = chain[0];
            throw new CertificateException("Identity inspection only");
          }
        };
    SSLContext tls = SSLContext.getInstance("TLS");
    tls.init(null, new TrustManager[] {reader}, null);
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(RecorderProfile.validHost(host), port), 3000);
      socket.setSoTimeout(3000);
      try (SSLSocket secure =
          (SSLSocket) tls.getSocketFactory().createSocket(socket, host, port, true)) {
        secure.setSoTimeout(3000);
        try {
          secure.startHandshake();
        } catch (SSLException expected) {
          if (observed[0] == null) throw expected;
        }
      }
    }
    if (observed[0] == null) throw new IOException("Recorder did not provide a certificate");
    observed[0].checkValidity();
    return observed[0];
  }

  static String fingerprint(X509Certificate certificate) throws Exception {
    byte[] hash = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
    StringBuilder result = new StringBuilder();
    for (byte value : hash) {
      if (result.length() > 0) result.append(':');
      result.append(String.format(java.util.Locale.ROOT, "%02X", value & 255));
    }
    return result.toString();
  }
}
