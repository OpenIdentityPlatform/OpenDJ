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
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.io.ASN1WriterTestCase;
import org.forgerock.opendj.ldap.DecodeException;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.memory.ByteBufferWrapper;
import org.glassfish.grizzly.memory.MemoryManager;

/**
 * This class provides testcases for ASN1BufferWriter.
 */
public class ASN1BufferWriterTestCase extends ASN1WriterTestCase {

    private final ASN1BufferWriter writer = new ASN1BufferWriter();

    @Override
    protected byte[] getEncodedBytes() throws IOException, DecodeException {
        final Buffer buffer = writer.getBuffer();
        final byte[] byteArray = new byte[buffer.remaining()];
        buffer.get(byteArray);
        return byteArray;
    }

    @Override
    protected ASN1Reader getReader(final byte[] encodedBytes) throws DecodeException, IOException {
        final ByteBufferWrapper buffer = new ByteBufferWrapper(ByteBuffer.wrap(encodedBytes));
        final ASN1BufferReader reader =
                new ASN1BufferReader(0, MemoryManager.DEFAULT_MEMORY_MANAGER);
        reader.appendBytesRead(buffer);
        return reader;
    }

    @Override
    protected ASN1Writer getWriter() throws IOException {
        writer.flush();
        writer.recycle();
        return writer;
    }
}
