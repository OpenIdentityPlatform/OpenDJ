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
 *  Copyright 2016 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;


import static com.forgerock.opendj.cli.CliMessages.ERR_ARG_CANNOT_DECODE_AS_INT;
import static com.forgerock.opendj.cli.CliMessages.ERR_FILEARG_NO_SUCH_FILE;
import static com.forgerock.opendj.cli.CliMessages.ERR_INTARG_VALUE_ABOVE_UPPER_BOUND;
import static com.forgerock.opendj.cli.CliMessages.ERR_TOOL_CONFLICTING_ARGS;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.addValueNeededLongArgs;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.addValueNeededShortArgs;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.args;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.buildArgs;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.createTempFile;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.toDataProviderArray;
import static org.assertj.core.api.Assertions.fail;
import static org.fest.assertions.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.forgerock.opendj.ldap.controls.AccountUsabilityRequestControl;
import com.forgerock.opendj.ldap.tools.ToolsTestUtils.Args;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LdapResultHandler;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.PasswordPolicyRequestControl;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Responses;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;


/** A set of test cases for the LDAPPasswordModify tool. */
@Test
public class LDAPPasswordModifyTestCase extends LDAPToolsTestCase {

    private final class LdapPasswordModifyToolLdapServer extends ToolLdapServer {
        private final class LdapPasswordModifyRequestHandler extends ToolLdapServer.ToolLdapServerConnection {
            @Override
            public <R extends ExtendedResult> void handleExtendedRequest(final Integer requestContext,
                                                                         final ExtendedRequest<R> request,
                                                                         final IntermediateResponseHandler irh,
                                                                         final LdapResultHandler<R> resultHandler) {
                assertThat(request.getOID()).isEqualTo("1.3.6.1.4.1.4203.1.11.1");
                assertThat(request.getValue().toASCIIString()).isEqualTo(
                        "0*%80%0Edn: uid=marvin%81%0Boldpassword%82%0Bnewpassword");
                assertThatControlsHaveBeenSentInRequest(request.getControls());
                try {
                    resultHandler.handleResult(request.getResultDecoder().decodeExtendedResult(
                            Responses.newPasswordModifyExtendedResult(ResultCode.SUCCESS), new DecodeOptions()));
                } catch (DecodeException e) {
                    fail("Unexpected error occurred while mocking server password modify response.", e);
                }
            }
        }

        @Override
        ToolLdapServerConnection newServerConnection() {
            return new LdapPasswordModifyRequestHandler();
        }
    }

    @Override
    ToolLdapServer createFakeServer() {
        return new LdapPasswordModifyToolLdapServer();
    }

    /**
     * Retrieves sets of invalid arguments that may not be used to initialize
     * the LDAPPasswordModify tool.
     *
     * @return Sets of invalid arguments that may not be used to initialize the
     * LDAPPasswordModify tool.
     */
    @DataProvider(name = "invalidArgs")
    public Object[][] getInvalidArgumentLists() {
        final List<List<String>> argLists = new ArrayList<>();
        final List<LocalizableMessage> reasonList = new ArrayList<>();

        addValueNeededShortArgs(argLists, reasonList, "a", "N", "n", "c", "C", "D", "w", "j", "K", "P", "W", "h", "p");
        addValueNeededLongArgs(argLists, reasonList, "hostname", "port", "control",
                "keyStorePasswordFile", "trustStorePassword", "trustStorePasswordFile");

        argLists.add(args("-D", "cn=Directory Manager", "-j", "no.such.file"));
        reasonList.add(ERR_FILEARG_NO_SUCH_FILE.get("no.such.file", "bindPasswordFile"));

        argLists.add(args("-D", "cn=Directory Manager", "-w", "password", "-j", "src/test/resources/dummy-truststore"));
        reasonList.add(ERR_TOOL_CONFLICTING_ARGS.get("bindPassword", "bindPasswordFile"));

        argLists.add(args("-D", "cn=Directory Manager", "-c", "password", "-C", "src/test/resources/dummy-truststore"));
        reasonList.add(ERR_TOOL_CONFLICTING_ARGS.get("currentPassword", "currentPasswordFile"));

        argLists.add(args("-D", "cn=Directory Manager", "-n", "password", "-F", "src/test/resources/dummy-truststore"));
        reasonList.add(ERR_TOOL_CONFLICTING_ARGS.get("newPassword", "newPasswordFile"));

        argLists.add(args("-Z", "-q"));
        reasonList.add(ERR_TOOL_CONFLICTING_ARGS.get("useStartTLS", "useSSL"));

        argLists.add(args("-p", "nonnumeric"));
        reasonList.add(ERR_ARG_CANNOT_DECODE_AS_INT.get("nonnumeric", "port"));

        argLists.add(args("-p", "999999"));
        reasonList.add(ERR_INTARG_VALUE_ABOVE_UPPER_BOUND.get("port", 999999, 65535));

        return toDataProviderArray(argLists, reasonList);
    }

    @Test
    public void testLdapPasswordModify() throws Exception {
        runToolOnMockedServer("-c", "oldpassword",
                              "-n", "newpassword");
    }

    @Test
    public void testLdapPasswordModifyWithPasswordInFiles() throws Exception {
        final String oldPwdFilePath = createTempFile("oldpassword");
        final String newPwdFilePath = createTempFile("newpassword");
        runToolOnMockedServer("-C", oldPwdFilePath,
                              "-F", newPwdFilePath);
    }

    @Test
    public void testLdapPasswordModifyWithControls() throws Exception {
        controls.add(PasswordPolicyRequestControl.newControl(false));
        controls.add(AccountUsabilityRequestControl.newControl(false));
        runToolOnMockedServer("-J", "1.3.6.1.4.1.42.2.27.8.5.1:false",
                              "-J", "1.3.6.1.4.1.42.2.27.9.5.8:false",
                              "-c", "oldpassword",
                              "-n", "newpassword");
    }

    @Override
    Args toolConstantArguments() {
        return buildArgs().add("-a", "dn: uid=marvin");
    }

    @Override
    ToolConsoleApplication createInstance() {
        return new LDAPPasswordModify(outStream, errStream);
    }
}

