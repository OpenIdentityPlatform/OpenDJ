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

import static com.forgerock.opendj.cli.CliMessages.ERR_ARGPARSER_NO_VALUE_FOR_REQUIRED_ARG;
import static com.forgerock.opendj.cli.CliMessages.ERR_ARGPARSER_TOO_FEW_TRAILING_ARGUMENTS;
import static com.forgerock.opendj.cli.CliMessages.ERR_ARG_CANNOT_DECODE_AS_INT;
import static com.forgerock.opendj.cli.CliMessages.ERR_INTARG_VALUE_ABOVE_UPPER_BOUND;
import static com.forgerock.opendj.cli.CliMessages.ERR_MCARG_VALUE_NOT_ALLOWED;
import static com.forgerock.opendj.cli.CliMessages.ERR_TOOL_CONFLICTING_ARGS;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_LDAP_FILTER_NO_EQUAL_SIGN;
import static com.forgerock.opendj.ldap.tools.ToolLdapServer.DIRECTORY_MANAGER;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.ERR_PSEARCH_DOESNT_START_WITH_PS;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.ERR_PSEARCH_INVALID_CHANGE_TYPE;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.ERR_TOOL_INVALID_CONTROL_STRING;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.addValueNeededLongArgs;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.addValueNeededShortArgs;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.args;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.toDataProviderArray;
import static org.fest.assertions.Assertions.assertThat;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.buildArgs;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LdapResultHandler;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.controls.AssertionRequestControl;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.forgerock.opendj.ldap.controls.GetEffectiveRightsRequestControl;
import org.forgerock.opendj.ldap.controls.MatchedValuesRequestControl;
import org.forgerock.opendj.ldap.controls.PersistentSearchChangeType;
import org.forgerock.opendj.ldap.controls.PersistentSearchRequestControl;
import org.forgerock.opendj.ldap.controls.ProxiedAuthV2RequestControl;
import org.forgerock.opendj.ldap.controls.ServerSideSortRequestControl;
import org.forgerock.opendj.ldap.controls.SimplePagedResultsControl;
import org.forgerock.opendj.ldap.controls.SubentriesRequestControl;
import org.forgerock.opendj.ldap.controls.VirtualListViewRequestControl;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** A set of test cases for the LDAPSearch tool. */
@SuppressWarnings("javadoc")
@Test
public class LDAPSearchTestCase extends LDAPToolsTestCase {

    private final class LdapSearchToolLdapServer extends ToolLdapServer {

        private final class LdapSearchRequestHandler extends ToolLdapServer.ToolLdapServerConnection {
            @Override
            public void handleSearch(final Integer requestContext,
                                     final SearchRequest request,
                                     final IntermediateResponseHandler intermediateResponseHandler,
                                     final SearchResultHandler entryHandler,
                                     final LdapResultHandler<Result> resultHandler) {
                assertThat(request.getName().toString()).isEqualTo("ou=people,dc=example,dc=com");
                assertThat(request.getDereferenceAliasesPolicy()).isEqualTo(DereferenceAliasesPolicy.FINDING_BASE);
                assertThat(request.getSizeLimit()).isEqualTo(100);
                assertThat(request.isTypesOnly()).isTrue();
                assertThat(request.getTimeLimit()).isEqualTo(120);
                assertThat(request.getScope()).isEqualTo(SearchScope.SUBORDINATES);
                assertThat(request.getAttributes()).containsExactly("uid", "objectclass", "description");
                assertThat(request.getFilter().toString()).isEqualTo("(st=Alabama)");
                assertThatControlsHaveBeenSentInRequest(request.getControls());
                resultHandler.handleResult(Responses.newResult(ResultCode.SUCCESS));
            }
        }
        @Override
        ToolLdapServerConnection newServerConnection() {
            return new LdapSearchRequestHandler();
        }

    }

    @Override
    ToolLdapServer createFakeServer() {
        return new LdapSearchToolLdapServer();
    }

