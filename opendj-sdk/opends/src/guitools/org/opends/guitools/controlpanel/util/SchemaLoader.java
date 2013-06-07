/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.util;

import static org.opends.messages.ConfigMessages.*;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;

import org.opends.messages.Message;
import org.opends.quicksetup.util.Utils;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.MatchingRule;
import org.opends.server.config.ConfigConstants;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SchemaConfigManager;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.Schema;

/**
 * Class used to retrieve the schema from the schema files.
 *
 */
public class SchemaLoader
{
  private Schema schema;
  private final String[] attrsToKeep = {
      ConfigConstants.ATTR_ATTRIBUTE_TYPES_LC,
      ConfigConstants.ATTR_OBJECTCLASSES_LC,
      ConfigConstants.ATTR_NAME_FORMS_LC,
      ConfigConstants.ATTR_DIT_CONTENT_RULES_LC,
      ConfigConstants.ATTR_DIT_STRUCTURE_RULES_LC,
      ConfigConstants.ATTR_MATCHING_RULE_USE_LC};
  private final String[] ocsToKeep = {"top"};

  private final ArrayList<ObjectClass> objectclassesToKeep =
    new ArrayList<ObjectClass>();
  private final ArrayList<AttributeType> attributesToKeep =
    new ArrayList<AttributeType>();
  private final ArrayList<MatchingRule> matchingRulesToKeep =
    new ArrayList<MatchingRule>();
  private final ArrayList<AttributeSyntax<?>> syntaxesToKeep =
    new ArrayList<AttributeSyntax<?>>();

  /**
   * Constructor.
   *
   */
  public SchemaLoader()
  {
    Schema sc = DirectoryServer.getSchema();
    for (String name : ocsToKeep)
    {
      ObjectClass oc = sc.getObjectClass(name.toLowerCase());
      if (oc != null)
      {
        objectclassesToKeep.add(oc);
      }
    }
    for (String name : attrsToKeep)
    {
      AttributeType attr = sc.getAttributeType(name.toLowerCase());
      if (attr != null)
      {
        attributesToKeep.add(attr);
      }
    }
    for (MatchingRule mr : sc.getMatchingRules().values())
    {
      matchingRulesToKeep.add(mr);
    }
    for (AttributeSyntax<?> syntax : sc.getSyntaxes().values())
    {
      syntaxesToKeep.add(syntax);
    }
  }

  private static String getSchemaDirectoryPath()
  {
    File schemaDir =
      DirectoryServer.getEnvironmentConfig().getSchemaDirectory();
    if (schemaDir != null) {
      return schemaDir.getAbsolutePath();
    } else {
      return null;
    }
  }

  /**
   * Reads the schema.
   * @throws ConfigException if an error occurs reading the schema.
   * @throws InitializationException if an error occurs trying to find out
   * the schema files.
   * @throws DirectoryException if there is an error registering the minimal
   * objectclasses.
   */
  public void readSchema() throws DirectoryException,
  ConfigException, InitializationException
  {
    schema = getBaseSchema();

    String[] fileNames;
    String schemaDirPath= getSchemaDirectoryPath();
    try
    {
      // Load install directory schema
      File schemaDir = new File(schemaDirPath);
      if (schemaDirPath == null || ! schemaDir.exists())
      {
        Message message = ERR_CONFIG_SCHEMA_NO_SCHEMA_DIR.get(schemaDirPath);
        throw new InitializationException(message);
      }
      else if (! schemaDir.isDirectory())
      {
        Message message =
          ERR_CONFIG_SCHEMA_DIR_NOT_DIRECTORY.get(schemaDirPath);
        throw new InitializationException(message);
      }
      FileFilter ldifFiles = new FileFilter()
      {
        /**
         * {@inheritDoc}
         */
        public boolean accept(File f)
        {
          boolean accept = false;
          if (f != null)
          {
            if (f.isDirectory())
            {
              accept = true;
            } else if (Utils.isWindows())
            {
              accept =
                  f.getName().toLowerCase().endsWith(".ldif");
            } else
            {
              accept = f.getName().endsWith(".ldif");
            }
          }
          return accept;
        }
      };
      File[] schemaFiles = schemaDir.listFiles(ldifFiles);
      int size = schemaFiles.length;

      ArrayList<String> fileList = new ArrayList<String>(size);
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
    catch (InitializationException ie)
    {
      throw ie;
    }
    catch (Exception e)
    {
      Message message = ERR_CONFIG_SCHEMA_CANNOT_LIST_FILES.get(
          schemaDirPath, e.getMessage());
      throw new InitializationException(message, e);
    }

//  Iterate through the schema files and read them as an LDIF file containing
//  a single entry.  Then get the attributeTypes and objectClasses attributes
//  from that entry and parse them to initialize the server schema.
    for (String schemaFile : fileNames)
    {
      SchemaConfigManager.loadSchemaFile(schema, schemaFile);
    }
  }

  /**
   * Returns a basic version of the schema.  The schema is created and contains
   * enough definitions for the schema to be loaded.
   * @return a basic version of the schema.
   * @throws DirectoryException if there is an error registering the minimal
   * objectclasses.
   */
  protected Schema getBaseSchema() throws DirectoryException
  {
    Schema schema = new Schema();
    for (MatchingRule mr : matchingRulesToKeep)
    {
      schema.registerMatchingRule(mr, true);
    }
    for (AttributeSyntax<?> syntax : syntaxesToKeep)
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
   * @return the schema that was read.
   */
  public Schema getSchema()
  {
    return schema;
  }
}
