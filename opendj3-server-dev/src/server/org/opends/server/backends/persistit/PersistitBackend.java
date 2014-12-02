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
 *      Copyright 2014 ForgeRock AS
 */
package org.opends.server.backends.persistit;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.admin.std.server.LocalDBBackendCfg;
import org.opends.server.backends.pluggable.PluggableStorageBackend;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;

import com.persistit.exception.PersistitException;
import com.sleepycat.je.DatabaseException;

import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.JebMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This is an implementation of a Directory Server Backend which stores entries
 * locally in a Persistit database.
 */
public class PersistitBackend extends PluggableStorageBackend<LocalDBBackendCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private PersistitRootContainer rootContainer;

  /**
   * Returns the root container.
   *
   * @return the root container
   */
  public PersistitRootContainer getRootContainer()
  {
    return rootContainer;
  }

  /** {@inheritDoc} */
  @Override
  public void initializeBackend() throws ConfigException, InitializationException
  {
    rootContainer = new PersistitRootContainer(this, cfg);
    rootContainer.open();

    registerBaseDNs(cfg.getBaseDN());

    cfg.addLocalDBChangeListener(this);
  }

  /** {@inheritDoc} */
  @Override
  public void finalizeBackend()
  {
    super.finalizeBackend();
    cfg.removeLocalDBChangeListener(this);

    deregisterBaseDNs(rootContainer.getSuffixContainers().keySet());

    // Close the database.
    try
    {
      rootContainer.close();
      rootContainer = null;
    }
    catch (DatabaseException e)
    {
      logger.traceException(e);
      logger.error(ERR_JEB_DATABASE_EXCEPTION, e.getMessage());
    }

    // Log an informational message.
    logger.info(NOTE_BACKEND_OFFLINE, cfg.getBackendId());
  }

  /** {@inheritDoc} */
  @Override
  public long getEntryCount()
  {
    if (rootContainer != null)
    {
      try
      {
        return rootContainer.getEntryCount();
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }
    return -1;
  }

  /** {@inheritDoc} */
  @Override
  public Entry getEntry(DN entryDN) throws DirectoryException
  {
    PersistitSuffixContainer sc = rootContainer.getSuffixContainer(entryDN);
    try
    {
      return sc.getEntry(entryDN);
    }
    catch (PersistitException e)
    {
      logger.traceException(e);
      throw createDirectoryException(e);
    }
  }

  /**
   * Creates a customized DirectoryException from the DatabaseException thrown
   * by JE backend.
   *
   * @param e
   *          The PersistitException to be converted.
   * @return DirectoryException created from exception.
   */
  private DirectoryException createDirectoryException(PersistitException e)
  {
    // TODO JNR rename the exception to remove the "JEB"
    LocalizableMessage message = ERR_JEB_DATABASE_EXCEPTION.get(stackTraceToSingleLineString(e));
    return new DirectoryException(DirectoryServer.getServerErrorResultCode(), message, e);
  }
}
