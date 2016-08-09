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
 * Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.core;

import static org.opends.messages.SchemaMessages.ERR_PARSING_LDAP_SYNTAX_OID;

import static org.opends.messages.SchemaMessages.ERR_PARSING_MATCHING_RULE_USE_OID;
import static org.opends.messages.SchemaMessages.ERR_PARSING_DIT_STRUCTURE_RULE_RULEID;
import static org.opends.messages.SchemaMessages.ERR_PARSING_DIT_CONTENT_RULE_OID;
import static org.opends.messages.SchemaMessages.ERR_PARSING_NAME_FORM_OID;
import static org.opends.messages.SchemaMessages.ERR_PARSING_OBJECTCLASS_OID;
import static org.opends.messages.SchemaMessages.ERR_PARSING_ATTRIBUTE_TYPE_OID;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_TRUNCATED_VALUE1;
import static org.opends.messages.ConfigMessages.WARN_CONFIG_SCHEMA_CANNOT_OPEN_FILE;
import static org.opends.server.util.ServerConstants.SCHEMA_PROPERTY_FILENAME;
import static org.opends.messages.SchemaMessages.ERR_SCHEMA_HAS_WARNINGS;
import static org.forgerock.util.Utils.*;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.opends.server.replication.plugin.HistoricalCsnOrderingMatchingRuleImpl;
import org.opends.server.schema.AciSyntax;
import org.opends.server.schema.SubtreeSpecificationSyntax;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg1;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.ClassPropertyDefinition;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.ConflictingSchemaElementException;
import org.forgerock.opendj.ldap.schema.DITContentRule;
import org.forgerock.opendj.ldap.schema.DITStructureRule;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.MatchingRuleUse;
import org.forgerock.opendj.ldap.schema.NameForm;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.SchemaValidationPolicy;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.forgerock.opendj.ldap.schema.AttributeType.Builder;
import org.forgerock.opendj.ldap.schema.SchemaBuilder.SchemaBuilderHook;
import org.forgerock.opendj.ldif.EntryReader;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.opendj.server.config.meta.SchemaProviderCfgDefn;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.forgerock.opendj.server.config.server.SchemaProviderCfg;
import org.forgerock.util.Option;
import org.forgerock.util.Utils;
import org.opends.server.schema.SchemaProvider;
import org.opends.server.types.Attribute;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.util.ActivateOnceSDKSchemaIsUsed;
import org.opends.server.util.StaticUtils;

import com.forgerock.opendj.util.SubstringReader;

/**
 * Responsible for loading the server schema.
 * <p>
 * The schema is loaded in three steps :
 * <ul>
 *   <li>Start from the core schema.</li>
 *   <li>Load schema elements from the schema providers defined in configuration.</li>
 *   <li>Load all schema files located in the schema directory.</li>
 * </ul>
 */
