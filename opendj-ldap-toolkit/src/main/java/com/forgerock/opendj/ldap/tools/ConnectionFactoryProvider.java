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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2012 ForgeRock AS
 */

package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.ldap.tools.ToolConstants.*;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.KeyManagers;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LDAPOptions;
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

/**
 * A connection factory designed for use with command line tools.
 */
final class ConnectionFactoryProvider {
    /**
     * End Of Line.
     */
    static final String EOL = System.getProperty("line.separator");

    /**
     * The Logger.
     */
    static final Logger LOG = Logger.getLogger(ConnectionFactoryProvider.class.getName());

    /**
     * The 'hostName' global argument.
     */
    private StringArgument hostNameArg = null;

    /**
     * The 'port' global argument.
     */
    private IntegerArgument portArg = null;

    /**
     * The 'bindDN' global argument.
     */
    private StringArgument bindNameArg = null;

    /**
     * The 'bindPasswordFile' global argument.
     */
    private FileBasedArgument bindPasswordFileArg = null;

    /**
     * The 'bindPassword' global argument.
     */
    private StringArgument bindPasswordArg = null;

    /**
     * The 'trustAllArg' global argument.
     */
    private BooleanArgument trustAllArg = null;

    /**
     * The 'trustStore' global argument.
     */
    private StringArgument trustStorePathArg = null;

    /**
     * The 'trustStorePassword' global argument.
     */
    private StringArgument trustStorePasswordArg = null;

    /**
     * The 'trustStorePasswordFile' global argument.
     */
    private FileBasedArgument trustStorePasswordFileArg = null;

    /**
     * The 'keyStore' global argument.
     */
    private StringArgument keyStorePathArg = null;

    /**
     * The 'keyStorePassword' global argument.
     */
    private StringArgument keyStorePasswordArg = null;

    /**
     * The 'keyStorePasswordFile' global argument.
     */
    private FileBasedArgument keyStorePasswordFileArg = null;

    /**
     * The 'certNicknameArg' global argument.
     */
    private StringArgument certNicknameArg = null;

    /**
     * The 'useSSLArg' global argument.
     */
    private BooleanArgument useSSLArg = null;

    /**
     * The 'useStartTLSArg' global argument.
     */
    private BooleanArgument useStartTLSArg = null;

    /**
     * Argument indicating a SASL option.
     */
    private StringArgument saslOptionArg = null;

    /**
     * Whether to request that the server return the authorization ID in the
     * bind response.
     */
    private final BooleanArgument reportAuthzIDArg;

    /**
     * Whether to use the password policy control in the bind request.
     */
    private final BooleanArgument usePasswordPolicyControlArg;

    private int port = 389;

    private SSLContext sslContext;

    private ConnectionFactory connFactory;

    private ConnectionFactory authenticatedConnFactory;

    private BindRequest bindRequest = null;

    private final ConsoleApplication app;

    public ConnectionFactoryProvider(final ArgumentParser argumentParser,
            final ConsoleApplication app) throws ArgumentException {
        this(argumentParser, app, "cn=Directory Manager", 389, false);
    }

