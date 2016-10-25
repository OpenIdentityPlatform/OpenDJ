/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions copyright 2014-2016 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.cli.ArgumentConstants.USE_SYSTEM_STREAM_TOKEN;
import static com.forgerock.opendj.cli.CliConstants.NO_WRAPPING_BY_DEFAULT;
import static com.forgerock.opendj.cli.Utils.filterExitCode;
import static com.forgerock.opendj.cli.Utils.readBytesFromFile;
import static com.forgerock.opendj.cli.Utils.secondsToTimeString;
import static com.forgerock.opendj.ldap.tools.LDAPToolException.newToolException;
import static com.forgerock.opendj.ldap.tools.LDAPToolException.newToolExceptionAlreadyPrinted;
import static com.forgerock.opendj.ldap.tools.LDAPToolException.newToolParamException;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.StringArgument;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageDescriptor;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.AssertionRequestControl;
import org.forgerock.opendj.ldap.controls.AuthorizationIdentityRequestControl;
import org.forgerock.opendj.ldap.controls.AuthorizationIdentityResponseControl;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.forgerock.opendj.ldap.controls.GetEffectiveRightsRequestControl;
import org.forgerock.opendj.ldap.controls.PasswordExpiredResponseControl;
import org.forgerock.opendj.ldap.controls.PasswordExpiringResponseControl;
import org.forgerock.opendj.ldap.controls.PasswordPolicyErrorType;
import org.forgerock.opendj.ldap.controls.PasswordPolicyRequestControl;
import org.forgerock.opendj.ldap.controls.PasswordPolicyResponseControl;
import org.forgerock.opendj.ldap.controls.PasswordPolicyWarningType;
import org.forgerock.opendj.ldap.controls.SubtreeDeleteRequestControl;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.Request;
import org.forgerock.opendj.ldap.responses.BindResult;

import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.ldap.controls.AccountUsabilityRequestControl;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.util.annotations.VisibleForTesting;

/**
 * This class provides utility functions for all the client side tools.
 */
final class Utils {

    static int printErrorMessage(final ConsoleApplication app, final LdapException ldapException) {
        return printErrorMessage(app, ldapException, null);
    }

    static int printErrorMessage(final ConsoleApplication app,
            final LdapException ldapException, final LocalizableMessageDescriptor.Arg2<Number, Object> errorMsg) {
        return printErrorMessage(app, ldapException.getResult(), errorMsg);
    }

    /**
     * Prints a multi-line error message with the provided information to the
     * given print stream.
     *
     * @param app
     *            The console app to use to write the error message.
     * @param result
     *            The error result.
     * @param errorMsg
     *            The error message associated to the application to use to display error result code and label.
     * @return The error code.
     */
    static int printErrorMessage(final ConsoleApplication app,
            final Result result, final LocalizableMessageDescriptor.Arg2<Number, Object> errorMsg) {
        final ResultCode resultCode = result.getResultCode();
        final int rc = resultCode.intValue();

        if (rc != ResultCode.UNDEFINED.intValue() && errorMsg != null) {
            app.errPrintln(errorMsg.get(rc, resultCode.toString()));
        }
        printlnTextMsg(app, ERR_TOOL_ERROR_MESSAGE, result.getDiagnosticMessage());
        printlnTextMsg(app, ERR_TOOL_MATCHED_DN, result.getMatchedDN());

        final Throwable cause = result.getCause();
        if (app.isVerbose() && cause != null) {
            cause.printStackTrace(app.getErrorStream());
        }

        return rc;
    }

    static void printSuccessMessage(
            final ConsoleApplication app, final Result r, final String operationType, final String dn) {
        app.println(INFO_OPERATION_SUCCESSFUL.get(operationType, dn));
        printlnTextMsg(app, r.getDiagnosticMessage());
        for (final String uri : r.getReferralURIs()) {
            app.println(LocalizableMessage.raw(uri));
        }
    }

