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
 * Copyright 2016 ForgeRock AS.
 */
package org.opends.server.util.embedded;

import static org.opends.messages.UtilityMessages.ERR_EMBEDDED_SERVER_LDIF_MANAGEMENT_CONTEXT;
import static org.opends.server.util.ServerConstants.*;
import static org.forgerock.opendj.config.client.ldap.LDAPManagementContext.newManagementContext;
import static org.forgerock.opendj.config.client.ldap.LDAPManagementContext.newLDIFManagementContext;
import static org.opends.messages.UtilityMessages.*;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.adapter.server3x.Adapters;
import org.forgerock.opendj.config.LDAPProfile;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SimpleBindRequest;
import org.forgerock.util.Options;
import org.forgerock.util.Reject;
import org.opends.quicksetup.TempLogFile;
import org.opends.server.config.ConfigConstants;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.tools.ImportLDIF;
import org.opends.server.tools.InstallDS;
import org.opends.server.tools.RebuildIndex;
import org.opends.server.tools.dsreplication.ReplicationCliMain;
import org.opends.server.tools.upgrade.UpgradeCli;
import org.opends.server.types.DirectoryEnvironmentConfig;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.util.DynamicConstants;
import org.opends.server.util.StaticUtils;

/**
 * Represents an embedded directory server on which high-level operations
 * are available (setup, upgrade, start, stop, ...).
 */
public class EmbeddedDirectoryServer
{
  private static final String EMBEDDED_OPEN_DJ_PREFIX = "embeddedOpenDJ";
  private static final String ARCHIVE_ROOT_DIRECTORY = DynamicConstants.SHORT_NAME.toLowerCase();
  private static final String QUICKSETUP_ROOT_PROPERTY = "org.opends.quicksetup.Root";
  private static final String QUICKSETUP_INSTANCE_PROPERTY = "org.opends.quicksetup.instance";

  /** The parameters for install and instance directories, and configuration file of the server. */
  private final ConfigParameters configParams;

  /** The connection parameters for the server. */
  private final ConnectionParameters connectionParams;

  /** The connection factory to get connections to the server. */
  private final LDAPConnectionFactory ldapConnectionFactory;

  /** The output stream used for feedback during operations on server. */
  private final OutputStream outStream;

  /** The error stream used for feedback during operations on server. */
  private final OutputStream errStream;

  private EmbeddedDirectoryServer(ConfigParameters configParams, ConnectionParameters connParams,
      OutputStream out, OutputStream err)
  {
    Reject.ifNull(configParams.getServerRootDirectory());
    if (connParams != null)
    {
      Reject.ifNull(
        connParams.getHostName(),
        connParams.getLdapPort(),
        connParams.getBindDn(),
        connParams.getBindPassword());
    }
    if (configParams.getConfigurationFile() == null)
    {
      // use the default path if configuration file is not provided
      configParams.configurationFile(getDefaultConfigurationFilePath(configParams.getServerRootDirectory()));
    }
    this.configParams = configParams;
    this.connectionParams = connParams;
    this.outStream = out;
    this.errStream = err;
    if (connectionParams != null)
    {
      SimpleBindRequest authRequest = Requests.newSimpleBindRequest(
          connectionParams.getBindDn(), connectionParams.getBindPassword().toCharArray());
      ldapConnectionFactory = new LDAPConnectionFactory(
          connectionParams.getHostName(),
          connectionParams.getLdapPort(),
          Options.defaultOptions().set(LDAPConnectionFactory.AUTHN_BIND_REQUEST, authRequest));
    }
    else
    {
      ldapConnectionFactory = null;
    }
  }

  private EmbeddedDirectoryServer(ConfigParameters configParams, ConnectionParameters connParams)
  {
    this(configParams, connParams, System.out, System.err);
  }

  private static String getDefaultConfigurationFilePath(String serverRootDirectory)
  {
    return Paths.get(serverRootDirectory)
        .resolve(ConfigConstants.CONFIG_DIR_NAME)
        .resolve(ConfigConstants.CONFIG_FILE_NAME)
        .toString();
  }

