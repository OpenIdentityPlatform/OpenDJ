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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.api;



import java.util.List;

import org.opends.server.admin.std.server.MatchingRuleCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;



/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server module that implements a matching
 * rule.
 *
 * @param  <T>  The type of configuration handled by this matching
 *              rule.
 */
public abstract class MatchingRule<T extends MatchingRuleCfg>
{
  /**
   * Initializes this matching rule based on the information in the
   * provided configuration entry.
   *
   * @param  configuration  The configuration to use to intialize this
   *                        matching rule.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in
   *                           the process of performing the
   *                           initialization.
   *
   * @throws  InitializationException  If a problem that is not
   *                                   configuration-related occurs
   *                                   during initialization.
   */
  public abstract void initializeMatchingRule(T configuration)
         throws ConfigException, InitializationException;



  /**
   * Indicates whether the provided configuration is acceptable for
   * this matching rule.  It should be possible to call this method on
   * an uninitialized matching rule instance in order to determine
   * whether the matching rule would be able to use the provided
   * configuration.
   * <BR><BR>
   * Note that implementations which use a subclass of the provided
   * configuration class will likely need to cast the configuration
   * to the appropriate subclass type.
   *
   * @param  configuration        The matching rule configuration for
   *                              which to make the determination.
   * @param  unacceptableReasons  A list that may be used to hold the
   *                              reasons that the provided
   *                              configuration is not acceptable.
   *
   * @return  {@code true} if the provided configuration is acceptable
   *          for this matching rule, or {@code false} if not.
   */
  public boolean isConfigurationAcceptable(
                      MatchingRuleCfg configuration,
                      List<String> unacceptableReasons)
  {
    // This default implementation does not perform any special
    // validation.  It should be overridden by matching rule
    // implementations that wish to perform more detailed validation.
    return true;
  }



  /**
   * Performs any finalization that may be needed whenever this
   * matching rule is taken out of service.
   */
  public void finalizeMatchingRule()
  {
    // No implementation is required by default.
  }



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

