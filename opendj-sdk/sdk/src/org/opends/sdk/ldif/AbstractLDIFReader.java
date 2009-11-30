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



import static org.opends.messages.UtilityMessages.*;
import static org.opends.sdk.util.StaticUtils.toLowerCase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.sdk.*;
import org.opends.sdk.util.*;



/**
 * Common LDIF reader functionality.
 */
abstract class AbstractLDIFReader extends AbstractLDIFStream
{
  static final class KeyValuePair
  {
    String key;

    String value;
  }



  /**
   * LDIF reader implementation interface.
   */
  interface LDIFReaderImpl
  {

    /**
     * Closes any resources associated with this LDIF reader
     * implementation.
     *
     * @throws IOException
     *           If an error occurs while closing.
     */
    void close() throws IOException;



    /**
     * Reads the next line of LDIF from the underlying LDIF source.
     * Implementations must remove trailing line delimiters.
     *
     * @return The next line of LDIF, or {@code null} if the end of the
     *         LDIF source has been reached.
     * @throws IOException
     *           If an error occurs while reading from the LDIF source.
     */
    String readLine() throws IOException;
  }



  final class LDIFRecord
  {
    final Iterator<String> iterator;

    final LinkedList<String> ldifLines;

    final long lineNumber;



    private LDIFRecord(long lineNumber, LinkedList<String> ldifLines)
    {
      this.lineNumber = lineNumber;
      this.ldifLines = ldifLines;
      this.iterator = ldifLines.iterator();
    }
  }



  /**
   * LDIF output stream writer implementation.
   */
  private final class LDIFReaderInputStreamImpl implements
      LDIFReaderImpl
  {

    private BufferedReader reader;



    /**
     * Creates a new LDIF input stream reader implementation.
     *
     * @param in
     *          The input stream to use.
     */
    LDIFReaderInputStreamImpl(InputStream in)
    {
      this.reader = new BufferedReader(new InputStreamReader(in));
    }



    /**
     * {@inheritDoc}
     */
    public void close() throws IOException
    {
      reader.close();
      reader = null;
    }



    /**
     *{@inheritDoc}
     */
    public String readLine() throws IOException
    {
      String line = null;
      if (reader != null)
      {
        line = reader.readLine();
        if (line == null)
        {
          // Automatically close.
          close();
        }
      }
      return line;
    }
  }



  /**
   * LDIF output stream writer implementation.
   */
  private final class LDIFReaderListImpl implements LDIFReaderImpl
  {

    private final Iterator<String> iterator;



    /**
     * Creates a new LDIF list reader.
     *
     * @param ldifLines
     *          The string list.
     */
    LDIFReaderListImpl(List<String> ldifLines)
    {
      this.iterator = ldifLines.iterator();
    }



    /**
     * {@inheritDoc}
     */
    public void close() throws IOException
    {
      // Nothing to do.
    }



    /**
     *{@inheritDoc}
     */
    public String readLine() throws IOException
    {
      if (iterator.hasNext())
      {
        return iterator.next();
      }
      else
      {
        return null;
      }
    }
  }



  boolean validateSchema = true;

  private final LDIFReaderImpl impl;

  private long lineNumber = 0;



  /**
   * Creates a new LDIF entry reader whose source is the provided input
   * stream.
   *
   * @param in
   *          The input stream to use.
   */
  AbstractLDIFReader(InputStream in)
  {
    Validator.ensureNotNull(in);
    this.impl = new LDIFReaderInputStreamImpl(in);
  }



  /**
   * Creates a new LDIF entry reader which will read lines of LDIF from
   * the provided list.
   *
   * @param ldifLines
   *          The list from which lines of LDIF should be read.
   */
  AbstractLDIFReader(List<String> ldifLines)
  {
    Validator.ensureNotNull(ldifLines);
    this.impl = new LDIFReaderListImpl(ldifLines);
  }



  final void close0() throws IOException
  {
    impl.close();
  }



  final int parseColonPosition(LDIFRecord record, String ldifLine)
      throws DecodeException
  {
    final int colonPos = ldifLine.indexOf(":");
    if (colonPos <= 0)
    {
      final Message message = ERR_LDIF_NO_ATTR_NAME.get(
          record.lineNumber, ldifLine);
      throw DecodeException.error(message);
    }
    return colonPos;
  }



