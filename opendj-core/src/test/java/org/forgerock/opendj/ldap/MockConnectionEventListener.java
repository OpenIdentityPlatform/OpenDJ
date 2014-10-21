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
 *      Copyright 2012-2014 ForgeRock AS.
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
