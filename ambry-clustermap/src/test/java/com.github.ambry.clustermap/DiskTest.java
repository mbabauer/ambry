package com.github.ambry.clustermap;

import com.github.ambry.config.ClusterMapConfig;
import com.github.ambry.config.VerifiableProperties;
import java.util.Properties;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;


// TestDisk permits Disk to be constructed with a null DataNode.
class TestDisk extends Disk {
  public TestDisk(JSONObject jsonObject, ClusterMapConfig clusterMapConfig)
      throws JSONException {
    super(null, jsonObject, clusterMapConfig);
  }

  @Override
  public void validateDataNode() {
    // Null DataNodeId OK for test.
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    TestDisk testDisk = (TestDisk) o;

    if (!getMountPath().equals(testDisk.getMountPath())) {
      return false;
    }
    if (getRawCapacityInBytes() != testDisk.getRawCapacityInBytes()) {
      return false;
    }
    return getHardState() == testDisk.getHardState();
  }

  @Override
  public HardwareState getState() {
    return (isHardDown() || isSoftDown()) ? HardwareState.UNAVAILABLE : HardwareState.AVAILABLE;
  }
}

/**
 * Tests {@link Disk} class.
 */
public class DiskTest {
  @Test
  public void basics()
      throws JSONException {
    JSONObject jsonObject = TestUtils.getJsonDisk("/mnt1", HardwareState.AVAILABLE, 100 * 1024 * 1024 * 1024L);
    ClusterMapConfig clusterMapConfig = new ClusterMapConfig(new VerifiableProperties(new Properties()));

    Disk testDisk = new TestDisk(jsonObject, clusterMapConfig);

    assertEquals(testDisk.getMountPath(), "/mnt1");
    assertEquals(testDisk.getHardState(), HardwareState.AVAILABLE);
    assertEquals(testDisk.getRawCapacityInBytes(), 100 * 1024 * 1024 * 1024L);
    assertEquals(testDisk.toJSONObject().toString(), jsonObject.toString());
    assertEquals(testDisk, new TestDisk(testDisk.toJSONObject(), clusterMapConfig));
  }

  public void failValidation(JSONObject jsonObject, ClusterMapConfig clusterMapConfig)
      throws JSONException {
    try {
      new TestDisk(jsonObject, clusterMapConfig);
      fail("Construction of TestDisk should have failed validation.");
    } catch (IllegalStateException e) {
      // Expected.
    }
  }

  @Test
  public void validation()
      throws JSONException {
    ClusterMapConfig clusterMapConfig = new ClusterMapConfig(new VerifiableProperties(new Properties()));
    try {
      // Null DataNode
      new Disk(null, TestUtils.getJsonDisk("/mnt1", HardwareState.AVAILABLE, 100 * 1024 * 1024 * 1024L),
          clusterMapConfig);
      fail("Construction of Disk should have failed validation.");
    } catch (IllegalStateException e) {
      // Expected.
    }

    // Bad mount path (empty)
    failValidation(TestUtils.getJsonDisk("", HardwareState.AVAILABLE, 100 * 1024 * 1024 * 1024L), clusterMapConfig);

    // Bad mount path (relative path)
    failValidation(TestUtils.getJsonDisk("mnt1", HardwareState.AVAILABLE, 100 * 1024 * 1024 * 1024L), clusterMapConfig);

    // Bad capacity (too small)
    failValidation(TestUtils.getJsonDisk("/mnt1", HardwareState.UNAVAILABLE, 0), clusterMapConfig);

    // Bad capacity (too big)
    failValidation(TestUtils
        .getJsonDisk("/mnt1", HardwareState.UNAVAILABLE, 1024 * 1024 * 1024 * 1024 * 1024 * 1024 * 1024 * 1024L),
        clusterMapConfig);
  }

  @Test
  public void testDiskSoftState()
      throws JSONException, InterruptedException {
    JSONObject jsonObject = TestUtils.getJsonDisk("/mnt1", HardwareState.AVAILABLE, 100 * 1024 * 1024 * 1024L);
    ClusterMapConfig clusterMapConfig = new ClusterMapConfig(new VerifiableProperties(new Properties()));

    long windowMs = clusterMapConfig.clusterMapDiskWindowMs;
    int threshold = clusterMapConfig.clusterMapDiskErrorThreshold;
    long retryBackoffMs = clusterMapConfig.clusterMapDiskRetryBackoffMs;

    Disk testDisk = new TestDisk(jsonObject, clusterMapConfig);
    for (int i = 0; i <= threshold; i++) {
      assertEquals(testDisk.getState(), HardwareState.AVAILABLE);
      testDisk.onDiskError();
    }
    assertEquals(testDisk.getState(), HardwareState.UNAVAILABLE);
    Thread.sleep(retryBackoffMs + 1);
    assertEquals(testDisk.getState(), HardwareState.AVAILABLE);

    if (threshold > 1) {
      for (int i = 0; i <= threshold; i++) {
        assertEquals(testDisk.getState(), HardwareState.AVAILABLE);
        testDisk.onDiskError();
        Thread.sleep(windowMs + 1);
      }
    }
    assertEquals(testDisk.getState(), HardwareState.AVAILABLE);
  }
}
