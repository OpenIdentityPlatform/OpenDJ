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
 *      Copyright 2012-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg2;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.util.Reject;

import static com.forgerock.opendj.ldap.CoreMessages.*;

/**
 * An LDAP generalized time as defined in RFC 4517. This class facilitates
 * parsing of generalized time values to and from {@link Date} and
 * {@link Calendar} classes.
 * <p>
 * The following are examples of generalized time values:
 *
 * <pre>
 * 199412161032Z
 * 199412160532-0500
 * </pre>
 *
 * @see <a href="http://tools.ietf.org/html/rfc4517#section-3.3.13">RFC 4517 -
 *      Lightweight Directory Access Protocol (LDAP): Syntaxes and Matching
 *      Rules </a>
 */
public final class GeneralizedTime implements Comparable<GeneralizedTime> {

    /** UTC TimeZone is assumed to never change over JVM lifetime. */
    private static final TimeZone TIME_ZONE_UTC_OBJ = TimeZone.getTimeZone("UTC");

    /** The smallest time representable using the generalized time syntax. */
    public static final GeneralizedTime MIN_GENERALIZED_TIME = valueOf("00010101000000Z");

    /** The smallest time in milli-seconds representable using the generalized time syntax. */
    public static final long MIN_GENERALIZED_TIME_MS = MIN_GENERALIZED_TIME.getTimeInMillis();

    /**
     * Returns a generalized time whose value is the current time, using the
     * default time zone and locale.
     *
     * @return A generalized time whose value is the current time.
     */
    public static GeneralizedTime currentTime() {
        return valueOf(Calendar.getInstance());
    }

    /**
     * Returns a generalized time representing the provided {@code Calendar}.
     * <p>
     * The provided calendar will be defensively copied in order to preserve
     * immutability.
     *
     * @param calendar
     *            The calendar to be converted to a generalized time.
     * @return A generalized time representing the provided {@code Calendar}.
     */
    public static GeneralizedTime valueOf(final Calendar calendar) {
        Reject.ifNull(calendar);
        return new GeneralizedTime((Calendar) calendar.clone(), null, Long.MIN_VALUE, null);
    }

    /**
     * Returns a generalized time representing the provided {@code Date}.
     * <p>
     * The provided date will be defensively copied in order to preserve
     * immutability.
     *
     * @param date
     *            The date to be converted to a generalized time.
     * @return A generalized time representing the provided {@code Date}.
     */
    public static GeneralizedTime valueOf(final Date date) {
        Reject.ifNull(date);
        return new GeneralizedTime(null, (Date) date.clone(), Long.MIN_VALUE, null);
    }

    /**
     * Returns a generalized time representing the provided time in milliseconds
     * since the epoch.
     *
     * @param timeMS
     *            The time to be converted to a generalized time.
     * @return A generalized time representing the provided time in milliseconds
     *         since the epoch.
     */
    public static GeneralizedTime valueOf(final long timeMS) {
        Reject.ifTrue(timeMS < MIN_GENERALIZED_TIME_MS, "timeMS is too old to represent as a generalized time");
        return new GeneralizedTime(null, null, timeMS, null);
    }

