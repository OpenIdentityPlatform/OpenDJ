/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;

import java.util.Comparator;

/**
 * This comparator is used to sort databases in order of priority
 * for preloading into the cache.
 */
public class DbPreloadComparator implements Comparator<Database>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();


  /**
   * Calculate the relative priority of a database for preloading.
   *
   * @param database A handle to the database.
   * @return 1 for id2entry database, 2 for dn2id database, 3 for all others.
   */
  static private int priority(Database database)
  {
    try
    {
      String name = database.getDatabaseName();
      if (name.endsWith(EntryContainer.ID2ENTRY_DATABASE_NAME))
      {
        return 1;
      }
      else if (name.endsWith(EntryContainer.DN2ID_DATABASE_NAME))
      {
        return 2;
      }
      else
      {
        return 3;
      }
    }
    catch (DatabaseException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      return 3;
    }
  }

  /**
   * Compares its two arguments for order.  Returns a negative integer,
   * zero, or a positive integer as the first argument is less than, equal
   * to, or greater than the second.
   *
   * @param database1 the first object to be compared.
   * @param database2 the second object to be compared.
   * @return a negative integer, zero, or a positive integer as the
   *         first argument is less than, equal to, or greater than the
   *         second.
   **/
  public int compare(Database database1, Database database2)
  {
    return priority(database1) - priority(database2);
  }
}
