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

package org.opends.server.util.cli;

import org.opends.messages.Message;
import static org.opends.messages.UtilityMessages.*;
import static org.opends.messages.QuickSetupMessages.*;
import static org.opends.messages.ToolMessages.*;

import org.opends.quicksetup.Step;
import org.opends.quicksetup.UserDataCertificateException;
import org.opends.quicksetup.util.Utils;
import org.opends.server.tools.dsconfig.ArgumentExceptionFactory;
import org.opends.server.tools.LDAPConnectionOptions;
import org.opends.server.tools.SSLConnectionFactory;
import org.opends.server.tools.SSLConnectionException;
import org.opends.server.admin.client.cli.SecureConnectionCliArgs;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.SelectableCertificateKeyManager;
import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ApplicationKeyManager;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Supports interacting with a user through the command line to
 * prompt for information necessary to create an LDAP connection.
 */
public class LDAPConnectionConsoleInteraction {

  private boolean useSSL;
  private boolean useStartTLS;
  private String hostName;
  private int portNumber;
  private String bindDN;
  private String adminUID;
  private String bindPassword;
  private KeyManager keyManager;
  private ApplicationTrustManager trustManager;
  // Boolean that tells if we ask for bind DN or admin UID in the same prompt.
  private boolean useAdminOrBindDn = false;
  // Boolean that tells if we must propose LDAP if it is available even if the
  // user provided certificate parameters.
  private boolean displayLdapIfSecureParameters = false;

  // The SecureConnectionCliArgsList object.
  private SecureConnectionCliArgs secureArgsList = null;

  // Indicate if we need to display the heading
  private boolean isHeadingDisplayed = false;

  // the Console application
  private ConsoleApplication app;

  // Indicate if the truststore in in memory
  private boolean trustStoreInMemory = false;

  // Indicate that the trust manager was created with the parameters provided
  private boolean trustManagerInitialized;

  // The truststore to use for the SSL or STARTTLS connection
  private KeyStore truststore;

  private String keystorePath;

  private String keystorePassword;

  private String certifNickname;

  private String truststorePath;

  private String truststorePassword;

  private Message heading = INFO_LDAP_CONN_HEADING_CONNECTION_PARAMETERS.get();

  /**
   * Enumeration description protocols for interactive CLI choices.
   */
  private enum Protocols
  {
    LDAP(1, INFO_LDAP_CONN_PROMPT_SECURITY_LDAP.get()), SSL(2,
        INFO_LDAP_CONN_PROMPT_SECURITY_USE_SSL.get()), START_TLS(3,
        INFO_LDAP_CONN_PROMPT_SECURITY_USE_START_TLS.get());

    private Integer choice;

    private Message msg;

    /**
     * Private constructor.
     *
     * @param i
     *          the menu return value.
     * @param msg
     *          the message message.
     */
    private Protocols(int i, Message msg)
    {
      choice = i;
      this.msg = msg;
    }

    /**
     * Returns the choice number.
     *
     * @return the attribute name.
     */
    public Integer getChoice()
    {
      return choice;
    }

    /**
     * Return the menu message.
     *
     * @return the menu message.
     */
    public Message getMenuMessage()
    {
      return msg;
    }
  }

  /**
   * Enumeration description protocols for interactive CLI choices.
   */
  private enum TrustMethod
  {
    TRUSTALL(1, INFO_LDAP_CONN_PROMPT_SECURITY_USE_TRUST_ALL.get()),

    TRUSTSTORE(2,INFO_LDAP_CONN_PROMPT_SECURITY_TRUSTSTORE.get()),

    DISPLAY_CERTIFICATE(3,INFO_LDAP_CONN_PROMPT_SECURITY_MANUAL_CHECK.get());

    private Integer choice;

    private Message msg;

    /**
     * Private constructor.
     *
     * @param i
     *          the menu return value.
     * @param msg
     *          the message message.
     */
    private TrustMethod(int i, Message msg)
    {
      choice = new Integer(i);
      this.msg = msg;
    }

    /**
     * Returns the choice number.
     *
     * @return the attribute name.
     */
    public Integer getChoice()
    {
      return choice;
    }

    /**
     * Return the menu message.
     *
     * @return the menu message.
     */
    public Message getMenuMessage()
    {
      return msg;
    }
  }

  /**
   * Enumeration description server certificate trust option.
   */
  private enum TrustOption
  {
    UNTRUSTED(1, INFO_LDAP_CONN_PROMPT_SECURITY_TRUST_OPTION_NO.get()),
    SESSION(2,INFO_LDAP_CONN_PROMPT_SECURITY_TRUST_OPTION_SESSION.get()),
    PERMAMENT(3,INFO_LDAP_CONN_PROMPT_SECURITY_TRUST_OPTION_ALWAYS.get()),
    CERTIFICATE_DETAILS(4,
        INFO_LDAP_CONN_PROMPT_SECURITY_CERTIFICATE_DETAILS.get());

    private Integer choice;

    private Message msg;

    /**
     * Private constructor.
     *
     * @param i
     *          the menu return value.
     * @param msg
     *          the message message.
     */
    private TrustOption(int i, Message msg)
    {
      choice = new Integer(i);
      this.msg = msg;
    }

    /**
     * Returns the choice number.
     *
     * @return the attribute name.
     */
    public Integer getChoice()
    {
      return choice;
    }

