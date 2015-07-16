/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.types;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ResultCode;

import static org.forgerock.util.Reject.*;
import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

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
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public abstract class CommonSchemaElements implements SchemaFileElement {

  /** Indicates whether this definition is declared "obsolete". */
  private final boolean isObsolete;

  /** The hash code for this definition. */
  private final int hashCode;

  /**
   * The set of additional name-value pairs associated with this
   * definition.
   */
  private final Map<String, List<String>> extraProperties;

  /**
   * The set of names for this definition, in a mapping between
   * the all-lowercase form and the user-defined form.
   */
  private final Map<String, String> names;

  /** The description for this definition. */
  private final String description;

  /** The OID that may be used to reference this definition. */
  private final String oid;

  /** The primary name to use for this definition. */
  private final String primaryName;

  /** The lower case name for this definition. */
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

    // OPENDJ-1645: oid changes during server bootstrap, so prefer using name if available
    hashCode = getNameOrOID().hashCode();

    // Construct the normalized attribute name mapping.
    if (names != null) {
      this.names = new LinkedHashMap<>(names.size());

      // Make sure the primary name is first (never null).
      this.names.put(lowerName, this.primaryName);

      // Add the remaining names in the order specified.
      for (String name : names) {
        this.names.put(toLowerCase(name), name);
      }
    } else if (this.primaryName != null) {
      this.names = Collections.singletonMap(lowerName, this.primaryName);
    } else {
      this.names = Collections.emptyMap();
    }

    // FIXME: should really be a deep-copy.
    if (extraProperties != null) {
      this.extraProperties = new LinkedHashMap<>(extraProperties);
    } else {
      this.extraProperties = Collections.emptyMap();
    }
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
    }
    // Guaranteed not to be null.
    return oid;
  }

  /**
   * Retrieves the normalized primary name or OID for this schema
   * definition. If it does not have any names, then the OID will be
   * returned.
   *
   * @return The name or OID for this schema definition.
   */
  public final String getNormalizedPrimaryNameOrOID() {
    if (lowerName != null) {
      return lowerName;
    }
    // Guaranteed not to be null.
    return oid;
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

    return names.containsKey(lowerValue) || oid.equals(lowerValue);
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
  public static String getSchemaFile(SchemaFileElement elem)
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
  public static String getSingleValueProperty(SchemaFileElement elem,
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
  public static void setSchemaFile(SchemaFileElement elem, String schemaFile)
  {
    setExtraProperty(elem, SCHEMA_PROPERTY_FILENAME, schemaFile);
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



  /** {@inheritDoc} */
  @Override
  public final Map<String, List<String>> getExtraProperties()
  {
    return extraProperties;
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
  public static void setExtraProperty(SchemaFileElement elem,
      String name, String value)
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

    ifNull(name);

    if (values == null || values.isEmpty())
    {
      extraProperties.remove(name);
    }
    else
    {
      LinkedList<String> valuesCopy = new LinkedList<>(values);
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
  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o instanceof CommonSchemaElements) {
      CommonSchemaElements other = (CommonSchemaElements) o;
      return getNameOrOID().equals(other.getNameOrOID());
    }

    return false;
  }



  /**
   * Retrieves the hash code for this schema definition. It will be
   * based on the sum of the bytes of the OID.
   *
   * @return The hash code for this schema definition.
   */
  @Override
  public final int hashCode() {
    return hashCode;
  }

  /**
   * Retrieves the definition string used to create this attribute
   * type and including the X-SCHEMA-FILE extension.
   *
   * @param elem The element where to get definition from
   * @return  The definition string used to create this attribute
   *          type including the X-SCHEMA-FILE extension.
   */
  public static String getDefinitionWithFileName(SchemaFileElement elem)
  {
    final String schemaFile = getSchemaFile(elem);
    final String definition = elem.toString();
    if (schemaFile != null)
    {
      int pos = definition.lastIndexOf(')');
      return definition.substring(0, pos).trim() + " "
          + SCHEMA_PROPERTY_FILENAME + " '" + schemaFile + "' )";
    }
    return definition;
  }
}
