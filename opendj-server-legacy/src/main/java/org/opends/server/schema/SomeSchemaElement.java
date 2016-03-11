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
package org.opends.server.schema;

import static org.opends.server.util.ServerConstants.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.SchemaElement;
import org.opends.server.config.ConfigConstants;
import org.opends.server.core.ServerContext;
import org.opends.server.types.CommonSchemaElements;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.opends.server.util.RemoveOnceSDKSchemaIsUsed;
import org.opends.server.util.ServerConstants;

/**
 * Represents a schema element which is either a SDK attribute type or an objectclass from the server.
 * <p>
 * In absence of a common interface, this class allows to process all elements in the same way,
 * and to provide useful server-oriented methods like {@code getSchemaFile()} or
 * {@code getOrigin()}.
 */
@RemoveOnceSDKSchemaIsUsed("This class is a temporary mechanism"
    + " to manage in the same way SDK and server schema element classes")
public class SomeSchemaElement implements SchemaElement
{
  private final ObjectClass objectClass;
  private AttributeType attributeType;

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

  /**
   * Returns whether the wrapped element is an object class.
   *
   * @return {@code true} when the wrapped element is an object class, {@code false} otherwise
   */
  public boolean isObjectClass()
  {
    return objectClass != null;
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
    return attributeType != null ? attributeType.getNames() : objectClass.getNormalizedNames();
  }

  @Override
  public Map<String, List<String>> getExtraProperties()
  {
    return attributeType != null ? attributeType.getExtraProperties() : objectClass.getExtraProperties();
  }

  @Override
  public String toString()
  {
    return attributeType != null ? attributeType.toString() : objectClass.toString();
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
    final String schemaFile = getSchemaFile();
    final String definition = toString();
    if (schemaFile != null)
    {
      int pos = definition.lastIndexOf(')');
      return definition.substring(0, pos).trim() + " " + SCHEMA_PROPERTY_FILENAME + " '" + schemaFile + "' )";
    }
    return definition;
  }

  /**
   * Returns the name of the schema file that contains the definition of the wrapped element.
   *
   * @return the name of the schema file that contains the definition of the wrapped element.
   */
  public String getSchemaFile()
  {
    return getExtraPropertySingleValue(ServerConstants.SCHEMA_PROPERTY_FILENAME);
  }

  /**
   * Sets the name of the schema file that contains the definition of the wrapped element.
   *
   * @param serverContext
   *          the server context
   * @param schemaFile
   *          the name of the schema file that contains the definition of the wrapped element.
   */
  public void setSchemaFile(ServerContext serverContext, String schemaFile)
  {
    setExtraPropertySingleValue(serverContext, SCHEMA_PROPERTY_FILENAME, schemaFile);
  }

  /**
   * Returns the origin of the provided schema element.
   * @return the origin of the provided schema element.
   */
  public String getOrigin()
  {
    return getExtraPropertySingleValue(ServerConstants.SCHEMA_PROPERTY_ORIGIN);
  }

  private String getExtraPropertySingleValue(String schemaPropertyOrigin)
  {
    if (objectClass != null)
    {
      return CommonSchemaElements.getSingleValueProperty(objectClass, schemaPropertyOrigin);
    }
    List<String> values = attributeType.getExtraProperties().get(schemaPropertyOrigin);
    return values != null && !values.isEmpty() ? values.get(0) : null;
  }

  /**
   * Returns the attribute name of the wrapped element.
   * @return the attribute name of the wrapped element.
   */
  public String getAttributeName()
  {
    return attributeType!= null ? ConfigConstants.ATTR_ATTRIBUTE_TYPES : ConfigConstants.ATTR_OBJECTCLASSES;
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
    if (attributeType != null)
    {
      List<String> values = value != null ?  Arrays.asList(value) : null;
      setExtraPropertyMultipleValues(serverContext, property, values);
    }
    else
    {
      CommonSchemaElements.setExtraProperty(objectClass, property, value);
    }
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
    if (attributeType != null)
    {
      SchemaBuilder schemaBuilder = serverContext != null ?
          new SchemaBuilder(serverContext.getSchemaNG()) : new SchemaBuilder(Schema.getDefaultSchema());
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
      objectClass.setExtraProperty(property, values);
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

  @Override
  public String getDescription()
  {
    return attributeType != null ? attributeType.getDescription() : objectClass.getDescription();
  }
}
