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

import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ServerDescriptor;
import org.opends.admin.ads.TopologyCache;
import org.opends.admin.ads.TopologyCacheException;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.guitools.statuspanel.ConfigFromFile;
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
import org.opends.server.util.args.ArgumentException;


import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.IOException;

import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;

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
class UninstallCliHelper extends CliApplicationHelper {

  static private final Logger LOG =
          Logger.getLogger(UninstallCliHelper.class.getName());

  private UninstallerArgumentParser parser;

  /**
   * Default constructor.
   */
  public UninstallCliHelper()
  {
    super(System.out, System.err, System.in);
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
    }
    userData.setReferencedHostName(referencedHostName);

    /*
     * Step 4: check if server is running.  Depending if it is running and the
     * OS we are running, ask for authentication information.
     */
    if (!isCancelled)
    {
      isCancelled = checkServerState(userData, isInteractive);
    }

    if (isCancelled && !userData.isForceOnError())
    {
      userData = null;
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
    Message[] options = new Message[] {
      Message.raw("1"),
      Message.raw("2"),
      Message.raw("3")
    };
    Message answer = promptConfirm(INFO_CLI_UNINSTALL_WHAT_TO_DELETE.get(),
        options[0], options);
    if (options[2].toString().equals(answer.toString()))
    {
      cancelled = true;
    }
    else if (options[0].toString().equals(answer.toString()))
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
        printLineBreak();
//      Ask for confirmation for the different items
        Message[] keys = {
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


        Message[] validValues = {
                INFO_CLI_YES_LONG.get(),
                INFO_CLI_NO_LONG.get(),
                INFO_CLI_YES_SHORT.get(),
                INFO_CLI_NO_SHORT.get()
        };
        boolean[] answers = new boolean[keys.length];
        for (int i=0; i<keys.length; i++)
        {
          boolean ignore = ((i == 6) && (outsideDbs.size() == 0)) ||
          ((i == 7) && (outsideLogs.size() == 0));
          if (!ignore)
          {
            Message msg = keys[i];
            answer = promptConfirm(msg, INFO_CLI_YES_LONG.get(), validValues);

            answers[i] = INFO_CLI_YES_LONG.get().toString().equalsIgnoreCase(
                answer.toString()) || INFO_CLI_YES_SHORT.get().toString().
                equalsIgnoreCase(answer.toString());
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
          printLineBreak();
          printErrorMessage(ERR_CLI_UNINSTALL_NOTHING_TO_BE_UNINSTALLED.get());
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
   * @param interactive boolean telling whether this is an interactive uninstall
   * or not.
   * @return <CODE>true</CODE> if the user wants to continue with uninstall and
   * <CODE>false</CODE> otherwise.
   * @throws UserDataException if there is a problem with the data
   * provided by the user (in the particular case where we are on quiet
   * uninstall and some data is missing or not valid).
   */
  private boolean checkServerState(UninstallUserData userData,
                                        boolean interactive)
  throws UserDataException
  {
    boolean cancelled = false;
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
            printLineBreak();
            cancelled = !askForAuthenticationIfNeeded(userData);
            if (cancelled)
            {
              /* Ask for confirmation to stop server */
              printLineBreak();
              cancelled = !confirmToStopServer();
            }
            else
            {
              cancelled = !updateUserUninstallDataWithRemoteServers(userData,
                  interactive);
              if (cancelled)
              {
                printLineBreak();
                /* Ask for confirmation to stop server */
                cancelled = !confirmToStopServer();
              }
            }
          }
          else
          {
            printLineBreak();
            /* Ask for confirmation to stop server */
            cancelled = !confirmToStopServer();
          }
        }
        else
        {
          cancelled =
            !updateUserUninstallDataWithRemoteServers(userData, interactive);
        }
      }
      else
      {
        if (interactive)
        {
          printLineBreak();
          if (confirmToUpdateRemoteAndStart())
          {
            boolean startWorked = startServer(userData.isQuiet());
            // Ask for authentication if needed, etc.
            if (startWorked)
            {
              cancelled = !askForAuthenticationIfNeeded(userData);
              if (cancelled)
              {
                printLineBreak();
                /* Ask for confirmation to stop server */
                cancelled = !confirmToStopServer();
              }
              else
              {
                cancelled = !updateUserUninstallDataWithRemoteServers(userData,
                    interactive);
                if (cancelled)
                {
                  printLineBreak();
                  /* Ask for confirmation to stop server */
                  cancelled = !confirmToStopServer();
                }
              }
            }
            else
            {
              userData.setStopServer(false);
              printLineBreak();
              /* Ask for confirmation to delete files */
              cancelled = !confirmDeleteFiles();
            }
          }
          else
          {
            printLineBreak();
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
              !updateUserUninstallDataWithRemoteServers(userData, interactive);
          }
          else
          {
            cancelled  = !userData.isForceOnError();
            userData.setStopServer(false);
          }
        }
      }
    }
    else
    {
      if (conf.isServerRunning())
      {
        if (interactive)
        {
          printLineBreak();
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
          printLineBreak();
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
    return confirm(INFO_CLI_UNINSTALL_CONFIRM_STOP.get());
  }

  /**
   *  Ask for confirmation to delete files.
   *  @return <CODE>true</CODE> if the user wants to continue and delete the
   *  files.  <CODE>false</CODE> otherwise.
   */
  private boolean confirmDeleteFiles()
  {
    return confirm(INFO_CLI_UNINSTALL_CONFIRM_DELETE_FILES.get());
  }

  /**
   *  Ask for confirmation to update configuration on remote servers.
   *  @return <CODE>true</CODE> if the user wants to continue and stop the
   *  server.  <CODE>false</CODE> otherwise.
   */
  private boolean confirmToUpdateRemote()
  {
    return confirm(INFO_CLI_UNINSTALL_CONFIRM_UPDATE_REMOTE.get());
  }

  /**
   *  Ask for confirmation to update configuration on remote servers.
   *  @return <CODE>true</CODE> if the user wants to continue and stop the
   *  server.  <CODE>false</CODE> otherwise.
   */
  private boolean confirmToUpdateRemoteAndStart()
  {
    return confirm(INFO_CLI_UNINSTALL_CONFIRM_UPDATE_REMOTE_AND_START.get());
  }

  /**
   *  Ask for confirmation to provide again authentication.
   *  @return <CODE>true</CODE> if the user wants to provide authentication
   *  againr.  <CODE>false</CODE> otherwise.
   */
  private boolean promptToProvideAuthenticationAgain()
  {
    return confirm(INFO_UNINSTALL_CONFIRM_PROVIDE_AUTHENTICATION_AGAIN.get());
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
    boolean couldConnect = false;
    ConfigFromFile conf = new ConfigFromFile();
    conf.readConfiguration();
    String ldapUrl = conf.getLDAPURL();
    String startTlsUrl = conf.getStartTLSURL();
    String ldapsUrl = conf.getLDAPSURL();
    while (!couldConnect && accepted)
    {
      boolean prompted = false;
      while (uid == null)
      {
        printLineBreak();
        uid = askForAdministratorUID(parser.getDefaultAdministratorUID());
        prompted = true;
      }
      while (pwd == null)
      {
        if (!prompted)
        {
          printLineBreak();
        }
        pwd = askForAdministratorPwd();
      }
      userData.setAdminUID(uid);
      userData.setAdminPwd(pwd);
      InitialLdapContext ctx = null;
      String usedUrl = null;
      try
      {
        String dn = ADSContext.getAdministratorDN(uid);
        if ((ldapsUrl != null) && (parser.useSSL() || !parser.useStartTLS()))
        {
          usedUrl = ldapsUrl;
          ctx = Utils.createLdapsContext(ldapsUrl, dn, pwd,
              Utils.getDefaultLDAPTimeout(), null, userData.getTrustManager());
        }
        else if ((startTlsUrl != null) &&
            (!parser.useSSL() || parser.useStartTLS()))
        {
          usedUrl = startTlsUrl;
          ctx = Utils.createStartTLSContext(startTlsUrl, dn, pwd,
              Utils.getDefaultLDAPTimeout(), null, userData.getTrustManager(),
              null);
        }
        else if ((ldapUrl != null) && !parser.useSSL() && !parser.useStartTLS())
        {
          usedUrl = ldapUrl;
          ctx = Utils.createLdapContext(ldapUrl, dn, pwd,
              Utils.getDefaultLDAPTimeout(), null);
        }
        else
        {

          LOG.log(Level.WARNING,
              "Error retrieving a valid LDAP URL in conf file");
          printErrorMessage(ERR_COULD_NOT_FIND_VALID_LDAPURL.get());
        }
        if (usedUrl != null)
        {
          userData.setLocalServerUrl(usedUrl);
          couldConnect = true;
        }
      } catch (NamingException ne)
      {
        LOG.log(Level.WARNING, "Error connecting to server: "+ne, ne);

        if (Utils.isCertificateException(ne))
        {
          printLineBreak();
          accepted = promptForCertificateConfirmation(ne,
              userData.getTrustManager(), usedUrl);
        }
        else
        {
          uid = null;
          pwd = null;
          printLineBreak();
          printErrorMessage(
              Utils.getThrowableMsg(INFO_ERROR_CONNECTING_TO_LOCAL.get(), ne));
          printLineBreak();
          accepted = promptToProvideAuthenticationAgain();
        }

      } catch (Throwable t)
      {
        LOG.log(Level.WARNING, "Error connecting to server: "+t, t);
        uid = null;
        pwd = null;
        printLineBreak();
        printErrorMessage(Utils.getThrowableMsg(INFO_BUG_MSG.get(), t));
        printLineBreak();
        accepted = promptToProvideAuthenticationAgain();
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
    }

    if (accepted)
    {
      String referencedHostName = userData.getReferencedHostName();
      while (referencedHostName == null)
      {
        printLineBreak();
        referencedHostName = askForReferencedHostName(userData.getHostName());
      }
      userData.setReferencedHostName(referencedHostName);
    }
    userData.setUpdateRemoteReplication(accepted);
    return accepted;
  }

  private String askForReferencedHostName(String defaultHostName)
  {
    return promptForString(INFO_UNINSTALL_CLI_REFERENCED_HOSTNAME_PROMPT.get(),
        defaultHostName);
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
        printLineBreak();
      }
      controller.startServer(supressOutput);
      if (!supressOutput)
      {
        printLineBreak();
      }
      serverStarted = Installation.getLocal().getStatus().isServerRunning();
    }
    catch (ApplicationException ae)
    {
      if (!supressOutput)
      {
        printErrorMessage(ae.getMessage());
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
   * @param interactive whether we are in interactive mode or not.
   * @return <CODE>true</CODE> if we could connect
   * to the remote servers and all the presented certificates were accepted and
   * <CODE>false</CODE> otherwise.
   */
  private boolean updateUserUninstallDataWithRemoteServers(
      UninstallUserData userData, boolean interactive)
  {
    boolean accepted = false;
    InitialLdapContext ctx = null;
    try
    {
      ConfigFromFile conf = new ConfigFromFile();
      conf.readConfiguration();
      String ldapUrl = conf.getLDAPURL();
      String startTlsUrl = conf.getStartTLSURL();
      String ldapsUrl = conf.getLDAPSURL();
      String adminUid = userData.getAdminUID();
      String pwd = userData.getAdminPwd();
      String dn = ADSContext.getAdministratorDN(adminUid);
      if ((ldapsUrl != null) && (parser.useSSL() || !parser.useStartTLS()))
      {
        ctx = Utils.createLdapsContext(ldapsUrl, dn, pwd,
            Utils.getDefaultLDAPTimeout(), null, userData.getTrustManager());
      }
      else if ((startTlsUrl != null) &&
          (!parser.useSSL() || parser.useStartTLS()))
      {
        ctx = Utils.createStartTLSContext(startTlsUrl, dn, pwd,
            Utils.getDefaultLDAPTimeout(), null, userData.getTrustManager(),
            null);
      }
      else if ((ldapUrl != null) && !parser.useSSL() && !parser.useStartTLS())
      {
        ctx = Utils.createLdapContext(ldapUrl, dn, pwd,
            Utils.getDefaultLDAPTimeout(), null);
      }
      else
      {
        LOG.log(Level.WARNING,
            "Error retrieving a valid LDAP URL in conf file");
        printLineBreak();
        printErrorMessage(ERR_COULD_NOT_FIND_VALID_LDAPURL.get());
      }
      ADSContext adsContext = new ADSContext(ctx);
      TopologyCache cache = new TopologyCache(adsContext,
          userData.getTrustManager());
      cache.reloadTopology();

      accepted = handleTopologyCache(cache, interactive, userData);
      userData.setRemoteServers(cache.getServers());
    } catch (NamingException ne)
    {
      LOG.log(Level.WARNING, "Error connecting to server: "+ne, ne);
      if (Utils.isCertificateException(ne))
      {
        printLineBreak();
        printErrorMessage(INFO_ERROR_READING_CONFIG_LDAP_CERTIFICATE.get(
            ne.getMessage()));
      }
      else
      {
        printLineBreak();
        printErrorMessage(
            Utils.getThrowableMsg(INFO_ERROR_CONNECTING_TO_LOCAL.get(), ne));
      }
    } catch (TopologyCacheException te)
    {
      LOG.log(Level.WARNING, "Error connecting to server: "+te, te);
      printLineBreak();
      printErrorMessage(Utils.getMessage(te));

    } catch (Throwable t)
    {
      LOG.log(Level.WARNING, "Error connecting to server: "+t, t);
      printLineBreak();
      printErrorMessage(Utils.getThrowableMsg(INFO_BUG_MSG.get(), t));
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
   * @param interactive if we are in interactive mode or not.
   */
  private boolean handleTopologyCache(TopologyCache cache, boolean interactive,
      UninstallUserData userData)
  {
    boolean returnValue;
    boolean stopProcessing = false;
    boolean reloadTopologyCache = false;
    ApplicationTrustManager trustManager = userData.getTrustManager();
    Set<TopologyCacheException> exceptions =
      new HashSet<TopologyCacheException>();
    /* Analyze if we had any exception while loading servers.  For the moment
     * only throw the exception found if the user did not provide the
     * Administrator DN and this caused a problem authenticating in one server
     * or if there is a certificate problem.
     */
    Set<ServerDescriptor> servers = cache.getServers();
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
        printLineBreak();
        printErrorMessage(INFO_NOT_GLOBAL_ADMINISTRATOR_PROVIDED.get());
        stopProcessing = true;
        break;
      case GENERIC_CREATING_CONNECTION:
        if ((e.getCause() != null) &&
            Utils.isCertificateException(e.getCause()))
        {
          if (interactive)
          {
            printLineBreak();
            if (promptForCertificateConfirmation(e.getCause(),
                trustManager, e.getLdapUrl()))
            {
              stopProcessing = true;
              reloadTopologyCache = true;
            }
            else
            {
              stopProcessing = true;
            }
          }
          else
          {
            stopProcessing = true;
            printLineBreak();
            printErrorMessage(
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
        printLineBreak();
        returnValue = confirm(
            ERR_UNINSTALL_READING_REGISTERED_SERVERS_CONFIRM_UPDATE_REMOTE.get(
                Utils.getMessageFromCollection(exceptionMsgs,
                  Constants.LINE_SEPARATOR).toString()));
      }
      else if (reloadTopologyCache)
      {
       returnValue = updateUserUninstallDataWithRemoteServers(userData,
           interactive);
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
        printLineBreak();
        printErrorMessage(Utils.getMessageFromCollection(exceptionMsgs,
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
}
