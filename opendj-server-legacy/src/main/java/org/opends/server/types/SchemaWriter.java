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
 * Copyright 2016 ForgeRock AS.
 */
package org.opends.server.types;

import static org.opends.messages.BackendMessages.ERR_SCHEMA_CANNOT_FIND_CONCAT_FILE;
import static org.opends.server.util.ServerConstants.*;

import static org.opends.messages.BackendMessages.ERR_SCHEMA_COULD_NOT_PARSE_DEFINITION;
import static org.opends.messages.BackendMessages.ERR_SCHEMA_PARSE_LINE;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.messages.BackendMessages.ERR_SCHEMA_ERROR_DETERMINING_SCHEMA_CHANGES;
import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.forgerock.opendj.ldap.ModificationType.ADD;
import static org.forgerock.opendj.ldap.ModificationType.DELETE;
import static org.opends.messages.BackendMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.getExceptionMessage;
import static org.opends.server.util.StaticUtils.toLowerCase;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.ParseException;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SchemaConfigManager;
import org.opends.server.util.Base64;
import org.opends.server.util.BuildVersion;
import org.opends.server.util.SchemaUtils;

/**
 * Provides support to write schema files.
 */
public class SchemaWriter
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final AttributeType attributeTypesType = getAttributeTypesAttributeType();
  private static final AttributeType ditStructureRulesType = getDITStructureRulesAttributeType();
  private static final AttributeType ditContentRulesType = getDITContentRulesAttributeType();
  private static final AttributeType ldapSyntaxesType = getLDAPSyntaxesAttributeType();
  private static final AttributeType matchingRuleUsesType = getMatchingRuleUseAttributeType();
  private static final AttributeType nameFormsType = getNameFormsAttributeType();
  private static final AttributeType objectClassesType = getObjectClassesAttributeType();

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
  public static void compareConcatenatedSchema(Set<String> oldElements, Set<String> newElements,
      AttributeType elementType, List<Modification> mods)
  {
    AttributeBuilder builder = new AttributeBuilder(elementType);
    addModification(mods, DELETE, oldElements, newElements, builder);

    builder.setAttributeDescription(AttributeDescription.create(elementType));
    addModification(mods, ADD, newElements, oldElements, builder);
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
  public static void generateConcatenatedSchema(Set<String> attributeTypes, Set<String> objectClasses,
      Set<String> nameForms, Set<String> ditContentRules, Set<String> ditStructureRules, Set<String> matchingRuleUses,
      Set<String> ldapSyntaxes) throws IOException
  {
    // Get a sorted list of the files in the schema directory.
    TreeSet<File> schemaFiles = new TreeSet<>();
    String schemaDirectory = SchemaConfigManager.getSchemaDirectoryPath();

    final FilenameFilter filter = new SchemaConfigManager.SchemaFileFilter();
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
  public static void readConcatenatedSchema(File concatSchemaFile, Set<String> attributeTypes,
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
   * Updates the concatenated schema if changes are detected in the current schema files.
   * <p>
   * Identify any differences that may exist between the concatenated schema file from the last
   * online modification and the current schema files. If there are any differences, then they
   * should be from making changes to the schema files with the server offline.
   */
  public static void updateConcatenatedSchema() throws InitializationException
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

        DirectoryServer.setOfflineSchemaChanges(mods);

        // Write a new concatenated schema file with the most recent information
        // so we don't re-find these same changes on the next startup.
        writeConcatenatedSchema();
      }
    }
    catch (InitializationException ie)
    {
      throw ie;
    }
    catch (Exception e)
    {
      logger.traceException(e);

      logger.error(ERR_SCHEMA_ERROR_DETERMINING_SCHEMA_CHANGES, getExceptionMessage(e));
    }
  }

  /**
   * Writes a single file containing all schema element definitions, which can be used on startup to
   * determine whether the schema files were edited with the server offline.
   */
  public static void writeConcatenatedSchema()
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

      File configFile = new File(DirectoryServer.getConfigFile());
      File configDirectory = configFile.getParentFile();
      File upgradeDirectory = new File(configDirectory, "upgrade");
      upgradeDirectory.mkdir();
      File concatFile = new File(upgradeDirectory, SCHEMA_CONCAT_FILE_NAME);
      concatFilePath = concatFile.getAbsolutePath();

      File tempFile = new File(concatFilePath + ".tmp");
      try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile, false)))
      {
        writeLines(writer, "dn: " + DirectoryServer.getSchemaDN(), "objectClass: top", "objectClass: ldapSubentry",
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

  private static void addModification(List<Modification> mods, ModificationType modType, Set<String> included,
      Set<String> excluded, AttributeBuilder builder)
  {
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

  private static File getConcatenatedSchemaFile() throws InitializationException
  {
    File configDirectory = new File(DirectoryServer.getConfigFile()).getParentFile();
    File upgradeDirectory = new File(configDirectory, "upgrade");
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

  private static String getSchemaDefinition(String definition, String schemaFile) throws ParseException
  {
    if (definition.startsWith("::"))
    {
      // See OPENDJ-2792: the definition of the ds-cfg-csv-delimiter-char attribute type
      // had a space accidentally added after the closing parenthesis.
      // This was unfortunately interpreted as base64
      definition = ByteString.wrap(Base64.decode(definition.substring(2).trim())).toString();
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

  private static List<StringBuilder> readSchemaElementsFromLdif(File f) throws IOException, FileNotFoundException
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
}
