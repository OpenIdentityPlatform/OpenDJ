/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.asn1;



import org.opends.server.types.ByteSequence;

import java.io.Closeable;
import java.io.IOException;
import java.io.Flushable;


/**
 * An interface for encoding ASN.1 elements to a data source.
 * <p>
 * Methods for creating {@link ASN1Writer}s are provided in the
 * {@link ASN1} class.
 */
public interface ASN1Writer extends Closeable, Flushable
{

  /**
   * Closes this ASN.1 writer, flushing it first. Closing a
   * previously closed ASN.1 writer has no effect. Any unfinished
   * sequences and/or sets will be ended.
   *
   * @throws IOException
   *           If an error occurs while closing the writer.
   */
  public void close() throws IOException;

  /**
   * Flushes the writer. If the writer has saved any elements from the
   * previous write methods in a buffer, write them immediately to their
   * intended destination. Then, if that destination is another byte stream,
   * flush it. Thus one flush() invocation will flush all the buffers in a
   * chain of streams.
   * <p/>
   * If the intended destination of this stream is an abstraction provided
   * by the underlying operating system, for example a file, then flushing
   * the stream guarantees only that bytes previously written to the stream
   * are passed to the operating system for writing; it does not guarantee
   * that they are actually written to a physical device such as a disk drive.
   *
   * @throws IOException If an I/O error occurs
   */
  public void flush() throws IOException;



  /**
   * Writes a boolean element using the Universal Boolean ASN.1 type
   * tag.
   *
   * @param booleanValue
   *          The boolean value to write.
   * @return a reference to this object.
   * @throws IOException
   *           If an error occurs while writing.
   */
  ASN1Writer writeBoolean(boolean booleanValue) throws IOException;



  /**
   * Writes a boolean element using the provided type tag.
   *
   * @param type
   *          The type tag to use.
   * @param booleanValue
   *          The boolean value to write.
   * @return a reference to this object.
   * @throws IOException
   *           If an error occurs while writing.
   */
  ASN1Writer writeBoolean(byte type, boolean booleanValue) throws IOException;



  /**
   * Finish writing a sequence.
   *
   * @return a reference to this object.
   * @throws IOException
   *           If an error occurs while writing.
   */
  ASN1Writer writeEndSequence() throws IOException;



  /**
   * Finish writing a set.
   *
   * @return a reference to this object.
   * @throws IOException
   *           If an error occurs while writing.
   */
  ASN1Writer writeEndSet() throws IOException;



  /**
   * Writes an integer element using the provided type tag.
   *
   * @param type
   *          The type tag to use.
   * @param intValue
   *          The integer value to write.
   * @return a reference to this object.
   * @throws IOException
   *           If an error occurs while writing.
   */
  ASN1Writer writeInteger(byte type, int intValue) throws IOException;



  /**
   * Writes an integer element using the provided type tag.
   *
   * @param type
   *          The type tag to use.
   * @param longValue
   *          The integer value to write.
   * @return a reference to this object.
   * @throws IOException
   *           If an error occurs while writing.
   */
  ASN1Writer writeInteger(byte type, long longValue) throws IOException;



  /**
   * Writes an integer element using the Universal Integer ASN.1 type
   * tag.
   *
   * @param intValue
   *          The integer value to write.
   * @return a reference to this object.
   * @throws IOException
   *           If an error occurs while writing.
   */
  ASN1Writer writeInteger(int intValue) throws IOException;



  /**
   * Writes an integer element using the Universal Integer ASN.1 type
   * tag.
   *
   * @param longValue
   *          The integer value to write.
   * @return a reference to this object.
   * @throws IOException
   *           If an error occurs while writing.
   */
  ASN1Writer writeInteger(long longValue) throws IOException;



  /**
   * Writes an enumerated element using the Universal Enumerated ASN.1 type
   * tag.
   *
   * @param intValue
   *          The integer value of the enumeration to write.
   * @return a reference to this object.
   * @throws IOException
   *           If an error occurs while writing.
   */
  ASN1Writer writeEnumerated(int intValue) throws IOException;


