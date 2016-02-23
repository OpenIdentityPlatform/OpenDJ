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
 * Copyright 2012-2014 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import static org.fest.assertions.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.opendj.ldap.responses.ExtendedResult;

/**
 * A connection event listener which records events and signals when it has been
 * notified.
 */
@SuppressWarnings("javadoc")
public final class MockConnectionEventListener implements ConnectionEventListener {
    private final CountDownLatch closedLatch = new CountDownLatch(1);
    private final CountDownLatch errorLatch = new CountDownLatch(1);
    private final CountDownLatch notificationLatch = new CountDownLatch(1);
    private Boolean isDisconnectNotification;
    private LdapException error;
    private ExtendedResult notification;
    private final AtomicInteger invocationCount = new AtomicInteger();

    /** {@inheritDoc} */
    @Override
    public void handleConnectionClosed() {
        invocationCount.incrementAndGet();
        closedLatch.countDown();
    }

    /** {@inheritDoc} */
    @Override
    public void handleConnectionError(boolean isDisconnectNotification, LdapException error) {
        this.isDisconnectNotification = isDisconnectNotification;
        this.error = error;
        invocationCount.incrementAndGet();
        errorLatch.countDown();
    }

    /** {@inheritDoc} */
    @Override
    public void handleUnsolicitedNotification(ExtendedResult notification) {
        this.notification = notification;
        invocationCount.incrementAndGet();
        notificationLatch.countDown();
    }

    public void awaitClose(long timeout, TimeUnit unit) {
        await(closedLatch, timeout, unit);
    }

    public void awaitError(long timeout, TimeUnit unit) {
        await(errorLatch, timeout, unit);
    }

    public void awaitNotification(long timeout, TimeUnit unit) {
        await(notificationLatch, timeout, unit);
    }

    public Boolean isDisconnectNotification() {
        return isDisconnectNotification;
    }

    public LdapException getError() {
        return error;
    }

    public ExtendedResult getNotification() {
        return notification;
    }

    private void await(CountDownLatch latch, long timeout, TimeUnit unit) {
        try {
            assertThat(latch.await(timeout, unit)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public int getInvocationCount() {
        return invocationCount.get();
    }
}
