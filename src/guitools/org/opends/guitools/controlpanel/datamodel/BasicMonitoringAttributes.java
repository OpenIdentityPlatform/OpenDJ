/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.guitools.controlpanel.datamodel;

import static org.opends.messages.AdminToolMessages.*;

import org.opends.messages.Message;
import org.opends.server.monitors.TraditionalWorkQueueMonitor;
import org.opends.server.util.ServerConstants;

/**
 * The enumeration containing the different monitoring attributes that can be
 * displayed in the monitoring panels.
 */
public enum BasicMonitoringAttributes implements MonitoringAttributes
{
  /**
   * Start Date.
   */
  START_DATE(Message.EMPTY, ServerConstants.ATTR_START_TIME),
  /**
   * Current Date.
   */
  CURRENT_DATE(Message.EMPTY, ServerConstants.ATTR_CURRENT_TIME),
  /**
   * Current Connections.
   */
  CURRENT_CONNECTIONS(Message.EMPTY, ServerConstants.ATTR_CURRENT_CONNS),
  /**
   * Maximum Connections.
   */
  MAX_CONNECTIONS(Message.EMPTY, ServerConstants.ATTR_MAX_CONNS),
  /**
   * Total Connections.
   */
  TOTAL_CONNECTIONS(Message.EMPTY, ServerConstants.ATTR_TOTAL_CONNS),
  /**
   * Average Request Backlog.
   */
  AVERAGE_REQUEST_BACKLOG(INFO_CTRL_PANEL_AVERAGE_REQUEST_BACKLOG.get(),
  TraditionalWorkQueueMonitor.ATTR_AVERAGE_BACKLOG),
  /**
   * Max Request Backlog.
   */
  MAX_REQUEST_BACKLOG(INFO_CTRL_PANEL_MAX_REQUEST_BACKLOG.get(),
  TraditionalWorkQueueMonitor.ATTR_MAX_BACKLOG),
  /**
   * Current Request Backlog.
   */
  CURRENT_REQUEST_BACKLOG(INFO_CTRL_PANEL_CURRENT_REQUEST_BACKLOG.get(),
  TraditionalWorkQueueMonitor.ATTR_CURRENT_BACKLOG),
  /**
   * Requests submitted.
   */
  REQUESTS_SUBMITTED(INFO_CTRL_PANEL_REQUESTS_SUBMITTED.get(),
  TraditionalWorkQueueMonitor.ATTR_OPS_SUBMITTED),
  /**
   * Requests rejected.
   */
  REQUESTS_REJECTED(INFO_CTRL_PANEL_REQUESTS_REJECTED.get(),
  TraditionalWorkQueueMonitor.ATTR_OPS_REJECTED_QUEUE_FULL),
  /**
   * Entry cache Hits.
   */
  ENTRY_CACHE_HITS(INFO_CTRL_PANEL_ENTRY_CACHE_HITS.get(),
  "entryCacheHits"),
  /**
   * Current entry cache count.
   */
  CURRENT_ENTRY_CACHE_COUNT(INFO_CTRL_PANEL_CURRENT_ENTRY_CACHE_COUNT.get(),
  "currentEntryCacheCount"),
  /**
   * Entry cache tries.
   */
  ENTRY_CACHE_TRIES(INFO_CTRL_PANEL_ENTRY_CACHE_TRIES.get(),
  "entryCacheTries"),
  /**
   * Entry cache hit ratio.
   */
  ENTRY_CACHE_HIT_RATIO(INFO_CTRL_PANEL_ENTRY_CACHE_HIT_RATIO.get(),
  "entryCacheHitRatio"),
  /**
   * Entry cache current size.
   */
  CURRENT_ENTRY_CACHE_SIZE(INFO_CTRL_PANEL_CURRENT_ENTRY_CACHE_SIZE.get(),
  "currentEntryCacheSize"),
  /**
   * Entry cache max size.
   */
  MAX_ENTRY_CACHE_SIZE(INFO_CTRL_PANEL_MAX_ENTRY_CACHE_SIZE.get(),
  "maxEntryCacheSize"),
  /**
   * Entry cache max count.
   */
  MAX_ENTRY_CACHE_COUNT(INFO_CTRL_PANEL_MAX_ENTRY_CACHE_COUNT.get(),
  "maxEntryCacheCount"),
  /**
   * Available CPUs.
   */
  AVAILABLE_CPUS(INFO_CTRL_PANEL_AVAILABLE_CPUS.get(),
  "availableCPUs"),
  /**
   * System Name.
   */
  SYSTEM_NAME(INFO_CTRL_PANEL_SYSTEM_NAME.get(),
  "systemName"),
  /**
   * Operating System.
   */
  OPERATING_SYSTEM(INFO_CTRL_PANEL_OPERATING_SYSTEM.get(),
  "operatingSystem"),
  /**
   * Free used memory.
   */
  FREE_USED_MEMORY(INFO_CTRL_PANEL_FREE_USED_MEMORY.get(),
  "freeUsedMemory"),
  /**
   * Max memory.
   */
  MAX_MEMORY(INFO_CTRL_PANEL_MAX_MEMORY.get(),
  "maxMemory"),
  /**
   * Used memory.
   */
  USED_MEMORY(INFO_CTRL_PANEL_USED_MEMORY.get(),
  "usedMemory"),
  /**
   * Class path.
   */
  CLASS_PATH(INFO_CTRL_PANEL_CLASS_PATH.get(),
  "classPath"),
  /**
   * Java Vendor.
   */
  JAVA_VENDOR(INFO_CTRL_PANEL_JAVA_VENDOR.get(),
  "javaVendor"),
  /**
   * JVM Vendor.
   */
  JVM_VENDOR(INFO_CTRL_PANEL_JVM_VENDOR.get(),
  "javaVendor"),
  /**
   * Java Version.
   */
  JAVA_VERSION(INFO_CTRL_PANEL_JAVA_VERSION.get(),
  "javaVersion"),
  /**
   * JVM Version.
   */
  JVM_VERSION(INFO_CTRL_PANEL_JVM_VERSION.get(),
  "jvmVersion"),
  /**
   * JVM Architecture.
   */
  JVM_ARCHITECTURE(INFO_CTRL_PANEL_JVM_ARCHITECTURE.get(),
  "jvmArchitecture"),
  /**
   * JVM Arguments.
   */
  JVM_ARGUMENTS(INFO_CTRL_PANEL_JVM_ARGUMENTS.get(),
  "jvmArguments"),
  /**
   * Add Request.
   */
  ADD_REQUESTS(INFO_CTRL_PANEL_ADD_REQUESTS_LABEL.get(),
  "addRequests"),
  /**
   * Add Responses.
   */
  ADD_RESPONSES(INFO_CTRL_PANEL_ADD_RESPONSES_LABEL.get(),
  "addResponses"),
  /**
   * Bind Request.
   */
  BIND_REQUESTS(INFO_CTRL_PANEL_BIND_REQUESTS_LABEL.get(),
  "bindRequests"),
  /**
   * Bind Responses.
   */
  BIND_RESPONSES(INFO_CTRL_PANEL_BIND_RESPONSES_LABEL.get(),
  "bindResponses"),
  /**
   * Compare Requests.
   */
  COMPARE_REQUESTS(INFO_CTRL_PANEL_COMPARE_REQUESTS_LABEL.get(),
  "compareRequests"),
  /**
   * Compare Responses.
   */
  COMPARE_RESPONSES(INFO_CTRL_PANEL_COMPARE_RESPONSES_LABEL.get(),
  "compareResponses"),
  /**
   * Delete Request.
   */
  DELETE_REQUESTS(INFO_CTRL_PANEL_DELETE_REQUESTS_LABEL.get(),
  "deleteRequests"),
  /**
   * Delete Responses.
   */
  DELETE_RESPONSES(INFO_CTRL_PANEL_DELETE_RESPONSES_LABEL.get(),
  "deleteResponses"),
  /**
   * Extended Request.
   */
  EXTENDED_REQUESTS(INFO_CTRL_PANEL_EXTENDED_REQUESTS_LABEL.get(),
  "extendedRequests"),
  /**
   * Extended Responses.
   */
  EXTENDED_RESPONSES(INFO_CTRL_PANEL_EXTENDED_RESPONSES_LABEL.get(),
  "extendedResponses"),
  /**
   * Modify DN Request.
   */
  MOD_DN_REQUESTS(INFO_CTRL_PANEL_MOD_DN_REQUESTS_LABEL.get(),
  "modifyDNRequests"),
  /**
   * Modify DN Responses.
   */
  MOD_DN_RESPONSES(INFO_CTRL_PANEL_MOD_DN_RESPONSES_LABEL.get(),
  "modifyDNResponses"),
  /**
   * Modify Request.
   */
  MOD_REQUESTS(INFO_CTRL_PANEL_MOD_REQUESTS_LABEL.get(),
  "modifyRequests"),
  /**
   * Modify Responses.
   */
  MOD_RESPONSES(INFO_CTRL_PANEL_MOD_RESPONSES_LABEL.get(),
  "modifyResponses"),
  /**
   * Search Request.
   */
  SEARCH_REQUESTS(INFO_CTRL_PANEL_SEARCH_REQUESTS_LABEL.get(),
  "searchRequests"),
  /**
   * Searches Done.
   */
  SEARCH_DONE(INFO_CTRL_PANEL_SEARCH_DONE_LABEL.get(),
  "searchResultsDone"),
  /**
   * Unbind Request.
   */
  UNBIND_REQUESTS(INFO_CTRL_PANEL_UNBIND_REQUESTS_LABEL.get(),
  "unbindRequests"),;