    /**
     * Return the menu message.
     *
     * @return the menu message.
     */
    public Message getMenuMessage()
    {
      return msg;
    }
  }
  /**
   * Constructs a parameterized instance.
   *
   * @param app console application
   * @param secureArgs existing set of arguments that have already
   *        been parsed and contain some potential command line specified
   *        LDAP arguments
   */
  public LDAPConnectionConsoleInteraction(ConsoleApplication app,
                                          SecureConnectionCliArgs secureArgs) {
    this.app = app;
    this.secureArgsList = secureArgs;
  }

  /**
   * Interact with the user though the console to get information
   * necessary to establish an LDAP connection.
   *
   * @throws ArgumentException if there is a problem with the arguments
   */
  public void run()
          throws ArgumentException
  {
    run(true, true);
  }


  /**
   * Interact with the user though the console to get information
   * necessary to establish an LDAP connection.
   * @param canUseSSL whether we can propose to connect using SSL or not.
   * @param canUseStartTLS whether we can propose to connect using Start TLS or
   * not.
   *
   * @throws ArgumentException if there is a problem with the arguments
   */
  public void run(boolean canUseSSL, boolean canUseStartTLS)
          throws ArgumentException
  {
    boolean secureConnection = (canUseSSL || canUseStartTLS) &&
      (
          secureArgsList.useSSLArg.isPresent()
          ||
          secureArgsList.useStartTLSArg.isPresent()
          ||
          secureArgsList.trustAllArg.isPresent()
          ||
          secureArgsList.trustStorePathArg.isPresent()
          ||
          secureArgsList.trustStorePasswordArg.isPresent()
          ||
          secureArgsList.trustStorePasswordFileArg.isPresent()
          ||
          secureArgsList.keyStorePathArg.isPresent()
          ||
          secureArgsList.keyStorePasswordArg.isPresent()
          ||
          secureArgsList.keyStorePasswordFileArg.isPresent()
      );

    // Get the LDAP host.
    hostName = secureArgsList.hostNameArg.getValue();
    final String tmpHostName = hostName;
    if (app.isInteractive() && !secureArgsList.hostNameArg.isPresent())
    {
      checkHeadingDisplayed();

      ValidationCallback<String> callback = new ValidationCallback<String>()
      {

        public String validate(ConsoleApplication app, String input)
            throws CLIException
        {
          String ninput = input.trim();
          if (ninput.length() == 0)
          {
            return tmpHostName;
          }
          else
          {
            try
            {
              InetAddress.getByName(ninput);
              return ninput;
            }
            catch (UnknownHostException e)
            {
              // Try again...
              app.println();
              app.println(ERR_LDAP_CONN_BAD_HOST_NAME.get(ninput));
              app.println();
              return null;
            }
          }
        }

      };

      try
      {
        app.println();
        hostName = app.readValidatedInput(INFO_LDAP_CONN_PROMPT_HOST_NAME
            .get(hostName), callback);
      }
      catch (CLIException e)
      {
        throw ArgumentExceptionFactory.unableToReadConnectionParameters(e);
      }
    }

    useSSL = secureArgsList.useSSL();
    useStartTLS = secureArgsList.useStartTLS();
    boolean connectionTypeIsSet =
      (
        secureArgsList.useSSLArg.isPresent()
        ||
        secureArgsList.useStartTLSArg.isPresent()
        ||
        (
          secureArgsList.useSSLArg.isValueSetByProperty()
          &&
          secureArgsList.useStartTLSArg.isValueSetByProperty()
        )
      );
    if (app.isInteractive() && !connectionTypeIsSet)
    {
      checkHeadingDisplayed();

      MenuBuilder<Integer> builder = new MenuBuilder<Integer>(app);
      builder.setPrompt(INFO_LDAP_CONN_PROMPT_SECURITY_USE_SECURE_CTX.get());

      Protocols defaultProtocol ;
      if (secureConnection)
      {
        defaultProtocol = Protocols.SSL;
      }
      else
      {
        defaultProtocol = Protocols.LDAP;
      }
      for (Protocols p : Protocols.values())
      {
        if (secureConnection && p.equals(Protocols.LDAP) &&
            !displayLdapIfSecureParameters)
        {
          continue ;
        }
        if (!canUseSSL && p.equals(Protocols.SSL))
        {
          continue;
        }
        if (!canUseStartTLS && p.equals(Protocols.START_TLS))
        {
          continue;
        }
        int i = builder.addNumberedOption(p.getMenuMessage(), MenuResult
            .success(p.getChoice()));
        if (p.equals(defaultProtocol))
        {
          builder.setDefault(
              INFO_LDAP_CONN_PROMPT_SECURITY_PROTOCOL_DEFAULT_CHOICE
                  .get(i), MenuResult.success(p.getChoice()));
        }
      }

      Menu<Integer> menu = builder.toMenu();
      try
      {
        MenuResult<Integer> result = menu.run();
        if (result.isSuccess())
        {
          if (result.getValue().equals(Protocols.SSL.getChoice()))
          {
            useSSL = true;
          }
          else if (result.getValue()
              .equals(Protocols.START_TLS.getChoice()))
          {
            useStartTLS = true;
          }
        }
        else
        {
          // Should never happen.
          throw new RuntimeException();
        }
      }
      catch (CLIException e)
      {
        throw new RuntimeException(e);
      }
    }

    if ((useSSL || useStartTLS) && (trustManager == null))
    {
      initializeTrustManager();
    }

    // Get the LDAP port.
    if (!useSSL)
    {
      portNumber = secureArgsList.portArg.getIntValue();
    }
    else
    {
      if (secureArgsList.portArg.isPresent())
      {
        portNumber = secureArgsList.portArg.getIntValue();
      }
      else
      {
        portNumber = 636;
      }
    }
    final int tmpPortNumber = portNumber;
    if (app.isInteractive() && !secureArgsList.portArg.isPresent())
    {
      checkHeadingDisplayed();

      ValidationCallback<Integer> callback = new ValidationCallback<Integer>()
      {

        public Integer validate(ConsoleApplication app, String input)
            throws CLIException
        {
          String ninput = input.trim();
          if (ninput.length() == 0)
          {
            return tmpPortNumber;
          }
          else
          {
            try
            {
              int i = Integer.parseInt(ninput);
              if (i < 1 || i > 65535)
              {
                throw new NumberFormatException();
              }
              return i;
            }
            catch (NumberFormatException e)
            {
              // Try again...
              app.println();
              app.println(ERR_LDAP_CONN_BAD_PORT_NUMBER.get(ninput));
              app.println();
              return null;
            }
          }
        }

      };

      try
      {
        app.println();
        portNumber = app.readValidatedInput(INFO_LDAP_CONN_PROMPT_PORT_NUMBER
            .get(portNumber), callback);
      }
      catch (CLIException e)
      {
        throw ArgumentExceptionFactory.unableToReadConnectionParameters(e);
      }
    }

    // Get the LDAP bind credentials.
    bindDN = secureArgsList.bindDnArg.getValue();
    adminUID = secureArgsList.adminUidArg.getValue();
    final boolean useAdmin = secureArgsList.useAdminUID();
    boolean argIsPresent = useAdmin ?
        secureArgsList.adminUidArg.isPresent() :
          secureArgsList.bindDnArg.isPresent();
    final String tmpBindDN = bindDN;
    final String tmpAdminUID = adminUID;
    if (keyManager == null)
    {
      if (app.isInteractive() && !argIsPresent)
      {
        checkHeadingDisplayed();

        ValidationCallback<String> callback = new ValidationCallback<String>()
        {

          public String validate(ConsoleApplication app, String input)
              throws CLIException
          {
            String ninput = input.trim();
            if (ninput.length() == 0)
            {
              if (useAdmin)
              {
                return tmpAdminUID;
              }
              else
              {
                return tmpBindDN;
              }
            }
            else
            {
              return ninput;
            }
          }

        };

        try
        {
          app.println();
          if (useAdminOrBindDn)
          {
            String def = (adminUID != null) ? adminUID : bindDN;
            String v = app.readValidatedInput(
                INFO_LDAP_CONN_GLOBAL_ADMINISTRATOR_OR_BINDDN_PROMPT.get(def),
                callback);
            if (Utils.isDn(v))
            {
              bindDN = v;
              adminUID = null;
            }
            else
            {
              bindDN = null;
              adminUID = v;
            }
          }
          else if (useAdmin)
          {
            adminUID = app.readValidatedInput(
                INFO_LDAP_CONN_PROMPT_ADMINISTRATOR_UID.get(adminUID),
                callback);
          }
          else
          {
            bindDN = app.readValidatedInput(INFO_LDAP_CONN_PROMPT_BIND_DN
              .get(bindDN), callback);
          }
        }
        catch (CLIException e)
        {
          throw ArgumentExceptionFactory
              .unableToReadConnectionParameters(e);
        }
      }
    }
    else
    {
      bindDN = null;
      adminUID = null;
    }

    bindPassword = secureArgsList.bindPasswordArg.getValue();
    if (keyManager == null)
    {
      if (secureArgsList.bindPasswordFileArg.isPresent())
      {
        // Read from file if it exists.
        bindPassword = secureArgsList.bindPasswordFileArg.getValue();

        if (bindPassword == null)
        {
          if (useAdmin)
          {
            throw ArgumentExceptionFactory.missingBindPassword(adminUID);
          }
          else
          {
            throw ArgumentExceptionFactory.missingBindPassword(bindDN);
          }
        }
      }
      else if (bindPassword == null || bindPassword.equals("-"))
      {
        // Read the password from the stdin.
        if (!app.isInteractive())
        {
          throw ArgumentExceptionFactory
              .unableToReadBindPasswordInteractively();
        }

        checkHeadingDisplayed();

        try
        {
          app.println();
          Message prompt;
          if (useAdminOrBindDn)
          {
            if (adminUID != null)
            {
              prompt = INFO_LDAPAUTH_PASSWORD_PROMPT.get(adminUID);
            }
            else
            {
              prompt = INFO_LDAPAUTH_PASSWORD_PROMPT.get(bindDN);
            }
          }
          else if (useAdmin)
          {
            prompt = INFO_LDAPAUTH_PASSWORD_PROMPT.get(adminUID);
          }
          else
          {
            prompt = INFO_LDAPAUTH_PASSWORD_PROMPT.get(bindDN);
          }
          bindPassword = app.readPassword(prompt);
        }
        catch (Exception e)
        {
          throw ArgumentExceptionFactory
              .unableToReadConnectionParameters(e);
        }
      }
    }
  }

