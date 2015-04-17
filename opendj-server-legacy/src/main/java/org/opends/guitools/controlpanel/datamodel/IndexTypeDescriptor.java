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

import java.util.LinkedHashSet;
import java.util.Set;

import org.opends.server.admin.std.meta.BackendIndexCfgDefn;
import org.opends.server.admin.std.meta.LocalDBIndexCfgDefn;
import org.opends.server.backends.jeb.RemoveOnceLocalDBBackendIsPluggable;

/** Defines the set of values for the index type. */
@RemoveOnceLocalDBBackendIsPluggable
public enum IndexTypeDescriptor
{
  /**
   * This index type is used to improve the efficiency of searches using
   * approximate matching search filters.
   */
  APPROXIMATE("approximate"),

  /**
   * This index type is used to improve the efficiency of searches using
   * equality search filters.
   */
  EQUALITY("equality"),

  /**
   * This index type is used to improve the efficiency of searches using
   * extensible matching search filters.
   */
  EXTENSIBLE("extensible"),

  /**
   * This index type is used to improve the efficiency of searches using
   * "greater than or equal to" or "less then or equal to" search filters.
   */
  ORDERING("ordering"),

  /**
   * This index type is used to improve the efficiency of searches using the
   * presence search filters.
   */
  PRESENCE("presence"),

  /**
   * This index type is used to improve the efficiency of searches using
   * substring search filters.
   */
  SUBSTRING("substring");

  private final String name;

  private IndexTypeDescriptor(final String name)
  {
    this.name = name;
  }

  @Override
  public String toString()
  {
    return name;
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
    switch (this)
    {
    case APPROXIMATE:
      return BackendIndexCfgDefn.IndexType.APPROXIMATE;
    case EQUALITY:
      return BackendIndexCfgDefn.IndexType.EQUALITY;
    case EXTENSIBLE:
      return BackendIndexCfgDefn.IndexType.EXTENSIBLE;
    case ORDERING:
      return BackendIndexCfgDefn.IndexType.ORDERING;
    case PRESENCE:
      return BackendIndexCfgDefn.IndexType.PRESENCE;
    case SUBSTRING:
      return BackendIndexCfgDefn.IndexType.SUBSTRING;
    default:
      throw new IllegalArgumentException("No BackendIndexCfgDefn.IndexType corresponding to: " + this);
    }
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
    switch (this)
    {
    case APPROXIMATE:
      return LocalDBIndexCfgDefn.IndexType.APPROXIMATE;
    case EQUALITY:
      return LocalDBIndexCfgDefn.IndexType.EQUALITY;
    case EXTENSIBLE:
      return LocalDBIndexCfgDefn.IndexType.EXTENSIBLE;
    case ORDERING:
      return LocalDBIndexCfgDefn.IndexType.ORDERING;
    case PRESENCE:
      return LocalDBIndexCfgDefn.IndexType.PRESENCE;
    case SUBSTRING:
      return LocalDBIndexCfgDefn.IndexType.SUBSTRING;
    default:
      throw new IllegalArgumentException("No LocalDBIndexCfgDefn.IndexType corresponding to: " + this);
    }
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
    final Set<IndexTypeDescriptor> indexTypeDescriptors = new LinkedHashSet<IndexTypeDescriptor>();
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
    final Set<IndexTypeDescriptor> indexTypeDescriptors = new LinkedHashSet<IndexTypeDescriptor>();
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
    final Set<BackendIndexCfgDefn.IndexType> indexTypes = new LinkedHashSet<BackendIndexCfgDefn.IndexType>();
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
    final Set<LocalDBIndexCfgDefn.IndexType> indexTypes = new LinkedHashSet<LocalDBIndexCfgDefn.IndexType>();
    for (final IndexTypeDescriptor indexTypeDescriptor : indexTypeDescriptors)
    {
      indexTypes.add(indexTypeDescriptor.toLocalDBIndexType());
    }
    return indexTypes;
  }

}
