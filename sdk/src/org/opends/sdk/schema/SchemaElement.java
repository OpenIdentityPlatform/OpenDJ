/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk.schema;



import java.util.List;
import java.util.Map;

import com.sun.opends.sdk.util.Message;
import com.sun.opends.sdk.util.Validator;



/**
 * An abstract base class for LDAP schema definitions which contain an
 * description, and an optional set of extra properties.
 * <p>
 * This class defines common properties and behaviour of the various
 * types of schema definitions (e.g. object class definitions, and
 * attribute type definitions).
 */
abstract class SchemaElement
{
  // The description for this definition.
  final String description;

  // The set of additional name-value pairs.
  final Map<String, List<String>> extraProperties;



  SchemaElement(String description,
      Map<String, List<String>> extraProperties)
  {
    Validator.ensureNotNull(description, extraProperties);
    this.description = description;
    this.extraProperties = extraProperties;
  }



  /**
   * Retrieves the description for this schema definition.
   *
   * @return The description for this schema definition.
   */
  public final String getDescription()
  {

    return description;
  }



  /**
   * Retrieves an iterable over the value(s) of the specified "extra"
   * property for this schema definition.
   *
   * @param name
   *          The name of the "extra" property for which to retrieve the
   *          value(s).
   * @return Returns an iterable over the value(s) of the specified
   *         "extra" property for this schema definition, or
   *         <code>null</code> if no such property is defined.
   */
  public final Iterable<String> getExtraProperty(String name)
  {

    return extraProperties.get(name);
  }



  /**
   * Retrieves an iterable over the names of "extra" properties
   * associated with this schema definition.
   *
   * @return Returns an iterable over the names of "extra" properties
   *         associated with this schema definition.
   */
  public final Iterable<String> getExtraPropertyNames()
  {

    return extraProperties.keySet();
  }



  /**
   * Builds a string representation of this schema definition in the
   * form specified in RFC 2252.
   *
   * @return The string representation of this schema definition in the
   *         form specified in RFC 2252.
   */
  final String buildDefinition()
  {
    final StringBuilder buffer = new StringBuilder();

    buffer.append("( ");

    toStringContent(buffer);

    if (!extraProperties.isEmpty())
    {
      for (final Map.Entry<String, List<String>> e : extraProperties
          .entrySet())
      {

        final String property = e.getKey();

        final List<String> valueList = e.getValue();

        buffer.append(" ");
        buffer.append(property);

        if (valueList.size() == 1)
        {
          buffer.append(" '");
          buffer.append(valueList.get(0));
          buffer.append("'");
        }
        else
        {
          buffer.append(" ( ");

          for (final String value : valueList)
          {
            buffer.append("'");
            buffer.append(value);
            buffer.append("' ");
          }

          buffer.append(")");
        }
      }
    }

    buffer.append(" )");

    return buffer.toString();
  }



  /**
   * Appends a string representation of this schema definition's
   * non-generic properties to the provided buffer.
   *
   * @param buffer
   *          The buffer to which the information should be appended.
   */
  abstract void toStringContent(StringBuilder buffer);



  abstract void validate(List<Message> warnings, Schema schema)
      throws SchemaException;
}
