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
 *      Copyright 2007-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS
 */
package org.opends.server.admin.client.cli;

import static com.forgerock.opendj.cli.CliMessages.INFO_DESCRIPTION_ADMIN_PORT;
import static com.forgerock.opendj.cli.Utils.LINE_SEPARATOR;
import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.ToolMessages.*;
import static com.forgerock.opendj.cli.ReturnCode.SUCCESS;
import static com.forgerock.opendj.cli.ReturnCode.CONFLICTING_ARGS;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;

import static org.opends.server.util.ServerConstants.MAX_LINE_WIDTH;
import static org.opends.server.util.StaticUtils.*;
import static com.forgerock.opendj.cli.ArgumentConstants.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.LinkedHashSet;

import org.forgerock.i18n.slf4j.LocalizedLogger;

import javax.net.ssl.KeyManager;

import org.opends.admin.ads.util.ApplicationKeyManager;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.quicksetup.Constants;
import org.opends.server.admin.AdministrationConnector;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.server.AdministrationConnectorCfg;
import org.opends.server.admin.std.server.FileBasedTrustManagerProviderCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.std.server.TrustManagerProviderCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.util.PasswordReader;
import org.opends.server.util.SelectableCertificateKeyManager;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.CommonArguments;
import com.forgerock.opendj.cli.FileBasedArgument;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.StringArgument;

/**
 * This is a commodity class that can be used to check the arguments required
 * to establish a secure connection in the command line.  It can be used
 * to generate an ApplicationTrustManager object based on the options provided
 * by the user in the command line.
 *
 */
public final class SecureConnectionCliArgs
{
  /**
   * The 'hostName' global argument.
   */
  public StringArgument hostNameArg = null;

  /**
   * The 'port' global argument.
   */
  public IntegerArgument portArg = null;

  /**
   * The 'bindDN' global argument.
   */
  public StringArgument bindDnArg = null;

  /**
   * The 'adminUID' global argument.
   */
  public StringArgument adminUidArg = null;

  /**
   * The 'bindPasswordFile' global argument.
   */
  public FileBasedArgument bindPasswordFileArg = null;

  /**
   * The 'bindPassword' global argument.
   */
  public StringArgument bindPasswordArg = null;

  /**
   * The 'trustAllArg' global argument.
   */
  public BooleanArgument trustAllArg = null;

  /**
   * The 'trustStore' global argument.
   */
  public StringArgument trustStorePathArg = null;

  /**
   * The 'trustStorePassword' global argument.
   */
  public StringArgument trustStorePasswordArg = null;

  /**
   * The 'trustStorePasswordFile' global argument.
   */
  public FileBasedArgument trustStorePasswordFileArg = null;

  /**
   * The 'keyStore' global argument.
   */
  public StringArgument keyStorePathArg = null;

  /**
   * The 'keyStorePassword' global argument.
   */
  public StringArgument keyStorePasswordArg = null;

  /**
   * The 'keyStorePasswordFile' global argument.
   */
  public FileBasedArgument keyStorePasswordFileArg = null;

  /**
   * The 'certNicknameArg' global argument.
   */
  public StringArgument certNicknameArg = null;

  /**
   * The 'useSSLArg' global argument.
   */
  public BooleanArgument useSSLArg = null;

  /**
   * The 'useStartTLSArg' global argument.
   */
  public BooleanArgument useStartTLSArg = null;

  /**
   * Argument indicating a SASL option.
   */
  public StringArgument  saslOptionArg = null;

  /**
   * Argument to specify the connection timeout.
   */
  public IntegerArgument connectTimeoutArg = null;

  /**
   * Private container for global arguments.
   */
  private LinkedHashSet<Argument> argList = null;

  /** The trust manager. */
  private ApplicationTrustManager trustManager;

  private boolean configurationInitialized = false;
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Defines if the CLI always use the SSL connection type. */
  private boolean alwaysSSL = false;

