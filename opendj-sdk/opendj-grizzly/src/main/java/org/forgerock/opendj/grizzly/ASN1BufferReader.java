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
 *      Portions copyright 2012-2015 ForgeRock AS.
 */
package org.forgerock.opendj.grizzly;

import static com.forgerock.opendj.ldap.CoreMessages.*;

import java.io.IOException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.AbstractASN1Reader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.memory.BuffersBuffer;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.MemoryManager;

/** Grizzly ASN1 reader implementation. */
final class ASN1BufferReader extends AbstractASN1Reader {
    private final class ChildSequenceLimiter implements SequenceLimiter {
        private SequenceLimiter parent;
        private ChildSequenceLimiter child;
        private int readLimit;
        private int bytesRead;

        public void checkLimit(final int readSize) throws IOException {
            if (0 < readLimit && readLimit < bytesRead + readSize) {
                final LocalizableMessage message = ERR_ASN1_TRUNCATED_LENGTH_BYTE.get();
                throw DecodeException.fatalError(message);
            }
            parent.checkLimit(readSize);
            bytesRead += readSize;
        }

        public SequenceLimiter endSequence() throws IOException {
            parent.checkLimit(remaining());
            if (remaining() > 0) {
                logger.debug(LocalizableMessage.raw(
                    "Ignoring %d unused trailing bytes in ASN.1 SEQUENCE", remaining()));
            }
            for (int i = 0; i < remaining(); i++) {
                buffer.get();
            }
            return parent;
        }

        public int remaining() {
            return readLimit - bytesRead;
        }

        public ChildSequenceLimiter startSequence(final int readLimit) {
            if (child == null) {
                child = new ChildSequenceLimiter();
                child.parent = this;
            }
            child.readLimit = readLimit;
            child.bytesRead = 0;
            return child;
        }
    }

    private final class RootSequenceLimiter implements SequenceLimiter {
        private ChildSequenceLimiter child;

        public void checkLimit(final int readSize) throws IOException {
            if (buffer.remaining() < readSize) {
                final LocalizableMessage message = ERR_ASN1_TRUNCATED_LENGTH_BYTE.get();
                throw DecodeException.fatalError(message);
            }
        }

        public ChildSequenceLimiter endSequence() throws DecodeException {
            final LocalizableMessage message = ERR_ASN1_SEQUENCE_READ_NOT_STARTED.get();
            throw new IllegalStateException(message.toString());
        }

        public int remaining() {
            return buffer.remaining();
        }

        public ChildSequenceLimiter startSequence(final int readLimit) {
            if (child == null) {
                child = new ChildSequenceLimiter();
                child.parent = this;
            }
            child.readLimit = readLimit;
            child.bytesRead = 0;
            return child;
        }
    }

    private interface SequenceLimiter {
        void checkLimit(int readSize) throws IOException;

        SequenceLimiter endSequence() throws IOException;

        int remaining();

        SequenceLimiter startSequence(int readLimit);
    }

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    private static final int MAX_STRING_BUFFER_SIZE = 1024;
    private int state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;
    private byte peekType;
    private int peekLength = -1;
    private int lengthBytesNeeded;
    private final int maxElementSize;
    private final CompositeBuffer buffer;
    private SequenceLimiter readLimiter;
    private final byte[] stringBuffer;

    /**
     * Creates a new ASN1 reader whose source is the provided input stream and
     * having a user defined maximum BER element size.
     *
     * @param maxElementSize
     *            The maximum BER element size, or <code>0</code> to indicate
     *            that there is no limit.
     * @param memoryManager
     *            The memory manager to use for buffering.
     */
    ASN1BufferReader(final int maxElementSize, final MemoryManager<?> memoryManager) {
        this.readLimiter = new RootSequenceLimiter();
        this.stringBuffer = new byte[MAX_STRING_BUFFER_SIZE];
        this.maxElementSize = maxElementSize;
        this.buffer = BuffersBuffer.create(memoryManager);
    }

    /**
     * Closes this ASN.1 reader and the underlying stream.
     *
     * @throws IOException
     *             if an I/O error occurs
     */
    public void close() throws IOException {
        buffer.dispose();
    }

