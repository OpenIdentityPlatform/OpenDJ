/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.loggers;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides mapping from class names to simple category names used for logging.
 * <p>
 * Given a classname, eg org.forgerock.opendj.server.core.SomeClass, it allows
 * to get the corresponding simplified category name if it exists, eg "CORE". If
 * no simplified category name exist, the classname is used as a category name.
 */
public class LoggingCategoryNames
{
  /**
   * Contains mapping from class names (or package names) to category names. In
   * most case, package name is sufficient to map to a category name. It is
   * valid if several entries point to the same category name.
   */
  private static final Map<String, String> NAMES = new HashMap<>();
  private static final String DEFAULT_CATEGORY = "NONE";
  static
  {
    // The category used for messages associated with the core server.
    NAMES.put("org.opends.messages.core", "CORE");
    NAMES.put("org.opends.messages.runtime", "JVM");
    NAMES.put("com.forgerock.opendj.ldap", "SDK");

    // The category used for messages associated with server extensions
    // (e.g. extended operations, SASL mechanisms, password storage, schemes, password validators, etc.).
    NAMES.put("org.opends.messages.extension", "EXTENSIONS");

    // The category used for messages associated with
    // connection and protocol handling (e.g., ASN.1 and LDAP).
    NAMES.put("org.opends.messages.protocol", "PROTOCOL");

    // The category used for messages associated with configuration handling.
    NAMES.put("org.opends.messages.config", "CONFIG");

    // The category used for messages associated with the server loggers.
    NAMES.put("org.opends.messages.logger", "LOG");

    // The category used for messages associated with the general server utilities.
    NAMES.put("org.opends.messages.utility", "UTIL");

    // The category used for messages associated with the server schema elements.
    NAMES.put("org.opends.messages.schema", "SCHEMA");

    // The category that will be used for messages associated with plugin processing.
    NAMES.put("org.opends.messages.plugin", "PLUGIN");

    // The category used for messages associated with generic backends.
    NAMES.put("org.opends.messages.backend", "BACKEND");

    // The category used for messages associated with tools
    NAMES.put("org.opends.messages.tool", "TOOLS");

    // The category used for messages associated with tasks
    NAMES.put("org.opends.messages.task", "TASK");

    // The category used for messages associated with Access Control
    NAMES.put("org.opends.messages.access_control", "ACCESS_CONTROL");

    // The category used for messages associated with the administration framework.
    NAMES.put("org.opends.messages.admin", "ADMIN");

    // The category used for messages associated with the Synchronization
    NAMES.put("org.opends.server.replication", "SYNC");

    // The category used for messages associated with quicksetup tools
    NAMES.put("org.opends.messages.quickSetup", "QUICKSETUP");

    // The category used for messages associated with the tool like the offline installer and unintaller.
    NAMES.put("org.opends.messages.admin_tool", "ADMIN_TOOL");

    // The category used for messages associated with common audit.
    NAMES.put("org.forgerock.audit", "AUDIT");
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
  public static String getCategoryName(final String className)
  {
    return getCategoryName(className, null);
  }

  /**
   * Returns the simple category name corresponding to the provided class name
   * or a class name if no mapping corresponds.
   * The returned class name will be {@code fallbackCategory} if the class name is
   * null.
   *
   * @param className
   *          The classname to retrieve the category name from.
   * @param fallbackCategory
   *          The category to return when className is null.
   * @return the simple category name, or the provided className if no matching
   *         simple category name is found
   */
  public static String getCategoryName(final String className, String fallbackCategory)
  {
    if (className == null)
    {
      return fallbackCategory == null ? DEFAULT_CATEGORY : fallbackCategory;
    }
    final String category = NAMES.get(className);
    return category != null ? category : className;
  }
}
