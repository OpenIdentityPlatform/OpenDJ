/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2016 ForgeRock AS
 */
package org.opends.server.schema;

import static org.opends.server.schema.SchemaConstants.*;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.opends.server.admin.std.server.AttributeSyntaxCfg;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.core.ServerContext;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Schema.SchemaUpdater;

/**
 * This class implements the access control information (aci) attribute syntax.
 */
public class AciSyntax
       extends AttributeSyntax<AttributeSyntaxCfg>
{
  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public AciSyntax()
  {
    super();
  }

  @Override
  public void initializeSyntax(AttributeSyntaxCfg configuration, ServerContext serverContext)
      throws ConfigException, DirectoryException
  {
    // Add the Aci syntax to the "new" schema
    serverContext.getSchema().updateSchema(new SchemaUpdater()
    {
      @Override
      public Schema update(SchemaBuilder builder)
      {
        return addAciSyntax(builder).toSchema();
      }
    });
  }

  /**
   * Adds the ACI syntax to the provided schema builder.
   *
   * @param builder
   *          where to add the ACI syntax
   * @return the provided builder
   */
  public static SchemaBuilder addAciSyntax(SchemaBuilder builder)
  {
    return builder
        .buildSyntax(SYNTAX_ACI_OID)
        .description(SYNTAX_ACI_DESCRIPTION)
        .implementation(new AciSyntaxImpl())
        .addToSchema();
  }

  @Override
  public Syntax getSDKSyntax(org.forgerock.opendj.ldap.schema.Schema schema)
  {
    return schema.getSyntax(SchemaConstants.SYNTAX_ACI_OID);
  }

  @Override
  public String getName()
  {
    return SYNTAX_ACI_NAME;
  }

  @Override
  public String getOID()
  {
    return SYNTAX_ACI_OID;
  }

  @Override
  public String getDescription()
  {
    return SYNTAX_ACI_DESCRIPTION;
  }
}