  static
  {
    // Do this here, since static references do not work if this is done
    // in the constructor.
    for (BasicMonitoringAttributes attr : BasicMonitoringAttributes.values())
    {
      attr.calculateProperties();
    }
  }

  private Message msg;
  private String attributeName;
  private boolean isAborted;
  private boolean isNumeric;
  private boolean isGMTDate;
  private boolean isValueInBytes;
  private boolean canHaveAverage;
  private boolean isTime;

  private BasicMonitoringAttributes(Message msg, String attributeName)
  {
    this.msg = msg;
    this.attributeName = attributeName;
  }

  /**
   * {@inheritDoc}
   */
  public Message getMessage()
  {
    return msg;
  }

  /**
   * {@inheritDoc}
   */
  public String getAttributeName()
  {
    return attributeName;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isAborted()
  {
    return isAborted;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isNumeric()
  {
    return isNumeric;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isTime()
  {
    return isTime;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isGMTDate()
  {
    return isGMTDate;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isValueInBytes()
  {
    return isValueInBytes;
  }

  /**
   * {@inheritDoc}
   */
  public boolean canHaveAverage()
  {
    return canHaveAverage;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isNumericDate()
  {
    return false;
  }

  private void calculateProperties()
  {
    isAborted = attributeName.indexOf("ds-mon-aborted-") == 0;

    canHaveAverage = attributeName.endsWith("total-count") ||
    attributeName.endsWith("Requests") ||
    attributeName.endsWith("Responses") ||
    this == SEARCH_DONE;

    isGMTDate = this == START_DATE ||
    this == CURRENT_DATE;

    isValueInBytes = this == FREE_USED_MEMORY ||
    this == MAX_MEMORY ||
    this == USED_MEMORY;

    isTime = attributeName.indexOf("time") != -1;

    isNumeric =
    !this.isGMTDate() &&
    this != SYSTEM_NAME &&
    this != OPERATING_SYSTEM &&
    this != CLASS_PATH &&
    this != JAVA_VENDOR &&
    this != JVM_VENDOR &&
    this != JAVA_VERSION &&
    this != JVM_VERSION &&
    this != JVM_ARCHITECTURE &&
    this != JVM_ARGUMENTS;
  }
}
