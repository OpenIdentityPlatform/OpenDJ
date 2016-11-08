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
 * Portions copyright 2012-2016 ForgeRock AS.
 */
package org.forgerock.opendj.grizzly;

import static com.forgerock.opendj.ldap.CoreMessages.ERR_ASN1_SEQUENCE_WRITE_NOT_STARTED;

import java.io.IOException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.io.AbstractASN1Writer;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Cacheable;
import org.glassfish.grizzly.memory.MemoryManager;

import com.forgerock.opendj.util.StaticUtils;

/** Grizzly ASN1 writer implementation. */
final class ASN1BufferWriter extends AbstractASN1Writer implements Cacheable {
    private class ChildSequenceBuffer implements SequenceBuffer {
        private SequenceBuffer parent;
        private ChildSequenceBuffer child;
        private final ByteStringBuilder buffer = new ByteStringBuilder(BUFFER_INIT_SIZE);

        @Override
        public SequenceBuffer endSequence() throws IOException {
            writeLength(parent, buffer.length());
            parent.writeByteArray(buffer.getBackingArray(), 0, buffer.length());
            buffer.clearAndTruncate(DEFAULT_MAX_INTERNAL_BUFFER_SIZE, BUFFER_INIT_SIZE);
            if (logger.isTraceEnabled()) {
                logger.trace("WRITE ASN.1 END SEQUENCE(length=%d)", buffer.length());
            }
            return parent;
        }

        @Override
        public SequenceBuffer startSequence(final byte type) throws IOException {
            if (child == null) {
                child = new ChildSequenceBuffer();
                child.parent = this;
            }
            buffer.appendByte(type);
            child.buffer.clear();
            return child;
        }

        @Override
        public void writeByte(final byte b) throws IOException {
            buffer.appendByte(b);
        }

        @Override
        public void writeByteSequence(ByteSequence bs) {
            buffer.appendBytes(bs);
        }

        @Override
        public void writeByteArray(final byte[] bs, final int offset, final int length) throws IOException {
            buffer.appendBytes(bs, offset, length);
        }
    }

    private class RootSequenceBuffer implements SequenceBuffer {
        private ChildSequenceBuffer child;

        @Override
        public SequenceBuffer endSequence() throws IOException {
            final LocalizableMessage message = ERR_ASN1_SEQUENCE_WRITE_NOT_STARTED.get();
            throw new IllegalStateException(message.toString());
        }

        @Override
        public SequenceBuffer startSequence(final byte type) throws IOException {
            if (child == null) {
                child = new ChildSequenceBuffer();
                child.parent = this;
            }
            ensureAdditionalCapacity(1);
            outBuffer.put(type);
            child.buffer.clear();
            return child;
        }

        @Override
        public void writeByte(final byte b) throws IOException {
            ensureAdditionalCapacity(1);
            outBuffer.put(b);
        }

        @Override
        public void writeByteSequence(ByteSequence bs) {
            ensureAdditionalCapacity(bs.length());
            bs.copyTo(outBuffer.toByteBuffer());
            outBuffer.position(outBuffer.position() + bs.length());
        }

        @Override
        public void writeByteArray(final byte[] bs, final int offset, final int length)
                throws IOException {
            ensureAdditionalCapacity(length);
            outBuffer.put(bs, offset, length);
        }
    }

    private interface SequenceBuffer {
        SequenceBuffer endSequence() throws IOException;

        SequenceBuffer startSequence(byte type) throws IOException;

        void writeByte(byte b) throws IOException;

        void writeByteSequence(ByteSequence bs) throws IOException;

        void writeByteArray(byte[] bs, int offset, int length) throws IOException;
    }

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /** Initial size of newly created buffers. */
    private static final int BUFFER_INIT_SIZE = 1024;
    /** Default maximum size for cached protocol/entry encoding buffers. */
    private static final int DEFAULT_MAX_INTERNAL_BUFFER_SIZE = 32 * 1024;

    private MemoryManager<Buffer> memoryManager;
    private SequenceBuffer sequenceBuffer;
    private Buffer outBuffer;
    private final RootSequenceBuffer rootBuffer;

    /** Creates a new ASN.1 writer that writes to a StreamWriter. */
    ASN1BufferWriter() {
        this.rootBuffer = new RootSequenceBuffer();
    }

