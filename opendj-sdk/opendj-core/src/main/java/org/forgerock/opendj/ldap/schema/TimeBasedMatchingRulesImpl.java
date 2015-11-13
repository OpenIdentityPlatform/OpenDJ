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
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap.schema;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.GeneralizedTime;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;
import org.forgerock.opendj.ldap.spi.Indexer;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.forgerock.util.time.TimeService;

import static com.forgerock.opendj.ldap.CoreMessages.*;
import static com.forgerock.opendj.util.StaticUtils.*;
import static org.forgerock.opendj.ldap.DecodeException.*;
import static org.forgerock.opendj.ldap.schema.GeneralizedTimeEqualityMatchingRuleImpl.createNormalizedAttributeValue;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

/** Implementations of time-based matching rules. */
final class TimeBasedMatchingRulesImpl {

    private static final TimeZone TIME_ZONE_UTC = TimeZone.getTimeZone("UTC");

    /** Constants for generating keys. */
    private static final char SECOND = 's';
    private static final char MINUTE = 'm';
    private static final char HOUR = 'h';
    private static final char MONTH = 'M';
    private static final char DAY = 'D';
    private static final char YEAR = 'Y';

    private TimeBasedMatchingRulesImpl() {
        // not instantiable
    }

    /**
     * Creates a relative time greater than matching rule.
     *
     * @return the matching rule implementation
     */
    static MatchingRuleImpl relativeTimeGTOMatchingRule() {
        return new RelativeTimeGreaterThanOrderingMatchingRuleImpl();
    }

    /**
     * Creates a relative time less than matching rule.
     *
     * @return the matching rule implementation
     */
    static MatchingRuleImpl relativeTimeLTOMatchingRule() {
        return new RelativeTimeLessThanOrderingMatchingRuleImpl();
    }

    /**
     * Creates a partial date and time matching rule.
     *
     * @return the matching rule implementation
     */
    static MatchingRuleImpl partialDateAndTimeMatchingRule() {
        return new PartialDateAndTimeMatchingRuleImpl();
    }

    /** This class defines a matching rule which is used for time-based searches. */
    private static abstract class TimeBasedMatchingRuleImpl extends AbstractMatchingRuleImpl {

        /** Unit tests can inject fake timestamps if necessary. */
        final TimeService timeService = TimeService.SYSTEM;

        @Override
        public final ByteString normalizeAttributeValue(Schema schema, ByteSequence value) throws DecodeException {
            return GeneralizedTimeEqualityMatchingRuleImpl.normalizeAttributeValue(value);
        }

        /** Utility method to convert the provided integer and the provided byte representing a digit to an integer. */
        int multiplyByTenAndAddUnits(int number, byte digitByte) {
            return number * 10 + (digitByte - '0');
        }
    }

    /** Defines the relative time ordering matching rule. */
    private static abstract class RelativeTimeOrderingMatchingRuleImpl extends TimeBasedMatchingRuleImpl {

        final Indexer indexer = new DefaultIndexer(EMR_GENERALIZED_TIME_NAME);

        @Override
        public Collection<? extends Indexer> createIndexers(IndexingOptions options) {
            return Collections.singletonList(indexer);
        }