  /**
   * Writes a null element using the Universal Null ASN.1 type tag.
   *
   * @return a reference to this object.
   * @throws IOException
   *           If an error occurs while writing.
   */
  ASN1Writer writeNull() throws IOException;



  /**
   * Writes a null element using the provided type tag.
   *
   * @param type
   *          The type tag to use.
   * @return a reference to this object.
   * @throws IOException
   *           If an error occurs while writing.
   */
  ASN1Writer writeNull(byte type) throws IOException;



  /**
   * Writes an octet string element using the provided type tag and
   * byte array.
   *
   * @param type
   *          The type tag to use.
   * @param value
   *          The byte array containing the data to write.
   * @param offset
   *          The offset in the byte array.
   * @param length
   *          The number of bytes to write.
   * @return a reference to this object.
   * @throws IOException
   *           If an error occurs while writing.
   */
  ASN1Writer writeOctetString(byte type, byte[] value, int offset, int length)
      throws IOException;



  /**
   * Writes an octet string element using the provided type tag and
   * byte sequence.
   *
   * @param type
   *          The type tag to use.
   * @param value
   *          The byte sequence containing the data to write.
   * @return a reference to this object.
   * @throws IOException
   *           If an error occurs while writing.
   */
  ASN1Writer writeOctetString(byte type, ByteSequence value) throws IOException;



  /**
   * Writes an octet string element using the provided type tag and
   * the UTF-8 encoded bytes of the provided string.
   *
   * @param type
   *          The type tag to use.
   * @param value
   *          The string to write.
   * @return a reference to this object.
   * @throws IOException
   *           If an error occurs while writing.
   */
  ASN1Writer writeOctetString(byte type, String value) throws IOException;



  /**
   * Writes an octet string element using the Universal Octet String
   * ASN.1 type tag and the provided byte array.
   *
   * @param value
   *          The byte array containing the data to write.
   * @param offset
   *          The offset in the byte array.
   * @param length
   *          The number of bytes to write.
   * @return a reference to this object.
   * @throws IOException
   *           If an error occurs while writing.
   */
  ASN1Writer writeOctetString(byte[] value, int offset, int length)
      throws IOException;



  /**
   * Writes an octet string element using the Universal Octet String
   * ASN.1 type tag and the provided byte sequence.
   *
   * @param value
   *          The byte sequence containing the data to write.
   * @return a reference to this object.
   * @throws IOException
   *           If an error occurs while writing.
   */
  ASN1Writer writeOctetString(ByteSequence value) throws IOException;



  /**
   * Writes an octet string element using the Universal Octet String
   * ASN.1 type tag and the UTF-8 encoded bytes of the provided
   * string.
   *
   * @param value
   *          The string to write.
   * @return a reference to this object.
   * @throws IOException
   *           If an error occurs while writing.
   */
  ASN1Writer writeOctetString(String value) throws IOException;



  /**
   * Writes a sequence element using the Universal Sequence ASN.1 type
   * tag. All further writes will be part of this set until
   * {@link #writeEndSequence()} is called.
   *
   * @return a reference to this object.
   * @throws IOException
   *           If an error occurs while writing.
   */
  ASN1Writer writeStartSequence() throws IOException;



  /**
   * Writes a sequence element using the provided type tag. All
   * further writes will be part of this set until
   * {@link #writeEndSequence()} is called.
   *
   * @param type
   *          The type tag to use.
   * @return a reference to this object.
   * @throws IOException
   *           If an error occurs while writing.
   */
  ASN1Writer writeStartSequence(byte type) throws IOException;



  /**
   * Writes a set element using the Universal Set type tag. All
   * further writes will be part of this set until
   * {@link #writeEndSet()} is called.
   *
   * @return a reference to this object.
   * @throws IOException
   *           If an error occurs while writing.
   */
  ASN1Writer writeStartSet() throws IOException;
}
