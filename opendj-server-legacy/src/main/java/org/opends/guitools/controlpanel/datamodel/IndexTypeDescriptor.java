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
package org.opends.guitools.controlpanel.datamodel;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.opends.server.admin.std.meta.BackendIndexCfgDefn;
import org.opends.server.admin.std.meta.LocalDBIndexCfgDefn;
import org.opends.server.backends.jeb.RemoveOnceLocalDBBackendIsPluggable;

/**
 * Defines the set of values for the index type and provides adaptors to convert
 * from/to corresponding configuration classes.
 */
@RemoveOnceLocalDBBackendIsPluggable
public enum IndexTypeDescriptor
{
  /**
   * This index type is used to improve the efficiency of searches using
   * approximate matching search filters.
   */
  APPROXIMATE(LocalDBIndexCfgDefn.IndexType.APPROXIMATE, BackendIndexCfgDefn.IndexType.APPROXIMATE,
      org.forgerock.opendj.server.config.meta.LocalDBIndexCfgDefn.IndexType.APPROXIMATE,
      org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType.APPROXIMATE),

  /**
   * This index type is used to improve the efficiency of searches using
   * equality search filters.
   */
  EQUALITY(LocalDBIndexCfgDefn.IndexType.EQUALITY, BackendIndexCfgDefn.IndexType.EQUALITY,
      org.forgerock.opendj.server.config.meta.LocalDBIndexCfgDefn.IndexType.EQUALITY,
      org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType.EQUALITY),

  /**
   * This index type is used to improve the efficiency of searches using
   * extensible matching search filters.
   */
  EXTENSIBLE(LocalDBIndexCfgDefn.IndexType.EXTENSIBLE, BackendIndexCfgDefn.IndexType.EXTENSIBLE,
      org.forgerock.opendj.server.config.meta.LocalDBIndexCfgDefn.IndexType.EXTENSIBLE,
      org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType.EXTENSIBLE),

  /**
   * This index type is used to improve the efficiency of searches using
   * "greater than or equal to" or "less then or equal to" search filters.
   */
  ORDERING(LocalDBIndexCfgDefn.IndexType.ORDERING, BackendIndexCfgDefn.IndexType.ORDERING,
      org.forgerock.opendj.server.config.meta.LocalDBIndexCfgDefn.IndexType.ORDERING,
      org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType.ORDERING),

  /**
   * This index type is used to improve the efficiency of searches using the
   * presence search filters.
   */
  PRESENCE(LocalDBIndexCfgDefn.IndexType.PRESENCE, BackendIndexCfgDefn.IndexType.PRESENCE,
      org.forgerock.opendj.server.config.meta.LocalDBIndexCfgDefn.IndexType.PRESENCE,
      org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType.PRESENCE),

  /**
   * This index type is used to improve the efficiency of searches using
   * substring search filters.
   */
  SUBSTRING(LocalDBIndexCfgDefn.IndexType.SUBSTRING, BackendIndexCfgDefn.IndexType.SUBSTRING,
      org.forgerock.opendj.server.config.meta.LocalDBIndexCfgDefn.IndexType.SUBSTRING,
      org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType.SUBSTRING);

  private final LocalDBIndexCfgDefn.IndexType oldConfigLocalDBIndexType;
  private final BackendIndexCfgDefn.IndexType oldConfigBackendIndexType;
  private final org.forgerock.opendj.server.config.meta.LocalDBIndexCfgDefn.IndexType localDBIndexType;
  private final org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType backendIndexType;

  private IndexTypeDescriptor(final LocalDBIndexCfgDefn.IndexType oldConfigLocalDBIndexType,
      final BackendIndexCfgDefn.IndexType oldConfigBackendIndexType,
      final org.forgerock.opendj.server.config.meta.LocalDBIndexCfgDefn.IndexType localDBIndexType,
      final org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType backendIndexType)
  {
    this.oldConfigLocalDBIndexType = oldConfigLocalDBIndexType;
    this.oldConfigBackendIndexType = oldConfigBackendIndexType;
    this.localDBIndexType = localDBIndexType;
    this.backendIndexType = backendIndexType;
  }