  /**
   * Get the trust manager.
   *
   * @return The trust manager based on CLI args on interactive prompt.
   * @throws ArgumentException If an error occurs when getting args values.
   */
  private ApplicationTrustManager getTrustManagerInternal()
  throws ArgumentException
  {
    // If we have the trustALL flag, don't do anything
    // just return null
    if (secureArgsList.trustAllArg.isPresent())
    {
      return null;
    }

      // Check if some trust manager info are set
    boolean weDontKnowTheTrustMethod =
      !(  secureArgsList.trustAllArg.isPresent()
          ||
          secureArgsList.trustStorePathArg.isPresent()
          ||
          secureArgsList.trustStorePasswordArg.isPresent()
          ||
          secureArgsList.trustStorePasswordFileArg.isPresent()
        );
    boolean askForTrustStore = false;
    if (app.isInteractive() && weDontKnowTheTrustMethod)
    {
      checkHeadingDisplayed();

      app.println();
      MenuBuilder<Integer> builder = new MenuBuilder<Integer>(app);
      builder.setPrompt(INFO_LDAP_CONN_PROMPT_SECURITY_TRUST_METHOD.get());

      TrustMethod defaultTrustMethod = TrustMethod.DISPLAY_CERTIFICATE;
      for (TrustMethod t : TrustMethod.values())
      {
        int i = builder.addNumberedOption(t.getMenuMessage(), MenuResult
            .success(t.getChoice()));
        if (t.equals(defaultTrustMethod))
        {
          builder.setDefault(
              INFO_LDAP_CONN_PROMPT_SECURITY_PROTOCOL_DEFAULT_CHOICE
                  .get(new Integer(i)), MenuResult.success(t.getChoice()));
        }
      }

      Menu<Integer> menu = builder.toMenu();
      trustStoreInMemory = false;
      try
      {
        MenuResult<Integer> result = menu.run();
        if (result.isSuccess())
        {
          if (result.getValue().equals(TrustMethod.TRUSTALL.getChoice()))
          {
            // If we have the trustALL flag, don't do anything
            // just return null
            return null;
          }
          else if (result.getValue().equals(
              TrustMethod.TRUSTSTORE.getChoice()))
          {
            // We have to ask for truststore info
            askForTrustStore = true;
          }
          else if (result.getValue().equals(
              TrustMethod.DISPLAY_CERTIFICATE.getChoice()))
          {
            // The certificate will be displayed to the user
            askForTrustStore = false;
            trustStoreInMemory = true;
          }
          else
          {
            // Should never happen.
            throw new RuntimeException();
          }
        }
        else
        {
          // Should never happen.
          throw new RuntimeException();
        }
      }
      catch (CLIException e)
      {
        throw new RuntimeException(e);

      }
    }

    // If we not trust all server certificates, we have to get info
    // about truststore. First get the truststore path.
    truststorePath = secureArgsList.trustStorePathArg.getValue();
    if (app.isInteractive() && !secureArgsList.trustStorePathArg.isPresent()
        && askForTrustStore)
    {
      checkHeadingDisplayed();

      ValidationCallback<String> callback = new ValidationCallback<String>()
      {
        public String validate(ConsoleApplication app, String input)
            throws CLIException
        {
          String ninput = input.trim();
          if (ninput.length() == 0)
          {
            app.println();
            app.println(ERR_LDAP_CONN_PROMPT_SECURITY_INVALID_FILE_PATH
                .get());
            app.println();
            return null;
          }
          File f = new File(ninput);
          if (f.exists() && f.canRead() && !f.isDirectory())
          {
            return ninput;
          }
          else
          {
            app.println();
            app.println(ERR_LDAP_CONN_PROMPT_SECURITY_INVALID_FILE_PATH
                .get());
            app.println();
            return null;
          }
        }
      };

      try
      {
        app.println();
        truststorePath = app.readValidatedInput(
            INFO_LDAP_CONN_PROMPT_SECURITY_TRUSTSTORE_PATH.get(), callback);
      }
      catch (CLIException e)
      {
        throw ArgumentExceptionFactory.unableToReadConnectionParameters(e);
      }
    }

    // Then the truststore password.
    //  As the most common case is to have no password for truststore,
    // we don't ask it in the interactive mode.
    truststorePassword = secureArgsList.trustStorePasswordArg
        .getValue();

    if (secureArgsList.trustStorePasswordFileArg.isPresent())
    {
      // Read from file if it exists.
      truststorePassword = secureArgsList.trustStorePasswordFileArg
          .getValue();
    }
    if ((truststorePassword !=  null) && (truststorePassword.equals("-")))
    {
      // Read the password from the stdin.
      if (!app.isInteractive())
      {
        truststorePassword = null;
      }
      else
      {
        checkHeadingDisplayed();

        try
        {
          app.println();
          Message prompt = INFO_LDAP_CONN_PROMPT_SECURITY_TRUSTSTORE_PASSWORD
              .get(truststorePath);
          truststorePassword = app.readPassword(prompt);
        }
        catch (Exception e)
        {
          throw ArgumentExceptionFactory.unableToReadConnectionParameters(e);
        }
      }
    }
    // We'we got all the information to get the truststore manager
    try
    {
      truststore = KeyStore.getInstance(KeyStore.getDefaultType());
      if (truststorePath != null)
      {
        FileInputStream fos = new FileInputStream(truststorePath);
        if (truststorePassword != null)
        {
          truststore.load(fos, truststorePassword.toCharArray());
        }
        else
        {
          truststore.load(fos, null);
        }
        fos.close();
      }
      else
      {
        truststore.load(null, null);
      }
      return new ApplicationTrustManager(truststore);
    }
    catch (Exception e)
    {
      throw ArgumentExceptionFactory.unableToReadConnectionParameters(e);
    }
  }