@ActivateOnceSDKSchemaIsUsed
public final class SchemaHandler
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final String CORE_SCHEMA_PROVIDER_NAME = "Core Schema";
  private static final String CORE_SCHEMA_FILE = "00-core.ldif";
  private static final String RFC_3112_SCHEMA_FILE = "03-rfc3112.ldif";

  private ServerContext serverContext;

  /**
   * The schema.
   * <p>
   * @GuardedBy("exclusiveLock")
   */
  private volatile Schema schemaNG;

  /**
   * A set of extra attributes that are not used directly by the schema but may
   * be used by other component to store information in the schema.
   * <p>
   * ex : Replication uses this to store its state and GenerationID.
   */
  private Map<String, Attribute> extraAttributes = new HashMap<>();

  /** Guards updates to the schema. */
  private final Lock exclusiveLock = new ReentrantLock();

  private long oldestModificationTime = -1L;

  private long youngestModificationTime = -1L;

  /**
   * Creates a new instance.
   */
  public SchemaHandler()
  {
    // no implementation.
  }

  /**
   * Initialize this schema handler.
   *
   * @param serverContext
   *          The server context.
   * @throws ConfigException
   *           If a configuration problem arises in the process of performing
   *           the initialization.
   * @throws InitializationException
   *           If a problem that is not configuration-related occurs during
   *           initialization.
   */
  public void initialize(final ServerContext serverContext) throws InitializationException, ConfigException
  {
    this.serverContext = serverContext;

    exclusiveLock.lock();
    try
    {
      // Start from the core schema
      final SchemaBuilder schemaBuilder = new SchemaBuilder(Schema.getCoreSchema());

      loadSchemaFromProviders(serverContext.getRootConfig(), schemaBuilder);

      try
      {
        // Add server specific syntaxes and matching rules not provided by the SDK
        AciSyntax.addAciSyntax(schemaBuilder);
        SubtreeSpecificationSyntax.addSubtreeSpecificationSyntax(schemaBuilder);
        HistoricalCsnOrderingMatchingRuleImpl.addHistoricalCsnOrderingMatchingRule(schemaBuilder);
      }
      catch (ConflictingSchemaElementException e)
      {
        throw new ConfigException(e.getMessageObject(), e);
      }

      completeSchemaFromFiles(schemaBuilder);

      try
      {
        switchSchema(schemaBuilder.toSchema());
      }
      catch (DirectoryException e)
      {
        throw new ConfigException(e.getMessageObject(), e);
      }
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  /**
   * Returns the schema.
   *
   * @return the schema
   */
  public Schema getSchema()
  {
    return schemaNG;
  }

  /**
   * Update the schema using the provided schema updater.
   * <p>
   * An implicit lock is performed, so it is in general not necessary
   * to call the {code lock()}  and {code unlock() methods.
   * However, these method should be used if/when the SchemaBuilder passed
   * as an argument to the updater is not used to return the schema
   * (see for example usage in {@code CoreSchemaProvider} class). This
   * case should remain exceptional.
   *
   * @param updater
   *          the updater that returns a new schema
   * @throws DirectoryException if there is any problem updating the schema
   */
  public void updateSchema(SchemaUpdater updater) throws DirectoryException
  {
    exclusiveLock.lock();
    try
    {
      switchSchema(updater.update(new SchemaBuilder(schemaNG)));
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  /**
   * Update this handler with the provided schema.
   *
   * @param schema
   *          the new schema to use
   * @throws DirectoryException
   *            if the provided schema contains warnings
   */
  public void updateSchema(Schema schema) throws DirectoryException
  {
    exclusiveLock.lock();
    try
    {
      switchSchema(schema);
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  /**
   * Updates the schema option  if the new value differs from the old value.
   *
   * @param <T> the schema option's type
   * @param option the schema option to update
   * @param newValue the new value for the schema option
   * @throws DirectoryException if there is any problem updating the schema
   */
  public <T> void updateSchemaOption(final Option<T> option, final T newValue) throws DirectoryException
  {
    final T oldValue = schemaNG.getOption(option);
    if (!oldValue.equals(newValue))
    {
      updateSchema(new SchemaUpdater()
      {
        @Override
        public Schema update(SchemaBuilder builder)
        {
          return builder.setOption(option, newValue).toSchema();
        }
      });
    }
  }

  /**
   * Put (add or replace) a new extra Attribute to this schema handler.
   *
   * @param  name     The identifier of the extra Attribute.
   *
   * @param  attr     The extra attribute that must be added to
   *                  this schema handler.
   */
  public void putExtraAttribute(String name, Attribute attr)
  {
    extraAttributes.put(name, attr);
  }

  /**
   * Returns the extra Attributes stored in this schema handler.
   *
   * @return  The extra Attributes.
   */
  public Collection<Attribute> getExtraAttributes()
  {
    return extraAttributes.values();
  }

  /**
   * Adds the provided schema file to the provided schema element definition.
   *
   * @param definition
   *            The schema element definition
   * @param schemaFile
   *            The name of the schema file to include in the definition
   * @return  The definition string of the element
   *          including the X-SCHEMA-FILE extension.
   */
  public static String addSchemaFileToElementDefinitionIfAbsent(String definition, String schemaFile)
  {
    if (schemaFile != null && !definition.contains(SCHEMA_PROPERTY_FILENAME))
    {
      int pos = definition.lastIndexOf(')');
      return definition.substring(0, pos).trim() + " " + SCHEMA_PROPERTY_FILENAME + " '" + schemaFile + "' )";
    }
    return definition;
  }

  /**
   * Parses the schema file (value of X-SCHEMA-FILE extension) from the provided schema element
   * definition.
   * <p>
   * It expects a single value for the X-SCHEMA-FILE extension, e.g.:
   * "X-SCHEMA-FILE '99-user.ldif'", as there is no sensible meaning for multiple values.
   *
   * @param definition
   *          The definition of a schema element
   * @return the value of the schema file or {@code null} if the X-SCHEMA-FILE extension is not
   *         present in the definition
   * @throws DirectoryException
   *            If an error occurs while parsing the schema element definition
   */
  public static String parseSchemaFileFromElementDefinition(String definition) throws DirectoryException
  {
    int pos = definition.lastIndexOf(SCHEMA_PROPERTY_FILENAME);
    if (pos == -1)
    {
      return null;
    }

    SubstringReader reader = new SubstringReader(definition);
    reader.read(pos + SCHEMA_PROPERTY_FILENAME.length());

    int length = 0;
    reader.skipWhitespaces();
    reader.mark();
    try
    {
      // Accept both a quoted value or an unquoted value
      char c = reader.read();
      if (c == '\'')
      {
        reader.mark();
        // Parse until the closing quote.
        while (reader.read() != '\'')
        {
          length++;
        }
      }
      else
      {
        // Parse until the next space.
        do
        {
          length++;
        }
        while (reader.read() != ' ');
      }
      reader.reset();
      return reader.read(length);
    }
    catch (final StringIndexOutOfBoundsException e)
    {
      // TODO : write the correct message = Error when trying to parse the schema file from a schema
      // element definition
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, LocalizableMessage.raw(""));
    }
  }


  /** Takes an exclusive lock on the schema. */
  public void exclusiveLock()
  {
    exclusiveLock.lock();
  }

  /** Releases an exclusive lock on the schema. */
  public void exclusiveUnlock()
  {
    exclusiveLock.unlock();
  }

  /**
   * Load the schema from provided root configuration.
   *
   * @param rootConfiguration
   *          The root to retrieve schema provider configurations.
   * @param schemaBuilder
   *          The schema builder that providers should update.
   * @param schemaUpdater
   *          The updater that providers should use when applying a configuration change.
   */
  private void loadSchemaFromProviders(final RootCfg rootConfiguration, final SchemaBuilder schemaBuilder)
      throws ConfigException, InitializationException {
    for (final String name : rootConfiguration.listSchemaProviders())
    {
      final SchemaProviderCfg config = rootConfiguration.getSchemaProvider(name);
      if (config.isEnabled())
      {
        loadSchemaProvider(config.getJavaClass(), config, schemaBuilder, true);
      }
      else if (name.equals(CORE_SCHEMA_PROVIDER_NAME))
      {
        throw new ConfigException(ERR_CONFIG_CORE_SCHEMA_PROVIDER_DISABLED.get(config.dn()));
      }
    }
  }

  /**
   * Load the schema provider from the provided class name.
   * <p>
   * If {@code} initialize} is {@code true}, then the provider is initialized,
   * and the provided schema builder is updated with schema elements from the provider.
   */
  private <T extends SchemaProviderCfg> SchemaProvider<T> loadSchemaProvider(final String className,
      final T config, final SchemaBuilder schemaBuilder, final boolean initialize)
      throws InitializationException
  {
    try
    {
      final ClassPropertyDefinition propertyDef = SchemaProviderCfgDefn.getInstance().getJavaClassPropertyDefinition();
      final Class<? extends SchemaProvider> providerClass = propertyDef.loadClass(className, SchemaProvider.class);
      final SchemaProvider<T> provider = providerClass.newInstance();

      if (initialize)
      {
        provider.initialize(serverContext, config, schemaBuilder);
      }
      else
      {
        final List<LocalizableMessage> unacceptableReasons = new ArrayList<>();
        if (!provider.isConfigurationAcceptable(config, unacceptableReasons))
        {
          final String reasons = Utils.joinAsString(".  ", unacceptableReasons);
          throw new InitializationException(ERR_CONFIG_SCHEMA_PROVIDER_CONFIG_NOT_ACCEPTABLE.get(config.dn(), reasons));
        }
      }
      return provider;
    }
    catch (Exception e)
    {
      throw new InitializationException(ERR_CONFIG_SCHEMA_PROVIDER_CANT_BE_INITIALIZED.get(
          className, config.dn(), stackTraceToSingleLineString(e)), e);
    }
  }

  /**
   * Retrieves the path to the directory containing the server schema files.
   *
   * @return The path to the directory containing the server schema files.
   */
  private File getSchemaDirectoryPath() throws InitializationException
  {
    final File dir = serverContext.getEnvironment().getSchemaDirectory();
    if (dir == null)
    {
      throw new InitializationException(ERR_CONFIG_SCHEMA_NO_SCHEMA_DIR.get(null));
    }
    if (!dir.exists())
    {
      throw new InitializationException(ERR_CONFIG_SCHEMA_NO_SCHEMA_DIR.get(dir.getPath()));
    }
    if (!dir.isDirectory())
    {
      throw new InitializationException(ERR_CONFIG_SCHEMA_DIR_NOT_DIRECTORY.get(dir.getPath()));
    }
    return dir;
  }

  /** Returns the LDIF reader on provided LDIF file. The caller must ensure the reader is closed. */
  private EntryReader getLDIFReader(final File ldifFile, final Schema schema)
      throws InitializationException
  {
    try
    {
      final LDIFEntryReader reader = new LDIFEntryReader(new FileReader(ldifFile));
      reader.setSchema(schema);
      reader.setSchemaValidationPolicy(SchemaValidationPolicy.ignoreAll());
      return reader;
    }
    catch (FileNotFoundException e)
    {
      throw new InitializationException(WARN_CONFIG_SCHEMA_CANNOT_OPEN_FILE.get(ldifFile.getPath(),
          ldifFile.getParent(), StaticUtils.getExceptionMessage(e)));
    }
  }

  /**
   * Complete the schema with schema files.
   *
   * @param schemaBuilder
   *          The schema builder to update with the content of the schema files.
   * @throws ConfigException
   *           If a configuration problem causes the schema element
   *           initialization to fail.
   * @throws InitializationException
   *           If a problem occurs while initializing the schema elements that
   *           is not related to the server configuration.
   */
  private void completeSchemaFromFiles(final SchemaBuilder schemaBuilder)
      throws ConfigException, InitializationException
  {
    final File schemaDirectory = getSchemaDirectoryPath();
    for (String schemaFile : getSchemaFileNames(schemaDirectory))
    {
      loadSchemaFile(new File(schemaDirectory, schemaFile), schemaBuilder, Schema.getDefaultSchema());
    }
  }

  /** Returns the list of names of schema files contained in the provided directory. */
  private List<String> getSchemaFileNames(final File schemaDirectory) throws InitializationException {
    try
    {
      final File[] schemaFiles = schemaDirectory.listFiles(new SchemaFileFilter());
      final List<String> schemaFileNames = new ArrayList<>(schemaFiles.length);

      for (final File f : schemaFiles)
      {
        if (f.isFile())
        {
          schemaFileNames.add(f.getName());
        }

        final long modificationTime = f.lastModified();
        if (oldestModificationTime <= 0L
            || modificationTime < oldestModificationTime)
        {
          oldestModificationTime = modificationTime;
        }

        if (youngestModificationTime <= 0
            || modificationTime > youngestModificationTime)
        {
          youngestModificationTime = modificationTime;
        }
      }
      // If the oldest and youngest modification timestamps didn't get set
      // then set them to the current time.
      if (oldestModificationTime <= 0)
      {
        oldestModificationTime = System.currentTimeMillis();
      }

      if (youngestModificationTime <= 0)
      {
        youngestModificationTime = oldestModificationTime;
      }
      Collections.sort(schemaFileNames);
      return schemaFileNames;
    }
    catch (Exception e)
    {
      logger.traceException(e);
      throw new InitializationException(ERR_CONFIG_SCHEMA_CANNOT_LIST_FILES
          .get(schemaDirectory, getExceptionMessage(e)), e);
    }
  }

  /** Returns the schema entry from the provided reader, which may be {@code null} if file is empty. */
  private Entry readSchemaEntry(final EntryReader reader, final File schemaFile) throws InitializationException {
    try
    {
      if (!reader.hasNext())
      {
        // empty file, just skip it
        return null;
      }
      Entry entry = reader.readEntry();
      if (reader.hasNext())
      {
        logger.warn(WARN_CONFIG_SCHEMA_MULTIPLE_ENTRIES_IN_FILE, schemaFile.getPath(), schemaFile.getParent());
      }
      return entry;
    }
    catch (IOException e)
    {
      throw new InitializationException(WARN_CONFIG_SCHEMA_CANNOT_READ_LDIF_ENTRY.get(
              schemaFile.getPath(), schemaFile.getParent(), getExceptionMessage(e)), e);
    }
    finally
    {
      closeSilently(reader);
    }
  }

  /**
   * Add the schema from the provided schema file to the provided schema
   * builder.
   *
   * @param schemaFile
   *          the schema file to be loaded
   * @param schemaBuilder
   *          The schema builder in which the contents of the schema file are to
   *          be loaded.
   * @param readSchema
   *          The schema used to read the file.
   * @throws InitializationException
   *           If a problem occurs while initializing the schema elements.
   */
  private void loadSchemaFile(final File schemaFile, final SchemaBuilder schemaBuilder, final Schema readSchema)
         throws InitializationException, ConfigException
  {
    EntryReader reader = null;
    try
    {
      reader = getLDIFReader(schemaFile, readSchema);
      final Entry entry = readSchemaEntry(reader, schemaFile);
      if (entry != null)
      {
        boolean failOnError = true;
        updateSchemaBuilderWithEntry(schemaBuilder, entry, schemaFile.getName(), failOnError);
      }
    }
    finally {
      Utils.closeSilently(reader);
    }
  }

  private void updateSchemaBuilderWithEntry(SchemaBuilder schemaBuilder, Entry schemaEntry, String schemaFile,
      boolean failOnError) throws ConfigException
  {

    // immediately overwrite these definitions which are already defined in the SDK core schema
    final boolean overwriteCoreSchemaDefinitions =
        CORE_SCHEMA_FILE.equals(schemaFile) || RFC_3112_SCHEMA_FILE.equals(schemaFile);

    updateSchemaBuilderWithEntry0(schemaBuilder, schemaEntry, schemaFile, overwriteCoreSchemaDefinitions);

    // check that the update is correct
    Collection<LocalizableMessage> warnings = schemaBuilder.toSchema().getWarnings();
    if (!warnings.isEmpty())
    {
      if (!overwriteCoreSchemaDefinitions)
      {
        logger.warn(WARN_CONFIG_SCHEMA_FILE_HAS_SCHEMA_WARNING, schemaFile, warnings);
        // try to update again with overwriting
        updateSchemaBuilderWithEntry0(schemaBuilder, schemaEntry, schemaFile, true);
        warnings = schemaBuilder.toSchema().getWarnings();
        if (!warnings.isEmpty())
        {
          reportSchemaWarnings(WARN_CONFIG_SCHEMA_FILE_HAS_SCHEMA_WARNING_WITH_OVERWRITE.get(schemaFile, warnings),
              failOnError);
        }
      }
      else
      {
        reportSchemaWarnings(WARN_CONFIG_SCHEMA_FILE_HAS_SCHEMA_WARNING_WITH_OVERWRITE.get(schemaFile, warnings),
            failOnError);
      }
    }
  }

  private void updateSchemaBuilderWithEntry0(final SchemaBuilder schemaBuilder, final Entry schemaEntry,
      final String schemaFile, final boolean overwrite)
  {
    schemaBuilder.addSchema(schemaEntry, overwrite, new SchemaBuilderHook()
    {
      @Override
      public void beforeAddSyntax(Syntax.Builder builder)
      {
        builder.removeExtraProperty(SCHEMA_PROPERTY_FILENAME).extraProperties(SCHEMA_PROPERTY_FILENAME, schemaFile);
      }

      @Override
      public void beforeAddObjectClass(ObjectClass.Builder builder)
      {
        builder.removeExtraProperty(SCHEMA_PROPERTY_FILENAME).extraProperties(SCHEMA_PROPERTY_FILENAME, schemaFile);
      }

      @Override
      public void beforeAddNameForm(NameForm.Builder builder)
      {
        builder.removeExtraProperty(SCHEMA_PROPERTY_FILENAME).extraProperties(SCHEMA_PROPERTY_FILENAME, schemaFile);
      }

      @Override
      public void beforeAddMatchingRuleUse(MatchingRuleUse.Builder builder)
      {
        builder.removeExtraProperty(SCHEMA_PROPERTY_FILENAME).extraProperties(SCHEMA_PROPERTY_FILENAME, schemaFile);
      }

      @Override
      public void beforeAddMatchingRule(MatchingRule.Builder builder)
      {
        builder.removeExtraProperty(SCHEMA_PROPERTY_FILENAME).extraProperties(SCHEMA_PROPERTY_FILENAME, schemaFile);
      }

      @Override
      public void beforeAddDitStructureRule(DITStructureRule.Builder builder)
      {
        builder.removeExtraProperty(SCHEMA_PROPERTY_FILENAME).extraProperties(SCHEMA_PROPERTY_FILENAME, schemaFile);
      }

      @Override
      public void beforeAddDitContentRule(DITContentRule.Builder builder)
      {
        builder.removeExtraProperty(SCHEMA_PROPERTY_FILENAME).extraProperties(SCHEMA_PROPERTY_FILENAME, schemaFile);
      }

      @Override
      public void beforeAddAttribute(Builder builder)
      {
        builder.removeExtraProperty(SCHEMA_PROPERTY_FILENAME).extraProperties(SCHEMA_PROPERTY_FILENAME, schemaFile);
      }
    });
  }

  private void switchSchema(Schema newSchema) throws DirectoryException
  {
    rejectSchemaWithWarnings(newSchema);
    schemaNG = newSchema.asNonStrictSchema();
    Schema.setDefaultSchema(schemaNG);
  }

  private void rejectSchemaWithWarnings(Schema newSchema) throws DirectoryException
  {
    Collection<LocalizableMessage> warnings = newSchema.getWarnings();
    if (!warnings.isEmpty())
    {
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
          ERR_SCHEMA_HAS_WARNINGS.get(warnings.size(), Utils.joinAsString("; ", warnings)));
    }
  }

  private void reportSchemaWarnings(LocalizableMessage message, boolean failOnError) throws ConfigException
  {
    if (failOnError)
    {
      throw new ConfigException(message);
    }
    logger.error(message);
  }

  /** A file filter implementation that accepts only LDIF files. */
  private static class SchemaFileFilter implements FilenameFilter
  {
    private static final String LDIF_SUFFIX = ".ldif";

    @Override
    public boolean accept(File directory, String filename)
    {
      return filename.endsWith(LDIF_SUFFIX);
    }
  }

  /** Interface to update a schema provided a schema builder. */
  public interface SchemaUpdater
  {
    /**
     * Returns an updated schema.
     *
     * @param builder
     *          The builder on the current schema
     * @return the new schema
     * @throws DirectoryException
     *          If an error occurs during the schema update
     */
    Schema update(SchemaBuilder builder) throws DirectoryException;
  }

  /**
   * Returns the OID from the provided attribute type definition, assuming the definition is valid.
   * <p>
   * This method does not perform any check.
   *
   * @param definition
   *          The definition of an attribute type, assumed to be valid
   * @return the OID, which is never {@code null}
   * @throws DirectoryException
   *           If a problem occurs while parsing the definition
   */
  public static String parseAttributeTypeOID(String definition) throws DirectoryException
  {
    return parseOID(definition, ERR_PARSING_ATTRIBUTE_TYPE_OID);
  }

  /**
   * Returns the OID from the provided object class definition, assuming the definition is valid.
   * <p>
   * This method does not perform any check.
   *
   * @param definition
   *          The definition of a object class, assumed to be valid
   * @return the OID, which is never {@code null}
   * @throws DirectoryException
   *           If a problem occurs while parsing the definition
   */
  public static String parseObjectClassOID(String definition) throws DirectoryException
  {
    return parseOID(definition, ERR_PARSING_OBJECTCLASS_OID);
  }

  /**
   * Returns the OID from the provided name form definition, assuming the definition is valid.
   * <p>
   * This method does not perform any check.
   *
   * @param definition
   *          The definition of a name form, assumed to be valid
   * @return the OID, which is never {@code null}
   * @throws DirectoryException
   *           If a problem occurs while parsing the definition
   */
  public static String parseNameFormOID(String definition) throws DirectoryException
  {
    return parseOID(definition, ERR_PARSING_NAME_FORM_OID);
  }

  /**
   * Returns the OID from the provided DIT content rule definition, assuming the definition is valid.
   * <p>
   * This method does not perform any check.
   *
   * @param definition
   *          The definition of a DIT content rule, assumed to be valid
   * @return the OID, which is never {@code null}
   * @throws DirectoryException
   *           If a problem occurs while parsing the definition
   */
  public static String parseDITContentRuleOID(String definition) throws DirectoryException
  {
    return parseOID(definition, ERR_PARSING_DIT_CONTENT_RULE_OID);
  }

  /**
   * Returns the ruleID from the provided DIT structure rule definition, assuming the definition is
   * valid.
   * <p>
   * This method does not perform any check.
   *
   * @param definition
   *          The definition of a DIT structure rule, assumed to be valid
   * @return the OID, which is never {@code null}
   * @throws DirectoryException
   *           If a problem occurs while parsing the definition
   */
  public static int parseRuleID(String definition) throws DirectoryException
  {
    // Reuse code of parseOID, even though this is not an OID
    return Integer.parseInt(parseOID(definition, ERR_PARSING_DIT_STRUCTURE_RULE_RULEID));
  }

  /**
   * Returns the OID from the provided matching rule use definition, assuming the definition is valid.
   * <p>
   * This method does not perform any check.
   *
   * @param definition
   *          The definition of a matching rule use, assumed to be valid
   * @return the OID, which is never {@code null}
   * @throws DirectoryException
   *           If a problem occurs while parsing the definition
   */
  public static String parseMatchingRuleUseOID(String definition) throws DirectoryException
  {
    return parseOID(definition, ERR_PARSING_MATCHING_RULE_USE_OID);
  }

  /**
   * Returns the OID from the provided syntax definition, assuming the definition is valid.
   * <p>
   * This method does not perform any check.
   *
   * @param definition
   *          The definition of a syntax, assumed to be valid
   * @return the OID, which is never {@code null}
   * @throws DirectoryException
   *           If a problem occurs while parsing the definition
   */
  public static String parseSyntaxOID(String definition) throws DirectoryException
  {
    return parseOID(definition, ERR_PARSING_LDAP_SYNTAX_OID);
  }

  private static String parseOID(String definition, Arg1<Object> parsingErrorMsg) throws DirectoryException
  {
    try
    {
      int pos = 0;
      int length = definition.length();
      // Skip over any leading whitespace.
      while (pos < length && (definition.charAt(pos) == ' '))
      {
        pos++;
      }
      // Skip the open parenthesis.
      pos++;
      // Skip over any spaces immediately following the opening parenthesis.
      while (pos < length && definition.charAt(pos) == ' ')
      {
        pos++;
      }
      // The next set of characters must be the OID.
      int oidStartPos = pos;
      while (pos < length && definition.charAt(pos) != ' ' && definition.charAt(pos) != ')')
      {
        pos++;
      }
      return definition.substring(oidStartPos, pos);
    }
    catch (IndexOutOfBoundsException e)
    {
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, parsingErrorMsg.get(definition), e);
    }
  }
}