    /** Reset the writer. */
    void reset(final MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
        sequenceBuffer = rootBuffer;
        outBuffer = memoryManager.allocate(BUFFER_INIT_SIZE);
    }

    void ensureAdditionalCapacity(final int size) {
        final int newCount = outBuffer.position() + size;
        if (newCount > outBuffer.limit()) {
            outBuffer = memoryManager.reallocate(outBuffer, Math.max(outBuffer.limit() << 1, newCount));
        }
    }

    /**
     * Closes this ASN.1 writer and the underlying outputstream. Any unfinished
     * sequences will be ended.
     *
     * @throws IOException
     *             if an error occurs while closing the stream.
     */
    @Override
    public void close() throws IOException {
        outBuffer = null;
    }

    /**
     * Flushes the stream.
     *
     * @throws IOException
     *             If an I/O error occurs
     */
    @Override
    public void flush() throws IOException {
        // Do nothing
    }

    /** Recycle the writer to allow re-use. */
    @Override
    public void recycle() {
        memoryManager = null;
        sequenceBuffer = null;
        outBuffer = null;
    }

    @Override
    public ASN1Writer writeBoolean(final byte type, final boolean booleanValue) throws IOException {
        sequenceBuffer.writeByte(type);
        writeLength(sequenceBuffer, 1);
        sequenceBuffer.writeByte(booleanValue ? ASN1.BOOLEAN_VALUE_TRUE : ASN1.BOOLEAN_VALUE_FALSE);

        if (logger.isTraceEnabled()) {
            logger.trace("WRITE ASN.1 BOOLEAN(type=0x%x, length=%d, value=%s)", type, 1, booleanValue);
        }
        return this;
    }

    @Override
    public ASN1Writer writeEndSequence() throws IOException {
        sequenceBuffer = sequenceBuffer.endSequence();

        return this;
    }

    @Override
    public ASN1Writer writeEndSet() throws IOException {
        return writeEndSequence();
    }

    @Override
    public ASN1Writer writeEnumerated(final byte type, final int intValue) throws IOException {
        return writeInteger(type, intValue);
    }

    @Override
    public ASN1Writer writeInteger(final byte type, final int intValue) throws IOException {
        sequenceBuffer.writeByte(type);
        if (((intValue < 0) && ((intValue & 0xFFFFFF80) == 0xFFFFFF80))
                || ((intValue & 0x0000007F) == intValue)) {
            writeLength(sequenceBuffer, 1);
            sequenceBuffer.writeByte((byte) intValue);
            if (logger.isTraceEnabled()) {
                logger.trace("WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)", type, 1, intValue);
            }
        } else if (((intValue < 0) && ((intValue & 0xFFFF8000) == 0xFFFF8000))
                || ((intValue & 0x00007FFF) == intValue)) {
            writeLength(sequenceBuffer, 2);
            sequenceBuffer.writeByte((byte) (intValue >> 8));
            sequenceBuffer.writeByte((byte) intValue);
            if (logger.isTraceEnabled()) {
                logger.trace("WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)", type, 2, intValue);
            }
        } else if (((intValue < 0) && ((intValue & 0xFF800000) == 0xFF800000))
                || ((intValue & 0x007FFFFF) == intValue)) {
            writeLength(sequenceBuffer, 3);
            sequenceBuffer.writeByte((byte) (intValue >> 16));
            sequenceBuffer.writeByte((byte) (intValue >> 8));
            sequenceBuffer.writeByte((byte) intValue);
            if (logger.isTraceEnabled()) {
                logger.trace("WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)", type, 3, intValue);
            }
        } else {
            writeLength(sequenceBuffer, 4);
            sequenceBuffer.writeByte((byte) (intValue >> 24));
            sequenceBuffer.writeByte((byte) (intValue >> 16));
            sequenceBuffer.writeByte((byte) (intValue >> 8));
            sequenceBuffer.writeByte((byte) intValue);
            if (logger.isTraceEnabled()) {
                logger.trace("WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)", type, 4, intValue);
            }
        }
        return this;
    }

