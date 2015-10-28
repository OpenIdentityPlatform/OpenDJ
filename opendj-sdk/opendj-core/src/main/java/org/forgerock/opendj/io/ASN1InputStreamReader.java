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
 *      Portions copyright 2012-2015 ForgeRock AS.
 */
package org.forgerock.opendj.io;

import static com.forgerock.opendj.ldap.CoreMessages.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.LinkedList;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;

import com.forgerock.opendj.util.SizeLimitInputStream;

/**
 * An ASN1Reader that reads from an input stream.
 */
final class ASN1InputStreamReader extends AbstractASN1Reader {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    private int state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;
    private byte peekType;
    private int peekLength = -1;
    private int lengthBytesNeeded;
    private final int maxElementSize;
    private InputStream in;
    private final LinkedList<InputStream> streamStack = new LinkedList<>();
    private byte[] buffer = new byte[512];

    /**
     * Creates a new ASN1 reader whose source is the provided input stream and
     * having a user defined maximum BER element size.
     *
     * @param stream
     *            The input stream to be read.
     * @param maxElementSize
     *            The maximum BER element size, or <code>0</code> to indicate
     *            that there is no limit.
     */
    ASN1InputStreamReader(final InputStream stream, final int maxElementSize) {
        this.in = stream;
        this.maxElementSize = maxElementSize;
    }

    /** {@inheritDoc} */
    public void close() throws IOException {
        // Calling close of SizeLimitInputStream should close the parent stream.
        in.close();
        streamStack.clear();
    }

    /** {@inheritDoc} */
    public boolean elementAvailable() throws IOException {
        return (state != ASN1.ELEMENT_READ_STATE_NEED_TYPE || needTypeState(false, false))
            && (state != ASN1.ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE || needFirstLengthByteState(false, false))
            && (state != ASN1.ELEMENT_READ_STATE_NEED_ADDITIONAL_LENGTH_BYTES
                 || needAdditionalLengthBytesState(false, false))
            && peekLength <= in.available();
    }

    /** {@inheritDoc} */
    public boolean hasNextElement() throws IOException {
        if (!streamStack.isEmpty()) {
            // We are reading a sub sequence. Return true as long as we
            // haven't exhausted the size limit for the sub sequence sub input
            // stream.
            final SizeLimitInputStream subSq = (SizeLimitInputStream) in;
            return subSq.getSizeLimit() - subSq.getBytesRead() > 0;
        }

        return state != ASN1.ELEMENT_READ_STATE_NEED_TYPE || needTypeState(true, false);
    }

    /** {@inheritDoc} */
    public int peekLength() throws IOException {
        peekType();
        switch (state) {
        case ASN1.ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE:
            needFirstLengthByteState(true, true);
            break;
        case ASN1.ELEMENT_READ_STATE_NEED_ADDITIONAL_LENGTH_BYTES:
            needAdditionalLengthBytesState(true, true);
            break;
        default: // ASN1.ELEMENT_READ_STATE_NEED_VALUE_BYTES
            break;
        }
        return peekLength;
    }

