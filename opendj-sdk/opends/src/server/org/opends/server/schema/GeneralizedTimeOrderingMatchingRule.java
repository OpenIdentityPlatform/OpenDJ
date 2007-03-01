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
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.concurrent.locks.ReentrantLock;

import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.AcceptRejectWarn;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines the generalizedTimeOrderingMatch matching rule defined in
 * X.520 and referenced in RFC 2252.
 */
public class GeneralizedTimeOrderingMatchingRule
       extends OrderingMatchingRule
{



  /**
   * The serial version identifier required to satisfy the compiler because this
   * class implements the <CODE>java.io.Serializable</CODE> interface.  This
   * value was generated using the <CODE>serialver</CODE> command-line utility
   * included with the Java SDK.
   */
  private static final long serialVersionUID = -6343622924726948145L;



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



  /**
   * The time zone used for UTC time.
   */
  private static TimeZone utcTimeZone;



  /*
   * Create the date formatter that will be used to construct and parse
   * normalized generalized time values.
   */
  static
  {
    utcTimeZone = TimeZone.getTimeZone("UTC");

    dateFormat = new SimpleDateFormat(DATE_FORMAT_GENERALIZED_TIME);
    dateFormat.setLenient(false);
    dateFormat.setTimeZone(utcTimeZone);

    dateFormatLock = new ReentrantLock();
  }



  /**
   * Creates a new instance of this generalizedTimeMatch matching rule.
   */
  public GeneralizedTimeOrderingMatchingRule()
  {
    super();

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
    return OMR_GENERALIZED_TIME_NAME;
  }



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  public String getOID()
  {
    return OMR_GENERALIZED_TIME_OID;
  }



  /**
   * Retrieves the description for this matching rule.
   *
   * @return  The description for this matching rule, or <CODE>null</CODE> if
   *          there is none.
   */
  public String getDescription()
  {
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
    return SYNTAX_GENERALIZED_TIME_OID;
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
    String valueString = value.stringValue().toUpperCase();
    int length = valueString.length();


    //Make sure that it has at least eleven characters and parse the first ten
    // as the year, month, day, and hour.
    if (length < 11)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_TOO_SHORT;
      String message = getMessage(msgID, valueString);

      switch (DirectoryServer.getSyntaxEnforcementPolicy())
      {
        case REJECT:
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID);
        case WARN:
          logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                   message, msgID);
          return new ASN1OctetString(valueString);
        default:
          return new ASN1OctetString(valueString);
      }
    }


    // The year, month, day, and hour must always be specified.
    int year;
    int month;
    int day;
    int hour;
    try
    {
      year  = Integer.parseInt(valueString.substring(0, 4));
      month = Integer.parseInt(valueString.substring(4, 6));
      day   = Integer.parseInt(valueString.substring(6, 8));
      hour  = Integer.parseInt(valueString.substring(8, 10));
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_CANNOT_PARSE;
      String message = getMessage(msgID, valueString,
                                  String.valueOf(e));

      switch (DirectoryServer.getSyntaxEnforcementPolicy())
      {
        case REJECT:
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID);
        case WARN:
          logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                   message, msgID);
          return new ASN1OctetString(valueString);
        default:
          return new ASN1OctetString(valueString);
      }
    }


    // The minute may come next, but if not then it should indicate that we've
    // hit the end of the value.
    int  minute;
    if (isDigit(valueString.charAt(10)))
    {
      try
      {
        minute = Integer.parseInt(valueString.substring(10, 12));
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_CANNOT_PARSE;
        String message = getMessage(msgID, valueString,
                                    String.valueOf(e));

        switch (DirectoryServer.getSyntaxEnforcementPolicy())
        {
          case REJECT:
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message, msgID);
          case WARN:
            logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                     message, msgID);
            return new ASN1OctetString(valueString);
          default:
            return new ASN1OctetString(valueString);
        }
      }
    }
    else
    {
      return processValueEnd(valueString, 10, year, month, day, hour, 0, 0, 0);
    }


    // The second should come next, but if not then it should indicate that
    // we've hit the end of the value.
    int second;
    if (length < 13)
    {
      // Technically, this is invalid.  If we're enforcing strict syntax
      // adherence, then throw an exception.  Otherwise, just assume that it's
      // a time with a second of zero and parse it in the local time zone.
      if (DirectoryServer.getSyntaxEnforcementPolicy() ==
          AcceptRejectWarn.REJECT)
      {
        int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_TOO_SHORT;
        String message = getMessage(msgID, valueString);
        throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                     message, msgID);
      }
      else
      {
        GregorianCalendar calendar =
             new GregorianCalendar(year, (month-1), day, hour, minute, 0);
        calendar.setTimeZone(utcTimeZone);

        dateFormatLock.lock();

        try
        {
          return new ASN1OctetString(dateFormat.format(calendar.getTime()));
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
          }

          int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_NORMALIZE_FAILURE;
          String message = getMessage(msgID, valueString,
                                      stackTraceToSingleLineString(e));

          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID, e);
        }
        finally
        {
          dateFormatLock.unlock();
        }
      }
    }
    else
    {
      if (isDigit(valueString.charAt(12)))
      {
        try
        {
          second = Integer.parseInt(valueString.substring(12, 14));
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
          }

          int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_CANNOT_PARSE;
          String message = getMessage(msgID, valueString,
                                      String.valueOf(e));

          if (DirectoryServer.getSyntaxEnforcementPolicy() ==
              AcceptRejectWarn.REJECT)
          {
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message, msgID, e);
          }
          else
          {
            logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.MILD_ERROR,
                     message, msgID);
            return new ASN1OctetString(valueString);
          }
        }
      }
      else
      {
        return processValueEnd(valueString, 12, year, month, day, hour, minute,
                               0, 0);
      }
    }


    // If the next character is a period, then it will start the sub-second
    // portion of the value.  Otherwise, it should indicate that we've hit the
    // end of the value.
    if (length < 15)
    {
      // Technically, this is invalid.  If we're enforcing strict syntax
      // adherence, then throw an exception.  Otherwise, just assume that it's
      // a time with a second of zero and parse it in the local time zone.
      if (DirectoryServer.getSyntaxEnforcementPolicy() ==
          AcceptRejectWarn.REJECT)
      {
        int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_TOO_SHORT;
        String message = getMessage(msgID, valueString);
        throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                     message, msgID);
      }
      else
      {
        GregorianCalendar calendar =
             new GregorianCalendar(year, (month-1), day, hour, minute, second);
        calendar.setTimeZone(utcTimeZone);

        dateFormatLock.lock();

        try
        {
          return new ASN1OctetString(dateFormat.format(calendar.getTime()));
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
          }

          int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_NORMALIZE_FAILURE;
          String message = getMessage(msgID, valueString,
                                      stackTraceToSingleLineString(e));

          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID, e);
        }
        finally
        {
          dateFormatLock.unlock();
        }
      }
    }
    else
    {
      if (valueString.charAt(14) == '.')
      {
        // There should be some number of digits following the decimal point to
        // indicate the sub-second value.  We'll read all of them now, but may
        // throw some away later.
        char c;
        int pos = 15;
        StringBuilder buffer = new StringBuilder(3);
        for ( ; pos < length; pos++)
        {
          if (isDigit(c = valueString.charAt(pos)))
          {
            buffer.append(c);
          }
          else
          {
            break;
          }
        }

        int millisecond;
        switch (buffer.length())
        {
          case 0:
            millisecond = 0;
            break;
          case 1:
            millisecond = (100 * Integer.parseInt(buffer.toString()));
            break;
          case 2:
            millisecond = (10 * Integer.parseInt(buffer.toString()));
            break;
          case 3:
            millisecond = Integer.parseInt(buffer.toString());
            break;
          default:
            // We only want three digits for the millisecond, but if the fourth
            // digit is greater than or equal to five, then we may need to round
            // up.
            millisecond = Integer.parseInt(buffer.toString().substring(0, 3));
            switch (buffer.charAt(3))
            {
              case 5:
              case 6:
              case 7:
              case 8:
              case 9:
                millisecond++;
                break;
            }
            break;
        }

        return processValueEnd(valueString, pos, year, month, day, hour, minute,
                               second, millisecond);
      }
      else
      {
        return processValueEnd(valueString, 14, year, month, day, hour, minute,
                               second, 0);
      }
    }
  }



  /**
   * Processes the specified portion of the value as the end of the generalized
   * time specification.  If the character at the specified location is a 'Z',
   * then it will be assumed that the value is already in UTC.  If it is a '+'
   * or '-', then it will be assumed that the remainder is an offset from UTC.
   * Otherwise, it will be an error.
   *
   * @param  valueString  The value being parsed as a generalized time string.
   * @param  endPos       The position at which the end of the value begins.
   * @param  year         The year parsed from the value.
   * @param  month        The month parsed from the value.
   * @param  day          The day parsed from the value.
   * @param  hour         The hour parsed from the value.
   * @param  minute       The minute parsed from the value.
   * @param  second       The second parsed from the value.
   * @param  millisecond  The millisecond parsed from the value.
   *
   * @return  The normalized representation of the generalized time parsed from
   *          the value.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to decode
   *                              the end of the generalized time value.
   */
  private ByteString processValueEnd(String valueString, int endPos, int year,
                                     int month, int day, int hour, int minute,
                                     int second, int millisecond)
          throws DirectoryException
  {
    // First, check to see if we are at the end of the string.  If so, then
    // that could either result in an exception or assuming that we should just
    // use the local time zone.
    int length = valueString.length();
    if (endPos >= length)
    {
      if (DirectoryServer.getSyntaxEnforcementPolicy() ==
          AcceptRejectWarn.REJECT)
      {
        int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_TOO_SHORT;
        String message = getMessage(msgID, valueString);
        throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                     message, msgID);
      }
      else
      {
        GregorianCalendar calendar =
             new GregorianCalendar(year, (month-1), day, hour, minute, second);
        calendar.setTimeZone(utcTimeZone);
        calendar.set(Calendar.MILLISECOND, millisecond);

        dateFormatLock.lock();

        try
        {
          return new ASN1OctetString(dateFormat.format(calendar.getTime()));
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
          }

          int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_NORMALIZE_FAILURE;
          String message = getMessage(msgID, valueString,
                                      stackTraceToSingleLineString(e));

          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID, e);
        }
        finally
        {
          dateFormatLock.unlock();
        }
      }
    }


    // See what the character is at the specified position.  If it is a 'Z',
    // then make sure it's the end of the value and treat it as a UTC date.
    char c = valueString.charAt(endPos);
    if (c == 'Z')
    {
      if (endPos == (length-1))
      {
        GregorianCalendar calendar =
             new GregorianCalendar(year, (month-1), day, hour, minute, second);
        calendar.setTimeZone(utcTimeZone);
        calendar.set(Calendar.MILLISECOND, millisecond);

        dateFormatLock.lock();

        try
        {
          return new ASN1OctetString(dateFormat.format(calendar.getTime()));
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
          }

          int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_NORMALIZE_FAILURE;
          String message = getMessage(msgID, valueString,
                                      stackTraceToSingleLineString(e));

          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID, e);
        }
        finally
        {
          dateFormatLock.unlock();
        }
      }
      else
      {
        // This is weird because the Z wasn't the last character.  If we should
        // enforce strict syntax checking, then throw an exception.  Otherwise,
        // return what we've got so far.
        if (DirectoryServer.getSyntaxEnforcementPolicy() ==
            AcceptRejectWarn.REJECT)
        {
          int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_CHAR;
          String message = getMessage(msgID, valueString, 'Z', endPos);
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID);
        }
        else
        {
          GregorianCalendar calendar =
               new GregorianCalendar(TimeZone.getTimeZone("UTC"));
          calendar.setTimeZone(utcTimeZone);
          calendar.set(year, (month-1), day, hour, minute, second);
          calendar.set(Calendar.MILLISECOND, millisecond);

          dateFormatLock.lock();

          try
          {
            return new ASN1OctetString(dateFormat.format(calendar.getTime()));
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            int msgID = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_NORMALIZE_FAILURE;
            String message = getMessage(msgID, valueString,
                                        stackTraceToSingleLineString(e));

            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message, msgID, e);
          }
          finally
          {
            dateFormatLock.unlock();
          }
        }
      }
    }


    // If the character is a plus or minus, then take the next two or four
    // digits and use them as a time zone offset.
    else if ((c == '-') || (c == '+'))
    {
      int offset;
      int charsRemaining = length - endPos - 1;
      if (charsRemaining == 2)
      {
        // The offset specifies the number of hours off GMT.
        try
        {
          offset = Integer.parseInt(valueString.substring(endPos+1)) * 3600000;
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
          }

          int msgID = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_OFFSET;
          String message = getMessage(msgID, valueString,
                                      valueString.substring(endPos));

          if (DirectoryServer.getSyntaxEnforcementPolicy() ==
              AcceptRejectWarn.REJECT)
          {
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message, msgID);
          }
          else
          {
            logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.MILD_ERROR,
                     message, msgID);
            offset = 0;
          }
        }
      }
      else if (charsRemaining == 4)
      {
        // The offset specifies the number of hours and minutes off GMT.
        try
        {
          String hourStr = valueString.substring(endPos+1, endPos+3);
          String minStr  = valueString.substring(endPos+3, endPos+5);
          offset = (Integer.parseInt(hourStr) * 3600000) +
                   (Integer.parseInt(minStr) * 1000);
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
          }

          int msgID = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_OFFSET;
          String message = getMessage(msgID, valueString,
                                      valueString.substring(endPos));

          if (DirectoryServer.getSyntaxEnforcementPolicy() ==
              AcceptRejectWarn.REJECT)
          {
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message, msgID);
          }
          else
          {
            logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.MILD_ERROR,
                     message, msgID);
            offset = 0;
          }
        }
      }
      else
      {
        // It is an invalid offset, so either throw an exception or assume the
        // local time zone.
        int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_OFFSET;
        String message = getMessage(msgID, valueString,
                                    valueString.substring(endPos));

        if (DirectoryServer.getSyntaxEnforcementPolicy() ==
            AcceptRejectWarn.REJECT)
        {
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID);
        }
        else
        {
          logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.MILD_ERROR,
                   message, msgID);
          offset = TimeZone.getDefault().getRawOffset();
        }
      }

      GregorianCalendar calendar = new GregorianCalendar(year, (month-1), day,
                                                         hour, minute, second);
      calendar.setTimeZone(utcTimeZone);
      calendar.set(Calendar.MILLISECOND, millisecond);
      calendar.set(Calendar.ZONE_OFFSET, offset);

      dateFormatLock.lock();

      try
      {
        return new ASN1OctetString(dateFormat.format(calendar.getTime()));
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_NORMALIZE_FAILURE;
        String message = getMessage(msgID, valueString,
                                    stackTraceToSingleLineString(e));

        throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                     message, msgID, e);
      }
      finally
      {
        dateFormatLock.unlock();
      }
    }


    // If we've gotten here, then there was an illegal character at the end of
    // the value.  Either throw an exception or assume the default time zone.
    int    msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_CHAR;
    String message = getMessage(msgID, valueString, c, endPos);

    if (DirectoryServer.getSyntaxEnforcementPolicy() ==
        AcceptRejectWarn.REJECT)
    {
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                   message, msgID);
    }
    else
    {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.MILD_ERROR, message,
               msgID);

      GregorianCalendar calendar = new GregorianCalendar(year, (month-1), day,
                                                         hour, minute, second);
      calendar.setTimeZone(utcTimeZone);
      calendar.set(Calendar.MILLISECOND, millisecond);

      dateFormatLock.lock();

      try
      {
        return new ASN1OctetString(dateFormat.format(calendar.getTime()));
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID   = MSGID_ATTR_SYNTAX_GENERALIZED_TIME_NORMALIZE_FAILURE;
        message = getMessage(msgID, valueString,
                                    stackTraceToSingleLineString(e));

        throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                     message, msgID, e);
      }
      finally
      {
        dateFormatLock.unlock();
      }
    }
  }



  /**
   * Compares the first value to the second and returns a value that indicates
   * their relative order.
   *
   * @param  value1  The normalized form of the first value to compare.
   * @param  value2  The normalized form of the second value to compare.
   *
   * @return  A negative integer if <CODE>value1</CODE> should come before
   *          <CODE>value2</CODE> in ascending order, a positive integer if
   *          <CODE>value1</CODE> should come after <CODE>value2</CODE> in
   *          ascending order, or zero if there is no difference between the
   *          values with regard to ordering.
   */
  public int compareValues(ByteString value1, ByteString value2)
  {
    return compare(value1.value(), value2.value());
  }



  /**
   * Compares the contents of the provided byte arrays to determine their
   * relative order.
   *
   * @param  b1  The first byte array to use in the comparison.
   * @param  b2  The second byte array to use in the comparison.
   *
   * @return  A negative integer if <CODE>b1</CODE> should come before
   *          <CODE>b2</CODE> in ascending order, a positive integer if
   *          <CODE>b1</CODE> should come after <CODE>b2</CODE> in ascending
   *          order, or zero if there is no difference between the values with
   *          regard to ordering.
   */
  public int compare(byte[] b1, byte[] b2)
  {
    int minLength = Math.min(b1.length, b2.length);

    for (int i=0; i < minLength; i++)
    {
      if (b1[i] == b2[i])
      {
        continue;
      }
      else if (b1[i] < b2[i])
      {
        return -1;
      }
      else if (b1[i] > b2[i])
      {
        return 1;
      }
    }

    if (b1.length == b2.length)
    {
      return 0;
    }
    else if (b1.length < b2.length)
    {
      return -1;
    }
    else
    {
      return 1;
    }
  }
}