    /**
     * Retrieves sets of invalid arguments that may not be used to initialize
     * the LDAPSearch tool.
     *
     * @return Sets of invalid arguments that may not be used to initialize the
     * LDAPSearch tool.
     */
    @DataProvider(name = "invalidArgs")
    public Object[][] getInvalidArgumentLists() {
        final List<List<String>> argLists = new ArrayList<>();
        final List<LocalizableMessage> reasonList = new ArrayList<>();

        argLists.add(args());
        reasonList.add(ERR_ARGPARSER_TOO_FEW_TRAILING_ARGUMENTS.get(1));

        addValueNeededShortArgs(argLists, reasonList,
                "b", "D", "w", "j", "Y", "K", "P", "W", "h", "p", "J", "z", "l", "s", "a", "o");
        addValueNeededLongArgs(argLists, reasonList,
                               "assertionFilter", "matchedValuesFilter", "hostname", "port", "control");

        argLists.add(args("(objectClass=*)"));
        reasonList.add(ERR_ARGPARSER_NO_VALUE_FOR_REQUIRED_ARG.get("baseDN"));

        argLists.add(args("-b", ""));
        reasonList.add(ERR_ARGPARSER_TOO_FEW_TRAILING_ARGUMENTS.get(1));

        argLists.add(args("-b", "", "--assertionFilter", "(invalidfilter)", "(objectClass=*)"));
        reasonList.add(ERR_LDAP_FILTER_NO_EQUAL_SIGN.get("(invalidfilter)", 1, 14));

        argLists.add(args("-b", "", "--matchedValuesFilter", "(invalidfilter)", "(objectClass=*)"));
        reasonList.add(ERR_LDAP_FILTER_NO_EQUAL_SIGN.get("(invalidfilter)", 1, 14));

        argLists.add(args("-D", "cn=Directory Manager",
                          "-j", "src/test/resources/dummy-truststore",
                          "-w", "password",
                          "-b", "",
                          "(objectClass=*)"));
        reasonList.add(ERR_TOOL_CONFLICTING_ARGS.get("bindPassword", "bindPasswordFile"));

        argLists.add(args("-b", "", "-J", "1.2.3.4:invalidcriticality", "(objectClass=*)"));
        reasonList.add(ERR_TOOL_INVALID_CONTROL_STRING.get("1.2.3.4:invalidcriticality"));

        argLists.add(args("-b", "", "-s", "invalid", "(objectClass=*)"));
        reasonList.add(ERR_MCARG_VALUE_NOT_ALLOWED.get("searchScope", "invalid"));

        argLists.add(args("-b", "", "-a", "invalid", "(objectClass=*)"));
        reasonList.add(ERR_MCARG_VALUE_NOT_ALLOWED.get("dereferencePolicy", "invalid"));

        argLists.add(args("-b", "", "-C", "invalid", "(objectClass=*)"));
        reasonList.add(ERR_PSEARCH_DOESNT_START_WITH_PS.get("invalid"));

        argLists.add(args("-b", "", "-C", "ps:invalid", "(objectClass=*)"));
        reasonList.add(ERR_PSEARCH_INVALID_CHANGE_TYPE.get("invalid"));

        argLists.add(args("-Z", "-q", "-b", "", "(objectClass=*"));
        reasonList.add(ERR_TOOL_CONFLICTING_ARGS.get("useStartTLS", "useSSL"));

        argLists.add(args("-p", "nonnumeric", "-b", "", "(objectClass=*)"));
        reasonList.add(ERR_ARG_CANNOT_DECODE_AS_INT.get("nonnumeric", "port"));

        argLists.add(args("-p", "999999", "-b", "", "(objectClass=*)"));
        reasonList.add(ERR_INTARG_VALUE_ABOVE_UPPER_BOUND.get("port", 999999, 65535));

        argLists.add(args("-z", "nonnumeric", "-b", "", "(objectClass=*)"));
        reasonList.add(ERR_ARG_CANNOT_DECODE_AS_INT.get("nonnumeric", "sizeLimit"));

        argLists.add(args("-l", "nonnumeric", "-b", "", "(objectClass=*)"));
        reasonList.add(ERR_ARG_CANNOT_DECODE_AS_INT.get("nonnumeric", "timeLimit"));

        return toDataProviderArray(argLists, reasonList);
    }

