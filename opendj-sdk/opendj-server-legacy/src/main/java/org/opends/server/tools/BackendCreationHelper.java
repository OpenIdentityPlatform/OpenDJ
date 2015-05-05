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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.opendj.config.LDAPProfile;
import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.config.client.ldap.LDAPManagementContext;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.client.BackendCfgClient;
import org.forgerock.opendj.server.config.client.BackendIndexCfgClient;
import org.forgerock.opendj.server.config.client.LocalDBBackendCfgClient;
import org.forgerock.opendj.server.config.client.LocalDBIndexCfgClient;
import org.forgerock.opendj.server.config.client.PluggableBackendCfgClient;
import org.forgerock.opendj.server.config.client.RootCfgClient;
import org.forgerock.opendj.server.config.meta.BackendCfgDefn.WritabilityMode;
import org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn;
import org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType;
import org.forgerock.opendj.server.config.meta.LocalDBBackendCfgDefn;
import org.forgerock.opendj.server.config.meta.LocalDBIndexCfgDefn;
import org.forgerock.opendj.server.config.server.BackendCfg;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.quicksetup.Installation;
import org.opends.server.backends.jeb.RemoveOnceLocalDBBackendIsPluggable;

/**
 * Utility class which can be used by tools to create a new backend with default indexes.
 */
public class BackendCreationHelper
{
  /** Describes an attribute index which should be created during installation. */
  public static final class DefaultIndex
  {
    static DefaultIndex withEqualityAndSubstring(final String name)
    {
      return new DefaultIndex(name, true);
    }

    static DefaultIndex withEquality(final String name)
    {
      return new DefaultIndex(name, false);
    }

    private final String name;
    private final boolean shouldCreateSubstringIndex;

    private DefaultIndex(final String name, final boolean substringIndex)
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
  }

  /** Default indexes to add in a new backend. */
  public static final DefaultIndex[] DEFAULT_INDEXES = {
    DefaultIndex.withEqualityAndSubstring("cn"),
    DefaultIndex.withEqualityAndSubstring("givenName"),
    DefaultIndex.withEqualityAndSubstring("mail"),
    DefaultIndex.withEqualityAndSubstring("sn"),
    DefaultIndex.withEqualityAndSubstring("telephoneNumber"),
    DefaultIndex.withEquality("member"),
    DefaultIndex.withEquality("uid"),
    DefaultIndex.withEquality("uniqueMember")
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
    final List<IOException> exceptions = new ArrayList<>();
    final ManagementContext context = LDAPManagementContext.newLDIFManagementContext(
        Installation.getLocal().getCurrentConfigurationFile(), LDAPProfile.getInstance(), exceptions);
    createBackend(context.getRootConfiguration(), backendName, baseDNs, backendType);
    context.close();
    Utilities.throwFirstFrom(exceptions);
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
  public static void createBackend(RootCfgClient rootConfiguration, String backendName, Collection<DN> baseDNs,
      ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg> backendType) throws Exception
  {
      final BackendCfgClient backendCfgClient = rootConfiguration.createBackend(backendType, backendName, null);
      backendCfgClient.setEnabled(true);
      backendCfgClient.setBaseDN(baseDNs);
      backendCfgClient.setWritabilityMode(WritabilityMode.ENABLED);
      backendCfgClient.commit();

      if (backendType instanceof LocalDBBackendCfgDefn)
      {
        addJEDefaultIndexes((LocalDBBackendCfgClient) backendCfgClient);
      }
      else
      {
        addBackendDefaultIndexes((PluggableBackendCfgClient) backendCfgClient);
      }
  }

  private static void addBackendDefaultIndexes(PluggableBackendCfgClient backendCfgClient) throws Exception
  {
    for (DefaultIndex defaultIndex : DEFAULT_INDEXES)
    {
      final BackendIndexCfgClient index =
          backendCfgClient.createBackendIndex(BackendIndexCfgDefn.getInstance(), defaultIndex.name, null);

      final List<IndexType> indexTypes = new LinkedList<>();
      indexTypes.add(IndexType.EQUALITY);
      if (defaultIndex.shouldCreateSubstringIndex)
      {
        indexTypes.add(IndexType.SUBSTRING);
      }
      index.setIndexType(indexTypes);

      index.commit();
    }
  }

  @RemoveOnceLocalDBBackendIsPluggable
  private static void addJEDefaultIndexes(final LocalDBBackendCfgClient jeBackendCfgClient) throws Exception
  {
    for (DefaultIndex defaultIndex : DEFAULT_INDEXES)
    {
      final LocalDBIndexCfgClient jeIndex =
          jeBackendCfgClient.createLocalDBIndex(LocalDBIndexCfgDefn.getInstance(), defaultIndex.name, null);

      final List<LocalDBIndexCfgDefn.IndexType> indexTypes = new ArrayList<>();
      indexTypes.add(LocalDBIndexCfgDefn.IndexType.EQUALITY);
      if (defaultIndex.shouldCreateSubstringIndex)
      {
        indexTypes.add(LocalDBIndexCfgDefn.IndexType.SUBSTRING);
      }
      jeIndex.setIndexType(indexTypes);

      jeIndex.commit();
    }
  }

}
