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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */

package org.opends.guitools.uninstaller;

import org.opends.server.admin.client.cli.DsFrameworkCliReturnCode;
import org.opends.server.admin.client.cli.SecureConnectionCliArgs;

import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ServerDescriptor;
import org.opends.admin.ads.TopologyCache;
import org.opends.admin.ads.TopologyCacheException;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.guitools.controlpanel.datamodel.ConnectionProtocolPolicy;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
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
public class UninstallCliHelper extends ConsoleApplication {

  static private final Logger LOG =
          Logger.getLogger(UninstallCliHelper.class.getName());

  private UninstallerArgumentParser parser;

  private boolean forceNonInteractive;

  private LDAPConnectionConsoleInteraction ci = null;

  private ControlPanelInfo info;

  // This CLI is always using the administration connector with SSL
  private final boolean alwaysSSL = true;
  private boolean useSSL = true;
  private boolean useStartTLS = false;

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
   * line.  The code assumes that the arguments have already been parsed.
   * @param rawArguments the arguments provided in the command line.
   * @return the UserData object with what the user wants to uninstall
   * and null if the user cancels the uninstallation.
   * @throws UserDataException if there is an error with the data
   * in the arguments.
   * @throws ApplicationException if there is an error processing data in
   * non-interactive mode and an error must be thrown (not in force on error
   * mode).
   */
  public UninstallUserData createUserData(UninstallerArgumentParser args,
      String[] rawArguments)
  throws UserDataException, ApplicationException
  {
    parser = args;
    UninstallUserData userData = new UninstallUserData();
    try
    {
      boolean isInteractive;
      boolean isQuiet;
      boolean isVerbose;
      boolean isCancelled = false;

      /* Step 1: analyze the arguments.
       */

      isInteractive = args.isInteractive();

      isQuiet = args.isQuiet();

      isVerbose = args.isVerbose();

      userData.setQuiet(isQuiet);
      userData.setVerbose(isVerbose);
      userData.setForceOnError(args.isForceOnError());
      userData.setTrustManager(args.getTrustManager());

      /*
       * Step 2: check that the provided parameters are compatible.
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
      }
      try
      {
        UninstallData d = new UninstallData(Installation.getLocal());
        userData.setReplicationServer(
            referencedHostName+":"+d.getReplicationServerPort());
      }
      catch (Throwable t)
      {
        LOG.log(Level.SEVERE, "Could not create UninstallData: "+t, t);
        userData.setReplicationServer(
            referencedHostName+":8989");
      }
      info = ControlPanelInfo.getInstance();
      info.setTrustManager(userData.getTrustManager());
      info.regenerateDescriptor();
      info.setConnectionPolicy(ConnectionProtocolPolicy.USE_ADMIN);

      String adminConnectorUrl = info.getAdminConnectorURL();

      if (adminConnectorUrl == null)
      {
        LOG.log(Level.WARNING,
        "Error retrieving a valid LDAP URL in conf file.");
        if (!parser.isInteractive())
        {
          Message msg = ERR_COULD_NOT_FIND_VALID_LDAPURL.get();
          throw new ApplicationException(ReturnCode.APPLICATION_ERROR, msg,
              null);
        }
      }
      userData.setLocalServerUrl(adminConnectorUrl);
      userData.setReferencedHostName(referencedHostName);

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
        LOG.log(Level.INFO, "User cancelled uninstall.");
        userData = null;
      }

      if ((userData != null) && !args.isQuiet())
      {
        println();
      }
    }
    catch (Throwable t)
    {
      LOG.log(Level.WARNING, "Exception: "+t, t);
      if (t instanceof UserDataException)
      {
        throw (UserDataException)t;
      }
      else if (t instanceof ApplicationException)
      {
        throw (ApplicationException)t;
      }
      else
      {
        throw new IllegalStateException("Unexpected error: "+t, t);
      }
    }
    LOG.log(Level.INFO, "Successfully created user data");
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
      Set<String> outsideDbs, Set<String> outsideLogs) throws UserDataException
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

    builder.setMaxTries(CONFIRMATION_MAX_TRIES);

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
      LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
      throw new UserDataException(null, ce.getMessageObject(), ce);
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
      while (!somethingSelected && !cancelled)
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
        try
        {
          for (int i=0; i<msgs.length; i++)
          {
            boolean ignore = ((i == 6) && (outsideDbs.size() == 0)) ||
            ((i == 7) && (outsideLogs.size() == 0));
            if (!ignore)
            {
              answers[i] = askConfirmation(msgs[i], true, LOG);
            }
            else
            {
              answers[i] = false;
            }
          }
        }
        catch (CLIException ce)
        {
          throw new UserDataException(null, ce.getMessageObject(), ce);
        }

        if (!cancelled)
        {
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
            printErrorMessage(
                ERR_CLI_UNINSTALL_NOTHING_TO_BE_UNINSTALLED.get());
          }
          else
          {
            somethingSelected = true;
          }
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
   * provided by the user (in the particular case where we are on
   * non-interactive uninstall and some data is missing or not valid).
   * @throws ApplicationException if there is an error processing data in
   * non-interactive mode and an error must be thrown (not in force on error
   * mode).
   */
  private boolean checkServerState(UninstallUserData userData)
  throws UserDataException, ApplicationException
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
    LOG.log(Level.INFO, "interactive: "+interactive);
    LOG.log(Level.INFO, "forceOnError: "+forceOnError);
    LOG.log(Level.INFO, "conf.isADS(): "+conf.isADS());
    LOG.log(Level.INFO, "conf.isReplicationServer(): "+
        conf.isReplicationServer());
    LOG.log(Level.INFO, "conf.isServerRunning(): "+conf.isServerRunning());
    if (conf.isADS() && conf.isReplicationServer())
    {
      if (conf.isServerRunning())
      {
        if (interactive)
        {
          try
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
          catch (CLIException ce)
          {
            throw new UserDataException(null, ce.getMessageObject(), ce);
          }
        }
        else
        {
          boolean errorWithRemote =
            !updateUserUninstallDataWithRemoteServers(userData);
          cancelled = errorWithRemote && !parser.isForceOnError();
          LOG.log(Level.INFO, "Non interactive mode.  errorWithRemote: "+
              errorWithRemote);
        }
      }
      else
      {
        if (interactive)
        {
          println();
          try
          {
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
                  cancelled =
                    !updateUserUninstallDataWithRemoteServers(userData);
                  if (cancelled)
                  {
                    println();
                    /* Ask for confirmation to stop server */
                    cancelled = !confirmToStopServer();
                  }
                }
                userData.setStopServer(true);
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
          catch (CLIException ce)
          {
            throw new UserDataException(null, ce.getMessageObject(), ce);
          }
        }
        else
        {
          boolean startWorked = startServer(userData.isQuiet());
          // Ask for authentication if needed, etc.
          if (startWorked)
          {
            userData.setStopServer(true);
            boolean errorWithRemote =
              !updateUserUninstallDataWithRemoteServers(userData);
            cancelled = errorWithRemote && !parser.isForceOnError();
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
        LOG.log(Level.INFO, "Must stop the server after confirmations? "+
            userData.getStopServer());
      }
    }
    else
    {
      if (conf.isServerRunning())
      {
        try
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
            LOG.log(Level.INFO, "Must stop the server after confirmations? "+
                userData.getStopServer());
          }
        }
        catch (CLIException ce)
        {
          throw new UserDataException(null, ce.getMessageObject(), ce);
        }
      }
      else
      {
        userData.setStopServer(false);
        if (interactive)
        {
          println();
          /* Ask for confirmation to delete files */
          try
          {
            cancelled = !confirmDeleteFiles();
          }
          catch (CLIException ce)
          {
            throw new UserDataException(null, ce.getMessageObject(), ce);
          }
        }
      }
    }
    LOG.log(Level.INFO, "cancelled: "+cancelled);
    return cancelled;
  }

  /**
   *  Ask for confirmation to stop server.
   *  @return <CODE>true</CODE> if the user wants to continue and stop the
   *  server.  <CODE>false</CODE> otherwise.
   *  @throws CLIException if the user reached the confirmation limit.
   */
  private boolean confirmToStopServer() throws CLIException
  {
    return askConfirmation(INFO_CLI_UNINSTALL_CONFIRM_STOP.get(), true, LOG);
  }

  /**
   *  Ask for confirmation to delete files.
   *  @return <CODE>true</CODE> if the user wants to continue and delete the
   *  files.  <CODE>false</CODE> otherwise.
   *  @throws CLIException if the user reached the confirmation limit.
   */
  private boolean confirmDeleteFiles() throws CLIException
  {
    return askConfirmation(INFO_CLI_UNINSTALL_CONFIRM_DELETE_FILES.get(), true,
        LOG);
  }

  /**
   *  Ask for confirmation to update configuration on remote servers.
   *  @return <CODE>true</CODE> if the user wants to continue and stop the
   *  server.  <CODE>false</CODE> otherwise.
   *  @throws CLIException if the user reached the confirmation limit.
   */
  private boolean confirmToUpdateRemote() throws CLIException
  {
    return askConfirmation(INFO_CLI_UNINSTALL_CONFIRM_UPDATE_REMOTE.get(), true,
        LOG);
  }

  /**
   *  Ask for confirmation to update configuration on remote servers.
   *  @return <CODE>true</CODE> if the user wants to continue and stop the
   *  server.  <CODE>false</CODE> otherwise.
   *  @throws CLIException if the user reached the confirmation limit.
   */
  private boolean confirmToUpdateRemoteAndStart() throws CLIException
  {
    return askConfirmation(
        INFO_CLI_UNINSTALL_CONFIRM_UPDATE_REMOTE_AND_START.get(), true, LOG);
  }

  /**
   *  Ask for confirmation to provide again authentication.
   *  @return <CODE>true</CODE> if the user wants to provide authentication
   *  again.  <CODE>false</CODE> otherwise.
   *  @throws CLIException if the user reached the confirmation limit.
   */
  private boolean promptToProvideAuthenticationAgain() throws CLIException
  {
    return askConfirmation(
        INFO_UNINSTALL_CONFIRM_PROVIDE_AUTHENTICATION_AGAIN.get(), true, LOG);
  }

  /**
   *  Ask for data required to update configuration on remote servers.  If
   *  all the data is provided and validated, we assume that the user wants
   *  to update the remote servers.
   *  @return <CODE>true</CODE> if the user wants to continue and update the
   *  remote servers.  <CODE>false</CODE> otherwise.
   *  @throws UserDataException if there is a problem with the information
   *  provided by the user.
   *  @throws ApplicationException if there is an error processing data.
   */
  private boolean askForAuthenticationIfNeeded(UninstallUserData userData)
  throws UserDataException, ApplicationException
  {
    boolean accepted = true;
    String uid = userData.getAdminUID();
    String pwd = userData.getAdminPwd();

    boolean couldConnect = false;

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
      if (uid != null)
      {
        secureArgsList.bindDnArg.addValue(ADSContext.getAdministratorDN(uid));
        secureArgsList.bindDnArg.setPresent(true);
      }
      else
      {
        secureArgsList.bindDnArg.setPresent(false);
      }
      secureArgsList.bindPasswordArg.clearValues();
      if (pwd != null)
      {
        secureArgsList.bindPasswordArg.addValue(pwd);
        secureArgsList.bindPasswordArg.setPresent(true);
      }
      else
      {
        secureArgsList.bindPasswordArg.setPresent(false);
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
        ci.run(true, false);
        userData.setAdminUID(ci.getAdministratorUID());
        userData.setAdminPwd(ci.getBindPassword());

        info.setConnectionPolicy(ConnectionProtocolPolicy.USE_ADMIN);

        String adminConnectorUrl = info.getAdminConnectorURL();
        if (adminConnectorUrl == null)
        {
          LOG.log(Level.WARNING,
         "Error retrieving a valid Administration Connector URL in conf file.");
          Message msg = ERR_COULD_NOT_FIND_VALID_LDAPURL.get();
            throw new ApplicationException(ReturnCode.APPLICATION_ERROR, msg,
                null);
        }
        try
        {
          URI uri = new URI(adminConnectorUrl);
          int port = uri.getPort();
          secureArgsList.portArg.clearValues();
          secureArgsList.portArg.addValue(String.valueOf(port));
          ci.setPortNumber(port);
        }
        catch (Throwable t)
        {
          LOG.log(Level.SEVERE, "Error parsing url: "+adminConnectorUrl);
        }
        LDAPManagementContextFactory factory =
          new LDAPManagementContextFactory(alwaysSSL);
        factory.getManagementContext(this, ci);
        updateTrustManager(userData, ci);

        info.setConnectionPolicy(ConnectionProtocolPolicy.USE_ADMIN);

        adminConnectorUrl = info.getAdminConnectorURL();

        if (adminConnectorUrl == null)
        {
          LOG.log(Level.WARNING,
         "Error retrieving a valid Administration Connector URL in conf file.");
          Message msg = ERR_COULD_NOT_FIND_VALID_LDAPURL.get();
          throw new ApplicationException(ReturnCode.APPLICATION_ERROR, msg,
              null);
        }

        userData.setLocalServerUrl(adminConnectorUrl);
        couldConnect = true;
      }
      catch (ArgumentException e) {
        printErrorMessage(e.getMessageObject());
        println();
      }
      catch (ClientException e) {
        printErrorMessage(e.getMessageObject());
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
        try
        {
          accepted = promptToProvideAuthenticationAgain();
          if (accepted)
          {
            uid = null;
            pwd = null;
          }
        }
        catch (CLIException ce)
        {
          throw new UserDataException(null, ce.getMessageObject(), ce);
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
    LOG.log(Level.INFO, "startServer, supressOutput: "+supressOutput);
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
      public String getInstancePath()
      {
        String installPath =  getInstallationPath();

        // look for <installPath>/lib/resource.loc
        String instancePathFileName = installPath + File.separator + "lib"
        + File.separator + "resource.loc";
        File f = new File(instancePathFileName);

        if (! f.exists())
        {
          return installPath;
        }

        BufferedReader reader;
        try
        {
          reader = new BufferedReader(new FileReader(instancePathFileName));
        }
        catch (Exception e)
        {
          return installPath;
        }


        // Read the first line and close the file.
        String line;
        try
        {
          line = reader.readLine();
          return new File(line).getAbsolutePath();
        }
        catch (Exception e)
        {
          return installPath;
        }
        finally
        {
          try
          {
            reader.close();
          } catch (Exception e) {}
        }
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
      LOG.log(Level.INFO, "server started successfully. serverStarted: "+
          serverStarted);
    }
    catch (ApplicationException ae)
    {
      LOG.log(Level.WARNING, "ApplicationException: "+ae, ae);
      if (!supressOutput)
      {
        printErrorMessage(ae.getMessageObject());
      }
    }
    catch (Throwable t)
    {
      LOG.log(Level.SEVERE, "Unexpected error: "+t, t);
      throw new IllegalStateException("Unexpected error: "+t, t);
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
   * @throws UserDataException if were are not in interactive mode and not in
   * force on error mode and the operation must be stopped.
   * @throws ApplicationException if there is an error processing data in
   * non-interactive mode and an error must be thrown (not in force on error
   * mode).
   */
  private boolean updateUserUninstallDataWithRemoteServers(
      UninstallUserData userData) throws UserDataException, ApplicationException
  {
    boolean accepted = false;
    boolean interactive = parser.isInteractive();
    boolean forceOnError = parser.isForceOnError();

    boolean exceptionOccurred = true;

    Message exceptionMsg = null;

    LOG.log(Level.INFO, "Updating user data with remote servers.");

    InitialLdapContext ctx = null;
    try
    {
      info.setTrustManager(userData.getTrustManager());

      String host = "localhost";
      int port = 389;
      String adminUid = userData.getAdminUID();
      String pwd = userData.getAdminPwd();
      String dn = ADSContext.getAdministratorDN(adminUid);

      info.setConnectionPolicy(ConnectionProtocolPolicy.USE_ADMIN);
      String adminConnectorUrl = info.getAdminConnectorURL();
      try
      {
        URI uri = new URI(adminConnectorUrl);
        host = uri.getHost();
        port = uri.getPort();
      }
      catch (Throwable t)
      {
        LOG.log(Level.SEVERE, "Error parsing url: "+adminConnectorUrl);
      }
      ctx = createAdministrativeContext(host, port, useSSL, useStartTLS, dn,
          pwd, userData.getTrustManager());

      ADSContext adsContext = new ADSContext(ctx);
      if (interactive && (userData.getTrustManager() == null))
      {
        // This is required when the user did  connect to the server using SSL
        // or Start TLS in interactive mode.  In this case
        // LDAPConnectionInteraction.run does not initialize the keystore and
        // the trust manager is null.
        forceTrustManagerInitialization();
        updateTrustManager(userData, ci);
      }
      LOG.log(Level.INFO, "Reloading topology");
      TopologyCache cache = new TopologyCache(adsContext,
          userData.getTrustManager());
      cache.getFilter().setSearchMonitoringInformation(false);
      cache.reloadTopology();

      accepted = handleTopologyCache(cache, userData);

      exceptionOccurred = false;
    }
    catch (NamingException ne)
    {
      LOG.log(Level.WARNING, "Error connecting to server: "+ne, ne);
      if (Utils.isCertificateException(ne))
      {
        String details = ne.getMessage() != null ?
            ne.getMessage() : ne.toString();
        exceptionMsg =
          INFO_ERROR_READING_CONFIG_LDAP_CERTIFICATE.get(details);
      }
      else
      {
        exceptionMsg = Utils.getThrowableMsg(
            INFO_ERROR_CONNECTING_TO_LOCAL.get(), ne);
      }
    } catch (TopologyCacheException te)
    {
      LOG.log(Level.WARNING, "Error connecting to server: "+te, te);
      exceptionMsg = Utils.getMessage(te);

    } catch (ApplicationException ae)
    {
      throw ae;

    } catch (Throwable t)
    {
      LOG.log(Level.WARNING, "Error connecting to server: "+t, t);
      exceptionMsg = Utils.getThrowableMsg(INFO_BUG_MSG.get(), t);
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
          println();
          printErrorMessage(ERR_UNINSTALL_ERROR_UPDATING_REMOTE_FORCE.get(
              "--"+parser.getSecureArgsList().adminUidArg.getLongIdentifier(),
              "--"+ToolConstants.OPTION_LONG_BINDPWD,
              "--"+ToolConstants.OPTION_LONG_BINDPWD_FILE,
              String.valueOf(exceptionMsg)));
        }
        else
        {
          println();
          throw new UserDataException(null,
              ERR_UNINSTALL_ERROR_UPDATING_REMOTE_NO_FORCE.get(
                  "--"+
                  parser.getSecureArgsList().adminUidArg.getLongIdentifier(),
                  "--"+ToolConstants.OPTION_LONG_BINDPWD,
                  "--"+ToolConstants.OPTION_LONG_BINDPWD_FILE,
                  "--"+parser.forceOnErrorArg.getLongIdentifier(),
                  String.valueOf(exceptionMsg)));
        }
      }
      else
      {
        try
        {
          accepted = askConfirmation(
              ERR_UNINSTALL_NOT_UPDATE_REMOTE_PROMPT.get(),
              false, LOG);
        }
        catch (CLIException ce)
        {
          throw new UserDataException(null, ce.getMessageObject(), ce);
        }
      }
    }
    userData.setUpdateRemoteReplication(accepted);
    LOG.log(Level.INFO, "accepted: "+accepted);
    return accepted;
  }

  /**
   * Method that interacts with the user depending on what errors where
   * encountered in the TopologyCache object.  This method assumes that the
   * TopologyCache has been reloaded.
   * Returns <CODE>true</CODE> if the user accepts all the problems encountered
   * and <CODE>false</CODE> otherwise.
   * @param userData the user data.
   * @throws UserDataException if there is an error with the information
   * provided by the user when we are in non-interactive mode.
   * @throws ApplicationException if there is an error processing data in
   * non-interactive mode and an error must be thrown (not in force on error
   * mode).
   */
  private boolean handleTopologyCache(TopologyCache cache,
      UninstallUserData userData) throws UserDataException, ApplicationException
  {
    boolean returnValue;
    boolean stopProcessing = false;
    boolean reloadTopologyCache = false;
    boolean interactive = parser.isInteractive();

    LOG.log(Level.INFO, "Handle topology cache.");

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
      LOG.log(Level.INFO, "Analyzing exception: "+e, e);
      if (stopProcessing)
      {
        break;
      }
      switch (e.getType())
      {
      case NOT_GLOBAL_ADMINISTRATOR:
        println();
        printErrorMessage(INFO_NOT_GLOBAL_ADMINISTRATOR_PROVIDED.get());
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
            exceptionMsgs.add(
                INFO_ERROR_READING_CONFIG_LDAP_CERTIFICATE_SERVER.get(
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
        try
        {
          returnValue = askConfirmation(
            ERR_UNINSTALL_READING_REGISTERED_SERVERS_CONFIRM_UPDATE_REMOTE.get(
                Utils.getMessageFromCollection(exceptionMsgs,
                  Constants.LINE_SEPARATOR).toString()), true, LOG);
        }
        catch (CLIException ce)
        {
          throw new UserDataException(null, ce.getMessageObject(), ce);
        }
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
      LOG.log(Level.INFO, "exceptionMsgs: "+exceptionMsgs);
      if (exceptionMsgs.size() > 0)
      {
        if (parser.isForceOnError())
        {
          Message msg = Utils.getMessageFromCollection(exceptionMsgs,
              Constants.LINE_SEPARATOR);
          println();
          printErrorMessage(msg);
          returnValue = false;
        }
        else
        {
          Message msg =
            ERR_UNINSTALL_ERROR_UPDATING_REMOTE_NO_FORCE.get(
              "--"+
              parser.getSecureArgsList().adminUidArg.getLongIdentifier(),
              "--"+ToolConstants.OPTION_LONG_BINDPWD,
              "--"+ToolConstants.OPTION_LONG_BINDPWD_FILE,
              "--"+parser.forceOnErrorArg.getLongIdentifier(),
              Utils.getMessageFromCollection(exceptionMsgs,
                  Constants.LINE_SEPARATOR).toString());
          throw new ApplicationException(ReturnCode.APPLICATION_ERROR, msg,
              null);
        }
      }
      else
      {
        returnValue = true;
      }
    }
    LOG.log(Level.INFO, "Return value: "+returnValue);
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
    if (forceNonInteractive)
    {
      return false;
    }
    else
    {
      return parser.isInteractive();
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

   private void printErrorMessage(Message msg)
   {
     super.println(msg);
     LOG.log(Level.WARNING, msg.toString());
   }
}