  /**
   * Creates an instance of an embedded directory server for any operation.
   *
   * @param configParams
   *          The basic configuration parameters for the server.
   * @param connParams
   *          The connection parameters for the server.
   * @param out
   *          Output stream used for feedback during operations on server
   * @param err
   *          Error stream used for feedback during operations on server
   * @return the embedded directory server
   */
  public static EmbeddedDirectoryServer manageEmbeddedDirectoryServer(ConfigParameters configParams,
      ConnectionParameters connParams, OutputStream out, OutputStream err)
  {
    return new EmbeddedDirectoryServer(configParams, connParams, out, err);
  }

  /**
   * Creates an instance of an embedded directory server for start/stop operation.
   * <p>
   * To be able to perform any operation on the server, use the alternative {@code defineServer()}
   * method.
   *
   * @param configParams
   *          The basic configuration parameters for the server.
   * @param out
   *          Output stream used for feedback during operations on server
   * @param err
   *          Error stream used for feedback during operations on server
   * @return the directory server
   */
  public static EmbeddedDirectoryServer manageEmbeddedDirectoryServerForStartStop(ConfigParameters configParams,
      OutputStream out, OutputStream err)
  {
    return new EmbeddedDirectoryServer(configParams, null, out, err);
  }

  /**
   * Creates an instance of an embedded directory server for start/stop operation.
   * <p>
   * To be able to perform any operation on the server, use the alternative {@code defineServer()}
   * method.
   *
   * @param configParams
   *          The basic configuration parameters for the server.
   * @return the directory server
   */
  public static EmbeddedDirectoryServer manageEmbeddedDirectoryServerForStartStop(
      ConfigParameters configParams)
  {
    return new EmbeddedDirectoryServer(configParams, null, System.out, System.err);
  }

  /**
   * Configures replication between this directory server (first server) and another server
   * (second server).
   * <p>
   * This method updates the configuration of the servers to replicate the data under the
   * base DN specified in the parameters.
   *
   * @param parameters
   *            The parameters for the replication.
   * @throws EmbeddedDirectoryServerException
   *            If a problem occurs.
   */
  public void configureReplication(ReplicationParameters parameters) throws EmbeddedDirectoryServerException
  {
    Reject.checkNotNull(connectionParams);
    int returnCode = ReplicationCliMain.mainCLI(
        parameters.toCommandLineArgumentsConfiguration(configParams.getConfigurationFile(), connectionParams),
        !isRunning(), outStream, errStream);

    if (returnCode != 0)
    {
      throw new EmbeddedDirectoryServerException(ERR_EMBEDDED_SERVER_CONFIGURE_REPLICATION.get(
          configParams.getServerRootDirectory(), parameters.getReplicationPortSource(),
          parameters.getHostnameDestination(), parameters.getReplicationPortDestination(), returnCode));
    }
  }

  /**
   * Returns the configuration of this server, which can be read or updated.
   * <p>
   * The returned object is an instance of {@code ManagementContext} class, which allow access to the
   * root configuration object using its {@code getRootConfiguration()} method.
   * Starting from the root configuration, it is possible to access any configuration object, in order
   * to perform read or update operations.
   * <p>
   * Note that {@code ManagementContext} instance must be closed after usage. It is recommended to use
   * it inside a try-with-resource statement.
   * <p>
   * Example reading configuration:
   * <pre>
   *   try(ManagementContext config = server.getConfiguration()) {
   *      List<String> syncProviders = config.getRootConfiguration().listSynchronizationProviders();
   *      System.out.println("sync providers=" + syncProviders);
   *   }
   * </pre>
   * <p>
   * Example updating configuration:
   * <pre>
   *   try(ManagementContext config = server.getConfiguration()) {
   *      JEBackendCfgClient userRoot = (JEBackendCfgClient) config.getRootConfiguration().getBackend("userRoot");
   *      userRoot.setBaseDN(Arrays.asList(DN.valueOf("dc=example,dc=com")));
   *      userRoot.setDBCachePercent(70);
   *      // changes must be committed to be effective
   *      userRoot.commit();
   *   }
   * </pre>
   * @return the management context object which gives direct access to the root configuration of the server
   * @throws EmbeddedDirectoryServerException
   *            If the retrieval of the configuration fails
   */
  @SuppressWarnings("resource")
  public ManagementContext getConfiguration() throws EmbeddedDirectoryServerException
  {
    try
    {
      if (isRunning())
      {
        Connection ldapConnection = ldapConnectionFactory.getConnection();
        return newManagementContext(ldapConnection, LDAPProfile.getInstance());
      }
      return newLDIFManagementContext(new File(configParams.getConfigurationFile()));
    }
    catch (IOException e)
    {
      throw new EmbeddedDirectoryServerException(
          ERR_EMBEDDED_SERVER_LDIF_MANAGEMENT_CONTEXT.get(configParams.getConfigurationFile()));
    }
  }

