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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.tools.makeldif;



import java.util.List;
import java.util.Random;

import org.opends.server.types.InitializationException;

import static org.opends.messages.ToolMessages.*;
import org.forgerock.i18n.LocalizableMessage;


/**
 * This class defines a tag that may be used to select a value from a
 * pre-defined list, optionally defining weights for each value that can impact
 * the likelihood of a given item being selected.  The itemts to include in the
 * list should be specified as arguments to the tag.  If the argument ends with
 * a semicolon followed by an integer, then that will be the weight for that
 * particular item.  If no weight is given, then the weight for that item will
 * be assumed to be one.
 */
public class ListTag
       extends Tag
{
  /** The ultimate cumulative weight. */
  private int cumulativeWeight;

  /** The set of cumulative weights for the list items. */
  private int[] valueWeights;

  /** The set of values in the list. */
  private String[] valueStrings;

  /** The random number generator for this tag. */
  private Random random;



  /** Creates a new instance of this list tag. */
  public ListTag()
  {
    // No implementation required.
  }



  /**
   * Retrieves the name for this tag.
   *
   * @return  The name for this tag.
   */
  @Override
  public String getName()
  {
    return "List";
  }



  /**
   * Indicates whether this tag is allowed for use in the extra lines for
   * branches.
   *
   * @return  <CODE>true</CODE> if this tag may be used in branch definitions,
   *          or <CODE>false</CODE> if not.
   */
  @Override
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
  @Override
  public void initializeForBranch(TemplateFile templateFile, Branch branch,
                                  String[] arguments, int lineNumber,
                                  List<LocalizableMessage> warnings)
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
  @Override
  public void initializeForTemplate(TemplateFile templateFile,
                                    Template template, String[] arguments,
                                    int lineNumber, List<LocalizableMessage> warnings)
         throws InitializationException
  {
    initializeInternal(templateFile, arguments, lineNumber, warnings);
  }



  /**
   * Performs any initialization for this tag that may be needed for this tag.
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
                                  int lineNumber, List<LocalizableMessage> warnings)
          throws InitializationException
  {
    if (arguments.length == 0)
    {
      throw new InitializationException(
              ERR_MAKELDIF_TAG_LIST_NO_ARGUMENTS.get(lineNumber));
    }


    valueStrings     = new String[arguments.length];
    valueWeights     = new int[arguments.length];
    cumulativeWeight = 0;
    random           = templateFile.getRandom();

    for (int i=0; i < arguments.length; i++)
    {
      String s = arguments[i];

      int weight = 1;
      int semicolonPos = s.lastIndexOf(';');
      if (semicolonPos >= 0)
      {
        try
        {
          weight = Integer.parseInt(s.substring(semicolonPos+1));
          s = s.substring(0, semicolonPos);
        }
        catch (Exception e)
        {
          warnings.add(WARN_MAKELDIF_TAG_LIST_INVALID_WEIGHT.get(
                          lineNumber,s));
        }
      }

      cumulativeWeight += weight;
      valueStrings[i] = s;
      valueWeights[i] = cumulativeWeight;
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
  @Override
  public TagResult generateValue(TemplateEntry templateEntry,
                                 TemplateValue templateValue)
  {
    int selectedWeight = random.nextInt(cumulativeWeight) + 1;
    for (int i=0; i < valueWeights.length; i++)
    {
      if (selectedWeight <= valueWeights[i])
      {
        templateValue.getValue().append(valueStrings[i]);
        break;
      }
    }


    return TagResult.SUCCESS_RESULT;
  }
}

