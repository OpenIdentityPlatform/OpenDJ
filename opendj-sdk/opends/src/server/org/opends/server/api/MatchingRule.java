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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.api;



import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

import static org.opends.server.loggers.Debug.*;



/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server module that implements a matching
 * rule.
 */
public abstract class MatchingRule
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.api.MatchingRule";



  /**
   * Initializes this matching rule based on the information in the
   * provided configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the
   *                      information to use to initialize this
   *                      matching rule.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in
   *                           the process of performing the
   *                           initialization.
   *
   * @throws  InitializationException  If a problem that is not
   *                                   configuration-related occurs
   *                                   during initialization.
   */
  public abstract void initializeMatchingRule(ConfigEntry configEntry)
         throws ConfigException, InitializationException;



  /**
   * Retrieves the common name for this matching rule.
   *
   * @return  The common name for this matching rule, or
   *          <CODE>null</CODE> if it does not have a name.
   */
  public abstract String getName();



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  public abstract String getOID();



  /**
   * Retrieves the name or OID for this matching rule.  If it has a
   * name, then it will be returned.  Otherwise, the OID will be
   * returned.
   *
   * @return  The name or OID for this matching rule.
   */
  public String getNameOrOID()
  {
    assert debugEnter(CLASS_NAME, "getNameOrOID");

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
   * @return  The description for this matching rule, or
   *          <CODE>null</CODE> if there is none.
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
   * The default implementation will always return <CODE>false</CODE>.
   * If that is not acceptable for a particular matching rule
   * implementation, then it should override this method and perform
   * the appropriate processing to return the correct value.
   *
   * @return  <CODE>true</CODE> if this matching rule is declared
   *          "OBSOLETE", or <CODE>false</CODE> if not.
   */
  public boolean isObsolete()
  {
    assert debugEnter(CLASS_NAME, "isObsolete");

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
   * @return  <CODE>TRUE</CODE> if the attribute value should be
   *          considered a match for the provided assertion value,
   *          <CODE>FALSE</CODE> if it does not match, or
   *          <CODE>UNDEFINED</CODE> if the result is undefined.
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
  public int hashCode()
  {
    assert debugEnter(CLASS_NAME, "hashCode");

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
   * @return  <CODE>true</CODE> if the provided object is equal to
   *          this matching rule, or <CODE>false</CODE> if it is not.
   */
  public boolean equals(Object o)
  {
    assert debugEnter(CLASS_NAME, "equals", String.valueOf(o));

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
  public String toString()
  {
    assert debugEnter(CLASS_NAME, "toString");

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
  public void toString(StringBuilder buffer)
  {
    assert debugEnter(CLASS_NAME, "toString",
                      "java.lang.StringBuilder");

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

