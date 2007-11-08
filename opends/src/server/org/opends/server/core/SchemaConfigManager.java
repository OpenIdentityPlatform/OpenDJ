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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;
import org.opends.messages.Message;



import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.api.ConfigHandler;
import org.opends.server.config.ConfigException;
import org.opends.server.schema.AttributeTypeSyntax;
import org.opends.server.schema.DITContentRuleSyntax;
import org.opends.server.schema.DITStructureRuleSyntax;
import org.opends.server.schema.MatchingRuleUseSyntax;
import org.opends.server.schema.NameFormSyntax;
import org.opends.server.schema.ObjectClassSyntax;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DITContentRule;
import org.opends.server.types.DITStructureRule;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.MatchingRuleUse;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.NameForm;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.Schema;
import org.opends.server.util.LDIFReader;

import static org.opends.server.config.ConfigConstants.*;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.loggers.ErrorLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a utility that will be used to manage the interaction with
 * the Directory Server schema.  It will be used to initially load all of the
 * matching rules and attribute syntaxes that have been defined in the
 * configuration, and will then read the actual schema definitions.  At present,
 * only attribute types and objectclasses are supported in the schema config
 * files.  Other components like DIT content rules, DIT structure rules, name
 * forms, and matching rule use definitions will be ignored.
 */
