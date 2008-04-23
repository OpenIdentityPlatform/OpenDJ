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


import javax.management.MBeanServer;
import com.sun.management.snmp.agent.SnmpMib;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;


/**
 * The class reprents the "DsMIB" group implementation.
 * The group is defined with the following oid: 1.3.6.1.2.1.66.
 */
public class DsMIBImpl extends DsMIB implements NotificationListener {

   /**
   * The serial version identifier required to satisfy the compiler because
   * this class implements the <CODE>java.io.Serializable</CODE> interface.
   * This value was generated using the <CODE>serialver</CODE> command-line
   * utility included with the Java SDK.
   */
  private static final long serialVersionUID = 6787374593664749374L;

  /**
   * The debug log tracer for this class.
   */
  private static final DebugTracer TRACER = DebugLogger.getTracer();

  /**
   * Directory Server MIB access.
   */
  private SnmpMib mib;

  /**
   * Register or not the SNMP MBean.
   */
  private boolean registeredSnmpMBean = false;

  /**
   * List of DsTableEntries.
   */
  private Hashtable<ObjectName, DsEntry> dsTableEntries =
          new Hashtable<ObjectName, DsEntry>();

  /**
   * List of DsIntTableEntries.
   */
  private Hashtable<ObjectName, DsEntry> dsApplIfOpsTableEntries =
          new Hashtable<ObjectName, DsEntry>();

   /**
   * List of DsIntTableEntries.
   */
  private Hashtable<ObjectName, DsEntry> dsIntTableEntries =
          new Hashtable<ObjectName, DsEntry>();

  /**
   * Directory Server MBeanServer.
   */
  private MBeanServer server;

  /**
   * cn=monitor Mapping Class SNMP->MBean.
   */
  private SNMPMonitor monitor;

  /**
   * Start Table indexes.
   */
  private int applIndex = 1;
  private int applIfOpsIndex = 1;
  private int intIndex = 1;

  /**
   * Constructor for the "DsMIB" group.
   * If the group contains a table, the entries created through an SNMP SET
   * will not be registered in Java DMK.
   * @param myMib snmp mib
   * @param server where the MBeans are registered
   * @param registered if the MBeans should be registered in server
   */
  public DsMIBImpl(SnmpMib myMib, MBeanServer server, boolean registered) {
    super(myMib);
    this.mib = myMib;
    this.server = server;
    this.monitor = SNMPMonitor.getMonitor(server);
    this.registeredSnmpMBean = registered;
    this.dsTableEntries.clear();
    this.dsApplIfOpsTableEntries.clear();
    this.dsIntTableEntries.clear();
    // Initialize the MIB
    initDsTables();

    if (DebugLogger.debugEnabled()) {
      TRACER.debugVerbose("DsMIB Group Created");
    }
  }

  /**
   * Returns the Set of ObjectName of all the created entries in all the Table.
   * @return The Set of ObjectName
   */
  @SuppressWarnings("unchecked")
  public Set<ObjectName> getEntriesObjectNames() {
    Set<ObjectName> results = new HashSet<ObjectName>();
    results.addAll(this.dsTableEntries.keySet());
    results.addAll(this.dsApplIfOpsTableEntries.keySet());
    results.addAll(this.dsIntTableEntries.keySet());
    return results;
  }

  /**
   * Returns the list of Created Entries.
   * @return Set of created entries
   */
  @SuppressWarnings("unchecked")
  public Set<ObjectName> getEntries() {
    Set results = new HashSet();
    results.addAll(this.dsTableEntries.values());
    results.addAll(this.dsApplIfOpsTableEntries.values());
    results.addAll(this.dsIntTableEntries.values());
    return results;
  }

  /**
   * handleNotification callback called when an MBeansServer Notification is
   * received.
   * @param notification received
   * @param handback The handback
   */
  public void handleNotification(Notification notification, Object handback) {
    if (notification instanceof MBeanServerNotification) {
      MBeanServerNotification notif = (MBeanServerNotification) notification;
      // Process the ConnectionHandler Registration
      if (notif.getType().equals(
              MBeanServerNotification.REGISTRATION_NOTIFICATION)) {
        ObjectName name = notif.getMBeanName();
        if ((name.getKeyProperty("Rdn1").equals("cn-monitor")) &&
                (isAConnectionHandler(name))) {
          addRowInDsApplIfOpsTable(name);
        }
      }
      // Process the ConnectionHandler unregistration
      else if (notif.getType().equals(
              MBeanServerNotification.UNREGISTRATION_NOTIFICATION)) {
        ObjectName name = notif.getMBeanName();
        if ((name.getKeyProperty("Rdn1").equals("cn-monitor")) &&
                (isAConnectionHandler(name))) {
          removeRowInDsApplIfOpsTable(name);
        }
      }
    }
  }

