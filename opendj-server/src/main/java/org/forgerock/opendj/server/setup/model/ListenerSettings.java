/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2014 ForgeRock AS.
 */
package org.forgerock.opendj.server.setup.model;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

class ListenerSettings {

    /** Default value for incrementing port number. */
    static final int PORT_INCREMENT = 1000;

    /** Default port number for the LDAP port. */
    static final int DEFAULT_LDAP_PORT = 389;

    /** Default port number for the LDAPS port. */
    static final int DEFAULT_LDAPS_PORT = 1636;

    /** Default port number for the administrator port. */
    static final int DEFAULT_ADMIN_PORT = 1444;

    /** Default port number for the SSL Connection. */
    static final int DEFAULT_SSL_PORT = 636;

    /** Default port number for the JMX Connection handler. */
    static final int DEFAULT_JMX_PORT = 1689;

    /** Default port number for the HTTP Connection handler. */
    static final int DEFAULT_HTTP_PORT = 8080;

    /** Default port number for the SNMP Connection handler. */
    static final int DEFAULT_SNMP_PORT = 161;

    /** Default name of root user DN. */
    static final String DEFAULT_ROOT_USER_DN = "cn=Directory Manager";

    private String hostName;
    private int ldapPort;
    private int ldapsPort;
    private int adminPort;
    private boolean isJMXConnectionHandlerEnbled;
    private int jmxPort;
    private boolean isHTTPConnectionHandlerEnabled;
    private int httpPort;
    private boolean isSNMPConnectionHandlerEnabled;
    private int snmpPort;
    private String rootUserDN;
    private char[] password;
    private File passwordFile;
    private boolean isSSLEnabled;
    private boolean isTLSEnabled;
    private int sslPortNumber;
    private Certificate certificate;

    ListenerSettings() {
        hostName = "";
        ldapPort = DEFAULT_LDAP_PORT;
        ldapsPort = DEFAULT_LDAPS_PORT;
        adminPort = DEFAULT_ADMIN_PORT;
        jmxPort = DEFAULT_JMX_PORT;
        isJMXConnectionHandlerEnbled = false;
        httpPort = DEFAULT_HTTP_PORT;
        isHTTPConnectionHandlerEnabled = true;
        snmpPort = DEFAULT_SNMP_PORT;
        isSNMPConnectionHandlerEnabled = false;
        rootUserDN = DEFAULT_ROOT_USER_DN;
        isSSLEnabled = false;
        isTLSEnabled = false;
        sslPortNumber = DEFAULT_SSL_PORT;
        certificate = null;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public int getLdapPort() {
        return ldapPort;
    }

    public void setLdapPort(int ldapPort) {
        this.ldapPort = ldapPort;
    }

    public int getLdapsPort() {
        return ldapsPort;
    }

    public void setLdapsPort(int ldapsPort) {
        this.ldapsPort = ldapsPort;
    }

    public int getAdminPort() {
        return adminPort;
    }

    public void setAdminPort(int adminPort) {
        this.adminPort = adminPort;
    }

    public int getJMXPort() {
        return jmxPort;
    }

    public void setJMXPort(int jmxPort) {
        this.jmxPort = jmxPort;
    }

    public boolean isJMXConnectionHandlerEnbled() {
        return isJMXConnectionHandlerEnbled;
    }

    public void setJMXConnectionHandlerEnbled(boolean isJMXConnectionHandlerEnbled) {
        this.isJMXConnectionHandlerEnbled = isJMXConnectionHandlerEnbled;
    }

    public int getHTTPPort() {
        return httpPort;
    }

    public void setHTTPPort(int httpPort) {
        this.httpPort = httpPort;
    }

    public boolean isHTTPConnectionHandlerEnabled() {
        return isHTTPConnectionHandlerEnabled;
    }

    public void setHTTPConnectionHandlerEnabled(boolean isHTTPConnectionHandlerEnabled) {
        this.isHTTPConnectionHandlerEnabled = isHTTPConnectionHandlerEnabled;
    }

    public int getSNMPPort() {
        return snmpPort;
    }

    public void setSNMPPort(int snmpPort) {
        this.snmpPort = snmpPort;
    }

    public boolean isSNMPConnectionHandlerEnabled() {
        return isSNMPConnectionHandlerEnabled;
    }

    public void setSNMPConnectionHandlerEnabled(boolean isSNMPConnectionHandlerEnabled) {
        this.isSNMPConnectionHandlerEnabled = isSNMPConnectionHandlerEnabled;
    }

    public String getRootUserDN() {
        return rootUserDN;
    }

    public void setRootUserDN(String rootUserDN) {
        this.rootUserDN = rootUserDN;
    }

    public String getPassword() {
        if (password == null) {
            return null;
        }
        return String.valueOf(password);
    }

    public void setPassword(String password) {
        this.password = password.toCharArray();
    }

    public File getPasswordFile() {
        return passwordFile;
    }

    public void setPasswordFile(File pwdFile) {
        this.passwordFile = pwdFile;
    }

    public boolean isSSLEnabled() {
        return isSSLEnabled;
    }

    public void setSSLEnabled(boolean isSSLEnabled) {
        this.isSSLEnabled = isSSLEnabled;
    }

    public boolean isTLSEnabled() {
        return isTLSEnabled;
    }

    public void setTLSEnabled(boolean isTLSEnabled) {
        this.isTLSEnabled = isTLSEnabled;
    }

    public int getSSLPortNumber() {
        return sslPortNumber;
    }

    public void setSSLPortNumber(int sslPortNumber) {
        this.sslPortNumber = sslPortNumber;
    }

    public Certificate getCertificate() {
        return certificate;
    }

    public void setCertificate(Certificate certificate) {
        this.certificate = certificate;
    }

    static int getFreeSocketPort(int startPortNumber) {
        return getFreeSocketPort(startPortNumber, new TestPortImpl());
    }

    private static int getFreeSocketPort(int startPortNumber, TestPort testPort) {
        int port = startPortNumber;
        while (port >= 0 && port <= 65535) {
            try {
                testPort.canBindToPort(port);
                return port;
            } catch (IOException e) {
                port = port + PORT_INCREMENT;
            }
        }
        throw new IllegalArgumentException("Invalid port.");
    }

    interface TestPort {
        void canBindToPort(int portNumber) throws IOException;
    }

    static class TestPortImpl implements TestPort {
        public void canBindToPort(int portNumber) throws IOException {
            ServerSocket socket = null;
            try {
                socket = new ServerSocket();
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(portNumber));
            } finally {
                close(socket);
            }
        }

        private void close(ServerSocket socket) {
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (final IOException ignored) {
                // Ignore.
            }
        }
    }
}
