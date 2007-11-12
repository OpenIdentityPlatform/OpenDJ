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

package org.opends.guitools.uninstaller;

import org.opends.server.admin.client.cli.DsFrameworkCliReturnCode;
import org.opends.server.admin.client.cli.SecureConnectionCliArgs;

import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ServerDescriptor;
import org.opends.admin.ads.TopologyCache;
import org.opends.admin.ads.TopologyCacheException;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.guitools.statuspanel.ConfigException;
import org.opends.guitools.statuspanel.ConfigFromFile;
import org.opends.guitools.statuspanel.ConnectionProtocolPolicy;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.QuickSetupMessages.*;

import org.opends.quicksetup.*;
import org.opends.quicksetup.event.ProgressUpdateEvent;
import org.opends.quicksetup.event.ProgressUpdateListener;
import org.opends.quicksetup.util.PlainTextProgressMessageFormatter;
import org.opends.quicksetup.util.ServerController;
import org.opends.quicksetup.util.Utils;
import org.opends.server.tools.ClientException;
import org.opends.server.tools.ToolConstants;
import org.opends.server.tools.dsconfig.LDAPManagementContextFactory;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.cli.CLIException;
import org.opends.server.util.cli.ConsoleApplication;
import org.opends.server.util.cli.LDAPConnectionConsoleInteraction;
import org.opends.server.util.cli.Menu;
import org.opends.server.util.cli.MenuBuilder;
import org.opends.server.util.cli.MenuResult;


import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.IOException;
import java.net.URI;

import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;
import javax.net.ssl.TrustManager;

/**
 * The class used to provide some CLI interface in the uninstall.
 *
 * This class basically is in charge of parsing the data provided by the user
 * in the command line and displaying messages asking the user for information.
 *
 * Once the user has provided all the required information it calls Uninstaller
 * and launches it.
 *
 */
class UninstallCliHelper extends ConsoleApplication {

  static private final Logger LOG =
          Logger.getLogger(UninstallCliHelper.class.getName());

  private UninstallerArgumentParser parser;

  private LDAPConnectionConsoleInteraction ci = null;

  /**
   * Default constructor.
   */
  public UninstallCliHelper()
  {
    super(System.in, System.out, System.err);
  }