    @Override
    public ASN1Writer writeInteger(final byte type, final long longValue) throws IOException {
        sequenceBuffer.writeByte(type);
        if (((longValue < 0) && ((longValue & 0xFFFFFFFFFFFFFF80L) == 0xFFFFFFFFFFFFFF80L))
                || ((longValue & 0x000000000000007FL) == longValue)) {
            writeLength(sequenceBuffer, 1);
            sequenceBuffer.writeByte((byte) longValue);
            if (logger.isTraceEnabled()) {
                logger.trace("WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)", type, 1, longValue);
            }
        } else if (((longValue < 0) && ((longValue & 0xFFFFFFFFFFFF8000L) == 0xFFFFFFFFFFFF8000L))
                || ((longValue & 0x0000000000007FFFL) == longValue)) {
            writeLength(sequenceBuffer, 2);
            sequenceBuffer.writeByte((byte) (longValue >> 8));
            sequenceBuffer.writeByte((byte) longValue);
            if (logger.isTraceEnabled()) {
                logger.trace("WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)", type, 2, longValue);
            }
        } else if (((longValue < 0) && ((longValue & 0xFFFFFFFFFF800000L) == 0xFFFFFFFFFF800000L))
                || ((longValue & 0x00000000007FFFFFL) == longValue)) {
            writeLength(sequenceBuffer, 3);
            sequenceBuffer.writeByte((byte) (longValue >> 16));
            sequenceBuffer.writeByte((byte) (longValue >> 8));
            sequenceBuffer.writeByte((byte) longValue);
            if (logger.isTraceEnabled()) {
                logger.trace("WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)", type, 3, longValue);
            }
        } else if (((longValue < 0) && ((longValue & 0xFFFFFFFF80000000L) == 0xFFFFFFFF80000000L))
                || ((longValue & 0x000000007FFFFFFFL) == longValue)) {
            writeLength(sequenceBuffer, 4);
            sequenceBuffer.writeByte((byte) (longValue >> 24));
            sequenceBuffer.writeByte((byte) (longValue >> 16));
            sequenceBuffer.writeByte((byte) (longValue >> 8));
            sequenceBuffer.writeByte((byte) longValue);
            if (logger.isTraceEnabled()) {
                logger.trace("WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)", type, 4, longValue);
            }
        } else if (((longValue < 0) && ((longValue & 0xFFFFFF8000000000L) == 0xFFFFFF8000000000L))
                || ((longValue & 0x0000007FFFFFFFFFL) == longValue)) {
            writeLength(sequenceBuffer, 5);
            sequenceBuffer.writeByte((byte) (longValue >> 32));
            sequenceBuffer.writeByte((byte) (longValue >> 24));
            sequenceBuffer.writeByte((byte) (longValue >> 16));
            sequenceBuffer.writeByte((byte) (longValue >> 8));
            sequenceBuffer.writeByte((byte) longValue);
            if (logger.isTraceEnabled()) {
                logger.trace("WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)", type, 5, longValue);
            }
        } else if (((longValue < 0) && ((longValue & 0xFFFF800000000000L) == 0xFFFF800000000000L))
                || ((longValue & 0x00007FFFFFFFFFFFL) == longValue)) {
            writeLength(sequenceBuffer, 6);
            sequenceBuffer.writeByte((byte) (longValue >> 40));
            sequenceBuffer.writeByte((byte) (longValue >> 32));
            sequenceBuffer.writeByte((byte) (longValue >> 24));
            sequenceBuffer.writeByte((byte) (longValue >> 16));
            sequenceBuffer.writeByte((byte) (longValue >> 8));
            sequenceBuffer.writeByte((byte) longValue);
            if (logger.isTraceEnabled()) {
                logger.trace("WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)", type, 6, longValue);
            }
        } else if (((longValue < 0) && ((longValue & 0xFF80000000000000L) == 0xFF80000000000000L))
                || ((longValue & 0x007FFFFFFFFFFFFFL) == longValue)) {
            writeLength(sequenceBuffer, 7);
            sequenceBuffer.writeByte((byte) (longValue >> 48));
            sequenceBuffer.writeByte((byte) (longValue >> 40));
            sequenceBuffer.writeByte((byte) (longValue >> 32));
            sequenceBuffer.writeByte((byte) (longValue >> 24));
            sequenceBuffer.writeByte((byte) (longValue >> 16));
            sequenceBuffer.writeByte((byte) (longValue >> 8));
            sequenceBuffer.writeByte((byte) longValue);
            if (logger.isTraceEnabled()) {
                logger.trace("WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)", type, 7, longValue);
            }
        } else {
            writeLength(sequenceBuffer, 8);
            sequenceBuffer.writeByte((byte) (longValue >> 56));
            sequenceBuffer.writeByte((byte) (longValue >> 48));
            sequenceBuffer.writeByte((byte) (longValue >> 40));
            sequenceBuffer.writeByte((byte) (longValue >> 32));
            sequenceBuffer.writeByte((byte) (longValue >> 24));
            sequenceBuffer.writeByte((byte) (longValue >> 16));
            sequenceBuffer.writeByte((byte) (longValue >> 8));
            sequenceBuffer.writeByte((byte) longValue);
            if (logger.isTraceEnabled()) {
                logger.trace("WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)", type, 8, longValue);
            }
        }
        return this;
    }

