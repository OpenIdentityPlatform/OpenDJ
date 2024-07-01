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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.cli.CliMessages.ERR_ARGPARSER_NO_ARGUMENT_WITH_LONG_ID;
import static com.forgerock.opendj.cli.CliMessages.ERR_ARGPARSER_NO_ARGUMENT_WITH_SHORT_ID;
import static com.forgerock.opendj.cli.CliMessages.ERR_ARGPARSER_TOO_FEW_TRAILING_ARGUMENTS;
import static com.forgerock.opendj.cli.CliMessages.ERR_ARG_CANNOT_DECODE_AS_INT;
import static com.forgerock.opendj.cli.CliMessages.ERR_FILEARG_NO_SUCH_FILE;
import static com.forgerock.opendj.cli.CliMessages.ERR_INTARG_VALUE_ABOVE_UPPER_BOUND;
import static com.forgerock.opendj.cli.CliMessages.ERR_TOOL_CONFLICTING_ARGS;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_LDAP_FILTER_NO_EQUAL_SIGN;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.ERR_LDAPCOMPARE_INVALID_ATTR_STRING;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.ERR_TOOL_INVALID_CONTROL_STRING;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.addValueNeededLongArgs;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.addValueNeededShortArgs;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.args;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.toDataProviderArray;
import static org.fest.assertions.Assertions.assertThat;
import static org.forgerock.opendj.ldap.ResultCode.COMPARE_TRUE;

import java.util.ArrayList;
import java.util.List;

import com.forgerock.opendj.ldap.controls.RealAttributesOnlyRequestControl;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LdapResultHandler;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.AssertionRequestControl;
import org.forgerock.opendj.ldap.controls.ProxiedAuthV2RequestControl;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.Responses;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** A set of test cases for the LDAPCompare tool. */
@SuppressWarnings("javadoc")
@Test
public class LDAPCompareTestCase extends LDAPToolsTestCase {

    private final class LdapCompareToolLdapServer extends ToolLdapServer {
        private final class LdapCompareRequestHandler extends ToolLdapServer.ToolLdapServerConnection {
            @Override
            public void handleCompare(final Integer requestContext,
                                      final CompareRequest request,
                                      final IntermediateResponseHandler intermediateResponseHandler,
                                      final LdapResultHandler<CompareResult> resultHandler) {
                assertThat(request.getName().toString()).isEqualTo("uid=marvin");
                assertThat(request.getAssertionValueAsString()).isEqualTo("the paranoid android");
                assertThat(request.getAttributeDescription().toString()).isEqualTo("description");
                assertThatControlsHaveBeenSentInRequest(request.getControls());
                resultHandler.handleResult(Responses.newCompareResult(COMPARE_TRUE));
            }
        }

        @Override
        ToolLdapServerConnection newServerConnection() {
            return new LdapCompareRequestHandler();
        }
    }

    @Override
    ToolLdapServer createFakeServer() {
        return new LdapCompareToolLdapServer();
    }

