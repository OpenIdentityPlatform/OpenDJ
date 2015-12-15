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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;
import static com.forgerock.opendj.cli.Utils.filterExitCode;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.requests.PasswordModifyExtendedRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.PasswordModifyExtendedResult;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.CommonArguments;
import com.forgerock.opendj.cli.ConnectionFactoryProvider;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.FileBasedArgument;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.StringArgument;

/**
 * A tool that can be used to issue LDAP password modify extended requests to
 * the Directory Server. It exposes the three primary options available for this
 * operation, which are:
 * <UL>
 * <LI>The user identity whose password should be changed.</LI>
 * <LI>The current password for the user.</LI>
 * <LI>The new password for the user.
 * </UL>
 * All of these are optional components that may be included or omitted from the
 * request.
 */
public final class LDAPPasswordModify extends ConsoleApplication {
    /**
     * Parses the command-line arguments, establishes a connection to the
     * Directory Server, sends the password modify request, and reads the
     * response.
     *
     * @param args
     *            The command-line arguments provided to this program.
     */
    public static void main(final String[] args) {
        final int retCode = new LDAPPasswordModify().run(args);
        System.exit(filterExitCode(retCode));
    }

    private BooleanArgument verbose;

    private LDAPPasswordModify() {
        // Nothing to do.
    }

    @Override
    public boolean isInteractive() {
        return false;
    }

    @Override
    public boolean isVerbose() {
        return verbose.isPresent();
    }

