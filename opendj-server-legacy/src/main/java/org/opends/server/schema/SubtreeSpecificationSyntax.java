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
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.schema;

import static org.opends.server.schema.SchemaConstants.*;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.forgerock.opendj.server.config.server.AttributeSyntaxCfg;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.core.ServerContext;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.schema.SchemaHandler.SchemaUpdater;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.SubtreeSpecification;

/**
 * This class defines the subtree specification attribute syntax,
 * which is used to specify the scope of sub-entries (RFC 3672).
 */
public final class SubtreeSpecificationSyntax
       extends AttributeSyntax<AttributeSyntaxCfg>
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Creates a new instance of this syntax. Note that the only thing
   * that should be done here is to invoke the default constructor for
   * the superclass. All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public SubtreeSpecificationSyntax() {
    // No implementation required.
  }

  @Override
  public void initializeSyntax(AttributeSyntaxCfg configuration, ServerContext serverContext)
      throws ConfigException, DirectoryException
  {
    // Add the subtree specification syntax to the "new" schema
    serverContext.getSchemaHandler().updateSchema(new SchemaUpdater()
    {
      @Override
      public void update(SchemaBuilder builder)
      {
        addSubtreeSpecificationSyntax(builder);
      }
    });
  }

  /**
   * Adds the subtree specification syntax to the provided schema builder.
   *
   * @param builder
   *          where to add the subtree specification syntax
   * @return the provided builder
   */
  public static SchemaBuilder addSubtreeSpecificationSyntax(SchemaBuilder builder)
  {
    return builder
        .buildSyntax(SYNTAX_SUBTREE_SPECIFICATION_OID)
        .description(SYNTAX_SUBTREE_SPECIFICATION_DESCRIPTION)
        .implementation(new SubtreeSpecificationSyntaxImpl())
        .addToSchema();
  }

  @Override
  public Syntax getSDKSyntax(Schema schema)
  {
    return schema.getSyntax(SchemaConstants.SYNTAX_SUBTREE_SPECIFICATION_OID);
  }

  @Override
  public String getName() {
    return SYNTAX_SUBTREE_SPECIFICATION_NAME;
  }

  @Override
  public String getOID() {
    return SYNTAX_SUBTREE_SPECIFICATION_OID;
  }

  @Override
  public String getDescription() {
    return SYNTAX_SUBTREE_SPECIFICATION_DESCRIPTION;
  }

  @Override
  public boolean valueIsAcceptable(ByteSequence value,
                                   LocalizableMessageBuilder invalidReason) {
    // Use the subtree specification code to make this determination.
    try {
      SubtreeSpecification.valueOf(DN.rootDN(), value.toString());

      return true;
    } catch (DirectoryException e) {
      logger.traceException(e);

      invalidReason.append(e.getMessageObject());
      return false;
    }
  }

  @Override
  public boolean isBEREncodingRequired()
  {
    return false;
  }

  @Override
  public boolean isHumanReadable()
  {
    return true;
  }
}
