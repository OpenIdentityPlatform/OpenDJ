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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions copyright 2011-2016 ForgeRock AS.
 */
package com.forgerock.opendj.cli;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.CliConstants.DEFAULT_LDAP_PORT;
import static com.forgerock.opendj.cli.CliMessages.*;
import static com.forgerock.opendj.cli.Utils.getHostNameForLdapUrl;
import static com.forgerock.opendj.cli.Utils.throwIfArgumentsConflict;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.*;
import static org.forgerock.util.time.Duration.*;

import static com.forgerock.opendj.cli.CommonArguments.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import com.forgerock.opendj.util.StaticUtils;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.KeyManagers;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.SSLContextBuilder;
import org.forgerock.opendj.ldap.TrustManagers;
import org.forgerock.opendj.ldap.controls.AuthorizationIdentityRequestControl;
import org.forgerock.opendj.ldap.controls.PasswordPolicyRequestControl;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CRAMMD5SASLBindRequest;
import org.forgerock.opendj.ldap.requests.DigestMD5SASLBindRequest;
import org.forgerock.opendj.ldap.requests.ExternalSASLBindRequest;
import org.forgerock.opendj.ldap.requests.GSSAPISASLBindRequest;
import org.forgerock.opendj.ldap.requests.PlainSASLBindRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.util.Options;

/** A connection factory designed for use with command line tools. */
public final class ConnectionFactoryProvider {
    /** The Logger. */
    static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /** The 'hostName' global argument. */
    private StringArgument hostNameArg;
    /** The 'port' global argument. */
    private IntegerArgument portArg;

    /** The 'bindDN' global argument. */
    private StringArgument bindNameArg;
    /** The 'bindPasswordFile' global argument. */
    private FileBasedArgument bindPasswordFileArg;

    /** The 'password' value. */
    private char[] password;
    /** The 'bindPassword' global argument. */
    private StringArgument bindPasswordArg;

    /** The 'connectTimeOut' global argument. */
    private IntegerArgument connectTimeOut;

    /** The 'trustAllArg' global argument. */
    private BooleanArgument trustAllArg;
    /** The 'trustStore' global argument. */
    private StringArgument trustStorePathArg;
    /** The 'trustStorePassword' global argument. */
    private StringArgument trustStorePasswordArg;
    /** The 'trustStorePasswordFile' global argument. */
    private FileBasedArgument trustStorePasswordFileArg;

    /** The 'keyStore' global argument. */
    private StringArgument keyStorePathArg;
    /** The 'keyStorePassword' global argument. */
    private StringArgument keyStorePasswordArg;
    /** The 'keyStorePasswordFile' global argument. */
    private FileBasedArgument keyStorePasswordFileArg;

    /** The 'certNicknameArg' global argument. */
    private StringArgument certNicknameArg;

    /** The 'useSSLArg' global argument. */
    private BooleanArgument useSSLArg;
    /** The 'useStartTLSArg' global argument. */
    private BooleanArgument useStartTLSArg;
    /** Argument indicating a SASL option. */
    private StringArgument saslOptionArg;

    /**
     * Whether to request that the server return the authorization ID in the
     * bind response.
     */
    private final BooleanArgument reportAuthzIDArg;

    /** Whether to use the password policy control in the bind request. */
    private final BooleanArgument usePasswordPolicyControlArg;

    /** The SSL context linked to this connection. */
    private SSLContext sslContext;

    /**  The basic connection factory. */
    private ConnectionFactory connFactory;

    /** The bind request to connect with. */
    private BindRequest bindRequest;

    /** The console application linked to this connection in interactive mode. */
    private final ConsoleApplication app;

    /** If this connection should be an admin connection. */
    private boolean isAdminConnection;

