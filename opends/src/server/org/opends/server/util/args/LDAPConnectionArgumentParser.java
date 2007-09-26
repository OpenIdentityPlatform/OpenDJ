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
import static org.opends.server.util.ServerConstants.MAX_LINE_WIDTH;
import static org.opends.server.util.StaticUtils.wrapText;
import org.opends.server.util.cli.LDAPConnectionConsoleInteraction;
import org.opends.server.admin.client.cli.SecureConnectionCliArgs;
import org.opends.server.types.OpenDsException;

import java.util.LinkedList;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.PrintStream;

/**
 * Creates an argument parser pre-populated with arguments for specifying
 * information for openning and LDAPConnection an LDAP connection.
 */
public class LDAPConnectionArgumentParser extends ArgumentParser {

  private SecureConnectionCliArgs args;

  /**
   * Creates a new instance of this argument parser with no arguments.
   * Unnamed trailing arguments will not be allowed.
   *
   * @param  mainClassName               The fully-qualified name of the Java
   *                                     class that should be invoked to launch
   *                                     the program with which this argument
   *                                     parser is associated.
   * @param  toolDescription             A human-readable description for the
   *                                     tool, which will be included when
   *                                     displaying usage information.
   * @param  longArgumentsCaseSensitive  Indicates whether long arguments should
   * @param  argumentGroup               Group to which LDAP arguments will be
   *                                     added to the parser.  May be null to
   *                                     indicate that arguments should be
   *                                     added to the default group
   */
  public LDAPConnectionArgumentParser(String mainClassName,
                                      Message toolDescription,
                                      boolean longArgumentsCaseSensitive,
                                      ArgumentGroup argumentGroup) {
    super(mainClassName, toolDescription, longArgumentsCaseSensitive);
    addLdapConnectionArguments(argumentGroup);
  }

  /**
   * Creates a new instance of this argument parser with no arguments that may
   * or may not be allowed to have unnamed trailing arguments.
   *
   * @param  mainClassName               The fully-qualified name of the Java
   *                                     class that should be invoked to launch
   *                                     the program with which this argument
   *                                     parser is associated.
   * @param  toolDescription             A human-readable description for the
   *                                     tool, which will be included when
   *                                     displaying usage information.
   * @param  longArgumentsCaseSensitive  Indicates whether long arguments should
   *                                     be treated in a case-sensitive manner.
   * @param  allowsTrailingArguments     Indicates whether this parser allows
   *                                     unnamed trailing arguments to be
   *                                     provided.
   * @param  minTrailingArguments        The minimum number of unnamed trailing
   *                                     arguments that must be provided.  A
   *                                     value less than or equal to zero
   *                                     indicates that no minimum will be
   *                                     enforced.
   * @param  maxTrailingArguments        The maximum number of unnamed trailing
   *                                     arguments that may be provided.  A
   *                                     value less than or equal to zero
   *                                     indicates that no maximum will be
   *                                     enforced.
   * @param  trailingArgsDisplayName     The display name that should be used
   *                                     as a placeholder for unnamed trailing
   *                                     arguments in the generated usage
   *                                     information.
   * @param  argumentGroup               Group to which LDAP arguments will be
   *                                     added to the parser.  May be null to
   *                                     indicate that arguments should be
   *                                     added to the default group
   */
  public LDAPConnectionArgumentParser(String mainClassName,
                                      Message toolDescription,
                                      boolean longArgumentsCaseSensitive,
                                      boolean allowsTrailingArguments,
                                      int minTrailingArguments,
                                      int maxTrailingArguments,
                                      String trailingArgsDisplayName,
                                      ArgumentGroup argumentGroup) {
    super(mainClassName, toolDescription, longArgumentsCaseSensitive,
            allowsTrailingArguments, minTrailingArguments, maxTrailingArguments,
            trailingArgsDisplayName);
    addLdapConnectionArguments(argumentGroup);
  }

  /**
   * Indicates whether or not the user has indicated that they would like
   * to perform a remote operation based on the arguments.
   *
   * @return true if the user wants to perform a remote operation;
   *         false otherwise
   */
  public boolean argumentsPresent() {
    return args != null && args.argumentsPresent();
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
    return connect(this.args, out, err);
  }