  /**
   * Creates a new instance of secure arguments.
   *
   * @param alwaysSSL If true, always use the SSL connection type. In this case,
   * the arguments useSSL and startTLS are not present.
   */
  public SecureConnectionCliArgs(boolean alwaysSSL)
  {
    if (alwaysSSL) {
      this.alwaysSSL = true;
    }
  }

  /**
   * Indicates whether or not any of the arguments are present.
   *
   * @return boolean where true indicates that at least one of the
   *         arguments is present
   */
  public boolean argumentsPresent() {
    boolean present = false;
    if (argList != null) {
      for (Argument arg : argList) {
        if (arg.isPresent()) {
          present = true;
          break;
        }
      }
    }
    return present;
  }

  /**
   * Get the admin UID which has to be used for the command.
   *
   * @return The admin UID specified by the command line argument, or the
   *         default value, if not specified.
   */
  public String getAdministratorUID()
  {
    if (adminUidArg.isPresent())
    {
      return adminUidArg.getValue();
    }
    else
    {
      return adminUidArg.getDefaultValue();
    }
  }

  /**
   * Tells whether this parser uses the Administrator UID (instead of the
   * bind DN) or not.
   * @return <CODE>true</CODE> if this parser uses the Administrator UID and
   * <CODE>false</CODE> otherwise.
   */
  public boolean useAdminUID()
  {
    return !adminUidArg.isHidden();
  }

  /**
   * Get the bindDN which has to be used for the command.
   *
   * @return The bindDN specified by the command line argument, or the
   *         default value, if not specified.
   */
  public String getBindDN()
  {
    if (bindDnArg.isPresent())
    {
      return bindDnArg.getValue();
    }
    else
    {
      return bindDnArg.getDefaultValue();
    }
  }

  /**
   * Get the password which has to be used for the command.
   *
   * @param dn
   *          The user DN for which to password could be asked.
   * @param out
   *          The input stream to used if we have to prompt to the
   *          user.
   * @param err
   *          The error stream to used if we have to prompt to the
   *          user.
   * @param clearArg
   *          The password StringArgument argument.
   * @param fileArg
   *          The password FileBased argument.
   * @return The password stored into the specified file on by the
   *         command line argument, or prompts it if not specified.
   */
  public String getBindPassword(String dn,
      OutputStream out, OutputStream err, StringArgument clearArg,
      FileBasedArgument fileArg)
  {
    if (clearArg.isPresent())
    {
      String bindPasswordValue = clearArg.getValue();
      if(bindPasswordValue != null && "-".equals(bindPasswordValue))
      {
        // read the password from the stdin.
        try
        {
          out.write(INFO_LDAPAUTH_PASSWORD_PROMPT.get(dn).toString().getBytes());
          out.flush();
          char[] pwChars = PasswordReader.readPassword();
          bindPasswordValue = new String(pwChars);
        } catch(Exception ex)
        {
          logger.traceException(ex);
          try
          {
            err.write(wrapText(ex.getMessage(), MAX_LINE_WIDTH).getBytes());
            err.write(LINE_SEPARATOR.getBytes());
          }
          catch (IOException e)
          {
          }
          return null;
        }
      }
      return bindPasswordValue;
    }
    else
      if (fileArg.isPresent())
      {
        return fileArg.getValue();
      }
      else
      {
        // read the password from the stdin.
        try
        {
          out.write(
              INFO_LDAPAUTH_PASSWORD_PROMPT.get(dn).toString().getBytes());
          out.flush();
          char[] pwChars = PasswordReader.readPassword();
          return new String(pwChars);
        }
        catch (Exception ex)
        {
          logger.traceException(ex);
          try
          {
            err.write(wrapText(ex.getMessage(), MAX_LINE_WIDTH).getBytes());
            err.write(LINE_SEPARATOR.getBytes());
          }
          catch (IOException e)
          {
          }
          return null;
        }
      }

  }

  /**
   * Get the password which has to be used for the command.
   *
   * @param dn
   *          The user DN for which to password could be asked.
   * @param out
   *          The input stream to used if we have to prompt to the
   *          user.
   * @param err
   *          The error stream to used if we have to prompt to the
   *          user.
   * @return The password stored into the specified file on by the
   *         command line argument, or prompts it if not specified.
   */
  public String getBindPassword(String dn, OutputStream out, OutputStream err)
  {
    return getBindPassword(dn, out, err, bindPasswordArg, bindPasswordFileArg);
  }

