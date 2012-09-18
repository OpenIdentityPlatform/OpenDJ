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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions copyright 2012 ForgeRock AS.
 */
package org.forgerock.opendj.asn1;

import static org.forgerock.opendj.ldap.CoreMessages.ERR_ASN1_SEQUENCE_WRITE_NOT_STARTED;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.logging.Level;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteStringBuilder;

import com.forgerock.opendj.util.ByteSequenceOutputStream;
import com.forgerock.opendj.util.StaticUtils;

/**
 * An ASN1Writer implementation that outputs to an outputstream.
 */
final class ASN1OutputStreamWriter extends AbstractASN1Writer implements ASN1Writer {
    private final OutputStream rootStream;
    private OutputStream out;
    private final ArrayList<ByteSequenceOutputStream> streamStack;
    private int stackDepth;

    /**
     * Creates a new ASN.1 output stream reader.
     *
     * @param stream
     *            The underlying output stream.
     */
    ASN1OutputStreamWriter(final OutputStream stream) {
        this.out = stream;
        this.rootStream = stream;
        this.streamStack = new ArrayList<ByteSequenceOutputStream>();
        this.stackDepth = -1;
    }

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        while (stackDepth >= 0) {
            writeEndSequence();
        }
        rootStream.flush();

        streamStack.clear();
        rootStream.close();
    }

    /**
     * {@inheritDoc}
     */
    public void flush() throws IOException {
        rootStream.flush();
    }

    /**
     * {@inheritDoc}
     */
    public ASN1Writer writeBoolean(final byte type, final boolean booleanValue) throws IOException {
        out.write(type);
        writeLength(1);
        out.write(booleanValue ? ASN1.BOOLEAN_VALUE_TRUE : ASN1.BOOLEAN_VALUE_FALSE);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST)) {
            StaticUtils.DEBUG_LOG.finest(String.format(
                    "WRITE ASN.1 BOOLEAN(type=0x%x, length=%d, value=%s)", type, 1, String
                            .valueOf(booleanValue)));
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ASN1Writer writeEndSequence() throws IOException {
        if (stackDepth < 0) {
            final LocalizableMessage message = ERR_ASN1_SEQUENCE_WRITE_NOT_STARTED.get();
            throw new IllegalStateException(message.toString());
        }

        final ByteSequenceOutputStream childStream = streamStack.get(stackDepth);

        // Decrement the stack depth and get the parent stream
        --stackDepth;

        final OutputStream parentStream = stackDepth < 0 ? rootStream : streamStack.get(stackDepth);

        // Switch to parent stream and reset the sub-stream
        out = parentStream;

        // Write the length and contents of the sub-stream
        writeLength(childStream.length());
        childStream.writeTo(parentStream);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST)) {
            StaticUtils.DEBUG_LOG.finest(String.format("WRITE ASN.1 END SEQUENCE(length=%d)",
                    childStream.length()));
        }

        childStream.reset();
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ASN1Writer writeEndSet() throws IOException {
        return writeEndSequence();
    }

    /**
     * {@inheritDoc}
     */
    public ASN1Writer writeEnumerated(final byte type, final int intValue) throws IOException {
        return writeInteger(type, intValue);
    }

    /**
     * {@inheritDoc}
     */
    public ASN1Writer writeInteger(final byte type, final int intValue) throws IOException {
        out.write(type);
        if (((intValue < 0) && ((intValue & 0xFFFFFF80) == 0xFFFFFF80))
                || ((intValue & 0x0000007F) == intValue)) {
            writeLength(1);
            out.write((byte) (intValue & 0xFF));
            if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST)) {
                StaticUtils.DEBUG_LOG.finest(String.format(
                        "WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)", type, 1, intValue));
            }
        } else if (((intValue < 0) && ((intValue & 0xFFFF8000) == 0xFFFF8000))
                || ((intValue & 0x00007FFF) == intValue)) {
            writeLength(2);
            out.write((byte) ((intValue >> 8) & 0xFF));
            out.write((byte) (intValue & 0xFF));
            if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST)) {
                StaticUtils.DEBUG_LOG.finest(String.format(
                        "WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)", type, 2, intValue));
            }
        } else if (((intValue < 0) && ((intValue & 0xFF800000) == 0xFF800000))
                || ((intValue & 0x007FFFFF) == intValue)) {
            writeLength(3);
            out.write((byte) ((intValue >> 16) & 0xFF));
            out.write((byte) ((intValue >> 8) & 0xFF));
            out.write((byte) (intValue & 0xFF));
            if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST)) {
                StaticUtils.DEBUG_LOG.finest(String.format(
                        "WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)", type, 3, intValue));
            }
        } else {
            writeLength(4);
            out.write((byte) ((intValue >> 24) & 0xFF));
            out.write((byte) ((intValue >> 16) & 0xFF));
            out.write((byte) ((intValue >> 8) & 0xFF));
            out.write((byte) (intValue & 0xFF));
            if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST)) {
                StaticUtils.DEBUG_LOG.finest(String.format(
                        "WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)", type, 4, intValue));
            }
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ASN1Writer writeInteger(final byte type, final long longValue) throws IOException {
        out.write(type);
        if (((longValue < 0) && ((longValue & 0xFFFFFFFFFFFFFF80L) == 0xFFFFFFFFFFFFFF80L))
                || ((longValue & 0x000000000000007FL) == longValue)) {
            writeLength(1);
            out.write((byte) (longValue & 0xFF));
            if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST)) {
                StaticUtils.DEBUG_LOG.finest(String.format(
                        "WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)", type, 1, longValue));
            }
        } else if (((longValue < 0) && ((longValue & 0xFFFFFFFFFFFF8000L) == 0xFFFFFFFFFFFF8000L))
                || ((longValue & 0x0000000000007FFFL) == longValue)) {
            writeLength(2);
            out.write((byte) ((longValue >> 8) & 0xFF));
            out.write((byte) (longValue & 0xFF));
            if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST)) {
                StaticUtils.DEBUG_LOG.finest(String.format(
                        "WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)", type, 2, longValue));
            }
        } else if (((longValue < 0) && ((longValue & 0xFFFFFFFFFF800000L) == 0xFFFFFFFFFF800000L))
                || ((longValue & 0x00000000007FFFFFL) == longValue)) {
            writeLength(3);
            out.write((byte) ((longValue >> 16) & 0xFF));
            out.write((byte) ((longValue >> 8) & 0xFF));
            out.write((byte) (longValue & 0xFF));
            if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST)) {
                StaticUtils.DEBUG_LOG.finest(String.format(
                        "WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)", type, 3, longValue));
            }
        } else if (((longValue < 0) && ((longValue & 0xFFFFFFFF80000000L) == 0xFFFFFFFF80000000L))
                || ((longValue & 0x000000007FFFFFFFL) == longValue)) {
            writeLength(4);
            out.write((byte) ((longValue >> 24) & 0xFF));
            out.write((byte) ((longValue >> 16) & 0xFF));
            out.write((byte) ((longValue >> 8) & 0xFF));
            out.write((byte) (longValue & 0xFF));
            if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST)) {
                StaticUtils.DEBUG_LOG.finest(String.format(
                        "WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)", type, 4, longValue));
            }
        } else if (((longValue < 0) && ((longValue & 0xFFFFFF8000000000L) == 0xFFFFFF8000000000L))
                || ((longValue & 0x0000007FFFFFFFFFL) == longValue)) {
            writeLength(5);
            out.write((byte) ((longValue >> 32) & 0xFF));
            out.write((byte) ((longValue >> 24) & 0xFF));
            out.write((byte) ((longValue >> 16) & 0xFF));
            out.write((byte) ((longValue >> 8) & 0xFF));
            out.write((byte) (longValue & 0xFF));
            if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST)) {
                StaticUtils.DEBUG_LOG.finest(String.format(
                        "WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)", type, 5, longValue));
            }
        } else if (((longValue < 0) && ((longValue & 0xFFFF800000000000L) == 0xFFFF800000000000L))
                || ((longValue & 0x00007FFFFFFFFFFFL) == longValue)) {
            writeLength(6);
            out.write((byte) ((longValue >> 40) & 0xFF));
            out.write((byte) ((longValue >> 32) & 0xFF));
            out.write((byte) ((longValue >> 24) & 0xFF));
            out.write((byte) ((longValue >> 16) & 0xFF));
            out.write((byte) ((longValue >> 8) & 0xFF));
            out.write((byte) (longValue & 0xFF));
            if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST)) {
                StaticUtils.DEBUG_LOG.finest(String.format(
                        "WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)", type, 6, longValue));
            }
        } else if (((longValue < 0) && ((longValue & 0xFF80000000000000L) == 0xFF80000000000000L))
                || ((longValue & 0x007FFFFFFFFFFFFFL) == longValue)) {
            writeLength(7);
            out.write((byte) ((longValue >> 48) & 0xFF));
            out.write((byte) ((longValue >> 40) & 0xFF));
            out.write((byte) ((longValue >> 32) & 0xFF));
            out.write((byte) ((longValue >> 24) & 0xFF));
            out.write((byte) ((longValue >> 16) & 0xFF));
            out.write((byte) ((longValue >> 8) & 0xFF));
            out.write((byte) (longValue & 0xFF));
            if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST)) {
                StaticUtils.DEBUG_LOG.finest(String.format(
                        "WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)", type, 7, longValue));
            }
        } else {
            writeLength(8);
            out.write((byte) ((longValue >> 56) & 0xFF));
            out.write((byte) ((longValue >> 48) & 0xFF));
            out.write((byte) ((longValue >> 40) & 0xFF));
            out.write((byte) ((longValue >> 32) & 0xFF));
            out.write((byte) ((longValue >> 24) & 0xFF));
            out.write((byte) ((longValue >> 16) & 0xFF));
            out.write((byte) ((longValue >> 8) & 0xFF));
            out.write((byte) (longValue & 0xFF));
            if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST)) {
                StaticUtils.DEBUG_LOG.finest(String.format(
                        "WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)", type, 8, longValue));
            }
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ASN1Writer writeNull(final byte type) throws IOException {
        out.write(type);
        writeLength(0);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST)) {
            StaticUtils.DEBUG_LOG.finest(String.format("WRITE ASN.1 NULL(type=0x%x, length=%d)",
                    type, 0));
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ASN1Writer writeOctetString(final byte type, final byte[] value, final int offset,
            final int length) throws IOException {
        out.write(type);
        writeLength(length);
        out.write(value, offset, length);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST)) {
            StaticUtils.DEBUG_LOG.finest(String.format(
                    "WRITE ASN.1 OCTETSTRING(type=0x%x, length=%d)", type, length));
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ASN1Writer writeOctetString(final byte type, final ByteSequence value)
            throws IOException {
        out.write(type);
        writeLength(value.length());
        value.copyTo(out);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST)) {
            StaticUtils.DEBUG_LOG.finest(String.format(
                    "WRITE ASN.1 OCTETSTRING(type=0x%x, length=%d)", type, value.length()));
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ASN1Writer writeOctetString(final byte type, final String value) throws IOException {
        out.write(type);

        if (value == null) {
            writeLength(0);
            return this;
        }

        final byte[] bytes = StaticUtils.getBytes(value);
        writeLength(bytes.length);
        out.write(bytes);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST)) {
            StaticUtils.DEBUG_LOG.finest(String.format(
                    "WRITE ASN.1 OCTETSTRING(type=0x%x, length=%d, " + "value=%s)", type,
                    bytes.length, value));
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ASN1Writer writeStartSequence(final byte type) throws IOException {
        // Write the type in current stream switch to next sub-stream
        out.write(type);

        // Increment the stack depth and get the sub-stream from the stack
        ++stackDepth;

        // Make sure we have a cached sub-stream at this depth
        if (stackDepth >= streamStack.size()) {
            final ByteSequenceOutputStream subStream =
                    new ByteSequenceOutputStream(new ByteStringBuilder());
            streamStack.add(subStream);
            out = subStream;
        } else {
            out = streamStack.get(stackDepth);
        }

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST)) {
            StaticUtils.DEBUG_LOG.finest(String.format("WRITE ASN.1 START SEQUENCE(type=0x%x)",
                    type));
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ASN1Writer writeStartSet(final byte type) throws IOException {
        // From an implementation point of view, a set is equivalent to a
        // sequence.
        return writeStartSequence(type);
    }

    /**
     * Writes the provided value for use as the length of an ASN.1 element.
     *
     * @param length
     *            The length to encode for use in an ASN.1 element.
     * @throws IOException
     *             if an error occurs while writing.
     */
    private void writeLength(final int length) throws IOException {
        if (length < 128) {
            out.write((byte) length);
        } else if ((length & 0x000000FF) == length) {
            out.write((byte) 0x81);
            out.write((byte) (length & 0xFF));
        } else if ((length & 0x0000FFFF) == length) {
            out.write((byte) 0x82);
            out.write((byte) ((length >> 8) & 0xFF));
            out.write((byte) (length & 0xFF));
        } else if ((length & 0x00FFFFFF) == length) {
            out.write((byte) 0x83);
            out.write((byte) ((length >> 16) & 0xFF));
            out.write((byte) ((length >> 8) & 0xFF));
            out.write((byte) (length & 0xFF));
        } else {
            out.write((byte) 0x84);
            out.write((byte) ((length >> 24) & 0xFF));
            out.write((byte) ((length >> 16) & 0xFF));
            out.write((byte) ((length >> 8) & 0xFF));
            out.write((byte) (length & 0xFF));
        }
    }
}
