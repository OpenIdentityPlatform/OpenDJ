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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions copyright 2012-2015 ForgeRock AS.
 */
package com.forgerock.opendj.cli;

/**
 * This class defines a number of constants used by tools.
 */
public final class CliConstants {

    /** The minimum java specification supported string version. */
    public static final float MINIMUM_JAVA_VERSION = 1.7F;

    /** Default value for LDAP connection timeout. */
    public static final int DEFAULT_LDAP_CONNECT_TIMEOUT = 30000;

    /** Default value for incrementing port number. */
    public static final int PORT_INCREMENT = 1000;

    /** Default port number for the LDAP port. */
    public static final int DEFAULT_LDAP_PORT = 389;

    /** Default port number for the LDAPS port. */
    public static final int DEFAULT_LDAPS_PORT = 1636;

    /** Default port number for the administrator port. */
    public static final int DEFAULT_ADMIN_PORT = 1444;

    /** Default port number for the SSL Connection. */
    public static final int DEFAULT_SSL_PORT = 636;

    /** Default port number for the JMX Connection handler. */
    public static final int DEFAULT_JMX_PORT = 1689;

    /** Default port number for the HTTP Connection handler. */
    public static final int DEFAULT_HTTP_PORT = 8080;

    /** Default port number for the SNMP Connection handler. */
    public static final int DEFAULT_SNMP_PORT = 161;

    /** Default name of root user DN. */
    public static final String DEFAULT_ROOT_USER_DN = "cn=Directory Manager";

    /** Default Administration Connector port. */
    public static final int DEFAULT_ADMINISTRATION_CONNECTOR_PORT = 4444;

    /** Default Administration UID. */
    public static final String GLOBAL_ADMIN_UID = "admin";


    /** Prevent instantiation. */
    private CliConstants() {

    }

}
