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

import org.forgerock.http.routing.Router;
import org.forgerock.opendj.config.server.ServerManagementContext;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.opends.server.extensions.DiskSpaceMonitor;
import org.opends.server.loggers.CommonAudit;
import org.opends.server.types.CryptoManager;
import org.opends.server.types.DirectoryEnvironmentConfig;
import org.opends.server.types.Schema;

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
   *
   * @return the schema
   */
  Schema getSchema();

  /**
   * Returns the new schema of the server (SDK schema).
   * <p>
   * This method will disappear once migration to new schema
   * is finished. Meanwhile, it is necessary to keep both the
   * legacy version and the new version.
   *
   * @return the new version of the schema
   */
  org.forgerock.opendj.ldap.schema.Schema getSchemaNG();

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
}