  final ByteString parseSingleValue(LDIFRecord record, String ldifLine,
      DN entryDN, int colonPos, String attrName) throws DecodeException
  {

    // Look at the character immediately after the colon. If there is
    // none, then assume an attribute with an empty value. If it is
    // another colon, then the value must be base64-encoded. If it is a
    // less-than sign, then assume that it is a URL. Otherwise, it is a
    // regular value.
    final int length = ldifLine.length();
    ByteString value;
    if (colonPos == length - 1)
    {
      value = ByteString.empty();
    }
    else
    {
      final char c = ldifLine.charAt(colonPos + 1);
      if (c == ':')
      {
        // The value is base64-encoded. Find the first non-blank
        // character, take the rest of the line, and base64-decode it.
        int pos = colonPos + 2;
        while (pos < length && ldifLine.charAt(pos) == ' ')
        {
          pos++;
        }

        try
        {
          value = Base64.decode(ldifLine.substring(pos));
        }
        catch (final LocalizedIllegalArgumentException e)
        {
          // The value did not have a valid base64-encoding.
          final Message message = ERR_LDIF_COULD_NOT_BASE64_DECODE_ATTR
              .get(entryDN.toString(), record.lineNumber, ldifLine, e
                  .getMessageObject());
          throw DecodeException.error(message);
        }
      }
      else if (c == '<')
      {
        // Find the first non-blank character, decode the rest of the
        // line as a URL, and read its contents.
        int pos = colonPos + 2;
        while (pos < length && ldifLine.charAt(pos) == ' ')
        {
          pos++;
        }

        URL contentURL;
        try
        {
          contentURL = new URL(ldifLine.substring(pos));
        }
        catch (final Exception e)
        {
          // The URL was malformed or had an invalid protocol.
          final Message message = ERR_LDIF_INVALID_URL.get(entryDN
              .toString(), record.lineNumber, attrName, String
              .valueOf(e));
          throw DecodeException.error(message);
        }

        InputStream inputStream = null;
        ByteStringBuilder builder = null;
        try
        {
          builder = new ByteStringBuilder();
          inputStream = contentURL.openConnection().getInputStream();

          int bytesRead;
          final byte[] buffer = new byte[4096];
          while ((bytesRead = inputStream.read(buffer)) > 0)
          {
            builder.append(buffer, 0, bytesRead);
          }

          value = builder.toByteString();
        }
        catch (final Exception e)
        {
          // We were unable to read the contents of that URL for some
          // reason.
          final Message message = ERR_LDIF_URL_IO_ERROR.get(entryDN
              .toString(), record.lineNumber, attrName, String
              .valueOf(contentURL), String.valueOf(e));
          throw DecodeException.error(message);
        }
        finally
        {
          if (inputStream != null)
          {
            try
            {
              inputStream.close();
            }
            catch (final Exception e)
            {
              // Ignore.
            }
          }
        }
      }
      else
      {
        // The rest of the line should be the value. Skip over any
        // spaces and take the rest of the line as the value.
        int pos = colonPos + 1;
        while (pos < length && ldifLine.charAt(pos) == ' ')
        {
          pos++;
        }

        value = ByteString.valueOf(ldifLine.substring(pos));
      }
    }
    return value;
  }