  /**
   * Returns an internal connection to the server that will be authenticated as the specified user.
   *
   * @param userDn
   *            The user to be used for authentication to the server
   * @return the connection
   * @throws EmbeddedDirectoryServerException
   *            If the connection can't be returned
   */
  public Connection getInternalConnection(DN userDn) throws EmbeddedDirectoryServerException
  {
    try
    {
      return Adapters.newConnection(new InternalClientConnection(userDn));
    }
    catch (DirectoryException e)
    {
      throw new EmbeddedDirectoryServerException(ERR_EMBEDDED_SERVER_INTERNAL_CONNECTION.get(userDn));
    }
  }

  /**
   * Imports LDIF data to the directory server, overwriting existing data.
   * <p>
   * The import is implemented only for online use.
   *
   * @param parameters
   *            The import parameters.
   * @throws EmbeddedDirectoryServerException
   *            If the import fails
   */
  public void importLDIF(ImportParameters parameters) throws EmbeddedDirectoryServerException
  {
    checkServerIsRunning();
    Reject.checkNotNull(connectionParams);    int returnCode = ImportLDIF.mainImportLDIF(
        parameters.toCommandLineArguments(configParams.getConfigurationFile(), connectionParams),
        !isRunning(), outStream, errStream);

    if (returnCode != 0)
    {
      throw new EmbeddedDirectoryServerException(ERR_EMBEDDED_SERVER_IMPORT_DATA.get(
          parameters.getLdifFile(), configParams.getServerRootDirectory(), returnCode));
    }
  }

  /**
   * Initializes replication between this server and another server.
   *
   * @param parameters
   *            The parameters for the replication.
   * @throws EmbeddedDirectoryServerException
   *            If a problem occurs.
   */
  public void initializeReplication(ReplicationParameters parameters) throws EmbeddedDirectoryServerException
  {
    Reject.checkNotNull(connectionParams);
    int returnCode = ReplicationCliMain.mainCLI(
        parameters.toCommandLineArgumentsInitialize(configParams.getConfigurationFile(), connectionParams),
        !isRunning(), outStream, errStream);

    if (returnCode != 0)
    {
      throw new EmbeddedDirectoryServerException(ERR_EMBEDDED_SERVER_INITIALIZE_REPLICATION.get(
          configParams.getServerRootDirectory(), connectionParams.getAdminPort(), parameters.getHostnameDestination(),
          parameters.getAdminPortDestination(), returnCode));
    }
  }

  /**
   * Indicates whether replication is currently running for the embedded server.
   *
   * @param parameters
   *            The parameters for the replication.
   * @return {@code true} if replication is running, {@code false} otherwise
   */
  public boolean isReplicationRunning(ReplicationParameters parameters)
  {
    Reject.checkNotNull(connectionParams);
    int returnCode = ReplicationCliMain.mainCLI(
        parameters.toCommandLineArgumentsStatus(configParams.getConfigurationFile(), connectionParams),
        !isRunning(), outStream, errStream);
    return returnCode == 0;
  }

