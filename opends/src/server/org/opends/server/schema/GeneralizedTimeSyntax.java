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
import java.util.concurrent.locks.ReentrantLock;

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

import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines the generalized time attribute syntax, which is a way of
 * representing time in a form like "YYYYMMDDhhmmssZ".  The actual form is
 * somewhat flexible, and may omit the minute and second information, or may
 * include sub-second information.  It may also replace "Z" with a time zone
 * offset like "-0500" for representing values that are not in UTC.
 */
public class GeneralizedTimeSyntax
       extends AttributeSyntax
{



  /**
   * The lock that will be used to provide threadsafe access to the date
   * formatter.
   */
  private static ReentrantLock dateFormatLock;



  /**
   * The date formatter that will be used to convert dates into generalized time
   * values.  Note that all interaction with it must be synchronized.
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
   * normalized generalized time values.
   */
  static
  {
    dateFormat = new SimpleDateFormat(DATE_FORMAT_GENERALIZED_TIME);
    dateFormat.setLenient(false);
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

    dateFormatLock = new ReentrantLock();
  }



  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public GeneralizedTimeSyntax()
  {
    super();

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

    defaultEqualityMatchingRule =
         DirectoryServer.getEqualityMatchingRule(EMR_GENERALIZED_TIME_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE,
               EMR_GENERALIZED_TIME_OID, SYNTAX_GENERALIZED_TIME_NAME);
    }

    defaultOrderingMatchingRule =
         DirectoryServer.getOrderingMatchingRule(OMR_GENERALIZED_TIME_OID);
    if (defaultOrderingMatchingRule == null)
    {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE,
               OMR_GENERALIZED_TIME_OID, SYNTAX_GENERALIZED_TIME_NAME);
    }

    defaultSubstringMatchingRule =
         DirectoryServer.getSubstringMatchingRule(SMR_CASE_IGNORE_OID);
    if (defaultSubstringMatchingRule == null)
    {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE,
               SMR_CASE_IGNORE_OID, SYNTAX_GENERALIZED_TIME_NAME);
    }
  }



  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  public String getSyntaxName()
  {

    return SYNTAX_GENERALIZED_TIME_NAME;
  }



  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  public String getOID()
  {

    return SYNTAX_GENERALIZED_TIME_OID;
  }



  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  public String getDescription()
  {

    return SYNTAX_GENERALIZED_TIME_DESCRIPTION;
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
                                   StringBuilder invalidReason)
  {


    // Get the value as a string and verify that it is at least long enough for
    // "YYYYMMDDhhZ", which is the shortest allowed value.
    String valueString = value.stringValue().toUpperCase();
    int    length      = valueString.length();
    if (length < 11)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_TOO_SHORT;
      String message = getMessage(msgID, valueString);
      invalidReason.append(message);
      return false;
    }


    // The first four characters are the century and year, and they must be
    // numeric digits between 0 and 9.
    for (int i=0; i < 4; i++)
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
          int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_YEAR;
          String message = getMessage(msgID, valueString,
                                      valueString.charAt(i));
          invalidReason.append(message);
          return false;
      }
    }


    // The next two characters are the month, and they must form the string
    // representation of an integer between 01 and 12.
    char m1 = valueString.charAt(4);
    char m2 = valueString.charAt(5);
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
            int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_MONTH;
            String message = getMessage(msgID, valueString,
                                        valueString.substring(4, 6));
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
            int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_MONTH;
            String message = getMessage(msgID, valueString,
                                        valueString.substring(4, 6));
            invalidReason.append(message);
            return false;
        }
        break;
      default:
        int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_MONTH;
        String message = getMessage(msgID, valueString,
                                    valueString.substring(4, 6));
        invalidReason.append(message);
        return false;
    }


    // The next two characters should be the day of the month, and they must
    // form the string representation of an integer between 01 and 31.
    // This doesn't do any validation against the year or month, so it will
    // allow dates like April 31, or February 29 in a non-leap year, but we'll
    // let those slide.
    char d1 = valueString.charAt(6);
    char d2 = valueString.charAt(7);
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
            int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_DAY;
            String message = getMessage(msgID, valueString,
                                        valueString.substring(6, 8));
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
            int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_DAY;
            String message = getMessage(msgID, valueString,
                                        valueString.substring(6, 8));
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
            int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_DAY;
            String message = getMessage(msgID, valueString,
                                        valueString.substring(6, 8));
            invalidReason.append(message);
            return false;
        }
        break;
      default:
        int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_DAY;
        String message = getMessage(msgID, valueString,
                                    valueString.substring(6, 8));
        invalidReason.append(message);
        return false;
    }


    // The next two characters must be the hour, and they must form the string
    // representation of an integer between 00 and 23.
    char h1 = valueString.charAt(8);
    char h2 = valueString.charAt(9);
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
            int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_HOUR;
            String message = getMessage(msgID, valueString,
                                        valueString.substring(8, 10));
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
            int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_HOUR;
            String message = getMessage(msgID, valueString,
                                        valueString.substring(8, 10));
            invalidReason.append(message);
            return false;
        }
        break;
      default:
        int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_HOUR;
        String message = getMessage(msgID, valueString,
                                    valueString.substring(8, 10));
        invalidReason.append(message);
        return false;
    }


    // Next, there should be either two digits comprising an integer between 00
    // and 59 (for the minute), a letter 'Z' (for the UTC specifier), or a plus
    // or minus sign followed by two or four digits (for the UTC offset).
    m1 = valueString.charAt(10);
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
        if (length < 13)
        {
          int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_CHAR;
          String message = getMessage(msgID, valueString, m1, 10);
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
            int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_MINUTE;
            String message = getMessage(msgID, valueString,
                                        valueString.substring(10, 12));
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
          int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_CHAR;
          String message = getMessage(msgID, valueString, m1, 10);
          invalidReason.append(message);
          return false;
        }

      case '+':
      case '-':
        // These are fine only if there are exactly two or four more digits that
        // specify a valid offset.
        if ((length == 13) || (length == 15))
        {
          return hasValidOffset(valueString, 11, invalidReason);
        }
        else
        {
          int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_CHAR;
          String message = getMessage(msgID, valueString, m1, 10);
          invalidReason.append(message);
          return false;
        }

      default:
        int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_CHAR;
        String message = getMessage(msgID, valueString, m1, 10);
        invalidReason.append(message);
        return false;
    }


    // Next, there should be either two digits comprising an integer between 00
    // and 60 (for the second, including a possible leap second), a letter 'Z'
    // (for the UTC specifier), or a plus or minus sign followed by two or four
    // digits (for the UTC offset).
    char s1 = valueString.charAt(12);
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
        if (length < 15)
        {
          int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_CHAR;
          String message = getMessage(msgID, valueString, s1, 12);
          invalidReason.append(message);
          return false;
        }

        switch (valueString.charAt(13))
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
            int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_SECOND;
            String message = getMessage(msgID, valueString,
                                        valueString.substring(12, 14));
            invalidReason.append(message);
            return false;
        }

        break;
      case '6':
        // There must be at least two more characters and the next one must be
        // a 0.
        if (length < 15)
        {
          int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_CHAR;
          String message = getMessage(msgID, valueString, s1, 12);
          invalidReason.append(message);
          return false;
        }

        if (valueString.charAt(13) != '0')
        {
          int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_SECOND;
          String message = getMessage(msgID, valueString,
                                      valueString.substring(12, 14));
          invalidReason.append(message);
          return false;
        }

        break;
      case 'Z':
        // This is fine only if we are at the end of the value.
        if (length == 13)
        {
          return true;
        }
        else
        {
          int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_CHAR;
          String message = getMessage(msgID, valueString, s1, 12);
          invalidReason.append(message);
          return false;
        }

      case '+':
      case '-':
        // These are fine only if there are exactly two or four more digits that
        // specify a valid offset.
        if ((length == 15) || (length == 17))
        {
          return hasValidOffset(valueString, 13, invalidReason);
        }
        else
        {
          int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_CHAR;
          String message = getMessage(msgID, valueString, s1, 12);
          invalidReason.append(message);
          return false;
        }

      default:
        int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_CHAR;
        String message = getMessage(msgID, valueString, s1, 12);
        invalidReason.append(message);
        return false;
    }


    // Next, there should be either a period or comma followed by between one
    // and three digits (to specify the sub-second), a letter 'Z' (for the UTC
    // specifier), or a plus or minus sign followed by two our four digits (for
    // the UTC offset).
    switch (valueString.charAt(14))
    {
      case '.':
      case ',':
        // There will be a sub-second portion.  Walk through the rest of the
        // value until we find a Z, +, or -.
        boolean endFound = false;
        int pos = 15;
        while (pos < length)
        {

          switch (valueString.charAt(pos))
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
              // These are fine as long as we don't have more than three.
              if (pos > 17)
              {
                int msgID = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_LONG_SUBSECOND;
                String message = getMessage(msgID, value);
                invalidReason.append(message);
                return false;
              }

              break;
            case 'Z':
              // This must be the end of the string and there must have been
              // at least one sub-second digit.
              if (pos == 15)
              {
                int msgID =
                     MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_SUBSECOND;
                String message = getMessage(msgID, value);
                invalidReason.append(message);
                return false;
              }

              if (pos != (length-1))
              {
                int msgID = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_CHAR;
                String message = getMessage(msgID, valueString,
                                            valueString.charAt(pos), pos);
                invalidReason.append(message);
                return false;
              }

              return true;

            case '+':
            case '-':
              // There must have been at least one sub-second digit, and there
              // must be either two or four digits left.
              if (pos == 15)
              {
                int msgID =
                     MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_SUBSECOND;
                String message = getMessage(msgID, value);
                invalidReason.append(message);
                return false;
              }
              else if ((length != 17) && (length != 19))
              {
                int msgID = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_CHAR;
                String message = getMessage(msgID, valueString,
                                            valueString.charAt(pos), pos);
                invalidReason.append(message);
                return false;
              }
              else
              {
                return hasValidOffset(valueString, pos+1, invalidReason);
              }

            default:
              int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_CHAR;
              String message = getMessage(msgID, valueString,
                                          valueString.charAt(pos), pos);
              invalidReason.append(message);
              return false;
          }

          pos++;
        }


        // There must be at least two more characters and the first must be a
        // digit.
        if (length < 16)
        {
          int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_CHAR;
          String message = getMessage(msgID, valueString, s1, 12);
          invalidReason.append(message);
          return false;
        }

        break;

      case 'Z':
        // This is fine only if we are at the end of the value.
        if (length == 15)
        {
          return true;
        }
        else
        {
          int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_CHAR;
          String message = getMessage(msgID, valueString,
                                      valueString.charAt(14), 14);
          invalidReason.append(message);
          return false;
        }

      case '+':
      case '-':
        // These are fine only if there are exactly two or four more digits that
        // specify a valid offset.
        if ((length == 17) || (length == 19))
        {
          return hasValidOffset(valueString, 15, invalidReason);
        }
        else
        {
          int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_CHAR;
          String message = getMessage(msgID, valueString,
                                      valueString.charAt(14), 14);
          invalidReason.append(message);
          return false;
        }

      default:
        int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_CHAR;
        String message = getMessage(msgID, valueString, valueString.charAt(14),
                                    14);
        invalidReason.append(message);
        return false;
    }

    return true;
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
                                 StringBuilder invalidReason)
  {


    int offsetLength = value.length() - startPos;
    if (offsetLength < 2)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_TOO_SHORT;
      String message = getMessage(msgID, value);
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
            int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_OFFSET;
            String message = getMessage(msgID, value,
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
            int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_OFFSET;
            String message = getMessage(msgID, value,
                                        value.substring(startPos,
                                                        startPos+offsetLength));
            invalidReason.append(message);
            return false;
        }
        break;
      default:
        int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_OFFSET;
        String message = getMessage(msgID, value,
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
              int msgID = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_OFFSET;
              String message =
                   getMessage(msgID, value,value.substring(startPos,
                                                      startPos+offsetLength));
              invalidReason.append(message);
              return false;
          }
          break;
        default:
          int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_OFFSET;
          String message = getMessage(msgID, value,
                                      value.substring(startPos,
                                                      startPos+offsetLength));
          invalidReason.append(message);
          return false;
      }
    }

    return true;
  }



  /**
   * Retrieves the generalized time representation of the provided date.
   *
   * @param  d  The date to retrieve in generalized time form.
   *
   * @return  The generalized time representation of the provided date.
   */
  public static String format(Date d)
  {

    dateFormatLock.lock();

    try
    {
      return dateFormat.format(d);
    }
    finally
    {
      dateFormatLock.unlock();
    }
  }



  /**
   * Retrieves the generalized time representation of the provided date.
   *
   * @param  t  The timestamp to retrieve in generalized time form.
   *
   * @return  The generalized time representation of the provided date.
   */
  public static String format(long t)
  {

    dateFormatLock.lock();

    try
    {
      return dateFormat.format(new Date(t));
    }
    finally
    {
      dateFormatLock.unlock();
    }
  }




  /**
   * Retrieves an attribute value containing a generalized time representation
   * of the provided date.
   *
   * @param  time  The time for which to retrieve the generalized time value.
   *
   * @return  The attribute value created from the date.
   */
  public static AttributeValue createGeneralizedTimeValue(long time)
  {

    String valueString;

    dateFormatLock.lock();

    try
    {
      valueString = dateFormat.format(new Date(time));
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      // This should never happen.
      valueString = null;
    }
    finally
    {
      dateFormatLock.unlock();
    }

    return new AttributeValue(new ASN1OctetString(valueString),
                              new ASN1OctetString(valueString));
  }



  /**
   * Decodes the provided normalized value as a generalized time value and
   * retrieves a timestamp containing its representation.
   *
   * @param  normalizedValue  The normalized generalized time value to decode to
   *                          a Java <CODE>Date</CODE>.
   *
   * @return  The timestamp created from the provided generalized time value.
   *
   * @throws  DirectoryException  If the provided value cannot be parsed as a
   *                              valid generalized time string.
   */
  public static long decodeGeneralizedTimeValue(ByteString normalizedValue)
         throws DirectoryException
  {

    String valueString = normalizedValue.stringValue();
    try
    {
      dateFormatLock.lock();

      try
      {
        return dateFormat.parse(valueString).getTime();
      }
      catch (Exception e)
      {
        // We'll let this one be handled by the outer try/catch block.
        throw e;
      }
      finally
      {
        dateFormatLock.unlock();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_CANNOT_PARSE;
      String message = getMessage(msgID, valueString, String.valueOf(e));

      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                   message, msgID, e);
    }
  }
}

