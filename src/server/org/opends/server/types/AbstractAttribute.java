/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import static org.opends.server.util.StaticUtils.*;

import java.util.Collection;
import java.util.Set;



/**
 * An abstract base class for implementing new types of
 * {@link Attribute}.
 */
@org.opends.server.types.PublicAPI(
    stability = org.opends.server.types.StabilityLevel.UNCOMMITTED,
    mayInstantiate = false,
    mayExtend = false,
    mayInvoke = true)
public abstract class AbstractAttribute implements Attribute
{

  /**
   * Creates a new abstract attribute.
   */
  protected AbstractAttribute()
  {
    // No implementation required.
  }



  /**
   * {@inheritDoc}
   * <p>
   * This implementation iterates through each attribute value in the
   * provided collection, checking to see if this attribute contains
   * the value using {@link #contains(AttributeValue)}.
   */
  public boolean containsAll(Collection<AttributeValue> values)
  {
    for (AttributeValue value : values)
    {
      if (!contains(value))
      {
        return false;
      }
    }

    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }
    else if (o instanceof Attribute)
    {
      Attribute a = (Attribute) o;

      if (!getAttributeType().equals(a.getAttributeType()))
      {
        return false;
      }

      if (size() != a.size())
      {
        return false;
      }

      for (AttributeValue v : a)
      {
        if (!contains(v))
        {
          return false;
        }
      }

      return optionsEqual(a.getOptions());
    }
    else
    {
      return false;
    }
  }



  /**
   * {@inheritDoc}
   * <p>
   * This implementation returns the primary name associated with this
   * attribute's attribute type or, if there is no primary name, the
   * attribute type's OID.
   */
  public String getName()
  {
    return getAttributeType().getNameOrOID();
  }



  /**
   * {@inheritDoc}
   * <p>
   * This implementation returns this attribute's name if there are no
   * attribute options, otherwise it constructs a string comprising of
   * this attribute's name followed by a semi-colon and a semi-colon
   * separated list of its attribute options.
   */
  public String getNameWithOptions()
  {
    if (!hasOptions())
    {
      return getName();
    }
    else
    {
      StringBuilder buffer = new StringBuilder();
      buffer.append(getName());
      for (String option : getOptions())
      {
        buffer.append(';');
        buffer.append(option);
      }
      return buffer.toString();
    }
  }



  /**
   * {@inheritDoc}
   * <p>
   * This implementation returns <code>true</code> if the provided
   * collection of options is <code>null</code> or empty. If the
   * collection is non-empty and this attribute does not have any
   * options then it returns <code>false</code>. Otherwise, each
   * option in the provided collection is checked using
   * {@link #hasOption(String)} and <code>true</code> is
   * returned if all the provided options are present.
   */
  public boolean hasAllOptions(Collection<String> options)
  {
    if (options == null || options.isEmpty())
    {
      return true;
    }

    if (!hasOptions())
    {
      return false;
    }

    for (String option : options)
    {
      if (!hasOption(option))
      {
        return false;
      }
    }

    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final int hashCode()
  {
    int hashCode = getAttributeType().hashCode();

    for (AttributeValue value : this)
    {
      hashCode += value.hashCode();
    }

    return hashCode;
  }



  /**
   * {@inheritDoc}
   * <p>
   * This implementation calls {@link #getOptions()} to
   * retrieve this attribute's set of options and then compares them
   * one at a time against the provided option. All comparisons are
   * case insensitive (this is why we iterate through the set of
   * options, rather than doing a simpler set membership test).
   */
  public boolean hasOption(String option)
  {
    String noption = toLowerCase(option);

    // Cannot use Set.contains() because the options are not
    // normalized.
    for (String o : getOptions())
    {
      String no = toLowerCase(o);
      if (no.equals(noption))
      {
        return true;
      }
    }
    return false;
  }



  /**
   * {@inheritDoc}
   * <p>
   * This implementation retrieves the set of options associated with
   * this attribute and tests to see if it is empty.
   */
  public boolean hasOptions()
  {
    return !getOptions().isEmpty();
  }



  /**
   * {@inheritDoc}
   * <p>
   * This implementation returns <code>true</code> if the
   * {@link #size()} of this attribute is zero.
   */
  public boolean isEmpty()
  {
    return size() == 0;
  }



  /**
   * {@inheritDoc}
   * <p>
   * This implementation returns !{@link #hasOptions()} if the
   * provided set of options is <code>null</code>. Otherwise it
   * checks that the size of the provided set of options is equal to
   * the size of this attribute's options, return <code>false</code>
   * if the sizes differ. If the sizes are the same then each option
   * in the provided set is checked using
   * {@link #hasOption(String)} and <code>true</code> is
   * returned if all the provided options are present.
   */
  public boolean optionsEqual(Set<String> options)
  {
    if (options == null)
    {
      return !hasOptions();
    }

    if (getOptions().size() != options.size())
    {
      return false;
    }

    // Cannot use Set.containsAll() because the set of options are
    // not normalized.
    for (String option : options)
    {
      if (!hasOption(option))
      {
        return false;
      }
    }

    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }
}
