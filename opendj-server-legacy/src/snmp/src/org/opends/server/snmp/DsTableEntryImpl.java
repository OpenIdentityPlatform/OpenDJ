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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014 ForgeRock AS.
 * Portions Copyright 2024 3A Systems, LLC.
 */
package org.opends.server.snmp;

import com.sun.management.snmp.agent.SnmpMib;

import java.util.Iterator;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.guitools.controlpanel.util.Utilities;

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

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /**
     * Directory Server MBeanServer.
     */
    private MBeanServer server;
    /**
     * Mapping Class.
     */
    private SNMPMonitor monitor;
    /**
     * ObjectName of the entry.
     */
    private ObjectName entryName;
    /**
     * Index of the Directory Server Instance (applIndex).
     */
    private Integer applIndex;

    /**
     * Creates a DsTableEntry.
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
     * Getter for the "DsServerType" variable.
     * @return a Byte[] representing the Ds Server Type
     */
    public Byte[] getDsServerType() {
        try {
            String value1 = (String) this.monitor.getAttribute(
                    SNMPConnectionHandlerDefinitions.MONITOR_VERSION_OBJECTNAME,
                    "fullVersion");
            String value2 = (String) this.monitor.getAttribute(
                    SNMPConnectionHandlerDefinitions.MONITOR_VERSION_OBJECTNAME,
                    "buildID");
            return SNMPMonitor.string2ByteArray(value1 + " - " + value2);
        } catch (Exception ex) {
            logger.traceException(ex);
            return null;
        }
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
            logger.traceException(ex);
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
            logger.traceException(ex);
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
                if (value != null && value instanceof String) {
                    result = result + new Long((String) value);
                }else if (value != null && value instanceof Long) {
                    result = result + (Long)value;
                }
            }
            return SNMPMonitor.gauge32Value(result);
        } catch (Exception ex) {
            logger.traceException(ex);
        }
        return 0L;
    }

    /**
     * {@inheritDoc}
     * @return DsServerDescription as String
     */
    @Override
    public String getDsServerDescription() {
        return Utilities.getServerRootDirectory().getAbsolutePath();
    }

    /**
     * Gets the object of the entry.
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
              logger.traceException(ex);
            }
        }
        return this.entryName;
    }
}