    @Override
    public ASN1Writer writeNull(final byte type) throws IOException {
        sequenceBuffer.writeByte(type);
        writeLength(sequenceBuffer, 0);

        if (logger.isTraceEnabled()) {
            logger.trace("WRITE ASN.1 NULL(type=0x%x, length=%d)", type, 0);
        }
        return this;
    }

    @Override
    public ASN1Writer writeOctetString(final byte type, final byte[] value, final int offset,
            final int length) throws IOException {
        sequenceBuffer.writeByte(type);
        writeLength(sequenceBuffer, length);
        sequenceBuffer.writeByteArray(value, offset, length);

        if (logger.isTraceEnabled()) {
            logger.trace("WRITE ASN.1 OCTETSTRING(type=0x%x, length=%d)", type, length);
        }
        return this;
    }

    @Override
    public ASN1Writer writeOctetString(final byte type, final ByteSequence value)
            throws IOException {
        sequenceBuffer.writeByte(type);
        writeLength(sequenceBuffer, value.length());
        sequenceBuffer.writeByteSequence(value);

        if (logger.isTraceEnabled()) {
            logger.trace("WRITE ASN.1 OCTETSTRING(type=0x%x, length=%d)", type, value.length());
        }
        return this;
    }

    @Override
    public ASN1Writer writeOctetString(final byte type, final String value) throws IOException {
        sequenceBuffer.writeByte(type);

        if (value == null) {
            writeLength(sequenceBuffer, 0);
            return this;
        }

        final byte[] bytes = StaticUtils.getBytes(value);
        writeLength(sequenceBuffer, bytes.length);
        sequenceBuffer.writeByteArray(bytes, 0, bytes.length);

        if (logger.isTraceEnabled()) {
            logger.trace("WRITE ASN.1 OCTETSTRING(type=0x%x, length=%d, value=%s)", type, bytes.length, value);
        }
        return this;
    }

    @Override
    public ASN1Writer writeStartSequence(final byte type) throws IOException {
        // Get a child sequence buffer
        sequenceBuffer = sequenceBuffer.startSequence(type);

        if (logger.isTraceEnabled()) {
            logger.trace("WRITE ASN.1 START SEQUENCE(type=0x%x)", type);
        }
        return this;
    }

    @Override
    public ASN1Writer writeStartSet(final byte type) throws IOException {
        // From an implementation point of view, a set is equivalent to a
        // sequence.
        return writeStartSequence(type);
    }

    public Buffer getBuffer() {
        outBuffer.allowBufferDispose(true);
        return outBuffer.flip();
    }

    /**
     * Writes the provided value for use as the length of an ASN.1 element.
     *
     * @param buffer
     *            The sequence buffer to write to.
     * @param length
     *            The length to encode for use in an ASN.1 element.
     * @throws IOException
     *             if an error occurs while writing.
     */
    private void writeLength(final SequenceBuffer buffer, final int length) throws IOException {
        if (length < 128) {
            buffer.writeByte((byte) length);
        } else if ((length & 0x000000FF) == length) {
            buffer.writeByte((byte) 0x81);
            buffer.writeByte((byte) length);
        } else if ((length & 0x0000FFFF) == length) {
            buffer.writeByte((byte) 0x82);
            buffer.writeByte((byte) (length >> 8));
            buffer.writeByte((byte) length);
        } else if ((length & 0x00FFFFFF) == length) {
            buffer.writeByte((byte) 0x83);
            buffer.writeByte((byte) (length >> 16));
            buffer.writeByte((byte) (length >> 8));
            buffer.writeByte((byte) length);
        } else {
            buffer.writeByte((byte) 0x84);
            buffer.writeByte((byte) (length >> 24));
            buffer.writeByte((byte) (length >> 16));
            buffer.writeByte((byte) (length >> 8));
            buffer.writeByte((byte) length);
        }
    }
}
