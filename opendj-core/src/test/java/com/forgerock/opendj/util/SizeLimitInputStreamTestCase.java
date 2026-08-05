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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package com.forgerock.opendj.util;

import static org.fest.assertions.Assertions.*;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.testng.annotations.Test;

/** Test {@code SizeLimitInputStream}. */
@SuppressWarnings("javadoc")
public final class SizeLimitInputStreamTestCase extends UtilTestCase {

    @Test
    public void testSkipAccountsForTheBytesActuallySkipped() throws Exception {
        // A BufferedInputStream only skips what its buffer holds, without reaching the end of the
        // parent stream, which is exactly the case getBytesRead() used to get wrong.
        final InputStream parent = new BufferedInputStream(new ByteArrayInputStream(new byte[32]), 4);
        // Fill the buffer with 4 bytes and consume one of them, leaving 3 skippable bytes in it.
        parent.read();

        final SizeLimitInputStream stream = new SizeLimitInputStream(parent, 32);
        assertThat(stream.skip(16)).isEqualTo(3);
        assertThat(stream.getBytesRead()).isEqualTo(3);
    }

    @Test
    public void testSkipIsCappedToTheSizeLimit() throws Exception {
        final SizeLimitInputStream stream =
                new SizeLimitInputStream(new ByteArrayInputStream(new byte[32]), 8);

        assertThat(stream.skip(20)).isEqualTo(8);
        assertThat(stream.getBytesRead()).isEqualTo(8);
        assertThat(stream.read()).isEqualTo(-1);
    }

    @Test
    public void testSkipStopsAtTheEndOfTheParentStream() throws Exception {
        final SizeLimitInputStream stream =
                new SizeLimitInputStream(new ByteArrayInputStream(new byte[2]), 8);

        assertThat(stream.skip(8)).isEqualTo(2);
        assertThat(stream.getBytesRead()).isEqualTo(2);
    }

    @Test
    public void testReadAccountsForTheBytesActuallyRead() throws Exception {
        final SizeLimitInputStream stream =
                new SizeLimitInputStream(new ByteArrayInputStream(new byte[32]), 8);

        assertThat(stream.read()).isEqualTo(0);
        assertThat(stream.read(new byte[16])).isEqualTo(7);
        assertThat(stream.getBytesRead()).isEqualTo(8);
        assertThat(stream.read()).isEqualTo(-1);
    }
}
