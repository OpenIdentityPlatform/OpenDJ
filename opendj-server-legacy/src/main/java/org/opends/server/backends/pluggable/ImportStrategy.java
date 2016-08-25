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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import java.util.concurrent.ExecutionException;

import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.backends.RebuildConfig;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;

/** The strategy to use for importing LDIF files. */
interface ImportStrategy
{
  /**
   * Imports information from an LDIF file.
   *
   * @param importConfig
   *          The configuration to use when performing the import
   * @return Information about the result of the import processing
   * @throws InitializationException
   *           If a problem occurs during initialization
   * @throws ConfigException
   *           If the configuration is invalid
   * @throws InterruptedException
   *           If the import process has been interrupted
   * @throws ExecutionException
   *           If a problem occurs while performing the LDIF import
   * @see {@link Backend#importLDIF(LDIFImportConfig, ServerContext)}
   */
  LDIFImportResult importLDIF(LDIFImportConfig importConfig)
      throws InitializationException, ConfigException, InterruptedException, ExecutionException;

  /**
   * Rebuild indexes.
   *
   * @param rebuildConfig
   *          The configuration to sue when performing the rebuild.
   * @throws InitializationException
   *           If a problem occurs during initialization
   * @throws ConfigException
   *           If the configuration is invalid
   * @throws InterruptedException
   *           If the rebuild process has been interrupted
   * @throws ExecutionException
   *           If a problem occurs while performing the rebuild
   * @see {@link Backend#rebuildIndex(RebuildConfig, ServerContext)}
   */
  void rebuildIndex(RebuildConfig rebuildConfig)
      throws InitializationException, ConfigException, InterruptedException, ExecutionException;
}