  /**
   * Get the password which has to be used for the command without prompting
   * the user.  If no password was specified, return null.
   *
   * @param clearArg
   *          The password StringArgument argument.
   * @param fileArg
   *          The password FileBased argument.
   * @return The password stored into the specified file on by the
   *         command line argument, or null it if not specified.
   */
  public String getBindPassword(StringArgument clearArg,
      FileBasedArgument fileArg)
  {
    String pwd;
    if (clearArg.isPresent())
    {
      pwd = clearArg.getValue();
    }
    else
      if (fileArg.isPresent())
      {
        pwd = fileArg.getValue();
      }
      else
      {
        pwd = null;
      }
    return pwd;
  }

  /**
   * Get the password which has to be used for the command without prompting
   * the user.  If no password was specified, return null.
   *
   * @return The password stored into the specified file on by the
   *         command line argument, or null it if not specified.
   */
  public String getBindPassword()
  {
    return getBindPassword(bindPasswordArg, bindPasswordFileArg);
  }

  /**
   * Initialize Global option.
   *
   * @throws ArgumentException
   *           If there is a problem with any of the parameters used
   *           to create this argument.
   * @return a ArrayList with the options created.
   */
  public LinkedHashSet<Argument> createGlobalArguments()
  throws ArgumentException
  {
    argList = new LinkedHashSet<Argument>();

    useSSLArg = CommonArguments.getUseSSL();
    if (!alwaysSSL) {
      argList.add(useSSLArg);
    } else {
      // simulate that the useSSL arg has been given in the CLI
      useSSLArg.setPresent(true);
    }

    useStartTLSArg = CommonArguments.getStartTLS();
    if (!alwaysSSL) {
      argList.add(useStartTLSArg);
    }

    String defaultHostName;
    try {
      defaultHostName = InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      defaultHostName="Unknown (" + e + ")";
    }
    hostNameArg = CommonArguments.getHostName(defaultHostName);
    argList.add(hostNameArg);

    portArg =
        CommonArguments.getPort(
            AdministrationConnector.DEFAULT_ADMINISTRATION_CONNECTOR_PORT,
            alwaysSSL ? INFO_DESCRIPTION_ADMIN_PORT.get()
                : INFO_DESCRIPTION_PORT.get());
    argList.add(portArg);

    bindDnArg = CommonArguments.getBindDN("cn=Directory Manager");
    argList.add(bindDnArg);

    // It is up to the classes that required admin UID to make this argument
    // visible and add it.
    adminUidArg = new StringArgument("adminUID", 'I',
        OPTION_LONG_ADMIN_UID, false, false, true,
        INFO_ADMINUID_PLACEHOLDER.get(),
        Constants.GLOBAL_ADMIN_UID, null,
        INFO_DESCRIPTION_ADMIN_UID.get());
    adminUidArg.setPropertyName(OPTION_LONG_ADMIN_UID);
    adminUidArg.setHidden(true);

    bindPasswordArg = CommonArguments.getBindPassword();
    argList.add(bindPasswordArg);

    bindPasswordFileArg = CommonArguments.getBindPasswordFile();
    argList.add(bindPasswordFileArg);

    saslOptionArg = CommonArguments.getSASL();
    argList.add(saslOptionArg);

    trustAllArg = CommonArguments.getTrustAll();
    argList.add(trustAllArg);

    trustStorePathArg = CommonArguments.getTrustStorePath();
    argList.add(trustStorePathArg);

    trustStorePasswordArg = CommonArguments.getTrustStorePassword();
    argList.add(trustStorePasswordArg);

    trustStorePasswordFileArg = CommonArguments.getTrustStorePasswordFile();
    argList.add(trustStorePasswordFileArg);

    keyStorePathArg = CommonArguments.getKeyStorePath();
    argList.add(keyStorePathArg);

    keyStorePasswordArg = CommonArguments.getKeyStorePassword();
    argList.add(keyStorePasswordArg);

    keyStorePasswordFileArg = CommonArguments.getKeyStorePasswordFile();
    argList.add(keyStorePasswordFileArg);

    certNicknameArg = CommonArguments.getCertNickName();
    argList.add(certNicknameArg);

    connectTimeoutArg =
        CommonArguments.getConnectTimeOut(ConnectionUtils
            .getDefaultLDAPTimeout());
    connectTimeoutArg.setHidden(false);
    argList.add(connectTimeoutArg);

    return argList;
  }

