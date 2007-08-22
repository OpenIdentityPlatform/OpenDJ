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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.util.args;

import org.opends.messages.Message;
import static org.opends.messages.ToolMessages.*;
import org.opends.server.tools.LDAPConnection;
import org.opends.server.tools.LDAPConnectionOptions;
import org.opends.server.tools.SSLConnectionFactory;
import org.opends.server.tools.SSLConnectionException;
import org.opends.server.tools.LDAPConnectionException;
import static org.opends.server.tools.ToolConstants.*;
import static org.opends.server.util.ServerConstants.MAX_LINE_WIDTH;
import static org.opends.server.util.StaticUtils.wrapText;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.PrintStream;

/**
 * Creates an argument parser pre-populated with arguments for specifying
 * information for openning and LDAPConnection an LDAP connection.
 */
public class LDAPConnectionArgumentParser extends ArgumentParser {

  /** Argument indicating whether all SSL certs will be trusted. */
  protected BooleanArgument   trustAll;

  /** Argument indicating whether or not to use SSL. */
  protected BooleanArgument   useSSL;

  /** Argument indicating whether or not to use StartTLS. */
  protected BooleanArgument   useStartTLS;

  /** Argument indicating location of a bind password file. */
  protected FileBasedArgument bindPWFile;

  /** Argument indicating the location of the keystore password file. */
  protected FileBasedArgument keyStorePWFile;

  /** Argument indicating the location of the trust store password file. */
  protected FileBasedArgument trustStorePWFile;

  /** Argument indicating the port of the directory server. */
  protected IntegerArgument   port;

  /** Argument indicating the DN of the user with which to bind. */
  protected StringArgument    bindDN;

  /** Argument indicating the password of the user with which to bind. */
  protected StringArgument    bindPW;

  /** Argument indicating the nickname of the certificate to use. */
  protected StringArgument    certNickname;

  /** Argument indicating the hostname of the directory server. */
  protected StringArgument    host;

  /** Argument indicating the location of the keystore file. */
  protected StringArgument    keyStoreFile;

  /** Argument indicating the password fo the keystore. */
  protected StringArgument    keyStorePW;

  /** Argument indicating a SASL option. */
  protected StringArgument    saslOption;

  /** Argument indicating the location of the trust store file. */
  protected StringArgument    trustStoreFile;

  /** Argument indicating the password of the trust store. */
  protected StringArgument    trustStorePW;

  /**
   * {@inheritDoc}
   */
  public LDAPConnectionArgumentParser(String mainClassName,
                                      Message toolDescription,
                                      boolean longArgumentsCaseSensitive) {
    super(mainClassName, toolDescription, longArgumentsCaseSensitive);
    addLdapConnectionArguments();
  }

  /**
   * {@inheritDoc}
   */
  public LDAPConnectionArgumentParser(String mainClassName,
                                      Message toolDescription,
                                      boolean longArgumentsCaseSensitive,
                                      boolean allowsTrailingArguments,
                                      int minTrailingArguments,
                                      int maxTrailingArguments,
                                      String trailingArgsDisplayName) {
    super(mainClassName, toolDescription, longArgumentsCaseSensitive,
            allowsTrailingArguments, minTrailingArguments, maxTrailingArguments,
            trailingArgsDisplayName);
    addLdapConnectionArguments();
  }

  /**
   * Indicates whether or not the user has indicated that they would like
   * to perform a remote operation based on the arguments.
   *
   * @return true if the user wants to perform a remote operation;
   *         false otherwise
   */
  public boolean isLdapOperation() {
    // This may not be ideal in all cases.  We might want to assume no
    // host means 'localhost'.  However we would still need some way to
    // tell whether the user intends this invocation to be remote.
    return host.isPresent();
  }

