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
 * Portions copyright 2011-2016 ForgeRock AS.
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
import org.testng.annotations.Test;

/**
 * This class provides testcases for ASN1BufferWriter.
 */
@Test
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
        return new ASN1BufferReader(0, new ByteBufferWrapper(ByteBuffer.wrap(encodedBytes)));
    }

    @Override
    protected ASN1Writer getWriter() throws IOException {
        writer.flush();
        writer.reset(MemoryManager.DEFAULT_MEMORY_MANAGER);
        return writer;
    }
}
