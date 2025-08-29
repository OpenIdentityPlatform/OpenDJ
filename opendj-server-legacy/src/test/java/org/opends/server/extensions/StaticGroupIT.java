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
 *  Copyright 2025 3A Systems, LLC.
 */
package org.opends.server.extensions;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.opends.server.types.Modification;
import java.util.concurrent.*;
import java.util.*;
import static org.testng.Assert.*;

public class StaticGroupIT {
    private static final int THREAD_COUNT = 10;
    private static final int TOTAL_REQUESTS = 100;

    @Test
    public void testConcurrentUpdateMembersNoDeadlock() throws Exception {
        StaticGroup group = TestUtils.createNestedTestGroup();
        List<Modification> modifications = TestUtils.createAddUserModifications();
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            futures.add(executor.submit(() -> {
                try {
                    group.updateMembers(modifications);
                } catch (org.opends.server.types.DirectoryException e) {
                    throw new RuntimeException(e);
                }
            }));
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();
    assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
    // Проверяем, что группа содержит пользователя
    assertTrue(group.isMember(TestUtils.TEST_USER_DN, null));
    }
}
}
