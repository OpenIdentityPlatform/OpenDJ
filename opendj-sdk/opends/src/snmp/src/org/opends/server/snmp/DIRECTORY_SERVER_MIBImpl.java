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
import javax.management.ObjectName;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.debug.DebugTracer;

/**
 * The class is the "DIRECTORY-SERVER-MIB" implementation.
 */
public class DIRECTORY_SERVER_MIBImpl extends DIRECTORY_SERVER_MIB {

    /**
     * The serial version identifier required to satisfy the compiler because
     * this class implements the <CODE>java.io.Serializable</CODE> interface.
     * This value was generated using the <CODE>serialver</CODE> command-line
     * utility included with the Java SDK.
     */
    private static final long serialVersionUID = 1420660265781848102L;
    /**
     * The debug log tracer for this class.
     */
    private static final DebugTracer TRACER = DebugLogger.getTracer();
    /**
     * Indicates if the SNMP Mbeans have to be registered or not.
     */
    private boolean registeredSnmpMBean = false;
    /**
     * The Current Directory Server MIB.
     */
    private DsMIBImpl dsMibGroup = null;
    /**
     * ObjectName of the Current Direcotry Server MIB.
     */
    private ObjectName mibObName;
    /**
     * ObjectName of the dsMIB group in the Directory Server MIB.
     */
    private ObjectName groupObjName;

    /**
     * Creates the Current Directory Server MIB.
     * @param registeredMBean indicates if the SNMP MBean has to register
     * in the Direcotry Server MBeanServer
     * @param mibName of the Directory Server MIB
     */
    public DIRECTORY_SERVER_MIBImpl(boolean registeredMBean,
            ObjectName mibName) {
        super();
        this.registeredSnmpMBean = registeredMBean;
        this.mibObName = mibName;
        if (DebugLogger.debugEnabled()) {
            TRACER.debugVerbose("DIRECTORY_SERVER_MIB=" + this.mibObName +
                    " created with registerMBean=" + this.registeredSnmpMBean);
        }
    }

    /**
     * {@inheritDoc}
     * @throws java.lang.Exception if the DsMib Group couls not be initialized
     */
    @Override
    protected void initDsMIB(MBeanServer server)
            throws Exception {

        final String oid = getGroupOid("DsMIB", "1.3.6.1.2.1.66");
        if (server != null) {
            groupObjName = new ObjectName(
                    SNMPConnectionHandlerDefinitions.SNMP_DOMAIN +
                    "type=group,name=DsMib");
        }
        final DsMIBMeta meta = createDsMIBMetaNode("DsMIB", oid, groupObjName,
                server);
        if (meta != null) {
            meta.registerTableNodes(this, server);

            // Note that when using standard metadata,
            // the returned object must implement the "DsMIBMBean"
            // interface.
            //
            final DsMIBMBean group = (DsMIBMBean) createDsMIBMBean("DsMIB", oid,
                    groupObjName, server);
            meta.setInstance(group);
            registerGroupNode("DsMIB", oid, groupObjName, meta, group, server);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object createDsMIBMBean(String groupName,
            String groupOid, ObjectName groupObjname, MBeanServer server) {
        this.dsMibGroup = new DsMIBImpl(this, server, this.registeredSnmpMBean);
        return this.dsMibGroup;
    }

    /**
     * Returns the created dsMIB group.
     * @return the DsMIBImpl
     */
    protected DsMIBImpl getMib() {
        return this.dsMibGroup;
    }

    /**
     * Returns the ObjectName of the dsMIB group.
     * @return the ObjectName of the created dsMIB group
     */
    protected ObjectName getObjectName() {
        return this.groupObjName;
    }
}