    /**
     * Parses the provided string as an LDAP generalized time.
     *
     * @param time
     *            The generalized time value to be parsed.
     * @return The parsed generalized time.
     * @throws LocalizedIllegalArgumentException
     *             If {@code time} cannot be parsed as a valid generalized time
     *             string.
     * @throws NullPointerException
     *             If {@code time} was {@code null}.
     */
    public static GeneralizedTime valueOf(final String time) {
        int year = 0;
        int month = 0;
        int day = 0;
        int hour = 0;
        int minute = 0;
        int second = 0;

        // Get the value as a string and verify that it is at least long
        // enough for "YYYYMMDDhhZ", which is the shortest allowed value.
        final String valueString = time.toUpperCase();
        final int length = valueString.length();
        if (length < 11) {
            final LocalizableMessage message =
                    WARN_ATTR_SYNTAX_GENERALIZED_TIME_TOO_SHORT.get(valueString);
            throw new LocalizedIllegalArgumentException(message);
        }

        // The first four characters are the century and year, and they must
        // be numeric digits between 0 and 9.
        for (int i = 0; i < 4; i++) {
            char c = valueString.charAt(i);
            final int val = toInt(c,
                    WARN_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_YEAR, valueString, String.valueOf(c));
            year = (year * 10) + val;
        }

        // The next two characters are the month, and they must form the
        // string representation of an integer between 01 and 12.
        char m1 = valueString.charAt(4);
        final char m2 = valueString.charAt(5);
        final String monthValue = valueString.substring(4, 6);
        switch (m1) {
        case '0':
            // m2 must be a digit between 1 and 9.
            switch (m2) {
            case '1':
                month = Calendar.JANUARY;
                break;

            case '2':
                month = Calendar.FEBRUARY;
                break;

            case '3':
                month = Calendar.MARCH;
                break;

            case '4':
                month = Calendar.APRIL;
                break;

            case '5':
                month = Calendar.MAY;
                break;

            case '6':
                month = Calendar.JUNE;
                break;

            case '7':
                month = Calendar.JULY;
                break;

            case '8':
                month = Calendar.AUGUST;
                break;

            case '9':
                month = Calendar.SEPTEMBER;
                break;

            default:
                throw new LocalizedIllegalArgumentException(
                        WARN_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_MONTH.get(valueString, monthValue));
            }
            break;
        case '1':
            // m2 must be a digit between 0 and 2.
            switch (m2) {
            case '0':
                month = Calendar.OCTOBER;
                break;

            case '1':
                month = Calendar.NOVEMBER;
                break;

            case '2':
                month = Calendar.DECEMBER;
                break;

            default:
                throw new LocalizedIllegalArgumentException(
                        WARN_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_MONTH.get(valueString, monthValue));
            }
            break;
        default:
            throw new LocalizedIllegalArgumentException(
                    WARN_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_MONTH.get(valueString, monthValue));
        }

        // The next two characters should be the day of the month, and they
        // must form the string representation of an integer between 01 and
        // 31. This doesn't do any validation against the year or month, so
        // it will allow dates like April 31, or February 29 in a non-leap
        // year, but we'll let those slide.
        final char d1 = valueString.charAt(6);
        final char d2 = valueString.charAt(7);
        final String dayValue = valueString.substring(6, 8);
        switch (d1) {
        case '0':
            // d2 must be a digit between 1 and 9.
            day = toInt(d2, WARN_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_DAY, valueString, dayValue);
            if (day == 0) {
                throw new LocalizedIllegalArgumentException(
                        WARN_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_DAY.get(valueString, dayValue));
            }
            break;

        case '1':
            // d2 must be a digit between 0 and 9.
            day = 10 + toInt(d2, WARN_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_DAY, valueString, dayValue);
            break;

        case '2':
            // d2 must be a digit between 0 and 9.
            day = 20 + toInt(d2, WARN_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_DAY, valueString, dayValue);
            break;

        case '3':
            // d2 must be either 0 or 1.
            switch (d2) {
            case '0':
                day = 30;
                break;

            case '1':
                day = 31;
                break;

            default:
                throw new LocalizedIllegalArgumentException(
                        WARN_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_DAY.get(valueString, dayValue));
            }
            break;

        default:
            throw new LocalizedIllegalArgumentException(
                    WARN_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_DAY.get(valueString, dayValue));
        }

        // The next two characters must be the hour, and they must form the
        // string representation of an integer between 00 and 23.
        final char h1 = valueString.charAt(8);
        final char h2 = valueString.charAt(9);
        final String hourValue = valueString.substring(8, 10);
        switch (h1) {
        case '0':
            hour = toInt(h2, WARN_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_HOUR, valueString, hourValue);
            break;

        case '1':
            hour = 10 + toInt(h2, WARN_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_HOUR, valueString, hourValue);
            break;

        case '2':
            switch (h2) {
            case '0':
                hour = 20;
                break;

            case '1':
                hour = 21;
                break;

            case '2':
                hour = 22;
                break;

            case '3':
                hour = 23;
                break;

            default:
                throw new LocalizedIllegalArgumentException(
                        WARN_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_HOUR.get(valueString, hourValue));
            }
            break;

        default:
            throw new LocalizedIllegalArgumentException(
                    WARN_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_HOUR.get(valueString, hourValue));
        }

        // Next, there should be either two digits comprising an integer
        // between 00 and 59 (for the minute), a letter 'Z' (for the UTC
        // specifier), a plus or minus sign followed by two or four digits
        // (for the UTC offset), or a period or comma representing the
        // fraction.
        m1 = valueString.charAt(10);
        switch (m1) {
        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
            // There must be at least two more characters, and the next one
            // must be a digit between 0 and 9.
            if (length < 13) {
                throw invalidChar(valueString, m1, 10);
            }

            minute = 10 * (m1 - '0');
            minute += toInt(valueString.charAt(11),
                    WARN_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_MINUTE, valueString, valueString.substring(10, 12));

            break;

        case 'Z':
        case 'z':
                // This is fine only if we are at the end of the value.
            if (length == 11) {
                final TimeZone tz = TIME_ZONE_UTC_OBJ;
                return createTime(valueString, year, month, day, hour, minute, second, tz);
            } else {
                throw invalidChar(valueString, m1, 10);
            }

        case '+':
        case '-':
            // These are fine only if there are exactly two or four more
            // digits that specify a valid offset.
            if (length == 13 || length == 15) {
                final TimeZone tz = getTimeZoneForOffset(valueString, 10);
                return createTime(valueString, year, month, day, hour, minute, second, tz);
            } else {
                throw invalidChar(valueString, m1, 10);
            }

        case '.':
        case ',':
            return finishDecodingFraction(valueString, 11, year, month, day, hour, minute, second,
                    3600000);

        default:
            throw invalidChar(valueString, m1, 10);
        }

        // Next, there should be either two digits comprising an integer
        // between 00 and 60 (for the second, including a possible leap
        // second), a letter 'Z' (for the UTC specifier), a plus or minus
        // sign followed by two or four digits (for the UTC offset), or a
        // period or comma to start the fraction.
        final char s1 = valueString.charAt(12);
        switch (s1) {
        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
            // There must be at least two more characters, and the next one
            // must be a digit between 0 and 9.
            if (length < 15) {
                throw invalidChar(valueString, s1, 12);
            }

            second = 10 * (s1 - '0');
            second += toInt(valueString.charAt(13),
                WARN_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_MINUTE, valueString, valueString.substring(12, 14));

            break;

        case '6':
            // There must be at least two more characters and the next one
            // must be a 0.
            if (length < 15) {
                throw invalidChar(valueString, s1, 12);
            }

            if (valueString.charAt(13) != '0') {
                throw new LocalizedIllegalArgumentException(
                        WARN_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_SECOND.get(
                                valueString, valueString.substring(12, 14)));
            }

            second = 60;
            break;

        case 'Z':
        case 'z':
            // This is fine only if we are at the end of the value.
            if (length == 13) {
                final TimeZone tz = TIME_ZONE_UTC_OBJ;
                return createTime(valueString, year, month, day, hour, minute, second, tz);
            } else {
                throw invalidChar(valueString, s1, 12);
            }

        case '+':
        case '-':
            // These are fine only if there are exactly two or four more
            // digits that specify a valid offset.
            if (length == 15 || length == 17) {
                final TimeZone tz = getTimeZoneForOffset(valueString, 12);
                return createTime(valueString, year, month, day, hour, minute, second, tz);
            } else {
                throw invalidChar(valueString, s1, 12);
            }

        case '.':
        case ',':
            return finishDecodingFraction(valueString, 13, year, month, day, hour, minute, second,
                    60000);

        default:
            throw invalidChar(valueString, s1, 12);
        }

        // Next, there should be either a period or comma followed by
        // between one and three digits (to specify the sub-second), a
        // letter 'Z' (for the UTC specifier), or a plus or minus sign
        // followed by two our four digits (for the UTC offset).
        switch (valueString.charAt(14)) {
        case '.':
        case ',':
            return finishDecodingFraction(valueString, 15, year, month, day, hour, minute, second,
                    1000);

        case 'Z':
        case 'z':
            // This is fine only if we are at the end of the value.
            if (length == 15) {
                final TimeZone tz = TIME_ZONE_UTC_OBJ;
                return createTime(valueString, year, month, day, hour, minute, second, tz);
            } else {
                throw invalidChar(valueString, valueString.charAt(14), 14);
            }

        case '+':
        case '-':
            // These are fine only if there are exactly two or four more
            // digits that specify a valid offset.
            if (length == 17 || length == 19) {
                final TimeZone tz = getTimeZoneForOffset(valueString, 14);
                return createTime(valueString, year, month, day, hour, minute, second, tz);
            } else {
                throw invalidChar(valueString, valueString.charAt(14), 14);
            }

        default:
            throw invalidChar(valueString, valueString.charAt(14), 14);
        }
    }

