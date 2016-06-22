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
package org.opends.server.types;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.forgerock.opendj.ldap.schema.SchemaOptions.*;
import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.config.ConfigConstants.*;
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
import java.text.ParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg1;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ConflictingSchemaElementException;
import org.forgerock.opendj.ldap.schema.DITContentRule;
import org.forgerock.opendj.ldap.schema.DITStructureRule;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.MatchingRuleUse;
import org.forgerock.opendj.ldap.schema.MatchingRuleUse.Builder;
import org.forgerock.opendj.ldap.schema.NameForm;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.forgerock.opendj.ldap.schema.UnknownSchemaElementException;
import org.forgerock.util.Option;
import org.forgerock.util.Utils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SchemaConfigManager;
import org.opends.server.util.Base64;

/**
 * This class defines a data structure that holds information about the components of the Directory
 * Server schema. It includes the following kinds of elements:
 * <UL>
 * <LI>Attribute type definitions</LI>
 * <LI>Objectclass definitions</LI>
 * <LI>syntax definitions</LI>
 * <LI>Matching rule definitions</LI>
 * <LI>Matching rule use definitions</LI>
 * <LI>DIT content rule definitions</LI>
 * <LI>DIT structure rule definitions</LI>
 * <LI>Name form definitions</LI>
 * </UL>
 * It always uses non-strict {@link org.forgerock.opendj.ldap.schema.Schema} under the hood.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class Schema
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The oldest modification timestamp for any schema configuration file. */
  private long oldestModificationTime;
  /** The youngest modification timestamp for any schema configuration file. */
  private long youngestModificationTime;

  /**
   * A set of extra attributes that are not used directly by the schema but may
   * be used by other component to store information in the schema.
   * <p>
   * ex : Replication uses this to store its state and GenerationID.
   */
  private Map<String, Attribute> extraAttributes = new HashMap<>();

  /**
   * The SDK schema.
   * <p>
   * It will progressively take over server implementation of the schema.
   * <p>
   * @GuardedBy("exclusiveLock")
   */
  private volatile org.forgerock.opendj.ldap.schema.Schema schemaNG;

  /** Guards updates to the schema. */
  private final Lock exclusiveLock = new ReentrantLock();

  /**
   * Creates a new schema structure with all elements initialized but empty.
   *
   * @param schemaNG
   *          The SDK schema
   * @throws DirectoryException
   *           if the schema has warnings
   */
  public Schema(org.forgerock.opendj.ldap.schema.Schema schemaNG) throws DirectoryException
  {
    final org.forgerock.opendj.ldap.schema.Schema newSchemaNG =
        new SchemaBuilder(schemaNG)
        .setOption(DEFAULT_SYNTAX_OID, getDirectoryStringSyntax().getOID())
        .toSchema();
    switchSchema(newSchemaNG);

    oldestModificationTime    = System.currentTimeMillis();
    youngestModificationTime  = oldestModificationTime;
  }

  /**
   * Returns the SDK schema.
   *
   * @return the SDK schema
   */
  public org.forgerock.opendj.ldap.schema.Schema getSchemaNG()
  {
    return schemaNG;
  }

  /**
   * Retrieves the attribute type definitions for this schema.
   *
   * @return  The attribute type definitions for this schema.
   */
  public Collection<AttributeType> getAttributeTypes()
  {
    return schemaNG.getAttributeTypes();
  }

  /**
   * Indicates whether this schema definition includes an attribute type with the provided name or
   * OID.
   *
   * @param nameOrOid
   *          The name or OID for which to make the determination
   * @return {@code true} if this schema contains an attribute type with the provided name or OID,
   *         or {@code false} if not.
   */
  public boolean hasAttributeType(String nameOrOid)
  {
    return schemaNG.hasAttributeType(nameOrOid);
  }

  /**
   * Retrieves the attribute type definition with the specified name or OID.
   *
   * @param nameOrOid
   *          The name or OID of the attribute type to retrieve
   * @return The requested attribute type
   */
  public AttributeType getAttributeType(String nameOrOid)
  {
    try
    {
      return schemaNG.getAttributeType(nameOrOid);
    }
    catch (UnknownSchemaElementException e)
    {
      // It should never happen because we only use non-strict schemas
      throw new RuntimeException(e);
    }
  }

  /**
   * Retrieves the attribute type definition with the specified name or OID.
   *
   * @param nameOrOid
   *          The name or OID of the attribute type to retrieve
   * @param syntax
   *          The syntax to use when creating the temporary "place-holder" attribute type.
   * @return The requested attribute type
   */
  public AttributeType getAttributeType(String nameOrOid, Syntax syntax)
  {
    try
    {
      return schemaNG.getAttributeType(nameOrOid, syntax);
    }
    catch (UnknownSchemaElementException e)
    {
      // It should never happen because we only use non-strict schemas
      throw new RuntimeException(e);
    }
  }

  /**
   * Parses an object class from its provided definition.
   *
   * @param definition
   *          The definition of the object class
   * @return the object class
   * @throws DirectoryException
   *           If an error occurs
   */
  public ObjectClass parseObjectClass(final String definition) throws DirectoryException
  {
    try
    {
      SchemaBuilder builder = new SchemaBuilder(schemaNG);
      builder.addObjectClass(definition, true);
      org.forgerock.opendj.ldap.schema.Schema newSchema = builder.toSchema();
      rejectSchemaWithWarnings(newSchema);
      return newSchema.getObjectClass(parseObjectClassOID(definition));
    }
    catch (UnknownSchemaElementException e)
    {
      // this should never happen
      LocalizableMessage msg = ERR_OBJECT_CLASS_CANNOT_REGISTER.get(definition);
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, msg, e);
    }
    catch (LocalizedIllegalArgumentException e)
    {
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, e.getMessageObject(), e);
    }
  }

  /**
   * Parses an attribute type from its provided definition.
   *
   * @param definition
   *          The definition of the attribute type
   * @return the attribute type
   * @throws DirectoryException
   *            If an error occurs
   */
  public AttributeType parseAttributeType(final String definition) throws DirectoryException
  {
    try
    {
      SchemaBuilder builder = new SchemaBuilder(schemaNG);
      builder.addAttributeType(definition, true);
      org.forgerock.opendj.ldap.schema.Schema newSchema = builder.toSchema();
      rejectSchemaWithWarnings(newSchema);
      return newSchema.getAttributeType(parseAttributeTypeOID(definition));
    }
    catch (UnknownSchemaElementException e)
    {
      // this should never happen
      LocalizableMessage msg = ERR_ATTR_TYPE_CANNOT_REGISTER.get(definition);
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, msg, e);
    }
    catch (LocalizedIllegalArgumentException e)
    {
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, e.getMessageObject(), e);
    }
  }

  /**
   * Parses a matching rule use from its provided definition.
   *
   * @param definition
   *          The definition of the matching rule use
   * @return the matching rule use
   * @throws DirectoryException
   *            If an error occurs
   */
  public MatchingRuleUse parseMatchingRuleUse(final String definition) throws DirectoryException
  {
    try
    {
      SchemaBuilder builder = new SchemaBuilder(schemaNG);
      builder.addMatchingRuleUse(definition, true);
      org.forgerock.opendj.ldap.schema.Schema newSchema = builder.toSchema();
      rejectSchemaWithWarnings(newSchema);
      return newSchema.getMatchingRuleUse(parseMatchingRuleUseOID(definition));
    }
    catch (UnknownSchemaElementException e)
    {
      LocalizableMessage msg = ERR_MATCHING_RULE_USE_CANNOT_REGISTER.get(definition);
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, msg, e);
    }
    catch (LocalizedIllegalArgumentException e)
    {
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, e.getMessageObject(), e);
    }
  }

  /**
   * Parses a name form from its provided definition.
   *
   * @param definition
   *          The definition of the name form
   * @return the name form
   * @throws DirectoryException
   *           If an error occurs
   */
  public NameForm parseNameForm(final String definition) throws DirectoryException
  {
    try
    {
      SchemaBuilder builder = new SchemaBuilder(schemaNG);
      builder.addNameForm(definition, true);
      org.forgerock.opendj.ldap.schema.Schema newSchema = builder.toSchema();
      rejectSchemaWithWarnings(newSchema);
      return newSchema.getNameForm(parseNameFormOID(definition));
    }
    catch (UnknownSchemaElementException e)
    {
      LocalizableMessage msg = ERR_NAME_FORM_CANNOT_REGISTER.get(definition);
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, msg, e);
    }
    catch (LocalizedIllegalArgumentException e)
    {
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, e.getMessageObject(), e);
    }
  }

  /**
   * Parses a a DIT content rule from its provided definition.
   *
   * @param definition
   *          The definition of the matching rule use
   * @return the DIT content rule
   * @throws DirectoryException
   *           If an error occurs
   */
  public DITContentRule parseDITContentRule(final String definition) throws DirectoryException
  {
    try
    {
      SchemaBuilder builder = new SchemaBuilder(schemaNG);
      builder.addDITContentRule(definition, true);
      org.forgerock.opendj.ldap.schema.Schema newSchema = builder.toSchema();
      rejectSchemaWithWarnings(newSchema);
      return newSchema.getDITContentRule(parseDITContentRuleOID(definition));
    }
    catch (UnknownSchemaElementException e)
    {
      LocalizableMessage msg = ERR_DIT_CONTENT_RULE_CANNOT_REGISTER.get(definition);
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, msg, e);
    }
    catch (LocalizedIllegalArgumentException e)
    {
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, e.getMessageObject(), e);
    }
  }

  /**
   * Parses a DIT structure rule from its provided definition.
   *
   * @param definition
   *          The definition of the DIT structure rule
   * @return the DIT structure rule
   * @throws DirectoryException
   *           If an error occurs
   */
  public DITStructureRule parseDITStructureRule(String definition) throws DirectoryException
  {
    try
    {
      SchemaBuilder builder = new SchemaBuilder(schemaNG);
      builder.addDITStructureRule(definition, true);
      org.forgerock.opendj.ldap.schema.Schema newSchema = builder.toSchema();
      rejectSchemaWithWarnings(newSchema);
      return newSchema.getDITStructureRule(parseRuleID(definition));
    }
    catch (UnknownSchemaElementException e)
    {
      LocalizableMessage msg = ERR_NAME_FORM_CANNOT_REGISTER.get(definition);
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, msg, e);
    }
    catch (LocalizedIllegalArgumentException e)
    {
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, e.getMessageObject(), e);
    }
  }

  /**
   * Registers an attribute type from its provided definition.
   *
   * @param definition
   *          The definition of the attribute type
   * @param schemaFile
   *          The schema file where this definition belongs,
   *          maybe {@code null}
   * @param overwrite
   *          Indicates whether to overwrite the attribute
   *          type if it already exists based on OID or name
   * @throws DirectoryException
   *            If an error occurs
   */
  public void registerAttributeType(final String definition, final String schemaFile, final boolean overwrite)
      throws DirectoryException
  {
    exclusiveLock.lock();
    try
    {
      String defWithFile = getDefinitionWithSchemaFile(definition, schemaFile);
      switchSchema(new SchemaBuilder(schemaNG)
          .addAttributeType(defWithFile, overwrite)
          .toSchema());
    }
    catch (ConflictingSchemaElementException | UnknownSchemaElementException e)
    {
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, e.getMessageObject(), e);
    }
    catch (LocalizedIllegalArgumentException e)
    {
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, e.getMessageObject(), e);
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  /**
   * Registers the provided attribute type definition with this schema.
   *
   * @param attributeType
   *          The attribute type to register with this schema.
   * @param schemaFile
   *          The schema file where this definition belongs, maybe {@code null}
   * @param overwriteExisting
   *          Indicates whether to overwrite an existing mapping if there are any conflicts (i.e.,
   *          another attribute type with the same OID or name).
   * @throws DirectoryException
   *           If a conflict is encountered and the <CODE>overwriteExisting</CODE> flag is set to
   *           {@code false}
   */
  public void registerAttributeType(final AttributeType attributeType, final String schemaFile,
      final boolean overwriteExisting) throws DirectoryException
  {
    exclusiveLock.lock();
    try
    {
      SchemaBuilder builder = new SchemaBuilder(schemaNG);
      registerAttributeType0(builder, attributeType, schemaFile, overwriteExisting);
      switchSchema(builder.toSchema());
    }
    catch (LocalizedIllegalArgumentException e)
    {
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, e.getMessageObject(), e);
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  private void registerAttributeType0(SchemaBuilder builder, final AttributeType attributeType,
      final String schemaFile, final boolean overwriteExisting)
  {
    AttributeType.Builder b = builder.buildAttributeType(attributeType);
    if (schemaFile != null)
    {
      b.removeExtraProperty(SCHEMA_PROPERTY_FILENAME)
       .extraProperties(SCHEMA_PROPERTY_FILENAME, schemaFile);
    }
    if (overwriteExisting)
    {
      b.addToSchemaOverwrite();
    }
    else
    {
      b.addToSchema();
    }
  }

  /**
   * Replaces an existing attribute type by the provided new attribute type.
   *
   * @param newAttributeType
   *          Attribute type to register to the schema.
   * @param existingAttributeType
   *          Attribute type to remove from the schema.
   * @param schemaFile
   *          The schema file which the new object class belongs to.
   * @throws DirectoryException
   *            If an errors occurs.
   */
  public void replaceAttributeType(AttributeType newAttributeType, AttributeType existingAttributeType,
      String schemaFile) throws DirectoryException
  {
    exclusiveLock.lock();
    try
    {
      SchemaBuilder builder = new SchemaBuilder(schemaNG);
      builder.removeAttributeType(existingAttributeType.getNameOrOID());
      registerAttributeType0(builder, newAttributeType, schemaFile, false);
      switchSchema(builder.toSchema());
    }
    finally
    {
      exclusiveLock.unlock();
    }
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

  private static String parseMatchingRuleUseOID(String definition) throws DirectoryException
  {
    return parseOID(definition, ERR_PARSING_MATCHING_RULE_USE_OID);
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

  private static String parseDITContentRuleOID(String definition) throws DirectoryException
  {
    return parseOID(definition, ERR_PARSING_DIT_CONTENT_RULE_OID);
  }

  /**
   * Returns the ruleID from the provided dit structure rule definition, assuming the definition is
   * valid.
   * <p>
   * This method does not perform any check.
   *
   * @param definition
   *          The definition of a dit structure rule, assumed to be valid
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

  /**
   * Deregisters the provided attribute type definition with this schema.
   *
   * @param  attributeType  The attribute type to deregister with this schema.
   * @throws DirectoryException
   *           If the attribute type is referenced by another schema element.
   */
  public void deregisterAttributeType(final AttributeType attributeType) throws DirectoryException
  {
    exclusiveLock.lock();
    try
    {
      SchemaBuilder builder = new SchemaBuilder(schemaNG);
      if (builder.removeAttributeType(attributeType.getNameOrOID()))
      {
        switchSchema(builder.toSchema());
      }
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  /**
   * Retrieves the objectclass definitions for this schema.
   *
   * @return The objectclass definitions for this schema.
   */
  public Collection<ObjectClass> getObjectClasses()
  {
    return schemaNG.getObjectClasses();
  }

  /**
   * Indicates whether this schema definition includes an objectclass with the provided name or OID.
   *
   * @param nameOrOid
   *          The name or OID for which to make the determination.
   * @return {@code true} if this schema contains an objectclass with the provided name or OID, or
   *         {@code false} if not.
   */
  public boolean hasObjectClass(String nameOrOid)
  {
    return schemaNG.hasObjectClass(nameOrOid);
  }



  /**
   * Retrieves the objectclass definition with the specified name or OID.
   *
   * @param nameOrOid
   *          The name or OID of the objectclass to retrieve.
   * @return The requested objectclass, or {@code null} if no class is registered with the provided
   *         name or OID.
   */
  public ObjectClass getObjectClass(String nameOrOid)
  {
    return schemaNG.getObjectClass(nameOrOid);
  }

  /**
   * Registers the provided objectclass definition with this schema.
   *
   * @param objectClass
   *          The objectclass to register with this schema.
   * @param schemaFile
   *          The schema file where this definition belongs, maybe {@code null}
   * @param overwriteExisting
   *          Indicates whether to overwrite an existing mapping if there are any conflicts (i.e.,
   *          another objectclass with the same OID or name).
   * @throws DirectoryException
   *           If a conflict is encountered and the {@code overwriteExisting} flag is set to
   *           {@code false}.
   */
  public void registerObjectClass(ObjectClass objectClass, String schemaFile, boolean overwriteExisting)
         throws DirectoryException
  {
    exclusiveLock.lock();
    try
    {
      SchemaBuilder builder = new SchemaBuilder(schemaNG);
      registerObjectClass0(builder, objectClass, schemaFile, overwriteExisting);
      switchSchema(builder.toSchema());
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  private void registerObjectClass0(SchemaBuilder builder, ObjectClass objectClass, String schemaFile,
      boolean overwriteExisting)
  {
    ObjectClass.Builder b = builder.buildObjectClass(objectClass);
    if (schemaFile != null)
    {
      b.removeExtraProperty(SCHEMA_PROPERTY_FILENAME)
       .extraProperties(SCHEMA_PROPERTY_FILENAME, schemaFile);
    }
    if (overwriteExisting)
    {
      b.addToSchemaOverwrite();
    }
    else
    {
      b.addToSchema();
    }
  }

  /**
   * Registers an object class from its provided definition.
   *
   * @param definition
   *          The definition of the object class
   * @param schemaFile
   *          The schema file where this definition belongs, may be {@code null}
   * @param overwriteExisting
   *          Indicates whether to overwrite the object class
   *          if it already exists based on OID or name
   * @throws DirectoryException
   *            If an error occurs
   */
  public void registerObjectClass(String definition, String schemaFile, boolean overwriteExisting)
      throws DirectoryException
  {
    exclusiveLock.lock();
    try
    {
      String defWithFile = getDefinitionWithSchemaFile(definition, schemaFile);
      switchSchema(new SchemaBuilder(schemaNG)
          .addObjectClass(defWithFile, overwriteExisting)
          .toSchema());
    }
    catch (ConflictingSchemaElementException | UnknownSchemaElementException e)
    {
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, e.getMessageObject(), e);
    }
    catch (LocalizedIllegalArgumentException e)
    {
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, e.getMessageObject(), e);
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  /**
   * Deregisters the provided objectclass definition with this schema.
   *
   * @param  objectClass  The objectclass to deregister with this schema.
   * @throws DirectoryException
   *           If the object class is referenced by another schema element.
   */
  public void deregisterObjectClass(ObjectClass objectClass) throws DirectoryException
  {
    exclusiveLock.lock();
    try
    {
      SchemaBuilder builder = new SchemaBuilder(schemaNG);
      if (builder.removeObjectClass(objectClass.getNameOrOID()))
      {
        switchSchema(builder.toSchema());
      }
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }



  /**
   * Retrieves the syntax definitions for this schema.
   *
   * @return The syntax definitions for this schema.
   */
  public Collection<Syntax> getSyntaxes()
  {
    return schemaNG.getSyntaxes();
  }



  /**
   * Indicates whether this schema definition includes an attribute syntax with the provided OID.
   *
   * @param oid
   *          The OID for which to make the determination
   * @return {@code true} if this schema contains an syntax with the provided OID, or {@code false}
   *         if not.
   */
  public boolean hasSyntax(String oid)
  {
    return schemaNG.hasSyntax(oid);
  }

  /**
   * Retrieves the syntax definition with the OID.
   *
   * @param numericOid
   *          The OID of the syntax to retrieve.
   * @return The requested syntax, or {@code null} if no syntax is registered with the provided OID.
   */
  public Syntax getSyntax(String numericOid)
  {
    return schemaNG.getSyntax(numericOid);
  }

  /**
   * Registers the provided syntax definition with this schema.
   *
   * @param syntax
   *          The syntax to register with this schema.
   * @param overwriteExisting
   *          Indicates whether to overwrite an existing mapping if there are any conflicts (i.e.,
   *          another attribute syntax with the same OID).
   * @throws DirectoryException
   *           If a conflict is encountered and the <CODE>overwriteExisting</CODE> flag is set to
   *           {@code false}
   */
  public void registerSyntax(final Syntax syntax, final boolean overwriteExisting) throws DirectoryException
  {
    exclusiveLock.lock();
    try
    {
      SchemaBuilder builder = new SchemaBuilder(schemaNG);
      Syntax.Builder b = builder.buildSyntax(syntax);
      if (overwriteExisting)
      {
        b.addToSchemaOverwrite();
      }
      else
      {
        b.addToSchema();
      }
      switchSchema(builder.toSchema());
    }
    catch (LocalizedIllegalArgumentException e)
    {
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, e.getMessageObject(), e);
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  /**
   * Registers the provided syntax definition with this schema.
   *
   * @param definition
   *          The definition to register with this schema.
   * @param overwriteExisting
   *          Indicates whether to overwrite an existing mapping if there are any conflicts (i.e.,
   *          another attribute syntax with the same OID).
   * @throws DirectoryException
   *           If a conflict is encountered and the <CODE>overwriteExisting</CODE> flag is set to
   *           {@code false}
   */
  public void registerSyntax(final String definition, final boolean overwriteExisting) throws DirectoryException
  {
    exclusiveLock.lock();
    try
    {
      org.forgerock.opendj.ldap.schema.Schema newSchema =
          new SchemaBuilder(schemaNG)
          .addSyntax(definition, overwriteExisting)
          .toSchema();
      switchSchema(newSchema);
    }
    catch (ConflictingSchemaElementException | UnknownSchemaElementException e)
    {
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, e.getMessageObject(), e);
    }
    catch (LocalizedIllegalArgumentException e)
    {
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, e.getMessageObject(), e);
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  /**
   * Deregisters the provided syntax definition with this schema.
   *
   * @param syntax
   *          The syntax to deregister with this schema.
   * @throws DirectoryException
   *           If the LDAP syntax is referenced by another schema element.
   */
  public void deregisterSyntax(final Syntax syntax) throws DirectoryException
  {
    exclusiveLock.lock();
    try
    {
      SchemaBuilder builder = new SchemaBuilder(schemaNG);
      builder.removeSyntax(syntax.getOID());
      switchSchema(builder.toSchema());
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  /**
   * Retrieves all matching rule definitions for this schema.
   *
   * @return The matching rule definitions for this schema
   */
  public Collection<MatchingRule> getMatchingRules()
  {
    return schemaNG.getMatchingRules();
  }

  /**
   * Indicates whether this schema definition includes a matching rule with the provided name or
   * OID.
   *
   * @param nameOrOid
   *          The name or OID for which to make the determination
   * @return {@code true} if this schema contains a matching rule with the provided name or OID, or
   *         {@code false} if not.
   */
  public boolean hasMatchingRule(String nameOrOid)
  {
    return schemaNG.hasMatchingRule(nameOrOid);
  }

  /**
   * Retrieves the matching rule definition with the specified name or OID.
   *
   * @param nameOrOid
   *          The name or OID of the matching rule to retrieve
   * @return The requested matching rule, or {@code null} if no rule is registered with the provided
   *         name or OID.
   * @throws UnknownSchemaElementException
   *           If the requested matching rule was not found or if the provided name is ambiguous.
   */
  public MatchingRule getMatchingRule(String nameOrOid)
  {
    return schemaNG.getMatchingRule(nameOrOid);
  }

  /**
   * Registers the provided matching rule definitions with this schema.
   *
   * @param matchingRules
   *          The matching rules to register with this schema.
   * @param overwriteExisting
   *          Indicates whether to overwrite an existing mapping if there are
   *          any conflicts (i.e., another matching rule with the same OID or
   *          name).
   * @throws DirectoryException
   *           If a conflict is encountered and the
   *           {@code overwriteExisting} flag is set to {@code false}
   */
  public void registerMatchingRules(Collection<MatchingRule> matchingRules, boolean overwriteExisting)
      throws DirectoryException
  {
    exclusiveLock.lock();
    try
    {
      SchemaBuilder builder = new SchemaBuilder(schemaNG);
      for (MatchingRule matchingRule : matchingRules)
      {
        MatchingRule.Builder b = builder.buildMatchingRule(matchingRule);
        if (overwriteExisting)
        {
          b.addToSchemaOverwrite();
        }
        else
        {
          b.addToSchema();
        }
      }
      switchSchema(builder.toSchema());
    }
    catch (LocalizedIllegalArgumentException e)
    {
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, e.getMessageObject(), e);
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  /**
   * Deregisters the provided matching rule definition with this schema.
   *
   * @param matchingRule
   *          The matching rule to deregister with this schema.
   * @throws DirectoryException
   *           If the schema has constraints violations.
   */
  public void deregisterMatchingRule(final MatchingRule matchingRule) throws DirectoryException
  {
    exclusiveLock.lock();
    try
    {
      SchemaBuilder builder = new SchemaBuilder(schemaNG);
      builder.removeMatchingRule(matchingRule.getNameOrOID());
      switchSchema(builder.toSchema());
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }


  /**
   * Retrieves the matching rule use definitions for this schema, as a
   * mapping between the matching rule for the matching rule use
   * definition and the matching rule use itself.  Each matching rule
   * use should only be present once, since its only key is its
   * matching rule.  The contents of the returned mapping must not be
   * altered.
   *
   * @return  The matching rule use definitions for this schema.
   */
  public Collection<MatchingRuleUse> getMatchingRuleUses()
  {
    return schemaNG.getMatchingRuleUses();
  }



  /**
   * Retrieves the matching rule use definition for the specified matching rule.
   *
   * @param matchingRule
   *          The matching rule for which to retrieve the matching rule use definition.
   * @return The matching rule use definition, or {@code null} if none exists for the specified
   *         matching rule.
   */
  public MatchingRuleUse getMatchingRuleUse(MatchingRule matchingRule)
  {
    return schemaNG.getMatchingRuleUse(matchingRule);
  }

  /**
   * Registers the provided matching rule use definition with this schema.
   *
   * @param matchingRuleUse
   *          The matching rule use definition to register.
   * @param schemaFile
   *          The schema file where this definition belongs, maybe {@code null}
   * @param overwriteExisting
   *          Indicates whether to overwrite an existing mapping if there are any conflicts (i.e.,
   *          another matching rule use with the same matching rule).
   * @throws DirectoryException
   *           If a conflict is encountered and the {@code overwriteExisting} flag is set to
   *           {@code false}
   */
  public void registerMatchingRuleUse(MatchingRuleUse matchingRuleUse, String schemaFile, boolean overwriteExisting)
      throws DirectoryException
  {
    exclusiveLock.lock();
    try
    {
      SchemaBuilder builder = new SchemaBuilder(schemaNG);
      Builder mruBuilder = builder.buildMatchingRuleUse(matchingRuleUse);
      if (schemaFile != null)
      {
        mruBuilder.removeExtraProperty(SCHEMA_PROPERTY_FILENAME)
                  .extraProperties(SCHEMA_PROPERTY_FILENAME, schemaFile);
      }
      if (overwriteExisting)
      {
        mruBuilder.addToSchemaOverwrite();
      }
      else
      {
        mruBuilder.addToSchema();
      }
      switchSchema(builder.toSchema());
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  private String getDefinitionWithSchemaFile(String definition, String schemaFile)
  {
    return schemaFile != null ? addSchemaFileToElementDefinitionIfAbsent(definition, schemaFile) : definition;
  }

  /**
   * Deregisters the provided matching rule use definition with this schema.
   *
   * @param matchingRuleUse
   *          The matching rule use to deregister with this schema.
   * @throws DirectoryException
   *            If the schema has constraints violations.
   */
  public void deregisterMatchingRuleUse(org.forgerock.opendj.ldap.schema.MatchingRuleUse matchingRuleUse)
      throws DirectoryException
  {
    exclusiveLock.lock();
    try
    {
      SchemaBuilder builder = new SchemaBuilder(schemaNG);
      builder.removeMatchingRuleUse(matchingRuleUse.getNameOrOID());
      switchSchema(builder.toSchema());
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }



  /**
   * Retrieves the DIT content rule definitions for this schema.
   *
   * @return  The DIT content rule definitions for this schema.
   */
  public Collection<DITContentRule> getDITContentRules()
  {
    return schemaNG.getDITContentRules();
  }


  /**
   * Retrieves the DIT content rule definition for the specified objectclass.
   *
   * @param objectClass
   *          The objectclass for the DIT content rule to retrieve.
   * @return The requested DIT content rule, or {@code null} if no DIT content rule is registered
   *         with the provided objectclass.
   */
  public DITContentRule getDITContentRule(ObjectClass objectClass)
  {
    return schemaNG.getDITContentRule(objectClass);
  }



  /**
   * Registers the provided DIT content rule definition with this schema.
   *
   * @param ditContentRule
   *          The DIT content rule to register.
   * @param schemaFile
   *          The schema file where this definition belongs, maybe {@code null}
   * @param overwriteExisting
   *          Indicates whether to overwrite an existing mapping if there are any conflicts (i.e.,
   *          another DIT content rule with the same objectclass).
   * @throws DirectoryException
   *           If a conflict is encountered and the <CODE>overwriteExisting</CODE> flag is set to
   *           {@code false}
   */
  public void registerDITContentRule(DITContentRule ditContentRule, String schemaFile, boolean overwriteExisting)
      throws DirectoryException
  {
    exclusiveLock.lock();
    try
    {
      SchemaBuilder builder = new SchemaBuilder(schemaNG);
      DITContentRule.Builder dcrBuilder = builder.buildDITContentRule(ditContentRule);
      if (schemaFile != null)
      {
        dcrBuilder.removeExtraProperty(SCHEMA_PROPERTY_FILENAME)
                  .extraProperties(SCHEMA_PROPERTY_FILENAME, schemaFile);
      }
      if (overwriteExisting)
      {
        dcrBuilder.addToSchemaOverwrite();
      }
      else
      {
        dcrBuilder.addToSchema();
      }
      switchSchema(builder.toSchema());
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  /**
   * Deregisters the provided DIT content rule definition with this
   * schema.
   *
   * @param  ditContentRule  The DIT content rule to deregister with
   *                         this schema.
   * @throws DirectoryException
   *            May be thrown if the schema has constraint violations, although
   *            deregistering a DIT content rule should not break any constraint.
   */
  public void deregisterDITContentRule(DITContentRule ditContentRule) throws DirectoryException
  {
    exclusiveLock.lock();
    try
    {
      SchemaBuilder builder = new SchemaBuilder(schemaNG);
      builder.removeDITContentRule(ditContentRule.getNameOrOID());
      switchSchema(builder.toSchema());
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }



  /**
   * Retrieves the DIT structure rule definitions for this schema.
   * The contents of the returned mapping must not be altered.
   *
   * @return The DIT structure rule definitions for this schema.
   */
  public Collection<DITStructureRule> getDITStructureRules()
  {
    return schemaNG.getDITStuctureRules();
  }

  /**
   * Retrieves the DIT structure rule definition with the provided rule ID.
   *
   * @param ruleID
   *          The rule ID for the DIT structure rule to retrieve.
   * @return The requested DIT structure rule, or {@code null} if no DIT structure rule is
   *         registered with the provided rule ID.
   */
  public DITStructureRule getDITStructureRule(int ruleID)
  {
    return schemaNG.getDITStructureRule(ruleID);
  }

  /**
   * Retrieves the DIT structure rule definitions for the provided name form.
   *
   * @param nameForm
   *          The name form for the DIT structure rule to retrieve.
   * @return The requested DIT structure rules, or {@code null} if no DIT structure rule is
   *         registered with the provided name form.
   */
  public Collection<DITStructureRule> getDITStructureRules(NameForm nameForm)
  {
    return schemaNG.getDITStructureRules(nameForm);
  }



  /**
   * Registers the provided DIT structure rule definition with this schema.
   *
   * @param ditStructureRule
   *          The DIT structure rule to register.
   * @param schemaFile
   *          The schema file where this definition belongs, maybe {@code null}
   * @param overwriteExisting
   *          Indicates whether to overwrite an existing mapping if there are any conflicts (i.e.,
   *          another DIT structure rule with the same name form).
   * @throws DirectoryException
   *           If a conflict is encountered and the {@code overwriteExisting} flag is set to
   *           {@code false}
   */
  public void registerDITStructureRule(DITStructureRule ditStructureRule, String schemaFile, boolean overwriteExisting)
      throws DirectoryException
  {
    exclusiveLock.lock();
    try
    {
      SchemaBuilder builder = new SchemaBuilder(schemaNG);
      DITStructureRule.Builder dsrBuilder = builder.buildDITStructureRule(ditStructureRule);
      if (schemaFile != null)
      {
        dsrBuilder.removeExtraProperty(SCHEMA_PROPERTY_FILENAME)
                  .extraProperties(SCHEMA_PROPERTY_FILENAME, schemaFile);
      }
      if (overwriteExisting)
      {
        dsrBuilder.addToSchemaOverwrite();
      }
      else
      {
        dsrBuilder.addToSchema();
      }
      switchSchema(builder.toSchema());
    }
    catch (LocalizedIllegalArgumentException e)
    {
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, e.getMessageObject(), e);
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  /**
   * Deregisters the provided DIT structure rule definition with this schema.
   *
   * @param ditStructureRule
   *          The DIT structure rule to deregister with this schema.
   * @throws DirectoryException
   *           If an error occurs.
   */
  public void deregisterDITStructureRule(DITStructureRule ditStructureRule) throws DirectoryException
  {
    exclusiveLock.lock();
    try
    {
      SchemaBuilder builder = new SchemaBuilder(schemaNG);
      builder.removeDITStructureRule(ditStructureRule.getRuleID());
      switchSchema(builder.toSchema());
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }



  /**
   * Retrieves the name form definitions for this schema.
   *
   * @return  The name form definitions for this schema.
   */
  public Collection<NameForm> getNameForms()
  {
    return schemaNG.getNameForms();
  }

  /**
   * Indicates whether this schema definition includes a name form with the specified name or OID.
   *
   * @param nameOrOid
   *          The name or OID for which to make the determination.
   * @return {@code true} if this schema contains a name form with the provided name or OID, or
   *         {@code false} if not.
   */
  public boolean hasNameForm(String nameOrOid)
  {
    return schemaNG.hasNameForm(nameOrOid);
  }



  /**
   * Retrieves the name forms definition for the specified objectclass.
   *
   * @param objectClass
   *          The objectclass for the name form to retrieve.
   * @return The requested name forms, or {@code null} if no name forms are registered with the
   *         provided objectClass.
   */
  public Collection<NameForm> getNameForm(ObjectClass objectClass)
  {
    return schemaNG.getNameForms(objectClass);
  }



  /**
   * Retrieves the name form definition with the provided name or OID.
   *
   * @param nameOrOid
   *          The name or OID of the name form to retrieve.
   * @return The requested name form, or {@code null} if no name form is registered with the
   *         provided name or OID.
   */
  public NameForm getNameForm(String nameOrOid)
  {
    return schemaNG.getNameForm(nameOrOid);
  }



  /**
   * Registers the provided name form definition with this schema.
   *
   * @param nameForm
   *          The name form definition to register.
   * @param schemaFile
   *          The schema file where this definition belongs, maybe {@code null}
   * @param overwriteExisting
   *          Indicates whether to overwrite an existing mapping if there are any conflicts (i.e.,
   *          another name form with the same objectclass).
   * @throws DirectoryException
   *           If a conflict is encountered and the <CODE>overwriteExisting</CODE> flag is set to
   *           {@code false}
   */
  public void registerNameForm(NameForm nameForm, String schemaFile, boolean overwriteExisting)
      throws DirectoryException
  {
    exclusiveLock.lock();
    try
    {
      SchemaBuilder builder = new SchemaBuilder(schemaNG);
      NameForm.Builder formBuilder = builder.buildNameForm(nameForm);
      if (schemaFile != null)
      {
        formBuilder.removeExtraProperty(SCHEMA_PROPERTY_FILENAME)
                   .extraProperties(SCHEMA_PROPERTY_FILENAME, schemaFile);
      }
      if (overwriteExisting)
      {
        formBuilder.addToSchemaOverwrite();
      }
      else
      {
        formBuilder.addToSchema();
      }
      switchSchema(builder.toSchema());
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  /**
   * Deregisters the provided name form definition with this schema.
   *
   * @param  nameForm  The name form definition to deregister.
   * @throws DirectoryException
   *            If an error occurs.
   */
  public void deregisterNameForm(NameForm nameForm) throws DirectoryException
  {
    exclusiveLock.lock();
    try
    {
      SchemaBuilder builder = new SchemaBuilder(schemaNG);
      builder.removeNameForm(nameForm.getNameOrOID());
      switchSchema(builder.toSchema());
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }



  /**
   * Retrieves the modification timestamp for the file in the schema
   * configuration directory with the oldest last modified time.
   *
   * @return  The modification timestamp for the file in the schema
   *          configuration directory with the oldest last modified
   *          time.
   */
  public long getOldestModificationTime()
  {
    return oldestModificationTime;
  }



  /**
   * Sets the modification timestamp for the oldest file in the schema
   * configuration directory.
   *
   * @param  oldestModificationTime  The modification timestamp for
   *                                 the oldest file in the schema
   *                                 configuration directory.
   */
  public void setOldestModificationTime(long oldestModificationTime)
  {
    this.oldestModificationTime = oldestModificationTime;
  }



  /**
   * Retrieves the modification timestamp for the file in the schema
   * configuration directory with the youngest last modified time.
   *
   * @return  The modification timestamp for the file in the schema
   *          configuration directory with the youngest last modified
   *          time.
   */
  public long getYoungestModificationTime()
  {
    return youngestModificationTime;
  }



  /**
   * Sets the modification timestamp for the youngest file in the
   * schema configuration directory.
   *
   * @param  youngestModificationTime  The modification timestamp for
   *                                   the youngest file in the schema
   *                                   configuration directory.
   */
  public void setYoungestModificationTime(
                   long youngestModificationTime)
  {
    this.youngestModificationTime = youngestModificationTime;
  }

  /**
   * Creates a new {@link Schema} object that is a duplicate of this one. It elements may be added
   * and removed from the duplicate without impacting this version.
   *
   * @return A new {@link Schema} object that is a duplicate of this one.
   */
  public Schema duplicate()
  {
    Schema dupSchema;
    try
    {
      dupSchema = new Schema(schemaNG);
    }
    catch (DirectoryException unexpected)
    {
      // the schema has already been validated
      throw new RuntimeException(unexpected);
    }

    dupSchema.oldestModificationTime   = oldestModificationTime;
    dupSchema.youngestModificationTime = youngestModificationTime;
    if (extraAttributes != null)
    {
      dupSchema.extraAttributes = new HashMap<>(extraAttributes);
    }

    return dupSchema;
  }


  /**
   * Get the extraAttributes stored in this schema.
   *
   * @return  The extraAttributes stored in this schema.
   */
  public Collection<Attribute> getExtraAttributes()
  {
    return extraAttributes.values();
  }


  /**
   * Add a new extra Attribute for this schema.
   *
   * @param  name     The identifier of the extra Attribute.
   *
   * @param  attr     The extra attribute that must be added to
   *                  this Schema.
   */
  public void addExtraAttribute(String name, Attribute attr)
  {
    extraAttributes.put(name, attr);
  }


  /**
   * Writes a single file containing all schema element definitions,
   * which can be used on startup to determine whether the schema
   * files were edited with the server offline.
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
      genConcatenatedSchema(attributeTypes, objectClasses, nameForms,
                            ditContentRules, ditStructureRules,
                            matchingRuleUses,ldapSyntaxes);


      File configFile = new File(DirectoryServer.getConfigFile());
      File configDirectory = configFile.getParentFile();
      File upgradeDirectory = new File(configDirectory, "upgrade");
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
      // world.  The worst that should happen is that the schema
      // changes could potentially be sent to the other servers again
      // when this server is restarted, which shouldn't hurt anything.
      // Still, we should log a warning message.
      logger.error(ERR_SCHEMA_CANNOT_WRITE_CONCAT_SCHEMA_FILE, concatFilePath, getExceptionMessage(e));
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

  /**
   * Reads the files contained in the schema directory and generates a
   * concatenated view of their contents in the provided sets.
   *
   * @param  attributeTypes     The set into which to place the
   *                            attribute types read from the schema
   *                            files.
   * @param  objectClasses      The set into which to place the object
   *                            classes read from the schema files.
   * @param  nameForms          The set into which to place the name
   *                            forms read from the schema files.
   * @param  ditContentRules    The set into which to place the DIT
   *                            content rules read from the schema
   *                            files.
   * @param  ditStructureRules  The set into which to place the DIT
   *                            structure rules read from the schema
   *                            files.
   * @param  matchingRuleUses   The set into which to place the
   *                            matching rule uses read from the
   *                            schema files.
   * @param ldapSyntaxes The set into which to place the
   *                            ldap syntaxes read from the
   *                            schema files.
   *
   * @throws  IOException  If a problem occurs while reading the
   *                       schema file elements.
   */
  public static void genConcatenatedSchema(
                          Set<String> attributeTypes,
                          Set<String> objectClasses,
                          Set<String> nameForms,
                          Set<String> ditContentRules,
                          Set<String> ditStructureRules,
                          Set<String> matchingRuleUses,
                          Set<String> ldapSyntaxes)
          throws IOException
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

      // Iterate through each line in the list.  Find the colon and
      // get the attribute name at the beginning.  If it's something
      // that we don't recognize, then skip it.  Otherwise, add the
      // X-SCHEMA-FILE extension and add it to the appropriate schema
      // element list.
      for (StringBuilder buffer : lines)
      {
        String line = buffer.toString().trim();
        parseSchemaLine(line, f.getName(), attributeTypes, objectClasses,
            nameForms, ditContentRules, ditStructureRules, matchingRuleUses,
            ldapSyntaxes);
      }
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



  /**
   * Reads data from the specified concatenated schema file into the
   * provided sets.
   *
   * @param  concatSchemaFile   The concatenated schema file to be read.
   * @param  attributeTypes     The set into which to place the attribute types
   *                            read from the concatenated schema file.
   * @param  objectClasses      The set into which to place the object classes
   *                            read from the concatenated schema file.
   * @param  nameForms          The set into which to place the name forms
   *                            read from the concatenated schema file.
   * @param  ditContentRules    The set into which to place the DIT content rules
   *                            read from the concatenated schema file.
   * @param  ditStructureRules  The set into which to place the DIT structure rules
   *                            read from the concatenated schema file.
   * @param  matchingRuleUses   The set into which to place the matching rule
   *                            uses read from the concatenated schema file.
   * @param ldapSyntaxes        The set into which to place the ldap syntaxes
   *                            read from the concatenated schema file.
   *
   * @throws  IOException  If a problem occurs while reading the
   *                       schema file elements.
   */
  public static void readConcatenatedSchema(File concatSchemaFile,
                          Set<String> attributeTypes,
                          Set<String> objectClasses,
                          Set<String> nameForms,
                          Set<String> ditContentRules,
                          Set<String> ditStructureRules,
                          Set<String> matchingRuleUses,
                          Set<String> ldapSyntaxes)
          throws IOException
  {
    try (BufferedReader reader = new BufferedReader(new FileReader(concatSchemaFile)))
    {
      String line;
      while ((line = reader.readLine()) != null)
      {
        parseSchemaLine(line, null, attributeTypes, objectClasses,
            nameForms, ditContentRules, ditStructureRules, matchingRuleUses,
            ldapSyntaxes);
      }
    }
  }

  private static void parseSchemaLine(String definition, String fileName,
      Set<String> attributeTypes,
      Set<String> objectClasses,
      Set<String> nameForms,
      Set<String> ditContentRules,
      Set<String> ditStructureRules,
      Set<String> matchingRuleUses,
      Set<String> ldapSyntaxes)
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
    } catch (ParseException pe)
    {
      logger.error(ERR_SCHEMA_PARSE_LINE.get(definition, pe.getLocalizedMessage()));
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

    return addSchemaFileToElementDefinitionIfAbsent(definition, schemaFile);
  }

  /**
   * Compares the provided sets of schema element definitions and
   * writes any differences found into the given list of
   * modifications.
   *
   * @param  oldElements  The set of elements of the specified type
   *                      read from the previous concatenated schema
   *                      files.
   * @param  newElements  The set of elements of the specified type
   *                      read from the server's current schema.
   * @param  elementType  The attribute type associated with the
   *                      schema element being compared.
   * @param  mods         The list of modifications into which any
   *                      identified differences should be written.
   */
  public static void compareConcatenatedSchema(
                          Set<String> oldElements,
                          Set<String> newElements,
                          AttributeType elementType,
                          List<Modification> mods)
  {
    AttributeBuilder builder = new AttributeBuilder(elementType);
    addModification(mods, DELETE, oldElements, newElements, builder);

    builder.setAttributeDescription(AttributeDescription.create(elementType));
    addModification(mods, ADD, newElements, oldElements, builder);
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

  /**
   * Destroys the structures maintained by the schema so that they are
   * no longer usable. This should only be called at the end of the
   * server shutdown process, and it can help detect inappropriate
   * cached references.
   */
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=true)
  public synchronized void destroy()
  {
    if (schemaNG != null)
    {
      schemaNG = null;
    }

    if (extraAttributes != null)
    {
      extraAttributes.clear();
      extraAttributes = null;
    }
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

  /** Interface to update a schema provided a schema builder. */
  public interface SchemaUpdater
  {
    /**
     * Returns an updated schema.
     *
     * @param builder
     *          The builder on the current schema
     * @return the new schema
     */
    org.forgerock.opendj.ldap.schema.Schema update(SchemaBuilder builder);
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
        public org.forgerock.opendj.ldap.schema.Schema update(SchemaBuilder builder)
        {
          return builder.setOption(option, newValue).toSchema();
        }
      });
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

  private void switchSchema(org.forgerock.opendj.ldap.schema.Schema newSchema) throws DirectoryException
  {
    rejectSchemaWithWarnings(newSchema);
    schemaNG = newSchema.asNonStrictSchema();
    if (DirectoryServer.getSchema() == this)
    {
      org.forgerock.opendj.ldap.schema.Schema.setDefaultSchema(schemaNG);
    }
  }

  private void rejectSchemaWithWarnings(org.forgerock.opendj.ldap.schema.Schema newSchema) throws DirectoryException
  {
    Collection<LocalizableMessage> warnings = newSchema.getWarnings();
    if (!warnings.isEmpty())
    {
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
          ERR_SCHEMA_HAS_WARNINGS.get(warnings.size(), Utils.joinAsString("; ", warnings)));
    }
  }

  /**
   * Replaces an existing object class by another object class.
   *
   * @param objectClass
   *          Object class to register to the schema.
   * @param existingClass
   *          Object class to remove from the schema.
   * @param schemaFile
   *          The schema file which the new object class belongs to.
   * @throws DirectoryException
   *            If an errors occurs.
   */
  public void replaceObjectClass(ObjectClass objectClass, ObjectClass existingClass, String schemaFile)
      throws DirectoryException
  {
    exclusiveLock.lock();
    try
    {
      SchemaBuilder builder = new SchemaBuilder(schemaNG);
      builder.removeObjectClass(existingClass.getNameOrOID());
      registerObjectClass0(builder, objectClass, schemaFile, false);
      switchSchema(builder.toSchema());
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }
}