  /**
   * Get the host name which has to be used for the command.
   *
   * @return The host name specified by the command line argument, or
   *         the default value, if not specified.
   */
  public String getHostName()
  {
    if (hostNameArg.isPresent())
    {
      return hostNameArg.getValue();
    }
    return hostNameArg.getDefaultValue();
  }

  /**
   * Get the port which has to be used for the command.
   *
   * @return The port specified by the command line argument, or the default
   *         value, if not specified.
   */
  public String getPort()
  {
    if (portArg.isPresent())
    {
      return portArg.getValue();
    }
    return portArg.getDefaultValue();
  }

  /**
   * Indication if provided global options are validate.
   *
   * @param buf the LocalizableMessageBuilder to write the error messages.
   * @return return code.
   */
  public int validateGlobalOptions(LocalizableMessageBuilder buf)
  {
    ArrayList<LocalizableMessage> errors = new ArrayList<LocalizableMessage>();
    // Couldn't have at the same time bindPassword and bindPasswordFile
    if (bindPasswordArg.isPresent() && bindPasswordFileArg.isPresent()) {
      LocalizableMessage message = ERR_TOOL_CONFLICTING_ARGS.get(
          bindPasswordArg.getLongIdentifier(),
          bindPasswordFileArg.getLongIdentifier());
      errors.add(message);
    }

    // Couldn't have at the same time trustAll and
    // trustStore related arg
    if (trustAllArg.isPresent() && trustStorePathArg.isPresent()) {
      LocalizableMessage message = ERR_TOOL_CONFLICTING_ARGS.get(
          trustAllArg.getLongIdentifier(),
          trustStorePathArg.getLongIdentifier());
      errors.add(message);
    }
    if (trustAllArg.isPresent() && trustStorePasswordArg.isPresent()) {
      LocalizableMessage message = ERR_TOOL_CONFLICTING_ARGS.get(
          trustAllArg.getLongIdentifier(),
          trustStorePasswordArg.getLongIdentifier());
      errors.add(message);
    }
    if (trustAllArg.isPresent() && trustStorePasswordFileArg.isPresent()) {
      LocalizableMessage message = ERR_TOOL_CONFLICTING_ARGS.get(
          trustAllArg.getLongIdentifier(),
          trustStorePasswordFileArg.getLongIdentifier());
      errors.add(message);
    }

    // Couldn't have at the same time trustStorePasswordArg and
    // trustStorePasswordFileArg
    if (trustStorePasswordArg.isPresent()
        && trustStorePasswordFileArg.isPresent()) {
      LocalizableMessage message = ERR_TOOL_CONFLICTING_ARGS.get(
          trustStorePasswordArg.getLongIdentifier(),
          trustStorePasswordFileArg.getLongIdentifier());
      errors.add(message);
    }

    if (trustStorePathArg.isPresent())
    {
      // Check that the path exists and is readable
      String value = trustStorePathArg.getValue();
      if (!canRead(trustStorePathArg.getValue()))
      {
        LocalizableMessage message = ERR_CANNOT_READ_TRUSTSTORE.get(
            value);
        errors.add(message);
      }
    }

    if (keyStorePathArg.isPresent())
    {
      // Check that the path exists and is readable
      String value = keyStorePathArg.getValue();
      if (!canRead(trustStorePathArg.getValue()))
      {
        LocalizableMessage message = ERR_CANNOT_READ_KEYSTORE.get(
            value);
        errors.add(message);
      }
    }

    // Couldn't have at the same time startTLSArg and
    // useSSLArg
    if (useStartTLSArg.isPresent()
        && useSSLArg.isPresent()) {
      LocalizableMessage message = ERR_TOOL_CONFLICTING_ARGS.get(
          useStartTLSArg
          .getLongIdentifier(), useSSLArg.getLongIdentifier());
      errors.add(message);
    }
    if (errors.size() > 0)
    {
      for (LocalizableMessage error : errors)
      {
        if (buf.length() > 0)
        {
          buf.append(LINE_SEPARATOR);
        }
        buf.append(error);
      }
      return CONFLICTING_ARGS.get();
    }

    return SUCCESS.get();
  }
  /**
   * Indication if provided global options are validate.
   *
   * @param err the stream to be used to print error message.
   * @return return code.
   */
  public int validateGlobalOptions(PrintStream err)
  {
    LocalizableMessageBuilder buf = new LocalizableMessageBuilder();
    int returnValue = validateGlobalOptions(buf);
    if (buf.length() > 0)
    {
      err.println(wrapText(buf.toString(), MAX_LINE_WIDTH));
    }
    return returnValue;
  }


