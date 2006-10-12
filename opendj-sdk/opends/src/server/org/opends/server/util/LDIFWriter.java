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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.util;



import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.Collection;

import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.Modification;
import org.opends.server.types.RDN;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.util.Validator.*;



/**
 * This class provides a mechanism for writing entries in LDIF form to a file or
 * an output stream.
 */
public final class LDIFWriter
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME = "org.opends.server.util.LDIFWriter";


  // FIXME -- Add support for generating a hash when writing the data.
  // FIXME -- Add support for signing the hash that is generated.



  // The writer to which the LDIF information will be written.
  private BufferedWriter writer;

  // The configuration to use for the export.
  private LDIFExportConfig exportConfig;

  // Regular expression used for splitting comments on line-breaks.
  private static final Pattern SPLIT_NEWLINE = Pattern.compile("\\r?\\n");



  /**
   * Creates a new LDIF writer with the provided configuration.
   *
   * @param  exportConfig  The configuration to use for the export.  It must not
   *                       be <CODE>null</CODE>.
   *
   * @throws  IOException  If a problem occurs while opening the writer.
   */
  public LDIFWriter(LDIFExportConfig exportConfig)
         throws IOException
  {
    assert debugConstructor(CLASS_NAME, String.valueOf(exportConfig));

    ensureNotNull(exportConfig);
    this.exportConfig = exportConfig;

    writer = exportConfig.getWriter();
  }



  /**
   * Writes the provided comment to the LDIF file, optionally wrapping near the
   * specified column.  Each line will be prefixed by the octothorpe (#)
   * character followed by a space.  If the comment should be wrapped at a
   * specified column, then it will attempt to do so at the first whitespace
   * character at or before that column (so it will try not wrap in the middle
   * of a word).
   * <BR><BR>
   * This comment will be ignored by the
   * Directory Server's LDIF reader, as well as any other compliant LDIF parsing
   * software.
   *
   * @param  comment     The comment to be written.  Any line breaks that it
   *                     contains will be honored, and potentially new line
   *                     breaks may be introduced by the wrapping process.  It
   *                     must not be <CODE>null</CODE>.
   * @param  wrapColumn  The column at which long lines should be wrapped, or
   *                     -1 to indicate that no additional wrapping should be
   *                     added.  This will override the wrap column setting
   *                     specified in the LDIF export configuration.
   *
   * @throws  IOException  If a problem occurs while attempting to write the
   *                       comment to the LDIF file.
   */
  public void writeComment(String comment, int wrapColumn)
         throws IOException
  {
    assert debugEnter(CLASS_NAME, "writeComment", String.valueOf(comment),
                      String.valueOf(wrapColumn));

    ensureNotNull(comment);


    // First, break up the comment into multiple lines to preserve the original
    // spacing that it contained.
    String[] lines = SPLIT_NEWLINE.split(comment);

    // Now iterate through the lines and write them out, prefixing and wrapping
    // them as necessary.
    for (String l : lines)
    {
      if (wrapColumn <= 0)
      {
        writer.write("# ");
        writer.write(l);
        writer.newLine();
      }
      else
      {
        int breakColumn = wrapColumn - 2;

        if (l.length() <= breakColumn)
        {
          writer.write("# ");
          writer.write(l);
          writer.newLine();
        }
        else
        {
          int startPos = 0;
outerLoop:
          while (startPos < l.length())
          {
            if ((startPos+breakColumn) >= l.length())
            {

              writer.write("# ");
              writer.write(l.substring(startPos));
              writer.newLine();
              startPos = l.length();
            }
            else
            {
              int endPos = startPos + breakColumn;

              int i=endPos - 1;
              while (i > startPos)
              {
                if (l.charAt(i) == ' ')
                {
                  writer.write("# ");
                  writer.write(l.substring(startPos, i));
                  writer.newLine();

                  startPos = i+1;
                  continue outerLoop;
                }

                i--;
              }

              // If we've gotten here, then there are no spaces on the entire
              // line.  If that happens, then we'll have to break in the middle
              // of a word.
              writer.write("# ");
              writer.write(l.substring(startPos, endPos));
              writer.newLine();

              startPos = endPos;
            }
          }
        }
      }
    }
  }

  /**
 * Iterates over each entry contained in the map and writes out the entry in
 * LDIF format. The main benefit of this method is that the entries can be
 * sorted by DN and output in sorted order.
 *
 * @param entries The Map containing the entries keyed by DN.
 *
 * @return <CODE>true</CODE>of all of the entries were
 *                  written out, <CODE>false</CODE>if it was not
 *                  because of the export configuration.
 *
 * @throws IOException  If a problem occurs while writing the entry to LDIF.
 *
 * @throws LDIFException If a problem occurs while trying to determine
 *                         whether to include the entry in the export.
 */
