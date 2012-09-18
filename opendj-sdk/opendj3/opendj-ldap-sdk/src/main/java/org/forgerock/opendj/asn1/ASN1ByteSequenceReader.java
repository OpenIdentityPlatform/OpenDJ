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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.asn1;

import static org.forgerock.opendj.ldap.CoreMessages.*;

import java.io.IOException;
import java.util.LinkedList;
import java.util.logging.Level;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;

import com.forgerock.opendj.util.StaticUtils;

/**
 * An ASN.1 reader that reads from a {@link ByteSequenceReader}.
 */
final class ASN1ByteSequenceReader extends AbstractASN1Reader implements ASN1Reader {

    private int state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;

    private byte peekType = 0;

    private int peekLength = -1;

    private final int maxElementSize;

    private ByteSequenceReader reader;

    private final LinkedList<ByteSequenceReader> readerStack;

    /**
     * Creates a new ASN1 reader whose source is the provided byte sequence
     * reader and having a user defined maximum BER element size.
     *
     * @param reader
     *            The byte sequence reader to be read.
     * @param maxElementSize
     *            The maximum BER element size, or <code>0</code> to indicate
     *            that there is no limit.
     */
    ASN1ByteSequenceReader(final ByteSequenceReader reader, final int maxElementSize) {
        this.reader = reader;
        this.readerStack = new LinkedList<ByteSequenceReader>();
        this.maxElementSize = maxElementSize;
    }

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        readerStack.clear();
    }

    /**
     * {@inheritDoc}
     */
    public boolean elementAvailable() throws IOException {
        if ((state == ASN1.ELEMENT_READ_STATE_NEED_TYPE) && !needTypeState(false)) {
            return false;
        }
        if ((state == ASN1.ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE)
                && !needFirstLengthByteState(false)) {
            return false;
        }

        return peekLength <= reader.remaining();
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasNextElement() throws IOException {
        return (state != ASN1.ELEMENT_READ_STATE_NEED_TYPE) || needTypeState(false);
    }

    /**
     * {@inheritDoc}
     */
    public int peekLength() throws IOException {
        peekType();

        if (state == ASN1.ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE) {
            needFirstLengthByteState(true);
        }

        return peekLength;
    }

    /**
     * {@inheritDoc}
     */
    public byte peekType() throws IOException {
        if (state == ASN1.ELEMENT_READ_STATE_NEED_TYPE) {
            // Read just the type.
            if (reader.remaining() <= 0) {
                final LocalizableMessage message = ERR_ASN1_TRUCATED_TYPE_BYTE.get();
                throw DecodeException.fatalError(message);
            }
            final int type = reader.get();

            peekType = (byte) type;
            state = ASN1.ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE;
        }

        return peekType;
    }

    /**
     * {@inheritDoc}
     */
    public boolean readBoolean() throws IOException {
        // Read the header if haven't done so already
        peekLength();

        if (peekLength != 1) {
            final LocalizableMessage message = ERR_ASN1_BOOLEAN_INVALID_LENGTH.get(peekLength);
            throw DecodeException.fatalError(message);
        }

        if (reader.remaining() < peekLength) {
            final LocalizableMessage message = ERR_ASN1_BOOLEAN_TRUNCATED_VALUE.get(peekLength);
            throw DecodeException.fatalError(message);
        }
        final int readByte = reader.get();

        state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;
        return readByte != 0x00;
    }

    /**
     * {@inheritDoc}
     */
    public void readEndSequence() throws IOException {
        if (readerStack.isEmpty()) {
            final LocalizableMessage message = ERR_ASN1_SEQUENCE_READ_NOT_STARTED.get();
            throw new IllegalStateException(message.toString());
        }

        if ((reader.remaining() > 0) && StaticUtils.DEBUG_LOG.isLoggable(Level.FINE)) {
            StaticUtils.DEBUG_LOG.fine("Ignoring " + reader.remaining()
                    + " unused trailing bytes in " + "ASN.1 SEQUENCE");
        }

        reader = readerStack.removeFirst();

        // Reset the state
        state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;
    }

    /**
     * {@inheritDoc}
     */
    public void readEndSet() throws IOException {
        // From an implementation point of view, a set is equivalent to a
        // sequence.
        readEndSequence();
    }

    /**
     * {@inheritDoc}
     */
    public int readEnumerated() throws IOException {
        // Read the header if haven't done so already
        peekLength();

        if ((peekLength < 1) || (peekLength > 4)) {
            final LocalizableMessage message = ERR_ASN1_INTEGER_INVALID_LENGTH.get(peekLength);
            throw DecodeException.fatalError(message);
        }

        // From an implementation point of view, an enumerated value is
        // equivalent to an integer.
        return (int) readInteger();
    }

    /**
     * {@inheritDoc}
     */
    public long readInteger() throws IOException {
        // Read the header if haven't done so already
        peekLength();

        if ((peekLength < 1) || (peekLength > 8)) {
            final LocalizableMessage message = ERR_ASN1_INTEGER_INVALID_LENGTH.get(peekLength);
            throw DecodeException.fatalError(message);
        }

        if (reader.remaining() < peekLength) {
            final LocalizableMessage message = ERR_ASN1_INTEGER_TRUNCATED_VALUE.get(peekLength);
            throw DecodeException.fatalError(message);
        }
        if (peekLength > 4) {
            long longValue = 0;
            for (int i = 0; i < peekLength; i++) {
                final int readByte = reader.get();
                if ((i == 0) && (readByte < 0)) {
                    longValue = 0xFFFFFFFFFFFFFFFFL;
                }
                longValue = (longValue << 8) | (readByte & 0xFF);
            }

            state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;
            return longValue;
        } else {
            int intValue = 0;
            for (int i = 0; i < peekLength; i++) {
                final int readByte = reader.get();
                if ((i == 0) && (readByte < 0)) {
                    intValue = 0xFFFFFFFF;
                }
                intValue = (intValue << 8) | (readByte & 0xFF);
            }

            state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;
            return intValue;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void readNull() throws IOException {
        // Read the header if haven't done so already
        peekLength();

        // Make sure that the decoded length is exactly zero byte.
        if (peekLength != 0) {
            final LocalizableMessage message = ERR_ASN1_NULL_INVALID_LENGTH.get(peekLength);
            throw DecodeException.fatalError(message);
        }

        state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;
    }

    /**
     * {@inheritDoc}
     */
    public ByteString readOctetString() throws IOException {
        // Read the header if haven't done so already
        peekLength();

        if (reader.remaining() < peekLength) {
            final LocalizableMessage message =
                    ERR_ASN1_OCTET_STRING_TRUNCATED_VALUE.get(peekLength);
            throw DecodeException.fatalError(message);
        }

        state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;
        return reader.getByteString(peekLength);
    }

    /**
     * {@inheritDoc}
     */
    public ByteStringBuilder readOctetString(final ByteStringBuilder builder) throws IOException {
        // Read the header if haven't done so already
        peekLength();

        // Copy the value.
        if (reader.remaining() < peekLength) {
            final LocalizableMessage message =
                    ERR_ASN1_OCTET_STRING_TRUNCATED_VALUE.get(peekLength);
            throw DecodeException.fatalError(message);
        }
        builder.append(reader, peekLength);

        state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;
        return builder;
    }

    /**
     * {@inheritDoc}
     */
    public String readOctetStringAsString() throws IOException {
        // Read the header if haven't done so already
        peekLength();

        if (reader.remaining() < peekLength) {
            final LocalizableMessage message =
                    ERR_ASN1_OCTET_STRING_TRUNCATED_VALUE.get(peekLength);
            throw DecodeException.fatalError(message);
        }

        state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;
        return reader.getString(peekLength);
    }

    /**
     * {@inheritDoc}
     */
    public void readStartSequence() throws IOException {
        // Read the header if haven't done so already
        peekLength();

        if (reader.remaining() < peekLength) {
            final LocalizableMessage message =
                    ERR_ASN1_SEQUENCE_SET_TRUNCATED_VALUE.get(peekLength);
            throw DecodeException.fatalError(message);
        }

        final ByteSequenceReader subByteString = reader.getByteSequence(peekLength).asReader();
        readerStack.addFirst(reader);
        reader = subByteString;

        // Reset the state
        state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;
    }

    /**
     * {@inheritDoc}
     */
    public void readStartSet() throws IOException {
        // From an implementation point of view, a set is equivalent to a
        // sequence.
        readStartSequence();
    }

    /**
     * {@inheritDoc}
     */
    public ASN1Reader skipElement() throws IOException {
        // Read the header if haven't done so already
        peekLength();

        if (reader.remaining() < peekLength) {
            final LocalizableMessage message = ERR_ASN1_SKIP_TRUNCATED_VALUE.get(peekLength);
            throw DecodeException.fatalError(message);
        }

        state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;
        reader.skip(peekLength);
        return this;
    }

    /**
     * Internal helper method reading the first length bytes and transition to
     * the next state if successful.
     *
     * @param throwEofException
     *            <code>true</code> to throw an exception when the end of the
     *            sequence is encountered.
     * @return <code>true</code> if the length bytes was successfully read
     * @throws IOException
     *             If an error occurs while trying to decode an ASN1 element.
     */
    private boolean needFirstLengthByteState(final boolean throwEofException) throws IOException {
        if (reader.remaining() <= 0) {
            if (throwEofException) {
                final LocalizableMessage message = ERR_ASN1_TRUNCATED_LENGTH_BYTE.get();
                throw DecodeException.fatalError(message);
            }
            return false;
        }
        int readByte = reader.get();
        peekLength = (readByte & 0x7F);
        if (peekLength != readByte) {
            int lengthBytesNeeded = peekLength;
            if (lengthBytesNeeded > 4) {
                final LocalizableMessage message =
                        ERR_ASN1_INVALID_NUM_LENGTH_BYTES.get(lengthBytesNeeded);
                throw DecodeException.fatalError(message);
            }

            peekLength = 0x00;
            if (reader.remaining() < lengthBytesNeeded) {
                if (throwEofException) {
                    final LocalizableMessage message =
                            ERR_ASN1_TRUNCATED_LENGTH_BYTES.get(lengthBytesNeeded);
                    throw DecodeException.fatalError(message);
                }
                return false;
            }

            while (lengthBytesNeeded > 0) {
                readByte = reader.get();
                peekLength = (peekLength << 8) | (readByte & 0xFF);
                lengthBytesNeeded--;
            }
        }

        // Make sure that the element is not larger than the maximum allowed
        // message size.
        if ((maxElementSize > 0) && (peekLength > maxElementSize)) {
            final LocalizableMessage message =
                    ERR_LDAP_CLIENT_DECODE_MAX_REQUEST_SIZE_EXCEEDED
                            .get(peekLength, maxElementSize);
            throw DecodeException.fatalError(message);
        }
        state = ASN1.ELEMENT_READ_STATE_NEED_VALUE_BYTES;
        return true;
    }

    /**
     * Internal helper method reading the ASN.1 type byte and transition to the
     * next state if successful.
     *
     * @param throwEofException
     *            <code>true</code> to throw an exception when the end of the
     *            sequence is encountered.
     * @return <code>true</code> if the type byte was successfully read
     * @throws IOException
     *             If an error occurs while trying to decode an ASN1 element.
     */
    private boolean needTypeState(final boolean throwEofException) throws IOException {
        // Read just the type.
        if (reader.remaining() <= 0) {
            if (throwEofException) {
                final LocalizableMessage message = ERR_ASN1_TRUCATED_TYPE_BYTE.get();
                throw DecodeException.fatalError(message);
            }
            return false;
        }
        final int type = reader.get();

        peekType = (byte) type;
        state = ASN1.ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE;
        return true;
    }
}
