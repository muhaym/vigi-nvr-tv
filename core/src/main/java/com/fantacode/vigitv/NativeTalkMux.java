package com.fantacode.vigitv;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/** Single 16 kHz mono PCMU track. VIGI SDK codec 211 maps to private TS type 0x91. */
final class NativeTalkMux {
  int audioCounter, patCounter, pmtCounter, frames;
  long samples;

  static int crc(byte[] bytes) {
    int value = -1;
    for (byte b : bytes) {
      value ^= (b & 255) << 24;
      for (int i = 0; i < 8; i++) value = (value << 1) ^ ((value < 0) ? 0x04c11db7 : 0);
    }
    return value;
  }

  static byte[] section(byte[] prefix) {
    byte[] out = Arrays.copyOf(prefix, prefix.length + 4);
    int crc = crc(prefix);
    for (int i = 0; i < 4; i++) out[prefix.length + i] = (byte) (crc >>> (24 - 8 * i));
    return out;
  }

  static byte[] table(int pid, int counter, byte[] section) {
    byte[] out = new byte[188];
    Arrays.fill(out, (byte) 255);
    out[0] = 0x47;
    out[1] = (byte) (0x40 | (pid >>> 8));
    out[2] = (byte) pid;
    out[3] = (byte) (0x10 | (counter & 15));
    out[4] = 0;
    System.arraycopy(section, 0, out, 5, section.length);
    return out;
  }

  byte[] frame(short[] pcm, int count) {
    if (count <= 0 || count > 1600 || count > pcm.length)
      throw new IllegalArgumentException("Invalid talk audio frame");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    if (frames++ % 25 == 0) {
      byte[] pat =
          table(
              0,
              patCounter++,
              section(
                  new byte[] {0, (byte) 0xb0, 13, 0, 1, (byte) 0xc1, 0, 0, 0, 1, (byte) 0xf0, 0}));
      byte[] pmt =
          table(
              0x1000,
              pmtCounter++,
              section(
                  new byte[] {
                    2,
                    (byte) 0xb0,
                    18,
                    0,
                    1,
                    (byte) 0xc1,
                    0,
                    0,
                    (byte) 0xff,
                    (byte) 0xff,
                    (byte) 0xf0,
                    0,
                    (byte) 0x91,
                    (byte) 0xe1,
                    0,
                    (byte) 0xf0,
                    0
                  }));
      out.write(pat, 0, pat.length);
      out.write(pmt, 0, pmt.length);
    }
    byte[] pes = new byte[14 + count];
    pes[2] = 1;
    pes[3] = (byte) 0xc0;
    pes[4] = (byte) ((count + 8) >>> 8);
    pes[5] = (byte) (count + 8);
    pes[6] = (byte) 0x80;
    pes[7] = (byte) 0x80;
    pes[8] = 5;
    long pts = (samples * 90000 / 16000) & 0x1ffffffffL;
    pes[9] = (byte) (0x21 | ((pts >>> 29) & 14));
    pes[10] = (byte) (pts >>> 22);
    pes[11] = (byte) (((pts >>> 14) & 254) | 1);
    pes[12] = (byte) (pts >>> 7);
    pes[13] = (byte) (((pts << 1) & 254) | 1);
    for (int i = 0; i < count; i++) pes[14 + i] = TalkCodec.encode(pcm[i]);
    samples += count;
    for (int offset = 0; offset < pes.length; ) {
      byte[] packet = new byte[188];
      Arrays.fill(packet, (byte) 255);
      int size = Math.min(184, pes.length - offset);
      packet[0] = 0x47;
      packet[1] = (byte) (1 | (offset == 0 ? 0x40 : 0));
      packet[2] = 0;
      packet[3] = (byte) (0x10 | (audioCounter++ & 15));
      int start = 4;
      if (size < 184) {
        packet[3] |= 0x20;
        int adaptation = 183 - size;
        packet[4] = (byte) adaptation;
        if (adaptation > 0) packet[5] = 0;
        start += 1 + adaptation;
      }
      System.arraycopy(pes, offset, packet, start, size);
      out.write(packet, 0, packet.length);
      offset += size;
    }
    return out.toByteArray();
  }
}