    private static LocalizedIllegalArgumentException invalidChar(String valueString, char c, int pos) {
        return new LocalizedIllegalArgumentException(
                WARN_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_CHAR.get(
                        valueString, String.valueOf(c), pos));
    }

    private static int toInt(char c, Arg2<Object, Object> invalidSyntaxMsg, String valueString, String unitValue) {
        switch (c) {
        case '0':
            return 0;
        case '1':
            return 1;
        case '2':
            return 2;
        case '3':
            return 3;
        case '4':
            return 4;
        case '5':
            return 5;
        case '6':
            return 6;
        case '7':
            return 7;
        case '8':
            return 8;
        case '9':
            return 9;
        default:
            throw new LocalizedIllegalArgumentException(
                invalidSyntaxMsg.get(valueString, unitValue));
        }
    }

    /**
     * Returns a generalized time object representing the provided date / time
     * parameters.
     *
     * @param value
     *            The generalized time string representation.
     * @param year
     *            The year.
     * @param month
     *            The month.
     * @param day
     *            The day.
     * @param hour
     *            The hour.
     * @param minute
     *            The minute.
     * @param second
     *            The second.
     * @param tz
     *            The timezone.
     * @return A generalized time representing the provided date / time
     *         parameters.
     * @throws LocalizedIllegalArgumentException
     *             If the generalized time could not be created.
     */
    private static GeneralizedTime createTime(final String value, final int year, final int month,
            final int day, final int hour, final int minute, final int second, final TimeZone tz) {
        try {
            final GregorianCalendar calendar = new GregorianCalendar();
            calendar.setLenient(false);
            calendar.setTimeZone(tz);
            calendar.set(year, month, day, hour, minute, second);
            calendar.set(Calendar.MILLISECOND, 0);
            return new GeneralizedTime(calendar, null, Long.MIN_VALUE, value);
        } catch (final Exception e) {
            // This should only happen if the provided date wasn't legal
            // (e.g., September 31).
            final LocalizableMessage message =
                    WARN_ATTR_SYNTAX_GENERALIZED_TIME_ILLEGAL_TIME.get(value, String.valueOf(e));
            throw new LocalizedIllegalArgumentException(message, e);
        }
    }

