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
import static com.forgerock.opendj.cli.CliConstants.*;

/**
 * This class provides listener settings for the OpenDJ3 setup.
 */
public class ListenerSettings {

    private String hostName;
    private int ldapPort;
    private int ldapsPort;
    private int adminPort;
    private boolean isJMXConnectionHandlerEnabled;
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

    /**
     * Default constructor.
     */
    public ListenerSettings() {
        hostName = "";
        ldapPort = DEFAULT_LDAP_PORT;
        ldapsPort = DEFAULT_LDAPS_PORT;
        adminPort = DEFAULT_ADMIN_PORT;
        jmxPort = DEFAULT_JMX_PORT;
        isJMXConnectionHandlerEnabled = false;
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

    /**
     * Returns the host name.
     *
     * @return The host name of the local machine.
     */
    public String getHostName() {
        return hostName;
    }

    /**
     * Sets the host name of the machine.
     *
     * @param hostName
     *            The host name of the current machine.
     */
    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    /**
     * Returns the value of the LDAP port.
     *
     * @return The value of the LDAP port.
     */
    public int getLdapPort() {
        return ldapPort;
    }

    /**
     * Sets the value of the LDAP port.
     *
     * @param ldapPort
     *            The LDAP port's value to set.
     */
    public void setLdapPort(int ldapPort) {
        this.ldapPort = ldapPort;
    }

    /**
     * Return the LDAPs port.
     *
     * @return The LDAPs port's value.
     */
    public int getLdapsPort() {
        return ldapsPort;
    }

    /**
     * Sets the LDAPs port value.
     *
     * @param ldapsPort
     *            The LDAPs port's value to set.
     */
    public void setLdapsPort(int ldapsPort) {
        this.ldapsPort = ldapsPort;
    }

    /**
     * Returns the administration connector port.
     *
     * @return The administration connector's port
     */
    public int getAdminPort() {
        return adminPort;
    }

    /**
     * Sets the administration connector 's port.
     *
     * @param adminPort
     *            The administration connector.
     */
    public void setAdminPort(int adminPort) {
        this.adminPort = adminPort;
    }

    /**
     * Returns the JMX's port value.
     *
     * @return The JMX's port value.
     */
    public int getJMXPort() {
        return jmxPort;
    }

    /**
     * Sets the JMX port's value.
     *
     * @param jmxPort
     *            The JMX port's value.
     */
    public void setJMXPort(int jmxPort) {
        this.jmxPort = jmxPort;
    }

    /**
     * Returns {@code true} if the JMX connection handler is enabled.
     *
     * @return {@code true} if the JMX connection handler is enabled.
     */
    public boolean isJMXConnectionHandlerEnabled() {
        return isJMXConnectionHandlerEnabled;
    }

    /**
     * Sets the status of the JMX connection handler.
     *
     * @param isJMXConnectionHandlerEnabled
     *            true} if the JMX connection handler is enabled.
     */
    public void setJMXConnectionHandlerEnabled(boolean isJMXConnectionHandlerEnabled) {
        this.isJMXConnectionHandlerEnabled = isJMXConnectionHandlerEnabled;
    }

    /**
     * Returns the value of the HTTP connection handler port.
     *
     * @return The value of the HTTP connection handler port.
     */
    public int getHTTPPort() {
        return httpPort;
    }

    /**
     * Sets the value of the port which is going to be used bu the HTTP connection handler.
     *
     * @param httpPort
     *            The value of the HTTP port.
     */
    public void setHTTPPort(int httpPort) {
        this.httpPort = httpPort;
    }

    /**
     * Returns {@code true} if the HTTP connection handler is enabled.
     *
     * @return {@code true} if the HTTP connection handler is enabled.
     */
    public boolean isHTTPConnectionHandlerEnabled() {
        return isHTTPConnectionHandlerEnabled;
    }

    /**
     * Sets the status of the HTTP connection handler.
     *
     * @param isHTTPConnectionHandlerEnabled
     *            true} if the HTTP connection handler is enabled.
     */
    public void setHTTPConnectionHandlerEnabled(boolean isHTTPConnectionHandlerEnabled) {
        this.isHTTPConnectionHandlerEnabled = isHTTPConnectionHandlerEnabled;
    }

    /**
     * Returns the value of the port used by SNMP.
     *
     * @return The value of the port used by SNMP.
     */
    public int getSNMPPort() {
        return snmpPort;
    }

    /**
     * Sets the value of the port used by SNMP.
     *
     * @param snmpPort
     *            The value of the port used by SNMP.
     */
    public void setSNMPPort(int snmpPort) {
        this.snmpPort = snmpPort;
    }

    /**
     * Returns {@code true} if the SNMP connection handler is enabled.
     *
     * @return {@code true} if the SNMP connection handler is enabled. {@code false} otherwise.
     */
    public boolean isSNMPConnectionHandlerEnabled() {
        return isSNMPConnectionHandlerEnabled;
    }

    /**
     * Sets the status of the HTTP connection handler.
     *
     * @param isSNMPConnectionHandlerEnabled
     *            {@code true} if the HTTP connection handler is enabled.
     */
    public void setSNMPConnectionHandlerEnabled(boolean isSNMPConnectionHandlerEnabled) {
        this.isSNMPConnectionHandlerEnabled = isSNMPConnectionHandlerEnabled;
    }

    /**
     * Returns the root user DN.
     *
     * @return The root user DN.
     */
    public String getRootUserDN() {
        return rootUserDN;
    }

    /**
     * Sets the root user DN.
     *
     * @param rootUserDN
     *            The root user DN.
     */
    public void setRootUserDN(String rootUserDN) {
        this.rootUserDN = rootUserDN;
    }

    /**
     * Returns the password linked to this root user DN.
     *
     * @return The password linked to this root user DN.
     */
    public String getPassword() {
        if (password != null) {
            return String.valueOf(password);
        }
        return null;
    }

    /**
     * Sets the user root's password.
     *
     * @param password
     *            The password to set to this user root DN.
     */
    public void setPassword(String password) {
        this.password = password.toCharArray();
    }

    /**
     * The file containing the password for the initial root user for the directory server.
     *
     * @return The file containing the password for the initial root user.
     */
    public File getPasswordFile() {
        return passwordFile;
    }

    /**
     * Sets the file containing the password for the initial root user for the directory server.
     *
     * @param pwdFile
     *            The file containing the password for the initial root user for the directory server.
     */
    public void setPasswordFile(File pwdFile) {
        this.passwordFile = pwdFile;
    }

    /**
     * Returns {@code true} is SSL is enabled.
     *
     * @return {@code true} is SSL is enabled, {@code false} otherwise.
     */
    public boolean isSSLEnabled() {
        return isSSLEnabled;
    }

    /**
     * Sets a flag is SSL is enabled.
     *
     * @param isSSLEnabled
     *            {@code true} is SSL is enabled, {@code false} otherwise.
     */
    public void setSSLEnabled(boolean isSSLEnabled) {
        this.isSSLEnabled = isSSLEnabled;
    }

    /**
     * Returns {@code true} is TLS is enabled.
     *
     * @return {@code true} is TLS is enabled, {@code false} otherwise.
     */
    public boolean isTLSEnabled() {
        return isTLSEnabled;
    }

    /**
     * Sets a flag is TLS is enabled.
     *
     * @param isTLSEnabled
     *            {@code true} is TLS is enabled, {@code false} otherwise.
     */
    public void setTLSEnabled(boolean isTLSEnabled) {
        this.isTLSEnabled = isTLSEnabled;
    }

    /**
     * Returns the port number which is used with SSL.
     *
     * @return The SSL port's number.
     */
    public int getSSLPortNumber() {
        return sslPortNumber;
    }

    /**
     * Sets the SSL's port number.
     *
     * @param sslPortNumber
     *            The port number which should be used with SSL.
     */
    public void setSSLPortNumber(int sslPortNumber) {
        this.sslPortNumber = sslPortNumber;
    }

    /**
     * Returns the certificate linked to this setup.
     *
     * @return The certificate linked to this setup.
     */
    public Certificate getCertificate() {
        return certificate;
    }

    /**
     * Sets the certificate used in this setup.
     *
     * @param certificate
     *            The certificate used in this setup.
     */
    public void setCertificate(Certificate certificate) {
        this.certificate = certificate;
    }

    /**
     * Returns the port number which is currently free.
     * @param startPortNumber The port number to start with.
     * @return The port number which is currently free.
     */
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
