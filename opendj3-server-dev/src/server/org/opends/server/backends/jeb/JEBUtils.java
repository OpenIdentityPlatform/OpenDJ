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
package org.opends.server.backends.jeb;

import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;

/**
 * JE Backend utility methods.
 */
final class JEBUtils
{
  /** A database key for the presence index. */
  static final DatabaseEntry presenceKey = new DatabaseEntry(PresenceIndexer.presenceKeyBytes);

  private JEBUtils()
  {
    // Private for utility classes
  }

  /**
   * Converts the provided JE environment to a {@link DatabaseConfig} object
   * that disallows duplicates.
   *
   * @param env
   *          the environment object to convert
   * @return a new {@link DatabaseConfig} object
   */
  static DatabaseConfig toDatabaseConfigNoDuplicates(Environment env)
  {
    final DatabaseConfig result = new DatabaseConfig();
    if (env.getConfig().getReadOnly())
    {
      result.setReadOnly(true);
      result.setAllowCreate(false);
      result.setTransactional(false);
    }
    else if (!env.getConfig().getTransactional())
    {
      result.setAllowCreate(true);
      result.setTransactional(false);
      result.setDeferredWrite(true);
    }
    else
    {
      result.setAllowCreate(true);
      result.setTransactional(true);
    }
    return result;
  }

  /**
   * Converts the provided JE environment to a {@link DatabaseConfig} object
   * that allows duplicates.
   *
   * @param env
   *          the environment object to convert
   * @return a new {@link DatabaseConfig} object
   */
  static DatabaseConfig toDatabaseConfigAllowDuplicates(Environment env)
  {
    final DatabaseConfig result = new DatabaseConfig();
    if (env.getConfig().getReadOnly())
    {
      result.setReadOnly(true);
      result.setSortedDuplicates(true);
      result.setAllowCreate(false);
      result.setTransactional(false);
    }
    else if (!env.getConfig().getTransactional())
    {
      result.setSortedDuplicates(true);
      result.setAllowCreate(true);
      result.setTransactional(false);
      result.setDeferredWrite(true);
    }
    else
    {
      result.setSortedDuplicates(true);
      result.setAllowCreate(true);
      result.setTransactional(true);
    }
    return result;
  }
}
