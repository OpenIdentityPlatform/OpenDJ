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
package org.opends.server.servicetag;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.servicetag.Util.*;
import static org.opends.server.servicetag.ServiceTagDefinition.*;
import static org.opends.server.loggers.debug.DebugLogger.*;

/**
 * Allows to create / register/ remove / find ServiceTags calling the
 * appropriated CLI stclient if available on the system.
 */
public class Registry {

    // The tracer object for the debug logger.
    private static final DebugTracer TRACER = getTracer();
    // ServiceTag services are not relocatable
    private static final String STCLIENT_SOLARIS
            = "/usr/bin/stclient";
    private static final String STCLIENT_LINUX
            = "/opt/sun/servicetag/bin/stclient";
    private static final int ST_ERR_NOT_AUTH = 245;
    private static final int ST_ERR_REC_NOT_FOUND = 225;
    private static final String INSTANCE_URN_DESC = "Product instance URN=";
    private static File stclient = null;
    private static String stclientPath = null;
    private static Registry registry = null;

    // Private contructor
    private Registry() {
    }

    /**
     * Returns the common service tag registry.
     * @return the object for the common system service tag registry.
     * @throws UnsupportedOperationException if the common registry is
     * not supported.
     */
    public static Registry getSystemRegistry() throws
            UnsupportedOperationException {
        if ((registry == null) && (isSupported())) {
            registry = new Registry();
        } else {
            throw new UnsupportedOperationException(
                    "Registry class is not supported");
        }
        return registry;
    }

    /**
     * Returns true if the DsRegistry class is supported on this system.
     * @return true if the DsRegistry class is supported,
     * otherwise false.
     */
    public static boolean isSupported() {
        return (getSTclientPath() != null);
    }

    /**
     * Tests if the corresponding ServiceTag exists in common registry.
     * @param productUrn of the product to look for.
     * @param installedLocation identifying the installed product.
     * @return true if at least corresponding ServiceTag exists, otherwise
     * return false.
     * @throws IOException if an error occurred in the stclient call.
     */
    public boolean existServiceTag(String productUrn,
            String installedLocation) throws IOException {

        boolean found = false;
        Set<ServiceTag> tags;

        tags = this.findServiceTags(productUrn);
        for (ServiceTag svcTag : tags) {
          if (svcTag.getProductDefinedInstanceID().equals
                (installedLocation)) {
            found = true;
            break;
          }
        }
        return found;
    }

    /**
     * Tests if the corresponding ServiceTag exists in common registry.
     * @param instanceUrn of the product to look for.
     * @return true if at least corresponding ServiceTag exists, otherwise
     * return false.
     */
    public boolean existServiceTag(String instanceUrn) {
        try {
            ServiceTag svcTag = getServiceTag(instanceUrn);
            if (svcTag == null) {
                return false;
            } else {
                return true;
            }
        } catch (IOException ex) {
            if (debugEnabled()) {
                TRACER.debugCaught(DebugLogLevel.WARNING, ex);
            }
            return false;
        }
    }

