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
 * Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.core;

import java.util.concurrent.ScheduledExecutorService;

import org.forgerock.http.routing.Router;
import org.forgerock.opendj.config.server.ServerManagementContext;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.opends.server.api.KeyManagerProvider;
import org.opends.server.api.TrustManagerProvider;
import org.opends.server.config.ConfigurationHandler;
import org.opends.server.discovery.ServiceDiscoveryMechanismConfigManager;
import org.opends.server.extensions.DiskSpaceMonitor;
import org.opends.server.loggers.CommonAudit;
import org.opends.server.schema.SchemaHandler;
import org.opends.server.types.CryptoManager;
import org.opends.server.types.DirectoryEnvironmentConfig;

/** Context for the server, giving access to global properties of the server. */
public interface ServerContext
{
  /**
   * Returns the directory of server instance.
   *
   * @return the instance root directory
   */
  String getInstanceRoot();

  /**
   * Returns the root directory of server.
   *
   * @return the server root directory
   */
  String getServerRoot();

  /**
   * Returns the schema of the server.
   * <p>
   * The schema is immutable. Any change on the schema must be done using a {@code SchemaHandler}
   * which is available through the {@code getSchemaHandler()} method.
   *
   * @return the schema
   */
  Schema getSchema();

  /**
   * Returns the schema handler, which provides operations to the schema.
   *
   * @return the schema handler
   */
  SchemaHandler getSchemaHandler();

  /**
   * Returns the configuration handler, which provides operations to the configuration.
   *
   * @return the configuration handler
   */
  ConfigurationHandler getConfigurationHandler();

  /**
   * Returns the environment of the server.
   *
   * @return the environment
   */
  DirectoryEnvironmentConfig getEnvironment();

  /**
   * Returns the server management context, which gives
   * an entry point on configuration objects.
   *
   * @return the server management context
   */
  ServerManagementContext getServerManagementContext();

  /**
   * Returns the root configuration object.
   * <p>
   * Equivalent to calling {@code getServerManagementContext().getRootConfiguration()}.
   *
   * @return the root configuration object
   */
  RootCfg getRootConfig();

  /**
   * Returns the memory quota system for reserving long term memory.
   *
   * @return the memory quota system
   */
  MemoryQuota getMemoryQuota();

  /**
   * Returns the Disk Space Monitoring service, for checking free disk space.
   * Configure a directory to be monitored and optionally get alerted when
   * disk space transitions from low to full to back to normal.
   *
   * @return the Disk Space Monitoring service
   */
  DiskSpaceMonitor getDiskSpaceMonitor();

  /**
   * Returns the HTTP request router.
   *
   * @return the HTTP Router service
   */
  Router getHTTPRouter();

  /**
   * Returns the common audit manager.
   *
   * @return the common audit manager
   */
  CommonAudit getCommonAudit();

  /**
   * Returns the Service Discovery Mechanism Config Manager.
   *
   * @return the Service Discovery Mechanism Config Manager
   */
  ServiceDiscoveryMechanismConfigManager getServiceDiscoveryMechanismConfigManager();

  /**
   * Returns the logger config manager.
   *
   * @return the logger config manager
   */
  LoggerConfigManager getLoggerConfigManager();

  /**
   * Returns the Crypto Manager for the instance.
   *
   * @return the Crypto Manager for the instance
   */
  CryptoManager getCryptoManager();

  /**
   * Returns the UNIX's cron-like executor service.
   *
   * @return the UNIX's cron-like executor service
   */
  ScheduledExecutorService getCronExecutorService();

  /**
   * Returns the manager of backends.
   *
   * @return backend manager
   */
  BackendConfigManager getBackendConfigManager();

  /**
   * Returns the manager of core configuration.
   *
   * @return core configuration manager
   */
  CoreConfigManager getCoreConfigManager();

  /**
   * Returns the key manager provider matching the provided DN.
   *
   * @param keyManagerProviderDN
   *          the DN of the key manager provider
   * @return the key manager provider, or {@code null} if none match
   */
  KeyManagerProvider<?> getKeyManagerProvider(DN keyManagerProviderDN);

  /**
   * Returns the trust manager provider matching the provided DN.
   *
   * @param trustManagerProviderDN
   *          the DN of the trust manager provider
   * @return the trust manager provider, or {@code null} if none match
   */
  TrustManagerProvider<?> getTrustManagerProvider(DN trustManagerProviderDN);
}
