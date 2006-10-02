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



import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ResultCode;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines the bit string attribute syntax, which is comprised of
 * a string of binary digits surrounded by single quotes and followed by a
 * capital letter "B" (e.g., '101001'B).
 */
public class BitStringSyntax
       extends AttributeSyntax
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.schema.BitStringSyntax";



  // The default equality matching rule for this syntax.
  private EqualityMatchingRule defaultEqualityMatchingRule;



  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public BitStringSyntax()
  {
    super();

    assert debugConstructor(CLASS_NAME);
  }



  /**
   * Initializes this attribute syntax based on the information in the provided
   * configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this attribute syntax.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   */
  public void initializeSyntax(ConfigEntry configEntry)
         throws ConfigException
  {
    assert debugEnter(CLASS_NAME, "initializeSyntax",
                      String.valueOf(configEntry));


    defaultEqualityMatchingRule =
         DirectoryServer.getEqualityMatchingRule(EMR_BIT_STRING_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE,
               EMR_BIT_STRING_OID, SYNTAX_BIT_STRING_NAME);
    }
  }



  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  public String getSyntaxName()
  {
    assert debugEnter(CLASS_NAME, "getSyntaxName");

    return SYNTAX_BIT_STRING_NAME;
  }



  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  public String getOID()
  {
    assert debugEnter(CLASS_NAME, "getOID");

    return SYNTAX_BIT_STRING_OID;
  }



  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  public String getDescription()
  {
    assert debugEnter(CLASS_NAME, "getDescription");

    return SYNTAX_BIT_STRING_DESCRIPTION;
  }



  /**
   * Retrieves the default equality matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default equality matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if equality
   *          matches will not be allowed for this type by default.
   */
  public EqualityMatchingRule getEqualityMatchingRule()
  {
    assert debugEnter(CLASS_NAME, "getEqualityMatchingRule");

    return defaultEqualityMatchingRule;
  }



  /**
   * Retrieves the default ordering matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default ordering matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if ordering
   *          matches will not be allowed for this type by default.
   */
  public OrderingMatchingRule getOrderingMatchingRule()
  {
    assert debugEnter(CLASS_NAME, "getOrderingMatchingRule");

    // Ordering matches are not allowed by default.
    return null;
  }



  /**
   * Retrieves the default substring matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default substring matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if substring
   *          matches will not be allowed for this type by default.
   */
  public SubstringMatchingRule getSubstringMatchingRule()
  {
    assert debugEnter(CLASS_NAME, "getSubstringMatchingRule");

    // Substring matches are not allowed by default.
    return null;
  }



  /**
   * Retrieves the default approximate matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default approximate matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if approximate
   *          matches will not be allowed for this type by default.
   */
  public ApproximateMatchingRule getApproximateMatchingRule()
  {
    assert debugEnter(CLASS_NAME, "getApproximateMatchingRule");

    // Approximate matches are not allowed by default.
    return null;
  }



  /**
   * Indicates whether the provided value is acceptable for use in an attribute
   * with this syntax.  If it is not, then the reason may be appended to the
   * provided buffer.
   *
   * @param  value          The value for which to make the determination.
   * @param  invalidReason  The buffer to which the invalid reason should be
   *                        appended.
   *
   * @return  <CODE>true</CODE> if the provided value is acceptable for use with
   *          this syntax, or <CODE>false</CODE> if not.
   */
  public boolean valueIsAcceptable(ByteString value,
                                   StringBuilder invalidReason)
  {
    assert debugEnter(CLASS_NAME, "valueIsAcceptable", String.valueOf(value),
                      "java.lang.StringBuilder");

    String valueString = value.stringValue().toUpperCase();

    int length = valueString.length();
    if (length < 3)
    {
      invalidReason.append(getMessage(MSGID_ATTR_SYNTAX_BIT_STRING_TOO_SHORT,
                                      value.stringValue()));
      return false;
    }


    if ((valueString.charAt(0) != '\'') ||
        (valueString.charAt(length-2) != '\'') ||
        (valueString.charAt(length-1) != 'B'))
    {
      invalidReason.append(getMessage(MSGID_ATTR_SYNTAX_BIT_STRING_NOT_QUOTED,
                                      value.stringValue()));
      return false;
    }


    for (int i=1; i < (length-2); i++)
    {
      switch (valueString.charAt(i))
      {
        case '0':
        case '1':
          // These characters are fine.
          break;
        default:
          invalidReason.append(getMessage(
                                    MSGID_ATTR_SYNTAX_BIT_STRING_INVALID_BIT,
                                    value.stringValue(),
                                    valueString.charAt(i)));
          return false;
      }
    }


    // If we've gotten here, then everything is fine.
    return true;
  }



  /**
   * Retrieves an attribute value containing a bit string representation of the
   * provided value.
   *
   * @param  b  The byte for which to retrieve the bit string value.
   *
   * @return  The attribute value created from the provided byte.
   */
  public static AttributeValue createBitStringValue(byte b)
  {
    assert debugEnter(CLASS_NAME, "createBitStringValue", String.valueOf(b));

    String bitString = "'" + byteToBinary(b) + "'B";
    return new AttributeValue(new ASN1OctetString(bitString),
                              new ASN1OctetString(bitString));
  }



  /**
   * Retrieves an attribute value containing a bit string representation of the
   * provided value.
   *
   * @param  b  The byte array for which to retrieve the bit string value.
   *
   * @return  The attribute value created from the provided byte array.
   */
  public static AttributeValue createBitStringValue(byte[] b)
  {
    assert debugEnter(CLASS_NAME, "createBitStringValue", String.valueOf(b));

    int    length;
    String bitString;
    if ((b == null) || ((length = b.length) == 0))
    {
      bitString = "''B";
    }
    else
    {
      StringBuilder buffer = new StringBuilder(3 + (8*length));
      buffer.append("'");

      for (int i=0; i < length; i++)
      {
        buffer.append(byteToBinary(b[i]));
      }

      buffer.append("'B");

      bitString = buffer.toString();
    }

    return new AttributeValue(new ASN1OctetString(bitString),
                              new ASN1OctetString(bitString));
  }



  /**
   * Decodes the provided normalized value as a bit string and retrieves a byte
   * array containing its binary representation.  Note that if the bit string
   * contained in the provided value has a length that is not a multiple of
   * eight, then the final byte will be padded with <B>leading</B> zeros, so
   * the bit string value '1111111111'B will be returned as the byte array
   * { 11111111, 00000011 }.
   *
   * @param  normalizedValue  The normalized bit string value to decode to a
   *                          byte array.
   *
   * @return  The byte array containing the binary representation of the
   *          provided bit string value.
   *
   * @throws  DirectoryException  If the provided value cannot be parsed as a
   *                              valid bit string.
   */
  public static byte[] decodeBitStringValue(ByteString normalizedValue)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "decodeBitStringValue",
                      String.valueOf(normalizedValue));

    String valueString = normalizedValue.stringValue();
    int length = valueString.length();
    if (length < 3)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_BIT_STRING_TOO_SHORT;
      String message = getMessage(msgID, valueString);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                   message, msgID);
    }


    if ((valueString.charAt(0) != '\'') ||
        (valueString.charAt(length-2) != '\'') ||
        (valueString.charAt(length-1) != 'B'))
    {
      int pos;
      if (valueString.charAt(0) != '\'')
      {
        pos = 0;
      }
      else if (valueString.charAt(length-2) != '\'')
      {
        pos = length - 2;
      }
      else
      {
        pos = length - 1;
      }

      int    msgID   = MSGID_ATTR_SYNTAX_BIT_STRING_NOT_QUOTED;
      String message = getMessage(msgID, valueString);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                   message, msgID);
    }


    int numBits      = length - 3;
    int numFullBytes = numBits / 8;
    int numBytes     = numFullBytes;
    int numExtraBits = (numBits % 8);
    if (numExtraBits != 0)
    {
      numBytes++;
    }

    byte[] b = new byte[numBytes];
    int pos = 1;
    for (int i=0; i < numFullBytes; i++)
    {
      b[i] = 0x00;
      for (int j=0; j < 8; j++)
      {
        char c = valueString.charAt(pos++);
        if (c == '0')
        {
          b[i] <<= 1;
        }
        else if (c == '1')
        {
          b[i] = (byte) ((b[i] << 1) | 0x01);
        }
        else
        {
          int    msgID   = MSGID_ATTR_SYNTAX_BIT_STRING_INVALID_BIT;
          String message = getMessage(msgID, valueString, c);
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID);
        }
      }
    }

    if (numExtraBits > 0)
    {
      b[numFullBytes] = 0x00;
      for (int i=0; i < numExtraBits; i++)
      {
        char c = valueString.charAt(pos++);
        if (c == '0')
        {
          b[numFullBytes] <<= 1;
        }
        else if (c == '1')
        {
          b[numFullBytes] = (byte) ((b[numFullBytes] << 1) | 0x01);
        }
        else
        {
          int    msgID   = MSGID_ATTR_SYNTAX_BIT_STRING_INVALID_BIT;
          String message = getMessage(msgID, valueString, c);
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID);
        }
      }
    }

    return b;
  }
}

