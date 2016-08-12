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
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.schema;

import static org.opends.server.schema.SchemaConstants.*;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.forgerock.opendj.server.config.server.AttributeSyntaxCfg;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.core.SchemaHandler.SchemaUpdater;
import org.opends.server.core.ServerContext;
import org.opends.server.types.DirectoryException;

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
    serverContext.getSchemaHandler().updateSchema(new SchemaUpdater()
    {
      @Override
      public void update(SchemaBuilder builder)
      {
        addAciSyntax(builder);
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