        /**
         * Normalize the provided assertion value.
         * <p>
         * An assertion value may contain one of the following:
         * <pre>
         * s = second
         * m = minute
         * h = hour
         * d = day
         * w = week
         * </pre>
         *
         * An example assertion is
         * <pre>
         *   OID:=(-)1d
         * </pre>
         *
         * where a '-' means that the user intends to search only the expired
         * events. In this example we are searching for an event expired 1 day
         * back.
         * <p>
         * This method takes the assertion value adds/substracts it to/from the
         * current time and calculates a time which will be used as a relative
         * time by inherited rules.
         */
        ByteString normalizeAssertionValue(ByteSequence assertionValue) throws DecodeException {
            int index = 0;
            boolean signed = false;
            byte firstByte = assertionValue.byteAt(0);

            if (firstByte == '-') {
                // Turn the sign on to go back in past.
                signed = true;
                index = 1;
            } else if (firstByte == '+') {
                // '+" is not required but we won't reject it either.
                index = 1;
            }

            long second = 0;
            long minute = 0;
            long hour = 0;
            long day = 0;
            long week = 0;

            boolean containsTimeUnit = false;
            int number = 0;

            for (; index < assertionValue.length(); index++) {
                byte b = assertionValue.byteAt(index);
                if (isDigit((char) b)) {
                    number = multiplyByTenAndAddUnits(number, b);
                } else {
                    if (containsTimeUnit) {
                        // We already have time unit found by now.
                        throw error(WARN_ATTR_CONFLICTING_ASSERTION_FORMAT.get(assertionValue));
                    }
                    switch (b) {
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
                        throw error(WARN_ATTR_INVALID_RELATIVE_TIME_ASSERTION_FORMAT.get(assertionValue, (char) b));
                    }
                    containsTimeUnit = true;
                    number = 0;
                }
            }

            if (!containsTimeUnit) {
                // There was no time unit so assume it is seconds.
                second = number;
            }

            long delta = (second + minute * 60 + hour * 3600 + day * 24 * 3600 + week * 7 * 24 * 3600) * 1000;
            long now = timeService.now();
            return createNormalizedAttributeValue(signed ? now - delta : now + delta);
        }

    }

    /** Defines the "greater-than" relative time matching rule. */
    private static final class RelativeTimeGreaterThanOrderingMatchingRuleImpl extends
        RelativeTimeOrderingMatchingRuleImpl {

        @Override
        public Assertion getAssertion(final Schema schema, final ByteSequence value) throws DecodeException {
            final ByteString assertionValue = normalizeAssertionValue(value);

            return new Assertion() {
                @Override
                public ConditionResult matches(ByteSequence attributeValue) {
                    return ConditionResult.valueOf(attributeValue.compareTo(assertionValue) > 0);
                }

                @Override
                public <T> T createIndexQuery(IndexQueryFactory<T> factory) throws DecodeException {
                    return factory.createRangeMatchQuery(indexer.getIndexID(), assertionValue, ByteString.empty(),
                        false, false);
                }
            };
        }
    }

    /** Defines the "less-than" relative time matching rule. */
    private static final class RelativeTimeLessThanOrderingMatchingRuleImpl extends
        RelativeTimeOrderingMatchingRuleImpl {

        @Override
        public Assertion getAssertion(final Schema schema, final ByteSequence value) throws DecodeException {
            final ByteString assertionValue = normalizeAssertionValue(value);

            return new Assertion() {
                @Override
                public ConditionResult matches(ByteSequence attributeValue) {
                    return ConditionResult.valueOf(attributeValue.compareTo(assertionValue) < 0);
                }

                @Override
                public <T> T createIndexQuery(IndexQueryFactory<T> factory) throws DecodeException {
                    return factory.createRangeMatchQuery(indexer.getIndexID(), ByteString.empty(), assertionValue,
                        false, false);
                }
            };
        }
    }

    /** Defines the partial date and time matching rule. */
    private static final class PartialDateAndTimeMatchingRuleImpl extends TimeBasedMatchingRuleImpl {

        private final Indexer indexer = new PartialDateAndTimeIndexer(this);

        @Override
        public Collection<? extends Indexer> createIndexers(IndexingOptions options) {
            return Collections.singletonList(indexer);
        }