    /**
     * Default constructor to create a connection factory designed for use with command line tools,
     * adding basic LDAP connection arguments to the specified parser (e.g: hostname, bindname...etc).
     *
     * @param argumentParser
     *            The argument parser.
     * @param app
     *            The console application linked to this connection factory.
     * @throws ArgumentException
     *             If an error occurs during parsing the arguments.
     */
    public ConnectionFactoryProvider(final ArgumentParser argumentParser, final ConsoleApplication app)
            throws ArgumentException {
        this(argumentParser, app, "", DEFAULT_LDAP_PORT, false);
    }

    /**
     * Constructor to create a connection factory designed for use with command line tools,
     * adding basic LDAP connection arguments to the specified parser (e.g: hostname, bindname...etc).
     *
     * @param argumentParser
     *            The argument parser.
     * @param app
     *            The console application linked to this connection factory.
     * @param defaultBindDN
     *            The bind DN default's value.
     * @param defaultPort
     *            The LDAP port default's value.
     * @param alwaysSSL
     *            {@code true} if this connection should be used with SSL.
     * @throws ArgumentException
     *             If an error occurs during parsing the elements.
     */
    public ConnectionFactoryProvider(final ArgumentParser argumentParser,
            final ConsoleApplication app, final String defaultBindDN, final int defaultPort,
            final boolean alwaysSSL) throws ArgumentException {
        this.app = app;

        useSSLArg = useSSLArgument();
        if (!alwaysSSL) {
            argumentParser.addLdapConnectionArgument(useSSLArg);
        } else {
            // simulate that the useSSL arg has been given in the CLI
            useSSLArg.setPresent(true);
        }

        useStartTLSArg = startTLSArgument();
        if (!alwaysSSL) {
            argumentParser.addLdapConnectionArgument(useStartTLSArg);
        }

        String defaultHostName = "localhost";
        hostNameArg = hostNameArgument(defaultHostName);
        argumentParser.addLdapConnectionArgument(hostNameArg);

        LocalizableMessage portDescription = INFO_DESCRIPTION_PORT.get();
        if (alwaysSSL) {
            portDescription = INFO_DESCRIPTION_ADMIN_PORT.get();
        }

        portArg = portArgument(defaultPort, portDescription);
        argumentParser.addLdapConnectionArgument(portArg);

        bindNameArg = bindDNArgument(defaultBindDN);
        argumentParser.addLdapConnectionArgument(bindNameArg);

        bindPasswordArg = bindPasswordArgument();
        argumentParser.addLdapConnectionArgument(bindPasswordArg);

        bindPasswordFileArg = bindPasswordFileArgument();
        argumentParser.addLdapConnectionArgument(bindPasswordFileArg);

        saslOptionArg = saslArgument();
        argumentParser.addLdapConnectionArgument(saslOptionArg);

        trustAllArg = trustAllArgument();
        argumentParser.addLdapConnectionArgument(trustAllArg);

        trustStorePathArg = trustStorePathArgument();
        argumentParser.addLdapConnectionArgument(trustStorePathArg);

        trustStorePasswordArg = trustStorePasswordArgument();
        argumentParser.addLdapConnectionArgument(trustStorePasswordArg);

        trustStorePasswordFileArg = trustStorePasswordFileArgument();
        argumentParser.addLdapConnectionArgument(trustStorePasswordFileArg);

        keyStorePathArg = keyStorePathArgument();
        argumentParser.addLdapConnectionArgument(keyStorePathArg);

        keyStorePasswordArg = keyStorePasswordArgument();
        argumentParser.addLdapConnectionArgument(keyStorePasswordArg);

        keyStorePasswordFileArg = keyStorePasswordFileArgument();
        argumentParser.addLdapConnectionArgument(keyStorePasswordFileArg);

        certNicknameArg = certNickNameArgument();
        argumentParser.addLdapConnectionArgument(certNicknameArg);

        reportAuthzIDArg = reportAuthzIdArgument();
        argumentParser.addArgument(reportAuthzIDArg);

        connectTimeOut = connectTimeOutArgument();
        argumentParser.addArgument(connectTimeOut);

        usePasswordPolicyControlArg =
                BooleanArgument.builder(OPTION_LONG_USE_PW_POLICY_CTL)
                        .description(INFO_DESCRIPTION_USE_PWP_CONTROL.get())
                        .buildAndAddToParser(argumentParser);
    }