    /**
     * Completes decoding the generalized time value containing a fractional
     * component. It will also decode the trailing 'Z' or offset.
     *
     * @param value
     *            The whole value, including the fractional component and time
     *            zone information.
     * @param startPos
     *            The position of the first character after the period in the
     *            value string.
     * @param year
     *            The year decoded from the provided value.
     * @param month
     *            The month decoded from the provided value.
     * @param day
     *            The day decoded from the provided value.
     * @param hour
     *            The hour decoded from the provided value.
     * @param minute
     *            The minute decoded from the provided value.
     * @param second
     *            The second decoded from the provided value.
     * @param multiplier
     *            The multiplier value that should be used to scale the fraction
     *            appropriately. If it's a fraction of an hour, then it should
     *            be 3600000 (60*60*1000). If it's a fraction of a minute, then
     *            it should be 60000. If it's a fraction of a second, then it
     *            should be 1000.
     * @return The timestamp created from the provided generalized time value
     *         including the fractional element.
     * @throws LocalizedIllegalArgumentException
     *             If the provided value cannot be parsed as a valid generalized
     *             time string.
     */
    private static GeneralizedTime finishDecodingFraction(final String value, final int startPos,
            final int year, final int month, final int day, final int hour, final int minute,
            final int second, final int multiplier) {
        final int length = value.length();
        final StringBuilder fractionBuffer = new StringBuilder((2 + length) - startPos);
        fractionBuffer.append("0.");

        TimeZone timeZone = null;

    outerLoop:
        for (int i = startPos; i < length; i++) {
            final char c = value.charAt(i);
            switch (c) {
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
                fractionBuffer.append(c);
                break;

            case 'Z':
            case 'z':
                // This is only acceptable if we're at the end of the value.
                if (i != (value.length() - 1)) {
                    final LocalizableMessage message =
                            WARN_ATTR_SYNTAX_GENERALIZED_TIME_ILLEGAL_FRACTION_CHAR.get(value,
                                    String.valueOf(c));
                    throw new LocalizedIllegalArgumentException(message);
                }

                timeZone = TIME_ZONE_UTC_OBJ;
                break outerLoop;

            case '+':
            case '-':
                timeZone = getTimeZoneForOffset(value, i);
                break outerLoop;

            default:
                final LocalizableMessage message =
                        WARN_ATTR_SYNTAX_GENERALIZED_TIME_ILLEGAL_FRACTION_CHAR.get(value, String
                                .valueOf(c));
                throw new LocalizedIllegalArgumentException(message);
            }
        }

        if (fractionBuffer.length() == 2) {
            final LocalizableMessage message =
                    WARN_ATTR_SYNTAX_GENERALIZED_TIME_EMPTY_FRACTION.get(value);
            throw new LocalizedIllegalArgumentException(message);
        }

        if (timeZone == null) {
            final LocalizableMessage message =
                    WARN_ATTR_SYNTAX_GENERALIZED_TIME_NO_TIME_ZONE_INFO.get(value);
            throw new LocalizedIllegalArgumentException(message);
        }

        final Double fractionValue = Double.parseDouble(fractionBuffer.toString());
        final int additionalMilliseconds = (int) Math.round(fractionValue * multiplier);

        try {
            final GregorianCalendar calendar = new GregorianCalendar();
            calendar.setLenient(false);
            calendar.setTimeZone(timeZone);
            calendar.set(year, month, day, hour, minute, second);
            calendar.set(Calendar.MILLISECOND, additionalMilliseconds);
            return new GeneralizedTime(calendar, null, Long.MIN_VALUE, value);
        } catch (final Exception e) {
            // This should only happen if the provided date wasn't legal
            // (e.g., September 31).
            final LocalizableMessage message =
                    WARN_ATTR_SYNTAX_GENERALIZED_TIME_ILLEGAL_TIME.get(value, String.valueOf(e));
            throw new LocalizedIllegalArgumentException(message, e);
        }
    }