        @Override
        public Assertion getAssertion(final Schema schema, final ByteSequence value) throws DecodeException {
            final ByteString assertionValue = normalizeAssertionValue(value);

            return new Assertion() {
                @Override
                public ConditionResult matches(ByteSequence attributeValue) {
                    return valuesMatch(attributeValue, assertionValue);
                }

                @Override
                public <T> T createIndexQuery(IndexQueryFactory<T> factory) throws DecodeException {
                    final ByteSequenceReader reader = assertionValue.asReader();
                    int assertSecond = reader.readByte();
                    int assertMinute = reader.readByte();
                    int assertHour = reader.readByte();
                    int assertDay = reader.readByte();
                    int assertMonth = reader.readByte();
                    int assertYear = reader.readCompactUnsignedInt();

                    List<T> queries = new ArrayList<>();
                    if (assertSecond >= 0) {
                        queries.add(createExactMatchByteQuery(factory, assertSecond, SECOND));
                    }
                    if (assertMinute >= 0) {
                        queries.add(createExactMatchByteQuery(factory, assertMinute, MINUTE));
                    }
                    if (assertHour >= 0) {
                        queries.add(createExactMatchByteQuery(factory, assertHour, HOUR));
                    }
                    if (assertDay > 0) {
                        queries.add(createExactMatchByteQuery(factory, assertDay, DAY));
                    }
                    if (assertMonth >= 0) {
                        queries.add(createExactMatchByteQuery(factory, assertMonth, MONTH));
                    }
                    if (assertYear > 0) {
                        queries.add(createExactMatchCompactUnsignedQuery(factory, assertYear, YEAR));
                    }
                    return factory.createIntersectionQuery(queries);
                }

                private <T> T createExactMatchByteQuery(
                        IndexQueryFactory<T> factory, int assertionValue, char type) {
                    return factory.createExactMatchQuery(
                            indexer.getIndexID(), byteKey(assertionValue, type));
                }

                private <T> T createExactMatchCompactUnsignedQuery(
                        IndexQueryFactory<T> factory, int assertionValue, char type) {
                    return factory.createExactMatchQuery(
                            indexer.getIndexID(), compactUnsignedKey(assertionValue, type));
                }
            };
        }

