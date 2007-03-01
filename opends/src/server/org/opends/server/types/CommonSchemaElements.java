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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;

import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.toLowerCase;
import static org.opends.server.util.Validator.*;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;



/**
 * An abstract base class for LDAP schema definitions which contain an
 * OID, optional names, description, an obsolete flag, and an optional
 * set of extra properties.
 * <p>
 * This class defines common properties and behaviour of the various
 * types of schema definitions (e.g. object class definitions, and
 * attribute type definitions).
 * <p>
 * Any methods which accesses the set of names associated with this
 * definition, will retrieve the primary name as the first name,
 * regardless of whether or not it was contained in the original set
 * of <code>names</code> passed to the constructor.
 * <p>
 * Where ordered sets of names, or extra properties are provided, the
 * ordering will be preserved when the associated fields are accessed
 * via their getters or via the {@link #toString()} methods.
 * <p>
 * Note that these schema elements are not completely immutable, as
 * the set of extra properties for the schema element may be altered
 * after the element is created.  Among other things, this allows the
 * associated schema file to be edited so that an element created over
 * protocol may be associated with a particular schema file.
 */
public abstract class CommonSchemaElements {

  // Indicates whether this definition is declared "obsolete".
  private final boolean isObsolete;

  // The set of additional name-value pairs associated with this
  // definition.
  private final Map<String, List<String>> extraProperties;

  // The set of names for this definition, in a mapping between
  // the all-lowercase form and the user-defined form.
  private final Map<String, String> names;

  // The description for this definition.
  private final String description;

  // The OID that may be used to reference this definition.
  private final String oid;

  // The primary name to use for this definition.
  private final String primaryName;

  // The lower case name for this definition.
  private final String lowerName;



  /**
   * Creates a new definition with the provided information.
   * <p>
   * If no <code>primaryName</code> is specified, but a set of
   * <code>names</code> is specified, then the first name retrieved
   * from the set of <code>names</code> will be used as the primary
   * name.
   *
   * @param primaryName
   *          The primary name for this definition, or
   *          <code>null</code> if there is no primary name.
   * @param names
   *          The full set of names for this definition, or
   *          <code>null</code> if there are no names.
   * @param oid
   *          The OID for this definition (must not be
   *          <code>null</code>).
   * @param description
   *          The description for the definition, or <code>null</code>
   *          if there is no description.
   * @param isObsolete
   *          Indicates whether this definition is declared
   *          "obsolete".
   * @param extraProperties
   *          A set of extra properties for this definition, or
   *          <code>null</code> if there are no extra properties.
   * @throws NullPointerException
   *           If the provided OID was <code>null</code>.
   */
  protected CommonSchemaElements(String primaryName,
      Collection<String> names, String oid, String description,
      boolean isObsolete, Map<String, List<String>> extraProperties)
      throws NullPointerException {


    // Make sure mandatory parameters are specified.
    if (oid == null) {
      throw new NullPointerException(
          "No oid specified in constructor");
    }

    this.oid = oid;
    this.description = description;
    this.isObsolete = isObsolete;

    // Make sure we have a primary name if possible.
    if (primaryName == null) {
      if (names != null && !names.isEmpty()) {
        this.primaryName = names.iterator().next();
      } else {
        this.primaryName = null;
      }
    } else {
      this.primaryName = primaryName;
    }
    this.lowerName = toLowerCase(primaryName);

    // Construct the normalized attribute name mapping.
    if (names != null) {
      this.names = new LinkedHashMap<String, String>(names.size());

      // Make sure the primary name is first (never null).
      this.names.put(lowerName, this.primaryName);

      // Add the remaining names in the order specified.
      for (String name : names) {
        this.names.put(toLowerCase(name), name);
      }
    } else if (this.primaryName != null) {
      this.names = Collections.singletonMap(lowerName,
          this.primaryName);
    } else {
      this.names = Collections.emptyMap();
    }

    // FIXME: should really be a deep-copy.
    if (extraProperties != null) {
      this.extraProperties = new LinkedHashMap<String, List<String>>(
          extraProperties);
    } else {
      this.extraProperties = Collections.emptyMap();
    }
  }



  /**
   * Retrieves the primary name for this schema definition.
   *
   * @return The primary name for this schema definition, or
   *         <code>null</code> if there is no primary name.
   */
  public final String getPrimaryName() {

    return primaryName;
  }



