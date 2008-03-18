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

import com.sun.management.snmp.SnmpStatusException;
import com.sun.management.snmp.agent.SnmpMib;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;

/**
 * The class is used for representing "DsApplIfOpsEntryImpl" implementation.
 */
public class DsApplIfOpsEntryImpl extends DsApplIfOpsEntry implements DsEntry {

  /**
   * The serial version identifier required to satisfy the compiler because
   * this class implements the <CODE>java.io.Serializable</CODE> interface.
   * This value was generated using the <CODE>serialver</CODE> command-line
   * utility included with the Java SDK.
   */
  private static final long serialVersionUID = 3876259684025799091L;
  /**
   * The debug log tracer for this class.
   */
  private static final DebugTracer TRACER = DebugLogger.getTracer();
  /**
   * ObjectName of the DsApplIfOpsEntry
   */
  private ObjectName entryName;
  /**
   * MBeanServer where the cn=monitor Connection Handler MBean are registered 
   */
  private MBeanServer server;
  /**
   * ObjectName of the cn=monitor Connection Handler MBean
   */
  private ObjectName connectionHandlerName;
  /**
   * SNMPMonitor representing the gateway beetween SNMP MBeans and cn=monitor
   * MBeans
   */
  private SNMPMonitor monitor;
  /**
   * ObjectName of the MBeans representing the Statistics of the cn=monitor
   * ConnectionHandler 
   */
  private ObjectName stats;

  /**
   * Created a DsApplIfOpsEntry in the SnmpMib
   * @param mib where the entry has to be created
   * @param server where the corresponding cn=monitor MBean are registered
   * @param connectionHandlerObjectName mapping ObjectName
   * @param applIndex key in the DsTable
   * @param connectionHandlerIndex key corresponding to this entry in the 
   * DsApplIfOpsTable
   */
  public DsApplIfOpsEntryImpl(SnmpMib mib, MBeanServer server,
          ObjectName connectionHandlerObjectName, int applIndex, 
          int connectionHandlerIndex) {
    super(mib);
    this.server = server;
    this.connectionHandlerName = connectionHandlerObjectName;
    this.ApplIndex = new Integer(applIndex);
    this.DsApplIfProtocolIndex = new Integer(connectionHandlerIndex);
    this.monitor = SNMPMonitor.getMonitor(server);
  }

  /**
   * {@inheritDoc}
   * @return DsApplIfSearchOps
   */
  @Override
  public Long getDsApplIfSearchOps() {
    if (stats == null) {
      stats = this.monitor.getConnectionHandlerStatistics(
              connectionHandlerName);
    }
    if (stats != null) {
      long value = Long.parseLong((String) this.monitor.getAttribute(stats, 
              "searchRequests"));
      return SNMPMonitor.counter32Value(value);
    } else {
      return 0L;
    }

  }

  /**
   * {@inheritDoc}
   * @return DsApplIfModifyRDNOps
   */
  @Override
  public Long getDsApplIfModifyRDNOps() {
    if (stats == null) {
      stats = this.monitor.getConnectionHandlerStatistics(
              connectionHandlerName);
    }
    if (stats != null) {
      long value = Long.parseLong((String) this.monitor.getAttribute(
              stats, "modifyDNRequests"));
      return SNMPMonitor.counter32Value(value);
    } else {
      return 0L;
    }
  }

  /**
   * {@inheritDoc}
   * @return DsApplIfModifyEntryOps
   */
  @Override
  public Long getDsApplIfModifyEntryOps() {
    if (stats == null) {
      stats = this.monitor.getConnectionHandlerStatistics(
              connectionHandlerName);
    }
    if (stats != null) {
      long value = Long.parseLong((String) this.monitor.getAttribute(
              stats, "modifyRequests"));
      return SNMPMonitor.counter32Value(value);
    } else {
      return 0L;
    }
  }

