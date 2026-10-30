package com.fantacode.vigitv;

import android.content.Context;
import android.media.*;
import java.io.IOException;

/** Explicit USB input only. Rejects automatic fallback after removal or a routing failure. */
final class UsbMicSource implements AutoCloseable {
  final Context context;
  final AudioDeviceInfo device;
  volatile AudioRecord recorder;
  boolean closed;

  static boolean isUsb(AudioDeviceInfo device) {
    int type = device.getType();
    return device.isSource()
        && (type == AudioDeviceInfo.TYPE_USB_DEVICE
            || type == AudioDeviceInfo.TYPE_USB_HEADSET
            || type == AudioDeviceInfo.TYPE_USB_ACCESSORY);
  }

  UsbMicSource(Context context) throws IOException {
    this.context = context.getApplicationContext();
    AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    AudioDeviceInfo found = null;
    for (AudioDeviceInfo input : manager.getDevices(AudioManager.GET_DEVICES_INPUTS))
      if (isUsb(input)) {
        found = input;
        break;
      }
    if (found == null)
      throw new IOException("Connect a USB microphone or USB headset to the TV first.");
    device = found;
  }

  synchronized void open(int rate) throws IOException {
    if (closed) throw new IOException("Microphone test stopped");
    if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
        != android.content.pm.PackageManager.PERMISSION_GRANTED)
      throw new SecurityException("Microphone permission is required");
    int buffer =
        AudioRecord.getMinBufferSize(
            rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
    if (buffer <= 0)
      throw new IOException("The USB microphone does not support this audio format.");
    AudioRecord input =
        new AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(
                new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build())
            .setBufferSizeInBytes(Math.max(buffer * 2, rate / 5 * 2))
            .build();
    recorder = input;
    if (input.getState() != AudioRecord.STATE_INITIALIZED || !input.setPreferredDevice(device))
      throw new IOException("The USB microphone could not be selected.");
    input.startRecording();
    if (input.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
      throw new IOException("The USB microphone could not start.");
  }

  int read(short[] samples, int offset, int count) throws IOException {
    AudioRecord input = recorder;
    if (input == null) throw new IOException("USB microphone stopped");
    int got = input.read(samples, offset, count, AudioRecord.READ_BLOCKING);
    AudioDeviceInfo routed = input.getRoutedDevice();
    if (routed == null || !isUsb(routed) || routed.getId() != device.getId())
      throw new IOException("USB microphone disconnected or changed. Talk stopped.");
    if (got <= 0) throw new IOException("USB microphone input stopped");
    return got;
  }

  public synchronized void close() {
    closed = true;
    AudioRecord input = recorder;
    recorder = null;
    if (input != null) {
      try {
        input.stop();
      } catch (Exception ignored) {
      }
      input.release();
    }
  }
}
