package com.fantacode.vigitv;

/** G.711 mu-law conversion for VIGI's 16 kHz mono talk channel. */
final class TalkCodec {
  static byte encode(short sample) {
    int value = sample, sign = (value >> 8) & 0x80;
    if (sign != 0) value = -value;
    if (value > 32635) value = 32635;
    value += 132;
    int exponent = 7;
    for (int mask = 0x4000; (value & mask) == 0 && exponent > 0; mask >>= 1) exponent--;
    int mantissa = (value >> (exponent + 3)) & 15;
    return (byte) ~(sign | (exponent << 4) | mantissa);
  }

  static byte[] frame(short[] samples, int count) {
    if (count < 0 || count > samples.length || count > 65535) throw new IllegalArgumentException();
    byte[] packet = new byte[count + 4];
    packet[0] = 0x24;
    packet[1] = 0;
    packet[2] = (byte) (count >> 8);
    packet[3] = (byte) count;
    for (int i = 0; i < count; i++) packet[i + 4] = encode(samples[i]);
    return packet;
  }
}