    private int run(final String[] args) {
        // Create the command-line argument parser for use with this program.
        final LocalizableMessage toolDescription = INFO_LDAPPWMOD_TOOL_DESCRIPTION.get();
        final ArgumentParser argParser =
                new ArgumentParser(LDAPPasswordModify.class.getName(), toolDescription, false);
        argParser.setVersionHandler(new SdkVersionHandler());
        argParser.setShortToolDescription(REF_SHORT_DESC_LDAPPASSWORDMODIFY.get());

        ConnectionFactoryProvider connectionFactoryProvider;
        ConnectionFactory connectionFactory;

        FileBasedArgument currentPWFile;
        FileBasedArgument newPWFile;
        BooleanArgument showUsage;
        IntegerArgument version;
        StringArgument currentPW;
        StringArgument controlStr;
        StringArgument newPW;
        StringArgument proxyAuthzID;
        StringArgument propertiesFileArgument;
        BooleanArgument noPropertiesFileArgument;

        try {
            connectionFactoryProvider = new ConnectionFactoryProvider(argParser, this);

            propertiesFileArgument = CommonArguments.getPropertiesFile();
            argParser.addArgument(propertiesFileArgument);
            argParser.setFilePropertiesArgument(propertiesFileArgument);

            noPropertiesFileArgument = CommonArguments.getNoPropertiesFile();
            argParser.addArgument(noPropertiesFileArgument);
            argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

            newPW =
                    new StringArgument("newpw", 'n', "newPassword", false, false, true,
                            INFO_NEW_PASSWORD_PLACEHOLDER.get(), null, null,
                            INFO_LDAPPWMOD_DESCRIPTION_NEWPW.get());
            newPW.setPropertyName("newPassword");
            argParser.addArgument(newPW);

            newPWFile =
                    new FileBasedArgument("newpwfile", 'F', "newPasswordFile", false, false,
                            INFO_FILE_PLACEHOLDER.get(), null, null,
                            INFO_LDAPPWMOD_DESCRIPTION_NEWPWFILE.get());
            newPWFile.setPropertyName("newPasswordFile");
            argParser.addArgument(newPWFile);

            currentPW =
                    new StringArgument("currentpw", 'c', "currentPassword", false, false, true,
                            INFO_CURRENT_PASSWORD_PLACEHOLDER.get(), null, null,
                            INFO_LDAPPWMOD_DESCRIPTION_CURRENTPW.get());
            currentPW.setPropertyName("currentPassword");
            argParser.addArgument(currentPW);

            currentPWFile =
                    new FileBasedArgument("currentpwfile", 'C', "currentPasswordFile", false,
                            false, INFO_FILE_PLACEHOLDER.get(), null, null,
                            INFO_LDAPPWMOD_DESCRIPTION_CURRENTPWFILE.get());
            currentPWFile.setPropertyName("currentPasswordFile");
            argParser.addArgument(currentPWFile);

            proxyAuthzID =
                    new StringArgument("authzid", 'a', "authzID", false, false, true,
                            INFO_PROXYAUTHID_PLACEHOLDER.get(), null, null,
                            INFO_LDAPPWMOD_DESCRIPTION_AUTHZID.get());
            proxyAuthzID.setPropertyName("authzID");
            argParser.addArgument(proxyAuthzID);

            controlStr =
                    new StringArgument("control", 'J', "control", false, true, true,
                            INFO_LDAP_CONTROL_PLACEHOLDER.get(), null, null,
                            INFO_DESCRIPTION_CONTROLS.get());
            controlStr.setPropertyName("control");
            argParser.addArgument(controlStr);

            version = CommonArguments.getLdapVersion();
            argParser.addArgument(version);

            verbose = CommonArguments.getVerbose();
            argParser.addArgument(verbose);

            showUsage = CommonArguments.getShowUsage();
            argParser.addArgument(showUsage);
            argParser.setUsageArgument(showUsage, getOutputStream());
        } catch (final ArgumentException ae) {
            errPrintln(ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        // Parse the command-line arguments provided to this program.
        try {
            argParser.parseArguments(args);

            // If we should just display usage or version information, then print it and exit.
            if (argParser.usageOrVersionDisplayed()) {
                return 0;
            }

            connectionFactory = connectionFactoryProvider.getAuthenticatedConnectionFactory();
        } catch (final ArgumentException ae) {
            argParser.displayMessageAndUsageReference(getErrStream(), ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        final PasswordModifyExtendedRequest request = Requests.newPasswordModifyExtendedRequest();
        try {
            final int versionNumber = version.getIntValue();
            if (versionNumber != 2 && versionNumber != 3) {
                errPrintln(ERR_DESCRIPTION_INVALID_VERSION.get(String.valueOf(versionNumber)));
                return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
            }
        } catch (final ArgumentException ae) {
            errPrintln(ERR_DESCRIPTION_INVALID_VERSION.get(version.getValue()));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        if (controlStr.isPresent()) {
            for (final String ctrlString : controlStr.getValues()) {
                try {
                    final Control ctrl = Utils.getControl(ctrlString);
                    request.addControl(ctrl);
                } catch (final DecodeException de) {
                    errPrintln(ERR_TOOL_INVALID_CONTROL_STRING.get(ctrlString));
                    ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
                }
            }
        }

        if (newPW.isPresent() && newPWFile.isPresent()) {
            final LocalizableMessage message =
                    ERR_LDAPPWMOD_CONFLICTING_ARGS.get(newPW.getLongIdentifier(), newPWFile
                            .getLongIdentifier());
            errPrintln(message);
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        if (currentPW.isPresent() && currentPWFile.isPresent()) {
            final LocalizableMessage message =
                    ERR_LDAPPWMOD_CONFLICTING_ARGS.get(currentPW.getLongIdentifier(), currentPWFile
                            .getLongIdentifier());
            errPrintln(message);
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        Connection connection;
        try {
            connection = connectionFactory.getConnection();
        } catch (final LdapException ere) {
            return Utils.printErrorMessage(this, ere);
        }

        if (proxyAuthzID.isPresent()) {
            request.setUserIdentity(proxyAuthzID.getValue());
        }

        if (currentPW.isPresent()) {
            request.setOldPassword(currentPW.getValue().toCharArray());
        } else if (currentPWFile.isPresent()) {
            request.setOldPassword(currentPWFile.getValue().toCharArray());
        }

        if (newPW.isPresent()) {
            request.setNewPassword(newPW.getValue().toCharArray());
        } else if (newPWFile.isPresent()) {
            request.setNewPassword(newPWFile.getValue().toCharArray());
        }

        PasswordModifyExtendedResult result;
        try {
            result = connection.extendedRequest(request);
        } catch (final LdapException e) {
            LocalizableMessage message =
                    ERR_LDAPPWMOD_FAILED.get(e.getResult().getResultCode().intValue(), e
                            .getResult().getResultCode().toString());
            errPrintln(message);

            final String errorMessage = e.getResult().getDiagnosticMessage();
            if (errorMessage != null && errorMessage.length() > 0) {
                message = ERR_LDAPPWMOD_FAILURE_ERROR_MESSAGE.get(errorMessage);
                errPrintln(message);
            }

            final String matchedDN = e.getResult().getMatchedDN();
            if (matchedDN != null && matchedDN.length() > 0) {
                message = ERR_LDAPPWMOD_FAILURE_MATCHED_DN.get(matchedDN);
                errPrintln(message);
            }
            return e.getResult().getResultCode().intValue();
        }

        println(INFO_LDAPPWMOD_SUCCESSFUL.get());

        final String additionalInfo = result.getDiagnosticMessage();
        if (additionalInfo != null && additionalInfo.length() > 0) {
            println(INFO_LDAPPWMOD_ADDITIONAL_INFO.get(additionalInfo));
        }

        if (result.getGeneratedPassword() != null) {
            println(INFO_LDAPPWMOD_GENERATED_PASSWORD.get(ByteString.valueOfBytes(
                    result.getGeneratedPassword()).toString()));
        }

        return 0;
    }
}