    /**
     * Decodes a time zone offset from the provided value.
     *
     * @param value
     *            The whole value, including the offset.
     * @param startPos
     *            The position of the first character that is contained in the
     *            offset. This should be the position of the plus or minus
     *            character.
     * @return The {@code TimeZone} object representing the decoded time zone.
     */
    private static TimeZone getTimeZoneForOffset(final String value, final int startPos) {
        final String offSetStr = value.substring(startPos);
        final int len = offSetStr.length();
        if (len != 3 && len != 5) {
            final LocalizableMessage message =
                    WARN_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_OFFSET.get(value, offSetStr);
            throw new LocalizedIllegalArgumentException(message);
        }

        // The first character must be either a plus or minus.
        switch (offSetStr.charAt(0)) {
        case '+':
        case '-':
            // These are OK.
            break;

        default:
            final LocalizableMessage message =
                    WARN_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_OFFSET.get(value, offSetStr);
            throw new LocalizedIllegalArgumentException(message);
        }

        // The first two characters must be an integer between 00 and 23.
        switch (offSetStr.charAt(1)) {
        case '0':
        case '1':
            switch (offSetStr.charAt(2)) {
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
                final LocalizableMessage message =
                        WARN_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_OFFSET.get(value, offSetStr);
                throw new LocalizedIllegalArgumentException(message);
            }
            break;

        case '2':
            switch (offSetStr.charAt(2)) {
            case '0':
            case '1':
            case '2':
            case '3':
                // These are all fine.
                break;

            default:
                final LocalizableMessage message =
                        WARN_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_OFFSET.get(value, offSetStr);
                throw new LocalizedIllegalArgumentException(message);
            }
            break;

        default:
            final LocalizableMessage message =
                    WARN_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_OFFSET.get(value, offSetStr);
            throw new LocalizedIllegalArgumentException(message);
        }

        // If there are two more characters, then they must be an integer
        // between 00 and 59.
        if (offSetStr.length() == 5) {
            switch (offSetStr.charAt(3)) {
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
                switch (offSetStr.charAt(4)) {
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
                    final LocalizableMessage message =
                            WARN_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_OFFSET.get(value, offSetStr);
                    throw new LocalizedIllegalArgumentException(message);
                }
                break;

            default:
                final LocalizableMessage message =
                        WARN_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_OFFSET.get(value, offSetStr);
                throw new LocalizedIllegalArgumentException(message);
            }
        }

        // If we've gotten here, then it looks like a valid offset. We can
        // create a time zone by using "GMT" followed by the offset.
        return TimeZone.getTimeZone("GMT" + offSetStr);
    }

    /** Lazily constructed internal representations. */
    private volatile Calendar calendar;
    private volatile Date date;
    private volatile String stringValue;
    private volatile long timeMS;

    private GeneralizedTime(final Calendar calendar, final Date date, final long time,
            final String stringValue) {
        this.calendar = calendar;
        this.date = date;
        this.timeMS = time;
        this.stringValue = stringValue;
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(final GeneralizedTime o) {
        final Long timeMS1 = getTimeInMillis();
        final Long timeMS2 = o.getTimeInMillis();
        return timeMS1.compareTo(timeMS2);
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof GeneralizedTime) {
            return getTimeInMillis() == ((GeneralizedTime) obj).getTimeInMillis();
        } else {
            return false;
        }
    }

