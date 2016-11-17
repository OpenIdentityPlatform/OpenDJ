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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.cli.CliMessages.INFO_FILE_PLACEHOLDER;
import static com.forgerock.opendj.cli.ToolVersionHandler.newSdkVersionHandler;
import static com.forgerock.opendj.cli.Utils.throwIfArgumentsConflict;
import static com.forgerock.opendj.ldap.tools.LDAPToolException.newToolParamException;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;
import static com.forgerock.opendj.cli.CommonArguments.*;
import static com.forgerock.opendj.ldap.tools.Utils.addControlsToRequest;
import static com.forgerock.opendj.ldap.tools.Utils.printErrorMessage;
import static com.forgerock.opendj.ldap.tools.Utils.readControls;
import static com.forgerock.opendj.ldap.tools.Utils.runTool;
import static com.forgerock.opendj.ldap.tools.Utils.runToolAndExit;

import java.io.PrintStream;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.PasswordModifyExtendedRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.PasswordModifyExtendedResult;
import org.forgerock.util.annotations.VisibleForTesting;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ConnectionFactoryProvider;
import com.forgerock.opendj.cli.FileBasedArgument;
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
public final class LDAPPasswordModify extends ToolConsoleApplication {

    /**
     * The main method for ldappasswordmodify tool.
     *
     * @param args
     *            The command-line arguments provided to this program.
     */
    public static void main(final String[] args) {
        runToolAndExit(new LDAPPasswordModify(System.out, System.err), args);
    }

    /**
     * This method should be used to run this ldap tool programmatically.
     * Output and errors will be printed on provided {@link PrintStream}.
     *
     * @param out
     *            The {@link PrintStream} to use to write tool output.
     * @param err
     *            The {@link PrintStream} to use to write tool errors.
     * @param args
     *            The arguments to use with this tool.
     * @return The code returned by the tool
     */
    public static int run(final PrintStream out, final PrintStream err, final String... args) {
        return runTool(new LDAPPasswordModify(out, err), args);
    }

    private BooleanArgument verbose;

    @VisibleForTesting
    LDAPPasswordModify(final PrintStream out, final PrintStream err) {
        super(out, err);
    }

    @Override
    public boolean isInteractive() {
        return false;
    }

    @Override
    public boolean isVerbose() {
        return verbose.isPresent();
    }

    @Override
    int run(final String... args) throws LDAPToolException {
        // Create the command-line argument parser for use with this program.
        final LocalizableMessage toolDescription = INFO_LDAPPWMOD_TOOL_DESCRIPTION.get();
        final LDAPToolArgumentParser argParser = LDAPToolArgumentParser.builder(LDAPPasswordModify.class.getName())
                .toolDescription(toolDescription)
                .needAuthenticatedConnectionFactory()
                .build();
        argParser.setVersionHandler(newSdkVersionHandler());
        argParser.setShortToolDescription(REF_SHORT_DESC_LDAPPASSWORDMODIFY.get());

        ConnectionFactoryProvider connectionFactoryProvider;

        FileBasedArgument currentPWFile;
        FileBasedArgument newPWFile;
        BooleanArgument showUsage;
        StringArgument currentPW;
        StringArgument controlStr;
        StringArgument newPW;
        StringArgument proxyAuthzID;
        StringArgument propertiesFileArgument;
        BooleanArgument noPropertiesFileArgument;

        try {
            connectionFactoryProvider = new ConnectionFactoryProvider(argParser, this);

            propertiesFileArgument = propertiesFileArgument();
            argParser.addArgument(propertiesFileArgument);
            argParser.setFilePropertiesArgument(propertiesFileArgument);

            noPropertiesFileArgument = noPropertiesFileArgument();
            argParser.addArgument(noPropertiesFileArgument);
            argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

            newPW =
                    StringArgument.builder("newPassword")
                            .shortIdentifier('n')
                            .description(INFO_LDAPPWMOD_DESCRIPTION_NEWPW.get())
                            .valuePlaceholder(INFO_NEW_PASSWORD_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            newPWFile =
                    FileBasedArgument.builder("newPasswordFile")
                            .shortIdentifier('F')
                            .description(INFO_LDAPPWMOD_DESCRIPTION_NEWPWFILE.get())
                            .valuePlaceholder(INFO_FILE_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            currentPW =
                    StringArgument.builder("currentPassword")
                            .shortIdentifier('c')
                            .description(INFO_LDAPPWMOD_DESCRIPTION_CURRENTPW.get())
                            .valuePlaceholder(INFO_CURRENT_PASSWORD_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            currentPWFile =
                    FileBasedArgument.builder("currentPasswordFile")
                            .shortIdentifier('C')
                            .description(INFO_LDAPPWMOD_DESCRIPTION_CURRENTPWFILE.get())
                            .valuePlaceholder(INFO_FILE_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            proxyAuthzID =
                    StringArgument.builder("authzID")
                            .shortIdentifier('a')
                            .description(INFO_LDAPPWMOD_DESCRIPTION_AUTHZID.get())
                            .valuePlaceholder(INFO_PROXYAUTHID_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            controlStr = controlArgument();
            argParser.addArgument(controlStr);

            verbose = verboseArgument();
            argParser.addArgument(verbose);

            showUsage = showUsageArgument();
            argParser.addArgument(showUsage);
            argParser.setUsageArgument(showUsage, getOutputStream());
        } catch (final ArgumentException ae) {
            errPrintln(ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        argParser.parseArgumentsNoBindRequest(args, getErrStream(), connectionFactoryProvider);
        if (argParser.usageOrVersionDisplayed()) {
            return ResultCode.SUCCESS.intValue();
        }

        final PasswordModifyExtendedRequest request = Requests.newPasswordModifyExtendedRequest();
        addControlsToRequest(request, readControls(controlStr));

        try {
            throwIfArgumentsConflict(newPW, newPWFile);
            throwIfArgumentsConflict(currentPW, currentPWFile);
        } catch (final ArgumentException e) {
            throw newToolParamException(e, e.getMessageObject());
        }

        try (Connection connection = argParser.getConnectionFactory().getConnection()) {
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

            PasswordModifyExtendedResult result = connection.extendedRequest(request);
            println(INFO_LDAPPWMOD_SUCCESSFUL.get());
            Utils.printlnTextMsg(this, INFO_LDAPPWMOD_ADDITIONAL_INFO, result.getDiagnosticMessage());
            if (result.getGeneratedPassword() != null) {
                println(INFO_LDAPPWMOD_GENERATED_PASSWORD.get(
                        ByteString.valueOfBytes(result.getGeneratedPassword()).toString()));
            }

            return ResultCode.SUCCESS.intValue();
        } catch (final LdapException e) {
            return printErrorMessage(this, e, ERR_LDAPPWMOD_FAILED);
        }
    }
}
