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
package org.opends.server.tools.makeldif;



import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import org.opends.server.core.InitializationException;

import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;



/**
 * This class defines a tag that is used provide values from a text file.  The
 * file should have one value per line.  Access to the values may be either at
 * random or in sequential order.
 */
public class FileTag
       extends Tag
{
  // Indicates whether the values should be selected sequentially or at random.
  private boolean sequential;

  // The file containing the data.
  private File dataFile;

  // The index used for sequential access.
  private int nextIndex;

  // The random number generator for this tag.
  private Random random;

  // The array of lines read from the file.
  private String[] fileLines;



  /**
   * Creates a new instance of this file tag.
   */
  public FileTag()
  {
    sequential = false;
    dataFile   = null;
    nextIndex  = 0;
    random     = null;
    fileLines  = null;
  }



  /**
   * Retrieves the name for this tag.
   *
   * @return  The name for this tag.
   */
  public String getName()
  {
    return "File";
  }



  /**
   * Indicates whether this tag is allowed for use in the extra lines for
   * branches.
   *
   * @return  <CODE>true</CODE> if this tag may be used in branch definitions,
   *          or <CODE>false</CODE> if not.
   */
  public boolean allowedInBranch()
  {
    return true;
  }



  /**
   * Performs any initialization for this tag that may be needed while parsing
   * a branch definition.
   *
   * @param  templateFile  The template file in which this tag is used.
   * @param  branch        The branch in which this tag is used.
   * @param  arguments     The set of arguments provided for this tag.
   * @param  lineNumber    The line number on which this tag appears in the
   *                       template file.
   * @param  warnings      A list into which any appropriate warning messages
   *                       may be placed.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   this tag.
   */
  public void initializeForBranch(TemplateFile templateFile, Branch branch,
                                  String[] arguments, int lineNumber,
                                  List<String> warnings)
         throws InitializationException
  {
    initializeInternal(templateFile, arguments, lineNumber, warnings);
  }



  /**
   * Performs any initialization for this tag that may be needed while parsing
   * a template definition.
   *
   * @param  templateFile  The template file in which this tag is used.
   * @param  template      The template in which this tag is used.
   * @param  arguments     The set of arguments provided for this tag.
   * @param  lineNumber    The line number on which this tag appears in the
   *                       template file.
   * @param  warnings      A list into which any appropriate warning messages
   *                       may be placed.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   this tag.
   */
  public void initializeForTemplate(TemplateFile templateFile,
                                    Template template, String[] arguments,
                                    int lineNumber, List<String> warnings)
         throws InitializationException
  {
    initializeInternal(templateFile, arguments, lineNumber, warnings);
  }



  /**
   * Performs any initialization for this tag that may be needed.
   *
   * @param  templateFile  The template file in which this tag is used.
   * @param  arguments     The set of arguments provided for this tag.
   * @param  lineNumber    The line number on which this tag appears in the
   *                       template file.
   * @param  warnings      A list into which any appropriate warning messages
   *                       may be placed.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   this tag.
   */
  private void initializeInternal(TemplateFile templateFile, String[] arguments,
                                  int lineNumber, List<String> warnings)
          throws InitializationException
  {
    random = templateFile.getRandom();


    // There must be at least one argument, and possibly two.
    if ((arguments.length < 1) || (arguments.length > 2))
    {
      int    msgID   = MSGID_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT;
      String message = getMessage(msgID, getName(), lineNumber, 1, 2,
                                  arguments.length);
      throw new InitializationException(msgID, message);
    }


    // The first argument should be the path to the file.
    dataFile = templateFile.getFile(arguments[0]);
    if ((dataFile == null) || (! dataFile.exists()))
    {
      int    msgID   = MSGID_MAKELDIF_TAG_CANNOT_FIND_FILE;
      String message = getMessage(msgID, arguments[0], getName(), lineNumber);
      throw new InitializationException(msgID, message);
    }


    // If there is a second argument, then it should be either "sequential" or
    // "random".  If there isn't one, then we should assume "random".
    if (arguments.length == 2)
    {
      if (arguments[1].equalsIgnoreCase("sequential"))
      {
        sequential = true;
        nextIndex  = 0;
      }
      else if (arguments[1].equalsIgnoreCase("random"))
      {
        sequential = false;
      }
      else
      {
        int    msgID   = MSGID_MAKELDIF_TAG_INVALID_FILE_ACCESS_MODE;
        String message = getMessage(msgID, arguments[1], getName(), lineNumber);
        throw new InitializationException(msgID, message);
      }
    }
    else
    {
      sequential = false;
    }


    // See if the file has already been read into memory.  If not, then read it.
    try
    {
      fileLines = templateFile.getFileLines(dataFile);
    }
    catch (IOException ioe)
    {
      int    msgID   = MSGID_MAKELDIF_TAG_CANNOT_READ_FILE;
      String message = getMessage(msgID, arguments[0], getName(), lineNumber,
                                  String.valueOf(ioe));
      throw new InitializationException(msgID, message, ioe);
    }
  }



  /**
   * Generates the content for this tag by appending it to the provided tag.
   *
   * @param  templateEntry  The entry for which this tag is being generated.
   * @param  templateValue  The template value to which the generated content
   *                        should be appended.
   *
   * @return  The result of generating content for this tag.
   */
  public TagResult generateValue(TemplateEntry templateEntry,
                                 TemplateValue templateValue)
  {
    if (sequential)
    {
      templateValue.append(fileLines[nextIndex++]);
      if (nextIndex >= fileLines.length)
      {
        nextIndex = 0;
      }
    }
    else
    {
      templateValue.append(fileLines[random.nextInt(fileLines.length)]);
    }

    return TagResult.SUCCESS_RESULT;
  }
}

