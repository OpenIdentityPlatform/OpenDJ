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
 * the absence of another attribute and/or attribute value.
 */
public class IfAbsentTag
       extends Tag
{
  /** The attribute type for which to make the determination. */
  private AttributeType attributeType;

  /** The value for which to make the determination. */
  private String assertionValue;



  /** Creates a new instance of this ifabsent tag. */
  public IfAbsentTag()
  {
    attributeType  = null;
    assertionValue = null;
  }

  @Override
  public String getName()
  {
    return "IfAbsent";
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

  @Override
  public TagResult generateValue(TemplateEntry templateEntry,
                                 TemplateValue templateValue)
  {
    List<TemplateValue> values = templateEntry.getValues(attributeType);
    if (values == null || values.isEmpty())
    {
      return TagResult.SUCCESS_RESULT;
    }

    if (assertionValue == null)
    {
      return TagResult.OMIT_FROM_ENTRY;
    }

    for (TemplateValue v : values)
    {
      if (assertionValue.equals(v.getValue().toString()))
      {
        return TagResult.OMIT_FROM_ENTRY;
      }
    }
    return TagResult.SUCCESS_RESULT;
  }
}
