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
 *      Portions Copyright 2013-2014 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import org.forgerock.i18n.slf4j.LocalizedLogger;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Transaction;

/**
 * Utility class for JE.
 */
public final class JEUtils
{

  /** The tracer object for the debug logger. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private JEUtils()
  {
    // Utility class
  }

  /**
   * Aborts the current transaction. It has no effect if the transaction has
   * committed.
   * <p>
   * This method should only be used after an exception was caught and is about
   * to be rethrown .
   *
   * @param txn
   *          the transaction to abort
   */
  public static void abort(Transaction txn)
  {
    if (txn != null)
    {
      try
      {
        txn.abort();
      }
      catch (DatabaseException ignored)
      {
        // Ignored because code is already throwing an exception
        logger.traceException(ignored);
      }
    }
  }
}
