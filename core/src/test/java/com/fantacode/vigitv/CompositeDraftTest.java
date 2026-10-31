package com.fantacode.vigitv;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class CompositeDraftTest {
  RecorderProfile profile() {
    RecorderProfile p = new RecorderProfile();
    p.host = "nvr.local";
    p.cameras.put(2, new RecorderProfile.Camera(2, "Entrance", 0, false));
    p.cameras.put(7, new RecorderProfile.Camera(7, "Hall", 5, false));
    return p;
  }

  @Test
  public void movingCameraClearsPreviousCellAndPreservesOriginal() {
    RecorderProfile p = profile();
    CompositeDraft draft = new CompositeDraft(p);
    draft.assign(1, 7);
    assertEquals(7, draft.cells[1]);
    assertEquals(0, draft.cells[5]);
    assertEquals(5, p.cameras.get(7).cell);
    assertTrue(draft.unassigned().isEmpty());
  }

  @Test
  public void shrinkingPreservesRowsAndReportsDisplacedCameras() {
    CompositeDraft draft = new CompositeDraft(profile());
    draft.assign(15, 7);
    draft.resize(2, 2);
    assertEquals(2, draft.cells[0]);
    assertEquals(Arrays.asList(7), draft.unassigned());
    try {
      draft.build();
      fail();
    } catch (Exception expected) {
    }
  }

  @Test
  public void fillAndEmptyRemainDraftOnly() throws Exception {
    RecorderProfile p = profile();
    CompositeDraft draft = new CompositeDraft(p);
    draft.fill();
    assertEquals(7, draft.cells[1]);
    draft.assign(0, 0);
    assertEquals(Arrays.asList(2), draft.unassigned());
    assertEquals(0, p.cameras.get(2).cell);
    draft.assign(3, 2);
    RecorderProfile result = draft.build();
    assertTrue(result.combined);
    assertEquals(3, result.cameras.get(2).cell);
  }

  @Test
  public void invalidSelectionsDoNotChangeDraft() {
    CompositeDraft draft = new CompositeDraft(profile());
    for (int[] pair : new int[][] {{-1, 2}, {16, 2}, {0, 9}})
      try {
        draft.assign(pair[0], pair[1]);
        fail();
      } catch (IllegalArgumentException expected) {
      }
    try {
      draft.resize(9, 4);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    assertEquals(2, draft.cells[0]);
    assertEquals(7, draft.cells[5]);
  }
}