  /**
   * Indicate if the SSL mode is required.
   *
   * @return True if SSL mode is required
   */
  public boolean useSSL()
  {
    return useSSLArg.isPresent() || alwaysSSL();
  }

  /**
   * Indicate if the startTLS mode is required.
   *
   * @return True if startTLS mode is required
   */
  public boolean useStartTLS()
  {
    return useStartTLSArg.isPresent();
  }

  /**
   * Indicate if the SSL mode is always used.
   *
   * @return True if SSL mode is always used.
   */
  public boolean alwaysSSL()
  {
    return alwaysSSL;
  }

  /**
   * Handle TrustStore.
   *
   * @return The trustStore manager to be used for the command.
   */
  public ApplicationTrustManager getTrustManager()
  {
    if (trustManager == null)
    {
      KeyStore truststore = null ;
      if (trustAllArg.isPresent())
      {
        // Running a null TrustManager  will force createLdapsContext and
        // createStartTLSContext to use a bindTrustManager.
        return null ;
      }
      else
        if (trustStorePathArg.isPresent())
        {
          FileInputStream fos = null;

          try
          {
            fos = new FileInputStream(trustStorePathArg.getValue());
            String trustStorePasswordStringValue = null;
            char[] trustStorePasswordValue = null;
            if (trustStorePasswordArg.isPresent())
            {
              trustStorePasswordStringValue = trustStorePasswordArg.getValue();
            }
            else if (trustStorePasswordFileArg.isPresent())
            {
              trustStorePasswordStringValue =
                trustStorePasswordFileArg.getValue();
            }

            if (trustStorePasswordStringValue !=  null)
            {
              trustStorePasswordStringValue = System
              .getProperty("javax.net.ssl.trustStorePassword");
            }


            if (trustStorePasswordStringValue !=  null)
            {
              trustStorePasswordValue =
                trustStorePasswordStringValue.toCharArray();
            }

            truststore = KeyStore.getInstance(KeyStore.getDefaultType());
            truststore.load(fos, trustStorePasswordValue);
          }
          catch (KeyStoreException e)
          {
            // Nothing to do: if this occurs we will systematically refuse the
            // certificates.  Maybe we should avoid this and be strict, but we
            // are in a best effort mode.
            logger.warn(LocalizableMessage.raw("Error with the truststore"), e);
          }
          catch (NoSuchAlgorithmException e)
          {
            // Nothing to do: if this occurs we will systematically refuse the
            // certificates.  Maybe we should avoid this and be strict, but we
            // are in a best effort mode.
            logger.warn(LocalizableMessage.raw("Error with the truststore"), e);
          }
          catch (CertificateException e)
          {
            // Nothing to do: if this occurs we will systematically refuse the
            // certificates.  Maybe we should avoid this and be strict, but we
            // are in a best effort mode.
            logger.warn(LocalizableMessage.raw("Error with the truststore"), e);
          }
          catch (IOException e)
          {
            // Nothing to do: if this occurs we will systematically refuse the
            // certificates.  Maybe we should avoid this and be strict, but we
            // are in a best effort mode.
            logger.warn(LocalizableMessage.raw("Error with the truststore"), e);
          }
          finally
          {
            close(fos);
          }
        }
      trustManager = new ApplicationTrustManager(truststore);
    }
    return trustManager;
  }