  final LDIFRecord readLDIFRecord() throws IOException
  {
    // Read the entry lines into a buffer.
    final StringBuilder lastLineBuilder = new StringBuilder();
    final LinkedList<String> ldifLines = new LinkedList<String>();
    long recordLineNumber = 0;

    final int START = 0;
    final int START_COMMENT_LINE = 1;
    final int GOT_LDIF_LINE = 2;
    final int GOT_COMMENT_LINE = 3;
    final int APPENDING_LDIF_LINE = 4;

    int state = START;

    while (true)
    {
      final String line = readLine();

      switch (state)
      {
      case START:
        if (line == null)
        {
          // We have reached the end of the LDIF source.
          return null;
        }
        else if (line.length() == 0)
        {
          // Skip leading blank lines.
        }
        else if (line.charAt(0) == '#')
        {
          // This is a comment at the start of the LDIF record.
          state = START_COMMENT_LINE;
        }
        else if (isContinuationLine(line))
        {
          // Fatal: got a continuation line at the start of the record.
          final Message message = ERR_LDIF_INVALID_LEADING_SPACE.get(
              lineNumber, line);
          throw DecodeException.fatalError(message);
        }
        else
        {
          // Got the first line of LDIF.
          ldifLines.add(line);
          recordLineNumber = lineNumber;
          state = GOT_LDIF_LINE;
        }
        break;
      case START_COMMENT_LINE:
        if (line == null)
        {
          // We have reached the end of the LDIF source.
          return null;
        }
        else if (line.length() == 0)
        {
          // Skip leading blank lines and comments.
          state = START;
        }
        else if (line.charAt(0) == '#')
        {
          // This is another comment at the start of the LDIF record.
        }
        else if (isContinuationLine(line))
        {
          // Skip comment continuation lines.
        }
        else
        {
          // Got the first line of LDIF.
          ldifLines.add(line);
          recordLineNumber = lineNumber;
          state = GOT_LDIF_LINE;
        }
        break;
      case GOT_LDIF_LINE:
        if (line == null)
        {
          // We have reached the end of the LDIF source.
          return new LDIFRecord(recordLineNumber, ldifLines);
        }
        else if (line.length() == 0)
        {
          // We have reached the end of the LDIF record.
          return new LDIFRecord(recordLineNumber, ldifLines);
        }
        else if (line.charAt(0) == '#')
        {
          // This is a comment.
          state = GOT_COMMENT_LINE;
        }
        else if (isContinuationLine(line))
        {
          // Got a continuation line for the previous line.
          lastLineBuilder.setLength(0);
          lastLineBuilder.append(ldifLines.removeLast());
          lastLineBuilder.append(line.substring(1));
          state = APPENDING_LDIF_LINE;
        }
        else
        {
          // Got the next line of LDIF.
          ldifLines.add(line);
          state = GOT_LDIF_LINE;
        }
        break;
      case GOT_COMMENT_LINE:
        if (line == null)
        {
          // We have reached the end of the LDIF source.
          return new LDIFRecord(recordLineNumber, ldifLines);
        }
        else if (line.length() == 0)
        {
          // We have reached the end of the LDIF record.
          return new LDIFRecord(recordLineNumber, ldifLines);
        }
        else if (line.charAt(0) == '#')
        {
          // This is another comment.
          state = GOT_COMMENT_LINE;
        }
        else if (isContinuationLine(line))
        {
          // Skip comment continuation lines.
        }
        else
        {
          // Got the next line of LDIF.
          ldifLines.add(line);
          state = GOT_LDIF_LINE;
        }
        break;
      case APPENDING_LDIF_LINE:
        if (line == null)
        {
          // We have reached the end of the LDIF source.
          ldifLines.add(lastLineBuilder.toString());
          return new LDIFRecord(recordLineNumber, ldifLines);
        }
        else if (line.length() == 0)
        {
          // We have reached the end of the LDIF record.
          ldifLines.add(lastLineBuilder.toString());
          return new LDIFRecord(recordLineNumber, ldifLines);
        }
        else if (line.charAt(0) == '#')
        {
          // This is a comment.
          ldifLines.add(lastLineBuilder.toString());
          state = GOT_COMMENT_LINE;
        }
        else if (isContinuationLine(line))
        {
          // Got another continuation line for the previous line.
          lastLineBuilder.append(line.substring(1));
        }
        else
        {
          // Got the next line of LDIF.
          ldifLines.add(lastLineBuilder.toString());
          ldifLines.add(line);
          state = GOT_LDIF_LINE;
        }
        break;
      }
    }
  }



