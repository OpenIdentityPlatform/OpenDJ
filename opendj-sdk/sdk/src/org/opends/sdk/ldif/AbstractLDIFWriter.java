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

package org.opends.sdk.ldif;



import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.regex.Pattern;

import org.opends.sdk.controls.Control;
import org.opends.sdk.util.Base64;
import org.opends.sdk.util.ByteSequence;
import org.opends.sdk.util.ByteString;
import org.opends.sdk.util.Validator;



/**
 * Common LDIF writer functionality.
 */
abstract class AbstractLDIFWriter extends AbstractLDIFStream
{

  /**
   * LDIF writer implementation interface.
   */
  interface LDIFWriterImpl
  {

    /**
     * Closes any resources associated with this LDIF writer
     * implementation.
     * 
     * @throws IOException
     *           If an error occurs while closing.
     */
    void close() throws IOException;



    /**
     * Flushes this LDIF writer implementation so that any buffered data
     * is written immediately to underlying stream, flushing the stream
     * if it is also {@code Flushable}.
     * <p>
     * If the intended destination of this stream is an abstraction
     * provided by the underlying operating system, for example a file,
     * then flushing the stream guarantees only that bytes previously
     * written to the stream are passed to the operating system for
     * writing; it does not guarantee that they are actually written to
     * a physical device such as a disk drive.
     * 
     * @throws IOException
     *           If an error occurs while flushing.
     */
    void flush() throws IOException;



    /**
     * Prints the provided {@code CharSequence}. Implementations must
     * not add a new-line character sequence.
     * 
     * @param s
     *          The {@code CharSequence} to be printed.
     * @throws IOException
     *           If an error occurs while printing {@code s}.
     */
    void print(CharSequence s) throws IOException;



    /**
     * Prints a new-line character sequence.
     * 
     * @throws IOException
     *           If an error occurs while printing the new-line
     *           character sequence.
     */
    void println() throws IOException;
  }



  /**
   * LDIF string list writer implementation.
   */
  private final class LDIFWriterListImpl implements LDIFWriterImpl
  {

    private final StringBuilder builder = new StringBuilder();

    private final List<String> ldifLines;



    /**
     * Creates a new LDIF list writer.
     * 
     * @param ldifLines
     *          The string list.
     */
    LDIFWriterListImpl(List<String> ldifLines)
    {
      this.ldifLines = ldifLines;
    }



    /**
     * {@inheritDoc}
     */
    public void close() throws IOException
    {
      // Nothing to do.
    }



    /**
     * {@inheritDoc}
     */
    public void flush() throws IOException
    {
      // Nothing to do.
    }



    /**
     * {@inheritDoc}
     */
    public void print(CharSequence s) throws IOException
    {
      builder.append(s);
    }



    /**
     * {@inheritDoc}
     */
    public void println() throws IOException
    {
      ldifLines.add(builder.toString());
      builder.setLength(0);
    }
  }



  /**
   * LDIF output stream writer implementation.
   */
  private final class LDIFWriterOutputStreamImpl implements
      LDIFWriterImpl
  {

    private final BufferedWriter writer;



    /**
     * Creates a new LDIF output stream writer.
     * 
     * @param out
     *          The output stream.
     */
    LDIFWriterOutputStreamImpl(OutputStream out)
    {
      this.writer = new BufferedWriter(new OutputStreamWriter(out));
    }



    /**
     * {@inheritDoc}
     */
    public void close() throws IOException
    {
      writer.close();
    }



    /**
     * {@inheritDoc}
     */
    public void flush() throws IOException
    {
      writer.flush();
    }



    /**
     * {@inheritDoc}
     */
    public void print(CharSequence s) throws IOException
    {
      writer.append(s);
    }



    /**
     * {@inheritDoc}
     */
    public void println() throws IOException
    {
      writer.newLine();
    }
  }

  // Regular expression used for splitting comments on line-breaks.
  private static final Pattern SPLIT_NEWLINE =
      Pattern.compile("\\r?\\n");

  boolean addUserFriendlyComments = false;

  final LDIFWriterImpl impl;

  int wrapColumn = 0;

  private final StringBuilder builder = new StringBuilder(80);



  /**
   * Creates a new LDIF entry writer which will append lines of LDIF to
   * the provided list.
   * 
   * @param ldifLines
   *          The list to which lines of LDIF should be appended.
   */
  public AbstractLDIFWriter(List<String> ldifLines)
  {
    Validator.ensureNotNull(ldifLines);
    this.impl = new LDIFWriterListImpl(ldifLines);
  }



  /**
   * Creates a new LDIF entry writer whose destination is the provided
   * output stream.
   * 
   * @param out
   *          The output stream to use.
   */
  public AbstractLDIFWriter(OutputStream out)
  {
    Validator.ensureNotNull(out);
    this.impl = new LDIFWriterOutputStreamImpl(out);
  }



  final void close0() throws IOException
  {
    flush0();
    impl.close();
  }



  final void flush0() throws IOException
  {
    impl.flush();
  }



