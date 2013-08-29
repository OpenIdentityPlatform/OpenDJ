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
 *      Copyright 2013 ForgeRock AS
 */
package org.forgerock.opendj.ldap;

import static java.util.Arrays.asList;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;
import static org.forgerock.opendj.ldap.Connections.newLoadBalancer;
import static org.forgerock.opendj.ldap.ErrorResultException.newErrorResult;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.logging.Level;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.forgerock.opendj.util.StaticUtils;

@SuppressWarnings("javadoc")
public class AbstractLoadBalancingAlgorithmTestCase extends SdkTestCase {

    /**
     * Disables logging before the tests.
     */
    @BeforeClass()
    public void disableLogging() {
        StaticUtils.DEBUG_LOG.setLevel(Level.SEVERE);
    }

    /**
     * Re-enable logging after the tests.
     */
    @AfterClass()
    public void enableLogging() {
        StaticUtils.DEBUG_LOG.setLevel(Level.INFO);
    }

    /**
     * Tests fix for OPENDJ-1112: when a load balancer fails completely the
     * connection exception should include the last error that occurred.
     */
    @Test
    public void testFinalFailureExposedAsCause() throws Exception {
        /*
         * Create load-balancer with two failed connection factories.
         */
        final ConnectionFactory first = mock(ConnectionFactory.class);
        final ErrorResultException firstError = newErrorResult(ResultCode.CLIENT_SIDE_SERVER_DOWN);
        when(first.getConnection()).thenThrow(firstError);

        final ConnectionFactory second = mock(ConnectionFactory.class);
        final ErrorResultException secondError = newErrorResult(ResultCode.CLIENT_SIDE_SERVER_DOWN);
        when(second.getConnection()).thenThrow(secondError);

        final ConnectionFactory loadBalancer =
                newLoadBalancer(new RoundRobinLoadBalancingAlgorithm(asList(first, second)));

        /*
         * Belt and braces check to ensure that factory methods don't return
         * same instance and fool this test.
         */
        assertThat(firstError).isNotSameAs(secondError);

        try {
            loadBalancer.getConnection().close();
            fail("Unexpectedly obtained a connection");
        } catch (ErrorResultException e) {
            assertThat(e.getCause()).isSameAs(secondError);
        } finally {
            loadBalancer.close();
        }
    }
}
