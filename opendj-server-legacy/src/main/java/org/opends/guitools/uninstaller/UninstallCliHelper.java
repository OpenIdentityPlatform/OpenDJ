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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.uninstaller;

import static org.forgerock.util.Utils.*;
import static org.opends.admin.ads.util.PreferredConnection.Type.*;
import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.QuickSetupMessages.*;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.Utils.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.naming.NamingException;
import javax.net.ssl.TrustManager;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ServerDescriptor;
import org.opends.admin.ads.TopologyCache;
import org.opends.admin.ads.TopologyCacheException;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.admin.ads.util.PreferredConnection.Type;
import org.opends.guitools.controlpanel.datamodel.ConnectionProtocolPolicy;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.quicksetup.Application;
import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.Configuration;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.ProgressStep;
import org.opends.quicksetup.Step;
import org.opends.quicksetup.UserDataException;
import org.opends.quicksetup.event.ProgressUpdateEvent;
import org.opends.quicksetup.event.ProgressUpdateListener;
import org.opends.quicksetup.util.PlainTextProgressMessageFormatter;
import org.opends.quicksetup.util.ServerController;
import org.opends.quicksetup.util.Utils;
import org.opends.server.admin.client.cli.SecureConnectionCliArgs;
import org.opends.server.types.HostPort;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.cli.LDAPConnectionConsoleInteraction;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.Menu;
import com.forgerock.opendj.cli.MenuBuilder;
import com.forgerock.opendj.cli.MenuResult;
import com.forgerock.opendj.cli.ReturnCode;
import com.forgerock.opendj.cli.StringArgument;

/**
 * The class used to provide some CLI interface in the uninstall.
 *
 * This class basically is in charge of parsing the data provided by the user
 * in the command line and displaying messages asking the user for information.
 *
 * Once the user has provided all the required information it calls Uninstaller
 * and launches it.
 */
public class UninstallCliHelper extends ConsoleApplication {
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private UninstallerArgumentParser parser;
  private LDAPConnectionConsoleInteraction ci;
  private ControlPanelInfo info;

  private boolean forceNonInteractive;
  private Type connectionType = LDAPS;

  /** Default constructor. */
  public UninstallCliHelper()
  {
    // Nothing to do.
  }

