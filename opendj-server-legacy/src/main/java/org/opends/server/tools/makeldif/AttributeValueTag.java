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

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.InitializationException;

import static org.opends.messages.ToolMessages.*;

/**
 * This class defines a tag that is used to reference the value of a specified
 * attribute already defined in the entry.
 */
public class AttributeValueTag
       extends Tag
{
  /** The attribute type that specifies which value should be used. */
  private AttributeType attributeType;

  /** The maximum number of characters to include from the value. */
  private int numCharacters;



  /** Creates a new instance of this attribute value tag. */
  public AttributeValueTag()
  {
    attributeType = null;
    numCharacters = 0;
  }

  @Override
  public String getName()
  {
    return "AttributeValue";
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

    attributeType = DirectoryServer.getSchema().getAttributeType(arguments[0]);
    if (! branch.hasAttribute(attributeType))
    {
      LocalizableMessage message =
          ERR_MAKELDIF_TAG_UNDEFINED_ATTRIBUTE.get(arguments[0], lineNumber);
      throw new InitializationException(message);
    }

    if (arguments.length == 2)
    {
      try
      {
        numCharacters = Integer.parseInt(arguments[1]);
        if (numCharacters < 0)
        {
          LocalizableMessage message = ERR_MAKELDIF_TAG_INTEGER_BELOW_LOWER_BOUND.get(
              numCharacters, 0, getName(), lineNumber);
          throw new InitializationException(message);
        }
      }
      catch (NumberFormatException nfe)
      {
        LocalizableMessage message = ERR_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER.get(
            arguments[1], getName(), lineNumber);
        throw new InitializationException(message);
      }
    }
    else
    {
      numCharacters = 0;
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
      try
      {
        numCharacters = Integer.parseInt(arguments[1]);
        if (numCharacters < 0)
        {
          LocalizableMessage message = ERR_MAKELDIF_TAG_INTEGER_BELOW_LOWER_BOUND.get(
              numCharacters, 0, getName(), lineNumber);
          throw new InitializationException(message);
        }
      }
      catch (NumberFormatException nfe)
      {
        LocalizableMessage message = ERR_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER.get(
            arguments[1], getName(), lineNumber);
        throw new InitializationException(message);
      }
    }
    else
    {
      numCharacters = 0;
    }
  }

  @Override
  public TagResult generateValue(TemplateEntry templateEntry,
                                 TemplateValue templateValue)
  {
    TemplateValue v = templateEntry.getValue(attributeType);
    if (v == null)
    {
      // This is fine -- we just won't append anything.
      return TagResult.SUCCESS_RESULT;
    }

    if (numCharacters > 0)
    {
      String valueString = v.getValue().toString();
      if (valueString.length() > numCharacters)
      {
        templateValue.append(valueString.substring(0, numCharacters));
      }
      else
      {
        templateValue.append(valueString);
      }
    }
    else
    {
      templateValue.getValue().append(v.getValue());
    }

    return TagResult.SUCCESS_RESULT;
  }
}
