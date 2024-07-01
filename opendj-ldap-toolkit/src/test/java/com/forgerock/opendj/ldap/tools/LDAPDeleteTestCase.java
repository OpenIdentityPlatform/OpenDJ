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
import static com.forgerock.opendj.ldap.tools.ToolsMessages.ERR_TOOL_INVALID_CONTROL_STRING;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.addValueNeededShortArgs;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.args;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.toDataProviderArray;
import static org.fest.assertions.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LdapResultHandler;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.forgerock.opendj.ldap.controls.SubtreeDeleteRequestControl;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** A set of test cases for the LDAPDelete tool. */
@Test
public class LDAPDeleteTestCase extends LDAPToolsTestCase {

    private final class LdapDeleteToolLdapServer extends ToolLdapServer {
        private final class LdapDeleteRequestHandler extends ToolLdapServer.ToolLdapServerConnection {
            @Override
            public void handleDelete(final Integer requestContext,
                                     final DeleteRequest request,
                                     final IntermediateResponseHandler intermediateResponseHandler,
                                     final LdapResultHandler<Result> resultHandler) {
                assertThat(request.getName().toString()).isEqualTo("uid=marvin");
                assertThatControlsHaveBeenSentInRequest(request.getControls());
                resultHandler.handleResult(Responses.newResult(ResultCode.SUCCESS));
            }
        }

        @Override
        ToolLdapServerConnection newServerConnection() {
            return new LdapDeleteRequestHandler();
        }
    }

    @Override
    ToolLdapServer createFakeServer() {
        return new LdapDeleteToolLdapServer();
    }

    /**
     * Retrieves sets of invalid arguments that may not be used to initialize
     * the LDAPDelete tool.
     *
     * @return Sets of invalid arguments that may not be used to initialize the
     * LDAPDelete tool.
     */
    @DataProvider(name = "invalidArgs")
    public Object[][] getInvalidArgumentLists() {
        final List<List<String>> argLists = new ArrayList<>();
        final List<LocalizableMessage> reasonList = new ArrayList<>();

        addValueNeededShortArgs(
                argLists, reasonList, "D", "w", "j", "K", "P", "W", "h", "p", "J", "o");

        argLists.add(args("-D", "cn=Directory Manager", "-j", "no.such.file", "o=test"));
        reasonList.add(ERR_FILEARG_NO_SUCH_FILE.get("no.such.file", "bindPasswordFile"));

        argLists.add(args("-J", "1.2.3.4:invalidcriticality", "o=test"));
        reasonList.add(ERR_TOOL_INVALID_CONTROL_STRING.get("1.2.3.4:invalidcriticality"));

        argLists.add(args("-Z", "-q", "o=test"));
        reasonList.add(ERR_TOOL_CONFLICTING_ARGS.get("useStartTLS", "useSSL"));

        argLists.add(args("-p", "nonnumeric", "o=test"));
        reasonList.add(ERR_ARG_CANNOT_DECODE_AS_INT.get("nonnumeric", "port"));

        argLists.add(args("-p", "999999", "o=test"));
        reasonList.add(ERR_INTARG_VALUE_ABOVE_UPPER_BOUND.get("port", 999999, 65535));

        return toDataProviderArray(argLists, reasonList);
    }

    @Test
    public void simpleDelete() throws Exception {
        runToolOnMockedServer("uid=marvin");
    }

    @Test
    public void deleteWithControl() throws Exception {
        controls.add(GenericControl.newControl("1.2.3.4.5.6.7.42", false, "testcontrol"));
        runToolOnMockedServer("-J", "1.2.3.4.5.6.7.42:false:testcontrol",
                              "uid=marvin");
    }

    @Test
    public void subtreeDeleteWithControl() throws Exception {
        controls.add(GenericControl.newControl("1.2.3.4.5.6.7.42", false, "testcontrol"));
        controls.add(SubtreeDeleteRequestControl.newControl(false));
        runToolOnMockedServer("-J", "1.2.3.4.5.6.7.42:false:testcontrol",
                              "--deleteSubtree",
                              "uid=marvin");
    }

    @Test
    public void subtreeDeleteWithControlDryRun() throws Exception {
        controls.add(GenericControl.newControl("1.2.3.4.5.6.7.42", false, "testcontrol"));
        controls.add(SubtreeDeleteRequestControl.newControl(false));
        runToolOnMockedServer("-J", "1.2.3.4.5.6.7.42:false:testcontrol",
                              "--deleteSubtree",
                              "-n",
                              "uid=marvin");
    }

    @Override
    ToolConsoleApplication createInstance() {
        return new LDAPDelete(outStream, errStream);
    }
}

