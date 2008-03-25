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

import java.io.File;

import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;

import static org.opends.server.loggers.debug.DebugLogger.*;

import com.sun.management.comm.SnmpV3AdaptorServer;
import com.sun.management.snmp.InetAddressAcl;
import com.sun.management.snmp.SnmpEngineParameters;
import com.sun.management.snmp.UserAcl;

import java.net.InetAddress;
import java.util.Iterator;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.opends.server.admin.std.server.SNMPConnectionHandlerCfg;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.ResultCode;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.Validator;


/**
 * The SNMPClassLoaderProvider.
 */
public class SNMPClassLoaderProvider {

    /**
     * The debug log tracer for this class.
     */
    private static final DebugTracer TRACER = DebugLogger.getTracer();

    /**
     * The current configuration state.
     */
    private SNMPConnectionHandlerCfg currentConfig;


    /**
     * MBeanServer of OpenDS.
     */
    private MBeanServer server;

    /**
     * MIB to manage.
     */
    private DIRECTORY_SERVER_MIBImpl dsMib;

    /**
     * ObjectName of the MIB2605.
     */
    private ObjectName mibObjName;

    /**
     * ObjectName of the SnmpAdaptor.
     */
    private ObjectName snmpObjName;

    /**
     * SNMP Port Number for SNMP requests.
     */
    private int snmpPort = 161;

    /**
     * Default SNMP trap port Number for SNMP Traps.
     */
    private int snmpTrapPort = 162;

    /**
     * Default SNMP Version.
     */
    private String snmpVersion =
      SNMPConnectionHandlerDefinitions.SNMP_VERSION_V3;

    /**
     * Registration of the SNMP MBeans.
     */
    private boolean registeredSNMPMBeans = false;

    /**
     * The unique name for this connection handler.
     */
    private String connectionHandlerName;

    /**
     * ObjectName of the UsmMIB.
     */
    private ObjectName UsmObjName;

    private SnmpV3AdaptorServer snmpAdaptor;

    /**
     * Default constructor.
     */
    public SNMPClassLoaderProvider() {
      // No implementation required
    }

    /**
     * Initialization.
     * @param configuration The configuration
     */
    public void initializeConnectionHandler(
            SNMPConnectionHandlerCfg configuration) {


        // Keep the connection handler configuration
        this.currentConfig = configuration;

        // Get the Directory Server JMX MBeanServer
        this.server = DirectoryServer.getJMXMBeanServer();

        // Initialize he Connection Handler with the givewn configuration
        this.initializeConnectionHandler();

    }

    /**
     * Applies the configuration changes to this change listener.
     *
     * @param configuration
     *          The new configuration containing the changes.
     * @return Returns information about the result of changing the
     *         configuration.
     */
    public ConfigChangeResult applyConfigurationChange(
            SNMPConnectionHandlerCfg configuration) {

        try {

            // Register/UnRegister SNMP MBeans
            if ((this.registeredSNMPMBeans) &&
                    (!configuration.isRegisteredMbean())) {
                this.unregisterSnmpMBeans();
                this.registeredSNMPMBeans = configuration.isRegisteredMbean();
            } else if ((!this.registeredSNMPMBeans) &&
                    (configuration.isRegisteredMbean())) {
                this.unregisterSnmpMBeans();
                this.registeredSNMPMBeans = configuration.isRegisteredMbean();
            }

            // PortNumber/Version
            if ((this.snmpPort != configuration.getListenPort())) {
                this.server.unregisterMBean(this.snmpObjName);
                this.snmpAdaptor.stop();
                this.snmpPort = configuration.getListenPort();
                this.snmpAdaptor = this.getSnmpAdaptor(configuration);


                // Creates and starts the SNMP Adaptor
                this.snmpObjName = new ObjectName(
                        SNMPConnectionHandlerDefinitions.SNMP_DOMAIN +
                        "class=SnmpAdaptorServer,protocol=snmp," +
                        "port=" + snmpPort);
                this.server.registerMBean(this.snmpAdaptor, this.snmpObjName);
                this.snmpAdaptor.start();

                // Send a coldStart SNMP Trap on the new trap port if required
                if (this.snmpTrapPort != configuration.getTrapPort()) {
                    this.snmpTrapPort = configuration.getTrapPort();
                    this.snmpAdaptor.setTrapPort(snmpTrapPort);
                    this.snmpAdaptor.snmpV1Trap(0, 0, null);
                }
            }
        } catch (Exception ex) {
            if (debugEnabled()) {
                TRACER.debugCaught(DebugLogLevel.ERROR, ex);
            }
        }

        // Check if the security file
        // If security file have changed, changeConfiguration not
        // Supported.

        return new ConfigChangeResult(ResultCode.SUCCESS, false);
    }

    /**
     * Gets the ObjectName of the crated USM MIB MBean.
     * @return The UsmMIB ObjectName
     */
    public ObjectName getUsmMIBName() {
        return this.UsmObjName;
    }