  /**
   * Creates a UserData based in the arguments provided.  It asks
   * user for additional information if what is provided in the arguments is not
   * enough.
   * @param args the ArgumentParser with the allowed arguments of the command
   * line.
   * @param rawArguments the arguments provided in the command line.
   * @return the UserData object with what the user wants to uninstall
   * and null if the user cancels the uninstallation.
   * @throws UserDataException if there is an error parsing the data
   * in the arguments.
   */
  public UninstallUserData createUserData(UninstallerArgumentParser args,
      String[] rawArguments)
  throws UserDataException
  {
    parser = args;
    UninstallUserData userData = new UninstallUserData();

    boolean isInteractive;
    boolean isQuiet;
    boolean isCancelled = false;

    /* Step 1: analyze the arguments.
     */
    try
    {
      args.parseArguments(rawArguments);
    }
    catch (ArgumentException ae)
    {
      throw new UserDataException(null, ae.getMessageObject());
    }

    isInteractive = args.isInteractive();

    isQuiet = args.isQuiet();

    userData.setQuiet(isQuiet);
    userData.setForceOnError(args.isForceOnError());
    userData.setTrustManager(args.getTrustManager());

    /* Step 2: check that the provided parameters are compatible.
     */
    MessageBuilder buf = new MessageBuilder();
    int v = args.validateGlobalOptions(buf);
    if (v != DsFrameworkCliReturnCode.SUCCESSFUL_NOP.getReturnCode())
    {
      throw new UserDataException(null, buf.toMessage());
    }

    /* Step 3: If this is an interactive uninstall ask for confirmation to
     * delete the different parts of the installation if the user did not
     * specify anything to delete.  If we are not in interactive mode
     * check that the user specified something to be deleted.
     */
    Set<String> outsideDbs;
    Set<String> outsideLogs;
    Configuration config =
            Installation.getLocal().getCurrentConfiguration();
    try {
      outsideDbs = config.getOutsideDbs();
    } catch (IOException ioe) {
      outsideDbs = Collections.emptySet();
      LOG.log(Level.INFO, "error determining outside databases", ioe);
    }

    try {
      outsideLogs = config.getOutsideLogs();
    } catch (IOException ioe) {
      outsideLogs = Collections.emptySet();
      LOG.log(Level.INFO, "error determining outside logs", ioe);
    }

    boolean somethingSpecifiedToDelete =
      args.removeAll() ||
      args.removeBackupFiles() ||
      args.removeDatabases() ||
      args.removeLDIFFiles() ||
      args.removeConfigurationFiles() ||
      args.removeLogFiles() ||
      args.removeServerLibraries();

    if (somethingSpecifiedToDelete)
    {
      userData.setRemoveBackups(args.removeAll() || args.removeBackupFiles());
      userData.setRemoveConfigurationAndSchema(args.removeAll() ||
          args.removeConfigurationFiles());
      userData.setRemoveDatabases(args.removeAll() || args.removeDatabases());
      userData.setRemoveLDIFs(args.removeAll() || args.removeLDIFFiles());
      userData.setRemoveLibrariesAndTools(args.removeAll() ||
          args.removeServerLibraries());
      userData.setRemoveLogs(args.removeAll() || args.removeLogFiles());

      userData.setExternalDbsToRemove(outsideDbs);
      userData.setExternalLogsToRemove(outsideLogs);
    }
    else
    {
      if (!isInteractive)
      {
        throw new UserDataException(null,
            ERR_CLI_UNINSTALL_NOTHING_TO_BE_UNINSTALLED_NON_INTERACTIVE.get());
      }
      else
      {
        isCancelled = askWhatToDelete(userData, outsideDbs, outsideLogs);
      }
    }
    String adminUid = args.getAdministratorUID();
    if ((adminUid == null) && !args.isInteractive())
    {
      adminUid = args.getDefaultAdministratorUID();
    }
    userData.setAdminUID(adminUid);
    userData.setAdminPwd(args.getBindPassword());
    String referencedHostName = args.getReferencedHostName();
    if ((referencedHostName == null) && !args.isInteractive())
    {
      referencedHostName = args.getDefaultReferencedHostName();
      try
      {
        UninstallData d = new UninstallData(Installation.getLocal());
        userData.setReplicationServer(
            referencedHostName+":"+d.getReplicationServerPort());
        userData.setReferencedHostName(referencedHostName);
      }
      catch (Throwable t)
      {
        LOG.log(Level.SEVERE, "Could not create UninstallData: "+t, t);
      }
    }
    userData.setUseSSL(parser.useSSL());
    userData.setUseStartTLS(parser.useStartTLS());

    /*
     * Step 4: check if server is running.  Depending if it is running and the
     * OS we are running, ask for authentication information.
     */
    if (!isCancelled)
    {
      isCancelled = checkServerState(userData);
    }

    if (isCancelled && !userData.isForceOnError())
    {
      userData = null;
    }

    if ((userData != null) && !args.isQuiet())
    {
      println();
    }


    return userData;
  }

