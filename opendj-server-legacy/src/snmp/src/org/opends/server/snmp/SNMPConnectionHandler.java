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
 * Portions Copyright 2014-2016 ForgeRock AS.
 * Portions Copyright 2024 3A Systems, LLC
 */
package org.opends.server.snmp;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.HostPort;
import org.forgerock.opendj.server.config.server.SNMPConnectionHandlerCfg;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.types.InitializationException;

import static org.opends.messages.ProtocolMessages.*;

/**
 * This class defines an SNMP connection handler, which can be used to answer
 * SNMP Requests on MIB 2605. The MIB 2605 exposes a set of information
 * on Directory Server instances, protocol handlers. The information
 * regarding peer Directory Servers are not supported yet.
 */
public final class SNMPConnectionHandler
        extends ConnectionHandler<SNMPConnectionHandlerCfg>
        implements ConfigurationChangeListener<SNMPConnectionHandlerCfg>,
        AlertGenerator {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    // Current configuration
    SNMPConnectionHandlerCfg currentConfig;
    /**
     * The list of active client connection.
     */
    private LinkedList<ClientConnection> connectionList;
    /**
     * The set of listeners for this connection handler.
     */
    private LinkedList<HostPort> listeners = new LinkedList<HostPort>();
    /**
     * SNMP Connection Handler delegation class.
     */
    private SNMPClassLoaderProvider provider;
    /**
     * Is the SNMP Connection Handler Operational.
     */
    private boolean isOperational = false;

    /**
     * Creates a new instance of this connection handler.  All initialization
     * should be performed in the {@code initializeConnectionHandler} method.
     */
    public SNMPConnectionHandler() {
        super("SNMPConnectionHandler");
        this.connectionList = new LinkedList<ClientConnection>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initializeConnectionHandler(ServerContext serverContext, SNMPConnectionHandlerCfg configuration)
            throws ConfigException, InitializationException {

        if (configuration == null) {
            logger.error(ERR_SNMP_CONNHANDLER_NO_CONFIGURATION);
            return;
        }

        // Keep the connection handler configuration
        this.currentConfig = configuration;

        String jarLocation = this.currentConfig.getOpendmkJarfile();
        if ((jarLocation==null) || (jarLocation.length()==0)){
          logger.error(ERR_SNMP_CONNHANDLER_NO_OPENDMK_JARFILES);
          return;
        }

        // Get the jarFile Location and test if exists to be able to
        // start the SNMP Connection Handler as requested
        File jarFile = new File(jarLocation);
        File fullpathFile;

        if (!jarFile.isAbsolute()) {
            fullpathFile = new File(DirectoryServer.getServerRoot(),
                    this.currentConfig.getOpendmkJarfile());
        } else {
            fullpathFile = new File(this.currentConfig.getOpendmkJarfile());
        }

        if (!fullpathFile.exists()) {
           logger.error(ERR_SNMP_CONNHANDLER_OPENDMK_JARFILES_DOES_NOT_EXIST,
                  fullpathFile.getAbsolutePath());
            return;
        }

        // Clear the listeners list
        this.listeners.clear();
        this.listeners.add(new HostPort("0.0.0.0",
                this.currentConfig.getListenPort()));

        if (!this.isOperational(fullpathFile)) {
            logger.error(ERR_SNMP_CONNHANDLER_OPENDMK_JARFILES_NOT_OPERATIONAL,
                  fullpathFile.getAbsolutePath());
            return;
        }

        // Create the SNMPClassLoaderProvider
        this.provider = new SNMPClassLoaderProvider();

        // Call the delegate class
        try {
          this.provider.initializeConnectionHandler(this.currentConfig);
        }
        catch (Exception ex) {
            logger.error(ERR_SNMP_CONNHANDLER_BAD_CONFIGURATION);
            return;
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override()
    public String getConnectionHandlerName() {
        return "SNMP Connection Handler";
    }

    /**
     * {@inheritDoc}
     */
    @Override()
    public String getProtocol() {
        return "SNMP";
    }

    /**
     * {@inheritDoc}
     */
    @Override()
    public Collection<HostPort> getListeners() {
        return this.listeners;
    }

    /**
     * {@inheritDoc}
     */
    @Override()
    public Collection<ClientConnection> getClientConnections() {
        // There are no client connections for this connection handler.
        return this.connectionList;
    }

    /**
     * {@inheritDoc}
     */
    @Override()
    public void run() {
    }

    /**
     * {@inheritDoc}
     */
    @Override()
    public void toString(StringBuilder buffer) {
        buffer.append("SNMPConnectionHandler");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConfigurationChangeAcceptable(
            SNMPConnectionHandlerCfg configuration,
            List<LocalizableMessage> unacceptableReasons) {
        // The configuration should always be acceptable.
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConfigChangeResult applyConfigurationChange(
            SNMPConnectionHandlerCfg configuration) {
        if ((this.isOperational) && (this.provider!=null)){
            return this.provider.applyConfigurationChange(configuration);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DN getComponentEntryDN() {
        return this.currentConfig.dn();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getClassName() {
        return SNMPConnectionHandler.class.getName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LinkedHashMap<String, String> getAlerts() {
        LinkedHashMap<String, String> alerts =
          new LinkedHashMap<String, String>();
        return alerts;
    }

    @SuppressWarnings("unchecked")
    private void addFile(File file) {
        try {
            String url = "jar:" + file.toURI().toURL() + "!/";
            URL u = new URL(url);
            ClassLoader sysloader =ClassLoader.getSystemClassLoader();
            try {
                Method method = sysloader.getClass().getDeclaredMethod("addURL", URL.class);
                method.setAccessible(true);
                method.invoke(sysloader, u);
            }catch (NoSuchMethodException e) {
                Method method = sysloader.getClass().getDeclaredMethod("appendToClassPathForInstrumentation", String.class);
                method.setAccessible(true);
                method.invoke(sysloader, file.toString());
            }
        }
        catch (Throwable t) {
        }
    }//end method

    private void initSnmpClasses() {
        try {
            URLClassLoader opendsLoader =
              (URLClassLoader)DirectoryServer.getClassLoader();
            Class.forName("com.sun.management.comm.SnmpV3AdaptorServer",
                true, opendsLoader);
            Class.forName("com.sun.management.snmp.InetAddressAcl",
                true, opendsLoader);
            Class.forName("com.sun.management.snmp.SnmpEngineParameters",
                true, opendsLoader);
            Class.forName("com.sun.management.snmp.UserAcl",true, opendsLoader);
            this.isOperational = true;
        } catch (ClassNotFoundException ex) {
            this.isOperational = false;
        }
    }

    /**
     * Indicate if operational.
     * @param file The file
     * @return true is operational
     */
    public boolean isOperational(File file) {
        this.addFile(file);
        this.initSnmpClasses();
        return this.isOperational;
    }

    /**
     * Indicate if operational.
     * @return true is operational
     */
    public boolean isOperational() {
        return this.isOperational;
    }

    /**
     * {@inheritDoc}
    */
    @Override
    public void finalizeConnectionHandler(LocalizableMessage finalizeReason) {
        if (this.provider!=null) {
            this.provider.finalizeConnectionHandler();
        }
    }
}


