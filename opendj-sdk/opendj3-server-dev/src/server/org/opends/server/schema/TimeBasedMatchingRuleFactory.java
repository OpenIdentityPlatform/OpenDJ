/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS
 */
package org.opends.server.schema;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.opends.server.admin.std.server.MatchingRuleCfg;
import org.opends.server.api.AbstractMatchingRule;
import org.opends.server.api.ExtensibleIndexer;
import org.opends.server.api.ExtensibleMatchingRule;
import org.opends.server.api.MatchingRule;
import org.opends.server.api.MatchingRuleFactory;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.util.StaticUtils;

import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.schema.GeneralizedTimeSyntax.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.util.TimeThread.*;

/**
 * This class acts as a factory for time-based matching rules.
 */
public final class TimeBasedMatchingRuleFactory
        extends MatchingRuleFactory<MatchingRuleCfg>
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Greater-than RelativeTimeMatchingRule. */
  private MatchingRule greaterThanRTMRule;

  /** Less-than RelativeTimeMatchingRule. */
  private MatchingRule lessThanRTMRule;

  /** PartialDayAndTimeMatchingRule. */
  private MatchingRule partialDTMatchingRule;

  /** A Collection of matching rules managed by this factory. */
  private Set<MatchingRule> matchingRules;

  private static final TimeZone TIME_ZONE_UTC_OBJ =
      TimeZone.getTimeZone(TIME_ZONE_UTC);


  /** Constants for generating keys. */
  private static final char SECOND = 's';
  private static final char MINUTE = 'm';
  private static final char HOUR = 'h';
  private static final char MONTH = 'M';
  private static final char DATE = 'D';
  private static final char YEAR = 'Y';


  /** {@inheritDoc} */
  @Override
  public void initializeMatchingRule(MatchingRuleCfg configuration)
          throws ConfigException, InitializationException
  {
    matchingRules = new HashSet<MatchingRule>();
    greaterThanRTMRule = new RelativeTimeGTOrderingMatchingRule();
    matchingRules.add(greaterThanRTMRule);
    lessThanRTMRule = new RelativeTimeLTOrderingMatchingRule();
    matchingRules.add(lessThanRTMRule);
    partialDTMatchingRule = new PartialDateAndTimeMatchingRule();
    matchingRules.add(partialDTMatchingRule);
  }



  /** {@inheritDoc} */
  @Override
  public Collection<MatchingRule> getMatchingRules()
  {
    return Collections.unmodifiableCollection(matchingRules);
  }



  /**
   * This class defines a matching rule which is used for time-based searches.
   */
  private  abstract class TimeBasedMatchingRule extends AbstractMatchingRule
          implements ExtensibleMatchingRule
  {
    /** {@inheritDoc} */
    @Override
    public String getDescription()
    {
      //There is no standard definition.
      return null;
    }



    /** {@inheritDoc} */
    @Override
    public String getSyntaxOID()
    {
       return SYNTAX_GENERALIZED_TIME_OID;
    }



    /** {@inheritDoc} */
    @Override
    public ByteString normalizeAttributeValue(ByteSequence value)
            throws DecodeException
    {
      try
      {
        long timestamp = decodeGeneralizedTimeValue(value);
        return ByteString.valueOf(timestamp);
      }
      catch (DirectoryException de)
      {
        switch (DirectoryServer.getSyntaxEnforcementPolicy())
        {
          case REJECT:
            throw DecodeException.error(de.getMessageObject(), de);

          case WARN:
            logger.error(de.getMessageObject());
            break;
        }
        return value.toByteString();
      }
    }
  }



 /**
  * This class defines a matching rule which matches  the relative time for
  * time-based searches.
  */
  private abstract class RelativeTimeOrderingMatchingRule
          extends TimeBasedMatchingRule
          implements OrderingMatchingRule
  {
    /**
     * The serial version identifier required to satisfy the compiler because
     * this class implements the <CODE>java.io.Serializable</CODE> interface.
     * This value was generated using the <CODE>serialver</CODE> command-line
     * utility included with the Java SDK.
     */
    private static final long serialVersionUID = -3501812894473163490L;



    /**
     * Indexer associated with this instance.
     */
    protected ExtensibleIndexer indexer;


    /** {@inheritDoc} */
    @Override
    public ByteString normalizeAssertionValue(ByteSequence value)
        throws DecodeException
    {
      /**
      An assertion value may contain one of the following:
      s = second
      m = minute
      h = hour
      d = day
      w = week

      An example assertion is OID:=(-)1d, where a '-' means that the user
      intends to search only the expired events. In this example we are
      searching for an event expired 1 day back.

      Use this method to parse, validate and normalize the assertion value
      into a format to be recognized by the valuesMatch routine. This method
      takes the assertion value, adds/substracts it to/from the current time
      and calculates a time which will be used as a relative time by inherited
       rules.
      */

      int index = 0;
      boolean signed = false;
      byte firstByte = value.byteAt(0);

      if(firstByte == '-')
      {
        //Turn the sign on to go back in past.
        signed = true;
        index = 1;
      }
      else if(firstByte == '+')
      {
        //'+" is not required but we won't reject it either.
        index = 1;
      }

      long second = 0;
      long minute = 0;
      long hour = 0;
      long day = 0;
      long week = 0;

      boolean containsTimeUnit = false;
      int number = 0;

      for(; index<value.length(); index++)
      {
        byte b = value.byteAt(index);
        if(isDigit((char)b))
        {
          number = multiplyByTenThenAddUnits(number, b);
        }
        else
        {
          LocalizableMessage message = null;
          if(containsTimeUnit)
          {
            //We already have time unit found by now.
            message = WARN_ATTR_CONFLICTING_ASSERTION_FORMAT.get(value);
          }
          else
          {
            switch(b)
            {
              case 's':
                second = number;
                break;
              case 'm':
                minute = number;
                break;
              case 'h':
                hour = number;
                break;
              case 'd':
                day = number;
                break;
              case 'w':
                week = number;
                break;
              default:
                message = WARN_ATTR_INVALID_RELATIVE_TIME_ASSERTION_FORMAT.get(value, (char) b);
            }
          }
          if(message !=null)
          {
            //Log the message and throw an exception.
            logger.error(message);
            throw DecodeException.error(message);
          }
          containsTimeUnit = true;
          number = 0;
        }
      }

      if(!containsTimeUnit)
      {
        //There was no time unit so assume it is seconds.
        second = number;
      }

      long delta = (second + minute*60 +  hour*3600 + day*24*3600 +
              week*7*24*3600)*1000 ;
      long now = getTime();
      return ByteString.valueOf(signed ? now - delta : now + delta);
    }



    /** {@inheritDoc} */
    @Override
    public int compareValues(ByteSequence value1, ByteSequence value2)
    {
      return value1.compareTo(value2);
    }



    /** {@inheritDoc} */
    @Override
    public int compare(byte[] arg0, byte[] arg1)
    {
      return StaticUtils.compare(arg0, arg1);
    }



    /** {@inheritDoc} */
    @Override
    public Collection<ExtensibleIndexer> getIndexers()
    {
      if(indexer == null)
      {
        indexer = new RelativeTimeExtensibleIndexer(this);
      }
      return Collections.singletonList(indexer);
    }
  }



 /**
  * This class defines a matching rule which calculates the "greater-than"
  * relative time for time-based searches.
  */
  private final class RelativeTimeGTOrderingMatchingRule
          extends RelativeTimeOrderingMatchingRule
  {
    /** All the names for this matching rule. */
    private final List<String> names;



    /**
     * The serial version identifier required to satisfy the compiler because
     * this class implements the <CODE>java.io.Serializable</CODE> interface.
     * This value was generated using the <CODE>serialver</CODE> command-line
     * utility included with the Java SDK.
     */
     private static final long serialVersionUID = 7247241496402474136L;


    RelativeTimeGTOrderingMatchingRule()
    {
      names = new ArrayList<String>();
      names.add(EXT_OMR_RELATIVE_TIME_GT_NAME);
      names.add(EXT_OMR_RELATIVE_TIME_GT_ALT_NAME);
    }


    /** {@inheritDoc} */
    @Override
    public Collection<String> getNames()
    {
      return Collections.unmodifiableList(names);
    }



    /** {@inheritDoc} */
    @Override
    public String getOID()
    {
      return EXT_OMR_RELATIVE_TIME_GT_OID;
    }



    /** {@inheritDoc} */
    @Override
    public ConditionResult valuesMatch(ByteSequence attributeValue,
        ByteSequence assertionValue)
    {
      int ret = compareValues(attributeValue, assertionValue);
      return ConditionResult.valueOf(ret > 0);
    }

    /** {@inheritDoc} */
    @Override
    public Assertion getAssertion(final ByteSequence value)
        throws DecodeException
    {
      final ByteString assertionValue = normalizeAssertionValue(value);
      return new Assertion()
      {
        @Override
        public ConditionResult matches(ByteSequence attributeValue)
        {
          return valuesMatch(attributeValue, assertionValue);
        }

        @Override
        public <T> T createIndexQuery(IndexQueryFactory<T> factory)
            throws DecodeException
        {
          return factory.createRangeMatchQuery(indexer.getExtensibleIndexID(),
              assertionValue, ByteString.empty(), false, false);
        }
      };
    }


    /** {@inheritDoc} */
    @Override
    public <T> T createIndexQuery(ByteSequence assertionValue,
        IndexQueryFactory<T> factory) throws DecodeException
    {
      return getAssertion(assertionValue).createIndexQuery(factory);
    }
  }



  /**
  * This class defines a matching rule which calculates the "less-than"
  * relative time for time-based searches.
  */
  private final class RelativeTimeLTOrderingMatchingRule
          extends RelativeTimeOrderingMatchingRule
  {
    /** All the names for this matching rule. */
    private final List<String> names;



    /**
     * The serial version identifier required to satisfy the compiler because
     * this class implements the <CODE>java.io.Serializable</CODE> interface.
     * This value was generated using the <CODE>serialver</CODE> command-line
     * utility included with the Java SDK.
     */
   private static final long serialVersionUID = -5122459830973558441L;



    RelativeTimeLTOrderingMatchingRule()
    {
      names = new ArrayList<String>();
      names.add(EXT_OMR_RELATIVE_TIME_LT_NAME);
      names.add(EXT_OMR_RELATIVE_TIME_LT_ALT_NAME);
    }


    /** {@inheritDoc} */
    @Override
    public Collection<String> getNames()
    {
      return Collections.unmodifiableList(names);
    }



    /** {@inheritDoc} */
    @Override
    public String getOID()
    {
      return EXT_OMR_RELATIVE_TIME_LT_OID;
    }



    /** {@inheritDoc} */
    @Override
    public ConditionResult valuesMatch(ByteSequence attributeValue,
        ByteSequence assertionValue)
    {
      int ret = compareValues(attributeValue, assertionValue);
      return ConditionResult.valueOf(ret < 0);
    }

    /** {@inheritDoc} */
    @Override
    public Assertion getAssertion(final ByteSequence value)
        throws DecodeException
    {
      final ByteString assertionValue = normalizeAssertionValue(value);
      return new Assertion()
      {
        @Override
        public ConditionResult matches(ByteSequence attributeValue)
        {
          return valuesMatch(attributeValue, assertionValue);
        }

        @Override
        public <T> T createIndexQuery(IndexQueryFactory<T> factory)
            throws DecodeException
        {
          return factory.createRangeMatchQuery(indexer.getExtensibleIndexID(),
              ByteString.empty(), assertionValue, false, false);
        }
      };
    }

    /** {@inheritDoc} */
    @Override
    public <T> T createIndexQuery(ByteSequence assertionValue,
        IndexQueryFactory<T> factory) throws DecodeException
    {
      return getAssertion(assertionValue).createIndexQuery(factory);
    }
  }



  /**
   * Extensible Indexer class for Relative Time Matching rules which share
   * the same index. This Indexer is shared by both greater than and less than
   * Relative Time Matching Rules.
   */
  private final class RelativeTimeExtensibleIndexer extends
      ExtensibleIndexer
  {

    /**
     * The Extensible Matching Rule.
     */
    private final RelativeTimeOrderingMatchingRule matchingRule;



    /**
     * Creates a new instance of RelativeTimeExtensibleIndexer.
     *
     * @param matchingRule The relative time Matching Rule.
     */
    private RelativeTimeExtensibleIndexer(
        RelativeTimeOrderingMatchingRule matchingRule)
    {
      this.matchingRule = matchingRule;
    }



    /** {@inheritDoc} */
    @Override
    public String getExtensibleIndexID()
    {
      return EXTENSIBLE_INDEXER_ID_DEFAULT;
    }



    /** {@inheritDoc} */
    @Override
    public final void createKeys(Schema schema, ByteSequence value2,
        IndexingOptions options, Collection<ByteString> keys)
        throws DecodeException
    {
      keys.add(matchingRule.normalizeAttributeValue(value2));
    }

    /** {@inheritDoc} */
    @Override
    public String getIndexID()
    {
      return RELATIVE_TIME_INDEX_NAME + "." + getExtensibleIndexID();
    }
  }



  /**
   * This class performs the partial date and time matching capabilities.
   */
  private final class PartialDateAndTimeMatchingRule
          extends TimeBasedMatchingRule
  {
    /**
     * Indexer associated with this instance.
     */
    private ExtensibleIndexer indexer;



    /** {@inheritDoc} */
    @Override
    public String getOID()
    {
      return EXT_PARTIAL_DATE_TIME_OID;
    }



    /** {@inheritDoc} */
    @Override
    public Collection<String> getNames()
    {
      return Collections.singleton(EXT_PARTIAL_DATE_TIME_NAME);
    }



    /** {@inheritDoc} */
    @Override
    public ByteString normalizeAssertionValue(ByteSequence value)
        throws DecodeException
    {
     /**
      An assertion value may contain one or all of the following:
      D = day
      M = month
      Y = year
      h = hour
      m = month
      s = second

      An example assertion is OID:=04M. In this example we are
      searching for entries corresponding to month of april.

      Use this method to parse, validate and normalize the assertion value
      into a format to be recognized by the compare routine. The normalized
      value is actually the format of : smhDMY.
      */
      final int initDate = 0;
      final int initVal = -1;
      int second = initVal;
      int minute = initVal;
      int hour = initVal;
      int date = initDate;
      int month = initVal;
      int year = initDate;
      int number = 0;

      int length = value.length();
      for(int index=0; index<length; index++)
      {
        byte b = value.byteAt(index);
        if(isDigit((char)b))
        {
          number = multiplyByTenThenAddUnits(number, b);
        }
        else
        {
          LocalizableMessage message = null;
          switch(b)
          {
            case 's':
              if (second != initVal)
              {
                 message = WARN_ATTR_DUPLICATE_SECOND_ASSERTION_FORMAT.get(value, date);
              }
              else
              {
                second = number;
              }
              break;
            case 'm':
              if (minute != initVal)
              {
                 message = WARN_ATTR_DUPLICATE_MINUTE_ASSERTION_FORMAT.get(value, date);
              }
              else
              {
                minute = number;
              }
              break;
            case 'h':
              if (hour != initVal)
              {
                 message = WARN_ATTR_DUPLICATE_HOUR_ASSERTION_FORMAT.get(value, date);
              }
              else
              {
                hour = number;
              }
              break;
            case 'D':
              if(number == 0)
              {
                message = WARN_ATTR_INVALID_DATE_ASSERTION_FORMAT.get(value, number);
              }
            else if (date != initDate)
              {
                message = WARN_ATTR_DUPLICATE_DATE_ASSERTION_FORMAT.get(value, date);
              }
              else
              {
                date = number;
              }
              break;
            case 'M':
              if (number == 0)
              {
                message = WARN_ATTR_INVALID_MONTH_ASSERTION_FORMAT.get(value, number);
              }
              else if (month != initVal)
              {
                message = WARN_ATTR_DUPLICATE_MONTH_ASSERTION_FORMAT.get(value, month);
              }
              else
              {
                month = number;
              }
              break;
            case 'Y':
              if(number == 0)
              {
                message = WARN_ATTR_INVALID_YEAR_ASSERTION_FORMAT.get(value, number);
              }
              else if (year != initDate)
              {
                message = WARN_ATTR_DUPLICATE_YEAR_ASSERTION_FORMAT.get(value, year);
              }
              else
              {
                year = number;
              }
              break;
            default:
              message = WARN_ATTR_INVALID_PARTIAL_TIME_ASSERTION_FORMAT.get(value, (char) b);
          }
          if(message !=null)
          {
            logger.error(message);
            throw DecodeException.error(message);
          }
          number = 0;
        }
      }

      month = toCalendarMonth(month, value);

      //Validate year, month , date , hour, minute and second in that order.
      // -1 values are allowed when these values have not been provided
      if (year < 0)
      {
        //A future date is allowed.
        logAndThrow(WARN_ATTR_INVALID_YEAR_ASSERTION_FORMAT.get(value, year));
      }
      if (isDateInvalid(date, month, year))
      {
        logAndThrow(WARN_ATTR_INVALID_DATE_ASSERTION_FORMAT.get(value, date));
      }
      if (hour < initVal || hour > 23)
      {
        logAndThrow(WARN_ATTR_INVALID_HOUR_ASSERTION_FORMAT.get(value, hour));
      }
      if (minute < initVal || minute > 59)
      {
        logAndThrow(WARN_ATTR_INVALID_MINUTE_ASSERTION_FORMAT.get(value, minute));
      }
      if (second < initVal || second > 60) // Consider leap seconds.
      {
        logAndThrow(WARN_ATTR_INVALID_SECOND_ASSERTION_FORMAT.get(value, second));
      }

      // Since we reached here we have a valid assertion value.
      // Construct a normalized value in the order: SECOND MINUTE HOUR  DATE MONTH YEAR.
      ByteBuffer bb = ByteBuffer.allocate(6*4);
      bb.putInt(second);
      bb.putInt(minute);
      bb.putInt(hour);
      bb.putInt(date);
      bb.putInt(month);
      bb.putInt(year);
      return ByteString.wrap(bb.array());
    }

    private void logAndThrow(LocalizableMessage message) throws DecodeException
    {
      logger.warn(message);
      throw DecodeException.error(message);
    }

    private boolean isDateInvalid(int date, int month, int year)
    {
      switch (date)
      {
      case 29:
        return month == Calendar.FEBRUARY && !isLeapYear(year);
      case 30:
        return month == Calendar.FEBRUARY;
      case 31:
        return month != -1 && month != Calendar.JANUARY
            && month != Calendar.MARCH && month != Calendar.MAY
            && month != Calendar.JULY && month != Calendar.AUGUST
            && month != Calendar.OCTOBER && month != Calendar.DECEMBER;
      default:
        return date < 0 || date > 31;
      }
    }

    private boolean isLeapYear(int year)
    {
      if (year % 400 == 0)
      {
        return true;
      }
      if (year % 100 == 0)
      {
        return false;
      }
      return year % 4 == 0;
    }

    private int toCalendarMonth(int month, ByteSequence value) throws DecodeException
    {
      switch (month)
      {
      case -1:
        // just allow this.
        return -1;
      case 1:
        return Calendar.JANUARY;
      case 2:
        return Calendar.FEBRUARY;
      case 3:
        return Calendar.MARCH;
      case 4:
        return Calendar.APRIL;
      case 5:
        return Calendar.MAY;
      case 6:
        return Calendar.JUNE;
      case 7:
        return Calendar.JULY;
      case 8:
        return Calendar.AUGUST;
      case 9:
        return Calendar.SEPTEMBER;
      case 10:
        return Calendar.OCTOBER;
      case 11:
        return Calendar.NOVEMBER;
      case 12:
        return Calendar.DECEMBER;
      default:
        LocalizableMessage message = WARN_ATTR_INVALID_MONTH_ASSERTION_FORMAT.get(value, month);
        logger.warn(message);
        throw DecodeException.error(message);
      }
    }

    /** {@inheritDoc} */
    @Override
    public ConditionResult valuesMatch(ByteSequence attributeValue,
        ByteSequence assertionValue)
    {
      // Build the information from the attribute value.
      GregorianCalendar cal = new GregorianCalendar(TIME_ZONE_UTC_OBJ);
      cal.setLenient(false);
      cal.setTimeInMillis(((ByteString) attributeValue).toLong());
      int second = cal.get(Calendar.SECOND);
      int minute = cal.get(Calendar.MINUTE);
      int hour = cal.get(Calendar.HOUR_OF_DAY);
      int date = cal.get(Calendar.DATE);
      int month = cal.get(Calendar.MONTH);
      int year = cal.get(Calendar.YEAR);

      //Build the information from the assertion value.
      ByteBuffer bb = ByteBuffer.wrap(assertionValue.toByteArray());
      int assertSecond = bb.getInt(0);
      int assertMinute = bb.getInt(4);
      int assertHour = bb.getInt(8);
      int assertDate = bb.getInt(12);
      int assertMonth = bb.getInt(16);
      int assertYear = bb.getInt(20);

      // All the non-zero and non -1 values should match.
      if ((assertSecond != -1 && assertSecond != second)
          || (assertMinute != -1 && assertMinute != minute)
          || (assertHour != -1 && assertHour != hour)
          || (assertDate != 0 && assertDate != date)
          || (assertMonth != -1 && assertMonth != month)
          || (assertYear != 0 && assertYear != year))
      {
        return ConditionResult.FALSE;
      }
     return ConditionResult.TRUE;
    }



    /** {@inheritDoc} */
    @Override
    public Collection<ExtensibleIndexer> getIndexers()
    {
      if(indexer == null)
      {
        indexer = new PartialDateAndTimeExtensibleIndexer(this);
      }
      return Collections.singletonList(indexer);
    }



    /** {@inheritDoc} */
    @Override
    public <T> T createIndexQuery(ByteSequence assertionValue,
            IndexQueryFactory<T> factory) throws DecodeException
    {
      //Build the information from the assertion value.
      byte[] arr = normalizeAssertionValue(assertionValue).toByteArray();
      ByteBuffer bb = ByteBuffer.wrap(arr);

      int assertSecond = bb.getInt(0);
      int assertMinute = bb.getInt(4);
      int assertHour = bb.getInt(8);
      int assertDate = bb.getInt(12);
      int assertMonth = bb.getInt(16);
      int assertYear = bb.getInt(20);

      List<T> queries = new ArrayList<T>();
      if(assertSecond >= 0)
      {
        queries.add(createExactMatchQuery(factory, assertSecond, SECOND));
      }
      if(assertMinute >=0)
      {
        queries.add(createExactMatchQuery(factory, assertMinute, MINUTE));
      }
      if(assertHour >=0)
      {
        queries.add(createExactMatchQuery(factory, assertHour, HOUR));
      }
      if(assertDate >0)
      {
        queries.add(createExactMatchQuery(factory, assertDate, DATE));
      }
      if(assertMonth >=0)
      {
        queries.add(createExactMatchQuery(factory, assertMonth, MONTH));
      }
      if(assertYear > 0)
      {
        queries.add(createExactMatchQuery(factory, assertYear, YEAR));
      }
      return factory.createIntersectionQuery(queries);
    }

    private <T> T createExactMatchQuery(IndexQueryFactory<T> factory,
        int assertionValue, char type)
    {
      return factory.createExactMatchQuery(
          indexer.getExtensibleIndexID(), getKey(assertionValue, type));
    }


    /**
     * Decomposes an attribute value into a set of partial date and time index
     * keys.
     *
     * @param attValue
     *          The normalized attribute value
     * @param set
     *          A set into which the keys will be inserted.
     */
    private void timeKeys(ByteSequence attributeValue, Collection<ByteString> keys)
    {
      long timeInMS = 0L;
      try
      {
        timeInMS = decodeGeneralizedTimeValue(attributeValue);
      }
      catch(DirectoryException de)
      {
        //If the schema check is on this should never reach here. If not then we
        //would return from here.
        return;
      }
      //Build the information from the attribute value.
      GregorianCalendar cal = new GregorianCalendar(TIME_ZONE_UTC_OBJ);
      cal.setTimeInMillis(timeInMS);
      addKeyIfNotZero(keys, cal, Calendar.SECOND, SECOND);
      addKeyIfNotZero(keys, cal, Calendar.MINUTE, MINUTE);
      addKeyIfNotZero(keys, cal, Calendar.HOUR_OF_DAY, HOUR);
      addKeyIfNotZero(keys, cal, Calendar.DATE, DATE);
      addKeyIfNotZero(keys, cal, Calendar.MONTH, MONTH);
      addKeyIfNotZero(keys, cal, Calendar.YEAR, YEAR);
    }

    private void addKeyIfNotZero(Collection<ByteString> keys,
        GregorianCalendar cal, int calField, char type)
    {
      int value = cal.get(calField);
      if (value >= 0)
      {
        keys.add(getKey(value, type));
      }
    }

    private ByteString getKey(int value, char type)
    {
      ByteStringBuilder builder = new ByteStringBuilder();
      builder.append(type);
      builder.append(value);
      return builder.toByteString();
    }
  }

  private int multiplyByTenThenAddUnits(int number, byte b)
  {
    switch (b)
    {
    case '0':
      return number * 10;
    case '1':
      return number * 10 + 1;
    case '2':
      return number * 10 + 2;
    case '3':
      return number * 10 + 3;
    case '4':
      return number * 10 + 4;
    case '5':
      return number * 10 + 5;
    case '6':
      return number * 10 + 6;
    case '7':
      return number * 10 + 7;
    case '8':
      return number * 10 + 8;
    case '9':
      return number * 10 + 9;
    }
    return number;
  }


   /**
   * Extensible Indexer class for Partial Date and Time Matching rules.
   */
  private final class PartialDateAndTimeExtensibleIndexer extends
      ExtensibleIndexer
  {
    /** The partial date and Time matching Rule. */
    private final PartialDateAndTimeMatchingRule matchingRule;



    /**
     * Creates a new instance of PartialDateAndTimeExtensibleIndexer.
     *
     * @param matchingRule
     *          The PartialDateAndTime Rule.
     */
    private PartialDateAndTimeExtensibleIndexer(
        PartialDateAndTimeMatchingRule matchingRule)
    {
      this.matchingRule = matchingRule;
    }



    /** {@inheritDoc} */
    @Override
    public void createKeys(Schema schema, ByteSequence value,
        IndexingOptions options, Collection<ByteString> keys)
    {
      matchingRule.timeKeys(value, keys);
    }

    /** {@inheritDoc} */
    @Override
    public String getIndexID()
    {
      return PARTIAL_DATE_TIME_INDEX_NAME + "." + getExtensibleIndexID();
    }



    /** {@inheritDoc} */
    @Override
    public String getExtensibleIndexID()
    {
      return EXTENSIBLE_INDEXER_ID_DEFAULT;
    }
  }
}
