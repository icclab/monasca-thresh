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

import monasca.common.model.alarm.AlarmExpression;
import monasca.common.model.alarm.AlarmState;
import monasca.common.model.alarm.AlarmSubExpression;
import monasca.common.model.domain.common.AbstractEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * An alarm comprised of sub-alarms.
 */
/**
 * @author craigbr
 *
 */
public class Alarm extends AbstractEntity {
  private Map<String, SubAlarm> subAlarms;
  private Set<MetricDefinitionAndTenantId> alarmedMetrics = new HashSet<>();
  private AlarmState state;
  private String stateChangeReason;
  private String alarmDefinitionId;

  public Alarm() {
  }

  public Alarm(AlarmDefinition alarmDefinition, AlarmState state) {
    this.id = UUID.randomUUID().toString();
    List<SubExpression> subExpressions = alarmDefinition.getSubExpressions();
    final List<SubAlarm> subAlarms = new ArrayList<>(subExpressions.size());
    for (final SubExpression subExpr : subExpressions) {
      subAlarms.add(new SubAlarm(UUID.randomUUID().toString(), id, subExpr));
    }
    setSubAlarms(subAlarms);
    this.state = state;
    this.alarmDefinitionId = alarmDefinition.getId();
  }

  static String buildStateChangeReason(AlarmState alarmState, List<String> subAlarmExpressions) {
    if (AlarmState.UNDETERMINED.equals(alarmState)) {
      return String.format("No data was present for the sub-alarms: %s", subAlarmExpressions);
    } else if (AlarmState.ALARM.equals(alarmState)) {
      return String.format("Thresholds were exceeded for the sub-alarms: %s", subAlarmExpressions);
    } else {
      return "The alarm threshold(s) have not been exceeded";
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    Alarm other = (Alarm) obj;

    if (state != other.state) {
      return false;
    }
    if (!compareObjects(alarmDefinitionId, other.alarmDefinitionId)) {
      return false;
    }
    if (!compareObjects(subAlarms, other.subAlarms)) {
      return false;
    }
    if (!compareObjects(stateChangeReason, other.stateChangeReason)) {
      return false;
    }
    if (!compareObjects(alarmedMetrics, other.alarmedMetrics)) {
      return false;
    }
    return true;
  }

  private boolean compareObjects(final Object o1, final Object o2) {
    if (o1 == null) {
      if (o2 != null) {
        return false;
      }
    } else if (!o1.equals(o2)) {
      return false;
    }
    return true;
  }

  /**
   * Evaluates the {@code alarm}, updating the alarm's state if necessary and returning true if the
   * alarm's state changed, else false.
   */
  public boolean evaluate(AlarmExpression expression) {
    AlarmState initialState = state;
    List<String> unitializedSubAlarms = new ArrayList<String>();
    for (SubAlarm subAlarm : subAlarms.values()) {
      if (AlarmState.UNDETERMINED.equals(subAlarm.getState())) {
        unitializedSubAlarms.add(subAlarm.getExpression().toString());
      }
    }

    // Handle UNDETERMINED state
    if (!unitializedSubAlarms.isEmpty()) {
      if (AlarmState.UNDETERMINED.equals(initialState)) {
        return false;
      }
      state = AlarmState.UNDETERMINED;
      stateChangeReason = buildStateChangeReason(state, unitializedSubAlarms);
      return true;
    }

    Map<AlarmSubExpression, Boolean> subExpressionValues =
        new HashMap<AlarmSubExpression, Boolean>();
    for (SubAlarm subAlarm : subAlarms.values()) {
      subExpressionValues.put(subAlarm.getExpression(),
          AlarmState.ALARM.equals(subAlarm.getState()));
    }

    // Handle ALARM state
    if (expression.evaluate(subExpressionValues)) {
      if (AlarmState.ALARM.equals(initialState)) {
        return false;
      }

      List<String> subAlarmExpressions = new ArrayList<String>();
      for (SubAlarm subAlarm : subAlarms.values()) {
        if (AlarmState.ALARM.equals(subAlarm.getState())) {
          subAlarmExpressions.add(subAlarm.getExpression().toString());
        }
      }

      state = AlarmState.ALARM;
      stateChangeReason = buildStateChangeReason(state, subAlarmExpressions);
      return true;
    }

    if (AlarmState.OK.equals(initialState)) {
      return false;
    }
    state = AlarmState.OK;
    stateChangeReason = buildStateChangeReason(state, null);
    return true;
  }

  public AlarmState getState() {
    return state;
  }

  public String getStateChangeReason() {
    return stateChangeReason;
  }

  public Collection<SubAlarm> getSubAlarms() {
    return subAlarms.values();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((state == null) ? 0 : state.hashCode());
    result = prime * result + ((subAlarms == null) ? 0 : subAlarms.hashCode());
    result = prime * result + ((alarmDefinitionId == null) ? 0 : alarmDefinitionId.hashCode());
    result = prime * result + ((stateChangeReason == null) ? 0 : stateChangeReason.hashCode());
    result = prime * result + ((alarmedMetrics == null) ? 0 : alarmedMetrics.hashCode());
    return result;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setState(AlarmState state) {
    this.state = state;
  }

  public void setSubAlarms(List<SubAlarm> subAlarms) {
    this.subAlarms = new HashMap<String, SubAlarm>();
    for (SubAlarm subAlarm : subAlarms) {
      this.subAlarms.put(subAlarm.getId(), subAlarm);
    }
  }

  @Override
  public String toString() {
    final StringBuilder alarmedMetricsString = new StringBuilder();
    for (final MetricDefinitionAndTenantId md : this.alarmedMetrics) {
      if (alarmedMetricsString.length() > 0) {
        alarmedMetricsString.append(',');
      }
      alarmedMetricsString.append(md.toString());
    }
    return String.format("Alarm [id=%s, state=%s, alarmDefinitionid=%s, alarmedMetrics=[%s]]",
        getId(), state, alarmDefinitionId, alarmedMetricsString);
  }

  public void updateSubAlarm(SubAlarm subAlarm) {
    subAlarms.put(subAlarm.getId(), subAlarm);
  }

  public boolean removeSubAlarmById(String toDeleteId) {
    return subAlarms.remove(toDeleteId) != null;
  }

  public String getAlarmDefinitionId() {
    return alarmDefinitionId;
  }

  public void setAlarmDefinitionId(String alarmDefinitionId) {
    this.alarmDefinitionId = alarmDefinitionId;
  }

  public Set<MetricDefinitionAndTenantId> getAlarmedMetrics() {
    return alarmedMetrics;
  }

  public void setAlarmedMetrics(Set<MetricDefinitionAndTenantId> alarmedMetrics) {
    this.alarmedMetrics = alarmedMetrics;
  }

  public void addAlarmedMetric(MetricDefinitionAndTenantId alarmedMetric) {
    this.alarmedMetrics.add(alarmedMetric);
  }
}