  /**
   * Creates a new LDAPConnection and invokes a connect operation using
   * information provided in the parsed set of arguments that were provided
   * by the user.
   *
   * @param args with which to connect
   * @param out stream to write messages
   * @param err stream to write messages
   * @return LDAPConnection created by this class from parsed arguments
   * @throws LDAPConnectionException if there was a problem connecting
   *         to the server indicated by the input arguments
   * @throws ArgumentException if there was a problem processing the input
   *         arguments
   */
  private LDAPConnection connect(SecureConnectionCliArgs args,
                                PrintStream out, PrintStream err)
          throws LDAPConnectionException, ArgumentException
  {
    // If both a bind password and bind password file were provided, then return
    // an error.
    if (args.bindPasswordArg.isPresent() &&
            args.bindPasswordFileArg.isPresent())
    {
      Message message = ERR_LDAP_CONN_MUTUALLY_EXCLUSIVE_ARGUMENTS.get(
              args.bindPasswordArg.getLongIdentifier(),
              args.bindPasswordFileArg.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      throw new ArgumentException(message);
    }


    // If both a key store password and key store password file were provided,
    // then return an error.
    if (args.keyStorePasswordArg.isPresent() &&
            args.keyStorePasswordFileArg.isPresent())
    {
      Message message = ERR_LDAP_CONN_MUTUALLY_EXCLUSIVE_ARGUMENTS.get(
              args.keyStorePasswordArg.getLongIdentifier(),
              args.keyStorePasswordFileArg.getLongIdentifier());
      throw new ArgumentException(message);
    }


    // If both a trust store password and trust store password file were
    // provided, then return an error.
    if (args.trustStorePasswordArg.isPresent() &&
            args.trustStorePasswordFileArg.isPresent())
    {
      Message message = ERR_LDAP_CONN_MUTUALLY_EXCLUSIVE_ARGUMENTS.get(
              args.trustStorePasswordArg.getLongIdentifier(),
              args.trustStorePasswordFileArg.getLongIdentifier());
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
    if (args.useSSLArg.isPresent())
    {
      if (args.useStartTLSArg.isPresent())
      {
        Message message = ERR_LDAP_CONN_MUTUALLY_EXCLUSIVE_ARGUMENTS.get(
                args.useSSLArg.getLongIdentifier(),
                args.useSSLArg.getLongIdentifier());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        throw new ArgumentException(message);
      }
      else
      {
        connectionOptions.setUseSSL(true);
      }
    }
    else if (args.useStartTLSArg.isPresent())
    {
      connectionOptions.setStartTLS(true);
    }


    // If we should blindly trust any certificate, then install the appropriate
    // SSL connection factory.
    if (args.useSSLArg.isPresent() || args.useStartTLSArg.isPresent())
    {
      try
      {
        String clientAlias;
        if (args.certNicknameArg.isPresent())
        {
          clientAlias = args.certNicknameArg.getValue();
        }
        else
        {
          clientAlias = null;
        }

        SSLConnectionFactory sslConnectionFactory = new SSLConnectionFactory();
        sslConnectionFactory.init(args.trustAllArg.isPresent(),
                args.keyStorePathArg.getValue(),
                args.keyStorePasswordArg.getValue(),
                clientAlias,
                args.trustStorePathArg.getValue(),
                args.trustStorePasswordArg.getValue());

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
    if (args.saslOptionArg.isPresent())
    {
      String             mechanism = null;
      LinkedList<String> options   = new LinkedList<String>();

      for (String s : args.saslOptionArg.getValues())
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
    return connect(
            args.hostNameArg.getValue(),
            args.portArg.getIntValue(),
            args.bindDnArg.getValue(),
            args.bindPasswordArg.getValue(),
            connectionOptions, out, err);
  }

  /**
   * Creates a connection using a console interaction that will be used
   * to potientially interact with the user to prompt for necessary
   * information for establishing the connection.
   *
   * @param ui user interaction for prompting the user
   * @param out stream to write messages
   * @param err stream to write messages
   * @return LDAPConnection created by this class from parsed arguments
   * @throws LDAPConnectionException if there was a problem connecting
   *         to the server indicated by the input arguments
   */
  public LDAPConnection connect(LDAPConnectionConsoleInteraction ui,
                                PrintStream out, PrintStream err)
          throws LDAPConnectionException
  {
    LDAPConnection connection = null;
    try {
      ui.run();
      LDAPConnectionOptions options = new LDAPConnectionOptions();
      options.setVersionNumber(3);
      connection = connect(
              ui.getHostName(),
              ui.getPortNumber(),
              ui.getBindDN(),
              ui.getBindPassword(),
              ui.populateLDAPOptions(options), out, err);
    } catch (OpenDsException e) {
      err.println(e.getMessageObject());
    }
    return connection;
  }


  /**
   * Creates a connection from information provided.
   *
   * @param host of the server
   * @param port of the server
   * @param bindDN with which to connect
   * @param bindPw with which to connect
   * @param options with which to connect
   * @param out stream to write messages
   * @param err stream to write messages
   * @return LDAPConnection created by this class from parsed arguments
   * @throws LDAPConnectionException if there was a problem connecting
   *         to the server indicated by the input arguments
   */
  public LDAPConnection connect(String host, int port,
                                String bindDN, String bindPw,
                                LDAPConnectionOptions options,
                                PrintStream out,
                                PrintStream err)
          throws LDAPConnectionException
  {

    // Attempt to connect and authenticate to the Directory Server.
    AtomicInteger nextMessageID = new AtomicInteger(1);

    LDAPConnection connection = new LDAPConnection(
            host, port, options, out, err);

    connection.connectToHost(bindDN, bindPw, nextMessageID);

    return connection;
  }

  /**
   * Gets the arguments associated with this parser.
   *
   * @return arguments for this parser.
   */
  public SecureConnectionCliArgs getArguments() {
    return args;
  }

  private void addLdapConnectionArguments(ArgumentGroup argGroup) {
    args = new SecureConnectionCliArgs();
    try {
      LinkedHashSet<Argument> argSet = args.createGlobalArguments();
      for (Argument arg : argSet) {
        addArgument(arg, argGroup);
      }
    }
    catch (ArgumentException ae) {
      ae.printStackTrace(); // Should never happen
    }

  }

}