  /**
   * Commodity method used to ask the user to confirm the deletion of certain
   * parts of the server.  It updates the provided UserData object
   * accordingly.  Returns <CODE>true</CODE> if the user cancels and <CODE>
   * false</CODE> otherwise.
   * @param userData the UserData object to be updated.
   * @param outsideDbs the set of relative paths of databases located outside
   * the installation path of the server.
   * @param outsideLogs the set of relative paths of log files located outside
   * the installation path of the server.
   * @return <CODE>true</CODE> if the user cancels and <CODE>false</CODE>
   * otherwise.
   */
  private boolean askWhatToDelete(UninstallUserData userData,
      Set<String> outsideDbs, Set<String> outsideLogs)
  {
    boolean cancelled = false;
    final int REMOVE_ALL = 1;
    final int SPECIFY_TO_REMOVE = 2;
    int[] indexes = {REMOVE_ALL, SPECIFY_TO_REMOVE};
    Message[] msgs = new Message[] {
        INFO_CLI_UNINSTALL_REMOVE_ALL.get(),
        INFO_CLI_UNINSTALL_SPECIFY_WHAT_REMOVE.get()
      };

    MenuBuilder<Integer> builder = new MenuBuilder<Integer>(this);
    builder.setPrompt(INFO_CLI_UNINSTALL_WHAT_TO_DELETE.get());

    for (int i=0; i<indexes.length; i++)
    {
      builder.addNumberedOption(msgs[i], MenuResult.success(indexes[i]));
    }

    builder.addQuitOption();

    builder.setDefault(Message.raw(String.valueOf(REMOVE_ALL)),
        MenuResult.success(REMOVE_ALL));

    Menu<Integer> menu = builder.toMenu();
    int choice;
    try
    {
      MenuResult<Integer> m = menu.run();
      if (m.isSuccess())
      {
        choice = m.getValue();
      }
      else if (m.isQuit())
      {
        choice = REMOVE_ALL;
        cancelled = true;
      }
      else
      {
        // Should never happen.
        throw new RuntimeException();
      }
    }
    catch (CLIException ce)
    {
      choice = REMOVE_ALL;
      LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
    }

    if (cancelled)
    {
      // Nothing to do
    }
    else if (choice == REMOVE_ALL)
    {
      userData.setRemoveBackups(true);
      userData.setRemoveConfigurationAndSchema(true);
      userData.setRemoveDatabases(true);
      userData.setRemoveLDIFs(true);
      userData.setRemoveLibrariesAndTools(true);
      userData.setRemoveLogs(true);

      userData.setExternalDbsToRemove(outsideDbs);
      userData.setExternalLogsToRemove(outsideLogs);
    }
    else
    {
      boolean somethingSelected = false;
      while (!somethingSelected)
      {
        println();
//      Ask for confirmation for the different items
        msgs = new Message [] {
                INFO_CLI_UNINSTALL_CONFIRM_LIBRARIES_BINARIES.get(),
                INFO_CLI_UNINSTALL_CONFIRM_DATABASES.get(),
                INFO_CLI_UNINSTALL_CONFIRM_LOGS.get(),
                INFO_CLI_UNINSTALL_CONFIRM_CONFIGURATION_SCHEMA.get(),
                INFO_CLI_UNINSTALL_CONFIRM_BACKUPS.get(),
                INFO_CLI_UNINSTALL_CONFIRM_LDIFS.get(),
                INFO_CLI_UNINSTALL_CONFIRM_OUTSIDEDBS.get(
                        Utils.getStringFromCollection(outsideDbs,
                                Constants.LINE_SEPARATOR)),
                INFO_CLI_UNINSTALL_CONFIRM_OUTSIDELOGS.get(
                        Utils.getStringFromCollection(outsideLogs,
                                Constants.LINE_SEPARATOR)
                )
        };

        boolean[] answers = new boolean[msgs.length];
        for (int i=0; i<msgs.length; i++)
        {
          boolean ignore = ((i == 6) && (outsideDbs.size() == 0)) ||
          ((i == 7) && (outsideLogs.size() == 0));
          if (!ignore)
          {
            answers[i] = confirm(msgs[i], true);
          }
          else
          {
            answers[i] = false;
          }
        }

        for (int i=0; i<answers.length; i++)
        {
          switch (i)
          {
          case 0:
            userData.setRemoveLibrariesAndTools(answers[i]);
            break;

          case 1:
            userData.setRemoveDatabases(answers[i]);
            break;

          case 2:
            userData.setRemoveLogs(answers[i]);
            break;

          case 3:
            userData.setRemoveConfigurationAndSchema(answers[i]);
            break;

          case 4:
            userData.setRemoveBackups(answers[i]);
            break;

          case 5:
            userData.setRemoveLDIFs(answers[i]);
            break;

          case 6:
            if (answers[i])
            {
              userData.setExternalDbsToRemove(outsideDbs);
            }
            break;

          case 7:
            if (answers[i])
            {
              userData.setExternalLogsToRemove(outsideLogs);
            }
            break;
          }
        }
        if ((userData.getExternalDbsToRemove().size() == 0) &&
            (userData.getExternalLogsToRemove().size() == 0) &&
            !userData.getRemoveLibrariesAndTools() &&
            !userData.getRemoveDatabases() &&
            !userData.getRemoveConfigurationAndSchema() &&
            !userData.getRemoveBackups() &&
            !userData.getRemoveLDIFs() &&
            !userData.getRemoveLogs())
        {
          somethingSelected = false;
          println();
          println(ERR_CLI_UNINSTALL_NOTHING_TO_BE_UNINSTALLED.get());
        }
        else
        {
          somethingSelected = true;
        }
      }
    }

    return cancelled;
  }

