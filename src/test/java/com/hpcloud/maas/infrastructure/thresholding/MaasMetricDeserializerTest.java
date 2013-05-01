package com.hpcloud.maas.infrastructure.thresholding;

import static org.testng.Assert.assertEquals;

import java.util.Collections;
import java.util.List;

import org.testng.annotations.Test;

import com.hpcloud.maas.common.model.metric.FlatMetric;
import com.hpcloud.maas.common.model.metric.FlatMetrics;

/**
 * @author Jonathan Halterman
 */
@Test
public class MaasMetricDeserializerTest {
  private MaasMetricDeserializer deserializer = new MaasMetricDeserializer();

  public void shouldDeserialize() {
    FlatMetric initial = new FlatMetric("bob", "test", "1", null, 123, 5.0);
    List<List<?>> metrics = deserializer.deserialize(FlatMetrics.toJson(initial));
    assertEquals(metrics, Collections.singletonList(Collections.singletonList(initial.toMetric())));
  }
}
