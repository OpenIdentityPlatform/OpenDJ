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
 *      Portions copyright 2011-2013 ForgeRock AS
 */
package org.forgerock.opendj.io;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;

import org.forgerock.opendj.ldap.ByteSequence;

/**
 * An interface for encoding ASN.1 elements to a data source.
 * <p>
 * Methods for creating {@link ASN1Writer}s are provided in the {@link ASN1}
 * class.
 */
public interface ASN1Writer extends Closeable, Flushable {

    /**
     * Closes this ASN.1 writer, flushing it first. Closing a previously closed
     * ASN.1 writer has no effect. Any unfinished sequences and/or sets will be
     * ended.
     *
     * @throws IOException
     *             If an error occurs while closing.
     */
    void close() throws IOException;

    /**
     * Flushes this ASN.1 writer so that any buffered elements are written
     * immediately to their intended destination. Then, if that destination is
     * another byte stream, flush it. Thus one {@code flush()} invocation will
     * flush all the buffers in a chain of streams.
     * <p>
     * If the intended destination of this stream is an abstraction provided by
     * the underlying operating system, for example a file, then flushing the
     * stream guarantees only that bytes previously written to the stream are
     * passed to the operating system for writing; it does not guarantee that
     * they are actually written to a physical device such as a disk drive.
     *
     * @throws IOException
     *             If an error occurs while flushing.
     */
    void flush() throws IOException;

    /**
     * Writes a boolean element using the Universal Boolean ASN.1 type tag.
     *
     * @param value
     *            The boolean value.
     * @return A reference to this ASN.1 writer.
     * @throws IOException
     *             If an error occurs while writing the element.
     */
    ASN1Writer writeBoolean(boolean value) throws IOException;

    /**
     * Writes a boolean element using the provided type tag.
     *
     * @param type
     *            The type tag of the element.
     * @param value
     *            The boolean value.
     * @return A reference to this ASN.1 writer.
     * @throws IOException
     *             If an error occurs while writing the element.
     */
    ASN1Writer writeBoolean(byte type, boolean value) throws IOException;

    /**
     * Finishes writing a sequence element.
     *
     * @return A reference to this ASN.1 writer.
     * @throws IOException
     *             If an error occurs while writing the element.
     * @throws IllegalStateException
     *             If there is no sequence being written.
     */
    ASN1Writer writeEndSequence() throws IOException;

    /**
     * Finishes writing a set element.
     *
     * @return A reference to this ASN.1 writer.
     * @throws IOException
     *             If an error occurs while writing the element.
     * @throws IllegalStateException
     *             If there is no set being written.
     */
    ASN1Writer writeEndSet() throws IOException;

    /**
     * Writes an enumerated element using the provided type tag.
     *
     * @param type
     *            The type tag of the element.
     * @param value
     *            The enumerated value.
     * @return A reference to this ASN.1 writer.
     * @throws IOException
     *             If an error occurs while writing the element.
     */
    ASN1Writer writeEnumerated(byte type, int value) throws IOException;

    /**
     * Writes an enumerated element using the Universal Enumerated ASN.1 type
     * tag.
     *
     * @param value
     *            The enumerated value.
     * @return A reference to this ASN.1 writer.
     * @throws IOException
     *             If an error occurs while writing the element.
     */
    ASN1Writer writeEnumerated(int value) throws IOException;

    /**
     * Writes an integer element using the provided type tag.
     *
     * @param type
     *            The type tag of the element.
     * @param value
     *            The integer value.
     * @return A reference to this ASN.1 writer.
     * @throws IOException
     *             If an error occurs while writing the element.
     */
    ASN1Writer writeInteger(byte type, int value) throws IOException;

    /**
     * Writes an integer element using the provided type tag.
     *
     * @param type
     *            The type tag of the element.
     * @param value
     *            The integer value.
     * @return A reference to this ASN.1 writer.
     * @throws IOException
     *             If an error occurs while writing the element.
     */
    ASN1Writer writeInteger(byte type, long value) throws IOException;

    /**
     * Writes an integer element using the Universal Integer ASN.1 type tag.
     *
     * @param value
     *            The integer value.
     * @return A reference to this ASN.1 writer.
     * @throws IOException
     *             If an error occurs while writing the element.
     */
    ASN1Writer writeInteger(int value) throws IOException;

    /**
     * Writes an integer element using the Universal Integer ASN.1 type tag.
     *
     * @param value
     *            The integer value.
     * @return A reference to this ASN.1 writer.
     * @throws IOException
     *             If an error occurs while writing the element.
     */
    ASN1Writer writeInteger(long value) throws IOException;

    /**
     * Writes a null element using the Universal Null ASN.1 type tag.
     *
     * @return A reference to this ASN.1 writer.
     * @throws IOException
     *             If an error occurs while writing the element.
     */
    ASN1Writer writeNull() throws IOException;

