package com.fantacode.vigitv;

import java.nio.charset.StandardCharsets;

/** Native-stream password transform; go2rtc MIT attribution is in THIRD-PARTY-NOTICES.txt. */
final class VigiNativeAuth {
  static String encode(String password) {
    String shortKey = "RDpbLfCPsJZ7fiv",
        longKey =
            "yLwVl0zKqws7LgKPRQ84Mdt708T1qQ3Ha7xv3H7NyU84p21BriUWBU43odz3iP4rBL3cD02KZciXTysVXiV8ngg6vL48rPJyAUw0HurW20xqxv9aYb4M9wK1Ae0wlro510qXeU07kV57fQMc8L6aLgMLwygtc0F10a0Dg70TOoouyFhdysuRMO51yY5ZlOZZLEal1h0t9YQW0Ko7oBwmCAHoic4HYbUyVeU3sfQ1xtXcPcf1aT303wAQhv66qzW";
    byte[] input = password.getBytes(StandardCharsets.UTF_8);
    StringBuilder out = new StringBuilder();
    try {
      for (int i = 0; i < Math.max(input.length, shortKey.length()); i++) {
        int a = 187, b = 187;
        if (i >= input.length) a = shortKey.charAt(i);
        else if (i >= shortKey.length()) b = input[i] & 255;
        else {
          a = shortKey.charAt(i);
          b = input[i] & 255;
        }
        out.append(longKey.charAt((a ^ b) % longKey.length()));
      }
      return out.toString();
    } finally {
      java.util.Arrays.fill(input, (byte) 0);
    }
  }
}
