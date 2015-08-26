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
 *      Copyright 2015-2016 ForgeRock AS
 */
package org.opends.server.types;

import static org.opends.server.util.StaticUtils.*;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.util.Reject;

/** Temporary class until we move to {@link org.forgerock.opendj.ldap.AttributeDescription}. */
public final class AttributeDescription implements Comparable<AttributeDescription>
{
  private final AttributeType attributeType;
  private final Set<String> options;

  private AttributeDescription(AttributeType attributeType, Set<String> options)
  {
    Reject.ifNull(attributeType);
    Reject.ifNull(options);
    this.attributeType = attributeType;
    this.options = options;
  }

  /**
   * Creates an attribute description with the attribute type and options of the provided
   * {@link Attribute}.
   *
   * @param attr
   *          The attribute.
   * @return The attribute description.
   * @throws NullPointerException
   *           If {@code attributeType} or {@code options} was {@code null}.
   */
  public static AttributeDescription create(Attribute attr)
  {
    return create(attr.getAttributeType(), attr.getOptions());
  }

  /**
   * Creates an attribute description having the provided attribute type and options.
   *
   * @param attributeType
   *          The attribute type.
   * @param options
   *          The attribute options.
   * @return The attribute description.
   * @throws NullPointerException
   *           If {@code attributeType} or {@code options} was {@code null}.
   */
  public static AttributeDescription create(AttributeType attributeType, Set<String> options)
  {
    return new AttributeDescription(attributeType, options);
  }

  /**
   * Returns the attribute type associated with this attribute description.
   *
   * @return The attribute type associated with this attribute description.
   */
  public AttributeType getAttributeType()
  {
    return attributeType;
  }

  /**
   * Returns the set of not normalized options contained in this attribute description.
   *
   * @return A set containing the not normalized options.
   */
  public Set<String> getOptions()
  {
    return options;
  }

  /**
   * Indicates whether the provided set of not normalized options contains the provided option.
   *
   * @param options
   *          The set of not normalized options where to do the search.
   * @param optionToFind
   *          The option for which to make the determination.
   * @return {@code true} if the provided set of options has the provided option, or {@code false}
   *         if not.
   */
  public static boolean containsOption(Set<String> options, String optionToFind)
  {
    String normToFind = toLowerCase(optionToFind);

    // Cannot use Set.contains() because the options are not normalized.
    for (String o : options)
    {
      String norm = toLowerCase(o);
      if (norm.equals(normToFind))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Indicates whether the provided first set of not normalized options contains all values from the
   * second set of not normalized options.
   *
   * @param options1
   *          The first set of not normalized options where to do the search.
   * @param options2
   *          The second set of not normalized options that must all be found.
   * @return {@code true} if the first provided set of options has all the options from the second
   *         provided set of options.
   */
  public static boolean containsAllOptions(Collection<String> options1, Collection<String> options2)
  {
    if (options1 == options2)
    {
      return true;
    }
    else if (isEmpty(options2))
    {
      return true;
    }
    else if (isEmpty(options1))
    {
      return false;
    }
    // normalize all options before calling containsAll()
    Set<String> set1 = toLowercaseSet(options1);
    Set<String> set2 = toLowercaseSet(options2);
    return set1.size() >= set2.size() && set1.containsAll(set2);
  }

  /**
   * Indicates whether the provided first set of not normalized options equals the second set of not
   * normalized options.
   *
   * @param options1
   *          The first set of not normalized options.
   * @param options2
   *          The second set of not normalized options.
   * @return {@code true} if the first provided set of options equals the second provided set of
   *         options.
   */
  public static boolean optionsEqual(Set<String> options1, Set<String> options2)
  {
    if (options1 == options2)
    {
      return true;
    }
    else if (isEmpty(options2))
    {
      return isEmpty(options1);
    }
    else if (isEmpty(options1))
    {
      return false;
    }
    // normalize all options before calling containsAll()
    Set<String> set1 = toLowercaseSet(options1);
    Set<String> set2 = toLowercaseSet(options2);
    return set1.equals(set2);
  }

  private static boolean isEmpty(Collection<String> col)
  {
    return col == null || col.isEmpty();
  }

  private static SortedSet<String> toLowercaseSet(Collection<String> strings)
  {
    final SortedSet<String> results = new TreeSet<>();
    for (String s : strings)
    {
      results.add(toLowerCase(s));
    }
    return results;
  }

  @Override
  public int compareTo(AttributeDescription other)
  {
    if (this == other)
    {
      return 0;
    }
    return compare(attributeType, options, other.attributeType, other.options);
  }

  /**
   * Compares the first attribute type and options to the second attribute type and options, as if
   * they were both instances of {@link AttributeDescription}.
   * <p>
   * The attribute types are compared first and then, if equal, the options are normalized, sorted,
   * and compared.
   *
   * @param attrType1
   *          The first attribute type to be compared.
   * @param options1
   *          The first options to be compared.
   * @param attrType2
   *          The second attribute type to be compared.
   * @param options2
   *          The second options to be compared.
   * @return A negative integer, zero, or a positive integer as this attribute description is less
   *         than, equal to, or greater than the specified attribute description.
   * @throws NullPointerException
   *           If {@code name} was {@code null}.
   * @see AttributeDescription#compareTo(AttributeDescription)
   */
  public static int compare(AttributeType attrType1, Set<String> options1,
      AttributeType attrType2, Set<String> options2)
  {
    int cmp = attrType1.compareTo(attrType2);
    if (cmp != 0)
    {
      return cmp;
    }
    if (options1 == options2)
    {
      return 0;
    }
    return compare(toLowercaseSet(options1), toLowercaseSet(options2));
  }

  private static int compare(SortedSet<String> options1, SortedSet<String> options2)
  {
    Iterator<String> it1 = options1.iterator();
    Iterator<String> it2 = options2.iterator();
    while (it1.hasNext() && it2.hasNext())
    {
      int cmp = it1.next().compareTo(it2.next());
      if (cmp != 0)
      {
        return cmp;
      }
    }
    if (it1.hasNext())
    {
      return 1;
    }
    else if (it2.hasNext())
    {
      return -1;
    }
    return 0;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (obj == this)
    {
      return true;
    }
    if (!(obj instanceof AttributeDescription))
    {
      return false;
    }
    final AttributeDescription other = (AttributeDescription) obj;
    return attributeType.equals(other.attributeType) && optionsEqual(options, other.options);
  }

  @Override
  public int hashCode()
  {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((attributeType == null) ? 0 : attributeType.hashCode());
    result = prime * result + ((options == null) ? 0 : options.hashCode());
    return result;
  }

  @Override
  public String toString()
  {
    final StringBuilder buffer = new StringBuilder();
    buffer.append(attributeType.getNameOrOID());
    for (String option : options)
    {
      buffer.append(';');
      buffer.append(option);
    }
    return buffer.toString();
  }
}
