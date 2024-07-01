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
import static org.forgerock.opendj.ldap.Connections.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.forgerock.opendj.ldap.Connections.LeastRequestsDispatcher;
import org.forgerock.opendj.ldap.RequestLoadBalancer.PartitionedRequest;
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
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.SimpleBindRequest;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;
import org.testng.annotations.Test;

import com.forgerock.opendj.ldap.controls.AffinityControl;

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
    public void affinityRequestLoadBalancerUsesConsistentIndexing() {
        final Function<Request, PartitionedRequest, NeverThrowsException> f =
                newAffinityRequestLoadBalancerNextFunction(asList(mock(ConnectionFactory.class),
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

    @Test
    public void leastRequestsDispatcherMustChooseTheLessSaturatedServer() {
        LeastRequestsDispatcher dispatcher = new Connections.LeastRequestsDispatcher(3);
        Function<Request, PartitionedRequest, NeverThrowsException> next =
                newLeastRequestsLoadBalancerNextFunction(dispatcher);
        Function<Integer, Void, NeverThrowsException> end =
                newLeastRequestsLoadBalancerEndOfRequestFunction(dispatcher);

        final SearchRequest[] reqs = new SearchRequest[11];
        for (int i = 0; i < reqs.length; i++) {
            reqs[i] = mock(SearchRequest.class);
        }
        assertThat(next.apply(reqs[0]).getServerIndex()).isEqualTo(0);  // number of reqs = [1, 0, 0]
        assertThat(next.apply(reqs[1]).getServerIndex()).isEqualTo(1);  // number of reqs = [1, 1, 0]
        assertThat(next.apply(reqs[2]).getServerIndex()).isEqualTo(2);  // number of reqs = [1, 1, 1]
        end.apply(1);                                                   // number of reqs = [1, 0, 1]
        assertThat(next.apply(reqs[3]).getServerIndex()).isEqualTo(1);  // number of reqs = [1, 1, 1]
        end.apply(1);                                                   // number of reqs = [1, 0, 1]
        assertThat(next.apply(reqs[5]).getServerIndex()).isEqualTo(1);  // number of reqs = [1, 1, 1]
        assertThat(next.apply(reqs[6]).getServerIndex()).isEqualTo(0);  // number of reqs = [2, 1, 1]
        assertThat(next.apply(reqs[7]).getServerIndex()).isEqualTo(1);  // number of reqs = [2, 2, 1]
        assertThat(next.apply(reqs[8]).getServerIndex()).isEqualTo(2);  // number of reqs = [2, 2, 2]
        assertThat(next.apply(reqs[9]).getServerIndex()).isEqualTo(0);  // number of reqs = [3, 2, 2]
        end.apply(2);                                                   // number of reqs = [3, 2, 1]
        assertThat(next.apply(reqs[10]).getServerIndex()).isEqualTo(2); // number of reqs = [3, 2, 2]
    }

    @Test
    public void leastRequestsDispatcherMustTakeConnectionAffinityControlIntoAccount() {
        LeastRequestsDispatcher dispatcher = new Connections.LeastRequestsDispatcher(3);
        Function<Request, PartitionedRequest, NeverThrowsException> next =
                newLeastRequestsLoadBalancerNextFunction(dispatcher);

        final Request[] reqs = new Request[11];
        for (int i = 0; i < reqs.length; i++) {
            reqs[i] = Requests.newDeleteRequest("o=example");
        }
        assertThat(next.apply(reqs[0]).getServerIndex()).isEqualTo(0); // number of reqs = [1, 0, 0]

        // control should now force the same connection three times
        // control should be removed from the RequestWithIndex object used to perform the actual query
        for (int i = 1; i <= 3; i++) {
            reqs[i].addControl(AffinityControl.newControl(ByteString.valueOfUtf8("val"), false));
        }
        PartitionedRequest req1 = next.apply(reqs[1]);
        assertThat(req1.getServerIndex()).isEqualTo(0); // number of reqs = [2, 0, 0]
        assertThat(req1.getRequest().getControls()).isEmpty();
        assertThat(reqs[1].getControls()).hasSize(1);

        PartitionedRequest req2 = next.apply(reqs[2]);
        assertThat(req2.getServerIndex()).isEqualTo(0); // number of reqs = [3, 0, 0]
        assertThat(req2.getRequest().getControls()).isEmpty();
        assertThat(reqs[2].getControls()).hasSize(1);

        PartitionedRequest req3 = next.apply(reqs[3]);
        assertThat(req3.getServerIndex()).isEqualTo(0); // number of reqs = [4, 0, 0]
        assertThat(req3.getRequest().getControls()).isEmpty();
        assertThat(reqs[3].getControls()).hasSize(1);

        // back to default "saturation-based" behavior
        assertThat(next.apply(reqs[4]).getServerIndex()).isEqualTo(1); // number of reqs = [4, 1, 0]
        assertThat(next.apply(reqs[5]).getServerIndex()).isEqualTo(2); // number of reqs = [4, 1, 1]
        assertThat(next.apply(reqs[6]).getServerIndex()).isEqualTo(1); // number of reqs = [4, 2, 1]
        assertThat(next.apply(reqs[7]).getServerIndex()).isEqualTo(2); // number of reqs = [4, 2, 2]
        assertThat(next.apply(reqs[8]).getServerIndex()).isEqualTo(1); // number of reqs = [4, 3, 2]
        assertThat(next.apply(reqs[9]).getServerIndex()).isEqualTo(2); // number of reqs = [4, 3, 3]
    }

    private void assertRequestsAreRoutedConsistently(
            final Function<Request, PartitionedRequest, NeverThrowsException> f, final Request r,
            final int firstExpectedIndex, final int secondExpectedIndex) {
        assertThat(index(f, r)).isEqualTo(firstExpectedIndex);
        assertThat(index(f, r)).isEqualTo(secondExpectedIndex);
    }

    private int index(final Function<Request, PartitionedRequest, NeverThrowsException> function,
                      final Request request) {
        return function.apply(request).getServerIndex();
    }
}
