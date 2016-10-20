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
package org.forgerock.opendj.grizzly;

import org.forgerock.opendj.ldap.spi.LdapMessages.LdapResponseMessage;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.WriteHandler;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import com.forgerock.reactive.Completable;

final class LdapResponseMessageWriter implements Subscriber<LdapResponseMessage> {

    private final Connection<?> connection;
    private final Completable.Subscriber completable;
    private Subscription upstream;

    LdapResponseMessageWriter(final Connection<?> connection, final Completable.Subscriber completable) {
        this.connection = connection;
        this.completable = completable;
    }

    @Override
    public void onSubscribe(Subscription s) {
        this.upstream = s;
        requestMore();
    }

    private void requestMore() {
        if (connection.canWrite()) {
            upstream.request(1);
        } else {
            connection.notifyCanWrite(new WriteHandler() {
                @Override
                public void onWritePossible() throws Exception {
                    upstream.request(1);
                }

                @Override
                public void onError(Throwable t) {
                    upstream.cancel();
                    completable.onError(t);
                }
            });
        }
    }

    @Override
    public void onNext(LdapResponseMessage message) {
        connection.write(message);
        requestMore();
    }

    @Override
    public void onError(Throwable t) {
        completable.onError(t);
    }

    @Override
    public void onComplete() {
        completable.onComplete();
    }
}
