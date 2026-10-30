package com.fantacode.vigitv;

import org.junit.Test;

public class ProtocolRegressionTest {
  @Test
  public void signalAndViewport() throws Exception {
    MicSignalSelfTest.main(new String[0]);
    DigitalViewportSelfTest.main(new String[0]);
    PermissionSessionSelfTest.main(new String[0]);
  }

  @Test
  public void audioMux() throws Exception {
    NativeTalkMuxSelfTest.main(new String[0]);
  }

  @Test
  public void recorderSession() throws Exception {
    java.nio.file.Path directory = java.nio.file.Files.createTempDirectory("recorder-test-");
    java.nio.file.Path keys = directory.resolve("test-only.p12");
    String tool =
        System.getProperty("java.home")
            + "/bin/keytool"
            + (System.getProperty("os.name").startsWith("Windows") ? ".exe" : "");
    Process process =
        new ProcessBuilder(
                tool,
                "-genkeypair",
                "-noprompt",
                "-keystore",
                keys.toString(),
                "-storepass",
                "test-only",
                "-keypass",
                "test-only",
                "-alias",
                "probe",
                "-keyalg",
                "RSA",
                "-validity",
                "2",
                "-dname",
                "CN=localhost",
                "-ext",
                "SAN=ip:127.0.0.1,dns:localhost")
            .redirectErrorStream(true)
            .start();
    try (java.io.InputStream output = process.getInputStream()) {
      byte[] buffer = new byte[1024];
      while (output.read(buffer) != -1) {}
    }
    if (process.waitFor() != 0) throw new AssertionError("Test certificate generation failed");
    try {
      NativeNvrSessionSelfTest.main(new String[] {keys.toString()});
      NvrWebControlSelfTest.main(new String[] {keys.toString()});
      RecorderTrustSelfTest.main(new String[] {keys.toString()});
    } finally {
      java.nio.file.Files.deleteIfExists(keys);
      java.nio.file.Files.deleteIfExists(directory);
    }
  }
}
