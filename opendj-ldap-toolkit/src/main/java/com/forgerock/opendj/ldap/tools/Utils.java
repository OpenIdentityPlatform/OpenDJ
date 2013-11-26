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
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;
import static com.forgerock.opendj.util.StaticUtils.EOL;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.StringTokenizer;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.controls.AuthorizationIdentityRequestControl;
import org.forgerock.opendj.ldap.controls.AuthorizationIdentityResponseControl;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.forgerock.opendj.ldap.controls.GetEffectiveRightsRequestControl;
import org.forgerock.opendj.ldap.controls.PasswordExpiredResponseControl;
import org.forgerock.opendj.ldap.controls.PasswordExpiringResponseControl;
import org.forgerock.opendj.ldap.controls.PasswordPolicyErrorType;
import org.forgerock.opendj.ldap.controls.PasswordPolicyRequestControl;
import org.forgerock.opendj.ldap.controls.PasswordPolicyResponseControl;
import org.forgerock.opendj.ldap.controls.PasswordPolicyWarningType;
import org.forgerock.opendj.ldap.controls.SubtreeDeleteRequestControl;
import org.forgerock.opendj.ldap.responses.BindResult;

import com.forgerock.opendj.ldap.controls.AccountUsabilityRequestControl;
import com.forgerock.opendj.ldap.tools.AuthenticatedConnectionFactory.AuthenticatedConnection;
import com.forgerock.opendj.util.StaticUtils;

/**
 * This class provides utility functions for all the client side tools.
 */
final class Utils {
    /**
     * The name of a command-line script used to launch a tool.
     */
    static final String PROPERTY_SCRIPT_NAME = "com.forgerock.opendj.ldap.tools.scriptName";

    /**
     * The column at which to wrap long lines of output in the command-line
     * tools.
     */
    static final int MAX_LINE_WIDTH;

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
    static int filterExitCode(final int exitCode) {
        if (exitCode < 0) {
            return 255;
        } else if (exitCode > 255) {
            return 255;
        } else {
            return exitCode;
        }
    }

    /**
     * Parse the specified command line argument to create the appropriate
     * LDAPControl. The argument string should be in the format
     * controloid[:criticality[:value|::b64value|:&lt;fileurl]]
     *
     * @param argString
     *            The argument string containing the encoded control
     *            information.
     * @return The control decoded from the provided string, or
     *         <CODE>null</CODE> if an error occurs while parsing the argument
     *         value.
     * @throws org.forgerock.opendj.ldap.DecodeException
     *             If an error occurs.
     */
    static GenericControl getControl(final String argString) throws DecodeException {
        String controlOID = null;
        boolean controlCriticality = false;
        ByteString controlValue = null;

        int idx = argString.indexOf(":");

        if (idx < 0) {
            controlOID = argString;
        } else {
            controlOID = argString.substring(0, idx);
        }

        final String lowerOID = StaticUtils.toLowerCase(controlOID);
        if (lowerOID.equals("accountusable") || lowerOID.equals("accountusability")) {
            controlOID = AccountUsabilityRequestControl.OID;
        } else if (lowerOID.equals("authzid") || lowerOID.equals("authorizationidentity")) {
            controlOID = AuthorizationIdentityRequestControl.OID;
        } else if (lowerOID.equals("noop") || lowerOID.equals("no-op")) {
            // controlOID = OID_LDAP_NOOP_OPENLDAP_ASSIGNED;
        } else if (lowerOID.equals("subentries")) {
            // controlOID = OID_LDAP_SUBENTRIES;
        } else if (lowerOID.equals("managedsait")) {
            // controlOID = OID_MANAGE_DSAIT_CONTROL;
        } else if (lowerOID.equals("pwpolicy") || lowerOID.equals("passwordpolicy")) {
            controlOID = PasswordPolicyRequestControl.OID;
        } else if (lowerOID.equals("subtreedelete") || lowerOID.equals("treedelete")) {
            controlOID = SubtreeDeleteRequestControl.OID;
        } else if (lowerOID.equals("realattrsonly") || lowerOID.equals("realattributesonly")) {
            // controlOID = OID_REAL_ATTRS_ONLY;
        } else if (lowerOID.equals("virtualattrsonly") || lowerOID.equals("virtualattributesonly")) {
            // controlOID = OID_VIRTUAL_ATTRS_ONLY;
        } else if (lowerOID.equals("effectiverights") || lowerOID.equals("geteffectiverights")) {
            controlOID = GetEffectiveRightsRequestControl.OID;
        }

        if (idx < 0) {
            return GenericControl.newControl(controlOID);
        }

        final String remainder = argString.substring(idx + 1, argString.length());

        idx = remainder.indexOf(":");
        if (idx == -1) {
            if (remainder.equalsIgnoreCase("true")) {
                controlCriticality = true;
            } else if (remainder.equalsIgnoreCase("false")) {
                controlCriticality = false;
            } else {
                // TODO: I18N
                throw DecodeException.error(LocalizableMessage
                        .raw("Invalid format for criticality value:" + remainder));
            }
            return GenericControl.newControl(controlOID, controlCriticality);

        }

        final String critical = remainder.substring(0, idx);
        if (critical.equalsIgnoreCase("true")) {
            controlCriticality = true;
        } else if (critical.equalsIgnoreCase("false")) {
            controlCriticality = false;
        } else {
            // TODO: I18N
            throw DecodeException.error(LocalizableMessage
                    .raw("Invalid format for criticality value:" + critical));
        }

        final String valString = remainder.substring(idx + 1, remainder.length());
        if (valString.charAt(0) == ':') {
            controlValue = ByteString.valueOf(valString.substring(1, valString.length()));
        } else if (valString.charAt(0) == '<') {
            // Read data from the file.
            final String filePath = valString.substring(1, valString.length());
            try {
                final byte[] val = readBytesFromFile(filePath);
                controlValue = ByteString.wrap(val);
            } catch (final Exception e) {
                return null;
            }
        } else {
            controlValue = ByteString.valueOf(valString);
        }

        return GenericControl.newControl(controlOID, controlCriticality, controlValue);
    }