  /**
   * Retrieve the normalized primary name for this schema definition.
   *
   * @return Returns the normalized primary name for this attribute
   *         type, or <code>null</code> if there is no primary name.
   */
  public final String getNormalizedPrimaryName() {

    return lowerName;
  }



  /**
   * Retrieves an iterable over the set of normalized names that may
   * be used to reference this schema definition. The normalized form
   * of an attribute name is defined as the user-defined name
   * converted to lower-case.
   *
   * @return Returns an iterable over the set of normalized names that
   *         may be used to reference this schema definition.
   */
  public final Iterable<String> getNormalizedNames() {

    return names.keySet();
  }



  /**
   * Retrieves an iterable over the set of user-defined names that may
   * be used to reference this schema definition.
   *
   * @return Returns an iterable over the set of user-defined names
   *         that may be used to reference this schema definition.
   */
  public final Iterable<String> getUserDefinedNames() {

    return names.values();
  }



  /**
   * Indicates whether this schema definition has the specified name.
   *
   * @param lowerName
   *          The lowercase name for which to make the determination.
   * @return <code>true</code> if the specified name is assigned to
   *         this schema definition, or <code>false</code> if not.
   */
  public final boolean hasName(String lowerName) {

    return names.containsKey(lowerName);
  }



  /**
   * Retrieves the OID for this schema definition.
   *
   * @return The OID for this schema definition.
   */
  public final String getOID() {

    return oid;
  }



  /**
   * Retrieves the name or OID for this schema definition. If it has
   * one or more names, then the primary name will be returned. If it
   * does not have any names, then the OID will be returned.
   *
   * @return The name or OID for this schema definition.
   */
  public final String getNameOrOID() {

    if (primaryName != null) {
      return primaryName;
    } else {
      // Guaranteed not to be null.
      return oid;
    }
  }



  /**
   * Indicates whether this schema definition has the specified name
   * or OID.
   *
   * @param lowerValue
   *          The lowercase value for which to make the determination.
   * @return <code>true</code> if the provided value matches the OID
   *         or one of the names assigned to this schema definition,
   *         or <code>false</code> if not.
   */
  public final boolean hasNameOrOID(String lowerValue) {

    if (names.containsKey(lowerValue)) {
      return true;
    }

    return oid.equals(lowerValue);
  }



