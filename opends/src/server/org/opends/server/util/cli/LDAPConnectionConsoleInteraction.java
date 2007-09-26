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
import static org.opends.messages.ToolMessages.INFO_LDAPAUTH_PASSWORD_PROMPT;
import org.opends.server.tools.dsconfig.ArgumentExceptionFactory;
import org.opends.server.tools.LDAPConnectionOptions;
import org.opends.server.tools.SSLConnectionFactory;
import org.opends.server.tools.SSLConnectionException;
import org.opends.server.admin.client.cli.SecureConnectionCliArgs;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.SelectableCertificateKeyManager;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ApplicationKeyManager;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

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
  private String bindPassword;
  private KeyManager keyManager;
  private TrustManager trustManager;

  // The SecureConnectionCliArgsList object.
  private SecureConnectionCliArgs secureArgsList = null;

  // Indicate if we need to display the heading
  private boolean isHeadingDisplayed = false;

  // the Console application
  private ConsoleApplication app;

  // Indicate if the truststore in in memory
  private boolean trustStoreInMemory = false;

  // The truststore to use for the SSL or STARTTLS connection
  private KeyStore truststore;

  private String keystorePath;

  private String keystorePassword;

  private String certifNickname;

  private String truststorePath;

  private String truststorePassword;

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
    boolean secureConnection =
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
      if (!isHeadingDisplayed)
      {
        app.println();
        app.println();
        app.println(INFO_LDAP_CONN_HEADING_CONNECTION_PARAMETERS.get());
        isHeadingDisplayed = true;
      }

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
      if (!isHeadingDisplayed)
      {
        app.println();
        app.println();
        app.println(INFO_LDAP_CONN_HEADING_CONNECTION_PARAMETERS.get());
        isHeadingDisplayed = true;
      }

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
        if (secureConnection && p.equals(Protocols.LDAP))
        {
          continue ;
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

    if (useSSL || useStartTLS)
    {
      // Get truststore info
      trustManager = getTrustManagerInternal();

      // Check if we need client side authentication
      keyManager = getKeyManagerInternal();
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
      if (!isHeadingDisplayed)
      {
        app.println();
        app.println();
        app.println(INFO_LDAP_CONN_HEADING_CONNECTION_PARAMETERS.get());
        isHeadingDisplayed = true;
      }

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
    final String tmpBindDN = bindDN;
    if (keyManager == null)
    {
      if (app.isInteractive() && !secureArgsList.bindDnArg.isPresent())
      {
        if (!isHeadingDisplayed)
        {
          app.println();
          app.println();
          app.println(INFO_LDAP_CONN_HEADING_CONNECTION_PARAMETERS.get());
          isHeadingDisplayed = true;
        }

        ValidationCallback<String> callback = new ValidationCallback<String>()
        {

          public String validate(ConsoleApplication app, String input)
              throws CLIException
          {
            String ninput = input.trim();
            if (ninput.length() == 0)
            {
              return tmpBindDN;
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
          bindDN = app.readValidatedInput(INFO_LDAP_CONN_PROMPT_BIND_DN
              .get(bindDN), callback);
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
      bindDN = null ;
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
          throw ArgumentExceptionFactory.missingBindPassword(bindDN);
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

        if (!isHeadingDisplayed)
        {
          app.println();
          app.println();
          app.println(INFO_LDAP_CONN_HEADING_CONNECTION_PARAMETERS.get());
          isHeadingDisplayed = true;
        }

        try
        {
          app.println();
          Message prompt = INFO_LDAPAUTH_PASSWORD_PROMPT.get(bindDN);
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
      if (!isHeadingDisplayed)
      {
        app.println();
        app.println();
        app.println(INFO_LDAP_CONN_HEADING_CONNECTION_PARAMETERS.get());
        isHeadingDisplayed = true;
      }

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
      if (!isHeadingDisplayed)
      {
        app.println();
        app.println();
        app.println(INFO_LDAP_CONN_HEADING_CONNECTION_PARAMETERS.get());
        isHeadingDisplayed = true;
      }

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
        if (!isHeadingDisplayed)
        {
          app.println();
          app.println();
          app.println(INFO_LDAP_CONN_HEADING_CONNECTION_PARAMETERS.get());
          isHeadingDisplayed = true;
        }

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
      if (!isHeadingDisplayed)
      {
        app.println();
        app.println();
        app.println(INFO_LDAP_CONN_HEADING_CONNECTION_PARAMETERS.get());
        isHeadingDisplayed = true;
      }

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

      if (!isHeadingDisplayed)
      {
        app.println();
        app.println();
        app.println(INFO_LDAP_CONN_HEADING_CONNECTION_PARAMETERS.get());
        isHeadingDisplayed = true;
      }

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
      if (!isHeadingDisplayed)
      {
        app.println();
        app.println();
        app.println(INFO_LDAP_CONN_HEADING_CONNECTION_PARAMETERS.get());
        isHeadingDisplayed = true;
      }

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
   * Gets the bind DN name that should be used for connections based on
   * this interaction.
   *
   * @return bind DN for connections
   */
  public String getBindDN() {
    return this.bindDN;
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
              // What should we do else?
              return false;
            }
          }

          // Update the trust manager
          trustManager = new ApplicationTrustManager(truststore);

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

}
