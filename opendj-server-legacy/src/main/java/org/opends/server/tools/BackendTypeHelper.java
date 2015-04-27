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
 *      Copyright 2015 ForgeRock AS
 */
package org.opends.server.tools;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.AbstractManagedObjectDefinition;
import org.forgerock.opendj.config.ConfigurationFramework;
import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.server.config.client.BackendCfgClient;
import org.forgerock.opendj.server.config.meta.LocalDBBackendCfgDefn;
import org.forgerock.opendj.server.config.meta.PluggableBackendCfgDefn;
import org.forgerock.opendj.server.config.server.BackendCfg;

/**
 * Helper class for setup applications. It helps applications to provide a
 * backend type choice to the user.
 */
public class BackendTypeHelper
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private List<ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg>> backends;

  /** Creates a new backend type helper. */
  public BackendTypeHelper()
  {
    initializeConfigurationFramework();
    createAvailableBackendsList();
  }

  private void initializeConfigurationFramework()
  {
    if (!ConfigurationFramework.getInstance().isInitialized())
    {
      try
      {
        ConfigurationFramework.getInstance().initialize();
      }
      catch (ConfigException e)
      {
        final LocalizableMessage message = LocalizableMessage.raw(
            "Error occured while loading the configuration framework: " + e.getLocalizedMessage());
        logger.error(message);
        throw new RuntimeException(message.toString());
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void createAvailableBackendsList()
  {
    backends = new LinkedList<>();
    backends.add(LocalDBBackendCfgDefn.getInstance());

    for (AbstractManagedObjectDefinition<?, ?> backendType : PluggableBackendCfgDefn.getInstance().getAllChildren())
    {
      // Filtering out only the non-abstract backends to avoid users attempt to create abstract ones
      if (backendType instanceof ManagedObjectDefinition)
      {
        backends.add((ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg>) backendType);
      }
    }
  }

  ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg> retrieveBackendTypeFromName(
      final String backendTypeStr)
  {
    for (ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg> backendType : getBackendTypes())
    {
      final String name = backendType.getName();
      if (backendTypeStr.equalsIgnoreCase(name)
          || backendTypeStr.equalsIgnoreCase(filterSchemaBackendName(name)))
      {
        return backendType;
      }
    }

    return null;
  }

  List<ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg>> getBackendTypes()
  {
    return backends;
  }

  String getPrintableBackendTypeNames()
  {
    String backendTypeNames = "";
    for (final String backendName : getBackendTypeNames())
    {
      backendTypeNames += backendName + ", ";
    }

    if (backendTypeNames.isEmpty())
    {
      return "Impossible to retrieve supported backend type list";
    }

    return backendTypeNames.substring(0, backendTypeNames.length() - 2);
  }

  /**
   * Return a list of all available backend type printable names.
   *
   * @return A list of all available backend type printable names.
   */
  public List<String> getBackendTypeNames()
  {
    final List<String> backendTypeNames = new LinkedList<>();
    for (ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg> backendType : backends)
    {
      backendTypeNames.add(filterSchemaBackendName(backendType.getName()));
    }

    return backendTypeNames;
  }

  String filterSchemaBackendName(final String dsCfgBackendName)
  {
    final String cfgNameRegExp = "(.*)-backend.*";
    final Matcher regExpMatcher = Pattern.compile(cfgNameRegExp, Pattern.CASE_INSENSITIVE).matcher(dsCfgBackendName);
    if (regExpMatcher.matches())
    {
      return regExpMatcher.group(1);
    }

    return dsCfgBackendName;
  }
}
