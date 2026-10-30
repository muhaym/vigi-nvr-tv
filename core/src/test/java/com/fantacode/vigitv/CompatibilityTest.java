package com.fantacode.vigitv;

import static org.junit.Assert.*;

import java.net.URI;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class CompatibilityTest {
  @Test
  public void supportsSmallAndLargeRecorderChannelCounts() throws Exception {
    for (int count : new int[] {4, 8, 16, 32, 64, 96}) {
      JSONArray table = new JSONArray();
      for (int channel = 1; channel <= count; channel++)
        table.put(new JSONObject().put("id", channel).put("name", "Camera " + channel));
      TreeMap<Integer, RecorderProfile.Camera> found =
          CameraDiscovery.parse(
              new JSONObject().put("chm", new JSONObject().put("added_dev", table)));
      assertEquals(count, found.size());
      RecorderProfile profile = new RecorderProfile();
      profile.host = "recorder.local";
      profile.cameras.putAll(found);
      RecorderProfile copy = RecorderProfile.parse(profile.json());
      assertEquals(count, copy.cameras.size());
      assertEquals("Camera " + count, copy.names()[count - 1]);
      for (int channel = 1; channel <= count; channel++) {
        assertEquals(channel - 1, NativeNvrSession.nativeChannel(channel));
        assertEquals(
            channel - 1,
            AlarmProtocol.command(channel, true)
                .getJSONObject("chm")
                .getJSONObject("manual_msg_alarm")
                .getInt("chn_id"));
      }
    }
  }

  @Test
  public void rejectsOutOfRangeChannelsEverywhere() throws Exception {
    for (int channel : new int[] {-1, 0, 97, Integer.MAX_VALUE}) {
      try {
        NativeNvrSession.nativeChannel(channel);
        fail();
      } catch (IllegalArgumentException expected) {
      }
      try {
        NvrWebControl.validateChannel(channel);
        fail();
      } catch (IllegalArgumentException expected) {
      }
      try {
        RecorderStreams.live("nvr.local", 554, "u", "p", channel, true);
        fail();
      } catch (IllegalArgumentException expected) {
      }
    }
  }

  @Test
  public void preservesCustomPortsAndDefaultsForExistingProfiles() throws Exception {
    RecorderProfile old = RecorderProfile.parse(new JSONObject("{host:'recorder.local'}"));
    assertEquals(443, old.ports.https);
    assertEquals(554, old.ports.rtsp);
    assertEquals(8443, old.ports.talk);
    old.ports = new RecorderPorts(10443, 10554, 18443);
    RecorderProfile copy = RecorderProfile.parse(old.json());
    assertEquals(10443, copy.ports.https);
    assertEquals(10554, copy.ports.rtsp);
    assertEquals(18443, copy.ports.talk);
  }

  @Test
  public void validatesAllConnectionPorts() {
    for (int port : new int[] {-1, 0, 65536})
      try {
        new RecorderPorts(443, port, 8443);
        fail();
      } catch (IllegalArgumentException expected) {
      }
    assertEquals(65535, new RecorderPorts(65535, 1, 443).https);
  }

  @Test
  public void rejectsHostInjectionAndInvalidDnsLabels() {
    for (String host :
        new String[] {
          null,
          "nvr.-bad.local",
          "nvr.bad-.local",
          "[::1]",
          "nvr.local?x",
          "nvr.local#x",
          "nvr.local\\path"
        })
      try {
        RecorderProfile.validHost(host);
        fail();
      } catch (IllegalArgumentException expected) {
      }
  }

  @Test
  public void cameraDiscoveryRejectsConflictingIdentity() throws Exception {
    try {
      CameraDiscovery.parse(new JSONObject("{added_dev:[{id:1,name:'A'},{id:1,name:'B'}]}"));
      fail();
    } catch (java.io.IOException expected) {
    }
  }

  @Test
  public void cameraDiscoveryBoundsUntrustedResponse() throws Exception {
    JSONArray data = new JSONArray();
    for (int i = 0; i < 5000; i++) data.put(i);
    try {
      CameraDiscovery.parse(new JSONObject().put("data", data));
      fail();
    } catch (java.io.IOException expected) {
    }
  }

  @Test
  public void cameraNamesPreserveLiteralPlusAndHandleBadEncoding() throws Exception {
    TreeMap<Integer, RecorderProfile.Camera> found =
        CameraDiscovery.parse(
            new JSONObject("{data:[{id:1,name:'A+B%20C'},{id:2,name:'bad%ZZ'},{id:3,name:'  '}]}"));
    assertEquals("A+B C", found.get(1).name);
    assertEquals("bad%ZZ", found.get(2).name);
    assertEquals("Camera 3", found.get(3).name);
  }

  @Test
  public void groupsPreserveOrderAndPruneRemovedChannels() throws Exception {
    ArrayList<CameraGroup> groups =
        CameraGroup.load("[{name:'Office',ids:[96,3,3,7]}]", new HashSet<>(Arrays.asList(3, 96)));
    assertEquals(Arrays.asList(96, 3), groups.get(0).ids);
    assertEquals(
        groups.get(0).ids,
        CameraGroup.load(CameraGroup.save(groups), new HashSet<>(Arrays.asList(3, 96))).get(0).ids);
  }

  @Test
  public void malformedGroupsFallBackAtomically() {
    for (String json : new String[] {"", "not json", "[{name:'Partial',ids:[1]},{}]"}) {
      ArrayList<CameraGroup> groups = CameraGroup.load(json, new HashSet<>(Arrays.asList(4, 1)));
      assertEquals(1, groups.size());
      assertEquals("All cameras", groups.get(0).name);
      assertEquals(Arrays.asList(1, 4), groups.get(0).ids);
    }
  }

  @Test
  public void emptyRecorderStillHasOneUsableGroup() {
    assertEquals(1, CameraGroup.load("[]", Collections.emptySet()).size());
    assertTrue(CameraGroup.load("[]", Collections.emptySet()).get(0).ids.isEmpty());
  }

  @Test
  public void streamCredentialsCannotInjectAHostOrQuery() throws Exception {
    String text = RecorderStreams.live("nvr.local", 10554, "a@b", "p:/?# %", 96, true);
    URI uri = new URI(text);
    assertEquals("nvr.local", uri.getHost());
    assertEquals(10554, uri.getPort());
    assertEquals("/live/96/1/avm", uri.getPath());
    assertNull(uri.getQuery());
    assertNull(uri.getFragment());
    assertTrue(text.contains("a%40b:p%3A%2F%3F%23%20%25@"));
  }

  @Test
  public void liveAndCombinedKeepDistinctChannelSemantics() {
    assertTrue(
        RecorderStreams.live("nvr.local", 554, "u", "p", 1, false).endsWith("/live/1/2/avm"));
    assertTrue(RecorderStreams.combined("nvr.local", 554, "u", "p").endsWith("/live/0/1/avm"));
  }

  @Test
  public void playbackAlwaysUsesUtcAndValidatedRange() {
    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("GMT+05:30"));
      assertEquals("20260101t000000z", RecorderStreams.utc(1767225600000L));
      assertTrue(
          RecorderStreams.replay("nvr.local", 554, "u", "p", 96, 1767225600000L, 1767227400000L)
              .endsWith("/replay/96/1/avm?starttime=20260101t000000z&endtime=20260101t003000z"));
      for (long[] pair : new long[][] {{0, 1}, {2, 1}, {1, 1}})
        try {
          RecorderStreams.replay("nvr.local", 554, "u", "p", 1, pair[0], pair[1]);
          fail();
        } catch (IllegalArgumentException expected) {
        }
    } finally {
      TimeZone.setDefault(original);
    }
  }

  @Test
  public void remotePatternValidationAndSaltedDerivation() throws Exception {
    assertFalse(PatternSecret.valid(null));
    assertFalse(PatternSecret.valid("012".toCharArray()));
    assertFalse(PatternSecret.valid("0012".toCharArray()));
    assertFalse(PatternSecret.valid("0129".toCharArray()));
    assertTrue(PatternSecret.valid("0123".toCharArray()));
    byte[] first = PatternSecret.derive("0123".toCharArray(), new byte[32]);
    byte[] salt = new byte[32];
    salt[0] = 1;
    assertFalse(Arrays.equals(first, PatternSecret.derive("0123".toCharArray(), salt)));
  }
}