    /**
     * Returns the value of this generalized time in milliseconds since the
     * epoch.
     *
     * @return The value of this generalized time in milliseconds since the
     *         epoch.
     */
    public long getTimeInMillis() {
        long tmpTimeMS = timeMS;
        if (tmpTimeMS == Long.MIN_VALUE) {
            if (date != null) {
                tmpTimeMS = date.getTime();
            } else {
                tmpTimeMS = calendar.getTimeInMillis();
            }
            timeMS = tmpTimeMS;
        }
        return tmpTimeMS;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return ((Long) getTimeInMillis()).hashCode();
    }

    /**
     * Returns a {@code Calendar} representation of this generalized time.
     * <p>
     * Subsequent modifications to the returned calendar will not alter the
     * internal state of this generalized time.
     *
     * @return A {@code Calendar} representation of this generalized time.
     */
    public Calendar toCalendar() {
        return (Calendar) getCalendar().clone();
    }

    /**
     * Returns a {@code Date} representation of this generalized time.
     * <p>
     * Subsequent modifications to the returned date will not alter the internal
     * state of this generalized time.
     *
     * @return A {@code Date} representation of this generalized time.
     */
    public Date toDate() {
        Date tmpDate = date;
        if (tmpDate == null) {
            tmpDate = new Date(getTimeInMillis());
            date = tmpDate;
        }
        return (Date) tmpDate.clone();
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        String tmpString = stringValue;
        if (tmpString == null) {
            // Do this in a thread-safe non-synchronized fashion.
            // (Simple)DateFormat is neither fast nor thread-safe.
            final StringBuilder sb = new StringBuilder(19);
            final Calendar tmpCalendar = getCalendar();

            // Format the year yyyy.
            int n = tmpCalendar.get(Calendar.YEAR);
            if (n < 0) {
                throw new IllegalArgumentException("Year cannot be < 0:" + n);
            } else if (n < 10) {
                sb.append("000");
            } else if (n < 100) {
                sb.append("00");
            } else if (n < 1000) {
                sb.append("0");
            }
            sb.append(n);

            // Format the month MM.
            n = tmpCalendar.get(Calendar.MONTH) + 1;
            if (n < 10) {
                sb.append("0");
            }
            sb.append(n);

            // Format the day dd.
            n = tmpCalendar.get(Calendar.DAY_OF_MONTH);
            if (n < 10) {
                sb.append("0");
            }
            sb.append(n);

            // Format the hour HH.
            n = tmpCalendar.get(Calendar.HOUR_OF_DAY);
            if (n < 10) {
                sb.append("0");
            }
            sb.append(n);

            // Format the minute mm.
            n = tmpCalendar.get(Calendar.MINUTE);
            if (n < 10) {
                sb.append("0");
            }
            sb.append(n);

            // Format the seconds ss.
            n = tmpCalendar.get(Calendar.SECOND);
            if (n < 10) {
                sb.append("0");
            }
            sb.append(n);

            // Format the milli-seconds.
            n = tmpCalendar.get(Calendar.MILLISECOND);
            if (n != 0) {
                sb.append('.');
                if (n < 10) {
                    sb.append("00");
                } else if (n < 100) {
                    sb.append("0");
                }
                sb.append(n);
            }

            // Format the timezone.
            n = tmpCalendar.get(Calendar.ZONE_OFFSET) + tmpCalendar.get(Calendar.DST_OFFSET);
            if (n == 0) {
                sb.append('Z');
            } else {
                if (n < 0) {
                    sb.append('-');
                    n = -n;
                } else {
                    sb.append('+');
                }
                n = n / 60000; // Minutes.

                final int h = n / 60;
                if (h < 10) {
                    sb.append("0");
                }
                sb.append(h);

                final int m = n % 60;
                if (m < 10) {
                    sb.append("0");
                }
                sb.append(m);
            }
            tmpString = sb.toString();
            stringValue = tmpString;
        }
        return tmpString;
    }

    private Calendar getCalendar() {
        Calendar tmpCalendar = calendar;
        if (tmpCalendar == null) {
            tmpCalendar = new GregorianCalendar(TIME_ZONE_UTC_OBJ);
            tmpCalendar.setLenient(false);
            tmpCalendar.setTimeInMillis(getTimeInMillis());
            calendar = tmpCalendar;
        }
        return tmpCalendar;
    }
}
