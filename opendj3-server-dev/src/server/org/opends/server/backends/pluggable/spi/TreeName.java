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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Assumes name components don't contain a '/'. */
public final class TreeName
{
  public static TreeName of(final String... names)
  {
    return new TreeName(Arrays.asList(names));
  }

  private final List<String> names;
  private final String s;

  public TreeName(final List<String> names)
  {
    this.names = names;
    final StringBuilder builder = new StringBuilder();
    for (final String name : names)
    {
      builder.append('/');
      builder.append(name);
    }
    this.s = builder.toString();
  }

  public List<String> getNames()
  {
    return names;
  }

  public TreeName child(final String name)
  {
    final List<String> newNames = new ArrayList<String>(names.size() + 1);
    newNames.addAll(names);
    newNames.add(name);
    return new TreeName(newNames);
  }

  public TreeName getSuffix()
  {
    if (names.size() == 0)
    {
      throw new IllegalStateException();
    }
    return new TreeName(Collections.singletonList(names.get(0)));
  }

  public TreeName replaceSuffix(TreeName newSuffix)
  {
    if (names.size() == 0)
    {
      throw new IllegalStateException();
    }
    final ArrayList<String> newNames = new ArrayList<String>(names);
    newNames.set(0, newSuffix.names.get(0));
    return new TreeName(newNames);
  }

  public boolean isSuffixOf(TreeName treeName)
  {
    if (names.size() > treeName.names.size())
    {
      return false;
    }
    for (int i = 0; i < names.size(); i++)
    {
      if (!treeName.names.get(i).equals(names.get(i)))
      {
        return false;
      }
    }
    return true;
  }

  public TreeName getIndex()
  {
    if (names.size() == 1)
    {
      return null;
    }
    return new TreeName(names.subList(1, names.size()));
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