  /**
   * Get the key manager.
   *
   * @return The key manager based on CLI args on interactive prompt.
   * @throws ArgumentException If an error occurs when getting args values.
   */
  private KeyManager getKeyManagerInternal()
  throws ArgumentException
  {
    // Do we need client side authentication ?
    // If one of the client side authentication args is set, we assume
    // that we
    // need client side authentication.
    boolean weDontKnowIfWeNeedKeystore = !(secureArgsList.keyStorePathArg
        .isPresent()
        || secureArgsList.keyStorePasswordArg.isPresent()
        || secureArgsList.keyStorePasswordFileArg.isPresent()
        || secureArgsList.certNicknameArg
        .isPresent());

    // We don't have specific key manager parameter.
    // We assume that no client side authentication is required
    // Client side authentication is not the common use case. As a
    // consequence, interactive mode doesn't add an extra question
    // which will be in most cases useless.
    if (weDontKnowIfWeNeedKeystore)
    {
      return null;
    }

    // Get info about keystore. First get the keystore path.
    keystorePath = secureArgsList.keyStorePathArg.getValue();
    if (app.isInteractive() && !secureArgsList.keyStorePathArg.isPresent())
    {
      checkHeadingDisplayed();

      ValidationCallback<String> callback = new ValidationCallback<String>()
      {
        public String validate(ConsoleApplication app, String input)
            throws CLIException
        {
          String ninput = input.trim();
          if (ninput.length() == 0)
          {
            return ninput;
          }
          File f = new File(ninput);
          if (f.exists() && f.canRead() && !f.isDirectory())
          {
            return ninput;
          }
          else
          {
            app.println();
            app.println(ERR_LDAP_CONN_PROMPT_SECURITY_INVALID_FILE_PATH
                .get());
            app.println();
            return null;
          }
        }
      };

      try
      {
        app.println();
        keystorePath = app.readValidatedInput(
            INFO_LDAP_CONN_PROMPT_SECURITY_KEYSTORE_PATH.get(), callback);
      }
      catch (CLIException e)
      {
        throw ArgumentExceptionFactory.unableToReadConnectionParameters(e);
      }
    }

    // Then the keystore password.
    keystorePassword = secureArgsList.keyStorePasswordArg.getValue();

    if (secureArgsList.keyStorePasswordFileArg.isPresent())
    {
      // Read from file if it exists.
      keystorePassword = secureArgsList.keyStorePasswordFileArg.getValue();

      if (keystorePassword == null)
      {
        throw ArgumentExceptionFactory.missingBindPassword(keystorePassword);
      }
    }
    else if (keystorePassword == null || keystorePassword.equals("-"))
    {
      // Read the password from the stdin.
      if (!app.isInteractive())
      {
        throw ArgumentExceptionFactory
            .unableToReadBindPasswordInteractively();
      }

      checkHeadingDisplayed();

      try
      {
        app.println();
        Message prompt = INFO_LDAP_CONN_PROMPT_SECURITY_KEYSTORE_PASSWORD
            .get(keystorePath);
        keystorePassword = app.readPassword(prompt);
      }
      catch (Exception e)
      {
        throw ArgumentExceptionFactory.unableToReadConnectionParameters(e);
      }
    }

    // finally the certificate name, if needed.
    KeyStore keystore = null;
    Enumeration<String> aliasesEnum = null;
    try
    {
      FileInputStream fos = new FileInputStream(keystorePath);
      keystore = KeyStore.getInstance(KeyStore.getDefaultType());
      keystore.load(fos, keystorePassword.toCharArray());
      fos.close();
      aliasesEnum = keystore.aliases();
    }
    catch (Exception e)
    {
      throw ArgumentExceptionFactory.unableToReadConnectionParameters(e);
    }

    certifNickname = secureArgsList.certNicknameArg.getValue();
    if (app.isInteractive() && !secureArgsList.certNicknameArg.isPresent()
        && aliasesEnum.hasMoreElements())
    {
      checkHeadingDisplayed();

      try
      {
        MenuBuilder<String> builder = new MenuBuilder<String>(app);
        builder.setPrompt(INFO_LDAP_CONN_PROMPT_SECURITY_CERTIFICATE_ALIASES
            .get());
        int certificateNumber = 0;
        for (; aliasesEnum.hasMoreElements();)
        {
          String alias = aliasesEnum.nextElement();
          if (keystore.isKeyEntry(alias))
          {
            X509Certificate certif = (X509Certificate) keystore
                .getCertificate(alias);
            certificateNumber++;
            builder.addNumberedOption(
                INFO_LDAP_CONN_PROMPT_SECURITY_CERTIFICATE_ALIAS.get(alias,
                    certif.getSubjectDN().getName()), MenuResult
                    .success(alias));
          }
        }

        if (certificateNumber > 1)
        {
          app.println();
          Menu<String> menu = builder.toMenu();
          MenuResult<String> result = menu.run();
          if (result.isSuccess())
          {
            certifNickname = result.getValue();
          }
          else
          {
            // Should never happen.
            throw new RuntimeException();
          }
        }
        else
        {
          certifNickname = null;
        }
      }
      catch (KeyStoreException e)
      {
        throw ArgumentExceptionFactory.unableToReadConnectionParameters(e);
      }
      catch (CLIException e)
      {
        throw ArgumentExceptionFactory.unableToReadConnectionParameters(e);
      }
    }

    // We'we got all the information to get the keys manager
    ApplicationKeyManager akm = new ApplicationKeyManager(keystore,
        keystorePassword.toCharArray());

    if (certifNickname != null)
    {
      return new SelectableCertificateKeyManager(akm, certifNickname);
    }
    else
    {
      return akm;
    }
  }

