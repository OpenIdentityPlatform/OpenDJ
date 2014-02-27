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
 *      Copyright 2013-2014 ForgeRock AS.
 */

package com.forgerock.opendj.ldap;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import org.forgerock.opendj.ldap.Connections;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LDAPListener;
import org.forgerock.opendj.ldap.LDAPOptions;
import org.forgerock.opendj.ldap.RequestHandler;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.TestCaseUtils;
import org.forgerock.opendj.ldap.TimeoutResultException;
import org.forgerock.opendj.ldap.controls.PersistentSearchRequestControl;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.Test;

/**
 * Tests LDAP connection implementation class.
 */
@SuppressWarnings("javadoc")
public class LDAPConnectionTestCase extends LDAPTestCase {

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
        LDAPConnectionFactory factory =
                new LDAPConnectionFactory(address, new LDAPOptions().setTimeout(100,
                        TimeUnit.SECONDS));
        LDAPConnection connection = (LDAPConnection) factory.getConnection();
        try {
            SearchRequest request =
                    Requests.newSearchRequest("dc=test", SearchScope.BASE_OBJECT, "(objectClass=*)");
            if (isPersistentSearch) {
                request.addControl(PersistentSearchRequestControl.newControl(true, true, true));
            }
            SearchResultHandler handler = mock(SearchResultHandler.class);
            connection.searchAsync(request, null, handler);

            // Pass in a time which is guaranteed to trigger expiration.
            connection.handleTimeout(System.currentTimeMillis() + 1000000);
            if (isPersistentSearch) {
                verifyZeroInteractions(handler);
            } else {
                ArgumentCaptor<ErrorResultException> arg =
                        ArgumentCaptor.forClass(ErrorResultException.class);
                verify(handler).handleErrorResult(arg.capture());
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