  /**
   * Convert the index type to the equivalent
   * {@code BackendIndexCfgDefn.IndexType}.
   *
   * @return The index type to the equivalent
   *         {@code BackendIndexCfgDefn.IndexType}
   */
  public BackendIndexCfgDefn.IndexType toBackendIndexType()
  {
    return oldConfigBackendIndexType;
  }

  /**
   * Convert the index type to the equivalent
   * {@code LocalDBIndexCfgDefn.IndexType}.
   *
   * @return The index type to the equivalent
   *         {@code LocalDBIndexCfgDefn.IndexType}
   */
  public LocalDBIndexCfgDefn.IndexType toLocalDBIndexType()
  {
    return oldConfigLocalDBIndexType;
  }

  private static IndexTypeDescriptor fromBackendIndexType(final BackendIndexCfgDefn.IndexType indexType)
  {
    switch (indexType)
    {
    case APPROXIMATE:
      return APPROXIMATE;
    case EQUALITY:
      return EQUALITY;
    case EXTENSIBLE:
      return EXTENSIBLE;
    case ORDERING:
      return ORDERING;
    case PRESENCE:
      return PRESENCE;
    case SUBSTRING:
      return SUBSTRING;
    default:
      throw new IllegalArgumentException("No IndexTypeDescriptor corresponding to: " + indexType);
    }
  }

  private static IndexTypeDescriptor fromLocalDBIndexType(final LocalDBIndexCfgDefn.IndexType indexType)
  {
    switch (indexType)
    {
    case APPROXIMATE:
      return APPROXIMATE;
    case EQUALITY:
      return EQUALITY;
    case EXTENSIBLE:
      return EXTENSIBLE;
    case ORDERING:
      return ORDERING;
    case PRESENCE:
      return PRESENCE;
    case SUBSTRING:
      return SUBSTRING;
    default:
      throw new IllegalArgumentException("No IndexTypeDescriptor corresponding to: " + indexType);
    }
  }

  /**
   * Convert the provided {@code Set<BackendIndexCfgDefn.IndexType>} to a
   * {@code Set<IndexTypeDescriptor>}.
   *
   * @param indexTypes
   *          A set of {@code Set<BackendIndexCfgDefn.IndexType>}
   * @return A set of {@code Set<IndexTypeDescriptor>} corresponding to the
   *         provided {@code Set<BackendIndexCfgDefn.IndexType>}
   */
  public static Set<IndexTypeDescriptor> fromBackendIndexTypes(final Set<BackendIndexCfgDefn.IndexType> indexTypes)
  {
    final Set<IndexTypeDescriptor> indexTypeDescriptors = new LinkedHashSet<>();
    for (final BackendIndexCfgDefn.IndexType indexType : indexTypes)
    {
      indexTypeDescriptors.add(fromBackendIndexType(indexType));
    }
    return indexTypeDescriptors;
  }

  /**
   * Convert the provided {@code Set<LocalDBIndexCfgDefn.IndexType} to a
   * {@code Set<IndexTypeDescriptor>}.
   *
   * @param indexTypes
   *          A set of {@code Set<LocalDBIndexCfgDefn.IndexType>}
   * @return A set of {@code Set<IndexTypeDescriptor>} corresponding to the
   *         provided {@code Set<LocalDBIndexCfgDefn.IndexType>}
   */
  public static Set<IndexTypeDescriptor> fromLocalDBIndexTypes(final Set<LocalDBIndexCfgDefn.IndexType> indexTypes)
  {
    final Set<IndexTypeDescriptor> indexTypeDescriptors = new LinkedHashSet<>();
    for (final LocalDBIndexCfgDefn.IndexType indexType : indexTypes)
    {
      indexTypeDescriptors.add(fromLocalDBIndexType(indexType));
    }
    return indexTypeDescriptors;
  }

