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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2013-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.util.Options;

/**
 * A round robin load balancing algorithm distributes connection requests across a list of
 * connection factories one at a time. When the end of the list is reached, the algorithm starts again from the
 * beginning.
 */
final class RoundRobinLoadBalancingAlgorithm extends AbstractLoadBalancingAlgorithm {
    private final int maxIndex;
    private final AtomicInteger nextIndex = new AtomicInteger(-1);

    RoundRobinLoadBalancingAlgorithm(final Collection<? extends ConnectionFactory> factories, final Options options) {
        super(factories, options);
        this.maxIndex = factories.size();
    }

    @Override
    String getAlgorithmName() {
        return "RoundRobin";
    }

    @Override
    int getInitialConnectionFactoryIndex() {
        // A round robin pool of one connection factories is unlikely in
        // practice and requires special treatment.
        if (maxIndex == 1) {
            return 0;
        }

        // Determine the next factory to use: avoid blocking algorithm.
        int oldNextIndex;
        int newNextIndex;
        do {
            oldNextIndex = nextIndex.get();
            newNextIndex = oldNextIndex + 1;
            if (newNextIndex == maxIndex) {
                newNextIndex = 0;
            }
        } while (!nextIndex.compareAndSet(oldNextIndex, newNextIndex));

        // There's a potential, but benign, race condition here: other threads
        // could jump in and rotate through the list before we return the
        // connection factory.
        return newNextIndex;
    }

}