  /**
   * Creates a UserData based in the arguments provided. It asks user for
   * additional information if what is provided in the arguments is not enough.
   *
   * @param args
   *          the ArgumentParser with the allowed arguments of the command line.
   *          The code assumes that the arguments have already been parsed.
   * @return the UserData object with what the user wants to uninstall and null
   *         if the user cancels the uninstallation.
   * @throws UserDataException
   *           if there is an error with the data in the arguments.
   * @throws ClientException
   *           If there is an error processing data in non-interactive mode and
   *           an error must be thrown (not in force on error mode).
   */
  public UninstallUserData createUserData(UninstallerArgumentParser args)
      throws UserDataException, ClientException
  {
    parser = args;
    UninstallUserData userData = new UninstallUserData();
    try
    {
      boolean isInteractive;
      boolean isQuiet;
      boolean isVerbose;
      boolean isCanceled = false;

      /* Step 1: analyze the arguments. */

      isInteractive = args.isInteractive();

      isQuiet = args.isQuiet();

      isVerbose = args.isVerbose();

      userData.setQuiet(isQuiet);
      userData.setVerbose(isVerbose);
      userData.setForceOnError(args.isForceOnError());
      userData.setTrustManager(args.getTrustManager());

      userData.setConnectTimeout(getConnectTimeout());

      /* Step 2: check that the provided parameters are compatible. */
      LocalizableMessageBuilder buf = new LocalizableMessageBuilder();
      int v = args.validateGlobalOptions(buf);
      if (v != ReturnCode.SUCCESS.get())
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
        logger.info(LocalizableMessage.raw("error determining outside databases", ioe));
      }

      try {
        outsideLogs = config.getOutsideLogs();
      } catch (IOException ioe) {
        outsideLogs = Collections.emptySet();
        logger.info(LocalizableMessage.raw("error determining outside logs", ioe));
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
      else if (!isInteractive)
      {
        throw new UserDataException(null,
           ERR_CLI_UNINSTALL_NOTHING_TO_BE_UNINSTALLED_NON_INTERACTIVE.get());
      }
      else
      {
        isCanceled = askWhatToDelete(userData, outsideDbs, outsideLogs);
      }
      String adminUid = args.getAdministratorUID();
      if (adminUid == null && !args.isInteractive())
      {
        adminUid = args.getDefaultAdministratorUID();
      }
      userData.setAdminUID(adminUid);
      userData.setAdminPwd(args.getBindPassword());
      String referencedHostName = args.getReferencedHostName();
      if (referencedHostName == null && !args.isInteractive())
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
        logger.error(LocalizableMessage.raw("Could not create UninstallData: "+t, t));
        userData.setReplicationServer(
            referencedHostName+":8989");
      }
      info = ControlPanelInfo.getInstance();
      info.setTrustManager(userData.getTrustManager());
      info.setConnectTimeout(getConnectTimeout());
      info.regenerateDescriptor();
      info.setConnectionPolicy(ConnectionProtocolPolicy.USE_ADMIN);
      String adminConnectorUrl = info.getAdminConnectorURL();
      if (adminConnectorUrl == null)
      {
        logger.warn(LocalizableMessage.raw(
        "Error retrieving a valid LDAP URL in conf file."));
        if (!parser.isInteractive())
        {
          LocalizableMessage msg = ERR_COULD_NOT_FIND_VALID_LDAPURL.get();
          throw new ClientException(ReturnCode.APPLICATION_ERROR, msg);
        }
      }
      userData.setLocalServerUrl(adminConnectorUrl);
      userData.setReferencedHostName(referencedHostName);

      /*
       * Step 4: check if server is running.  Depending if it is running and the
       * OS we are running, ask for authentication information.
       */
      if (!isCanceled)
      {
        isCanceled = checkServerState(userData);
        if (isCanceled && !userData.isForceOnError())
        {
          logger.info(LocalizableMessage.raw("User cancelled uninstall."));
          userData = null;
        }
      }

      if (userData != null && !args.isQuiet())
      {
        println();
      }
    }
    catch (Throwable t)
    {
      logger.warn(LocalizableMessage.raw("Exception: "+t, t));
      if (t instanceof UserDataException)
      {
        throw (UserDataException)t;
      }
      else if (t instanceof ClientException)
      {
        throw (ClientException)t;
      }
      else
      {
        throw new IllegalStateException("Unexpected error: "+t, t);
      }
    }
    logger.info(LocalizableMessage.raw("Successfully created user data"));
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
    LocalizableMessage[] msgs = new LocalizableMessage[] {
        INFO_CLI_UNINSTALL_REMOVE_ALL.get(),
        INFO_CLI_UNINSTALL_SPECIFY_WHAT_REMOVE.get()
      };

    MenuBuilder<Integer> builder = new MenuBuilder<>(this);
    builder.setPrompt(INFO_CLI_UNINSTALL_WHAT_TO_DELETE.get());

    for (int i=0; i<indexes.length; i++)
    {
      builder.addNumberedOption(msgs[i], MenuResult.success(indexes[i]));
    }

    builder.addQuitOption();

