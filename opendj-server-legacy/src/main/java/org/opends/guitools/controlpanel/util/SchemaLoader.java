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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.util;

import static org.opends.messages.ConfigMessages.*;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.server.config.ConfigConstants;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SchemaConfigManager;
import org.opends.server.schema.SchemaConstants;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ObjectClass;
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
      ObjectClass oc = sc.getObjectClass(name.toLowerCase());
      if (oc != null)
      {
        objectclassesToKeep.add(oc);
      }
    }
    for (String name : ATTRIBUTES_TO_KEEP)
    {
      AttributeType attr = sc.getAttributeType(name.toLowerCase());
      if (attr != null)
      {
        attributesToKeep.add(attr);
      }
    }
    matchingRulesToKeep.addAll(sc.getMatchingRules().values());
    syntaxesToKeep.addAll(sc.getSyntaxes().values());
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

    String[] fileNames;
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
      List<String> fileList = new ArrayList<>(schemaFiles.length);
      for (File f : schemaFiles)
      {
        if (f.isFile())
        {
          fileList.add(f.getName());
        }
      }

      fileNames = new String[fileList.size()];
      fileList.toArray(fileNames);
      Arrays.sort(fileNames);
    }
    catch (Exception e)
    {
      throw new InitializationException(ERR_CONFIG_SCHEMA_CANNOT_LIST_FILES.get(schemaDirPath, e.getMessage()), e);
    }

    //  Iterate through the schema files and read them as an LDIF file
    //  containing a single entry.  Then get the attributeTypes and
    //  objectClasses attributes from that entry and parse them to
    //  initialize the server schema.
    for (String schemaFile : fileNames)
    {
      // no server context to pass
      SchemaConfigManager.loadSchemaFile(null, schema, schemaFile);
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
    Schema schema = new Schema();
    for (MatchingRule mr : matchingRulesToKeep)
    {
      schema.registerMatchingRule(mr, true);
    }
    for (Syntax syntax : syntaxesToKeep)
    {
      schema.registerSyntax(syntax, true);
    }
    for (AttributeType attr : attributesToKeep)
    {
      schema.registerAttributeType(attr, true);
    }
    for (ObjectClass oc : objectclassesToKeep)
    {
      schema.registerObjectClass(oc, true);
    }
    return schema;
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
