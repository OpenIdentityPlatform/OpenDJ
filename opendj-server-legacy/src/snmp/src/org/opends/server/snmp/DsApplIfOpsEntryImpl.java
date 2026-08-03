/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2012-2014 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.snmp;

import com.sun.management.snmp.agent.SnmpMib;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.forgerock.i18n.slf4j.LocalizedLogger;

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

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * ObjectName of the DsApplIfOpsEntry.
   */
  private ObjectName entryName;
  /**
   * MBeanServer where the cn=monitor Connection Handler MBean are registered.
   */
  private MBeanServer server;
  /**
   * ObjectName of the cn=monitor Connection Handler MBean.
   */
  private ObjectName connectionHandlerName;
  /**
   * SNMPMonitor representing the gateway between SNMP MBeans and cn=monitor
   * MBeans.
   */
  private SNMPMonitor monitor;
  /**
   * ObjectName of the MBeans representing the Statistics of the cn=monitor
   * ConnectionHandler.
   */
  private ObjectName stats;

  /**
   * Created a DsApplIfOpsEntry in the SnmpMib.
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
   * Getter for the "DsApplIfProtocol" variable.
   * @return an OID representing the connection handler:port
   */
  public String getDsApplIfProtocol() {
      Object listener = this.monitor.getAttribute(
              this.connectionHandlerName, "ds-connectionhandler-listener");
      if (listener instanceof Object[]) {
          // A connection handler with several listen addresses reports them as an array.
          Object[] listeners = (Object[]) listener;
          listener = listeners.length > 0 ? listeners[0] : null;
      }
      String portNumber = listener != null ? String.valueOf(listener) : null;
      if (portNumber==null) {
          return this.DsApplIfProtocol;
      }
      else {
          int index = portNumber.lastIndexOf(":");
          if (index==-1) {
              return this.DsApplIfProtocol;
          }
          return  new String("1.3.6.1..27.3.") + portNumber.substring(index+1);
      }
  }

  /**
   * Returns the value of the provided connection handler statistic as a
   * counter, or zero if the statistic is not available or does not hold a
   * number.
   *
   * @param statisticName the name of the connection handler statistic
   * @return the counter value of the statistic
   */
  private Long getCounter32Statistic(String statisticName) {
    if (stats == null) {
      stats = this.monitor.getConnectionHandlerStatistics(
              connectionHandlerName);
    }
    if (stats == null) {
      return 0L;
    }
    try {
      long value = Long.parseLong(
              String.valueOf(this.monitor.getAttribute(stats, statisticName)));
      return SNMPMonitor.counter32Value(value);
    } catch (NumberFormatException e) {
      // The statistic is not available or is not a number.
      return 0L;
    }
  }

  /**
   * {@inheritDoc}
   * @return DsApplIfSearchOps
   */
  @Override
  public Long getDsApplIfSearchOps() {
    return getCounter32Statistic("searchRequests");
  }

  /**
   * {@inheritDoc}
   * @return DsApplIfOneLevelSearchOps
   */
  @Override
  public Long getDsApplIfOneLevelSearchOps() {
    return getCounter32Statistic("searchOneRequests");
  }

  /**
   * {@inheritDoc}
   * @return DsApplIfWholeSubtreeSearchOps
   */
  @Override
  public Long getDsApplIfWholeSubtreeSearchOps() {
    return getCounter32Statistic("searchSubRequests");
  }

  /**
   * {@inheritDoc}
   * @return DsApplIfModifyRDNOps
   */
  @Override
  public Long getDsApplIfModifyRDNOps() {
    return getCounter32Statistic("modifyDNRequests");
  }

  /**
   * {@inheritDoc}
   * @return DsApplIfModifyEntryOps
   */
  @Override
  public Long getDsApplIfModifyEntryOps() {
    return getCounter32Statistic("modifyRequests");
  }

  /**
   * {@inheritDoc}
   * @return DsApplIfRemoveEntryOps
   */
  @Override
  public Long getDsApplIfRemoveEntryOps() {
    return getCounter32Statistic("deleteRequests");
  }

  /**
   * {@inheritDoc}
   * @return DsApplIfAddEntryOps
   */
  @Override
  public Long getDsApplIfAddEntryOps() {
    return getCounter32Statistic("addRequests");
  }

  /**
   * {@inheritDoc}
   * @return DsApplIfCompareOps
   */
  @Override
  public Long getDsApplIfCompareOps() {
    return getCounter32Statistic("compareRequests");
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
    return getCounter32Statistic("bytesWritten");
  }

  /**
   * {@inheritDoc}
   * @return DsApplIfInBytes
   */
  @Override
  public Long getDsApplIfInBytes() {
    return getCounter32Statistic("bytesRead");
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
   * Returns the ObjectName of the SNMP entry MBean.
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
        logger.traceException(ex);
        return null;
      }
    }
    return this.entryName;
  }
}