    builder.setDefault(LocalizableMessage.raw(String.valueOf(REMOVE_ALL)),
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
    catch (ClientException ce)
    {
      logger.warn(LocalizableMessage.raw("Error reading input: "+ce, ce));
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
        msgs = new LocalizableMessage [] {
                INFO_CLI_UNINSTALL_CONFIRM_LIBRARIES_BINARIES.get(),
                INFO_CLI_UNINSTALL_CONFIRM_DATABASES.get(),
                INFO_CLI_UNINSTALL_CONFIRM_LOGS.get(),
                INFO_CLI_UNINSTALL_CONFIRM_CONFIGURATION_SCHEMA.get(),
                INFO_CLI_UNINSTALL_CONFIRM_BACKUPS.get(),
                INFO_CLI_UNINSTALL_CONFIRM_LDIFS.get(),
                INFO_CLI_UNINSTALL_CONFIRM_OUTSIDEDBS.get(
                        joinAsString(Constants.LINE_SEPARATOR, outsideDbs)),
                INFO_CLI_UNINSTALL_CONFIRM_OUTSIDELOGS.get(
                        joinAsString(Constants.LINE_SEPARATOR, outsideLogs)
                )
        };

        boolean[] answers = new boolean[msgs.length];
        try
        {
          for (int i=0; i<msgs.length; i++)
          {
            boolean ignore = (i == 6 && outsideDbs.isEmpty())
                || (i == 7 && outsideLogs.isEmpty());
            if (!ignore)
            {
              answers[i] = askConfirmation(msgs[i], true, logger);
            }
            else
            {
              answers[i] = false;
            }
          }
        }
        catch (ClientException ce)
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
          if (userData.getExternalDbsToRemove().isEmpty() &&
              userData.getExternalLogsToRemove().isEmpty() &&
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
   * be stopped or not. It also prompts (if required) for authentication.
   *
   * @param userData
   *          the UserData object to be updated with the authentication of the
   *          user.
   * @return <CODE>true</CODE> if the user wants to continue with uninstall and
   *         <CODE>false</CODE> otherwise.
   * @throws UserDataException
   *           if there is a problem with the data provided by the user (in the
   *           particular case where we are on non-interactive uninstall and
   *           some data is missing or not valid).
   * @throws ClientException
   *           If there is an error processing data in non-interactive mode and
   *           an error must be thrown (not in force on error mode).
   */
  private boolean checkServerState(UninstallUserData userData)
  throws UserDataException, ClientException
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
      logger.warn(LocalizableMessage.raw("Error processing task: "+t, t));
      throw new UserDataException(Step.CONFIRM_UNINSTALL,
          getThrowableMsg(INFO_BUG_MSG.get(), t));
    }
    logger.info(LocalizableMessage.raw("interactive: "+interactive));
    logger.info(LocalizableMessage.raw("forceOnError: "+forceOnError));
    logger.info(LocalizableMessage.raw("conf.isADS(): "+conf.isADS()));
    logger.info(LocalizableMessage.raw("conf.isReplicationServer(): "+
        conf.isReplicationServer()));
    logger.info(LocalizableMessage.raw("conf.isServerRunning(): "+conf.isServerRunning()));
    if (conf.isADS() && conf.isReplicationServer())
    {
      if (conf.isServerRunning())
      {
        if (interactive)
        {
          try
          {
            println();
            if (confirmToUpdateRemote())
            {
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
              /* Ask for confirmation to stop server */
              cancelled = !confirmToStopServer();
            }
          }
          catch (ClientException ce)
          {
            throw new UserDataException(null, ce.getMessageObject(), ce);
          }
        }
        else
        {
          boolean errorWithRemote =
            !updateUserUninstallDataWithRemoteServers(userData);
          cancelled = errorWithRemote && !parser.isForceOnError();
          logger.info(LocalizableMessage.raw("Non interactive mode.  errorWithRemote: "+
              errorWithRemote));
        }
      }
      else if (interactive)
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
        catch (ClientException ce)
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
      if (!cancelled || parser.isForceOnError())
      {
        /* During all the confirmations, the server might be stopped. */
        userData.setStopServer(
            Installation.getLocal().getStatus().isServerRunning());
        logger.info(LocalizableMessage.raw("Must stop the server after confirmations? "+
            userData.getStopServer()));
      }
    }
    else if (conf.isServerRunning())
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
          logger.info(LocalizableMessage.raw("Must stop the server after confirmations? "+
              userData.getStopServer()));
        }
      }
      catch (ClientException ce)
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
        catch (ClientException ce)
        {
          throw new UserDataException(null, ce.getMessageObject(), ce);
        }
      }
    }
    logger.info(LocalizableMessage.raw("cancelled: "+cancelled));
    return cancelled;
  }

  /**
   *  Ask for confirmation to stop server.
   *  @return <CODE>true</CODE> if the user wants to continue and stop the
   *  server.  <CODE>false</CODE> otherwise.
   *  @throws ClientException if the user reached the confirmation limit.
   */
  private boolean confirmToStopServer() throws ClientException
  {
    return askConfirmation(INFO_CLI_UNINSTALL_CONFIRM_STOP.get(), true, logger);
  }

  /**
   *  Ask for confirmation to delete files.
   *  @return <CODE>true</CODE> if the user wants to continue and delete the
   *  files.  <CODE>false</CODE> otherwise.
   *  @throws ClientException if the user reached the confirmation limit.
   */
  private boolean confirmDeleteFiles() throws ClientException
  {
    return askConfirmation(INFO_CLI_UNINSTALL_CONFIRM_DELETE_FILES.get(), true,
        logger);
  }

  /**
   *  Ask for confirmation to update configuration on remote servers.
   *  @return <CODE>true</CODE> if the user wants to continue and stop the
   *  server.  <CODE>false</CODE> otherwise.
   *  @throws ClientException if the user reached the confirmation limit.
   */
  private boolean confirmToUpdateRemote() throws ClientException
  {
    return askConfirmation(INFO_CLI_UNINSTALL_CONFIRM_UPDATE_REMOTE.get(), true,
        logger);
  }

  /**
   *  Ask for confirmation to update configuration on remote servers.
   *  @return <CODE>true</CODE> if the user wants to continue and stop the
   *  server.  <CODE>false</CODE> otherwise.
   *  @throws ClientException if the user reached the confirmation limit.
   */
  private boolean confirmToUpdateRemoteAndStart() throws ClientException
  {
    return askConfirmation(
        INFO_CLI_UNINSTALL_CONFIRM_UPDATE_REMOTE_AND_START.get(), true, logger);
  }

  /**
   *  Ask for confirmation to provide again authentication.
   *  @return <CODE>true</CODE> if the user wants to provide authentication
   *  again.  <CODE>false</CODE> otherwise.
   *  @throws ClientException if the user reached the confirmation limit.
   */
  private boolean promptToProvideAuthenticationAgain() throws ClientException
  {
    return askConfirmation(
        INFO_UNINSTALL_CONFIRM_PROVIDE_AUTHENTICATION_AGAIN.get(), true, logger);
  }

  /**
   * Ask for data required to update configuration on remote servers. If all the
   * data is provided and validated, we assume that the user wants to update the
   * remote servers.
   *
   * @return <CODE>true</CODE> if the user wants to continue and update the
   *         remote servers. <CODE>false</CODE> otherwise.
   * @throws UserDataException
   *           if there is a problem with the information provided by the user.
   * @throws ClientException
   *           If there is an error processing data.
   */
  private boolean askForAuthenticationIfNeeded(UninstallUserData userData)
  throws UserDataException, ClientException
  {
    boolean accepted = true;
    String uid = userData.getAdminUID();
    String pwd = userData.getAdminPwd();

    boolean couldConnect = false;

    while (!couldConnect && accepted)
    {
      // This is done because we do not need to ask the user about these parameters.
      // If we force their presence the class LDAPConnectionConsoleInteraction will not prompt the user for them.
      SecureConnectionCliArgs secureArgsList = parser.getSecureArgsList();

      StringArgument hostNameArg = secureArgsList.getHostNameArg();
      hostNameArg.setPresent(true);
      hostNameArg.clearValues();
      hostNameArg.addValue(hostNameArg.getDefaultValue());

      IntegerArgument portArg = secureArgsList.getPortArg();
      portArg.setPresent(true);
      portArg.clearValues();
      portArg.addValue(portArg.getDefaultValue());

      StringArgument bindDnArg = secureArgsList.getBindDnArg();
      bindDnArg.clearValues();
      if (uid != null)
      {
        bindDnArg.addValue(ADSContext.getAdministratorDN(uid));
        bindDnArg.setPresent(true);
      }
      else
      {
        bindDnArg.setPresent(false);
      }

      StringArgument bindPasswordArg = secureArgsList.getBindPasswordArg();
      bindPasswordArg.clearValues();
      if (pwd != null)
      {
        bindPasswordArg.addValue(pwd);
        bindPasswordArg.setPresent(true);
      }
      else
      {
        bindPasswordArg.setPresent(false);
      }

      if (ci == null)
      {
        ci = new LDAPConnectionConsoleInteraction(this, parser.getSecureArgsList());
        ci.setDisplayLdapIfSecureParameters(true);
      }

      try
      {
        ci.run(false);
        userData.setAdminUID(ci.getAdministratorUID());
        userData.setAdminPwd(ci.getBindPassword());

        info.setConnectionPolicy(ConnectionProtocolPolicy.USE_ADMIN);
        String adminConnectorUrl = info.getAdminConnectorURL();
        if (adminConnectorUrl == null)
        {
          logger.warn(LocalizableMessage.raw("Error retrieving a valid Administration Connector URL in conf file."));
          LocalizableMessage msg = ERR_COULD_NOT_FIND_VALID_LDAPURL.get();
          throw new ClientException(ReturnCode.APPLICATION_ERROR, msg);
        }
        try
        {
          URI uri = new URI(adminConnectorUrl);
          int port = uri.getPort();
          portArg.clearValues();
          portArg.addValue(String.valueOf(port));
          ci.setPortNumber(port);
        }
        catch (Throwable t)
        {
          logger.error(LocalizableMessage.raw("Error parsing url: "+adminConnectorUrl));
        }
        updateTrustManager(userData, ci);

        info.setConnectionPolicy(ConnectionProtocolPolicy.USE_ADMIN);
        adminConnectorUrl = info.getAdminConnectorURL();
        if (adminConnectorUrl == null)
        {
          logger.warn(LocalizableMessage.raw(
         "Error retrieving a valid Administration Connector URL in conf file."));
          LocalizableMessage msg = ERR_COULD_NOT_FIND_VALID_LDAPURL.get();
          throw new ClientException(ReturnCode.APPLICATION_ERROR, msg);
        }

        userData.setLocalServerUrl(adminConnectorUrl);
        couldConnect = true;
      }
      catch (ArgumentException e)
      {
        parser.displayMessageAndUsageReference(getErrStream(), e.getMessageObject());
      }
      catch (ClientException e) {
        printErrorMessage(e.getMessageObject());
        println();
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
        catch (ClientException ce)
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
        logger.error(LocalizableMessage.raw("Could not create UninstallData: "+t, t));
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
    catch (ClientException ce)
    {
      logger.warn(LocalizableMessage.raw("Error reading input: %s", ce), ce);
    }
    return s;
  }

  private boolean startServer(boolean suppressOutput)
  {
    logger.info(LocalizableMessage.raw("startServer, suppressOutput: " + suppressOutput));
    boolean serverStarted = false;
    Application application = new Application()
    {
      @Override
      public String getInstallationPath()
      {
        return Installation.getLocal().getRootDirectory().getAbsolutePath();
      }
      @Override
      public String getInstancePath()
      {
        String installPath =  getInstallationPath();

        // look for <installPath>/lib/resource.loc
        String instancePathFileName = installPath + File.separator + "lib"
        + File.separator + "resource.loc";
        File f = new File(instancePathFileName);
        if (!f.exists())
        {
          return installPath;
        }

        // Read the first line and close the file.
        try (BufferedReader reader = new BufferedReader(new FileReader(instancePathFileName)))
        {
          String line = reader.readLine();
          return new File(line).getAbsolutePath();
        }
        catch (Exception e)
        {
          return installPath;
        }
      }
      @Override
      public ProgressStep getCurrentProgressStep()
      {
        return UninstallProgressStep.NOT_STARTED;
      }
      @Override
      public Integer getRatio(ProgressStep step)
      {
        return 0;
      }
      @Override
      public LocalizableMessage getSummary(ProgressStep step)
      {
        return null;
      }
      @Override
      public boolean isFinished()
      {
        return false;
      }
      @Override
      public boolean isCancellable()
      {
        return false;
      }
      @Override
      public void cancel()
      {
        // no-op
      }
      @Override
      public void run()
      {
        // no-op
      }
    };
    application.setProgressMessageFormatter(
        new PlainTextProgressMessageFormatter());
    if (!suppressOutput)
    {
      application.addProgressUpdateListener(
          new ProgressUpdateListener() {
            @Override
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
      if (suppressOutput)
      {
        controller.startServer(true);
      }
      else
      {
        println();
        controller.startServer(false);
        println();
      }
      serverStarted = Installation.getLocal().getStatus().isServerRunning();
      logger.info(LocalizableMessage.raw("server started successfully. serverStarted: "+
          serverStarted));
    }
    catch (ApplicationException ae)
    {
      logger.warn(LocalizableMessage.raw("ApplicationException: "+ae, ae));
      if (!suppressOutput)
      {
        printErrorMessage(ae.getMessageObject());
      }
    }
    catch (Throwable t)
    {
      logger.error(LocalizableMessage.raw("Unexpected error: "+t, t));
      throw new IllegalStateException("Unexpected error: "+t, t);
    }
    return serverStarted;
  }

  /**
   * Updates the contents of the UninstallUserData while trying to connect to
   * the remote servers. It returns <CODE>true</CODE> if we could connect to the
   * remote servers and all the presented certificates were accepted and
   * <CODE>false</CODE> otherwise. continue if
   *
   * @param userData
   *          the user data to be updated.
   * @return <CODE>true</CODE> if we could connect to the remote servers and all
   *         the presented certificates were accepted and <CODE>false</CODE>
   *         otherwise.
   * @throws UserDataException
   *           if were are not in interactive mode and not in force on error
   *           mode and the operation must be stopped.
   * @throws ClientException
   *           If there is an error processing data in non-interactive mode and
   *           an error must be thrown (not in force on error mode).
   */
  private boolean updateUserUninstallDataWithRemoteServers(
      UninstallUserData userData) throws UserDataException, ClientException
  {
    boolean accepted = false;
    boolean interactive = parser.isInteractive();
    boolean forceOnError = parser.isForceOnError();

    boolean exceptionOccurred = true;

    LocalizableMessage exceptionMsg = null;

    logger.info(LocalizableMessage.raw("Updating user data with remote servers."));

    ConnectionWrapper conn = null;
    try
    {
      info.setTrustManager(userData.getTrustManager());
      info.setConnectTimeout(getConnectTimeout());
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
        logger.error(LocalizableMessage.raw("Error parsing url: "+adminConnectorUrl));
      }
      conn = new ConnectionWrapper(new HostPort(host, port), connectionType, dn, pwd,
          getConnectTimeout(), userData.getTrustManager());

      ADSContext adsContext = new ADSContext(conn);
      if (interactive && userData.getTrustManager() == null)
      {
        // This is required when the user did  connect to the server using SSL
        // or Start TLS in interactive mode.  In this case
        // LDAPConnectionInteraction.run does not initialize the keystore and
        // the trust manager is null.
        forceTrustManagerInitialization();
        updateTrustManager(userData, ci);
      }
      logger.info(LocalizableMessage.raw("Reloading topology"));
      TopologyCache cache = new TopologyCache(adsContext,
          userData.getTrustManager(), getConnectTimeout());
      cache.getFilter().setSearchMonitoringInformation(false);
      cache.reloadTopology();

      accepted = handleTopologyCache(cache, userData);

      exceptionOccurred = false;
    }
    catch (NamingException ne)
    {
      logger.warn(LocalizableMessage.raw("Error connecting to server: "+ne, ne));
      if (isCertificateException(ne))
      {
        String details = ne.getMessage() != null ?
            ne.getMessage() : ne.toString();
        exceptionMsg = INFO_ERROR_READING_CONFIG_LDAP_CERTIFICATE.get(details);
      }
      else
      {
        exceptionMsg = getThrowableMsg(INFO_ERROR_CONNECTING_TO_LOCAL.get(), ne);
      }
    } catch (TopologyCacheException te)
    {
      logger.warn(LocalizableMessage.raw("Error connecting to server: "+te, te));
      exceptionMsg = Utils.getMessage(te);
    } catch (ClientException ce)
    {
      throw ce;
    } catch (Throwable t)
    {
      logger.warn(LocalizableMessage.raw("Error connecting to server: "+t, t));
      exceptionMsg = getThrowableMsg(INFO_BUG_MSG.get(), t);
    }
    finally
    {
      StaticUtils.close(conn);
    }
    if (exceptionOccurred)
    {
      if (!interactive)
      {
        if (forceOnError)
        {
          println();
          printErrorMessage(ERR_UNINSTALL_ERROR_UPDATING_REMOTE_FORCE.get(
              "--" + parser.getSecureArgsList().getAdminUidArg().getLongIdentifier(),
              "--" + OPTION_LONG_BINDPWD,
              "--" + OPTION_LONG_BINDPWD_FILE,
              exceptionMsg));
        }
        else
        {
          println();
          throw new UserDataException(null,
              ERR_UNINSTALL_ERROR_UPDATING_REMOTE_NO_FORCE.get(
                  "--" + parser.getSecureArgsList().getAdminUidArg().getLongIdentifier(),
                  "--" + OPTION_LONG_BINDPWD,
                  "--" + OPTION_LONG_BINDPWD_FILE,
                  "--" + parser.forceOnErrorArg.getLongIdentifier(),
                  exceptionMsg));
        }
      }
      else
      {
        try
        {
          accepted = askConfirmation(
              ERR_UNINSTALL_NOT_UPDATE_REMOTE_PROMPT.get(),
              false, logger);
        }
        catch (ClientException ce)
        {
          throw new UserDataException(null, ce.getMessageObject(), ce);
        }
      }
    }
    userData.setUpdateRemoteReplication(accepted);
    logger.info(LocalizableMessage.raw("accepted: "+accepted));
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
   * @throws ClientException if there is an error processing data in
   * non-interactive mode and an error must be thrown (not in force on error
   * mode).
   */
  private boolean handleTopologyCache(TopologyCache cache,
      UninstallUserData userData) throws UserDataException, ClientException
  {
    boolean returnValue;
    boolean stopProcessing = false;
    boolean reloadTopologyCache = false;

    logger.info(LocalizableMessage.raw("Handle topology cache."));

    Set<TopologyCacheException> exceptions = new HashSet<>();
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
    Set<LocalizableMessage> exceptionMsgs = new LinkedHashSet<>();
    /* Check the exceptions and see if we throw them or not. */
    for (TopologyCacheException e : exceptions)
    {
      logger.info(LocalizableMessage.raw("Analyzing exception: "+e, e));
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
        if (isCertificateException(e.getCause()))
        {
          if (isInteractive())
          {
            println();
            stopProcessing = true;
            if (ci.promptForCertificateConfirmation(e.getCause(),
                e.getTrustManager(), e.getLdapUrl(), logger))
            {
              reloadTopologyCache = true;
              updateTrustManager(userData, ci);
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
    if (isInteractive())
    {
      if (!stopProcessing && !exceptionMsgs.isEmpty())
      {
        println();
        try
        {
          returnValue = askConfirmation(
            ERR_UNINSTALL_READING_REGISTERED_SERVERS_CONFIRM_UPDATE_REMOTE.get(
                Utils.getMessageFromCollection(exceptionMsgs,
                  Constants.LINE_SEPARATOR)), true, logger);
        }
        catch (ClientException ce)
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
      logger.info(LocalizableMessage.raw("exceptionMsgs: "+exceptionMsgs));
      if (!exceptionMsgs.isEmpty())
      {
        if (parser.isForceOnError())
        {
          LocalizableMessage msg = Utils.getMessageFromCollection(exceptionMsgs,
              Constants.LINE_SEPARATOR);
          println();
          printErrorMessage(msg);
          returnValue = false;
        }
        else
        {
          LocalizableMessage msg =
            ERR_UNINSTALL_ERROR_UPDATING_REMOTE_NO_FORCE.get(
              "--" + parser.getSecureArgsList().getAdminUidArg().getLongIdentifier(),
              "--" + OPTION_LONG_BINDPWD,
              "--" + OPTION_LONG_BINDPWD_FILE,
              "--" + parser.forceOnErrorArg.getLongIdentifier(),
              Utils.getMessageFromCollection(exceptionMsgs, Constants.LINE_SEPARATOR));
          throw new ClientException(ReturnCode.APPLICATION_ERROR, msg);
        }
      }
      else
      {
        returnValue = true;
      }
    }
    logger.info(LocalizableMessage.raw("Return value: "+returnValue));
    return returnValue;
  }

  @Override
  public boolean isAdvancedMode() {
    return false;
  }

  @Override
  public boolean isInteractive() {
    return !forceNonInteractive && parser.isInteractive();
  }

  @Override
  public boolean isMenuDrivenMode() {
    return true;
  }

  @Override
  public boolean isQuiet() {
    return false;
  }

  @Override
  public boolean isScriptFriendly() {
    return false;
  }

  @Override
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

   /** Forces the initialization of the trust manager in the LDAPConnectionInteraction object. */
   private void forceTrustManagerInitialization()
   {
     forceNonInteractive = true;
     try
     {
       ci.initializeTrustManagerIfRequired();
     }
     catch (ArgumentException ae)
     {
       logger.warn(LocalizableMessage.raw("Error initializing trust store: "+ae, ae));
     }
     forceNonInteractive = false;
   }

   private void printErrorMessage(LocalizableMessage msg)
   {
     super.println(msg);
     logger.warn(LocalizableMessage.raw(msg));
   }

   /**
    * Returns the timeout to be used to connect in milliseconds.  The method
    * must be called after parsing the arguments.
    * @return the timeout to be used to connect in milliseconds.  Returns
    * {@code 0} if there is no timeout.
    * @throw {@code IllegalStateException} if the method is called before
    * parsing the arguments.
    */
   private int getConnectTimeout()
   {
     try
     {
       return parser.getSecureArgsList().getConnectTimeoutArg().getIntValue();
     }
     catch (ArgumentException ae)
     {
       throw new IllegalStateException("Argument parser is not parsed: "+ae,
           ae);
     }
   }
}
