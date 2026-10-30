package com.fantacode.vigitv;

public class MicSignalSelfTest {
  static void check(boolean value, String label) {
    if (!value) throw new AssertionError(label);
  }

  static short[] wave(int amplitude, int offset) {
    short[] s = new short[640];
    for (int i = 0; i < s.length; i++)
      s[i] = (short) (offset + (i % 2 == 0 ? amplitude : -amplitude));
    return s;
  }

  public static void main(String[] args) {
    MicSignal constant = new MicSignal();
    for (int i = 0; i < 250; i++)
      check(constant.update(wave(0, 20000), 640) == 0, "DC offset must not look like speech");
    check(constant.activeFrames == 0, "no DC activity");
    MicSignal noise = new MicSignal();
    for (int i = 0; i < 250; i++) noise.update(wave(80, 0), 640);
    check(noise.activeFrames == 0, "steady low noise is not voice confirmation");
    MicSignal voice = new MicSignal();
    for (int i = 0; i < 30; i++) voice.update(wave(20, 0), 640);
    for (int i = 0; i < 20; i++)
      check(voice.update(wave(1500, 0), 640) > 50, "strong changing input moves meter");
    check(voice.activeFrames >= 5, "activity after quiet baseline");
    MicSignal click = new MicSignal();
    for (int i = 0; i < 30; i++) click.update(wave(20, 0), 640);
    click.update(wave(1500, 0), 640);
    check(click.activeFrames < 5, "single transient is not enough");
    System.out.println(
        "Mic diagnostics: DC offset, steady noise, isolated click, and sustained input-change"
            + " checks passed");
  }
}
