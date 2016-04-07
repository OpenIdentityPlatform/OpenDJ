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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.opendj.ldap.Connections.newShardedRequestLoadBalancerFunction;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.CRAMMD5SASLBindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.DigestMD5SASLBindRequest;
import org.forgerock.opendj.ldap.requests.GSSAPISASLBindRequest;
import org.forgerock.opendj.ldap.requests.GenericExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.PasswordModifyExtendedRequest;
import org.forgerock.opendj.ldap.requests.PlainSASLBindRequest;
import org.forgerock.opendj.ldap.requests.Request;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.SimpleBindRequest;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ConnectionsTestCase extends SdkTestCase {

    @Test
    public void testUncloseableConnectionClose() throws Exception {
        final Connection connection = mock(Connection.class);
        final Connection uncloseable = Connections.uncloseable(connection);
        uncloseable.close();
        verifyZeroInteractions(connection);
    }

    @Test
    public void testUncloseableConnectionNotClose() throws Exception {
        final Connection connection = mock(Connection.class);
        final Connection uncloseable = Connections.uncloseable(connection);
        uncloseable.applyChange(null);
        verify(connection).applyChange(null);
    }

    @Test
    public void testUncloseableConnectionUnbind() throws Exception {
        final Connection connection = mock(Connection.class);
        final Connection uncloseable = Connections.uncloseable(connection);
        uncloseable.close(null, null);
        verifyZeroInteractions(connection);
    }

    @Test
    public void shardedRequestLoadBalancerUsesConsistentIndexing() {
        final Function<Request, Integer, NeverThrowsException> f =
                newShardedRequestLoadBalancerFunction(asList(mock(ConnectionFactory.class),
                                                             mock(ConnectionFactory.class)));

        // These two DNs have a different hash code.
        final DN dn1 = DN.valueOf("cn=target1,dc=example,dc=com");
        final DN dn2 = DN.valueOf("cn=target2,dc=example,dc=com");

        final AddRequest addRequest = mock(AddRequest.class);
        when(addRequest.getName()).thenReturn(dn1, dn2);
        final int dn1index = index(f, addRequest);
        final int dn2index = index(f, addRequest);
        assertThat(dn1index).isNotEqualTo(dn2index);

        final SimpleBindRequest simpleBindRequest = mock(SimpleBindRequest.class);
        when(simpleBindRequest.getName()).thenReturn(dn1.toString(), dn2.toString());
        assertRequestsAreRoutedConsistently(f, simpleBindRequest, dn1index, dn2index);

        final CompareRequest compareRequest = mock(CompareRequest.class);
        when(compareRequest.getName()).thenReturn(dn1, dn2);
        assertRequestsAreRoutedConsistently(f, compareRequest, dn1index, dn2index);

        final DeleteRequest deleteRequest = mock(DeleteRequest.class);
        when(deleteRequest.getName()).thenReturn(dn1, dn2);
        assertRequestsAreRoutedConsistently(f, deleteRequest, dn1index, dn2index);

        final ModifyRequest modifyRequest = mock(ModifyRequest.class);
        when(modifyRequest.getName()).thenReturn(dn1, dn2);
        assertRequestsAreRoutedConsistently(f, modifyRequest, dn1index, dn2index);

        final ModifyDNRequest modifyDNRequest = mock(ModifyDNRequest.class);
        when(modifyDNRequest.getName()).thenReturn(dn1, dn2);
        assertRequestsAreRoutedConsistently(f, modifyDNRequest, dn1index, dn2index);

        final SearchRequest searchRequest = mock(SearchRequest.class);
        when(searchRequest.getName()).thenReturn(dn1, dn2);
        assertRequestsAreRoutedConsistently(f, searchRequest, dn1index, dn2index);

        // Authzid based operations.
        final String authzid1 = "dn:" + dn1.toString();
        final String authzid2 = "dn:" + dn2.toString();

        final PasswordModifyExtendedRequest passwordModifyRequest = mock(PasswordModifyExtendedRequest.class);
        when(passwordModifyRequest.getUserIdentityAsString()).thenReturn(authzid1, authzid2);
        assertRequestsAreRoutedConsistently(f, passwordModifyRequest, dn1index, dn2index);

        final PlainSASLBindRequest plainSASLBindRequest = mock(PlainSASLBindRequest.class);
        when(plainSASLBindRequest.getAuthenticationID()).thenReturn(authzid1, authzid2);
        assertRequestsAreRoutedConsistently(f, plainSASLBindRequest, dn1index, dn2index);

        final CRAMMD5SASLBindRequest cramMD5SASLBindRequest = mock(CRAMMD5SASLBindRequest.class);
        when(cramMD5SASLBindRequest.getAuthenticationID()).thenReturn(authzid1, authzid2);
        assertRequestsAreRoutedConsistently(f, cramMD5SASLBindRequest, dn1index, dn2index);

        final DigestMD5SASLBindRequest digestMD5SASLBindRequest = mock(DigestMD5SASLBindRequest.class);
        when(digestMD5SASLBindRequest.getAuthenticationID()).thenReturn(authzid1, authzid2);
        assertRequestsAreRoutedConsistently(f, digestMD5SASLBindRequest, dn1index, dn2index);

        final GSSAPISASLBindRequest gssapiSASLBindRequest = mock(GSSAPISASLBindRequest.class);
        when(gssapiSASLBindRequest.getAuthenticationID()).thenReturn(authzid1, authzid2);
        assertRequestsAreRoutedConsistently(f, gssapiSASLBindRequest, dn1index, dn2index);

        // Requests that have no target will return a random index, but since we only have one factory the index will
        // always be 0.
        final GenericExtendedRequest genericExtendedRequest = mock(GenericExtendedRequest.class);
        assertThat(index(f, genericExtendedRequest)).isBetween(0, 1);
    }

    private void assertRequestsAreRoutedConsistently(final Function<Request, Integer, NeverThrowsException> f,
                                                     final Request r,
                                                     final int firstExpectedIndex,
                                                     final int secondExpectedIndex) {
        assertThat(index(f, r)).isEqualTo(firstExpectedIndex);
        assertThat(index(f, r)).isEqualTo(secondExpectedIndex);
    }

    private int index(final Function<Request, Integer, NeverThrowsException> function, final Request request) {
        return function.apply(request);
    }
}
