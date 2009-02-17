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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.server.api;



import java.util.Collection;

import org.opends.server.types.ByteSequence;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.DirectoryException;



/**
 * This class provides default implementation of MatchingRule. A
 * matching rule implemented by a Directory Server module must extend
 * this class.
 */
@org.opends.server.types.PublicAPI(
    stability = org.opends.server.types.StabilityLevel.VOLATILE,
    mayInstantiate = false,
    mayExtend = true,
    mayInvoke = false)
public abstract class AbstractMatchingRule implements MatchingRule
{
  /**
   * {@inheritDoc}
   */
  public abstract String getName();



  /**
   * {@inheritDoc}
   */
  public abstract Collection<String> getAllNames();



  /**
   * {@inheritDoc}
   */
  public abstract String getOID();



  /**
   * {@inheritDoc}
   */
  public ByteString normalizeAssertionValue(ByteSequence value)
      throws DirectoryException
  {
    // Default implementation is to use attribute value normalization.
    return normalizeValue(value);
  }



  /**
   * {@inheritDoc}
   */
  public final String getNameOrOID()
  {
    String name = getName();
    if ((name == null) || (name.length() == 0))
    {
      return getOID();
    }
    else
    {
      return name;
    }
  }



  /**
   * {@inheritDoc}
   */
  public abstract String getDescription();



  /**
   * {@inheritDoc}
   */
  public abstract String getSyntaxOID();



  /**
   * {@inheritDoc}
   */
  public boolean isObsolete()
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public abstract ByteString normalizeValue(ByteSequence value)
      throws DirectoryException;



  /**
   * {@inheritDoc}
   */
  public ConditionResult valuesMatch(
      ByteSequence attributeValue, ByteSequence assertionValue)
  {
    //Default implementation of most rule types.
    return ConditionResult.UNDEFINED;
  }



  /**
   * Retrieves the hash code for this matching rule. It will be
   * calculated as the sum of the characters in the OID.
   *
   * @return The hash code for this matching rule.
   */
  @Override
  public final int hashCode()
  {
    int hashCode = 0;

    String oidString = getOID();
    int oidLength = oidString.length();
    for (int i = 0; i < oidLength; i++)
    {
      hashCode += oidString.charAt(i);
    }

    return hashCode;
  }



  /**
   * Indicates whether the provided object is equal to this matching
   * rule. The provided object will be considered equal to this
   * matching rule only if it is a matching rule with the same OID.
   *
   * @param o
   *          The object for which to make the determination.
   * @return {@code true} if the provided object is equal to this
   *         matching rule, or {@code false} if it is not.
   */
  @Override
  public final boolean equals(Object o)
  {
    if (o == null)
    {
      return false;
    }

    if (this == o)
    {
      return true;
    }

    if (!(o instanceof MatchingRule))
    {
      return false;
    }

    return getOID().equals(((MatchingRule) o).getOID());
  }



  /**
   * Retrieves a string representation of this matching rule in the
   * format defined in RFC 2252.
   *
   * @return A string representation of this matching rule in the
   *         format defined in RFC 2252.
   */
  @Override
  public final String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * {@inheritDoc}
   */
  public final void toString(StringBuilder buffer)
  {
    buffer.append("( ");
    buffer.append(getOID());
    buffer.append(" NAME '");
    buffer.append(getName());

    String description = getDescription();
    if ((description != null) && (description.length() > 0))
    {
      buffer.append("' DESC '");
      buffer.append(description);
    }

    if (isObsolete())
    {
      buffer.append("' OBSOLETE SYNTAX ");
    }
    else
    {
      buffer.append("' SYNTAX ");
    }

    buffer.append(getSyntaxOID());
    buffer.append(" )");
  }
}
