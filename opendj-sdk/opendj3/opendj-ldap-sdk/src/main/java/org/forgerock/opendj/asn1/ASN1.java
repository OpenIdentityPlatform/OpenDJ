/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */

package org.forgerock.opendj.asn1;



import java.io.InputStream;
import java.io.OutputStream;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;

import com.forgerock.opendj.util.ByteSequenceOutputStream;



/**
 * This class contains various static factory methods for creating ASN.1 readers
 * and writers.
 *
 * @see ASN1Reader
 * @see ASN1Writer
 */
public final class ASN1
{

  /**
   * Returns an ASN.1 reader whose source is the provided byte array and having
   * an unlimited maximum BER element size.
   *
   * @param array
   *          The byte array to use.
   * @return The new ASN.1 reader.
   */
  public static ASN1Reader getReader(final byte[] array)
  {
    return getReader(array, 0);
  }



  /**
   * Returns an ASN.1 reader whose source is the provided byte array and having
   * a user defined maximum BER element size.
   *
   * @param array
   *          The byte array to use.
   * @param maxElementSize
   *          The maximum BER element size, or {@code 0} to indicate that there
   *          is no limit.
   * @return The new ASN.1 reader.
   */
  public static ASN1Reader getReader(final byte[] array,
      final int maxElementSize)
  {
    return getReader(ByteString.wrap(array), maxElementSize);
  }



  /**
   * Returns an ASN.1 reader whose source is the provided byte sequence and
   * having an unlimited maximum BER element size.
   *
   * @param sequence
   *          The byte sequence to use.
   * @return The new ASN.1 reader.
   */
  public static ASN1Reader getReader(final ByteSequence sequence)
  {
    return getReader(sequence, 0);
  }



  /**
   * Returns an ASN.1 reader whose source is the provided byte sequence and
   * having a user defined maximum BER element size.
   *
   * @param sequence
   *          The byte sequence to use.
   * @param maxElementSize
   *          The maximum BER element size, or {@code 0} to indicate that there
   *          is no limit.
   * @return The new ASN.1 reader.
   */
  public static ASN1Reader getReader(final ByteSequence sequence,
      final int maxElementSize)
  {
    return new ASN1ByteSequenceReader(sequence.asReader(), maxElementSize);
  }



  /**
   * Returns an ASN.1 reader whose source is the provided byte sequence reader
   * and having an unlimited maximum BER element size.
   *
   * @param reader
   *          The byte sequence reader to use.
   * @return The new ASN.1 reader.
   */
  public static ASN1Reader getReader(final ByteSequenceReader reader)
  {
    return getReader(reader, 0);
  }



  /**
   * Returns an ASN.1 reader whose source is the provided byte sequence reader
   * and having a user defined maximum BER element size.
   *
   * @param reader
   *          The byte sequence reader to use.
   * @param maxElementSize
   *          The maximum BER element size, or {@code 0} to indicate that there
   *          is no limit.
   * @return The new ASN.1 reader.
   */
  public static ASN1Reader getReader(final ByteSequenceReader reader,
      final int maxElementSize)
  {
    return new ASN1ByteSequenceReader(reader, maxElementSize);
  }



  /**
   * Returns an ASN.1 reader whose source is the provided input stream and
   * having an unlimited maximum BER element size.
   *
   * @param stream
   *          The input stream to use.
   * @return The new ASN.1 reader.
   */
  public static ASN1Reader getReader(final InputStream stream)
  {
    return getReader(stream, 0);
  }



  /**
   * Returns an ASN.1 reader whose source is the provided input stream and
   * having a user defined maximum BER element size.
   *
   * @param stream
   *          The input stream to use.
   * @param maxElementSize
   *          The maximum BER element size, or {@code 0} to indicate that there
   *          is no limit.
   * @return The new ASN.1 reader.
   */
  public static ASN1Reader getReader(final InputStream stream,
      final int maxElementSize)
  {
    return new ASN1InputStreamReader(stream, maxElementSize);
  }



  /**
   * Returns an ASN.1 writer whose destination is the provided byte string
   * builder.
   *
   * @param builder
   *          The byte string builder to use.
   * @return The new ASN.1 writer.
   */
  public static ASN1Writer getWriter(final ByteStringBuilder builder)
  {
    final ByteSequenceOutputStream outputStream = new ByteSequenceOutputStream(
        builder);
    return getWriter(outputStream);
  }



  /**
   * Returns an ASN.1 writer whose destination is the provided output stream.
   *
   * @param stream
   *          The output stream to use.
   * @return The new ASN.1 writer.
   */
  public static ASN1Writer getWriter(final OutputStream stream)
  {
    return new ASN1OutputStreamWriter(stream);
  }



  // Prevent instantiation.
  private ASN1()
  {
    // Nothing to do.
  }
}