  /**
   * Commodity method used to ask the user (when necessary) if the server must
   * be stopped or not.  It also prompts (if required) for authentication.
   * @param userData the UserData object to be updated with the
   * authentication of the user.
   * @return <CODE>true</CODE> if the user wants to continue with uninstall and
   * <CODE>false</CODE> otherwise.
   * @throws UserDataException if there is a problem with the data
   * provided by the user (in the particular case where we are on quiet
   * uninstall and some data is missing or not valid).
   */
  private boolean checkServerState(UninstallUserData userData)
  throws UserDataException
  {
    boolean cancelled = false;
    boolean interactive = parser.isInteractive();
    boolean forceOnError = parser.isForceOnError();
    UninstallData conf = null;
    try
    {
      conf = new UninstallData(Installation.getLocal());
    }
    catch (Throwable t)
    {
      LOG.log(Level.WARNING, "Error processing task: "+t, t);
      throw new UserDataException(Step.CONFIRM_UNINSTALL,
          Utils.getThrowableMsg(INFO_BUG_MSG.get(), t));
    }
    if (conf.isADS() && conf.isReplicationServer())
    {
      if (conf.isServerRunning())
      {
        if (interactive)
        {
          if (confirmToUpdateRemote())
          {
            println();
            cancelled = !askForAuthenticationIfNeeded(userData);
            if (cancelled)
            {
              /* Ask for confirmation to stop server */
              println();
              cancelled = !confirmToStopServer();
            }
            else
            {
              cancelled = !updateUserUninstallDataWithRemoteServers(userData);
              if (cancelled)
              {
                println();
                /* Ask for confirmation to stop server */
                cancelled = !confirmToStopServer();
              }
            }
          }
          else
          {
            println();
            /* Ask for confirmation to stop server */
            cancelled = !confirmToStopServer();
          }
        }
        else
        {
          cancelled =
            !updateUserUninstallDataWithRemoteServers(userData);
        }
      }
      else
      {
        if (interactive)
        {
          println();
          if (confirmToUpdateRemoteAndStart())
          {
            boolean startWorked = startServer(userData.isQuiet());
            // Ask for authentication if needed, etc.
            if (startWorked)
            {
              cancelled = !askForAuthenticationIfNeeded(userData);
              if (cancelled)
              {
                println();
                /* Ask for confirmation to stop server */
                cancelled = !confirmToStopServer();
              }
              else
              {
                cancelled = !updateUserUninstallDataWithRemoteServers(userData);
                if (cancelled)
                {
                  println();
                  /* Ask for confirmation to stop server */
                  cancelled = !confirmToStopServer();
                }
              }
            }
            else
            {
              userData.setStopServer(false);
              println();
              /* Ask for confirmation to delete files */
              cancelled = !confirmDeleteFiles();
            }
          }
          else
          {
            println();
            /* Ask for confirmation to delete files */
            cancelled = !confirmDeleteFiles();
          }
        }
        else
        {
          boolean startWorked = startServer(userData.isQuiet());
          // Ask for authentication if needed, etc.
          if (startWorked)
          {
            userData.setStopServer(true);
            cancelled =
              !updateUserUninstallDataWithRemoteServers(userData);
          }
          else
          {
            cancelled  = !forceOnError;
            userData.setStopServer(false);
          }
        }
      }
      if (!cancelled || parser.isForceOnError())
      {
        /* During all the confirmations, the server might be stopped. */
        userData.setStopServer(
            Installation.getLocal().getStatus().isServerRunning());
      }
    }
    else
    {
      if (conf.isServerRunning())
      {
        if (interactive)
        {
          println();
          /* Ask for confirmation to stop server */
          cancelled = !confirmToStopServer();
        }

        if (!cancelled)
        {
          /* During all the confirmations, the server might be stopped. */
          userData.setStopServer(
              Installation.getLocal().getStatus().isServerRunning());
        }
      }
      else
      {
        userData.setStopServer(false);
        if (interactive)
        {
          println();
          /* Ask for confirmation to delete files */
          cancelled = !confirmDeleteFiles();
        }
      }
    }
    return cancelled;
  }

  /**
   *  Ask for confirmation to stop server.
   *  @return <CODE>true</CODE> if the user wants to continue and stop the
   *  server.  <CODE>false</CODE> otherwise.
   */
  private boolean confirmToStopServer()
  {
    return confirm(INFO_CLI_UNINSTALL_CONFIRM_STOP.get(), true);
  }

  /**
   *  Ask for confirmation to delete files.
   *  @return <CODE>true</CODE> if the user wants to continue and delete the
   *  files.  <CODE>false</CODE> otherwise.
   */
  private boolean confirmDeleteFiles()
  {
    return confirm(INFO_CLI_UNINSTALL_CONFIRM_DELETE_FILES.get(), true);
  }

