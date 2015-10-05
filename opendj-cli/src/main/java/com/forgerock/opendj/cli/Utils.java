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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions copyright 2014-2015 ForgeRock AS.
 */
package com.forgerock.opendj.cli;

import static com.forgerock.opendj.cli.CliMessages.*;

import com.forgerock.opendj.util.OperatingSystem;

import static com.forgerock.opendj.util.StaticUtils.EOL;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.TimeZone;

import javax.naming.AuthenticationException;
import javax.naming.CommunicationException;
import javax.naming.NamingException;
import javax.naming.NamingSecurityException;
import javax.naming.NoPermissionException;
import javax.net.ssl.SSLHandshakeException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.LocalizableMessageDescriptor;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.RDN;

/** This class provides utility functions for all the client side tools. */
public final class Utils {

    /** Platform appropriate line separator. */
    public static final String LINE_SEPARATOR = System.getProperty("line.separator");

    /**
     * The value used to display arguments that must be obfuscated (such as passwords). This does not require
     * localization (since the output of command builder by its nature is not localized).
     */
    public static final String OBFUSCATED_VALUE = "******";

    /** The maximum number of times we try to confirm. */
    public static final int CONFIRMATION_MAX_TRIES = 5;

    /**
     * The date format string that will be used to construct and parse dates represented using generalized time. It is
     * assumed that the provided date formatter will be set to UTC.
     */
    public static final String DATE_FORMAT_LOCAL_TIME = "dd/MMM/yyyy:HH:mm:ss Z";

    /**
     * Returns the message to be displayed in the file with the equivalent command-line with information about the
     * current time.
     *
     * @return the message to be displayed in the file with the equivalent command-line with information about the
     *         current time.
     */
    public static String getCurrentOperationDateMessage() {
        String date = formatDateTimeStringForEquivalentCommand(new Date());
        return INFO_OPERATION_START_TIME_MESSAGE.get(date).toString();
    }

    private static final String COMMENT_SHELL_UNIX = "# ";
    private static final String COMMENT_BATCH_WINDOWS = "rem ";

    /** The String used to write comments in a shell (or batch) script. */
    public static final String SHELL_COMMENT_SEPARATOR = OperatingSystem.isWindows() ? COMMENT_BATCH_WINDOWS
            : COMMENT_SHELL_UNIX;

    /** The column at which to wrap long lines of output in the command-line tools. */
    public static final int MAX_LINE_WIDTH;
    static {
        int columns = 80;
        try {
            final String s = System.getenv("COLUMNS");
            if (s != null) {
                columns = Integer.parseInt(s);
            }
        } catch (final Exception e) {
            // Do nothing.
        }
        MAX_LINE_WIDTH = columns - 1;
    }