  /**
   * Indicates whether or not a connection should use SSL based on
   * this interaction.
   *
   * @return boolean where true means use SSL
   */
  public boolean useSSL() {
    return useSSL;
  }

  /**
   * Indicates whether or not a connection should use StartTLS based on
   * this interaction.
   *
   * @return boolean where true means use StartTLS
   */
  public boolean useStartTLS() {
    return useStartTLS;
  }

  /**
   * Gets the host name that should be used for connections based on
   * this interaction.
   *
   * @return host name for connections
   */
  public String getHostName() {
    return hostName;
  }

  /**
   * Gets the port number name that should be used for connections based on
   * this interaction.
   *
   * @return port number for connections
   */
  public int getPortNumber() {
    return portNumber;
  }

  /**
   * Sets the port number name that should be used for connections based on
   * this interaction.
   *
   * @param portNumber port number for connections
   */
  public void setPortNumber(int portNumber) {
    this.portNumber = portNumber;
  }

  /**
   * Gets the bind DN name that should be used for connections based on
   * this interaction.
   *
   * @return bind DN for connections
   */
  public String getBindDN() {
    String dn;
    if (useAdminOrBindDn)
    {
      if (this.adminUID != null)
      {
        dn = ADSContext.getAdministratorDN(this.adminUID);
      }
      else
      {
        dn = this.bindDN;
      }
    }
    else if (secureArgsList.useAdminUID())
    {
      dn = ADSContext.getAdministratorDN(this.adminUID);
    }
    else
    {
      dn = this.bindDN;
    }
    return dn;
  }

