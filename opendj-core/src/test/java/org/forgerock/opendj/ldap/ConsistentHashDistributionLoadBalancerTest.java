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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.opendj.ldap.Connections.newFixedSizeDistributionLoadBalancer;
import static org.forgerock.opendj.ldap.Filter.alwaysTrue;
import static org.forgerock.opendj.ldap.SearchScope.BASE_OBJECT;
import static org.forgerock.opendj.ldap.SearchScope.SINGLE_LEVEL;
import static org.forgerock.opendj.ldap.SearchScope.SUBORDINATES;
import static org.forgerock.opendj.ldap.SearchScope.WHOLE_SUBTREE;
import static org.forgerock.opendj.ldap.TestCaseUtils.mockConnectionFactory;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.forgerock.opendj.ldap.responses.Responses.newBindResult;
import static org.forgerock.opendj.ldap.responses.Responses.newCompareResult;
import static org.forgerock.opendj.ldap.responses.Responses.newResult;
import static org.forgerock.opendj.ldap.spi.LdapPromises.newSuccessfulLdapPromise;
import static org.forgerock.util.Options.defaultOptions;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ConsistentHashDistributionLoadBalancerTest extends SdkTestCase {
    private static final DN PARTITION_BASE_DN = DN.valueOf("ou=people,dc=example,dc=com");
    private static final DN DN_1_BELOW_PARTITION_BASE_DN = PARTITION_BASE_DN.child("uid=bjensen");
    private static final DN DN_2_BELOW_PARTITION_BASE_DN = DN_1_BELOW_PARTITION_BASE_DN.child("cn=prefs");
    private static final DN DN_ABOVE_PARTITION_BASE_DN = PARTITION_BASE_DN.parent();
    private static final DN UNPARTITIONED_DN = DN.valueOf("ou=groups,dc=example,dc=com");
    private static final LdapPromise<Result> SUCCESS = newSuccessfulLdapPromise(newResult(ResultCode.SUCCESS));
    private static final LdapPromise<BindResult> BIND_SUCCESS =
            newSuccessfulLdapPromise(newBindResult(ResultCode.SUCCESS));
    private static final LdapPromise<CompareResult> COMPARE_SUCCESS =
            newSuccessfulLdapPromise(newCompareResult(ResultCode.SUCCESS));
    private static final int P1_HASH = 0x00000000;
    private static final int P2_HASH = 0x80000000;
    @Mock
    private Connection partition1Conn;
    @Mock
    private Connection partition2Conn;
    @Mock
    private Function<Object, Integer, NeverThrowsException> hashFunction;
    private ConnectionFactory partition1;
    private ConnectionFactory partition2;
    private ConnectionFactory loadBalancer;

    @BeforeMethod
    public void beforeMethod() {
        initMocks(this);

        partition1 = mockConnectionFactory(partition1Conn);
        partition2 = mockConnectionFactory(partition2Conn);

        when(hashFunction.apply(any())).thenReturn(P1_HASH, P2_HASH);

        final ConsistentHashMap<ConnectionFactory> partitions = new ConsistentHashMap<>(hashFunction);
        partitions.put("P1", partition1, 1);
        partitions.put("P2", partition2, 1);

        loadBalancer = newFixedSizeDistributionLoadBalancer(PARTITION_BASE_DN, partitions, defaultOptions());

        when(partition1Conn.addAsync(any(AddRequest.class),
                                     any(IntermediateResponseHandler.class))).thenReturn(SUCCESS);
        when(partition2Conn.addAsync(any(AddRequest.class),
                                     any(IntermediateResponseHandler.class))).thenReturn(SUCCESS);

        when(partition1Conn.bindAsync(any(BindRequest.class),
                                      any(IntermediateResponseHandler.class))).thenReturn(BIND_SUCCESS);
        when(partition2Conn.bindAsync(any(BindRequest.class),
                                      any(IntermediateResponseHandler.class))).thenReturn(BIND_SUCCESS);

        when(partition1Conn.compareAsync(any(CompareRequest.class),
                                         any(IntermediateResponseHandler.class))).thenReturn(COMPARE_SUCCESS);
        when(partition2Conn.compareAsync(any(CompareRequest.class),
                                         any(IntermediateResponseHandler.class))).thenReturn(COMPARE_SUCCESS);

        when(partition1Conn.deleteAsync(any(DeleteRequest.class),
                                        any(IntermediateResponseHandler.class))).thenReturn(SUCCESS);
        when(partition2Conn.deleteAsync(any(DeleteRequest.class),
                                        any(IntermediateResponseHandler.class))).thenReturn(SUCCESS);

        when(partition1Conn.extendedRequestAsync(any(ExtendedRequest.class),
                                                 any(IntermediateResponseHandler.class))).thenReturn(SUCCESS);
        when(partition2Conn.extendedRequestAsync(any(ExtendedRequest.class),
                                                 any(IntermediateResponseHandler.class))).thenReturn(SUCCESS);

        when(partition1Conn.modifyAsync(any(ModifyRequest.class),
                                        any(IntermediateResponseHandler.class))).thenReturn(SUCCESS);
        when(partition2Conn.modifyAsync(any(ModifyRequest.class),
                                        any(IntermediateResponseHandler.class))).thenReturn(SUCCESS);

        when(partition1Conn.modifyDNAsync(any(ModifyDNRequest.class),
                                          any(IntermediateResponseHandler.class))).thenReturn(SUCCESS);
        when(partition2Conn.modifyDNAsync(any(ModifyDNRequest.class),
                                          any(IntermediateResponseHandler.class))).thenReturn(SUCCESS);

        when(partition1Conn.searchAsync(any(SearchRequest.class),
                                        any(IntermediateResponseHandler.class),
                                        any(SearchResultHandler.class))) .thenReturn(SUCCESS);
        when(partition2Conn.searchAsync(any(SearchRequest.class),
                                        any(IntermediateResponseHandler.class),
                                        any(SearchResultHandler.class))).thenReturn(SUCCESS);
    }

    @Test
    public void closeShouldCloseAllPartitions() throws Exception {
        loadBalancer.close();
        verify(partition1).close();
        verify(partition2).close();
    }

    @Test
    public void getConnectionShouldReturnLogicalConnection() throws Exception {
        try (final Connection connection = loadBalancer.getConnection()) {
            assertThat(connection).isNotNull();
            connection.close();
        }
        verifyZeroInteractions(partition1, partition2);
    }

    @Test
    public void getConnectionAsyncShouldReturnLogicalConnection() throws Exception {
        try (final Connection connection = loadBalancer.getConnectionAsync().get()) {
            assertThat(connection).isNotNull();
            connection.close();
        }
        verifyZeroInteractions(partition1, partition2);
    }

    @DataProvider
    public Object[][] requests() {
        return new Object[][] { { P1_HASH, false, UNPARTITIONED_DN, UNPARTITIONED_DN },
                                { P2_HASH, true, UNPARTITIONED_DN, UNPARTITIONED_DN },
                                { P1_HASH, false, DN_ABOVE_PARTITION_BASE_DN, DN_ABOVE_PARTITION_BASE_DN },
                                { P2_HASH, true, DN_ABOVE_PARTITION_BASE_DN, DN_ABOVE_PARTITION_BASE_DN },
                                { P1_HASH, false, DN_1_BELOW_PARTITION_BASE_DN, DN_1_BELOW_PARTITION_BASE_DN },
                                { P2_HASH, true, DN_1_BELOW_PARTITION_BASE_DN, DN_1_BELOW_PARTITION_BASE_DN },
                                { P1_HASH, false, DN_2_BELOW_PARTITION_BASE_DN, DN_1_BELOW_PARTITION_BASE_DN },
                                { P2_HASH, true, DN_2_BELOW_PARTITION_BASE_DN, DN_1_BELOW_PARTITION_BASE_DN } };
    }

    @Test(dataProvider = "requests")
    public void addShouldRouteCorrectly(final int hash, final boolean isSecondPartition, final DN requestDN,
                                        final DN partitionDN) throws Exception {
        when(hashFunction.apply(any())).thenReturn(hash);
        try (final Connection connection = loadBalancer.getConnection()) {
            connection.addAsync(newAddRequest(requestDN));
        }
        verify(hitPartition(isSecondPartition)).addAsync(any(AddRequest.class), any(IntermediateResponseHandler.class));
        verify(hashFunction).apply(partitionDN.toNormalizedUrlSafeString());
        verify(hitPartition(isSecondPartition)).close();
        verifyZeroInteractions(missPartition(isSecondPartition));
    }

    @Test(dataProvider = "requests")
    public void bindShouldRouteCorrectly(final int hash, final boolean isSecondPartition, final DN requestDN,
                                         final DN partitionDN) throws Exception {
        when(hashFunction.apply(any())).thenReturn(hash);
        try (final Connection connection = loadBalancer.getConnection()) {
            connection.bindAsync(Requests.newSimpleBindRequest(requestDN.toString(), "password".toCharArray()));
        }
        verify(hitPartition(isSecondPartition)).bindAsync(any(BindRequest.class),
                                                          any(IntermediateResponseHandler.class));
        verify(hashFunction).apply(partitionDN.toNormalizedUrlSafeString());
        verify(hitPartition(isSecondPartition)).close();
        verifyZeroInteractions(missPartition(isSecondPartition));
    }

    @Test(dataProvider = "requests")
    public void compareShouldRouteCorrectly(final int hash, final boolean isSecondPartition, final DN requestDN,
                                            final DN partitionDN) throws Exception {
        when(hashFunction.apply(any())).thenReturn(hash);
        try (final Connection connection = loadBalancer.getConnection()) {
            connection.compareAsync(newCompareRequest(requestDN.toString(), "cn", "test"));
        }
        verify(hitPartition(isSecondPartition)).compareAsync(any(CompareRequest.class),
                                                             any(IntermediateResponseHandler.class));
        verify(hashFunction).apply(partitionDN.toNormalizedUrlSafeString());
        verify(hitPartition(isSecondPartition)).close();
        verifyZeroInteractions(missPartition(isSecondPartition));
    }

    @Test(dataProvider = "requests")
    public void deleteShouldRouteCorrectly(final int hash, final boolean isSecondPartition, final DN requestDN,
                                           final DN partitionDN) throws Exception {
        when(hashFunction.apply(any())).thenReturn(hash);
        try (final Connection connection = loadBalancer.getConnection()) {
            connection.deleteAsync(newDeleteRequest(requestDN));
        }
        verify(hitPartition(isSecondPartition)).deleteAsync(any(DeleteRequest.class),
                                                            any(IntermediateResponseHandler.class));
        verify(hashFunction).apply(partitionDN.toNormalizedUrlSafeString());
        verify(hitPartition(isSecondPartition)).close();
        verifyZeroInteractions(missPartition(isSecondPartition));
    }

    @Test(dataProvider = "requests")
    public void extendedShouldRouteCorrectly(final int hash, final boolean isSecondPartition, final DN requestDN,
                                             final DN partitionDN) throws Exception {
        when(hashFunction.apply(any())).thenReturn(hash);
        try (final Connection connection = loadBalancer.getConnection()) {
            connection.extendedRequestAsync(newPasswordModifyExtendedRequest().setUserIdentity("dn:" + requestDN));
        }
        verify(hitPartition(isSecondPartition)).extendedRequestAsync(any(ExtendedRequest.class),
                                                                     any(IntermediateResponseHandler.class));
        verify(hashFunction).apply(partitionDN.toNormalizedUrlSafeString());
        verify(hitPartition(isSecondPartition)).close();
        verifyZeroInteractions(missPartition(isSecondPartition));
    }

    @Test(dataProvider = "requests")
    public void modifyShouldRouteCorrectly(final int hash, final boolean isSecondPartition, final DN requestDN,
                                           final DN partitionDN) throws Exception {
        when(hashFunction.apply(any())).thenReturn(hash);
        try (final Connection connection = loadBalancer.getConnection()) {
            connection.modifyAsync(newModifyRequest(requestDN));
        }
        verify(hitPartition(isSecondPartition)).modifyAsync(any(ModifyRequest.class),
                                                            any(IntermediateResponseHandler.class));
        verify(hashFunction).apply(partitionDN.toNormalizedUrlSafeString());
        verify(hitPartition(isSecondPartition)).close();
        verifyZeroInteractions(missPartition(isSecondPartition));
    }

    @Test(dataProvider = "requests")
    public void modifyDNShouldRouteCorrectly(final int hash, final boolean isSecondPartition, final DN requestDN,
                                             final DN partitionDN) throws Exception {
        when(hashFunction.apply(any())).thenReturn(hash);
        try (final Connection connection = loadBalancer.getConnection()) {
            connection.modifyDNAsync(newModifyDNRequest(requestDN, RDN.valueOf("cn=changed")));
        }
        verify(hitPartition(isSecondPartition)).modifyDNAsync(any(ModifyDNRequest.class),
                                                              any(IntermediateResponseHandler.class));
        verify(hashFunction, atLeastOnce()).apply(partitionDN.toNormalizedUrlSafeString());
        verify(hitPartition(isSecondPartition)).close();
        verifyZeroInteractions(missPartition(isSecondPartition));
    }

    @Test(dataProvider = "requests")
    public void searchBaseShouldRouteCorrectly(final int hash, final boolean isSecondPartition, final DN requestDN,
                                               final DN partitionDN) throws Exception {
        when(hashFunction.apply(any())).thenReturn(hash);
        try (final Connection connection = loadBalancer.getConnection()) {
            connection.searchAsync(newSearchRequest(requestDN, BASE_OBJECT, alwaysTrue()),
                                   mock(SearchResultHandler.class));
        }
        verify(hitPartition(isSecondPartition)).searchAsync(any(SearchRequest.class),
                                                            any(IntermediateResponseHandler.class),
                                                            any(SearchResultHandler.class));
        verify(hashFunction).apply(partitionDN.toNormalizedUrlSafeString());
        verify(hitPartition(isSecondPartition)).close();
        verifyZeroInteractions(missPartition(isSecondPartition));
    }

    @Test
    public void searchSingleLevelBelowPartitionBaseDNShouldRouteToSinglePartition() throws Exception {
        verifySearchAgainstSinglePartition(DN_1_BELOW_PARTITION_BASE_DN, SINGLE_LEVEL);
    }

    @Test
    public void searchSingleLevelAbovePartitionBaseDNShouldRouteToSinglePartition() throws Exception {
        verifySearchAgainstSinglePartition(DN_ABOVE_PARTITION_BASE_DN, SINGLE_LEVEL);
    }

    @Test
    public void searchSingleLevelAdjacentToPartitionBaseDNShouldRouteToSinglePartition() throws Exception {
        verifySearchAgainstSinglePartition(UNPARTITIONED_DN, SINGLE_LEVEL);
    }

    @Test
    public void searchSingleLevelAtPartitionBaseDNShouldRouteToAllPartitions() throws Exception {
        verifySearchAgainstAllPartitions(PARTITION_BASE_DN, SINGLE_LEVEL);
    }

    @Test
    public void searchSubtreeBelowPartitionBaseDNShouldRouteToSinglePartition() throws Exception {
        verifySearchAgainstSinglePartition(DN_1_BELOW_PARTITION_BASE_DN, WHOLE_SUBTREE);
    }

    @Test
    public void searchSubtreeAdjacentToPartitionBaseDNShouldRouteToSinglePartition() throws Exception {
        verifySearchAgainstSinglePartition(UNPARTITIONED_DN, WHOLE_SUBTREE);
    }

    @Test
    public void searchSubtreeAbovePartitionBaseDNShouldRouteToAllPartitions() throws Exception {
        verifySearchAgainstAllPartitions(DN_ABOVE_PARTITION_BASE_DN, WHOLE_SUBTREE);
    }

    @Test
    public void searchSubtreeAtPartitionBaseDNShouldRouteToAllPartitions() throws Exception {
        verifySearchAgainstAllPartitions(PARTITION_BASE_DN, WHOLE_SUBTREE);
    }

    @Test
    public void searchSubordinatesBelowPartitionBaseDNShouldRouteToSinglePartition() throws Exception {
        verifySearchAgainstSinglePartition(DN_1_BELOW_PARTITION_BASE_DN, SUBORDINATES);
    }

    @Test
    public void searchSubordinatesAdjacentToPartitionBaseDNShouldRouteToSinglePartition() throws Exception {
        verifySearchAgainstSinglePartition(UNPARTITIONED_DN, SUBORDINATES);
    }

    @Test
    public void searchSubordinatesAbovePartitionBaseDNShouldRouteToAllPartitions() throws Exception {
        verifySearchAgainstAllPartitions(DN_ABOVE_PARTITION_BASE_DN, SUBORDINATES);
    }

    @Test
    public void searchSubordinatesAtPartitionBaseDNShouldRouteToAllPartitions() throws Exception {
        verifySearchAgainstAllPartitions(PARTITION_BASE_DN, SUBORDINATES);
    }

    private void verifySearchAgainstSinglePartition(final DN dn, final SearchScope scope) throws Exception {
        when(hashFunction.apply(any())).thenReturn(P1_HASH);
        try (final Connection connection = loadBalancer.getConnection()) {
            connection.searchAsync(newSearchRequest(dn, scope, alwaysTrue()),
                                   mock(SearchResultHandler.class));
        }
        verify(partition1Conn).searchAsync(any(SearchRequest.class),
                                           any(IntermediateResponseHandler.class),
                                           any(SearchResultHandler.class));
        verify(hashFunction).apply(dn.toNormalizedUrlSafeString());
        verify(partition1Conn).close();
        verifyZeroInteractions(partition2Conn);
    }

    private void verifySearchAgainstAllPartitions(final DN dn, final SearchScope scope) throws Exception {
        when(hashFunction.apply(any())).thenReturn(P1_HASH);
        try (final Connection connection = loadBalancer.getConnection()) {
            connection.searchAsync(newSearchRequest(dn, scope, alwaysTrue()), mock(SearchResultHandler.class));
        }
        verify(partition1Conn).searchAsync(any(SearchRequest.class),
                                           any(IntermediateResponseHandler.class),
                                           any(SearchResultHandler.class));
        verify(partition2Conn).searchAsync(any(SearchRequest.class),
                                           any(IntermediateResponseHandler.class),
                                           any(SearchResultHandler.class));
        verify(partition1Conn).close();
        verify(partition2Conn).close();
    }

    private Connection missPartition(final boolean isSecondPartition) {
        return isSecondPartition ? partition1Conn : partition2Conn;
    }

    private Connection hitPartition(final boolean isSecondPartition) {
        return isSecondPartition ? partition2Conn : partition1Conn;
    }
}