    /**
     * Returns the connect time out.
     *
     * @return The connect time out value.
     */
    public int getConnectTimeout() {
        if (connectTimeOut.isPresent()) {
            try {
                return connectTimeOut.getIntValue();
            } catch (ArgumentException e) {
                return Integer.valueOf(connectTimeOut.getDefaultValue());
            }
        }
        return Integer.valueOf(connectTimeOut.getDefaultValue());
    }


    /**
     * Returns the host name if the argument is present otherwise, if the application
     * is interactive, prompt the user for it.
     *
     * @return The host name value.
     * @throws ArgumentException
     *             If the host name cannot be retrieved.
     */
    public String getHostname() throws ArgumentException {
        String value = "";

        if (hostNameArg.isPresent()) {
            value = hostNameArg.getValue();
        } else if (app.isInteractive()) {
            try {
                value = app.readInput(INFO_DESCRIPTION_HOST.get(), getHostNameDefaultValue(value));
                app.println();
                hostNameArg.addValue(value);
                hostNameArg.setPresent(true);
            } catch (ClientException e) {
                throw new ArgumentException(ERR_ERROR_CANNOT_READ_HOST_NAME.get(), e);
            }
        } else {
            return getHostNameDefaultValue(value);
        }

        return getHostNameForLdapUrl(value);
    }

    private String getHostNameDefaultValue(String fallbackValue) {
        return hostNameArg.getDefaultValue() != null ? hostNameArg.getDefaultValue() : fallbackValue;
    }

    /**
     * Get the port which has to be used for the command.
     *
     * @return The port specified by the command line argument, or the default value, if not specified.
     */
    public int getPort() {
        if (portArg.isPresent()) {
            try {
                return portArg.getIntValue();
            } catch (ArgumentException e) {
                return Integer.valueOf(portArg.getDefaultValue());
            }
        } else if (app.isInteractive()) {
            final LocalizableMessage portMsg =
                    isAdminConnection ? INFO_DESCRIPTION_ADMIN_PORT.get() : INFO_DESCRIPTION_PORT.get();
            int value = app.askPort(portMsg, Integer.valueOf(portArg.getDefaultValue()), logger);
            app.println();
            portArg.addValue(Integer.toString(value));
            portArg.setPresent(true);
            return value;
        }
        return Integer.valueOf(portArg.getDefaultValue());
    }

    /**
     * Indicate if the SSL mode is required.
     *
     * @return True if SSL mode is required
     */
    public boolean useSSL() {
        return useSSLArg.isPresent();
    }

    /**
     * Indicate if the startTLS mode is required.
     *
     * @return True if startTLS mode is required
     */
    public boolean useStartTLS() {
        return useStartTLSArg.isPresent();
    }

    /**
     * Return a list of the available TLS protocols to use.
     *
     * <p>
     * All protocols starting with "SSL" are removed. In particular the SSLv2Hello pseudo protocol is removed which will
     * prevent connections to a server that doesn't have this configured.
     * </p>
     * <p>
     * To override the defaults, set the <em>org.opends.ldaps.protocols</em> system property to a comma-separated list
     * of desired protocols.
     * </p>
     *
     * @return A list of valid protocol strings.
     * @throws NoSuchAlgorithmException
     *         If an SSL context could not be created.
     */
    public static List<String> getDefaultProtocols() throws NoSuchAlgorithmException {
    	return getDefaultProtocols(SSLContext.getDefault());
    }
    public static List<String> getDefaultProtocols(SSLContext sslContext) throws NoSuchAlgorithmException {
        List<String> enabled = Arrays.asList(sslContext.createSSLEngine().getEnabledProtocols());
        final String property = System.getProperty("org.opends.ldaps.protocols");
        final List<String> defaults = new ArrayList<>();
        if (property != null && property.length() != 0) {
            for (String protocol : property.split(",")) {
                if (enabled.contains(protocol)) {
                    defaults.add(protocol);
                }
            }
            return defaults;
        }
        // exclude SSLv2Hello and SSLv3
        for (String protocol : enabled) {
            if (!protocol.startsWith("SSL")) {
                defaults.add(protocol);
            }
        }
        return defaults;
    }

