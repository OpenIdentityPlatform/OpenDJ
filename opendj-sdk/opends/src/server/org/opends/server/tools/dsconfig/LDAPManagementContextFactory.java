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
package org.opends.server.tools.dsconfig;


import static org.opends.messages.DSConfigMessages.*;
import static org.opends.messages.ToolMessages.*;

import java.io.File;
import java.io.FileInputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.util.LinkedHashSet;

import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import org.opends.admin.ads.util.ApplicationKeyManager;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.admin.client.AuthenticationException;
import org.opends.server.admin.client.AuthenticationNotSupportedException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.cli.DsFrameworkCliReturnCode;
import org.opends.server.admin.client.cli.SecureConnectionCliArgs;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.client.ldap.LDAPConnection;
import org.opends.server.admin.client.ldap.LDAPManagementContext;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.tools.ClientException;
import org.opends.server.util.SelectableCertificateKeyManager;
import org.opends.server.util.args.Argument;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.SubCommandArgumentParser;
import org.opends.server.util.cli.CLIException;
import org.opends.server.util.cli.ConsoleApplication;
import org.opends.server.util.cli.Menu;
import org.opends.server.util.cli.MenuBuilder;
import org.opends.server.util.cli.MenuResult;
import org.opends.server.util.cli.ValidationCallback;



/**
 * An LDAP management context factory.
 */
