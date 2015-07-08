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
 *      Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.loggers;

import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Provides mapping from class names to simple category names used for logging.
 * <p>
 * Given a classname, eg org.forgerock.opendj.server.core.SomeClass, it allows
 * to get the corresponding simplified category name if it exists, eg "CORE". If
 * no simplified category name exist, the classname is used as a category name.
 */
class LoggingCategoryNames
{
  /**
   * Contains mapping from class names (or package names) to category names. In
   * most case, package name is sufficient to map to a category name. It is
   * valid if several entries point to the same category name.
   */
  private static final NavigableMap<String, String> NAMES = new TreeMap<>();
  static
  {
    // The category used for messages associated with the core server.
    NAMES.put("org.opends.server.core", "CORE");
    NAMES.put("org.forgerock.opendj.ldap", "CORE");

    // The category used for messages associated with server
    //extensions (e.g., extended operations, SASL mechanisms, password storage
    // schemes, password validators, etc.).
    NAMES.put("org.opends.server.extensions", "EXTENSIONS");

    // The category used for messages associated with
    // connection and protocol handling (e.g., ASN.1 and LDAP).
    NAMES.put("org.opends.server.protocol", "PROTOCOL");
    NAMES.put("org.forgerock.opendj.io", "PROTOCOL");

    // The category used for messages associated with configuration handling.
    NAMES.put("org.opends.server.config", "CONFIG");

    // The category used for messages associated with the server loggers.
    NAMES.put("org.opends.server.loggers", "LOG");

    // The category used for messages associated with the
    // general server utilities.
    NAMES.put("org.opends.server.util", "UTIL");

    // The category used for messages associated with the
    // server schema elements.
    NAMES.put("org.opends.server.schema", "SCHEMA");
    NAMES.put("org.forgerock.opendj.ldap.schema", "SCHEMA");

    // The category used for messages associated with the server controls.
    NAMES.put("org.opends.server.controls", "CONTROLS");
    NAMES.put("org.forgerock.opendj.ldap.controls", "CONTROLS");

    // The category that will be used for messages associated with plugin
    // processing.
    NAMES.put("org.opends.server.plugins", "PLUGIN");

    // The category used for messages associated with the JE backend.
    NAMES.put("org.opends.server.backends.jeb", "JEB");

    // The category used for messages associated with generic backends.
    NAMES.put("org.opends.server.backends", "BACKEND");

    // The category used for messages associated with tools
    NAMES.put("org.opends.server.tools", "TOOLS");

    // The category used for messages associated with upgrade tool
    NAMES.put("org.opends.server.tools.upgrade", "UPGRADE");

    // The category used for messages associated with tasks
    NAMES.put("org.opends.server.tasks", "TASK");

    // The category used for messages associated with Access Control
    NAMES.put("org.opends.server.authorization", "ACCESS_CONTROL");

    // The category used for messages associated with the administration
    // framework.
    NAMES.put("org.opends.server.admin", "ADMIN");

    // The category used for messages associated with the Synchronization
    NAMES.put("org.opends.server.replication", "SYNC");

    // The category used for messages associated with version information
    //PACKAGES_TO_NAMES.put("org.opends.server", "VERSION");

    // The category used for messages associated with quicksetup tools
    NAMES.put("org.opends.quicksetup", "QUICKSETUP");

    // The category used for messages associated with the tool like the offline
    // installer and unintaller.
    NAMES.put("org.opends.quicksetup.offline", "ADMIN_TOOL");
    NAMES.put("org.opends.guitools.uninstaller", "ADMIN_TOOL");

    // The category used for messages associated with the dsconfig
    // administration tool.
    NAMES.put("org.opends.admin.ads", "DSCONFIG");

    // The category used for messages associated with the runtime information
    //NAMES.put("org.opends.server", "RUNTIME_INFORMATION");
  }

  /**
   * Returns the simple category name corresponding to the provided class name
   * or the class name if no mapping corresponds.
   *
   * @param className
   *          The classname to retrieve the category name from.
   * @return the simple category name, or the provided className if no matching
   *         simple category name is found
   */
  static String getCategoryName(final String className)
  {
    final Entry<String, String> entry = NAMES.floorEntry(className);
    if (entry != null && className.startsWith(entry.getKey()))
    {
      return entry.getValue();
    }
    return className;
  }
}