    public ConnectionFactoryProvider(final ArgumentParser argumentParser,
            final ConsoleApplication app, final String defaultBindDN, final int defaultPort,
            final boolean alwaysSSL) throws ArgumentException {
        this.app = app;
        useSSLArg =
                new BooleanArgument("useSSL", OPTION_SHORT_USE_SSL, OPTION_LONG_USE_SSL,
                        INFO_DESCRIPTION_USE_SSL.get());
        useSSLArg.setPropertyName(OPTION_LONG_USE_SSL);
        if (!alwaysSSL) {
            argumentParser.addLdapConnectionArgument(useSSLArg);
        } else {
            // simulate that the useSSL arg has been given in the CLI
            useSSLArg.setPresent(true);
        }

        useStartTLSArg =
                new BooleanArgument("startTLS", OPTION_SHORT_START_TLS, OPTION_LONG_START_TLS,
                        INFO_DESCRIPTION_START_TLS.get());
        useStartTLSArg.setPropertyName(OPTION_LONG_START_TLS);
        if (!alwaysSSL) {
            argumentParser.addLdapConnectionArgument(useStartTLSArg);
        }

        String defaultHostName;
        try {
            defaultHostName = InetAddress.getLocalHost().getHostName();
        } catch (final Exception e) {
            defaultHostName = "Unknown (" + e + ")";
        }
        hostNameArg =
                new StringArgument("host", OPTION_SHORT_HOST, OPTION_LONG_HOST, false, false, true,
                        INFO_HOST_PLACEHOLDER.get(), defaultHostName, null, INFO_DESCRIPTION_HOST
                                .get());
        hostNameArg.setPropertyName(OPTION_LONG_HOST);
        argumentParser.addLdapConnectionArgument(hostNameArg);

        LocalizableMessage portDescription = INFO_DESCRIPTION_PORT.get();
        if (alwaysSSL) {
            portDescription = INFO_DESCRIPTION_ADMIN_PORT.get();
        }

        portArg =
                new IntegerArgument("port", OPTION_SHORT_PORT, OPTION_LONG_PORT, false, false,
                        true, INFO_PORT_PLACEHOLDER.get(), defaultPort, null, portDescription);
        portArg.setPropertyName(OPTION_LONG_PORT);
        argumentParser.addLdapConnectionArgument(portArg);

        bindNameArg =
                new StringArgument("bindDN", OPTION_SHORT_BINDDN, OPTION_LONG_BINDDN, false, false,
                        true, INFO_BINDDN_PLACEHOLDER.get(), defaultBindDN, null,
                        INFO_DESCRIPTION_BINDDN.get());
        bindNameArg.setPropertyName(OPTION_LONG_BINDDN);
        argumentParser.addLdapConnectionArgument(bindNameArg);

        bindPasswordArg =
                new StringArgument("bindPassword", OPTION_SHORT_BINDPWD, OPTION_LONG_BINDPWD,
                        false, false, true, INFO_BINDPWD_PLACEHOLDER.get(), null, null,
                        INFO_DESCRIPTION_BINDPASSWORD.get());
        bindPasswordArg.setPropertyName(OPTION_LONG_BINDPWD);
        argumentParser.addLdapConnectionArgument(bindPasswordArg);

        bindPasswordFileArg =
                new FileBasedArgument("bindPasswordFile", OPTION_SHORT_BINDPWD_FILE,
                        OPTION_LONG_BINDPWD_FILE, false, false,
                        INFO_BINDPWD_FILE_PLACEHOLDER.get(), null, null,
                        INFO_DESCRIPTION_BINDPASSWORDFILE.get());
        bindPasswordFileArg.setPropertyName(OPTION_LONG_BINDPWD_FILE);
        argumentParser.addLdapConnectionArgument(bindPasswordFileArg);

        saslOptionArg =
                new StringArgument("sasloption", OPTION_SHORT_SASLOPTION, OPTION_LONG_SASLOPTION,
                        false, true, true, INFO_SASL_OPTION_PLACEHOLDER.get(), null, null,
                        INFO_LDAP_CONN_DESCRIPTION_SASLOPTIONS.get());
        saslOptionArg.setPropertyName(OPTION_LONG_SASLOPTION);
        argumentParser.addLdapConnectionArgument(saslOptionArg);

        trustAllArg =
                new BooleanArgument("trustAll", OPTION_SHORT_TRUSTALL, OPTION_LONG_TRUSTALL,
                        INFO_DESCRIPTION_TRUSTALL.get());
        trustAllArg.setPropertyName(OPTION_LONG_TRUSTALL);
        argumentParser.addLdapConnectionArgument(trustAllArg);

        trustStorePathArg =
                new StringArgument("trustStorePath", OPTION_SHORT_TRUSTSTOREPATH,
                        OPTION_LONG_TRUSTSTOREPATH, false, false, true,
                        INFO_TRUSTSTOREPATH_PLACEHOLDER.get(), null, null,
                        INFO_DESCRIPTION_TRUSTSTOREPATH.get());
        trustStorePathArg.setPropertyName(OPTION_LONG_TRUSTSTOREPATH);
        argumentParser.addLdapConnectionArgument(trustStorePathArg);

        trustStorePasswordArg =
                new StringArgument("trustStorePassword", OPTION_SHORT_TRUSTSTORE_PWD,
                        OPTION_LONG_TRUSTSTORE_PWD, false, false, true,
                        INFO_TRUSTSTORE_PWD_PLACEHOLDER.get(), null, null,
                        INFO_DESCRIPTION_TRUSTSTOREPASSWORD.get());
        trustStorePasswordArg.setPropertyName(OPTION_LONG_TRUSTSTORE_PWD);
        argumentParser.addLdapConnectionArgument(trustStorePasswordArg);

        trustStorePasswordFileArg =
                new FileBasedArgument("trustStorePasswordFile", OPTION_SHORT_TRUSTSTORE_PWD_FILE,
                        OPTION_LONG_TRUSTSTORE_PWD_FILE, false, false,
                        INFO_TRUSTSTORE_PWD_FILE_PLACEHOLDER.get(), null, null,
                        INFO_DESCRIPTION_TRUSTSTOREPASSWORD_FILE.get());
        trustStorePasswordFileArg.setPropertyName(OPTION_LONG_TRUSTSTORE_PWD_FILE);
        argumentParser.addLdapConnectionArgument(trustStorePasswordFileArg);

        keyStorePathArg =
                new StringArgument("keyStorePath", OPTION_SHORT_KEYSTOREPATH,
                        OPTION_LONG_KEYSTOREPATH, false, false, true, INFO_KEYSTOREPATH_PLACEHOLDER
                                .get(), null, null, INFO_DESCRIPTION_KEYSTOREPATH.get());
        keyStorePathArg.setPropertyName(OPTION_LONG_KEYSTOREPATH);
        argumentParser.addLdapConnectionArgument(keyStorePathArg);

        keyStorePasswordArg =
                new StringArgument("keyStorePassword", OPTION_SHORT_KEYSTORE_PWD,
                        OPTION_LONG_KEYSTORE_PWD, false, false, true, INFO_KEYSTORE_PWD_PLACEHOLDER
                                .get(), null, null, INFO_DESCRIPTION_KEYSTOREPASSWORD.get());
        keyStorePasswordArg.setPropertyName(OPTION_LONG_KEYSTORE_PWD);
        argumentParser.addLdapConnectionArgument(keyStorePasswordArg);

        keyStorePasswordFileArg =
                new FileBasedArgument("keystorePasswordFile", OPTION_SHORT_KEYSTORE_PWD_FILE,
                        OPTION_LONG_KEYSTORE_PWD_FILE, false, false,
                        INFO_KEYSTORE_PWD_FILE_PLACEHOLDER.get(), null, null,
                        INFO_DESCRIPTION_KEYSTOREPASSWORD_FILE.get());
        keyStorePasswordFileArg.setPropertyName(OPTION_LONG_KEYSTORE_PWD_FILE);
        argumentParser.addLdapConnectionArgument(keyStorePasswordFileArg);

        certNicknameArg =
                new StringArgument("certNickname", OPTION_SHORT_CERT_NICKNAME,
                        OPTION_LONG_CERT_NICKNAME, false, false, true, INFO_NICKNAME_PLACEHOLDER
                                .get(), null, null, INFO_DESCRIPTION_CERT_NICKNAME.get());
        certNicknameArg.setPropertyName(OPTION_LONG_CERT_NICKNAME);
        argumentParser.addLdapConnectionArgument(certNicknameArg);

        reportAuthzIDArg =
                new BooleanArgument("reportauthzid", 'E', OPTION_LONG_REPORT_AUTHZ_ID,
                        INFO_DESCRIPTION_REPORT_AUTHZID.get());
        reportAuthzIDArg.setPropertyName(OPTION_LONG_REPORT_AUTHZ_ID);
        argumentParser.addArgument(reportAuthzIDArg);

        usePasswordPolicyControlArg =
                new BooleanArgument("usepwpolicycontrol", null, OPTION_LONG_USE_PW_POLICY_CTL,
                        INFO_DESCRIPTION_USE_PWP_CONTROL.get());
        usePasswordPolicyControlArg.setPropertyName(OPTION_LONG_USE_PW_POLICY_CTL);
        argumentParser.addArgument(usePasswordPolicyControlArg);
    }