    /**
     * Constructs a connection factory for pre-authenticated connections. Checks if any conflicting arguments are
     * present, build the connection with selected arguments and returns the connection factory. If the application is
     * interactive, it will prompt the user for missing parameters.
     *
     * @return The connection factory.
     * @throws ArgumentException
     *         If an error occurs during the parsing of the arguments (conflicting arguments or if an error occurs
     *         during building SSL context).
     */
    public ConnectionFactory getAuthenticatedConnectionFactory() throws ArgumentException {
        return getConnectionFactory(true);
    }

    /**
     * Constructs a connection factory for unauthenticated connections. Checks if any conflicting arguments are present,
     * build the connection with selected arguments and returns the connection factory. If the application is
     * interactive, it will prompt the user for missing parameters.
     *
     * @return The connection factory.
     * @throws ArgumentException
     *         If an error occurs during the parsing of the arguments (conflicting arguments or if an error occurs
     *         during building SSL context).
     */
    public ConnectionFactory getUnauthenticatedConnectionFactory() throws ArgumentException {
        return getConnectionFactory(false);
    }

    private ConnectionFactory getConnectionFactory(boolean usePreAuthentication) throws ArgumentException {
        if (connFactory == null) {
            checkForConflictingArguments();

            if (app.isInteractive()) {
                boolean portIsMissing = !portArg.isPresent() || portArg.getIntValue() == 0;
                boolean bindPwdIsMissing = !bindPasswordArg.isPresent() && !bindPasswordFileArg.isPresent();
                if (!hostNameArg.isPresent() || portIsMissing || !bindNameArg.isPresent() || bindPwdIsMissing) {
                    app.printHeader(INFO_LDAP_CONN_HEADING_CONNECTION_PARAMETERS.get());
                }
                if (!hostNameArg.isPresent()) {
                    getHostname();
                }
                if (portIsMissing) {
                    getPort();
                }
                if (!bindNameArg.isPresent()) {
                    getBindName();
                }
                if (bindPwdIsMissing) {
                    getPassword();
                }
            }

            try {
                if (useSSLArg.isPresent() || useStartTLSArg.isPresent()) {
                    String clientAlias;
                    if (certNicknameArg.isPresent()) {
                        clientAlias = certNicknameArg.getValue();
                    } else {
                        clientAlias = null;
                    }

                    if (sslContext == null) {
                        final TrustManager trustManager = getTrustManager();

                        X509KeyManager keyManager = null;
                        final X509KeyManager akm = getKeyManager(keyStorePathArg.getValue());

                        if (akm != null && clientAlias != null) {
                            keyManager = KeyManagers.useSingleCertificate(clientAlias, akm);
                        } else {
                        	keyManager = akm;
                        }

                        sslContext =
                                new SSLContextBuilder().setTrustManager(trustManager)
                                        .setKeyManager(keyManager).getSSLContext();
                    }
                }
            } catch (final Exception e) {
                throw new ArgumentException(ERR_LDAP_CONN_CANNOT_INITIALIZE_SSL.get(e.toString()),
                        e);
            }

            Options options = Options.defaultOptions();
            if (sslContext != null) {
                try {
                    options.set(SSL_CONTEXT, sslContext)
                            .set(SSL_USE_STARTTLS, useStartTLSArg.isPresent())
                            .set(SSL_ENABLED_PROTOCOLS, getDefaultProtocols(sslContext));
                } catch (NoSuchAlgorithmException e) {
                    throw new ArgumentException(ERR_LDAP_CONN_CANNOT_INITIALIZE_SSL.get(e.toString()), e);
                }
            }
            options.set(CONNECT_TIMEOUT, duration(getConnectTimeout(), TimeUnit.MILLISECONDS));
            if (usePreAuthentication) {
                options.set(AUTHN_BIND_REQUEST, getBindRequest());
            }
            connFactory = new LDAPConnectionFactory(hostNameArg.getValue(), portArg.getIntValue(), options);
        }
        return connFactory;
    }

