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
package org.opends.server.types;

import static org.opends.server.util.ServerConstants.*;
import org.opends.server.loggers.LogCategory;

/**
 * Logging categories for the debug log messages.
 */
public class DebugLogCategory extends LogCategory
{
  /**
   * The log category that will be used for general debug messages.
   */
  public static final LogCategory MESSAGE = new DebugLogCategory(
      DEBUG_CATEGORY_MESSAGE);

  /**
   * The log category that will be used for constructor messages.
   * Only logger related classes may use this.
   */
  public static final LogCategory CONSTRUCTOR = new DebugLogCategory(
      DEBUG_CATEGORY_CONSTRUCTOR);


  /**
   * The log category that will be used for raw data read messages.
   */
  public static final LogCategory DATA = new DebugLogCategory(
      DEBUG_CATEGORY_DATA);


  /**
   * The log category that will be used for thrown exception messages.
   * Only logger related classes may use this.
   */
  public static final LogCategory THROWN = new DebugLogCategory(
      DEBUG_CATEGORY_THROWN);

  /**
   * The log category that will be used for caught exception messages.
   * Only logger related classes may use this.
   */
  public static final LogCategory CAUGHT = new DebugLogCategory(
      DEBUG_CATEGORY_THROWN);

  /**
   * The log category that will be used for method entry messages.
   * Only logger related classes may use this.
   */
  public static final LogCategory ENTER = new DebugLogCategory(
      DEBUG_CATEGORY_ENTER);

  /**
   * The log category that will be used for method exit messages.
   * Only logger related classes may use this.
   */
  public static final LogCategory EXIT = new DebugLogCategory(
      DEBUG_CATEGORY_EXIT);

  /**
   * The log category that will be used for protocol
   * elements messages.
   */
  public static final LogCategory PROTOCOL = new DebugLogCategory(
      DEBUG_CATEGORY_PROTOCOL);

  /**
   * The log category that will be used for raw data access
   * from the JE database messages.
   */
  public static final LogCategory DATABASE_ACCESS =
      new DebugLogCategory(DEBUG_CATEGORY_DATABASE_ACCESS);


  /**
   * Constructor for the DebugLogCategory class.
   *
   * @param  name  The name of the level.
   */
  public DebugLogCategory(String name)
  {
    super(name);
  }
}
