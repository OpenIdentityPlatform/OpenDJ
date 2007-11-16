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

package org.opends.guitools.replicationcli;

import static org.opends.guitools.replicationcli.ReplicationCliReturnCode.*;
import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.QuickSetupMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.quicksetup.util.Utils.getFirstValue;
import static org.opends.quicksetup.util.Utils.getThrowableMsg;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NameAlreadyBoundException;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.net.ssl.TrustManager;

import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ADSContextException;
import org.opends.admin.ads.ReplicaDescriptor;
import org.opends.admin.ads.ServerDescriptor;
import org.opends.admin.ads.SuffixDescriptor;
import org.opends.admin.ads.TopologyCache;
import org.opends.admin.ads.TopologyCacheException;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.admin.ads.util.ServerLoader;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.messages.UtilityMessages;
import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.QuickSetupLog;
import org.opends.quicksetup.ReturnCode;
import org.opends.quicksetup.event.ProgressUpdateEvent;
import org.opends.quicksetup.event.ProgressUpdateListener;
import org.opends.quicksetup.installer.Installer;
import org.opends.quicksetup.installer.InstallerHelper;
import org.opends.quicksetup.installer.PeerNotFoundException;
import org.opends.quicksetup.installer.offline.OfflineInstaller;
import org.opends.quicksetup.util.PlainTextProgressMessageFormatter;
import org.opends.quicksetup.util.Utils;
import org.opends.server.admin.AttributeTypePropertyDefinition;
import org.opends.server.admin.ClassLoaderProvider;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.DefaultBehaviorException;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.client.ldap.LDAPManagementContext;
import org.opends.server.admin.std.client.*;
import org.opends.server.admin.std.meta.*;
import org.opends.server.core.DirectoryServer;
import org.opends.server.tools.ClientException;
import org.opends.server.tools.ToolConstants;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.types.NullOutputStream;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.SetupUtils;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.cli.CLIException;
import org.opends.server.util.cli.ConsoleApplication;
import org.opends.server.util.cli.LDAPConnectionConsoleInteraction;
import org.opends.server.util.table.TableBuilder;
import org.opends.server.util.table.TextTablePrinter;

/**
 * This class provides a tool that can be used to enable and disable replication
 * and also to initialize the contents of a replicated suffix with the contents
 * of another suffix.  It also allows to display the replicated status of the
 * different base DNs of the servers that are registered in the ADS.
 */
public class ReplicationCliMain extends ConsoleApplication
{
  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME = ReplicationCliMain.class.getName();

  /** Prefix for log files. */
  static public final String LOG_FILE_PREFIX = "opends-replication-";

  /** Suffix for log files. */
  static public final String LOG_FILE_SUFFIX = ".log";

  private boolean forceNonInteractive;

  private static final Logger LOG =
    Logger.getLogger(ReplicationCliMain.class.getName());

  // The argument parser to be used.
  private ReplicationCliArgumentParser argParser;
  private LDAPConnectionConsoleInteraction ci = null;
  // The message formatter
  PlainTextProgressMessageFormatter formatter =
      new PlainTextProgressMessageFormatter();

  /**
   * Constructor for the ReplicationCliMain object.
   *
   * @param out the print stream to use for standard output.
   * @param err the print stream to use for standard error.
   * @param in the input stream to use for standard input.
   */
  public ReplicationCliMain(PrintStream out, PrintStream err, InputStream in)
  {
    super(in, out, err);
  }

  /**
   * The main method for the replication tool.
   *
   * @param args the command-line arguments provided to this program.
   */