    /**
     * Prints a multi-line error message with the provided information to the
     * given print stream.
     *
     * @param app
     *            The console app to use to write the error message.
     * @param ere
     *            The error result.
     * @return The error code.
     */
    static int printErrorMessage(final ConsoleApplication app, final ErrorResultException ere) {
        // if ((ere.getMessage() != null) && (ere.getMessage().length() >
        // 0))
        // {
        // app.println(LocalizableMessage.raw(ere.getMessage()));
        // }

        if (ere.getResult().getResultCode().intValue() >= 0) {
            app.println(ERR_TOOL_RESULT_CODE.get(ere.getResult().getResultCode().intValue(), ere
                    .getResult().getResultCode().toString()));
        }

        if ((ere.getResult().getDiagnosticMessage() != null)
                && (ere.getResult().getDiagnosticMessage().length() > 0)) {
            app.println(ERR_TOOL_ERROR_MESSAGE.get(ere.getResult().getDiagnosticMessage()));
        }

        if (ere.getResult().getMatchedDN() != null && ere.getResult().getMatchedDN().length() > 0) {
            app.println(ERR_TOOL_MATCHED_DN.get(ere.getResult().getMatchedDN()));
        }

        if (app.isVerbose() && ere.getResult().getCause() != null) {
            ere.getResult().getCause().printStackTrace(app.getErrorStream());
        }

        return ere.getResult().getResultCode().intValue();
    }

