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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.util;

import static org.opends.messages.SchemaMessages.ERR_SCHEMA_HAS_WARNINGS;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.forgerock.util.Utils;
import org.opends.server.config.ConfigConstants;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.schema.SchemaConstants;
import org.opends.server.schema.SchemaHandler;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.util.SchemaUtils;
import org.opends.server.util.StaticUtils;

/** Class used to retrieve the schema from the schema files. */
public class SchemaLoader
{
  private Schema schema;
  private static final String[] ATTRIBUTES_TO_KEEP = {
    ConfigConstants.ATTR_ATTRIBUTE_TYPES_LC,
    ConfigConstants.ATTR_OBJECTCLASSES_LC,
    ConfigConstants.ATTR_NAME_FORMS_LC,
    ConfigConstants.ATTR_DIT_CONTENT_RULES_LC,
    ConfigConstants.ATTR_DIT_STRUCTURE_RULES_LC,
    ConfigConstants.ATTR_MATCHING_RULE_USE_LC };
  private static final String[] OBJECTCLASS_TO_KEEP = { SchemaConstants.TOP_OBJECTCLASS_NAME };

  private final List<ObjectClass> objectclassesToKeep = new ArrayList<>();
  private final List<AttributeType> attributesToKeep = new ArrayList<>();
  /** List of matching rules to keep in the schema. */
  protected final List<MatchingRule> matchingRulesToKeep = new ArrayList<>();
  /** List of attribute syntaxes to keep in the schema. */
  protected final List<Syntax> syntaxesToKeep = new ArrayList<>();

  private final ServerContext serverContext;

  /** Constructor. */
  public SchemaLoader()
  {
    serverContext = DirectoryServer.getInstance().getServerContext();
    Schema schema = serverContext.getSchemaHandler().getSchema();
    for (String name : OBJECTCLASS_TO_KEEP)
    {
      ObjectClass oc = schema.getObjectClass(name);
      if (!oc.isPlaceHolder())
      {
        objectclassesToKeep.add(oc);
      }
    }
    for (String name : ATTRIBUTES_TO_KEEP)
    {
      if (schema.hasAttributeType(name))
      {
        attributesToKeep.add(schema.getAttributeType(name));
      }
    }
    matchingRulesToKeep.addAll(schema.getMatchingRules());
    syntaxesToKeep.addAll(schema.getSyntaxes());
  }

  /**
   * Reads and returns the schema.
   *
   * @return the schema
   *
   * @throws ConfigException
   *           if an error occurs reading the schema.
   * @throws InitializationException
   *           if an error occurs trying to find out the schema files.
   * @throws DirectoryException
   *           if there is an error registering the minimal objectclasses.
   */
  public Schema readSchema() throws DirectoryException, ConfigException, InitializationException
  {
    SchemaHandler schemaHandler = serverContext.getSchemaHandler();
    final File schemaDir = schemaHandler.getSchemaDirectoryPath();
    final List<String> fileNames = StaticUtils.getFileNames(SchemaUtils.getSchemaFiles(schemaDir));

    // build the schema from schema files
    Schema baseSchema = getBaseSchema();
    SchemaBuilder schemaBuilder = new SchemaBuilder(baseSchema);
    for (String schemaFile : fileNames)
    {
      schemaHandler.loadSchemaFileIntoSchemaBuilder(new File(schemaDir, schemaFile), schemaBuilder, baseSchema);
    }
    return buildSchema(schemaBuilder);
  }

  Schema buildSchema(SchemaBuilder schemaBuilder) throws InitializationException
  {
    schema = schemaBuilder.toSchema();
    Collection<LocalizableMessage> warnings = schema.getWarnings();
    if (!warnings.isEmpty())
    {
      throw new InitializationException(
          ERR_SCHEMA_HAS_WARNINGS.get(warnings.size(), Utils.joinAsString("; ", warnings)));
    }
    return schema;
  }

  /**
   * Returns a basic version of the schema. The schema is created and contains
   * enough definitions for the schema to be loaded.
   *
   * @return a basic version of the schema.
   * @throws DirectoryException
   *           if there is an error registering the minimal objectclasses.
   */
  protected Schema getBaseSchema() throws DirectoryException
  {
    try
    {
      SchemaBuilder builder = new SchemaBuilder(org.forgerock.opendj.ldap.schema.Schema.getDefaultSchema());
      for (Syntax syntax : syntaxesToKeep)
      {
        builder.buildSyntax(syntax).addToSchemaOverwrite();
      }
      for (MatchingRule mr : matchingRulesToKeep)
      {
        builder.buildMatchingRule(mr).addToSchemaOverwrite();
      }
      for (AttributeType attr : attributesToKeep)
      {
        builder.buildAttributeType(attr).addToSchemaOverwrite();
      }
      for (ObjectClass oc : objectclassesToKeep)
      {
        builder.buildObjectClass(oc).addToSchemaOverwrite();
      }
      return builder.toSchema();
    }
    catch (LocalizedIllegalArgumentException e)
    {
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, e.getMessageObject(), e);
    }
  }

  /**
   * Returns the schema that was read.
   *
   * @return the schema that was read.
   */
  public Schema getSchema()
  {
    return schema;
  }
}
