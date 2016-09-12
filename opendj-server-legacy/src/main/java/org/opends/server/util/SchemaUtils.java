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
package org.opends.server.util;

import static org.opends.messages.ConfigMessages.ERR_CONFIG_SCHEMA_CANNOT_LIST_FILES;
import static org.opends.server.util.StaticUtils.getExceptionMessage;
import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.util.ServerConstants.SCHEMA_PROPERTY_FILENAME;
import static org.opends.server.schema.SchemaConstants.SYNTAX_AUTH_PASSWORD_OID;
import static org.opends.server.schema.SchemaConstants.SYNTAX_USER_PASSWORD_OID;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg1;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.SchemaElement;
import org.opends.server.core.ServerContext;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

import com.forgerock.opendj.util.OperatingSystem;
import com.forgerock.opendj.util.SubstringReader;

/** Utility methods related to schema. */
public class SchemaUtils
{

  /** Private constructor to prevent instantiation. */
  private SchemaUtils() {
    // No implementation required.
  }

  private static final String CONFIG_SCHEMA_ELEMENTS_FILE = "02-config.ldif";

  /** Represents a password type, including a "not a password" value. */
  public enum PasswordType
  {
    /** Auth Password. */
    AUTH_PASSWORD,
    /** User Password. */
    USER_PASSWORD,
    /** Not a password. */
    NOT_A_PASSWORD
  }

  /** A file filter implementation that accepts only LDIF files. */
  public static class SchemaFileFilter implements FilenameFilter
  {
    private static final String LDIF_SUFFIX = ".ldif";

    @Override
    public boolean accept(File directory, String filename)
    {
      return OperatingSystem.isWindows() ?
          filename.toLowerCase().endsWith(LDIF_SUFFIX) : filename.endsWith(LDIF_SUFFIX);
    }
  }

  /**
   * Checks if the provided attribute type contains a password.
   *
   * @param attrType
   *            The attribute type to check.
   * @return a PasswordTypeCheck result
   */
  public static PasswordType checkPasswordType(AttributeType attrType)
  {
    final String syntaxOID = attrType.getSyntax().getOID();
    if (syntaxOID.equals(SYNTAX_AUTH_PASSWORD_OID))
    {
      return PasswordType.AUTH_PASSWORD;
    }
    else if (attrType.hasName("userPassword") || syntaxOID.equals(SYNTAX_USER_PASSWORD_OID))
    {
      return PasswordType.USER_PASSWORD;
    }
    return PasswordType.NOT_A_PASSWORD;
  }

  /**
   * Retrieves an attribute value containing a representation of the provided
   * boolean value.
   *
   * @param  b  The boolean value for which to retrieve the attribute value.
   *
   * @return  The attribute value created from the provided boolean value.
   */
  public static ByteString createBooleanValue(boolean b)
  {
    return b ? ServerConstants.TRUE_VALUE : ServerConstants.FALSE_VALUE;
  }

  /**
   * Retrieves the definition string used to create the provided schema element and including the
   * X-SCHEMA-FILE extension.
   *
   * @param element
   *            The schema element.
   * @return The definition string used to create the schema element including the X-SCHEMA-FILE
   *         extension.
   */
  public static String getElementDefinitionWithFileName(SchemaElement element)
  {
    final String definition = element.toString();
    return addSchemaFileToElementDefinitionIfAbsent(definition, getElementSchemaFile(element));
  }

  /**
   * Returns the origin of the provided schema element.
   *
   * @param element
   *            The schema element.
   * @return the origin of the schema element as defined in the extra properties.
   */
  public static String getElementOrigin(SchemaElement element)
  {
    return getElementPropertyAsSingleValue(element, ServerConstants.SCHEMA_PROPERTY_ORIGIN);
  }

  /**
   * Returns the single value of the provided extra property for the provided schema element.
   *
   * @param element
   *            The schema element.
   * @param property
   *            The name of property to retrieve.
   * @return the single value of the extra property
   */
  public static String getElementPropertyAsSingleValue(SchemaElement element, String property)
  {
    List<String> values = element.getExtraProperties().get(property);
    return values != null && !values.isEmpty() ? values.get(0) : null;
  }

