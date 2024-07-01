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
import org.forgerock.i18n.LocalizableMessage;



import java.util.List;

import org.opends.server.types.InitializationException;

import static org.opends.messages.ToolMessages.*;



/**
 * This class defines a tag that is used to hold static text (i.e., text that
 * appears outside of any tag).
 */
public class StaticTextTag
       extends Tag
{
  /** The static text to include in the LDIF. */
  private String text;



  /** Creates a new instance of this static text tag. */
  public StaticTextTag()
  {
    text = "";
  }



  /**
   * Retrieves the name for this tag.
   *
   * @return  The name for this tag.
   */
  @Override
  public String getName()
  {
    return "StaticText";
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
    if (arguments.length != 1)
    {
      LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_COUNT.get(
          getName(), lineNumber, 1, arguments.length);
      throw new InitializationException(message);
    }

    text = arguments[0];
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
    if (arguments.length != 1)
    {
      LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_COUNT.get(
          getName(), lineNumber, 1, arguments.length);
      throw new InitializationException(message);
    }

    text = arguments[0];
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
    templateValue.append(text);
    return TagResult.SUCCESS_RESULT;
  }
}