        /**
         * Normalize the provided assertion value.
         * <p>
         * An assertion value may contain one or all of the following:
         * <pre>
         * D = day
         * M = month
         * Y = year
         * h = hour
         * m = month
         * s = second
         * </pre>
         *
         * An example assertion is
         * <pre>
         *  OID:=04M
         * </pre>
         * In this example we are searching for entries corresponding to month
         * of april.
         * <p>
         * The normalized value is actually the format of : smhDMY.
         */
        private ByteString normalizeAssertionValue(ByteSequence assertionValue) throws DecodeException {
            final int initDay = 0;
            final int initValue = -1;
            int second = initValue;
            int minute = initValue;
            int hour = initValue;
            int day = initDay;
            int month = initValue;
            int year = initDay;
            int number = 0;

            int length = assertionValue.length();
            for (int index = 0; index < length; index++) {
                byte b = assertionValue.byteAt(index);
                if (isDigit((char) b)) {
                    number = multiplyByTenAndAddUnits(number, b);
                } else {
                    switch (b) {
                    case 's':
                        if (second != initValue) {
                            throw error(WARN_ATTR_DUPLICATE_SECOND_ASSERTION_FORMAT.get(assertionValue, day));
                        }
                        second = number;
                        break;
                    case 'm':
                        if (minute != initValue) {
                            throw error(WARN_ATTR_DUPLICATE_MINUTE_ASSERTION_FORMAT.get(assertionValue, day));
                        }
                        minute = number;
                        break;
                    case 'h':
                        if (hour != initValue) {
                            throw error(WARN_ATTR_DUPLICATE_HOUR_ASSERTION_FORMAT.get(assertionValue, day));
                        }
                        hour = number;
                        break;
                    case 'D':
                        if (number == 0) {
                            throw error(WARN_ATTR_INVALID_DAY_ASSERTION_FORMAT.get(assertionValue, number));
                        } else if (day != initDay) {
                            throw error(WARN_ATTR_DUPLICATE_DAY_ASSERTION_FORMAT.get(assertionValue, day));
                        }
                        day = number;
                        break;
                    case 'M':
                        if (number == 0) {
                            throw error(WARN_ATTR_INVALID_MONTH_ASSERTION_FORMAT.get(assertionValue, number));
                        } else if (month != initValue) {
                            throw error(WARN_ATTR_DUPLICATE_MONTH_ASSERTION_FORMAT.get(assertionValue, month));
                        }
                        month = number;
                        break;
                    case 'Y':
                        if (number == 0) {
                            throw error(WARN_ATTR_INVALID_YEAR_ASSERTION_FORMAT.get(assertionValue, number));
                        } else if (year != initDay) {
                            throw error(WARN_ATTR_DUPLICATE_YEAR_ASSERTION_FORMAT.get(assertionValue, year));
                        }
                        year = number;
                        break;
                    default:
                        throw error(WARN_ATTR_INVALID_PARTIAL_TIME_ASSERTION_FORMAT.get(assertionValue, (char) b));
                    }
                    number = 0;
                }
            }

            month = toCalendarMonth(month, assertionValue);

            // Validate year, month , day , hour, minute and second in that order.
            // -1 values are allowed when these values have not been provided
            if (year < 0) {
                // A future date is allowed.
                throw error(WARN_ATTR_INVALID_YEAR_ASSERTION_FORMAT.get(assertionValue, year));
            }
            if (isDayInvalid(day, month, year)) {
                throw error(WARN_ATTR_INVALID_DAY_ASSERTION_FORMAT.get(assertionValue, day));
            }
            if (hour < initValue || hour > 23) {
                throw error(WARN_ATTR_INVALID_HOUR_ASSERTION_FORMAT.get(assertionValue, hour));
            }
            if (minute < initValue || minute > 59) {
                throw error(WARN_ATTR_INVALID_MINUTE_ASSERTION_FORMAT.get(assertionValue, minute));
            }
            // Consider leap seconds.
            if (second < initValue || second > 60) {
                throw error(WARN_ATTR_INVALID_SECOND_ASSERTION_FORMAT.get(assertionValue, second));
            }

            // Since we reached here we have a valid assertion value.
            // Construct a normalized value in the order: SECOND MINUTE HOUR DAY MONTH YEAR.
            // Using compact unsigned for year will use only two bytes until the year 16384 :)
            return new ByteStringBuilder(5 + 2)
                .appendByte(second).appendByte(minute).appendByte(hour)
                .appendByte(day).appendByte(month).appendCompactUnsigned(year).toByteString();
        }

        private boolean isDayInvalid(int day, int month, int year) {
            switch (day) {
            case 29:
                return month == Calendar.FEBRUARY && !isLeapYear(year);
            case 30:
                return month == Calendar.FEBRUARY;
            case 31:
                return month != -1
                    && month != Calendar.JANUARY
                    && month != Calendar.MARCH
                    && month != Calendar.MAY
                    && month != Calendar.JULY
                    && month != Calendar.AUGUST
                    && month != Calendar.OCTOBER
                    && month != Calendar.DECEMBER;
            default:
                return day < 0 || day > 31;
            }
        }

        private boolean isLeapYear(int year) {
            return year % 400 == 0 || (year % 100 != 0 && year % 4 == 0);
        }

        private int toCalendarMonth(int month, ByteSequence value) throws DecodeException {
            if (month == -1) {
                // just allow this.
                return -1;
            } else if (1 <= month && month <= 12) {
                // java.util.Calendar months are numbered from 0
                return month - 1;
            }
            throw error(WARN_ATTR_INVALID_MONTH_ASSERTION_FORMAT.get(value, month));
        }

