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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2015 ForgeRock AS
 */
package com.forgerock.opendj.util;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import org.forgerock.i18n.LocalizableException;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ProviderNotFoundException;
import org.forgerock.opendj.ldap.spi.Provider;
import org.forgerock.util.Reject;
import org.forgerock.util.Utils;

/**
 * Common utility methods.
 */
public final class StaticUtils {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /**
     * Indicates whether the SDK is being used in debug mode. In debug mode
     * components may enable certain instrumentation in order to help debug
     * applications.
     */
    public static final boolean DEBUG_ENABLED =
            System.getProperty("org.forgerock.opendj.debug") != null;

    private static final boolean DEBUG_TO_STDERR = System
            .getProperty("org.forgerock.opendj.debug.stderr") != null;

    static {
        logIfDebugEnabled("debugging enabled", null);
    }

    /**
     * The end-of-line character for this platform.
     */
    public static final String EOL = System.getProperty("line.separator");

    /**
     * A zero-length byte array.
     */
    public static final byte[] EMPTY_BYTES = new byte[0];

    /** The name of the time zone for universal coordinated time (UTC). */
    private static final String TIME_ZONE_UTC = "UTC";

    /** UTC TimeZone is assumed to never change over JVM lifetime. */
    private static final TimeZone TIME_ZONE_UTC_OBJ = TimeZone.getTimeZone(TIME_ZONE_UTC);

    private static final String[][] BYTE_HEX_STRINGS = new String[2][256];
    private static final int UPPER_CASE = 0;
    private static final int LOWER_CASE = 1;

    static {
        String[] hexDigits = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F"};
        for (int i = 0; i < 256; i++) {
            BYTE_HEX_STRINGS[UPPER_CASE][i] = hexDigits[i >>> 4] + hexDigits[i & 0xF];
            BYTE_HEX_STRINGS[LOWER_CASE][i] = BYTE_HEX_STRINGS[UPPER_CASE][i].toLowerCase();
        }
    }

    /**
     * The default scheduler which should be used when the application does not
     * provide one.
     */
    public static final ReferenceCountedObject<ScheduledExecutorService> DEFAULT_SCHEDULER =
            new ReferenceCountedObject<ScheduledExecutorService>() {

                @Override
                protected ScheduledExecutorService newInstance() {
                    final ThreadFactory factory =
                            Utils.newThreadFactory(null, "OpenDJ LDAP SDK Default Scheduler", true);
                    return Executors.newSingleThreadScheduledExecutor(factory);
                }

                @Override
                protected void destroyInstance(ScheduledExecutorService instance) {
                    instance.shutdownNow();
                }
            };

    /**
     * Retrieves a string representation of the provided byte in hexadecimal.
     *
     * @param b
     *            The byte for which to retrieve the hexadecimal string
     *            representation.
     * @return The string representation of the provided byte in hexadecimal.
     */
    public static String byteToHex(final byte b) {
        return BYTE_HEX_STRINGS[UPPER_CASE][b & 0xFF];
    }

    /**
     * Retrieves a string representation of the provided byte in hexadecimal.
     *
     * @param b
     *            The byte for which to retrieve the hexadecimal string
     *            representation.
     * @return The string representation of the provided byte in hexadecimal
     *         using lowercase characters.
     */
    public static String byteToLowerHex(final byte b) {
        return BYTE_HEX_STRINGS[LOWER_CASE][b & 0xFF];
    }

    /**
     * Returns a string containing provided date formatted using the generalized
     * time syntax.
     *
     * @param date
     *            The date to be formated.
     * @return The string containing provided date formatted using the
     *         generalized time syntax.
     * @throws NullPointerException
     *             If {@code date} was {@code null}.
     */
    public static String formatAsGeneralizedTime(final Date date) {
        return formatAsGeneralizedTime(date.getTime());
    }