    @Test
    public void testLdapSearch() throws Exception {
        runToolOnMockedServer();
    }

    @Test
    public void testLdapSearchWithAssertionFilter() throws Exception {
        controls.add(AssertionRequestControl.newControl(true, Filter.or(Filter.equality("uid", "user.1"),
                                                                        Filter.greaterOrEqual("age", "40"))));
        runToolOnMockedServer("--assertionFilter", "(|(uid=user.1)(age>=40))");
    }

    @Test
    public void testLdapSearchWithPersistentSearch() throws Exception {
        controls.add(PersistentSearchRequestControl.newControl(true, false, true, PersistentSearchChangeType.ADD));
        runToolOnMockedServer("-C", "ps:add:0:1");
    }

    @Test
    public void testLdapSearchWithProxyAuthz() throws Exception {
        controls.add(ProxiedAuthV2RequestControl.newControl("dn: uid=marvin"));
        runToolOnMockedServer("-Y", "dn: uid=marvin");
    }

    @Test
    public void testLdapSearchWithGetEffectiveRightsProxyAuthz() throws Exception {
        controls.add(GetEffectiveRightsRequestControl.newControl(false, "uid=marvin", "description", "objectclass"));
        runToolOnMockedServer("-g", "dn: uid=marvin",
                              "-e", "description",
                              "-e", "objectclass");
    }

    @Test
    public void testLdapSearchWithVLVControl() throws Exception {
        controls.add(ServerSideSortRequestControl.newControl(false, "sn"));
        controls.add(VirtualListViewRequestControl.newOffsetControl(true, 10, 2, 1, 3, null));
        runToolOnMockedServer("-G", "1:3:10:2",
                              "--sortOrder", "sn");
    }

    @Test
    public void testLdapSearchWithMatchedValuesFilter() throws Exception {
        controls.add(MatchedValuesRequestControl.newControl(true, "uid=user.1*"));
        runToolOnMockedServer("--matchedValuesFilter", "uid=user.1*");
    }

    @Test
    public void testLdapSearchWithSubEntriesControl() throws Exception {
        controls.add(GenericControl.newControl("1.2.3.4.5.6.7.8", false, "myvalue"));
        controls.add(SubentriesRequestControl.newControl(true, true));
        runToolOnMockedServer("-J", "1.2.3.4.5.6.7.8:false:myvalue",
                              "--subEntries");
    }

    @Test
    public void testLdapSearchWithSubEntriesControlDryRun() throws Exception {
        controls.add(GenericControl.newControl("1.2.3.4.5.6.7.8", false, "myvalue"));
        controls.add(SubentriesRequestControl.newControl(true, true));
        runToolOnMockedServer("-J", "1.2.3.4.5.6.7.8:false:myvalue",
                              "--subEntries",
                              "-n");
    }

    @Test
    public void testLdapSearchWithSimplePaged() throws Exception {
        controls.add(SimplePagedResultsControl.newControl(true, 10, ByteString.empty()));
        runToolOnMockedServer("--simplePageSize", "10");
    }

    @Override
    int runToolOnMockedServer(final String... additionalArgs) {
        final int res = runTool(buildArgs().add("-h", server.getHostName())
                                           .add("-p", server.getPort())
                                           .add("-D", DIRECTORY_MANAGER)
                                           .add("-w", "password")
                                           .add("-b", "ou=people,dc=example,dc=com")
                                           .add("-a", "find")
                                           .add("-A")
                                           .add("-s", "subordinates")
                                           .add("-l", "120")
                                           .add("-z", "100")
                                           .addAll(additionalArgs)
                                           .addAll("(st=Alabama)", "uid", "objectclass", "description")
                                           .toArray());
        assertThat(res).isEqualTo(ResultCode.SUCCESS.intValue());
        return res;
    }

    @Override
    ToolConsoleApplication createInstance() {
        return new LDAPSearch(outStream, errStream);
    }
}