  /**
   * Returns the schema file of the provided schema element.
   *
   * @param element
   *            The schema element.
   * @return the schema file of schema element.
   */
  public static String getElementSchemaFile(SchemaElement element)
  {
    return getElementPropertyAsSingleValue(element, ServerConstants.SCHEMA_PROPERTY_FILENAME);
  }

  /**
   * Returns a new collection with the result of calling {@link ObjectClass#getNameOrOID()} on each
   * element of the provided collection.
   *
   * @param objectClasses
   *          the schema elements on which to act
   * @return a new collection comprised of the names or OIDs of each element
   */
  public static Collection<String> getNameOrOIDsForOCs(Collection<ObjectClass> objectClasses)
  {
    Set<String> results = new HashSet<>(objectClasses.size());
    for (ObjectClass objectClass : objectClasses)
    {
      results.add(objectClass.getNameOrOID());
    }
    return results;
  }

  /**
   * Returns a new collection with the result of calling {@link AttributeType#getNameOrOID()} on
   * each element of the provided collection.
   *
   * @param attributeTypes
   *          the schema elements on which to act
   * @return a new collection comprised of the names or OIDs of each element
   */
  public static Collection<String> getNameOrOIDsForATs(Collection<AttributeType> attributeTypes)
  {
    Set<String> results = new HashSet<>(attributeTypes.size());
    for (AttributeType attrType : attributeTypes)
    {
      results.add(attrType.getNameOrOID());
    }
    return results;
  }

  /**
   * Returns the new updated attribute type with the provided extra property and its values.
   *
   * @param serverContext
   *          the server context
   * @param attributeType
   *          attribute type to update
   * @param property
   *          the property to set
   * @param values
   *          the values to set
   * @return the new updated attribute type
   */
  public static AttributeType getNewAttributeTypeWithProperty(ServerContext serverContext, AttributeType attributeType,
      String property, String...values)
  {
    SchemaBuilder schemaBuilder =
         new SchemaBuilder(serverContext != null ? serverContext.getSchema() : Schema.getDefaultSchema());
    AttributeType.Builder builder =
        schemaBuilder.buildAttributeType(attributeType).removeExtraProperty(property, (String) null);
    if (values != null && values.length > 0)
    {
      builder.extraProperties(property, values);
      return builder.addToSchemaOverwrite().toSchema().getAttributeType(attributeType.getNameOrOID());
    }
    return attributeType;
  }

  /**
   * Returns the list of schema files contained in the provided schema directory.
   *
   * @param schemaDirectory
   *            The directory containing schema files
   * @return the schema files
   * @throws InitializationException
   *            If the files can't be retrieved
   */
  public static File[] getSchemaFiles(File schemaDirectory) throws InitializationException
  {
    try
    {
      return schemaDirectory.listFiles(new SchemaUtils.SchemaFileFilter());
    }
    catch (Exception e)
    {
      throw new InitializationException(
          ERR_CONFIG_SCHEMA_CANNOT_LIST_FILES.get(schemaDirectory, getExceptionMessage(e)), e);
    }
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
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
          LocalizableMessage.raw("Error when trying to parse the schema file from a schema element definition"));
    }
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

  /**
   * Returns the OID from the provided definition, using the provided message if an error occurs.
   *
   * @param definition
   *            The definition of a schema element
   * @param parsingErrorMsg
   *            Error message to use in case of failure (should be related to
   *            the specific schema element parsed)
   * @return the OID corresponding to the definition
   * @throws DirectoryException
   *            If the parsing of the definition fails
   */
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

  /**
   * Indicates if the provided schema file corresponds to the configuration schema.
   * <p>
   * The file containing the definitions of the schema elements used for configuration must not be
   * imported nor propagated to other servers because these definitions may vary between versions of
   * OpenDJ.
   *
   * @param schemaFile
   *            The name of a file defining schema elements
   * @return {@code true} if the file defines configuration elements,
   *         {@code false} otherwise
   */
  public static boolean is02ConfigLdif(String schemaFile)
  {
    return CONFIG_SCHEMA_ELEMENTS_FILE.equals(schemaFile);
  }
}
