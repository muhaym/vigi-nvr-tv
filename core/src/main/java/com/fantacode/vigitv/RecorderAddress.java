package com.fantacode.vigitv;

import java.net.URI;

/**
 * Accept the recorder's browser address without accepting embedded credentials or arbitrary paths.
 */
final class RecorderAddress {
  final String host;
  final int https;

  private RecorderAddress(String host, int https) {
    this.host = host;
    this.https = https;
  }

  static RecorderAddress parse(String entered, int defaultPort) {
    if (entered == null || entered.trim().isEmpty())
      throw new IllegalArgumentException("Enter your recorder IP address or hostname");
    try {
      String text = entered.trim();
      URI uri = new URI(text.contains("://") ? text : "https://" + text);
      if (!"https".equalsIgnoreCase(uri.getScheme())
          || uri.getRawUserInfo() != null
          || uri.getRawQuery() != null
          || (uri.getRawPath() != null
              && !uri.getRawPath().isEmpty()
              && !"/".equals(uri.getRawPath()))
          || (uri.getRawFragment() != null
              && !uri.getRawFragment().isEmpty()
              && !"/".equals(uri.getRawFragment()))) throw new IllegalArgumentException();
      String host = RecorderProfile.validHost(uri.getHost());
      int port = uri.getPort() == -1 ? defaultPort : uri.getPort();
      RecorderPorts checked = new RecorderPorts(port, 554, 8443);
      return new RecorderAddress(host, checked.https);
    } catch (Exception invalid) {
      throw new IllegalArgumentException(
          "Use a recorder IP, hostname or HTTPS address without sign-in details or a page path");
    }
  }
}
