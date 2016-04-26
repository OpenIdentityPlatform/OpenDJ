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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.tools;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.forgerock.opendj.config.AbstractManagedObjectDefinition;
import org.forgerock.opendj.config.DefinedDefaultBehaviorProvider;
import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.server.config.client.BackendCfgClient;
import org.forgerock.opendj.server.config.meta.PluggableBackendCfgDefn;
import org.forgerock.opendj.server.config.server.BackendCfg;
import org.opends.guitools.controlpanel.util.Utilities;

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

    @Override
    public boolean equals(Object obj)
    {
      return obj instanceof BackendTypeUIAdapter && ((BackendTypeUIAdapter) obj).toString().equals(toString());
    }

    @Override
    public int hashCode()
    {
      return toString().hashCode();
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
  }

  private final List<ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg>> backends;

  /** Creates a new backend type helper. */
  @SuppressWarnings("unchecked")
  public BackendTypeHelper()
  {
    Utilities.initializeConfigurationFramework();

    backends = new LinkedList<>();

    for (AbstractManagedObjectDefinition<?, ?> backendType : PluggableBackendCfgDefn.getInstance().getAllChildren())
    {
      // Filtering out only the non-abstract backends to avoid users attempt to create abstract ones
      if (backendType instanceof ManagedObjectDefinition)
      {
        final DefinedDefaultBehaviorProvider<String> defaultBehaviorProvider =
                (DefinedDefaultBehaviorProvider<String>) backendType.getPropertyDefinition("java-class")
                                                                    .getDefaultBehaviorProvider();
        final Iterator<String> defaultBackendClassNameIterator = defaultBehaviorProvider.getDefaultValues().iterator();
        if (!defaultBackendClassNameIterator.hasNext())
        {
          return;
        }
        addToBackendListIfClassExists(defaultBackendClassNameIterator.next(),
                (ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg>) backendType);
      }
    }
  }

  private void addToBackendListIfClassExists(final String backendClassName,
          final ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg> backendToAdd)
  {
    try
    {
      Class.forName(backendClassName);
      backends.add(backendToAdd);
    }
    catch (ClassNotFoundException ignored)
    {
      // The backend is not supported in the running version.
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
  private static BackendTypeUIAdapter getBackendTypeAdapter(
      ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg> backend)
  {
    return new BackendTypeUIAdapter(backend);
  }
}
