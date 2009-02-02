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
 *      Copyright 2007-2009 Sun Microsystems, Inc.
 */

package org.opends.server.admin.client.cli;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.admin.client.cli.DsFrameworkCliReturnCode.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.tools.ToolConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.LinkedHashSet;

import javax.net.ssl.KeyManager;

import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.util.PasswordReader;
import org.opends.server.util.args.Argument;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentGroup;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.SubCommandArgumentParser;

/**
 * This is a commodity class that can be used to check the arguments required
 * to establish a secure connection in the command line.  It can be used
 * to generate an ApplicationTrustManager object based on the options provided
 * by the user in the command line.
 *
 */
public abstract class SecureConnectionCliParser extends SubCommandArgumentParser
{
  /**
   * The showUsage' global argument.
   */
  protected BooleanArgument showUsageArg = null;

  /**
   * The 'verbose' global argument.
   */
  protected BooleanArgument verboseArg = null;

  /**
   * The secure args list object.
   */
  protected SecureConnectionCliArgs secureArgsList ;

  /**
   * Argument indicating a properties file argument.
   */
  protected StringArgument  propertiesFileArg = null;

  /**
   * The argument which should be used to indicate that we will not
   * look for properties file.
   */
  protected BooleanArgument noPropertiesFileArg;

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * End Of Line.
   */
  public static String EOL = System.getProperty("line.separator");

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
      Message toolDescription, boolean longArgumentsCaseSensitive)
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
  protected String getBindPassword(String dn,
      OutputStream out, OutputStream err, StringArgument clearArg,
      FileBasedArgument fileArg)
  {
    if (clearArg.isPresent())
    {
      String bindPasswordValue = clearArg.getValue();
      if(bindPasswordValue != null && bindPasswordValue.equals("-"))
      {
        // read the password from the stdin.
        try
        {
          out.write(INFO_LDAPAUTH_PASSWORD_PROMPT.get(dn).getBytes());
          out.flush();
          char[] pwChars = PasswordReader.readPassword();
          bindPasswordValue = new String(pwChars);
        } catch(Exception ex)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, ex);
          }
          try
          {
            err.write(wrapText(ex.getMessage(), MAX_LINE_WIDTH).getBytes());
            err.write(EOL.getBytes());
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
        out.write(INFO_LDAPAUTH_PASSWORD_PROMPT.get(dn).toString().getBytes());
        out.flush();
        char[] pwChars = PasswordReader.readPassword();
        return new String(pwChars);
      }
      catch (Exception ex)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, ex);
        }
        try
        {
          err.write(wrapText(ex.getMessage(), MAX_LINE_WIDTH).getBytes());
          err.write(EOL.getBytes());
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
    return getBindPassword(dn, out, err, secureArgsList.bindPasswordArg,
        secureArgsList.bindPasswordFileArg);
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
    return getBindPassword(secureArgsList.bindPasswordArg,
        secureArgsList.bindPasswordFileArg);
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
  protected LinkedHashSet<Argument> createGlobalArguments(
      OutputStream outStream, boolean alwaysSSL)
  throws ArgumentException
  {
    secureArgsList = new SecureConnectionCliArgs(alwaysSSL);
    LinkedHashSet<Argument> set = secureArgsList.createGlobalArguments();

    showUsageArg = new BooleanArgument("showUsage", OPTION_SHORT_HELP,
        OPTION_LONG_HELP, INFO_DESCRIPTION_SHOWUSAGE.get());
    setUsageArgument(showUsageArg, outStream);
    set.add(showUsageArg);

    verboseArg = new BooleanArgument("verbose", OPTION_SHORT_VERBOSE,
        OPTION_LONG_VERBOSE, INFO_DESCRIPTION_VERBOSE.get());
    set.add(verboseArg);

    propertiesFileArg = new StringArgument("propertiesFilePath",
        null, OPTION_LONG_PROP_FILE_PATH,
        false, false, true, INFO_PROP_FILE_PATH_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_PROP_FILE_PATH.get());
    setFilePropertiesArgument(propertiesFileArg);
    set.add(propertiesFileArg);

    noPropertiesFileArg = new BooleanArgument(
        "noPropertiesFileArgument", null, OPTION_LONG_NO_PROP_FILE,
        INFO_DESCRIPTION_NO_PROP_FILE.get());
    setNoPropertiesFileArgument(noPropertiesFileArg);
    set.add(noPropertiesFileArg);


    return set;
  }

  /**
   * Initialize the global options with the provided set of arguments.
   * @param args the arguments to use to initialize the global options.
   * @throws ArgumentException if there is a conflict with the provided
   * arguments.
   */
  protected void initializeGlobalArguments(Collection<Argument> args)
  throws ArgumentException
  {
    initializeGlobalArguments(args, null);
  }


  /**
   * Initialize the global options with the provided set of arguments.
   * @param args the arguments to use to initialize the global options.
   * @param argGroup to which args will be added
   * @throws ArgumentException if there is a conflict with the provided
   * arguments.
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
   * @param buf the MessageBuilder to write the error messages.
   * @return return code.
   */
  public int validateGlobalOptions(MessageBuilder buf)
  {
    int ret = secureArgsList.validateGlobalOptions(buf) ;

    // Couldn't have at the same time properties file arg and
    // propertiesFileArg
    if (noPropertiesFileArg.isPresent()
        && propertiesFileArg.isPresent())
    {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
          noPropertiesFileArg.getLongIdentifier(), propertiesFileArg
              .getLongIdentifier());
      if (buf.length() > 0)
      {
        buf.append(EOL);
      }
      buf.append(message);
      ret = CONFLICTING_ARGS.getReturnCode();
    }

    return ret;
  }
  /**
   * Indication if provided global options are validate.
   *
   * @param err the stream to be used to print error message.
   * @return return code.
   */
  public int validateGlobalOptions(PrintStream err)
  {
    MessageBuilder buf = new MessageBuilder();
    int returnValue = validateGlobalOptions(buf);
    if (buf.length() > 0)
    {
      err.println(wrapText(buf.toString(), MAX_LINE_WIDTH));
    }
    return returnValue;
  }

  /**
   * Indicate if the verbose mode is required.
   *
   * @return True if verbose mode is required
   */
  public boolean isVerbose()
  {
    if (verboseArg.isPresent())
    {
      return true;
    }
    else
    {
      return false ;
    }
  }


  /**
   * Indicate if the SSL mode is required.
   *
   * @return True if SSL mode is required
   */
  public boolean useSSL()
  {
    return secureArgsList.useSSL();
  }

  /**
   * Indicate if the startTLS mode is required.
   *
   * @return True if startTLS mode is required
   */
  public boolean useStartTLS()
  {
    return secureArgsList.useStartTLS();
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
   * Handle KeyStore.
   *
   * @return The keyStore manager to be used for the command.
   */
  public KeyManager getKeyManager()
  {
    return secureArgsList.getKeyManager() ;
  }
}
