package com.fantacode.vigitv;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;

public class TwoSpeakerBroadcastTest {
  static class Sink implements TwoSpeakerBroadcast.Sink {
    final List<byte[]> frames = Collections.synchronizedList(new ArrayList<>());
    final CountDownLatch entered = new CountDownLatch(1),
        delivered = new CountDownLatch(1),
        closed = new CountDownLatch(1);
    final AtomicInteger closes = new AtomicInteger();
    volatile CountDownLatch openGate, sendGate;
    volatile boolean failOpen, failSend, cancelled;
    int opens;

    public void open() throws Exception {
      opens++;
      entered.countDown();
      if (openGate != null) openGate.await();
      if (failOpen || cancelled) throw new IOException("refused");
    }

    public void send(byte[] frame) throws Exception {
      entered.countDown();
      if (sendGate != null) sendGate.await();
      if (failSend || cancelled) throw new IOException("write failed");
      frames.add(Arrays.copyOf(frame, frame.length));
      delivered.countDown();
    }

    public void cancel() {
      cancelled = true;
      if (openGate != null) openGate.countDown();
      if (sendGate != null) sendGate.countDown();
    }

    public void close() {
      closes.incrementAndGet();
      closed.countDown();
    }
  }

  static void await(CountDownLatch latch) throws Exception {
    assertTrue("worker timed out", latch.await(2, TimeUnit.SECONDS));
  }

  static byte[] frame() {
    byte[] b = new byte[188];
    b[0] = 0x47;
    b[10] = 42;
    return b;
  }

  TwoSpeakerBroadcast pair(Sink a, Sink b, List<Integer> connected) {
    return new TwoSpeakerBroadcast(2, 4, channel -> channel == 2 ? a : b, connected::add);
  }

  @Test
  public void allocationCheckOpensBothButNeverWritesAudio() throws Exception {
    Sink a = new Sink(), b = new Sink();
    List<Integer> connected = new ArrayList<>();
    try (TwoSpeakerBroadcast p = pair(a, b, connected)) {
      p.connect();
      assertTrue(p.ready);
      assertFalse(p.running);
      assertEquals(Arrays.asList(2, 4), connected);
      assertTrue(a.frames.isEmpty());
      assertTrue(b.frames.isEmpty());
    }
    assertEquals(1, a.closes.get());
    assertEquals(1, b.closes.get());
  }

  @Test
  public void identicalOwnedFramesReachBothIndependentWriters() throws Exception {
    Sink a = new Sink(), b = new Sink();
    a.sendGate = new CountDownLatch(1);
    try (TwoSpeakerBroadcast p = pair(a, b, new ArrayList<>())) {
      p.connect();
      p.start();
      byte[] bytes = frame();
      p.send(bytes);
      bytes[10] = 99;
      await(b.delivered);
      assertTrue(a.frames.isEmpty());
      a.sendGate.countDown();
      await(a.delivered);
      assertArrayEquals(a.frames.get(0), b.frames.get(0));
      assertEquals(42, a.frames.get(0)[10]);
      try {
        p.start();
        fail();
      } catch (IOException expected) {
      }
    }
  }

  @Test
  public void refusalOfSecondClosesFirstWithoutAnyAudio() throws Exception {
    Sink a = new Sink(), b = new Sink();
    b.failOpen = true;
    try (TwoSpeakerBroadcast p = pair(a, b, new ArrayList<>())) {
      try {
        p.connect();
        fail();
      } catch (IOException expected) {
      }
      assertTrue(p.closed);
      assertFalse(p.ready);
      assertTrue(a.frames.isEmpty());
      assertTrue(b.frames.isEmpty());
    }
    assertEquals(1, a.closes.get());
    assertEquals(1, b.closes.get());
  }

  @Test
  public void writerFailureClosesBothAndRejectsFurtherFrames() throws Exception {
    Sink a = new Sink(), b = new Sink();
    b.failSend = true;
    try (TwoSpeakerBroadcast p = pair(a, b, new ArrayList<>())) {
      p.connect();
      p.start();
      try {
        p.send(frame());
      } catch (IOException racedFailure) {
      }
      await(a.closed);
      await(b.closed);
      assertNotNull(p.failure);
      try {
        p.send(frame());
        fail();
      } catch (IOException expected) {
      }
    }
  }

  @Test
  public void boundedQueuesStopBothInsteadOfGrowingLatency() throws Exception {
    Sink a = new Sink(), b = new Sink();
    a.sendGate = new CountDownLatch(1);
    b.sendGate = new CountDownLatch(1);
    try (TwoSpeakerBroadcast p = pair(a, b, new ArrayList<>())) {
      p.connect();
      p.start();
      // Fill queues while holding both workers at the sender. No audio is emitted.
      boolean refused = false;
      for (int i = 0; i < 20; i++)
        try {
          p.send(frame());
        } catch (IOException expected) {
          refused = true;
          break;
        }
      assertTrue(refused);
      assertTrue(p.closed);
      await(a.closed);
      await(b.closed);
      assertEquals(1, a.closes.get());
      assertEquals(1, b.closes.get());
    }
  }

  @Test
  public void cancellationUnblocksAConnectionStillOpening() throws Exception {
    Sink a = new Sink(), b = new Sink();
    b.openGate = new CountDownLatch(1);
    try (TwoSpeakerBroadcast p = pair(a, b, new ArrayList<>())) {
      AtomicReference<Exception> outcome = new AtomicReference<>();
      Thread t =
          new Thread(
              () -> {
                try {
                  p.connect();
                } catch (Exception e) {
                  outcome.set(e);
                }
              });
      t.start();
      await(b.entered);
      p.close();
      t.join(2000);
      assertFalse(t.isAlive());
      assertNotNull(outcome.get());
      assertEquals(1, a.closes.get());
      assertEquals(1, b.closes.get());
    }
  }

  @Test
  public void validatesPairStateAndFrameBoundaries() throws Exception {
    for (int[] ids : new int[][] {{2, 2}, {0, 2}, {2, 97}})
      try {
        new TwoSpeakerBroadcast(ids[0], ids[1], c -> new Sink(), c -> {});
        fail();
      } catch (IllegalArgumentException expected) {
      }
    Sink a = new Sink(), b = new Sink();
    try (TwoSpeakerBroadcast p = pair(a, b, new ArrayList<>())) {
      try {
        p.start();
        fail();
      } catch (IOException expected) {
      }
      try {
        p.send(frame());
        fail();
      } catch (IOException expected) {
      }
      p.connect();
      try {
        p.connect();
        fail();
      } catch (IOException expected) {
      }
      p.start();
      for (int size : new int[] {0, 1, 188 * 33})
        try {
          p.send(new byte[size]);
          fail();
        } catch (IllegalArgumentException expected) {
        }
    }
  }
}
