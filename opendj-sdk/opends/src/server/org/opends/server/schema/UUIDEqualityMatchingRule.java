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
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;



/**
 * This class defines the uuidMatch matching rule defined in RFC 4530.  It will
 * be used as the default equality matching rule for the UUID syntax.
 */
public class UUIDEqualityMatchingRule
       extends EqualityMatchingRule
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.schema.UUIDEqualityMatchingRule";



  /**
   * Creates a new instance of this caseExactMatch matching rule.
   */
  public UUIDEqualityMatchingRule()
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

    return EMR_UUID_NAME;
  }



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  public String getOID()
  {
    assert debugEnter(CLASS_NAME, "getOID");

    return EMR_UUID_OID;
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
    return null;
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

    return SYNTAX_UUID_OID;
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

    byte[] valueBytes = value.value();
    if (valueBytes.length != 36)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_UUID_INVALID_LENGTH;
      String message = getMessage(msgID, value.stringValue(),
                                  valueBytes.length);
      switch (DirectoryServer.getSyntaxEnforcementPolicy())
      {
        case REJECT:
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID);
        case WARN:
          logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.MILD_ERROR,
                   message, msgID);
          return new ASN1OctetString(valueBytes);
        default:
          return new ASN1OctetString(valueBytes);
      }
    }

    byte[] normBytes = new byte[36];
    System.arraycopy(valueBytes, 0, normBytes, 0, 36);
    for (int i=0; i < 36; i++)
    {
      // The 9th, 14th, 19th, and 24th characters must be dashes.  All others
      // must be hex.  Convert all uppercase hex characters to lowercase.
      switch (i)
      {
        case 8:
        case 13:
        case 18:
        case 23:
          if (normBytes[i] != '-')
          {
            int    msgID   = MSGID_ATTR_SYNTAX_UUID_EXPECTED_DASH;
            String message = getMessage(msgID, value.stringValue(), i,
                                        String.valueOf(normBytes[i]));
            switch (DirectoryServer.getSyntaxEnforcementPolicy())
            {
              case REJECT:
                throw new DirectoryException(
                               ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                               msgID);
              case WARN:
                logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.MILD_ERROR,
                         message, msgID);
                return new ASN1OctetString(valueBytes);
              default:
                return new ASN1OctetString(valueBytes);
            }
          }
          break;
        default:
          switch (normBytes[i])
          {
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
            case 'a':
            case 'b':
            case 'c':
            case 'd':
            case 'e':
            case 'f':
              // These are all fine.
              break;
            case 'A':
              normBytes[i] = 'a';
              break;
            case 'B':
              normBytes[i] = 'b';
              break;
            case 'C':
              normBytes[i] = 'c';
              break;
            case 'D':
              normBytes[i] = 'd';
              break;
            case 'E':
              normBytes[i] = 'e';
              break;
            case 'F':
              normBytes[i] = 'f';
              break;
            default:
              int    msgID   = MSGID_ATTR_SYNTAX_UUID_EXPECTED_HEX;
              String message = getMessage(msgID, value.stringValue(), i,
                                          String.valueOf(normBytes[i]));
              switch (DirectoryServer.getSyntaxEnforcementPolicy())
              {
                case REJECT:
                  throw new DirectoryException(
                                 ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                 msgID);
                case WARN:
                  logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.MILD_ERROR,
                           message, msgID);
                  return new ASN1OctetString(valueBytes);
                default:
                  return new ASN1OctetString(valueBytes);
              }
          }
      }
    }

    return new ASN1OctetString(normBytes);
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

