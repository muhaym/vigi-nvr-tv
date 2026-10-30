package com.fantacode.vigitv;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.concurrent.*;
import javax.net.ssl.*;

/**
 * Real loopback TLS checks for credential-free identity inspection and pinned control transport.
 */
final class RecorderTrustSelfTest {
  interface Call {
    void run(int port) throws Exception;
  }

  interface Peer {
    void run(SSLSocket socket) throws Exception;
  }

  static void check(boolean value) {
    if (!value) throw new AssertionError("Recorder trust boundary failed");
  }

  static void exchange(SSLContext tls, Peer peer, Call client) throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try (SSLServerSocket server =
        (SSLServerSocket)
            tls.getServerSocketFactory()
                .createServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      server.setSoTimeout(5000);
      Future<?> served =
          executor.submit(
              () -> {
                try (SSLSocket socket = (SSLSocket) server.accept()) {
                  socket.setSoTimeout(5000);
                  peer.run(socket);
                } catch (Exception failure) {
                  throw new RuntimeException(failure);
                }
              });
      client.run(server.getLocalPort());
      served.get(7, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }
  }

  static void request(SSLSocket socket) throws Exception {
    InputStream input = socket.getInputStream();
    check(NativeNvrSession.line(input, 1024).startsWith("POST / HTTP/1.1"));
    NativeNvrSession.body(input, NativeNvrSession.readHeaders(input));
  }

  public static void main(String[] args) throws Exception {
    KeyStore keys = KeyStore.getInstance("PKCS12");
    try (InputStream file = new FileInputStream(args[0])) {
      keys.load(file, "test-only".toCharArray());
    }
    KeyManagerFactory managers =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    managers.init(keys, "test-only".toCharArray());
    SSLContext tls = SSLContext.getInstance("TLS");
    tls.init(managers.getKeyManagers(), null, null);
    X509Certificate certificate = (X509Certificate) keys.getCertificate("probe");
    // The inspector rejects the handshake after observing identity. No HTTP or credentials follow.
    exchange(
        tls,
        socket -> {
          try {
            socket.startHandshake();
            throw new AssertionError("Inspection accepted handshake");
          } catch (IOException expected) {
          }
        },
        port -> {
          X509Certificate observed = RecorderIdentity.inspect("127.0.0.1", port);
          check(MessageDigest.isEqual(certificate.getEncoded(), observed.getEncoded()));
          check(RecorderIdentity.fingerprint(observed).matches("[0-9A-F]{2}(?::[0-9A-F]{2}){31}"));
        });
    // A certificate trusted by the store must still fail when it differs from the exact selected
    // pin.
    exchange(
        tls,
        socket -> {
          try {
            socket.startHandshake();
            throw new AssertionError("Changed pin accepted");
          } catch (IOException expected) {
          }
        },
        port -> {
          try (PinnedRecorderHttp client = new PinnedRecorderHttp("127.0.0.1", port, certificate)) {
            client.pin[0] ^= 1;
            try {
              client.post("/", "{}");
              throw new AssertionError("Changed pin accepted");
            } catch (SSLException expected) {
            }
          }
        });
    // Redirects are errors, rather than forwarding a request (or credentials) to another origin.
    exchange(
        tls,
        socket -> {
          request(socket);
          socket
              .getOutputStream()
              .write(
                  "HTTP/1.1 302 Found\r\nLocation: https://invalid.example/\r\nConnection: close\r\nContent-Length: 0\r\n\r\n"
                      .getBytes(StandardCharsets.US_ASCII));
          socket.getOutputStream().flush();
        },
        port -> {
          try (PinnedRecorderHttp client = new PinnedRecorderHttp("127.0.0.1", port, certificate)) {
            try {
              client.post("/", "{}");
              throw new AssertionError("Redirect accepted");
            } catch (IOException expected) {
              check(expected.getMessage().contains("HTTP 302"));
            }
          }
        });
    exchange(
        tls,
        socket -> {
          request(socket);
          StringBuilder body = new StringBuilder();
          for (int i = 0; i < 70000; i++) body.append('x');
          NvrWebControlSelfTest.reply(socket.getOutputStream(), body.toString(), false);
        },
        port -> {
          try (PinnedRecorderHttp client = new PinnedRecorderHttp("127.0.0.1", port, certificate)) {
            try {
              client.post("/", "{}");
              throw new AssertionError("Oversize body accepted");
            } catch (IOException expected) {
              check(expected.getMessage().contains("exceeded limit"));
            }
          }
        });
    try (PinnedRecorderHttp client = new PinnedRecorderHttp("127.0.0.1", 1, certificate)) {
      for (String path :
          new String[] {"https://invalid.example/", "/other", "/stok=../ds", "/stok=a/ds?x=1"})
        try {
          client.post(path, "{}");
          throw new AssertionError("Control path accepted");
        } catch (IOException expected) {
          check(expected.getMessage().contains("Invalid recorder control path"));
        }
    }
  }
}