     // private methods
    private void initializeConnectionHandler() {


        // Compute the connectionHandler name
        this.connectionHandlerName = "SNMP Connection Handler " +
                this.currentConfig.getListenPort();

        // Gets the configuration parameters
        this.snmpPort = this.currentConfig.getListenPort();
        this.snmpTrapPort = this.currentConfig.getTrapPort();
        this.registeredSNMPMBeans = this.currentConfig.isRegisteredMbean();

        this.snmpVersion = this.currentConfig.getVersion().trim().toLowerCase();
        if (!SNMPConnectionHandlerDefinitions.SUPPORTED_SNMP_VERSION.contains(
                this.snmpVersion)) {
            this.snmpVersion = SNMPConnectionHandlerDefinitions.SNMP_VERSION_V3;
        }

        // Creates all the required objects for SNMP MIB 2605 Support
        try {

            // Creates and starts the SNMP Adaptor
            this.snmpObjName = new ObjectName(
                    SNMPConnectionHandlerDefinitions.SNMP_DOMAIN +
                    "class=SnmpAdaptorServer,protocol=snmp,port=" + snmpPort);

            // Create the SNMP Adaptor with the appropriated parameters
            this.snmpAdaptor = this.getSnmpAdaptor(this.currentConfig);

            // Create the Usm MIB to allow user management
            if ((this.registeredSNMPMBeans) && (this.snmpVersion.equals(
                    SNMPConnectionHandlerDefinitions.SNMP_VERSION_V3))) {

                this.UsmObjName = new ObjectName(
                        SNMPConnectionHandlerDefinitions.SNMP_DOMAIN +
                        "type=USM_MIB");

                try {
                     this.snmpAdaptor.registerUsmMib(server, this.UsmObjName);
                }
                catch (Exception ex) {
                }
            }

            this.snmpAdaptor.start();

            // Send a coldStart SNMP Trap.
            this.snmpAdaptor.setTrapPort(snmpTrapPort);
            this.snmpAdaptor.snmpV1Trap(0, 0, null);

            // Create an instance of the customized MIB
            this.mibObjName = new ObjectName(
                    SNMPConnectionHandlerDefinitions.SNMP_DOMAIN +
                    "class=DIRECTORY_SERVER_MIB");

            this.dsMib = new DIRECTORY_SERVER_MIBImpl(
                    this.registeredSNMPMBeans, this.mibObjName);
            this.dsMib.setSnmpAdaptor(snmpAdaptor);

            this.server.registerMBean(this.snmpAdaptor, snmpObjName);
            this.server.registerMBean(this.dsMib, this.mibObjName);

        } catch (Exception ex) {
            if (debugEnabled()) {
                TRACER.debugCaught(DebugLogLevel.ERROR, ex);
            }
        }
    }


    /**
     * Finalize.
     */
    public void finalizeConnectionHandler() {

        try {

            // Send a trap when stop
            this.snmpAdaptor.snmpV1Trap(0, 0, null);

            String[] names = this.snmpAdaptor.getMibs();

            // Stop the SNMP Adaptor
            this.snmpAdaptor.stop();

            this.server.unregisterMBean(this.snmpObjName);
            this.server.unregisterMBean(this.mibObjName );
            this.server.unregisterMBean(new ObjectName(
                        SNMPConnectionHandlerDefinitions.SNMP_DOMAIN +
                        "type=group,name=DsMib"));

            // Unregister the created SNMP MBeans
            if (this.registeredSNMPMBeans) {
                this.unregisterSnmpMBeans();

                if (this.snmpVersion.equals(
                        SNMPConnectionHandlerDefinitions.SNMP_VERSION_V3)) {
                    this.server.unregisterMBean(this.UsmObjName);
                }
            }
        } catch (Exception ex) {
            if (debugEnabled()) {
                TRACER.debugCaught(DebugLogLevel.ERROR, ex);
            }
        }
    }

    private void unregisterSnmpMBeans() {
        Set objectNames = this.dsMib.getMib().getEntriesObjectNames();
        for (Iterator iter = objectNames.iterator(); iter.hasNext();) {
            ObjectName name = (ObjectName) iter.next();
            try {
                this.server.unregisterMBean(name);
            } catch (Exception ex) {
            }
        }
    }


    private SnmpV3AdaptorServer getSnmpAdaptor(
        SNMPConnectionHandlerCfg configuration) {

        Validator.ensureNotNull(configuration);
        SnmpV3AdaptorServer adaptor = null;
        try {

            // Set the USM security file
            String usmConfigPath = configuration.getSecurityAgentFile();
            File file = StaticUtils.getFileForPath(usmConfigPath);

            if (configuration.getVersion().toLowerCase().equals(
                    SNMPConnectionHandlerDefinitions.SNMP_VERSION_V3)) {
                System.setProperty("jdmk.security.file",
                    file.getAbsolutePath());
            }

            // Create the Security Parameters for the engine
            SnmpEngineParameters engineParameters = new SnmpEngineParameters();

            // Set V3 Security parameters
            engineParameters.activateEncryption();

            // Create the UACL controller
            UserAcl uacls = (UserAcl)new SNMPUserAcl(configuration);
            engineParameters.setUserAcl(uacls);

            // V1/V2 Security parameters
            InetAddressAcl acls =
              (InetAddressAcl)new SNMPInetAddressAcl(configuration);

            adaptor = new SnmpV3AdaptorServer(engineParameters, null, acls,
                    configuration.getListenPort(), InetAddress.getLocalHost());

            // Enable the community to context translation for V1/V2 to V3
            adaptor.enableCommunityStringAtContext();

            return adaptor;
        } catch (Exception ex) {
            TRACER.debugError("Could not instanciate the SNMP Adaptor");
            return adaptor;
        }
    }
}