  /**
   * initDsTables in the dsMib Group.
   */
  private void initDsTables() {

    // Initialize the DSTable with one Entry : Current Directory Server
    initializeDsTable();

    // Initialize the DsApplIfOpsTable with one entry per Connection Handler.
    initializeDsApplIfOpsTable();

    // Register as listener of the MBeanServer Notification to process
    // new Connection Handler MBeans
    try {
      ObjectName name = new ObjectName(
              "JMImplementation:type=MBeanServerDelegate");
      this.server.addNotificationListener(name, this, null, null);
    } catch (Exception ex) {
      if (DebugLogger.debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, ex);
      }
    }
  }

  /**
   * initializeDsTable with the current Directory server instance.
   * Only one entry is created in this table
   **/
  private void initializeDsTable() {
    // Add the Current Directory Server Instance in the DsTable
    addRowInDsTable();
  }

  /**
   * initializeDsApplIfOpsTable with the already registered Connection
   * Handlers.
   **/
  private void initializeDsApplIfOpsTable() {
    // Get the list of ConnectionHandlers MBeans
    Set connectionHandlers = this.monitor.getConnectionHandlers();
    for (Iterator iter=connectionHandlers.iterator();iter.hasNext();) {
      ObjectName name = (ObjectName)iter.next();
      // Add the ConnectionHandler in the DsApplIfOpsTable
      addRowInDsApplIfOpsTable(name);
    }
  }

 /**
  * addRowInDsTable.
  * @return true if the entry has been added else false
  */
  private boolean addRowInDsTable() {

    try {

      // Create the entry
      DsTableEntryImpl entry = new DsTableEntryImpl(
              this.mib, this.server, this.applIndex);

      // if the entry alreday exists nothing to do
      if ((this.dsTableEntries.containsKey(entry.getObjectName())) ||
              (entry == null)) {
        return true;
      }

      // Add the entry in the table
      this.DsTable.addEntry(entry, entry.getObjectName());
      this.dsTableEntries.put(entry.getObjectName(), (DsEntry)entry);
      if (this.registeredSnmpMBean) {
        // Register the SNMP OID MBean
        this.server.registerMBean(entry, entry.getObjectName());
      }
    } catch (Exception ex) {
        if (DebugLogger.debugEnabled()) {
           TRACER.debugCaught(DebugLogLevel.ERROR, ex);
        }
        return false;
    }
    return true;
  }

  /**
   * addRowInDsIntTable Not Supported.
   * @return false (Not Supported for this current delivery)
   */
  private boolean addRowInDsIntTable() {
    return false;
  }

  /**
   * addRowInDsApplIfOpsTable.
   * @param connectionHandlerName to add
   * @return true if the entry has been added else false
   */
  private boolean addRowInDsApplIfOpsTable(ObjectName connectionHandlerName) {
    try {

      // Created the entry
      DsApplIfOpsEntryImpl entry = new DsApplIfOpsEntryImpl(
              this.mib, this.server, connectionHandlerName,
              this.applIndex, this.applIfOpsIndex);

      // If the entry already exists then nothing to do
      if ((this.dsApplIfOpsTableEntries.containsKey(entry.getObjectName())) ||
              (entry == null)) {
        return true;
      }
      // Add the entry in the Table
      this.DsApplIfOpsTable.addEntry(entry);
      this.dsApplIfOpsTableEntries.put(entry.getObjectName(), entry);
      this.applIfOpsIndex++;
      if (this.registeredSnmpMBean) {
        // Register the SNMP OID MBean in the MBeanServer
        this.server.registerMBean(entry, entry.getObjectName());
      }
    } catch (Exception ex) {
      if (DebugLogger.debugEnabled()) {
          TRACER.debugCaught(DebugLogLevel.ERROR, ex);
      }
      return false;
    }
    return true;
  }

  /**
   * removeRowInDsApplIfOpsTable.
   * @param connectionHandlerName
   * @return true if the entry has been removed else false
   */
  private boolean removeRowInDsApplIfOpsTable(ObjectName connectionHandlerName){
    try {
      // Check if the entry is known
      if (!this.dsApplIfOpsTableEntries.containsKey(connectionHandlerName)) {
        return false;
      }
      DsApplIfOpsEntryImpl entry = (DsApplIfOpsEntryImpl)
              this.dsApplIfOpsTableEntries.get(connectionHandlerName);

      this.DsApplIfOpsTable.removeEntry((DsApplIfOpsEntryMBean) entry);
      this.dsApplIfOpsTableEntries.remove(connectionHandlerName);
      this.server.unregisterMBean(entry.getObjectName());
      return true;
    } catch (Exception ex) {
      return false;
    }
  }


/**
 * isAConnectionHandler allows to check if the Mbean is a Connection Handler.
 * @param name of the MBean
 * @return true if the MBean is a Connection Handler else false
 */
  private boolean isAConnectionHandler(ObjectName name) {
    String canonicalName = name.getCanonicalName();
    if ((canonicalName.contains("Connection_Handler")) &&
            (!(canonicalName.endsWith("_Statistics")))) {
      return true;
    } else {
      return false;
    }
  }
}
