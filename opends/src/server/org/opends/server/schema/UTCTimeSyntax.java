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
package org.opends.server.schema;



import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.admin.std.server.AttributeSyntaxCfg;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ResultCode;

import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class implements the UTC time attribute syntax.  This is very similar to
 * the generalized time syntax (and actually has been deprecated in favor of
 * that), but requires that the minute be provided and does not allow for
 * sub-second times.  All matching will be performed using the generalized time
 * matching rules, and equality, ordering, and substring matching will be
 * allowed.
 */
public class UTCTimeSyntax
       extends AttributeSyntax<AttributeSyntaxCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * The lock that will be used to provide threadsafe access to the date
   * formatter.
   */
  private static Object dateFormatLock;



  /**
   * The date formatter that will be used to convert dates into UTC time values.
   * Note that all interaction with it must be synchronized.
   */
  private static SimpleDateFormat dateFormat;



  // The default equality matching rule for this syntax.
  private EqualityMatchingRule defaultEqualityMatchingRule;

  // The default ordering matching rule for this syntax.
  private OrderingMatchingRule defaultOrderingMatchingRule;

  // The default substring matching rule for this syntax.
  private SubstringMatchingRule defaultSubstringMatchingRule;



  /*
   * Create the date formatter that will be used to construct and parse
   * normalized UTC time values.
   */
  static
  {
    dateFormat = new SimpleDateFormat(DATE_FORMAT_UTC_TIME);
    dateFormat.setLenient(false);
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

    dateFormatLock = new Object();
  }



  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public UTCTimeSyntax()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  public void initializeSyntax(AttributeSyntaxCfg configuration)
         throws ConfigException
  {
    defaultEqualityMatchingRule =
         DirectoryServer.getEqualityMatchingRule(EMR_GENERALIZED_TIME_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logError(ERR_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE.get(
          EMR_GENERALIZED_TIME_OID, SYNTAX_UTC_TIME_NAME));
    }

    defaultOrderingMatchingRule =
         DirectoryServer.getOrderingMatchingRule(OMR_GENERALIZED_TIME_OID);
    if (defaultOrderingMatchingRule == null)
    {
      logError(ERR_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE.get(
          OMR_GENERALIZED_TIME_OID, SYNTAX_UTC_TIME_NAME));
    }

    defaultSubstringMatchingRule =
         DirectoryServer.getSubstringMatchingRule(SMR_CASE_IGNORE_OID);
    if (defaultSubstringMatchingRule == null)
    {
      logError(ERR_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE.get(
          SMR_CASE_IGNORE_OID, SYNTAX_UTC_TIME_NAME));
    }
  }



  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  public String getSyntaxName()
  {
    return SYNTAX_UTC_TIME_NAME;
  }



  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  public String getOID()
  {
    return SYNTAX_UTC_TIME_OID;
  }



  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  public String getDescription()
  {
    return SYNTAX_UTC_TIME_DESCRIPTION;
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
    return defaultOrderingMatchingRule;
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
    return defaultSubstringMatchingRule;
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
    // Approximate matching will not be allowed by default.
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
                                   MessageBuilder invalidReason)
  {
    // Get the value as a string and verify that it is at least long enough for
    // "YYYYMMDDhhmmZ", which is the shortest allowed value.
    String valueString = value.stringValue().toUpperCase();
    int    length      = valueString.length();
    if (length < 11)
    {
      Message message = ERR_ATTR_SYNTAX_UTC_TIME_TOO_SHORT.get(valueString);
      invalidReason.append(message);
      return false;
    }


    // The first two characters are the year, and they must be numeric digits
    // between 0 and 9.
    for (int i=0; i < 2; i++)
    {
      switch (valueString.charAt(i))
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
          // These are all fine.
          break;
        default:
          Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_YEAR.get(
                  valueString, String.valueOf(valueString.charAt(i)));
          invalidReason.append(message);
          return false;
      }
    }


    // The next two characters are the month, and they must form the string
    // representation of an integer between 01 and 12.
    char m1 = valueString.charAt(2);
    char m2 = valueString.charAt(3);
    switch (m1)
    {
      case '0':
        // m2 must be a digit between 1 and 9.
        switch (m2)
        {
          case '1':
          case '2':
          case '3':
          case '4':
          case '5':
          case '6':
          case '7':
          case '8':
          case '9':
            // These are all fine.
            break;
          default:
            Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_MONTH.get(
                    valueString, valueString.substring(2, 4));
            invalidReason.append(message);
            return false;
        }
        break;
      case '1':
        // m2 must be a digit between 0 and 2.
        switch (m2)
        {
          case '0':
          case '1':
          case '2':
            // These are all fine.
            break;
          default:
            Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_MONTH.get(
                    valueString, valueString.substring(2, 4));
            invalidReason.append(message);
            return false;
        }
        break;
      default:
        Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_MONTH.get(
                valueString, valueString.substring(2, 4));
        invalidReason.append(message);
        return false;
    }


    // The next two characters should be the day of the month, and they must
    // form the string representation of an integer between 01 and 31.
    // This doesn't do any validation against the year or month, so it will
    // allow dates like April 31, or February 29 in a non-leap year, but we'll
    // let those slide.
    char d1 = valueString.charAt(4);
    char d2 = valueString.charAt(5);
    switch (d1)
    {
      case '0':
        // d2 must be a digit between 1 and 9.
        switch (d2)
        {
          case '1':
          case '2':
          case '3':
          case '4':
          case '5':
          case '6':
          case '7':
          case '8':
          case '9':
            // These are all fine.
            break;
          default:
            Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_DAY.get(
                    valueString, valueString.substring(4, 6));
            invalidReason.append(message);
            return false;
        }
        break;
      case '1':
        // Treated the same as '2'.
      case '2':
        // d2 must be a digit between 0 and 9.
        switch (d2)
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
            // These are all fine.
            break;
          default:
            Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_DAY.get(
                    valueString, valueString.substring(4, 6));
            invalidReason.append(message);
            return false;
        }
        break;
      case '3':
        // d2 must be either 0 or 1.
        switch (d2)
        {
          case '0':
          case '1':
            // These are all fine.
            break;
          default:
            Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_DAY.get(
                    valueString, valueString.substring(4, 6));
            invalidReason.append(message);
            return false;
        }
        break;
      default:
        Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_DAY.get(valueString,
                                    valueString.substring(4, 6));
        invalidReason.append(message);
        return false;
    }


    // The next two characters must be the hour, and they must form the string
    // representation of an integer between 00 and 23.
    char h1 = valueString.charAt(6);
    char h2 = valueString.charAt(7);
    switch (h1)
    {
      case '0':
        // This is treated the same as '1'.
      case '1':
        switch (h2)
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
            // These are all fine.
            break;
          default:
            Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_HOUR.get(
                    valueString, valueString.substring(6, 8));
            invalidReason.append(message);
            return false;
        }
        break;
      case '2':
        // This must be a digit between 0 and 3.
        switch (h2)
        {
          case '0':
          case '1':
          case '2':
          case '3':
            // These are all fine.
            break;
          default:
            Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_HOUR.get(
                    valueString, valueString.substring(6, 8));
            invalidReason.append(message);
            return false;
        }
        break;
      default:
        Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_HOUR.get(valueString,
                                    valueString.substring(6, 8));
        invalidReason.append(message);
        return false;
    }


    // Next, there should be two digits comprising an integer between 00 and 59
    // for the minute.
    m1 = valueString.charAt(8);
    switch (m1)
    {
      case '0':
      case '1':
      case '2':
      case '3':
      case '4':
      case '5':
        // There must be at least two more characters, and the next one must
        // be a digit between 0 and 9.
        if (length < 11)
        {
          Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_CHAR.get(
                  valueString, String.valueOf(m1), 8);
          invalidReason.append(message);
          return false;
        }

        switch (valueString.charAt(9))
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
            // These are all fine.
            break;
          default:
            Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_MINUTE.get(
                    valueString, valueString.substring(8, 10));
            invalidReason.append(message);
            return false;
        }

        break;

      default:
        Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_CHAR.get(
                valueString, String.valueOf(m1), 8);
        invalidReason.append(message);
        return false;
    }


    // Next, there should be either two digits comprising an integer between 00
    // and 60 (for the second, including a possible leap second), a letter 'Z'
    // (for the UTC specifier), or a plus or minus sign followed by four digits
    // (for the UTC offset).
    char s1 = valueString.charAt(10);
    switch (s1)
    {
      case '0':
      case '1':
      case '2':
      case '3':
      case '4':
      case '5':
        // There must be at least two more characters, and the next one must
        // be a digit between 0 and 9.
        if (length < 13)
        {
          Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_CHAR.get(
                  valueString, String.valueOf(s1), 10);
          invalidReason.append(message);
          return false;
        }

        switch (valueString.charAt(11))
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
            // These are all fine.
            break;
          default:
            Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_SECOND.get(
                    valueString, valueString.substring(10, 12));
            invalidReason.append(message);
            return false;
        }

        break;
      case '6':
        // There must be at least two more characters and the next one must be
        // a 0.
        if (length < 13)
        {

          Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_CHAR.get(
                  valueString, String.valueOf(s1), 10);
          invalidReason.append(message);
          return false;
        }

        if (valueString.charAt(11) != '0')
        {
          Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_SECOND.get(
                  valueString, valueString.substring(10, 12));
          invalidReason.append(message);
          return false;
        }

        break;
      case 'Z':
        // This is fine only if we are at the end of the value.
        if (length == 11)
        {
          return true;
        }
        else
        {
          Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_CHAR.get(
                  valueString, String.valueOf(s1), 10);
          invalidReason.append(message);
          return false;
        }

      case '+':
      case '-':
        // These are fine only if there are exactly four more digits that
        // specify a valid offset.
        if (length == 15)
        {
          return hasValidOffset(valueString, 11, invalidReason);
        }
        else
        {
          Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_CHAR.get(
                  valueString, String.valueOf(s1), 10);
          invalidReason.append(message);
          return false;
        }

      default:
        Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_CHAR.get(
                valueString, String.valueOf(s1), 10);
        invalidReason.append(message);
        return false;
    }


    // The last element should be either a letter 'Z' (for the UTC specifier),
    // or a plus or minus sign followed by four digits (for the UTC offset).
    switch (valueString.charAt(12))
    {
      case 'Z':
        // This is fine only if we are at the end of the value.
        if (length == 13)
        {
          return true;
        }
        else
        {
          Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_CHAR.get(
                  valueString, String.valueOf(valueString.charAt(12)), 12);
          invalidReason.append(message);
          return false;
        }

      case '+':
      case '-':
        // These are fine only if there are four or two more digits that
        // specify a valid offset.
        if ((length == 17) || (length == 15))
        {
          return hasValidOffset(valueString, 13, invalidReason);
        }
        else
        {
          Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_CHAR.get(
                  valueString, String.valueOf(valueString.charAt(12)), 12);
          invalidReason.append(message);
          return false;
        }

      default:
        Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_CHAR.get(
                valueString, String.valueOf(valueString.charAt(12)), 12);
        invalidReason.append(message);
        return false;
    }
  }



  /**
   * Indicates whether the provided string contains a valid set of two or four
   * UTC offset digits.  The provided string must have either two or four
   * characters from the provided start position to the end of the value.
   *
   * @param  value          The whole value, including the offset.
   * @param  startPos       The position of the first character that is
   *                        contained in the offset.
   * @param  invalidReason  The buffer to which the invalid reason may be
   *                        appended if the string does not contain a valid set
   *                        of UTC offset digits.
   *
   * @return  <CODE>true</CODE> if the provided offset string is valid, or
   *          <CODE>false</CODE> if it is not.
   */
  private boolean hasValidOffset(String value, int startPos,
                                 MessageBuilder invalidReason)
  {
    int offsetLength = value.length() - startPos;
    if (offsetLength < 2)
    {
      Message message = ERR_ATTR_SYNTAX_UTC_TIME_TOO_SHORT.get(value);
      invalidReason.append(message);
      return false;
    }

    // The first two characters must be an integer between 00 and 23.
    switch (value.charAt(startPos))
    {
      case '0':
      case '1':
        switch (value.charAt(startPos+1))
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
            // These are all fine.
            break;
          default:
            Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_OFFSET.get(value,
                                        value.substring(startPos,
                                                        startPos+offsetLength));
            invalidReason.append(message);
            return false;
        }
        break;
      case '2':
        switch (value.charAt(startPos+1))
        {
          case '0':
          case '1':
          case '2':
          case '3':
            // These are all fine.
            break;
          default:
            Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_OFFSET.get(value,
                                        value.substring(startPos,
                                                        startPos+offsetLength));
            invalidReason.append(message);
            return false;
        }
        break;
      default:
        Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_OFFSET.get(value,
                                    value.substring(startPos,
                                                    startPos+offsetLength));
        invalidReason.append(message);
        return false;
    }


    // If there are two more characters, then they must be an integer between
    // 00 and 59.
    if (offsetLength == 4)
    {
      switch (value.charAt(startPos+2))
      {
        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
          switch (value.charAt(startPos+3))
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
              // These are all fine.
              break;
            default:
              Message message =
                   ERR_ATTR_SYNTAX_UTC_TIME_INVALID_OFFSET.get(
                           value,value.substring(startPos,
                           startPos+offsetLength));
              invalidReason.append(message);
              return false;
          }
          break;
        default:
          Message message = ERR_ATTR_SYNTAX_UTC_TIME_INVALID_OFFSET.get(value,
                                      value.substring(startPos,
                                                      startPos+offsetLength));
          invalidReason.append(message);
          return false;
      }
    }

    return true;
  }



  /**
   * Retrieves an attribute value containing a UTC time representation of the
   * provided date.
   *
   * @param  d  The date for which to retrieve the UTC time value.
   *
   * @return  The attribute value created from the date.
   */
  public static AttributeValue createUTCTimeValue(Date d)
  {
    String valueString;

    synchronized (dateFormatLock)
    {
      valueString = dateFormat.format(d);
    }

    return new AttributeValue(new ASN1OctetString(valueString),
                              new ASN1OctetString(valueString));
  }



  /**
   * Decodes the provided normalized value as a UTC time value and
   * retrieves a Java <CODE>Date</CODE> object containing its representation.
   *
   * @param  normalizedValue  The normalized UTC time value to decode to a
   *                          Java <CODE>Date</CODE>.
   *
   * @return  The Java <CODE>Date</CODE> created from the provided UTC time
   *          value.
   *
   * @throws  DirectoryException  If the provided value cannot be parsed as a
   *                              valid UTC time string.
   */
  public static Date decodeUTCTimeValue(ByteString normalizedValue)
         throws DirectoryException
  {
    String valueString = normalizedValue.stringValue();
    try
    {
      synchronized (dateFormatLock)
      {
        return dateFormat.parse(valueString);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_ATTR_SYNTAX_UTC_TIME_CANNOT_PARSE.get(
          valueString, String.valueOf(e));
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                   message, e);
    }
  }
}