    public ConnectionFactory getConnectionFactory() throws ArgumentException {
        if (connFactory == null) {
            port = portArg.getIntValue();

            // Couldn't have at the same time bindPassword and bindPasswordFile
            if (bindPasswordArg.isPresent() && bindPasswordFileArg.isPresent()) {
                final LocalizableMessage message =
                        ERR_TOOL_CONFLICTING_ARGS.get(bindPasswordArg.getLongIdentifier(),
                                bindPasswordFileArg.getLongIdentifier());
                throw new ArgumentException(message);
            }

            // Couldn't have at the same time trustAll and
            // trustStore related arg
            if (trustAllArg.isPresent() && trustStorePathArg.isPresent()) {
                final LocalizableMessage message =
                        ERR_TOOL_CONFLICTING_ARGS.get(trustAllArg.getLongIdentifier(),
                                trustStorePathArg.getLongIdentifier());
                throw new ArgumentException(message);
            }
            if (trustAllArg.isPresent() && trustStorePasswordArg.isPresent()) {
                final LocalizableMessage message =
                        ERR_TOOL_CONFLICTING_ARGS.get(trustAllArg.getLongIdentifier(),
                                trustStorePasswordArg.getLongIdentifier());
                throw new ArgumentException(message);
            }
            if (trustAllArg.isPresent() && trustStorePasswordFileArg.isPresent()) {
                final LocalizableMessage message =
                        ERR_TOOL_CONFLICTING_ARGS.get(trustAllArg.getLongIdentifier(),
                                trustStorePasswordFileArg.getLongIdentifier());
                throw new ArgumentException(message);
            }

            // Couldn't have at the same time trustStorePasswordArg and
            // trustStorePasswordFileArg
            if (trustStorePasswordArg.isPresent() && trustStorePasswordFileArg.isPresent()) {
                final LocalizableMessage message =
                        ERR_TOOL_CONFLICTING_ARGS.get(trustStorePasswordArg.getLongIdentifier(),
                                trustStorePasswordFileArg.getLongIdentifier());
                throw new ArgumentException(message);
            }

            if (trustStorePathArg.isPresent()) {
                // Check that the path exists and is readable
                final String value = trustStorePathArg.getValue();
                if (!canRead(trustStorePathArg.getValue())) {
                    final LocalizableMessage message = ERR_CANNOT_READ_TRUSTSTORE.get(value);
                    throw new ArgumentException(message);
                }
            }

            if (keyStorePathArg.isPresent()) {
                // Check that the path exists and is readable
                final String value = keyStorePathArg.getValue();
                if (!canRead(trustStorePathArg.getValue())) {
                    final LocalizableMessage message = ERR_CANNOT_READ_KEYSTORE.get(value);
                    throw new ArgumentException(message);
                }
            }

            // Couldn't have at the same time startTLSArg and
            // useSSLArg
            if (useStartTLSArg.isPresent() && useSSLArg.isPresent()) {
                final LocalizableMessage message =
                        ERR_TOOL_CONFLICTING_ARGS.get(useStartTLSArg.getLongIdentifier(), useSSLArg
                                .getLongIdentifier());
                throw new ArgumentException(message);
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

            if (sslContext != null) {
                final LDAPOptions options =
                        new LDAPOptions().setSSLContext(sslContext).setUseStartTLS(
                                useStartTLSArg.isPresent());
                connFactory = new LDAPConnectionFactory(hostNameArg.getValue(), port, options);
            } else {
                connFactory = new LDAPConnectionFactory(hostNameArg.getValue(), port);
            }
        }
        return connFactory;
    }

    public ConnectionFactory getAuthenticatedConnectionFactory() throws ArgumentException {
        if (authenticatedConnFactory == null) {
            authenticatedConnFactory = getConnectionFactory();
            BindRequest bindRequest = getBindRequest();
            if (bindRequest != null) {
                authenticatedConnFactory =
                        new AuthenticatedConnectionFactory(authenticatedConnFactory, bindRequest);
            }
        }
        return authenticatedConnFactory;
    }

    /**
     * Returns <CODE>true</CODE> if we can read on the provided path and
     * <CODE>false</CODE> otherwise.
     *
     * @param path
     *            the path.
     * @return <CODE>true</CODE> if we can read on the provided path and
     *         <CODE>false</CODE> otherwise.
     */
    private boolean canRead(final String path) {
        boolean canRead;
        final File file = new File(path);
        canRead = file.exists() && file.canRead();
        return canRead;
    }

    private String getAuthID(final String mech) throws ArgumentException {
        String value = null;
        for (final String s : saslOptionArg.getValues()) {
            if (s.startsWith(SASL_PROPERTY_AUTHID)) {
                value = parseSASLOptionValue(s);
                break;
            }
        }
        if (value == null && bindNameArg.isPresent()) {
            value = "dn: " + bindNameArg.getValue();
        }
        if (value == null && app.isInteractive()) {
            try {
                value =
                        app.readInput(LocalizableMessage.raw("Authentication ID:"), bindNameArg
                                .getDefaultValue() == null ? null : "dn: "
                                + bindNameArg.getDefaultValue());
            } catch (CLIException e) {
                throw new ArgumentException(LocalizableMessage
                        .raw("Unable to read authentication ID"), e);
            }
        }
        if (value == null) {
            final LocalizableMessage message = ERR_LDAPAUTH_SASL_AUTHID_REQUIRED.get(mech);
            throw new ArgumentException(message);
        }
        return value;
    }

    private String getAuthzID() throws ArgumentException {
        String value = null;
        for (final String s : saslOptionArg.getValues()) {
            if (s.startsWith(SASL_PROPERTY_AUTHZID)) {
                value = parseSASLOptionValue(s);
                break;
            }
        }
        return value;
    }

    private String getBindName() throws ArgumentException {
        String value = "";
        if (bindNameArg.isPresent()) {
            value = bindNameArg.getValue();
        } else if (app.isInteractive()) {
            try {
                value =
                        app.readInput(LocalizableMessage.raw("Bind name:"), bindNameArg
                                .getDefaultValue() == null ? value : bindNameArg.getDefaultValue());
            } catch (CLIException e) {
                throw new ArgumentException(LocalizableMessage.raw("Unable to read bind name"), e);
            }
        }

        return value;
    }

    public BindRequest getBindRequest() throws ArgumentException {
        if (bindRequest == null) {
            String mech = null;
            for (final String s : saslOptionArg.getValues()) {
                if (s.startsWith(SASL_PROPERTY_MECH)) {
                    mech = parseSASLOptionValue(s);
                    break;
                }
            }

            if (mech == null) {
                if (bindNameArg.isPresent() || bindPasswordFileArg.isPresent()
                        || bindPasswordArg.isPresent()) {
                    bindRequest = Requests.newSimpleBindRequest(getBindName(), getPassword());
                }
            } else if (mech.equals(DigestMD5SASLBindRequest.SASL_MECHANISM_NAME)) {
                bindRequest =
                        Requests.newDigestMD5SASLBindRequest(
                                getAuthID(DigestMD5SASLBindRequest.SASL_MECHANISM_NAME),
                                getPassword()).setAuthorizationID(getAuthzID())
                                .setRealm(getRealm());
            } else if (mech.equals(CRAMMD5SASLBindRequest.SASL_MECHANISM_NAME)) {
                bindRequest =
                        Requests.newCRAMMD5SASLBindRequest(
                                getAuthID(CRAMMD5SASLBindRequest.SASL_MECHANISM_NAME),
                                getPassword());
            } else if (mech.equals(GSSAPISASLBindRequest.SASL_MECHANISM_NAME)) {
                bindRequest =
                        Requests.newGSSAPISASLBindRequest(
                                getAuthID(GSSAPISASLBindRequest.SASL_MECHANISM_NAME), getPassword())
                                .setKDCAddress(getKDC()).setRealm(getRealm()).setAuthorizationID(
                                        getAuthzID());
            } else if (mech.equals(ExternalSASLBindRequest.SASL_MECHANISM_NAME)) {
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
            } else if (mech.equals(PlainSASLBindRequest.SASL_MECHANISM_NAME)) {
                bindRequest =
                        Requests.newPlainSASLBindRequest(
                                getAuthID(PlainSASLBindRequest.SASL_MECHANISM_NAME), getPassword())
                                .setAuthorizationID(getAuthzID());
            } else {
                throw new ArgumentException(ERR_LDAPAUTH_UNSUPPORTED_SASL_MECHANISM.get(mech));
            }

            if (reportAuthzIDArg.isPresent()) {
                bindRequest.addControl(AuthorizationIdentityRequestControl.newControl(false));
            }

            if (usePasswordPolicyControlArg.isPresent()) {
                bindRequest.addControl(PasswordPolicyRequestControl.newControl(false));
            }
        }
        return bindRequest;
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
        return connFactory.toString();
    }

    private String getKDC() throws ArgumentException {
        String value = null;
        for (final String s : saslOptionArg.getValues()) {
            if (s.startsWith(SASL_PROPERTY_KDC)) {
                value = parseSASLOptionValue(s);
                break;
            }
        }
        return value;
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
     */

    private X509KeyManager getKeyManager(String keyStoreFile) throws KeyStoreException,
            IOException, NoSuchAlgorithmException, CertificateException {
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

        final FileInputStream fos = new FileInputStream(keyStoreFile);
        final KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        keystore.load(fos, keyStorePIN);
        fos.close();

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
     * Get the password which has to be used for the command. If no password was
     * specified, return null.
     *
     * @return The password stored into the specified file on by the command
     *         line argument, or null it if not specified.
     */
    private char[] getPassword() throws ArgumentException {
        char[] value = "".toCharArray();

        if (bindPasswordArg.isPresent()) {
            value = bindPasswordArg.getValue().toCharArray();
        } else if (bindPasswordFileArg.isPresent()) {
            value = bindPasswordFileArg.getValue().toCharArray();
        }

        if (value.length == 0 && app.isInteractive()) {
            try {
                value = app.readPassword(LocalizableMessage.raw("Bind Password:"));
            } catch (CLIException e) {
                throw new ArgumentException(LocalizableMessage.raw("Unable to read password"), e);
            }
        }

        return value;
    }

    private String getRealm() throws ArgumentException {
        String value = null;
        for (final String s : saslOptionArg.getValues()) {
            if (s.startsWith(SASL_PROPERTY_REALM)) {
                value = parseSASLOptionValue(s);
                break;
            }
        }
        return value;
    }

    /**
     * Retrieves a <CODE>TrustManager</CODE> object that may be used for
     * interactions requiring access to a trust manager.
     *
     * @return A set of <CODE>TrustManager</CODE> objects that may be used for
     *         interactions requiring access to a trust manager.
     * @throws GeneralSecurityException
     *             If a problem occurs while interacting with the trust store.
     */
    private TrustManager getTrustManager() throws IOException, GeneralSecurityException {
        if (trustAllArg.isPresent()) {
            return TrustManagers.trustAll();
        }

        X509TrustManager tm = null;
        if (trustStorePathArg.isPresent() && trustStorePathArg.getValue().length() > 0) {
            tm =
                    TrustManagers.checkValidityDates(TrustManagers.checkHostName(hostNameArg
                            .getValue(), TrustManagers.checkUsingTrustStore(trustStorePathArg
                            .getValue(), getTrustStorePIN().toCharArray(), null)));
        } else if (getTrustStore() != null) {
            tm =
                    TrustManagers.checkValidityDates(TrustManagers.checkHostName(hostNameArg
                            .getValue(), TrustManagers.checkUsingTrustStore(getTrustStore(),
                            getTrustStorePIN().toCharArray(), null)));
        }

        if (app != null && !app.isQuiet()) {
            return new PromptingTrustManager(app, tm);
        }

        return null;
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
     * @return The PIN that should be used to access the trust store.
     */

    private String getTrustStorePIN() {
        String pwd;
        if (trustStorePasswordArg.isPresent()) {
            pwd = trustStorePasswordArg.getValue();
        } else if (trustStorePasswordFileArg.isPresent()) {
            pwd = trustStorePasswordFileArg.getValue();
        } else {
            pwd = System.getProperty("javax.net.ssl.trustStorePassword");
        }
        return pwd;
    }

    private String parseSASLOptionValue(final String option) throws ArgumentException {
        final int equalPos = option.indexOf('=');
        if (equalPos <= 0) {
            final LocalizableMessage message = ERR_LDAP_CONN_CANNOT_PARSE_SASL_OPTION.get(option);
            throw new ArgumentException(message);
        }

        return option.substring(equalPos + 1, option.length());
    }
}
