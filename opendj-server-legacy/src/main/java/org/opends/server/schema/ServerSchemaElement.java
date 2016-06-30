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
package org.opends.server.schema;

import static org.opends.server.types.Schema.addSchemaFileToElementDefinitionIfAbsent;
import static org.opends.messages.SchemaMessages.ERR_ATTR_SYNTAX_ILLEGAL_X_SCHEMA_FILE;
import static org.opends.server.util.ServerConstants.SCHEMA_PROPERTY_FILENAME;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.SchemaElement;
import org.opends.server.core.ServerContext;
import org.opends.server.types.DirectoryException;
import org.opends.server.util.ServerConstants;

/**
 * Utility class that provides common operations over schema elements in a server context.
 */
public class ServerSchemaElement
{

  private ServerSchemaElement()
  {
    // prevent instantiation
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
  public static String getDefinitionWithFileName(SchemaElement element)
  {
    final String definition = element.toString();
    return addSchemaFileToElementDefinitionIfAbsent(definition, getSchemaFile(element));
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
  public static String getExtraPropertyAsSingleValue(SchemaElement element, String property)
  {
    List<String> values = element.getExtraProperties().get(property);
    return values != null && !values.isEmpty() ? values.get(0) : null;
  }

  /**
   * Returns the origin of the provided schema element.
   *
   * @param element
   *            The schema element.
   * @return the origin of the schema element as defined in the extra properties.
   */
  public static String getOrigin(SchemaElement element)
  {
    return getExtraPropertyAsSingleValue(element, ServerConstants.SCHEMA_PROPERTY_ORIGIN);
  }

  /**
   * Returns the schema file of the provided schema element.
   *
   * @param element
   *            The schema element.
   * @return the schema file of schema element.
   */
  public static String getSchemaFile(SchemaElement element)
  {
    return getExtraPropertyAsSingleValue(element, ServerConstants.SCHEMA_PROPERTY_FILENAME);
  }

  /**
   * Updates the property of the provided attribute type.
   *
   * @param serverContext
   *          the server context
   * @param attributeType
   *          attribute type to update
   * @param property
   *          the property to set
   * @param values
   *          the values to set
   * @return the updated attribute type
   */
  public static AttributeType updateProperty(ServerContext serverContext, AttributeType attributeType, String property,
      String...values)
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