public final class LDAPManagementContextFactory implements
    ManagementContextFactory {

  // The SecureConnectionCliArgsList object.
  private SecureConnectionCliArgs secureArgsList = null;

  // The management context.
  private ManagementContext context = null;

  // Indicate if we need to display the heading
  private boolean isHeadingDisplayed = false;

  // the Console application
  private ConsoleApplication app;

  /**
   * Creates a new LDAP management context factory.
   */
  public LDAPManagementContextFactory() {
    // No implementation required.
  }



  /**
   * {@inheritDoc}
   */
  public ManagementContext getManagementContext(ConsoleApplication app)
      throws ArgumentException, ClientException {
    // Lazily create the LDAP management context.
    if (context == null) {
      this.app = app ;
      isHeadingDisplayed = false;

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

      if (app.isInteractive() && !secureConnection )
      {
        if (!isHeadingDisplayed)
        {
          app.println();
          app.println();
          app.println(INFO_DSCFG_HEADING_CONNECTION_PARAMETERS.get());
          isHeadingDisplayed = true;
        }

        try
        {
          app.println();
          secureConnection = app.confirmAction(
              INFO_DSCFG_PROMPT_SECURITY_USE_SECURE_CTX.get(),
              secureConnection);
        }
        catch (CLIException e)
        {
          // Should never happen.
          throw new RuntimeException(e);
        }
      }

      boolean useSSL = secureArgsList.useSSL();
      boolean useStartTSL = secureArgsList.useStartTLS();
      KeyManager keyManager = null ;
      TrustManager trustManager = null;
      boolean connectionTypeIsSet =
        (secureArgsList.useSSLArg.isPresent()
            ||
         secureArgsList.useStartTLSArg.isPresent() );
      if (app.isInteractive() && secureConnection && ! connectionTypeIsSet)
      {
        if (!isHeadingDisplayed)
        {
          app.println();
          app.println();
          app.println(INFO_DSCFG_HEADING_CONNECTION_PARAMETERS.get());
          isHeadingDisplayed = true;
        }

        // Construct the SSL/StartTLS menu.
        MenuBuilder<Boolean> builder = new MenuBuilder<Boolean>(app);
        builder.addNumberedOption(INFO_DSCFG_PROMPT_SECURITY_USE_SSL.get(),
            MenuResult.success(true));
        builder.addNumberedOption(INFO_DSCFG_PROMPT_SECURITY_USE_START_TSL
            .get(), MenuResult.success(false));
        builder.setDefault(INFO_DSCFG_PROMPT_SECURITY_USE_SSL.get(),
            MenuResult.success(true));

        Menu<Boolean> menu = builder.toMenu();
        try
        {
          MenuResult<Boolean> result = menu.run();
          if (result.isSuccess())
          {
            if (result.getValue())
            {
              useSSL = true;
            }
            else
            {
              useStartTSL = true;
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

      if (useSSL || useStartTSL)
      {
        // Get truststore info
        trustManager = getTrustManager();

        // Check if we need client side authentication
        keyManager = getKeyManager();
      }

      // Get the LDAP host.
      String hostName = secureArgsList.hostNameArg.getValue();
      final String tmpHostName = hostName;
      if (app.isInteractive() && !secureArgsList.hostNameArg.isPresent()) {
        if (!isHeadingDisplayed) {
          app.println();
          app.println();
          app.println(INFO_DSCFG_HEADING_CONNECTION_PARAMETERS.get());
          isHeadingDisplayed = true;
        }

        ValidationCallback<String> callback = new ValidationCallback<String>() {

          public String validate(ConsoleApplication app, String input)
              throws CLIException {
            String ninput = input.trim();
            if (ninput.length() == 0) {
              return tmpHostName;
            } else {
              try {
                InetAddress.getByName(ninput);
                return ninput;
              } catch (UnknownHostException e) {
                // Try again...
                app.println();
                app.println(ERR_DSCFG_BAD_HOST_NAME.get(ninput));
                app.println();
                return null;
              }
            }
          }

        };

        try {
          app.println();
          hostName = app.readValidatedInput(INFO_DSCFG_PROMPT_HOST_NAME
              .get(hostName), callback);
        } catch (CLIException e) {
          throw ArgumentExceptionFactory.unableToReadConnectionParameters(e);
        }
      }

      // Get the LDAP port.
      int portNumber ;
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
      if (app.isInteractive() && !secureArgsList.portArg.isPresent()) {
        if (!isHeadingDisplayed) {
          app.println();
          app.println();
          app.println(INFO_DSCFG_HEADING_CONNECTION_PARAMETERS.get());
          isHeadingDisplayed = true;
        }

        ValidationCallback<Integer> callback =
          new ValidationCallback<Integer>() {

          public Integer validate(ConsoleApplication app, String input)
              throws CLIException {
            String ninput = input.trim();
            if (ninput.length() == 0) {
              return tmpPortNumber;
            } else {
              try {
                int i = Integer.parseInt(ninput);
                if (i < 1 || i > 65535) {
                  throw new NumberFormatException();
                }
                return i;
              } catch (NumberFormatException e) {
                // Try again...
                app.println();
                app.println(ERR_DSCFG_BAD_PORT_NUMBER.get(ninput));
                app.println();
                return null;
              }
            }
          }

        };

        try {
          app.println();
          portNumber = app.readValidatedInput(INFO_DSCFG_PROMPT_PORT_NUMBER
              .get(portNumber), callback);
        } catch (CLIException e) {
          throw ArgumentExceptionFactory.unableToReadConnectionParameters(e);
        }
      }

      // Get the LDAP bind credentials.
      String bindDN = secureArgsList.bindDnArg.getValue();
      final String tmpBindDN = bindDN;
      if (keyManager == null)
      {
        if (app.isInteractive() && !secureArgsList.bindDnArg.isPresent())
        {
          if (!isHeadingDisplayed)
          {
            app.println();
            app.println();
            app.println(INFO_DSCFG_HEADING_CONNECTION_PARAMETERS.get());
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
            bindDN = app.readValidatedInput(INFO_DSCFG_PROMPT_BIND_DN
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

      String bindPassword = secureArgsList.bindPasswordArg.getValue();
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
            app.println(INFO_DSCFG_HEADING_CONNECTION_PARAMETERS.get());
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

      // Do we have a secure connection ?
      LDAPConnection conn ;
      if (useSSL)
      {
        InitialLdapContext ctx = null;
        String ldapsUrl = "ldaps://" + hostName + ":" + portNumber;
        try
        {
          ctx = ConnectionUtils.createLdapsContext(ldapsUrl, bindDN,
              bindPassword, ConnectionUtils.getDefaultLDAPTimeout(), null,
              trustManager, keyManager);
          conn = JNDIDirContextAdaptor.adapt(ctx);
        }
        catch (NamingException e)
        {
          Message message = ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(
              hostName, String.valueOf(portNumber));
          throw new ClientException(LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR,
              message) ;
        }
      }
      else if (useStartTSL)
      {
        InitialLdapContext ctx = null;
        String ldapUrl = "ldap://" + hostName + ":" + portNumber;
        try
        {
          ctx = ConnectionUtils.createStartTLSContext(ldapUrl, bindDN,
              bindPassword, ConnectionUtils.getDefaultLDAPTimeout(), null,
              trustManager, keyManager, null);
          conn = JNDIDirContextAdaptor.adapt(ctx);
        }
        catch (NamingException e)
        {
          Message message = ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(
              hostName, String.valueOf(portNumber));
          throw new ClientException(LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR,
              message) ;
        }
      }
      else
      {
        // Create the management context.
        try
        {
          conn = JNDIDirContextAdaptor.simpleBind(hostName, portNumber,
              bindDN, bindPassword);
        }
        catch (AuthenticationNotSupportedException e)
        {
          Message message = ERR_DSCFG_ERROR_LDAP_SIMPLE_BIND_NOT_SUPPORTED
              .get();
          throw new ClientException(LDAPResultCode.AUTH_METHOD_NOT_SUPPORTED,
              message);
        }
        catch (AuthenticationException e)
        {
          Message message = ERR_DSCFG_ERROR_LDAP_SIMPLE_BIND_FAILED
              .get(bindDN);
          throw new ClientException(LDAPResultCode.INVALID_CREDENTIALS,
              message);
        }
        catch (CommunicationException e)
        {
          Message message = ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(
              hostName, String.valueOf(portNumber));
          throw new ClientException(LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR,
              message);
        }
      }
      context = LDAPManagementContext.createFromContext(conn);
    }
    return context;
  }



  /**
   * {@inheritDoc}
   */
  public void registerGlobalArguments(SubCommandArgumentParser parser)
      throws ArgumentException {
    // Create the global arguments.
    secureArgsList = new SecureConnectionCliArgs();
    LinkedHashSet<Argument> args = secureArgsList.createGlobalArguments();


    // Register the global arguments.
    for (Argument arg : args)
    {
      parser.addGlobalArgument(arg);
    }

  }



  /**
   * {@inheritDoc}
   */
  public void validateGlobalArguments() throws ArgumentException {
    // Make sure that the user didn't specify any conflicting
    // arguments.
    MessageBuilder buf = new MessageBuilder();
    int v = secureArgsList.validateGlobalOptions(buf);
    if (v != DsFrameworkCliReturnCode.SUCCESSFUL_NOP.getReturnCode())
    {
      throw new ArgumentException(buf.toMessage());
    }
  }

  /**
   * Get the trust manager.
   *
   * @return The trust manager based on CLI args on interactive prompt.
   *
   * @throws ArgumentException If an error occurs when getting args values.
   */
  private ApplicationTrustManager getTrustManager()
  throws ArgumentException
  {
    boolean trustAll = secureArgsList.trustAllArg.isPresent();
    if (app.isInteractive() && !secureArgsList.trustAllArg.isPresent())
    {
      if (!isHeadingDisplayed)
      {
        app.println();
        app.println();
        app.println(INFO_DSCFG_HEADING_CONNECTION_PARAMETERS.get());
        isHeadingDisplayed = true;
      }

      try
      {
        app.println();
        trustAll = app.confirmAction(INFO_DSCFG_PROMPT_SECURITY_USE_TRUST_ALL
            .get(), false);
      }
      catch (CLIException e)
      {
        // Should never happen.
        throw new RuntimeException(e);
      }
    }

    // Trust everything, so no trust manager
    if (trustAll)
    {
      return null;
    }

    // If we not trust all server certificates, we have to get info
    // about truststore. First get the truststore path.
    String truststorePath = secureArgsList.trustStorePathArg.getValue();
    if (app.isInteractive() && !secureArgsList.trustStorePathArg.isPresent())
    {
      if (!isHeadingDisplayed)
      {
        app.println();
        app.println();
        app.println(INFO_DSCFG_HEADING_CONNECTION_PARAMETERS.get());
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
            return null;
          }
        }
      };

      try
      {
        app.println();
        truststorePath = app.readValidatedInput(
            INFO_DSCFG_PROMPT_SECURITY_TRUSTSTORE_PATH.get(), callback);
      }
      catch (CLIException e)
      {
        throw ArgumentExceptionFactory.unableToReadConnectionParameters(e);
      }
    }

    // Then the truststore password.
    String truststorePassword = secureArgsList.trustStorePasswordArg.getValue();

    if (secureArgsList.trustStorePasswordFileArg.isPresent())
    {
      // Read from file if it exists.
      truststorePassword = secureArgsList.trustStorePasswordFileArg.getValue();

      if (app.isInteractive() && (truststorePassword == null))
      {
          throw ArgumentExceptionFactory
            .missingValueInPropertyArgument(secureArgsList.
                trustStorePasswordArg.getName());
      }
    }
    else if (truststorePassword == null || truststorePassword.equals("-"))
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
          app.println(INFO_DSCFG_HEADING_CONNECTION_PARAMETERS.get());
          isHeadingDisplayed = true;
        }

        try
        {
          app.println();
          Message prompt = INFO_DSCFG_PROMPT_SECURITY_TRUSTSTORE_PASSWORD
              .get(truststorePath);
          truststorePassword = app.readPassword(prompt);
        }
        catch (Exception e)
        {
          throw ArgumentExceptionFactory.unableToReadConnectionParameters(e);
        }
      }
    }
    // We'we got all the information to get the trustore manager
    try
    {
      FileInputStream fos = new FileInputStream(truststorePath);
      KeyStore truststore = KeyStore.getInstance(KeyStore.getDefaultType());
      if (truststorePassword != null)
      {
        truststore.load(fos, truststorePassword.toCharArray());
      }
      else
      {
        truststore.load(fos, null);
      }
      fos.close();
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
   *
   * @throws ArgumentException If an error occurs when getting args values.
   */
  private KeyManager getKeyManager()
  throws ArgumentException
  {
    // Do we need client side authentication ?
    // If one of the client side authentication args is set, we assume that we
    // need client side authentication.
    boolean weDontKnowThatWeNeedKeystore =
      ! ( secureArgsList.keyStorePathArg.isPresent()
          ||
          secureArgsList.keyStorePasswordArg.isPresent()
          ||
          secureArgsList.keyStorePasswordFileArg.isPresent()
          ||
          secureArgsList.certNicknameArg.isPresent()
          );

    // We don't have specific key manager parameter set and
    // we are not in interactive mode ; just return null
    if (weDontKnowThatWeNeedKeystore && !app.isInteractive())
    {
      return null;
    }

    if (app.isInteractive() && weDontKnowThatWeNeedKeystore)
    {
      boolean needKeystore = false ;
      if (!isHeadingDisplayed)
      {
        app.println();
        app.println();
        app.println(INFO_DSCFG_HEADING_CONNECTION_PARAMETERS.get());
        isHeadingDisplayed = true;
      }

      try
      {
        app.println();
        needKeystore = app.confirmAction(
            INFO_DSCFG_PROMPT_SECURITY_KEYSTORE_NEEDED.get(), needKeystore);
        if (! needKeystore )
        {
          return null;
        }
      }
      catch (CLIException e)
      {
        // Should never happen.
        throw new RuntimeException(e);
      }
    }

    // Get info about keystore. First get the keystore path.
    String keystorePath = secureArgsList.keyStorePathArg.getValue();
    if (app.isInteractive() && !secureArgsList.keyStorePathArg.isPresent())
    {
      if (!isHeadingDisplayed)
      {
        app.println();
        app.println();
        app.println(INFO_DSCFG_HEADING_CONNECTION_PARAMETERS.get());
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
            return null;
          }
        }
      };

      try
      {
        app.println();
        keystorePath = app.readValidatedInput(
            INFO_DSCFG_PROMPT_SECURITY_KEYSTORE_PATH.get(), callback);
      }
      catch (CLIException e)
      {
        throw ArgumentExceptionFactory.unableToReadConnectionParameters(e);
      }
    }

    // Then the keystore password.
    String keystorePassword = secureArgsList.keyStorePasswordArg.getValue();

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
        app.println(INFO_DSCFG_HEADING_CONNECTION_PARAMETERS.get());
        isHeadingDisplayed = true;
      }

      try
      {
        app.println();
        Message prompt = INFO_DSCFG_PROMPT_SECURITY_KEYSTORE_PASSWORD
            .get(keystorePath);
        keystorePassword = app.readPassword(prompt);
      }
      catch (Exception e)
      {
        throw ArgumentExceptionFactory.unableToReadConnectionParameters(e);
      }
    }

    // finally the certificate name, if needed.
    String certifNickname = secureArgsList.certNicknameArg.getValue();
    if (app.isInteractive() && !secureArgsList.certNicknameArg.isPresent())
    {
      if (!isHeadingDisplayed)
      {
        app.println();
        app.println();
        app.println(INFO_DSCFG_HEADING_CONNECTION_PARAMETERS.get());
        isHeadingDisplayed = true;
      }
      ValidationCallback<String> callback = new ValidationCallback<String>() {

        public String validate(ConsoleApplication app, String input)
            throws CLIException {
          return  input.trim();
        }
      };

      try {
        app.println();
        certifNickname = app.readValidatedInput(
            INFO_DSCFG_PROMPT_SECURITY_CERTIFICATE_NAME.get(), callback);
      } catch (CLIException e) {
        throw ArgumentExceptionFactory.unableToReadConnectionParameters(e);
      }
    }

    // We'we got all the information to get the keystore manager
    try
    {
      FileInputStream fos = new FileInputStream(keystorePath);
      KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
      keystore.load(fos, keystorePassword.toCharArray());
      fos.close();
      ApplicationKeyManager akm = new ApplicationKeyManager(keystore,
          keystorePassword.toCharArray());

      if (certifNickname.length() != 0)
      {
        return new SelectableCertificateKeyManager(akm, certifNickname);
      }
      else
      {
        return akm ;
      }
    }
    catch (Exception e)
    {
      throw ArgumentExceptionFactory.unableToReadConnectionParameters(e);
    }
  }
}
