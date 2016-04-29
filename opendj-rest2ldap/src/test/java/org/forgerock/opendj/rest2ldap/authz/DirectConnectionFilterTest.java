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

import static org.fest.assertions.Assertions.*;
import static org.forgerock.opendj.ldap.LdapException.*;
import static org.forgerock.opendj.ldap.ResultCode.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.ExecutionException;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.rest2ldap.AuthenticatedConnectionContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.testng.ForgeRockTestCase;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class DirectConnectionFilterTest extends ForgeRockTestCase {

    private ConnectionFactory connectionFactory;
    private Connection connection;
    private DirectConnectionFilter filter;

    @BeforeMethod
    public void setUp() {
        connectionFactory = mock(ConnectionFactory.class);
        connection = mock(Connection.class);
        filter = new DirectConnectionFilter(connectionFactory);
    }

    @Test
    public void testInjectAuthenticatedConnectionContext() {
        when(connectionFactory.getConnectionAsync()).thenReturn(newResultPromise(connection));
        final Handler handler = mock(Handler.class);
        final ArgumentCaptor<Context> captureContext = ArgumentCaptor.forClass(Context.class);
        when(handler.handle(captureContext.capture(), any(Request.class)))
                .thenReturn(Response.newResponsePromise(new Response()));

        filter.filter(new RootContext(), new Request(), handler);

        assertThat(captureContext.getValue().asContext(AuthenticatedConnectionContext.class).getConnection())
                .isSameAs(connection);
        verify(connection).close();
    }

    @Test
    public void testErrorResponseSentIfCannotGetConnection() throws InterruptedException, ExecutionException {
        when(connectionFactory.getConnectionAsync()).thenReturn(newExceptionPromise(ADMIN_LIMIT_EXCEEDED));

        final Response response = filter.filter(new RootContext(), new Request(), mock(Handler.class)).get();

        assertThat(response.getStatus()).isEqualTo(Status.PAYLOAD_TOO_LARGE);
    }

    private static Promise<Connection, LdapException> newResultPromise(Connection connection) {
        return Promises.<Connection, LdapException> newResultPromise(connection);
    }

    private static Promise<Connection, LdapException> newExceptionPromise(ResultCode result) {
        return Promises.<Connection, LdapException> newExceptionPromise(newLdapException(result));
    }
}
