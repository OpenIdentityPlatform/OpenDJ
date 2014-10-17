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
 *      Copyright 2014 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import org.testng.annotations.Test;

import com.forgerock.opendj.ldap.tools.PerformanceRunner.ResponseTimeBuckets;

import static org.fest.assertions.Assertions.*;

public class PerformanceRunnerStatsTestCase extends ToolsTestCase {
    @Test
    public void testResponseTimeBuckets() throws Exception {
        ResponseTimeBuckets rtb = PerformanceRunner.getResponseTimeBuckets();
        for (long etime = 100L; etime <= 6000000L; etime += 10L) {
            rtb.addTimeToInterval(etime * 1000L);
        }
        double[] percentiles = new double[] { 0.0025, 0.0050, 0.0075, 0.05, 0.075, 0.1, 1.0, 2.0, 5.5, 10.0, 30.0,
            50.0, 80.0, 99.9, 99.99, 99.999 };
        long[] expectedResult = new long[] { 240L, 390L, 540L, 3000L, 4500L, 6000L, 60000L, 120000L, 330000L,
            600000L, 1800000L, 3000000L, 4800000L, 5500000L, 5500000L, 5500000L };
        int count = expectedResult.length;
        for (Long computedPercentile : rtb.getPercentile(percentiles, 599991)) {
            assertThat(computedPercentile).isEqualTo(expectedResult[--count]);
        }
    }
}
