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
 *      Portions copyright 2012-2013 ForgeRock AS.
 *      Portions Copyright 2014 Manuel Gaupp
 */

package org.forgerock.opendj.io;

import java.io.Closeable;
import java.io.IOException;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;

/**
 * An interface for decoding ASN.1 elements from a data source.
 * <p>
 * Methods for creating {@link ASN1Reader}s are provided in the {@link ASN1}
 * class.
 */
public interface ASN1Reader extends Closeable {

    /**
     * Closes this ASN.1 reader.
     *
     * @throws IOException
     *             If an error occurs while closing.
     */
    void close() throws IOException;

    /**
     * Indicates whether or not the next element can be read without blocking.
     *
     * @return {@code true} if a complete element is available or {@code false}
     *         otherwise.
     * @throws DecodeException
     *             If the available data was not valid ASN.1.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    boolean elementAvailable() throws DecodeException, IOException;

    /**
     * Indicates whether or not the current stream, sequence, or set contains
     * another element. Note that this method may return {@code true} even if a
     * previous call to {@link #elementAvailable} returned {@code false},
     * indicating that the current set or sequence contains another element but
     * an attempt to read that element may block. This method will block if
     * there is not enough data available to make the determination (typically
     * only the next element's type is required).
     *
     * @return {@code true} if the current stream, sequence, or set contains
     *         another element, or {@code false} if the end of the stream,
     *         sequence, or set has been reached.
     * @throws DecodeException
     *             If the available data was not valid ASN.1.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    boolean hasNextElement() throws DecodeException, IOException;

    /**
     * Returns the data length of the next element without actually reading it.
     *
     * @return The data length of the next element, or {@code -1} if the end of
     *         the stream, sequence, or set has been reached.
     * @throws DecodeException
     *             If the available data was not valid ASN.1.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    int peekLength() throws DecodeException, IOException;

    /**
     * Returns the type of the next element without actually reading it.
     *
     * @return The type of the next element, or {@code -1} if the end of the
     *         stream, sequence, or set has been reached.
     * @throws DecodeException
     *             If the available data was not valid ASN.1.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    byte peekType() throws DecodeException, IOException;

    /**
     * Reads the next element as a boolean having the Universal Boolean ASN.1
     * type tag.
     *
     * @return The decoded boolean value.
     * @throws DecodeException
     *             If the element cannot be decoded as a boolean.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    boolean readBoolean() throws DecodeException, IOException;

    /**
     * Reads the next element as a boolean having the provided type tag.
     *
     * @param type
     *            The expected type tag of the element.
     * @return The decoded boolean value.
     * @throws DecodeException
     *             If the element cannot be decoded as a boolean.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    boolean readBoolean(byte type) throws DecodeException, IOException;

    /**
     * Finishes reading a sequence and discards any unread elements.
     *
     * @throws DecodeException
     *             If an error occurs while advancing to the end of the
     *             sequence.
     * @throws IOException
     *             If an unexpected IO error occurred.
     * @throws IllegalStateException
     *             If there is no sequence being read.
     */
    void readEndSequence() throws DecodeException, IOException;

    /**
     * Finishes reading an explicit tag and discards any unread elements.
     *
     * @throws DecodeException
     *             If an error occurs while advancing to the end of the
     *             explicit tag.
     * @throws IOException
     *             If an unexpected IO error occurred.
     * @throws IllegalStateException
     *             If there is no explicit tag being read.
     */
    void readEndExplicitTag() throws DecodeException, IOException;

    /**
     * Finishes reading a set and discards any unread elements.
     *
     * @throws DecodeException
     *             If an error occurs while advancing to the end of the set.
     * @throws IOException
     *             If an unexpected IO error occurred.
     * @throws IllegalStateException
     *             If there is no set being read.
     */
    void readEndSet() throws DecodeException, IOException;