    /** {@inheritDoc} */
    public byte peekType() throws IOException {
        if (state == ASN1.ELEMENT_READ_STATE_NEED_TYPE) {
            needTypeState(true, true);
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

        final int readByte = in.read();
        if (readByte == -1) {
            final LocalizableMessage message = ERR_ASN1_BOOLEAN_TRUNCATED_VALUE.get(peekLength);
            throw DecodeException.fatalError(message);
        }

        logger.trace("READ ASN.1 BOOLEAN(type=0x%x, length=%d, value=%s)", peekType, peekLength, readByte != 0x00);

        state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;
        return readByte != 0x00;
    }

    /** {@inheritDoc} */
    public void readEndSequence() throws IOException {
        if (streamStack.isEmpty()) {
            final LocalizableMessage message = ERR_ASN1_SEQUENCE_READ_NOT_STARTED.get();
            throw new IllegalStateException(message.toString());
        }

        // Ignore all unused trailing components.
        final SizeLimitInputStream subSq = (SizeLimitInputStream) in;
        if (subSq.getSizeLimit() - subSq.getBytesRead() > 0) {
            logger.trace("Ignoring %d unused trailing bytes in ASN.1 SEQUENCE",
                    subSq.getSizeLimit() - subSq.getBytesRead());
            subSq.skip(subSq.getSizeLimit() - subSq.getBytesRead());
        }

        logger.trace("READ ASN.1 END SEQUENCE");

        in = streamStack.removeFirst();

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

        if (peekLength > 4) {
            long longValue = 0;
            for (int i = 0; i < peekLength; i++) {
                final int readByte = in.read();
                if (readByte == -1) {
                    final LocalizableMessage message =
                            ERR_ASN1_INTEGER_TRUNCATED_VALUE.get(peekLength);
                    throw DecodeException.fatalError(message);
                }
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
                final int readByte = in.read();
                if (readByte == -1) {
                    final LocalizableMessage message =
                            ERR_ASN1_INTEGER_TRUNCATED_VALUE.get(peekLength);
                    throw DecodeException.fatalError(message);
                }
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

        // Copy the value and construct the element to return.
        final byte[] value = new byte[peekLength];
        int bytesNeeded = peekLength;
        int bytesRead;
        while (bytesNeeded > 0) {
            bytesRead = in.read(value, peekLength - bytesNeeded, bytesNeeded);
            if (bytesRead < 0) {
                final LocalizableMessage message =
                        ERR_ASN1_OCTET_STRING_TRUNCATED_VALUE.get(peekLength);
                throw DecodeException.fatalError(message);
            }

            bytesNeeded -= bytesRead;
        }

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

        // Copy the value and construct the element to return.
        int bytesNeeded = peekLength;
        int bytesRead;
        while (bytesNeeded > 0) {
            bytesRead = builder.appendBytes(in, bytesNeeded);
            if (bytesRead < 0) {
                final LocalizableMessage message =
                        ERR_ASN1_OCTET_STRING_TRUNCATED_VALUE.get(peekLength);
                throw DecodeException.fatalError(message);
            }
            bytesNeeded -= bytesRead;
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

        // Resize the temp buffer if needed
        if (peekLength > buffer.length) {
            buffer = new byte[peekLength];
        }

        int bytesNeeded = peekLength;
        int bytesRead;
        while (bytesNeeded > 0) {
            bytesRead = in.read(buffer, peekLength - bytesNeeded, bytesNeeded);
            if (bytesRead < 0) {
                final LocalizableMessage message =
                        ERR_ASN1_OCTET_STRING_TRUNCATED_VALUE.get(peekLength);
                throw DecodeException.fatalError(message);
            }
            bytesNeeded -= bytesRead;
        }

        state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;

        String str;
        try {
            str = new String(buffer, 0, peekLength, "UTF-8");
        } catch (final UnsupportedEncodingException e) {
            // TODO: I18N
            throw new RuntimeException("Unable to decode ASN.1 OCTETSTRING bytes as UTF-8 string ", e);
        }

        logger.trace("READ ASN.1 OCTETSTRING(type=0x%x, length=%d, value=%s)", peekType, peekLength, str);

        return str;
    }

    /** {@inheritDoc} */
    public void readStartSequence() throws IOException {
        // Read the header if haven't done so already
        peekLength();

        final SizeLimitInputStream subStream = new SizeLimitInputStream(in, peekLength);

        logger.trace("READ ASN.1 START SEQUENCE(type=0x%x, length=%d)", peekType, peekLength);

        streamStack.addFirst(in);
        in = subStream;

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

        final long bytesSkipped = in.skip(peekLength);
        if (bytesSkipped != peekLength) {
            final LocalizableMessage message = ERR_ASN1_SKIP_TRUNCATED_VALUE.get(peekLength);
            throw DecodeException.fatalError(message);
        }
        state = ASN1.ELEMENT_READ_STATE_NEED_TYPE;
        return this;
    }

    /**
     * Internal helper method reading the additional ASN.1 length bytes and
     * transition to the next state if successful.
     *
     * @param isBlocking
     *            <code>true</code> to block if the type byte is not available
     *            or <code>false</code> to check for availability first.
     * @param throwEofException
     *            <code>true</code> to throw an exception when an EOF is
     *            encountered or <code>false</code> to return false.
     * @return <code>true</code> if the length bytes was successfully read.
     * @throws IOException
     *             If an error occurs while reading from the stream.
     */
    private boolean needAdditionalLengthBytesState(final boolean isBlocking,
            final boolean throwEofException) throws IOException {
        if (!isBlocking && (in.available() < lengthBytesNeeded)) {
            return false;
        }

        int readByte;
        while (lengthBytesNeeded > 0) {
            readByte = in.read();
            if (readByte == -1) {
                state = ASN1.ELEMENT_READ_STATE_NEED_ADDITIONAL_LENGTH_BYTES;
                if (throwEofException) {
                    final LocalizableMessage message =
                            ERR_ASN1_TRUNCATED_LENGTH_BYTES.get(lengthBytesNeeded);
                    throw DecodeException.fatalError(message);
                }
                return false;
            }
            peekLength = (peekLength << 8) | (readByte & 0xFF);
            lengthBytesNeeded--;
        }

        // Make sure that the element is not larger than the maximum allowed
        // message size.
        if (maxElementSize > 0 && peekLength > maxElementSize) {
            final LocalizableMessage message =
                    ERR_LDAP_CLIENT_DECODE_MAX_REQUEST_SIZE_EXCEEDED
                            .get(peekLength, maxElementSize);
            throw DecodeException.fatalError(message);
        }
        state = ASN1.ELEMENT_READ_STATE_NEED_VALUE_BYTES;
        return true;
    }

    /**
     * Internal helper method reading the first length bytes and transition to
     * the next state if successful.
     *
     * @param isBlocking
     *            <code>true</code> to block if the type byte is not available
     *            or <code>false</code> to check for availability first.
     * @param throwEofException
     *            <code>true</code> to throw an exception when an EOF is
     *            encountered or <code>false</code> to return false.
     * @return <code>true</code> if the length bytes was successfully read
     * @throws IOException
     *             If an error occurs while reading from the stream.
     */
    private boolean needFirstLengthByteState(final boolean isBlocking,
            final boolean throwEofException) throws IOException {
        if (!isBlocking && in.available() <= 0) {
            return false;
        }

        int readByte = in.read();
        if (readByte == -1) {
            if (throwEofException) {
                final LocalizableMessage message = ERR_ASN1_TRUNCATED_LENGTH_BYTE.get();
                throw DecodeException.fatalError(message);
            }
            return false;
        }
        peekLength = readByte & 0x7F;
        if (peekLength != readByte) {
            lengthBytesNeeded = peekLength;
            if (lengthBytesNeeded > 4) {
                final LocalizableMessage message =
                        ERR_ASN1_INVALID_NUM_LENGTH_BYTES.get(lengthBytesNeeded);
                throw DecodeException.fatalError(message);
            }
            peekLength = 0x00;

            if (!isBlocking && (in.available() < lengthBytesNeeded)) {
                state = ASN1.ELEMENT_READ_STATE_NEED_ADDITIONAL_LENGTH_BYTES;
                return false;
            }

            while (lengthBytesNeeded > 0) {
                readByte = in.read();
                if (readByte == -1) {
                    state = ASN1.ELEMENT_READ_STATE_NEED_ADDITIONAL_LENGTH_BYTES;
                    if (throwEofException) {
                        final LocalizableMessage message =
                                ERR_ASN1_TRUNCATED_LENGTH_BYTES.get(lengthBytesNeeded);
                        throw DecodeException.fatalError(message);
                    }
                    return false;
                }
                peekLength = (peekLength << 8) | (readByte & 0xFF);
                lengthBytesNeeded--;
            }
        }

        // Make sure that the element is not larger than the maximum allowed
        // message size.
        if (maxElementSize > 0 && peekLength > maxElementSize) {
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
     * @param isBlocking
     *            <code>true</code> to block if the type byte is not available
     *            or <code>false</code> to check for availability first.
     * @param throwEofException
     *            <code>true</code> to throw an exception when an EOF is
     *            encountered or <code>false</code> to return false.
     * @return <code>true</code> if the type byte was successfully read
     * @throws IOException
     *             If an error occurs while reading from the stream.
     */
    private boolean needTypeState(final boolean isBlocking, final boolean throwEofException)
            throws IOException {
        // Read just the type.
        if (!isBlocking && in.available() <= 0) {
            return false;
        }

        final int type = in.read();
        if (type == -1) {
            if (throwEofException) {
                final LocalizableMessage message = ERR_ASN1_TRUCATED_TYPE_BYTE.get();
                throw DecodeException.fatalError(message);
            }
            return false;
        }

        peekType = (byte) type;
        state = ASN1.ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE;
        return true;
    }
}