    /**
     * Verifies if the connection arguments are not conflicting together or if they are readable.
     *
     * @throws ArgumentException
     *             If arguments are conflicting or if the files cannot be read,
     *             an argument exception is thrown.
     */
    private void checkForConflictingArguments() throws ArgumentException {
        throwIfArgumentsConflict(bindPasswordArg, bindPasswordFileArg);
        throwIfArgumentsConflict(trustAllArg, trustStorePathArg);
        throwIfArgumentsConflict(trustAllArg, trustStorePasswordArg);
        throwIfArgumentsConflict(trustAllArg, trustStorePasswordFileArg);
        throwIfArgumentsConflict(trustStorePasswordArg, trustStorePasswordFileArg);
        throwIfArgumentsConflict(useStartTLSArg, useSSLArg);

        if (trustStorePathArg.isPresent()) {
            // Check that the path exists and is readable
            final String value = trustStorePathArg.getValue();
            if (!canReadPath(value)) {
                final LocalizableMessage message = ERR_CANNOT_READ_TRUSTSTORE.get(value);
                throw new ArgumentException(message);
            }
        }

        if (keyStorePathArg.isPresent()) {
            // Check that the path exists and is readable
            final String value = keyStorePathArg.getValue();
            if (!canReadPath(value)) {
                final LocalizableMessage message = ERR_CANNOT_READ_KEYSTORE.get(value);
                throw new ArgumentException(message);
            }
        }
    }

    /**
     * Returns {@code true} if we can read on the provided path and
     * {@code false} otherwise.
     *
     * @param path
     *            the path.
     * @return {@code true} if we can read on the provided path and
     *         {@code false} otherwise.
     */
    private boolean canReadPath(final String path) {
        final File file = new File(path);
        return file.exists() && file.canRead();
    }

    private String getAuthID(final String mech) throws ArgumentException {
        String value = getAuthID();
        if (value == null && bindNameArg.isPresent()) {
            value = "dn: " + bindNameArg.getValue();
        }
        if (value == null && app.isInteractive()) {
            try {
                value =
                        app.readInput(LocalizableMessage.raw("Authentication ID:"), bindNameArg
                                .getDefaultValue() == null ? null : "dn: "
                                + bindNameArg.getDefaultValue());
            } catch (ClientException e) {
                throw new ArgumentException(LocalizableMessage
                        .raw("Unable to read authentication ID"), e);
            }
        }
        if (value == null) {
            throw new ArgumentException(ERR_LDAPAUTH_SASL_AUTHID_REQUIRED.get(mech));
        }
        return value;
    }

    private String getAuthID() throws ArgumentException {
        return getSaslProperty(SASL_PROPERTY_AUTHID);
    }

    private String getAuthzID() throws ArgumentException {
        return getSaslProperty(SASL_PROPERTY_AUTHZID);
    }

    /**
     * Returns the bind name if the argument is present otherwise, in interactive mode, it
     * will prompt the user.
     *
     * @return The bind name used for this connection.
     * @throws ArgumentException
     *             If the bind name cannot be retrieved.
     */
    public String getBindName() throws ArgumentException {
        String value = "";
        if (bindNameArg.isPresent()) {
            value = bindNameArg.getValue();
        } else if (app.isInteractive()) {
            LocalizableMessage bindMsg;
            if (isAdminConnection) {
                bindMsg = INFO_DESCRIPTION_ADMIN_BINDDN.get();
            } else {
                bindMsg = INFO_DESCRIPTION_BINDDN.get();
            }
            try {
                value = app.readInput(bindMsg,
                        bindNameArg.getDefaultValue() == null ? value : bindNameArg.getDefaultValue());
                app.println();
                bindNameArg.clearValues();
                bindNameArg.addValue(value);
                bindNameArg.setPresent(true);
            } catch (ClientException e) {
                throw new ArgumentException(ERR_ERROR_CANNOT_READ_BIND_NAME.get(), e);
            }
        }

        return value;
    }

