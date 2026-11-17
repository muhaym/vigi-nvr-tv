package com.fantacode.vigitv;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/** Selected NVR speaker sessions, fed identical frames from one microphone clock. */
final class SpeakerBroadcast implements AutoCloseable {
  interface Sink extends AutoCloseable {
    void open() throws Exception;

    void send(byte[] frame) throws Exception;

    // Must unblock an in-progress open or send without waiting for the writer.
    void cancel();

    void close();
  }

  interface Factory {
    Sink create(int channel) throws Exception;
  }

  interface Observer {
    void connected(int channel);

    default void failed(Exception error) {}
  }

  static final class SpeakerFailure extends IOException {
    final int channel;

    SpeakerFailure(int channel, Exception cause) {
      super("Speaker " + channel + " connection failed", cause);
      this.channel = channel;
    }
  }

  final int[] channels;
  final Factory factory;
  final Observer observer;
  final Object lock = new Object();
  final List<Endpoint> endpoints = new ArrayList<>();
  volatile boolean closed, ready, running;
  volatile Exception failure;

  SpeakerBroadcast(int[] selected, Factory factory, Observer observer) {
    if (selected == null || selected.length == 0 || selected.length > RecorderProfile.MAX_CHANNELS)
      throw new IllegalArgumentException("Choose one or more speakers");
    channels = Arrays.copyOf(selected, selected.length);
    Set<Integer> unique = new HashSet<>();
    for (int channel : channels) {
      NativeNvrSession.nativeChannel(channel);
      if (!unique.add(channel)) throw new IllegalArgumentException("Choose each speaker only once");
    }
    this.factory = factory;
    this.observer = observer;
  }

  // A successful allocation check captures and sends no audio.
  void connect() throws Exception {
    synchronized (lock) {
      if (closed || ready || !endpoints.isEmpty())
        throw new IOException("Broadcast is not available");
    }
    try {
      for (int channel : channels) {
        Sink sink = factory.create(channel);
        Endpoint endpoint = new Endpoint(channel, sink);
        synchronized (lock) {
          if (closed) {
            endpoint.stop();
            throw new IOException("Broadcast stopped");
          }
          endpoints.add(endpoint);
        }
        try {
          sink.open();
        } catch (Exception error) {
          throw new SpeakerFailure(channel, error);
        }
        check();
        observer.connected(channel);
      }
      synchronized (lock) {
        check();
        ready = true;
      }
    } catch (Exception error) {
      fail(error);
      throw error;
    }
  }

  void start() throws IOException {
    synchronized (lock) {
      check();
      if (!ready || running) throw new IOException("Connect all selected speakers first");
      running = true;
      for (Endpoint endpoint : endpoints) {
        endpoint.worker = new Thread(endpoint, "pa-speaker-" + endpoint.channel);
        endpoint.worker.start();
      }
    }
  }

  void send(byte[] frame) throws Exception {
    check();
    if (!running) throw new IOException("Broadcast has not started");
    if (frame.length == 0 || frame.length % 188 != 0 || frame.length > 188 * 32)
      throw new IllegalArgumentException("Invalid broadcast audio frame");
    byte[] owned = Arrays.copyOf(frame, frame.length);
    List<Endpoint> targets;
    synchronized (lock) {
      targets = new ArrayList<>(endpoints);
    }
    for (Endpoint endpoint : targets) {
      check();
      if (!endpoint.queue.offer(owned)) {
        IOException error =
            new SpeakerFailure(
                endpoint.channel, new IOException("Speaker fell behind; all speakers stopped"));
        fail(error);
        throw error;
      }
    }
  }

  void check() throws IOException {
    if (failure != null)
      throw new IOException("Speaker connection failed; all speakers stopped", failure);
    if (closed) throw new IOException("Broadcast stopped");
  }

  void fail(Exception error) {
    boolean firstFailure;
    synchronized (lock) {
      firstFailure = failure == null && !closed;
      if (firstFailure) failure = error;
    }
    close();
    if (firstFailure) observer.failed(error);
  }

  @Override
  public void close() {
    List<Endpoint> targets;
    synchronized (lock) {
      if (closed) return;
      closed = true;
      ready = false;
      targets = new ArrayList<>(endpoints);
    }
    for (Endpoint endpoint : targets) endpoint.stop();
  }

  final class Endpoint implements Runnable {
    final int channel;
    final Sink sink;
    // Four 40 ms frames bound application-side skew; no unbounded queue or replay after reconnect.
    final ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(4);
    final AtomicBoolean stopped = new AtomicBoolean();
    volatile Thread worker;

    Endpoint(int channel, Sink sink) {
      this.channel = channel;
      this.sink = sink;
    }

    void stop() {
      if (!stopped.compareAndSet(false, true)) return;
      queue.clear();
      try {
        sink.cancel();
      } catch (RuntimeException ignored) {
      }
      Thread thread = worker;
      if (thread != null) thread.interrupt();
      try {
        sink.close();
      } catch (RuntimeException ignored) {
      }
    }

    @Override
    public void run() {
      try {
        while (!closed) {
          byte[] frame = queue.take();
          if (!closed) sink.send(frame);
        }
      } catch (InterruptedException interrupted) {
        if (!closed) fail(interrupted);
        Thread.currentThread().interrupt();
      } catch (Exception error) {
        if (!closed) fail(new SpeakerFailure(channel, error));
      } finally {
        stop();
      }
    }
  }
}