    /**
     * Determines if a complete ASN.1 element is ready to be read from the
     * stream reader.
     *
     * @return <code>true</code> if another complete element is available or
     *         <code>false</code> otherwise.
     * @throws IOException
     *             If an error occurs while trying to decode an ASN1 element.
     */
    public boolean elementAvailable() throws IOException {
        return (state != ASN1.ELEMENT_READ_STATE_NEED_TYPE || needTypeState(true))
                && (state != ASN1.ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE || needFirstLengthByteState(true))
                && (state != ASN1.ELEMENT_READ_STATE_NEED_ADDITIONAL_LENGTH_BYTES
                    || needAdditionalLengthBytesState(true))
                && peekLength <= readLimiter.remaining();
    }

    /**
     * Determines if the input stream contains at least one ASN.1 element to be
     * read.
     *
     * @return <code>true</code> if another element is available or
     *         <code>false</code> otherwise.
     * @throws IOException
     *             If an error occurs while trying to decode an ASN1 element.
     */
    public boolean hasNextElement() throws IOException {
        return state != ASN1.ELEMENT_READ_STATE_NEED_TYPE || needTypeState(true);
    }

    /** {@inheritDoc} */
    public int peekLength() throws IOException {
        peekType();

        switch (state) {
        case ASN1.ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE:
            needFirstLengthByteState(false);
            break;

        case ASN1.ELEMENT_READ_STATE_NEED_ADDITIONAL_LENGTH_BYTES:
            needAdditionalLengthBytesState(false);
        }

        return peekLength;
    }

    /** {@inheritDoc} */
    public byte peekType() throws IOException {
        if (state == ASN1.ELEMENT_READ_STATE_NEED_TYPE) {
            needTypeState(false);
        }

        return peekType;
    }

    /** {@inheritDoc} */
    public boolean readBoolean() throws IOException {
        // Read the header if haven't done so already
        peekLength();

        if (peekLength != 1) {
            final LocalizableMessage message = ERR_ASN1_BOOLEAN_INVALID_LENGTH.get(peekLength);
            throw DecodeException.fatalError(message);
        }

        readLimiter.checkLimit(peekLength);
        final byte readByte = buffer.get();

        logger.trace("READ ASN.1 BOOLEAN(type=0x%x, length=%d, value=%s)", peekType, peekLength, readByte != 0x00);

        state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;
        return readByte != 0x00;
    }

    /** {@inheritDoc} */
    public void readEndSequence() throws IOException {
        readLimiter = readLimiter.endSequence();

        logger.trace("READ ASN.1 END SEQUENCE");

        // Reset the state
        state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;
    }

    /** {@inheritDoc} */
    @Override
    public void readEndExplicitTag() throws DecodeException, IOException {
        readEndSequence();
    }

    /** {@inheritDoc} */
    public void readEndSet() throws IOException {
        // From an implementation point of view, a set is equivalent to a
        // sequence.
        readEndSequence();
    }

    /** {@inheritDoc} */
    public int readEnumerated() throws IOException {
        // Read the header if haven't done so already
        peekLength();

        if (peekLength < 1 || peekLength > 4) {
            final LocalizableMessage message = ERR_ASN1_INTEGER_INVALID_LENGTH.get(peekLength);
            throw DecodeException.fatalError(message);
        }

        // From an implementation point of view, an enumerated value is
        // equivalent to an integer.
        return (int) readInteger();
    }

    /** {@inheritDoc} */
    public long readInteger() throws IOException {
        // Read the header if haven't done so already
        peekLength();

        if (peekLength < 1 || peekLength > 8) {
            final LocalizableMessage message = ERR_ASN1_INTEGER_INVALID_LENGTH.get(peekLength);
            throw DecodeException.fatalError(message);
        }

        readLimiter.checkLimit(peekLength);
        if (peekLength > 4) {
            long longValue = 0;
            for (int i = 0; i < peekLength; i++) {
                final int readByte = buffer.get();
                if (i == 0 && ((byte) readByte) < 0) {
                    longValue = 0xFFFFFFFFFFFFFFFFL;
                }
                longValue = (longValue << 8) | (readByte & 0xFF);
            }

            state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;
            return longValue;
        } else {
            int intValue = 0;
            for (int i = 0; i < peekLength; i++) {
                final int readByte = buffer.get();
                if (i == 0 && ((byte) readByte) < 0) {
                    intValue = 0xFFFFFFFF;
                }
                intValue = (intValue << 8) | (readByte & 0xFF);
            }

            logger.trace("READ ASN.1 INTEGER(type=0x%x, length=%d, value=%d)", peekType, peekLength, intValue);

            state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;
            return intValue;
        }
    }