    /**
     * Retrieves sets of invalid arguments that may not be used to initialize
     * the LDAPCompare tool.
     *
     * @return Sets of invalid arguments that may not be used to initialize the
     * LDAPCompare tool.
     */
    @DataProvider(name = "invalidArgs")
    public Object[][] getInvalidArgumentLists() throws Exception {
        final List<List<String>> argLists = new ArrayList<>();
        final List<LocalizableMessage> reasonList = new ArrayList<>();

        argLists.add(args());
        reasonList.add(ERR_ARGPARSER_TOO_FEW_TRAILING_ARGUMENTS.get(2));

        addValueNeededShortArgs(argLists, reasonList, "D", "w", "j", "K", "P", "W", "h", "p", "J", "o");
        addValueNeededLongArgs(argLists, reasonList, "assertionFilter", "hostname", "port", "control");

        argLists.add(args("-I"));
        reasonList.add(ERR_ARGPARSER_NO_ARGUMENT_WITH_SHORT_ID.get("I"));

        argLists.add(args("--invalidLongArgument"));
        reasonList.add(ERR_ARGPARSER_NO_ARGUMENT_WITH_LONG_ID.get("invalidLongArgument"));

        argLists.add(args("--assertionFilter", "(invalidfilter)", "uid:test.user", "uid=test.user,o=test"));
        reasonList.add(ERR_LDAP_FILTER_NO_EQUAL_SIGN.get("(invalidfilter)", 1, 14));

        argLists.add(args("-D", "cn=Directory Manager", "-j", "no.such.file", "uid:test.user", "uid=test.user,o=test"));
        reasonList.add(ERR_FILEARG_NO_SUCH_FILE.get("no.such.file", "bindPasswordFile"));

        argLists.add(args("-D", "cn=Directory Manager", "-w", "password", "-j", "src/test/resources/dummy-truststore",
                          "uid:test.user", "uid=test.user,o=test"));
        reasonList.add(ERR_TOOL_CONFLICTING_ARGS.get("bindPassword", "bindPasswordFile"));

        argLists.add(args("-J", "1.2.3.4:invalidcriticality", "uid:test.user", "uid=test.user,o=test"));
        reasonList.add(ERR_TOOL_INVALID_CONTROL_STRING.get("1.2.3.4:invalidcriticality"));

        argLists.add(args("-Z", "-q", "o=test", "o=test2"));
        reasonList.add(ERR_TOOL_CONFLICTING_ARGS.get("useStartTLS", "useSSL"));

        argLists.add(args("-p", "nonnumeric", "uid:test.user", "uid=test.user,o=test"));
        reasonList.add(ERR_ARG_CANNOT_DECODE_AS_INT.get("nonnumeric", "port"));

        argLists.add(args("-p", "999999", "uid:test.user", "uid=test.user,o=test"));
        reasonList.add(ERR_INTARG_VALUE_ABOVE_UPPER_BOUND.get("port", 999999, 65535));

        argLists.add(args("-D", "cn=Directory Manager", "-w", "password"));
        reasonList.add(ERR_ARGPARSER_TOO_FEW_TRAILING_ARGUMENTS.get(2));

        argLists.add(args("-D", "cn=Directory Manager", "-w", "password", "uid:test.user"));
        reasonList.add(ERR_ARGPARSER_TOO_FEW_TRAILING_ARGUMENTS.get(2));

        argLists.add(args("-D", "cn=Directory Manager", "-w", "password", "malformed", "uid=test.user,o=test"));
        reasonList.add(ERR_LDAPCOMPARE_INVALID_ATTR_STRING.get("malformed"));

        return toDataProviderArray(argLists, reasonList);
    }

    @Test
    public void simpleComparison() throws Exception {
        runToolOnMockedServer("description:the paranoid android",
                              "uid=marvin");
    }

    @Test
    public void simpleComparisonUseReturnCode() throws Exception {
        runToolOnMockedServer(ResultCode.COMPARE_TRUE,
                              "-m",
                              "description:the paranoid android",
                              "uid=marvin");
    }

    @Test
    public void simpleComparisonWithProxyAs() throws Exception {
        controls.add(ProxiedAuthV2RequestControl.newControl("u:arthurdent"));
        runToolOnMockedServer("-Y", "u:arthurdent",
                              "description:the paranoid android",
                              "uid=marvin");
    }

    @Test
    public void comparisonWithAssertionFilter() throws Exception {
        controls.add(AssertionRequestControl.newControl(true, Filter.equality("initials", "FP")));
        runToolOnMockedServer("--assertionFilter", "(initials=FP)",
                              "description:the paranoid android",
                              "uid=marvin");
    }

    @Test
    public void comparisonWithAssertionFilterAndControl() throws Exception {
        controls.add(RealAttributesOnlyRequestControl.newControl(true));
        controls.add(AssertionRequestControl.newControl(true, Filter.equality("initials", "M")));
        runToolOnMockedServer("--assertionFilter", "(initials=M)",
                              "-J", "2.16.840.1.113730.3.4.17:true",
                              "description:the paranoid android",
                              "uid=marvin");
    }

    @Test
    public void comparisonWithAssertionFilterAndControlDryRun() throws Exception {
        controls.add(RealAttributesOnlyRequestControl.newControl(true));
        controls.add(AssertionRequestControl.newControl(true, Filter.equality("initials", "M")));
        runToolOnMockedServer("--assertionFilter", "(initials=M)",
                              "-J", "2.16.840.1.113730.3.4.17:true",
                              "-n",
                              "description:the paranoid android",
                              "uid=marvin");
    }

    @Override
    ToolConsoleApplication createInstance() {
        return new LDAPCompare(outStream, errStream);
    }
}