  /**
   * Handle KeyStore.
   *
   * @return The keyStore manager to be used for the command.
   */
  public KeyManager getKeyManager()
  {
    KeyStore keyStore = null;
    String keyStorePasswordStringValue = null;
    char[] keyStorePasswordValue = null;
    if (keyStorePathArg.isPresent())
    {
      FileInputStream fos = null;
      try
      {
        fos = new FileInputStream(keyStorePathArg.getValue());
        if (keyStorePasswordArg.isPresent())
        {
          keyStorePasswordStringValue = keyStorePasswordArg.getValue();
        }
        else if (keyStorePasswordFileArg.isPresent())
        {
          keyStorePasswordStringValue = keyStorePasswordFileArg.getValue();
        }
        if (keyStorePasswordStringValue != null)
        {
          keyStorePasswordValue = keyStorePasswordStringValue.toCharArray();
        }

        keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(fos,keyStorePasswordValue);
      }
      catch (KeyStoreException e)
      {
        // Nothing to do: if this occurs we will systematically refuse
        // the
        // certificates. Maybe we should avoid this and be strict, but
        // we are in a best effort mode.
        logger.warn(LocalizableMessage.raw("Error with the keystore"), e);
      }
      catch (NoSuchAlgorithmException e)
      {
        // Nothing to do: if this occurs we will systematically refuse
        // the
        // certificates. Maybe we should avoid this and be strict, but
        // we are
        // in a best effort mode.
        logger.warn(LocalizableMessage.raw("Error with the keystore"), e);
      }
      catch (CertificateException e)
      {
        // Nothing to do: if this occurs we will systematically refuse
        // the
        // certificates. Maybe we should avoid this and be strict, but
        // we are
        // in a best effort mode.
        logger.warn(LocalizableMessage.raw("Error with the keystore"), e);
      }
      catch (IOException e)
      {
        // Nothing to do: if this occurs we will systematically refuse
        // the
        // certificates. Maybe we should avoid this and be strict, but
        // we are
        // in a best effort mode.
        logger.warn(LocalizableMessage.raw("Error with the keystore"), e);
      }
      finally
      {
        close(fos);
      }

      char[] password = null;
      if (keyStorePasswordStringValue != null)
      {
        password = keyStorePasswordStringValue.toCharArray();
      }
      ApplicationKeyManager akm = new ApplicationKeyManager(keyStore,password);
      if (certNicknameArg.isPresent())
      {
        return new SelectableCertificateKeyManager(akm, certNicknameArg
            .getValue());
      }
      else
      {
        return akm;
      }
    }
    else
    {
      return null;
    }
  }

  /**
   * Returns <CODE>true</CODE> if we can read on the provided path and
   * <CODE>false</CODE> otherwise.
   * @param path the path.
   * @return <CODE>true</CODE> if we can read on the provided path and
   * <CODE>false</CODE> otherwise.
   */
  private boolean canRead(String path)
  {
    boolean canRead;
    File file = new File(path);
    if (file.exists())
    {
      canRead = file.canRead();
    }
    else
    {
      canRead = false;
    }
    return canRead;
  }