  /**
   *  Ask for confirmation to update configuration on remote servers.
   *  @return <CODE>true</CODE> if the user wants to continue and stop the
   *  server.  <CODE>false</CODE> otherwise.
   */
  private boolean confirmToUpdateRemote()
  {
    return confirm(INFO_CLI_UNINSTALL_CONFIRM_UPDATE_REMOTE.get(), true);
  }

  /**
   *  Ask for confirmation to update configuration on remote servers.
   *  @return <CODE>true</CODE> if the user wants to continue and stop the
   *  server.  <CODE>false</CODE> otherwise.
   */
  private boolean confirmToUpdateRemoteAndStart()
  {
    return confirm(
        INFO_CLI_UNINSTALL_CONFIRM_UPDATE_REMOTE_AND_START.get(), true);
  }

  /**
   *  Ask for confirmation to provide again authentication.
   *  @return <CODE>true</CODE> if the user wants to provide authentication
   *  againr.  <CODE>false</CODE> otherwise.
   */
  private boolean promptToProvideAuthenticationAgain()
  {
    return confirm(INFO_UNINSTALL_CONFIRM_PROVIDE_AUTHENTICATION_AGAIN.get(),
        true);
  }

  private boolean confirm(Message msg, boolean defaultValue)
  {
    boolean v = defaultValue;
    try
    {
      v = confirmAction(msg, defaultValue);
    }
    catch (CLIException ce)
    {
      LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
    }
    return v;
  }

  /**
   *  Ask for data required to update configuration on remote servers.  If
   *  all the data is provided and validated, we assume that the user wants
   *  to update the remote servers.
   *  @return <CODE>true</CODE> if the user wants to continue and update the
   *  remote servers.  <CODE>false</CODE> otherwise.
   */
  private boolean askForAuthenticationIfNeeded(UninstallUserData userData)
  {
    boolean accepted = true;
    String uid = userData.getAdminUID();
    String pwd = userData.getAdminPwd();
    boolean useSSL = userData.useSSL();
    boolean useStartTLS = userData.useStartTLS();

    boolean couldConnect = false;
    ConfigFromFile conf = new ConfigFromFile();
    conf.readConfiguration();

    boolean canUseSSL = conf.getLDAPSURL() != null;
    boolean canUseStartTLS = conf.getStartTLSURL() != null;

    while (!couldConnect && accepted)
    {

            // This is done because we do not need to ask the user about these
      // parameters.  If we force their presence the class
      // LDAPConnectionConsoleInteraction will not prompt the user for
      // them.
      SecureConnectionCliArgs secureArgsList = parser.getSecureArgsList();

      secureArgsList.hostNameArg.setPresent(true);
      secureArgsList.portArg.setPresent(true);
      secureArgsList.hostNameArg.clearValues();
      secureArgsList.hostNameArg.addValue(
          secureArgsList.hostNameArg.getDefaultValue());
      secureArgsList.portArg.clearValues();
      secureArgsList.portArg.addValue(
          secureArgsList.portArg.getDefaultValue());
      secureArgsList.bindDnArg.clearValues();
      secureArgsList.bindDnArg.addValue(ADSContext.getAdministratorDN(uid));
      secureArgsList.bindDnArg.setPresent(true);
      secureArgsList.bindPasswordArg.clearValues();
      secureArgsList.bindPasswordArg.addValue(pwd);
      secureArgsList.bindPasswordArg.setPresent(true);

      // We already know if SSL or StartTLS can be used.  If we cannot
      // use them we will not propose them in the connection parameters
      // and if none of them can be used we will just not ask for the
      // protocol to be used.
      if (!canUseSSL)
      {
        if (useSSL)
        {
          println();
          println(ERR_COULD_NOT_FIND_VALID_LDAPURL.get());
          println();
          secureArgsList.useSSLArg.setPresent(false);
        }
        else
        {
          secureArgsList.useSSLArg.setValueSetByProperty(true);
        }
      }
      if (!canUseStartTLS)
      {
        if (useStartTLS)
        {
          println();
          println(ERR_COULD_NOT_FIND_VALID_LDAPURL.get());
          println();
          secureArgsList.useStartTLSArg.setPresent(false);
        }
        secureArgsList.useStartTLSArg.setValueSetByProperty(true);
      }
      if (ci == null)
      {
        ci =
        new LDAPConnectionConsoleInteraction(this, parser.getSecureArgsList());
        ci.setDisplayLdapIfSecureParameters(true);
      }

      InitialLdapContext ctx = null;
      try
      {
        ci.run(canUseSSL, canUseStartTLS);
        useSSL = ci.useSSL();
        useStartTLS = ci.useStartTLS();
        userData.setAdminUID(ci.getAdministratorUID());
        userData.setAdminPwd(ci.getBindPassword());
        userData.setUseSSL(useSSL);
        userData.setUseStartTLS(useStartTLS);

        String ldapUrl = conf.getURL(
            ConnectionProtocolPolicy.getConnectionPolicy(
                useSSL, useStartTLS));
        try
        {
          URI uri = new URI(ldapUrl);
          int port = uri.getPort();
          secureArgsList.portArg.clearValues();
          secureArgsList.portArg.addValue(String.valueOf(port));
          ci.setPortNumber(port);
        }
        catch (Throwable t)
        {
          LOG.log(Level.SEVERE, "Error parsing url: "+ldapUrl);
        }
        LDAPManagementContextFactory factory =
          new LDAPManagementContextFactory();
        factory.getManagementContext(this, ci);
        updateTrustManager(userData, ci);
        ldapUrl = conf.getURL(
            ConnectionProtocolPolicy.getConnectionPolicy(ci.useSSL(),
                ci.useStartTLS()));
        userData.setLocalServerUrl(ldapUrl);
        couldConnect = true;
      }
      catch (ArgumentException e) {
        println(e.getMessageObject());
        println();
      }
      catch (ClientException e) {
        println(e.getMessageObject());
        println();
      }
      catch (ConfigException ce)
      {
        LOG.log(Level.WARNING,
            "Error retrieving a valid LDAP URL in conf file: "+ce, ce);
        println();
        println(ERR_COULD_NOT_FIND_VALID_LDAPURL.get());
        println();
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
            LOG.log(Level.INFO, "Error closing connection: "+t, t);
          }
        }
      }