    /**
     * Adds a new ServiceTag in the common registry.
     * @param st ServiceTag to add.
     * @return the added serviceTag with updated field.
     * @throws java.io.IOException if the serviceTag can not be added.
     */
    public ServiceTag addServiceTag(ServiceTag st) throws IOException {

        // Parameter checking
        if (st == null) {
            throw new NullPointerException("st parameter cannot be null");
        }

        // If the ServiceTag already exists
        if (existServiceTag(st.getInstanceURN())) {
            throw new IOException("Instance_urn = " +
                    st.getInstanceURN() + " already exists");
        }

        List<String> command = new ArrayList<String>();
        command.add(getSTclientPath());
        command.add("-a");
        command.add("-i");
        command.add(st.getInstanceURN());
        command.add("-p");
        command.add(st.getProductName());
        command.add("-e");
        command.add(st.getProductVersion());
        command.add("-t");
        command.add(st.getProductURN());

        if (st.getProductParentURN().length() > 0) {
            command.add("-F");
            command.add(st.getProductParentURN());
        }

        command.add("-P");
        command.add(st.getProductParent());

        if (st.getProductDefinedInstanceID().length() > 0) {
            command.add("-I");
            command.add(st.getProductDefinedInstanceID());
        }
        command.add("-m");
        command.add(st.getProductVendor());
        command.add("-A");
        command.add(st.getPlatformArch());
        command.add("-z");
        command.add(st.getContainer());
        command.add("-S");
        command.add(st.getSource());

        BufferedReader in = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            Process p = pb.start();
            String output = commandOutput(p);
            String urn = "";
            if (p.exitValue() == 0) {
                // Obtain the instance urn from the stclient output
                in = new BufferedReader(new StringReader(output));
                String line = null;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith(INSTANCE_URN_DESC)) {
                        urn = line.substring(INSTANCE_URN_DESC.length());
                        break;
                    }
                }
                if (urn.length() == 0) {
                    throw new IOException("Error in creating service tag:\n" +
                            output);
                }
                return getServiceTag(urn);
            } else {
                return checkReturnError(p.exitValue(), output, st);
            }
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    /**
     * Removes the corresponding ServiceTag from the common registry.
     * @param instanceURN of the ServiceTag to remove.
     * @return the remove DsServiceTag, if null, the ServiceTag does not exist.
     * @throws java.io.IOException if an error occured.
     */
    public ServiceTag removeServiceTag(String instanceURN) throws IOException {

        ServiceTag st = getServiceTag(instanceURN);
        if (st == null) {
            return null;
        }

        List<String> command = new ArrayList<String>();
        command.add(getSTclientPath());
        command.add("-d");
        command.add("-i");
        command.add(instanceURN);

        ProcessBuilder pb = new ProcessBuilder(command);
        Process p = pb.start();
        String output = commandOutput(p);
        if (p.exitValue() == 0) {
            return st;
        } else {
            return checkReturnError(p.exitValue(), output, st);
        }
    }

    /**
     * Returns the corresponding DsServiceTag object.
     * @param instanceURN of the ServiceTag to look for.
     * @return the corresponding DsServiceTag object.
     * @throws java.io.IOException if the tag could not be retrieved.
     */
    public ServiceTag getServiceTag(String instanceURN) throws IOException {

        List<String> command = new ArrayList<String>();
        command.add(getSTclientPath());
        command.add("-g");
        command.add("-i");
        command.add(instanceURN);

        ProcessBuilder pb = new ProcessBuilder(command);
        Process p = pb.start();
        String output = commandOutput(p);
        if (p.exitValue() == 0) {
            return parseServiceTag(output);
        } else {
            return checkReturnError(p.exitValue(), output, null);
        }
    }

    /**
     * Returns a Set of ServiceTag where the product urn is productURN.
     * @param productURN of the ServiceTags.
     * @return a Set of ServiceTag where the product_urn is productURN.
     * @throws java.io.IOException if error.
     */
    public Set<ServiceTag> findServiceTags(String productURN)
            throws IOException {

        List<String> command = new ArrayList<String>();
        command.add(getSTclientPath());
        command.add("-f");
        command.add("-t");
        command.add(productURN);

        BufferedReader in = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            Process p = pb.start();
            String output = commandOutput(p);

            Set<ServiceTag> instances = new HashSet<ServiceTag>();
            if (p.exitValue() == 0) {
                // parse the service tag output from stclient
                in = new BufferedReader(new StringReader(output));
                String line = null;
                while ((line = in.readLine()) != null) {
                    String s = line.trim();
                    if (s.startsWith("urn:st:")) {
                        instances.add(getServiceTag(s));
                    }
                }
            } else {
                checkReturnError(p.exitValue(), output, null);
            }
            return instances;
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    /**
     * Returns the corrsponding error based on the process exit value <>0.
     * @param exitValue return by the process.
     * @param output return by the CLI execution.
     * @param st returned by the CLI.
     * @return st returned by the CLI.
     * @throws java.io.IOException.
     */
    private static ServiceTag checkReturnError(int exitValue,
            String output,
            ServiceTag st) throws IOException {
        switch (exitValue) {
            case ST_ERR_REC_NOT_FOUND:
                return null;
            case ST_ERR_NOT_AUTH:
                if (st != null) {
                    throw new IOException(
                            "Not authorized to access " + st.getInstanceURN() +
                            " installer_uid=" + st.getInstallerUID());
                } else {
                    throw new IOException(
                            "Not authorized:" + output);
                }
            default:
                throw new IOException("stclient exits with error" +
                        " (" + exitValue + ")\n" + output);
        }
    }

    /**
     * Parses the stclient output and return the corresponding DsServiceTag
     * object.
     * @param output to parse.
     * @return the DsServiceTag.
     * @throws java.io.IOException if an error occured during the parsing.
     */
    private ServiceTag parseServiceTag(String output) throws IOException {
        BufferedReader in = null;
        try {
            Properties props = new Properties();
            // parse the service tag output from stclient
            in = new BufferedReader(new StringReader(output));
            String line = null;
            while ((line = in.readLine()) != null) {
                if ((line = line.trim()).length() > 0) {
                    String[] ss = line.trim().split("=", 2);
                    if (ss.length == 2) {
                        props.setProperty(ss[0].trim(), ss[1].trim());
                    } else {
                        props.setProperty(ss[0].trim(), "");
                    }
                }
            }

            String urn = props.getProperty(ST_NODE_INSTANCE_URN);
            String productName = props.getProperty(ST_NODE_PRODUCT_NAME);
            String productVersion = props.getProperty(ST_NODE_PRODUCT_VERSION);
            String productURN = props.getProperty(ST_NODE_PRODUCT_URN);
            String productParent = props.getProperty(ST_NODE_PRODUCT_PARENT);
            String productParentURN = props.getProperty(
                    ST_NODE_PRODUCT_PARENT_URN);
            String productDefinedInstanceID =
                    props.getProperty(ST_NODE_PRODUCT_DEFINED_INST_ID);
            String productVendor = props.getProperty(ST_NODE_PRODUCT_VENDOR);
            String platformArch = props.getProperty(ST_NODE_PLATFORM_ARCH);
            String container = props.getProperty(ST_NODE_CONTAINER);
            String source = props.getProperty(ST_NODE_SOURCE);
            int installerUID =
                    Util.getIntValue(props.getProperty(ST_NODE_INSTALLER_UID));
            Date timestamp =
                    Util.parseTimestamp(props.getProperty(ST_NODE_TIMESTAMP));

            return new ServiceTag(urn,
                    productName,
                    productVersion,
                    productURN,
                    productParent,
                    productParentURN,
                    productDefinedInstanceID,
                    productVendor,
                    platformArch,
                    container,
                    source,
                    installerUID,
                    timestamp);
        } finally {
            if (in != null) {
                in.close();
            }
        }

    }

    private static String getSTclientPath() {
        if (stclientPath == null) {
            // Initialization to determine the platform's stclient pathname
            String os = System.getProperty("os.name");
            if (os.equals("SunOS")) {
                stclient = new File(STCLIENT_SOLARIS);
            } else if (os.equals("Linux")) {
                stclient = new File(STCLIENT_LINUX);
            } else if (os.startsWith("Windows")) {
                stclient = getWindowsStClientFile();
            }
        }
        if ((stclientPath == null) && (stclient != null) && (stclient.exists()))
        {
            stclientPath = stclient.getAbsolutePath();
        }
        return stclientPath;
    }
}