    static void printPasswordPolicyResults(final ConsoleApplication app, final Connection connection) {
        if (connection instanceof AuthenticatedConnection) {
            final AuthenticatedConnection conn = (AuthenticatedConnection) connection;
            final BindResult result = conn.getAuthenticatedBindResult();

            try {
                final AuthorizationIdentityResponseControl control =
                        result.getControl(AuthorizationIdentityResponseControl.DECODER,
                                new DecodeOptions());
                if (control != null) {
                    final LocalizableMessage message =
                            INFO_BIND_AUTHZID_RETURNED.get(control.getAuthorizationID());
                    app.println(message);
                }
            } catch (final DecodeException e) {
                app.println(ERR_DECODE_CONTROL_FAILURE.get(e.getLocalizedMessage()));
            }

            try {
                final PasswordExpiredResponseControl control =
                        result.getControl(PasswordExpiredResponseControl.DECODER,
                                new DecodeOptions());
                if (control != null) {
                    final LocalizableMessage message = INFO_BIND_PASSWORD_EXPIRED.get();
                    app.println(message);
                }
            } catch (final DecodeException e) {
                app.println(ERR_DECODE_CONTROL_FAILURE.get(e.getLocalizedMessage()));
            }

            try {
                final PasswordExpiringResponseControl control =
                        result.getControl(PasswordExpiringResponseControl.DECODER,
                                new DecodeOptions());
                if (control != null) {
                    final LocalizableMessage timeString =
                            Utils.secondsToTimeString(control.getSecondsUntilExpiration());
                    final LocalizableMessage message = INFO_BIND_PASSWORD_EXPIRING.get(timeString);
                    app.println(message);
                }
            } catch (final DecodeException e) {
                app.println(ERR_DECODE_CONTROL_FAILURE.get(e.getLocalizedMessage()));
            }

            try {
                final PasswordPolicyResponseControl control =
                        result.getControl(PasswordPolicyResponseControl.DECODER,
                                new DecodeOptions());
                if (control != null) {
                    final PasswordPolicyErrorType errorType = control.getErrorType();
                    if (errorType == PasswordPolicyErrorType.PASSWORD_EXPIRED) {
                        final LocalizableMessage message = INFO_BIND_PASSWORD_EXPIRED.get();
                        app.println(message);
                    } else if (errorType == PasswordPolicyErrorType.ACCOUNT_LOCKED) {
                        final LocalizableMessage message = INFO_BIND_ACCOUNT_LOCKED.get();
                        app.println(message);
                    } else if (errorType == PasswordPolicyErrorType.CHANGE_AFTER_RESET) {

                        final LocalizableMessage message = INFO_BIND_MUST_CHANGE_PASSWORD.get();
                        app.println(message);
                    }

                    final PasswordPolicyWarningType warningType = control.getWarningType();
                    if (warningType == PasswordPolicyWarningType.TIME_BEFORE_EXPIRATION) {
                        final LocalizableMessage timeString =
                                Utils.secondsToTimeString(control.getWarningValue());
                        final LocalizableMessage message =
                                INFO_BIND_PASSWORD_EXPIRING.get(timeString);
                        app.println(message);
                    } else if (warningType == PasswordPolicyWarningType.GRACE_LOGINS_REMAINING) {
                        final LocalizableMessage message =
                                INFO_BIND_GRACE_LOGINS_REMAINING.get(control.getWarningValue());
                        app.println(message);
                    }
                }
            } catch (final DecodeException e) {
                app.println(ERR_DECODE_CONTROL_FAILURE.get(e.getLocalizedMessage()));
            }
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
    static byte[] readBytesFromFile(final String filePath) throws IOException {
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
    static LocalizableMessage secondsToTimeString(final int numSeconds) {
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
     * Sets default system property settings for the xxxrate performance tools.
     */
    static void setDefaultPerfToolProperties() {
        // Use SameThreadStrategy by default.
        if (System.getProperty("org.forgerock.opendj.transport.useWorkerThreads") == null) {
            System.setProperty("org.forgerock.opendj.transport.useWorkerThreads", "false");
        }

        // Configure connections to be terminate immediately after closing (this
        // prevents port exhaustion in xxxrate tools when
        // connecting/disconnecting).
        if (System.getProperty("org.forgerock.opendj.transport.linger") == null) {
            System.setProperty("org.forgerock.opendj.transport.linger", "0");
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
    static String wrapText(final LocalizableMessage message, final int width) {
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
    static String wrapText(final LocalizableMessage message, final int width, final int indent) {
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
    static String wrapText(final String text, final int width) {
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
    static String wrapText(final String text, int width, final int indent) {
        // Calculate the real width and indentation padding.
        width -= indent;
        final StringBuilder pb = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            pb.append(' ');
        }
        final String padding = pb.toString();

        final StringBuilder buffer = new StringBuilder();
        if (text != null) {
            final StringTokenizer lineTokenizer = new StringTokenizer(text, "\r\n", true);
            while (lineTokenizer.hasMoreTokens()) {
                final String line = lineTokenizer.nextToken();
                if (line.equals("\r") || line.equals("\n")) {
                    // It's an end-of-line character, so append it as-is.
                    buffer.append(line);
                } else if (line.length() <= width) {
                    // The line fits in the specified width, so append it as-is.
                    buffer.append(padding);
                    buffer.append(line);
                } else {
                    // The line doesn't fit in the specified width, so it needs
                    // to
                    // be
                    // wrapped. Do so at space boundaries.
                    StringBuilder lineBuffer = new StringBuilder();
                    StringBuilder delimBuffer = new StringBuilder();
                    final StringTokenizer wordTokenizer = new StringTokenizer(line, " ", true);
                    while (wordTokenizer.hasMoreTokens()) {
                        final String word = wordTokenizer.nextToken();
                        if (word.equals(" ")) {
                            // It's a space, so add it to the delim buffer only
                            // if the
                            // line
                            // buffer is not empty.
                            if (lineBuffer.length() > 0) {
                                delimBuffer.append(word);
                            }
                        } else if (word.length() > width) {
                            // This is a long word that can't be wrapped, so
                            // we'll
                            // just have
                            // to make do.
                            if (lineBuffer.length() > 0) {
                                buffer.append(padding);
                                buffer.append(lineBuffer);
                                buffer.append(EOL);
                                lineBuffer = new StringBuilder();
                            }
                            buffer.append(padding);
                            buffer.append(word);

                            if (wordTokenizer.hasMoreTokens()) {
                                // The next token must be a space, so remove it.
                                // If
                                // there are
                                // still more tokens after that, then append an
                                // EOL.
                                wordTokenizer.nextToken();
                                if (wordTokenizer.hasMoreTokens()) {
                                    buffer.append(EOL);
                                }
                            }

                            if (delimBuffer.length() > 0) {
                                delimBuffer = new StringBuilder();
                            }
                        } else {
                            // It's not a space, so see if we can fit it on the
                            // curent
                            // line.
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
                                // current line
                                // and start
                                // a new one.
                                buffer.append(padding);
                                buffer.append(lineBuffer);
                                buffer.append(EOL);

                                lineBuffer = new StringBuilder();
                                lineBuffer.append(word);

                                if (delimBuffer.length() > 0) {
                                    delimBuffer = new StringBuilder();
                                }
                            }
                        }
                    }

                    // If there's anything left in the line buffer, then add it
                    // to
                    // the
                    // final buffer.
                    buffer.append(padding);
                    buffer.append(lineBuffer);
                }
            }
        }
        return buffer.toString();
    }

    // Prevent instantiation.
    private Utils() {
        // Do nothing.
    }
}
