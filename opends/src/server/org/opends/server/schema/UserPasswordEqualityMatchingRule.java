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
package org.opends.server.schema;



import java.util.Arrays;

import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.InitializationException;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConditionResult;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.schema.SchemaConstants.*;



/**
 * This class implements the userPasswordMatch matching rule, which can be used
 * to determine whether a clear-text value matches an encoded password.
 */
public class UserPasswordEqualityMatchingRule
       extends EqualityMatchingRule
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.schema.UserPasswordEqualityMatchingRule";



  /**
   * Creates a new instance of this userPasswordMatch matching rule.
   */
  public UserPasswordEqualityMatchingRule()
  {
    super();

    assert debugConstructor(CLASS_NAME);
  }



  /**
   * Initializes this matching rule based on the information in the provided
   * configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this matching rule.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   *
   * @throws  InitializationException  If a problem that is not
   *                                   configuration-related occurs during
   *                                   initialization.
   */
  public void initializeMatchingRule(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {
    assert debugEnter(CLASS_NAME, "initializeMatchingRule",
                      String.valueOf(configEntry));

    // No initialization is required.
  }



  /**
   * Retrieves the common name for this matching rule.
   *
   * @return  The common name for this matching rule, or <CODE>null</CODE> if
   * it does not have a name.
   */
  public String getName()
  {
    assert debugEnter(CLASS_NAME, "getName");

    return EMR_USER_PASSWORD_NAME;
  }



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  public String getOID()
  {
    assert debugEnter(CLASS_NAME, "getOID");

    return EMR_USER_PASSWORD_OID;
  }



  /**
   * Retrieves the description for this matching rule.
   *
   * @return  The description for this matching rule, or <CODE>null</CODE> if
   *          there is none.
   */
  public String getDescription()
  {
    assert debugEnter(CLASS_NAME, "getDescription");

    // There is no standard description for this matching rule.
    return EMR_USER_PASSWORD_DESCRIPTION;
  }



  /**
   * Retrieves the OID of the syntax with which this matching rule is
   * associated.
   *
   * @return  The OID of the syntax with which this matching rule is associated.
   */
  public String getSyntaxOID()
  {
    assert debugEnter(CLASS_NAME, "getSyntaxOID");

    return SYNTAX_USER_PASSWORD_OID;
  }



  /**
   * Retrieves the normalized form of the provided value, which is best suited
   * for efficiently performing matching operations on that value.
   *
   * @param  value  The value to be normalized.
   *
   * @return  The normalized version of the provided value.
   *
   * @throws  DirectoryException  If the provided value is invalid according to
   *                              the associated attribute syntax.
   */
  public ByteString normalizeValue(ByteString value)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "normalizeValue", String.valueOf(value));


    // We will not alter the value in any way, but we'll create a new value
    // just in case something else is using the underlying array.
    byte[] currentValue = value.value();
    byte[] newValue     = new byte[currentValue.length];
    System.arraycopy(currentValue, 0, newValue, 0, currentValue.length);

    return new ASN1OctetString(newValue);
  }



  /**
   * Indicates whether the two provided normalized values are equal to each
   * other.
   *
   * @param  value1  The normalized form of the first value to compare.
   * @param  value2  The normalized form of the second value to compare.
   *
   * @return  <CODE>true</CODE> if the provided values are equal, or
   *          <CODE>false</CODE> if not.
   */
  public boolean areEqual(ByteString value1, ByteString value2)
  {
    assert debugEnter(CLASS_NAME, "areEqual", String.valueOf(value1),
                      String.valueOf(value2));

    // Since the values are already normalized, we just need to compare the
    // associated byte arrays.
    return Arrays.equals(value1.value(), value2.value());
  }



  /**
   * Indicates whether the provided attribute value should be considered a match
   * for the given assertion value.  This will only be used for the purpose of
   * extensible matching.  Other forms of matching against equality matching
   * rules should use the <CODE>areEqual</CODE> method.
   *
   * @param  attributeValue  The attribute value in a form that has been
   *                         normalized according to this matching rule.
   * @param  assertionValue  The assertion value in a form that has been
   *                         normalized according to this matching rule.
   *
   * @return  <CODE>true</CODE> if the attribute value should be considered a
   *          match for the provided assertion value, or <CODE>false</CODE> if
   *          not.
   */
  public ConditionResult valuesMatch(ByteString attributeValue,
                                     ByteString assertionValue)
  {
    assert debugEnter(CLASS_NAME, "valuesMatch",
                      String.valueOf(attributeValue),
                      String.valueOf(assertionValue));


    // We must be able to decode the attribute value using the user password
    // syntax.
    String[] userPWComponents;
    try
    {
      userPWComponents =
           UserPasswordSyntax.decodeUserPassword(attributeValue.stringValue());
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "valuesMatch", e);

      return ConditionResult.FALSE;
    }


    // The first element of the array will be the scheme.  Make sure that we
    // support the requested scheme.
    PasswordStorageScheme storageScheme =
         DirectoryServer.getPasswordStorageScheme(userPWComponents[0]);
    if (storageScheme == null)
    {
      // It's not a scheme that we can support.
      return ConditionResult.FALSE;
    }


    // We support the scheme, so make the determination.
    if (storageScheme.passwordMatches(assertionValue,
                                      new ASN1OctetString(userPWComponents[1])))
    {
      return ConditionResult.TRUE;
    }
    else
    {
      return ConditionResult.FALSE;
    }
  }



  /**
   * Generates a hash code for the provided attribute value.  This version of
   * the method will simply create a hash code from the normalized form of the
   * attribute value.  For matching rules explicitly designed to work in cases
   * where byte-for-byte comparisons of normalized values is not sufficient for
   * determining equality (e.g., if the associated attribute syntax is based on
   * hashed or encrypted values), then this method must be overridden to provide
   * an appropriate implementation for that case.
   *
   * @param  attributeValue  The attribute value for which to generate the hash
   *                         code.
   *
   * @return  The hash code generated for the provided attribute value.
   */
  public int generateHashCode(AttributeValue attributeValue)
  {
    assert debugEnter(CLASS_NAME, "generateHashCode",
                      String.valueOf(attributeValue));


    // Because of the variable encoding that may be used, we have no way of
    // comparing two user password values by hash code and therefore we'll
    // always return the same value so that the valuesMatch method will be
    // invoked to make the determination.
    return 1;
  }
}