    static void printPasswordPolicyResults(final ConsoleApplication app, final BindResult result) {
        try {
            final AuthorizationIdentityResponseControl control = result.getControl(
                    AuthorizationIdentityResponseControl.DECODER, new DecodeOptions());
            if (control != null) {
                app.println(INFO_BIND_AUTHZID_RETURNED.get(control.getAuthorizationID()));
            }
        } catch (final DecodeException e) {
            app.errPrintln(ERR_DECODE_CONTROL_FAILURE.get(e.getLocalizedMessage()));
        }

        try {
            final PasswordExpiredResponseControl control = result.getControl(
                    PasswordExpiredResponseControl.DECODER, new DecodeOptions());
            if (control != null) {
                app.println(INFO_BIND_PASSWORD_EXPIRED.get());
            }
        } catch (final DecodeException e) {
            app.errPrintln(ERR_DECODE_CONTROL_FAILURE.get(e.getLocalizedMessage()));
        }

        try {
            final PasswordExpiringResponseControl control = result.getControl(
                    PasswordExpiringResponseControl.DECODER, new DecodeOptions());
            if (control != null) {
                app.println(INFO_BIND_PASSWORD_EXPIRING.get(secondsToTimeString(control.getSecondsUntilExpiration())));
            }
        } catch (final DecodeException e) {
            app.errPrintln(ERR_DECODE_CONTROL_FAILURE.get(e.getLocalizedMessage()));
        }

        try {
            final PasswordPolicyResponseControl control = result.getControl(
                    PasswordPolicyResponseControl.DECODER, new DecodeOptions());
            if (control != null) {
                printPasswordPolicyError(control.getErrorType(), app);
                printPasswordPolicyWarning(control.getWarningType(), control.getWarningValue(), app);
            }
        } catch (final DecodeException e) {
            app.errPrintln(ERR_DECODE_CONTROL_FAILURE.get(e.getLocalizedMessage()));
        }
    }

    private static void printPasswordPolicyError(final PasswordPolicyErrorType errorType,
                                                 final ConsoleApplication app) {
        if (errorType == null) {
            return;
        }

        switch (errorType) {
        case PASSWORD_EXPIRED:
            app.println(INFO_BIND_PASSWORD_EXPIRED.get());
            break;
        case ACCOUNT_LOCKED:
            app.println(INFO_BIND_ACCOUNT_LOCKED.get());
            break;
        case CHANGE_AFTER_RESET:
            app.println(INFO_BIND_MUST_CHANGE_PASSWORD.get());
            break;
        }
    }

    private static void printPasswordPolicyWarning(final PasswordPolicyWarningType warningType,
                                                   final int warningValue,
                                                   final ConsoleApplication app) {
        if (warningType == null) {
            return;
        }

        switch (warningType) {
        case TIME_BEFORE_EXPIRATION:
            app.println(INFO_BIND_PASSWORD_EXPIRING.get(secondsToTimeString(warningValue)));
            break;
        case GRACE_LOGINS_REMAINING:
            app.println(INFO_BIND_GRACE_LOGINS_REMAINING.get(warningValue));
            break;
        }
    }

    /**
     * Sets default system property settings for the xxxrate performance tools.
     */
    static void setDefaultPerfToolProperties() {
        /* Configure connections to be terminate immediately after closing (this
         prevents port exhaustion in xxxrate tools when
         connecting/disconnecting).*/
        if (System.getProperty("org.forgerock.opendj.transport.linger") == null) {
            System.setProperty("org.forgerock.opendj.transport.linger", "0");
        }
    }

    static Filter readFilterFromString(final String filterStr) throws LDAPToolException {
        try {
            return Filter.valueOf(filterStr);
        } catch (final LocalizedIllegalArgumentException e) {
            throw newToolException(e, ResultCode.CLIENT_SIDE_FILTER_ERROR, e.getMessageObject());
        }
    }