  /**
   * Convert the provided {@code Set<IndexTypeDescriptor>} to a
   * {@code Set<BackendIndexCfgDefn.IndexType>}.
   *
   * @param indexTypeDescriptors
   *          A set of {@code Set<IndexTypeDescriptor>}
   * @return A set of {@code Set<BackendIndexCfgDefn.IndexType>} corresponding
   *         to the provided {@code Set<IndexTypeDescriptor>}
   */
  public static Set<BackendIndexCfgDefn.IndexType> toBackendIndexTypes(
      final Set<IndexTypeDescriptor> indexTypeDescriptors)
  {
    final Set<BackendIndexCfgDefn.IndexType> indexTypes = new LinkedHashSet<>();
    for (final IndexTypeDescriptor indexTypeDescriptor : indexTypeDescriptors)
    {
      indexTypes.add(indexTypeDescriptor.toBackendIndexType());
    }
    return indexTypes;
  }

  /**
   * Convert the provided {@code Set<IndexTypeDescriptor>} to a
   * {@code Set<LocalDBIndexCfgDefn.IndexType>}.
   *
   * @param indexTypeDescriptors
   *          A set of {@code Set<IndexTypeDescriptor>}
   * @return A set of {@code Set<LocalDBIndexCfgDefn.IndexType>} corresponding
   *         to the provided {@code Set<IndexTypeDescriptor>}
   */
  public static Set<LocalDBIndexCfgDefn.IndexType> toLocalDBIndexTypes(
      final Set<IndexTypeDescriptor> indexTypeDescriptors)
  {
    final Set<LocalDBIndexCfgDefn.IndexType> indexTypes = new LinkedHashSet<>();
    for (final IndexTypeDescriptor indexTypeDescriptor : indexTypeDescriptors)
    {
      indexTypes.add(indexTypeDescriptor.toLocalDBIndexType());
    }
    return indexTypes;
  }

  /**
   * Convert the provided {@code Set<IndexTypeDescriptor>} to a
   * {@code Set<org.forgerock.opendj.server.config.meta.LocalDBIndexCfgDefn.IndexType>}.
   *
   * @param indexTypeDescriptors
   *          A set of {@code Set<IndexTypeDescriptor>}
   * @return A set of
   *         {@code Set<org.forgerock.opendj.server.config.meta.LocalDBIndexCfgDefn.IndexType>}
   *         corresponding to the provided {@code Set<IndexTypeDescriptor>}
   */
  @RemoveOnceLocalDBBackendIsPluggable
  public static Set<org.forgerock.opendj.server.config.meta.LocalDBIndexCfgDefn.IndexType> toNewConfigLocalDBIndexTypes(
      final Set<IndexTypeDescriptor> indexTypeDescriptors)
  {
    Set<org.forgerock.opendj.server.config.meta.LocalDBIndexCfgDefn.IndexType> newConfigIndexTypes = new HashSet<>();
    for (IndexTypeDescriptor indexType : indexTypeDescriptors)
    {
      newConfigIndexTypes.add(indexType.localDBIndexType);
    }
    return newConfigIndexTypes;
  }

  /**
   * Convert the provided {@code Set<IndexTypeDescriptor>} to a
   * {@code Set<org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType>}.
   *
   * @param indexTypeDescriptors
   *          A set of {@code Set<IndexTypeDescriptor>}
   * @return A set of
   *         {@code Set<org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType>}
   *         corresponding to the provided {@code Set<IndexTypeDescriptor>}
   */
  public static Set<org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType> toNewConfigBackendIndexTypes(
      final Set<IndexTypeDescriptor> indexTypeDescriptors)
  {
    Set<org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType> newConfigIndexTypes = new HashSet<>();
    for (IndexTypeDescriptor indexType : indexTypeDescriptors)
    {
      newConfigIndexTypes.add(indexType.backendIndexType);
    }
    return newConfigIndexTypes;
  }

}
