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
package org.opends.server.schema;

import static org.opends.server.util.SchemaUtils.is02ConfigLdif;

import static org.opends.server.util.SchemaUtils.parseSchemaFileFromElementDefinition;
import static org.forgerock.opendj.ldap.ModificationType.ADD;
import static org.forgerock.opendj.ldap.ModificationType.DELETE;
import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.opends.messages.BackendMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.CollectionUtils.newArrayList;
import static org.opends.server.util.CollectionUtils.newLinkedList;
import static org.opends.server.util.SchemaUtils.getElementSchemaFile;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.AVA;
import org.forgerock.opendj.ldap.Base64;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.DITStructureRule;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaElement;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.opends.server.api.AlertGenerator;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.Attributes;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.Modification;
import org.opends.server.util.BuildVersion;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.SchemaUtils;

/**
 * Provides support to write schema files to disk.
 */
class SchemaFilesWriter
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final AttributeType attributeTypesType = getAttributeTypesAttributeType();
  private static final AttributeType ditStructureRulesType = getDITStructureRulesAttributeType();
  private static final AttributeType ditContentRulesType = getDITContentRulesAttributeType();
  private static final AttributeType ldapSyntaxesType = getLDAPSyntaxesAttributeType();
  private static final AttributeType matchingRuleUsesType = getMatchingRuleUseAttributeType();
  private static final AttributeType nameFormsType = getNameFormsAttributeType();
  private static final AttributeType objectClassesType = getObjectClassesAttributeType();

  private final ServerContext serverContext;

  /**
   * Creates a schema writer.
   *
   * @param serverContext
   *            The server context.
   */
  public SchemaFilesWriter(ServerContext serverContext)
  {
    this.serverContext = serverContext;
  }

  /**
   * Rewrites all schema files defined in the provided list of modified files with the provided
   * schema.
   *
   * @param newSchema
   *          The new schema that should be used.
   * @param extraAttributes
   *          The extra attributes to write in user schema file.
   * @param modifiedSchemaFiles
   *          The list of files that should be modified.
   * @param alertGenerator
   *          The alert generator.
   * @throws DirectoryException
   *           When the new file cannot be written.
   */
  public void updateSchemaFiles(Schema newSchema, Collection<Attribute> extraAttributes,
      TreeSet<String> modifiedSchemaFiles, AlertGenerator alertGenerator)
          throws DirectoryException
  {
    /*
     * We'll re-write all impacted schema files by first creating them in a temporary location
     * and then replacing the existing schema files with the new versions.
     * If all that goes successfully, then activate the new schema.
     */
    HashMap<String, File> tempSchemaFiles = new HashMap<>();
    try
    {
      for (String schemaFile : modifiedSchemaFiles)
      {
        File tempSchemaFile = writeTempSchemaFile(newSchema, extraAttributes, schemaFile);
        tempSchemaFiles.put(schemaFile, tempSchemaFile);
      }

      installSchemaFiles(alertGenerator, tempSchemaFiles);
    }
    catch (DirectoryException de)
    {
      logger.traceException(de);

      throw de;
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message =
          ERR_SCHEMA_MODIFY_CANNOT_WRITE_NEW_SCHEMA.get(getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message, e);
    }
    finally
    {
      cleanUpTempSchemaFiles(tempSchemaFiles);
    }

    // Create a single file with all of the concatenated schema information
    // that we can use on startup to detect whether the schema files have been
    // edited with the server offline.
    writeConcatenatedSchema();
  }

  /**
   * Updates the concatenated schema if changes are detected in the current schema files
   * and return the modifications.
   * <p>
   * Identify any differences that may exist between the concatenated schema file from the last
   * online modification and the current schema files. If there are any differences, then they
   * should be from making changes to the schema files with the server offline.
   *
   * @return the list of modifications made offline on the schema.
   * @throws InitializationException
   *            If concatenated schema can't be updated
   */
  public List<Modification> updateConcatenatedSchemaIfChangesDetected() throws InitializationException
  {
    try
    {
      // First, generate lists of elements from the current schema.
      Set<String> newATs = new LinkedHashSet<>();
      Set<String> newOCs = new LinkedHashSet<>();
      Set<String> newNFs = new LinkedHashSet<>();
      Set<String> newDCRs = new LinkedHashSet<>();
      Set<String> newDSRs = new LinkedHashSet<>();
      Set<String> newMRUs = new LinkedHashSet<>();
      Set<String> newLSs = new LinkedHashSet<>();
      generateConcatenatedSchema(newATs, newOCs, newNFs, newDCRs, newDSRs, newMRUs, newLSs);

      // Next, generate lists of elements from the previous concatenated schema.
      // If there isn't a previous concatenated schema, then use the base
      // schema for the current revision.
      File concatFile = getConcatenatedSchemaFile();

      Set<String> oldATs = new LinkedHashSet<>();
      Set<String> oldOCs = new LinkedHashSet<>();
      Set<String> oldNFs = new LinkedHashSet<>();
      Set<String> oldDCRs = new LinkedHashSet<>();
      Set<String> oldDSRs = new LinkedHashSet<>();
      Set<String> oldMRUs = new LinkedHashSet<>();
      Set<String> oldLSs = new LinkedHashSet<>();
      readConcatenatedSchema(concatFile, oldATs, oldOCs, oldNFs, oldDCRs, oldDSRs, oldMRUs, oldLSs);

      // Create a list of modifications and add any differences between the old
      // and new schema into them.
      List<Modification> mods = new LinkedList<>();
      compareConcatenatedSchema(oldATs, newATs, attributeTypesType, mods);
      compareConcatenatedSchema(oldOCs, newOCs, objectClassesType, mods);
      compareConcatenatedSchema(oldNFs, newNFs, nameFormsType, mods);
      compareConcatenatedSchema(oldDCRs, newDCRs, ditContentRulesType, mods);
      compareConcatenatedSchema(oldDSRs, newDSRs, ditStructureRulesType, mods);
      compareConcatenatedSchema(oldMRUs, newMRUs, matchingRuleUsesType, mods);
      compareConcatenatedSchema(oldLSs, newLSs, ldapSyntaxesType, mods);
      if (!mods.isEmpty())
      {
        // TODO : Raise an alert notification.

        // Write a new concatenated schema file with the most recent information
        // so we don't re-find these same changes on the next startup.
        writeConcatenatedSchema();
      }
      return filterOutConfigSchemaElementFromModifications(mods);
    }
    catch (InitializationException ie)
    {
      throw ie;
    }
    catch (Exception e)
    {
      logger.traceException(e);

      logger.error(ERR_SCHEMA_ERROR_DETERMINING_SCHEMA_CHANGES, getExceptionMessage(e));
      return Collections.emptyList();
    }
  }

  private List<Modification> filterOutConfigSchemaElementFromModifications(final List<Modification> mods)
      throws DirectoryException
  {
    final List<Modification> filteredMods = new ArrayList<>();
    for (Modification modification : mods)
    {
      for (ByteString v : modification.getAttribute())
      {
        String definition = v.toString();
        if (!isFrom02ConfigLdif(definition))
        {
          filteredMods.add(modification);
        }
      }
    }
    return filteredMods;
  }

  private boolean isFrom02ConfigLdif(String definition) throws DirectoryException
  {
    return is02ConfigLdif(parseSchemaFileFromElementDefinition(definition));
  }

  /**
   * Writes a single file containing all schema element definitions, which can be used on startup to
   * determine whether the schema files were edited with the server offline.
   */
  public void writeConcatenatedSchema()
  {
    String concatFilePath = null;
    try
    {
      Set<String> attributeTypes = new LinkedHashSet<>();
      Set<String> objectClasses = new LinkedHashSet<>();
      Set<String> nameForms = new LinkedHashSet<>();
      Set<String> ditContentRules = new LinkedHashSet<>();
      Set<String> ditStructureRules = new LinkedHashSet<>();
      Set<String> matchingRuleUses = new LinkedHashSet<>();
      Set<String> ldapSyntaxes = new LinkedHashSet<>();
      generateConcatenatedSchema(attributeTypes, objectClasses, nameForms, ditContentRules, ditStructureRules,
          matchingRuleUses, ldapSyntaxes);

      File upgradeDirectory = getUpgradeDirectory();
      upgradeDirectory.mkdir();
      File concatFile = new File(upgradeDirectory, SCHEMA_CONCAT_FILE_NAME);
      concatFilePath = concatFile.getAbsolutePath();

      File tempFile = new File(concatFilePath + ".tmp");
      try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile, false)))
      {
        writeLines(writer,
            "dn: " + DirectoryServer.getSchemaDN(),
            "objectClass: top",
            "objectClass: ldapSubentry",
            "objectClass: subschema");

        writeLines(writer, ATTR_ATTRIBUTE_TYPES, attributeTypes);
        writeLines(writer, ATTR_OBJECTCLASSES, objectClasses);
        writeLines(writer, ATTR_NAME_FORMS, nameForms);
        writeLines(writer, ATTR_DIT_CONTENT_RULES, ditContentRules);
        writeLines(writer, ATTR_DIT_STRUCTURE_RULES, ditStructureRules);
        writeLines(writer, ATTR_MATCHING_RULE_USE, matchingRuleUses);
        writeLines(writer, ATTR_LDAP_SYNTAXES, ldapSyntaxes);
      }

      if (concatFile.exists())
      {
        concatFile.delete();
      }
      tempFile.renameTo(concatFile);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      // This is definitely not ideal, but it's not the end of the
      // world. The worst that should happen is that the schema
      // changes could potentially be sent to the other servers again
      // when this server is restarted, which shouldn't hurt anything.
      // Still, we should log a warning message.
      logger.error(ERR_SCHEMA_CANNOT_WRITE_CONCAT_SCHEMA_FILE, concatFilePath, getExceptionMessage(e));
    }
  }

  /**
   * Reads the files contained in the schema directory and generates a concatenated view of their
   * contents in the provided sets.
   *
   * @param attributeTypes
   *          The set into which to place the attribute types read from the schema files.
   * @param objectClasses
   *          The set into which to place the object classes read from the schema files.
   * @param nameForms
   *          The set into which to place the name forms read from the schema files.
   * @param ditContentRules
   *          The set into which to place the DIT content rules read from the schema files.
   * @param ditStructureRules
   *          The set into which to place the DIT structure rules read from the schema files.
   * @param matchingRuleUses
   *          The set into which to place the matching rule uses read from the schema files.
   * @param ldapSyntaxes
   *          The set into which to place the ldap syntaxes read from the schema files.
   * @throws IOException
   *           If a problem occurs while reading the schema file elements.
   */
  private void generateConcatenatedSchema(Set<String> attributeTypes, Set<String> objectClasses,
      Set<String> nameForms, Set<String> ditContentRules, Set<String> ditStructureRules, Set<String> matchingRuleUses,
      Set<String> ldapSyntaxes) throws IOException
  {
    // Get a sorted list of the files in the schema directory.
    TreeSet<File> schemaFiles = new TreeSet<>();
    String schemaDirectory = getSchemaDirectoryPath();

    final FilenameFilter filter = new SchemaUtils.SchemaFileFilter();
    for (File f : new File(schemaDirectory).listFiles(filter))
    {
      if (f.isFile())
      {
        schemaFiles.add(f);
      }
    }

    // Open each of the files in order and read the elements that they
    // contain, appending them to the appropriate lists.
    for (File f : schemaFiles)
    {
      List<StringBuilder> lines = readSchemaElementsFromLdif(f);

      // Iterate through each line in the list. Find the colon and
      // get the attribute name at the beginning. If it's something
      // that we don't recognize, then skip it. Otherwise, add the
      // X-SCHEMA-FILE extension and add it to the appropriate schema
      // element list.
      for (StringBuilder buffer : lines)
      {
        String line = buffer.toString().trim();
        parseSchemaLine(line, f.getName(), attributeTypes, objectClasses, nameForms, ditContentRules,
            ditStructureRules, matchingRuleUses, ldapSyntaxes);
      }
    }
  }

  /**
   * Reads data from the specified concatenated schema file into the provided sets.
   *
   * @param concatSchemaFile
   *          The concatenated schema file to be read.
   * @param attributeTypes
   *          The set into which to place the attribute types read from the concatenated schema
   *          file.
   * @param objectClasses
   *          The set into which to place the object classes read from the concatenated schema file.
   * @param nameForms
   *          The set into which to place the name forms read from the concatenated schema file.
   * @param ditContentRules
   *          The set into which to place the DIT content rules read from the concatenated schema
   *          file.
   * @param ditStructureRules
   *          The set into which to place the DIT structure rules read from the concatenated schema
   *          file.
   * @param matchingRuleUses
   *          The set into which to place the matching rule uses read from the concatenated schema
   *          file.
   * @param ldapSyntaxes
   *          The set into which to place the ldap syntaxes read from the concatenated schema file.
   * @throws IOException
   *           If a problem occurs while reading the schema file elements.
   */
  private static void readConcatenatedSchema(File concatSchemaFile, Set<String> attributeTypes,
      Set<String> objectClasses, Set<String> nameForms, Set<String> ditContentRules, Set<String> ditStructureRules,
      Set<String> matchingRuleUses, Set<String> ldapSyntaxes) throws IOException
  {
    try (BufferedReader reader = new BufferedReader(new FileReader(concatSchemaFile)))
    {
      String line;
      while ((line = reader.readLine()) != null)
      {
        parseSchemaLine(line, null, attributeTypes, objectClasses, nameForms, ditContentRules, ditStructureRules,
            matchingRuleUses, ldapSyntaxes);
      }
    }
  }

  /**
   * Compares the provided sets of schema element definitions and writes any differences found into
   * the given list of modifications.
   *
   * @param oldElements
   *          The set of elements of the specified type read from the previous concatenated schema
   *          files.
   * @param newElements
   *          The set of elements of the specified type read from the server's current schema.
   * @param elementType
   *          The attribute type associated with the schema element being compared.
   * @param mods
   *          The list of modifications into which any identified differences should be written.
   */
  private static void compareConcatenatedSchema(Set<String> oldElements, Set<String> newElements,
      AttributeType elementType, List<Modification> mods)
  {
    addModification(mods, DELETE, oldElements, newElements, elementType);
    addModification(mods, ADD, newElements, oldElements, elementType);
  }

  private static void addModification(List<Modification> mods, ModificationType modType, Set<String> included,
      Set<String> excluded, AttributeType attrType)
  {
    AttributeBuilder builder = new AttributeBuilder(attrType);
    for (String val : included)
    {
      if (!excluded.contains(val))
      {
        builder.add(val);
      }
    }

    if (!builder.isEmpty())
    {
      mods.add(new Modification(modType, builder.toAttribute()));
    }
  }

  private static void addSchemaDefinition(Set<String> definitions, String line, String attrName, String fileName)
      throws ParseException
  {
    definitions.add(getSchemaDefinition(line.substring(attrName.length()), fileName));
  }

  private static String getSchemaDefinition(String definition, String schemaFile) throws ParseException
  {
    if (definition.startsWith("::"))
    {
      // See OPENDJ-2792: the definition of the ds-cfg-csv-delimiter-char attribute type
      // had a space accidentally added after the closing parenthesis.
      // This was unfortunately interpreted as base64
      definition = ByteString.wrap(Base64.decode(definition.substring(2).trim()).toByteArray()).toString();
    }
    else if (definition.startsWith(":"))
    {
      definition = definition.substring(1).trim();
    }
    else
    {
      throw new ParseException(ERR_SCHEMA_COULD_NOT_PARSE_DEFINITION.get().toString(), 0);
    }

    return SchemaUtils.addSchemaFileToElementDefinitionIfAbsent(definition, schemaFile);
  }

  private static void parseSchemaLine(String definition, String fileName, Set<String> attributeTypes,
      Set<String> objectClasses, Set<String> nameForms, Set<String> ditContentRules, Set<String> ditStructureRules,
      Set<String> matchingRuleUses, Set<String> ldapSyntaxes)
  {
    String lowerLine = toLowerCase(definition);

    try
    {
      if (lowerLine.startsWith(ATTR_ATTRIBUTE_TYPES_LC))
      {
        addSchemaDefinition(attributeTypes, definition, ATTR_ATTRIBUTE_TYPES_LC, fileName);
      }
      else if (lowerLine.startsWith(ATTR_OBJECTCLASSES_LC))
      {
        addSchemaDefinition(objectClasses, definition, ATTR_OBJECTCLASSES_LC, fileName);
      }
      else if (lowerLine.startsWith(ATTR_NAME_FORMS_LC))
      {
        addSchemaDefinition(nameForms, definition, ATTR_NAME_FORMS_LC, fileName);
      }
      else if (lowerLine.startsWith(ATTR_DIT_CONTENT_RULES_LC))
      {
        addSchemaDefinition(ditContentRules, definition, ATTR_DIT_CONTENT_RULES_LC, fileName);
      }
      else if (lowerLine.startsWith(ATTR_DIT_STRUCTURE_RULES_LC))
      {
        addSchemaDefinition(ditStructureRules, definition, ATTR_DIT_STRUCTURE_RULES_LC, fileName);
      }
      else if (lowerLine.startsWith(ATTR_MATCHING_RULE_USE_LC))
      {
        addSchemaDefinition(matchingRuleUses, definition, ATTR_MATCHING_RULE_USE_LC, fileName);
      }
      else if (lowerLine.startsWith(ATTR_LDAP_SYNTAXES_LC))
      {
        addSchemaDefinition(ldapSyntaxes, definition, ATTR_LDAP_SYNTAXES_LC, fileName);
      }
    }
    catch (ParseException pe)
    {
      logger.error(ERR_SCHEMA_PARSE_LINE.get(definition, pe.getLocalizedMessage()));
    }
  }

  private static void writeLines(BufferedWriter writer, String... lines) throws IOException
  {
    for (String line : lines)
    {
      writer.write(line);
      writer.newLine();
    }
  }

  private static void writeLines(BufferedWriter writer, String beforeColumn, Set<String> lines) throws IOException
  {
    for (String line : lines)
    {
      writer.write(beforeColumn);
      writer.write(": ");
      writer.write(line);
      writer.newLine();
    }
  }

  private String getSchemaDirectoryPath()
  {
    File schemaDir = serverContext.getEnvironment().getSchemaDirectory();
    return schemaDir != null ? schemaDir.getAbsolutePath() : null;
  }

  /** Returns the upgrade directory of the server. */
  private File getUpgradeDirectory()
  {
    File configFile = serverContext.getEnvironment().getConfigFile();
    return new File(configFile.getParentFile(), "upgrade");
  }

  private File getConcatenatedSchemaFile() throws InitializationException
  {
    File upgradeDirectory = getUpgradeDirectory();
    File concatFile = new File(upgradeDirectory, SCHEMA_CONCAT_FILE_NAME);
    if (concatFile.exists())
    {
      return concatFile.getAbsoluteFile();
    }

    String fileName = SCHEMA_BASE_FILE_NAME_WITHOUT_REVISION + BuildVersion.instanceVersion().getRevision();
    concatFile = new File(upgradeDirectory, fileName);
    if (concatFile.exists())
    {
      return concatFile.getAbsoluteFile();
    }

    String runningUnitTestsStr = System.getProperty(PROPERTY_RUNNING_UNIT_TESTS);
    if ("true".equalsIgnoreCase(runningUnitTestsStr))
    {
      writeConcatenatedSchema();
      concatFile = new File(upgradeDirectory, SCHEMA_CONCAT_FILE_NAME);
      return concatFile.getAbsoluteFile();
    }
    throw new InitializationException(ERR_SCHEMA_CANNOT_FIND_CONCAT_FILE.get(upgradeDirectory.getAbsolutePath(),
        SCHEMA_CONCAT_FILE_NAME, concatFile.getName()));
  }

  private List<StringBuilder> readSchemaElementsFromLdif(File f) throws IOException, FileNotFoundException
  {
    final LinkedList<StringBuilder> lines = new LinkedList<>();

    try (BufferedReader reader = new BufferedReader(new FileReader(f)))
    {
      String line;
      while ((line = reader.readLine()) != null)
      {
        if (line.startsWith("#") || line.length() == 0)
        {
          continue;
        }
        else if (line.startsWith(" "))
        {
          lines.getLast().append(line.substring(1));
        }
        else
        {
          lines.add(new StringBuilder(line));
        }
      }
    }
    return lines;
  }

  /**
   * Creates an empty entry that may be used as the basis for a new schema file.
   *
   * @return An empty entry that may be used as the basis for a new schema file.
   */
  private org.opends.server.types.Entry createEmptySchemaEntry()
  {
    Map<ObjectClass,String> objectClasses = new LinkedHashMap<>();
    objectClasses.put(CoreSchema.getTopObjectClass(), OC_TOP);
    objectClasses.put(serverContext.getSchema().getObjectClass(OC_LDAP_SUBENTRY_LC), OC_LDAP_SUBENTRY);
    objectClasses.put(serverContext.getSchema().getObjectClass(OC_SUBSCHEMA), OC_SUBSCHEMA);

    Map<AttributeType,List<Attribute>> userAttributes = new LinkedHashMap<>();
    Map<AttributeType,List<Attribute>> operationalAttributes = new LinkedHashMap<>();

    DN  dn  = DirectoryServer.getSchemaDN();
    for (AVA ava : dn.rdn())
    {
      AttributeType type = ava.getAttributeType();
      Map<AttributeType, List<Attribute>> attrs = type.isOperational() ? operationalAttributes : userAttributes;
      attrs.put(type, newLinkedList(Attributes.create(type, ava.getAttributeValue())));
    }

    return new org.opends.server.types.Entry(dn, objectClasses,  userAttributes, operationalAttributes);
  }

  /**
   * Writes a temporary version of the specified schema file.
   *
   * @param schema
   *          The schema from which to take the definitions to be
   * @param schemaFile
   *          The name of the schema file to be written.
   * @throws DirectoryException
   *           If an unexpected problem occurs while identifying the schema definitions to include
   *           in the schema file.
   * @throws IOException
   *           If an unexpected error occurs while attempting to write the temporary schema file.
   * @throws LDIFException
   *           If an unexpected problem occurs while generating the LDIF representation of the
   *           schema entry.
   */
  private File writeTempSchemaFile(Schema schema, Collection<Attribute> extraAttributes, String schemaFile)
          throws DirectoryException, IOException, LDIFException
  {
    org.opends.server.types.Entry schemaEntry = createEmptySchemaEntry();

     /*
     * Add all of the ldap syntax descriptions to the schema entry. We do
     * this only for the real part of the ldapsyntaxes attribute. The real part
     * is read and write to/from the schema files.
     */
    Set<ByteString> values = getValuesForSchemaFile(getCustomSyntaxes(schema), schemaFile);
    addAttribute(schemaEntry, ldapSyntaxesType, values);

    // Add all of the appropriate attribute types to the schema entry.  We need
    // to be careful of the ordering to ensure that any superior types in the
    // same file are written before the subordinate types.
    values = getAttributeTypeValuesForSchemaFile(schema, schemaFile);
    addAttribute(schemaEntry, attributeTypesType, values);

    // Add all of the appropriate objectclasses to the schema entry.  We need
    // to be careful of the ordering to ensure that any superior classes in the
    // same file are written before the subordinate classes.
    values = getObjectClassValuesForSchemaFile(schema, schemaFile);
    addAttribute(schemaEntry, objectClassesType, values);

    // Add all of the appropriate name forms to the schema entry.  Since there
    // is no hierarchical relationship between name forms, we don't need to
    // worry about ordering.
    values = getValuesForSchemaFile(schema.getNameForms(), schemaFile);
    addAttribute(schemaEntry, nameFormsType, values);

    // Add all of the appropriate DIT content rules to the schema entry.  Since
    // there is no hierarchical relationship between DIT content rules, we don't
    // need to worry about ordering.
    values = getValuesForSchemaFile(schema.getDITContentRules(), schemaFile);
    addAttribute(schemaEntry, ditContentRulesType, values);

    // Add all of the appropriate DIT structure rules to the schema entry.  We
    // need to be careful of the ordering to ensure that any superior rules in
    // the same file are written before the subordinate rules.
    values = getDITStructureRuleValuesForSchemaFile(schema, schemaFile);
    addAttribute(schemaEntry, ditStructureRulesType, values);

    // Add all of the appropriate matching rule uses to the schema entry.  Since
    // there is no hierarchical relationship between matching rule uses, we
    // don't need to worry about ordering.
    values = getValuesForSchemaFile(schema.getMatchingRuleUses(), schemaFile);
    addAttribute(schemaEntry, matchingRuleUsesType, values);

    if (FILE_USER_SCHEMA_ELEMENTS.equals(schemaFile))
    {
      for (Attribute attribute : extraAttributes)
      {
        AttributeType attributeType = attribute.getAttributeDescription().getAttributeType();
        schemaEntry.putAttribute(attributeType, newArrayList(attribute));
      }
    }

    // Create a temporary file to which we can write the schema entry.
    File tempFile = File.createTempFile(schemaFile, "temp");
    LDIFExportConfig exportConfig =
         new LDIFExportConfig(tempFile.getAbsolutePath(),
                              ExistingFileBehavior.OVERWRITE);
    try (LDIFWriter ldifWriter = new LDIFWriter(exportConfig))
    {
      ldifWriter.writeEntry(schemaEntry);
    }

    return tempFile;
  }

  /**
   * Returns custom syntaxes defined by OpenDJ configuration or by users.
   * <p>
   * These are non-standard syntaxes.
   *
   * @param schema
   *          the schema where to extract custom syntaxes from
   * @return custom, non-standard syntaxes
   */
  private Collection<Syntax> getCustomSyntaxes(Schema schema)
  {
    List<Syntax> results = new ArrayList<>();
    for (Syntax syntax : schema.getSyntaxes())
    {
      for (String propertyName : syntax.getExtraProperties().keySet())
      {
        if ("x-subst".equalsIgnoreCase(propertyName)
            || "x-pattern".equalsIgnoreCase(propertyName)
            || "x-enum".equalsIgnoreCase(propertyName)
            || "x-schema-file".equalsIgnoreCase(propertyName))
        {
          results.add(syntax);
          break;
        }
      }
    }
    return results;
  }

  private Set<ByteString> getValuesForSchemaFile(Collection<? extends SchemaElement> schemaElements, String schemaFile)
  {
    Set<ByteString> values = new LinkedHashSet<>();
    for (SchemaElement schemaElement : schemaElements)
    {
      if (schemaFile.equals(getElementSchemaFile(schemaElement)))
      {
        values.add(ByteString.valueOfUtf8(schemaElement.toString()));
      }
    }
    return values;
  }

  private Set<ByteString> getAttributeTypeValuesForSchemaFile(Schema schema,
      String schemaFile) throws DirectoryException
  {
    Set<AttributeType> addedTypes = new HashSet<>();
    Set<ByteString> values = new LinkedHashSet<>();
    for (AttributeType at : schema.getAttributeTypes())
    {
      if (schemaFile.equals(getElementSchemaFile(at)))
      {
        addAttrTypeToSchemaFile(schemaFile, at, values, addedTypes, 0);
      }
    }
    return values;
  }

  private Set<ByteString> getObjectClassValuesForSchemaFile(Schema schema,
      String schemaFile) throws DirectoryException
  {
    Set<ObjectClass> addedClasses = new HashSet<>();
    Set<ByteString> values = new LinkedHashSet<>();
    for (ObjectClass oc : schema.getObjectClasses())
    {
      if (schemaFile.equals(getElementSchemaFile(oc)))
      {
        addObjectClassToSchemaFile(schemaFile, oc, values, addedClasses, 0);
      }
    }
    return values;
  }

  private Set<ByteString> getDITStructureRuleValuesForSchemaFile(Schema schema,
      String schemaFile) throws DirectoryException
  {
    Set<DITStructureRule> addedDSRs = new HashSet<>();
    Set<ByteString> values = new LinkedHashSet<>();
    for (DITStructureRule dsr : schema.getDITStuctureRules())
    {
      if (schemaFile.equals(getElementSchemaFile(dsr)))
      {
        addDITStructureRuleToSchemaFile(schemaFile, dsr, values, addedDSRs, 0);
      }
    }
    return values;
  }

  private void addAttribute(org.opends.server.types.Entry schemaEntry, AttributeType attrType, Set<ByteString> values)
  {
    if (!values.isEmpty())
    {
      AttributeBuilder builder = new AttributeBuilder(attrType);
      builder.addAll(values);
      schemaEntry.putAttribute(attrType, newArrayList(builder.toAttribute()));
    }
  }

  /**
   * Adds the definition for the specified attribute type to the provided set of attribute values,
   * recursively adding superior types as appropriate.
   *
   * @param schema
   *          The schema containing the attribute type.
   * @param schemaFile
   *          The schema file with which the attribute type is associated.
   * @param attributeType
   *          The attribute type whose definition should be added to the value set.
   * @param values
   *          The set of values for attribute type definitions already added.
   * @param addedTypes
   *          The set of attribute types whose definitions have already been added to the set of
   *          values.
   * @param depth
   *          A depth counter to use in an attempt to detect circular references.
   */
  private void addAttrTypeToSchemaFile(String schemaFile, AttributeType attributeType, Set<ByteString> values,
      Set<AttributeType> addedTypes, int depth) throws DirectoryException
  {
    if (depth > 20)
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_CIRCULAR_REFERENCE_AT.get(
          attributeType.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    if (addedTypes.contains(attributeType))
    {
      return;
    }

    AttributeType superiorType = attributeType.getSuperiorType();
    if (superiorType != null &&
        schemaFile.equals(getElementSchemaFile(attributeType)) &&
        !addedTypes.contains(superiorType))
    {
      addAttrTypeToSchemaFile(schemaFile, superiorType, values, addedTypes, depth+1);
    }

    values.add(ByteString.valueOfUtf8(attributeType.toString()));
    addedTypes.add(attributeType);
  }

  /**
   * Adds the definition for the specified objectclass to the provided set of attribute values,
   * recursively adding superior classes as appropriate.
   *
   * @param schemaFile
   *          The schema file with which the objectclass is associated.
   * @param objectClass
   *          The objectclass whose definition should be added to the value set.
   * @param values
   *          The set of values for objectclass definitions already added.
   * @param addedClasses
   *          The set of objectclasses whose definitions have already been added to the set of
   *          values.
   * @param depth
   *          A depth counter to use in an attempt to detect circular references.
   */
  private void addObjectClassToSchemaFile(String schemaFile, ObjectClass objectClass, Set<ByteString> values,
      Set<ObjectClass> addedClasses, int depth) throws DirectoryException
  {
    if (depth > 20)
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_CIRCULAR_REFERENCE_OC.get(
          objectClass.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    if (addedClasses.contains(objectClass))
    {
      return;
    }

    for(ObjectClass superiorClass : objectClass.getSuperiorClasses())
    {
      if (schemaFile.equals(getElementSchemaFile(superiorClass)) &&
          !addedClasses.contains(superiorClass))
      {
        addObjectClassToSchemaFile(schemaFile, superiorClass, values,
                                   addedClasses, depth+1);
      }
    }
    values.add(ByteString.valueOfUtf8(objectClass.toString()));
    addedClasses.add(objectClass);
  }

  /**
   * Adds the definition for the specified DIT structure rule to the provided set of attribute
   * values, recursively adding superior rules as appropriate.
   *
   * @param schema
   *          The schema containing the DIT structure rule.
   * @param schemaFile
   *          The schema file with which the DIT structure rule is associated.
   * @param ditStructureRule
   *          The DIT structure rule whose definition should be added to the value set.
   * @param values
   *          The set of values for DIT structure rule definitions already added.
   * @param addedDSRs
   *          The set of DIT structure rules whose definitions have already been added added to the
   *          set of values.
   * @param depth
   *          A depth counter to use in an attempt to detect circular references.
   */
  private void addDITStructureRuleToSchemaFile(String schemaFile, DITStructureRule ditStructureRule,
      Set<ByteString> values, Set<DITStructureRule> addedDSRs, int depth) throws DirectoryException
  {
    if (depth > 20)
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_CIRCULAR_REFERENCE_DSR.get(
          ditStructureRule.getNameOrRuleID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    if (addedDSRs.contains(ditStructureRule))
    {
      return;
    }

    for (DITStructureRule dsr : ditStructureRule.getSuperiorRules())
    {
      if (schemaFile.equals(getElementSchemaFile(dsr)) && !addedDSRs.contains(dsr))
      {
        addDITStructureRuleToSchemaFile(schemaFile, dsr, values,
                                        addedDSRs, depth+1);
      }
    }

    values.add(ByteString.valueOfUtf8(ditStructureRule.toString()));
    addedDSRs.add(ditStructureRule);
  }

  /**
   * Moves the specified temporary schema files in place of the active versions. If an error occurs
   * in the process, then this method will attempt to restore the original schema files if possible.
   *
   * @param tempSchemaFiles
   *          The set of temporary schema files to be activated.
   * @throws DirectoryException
   *           If a problem occurs while attempting to install the temporary schema files.
   * @throws InitializationException
   *           If directory of schema files can't be retrieved
   */
  private void installSchemaFiles(AlertGenerator alertGenerator, HashMap<String,File> tempSchemaFiles)
          throws DirectoryException, InitializationException
  {
    // Create lists that will hold the three types of files we'll be dealing
    // with (the temporary files that will be installed, the installed schema
    // files, and the previously-installed schema files).
    ArrayList<File> installedFileList = new ArrayList<>();
    ArrayList<File> tempFileList      = new ArrayList<>();
    ArrayList<File> origFileList      = new ArrayList<>();

    File schemaInstanceDir = serverContext.getSchemaHandler().getSchemaDirectoryPath();

    for (Map.Entry<String, File> entry : tempSchemaFiles.entrySet())
    {
      String name = entry.getKey();
      installedFileList.add(new File(schemaInstanceDir, name));
      tempFileList.add(entry.getValue());
      origFileList.add(new File(schemaInstanceDir, name + ".orig"));
    }

    // If there are any old ".orig" files laying around from a previous
    // attempt, then try to clean them up.
    for (File f : origFileList)
    {
      if (f.exists())
      {
        f.delete();
      }
    }

    // Copy all of the currently-installed files with a ".orig" extension.  If
    // this fails, then try to clean up the copies.
    try
    {
      for (int i=0; i < installedFileList.size(); i++)
      {
        File installedFile = installedFileList.get(i);
        File origFile      = origFileList.get(i);

        if (installedFile.exists())
        {
          Files.copy(installedFile.toPath(), origFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      boolean allCleaned = true;
      for (File f : origFileList)
      {
        try
        {
          if (f.exists() && !f.delete())
          {
            allCleaned = false;
          }
        }
        catch (Exception e2)
        {
          logger.traceException(e2);

          allCleaned = false;
        }
      }

      LocalizableMessage message;
      if (allCleaned)
      {
        message = ERR_SCHEMA_MODIFY_CANNOT_WRITE_ORIG_FILES_CLEANED.get(getExceptionMessage(e));
      }
      else
      {
        message = ERR_SCHEMA_MODIFY_CANNOT_WRITE_ORIG_FILES_NOT_CLEANED.get(getExceptionMessage(e));
        DirectoryServer.sendAlertNotification(alertGenerator, ALERT_TYPE_CANNOT_COPY_SCHEMA_FILES, message);
      }
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message, e);
    }

    // Try to copy all of the temporary files into place over the installed
    // files.  If this fails, then try to restore the originals.
    try
    {
      for (int i=0; i < installedFileList.size(); i++)
      {
        File installedFile = installedFileList.get(i);
        File tempFile      = tempFileList.get(i);
        Files.copy(tempFile.toPath(), installedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      deleteFiles(installedFileList);

      boolean allRestored = true;
      for (int i=0; i < installedFileList.size(); i++)
      {
        File installedFile = installedFileList.get(i);
        File origFile      = origFileList.get(i);

        try
        {
          if (origFile.exists() && !origFile.renameTo(installedFile))
          {
            allRestored = false;
          }
        }
        catch (Exception e2)
        {
          logger.traceException(e2);

          allRestored = false;
        }
      }

      LocalizableMessage message;
      if (allRestored)
      {
        message = ERR_SCHEMA_MODIFY_CANNOT_WRITE_NEW_FILES_RESTORED.get(getExceptionMessage(e));
      }
      else
      {
        message = ERR_SCHEMA_MODIFY_CANNOT_WRITE_NEW_FILES_NOT_RESTORED.get(getExceptionMessage(e));
        DirectoryServer.sendAlertNotification(alertGenerator, ALERT_TYPE_CANNOT_WRITE_NEW_SCHEMA_FILES, message);
      }
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message, e);
    }

    deleteFiles(origFileList);
    deleteFiles(tempFileList);
  }

  /**
   * Performs any necessary cleanup in an attempt to delete any temporary schema
   * files that may have been left over after trying to install the new schema.
   *
   * @param  tempSchemaFiles  The set of temporary schema files that have been
   *                          created and are candidates for cleanup.
   */
  private void cleanUpTempSchemaFiles(HashMap<String,File> tempSchemaFiles)
  {
    deleteFiles(tempSchemaFiles.values());
  }
}
