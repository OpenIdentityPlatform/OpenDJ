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
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.schema.SchemaConstants.*;



/**
 * This class implements the userPasswordExactMatch matching rule, which will
 * simply compare encoded hashed password values to see if they are exactly
 * equal to each other.
 */
public class UserPasswordExactEqualityMatchingRule
       extends EqualityMatchingRule
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.schema.UserPasswordExactEqualityMatchingRule";



  /**
   * Creates a new instance of this userPasswordExactMatch matching rule.
   */
  public UserPasswordExactEqualityMatchingRule()
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

    return EMR_USER_PASSWORD_EXACT_NAME;
  }



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  public String getOID()
  {
    assert debugEnter(CLASS_NAME, "getOID");

    return EMR_USER_PASSWORD_EXACT_OID;
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
    return EMR_USER_PASSWORD_EXACT_DESCRIPTION;
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


    // The normalized form of this matching rule is exactly equal to the
    // non-normalized form, except that the scheme needs to be converted to
    // lowercase (if there is one).
    byte[] valueBytes = value.value();
    byte[] newValueBytes = new byte[valueBytes.length];
    System.arraycopy(valueBytes, 0, newValueBytes, 0, valueBytes.length);

    if (UserPasswordSyntax.isEncoded(value))
    {
schemeLoop:
      for (int i=1; i < newValueBytes.length; i++)
      {
        switch (newValueBytes[i])
        {
          case 'A':
            newValueBytes[i] = 'a';
            break;
          case 'B':
            newValueBytes[i] = 'b';
            break;
          case 'C':
            newValueBytes[i] = 'c';
            break;
          case 'D':
            newValueBytes[i] = 'd';
            break;
          case 'E':
            newValueBytes[i] = 'e';
            break;
          case 'F':
            newValueBytes[i] = 'f';
            break;
          case 'G':
            newValueBytes[i] = 'g';
            break;
          case 'H':
            newValueBytes[i] = 'h';
            break;
          case 'I':
            newValueBytes[i] = 'i';
            break;
          case 'J':
            newValueBytes[i] = 'j';
            break;
          case 'K':
            newValueBytes[i] = 'k';
            break;
          case 'L':
            newValueBytes[i] = 'l';
            break;
          case 'M':
            newValueBytes[i] = 'm';
            break;
          case 'N':
            newValueBytes[i] = 'n';
            break;
          case 'O':
            newValueBytes[i] = 'o';
            break;
          case 'P':
            newValueBytes[i] = 'p';
            break;
          case 'Q':
            newValueBytes[i] = 'q';
            break;
          case 'R':
            newValueBytes[i] = 'r';
            break;
          case 'S':
            newValueBytes[i] = 's';
            break;
          case 'T':
            newValueBytes[i] = 't';
            break;
          case 'U':
            newValueBytes[i] = 'u';
            break;
          case 'V':
            newValueBytes[i] = 'v';
            break;
          case 'W':
            newValueBytes[i] = 'w';
            break;
          case 'X':
            newValueBytes[i] = 'x';
            break;
          case 'Y':
            newValueBytes[i] = 'y';
            break;
          case 'Z':
            newValueBytes[i] = 'z';
            break;
          case '}':
            break schemeLoop;
        }
      }
    }

    return new ASN1OctetString(newValueBytes);
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
}

