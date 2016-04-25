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

import java.util.List;

import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.SchemaElement;
import org.opends.server.core.ServerContext;
import org.opends.server.util.ServerConstants;

/**
 * Provides common operations for server schema elements.
 */
public class ServerSchemaElement
{

  private final SchemaElement element;

  /**
   * Creates an element.
   *
   * @param element
   *            The schema element to wrap.
   */
  public ServerSchemaElement(SchemaElement element)
  {
    this.element = element;
  }

  /**
   * Returns the schema file of the provided schema element.
   *
   * @return the schema file of the provided schema element.
   */
  public String getSchemaFile()
  {
    return getExtraPropertySingleValue(ServerConstants.SCHEMA_PROPERTY_FILENAME);
  }

  /**
   * Returns the origin of the provided schema element.
   *
   * @return the origin of the provided schema element.
   */
  public String getOrigin()
  {
    return getExtraPropertySingleValue(ServerConstants.SCHEMA_PROPERTY_ORIGIN);
  }

  private String getExtraPropertySingleValue(String property)
  {
    List<String> values = element.getExtraProperties().get(property);
    return values != null && !values.isEmpty() ? values.get(0) : null;
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
