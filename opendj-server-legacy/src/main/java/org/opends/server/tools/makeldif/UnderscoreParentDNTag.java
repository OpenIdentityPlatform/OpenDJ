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
import org.forgerock.opendj.ldap.DN;
import org.forgerock.util.Utils;
import org.opends.server.types.InitializationException;

import static org.opends.messages.ToolMessages.*;

/**
 * This class defines a tag that is used to include the DN of the parent entry
 * in the attribute value, with underscores in place of commas.
 */
public class UnderscoreParentDNTag
       extends Tag
{
  /** Creates a new instance of this underscore parent DN tag. */
  public UnderscoreParentDNTag()
  {
    // No implementation required.
  }

  @Override
  public String getName()
  {
    return "_ParentDN";
  }

  @Override
  public boolean allowedInBranch()
  {
    return false;
  }

  @Override
  public void initializeForTemplate(TemplateFile templateFile,
                                    Template template, String[] arguments,
                                    int lineNumber, List<LocalizableMessage> warnings)
         throws InitializationException
  {
    if (arguments.length != 0)
    {
      LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_COUNT.get(
          getName(), lineNumber, 0, arguments.length);
      throw new InitializationException(message);
    }
  }

  @Override
  public TagResult generateValue(TemplateEntry templateEntry,
                                 TemplateValue templateValue)
  {
    DN parentDN = templateEntry.getParentDN();
    if (parentDN != null && !parentDN.isRootDN())
    {
      Utils.joinAsString(templateValue.getValue(), "_", parentDN);
    }
    return TagResult.SUCCESS_RESULT;
  }
}
