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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.types;

import static org.forgerock.util.Reject.*;
import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;

import java.util.List;
import java.util.Map;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.SchemaElement;
import org.opends.server.util.RemoveOnceSDKSchemaIsUsed;

/**
 * Utility class to retrieve information from a SchemaElement and to set extra property
 * for a SchemaElement.
 * <p>
 * Note that {@code setSchemaFile()} method works ONLY for non-SDK classes, because SDK schema
 * elements are immutable, so modifying the map for extra properties has no effect on the actual
 * element.
 */
@RemoveOnceSDKSchemaIsUsed("All read methods can be provided by ServerSchemaElement class. Write method" +
 " has to rebuild fully the schema element within the schema, which means specific code for each element")
public final class CommonSchemaElements
{
  private CommonSchemaElements()
  {
    // private for utility classes
  }

  /**
   * Check if the extra schema properties contain safe filenames.
   *
   * @param extraProperties
   *          The schema properties to check.
   *
   * @throws DirectoryException
   *          If a provided value was unsafe.
   */
  public static void checkSafeProperties(Map <String,List<String>>
      extraProperties)
      throws DirectoryException
  {
    // Check that X-SCHEMA-FILE doesn't contain unsafe characters
    List<String> filenames = extraProperties.get(SCHEMA_PROPERTY_FILENAME);
    if (filenames != null && !filenames.isEmpty()) {
      String filename = filenames.get(0);
      if (filename.indexOf('/') != -1 || filename.indexOf('\\') != -1)
      {
        LocalizableMessage message = ERR_ATTR_SYNTAX_ILLEGAL_X_SCHEMA_FILE.get(filename);
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
            message);
      }
    }
  }

  /**
   * Retrieves the name of the schema file that contains the
   * definition for this schema definition.
   *
   * @param elem The element where to get the schema file from
   * @return The name of the schema file that contains the definition
   *         for this schema definition, or <code>null</code> if it
   *         is not known or if it is not stored in any schema file.
   */
  public static String getSchemaFile(SchemaElement elem)
  {
    return getSingleValueProperty(elem, SCHEMA_PROPERTY_FILENAME);
  }

  /**
   * Retrieves the name of a single value property for this schema element.
   *
   * @param elem The element where to get the single value property from
   * @param propertyName The name of the property to get
   * @return The single value for this property, or <code>null</code> if it
   *         is this property is not set.
   */
  public static String getSingleValueProperty(SchemaElement elem,
      String propertyName)
  {
    List<String> values = elem.getExtraProperties().get(propertyName);
    if (values != null && !values.isEmpty()) {
      return values.get(0);
    }
    return null;
  }

  /**
   * Specifies the name of the schema file that contains the
   * definition for this schema element.  If a schema file is already
   * defined in the set of extra properties, then it will be
   * overwritten.  If the provided schema file value is {@code null},
   * then any existing schema file definition will be removed.
   *
   * @param elem The element where to set the schema file
   * @param  schemaFile  The name of the schema file that contains the
   *                     definition for this schema element.
   */
  public static void setSchemaFile(SchemaElement elem, String schemaFile)
  {
    setExtraProperty(elem, SCHEMA_PROPERTY_FILENAME, schemaFile);
  }

  /**
   * Sets the value for an "extra" property for this schema element.
   * If a property already exists with the specified name, then it
   * will be overwritten.  If the value is {@code null}, then any
   * existing property with the given name will be removed.
   *
   * @param elem The element where to set the extra property
   * @param  name   The name for the "extra" property.  It must not be
   *                {@code null}.
   * @param  value  The value for the "extra" property.  If it is
   *                {@code null}, then any existing definition will be removed.
   */
  private static void setExtraProperty(SchemaElement elem, String name, String value)
  {
    ifNull(name);

    if (value == null)
    {
      elem.getExtraProperties().remove(name);
    }
    else
    {
      elem.getExtraProperties().put(name, newLinkedList(value));
    }
  }

  /**
   * Retrieves the definition string used to create this attribute
   * type and including the X-SCHEMA-FILE extension.
   *
   * @param elem The element where to get definition from
   * @return  The definition string used to create this attribute
   *          type including the X-SCHEMA-FILE extension.
   */
  public static String getDefinitionWithFileName(SchemaElement elem)
  {
    final String definition = elem.toString();
    return Schema.addSchemaFileToElementDefinitionIfAbsent(definition, getSchemaFile(elem));
  }
}