public boolean writeEntries(Collection <Entry> entries)
  throws IOException, LDIFException {
      assert debugEnter(CLASS_NAME, "writeEntry", String.valueOf(entries));

     boolean ret=true;
     Iterator<Entry> i = entries.iterator();
     while(ret && i.hasNext()) {
         ret=writeEntry(i.next());
     }
      return ret;
  }


  /**
   * Writes the provided entry to LDIF.
   *
   * @param  entry  The entry to be written.  It must not be <CODE>null</CODE>.
   *
   * @return  <CODE>true</CODE> if the entry was actually written, or
   *          <CODE>false</CODE> if it was not because of the export
   *          configuration.
   *
   * @throws  IOException  If a problem occurs while writing the entry to LDIF.
   *
   * @throws  LDIFException  If a problem occurs while trying to determine
   *                         whether to include the entry in the export.
   */
  public boolean writeEntry(Entry entry)
         throws IOException, LDIFException
  {
    assert debugEnter(CLASS_NAME, "writeEntry", String.valueOf(entry));

    ensureNotNull(entry);
    return entry.toLDIF(exportConfig);
  }



  /**
   * Writes an add change record for the provided entry.  No filtering will be
   * performed for this entry, nor will any export plugins be invoked.  Further,
   * only the user attributes will be included.
   *
   * @param  entry  The entry to include in the add change record.  It must not
   *                be <CODE>null</CODE>.
   *
   * @throws  IOException  If a problem occurs while writing the add record.
   */
  public void writeAddChangeRecord(Entry entry)
         throws IOException
  {
    assert debugEnter(CLASS_NAME, "writeAddChangeRecord",
                      String.valueOf(entry));

    ensureNotNull(entry);


    // Get the information necessary to write the LDIF.
    BufferedWriter writer     = exportConfig.getWriter();
    int            wrapColumn = exportConfig.getWrapColumn();
    boolean        wrapLines  = (wrapColumn > 1);


    // First, write the DN.
    StringBuilder dnLine = new StringBuilder();
    dnLine.append("dn");
    appendLDIFSeparatorAndValue(dnLine, getBytes(entry.getDN().toString()));
    writeLDIFLine(dnLine, writer, wrapLines, wrapColumn);


    // Next, the changetype.
    StringBuilder changeTypeLine = new StringBuilder("changetype: add");
    writeLDIFLine(changeTypeLine, writer, wrapLines, wrapColumn);


    // Now the objectclasses.
    for (String s : entry.getObjectClasses().values())
    {
      StringBuilder ocLine = new StringBuilder();
      ocLine.append("objectClass: ");
      ocLine.append(s);
      writeLDIFLine(ocLine, writer, wrapLines, wrapColumn);
    }


    // Finally, the set of user attributes.
    for (AttributeType attrType : entry.getUserAttributes().keySet())
    {
      List<Attribute> attrList = entry.getUserAttribute(attrType);
      for (Attribute a : attrList)
      {
        StringBuilder attrName = new StringBuilder(a.getName());
        for (String o : a.getOptions())
        {
          attrName.append(";");
          attrName.append(o);
        }

        for (AttributeValue v : a.getValues())
        {
          StringBuilder attrLine = new StringBuilder();
          attrLine.append(attrName);
          appendLDIFSeparatorAndValue(attrLine, v.getValueBytes());
          writeLDIFLine(attrLine, writer, wrapLines, wrapColumn);
        }
      }
    }


    // Make sure there is a blank line after the entry.
    writer.newLine();
  }



  /**
   * Writes a delete change record for the provided entry, optionally including
   * a comment with the full entry contents.  No filtering will be performed for
   * this entry, nor will any export plugins be invoked.  Further, only the user
   * attributes will be included.
   *
   * @param  entry         The entry to include in the delete change record.  It
   *                       must not be <CODE>null</CODE>.
   * @param  commentEntry  Indicates whether to include a comment with the
   *                       contents of the entry.
   *
   * @throws  IOException  If a problem occurs while writing the delete record.
   */
  public void writeDeleteChangeRecord(Entry entry, boolean commentEntry)
         throws IOException
  {
    assert debugEnter(CLASS_NAME, "writeDeleteChangeRecord",
                      String.valueOf(entry), String.valueOf(commentEntry));

    ensureNotNull(entry);

    // Get the information necessary to write the LDIF.
    BufferedWriter writer     = exportConfig.getWriter();
    int            wrapColumn = exportConfig.getWrapColumn();
    boolean        wrapLines  = (wrapColumn > 1);


    // Add the DN and changetype lines.
    StringBuilder dnLine = new StringBuilder();
    dnLine.append("dn");
    appendLDIFSeparatorAndValue(dnLine, getBytes(entry.getDN().toString()));
    writeLDIFLine(dnLine, writer, wrapLines, wrapColumn);

    StringBuilder changeTypeLine = new StringBuilder("changetype: delete");
    writeLDIFLine(changeTypeLine, writer, wrapLines, wrapColumn);


    // If we should include a comment with the rest of the entry contents, then
    // do so now.
    if (commentEntry)
    {
      // Write the objectclasses.
      for (String s : entry.getObjectClasses().values())
      {
        StringBuilder ocLine = new StringBuilder();
        ocLine.append("# objectClass: ");
        ocLine.append(s);
        writeLDIFLine(ocLine, writer, wrapLines, wrapColumn);
      }

      // Write the set of user attributes.
      for (AttributeType attrType : entry.getUserAttributes().keySet())
      {
        List<Attribute> attrList = entry.getUserAttribute(attrType);
        for (Attribute a : attrList)
        {
          StringBuilder attrName = new StringBuilder();
          attrName.append("# ");
          attrName.append(a.getName());
          for (String o : a.getOptions())
          {
            attrName.append(";");
            attrName.append(o);
          }

          for (AttributeValue v : a.getValues())
          {
            StringBuilder attrLine = new StringBuilder();
            attrLine.append(attrName);
            appendLDIFSeparatorAndValue(attrLine, v.getValueBytes());
            writeLDIFLine(attrLine, writer, wrapLines, wrapColumn);
          }
        }
      }
    }


    // Make sure there is a blank line after the entry.
    writer.newLine();
  }



  /**
   * Writes a modify change record with the provided information.  No filtering
   * will be performed, nor will any export plugins be invoked.
   *
   * @param  dn             The DN of the entry being modified.  It must not be
   *                        <CODE>null</CODE>.
   * @param  modifications  The set of modifications to include in the change
   *                        record.  It must not be <CODE>null</CODE>.
   *
   * @throws  IOException  If a problem occurs while writing the modify record.
   */
  public void writeModifyChangeRecord(DN dn, List<Modification> modifications)
         throws IOException
  {
    assert debugEnter(CLASS_NAME, "writeModifyChangeRecord", String.valueOf(dn),
                      String.valueOf(modifications));

    ensureNotNull(dn, modifications);

    // If there aren't any modifications, then there's nothing to do.
    if (modifications.isEmpty())
    {
      return;
    }


    // Get the information necessary to write the LDIF.
    BufferedWriter writer     = exportConfig.getWriter();
    int            wrapColumn = exportConfig.getWrapColumn();
    boolean        wrapLines  = (wrapColumn > 1);


    // Write the DN and changetype.
    StringBuilder dnLine = new StringBuilder();
    dnLine.append("dn");
    appendLDIFSeparatorAndValue(dnLine, getBytes(dn.toString()));
    writeLDIFLine(dnLine, writer, wrapLines, wrapColumn);

    StringBuilder changeTypeLine = new StringBuilder("changetype: modify");
    writeLDIFLine(changeTypeLine, writer, wrapLines, wrapColumn);


    // Iterate through the modifications and write them to the LDIF.
    Iterator<Modification> iterator = modifications.iterator();
    while (iterator.hasNext())
    {
      Modification m    = iterator.next();
      Attribute    a    = m.getAttribute();

      StringBuilder nameBuffer = new StringBuilder(a.getName());
      for (String o : a.getOptions())
      {
        nameBuffer.append(";");
        nameBuffer.append(o);
      }
      String  name = nameBuffer.toString();

      StringBuilder modTypeLine = new StringBuilder();
      switch (m.getModificationType())
      {
        case ADD:
          modTypeLine.append("add: ");
          modTypeLine.append(name);
          break;
        case DELETE:
          modTypeLine.append("delete: ");
          modTypeLine.append(name);
          break;
        case REPLACE:
          modTypeLine.append("replace: ");
          modTypeLine.append(name);
          break;
        case INCREMENT:
          modTypeLine.append("increment: ");
          modTypeLine.append(name);
          break;
        default:
          // We have no idea what the changetype is, so we can't write anything.
          continue;
      }
      writeLDIFLine(modTypeLine, writer, wrapLines, wrapColumn);

      for (AttributeValue v : a.getValues())
      {
        StringBuilder valueLine = new StringBuilder();
        valueLine.append(name);
        appendLDIFSeparatorAndValue(valueLine, v.getValueBytes());
        writeLDIFLine(valueLine, writer, wrapLines, wrapColumn);
      }


      // If this is the last modification, then append blank line.  Otherwise
      // write a line with just a dash.
      if (iterator.hasNext())
      {
        writer.write("-");
        writer.newLine();
      }
      else
      {
        writer.newLine();
      }
    }
  }



  /**
   * Writes a modify DN change record with the provided information.  No
   * filtering will be performed, nor will any export plugins be invoked.
   *
   * @param  dn            The DN of the entry before the rename.  It must not
   *                       be <CODE>null</CODE>.
   * @param  newRDN        The new RDN for the entry.  It must not be
   *                       <CODE>null</CODE>.
   * @param  deleteOldRDN  Indicates whether the old RDN value should be removed
   *                       from the entry.
   * @param  newSuperior   The new superior DN for the entry, or
   *                       <CODE>null</CODE> if the entry will stay below the
   *                       same parent.
   *
   * @throws  IOException  If a problem occurs while writing the modify record.
   */
  public void writeModifyDNChangeRecord(DN dn, RDN newRDN, boolean deleteOldRDN,
                                        DN newSuperior)
         throws IOException
  {
    assert debugEnter(CLASS_NAME, "writeModifyDNChangeRecord",
                      String.valueOf(dn), String.valueOf(newRDN),
                      String.valueOf(deleteOldRDN),
                      String.valueOf(newSuperior));

    ensureNotNull(dn, newRDN);


    // Get the information necessary to write the LDIF.
    BufferedWriter writer     = exportConfig.getWriter();
    int            wrapColumn = exportConfig.getWrapColumn();
    boolean        wrapLines  = (wrapColumn > 1);


    // Write the current DN.
    StringBuilder dnLine = new StringBuilder();
    dnLine.append("dn");
    appendLDIFSeparatorAndValue(dnLine, getBytes(dn.toString()));
    writeLDIFLine(dnLine, writer, wrapLines, wrapColumn);


    // Write the changetype.  Some older tools may not support the "moddn"
    // changetype, so only use it if a newSuperior element has been provided,
    // but use modrdn elsewhere.
    if (newSuperior == null)
    {
      StringBuilder changeTypeLine = new StringBuilder("changetype: modrdn");
      writeLDIFLine(changeTypeLine, writer, wrapLines, wrapColumn);
    }
    else
    {
      StringBuilder changeTypeLine = new StringBuilder("changetype: moddn");
      writeLDIFLine(changeTypeLine, writer, wrapLines, wrapColumn);
    }


    // Write the newRDN element.
    StringBuilder rdnLine = new StringBuilder();
    rdnLine.append("newrdn");
    appendLDIFSeparatorAndValue(rdnLine, getBytes(newRDN.toString()));
    writeLDIFLine(rdnLine, writer, wrapLines, wrapColumn);


    // Write the deleteOldRDN element.
    StringBuilder deleteOldRDNLine = new StringBuilder();
    deleteOldRDNLine.append("deleteoldrdn: ");
    deleteOldRDNLine.append(deleteOldRDN ? "1" : "0");
    writeLDIFLine(deleteOldRDNLine, writer, wrapLines, wrapColumn);

    if (newSuperior != null)
    {
      StringBuilder newSuperiorLine = new StringBuilder();
      newSuperiorLine.append("newsuperior");
      appendLDIFSeparatorAndValue(newSuperiorLine,
                                  getBytes(newSuperior.toString()));
      writeLDIFLine(newSuperiorLine, writer, wrapLines, wrapColumn);
    }


    // Make sure there is a blank line after the entry.
    writer.newLine();
  }



  /**
   * Flushes the data written to the output stream or underlying file.
   *
   * @throws  IOException  If a problem occurs while flushing the output.
   */
  public void flush()
         throws IOException
  {
    assert debugEnter(CLASS_NAME, "flush");

    writer.flush();
  }



  /**
   * Closes the LDIF writer and the underlying output stream or file.
   *
   * @throws  IOException  If a problem occurs while closing the writer.
   */
  public void close()
         throws IOException
  {
    assert debugEnter(CLASS_NAME, "close");

    writer.flush();
    writer.close();
  }



  /**
   * Appends an LDIF separator and properly-encoded form of the given
   * value to the provided buffer.  If the value is safe to include
   * as-is, then a single colon, a single space, space, and the
   * provided value will be appended.  Otherwise, two colons, a single
   * space, and a base64-encoded form of the value will be appended.
   *
   * @param  buffer      The buffer to which the information should be
   *                     appended.  It must not be <CODE>null</CODE>.
   * @param  valueBytes  The value to append to the buffer.  It must not be
   *                     <CODE>null</CODE>.
   */
  public static void appendLDIFSeparatorAndValue(StringBuilder buffer,
                                                 byte[] valueBytes)
  {
    assert debugEnter(CLASS_NAME, "appendLDIFSeparatorAndValue",
                      "java.lang.StringBuilder",
                      String.valueOf(valueBytes));

    ensureNotNull(buffer, valueBytes);


    // If the value is empty, then just append a single colon and a single
    // space.
    if ((valueBytes == null) || (valueBytes.length == 0))
    {
      buffer.append(": ");
      return;
    }


    if (needsBase64Encoding(valueBytes))
    {
      buffer.append(":: ");
      buffer.append(Base64.encode(valueBytes));
    }
    else
    {
      buffer.append(": ");

      try
      {
        buffer.append(new String(valueBytes, "UTF-8"));
      }
      catch (Exception e)
      {
        // This should never happen.
        assert debugException(CLASS_NAME, "appendLDIFSeparatorAndValue", e);
        buffer.append(new String(valueBytes));
      }
    }
  }



  /**
   * Writes the provided line to LDIF using the provided information.
   *
   * @param  line        The line of information to write.  It must not be
   *                     <CODE>null</CODE>.
   * @param  writer      The writer to which the data should be written.  It
   *                     must not be <CODE>null</CODE>.
   * @param  wrapLines   Indicates whether to wrap long lines.
   * @param  wrapColumn  The column at which long lines should be wrapped.
   *
   * @throws  IOException  If a problem occurs while writing the information.
   */
  public static void writeLDIFLine(StringBuilder line, BufferedWriter writer,
                                   boolean wrapLines, int wrapColumn)
          throws IOException
  {
    assert debugEnter(CLASS_NAME, "writeLDIFLine", String.valueOf(line),
                      String.valueOf(writer), String.valueOf(wrapLines),
                      String.valueOf(wrapColumn));

    ensureNotNull(line, writer);

    int length = line.length();
    if (wrapLines && (length > wrapColumn))
    {
      writer.write(line.substring(0, wrapColumn));
      writer.newLine();

      int pos = wrapColumn;
      while (pos < length)
      {
        int writeLength = Math.min(wrapColumn-1, length-pos);
        writer.write(' ');
        writer.write(line.substring(pos, pos+writeLength));
        writer.newLine();

        pos += wrapColumn-1;
      }
    }
    else
    {
      writer.write(line.toString());
      writer.newLine();
    }
  }
}

