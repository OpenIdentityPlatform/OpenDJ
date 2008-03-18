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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.snmp;

import com.sun.management.snmp.agent.SnmpMib;
import java.util.Iterator;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;

/**
 * The class is used for implementing the "DsTableEntry" group implementation.
 * The group is defined with the following oid: 1.3.6.1.2.1.66.1.1.
 */
public class DsTableEntryImpl extends DsTableEntry implements DsEntry {

  /**
   * The serial version identifier required to satisfy the compiler because
   * this class implements the <CODE>java.io.Serializable</CODE> interface.
   * This value was generated using the <CODE>serialver</CODE> command-line
   * utility included with the Java SDK.
   */
  private static final long serialVersionUID = -3346380035687141480L;
  /**
   * The debug log tracer for this class.
   */
  private static final DebugTracer TRACER = DebugLogger.getTracer();
  /**
   * Directory Server MBeanServer
   */
  private MBeanServer server;
  /**
   * Mapping Class
   */
  private SNMPMonitor monitor;
  /**
   * ObjectName of the entry
   */
  private ObjectName entryName;
  /**
   * Index of the Directory Server Instance (applIndex)
   */
  private Integer applIndex;

  /**
   * Creates a DsTableEntry
   * @param mib the SNMP Mib where the entry will be created
   * @param server where the mapping objects will be found
   * @param index of the entry in the DsTable
   */
  public DsTableEntryImpl(SnmpMib mib,
          MBeanServer server,
          int index) {
    super(mib);
    this.server = server;
    this.monitor = SNMPMonitor.getMonitor(server);
    this.applIndex = new Integer(index);
  }

  /**
   * {@inheritDoc}
   * @return DsCacheHits as Long
   */
  @Override
  public Long getDsCacheHits() {
    try {
      Long value = Long.parseLong((String) this.monitor.getAttribute(
              SNMPConnectionHandlerDefinitions.MONITOR_ENTRY_CACHES_OBJECTNANE,
              "entryCacheHits"));
      return SNMPMonitor.counter32Value(value);
    } catch (Exception ex) {
      if (DebugLogger.debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, ex);
      }
    }
    return 0L;
  }

  /**
   * {@inheritDoc}
   * @return DsCacheEntries as Long
   */
  @Override
  public Long getDsCacheEntries() {
    try {
      Long value = Long.parseLong((String) this.monitor.getAttribute(
              SNMPConnectionHandlerDefinitions.MONITOR_ENTRY_CACHES_OBJECTNANE,
              "currentEntryCacheCount"));
      return SNMPMonitor.gauge32Value(value);
    } catch (Exception ex) {
      if (DebugLogger.debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, ex);
      }
    }
    return 0L;
  }

  /**
   * {@inheritDoc}
   * @return DsMasterEntries as Long
   */
  @Override
  public Long getDsMasterEntries() {
    Set monitorBackends = null;
    Long result = 0L;
    try {
      monitorBackends = this.server.queryNames(SNMPMonitor.pattern, null);
      for (Iterator iter = monitorBackends.iterator(); iter.hasNext();) {
        ObjectName name = (ObjectName) iter.next();
        Object value = this.monitor.getAttribute(name, 
                "ds-backend-entry-count");
        if (value != null) {
          result = result + new Long((String) value);
        }
      }
      return SNMPMonitor.gauge32Value(result);
    } catch (Exception ex) {
      if (DebugLogger.debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, ex);
      }
    }
    return 0L;
  }

  /**
   * {@inheritDoc}
   * @return DsServerDescription as String
   */
  @Override
  public String getDsServerDescription() {
    String result = null;
    try {
      result = (String) this.monitor.getAttribute(
        SNMPConnectionHandlerDefinitions.MONITOR_SYSTEM_INFORMATION_OBJECTNAME,
        "workingDirectory");
    } catch (Exception ex) {
      if (DebugLogger.debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, ex);
      }
    }
    return result;
  }

  /**
   * Gets the object of the entry
   * @return ObjectName of the entry
   */
  public ObjectName getObjectName() {
    if (this.entryName == null) {
      try {
        this.entryName = new ObjectName(
        SNMPConnectionHandlerDefinitions.SNMP_DOMAIN +
        "type=DsTableEntry,name=" +
        SNMPConnectionHandlerDefinitions.MONITOR_SYSTEM_INFORMATION_OBJECTNAME);
      } catch (Exception ex) {
        if (DebugLogger.debugEnabled()) {
          TRACER.debugCaught(DebugLogLevel.ERROR, ex);
        }
      }
    }
    return this.entryName;
  }
}
