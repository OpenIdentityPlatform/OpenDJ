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
 * Copyright 2014-2015 ForgeRock AS.
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
