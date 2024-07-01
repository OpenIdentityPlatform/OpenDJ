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
package org.forgerock.opendj.ldap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ConsistentHashMapTest extends SdkTestCase {
    private static final String P1 = "partition-1";
    private static final String P2 = "partition-2";
    private static final String P3 = "partition-3";
    private static final String P4 = "partition-4";

    @DataProvider
    public static Object[][] md5HashFunction() {
        // @Checkstyle:off
        return new Object[][] {
            { P1, ByteString.valueOfHex("94f940e6c2a31703d6be067b87d67968").toInt() },
            { P2, ByteString.valueOfHex("1038f27a22f8b574670bfdd7f918ffb4").toInt() },
            { P3, ByteString.valueOfHex("a88f746bfae24480be40df47c679228a").toInt() },
            { P4, ByteString.valueOfHex("bdb89200d2a310f5afb345621f544183").toInt() },
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "md5HashFunction")
    public void testMD5HashFunction(final String s, final int expected) {
        assertThat(ConsistentHashMap.MD5.apply(s)).isEqualTo(expected);
    }

    @Test
    public void testConsistentHashMap() {
        // Create a hash function that returns predictable values.
        @SuppressWarnings({ "rawtypes", "unchecked" })
        final Function<Object, Integer, NeverThrowsException> hashFunction = mock(Function.class);

        // Hashes for the 16 logical partitions.
        when(hashFunction.apply(any())).thenReturn(0x00000000, 0x40000000, 0x80000000, 0xC0000000,
                                                   0x10000000, 0x50000000, 0x90000000, 0xD0000000,
                                                   0x20000000, 0x60000000, 0xA0000000, 0xE0000000,
                                                   0x30000000, 0x70000000, 0xB0000000, 0xF0000000);

        final ConsistentHashMap<String> partitions = new ConsistentHashMap<>(hashFunction);
        partitions.put(P1, P1, 4);
        partitions.put(P2, P2, 4);
        partitions.put(P3, P3, 4);
        partitions.put(P4, P4, 4);

        // Check the structure of the CHM.
        assertThat(partitions.isEmpty()).isFalse();
        assertThat(partitions.size()).isEqualTo(4);
        assertThat(partitions.getAll()).containsOnly(P1, P2, P3, P4);

        final Map<String, Long> weights = partitions.getWeights();
        assertThat(weights.size()).isEqualTo(4);
        assertThat(weights).containsEntry(P1, 0x40000000L);
        assertThat(weights).containsEntry(P2, 0x40000000L);
        assertThat(weights).containsEntry(P3, 0x40000000L);
        assertThat(weights).containsEntry(P4, 0x40000000L);

        // Hashes for several key lookups.
        when(hashFunction.apply(any())).thenReturn(0x70000001, 0x7FFFFFFF, 0x80000000,          // P1
                                                   0xF0000001, 0xFFFFFFFF, 0x00000000,          // P1
                                                   0x00000001, 0x0FFFFFFF, 0x10000000,          // P2
                                                   0xE0000001, 0xEFFFFFFF, 0xF0000000);         // P4
        assertThat(partitions.get(P1)).isEqualTo(P1);
        assertThat(partitions.get(P1)).isEqualTo(P1);
        assertThat(partitions.get(P1)).isEqualTo(P1);

        assertThat(partitions.get(P1)).isEqualTo(P1);
        assertThat(partitions.get(P1)).isEqualTo(P1);
        assertThat(partitions.get(P1)).isEqualTo(P1);

        assertThat(partitions.get(P2)).isEqualTo(P2);
        assertThat(partitions.get(P2)).isEqualTo(P2);
        assertThat(partitions.get(P2)).isEqualTo(P2);

        assertThat(partitions.get(P4)).isEqualTo(P4);
        assertThat(partitions.get(P4)).isEqualTo(P4);
        assertThat(partitions.get(P4)).isEqualTo(P4);

        // Remove a partition and retry.
        when(hashFunction.apply(any())).thenReturn(0x10000000, 0x50000000, 0x90000000, 0xD0000000);
        partitions.remove(P2);

        // Check the structure of the CHM.
        assertThat(partitions.isEmpty()).isFalse();
        assertThat(partitions.size()).isEqualTo(3);
        assertThat(partitions.getAll()).containsOnly(P1, P3, P4);

        final Map<String, Long> newWeights = partitions.getWeights();
        assertThat(newWeights.size()).isEqualTo(3);
        assertThat(newWeights).containsEntry(P1, 0x40000000L);
        assertThat(newWeights).containsEntry(P3, 0x80000000L); // P2 now falls through to P3
        assertThat(newWeights).containsEntry(P4, 0x40000000L);

        // Hashes for several key lookups.
        when(hashFunction.apply(any())).thenReturn(0x70000001, 0x7FFFFFFF, 0x80000000,          // P1
                                                   0xF0000001, 0xFFFFFFFF, 0x00000000,          // P1
                                                   0x00000001, 0x0FFFFFFF, 0x10000000,          // P3 (was P2)
                                                   0xE0000001, 0xEFFFFFFF, 0xF0000000);         // P4
        assertThat(partitions.get(P1)).isEqualTo(P1);
        assertThat(partitions.get(P1)).isEqualTo(P1);
        assertThat(partitions.get(P1)).isEqualTo(P1);

        assertThat(partitions.get(P1)).isEqualTo(P1);
        assertThat(partitions.get(P1)).isEqualTo(P1);
        assertThat(partitions.get(P1)).isEqualTo(P1);

        assertThat(partitions.get(P2)).isEqualTo(P3);
        assertThat(partitions.get(P2)).isEqualTo(P3);
        assertThat(partitions.get(P2)).isEqualTo(P3);

        assertThat(partitions.get(P4)).isEqualTo(P4);
        assertThat(partitions.get(P4)).isEqualTo(P4);
        assertThat(partitions.get(P4)).isEqualTo(P4);
    }
}