      if (!couldConnect)
      {
        accepted = promptToProvideAuthenticationAgain();
        if (accepted)
        {
          uid = null;
          pwd = null;
        }
      }
    }

    if (accepted)
    {
      String referencedHostName = parser.getReferencedHostName();
      while (referencedHostName == null)
      {
        println();
        referencedHostName = askForReferencedHostName(userData.getHostName());
      }
      try
      {
        UninstallData d = new UninstallData(Installation.getLocal());
        userData.setReplicationServer(
            referencedHostName+":"+d.getReplicationServerPort());
        userData.setReferencedHostName(referencedHostName);
      }
      catch (Throwable t)
      {
        LOG.log(Level.SEVERE, "Could not create UninstallData: "+t, t);
      }
    }
    userData.setUpdateRemoteReplication(accepted);
    return accepted;
  }

  private String askForReferencedHostName(String defaultHostName)
  {
    String s = defaultHostName;
    try
    {
      s = readInput(INFO_UNINSTALL_CLI_REFERENCED_HOSTNAME_PROMPT.get(),
          defaultHostName);
    }
    catch (CLIException ce)
    {
      LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
    }
    return s;
  }

  private boolean startServer(boolean supressOutput)
  {
    boolean serverStarted = false;
    Application application = new Application()
    {
      /**
       * {@inheritDoc}
       */
      public String getInstallationPath()
      {
        return Installation.getLocal().getRootDirectory().getAbsolutePath();
      }
      /**
       * {@inheritDoc}
       */
      public ProgressStep getCurrentProgressStep()
      {
        return UninstallProgressStep.NOT_STARTED;
      }
      /**
       * {@inheritDoc}
       */
      public Integer getRatio(ProgressStep step)
      {
        return 0;
      }
      /**
       * {@inheritDoc}
       */
      public Message getSummary(ProgressStep step)
      {
        return null;
      }
      /**
       * {@inheritDoc}
       */
      public boolean isFinished()
      {
        return false;
      }
      /**
       * {@inheritDoc}
       */
      public boolean isCancellable()
      {
        return false;
      }
      /**
       * {@inheritDoc}
       */
      public void cancel()
      {
      }
      /**
       * {@inheritDoc}
       */
      public void run()
      {
      }
    };
    application.setProgressMessageFormatter(
        new PlainTextProgressMessageFormatter());
    if (!supressOutput)
    {
      application.addProgressUpdateListener(
          new ProgressUpdateListener() {
            public void progressUpdate(ProgressUpdateEvent ev) {
              System.out.print(ev.getNewLogs().toString());
              System.out.flush();
            }
          });
    }
    ServerController controller = new ServerController(application,
        Installation.getLocal());
    try
    {
      if (!supressOutput)
      {
        printlnProgress();
      }
      controller.startServer(supressOutput);
      if (!supressOutput)
      {
        printlnProgress();
      }
      serverStarted = Installation.getLocal().getStatus().isServerRunning();
    }
    catch (ApplicationException ae)
    {
      if (!supressOutput)
      {
        println(ae.getMessageObject());
      }
    }
    return serverStarted;
  }

  /**
   * Updates the contents of the UninstallUserData while trying to connect
   * to the remote servers.  It returns <CODE>true</CODE> if we could connect
   * to the remote servers and all the presented certificates were accepted and
   * <CODE>false</CODE> otherwise.
   * continue if
   * @param userData the user data to be updated.
   * @return <CODE>true</CODE> if we could connect
   * to the remote servers and all the presented certificates were accepted and
   * <CODE>false</CODE> otherwise.
   */
  private boolean updateUserUninstallDataWithRemoteServers(
      UninstallUserData userData)
  {
    boolean accepted = false;
    boolean interactive = parser.isInteractive();
    boolean forceOnError = parser.isForceOnError();

    boolean exceptionOccurred = true;

    InitialLdapContext ctx = null;
    try
    {
      ConfigFromFile conf = new ConfigFromFile();
      conf.readConfiguration();

      String host = "localhost";
      int port = 389;
      boolean useSSL = userData.useSSL();
      boolean useStartTLS = userData.useStartTLS();
      String adminUid = userData.getAdminUID();
      String pwd = userData.getAdminPwd();
      String dn = ADSContext.getAdministratorDN(adminUid);

      String ldapUrl = conf.getURL(
          ConnectionProtocolPolicy.getConnectionPolicy(useSSL, useStartTLS));
      try
      {
        URI uri = new URI(ldapUrl);
        host = uri.getHost();
        port = uri.getPort();
      }
      catch (Throwable t)
      {
        LOG.log(Level.SEVERE, "Error parsing url: "+ldapUrl);
      }
      ctx = createAdministrativeContext(host, port, useSSL, useStartTLS, dn,
          pwd, userData.getTrustManager());

      ADSContext adsContext = new ADSContext(ctx);
      TopologyCache cache = new TopologyCache(adsContext,
          userData.getTrustManager());
      cache.reloadTopology();

      accepted = handleTopologyCache(cache, userData);

      exceptionOccurred = false;
    }
    catch (ConfigException ce)
    {
      LOG.log(Level.WARNING,
          "Error retrieving a valid LDAP URL in conf file: "+ce, ce);
      println();
      println(ERR_COULD_NOT_FIND_VALID_LDAPURL.get());
    }
    catch (NamingException ne)
    {
      LOG.log(Level.WARNING, "Error connecting to server: "+ne, ne);
      if (Utils.isCertificateException(ne))
      {
        println();
        println(INFO_ERROR_READING_CONFIG_LDAP_CERTIFICATE.get(
            ne.getMessage()));
      }
      else
      {
        println();
        println(
            Utils.getThrowableMsg(INFO_ERROR_CONNECTING_TO_LOCAL.get(), ne));
      }
    } catch (TopologyCacheException te)
    {
      LOG.log(Level.WARNING, "Error connecting to server: "+te, te);
      println();
      println(Utils.getMessage(te));

    } catch (Throwable t)
    {
      LOG.log(Level.WARNING, "Error connecting to server: "+t, t);
      println();
      println(Utils.getThrowableMsg(INFO_BUG_MSG.get(), t));
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
          LOG.log(Level.INFO, "Error closing connection: "+t, t);
        }
      }
    }
    if (exceptionOccurred)
    {
      if (!interactive)
      {
        if (forceOnError)
        {
          println(ERR_UNINSTALL_ERROR_UPDATING_REMOTE_FORCE.get(
              parser.getSecureArgsList().adminUidArg.getLongIdentifier(),
              ToolConstants.OPTION_LONG_BINDPWD,
              ToolConstants.OPTION_LONG_BINDPWD_FILE));
        }
        else
        {
          println(
              ERR_UNINSTALL_ERROR_UPDATING_REMOTE_NO_FORCE.get(
                  parser.getSecureArgsList().adminUidArg.getLongIdentifier(),
                  ToolConstants.OPTION_LONG_BINDPWD,
                  ToolConstants.OPTION_LONG_BINDPWD_FILE,
                  parser.forceOnErrorArg.getLongIdentifier()));
        }
      }
      else
      {
        accepted = confirm(ERR_UNINSTALL_NOT_UPDATE_REMOTE_PROMPT.get(), false);
      }
    }
    userData.setUpdateRemoteReplication(accepted);
    return accepted;
  }

  /**
   * Method that interacts with the user depending on what errors where
   * encountered in the TopologyCache object.  This method assumes that the
   * TopologyCache has been reloaded.
   * Returns <CODE>true</CODE> if the user accepts all the problems encountered
   * and <CODE>false</CODE> otherwise.
   * @param userData the user data.
   */
  private boolean handleTopologyCache(TopologyCache cache,
      UninstallUserData userData)
  {
    boolean returnValue;
    boolean stopProcessing = false;
    boolean reloadTopologyCache = false;
    boolean interactive = parser.isInteractive();

    Set<TopologyCacheException> exceptions =
      new HashSet<TopologyCacheException>();
    /* Analyze if we had any exception while loading servers.  For the moment
     * only throw the exception found if the user did not provide the
     * Administrator DN and this caused a problem authenticating in one server
     * or if there is a certificate problem.
     */
    Set<ServerDescriptor> servers = cache.getServers();
    userData.setRemoteServers(servers);
    for (ServerDescriptor server : servers)
    {
      TopologyCacheException e = server.getLastException();
      if (e != null)
      {
        exceptions.add(e);
      }
    }
    Set<Message> exceptionMsgs = new LinkedHashSet<Message>();
    /* Check the exceptions and see if we throw them or not. */
    for (TopologyCacheException e : exceptions)
    {
      if (stopProcessing)
      {
        break;
      }
      switch (e.getType())
      {
      case NOT_GLOBAL_ADMINISTRATOR:
        println();
        println(INFO_NOT_GLOBAL_ADMINISTRATOR_PROVIDED.get());
        stopProcessing = true;
        break;
      case GENERIC_CREATING_CONNECTION:
        if ((e.getCause() != null) &&
            Utils.isCertificateException(e.getCause()))
        {
          if (interactive)
          {
            println();
            if (ci.promptForCertificateConfirmation(e.getCause(),
                e.getTrustManager(), e.getLdapUrl(), true, LOG))
            {
              stopProcessing = true;
              reloadTopologyCache = true;
              updateTrustManager(userData, ci);
            }
            else
            {
              stopProcessing = true;
            }
          }
          else
          {
            stopProcessing = true;
            println();
            println(INFO_ERROR_READING_CONFIG_LDAP_CERTIFICATE_SERVER.get(
                e.getHostPort(), e.getCause().getMessage()));
          }
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
    if (interactive)
    {
      if (!stopProcessing && (exceptionMsgs.size() > 0))
      {
        println();
        returnValue = confirm(
            ERR_UNINSTALL_READING_REGISTERED_SERVERS_CONFIRM_UPDATE_REMOTE.get(
                Utils.getMessageFromCollection(exceptionMsgs,
                  Constants.LINE_SEPARATOR).toString()), true);
      }
      else if (reloadTopologyCache)
      {
       returnValue = updateUserUninstallDataWithRemoteServers(userData);
      }
      else
      {
        returnValue = !stopProcessing;
      }
    }
    else
    {
      if (exceptionMsgs.size() > 0)
      {
        println();
        println(Utils.getMessageFromCollection(exceptionMsgs,
            Constants.LINE_SEPARATOR));
        returnValue = false;
      }
      else
      {
        returnValue = true;
      }
    }
    return returnValue;
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
    return parser.isInteractive();
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
  public boolean isQuiet() {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isScriptFriendly() {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isVerbose() {
    return true;
  }

  /**
   * Commodity method to update the user data with the trust manager in the
   * LDAPConnectionConsoleInteraction object.
   * @param userData the user data to be updated.
   * @param ci the LDAPConnectionConsoleInteraction object to be used to update
   * the user data object.
   */
   private void updateTrustManager(UninstallUserData userData,
       LDAPConnectionConsoleInteraction ci)
   {
     ApplicationTrustManager trust = null;
     TrustManager t = ci.getTrustManager();
     if (t != null)
     {
       if (t instanceof ApplicationTrustManager)
       {
         trust = (ApplicationTrustManager)t;
       }
       else
       {
         trust = new ApplicationTrustManager(ci.getKeyStore());
       }
     }
     userData.setTrustManager(trust);
   }
}
