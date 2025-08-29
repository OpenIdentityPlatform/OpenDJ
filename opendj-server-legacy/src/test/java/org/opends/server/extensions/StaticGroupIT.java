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
        }
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();
    assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
    // Проверяем, что группа содержит пользователя
    assertTrue(group.isMember(TestUtils.TEST_USER_DN, null));
    }
}
