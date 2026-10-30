package com.fantacode.vigitv;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.*;

/**
 * Digest primitives shared by pinned recorder transports. No sockets or credentials are retained.
 */
final class DigestAuth {
  static String parameter(String value, String name) {
    Matcher m =
        Pattern.compile("(?i)(?:^|[,\\s])" + name + "\\s*=\\s*(?:\"([^\"]*)\"|([^,\\s]+))")
            .matcher(value);
    return m.find() ? (m.group(1) != null ? m.group(1) : m.group(2)) : "";
  }

  static String hash(String input, String algorithm) throws Exception {
    byte[] bytes =
        MessageDigest.getInstance(algorithm).digest(input.getBytes(StandardCharsets.UTF_8));
    StringBuilder b = new StringBuilder();
    for (byte v : bytes) b.append(String.format(Locale.US, "%02x", v & 255));
    return b.toString();
  }

  static String quoted(String s) throws IOException {
    if (s.indexOf('\r') >= 0 || s.indexOf('\n') >= 0)
      throw new IOException("Invalid recorder authentication field");
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