        private ConditionResult valuesMatch(ByteSequence attributeValue, ByteSequence assertionValue) {
            // Build the information from the attribute value.
            GregorianCalendar cal = new GregorianCalendar(TIME_ZONE_UTC);
            cal.setLenient(false);
            cal.setTimeInMillis(attributeValue.toByteString().toLong() + GeneralizedTime.MIN_GENERALIZED_TIME_MS);
            int second = cal.get(Calendar.SECOND);
            int minute = cal.get(Calendar.MINUTE);
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int day = cal.get(Calendar.DAY_OF_MONTH);
            int month = cal.get(Calendar.MONTH);
            int year = cal.get(Calendar.YEAR);

            // Build the information from the assertion value.
            ByteSequenceReader r = assertionValue.asReader();
            int assertSecond = r.readByte();
            int assertMinute = r.readByte();
            int assertHour = r.readByte();
            int assertDay = r.readByte();
            int assertMonth = r.readByte();
            int assertYear = r.readCompactUnsignedInt();

            // All the non-zero and non -1 values should match.
            return ConditionResult.valueOf(
                (assertSecond == -1 || assertSecond == second)
                    && (assertMinute == -1 || assertMinute == minute)
                    && (assertHour == -1 || assertHour == hour)
                    && (assertDay == 0 || assertDay == day)
                    && (assertMonth == -1 || assertMonth == month)
                    && (assertYear == 0 || assertYear == year));
        }

        /**
         * Decomposes an attribute value into a set of partial date and time
         * index keys.
         *
         * @param attributeValue
         *            The normalized attribute value
         * @param keys
         *            A set into which the keys will be inserted.
         */
        private void timeKeys(ByteSequence attributeValue, Collection<ByteString> keys) {
            long timeInMillis = 0L;
            try {
                timeInMillis = GeneralizedTime.valueOf(attributeValue.toString()).getTimeInMillis();
            } catch (IllegalArgumentException e) {
                return;
            }
            GregorianCalendar cal = new GregorianCalendar(TIME_ZONE_UTC);
            cal.setTimeInMillis(timeInMillis);
            addKeyIfNotZero(keys, cal, Calendar.SECOND, SECOND);
            addKeyIfNotZero(keys, cal, Calendar.MINUTE, MINUTE);
            addKeyIfNotZero(keys, cal, Calendar.HOUR_OF_DAY, HOUR);
            addKeyIfNotZero(keys, cal, Calendar.DAY_OF_MONTH, DAY);
            addKeyIfNotZero(keys, cal, Calendar.MONTH, MONTH);
            addKeyIfNotZero(keys, cal, Calendar.YEAR, YEAR);
        }

        private void addKeyIfNotZero(Collection<ByteString> keys, GregorianCalendar cal, int calField, char type) {
            int value = cal.get(calField);
            if (value >= 0) {
                switch (type) {
                case SECOND:
                case MINUTE:
                case HOUR:
                case DAY:
                case MONTH:
                    keys.add(byteKey(value, type));
                    break;

                case YEAR:
                    keys.add(compactUnsignedKey(value, type));
                    break;

                default:
                    break;
                }
            }
        }

        private ByteString byteKey(int value, char type) {
            return new ByteStringBuilder().appendInt(type).appendByte(value).toByteString();
        }

        private ByteString compactUnsignedKey(long value, char type) {
            return new ByteStringBuilder().appendInt(type).appendCompactUnsigned(value).toByteString();
        }
    }

    /** Indexer for Partial Date and Time Matching rules. */
    private static final class PartialDateAndTimeIndexer implements Indexer {

        private final PartialDateAndTimeMatchingRuleImpl matchingRule;

        private PartialDateAndTimeIndexer(PartialDateAndTimeMatchingRuleImpl matchingRule) {
            this.matchingRule = matchingRule;
        }

        @Override
        public void createKeys(Schema schema, ByteSequence value, Collection<ByteString> keys) {
            matchingRule.timeKeys(value, keys);
        }

        @Override
        public String keyToHumanReadableString(ByteSequence key) {
            return key.toByteString().toHexString();
        }

        @Override
        public String getIndexID() {
            return MR_PARTIAL_DATE_AND_TIME_NAME;
        }
    }
}
