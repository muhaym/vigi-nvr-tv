package com.fantacode.vigitv;

import static org.junit.Assert.*;

import org.json.*;
import org.junit.Test;

public class ProfileTest {
  @Test
  public void validatesHostWithoutUrlOrCredentialInjection() {
    assertEquals("recorder.local", RecorderProfile.validHost(" RECORDER.LOCAL "));
    assertEquals("10.1.2.3", RecorderProfile.validHost("10.1.2.3"));
    for (String bad :
        new String[] {
          "", "https://host/", "user@host", "10.1.2.999", "host:443", "host/path", "a..b", "1.2.3"
        })
      try {
        RecorderProfile.validHost(bad);
        fail(bad);
      } catch (IllegalArgumentException expected) {
      }
  }

  @Test
  public void roundTripPreservesSparseChannelIdentityAndGrid() throws Exception {
    RecorderProfile profile = new RecorderProfile();
    profile.host = "recorder.local";
    profile.columns = 3;
    profile.rows = 2;
    profile.combined = true;
    profile.cameras.put(2, new RecorderProfile.Camera(2, "Entrance", 4, true));
    profile.cameras.put(7, new RecorderProfile.Camera(7, "Hall", 0, false));
    RecorderProfile copy = RecorderProfile.parse(profile.json());
    assertEquals(2, copy.cameras.size());
    assertEquals(4, copy.cells()[2]);
    assertEquals(-1, copy.cells()[1]);
    assertEquals("Hall", copy.names()[6]);
    assertTrue(copy.cameras.get(2).lens);
  }

  @Test
  public void rejectsDuplicateChannelsAndOutOfBoundsCells() throws Exception {
    JSONObject base =
        new JSONObject(
            "{host:'recorder.local',columns:2,rows:2,cameras:[{channel:1,name:'A',cell:0},{channel:1,name:'B',cell:1}]}");
    try {
      RecorderProfile.parse(base);
      fail();
    } catch (JSONException expected) {
    }
    base.put("cameras", new JSONArray("[{channel:1,cell:4}]"));
    try {
      RecorderProfile.parse(base);
      fail();
    } catch (JSONException expected) {
    }
  }

  @Test
  public void discoversOnlyIdentifiersAndLabelsFromTableWrappers() throws Exception {
    JSONObject response =
        new JSONObject(
            "{error_code:0,chm:{added_dev:[{added_dev_2:{id:'2',name:'Front%20door',ip:'10.0.0.9',password:'not-for-storage'}},{added_dev_7:{id:'7',name:'A+B'}}]}}");
    java.util.TreeMap<Integer, RecorderProfile.Camera> cameras = CameraDiscovery.parse(response);
    assertEquals(2, cameras.size());
    assertEquals("Front door", cameras.get(2).name);
    assertEquals("A+B", cameras.get(7).name);
    assertEquals(-1, cameras.get(2).cell);
  }

  @Test
  public void emptyDiscoveryRequiresManualConfiguration() throws Exception {
    try {
      CameraDiscovery.parse(new JSONObject("{error_code:0,chm:{added_dev:[]}}"));
      fail();
    } catch (java.io.IOException expected) {
    }
  }

  @Test
  public void rejectsAmbiguousCombinedCameraMappings() throws Exception {
    for (String cameras :
        new String[] {"[{channel:1,cell:0},{channel:2,cell:0}]", "[{channel:1,cell:-1}]"}) {
      JSONObject profile =
          new JSONObject()
              .put("host", "recorder.local")
              .put("combined", true)
              .put("cameras", new JSONArray(cameras));
      try {
        RecorderProfile.parse(profile);
        fail();
      } catch (JSONException expected) {
      }
    }
  }
}
