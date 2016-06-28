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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.adapter.server3x.Converters;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.DITContentRule;
import org.forgerock.opendj.ldap.schema.DITStructureRule;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.MatchingRuleUse;
import org.forgerock.opendj.ldap.schema.NameForm;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.SchemaBuilder.SchemaBuilderHook;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.forgerock.opendj.ldap.schema.AttributeType.Builder;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Modification;
import org.opends.server.types.Schema;
import org.opends.server.types.Schema.SchemaUpdater;

import static org.forgerock.opendj.ldap.schema.SchemaValidationPolicy.*;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.util.ServerConstants.SCHEMA_PROPERTY_FILENAME;

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

  private static final String CORE_SCHEMA_FILE = "00-core.ldif";
  private static final String RFC_3112_SCHEMA_FILE = "03-rfc3112.ldif";

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
    File schemaDir = DirectoryServer.getEnvironmentConfig().getSchemaDirectory();
    return schemaDir != null ? schemaDir.getAbsolutePath() : null;
  }

  /**
   * Retrieves a reference to the schema information that has been read from the server
   * configuration.
   * <p>
   * Note that this information will not be complete until the {@link #initializeMatchingRules()},
   * {@link #initializeAttributeSyntaxes()} methods have been called.
   *
   * @return A reference to the schema information that has been read from the server configuration.
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
    AttributeSyntaxConfigManager syntaxConfigManager = new AttributeSyntaxConfigManager(serverContext);
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
    File schemaInstanceDir = schemaInstanceDirPath != null ? new File(schemaInstanceDirPath) : null;
    long oldestModificationTime   = -1L;
    long youngestModificationTime = -1L;
    List<String> fileNames;

    try
    {
      if (schemaInstanceDir == null || !schemaInstanceDir.exists())
      {
        throw new InitializationException(ERR_CONFIG_SCHEMA_NO_SCHEMA_DIR.get(schemaInstanceDirPath));
      }
      if (!schemaInstanceDir.isDirectory())
      {
        throw new InitializationException(ERR_CONFIG_SCHEMA_DIR_NOT_DIRECTORY.get(schemaInstanceDirPath));
      }

      File[] schemaInstanceDirFiles = schemaInstanceDir.listFiles(new SchemaFileFilter());
      fileNames = new ArrayList<>(schemaInstanceDirFiles.length);

      for (File f : schemaInstanceDirFiles)
      {
        if (f.isFile())
        {
          fileNames.add(f.getName());
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

      Collections.sort(fileNames);
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
   * @throws  ConfigException  If a configuration problem causes the schema
   *                           element initialization to fail.
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the schema elements that is not related
   *                                   to the server configuration.
   */
  public static void loadSchemaFile(Schema schema, String schemaFile)
         throws ConfigException, InitializationException
  {
    loadSchemaFile(schema, schemaFile, true);
  }

  /**
   * Loads the contents of the specified schema file into the provided schema and returns the list
   * of modifications.
   *
   * @param schema
   *          The schema in which the contents of the schema file are to be loaded.
   * @param schemaFile
   *          The name of the schema file to be loaded into the provided schema.
   * @return A list of the modifications that could be performed in order to obtain the contents of
   *         the file.
   * @throws ConfigException
   *           If a configuration problem causes the schema element initialization to fail.
   * @throws InitializationException
   *           If a problem occurs while initializing the schema elements that is not related to the
   *           server configuration.
   */
  public static List<Modification> loadSchemaFileReturnModifications(Schema schema, String schemaFile)
      throws ConfigException, InitializationException
  {
    final Entry entry = loadSchemaFile(schema, schemaFile, true);
    if (entry != null)
    {
      return createAddModifications(entry,
          CoreSchema.getLDAPSyntaxesAttributeType(),
          CoreSchema.getAttributeTypesAttributeType(),
          CoreSchema.getObjectClassesAttributeType(),
          CoreSchema.getNameFormsAttributeType(),
          CoreSchema.getDITContentRulesAttributeType(),
          CoreSchema.getDITStructureRulesAttributeType(),
          CoreSchema.getMatchingRuleUseAttributeType());
    }
    return Collections.emptyList();
  }

  private static List<Modification> createAddModifications(Entry entry, AttributeType... attrTypes)
  {
    List<Modification> mods = new ArrayList<>(entry.getAttributeCount());
    for (AttributeType attrType : attrTypes)
    {
      for (Attribute a : entry.getAllAttributes(AttributeDescription.create(attrType)))
      {
        mods.add(new Modification(ModificationType.ADD, Converters.toAttribute(a)));
      }
    }
    return mods;
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
   * @return the schema entry that has been read from the schema file
   * @throws  ConfigException  If a configuration problem causes the schema
   *                           element initialization to fail.
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the schema elements that is not related
   *                                   to the server configuration.
   */
  private static Entry loadSchemaFile(Schema schema, String schemaFile, boolean failOnError)
      throws ConfigException, InitializationException
  {
    final Entry entry = readSchemaEntryFromFile(schemaFile, failOnError);
    if (entry != null)
    {
      updateSchemaWithEntry(schema, schemaFile, failOnError, entry);
    }
    return entry;
  }

  private static void updateSchemaWithEntry(Schema schema, String schemaFile, boolean failOnError,
      final Entry schemaEntry) throws ConfigException
  {
    try
    {
      // immediately overwrite these definitions which are already defined in the SDK core schema
      final boolean overwriteCoreSchemaDefinitions =
          CORE_SCHEMA_FILE.equals(schemaFile) || RFC_3112_SCHEMA_FILE.equals(schemaFile);
      updateSchema(schema, schemaFile, schemaEntry, overwriteCoreSchemaDefinitions);
    }
    catch (DirectoryException e)
    {
      if (e.getResultCode().equals(ResultCode.CONSTRAINT_VIOLATION))
      {
        // Register it with the schema. We will allow duplicates, with the
        // later definition overriding any earlier definition, but we want
        // to trap them and log a warning.
        logger.warn(WARN_CONFIG_CONFLICTING_DEFINITIONS_IN_SCHEMA_FILE, schemaFile, e.getMessageObject());
        try
        {
          updateSchema(schema, schemaFile, schemaEntry, true);
        }
        catch (DirectoryException e2)
        {
          // This should never happen
          logger.traceException(e2);
        }
      }
      else
      {
        reportError(failOnError, e,
            WARN_CONFIG_SCHEMA_CANNOT_PARSE_DEFINITIONS_IN_SCHEMA_FILE.get(schemaFile, e.getMessageObject()));
      }
    }
  }

  private static Entry readSchemaEntryFromFile(String schemaFile, boolean failOnError)
      throws ConfigException, InitializationException
  {
    // Create an LDIF reader to use when reading the files.
    String schemaDirPath = getSchemaDirectoryPath();
    File f = new File(schemaDirPath, schemaFile);
    try (final FileInputStream in = new FileInputStream(f);
        final LDIFEntryReader reader = new LDIFEntryReader(in))
    {
      reader.setSchemaValidationPolicy(ignoreAll());

      if (!reader.hasNext())
      {
        // The file was empty -- skip it.
        return null;
      }
      final Entry entry = reader.readEntry();
      if (reader.hasNext())
      {
        // If there are any more entries in the file, then print a warning message.
        logger.warn(WARN_CONFIG_SCHEMA_MULTIPLE_ENTRIES_IN_FILE, schemaFile, schemaDirPath);
      }
      return entry;
    }
    catch (FileNotFoundException e)
    {
      logger.traceException(e);

      LocalizableMessage message =
          WARN_CONFIG_SCHEMA_CANNOT_OPEN_FILE.get(schemaFile, schemaDirPath, getExceptionMessage(e));

      if (failOnError)
      {
        throw new ConfigException(message);
      }
      logger.error(message);
      return null;
    }
    catch (IOException e)
    {
      logger.traceException(e);

      LocalizableMessage message =
          WARN_CONFIG_SCHEMA_CANNOT_READ_LDIF_ENTRY.get(schemaFile, schemaDirPath, getExceptionMessage(e));

      if (failOnError)
      {
        throw new InitializationException(message, e);
      }
      logger.error(message);
      return null;
    }
  }

    private static void updateSchema(Schema schema, final String schemaFile, final Entry schemaEntry,
            final boolean overwrite) throws DirectoryException
  {
    schema.updateSchema(new SchemaUpdater()
    {
      @Override
      public org.forgerock.opendj.ldap.schema.Schema update(SchemaBuilder builder)
      {
        return builder.addSchema(schemaEntry, overwrite, new SchemaBuilderHook() {
            @Override
            public void beforeAddSyntax(Syntax.Builder builder) {
                builder.removeExtraProperty(SCHEMA_PROPERTY_FILENAME)
                       .extraProperties(SCHEMA_PROPERTY_FILENAME, schemaFile);
            }
            @Override
            public void beforeAddObjectClass(ObjectClass.Builder builder) {
                builder.removeExtraProperty(SCHEMA_PROPERTY_FILENAME)
                       .extraProperties(SCHEMA_PROPERTY_FILENAME, schemaFile);
            }
            @Override
            public void beforeAddNameForm(NameForm.Builder builder) {
                builder.removeExtraProperty(SCHEMA_PROPERTY_FILENAME)
                       .extraProperties(SCHEMA_PROPERTY_FILENAME, schemaFile);
            }
            @Override
            public void beforeAddMatchingRuleUse(MatchingRuleUse.Builder builder) {
                builder.removeExtraProperty(SCHEMA_PROPERTY_FILENAME)
                       .extraProperties(SCHEMA_PROPERTY_FILENAME, schemaFile);
            }
            @Override
            public void beforeAddMatchingRule(MatchingRule.Builder builder) {
                builder.removeExtraProperty(SCHEMA_PROPERTY_FILENAME)
                       .extraProperties(SCHEMA_PROPERTY_FILENAME, schemaFile);
            }
            @Override
            public void beforeAddDitStructureRule(DITStructureRule.Builder builder) {
                builder.removeExtraProperty(SCHEMA_PROPERTY_FILENAME)
                       .extraProperties(SCHEMA_PROPERTY_FILENAME, schemaFile);
            }
            @Override
            public void beforeAddDitContentRule(DITContentRule.Builder builder) {
                builder.removeExtraProperty(SCHEMA_PROPERTY_FILENAME)
                       .extraProperties(SCHEMA_PROPERTY_FILENAME, schemaFile);
            }
            @Override
            public void beforeAddAttribute(Builder builder) {
                builder.removeExtraProperty(SCHEMA_PROPERTY_FILENAME)
                       .extraProperties(SCHEMA_PROPERTY_FILENAME, schemaFile);
            }
        }).toSchema();
      }
    });
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
}
