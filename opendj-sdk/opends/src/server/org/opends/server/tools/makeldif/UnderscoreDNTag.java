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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.tools.makeldif;



import java.util.List;

import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;

import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;



/**
 * This class defines a tag that is used to include the DN of the current entry
 * in the attribute value, with underscores in place of the commas.
 */
public class UnderscoreDNTag
       extends Tag
{
  // The number of DN components to include.
  private int numComponents;



  /**
   * Creates a new instance of this DN tag.
   */
  public UnderscoreDNTag()
  {
    numComponents = 0;
  }



  /**
   * Retrieves the name for this tag.
   *
   * @return  The name for this tag.
   */
  public String getName()
  {
    return "_DN";
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
    initializeInternal(templateFile, arguments, lineNumber);
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
    initializeInternal(templateFile, arguments, lineNumber);
  }



  /**
   * Performs any initialization for this tag that may be needed for this tag.
   *
   * @param  templateFile  The template file in which this tag is used.
   * @param  arguments     The set of arguments provided for this tag.
   * @param  lineNumber    The line number on which this tag appears in the
   *                       template file.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   this tag.
   */
  private void initializeInternal(TemplateFile templateFile, String[] arguments,
                                  int lineNumber)
          throws InitializationException
  {
    if (arguments.length == 0)
    {
      numComponents = 0;
    }
    else if (arguments.length == 1)
    {
      try
      {
        numComponents = Integer.parseInt(arguments[0]);
      }
      catch (NumberFormatException nfe)
      {
        int    msgID   = MSGID_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER;
        String message = getMessage(msgID, arguments[0], getName(), lineNumber);
        throw new InitializationException(msgID, message);
      }
    }
    else
    {
      int    msgID   = MSGID_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT;
      String message = getMessage(msgID, getName(), lineNumber, 0, 1,
                                  arguments.length);

      throw new InitializationException(msgID, message);
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
    DN dn = templateEntry.getDN();
    if ((dn == null) || dn.isNullDN())
    {
      return TagResult.SUCCESS_RESULT;
    }

    if (numComponents == 0)
    {
      dn.getRDN(0).toString(templateValue.getValue());
      for (int i=1; i < dn.getNumComponents(); i++)
      {
        templateValue.append("_");
        dn.getRDN(i).toString(templateValue.getValue());
      }
    }
    else if (numComponents > 0)
    {
      int count = Math.min(numComponents, dn.getNumComponents());

      dn.getRDN(0).toString(templateValue.getValue());
      for (int i = 1; i < count; i++)
      {
        templateValue.append("_");
        dn.getRDN(i).toString(templateValue.getValue());
      }
    }
    else
    {
      int sz = dn.getNumComponents();
      int count = Math.min(Math.abs(numComponents), sz);

      dn.getRDN(sz - count).toString(templateValue.getValue());
      for (int i = 1; i < count; i++) {
        templateValue.append("_");
        dn.getRDN(sz - count + i).toString(templateValue.getValue());
      }
    }

    return TagResult.SUCCESS_RESULT;
  }
}

