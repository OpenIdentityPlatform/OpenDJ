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

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.makeldif.DNTagUtils.*;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.InitializationException;

/**
 * This class defines a tag that is used to include the DN of the current entry
 * in the attribute value.
 */
public class DNTag
       extends Tag
{
  /** The number of DN components to include. */
  private int numComponents;

  /** Creates a new instance of this DN tag. */
  public DNTag()
  {
    numComponents = 0;
  }

  @Override
  public String getName()
  {
    return "DN";
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
    initializeInternal(arguments, lineNumber);
  }

  @Override
  public void initializeForTemplate(TemplateFile templateFile,
                                    Template template, String[] arguments,
                                    int lineNumber, List<LocalizableMessage> warnings)
         throws InitializationException
  {
    initializeInternal(arguments, lineNumber);
  }

  private void initializeInternal(String[] arguments, int lineNumber) throws InitializationException
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
        LocalizableMessage message = ERR_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER.get(
            arguments[0], getName(), lineNumber);
        throw new InitializationException(message);
      }
    }
    else
    {
      LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(
          getName(), lineNumber, 0, 1, arguments.length);
      throw new InitializationException(message);
    }
  }

  @Override
  public TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue)
  {
    DN dn = templateEntry.getDN();
    if (dn != null && !dn.isRootDN())
    {
      templateValue.getValue().append(generateDNKeepingRDNs(dn, numComponents));
    }
    return TagResult.SUCCESS_RESULT;
  }
}