    /**
     * Returns a string containing provided date formatted using the generalized
     * time syntax.
     *
     * @param date
     *            The date to be formated.
     * @return The string containing provided date formatted using the
     *         generalized time syntax.
     * @throws IllegalArgumentException
     *             If {@code date} was invalid.
     */
    public static String formatAsGeneralizedTime(final long date) {
        // Generalized time has the format yyyyMMddHHmmss.SSS'Z'

        // Do this in a thread-safe non-synchronized fashion.
        // (Simple)DateFormat is neither fast nor thread-safe.

        final StringBuilder sb = new StringBuilder(19);

        final GregorianCalendar calendar = new GregorianCalendar(TIME_ZONE_UTC_OBJ);
        calendar.setLenient(false);
        calendar.setTimeInMillis(date);

        // Format the year yyyy.
        int n = calendar.get(Calendar.YEAR);
        if (n < 0) {
            final IllegalArgumentException e = new IllegalArgumentException("Year cannot be < 0:" + n);
            throw e;
        } else if (n < 10) {
            sb.append("000");
        } else if (n < 100) {
            sb.append("00");
        } else if (n < 1000) {
            sb.append("0");
        }
        sb.append(n);

        // Format the month MM.
        n = calendar.get(Calendar.MONTH) + 1;
        if (n < 10) {
            sb.append("0");
        }
        sb.append(n);

        // Format the day dd.
        n = calendar.get(Calendar.DAY_OF_MONTH);
        if (n < 10) {
            sb.append("0");
        }
        sb.append(n);

        // Format the hour HH.
        n = calendar.get(Calendar.HOUR_OF_DAY);
        if (n < 10) {
            sb.append("0");
        }
        sb.append(n);

        // Format the minute mm.
        n = calendar.get(Calendar.MINUTE);
        if (n < 10) {
            sb.append("0");
        }
        sb.append(n);

        // Format the seconds ss.
        n = calendar.get(Calendar.SECOND);
        if (n < 10) {
            sb.append("0");
        }
        sb.append(n);

        // Format the milli-seconds.
        sb.append('.');
        n = calendar.get(Calendar.MILLISECOND);
        if (n < 10) {
            sb.append("00");
        } else if (n < 100) {
            sb.append("0");
        }
        sb.append(n);

        // Format the timezone (always Z).
        sb.append('Z');

        return sb.toString();
    }

