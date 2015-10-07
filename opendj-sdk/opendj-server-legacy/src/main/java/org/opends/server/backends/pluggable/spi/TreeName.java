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
 *      Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable.spi;

import org.forgerock.util.Reject;

/**
 * Represents the name of a tree (key-value store) in a database.
 * A tree name is made of the baseDN it is part of, and the identifier of the index it represents.
 * <p>
 * Note: This class assumes name components don't contain a '/'.
 */
public final class TreeName implements Comparable<TreeName>
{
  private final String baseDN;
  private final String indexId;
  private final String s;

  /**
   * Builds a tree name.
   *
   * @param baseDN
   *          the base DN
   * @param indexId
   *          the index identifier
   */
  public TreeName(String baseDN, String indexId)
  {
    this.baseDN = baseDN;
    this.indexId = indexId;
    this.s = '/' + baseDN + '/' + indexId;
  }

  /**
   * Builds a new {@link TreeName} object based on the provided string representation.
   *
   * @param treeName the string representation of the tree name
   * @return a new {@link TreeName} object constructed from the provided string
   */
  public static TreeName valueOf(String treeName)
  {
    int lastSlash = treeName.lastIndexOf('/');
    Reject.ifTrue(lastSlash < 2 || treeName.charAt(0) != '/', "TreeName is not of the form /<name>/<name>");
    String baseDN = treeName.substring(1, lastSlash);
    String indexId = treeName.substring(lastSlash + 1);
    return new TreeName(baseDN, indexId);
  }

  /**
   * Returns the base DN.
   *
   * @return a {@code String} representing the base DN
   */
  public String getBaseDN()
  {
    return baseDN;
  }

  /**
   * Returns the index identifier.
   *
   * @return a {@code String} representing the base DN
   */
  public String getIndexId()
  {
    return indexId;
  }



  /** {@inheritDoc} */
  @Override
  public boolean equals(final Object obj)
  {
    if (this == obj)
    {
      return true;
    }
    else if (obj instanceof TreeName)
    {
      return s.equals(((TreeName) obj).s);
    }
    else
    {
      return false;
    }
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode()
  {
    return s.hashCode();
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return s;
  }

  @Override
  public int compareTo(TreeName o)
  {
    return s.compareTo(o.s);
  }
}
