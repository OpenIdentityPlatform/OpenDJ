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
package org.opends.server.core;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.opends.server.schema.DITContentRuleSyntax;
import org.opends.server.schema.DITStructureRuleSyntax;
import org.opends.server.types.Attribute;
import org.opends.server.types.DITContentRule;
import org.opends.server.types.DITStructureRule;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Modification;
import org.opends.server.types.Schema;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.StaticUtils;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.types.CommonSchemaElements.*;
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
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The schema that has been parsed from the server configuration. */
  private Schema schema;

  private final ServerContext serverContext;

  /**
   * Creates a new instance of this schema config manager.
   *
   * @param serverContext
   *            The server context.
   */
  public SchemaConfigManager(ServerContext serverContext)
  {
    this.serverContext = serverContext;
    try
    {
      // the manager will build the schema from scratch, but we need to start from core schema for SDK schema
      schema = new Schema(org.forgerock.opendj.ldap.schema.Schema.getCoreSchema());
    }
    catch (DirectoryException unexpected)
    {
      // the core schema should not have any warning
      throw new RuntimeException(unexpected);
    }
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
    }
    return null;
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
    MatchingRuleConfigManager matchingRuleConfigManager = new MatchingRuleConfigManager(serverContext);
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
         new AttributeSyntaxConfigManager(serverContext);
    syntaxConfigManager.initializeAttributeSyntaxes();
  }

  /** Filter implementation that accepts only ldif files. */
  public static class SchemaFileFilter implements FilenameFilter
  {
    @Override
    public boolean accept(File directory, String filename)
    {
      return filename.endsWith(".ldif");
    }
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
    String schemaInstanceDirPath  = getSchemaDirectoryPath();
    File schemaInstanceDir        = null;

    try
    {
      if (schemaInstanceDirPath != null)
      {
        schemaInstanceDir = new File(schemaInstanceDirPath);
      }
    } catch (Exception e)
    {
      schemaInstanceDir = null;
    }
    long oldestModificationTime   = -1L;
    long youngestModificationTime = -1L;
    String[] fileNames;

    try
    {
      if (schemaInstanceDir == null || ! schemaInstanceDir.exists())
      {
        LocalizableMessage message =
          ERR_CONFIG_SCHEMA_NO_SCHEMA_DIR.get(schemaInstanceDirPath);
        throw new InitializationException(message);
      }
      if (! schemaInstanceDir.isDirectory())
      {
        LocalizableMessage message =
            ERR_CONFIG_SCHEMA_DIR_NOT_DIRECTORY.get(schemaInstanceDirPath);
        throw new InitializationException(message);
      }

      FilenameFilter filter = new SchemaFileFilter();
      File[] schemaInstanceDirFiles =
                schemaInstanceDir.listFiles(filter);
      int fileNumber = schemaInstanceDirFiles.length ;
      ArrayList<String> fileList = new ArrayList<>(fileNumber);

      for (File f : schemaInstanceDirFiles)
      {
        if (f.isFile())
        {
          fileList.add(f.getName());
        }

        long modificationTime = f.lastModified();
        if (oldestModificationTime <= 0L ||
            modificationTime < oldestModificationTime)
        {
          oldestModificationTime = modificationTime;
        }

        if (youngestModificationTime <= 0 ||
            modificationTime > youngestModificationTime)
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
      logger.traceException(ie);

      throw ie;
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_CONFIG_SCHEMA_CANNOT_LIST_FILES.get(
          schemaInstanceDirPath, getExceptionMessage(e));
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
  public static List<Modification> loadSchemaFile(Schema schema, String schemaFile)
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
  private static List<Modification> loadSchemaFile(Schema schema, String schemaFile,
      boolean failOnError) throws ConfigException, InitializationException
  {
    // Create an LDIF reader to use when reading the files.
    String schemaDirPath = getSchemaDirectoryPath();
    File f = new File(schemaDirPath, schemaFile);
    LDIFReader reader;
    try
    {
      reader = new LDIFReader(new LDIFImportConfig(f.getAbsolutePath()));
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = WARN_CONFIG_SCHEMA_CANNOT_OPEN_FILE.get(
              schemaFile, schemaDirPath, getExceptionMessage(e));

      if (failOnError)
      {
        throw new ConfigException(message);
      }
      else
      {
        logger.error(message);
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
        return new LinkedList<>();
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = WARN_CONFIG_SCHEMA_CANNOT_READ_LDIF_ENTRY.get(
              schemaFile, schemaDirPath, getExceptionMessage(e));

      if (failOnError)
      {
        throw new InitializationException(message, e);
      }
      else
      {
        logger.error(message);
        StaticUtils.close(reader);
        return null;
      }
    }

    // If there are any more entries in the file, then print a warning message.
    try
    {
      Entry e = reader.readEntry(false);
      if (e != null)
      {
        logger.warn(WARN_CONFIG_SCHEMA_MULTIPLE_ENTRIES_IN_FILE, schemaFile, schemaDirPath);
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      logger.warn(WARN_CONFIG_SCHEMA_UNPARSEABLE_EXTRA_DATA_IN_FILE, schemaFile, schemaDirPath, getExceptionMessage(e));
    }
    finally
    {
      StaticUtils.close(reader);
    }

    // Get the attributeTypes attribute from the entry.
    List<Modification> mods = new LinkedList<>();

    //parse the syntaxes first because attributes rely on these.
    List<Attribute> ldapSyntaxList = getLdapSyntaxesAttributes(schema, entry, mods);
    List<Attribute> attrList = getAttributeTypeAttributes(schema, entry, mods);
    List<Attribute> ocList = getObjectClassesAttributes(schema, entry, mods);
    List<Attribute> nfList = getNameFormsAttributes(schema, entry, mods);
    List<Attribute> dcrList = getDITContentRulesAttributes(schema, entry, mods);
    List<Attribute> dsrList = getDITStructureRulesAttributes(schema, entry, mods);
    List<Attribute> mruList = getMatchingRuleUsesAttributes(schema, entry, mods);

    // Loop on all the attribute of the schema entry to
    // find the extra attribute that should be loaded in the Schema.
    for (Attribute attribute : entry.getAttributes())
    {
      if (!isSchemaAttribute(attribute))
      {
        schema.addExtraAttribute(attribute.getAttributeDescription().getNameOrOID(), attribute);
      }
    }

    parseLdapSyntaxesDefinitions(schema, schemaFile, failOnError, ldapSyntaxList);
    parseAttributeTypeDefinitions(schema, schemaFile, failOnError, attrList);
    parseObjectclassDefinitions(schema, schemaFile, failOnError, ocList);
    parseNameFormDefinitions(schema, schemaFile, failOnError, nfList);
    parseDITContentRuleDefinitions(schema, schemaFile, failOnError, dcrList);
    parseDITStructureRuleDefinitions(schema, schemaFile, failOnError, dsrList);
    parseMatchingRuleUseDefinitions(schema, schemaFile, failOnError, mruList);

    return mods;
  }

  private static List<Attribute> getLdapSyntaxesAttributes(Schema schema,
      Entry entry, List<Modification> mods) throws ConfigException
  {
    Syntax syntax = schema.getSyntax(SYNTAX_LDAP_SYNTAX_OID);
    if (syntax == null)
    {
      syntax = CoreSchema.getLDAPSyntaxDescriptionSyntax();
    }

    AttributeType ldapSyntaxAttrType = schema.getAttributeType(ATTR_LDAP_SYNTAXES, syntax);
    return createAddModifications(entry, mods, ldapSyntaxAttrType);
  }

  private static List<Attribute> getAttributeTypeAttributes(Schema schema,
      Entry entry, List<Modification> mods) throws ConfigException,
      InitializationException
  {
    Syntax syntax = schema.getSyntax(SYNTAX_ATTRIBUTE_TYPE_OID);
    if (syntax == null)
    {
      syntax = CoreSchema.getAttributeTypeDescriptionSyntax();
    }
    AttributeType attributeAttrType = schema.getAttributeType(ATTR_ATTRIBUTE_TYPES, syntax);
    return createAddModifications(entry, mods, attributeAttrType);
  }

  /** Get the objectClasses attribute from the entry. */
  private static List<Attribute> getObjectClassesAttributes(Schema schema,
      Entry entry, List<Modification> mods) throws ConfigException,
      InitializationException
  {
    Syntax syntax = schema.getSyntax(SYNTAX_OBJECTCLASS_OID);
    if (syntax == null)
    {
      syntax = CoreSchema.getObjectClassDescriptionSyntax();
    }
    AttributeType objectclassAttrType = schema.getAttributeType(ATTR_OBJECTCLASSES, syntax);
    return createAddModifications(entry, mods, objectclassAttrType);
  }

  /** Get the name forms attribute from the entry. */
  private static List<Attribute> getNameFormsAttributes(Schema schema,
      Entry entry, List<Modification> mods) throws ConfigException,
      InitializationException
  {
    Syntax syntax = schema.getSyntax(SYNTAX_NAME_FORM_OID);
    if (syntax == null)
    {
      syntax = CoreSchema.getNameFormDescriptionSyntax();
    }
    AttributeType nameFormAttrType = schema.getAttributeType(ATTR_NAME_FORMS, syntax);
    return createAddModifications(entry, mods, nameFormAttrType);
  }

  /** Get the DIT content rules attribute from the entry. */
  private static List<Attribute> getDITContentRulesAttributes(Schema schema,
      Entry entry, List<Modification> mods) throws ConfigException,
      InitializationException
  {
    Syntax syntax = schema.getSyntax(SYNTAX_DIT_CONTENT_RULE_OID);
    if (syntax == null)
    {
      syntax = CoreSchema.getDITContentRuleDescriptionSyntax();
    }
    AttributeType dcrAttrType = schema.getAttributeType(ATTR_DIT_CONTENT_RULES, syntax);
    return createAddModifications(entry, mods, dcrAttrType);
  }

  /** Get the DIT structure rules attribute from the entry. */
  private static List<Attribute> getDITStructureRulesAttributes(Schema schema,
      Entry entry, List<Modification> mods) throws ConfigException,
      InitializationException
  {
    Syntax syntax = schema.getSyntax(SYNTAX_DIT_STRUCTURE_RULE_OID);
    if (syntax == null)
    {
      syntax = CoreSchema.getDITStructureRuleDescriptionSyntax();
    }
    AttributeType dsrAttrType = schema.getAttributeType(ATTR_DIT_STRUCTURE_RULES, syntax);
    return createAddModifications(entry, mods, dsrAttrType);
  }

  /** Get the matching rule uses attribute from the entry. */
  private static List<Attribute> getMatchingRuleUsesAttributes(Schema schema,
      Entry entry, List<Modification> mods) throws ConfigException,
      InitializationException
  {
    Syntax syntax = schema.getSyntax(SYNTAX_MATCHING_RULE_USE_OID);
    if (syntax == null)
    {
      syntax = CoreSchema.getMatchingRuleUseDescriptionSyntax();
    }
    AttributeType mruAttrType = schema.getAttributeType(ATTR_MATCHING_RULE_USE, syntax);
    return createAddModifications(entry, mods, mruAttrType);
  }

  private static List<Attribute> createAddModifications(Entry entry,
      List<Modification> mods, AttributeType attrType)
  {
    List<Attribute> attributes = entry.getAttribute(attrType);
    for (Attribute a : attributes)
    {
      mods.add(new Modification(ModificationType.ADD, a));
    }
    return attributes;
  }

  /** Parse the ldapsyntaxes definitions if there are any. */
  private static void parseLdapSyntaxesDefinitions(Schema schema,
      String schemaFile, boolean failOnError, List<Attribute> ldapSyntaxList)
      throws ConfigException
  {
    if (ldapSyntaxList != null)
    {
      for (Attribute a : ldapSyntaxList)
      {
        for (ByteString v : a)
        {
          final String definition = Schema.addSchemaFileToElementDefinitionIfAbsent(v.toString(), schemaFile);
          try
          {
            schema.registerLdapSyntaxDescription(definition, failOnError);
          }
          catch (DirectoryException de)
          {
            logger.traceException(de);

            if (de.getResultCode().equals(ResultCode.CONSTRAINT_VIOLATION))
            {
              // Register it with the schema.  We will allow duplicates, with the
              // later definition overriding any earlier definition, but we want
              // to trap them and log a warning.
              logger.warn(WARN_CONFIG_SCHEMA_CONFLICTING_LDAP_SYNTAX, schemaFile, de.getMessageObject());
              try
              {
                schema.registerLdapSyntaxDescription(definition, true);
              }
              catch (Exception e)
              {
                // This should never happen.
                logger.traceException(e);
              }
            }
            else
            {
              LocalizableMessage message = WARN_CONFIG_SCHEMA_CANNOT_PARSE_LDAP_SYNTAX.get(
                  schemaFile, de.getMessageObject());
              reportError(failOnError, de, message);
            }
          }
        }
      }
    }
  }

  /** Parse the attribute type definitions if there are any. */
  private static void parseAttributeTypeDefinitions(
      Schema schema, String schemaFile, boolean failOnError, List<Attribute> attrList)
          throws ConfigException
  {
    if (attrList != null)
    {
      List<String> definitions = new ArrayList<>();
      for (Attribute a : attrList)
      {
        for (ByteString v : a)
        {
          definitions.add(v.toString());
        }
      }
      try
      {
        schema.registerAttributeTypes(definitions, schemaFile, !failOnError);
      }
      catch (DirectoryException de)
      {
        logger.traceException(de);

        if (de.getResultCode().equals(ResultCode.CONSTRAINT_VIOLATION))
        {
          // Register it with the schema. We will allow duplicates, with the
          // later definition overriding any earlier definition, but we want
          // to trap them and log a warning.
          logger.warn(WARN_CONFIG_SCHEMA_CONFLICTING_ATTR_TYPE, schemaFile, de.getMessageObject());
          try
          {
            schema.registerAttributeTypes(definitions, schemaFile, true);
          }
          catch (DirectoryException e)
          {
            // This should never happen
            logger.traceException(e);
          }
        }
        else
        {
          LocalizableMessage message = WARN_CONFIG_SCHEMA_CANNOT_PARSE_ATTR_TYPE.get(schemaFile, de.getMessageObject());
          reportError(failOnError, de, message);
        }
      }
    }
  }

  /** Parse the objectclass definitions if there are any. */
  private static void parseObjectclassDefinitions(Schema schema,
      String schemaFile, boolean failOnError, List<Attribute> ocList)
      throws ConfigException
  {
    if (ocList != null)
    {
      List<String> definitions = new ArrayList<>();
      for (Attribute a : ocList)
      {
        for (ByteString v : a)
        {
          definitions.add(v.toString());
        }
      }
      try
      {
        schema.registerObjectClasses(definitions, schemaFile, !failOnError);
      }
      catch (DirectoryException de)
      {
        logger.traceException(de);

        if (de.getResultCode().equals(ResultCode.CONSTRAINT_VIOLATION))
        {
          // Register it with the schema. We will allow duplicates, with the
          // later definition overriding any earlier definition, but we want
          // to trap them and log a warning.
          logger.warn(WARN_CONFIG_SCHEMA_CONFLICTING_OC, schemaFile, de.getMessageObject());
          try
          {
            schema.registerObjectClasses(definitions, schemaFile, true);
          }
          catch (DirectoryException e)
          {
            // This should never happen
            logger.traceException(e);
          }
        }
        else
        {
          LocalizableMessage message = WARN_CONFIG_SCHEMA_CANNOT_PARSE_OC.get(schemaFile, de.getMessageObject());
          reportError(failOnError, de, message);
        }
      }
    }
  }

  /** Parse the name form definitions if there are any. */
  private static void parseNameFormDefinitions(Schema schema,
      String schemaFile, boolean failOnError, List<Attribute> nfList)
      throws ConfigException
  {
    if (nfList != null)
    {
      for (Attribute a : nfList)
      {
        for (ByteString v : a)
        {
          // Register it with the schema.  We will allow duplicates, with the
          // later definition overriding any earlier definition, but we want
          // to trap them and log a warning.
          try
          {
            schema.registerNameForm(v.toString(), schemaFile, failOnError);
          }
          catch (DirectoryException de)
          {
            logger.traceException(de);

            logger.warn(WARN_CONFIG_SCHEMA_CONFLICTING_NAME_FORM, schemaFile, de.getMessageObject());

            try
            {
              schema.registerNameForm(v.toString(), schemaFile, true);
            }
            catch (Exception e)
            {
              // This should never happen.
              logger.traceException(e);
            }
          }
        }
      }
    }
  }

  /** Parse the DIT content rule definitions if there are any. */
  private static void parseDITContentRuleDefinitions(Schema schema,
      String schemaFile, boolean failOnError, List<Attribute> dcrList)
      throws ConfigException
  {
    if (dcrList != null)
    {
      for (Attribute a : dcrList)
      {
        for (ByteString v : a)
        {
          // Parse the DIT content rule.
          DITContentRule dcr;
          try
          {
            dcr = DITContentRuleSyntax.decodeDITContentRule(v, schema, false);
            dcr.getExtraProperties().remove(SCHEMA_PROPERTY_FILENAME);
            setSchemaFile(dcr, schemaFile);
          }
          catch (DirectoryException de)
          {
            logger.traceException(de);

            LocalizableMessage message = WARN_CONFIG_SCHEMA_CANNOT_PARSE_DCR.get(
                    schemaFile, de.getMessageObject());
            reportError(failOnError, de, message);
            continue;
          }
          catch (Exception e)
          {
            logger.traceException(e);

            LocalizableMessage message = WARN_CONFIG_SCHEMA_CANNOT_PARSE_DCR.get(
                    schemaFile, v + ":  " + getExceptionMessage(e));
            reportError(failOnError, e, message);
            continue;
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
            logger.traceException(de);

            logger.warn(WARN_CONFIG_SCHEMA_CONFLICTING_DCR, schemaFile, de.getMessageObject());

            try
            {
              schema.registerDITContentRule(dcr, true);
            }
            catch (Exception e)
            {
              // This should never happen.
              logger.traceException(e);
            }
          }
        }
      }
    }
  }

  private static void reportError(boolean failOnError, Exception e,
      LocalizableMessage message) throws ConfigException
  {
    if (failOnError)
    {
      throw new ConfigException(message, e);
    }
    logger.error(message);
  }

  /** Parse the DIT structure rule definitions if there are any. */
  private static void parseDITStructureRuleDefinitions(Schema schema,
      String schemaFile, boolean failOnError, List<Attribute> dsrList)
      throws ConfigException
  {
    if (dsrList != null)
    {
      for (Attribute a : dsrList)
      {
        for (ByteString v : a)
        {
          // Parse the DIT content rule.
          DITStructureRule dsr;
          try
          {
            dsr = DITStructureRuleSyntax.decodeDITStructureRule(v, schema, false);
            dsr.getExtraProperties().remove(SCHEMA_PROPERTY_FILENAME);
            setSchemaFile(dsr, schemaFile);
          }
          catch (DirectoryException de)
          {
            logger.traceException(de);

            LocalizableMessage message = WARN_CONFIG_SCHEMA_CANNOT_PARSE_DSR.get(
                    schemaFile, de.getMessageObject());
            reportError(failOnError, de, message);
            continue;
          }
          catch (Exception e)
          {
            logger.traceException(e);

            LocalizableMessage message = WARN_CONFIG_SCHEMA_CANNOT_PARSE_DSR.get(
                    schemaFile, v + ":  " + getExceptionMessage(e));
            reportError(failOnError, e, message);
            continue;
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
            logger.traceException(de);

            logger.warn(WARN_CONFIG_SCHEMA_CONFLICTING_DSR, schemaFile, de.getMessageObject());

            try
            {
              schema.registerDITStructureRule(dsr, true);
            }
            catch (Exception e)
            {
              // This should never happen.
              logger.traceException(e);
            }
          }
        }
      }
    }
  }

  /** Parse the matching rule use definitions if there are any. */
  private static void parseMatchingRuleUseDefinitions(Schema schema,
      String schemaFile, boolean failOnError, List<Attribute> mruList)
      throws ConfigException
  {
    if (mruList != null)
    {
      for (Attribute a : mruList)
      {
        for (ByteString v : a)
        {
          // Register it with the schema.  We will allow duplicates, with the
          // later definition overriding any earlier definition, but we want
          // to trap them and log a warning.
          try
          {
            schema.registerMatchingRuleUse(v.toString(), schemaFile, failOnError);
          }
          catch (DirectoryException de)
          {
            logger.traceException(de);

            logger.warn(WARN_CONFIG_SCHEMA_CONFLICTING_MRU, schemaFile, de.getMessageObject());

            try
            {
              schema.registerMatchingRuleUse(v.toString(), schemaFile, true);
            }
            catch (Exception e)
            {
              // This should never happen.
              logger.traceException(e);
            }
          }
        }
      }
    }
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
  public static boolean isSchemaAttribute(Attribute attribute)
  {
    String attributeOid = attribute.getAttributeDescription().getAttributeType().getOID();
    return attributeOid.equals("2.5.21.1") ||
        attributeOid.equals("2.5.21.2") ||
        attributeOid.equals("2.5.21.4") ||
        attributeOid.equals("2.5.21.5") ||
        attributeOid.equals("2.5.21.6") ||
        attributeOid.equals("2.5.21.7") ||
        attributeOid.equals("2.5.21.8") ||
        attributeOid.equals("2.5.4.3") ||
        attributeOid.equals("1.3.6.1.4.1.1466.101.120.16") ||
        attributeOid.equals("cn-oid") ||
        attributeOid.equals("attributetypes-oid") ||
        attributeOid.equals("objectclasses-oid") ||
        attributeOid.equals("matchingrules-oid") ||
        attributeOid.equals("matchingruleuse-oid") ||
        attributeOid.equals("nameformdescription-oid") ||
        attributeOid.equals("ditcontentrules-oid") ||
        attributeOid.equals("ditstructurerules-oid") ||
        attributeOid.equals("ldapsyntaxes-oid");
  }
}
