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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.admin.client.cli;

import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.messages.AdminMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.tools.ToolConstants.*;
import static org.opends.server.util.ServerConstants.MAX_LINE_WIDTH;
import static org.opends.server.util.StaticUtils.wrapText;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;
import javax.net.ssl.KeyManager;

import org.opends.admin.ads.ADSContextException;
import org.opends.admin.ads.util.ApplicationKeyManager;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.util.PasswordReader;
import org.opends.server.util.SelectableCertificateKeyManager;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.SubCommand;
import org.opends.server.util.args.SubCommandArgumentParser;

import static org.opends.server.admin.client.cli.DsFrameworkCliReturnCode.*;

/**
 * This class will parser CLI arguments.
 */
public class DsFrameworkCliParser extends SubCommandArgumentParser
{
  /**
   * End Of Line.
   */
  private String EOL = System.getProperty("line.separator");

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The showUsage' global argument.
   */
  private BooleanArgument showUsageArg = null;

  /**
   * The 'useSSLArg' global argument.
   */
  private BooleanArgument useSSLArg = null;

  /**
   * The 'startTLSArg' global argument.
   */
  private BooleanArgument startTLSArg = null;

  /**
   * The 'hostName' global argument.
   */
  private StringArgument hostNameArg = null;

  /**
   * The 'port' global argument.
   */
  private IntegerArgument portArg = null;

  /**
   * The 'binDN' global argument.
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
   * The 'verbose' global argument.
   */
  private BooleanArgument verboseArg = null;

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
   * The Logger.
   */
  static private final Logger LOG =
    Logger.getLogger(DsFrameworkCliParser.class.getName());

  /**
   * The different CLI group.
   */
  public HashSet<DsFrameworkCliSubCommandGroup> cliGroup;



  /**
   * Creates a new instance of this subcommand argument parser with no
   * arguments.
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
  public DsFrameworkCliParser(String mainClassName, String toolDescription,
      boolean longArgumentsCaseSensitive)
  {
    super(mainClassName, toolDescription, longArgumentsCaseSensitive);
    cliGroup = new HashSet<DsFrameworkCliSubCommandGroup>();
  }

  /**
   * Initialize the parser with the Global options and subcommands.
   *
   * @param outStream
   *          The output stream to use for standard output, or <CODE>null</CODE>
   *          if standard output is not needed.
   * @throws ArgumentException
   *           If there is a problem with any of the parameters used
   *           to create this argument.
   */
  public void initializeParser(OutputStream outStream)
      throws ArgumentException
  {
    // Global parameters
    initializeGlobalOption(outStream);

    // ads  Group cli
    cliGroup.add(new DsFrameworkCliAds());

    // Server-group Group cli
    cliGroup.add(new DsFrameworkCliServerGroup());

    // Server Group cli
    cliGroup.add(new DsFrameworkCliServer());

    // User Admin cli
    cliGroup.add(new DsFrameworkCliGlobalAdmin());

    // Initialization
    Comparator<SubCommand> c = new Comparator<SubCommand>() {

      public int compare(SubCommand o1, SubCommand o2) {
        return o1.getName().compareTo(o2.getName());
      }
    };

    SortedSet<SubCommand> allSubCommands = new TreeSet<SubCommand>(c);

    for (DsFrameworkCliSubCommandGroup oneCli : cliGroup)
    {
      oneCli.initializeCliGroup(this, verboseArg);
      Set<SubCommand> oneCliSubCmds = oneCli.getSubCommands() ;
      allSubCommands.addAll(oneCliSubCmds);

      // register group help
      String grpName = oneCli.getGroupName();
      String option = OPTION_LONG_HELP + "-" + grpName;
      BooleanArgument arg = new BooleanArgument(option, null, option,
          MSGID_DSCFG_DESCRIPTION_SHOW_GROUP_USAGE, grpName);
      addGlobalArgument(arg);
      arg.setHidden(oneCli.isHidden());
      TreeSet<SubCommand> subCmds = new TreeSet<SubCommand>(c);
      subCmds.addAll(oneCliSubCmds);
      setUsageGroupArgument(arg, subCmds);
    }

    // Register the --help-all argument.
    String option = OPTION_LONG_HELP + "-all";
    BooleanArgument arg = new BooleanArgument(option, null, option,
        MSGID_DSCFG_DESCRIPTION_SHOW_GROUP_USAGE_ALL);

    addGlobalArgument(arg);
    setUsageGroupArgument(arg, allSubCommands);

  }