  /**
   * Indicates whether this server is currently running.
   *
   * @return {@code true} if the server is currently running, or {@code false} if not.
   */
  public boolean isRunning()
  {
    return DirectoryServer.isRunning();
  }

  /**
   * Set this server up from the root directory.
   * <p>
   * As a pre-requisite, the OpenDJ archive must have been previously extracted to some
   * directory. To perform a setup directly from an archive, see {@code setupFromArchive()}.
   *
   * @param parameters
   *            The setup parameters.
   * @throws EmbeddedDirectoryServerException
   *            If the setup fails for any reason.
   */
  public void setup(SetupParameters parameters) throws EmbeddedDirectoryServerException
  {
    Reject.checkNotNull(connectionParams);
    System.setProperty(PROPERTY_SERVER_ROOT, configParams.getServerRootDirectory());
    System.setProperty(QUICKSETUP_ROOT_PROPERTY, configParams.getServerRootDirectory());
    String instanceDir = configParams.getServerInstanceDirectory() != null ?
        configParams.getServerInstanceDirectory() :
        configParams.getServerRootDirectory();
    System.setProperty(QUICKSETUP_INSTANCE_PROPERTY, instanceDir);
    System.setProperty(PROPERTY_INSTANCE_ROOT, instanceDir);

    int returnCode = InstallDS.mainCLI(parameters.toCommandLineArguments(connectionParams), outStream, errStream,
        TempLogFile.newTempLogFile(EMBEDDED_OPEN_DJ_PREFIX));

    if (returnCode != 0)
    {
      throw new EmbeddedDirectoryServerException(ERR_EMBEDDED_SERVER_SETUP.get(
          configParams.getServerRootDirectory(),  parameters.getBaseDn(), parameters.getBackendType(), returnCode));
    }
  }

  /**
   * Extracts the provided archive to the appropriate root directory of the server.
   * <p>
   * As the DJ archive includes the "opendj" directory, it is mandatory to have
   * the root directory named after it when using this method.
   *
   * @param openDJZipFile
   *            The OpenDJ server archive.
   * @throws EmbeddedDirectoryServerException
   *            If the extraction of the archive fails.
   */
  public void extractArchiveForSetup(File openDJZipFile) throws EmbeddedDirectoryServerException
  {
    Reject.checkNotNull(connectionParams);
    try
    {
      File serverRoot = new File(configParams.getServerRootDirectory());
      if (!ARCHIVE_ROOT_DIRECTORY.equals(serverRoot.getName()))
      {
        throw new EmbeddedDirectoryServerException(
            ERR_EMBEDDED_SERVER_ARCHIVE_SETUP_WRONG_ROOT_DIRECTORY.get(ARCHIVE_ROOT_DIRECTORY, serverRoot));
      }
      // the directory where the zip file is extracted should be one level up from the server root.
      File deployDirectory = serverRoot.getParentFile();
      StaticUtils.extractZipArchive(openDJZipFile, deployDirectory);
    }
    catch (IOException e)
    {
      throw new EmbeddedDirectoryServerException(ERR_EMBEDDED_SERVER_SETUP_EXTRACT_ARCHIVE.get(
          openDJZipFile, configParams.getServerRootDirectory(), StaticUtils.stackTraceToSingleLineString(e)));
    }
  }

  /**
   * Rebuilds all the indexes of this server.
   * <p>
   * This operation is done offline, hence the server must be stopped when calling this method.
   *
   * @param parameters
   *            The parameters for rebuilding the indexes.
   * @throws EmbeddedDirectoryServerException
   *            If an error occurs.
   */
  public void rebuildIndex(RebuildIndexParameters parameters) throws EmbeddedDirectoryServerException
  {
    checkServerIsNotRunning();
    int returnCode = RebuildIndex.mainRebuildIndex(
        parameters.toCommandLineArguments(configParams.getConfigurationFile()), !isRunning(), outStream, errStream);

    if (returnCode != 0)
    {
      throw new EmbeddedDirectoryServerException(ERR_EMBEDDED_SERVER_REBUILD_INDEX.get(
          configParams.getServerRootDirectory(), returnCode));
    }
  }