  /**
   * {@inheritDoc}
   * @return DsApplIfRemoveEntryOps
   */
  @Override
  public Long getDsApplIfRemoveEntryOps() {
    if (stats == null) {
      stats = this.monitor.getConnectionHandlerStatistics(
              connectionHandlerName);
    }
    if (stats != null) {
      long value = Long.parseLong((String) this.monitor.getAttribute(
              stats, "deleteRequests"));
      return SNMPMonitor.counter32Value(value);
    } else {
      return 0L;
    }
  }

  /**
   * {@inheritDoc}
   * @return DsApplIfAddEntryOps
   */
  @Override
  public Long getDsApplIfAddEntryOps() {
    if (stats == null) {
      stats = this.monitor.getConnectionHandlerStatistics(
              connectionHandlerName);
    }
    if (stats != null) {
      long value = Long.parseLong((String) this.monitor.getAttribute(
              stats, "addRequests"));
      return SNMPMonitor.counter32Value(value);
    } else {
      return 0L;
    }
  }

  /**
   * {@inheritDoc}
   * @return DsApplIfCompareOps
   */
  @Override
  public Long getDsApplIfCompareOps() {
    if (stats == null) {
      stats = this.monitor.getConnectionHandlerStatistics(
              connectionHandlerName);
    }
    if (stats != null) {
      long value = Long.parseLong((String) this.monitor.getAttribute(
              stats, "compareRequests"));
      return SNMPMonitor.counter32Value(value);
    } else {
      return 0L;
    }
  }

  /**
   * {@inheritDoc}
   * @return DsApplIfReadOps
   */
  @Override
  public Long getDsApplIfReadOps() {
    return this.getDsApplIfCompareOps() + 
            this.getDsApplIfAddEntryOps() + 
            this.getDsApplIfRemoveEntryOps() + 
            this.getDsApplIfModifyEntryOps() + 
            this.getDsApplIfModifyRDNOps() + 
            this.getDsApplIfSearchOps();
  }

  /**
   * {@inheritDoc}
   * @return DsApplIfOutBytes
   */
  @Override
  public Long getDsApplIfOutBytes() {
    if (stats == null) {
      stats = this.monitor.getConnectionHandlerStatistics(
              connectionHandlerName);
    }
    if (stats != null) {
      long value = Long.parseLong((String) this.monitor.getAttribute(
              stats, "bytesWritten"));
      return SNMPMonitor.counter32Value(value);
    } else {
      return 0L;
    }
  }

  /**
   * {@inheritDoc}
   * @return DsApplIfInBytes
   */
  @Override
  public Long getDsApplIfInBytes() {
    if (stats == null) {
      stats = this.monitor.getConnectionHandlerStatistics(
              connectionHandlerName);
    }
    if (stats != null) {
      long value = Long.parseLong((String) this.monitor.getAttribute(
              stats, "bytesRead"));
      return SNMPMonitor.counter32Value(value);
    } else {
      return 0L;
    }
  }

  /**
   * {@inheritDoc}
   * @return DsApplIfProtocolIndex
   */
  @Override
  public Integer getDsApplIfProtocolIndex() {
    return this.DsApplIfProtocolIndex;
  }

  /**
   * {@inheritDoc}
   * @return ApplIndex index of the corresponding DsTable entry 
   */
  @Override
  public Integer getApplIndex() {
    return this.ApplIndex;
  }

  /**
   * Returns the ObjectName of the SNMP entry MBean
   * @return ObjectName of the entry
   */
  public ObjectName getObjectName() {
    if (this.entryName == null) {
      try {
        String name = this.connectionHandlerName.getKeyProperty("Rdn2");
        this.entryName = new ObjectName(
                SNMPConnectionHandlerDefinitions.SNMP_DOMAIN +
                "type=DsApplIfOpsEntry,name=" + name);
      } catch (Exception ex) {
        if (DebugLogger.debugEnabled()) {
           TRACER.debugCaught(DebugLogLevel.ERROR, ex);
        }
        return null;
      }
      return this.entryName;
    }
    return this.entryName;
  }
}