    /**
     * Formats a Date to String representation in "dd/MMM/yyyy:HH:mm:ss Z".
     *
     * @param date
     *            The date to format; null if <code>date</code> is null.
     * @return A string representation of the date.
     */
    public static String formatDateTimeStringForEquivalentCommand(final Date date) {
        if (date != null) {
            final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_LOCAL_TIME);
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            return dateFormat.format(date);
        }
        return null;
    }

    /**
     * Filters the provided value to ensure that it is appropriate for use as an
     * exit code. Exit code values are generally only allowed to be between 0
     * and 255, so any value outside of this range will be converted to 255,
     * which is the typical exit code used to indicate an overflow value.
     *
     * @param exitCode
     *            The exit code value to be processed.
     * @return An integer value between 0 and 255, inclusive. If the provided
     *         exit code was already between 0 and 255, then the original value
     *         will be returned. If the provided value was out of this range,
     *         then 255 will be returned.
     */
    public static int filterExitCode(final int exitCode) {
        if (exitCode < 0) {
            return 255;
        } else if (exitCode > 255) {
            return 255;
        } else {
            return exitCode;
        }
    }

    /**
     * Read the data from the specified file and return it in a byte array.
     *
     * @param filePath
     *            The path to the file that should be read.
     * @return A byte array containing the contents of the requested file.
     * @throws IOException
     *             If a problem occurs while trying to read the specified file.
     */
    public static byte[] readBytesFromFile(final String filePath) throws IOException {
        byte[] val = null;
        FileInputStream fis = null;
        try {
            final File file = new File(filePath);
            fis = new FileInputStream(file);
            final long length = file.length();
            val = new byte[(int) length];
            // Read in the bytes
            int offset = 0;
            int numRead = 0;
            while (offset < val.length
                    && (numRead = fis.read(val, offset, val.length - offset)) >= 0) {
                offset += numRead;
            }

            // Ensure all the bytes have been read in
            if (offset < val.length) {
                throw new IOException("Could not completely read file " + filePath);
            }

            return val;
        } finally {
            if (fis != null) {
                fis.close();
            }
        }
    }

    /**
     * Retrieves a user-friendly string that indicates the length of time (in
     * days, hours, minutes, and seconds) in the specified number of seconds.
     *
     * @param numSeconds
     *            The number of seconds to be converted to a more user-friendly
     *            value.
     * @return The user-friendly representation of the specified number of
     *         seconds.
     */
    public static LocalizableMessage secondsToTimeString(final int numSeconds) {
        if (numSeconds < 60) {
            // We can express it in seconds.
            return INFO_TIME_IN_SECONDS.get(numSeconds);
        } else if (numSeconds < 3600) {
            // We can express it in minutes and seconds.
            final int m = numSeconds / 60;
            final int s = numSeconds % 60;
            return INFO_TIME_IN_MINUTES_SECONDS.get(m, s);
        } else if (numSeconds < 86400) {
            // We can express it in hours, minutes, and seconds.
            final int h = numSeconds / 3600;
            final int m = (numSeconds % 3600) / 60;
            final int s = numSeconds % 3600 % 60;
            return INFO_TIME_IN_HOURS_MINUTES_SECONDS.get(h, m, s);
        } else {
            // We can express it in days, hours, minutes, and seconds.
            final int d = numSeconds / 86400;
            final int h = (numSeconds % 86400) / 3600;
            final int m = (numSeconds % 86400 % 3600) / 60;
            final int s = numSeconds % 86400 % 3600 % 60;
            return INFO_TIME_IN_DAYS_HOURS_MINUTES_SECONDS.get(d, h, m, s);
        }
    }

    /**
     * Inserts line breaks into the provided buffer to wrap text at no more than
     * the specified column width. Wrapping will only be done at space
     * boundaries and if there are no spaces within the specified width, then
     * wrapping will be performed at the first space after the specified column.
     *
     * @param message
     *            The message to be wrapped.
     * @param width
     *            The maximum number of characters to allow on a line if there
     *            is a suitable breaking point.
     * @return The wrapped text.
     */
    public static String wrapText(final LocalizableMessage message, final int width) {
        return wrapText(message.toString(), width, 0);
    }

    /**
     * Inserts line breaks into the provided buffer to wrap text at no more than
     * the specified column width. Wrapping will only be done at space
     * boundaries and if there are no spaces within the specified width, then
     * wrapping will be performed at the first space after the specified column.
     * In addition each line will be indented by the specified amount.
     *
     * @param message
     *            The message to be wrapped.
     * @param width
     *            The maximum number of characters to allow on a line if there
     *            is a suitable breaking point (including any indentation).
     * @param indent
     *            The number of columns to indent each line.
     * @return The wrapped text.
     */
    public static String wrapText(final LocalizableMessage message, final int width, final int indent) {
        return wrapText(message.toString(), width, indent);
    }

    /**
     * Inserts line breaks into the provided buffer to wrap text at no more than
     * the specified column width. Wrapping will only be done at space
     * boundaries and if there are no spaces within the specified width, then
     * wrapping will be performed at the first space after the specified column.
     *
     * @param text
     *            The text to be wrapped.
     * @param width
     *            The maximum number of characters to allow on a line if there
     *            is a suitable breaking point.
     * @return The wrapped text.
     */
    public static String wrapText(final String text, final int width) {
        return wrapText(text, width, 0);
    }

    /**
     * Inserts line breaks into the provided buffer to wrap text at no more than
     * the specified column width. Wrapping will only be done at space
     * boundaries and if there are no spaces within the specified width, then
     * wrapping will be performed at the first space after the specified column.
     * In addition each line will be indented by the specified amount.
     *
     * @param text
     *            The text to be wrapped.
     * @param width
     *            The maximum number of characters to allow on a line if there
     *            is a suitable breaking point (including any indentation).
     * @param indent
     *            The number of columns to indent each line.
     * @return The wrapped text.
     */
    public static String wrapText(final String text, int width, final int indent) {
        if (text == null) {
            return "";
        }

        // Calculate the real width and indentation padding.
        width -= indent;
        final StringBuilder pb = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            pb.append(' ');
        }
        final String padding = pb.toString();

        final StringBuilder buffer = new StringBuilder();
        final StringTokenizer lineTokenizer = new StringTokenizer(text, "\r\n", true);
        while (lineTokenizer.hasMoreTokens()) {
            final String line = lineTokenizer.nextToken();
            if ("\r".equals(line) || "\n".equals(line)) {
                // It's an end-of-line character, so append it as-is.
                buffer.append(line);
            } else if (line.length() <= width) {
                // The line fits in the specified width, so append it as-is.
                buffer.append(padding);
                buffer.append(line);
            } else {
                // The line doesn't fit in the specified width, so it needs
                // to be wrapped. Do so at space boundaries.
                StringBuilder lineBuffer = new StringBuilder();
                StringBuilder delimBuffer = new StringBuilder();
                final StringTokenizer wordTokenizer = new StringTokenizer(line, " ", true);
                while (wordTokenizer.hasMoreTokens()) {
                    final String word = wordTokenizer.nextToken();
                    if (" ".equals(word)) {
                        // It's a space, so add it to the delim buffer only
                        // if the line buffer is not empty.
                        if (lineBuffer.length() > 0) {
                            delimBuffer.append(word);
                        }
                    } else if (word.length() > width) {
                        // This is a long word that can't be wrapped,
                        // so we'll just have to make do.
                        if (lineBuffer.length() > 0) {
                            buffer.append(padding).append(lineBuffer).append(EOL);
                            lineBuffer = new StringBuilder();
                        }
                        buffer.append(padding);
                        buffer.append(word);

                        if (wordTokenizer.hasMoreTokens()) {
                            // The next token must be a space, so remove it.
                            // If there are still more tokens after that, then append an EOL.
                            wordTokenizer.nextToken();
                            if (wordTokenizer.hasMoreTokens()) {
                                buffer.append(EOL);
                            }
                        }

                        if (delimBuffer.length() > 0) {
                            delimBuffer = new StringBuilder();
                        }
                    } else {
                        // It's not a space, so see if we can fit it on the current line.
                        final int newLineLength =
                                lineBuffer.length() + delimBuffer.length() + word.length();
                        if (newLineLength < width) {
                            // It does fit on the line, so add it.
                            lineBuffer.append(delimBuffer).append(word);

                            if (delimBuffer.length() > 0) {
                                delimBuffer = new StringBuilder();
                            }
                        } else {
                            // It doesn't fit on the line, so end the
                            // current line and start a new one.
                            buffer.append(padding).append(lineBuffer).append(EOL);

                            lineBuffer = new StringBuilder();
                            lineBuffer.append(word);

                            if (delimBuffer.length() > 0) {
                                delimBuffer = new StringBuilder();
                            }
                        }
                    }
                }

                // If there's anything left in the line buffer, then add it
                // to the final buffer.
                buffer.append(padding);
                buffer.append(lineBuffer);
            }
        }
        return buffer.toString();
    }

    /**
     * Checks the java version.
     *
     * @throws ClientException
     *             If the java version we are running on is not compatible.
     */
    public static void checkJavaVersion() throws ClientException {
        final String version = System.getProperty("java.specification.version");
        if (Float.valueOf(version) < CliConstants.MINIMUM_JAVA_VERSION) {
            final String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
            throw new ClientException(ReturnCode.JAVA_VERSION_INCOMPATIBLE,
                    ERR_INCOMPATIBLE_JAVA_VERSION.get(CliConstants.MINIMUM_JAVA_VERSION, version, javaBin), null);
        }
    }

    /**
     * Returns the default host name.
     *
     * @return The default host name or empty string if the host name cannot be resolved.
     */
    public static String getDefaultHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            // Fails.
        }
        String host = System.getenv("COMPUTERNAME"); // Windows.
        if (host != null) {
            return host;
        }
        host = System.getenv("HOSTNAME"); // Unix.
        if (host != null) {
            return host;
        }
        return "";
    }

    /**
     * Tells whether the provided Throwable was caused because of a problem with a certificate while trying to establish
     * a connection.
     *
     * @param t
     *            The Throwable to analyze.
     * @return <CODE>true</CODE> if the provided Throwable was caused because of a problem with a certificate while
     *         trying to establish a connection and <CODE>false</CODE> otherwise.
     */
    public static boolean isCertificateException(Throwable t) {
        while (t != null) {
            if (t instanceof SSLHandshakeException || t instanceof GeneralSecurityException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    /**
     * Returns a message object for the given NamingException.
     *
     * @param ne
     *            The NamingException.
     * @param hostPort
     *            The hostPort representation of the server we were contacting when the NamingException occurred.
     * @return A message object for the given NamingException.
     */
    public static LocalizableMessage getMessageForException(NamingException ne, String hostPort) {
        String arg;
        if (ne.getLocalizedMessage() != null) {
            arg = ne.getLocalizedMessage();
        } else if (ne.getExplanation() != null) {
            arg = ne.getExplanation();
        } else {
            arg = ne.toString(true);
        }

        if (Utils.isCertificateException(ne)) {
            return INFO_ERROR_READING_CONFIG_LDAP_CERTIFICATE_SERVER.get(hostPort, arg);
        } else if (ne instanceof AuthenticationException) {
            return INFO_CANNOT_CONNECT_TO_REMOTE_AUTHENTICATION.get(hostPort, arg);
        } else if (ne instanceof NoPermissionException) {
            return INFO_CANNOT_CONNECT_TO_REMOTE_PERMISSIONS.get(hostPort, arg);
        } else if (ne instanceof NamingSecurityException) {
            return INFO_CANNOT_CONNECT_TO_REMOTE_PERMISSIONS.get(hostPort, arg);
        } else if (ne instanceof CommunicationException) {
            return ERR_CANNOT_CONNECT_TO_REMOTE_COMMUNICATION.get(hostPort, arg);
        } else {
            return INFO_CANNOT_CONNECT_TO_REMOTE_GENERIC.get(hostPort, arg);
        }
    }

    /**
     * Returns a localized message for a given properties key an throwable.
     *
     * @param message
     *            prefix
     * @param t
     *            the throwable for which we want to get a message.
     * @return a localized message for a given properties key and throwable.
     */
    public static LocalizableMessage getThrowableMsg(final LocalizableMessage message, final Throwable t) {
        LocalizableMessageDescriptor.Arg1<Object> tag;
        if (isOutOfMemory(t)) {
            tag = INFO_EXCEPTION_OUT_OF_MEMORY_DETAILS;
        } else {
            tag = INFO_EXCEPTION_DETAILS;
        }
        final LocalizableMessageBuilder mb = new LocalizableMessageBuilder(message);
        String detail = t.toString();
        if (detail != null) {
            mb.append("  ").append(tag.get(detail));
        }
        return mb.toMessage();
    }

    /**
     * Returns <CODE>true</CODE> if we can write on the provided path and <CODE>false</CODE> otherwise.
     *
     * @param path
     *            the path.
     * @return <CODE>true</CODE> if we can write on the provided path and <CODE>false</CODE> otherwise.
     */
    public static boolean canWrite(String path) {
        final File file = new File(path);
        if (file.exists()) {
            return file.canWrite();
        }
        final File parentFile = file.getParentFile();
        return parentFile != null && parentFile.canWrite();
    }

    /** Prevent instantiation. */
    private Utils() {
        // Do nothing.
    }

    /**
     * Returns {@code true} if the the provided string is a DN and {@code false} otherwise.
     *
     * @param dn
     *            The String we are analyzing.
     * @return {@code true} if the the provided string is a DN and {@code false} otherwise.
     */
    public static boolean isDN(String dn) {
        try {
            DN.valueOf(dn);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Returns the DN of the global administrator for a given UID.
     *
     * @param uid
     *            The UID to be used to generate the DN.
     * @return The DN of the administrator for the given UID.
     */
    public static String getAdministratorDN(String uid) {
        return RDN.valueOf("cn=" + uid) + ",cn=Administrators, cn=admin data";
    }

    /**
     * Tells whether this throwable has been generated for an out of memory error or not.
     *
     * @param t
     *            The throwable to analyze.
     * @return {@code true} if the throwable was generated by an out of memory error and false otherwise.
     */
    private static boolean isOutOfMemory(Throwable t) {
        boolean isOutOfMemory = false;
        while (!isOutOfMemory && t != null) {
            if (t instanceof OutOfMemoryError) {
                isOutOfMemory = true;
            } else if (t instanceof IOException) {
                final String msg = t.toString();
                if (msg != null) {
                    isOutOfMemory = msg.contains("Not enough space");
                }
            }
            t = t.getCause();
        }
        return isOutOfMemory;
    }

    /**
     * Returns the string that can be used to represent a given host name in a LDAP URL.
     * This method must be used when we have IPv6 addresses (the address in the LDAP URL
     *  must be enclosed with brackets).
     *  E.g:<pre>
     *  -h "[2a01:e35:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx]"
     *  </pre>
     *
     * @param host
     *            The host name.
     * @return The String that can be used to represent a given host name in a LDAP URL.
     */
    public static String getHostNameForLdapUrl(String host) {
        if (host != null && host.contains(":")) {
            // Assume an IPv6 address has been specified and adds the brackets
            // for the URL.
            host = host.trim();
            if (!host.startsWith("[")) {
                host = "[" + host;
            }
            if (!host.endsWith("]")) {
                host = host + "]";
            }
        }
        return host;
    }

    /**
     * Prints the provided string on the provided stream.
     *
     * @param stream
     *            The stream to print the message.
     * @param message
     *            The message to print.
     */
    public static void printWrappedText(final PrintStream stream, final String message) {
        if (stream != null && message != null && !message.isEmpty()) {
            stream.println(wrapText(message, MAX_LINE_WIDTH));
        }
    }

    /**
     * Print the provided message on the provided stream.
     *
     * @param stream
     *            The stream to print the message.
     * @param message
     *            The message to print.
     */
    public static void printWrappedText(final PrintStream stream, final LocalizableMessage message) {
        printWrappedText(stream, message != null ? message.toString() : null);
    }
}
