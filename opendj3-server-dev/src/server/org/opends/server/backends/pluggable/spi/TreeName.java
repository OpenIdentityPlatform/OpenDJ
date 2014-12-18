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
 *      Copyright 2014 ForgeRock AS
 */
package org.opends.server.backends.pluggable.spi;

/** Assumes name components don't contain a '/'. */
public final class TreeName
{
  private final String baseDN;
  private final String indexId;
  private final String s;

  public TreeName(String baseDN, String indexId)
  {
    this.baseDN = baseDN;
    this.indexId = indexId;
    this.s = '/' + baseDN + '/' + indexId;
  }

  public String getBaseDN()
  {
    return baseDN;
  }

  public TreeName replaceBaseDN(String newBaseDN)
  {
    return new TreeName(newBaseDN, indexId);
  }

  public String getIndexId()
  {
    return indexId;
  }

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

  @Override
  public int hashCode()
  {
    return s.hashCode();
  }

  @Override
  public String toString()
  {
    return s;
  }
}