    /** {@inheritDoc} */
    public void readNull() throws IOException {
        // Read the header if haven't done so already
        peekLength();

        // Make sure that the decoded length is exactly zero byte.
        if (peekLength != 0) {
            final LocalizableMessage message = ERR_ASN1_NULL_INVALID_LENGTH.get(peekLength);
            throw DecodeException.fatalError(message);
        }

        logger.trace("READ ASN.1 NULL(type=0x%x, length=%d)", peekType, peekLength);

        state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;
    }

    /** {@inheritDoc} */
    public ByteString readOctetString() throws IOException {
        // Read the header if haven't done so already
        peekLength();

        if (peekLength == 0) {
            state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;
            return ByteString.empty();
        }

        readLimiter.checkLimit(peekLength);
        // Copy the value and construct the element to return.
        final byte[] value = new byte[peekLength];
        buffer.get(value);

        logger.trace("READ ASN.1 OCTETSTRING(type=0x%x, length=%d)", peekType, peekLength);

        state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;
        return ByteString.wrap(value);
    }

    /** {@inheritDoc} */
    public ByteStringBuilder readOctetString(final ByteStringBuilder builder) throws IOException {
        // Read the header if haven't done so already
        peekLength();

        if (peekLength == 0) {
            state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;
            return builder;
        }

        readLimiter.checkLimit(peekLength);
        // Copy the value and construct the element to return.
        // TODO: Is there a more efficient way to do this?
        for (int i = 0; i < peekLength; i++) {
            builder.appendByte(buffer.get());
        }

        logger.trace("READ ASN.1 OCTETSTRING(type=0x%x, length=%d)", peekType, peekLength);

        state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;
        return builder;
    }

    /** {@inheritDoc} */
    public String readOctetStringAsString() throws IOException {
        // Read the header if haven't done so already
        peekLength();

        if (peekLength == 0) {
            state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;
            return "";
        }

        byte[] readBuffer;
        if (peekLength <= stringBuffer.length) {
            readBuffer = stringBuffer;
        } else {
            readBuffer = new byte[peekLength];
        }

        readLimiter.checkLimit(peekLength);
        buffer.get(readBuffer, 0, peekLength);

        state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;

        String str;
        try {
            str = new String(readBuffer, 0, peekLength, "UTF-8");
        } catch (final Exception e) {
            // TODO: I18N
            logger.warn(LocalizableMessage.raw("Unable to decode ASN.1 OCTETSTRING bytes as UTF-8 string: %s", e));

            str = new String(stringBuffer, 0, peekLength);
        }

        logger.trace("READ ASN.1 OCTETSTRING(type=0x%x, length=%d, value=%s)", peekType, peekLength, str);

        return str;
    }

    /** {@inheritDoc} */
    public void readStartSequence() throws IOException {
        // Read the header if haven't done so already
        peekLength();

        readLimiter = readLimiter.startSequence(peekLength);

        logger.trace("READ ASN.1 START SEQUENCE(type=0x%x, length=%d)", peekType, peekLength);

        // Reset the state
        state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;
    }

    /** {@inheritDoc} */
    @Override
    public void readStartExplicitTag() throws DecodeException, IOException {
        readStartSequence();
    }

    /** {@inheritDoc} */
    public void readStartSet() throws IOException {
        // From an implementation point of view, a set is equivalent to a
        // sequence.
        readStartSequence();
    }

    /** {@inheritDoc} */
    public ASN1Reader skipElement() throws IOException {
        // Read the header if haven't done so already
        peekLength();

        readLimiter.checkLimit(peekLength);
        for (int i = 0; i < peekLength; i++) {
            buffer.get();
        }
        state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;
        return this;
    }

