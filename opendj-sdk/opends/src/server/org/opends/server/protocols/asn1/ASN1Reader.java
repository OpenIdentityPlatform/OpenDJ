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



import java.io.Closeable;
import java.io.IOException;

import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringBuilder;


/**
 * An interface for decoding ASN.1 elements from a data source.
 * <p>
 * Methods for creating {@link ASN1Reader}s are provided in the
 * {@link ASN1} class.
 */
public interface ASN1Reader extends Closeable
{

  /**
   * Closes this ASN.1 reader.
   *
   * @throws IOException if an I/O error occurs
   */
  void close() throws IOException;



  /**
   * Determines if a complete ASN.1 element is waiting to be read.
   *
   * @return <code>true</code> if another complete element is available or
   *         <code>false</code> otherwise.
   * @throws ASN1Exception If an error occurs while trying to decode
   *                       an ASN1 element.
   */
  public boolean elementAvailable() throws ASN1Exception;



  /**
   * Determines if at least one ASN.1 element is waiting to be read.
   *
   * @return <code>true</code> if another element is available or
   *         <code>false</code> if the EOF is reached.
   * @throws ASN1Exception
   *           If an error occurs while trying to decode an ASN1
   *           element.
   */
  boolean hasNextElement() throws ASN1Exception;



  /**
   * Gets the data length of the next element without actually reading
   * the element and advancing the cursor.
   *
   * @return The data length of the next element or -1 if the EOF is
   *         encountered.
   * @throws ASN1Exception
   *           If an error occurs while determining the length.
   */
  int peekLength() throws ASN1Exception;



  /**
   * Gets the BER type of the next element without actually reading
   * the element and advancing the cursor.
   *
   * @return The BER type of the next element or -1 if the EOF is
   *         encountered.
   * @throws ASN1Exception
   *           If an error occurs while determining the BER type.
   */
  byte peekType() throws ASN1Exception;



  /**
   * Reads the next ASN.1 element as a boolean and advance the cursor.
   *
   * @return The decoded boolean value.
   * @throws ASN1Exception
   *           If the element cannot be decoded as a boolean.
   */
  boolean readBoolean() throws ASN1Exception;



  /**
   * Finishes reading a sequence. Any elements not read in the
   * sequence will be discarded.
   *
   * @throws ASN1Exception
   *           If an error occurs while advancing to the end of the
   *           sequence.
   */
  void readEndSequence() throws ASN1Exception;



  /**
   * Finishes reading a set. Any elements not read in the set will be
   * discarded.
   *
   * @throws ASN1Exception
   *           If an error occurs while advancing to the end of the set.
   */
  void readEndSet() throws ASN1Exception;



  /**
   * Reads the next ASN.1 element as an enumerated value and advances
   * the cursor.
   *
   * @return The decoded enumerated value.
   * @throws ASN1Exception
   *           If the element cannot be decoded as an enumerated value.
   */
  int readEnumerated() throws ASN1Exception;



  /**
   * Reads the next ASN.1 element as an integer and advances the
   * cursor.
   *
   * @return The decoded integer value.
   * @throws ASN1Exception
   *           If the element cannot be decoded as a integer.
   */
  long readInteger() throws ASN1Exception;



  /**
   * Reads the next ASN.1 element as a null element and advances the
   * cursor.
   *
   * @throws ASN1Exception
   *           If the element cannot be decoded as an null element.
   */
  void readNull() throws ASN1Exception;



  /**
   * Reads the next ASN.1 element as an octet string and advances the
   * cursor.
   *
   * @return The decoded octet string value represented using a
   *         {@link ByteString}.
   * @throws ASN1Exception
   *           If the element cannot be decoded as an octet string.
   */
  ByteString readOctetString() throws ASN1Exception;



  /**
   * Reads the next ASN.1 element as an octet string and advances the
   * cursor. The data will be appended to the provided
   * {@link ByteStringBuilder}.
   *
   * @param buffer
   *          The {@link ByteStringBuilder} to append the data to.
   * @throws ASN1Exception
   *           If the element cannot be decoded as an octet string.
   */
  void readOctetString(ByteStringBuilder buffer) throws ASN1Exception;



  /**
   * Reads the next ASN.1 element as an octet string and advances the
   * cursor. The data will be decoded to a UTF-8 string. This method
   * is equivalent to:
   *
   * <pre>
   * readOctetStringAsString(&quot;UTF-8&quot;);
   * </pre>
   *
   * @return The string representation of the octet string data.
   * @throws ASN1Exception
   *           If the element cannot be decoded as an octet string.
   */
  String readOctetStringAsString() throws ASN1Exception;



  /**
   * Reads the next ASN.1 element as an octet string and advances the
   * cursor. The data will be decoded to a string using the provided
   * character set.
   *
   * @param charSet
   *          The character set to use in order to decode the data
   *          into a string.
   * @return The string representation of the octet string data.
   * @throws ASN1Exception
   *           If the element cannot be decoded as an octet string.
   */
  String readOctetStringAsString(String charSet) throws ASN1Exception;



  /**
   * Reads the next ASN.1 element as a sequence. All further reads
   * will read the elements in the sequence until
   * {@link #readEndSequence()} is called.
   *
   * @throws ASN1Exception
   *           If the next element is not a sequence.
   */
  void readStartSequence() throws ASN1Exception;



  /**
   * Reads the next ASN.1 element as a set. All further reads will read
   * the elements in the sequence until {@link #readEndSet()} is called.
   *
   * @throws ASN1Exception
   *           If the next element is not a set.
   */
  void readStartSet() throws ASN1Exception;



  /**
   * Advances this ASN.1 reader beyond the next ASN.1 element without
   * decoding it.
   *
   * @throws ASN1Exception
   *           If the next ASN.1 element could not be skipped.
   */
  void skipElement() throws ASN1Exception;
}
