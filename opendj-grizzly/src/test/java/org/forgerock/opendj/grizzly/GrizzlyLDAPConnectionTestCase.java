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
 * Copyright 2013-2015 ForgeRock AS.
 */

package org.forgerock.opendj.grizzly;

import static org.fest.assertions.Assertions.assertThat;
import static org.forgerock.util.time.Duration.duration;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import static org.forgerock.opendj.ldap.LDAPConnectionFactory.REQUEST_TIMEOUT;
import java.net.InetSocketAddress;

import org.forgerock.opendj.ldap.Connections;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LDAPListener;
import org.forgerock.opendj.ldap.RequestHandler;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SdkTestCase;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.TestCaseUtils;
import org.forgerock.opendj.ldap.TimeoutResultException;
import org.forgerock.opendj.ldap.controls.PersistentSearchRequestControl;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.util.Options;
import org.forgerock.util.promise.ExceptionHandler;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.Test;

/**
 * Tests LDAP connection implementation class.
 */
@SuppressWarnings("javadoc")
public class GrizzlyLDAPConnectionTestCase extends SdkTestCase {

    /**
     * Tests that a normal request is subject to client side timeout checking.
     */
    @Test
    public void testRequestTimeout() throws Exception {
        doTestRequestTimeout(false);
    }

    /**
     * Tests that a persistent search request is not subject to client side
     * timeout checking.
     */
    @Test
    public void testRequestTimeoutPersistentSearch() throws Exception {
        doTestRequestTimeout(true);
    }

    private void doTestRequestTimeout(boolean isPersistentSearch) throws Exception {
        InetSocketAddress address = TestCaseUtils.findFreeSocketAddress();

        /*
         * Use a mock server implementation which will ignore incoming requests
         * and leave the client waiting forever for a response.
         */
        @SuppressWarnings("unchecked")
        LDAPListener listener =
                new LDAPListener(address, Connections
                        .newServerConnectionFactory(mock(RequestHandler.class)));

        /*
         * Use a very long time out in order to prevent the timeout thread from
         * triggering the timeout.
         */
        GrizzlyLDAPConnectionFactory factory = new GrizzlyLDAPConnectionFactory(address.getHostName(),
                                                                  address.getPort(),
                                                                  Options.defaultOptions()
                                                                         .set(REQUEST_TIMEOUT, duration("100 ms")));
        GrizzlyLDAPConnection connection = (GrizzlyLDAPConnection) factory.getConnectionAsync().getOrThrow();
        try {
            SearchRequest request =
                    Requests.newSearchRequest("dc=test", SearchScope.BASE_OBJECT, "(objectClass=*)");
            if (isPersistentSearch) {
                request.addControl(PersistentSearchRequestControl.newControl(true, true, true));
            }
            SearchResultHandler searchHandler = mock(SearchResultHandler.class);
            @SuppressWarnings("unchecked")
            ExceptionHandler<LdapException> exceptionHandler = mock(ExceptionHandler.class);
            connection.searchAsync(request, null, searchHandler).thenOnException(exceptionHandler);

            // Pass in a time which is guaranteed to trigger expiration.
            connection.handleTimeout(System.currentTimeMillis() + 1000000);
            if (isPersistentSearch) {
                verifyZeroInteractions(searchHandler);
            } else {
                ArgumentCaptor<LdapException> arg = ArgumentCaptor.forClass(LdapException.class);
                verify(exceptionHandler).handleException(arg.capture());
                assertThat(arg.getValue()).isInstanceOf(TimeoutResultException.class);
                assertThat(arg.getValue().getResult().getResultCode()).isEqualTo(
                        ResultCode.CLIENT_SIDE_TIMEOUT);
            }
        } finally {
            connection.close();
            listener.close();
            factory.close();
        }
    }

}