  /**
   * Gets the administrator UID name that should be used for connections based
   * on this interaction.
   *
   * @return administrator UID for connections
   */
  public String getAdministratorUID() {
    return this.adminUID;
  }

  /**
   * Gets the bind password that should be used for connections based on
   * this interaction.
   *
   * @return bind password for connections
   */
  public String getBindPassword() {
    return this.bindPassword;
  }

  /**
   * Gets the trust manager that should be used for connections based on
   * this interaction.
   *
   * @return trust manager for connections
   */
  public TrustManager getTrustManager() {
    return this.trustManager;
  }

  /**
   * Gets the key store that should be used for connections based on
   * this interaction.
   *
   * @return key store for connections
   */
  public KeyStore getKeyStore() {
    return this.truststore;
  }

  /**
   * Gets the key manager that should be used for connections based on
   * this interaction.
   *
   * @return key manager for connections
   */
  public KeyManager getKeyManager() {
    return this.keyManager;
  }

  /**
   * Indicate if the truststore is in memory.
   *
   * @return true if the truststore is in memory.
   */
  public boolean isTrustStoreInMemory() {
    return this.trustStoreInMemory;
  }

  /**
   * Indicate if the certificate chain can be trusted.
   *
   * @param chain The certificate chain to validate
   * @return true if the server certificate is trusted.
   */
  public boolean checkServerCertificate(X509Certificate[] chain)
  {
    return checkServerCertificate(chain, null, null);
  }