  final void writeComment0(CharSequence comment) throws IOException,
      NullPointerException
  {
    Validator.ensureNotNull(comment);

    // First, break up the comment into multiple lines to preserve the
    // original spacing that it contained.
    final String[] lines = SPLIT_NEWLINE.split(comment);

    // Now iterate through the lines and write them out, prefixing and
    // wrapping them as necessary.
    for (final String line : lines)
    {
      if (!shouldWrap())
      {
        impl.print("# ");
        impl.print(line);
        impl.println();
      }
      else
      {
        final int breakColumn = wrapColumn - 2;

        if (line.length() <= breakColumn)
        {
          impl.print("# ");
          impl.print(line);
          impl.println();
        }
        else
        {
          int startPos = 0;
          outerLoop: while (startPos < line.length())
          {
            if (startPos + breakColumn >= line.length())
            {
              impl.print("# ");
              impl.print(line.substring(startPos));
              impl.println();
              startPos = line.length();
            }
            else
            {
              final int endPos = startPos + breakColumn;

              int i = endPos - 1;
              while (i > startPos)
              {
                if (line.charAt(i) == ' ')
                {
                  impl.print("# ");
                  impl.print(line.substring(startPos, i));
                  impl.println();

                  startPos = i + 1;
                  continue outerLoop;
                }

                i--;
              }

              // If we've gotten here, then there are no spaces on the
              // entire line. If that happens, then we'll have to break
              // in the middle of a word.
              impl.print("# ");
              impl.print(line.substring(startPos, endPos));
              impl.println();

              startPos = endPos;
            }
          }
        }
      }
    }
  }



  final void writeControls(Iterable<Control> controls)
      throws IOException
  {
    for (final Control control : controls)
    {
      final StringBuilder key = new StringBuilder("control: ");
      key.append(control.getOID());
      key.append(control.isCritical() ? " true" : " false");

      if (control.hasValue())
      {
        writeKeyAndValue(key, control.getValue());
      }
      else
      {
        writeLine(key);
      }
    }
  }



  final void writeKeyAndValue(CharSequence key, ByteSequence value)
      throws IOException
  {
    builder.setLength(0);

    // If the value is empty, then just append a single colon and a
    // single space.
    if (value.length() == 0)
    {
      builder.append(key);
      builder.append(": ");
    }
    else if (needsBase64Encoding(value))
    {
      if (addUserFriendlyComments)
      {
        // TODO: Only display comments for valid UTF-8 values, not
        // binary values.
      }

      builder.setLength(0);
      builder.append(key);
      builder.append(":: ");
      builder.append(Base64.encode(value));
    }
    else
    {
      builder.append(key);
      builder.append(": ");
      builder.append(value.toString());
    }

    writeLine(builder);
  }



  final void writeKeyAndValue(CharSequence key, CharSequence value)
      throws IOException
  {
    // FIXME: We should optimize this at some point.
    writeKeyAndValue(key, ByteString.valueOf(value.toString()));
  }



  final void writeLine(CharSequence line) throws IOException
  {
    final int length = line.length();
    if (shouldWrap() && length > wrapColumn)
    {
      impl.print(line.subSequence(0, wrapColumn));
      impl.println();
      int pos = wrapColumn;
      while (pos < length)
      {
        final int writeLength = Math.min(wrapColumn - 1, length - pos);
        impl.print(" ");
        impl.print(line.subSequence(pos, pos + writeLength));
        impl.println();
        pos += wrapColumn - 1;
      }
    }
    else
    {
      impl.print(line);
      impl.println();
    }
  }



  private boolean needsBase64Encoding(ByteSequence bytes)
  {
    final int length = bytes.length();
    if (length == 0)
    {
      return false;
    }

    // If the value starts with a space, colon, or less than, then it
    // needs to be base64 encoded.
    switch (bytes.byteAt(0))
    {
    case 0x20: // Space
    case 0x3A: // Colon
    case 0x3C: // Less-than
      return true;
    }

    // If the value ends with a space, then it needs to be
    // base64 encoded.
    if (length > 1 && bytes.byteAt(length - 1) == 0x20)
    {
      return true;
    }

    // If the value contains a null, newline, or return character, then
    // it needs to be base64 encoded.
    byte b;
    for (int i = 0; i < bytes.length(); i++)
    {
      b = bytes.byteAt(i);
      if (b > 127 || b < 0)
      {
        return true;
      }

      switch (b)
      {
      case 0x00: // Null
      case 0x0A: // New line
      case 0x0D: // Carriage return
        return true;
      }
    }

    // If we've made it here, then there's no reason to base64 encode.
    return false;
  }



  private boolean shouldWrap()
  {
    return wrapColumn > 1;
  }



  @SuppressWarnings("unused")
  private void writeKeyAndURL(CharSequence key, CharSequence url)
      throws IOException
  {
    builder.setLength(0);

    builder.append(key);
    builder.append(":: ");
    builder.append(url);

    writeLine(builder);
  }
}
