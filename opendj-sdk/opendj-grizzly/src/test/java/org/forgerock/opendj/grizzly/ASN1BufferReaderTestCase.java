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
 *      Portions copyright 2011-2013 ForgeRock AS
 */

package org.forgerock.opendj.grizzly;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1ReaderTestCase;
import org.glassfish.grizzly.memory.ByteBufferWrapper;
import org.glassfish.grizzly.memory.MemoryManager;

/**
 * This class provides test cases for ASN1BufferReader.
 */
public class ASN1BufferReaderTestCase extends ASN1ReaderTestCase {
    @Override
    protected ASN1Reader getReader(final byte[] b, final int maxElementSize) throws IOException {
        final ByteBufferWrapper buffer = new ByteBufferWrapper(ByteBuffer.wrap(b));
        final ASN1BufferReader reader =
                new ASN1BufferReader(maxElementSize, MemoryManager.DEFAULT_MEMORY_MANAGER);
        reader.appendBytesRead(buffer);
        return reader;
    }
}
