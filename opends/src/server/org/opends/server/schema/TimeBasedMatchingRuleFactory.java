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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011 ForgeRock AS
 */


package org.opends.server.schema;

import org.opends.server.util.StaticUtils;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.opends.messages.Message;
import org.opends.server.api.ExtensibleIndexer;
import org.opends.server.api.IndexQueryFactory;
import org.opends.server.api.MatchingRule;
import org.opends.server.api.MatchingRuleFactory;
import org.opends.server.admin.std.server.MatchingRuleCfg;
import org.opends.server.api.AbstractMatchingRule;
import org.opends.server.api.ExtensibleMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteSequence;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringBuilder;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.IndexConfig;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.util.TimeThread.*;

import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.schema.GeneralizedTimeSyntax.*;



/**
 * This class acts as a factory for time-based matching rules.
 */
public final class TimeBasedMatchingRuleFactory
        extends MatchingRuleFactory<MatchingRuleCfg>
{
  //Greater-than RelativeTimeMatchingRule.
  private MatchingRule greaterThanRTMRule;


  //Less-than RelativeTimeMatchingRule.
  private MatchingRule lessThanRTMRule;


  //PartialDayAndTimeMatchingRule.
  private MatchingRule partialDTMatchingRule;


  //A Collection of matching rules managed by this factory.
  private Set<MatchingRule> matchingRules;


  private static final TimeZone TIME_ZONE_UTC_OBJ =
      TimeZone.getTimeZone(TIME_ZONE_UTC);


  //Constants for generating keys.
  private static final char SECOND = 's';


  private static final char MINUTE = 'm';


  private static final char HOUR = 'h';


  private static final char MONTH = 'M';


  private static final char DATE = 'D';


  private static final char YEAR = 'Y';


  /**
   * {@inheritDoc}
   */
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



  /**
   * {@inheritDoc}
   */
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
    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription()
    {
      //There is no standard definition.
      return null;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String getSyntaxOID()
    {
       return SYNTAX_GENERALIZED_TIME_OID;
    }



    /**
      * {@inheritDoc}
      */
    @Override
    public ByteString normalizeValue(ByteSequence value)
            throws DirectoryException
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
            throw de;

          case WARN:
            logError(de.getMessageObject());
            return value.toByteString();

          default:
            return value.toByteString();
        }
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


     /**
      * {@inheritDoc}
      */
    @Override
    public ByteString normalizeAssertionValue(ByteSequence value)
            throws DirectoryException
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
      long number = 0;

      for(; index<value.length(); index++)
      {
        byte b = value.byteAt(index);
        if(isDigit((char)b))
        {
          switch (value.byteAt(index))
          {
            case '0':
              number = (number * 10);
              break;

            case '1':
              number = (number * 10) + 1;
              break;

            case '2':
              number = (number * 10) + 2;
              break;

            case '3':
              number = (number * 10) + 3;
              break;

            case '4':
              number = (number * 10) + 4;
              break;

            case '5':
              number = (number * 10) + 5;
              break;

            case '6':
              number = (number * 10) + 6;
              break;

            case '7':
              number = (number * 10) + 7;
              break;

            case '8':
              number = (number * 10) + 8;
              break;

            case '9':
              number = (number * 10) + 9;
              break;
          }
        }
        else
        {
          Message message = null;
          if(containsTimeUnit)
          {
            //We already have time unit found by now.
            message = WARN_ATTR_CONFLICTING_ASSERTION_FORMAT.
                       get(value.toString());
          }
          else
          {
            switch(value.byteAt(index))
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
                  message =
                          WARN_ATTR_INVALID_RELATIVE_TIME_ASSERTION_FORMAT.
                          get(value.toString(),(char)value.byteAt(index));
            }
          }
          if(message !=null)
          {
            //Log the message and throw an exception.
            logError(message);
            throw new DirectoryException(
                    ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
          }
          else
          {
            containsTimeUnit = true;
            number = 0;
          }
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
      return signed?ByteString.valueOf(now-delta):
                            ByteString.valueOf(now+delta);
    }



    /**
     * {@inheritDoc}
     */
    public int compareValues(ByteSequence value1, ByteSequence value2)
    {
      return value1.compareTo(value2);
    }



    /**
      * {@inheritDoc}
      */
    public int compare(byte[] arg0, byte[] arg1)
    {
      return StaticUtils.compare(arg0, arg1);
    }



    /**
    * {@inheritDoc}
    */
    public Collection<ExtensibleIndexer> getIndexers(IndexConfig config)
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
    //All the names for this matching rule.
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


     /**
      * {@inheritDoc}
      */
    @Override
    public String getName()
    {
      return EXT_OMR_RELATIVE_TIME_GT_NAME;
    }



    /**
      * {@inheritDoc}
      */
    @Override
    public Collection<String> getAllNames()
    {
      return Collections.unmodifiableList(names);
    }



    /**
      * {@inheritDoc}
      */
    @Override
    public String getOID()
    {
      return EXT_OMR_RELATIVE_TIME_GT_OID;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public ConditionResult valuesMatch(ByteSequence attributeValue,
        ByteSequence assertionValue)
    {
      int ret = compareValues(attributeValue, assertionValue);

      if (ret > 0)
      {
        return ConditionResult.TRUE;
      }
      else
      {
        return ConditionResult.FALSE;
      }
    }



    /**
    * {@inheritDoc}
    */
    public <T> T createIndexQuery(ByteSequence assertionValue,
        IndexQueryFactory<T> factory) throws DirectoryException
    {
      return factory.createRangeMatchQuery(indexer
          .getExtensibleIndexID(), normalizeAssertionValue(assertionValue),
          ByteString.empty(), false, false);
    }
  }



  /**
  * This class defines a matching rule which calculates the "less-than"
  * relative time for time-based searches.
  */
  private final class RelativeTimeLTOrderingMatchingRule
          extends RelativeTimeOrderingMatchingRule
  {
    //All the names for this matching rule.
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



     /**
      * {@inheritDoc}
      */
    @Override
    public String getName()
    {
      return EXT_OMR_RELATIVE_TIME_LT_NAME;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<String> getAllNames()
    {
      return Collections.unmodifiableList(names);
    }



    /**
      * {@inheritDoc}
      */
    @Override
    public String getOID()
    {
      return EXT_OMR_RELATIVE_TIME_LT_OID;
    }



     /**
     * {@inheritDoc}
     */
    @Override
    public ConditionResult valuesMatch(ByteSequence attributeValue,
        ByteSequence assertionValue)
    {
      int ret = compareValues(attributeValue, assertionValue);

      if (ret < 0)
      {
        return ConditionResult.TRUE;
      }
      else
      {
        return ConditionResult.FALSE;
      }
    }


    /**
    * {@inheritDoc}
    */
    public <T> T createIndexQuery(ByteSequence assertionValue,
        IndexQueryFactory<T> factory) throws DirectoryException
    {
      return factory.createRangeMatchQuery(indexer
          .getExtensibleIndexID(), ByteString.empty(),
          normalizeAssertionValue(assertionValue),false, false);
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



    /**
     * {@inheritDoc}
     */
    @Override
    public String getExtensibleIndexID()
    {
      return EXTENSIBLE_INDEXER_ID_DEFAULT;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public final void getKeys(AttributeValue value, Set<byte[]> keys)
    {
      ByteString key;
      try
      {
        key = matchingRule.normalizeValue(value.getValue());
        keys.add(key.toByteArray());
      }
      catch (DirectoryException de)
      {
        //don't do anything.
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public final void getKeys(AttributeValue value,
        Map<byte[], Boolean> modifiedKeys, Boolean insert)
    {
      Set<byte[]> keys = new HashSet<byte[]>();
      getKeys(value, keys);
      for (byte[] key : keys)
      {
        Boolean cInsert = modifiedKeys.get(key);
        if (cInsert == null)
        {
          modifiedKeys.put(key, insert);
        }
        else if (!cInsert.equals(insert))
        {
          modifiedKeys.remove(key);
        }
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String getPreferredIndexName()
    {
      return RELATIVE_TIME_INDEX_NAME;
    }
  }



  /**
   * This class performs the partial date and time matching capabilities.
   */
  private final class PartialDateAndTimeMatchingRule
          extends TimeBasedMatchingRule
          implements ExtensibleMatchingRule
  {
     /**
      * Indexer associated with this instance.
      */
     private ExtensibleIndexer indexer;



    /**
     * {@inheritDoc}
     */
    @Override
    public String getName()
    {
      return EXT_PARTIAL_DATE_TIME_NAME;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String getOID()
    {
      return EXT_PARTIAL_DATE_TIME_OID;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<String> getAllNames()
    {
      return Collections.singleton(getName());
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public ByteString normalizeAssertionValue(ByteSequence value)
        throws DirectoryException
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
      int second = -1;
      int minute = -1;
      int hour = -1;
      int date = 0;
      int year = 0;
      int number = 0;
      int month = -1;

      int length = value.length();
      for(int index=0; index<length; index++)
      {
        byte b = value.byteAt(index);
        if(isDigit((char)b))
        {
          switch (value.byteAt(index))
          {
            case '0':
              number = (number * 10);
              break;

            case '1':
              number = (number * 10) + 1;
              break;

            case '2':
              number = (number * 10) + 2;
              break;

            case '3':
              number = (number * 10) + 3;
              break;

            case '4':
              number = (number * 10) + 4;
              break;

            case '5':
              number = (number * 10) + 5;
              break;

            case '6':
              number = (number * 10) + 6;
              break;

            case '7':
              number = (number * 10) + 7;
              break;

            case '8':
              number = (number * 10) + 8;
              break;

            case '9':
              number = (number * 10) + 9;
              break;
          }
        }
        else
        {
          Message message = null;
          switch(value.byteAt(index))
          {
            case 's':
              if(second >0)
              {
                 message =
                        WARN_ATTR_DUPLICATE_SECOND_ASSERTION_FORMAT.get(
                        value.toString(),date);
              }
              else
              {
                second = number;
              }
              break;
            case 'm':
              if(minute >0)
              {
                 message =
                        WARN_ATTR_DUPLICATE_MINUTE_ASSERTION_FORMAT.get(
                        value.toString(),date);
              }
              else
              {
                minute = number;
              }
              break;
            case 'h':
              if(hour >0)
              {
                 message =
                        WARN_ATTR_DUPLICATE_HOUR_ASSERTION_FORMAT.get(
                        value.toString(),date);
              }
              else
              {
                hour = number;
              }
              break;
            case 'D':
              if(number == 0)
              {
                message =
                        WARN_ATTR_INVALID_DATE_ASSERTION_FORMAT.get(
                        value.toString(), number);
              }
              else if(date > 0)
              {
                message =
                        WARN_ATTR_DUPLICATE_DATE_ASSERTION_FORMAT.get(
                        value.toString(),date);
              }
              else
              {
                date = number;
              }
              break;
            case 'M':
               if(number == 0)
              {
                message =
                        WARN_ATTR_INVALID_MONTH_ASSERTION_FORMAT.
                        get(value.toString(),number);
              }
              else if(month > 0)
              {
                message =
                        WARN_ATTR_DUPLICATE_MONTH_ASSERTION_FORMAT.get(
                        value.toString(),month);
              }
              else
              {
                month = number;
              }
              break;
            case 'Y':
              if(number == 0)
              {
                message =
                        WARN_ATTR_INVALID_YEAR_ASSERTION_FORMAT.
                        get(value.toString(),number);
              }
              else if(year >0)
              {
                message = WARN_ATTR_DUPLICATE_YEAR_ASSERTION_FORMAT.
                        get(value.toString(),year);
              }
              else
              {
                year = number;
              }
              break;
            default:
                message =
                        WARN_ATTR_INVALID_PARTIAL_TIME_ASSERTION_FORMAT.
                        get(value.toString(),(char)value.byteAt(index));
          }
          if(message !=null)
          {
            logError(message);
            throw new DirectoryException(
                    ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
          }
          else
          {
            number = 0;
          }
        }
      }

      //Validate year, month , date , hour, minute and second in that order.
      if(year < 0)
      {
        //A future date is allowed.
        Message message =
                WARN_ATTR_INVALID_YEAR_ASSERTION_FORMAT.
                get(value.toString(),year);
        logError(message);
        throw new DirectoryException(
                ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
      }

      switch(month)
      {
        case -1:
          //just allow this.
          break;
        case 1:
          month = Calendar.JANUARY;
          break;
        case 2:
          month = Calendar.FEBRUARY;
          break;
        case 3:
          month = Calendar.MARCH;
          break;
        case 4:
          month = Calendar.APRIL;
          break;
        case 5:
          month = Calendar.MAY;
          break;
        case 6:
          month = Calendar.JUNE;
          break;
        case 7:
          month = Calendar.JULY;
          break;
        case 8:
          month = Calendar.AUGUST;
          break;
        case 9:
          month = Calendar.SEPTEMBER;
          break;
        case 10:
          month = Calendar.OCTOBER;
          break;
        case 11:
          month = Calendar.NOVEMBER;
          break;
        case 12:
          month = Calendar.DECEMBER;
          break;
        default:
          Message message =
                WARN_ATTR_INVALID_MONTH_ASSERTION_FORMAT.
                get(value.toString(),month);
          logError(message);
           throw new DirectoryException(
                   ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
      }

      boolean invalidDate = false;
      switch(date)
      {
        case 29:
          if(month == Calendar.FEBRUARY && year%4 !=0)
          {
            invalidDate = true;
          }
          break;
        case 31:
          if(month != -1 && month != Calendar.JANUARY && month!= Calendar.MARCH
                  && month != Calendar.MAY && month != Calendar.JULY
                  && month != Calendar.AUGUST && month != Calendar.OCTOBER
                  && month != Calendar.DECEMBER)
          {
            invalidDate = true;
          }
          break;
        default:
          if(!(date >=0 && date <=31))
          {
            invalidDate = true;
          }
      }
      if(invalidDate)
      {
        Message message =
                WARN_ATTR_INVALID_DATE_ASSERTION_FORMAT.
                get(value.toString(),date);
        logError(message);
        throw new DirectoryException(
                ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
      }

      if(!(hour >=-1 && hour <=23))
      {
         Message message =
                WARN_ATTR_INVALID_HOUR_ASSERTION_FORMAT.
                get(value.toString(),date);
        logError(message);
        throw new DirectoryException(
                ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
      }

      if(!(minute >=-1 && minute <=59))
      {
           Message message =
                WARN_ATTR_INVALID_MINUTE_ASSERTION_FORMAT.
                get(value.toString(),date);
        logError(message);
        throw new DirectoryException(
                ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
      }

      if(!(second >=-1 && second <=60)) //Consider leap seconds.
      {
         Message message =
                WARN_ATTR_INVALID_SECOND_ASSERTION_FORMAT.
                get(value.toString(),date);
        logError(message);
        throw new DirectoryException(
                ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
      }

      /**
       * Since we reached here we have a valid assertion value. Construct
       * a normalized value in the order: SECOND MINUTE HOUR  DATE MONTH YEAR.
       */
      ByteBuffer bb = ByteBuffer.allocate(6*4);
      bb.putInt(second);
      bb.putInt(minute);
      bb.putInt(hour);
      bb.putInt(date);
      bb.putInt(month);
      bb.putInt(year);
      return ByteString.wrap(bb.array());
    }



     /**
     * {@inheritDoc}
     */
    @Override
    public ConditionResult valuesMatch(ByteSequence attributeValue,
        ByteSequence assertionValue)
    {
      long timeInMS = ((ByteString)attributeValue).toLong();
      //Build the information from the attribute value.
      GregorianCalendar cal = new GregorianCalendar(TIME_ZONE_UTC_OBJ);
      cal.setLenient(false);
      cal.setTimeInMillis(timeInMS);
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

      if(assertSecond != -1 && assertSecond !=second)
      {
        return ConditionResult.FALSE;
      }

      if(assertMinute !=-1 && assertMinute !=minute)
      {
        return ConditionResult.FALSE;
      }

      if(assertHour !=-1 && assertHour !=hour)
      {
        return ConditionResult.FALSE;
      }

      //All the non-zero values should match.
      if(assertDate !=0 && assertDate != date)
      {
        return ConditionResult.FALSE;
      }

      if(assertMonth !=-1 && assertMonth != month)
      {
        return ConditionResult.FALSE;
      }

      if(assertYear !=0 && assertYear != year)
      {
        return ConditionResult.FALSE;
      }

     return ConditionResult.TRUE;
    }



    /**
      * {@inheritDoc}
      */
    public Collection<ExtensibleIndexer> getIndexers(IndexConfig config)
    {
      if(indexer == null)
      {
        indexer = new PartialDateAndTimeExtensibleIndexer(this);
      }
      return Collections.singletonList(indexer);
    }



    /**
      * {@inheritDoc}
      */
    public <T> T createIndexQuery(ByteSequence assertionValue,
            IndexQueryFactory<T> factory) throws DirectoryException
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
        queries.add(factory.createExactMatchQuery(
                indexer.getExtensibleIndexID(),
               getKey(assertSecond,SECOND)));
      }

      if(assertMinute >=0)
      {
         queries.add(factory.createExactMatchQuery(
                indexer.getExtensibleIndexID(),
               getKey(assertMinute,MINUTE)));
      }

      if(assertHour >=0)
      {
         queries.add(factory.createExactMatchQuery(
                indexer.getExtensibleIndexID(),
               getKey(assertHour,HOUR)));
      }

      if(assertDate >0)
      {
        queries.add(factory.createExactMatchQuery(
                indexer.getExtensibleIndexID(),
                getKey(assertDate,DATE)));
      }

      if(assertMonth >=0)
      {
        queries.add(factory.createExactMatchQuery(
                indexer.getExtensibleIndexID(),
                getKey(assertMonth,MONTH)));
      }

      if(assertYear > 0)
      {
        queries.add(factory.createExactMatchQuery(
                indexer.getExtensibleIndexID(),
                getKey(assertYear,YEAR)));
      }
      return factory.createIntersectionQuery(queries);
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
    private void timeKeys(ByteString attributeValue, Set<byte[]> keys)
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
      int second = cal.get(Calendar.SECOND);
      int minute = cal.get(Calendar.MINUTE);
      int hour = cal.get(Calendar.HOUR_OF_DAY);
      int date = cal.get(Calendar.DATE);
      int month = cal.get(Calendar.MONTH);
      int year = cal.get(Calendar.YEAR);

      if (second >=0)
      {
        keys.add(getKey(second,SECOND).toByteArray());
      }

      if(minute >=0)
      {
        keys.add(getKey(minute,MINUTE).toByteArray());
      }

      if(hour >=0)
      {
        keys.add(getKey(hour,HOUR).toByteArray());
      }
      //Insert date.
      if(date > 0)
      {
        keys.add(getKey(date,DATE).toByteArray());
      }

      //Insert month.
      if(month >=0)
      {
        keys.add(getKey(month,MONTH).toByteArray());
      }

      if(year > 0)
      {
        keys.add(getKey(year,YEAR).toByteArray());
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



   /**
   * Extensible Indexer class for Partial Date and Time Matching rules.
   */
  private final class PartialDateAndTimeExtensibleIndexer extends
      ExtensibleIndexer
  {
    // The partial date and Time matching Rule.
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



    /**
     * {@inheritDoc}
     */
    @Override
    public void getKeys(AttributeValue value, Set<byte[]> keys)
    {
      matchingRule.timeKeys(value.getValue(), keys);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void getKeys(AttributeValue attValue,
        Map<byte[], Boolean> modifiedKeys, Boolean insert)
    {
      Set<byte[]> keys = new HashSet<byte[]>();
      getKeys(attValue, keys);
      for (byte[] key : keys)
      {
        Boolean cInsert = modifiedKeys.get(key);
        if (cInsert == null)
        {
          modifiedKeys.put(key, insert);
        }
        else if (!cInsert.equals(insert))
        {
          modifiedKeys.remove(key);
        }
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String getPreferredIndexName()
    {
      return PARTIAL_DATE_TIME_INDEX_NAME;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String getExtensibleIndexID()
    {
      return EXTENSIBLE_INDEXER_ID_DEFAULT;
    }
  }
}