    void appendBytesRead(final Buffer buffer) {
        this.buffer.append(buffer);
    }

    void disposeBytesRead() {
        this.buffer.shrink();
    }

    /**
     * Internal helper method reading the additional ASN.1 length bytes and
     * transition to the next state if successful.
     *
     * @param ensureRead
     *            <code>true</code> to check for availability first.
     * @return <code>true</code> if the length bytes was successfully read.
     * @throws IOException
     *             If an error occurs while reading from the stream.
     */
    private boolean needAdditionalLengthBytesState(final boolean ensureRead) throws IOException {
        if (ensureRead && (readLimiter.remaining() < lengthBytesNeeded)) {
            return false;
        }

        byte readByte;
        readLimiter.checkLimit(lengthBytesNeeded);
        while (lengthBytesNeeded > 0) {
            readByte = buffer.get();
            peekLength = (peekLength << 8) | (readByte & 0xFF);
            lengthBytesNeeded--;
        }

        // Make sure that the element is not larger than the maximum allowed
        // message size.
        if (maxElementSize > 0 && peekLength > maxElementSize) {
            final LocalizableMessage m =
                    ERR_LDAP_CLIENT_DECODE_MAX_REQUEST_SIZE_EXCEEDED
                            .get(peekLength, maxElementSize);
            throw DecodeException.fatalError(m);
        }
        state = ASN1.ELEMENT_READ_STATE_NEED_VALUE_BYTES;
        return true;
    }

    /**
     * Internal helper method reading the first length bytes and transition to
     * the next state if successful.
     *
     * @param ensureRead
     *            <code>true</code> to check for availability first.
     * @return <code>true</code> if the length bytes was successfully read
     * @throws IOException
     *             If an error occurs while trying to decode an ASN1 element.
     */
    private boolean needFirstLengthByteState(final boolean ensureRead) throws IOException {
        if (ensureRead && (readLimiter.remaining() <= 0)) {
            return false;
        }

        readLimiter.checkLimit(1);
        byte readByte = buffer.get();
        peekLength = (readByte & 0x7F);
        if (peekLength != readByte) {
            lengthBytesNeeded = peekLength;
            if (lengthBytesNeeded > 4) {
                final LocalizableMessage message =
                        ERR_ASN1_INVALID_NUM_LENGTH_BYTES.get(lengthBytesNeeded);
                throw DecodeException.fatalError(message);
            }
            peekLength = 0x00;

            if (ensureRead && (readLimiter.remaining() < lengthBytesNeeded)) {
                state = ASN1.ELEMENT_READ_STATE_NEED_ADDITIONAL_LENGTH_BYTES;
                return false;
            }

            readLimiter.checkLimit(lengthBytesNeeded);
            while (lengthBytesNeeded > 0) {
                readByte = buffer.get();
                peekLength = (peekLength << 8) | (readByte & 0xFF);
                lengthBytesNeeded--;
            }
        }

        // Make sure that the element is not larger than the maximum allowed
        // message size.
        if (maxElementSize > 0 && peekLength > maxElementSize) {
            final LocalizableMessage m =
                    ERR_LDAP_CLIENT_DECODE_MAX_REQUEST_SIZE_EXCEEDED
                            .get(peekLength, maxElementSize);
            throw DecodeException.fatalError(m);
        }
        state = ASN1.ELEMENT_READ_STATE_NEED_VALUE_BYTES;
        return true;
    }

    /**
     * Internal helper method reading the ASN.1 type byte and transition to the
     * next state if successful.
     *
     * @param ensureRead
     *            <code>true</code> to check for availability first.
     * @return <code>true</code> if the type byte was successfully read
     * @throws IOException
     *             If an error occurs while trying to decode an ASN1 element.
     */
    private boolean needTypeState(final boolean ensureRead) throws IOException {
        // Read just the type.
        if (ensureRead && readLimiter.remaining() <= 0) {
            return false;
        }

        readLimiter.checkLimit(1);
        peekType = buffer.get();
        state = ASN1.ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE;
        return true;
    }
}
