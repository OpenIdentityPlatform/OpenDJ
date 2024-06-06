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
 * Copyright 2024 3A Systems LLC.
 */

package org.openidentityplatform.opendj.embedded;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Config {

    private final String CONFIG_PREFIX = Config.class.getPackage().getName();
    private final int port = Integer.parseInt(System.getProperty(CONFIG_PREFIX + ".port", "1389"));

    private final int adminPort = Integer.parseInt(System.getProperty(CONFIG_PREFIX + ".admin_port", "4444"));

    private final String adminPassword = System.getProperty(CONFIG_PREFIX + ".password", "passw0rd");

    private final String baseDN = System.getProperty(CONFIG_PREFIX + ".root", "dc=openidentityplatform,dc=org");

    private final String backendType = System.getProperty(CONFIG_PREFIX + ".backend", "je");

    private final int jmxPort = Integer.parseInt(System.getProperty(CONFIG_PREFIX + ".jmx_port", "1689"));

    private final String ldifSchema = System.getProperty(CONFIG_PREFIX + ".ldif.schema");

    private final String file = System.getProperty(CONFIG_PREFIX + ".ldif.data", "/test.ldif");

    private final Set<String> skipSet = new HashSet<>(Arrays.asList(System.getProperty(CONFIG_PREFIX + ".skip", ",ou=sample-skip-group,").toLowerCase().split(";")));

    public int getPort() {
        return port;
    }

    public int getAdminPort() {
        return adminPort;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public String getBaseDN() {
        return baseDN;
    }

    public String getBackendType() {
        return backendType;
    }

    public int getJmxPort() {
        return jmxPort;
    }

    public String getLdifSchema() {
        return ldifSchema;
    }

    public String getFile() {
        return file;
    }

    public Set<String> getSkipSet() {
        return skipSet;
    }

    @Override
    public String toString() {
        return "Config{" +
                "port=" + port +
                ", adminPort=" + adminPort +
                ", adminPassword='" + adminPassword + '\'' +
                ", baseDN='" + baseDN + '\'' +
                ", backendType='" + backendType + '\'' +
                ", jmxPort=" + jmxPort +
                ", ldifSchema='" + ldifSchema + '\'' +
                ", file='" + file + '\'' +
                ", skipSet=" + skipSet +
                '}';
    }
}