  /**
   * Initialize Global option.
   *
   * @param outStream
   *          The output stream used for the usage.
   * @throws ArgumentException
   *           If there is a problem with any of the parameters used
   *           to create this argument.
   */
  private void initializeGlobalOption(OutputStream outStream)
  throws ArgumentException
  {
    showUsageArg = new BooleanArgument("showUsage", OPTION_SHORT_HELP,
        OPTION_LONG_HELP, MSGID_DESCRIPTION_SHOWUSAGE);
    addGlobalArgument(showUsageArg);
    setUsageArgument(showUsageArg, outStream);

    useSSLArg = new BooleanArgument("useSSL", OPTION_SHORT_USE_SSL,
        OPTION_LONG_USE_SSL, MSGID_DESCRIPTION_USE_SSL);
    addGlobalArgument(useSSLArg);

    startTLSArg = new BooleanArgument("startTLS", OPTION_SHORT_START_TLS,
        OPTION_LONG_START_TLS,
        MSGID_DESCRIPTION_START_TLS);
    addGlobalArgument(startTLSArg);

    hostNameArg = new StringArgument("host", OPTION_SHORT_HOST,
        OPTION_LONG_HOST, false, false, true, OPTION_VALUE_HOST, "localhost",
        null, MSGID_DESCRIPTION_HOST);
    addGlobalArgument(hostNameArg);

    portArg = new IntegerArgument("port", OPTION_SHORT_PORT, OPTION_LONG_PORT,
        false, false, true, OPTION_VALUE_PORT, 389, null,
        MSGID_DESCRIPTION_PORT);
    addGlobalArgument(portArg);

    bindDnArg = new StringArgument("bindDN", OPTION_SHORT_BINDDN,
        OPTION_LONG_BINDDN, false, false, true, OPTION_VALUE_BINDDN,
        "cn=Directory Manager", null, MSGID_DESCRIPTION_BINDDN);
    addGlobalArgument(bindDnArg);

    bindPasswordArg = new StringArgument("bindPassword",
        OPTION_SHORT_BINDPWD, OPTION_LONG_BINDPWD, false, false, true,
        OPTION_VALUE_BINDPWD, null, null, MSGID_DESCRIPTION_BINDPASSWORD);
    addGlobalArgument(bindPasswordArg);

    bindPasswordFileArg = new FileBasedArgument("bindPasswordFile",
        OPTION_SHORT_BINDPWD_FILE, OPTION_LONG_BINDPWD_FILE, false, false,
        OPTION_VALUE_BINDPWD_FILE, null, null,
        MSGID_DESCRIPTION_BINDPASSWORDFILE);
    addGlobalArgument(bindPasswordFileArg);

    trustAllArg = new BooleanArgument("trustAll", 'X', "trustAll",
        MSGID_DESCRIPTION_TRUSTALL);
    addGlobalArgument(trustAllArg);

    trustStorePathArg = new StringArgument("trustStorePath",
        OPTION_SHORT_TRUSTSTOREPATH, OPTION_LONG_TRUSTSTOREPATH, false,
        false, true, OPTION_VALUE_TRUSTSTOREPATH, null, null,
        MSGID_DESCRIPTION_TRUSTSTOREPATH);
    addGlobalArgument(trustStorePathArg);

    trustStorePasswordArg = new StringArgument("trustStorePassword", null,
        OPTION_LONG_TRUSTSTORE_PWD, false, false, true,
        OPTION_VALUE_TRUSTSTORE_PWD, null, null,
        MSGID_DESCRIPTION_TRUSTSTOREPASSWORD);
    addGlobalArgument(trustStorePasswordArg);

    trustStorePasswordFileArg = new FileBasedArgument("truststorepasswordfile",
        OPTION_SHORT_TRUSTSTORE_PWD_FILE, OPTION_LONG_TRUSTSTORE_PWD_FILE,
        false, false, OPTION_VALUE_TRUSTSTORE_PWD_FILE, null, null,
        MSGID_DESCRIPTION_TRUSTSTOREPASSWORD_FILE);
    addGlobalArgument(trustStorePasswordFileArg);

    keyStorePathArg = new StringArgument("keyStorePath",
        OPTION_SHORT_KEYSTOREPATH, OPTION_LONG_KEYSTOREPATH, false, false,
        true, OPTION_VALUE_KEYSTOREPATH, null, null,
        MSGID_DESCRIPTION_KEYSTOREPATH);
    addGlobalArgument(keyStorePathArg);

    keyStorePasswordArg = new StringArgument("keyStorePassword", null,
        OPTION_LONG_KEYSTORE_PWD, false, false, true,
        OPTION_VALUE_KEYSTORE_PWD, null, null,
        MSGID_DESCRIPTION_KEYSTOREPASSWORD);
    addGlobalArgument(keyStorePasswordArg);

    keyStorePasswordFileArg = new FileBasedArgument("keystorepasswordfile",
        OPTION_SHORT_KEYSTORE_PWD_FILE, OPTION_LONG_KEYSTORE_PWD_FILE, false,
        false, OPTION_VALUE_KEYSTORE_PWD_FILE, null, null,
        MSGID_DESCRIPTION_KEYSTOREPASSWORD_FILE);
    addGlobalArgument(keyStorePasswordFileArg);

    certNicknameArg = new StringArgument("certnickname", 'N', "certNickname",
        false, false, true, "{nickname}", null, null,
        MSGID_DESCRIPTION_CERT_NICKNAME);
    addGlobalArgument(certNicknameArg);

    verboseArg = new BooleanArgument("verbose", 'v', "verbose",
        MSGID_DESCRIPTION_VERBOSE);
    addGlobalArgument(verboseArg);
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
    else
    {
      return hostNameArg.getDefaultValue();
    }
  }

