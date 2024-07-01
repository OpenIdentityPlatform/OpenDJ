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

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.config.client.ldap.LDAPManagementContext;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.client.BackendCfgClient;
import org.forgerock.opendj.server.config.client.BackendIndexCfgClient;
import org.forgerock.opendj.server.config.client.LocalBackendCfgClient;
import org.forgerock.opendj.server.config.client.PluggableBackendCfgClient;
import org.forgerock.opendj.server.config.client.RootCfgClient;
import org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn;
import org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType;
import org.forgerock.opendj.server.config.meta.LocalBackendCfgDefn.WritabilityMode;
import org.forgerock.opendj.server.config.server.BackendCfg;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.quicksetup.Installation;

/** Utility class which can be used by tools to create a new backend with default indexes. */
public class BackendCreationHelper
{
  /** Describes an attribute index which should be created during installation. */
  public static final class CreateIndex
  {
    private static CreateIndex withEqualityAndSubstring(final String name)
    {
      return new CreateIndex(name, true);
    }

    private static CreateIndex withEquality(final String name)
    {
      return new CreateIndex(name, false);
    }

    private final String name;
    private final boolean shouldCreateSubstringIndex;

    private CreateIndex(final String name, final boolean substringIndex)
    {
      this.name = name;
      this.shouldCreateSubstringIndex = substringIndex;
    }

    /**
     * Return the name of this default index.
     *
     * @return The name of this default index
     */
    public String getName()
    {
      return name;
    }

    /**
     * Return {@code true} if the substring index type should be enabled for
     * this index.
     *
     * @return {@code true} if the substring index type should be enabled for
     *         this index.
     */
    public boolean shouldCreateSubstringIndex()
    {
      return shouldCreateSubstringIndex;
    }

    @Override
    public String toString()
    {
      String className = getClass().getSimpleName();
      if (shouldCreateSubstringIndex)
      {
        return className + "(" + name + ".equality" + ", " + name + ".substring" + ")";
      }
      return className + "(" + name + ".equality" + ")";
    }
  }

  /** Default indexes to add in a new backend. */
  public static final CreateIndex[] DEFAULT_INDEXES = {
    CreateIndex.withEqualityAndSubstring("cn"),
    CreateIndex.withEqualityAndSubstring("givenName"),
    CreateIndex.withEqualityAndSubstring("mail"),
    CreateIndex.withEqualityAndSubstring("sn"),
    CreateIndex.withEqualityAndSubstring("telephoneNumber"),
    CreateIndex.withEquality("member"),
    CreateIndex.withEquality("uid"),
    CreateIndex.withEquality("uniqueMember")
  };

  /**
   * Add a new backend with the provided name in the config.ldif file.
   *
   * @param backendName
   *          The new backend name
   * @param baseDNs
   *          The base dns to add in the new backend.
   * @param backendType
   *          The backend type
   * @throws Exception
   *           If any problems occurred
   */
  public static void createBackendOffline(String backendName, Collection<DN> baseDNs,
      ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg> backendType) throws Exception
  {
    Utilities.initializeConfigurationFramework();
    final File configFile = Installation.getLocal().getCurrentConfigurationFile();
    try (ManagementContext context = LDAPManagementContext.newLDIFManagementContext(configFile))
    {
      createBackend(context.getRootConfiguration(), backendName, baseDNs, backendType);
    }
  }

  /**
   * Add a new backend with the provided name in the config.ldif file.
   *
   * @param backendName
   *          The new backend name
   * @param baseDNs
   *          The base dns to add in the new backend.
   * @param backendType
   *          The backend type
   * @param conn
   *          The connection to the server
   * @throws Exception
   *           If any problems occurred
   */
  public static void createBackendOnline(String backendName, Collection<DN> baseDNs,
      ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg> backendType, ConnectionWrapper conn)
      throws Exception
  {
    createBackend(conn.getRootConfiguration(), backendName, baseDNs, backendType);
  }

  /**
   * Create a backend with the provided name using the provided
   * {@code RootCfgClient}.
   *
   * @param rootConfiguration
   *          The root configuration to use to create the new backend
   * @param backendName
   *          The new backend name
   * @param baseDNs
   *          The base dns to add in the new backend.
   * @param backendType
   *          The backend type
   * @throws Exception
   *           If any problems occurred
   */
  private static void createBackend(RootCfgClient rootConfiguration, String backendName, Collection<DN> baseDNs,
      ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg> backendType) throws Exception
  {
    final LocalBackendCfgClient backendCfgClient =
        (LocalBackendCfgClient) rootConfiguration.createBackend(backendType, backendName, null);
    backendCfgClient.setEnabled(true);
    backendCfgClient.setBaseDN(baseDNs);
    backendCfgClient.setWritabilityMode(WritabilityMode.ENABLED);
    backendCfgClient.commit();

    addBackendDefaultIndexes((PluggableBackendCfgClient) backendCfgClient);
  }

  private static void addBackendDefaultIndexes(PluggableBackendCfgClient backendCfgClient) throws Exception
  {
    for (CreateIndex index : DEFAULT_INDEXES)
    {
      final BackendIndexCfgClient indexCfg =
          backendCfgClient.createBackendIndex(BackendIndexCfgDefn.getInstance(), index.name, null);

      final List<IndexType> indexTypes = new LinkedList<>();
      indexTypes.add(IndexType.EQUALITY);
      if (index.shouldCreateSubstringIndex)
      {
        indexTypes.add(IndexType.SUBSTRING);
      }
      indexCfg.setIndexType(indexTypes);

      indexCfg.commit();
    }
  }
}
