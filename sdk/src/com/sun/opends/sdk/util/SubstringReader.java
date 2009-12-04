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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package com.sun.opends.sdk.util;

/**
 * A sub-string reader.
 */
public class SubstringReader
{
  private String source;
  private int pos;
  private int mark;

  public SubstringReader(String s) {
    Validator.ensureNotNull(s);
    source = s;
    pos = 0;
    mark = 0;
  }

  /**
   * Attemps to read a substring of the specified length.
   *
   * @param length The number of characters to read.
   * @return The substring.
   */
  public String read(int length)
  {
    String substring = source.substring(pos, pos + length);
    pos += length;
    return substring;
  }

  public char read()
  {
    return source.charAt(pos++);
  }

  public String getString()
  {
    return source;
  }

  public int pos()
  {
    return pos;
  }

  public int remaining()
  {
    return source.length() - pos;
  }

  /**
   * Marks the present position in the stream. Subsequent calls
   * to reset() will reposition the stream to this point.
   */
  public void mark()
  {
    mark = pos;
  }

  /**
   * Resets the stream to the most recent mark, or to the beginning
   * of the string if it has never been marked.
   */
  public void reset()
  {
    pos = mark;
  }

  public int skipWhitespaces()
  {
    int skipped = 0;
    while(pos < source.length() && source.charAt(pos) == ' ')
    {
      skipped++;
      pos++;
    }
    return skipped;
  }
}