public class SchemaConfigManager
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The schema that has been parsed from the server configuration.
  private Schema schema;

  // The configuration handler for the Directory Server.
  private ConfigHandler configHandler;



  /**
   * Creates a new instance of this schema config manager.
   */
  public SchemaConfigManager()
  {
    configHandler = DirectoryServer.getConfigHandler();

    schema = new Schema();
  }



  /**
   * Retrieves the path to the directory containing the server schema files.
   *
   * @return  The path to the directory containing the server schema files.
   */
  public static String getSchemaDirectoryPath()
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
   * Retrieves a reference to the schema information that has been read from the
   * server configuration.  Note that this information will not be complete
   * until the <CODE>initializeMatchingRules</CODE>,
   * <CODE>initializeAttributeSyntaxes</CODE>, and
   * <CODE>initializeAttributeTypesAndObjectClasses</CODE> methods have been
   * called.
   *
   * @return  A reference to the schema information that has been read from the
   *          server configuration.
   */
  public Schema getSchema()
  {
    return schema;
  }



  /**
   * Initializes all the matching rules defined in the Directory Server
   * configuration.  This should only be called at Directory Server startup.
   *
   * @throws  ConfigException  If a configuration problem causes the matching
   *                           rule initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the matching rules that is not related to
   *                                   the server configuration.
   */
  public void initializeMatchingRules()
         throws ConfigException, InitializationException
  {
    MatchingRuleConfigManager matchingRuleConfigManager =
         new MatchingRuleConfigManager();
    matchingRuleConfigManager.initializeMatchingRules();
  }



  /**
   * Initializes all the attribute syntaxes defined in the Directory Server
   * configuration.  This should only be called at Directory Server startup.
   *
   * @throws  ConfigException  If a configuration problem causes the syntax
   *                           initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the syntaxes that is not related to the
   *                                   server configuration.
   */
  public void initializeAttributeSyntaxes()
         throws ConfigException, InitializationException
  {
    AttributeSyntaxConfigManager syntaxConfigManager =
         new AttributeSyntaxConfigManager();
    syntaxConfigManager.initializeAttributeSyntaxes();
  }



  /**
   * Initializes all the attribute type, object class, name form, DIT content
   * rule, DIT structure rule, and matching rule use definitions by reading the
   * server schema files.  These files will be located in a single directory and
   * will be processed in lexicographic order.  However, to make the order
   * easier to understand, they may be prefixed with a two digit number (with a
   * leading zero if necessary) so that they will be read in numeric order.
   * This should only be called at Directory Server startup.
   *
   * @throws  ConfigException  If a configuration problem causes the schema
   *                           element initialization to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the schema elements that is not related
   *                                   to the server configuration.
   */
  public void initializeSchemaFromFiles()
         throws ConfigException, InitializationException
  {
    // Construct the path to the directory that should contain the schema files
    // and make sure that it exists and is a directory.  Get a list of the files
    // in that directory sorted in alphabetic order.
    String schemaDirPath          = getSchemaDirectoryPath();
    File schemaDir                = new File(schemaDirPath);
    long oldestModificationTime   = -1L;
    long youngestModificationTime = -1L;
    String[] fileNames;

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

        long modificationTime = f.lastModified();
        if ((oldestModificationTime <= 0L) ||
            (modificationTime < oldestModificationTime))
        {
          oldestModificationTime = modificationTime;
        }

        if ((youngestModificationTime <= 0) ||
            (modificationTime > youngestModificationTime))
        {
          youngestModificationTime = modificationTime;
        }
      }

      fileNames = new String[fileList.size()];
      fileList.toArray(fileNames);
      Arrays.sort(fileNames);
    }
    catch (InitializationException ie)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ie);
      }

      throw ie;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CONFIG_SCHEMA_CANNOT_LIST_FILES.get(
          schemaDirPath, getExceptionMessage(e));
      throw new InitializationException(message, e);
    }


    // If the oldest and youngest modification timestamps didn't get set for
    // some reason, then set them to the current time.
    if (oldestModificationTime <= 0)
    {
      oldestModificationTime = System.currentTimeMillis();
    }

    if (youngestModificationTime <= 0)
    {
      youngestModificationTime = oldestModificationTime;
    }

    schema.setOldestModificationTime(oldestModificationTime);
    schema.setYoungestModificationTime(youngestModificationTime);


    // Iterate through the schema files and read them as an LDIF file containing
    // a single entry.  Then get the attributeTypes and objectClasses attributes
    // from that entry and parse them to initialize the server schema.
    for (String schemaFile : fileNames)
    {
      loadSchemaFile(schema, schemaFile, false);
    }
  }



  /**
   * Loads the contents of the specified schema file into the provided schema.
   *
   * @param  schema      The schema in which the contents of the schema file are
   *                     to be loaded.
   * @param  schemaFile  The name of the schema file to be loaded into the
   *                     provided schema.
   *
   * @return  A list of the modifications that could be performed in order to
   *          obtain the contents of the file.
   *
   * @throws  ConfigException  If a configuration problem causes the schema
   *                           element initialization to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the schema elements that is not related
   *                                   to the server configuration.
   */
  public static List<Modification> loadSchemaFile(Schema schema,
                                                  String schemaFile)
         throws ConfigException, InitializationException
  {
    return loadSchemaFile(schema, schemaFile, true);
  }



  /**
   * Loads the contents of the specified schema file into the provided schema.
   *
   * @param  schema       The schema in which the contents of the schema file
   *                      are to be loaded.
   * @param  schemaFile   The name of the schema file to be loaded into the
   *                      provided schema.
   * @param  failOnError  If {@code true}, indicates that this method should
   *                      throw an exception if certain kinds of errors occur.
   *                      If {@code false}, indicates that this method should
   *                      log an error message and return without an exception.
   *                      This should only be {@code false} when called from
   *                      {@code initializeSchemaFromFiles}.
   *
   * @return  A list of the modifications that could be performed in order to
   *          obtain the contents of the file, or {@code null} if a problem
   *          occurred and {@code failOnError} is {@code false}.
   *
   * @throws  ConfigException  If a configuration problem causes the schema
   *                           element initialization to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the schema elements that is not related
   *                                   to the server configuration.
   */
  private static List<Modification> loadSchemaFile(Schema schema,
                                                   String schemaFile,
                                                   boolean failOnError)
         throws ConfigException, InitializationException
  {
    // Create an LDIF reader to use when reading the files.
    String schemaDirPath = getSchemaDirectoryPath();
    LDIFReader reader;
    try
    {
      File f = new File(schemaDirPath, schemaFile);
      reader = new LDIFReader(new LDIFImportConfig(f.getAbsolutePath()));
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = WARN_CONFIG_SCHEMA_CANNOT_OPEN_FILE.get(
              schemaFile, schemaDirPath, getExceptionMessage(e));

      if (failOnError)
      {
        throw new ConfigException(message);
      }
      else
      {
        logError(message);
        return null;
      }
    }


    // Read the LDIF entry from the file and close the file.
    Entry entry;
    try
    {
      entry = reader.readEntry(false);

      if (entry == null)
      {
        // The file was empty -- skip it.
        reader.close();
        return new LinkedList<Modification>();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = WARN_CONFIG_SCHEMA_CANNOT_READ_LDIF_ENTRY.get(
              schemaFile, schemaDirPath, getExceptionMessage(e));

      if (failOnError)
      {
        throw new InitializationException(message, e);
      }
      else
      {
        logError(message);
        return null;
      }
    }

    // If there are any more entries in the file, then print a warning message.
    try
    {
      Entry e = reader.readEntry(false);
      if (e != null)
      {
        Message message = WARN_CONFIG_SCHEMA_MULTIPLE_ENTRIES_IN_FILE.get(
            schemaFile, schemaDirPath);
        logError(message);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = WARN_CONFIG_SCHEMA_UNPARSEABLE_EXTRA_DATA_IN_FILE.get(
          schemaFile, schemaDirPath, getExceptionMessage(e));
      logError(message);
    }

    try
    {
      reader.close();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }


    // Get the attributeTypes attribute from the entry.
    LinkedList<Modification> mods = new LinkedList<Modification>();
    AttributeTypeSyntax attrTypeSyntax;
    try
    {
      attrTypeSyntax = (AttributeTypeSyntax)
                       schema.getSyntax(SYNTAX_ATTRIBUTE_TYPE_OID);
      if (attrTypeSyntax == null)
      {
        attrTypeSyntax = new AttributeTypeSyntax();
        attrTypeSyntax.initializeSyntax(null);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      attrTypeSyntax = new AttributeTypeSyntax();
      attrTypeSyntax.initializeSyntax(null);
    }

    AttributeType attributeAttrType =
         schema.getAttributeType(ATTR_ATTRIBUTE_TYPES_LC);
    if (attributeAttrType == null)
    {
      attributeAttrType =
           DirectoryServer.getDefaultAttributeType(ATTR_ATTRIBUTE_TYPES,
                                                   attrTypeSyntax);
    }

    List<Attribute> attrList = entry.getAttribute(attributeAttrType);
    if ((attrList != null) && (! attrList.isEmpty()))
    {
      for (Attribute a : attrList)
      {
        mods.add(new Modification(ModificationType.ADD, a.duplicate()));
      }
    }


    // Get the objectClasses attribute from the entry.
    ObjectClassSyntax ocSyntax;
    try
    {
      ocSyntax = (ObjectClassSyntax) schema.getSyntax(SYNTAX_OBJECTCLASS_OID);
      if (ocSyntax == null)
      {
        ocSyntax = new ObjectClassSyntax();
        ocSyntax.initializeSyntax(null);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      ocSyntax = new ObjectClassSyntax();
      ocSyntax.initializeSyntax(null);
    }

    AttributeType objectclassAttrType =
         schema.getAttributeType(ATTR_OBJECTCLASSES_LC);
    if (objectclassAttrType == null)
    {
      objectclassAttrType =
           DirectoryServer.getDefaultAttributeType(ATTR_OBJECTCLASSES,
                                                   ocSyntax);
    }

    List<Attribute> ocList = entry.getAttribute(objectclassAttrType);
    if ((ocList != null) && (! ocList.isEmpty()))
    {
      for (Attribute a : ocList)
      {
        mods.add(new Modification(ModificationType.ADD, a.duplicate()));
      }
    }


    // Get the name forms attribute from the entry.
    NameFormSyntax nfSyntax;
    try
    {
      nfSyntax = (NameFormSyntax) schema.getSyntax(SYNTAX_NAME_FORM_OID);
      if (nfSyntax == null)
      {
        nfSyntax = new NameFormSyntax();
        nfSyntax.initializeSyntax(null);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      nfSyntax = new NameFormSyntax();
      nfSyntax.initializeSyntax(null);
    }

    AttributeType nameFormAttrType =
         schema.getAttributeType(ATTR_NAME_FORMS_LC);
    if (nameFormAttrType == null)
    {
      nameFormAttrType =
           DirectoryServer.getDefaultAttributeType(ATTR_NAME_FORMS, nfSyntax);
    }

    List<Attribute> nfList = entry.getAttribute(nameFormAttrType);
    if ((nfList != null) && (! nfList.isEmpty()))
    {
      for (Attribute a : nfList)
      {
        mods.add(new Modification(ModificationType.ADD, a.duplicate()));
      }
    }


    // Get the DIT content rules attribute from the entry.
    DITContentRuleSyntax dcrSyntax;
    try
    {
      dcrSyntax = (DITContentRuleSyntax)
                  schema.getSyntax(SYNTAX_DIT_CONTENT_RULE_OID);
      if (dcrSyntax == null)
      {
        dcrSyntax = new DITContentRuleSyntax();
        dcrSyntax.initializeSyntax(null);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      dcrSyntax = new DITContentRuleSyntax();
      dcrSyntax.initializeSyntax(null);
    }

    AttributeType dcrAttrType =
         schema.getAttributeType(ATTR_DIT_CONTENT_RULES_LC);
    if (dcrAttrType == null)
    {
      dcrAttrType =
           DirectoryServer.getDefaultAttributeType(ATTR_DIT_CONTENT_RULES,
                                                   dcrSyntax);
    }

    List<Attribute> dcrList = entry.getAttribute(dcrAttrType);
    if ((dcrList != null) && (! dcrList.isEmpty()))
    {
      for (Attribute a : dcrList)
      {
        mods.add(new Modification(ModificationType.ADD, a.duplicate()));
      }
    }


    // Get the DIT structure rules attribute from the entry.
    DITStructureRuleSyntax dsrSyntax;
    try
    {
      dsrSyntax = (DITStructureRuleSyntax)
                  schema.getSyntax(SYNTAX_DIT_STRUCTURE_RULE_OID);
      if (dsrSyntax == null)
      {
        dsrSyntax = new DITStructureRuleSyntax();
        dsrSyntax.initializeSyntax(null);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      dsrSyntax = new DITStructureRuleSyntax();
      dsrSyntax.initializeSyntax(null);
    }

    AttributeType dsrAttrType =
         schema.getAttributeType(ATTR_DIT_STRUCTURE_RULES_LC);
    if (dsrAttrType == null)
    {
      dsrAttrType =
           DirectoryServer.getDefaultAttributeType(ATTR_DIT_STRUCTURE_RULES,
                                                   dsrSyntax);
    }

    List<Attribute> dsrList = entry.getAttribute(dsrAttrType);
    if ((dsrList != null) && (! dsrList.isEmpty()))
    {
      for (Attribute a : dsrList)
      {
        mods.add(new Modification(ModificationType.ADD, a.duplicate()));
      }
    }


    // Get the matching rule uses attribute from the entry.
    MatchingRuleUseSyntax mruSyntax;
    try
    {
      mruSyntax = (MatchingRuleUseSyntax)
                  schema.getSyntax(SYNTAX_MATCHING_RULE_USE_OID);
      if (mruSyntax == null)
      {
        mruSyntax = new MatchingRuleUseSyntax();
        mruSyntax.initializeSyntax(null);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      mruSyntax = new MatchingRuleUseSyntax();
      mruSyntax.initializeSyntax(null);
    }

    AttributeType mruAttrType =
         schema.getAttributeType(ATTR_MATCHING_RULE_USE_LC);
    if (mruAttrType == null)
    {
      mruAttrType =
           DirectoryServer.getDefaultAttributeType(ATTR_MATCHING_RULE_USE,
                                                   mruSyntax);
    }

    List<Attribute> mruList = entry.getAttribute(mruAttrType);
    if ((mruList != null) && (! mruList.isEmpty()))
    {
      for (Attribute a : mruList)
      {
        mods.add(new Modification(ModificationType.ADD, a.duplicate()));
      }
    }

    // Loop on all the attribute of the schema entry to
    // find the extra attribute that shoule be loaded in the Schema.
    for (Attribute attribute : entry.getAttributes())
    {
      if (!isSchemaAttribute(attribute))
      {
        schema.addExtraAttribute(attribute.getName(), attribute);
      }
    }

    // Parse the attribute type definitions if there are any.
    if (attrList != null)
    {
      for (Attribute a : attrList)
      {
        for (AttributeValue v : a.getValues())
        {
          // Parse the attribute type.
          AttributeType attrType;
          try
          {
            attrType = AttributeTypeSyntax.decodeAttributeType(v.getValue(),
                                                          schema, false);
            attrType.setExtraProperty(SCHEMA_PROPERTY_FILENAME, (String) null);
            attrType.setSchemaFile(schemaFile);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            Message message = WARN_CONFIG_SCHEMA_CANNOT_PARSE_ATTR_TYPE.get(
                    schemaFile, de.getMessageObject());

            if (failOnError)
            {
              throw new ConfigException(message, de);
            }
            else
            {
              logError(message);
              continue;
            }
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }

            Message message = WARN_CONFIG_SCHEMA_CANNOT_PARSE_ATTR_TYPE.get(
                    schemaFile, v.getStringValue() + ":  " +
                    getExceptionMessage(e));
            if (failOnError)
            {
              throw new ConfigException(message, e);
            }
            else
            {
              logError(message);
              continue;
            }
          }

          // Register it with the schema.  We will allow duplicates, with the
          // later definition overriding any earlier definition, but we want
          // to trap them and log a warning.
          try
          {
            schema.registerAttributeType(attrType, failOnError);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            Message message = WARN_CONFIG_SCHEMA_CONFLICTING_ATTR_TYPE.get(
                schemaFile, de.getMessageObject());
            logError(message);

            try
            {
              schema.registerAttributeType(attrType, true);
            }
            catch (Exception e)
            {
              // This should never happen.
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
            }
          }
        }
      }
    }


    // Parse the objectclass definitions if there are any.
    if (ocList != null)
    {
      for (Attribute a : ocList)
      {
        for (AttributeValue v : a.getValues())
        {
          // Parse the objectclass.
          ObjectClass oc;
          try
          {
            oc =
              ObjectClassSyntax.decodeObjectClass(v.getValue(), schema, false);
            oc.setExtraProperty(SCHEMA_PROPERTY_FILENAME, (String) null);
            oc.setSchemaFile(schemaFile);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            Message message = WARN_CONFIG_SCHEMA_CANNOT_PARSE_OC.get(
                    schemaFile,
                    de.getMessageObject());

            if (failOnError)
            {
              throw new ConfigException(message, de);
            }
            else
            {
              logError(message);
              continue;
            }
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }

            Message message = WARN_CONFIG_SCHEMA_CANNOT_PARSE_OC.get(
                    schemaFile,
                    v.getStringValue() + ":  " + getExceptionMessage(e));

            if (failOnError)
            {
              throw new ConfigException(message, e);
            }
            else
            {
              logError(message);
              continue;
            }
          }

          // Register it with the schema.  We will allow duplicates, with the
          // later definition overriding any earlier definition, but we want
          // to trap them and log a warning.
          try
          {
            schema.registerObjectClass(oc, failOnError);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            Message message = WARN_CONFIG_SCHEMA_CONFLICTING_OC.get(
                schemaFile, de.getMessageObject());
            logError(message);

            try
            {
              schema.registerObjectClass(oc, true);
            }
            catch (Exception e)
            {
              // This should never happen.
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
            }
          }
        }
      }
    }


    // Parse the name form definitions if there are any.
    if (nfList != null)
    {
      for (Attribute a : nfList)
      {
        for (AttributeValue v : a.getValues())
        {
          // Parse the name form.
          NameForm nf;
          try
          {
            nf = NameFormSyntax.decodeNameForm(v.getValue(), schema, false);
            nf.getExtraProperties().remove(SCHEMA_PROPERTY_FILENAME);
            nf.setSchemaFile(schemaFile);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            Message message = WARN_CONFIG_SCHEMA_CANNOT_PARSE_NAME_FORM.get(
                    schemaFile, de.getMessageObject());
            if (failOnError)
            {
              throw new ConfigException(message, de);
            }
            else
            {
              logError(message);
              continue;
            }
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }

            Message message = WARN_CONFIG_SCHEMA_CANNOT_PARSE_NAME_FORM.get(
                    schemaFile,  v.getStringValue() + ":  " +
                    getExceptionMessage(e));

            if (failOnError)
            {
              throw new ConfigException(message, e);
            }
            else
            {
              logError(message);
              continue;
            }
          }

          // Register it with the schema.  We will allow duplicates, with the
          // later definition overriding any earlier definition, but we want
          // to trap them and log a warning.
          try
          {
            schema.registerNameForm(nf, failOnError);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            Message message = WARN_CONFIG_SCHEMA_CONFLICTING_NAME_FORM.get(
                schemaFile, de.getMessageObject());
            logError(message);

            try
            {
              schema.registerNameForm(nf, true);
            }
            catch (Exception e)
            {
              // This should never happen.
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
            }
          }
        }
      }
    }


    // Parse the DIT content rule definitions if there are any.
    if (dcrList != null)
    {
      for (Attribute a : dcrList)
      {
        for (AttributeValue v : a.getValues())
        {
          // Parse the DIT content rule.
          DITContentRule dcr;
          try
          {
            dcr = DITContentRuleSyntax.decodeDITContentRule(
                v.getValue(), schema, false);
            dcr.getExtraProperties().remove(SCHEMA_PROPERTY_FILENAME);
            dcr.setSchemaFile(schemaFile);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            Message message = WARN_CONFIG_SCHEMA_CANNOT_PARSE_DCR.get(
                    schemaFile, de.getMessageObject());

            if (failOnError)
            {
              throw new ConfigException(message, de);
            }
            else
            {
              logError(message);
              continue;
            }
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }

            Message message = WARN_CONFIG_SCHEMA_CANNOT_PARSE_DCR.get(
                    schemaFile,v.getStringValue() + ":  " +
                    getExceptionMessage(e));

            if (failOnError)
            {
              throw new ConfigException(message, e);
            }
            else
            {
              logError(message);
              continue;
            }
          }

          // Register it with the schema.  We will allow duplicates, with the
          // later definition overriding any earlier definition, but we want
          // to trap them and log a warning.
          try
          {
            schema.registerDITContentRule(dcr, failOnError);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            Message message = WARN_CONFIG_SCHEMA_CONFLICTING_DCR.get(
                schemaFile, de.getMessageObject());
            logError(message);

            try
            {
              schema.registerDITContentRule(dcr, true);
            }
            catch (Exception e)
            {
              // This should never happen.
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
            }
          }
        }
      }
    }


    // Parse the DIT structure rule definitions if there are any.
    if (dsrList != null)
    {
      for (Attribute a : dsrList)
      {
        for (AttributeValue v : a.getValues())
        {
          // Parse the DIT content rule.
          DITStructureRule dsr;
          try
          {
            dsr = DITStructureRuleSyntax.decodeDITStructureRule(
                v.getValue(), schema, false);
            dsr.getExtraProperties().remove(SCHEMA_PROPERTY_FILENAME);
            dsr.setSchemaFile(schemaFile);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            Message message = WARN_CONFIG_SCHEMA_CANNOT_PARSE_DSR.get(
                    schemaFile, de.getMessageObject());

            if (failOnError)
            {
              throw new ConfigException(message, de);
            }
            else
            {
              logError(message);
              continue;
            }
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }

            Message message = WARN_CONFIG_SCHEMA_CANNOT_PARSE_DSR.get(
                    schemaFile, v.getStringValue() + ":  " +
                                        getExceptionMessage(e));

            if (failOnError)
            {
              throw new ConfigException(message, e);
            }
            else
            {
              logError(message);
              continue;
            }
          }

          // Register it with the schema.  We will allow duplicates, with the
          // later definition overriding any earlier definition, but we want
          // to trap them and log a warning.
          try
          {
            schema.registerDITStructureRule(dsr, failOnError);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            Message message = WARN_CONFIG_SCHEMA_CONFLICTING_DSR.get(
                schemaFile, de.getMessageObject());
            logError(message);

            try
            {
              schema.registerDITStructureRule(dsr, true);
            }
            catch (Exception e)
            {
              // This should never happen.
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
            }
          }
        }
      }
    }


    // Parse the matching rule use definitions if there are any.
    if (mruList != null)
    {
      for (Attribute a : mruList)
      {
        for (AttributeValue v : a.getValues())
        {
          // Parse the matching rule use definition.
          MatchingRuleUse mru;
          try
          {
            mru = MatchingRuleUseSyntax.decodeMatchingRuleUse(
                            v.getValue(), schema, false);
            mru.getExtraProperties().remove(SCHEMA_PROPERTY_FILENAME);
            mru.setSchemaFile(schemaFile);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            Message message = WARN_CONFIG_SCHEMA_CANNOT_PARSE_MRU.get(
                    schemaFile, de.getMessageObject());

            if (failOnError)
            {
              throw new ConfigException(message, de);
            }
            else
            {
              logError(message);
              continue;
            }
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }

            Message message = WARN_CONFIG_SCHEMA_CANNOT_PARSE_MRU.get(
                    schemaFile,
                    v.getStringValue() + ":  " +
                    getExceptionMessage(e));

            if (failOnError)
            {
              throw new ConfigException(message, e);
            }
            else
            {
              logError(message);
              continue;
            }
          }

          // Register it with the schema.  We will allow duplicates, with the
          // later definition overriding any earlier definition, but we want
          // to trap them and log a warning.
          try
          {
            schema.registerMatchingRuleUse(mru, failOnError);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            Message message = WARN_CONFIG_SCHEMA_CONFLICTING_MRU.get(
                schemaFile, de.getMessageObject());
            logError(message);

            try
            {
              schema.registerMatchingRuleUse(mru, true);
            }
            catch (Exception e)
            {
              // This should never happen.
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
            }
          }
        }
      }
    }


    return mods;
  }



  /**
   * This method checks if a given attribute is an attribute that
   * is used by the definition of the schema.
   *
   * @param attribute   The attribute to be checked.
   * @return            true if the attribute is part of the schema definition,
   *                    false if the attribute is not part of the schema
   *                    definition.
   */
  private static boolean isSchemaAttribute(Attribute attribute)
  {
    String attributeOid = attribute.getAttributeType().getOID();
    if (attributeOid.equals("2.5.21.1") ||
        attributeOid.equals("2.5.21.2") ||
        attributeOid.equals("2.5.21.4") ||
        attributeOid.equals("2.5.21.5") ||
        attributeOid.equals("2.5.21.6") ||
        attributeOid.equals("2.5.21.7") ||
        attributeOid.equals("2.5.21.8") ||
        attributeOid.equals("2.5.4.3")  ||
        attributeOid.equals("attributetypes-oid")      ||
        attributeOid.equals("objectclasses-oid")       ||
        attributeOid.equals("matchingRules-oid")       ||
        attributeOid.equals("matchingRuleUse-oid")     ||
        attributeOid.equals("NameFormDescription-oid") ||
        attributeOid.equals("dITContentRules-oid")     ||
        attributeOid.equals("dITStructureRules")
        )
    {
      return true;
    }
    else
    {
      return false;
    }
  }
}

