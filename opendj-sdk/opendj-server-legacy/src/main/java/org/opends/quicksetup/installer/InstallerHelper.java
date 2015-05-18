/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */

package org.opends.quicksetup.installer;

import static org.opends.messages.QuickSetupMessages.*;
import static org.opends.quicksetup.util.Utils.*;

import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.util.OperatingSystem.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import javax.naming.ldap.InitialLdapContext;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.CoreMessages;
import org.opends.messages.BackendMessages;
import org.opends.messages.ReplicationMessages;
import org.opends.quicksetup.Application;
import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.JavaArguments;
import org.opends.quicksetup.ReturnCode;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.util.OutputReader;
import org.opends.quicksetup.util.Utils;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.PropertyException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.client.ldap.LDAPManagementContext;
import org.opends.server.admin.std.client.BackendCfgClient;
import org.opends.server.admin.std.client.CryptoManagerCfgClient;
import org.opends.server.admin.std.client.LocalDBBackendCfgClient;
import org.opends.server.admin.std.client.ReplicationDomainCfgClient;
import org.opends.server.admin.std.client.ReplicationServerCfgClient;
import org.opends.server.admin.std.client.ReplicationSynchronizationProviderCfgClient;
import org.opends.server.admin.std.client.RootCfgClient;
import org.opends.server.admin.std.meta.BackendCfgDefn;
import org.opends.server.admin.std.meta.LocalDBBackendCfgDefn;
import org.opends.server.admin.std.meta.ReplicationDomainCfgDefn;
import org.opends.server.admin.std.meta.ReplicationServerCfgDefn;
import org.opends.server.admin.std.meta.ReplicationSynchronizationProviderCfgDefn;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.tools.ConfigureDS;
import org.opends.server.tools.ConfigureWindowsService;
import org.opends.server.tools.JavaPropertiesTool;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.SetupUtils;
import org.opends.server.util.StaticUtils;

/**
 * This is the only class that uses classes in org.opends.server (excluding the
 * case of DynamicConstants, SetupUtils and CertificateManager
 * which are already included in quicksetup.jar).
 *
 * Important note: do not include references to this class until OpenDS.jar has
 * been loaded. These classes must be loaded during Runtime.
 * The code is written in a way that when we execute the code that uses these
 * classes the required jar files are already loaded. However these jar files
 * are not necessarily loaded when we create this class.
 */
public class InstallerHelper {
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final int MAX_ID_VALUE = Short.MAX_VALUE;
  private static final long ONE_MEGABYTE = 1024L * 1024;
  /**
   * Invokes the method ConfigureDS.configMain with the provided parameters.
   * @param args the arguments to be passed to ConfigureDS.configMain.
   * @return the return code of the ConfigureDS.configMain method.
   * @throws ApplicationException if something goes wrong.
   * @see org.opends.server.tools.ConfigureDS#configMain(String[],
   *                                java.io.OutputStream, java.io.OutputStream)
   */
  public int invokeConfigureServer(String[] args) throws ApplicationException {
    return ConfigureDS.configMain(args, System.out, System.err);
  }

  /**
   * Invokes the import-ldif command-line with the provided parameters.
   *
   * @param application
   *          the application that is launching this.
   * @param args
   *          the arguments to be passed to import-ldif.
   * @return the return code of the import-ldif call.
   * @throws IOException
   *           if the process could not be launched.
   * @throws InterruptedException
   *           if the process was interrupted.
   */
  public int invokeImportLDIF(final Application application, String[] args) throws IOException, InterruptedException
  {
    final File installPath = new File(application.getInstallationPath());
    final File binPath = new File(installPath, isWindows() ? Installation.WINDOWS_BINARIES_PATH_RELATIVE
                                                           : Installation.UNIX_BINARIES_PATH_RELATIVE);
    final File importLDIFPath = new File(binPath, isWindows() ? Installation.WINDOWS_IMPORT_LDIF
                                                              : Installation.UNIX_IMPORT_LDIF);

    final ArrayList<String> argList = new ArrayList<String>();
    argList.add(Utils.getScriptPath(importLDIFPath.getAbsolutePath()));
    argList.addAll(Arrays.asList(args));
    logger.info(LocalizableMessage.raw("import-ldif arg list: " + argList));

    final ProcessBuilder processBuilder = new ProcessBuilder(argList.toArray(new String[argList.size()]));
    final Map<String, String> env = processBuilder.environment();
    env.remove(SetupUtils.OPENDJ_JAVA_HOME);
    env.remove(SetupUtils.OPENDJ_JAVA_ARGS);
    env.remove("CLASSPATH");
    processBuilder.directory(installPath);

    Process process = null;
    try
    {
      process = processBuilder.start();
      final BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream()));
      new OutputReader(err)
      {
        @Override
        public void processLine(final String line)
        {
          logger.warn(LocalizableMessage.raw("import-ldif error log: " + line));
          application.notifyListeners(LocalizableMessage.raw(line));
          application.notifyListeners(application.getLineBreak());
        }
      };

