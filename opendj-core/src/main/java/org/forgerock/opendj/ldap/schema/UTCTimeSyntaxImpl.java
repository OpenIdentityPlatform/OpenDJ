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
 *      Portions copyright 2012-2014 ForgeRock AS
 */
package org.forgerock.opendj.ldap.schema;

import static com.forgerock.opendj.ldap.CoreMessages.*;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.DecodeException;

/**
 * This class implements the UTC time attribute syntax. This is very similar to
 * the generalized time syntax (and actually has been deprecated in favor of
 * that), but requires that the minute be provided and does not allow for
 * sub-second times. All matching will be performed using the generalized time
 * matching rules, and equality, ordering, and substring matching will be
 * allowed.
 */
final class UTCTimeSyntaxImpl extends AbstractSyntaxImpl {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /**
     * The lock that will be used to provide threadsafe access to the date
     * formatter.
     */
    private static final Object DATE_FORMAT_LOCK;

    /**
     * The date formatter that will be used to convert dates into UTC time
     * values. Note that all interaction with it must be synchronized.
     */
    private static final SimpleDateFormat DATE_FORMAT;

    /**
     * The date formatter needs help converting 2-digit years.
     */
    private static Date datum1900;
    private static Date datum2000;

    /*
     * Create the date formatter that will be used to construct and parse
     * normalized UTC time values.
     */
    static {
        DATE_FORMAT = new SimpleDateFormat(DATE_FORMAT_UTC_TIME);
        DATE_FORMAT.setLenient(false);
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone(TIME_ZONE_UTC));

        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(TIME_ZONE_UTC));
        cal.clear();
        cal.set(1900, 0, 1);
        datum1900 = cal.getTime();

        cal.clear();
        cal.set(2000, 0, 1);
        datum2000 = cal.getTime();

        DATE_FORMAT_LOCK = new Object();
    }

    /**
     * Retrieves an string containing a UTC time representation of the provided
     * date.
     *
     * @param d
     *            The date for which to retrieve the UTC time value.
     * @return The attribute value created from the date.
     */
    static String createUTCTimeValue(final Date d) {
        synchronized (DATE_FORMAT_LOCK) {
            return DATE_FORMAT.format(d);
        }
    }

    /**
     * Decodes the provided normalized value as a UTC time value and retrieves a
     * Java <CODE>Date</CODE> object containing its representation.
     *
     * @param valueString
     *            The normalized UTC time value to decode to a Java
     *            <CODE>Date</CODE>.
     * @return The Java <CODE>Date</CODE> created from the provided UTC time
     *         value.
     * @throws DecodeException
     *             If the provided value cannot be parsed as a valid UTC time
     *             string.
     */
    static Date decodeUTCTimeValue(final String valueString) throws DecodeException {
        try {
            synchronized (DATE_FORMAT_LOCK) {
                // RFC 3280 4.1.2.5.1. defines the datum we need to
                // set for the parser.
                switch (valueString.charAt(0)) {
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                    // 00-49
                    DATE_FORMAT.set2DigitYearStart(datum2000);
                    break;
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                default:
                    // 50-99
                    DATE_FORMAT.set2DigitYearStart(datum1900);
                    break;
                }
                return DATE_FORMAT.parse(valueString);
            }
        } catch (final Exception e) {
            final LocalizableMessage message =
                    ERR_ATTR_SYNTAX_UTC_TIME_CANNOT_PARSE.get(valueString, e);
            final DecodeException de = DecodeException.error(message, e);
            logger.debug(LocalizableMessage.raw("%s", de));
            throw de;
        }
    }

    @Override
    public String getEqualityMatchingRule() {
        return EMR_GENERALIZED_TIME_OID;
    }

    public String getName() {
        return SYNTAX_UTC_TIME_NAME;
    }

    @Override
    public String getOrderingMatchingRule() {
        return OMR_GENERALIZED_TIME_OID;
    }

    @Override
    public String getSubstringMatchingRule() {
        return SMR_CASE_IGNORE_OID;
    }

    public boolean isHumanReadable() {
        return false;
    }

    /**
     * Indicates whether the provided value is acceptable for use in an
     * attribute with this syntax. If it is not, then the reason may be appended
     * to the provided buffer.
     *
     * @param schema
     *            The schema in which this syntax is defined.
     * @param value
     *            The value for which to make the determination.
     * @param invalidReason
     *            The buffer to which the invalid reason should be appended.
     * @return <CODE>true</CODE> if the provided value is acceptable for use
     *         with this syntax, or <CODE>false</CODE> if not.
     */
    public boolean valueIsAcceptable(final Schema schema, final ByteSequence value,
            final LocalizableMessageBuilder invalidReason) {
        // Get the value as a string and verify that it is at least long
        // enough for "YYYYMMDDhhmmZ", which is the shortest allowed value.
        final String valueString = value.toString().toUpperCase();
        final int length = valueString.length();
        if (length < 11) {
            final LocalizableMessage message = ERR_ATTR_SYNTAX_UTC_TIME_TOO_SHORT.get(valueString);
            invalidReason.append(message);
            return false;
        }

        // The first two characters are the year, and they must be numeric
        // digits between 0 and 9.
        for (int i = 0; i < 2; i++) {
            switch (valueString.charAt(i)) {
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
                        ERR_ATTR_SYNTAX_UTC_TIME_INVALID_YEAR.get(valueString, String
                                .valueOf(valueString.charAt(i)));
                invalidReason.append(message);
                return false;
            }
        }

        // The next two characters are the month, and they must form the
        // string representation of an integer between 01 and 12.
        char m1 = valueString.charAt(2);
        final char m2 = valueString.charAt(3);
        switch (m1) {
        case '0':
            // m2 must be a digit between 1 and 9.
            switch (m2) {
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
                        ERR_ATTR_SYNTAX_UTC_TIME_INVALID_MONTH.get(valueString, valueString
                                .substring(2, 4));
                invalidReason.append(message);
                return false;
            }
            break;
        case '1':
            // m2 must be a digit between 0 and 2.
            switch (m2) {
            case '0':
            case '1':
            case '2':
                // These are all fine.
                break;
            default:
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_UTC_TIME_INVALID_MONTH.get(valueString, valueString
                                .substring(2, 4));
                invalidReason.append(message);
                return false;
            }
            break;
        default:
            final LocalizableMessage message =
                    ERR_ATTR_SYNTAX_UTC_TIME_INVALID_MONTH.get(valueString, valueString.substring(
                            2, 4));
            invalidReason.append(message);
            return false;
        }

        // The next two characters should be the day of the month, and they
        // must form the string representation of an integer between 01 and
        // 31. This doesn't do any validation against the year or month, so
        // it will allow dates like April 31, or February 29 in a non-leap
        // year, but we'll let those slide.
        final char d1 = valueString.charAt(4);
        final char d2 = valueString.charAt(5);
        switch (d1) {
        case '0':
            // d2 must be a digit between 1 and 9.
            switch (d2) {
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
                        ERR_ATTR_SYNTAX_UTC_TIME_INVALID_DAY.get(valueString, valueString
                                .substring(4, 6));
                invalidReason.append(message);
                return false;
            }
            break;
        case '1':
            // Treated the same as '2'.
        case '2':
            // d2 must be a digit between 0 and 9.
            switch (d2) {
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
                        ERR_ATTR_SYNTAX_UTC_TIME_INVALID_DAY.get(valueString, valueString
                                .substring(4, 6));
                invalidReason.append(message);
                return false;
            }
            break;
        case '3':
            // d2 must be either 0 or 1.
            switch (d2) {
            case '0':
            case '1':
                // These are all fine.
                break;
            default:
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_UTC_TIME_INVALID_DAY.get(valueString, valueString
                                .substring(4, 6));
                invalidReason.append(message);
                return false;
            }
            break;
        default:
            final LocalizableMessage message =
                    ERR_ATTR_SYNTAX_UTC_TIME_INVALID_DAY.get(valueString, valueString.substring(4,
                            6));
            invalidReason.append(message);
            return false;
        }

        // The next two characters must be the hour, and they must form the
        // string representation of an integer between 00 and 23.
        final char h1 = valueString.charAt(6);
        final char h2 = valueString.charAt(7);
        switch (h1) {
        case '0':
            // This is treated the same as '1'.
        case '1':
            switch (h2) {
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
                        ERR_ATTR_SYNTAX_UTC_TIME_INVALID_HOUR.get(valueString, valueString
                                .substring(6, 8));
                invalidReason.append(message);
                return false;
            }
            break;
        case '2':
            // This must be a digit between 0 and 3.
            switch (h2) {
            case '0':
            case '1':
            case '2':
            case '3':
                // These are all fine.
                break;
            default:
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_UTC_TIME_INVALID_HOUR.get(valueString, valueString
                                .substring(6, 8));
                invalidReason.append(message);
                return false;
            }
            break;
        default:
            final LocalizableMessage message =
                    ERR_ATTR_SYNTAX_UTC_TIME_INVALID_HOUR.get(valueString, valueString.substring(6,
                            8));
            invalidReason.append(message);
            return false;
        }

        // Next, there should be two digits comprising an integer between 00
        // and 59 for the minute.
        m1 = valueString.charAt(8);
        switch (m1) {
        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
            // There must be at least two more characters, and the next one
            // must be a digit between 0 and 9.
            if (length < 11) {
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_UTC_TIME_INVALID_CHAR.get(valueString, String.valueOf(m1),
                                8);
                invalidReason.append(message);
                return false;
            }

            switch (valueString.charAt(9)) {
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
                        ERR_ATTR_SYNTAX_UTC_TIME_INVALID_MINUTE.get(valueString, valueString
                                .substring(8, 10));
                invalidReason.append(message);
                return false;
            }

            break;

        default:
            final LocalizableMessage message =
                    ERR_ATTR_SYNTAX_UTC_TIME_INVALID_CHAR.get(valueString, String.valueOf(m1), 8);
            invalidReason.append(message);
            return false;
        }

        // Next, there should be either two digits comprising an integer
        // between 00 and 60 (for the second, including a possible leap
        // second), a letter 'Z' (for the UTC specifier), or a plus or minus
        // sign followed by four digits (for the UTC offset).
        final char s1 = valueString.charAt(10);
        switch (s1) {
        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
            // There must be at least two more characters, and the next one
            // must be a digit between 0 and 9.
            if (length < 13) {
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_UTC_TIME_INVALID_CHAR.get(valueString, String.valueOf(s1),
                                10);
                invalidReason.append(message);
                return false;
            }

            switch (valueString.charAt(11)) {
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
                        ERR_ATTR_SYNTAX_UTC_TIME_INVALID_SECOND.get(valueString, valueString
                                .substring(10, 12));
                invalidReason.append(message);
                return false;
            }

            break;
        case '6':
            // There must be at least two more characters and the next one
            // must be a 0.
            if (length < 13) {

                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_UTC_TIME_INVALID_CHAR.get(valueString, String.valueOf(s1),
                                10);
                invalidReason.append(message);
                return false;
            }

            if (valueString.charAt(11) != '0') {
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_UTC_TIME_INVALID_SECOND.get(valueString, valueString
                                .substring(10, 12));
                invalidReason.append(message);
                return false;
            }

            break;
        case 'Z':
            // This is fine only if we are at the end of the value.
            if (length == 11) {
                return true;
            } else {
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_UTC_TIME_INVALID_CHAR.get(valueString, String.valueOf(s1),
                                10);
                invalidReason.append(message);
                return false;
            }

        case '+':
        case '-':
            // These are fine only if there are exactly four more digits that
            // specify a valid offset.
            if (length == 15) {
                return hasValidOffset(valueString, 11, invalidReason);
            } else {
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_UTC_TIME_INVALID_CHAR.get(valueString, String.valueOf(s1),
                                10);
                invalidReason.append(message);
                return false;
            }

        default:
            final LocalizableMessage message =
                    ERR_ATTR_SYNTAX_UTC_TIME_INVALID_CHAR.get(valueString, String.valueOf(s1), 10);
            invalidReason.append(message);
            return false;
        }

        // The last element should be either a letter 'Z' (for the UTC
        // specifier), or a plus or minus sign followed by four digits (for
        // the UTC offset).
        switch (valueString.charAt(12)) {
        case 'Z':
            // This is fine only if we are at the end of the value.
            if (length == 13) {
                return true;
            } else {
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_UTC_TIME_INVALID_CHAR.get(valueString, String
                                .valueOf(valueString.charAt(12)), 12);
                invalidReason.append(message);
                return false;
            }

        case '+':
        case '-':
            // These are fine only if there are four or two more digits that
            // specify a valid offset.
            if (length == 17 || length == 15) {
                return hasValidOffset(valueString, 13, invalidReason);
            } else {
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_UTC_TIME_INVALID_CHAR.get(valueString, String
                                .valueOf(valueString.charAt(12)), 12);
                invalidReason.append(message);
                return false;
            }

        default:
            final LocalizableMessage message =
                    ERR_ATTR_SYNTAX_UTC_TIME_INVALID_CHAR.get(valueString, String
                            .valueOf(valueString.charAt(12)), 12);
            invalidReason.append(message);
            return false;
        }
    }

    /**
     * Indicates whether the provided string contains a valid set of two or four
     * UTC offset digits. The provided string must have either two or four
     * characters from the provided start position to the end of the value.
     *
     * @param value
     *            The whole value, including the offset.
     * @param startPos
     *            The position of the first character that is contained in the
     *            offset.
     * @param invalidReason
     *            The buffer to which the invalid reason may be appended if the
     *            string does not contain a valid set of UTC offset digits.
     * @return <CODE>true</CODE> if the provided offset string is valid, or
     *         <CODE>false</CODE> if it is not.
     */
    private boolean hasValidOffset(final String value, final int startPos,
            final LocalizableMessageBuilder invalidReason) {
        final int offsetLength = value.length() - startPos;
        if (offsetLength < 2) {
            final LocalizableMessage message = ERR_ATTR_SYNTAX_UTC_TIME_TOO_SHORT.get(value);
            invalidReason.append(message);
            return false;
        }

        // The first two characters must be an integer between 00 and 23.
        switch (value.charAt(startPos)) {
        case '0':
        case '1':
            switch (value.charAt(startPos + 1)) {
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
                        ERR_ATTR_SYNTAX_UTC_TIME_INVALID_OFFSET.get(value, value.substring(
                                startPos, startPos + offsetLength));
                invalidReason.append(message);
                return false;
            }
            break;
        case '2':
            switch (value.charAt(startPos + 1)) {
            case '0':
            case '1':
            case '2':
            case '3':
                // These are all fine.
                break;
            default:
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_UTC_TIME_INVALID_OFFSET.get(value, value.substring(
                                startPos, startPos + offsetLength));
                invalidReason.append(message);
                return false;
            }
            break;
        default:
            final LocalizableMessage message =
                    ERR_ATTR_SYNTAX_UTC_TIME_INVALID_OFFSET.get(value, value.substring(startPos,
                            startPos + offsetLength));
            invalidReason.append(message);
            return false;
        }

        // If there are two more characters, then they must be an integer
        // between 00 and 59.
        if (offsetLength == 4) {
            switch (value.charAt(startPos + 2)) {
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
                switch (value.charAt(startPos + 3)) {
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
                            ERR_ATTR_SYNTAX_UTC_TIME_INVALID_OFFSET.get(value, value.substring(
                                    startPos, startPos + offsetLength));
                    invalidReason.append(message);
                    return false;
                }
                break;
            default:
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_UTC_TIME_INVALID_OFFSET.get(value, value.substring(
                                startPos, startPos + offsetLength));
                invalidReason.append(message);
                return false;
            }
        }

        return true;
    }
}
