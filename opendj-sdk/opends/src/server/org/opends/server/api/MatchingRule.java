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
package org.opends.server.api;

import java.util.Collection;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.DirectoryException;



/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server module that implements a matching
 * rule.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public abstract class MatchingRule
{
  /**
   * Retrieves the common name for this matching rule.
   *
   * @return  The common name for this matching rule, or {@code null}
   *          if it does not have a name.
   */
  public abstract String getName();



  /**
   * Retrieves all names for this matching rule.
   *
   * @return  All names for this matching rule.
   */
  public abstract Collection<String> getAllNames();



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  public abstract String getOID();



 /**
   * Retrieves the normalized form of the provided assertion value,
   * which is best suite for efficiently performing matching
   * operations on that value.
   *
   * @param  value  The assertion value to be normalized.
   *
   * @return  The normalized version of the provided value.
   *
   * @throws  DirectoryException  If the provided value is invalid
   *                              according to the associated
   *                              attribute syntax.
   */
  public ByteString normalizeAssertionValue(ByteString value)
         throws DirectoryException
  {
    // Default implementation is to use attribute value normalization.
     return normalizeValue(value);
  }



  /**
   * Retrieves the name or OID for this matching rule.  If it has a
   * name, then it will be returned.  Otherwise, the OID will be
   * returned.
   *
   * @return  The name or OID for this matching rule.
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
   * Retrieves the description for this matching rule.
   *
   * @return  The description for this matching rule, or {@code null}
   *          if there is none.
   */
  public abstract String getDescription();



  /**
   * Retrieves the OID of the syntax with which this matching rule is
   * associated.
   *
   * @return  The OID of the syntax with which this matching rule is
   *          associated.
   */
  public abstract String getSyntaxOID();



  /**
   * Indicates whether this matching rule is declared "OBSOLETE".
   * The default implementation will always return {@code false}.  If
   * that is not acceptable for a particular matching rule
   * implementation, then it should override this method and perform
   * the appropriate processing to return the correct value.
   *
   * @return  {@code true} if this matching rule is declared
   *          "OBSOLETE", or {@code false} if not.
   */
  public boolean isObsolete()
  {
    return false;
  }



  /**
   * Retrieves the normalized form of the provided value, which is
   * best suite for efficiently performing matching operations on that
   * value.
   *
   * @param  value  The value to be normalized.
   *
   * @return  The normalized version of the provided value.
   *
   * @throws  DirectoryException  If the provided value is invalid
   *                              according to the associated
   *                              attribute syntax.
   */
  public abstract ByteString normalizeValue(ByteString value)
         throws DirectoryException;



  /**
   * Indicates whether the provided attribute value should be
   * considered a match for the given assertion value.  This will only
   * be used for the purpose of extensible matching.  Subclasses
   * should define more specific methods that are appropriate to the
   * matching rule type.
   *
   * @param  attributeValue  The attribute value in a form that has
   *                         been normalized according to this
   *                         matching rule.
   * @param  assertionValue  The assertion value in a form that has
   *                         been normalized according to this
   *                         matching rule.
   *
   * @return  {@code TRUE} if the attribute value should be considered
   *          a match for the provided assertion value, {@code FALSE}
   *          if it does not match, or {@code UNDEFINED} if the result
   *          is undefined.
   */
  public abstract ConditionResult
                       valuesMatch(ByteString attributeValue,
                                   ByteString assertionValue);



  /**
   * Retrieves the hash code for this matching rule.  It will be
   * calculated as the sum of the characters in the OID.
   *
   * @return  The hash code for this matching rule.
   */
  public final int hashCode()
  {
    int hashCode = 0;

    String oidString = getOID();
    int    oidLength = oidString.length();
    for (int i=0; i < oidLength; i++)
    {
      hashCode += oidString.charAt(i);
    }

    return hashCode;
  }



  /**
   * Indicates whether the provided object is equal to this matching
   * rule.  The provided object will be considered equal to this
   * matching rule only if it is a matching rule with the same OID.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  {@code true} if the provided object is equal to this
   *          matching rule, or {@code false} if it is not.
   */
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

    if (! (o instanceof MatchingRule))
    {
      return false;
    }

    return getOID().equals(((MatchingRule) o).getOID());
  }



  /**
   * Retrieves a string representation of this matching rule in the
   * format defined in RFC 2252.
   *
   * @return  A string representation of this matching rule in the
   *          format defined in RFC 2252.
   */
  public final String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this matching rule in the
   * format defined in RFC 2252 to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
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