      final BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream()));
      new OutputReader(out)
      {
        @Override
        public void processLine(final String line)
        {
          logger.info(LocalizableMessage.raw("import-ldif out log: " + line));
          application.notifyListeners(LocalizableMessage.raw(line));
          application.notifyListeners(application.getLineBreak());
        }
      };

      return process.waitFor();
    }
    finally
    {
      if (process != null)
      {
        closeProcessStream(process.getErrorStream(), "error");
        closeProcessStream(process.getOutputStream(), "output");
      }
    }
  }

  private void closeProcessStream(final Closeable stream, final String streamName)
  {
    try
    {
      stream.close();
    }
    catch (Throwable t)
    {
      logger.warn(LocalizableMessage.raw("Error closing " + streamName + " stream: " + t, t));
    }
  }

  /**
   * Returns the LocalizableMessage ID that corresponds to a successfully started server.
   * @return the LocalizableMessage ID that corresponds to a successfully started server.
   */
  public String getStartedId()
  {
    return String.valueOf(CoreMessages.NOTE_DIRECTORY_SERVER_STARTED.ordinal());
  }

  /**
   * This methods enables this server as a Windows service.
   * @throws ApplicationException if something goes wrong.
   */
  public void enableWindowsService() throws ApplicationException {
    int code = ConfigureWindowsService.enableService(System.out, System.err);

    LocalizableMessage errorMessage = INFO_ERROR_ENABLING_WINDOWS_SERVICE.get();

    switch (code) {
      case
        ConfigureWindowsService.SERVICE_ENABLE_SUCCESS:
        break;
      case
        ConfigureWindowsService.SERVICE_ALREADY_ENABLED:
        break;
      default:
        throw new ApplicationException(
            ReturnCode.WINDOWS_SERVICE_ERROR,
                errorMessage, null);
    }
  }

  /**
   * This method disables this server as a Windows service.
   * @throws ApplicationException if something goes worong.
   */
  public void disableWindowsService() throws ApplicationException
  {
    int code = ConfigureWindowsService.disableService(System.out, System.err);
    if (code == ConfigureWindowsService.SERVICE_DISABLE_ERROR) {
      throw new ApplicationException(
          // TODO: fix this message's format string
          ReturnCode.WINDOWS_SERVICE_ERROR,
              INFO_ERROR_DISABLING_WINDOWS_SERVICE.get(""), null);
    }
  }

  /**
   * Creates a template LDIF file with an entry that has as dn the provided
   * baseDn.
   * @param baseDn the dn of the entry that will be created in the LDIF file.
   * @return the File object pointing to the created temporary file.
   * @throws ApplicationException if something goes wrong.
   */
  public File createBaseEntryTempFile(String baseDn)
          throws ApplicationException {
    File ldifFile;
    try
    {
      ldifFile = File.createTempFile("opendj-base-entry", ".ldif");
      ldifFile.deleteOnExit();
    } catch (IOException ioe)
    {
      LocalizableMessage failedMsg =
              getThrowableMsg(INFO_ERROR_CREATING_TEMP_FILE.get(), ioe);
      throw new ApplicationException(
          ReturnCode.FILE_SYSTEM_ACCESS_ERROR,
          failedMsg, ioe);
    }

    try
    {
      LDIFExportConfig exportConfig = new LDIFExportConfig(
          ldifFile.getAbsolutePath(), ExistingFileBehavior.OVERWRITE);

      LDIFWriter writer = new LDIFWriter(exportConfig);

      DN dn = DN.valueOf(baseDn);
      Entry entry = StaticUtils.createEntry(dn);

      writer.writeEntry(entry);
      writer.close();
    } catch (DirectoryException | LDIFException | IOException de) {
      throw new ApplicationException(
          ReturnCode.CONFIGURATION_ERROR,
              getThrowableMsg(INFO_ERROR_IMPORTING_LDIF.get(), de), de);
    } catch (Throwable t) {
      throw new ApplicationException(
          ReturnCode.BUG, getThrowableMsg(
              INFO_BUG_MSG.get(), t), t);
    }
    return ldifFile;
  }

  /**
   * Deletes a backend on the server.
   * @param ctx the connection to the server.
   * @param backendName the name of the backend to be deleted.
   * @param serverDisplay the server display.
   * @throws ApplicationException if something goes wrong.
   */
  public void deleteBackend(InitialLdapContext ctx, String backendName,
      String serverDisplay)
  throws ApplicationException
  {
    try
    {
      ManagementContext mCtx = LDAPManagementContext.createFromContext(
          JNDIDirContextAdaptor.adapt(ctx));
      RootCfgClient root = mCtx.getRootConfiguration();
      root.removeBackend(backendName);
    }
    catch (Throwable t)
    {
      throw new ApplicationException(
          ReturnCode.CONFIGURATION_ERROR,
          INFO_ERROR_CONFIGURING_REMOTE_GENERIC.get(serverDisplay, t),
          t);
    }
  }

  /**
   * Deletes a backend on the server.  It assumes the server is stopped.
   * @param backendName the name of the backend to be deleted.
   * @throws ApplicationException if something goes wrong.
   */
  public void deleteBackend(String backendName)
  throws ApplicationException
  {
    try
    {
      // Read the configuration file.
      String dn = Utilities.getRDNString("ds-cfg-backend-id",
          backendName)+",cn=Backends,cn=config";
      Utilities.deleteConfigSubtree(
          DirectoryServer.getConfigHandler(), DN.valueOf(dn));
    }
    catch (OpenDsException | ConfigException ode)
    {
      throw new ApplicationException(
          ReturnCode.CONFIGURATION_ERROR, ode.getMessageObject(), ode);
    }
  }


  /**
   * Creates a local database backend on the server.
   * @param ctx the connection to the server.
   * @param backendName the name of the backend to be created.
   * @param baseDNs the list of base DNs to be defined on the server.
   * @param serverDisplay the server display.
   * @throws ApplicationException if something goes wrong.
   */
  public void createLocalDBBackend(InitialLdapContext ctx,
      String backendName,
      Set<String> baseDNs,
      String serverDisplay)
  throws ApplicationException
  {
    try
    {
      ManagementContext mCtx = LDAPManagementContext.createFromContext(
          JNDIDirContextAdaptor.adapt(ctx));
      RootCfgClient root = mCtx.getRootConfiguration();
      LocalDBBackendCfgDefn provider = LocalDBBackendCfgDefn.getInstance();
      LocalDBBackendCfgClient backend = root.createBackend(provider,
          backendName, null);
      backend.setEnabled(true);
      Set<DN> setBaseDNs = new HashSet<DN>();
      for (String baseDN : baseDNs)
      {
        setBaseDNs.add(DN.valueOf(baseDN));
      }
      backend.setBaseDN(setBaseDNs);
      backend.setBackendId(backendName);
      backend.setWritabilityMode(BackendCfgDefn.WritabilityMode.ENABLED);
      backend.commit();
    }
    catch (Throwable t)
    {
      throw new ApplicationException(
          ReturnCode.CONFIGURATION_ERROR,
          INFO_ERROR_CONFIGURING_REMOTE_GENERIC.get(serverDisplay, t),
          t);
    }
  }

  /**
   * Sets the base DNs on a given backend.
   * @param ctx the connection to the server.
   * @param backendName the name of the backend where the base Dns must be
   * defined.
   * @param baseDNs the list of base DNs to be defined on the server.
   * @param serverDisplay the server display.
   * @throws ApplicationException if something goes wrong.
   */
  public void setBaseDns(InitialLdapContext ctx,
      String backendName,
      Set<String> baseDNs,
      String serverDisplay)
  throws ApplicationException
  {
    try
    {
      ManagementContext mCtx = LDAPManagementContext.createFromContext(
          JNDIDirContextAdaptor.adapt(ctx));
      RootCfgClient root = mCtx.getRootConfiguration();
      BackendCfgClient backend = root.getBackend(backendName);
      Set<DN> setBaseDNs = new HashSet<DN>();
      for (String baseDN : baseDNs)
      {
        setBaseDNs.add(DN.valueOf(baseDN));
      }
      backend.setBaseDN(setBaseDNs);
      backend.commit();
    }
    catch (Throwable t)
    {
      throw new ApplicationException(
          ReturnCode.CONFIGURATION_ERROR,
          INFO_ERROR_CONFIGURING_REMOTE_GENERIC.get(serverDisplay, t),
          t);
    }
  }

  /**
   * Configures the replication on a given server.
   * @param remoteCtx the connection to the server where we want to configure
   * the replication.
   * @param replicationServers a Map where the key value is the base dn and
   * the value is the list of replication servers for that base dn (or domain).
   * @param replicationPort the replicationPort of the server that is being
   * configured (it might not exist and the user specified it in the setup).
   * @param useSecureReplication whether to encrypt connections with the
   * replication port or not.
   * @param serverDisplay the server display.
   * @param usedReplicationServerIds the list of replication server ids that
   * are already used.
   * @param usedServerIds the list of server ids (domain ids) that
   * are already used.
   * @throws ApplicationException if something goes wrong.
   * @return a ConfiguredReplication object describing what has been configured.
   */
  public ConfiguredReplication configureReplication(
      InitialLdapContext remoteCtx, Map<String,Set<String>> replicationServers,
      int replicationPort, boolean useSecureReplication, String serverDisplay,
      Set<Integer> usedReplicationServerIds, Set<Integer> usedServerIds)
  throws ApplicationException
  {
    boolean synchProviderCreated;
    boolean synchProviderEnabled;
    boolean replicationServerCreated;
    boolean secureReplicationEnabled;
    try
    {
      ManagementContext mCtx = LDAPManagementContext.createFromContext(
          JNDIDirContextAdaptor.adapt(remoteCtx));
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
        // It does not exist.
      }
      if (sync == null)
      {
        ReplicationSynchronizationProviderCfgDefn provider =
          ReplicationSynchronizationProviderCfgDefn.getInstance();
        sync = root.createSynchronizationProvider(provider,
            "Multimaster Synchronization",
            new ArrayList<PropertyException>());
        sync.setJavaClass(
            org.opends.server.replication.plugin.MultimasterReplication.class.
            getName());
        sync.setEnabled(Boolean.TRUE);
        synchProviderCreated = true;
        synchProviderEnabled = false;
      }
      else
      {
        synchProviderCreated = false;
        if (!sync.isEnabled())
        {
          sync.setEnabled(Boolean.TRUE);
          synchProviderEnabled = true;
        }
        else
        {
          synchProviderEnabled = false;
        }
      }
      sync.commit();

      /*
       * Configure the replication server.
       */
      ReplicationServerCfgClient replicationServer;

      if (!sync.hasReplicationServer())
      {
        if (useSecureReplication)
        {
         CryptoManagerCfgClient crypto = root.getCryptoManager();
         if (!crypto.isSSLEncryption())
         {
           crypto.setSSLEncryption(true);
           crypto.commit();
           secureReplicationEnabled = true;
         }
         else
         {
           // Only mark as true if we actually change the configuration
           secureReplicationEnabled = false;
         }
        }
        else
        {
          secureReplicationEnabled = false;
        }
        int id = getReplicationId(usedReplicationServerIds);
        usedReplicationServerIds.add(id);
        replicationServer = sync.createReplicationServer(
            ReplicationServerCfgDefn.getInstance(),
            new ArrayList<PropertyException>());
        replicationServer.setReplicationServerId(id);
        replicationServer.setReplicationPort(replicationPort);
        replicationServerCreated = true;
      }
      else
      {
        secureReplicationEnabled = false;
        replicationServer = sync.getReplicationServer();
        usedReplicationServerIds.add(
            replicationServer.getReplicationServerId());
        replicationServerCreated = false;
      }

      Set<String> servers = replicationServer.getReplicationServer();
      if (servers == null)
      {
        servers = new HashSet<String>();
      }
      Set<String> oldServers = new HashSet<String>(servers);
      for (Set<String> rs : replicationServers.values())
      {
        servers.addAll(rs);
      }

      replicationServer.setReplicationServer(servers);
      replicationServer.commit();

      Set<String> newReplicationServers = intersect(servers, oldServers);

      /*
       * Create the domains
       */
      String[] domainNames = sync.listReplicationDomains();
      if (domainNames == null)
      {
        domainNames = new String[]{};
      }
      Set<ConfiguredDomain> domainsConf = new HashSet<ConfiguredDomain>();
      ReplicationDomainCfgClient[] domains =
        new ReplicationDomainCfgClient[domainNames.length];
      for (int i=0; i<domains.length; i++)
      {
        domains[i] = sync.getReplicationDomain(domainNames[i]);
      }
      for (String dn : replicationServers.keySet())
      {
        ReplicationDomainCfgClient domain = null;
        boolean isCreated;
        String domainName = null;
        for (int i = 0; i < domains.length && domain == null; i++)
        {
          if (areDnsEqual(dn,
              domains[i].getBaseDN().toString()))
          {
            domain = domains[i];
            domainName = domainNames[i];
          }
        }
        if (domain == null)
        {
          int domainId = getReplicationId(usedServerIds);
          usedServerIds.add(domainId);
          domainName = getDomainName(domainNames, domainId, dn);
          domain = sync.createReplicationDomain(
              ReplicationDomainCfgDefn.getInstance(), domainName,
              new ArrayList<PropertyException>());
          domain.setServerId(domainId);
          domain.setBaseDN(DN.valueOf(dn));
          isCreated = true;
        }
        else
        {
          isCreated = false;
        }
        oldServers = domain.getReplicationServer();
        if (oldServers == null)
        {
          oldServers = new TreeSet<String>();
        }
        servers = replicationServers.get(dn);
        domain.setReplicationServer(servers);
        usedServerIds.add(domain.getServerId());

        domain.commit();
        Set<String> addedServers = intersect(servers, oldServers);
        ConfiguredDomain domainConf = new ConfiguredDomain(domainName,
            isCreated, addedServers);
        domainsConf.add(domainConf);
      }
      return new ConfiguredReplication(synchProviderCreated,
          synchProviderEnabled, replicationServerCreated,
          secureReplicationEnabled, newReplicationServers,
          domainsConf);
    }
    catch (Throwable t)
    {
      throw new ApplicationException(
          ReturnCode.CONFIGURATION_ERROR,
          INFO_ERROR_CONFIGURING_REMOTE_GENERIC.get(serverDisplay, t),
          t);
    }
  }

  private Set<String> intersect(Set<String> set1, Set<String> set2)
  {
    Set<String> result = new TreeSet<String>(set1);
    result.removeAll(set2);
    return result;
  }

  /**
   * Configures the replication on a given server.
   *
   * @param remoteCtx
   *          the connection to the server where we want to configure the
   *          replication.
   * @param replConf
   *          the object describing what was configured.
   * @param serverDisplay
   *          the server display.
   * @throws ApplicationException
   *           if something goes wrong.
   */
  public void unconfigureReplication(InitialLdapContext remoteCtx, ConfiguredReplication replConf, String serverDisplay)
      throws ApplicationException
  {
    try
    {
      ManagementContext mCtx = LDAPManagementContext.createFromContext(JNDIDirContextAdaptor.adapt(remoteCtx));
      RootCfgClient root = mCtx.getRootConfiguration();
      final String syncProvider = "Multimaster Synchronization";
      // Unconfigure Synchronization plugin.
      if (replConf.isSynchProviderCreated())
      {
        try
        {
          root.removeSynchronizationProvider(syncProvider);
        }
        catch (ManagedObjectNotFoundException monfe)
        {
          // It does not exist.
        }
      }
      else
      {
        try
        {
          ReplicationSynchronizationProviderCfgClient sync =
              (ReplicationSynchronizationProviderCfgClient) root.getSynchronizationProvider(syncProvider);
          if (replConf.isSynchProviderEnabled())
          {
            sync.setEnabled(Boolean.FALSE);
          }

          if (replConf.isReplicationServerCreated())
          {
            sync.removeReplicationServer();
          }
          else if (sync.hasReplicationServer())
          {
            ReplicationServerCfgClient replicationServer = sync.getReplicationServer();
            Set<String> replServers = replicationServer.getReplicationServer();
            if (replServers != null)
            {
              replServers.removeAll(replConf.getNewReplicationServers());
              replicationServer.setReplicationServer(replServers);
              replicationServer.commit();
            }
          }

          for (ConfiguredDomain domain : replConf.getDomainsConf())
          {
            if (domain.isCreated())
            {
              sync.removeReplicationDomain(domain.getDomainName());
            }
            else
            {
              try
              {
                ReplicationDomainCfgClient d = sync.getReplicationDomain(domain.getDomainName());
                Set<String> replServers = d.getReplicationServer();
                if (replServers != null)
                {
                  replServers.removeAll(domain.getAddedReplicationServers());
                  d.setReplicationServer(replServers);
                  d.commit();
                }
              }
              catch (ManagedObjectNotFoundException monfe)
              {
                // It does not exist.
              }
            }
          }
          sync.commit();
        }
        catch (ManagedObjectNotFoundException monfe)
        {
          // It does not exist.
        }
      }

      if (replConf.isSecureReplicationEnabled())
      {
        CryptoManagerCfgClient crypto = root.getCryptoManager();
        if (crypto.isSSLEncryption())
        {
          crypto.setSSLEncryption(false);
          crypto.commit();
        }
      }
    }
    catch (Throwable t)
    {
      throw new ApplicationException(ReturnCode.CONFIGURATION_ERROR, INFO_ERROR_CONFIGURING_REMOTE_GENERIC.get(
          serverDisplay, t), t);
    }
  }

  /**
   * For the given state provided by a Task tells if the task is done or not.
   *
   * @param sState
   *          the String representing the task state.
   * @return <CODE>true</CODE> if the task is done and <CODE>false</CODE>
   *         otherwise.
   */
  public boolean isDone(String sState)
  {
    return TaskState.isDone(TaskState.fromString(sState));
  }

  /**
   * For the given state provided by a Task tells if the task is successful or
   * not.
   *
   * @param sState
   *          the String representing the task state.
   * @return <CODE>true</CODE> if the task is successful and <CODE>false</CODE>
   *         otherwise.
   */
  public boolean isSuccessful(String sState)
  {
    return TaskState.isSuccessful(TaskState.fromString(sState));
  }

  /**
   * For the given state provided by a Task tells if the task is complete with
   * errors or not.
   *
   * @param sState
   *          the String representing the task state.
   * @return <CODE>true</CODE> if the task is complete with errors and
   *         <CODE>false</CODE> otherwise.
   */
  public boolean isCompletedWithErrors(String sState)
  {
    return TaskState.COMPLETED_WITH_ERRORS == TaskState.fromString(sState);
  }

  /**
   * For the given state provided by a Task tells if the task is stopped by
   * error or not.
   *
   * @param sState
   *          the String representing the task state.
   * @return <CODE>true</CODE> if the task is stopped by error and
   *         <CODE>false</CODE> otherwise.
   */
  public boolean isStoppedByError(String sState)
  {
    return TaskState.STOPPED_BY_ERROR == TaskState.fromString(sState);
  }

  /**
   * Tells whether the provided log message corresponds to a peers not found
   * error during the initialization of a replica or not.
   *
   * @param logMsg
   *          the log message.
   * @return <CODE>true</CODE> if the log message corresponds to a peers not
   *         found error during initialization and <CODE>false</CODE> otherwise.
   */
  public boolean isPeersNotFoundError(String logMsg)
  {
    return logMsg.contains("=" + ReplicationMessages.ERR_NO_REACHABLE_PEER_IN_THE_DOMAIN.ordinal());
  }

  /**
   * Returns the ID to be used for a new replication server or domain.
   * @param usedIds the list of already used ids.
   * @return the ID to be used for a new replication server or domain.
   */
  public static int getReplicationId(Set<Integer> usedIds)
  {
    Random r = new Random();
    int id = 0;
    while (id == 0 || usedIds.contains(id))
    {
      id = r.nextInt(MAX_ID_VALUE);
    }
    return id;
  }

  /**
   * Returns the name to be used for a new replication domain.
   * @param existingDomains the existing domains names.
   * @param newDomainId the new domain replication id.
   * @param baseDN the base DN of the domain.
   * @return the name to be used for a new replication domain.
   */
  public static String getDomainName(String[] existingDomains, int newDomainId,
      String baseDN)
  {
    String domainName = baseDN;
    boolean nameExists = true;
    int j = 0;
    while (nameExists)
    {
      boolean found = false;
      for (int i=0; i<existingDomains.length && !found; i++)
      {
        found = existingDomains[i].equalsIgnoreCase(domainName);
      }
      if (found)
      {
        domainName = baseDN+"-"+j;
      }
      else
      {
        nameExists = false;
      }
      j++;
    }
    return domainName;
  }

  /**
   * Writes the set-java-home file that is used by the scripts to set the java
   * home and the java arguments.
   *
   * @param uData
   *          the data provided by the user.
   * @param installPath
   *          where the server is installed.
   * @throws IOException
   *           if an error occurred writing the file.
   */
  public void writeSetOpenDSJavaHome(UserData uData, String installPath) throws IOException
  {
    String javaHome = System.getProperty("java.home");
    if (javaHome == null || javaHome.length() == 0)
    {
      javaHome = System.getenv(SetupUtils.OPENDJ_JAVA_HOME);
    }

    // Try to transform things if necessary.  The following map has as key
    // the original JavaArgument object and as value the 'transformed' JavaArgument.
    Map<JavaArguments, JavaArguments> hmJavaArguments = new HashMap<JavaArguments, JavaArguments>();
    for (String script : uData.getScriptNamesForJavaArguments())
    {
      JavaArguments origJavaArguments = uData.getJavaArguments(script);
      if (hmJavaArguments.get(origJavaArguments) == null)
      {
        if (Utils.supportsOption(origJavaArguments.getStringArguments(), javaHome, installPath))
        {
          // The argument works, so just use it.
          hmJavaArguments.put(origJavaArguments, origJavaArguments);
        }
        else
        {
          // We have to fix it somehow: test separately memory and other
          // arguments to see if something works.
          JavaArguments transformedArguments = getBestEffortArguments(origJavaArguments, javaHome, installPath);
          hmJavaArguments.put(origJavaArguments, transformedArguments);
        }
      }
      // else, support is already checked.
    }

    Properties fileProperties = getJavaPropertiesFileContents(getPropertiesFileName(installPath));
    Map<String, JavaArguments> args = new HashMap<String, JavaArguments>();
    Map<String, String> otherProperties = new HashMap<String, String>();

    for (String script : uData.getScriptNamesForJavaArguments())
    {
      JavaArguments origJavaArgument = uData.getJavaArguments(script);
      JavaArguments transformedJavaArg = hmJavaArguments.get(origJavaArgument);
      JavaArguments defaultJavaArg = uData.getDefaultJavaArguments(script);

      // Apply the following policy: overwrite the values in the file only
      // if the values provided by the user are not the default ones.
      String propertiesKey = getJavaArgPropertyForScript(script);
      if (origJavaArgument.equals(defaultJavaArg) && fileProperties.containsKey(propertiesKey))
      {
        otherProperties.put(propertiesKey, fileProperties.getProperty(propertiesKey));
      }
      else
      {
        args.put(script, transformedJavaArg);
      }
    }

    putBooleanPropertyFrom("overwrite-env-java-home", fileProperties, otherProperties);
    putBooleanPropertyFrom("overwrite-env-java-args", fileProperties, otherProperties);

    if (!fileProperties.containsKey("default.java-home"))
    {
      otherProperties.put("default.java-home", javaHome);
    }

    writeSetOpenDSJavaHome(installPath, javaHome, args, otherProperties);
  }

  private void putBooleanPropertyFrom(
      final String propertyName, final Properties propertiesSource, final Map<String, String> destMap)
  {
    final String propertyValue = propertiesSource.getProperty(propertyName);
    if (propertyValue == null || !("true".equalsIgnoreCase(propertyValue) || "false".equalsIgnoreCase(propertyValue)))
    {
      destMap.put(propertyName, "false");
    }
    else
    {
      destMap.put("overwrite-env-java-home", propertyValue.toLowerCase());
    }
  }

  /**
   * Tries to figure out a new JavaArguments object that works, based on the
   * provided JavaArguments. It is more efficient to call this method if we are
   * sure that the provided JavaArguments object does not work.
   *
   * @param origJavaArguments
   *          the java arguments that does not work.
   * @param javaHome
   *          the java home to be used to test the java arguments.
   * @param installPath
   *          the install path.
   * @return a working JavaArguments object.
   */
  private JavaArguments getBestEffortArguments(JavaArguments origJavaArguments, String javaHome, String installPath)
  {
    JavaArguments memArgs = new JavaArguments();
    memArgs.setInitialMemory(origJavaArguments.getInitialMemory());
    memArgs.setMaxMemory(origJavaArguments.getMaxMemory());
    String m = memArgs.getStringArguments();
    boolean supportsMemory = false;
    if (m.length() > 0)
    {
      supportsMemory = Utils.supportsOption(m, javaHome, installPath);
    }

    JavaArguments additionalArgs = new JavaArguments();
    additionalArgs.setAdditionalArguments(origJavaArguments.getAdditionalArguments());
    String a = additionalArgs.getStringArguments();
    boolean supportsAdditional = false;
    if (a.length() > 0)
    {
      supportsAdditional = Utils.supportsOption(a, javaHome, installPath);
    }

    JavaArguments javaArgs = new JavaArguments();
    if (supportsMemory)
    {
      javaArgs.setInitialMemory(origJavaArguments.getInitialMemory());
      javaArgs.setMaxMemory(origJavaArguments.getMaxMemory());
    }
    else
    {
      // Try to figure out a smaller amount of memory.
      long currentMaxMemory = Runtime.getRuntime().maxMemory();
      int maxMemory = origJavaArguments.getMaxMemory();
      if (maxMemory != -1)
      {
        maxMemory = maxMemory / 2;
        while (ONE_MEGABYTE * maxMemory < currentMaxMemory
            && !Utils.supportsOption(JavaArguments.getMaxMemoryArgument(maxMemory), javaHome, installPath))
        {
          maxMemory = maxMemory / 2;
        }

        if (ONE_MEGABYTE * maxMemory > currentMaxMemory)
        {
          // Supports this option.
          javaArgs.setMaxMemory(maxMemory);
        }
      }
    }
    if (supportsAdditional)
    {
      javaArgs.setAdditionalArguments(origJavaArguments.getAdditionalArguments());
    }
    return javaArgs;
  }

  private List<String> getJavaPropertiesFileComments(String propertiesFile) throws IOException
  {
    ArrayList<String> commentLines = new ArrayList<String>();
    BufferedReader reader = new BufferedReader(new FileReader(propertiesFile));
    String line;
    while ((line = reader.readLine()) != null)
    {
      String trimmedLine = line.trim();
      if (trimmedLine.startsWith("#") || trimmedLine.length() == 0)
      {
        commentLines.add(line);
      }
      else
      {
        break;
      }
    }
    return commentLines;
  }

  private Properties getJavaPropertiesFileContents(String propertiesFile) throws IOException
  {
    FileInputStream fs = null;
    Properties fileProperties = new Properties();
    try
    {
      fs = new FileInputStream(propertiesFile);
      fileProperties.load(fs);
    }
    catch (Throwable t)
    { /* do nothing */
    }
    finally
    {
      StaticUtils.close(fs);
    }
    return fileProperties;
  }

  private String getPropertiesFileName(String installPath)
  {
    String configDir = Utils.getPath(
        Utils.getInstancePathFromInstallPath(installPath), Installation.CONFIG_PATH_RELATIVE);
    return Utils.getPath(configDir, Installation.DEFAULT_JAVA_PROPERTIES_FILE);
  }

  /**
   * Writes the set-java-home file that is used by the scripts to set the java
   * home and the java arguments. Since the set-java-home file is created and
   * may be changed, it's created under the instancePath.
   *
   * @param installPath
   *          the install path of the server.
   * @param javaHome
   *          the java home to be used.
   * @param arguments
   *          a Map containing as key the name of the script and as value, the
   *          java arguments to be set for the script.
   * @param otherProperties
   *          other properties that must be set in the file.
   * @throws IOException
   *           if an error occurred writing the file.
   */
  private void writeSetOpenDSJavaHome(String installPath, String javaHome, Map<String, JavaArguments> arguments,
      Map<String, String> otherProperties) throws IOException
  {
    String propertiesFile = getPropertiesFileName(installPath);
    List<String> commentLines = getJavaPropertiesFileComments(propertiesFile);
    BufferedWriter writer = new BufferedWriter(new FileWriter(propertiesFile, false));

    for (String line: commentLines)
    {
      writer.write(line);
      writer.newLine();
    }

    for (String key : otherProperties.keySet())
    {
      writer.write(key + "=" + otherProperties.get(key));
      writer.newLine();
    }

    for (String scriptName : arguments.keySet())
    {
      String argument = arguments.get(scriptName).getStringArguments();
      writer.newLine();
      writer.write(getJavaArgPropertyForScript(scriptName) + "=" + argument);
    }
    writer.close();

    String libDir = Utils.getPath(
        Utils.getInstancePathFromInstallPath(installPath), Installation.LIBRARIES_PATH_RELATIVE);
    // Create directory if it doesn't exist yet
    File fLib = new File(libDir);
    if (!fLib.exists())
    {
      fLib.mkdir();
    }
    final String destinationFile = Utils.getPath(libDir, isWindows() ? Installation.SET_JAVA_PROPERTIES_FILE_WINDOWS
                                                                     : Installation.SET_JAVA_PROPERTIES_FILE_UNIX);
    // Launch the script
    String[] args =
    {
        "--propertiesFile", propertiesFile,
        "--destinationFile", destinationFile,
        "--quiet"
    };

    int returnValue = JavaPropertiesTool.mainCLI(args);
    if (JavaPropertiesTool.ErrorReturnCode.SUCCESSFUL.getReturnCode() != returnValue &&
        JavaPropertiesTool.ErrorReturnCode.SUCCESSFUL_NOP.getReturnCode() != returnValue)
    {
      logger.warn(LocalizableMessage.raw("Error creating java home scripts, error code: " + returnValue));
      throw new IOException(ERR_ERROR_CREATING_JAVA_HOME_SCRIPTS.get(returnValue).toString());
    }
  }

  /**
   * Returns the java argument property for a given script.
   *
   * @param scriptName
   *          the script name.
   * @return the java argument property for a given script.
   */
  private static String getJavaArgPropertyForScript(String scriptName)
  {
    return scriptName + ".java-args";
  }

  /**
   * If the log message is of type "[03/Apr/2008:21:25:43 +0200] category=JEB
   * severity=NOTICE msgID=8847454 Processed 1 entries, imported 0, skipped 1,
   * rejected 0 and migrated 0 in 1 seconds (average rate 0.0/sec)" returns the
   * message part. Returns <CODE>null</CODE> otherwise.
   *
   * @param msg
   *          the message to be parsed.
   * @return the parsed import message.
   */
  public String getImportProgressMessage(String msg)
  {
    if (msg != null && (msg.contains("msgID=" + BackendMessages.NOTE_IMPORT_FINAL_STATUS.ordinal())
                        || msg.contains("msgID=" + BackendMessages.NOTE_IMPORT_PROGRESS_REPORT.ordinal())))
    {
      int index = msg.indexOf("msg=");
      if (index != -1)
      {
        return msg.substring(index + 4);
      }
    }
    return null;
  }
}
