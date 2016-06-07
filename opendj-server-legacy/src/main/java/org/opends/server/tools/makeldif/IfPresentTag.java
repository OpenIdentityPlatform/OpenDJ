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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.tools.makeldif;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.InitializationException;

import static org.opends.messages.ToolMessages.*;

/**
 * This class defines a tag that is used to base presence of one attribute on
 * the presence of another attribute and/or attribute value.
 */
public class IfPresentTag
       extends Tag
{
  /** The attribute type for which to make the determination. */
  private AttributeType attributeType;

  /** The value for which to make the determination. */
  private String assertionValue;



  /** Creates a new instance of this ifpresent tag. */
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
  @Override
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
    if (arguments.length < 1 || arguments.length > 2)
    {
      LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(
          getName(), lineNumber, 1, 2, arguments.length);
      throw new InitializationException(message);
    }

    AttributeType t = DirectoryServer.getSchema().getAttributeType(arguments[0]);
    if (! branch.hasAttribute(t))
    {
      LocalizableMessage message =
          ERR_MAKELDIF_TAG_UNDEFINED_ATTRIBUTE.get(arguments[0], lineNumber);
      throw new InitializationException(message);
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
  @Override
  public void initializeForTemplate(TemplateFile templateFile,
                                    Template template, String[] arguments,
                                    int lineNumber, List<LocalizableMessage> warnings)
         throws InitializationException
  {
    if (arguments.length < 1 || arguments.length > 2)
    {
      LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(
          getName(), lineNumber, 1, 2, arguments.length);
      throw new InitializationException(message);
    }

    attributeType = DirectoryServer.getSchema().getAttributeType(arguments[0]);
    if (! template.hasAttribute(attributeType))
    {
      LocalizableMessage message =
          ERR_MAKELDIF_TAG_UNDEFINED_ATTRIBUTE.get(arguments[0], lineNumber);
      throw new InitializationException(message);
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
  @Override
  public TagResult generateValue(TemplateEntry templateEntry,
                                 TemplateValue templateValue)
  {
    List<TemplateValue> values = templateEntry.getValues(attributeType);
    if (values == null || values.isEmpty())
    {
      return TagResult.OMIT_FROM_ENTRY;
    }

    if (assertionValue == null)
    {
      return TagResult.SUCCESS_RESULT;
    }

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
