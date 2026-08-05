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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions copyright 2013 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.opendj.io;

import static org.testng.Assert.*;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;

import org.testng.annotations.Test;

/**
 * Test class for ASN1InputStreamReader.
 */
public class ASN1InputStreamReaderTestCase extends ASN1ReaderTestCase {
    @Override
    protected ASN1Reader getReader(final byte[] b, final int maxElementSize) {
        final ByteArrayInputStream inStream = new ByteArrayInputStream(b);
        return new ASN1InputStreamReader(inStream, maxElementSize);
    }

    /**
     * Returns a reader whose underlying stream skips fewer bytes than requested without having
     * reached its end, which is what {@code BufferedInputStream} does once its buffer is partially
     * consumed.
     */
    private ASN1Reader getBufferedReader(final byte[] b) {
        return new ASN1InputStreamReader(new BufferedInputStream(new ByteArrayInputStream(b), 4), 0);
    }

    /**
     * Tests that the trailing components of a sequence are fully skipped even when the underlying
     * stream skips fewer bytes than requested.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test
    public void testDecodeSequenceIncompleteReadOverBufferedStream() throws Exception {
        // A sequence holding ten booleans, of which only the first one is read, followed by an
        // integer which must still be decoded correctly once the sequence has been skipped.
        final byte[] b =
                new byte[] { 0x30, 0x0C, 0x01, 0x01, 0x00, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
                    0x01, 0x01, 0x02, 0x01, 0x7F };
        final ASN1Reader reader = getBufferedReader(b);

        reader.readStartSequence();
        assertFalse(reader.readBoolean());
        reader.readEndSequence();

        assertEquals(reader.readInteger(), 127);
    }

    /**
     * Tests that {@code skipElement} does not report a truncated value when the underlying stream
     * skips fewer bytes than requested.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @Test
    public void testSkipElementOverBufferedStream() throws Exception {
        final byte[] b =
                new byte[] { 0x30, 0x0C, 0x02, 0x01, 0x05, 0x04, 0x04, 0x61, 0x62, 0x63, 0x64, 0x02,
                    0x01, 0x7F };
        final ASN1Reader reader = getBufferedReader(b);

        reader.readStartSequence();
        assertEquals(reader.readInteger(), 5);
        reader.skipElement();
        assertEquals(reader.readInteger(), 127);
        reader.readEndSequence();
    }
}
