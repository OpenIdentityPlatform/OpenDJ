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
package org.opends.server.servicetag;


import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * SystemEnvironment provides utilities to get System information :
 * hostname, os.name, os.version, os.arch.
 */
public class SystemEnvironment {

    private String hostname;
    private String osName;
    private String osVersion;
    private String osArchitecture;

    private static SystemEnvironment sysEnv = null;

    // Private contructor
    private SystemEnvironment() {
        try {
            this.hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            this.hostname = "Unknown host";
        }
        this.osName = System.getProperty("os.name");
        this.osVersion = System.getProperty("os.version");
        this.osArchitecture = System.getProperty("os.arch");
    }

    /**
     * Return the System Environment singleton object.
     * @return the DsSystemEnvironment object.
     */
    public static synchronized SystemEnvironment getSystemEnvironment() {
        if (sysEnv == null) {
            sysEnv = new SystemEnvironment();
        }
        return sysEnv;
    }

    /**
     * Returns the hostname.
     * @return The hostname.
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * Returns the osName.
     * @return The osName.
     */
    public String getOsName() {
        return osName;
    }

    /**
     * Returns the osVersion.
     * @return The osVersion.
     */
    public String getOsVersion() {
        return osVersion;
    }

    /**
     * Returns the osArchitecture.
     * @return The osArchitecture.
     */
    public String getOsArchitecture() {
        return osArchitecture;
    }
}
