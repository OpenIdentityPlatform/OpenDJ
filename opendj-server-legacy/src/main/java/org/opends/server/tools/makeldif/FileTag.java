/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.tools.makeldif;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.types.InitializationException;

import static org.opends.messages.ToolMessages.*;

/**
 * This class defines a tag that is used provide values from a text file.  The
 * file should have one value per line.  Access to the values may be either at
 * random or in sequential order.
 */
public class FileTag
       extends Tag
{
  /** Indicates whether the values should be selected sequentially or at random. */
  private boolean sequential;

  /** The file containing the data. */
  private File dataFile;

  /** The index used for sequential access. */
  private int nextIndex;

  /** The random number generator for this tag. */
  private Random random;

  /** The array of lines read from the file. */
  private String[] fileLines;

  /** Creates a new instance of this file tag. */
  public FileTag()
  {
    sequential = false;
    dataFile   = null;
    nextIndex  = 0;
    random     = null;
    fileLines  = null;
  }

  @Override
  public String getName()
  {
    return "File";
  }

  @Override
  public boolean allowedInBranch()
  {
    return true;
  }

  @Override
  public void initializeForBranch(TemplateFile templateFile, Branch branch,
                                  String[] arguments, int lineNumber,
                                  List<LocalizableMessage> warnings)
         throws InitializationException
  {
    initializeInternal(templateFile, arguments, lineNumber, warnings);
  }

  @Override
  public void initializeForTemplate(TemplateFile templateFile,
                                    Template template, String[] arguments,
                                    int lineNumber, List<LocalizableMessage> warnings)
         throws InitializationException
  {
    initializeInternal(templateFile, arguments, lineNumber, warnings);
  }

  private void initializeInternal(TemplateFile templateFile, String[] arguments,
                                  int lineNumber, List<LocalizableMessage> warnings)
          throws InitializationException
  {
    random = templateFile.getRandom();


    // There must be at least one argument, and possibly two.
    if (arguments.length < 1 || arguments.length > 2)
    {
      LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(
          getName(), lineNumber, 1, 2, arguments.length);
      throw new InitializationException(message);
    }


    // The first argument should be the path to the file.
    dataFile = templateFile.getFile(arguments[0]);
    if (dataFile == null || !dataFile.exists())
    {
      LocalizableMessage message = ERR_MAKELDIF_TAG_CANNOT_FIND_FILE.get(
          arguments[0], getName(), lineNumber);
      throw new InitializationException(message);
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
        LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_FILE_ACCESS_MODE.get(
            arguments[1], getName(), lineNumber);
        throw new InitializationException(message);
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
      LocalizableMessage message = ERR_MAKELDIF_TAG_CANNOT_READ_FILE.get(
          arguments[0], getName(), lineNumber, ioe);
      throw new InitializationException(message, ioe);
    }
  }

  @Override
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
