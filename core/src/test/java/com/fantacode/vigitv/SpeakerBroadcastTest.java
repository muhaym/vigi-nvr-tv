package com.fantacode.vigitv;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;

public class SpeakerBroadcastTest {
  static class Sink implements SpeakerBroadcast.Sink {
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

  SpeakerBroadcast pair(Sink a, Sink b, List<Integer> connected) {
    return new SpeakerBroadcast(new int[] {2, 4}, channel -> channel == 2 ? a : b, connected::add);
  }

  @Test
  public void allocationCheckOpensBothButNeverWritesAudio() throws Exception {
    Sink a = new Sink(), b = new Sink();
    List<Integer> connected = new ArrayList<>();
    try (SpeakerBroadcast p = pair(a, b, connected)) {
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
    try (SpeakerBroadcast p = pair(a, b, new ArrayList<>())) {
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
    try (SpeakerBroadcast p = pair(a, b, new ArrayList<>())) {
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
    try (SpeakerBroadcast p = pair(a, b, new ArrayList<>())) {
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
    try (SpeakerBroadcast p = pair(a, b, new ArrayList<>())) {
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
    try (SpeakerBroadcast p = pair(a, b, new ArrayList<>())) {
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
    for (int[] ids : new int[][] {null, {}, new int[97], {2, 2}, {0, 2}, {2, 97}})
      try {
        new SpeakerBroadcast(ids, c -> new Sink(), c -> {});
        fail();
      } catch (IllegalArgumentException expected) {
      }
    Sink a = new Sink(), b = new Sink();
    try (SpeakerBroadcast p = pair(a, b, new ArrayList<>())) {
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

  @Test
  public void distributesOneFrameToSingleSmallAndLargeSpeakerSelections() throws Exception {
    for (int count : new int[] {1, 3, 12, 96}) {
      int[] ids = new int[count];
      Map<Integer, Sink> sinks = new LinkedHashMap<>();
      for (int i = 0; i < count; i++) {
        ids[i] = i + 1;
        sinks.put(i + 1, new Sink());
      }
      List<Integer> connected = new ArrayList<>();
      try (SpeakerBroadcast p = new SpeakerBroadcast(ids, sinks::get, connected::add)) {
        p.connect();
        assertEquals(count, connected.size());
        p.start();
        byte[] expected = frame();
        p.send(expected);
        for (Sink sink : sinks.values()) {
          await(sink.delivered);
          assertArrayEquals(expected, sink.frames.get(0));
        }
      }
      for (Sink sink : sinks.values()) assertEquals(1, sink.closes.get());
    }
  }

  @Test
  public void selectionIsCapturedBeforeCallerCanChangeIt() throws Exception {
    int[] ids = {2, 4};
    List<Integer> connected = new ArrayList<>();
    try (SpeakerBroadcast p = new SpeakerBroadcast(ids, channel -> new Sink(), connected::add)) {
      ids[0] = 96;
      p.connect();
      assertEquals(Arrays.asList(2, 4), connected);
    }
  }

  @Test
  public void lastSpeakerRefusalClosesTheWholeGroupWithoutAudio() throws Exception {
    int[] ids = new int[12];
    Map<Integer, Sink> sinks = new LinkedHashMap<>();
    for (int i = 0; i < ids.length; i++) {
      ids[i] = i + 1;
      sinks.put(i + 1, new Sink());
    }
    sinks.get(12).failOpen = true;
    List<Integer> connected = new ArrayList<>();
    AtomicInteger failures = new AtomicInteger();
    try (SpeakerBroadcast p =
        new SpeakerBroadcast(
            ids,
            sinks::get,
            new SpeakerBroadcast.Observer() {
              public void connected(int id) {
                connected.add(id);
              }

              public void failed(Exception error) {
                failures.incrementAndGet();
              }
            })) {
      try {
        p.connect();
        fail();
      } catch (SpeakerBroadcast.SpeakerFailure expected) {
        assertEquals(12, expected.channel);
      }
      assertEquals(11, connected.size());
      assertEquals(1, failures.get());
      for (Sink sink : sinks.values()) {
        assertTrue(sink.frames.isEmpty());
        assertEquals(1, sink.closes.get());
      }
    }
  }

  @Test
  public void failureNotificationRunsOnceAndManualStopDoesNotReportFailure() throws Exception {
    AtomicInteger failures = new AtomicInteger();
    CountDownLatch notified = new CountDownLatch(1);
    Sink a = new Sink(), b = new Sink();
    a.failSend = true;
    b.failSend = true;
    SpeakerBroadcast.Observer observer =
        new SpeakerBroadcast.Observer() {
          public void connected(int id) {}

          public void failed(Exception error) {
            failures.incrementAndGet();
            notified.countDown();
          }
        };
    try (SpeakerBroadcast p =
        new SpeakerBroadcast(new int[] {2, 4}, id -> id == 2 ? a : b, observer)) {
      p.connect();
      p.start();
      try {
        p.send(frame());
      } catch (IOException racedFailure) {
      }
      await(notified);
      await(a.closed);
      await(b.closed);
      assertEquals(1, failures.get());
    }
    try (SpeakerBroadcast p = new SpeakerBroadcast(new int[] {1}, id -> new Sink(), observer)) {
      p.connect();
      p.close();
      assertEquals(1, failures.get());
    }
  }
}