    /**
     * Returns the bind request for this connection.
     *
     * @return The bind request for this connection.
     * @throws ArgumentException
     *             If the arguments of this connection are wrong.
     */
    public BindRequest getBindRequest() throws ArgumentException {
        if (bindRequest == null) {
            String mech = getMechanism();

            if (mech == null) {
                if (bindNameArg.isPresent() || bindPasswordFileArg.isPresent()
                        || bindPasswordArg.isPresent()) {
                    bindRequest = Requests.newSimpleBindRequest(getBindName(), getPassword());
                }
            } else if (DigestMD5SASLBindRequest.SASL_MECHANISM_NAME.equals(mech)) {
                bindRequest =
                        Requests.newDigestMD5SASLBindRequest(
                                getAuthID(DigestMD5SASLBindRequest.SASL_MECHANISM_NAME),
                                getPassword()).setAuthorizationID(getAuthzID())
                                .setRealm(getRealm());
            } else if (CRAMMD5SASLBindRequest.SASL_MECHANISM_NAME.equals(mech)) {
                bindRequest =
                        Requests.newCRAMMD5SASLBindRequest(
                                getAuthID(CRAMMD5SASLBindRequest.SASL_MECHANISM_NAME),
                                getPassword());
            } else if (GSSAPISASLBindRequest.SASL_MECHANISM_NAME.equals(mech)) {
                bindRequest =
                        Requests.newGSSAPISASLBindRequest(
                                getAuthID(GSSAPISASLBindRequest.SASL_MECHANISM_NAME), getPassword())
                                .setKDCAddress(getKDC()).setRealm(getRealm()).setAuthorizationID(
                                        getAuthzID());
            } else if (ExternalSASLBindRequest.SASL_MECHANISM_NAME.equals(mech)) {
                if (sslContext == null) {
                    final LocalizableMessage message = ERR_TOOL_SASLEXTERNAL_NEEDS_SSL_OR_TLS.get();
                    throw new ArgumentException(message);
                }
                if (!keyStorePathArg.isPresent() && getKeyStore() == null) {
                    final LocalizableMessage message = ERR_TOOL_SASLEXTERNAL_NEEDS_KEYSTORE.get();
                    throw new ArgumentException(message);
                }
                bindRequest =
                        Requests.newExternalSASLBindRequest().setAuthorizationID(getAuthzID());
            } else if (PlainSASLBindRequest.SASL_MECHANISM_NAME.equals(mech)) {
                bindRequest =
                        Requests.newPlainSASLBindRequest(
                                getAuthID(PlainSASLBindRequest.SASL_MECHANISM_NAME), getPassword())
                                .setAuthorizationID(getAuthzID());
            } else {
                throw new ArgumentException(ERR_LDAPAUTH_UNSUPPORTED_SASL_MECHANISM.get(mech));
            }

            if (bindRequest != null && reportAuthzIDArg.isPresent()) {
                bindRequest.addControl(AuthorizationIdentityRequestControl.newControl(false));
            }

            if (bindRequest != null && usePasswordPolicyControlArg.isPresent()) {
                bindRequest.addControl(PasswordPolicyRequestControl.newControl(false));
            }
        }
        return bindRequest;
    }

    private String getMechanism() throws ArgumentException {
        return getSaslProperty(SASL_PROPERTY_MECH);
    }

    private String getKDC() throws ArgumentException {
        return getSaslProperty(SASL_PROPERTY_KDC);
    }

    private String getRealm() throws ArgumentException {
        return getSaslProperty(SASL_PROPERTY_REALM);
    }