  /**
   * Indicate if the certificate chain can be trusted.
   *
   * @param chain The certificate chain to validate
   * @param authType the authentication type.
   * @param host the host we tried to connect and that presented the
   * certificate.
   * @return true if the server certificate is trusted.
   */
  public boolean checkServerCertificate(X509Certificate[] chain,
      String authType, String host)
    {
    if (trustManager == null)
    {
      try
      {
        initializeTrustManager();
      }
      catch (ArgumentException ae)
      {
        // Should not occur
        throw new RuntimeException(ae);
      }
    }
    app.println();
    app.println(INFO_LDAP_CONN_PROMPT_SECURITY_SERVER_CERTIFICATE.get());
    app.println();
    for (int i = 0; i < chain.length; i++)
    {
      // Certificate DN
      app.println(INFO_LDAP_CONN_SECURITY_SERVER_CERTIFICATE_USER_DN.get(
          chain[i].getSubjectDN().toString()));

      // certificate validity
      app.println(
          INFO_LDAP_CONN_SECURITY_SERVER_CERTIFICATE_VALIDITY.get(
              chain[i].getNotBefore().toString(),
              chain[i].getNotAfter().toString()));

      // certificate Issuer
      app.println(
          INFO_LDAP_CONN_SECURITY_SERVER_CERTIFICATE_ISSUER.get(
              chain[i].getIssuerDN().toString()));

      if (i+1 <chain.length)
      {
        app.println();
        app.println();
      }
    }
    MenuBuilder<Integer> builder = new MenuBuilder<Integer>(app);
    builder.setPrompt(INFO_LDAP_CONN_PROMPT_SECURITY_TRUST_OPTION.get());

    TrustOption defaultTrustMethod = TrustOption.SESSION ;
    for (TrustOption t : TrustOption.values())
    {
      int i = builder.addNumberedOption(t.getMenuMessage(), MenuResult
          .success(t.getChoice()));
      if (t.equals(defaultTrustMethod))
      {
        builder.setDefault(
            INFO_LDAP_CONN_PROMPT_SECURITY_PROTOCOL_DEFAULT_CHOICE
                .get(new Integer(i)), MenuResult.success(t.getChoice()));
      }
    }

    app.println();
    app.println();

    Menu<Integer> menu = builder.toMenu();
    while (true)
    {
      try
      {
        MenuResult<Integer> result = menu.run();
        if (result.isSuccess())
        {
          if (result.getValue().equals(TrustOption.UNTRUSTED.getChoice()))
          {
            return false;
          }

          if ((result.getValue().equals(TrustOption.CERTIFICATE_DETAILS
              .getChoice())))
          {
            for (int i = 0; i < chain.length; i++)
            {
              app.println();
              app.println(INFO_LDAP_CONN_SECURITY_SERVER_CERTIFICATE
                  .get(chain[i].toString()));
            }
            continue;
          }

          // We should add it in the memory truststore
          for (int i = 0; i < chain.length; i++)
          {
            String alias = chain[i].getSubjectDN().getName();
            try
            {
              truststore.setCertificateEntry(alias, chain[i]);
            }
            catch (KeyStoreException e1)
            {
              // What else should we do?
              return false;
            }
          }

          // Update the trust manager
          if (trustManager == null)
          {
            trustManager = new ApplicationTrustManager(truststore);
          }
          if ((authType != null) && (host != null))
          {
            // Update the trust manager with the new certificate
            trustManager.acceptCertificate(chain, authType, host);
          }
          else
          {
            // Do a full reset of the contents of the keystore.
            trustManager = new ApplicationTrustManager(truststore);
          }
          if (result.getValue().equals(TrustOption.PERMAMENT.getChoice()))
          {
            ValidationCallback<String> callback =
              new ValidationCallback<String>()
            {
              public String validate(ConsoleApplication app, String input)
                  throws CLIException
              {
                String ninput = input.trim();
                if (ninput.length() == 0)
                {
                  app.println();
                  app.println(ERR_LDAP_CONN_PROMPT_SECURITY_INVALID_FILE_PATH
                      .get());
                  app.println();
                  return null;
                }
                File f = new File(ninput);
                if (!f.isDirectory())
                {
                  return ninput;
                }
                else
                {
                  app.println();
                  app.println(ERR_LDAP_CONN_PROMPT_SECURITY_INVALID_FILE_PATH
                      .get());
                  app.println();
                  return null;
                }
              }
            };

            String truststorePath;
            try
            {
              app.println();
              truststorePath = app.readValidatedInput(
                  INFO_LDAP_CONN_PROMPT_SECURITY_TRUSTSTORE_PATH.get(),
                  callback);
            }
            catch (CLIException e)
            {
              return true;
            }

            // Read the password from the stdin.
            String truststorePassword;
            try
            {
              app.println();
              Message prompt = INFO_LDAP_CONN_PROMPT_SECURITY_KEYSTORE_PASSWORD
                  .get(truststorePath);
              truststorePassword = app.readPassword(prompt);
            }
            catch (Exception e)
            {
              return true;
            }
            try
            {
              KeyStore ts = KeyStore.getInstance("JKS");
              FileInputStream fis;
              try
              {
                fis = new FileInputStream(truststorePath);
              }
              catch (FileNotFoundException e)
              {
                fis = null;
              }
              ts.load(fis, truststorePassword.toCharArray());
              if (fis != null)
              {
                fis.close();
              }
              for (int i = 0; i < chain.length; i++)
              {
                String alias = chain[i].getSubjectDN().getName();
                ts.setCertificateEntry(alias, chain[i]);
              }
              FileOutputStream fos = new FileOutputStream(truststorePath);
              ts.store(fos, truststorePassword.toCharArray());
              if (fos != null)
              {
                fos.close();
              }
            }
            catch (Exception e)
            {
              return true;
            }
          }
          return true;
        }
        else
        {
          // Should never happen.
          throw new RuntimeException();
        }
      }
      catch (CLIException cliE)
      {
        throw new RuntimeException(cliE);
      }
    }
  }

 /**
  * Populates an a set of LDAP options with state from this interaction.
  *
  * @param  options existing set of options; may be null in which case this
  *         method will create a new set of <code>LDAPConnectionOptions</code>
  *         to be returned
  * @return used during this interaction
  * @throws SSLConnectionException if this interaction has specified the use
  *         of SSL and there is a problem initializing the SSL connection
  *         factory
  */
 public LDAPConnectionOptions populateLDAPOptions(
         LDAPConnectionOptions options)
         throws SSLConnectionException
 {
   if (options == null) {
     options = new LDAPConnectionOptions();
   }
   if (this.useSSL) {
     options.setUseSSL(true);
     SSLConnectionFactory sslConnectionFactory = new SSLConnectionFactory();
     sslConnectionFactory.init(getTrustManager() == null, keystorePath,
                               keystorePassword, certifNickname,
                               truststorePath, truststorePassword);
     options.setSSLConnectionFactory(sslConnectionFactory);
   } else {
     options.setUseSSL(false);
   }
   options.setStartTLS(this.useStartTLS);
   return options;
 }