  /**
   * Get the port which has to be used for the command.
   *
   * @return The port specified by the command line argument, or the
   *         default value, if not specified.
   */
  public String getPort()
  {
    if (portArg.isPresent())
    {
      return portArg.getValue();
    }
    else
    {
      return portArg.getDefaultValue();
    }
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
   * @return The password stored into the specified file on by the
   *         command line argument, or prompts it if not specified.
   */
  public String getBindPassword(String dn, OutputStream out, OutputStream err)
  {
    if (bindPasswordArg.isPresent())
    {
      String bindPasswordValue = bindPasswordArg.getValue();
      if(bindPasswordValue != null && bindPasswordValue.equals("-"))
      {
        // read the password from the stdin.
        try
        {
          out.write(getMessage(MSGID_LDAPAUTH_PASSWORD_PROMPT, dn).getBytes());
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
    if (bindPasswordFileArg.isPresent())
    {
      return bindPasswordFileArg.getValue();
    }
    else
    {
      // read the password from the stdin.
      try
      {
        out.write(getMessage(MSGID_LDAPAUTH_PASSWORD_PROMPT, dn).getBytes());
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
   * Handle the subcommand.
   * @param  outStream         The output stream to use for standard output.
   * @param  errStream         The output stream to use for standard error.
   *
   * @return the return code
   * @throws ADSContextException
   *           If there is a problem with when trying to perform the
   *           operation.
   * @throws ArgumentException
   *           If there is a problem with any of the parameters used
   *           to execute this subcommand.
   */
  public DsFrameworkCliReturnCode performSubCommand(OutputStream outStream,
      OutputStream errStream)
    throws ADSContextException, ArgumentException
  {
    SubCommand subCmd = getSubCommand();

    for (DsFrameworkCliSubCommandGroup oneCli : cliGroup)
    {
      if (oneCli.isSubCommand(subCmd))
      {
        return oneCli.performSubCommand( subCmd, outStream, errStream);
      }
    }

    // Should never occurs: If we are here, it means that the code to
    // handle to subcommand is not yet written.
    return ERROR_UNEXPECTED;
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
    if (useSSLArg.isPresent())
    {
      return true;
    }
    else
    {
      return false ;
    }
  }

  /**
   * Indicate if the startTLS mode is required.
   *
   * @return True if startTLS mode is required
   */
  public boolean startTLS()
  {
    if (startTLSArg.isPresent())
    {
      return true;
    }
    else
    {
      return false ;
    }
  }

  /**
   * Handle TrustStore.
   *
   * @return The trustStore manager to be used for the command.
   */
  public ApplicationTrustManager getTrustManager()
  {
    ApplicationTrustManager truststoreManager = null ;
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
      try
      {
        FileInputStream fos = new FileInputStream(trustStorePathArg.getValue());
        String trustStorePasswordStringValue = null;
        char[] trustStorePasswordValue = null;
        if (trustStorePasswordArg.isPresent())
        {
          trustStorePasswordStringValue = trustStorePasswordArg.getValue();
        }
        else if (trustStorePasswordFileArg.isPresent())
        {
          trustStorePasswordStringValue = trustStorePasswordFileArg.getValue();
        }

        if (trustStorePasswordStringValue !=  null)
        {
          trustStorePasswordStringValue = System
              .getProperty("javax.net.ssl.trustStorePassword");
        }


        if (trustStorePasswordStringValue !=  null)
        {
          trustStorePasswordValue = trustStorePasswordStringValue.toCharArray();
        }

        truststore = KeyStore.getInstance(KeyStore.getDefaultType());
        truststore.load(fos, trustStorePasswordValue);
        fos.close();
      }
      catch (KeyStoreException e)
      {
        // Nothing to do: if this occurs we will systematically refuse the
        // certificates.  Maybe we should avoid this and be strict, but we are
        // in a best effort mode.
        LOG.log(Level.WARNING, "Error with the truststore", e);
      }
      catch (NoSuchAlgorithmException e)
      {
        // Nothing to do: if this occurs we will systematically refuse the
        // certificates.  Maybe we should avoid this and be strict, but we are
        // in a best effort mode.
        LOG.log(Level.WARNING, "Error with the truststore", e);
      }
      catch (CertificateException e)
      {
        // Nothing to do: if this occurs we will systematically refuse the
        // certificates.  Maybe we should avoid this and be strict, but we are
        // in a best effort mode.
        LOG.log(Level.WARNING, "Error with the truststore", e);
      }
      catch (IOException e)
      {
        // Nothing to do: if this occurs we will systematically refuse the
        // certificates.  Maybe we should avoid this and be strict, but we are
        // in a best effort mode.
        LOG.log(Level.WARNING, "Error with the truststore", e);
      }
    }
    truststoreManager = new ApplicationTrustManager(truststore);
    return truststoreManager;
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
      try
      {
        FileInputStream fos = new FileInputStream(keyStorePathArg.getValue());
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
        fos.close();
      }
      catch (KeyStoreException e)
      {
        // Nothing to do: if this occurs we will systematically refuse
        // the
        // certificates. Maybe we should avoid this and be strict, but
        // we are in a best effort mode.
        LOG.log(Level.WARNING, "Error with the keystore", e);
      }
      catch (NoSuchAlgorithmException e)
      {
        // Nothing to do: if this occurs we will systematically refuse
        // the
        // certificates. Maybe we should avoid this and be strict, but
        // we are
        // in a best effort mode.
        LOG.log(Level.WARNING, "Error with the keystore", e);
      }
      catch (CertificateException e)
      {
        // Nothing to do: if this occurs we will systematically refuse
        // the
        // certificates. Maybe we should avoid this and be strict, but
        // we are
        // in a best effort mode.
        LOG.log(Level.WARNING, "Error with the keystore", e);
      }
      catch (IOException e)
      {
        // Nothing to do: if this occurs we will systematically refuse
        // the
        // certificates. Maybe we should avoid this and be strict, but
        // we are
        // in a best effort mode.
        LOG.log(Level.WARNING, "Error with the keystore", e);
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
   * Indication if provided global options are validate.
   *
   * @param err the stream to be used to print error message.
   * @return return code.
   */
  public int validateGlobalOption(PrintStream err)
  {
    // Couldn't have at the same time bindPassword and bindPasswordFile
    if(bindPasswordArg.isPresent() && bindPasswordFileArg.isPresent())
    {
      int    msgID   = MSGID_TOOL_CONFLICTING_ARGS;
      String message = getMessage(msgID, bindPasswordArg.getLongIdentifier(),
                                  bindPasswordFileArg.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return CONFLICTING_ARGS.getReturnCode();
    }

    // Couldn't have at the same time trustAll and
    // trustStore related arg
    if (trustAllArg.isPresent() && trustStorePathArg.isPresent())
    {
      int msgID = MSGID_TOOL_CONFLICTING_ARGS;
      String message = getMessage(msgID, trustAllArg.getLongIdentifier(),
          trustStorePathArg.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return CONFLICTING_ARGS.getReturnCode();
    }
    if (trustAllArg.isPresent() && trustStorePasswordArg.isPresent())
    {
      int msgID = MSGID_TOOL_CONFLICTING_ARGS;
      String message = getMessage(msgID, trustAllArg.getLongIdentifier(),
          trustStorePasswordArg.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return CONFLICTING_ARGS.getReturnCode();
    }
    if (trustAllArg.isPresent() && trustStorePasswordFileArg.isPresent())
    {
      int msgID = MSGID_TOOL_CONFLICTING_ARGS;
      String message = getMessage(msgID, trustAllArg.getLongIdentifier(),
          trustStorePasswordFileArg.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return CONFLICTING_ARGS.getReturnCode();
    }

    // Couldn't have at the same time trustStorePasswordArg and
    // trustStorePasswordFileArg
    if (trustStorePasswordArg.isPresent()
        && trustStorePasswordFileArg.isPresent())
    {
      int msgID = MSGID_TOOL_CONFLICTING_ARGS;
      String message = getMessage(msgID, trustStorePasswordArg
          .getLongIdentifier(), trustStorePasswordFileArg.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return CONFLICTING_ARGS.getReturnCode();
    }

    // Couldn't have at the same time startTLSArg and
    // useSSLArg
    if (startTLSArg.isPresent()
        && useSSLArg.isPresent())
    {
      int msgID = MSGID_TOOL_CONFLICTING_ARGS;
      String message = getMessage(msgID, startTLSArg
          .getLongIdentifier(), useSSLArg.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return CONFLICTING_ARGS.getReturnCode();
    }

    return SUCCESSFUL_NOP.getReturnCode();
  }


  /**
   * Get the InitialLdapContext that has to be used for the ADS.
   * @param  out         The output stream to use for standard output.
   * @param  err         The output stream to use for standard error.
   *
   * @return The InitialLdapContext that has to be used for the ADS.
   */
  public InitialLdapContext getContext(OutputStream out, OutputStream err)
  {
    // Get connection parameters
    String host = null ;
    String port = null;
    String dn   = null ;
    String pwd  = null;
    InitialLdapContext ctx = null;

    // Get connection parameters
    host = getHostName();
    port = getPort();
    dn   = getBindDN();
    pwd  = getBindPassword(dn, out, err);

    // Try to connect
    if (useSSL())
    {
      String ldapsUrl = "ldaps://" + host + ":" + port;
      try
      {
        ctx = ConnectionUtils.createLdapsContext(ldapsUrl, dn, pwd,
            ConnectionUtils.getDefaultLDAPTimeout(), null,getTrustManager(),
            getKeyManager());
      }
      catch (NamingException e)
      {
        int msgID = MSGID_ADMIN_CANNOT_CONNECT_TO_ADS;
        String message = getMessage(msgID, host);

        try
        {
          err.write(wrapText(message, MAX_LINE_WIDTH).getBytes());
          err.write(EOL.getBytes());
        }
        catch (IOException e1)
        {
        }
        return null;
      }
    }
    else if (startTLS())
    {
      String ldapUrl = "ldap://" + host + ":" + port;
      try
      {
        ctx = ConnectionUtils.createStartTLSContext(ldapUrl, dn, pwd,
            ConnectionUtils.getDefaultLDAPTimeout(), null, getTrustManager(),
            getKeyManager(), null);
      }
      catch (NamingException e)
      {
        int msgID = MSGID_ADMIN_CANNOT_CONNECT_TO_ADS;
        String message = getMessage(msgID, host);

        try
        {
          err.write(wrapText(message, MAX_LINE_WIDTH).getBytes());
          err.write(EOL.getBytes());
        }
        catch (IOException e1)
        {
        }
        return null;
      }
    }
    else
    {
      String ldapUrl = "ldap://" + host + ":" + port;
      try
      {
        ctx = ConnectionUtils.createLdapContext(ldapUrl, dn, pwd,
            ConnectionUtils.getDefaultLDAPTimeout(), null);
      }
      catch (NamingException e)
      {
        int msgID = MSGID_ADMIN_CANNOT_CONNECT_TO_ADS;
        String message = getMessage(msgID, host);

        try
        {
          err.write(wrapText(message, MAX_LINE_WIDTH).getBytes());
          err.write(EOL.getBytes());
        }
        catch (IOException e1)
        {
        }
        return null;
      }
    }
    return ctx;
  }
}
