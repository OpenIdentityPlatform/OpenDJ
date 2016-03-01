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
 * Copyright 2007-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */

package org.opends.server.admin.client.cli;

import static com.forgerock.opendj.cli.CommonArguments.*;
import static com.forgerock.opendj.cli.ReturnCode.*;
import static com.forgerock.opendj.cli.Utils.*;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.admin.ads.util.ApplicationTrustManager;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentGroup;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.StringArgument;
import com.forgerock.opendj.cli.SubCommandArgumentParser;

/**
 * This is a commodity class that can be used to check the arguments required to
 * establish a secure connection in the command line. It can be used to generate
 * an ApplicationTrustManager object based on the options provided by the user
 * in the command line.
 */
public abstract class SecureConnectionCliParser extends SubCommandArgumentParser
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The 'verbose' global argument. */
  protected BooleanArgument verboseArg;
  /** The secure args list object. */
  protected SecureConnectionCliArgs secureArgsList;
  /** Argument indicating a properties file argument. */
  protected StringArgument propertiesFileArg;
  /** The argument which should be used to indicate that we will not look for properties file. */
  protected BooleanArgument noPropertiesFileArg;

  /**
   * Creates a new instance of this argument parser with no arguments.
   *
   * @param mainClassName
   *          The fully-qualified name of the Java class that should
   *          be invoked to launch the program with which this
   *          argument parser is associated.
   * @param toolDescription
   *          A human-readable description for the tool, which will be
   *          included when displaying usage information.
   * @param longArgumentsCaseSensitive
   *          Indicates whether subcommand and long argument names
   *          should be treated in a case-sensitive manner.
   */
  protected SecureConnectionCliParser(String mainClassName,
      LocalizableMessage toolDescription, boolean longArgumentsCaseSensitive)
  {
    super(mainClassName, toolDescription, longArgumentsCaseSensitive);
  }

  /**
   * Get the bindDN which has to be used for the command.
   *
   * @return The bindDN specified by the command line argument, or the
   *         default value, if not specified.
   */
  public String getBindDN()
  {
    return secureArgsList.getBindDN();
  }

  /**
   * Returns the Administrator UID provided in the command-line.
   * @return the Administrator UID provided in the command-line.
   */
  public String getAdministratorUID()
  {
    return secureArgsList.getAdministratorUID();
  }

  /**
   * Gets the password which has to be used for the command without prompting
   * the user.  If no password was specified, return null.
   *
   * @return The password stored into the specified file on by the
   *         command line argument, or null it if not specified.
   */
  public String getBindPassword()
  {
    return getBindPassword(secureArgsList.getBindPasswordArg(), secureArgsList.getBindPasswordFileArg());
  }

  /**
   * Initialize Global option.
   *
   * @param outStream
   *          The output stream used for the usage.
   * @param alwaysSSL If true, always use the SSL connection type. In this case,
   * the arguments useSSL and startTLS are not present.
   *
   * @throws ArgumentException
   *           If there is a problem with any of the parameters used
   *           to create this argument.
   * @return a ArrayList with the options created.
   */
  protected Set<Argument> createGlobalArguments(OutputStream outStream, boolean alwaysSSL) throws ArgumentException
  {
    secureArgsList = new SecureConnectionCliArgs(alwaysSSL);
    Set<Argument> set = secureArgsList.createGlobalArguments();

    /* The 'showUsage' global argument. */
    final BooleanArgument showUsageArg = showUsageArgument();
    setUsageArgument(showUsageArg, outStream);
    set.add(showUsageArg);

    verboseArg = verboseArgument();
    set.add(verboseArg);

    propertiesFileArg = propertiesFileArgument();
    setFilePropertiesArgument(propertiesFileArg);
    set.add(propertiesFileArg);

    noPropertiesFileArg = noPropertiesFileArgument();
    setNoPropertiesFileArgument(noPropertiesFileArg);
    set.add(noPropertiesFileArg);

    return set;
  }

  /**
   * Initialize the global options with the provided set of arguments.
   *
   * @param args the arguments to use to initialize the global options.
   * @throws ArgumentException if there is a conflict with the provided
   *                           arguments.
   */
  protected void initializeGlobalArguments(Collection<Argument> args) throws ArgumentException
  {
    initializeGlobalArguments(args, null);
  }

  /**
   * Initialize the global options with the provided set of arguments.
   *
   * @param args     the arguments to use to initialize the global options.
   * @param argGroup to which args will be added
   * @throws ArgumentException if there is a conflict with the provided
   *                           arguments.
   */
  protected void initializeGlobalArguments(
          Collection<Argument> args,
          ArgumentGroup argGroup)
  throws ArgumentException
  {
    for (Argument arg : args)
    {
      addGlobalArgument(arg, argGroup);
    }

    // Set the propertiesFile argument
    setFilePropertiesArgument(propertiesFileArg);
  }

  /**
   * Get the host name which has to be used for the command.
   *
   * @return The host name specified by the command line argument, or
   *         the default value, if not specified.
   */
  public String getHostName()
  {
    return secureArgsList.getHostName();
  }

  /**
   * Get the port which has to be used for the command.
   *
   * @return The port specified by the command line argument, or the
   *         default value, if not specified.
   */
  public String getPort()
  {
    return secureArgsList.getPort();
  }

  /**
   * Indication if provided global options are validate.
   *
   * @param buf The {@link LocalizableMessageBuilder} to write the error message.
   * @return return code.
   */
  public int validateGlobalOptions(final LocalizableMessageBuilder buf)
  {
    final int ret = secureArgsList.validateGlobalOptions(buf) ;
    if (appendErrorMessageIfArgumentsConflict(buf, noPropertiesFileArg, propertiesFileArg))
    {
      return CONFLICTING_ARGS.get();
    }
    return ret;
  }

  /**
   * Indication if provided global options are validate.
   *
   * @param err The stream to be used to print error message.
   * @return return code.
   */
  public int validateGlobalOptions(PrintStream err)
  {
    LocalizableMessageBuilder buf = new LocalizableMessageBuilder();
    int returnValue = validateGlobalOptions(buf);
    printWrappedText(err, buf.toString());
    return returnValue;
  }

  /**
   * Indicate if the verbose mode is required.
   *
   * @return True if verbose mode is required
   */
  public boolean isVerbose()
  {
    return verboseArg.isPresent();
  }

  /**
   * Handle TrustStore.
   *
   * @return The trustStore manager to be used for the command.
   */
  public ApplicationTrustManager getTrustManager()
  {
    return secureArgsList.getTrustManager();
  }

  /**
   * Returns the timeout to be used to connect in milliseconds.
   * The method must be called after parsing the arguments.
   *
   * @return the timeout to be used to connect in milliseconds or {@code 0} if there is no timeout.
   * @throws IllegalStateException if the method is called before parsing the arguments.
   */
  public int getConnectTimeout()throws IllegalStateException
  {
    try
    {
      return secureArgsList.getConnectTimeoutArg().getIntValue();
    }
    catch (ArgumentException ae)
    {
      throw new IllegalStateException("Argument parser is not parsed: "+ae, ae);
    }
  }
}