  /**
   * Retrieves the name of the schema file that contains the
   * definition for this schema definition.
   *
   * @return The name of the schema file that contains the definition
   *         for this schema definition, or <code>null</code> if it
   *         is not known or if it is not stored in any schema file.
   */
  public final String getSchemaFile() {

    List<String> values = extraProperties
        .get(SCHEMA_PROPERTY_FILENAME);
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
   * @param  schemaFile  The name of the schema file that contains the
   *                     definition for this schema element.
   */
  public final void setSchemaFile(String schemaFile) {

    setExtraProperty(SCHEMA_PROPERTY_FILENAME, schemaFile);
  }



  /**
   * Retrieves the description for this schema definition.
   *
   * @return The description for this schema definition, or
   *         <code>null</code> if there is no description.
   */
  public final String getDescription() {

    return description;
  }



  /**
   * Indicates whether this schema definition is declared "obsolete".
   *
   * @return <code>true</code> if this schema definition is declared
   *         "obsolete", or <code>false</code> if not.
   */
  public final boolean isObsolete() {

    return isObsolete;
  }



  /**
   * Retrieves an iterable over the names of "extra" properties
   * associated with this schema definition.
   *
   * @return Returns an iterable over the names of "extra" properties
   *         associated with this schema definition.
   */
  public final Iterable<String> getExtraPropertyNames() {

    return extraProperties.keySet();
  }



  /**
   * Retrieves an iterable over the value(s) of the specified "extra"
   * property for this schema definition.
   *
   * @param name
   *          The name of the "extra" property for which to retrieve
   *          the value(s).
   * @return Returns an iterable over the value(s) of the specified
   *         "extra" property for this schema definition, or
   *         <code>null</code> if no such property is defined.
   */
  public final Iterable<String> getExtraProperty(String name) {

    return extraProperties.get(name);
  }



  /**
   * Sets the value for an "extra" property for this schema element.
   * If a property already exists with the specified name, then it
   * will be overwritten.  If the value is {@code null}, then any
   * existing property with the given name will be removed.
   *
   * @param  name   The name for the "extra" property.  It must not be
   *                {@code null}.
   * @param  value  The value for the "extra" property.  If it is
   *                {@code null}, then any existing definition will be
   *                removed.
   */
  public final void setExtraProperty(String name, String value) {

    ensureNotNull(name);

    if (value == null)
    {
      extraProperties.remove(name);
    }
    else
    {
      LinkedList<String> values = new LinkedList<String>();
      values.add(value);

      extraProperties.put(name, values);
    }
  }



  /**
   * Sets the values for an "extra" property for this schema element.
   * If a property already exists with the specified name, then it
   * will be overwritten.  If the set of values is {@code null} or
   * empty, then any existing property with the given name will be
   * removed.
   *
   * @param  name    The name for the "extra" property.  It must not
   *                 be {@code null}.
   * @param  values  The set of values for the "extra" property.  If
   *                 it is {@code null} or empty, then any existing
   *                 definition will be removed.
   */
  public final void setExtraProperty(String name,
                                     List<String> values) {

    ensureNotNull(name);

    if ((values == null) || values.isEmpty())
    {
      extraProperties.remove(name);
    }
    else
    {
      LinkedList<String> valuesCopy = new LinkedList<String>(values);
      extraProperties.put(name, valuesCopy);
    }
  }



  /**
   * Indicates whether the provided object is equal to this attribute
   * type. The object will be considered equal if it is an attribute
   * type with the same OID as the current type.
   *
   * @param o
   *          The object for which to make the determination.
   * @return <code>true</code> if the provided object is equal to
   *         this schema definition, or <code>false</code> if not.
   */
  public final boolean equals(Object o) {

    if (this == o) {
      return true;
    }

    if (o instanceof CommonSchemaElements) {
      CommonSchemaElements other = (CommonSchemaElements) o;
      return oid.equals(other.oid);
    }

    return false;
  }



  /**
   * Retrieves the hash code for this schema definition. It will be
   * based on the sum of the bytes of the OID.
   *
   * @return The hash code for this schema definition.
   */
  public final int hashCode() {

    return oid.hashCode();
  }



  /**
   * Retrieves the string representation of this schema definition in
   * the form specified in RFC 2252.
   *
   * @return The string representation of this schema definition in
   *         the form specified in RFC 2252.
   */
  public final String toString() {

    StringBuilder buffer = new StringBuilder();
    toString(buffer, true);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this schema definition in the
   * form specified in RFC 2252 to the provided buffer.
   *
   * @param buffer
   *          The buffer to which the information should be appended.
   * @param includeFileElement
   *          Indicates whether to include an "extra" property that
   *          specifies the path to the schema file from which this
   *          schema definition was loaded.
   */
  public final void toString(StringBuilder buffer,
      boolean includeFileElement) {

    buffer.append("( ");
    buffer.append(oid);

    if (!names.isEmpty()) {
      Iterator<String> iterator = names.values().iterator();

      String firstName = iterator.next();
      if (iterator.hasNext()) {
        buffer.append(" NAME ( '");
        buffer.append(firstName);

        while (iterator.hasNext()) {
          buffer.append("' '");
          buffer.append(iterator.next());
        }

        buffer.append("' )");
      } else {
        buffer.append(" NAME '");
        buffer.append(firstName);
        buffer.append("'");
      }
    }

    if ((description != null) && (description.length() > 0)) {
      buffer.append(" DESC '");
      buffer.append(description);
      buffer.append("'");
    }

    if (isObsolete) {
      buffer.append(" OBSOLETE");
    }

    // Delegate remaining string output to sub-class.
    toStringContent(buffer);

    if (!extraProperties.isEmpty()) {
      for (Map.Entry<String, List<String>> e : extraProperties
          .entrySet()) {

        String property = e.getKey();
        if (!includeFileElement
            && property.equals(SCHEMA_PROPERTY_FILENAME)) {
          // Don't include the schema file if it was not requested.
          continue;
        }

        List<String> valueList = e.getValue();

        buffer.append(" ");
        buffer.append(property);

        if (valueList.size() == 1) {
          buffer.append(" '");
          buffer.append(valueList.get(0));
          buffer.append("'");
        } else {
          buffer.append(" ( ");

          for (String value : valueList) {
            buffer.append("'");
            buffer.append(value);
            buffer.append("' ");
          }

          buffer.append(")");
        }
      }
    }

    buffer.append(" )");
  }



  /**
   * Appends a string representation of this schema definition's
   * non-generic properties to the provided buffer.
   *
   * @param buffer
   *          The buffer to which the information should be appended.
   */
  protected abstract void toStringContent(StringBuilder buffer);
}
