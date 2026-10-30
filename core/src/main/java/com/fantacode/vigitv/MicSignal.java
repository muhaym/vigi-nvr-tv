package com.fantacode.vigitv;

/** Measures changing AC input rather than treating DC offset or tiny noise as speech. */
final class MicSignal {

  int frames, activeFrames;
  double floor = Double.MAX_VALUE, rms, maxRms, maxRise;

  int update(short[] samples, int count) {

    double sum = 0, squares = 0;
    for (int i = 0; i < count; i++) {
      sum += samples[i];
      squares += (double) samples[i] * samples[i];
    }
    double mean = sum / Math.max(1, count);
    rms = Math.sqrt(Math.max(0, squares / Math.max(1, count) - mean * mean));
    frames++;
    maxRms = Math.max(maxRms, rms);

    if (frames > 5 && frames <= 30) floor = Math.min(floor, rms);

    if (frames <= 30) return 0;
    if (floor == Double.MAX_VALUE) floor = 0;

    double rise = Math.max(0, 20 * Math.log10((rms + 1) / (floor + 1)));
    maxRise = Math.max(maxRise, rise);
    if (rms > 160 && rise > 8) activeFrames++;

    return (int) Math.max(0, Math.min(100, Math.round(rise * 100 / 24)));
  }

  int absoluteLevel() {
    double db = rms <= 0 ? -96 : 20 * Math.log10(rms / 32768);
    return (int) Math.max(0, Math.min(100, Math.round((db + 66) * 100 / 48)));
  }

  String result() {
    if (activeFrames >= 5)
      return "Input changed above the noise floor. Check that it follows your voice.";
    if (maxRise > 8) return "Very quiet input changes. Voice capture is not confirmed.";
    return "No clear input change. Voice capture is not confirmed.";
  }
}