 /**
  * Prompts the user to accept the certificate.
  * @param t the throwable that was generated because the certificate was
  * not trusted.
  * @param usedTrustManager the trustManager used when trying to establish the
  * connection.
  * @param usedUrl the LDAP URL used to connect to the server.
  * @param displayErrorMessage whether to display an error message before
  * asking to accept the certificate or not.
  * @param logger the Logger used to log messages.
  * @return <CODE>true</CODE> if the user accepted the certificate and
  * <CODE>false</CODE> otherwise.
  */
 public boolean promptForCertificateConfirmation(Throwable t,
     ApplicationTrustManager usedTrustManager, String usedUrl,
     boolean displayErrorMessage, Logger logger)
 {
   boolean returnValue = false;
   ApplicationTrustManager.Cause cause;
   if (usedTrustManager != null)
   {
     cause = usedTrustManager.getLastRefusedCause();
   }
   else
   {
     cause = null;
   }
   if (logger != null)
   {
     logger.log(Level.INFO, "Certificate exception cause: "+cause);
   }
   UserDataCertificateException.Type excType = null;
   if (cause == ApplicationTrustManager.Cause.NOT_TRUSTED)
   {
     excType = UserDataCertificateException.Type.NOT_TRUSTED;
   }
   else if (cause ==
     ApplicationTrustManager.Cause.HOST_NAME_MISMATCH)
   {
     excType = UserDataCertificateException.Type.HOST_NAME_MISMATCH;
   }
   else
   {
     Message msg = Utils.getThrowableMsg(INFO_ERROR_CONNECTING_TO_LOCAL.get(),
         t);
     app.println(msg);
   }

   if (excType != null)
   {
     String h;
     int p;
     try
     {
       URI uri = new URI(usedUrl);
       h = uri.getHost();
       p = uri.getPort();
     }
     catch (Throwable t1)
     {
       if (logger != null)
       {
         logger.log(Level.WARNING, "Error parsing ldap url of ldap url.", t1);
       }
       h = INFO_NOT_AVAILABLE_LABEL.get().toString();
       p = -1;
     }



     UserDataCertificateException udce =
       new UserDataCertificateException(Step.REPLICATION_OPTIONS,
           INFO_CERTIFICATE_EXCEPTION.get(h, String.valueOf(p)), t, h, p,
               usedTrustManager.getLastRefusedChain(),
               usedTrustManager.getLastRefusedAuthType(), excType);

     Message msg;
     if (udce.getType() == UserDataCertificateException.Type.NOT_TRUSTED)
     {
       msg = INFO_CERTIFICATE_NOT_TRUSTED_TEXT_CLI.get(
           udce.getHost(), String.valueOf(udce.getPort()));
     }
     else
     {
       msg = INFO_CERTIFICATE_NAME_MISMATCH_TEXT_CLI.get(
           udce.getHost(), String.valueOf(udce.getPort()),
           udce.getHost(),
           udce.getHost(), String.valueOf(udce.getPort()));
     }
     if (displayErrorMessage)
     {
       app.println(msg);
     }
     X509Certificate[] chain = udce.getChain();
     String authType = udce.getAuthType();
     String host = udce.getHost();
     if (logger != null)
     {
       if (chain == null)
       {
         logger.log(Level.WARNING,
         "The chain is null for the UserDataCertificateException");
       }
       if (authType == null)
       {
         logger.log(Level.WARNING,
         "The auth type is null for the UserDataCertificateException");
       }
       if (host == null)
       {
         logger.log(Level.WARNING,
         "The host is null for the UserDataCertificateException");
       }
     }
     if (chain != null)
     {
       returnValue = checkServerCertificate(chain, authType, host);
     }
   }
   return returnValue;
 }

 /**
  * Sets the heading that is displayed in interactive mode.
  * @param heading the heading that is displayed in interactive mode.
  */
 public void setHeadingMessage(Message heading)
 {
   this.heading = heading;
 }

 /**
  * Displays the heading if it was not displayed before.
  *
  */
 private void checkHeadingDisplayed()
 {
   if (!isHeadingDisplayed)
   {
     app.println();
     app.println();
     app.println(heading);
     isHeadingDisplayed = true;
   }
 }

 /**
  * Tells whether during interaction we can ask for both the DN or the admin
  * UID.
  * @return <CODE>true</CODE> if during interaction we can ask for both the DN
  * and the admin UID and <CODE>false</CODE> otherwise.
  */
 public boolean isUseAdminOrBindDn()
 {
   return useAdminOrBindDn;
 }

 /**
  * Tells whether we can ask during interaction for both the DN and the admin
  * UID or not.
  * @param useAdminOrBindDn whether we can ask for both the DN and the admin UID
  * during interaction or not.
  */
 public void setUseAdminOrBindDn(boolean useAdminOrBindDn)
 {
   this.useAdminOrBindDn = useAdminOrBindDn;
 }

 /**
  * Tells whether we propose LDAP as protocol even if the user provided security
  * parameters.  This is required in command-lines that access multiple servers
  * (like dsreplication).
  * @param displayLdapIfSecureParameters whether propose LDAP as protocol even
  * if the user provided security parameters or not.
  */
 public void setDisplayLdapIfSecureParameters(
     boolean displayLdapIfSecureParameters)
 {
   this.displayLdapIfSecureParameters = displayLdapIfSecureParameters;
 }

 /**
  * Resets the heading displayed flag, so that next time we call run the heading
  * is displayed.
  */
 public void resetHeadingDisplayed()
 {
   isHeadingDisplayed = false;
 }

 /**
  * Forces the initialization of the trust manager with the arguments provided
  * by the user.
  * @throws ArgumentException if there is an error with the arguments provided
  * by the user.
  */
 public void initializeTrustManagerIfRequired() throws ArgumentException
 {
   if (!trustManagerInitialized)
   {
     initializeTrustManager();
   }
 }

 private void initializeTrustManager() throws ArgumentException
 {
   // Get truststore info
   trustManager = getTrustManagerInternal();

   // Check if we need client side authentication
   keyManager = getKeyManagerInternal();

   trustManagerInitialized = true;
 }
}
