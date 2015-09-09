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
 *      Copyright 2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap;

import static org.fest.assertions.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.forgerock.opendj.util.PackedLong;

/**
 * Test case for PackedLong.
 */
@Test(groups = "unit")
public class PackedLongTestCase extends SdkTestCase {

    @Test(dataProvider = "unsignedLongValues")
    public void testCanReadWriteByteArray(int size, long value) throws IOException {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();

        PackedLong.writeCompactUnsigned(os, value);
        assertThat(os.size()).isEqualTo(size);

        assertThat(PackedLong.getEncodedSize(value)).isEqualTo(size);
        assertThat(PackedLong.readCompactUnsignedLong(new ByteArrayInputStream(os.toByteArray()))).isEqualTo(value);
    }

    @DataProvider
    private static Object[][] unsignedLongValues() {
        return new Object[][] {
            { 1, 0 }, { 1, 0x7FL },
            { 2, 0x80L }, { 2, 0x3FFFL },
            { 3, 0x4000L }, { 3, 0x1FFFFFL },
            { 4, 0x200000L }, { 4, 0x0FFFFFFFL },
            { 5, 0x10000000L }, { 5, 0x7FFFFFFFFL },
            { 6, 0x800000000L }, { 6, 0x3FFFFFFFFFFL },
            { 7, 0x40000000000L }, { 7, 0x1FFFFFFFFFFFFL },
            { 8, 0x2000000000000L }, { 8, 0x00FFFFFFFFFFFFFFL }
        };
    }
}