    /**
     * Writes a null element using the provided type tag.
     *
     * @param type
     *            The type tag of the element.
     * @return A reference to this ASN.1 writer.
     * @throws IOException
     *             If an error occurs while writing the element.
     */
    ASN1Writer writeNull(byte type) throws IOException;

    /**
     * Writes an octet string element using the provided type tag.
     *
     * @param type
     *            The type tag of the element.
     * @param value
     *            The byte array containing the octet string data.
     * @return A reference to this ASN.1 writer.
     * @throws IOException
     *             If an error occurs while writing the element.
     */
    ASN1Writer writeOctetString(byte type, byte[] value) throws IOException;

    /**
     * Writes an octet string element using the provided type tag.
     *
     * @param type
     *            The type tag of the element.
     * @param value
     *            The byte array containing the octet string data.
     * @param offset
     *            The offset in the byte array.
     * @param length
     *            The number of bytes to write.
     * @return A reference to this ASN.1 writer.
     * @throws IOException
     *             If an error occurs while writing the element.
     */
    ASN1Writer writeOctetString(byte type, byte[] value, int offset, int length) throws IOException;

    /**
     * Writes an octet string element using the provided type tag.
     *
     * @param type
     *            The type tag of the element.
     * @param value
     *            The octet string value.
     * @return A reference to this ASN.1 writer.
     * @throws IOException
     *             If an error occurs while writing the element.
     */
    ASN1Writer writeOctetString(byte type, ByteSequence value) throws IOException;

    /**
     * Writes a string as a UTF-8 encoded octet string element using the
     * provided type tag.
     *
     * @param type
     *            The type tag of the element.
     * @param value
     *            The string to be written as a UTF-8 encoded octet string.
     * @return A reference to this ASN.1 writer.
     * @throws IOException
     *             If an error occurs while writing the element.
     */
    ASN1Writer writeOctetString(byte type, String value) throws IOException;

    /**
     * Writes an octet string element using the Universal Octet String ASN.1
     * type tag.
     *
     * @param value
     *            The byte array containing the octet string data.
     * @return A reference to this ASN.1 writer.
     * @throws IOException
     *             If an error occurs while writing the element.
     */
    ASN1Writer writeOctetString(byte[] value) throws IOException;

    /**
     * Writes an octet string element using the Universal Octet String ASN.1
     * type tag.
     *
     * @param value
     *            The byte array containing the octet string data.
     * @param offset
     *            The offset in the byte array.
     * @param length
     *            The number of bytes to write.
     * @return A reference to this ASN.1 writer.
     * @throws IOException
     *             If an error occurs while writing the element.
     */
    ASN1Writer writeOctetString(byte[] value, int offset, int length) throws IOException;

    /**
     * Writes an octet string element using the Universal Octet String ASN.1
     * type tag.
     *
     * @param value
     *            The octet string value.
     * @return A reference to this ASN.1 writer.
     * @throws IOException
     *             If an error occurs while writing the element.
     */
    ASN1Writer writeOctetString(ByteSequence value) throws IOException;

    /**
     * Writes a string as a UTF-8 encoded octet string element using the
     * Universal Octet String ASN.1 type tag.
     *
     * @param value
     *            The string to be written as a UTF-8 encoded octet string.
     * @return A reference to this ASN.1 writer.
     * @throws IOException
     *             If an error occurs while writing the element.
     */
    ASN1Writer writeOctetString(String value) throws IOException;

    /**
     * Writes a sequence element using the Universal Sequence ASN.1 type tag.
     * All further writes will append elements to the sequence until
     * {@link #writeEndSequence} is called.
     *
     * @return A reference to this ASN.1 writer.
     * @throws IOException
     *             If an error occurs while writing the element.
     */
    ASN1Writer writeStartSequence() throws IOException;

    /**
     * Writes a sequence element using the provided type tag. All further writes
     * will append elements to the sequence until {@link #writeEndSequence} is
     * called.
     *
     * @param type
     *            The type tag of the element.
     * @return A reference to this ASN.1 writer.
     * @throws IOException
     *             If an error occurs while writing the element.
     */
    ASN1Writer writeStartSequence(byte type) throws IOException;

    /**
     * Writes a set element using the Universal Set ASN.1 type tag. All further
     * writes will append elements to the set until {@link #writeEndSet} is
     * called.
     *
     * @return A reference to this ASN.1 writer.
     * @throws IOException
     *             If an error occurs while writing the element.
     */
    ASN1Writer writeStartSet() throws IOException;

    /**
     * Writes a set element using the provided type tag. All further writes will
     * append elements to the set until {@link #writeEndSet} is called.
     *
     * @param type
     *            The type tag of the element.
     * @return A reference to this ASN.1 writer.
     * @throws IOException
     *             If an error occurs while writing the element.
     */
    ASN1Writer writeStartSet(byte type) throws IOException;
}
