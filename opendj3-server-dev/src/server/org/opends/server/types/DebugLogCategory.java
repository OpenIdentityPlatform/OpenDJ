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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.types;

import static org.opends.server.util.ServerConstants.*;
import org.opends.server.loggers.LogCategory;

/**
 * Logging categories for the debug log messages.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class DebugLogCategory extends LogCategory
{
  /**
   * The log category that will be used for general debug messages.
   */
  public static final LogCategory MESSAGE = new DebugLogCategory(
      DEBUG_CATEGORY_MESSAGE);

  /**
   * The log category that will be used for caught exception messages.
   * Only logger related classes may use this.
   */
  public static final LogCategory CAUGHT = new DebugLogCategory(
      DEBUG_CATEGORY_CAUGHT);


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
