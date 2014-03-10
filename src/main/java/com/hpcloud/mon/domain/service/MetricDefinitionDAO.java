package com.hpcloud.mon.domain.service;

import java.util.List;

import com.hpcloud.mon.common.model.metric.MetricDefinition;

/**
 * Metric definition data access object.
 * 
 * @author Jonathan Halterman
 */
public interface MetricDefinitionDAO {
  /** Finds all metric definitions for all alarms. */
  List<MetricDefinition> findForAlarms();
}