    /**
     * Reads the next element as an enumerated having the Universal Enumerated
     * ASN.1 type tag.
     *
     * @return The decoded enumerated value.
     * @throws DecodeException
     *             If the element cannot be decoded as an enumerated value.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    int readEnumerated() throws DecodeException, IOException;

    /**
     * Reads the next element as an enumerated having the provided type tag.
     *
     * @param type
     *            The expected type tag of the element.
     * @return The decoded enumerated value.
     * @throws DecodeException
     *             If the element cannot be decoded as an enumerated value.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    int readEnumerated(byte type) throws DecodeException, IOException;

    /**
     * Reads the next element as an integer having the Universal Integer ASN.1
     * type tag.
     *
     * @return The decoded integer value.
     * @throws DecodeException
     *             If the element cannot be decoded as an integer.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    long readInteger() throws DecodeException, IOException;

    /**
     * Reads the next element as an integer having the provided type tag.
     *
     * @param type
     *            The expected type tag of the element.
     * @return The decoded integer value.
     * @throws DecodeException
     *             If the element cannot be decoded as an integer.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    long readInteger(byte type) throws DecodeException, IOException;

    /**
     * Reads the next element as a null element having the Universal Null ASN.1
     * type tag.
     *
     * @throws DecodeException
     *             If the element cannot be decoded as a null element.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    void readNull() throws DecodeException, IOException;

    /**
     * Reads the next element as a null element having the provided type tag.
     *
     * @param type
     *            The expected type tag of the element.
     * @throws DecodeException
     *             If the element cannot be decoded as a null element.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    void readNull(byte type) throws DecodeException, IOException;

    /**
     * Reads the next element as an octet string having the Universal Octet
     * String ASN.1 type tag.
     *
     * @return The decoded octet string represented using a {@link ByteString}.
     * @throws DecodeException
     *             If the element cannot be decoded as an octet string.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    ByteString readOctetString() throws DecodeException, IOException;

    /**
     * Reads the next element as an octet string having the provided type tag.
     *
     * @param type
     *            The expected type tag of the element.
     * @return The decoded octet string represented using a {@link ByteString}.
     * @throws DecodeException
     *             If the element cannot be decoded as an octet string.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    ByteString readOctetString(byte type) throws DecodeException, IOException;

    /**
     * Reads the next element as an octet string having the provided type tag
     * and appends it to the provided {@link ByteStringBuilder}.
     *
     * @param type
     *            The expected type tag of the element.
     * @param builder
     *            The {@link ByteStringBuilder} to append the octet string to.
     * @return A reference to {@code builder}.
     * @throws DecodeException
     *             If the element cannot be decoded as an octet string.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    ByteStringBuilder readOctetString(byte type, ByteStringBuilder builder) throws DecodeException,
            IOException;

    /**
     * Reads the next element as an octet string having the Universal Octet
     * String ASN.1 type tag and appends it to the provided
     * {@link ByteStringBuilder}.
     *
     * @param builder
     *            The {@link ByteStringBuilder} to append the octet string to.
     * @return A reference to {@code builder}.
     * @throws DecodeException
     *             If the element cannot be decoded as an octet string.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    ByteStringBuilder readOctetString(ByteStringBuilder builder) throws DecodeException,
            IOException;

    /**
     * Reads the next element as an octet string having the Universal Octet
     * String ASN.1 type tag and decodes the value as a UTF-8 encoded string.
     *
     * @return The decoded octet string as a UTF-8 encoded string.
     * @throws DecodeException
     *             If the element cannot be decoded as an octet string.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    String readOctetStringAsString() throws DecodeException, IOException;

    /**
     * Reads the next element as an octet string having the provided type tag
     * and decodes the value as a UTF-8 encoded string.
     *
     * @param type
     *            The expected type tag of the element.
     * @return The decoded octet string as a UTF-8 encoded string.
     * @throws DecodeException
     *             If the element cannot be decoded as an octet string.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    String readOctetStringAsString(byte type) throws DecodeException, IOException;

    /**
     * Reads the next element as a sequence having the Universal Sequence ASN.1
     * type tag. All further reads will read the elements in the sequence until
     * {@link #readEndSequence()} is called.
     *
     * @throws DecodeException
     *             If the element cannot be decoded as a sequence.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    void readStartSequence() throws DecodeException, IOException;

    /**
     * Reads the next element as an explicit ignoring the ASN.1 type tag. All
     * further reads will read the elements in the explicit tag until
     * {@link #readEndExplicitTag()} is called.
     *
     * @throws DecodeException
     *             If the element cannot be decoded as an explicit tag.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    void readStartExplicitTag() throws DecodeException, IOException;

    /**
     * Reads the next element as an explicit tag having the provided tag type.
     * All further reads will read the elements in the explicit tag until
     * {@link #readEndExplicitTag()} is called.
     *
     * @param type
     *            The expected type tag of the element.
     * @throws DecodeException
     *             If the element cannot be decoded as an explicit tag.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    void readStartExplicitTag(byte type) throws DecodeException, IOException;

    /**
     * Reads the next element as a sequence having the provided type tag. All
     * further reads will read the elements in the sequence until
     * {@link #readEndSequence()} is called.
     *
     * @param type
     *            The expected type tag of the element.
     * @throws DecodeException
     *             If the element cannot be decoded as a sequence.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    void readStartSequence(byte type) throws DecodeException, IOException;

    /**
     * Reads the next element as a set having the Universal Set ASN.1 type tag.
     * All further reads will read the elements in the set until
     * {@link #readEndSet()} is called.
     *
     * @throws DecodeException
     *             If the element cannot be decoded as a set.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    void readStartSet() throws DecodeException, IOException;

    /**
     * Reads the next element as a set having the provided type tag. All further
     * reads will read the elements in the set until {@link #readEndSet()} is
     * called.
     *
     * @param type
     *            The expected type tag of the element.
     * @throws DecodeException
     *             If the element cannot be decoded as a set.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    void readStartSet(byte type) throws DecodeException, IOException;

    /**
     * Skips the next element without decoding it.
     *
     * @return A reference to this ASN.1 reader.
     * @throws DecodeException
     *             If the next element could not be skipped.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    ASN1Reader skipElement() throws DecodeException, IOException;

    /**
     * Skips the next element having the provided type tag without decoding it.
     *
     * @param type
     *            The expected type tag of the element.
     * @return A reference to this ASN.1 reader.
     * @throws DecodeException
     *             If the next element does not have the provided type tag.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    ASN1Reader skipElement(byte type) throws DecodeException, IOException;
}
