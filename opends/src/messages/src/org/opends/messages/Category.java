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
 *      Portions Copyright 2007-2008 Sun Microsystems, Inc.
 */

package org.opends.messages;

import java.util.Map;
import java.util.HashMap;
import java.util.EnumSet;

/**
 * Defines values for message categories which are loosly based on
 * server components.  Categories contain an in value that can be
 * used as a mask for bitwise operations.
 */
@org.opends.server.types.PublicAPI(
    stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
    mayInstantiate=false,
    mayExtend=false,
    mayInvoke=true)
public enum Category {

  /**
   * The category that will be used for messages associated with the
   * core server.
   */
  CORE(0x00000000),

  /**
   * The category that will be used for messages associated with server
   * extensions (e.g., extended operations, SASL mechanisms, password storage
   * schemes, password validators, etc.).
   */
  EXTENSIONS(0x00100000),

  /**
   * The category that will be used for messages associated with
   * connection and protocol handling (e.g., ASN.1 and LDAP).
   */
  PROTOCOL(0x00200000),

  /**
   * The category that will be used for messages associated with
   * configuration handling.
   */
  CONFIG(0x00300000),

  /**
   * The category that will be used for messages associated with the
   * server loggers.
   */
  LOG(0x00400000),

  /**
   * The category that will be used for messages associated with the
   * general server utilities.
   */
  UTIL(0x00500000),

  /**
   * The category that will be used for messages associated with the
   * server schema elements.
   */
  SCHEMA(0x00600000),

  /**
   * The category that will be used for messages associated with plugin
   * processing.
   */
  PLUGIN(0x00700000),

  /**
   * The category used for messages associated with the JE backend.
   */
  JEB(0x00800000),

  /**
   * The category used for messages associated with generic backends.
   */
  BACKEND(0x00900000),

  /**
   * The category used for messages associated with tools.
   */
  TOOLS(0x00A00000),

  /**
   * The category used for messages associated with tasks.
   */
  TASK(0x00B00000),

  /**
   * The category used for messages associated with Access Control.
   */
  ACCESS_CONTROL(0x00C00000),

  /**
   * The category used for messages associated with the
   * administration framework.
   */
  ADMIN(0x00D00000),

  /**
   * The category used for messages associated with the Synchronization.
   */
  SYNC(0x00E00000),

  /**
   * The category used for messages associated with version information.
   */
  VERSION(0x00F00000),

  /**
   * The category used for messages associated with quicksetup tools.
   */
  QUICKSETUP(0x01000000),

  /**
   * The category used for messages associated with the tool like the
   * offline installer and unintaller.
   */
  ADMIN_TOOL(0x01100000),

  /**
   * The category used for messages associated with the dsconfig
   * administration tool.
   */
  DSCONFIG(0x01200000),

  /**
   * The category used for messages associated with the runtime information.
   */

  RUNTIME_INFORMATION(0x01300000),

  /**
   * The category that will be used for messages associated with
   * third-party (including user-defined) modules.
   */
  THIRD_PARTY(0x80000000),

  /**
   * The category that will be used for messages associated with
   * user-defined modules.
   */
  USER_DEFINED(0xFFF00000);

  static private Map<Integer,Category> MASK_VALUE_MAP;

  static {
    MASK_VALUE_MAP = new HashMap<Integer,Category>();
    for (Category c : EnumSet.allOf(Category.class)) {
      MASK_VALUE_MAP.put(c.mask, c);
    }
  }

  /**
   * Obtains the <code>Severity</code> associated with the the input
   * message ID <code>msgId</code>.
   * @param msgId int message ID
   * @return Severity assocated with the ID
   */
  static public Category parseMessageId(int msgId) {
    return Category.parseMask(msgId & 0xFFF00000);
  }

  /**
   * Obtains the <code>Severity</code> associated with a given mask
   * value.
   * @param mask for which a <code>Severity</code> is obtained.
   * @return Severity associated with <code>mask</code>
   */
  static public Category parseMask(int mask) {
    return MASK_VALUE_MAP.get(mask);
  }

  private final int mask;

  /**
   * Gets the mask value associated with this category.
   * @return int mask value
   */
  public int getMask() {
    return this.mask;
  }

  private Category(int intValue) {
    this.mask = intValue;
  }

}
