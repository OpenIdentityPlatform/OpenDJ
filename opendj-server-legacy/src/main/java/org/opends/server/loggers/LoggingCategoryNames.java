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
import java.util.NavigableMap;
import java.util.TreeMap;

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
  private static final Map<String, String> RESOURCE_NAMES = new HashMap<>();
  private static final NavigableMap<String, String> SOURCE_CLASSES = new TreeMap<>();
  private static final String DEFAULT_CATEGORY = "NONE";
  static
  {
    // The category used for messages associated with the core server.
    RESOURCE_NAMES.put("org.opends.messages.core", "CORE");
    SOURCE_CLASSES.put("org.opends.server.core", "CORE");
    RESOURCE_NAMES.put("org.opends.messages.runtime", "JVM");
    SOURCE_CLASSES.put("org.opends.server.util.RuntimeInformation", "JVM");
    RESOURCE_NAMES.put("com.forgerock.opendj.ldap", "SDK");
    SOURCE_CLASSES.put("org.forgerock.opendj.ldap", "SDK");

    // The category used for messages associated with server extensions
    // (e.g. extended operations, SASL mechanisms, password storage, schemes, password validators, etc.).
    RESOURCE_NAMES.put("org.opends.messages.extension", "EXTENSIONS");
    SOURCE_CLASSES.put("org.opends.server.extensions", "EXTENSIONS");

    // The category used for messages associated with
    // connection and protocol handling (e.g., ASN.1 and LDAP).
    RESOURCE_NAMES.put("org.opends.messages.protocol", "PROTOCOL");
    SOURCE_CLASSES.put("org.opends.server.protocol", "PROTOCOL");
    SOURCE_CLASSES.put("org.forgerock.opendj.io", "PROTOCOL");

    // The category used for messages associated with configuration handling.
    RESOURCE_NAMES.put("org.opends.messages.config", "CONFIG");
    SOURCE_CLASSES.put("org.opends.server.config", "CONFIG");

    // The category used for messages associated with the server loggers.
    RESOURCE_NAMES.put("org.opends.messages.logger", "LOG");
    SOURCE_CLASSES.put("org.opends.server.loggers", "LOG");

    // The category used for messages associated with the general server utilities.
    RESOURCE_NAMES.put("org.opends.messages.utility", "UTIL");
    SOURCE_CLASSES.put("org.opends.server.util", "UTIL");

    // The category used for messages associated with the server schema elements.
    RESOURCE_NAMES.put("org.opends.messages.schema", "SCHEMA");
    SOURCE_CLASSES.put("org.opends.server.schema", "SCHEMA");
    SOURCE_CLASSES.put("org.forgerock.opendj.ldap.schema", "SCHEMA");

    // The category that will be used for messages associated with plugin processing.
    RESOURCE_NAMES.put("org.opends.messages.plugin", "PLUGIN");
    SOURCE_CLASSES.put("org.opends.server.plugins", "PLUGIN");

    // The category used for messages associated with generic backends.
    RESOURCE_NAMES.put("org.opends.messages.backend", "BACKEND");
    SOURCE_CLASSES.put("org.opends.server.backends", "BACKEND");

    // The category used for messages associated with tools
    RESOURCE_NAMES.put("org.opends.messages.tool", "TOOLS");
    SOURCE_CLASSES.put("org.opends.server.tools", "TOOLS");

    // The category used for messages associated with tasks
    RESOURCE_NAMES.put("org.opends.messages.task", "TASK");
    SOURCE_CLASSES.put("org.opends.server.tasks", "TASK");

    // The category used for messages associated with Access Control
    RESOURCE_NAMES.put("org.opends.messages.access_control", "ACCESS_CONTROL");
    SOURCE_CLASSES.put("org.opends.server.authorization", "ACCESS_CONTROL");

    // The category used for messages associated with the administration framework.
    RESOURCE_NAMES.put("org.opends.messages.admin", "ADMIN");
    SOURCE_CLASSES.put("org.opends.server.admin", "ADMIN");

    // The category used for messages associated with the Synchronization
    RESOURCE_NAMES.put("org.opends.messages.replication", "SYNC");
    SOURCE_CLASSES.put("org.opends.server.replication", "SYNC");

    // The category used for messages associated with quicksetup tools
    RESOURCE_NAMES.put("org.opends.messages.quickSetup", "QUICKSETUP");
    SOURCE_CLASSES.put("org.opends.quicksetup", "QUICKSETUP");

    // The category used for messages associated with the tool like the offline installer and un-installer.
    RESOURCE_NAMES.put("org.opends.messages.admin_tool", "ADMIN_TOOL");
    SOURCE_CLASSES.put("org.opends.guitools.uninstaller", "ADMIN_TOOL");
    SOURCE_CLASSES.put("org.opends.admin.ads", "ADMIN_TOOL");

    // The category used for messages associated with common audit.
    RESOURCE_NAMES.put("org.forgerock.audit", "AUDIT");
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
    final String category = RESOURCE_NAMES.get(className);
    if (category == null)
    {
      final Map.Entry<String, String> entry = SOURCE_CLASSES.floorEntry(className);
      if (entry != null && className.startsWith(entry.getKey()))
      {
        return entry.getValue();
      }
      return className;
    }
    return category;
  }
}
