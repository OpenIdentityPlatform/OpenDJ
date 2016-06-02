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

import static org.opends.messages.ConfigMessages.*;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.opends.server.config.ConfigConstants;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SchemaConfigManager;
import org.opends.server.schema.SchemaConstants;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Schema;

import com.forgerock.opendj.util.OperatingSystem;

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

  /** Constructor. */
  public SchemaLoader()
  {
    Schema sc = DirectoryServer.getSchema();
    for (String name : OBJECTCLASS_TO_KEEP)
    {
      ObjectClass oc = sc.getObjectClass(name);
      if (!oc.isPlaceHolder())
      {
        objectclassesToKeep.add(oc);
      }
    }
    for (String name : ATTRIBUTES_TO_KEEP)
    {
      if (sc.hasAttributeType(name))
      {
        attributesToKeep.add(sc.getAttributeType(name));
      }
    }
    matchingRulesToKeep.addAll(sc.getMatchingRules());
    syntaxesToKeep.addAll(sc.getSyntaxes());
  }

  private static String getSchemaDirectoryPath()
  {
    File schemaDir = DirectoryServer.getEnvironmentConfig().getSchemaDirectory();
    return schemaDir != null ? schemaDir.getAbsolutePath() : null;
  }

  /**
   * Reads the schema.
   *
   * @throws ConfigException
   *           if an error occurs reading the schema.
   * @throws InitializationException
   *           if an error occurs trying to find out the schema files.
   * @throws DirectoryException
   *           if there is an error registering the minimal objectclasses.
   */
  public void readSchema() throws DirectoryException, ConfigException, InitializationException
  {
    schema = getBaseSchema();

    List<String> fileNames;
    String schemaDirPath = getSchemaDirectoryPath();
    try
    {
      // Load install directory schema
      File schemaDir = new File(schemaDirPath);
      if (schemaDirPath == null || !schemaDir.exists())
      {
        LocalizableMessage message = ERR_CONFIG_SCHEMA_NO_SCHEMA_DIR.get(schemaDirPath);
        throw new InitializationException(message);
      }
      else if (!schemaDir.isDirectory())
      {
        LocalizableMessage message = ERR_CONFIG_SCHEMA_DIR_NOT_DIRECTORY.get(schemaDirPath);
        throw new InitializationException(message);
      }
      FileFilter ldifFilesFilter = new FileFilter()
      {
        @Override
        public boolean accept(File f)
        {
          if (f != null)
          {
            if (f.isDirectory())
            {
              return true;
            }
            return OperatingSystem.isWindows() ? f.getName().toLowerCase().endsWith(".ldif")
                                               : f.getName().endsWith(".ldif");
          }
          return false;
        }
      };
      File[] schemaFiles = schemaDir.listFiles(ldifFilesFilter);
      fileNames = new ArrayList<>(schemaFiles.length);
      for (File f : schemaFiles)
      {
        if (f.isFile())
        {
          fileNames.add(f.getName());
        }
      }

      Collections.sort(fileNames);
    }
    catch (InitializationException ie)
    {
      throw ie;
    }
    catch (Exception e)
    {
      throw new InitializationException(ERR_CONFIG_SCHEMA_CANNOT_LIST_FILES.get(schemaDirPath, e.getMessage()), e);
    }

    // Iterate through the schema files and read them as an LDIF file containing a single entry.
    // Then get the attributeTypes and objectClasses attributes from that entry
    // and parse them to initialize the server schema.
    for (String schemaFile : fileNames)
    {
      SchemaConfigManager.loadSchemaFile(schema, schemaFile);
    }
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
      return new Schema(builder.toSchema());
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