    /**
     * Construct a byte array containing the UTF-8 encoding of the provided
     * character array.
     *
     * @param chars
     *            The character array to convert to a UTF-8 byte array.
     * @return A byte array containing the UTF-8 encoding of the provided
     *         character array.
     */
    public static byte[] getBytes(final char[] chars) {
        final Charset utf8 = Charset.forName("UTF-8");
        final ByteBuffer buffer = utf8.encode(CharBuffer.wrap(chars));
        final byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    /**
     * Construct a byte array containing the UTF-8 encoding of the provided
     * char sequence. This is significantly faster than calling
     * {@link String#getBytes(String)} for ASCII strings.
     *
     * @param s
     *            The char sequence to convert to a UTF-8 byte array.
     * @return Returns a byte array containing the UTF-8 encoding of the
     *         provided string.
     */
    public static byte[] getBytes(final CharSequence s) {
        if (s == null) {
            return null;
        }

        char c;
        final int length = s.length();
        final byte[] returnArray = new byte[length];
        for (int i = 0; i < length; i++) {
            c = s.charAt(i);
            returnArray[i] = (byte) (c & 0x0000007F);
            if (c != returnArray[i]) {
                try {
                    return s.toString().getBytes("UTF-8");
                } catch (UnsupportedEncodingException e) {
                    // TODO: I18N
                    throw new RuntimeException("Unable to encode UTF-8 string " + s, e);
                }
            }
        }

        return returnArray;
    }

    /**
     * Retrieves the best human-readable message for the provided exception. For
     * exceptions defined in the OpenDJ project, it will attempt to use the
     * message (combining it with the message ID if available). For some
     * exceptions that use encapsulation (e.g., InvocationTargetException), it
     * will be unwrapped and the cause will be treated. For all others, the
     *
     * @param t
     *            The {@code Throwable} object for which to retrieve the
     *            message.
     * @return The human-readable message generated for the provided exception.
     */
    public static LocalizableMessage getExceptionMessage(final Throwable t) {
        if (t instanceof LocalizableException) {
            final LocalizableException ie = (LocalizableException) t;
            return ie.getMessageObject();
        } else if (t instanceof NullPointerException) {
            final StackTraceElement[] stackElements = t.getStackTrace();

            final LocalizableMessageBuilder message = new LocalizableMessageBuilder();
            message.append("NullPointerException(");
            message.append(stackElements[0].getFileName());
            message.append(":");
            message.append(stackElements[0].getLineNumber());
            message.append(")");
            return message.toMessage();
        } else if (t instanceof InvocationTargetException && t.getCause() != null) {
            return getExceptionMessage(t.getCause());
        } else {
            final StringBuilder message = new StringBuilder();

            final String className = t.getClass().getName();
            final int periodPos = className.lastIndexOf('.');
            if (periodPos > 0) {
                message.append(className.substring(periodPos + 1));
            } else {
                message.append(className);
            }

            message.append("(");
            if (t.getMessage() == null) {
                final StackTraceElement[] stackElements = t.getStackTrace();
                message.append(stackElements[0].getFileName());
                message.append(":");
                message.append(stackElements[0].getLineNumber());

                // FIXME Temporary to debug issue 2256.
                if (t instanceof IllegalStateException) {
                    for (int i = 1; i < stackElements.length; i++) {
                        message.append(' ');
                        message.append(stackElements[i].getFileName());
                        message.append(":");
                        message.append(stackElements[i].getLineNumber());
                    }
                }
            } else {
                message.append(t.getMessage());
            }

            message.append(")");

            return LocalizableMessage.raw(message.toString());
        }
    }

    /**
     * Indicates whether the provided character is an ASCII alphabetic
     * character.
     *
     * @param c
     *            The character for which to make the determination.
     * @return <CODE>true</CODE> if the provided value is an uppercase or
     *         lowercase ASCII alphabetic character, or <CODE>false</CODE> if it
     *         is not.
     */
    public static boolean isAlpha(final char c) {
        final ASCIICharProp cp = ASCIICharProp.valueOf(c);
        return cp != null ? cp.isLetter() : false;
    }

    /**
     * Indicates whether the provided character is a numeric digit.
     *
     * @param c
     *            The character for which to make the determination.
     * @return <CODE>true</CODE> if the provided character represents a numeric
     *         digit, or <CODE>false</CODE> if not.
     */
    public static boolean isDigit(final char c) {
        final ASCIICharProp cp = ASCIICharProp.valueOf(c);
        return cp != null ? cp.isDigit() : false;
    }

    /**
     * Indicates whether the provided character is a hexadecimal digit.
     *
     * @param c
     *            The character for which to make the determination.
     * @return <CODE>true</CODE> if the provided character represents a
     *         hexadecimal digit, or <CODE>false</CODE> if not.
     */
    public static boolean isHexDigit(final char c) {
        final ASCIICharProp cp = ASCIICharProp.valueOf(c);
        return cp != null ? cp.isHexDigit() : false;
    }

    /**
     * Indicates whether the provided character is a keychar.
     *
     * @param c
     *            The character for which to make the determination.
     * @param allowCompatChars
     *            {@code true} if certain illegal characters should be allowed
     *            for compatibility reasons.
     * @return <CODE>true</CODE> if the provided character represents a keychar,
     *         or <CODE>false</CODE> if not.
     */
    public static boolean isKeyChar(final char c, final boolean allowCompatChars) {
        final ASCIICharProp cp = ASCIICharProp.valueOf(c);
        return cp != null ? cp.isKeyChar(allowCompatChars) : false;
    }

    /**
     * Retrieves a stack trace from the provided exception as a single-line
     * string.
     *
     * @param throwable
     *            The exception for which to retrieve the stack trace.
     * @param isFullStack
     *            If {@code true}, provides the full stack trace, otherwise
     *            provides a limited extract of stack trace
     * @return A stack trace from the provided exception as a single-line
     *         string.
     */
    public static String stackTraceToSingleLineString(Throwable throwable, boolean isFullStack) {
        StringBuilder buffer = new StringBuilder();
        stackTraceToSingleLineString(buffer, throwable, isFullStack);
        return buffer.toString();
    }


    /**
     * Appends a single-line string representation of the provided exception to
     * the given buffer.
     *
     * @param buffer
     *            The buffer to which the information is to be appended.
     * @param throwable
     *            The exception for which to retrieve the stack trace.
     * @param isFullStack
     *            If {@code true}, provides the full stack trace, otherwise
     *            provides a limited extract of stack trace
     */
    public static void stackTraceToSingleLineString(StringBuilder buffer, Throwable throwable, boolean isFullStack) {
        if (throwable == null) {
            return;
        }
        if (isFullStack) {
            // add class name and message of the exception
            buffer.append(throwable.getClass().getName());
            final String message = throwable.getLocalizedMessage();
            if (message != null && message.length() != 0) {
                buffer.append(": ").append(message);
            }
            // add first-level stack trace
            for (StackTraceElement e : throwable.getStackTrace()) {
                buffer.append(" / ");
                buffer.append(e.getFileName());
                buffer.append(":");
                buffer.append(e.getLineNumber());
            }
            // add stack trace of all underlying causes
            while (throwable.getCause() != null) {
                throwable = throwable.getCause();
                buffer.append("; caused by ");
                buffer.append(throwable);

                for (StackTraceElement e : throwable.getStackTrace()) {
                    buffer.append(" / ");
                    buffer.append(e.getFileName());
                    buffer.append(":");
                    buffer.append(e.getLineNumber());
                }
            }
        } else {
            if (throwable instanceof InvocationTargetException && throwable.getCause() != null) {
                throwable = throwable.getCause();
            }
            // add class name and message of the exception
            buffer.append(throwable.getClass().getSimpleName());
            final String message = throwable.getLocalizedMessage();
            if (message != null && message.length() != 0) {
                buffer.append(": ").append(message);
            }
            // add first 20 items of the first-level stack trace
            int i = 0;
            buffer.append(" (");
            for (StackTraceElement e : throwable.getStackTrace()) {
                if (i > 20) {
                    buffer.append(" ...");
                    break;
                } else if (i > 0) {
                    buffer.append(" ");
                }

                buffer.append(e.getFileName());
                buffer.append(":");
                buffer.append(e.getLineNumber());
                i++;
            }

            buffer.append(")");
        }
    }

    /**
     * Appends a lowercase string representation of the contents of the given
     * byte array to the provided buffer. This implementation presumes that the
     * provided string will contain only ASCII characters and is optimized for
     * that case. However, if a non-ASCII character is encountered it will fall
     * back on a more expensive algorithm that will work properly for non-ASCII
     * characters.
     *
     * @param b
     *            The byte array for which to obtain the lowercase string
     *            representation.
     * @param builder
     *            The buffer to which the lowercase form of the string should be
     *            appended.
     * @return The updated {@code StringBuilder}.
     */
    public static StringBuilder toLowerCase(final ByteSequence b, final StringBuilder builder) {
        Reject.ifNull(b, builder);

        // FIXME: What locale should we use for non-ASCII characters? I
        // think we should use default to the Unicode StringPrep.

        final int origBufferLen = builder.length();
        final int length = b.length();

        for (int i = 0; i < length; i++) {
            final int c = b.byteAt(i);

            if (c < 0) {
                builder.replace(origBufferLen, builder.length(), b.toString().toLowerCase(
                        Locale.ENGLISH));
                return builder;
            }

            // At this point 0 <= 'c' <= 128.
            final ASCIICharProp cp = ASCIICharProp.valueOf(c);
            builder.append(cp.toLowerCase());
        }

        return builder;
    }

    /**
     * Retrieves a lower-case representation of the given string. This
     * implementation presumes that the provided string will contain only ASCII
     * characters and is optimized for that case. However, if a non-ASCII
     * character is encountered it will fall back on a more expensive algorithm
     * that will work properly for non-ASCII characters.
     *
     * @param s
     *            The string for which to obtain the lower-case representation.
     * @return The lower-case representation of the given string.
     */
    public static String toLowerCase(final String s) {
        Reject.ifNull(s);

        // FIXME: What locale should we use for non-ASCII characters? I
        // think we should use default to the Unicode StringPrep.

        // This code is optimized for the case where the input string 's'
        // has already been converted to lowercase.
        final int length = s.length();
        int i = 0;
        ASCIICharProp cp = null;

        // Scan for non lowercase ASCII.
        while (i < length) {
            cp = ASCIICharProp.valueOf(s.charAt(i));
            if (cp == null || cp.isUpperCase()) {
                break;
            }
            i++;
        }

        if (i == length) {
            // String was already lowercase ASCII.
            return s;
        }

        // Found non lowercase ASCII.
        final StringBuilder builder = new StringBuilder(length);
        builder.append(s, 0, i);

        if (cp != null) {
            // Upper-case ASCII.
            builder.append(cp.toLowerCase());
            i++;
            while (i < length) {
                cp = ASCIICharProp.valueOf(s.charAt(i));
                if (cp == null) {
                    break;
                }
                builder.append(cp.toLowerCase());
                i++;
            }
        }

        if (i < length) {
            builder.append(s.substring(i).toLowerCase(Locale.ENGLISH));
        }

        return builder.toString();
    }

    /**
     * Appends a lower-case representation of the given string to the provided
     * buffer. This implementation presumes that the provided string will
     * contain only ASCII characters and is optimized for that case. However, if
     * a non-ASCII character is encountered it will fall back on a more
     * expensive algorithm that will work properly for non-ASCII characters.
     *
     * @param s
     *            The string for which to obtain the lower-case representation.
     * @param builder
     *            The {@code StringBuilder} to which the lower-case form of the
     *            string should be appended.
     * @return The updated {@code StringBuilder}.
     */
    public static StringBuilder toLowerCase(final String s, final StringBuilder builder) {
        Reject.ifNull(s);
        Reject.ifNull(builder);

        // FIXME: What locale should we use for non-ASCII characters? I
        // think we should use default to the Unicode StringPrep.

        final int length = s.length();
        builder.ensureCapacity(builder.length() + length);

        for (int i = 0; i < length; i++) {
            final ASCIICharProp cp = ASCIICharProp.valueOf(s.charAt(i));
            if (cp != null) {
                builder.append(cp.toLowerCase());
            } else {
                // Non-ASCII.
                builder.append(s.substring(i).toLowerCase(Locale.ENGLISH));
                return builder;
            }
        }

        return builder;
    }

    /**
     * Returns a copy of the provided byte array.
     *
     * @param bytes
     *            The byte array to be copied.
     * @return A copy of the provided byte array.
     */
    public static byte[] copyOfBytes(final byte[] bytes) {
        return Arrays.copyOf(bytes, bytes.length);
    }

    /**
     * Returns the stack trace for the calling method, but only if SDK debugging
     * is enabled.
     *
     * @return The stack trace for the calling method, but only if SDK debugging
     *         is enabled, otherwise {@code null}..
     */
    public static StackTraceElement[] getStackTraceIfDebugEnabled() {
        if (!DEBUG_ENABLED) {
            return null;
        }
        final StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        return Arrays.copyOfRange(stack, 2, stack.length);
    }

    /**
     * Logs the provided message and stack trace if SDK debugging is enabled to
     * either stderr or the debug logger.
     *
     * @param msg
     *            The message to be logged.
     * @param stackTrace
     *            The stack trace, which may be {@code null}.
     */
    public static void logIfDebugEnabled(final String msg, final StackTraceElement[] stackTrace) {
        if (DEBUG_ENABLED) {
            final StringBuilder builder = new StringBuilder("OPENDJ SDK: ");
            builder.append(msg);
            if (stackTrace != null) {
                builder.append(EOL);
                for (StackTraceElement e : stackTrace) {
                    builder.append("\tat ");
                    builder.append(e);
                    builder.append(EOL);
                }
            }
            if (DEBUG_TO_STDERR) {
                System.err.println(builder);
            } else {
                // TODO: I18N
                logger.error(LocalizableMessage.raw("%s", builder));
            }
        }
    }

    /**
     * Retrieves the printable ASCII representation of the provided byte.
     *
     * @param b
     *            The byte for which to retrieve the printable ASCII
     *            representation.
     * @return The printable ASCII representation of the provided byte, or a
     *         space if the provided byte does not have printable ASCII
     *         representation.
     */
    public static char byteToASCII(final byte b) {
        if (isPrintable(b)) {
            return (char) b;
        }
        return ' ';
    }

    /**
     * Returns whether the byte is a printable ASCII character.
     *
     * @param b
     *          The byte for which to determine whether it is printable ASCII
     * @return true if the byte is a printable ASCII character
     */
    public static boolean isPrintable(final byte b) {
        return 32 <= b && b <= 126;
    }

    /** Prevent instantiation. */
    private StaticUtils() {
        // No implementation required.
    }

    /**
     * Find and returns a provider of one or more implementations.
     * <p>
     * The provider is loaded using the {@code ServiceLoader} facility.
     *
     * @param <P>
     *            type of provider
     * @param providerClass
     *            class of provider
     * @param requestedProvider
     *            name of provider to use, or {@code null} if no specific
     *            provider is requested.
     * @param classLoader
     *            class loader to use to load the provider, or {@code null} to
     *            use the default class loader (the context class loader of the
     *            current thread).
     * @return a provider
     * @throws ProviderNotFoundException
     *             if no provider is available or if the provider requested
     *             using options is not found.
     */
    public static <P extends Provider> P getProvider(final Class<P> providerClass, final String requestedProvider,
            final ClassLoader classLoader) {
        ServiceLoader<P> loader = null;
        if (classLoader != null) {
            loader = ServiceLoader.load(providerClass, classLoader);
        } else {
            loader = ServiceLoader.load(providerClass);
        }
        StringBuilder providersFound = new StringBuilder();
        for (P provider : loader) {
            if (providersFound.length() > 0) {
                providersFound.append(" ");
            }
            providersFound.append(provider.getName());
            if (requestedProvider == null || provider.getName().equals(requestedProvider)) {
                return provider;
            }
        }
        if (providersFound.length() > 0) {
            throw new ProviderNotFoundException(providerClass, requestedProvider, String.format(
                    "The requested provider '%s' of type '%s' was not found. Available providers: %s",
                    requestedProvider, providerClass.getName(), providersFound));
        } else {
            throw new ProviderNotFoundException(providerClass, requestedProvider, String.format(
                    "There was no provider of type '%s' available.", providerClass.getName()));
        }
    }

}
