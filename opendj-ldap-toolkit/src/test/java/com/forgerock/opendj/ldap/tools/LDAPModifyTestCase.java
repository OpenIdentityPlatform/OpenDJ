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
import static com.forgerock.opendj.ldap.CoreMessages.ERR_LDAP_FILTER_NO_EQUAL_SIGN;
import static com.forgerock.opendj.ldap.tools.ToolLdapServer.DIRECTORY_MANAGER;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.ERR_TOOL_INVALID_CONTROL_STRING;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.addValueNeededLongArgs;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.addValueNeededShortArgs;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.args;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.createTempFile;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.toDataProviderArray;
import static org.fest.assertions.Assertions.assertThat;
import static org.forgerock.opendj.ldap.ModificationType.ADD;
import static org.forgerock.opendj.ldap.ModificationType.DELETE;
import static org.forgerock.opendj.ldap.ModificationType.REPLACE;

import java.util.ArrayList;
import java.util.List;

import com.forgerock.opendj.ldap.controls.RealAttributesOnlyRequestControl;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LdapResultHandler;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.AssertionRequestControl;
import org.forgerock.opendj.ldap.controls.PostReadRequestControl;
import org.forgerock.opendj.ldap.controls.PreReadRequestControl;
import org.forgerock.opendj.ldap.controls.ProxiedAuthV2RequestControl;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** A set of test cases for the LDAPModify tool. */
@SuppressWarnings("javadoc")
@Test
public class LDAPModifyTestCase extends LDAPToolsTestCase {

    private final class LdapModifyToolLdapServer extends ToolLdapServer {
        private final class LdapModifyRequestHandler extends ToolLdapServer.ToolLdapServerConnection {
            @Override
            public void handleAdd(final Integer requestContext,
                                  final AddRequest request,
                                  final IntermediateResponseHandler intermediateResponseHandler,
                                  final LdapResultHandler<Result> resultHandler) {
                if (request.getName().toString().equals("uid=error")) {
                    errorRaised = true;
                    resultHandler.handleResult(Responses.newResult(ResultCode.INSUFFICIENT_ACCESS_RIGHTS));
                    return;
                }
                assertThat(request.getName().toString()).isEqualTo("uid=marvin");
                assertThatControlsHaveBeenSentInRequest(request.getControls());
                resultHandler.handleResult(Responses.newResult(ResultCode.SUCCESS));
            }

            @Override
            public void handleDelete(final Integer requestContext,
                                     final DeleteRequest request,
                                     final IntermediateResponseHandler intermediateResponseHandler,
                                     final LdapResultHandler<Result> resultHandler) {
                if (errorRaised && !continueOnError) {
                    throw new RuntimeException(
                            "ldapmodify continues to process requests without the --continueOnError flag");
                }
                assertThat(request.getName().toString()).isEqualTo("uid=marvin");
                assertThatControlsHaveBeenSentInRequest(request.getControls());
                resultHandler.handleResult(Responses.newResult(ResultCode.SUCCESS));
            }

            @Override
            public void handleModify(final Integer requestContext,
                                     final ModifyRequest request,
                                     final IntermediateResponseHandler intermediateResponseHandler,
                                     final LdapResultHandler<Result> resultHandler) {
                assertThat(request.getName().toString()).isEqualTo("uid=marvin");
                final List<Modification> modifications = request.getModifications();
                assertThat(modifications.size()).isEqualTo(3);
                assertThatModificationIsEqualTo(modifications.get(0), ADD, "attributetoadd", "value");
                assertThatModificationIsEqualTo(
                        modifications.get(1), REPLACE, "description", "The paranoid android");
                assertThatModificationIsEqualTo(
                        modifications.get(2), DELETE, "todelete", "");
                assertThat(modifications.get(1).getModificationType()).isEqualTo(REPLACE);
                resultHandler.handleResult(Responses.newResult(ResultCode.SUCCESS));
            }

            private void assertThatModificationIsEqualTo(final Modification modification,
                                                         final ModificationType type,
                                                         final String attributeName,
                                                         final String attributeValue) {
                assertThat(modification.getModificationType()).isEqualTo(type);
                assertThat(modification.getAttribute().getAttributeDescriptionAsString()).isEqualTo(attributeName);
                if (type != DELETE) {
                    assertThat(modification.getAttribute().firstValueAsString()).isEqualTo(attributeValue);
                }
            }

            @Override
            public void handleModifyDN(final Integer requestContext,
                                       final ModifyDNRequest request,
                                       final IntermediateResponseHandler intermediateResponseHandler,
                                       final LdapResultHandler<Result> resultHandler) {
                assertThat(request.getName().toString()).isEqualTo("uid=marvin");
                assertThat(request.getNewRDN().toString()).isEqualTo("uid=arthurdent");
                assertThat(request.isDeleteOldRDN()).isTrue();
                assertThatControlsHaveBeenSentInRequest(request.getControls());
                resultHandler.handleResult(Responses.newResult(ResultCode.SUCCESS));
            }
        }

        @Override
        ToolLdapServerConnection newServerConnection() {
            return new LdapModifyRequestHandler();
        }
    }

    @Override
    ToolLdapServer createFakeServer() {
        return new LdapModifyToolLdapServer();
    }

    private boolean continueOnError;
    private boolean errorRaised;

