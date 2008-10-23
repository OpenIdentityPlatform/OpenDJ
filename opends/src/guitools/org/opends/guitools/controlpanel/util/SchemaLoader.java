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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.util;

import static org.opends.messages.ConfigMessages.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import org.opends.messages.Message;
import org.opends.server.config.ConfigConstants;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SchemaConfigManager;
import org.opends.server.types.AttributeType;
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

  /**
   * Constructor.
   *
   */
  public SchemaLoader()
  {
    schema = DirectoryServer.getSchema().duplicate();
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
   */
  public void readSchema() throws ConfigException, InitializationException
  {
    String schemaDirPath= getSchemaDirectoryPath();
    File schemaDir = new File(schemaDirPath);
    String[] attrsToKeep = {
        ConfigConstants.ATTR_ATTRIBUTE_TYPES_LC,
        ConfigConstants.ATTR_OBJECTCLASSES_LC,
        ConfigConstants.ATTR_NAME_FORMS_LC,
        ConfigConstants.ATTR_DIT_CONTENT_RULES_LC,
        ConfigConstants.ATTR_DIT_STRUCTURE_RULES_LC,
        ConfigConstants.ATTR_MATCHING_RULE_USE_LC};
    String[] ocsToKeep = {"top"};
    for (ObjectClass oc : schema.getObjectClasses().values())
    {
      String name = oc.getNameOrOID().toLowerCase();
      boolean found = false;
      for (int i=0; i<ocsToKeep.length; i++)
      {
        if (ocsToKeep[i].equals(name))
        {
          found = true;
          break;
        }
      }
      if (!found)
      {
        schema.deregisterObjectClass(oc);
      }
    }
    for (AttributeType attr : schema.getAttributeTypes().values())
    {
      String name = attr.getNameOrOID().toLowerCase();
      boolean found = false;
      for (int i=0; i<attrsToKeep.length; i++)
      {
        if (attrsToKeep[i].equals(name))
        {
          found = true;
          break;
        }
      }
      if (!found)
      {
        schema.deregisterAttributeType(attr);
      }
    }
    String[] fileNames = null;
    try
    {
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

      File[] schemaDirFiles = schemaDir.listFiles();
      ArrayList<String> fileList = new ArrayList<String>(schemaDirFiles.length);
      for (File f : schemaDirFiles)
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
    catch (Throwable t)
    {
      t.printStackTrace();
    }
    /*
    catch (InitializationException ie)
    {
      throw ie;
    }
    catch (Exception e)
    {
      Message message = ERR_CONFIG_SCHEMA_CANNOT_LIST_FILES.get(
          schemaDirPath, getExceptionMessage(e));
      throw new InitializationException(message, e);
    }
    */

//  Iterate through the schema files and read them as an LDIF file containing
//  a single entry.  Then get the attributeTypes and objectClasses attributes
//  from that entry and parse them to initialize the server schema.
    for (String schemaFile : fileNames)
    {
      SchemaConfigManager.loadSchemaFile(schema, schemaFile);
    }
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
