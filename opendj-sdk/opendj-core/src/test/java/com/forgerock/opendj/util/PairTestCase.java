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
 *      Copyright 2014 ForgeRock AS
 */
package com.forgerock.opendj.util;

import java.math.BigDecimal;
import java.util.Comparator;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static com.forgerock.opendj.util.Pair.*;

import static org.fest.assertions.Assertions.*;

/**
 * Tests the {@link Pair} class.
 */
@SuppressWarnings("javadoc")
public class PairTestCase extends UtilTestCase {

    @Test
    public void getters() throws Exception {
        final Pair<BigDecimal, BigDecimal> pair = of(BigDecimal.ONE, BigDecimal.TEN);
        assertThat(pair.getFirst()).isSameAs(BigDecimal.ONE);
        assertThat(pair.getSecond()).isSameAs(BigDecimal.TEN);
    }

    @DataProvider
    public Object[][] pairsEqualDataProvider() {
        final Pair<Integer, Integer> p12 = of(1, 2);
        return new Object[][] {
            new Object[] { p12, p12 },
            new Object[] { p12, of(1, 2) },
            new Object[] { of(null, null), empty() },
        };
    }

    @Test(dataProvider = "pairsEqualDataProvider")
    public void pairsEqual(Pair<Integer, Integer> p1, Pair<Integer, Integer> p2) {
        assertThat(p1).isEqualTo(p2);
        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
    }

    @DataProvider
    public Object[][] pairsNotEqualDataProvider() {
        final Pair<Integer, Integer> p12 = of(1, 2);
        return new Object[][] {
            new Object[] { p12, null },
            new Object[] { p12, empty() },
            new Object[] { empty(), p12 },
            new Object[] { of(null, 2), empty() },
            new Object[] { empty(), of(null, 2) },
        };
    }

    @Test(dataProvider = "pairsNotEqualDataProvider")
    public void pairsNotEqual(Pair<Integer, Integer> p1, Pair<Integer, Integer> p2) throws Exception {
        assertThat(p1).isNotEqualTo(p2);
        if (p2 != null) {
            assertThat(p1.hashCode()).isNotEqualTo(p2.hashCode());
        }
    }

    @DataProvider
    public Object[][] pairComparatorDataProvider() {
        return new Object[][] {
            new Object[] { of(2, 3), of(1, 4), 1 },
            new Object[] { of(1, 4), of(2, 3), -1 },
            new Object[] { of(1, 3), of(1, 2), 1 },
            new Object[] { of(1, 2), of(1, 3), -1 },
        };
    }

    @Test(dataProvider = "pairComparatorDataProvider")
    public void pairComparator(
            Pair<Integer, Integer> p1,
            Pair<Integer, Integer> p2,
            int compareResult) {
        final Comparator<Pair<Integer, Integer>> cmp = getPairComparator();
        assertThat(cmp.compare(p1, p2)).isEqualTo(compareResult);
    }

}