    static void parseArguments(final ArgumentParser argParser, final PrintStream stream, final String[] args)
            throws LDAPToolException {
        try {
            argParser.parseArguments(args);
        } catch (final ArgumentException e) {
            argParser.displayMessageAndUsageReference(stream, ERR_ERROR_PARSING_ARGS.get(e.getMessage()));
            throw newToolExceptionAlreadyPrinted(e, ResultCode.CLIENT_SIDE_PARAM_ERROR);
        }
    }

    static InputStream getLDIFToolInputStream(final ConsoleApplication app, final String filePath)
            throws LDAPToolException {
        if (!USE_SYSTEM_STREAM_TOKEN.equals(filePath)) {
            try {
                return new FileInputStream(filePath);
            } catch (final FileNotFoundException e) {
                throw newToolParamException(
                        e, ERR_LDIF_FILE_CANNOT_OPEN_FOR_READ.get(filePath, e.getLocalizedMessage()));
            }
        } else {
            return app.getInputStream();
        }
    }

    static OutputStream getLDIFToolOutputStream(final ConsoleApplication app, final StringArgument outputFileArg)
            throws LDAPToolException {
        final String filePath = outputFileArg.getValue();
        if (outputFileArg.isPresent() && !USE_SYSTEM_STREAM_TOKEN.equals(filePath)) {
            try {
                return new FileOutputStream(filePath);
            } catch (final FileNotFoundException e) {
                throw newToolParamException(
                        e, ERR_LDIF_FILE_CANNOT_OPEN_FOR_WRITE.get(filePath, e.getLocalizedMessage()));
            }
        } else {
            return app.getOutputStream();
        }
    }