  final void readLDIFRecordAttributeValue(LDIFRecord record,
      String ldifLine, Entry entry) throws DecodeException
  {
    // Parse the attribute description.
    final int colonPos = parseColonPosition(record, ldifLine);
    final String attrDescr = ldifLine.substring(0, colonPos);

    AttributeDescription attributeDescription;
    try
    {
      attributeDescription = AttributeDescription.valueOf(attrDescr,
          schema);
    }
    catch (final LocalizedIllegalArgumentException e)
    {
      throw DecodeException.error(e.getMessageObject());
    }

    // Now parse the attribute value.
    final ByteString value = parseSingleValue(record, ldifLine, entry
        .getName(), colonPos, attrDescr);

    // Skip the attribute if requested before performing any schema
    // checking: the attribute may have been excluded because it is
    // known to violate the schema.
    if (isAttributeExcluded(attributeDescription))
    {
      return;
    }

    // Ensure that the binary option is present if required.
    if (!attributeDescription.getAttributeType().getSyntax()
        .isBEREncodingRequired())
    {
      if (validateSchema
          && attributeDescription.containsOption("binary"))
      {
        final Message message = ERR_LDIF_INVALID_ATTR_OPTION.get(entry
            .getName().toString(), record.lineNumber, attrDescr);
        throw DecodeException.error(message);
      }
    }
    else
    {
      attributeDescription = AttributeDescription.create(
          attributeDescription, "binary");
    }

    Attribute attribute = entry.getAttribute(attributeDescription);
    if (attribute == null)
    {
      if (validateSchema)
      {
        final MessageBuilder invalidReason = new MessageBuilder();
        if (!attributeDescription.getAttributeType().getSyntax()
            .valueIsAcceptable(value, invalidReason))
        {
          final Message message = WARN_LDIF_VALUE_VIOLATES_SYNTAX.get(
              entry.getName().toString(), record.lineNumber, value
                  .toString(), attrDescr, invalidReason);
          throw DecodeException.error(message);
        }
      }

      attribute = new LinkedAttribute(attributeDescription, value);
      entry.addAttribute(attribute);
    }
    else
    {
      if (validateSchema)
      {
        final MessageBuilder invalidReason = new MessageBuilder();
        if (!attributeDescription.getAttributeType().getSyntax()
            .valueIsAcceptable(value, invalidReason))
        {
          final Message message = WARN_LDIF_VALUE_VIOLATES_SYNTAX.get(
              entry.getName().toString(), record.lineNumber, value
                  .toString(), attrDescr, invalidReason);
          throw DecodeException.error(message);
        }

        if (!attribute.add(value))
        {
          final Message message = WARN_LDIF_DUPLICATE_ATTR.get(entry
              .getName().toString(), record.lineNumber, attrDescr,
              value.toString());
          throw DecodeException.error(message);
        }

        if (attributeDescription.getAttributeType().isSingleValue())
        {
          final Message message = ERR_LDIF_MULTIPLE_VALUES_FOR_SINGLE_VALUED_ATTR
              .get(entry.getName().toString(), record.lineNumber,
                  attrDescr);
          throw DecodeException.error(message);
        }
      }
      else
      {
        attribute.add(value);
      }
    }
  }



