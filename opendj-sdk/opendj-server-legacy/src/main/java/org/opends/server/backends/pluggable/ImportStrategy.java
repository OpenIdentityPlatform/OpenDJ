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
 *      Copyright 2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import org.opends.server.core.ServerContext;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;

/** The strategy to use for importing LDIF files. */
interface ImportStrategy
{
  /**
   * Imports information from an LDIF file into the supplied root container.
   *
   * @param importConfig
   *          The configuration to use when performing the import
   * @param rootContainer
   *          The root container where to do the import
   * @param serverContext
   *          The server context
   * @return Information about the result of the import processing
   * @throws DirectoryException
   *           If a problem occurs while performing the LDIF import
   * @throws InitializationException
   *           If a problem occurs while initializing the LDIF import
   * @see {@link Backend#importLDIF(LDIFImportConfig, ServerContext)}
   */
  LDIFImportResult importLDIF(LDIFImportConfig importConfig, RootContainer rootContainer, ServerContext serverContext)
      throws DirectoryException, InitializationException;
}