  /**
   * Creates a new LDAPConnection and invokes a connect operation using
   * information provided in the parsed set of arguments that were provided
   * by the user.
   *
   * @param out stream to write messages
   * @param err stream to write messages
   * @return LDAPConnection created by this class from parsed arguments
   * @throws LDAPConnectionException if there was a problem connecting
   *         to the server indicated by the input arguments
   * @throws ArgumentException if there was a problem processing the input
   *         arguments
   */
  public LDAPConnection connect(PrintStream out, PrintStream err)
          throws LDAPConnectionException, ArgumentException
  {
    // If both a bind password and bind password file were provided, then return
    // an error.
    if (bindPW.isPresent() && bindPWFile.isPresent())
    {
      Message message = ERR_LDAP_CONN_MUTUALLY_EXCLUSIVE_ARGUMENTS.get(
              bindPW.getLongIdentifier(),
              bindPWFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      throw new ArgumentException(message);
    }


    // If both a key store password and key store password file were provided,
    // then return an error.
    if (keyStorePW.isPresent() && keyStorePWFile.isPresent())
    {
      Message message = ERR_LDAP_CONN_MUTUALLY_EXCLUSIVE_ARGUMENTS.get(
              keyStorePW.getLongIdentifier(),
              keyStorePWFile.getLongIdentifier());
      throw new ArgumentException(message);
    }


    // If both a trust store password and trust store password file were
    // provided, then return an error.
    if (trustStorePW.isPresent() && trustStorePWFile.isPresent())
    {
      Message message = ERR_LDAP_CONN_MUTUALLY_EXCLUSIVE_ARGUMENTS.get(
              trustStorePW.getLongIdentifier(),
              trustStorePWFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      throw new ArgumentException(message);
    }

    // Create the LDAP connection options object, which will be used to
    // customize the way that we connect to the server and specify a set of
    // basic defaults.
    LDAPConnectionOptions connectionOptions = new LDAPConnectionOptions();
    connectionOptions.setVersionNumber(3);


    // See if we should use SSL or StartTLS when establishing the connection.
    // If so, then make sure only one of them was specified.
    if (useSSL.isPresent())
    {
      if (useStartTLS.isPresent())
      {
        Message message = ERR_LDAP_CONN_MUTUALLY_EXCLUSIVE_ARGUMENTS.get(
                useSSL.getLongIdentifier(),
                useStartTLS.getLongIdentifier());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        throw new ArgumentException(message);
      }
      else
      {
        connectionOptions.setUseSSL(true);
      }
    }
    else if (useStartTLS.isPresent())
    {
      connectionOptions.setStartTLS(true);
    }


    // If we should blindly trust any certificate, then install the appropriate
    // SSL connection factory.
    if (useSSL.isPresent() || useStartTLS.isPresent())
    {
      try
      {
        String clientAlias;
        if (certNickname.isPresent())
        {
          clientAlias = certNickname.getValue();
        }
        else
        {
          clientAlias = null;
        }

        SSLConnectionFactory sslConnectionFactory = new SSLConnectionFactory();
        sslConnectionFactory.init(trustAll.isPresent(), keyStoreFile.getValue(),
                                  keyStorePW.getValue(), clientAlias,
                                  trustStoreFile.getValue(),
                                  trustStorePW.getValue());

        connectionOptions.setSSLConnectionFactory(sslConnectionFactory);
      }
      catch (SSLConnectionException sce)
      {
        Message message =
                ERR_LDAP_CONN_CANNOT_INITIALIZE_SSL.get(sce.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
      }
    }


    // If one or more SASL options were provided, then make sure that one of
    // them was "mech" and specified a valid SASL mechanism.
    if (saslOption.isPresent())
    {
      String             mechanism = null;
      LinkedList<String> options   = new LinkedList<String>();

      for (String s : saslOption.getValues())
      {
        int equalPos = s.indexOf('=');
        if (equalPos <= 0)
        {
          Message message = ERR_LDAP_CONN_CANNOT_PARSE_SASL_OPTION.get(s);
          err.println(wrapText(message, MAX_LINE_WIDTH));
          throw new ArgumentException(message);
        }
        else
        {
          String name  = s.substring(0, equalPos);

          if (name.equalsIgnoreCase("mech"))
          {
            mechanism = s;
          }
          else
          {
            options.add(s);
          }
        }
      }

      if (mechanism == null)
      {
        Message message = ERR_LDAP_CONN_NO_SASL_MECHANISM.get();
        err.println(wrapText(message, MAX_LINE_WIDTH));
        throw new ArgumentException(message);
      }

      connectionOptions.setSASLMechanism(mechanism);

      for (String option : options)
      {
        connectionOptions.addSASLProperty(option);
      }
    }


    // Attempt to connect and authenticate to the Directory Server.
    AtomicInteger nextMessageID = new AtomicInteger(1);

    LDAPConnection connection = new LDAPConnection(
            host.getValue(), port.getIntValue(),
            connectionOptions, out, err);

    connection.connectToHost(bindDN.getValue(), bindPW.getValue(),
                             nextMessageID);

    return connection;
  }

  private void addLdapConnectionArguments() {

    try
    {
      host = new StringArgument(
              "host", OPTION_SHORT_HOST,
              OPTION_LONG_HOST, false, false, true,
              OPTION_VALUE_HOST, "127.0.0.1", null,
              INFO_LDAP_CONN_DESCRIPTION_HOST.get());
      addArgument(host);

      port = new IntegerArgument(
              "port", OPTION_SHORT_PORT,
              OPTION_LONG_PORT, false, false, true,
              OPTION_VALUE_PORT, 389, null, true, 1,
              true, 65535, INFO_LDAP_CONN_DESCRIPTION_PORT.get());
      addArgument(port);

      useSSL = new BooleanArgument(
              "usessl", OPTION_SHORT_USE_SSL,
              OPTION_LONG_USE_SSL,
              INFO_LDAP_CONN_DESCRIPTION_USESSL.get());
      addArgument(useSSL);

      useStartTLS = new BooleanArgument(
              "usestarttls", OPTION_SHORT_START_TLS,
              OPTION_LONG_START_TLS,
              INFO_LDAP_CONN_DESCRIPTION_USESTARTTLS.get());
      addArgument(useStartTLS);

      bindDN = new StringArgument(
              "binddn", OPTION_SHORT_BINDDN,
              OPTION_LONG_BINDDN, false, false, true,
              OPTION_VALUE_BINDDN, null, null,
              INFO_LDAP_CONN_DESCRIPTION_BINDDN.get());
      addArgument(bindDN);

      bindPW = new StringArgument(
              "bindpw", OPTION_SHORT_BINDPWD,
              OPTION_LONG_BINDPWD, false, false,
              true,
              OPTION_VALUE_BINDPWD, null, null,
              INFO_LDAP_CONN_DESCRIPTION_BINDPW.get());
      addArgument(bindPW);

      bindPWFile = new FileBasedArgument(
              "bindpwfile",
              OPTION_SHORT_BINDPWD_FILE,
              OPTION_LONG_BINDPWD_FILE,
              false, false,
              OPTION_VALUE_BINDPWD_FILE,
              null, null,
              INFO_LDAP_CONN_DESCRIPTION_BINDPWFILE.get());
      addArgument(bindPWFile);

      saslOption = new StringArgument(
              "sasloption", OPTION_SHORT_SASLOPTION,
              OPTION_LONG_SASLOPTION, false,
              true, true,
              OPTION_VALUE_SASLOPTION, null, null,
              INFO_LDAP_CONN_DESCRIPTION_SASLOPTIONS.get());
      addArgument(saslOption);

      trustAll = new BooleanArgument(
              "trustall", 'X', "trustAll",
              INFO_LDAP_CONN_DESCRIPTION_TRUST_ALL.get());
      addArgument(trustAll);

      keyStoreFile = new StringArgument(
              "keystorefile",
              OPTION_SHORT_KEYSTOREPATH,
              OPTION_LONG_KEYSTOREPATH,
              false, false, true,
              OPTION_VALUE_KEYSTOREPATH,
              null, null,
              INFO_LDAP_CONN_DESCRIPTION_KSFILE.get());
      addArgument(keyStoreFile);

      keyStorePW = new StringArgument(
              "keystorepw", OPTION_SHORT_KEYSTORE_PWD,
              OPTION_LONG_KEYSTORE_PWD,
              false, false, true,
              OPTION_VALUE_KEYSTORE_PWD,
              null, null,
              INFO_LDAP_CONN_DESCRIPTION_KSPW.get());
      addArgument(keyStorePW);

      keyStorePWFile = new FileBasedArgument(
              "keystorepwfile",
              OPTION_SHORT_KEYSTORE_PWD_FILE,
              OPTION_LONG_KEYSTORE_PWD_FILE,
              false, false,
              OPTION_VALUE_KEYSTORE_PWD_FILE,
              null, null,
              INFO_LDAP_CONN_DESCRIPTION_KSPWFILE.get());
      addArgument(keyStorePWFile);

      certNickname = new StringArgument(
              "certnickname", 'N', "certNickname",
              false, false, true, "{nickname}", null,
              null, INFO_DESCRIPTION_CERT_NICKNAME.get());
      addArgument(certNickname);

      trustStoreFile = new StringArgument(
              "truststorefile",
              OPTION_SHORT_TRUSTSTOREPATH,
              OPTION_LONG_TRUSTSTOREPATH,
              false, false, true,
              OPTION_VALUE_TRUSTSTOREPATH,
              null, null,
              INFO_LDAP_CONN_DESCRIPTION_TSFILE.get());
      addArgument(trustStoreFile);

      trustStorePW = new StringArgument(
              "truststorepw", 'T',
              OPTION_LONG_TRUSTSTORE_PWD,
              false, false,
              true, OPTION_VALUE_TRUSTSTORE_PWD, null,
              null, INFO_LDAP_CONN_DESCRIPTION_TSPW.get());
      addArgument(trustStorePW);

      trustStorePWFile = new FileBasedArgument(
              "truststorepwfile",
              OPTION_SHORT_TRUSTSTORE_PWD_FILE,
              OPTION_LONG_TRUSTSTORE_PWD_FILE,
              false, false,
              OPTION_VALUE_TRUSTSTORE_PWD_FILE, null, null,
              INFO_LDAP_CONN_DESCRIPTION_TSPWFILE.get());
      addArgument(trustStorePWFile);

    }
    catch (ArgumentException ae)
    {
      // Should never happen
    }

  }

}