  final DN readLDIFRecordDN(LDIFRecord record) throws DecodeException
  {
    String ldifLine = record.iterator.next();
    int colonPos = ldifLine.indexOf(":");
    if (colonPos <= 0)
    {
      final Message message = ERR_LDIF_NO_ATTR_NAME.get(
          record.lineNumber, ldifLine.toString());
      throw DecodeException.error(message);
    }

    String attrName = toLowerCase(ldifLine.substring(0, colonPos));
    if (attrName.equals("version"))
    {
      // This is the version line, try the next line if there is one.
      if (!record.iterator.hasNext())
      {
        return null;
      }

      ldifLine = record.iterator.next();
      colonPos = ldifLine.indexOf(":");
      if (colonPos <= 0)
      {
        final Message message = ERR_LDIF_NO_ATTR_NAME.get(
            record.lineNumber, ldifLine.toString());
        throw DecodeException.error(message);
      }

      attrName = toLowerCase(ldifLine.substring(0, colonPos));
    }

    if (!attrName.equals("dn"))
    {
      final Message message = ERR_LDIF_NO_DN.get(record.lineNumber,
          ldifLine.toString());
      throw DecodeException.error(message);
    }

    // Look at the character immediately after the colon. If there is
    // none, then assume the null DN. If it is another colon, then the
    // DN must be base64-encoded. Otherwise, it may be one or more
    // spaces.
    final int length = ldifLine.length();
    if (colonPos == length - 1)
    {
      return DN.rootDN();
    }

    String dnString = null;

    if (ldifLine.charAt(colonPos + 1) == ':')
    {
      // The DN is base64-encoded. Find the first non-blank character
      // and take the rest of the line and base64-decode it.
      int pos = colonPos + 2;
      while (pos < length && ldifLine.charAt(pos) == ' ')
      {
        pos++;
      }

      final String base64DN = ldifLine.substring(pos);
      try
      {
        dnString = Base64.decode(base64DN).toString();
      }
      catch (final LocalizedIllegalArgumentException e)
      {
        // The value did not have a valid base64-encoding.
        final Message message = ERR_LDIF_COULD_NOT_BASE64_DECODE_DN
            .get(record.lineNumber, ldifLine, e.getMessageObject());
        throw DecodeException.error(message);
      }
    }
    else
    {
      // The rest of the value should be the DN. Skip over any spaces
      // and attempt to decode the rest of the line as the DN.
      int pos = colonPos + 1;
      while (pos < length && ldifLine.charAt(pos) == ' ')
      {
        pos++;
      }

      dnString = ldifLine.substring(pos);
    }

    try
    {
      return DN.valueOf(dnString, schema);
    }
    catch (final LocalizedIllegalArgumentException e)
    {
      final Message message = ERR_LDIF_INVALID_DN.get(
          record.lineNumber, ldifLine, e.getMessageObject());
      throw DecodeException.error(message);
    }
  }



  final String readLDIFRecordKeyValuePair(LDIFRecord record,
      KeyValuePair pair, boolean allowBase64) throws DecodeException
  {
    final String ldifLine = record.iterator.next();
    final int colonPos = ldifLine.indexOf(":");
    if (colonPos <= 0)
    {
      final Message message = ERR_LDIF_NO_ATTR_NAME.get(
          record.lineNumber, ldifLine);
      throw DecodeException.error(message);
    }
    pair.key = ldifLine.substring(0, colonPos);

    // Look at the character immediately after the colon. If there is
    // none, then no value was specified. Throw an exception
    final int length = ldifLine.length();
    if (colonPos == length - 1)
    {
      // FIXME: improve error.
      final Message message = Message
          .raw("Malformed changetype attribute");
      throw DecodeException.error(message);
    }

    if (allowBase64 && ldifLine.charAt(colonPos + 1) == ':')
    {
      // The value is base64-encoded. Find the first non-blank
      // character, take the rest of the line, and base64-decode it.
      int pos = colonPos + 2;
      while (pos < length && ldifLine.charAt(pos) == ' ')
      {
        pos++;
      }

      try
      {
        pair.value = Base64.decode(ldifLine.substring(pos)).toString();
      }
      catch (final LocalizedIllegalArgumentException e)
      {
        // The value did not have a valid base64-encoding.
        // FIXME: improve error.
        final Message message = Message
            .raw("Malformed base64 changetype attribute");
        throw DecodeException.error(message);
      }
    }
    else
    {
      // The rest of the value should be the changetype. Skip over any
      // spaces and attempt to decode the rest of the line as the
      // changetype string.
      int pos = colonPos + 1;
      while (pos < length && ldifLine.charAt(pos) == ' ')
      {
        pos++;
      }

      pair.value = ldifLine.substring(pos);
    }

    return ldifLine;
  }



  final void rejectLDIFRecord(LDIFRecord record, Message message)
      throws DecodeException
  {
    // FIXME: not yet implemented.
    throw DecodeException.error(message);
  }



  final void skipLDIFRecord(LDIFRecord record, Message message)
  {
    // FIXME: not yet implemented.
  }



  // Determine whether the provided line is a continuation line. Note
  // that while RFC 2849 technically only allows a space in this
  // position, both OpenLDAP and the Sun Java System Directory Server
  // allow a tab as well, so we will too for compatibility reasons. See
  // issue #852 for details.
  private boolean isContinuationLine(String line)
  {
    return line.charAt(0) == ' ' || line.charAt(0) == '\t';
  }



  private String readLine() throws IOException
  {
    final String line = impl.readLine();
    if (line != null)
    {
      lineNumber++;
    }
    return line;
  }

}
