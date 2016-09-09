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

import static org.opends.server.util.ServerConstants.*;
import static org.forgerock.opendj.config.client.ldap.LDAPManagementContext.newManagementContext;
import static org.forgerock.opendj.config.client.ldap.LDAPManagementContext.newLDIFManagementContext;
import static org.opends.messages.UtilityMessages.*;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

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
import org.forgerock.opendj.server.config.client.RootCfgClient;
import org.forgerock.util.Options;
import org.forgerock.util.Pair;
import org.opends.quicksetup.TempLogFile;
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
import org.opends.server.util.StaticUtils;

/**
 * Represents an embedded directory server on which high-level operations
 * are available (setup, upgrade, start, stop, ...).
 */
public class EmbeddedDirectoryServer
{
  private static final String EMBEDDED_OPEN_DJ_PREFIX = "embeddedOpenDJ";

  private static final String QUICKSETUP_ROOT_PROPERTY = "org.opends.quicksetup.Root";
  private static final String QUICKSETUP_INSTANCE_PROPERTY = "org.opends.quicksetup.Root";

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

  /**
   * Defines an embedded directory server, providing the output and error streams used for
   * giving feedback during operations on the server.
   *
   * @param configParams
   *          The basic configuration parameters for the server.
   * @param connParams
   *          The connection parameters for the server.
   * @param out
   *          Output stream used for feedback during operations on server
   * @param err
   *          Error stream used for feedback during operations on server
   */
  private EmbeddedDirectoryServer(ConfigParameters configParams, ConnectionParameters connParams,
      OutputStream out, OutputStream err)
  {
    this.configParams = configParams;
    this.connectionParams = connParams;
    this.outStream = out;
    this.errStream = err;
    if (connectionParams != null)
    {
      SimpleBindRequest authRequest = Requests.newSimpleBindRequest(
          connectionParams.getBindDn(), connectionParams.getBindPassword().toCharArray());
      ldapConnectionFactory = new LDAPConnectionFactory(
          connectionParams.getHostname(),
          connectionParams.getLdapPort(),
          Options.defaultOptions().set(LDAPConnectionFactory.AUTHN_BIND_REQUEST, authRequest));
    }
    else
    {
      ldapConnectionFactory = null;
    }
  }

  /**
   * Defines an embedded directory server.
   * <p>
   * Output/error streams used for giving feedback during operations are default system ones.
   *
   * @param configParams
   *          The basic configuration parameters for the server.
   * @param connParams
   *          The connection parameters for the server.
   */
  private EmbeddedDirectoryServer(ConfigParameters configParams, ConnectionParameters connParams)
  {
    this(configParams, connParams, System.out, System.err);
  }

  /**
   * Defines an embedded directory server for any operation.
   *
   * @param configParams
   *          The basic configuration parameters for the server.
   * @param connParams
   *          The connection parameters for the server.
   * @param out
   *          Output stream used for feedback during operations on server
   * @param err
   *          Error stream used for feedback during operations on server
   * @return the directory server
   */
  public static EmbeddedDirectoryServer defineServer(ConfigParameters configParams,
      ConnectionParameters connParams, OutputStream out, OutputStream err)
  {
    return new EmbeddedDirectoryServer(configParams, connParams, out, err);
  }

  /**
   * Defines an embedded directory server for start/stop operation.
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
  public static EmbeddedDirectoryServer defineServerForStartStopOperations(ConfigParameters configParams,
      OutputStream out, OutputStream err)
  {
    return new EmbeddedDirectoryServer(configParams, null, out, err);
  }

  /**
   * Defines an embedded directory server for start/stop operation.
   * <p>
   * To be able to perform any operation on the server, use the alternative {@code defineServer()}
   * method.
   *
   * @param configParams
   *          The basic configuration parameters for the server.
   * @return the directory server
   */
  public static EmbeddedDirectoryServer defineServerForStartStopOperations(ConfigParameters configParams)
  {
    return new EmbeddedDirectoryServer(configParams, null, System.out, System.err);
  }

