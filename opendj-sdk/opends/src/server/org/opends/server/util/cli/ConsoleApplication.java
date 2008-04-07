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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.util.cli;



import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.DSConfigMessages.*;
import static org.opends.messages.QuickSetupMessages.*;
import static org.opends.messages.UtilityMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NamingException;
import javax.naming.NoPermissionException;
import javax.naming.ldap.InitialLdapContext;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.admin.ads.util.OpendsCertificateException;
import org.opends.messages.Message;
import org.opends.quicksetup.util.Utils;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.tools.ClientException;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.PasswordReader;


/**
 * This class provides an abstract base class which can be used as the
 * basis of a console-based application.
 */
public abstract class ConsoleApplication {

  /**
   * A null reader.
   */
  private static final class NullReader extends Reader {

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
      // Do nothing.
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
      return -1;
    }
  }

  // The error stream which this application should use.
  private final PrintStream err;

  // The input stream reader which this application should use.
  private final BufferedReader in;

  // The output stream which this application should use.
  private final PrintStream out;

  /**
   *  The maximum number of times we try to confirm.
   */
  protected final static int CONFIRMATION_MAX_TRIES = 5;

  /**
   * Creates a new console application instance.
   *
   * @param in
   *          The application input stream.
   * @param out
   *          The application output stream.
   * @param err
   *          The application error stream.
   */
  protected ConsoleApplication(BufferedReader in, PrintStream out,
      PrintStream err) {
    if (in != null) {
      this.in = in;
    } else {
      this.in = new BufferedReader(new NullReader());
    }

    if (out != null) {
      this.out = out;
    } else {
      this.out = NullOutputStream.printStream();
    }

    if (err != null) {
      this.err = out;
    } else {
      this.err = NullOutputStream.printStream();
    }
  }



  /**
   * Creates a new console application instance.
   *
   * @param in
   *          The application input stream.
   * @param out
   *          The application output stream.
   * @param err
   *          The application error stream.
   */
  protected ConsoleApplication(InputStream in, OutputStream out,
      OutputStream err) {
    if (in != null) {
      this.in = new BufferedReader(new InputStreamReader(in));
    } else {
      this.in = new BufferedReader(new NullReader());
    }

    if (out != null) {
      this.out = new PrintStream(out);
    } else {
      this.out = NullOutputStream.printStream();
    }

    if (err != null) {
      this.err = new PrintStream(err);
    } else {
      this.err = NullOutputStream.printStream();
    }
  }



  /**
   * Interactively confirms whether a user wishes to perform an
   * action. If the application is non-interactive, then the provided
   * default is returned automatically.
   *
   * @param prompt
   *          The prompt describing the action.
   * @param defaultValue
   *          The default value for the confirmation message. This
   *          will be returned if the application is non-interactive
   *          or if the user just presses return.
   * @return Returns <code>true</code> if the user wishes the action
   *         to be performed, or <code>false</code> if they refused,
   *         or if an exception occurred.
   * @throws CLIException
   *           If the user's response could not be read from the
   *           console for some reason.
   */
  public final boolean confirmAction(Message prompt, final boolean defaultValue)
      throws CLIException {
    if (!isInteractive()) {
      return defaultValue;
    }

    final Message yes = INFO_GENERAL_YES.get();
    final Message no = INFO_GENERAL_NO.get();
    final Message errMsg = ERR_CONSOLE_APP_CONFIRM.get(yes, no);
    prompt = INFO_MENU_PROMPT_CONFIRM.get(prompt, yes, no, defaultValue ? yes
        : no);

    ValidationCallback<Boolean> validator = new ValidationCallback<Boolean>() {

      public Boolean validate(ConsoleApplication app, String input) {
        String ninput = input.toLowerCase().trim();
        if (ninput.length() == 0) {
          return defaultValue;
        } else if (no.toString().startsWith(ninput)) {
          return false;
        } else if (yes.toString().startsWith(ninput)) {
          return true;
        } else {
          // Try again...
          app.println();
          app.println(errMsg);
          app.println();
        }

        return null;
      }
    };

    return readValidatedInput(prompt, validator, CONFIRMATION_MAX_TRIES);
  }



  /**
   * Gets the application error stream.
   *
   * @return Returns the application error stream.
   */
  public final PrintStream getErrorStream() {
    return err;
  }



  /**
   * Gets the application input stream.
   *
   * @return Returns the application input stream.
   */
  public final BufferedReader getInputStream() {
    return in;
  }



  /**
   * Gets the application output stream.
   *
   * @return Returns the application output stream.
   */
  public final PrintStream getOutputStream() {
    return out;
  }



  /**
   * Indicates whether or not the user has requested advanced mode.
   *
   * @return Returns <code>true</code> if the user has requested
   *         advanced mode.
   */
  public abstract boolean isAdvancedMode();



  /**
   * Indicates whether or not the user has requested interactive
   * behavior.
   *
   * @return Returns <code>true</code> if the user has requested
   *         interactive behavior.
   */
  public abstract boolean isInteractive();



  /**
   * Indicates whether or not this console application is running in
   * its menu-driven mode. This can be used to dictate whether output
   * should go to the error stream or not. In addition, it may also
   * dictate whether or not sub-menus should display a cancel option
   * as well as a quit option.
   *
   * @return Returns <code>true</code> if this console application
   *         is running in its menu-driven mode.
   */
  public abstract boolean isMenuDrivenMode();



  /**
   * Indicates whether or not the user has requested quiet output.
   *
   * @return Returns <code>true</code> if the user has requested
   *         quiet output.
   */
  public abstract boolean isQuiet();



  /**
   * Indicates whether or not the user has requested script-friendly
   * output.
   *
   * @return Returns <code>true</code> if the user has requested
   *         script-friendly output.
   */
  public abstract boolean isScriptFriendly();



  /**
   * Indicates whether or not the user has requested verbose output.
   *
   * @return Returns <code>true</code> if the user has requested
   *         verbose output.
   */
  public abstract boolean isVerbose();



  /**
   * Interactively prompts the user to press return to continue. This
   * method should be called in situations where a user needs to be
   * given a chance to read some documentation before continuing
   * (continuing may cause the documentation to be scrolled out of
   * view).
   */
  public final void pressReturnToContinue() {
    Message msg = INFO_MENU_PROMPT_RETURN_TO_CONTINUE.get();
    try {
      readLineOfInput(msg);
    } catch (CLIException e) {
      // Ignore the exception - applications don't care.
    }
  }



  /**
   * Displays a blank line to the error stream.
   */
  public final void println() {
    err.println();
  }



  /**
   * Displays a message to the error stream.
   *
   * @param msg
   *          The message.
   */
  public final void println(Message msg) {
    err.println(wrapText(msg, MAX_LINE_WIDTH));
  }


  /**
   * Displays a message to the error stream.
   *
   * @param msg
   *          The message.
   */
  public final void print(Message msg) {
    err.print(wrapText(msg, MAX_LINE_WIDTH));
  }

  /**
   * Displays a blank line to the output stream if we are not in quiet mode.
   */
  public final void printlnProgress() {
    if (!isQuiet())
    {
      out.println();
    }
  }


  /**
   * Displays a message to the output stream if we are not in quiet mode.
   *
   * @param msg
   *          The message.
   */
  public final void printProgress(Message msg) {
    if (!isQuiet())
    {
      out.print(msg);
    }
  }


  /**
   * Displays a message to the error stream indented by the specified
   * number of columns.
   *
   * @param msg
   *          The message.
   * @param indent
   *          The number of columns to indent.
   */
  public final void println(Message msg, int indent) {
    err.println(wrapText(msg, MAX_LINE_WIDTH, indent));
  }



  /**
   * Displays a message to the error stream if verbose mode is
   * enabled.
   *
   * @param msg
   *          The verbose message.
   */
  public final void printVerboseMessage(Message msg) {
    if (isVerbose() || isInteractive()) {
      err.println(wrapText(msg, MAX_LINE_WIDTH));
    }
  }



  /**
   * Interactively retrieves a line of input from the console.
   *
   * @param prompt
   *          The prompt.
   * @return Returns the line of input, or <code>null</code> if the
   *         end of input has been reached.
   * @throws CLIException
   *           If the line of input could not be retrieved for some
   *           reason.
   */
  public final String readLineOfInput(Message prompt) throws CLIException {
    if (prompt != null)
    {
      err.print(wrapText(prompt + " ", MAX_LINE_WIDTH));
    }
    try {
      String s = in.readLine();
      if (s == null) {
        throw CLIException
            .adaptInputException(new EOFException("End of input"));
      } else {
        return s;
      }
    } catch (IOException e) {
      throw CLIException.adaptInputException(e);
    }
  }


  /**
   * Commodity method that interactively prompts (on error output) the user to
   * provide a string value.  Any non-empty string will be allowed (the empty
   * string will indicate that the default should be used, if there is one).
   *
   * @param  prompt        The prompt to present to the user.
   * @param  defaultValue  The default value to assume if the user presses ENTER
   *                       without typing anything, or <CODE>null</CODE> if
   *                       there should not be a default and the user must
   *                       explicitly provide a value.
   *
   * @throws CLIException
   *           If the line of input could not be retrieved for some
   *           reason.
   * @return  The string value read from the user.
   */
  public String readInput(Message prompt, String defaultValue)
  throws CLIException {
    while (true) {
      if (defaultValue != null) {
        prompt = INFO_PROMPT_SINGLE_DEFAULT.get(prompt.toString(),
            defaultValue);
      }
      String response = readLineOfInput(prompt);

      if ("".equals(response)) {
        if (defaultValue == null) {
          print(INFO_ERROR_EMPTY_RESPONSE.get());
        } else {
          return defaultValue;
        }
      } else {
        return response;
      }
    }
  }

  /**
   * Commodity method that interactively prompts (on error output) the user to
   * provide a string value.  Any non-empty string will be allowed (the empty
   * string will indicate that the default should be used, if there is one).
   * If an error occurs a message will be logged to the provided logger.
   *
   * @param  prompt        The prompt to present to the user.
   * @param  defaultValue  The default value to assume if the user presses ENTER
   *                       without typing anything, or <CODE>null</CODE> if
   *                       there should not be a default and the user must
   *                       explicitly provide a value.
   *
   * @param logger the Logger to be used to log the error message.
   * @return  The string value read from the user.
   */
  public String readInput(Message prompt, String defaultValue, Logger logger)
  {
    String s = defaultValue;
    try
    {
      s = readInput(prompt, defaultValue);
    }
    catch (CLIException ce)
    {
      logger.log(Level.WARNING, "Error reading input: "+ce, ce);
    }
    return s;
  }

  /**
   * Interactively retrieves a password from the console.
   *
   * @param prompt
   *          The password prompt.
   * @return Returns the password.
   * @throws CLIException
   *           If the password could not be retrieved for some reason.
   */
  public final String readPassword(Message prompt) throws CLIException {
    err.print(wrapText(prompt + " ", MAX_LINE_WIDTH));
    char[] pwChars;
    try {
      pwChars = PasswordReader.readPassword();
    } catch (Exception e) {
      throw CLIException.adaptInputException(e);
    }
    return new String(pwChars);
  }

  /**
   * Commodity method that interactively retrieves a password from the
   * console. If there is an error an error message is logged to the provided
   * Logger and <CODE>null</CODE> is returned.
   *
   * @param prompt
   *          The password prompt.
   * @param logger the Logger to be used to log the error message.
   * @return Returns the password.
   */
  protected final String readPassword(Message prompt, Logger logger)
  {
    String pwd = null;
    try
    {
      pwd = readPassword(prompt);
    }
    catch (CLIException ce)
    {
      logger.log(Level.WARNING, "Error reading input: "+ce, ce);
    }
    return pwd;
  }

  /**
   * Interactively retrieves a port value from the console.
   *
   * @param prompt
   *          The port prompt.
   * @param defaultValue
   *          The port default value.
   * @return Returns the port.
   * @throws CLIException
   *           If the port could not be retrieved for some reason.
   */
  public final int readPort(Message prompt, final int defaultValue)
  throws CLIException
  {
    ValidationCallback<Integer> callback = new ValidationCallback<Integer>()
    {
      public Integer validate(ConsoleApplication app, String input)
          throws CLIException
      {
        String ninput = input.trim();
        if (ninput.length() == 0)
        {
          return defaultValue;
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

    if (defaultValue != -1) {
      prompt = INFO_PROMPT_SINGLE_DEFAULT.get(prompt.toString(),
          String.valueOf(defaultValue));
    }

    return readValidatedInput(prompt, callback);
  }

  /**
   * Interactively prompts for user input and continues until valid
   * input is provided.
   *
   * @param <T>
   *          The type of decoded user input.
   * @param prompt
   *          The interactive prompt which should be displayed on each
   *          input attempt.
   * @param validator
   *          An input validator responsible for validating and
   *          decoding the user's response.
   * @return Returns the decoded user's response.
   * @throws CLIException
   *           If an unexpected error occurred which prevented
   *           validation.
   */
  public final <T> T readValidatedInput(Message prompt,
      ValidationCallback<T> validator) throws CLIException {
    while (true) {
      String response = readLineOfInput(prompt);
      T value = validator.validate(this, response);
      if (value != null) {
        return value;
      }
    }
  }

  /**
   * Interactively prompts for user input and continues until valid
   * input is provided.
   *
   * @param <T>
   *          The type of decoded user input.
   * @param prompt
   *          The interactive prompt which should be displayed on each
   *          input attempt.
   * @param validator
   *          An input validator responsible for validating and
   *          decoding the user's response.
   * @param maxTries
   *          The maximum number of tries that we can make.
   * @return Returns the decoded user's response.
   * @throws CLIException
   *           If an unexpected error occurred which prevented
   *           validation or if the maximum number of tries was reached.
   */
  public final <T> T readValidatedInput(Message prompt,
      ValidationCallback<T> validator, int maxTries) throws CLIException {
    int nTries = 0;
    while (nTries < maxTries) {
      String response = readLineOfInput(prompt);
      T value = validator.validate(this, response);
      if (value != null) {
        return value;
      }
      nTries++;
    }
    throw new CLIException(ERR_TRIES_LIMIT_REACHED.get(maxTries));
  }

  /**
   * Commodity method that interactively confirms whether a user wishes to
   * perform an action. If the application is non-interactive, then the provided
   * default is returned automatically.  If there is an error an error message
   * is logged to the provided Logger and the defaul value is returned.
   *
   * @param prompt
   *          The prompt describing the action.
   * @param defaultValue
   *          The default value for the confirmation message. This
   *          will be returned if the application is non-interactive
   *          or if the user just presses return.
   * @param logger the Logger to be used to log the error message.
   * @return Returns <code>true</code> if the user wishes the action
   *         to be performed, or <code>false</code> if they refused.
   * @throws CLIException if the user did not provide valid answer after
   *         a certain number of tries
   *         (ConsoleApplication.CONFIRMATION_MAX_TRIES)
   */
  protected final boolean askConfirmation(Message prompt, boolean defaultValue,
      Logger logger) throws CLIException
  {
    boolean v = defaultValue;

    boolean done = false;
    int nTries = 0;

    while (!done && (nTries < CONFIRMATION_MAX_TRIES))
    {
      nTries++;
      try
      {
        v = confirmAction(prompt, defaultValue);
        done = true;
      }
      catch (CLIException ce)
      {
        if (ce.getMessageObject().getDescriptor().equals(
            ERR_CONFIRMATION_TRIES_LIMIT_REACHED) ||
            ce.getMessageObject().getDescriptor().equals(
                ERR_TRIES_LIMIT_REACHED))
        {
          throw ce;
        }
        logger.log(Level.WARNING, "Error reading input: "+ce, ce);
//      Try again...
        println();
      }
    }

    if (!done)
    {
      // This means we reached the maximum number of tries
      throw new CLIException(ERR_CONFIRMATION_TRIES_LIMIT_REACHED.get(
          CONFIRMATION_MAX_TRIES));
    }
    return v;
  }

  /**
   * Returns an InitialLdapContext using the provided parameters.  We try
   * to guarantee that the connection is able to read the configuration.
   * @param host the host name.
   * @param port the port to connect.
   * @param useSSL whether to use SSL or not.
   * @param useStartTLS whether to use StartTLS or not.
   * @param bindDn the bind dn to be used.
   * @param pwd the password.
   * @param trustManager the trust manager.
   * @return an InitialLdapContext connected.
   * @throws NamingException if there was an error establishing the connection.
   */
  protected InitialLdapContext createAdministrativeContext(String host,
      int port, boolean useSSL, boolean useStartTLS, String bindDn, String pwd,
      ApplicationTrustManager trustManager)
  throws NamingException
  {
    InitialLdapContext ctx;
    String ldapUrl = ConnectionUtils.getLDAPUrl(host, port, useSSL);
    if (useSSL)
    {
      ctx = Utils.createLdapsContext(ldapUrl, bindDn, pwd,
          Utils.getDefaultLDAPTimeout(), null, trustManager);
    }
    else if (useStartTLS)
    {
      ctx = Utils.createStartTLSContext(ldapUrl, bindDn, pwd,
          Utils.getDefaultLDAPTimeout(), null, trustManager,
          null);
    }
    else
    {
      ctx = Utils.createLdapContext(ldapUrl, bindDn, pwd,
          Utils.getDefaultLDAPTimeout(), null);
    }
    if (!ConnectionUtils.connectedAsAdministrativeUser(ctx))
    {
      throw new NoPermissionException(
          ERR_NOT_ADMINISTRATIVE_USER.get().toString());
    }
    return ctx;
  }

  /**
   * Creates an Initial LDAP Context interacting with the user if the
   * application is interactive.
   * @param ci the LDAPConnectionConsoleInteraction object that is assumed
   * to have been already run.
   * @return the initial LDAP context or <CODE>null</CODE> if the user did
   * not accept to trust the certificates.
   * @throws ClientException if there was an error establishing the connection.
   */
  protected InitialLdapContext createInitialLdapContextInteracting(
      LDAPConnectionConsoleInteraction ci) throws ClientException
  {
    // Interact with the user though the console to get
    // LDAP connection information
    String hostName = ConnectionUtils.getHostNameForLdapUrl(ci.getHostName());
    Integer portNumber = ci.getPortNumber();
    String bindDN = ci.getBindDN();
    String bindPassword = ci.getBindPassword();
    TrustManager trustManager = ci.getTrustManager();
    KeyManager keyManager = ci.getKeyManager();

    InitialLdapContext ctx;

    if (ci.useSSL())
    {
      String ldapsUrl = "ldaps://" + hostName + ":" + portNumber;
      while (true)
      {
        try
        {
          ctx = ConnectionUtils.createLdapsContext(ldapsUrl, bindDN,
              bindPassword, ConnectionUtils.getDefaultLDAPTimeout(), null,
              trustManager, keyManager);
          ctx.reconnect(null);
          break;
        }
        catch (NamingException e)
        {
          if ( isInteractive() && ci.isTrustStoreInMemory())
          {
            if ((e.getRootCause() != null)
                && (e.getRootCause().getCause()
                    instanceof OpendsCertificateException))
            {
              OpendsCertificateException oce =
                (OpendsCertificateException) e.getRootCause().getCause();
              String authType = null;
              if (trustManager instanceof ApplicationTrustManager)
              {
                ApplicationTrustManager appTrustManager =
                  (ApplicationTrustManager)trustManager;
                authType = appTrustManager.getLastRefusedAuthType();
              }
                if (ci.checkServerCertificate(oce.getChain(), authType,
                    hostName))
                {
                  // If the certificate is trusted, update the trust manager.
                  trustManager = ci.getTrustManager();

                  // Try to connect again.
                  continue ;
                }
                else
                {
                  // Assume user cancelled.
                  return null;
                }
            }
            else
            {
              Message message = ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(
                  hostName, String.valueOf(portNumber));
              throw new ClientException(
                  LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR, message);
            }
          }
          Message message = ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(
              hostName, String.valueOf(portNumber));
          throw new ClientException(
              LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR, message);
        }
      }
    }
    else if (ci.useStartTLS())
    {
      String ldapUrl = "ldap://" + hostName + ":" + portNumber;
      while (true)
      {
        try
        {
          ctx = ConnectionUtils.createStartTLSContext(ldapUrl, bindDN,
              bindPassword, ConnectionUtils.getDefaultLDAPTimeout(), null,
              trustManager, keyManager, null);
          ctx.reconnect(null);
          break;
        }
        catch (NamingException e)
        {
          if ( isInteractive() && ci.isTrustStoreInMemory())
          {
            if ((e.getRootCause() != null)
                && (e.getRootCause().getCause()
                    instanceof OpendsCertificateException))
            {
              String authType = null;
              if (trustManager instanceof ApplicationTrustManager)
              {
                ApplicationTrustManager appTrustManager =
                  (ApplicationTrustManager)trustManager;
                authType = appTrustManager.getLastRefusedAuthType();
              }
              OpendsCertificateException oce =
                (OpendsCertificateException) e.getRootCause().getCause();
                if (ci.checkServerCertificate(oce.getChain(), authType,
                    hostName))
                {
                  // If the certificate is trusted, update the trust manager.
                  trustManager = ci.getTrustManager();

                  // Try to connect again.
                  continue ;
                }
                else
                {
                  // Assume user cancelled.
                  return null;
                }
            }
            else
            {
              Message message = ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(
                  hostName, String.valueOf(portNumber));
              throw new ClientException(
                  LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR, message);
            }
          }
          Message message = ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(
              hostName, String.valueOf(portNumber));
          throw new ClientException(
              LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR, message);
        }
      }
    }
    else
    {
      String ldapUrl = "ldap://" + hostName + ":" + portNumber;
      while (true)
      {
        try
        {
          ctx = ConnectionUtils.createLdapContext(ldapUrl, bindDN,
              bindPassword, ConnectionUtils.getDefaultLDAPTimeout(), null);
          ctx.reconnect(null);
          break;
        }
        catch (NamingException e)
        {
          if ( isInteractive() && ci.isTrustStoreInMemory())
          {
            if ((e.getRootCause() != null)
                && (e.getRootCause().getCause()
                    instanceof OpendsCertificateException))
            {
              String authType = null;
              if (trustManager instanceof ApplicationTrustManager)
              {
                ApplicationTrustManager appTrustManager =
                  (ApplicationTrustManager)trustManager;
                authType = appTrustManager.getLastRefusedAuthType();
              }
              OpendsCertificateException oce =
                (OpendsCertificateException) e.getRootCause().getCause();
                if (ci.checkServerCertificate(oce.getChain(), authType,
                    hostName))
                {
                  // If the certificate is trusted, update the trust manager.
                  trustManager = ci.getTrustManager();

                  // Try to connect again.
                  continue ;
                }
                else
                {
                  // Assume user cancelled.
                  return null;
                }
            }
            else
            {
              Message message = ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(
                  hostName, String.valueOf(portNumber));
              throw new ClientException(
                  LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR, message);
            }
          }
          Message message = ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(
              hostName, String.valueOf(portNumber));
          throw new ClientException(
              LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR, message);
        }
      }
    }
    return ctx;
  }
}