  public static void main(String[] args)
  {
    int retCode = mainCLI(args, true, System.out, System.err, System.in);

    System.exit(retCode);
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the replication tool.
   *
   * @param args the command-line arguments provided to this program.
   *
   * @return The error code.
   */

  public static int mainCLI(String[] args)
  {
    return mainCLI(args, true, System.out, System.err, System.in);
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the replication tool.
   *
   * @param  args              The command-line arguments provided to this
   *                           program.
   * @param initializeServer   Indicates whether to initialize the server.
   * @param  outStream         The output stream to use for standard output, or
   *                           <CODE>null</CODE> if standard output is not
   *                           needed.
   * @param  errStream         The output stream to use for standard error, or
   *                           <CODE>null</CODE> if standard error is not
   *                           needed.
   * @param  inStream          The input stream to use for standard input.
   * @return The error code.
   */

  public static int mainCLI(String[] args, boolean initializeServer,
      OutputStream outStream, OutputStream errStream, InputStream inStream)
  {
    PrintStream out;
    if (outStream == null)
    {
      out = NullOutputStream.printStream();
    }
    else
    {
      out = new PrintStream(outStream);
    }

    PrintStream err;
    if (errStream == null)
    {
      err = NullOutputStream.printStream();
    }
    else
    {
      err = new PrintStream(errStream);
    }

    try {
      QuickSetupLog.initLogFileHandler(
              File.createTempFile(LOG_FILE_PREFIX, LOG_FILE_SUFFIX),
              "org.opends.guitools.replicationcli");
      QuickSetupLog.disableConsoleLogging();
    } catch (Throwable t) {
      System.err.println("Unable to initialize log");
      t.printStackTrace();
    }

    ReplicationCliMain replicationCli = new ReplicationCliMain(out, err,
        inStream);
    return replicationCli.execute(args, initializeServer);
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the replication tool.
   *
   * @param args the command-line arguments provided to this program.
   * @param  initializeServer  Indicates whether to initialize the server.
   *
   * @return The error code.
   */
  public int execute(String[] args, boolean initializeServer)
  {
    ReplicationCliReturnCode returnValue = SUCCESSFUL_NOP;
    // Create the command-line argument parser for use with this
    // program.
    try
    {
      argParser = new ReplicationCliArgumentParser(CLASS_NAME);
      argParser.initializeParser(getOutputStream());
    }
    catch (ArgumentException ae)
    {
      Message message =
        UtilityMessages.ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
      println(message);
      LOG.log(Level.SEVERE, "Complete error stack:", ae);
      returnValue = CANNOT_INITIALIZE_ARGS;
    }

    if (returnValue == SUCCESSFUL_NOP)
    {
      //  Parse the command-line arguments provided to this program.
      try
      {
        argParser.parseArguments(args);
      }
      catch (ArgumentException ae)
      {
        Message message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());

        println(message);
        println();
        println(Message.raw(argParser.getUsage()));
        LOG.log(Level.SEVERE, "Complete error stack:", ae);
        returnValue = ERROR_USER_DATA;
      }
    }

    if (!argParser.usageOrVersionDisplayed())
    {
      if (returnValue == SUCCESSFUL_NOP)
      {
        /* Check that the provided parameters are compatible.
         */
        MessageBuilder buf = new MessageBuilder();
        argParser.validateOptions(buf);
        if (buf.length() > 0)
        {
          println(buf.toMessage());
          println(Message.raw(argParser.getUsage()));
          returnValue = ERROR_USER_DATA;
        }
      }
      if (initializeServer)
      {
        DirectoryServer.bootstrapClient();

        // Bootstrap definition classes.
        try
        {
          ClassLoaderProvider.getInstance().enable();
          // Switch off class name validation in client.
          ClassPropertyDefinition.setAllowClassValidation(false);

          // Switch off attribute type name validation in client.
          AttributeTypePropertyDefinition.setCheckSchema(false);
        }
        catch (InitializationException ie)
        {
          println(ie.getMessageObject());
          returnValue = ERROR_INITIALIZING_ADMINISTRATION_FRAMEWORK;
        }
      }

      if (returnValue == SUCCESSFUL_NOP)
      {
        ci = new LDAPConnectionConsoleInteraction(this,
            argParser.getSecureArgsList());
        ci.setDisplayLdapIfSecureParameters(
            !argParser.isInitializeAllReplicationSubcommand() &&
            !argParser.isPreExternalInitializationSubcommand() ||
            !argParser.isPostExternalInitializationSubcommand());
      }
      if (returnValue == SUCCESSFUL_NOP)
      {
        if (argParser.isEnableReplicationSubcommand())
        {
          returnValue = enableReplication();
        }
        else if (argParser.isDisableReplicationSubcommand())
        {
          returnValue = disableReplication();
        }
        else if (argParser.isInitializeReplicationSubcommand())
        {
          returnValue = initializeReplication();
        }
        else if (argParser.isInitializeAllReplicationSubcommand())
        {
          returnValue = initializeAllReplication();
        }
        else if (argParser.isPreExternalInitializationSubcommand())
        {
          returnValue = preExternalInitialization();
        }
        else if (argParser.isPostExternalInitializationSubcommand())
        {
          returnValue = postExternalInitialization();
        }
        else if (argParser.isStatusReplicationSubcommand())
        {
          returnValue = statusReplication();
        }
        else
        {
          println(ERR_REPLICATION_VALID_SUBCOMMAND_NOT_FOUND.get());
          println(Message.raw(argParser.getUsage()));
          returnValue = ERROR_USER_DATA;
        }
      }
    }
    return returnValue.getReturnCode();
  }

  /**
   * Based on the data provided in the command-line it enables replication
   * between two servers.
   * @return the error code if the operation failed and 0 if it was successful.
   */
  private ReplicationCliReturnCode enableReplication()
  {
    ReplicationCliReturnCode returnValue = SUCCESSFUL_NOP;
    EnableReplicationUserData uData = new EnableReplicationUserData();
    if (argParser.isInteractive())
    {
      try
      {
        if (promptIfRequired(uData))
        {
          returnValue = enableReplication(uData);
        }
        else
        {
          returnValue = USER_CANCELLED;
        }
      }
      catch (ReplicationCliException rce)
      {
        returnValue = rce.getErrorCode();
        println();
        println(getCriticalExceptionMessage(rce));
      }
    }
    else
    {
      initializeWithArgParser(uData);
      returnValue = enableReplication(uData);
    }
    return returnValue;
  }

  /**
   * Based on the data provided in the command-line it disables replication
   * in the server.
   * @return the error code if the operation failed and SUCCESSFUL if it was
   * successful.
   */
  private ReplicationCliReturnCode disableReplication()
  {
    ReplicationCliReturnCode returnValue = SUCCESSFUL_NOP;
    DisableReplicationUserData uData = new DisableReplicationUserData();
    if (argParser.isInteractive())
    {
      try
      {
        if (promptIfRequired(uData))
        {
          returnValue = disableReplication(uData);
        }
        else
        {
          returnValue = USER_CANCELLED;
        }
      }
      catch (ReplicationCliException rce)
      {
        returnValue = rce.getErrorCode();
        println();
        println(getCriticalExceptionMessage(rce));
      }
    }
    else
    {
      initializeWithArgParser(uData);
      returnValue = disableReplication(uData);
    }
    return returnValue;
  }

  /**
   * Based on the data provided in the command-line initialize the contents
   * of the whole replication topology.
   * @return the error code if the operation failed and SUCCESSFUL if it was
   * successful.
   */
  private ReplicationCliReturnCode initializeAllReplication()
  {
    ReplicationCliReturnCode returnValue = SUCCESSFUL_NOP;
    InitializeAllReplicationUserData uData =
      new InitializeAllReplicationUserData();
    if (argParser.isInteractive())
    {
      if (promptIfRequired(uData))
      {
        returnValue = initializeAllReplication(uData);
      }
      else
      {
        returnValue = USER_CANCELLED;
      }
    }
    else
    {
      initializeWithArgParser(uData);
      returnValue = initializeAllReplication(uData);
    }
    return returnValue;
  }

  /**
   * Based on the data provided in the command-line execute the pre external
   * initialization operation.
   * @return the error code if the operation failed and SUCCESSFUL if it was
   * successful.
   */
  private ReplicationCliReturnCode preExternalInitialization()
  {
    ReplicationCliReturnCode returnValue = SUCCESSFUL_NOP;
    PreExternalInitializationUserData uData =
      new PreExternalInitializationUserData();
    if (argParser.isInteractive())
    {
      if (promptIfRequired(uData))
      {
        returnValue = preExternalInitialization(uData);
      }
      else
      {
        returnValue = USER_CANCELLED;
      }
    }
    else
    {
      initializeWithArgParser(uData);
      returnValue = preExternalInitialization(uData);
    }
    return returnValue;
  }

  /**
   * Based on the data provided in the command-line execute the post external
   * initialization operation.
   * @return the error code if the operation failed and SUCCESSFUL if it was
   * successful.
   */
  private ReplicationCliReturnCode postExternalInitialization()
  {
    ReplicationCliReturnCode returnValue = SUCCESSFUL_NOP;
    PostExternalInitializationUserData uData =
      new PostExternalInitializationUserData();
    if (argParser.isInteractive())
    {
      if (promptIfRequired(uData))
      {
        returnValue = postExternalInitialization(uData);
      }
      else
      {
        returnValue = USER_CANCELLED;
      }
    }
    else
    {
      initializeWithArgParser(uData);
      returnValue = postExternalInitialization(uData);
    }
    return returnValue;
  }

  /**
   * Based on the data provided in the command-line it displays replication
   * status.
   * @return the error code if the operation failed and SUCCESSFUL if it was
   * successful.
   */
  private ReplicationCliReturnCode statusReplication()
  {
    ReplicationCliReturnCode returnValue = SUCCESSFUL_NOP;
    StatusReplicationUserData uData = new StatusReplicationUserData();
    if (argParser.isInteractive())
    {
      try
      {
        if (promptIfRequired(uData))
        {
          returnValue = statusReplication(uData);
        }
        else
        {
          returnValue = USER_CANCELLED;
        }
      }
      catch (ReplicationCliException rce)
      {
        returnValue = rce.getErrorCode();
        println();
        println(getCriticalExceptionMessage(rce));
      }
    }
    else
    {
      initializeWithArgParser(uData);
      returnValue = statusReplication(uData);
    }
    return returnValue;
  }

  /**
   * Based on the data provided in the command-line it initializes replication
   * between two servers.
   * @return the error code if the operation failed and SUCCESSFUL if it was
   * successful.
   */
  private ReplicationCliReturnCode initializeReplication()
  {
    ReplicationCliReturnCode returnValue = SUCCESSFUL_NOP;
    InitializeReplicationUserData uData = new InitializeReplicationUserData();
    if (argParser.isInteractive())
    {
      if (promptIfRequired(uData))
      {
        returnValue = initializeReplication(uData);
      }
      else
      {
        returnValue = USER_CANCELLED;
      }
    }
    else
    {
      initializeWithArgParser(uData);
      returnValue = initializeReplication(uData);
    }
    return returnValue;
  }

  /**
   * Updates the contents of the provided EnableReplicationUserData object
   * with the information provided in the command-line.  If some information
   * is missing, ask the user to provide valid data.
   * We assume that if this method is called we are in interactive mode.
   * @param uData the object to be updated.
   * @return <CODE>true</CODE> if the object was successfully updated and
   * <CODE>false</CODE> if the user cancelled the operation.
   * @throws ReplicationCliException if a critical error occurs reading the
   * ADS.
   */
  private boolean promptIfRequired(EnableReplicationUserData uData)
  throws ReplicationCliException
  {
    boolean cancelled = false;

    boolean administratorDefined = false;

    ci.setUseAdminOrBindDn(true);

    String adminPwd = argParser.getBindPasswordAdmin();
    String adminUid = argParser.getAdministratorUID();

    /*
     * Try to connect to the first server.
     */
    String host1 = argParser.getHostName1();
    int port1 = argParser.getPort1();
    boolean useSSL1 = argParser.useSSL1();
    boolean useStartTLS1 = argParser.useStartTLS1();
    String bindDn1 = argParser.getBindDn1();
    String pwd1 = argParser.getBindPassword1();

    String pwd = null;
    if (pwd1 != null)
    {
      pwd = pwd1;
    }
    else if (bindDn1 != null)
    {
      pwd = null;
    }
    else
    {
      pwd = adminPwd;
    }

    initializeGlobalArguments(host1, port1, useSSL1, useStartTLS1, adminUid,
        bindDn1, pwd);
    InitialLdapContext ctx1 = null;

    while ((ctx1 == null) && !cancelled)
    {
      try
      {
        ci.setHeadingMessage(
            INFO_REPLICATION_ENABLE_HOST1_CONNECTION_PARAMETERS.get());
        ci.run();
        useSSL1 = ci.useSSL();
        useStartTLS1 = ci.useStartTLS();
        host1 = ci.getHostName();
        port1 = ci.getPortNumber();
        if (ci.getProvidedAdminUID() != null)
        {
          adminUid = ci.getProvidedAdminUID();
          if (ci.getProvidedBindDN() == null)
          {
            // If the explicit bind DN is not null, the password corresponds
            // to that bind DN.  We are in the case where the user provides
            // bind DN on first server and admin UID globally.
            adminPwd = ci.getBindPassword();
          }
        }
        bindDn1 = ci.getBindDN();
        pwd1 = ci.getBindPassword();

        ctx1 = createInitialLdapContextInteracting(ci);

        if (ctx1 == null)
        {
          cancelled = true;
        }
      }
      catch (ClientException ce)
      {
        LOG.log(Level.WARNING, "Client exception "+ce);
        println();
        println(ce.getMessageObject());
        println();
        resetConnectionArguments();
      }
      catch (ArgumentException ae)
      {
        LOG.log(Level.WARNING, "Argument exception "+ae);
        println();
        println(ae.getMessageObject());
        println();
        cancelled = true;
      }
    }

    if (!cancelled)
    {
      uData.setHostName1(host1);
      uData.setPort1(port1);
      uData.setBindDn1(bindDn1);
      uData.setPwd1(pwd1);
      uData.setUseSSL1(useSSL1);
      uData.setUseStartTLS1(useStartTLS1);
    }
    int replicationPort1 = -1;
    boolean secureReplication1 = argParser.isSecureReplication1();
    if (ctx1 != null)
    {
      // Try to get the replication port for server 1 only if it is required.
      if (!hasReplicationPort(ctx1))
      {
        boolean tryWithDefault = argParser.getReplicationPort1() != -1;
        while (replicationPort1 == -1)
        {
          if (tryWithDefault)
          {
            replicationPort1 = argParser.getReplicationPort1();
            tryWithDefault = false;
          }
          else
          {
            replicationPort1 = askPort(
                INFO_REPLICATION_ENABLE_REPLICATIONPORT1_PROMPT.get(),
                argParser.getDefaultReplicationPort1());
            println();
          }
          if (!argParser.skipReplicationPortCheck() && isLocalHost(host1))
          {
            if (!SetupUtils.canUseAsPort(replicationPort1))
            {
              println();
              println(getCannotBindToPortError(replicationPort1));
              println();
              replicationPort1 = -1;
            }
          }
          else
          {
            // This is something that we must do in any case... this test is
            // already included when we call SetupUtils.canUseAsPort
            if (replicationPort1 == port1)
            {
              println();
              println(
                  ERR_REPLICATION_PORT_AND_REPLICATION_PORT_EQUAL.get(
                      host1, String.valueOf(replicationPort1)));
              println();
              replicationPort1 = -1;
            }
          }
        }
        if (!secureReplication1)
        {
          secureReplication1 =
            askConfirmation(INFO_REPLICATION_ENABLE_SECURE1_PROMPT.get(
                String.valueOf(replicationPort1)), false, LOG);
          println();
        }
      }
      // If the server contains an ADS. Try to load it and only load it: if
      // there are issues with the ADS they will be encountered in the
      // enableReplication(EnableReplicationUserData) method.  Here we have
      // to load the ADS to ask the user to accept the certificates and
      // eventually admin authentication data.
      InitialLdapContext[] aux = new InitialLdapContext[] {ctx1};
      cancelled = !loadADSAndAcceptCertificates(aux, uData, true);
      ctx1 = aux[0];
      if (!cancelled)
      {
        administratorDefined |= hasAdministrator(ctx1);
        if (uData.getAdminPwd() != null)
        {
          adminPwd = uData.getAdminPwd();
        }
      }
    }
    uData.setReplicationPort1(replicationPort1);
    uData.setSecureReplication1(secureReplication1);

    /*
     * Prompt for information on the second server.
     */
    String host2 = null;
    int port2 = -1;
    String bindDn2 = null;
    String pwd2 = null;
    boolean useSSL2 = false;
    boolean useStartTLS2 = false;
    ci.resetHeadingDisplayed();
    if (!cancelled)
    {
      host2 = argParser.getHostName2();
      port2 = argParser.getPort2();
      useSSL2 = argParser.useSSL2();
      useStartTLS2 = argParser.useStartTLS2();
      bindDn2 = argParser.getBindDn2();
      pwd2 = argParser.getBindPassword2();
      if (pwd2 != null)
      {
        pwd = pwd2;
      }
      else if (bindDn2 != null)
      {
        pwd = null;
      }
      else
      {
        pwd = adminPwd;
      }

      initializeGlobalArguments(host2, port2, useSSL2, useStartTLS2, adminUid,
          bindDn2, pwd);
    }
    InitialLdapContext ctx2 = null;

    while ((ctx2 == null) && !cancelled)
    {
      try
      {
        ci.setHeadingMessage(
            INFO_REPLICATION_ENABLE_HOST2_CONNECTION_PARAMETERS.get());
        ci.run();
        useSSL2 = ci.useSSL();
        useStartTLS2 = ci.useStartTLS();
        host2 = ci.getHostName();
        port2 = ci.getPortNumber();
        if (ci.getProvidedAdminUID() != null)
        {
          adminUid = ci.getProvidedAdminUID();
          if (ci.getProvidedBindDN() == null)
          {
            // If the explicit bind DN is not null, the password corresponds
            // to that bind DN.  We are in the case where the user provides
            // bind DN on first server and admin UID globally.
            adminPwd = ci.getBindPassword();
          }
        }
        bindDn2 = ci.getBindDN();
        pwd2 = ci.getBindPassword();

        boolean error = false;
        if (host1.equalsIgnoreCase(host2))
        {
          if (port1 == port2)
          {
            port2 = -1;
            Message message = ERR_REPLICATION_ENABLE_SAME_SERVER_PORT.get(
                host1, String.valueOf(port1));
            println();
            println(message);
            println();
            error = true;
          }
        }

        if (!error)
        {
          ctx2 = createInitialLdapContextInteracting(ci);

          if (ctx2 == null)
          {
            cancelled = true;
          }
        }
      }
      catch (ClientException ce)
      {
        LOG.log(Level.WARNING, "Client exception "+ce);
        println();
        println(ce.getMessageObject());
        println();
        resetConnectionArguments();
      }
      catch (ArgumentException ae)
      {
        LOG.log(Level.WARNING, "Argument exception "+ae);
        println();
        println(ae.getMessageObject());
        println();
        cancelled = true;
      }
    }

    if (!cancelled)
    {
      uData.setHostName2(host2);
      uData.setPort2(port2);
      uData.setBindDn2(bindDn2);
      uData.setPwd2(pwd2);
      uData.setUseSSL2(useSSL2);
      uData.setUseStartTLS2(useStartTLS2);
    }

    int replicationPort2 = -1;
    boolean secureReplication2 = argParser.isSecureReplication2();
    if (ctx2 != null)
    {
      if (!hasReplicationPort(ctx2))
      {
        boolean tryWithDefault = argParser.getReplicationPort2() != -1;
        while (replicationPort2 == -1)
        {
          if (tryWithDefault)
          {
            replicationPort2 = argParser.getReplicationPort2();
            tryWithDefault = false;
          }
          else
          {
            replicationPort2 = askPort(
                INFO_REPLICATION_ENABLE_REPLICATIONPORT2_PROMPT.get(),
                argParser.getDefaultReplicationPort2());
            println();
          }
          if (!argParser.skipReplicationPortCheck() && isLocalHost(host2))
          {
            if (!SetupUtils.canUseAsPort(replicationPort2))
            {
              println();
              println(getCannotBindToPortError(replicationPort2));
              println();
              replicationPort2 = -1;
            }
          }
          else
          {
            // This is something that we must do in any case... this test is
            // already included when we call SetupUtils.canUseAsPort
            if (replicationPort2 == port2)
            {
              println();
              println(
                  ERR_REPLICATION_PORT_AND_REPLICATION_PORT_EQUAL.get(
                      host2, String.valueOf(replicationPort2)));
              replicationPort2 = -1;
            }
          }
          if (host1.equalsIgnoreCase(host2))
          {
            if ((replicationPort1 > 0) &&
                (replicationPort1 == replicationPort2))
            {
              println();
              println(ERR_REPLICATION_SAME_REPLICATION_PORT.get(
                      String.valueOf(replicationPort2), host1));
              println();
              replicationPort2 = -1;
            }
          }
        }
        if (!secureReplication2)
        {
          secureReplication2 =
            askConfirmation(INFO_REPLICATION_ENABLE_SECURE2_PROMPT.get(
                String.valueOf(replicationPort2)), false, LOG);
          println();
        }
      }
      // If the server contains an ADS. Try to load it and only load it: if
      // there are issues with the ADS they will be encountered in the
      // enableReplication(EnableReplicationUserData) method.  Here we have
      // to load the ADS to ask the user to accept the certificates.
      InitialLdapContext[] aux = new InitialLdapContext[] {ctx2};
      cancelled = !loadADSAndAcceptCertificates(aux, uData, false);
      ctx2 = aux[0];
      if (!cancelled)
      {
        administratorDefined |= hasAdministrator(ctx2);
      }
    }
    uData.setReplicationPort2(replicationPort2);
    uData.setSecureReplication2(secureReplication2);

    // If the adminUid and adminPwd are not set in the EnableReplicationUserData
    // object, that means that there are no administrators and that they
    // must be created. The adminUId and adminPwd are updated inside
    // loadADSAndAcceptCertificates.
    boolean promptedForAdmin = false;

    // There is a case where we haven't had need for the administrator
    // credentials even if the administrators are defined: where all the servers
    // can be accessed with another user (for instance if all the server have
    // defined cn=directory manager and all the entries have the same password).
    if (!cancelled && (uData.getAdminUid() == null) && !administratorDefined)
    {
      if (adminUid == null)
      {
        println(INFO_REPLICATION_ENABLE_ADMINISTRATOR_MUST_BE_CREATED.get());
        promptedForAdmin = true;
        adminUid= askForAdministratorUID(
            argParser.getDefaultAdministratorUID());
        println();
      }
      uData.setAdminUid(adminUid);
    }

    if (uData.getAdminPwd() == null)
    {
      uData.setAdminPwd(adminPwd);
    }
    if (!cancelled && (uData.getAdminPwd() == null) && !administratorDefined)
    {
      adminPwd = null;
      while (adminPwd == null)
      {
        if (!promptedForAdmin)
        {
          println();
          println(INFO_REPLICATION_ENABLE_ADMINISTRATOR_MUST_BE_CREATED.get());
          println();
        }
        while (adminPwd == null)
        {
          adminPwd = askForAdministratorPwd();
          println();
        }
        String adminPwdConfirm = null;
        while (adminPwdConfirm == null)
        {
          adminPwdConfirm =
          readPassword(INFO_ADMINISTRATOR_PWD_CONFIRM_PROMPT.get(), LOG);
          println();
        }
        if (!adminPwd.equals(adminPwdConfirm))
        {
          println();
          println(ERR_ADMINISTRATOR_PWD_DO_NOT_MATCH.get());
          println();
          adminPwd = null;
        }
      }
      uData.setAdminPwd(adminPwd);
    }

    if (!cancelled)
    {
      LinkedList<String> suffixes = argParser.getBaseDNs();
      checkSuffixesForEnableReplication(suffixes, ctx1, ctx2, true);
      cancelled = suffixes.isEmpty();

      uData.setBaseDNs(suffixes);
    }

    if (ctx1 != null)
    {
      try
      {
        ctx1.close();
      }
      catch (Throwable t)
      {
      }
    }

    if (ctx2 != null)
    {
      try
      {
        ctx2.close();
      }
      catch (Throwable t)
      {
      }
    }

    uData.setReplicateSchema(!argParser.noSchemaReplication());

    return !cancelled;
  }

  /**
   * Updates the contents of the provided DisableReplicationUserData object
   * with the information provided in the command-line.  If some information
   * is missing, ask the user to provide valid data.
   * We assume that if this method is called we are in interactive mode.
   * @param uData the object to be updated.
   * @return <CODE>true</CODE> if the object was successfully updated and
   * <CODE>false</CODE> if the user cancelled the operation.
   * @throws ReplicationCliException if there is a critical error reading the
   * ADS.
   */
  private boolean promptIfRequired(DisableReplicationUserData uData)
  throws ReplicationCliException
  {
    boolean cancelled = false;

    String adminPwd = argParser.getBindPasswordAdmin();
    String adminUid = argParser.getAdministratorUID();
    String bindDn = argParser.getBindDNToDisable();

    // This is done because we want to ask explicitly for this

    String host = argParser.getHostNameToDisable();
    int port = argParser.getPortToDisable();
    boolean useSSL = argParser.useSSLToDisable();
    boolean useStartTLS = argParser.useStartTLSToDisable();

    /*
     * Try to connect to the server.
     */
    InitialLdapContext ctx = null;

    while ((ctx == null) && !cancelled)
    {
      try
      {
        ci.setUseAdminOrBindDn(true);
        ci.run();
        useSSL = ci.useSSL();
        useStartTLS = ci.useStartTLS();
        host = ci.getHostName();
        port = ci.getPortNumber();
        bindDn = ci.getProvidedBindDN();
        adminUid = ci.getProvidedAdminUID();
        adminPwd = ci.getBindPassword();

        ctx = createInitialLdapContextInteracting(ci);

        if (ctx == null)
        {
          cancelled = true;
        }
      }
      catch (ClientException ce)
      {
        LOG.log(Level.WARNING, "Client exception "+ce);
        println();
        println(ce.getMessageObject());
        println();
        resetConnectionArguments();
      }
      catch (ArgumentException ae)
      {
        LOG.log(Level.WARNING, "Argument exception "+ae);
        println();
        println(ae.getMessageObject());
        println();
        cancelled = true;
      }
    }

    if (!cancelled)
    {
      uData.setHostName(host);
      uData.setPort(port);
      uData.setUseSSL(useSSL);
      uData.setUseStartTLS(useStartTLS);
      uData.setAdminUid(adminUid);
      uData.setBindDn(bindDn);
      uData.setAdminPwd(adminPwd);
    }
    if ((ctx != null) && (adminUid != null))
    {
      // If the server contains an ADS, try to load it and only load it: if
      // there are issues with the ADS they will be encountered in the
      // disableReplication(DisableReplicationUserData) method.  Here we have
      // to load the ADS to ask the user to accept the certificates and
      // eventually admin authentication data.
      InitialLdapContext[] aux = new InitialLdapContext[] {ctx};
      cancelled = !loadADSAndAcceptCertificates(aux, uData, false);
      ctx = aux[0];
    }

    if (!cancelled)
    {
      LinkedList<String> suffixes = argParser.getBaseDNs();
      checkSuffixesForDisableReplication(suffixes, ctx, true);
      cancelled = suffixes.isEmpty();

      uData.setBaseDNs(suffixes);
    }

    if (!cancelled)
    {
      // Ask for confirmation to disable.
      boolean disableADS = false;
      boolean disableSchema = false;
      for (String dn : uData.getBaseDNs())
      {
        if (Utils.areDnsEqual(ADSContext.getAdministrationSuffixDN(), dn))
        {
          disableADS = true;
        }
        else if (Utils.areDnsEqual(Constants.SCHEMA_DN, dn))
        {
          disableSchema = true;
        }
      }
      if (disableADS)
      {
        println();
        cancelled = !askConfirmation(INFO_REPLICATION_CONFIRM_DISABLE_ADS.get(
            ADSContext.getAdministrationSuffixDN()), true, LOG);
        println();
      }
      if (disableSchema)
      {
        println();
        cancelled = !askConfirmation(
            INFO_REPLICATION_CONFIRM_DISABLE_SCHEMA.get(), true, LOG);
        println();
      }
      if (!disableSchema && !disableADS)
      {
        println();
        cancelled = !askConfirmation(
            INFO_REPLICATION_CONFIRM_DISABLE_GENERIC.get(), true, LOG);
        println();
      }
    }

    if (ctx != null)
    {
      try
      {
        ctx.close();
      }
      catch (Throwable t)
      {
      }
    }

    return !cancelled;
  }

  /**
   * Updates the contents of the provided InitializeAllReplicationUserData
   * object with the information provided in the command-line.  If some
   * information is missing, ask the user to provide valid data.
   * We assume that if this method is called we are in interactive mode.
   * @param uData the object to be updated.
   * @return <CODE>true</CODE> if the object was successfully updated and
   * <CODE>false</CODE> if the user cancelled the operation.
   */
  private boolean promptIfRequired(InitializeAllReplicationUserData uData)
  {
    boolean cancelled = false;

    String adminPwd = argParser.getBindPasswordAdmin();
    String adminUid = argParser.getAdministratorUID();

    String host = argParser.getHostNameToInitializeAll();
    int port = argParser.getPortToInitializeAll();
    boolean useSSL = argParser.useSSLToInitializeAll();
    boolean useStartTLS = argParser.useStartTLSToInitializeAll();

    /*
     * Try to connect to the server.
     */
    InitialLdapContext ctx = null;

    while ((ctx == null) && !cancelled)
    {
      try
      {
        ci.setHeadingMessage(
            INFO_REPLICATION_INITIALIZE_SOURCE_CONNECTION_PARAMETERS.get());
        ci.run();
        useSSL = ci.useSSL();
        useStartTLS = ci.useStartTLS();
        host = ci.getHostName();
        port = ci.getPortNumber();
        adminUid = ci.getAdministratorUID();
        adminPwd = ci.getBindPassword();

        ctx = createInitialLdapContextInteracting(ci);

        if (ctx == null)
        {
          cancelled = true;
        }
      }
      catch (ClientException ce)
      {
        LOG.log(Level.WARNING, "Client exception "+ce);
        println();
        println(ce.getMessageObject());
        println();
        resetConnectionArguments();
      }
      catch (ArgumentException ae)
      {
        LOG.log(Level.WARNING, "Argument exception "+ae);
        println();
        println(ae.getMessageObject());
        println();
        cancelled = true;
      }
    }
    if (!cancelled)
    {
      uData.setHostName(host);
      uData.setPort(port);
      uData.setUseSSL(useSSL);
      uData.setUseStartTLS(useStartTLS);
      uData.setAdminUid(adminUid);
      uData.setAdminPwd(adminPwd);
    }

    if (!cancelled)
    {
      LinkedList<String> suffixes = argParser.getBaseDNs();
      checkSuffixesForInitializeReplication(suffixes, ctx, true);
      cancelled = suffixes.isEmpty();

      uData.setBaseDNs(suffixes);
    }

    if (!cancelled)
    {
      // Ask for confirmation to initialize.
      boolean initializeADS = false;
      for (String dn : uData.getBaseDNs())
      {
        if (Utils.areDnsEqual(ADSContext.getAdministrationSuffixDN(), dn))
        {
          initializeADS = true;
        }
      }
      String hostPortSource = ConnectionUtils.getHostPort(ctx);
      if (initializeADS)
      {
        println();
        cancelled = !askConfirmation(
            INFO_REPLICATION_CONFIRM_INITIALIZE_ALL_ADS.get(
            ADSContext.getAdministrationSuffixDN(), hostPortSource), true, LOG);
        println();
      }
      else
      {
        println();
        cancelled = !askConfirmation(
            INFO_REPLICATION_CONFIRM_INITIALIZE_ALL_GENERIC.get(
                hostPortSource), true, LOG);
        println();
      }
    }

    if (ctx != null)
    {
      try
      {
        ctx.close();
      }
      catch (Throwable t)
      {
      }
    }

    return !cancelled;
  }

  /**
   * Updates the contents of the provided PreExternalInitializationUserData
   * object with the information provided in the command-line.  If some
   * information is missing, ask the user to provide valid data.
   * We assume that if this method is called we are in interactive mode.
   * @param uData the object to be updated.
   * @return <CODE>true</CODE> if the object was successfully updated and
   * <CODE>false</CODE> if the user cancelled the operation.
   */
  private boolean promptIfRequired(PreExternalInitializationUserData uData)
  {
    boolean cancelled = false;

    String adminPwd = argParser.getBindPasswordAdmin();
    String adminUid = argParser.getAdministratorUID();

    String host = argParser.getHostNameToInitializeAll();
    int port = argParser.getPortToInitializeAll();
    boolean useSSL = argParser.useSSLToInitializeAll();
    boolean useStartTLS = argParser.useStartTLSToInitializeAll();

    /*
     * Try to connect to the server.
     */
    InitialLdapContext ctx = null;

    while ((ctx == null) && !cancelled)
    {
      try
      {
        ci.run();
        useSSL = ci.useSSL();
        useStartTLS = ci.useStartTLS();
        host = ci.getHostName();
        port = ci.getPortNumber();
        adminUid = ci.getAdministratorUID();
        adminPwd = ci.getBindPassword();

        ctx = createInitialLdapContextInteracting(ci);

        if (ctx == null)
        {
          cancelled = true;
        }
      }
      catch (ClientException ce)
      {
        LOG.log(Level.WARNING, "Client exception "+ce);
        println();
        println(ce.getMessageObject());
        println();
        resetConnectionArguments();
      }
      catch (ArgumentException ae)
      {
        LOG.log(Level.WARNING, "Argument exception "+ae);
        println();
        println(ae.getMessageObject());
        println();
        cancelled = true;
      }
    }
    if (!cancelled)
    {
      boolean onlyLocal = false;
      if (!argParser.isExternalInitializationOnlyInLocal())
      {
        println();
        onlyLocal = askConfirmation(
            INFO_REPLICATION_PRE_EXTERNAL_INITIALIZATION_LOCAL_PROMPT.get(
                ConnectionUtils.getHostPort(ctx)), false, LOG);
      }
      else
      {
        onlyLocal = true;
      }
      uData.setOnlyLocal(onlyLocal);

      uData.setHostName(host);
      uData.setPort(port);
      uData.setUseSSL(useSSL);
      uData.setUseStartTLS(useStartTLS);
      uData.setAdminUid(adminUid);
      uData.setAdminPwd(adminPwd);
    }

    if (!cancelled)
    {
      LinkedList<String> suffixes = argParser.getBaseDNs();
      checkSuffixesForInitializeReplication(suffixes, ctx, true);
      cancelled = suffixes.isEmpty();

      uData.setBaseDNs(suffixes);
    }

    if (ctx != null)
    {
      try
      {
        ctx.close();
      }
      catch (Throwable t)
      {
      }
    }

    return !cancelled;
  }

  /**
   * Updates the contents of the provided PostExternalInitializationUserData
   * object with the information provided in the command-line.  If some
   * information is missing, ask the user to provide valid data.
   * We assume that if this method is called we are in interactive mode.
   * @param uData the object to be updated.
   * @return <CODE>true</CODE> if the object was successfully updated and
   * <CODE>false</CODE> if the user cancelled the operation.
   */
  private boolean promptIfRequired(PostExternalInitializationUserData uData)
  {
    boolean cancelled = false;

    String adminPwd = argParser.getBindPasswordAdmin();
    String adminUid = argParser.getAdministratorUID();

    String host = argParser.getHostNameToInitializeAll();
    int port = argParser.getPortToInitializeAll();
    boolean useSSL = argParser.useSSLToInitializeAll();
    boolean useStartTLS = argParser.useStartTLSToInitializeAll();

    /*
     * Try to connect to the server.
     */
    InitialLdapContext ctx = null;

    while ((ctx == null) && !cancelled)
    {
      try
      {
        ci.run();
        useSSL = ci.useSSL();
        useStartTLS = ci.useStartTLS();
        host = ci.getHostName();
        port = ci.getPortNumber();
        adminUid = ci.getAdministratorUID();
        adminPwd = ci.getBindPassword();

        ctx = createInitialLdapContextInteracting(ci);

        if (ctx == null)
        {
          cancelled = true;
        }
      }
      catch (ClientException ce)
      {
        LOG.log(Level.WARNING, "Client exception "+ce);
        println();
        println(ce.getMessageObject());
        println();
        resetConnectionArguments();
      }
      catch (ArgumentException ae)
      {
        LOG.log(Level.WARNING, "Argument exception "+ae);
        println();
        println(ae.getMessageObject());
        println();
        cancelled = true;
      }
    }
    if (!cancelled)
    {
      uData.setHostName(host);
      uData.setPort(port);
      uData.setUseSSL(useSSL);
      uData.setUseStartTLS(useStartTLS);
      uData.setAdminUid(adminUid);
      uData.setAdminPwd(adminPwd);
    }

    if (!cancelled)
    {
      LinkedList<String> suffixes = argParser.getBaseDNs();
      checkSuffixesForInitializeReplication(suffixes, ctx, true);
      cancelled = suffixes.isEmpty();

      uData.setBaseDNs(suffixes);
    }

    if (ctx != null)
    {
      try
      {
        ctx.close();
      }
      catch (Throwable t)
      {
      }
    }

    return !cancelled;
  }

  /**
   * Updates the contents of the provided StatusReplicationUserData object
   * with the information provided in the command-line.  If some information
   * is missing, ask the user to provide valid data.
   * We assume that if this method is called we are in interactive mode.
   * @param uData the object to be updated.
   * @return <CODE>true</CODE> if the object was successfully updated and
   * <CODE>false</CODE> if the user cancelled the operation.
   * @throws ReplicationCliException if a critical error occurs reading the
   * ADS.
   */
  private boolean promptIfRequired(StatusReplicationUserData uData)
  throws ReplicationCliException
  {
    boolean cancelled = false;

    String adminPwd = argParser.getBindPasswordAdmin();
    String adminUid = argParser.getAdministratorUID();

    String host = argParser.getHostNameToStatus();
    int port = argParser.getPortToStatus();
    boolean useSSL = argParser.useSSLToStatus();
    boolean useStartTLS = argParser.useStartTLSToStatus();

    /*
     * Try to connect to the server.
     */
    InitialLdapContext ctx = null;

    while ((ctx == null) && !cancelled)
    {
      try
      {
        ci.run();
        useSSL = ci.useSSL();
        useStartTLS = ci.useStartTLS();
        host = ci.getHostName();
        port = ci.getPortNumber();
        adminUid = ci.getAdministratorUID();
        adminPwd = ci.getBindPassword();

        ctx = createInitialLdapContextInteracting(ci);

        if (ctx == null)
        {
          cancelled = true;
        }
      }
      catch (ClientException ce)
      {
        LOG.log(Level.WARNING, "Client exception "+ce);
        println();
        println(ce.getMessageObject());
        println();
        resetConnectionArguments();
      }
      catch (ArgumentException ae)
      {
        LOG.log(Level.WARNING, "Argument exception "+ae);
        println();
        println(ae.getMessageObject());
        println();
        cancelled = true;
      }
    }
    if (!cancelled)
    {
      uData.setHostName(host);
      uData.setPort(port);
      uData.setUseSSL(useSSL);
      uData.setUseStartTLS(useStartTLS);
      uData.setAdminUid(adminUid);
      uData.setAdminPwd(adminPwd);
      uData.setScriptFriendly(argParser.isScriptFriendly());
    }
    if (ctx != null)
    {
      // If the server contains an ADS, try to load it and only load it: if
      // there are issues with the ADS they will be encountered in the
      // statusReplication(StatusReplicationUserData) method.  Here we have
      // to load the ADS to ask the user to accept the certificates and
      // eventually admin authentication data.
      InitialLdapContext[] aux = new InitialLdapContext[] {ctx};
      cancelled = !loadADSAndAcceptCertificates(aux, uData, false);
      ctx = aux[0];
    }

    if (!cancelled)
    {
      LinkedList<String> suffixes = argParser.getBaseDNs();
      uData.setBaseDNs(suffixes);
    }

    if (ctx != null)
    {
      try
      {
        ctx.close();
      }
      catch (Throwable t)
      {
      }
    }

    return !cancelled;
  }

  /**
   * Updates the contents of the provided InitializeReplicationUserData object
   * with the information provided in the command-line.  If some information
   * is missing, ask the user to provide valid data.
   * We assume that if this method is called we are in interactive mode.
   * @param uData the object to be updated.
   * @return <CODE>true</CODE> if the object was successfully updated and
   * <CODE>false</CODE> if the user cancelled the operation.
   */
  private boolean promptIfRequired(InitializeReplicationUserData uData)
  {
    boolean cancelled = false;

    String adminPwd = argParser.getBindPasswordAdmin();
    String adminUid = argParser.getAdministratorUID();

    String hostSource = argParser.getHostNameSource();
    int portSource = argParser.getPortSource();
    boolean useSSLSource = argParser.useSSLSource();
    boolean useStartTLSSource = argParser.useStartTLSSource();

    initializeGlobalArguments(hostSource, portSource, useSSLSource,
        useStartTLSSource, adminUid, null, adminPwd);
    /*
     * Try to connect to the source server.
     */
    InitialLdapContext ctxSource = null;

    while ((ctxSource == null) && !cancelled)
    {
      try
      {
        ci.setHeadingMessage(
            INFO_REPLICATION_INITIALIZE_SOURCE_CONNECTION_PARAMETERS.get());
        ci.run();
        useSSLSource = ci.useSSL();
        useStartTLSSource = ci.useStartTLS();
        hostSource = ci.getHostName();
        portSource = ci.getPortNumber();
        adminUid = ci.getAdministratorUID();
        adminPwd = ci.getBindPassword();

        ctxSource = createInitialLdapContextInteracting(ci);

        if (ctxSource == null)
        {
          cancelled = true;
        }
      }
      catch (ClientException ce)
      {
        LOG.log(Level.WARNING, "Client exception "+ce);
        println();
        println(ce.getMessageObject());
        println();
        resetConnectionArguments();
      }
      catch (ArgumentException ae)
      {
        LOG.log(Level.WARNING, "Argument exception "+ae);
        println();
        println(ae.getMessageObject());
        println();
        cancelled = true;
      }
    }
    if (!cancelled)
    {
      uData.setHostNameSource(hostSource);
      uData.setPortSource(portSource);
      uData.setUseSSLSource(useSSLSource);
      uData.setUseStartTLSSource(useStartTLSSource);
      uData.setAdminUid(adminUid);
      uData.setAdminPwd(adminPwd);
    }

    /* Prompt for destination server credentials */
    String hostDestination = argParser.getHostNameDestination();
    int portDestination = argParser.getPortDestination();
    boolean useSSLDestination = argParser.useSSLDestination();
    boolean useStartTLSDestination = argParser.useStartTLSDestination();

    initializeGlobalArguments(hostDestination, portDestination,
        useSSLDestination, useStartTLSDestination, adminUid, null, adminPwd);
    /*
     * Try to connect to the destination server.
     */
    InitialLdapContext ctxDestination = null;

    ci.resetHeadingDisplayed();
    while ((ctxDestination == null) && !cancelled)
    {
      try
      {
        ci.setHeadingMessage(
           INFO_REPLICATION_INITIALIZE_DESTINATION_CONNECTION_PARAMETERS.get());
        ci.run();
        useSSLDestination = ci.useSSL();
        useStartTLSDestination = ci.useStartTLS();
        hostDestination = ci.getHostName();
        portDestination = ci.getPortNumber();

        ctxDestination = createInitialLdapContextInteracting(ci);

        if (ctxDestination == null)
        {
          cancelled = true;
        }
      }
      catch (ClientException ce)
      {
        LOG.log(Level.WARNING, "Client exception "+ce);
        println();
        println(ce.getMessageObject());
        println();
        resetConnectionArguments();
      }
      catch (ArgumentException ae)
      {
        LOG.log(Level.WARNING, "Argument exception "+ae);
        println();
        println(ae.getMessageObject());
        println();
        cancelled = true;
      }
    }    if (!cancelled)
    {
      uData.setHostNameDestination(hostDestination);
      uData.setPortDestination(portDestination);
      uData.setUseSSLDestination(useSSLDestination);
      uData.setUseStartTLSDestination(useStartTLSDestination);
    }

    if (!cancelled)
    {
      LinkedList<String> suffixes = argParser.getBaseDNs();
      checkSuffixesForInitializeReplication(suffixes, ctxSource, ctxDestination,
          true);
      cancelled = suffixes.isEmpty();

      uData.setBaseDNs(suffixes);
    }

    if (!cancelled)
    {
      // Ask for confirmation to initialize.
      boolean initializeADS = false;
      for (String dn : uData.getBaseDNs())
      {
        if (Utils.areDnsEqual(ADSContext.getAdministrationSuffixDN(), dn))
        {
          initializeADS = true;
          break;
        }
      }
      String hostPortSource = ConnectionUtils.getHostPort(ctxSource);
      String hostPortDestination = ConnectionUtils.getHostPort(ctxDestination);
      if (initializeADS)
      {
        println();
        cancelled = !askConfirmation(
            INFO_REPLICATION_CONFIRM_INITIALIZE_ADS.get(
            ADSContext.getAdministrationSuffixDN(), hostPortDestination,
            hostPortSource), true, LOG);
        println();
      }
      else
      {
        println();
        cancelled = !askConfirmation(
            INFO_REPLICATION_CONFIRM_INITIALIZE_GENERIC.get(
            hostPortDestination, hostPortSource), true, LOG);
        println();
      }
    }

    if (ctxSource != null)
    {
      try
      {
        ctxSource.close();
      }
      catch (Throwable t)
      {
      }
    }

    if (ctxDestination != null)
    {
      try
      {
        ctxDestination.close();
      }
      catch (Throwable t)
      {
      }
    }
    return !cancelled;
  }

  /**
   * Commodity method that simply checks if a provided value is null or not,
   * if it is not <CODE>null</CODE> returns it and if it is <CODE>null</CODE>
   * returns the provided default value.
   * @param v the value to analyze.
   * @param defaultValue the default value.
   * @return if the provided value is not <CODE>null</CODE> returns it and if it
   * is <CODE>null</CODE> returns the provided default value.
   */
  private String getValue(String v, String defaultValue)
  {
    if (v != null)
    {
      return v;
    }
    else
    {
      return defaultValue;
    }
  }

  /**
   * Commodity method that simply checks if a provided value is -1 or not,
   * if it is not -1 returns it and if it is -1 returns the provided default
   * value.
   * @param v the value to analyze.
   * @param defaultValue the default value.
   * @return if the provided value is not -1 returns it and if it is -1 returns
   * the provided default value.
   */
  private int getValue(int v, int defaultValue)
  {
    if (v != -1)
    {
      return v;
    }
    else
    {
      return defaultValue;
    }
  }

  /**
   * Returns the trust manager to be used by this application.
   * @return the trust manager to be used by this application.
   */
  private ApplicationTrustManager getTrustManager()
  {
    ApplicationTrustManager trust;
    if (isInteractive())
    {
      TrustManager t = ci.getTrustManager();
      if (t == null)
      {
        trust = null;
      }
      else if (t instanceof ApplicationTrustManager)
      {
        trust = (ApplicationTrustManager)t;
      }
      else
      {
        trust = new ApplicationTrustManager(ci.getKeyStore());
      }
    }
    else
    {
      trust = argParser.getTrustManager();
    }
    return trust;
  }

  /**
   * Initializes the contents of the provided enable replication user data
   * object with what was provided in the command-line without prompting to the
   * user.
   * @param uData the enable replication user data object to be initialized.
   */
  private void initializeWithArgParser(EnableReplicationUserData uData)
  {
    uData.setBaseDNs(new LinkedList<String>(argParser.getBaseDNs()));
    String adminUid = getValue(argParser.getAdministratorUID(),
        argParser.getDefaultAdministratorUID());
    uData.setAdminUid(adminUid);
    String adminPwd = argParser.getBindPasswordAdmin();
    uData.setAdminPwd(adminPwd);

    String host1Name = getValue(argParser.getHostName1(),
        argParser.getDefaultHostName1());
    uData.setHostName1(host1Name);
    int port1 = getValue(argParser.getPort1(),
        argParser.getDefaultPort1());
    uData.setPort1(port1);
    uData.setUseSSL1(argParser.useSSL1());
    uData.setUseStartTLS1(argParser.useStartTLS1());
    String pwd1 = argParser.getBindPassword1();
    if (pwd1 == null)
    {
      uData.setBindDn1(ADSContext.getAdministratorDN(adminUid));
      uData.setPwd1(adminPwd);
    }
    else
    {
      // Best-effort: try to use admin, if it does not work, use bind DN.
      try
      {
        InitialLdapContext ctx = createAdministrativeContext(
            uData.getHostName1(), uData.getPort1(), uData.useSSL1(),
            uData.useStartTLS1(), ADSContext.getAdministratorDN(adminUid),
            adminPwd, getTrustManager());
        uData.setBindDn1(ADSContext.getAdministratorDN(adminUid));
        uData.setPwd1(adminPwd);
        ctx.close();
      }
      catch (Throwable t)
      {
        String bindDn = getValue(argParser.getBindDn1(),
            argParser.getDefaultBindDn1());
        uData.setBindDn1(bindDn);
        uData.setPwd1(pwd1);
      }
    }
    int replicationPort1 = getValue(argParser.getReplicationPort1(),
        argParser.getDefaultReplicationPort1());
    uData.setReplicationPort1(replicationPort1);
    uData.setSecureReplication1(argParser.isSecureReplication1());

    String host2Name = getValue(argParser.getHostName2(),
        argParser.getDefaultHostName2());
    uData.setHostName2(host2Name);
    int port2 = getValue(argParser.getPort2(),
        argParser.getDefaultPort2());
    uData.setPort2(port2);
    uData.setUseSSL2(argParser.useSSL2());
    uData.setUseStartTLS2(argParser.useStartTLS2());
    String pwd2 = argParser.getBindPassword2();
    if (pwd2 == null)
    {
      uData.setBindDn2(ADSContext.getAdministratorDN(adminUid));
      uData.setPwd2(adminPwd);
    }
    else
    {
      // Best-effort: try to use admin, if it does not work, use bind DN.
      try
      {
        InitialLdapContext ctx = createAdministrativeContext(
            uData.getHostName2(), uData.getPort2(), uData.useSSL2(),
            uData.useStartTLS2(), ADSContext.getAdministratorDN(adminUid),
            adminPwd, getTrustManager());
        uData.setBindDn2(ADSContext.getAdministratorDN(adminUid));
        uData.setPwd2(adminPwd);
        ctx.close();
      }
      catch (Throwable t)
      {
        String bindDn = getValue(argParser.getBindDn2(),
            argParser.getDefaultBindDn2());
        uData.setBindDn2(bindDn);
        uData.setPwd2(pwd2);
      }
    }
    int replicationPort2 = getValue(argParser.getReplicationPort2(),
        argParser.getDefaultReplicationPort2());
    uData.setReplicationPort2(replicationPort2);
    uData.setSecureReplication2(argParser.isSecureReplication2());
    uData.setReplicateSchema(!argParser.noSchemaReplication());
  }

  /**
   * Initializes the contents of the provided initialize replication user data
   * object with what was provided in the command-line without prompting to the
   * user.
   * @param uData the initialize replication user data object to be initialized.
   */
  private void initializeWithArgParser(InitializeReplicationUserData uData)
  {
    uData.setBaseDNs(new LinkedList<String>(argParser.getBaseDNs()));
    String adminUid = getValue(argParser.getAdministratorUID(),
        argParser.getDefaultAdministratorUID());
    uData.setAdminUid(adminUid);
    String adminPwd = argParser.getBindPasswordAdmin();
    uData.setAdminPwd(adminPwd);

    String hostNameSource = getValue(argParser.getHostNameSource(),
        argParser.getDefaultHostNameSource());
    uData.setHostNameSource(hostNameSource);
    int portSource = getValue(argParser.getPortSource(),
        argParser.getDefaultPortSource());
    uData.setPortSource(portSource);
    uData.setUseSSLSource(argParser.useSSLSource());
    uData.setUseStartTLSSource(argParser.useStartTLSSource());

    String hostNameDestination = getValue(
        argParser.getHostNameDestination(),
        argParser.getDefaultHostNameDestination());
    uData.setHostNameDestination(hostNameDestination);
    int portDestination = getValue(argParser.getPortDestination(),
        argParser.getDefaultPortDestination());
    uData.setPortDestination(portDestination);
    uData.setUseSSLDestination(argParser.useSSLDestination());
    uData.setUseStartTLSDestination(argParser.useStartTLSDestination());
  }

  /**
   * Initializes the contents of the provided disable replication user data
   * object with what was provided in the command-line without prompting to the
   * user.
   * @param uData the disable replication user data object to be initialized.
   */
  private void initializeWithArgParser(DisableReplicationUserData uData)
  {
    uData.setBaseDNs(new LinkedList<String>(argParser.getBaseDNs()));
    String adminUid = argParser.getAdministratorUID();
    String bindDn = argParser.getBindDN();
    if ((bindDn == null) && (adminUid == null))
    {
      adminUid = argParser.getDefaultAdministratorUID();
    }
    uData.setAdminUid(adminUid);
    uData.setBindDn(bindDn);
    String adminPwd = argParser.getBindPasswordAdmin();
    uData.setAdminPwd(adminPwd);

    String hostName = getValue(argParser.getHostNameToDisable(),
        argParser.getDefaultHostNameToDisable());
    uData.setHostName(hostName);
    int port = getValue(argParser.getPortToDisable(),
        argParser.getDefaultPortToDisable());
    uData.setPort(port);
    uData.setUseSSL(argParser.useSSLToDisable());
    uData.setUseStartTLS(argParser.useStartTLSToDisable());
  }

  /**
   * Initializes the contents of the provided initialize all replication user
   * data object with what was provided in the command-line without prompting to
   * the user.
   * @param uData the initialize all replication user data object to be
   * initialized.
   */
  private void initializeWithArgParser(InitializeAllReplicationUserData uData)
  {
    uData.setBaseDNs(new LinkedList<String>(argParser.getBaseDNs()));
    String adminUid = getValue(argParser.getAdministratorUID(),
        argParser.getDefaultAdministratorUID());
    uData.setAdminUid(adminUid);
    String adminPwd = argParser.getBindPasswordAdmin();
    uData.setAdminPwd(adminPwd);

    String hostName = getValue(argParser.getHostNameToInitializeAll(),
        argParser.getDefaultHostNameToInitializeAll());
    uData.setHostName(hostName);
    int port = getValue(argParser.getPortToInitializeAll(),
        argParser.getDefaultPortToInitializeAll());
    uData.setPort(port);
    uData.setUseSSL(argParser.useSSLToInitializeAll());
    uData.setUseStartTLS(argParser.useStartTLSToInitializeAll());
  }

  /**
   * Initializes the contents of the provided pre external replication user
   * data object with what was provided in the command-line without prompting to
   * the user.
   * @param uData the pre external replication user data object to be
   * initialized.
   */
  private void initializeWithArgParser(PreExternalInitializationUserData uData)
  {
    uData.setBaseDNs(new LinkedList<String>(argParser.getBaseDNs()));
    String adminUid = getValue(argParser.getAdministratorUID(),
        argParser.getDefaultAdministratorUID());
    uData.setAdminUid(adminUid);
    String adminPwd = argParser.getBindPasswordAdmin();
    uData.setAdminPwd(adminPwd);

    String hostName = getValue(argParser.getHostNameToInitializeAll(),
        argParser.getDefaultHostNameToInitializeAll());
    uData.setHostName(hostName);
    int port = getValue(argParser.getPortToInitializeAll(),
        argParser.getDefaultPortToInitializeAll());
    uData.setPort(port);
    uData.setUseSSL(argParser.useSSLToInitializeAll());
    uData.setUseStartTLS(argParser.useStartTLSToInitializeAll());
    uData.setOnlyLocal(argParser.isExternalInitializationOnlyInLocal());
  }

  /**
   * Initializes the contents of the provided post external replication user
   * data object with what was provided in the command-line without prompting to
   * the user.
   * @param uData the pre external replication user data object to be
   * initialized.
   */
  private void initializeWithArgParser(PostExternalInitializationUserData uData)
  {
    uData.setBaseDNs(new LinkedList<String>(argParser.getBaseDNs()));
    String adminUid = getValue(argParser.getAdministratorUID(),
        argParser.getDefaultAdministratorUID());
    uData.setAdminUid(adminUid);
    String adminPwd = argParser.getBindPasswordAdmin();
    uData.setAdminPwd(adminPwd);

    String hostName = getValue(argParser.getHostNameToInitializeAll(),
        argParser.getDefaultHostNameToInitializeAll());
    uData.setHostName(hostName);
    int port = getValue(argParser.getPortToInitializeAll(),
        argParser.getDefaultPortToInitializeAll());
    uData.setPort(port);
    uData.setUseSSL(argParser.useSSLToInitializeAll());
    uData.setUseStartTLS(argParser.useStartTLSToInitializeAll());
  }


  /**
   * Initializes the contents of the provided status replication user data
   * object with what was provided in the command-line without prompting to the
   * user.
   * @param uData the disable replication user data object to be initialized.
   */
  private void initializeWithArgParser(StatusReplicationUserData uData)
  {
    uData.setBaseDNs(new LinkedList<String>(argParser.getBaseDNs()));
    String adminUid = getValue(argParser.getAdministratorUID(),
        argParser.getDefaultAdministratorUID());
    uData.setAdminUid(adminUid);
    String adminPwd = argParser.getBindPasswordAdmin();
    uData.setAdminPwd(adminPwd);

    String hostName = getValue(argParser.getHostNameToStatus(),
        argParser.getDefaultHostNameToStatus());
    uData.setHostName(hostName);
    int port = getValue(argParser.getPortToStatus(),
        argParser.getDefaultPortToStatus());
    uData.setPort(port);
    uData.setUseSSL(argParser.useSSLToStatus());
    uData.setUseStartTLS(argParser.useStartTLSToStatus());

    uData.setScriptFriendly(argParser.isScriptFriendly());
  }

  /**
   * Tells whether the server to which the LdapContext is connected has a
   * replication port or not.
   * @param ctx the InitialLdapContext to be used.
   * @return <CODE>true</CODE> if the replication port for the server could
   * be found and <CODE>false</CODE> otherwise.
   */
  private boolean hasReplicationPort(InitialLdapContext ctx)
  {
    return getReplicationPort(ctx) != -1;
  }

  /**
   * Returns the replication port of server to which the LdapContext is
   * connected and -1 if the replication port could not be found.
   * @param ctx the InitialLdapContext to be used.
   * @return the replication port of server to which the LdapContext is
   * connected and -1 if the replication port could not be found.
   */
  private int getReplicationPort(InitialLdapContext ctx)
  {
    int replicationPort = -1;
    try
    {
      ManagementContext mCtx = LDAPManagementContext.createFromContext(
          JNDIDirContextAdaptor.adapt(ctx));
      RootCfgClient root = mCtx.getRootConfiguration();

      ReplicationSynchronizationProviderCfgClient sync = null;
      sync = (ReplicationSynchronizationProviderCfgClient)
      root.getSynchronizationProvider("Multimaster Synchronization");
      if (sync.hasReplicationServer())
      {
        ReplicationServerCfgClient replicationServer =
          sync.getReplicationServer();
        replicationPort = replicationServer.getReplicationPort();
      }
    }
    catch (Throwable t)
    {
      LOG.log(Level.WARNING,
          "Unexpected error retrieving the replication port: "+t, t);
    }
    return replicationPort;
  }

  /**
   * Loads the ADS with the provided context.  If there are certificates to
   * be accepted we prompt them to the user.  If there are errors loading the
   * servers we display them to the user and we ask for confirmation.  If the
   * provided ctx is not using Global Administrator credentials, we prompt the
   * user to provide them and update the provide ReplicationUserData
   * accordingly.
   * @param ctx the Ldap context to be used in an array: note the context
   * may be modified with the new credentials provided by the user.
   * @param uData the ReplicationUserData to be udpated.
   * @param isFirstOrSourceServer whether this is the first server in the
   * enable replication subcommand or the source server in the initialize server
   * subcommand.
   * @throws ReplicationCliException if a critical error occurred.
   * @return <CODE>true</CODE> if everything went fine and the user accepted
   * all the certificates and confirmed everything.  Returns <CODE>false</CODE>
   * if the user did not accept a certificate or any of the confirmation
   * messages.
   */
  private boolean loadADSAndAcceptCertificates(InitialLdapContext[] ctx,
      ReplicationUserData uData, boolean isFirstOrSourceServer)
  throws ReplicationCliException
  {
    boolean cancelled = false;
    boolean triedWithUserProvidedAdmin = false;
    String host = ConnectionUtils.getHostName(ctx[0]);
    int port = ConnectionUtils.getPort(ctx[0]);
    boolean isSSL = ConnectionUtils.isSSL(ctx[0]);
    boolean isStartTLS = ConnectionUtils.isStartTLS(ctx[0]);
    if (getTrustManager() == null)
    {
      // This is required when the user did  connect to the server using SSL or
      // Start TLS.  In this case LDAPConnectionInteraction.run does not
      // initialize the keystore and the trust manager is null.
      forceTrustManagerInitialization();
    }
    try
    {
      ADSContext adsContext = new ADSContext(ctx[0]);
      if (adsContext.hasAdminData())
      {
        boolean reloadTopology = true;
        LinkedList<Message> exceptionMsgs = new LinkedList<Message>();
        while (reloadTopology && !cancelled)
        {
          // We must recreate the cache because the trust manager in the
          // LDAPConnectionConsoleInteraction object might have changed.
          TopologyCache cache = new TopologyCache(adsContext,
              getTrustManager());
          cache.reloadTopology();

          reloadTopology = false;
          exceptionMsgs.clear();

          /* Analyze if we had any exception while loading servers.  For the
           * moment only throw the exception found if the user did not provide
           * the Administrator DN and this caused a problem authenticating in
           * one server or if there is a certificate problem.
           */
          Set<TopologyCacheException> exceptions =
            new HashSet<TopologyCacheException>();
          Set<ServerDescriptor> servers = cache.getServers();
          for (ServerDescriptor server : servers)
          {
            TopologyCacheException e = server.getLastException();
            if (e != null)
            {
              exceptions.add(e);
            }
          }
          /* Check the exceptions and see if we throw them or not. */
          boolean notGlobalAdministratorError = false;
          for (TopologyCacheException e : exceptions)
          {
            if (notGlobalAdministratorError)
            {
              break;
            }
            switch (e.getType())
            {
              case NOT_GLOBAL_ADMINISTRATOR:
                notGlobalAdministratorError = true;
                boolean connected = false;

                String adminUid = uData.getAdminUid();
                String adminPwd = uData.getAdminPwd();

                boolean errorDisplayed = false;
                while (!connected)
                {
                  if ((!triedWithUserProvidedAdmin) && (adminPwd == null))
                  {
                    adminUid = getValue(argParser.getAdministratorUID(),
                        argParser.getDefaultAdministratorUID());
                    adminPwd = argParser.getBindPasswordAdmin();
                    triedWithUserProvidedAdmin = true;
                  }
                  if (adminPwd == null)
                  {
                    if (!errorDisplayed)
                    {
                      println();
                      println(
                          INFO_NOT_GLOBAL_ADMINISTRATOR_PROVIDED.get());
                      errorDisplayed = true;
                    }
                    adminUid = askForAdministratorUID(
                        argParser.getDefaultAdministratorUID());
                    println();
                    adminPwd = askForAdministratorPwd();
                    println();
                  }
                  try
                  {
                    ctx[0].close();
                  }
                  catch (Throwable t)
                  {
                  }
                  try
                  {
                    ctx[0] = createAdministrativeContext(host, port, isSSL,
                        isStartTLS, ADSContext.getAdministratorDN(adminUid),
                        adminPwd, getTrustManager());
                    adsContext = new ADSContext(ctx[0]);
                    cache = new TopologyCache(adsContext, getTrustManager());
                    connected = true;
                  }
                  catch (Throwable t)
                  {
                    println();
                    println(
                        ERR_ERROR_CONNECTING_TO_SERVER_PROMPT_AGAIN.get(
                        host+":"+port, t.getMessage()));
                    LOG.log(Level.WARNING, "Complete error stack:", t);
                    println();
                  }
                }
                uData.setAdminUid(adminUid);
                uData.setAdminPwd(adminPwd);
                if (uData instanceof EnableReplicationUserData)
                {
                  EnableReplicationUserData enableData =
                    (EnableReplicationUserData)uData;
                  if (isFirstOrSourceServer)
                  {
                    enableData.setBindDn1(
                        ADSContext.getAdministratorDN(adminUid));
                    enableData.setPwd1(adminPwd);
                  }
                  else
                  {
                    enableData.setBindDn2(
                        ADSContext.getAdministratorDN(adminUid));
                    enableData.setPwd2(adminPwd);
                  }
                }
                reloadTopology = true;
              break;
            case GENERIC_CREATING_CONNECTION:
              if ((e.getCause() != null) &&
                  Utils.isCertificateException(e.getCause()))
              {
                reloadTopology = true;
                cancelled = !ci.promptForCertificateConfirmation(e.getCause(),
                    e.getTrustManager(), e.getLdapUrl(), true, LOG);
              }
              else
              {
                exceptionMsgs.add(Utils.getMessage(e));
              }
              break;
            default:
              exceptionMsgs.add(Utils.getMessage(e));
            }
          }
        }
        if ((exceptionMsgs.size() > 0) && !cancelled)
        {
          if (uData instanceof StatusReplicationUserData)
          {
            println(
                ERR_REPLICATION_STATUS_READING_REGISTERED_SERVERS.get(
                    Utils.getMessageFromCollection(exceptionMsgs,
                        Constants.LINE_SEPARATOR).toString()));
            println();
          }
          else
          {
            cancelled = !askConfirmation(
               ERR_REPLICATION_READING_REGISTERED_SERVERS_CONFIRM_UPDATE_REMOTE.
                get(Utils.getMessageFromCollection(exceptionMsgs,
                    Constants.LINE_SEPARATOR).toString()), true, LOG);
          }
        }
      }
    }
    catch (ADSContextException ace)
    {
      LOG.log(Level.SEVERE, "Complete error stack:", ace);
      throw new ReplicationCliException(
          ERR_REPLICATION_READING_ADS.get(ace.getMessage()),
          ERROR_READING_ADS, ace);
    }
    catch (TopologyCacheException tce)
    {
      LOG.log(Level.SEVERE, "Complete error stack:", tce);
      throw new ReplicationCliException(
          ERR_REPLICATION_READING_ADS.get(tce.getMessage()),
          ERROR_READING_TOPOLOGY_CACHE, tce);
    }
    return !cancelled;
  }

  /**
   * Tells whether there is a Global Administrator defined in the server
   * to which the InitialLdapContext is connected.
   * @param ctx the InitialLdapContext.
   * @return <CODE>true</CODE> if we could find an administrator and
   * <CODE>false</CODE> otherwise.
   */
  private boolean hasAdministrator(InitialLdapContext ctx)
  {
    boolean isAdminDefined = false;
    try
    {
      ADSContext adsContext = new ADSContext(ctx);
      if (adsContext.hasAdminData())
      {
        Set administrators = adsContext.readAdministratorRegistry();
        isAdminDefined = administrators.size() > 0;
      }
    }
    catch (Throwable t)
    {
      LOG.log(Level.WARNING,
          "Unexpected error retrieving the ADS data: "+t, t);
    }
    return isAdminDefined;
  }

  /**
   * Helper type for the <CODE>getCommonSuffixes</CODE> method.
   */
  private enum SuffixRelationType
  {
    NOT_REPLICATED, FULLY_REPLICATED, REPLICATED, NOT_FULLY_REPLICATED, ALL
  }

  /**
   * Returns a Collection containing a list of suffixes that are defined in
   * two servers at the same time (depending on the value of the argument
   * replicated this list contains only the suffixes that are replicated
   * between the servers or the list of suffixes that are not replicated
   * between the servers).
   * @param ctx1 the connection to the first server.
   * @param ctx2 the connection to the second server.
   * @param type whether to return a list with the suffixes that are
   * replicated, fully replicated (replicas have exactly the same list of
   * replication servers), not replicated or all the common suffixes.
   * @return a Collection containing a list of suffixes that are replicated
   * (or those that can be replicated) in two servers.
   */
  private Collection<String> getCommonSuffixes(
      InitialLdapContext ctx1, InitialLdapContext ctx2, SuffixRelationType type)
  {
    LinkedList<String> suffixes = new LinkedList<String>();
    try
    {
      ServerDescriptor server1 = ServerDescriptor.createStandalone(ctx1);
      ServerDescriptor server2 = ServerDescriptor.createStandalone(ctx2);
      Set<ReplicaDescriptor> replicas1 = server1.getReplicas();
      Set<ReplicaDescriptor> replicas2 = server2.getReplicas();

      for (ReplicaDescriptor rep1 : replicas1)
      {
        for (ReplicaDescriptor rep2 : replicas2)
        {

          switch (type)
          {
          case NOT_REPLICATED:
            if (!areReplicated(rep1, rep2) &&
                Utils.areDnsEqual(rep1.getSuffix().getDN(),
                    rep2.getSuffix().getDN()))
            {
              suffixes.add(rep1.getSuffix().getDN());
            }
            break;
          case FULLY_REPLICATED:
            if (areFullyReplicated(rep1, rep2))
            {
              suffixes.add(rep1.getSuffix().getDN());
            }
            break;
          case REPLICATED:
            if (areReplicated(rep1, rep2))
            {
              suffixes.add(rep1.getSuffix().getDN());
            }
            break;
          case NOT_FULLY_REPLICATED:
            if (!areFullyReplicated(rep1, rep2) &&
                Utils.areDnsEqual(rep1.getSuffix().getDN(),
                    rep2.getSuffix().getDN()))
            {
              suffixes.add(rep1.getSuffix().getDN());
            }
            break;
          case ALL:
            if (Utils.areDnsEqual(rep1.getSuffix().getDN(),
                rep2.getSuffix().getDN()))
            {
              suffixes.add(rep1.getSuffix().getDN());
            }
            break;
            default:
              throw new IllegalStateException("Unknown type: "+type);
          }
        }
      }
    }
    catch (Throwable t)
    {
      LOG.log(Level.WARNING,
          "Unexpected error retrieving the server configuration: "+t, t);
    }
    return suffixes;
  }

  /**
   * Tells whether the two provided replicas are fully replicated or not.  The
   * code in fact checks that both replicas have the same DN that they are
   * replicated if both servers are replication servers and that both replicas
   * make reference to the other replication server.
   * @param rep1 the first replica.
   * @param rep2 the second replica.
   * @return <CODE>true</CODE> if we can assure that the two replicas are
   * replicated using the replication server and replication port information
   * and <CODE>false</CODE> otherwise.
   */
  private boolean areFullyReplicated(ReplicaDescriptor rep1,
      ReplicaDescriptor rep2)
  {
    boolean areFullyReplicated = false;
    if (Utils.areDnsEqual(rep1.getSuffix().getDN(), rep2.getSuffix().getDN()) &&
        rep1.isReplicated() && rep2.isReplicated() &&
        rep1.getServer().isReplicationServer() &&
        rep2.getServer().isReplicationServer())
    {
     Set<String> servers1 = rep1.getReplicationServers();
     Set<String> servers2 = rep2.getReplicationServers();
     String server1 = rep1.getServer().getReplicationServerHostPort();
     String server2 = rep2.getServer().getReplicationServerHostPort();
     areFullyReplicated = servers1.contains(server2) &&
     servers2.contains(server1);
    }
    return areFullyReplicated;
  }

  /**
   * Tells whether the two provided replicas are replicated or not.  The
   * code in fact checks that both replicas have the same DN and that they
   * have at least one common replication server referenced.
   * @param rep1 the first replica.
   * @param rep2 the second replica.
   * @return <CODE>true</CODE> if we can assure that the two replicas are
   * replicated and <CODE>false</CODE> otherwise.
   */
  private boolean areReplicated(ReplicaDescriptor rep1, ReplicaDescriptor rep2)
  {
    boolean areReplicated = false;
    if (Utils.areDnsEqual(rep1.getSuffix().getDN(), rep2.getSuffix().getDN()) &&
        rep1.isReplicated() && rep2.isReplicated())
    {
      Set<String> servers1 = rep1.getReplicationServers();
      Set<String> servers2 = rep2.getReplicationServers();
      servers1.retainAll(servers2);
      areReplicated = !servers1.isEmpty();
    }
    return areReplicated;
  }

  /**
   * Returns a Collection containing a list of replicas in a server.
   * @param ctx the connection to the server.
   * @return a Collection containing a list of replicas in a server.
   */
  private Collection<ReplicaDescriptor> getReplicas(InitialLdapContext ctx)
  {
    LinkedList<ReplicaDescriptor> suffixes =
      new LinkedList<ReplicaDescriptor>();
    try
    {
      ServerDescriptor server = ServerDescriptor.createStandalone(ctx);
      suffixes.addAll(server.getReplicas());
    }
    catch (Throwable t)
    {
      LOG.log(Level.WARNING,
          "Unexpected error retrieving the server configuration: "+t, t);
    }
    return suffixes;
  }

  /**
   * Enables the replication between two servers using the parameters in the
   * provided EnableReplicationUserData.  This method does not prompt to the
   * user for information if something is missing.
   * @param uData the EnableReplicationUserData object.
   * @return ReplicationCliReturnCode.SUCCESSFUL if the operation was
   * successful and the replication could be enabled and an error code
   * otherwise.
   */
  private ReplicationCliReturnCode enableReplication(
      EnableReplicationUserData uData)
  {
    ReplicationCliReturnCode returnValue = SUCCESSFUL_NOP;
    InitialLdapContext ctx1 = null;
    InitialLdapContext ctx2 = null;

    String host1 = uData.getHostName1();
    String host2 = uData.getHostName2();
    int port1 = uData.getPort1();
    int port2 = uData.getPort2();

    LinkedList<Message> errorMessages = new LinkedList<Message>();

    printlnProgress();
    printProgress(
        formatter.getFormattedWithPoints(INFO_REPLICATION_CONNECTING.get()));
    try
    {
      ctx1 = createAdministrativeContext(host1, port1, uData.useSSL1(),
          uData.useStartTLS1(), uData.getBindDn1(), uData.getPwd1(),
          getTrustManager());
    }
    catch (NamingException ne)
    {
      String hostPort = host1+":"+port1;
      errorMessages.add(getMessageForException(ne, hostPort));

      LOG.log(Level.SEVERE, "Complete error stack:", ne);
    }
    try
    {
      ctx2 = createAdministrativeContext(host2, port2, uData.useSSL2(),
          uData.useStartTLS2(), uData.getBindDn2(), uData.getPwd2(),
          getTrustManager());
    }
    catch (NamingException ne)
    {
      String hostPort = host2+":"+port2;
      errorMessages.add(getMessageForException(ne, hostPort));

      LOG.log(Level.SEVERE, "Complete error stack:", ne);
    }

    if (errorMessages.size() > 0)
    {
      returnValue = ERROR_CONNECTING;
    }

    if (errorMessages.isEmpty())
    {
      // This done is for the message informing that we are connecting.
      printProgress(formatter.getFormattedDone());
      printlnProgress();

//    If we are not in interactive mode do some checks...
      if (!argParser.isInteractive())
      {
        boolean hasReplicationPort1 = hasReplicationPort(ctx1);
        boolean hasReplicationPort2 = hasReplicationPort(ctx2);
        int replPort1 = uData.getReplicationPort1();
        int replPort2 = uData.getReplicationPort2();

        if (!hasReplicationPort1)
        {
          if (!argParser.skipReplicationPortCheck() &&
              isLocalHost(host1) &&
              !SetupUtils.canUseAsPort(replPort1))
          {
            errorMessages.add(getCannotBindToPortError(replPort1));
          }
        }
        if (!hasReplicationPort2)
        {
          if (!argParser.skipReplicationPortCheck() &&
              isLocalHost(host2) &&
              !SetupUtils.canUseAsPort(replPort2))
          {
            errorMessages.add(getCannotBindToPortError(replPort2));
          }
        }
        if (!hasReplicationPort1 && !hasReplicationPort2 &&
            (replPort1 == replPort2) &&
            (host1.equalsIgnoreCase(host2)))
        {
          errorMessages.add(ERR_REPLICATION_SAME_REPLICATION_PORT.get(
              String.valueOf(replPort1), host1));
        }

        if (argParser.skipReplicationPortCheck())
        {
          // This is something that we must do in any case... this test is
          // already included when we call SetupUtils.canUseAsPort
          if (replPort1 == port1)
          {
            errorMessages.add(
                ERR_REPLICATION_PORT_AND_REPLICATION_PORT_EQUAL.get(
                host1, String.valueOf(replPort1)));
          }

          if (replPort2 == port2)
          {
            errorMessages.add(
                ERR_REPLICATION_PORT_AND_REPLICATION_PORT_EQUAL.get(
                host2, String.valueOf(replPort2)));
          }
        }
      }
      if (errorMessages.size() > 0)
      {
        returnValue = ERROR_USER_DATA;
      }
    }

    if (errorMessages.isEmpty())
    {
      LinkedList<String> suffixes = uData.getBaseDNs();
      checkSuffixesForEnableReplication(suffixes, ctx1, ctx2, false);
      if (!suffixes.isEmpty())
      {
        uData.setBaseDNs(suffixes);
        try
        {
          updateConfiguration(ctx1, ctx2, uData);
          returnValue = SUCCESSFUL;
        }
        catch (ReplicationCliException rce)
        {
          returnValue = rce.getErrorCode();
          println();
          println(getCriticalExceptionMessage(rce));
          LOG.log(Level.SEVERE, "Complete error stack:", rce);
        }
      }
      else
      {
        // The error messages are already displayed in the method
        // checkSuffixesForEnableReplication.
        returnValue = REPLICATION_CANNOT_BE_ENABLED_ON_BASEDN;
      }
    }

    for (Message msg : errorMessages)
    {
      println();
      println(msg);
    }

    long time1 = Utils.getServerClock(ctx1);
    long time2 = Utils.getServerClock(ctx2);
    if ((time1 != -1) && (time2 != -1))
    {
      if (Math.abs(time1 - time2) >
      (Installer.WARNING_CLOCK_DIFFERENCE_THRESOLD_MINUTES * 60 * 1000))
      {
        println(INFO_WARNING_SERVERS_CLOCK_DIFFERENCE.get(
            ConnectionUtils.getHostPort(ctx1),
            ConnectionUtils.getHostPort(ctx2),
            String.valueOf(
                Installer.WARNING_CLOCK_DIFFERENCE_THRESOLD_MINUTES)));
      }
    }

    if (ctx1 != null)
    {
      try
      {
        ctx1.close();
      }
      catch (Throwable t)
      {
      }
    }

    if (ctx2 != null)
    {
      try
      {
        ctx2.close();
      }
      catch (Throwable t)
      {
      }
    }
    return returnValue;
  }

  /**
   * Disbles the replication in the server for the provided suffixes using the
   * data in the DisableReplicationUserData object.  This method does not prompt
   * to the user for information if something is missing.
   * @param uData the DisableReplicationUserData object.
   * @return ReplicationCliReturnCode.SUCCESSFUL if the operation was
   * successful and an error code otherwise.
   */
  private ReplicationCliReturnCode disableReplication(
      DisableReplicationUserData uData)
  {
    ReplicationCliReturnCode returnValue = SUCCESSFUL_NOP;
    InitialLdapContext ctx = null;
    printProgress(
        formatter.getFormattedWithPoints(INFO_REPLICATION_CONNECTING.get()));
    String bindDn = uData.getAdminUid() == null ? uData.getBindDn() :
      ADSContext.getAdministratorDN(uData.getAdminUid());
    try
    {
      ctx = createAdministrativeContext(uData.getHostName(), uData.getPort(),
          uData.useSSL(), uData.useStartTLS(), bindDn, uData.getAdminPwd(),
          getTrustManager());
    }
    catch (NamingException ne)
    {
      String hostPort = uData.getHostName()+":"+uData.getPort();
      println();
      println(getMessageForException(ne, hostPort));
      LOG.log(Level.SEVERE, "Complete error stack:", ne);
    }

    if (ctx != null)
    {
      // This done is for the message informing that we are connecting.
      printProgress(formatter.getFormattedDone());
      printlnProgress();
      LinkedList<String> suffixes = uData.getBaseDNs();
      checkSuffixesForDisableReplication(suffixes, ctx, false);
      if (!suffixes.isEmpty())
      {
        uData.setBaseDNs(suffixes);
        try
        {
          updateConfiguration(ctx, uData);
          returnValue = SUCCESSFUL;
        }
        catch (ReplicationCliException rce)
        {
          returnValue = rce.getErrorCode();
          println();
          println(getCriticalExceptionMessage(rce));
          LOG.log(Level.SEVERE, "Complete error stack:", rce);
        }
      }
      else
      {
        returnValue = REPLICATION_CANNOT_BE_DISABLED_ON_BASEDN;
      }
    }
    else
    {
      returnValue = ERROR_CONNECTING;
    }

    if (ctx != null)
    {
      try
      {
        ctx.close();
      }
      catch (Throwable t)
      {
      }
    }
    return returnValue;
  }

  /**
   * Displays the replication status of the baseDNs specified in the
   * StatusReplicationUserData object.  This method does not prompt
   * to the user for information if something is missing.
   * @param uData the StatusReplicationUserData object.
   * @return ReplicationCliReturnCode.SUCCESSFUL if the operation was
   * successful and an error code otherwise.
   */
  private ReplicationCliReturnCode statusReplication(
      StatusReplicationUserData uData)
  {
    ReplicationCliReturnCode returnValue = SUCCESSFUL_NOP;
    InitialLdapContext ctx = null;
    try
    {
      ctx = createAdministrativeContext(uData.getHostName(), uData.getPort(),
          uData.useSSL(), uData.useStartTLS(),
          ADSContext.getAdministratorDN(uData.getAdminUid()),
          uData.getAdminPwd(), getTrustManager());
    }
    catch (NamingException ne)
    {
      String hostPort = uData.getHostName()+":"+uData.getPort();
      println();
      println(getMessageForException(ne, hostPort));
      LOG.log(Level.SEVERE, "Complete error stack:", ne);
    }

    if (ctx != null)
    {
      uData.setBaseDNs(uData.getBaseDNs());
      try
      {
        displayStatus(ctx, uData);
        returnValue = SUCCESSFUL;
      }
      catch (ReplicationCliException rce)
      {
        returnValue = rce.getErrorCode();
        println();
        println(getCriticalExceptionMessage(rce));
        LOG.log(Level.SEVERE, "Complete error stack:", rce);
      }
    }
    else
    {
      returnValue = ERROR_CONNECTING;
    }

    if (ctx != null)
    {
      try
      {
        ctx.close();
      }
      catch (Throwable t)
      {
      }
    }
    return returnValue;
  }

  /**
   * Initializes the contents of one server with the contents of the other
   * using the parameters in the provided InitializeReplicationUserData.
   * This method does not prompt to the user for information if something is
   * missing.
   * @param uData the InitializeReplicationUserData object.
   * @return ReplicationCliReturnCode.SUCCESSFUL if the operation was
   * successful and an error code otherwise.
   */
  private ReplicationCliReturnCode initializeReplication(
      InitializeReplicationUserData uData)
  {
    ReplicationCliReturnCode returnValue = SUCCESSFUL_NOP;
    InitialLdapContext ctxSource = null;
    InitialLdapContext ctxDestination = null;
    try
    {
      ctxSource = createAdministrativeContext(uData.getHostNameSource(),
          uData.getPortSource(), uData.useSSLSource(),
          uData.useStartTLSSource(),
          ADSContext.getAdministratorDN(uData.getAdminUid()),
          uData.getAdminPwd(), getTrustManager());
    }
    catch (NamingException ne)
    {
      String hostPort = uData.getHostNameSource()+":"+uData.getPortSource();
      println();
      println(getMessageForException(ne, hostPort));
      LOG.log(Level.SEVERE, "Complete error stack:", ne);
    }
    try
    {
      ctxDestination = createAdministrativeContext(
          uData.getHostNameDestination(),
          uData.getPortDestination(), uData.useSSLDestination(),
          uData.useStartTLSDestination(),
          ADSContext.getAdministratorDN(uData.getAdminUid()),
          uData.getAdminPwd(), getTrustManager());
    }
    catch (NamingException ne)
    {
      String hostPort = uData.getHostNameDestination()+":"+
      uData.getPortDestination();
      println();
      println(getMessageForException(ne, hostPort));
      LOG.log(Level.SEVERE, "Complete error stack:", ne);
    }
    if ((ctxSource != null) && (ctxDestination != null))
    {
      LinkedList<String> baseDNs = uData.getBaseDNs();
      checkSuffixesForInitializeReplication(baseDNs, ctxSource, ctxDestination,
          false);
      if (!baseDNs.isEmpty())
      {
        for (String baseDN : baseDNs)
        {
          try
          {
            printlnProgress();
            Message msg = formatter.getFormattedProgress(
                INFO_PROGRESS_INITIALIZING_SUFFIX.get(baseDN,
                    ConnectionUtils.getHostPort(ctxSource)));
            printProgress(msg);
            printlnProgress();
            initializeSuffix(baseDN, ctxSource, ctxDestination, true);
          }
          catch (ReplicationCliException rce)
          {
            println();
            println(getCriticalExceptionMessage(rce));
            returnValue = rce.getErrorCode();
            LOG.log(Level.SEVERE, "Complete error stack:", rce);
          }
        }
      }
      else
      {
        returnValue = REPLICATION_CANNOT_BE_INITIALIZED_ON_BASEDN;
      }
    }
    else
    {
      returnValue = ERROR_CONNECTING;
    }

    if (ctxSource != null)
    {
      try
      {
        ctxSource.close();
      }
      catch (Throwable t)
      {
      }
    }

    if (ctxDestination != null)
    {
      try
      {
        ctxDestination.close();
      }
      catch (Throwable t)
      {
      }
    }
    return returnValue;
  }

  /**
   * Initializes the contents of a whole topology with the contents of the other
   * using the parameters in the provided InitializeAllReplicationUserData.
   * This method does not prompt to the user for information if something is
   * missing.
   * @param uData the InitializeAllReplicationUserData object.
   * @return ReplicationCliReturnCode.SUCCESSFUL if the operation was
   * successful and an error code otherwise.
   */
  private ReplicationCliReturnCode initializeAllReplication(
      InitializeAllReplicationUserData uData)
  {
    ReplicationCliReturnCode returnValue = SUCCESSFUL_NOP;
    InitialLdapContext ctx = null;
    try
    {
      ctx = createAdministrativeContext(uData.getHostName(), uData.getPort(),
          uData.useSSL(), uData.useStartTLS(),
          ADSContext.getAdministratorDN(uData.getAdminUid()),
          uData.getAdminPwd(), getTrustManager());
    }
    catch (NamingException ne)
    {
      String hostPort = uData.getHostName()+":"+uData.getPort();
      println();
      println(getMessageForException(ne, hostPort));
      LOG.log(Level.SEVERE, "Complete error stack:", ne);
    }
    if (ctx != null)
    {
      LinkedList<String> baseDNs = uData.getBaseDNs();
      checkSuffixesForInitializeReplication(baseDNs, ctx, false);
      if (!baseDNs.isEmpty())
      {
        for (String baseDN : baseDNs)
        {
          try
          {
            printlnProgress();
            Message msg = formatter.getFormattedProgress(
                INFO_PROGRESS_INITIALIZING_SUFFIX.get(baseDN,
                    ConnectionUtils.getHostPort(ctx)));
            printProgress(msg);
            println();
            initializeAllSuffix(baseDN, ctx, true);
          }
          catch (ReplicationCliException rce)
          {
            println();
            println(getCriticalExceptionMessage(rce));
            returnValue = rce.getErrorCode();
            LOG.log(Level.SEVERE, "Complete error stack:", rce);
          }
        }
      }
      else
      {
        returnValue = REPLICATION_CANNOT_BE_INITIALIZED_ON_BASEDN;
      }
    }
    else
    {
      returnValue = ERROR_CONNECTING;
    }

    if (ctx != null)
    {
      try
      {
        ctx.close();
      }
      catch (Throwable t)
      {
      }
    }

    return returnValue;
  }

  /**
   * Performs the operation that must be made before initializing the topology
   * using the import-ldif command or the binary copy.  The operation uses
   * the parameters in the provided InitializeAllReplicationUserData.
   * This method does not prompt to the user for information if something is
   * missing.
   * @param uData the PreExternalInitializationUserData object.
   * @return ReplicationCliReturnCode.SUCCESSFUL if the operation was
   * successful and an error code otherwise.
   */
  private ReplicationCliReturnCode preExternalInitialization(
      PreExternalInitializationUserData uData)
  {
    ReplicationCliReturnCode returnValue = SUCCESSFUL_NOP;
    InitialLdapContext ctx = null;
    try
    {
      ctx = createAdministrativeContext(uData.getHostName(), uData.getPort(),
          uData.useSSL(), uData.useStartTLS(),
          ADSContext.getAdministratorDN(uData.getAdminUid()),
          uData.getAdminPwd(), getTrustManager());
    }
    catch (NamingException ne)
    {
      String hostPort = uData.getHostName()+":"+uData.getPort();
      println();
      println(getMessageForException(ne, hostPort));
      LOG.log(Level.SEVERE, "Complete error stack:", ne);
    }
    if (ctx != null)
    {
      LinkedList<String> baseDNs = uData.getBaseDNs();
      checkSuffixesForInitializeReplication(baseDNs, ctx, false);
      if (!baseDNs.isEmpty())
      {
        for (String baseDN : baseDNs)
        {
          try
          {
            printlnProgress();
            Message msg = formatter.getFormattedWithPoints(
                INFO_PROGRESS_PRE_EXTERNAL_INITIALIZATION.get(baseDN));
            printProgress(msg);
            preExternalInitialization(baseDN, ctx, uData.isOnlyLocal(), false);
            printProgress(formatter.getFormattedDone());
            printlnProgress();
          }
          catch (ReplicationCliException rce)
          {
            println();
            println(getCriticalExceptionMessage(rce));
            returnValue = rce.getErrorCode();
            LOG.log(Level.SEVERE, "Complete error stack:", rce);
          }
        }
        if (uData.isOnlyLocal())
        {
          printlnProgress();
          printProgress(
              INFO_PROGRESS_PRE_INITIALIZATION_LOCAL_FINISHED_PROCEDURE.get(
                  ConnectionUtils.getHostPort(ctx),
                  ReplicationCliArgumentParser.
                  POST_EXTERNAL_INITIALIZATION_SUBCMD_NAME));
          printlnProgress();
        }
        else
        {
          printlnProgress();
          printProgress(
            INFO_PROGRESS_PRE_INITIALIZATION_FINISHED_PROCEDURE.get(
                ReplicationCliArgumentParser.
                POST_EXTERNAL_INITIALIZATION_SUBCMD_NAME));
          printlnProgress();
        }
      }
      else
      {
        returnValue = REPLICATION_CANNOT_BE_INITIALIZED_ON_BASEDN;
      }
    }
    else
    {
      returnValue = ERROR_CONNECTING;
    }

    if (ctx != null)
    {
      try
      {
        ctx.close();
      }
      catch (Throwable t)
      {
      }
    }

    return returnValue;
  }

  /**
   * Performs the operation that must be made after initializing the topology
   * using the import-ldif command or the binary copy.  The operation uses
   * the parameters in the provided InitializeAllReplicationUserData.
   * This method does not prompt to the user for information if something is
   * missing.
   * @param uData the PostExternalInitializationUserData object.
   * @return ReplicationCliReturnCode.SUCCESSFUL if the operation was
   * successful and an error code otherwise.
   */
  private ReplicationCliReturnCode postExternalInitialization(
      PostExternalInitializationUserData uData)
  {
    ReplicationCliReturnCode returnValue = SUCCESSFUL_NOP;
    InitialLdapContext ctx = null;
    try
    {
      ctx = createAdministrativeContext(uData.getHostName(), uData.getPort(),
          uData.useSSL(), uData.useStartTLS(),
          ADSContext.getAdministratorDN(uData.getAdminUid()),
          uData.getAdminPwd(), getTrustManager());
    }
    catch (NamingException ne)
    {
      String hostPort = uData.getHostName()+":"+uData.getPort();
      println();
      println(getMessageForException(ne, hostPort));
      LOG.log(Level.SEVERE, "Complete error stack:", ne);
    }
    if (ctx != null)
    {
      LinkedList<String> baseDNs = uData.getBaseDNs();
      checkSuffixesForInitializeReplication(baseDNs, ctx, false);
      if (!baseDNs.isEmpty())
      {
        for (String baseDN : baseDNs)
        {
          try
          {
            printlnProgress();
            Message msg = formatter.getFormattedWithPoints(
                INFO_PROGRESS_POST_EXTERNAL_INITIALIZATION.get(baseDN));
            printProgress(msg);
            postExternalInitialization(baseDN, ctx, false);
            printProgress(formatter.getFormattedDone());
            printlnProgress();
          }
          catch (ReplicationCliException rce)
          {
            println();
            println(getCriticalExceptionMessage(rce));
            returnValue = rce.getErrorCode();
            LOG.log(Level.SEVERE, "Complete error stack:", rce);
          }
        }
        printlnProgress();
        printProgress(
            INFO_PROGRESS_POST_INITIALIZATION_FINISHED_PROCEDURE.get());
        printlnProgress();
      }
      else
      {
        returnValue = REPLICATION_CANNOT_BE_INITIALIZED_ON_BASEDN;
      }
    }
    else
    {
      returnValue = ERROR_CONNECTING;
    }

    if (ctx != null)
    {
      try
      {
        ctx.close();
      }
      catch (Throwable t)
      {
      }
    }

    return returnValue;
  }


  /**
   * Checks that replication can actually be enabled in the provided baseDNs
   * for the two servers.
   * @param suffixes the suffixes provided by the user.  This Collection is
   * updated by removing the base DNs that cannot be enabled and with the
   * base DNs that the user provided interactively.
   * @param ctx1 connection to the first server.
   * @param ctx2 connection to the second server.
   * @param interactive whether to ask the user to provide interactively
   * base DNs if none of the provided base DNs can be enabled.
   */
  private void checkSuffixesForEnableReplication(Collection<String> suffixes,
      InitialLdapContext ctx1, InitialLdapContext ctx2,
      boolean interactive)
  {
    TreeSet<String> availableSuffixes =
      new TreeSet<String>(getCommonSuffixes(ctx1, ctx2,
          SuffixRelationType.NOT_FULLY_REPLICATED));
    TreeSet<String> alreadyReplicatedSuffixes =
      new TreeSet<String>(getCommonSuffixes(ctx1, ctx2,
          SuffixRelationType.FULLY_REPLICATED));

    if (availableSuffixes.size() == 0)
    {
      println();
      println(
          ERR_NO_SUFFIXES_AVAILABLE_TO_ENABLE_REPLICATION.get());

      LinkedList<String> userProvidedSuffixes = argParser.getBaseDNs();
      TreeSet<String> userProvidedReplicatedSuffixes = new TreeSet<String>();

      for (String s1 : userProvidedSuffixes)
      {
        for (String s2 : alreadyReplicatedSuffixes)
        {
          if (Utils.areDnsEqual(s1, s2))
          {
            userProvidedReplicatedSuffixes.add(s1);
          }
        }
      }
      if (userProvidedReplicatedSuffixes.size() > 0)
      {
        println();
        println(
            INFO_ALREADY_REPLICATED_SUFFIXES.get(
                Utils.getStringFromCollection(userProvidedReplicatedSuffixes,
                    Constants.LINE_SEPARATOR)));
      }
      suffixes.clear();
    }
    else
    {
      //  Verify that the provided suffixes are configured in the servers.
      TreeSet<String> notFound = new TreeSet<String>();
      TreeSet<String> alreadyReplicated = new TreeSet<String>();
      for (String dn : suffixes)
      {
        boolean found = false;
        for (String dn1 : availableSuffixes)
        {
          if (Utils.areDnsEqual(dn, dn1))
          {
            found = true;
            break;
          }
        }
        if (!found)
        {
          boolean isReplicated = false;
          for (String s : alreadyReplicatedSuffixes)
          {
            if (Utils.areDnsEqual(s, dn))
            {
              isReplicated = true;
              break;
            }
          }
          if (isReplicated)
          {
            alreadyReplicated.add(dn);
          }
          else
          {
            notFound.add(dn);
          }
        }
      }
      suffixes.removeAll(notFound);
      suffixes.removeAll(alreadyReplicated);
      if (notFound.size() > 0)
      {
        println();
        println(ERR_REPLICATION_ENABLE_SUFFIXES_NOT_FOUND.get(
              Utils.getStringFromCollection(notFound,
                  Constants.LINE_SEPARATOR)));
      }
      if (alreadyReplicated.size() > 0)
      {
        println();
        println(INFO_ALREADY_REPLICATED_SUFFIXES.get(
            Utils.getStringFromCollection(alreadyReplicated,
                Constants.LINE_SEPARATOR)));
      }
      if (interactive)
      {

        while (suffixes.isEmpty())
        {
          boolean noSchemaOrAds = false;
          for (String s: availableSuffixes)
          {
            if (!Utils.areDnsEqual(s, ADSContext.getAdministrationSuffixDN()) &&
                !Utils.areDnsEqual(s, Constants.SCHEMA_DN) &&
                !Utils.areDnsEqual(s, Constants.REPLICATION_CHANGES_DN))
            {
              noSchemaOrAds = true;
            }
          }
          if (!noSchemaOrAds)
          {
            // In interactive mode we do not propose to manage the
            // administration suffix.
            println();
            println(
                ERR_NO_SUFFIXES_AVAILABLE_TO_ENABLE_REPLICATION.get());
            break;
          }
          else
          {
            println();
            println(ERR_NO_SUFFIXES_SELECTED_TO_REPLICATE.get());
            for (String dn : availableSuffixes)
            {
              if (!Utils.areDnsEqual(dn,
                  ADSContext.getAdministrationSuffixDN()) &&
                  !Utils.areDnsEqual(dn, Constants.SCHEMA_DN) &&
                  !Utils.areDnsEqual(dn, Constants.REPLICATION_CHANGES_DN))
              {
                if (askConfirmation(
                    INFO_REPLICATION_ENABLE_SUFFIX_PROMPT.get(dn), true, LOG))
                {
                  suffixes.add(dn);
                }
              }
            }
          }
        }
      }
    }
  }

  /**
   * Checks that replication can actually be disabled in the provided baseDNs
   * for the server.
   * @param suffixes the suffixes provided by the user.  This Collection is
   * updated by removing the base DNs that cannot be disabled and with the
   * base DNs that the user provided interactively.
   * @param ctx connection to the server.
   * @param interactive whether to ask the user to provide interactively
   * base DNs if none of the provided base DNs can be disabled.
   */
  private void checkSuffixesForDisableReplication(Collection<String> suffixes,
      InitialLdapContext ctx, boolean interactive)
  {
    TreeSet<String> availableSuffixes = new TreeSet<String>();
    TreeSet<String> notReplicatedSuffixes = new TreeSet<String>();

    Collection<ReplicaDescriptor> replicas = getReplicas(ctx);
    for (ReplicaDescriptor rep : replicas)
    {
      String dn = rep.getSuffix().getDN();
      if (rep.isReplicated())
      {
        availableSuffixes.add(dn);
      }
      else
      {
        notReplicatedSuffixes.add(dn);
      }
    }
    if (availableSuffixes.size() == 0)
    {
      println();
      println(ERR_NO_SUFFIXES_AVAILABLE_TO_DISABLE_REPLICATION.get());
      LinkedList<String> userProvidedSuffixes = argParser.getBaseDNs();
      TreeSet<String> userProvidedNotReplicatedSuffixes =
        new TreeSet<String>();
      for (String s1 : userProvidedSuffixes)
      {
        for (String s2 : notReplicatedSuffixes)
        {
          if (Utils.areDnsEqual(s1, s2))
          {
            userProvidedNotReplicatedSuffixes.add(s1);
          }
        }
      }
      if (userProvidedNotReplicatedSuffixes.size() > 0)
      {
        println();
        println(INFO_ALREADY_NOT_REPLICATED_SUFFIXES.get(
            Utils.getStringFromCollection(
                userProvidedNotReplicatedSuffixes,
                Constants.LINE_SEPARATOR)));
      }
      suffixes.clear();
    }
    else
    {
      // Verify that the provided suffixes are configured in the servers.
      TreeSet<String> notFound = new TreeSet<String>();
      TreeSet<String> alreadyNotReplicated = new TreeSet<String>();
      for (String dn : suffixes)
      {
        boolean found = false;
        for (String dn1 : availableSuffixes)
        {
          if (Utils.areDnsEqual(dn, dn1))
          {
            found = true;
            break;
          }
        }
        if (!found)
        {
          boolean notReplicated = false;
          for (String s : notReplicatedSuffixes)
          {
            if (Utils.areDnsEqual(s, dn))
            {
              notReplicated = true;
              break;
            }
          }
          if (notReplicated)
          {
            alreadyNotReplicated.add(dn);
          }
          else
          {
            notFound.add(dn);
          }
        }
      }
      suffixes.removeAll(notFound);
      suffixes.removeAll(alreadyNotReplicated);
      if (notFound.size() > 0)
      {
        println();
        println(ERR_REPLICATION_DISABLE_SUFFIXES_NOT_FOUND.get(
                Utils.getStringFromCollection(notFound,
                    Constants.LINE_SEPARATOR)));
      }
      if (alreadyNotReplicated.size() > 0)
      {
        println();
        println(INFO_ALREADY_NOT_REPLICATED_SUFFIXES.get(
                Utils.getStringFromCollection(alreadyNotReplicated,
                    Constants.LINE_SEPARATOR)));
      }
      if (interactive)
      {
        while (suffixes.isEmpty())
        {
          boolean noSchemaOrAds = false;
          for (String s: availableSuffixes)
          {
            if (!Utils.areDnsEqual(s, ADSContext.getAdministrationSuffixDN()) &&
                !Utils.areDnsEqual(s, Constants.SCHEMA_DN) &&
                !Utils.areDnsEqual(s, Constants.REPLICATION_CHANGES_DN))
            {
              noSchemaOrAds = true;
            }
          }
          if (!noSchemaOrAds)
          {
            // In interactive mode we do not propose to manage the
            // administration suffix.
            println();
            println(ERR_NO_SUFFIXES_AVAILABLE_TO_DISABLE_REPLICATION.get());
            break;
          }
          else
          {
            println();
            println(ERR_NO_SUFFIXES_SELECTED_TO_DISABLE.get());
            for (String dn : availableSuffixes)
            {
              if (!Utils.areDnsEqual(dn,
                  ADSContext.getAdministrationSuffixDN()) &&
                  !Utils.areDnsEqual(dn, Constants.SCHEMA_DN) &&
                  !Utils.areDnsEqual(dn, Constants.REPLICATION_CHANGES_DN))
              {
                if (askConfirmation(
                    INFO_REPLICATION_DISABLE_SUFFIX_PROMPT.get(dn), true, LOG))
                {
                  suffixes.add(dn);
                }
              }
            }
          }
        }
      }
    }
  }

  /**
   * Checks that replication can actually be initialized in the provided baseDNs
   * for the server.
   * @param suffixes the suffixes provided by the user.  This Collection is
   * updated by removing the base DNs that cannot be initialized and with the
   * base DNs that the user provided interactively.
   * @param ctx connection to the server.
   * @param interactive whether to ask the user to provide interactively
   * base DNs if none of the provided base DNs can be initialized.
   */
  private void checkSuffixesForInitializeReplication(
      Collection<String> suffixes, InitialLdapContext ctx, boolean interactive)
  {
    TreeSet<String> availableSuffixes = new TreeSet<String>();
    TreeSet<String> notReplicatedSuffixes = new TreeSet<String>();

    Collection<ReplicaDescriptor> replicas = getReplicas(ctx);
    for (ReplicaDescriptor rep : replicas)
    {
      String dn = rep.getSuffix().getDN();
      if (rep.isReplicated())
      {
        availableSuffixes.add(dn);
      }
      else
      {
        notReplicatedSuffixes.add(dn);
      }
    }
    if (availableSuffixes.size() == 0)
    {
      println();
      if (argParser.isInitializeAllReplicationSubcommand())
      {
        println(ERR_NO_SUFFIXES_AVAILABLE_TO_INITIALIZE_ALL_REPLICATION.get());
      }
      else
      {
        println(
            ERR_NO_SUFFIXES_AVAILABLE_TO_INITIALIZE_LOCAL_REPLICATION.get());
      }
      LinkedList<String> userProvidedSuffixes = argParser.getBaseDNs();
      TreeSet<String> userProvidedNotReplicatedSuffixes =
        new TreeSet<String>();
      for (String s1 : userProvidedSuffixes)
      {
        for (String s2 : notReplicatedSuffixes)
        {
          if (Utils.areDnsEqual(s1, s2))
          {
            userProvidedNotReplicatedSuffixes.add(s1);
          }
        }
      }
      if (userProvidedNotReplicatedSuffixes.size() > 0)
      {
        println();
        println(INFO_ALREADY_NOT_REPLICATED_SUFFIXES.get(
            Utils.getStringFromCollection(
                userProvidedNotReplicatedSuffixes,
                Constants.LINE_SEPARATOR)));
      }
      suffixes.clear();
    }
    else
    {
      // Verify that the provided suffixes are configured in the servers.
      TreeSet<String> notFound = new TreeSet<String>();
      TreeSet<String> alreadyNotReplicated = new TreeSet<String>();
      for (String dn : suffixes)
      {
        boolean found = false;
        for (String dn1 : availableSuffixes)
        {
          if (Utils.areDnsEqual(dn, dn1))
          {
            found = true;
            break;
          }
        }
        if (!found)
        {
          boolean notReplicated = false;
          for (String s : notReplicatedSuffixes)
          {
            if (Utils.areDnsEqual(s, dn))
            {
              notReplicated = true;
              break;
            }
          }
          if (notReplicated)
          {
            alreadyNotReplicated.add(dn);
          }
          else
          {
            notFound.add(dn);
          }
        }
      }
      suffixes.removeAll(notFound);
      suffixes.removeAll(alreadyNotReplicated);
      if (notFound.size() > 0)
      {
        println();
        println(ERR_REPLICATION_INITIALIZE_LOCAL_SUFFIXES_NOT_FOUND.get(
                Utils.getStringFromCollection(notFound,
                    Constants.LINE_SEPARATOR)));
      }
      if (alreadyNotReplicated.size() > 0)
      {
        println();
        println(INFO_ALREADY_NOT_REPLICATED_SUFFIXES.get(
                Utils.getStringFromCollection(alreadyNotReplicated,
                    Constants.LINE_SEPARATOR)));
      }
      if (interactive)
      {
        while (suffixes.isEmpty())
        {
          boolean noSchemaOrAds = false;
          for (String s: availableSuffixes)
          {
            if (!Utils.areDnsEqual(s, ADSContext.getAdministrationSuffixDN()) &&
                !Utils.areDnsEqual(s, Constants.SCHEMA_DN) &&
                !Utils.areDnsEqual(s, Constants.REPLICATION_CHANGES_DN))
            {
              noSchemaOrAds = true;
            }
          }
          if (!noSchemaOrAds)
          {
            // In interactive mode we do not propose to manage the
            // administration suffix.
            println();
            if (argParser.isInitializeAllReplicationSubcommand())
            {
              println(
                ERR_NO_SUFFIXES_AVAILABLE_TO_INITIALIZE_ALL_REPLICATION.get());
            }
            else
            {
              println(
               ERR_NO_SUFFIXES_AVAILABLE_TO_INITIALIZE_LOCAL_REPLICATION.get());
            }
            break;
          }
          else
          {
            println();
            if (argParser.isInitializeAllReplicationSubcommand())
            {
              println(ERR_NO_SUFFIXES_SELECTED_TO_INITIALIZE_ALL.get());
            }
            else if (argParser.isPreExternalInitializationSubcommand())
            {
              println(
                 ERR_NO_SUFFIXES_SELECTED_TO_PRE_EXTERNAL_INITIALIZATION.get());
            }
            else if (argParser.isPostExternalInitializationSubcommand())
            {
              println(
                ERR_NO_SUFFIXES_SELECTED_TO_POST_EXTERNAL_INITIALIZATION.get());
            }
            for (String dn : availableSuffixes)
            {
              if (!Utils.areDnsEqual(dn,
                  ADSContext.getAdministrationSuffixDN()) &&
                  !Utils.areDnsEqual(dn, Constants.SCHEMA_DN) &&
                  !Utils.areDnsEqual(dn, Constants.REPLICATION_CHANGES_DN))
              {
                boolean addSuffix;
                if (argParser.isPreExternalInitializationSubcommand())
                {
                  addSuffix = askConfirmation(
                    INFO_REPLICATION_PRE_EXTERNAL_INITIALIZATION_SUFFIX_PROMPT.
                    get(dn), true, LOG);
                }
                else if (argParser.isPostExternalInitializationSubcommand())
                {
                  addSuffix = askConfirmation(
                    INFO_REPLICATION_POST_EXTERNAL_INITIALIZATION_SUFFIX_PROMPT.
                    get(dn), true, LOG);
                }
                else
                {
                  addSuffix = askConfirmation(
                      INFO_REPLICATION_INITIALIZE_ALL_SUFFIX_PROMPT.get(dn),
                      true, LOG);
                }
                if (addSuffix)
                {
                  suffixes.add(dn);
                }
              }
            }
          }
        }
      }
    }
  }


  /**
   * Checks that we can initialize the provided baseDNs between the two servers.
   * @param suffixes the suffixes provided by the user.  This Collection is
   * updated by removing the base DNs that cannot be enabled and with the
   * base DNs that the user provided interactively.
   * @param ctxSource connection to the source server.
   * @param ctxDestination connection to the destination server.
   * @param interactive whether to ask the user to provide interactively
   * base DNs if none of the provided base DNs can be initialized.
   */
  private void checkSuffixesForInitializeReplication(
      Collection<String> suffixes, InitialLdapContext ctxSource,
      InitialLdapContext ctxDestination, boolean interactive)
  {
    TreeSet<String> availableSuffixes = new TreeSet<String>(
        getCommonSuffixes(ctxSource, ctxDestination,
            SuffixRelationType.REPLICATED));
    if (availableSuffixes.size() == 0)
    {
      println();
      println(ERR_NO_SUFFIXES_AVAILABLE_TO_INITIALIZE_REPLICATION.get());
      suffixes.clear();
    }
    else
    {
      // Verify that the provided suffixes are configured in the servers.
      LinkedList<String> notFound = new LinkedList<String>();
      for (String dn : suffixes)
      {
        boolean found = false;
        for (String dn1 : availableSuffixes)
        {
          if (Utils.areDnsEqual(dn, dn1))
          {
            found = true;
            break;
          }
        }
        if (!found)
        {
          notFound.add(dn);
        }
      }
      suffixes.removeAll(notFound);
      if (notFound.size() > 0)
      {
        println();
        println(ERR_SUFFIXES_CANNOT_BE_INITIALIZED.get(
                Utils.getStringFromCollection(notFound,
                    Constants.LINE_SEPARATOR)));
      }
      if (interactive)
      {
        while (suffixes.isEmpty())
        {
          boolean noSchemaOrAds = false;
          for (String s: availableSuffixes)
          {
            if (!Utils.areDnsEqual(s, ADSContext.getAdministrationSuffixDN()) &&
                !Utils.areDnsEqual(s, Constants.SCHEMA_DN) &&
                !Utils.areDnsEqual(s, Constants.REPLICATION_CHANGES_DN))
            {
              noSchemaOrAds = true;
            }
          }
          if (!noSchemaOrAds)
          {
            // In interactive mode we do not propose to manage the
            // administration suffix.
            println();
            println(ERR_NO_SUFFIXES_AVAILABLE_TO_INITIALIZE_REPLICATION.get());
            break;
          }
          else
          {
            println();
            println(ERR_NO_SUFFIXES_SELECTED_TO_INITIALIZE.get());

            for (String dn : availableSuffixes)
            {
              if (!Utils.areDnsEqual(dn,
                  ADSContext.getAdministrationSuffixDN()) &&
                  !Utils.areDnsEqual(dn, Constants.SCHEMA_DN) &&
                  !Utils.areDnsEqual(dn, Constants.REPLICATION_CHANGES_DN))
              {
                if (askConfirmation(
                    INFO_REPLICATION_INITIALIZE_SUFFIX_PROMPT.get(dn), true,
                    LOG))
                {
                  suffixes.add(dn);
                }
              }
            }
          }
        }
      }
    }
  }

  /**
   * Updates the configuration in the two servers (and in other servers if
   * they are referenced) to enable replication.
   * @param ctx1 the connection to the first server.
   * @param ctx2 the connection to the second server.
   * @param uData the EnableReplicationUserData object containing the required
   * parameters to update the configuration.
   * @throws ReplicationCliException if there is an error.
   */
  private void updateConfiguration(InitialLdapContext ctx1,
      InitialLdapContext ctx2, EnableReplicationUserData uData)
  throws ReplicationCliException
  {
    LinkedHashSet<String> twoReplServers = new LinkedHashSet<String>();
    LinkedHashSet<String> allRepServers = new LinkedHashSet<String>();
    HashMap<String, LinkedHashSet<String>> hmRepServers =
      new HashMap<String, LinkedHashSet<String>>();
    Set<Integer> usedReplicationServerIds = new HashSet<Integer>();
    HashMap<String, Set<Integer>> hmUsedReplicationDomainIds =
      new HashMap<String, Set<Integer>>();

    ServerDescriptor server1;
    try
    {
      server1 = ServerDescriptor.createStandalone(ctx1);
    }
    catch (NamingException ne)
    {
      throw new ReplicationCliException(
          getMessageForException(ne, ConnectionUtils.getHostPort(ctx1)),
          ERROR_READING_CONFIGURATION, ne);
    }
    ServerDescriptor server2;
    try
    {
      server2 = ServerDescriptor.createStandalone(ctx2);
    }
    catch (NamingException ne)
    {
      throw new ReplicationCliException(
          getMessageForException(ne, ConnectionUtils.getHostPort(ctx2)),
          ERROR_READING_CONFIGURATION, ne);
    }

    ADSContext adsCtx1 = new ADSContext(ctx1);
    ADSContext adsCtx2 = new ADSContext(ctx2);

    if (!argParser.isInteractive())
    {
      // Inform the user of the potential errors that we found in the already
      // registered servers.
      LinkedHashSet<Message> messages = new LinkedHashSet<Message>();
      try
      {
        if (adsCtx1.hasAdminData())
        {
          TopologyCache cache = new TopologyCache(adsCtx1, getTrustManager());
          cache.reloadTopology();
          messages.addAll(getErrorMessages(cache));
        }

        if (adsCtx2.hasAdminData())
        {
          TopologyCache cache = new TopologyCache(adsCtx2, getTrustManager());
          cache.reloadTopology();
          messages.addAll(getErrorMessages(cache));
        }
      }
      catch (TopologyCacheException tce)
      {
        throw new ReplicationCliException(
            ERR_REPLICATION_READING_ADS.get(tce.getMessage()),
            ERROR_READING_TOPOLOGY_CACHE, tce);
      }
      catch (ADSContextException adce)
      {
        throw new ReplicationCliException(
            ERR_REPLICATION_READING_ADS.get(adce.getMessage()),
            ERROR_READING_ADS, adce);
      }
      if (!messages.isEmpty())
      {
        println(ERR_REPLICATION_READING_REGISTERED_SERVERS_WARNING.get(
                Utils.getMessageFromCollection(messages,
                    Constants.LINE_SEPARATOR).toString()));
      }
    }

    // These are used to identify which server we use to initialize
    // the contents of the other server (if any).
    InitialLdapContext ctxSource = null;
    InitialLdapContext ctxDestination = null;
    ADSContext adsCtxSource = null;

    boolean adsAlreadyReplicated = false;

    printProgress(formatter.getFormattedWithPoints(
        INFO_REPLICATION_ENABLE_UPDATING_ADS_CONTENTS.get()));
    try
    {
      if (adsCtx1.hasAdminData() && adsCtx2.hasAdminData())
      {
        Set<Map<ADSContext.ServerProperty, Object>> registry1 =
          adsCtx1.readServerRegistry();
        Set<Map<ADSContext.ServerProperty, Object>> registry2 =
          adsCtx2.readServerRegistry();
        if (registry2.size() <= 1)
        {
          // Only the server itself registered.
          if (!hasAdministrator(adsCtx1.getDirContext()))
          {
            adsCtx1.createAdministrator(getAdministratorProperties(uData));
          }
          if (registry2.size() == 0)
          {
            server2.updateAdsPropertiesWithServerProperties();
            registerServer(adsCtx1, server2.getAdsProperties());
          }
          else
          {
            registerServer(adsCtx1, registry2.iterator().next());
          }

          ctxSource = ctx1;
          ctxDestination = ctx2;
          adsCtxSource = adsCtx1;
        }
        else if (registry1.size() <= 1)
        {
          // Only the server itself registered.
          if (!hasAdministrator(adsCtx2.getDirContext()))
          {
            adsCtx2.createAdministrator(getAdministratorProperties(uData));
          }
          if (registry1.size() == 0)
          {
            server1.updateAdsPropertiesWithServerProperties();
            registerServer(adsCtx2, server1.getAdsProperties());
          }
          else
          {
            registerServer(adsCtx2, registry1.iterator().next());
          }

          ctxSource = ctx2;
          ctxDestination = ctx1;
          adsCtxSource = adsCtx2;
        }
        else if (!registry1.equals(registry2))
        {
          // TO COMPLETE: we may want to merge the ADS but for the moment
          // just say this is not supported.
          throw new ReplicationCliException(
              ERR_REPLICATION_ADS_MERGE_NOT_SUPPORTED.get(
                  ConnectionUtils.getHostPort(ctx1),
                  ConnectionUtils.getHostPort(ctx2)),
                  REPLICATION_ADS_MERGE_NOT_SUPPORTED, null);
        }
        else
        {
          // They are already replicated: nothing to do in terms of ADS
          // initialization or ADS update data
          adsAlreadyReplicated = true;
        }
      }
      else if (!adsCtx1.hasAdminData() && adsCtx2.hasAdminData())
      {
//        adsCtx1.createAdministrationSuffix(null);
        if (!hasAdministrator(adsCtx2.getDirContext()))
        {
          adsCtx2.createAdministrator(getAdministratorProperties(uData));
        }
        server1.updateAdsPropertiesWithServerProperties();
        registerServer(adsCtx2, server1.getAdsProperties());

        ctxSource = ctx2;
        ctxDestination = ctx1;
        adsCtxSource = adsCtx2;
      }
      else if (adsCtx1.hasAdminData() && !adsCtx2.hasAdminData())
      {
//        adsCtx2.createAdministrationSuffix(null);
        if (!hasAdministrator(adsCtx1.getDirContext()))
        {
          adsCtx1.createAdministrator(getAdministratorProperties(uData));
        }
        server2.updateAdsPropertiesWithServerProperties();
        registerServer(adsCtx1, server2.getAdsProperties());

        ctxSource = ctx1;
        ctxDestination = ctx2;
        adsCtxSource = adsCtx1;
      }
      else
      {
        adsCtx1.createAdminData(null);
        adsCtx1.createAdministrator(getAdministratorProperties(uData));
        server1.updateAdsPropertiesWithServerProperties();
        adsCtx1.registerServer(server1.getAdsProperties());
        server2.updateAdsPropertiesWithServerProperties();
        adsCtx1.registerServer(server2.getAdsProperties());
//        adsCtx2.createAdministrationSuffix(null);

        ctxSource = ctx1;
        ctxDestination = ctx2;
        adsCtxSource = adsCtx1;
      }
    }
    catch (ADSContextException adce)
    {
      throw new ReplicationCliException(
          ERR_REPLICATION_UPDATING_ADS.get(adce.getReason()),
          ERROR_UPDATING_ADS, adce);
    }
    if (!adsAlreadyReplicated)
    {
      try
      {
        ServerDescriptor.seedAdsTrustStore(ctxDestination,
            adsCtxSource.getTrustedCertificates());
      }
      catch (Throwable t)
      {
        LOG.log(Level.SEVERE, "Error seeding truststores: "+t, t);
        throw new ReplicationCliException(
            ERR_REPLICATION_ENABLE_SEEDING_TRUSTSTORE.get(t.toString()),
            ERROR_SEEDING_TRUSTORE, t);
      }
    }
    printProgress(formatter.getFormattedDone());
    printlnProgress();

    LinkedList<String> baseDNs = uData.getBaseDNs();
    if (!adsAlreadyReplicated)
    {
      boolean found = false;
      for (String dn : baseDNs)
      {
        if (Utils.areDnsEqual(dn, ADSContext.getAdministrationSuffixDN()))
        {
          found = true;
          break;
        }
      }
      if (!found)
      {
        baseDNs.add(ADSContext.getAdministrationSuffixDN());
        uData.setBaseDNs(baseDNs);
      }
    }

    if (uData.replicateSchema())
    {
      baseDNs = uData.getBaseDNs();
      baseDNs.add(Constants.SCHEMA_DN);
      uData.setBaseDNs(baseDNs);
    }
    TopologyCache cache1 = null;
    TopologyCache cache2 = null;
    try
    {
      if (adsCtx1.hasAdminData())
      {
        cache1 = new TopologyCache(adsCtx1, getTrustManager());
        cache1.reloadTopology();
        usedReplicationServerIds.addAll(getReplicationServerIds(cache1));
      }

      if (adsCtx2.hasAdminData())
      {
        cache2 = new TopologyCache(adsCtx2, getTrustManager());
        cache2.reloadTopology();
        usedReplicationServerIds.addAll(getReplicationServerIds(cache2));
      }
    }
    catch (ADSContextException adce)
    {
      throw new ReplicationCliException(
          ERR_REPLICATION_READING_ADS.get(adce.getMessage()),
          ERROR_READING_ADS, adce);
    }
    catch (TopologyCacheException tce)
    {
      throw new ReplicationCliException(
          ERR_REPLICATION_READING_ADS.get(tce.getMessage()),
          ERROR_READING_TOPOLOGY_CACHE, tce);
    }

    if (server1.isReplicationServer())
    {
      twoReplServers.add(server1.getReplicationServerHostPort());
      usedReplicationServerIds.add(server1.getReplicationServerId());
    }
    else
    {
      twoReplServers.add(
          ConnectionUtils.getHostName(ctx1)+":"+uData.getReplicationPort1());
    }
    if (server2.isReplicationServer())
    {
      twoReplServers.add(server2.getReplicationServerHostPort());
      usedReplicationServerIds.add(server2.getReplicationServerId());
    }
    else
    {
      twoReplServers.add(
          ConnectionUtils.getHostName(ctx2)+":"+uData.getReplicationPort2());
    }

    for (String baseDN : uData.getBaseDNs())
    {
      LinkedHashSet<String> repServersForBaseDN = new LinkedHashSet<String>();
      repServersForBaseDN.addAll(getReplicationServers(baseDN, cache1,
          server1));
      repServersForBaseDN.addAll(getReplicationServers(baseDN, cache2,
          server2));
      repServersForBaseDN.addAll(twoReplServers);
      hmRepServers.put(baseDN, repServersForBaseDN);

      Set<Integer> ids = new HashSet<Integer>();
      ids.addAll(getReplicationDomainIds(baseDN, server1));
      ids.addAll(getReplicationDomainIds(baseDN, server2));
      if (cache1 != null)
      {
        for (ServerDescriptor server : cache1.getServers())
        {
          ids.addAll(getReplicationDomainIds(baseDN, server));
        }
      }
      if (cache2 != null)
      {
        for (ServerDescriptor server : cache2.getServers())
        {
          ids.addAll(getReplicationDomainIds(baseDN, server));
        }
      }
      hmUsedReplicationDomainIds.put(baseDN, ids);
    }
    for (LinkedHashSet<String> v : hmRepServers.values())
    {
      allRepServers.addAll(v);
    }

    Set<String> alreadyConfiguredReplicationServers = new HashSet<String>();
    if (!server1.isReplicationServer())
    {
      try
      {
        configureAsReplicationServer(ctx1, uData.getReplicationPort1(),
            uData.isSecureReplication1(), allRepServers,
            usedReplicationServerIds);
      }
      catch (OpenDsException ode)
      {
        throw new ReplicationCliException(
            getMessageForReplicationServerException(ode,
            ConnectionUtils.getHostPort(ctx1)),
            ERROR_CONFIGURING_REPLICATIONSERVER, ode);
      }
    }
    else
    {
      try
      {
        updateReplicationServer(ctx1, allRepServers);
      }
      catch (OpenDsException ode)
      {
        throw new ReplicationCliException(
            getMessageForReplicationServerException(ode,
            ConnectionUtils.getHostPort(ctx1)),
            ERROR_CONFIGURING_REPLICATIONSERVER, ode);
      }
    }
    alreadyConfiguredReplicationServers.add(server1.getId());
    if (!server2.isReplicationServer())
    {
      try
      {
        configureAsReplicationServer(ctx2, uData.getReplicationPort2(),
            uData.isSecureReplication2(), allRepServers,
            usedReplicationServerIds);
      }
      catch (OpenDsException ode)
      {
        throw new ReplicationCliException(
            getMessageForReplicationServerException(ode,
            ConnectionUtils.getHostPort(ctx1)),
            ERROR_CONFIGURING_REPLICATIONSERVER, ode);
      }
    }
    else
    {
      try
      {
        updateReplicationServer(ctx2, allRepServers);
      }
      catch (OpenDsException ode)
      {
        throw new ReplicationCliException(
            getMessageForReplicationServerException(ode,
            ConnectionUtils.getHostPort(ctx1)),
            ERROR_CONFIGURING_REPLICATIONSERVER, ode);
      }
    }
    alreadyConfiguredReplicationServers.add(server2.getId());

    for (String baseDN : uData.getBaseDNs())
    {
      LinkedHashSet<String> repServers = hmRepServers.get(baseDN);
      Set<Integer> usedIds = hmUsedReplicationDomainIds.get(baseDN);
      Set<String> alreadyConfiguredServers = new HashSet<String>();

      try
      {
        configureToReplicateBaseDN(ctx1, baseDN, repServers, usedIds);
      }
      catch (OpenDsException ode)
      {
        Message msg = getMessageForEnableException(ode,
            ConnectionUtils.getHostPort(ctx1), baseDN);
        throw new ReplicationCliException(msg,
            ERROR_ENABLING_REPLICATION_ON_BASEDN, ode);
      }
      alreadyConfiguredServers.add(server1.getId());
      try
      {
        configureToReplicateBaseDN(ctx2, baseDN, repServers, usedIds);
      }
      catch (OpenDsException ode)
      {
        Message msg = getMessageForEnableException(ode,
            ConnectionUtils.getHostPort(ctx2), baseDN);
        throw new ReplicationCliException(msg,
            ERROR_ENABLING_REPLICATION_ON_BASEDN, ode);
      }
      alreadyConfiguredServers.add(server2.getId());

      if (cache1 != null)
      {
        configureToReplicateBaseDN(baseDN, repServers, usedIds, cache1, server1,
            alreadyConfiguredServers, allRepServers,
            alreadyConfiguredReplicationServers);
      }
      if (cache2 != null)
      {
        configureToReplicateBaseDN(baseDN, repServers, usedIds, cache2, server2,
            alreadyConfiguredServers, allRepServers,
            alreadyConfiguredReplicationServers);
      }
    }

    // Now that replication is configured in all servers, simply try to
    // initialize the contents of one ADS with the other (in the case where
    // already both servers were replicating the same ADS there is nothing to be
    // done).
    if ((ctxSource != null) && (ctxDestination != null))
    {
      printProgress(formatter.getFormattedWithPoints(
          INFO_ENABLE_REPLICATION_INITIALIZING_ADS.get(
              ConnectionUtils.getHostPort(ctxDestination),
              ConnectionUtils.getHostPort(ctxSource))));

      initializeSuffix(ADSContext.getAdministrationSuffixDN(), ctxSource,
          ctxDestination, false);
      printProgress(formatter.getFormattedDone());
      printlnProgress();
    }

    // If we must initialize the schema do so.
    if (mustInitializeSchema(server1, server2))
    {
      if (argParser.useSecondServerAsSchemaSource())
      {
        ctxSource = ctx2;
        ctxDestination = ctx1;
      }
      else
      {
        ctxSource = ctx1;
        ctxDestination = ctx2;
      }
      printProgress(formatter.getFormattedWithPoints(
          INFO_ENABLE_REPLICATION_INITIALIZING_SCHEMA.get(
              ConnectionUtils.getHostPort(ctxDestination),
              ConnectionUtils.getHostPort(ctxSource))));
      initializeSuffix(Constants.SCHEMA_DN, ctxSource,
          ctxDestination, false);
      printProgress(formatter.getFormattedDone());
      printlnProgress();
    }
  }

  /**
   * Updates the configuration in the server (and in other servers if
   * they are referenced) to disable replication.
   * @param ctx the connection to the server.
   * @param uData the DisableReplicationUserData object containing the required
   * parameters to update the configuration.
   * @throws ReplicationCliException if there is an error.
   */
  private void updateConfiguration(InitialLdapContext ctx,
      DisableReplicationUserData uData) throws ReplicationCliException
  {
    ServerDescriptor server;
    try
    {
      server = ServerDescriptor.createStandalone(ctx);
    }
    catch (NamingException ne)
    {
      throw new ReplicationCliException(
          getMessageForException(ne, ConnectionUtils.getHostPort(ctx)),
          ERROR_READING_CONFIGURATION, ne);
    }

    ADSContext adsCtx = new ADSContext(ctx);

    TopologyCache cache = null;
    // Only try to update remote server if the user provided a Global
    // Administrator to authenticate.
    boolean tryToUpdateRemote = uData.getAdminUid() != null;
    try
    {
      if (adsCtx.hasAdminData() && tryToUpdateRemote)
      {
        cache = new TopologyCache(adsCtx, getTrustManager());
        cache.reloadTopology();
      }
    }
    catch (ADSContextException adce)
    {
      throw new ReplicationCliException(
          ERR_REPLICATION_READING_ADS.get(adce.getMessage()),
          ERROR_READING_ADS, adce);
    }
    catch (TopologyCacheException tce)
    {
      throw new ReplicationCliException(
          ERR_REPLICATION_READING_ADS.get(tce.getMessage()),
          ERROR_READING_TOPOLOGY_CACHE, tce);
    }
    if (!argParser.isInteractive())
    {
      // Inform the user of the potential errors that we found.
      LinkedHashSet<Message> messages = new LinkedHashSet<Message>();
      if (cache != null)
      {
        messages.addAll(getErrorMessages(cache));
      }
      if (!messages.isEmpty())
      {
        println(
            ERR_REPLICATION_READING_REGISTERED_SERVERS_WARNING.get(
                Utils.getMessageFromCollection(messages,
                    Constants.LINE_SEPARATOR).toString()));
      }
    }

    String replicationServerHostPort = server.getReplicationServerHostPort();
    for (String baseDN : uData.getBaseDNs())
    {
      try
      {
        deleteReplicationDomain(ctx, baseDN);
      }
      catch (OpenDsException ode)
      {
        Message msg = getMessageForDisableException(ode,
            ConnectionUtils.getHostPort(ctx), baseDN);
        throw new ReplicationCliException(msg,
            ERROR_DISABLING_REPLICATION_ON_BASEDN, ode);
      }
    }

    if ((replicationServerHostPort != null) && (cache != null))
    {
      Set<ServerDescriptor> serversToUpdate =
        new LinkedHashSet<ServerDescriptor>();
      for (String baseDN : uData.getBaseDNs())
      {
        SuffixDescriptor suffix = getSuffix(baseDN, cache, server);
        if (suffix != null)
        {
          for (ReplicaDescriptor replica : suffix.getReplicas())
          {
            serversToUpdate.add(replica.getServer());
          }
        }
      }
      String bindDn = ConnectionUtils.getBindDN(ctx);
      String pwd = ConnectionUtils.getBindPassword(ctx);
      for (ServerDescriptor s : serversToUpdate)
      {
        removeReferencesInServer(s, replicationServerHostPort, bindDn, pwd,
            uData.getBaseDNs());
      }
    }
  }

  /**
   * Displays the replication status of the different base DNs in the servers
   * registered in the ADS.
   * @param ctx the connection to the server.
   * @param uData the StatusReplicationUserData object containing the required
   * parameters to update the configuration.
   * @throws ReplicationCliException if there is an error.
   */
  private void displayStatus(InitialLdapContext ctx,
      StatusReplicationUserData uData) throws ReplicationCliException
  {
    ADSContext adsCtx = new ADSContext(ctx);

    TopologyCache cache = null;
    try
    {
      if (adsCtx.hasAdminData())
      {
        cache = new TopologyCache(adsCtx, getTrustManager());
        cache.reloadTopology();
      }
    }
    catch (ADSContextException adce)
    {
      throw new ReplicationCliException(
          ERR_REPLICATION_READING_ADS.get(adce.getMessage()),
          ERROR_READING_ADS, adce);
    }
    catch (TopologyCacheException tce)
    {
      throw new ReplicationCliException(
          ERR_REPLICATION_READING_ADS.get(tce.getMessage()),
          ERROR_READING_TOPOLOGY_CACHE, tce);
    }
    if (!argParser.isInteractive())
    {
      // Inform the user of the potential errors that we found.
      LinkedHashSet<Message> messages = new LinkedHashSet<Message>();
      if (cache != null)
      {
        messages.addAll(getErrorMessages(cache));
      }
      if (!messages.isEmpty())
      {
        Message msg =
            ERR_REPLICATION_STATUS_READING_REGISTERED_SERVERS.get(
                Utils.getMessageFromCollection(messages,
                    Constants.LINE_SEPARATOR).toString());
        println(msg);
      }
    }

    LinkedList<String> userBaseDNs = uData.getBaseDNs();
    LinkedList<Set<ReplicaDescriptor>> replicaLists =
      new LinkedList<Set<ReplicaDescriptor>>();

    boolean oneReplicated = false;
    for (SuffixDescriptor suffix : cache.getSuffixes())
    {
      String dn = suffix.getDN();

      // If no base DNs where specified display all the base DNs but the schema
      // and cn=admin data.
      boolean found = userBaseDNs.isEmpty() &&
      !Utils.areDnsEqual(dn, ADSContext.getAdministrationSuffixDN()) &&
      !Utils.areDnsEqual(dn, Constants.SCHEMA_DN) &&
      !Utils.areDnsEqual(dn, Constants.REPLICATION_CHANGES_DN);
      for (String baseDN : userBaseDNs)
      {
        found = Utils.areDnsEqual(baseDN, dn);
        if (found)
        {
          break;
        }
      }
      if (found)
      {
        boolean replicated = false;
        for (ReplicaDescriptor replica : suffix.getReplicas())
        {
          if (replica.isReplicated())
          {
            replicated = true;
            break;
          }
        }
        if (replicated)
        {
          oneReplicated = true;
          replicaLists.add(suffix.getReplicas());
        }
        else
        {
          // Check if there are already some non replicated base DNs.
          found = false;
          for (Set<ReplicaDescriptor> replicas : replicaLists)
          {
            ReplicaDescriptor replica = replicas.iterator().next();
            if (!replica.isReplicated() &&
                Utils.areDnsEqual(dn, replica.getSuffix().getDN()))
            {
              replicas.addAll(suffix.getReplicas());
              found = true;
              break;
            }
          }
          if (!found)
          {
            replicaLists.add(suffix.getReplicas());
          }
        }
      }
    }

    if (replicaLists.isEmpty())
    {
      printProgress(INFO_REPLICATION_STATUS_NO_BASEDNS.get());
      printlnProgress();
    }
    else
    {
      LinkedList<Set<ReplicaDescriptor>> orderedReplicaLists =
        new LinkedList<Set<ReplicaDescriptor>>();
      for (Set<ReplicaDescriptor> replicas1 : replicaLists)
      {
        String dn1 = replicas1.iterator().next().getSuffix().getDN();
        boolean inserted = false;
        for (int i=0; i<orderedReplicaLists.size() && !inserted; i++)
        {
          String dn2 =
            orderedReplicaLists.get(i).iterator().next().getSuffix().getDN();
          if (dn1.compareTo(dn2) < 0)
          {
            orderedReplicaLists.add(i, replicas1);
            inserted = true;
          }
        }
        if (!inserted)
        {
          orderedReplicaLists.add(replicas1);
        }
      }
      for (Set<ReplicaDescriptor> replicas : orderedReplicaLists)
      {
        printlnProgress();
        displayStatus(replicas, uData.isScriptFriendly());
      }
      if (oneReplicated && !uData.isScriptFriendly())
      {
        printlnProgress();
        printProgress(INFO_REPLICATION_STATUS_REPLICATED_LEGEND.get());
        printlnProgress();
      }
    }
  }

  /**
   * Displays the replication status of the replicas provided.  The code assumes
   * that all the replicas have the same baseDN and that if they are replicated
   * all the replicas are replicated with each other.
   * @param replicas the list of replicas that we are trying to display.
   * @param scriptFriendly wheter to display it on script-friendly mode or not.
   */
  private void displayStatus(Set<ReplicaDescriptor> replicas,
      boolean scriptFriendly)
  {

    boolean isReplicated = false;
    Set<ReplicaDescriptor> orderedReplicas =
      new LinkedHashSet<ReplicaDescriptor>();
    Set<String> hostPorts = new TreeSet<String>();
    for (ReplicaDescriptor replica : replicas)
    {
      if (replica.isReplicated())
      {
        isReplicated = true;
      }
      hostPorts.add(replica.getServer().getHostPort(true));
    }
    for (String hostPort : hostPorts)
    {
      for (ReplicaDescriptor replica : replicas)
      {
        if (replica.getServer().getHostPort(true).equals(hostPort))
        {
          orderedReplicas.add(replica);
        }
      }
    }
    final int SERVERPORT = 0;
    final int NUMBER_ENTRIES = 1;
    final int MISSING_CHANGES = 2;
    final int AGE_OF_OLDEST_MISSING_CHANGE = 3;
    final int REPLICATION_PORT = 4;
    final int SECURE = 5;
    Message[] headers;
    if (scriptFriendly)
    {
      if (isReplicated)
      {
        headers = new Message[] {
            INFO_REPLICATION_STATUS_LABEL_SERVERPORT.get(),
            INFO_REPLICATION_STATUS_LABEL_NUMBER_ENTRIES.get(),
            INFO_REPLICATION_STATUS_LABEL_MISSING_CHANGES.get(),
            INFO_REPLICATION_STATUS_LABEL_AGE_OF_OLDEST_MISSING_CHANGE.get(),
            INFO_REPLICATION_STATUS_LABEL_REPLICATION_PORT.get(),
            INFO_REPLICATION_STATUS_LABEL_SECURE.get()
        };
      }
      else
      {
        headers = new Message[] {
            INFO_REPLICATION_STATUS_LABEL_SERVERPORT.get(),
            INFO_REPLICATION_STATUS_LABEL_NUMBER_ENTRIES.get()
        };
      }
    }
    else
    {
      if (isReplicated)
      {
        headers = new Message[] {
            INFO_REPLICATION_STATUS_HEADER_SERVERPORT.get(),
            INFO_REPLICATION_STATUS_HEADER_NUMBER_ENTRIES.get(),
            INFO_REPLICATION_STATUS_HEADER_MISSING_CHANGES.get(),
            INFO_REPLICATION_STATUS_HEADER_AGE_OF_OLDEST_MISSING_CHANGE.get(),
            INFO_REPLICATION_STATUS_HEADER_REPLICATION_PORT.get(),
            INFO_REPLICATION_STATUS_HEADER_SECURE.get()
        };
      }
      else
      {
        headers = new Message[] {
            INFO_REPLICATION_STATUS_HEADER_SERVERPORT.get(),
            INFO_REPLICATION_STATUS_HEADER_NUMBER_ENTRIES.get()
        };
      }
    }
    Message[][] values = new Message[orderedReplicas.size()][headers.length];

    int i = 0;
    for (ReplicaDescriptor replica : orderedReplicas)
    {
      Message v;
      for (int j=0; j<headers.length; j++)
      {
        switch (j)
        {
        case SERVERPORT:
          v = Message.raw(replica.getServer().getHostPort(true));
          break;
        case NUMBER_ENTRIES:
          int nEntries = replica.getEntries();
          if (nEntries >= 0)
          {
            v = Message.raw(String.valueOf(nEntries));
          }
          else
          {
            v = INFO_NOT_AVAILABLE_SHORT_LABEL.get();
          }
          break;
        case MISSING_CHANGES:
          int missingChanges = replica.getMissingChanges();
          if (missingChanges >= 0)
          {
            v = Message.raw(String.valueOf(missingChanges));
          }
          else
          {
            v = INFO_NOT_AVAILABLE_SHORT_LABEL.get();
          }
          break;
        case AGE_OF_OLDEST_MISSING_CHANGE:
          int ageOfOldestMissingChange = replica.getAgeOfOldestMissingChange();
          if (ageOfOldestMissingChange >= 0)
          {
            v = Message.raw(String.valueOf(ageOfOldestMissingChange));
          }
          else
          {
            v = INFO_NOT_AVAILABLE_SHORT_LABEL.get();
          }
          break;
        case REPLICATION_PORT:
          int replicationPort = replica.getServer().getReplicationServerPort();
          if (replicationPort >= 0)
          {
            v = Message.raw(String.valueOf(replicationPort));
          }
          else
          {
            v = INFO_NOT_AVAILABLE_SHORT_LABEL.get();
          }
          break;
        case SECURE:
          if (replica.getServer().isReplicationSecure())
          {
            v = INFO_REPLICATION_STATUS_SECURITY_ENABLED.get();
          }
          else
          {
            v = INFO_REPLICATION_STATUS_SECURITY_DISABLED.get();
          }
          break;
        default:
          throw new IllegalStateException("Unknown index: "+j);
        }
        values[i][j] = v;
      }
      i++;
    }

    String dn = replicas.iterator().next().getSuffix().getDN();
    if (scriptFriendly)
    {
      Message[] labels = {
          INFO_REPLICATION_STATUS_BASEDN.get(),
          INFO_REPLICATION_STATUS_IS_REPLICATED.get()
      };
      Message[] vs = {
          Message.raw(dn),
          isReplicated ? INFO_BASEDN_REPLICATED_LABEL.get() :
            INFO_BASEDN_NOT_REPLICATED_LABEL.get()
      };
      for (i=0; i<labels.length; i++)
      {
        printProgress(Message.raw(labels[i]+" "+vs[i]));
        printlnProgress();
      }

      for (i=0; i<values.length; i++)
      {
        printProgress(Message.raw("-"));
        printlnProgress();
        for (int j=0; j<values[i].length; j++)
        {
          printProgress(Message.raw(headers[j]+" "+values[i][j]));
          printlnProgress();
        }
      }
    }
    else
    {
      Message msg;
      if (isReplicated)
      {
        msg = INFO_REPLICATION_STATUS_REPLICATED.get(dn);
      }
      else
      {
        msg = INFO_REPLICATION_STATUS_NOT_REPLICATED.get(dn);
      }
      printProgressMessageNoWrap(msg);
      printlnProgress();
      int length = msg.length();
      StringBuffer buf = new StringBuffer();
      for (i=0; i<length; i++)
      {
        buf.append("=");
      }
      printProgressMessageNoWrap(Message.raw(buf.toString()));
      printlnProgress();

      TableBuilder table = new TableBuilder();
      for (i=0; i< headers.length; i++)
      {
        table.appendHeading(headers[i]);
      }
      for (i=0; i<values.length; i++)
      {
        table.startRow();
        for (int j=0; j<headers.length; j++)
        {
          table.appendCell(values[i][j]);
        }
      }
      TextTablePrinter printer = new TextTablePrinter(getOutputStream());
      printer.setColumnSeparator(ToolConstants.LIST_TABLE_SEPARATOR);
      table.print(printer);
    }
  }

  /**
   * Retrieves all the replication servers for a given baseDN.  The
   * ServerDescriptor is used to identify the server where the suffix is
   * defined and it cannot be null.  The TopologyCache is used to retrieve
   * replication servers defined in other replicas but not in the one we
   * get in the ServerDescriptor.
   * @param baseDN the base DN.
   * @param cache the TopologyCache (might be null).
   * @param server the ServerDescriptor.
   * @return a Set containing the replication servers currently being used
   * to replicate the baseDN defined in the server described by the
   * ServerDescriptor.
   */
  private LinkedHashSet<String> getReplicationServers(String baseDN,
      TopologyCache cache, ServerDescriptor server)
  {
    LinkedHashSet<String> servers = new LinkedHashSet<String>();
    for (ReplicaDescriptor replica : server.getReplicas())
    {
      if (Utils.areDnsEqual(replica.getSuffix().getDN(), baseDN))
      {
        servers.addAll(replica.getReplicationServers());
        break;
      }
    }
    if (cache != null)
    {
      Set<SuffixDescriptor> suffixes = cache.getSuffixes();
      for (SuffixDescriptor suffix : suffixes)
      {
        if (Utils.areDnsEqual(suffix.getDN(), baseDN))
        {
          Set<String> s = suffix.getReplicationServers();
          // Test that at least we share one of the replication servers.
          // If we do: we are dealing with the same replication topology
          // (we must consider the case of disjoint replication topologies
          // replicating the same base DN).
          HashSet<String> copy = new HashSet<String>(s);
          copy.retainAll(servers);
          if (!copy.isEmpty())
          {
            servers.addAll(s);
            break;
          }
        }
      }
    }
    return servers;
  }

  /**
   * Retrieves the suffix in the TopologyCache for a given baseDN.  The
   * ServerDescriptor is used to identify the server where the suffix is
   * defined.
   * @param baseDN the base DN.
   * @param cache the TopologyCache.
   * @param server the ServerDescriptor.
   * @return the suffix in the TopologyCache for a given baseDN.
   */
  private SuffixDescriptor getSuffix(String baseDN, TopologyCache cache,
      ServerDescriptor server)
  {
    SuffixDescriptor returnValue = null;
    Set<String> servers = new LinkedHashSet<String>();
    for (ReplicaDescriptor replica : server.getReplicas())
    {
      if (Utils.areDnsEqual(replica.getSuffix().getDN(), baseDN))
      {
        servers.addAll(replica.getReplicationServers());
        break;
      }
    }

    Set<SuffixDescriptor> suffixes = cache.getSuffixes();
    for (SuffixDescriptor suffix : suffixes)
    {
      if (Utils.areDnsEqual(suffix.getDN(), baseDN))
      {
        Set<String> s = suffix.getReplicationServers();
        // Test that at least we share one of the replication servers.
        // If we do: we are dealing with the same replication topology
        // (we must consider the case of disjoint replication topologies
        // replicating the same base DN).
        HashSet<String> copy = new HashSet<String>(s);
        copy.retainAll(servers);
        if (!copy.isEmpty())
        {
          returnValue = suffix;
          break;
        }
      }
    }
    return returnValue;
  }

  /**
   * Retrieves all the replication domain IDs for a given baseDN in the
   * ServerDescriptor.
   * @param baseDN the base DN.
   * @param server the ServerDescriptor.
   * @return a Set containing the replication domain IDs for a given baseDN in
   * the ServerDescriptor.
   */
  private Set<Integer> getReplicationDomainIds(String baseDN,
      ServerDescriptor server)
  {
    Set<Integer> ids = new HashSet<Integer>();
    for (ReplicaDescriptor replica : server.getReplicas())
    {
      if ((replica.isReplicated()) &&
      Utils.areDnsEqual(replica.getSuffix().getDN(), baseDN))
      {
        ids.add(replica.getReplicationId());
        break;
      }
    }
    return ids;
  }

  /**
   * Configures the server to which the provided InitialLdapContext is connected
   * as a replication server.  The replication server listens in the provided
   * port.
   * @param ctx the context connected to the server that we want to configure.
   * @param replicationPort the replication port of the replication server.
   * @param useSecureReplication whether to have encrypted communication with
   * the replication port or not.
   * @param replicationServers the list of replication servers to which the
   * replication server will communicate with.
   * @param usedReplicationServerIds the set of replication server IDs that
   * are already in use.  The set will be updated with the replication ID
   * that will be used by the newly configured replication server.
   * @throws OpenDsException if there is an error updating the configuration.
   */
  private void configureAsReplicationServer(InitialLdapContext ctx,
      int replicationPort, boolean useSecureReplication,
      LinkedHashSet<String> replicationServers,
      Set<Integer> usedReplicationServerIds) throws OpenDsException
  {
    printProgress(formatter.getFormattedWithPoints(
        INFO_REPLICATION_ENABLE_CONFIGURING_REPLICATION_SERVER.get(
            ConnectionUtils.getHostPort(ctx))));

    ManagementContext mCtx = LDAPManagementContext.createFromContext(
        JNDIDirContextAdaptor.adapt(ctx));
    RootCfgClient root = mCtx.getRootConfiguration();

    /*
     * Configure Synchronization plugin.
     */
    ReplicationSynchronizationProviderCfgClient sync = null;
    try
    {
      sync = (ReplicationSynchronizationProviderCfgClient)
      root.getSynchronizationProvider("Multimaster Synchronization");
    }
    catch (ManagedObjectNotFoundException monfe)
    {
      LOG.log(Level.INFO, "Synchronization server does not exist in "+
          ConnectionUtils.getHostPort(ctx));
    }
    if (sync == null)
    {
      ReplicationSynchronizationProviderCfgDefn provider =
        ReplicationSynchronizationProviderCfgDefn.getInstance();
      sync = root.createSynchronizationProvider(provider,
          "Multimaster Synchronization",
          new ArrayList<DefaultBehaviorException>());
      sync.setJavaClass(
          org.opends.server.replication.plugin.MultimasterReplication.class.
          getName());
      sync.setEnabled(Boolean.TRUE);
    }
    else
    {
      if (!sync.isEnabled())
      {
        sync.setEnabled(Boolean.TRUE);
      }
    }
    sync.commit();

    /*
     * Configure the replication server.
     */
    ReplicationServerCfgClient replicationServer = null;

    boolean mustCommit = false;

    if (!sync.hasReplicationServer())
    {
      CryptoManagerCfgClient crypto = root.getCryptoManager();
      if (useSecureReplication != crypto.isSSLEncryption())
      {
        crypto.setSSLEncryption(useSecureReplication);
        crypto.commit();
      }
      int id = InstallerHelper.getReplicationId(usedReplicationServerIds);
      usedReplicationServerIds.add(id);
      replicationServer = sync.createReplicationServer(
          ReplicationServerCfgDefn.getInstance(),
          new ArrayList<DefaultBehaviorException>());
      replicationServer.setReplicationServerId(id);
      replicationServer.setReplicationPort(replicationPort);
      replicationServer.setReplicationServer(replicationServers);
      mustCommit = true;
    }
    else
    {
      replicationServer = sync.getReplicationServer();
      usedReplicationServerIds.add(
          replicationServer.getReplicationServerId());
      Set<String> servers = replicationServer.getReplicationServer();
      if (servers == null)
      {
        replicationServer.setReplicationServer(replicationServers);
        mustCommit = true;
      }
      else if (!areReplicationServersEqual(servers, replicationServers))
      {
        replicationServers.addAll(servers);
        replicationServer.setReplicationServer(
            mergeReplicationServers(replicationServers, servers));
        mustCommit = true;
      }
    }
    if (mustCommit)
    {
      replicationServer.commit();
    }

    printProgress(formatter.getFormattedDone());
    printlnProgress();
  }

  /**
   * Updates the configuration of the replication server with the list of
   * replication servers provided.
   * @param ctx the context connected to the server that we want to update.
   * @param replicationServers the list of replication servers to which the
   * replication server will communicate with.
   * @throws OpenDsException if there is an error updating the configuration.
   */
  private void updateReplicationServer(InitialLdapContext ctx,
      LinkedHashSet<String> replicationServers) throws OpenDsException
  {
    printProgress(formatter.getFormattedWithPoints(
        INFO_REPLICATION_ENABLE_UPDATING_REPLICATION_SERVER.get(
            ConnectionUtils.getHostPort(ctx))));

    ManagementContext mCtx = LDAPManagementContext.createFromContext(
        JNDIDirContextAdaptor.adapt(ctx));
    RootCfgClient root = mCtx.getRootConfiguration();

    ReplicationSynchronizationProviderCfgClient sync =
      (ReplicationSynchronizationProviderCfgClient)
    root.getSynchronizationProvider("Multimaster Synchronization");
    boolean mustCommit = false;
    ReplicationServerCfgClient replicationServer = sync.getReplicationServer();
    Set<String> servers = replicationServer.getReplicationServer();
    if (servers == null)
    {
      replicationServer.setReplicationServer(replicationServers);
      mustCommit = true;
    }
    else if (!areReplicationServersEqual(servers, replicationServers))
    {
      replicationServers.addAll(servers);
      replicationServer.setReplicationServer(
          mergeReplicationServers(replicationServers, servers));
      mustCommit = true;
    }
    if (mustCommit)
    {
      replicationServer.commit();
    }

    printProgress(formatter.getFormattedDone());
    printlnProgress();
  }

  /**
   * Returns a Set containing all the replication server ids found in the
   * servers of a given TopologyCache object.
   * @param cache the TopologyCache object to use.
   * @return a Set containing all the replication server ids found in a given
   * TopologyCache object.
   */
  private Set<Integer> getReplicationServerIds(TopologyCache cache)
  {
    Set<Integer> ids = new HashSet<Integer>();
    for (ServerDescriptor server : cache.getServers())
    {
      if (server.isReplicationServer())
      {
        ids.add(server.getReplicationServerId());
      }
    }
    return ids;
  }

  /**
   * Configures a replication domain for a given base DN in the server to which
   * the provided InitialLdapContext is connected.
   * @param ctx the context connected to the server that we want to configure.
   * @param baseDN the base DN of the replication domain to configure.
   * @param replicationServers the list of replication servers to which the
   * replication domain will communicate with.
   * @param usedReplicationDomainIds the set of replication domain IDs that
   * are already in use.  The set will be updated with the replication ID
   * that will be used by the newly configured replication server.
   * @throws OpenDsException if there is an error updating the configuration.
   */
  private void configureToReplicateBaseDN(InitialLdapContext ctx,
      String baseDN,
      LinkedHashSet<String> replicationServers,
      Set<Integer> usedReplicationDomainIds) throws OpenDsException
  {
    boolean userSpecifiedAdminBaseDN = false;
    LinkedList<String> l = argParser.getBaseDNs();
    if (l != null)
    {
      for (String dn : l)
      {
        if (Utils.areDnsEqual(dn, ADSContext.getAdministrationSuffixDN()))
        {
          userSpecifiedAdminBaseDN = true;
          break;
        }
      }
    }
    if (!userSpecifiedAdminBaseDN && Utils.areDnsEqual(baseDN,
        ADSContext.getAdministrationSuffixDN()))
    {
      printProgress(formatter.getFormattedWithPoints(
          INFO_REPLICATION_ENABLE_CONFIGURING_ADS.get(
              ConnectionUtils.getHostPort(ctx))));
    }
    else
    {
      printProgress(formatter.getFormattedWithPoints(
          INFO_REPLICATION_ENABLE_CONFIGURING_BASEDN.get(baseDN,
              ConnectionUtils.getHostPort(ctx))));
    }
    ManagementContext mCtx = LDAPManagementContext.createFromContext(
        JNDIDirContextAdaptor.adapt(ctx));
    RootCfgClient root = mCtx.getRootConfiguration();

    ReplicationSynchronizationProviderCfgClient sync =
      (ReplicationSynchronizationProviderCfgClient)
      root.getSynchronizationProvider("Multimaster Synchronization");

    String[] domainNames = sync.listReplicationDomains();
    if (domainNames == null)
    {
      domainNames = new String[]{};
    }
    ReplicationDomainCfgClient[] domains =
      new ReplicationDomainCfgClient[domainNames.length];
    for (int i=0; i<domains.length; i++)
    {
      domains[i] = sync.getReplicationDomain(domainNames[i]);
    }
    ReplicationDomainCfgClient domain = null;
    String domainName = null;
    for (int i=0; i<domains.length && (domain == null); i++)
    {
      if (Utils.areDnsEqual(baseDN, domains[i].getBaseDN().toString()))
      {
        domain = domains[i];
        domainName = domainNames[i];
      }
    }
    boolean mustCommit = false;
    if (domain == null)
    {
      int domainId = InstallerHelper.getReplicationId(usedReplicationDomainIds);
      usedReplicationDomainIds.add(domainId);
      domainName = InstallerHelper.getDomainName(domainNames, domainId, baseDN);
      domain = sync.createReplicationDomain(
          ReplicationDomainCfgDefn.getInstance(), domainName,
          new ArrayList<DefaultBehaviorException>());
      domain.setServerId(domainId);
      domain.setBaseDN(DN.decode(baseDN));
      domain.setReplicationServer(replicationServers);
      mustCommit = true;
    }
    else
    {
      Set<String> servers = domain.getReplicationServer();
      if (servers == null)
      {
        domain.setReplicationServer(servers);
        mustCommit = true;
      }
      else if (!areReplicationServersEqual(servers, replicationServers))
      {
        domain.setReplicationServer(mergeReplicationServers(replicationServers,
            servers));
        mustCommit = true;
      }
    }

    if (mustCommit)
    {
      domain.commit();
    }

    printProgress(formatter.getFormattedDone());
    printlnProgress();
  }

  /**
   * Configures the baseDN to replicate in all the Replicas found in a Topology
   * Cache that are replicated with the Replica of the same base DN in the
   * provided ServerDescriptor object.
   * @param baseDN the base DN to replicate.
   * @param repServers the replication servers to be defined in the domain.
   * @param usedIds the replication domain Ids already used.  This Set is
   * updated with the new domains that are used.
   * @param cache the TopologyCache used to retrieve the different defined
   * replicas.
   * @param server the ServerDescriptor that is used to identify the
   * replication topology that we are interested at (we only update the replicas
   * that are already replicated with this server).
   * @param alreadyConfiguredServers the list of already configured servers.  If
   * a server is in this list no updates are performed to the domain.
   * @param alreadyConfiguredReplicationServers the list of already configured
   * servers.  If a server is in this list no updates are performed to the
   * replication server.
   * @throws ReplicationCliException if something goes wrong.
   */
  private void configureToReplicateBaseDN(String baseDN,
      LinkedHashSet<String> repServers, Set<Integer> usedIds,
      TopologyCache cache, ServerDescriptor server,
      Set<String> alreadyConfiguredServers, LinkedHashSet<String> allRepServers,
      Set<String> alreadyConfiguredReplicationServers)
  throws ReplicationCliException
  {
    SuffixDescriptor suffix = getSuffix(baseDN, cache, server);
    if (suffix != null)
    {
      for (ReplicaDescriptor replica: suffix.getReplicas())
      {
        ServerDescriptor s = replica.getServer();
        if (!alreadyConfiguredServers.contains(s.getId()))
        {
          String dn = ConnectionUtils.getBindDN(
              cache.getAdsContext().getDirContext());
          String pwd = ConnectionUtils.getBindPassword(
              cache.getAdsContext().getDirContext());

          ServerLoader loader = new ServerLoader(s.getAdsProperties(),
              dn, pwd, getTrustManager());
          InitialLdapContext ctx = null;
          try
          {
            ctx = loader.createContext();
            configureToReplicateBaseDN(ctx, baseDN, repServers, usedIds);
            if (!alreadyConfiguredReplicationServers.contains(s.getId()))
            {
              updateReplicationServer(ctx, allRepServers);
              alreadyConfiguredReplicationServers.add(s.getId());
            }
          }
          catch (NamingException ne)
          {
            String hostPort = server.getHostPort(true);
            Message msg = getMessageForException(ne, hostPort);
            throw new ReplicationCliException(msg, ERROR_CONNECTING, ne);
          }
          catch (OpenDsException ode)
          {
            String hostPort = server.getHostPort(true);
            Message msg = getMessageForEnableException(ode, hostPort, baseDN);
            throw new ReplicationCliException(msg,
                ERROR_ENABLING_REPLICATION_ON_BASEDN, ode);
          }
          finally
          {
            if (ctx != null)
            {
              try
              {
                ctx.close();
              }
              catch (Throwable t)
              {
              }
            }
          }
          alreadyConfiguredServers.add(s.getId());
        }
      }
    }
  }

  /**
   * Returns the Map of properties to be used to update the ADS.
   * This map uses the data provided by the user.
   * @return the Map of properties to be used to update the ADS.
   * This map uses the data provided by the user
   */
  private Map<ADSContext.AdministratorProperty, Object>
  getAdministratorProperties(ReplicationUserData uData)
  {
    Map<ADSContext.AdministratorProperty, Object> adminProperties =
      new HashMap<ADSContext.AdministratorProperty, Object>();
    adminProperties.put(ADSContext.AdministratorProperty.UID,
        uData.getAdminUid());
    adminProperties.put(ADSContext.AdministratorProperty.PASSWORD,
        uData.getAdminPwd());
    adminProperties.put(ADSContext.AdministratorProperty.DESCRIPTION,
        INFO_GLOBAL_ADMINISTRATOR_DESCRIPTION.get().toString());
    return adminProperties;
  }

  private void initializeSuffix(String baseDN, InitialLdapContext ctxSource,
      InitialLdapContext ctxDestination, boolean displayProgress)
  throws ReplicationCliException
  {
    int replicationId = -1;
    try
    {
      ServerDescriptor source = ServerDescriptor.createStandalone(ctxSource);
      for (ReplicaDescriptor replica : source.getReplicas())
      {
        if (Utils.areDnsEqual(replica.getSuffix().getDN(), baseDN))
        {
          replicationId = replica.getReplicationId();
          break;
        }
      }
    }
    catch (NamingException ne)
    {
      String hostPort = ConnectionUtils.getHostPort(ctxSource);
      Message msg = getMessageForException(ne, hostPort);
      throw new ReplicationCliException(msg, ERROR_READING_CONFIGURATION, ne);
    }

    if (replicationId == -1)
    {
      throw new ReplicationCliException(
          ERR_INITIALIZING_REPLICATIONID_NOT_FOUND.get(
              ConnectionUtils.getHostPort(ctxSource), baseDN),
          REPLICATIONID_NOT_FOUND, null);
    }

    OfflineInstaller installer = new OfflineInstaller();
    installer.setProgressMessageFormatter(formatter);
    installer.addProgressUpdateListener(new ProgressUpdateListener()
    {
      public void progressUpdate(ProgressUpdateEvent ev)
      {
        Message newLogDetails = ev.getNewLogs();
        if ((newLogDetails != null) &&
            !newLogDetails.toString().trim().equals(""))
        {
          printProgress(newLogDetails);
          printlnProgress();
        }
      }
    });
    int nTries = 5;
    boolean initDone = false;
    while (!initDone)
    {
      try
      {
        installer.initializeSuffix(ctxDestination, replicationId, baseDN,
            displayProgress, ConnectionUtils.getHostPort(ctxSource));
        initDone = true;
      }
      catch (PeerNotFoundException pnfe)
      {
        LOG.log(Level.INFO, "Peer could not be found");
        if (nTries == 1)
        {
          throw new ReplicationCliException(
              ERR_REPLICATION_INITIALIZING_TRIES_COMPLETED.get(
                  pnfe.getMessageObject().toString()),
              INITIALIZING_TRIES_COMPLETED, pnfe);
        }
        try
        {
          Thread.sleep((5 - nTries) * 3000);
        }
        catch (Throwable t)
        {
        }
      }
      catch (ApplicationException ae)
      {
        throw new ReplicationCliException(ae.getMessageObject(),
            ERROR_INITIALIZING_BASEDN_GENERIC, ae);
      }
      nTries--;
    }
  }

  private void initializeAllSuffix(String baseDN, InitialLdapContext ctx,
  boolean displayProgress) throws ReplicationCliException
  {
    int nTries = 5;
    boolean initDone = false;
    while (!initDone)
    {
      try
      {
        initializeAllSuffixTry(baseDN, ctx, displayProgress);
        initDone = true;
      }
      catch (PeerNotFoundException pnfe)
      {
        LOG.log(Level.INFO, "Peer could not be found");
        if (nTries == 1)
        {
          throw new ReplicationCliException(
              ERR_REPLICATION_INITIALIZING_TRIES_COMPLETED.get(
                  pnfe.getMessageObject().toString()),
              INITIALIZING_TRIES_COMPLETED, pnfe);
        }
        try
        {
          Thread.sleep((5 - nTries) * 3000);
        }
        catch (Throwable t)
        {
        }
      }
      catch (ApplicationException ae)
      {
        throw new ReplicationCliException(ae.getMessageObject(),
            ERROR_INITIALIZING_BASEDN_GENERIC, ae);
      }
      nTries--;
    }
  }

  /**
   * Launches the pre external initialization operation using the provided
   * connection on a given base DN.
   * @param baseDN the base DN that we want to reset.
   * @param ctx the connection to the server.
   * @param onlyLocal whether the resetting internal operations must only apply
   * to the server to which we are connected.
   * @param displayProgress whether to display operation progress or not.
   * @throws ReplicationCliException if there is an error performing the
   * operation.
   */
  private void preExternalInitialization(String baseDN, InitialLdapContext ctx,
      boolean onlyLocal, boolean displayProgress) throws ReplicationCliException
  {
    postPreExternalInitialization(baseDN, ctx, onlyLocal, displayProgress,
        true);
  }

  /**
   * Launches the post external initialization operation using the provided
   * connection on a given base DN required for replication to work.
   * @param baseDN the base DN that we want to reset.
   * @param ctx the connection to the server.
   * @param displayProgress whether to display operation progress or not.
   * @throws ReplicationCliException if there is an error performing the
   * operation.
   */
  private void postExternalInitialization(String baseDN, InitialLdapContext ctx,
      boolean displayProgress) throws ReplicationCliException
  {
    postPreExternalInitialization(baseDN, ctx, false, displayProgress, false);
  }

  /**
   * Launches the pre or post external initialization operation using the
   * provided connection on a given base DN.
   * @param baseDN the base DN that we want to reset.
   * @param ctx the connection to the server.
   * @param onlyLocal whether the resetting internal operations must only apply
   * to the server to which we are connected.
   * @param displayProgress whether to display operation progress or not.
   * @param isPre whether this is the pre operation or the post operation.
   * @throws ReplicationCliException if there is an error performing the
   * operation.
   */
  private void postPreExternalInitialization(String baseDN,
      InitialLdapContext ctx, boolean onlyLocal, boolean displayProgress,
      boolean isPre) throws ReplicationCliException
  {
    boolean taskCreated = false;
    int i = 1;
    boolean isOver = false;
    String dn = null;
    BasicAttributes attrs = new BasicAttributes();
    Attribute oc = new BasicAttribute("objectclass");
    oc.add("top");
    oc.add("ds-task");
    oc.add("ds-task-reset-generation-id");
    attrs.put(oc);
    attrs.put("ds-task-class-name",
        "org.opends.server.tasks.SetGenerationIdTask");
    if (isPre)
    {
      if (!onlyLocal)
      {
        attrs.put("ds-task-reset-generation-id-new-value", "-1");
      }
      else
      {
        try
        {
          attrs.put("ds-task-reset-generation-id-new-value",
            String.valueOf(getReplicationDomainId(ctx, baseDN)));
        }
        catch (NamingException ne)
        {
          LOG.log(Level.SEVERE, "Error get replication domain id for base DN "+
              baseDN+" on server "+ConnectionUtils.getHostPort(ctx), ne);

          throw new ReplicationCliException(getThrowableMsg(
              ERR_LAUNCHING_PRE_EXTERNAL_INITIALIZATION.get(), ne),
              ERROR_LAUNCHING_PRE_EXTERNAL_INITIALIZATION, ne);
        }
      }
    }
    attrs.put("ds-task-reset-generation-id-domain-base-dn", baseDN);
    while (!taskCreated)
    {
      String id = "dsreplication-reset-generation-id-"+i;
      dn = "ds-task-id="+id+",cn=Scheduled Tasks,cn=Tasks";
      attrs.put("ds-task-id", id);
      try
      {
        DirContext dirCtx = ctx.createSubcontext(dn, attrs);
        taskCreated = true;
        LOG.log(Level.INFO, "created task entry: "+attrs);
        dirCtx.close();
      }
      catch (NameAlreadyBoundException x)
      {
      }
      catch (NamingException ne)
      {
        LOG.log(Level.SEVERE, "Error creating task "+attrs, ne);
        Message msg = isPre ?
        ERR_LAUNCHING_PRE_EXTERNAL_INITIALIZATION.get():
          ERR_LAUNCHING_POST_EXTERNAL_INITIALIZATION.get();
        ReplicationCliReturnCode code = isPre?
            ERROR_LAUNCHING_PRE_EXTERNAL_INITIALIZATION:
              ERROR_LAUNCHING_POST_EXTERNAL_INITIALIZATION;
        throw new ReplicationCliException(
            getThrowableMsg(msg, ne), code, ne);
      }
      i++;
    }
    // Wait until it is over
    SearchControls searchControls = new SearchControls();
    searchControls.setCountLimit(1);
    searchControls.setSearchScope(
        SearchControls. OBJECT_SCOPE);
    String filter = "objectclass=*";
    searchControls.setReturningAttributes(
        new String[] {
            "ds-task-log-message",
            "ds-task-state"
        });
    String lastLogMsg = null;
    while (!isOver)
    {
      try
      {
        Thread.sleep(500);
      }
      catch (Throwable t)
      {
      }
      try
      {
        NamingEnumeration res = ctx.search(dn, filter, searchControls);
        SearchResult sr = (SearchResult)res.next();
        String logMsg = getFirstValue(sr, "ds-task-log-message");
        if (logMsg != null)
        {
          if (!logMsg.equals(lastLogMsg))
          {
            LOG.log(Level.INFO, logMsg);
            lastLogMsg = logMsg;
          }
        }
        InstallerHelper helper = new InstallerHelper();
        String state = getFirstValue(sr, "ds-task-state");

        if (helper.isDone(state) || helper.isStoppedByError(state))
        {
          isOver = true;
          Message errorMsg;
          String server = ConnectionUtils.getHostPort(ctx);
          if (lastLogMsg == null)
          {
            errorMsg = isPre ?
                INFO_ERROR_DURING_PRE_EXTERNAL_INITIALIZATION_NO_LOG.get(
                state, server) :
                  INFO_ERROR_DURING_POST_EXTERNAL_INITIALIZATION_NO_LOG.get(
                      state, server);
          }
          else
          {
            errorMsg = isPre ?
                INFO_ERROR_DURING_PRE_EXTERNAL_INITIALIZATION_LOG.get(
                lastLogMsg, state, server) :
                  INFO_ERROR_DURING_POST_EXTERNAL_INITIALIZATION_LOG.get(
                      lastLogMsg, state, server);
          }

          if (helper.isCompletedWithErrors(state))
          {
            LOG.log(Level.WARNING, "Completed with error: "+errorMsg);
            println(errorMsg);
          }
          else if (!helper.isSuccessful(state) ||
              helper.isStoppedByError(state))
          {
            LOG.log(Level.WARNING, "Error: "+errorMsg);
            ReplicationCliReturnCode code = isPre?
                ERROR_LAUNCHING_PRE_EXTERNAL_INITIALIZATION:
                  ERROR_LAUNCHING_POST_EXTERNAL_INITIALIZATION;
            throw new ReplicationCliException(errorMsg, code, null);
          }
        }
      }
      catch (NameNotFoundException x)
      {
        isOver = true;
      }
      catch (NamingException ne)
      {
        Message msg = isPre ?
            ERR_POOLING_PRE_EXTERNAL_INITIALIZATION.get():
              ERR_POOLING_POST_EXTERNAL_INITIALIZATION.get();
            throw new ReplicationCliException(
                getThrowableMsg(msg, ne), ERROR_CONNECTING, ne);
      }
    }
  }

  /**
   * Initializes a suffix with the contents of a replica that has a given
   * replication id.
   * @param ctx the connection to the server whose suffix we want to initialize.
   * @param baseDN the dn of the suffix.
   * @param displayProgress whether we want to display progress or not.
   * @throws ApplicationException if an unexpected error occurs.
   * @throws PeerNotFoundException if the replication mechanism cannot find
   * a peer.
   */
  public void initializeAllSuffixTry(String baseDN, InitialLdapContext ctx,
      boolean displayProgress)
  throws ApplicationException, PeerNotFoundException
  {
    boolean taskCreated = false;
    int i = 1;
    boolean isOver = false;
    String dn = null;
    String serverDisplay = ConnectionUtils.getHostPort(ctx);
    BasicAttributes attrs = new BasicAttributes();
    Attribute oc = new BasicAttribute("objectclass");
    oc.add("top");
    oc.add("ds-task");
    oc.add("ds-task-initialize-remote-replica");
    attrs.put(oc);
    attrs.put("ds-task-class-name",
        "org.opends.server.tasks.InitializeTargetTask");
    attrs.put("ds-task-initialize-domain-dn", baseDN);
    attrs.put("ds-task-initialize-replica-server-id", "all");
    while (!taskCreated)
    {
      String id = "quicksetup-initialize"+i;
      dn = "ds-task-id="+id+",cn=Scheduled Tasks,cn=Tasks";
      attrs.put("ds-task-id", id);
      try
      {
        DirContext dirCtx = ctx.createSubcontext(dn, attrs);
        taskCreated = true;
        LOG.log(Level.INFO, "created task entry: "+attrs);
        dirCtx.close();
      }
      catch (NameAlreadyBoundException x)
      {
        LOG.log(Level.WARNING, "A task with dn: "+dn+" already existed.");
      }
      catch (NamingException ne)
      {
        LOG.log(Level.SEVERE, "Error creating task "+attrs, ne);
        throw new ApplicationException(
            ReturnCode.APPLICATION_ERROR,
                getThrowableMsg(INFO_ERROR_LAUNCHING_INITIALIZATION.get(
                        serverDisplay), ne), ne);
      }
      i++;
    }
    // Wait until it is over
    SearchControls searchControls = new SearchControls();
    searchControls.setCountLimit(1);
    searchControls.setSearchScope(
        SearchControls. OBJECT_SCOPE);
    String filter = "objectclass=*";
    searchControls.setReturningAttributes(
        new String[] {
            "ds-task-unprocessed-entry-count",
            "ds-task-processed-entry-count",
            "ds-task-log-message",
            "ds-task-state"
        });
    Message lastDisplayedMsg = null;
    String lastLogMsg = null;
    long lastTimeMsgDisplayed = -1;
    long lastTimeMsgLogged = -1;
    int totalEntries = 0;
    while (!isOver)
    {
      try
      {
        Thread.sleep(500);
      }
      catch (Throwable t)
      {
      }
      try
      {
        NamingEnumeration res = ctx.search(dn, filter, searchControls);
        SearchResult sr = (SearchResult)res.next();

        // Get the number of entries that have been handled and
        // a percentage...
        Message msg;
        String sProcessed = getFirstValue(sr,
        "ds-task-processed-entry-count");
        String sUnprocessed = getFirstValue(sr,
        "ds-task-unprocessed-entry-count");
        int processed = -1;
        int unprocessed = -1;
        if (sProcessed != null)
        {
          processed = Integer.parseInt(sProcessed);
        }
        if (sUnprocessed != null)
        {
          unprocessed = Integer.parseInt(sUnprocessed);
        }
        totalEntries = Math.max(totalEntries, processed+unprocessed);

        if ((processed != -1) && (unprocessed != -1))
        {
          if (processed + unprocessed > 0)
          {
            int perc = (100 * processed) / (processed + unprocessed);
            msg = INFO_INITIALIZE_PROGRESS_WITH_PERCENTAGE.get(sProcessed,
                String.valueOf(perc));
          }
          else
          {
            //msg = INFO_NO_ENTRIES_TO_INITIALIZE.get();
            msg = null;
          }
        }
        else if (processed != -1)
        {
          msg = INFO_INITIALIZE_PROGRESS_WITH_PROCESSED.get(sProcessed);
        }
        else if (unprocessed != -1)
        {
          msg = INFO_INITIALIZE_PROGRESS_WITH_UNPROCESSED.get(sUnprocessed);
        }
        else
        {
          msg = lastDisplayedMsg;
        }

        if (msg != null)
        {
          long currentTime = System.currentTimeMillis();
          /* Refresh period: to avoid having too many lines in the log */
          long minRefreshPeriod;
          if (totalEntries < 100)
          {
            minRefreshPeriod = 0;
          }
          else if (totalEntries < 1000)
          {
            minRefreshPeriod = 1000;
          }
          else if (totalEntries < 10000)
          {
            minRefreshPeriod = 5000;
          }
          else
          {
            minRefreshPeriod = 10000;
          }
          if (((currentTime - minRefreshPeriod) > lastTimeMsgLogged))
          {
            lastTimeMsgLogged = currentTime;
            LOG.log(Level.INFO, "Progress msg: "+msg);
          }
          if (displayProgress)
          {
            if (((currentTime - minRefreshPeriod) > lastTimeMsgDisplayed) &&
                !msg.equals(lastDisplayedMsg))
            {
              printProgress(msg);
              lastDisplayedMsg = msg;
              printlnProgress();
              lastTimeMsgDisplayed = currentTime;
            }
          }
        }

        String logMsg = getFirstValue(sr, "ds-task-log-message");
        if (logMsg != null)
        {
          if (!logMsg.equals(lastLogMsg))
          {
            LOG.log(Level.INFO, logMsg);
            lastLogMsg = logMsg;
          }
        }
        InstallerHelper helper = new InstallerHelper();
        String state = getFirstValue(sr, "ds-task-state");

        if (helper.isDone(state) || helper.isStoppedByError(state))
        {
          isOver = true;
          Message errorMsg;
          LOG.log(Level.INFO, "Last task entry: "+sr);
          if (displayProgress && (msg != null) && !msg.equals(lastDisplayedMsg))
          {
            printProgress(msg);
            lastDisplayedMsg = msg;
            printlnProgress();
          }
          if (lastLogMsg == null)
          {
            errorMsg = INFO_ERROR_DURING_INITIALIZATION_NO_LOG.get(
                    serverDisplay, state, serverDisplay);
          }
          else
          {
            errorMsg = INFO_ERROR_DURING_INITIALIZATION_LOG.get(
                serverDisplay, lastLogMsg, state, serverDisplay);
          }

          LOG.log(Level.WARNING, "Processed errorMsg: "+errorMsg);
          if (helper.isCompletedWithErrors(state))
          {
            if (displayProgress)
            {
              println(errorMsg);
            }
          }
          else if (!helper.isSuccessful(state) ||
              helper.isStoppedByError(state))
          {
            ApplicationException ae = new ApplicationException(
                ReturnCode.APPLICATION_ERROR, errorMsg,
                null);
            if ((lastLogMsg == null) ||
                helper.isPeersNotFoundError(lastLogMsg))
            {
              LOG.log(Level.WARNING, "Throwing peer not found error.  "+
                  "Last Log Msg: "+lastLogMsg);
              // Assume that this is a peer not found error.
              throw new PeerNotFoundException(errorMsg);
            }
            else
            {
              LOG.log(Level.SEVERE, "Throwing ApplicationException.");
              throw ae;
            }
          }
          else
          {
            if (displayProgress)
            {
              printProgress(INFO_SUFFIX_INITIALIZED_SUCCESSFULLY.get());
              printlnProgress();
            }
            LOG.log(Level.INFO, "Initialization completed successfully.");
          }
        }
      }
      catch (NameNotFoundException x)
      {
        isOver = true;
        LOG.log(Level.INFO, "Initialization entry not found.");
        if (displayProgress)
        {
          printProgress(INFO_SUFFIX_INITIALIZED_SUCCESSFULLY.get());
          printlnProgress();
        }
      }
      catch (NamingException ne)
      {
        throw new ApplicationException(
            ReturnCode.APPLICATION_ERROR,
                getThrowableMsg(INFO_ERROR_POOLING_INITIALIZATION.get(
                    serverDisplay), ne), ne);
      }
    }
  }

  /**
   * Returns a set of error messages encountered in the provided TopologyCache.
   * @param cache the topology cache.
   * @return a set of error messages encountered in the provided TopologyCache.
   */
  private LinkedHashSet<Message> getErrorMessages(TopologyCache cache)
  {
    Set<TopologyCacheException> exceptions =
      new HashSet<TopologyCacheException>();
    Set<ServerDescriptor> servers = cache.getServers();
    LinkedHashSet<Message> exceptionMsgs = new LinkedHashSet<Message>();
    for (ServerDescriptor server : servers)
    {
      TopologyCacheException e = server.getLastException();
      if (e != null)
      {
        exceptions.add(e);
      }
    }
    /* Check the exceptions and see if we throw them or not. */
    for (TopologyCacheException e : exceptions)
    {
      switch (e.getType())
      {
        case NOT_GLOBAL_ADMINISTRATOR:
          exceptionMsgs.add(INFO_NOT_GLOBAL_ADMINISTRATOR_PROVIDED.get());

        break;
      case GENERIC_CREATING_CONNECTION:
        if ((e.getCause() != null) &&
            Utils.isCertificateException(e.getCause()))
        {
          exceptionMsgs.add(
              INFO_ERROR_READING_CONFIG_LDAP_CERTIFICATE_SERVER.get(
              e.getHostPort(), e.getCause().getMessage()));
        }
        else
        {
          exceptionMsgs.add(Utils.getMessage(e));
        }
        break;
      default:
        exceptionMsgs.add(Utils.getMessage(e));
      }
    }
    return exceptionMsgs;
  }

  /**
   * Removes the references to a replication server in the base DNs of a
   * given server.
   * @param server the server that we want to update.
   * @param replicationServer the replication server whose references we want
   * to remove.
   * @param bindDn the bindDn that must be used to log to the server.
   * @param pwd the password that must be used to log to the server.
   * @param baseDNs the list of base DNs where we want to remove the references
   * to the provided replication server.
   * @throws ReplicationCliException if there is an error updating the
   * configuration.
   */
  private void removeReferencesInServer(ServerDescriptor server,
      String replicationServer, String bindDn, String pwd,
      Collection<String> baseDNs)
  throws ReplicationCliException
  {
    ServerLoader loader = new ServerLoader(server.getAdsProperties(), bindDn,
        pwd, getTrustManager());
    InitialLdapContext ctx = null;
    String lastBaseDN = null;
    String hostPort = null;
    try
    {
      ctx = loader.createContext();
      hostPort = ConnectionUtils.getHostPort(ctx);
      ManagementContext mCtx = LDAPManagementContext.createFromContext(
          JNDIDirContextAdaptor.adapt(ctx));
      RootCfgClient root = mCtx.getRootConfiguration();
      ReplicationSynchronizationProviderCfgClient sync = null;
      try
      {
        sync = (ReplicationSynchronizationProviderCfgClient)
        root.getSynchronizationProvider("Multimaster Synchronization");
      }
      catch (ManagedObjectNotFoundException monfe)
      {
        // It does not exist.
        LOG.log(Level.INFO, "No synchronization found on "+ hostPort +".",
            monfe);
      }
      if (sync != null)
      {
        String[] domainNames = sync.listReplicationDomains();
        if (domainNames != null)
        {
          for (int i=0; i<domainNames.length; i++)
          {
            ReplicationDomainCfgClient domain =
              sync.getReplicationDomain(domainNames[i]);
            for (String baseDN : baseDNs)
            {
              lastBaseDN = baseDN;
              if (Utils.areDnsEqual(domain.getBaseDN().toString(),
                  baseDN))
              {
                printProgress(formatter.getFormattedWithPoints(
                    INFO_REPLICATION_REMOVING_REFERENCES_ON_REMOTE.get(baseDN,
                        hostPort)));
                Set<String> replServers = domain.getReplicationServer();
                if (replServers != null)
                {
                  String replServer = null;
                  for (String o : replServers)
                  {
                    if (replicationServer.equalsIgnoreCase(o))
                    {
                      replServer = o;
                      break;
                    }
                  }
                  if (replServer != null)
                  {
                    LOG.log(Level.INFO, "Updating references in domain " +
                        domain.getBaseDN()+" on " + hostPort + ".");
                    replServers.remove(replServer);
                    if (replServers.size() > 0)
                    {
                      domain.setReplicationServer(replServers);
                      domain.commit();
                    }
                    else
                    {
                      sync.removeReplicationDomain(domainNames[i]);
                      sync.commit();
                    }
                  }
                }
                printProgress(formatter.getFormattedDone());
                printlnProgress();
              }
            }
          }
        }
      }
    }
    catch (NamingException ne)
    {
      hostPort = server.getHostPort(true);
      Message msg = getMessageForException(ne, hostPort);
      throw new ReplicationCliException(msg, ERROR_CONNECTING, ne);
    }
    catch (OpenDsException ode)
    {
      if (lastBaseDN != null)
      {
        Message msg = getMessageForDisableException(ode, hostPort, lastBaseDN);
        throw new ReplicationCliException(msg,
          ERROR_DISABLING_REPLICATION_REMOVE_REFERENCE_ON_BASEDN, ode);
      }
      else
      {
        Message msg = ERR_REPLICATION_ERROR_READING_CONFIGURATION.get(hostPort,
            ode.getMessage());
        throw new ReplicationCliException(msg, ERROR_CONNECTING, ode);
      }
    }
    finally
    {
      if (ctx != null)
      {
        try
        {
          ctx.close();
        }
        catch (Throwable t)
        {
        }
      }
    }
  }

  /**
   * Deletes a replication domain in a server for a given base DN (disable
   * replication of the base DN).
   * @param ctx the connection to the server.
   * @param baseDN the base DN of the replication domain that we want to
   * delete.
   * @throws ReplicationCliException if there is an error updating the
   * configuration of the server.
   */
  private void deleteReplicationDomain(InitialLdapContext ctx,
      String baseDN) throws ReplicationCliException
  {
    String hostPort = ConnectionUtils.getHostPort(ctx);
    try
    {
      ManagementContext mCtx = LDAPManagementContext.createFromContext(
          JNDIDirContextAdaptor.adapt(ctx));
      RootCfgClient root = mCtx.getRootConfiguration();
      ReplicationSynchronizationProviderCfgClient sync = null;
      try
      {
        sync = (ReplicationSynchronizationProviderCfgClient)
        root.getSynchronizationProvider("Multimaster Synchronization");
      }
      catch (ManagedObjectNotFoundException monfe)
      {
        // It does not exist.
        LOG.log(Level.INFO, "No synchronization found on "+ hostPort +".",
            monfe);
      }
      if (sync != null)
      {
        String[] domainNames = sync.listReplicationDomains();
        if (domainNames != null)
        {
          for (int i=0; i<domainNames.length; i++)
          {
            ReplicationDomainCfgClient domain =
              sync.getReplicationDomain(domainNames[i]);
            if (Utils.areDnsEqual(domain.getBaseDN().toString(), baseDN))
            {
              printProgress(formatter.getFormattedWithPoints(
                  INFO_REPLICATION_DISABLING_BASEDN.get(baseDN,
                      hostPort)));
              sync.removeReplicationDomain(domainNames[i]);
              sync.commit();

              printProgress(formatter.getFormattedDone());
              printlnProgress();
            }
          }
        }
      }
    }
    catch (OpenDsException ode)
    {
      Message msg = getMessageForDisableException(ode, hostPort, baseDN);
        throw new ReplicationCliException(msg,
          ERROR_DISABLING_REPLICATION_REMOVE_REFERENCE_ON_BASEDN, ode);
    }
  }

  /**
   * Returns a message object for the given NamingException.
   * @param ne the NamingException.
   * @param hostPort the hostPort representation of the server we were
   * contacting when the NamingException occurred.
   * @return a message object for the given NamingException.
   */
  private Message getMessageForException(NamingException ne, String hostPort)
  {
    Message msg;
    if (Utils.isCertificateException(ne))
    {
      msg = INFO_ERROR_READING_CONFIG_LDAP_CERTIFICATE_SERVER.get(
              hostPort, ne.toString(true));
    }
    else
    {
       msg = INFO_CANNOT_CONNECT_TO_REMOTE_GENERIC.get(
          hostPort, ne.toString(true));
    }
    return msg;
  }

  /**
   * Returns a message for a given OpenDsException (we assume that was an
   * exception generated updating the configuration of the server) that
   * occurred when we were configuring the replication server.
   * @param ode the OpenDsException.
   * @param hostPort the hostPort representation of the server we were
   * contacting when the OpenDsException occurred.
   * @return a message for a given OpenDsException (we assume that was an
   * exception generated updating the configuration of the server) that
   * occurred when we were configuring the replication server.
   */
  private Message getMessageForReplicationServerException(OpenDsException ode,
      String hostPort)
  {
    return ERR_REPLICATION_CONFIGURING_REPLICATIONSERVER.get(hostPort);
  }

  /**
   * Returns a message for a given OpenDsException (we assume that was an
   * exception generated updating the configuration of the server) that
   * occurred when we were configuring some replication domain (creating
   * the replication domain or updating the list of replication servers of
   * the replication domain).
   * @param ode the OpenDsException.
   * @param hostPort the hostPort representation of the server we were
   * contacting when the OpenDsException occurred.
   * @return a message for a given OpenDsException (we assume that was an
   * exception generated updating the configuration of the server) that
   * occurred when we were configuring some replication domain (creating
   * the replication domain or updating the list of replication servers of
   * the replication domain).
   */
  private Message getMessageForEnableException(OpenDsException ode,
      String hostPort, String baseDN)
  {
    return ERR_REPLICATION_CONFIGURING_BASEDN.get(baseDN, hostPort);
  }

  /**
   * Returns a message for a given OpenDsException (we assume that was an
   * exception generated updating the configuration of the server) that
   * occurred when we were configuring some replication domain (deleting
   * the replication domain or updating the list of replication servers of
   * the replication domain).
   * @param ode the OpenDsException.
   * @param hostPort the hostPort representation of the server we were
   * contacting when the OpenDsException occurred.
   * @return a message for a given OpenDsException (we assume that was an
   * exception generated updating the configuration of the server) that
   * occurred when we were configuring some replication domain (deleting
   * the replication domain or updating the list of replication servers of
   * the replication domain).
   */
  private Message getMessageForDisableException(OpenDsException ode,
      String hostPort, String baseDN)
  {
    return ERR_REPLICATION_CONFIGURING_BASEDN.get(baseDN, hostPort);
  }

  /**
   * Returns a message informing the user that the provided port cannot be used.
   * @param port the port that cannot be used.
   * @return a message informing the user that the provided port cannot be used.
   */
  private Message getCannotBindToPortError(int port)
  {
    Message message;
    if (SetupUtils.isPriviledgedPort(port))
    {
      message = ERR_INSTALLDS_CANNOT_BIND_TO_PRIVILEGED_PORT.get(port);
    }
    else
    {
      message = ERR_INSTALLDS_CANNOT_BIND_TO_PORT.get(port);
    }
    return message;
  }

  /**
   * Convenience method used to know if one Set of replication servers equals
   * another set of replication servers.
   * @param s1 the first set of replication servers.
   * @param s2 the second set of replication servers.
   * @return <CODE>true</CODE> if the two sets represent the same replication
   * servers and <CODE>false</CODE> otherwise.
   */
  private boolean areReplicationServersEqual(Set<String> s1, Set<String> s2)
  {
    Set<String> c1 = new HashSet<String>();
    for (String s : s1)
    {
      c1.add(s.toLowerCase());
    }
    Set<String> c2 = new HashSet<String>();
    for (String s : s2)
    {
      c2.add(s.toLowerCase());
    }
    return c1.equals(c2);
  }

  /**
   * Convenience method used to merge two Sets of replication servers.
   * @param s1 the first set of replication servers.
   * @param s2 the second set of replication servers.
   * @return a Set of replication servers containing all the replication servers
   * specified in the provided Sets.
   */
  private Set<String> mergeReplicationServers(Set<String> s1, Set<String> s2)
  {
    Set<String> c1 = new HashSet<String>();
    for (String s : s1)
    {
      c1.add(s.toLowerCase());
    }
    for (String s : s2)
    {
      c1.add(s.toLowerCase());
    }
    return c1;
  }

  /**
   * Returns the message that must be displayed to the user for a given
   * exception.  This is assumed to be a critical exception that stops all
   * the processing.
   * @param rce the ReplicationCliException.
   * @return a message to be displayed to the user.
   */
  private Message getCriticalExceptionMessage(ReplicationCliException rce)
  {
    MessageBuilder mb = new MessageBuilder();
    mb.append(rce.getMessageObject());
    File logFile = QuickSetupLog.getLogFile();
    if (logFile != null)
    {
      mb.append(Constants.LINE_SEPARATOR);
      mb.append(INFO_GENERAL_SEE_FOR_DETAILS.get(logFile.getPath()));
    }
    // Check if the cause has already been included in the message
    Throwable c = rce.getCause();
    if (c != null)
    {
      String s;
      if (c instanceof NamingException)
      {
        s = ((NamingException)c).toString(true);
      }
      else
      {
        s = c.toString();
      }
      if (mb.toString().indexOf(s) == -1)
      {
        mb.append(Constants.LINE_SEPARATOR);
        mb.append(INFO_REPLICATION_CRITICAL_ERROR_DETAILS.get(s));
      }
    }
    return mb.toMessage();
  }

  /**
   * Basic method to know if the host is local or not.  This is only used to
   * know if we can perform a port check or not.
   * @param host the host to analyze.
   * @return <CODE>true</CODE> if it is the local host and <CODE>false</CODE>
   * otherwise.
   */
  private boolean isLocalHost(String host)
  {
    boolean isLocalHost = false;
    if (!"localhost".equalsIgnoreCase(host))
    {
      try
      {
        InetAddress localAddress = InetAddress.getLocalHost();
        InetAddress[] addresses = InetAddress.getAllByName(host);
        for (int i=0; i<addresses.length && !isLocalHost; i++)
        {
          isLocalHost = localAddress.equals(addresses[i]);
        }
      }
      catch (Throwable t)
      {
        LOG.log(Level.WARNING, "Failing checking host names: "+t, t);
      }
    }
    else
    {
      isLocalHost = true;
    }
    return isLocalHost;
  }

  private boolean mustInitializeSchema(ServerDescriptor server1,
      ServerDescriptor server2)
  {
    boolean mustInitializeSchema = false;
    if (!argParser.noSchemaReplication())
    {
      String id1 = server1.getSchemaReplicationID();
      String id2 = server2.getSchemaReplicationID();
      if (id1 != null)
      {
        mustInitializeSchema = id1.equals(id2);
      }
      else
      {
        mustInitializeSchema = true;
      }
    }
    return mustInitializeSchema;
  }

  /**
   * This method registers a server in a given ADSContext.  If the server was
   * already registered it unregisters it and registers again (some properties
   * might have changed).
   * @param adsContext the ADS Context to be used.
   * @param server the server to be registered.
   * @throws ADSContextException if an error occurs during the registration or
   * unregistration of the server.
   */
  private void registerServer(ADSContext adsContext,
      Map<ADSContext.ServerProperty, Object> serverProperties)
  throws ADSContextException
  {
    try
    {
      adsContext.registerServer(serverProperties);
    }
    catch (ADSContextException ade)
    {
      if (ade.getError() ==
        ADSContextException.ErrorType.ALREADY_REGISTERED)
      {
        LOG.log(Level.WARNING, "The server was already registered: "+
            serverProperties);
        adsContext.unregisterServer(serverProperties);
        adsContext.registerServer(serverProperties);
      }
      else
      {
        throw ade;
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean isAdvancedMode() {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isInteractive() {
    if (forceNonInteractive)
    {
      return false;
    }
    else
    {
      return argParser.isInteractive();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isMenuDrivenMode() {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isQuiet()
  {
    return argParser.isQuiet();
  }



  /**
   * {@inheritDoc}
   */
  public boolean isScriptFriendly() {
    return argParser.isScriptFriendly();
  }



  /**
   * {@inheritDoc}
   */
  public boolean isVerbose() {
    return true;
  }

  /**
   * Commodity method used to repeatidly ask the user to provide a port value.
   * @param prompt the prompt message.
   * @param defaultValue the default value of the port to be proposed to the
   * user.
   * @return the port value provided by the user.
   */
  private int askPort(Message prompt, int defaultValue)
  {
    int port = -1;
    while (port == -1)
    {
      try
      {
        port = readPort(prompt, defaultValue);
      }
      catch (CLIException ce)
      {
        port = -1;
        LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
      }
    }
    return port;
  }

  /**
   * Prompts the user to give the Global Administrator UID.
   * @param defaultValue the default value that will be proposed in the prompt
   * message.
   * @return the Global Administrator UID as provided by the user.
   */
  private String askForAdministratorUID(String defaultValue)
  {
    String s = defaultValue;
    try
    {
      s = readInput(INFO_ADMINISTRATOR_UID_PROMPT.get(), defaultValue);
    }
    catch (CLIException ce)
    {
      LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
    }
    return s;
  }

  /**
   * Prompts the user to give the Global Administrator password.
   * @return the Global Administrator password as provided by the user.
   */
  private String askForAdministratorPwd()
  {
    String pwd = readPassword(INFO_ADMINISTRATOR_PWD_PROMPT.get(), LOG);
    return pwd;
  }

  /**
   * Prints a message to the output with no wrapping if we are not in quiet
   * mode.
   * @param msg the message to be displayed.
   */
  private void printProgressMessageNoWrap(Message msg)
  {
    if (!isQuiet())
    {
      getOutputStream().print(msg.toString());
    }
  }

  /**
   * Resets the connection parameters for the LDAPConsoleInteraction  object.
   * The reset does not apply to the certificate parameters.  This is called
   * in order the LDAPConnectionConsoleInteraction object to ask for all this
   * connection parameters next time we call
   * LDAPConnectionConsoleInteraction.run().
   */
  private void resetConnectionArguments()
  {
    argParser.getSecureArgsList().hostNameArg.clearValues();
    argParser.getSecureArgsList().hostNameArg.setPresent(false);
    argParser.getSecureArgsList().portArg.clearValues();
    argParser.getSecureArgsList().portArg.setPresent(false);
    //  This is done to be able to call IntegerArgument.getIntValue()
    argParser.getSecureArgsList().portArg.addValue(
        argParser.getSecureArgsList().portArg.getDefaultValue());
    argParser.getSecureArgsList().bindDnArg.clearValues();
    argParser.getSecureArgsList().bindDnArg.setPresent(false);
    argParser.getSecureArgsList().bindPasswordArg.clearValues();
    argParser.getSecureArgsList().bindPasswordArg.setPresent(false);
    argParser.getSecureArgsList().bindPasswordFileArg.clearValues();
    argParser.getSecureArgsList().bindPasswordFileArg.setPresent(false);
    argParser.getSecureArgsList().adminUidArg.clearValues();
    argParser.getSecureArgsList().adminUidArg.setPresent(false);
  }

  /**
   * Initializes the global arguments in the parser with the provided values.
   */
  private void initializeGlobalArguments(String hostName, int port,
      boolean useSSL, boolean useStartTLS, String adminUid, String bindDn,
      String bindPwd)
  {
    resetConnectionArguments();
    if (hostName != null)
    {
      argParser.getSecureArgsList().hostNameArg.addValue(hostName);
      argParser.getSecureArgsList().hostNameArg.setPresent(true);
    }
    // resetConnectionArguments does not clear the values for the port
    argParser.getSecureArgsList().portArg.clearValues();
    if (port != -1)
    {
      argParser.getSecureArgsList().portArg.addValue(String.valueOf(port));
      argParser.getSecureArgsList().portArg.setPresent(true);
    }
    else
    {
      // This is done to be able to call IntegerArgument.getIntValue()
      argParser.getSecureArgsList().portArg.addValue(
          argParser.getSecureArgsList().portArg.getDefaultValue());
    }
    argParser.getSecureArgsList().useSSLArg.setPresent(useSSL);
    argParser.getSecureArgsList().useStartTLSArg.setPresent(useStartTLS);
    if (adminUid != null)
    {
      argParser.getSecureArgsList().adminUidArg.addValue(adminUid);
      argParser.getSecureArgsList().adminUidArg.setPresent(true);
    }
    if (bindDn != null)
    {
      argParser.getSecureArgsList().bindDnArg.addValue(bindDn);
      argParser.getSecureArgsList().bindDnArg.setPresent(true);
    }
    if (bindPwd != null)
    {
      argParser.getSecureArgsList().bindPasswordArg.addValue(bindPwd);
      argParser.getSecureArgsList().bindPasswordArg.setPresent(true);
    }
  }


  /**
   * Forces the initialization of the trust manager in the
   * LDAPConnectionInteraction object.
   */
  private void forceTrustManagerInitialization()
  {
    forceNonInteractive = true;
    try
    {
      ci.initializeTrustManagerIfRequired();
    }
    catch (ArgumentException ae)
    {
      LOG.log(Level.WARNING, "Error initializing trust store: "+ae, ae);
    }
    forceNonInteractive = false;
  }

  /**
   * Returns the replication domain ID for a given baseDN on the server.
   * @param ctx the connection to the server.
   * @param baseDN the baseDN for which we want the replication domain ID.
   * @return the replication domain ID or -1 if the replication domain ID
   * could not be found.
   * @throws NamingException if an error occurred reading the configuration
   * information.
   */
  private int getReplicationDomainId(InitialLdapContext ctx, String baseDN)
  throws NamingException
  {
    int domainId = -1;
    ServerDescriptor server = ServerDescriptor.createStandalone(ctx);
    for (ReplicaDescriptor replica : server.getReplicas())
    {
      if (Utils.areDnsEqual(replica.getSuffix().getDN(), baseDN))
      {
        domainId = replica.getReplicationId();
        break;
      }
    }
    return domainId;
  }
}