    /**
     * Return the content of all file which path are provided in the {@link List<String>}.
     *
     * @param filePaths
     *         The list of the paths of the files to get content from.
     * @return The aggregated content of the files in a {@link List<String>}.
     */
    static List<String> getLinesFromFiles(final List<String> filePaths) throws LDAPToolException {
        final List<String> filesLines = new ArrayList<>();

        for (final String filePath : filePaths) {
            try (final BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    filesLines.add(line);
                }
                filesLines.add("");
            } catch (final IOException e) {
                throw newToolParamException(
                        e, ERR_LDIF_FILE_CANNOT_OPEN_FOR_READ.get(filePath, e.getLocalizedMessage()));
            }
        }
        return filesLines;
    }

    static void addControlsToRequest(final Request request, final List<Control> controls) throws LDAPToolException {
        for (final Control control : controls) {
            request.addControl(control);
        }
    }

    static List<Control> readControls(final StringArgument controlArg) throws LDAPToolException {
        final List<Control> controls = new LinkedList<>();
        if (controlArg.isPresent()) {
            for (final String ctrlString : controlArg.getValues()) {
                try {
                    controls.add(getControl(ctrlString));
                } catch (final DecodeException e) {
                    throw newToolParamException(e, ERR_TOOL_INVALID_CONTROL_STRING.get(ctrlString));
                }
            }
        }
        return controls;
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
    private static GenericControl getControl(final String argString) throws DecodeException {
        final String[] control = argString.split(":");
        final int nbControlElements = control.length;

        final String controlOID = readControlID(control[0]);
        if (nbControlElements == 1) {
            return GenericControl.newControl(controlOID);
        }

        final boolean critic = readControlCriticality(control[1], argString);
        if (nbControlElements == 2) {
            return GenericControl.newControl(controlOID, critic);
        }

        final ByteString controlValue;
        if (control[2].isEmpty()) {
            controlValue = ByteString.valueOfBase64(control[3]);
        } else if (control[2].startsWith("<")) {
            // Read data from the file.
            try {
                controlValue = ByteString.wrap(readBytesFromFile(control[2].substring(1)));
            } catch (final Exception e) {
                return null;
            }
        } else {
            controlValue = ByteString.valueOfUtf8(control[2]);
        }

        return GenericControl.newControl(controlOID, critic, controlValue);
    }

    private static String readControlID(final String controlOidStr) {
        switch (controlOidStr.toLowerCase()) {
        case "accountusable":
        case "accountusability":
            return AccountUsabilityRequestControl.OID;
        case "authzid":
        case "authorizationidentity":
            return AuthorizationIdentityRequestControl.OID;
        case "pwpolicy":
        case "passwordpolicy":
            return PasswordPolicyRequestControl.OID;
        case "treedelete":
        case "subtreedelete":
            return SubtreeDeleteRequestControl.OID;
        case "effectiverights":
        case "geteffectiverights":
            return GetEffectiveRightsRequestControl.OID;
        case "noop":
        case "no-op":
        case "subentries":
        case "managedsait":
        case "realattributesonly":
        case "realattrsonly":
        case "virtualattributesonly":
        case "virtualattrsonly":
        default:
            // TODO‌ Support these request controls once migrated in the sdk
            return controlOidStr;
        }
    }

    private static boolean readControlCriticality(final String criticalityStr, final String controlStr)
            throws DecodeException {
        if ("true".equalsIgnoreCase(criticalityStr)) {
            return true;
        } else if ("false".equalsIgnoreCase(criticalityStr)) {
            return false;
        } else {
            throw DecodeException.error(ERR_DECODE_CONTROL_CRITICALITY.get(criticalityStr, controlStr));
        }
    }

    static Control readAssertionControl(final String assertionFilter)
            throws LDAPToolException {
        try {
            // FIXME -- Change this to the correct OID when the official one is assigned.
            return AssertionRequestControl.newControl(true, Filter.valueOf(assertionFilter));
        } catch (final LocalizedIllegalArgumentException e) {
            throw newToolParamException(e, ERR_LDAP_ASSERTION_INVALID_FILTER.get(e.getMessage()));
        }
    }

    static Connection getConnection(final ConnectionFactory connectionFactory, final BindRequest bindRequest,
            final BooleanArgument dryRunArg, final ConsoleApplication app) throws LDAPToolException {
        if (!dryRunArg.isPresent()) {
            try {
                final Connection connection = connectionFactory.getConnection();
                if (bindRequest != null) {
                    printPasswordPolicyResults(app, connection.bind(bindRequest));
                }
                return connection;
            } catch (final LdapException e) {
                printErrorMessage(app, e, ERR_LDAPP_BIND_FAILED);
                throw newToolExceptionAlreadyPrinted(e, e.getResult().getResultCode());
            }
        }
        return null;
    }

    static void printlnTextMsg(final ConsoleApplication app, final String msg) {
        printlnTextMsg(app, null, msg);
    }

    static void printlnTextMsg(final ConsoleApplication app,
                               final LocalizableMessageDescriptor.Arg1<Object> localizableMsg,
                               final String msg) {
        if (msg != null && !msg.isEmpty()) {
            app.errPrintln(localizableMsg == null ? LocalizableMessage.raw(msg)
                                                  : localizableMsg.get(msg));
        }
    }

    /**
     * Return the maximum line length before wrapping or {@code 0} if no wrap should be done.
     *
     * @param wrapColumn
     *         {@link IntegerArgument} which could be provided on the command line.
     * @return The maximum line length before wrapping or {@code 0} if no wrap should be done.
     */
    static int computeWrapColumn(final IntegerArgument wrapColumn) throws ArgumentException {
        if (wrapColumn.isPresent()) {
            return wrapColumn.getIntValue();
        }
        return NO_WRAPPING_BY_DEFAULT;
    }

    static void runToolAndExit(final ToolConsoleApplication tool, final String[] args) {
        System.exit(filterExitCode(runTool(tool, args)));
    }

    @VisibleForTesting
    static int runTool(final ToolConsoleApplication tool, final String... args) {
        try {
            return tool.run(args);
        } catch (final LDAPToolException e) {
            e.printErrorMessage(tool);
            return e.getResultCode();
        }
    }

    /** Prevent instantiation. */
    private Utils() {
        // Do nothing.
    }
}
