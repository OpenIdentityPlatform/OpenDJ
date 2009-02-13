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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.server.backends.ndb;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;

/**
 * This class is a wrapper around the NDB database object.
 */
public abstract class DatabaseContainer
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The database entryContainer.
   */
  protected EntryContainer entryContainer;

  /**
   * The name of the database within the entryContainer.
   */
  protected String name;

  /**
   * Create a new DatabaseContainer object.
   *
   * @param name The name of the entry database.
   * @param entryContainer The entryContainer of the entry database.
   */
  protected DatabaseContainer(String name, EntryContainer entryContainer)
  {
    this.entryContainer = entryContainer;
    this.name = name;
  }

  /**
   * Get a string representation of this object.
   * @return return A string representation of this object.
   */
  @Override
  public String toString()
  {
    return name;
  }

  /**
   * Get the NDB database name for this database container.
   *
   * @return NDB database name for this database container.
   */
  public String getName()
  {
    return name;
  }
}
