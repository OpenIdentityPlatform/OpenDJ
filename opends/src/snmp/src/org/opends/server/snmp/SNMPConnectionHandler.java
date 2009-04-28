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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
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
import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.HostPort;

import org.opends.server.admin.std.server.SNMPConnectionHandlerCfg;

import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.InitializationException;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;

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
    public void initializeConnectionHandler(
            SNMPConnectionHandlerCfg configuration)
            throws ConfigException, InitializationException {

        if (configuration == null) {
            Message message = ERR_SNMP_CONNHANDLER_NO_CONFIGURATION.get();
            logError(message);
            return;
        }

        // Keep the connection handler configuration
        this.currentConfig = configuration;

        String jarLocation = this.currentConfig.getOpendmkJarfile();
        if ((jarLocation==null) || (jarLocation.length()==0)){
            Message message = ERR_SNMP_CONNHANDLER_NO_OPENDMK_JARFILES.get();
            logError(message);
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
            Message message =
              ERR_SNMP_CONNHANDLER_OPENDMK_JARFILES_DOES_NOT_EXIST.get(
                  fullpathFile.getAbsolutePath());
            logError(message);
            return;
        }

        // Clear the listeners list
        this.listeners.clear();
        this.listeners.add(new HostPort("0.0.0.0",
                this.currentConfig.getListenPort()));

        if (!this.isOperational(fullpathFile)) {
            Message message =
              ERR_SNMP_CONNHANDLER_OPENDMK_JARFILES_NOT_OPERATIONAL.get(
                  fullpathFile.getAbsolutePath());
            logError(message);
            return;
        }

        // Create the SNMPClassLoaderProvider
        this.provider = new SNMPClassLoaderProvider();

        // Call the delegate class
        try {
          this.provider.initializeConnectionHandler(this.currentConfig);
        }
        catch (Exception ex) {
            Message message = ERR_SNMP_CONNHANDLER_BAD_CONFIGURATION.get();
            logError(message);
            return;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override()
    public void finalizeConnectionHandler(Message finalizeReason,
            boolean closeConnections) {
        if (this.provider!=null) {
            this.provider.finalizeConnectionHandler();
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
    public boolean isConfigurationChangeAcceptable(
            SNMPConnectionHandlerCfg configuration,
            List<Message> unacceptableReasons) {
        // The configuration should always be acceptable.
        return true;
    }

    /**
     * {@inheritDoc}
     */
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
    public DN getComponentEntryDN() {
        return this.currentConfig.dn();
    }

    /**
     * {@inheritDoc}
     */
    public String getClassName() {
        return SNMPConnectionHandler.class.getName();
    }

    /**
     * {@inheritDoc}
     */
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
            Class[] parameters = new Class[]{URL.class};
            URLClassLoader sysloader =
              (URLClassLoader)ClassLoader.getSystemClassLoader();
            Class sysclass = URLClassLoader.class;
            Method method =
              sysclass.getDeclaredMethod("addURL",new Class[]{URL.class});
            method.setAccessible(true);
            method.invoke(sysloader,new Object[]{ u });
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
}