    /**
     * Retrieves sets of invalid arguments that may not be used to initialize
     * the LDAPModify tool.
     *
     * @return Sets of invalid arguments that may not be used to initialize the
     * LDAPModify tool.
     */
    @DataProvider(name = "invalidArgs")
    public Object[][] getInvalidArgumentLists() {
        final List<List<String>> argLists = new ArrayList<>();
        final List<LocalizableMessage> reasonList = new ArrayList<>();

        addValueNeededShortArgs(argLists, reasonList, "D", "w", "j", "Y", "K", "P", "W", "h", "p", "J", "o");
        addValueNeededLongArgs(argLists, reasonList, "assertionFilter", "hostname",
                               "port", "control", "preReadAttributes", "postReadAttributes");

        argLists.add(args("-D", "cn=Directory Manager", "-j", "no.such.file"));
        reasonList.add(ERR_FILEARG_NO_SUCH_FILE.get("no.such.file", "bindPasswordFile"));

        argLists.add(args("-D", "cn=Directory Manager", "-w", "password", "-j", "src/test/resources/dummy-truststore"));
        reasonList.add(ERR_TOOL_CONFLICTING_ARGS.get("bindPassword", "bindPasswordFile"));

        argLists.add(args("-J", "1.2.3.4:invalidcriticality"));
        reasonList.add(ERR_TOOL_INVALID_CONTROL_STRING.get("1.2.3.4:invalidcriticality"));

        argLists.add(args("-Z", "-q"));
        reasonList.add(ERR_TOOL_CONFLICTING_ARGS.get("useStartTLS", "useSSL"));

        argLists.add(args("-p", "nonnumeric"));
        reasonList.add(ERR_ARG_CANNOT_DECODE_AS_INT.get("nonnumeric", "port"));

        argLists.add(args("-p", "999999"));
        reasonList.add(ERR_INTARG_VALUE_ABOVE_UPPER_BOUND.get("port", 999999, 65535));

        argLists.add(args("--assertionFilter", "(invalid)"));
        reasonList.add(ERR_LDAP_FILTER_NO_EQUAL_SIGN.get("(invalid)", 1, 8));

        return toDataProviderArray(argLists, reasonList);
    }

    @Test
    public void testNoContinueOnError() throws Exception {
        final String tmpFilePath = createTempFile("dn: uid=error",
                                                  "changetype: add",
                                                  "description: An error will be raised by server",
                                                  "",
                                                  "dn: uid=marvin",
                                                  "changetype: delete");
        final int res = runTool("-h", server.getHostName(),
                                "-p", server.getPort(),
                                "-D", DIRECTORY_MANAGER,
                                "-w", "password",
                                "-f", tmpFilePath);
        assertThat(res).isEqualTo(ResultCode.INSUFFICIENT_ACCESS_RIGHTS.intValue());
    }

    @Test
    public void testSimpleModifyRequests() throws Exception {
        final String tmpFilePath = createTempFile("dn: uid=marvin",
                                                  "changetype: modify",
                                                  "add: attributetoadd",
                                                  "attributetoadd: value",
                                                  "-",
                                                  "replace: description",
                                                  "description: The paranoid android",
                                                  "-",
                                                  "delete: todelete",
                                                  "");
        final String tmpFilePath2 = createTempFile("dn: uid=marvin",
                                                   "changetype: add");
        runToolOnMockedServer(tmpFilePath, tmpFilePath2);
    }

    @Test
    public void testAddAndDeleteRequests() throws Exception {
        controls.add(AssertionRequestControl.newControl(true,
                Filter.and(Filter.equality("uid", "marvin"), Filter.equality("description", "The paranoid android"))));
        controls.add(PostReadRequestControl.newControl(true, "uid"));
        final String tmpFilePath = createTempFile("dn: uid=marvin",
                                                  "changetype: add",
                                                  "description: The paranoid android",
                                                  "",
                                                  "dn: uid=marvin",
                                                  "changetype: delete");
        runToolOnMockedServer("--postReadAttributes", "uid",
                              "--assertionFilter", "(&(uid=marvin)(description=The paranoid android))",
                              "-f", tmpFilePath);
    }

    @Test
    public void testContinueOnError() throws Exception {
        continueOnError = true;
        controls.add(RealAttributesOnlyRequestControl.newControl(true));
        final String tmpFilePath = createTempFile("dn: uid=error",
                                                  "changetype: add",
                                                  "description: An error will be raised by server",
                                                  "",
                                                  "dn: uid=marvin",
                                                  "changetype: delete");
        runToolOnMockedServer("-c",
                              "-J", "2.16.840.1.113730.3.4.17:true",
                              tmpFilePath);
    }

    @Test
    public void testModifyDN() throws Exception {
        controls.add(ProxiedAuthV2RequestControl.newControl("dn: uid=marvin"));
        controls.add(PreReadRequestControl.newControl(true, "uid"));
        final String tmpFilePath = createTempFile("dn: uid=marvin",
                                                  "changetype: moddn",
                                                  "newrdn: uid=arthurdent",
                                                  "deleteoldrdn: 1");
        runToolOnMockedServer("-c",
                              "--preReadAttributes", "uid",
                              "-Y", "dn: uid=marvin",
                              tmpFilePath);
    }

    @Test
    public void testModifyDNDryRun() throws Exception {
        controls.add(ProxiedAuthV2RequestControl.newControl("dn: uid=marvin"));
        controls.add(PreReadRequestControl.newControl(true, "uid"));
        final String tmpFilePath = createTempFile("dn: uid=marvin",
                                                  "changetype: moddn",
                                                  "newrdn: uid=arthurdent",
                                                  "deleteoldrdn: 1");
        runToolOnMockedServer("-c",
                              "--preReadAttributes", "uid",
                              "-Y", "dn: uid=marvin",
                              "-n",
                              tmpFilePath);
    }

    @Override
    ToolConsoleApplication createInstance() {
        return new LDAPModify(outStream, errStream);
    }
}

