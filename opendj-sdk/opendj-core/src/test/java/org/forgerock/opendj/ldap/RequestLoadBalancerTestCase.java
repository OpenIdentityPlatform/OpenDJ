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
 *      Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.forgerock.opendj.ldap.LdapException.newLdapException;
import static org.forgerock.opendj.ldap.ResultCode.CLIENT_SIDE_CONNECT_ERROR;
import static org.forgerock.opendj.ldap.responses.Responses.newBindResult;
import static org.forgerock.opendj.ldap.responses.Responses.newCompareResult;
import static org.forgerock.opendj.ldap.responses.Responses.newGenericExtendedResult;
import static org.forgerock.opendj.ldap.responses.Responses.newResult;
import static org.forgerock.opendj.ldap.spi.LdapPromises.newSuccessfulLdapPromise;
import static org.forgerock.util.Options.defaultOptions;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNotNull;
import static org.mockito.Matchers.isNull;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.ArrayList;
import java.util.logging.Level;

import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.GenericExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Request;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promises;
import org.mockito.Mock;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class RequestLoadBalancerTestCase extends SdkTestCase {
    @Test
    public void closeLoadBalancerShouldCloseDelegateFactories() throws Exception {
        configureAllFactoriesOnline();
        loadBalancer.close();
        verify(factory1).close();
        verify(factory2).close();
        verify(factory3).close();
    }

    @Test
    public void getConnectionShouldNotInvokeDelegateFactory() throws Exception {
        configureAllFactoriesOnline();
        try (Connection connection = loadBalancer.getConnection()) {
            assertThat(connection).isNotNull();
            verifyZeroInteractions(factory1, factory2, factory3);
        }
        verifyZeroInteractions(factory1, factory2, factory3);
        loadBalancer.close();
    }

    @Test
    public void getConnectionReturnANewConnectionEachTime() throws Exception {
        configureAllFactoriesOnline();
        try (Connection connection1 = loadBalancer.getConnection();
             Connection connection2 = loadBalancer.getConnection()) {
            assertThat(connection1).isNotSameAs(connection2);
        }
        loadBalancer.close();
    }

    @Test
    public void getConnectionAsyncReturnANewConnectionEachTime() throws Exception {
        configureAllFactoriesOnline();
        try (Connection connection1 = loadBalancer.getConnectionAsync().get();
             Connection connection2 = loadBalancer.getConnectionAsync().get()) {
            assertThat(connection1).isNotSameAs(connection2);
        }
        loadBalancer.close();
    }

    @Test
    public void getConnectionAsyncShouldNotInvokeDelegateFactory() throws Exception {
        configureAllFactoriesOnline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            assertThat(connection).isNotNull();
            verifyZeroInteractions(factory1, factory2, factory3);
        }
        verifyZeroInteractions(factory1, factory2, factory3);
    }

    @Test
    public void connectionEventListenersNotifiedOnClose() throws Exception {
        configureAllFactoriesOnline();
        final ConnectionEventListener listener = mock(ConnectionEventListener.class);
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            connection.addConnectionEventListener(listener);
        }
        verify(listener).handleConnectionClosed();
    }

    @Test
    public void connectionEventListenersNotifiedOnError() throws Exception {
        configureAllFactoriesOffline();
        final ConnectionEventListener listener = mock(ConnectionEventListener.class);
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            connection.addConnectionEventListener(listener);
            try {
                connection.add(addRequest1);
                fail("add unexpectedly succeeded");
            } catch (LdapException ignored) {
                // Ignore.
            }
        }
        verify(listener).handleConnectionError(eq(false), any(LdapException.class));
        verify(listener).handleConnectionClosed();
    }

    @Test
    public void removedConnectionEventListenersShouldNotBeNotified() throws Exception {
        configureAllFactoriesOnline();
        final ConnectionEventListener listener = mock(ConnectionEventListener.class);
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            connection.addConnectionEventListener(listener);
            connection.removeConnectionEventListener(listener);
        }
        verifyZeroInteractions(listener);
    }

    @Test
    public void validAndClosedStateShouldBeMaintained() throws Exception {
        configureAllFactoriesOffline();
        final Connection connection = loadBalancer.getConnectionAsync().get();
        assertThat(connection.isValid()).isTrue();
        assertThat(connection.isClosed()).isFalse();

        try {
            connection.add(addRequest1);
            fail("add unexpectedly succeeded");
        } catch (LdapException ignored) {
            // Ignore.
        }
        assertThat(connection.isValid()).isFalse();
        assertThat(connection.isClosed()).isFalse();

        connection.close();
        assertThat(connection.isValid()).isFalse();
        assertThat(connection.isClosed()).isTrue();
    }

    @Test
    public void factoryToStringShouldReturnANonEmptyString() throws Exception {
        configureAllFactoriesOffline();
        assertThat(loadBalancer.toString()).isNotEmpty();
    }

    @Test
    public void connectionToStringShouldReturnANonEmptyString() throws Exception {
        configureAllFactoriesOffline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            assertThat(connection.toString()).isNotEmpty();
        }
    }

    @Test
    public void abandonRequestShouldBeIgnored() throws Exception {
        configureAllFactoriesOnline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            connection.abandonAsync(mock(AbandonRequest.class));
        }
        verifyZeroInteractions(factory1, factory2, factory3);
    }

    // We can't use a DataProviders here because the mocks will be re-initialized for each test method call.

    // ################## Add Requests ####################

    @Test
    public void addRequestsShouldBeRoutedCorrectly1() throws Exception {
        addRequestsShouldBeRoutedCorrectlyImpl(addRequest1, factory1, connection1);
    }

    @Test
    public void addRequestsShouldBeRoutedCorrectly2() throws Exception {
        addRequestsShouldBeRoutedCorrectlyImpl(addRequest2, factory2, connection2);
    }

    @Test
    public void addRequestsShouldBeRoutedCorrectly3() throws Exception {
        addRequestsShouldBeRoutedCorrectlyImpl(addRequest3, factory3, connection3);
    }

    private void addRequestsShouldBeRoutedCorrectlyImpl(final AddRequest addRequest,
                                                        final ConnectionFactory expectedFactory,
                                                        final Connection expectedConnection) throws Exception {
        configureAllFactoriesOnline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            connection.add(addRequest);
        }
        verify(expectedFactory).getConnectionAsync();
        verify(expectedConnection).addAsync(same(addRequest), isNull(IntermediateResponseHandler.class));
        verify(expectedConnection).close();
        verifyZeroInteractionsForRemainingFactories(expectedFactory);
    }

    @Test
    public void addRequestsShouldLinearProbeOnFailure1() throws Exception {
        addRequestsShouldLinearProbeOnFailureImpl(addRequest1);
    }

    @Test
    public void addRequestsShouldLinearProbeOnFailure2() throws Exception {
        addRequestsShouldLinearProbeOnFailureImpl(addRequest2);
    }

    @Test
    public void addRequestsShouldLinearProbeOnFailure3() throws Exception {
        addRequestsShouldLinearProbeOnFailureImpl(addRequest3);
    }

    private void addRequestsShouldLinearProbeOnFailureImpl(final AddRequest addRequest) throws Exception {
        configureFactoriesOneAndTwoOffline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            connection.add(addRequest);
        }
        verify(factory3).getConnectionAsync();
        verify(connection3).addAsync(same(addRequest), isNull(IntermediateResponseHandler.class));
        verify(connection3).close();
        verifyZeroInteractions(connection1);
        verifyZeroInteractions(connection2);
    }

    @Test
    public void addRequestsShouldFailWhenAllFactoriesOffline() throws Exception {
        configureAllFactoriesOffline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            try {
                connection.add(addRequest1);
            } catch (ConnectionException e) {
                assertThat(e.getResult().getResultCode()).isEqualTo(CLIENT_SIDE_CONNECT_ERROR);
            }
        }
        verify(factory1, atLeastOnce()).getConnectionAsync();
        verify(factory2, atLeastOnce()).getConnectionAsync();
        verify(factory3, atLeastOnce()).getConnectionAsync();
        verifyZeroInteractions(connection1);
        verifyZeroInteractions(connection2);
        verifyZeroInteractions(connection3);
    }

    // ################## Bind Requests ####################

    @Test
    public void bindRequestsShouldBeRoutedCorrectly1() throws Exception {
        bindRequestsShouldBeRoutedCorrectlyImpl(bindRequest1, factory1, connection1);
    }

    @Test
    public void bindRequestsShouldBeRoutedCorrectly2() throws Exception {
        bindRequestsShouldBeRoutedCorrectlyImpl(bindRequest2, factory2, connection2);
    }

    @Test
    public void bindRequestsShouldBeRoutedCorrectly3() throws Exception {
        bindRequestsShouldBeRoutedCorrectlyImpl(bindRequest3, factory3, connection3);
    }

    private void bindRequestsShouldBeRoutedCorrectlyImpl(final BindRequest bindRequest,
                                                         final ConnectionFactory expectedFactory,
                                                         final Connection expectedConnection) throws Exception {
        configureAllFactoriesOnline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            connection.bind(bindRequest);
        }
        verify(expectedFactory).getConnectionAsync();
        verify(expectedConnection).bindAsync(same(bindRequest), isNull(IntermediateResponseHandler.class));
        verify(expectedConnection).close();
        verifyZeroInteractionsForRemainingFactories(expectedFactory);
    }

    @Test
    public void bindRequestsShouldLinearProbeOnFailure1() throws Exception {
        bindRequestsShouldLinearProbeOnFailureImpl(bindRequest1);
    }

    @Test
    public void bindRequestsShouldLinearProbeOnFailure2() throws Exception {
        bindRequestsShouldLinearProbeOnFailureImpl(bindRequest2);
    }

    @Test
    public void bindRequestsShouldLinearProbeOnFailure3() throws Exception {
        bindRequestsShouldLinearProbeOnFailureImpl(bindRequest3);
    }

    private void bindRequestsShouldLinearProbeOnFailureImpl(final BindRequest bindRequest) throws Exception {
        configureFactoriesOneAndTwoOffline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            connection.bind(bindRequest);
        }
        verify(factory3).getConnectionAsync();
        verify(connection3).bindAsync(same(bindRequest), isNull(IntermediateResponseHandler.class));
        verify(connection3).close();
        verifyZeroInteractions(connection1);
        verifyZeroInteractions(connection2);
    }

    @Test
    public void bindRequestsShouldFailWhenAllFactoriesOffline() throws Exception {
        configureAllFactoriesOffline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            try {
                connection.bind(bindRequest1);
            } catch (ConnectionException e) {
                assertThat(e.getResult().getResultCode()).isEqualTo(CLIENT_SIDE_CONNECT_ERROR);
            }
        }
        verify(factory1, atLeastOnce()).getConnectionAsync();
        verify(factory2, atLeastOnce()).getConnectionAsync();
        verify(factory3, atLeastOnce()).getConnectionAsync();
        verifyZeroInteractions(connection1);
        verifyZeroInteractions(connection2);
        verifyZeroInteractions(connection3);
    }

    // ################## Compare Requests ####################

    @Test
    public void compareRequestsShouldBeRoutedCorrectly1() throws Exception {
        compareRequestsShouldBeRoutedCorrectlyImpl(compareRequest1, factory1, connection1);
    }

    @Test
    public void compareRequestsShouldBeRoutedCorrectly2() throws Exception {
        compareRequestsShouldBeRoutedCorrectlyImpl(compareRequest2, factory2, connection2);
    }

    @Test
    public void compareRequestsShouldBeRoutedCorrectly3() throws Exception {
        compareRequestsShouldBeRoutedCorrectlyImpl(compareRequest3, factory3, connection3);
    }

    private void compareRequestsShouldBeRoutedCorrectlyImpl(final CompareRequest compareRequest,
                                                            final ConnectionFactory expectedFactory,
                                                            final Connection expectedConnection) throws Exception {
        configureAllFactoriesOnline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            connection.compare(compareRequest);
        }
        verify(expectedFactory).getConnectionAsync();
        verify(expectedConnection).compareAsync(same(compareRequest), isNull(IntermediateResponseHandler.class));
        verify(expectedConnection).close();
        verifyZeroInteractionsForRemainingFactories(expectedFactory);
    }

    @Test
    public void compareRequestsShouldLinearProbeOnFailure1() throws Exception {
        compareRequestsShouldLinearProbeOnFailureImpl(compareRequest1);
    }

    @Test
    public void compareRequestsShouldLinearProbeOnFailure2() throws Exception {
        compareRequestsShouldLinearProbeOnFailureImpl(compareRequest2);
    }

    @Test
    public void compareRequestsShouldLinearProbeOnFailure3() throws Exception {
        compareRequestsShouldLinearProbeOnFailureImpl(compareRequest3);
    }

    private void compareRequestsShouldLinearProbeOnFailureImpl(final CompareRequest compareRequest) throws Exception {
        configureFactoriesOneAndTwoOffline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            connection.compare(compareRequest);
        }
        verify(factory3).getConnectionAsync();
        verify(connection3).compareAsync(same(compareRequest), isNull(IntermediateResponseHandler.class));
        verify(connection3).close();
        verifyZeroInteractions(connection1);
        verifyZeroInteractions(connection2);
    }

    @Test
    public void compareRequestsShouldFailWhenAllFactoriesOffline() throws Exception {
        configureAllFactoriesOffline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            try {
                connection.compare(compareRequest1);
            } catch (ConnectionException e) {
                assertThat(e.getResult().getResultCode()).isEqualTo(CLIENT_SIDE_CONNECT_ERROR);
            }
        }
        verify(factory1, atLeastOnce()).getConnectionAsync();
        verify(factory2, atLeastOnce()).getConnectionAsync();
        verify(factory3, atLeastOnce()).getConnectionAsync();
        verifyZeroInteractions(connection1);
        verifyZeroInteractions(connection2);
        verifyZeroInteractions(connection3);
    }

    // ################## Delete Requests ####################

    @Test
    public void deleteRequestsShouldBeRoutedCorrectly1() throws Exception {
        deleteRequestsShouldBeRoutedCorrectlyImpl(deleteRequest1, factory1, connection1);
    }

    @Test
    public void deleteRequestsShouldBeRoutedCorrectly2() throws Exception {
        deleteRequestsShouldBeRoutedCorrectlyImpl(deleteRequest2, factory2, connection2);
    }

    @Test
    public void deleteRequestsShouldBeRoutedCorrectly3() throws Exception {
        deleteRequestsShouldBeRoutedCorrectlyImpl(deleteRequest3, factory3, connection3);
    }

    private void deleteRequestsShouldBeRoutedCorrectlyImpl(final DeleteRequest deleteRequest,
                                                           final ConnectionFactory expectedFactory,
                                                           final Connection expectedConnection) throws Exception {
        configureAllFactoriesOnline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            connection.delete(deleteRequest);
        }
        verify(expectedFactory).getConnectionAsync();
        verify(expectedConnection).deleteAsync(same(deleteRequest), isNull(IntermediateResponseHandler.class));
        verify(expectedConnection).close();
        verifyZeroInteractionsForRemainingFactories(expectedFactory);
    }

    @Test
    public void deleteRequestsShouldLinearProbeOnFailure1() throws Exception {
        deleteRequestsShouldLinearProbeOnFailureImpl(deleteRequest1);
    }

    @Test
    public void deleteRequestsShouldLinearProbeOnFailure2() throws Exception {
        deleteRequestsShouldLinearProbeOnFailureImpl(deleteRequest2);
    }

    @Test
    public void deleteRequestsShouldLinearProbeOnFailure3() throws Exception {
        deleteRequestsShouldLinearProbeOnFailureImpl(deleteRequest3);
    }

    private void deleteRequestsShouldLinearProbeOnFailureImpl(final DeleteRequest deleteRequest) throws Exception {
        configureFactoriesOneAndTwoOffline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            connection.delete(deleteRequest);
        }
        verify(factory3).getConnectionAsync();
        verify(connection3).deleteAsync(same(deleteRequest), isNull(IntermediateResponseHandler.class));
        verify(connection3).close();
        verifyZeroInteractions(connection1);
        verifyZeroInteractions(connection2);
    }

    @Test
    public void deleteRequestsShouldFailWhenAllFactoriesOffline() throws Exception {
        configureAllFactoriesOffline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            try {
                connection.delete(deleteRequest1);
            } catch (ConnectionException e) {
                assertThat(e.getResult().getResultCode()).isEqualTo(CLIENT_SIDE_CONNECT_ERROR);
            }
        }
        verify(factory1, atLeastOnce()).getConnectionAsync();
        verify(factory2, atLeastOnce()).getConnectionAsync();
        verify(factory3, atLeastOnce()).getConnectionAsync();
        verifyZeroInteractions(connection1);
        verifyZeroInteractions(connection2);
        verifyZeroInteractions(connection3);
    }

    // ################## Extended Requests ####################

    @Test
    public void extendedRequestsShouldBeRoutedCorrectly1() throws Exception {
        extendedRequestsShouldBeRoutedCorrectlyImpl(extendedRequest1, factory1, connection1);
    }

    @Test
    public void extendedRequestsShouldBeRoutedCorrectly2() throws Exception {
        extendedRequestsShouldBeRoutedCorrectlyImpl(extendedRequest2, factory2, connection2);
    }

    @Test
    public void extendedRequestsShouldBeRoutedCorrectly3() throws Exception {
        extendedRequestsShouldBeRoutedCorrectlyImpl(extendedRequest3, factory3, connection3);
    }

    private void extendedRequestsShouldBeRoutedCorrectlyImpl(final ExtendedRequest<?> extendedRequest,
                                                             final ConnectionFactory expectedFactory,
                                                             final Connection expectedConnection) throws Exception {
        configureAllFactoriesOnline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            connection.extendedRequest(extendedRequest);
        }
        verify(expectedFactory).getConnectionAsync();
        verify(expectedConnection).extendedRequestAsync(same(extendedRequest),
                                                        isNull(IntermediateResponseHandler.class));
        verify(expectedConnection).close();
        verifyZeroInteractionsForRemainingFactories(expectedFactory);
    }

    @Test
    public void extendedRequestsShouldLinearProbeOnFailure1() throws Exception {
        extendedRequestsShouldLinearProbeOnFailureImpl(extendedRequest1);
    }

    @Test
    public void extendedRequestsShouldLinearProbeOnFailure2() throws Exception {
        extendedRequestsShouldLinearProbeOnFailureImpl(extendedRequest2);
    }

    @Test
    public void extendedRequestsShouldLinearProbeOnFailure3() throws Exception {
        extendedRequestsShouldLinearProbeOnFailureImpl(extendedRequest3);
    }

    private void extendedRequestsShouldLinearProbeOnFailureImpl(
            final ExtendedRequest<?> extendedRequest) throws Exception {
        configureFactoriesOneAndTwoOffline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            connection.extendedRequest(extendedRequest);
        }
        verify(factory3).getConnectionAsync();
        verify(connection3).extendedRequestAsync(same(extendedRequest), isNull(IntermediateResponseHandler.class));
        verify(connection3).close();
        verifyZeroInteractions(connection1);
        verifyZeroInteractions(connection2);
    }

    @Test
    public void extendedRequestsShouldFailWhenAllFactoriesOffline() throws Exception {
        configureAllFactoriesOffline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            try {
                connection.extendedRequest(extendedRequest1);
            } catch (ConnectionException e) {
                assertThat(e.getResult().getResultCode()).isEqualTo(CLIENT_SIDE_CONNECT_ERROR);
            }
        }
        verify(factory1, atLeastOnce()).getConnectionAsync();
        verify(factory2, atLeastOnce()).getConnectionAsync();
        verify(factory3, atLeastOnce()).getConnectionAsync();
        verifyZeroInteractions(connection1);
        verifyZeroInteractions(connection2);
        verifyZeroInteractions(connection3);
    }

    // ################## Modify Requests ####################

    @Test
    public void modifyRequestsShouldBeRoutedCorrectly1() throws Exception {
        modifyRequestsShouldBeRoutedCorrectlyImpl(modifyRequest1, factory1, connection1);
    }

    @Test
    public void modifyRequestsShouldBeRoutedCorrectly2() throws Exception {
        modifyRequestsShouldBeRoutedCorrectlyImpl(modifyRequest2, factory2, connection2);
    }

    @Test
    public void modifyRequestsShouldBeRoutedCorrectly3() throws Exception {
        modifyRequestsShouldBeRoutedCorrectlyImpl(modifyRequest3, factory3, connection3);
    }

    private void modifyRequestsShouldBeRoutedCorrectlyImpl(final ModifyRequest modifyRequest,
                                                           final ConnectionFactory expectedFactory,
                                                           final Connection expectedConnection) throws Exception {
        configureAllFactoriesOnline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            connection.modify(modifyRequest);
        }
        verify(expectedFactory).getConnectionAsync();
        verify(expectedConnection).modifyAsync(same(modifyRequest), isNull(IntermediateResponseHandler.class));
        verify(expectedConnection).close();
        verifyZeroInteractionsForRemainingFactories(expectedFactory);
    }

    @Test
    public void modifyRequestsShouldLinearProbeOnFailure1() throws Exception {
        modifyRequestsShouldLinearProbeOnFailureImpl(modifyRequest1);
    }

    @Test
    public void modifyRequestsShouldLinearProbeOnFailure2() throws Exception {
        modifyRequestsShouldLinearProbeOnFailureImpl(modifyRequest2);
    }

    @Test
    public void modifyRequestsShouldLinearProbeOnFailure3() throws Exception {
        modifyRequestsShouldLinearProbeOnFailureImpl(modifyRequest3);
    }

    private void modifyRequestsShouldLinearProbeOnFailureImpl(final ModifyRequest modifyRequest) throws Exception {
        configureFactoriesOneAndTwoOffline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            connection.modify(modifyRequest);
        }
        verify(factory3).getConnectionAsync();
        verify(connection3).modifyAsync(same(modifyRequest), isNull(IntermediateResponseHandler.class));
        verify(connection3).close();
        verifyZeroInteractions(connection1);
        verifyZeroInteractions(connection2);
    }

    @Test
    public void modifyRequestsShouldFailWhenAllFactoriesOffline() throws Exception {
        configureAllFactoriesOffline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            try {
                connection.modify(modifyRequest1);
            } catch (ConnectionException e) {
                assertThat(e.getResult().getResultCode()).isEqualTo(CLIENT_SIDE_CONNECT_ERROR);
            }
        }
        verify(factory1, atLeastOnce()).getConnectionAsync();
        verify(factory2, atLeastOnce()).getConnectionAsync();
        verify(factory3, atLeastOnce()).getConnectionAsync();
        verifyZeroInteractions(connection1);
        verifyZeroInteractions(connection2);
        verifyZeroInteractions(connection3);
    }

    // ################## ModifyDN Requests ####################

    @Test
    public void modifyDNRequestsShouldBeRoutedCorrectly1() throws Exception {
        modifyDNRequestsShouldBeRoutedCorrectlyImpl(modifyDNRequest1, factory1, connection1);
    }

    @Test
    public void modifyDNRequestsShouldBeRoutedCorrectly2() throws Exception {
        modifyDNRequestsShouldBeRoutedCorrectlyImpl(modifyDNRequest2, factory2, connection2);
    }

    @Test
    public void modifyDNRequestsShouldBeRoutedCorrectly3() throws Exception {
        modifyDNRequestsShouldBeRoutedCorrectlyImpl(modifyDNRequest3, factory3, connection3);
    }

    private void modifyDNRequestsShouldBeRoutedCorrectlyImpl(final ModifyDNRequest modifyDNRequest,
                                                             final ConnectionFactory expectedFactory,
                                                             final Connection expectedConnection) throws Exception {
        configureAllFactoriesOnline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            connection.modifyDN(modifyDNRequest);
        }
        verify(expectedFactory).getConnectionAsync();
        verify(expectedConnection).modifyDNAsync(same(modifyDNRequest), isNull(IntermediateResponseHandler.class));
        verify(expectedConnection).close();
        verifyZeroInteractionsForRemainingFactories(expectedFactory);
    }

    @Test
    public void modifyDNRequestsShouldLinearProbeOnFailure1() throws Exception {
        modifyDNRequestsShouldLinearProbeOnFailureImpl(modifyDNRequest1);
    }

    @Test
    public void modifyDNRequestsShouldLinearProbeOnFailure2() throws Exception {
        modifyDNRequestsShouldLinearProbeOnFailureImpl(modifyDNRequest2);
    }

    @Test
    public void modifyDNRequestsShouldLinearProbeOnFailure3() throws Exception {
        modifyDNRequestsShouldLinearProbeOnFailureImpl(modifyDNRequest3);
    }

    private void modifyDNRequestsShouldLinearProbeOnFailureImpl(
            final ModifyDNRequest modifyDNRequest) throws Exception {
        configureFactoriesOneAndTwoOffline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            connection.modifyDN(modifyDNRequest);
        }
        verify(factory3).getConnectionAsync();
        verify(connection3).modifyDNAsync(same(modifyDNRequest), isNull(IntermediateResponseHandler.class));
        verify(connection3).close();
        verifyZeroInteractions(connection1);
        verifyZeroInteractions(connection2);
    }

    @Test
    public void modifyDNRequestsShouldFailWhenAllFactoriesOffline() throws Exception {
        configureAllFactoriesOffline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            try {
                connection.modifyDN(modifyDNRequest1);
            } catch (ConnectionException e) {
                assertThat(e.getResult().getResultCode()).isEqualTo(CLIENT_SIDE_CONNECT_ERROR);
            }
        }
        verify(factory1, atLeastOnce()).getConnectionAsync();
        verify(factory2, atLeastOnce()).getConnectionAsync();
        verify(factory3, atLeastOnce()).getConnectionAsync();
        verifyZeroInteractions(connection1);
        verifyZeroInteractions(connection2);
        verifyZeroInteractions(connection3);
    }

    // ################## Search Requests ####################

    @Test
    public void searchRequestsShouldBeRoutedCorrectly1() throws Exception {
        searchRequestsShouldBeRoutedCorrectlyImpl(searchRequest1, factory1, connection1);
    }

    @Test
    public void searchRequestsShouldBeRoutedCorrectly2() throws Exception {
        searchRequestsShouldBeRoutedCorrectlyImpl(searchRequest2, factory2, connection2);
    }

    @Test
    public void searchRequestsShouldBeRoutedCorrectly3() throws Exception {
        searchRequestsShouldBeRoutedCorrectlyImpl(searchRequest3, factory3, connection3);
    }

    private void searchRequestsShouldBeRoutedCorrectlyImpl(final SearchRequest searchRequest,
                                                           final ConnectionFactory expectedFactory,
                                                           final Connection expectedConnection) throws Exception {
        configureAllFactoriesOnline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            connection.search(searchRequest, new ArrayList<>());
        }
        verify(expectedFactory).getConnectionAsync();
        verify(expectedConnection).searchAsync(same(searchRequest),
                                               isNull(IntermediateResponseHandler.class),
                                               isNotNull(SearchResultHandler.class));
        verify(expectedConnection).close();
        verifyZeroInteractionsForRemainingFactories(expectedFactory);
    }

    @Test
    public void searchRequestsShouldLinearProbeOnFailure1() throws Exception {
        searchRequestsShouldLinearProbeOnFailureImpl(searchRequest1);
    }

    @Test
    public void searchRequestsShouldLinearProbeOnFailure2() throws Exception {
        searchRequestsShouldLinearProbeOnFailureImpl(searchRequest2);
    }

    @Test
    public void searchRequestsShouldLinearProbeOnFailure3() throws Exception {
        searchRequestsShouldLinearProbeOnFailureImpl(searchRequest3);
    }

    private void searchRequestsShouldLinearProbeOnFailureImpl(final SearchRequest searchRequest) throws Exception {
        configureFactoriesOneAndTwoOffline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            connection.search(searchRequest, new ArrayList<>());
        }
        verify(factory3).getConnectionAsync();
        verify(connection3).searchAsync(same(searchRequest),
                                        isNull(IntermediateResponseHandler.class),
                                        isNotNull(SearchResultHandler.class));
        verify(connection3).close();
        verifyZeroInteractions(connection1);
        verifyZeroInteractions(connection2);
    }

    @Test
    public void searchRequestsShouldFailWhenAllFactoriesOffline() throws Exception {
        configureAllFactoriesOffline();
        try (Connection connection = loadBalancer.getConnectionAsync().get()) {
            try {
                connection.search(searchRequest1, new ArrayList<>());
            } catch (ConnectionException e) {
                assertThat(e.getResult().getResultCode()).isEqualTo(CLIENT_SIDE_CONNECT_ERROR);
            }
        }
        verify(factory1, atLeastOnce()).getConnectionAsync();
        verify(factory2, atLeastOnce()).getConnectionAsync();
        verify(factory3, atLeastOnce()).getConnectionAsync();
        verifyZeroInteractions(connection1);
        verifyZeroInteractions(connection2);
        verifyZeroInteractions(connection3);
    }

    @Mock private ConnectionFactory factory1;
    @Mock private ConnectionFactory factory2;
    @Mock private ConnectionFactory factory3;

    @Mock private AbstractAsynchronousConnection connection1;
    @Mock private AbstractAsynchronousConnection connection2;
    @Mock private AbstractAsynchronousConnection connection3;

    @Mock private AddRequest addRequest1;
    @Mock private AddRequest addRequest2;
    @Mock private AddRequest addRequest3;

    @Mock private BindRequest bindRequest1;
    @Mock private BindRequest bindRequest2;
    @Mock private BindRequest bindRequest3;

    @Mock private CompareRequest compareRequest1;
    @Mock private CompareRequest compareRequest2;
    @Mock private CompareRequest compareRequest3;

    @Mock private DeleteRequest deleteRequest1;
    @Mock private DeleteRequest deleteRequest2;
    @Mock private DeleteRequest deleteRequest3;

    @Mock private GenericExtendedRequest extendedRequest1;
    @Mock private GenericExtendedRequest extendedRequest2;
    @Mock private GenericExtendedRequest extendedRequest3;

    @Mock private ModifyRequest modifyRequest1;
    @Mock private ModifyRequest modifyRequest2;
    @Mock private ModifyRequest modifyRequest3;

    @Mock private ModifyDNRequest modifyDNRequest1;
    @Mock private ModifyDNRequest modifyDNRequest2;
    @Mock private ModifyDNRequest modifyDNRequest3;

    @Mock private SearchRequest searchRequest1;
    @Mock private SearchRequest searchRequest2;
    @Mock private SearchRequest searchRequest3;

    private ConnectionFactory loadBalancer;

    @BeforeMethod
    public void beforeMethod() {
        TestCaseUtils.setDefaultLogLevel(Level.SEVERE);
        initMocks(this);
        stub(this.connection1);
        stub(this.connection2);
        stub(this.connection3);
        loadBalancer = new RequestLoadBalancer("Test",
                                               asList(factory1, factory2, factory3),
                                               defaultOptions(), newNextFactoryFunction());
    }

    private Function<Request, Integer, NeverThrowsException> newNextFactoryFunction() {
        return new Function<Request, Integer, NeverThrowsException>() {
            @Override
            public Integer apply(final Request request) {
                if (request == addRequest1 || request == bindRequest1 || request == compareRequest1
                        || request == deleteRequest1 || request == extendedRequest1 || request == modifyRequest1
                        || request == modifyDNRequest1 || request == searchRequest1) {
                    return 0;
                }

                if (request == addRequest2 || request == bindRequest2 || request == compareRequest2
                        || request == deleteRequest2 || request == extendedRequest2 || request == modifyRequest2
                        || request == modifyDNRequest2 || request == searchRequest2) {
                    return 1;
                }

                if (request == addRequest3 || request == bindRequest3 || request == compareRequest3
                        || request == deleteRequest3 || request == extendedRequest3 || request == modifyRequest3
                        || request == modifyDNRequest3 || request == searchRequest3) {
                    return 2;
                }

                fail("Received unexpected request");
                return -1; // Keep compiler happy.
            }
        };
    }

    private void stub(final Connection connection) {
        when(connection.addAsync(any(AddRequest.class), any(IntermediateResponseHandler.class)))
                .thenReturn(newSuccessfulLdapPromise(newResult(ResultCode.SUCCESS)));
        when(connection.bindAsync(any(BindRequest.class), any(IntermediateResponseHandler.class)))
                .thenReturn(newSuccessfulLdapPromise(newBindResult(ResultCode.SUCCESS)));
        when(connection.compareAsync(any(CompareRequest.class), any(IntermediateResponseHandler.class)))
                .thenReturn(newSuccessfulLdapPromise(newCompareResult(ResultCode.SUCCESS)));
        when(connection.deleteAsync(any(DeleteRequest.class), any(IntermediateResponseHandler.class)))
                .thenReturn(newSuccessfulLdapPromise(newResult(ResultCode.SUCCESS)));
        when(connection.extendedRequestAsync(any(GenericExtendedRequest.class), any(IntermediateResponseHandler.class)))
                .thenReturn(newSuccessfulLdapPromise(newGenericExtendedResult(ResultCode.SUCCESS)));
        when(connection.modifyAsync(any(ModifyRequest.class), any(IntermediateResponseHandler.class)))
                .thenReturn(newSuccessfulLdapPromise(newResult(ResultCode.SUCCESS)));
        when(connection.modifyDNAsync(any(ModifyDNRequest.class), any(IntermediateResponseHandler.class)))
                .thenReturn(newSuccessfulLdapPromise(newResult(ResultCode.SUCCESS)));
        when(connection.searchAsync(any(SearchRequest.class), any(IntermediateResponseHandler.class),
                                    any(SearchResultHandler.class)))
                .thenReturn(newSuccessfulLdapPromise(newResult(ResultCode.SUCCESS)));
    }

    @AfterMethod
    public void afterMethod() {
        loadBalancer.close();
        TestCaseUtils.setDefaultLogLevel(Level.INFO);
    }

    private void configureAllFactoriesOnline() {
        when(factory1.getConnectionAsync())
                .thenReturn(Promises.<Connection, LdapException>newResultPromise(connection1));
        when(factory2.getConnectionAsync())
                .thenReturn(Promises.<Connection, LdapException>newResultPromise(connection2));
        when(factory3.getConnectionAsync())
                .thenReturn(Promises.<Connection, LdapException>newResultPromise(connection3));
    }

    private void configureFactoriesOneAndTwoOffline() {
        final LdapException connectionFailure = newLdapException(CLIENT_SIDE_CONNECT_ERROR);
        when(factory1.getConnectionAsync())
                .thenReturn(Promises.<Connection, LdapException>newExceptionPromise(connectionFailure));
        when(factory2.getConnectionAsync())
                .thenReturn(Promises.<Connection, LdapException>newExceptionPromise(connectionFailure));
        when(factory3.getConnectionAsync())
                .thenReturn(Promises.<Connection, LdapException>newResultPromise(connection3));
    }

    private void configureAllFactoriesOffline() {
        final LdapException connectionFailure = newLdapException(CLIENT_SIDE_CONNECT_ERROR);
        when(factory1.getConnectionAsync())
                .thenReturn(Promises.<Connection, LdapException>newExceptionPromise(connectionFailure));
        when(factory2.getConnectionAsync())
                .thenReturn(Promises.<Connection, LdapException>newExceptionPromise(connectionFailure));
        when(factory3.getConnectionAsync())
                .thenReturn(Promises.<Connection, LdapException>newExceptionPromise(connectionFailure));
    }

    private void verifyZeroInteractionsForRemainingFactories(final ConnectionFactory expectedFactory) {
        if (expectedFactory != factory1) {
            verifyZeroInteractions(factory1);
        }
        if (expectedFactory != factory2) {
            verifyZeroInteractions(factory2);
        }
        if (expectedFactory != factory3) {
            verifyZeroInteractions(factory3);
        }
    }
}