  /**
   * Restarts the directory server.
   * <p>
   * This will perform an in-core restart in which the existing server instance will be shut down, a
   * new instance will be created, and it will be reinitialized and restarted.
   *
   * @param className
   *          The name of the class that initiated the restart.
   * @param reason
   *          A message explaining the reason for the restart.
   */
  public void restart(String className, LocalizableMessage reason)
  {
    DirectoryServer.restart(className, reason, DirectoryServer.getEnvironmentConfig());
  }

  /**
   * Starts this server.
   *
   * @throws EmbeddedDirectoryServerException
   *           If the server is already running, or if an error occurs during server initialization
   *           or startup.
   */
  public void start() throws EmbeddedDirectoryServerException
  {
    if (DirectoryServer.isRunning())
    {
      throw new EmbeddedDirectoryServerException(ERR_EMBEDUTILS_SERVER_ALREADY_RUNNING.get(
          configParams.getServerRootDirectory()));
    }

    try
    {
      DirectoryServer directoryServer = DirectoryServer.reinitialize(createEnvironmentConfig());
      directoryServer.startServer();
    }
    catch (InitializationException | ConfigException e)
    {
      throw new EmbeddedDirectoryServerException(ERR_EMBEDDED_SERVER_START.get(
          configParams.getServerRootDirectory(), StaticUtils.stackTraceToSingleLineString(e)));
    }
  }

  private DirectoryEnvironmentConfig createEnvironmentConfig() throws InitializationException
  {
    // If server root directory or instance directory are not defined,
    // the DirectoryEnvironmentConfig class has several ways to find the values,
    // including using system properties.
    DirectoryEnvironmentConfig env = new DirectoryEnvironmentConfig();
    if (configParams.getServerRootDirectory() != null)
    {
      env.setServerRoot(new File(configParams.getServerRootDirectory()));
    }
    if (configParams.getServerInstanceDirectory() != null)
    {
      env.setInstanceRoot(new File(configParams.getServerInstanceDirectory()));
    }
    env.setForceDaemonThreads(true);
    env.setDisableConnectionHandlers(configParams.isDisableConnectionHandlers());
    env.setConfigFile(new File(configParams.getConfigurationFile()));
    return env;
  }

  /**
   * Stops this server.
   *
   * @param className
   *          The name of the class that initiated the shutdown.
   * @param reason
   *          A message explaining the reason for the shutdown.
   */
  public void stop(String className, LocalizableMessage reason)
  {
    DirectoryServer.shutDown(className, reason);
  }

  /**
   * Upgrades this server.
   * <p>
   * Upgrades the server configuration and application data so that it is compatible
   * with the installed binaries.
   *
   * @param parameters
   *          The upgrade parameters.
   * @throws EmbeddedDirectoryServerException
   *            If the upgrade fails
   */
  public void upgrade(UpgradeParameters parameters) throws EmbeddedDirectoryServerException
  {
    int returnCode = UpgradeCli.main(parameters.toCommandLineArguments(
            configParams.getConfigurationFile()), !isRunning(), outStream, errStream);

    if (returnCode != 0)
    {
      throw new EmbeddedDirectoryServerException(ERR_EMBEDDED_SERVER_UPGRADE.get(
          configParams.getServerRootDirectory(), returnCode));
    }
  }

  private void checkServerIsRunning() throws EmbeddedDirectoryServerException
  {
    if (!isRunning())
    {
      throw new EmbeddedDirectoryServerException(ERR_EMBEDDED_SERVER_IMPORT_DATA_SERVER_IS_NOT_RUNNING.get());
    }
  }

  private void checkServerIsNotRunning() throws EmbeddedDirectoryServerException
  {
    if (isRunning())
    {
      throw new EmbeddedDirectoryServerException(ERR_EMBEDDED_SERVER_REBUILD_INDEX_SERVER_IS_RUNNING.get());

    }
  }
}
