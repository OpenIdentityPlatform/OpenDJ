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



import java.util.List;

import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeType;
import org.opends.server.types.InitializationException;

import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a tag that is used to base presence of one attribute on
 * the presence of another attribute and/or attribute value.
 */
public class IfPresentTag
       extends Tag
{
  // The attribute type for which to make the determination.
  private AttributeType attributeType;

  // The value for which to make the determination.
  private String assertionValue;



  /**
   * Creates a new instance of this ifpresent tag.
   */
  public IfPresentTag()
  {
    attributeType  = null;
    assertionValue = null;
  }



  /**
   * Retrieves the name for this tag.
   *
   * @return  The name for this tag.
   */
  public String getName()
  {
    return "IfPresent";
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
    if ((arguments.length < 1) || (arguments.length > 2))
    {
      int    msgID   = MSGID_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT;
      String message = getMessage(msgID, getName(), lineNumber, 1, 2,
                                  arguments.length);
      throw new InitializationException(msgID, message);
    }

    String lowerName = toLowerCase(arguments[0]);
    AttributeType t = DirectoryServer.getAttributeType(lowerName, true);
    if (! branch.hasAttribute(t))
    {
      int    msgID   = MSGID_MAKELDIF_TAG_UNDEFINED_ATTRIBUTE;
      String message = getMessage(msgID, arguments[0], lineNumber);
      throw new InitializationException(msgID, message);
    }

    if (arguments.length == 2)
    {
      assertionValue = arguments[1];
    }
    else
    {
      assertionValue = null;
    }
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
    if ((arguments.length < 1) || (arguments.length > 2))
    {
      int    msgID   = MSGID_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT;
      String message = getMessage(msgID, getName(), lineNumber, 1, 2,
                                  arguments.length);
      throw new InitializationException(msgID, message);
    }

    String lowerName = toLowerCase(arguments[0]);
    AttributeType t = DirectoryServer.getAttributeType(lowerName, true);
    if (! template.hasAttribute(t))
    {
      int    msgID   = MSGID_MAKELDIF_TAG_UNDEFINED_ATTRIBUTE;
      String message = getMessage(msgID, arguments[0], lineNumber);
      throw new InitializationException(msgID, message);
    }

    if (arguments.length == 2)
    {
      assertionValue = arguments[1];
    }
    else
    {
      assertionValue = null;
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
    List<TemplateValue> values = templateEntry.getValues(attributeType);
    if ((values == null) || values.isEmpty())
    {
      return TagResult.OMIT_FROM_ENTRY;
    }

    if (assertionValue == null)
    {
      return TagResult.SUCCESS_RESULT;
    }
    else
    {
      for (TemplateValue v : values)
      {
        if (assertionValue.equals(v.getValue().toString()))
        {
          return TagResult.SUCCESS_RESULT;
        }
      }

      return TagResult.OMIT_FROM_ENTRY;
    }
  }
}

