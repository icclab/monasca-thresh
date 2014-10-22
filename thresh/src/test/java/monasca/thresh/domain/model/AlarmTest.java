/*
 * Copyright (c) 2014 Hewlett-Packard Development Company, L.P.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monasca.thresh.domain.model;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import monasca.common.model.alarm.AggregateFunction;
import monasca.common.model.alarm.AlarmExpression;
import monasca.common.model.alarm.AlarmOperator;
import monasca.common.model.alarm.AlarmState;
import monasca.common.model.alarm.AlarmSubExpression;
import monasca.common.model.metric.MetricDefinition;

import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Test
public class AlarmTest {
  private static final String TEST_ALARM_ID = "1";

  public void shouldBeUndeterminedIfAnySubAlarmIsUndetermined() {
    AlarmExpression expr =
        new AlarmExpression(
            "avg(hpcs.compute{instance_id=5,metric_name=cpu,device=1}, 1) > 5 times 3 AND avg(hpcs.compute{flavor_id=3,metric_name=mem}, 2) < 4 times 3");
    final Alarm alarm = createAlarm(expr);
    final Iterator<SubAlarm> iter = alarm.getSubAlarms().iterator();
    SubAlarm subAlarm1 = iter.next();
    subAlarm1.setState(AlarmState.UNDETERMINED);
    SubAlarm subAlarm2 = iter.next();
    subAlarm2.setState(AlarmState.ALARM);

    assertFalse(alarm.evaluate(expr));
    assertEquals(alarm.getState(), AlarmState.UNDETERMINED);
  }

  private Alarm createAlarm(AlarmExpression expr) {
    final AlarmDefinition alarmDefinition = new AlarmDefinition("42", "Test Def", "", expr, "LOW", true, new ArrayList<String>(0));
    Alarm alarm = new Alarm(alarmDefinition, AlarmState.UNDETERMINED);
    return alarm;
  }

  public void shouldEvaluateExpressionWithBooleanAnd() {
    AlarmExpression expr =
        new AlarmExpression(
            "avg(hpcs.compute{instance_id=5,metric_name=cpu,device=1}, 1) > 5 times 3 AND avg(hpcs.compute{flavor_id=3,metric_name=mem}, 2) < 4 times 3");
    final Alarm alarm = createAlarm(expr);

    final Iterator<SubAlarm> iter = alarm.getSubAlarms().iterator();
    SubAlarm subAlarm1 = iter.next();
    SubAlarm subAlarm2 = iter.next();

    assertFalse(alarm.evaluate(expr));
    assertEquals(alarm.getState(), AlarmState.UNDETERMINED);

    subAlarm1.setState(AlarmState.OK);
    assertFalse(alarm.evaluate(expr));

    // UNDETERMINED -> OK
    subAlarm2.setState(AlarmState.OK);
    assertTrue(alarm.evaluate(expr));
    assertEquals(alarm.getState(), AlarmState.OK);

    subAlarm2.setState(AlarmState.ALARM);
    assertFalse(alarm.evaluate(expr));

    // OK -> ALARM
    subAlarm1.setState(AlarmState.ALARM);
    assertTrue(alarm.evaluate(expr));
    assertEquals(alarm.getState(), AlarmState.ALARM);

    // ALARM -> UNDETERMINED
    subAlarm1.setState(AlarmState.UNDETERMINED);
    assertTrue(alarm.evaluate(expr));
    assertEquals(alarm.getState(), AlarmState.UNDETERMINED);
  }

  public void shouldEvaluateExpressionWithBooleanOr() {
    AlarmExpression expr =
        new AlarmExpression(
            "avg(hpcs.compute{instance_id=5,metric_name=cpu,device=1}, 1) > 5 times 3 OR avg(hpcs.compute{flavor_id=3,metric_name=mem}, 2) < 4 times 3");
    final Alarm alarm = createAlarm(expr);

    final Iterator<SubAlarm> iter = alarm.getSubAlarms().iterator();
    SubAlarm subAlarm1 = iter.next();
    SubAlarm subAlarm2 = iter.next();

    assertFalse(alarm.evaluate(expr));
    assertEquals(alarm.getState(), AlarmState.UNDETERMINED);

    subAlarm1.setState(AlarmState.ALARM);
    assertFalse(alarm.evaluate(expr));

    // UNDETERMINED -> ALARM
    subAlarm2.setState(AlarmState.OK);
    assertTrue(alarm.evaluate(expr));
    assertEquals(alarm.getState(), AlarmState.ALARM);

    // ALARM -> OK
    subAlarm1.setState(AlarmState.OK);
    subAlarm2.setState(AlarmState.OK);
    assertTrue(alarm.evaluate(expr));
    assertEquals(alarm.getState(), AlarmState.OK);

    // OK -> ALARM
    subAlarm2.setState(AlarmState.ALARM);
    assertTrue(alarm.evaluate(expr));
    assertEquals(alarm.getState(), AlarmState.ALARM);

    // ALARM -> ALARM
    assertFalse(alarm.evaluate(expr));
    assertEquals(alarm.getState(), AlarmState.ALARM);

    // ALARM -> UNDETERMINED
    subAlarm2.setState(AlarmState.UNDETERMINED);
    assertTrue(alarm.evaluate(expr));
    assertEquals(alarm.getState(), AlarmState.UNDETERMINED);
  }

  public void shouldBuiltStateChangeReason() {
    AlarmExpression expr =
        new AlarmExpression(
            "avg(hpcs.compute{instance_id=5,metric_name=cpu,device=1}, 1) > 5 times 3 OR avg(hpcs.compute{flavor_id=3,metric_name=mem}, 2) < 4 times 3");
    SubAlarm subAlarm1 =
        new SubAlarm("123", TEST_ALARM_ID, new SubExpression(UUID.randomUUID().toString(), expr
            .getSubExpressions().get(0)));
    SubAlarm subAlarm2 =
        new SubAlarm("456", TEST_ALARM_ID, new SubExpression(UUID.randomUUID().toString(), expr
            .getSubExpressions().get(1)));
    List<String> expressions =
        Arrays.asList(subAlarm1.getExpression().toString(), subAlarm2.getExpression().toString());

    assertEquals(
        Alarm.buildStateChangeReason(AlarmState.UNDETERMINED, expressions),
        "No data was present for the sub-alarms: [avg(hpcs.compute{device=1, instance_id=5, metric_name=cpu}, 1) > 5.0 times 3, avg(hpcs.compute{flavor_id=3, metric_name=mem}, 2) < 4.0 times 3]");

    assertEquals(
        Alarm.buildStateChangeReason(AlarmState.ALARM, expressions),
        "Thresholds were exceeded for the sub-alarms: [avg(hpcs.compute{device=1, instance_id=5, metric_name=cpu}, 1) > 5.0 times 3, avg(hpcs.compute{flavor_id=3, metric_name=mem}, 2) < 4.0 times 3]");
  }

  /**
   * This test is here because this case happened in the Threshold Engine. The AlarmExpression
   * resulted in a MetricDefinition with null dimensions and SubAlarm had empty dimensions and that
   * didn't match causing an IllegalArgumentException. The AlarmSubExpressionListener has been
   * changed to always generate empty dimensions and not null. This test will verify that logic
   * is still working.
   */
  public void testDimensions() {
    final AlarmExpression expression = AlarmExpression.of("max(cpu_system_perc) > 1");
    final MetricDefinition metricDefinition =
        new MetricDefinition("cpu_system_perc", new HashMap<String, String>());
    final AlarmSubExpression ase =
        new AlarmSubExpression(AggregateFunction.MAX, metricDefinition, AlarmOperator.GT, 1, 60, 1);
    final SubAlarm subAlarm = new SubAlarm("123", "456", new SubExpression(UUID.randomUUID().toString(), ase));
    final Map<AlarmSubExpression, Boolean> subExpressionValues =
        new HashMap<AlarmSubExpression, Boolean>();
    subExpressionValues.put(subAlarm.getExpression(), true);
    assertEquals(expression.getSubExpressions().get(0).getMetricDefinition().hashCode(),
        metricDefinition.hashCode());

    // Handle ALARM state
    assertTrue(expression.evaluate(subExpressionValues));
  }
}