  /**
   *  Returns the absolute path of the trust store file that appears on the
   *  config.  Returns <CODE>null</CODE> if the trust store is not defined or
   *  it does not exist.
   *
   *  @return the absolute path of the trust store file that appears on the
   *  config.
   *  @throws ConfigException if there is an error reading the configuration.
   */
  public String getTruststoreFileFromConfig() throws ConfigException
  {
    String truststoreFileAbsolute = null;
    TrustManagerProviderCfg trustManagerCfg = null;
    AdministrationConnectorCfg administrationConnectorCfg = null;

    boolean couldInitializeConfig = configurationInitialized;
    // Initialization for admin framework
    if (!configurationInitialized) {
      couldInitializeConfig = initializeConfiguration();
    }
    if (couldInitializeConfig)
    {
      // Get the Directory Server configuration handler and use it.
      RootCfg root =
        ServerManagementContext.getInstance().getRootConfiguration();
      administrationConnectorCfg = root.getAdministrationConnector();

      String trustManagerStr =
        administrationConnectorCfg.getTrustManagerProvider();
      trustManagerCfg = root.getTrustManagerProvider(trustManagerStr);
      if (trustManagerCfg instanceof FileBasedTrustManagerProviderCfg) {
        FileBasedTrustManagerProviderCfg fileBasedTrustManagerCfg =
          (FileBasedTrustManagerProviderCfg) trustManagerCfg;
        String truststoreFile = fileBasedTrustManagerCfg.getTrustStoreFile();
        // Check the file
        if (truststoreFile.startsWith(File.separator)) {
          truststoreFileAbsolute = truststoreFile;
        } else {
          truststoreFileAbsolute =
            DirectoryServer.getInstanceRoot() + File.separator + truststoreFile;
        }
        File f = new File(truststoreFileAbsolute);
        if (!f.exists() || !f.canRead() || f.isDirectory())
        {
          truststoreFileAbsolute = null;
        }
        else
        {
          // Try to get the canonical path.
          try
          {
            truststoreFileAbsolute = f.getCanonicalPath();
          }
          catch (Throwable t)
          {
            // We can ignore this error.
          }
        }
      }
    }
    return truststoreFileAbsolute;
  }

  /**
   * Returns the admin port from the configuration.
   * @return the admin port from the configuration.
   * @throws ConfigException if an error occurs reading the configuration.
   */
  public int getAdminPortFromConfig() throws ConfigException
  {
    int port;
    // Initialization for admin framework
    boolean couldInitializeConfiguration = configurationInitialized;
    if (!configurationInitialized) {
      couldInitializeConfiguration = initializeConfiguration();
    }
    if (couldInitializeConfiguration)
    {
      RootCfg root =
        ServerManagementContext.getInstance().getRootConfiguration();
      port = root.getAdministrationConnector().getListenPort();
    }
    else
    {
      port = AdministrationConnector.DEFAULT_ADMINISTRATION_CONNECTOR_PORT;
    }
    return port;
  }

  private boolean initializeConfiguration() {
    // check if the initialization is required
    try {
      ServerManagementContext.getInstance().getRootConfiguration().
      getAdministrationConnector();
    } catch (java.lang.Throwable th) {
      try {
        DirectoryServer.bootstrapClient();
        DirectoryServer.initializeJMX();
        DirectoryServer.getInstance().initializeConfiguration();
      } catch (Exception ex) {
        // do nothing
        return false;
      }
    }
    configurationInitialized = true;
    return true;
  }

  /**
   * Returns the port to be used according to the configuration and the
   * arguments provided by the user.
   * This method should be called after the arguments have been parsed.
   * @return the port to be used according to the configuration and the
   * arguments provided by the user.
   */
  public int getPortFromConfig()
  {
    int portNumber;
    if (alwaysSSL()) {
      portNumber =
        AdministrationConnector.DEFAULT_ADMINISTRATION_CONNECTOR_PORT;
      // Try to get the port from the config file
      try
      {
        portNumber = getAdminPortFromConfig();
      } catch (ConfigException ex) {
        // nothing to do
      }
    } else {
      portNumber = 636;
    }
    return portNumber;
  }

  /**
   * Updates the default values of the port and the trust store with what is
   * read in the configuration.
   * @throws ConfigException if there is an error reading the configuration.
   */
  public void initArgumentsWithConfiguration() throws ConfigException
  {
    int portNumber = getPortFromConfig();
    portArg.setDefaultValue(String.valueOf(portNumber));

    String truststoreFileAbsolute = getTruststoreFileFromConfig();
    if (truststoreFileAbsolute != null)
    {
      trustStorePathArg.setDefaultValue(truststoreFileAbsolute);
    }
  }
}
