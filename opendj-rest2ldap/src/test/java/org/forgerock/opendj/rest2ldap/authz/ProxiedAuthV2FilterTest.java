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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap.authz;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.opendj.ldap.AbstractSynchronousConnection;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionEventListener;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.controls.ProxiedAuthV2RequestControl;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.rest2ldap.AuthenticatedConnectionContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.services.context.SecurityContext;
import org.forgerock.testng.ForgeRockTestCase;
import org.forgerock.util.promise.Promises;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class ProxiedAuthV2FilterTest extends ForgeRockTestCase {

    private ProxiedAuthV2Filter filter;
    private ArgumentCaptor<Context> captureContext;
    private Handler handler;

    @BeforeMethod
    public void setUp() {
        captureContext = ArgumentCaptor.forClass(Context.class);
        handler = mock(Handler.class);
    }

    @Test
    public void testConnectionIsUsingProxiedAuthControlOnRequests() throws Exception {
        final ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        when(connectionFactory.getConnectionAsync())
                .thenReturn(Promises.<Connection, LdapException> newResultPromise(new CheckConnection() {
                    @Override
                    void verifyRequest(org.forgerock.opendj.ldap.requests.Request request) throws LdapException {
                        assertThat(request.getControls()).isNotEmpty();
                        final ProxiedAuthV2RequestControl control;
                        try {
                            control = request.getControl(ProxiedAuthV2RequestControl.DECODER, new DecodeOptions());
                        } catch (DecodeException e) {
                            throw LdapException.newLdapException(ResultCode.OPERATIONS_ERROR);
                        }
                        assertThat(control.getValue())
                                .isEqualTo(ByteString.valueOfUtf8("dn:uid=whatever,ou=people,dc=com"));
                    }
                }));
        filter = new ProxiedAuthV2Filter(connectionFactory);

        final Map<String, Object> authz = new HashMap<>();
        authz.put(SecurityContext.AUTHZID_DN, "uid=whatever,ou=people,dc=com");
        final SecurityContext securityContext = new SecurityContext(new RootContext(), "whatever", authz);

        when(handler.handle(captureContext.capture(), any(Request.class)))
                .thenReturn(Response.newResponsePromise(new Response()));
        filter.filter(securityContext, new Request(), handler);

        final Connection proxiedConnection = captureContext.getValue().asContext(AuthenticatedConnectionContext.class)
                .getConnection();
        proxiedConnection.add(Requests.newAddRequest("cn=test"));
        proxiedConnection.applyChange(Requests.newChangeRecord("dn: cn=test", "changetype: delete"));
        proxiedConnection.compare(Requests.newCompareRequest("cn=test", "foo", "bar"));
        proxiedConnection.search(Requests.newSearchRequest("cn=test", SearchScope.BASE_OBJECT, "(cn=test)", ""));
        proxiedConnection.modify(Requests.newModifyRequest("cn=test"));
        proxiedConnection.delete("cn=test");
        proxiedConnection.deleteSubtree("cn=test");
        proxiedConnection.extendedRequest("blah", ByteString.empty());
        proxiedConnection.modifyDN("cn=foo", "cn=bar");
    }

    private abstract static class CheckConnection extends AbstractSynchronousConnection {

        abstract void verifyRequest(org.forgerock.opendj.ldap.requests.Request request) throws LdapException;

        @Override
        public Result add(AddRequest request) throws LdapException {
            verifyRequest(request);
            return Responses.newResult(ResultCode.SUCCESS);
        }

        @Override
        public BindResult bind(BindRequest request) throws LdapException {
            return Responses.newBindResult(ResultCode.SUCCESS);
        }

        @Override
        public void close(UnbindRequest request, String reason) {
        }

        @Override
        public CompareResult compare(CompareRequest request) throws LdapException {
            verifyRequest(request);
            return Responses.newCompareResult(ResultCode.SUCCESS);
        }

        @Override
        public Result delete(DeleteRequest request) throws LdapException {
            verifyRequest(request);
            return Responses.newResult(ResultCode.SUCCESS);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <R extends ExtendedResult> R extendedRequest(ExtendedRequest<R> request,
                IntermediateResponseHandler handler) throws LdapException {
            verifyRequest(request);
            return (R) Responses.newGenericExtendedResult(ResultCode.SUCCESS);
        }

        @Override
        public Result search(SearchRequest request, SearchResultHandler handler) throws LdapException {
            verifyRequest(request);
            return Responses.newResult(ResultCode.SUCCESS);
        }

        @Override
        public Result modify(ModifyRequest request) throws LdapException {
            verifyRequest(request);
            return Responses.newResult(ResultCode.SUCCESS);
        }

        @Override
        public Result modifyDN(ModifyDNRequest request) throws LdapException {
            verifyRequest(request);
            return Responses.newResult(ResultCode.SUCCESS);
        }

        @Override
        public void addConnectionEventListener(ConnectionEventListener listener) {
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public boolean isValid() {
            return false;
        }

        @Override
        public void removeConnectionEventListener(ConnectionEventListener listener) {
        }

        @Override
        public String toString() {
            return null;
        }

    }
}
