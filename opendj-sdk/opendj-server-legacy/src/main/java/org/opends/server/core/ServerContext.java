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
 *      Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.core;

import org.forgerock.opendj.config.server.ServerManagementContext;
import org.opends.server.extensions.DiskSpaceMonitor;
import org.opends.server.schema.SchemaUpdater;
import org.opends.server.types.DirectoryEnvironmentConfig;
import org.opends.server.types.Schema;

/**
 * Context for the server, giving access to global properties of the server.
 */
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
   * Returns the schema updater, which provides
   * a mean to update the server's current schema.
   *
   * @return the schema updater
   */
  SchemaUpdater getSchemaUpdater();

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
   * @return the Disk Space Monioring service
   */
  DiskSpaceMonitor getDiskSpaceMonitor();
}
