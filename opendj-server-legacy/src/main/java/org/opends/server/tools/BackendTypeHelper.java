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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.forgerock.opendj.config.AbstractManagedObjectDefinition;
import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.server.config.client.BackendCfgClient;
import org.forgerock.opendj.server.config.meta.LocalDBBackendCfgDefn;
import org.forgerock.opendj.server.config.meta.PluggableBackendCfgDefn;
import org.forgerock.opendj.server.config.server.BackendCfg;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.server.backends.jeb.RemoveOnceLocalDBBackendIsPluggable;

/**
 * Helper class for setup applications. It helps applications to provide a
 * backend type choice to the user.
 */
public class BackendTypeHelper
{

  /**
   * Filter the provided backend name by removing the backend suffix.
   *
   * @param dsCfgBackendName
   *          The backend name
   * @return The backend name with the '-backend' suffix filtered out
   */
  public static String filterSchemaBackendName(final String dsCfgBackendName)
  {
    final String cfgNameRegExp = "(.*)-backend.*";
    final Matcher regExpMatcher = Pattern.compile(cfgNameRegExp, Pattern.CASE_INSENSITIVE).matcher(dsCfgBackendName);
    if (regExpMatcher.matches())
    {
      return regExpMatcher.group(1);
    }

    return dsCfgBackendName;
  }

  /** Adaptor to allow backend type selection in UIs. */
  public static class BackendTypeUIAdapter
  {
    private final ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg> backend;

    /**
     * Create a new {@code BackendTypeUIAdapter}.
     *
     * @param backend
     *          The backend to adapt
     */
    private BackendTypeUIAdapter(ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg> backend)
    {
      this.backend = backend;
    }

    /**
     * Return a user friendly readable name for this backend.
     *
     * @return A user friendly readable name for this backend.
     */
    @Override
    public String toString()
    {
      return backend.getUserFriendlyName().toString();
    }

    /**
     * Return the adapted backend object.
     *
     * @return The adapted backend object
     */
    public ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg> getBackend()
    {
      return backend;
    }

    /**
     * Return the old configuration framework backend object.
     *
     * @return The old configuration framework backend object
     */
    @SuppressWarnings("unchecked")
    public org.opends.server.admin.ManagedObjectDefinition<
        ? extends org.opends.server.admin.std.client.BackendCfgClient,
        ? extends org.opends.server.admin.std.server.BackendCfg> getLegacyConfigurationFrameworkBackend()
    {
      Utilities.initializeLegacyConfigurationFramework();
      if (isLocalDBBackend())
      {
        return org.opends.server.admin.std.meta.LocalDBBackendCfgDefn.getInstance();
      }

      for (org.opends.server.admin.AbstractManagedObjectDefinition<?, ?> oldConfigBackend :
        org.opends.server.admin.std.meta.PluggableBackendCfgDefn.getInstance().getAllChildren())
      {
        if (oldConfigBackend.getName().equals(getBackend().getName()))
        {
          return (org.opends.server.admin.ManagedObjectDefinition<
              ? extends org.opends.server.admin.std.client.BackendCfgClient,
              ? extends org.opends.server.admin.std.server.BackendCfg>) oldConfigBackend;
        }
      }
      throw new IllegalArgumentException("Impossible to find the equivalent backend type in old config framework: "
          + getBackend().getName());
    }

    @RemoveOnceLocalDBBackendIsPluggable
    private boolean isLocalDBBackend()
    {
      return getBackend().getName().equals(LocalDBBackendCfgDefn.getInstance().getName());
    }
  }

  private final List<ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg>> backends;

  /** Creates a new backend type helper. */
  @SuppressWarnings("unchecked")
  public BackendTypeHelper()
  {
    Utilities.initializeConfigurationFramework();

    backends = new LinkedList<>();

    // Add the JE backend if it is supported in this release.
    try
    {
      Class.forName("org.opends.server.backends.jeb.BackendImpl");
      backends.add(LocalDBBackendCfgDefn.getInstance());
    }
    catch (ClassNotFoundException ignored)
    {
      // Ignore: JE backend not supported.
    }

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
    for (ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg> backend : getBackendTypes())
    {
      backendTypeNames += filterSchemaBackendName(backend.getName()) + ", ";
    }

    if (backendTypeNames.isEmpty())
    {
      return "Impossible to retrieve supported backend type list";
    }

    return backendTypeNames.substring(0, backendTypeNames.length() - 2);
  }

  /**
   * Return a list which contains all available backend type adapted for UI.
   *
   * @return a list which contains all available backend type adapted for UI
   */
  public BackendTypeUIAdapter[] getBackendTypeUIAdaptors()
  {
    List<BackendTypeUIAdapter> adaptors = new ArrayList<>();
    for (ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg> backend : getBackendTypes())
    {
      adaptors.add(new BackendTypeUIAdapter(backend));
    }

    return adaptors.toArray(new BackendTypeUIAdapter[adaptors.size()]);
  }

  /**
   * Return a BackendTypeUIAdapter which adapts the backend identified by the
   * provided backend name.
   *
   * @param backendName
   *          the backend name which identifies the backend to adapt.
   * @return a BackendTypeUIAdapter which adapts the backend identified by the
   *         provided backend name.
   */
  public static BackendTypeUIAdapter getBackendTypeAdapter(String backendName)
  {
    ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg> backend =
        new BackendTypeHelper().retrieveBackendTypeFromName(backendName);
    return backend != null ? getBackendTypeAdapter(backend) : null;
  }

  /**
   * Return a BackendTypeUIAdapter which adapts the provided backend.
   *
   * @param backend
   *          the backend type to adapt.
   * @return a BackendTypeUIAdapter which adapts the provided backend.
   */
  public static BackendTypeUIAdapter getBackendTypeAdapter(
      ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg> backend)
  {
    return new BackendTypeUIAdapter(backend);
  }

}