    private String getSaslProperty(String propertyName) throws ArgumentException {
        for (final String s : saslOptionArg.getValues()) {
            if (s.startsWith(propertyName)) {
                return parseSASLOptionValue(s);
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return connFactory.toString();
    }

    /**
     * Retrieves a <CODE>KeyManager</CODE> object that may be used for
     * interactions requiring access to a key manager.
     *
     * @param keyStoreFile
     *            The path to the file containing the key store data.
     * @return A set of <CODE>KeyManager</CODE> objects that may be used for
     *         interactions requiring access to a key manager.
     * @throws java.security.KeyStoreException
     *             If a problem occurs while interacting with the key store.
     * @throws IOException
     *             If there is an I/O or format problem with the keystore data.
     * @throws NoSuchAlgorithmException
     *             If a problem occurs while loading with the key store.
     * @throws CertificateException
     *             If a problem occurs while loading with the key store.
     * @throws UnrecoverableKeyException 
     */
    public X509KeyManager getKeyManager(String keyStoreFile) throws KeyStoreException,
            IOException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException {
        if (keyStoreFile == null) {
            // Lookup the file name through the JDK property.
            keyStoreFile = getKeyStore();
        }

        if (keyStoreFile == null) {
            return null;
        }

        final String keyStorePass = getKeyStorePIN();
        char[] keyStorePIN = null;
        if (keyStorePass != null) {
            keyStorePIN = keyStorePass.toCharArray();
        }

        boolean isFips = StaticUtils.isFips();
        final String keyStoreType = KeyStore.getDefaultType();
        final KeyStore keystore = KeyStore.getInstance(keyStoreType);
        if (isFips) {
            keystore.load(null, keyStorePIN);
        } else {
            try (final FileInputStream fos = new FileInputStream(keyStoreFile)) {
                keystore.load(fos, keyStorePIN);
            }
        }

        if (isFips) {
	        String keyManagerAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
	        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(keyManagerAlgorithm);
	        keyManagerFactory.init(keystore, keyStorePIN);
	        
	        for (final KeyManager km : keyManagerFactory.getKeyManagers()) {
	            if (km instanceof X509KeyManager) {
	                return (X509KeyManager) km;
	            }
	        }
        }
        
        return new ApplicationKeyManager(keystore, keyStorePIN);
    }

    /**
     * Read the KeyStore from the JSSE system property.
     *
     * @return The path to the key store file.
     */

    private String getKeyStore() {
        return System.getProperty("javax.net.ssl.keyStore");
    }

    /**
     * Read the KeyStore PIN from the JSSE system property.
     *
     * @return The PIN that should be used to access the key store.
     */

    private String getKeyStorePIN() {
        String pwd;
        if (keyStorePasswordArg.isPresent()) {
            pwd = keyStorePasswordArg.getValue();
        } else if (keyStorePasswordFileArg.isPresent()) {
            pwd = keyStorePasswordFileArg.getValue();
        } else {
            pwd = System.getProperty("javax.net.ssl.keyStorePassword");
        }
        return pwd;
    }

    /**
     * Get the password which has to be used for the command. In interactive mode, if
     * the password arguments are missing, the user will be prompted.
     *
     * @return The password stored into the specified file on by the command
     *         line argument, or empty it if not specified.
     * @throws ArgumentException
     *             If a problem occurs while interacting with the password.
     */
    public char[] getPassword() throws ArgumentException {
        char[] value = "".toCharArray();

        if (bindPasswordArg.isPresent()) {
            value = bindPasswordArg.getValue().toCharArray();
        } else if (bindPasswordFileArg.isPresent()) {
            value = bindPasswordFileArg.getValue().toCharArray();
        } else if (password != null) {
            return password;
        }

        if (value.length == 0 && app.isInteractive()) {
            LocalizableMessage msg;
            if (isAdminConnection) {
                msg = INFO_LDAPAUTH_PASSWORD_PROMPT.get(getBindName());
            } else {
                msg = INFO_DESCRIPTION_BINDPASSWORD.get();
            }
            try {
                value = app.readPassword(msg);
                app.println();
            } catch (ClientException e) {
                throw new ArgumentException(ERR_ERROR_CANNOT_READ_PASSWORD.get(), e);
            }
            password = value;
        }
        return value;
    }

    /**
     * Retrieves a <CODE>TrustManager</CODE> object that may be used for
     * interactions requiring access to a trust manager.
     *
     * @return A set of <CODE>TrustManager</CODE> objects that may be used for
     *         interactions requiring access to a trust manager.
     * @throws IOException
     *             If the trust store file could not be found or could not be read.
     * @throws GeneralSecurityException
     *             If a problem occurs while interacting with the trust store.
     */
    public TrustManager getTrustManager() throws IOException, GeneralSecurityException {
        if (trustAllArg.isPresent()) {
            return TrustManagers.trustAll();
        }

        boolean isFips = StaticUtils.isFips();
        X509TrustManager tm = null;
        if (trustStorePathArg.isPresent() && trustStorePathArg.getValue().length() > 0) {
        	if (isFips) {
	            tm = TrustManagers.checkUsingTrustStore(trustStorePathArg.getValue(), getTrustStorePIN(), null);
        	} else {
	            tm = TrustManagers.checkValidityDates(TrustManagers.checkHostName(hostNameArg.getValue(),
	                    TrustManagers.checkUsingTrustStore(trustStorePathArg.getValue(), getTrustStorePIN(), null)));
        	}
        } else if (getTrustStore() != null) {
        	if (isFips) {
	            tm = TrustManagers.checkUsingTrustStore(getTrustStore(), getTrustStorePIN(), null);
        	} else {
                tm = TrustManagers.checkValidityDates(TrustManagers.checkHostName(hostNameArg.getValue(),
                        TrustManagers.checkUsingTrustStore(getTrustStore(), getTrustStorePIN(), null)));
        	}
        }

        if (app != null && !app.isQuiet() && !isFips) {
            return new PromptingTrustManager(app, tm);
        }

        if (isFips) {
            return TrustManagers.checkUsingPkcs12TrustStore();
        }

        return tm;
    }

    /**
     * Read the TrustStore from the JSSE system property.
     *
     * @return The path to the trust store file.
     */

    private String getTrustStore() {
        return System.getProperty("javax.net.ssl.trustStore");
    }

    /**
     * Read the TrustStore PIN from the JSSE system property.
     *
     * @return The PIN that should be used to access the trust store, can be null.
     */
    private char[] getTrustStorePIN() {
        String pwd;
        if (trustStorePasswordArg.isPresent()) {
            pwd = trustStorePasswordArg.getValue();
        } else if (trustStorePasswordFileArg.isPresent()) {
            pwd = trustStorePasswordFileArg.getValue();
        } else {
            pwd = System.getProperty("javax.net.ssl.trustStorePassword");
        }
        return pwd == null ? null : pwd.toCharArray();
    }

    private String parseSASLOptionValue(final String option) throws ArgumentException {
        final int equalPos = option.indexOf('=');
        if (equalPos == -1) {
            throw new ArgumentException(ERR_LDAP_CONN_CANNOT_PARSE_SASL_OPTION.get(option));
        }
        return option.substring(equalPos + 1, option.length());
    }

    /**
     * Specifies if this connection should be an administrator connection. If sets as one, the messages prompted to the
     * user will be different as a normal connection. E.g if set :
     *
     * <pre>
     * >>>> Specify OpenDJ LDAP connection parameters
     *
     * Directory server administration port number [4444]:
     * </pre>
     *
     * vs normal mode
     *
     * <pre>
     * >>>> Specify OpenDJ LDAP connection parameters
     *
     * Directory server port number [1389]:
     * </pre>
     */
    public void setIsAnAdminConnection() {
        isAdminConnection = true;
    }
}
