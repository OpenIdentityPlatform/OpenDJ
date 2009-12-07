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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package com.sun.opends.sdk.tools;



import static com.sun.opends.sdk.messages.Messages.*;
import static com.sun.opends.sdk.tools.ToolConstants.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.logging.Logger;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import org.opends.sdk.*;
import org.opends.sdk.ldap.LDAPConnectionFactory;
import org.opends.sdk.ldap.LDAPConnectionOptions;
import org.opends.sdk.requests.BindRequest;
import org.opends.sdk.requests.Requests;
import org.opends.sdk.sasl.*;

import com.sun.opends.sdk.util.LocalizedIllegalArgumentException;
import com.sun.opends.sdk.util.Message;
import com.sun.opends.sdk.util.SSLUtils;
import com.sun.opends.sdk.util.StaticUtils;



/**
 * A connection factory designed for use with command line tools.
 */
final class ArgumentParserConnectionFactory extends
    AbstractConnectionFactory<AsynchronousConnection> implements
    ConnectionFactory<AsynchronousConnection>
{
  /**
   * End Of Line.
   */
  static final String EOL = System.getProperty("line.separator");

  /**
   * The Logger.
   */
  static final Logger LOG = Logger
      .getLogger(ArgumentParserConnectionFactory.class.getName());

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
  private StringArgument bindDnArg = null;

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
   * Whether to request that the server return the authorization ID in
   * the bind response.
   */
  private final BooleanArgument reportAuthzID;

  /**
   * Whether to use the password policy control in the bind request.
   */
  private final BooleanArgument usePasswordPolicyControl;

  private int port = 389;

  private SSLContext sslContext;

  private ConnectionFactory<? extends AsynchronousConnection> connFactory;

  private BindRequest bindRequest = null;

  private final ConsoleApplication app;



  public ArgumentParserConnectionFactory(ArgumentParser argumentParser,
      ConsoleApplication app) throws ArgumentException
  {
    this(argumentParser, app, "cn=Directory Manager", 389, false);
  }



  public ArgumentParserConnectionFactory(ArgumentParser argumentParser,
      ConsoleApplication app, String defaultBindDN, int defaultPort,
      boolean alwaysSSL) throws ArgumentException
  {
    this.app = app;
    useSSLArg = new BooleanArgument("useSSL", OPTION_SHORT_USE_SSL,
        OPTION_LONG_USE_SSL, INFO_DESCRIPTION_USE_SSL.get());
    useSSLArg.setPropertyName(OPTION_LONG_USE_SSL);
    if (!alwaysSSL)
    {
      argumentParser.addLdapConnectionArgument(useSSLArg);
    }
    else
    {
      // simulate that the useSSL arg has been given in the CLI
      useSSLArg.setPresent(true);
    }

    useStartTLSArg = new BooleanArgument("startTLS",
        OPTION_SHORT_START_TLS, OPTION_LONG_START_TLS,
        INFO_DESCRIPTION_START_TLS.get());
    useStartTLSArg.setPropertyName(OPTION_LONG_START_TLS);
    if (!alwaysSSL)
    {
      argumentParser.addLdapConnectionArgument(useStartTLSArg);
    }

    String defaultHostName;
    try
    {
      defaultHostName = InetAddress.getLocalHost().getHostName();
    }
    catch (Exception e)
    {
      defaultHostName = "Unknown (" + e + ")";
    }
    hostNameArg = new StringArgument("host", OPTION_SHORT_HOST,
        OPTION_LONG_HOST, false, false, true, INFO_HOST_PLACEHOLDER
            .get(), defaultHostName, null, INFO_DESCRIPTION_HOST.get());
    hostNameArg.setPropertyName(OPTION_LONG_HOST);
    argumentParser.addLdapConnectionArgument(hostNameArg);

    Message portDescription = INFO_DESCRIPTION_PORT.get();
    if (alwaysSSL)
    {
      portDescription = INFO_DESCRIPTION_ADMIN_PORT.get();
    }

    portArg = new IntegerArgument("port", OPTION_SHORT_PORT,
        OPTION_LONG_PORT, false, false, true, INFO_PORT_PLACEHOLDER
            .get(), defaultPort, null, portDescription);
    portArg.setPropertyName(OPTION_LONG_PORT);
    argumentParser.addLdapConnectionArgument(portArg);

    bindDnArg = new StringArgument("bindDN", OPTION_SHORT_BINDDN,
        OPTION_LONG_BINDDN, false, false, true, INFO_BINDDN_PLACEHOLDER
            .get(), defaultBindDN, null, INFO_DESCRIPTION_BINDDN.get());
    bindDnArg.setPropertyName(OPTION_LONG_BINDDN);
    argumentParser.addLdapConnectionArgument(bindDnArg);

    bindPasswordArg = new StringArgument("bindPassword",
        OPTION_SHORT_BINDPWD, OPTION_LONG_BINDPWD, false, false, true,
        INFO_BINDPWD_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_BINDPASSWORD.get());
    bindPasswordArg.setPropertyName(OPTION_LONG_BINDPWD);
    argumentParser.addLdapConnectionArgument(bindPasswordArg);

    bindPasswordFileArg = new FileBasedArgument("bindPasswordFile",
        OPTION_SHORT_BINDPWD_FILE, OPTION_LONG_BINDPWD_FILE, false,
        false, INFO_BINDPWD_FILE_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_BINDPASSWORDFILE.get());
    bindPasswordFileArg.setPropertyName(OPTION_LONG_BINDPWD_FILE);
    argumentParser.addLdapConnectionArgument(bindPasswordFileArg);

    saslOptionArg = new StringArgument("sasloption",
        OPTION_SHORT_SASLOPTION, OPTION_LONG_SASLOPTION, false, true,
        true, INFO_SASL_OPTION_PLACEHOLDER.get(), null, null,
        INFO_LDAP_CONN_DESCRIPTION_SASLOPTIONS.get());
    saslOptionArg.setPropertyName(OPTION_LONG_SASLOPTION);
    argumentParser.addLdapConnectionArgument(saslOptionArg);

    trustAllArg = new BooleanArgument("trustAll",
        OPTION_SHORT_TRUSTALL, OPTION_LONG_TRUSTALL,
        INFO_DESCRIPTION_TRUSTALL.get());
    trustAllArg.setPropertyName(OPTION_LONG_TRUSTALL);
    argumentParser.addLdapConnectionArgument(trustAllArg);

    trustStorePathArg = new StringArgument("trustStorePath",
        OPTION_SHORT_TRUSTSTOREPATH, OPTION_LONG_TRUSTSTOREPATH, false,
        false, true, INFO_TRUSTSTOREPATH_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_TRUSTSTOREPATH.get());
    trustStorePathArg.setPropertyName(OPTION_LONG_TRUSTSTOREPATH);
    argumentParser.addLdapConnectionArgument(trustStorePathArg);

    trustStorePasswordArg = new StringArgument("trustStorePassword",
        OPTION_SHORT_TRUSTSTORE_PWD, OPTION_LONG_TRUSTSTORE_PWD, false,
        false, true, INFO_TRUSTSTORE_PWD_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_TRUSTSTOREPASSWORD.get());
    trustStorePasswordArg.setPropertyName(OPTION_LONG_TRUSTSTORE_PWD);
    argumentParser.addLdapConnectionArgument(trustStorePasswordArg);

    trustStorePasswordFileArg = new FileBasedArgument(
        "trustStorePasswordFile", OPTION_SHORT_TRUSTSTORE_PWD_FILE,
        OPTION_LONG_TRUSTSTORE_PWD_FILE, false, false,
        INFO_TRUSTSTORE_PWD_FILE_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_TRUSTSTOREPASSWORD_FILE.get());
    trustStorePasswordFileArg
        .setPropertyName(OPTION_LONG_TRUSTSTORE_PWD_FILE);
    argumentParser.addLdapConnectionArgument(trustStorePasswordFileArg);

    keyStorePathArg = new StringArgument("keyStorePath",
        OPTION_SHORT_KEYSTOREPATH, OPTION_LONG_KEYSTOREPATH, false,
        false, true, INFO_KEYSTOREPATH_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_KEYSTOREPATH.get());
    keyStorePathArg.setPropertyName(OPTION_LONG_KEYSTOREPATH);
    argumentParser.addLdapConnectionArgument(keyStorePathArg);

    keyStorePasswordArg = new StringArgument("keyStorePassword",
        OPTION_SHORT_KEYSTORE_PWD, OPTION_LONG_KEYSTORE_PWD, false,
        false, true, INFO_KEYSTORE_PWD_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_KEYSTOREPASSWORD.get());
    keyStorePasswordArg.setPropertyName(OPTION_LONG_KEYSTORE_PWD);
    argumentParser.addLdapConnectionArgument(keyStorePasswordArg);

    keyStorePasswordFileArg = new FileBasedArgument(
        "keystorePasswordFile", OPTION_SHORT_KEYSTORE_PWD_FILE,
        OPTION_LONG_KEYSTORE_PWD_FILE, false, false,
        INFO_KEYSTORE_PWD_FILE_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_KEYSTOREPASSWORD_FILE.get());
    keyStorePasswordFileArg
        .setPropertyName(OPTION_LONG_KEYSTORE_PWD_FILE);
    argumentParser.addLdapConnectionArgument(keyStorePasswordFileArg);

    certNicknameArg = new StringArgument("certNickname",
        OPTION_SHORT_CERT_NICKNAME, OPTION_LONG_CERT_NICKNAME, false,
        false, true, INFO_NICKNAME_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_CERT_NICKNAME.get());
    certNicknameArg.setPropertyName(OPTION_LONG_CERT_NICKNAME);
    argumentParser.addLdapConnectionArgument(certNicknameArg);

    reportAuthzID = new BooleanArgument("reportauthzid", 'E',
        OPTION_LONG_REPORT_AUTHZ_ID, INFO_DESCRIPTION_REPORT_AUTHZID
            .get());
    reportAuthzID.setPropertyName(OPTION_LONG_REPORT_AUTHZ_ID);
    argumentParser.addArgument(reportAuthzID);

    usePasswordPolicyControl = new BooleanArgument(
        "usepwpolicycontrol", null, OPTION_LONG_USE_PW_POLICY_CTL,
        INFO_DESCRIPTION_USE_PWP_CONTROL.get());
    usePasswordPolicyControl
        .setPropertyName(OPTION_LONG_USE_PW_POLICY_CTL);
    argumentParser.addArgument(usePasswordPolicyControl);
  }



  /**
   * {@inheritDoc}
   */
  public <P> ConnectionFuture<? extends AsynchronousConnection> getAsynchronousConnection(
      ConnectionResultHandler<? super AsynchronousConnection, P> handler,
      P p)
  {
    return connFactory.getAsynchronousConnection(handler, p);
  }



  public void validate() throws ArgumentException
  {
    port = portArg.getIntValue();

    // Couldn't have at the same time bindPassword and bindPasswordFile
    if (bindPasswordArg.isPresent() && bindPasswordFileArg.isPresent())
    {
      Message message = ERR_TOOL_CONFLICTING_ARGS
          .get(bindPasswordArg.getLongIdentifier(), bindPasswordFileArg
              .getLongIdentifier());
      throw new ArgumentException(message);
    }

    // Couldn't have at the same time trustAll and
    // trustStore related arg
    if (trustAllArg.isPresent() && trustStorePathArg.isPresent())
    {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(trustAllArg
          .getLongIdentifier(), trustStorePathArg.getLongIdentifier());
      throw new ArgumentException(message);
    }
    if (trustAllArg.isPresent() && trustStorePasswordArg.isPresent())
    {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(trustAllArg
          .getLongIdentifier(), trustStorePasswordArg
          .getLongIdentifier());
      throw new ArgumentException(message);
    }
    if (trustAllArg.isPresent()
        && trustStorePasswordFileArg.isPresent())
    {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(trustAllArg
          .getLongIdentifier(), trustStorePasswordFileArg
          .getLongIdentifier());
      throw new ArgumentException(message);
    }

    // Couldn't have at the same time trustStorePasswordArg and
    // trustStorePasswordFileArg
    if (trustStorePasswordArg.isPresent()
        && trustStorePasswordFileArg.isPresent())
    {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
          trustStorePasswordArg.getLongIdentifier(),
          trustStorePasswordFileArg.getLongIdentifier());
      throw new ArgumentException(message);
    }

    if (trustStorePathArg.isPresent())
    {
      // Check that the path exists and is readable
      String value = trustStorePathArg.getValue();
      if (!canRead(trustStorePathArg.getValue()))
      {
        Message message = ERR_CANNOT_READ_TRUSTSTORE.get(value);
        throw new ArgumentException(message);
      }
    }

    if (keyStorePathArg.isPresent())
    {
      // Check that the path exists and is readable
      String value = keyStorePathArg.getValue();
      if (!canRead(trustStorePathArg.getValue()))
      {
        Message message = ERR_CANNOT_READ_KEYSTORE.get(value);
        throw new ArgumentException(message);
      }
    }

    // Couldn't have at the same time startTLSArg and
    // useSSLArg
    if (useStartTLSArg.isPresent() && useSSLArg.isPresent())
    {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(useStartTLSArg
          .getLongIdentifier(), useSSLArg.getLongIdentifier());
      throw new ArgumentException(message);
    }

    try
    {
      if (useSSLArg.isPresent() || useStartTLSArg.isPresent())
      {
        String clientAlias;
        if (certNicknameArg.isPresent())
        {
          clientAlias = certNicknameArg.getValue();
        }
        else
        {
          clientAlias = null;
        }

        if (sslContext == null)
        {
          TrustManager trustManager = getTrustManager();

          KeyManager keyManager = null;
          X509KeyManager akm = getKeyManager(keyStorePathArg.getValue());

          if (keyManager != null && clientAlias != null)
          {
            keyManager = new SelectableCertificateKeyManager(akm,
                clientAlias);
          }
          sslContext = SSLUtils.getSSLContext(trustManager, keyManager);
        }
      }
    }
    catch (Exception e)
    {
      throw new ArgumentException(ERR_LDAP_CONN_CANNOT_INITIALIZE_SSL
          .get(e.toString()), e);
    }

    if (sslContext != null)
    {
      LDAPConnectionOptions options = LDAPConnectionOptions
          .defaultOptions().setSSLContext(sslContext).setUseStartTLS(
              useStartTLSArg.isPresent());
      connFactory = new LDAPConnectionFactory(hostNameArg.getValue(),
          port, options);
    }
    else
    {
      connFactory = new LDAPConnectionFactory(hostNameArg.getValue(),
          port);
    }

    try
    {
      bindRequest = getBindRequest();
    }
    catch (CLIException e)
    {
      throw new ArgumentException(Message.raw("Error reading input: "
          + e.toString()));
    }
    if (bindRequest != null)
    {
      connFactory = new AuthenticatedConnectionFactory(connFactory,
          bindRequest).setRebindAllowed(true);
    }
  }



  private BindRequest getBindRequest() throws CLIException,
      ArgumentException
  {
    String mech = null;
    for (String s : saslOptionArg.getValues())
    {
      if (s.startsWith(SASL_PROPERTY_MECH))
      {
        mech = parseSASLOptionValue(s);
        break;
      }
    }

    if (mech == null)
    {
      if (bindDnArg.isPresent() || bindPasswordFileArg.isPresent()
          || bindPasswordArg.isPresent())
      {
        return Requests.newSimpleBindRequest(getBindDN(), getPassword());
      }
      return null;
    }

    if (mech.equals(DigestMD5SASLBindRequest.SASL_MECHANISM_DIGEST_MD5))
    {
      return new DigestMD5SASLBindRequest(
          getAuthID(DigestMD5SASLBindRequest.SASL_MECHANISM_DIGEST_MD5),
          getAuthzID(), getPassword(), getRealm());
    }
    if (mech.equals(CRAMMD5SASLBindRequest.SASL_MECHANISM_CRAM_MD5))
    {
      return new CRAMMD5SASLBindRequest(
          getAuthID(CRAMMD5SASLBindRequest.SASL_MECHANISM_CRAM_MD5),
          getPassword());
    }
    if (mech.equals(GSSAPISASLBindRequest.SASL_MECHANISM_GSSAPI))
    {
      try
      {
        Subject subject = GSSAPISASLBindRequest.Kerberos5Login(
            getAuthID(GSSAPISASLBindRequest.SASL_MECHANISM_GSSAPI),
            getPassword(), getRealm(), getKDC());
        return new GSSAPISASLBindRequest(subject, getAuthzID());
      }
      catch (LoginException e)
      {
        Message message = ERR_LDAPAUTH_GSSAPI_LOCAL_AUTHENTICATION_FAILED
            .get(StaticUtils.getExceptionMessage(e));
        throw new ArgumentException(message, e);
      }
    }
    if (mech.equals(ExternalSASLBindRequest.SASL_MECHANISM_EXTERNAL))
    {
      if (sslContext == null)
      {
        Message message = ERR_TOOL_SASLEXTERNAL_NEEDS_SSL_OR_TLS.get();
        throw new ArgumentException(message);
      }
      if (!keyStorePathArg.isPresent() && getKeyStore() == null)
      {
        Message message = ERR_TOOL_SASLEXTERNAL_NEEDS_KEYSTORE.get();
        throw new ArgumentException(message);
      }
      return new ExternalSASLBindRequest(getAuthzID());
    }
    if (mech.equals(PlainSASLBindRequest.SASL_MECHANISM_PLAIN))
    {
      return new PlainSASLBindRequest(
          getAuthID(PlainSASLBindRequest.SASL_MECHANISM_PLAIN),
          getAuthzID(), getPassword());
    }

    throw new ArgumentException(ERR_LDAPAUTH_UNSUPPORTED_SASL_MECHANISM
        .get(mech));
  }



  private DN getBindDN() throws CLIException, ArgumentException
  {
    String value = "";
    if (bindDnArg.isPresent())
    {
      value = bindDnArg.getValue();
    }
    else if (app.isInteractive())
    {
      value = app.readInput(Message.raw("Bind DN:"), bindDnArg
          .getDefaultValue() == null ? value : bindDnArg
          .getDefaultValue());
    }

    try
    {
      return DN.valueOf(value);
    }
    catch (LocalizedIllegalArgumentException e)
    {
      throw new ArgumentException(e.getMessageObject());
    }
  }



  private String getAuthID(String mech) throws CLIException,
      ArgumentException
  {
    String value = null;
    for (String s : saslOptionArg.getValues())
    {
      if (s.startsWith(SASL_PROPERTY_AUTHID))
      {
        value = parseSASLOptionValue(s);
        break;
      }
    }
    if (value == null && bindDnArg.isPresent())
    {
      value = "dn: " + bindDnArg.getValue();
    }
    if (value == null && app.isInteractive())
    {
      value = app.readInput(Message.raw("Authentication ID:"),
          bindDnArg.getDefaultValue() == null ? null : "dn: "
              + bindDnArg.getDefaultValue());
    }
    if (value == null)
    {
      Message message = ERR_LDAPAUTH_SASL_AUTHID_REQUIRED.get(mech);
      throw new ArgumentException(message);
    }
    return value;
  }



  private String getAuthzID() throws CLIException, ArgumentException
  {
    String value = null;
    for (String s : saslOptionArg.getValues())
    {
      if (s.startsWith(SASL_PROPERTY_AUTHZID))
      {
        value = parseSASLOptionValue(s);
        break;
      }
    }
    return value;
  }



  /**
   * Get the password which has to be used for the command. If no
   * password was specified, return null.
   *
   * @return The password stored into the specified file on by the
   *         command line argument, or null it if not specified.
   */
  private ByteString getPassword() throws CLIException
  {
    String value = "";
    if (bindPasswordArg.isPresent())
    {
      value = bindPasswordArg.getValue();
    }
    else if (bindPasswordFileArg.isPresent())
    {
      value = bindPasswordFileArg.getValue();
    }
    if (value.length() == 0 && app.isInteractive())
    {
      value = app.readLineOfInput(Message.raw("Bind Password:"));
    }

    return ByteString.valueOf(value);
  }



  private String getRealm() throws ArgumentException, CLIException
  {
    String value = null;
    for (String s : saslOptionArg.getValues())
    {
      if (s.startsWith(SASL_PROPERTY_REALM))
      {
        value = parseSASLOptionValue(s);
        break;
      }
    }
    return value;
  }



  private String getKDC() throws ArgumentException, CLIException
  {
    String value = null;
    for (String s : saslOptionArg.getValues())
    {
      if (s.startsWith(SASL_PROPERTY_KDC))
      {
        value = parseSASLOptionValue(s);
        break;
      }
    }
    return value;
  }



  /**
   * Returns <CODE>true</CODE> if we can read on the provided path and
   * <CODE>false</CODE> otherwise.
   *
   * @param path
   *          the path.
   * @return <CODE>true</CODE> if we can read on the provided path and
   *         <CODE>false</CODE> otherwise.
   */
  private boolean canRead(String path)
  {
    boolean canRead;
    File file = new File(path);
    canRead = file.exists() && file.canRead();
    return canRead;
  }



  /**
   * Retrieves a <CODE>TrustManager</CODE> object that may be used for
   * interactions requiring access to a trust manager.
   *
   * @return A set of <CODE>TrustManager</CODE> objects that may be used
   *         for interactions requiring access to a trust manager.
   * @throws KeyStoreException
   *           If a problem occurs while interacting with the trust
   *           store.
   */
  private TrustManager getTrustManager() throws KeyStoreException,
      IOException, NoSuchAlgorithmException, CertificateException
  {
    if (trustAllArg.isPresent())
    {
      return new TrustAllTrustManager();
    }

    TrustStoreTrustManager tm = null;
    if (trustStorePathArg.isPresent()
        && trustStorePathArg.getValue().length() > 0)
    {
      tm = new TrustStoreTrustManager(trustStorePathArg.getValue(),
          getTrustStorePIN(), hostNameArg.getValue(), true);
    }
    else if (getTrustStore() != null)
    {
      tm = new TrustStoreTrustManager(getTrustStore(),
          getTrustStorePIN(), hostNameArg.getValue(), true);
    }

    if (app != null && !app.isQuiet())
    {
      return new PromptingTrustManager(app, tm);
    }
    return null;
  }



  /**
   * Retrieves a <CODE>KeyManager</CODE> object that may be used for
   * interactions requiring access to a key manager.
   *
   * @param keyStoreFile
   *          The path to the file containing the key store data.
   * @return A set of <CODE>KeyManager</CODE> objects that may be used
   *         for interactions requiring access to a key manager.
   * @throws java.security.KeyStoreException
   *           If a problem occurs while interacting with the key store.
   */

  private X509KeyManager getKeyManager(String keyStoreFile)
      throws KeyStoreException, IOException, NoSuchAlgorithmException,
      CertificateException
  {
    if (keyStoreFile == null)
    {
      // Lookup the file name through the JDK property.
      keyStoreFile = getKeyStore();
    }

    if (keyStoreFile == null)
    {
      return null;
    }

    String keyStorePass = getKeyStorePIN();
    char[] keyStorePIN = null;
    if (keyStorePass != null)
    {
      keyStorePIN = keyStorePass.toCharArray();
    }

    FileInputStream fos = new FileInputStream(keyStoreFile);
    KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
    keystore.load(fos, keyStorePIN);
    fos.close();

    return new ApplicationKeyManager(keystore, keyStorePIN);
  }



  /**
   * Read the KeyStore PIN from the JSSE system property.
   *
   * @return The PIN that should be used to access the key store.
   */

  private String getKeyStorePIN()
  {
    String pwd;
    if (keyStorePasswordArg.isPresent())
    {
      pwd = keyStorePasswordArg.getValue();
    }
    else if (keyStorePasswordFileArg.isPresent())
    {
      pwd = keyStorePasswordFileArg.getValue();
    }
    else
    {
      pwd = System.getProperty("javax.net.ssl.keyStorePassword");
    }
    return pwd;
  }



  /**
   * Read the TrustStore PIN from the JSSE system property.
   *
   * @return The PIN that should be used to access the trust store.
   */

  private String getTrustStorePIN()
  {
    String pwd;
    if (trustStorePasswordArg.isPresent())
    {
      pwd = trustStorePasswordArg.getValue();
    }
    else if (trustStorePasswordFileArg.isPresent())
    {
      pwd = trustStorePasswordFileArg.getValue();
    }
    else
    {
      pwd = System.getProperty("javax.net.ssl.trustStorePassword");
    }
    return pwd;
  }



  /**
   * Read the KeyStore from the JSSE system property.
   *
   * @return The path to the key store file.
   */

  private String getKeyStore()
  {
    return System.getProperty("javax.net.ssl.keyStore");
  }



  /**
   * Read the TrustStore from the JSSE system property.
   *
   * @return The path to the trust store file.
   */

  private String getTrustStore()
  {
    return System.getProperty("javax.net.ssl.trustStore");
  }



  private String parseSASLOptionValue(String option)
      throws ArgumentException
  {
    int equalPos = option.indexOf('=');
    if (equalPos <= 0)
    {
      Message message = ERR_LDAP_CONN_CANNOT_PARSE_SASL_OPTION
          .get(option);
      throw new ArgumentException(message);
    }

    return option.substring(equalPos + 1, option.length());
  }
}
