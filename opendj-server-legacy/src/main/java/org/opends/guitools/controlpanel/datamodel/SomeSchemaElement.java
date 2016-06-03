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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.datamodel;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.SchemaElement;
import org.opends.server.config.ConfigConstants;
import org.opends.server.core.ServerContext;
import org.opends.server.schema.ServerSchemaElement;
import org.opends.server.util.RemoveOnceSDKSchemaIsUsed;

/**
 * Represents a schema element which is either an attribute type or an object class.
 * <p>
 * Allows to share the methods getOID(), getNameOrOID(), getNames() and a setter on extra properties.
 */
@RemoveOnceSDKSchemaIsUsed("Some retrieval methods can be provided by ServerSchemaElement class. Others are only" +
 "necessary for the control panel code, including the setter methods: specific control panel class could handle it.")
public class SomeSchemaElement implements SchemaElement
{
  private ObjectClass objectClass;
  private AttributeType attributeType;
  private ServerSchemaElement element;

  /**
   * Builds SomeSchemaElement.
   *
   * @param objectClass
   *          the common schema element to wrap
   */
  public SomeSchemaElement(ObjectClass objectClass)
  {
    this.objectClass = objectClass;
    this.attributeType = null;
  }

  /**
   * Builds SomeSchemaElement.
   *
   * @param attributeType
   *          the attribute type element to wrap
   */
  public SomeSchemaElement(AttributeType attributeType)
  {
    this.objectClass = null;
    this.attributeType = attributeType;
  }

  /**
   * Returns the wrapped schema element as an object class.
   *
   * @return the wrapped object class
   */
  public ObjectClass getObjectClass()
  {
    return objectClass;
  }

  /**
   * Returns the wrapped schema element as an attribute type.
   *
   * @return the wrapped attribute type
   */
  public AttributeType getAttributeType()
  {
    return attributeType;
  }

  /**
   * Returns whether the wrapped element is an attribute type.
   *
   * @return {@code true} when the wrapped element is an attribute type, {@code false} otherwise
   */
  public boolean isAttributeType()
  {
    return attributeType != null;
  }

  private ServerSchemaElement asServerSchemaElement()
  {
    if (element == null)
    {
      element = attributeType != null ? new ServerSchemaElement(attributeType) : new ServerSchemaElement(objectClass);
    }
    return element;
  }

  /**
   * Returns the OID of the wrapped element.
   *
   * @return the OID of the wrapped element.
   */
  public String getOID()
  {
    return attributeType != null ? attributeType.getOID() : objectClass.getOID();
  }

  /**
   * Returns the name or OID of the wrapped element.
   *
   * @return the name or OID of the wrapped element.
   */
  public String getNameOrOID()
  {
    return attributeType != null ? attributeType.getNameOrOID() : objectClass.getNameOrOID();
  }

  /**
   * Returns the names of the wrapped element.
   *
   * @return the names of the wrapped element.
   */
  public Iterable<String> getNames()
  {
    return attributeType != null ? attributeType.getNames() : objectClass.getNames();
  }

  @Override
  public String getDescription()
  {
    return asServerSchemaElement().getDescription();
  }

  @Override
  public Map<String, List<String>> getExtraProperties()
  {
    return asServerSchemaElement().getExtraProperties();
  }

  @Override
  public String toString()
  {
    return asServerSchemaElement().toString();
  }

  /**
   * Retrieves the definition string used to create this attribute
   * type and including the X-SCHEMA-FILE extension.
   *
   * @return  The definition string used to create this attribute
   *          type including the X-SCHEMA-FILE extension.
   */
  public String getDefinitionWithFileName()
  {
    return asServerSchemaElement().getDefinitionWithFileName();
  }

  /**
   * Returns the name of the schema file that contains the definition of the wrapped element.
   *
   * @return the name of the schema file that contains the definition of the wrapped element.
   */
  public String getSchemaFile()
  {
    return asServerSchemaElement().getSchemaFile();
  }

  /**
   * Returns the origin of the provided schema element.
   * @return the origin of the provided schema element.
   */
  public String getOrigin()
  {
    return asServerSchemaElement().getOrigin();
  }

  /**
   * Returns the attribute name of the wrapped element.
   * <p>
   * This corresponds to the attribute name in the schema entry that corresponds to the provided
   * schema element.
   *
   * @return the attribute name of the wrapped element.
   */
  public String getAttributeName()
  {
    return attributeType != null ? ConfigConstants.ATTR_ATTRIBUTE_TYPES : ConfigConstants.ATTR_OBJECTCLASSES;
  }

  /**
   * Sets a single-valued extra property on the wrapped element.
   *
   * @param serverContext
   *          the server context
   * @param property
   *          the property to set
   * @param value
   *          the value to set
   */
  public void setExtraPropertySingleValue(ServerContext serverContext, String property, String value)
  {
    List<String> values = value != null ? Arrays.asList(value) : null;
    setExtraPropertyMultipleValues(serverContext, property, values);
  }

  /**
   * Sets a multi-valued extra property on the wrapped element.
   *
   * @param serverContext
   *          the server context
   * @param property
   *          the property to set
   * @param values
   *          the values to set
   */
  public void setExtraPropertyMultipleValues(ServerContext serverContext, String property, List<String> values)
  {
    Schema schemaNG = serverContext != null ? serverContext.getSchemaNG() : Schema.getDefaultSchema();
    SchemaBuilder schemaBuilder = new SchemaBuilder(schemaNG);
    if (attributeType != null)
    {
      AttributeType.Builder builder =
          schemaBuilder.buildAttributeType(attributeType).removeExtraProperty(property, (String) null);
      if (values != null  && !values.isEmpty())
      {
        builder.extraProperties(property, values);
      }
      attributeType = builder.addToSchemaOverwrite().toSchema().getAttributeType(attributeType.getNameOrOID());
    }
    else
    {
      ObjectClass.Builder builder =
          schemaBuilder.buildObjectClass(objectClass).removeExtraProperty(property, (String) null);
      if (values != null && !values.isEmpty())
      {
        builder.extraProperties(property, values);
      }
      objectClass = builder.addToSchemaOverwrite().toSchema().getObjectClass(objectClass.getNameOrOID());
    }
  }

  /**
   * Returns a copy of the provided attribute type, changing the superior attribute type.
   *
   * @param attributeType
   *          the attribute type for which a modified copy must be built
   * @param newSuperiorType
   *          the new superior attribute type to set, {@code null} means remove the superior type
   * @return an attribute type builder to build an updated copy of the provided attribute type
   */
  public static AttributeType changeSuperiorType(AttributeType attributeType, AttributeType newSuperiorType)
  {
    String superiorTypeOID = newSuperiorType != null ? newSuperiorType.getNameOrOID() : null;
    Schema schema = new SchemaBuilder()
      .buildAttributeType(attributeType)
      .superiorType(superiorTypeOID)
      .addToSchemaOverwrite()
      .toSchema();
    return schema.getAttributeType(attributeType.getNameOrOID());
  }
}