  /**
   * Displays the replication status on the output stream defined for this server.
   * <p>
   * Displays a list with the basic replication configuration of all base DNs of
   * the servers defined in the registration information.
   *
   * @param parameters
   *            The parameters for the replication.
   * @throws EmbeddedDirectoryServerException
   *            If a problem occurs.
   */
  public void displayReplicationStatus(ReplicationParameters parameters) throws EmbeddedDirectoryServerException
  {
    checkConnectionParameters();
    int returnCode = ReplicationCliMain.mainCLI(
        parameters.toCommandLineArgumentsStatus(configParams.getConfigurationFile(), connectionParams),
        !isRunning(), outStream, errStream);

    if (returnCode != 0)
    {
      throw new EmbeddedDirectoryServerException(
          ERR_EMBEDDED_SERVER_DISPLAY_REPLICATION_STATUS.get(configParams.getServerRootDirectory(), returnCode));
    }
  }

  /**
   * Enables replication between this directory server (first server) and another server
   * (second server).
   * <p>
   * Updates the configuration of the servers to replicate the data under the
   * base DN specified in the parameters.
   *
   * @param parameters
   *            The parameters for the replication.
   * @throws EmbeddedDirectoryServerException
   *            If a problem occurs.
   */
  public void enableReplication(ReplicationParameters parameters) throws EmbeddedDirectoryServerException
  {
    checkConnectionParameters();
    int returnCode = ReplicationCliMain.mainCLI(
        parameters.toCommandLineArgumentsEnable(configParams.getConfigurationFile(), connectionParams),
        !isRunning(), outStream, errStream);

    if (returnCode != 0)
    {
      throw new EmbeddedDirectoryServerException(ERR_EMBEDDED_SERVER_ENABLE_REPLICATION.get(
          configParams.getServerRootDirectory(), parameters.getReplicationPort1(), parameters.getHostname2(),
          parameters.getReplicationPort2(), returnCode));
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
  public void importData(ImportParameters parameters) throws EmbeddedDirectoryServerException
  {
    checkServerIsRunning();
    checkConnectionParameters();
    int returnCode = ImportLDIF.mainImportLDIF(
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
    checkConnectionParameters();
    int returnCode = ReplicationCliMain.mainCLI(
        parameters.toCommandLineArgumentsInitialize(configParams.getConfigurationFile(), connectionParams),
        !isRunning(), outStream, errStream);

    if (returnCode != 0)
    {
      throw new EmbeddedDirectoryServerException(ERR_EMBEDDED_SERVER_INITIALIZE_REPLICATION.get(
          configParams.getServerRootDirectory(), connectionParams.getAdminPort(), parameters.getHostname2(),
          parameters.getAdminPort2(), returnCode));
    }
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
   * Reads the configuration of this server with the provided configuration reader.
   * <p>
   * The configuration reader provides access to the root configuration of the directory,
   * which can then be used to read any configuration object and return the result as an
   * arbitrary object.
   * <p>
   * Example:
   * <pre>
   * server.readConfiguration(new DirectoryConfigReader<List<String>>() {
   *   public List<String> read(RootCfgClient rootConfig) {
   *    return Arrays.asList(rootConfig.listSynchronizationProviders());
   *   }
   * });
   * </pre>
   * @param <R>
   *          the type of the returned result
   * @param configReader
   *          the reader of the configuration
   * @return the result of the read
   * @throws EmbeddedDirectoryServerException
   *            If the read fails
   */
  public <R> R readConfiguration(DirectoryConfigReader<R> configReader) throws EmbeddedDirectoryServerException
  {
    checkConnectionParameters();
    Pair<ManagementContext, Connection> contextAndConnection = getManagementContext();
    try
    {
      RootCfgClient rootConfig = contextAndConnection.getFirst().getRootConfiguration();
      return configReader.read(rootConfig);
    }
    catch (Exception e)
    {
      throw new EmbeddedDirectoryServerException(ERR_EMBEDDED_SERVER_READ_CONFIG.get(
          configParams.getServerRootDirectory(), StaticUtils.stackTraceToSingleLineString(e)));
    }
    finally
    {
      StaticUtils.close(contextAndConnection.getFirst(), contextAndConnection.getSecond());
    }
  }

  /**
   * Setups this server from the root directory.
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
    checkConnectionParameters();

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
   * Setups this server from the provided archive.
   * <p>
   * As the DJ archive includes the "opendj" directory, it is mandatory to have
   * the root directory named "opendj" when using this method.
   *
   * @param openDJZipFile
   *            The OpenDJ server archive.
   * @param parameters
   *            The installation parameters.
   * @throws EmbeddedDirectoryServerException
   *            If the setup fails for any reason.
   */
  public void setupFromArchive(File openDJZipFile, SetupParameters parameters) throws EmbeddedDirectoryServerException
  {
    checkConnectionParameters();
    try
    {
      File serverRoot = new File(configParams.getServerRootDirectory());
      if (!serverRoot.getName().equals("opendj"))
      {
        throw new EmbeddedDirectoryServerException(LocalizableMessage.raw("Wrong server root directory" + serverRoot));
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
    setup(parameters);
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
   * Configures this server with the provided configuration updater.
   * <p>
   * The configuration updater provides access to the root configuration of the directory server,
   * which can then be used to perform one or more modifications on any configuration object.
   * <p>
   * Example:
   * <pre>
   * server.configure(new DirectoryConfigUpdater() {
   *
   *   public void update(RootCfgClient rootConfig) {
   *     JEBackendCfgClient userRoot = (JEBackendCfgClient) rootConfig.getBackend("userRoot");
   *     userRoot.setBaseDN(Arrays.asList(DN.valueOf("dc=example,dc=com")));
   *     userRoot.setDBCachePercent(70);
   *     userRoot.commit();
   *   }
   * });
   * </pre>
   *
   * @param configUpdater
   *            updates the configuration
   * @throws EmbeddedDirectoryServerException
   *            If an error occurs.
   */
  public void updateConfiguration(DirectoryConfigUpdater configUpdater) throws EmbeddedDirectoryServerException
  {
    checkConnectionParameters();
    Pair<ManagementContext, Connection> contextAndConnection = getManagementContext();
    try {
      RootCfgClient rootConfig = contextAndConnection.getFirst().getRootConfiguration();
      configUpdater.update(rootConfig);
    } catch (Exception e) {
      throw new EmbeddedDirectoryServerException(ERR_EMBEDDED_SERVER_UPDATE_CONFIG.get(
          configParams.getServerRootDirectory(), StaticUtils.stackTraceToSingleLineString(e)));
    }
    finally
    {
      StaticUtils.close(contextAndConnection.getFirst(), contextAndConnection.getSecond());
    }
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

  /**
   * Interface to update the configuration of the directory server.
   */
  public interface DirectoryConfigUpdater
  {
    /**
     * Updates the configuration, provided the root configuration object of the directory server.
     *
     * @param rootConfig
     *          The root configuration of the server.
     * @throws Exception
     *          If an error occurs.
     */
    public void update(RootCfgClient rootConfig) throws Exception;
  }

  /**
   * Interface to read the configuration of the directory server.
   *
   * @param <R>
   *            The type of the result returned by the read operation.
   */
  public interface DirectoryConfigReader<R>
  {
    /**
     * Reads the configuration, provided the root configuration object of the directory server.
     *
     * @param rootConfig
     *          The root configuration of the server.
     * @return the result of the read operation
     * @throws Exception
     *          If an error occurs.
     */
    public R read(RootCfgClient rootConfig) throws Exception;
  }

  private void checkConnectionParameters() throws EmbeddedDirectoryServerException
  {
    if (connectionParams == null)
    {
      throw new EmbeddedDirectoryServerException(LocalizableMessage.raw("Operation is not permitted"));
    }
  }

  private void checkServerIsRunning() throws EmbeddedDirectoryServerException
  {
    if (!isRunning())
    {
      throw new EmbeddedDirectoryServerException(LocalizableMessage.raw(
          "This operation is only available when server is online"));
    }
  }

  private void checkServerIsNotRunning() throws EmbeddedDirectoryServerException
  {
    if (isRunning())
    {
      throw new EmbeddedDirectoryServerException(LocalizableMessage.raw(
          "This operation is only available when server is offline"));
    }
  }

  /**
   * Retrieves the management context, and optionally a connection if the server is running, in order to
   * give access to the configuration of the server.
   */
  private Pair<ManagementContext, Connection> getManagementContext() throws EmbeddedDirectoryServerException
  {
    Connection ldapConnection = null;
    ManagementContext ctx = null;
    try
    {
      if (isRunning())
      {
        ldapConnection = ldapConnectionFactory.getConnection();
        ctx = newManagementContext(ldapConnection, LDAPProfile.getInstance());
      }
      else
      {
        ctx = newLDIFManagementContext(new File(configParams.getConfigurationFile()));
      }
      return Pair.of(ctx, ldapConnection);
    }
    catch (IOException e)
    {
      throw new EmbeddedDirectoryServerException(LocalizableMessage.raw("Error when initialising LDIF mgt ctx"));
    }
  }
}
