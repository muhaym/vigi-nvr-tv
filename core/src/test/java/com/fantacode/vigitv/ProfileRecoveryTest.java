package com.fantacode.vigitv;

import static org.junit.Assert.*;

import org.json.*;
import org.junit.Test;

public class ProfileRecoveryTest {
  RecorderProfile existing() {
    RecorderProfile p = new RecorderProfile();
    p.host = "recorder.local";
    p.certificate = "saved-public-certificate";
    p.combined = true;
    p.cameras.put(1, new RecorderProfile.Camera(1, "Entrance", 0, true));
    p.cameras.put(6, new RecorderProfile.Camera(6, "Hall", 5, false));
    return p;
  }

  @Test
  public void legacyNewCameraWithoutCellDoesNotEraseRecorderOnRestart() throws Exception {
    RecorderProfile p = existing();
    p.cameras.put(12, new RecorderProfile.Camera(12, "New camera", -1, false));
    RecorderProfile restored = RecorderProfile.parse(p.json());
    assertEquals(3, restored.cameras.size());
    assertEquals("recorder.local", restored.host);
    assertEquals(p.certificate, restored.certificate);
    assertFalse(restored.combined);
    assertTrue(restored.overviewNeedsReview);
    assertEquals(5, restored.cameras.get(6).cell);
    assertEquals(-1, restored.cameras.get(12).cell);
    assertEquals(3, RecorderProfile.parse(restored.json()).cameras.size());
  }

  @Test
  public void badGridAndOutOfRangeCellsFallBackToIndividualFeeds() throws Exception {
    JSONObject json = existing().json().put("columns", 0);
    json.getJSONArray("cameras").getJSONObject(1).put("cell", 99);
    RecorderProfile p = RecorderProfile.parse(json);
    assertEquals(4, p.columns);
    assertEquals(2, p.cameras.size());
    assertEquals(-1, p.cameras.get(6).cell);
    assertFalse(p.combined);
    assertTrue(p.overviewNeedsReview);
  }

  @Test
  public void duplicateOverviewCellsRecoverButDuplicateIdentitiesStillFail() throws Exception {
    RecorderProfile p = existing();
    p.cameras.get(6).cell = 0;
    assertFalse(RecorderProfile.parse(p.json()).combined);
    JSONObject json = p.json();
    json.getJSONArray("cameras").getJSONObject(1).put("channel", 1);
    try {
      RecorderProfile.parse(json);
      fail("Duplicate channels must remain invalid");
    } catch (JSONException expected) {
    }
  }

  @Test
  public void refreshingChangedTablePreservesKnownPositionsAndRequiresReview() throws Exception {
    RecorderProfile previous = existing();
    RecorderProfile p = new RecorderProfile();
    p.host = previous.host;
    p.cameras.put(1, new RecorderProfile.Camera(1, "Renamed entrance", -1, false));
    p.cameras.put(6, new RecorderProfile.Camera(6, "Hall", -1, false));
    p.cameras.put(12, new RecorderProfile.Camera(12, "New", -1, false));
    p.inheritOverview(previous);
    assertFalse(p.combined);
    assertTrue(p.overviewNeedsReview);
    assertEquals("Renamed entrance", p.name(1));
    assertTrue(p.cameras.get(1).lens);
    assertEquals(5, p.cameras.get(6).cell);
    assertTrue(previous.combined);
    assertEquals(3, RecorderProfile.parse(p.json()).cameras.size());
    p.cameras.remove(6);
    p.cameras.remove(12);
    p.inheritOverview(previous);
    assertFalse(p.combined);
  }

  @Test
  public void unchangedTableKeepsOverviewAndOtherRecordersNeverInheritPositions() throws Exception {
    RecorderProfile previous = existing();
    RecorderProfile p = RecorderProfile.parse(previous.json());
    p.inheritOverview(previous);
    assertTrue(p.combined);
    assertFalse(p.overviewNeedsReview);
    p = new RecorderProfile();
    p.host = "another.local";
    p.inheritOverview(previous);
    assertFalse(p.combined);
    assertFalse(p.overviewNeedsReview);
  }

  @Test
  public void validUserMappingClearsReviewFlag() throws Exception {
    RecorderProfile p = existing();
    p.overviewNeedsReview = true;
    RecorderProfile result = CompositeMapping.apply(p, 2, 2, "1,6,0,0");
    assertTrue(result.combined);
    assertFalse(result.overviewNeedsReview);
    assertTrue(p.overviewNeedsReview);
  }
}
