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

import static org.opends.server.types.Schema.addSchemaFileToElementDefinitionIfAbsent;
import static org.opends.server.schema.SchemaConstants.SYNTAX_AUTH_PASSWORD_OID;
import static org.opends.server.schema.SchemaConstants.SYNTAX_USER_PASSWORD_OID;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.SchemaElement;
import org.opends.server.core.ServerContext;

/** Utility methods related to schema. */
public class SchemaUtils
{

  /** Private constructor to prevent instantiation. */
  private SchemaUtils() {
    // No implementation required.
  }

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
    return addSchemaFileToElementDefinitionIfAbsent(definition, SchemaUtils.getElementSchemaFile(element));
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
         new SchemaBuilder(serverContext != null ? serverContext.getSchemaNG() : Schema.getDefaultSchema());
    AttributeType.Builder builder =
        schemaBuilder.buildAttributeType(attributeType).removeExtraProperty(property, (String) null);
    if (values != null && values.length > 0)
    {
      builder.extraProperties(property, values);
      return builder.addToSchemaOverwrite().toSchema().getAttributeType(attributeType.getNameOrOID());
    }
    return attributeType;
  }
}
