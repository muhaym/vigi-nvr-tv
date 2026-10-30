package com.fantacode.vigitv;

import java.io.*;
import java.util.*;

public final class NativeTalkMuxSelfTest {
  static void check(boolean value, String reason) {
    if (!value) throw new AssertionError(reason);
  }

  static int u(byte b) {
    return b & 255;
  }

  static int crc(byte[] data, int from, int count) {
    int value = -1;
    for (int n = from; n < from + count; n++) {
      value ^= u(data[n]) << 24;
      for (int i = 0; i < 8; i++)
        value = (value & 0x80000000) != 0 ? (value << 1) ^ 0x04c11db7 : value << 1;
    }
    return value;
  }

  static long pts(byte[] p) {
    return ((long) (p[9] & 14) << 29)
        | ((long) u(p[10]) << 22)
        | ((long) (p[11] & 254) << 14)
        | ((long) u(p[12]) << 7)
        | ((long) u(p[13]) >>> 1);
  }

  public static void main(String[] args) throws Exception {
    NativeTalkMux mux = new NativeTalkMux();
    int counter = 0;
    long total = 0;
    for (int f = 0; f < 80; f++) {
      int count = new int[] {1, 169, 170, 183, 184, 640, 1600}[f % 7];
      short[] pcm = new short[count];
      for (int i = 0; i < count; i++) pcm[i] = (short) (i * 113 - 16000);
      byte[] ts = mux.frame(pcm, count);
      check(ts.length % 188 == 0, "TS packet boundary");
      ByteArrayOutputStream payload = new ByteArrayOutputStream();
      int tables = 0;
      for (int n = 0; n < ts.length; n += 188) {
        check(ts[n] == 0x47, "sync");
        int pid = ((ts[n + 1] & 31) << 8) | u(ts[n + 2]);
        int offset = n + 4;
        if (pid == 0 || pid == 4096) {
          tables++;
          int start = n + 5, length = 3 + (((ts[start + 1] & 15) << 8) | u(ts[start + 2]));
          check(crc(ts, start, length) == 0, "PSI CRC");
          if (pid == 4096) check(u(ts[start + 12]) == 0x91, "VIGI PCMU stream type");
          continue;
        }
        check(pid == 256, "audio PID");
        check((ts[n + 3] & 15) == (counter++ & 15), "continuity including wrap");
        check(((ts[n + 1] & 64) != 0) == (payload.size() == 0), "PUSI only on PES start");
        if ((ts[n + 3] & 32) != 0) offset += 1 + u(ts[n + 4]);
        check(offset <= n + 188, "adaptation bounds");
        payload.write(ts, offset, n + 188 - offset);
      }
      check(tables == (f % 25 == 0 ? 2 : 0), "periodic program tables");
      byte[] pes = payload.toByteArray();
      check(pes.length == count + 14, "no TS stuffing leaks into audio");
      check(pes[0] == 0 && pes[1] == 0 && pes[2] == 1 && u(pes[3]) == 192, "audio PES start");
      check(((u(pes[4]) << 8) | u(pes[5])) == count + 8, "PES length");
      check(pts(pes) == total * 90000 / 16000, "16 kHz samples to 90 kHz PTS");
      for (int i = 0; i < count; i++)
        check(pes[14 + i] == TalkCodec.encode(pcm[i]), "raw PCMU payload without RTSP header");
      total += count;
    }
    check(TalkCodec.encode((short) 0) == (byte) 255, "mu-law silence");
    System.out.println(
        "PASS: MPEG-TS PSI CRC, VIGI PCMU type, PES length, padding, continuity wrap, periodic"
            + " tables, timestamp progression, and payload framing.");
  }
}
