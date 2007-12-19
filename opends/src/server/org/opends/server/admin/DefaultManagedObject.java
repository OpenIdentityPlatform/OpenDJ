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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.admin;



import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;



/**
 * A default managed object which should be created when a parent
 * managed object is created. Default managed objects are associated
 * with a {@link RelationDefinition}.
 *
 * @param <C>
 *          The type of client default managed object configuration.
 * @param <S>
 *          The type of server default managed object configuration.
 */
public final class DefaultManagedObject
    <C extends ConfigurationClient, S extends Configuration>
    implements PropertyProvider {

  /**
   * An interface for incrementally constructing default managed
   * objects.
   *
   * @param <C>
   *          The type of client default managed object configuration.
   * @param <S>
   *          The type of server default managed object configuration.
   */
  public static final class Builder
      <C extends ConfigurationClient, S extends Configuration> {

    // The default managed object's definition.
    private final ManagedObjectDefinition<C, S> definition;

    // The string encoded default managed object's properties.
    private final Map<String, List<String>> propertyStringValues =
      new HashMap<String, List<String>>();



    /**
     * Creates a new default managed object builder.
     *
     * @param definition
     *          The default managed object's definition.
     */
    public Builder(ManagedObjectDefinition<C, S> definition) {
      this.definition = definition;
    }



    /**
     * Construct a default managed object based on the properties of
     * this builder.
     *
     * @return Returns the new default managed object.
     */
    public DefaultManagedObject<C, S> getInstance() {
      return new DefaultManagedObject<C, S>(definition, propertyStringValues);
    }



    /**
     * Defines a property's values for the default managed object.
     *
     * @param name
     *          The name of the property.
     * @param values
     *          One or more property values in the string
     *          representation.
     */
    public void setPropertyValues(String name, String... values) {
      if (values == null || values.length == 0) {
        throw new IllegalArgumentException(
            "null or empty values specified for property " + name);
      }

      propertyStringValues.put(name, Arrays.asList(values));
    }
  }

  // The default managed object's definition.
  private final ManagedObjectDefinition<C, S> definition;

  // The string encoded default managed object's properties.
  private final Map<String, List<String>> propertyStringValues;



  // Private constructor.
  private DefaultManagedObject(ManagedObjectDefinition<C, S> definition,
      Map<String, List<String>> propertyStringValues) {
    this.definition = definition;
    this.propertyStringValues = propertyStringValues;
  }



  /**
   * Gets the managed object definition associated with this default
   * managed object.
   *
   * @return Returns the managed object definition associated with
   *         this default managed object.
   */
  public ManagedObjectDefinition<C, S> getManagedObjectDefinition() {
    return definition;
  }



  /**
   * Gets a mutable copy of the set of property values for the
   * specified property.
   *
   * @param <T>
   *          The type of the property to be retrieved.
   * @param pd
   *          The property to be retrieved.
   * @return Returns a newly allocated set containing a copy of the
   *         property's values. An empty set indicates that the
   *         property has no values defined and any default behavior
   *         is applicable.
   * @throws IllegalArgumentException
   *           If the property definition is not associated with this
   *           managed object's definition.
   */
  public <T> SortedSet<T> getPropertyValues(PropertyDefinition<T> pd)
      throws IllegalArgumentException {
    // Validate the property definition.
    definition.getPropertyDefinition(pd.getName());

    // Do a defensive copy.
    SortedSet<T> values = new TreeSet<T>(pd);
    List<String> stringValues = propertyStringValues.get(pd.getName());
    if (stringValues != null) {
      for (String stringValue : stringValues) {
        values.add(pd.decodeValue(stringValue));
      }
    }
    return values;
  }



  /**
   * Performs run-time initialization of properties.
   *
   * @throws Exception
   *           If this default managed object could not be
   *           initialized.
   */
  void initialize() throws Exception {
    // FIXME: it would be nice if we could decode all property values
    // at this point. However this is not possible on the server side
    // since some properties will be determined to be invalid since
    // the schema is not loaded.

    // Validate provided property names.
    for (String name : propertyStringValues.keySet()) {
      definition.getPropertyDefinition(name);
    }
  }
}
