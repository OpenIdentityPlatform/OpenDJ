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



import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

import org.opends.server.admin.DefinitionDecodingException.Reason;



/**
 * Defines the structure of an abstract managed object. Abstract managed objects
 * cannot be instantiated.
 * <p>
 * Applications can query a managed object definition in order to determine the
 * overall configuration model of an application.
 *
 * @param <C>
 *          The type of client managed object configuration that this definition
 *          represents.
 * @param <S>
 *          The type of server managed object configuration that this definition
 *          represents.
 */
public abstract class AbstractManagedObjectDefinition
    <C extends ConfigurationClient, S extends Configuration> {

  // The name of the definition.
  private final String name;

  // The parent managed object definition if applicable.
  private final AbstractManagedObjectDefinition<? super C, ? super S> parent;

  // The set of property definitions applicable to this managed object
  // definition.
  private final Map<String, PropertyDefinition<?>> propertyDefinitions;

  // The set of relation definitions applicable to this managed object
  // definition.
  private final Map<String, RelationDefinition<?, ?>> relationDefinitions;

  // The set of managed object definitions which inherit from this definition.
  private final Map<String,
    AbstractManagedObjectDefinition<? extends C, ? extends S>> children;



  /**
   * Create a new abstract managed object definition.
   *
   * @param name
   *          The name of the definition.
   * @param parent
   *          The parent definition, or <code>null</code> if there is no
   *          parent.
   */
  protected AbstractManagedObjectDefinition(String name,
      AbstractManagedObjectDefinition<? super C, ? super S> parent) {
    this.name = name;
    this.parent = parent;
    this.propertyDefinitions = new HashMap<String, PropertyDefinition<?>>();
    this.relationDefinitions = new HashMap<String, RelationDefinition<?,?>>();
    this.children = new HashMap<String,
      AbstractManagedObjectDefinition<? extends C, ? extends S>>();

    // If we have a parent definition then inherit its features.
    if (parent != null) {
      parent.children.put(name, this);
    }
  }



  /**
   * Get all the child managed object definitions which inherit from
   * this managed object definition.
   *
   * @return Returns an unmodifiable collection containing all the
   *         subordinate managed object definitions which inherit from
   *         this managed object definition.
   */
  public final Collection<AbstractManagedObjectDefinition
      <? extends C, ? extends S>> getAllChildren() {
    List<AbstractManagedObjectDefinition<? extends C, ? extends S>> list =
      new ArrayList<AbstractManagedObjectDefinition<? extends C, ? extends S>>(
        children.values());

    for (AbstractManagedObjectDefinition<? extends C, ? extends S> child :
        children.values()) {
      list.addAll(child.getAllChildren());
    }

    return Collections.unmodifiableCollection(list);
  }



  /**
   * Get all the property definitions associated with this type of
   * managed object. The returned collection will contain inherited
   * property definitions.
   *
   * @return Returns an unmodifiable collection containing all the
   *         property definitions associated with this type of managed
   *         object.
   */
  public final Collection<PropertyDefinition<?>> getAllPropertyDefinitions() {
    if (parent == null) {
      return getPropertyDefinitions();
    } else {
      List<PropertyDefinition<?>> list = new ArrayList<PropertyDefinition<?>>(
          propertyDefinitions.values());
      list.addAll(parent.getAllPropertyDefinitions());
      return Collections.unmodifiableCollection(list);
    }
  }



  /**
   * Get all the relation definitions associated with this type of
   * managed object. The returned collection will contain inherited
   * relation definitions.
   *
   * @return Returns an unmodifiable collection containing all the
   *         relation definitions associated with this type of managed
   *         object.
   */
  public final Collection<RelationDefinition<?, ?>>
      getAllRelationDefinitions() {
    if (parent == null) {
      return getRelationDefinitions();
    } else {
      List<RelationDefinition<?, ?>> list =
        new ArrayList<RelationDefinition<?, ?>>(relationDefinitions.values());
      list.addAll(parent.getAllRelationDefinitions());
      return Collections.unmodifiableCollection(list);
    }
  }



  /**
   * Get the named child managed object definition which inherits from
   * this managed object definition. This method will recursively
   * search down through the inheritance hierarchy.
   *
   * @param name
   *          The name of the managed object definition sub-type.
   * @return Returns the named child managed object definition which
   *         inherits from this managed object definition.
   * @throws IllegalArgumentException
   *           If the specified managed object definition name was
   *           null or empty or if the requested subordinate managed
   *           object definition was not found.
   */
  public final AbstractManagedObjectDefinition<? extends C, ? extends S>
      getChild(String name) throws IllegalArgumentException {
    if ((name == null) || (name.length() == 0)) {
      throw new IllegalArgumentException("null or empty managed object name");
    }

    AbstractManagedObjectDefinition<? extends C, ? extends S> d = children
        .get(name);

    if (d == null) {
      // Recursively search.
      for (AbstractManagedObjectDefinition<? extends C, ? extends S> child :
          children.values()) {
        try {
          d = child.getChild(name);
          break;
        } catch (IllegalArgumentException e) {
          // Try the next child.
        }
      }
    }

    if (d == null) {
      throw new IllegalArgumentException("child managed object definition \""
          + name + "\" not found");
    }

    return d;
  }



  /**
   * Get the child managed object definitions which inherit directly
   * from this managed object definition.
   *
   * @return Returns an unmodifiable collection containing the
   *         subordinate managed object definitions which inherit
   *         directly from this managed object definition.
   */
  public final Collection<AbstractManagedObjectDefinition
      <? extends C, ? extends S>> getChildren() {
    return Collections.unmodifiableCollection(children.values());
  }



  /**
   * Gets the optional description of this managed object definition
   * in the default locale.
   *
   * @return Returns the description of this managed object definition
   *         in the default locale, or <code>null</code> if there is
   *         no description.
   */
  public final String getDescription() {
    return getDescription(Locale.getDefault());
  }



  /**
   * Gets the optional description of this managed object definition
   * in the specified locale.
   *
   * @param locale
   *          The locale.
   * @return Returns the description of this managed object definition
   *         in the specified locale, or <code>null</code> if there
   *         is no description.
   */
  public final String getDescription(Locale locale) {
    try {
      return ManagedObjectDefinitionI18NResource.getInstance()
          .getMessage(this, "description", locale);
    } catch (MissingResourceException e) {
      return null;
    }
  }



  /**
   * Get the name of the definition.
   *
   * @return Returns the name of the definition.
   */
  public final String getName() {
    return name;
  }



  /**
   * Get the parent managed object definition, if applicable.
   *
   * @return Returns the parent of this managed object definition, or
   *         <code>null</code> if this definition does not have a
   *         parent.
   */
  public final AbstractManagedObjectDefinition<? super C,
      ? super S> getParent() {
    return parent;
  }



  /**
   * Get the specified property definition associated with this type
   * of managed object. The search will include any inherited property
   * definitions.
   *
   * @param name
   *          The name of the property definition to be retrieved.
   * @return Returns the specified property definition associated with
   *         this type of managed object.
   * @throws IllegalArgumentException
   *           If the specified property name was null or empty or if
   *           the requested property definition was not found.
   */
  public final PropertyDefinition getPropertyDefinition(String name)
      throws IllegalArgumentException {
    if ((name == null) || (name.length() == 0)) {
      throw new IllegalArgumentException("null or empty property name");
    }

    PropertyDefinition d = propertyDefinitions.get(name);

    if (d == null) {
      if (parent != null) {
        return parent.getPropertyDefinition(name);
      } else {
        throw new IllegalArgumentException("property definition \"" + name
            + "\" not found");
      }
    }

    return d;
  }



  /**
   * Get the property definitions defined by this managed object
   * definition. The returned collection will not contain inherited
   * property definitions.
   *
   * @return Returns an unmodifiable collection containing the
   *         property definitions defined by this managed object
   *         definition.
   */
  public final Collection<PropertyDefinition<?>> getPropertyDefinitions() {
    return Collections.unmodifiableCollection(propertyDefinitions
        .values());
  }



  /**
   * Get the specified relation definition associated with this type
   * of managed object.The search will include any inherited relation
   * definitions.
   *
   * @param name
   *          The name of the relation definition to be retrieved.
   * @return Returns the specified relation definition associated with
   *         this type of managed object.
   * @throws IllegalArgumentException
   *           If the specified relation name was null or empty or if
   *           the requested relation definition was not found.
   */
  public final RelationDefinition getRelationDefinition(String name)
      throws IllegalArgumentException {
    if ((name == null) || (name.length() == 0)) {
      throw new IllegalArgumentException("null or empty relation name");
    }

    RelationDefinition d = relationDefinitions.get(name);

    if (d == null) {
      if (parent != null) {
        return parent.getRelationDefinition(name);
      } else {
        throw new IllegalArgumentException("relation definition \"" + name
            + "\" not found");
      }
    }

    return d;
  }



  /**
   * Get the relation definitions defined by this managed object
   * definition. The returned collection will not contain inherited
   * relation definitions.
   *
   * @return Returns an unmodifiable collection containing the
   *         relation definitions defined by this managed object
   *         definition.
   */
  public final Collection<RelationDefinition<?,?>> getRelationDefinitions() {
    return Collections.unmodifiableCollection(relationDefinitions.values());
  }



  /**
   * Gets the synopsis of this managed object definition in the
   * default locale.
   *
   * @return Returns the synopsis of this managed object definition in
   *         the default locale.
   */
  public final String getSynopsis() {
    return getSynopsis(Locale.getDefault());
  }



  /**
   * Gets the synopsis of this managed object definition in the
   * specified locale.
   *
   * @param locale
   *          The locale.
   * @return Returns the synopsis of this managed object definition in
   *         the specified locale.
   */
  public final String getSynopsis(Locale locale) {
    return ManagedObjectDefinitionI18NResource.getInstance()
        .getMessage(this, "synopsis", locale);
  }



  /**
   * Gets the user friendly name of this managed object definition in
   * the default locale.
   *
   * @return Returns the user friendly name of this managed object
   *         definition in the default locale.
   */
  public final String getUserFriendlyName() {
    return getUserFriendlyName(Locale.getDefault());
  }



  /**
   * Gets the user friendly name of this managed object definition in
   * the specified locale.
   *
   * @param locale
   *          The locale.
   * @return Returns the user friendly name of this managed object
   *         definition in the specified locale.
   */
  public final String getUserFriendlyName(Locale locale) {
    return ManagedObjectDefinitionI18NResource.getInstance()
        .getMessage(this, "user-friendly-name", locale);
  }



  /**
   * Gets the user friendly plural name of this managed object
   * definition in the default locale.
   *
   * @return Returns the user friendly plural name of this managed
   *         object definition in the default locale.
   */
  public final String getUserFriendlyPluralName() {
    return getUserFriendlyPluralName(Locale.getDefault());
  }



  /**
   * Gets the user friendly plural name of this managed object
   * definition in the specified locale.
   *
   * @param locale
   *          The locale.
   * @return Returns the user friendly plural name of this managed
   *         object definition in the specified locale.
   */
  public final String getUserFriendlyPluralName(Locale locale) {
    return ManagedObjectDefinitionI18NResource.getInstance()
        .getMessage(this, "user-friendly-plural-name", locale);
  }



  /**
   * Determine whether there are any child managed object definitions which
   * inherit from this managed object definition.
   *
   * @return Returns <code>true</code> if this type of managed object has any
   *         child managed object definitions, <code>false</code> otherwise.
   */
  public final boolean hasChildren() {
    return !children.isEmpty();
  }



  /**
   * Determine whether this type of managed object has any property definitions.
   *
   * @return Returns <code>true</code> if this type of managed object has any
   *         property definitions, <code>false</code> otherwise.
   */
  public final boolean hasPropertyDefinitions() {
    return !propertyDefinitions.isEmpty();
  }



  /**
   * Determine whether this type of managed object has any relation definitions.
   *
   * @return Returns <code>true</code> if this type of managed object has any
   *         relation definitions, <code>false</code> otherwise.
   */
  public final boolean hasRelationDefinitions() {
    return !relationDefinitions.isEmpty();
  }



  /**
   * Determines whether or not this managed object definition is a
   * sub-type of the provided managed object definition. This managed
   * object definition is a sub-type of the provided managed object
   * definition if they are both the same or if the provided managed
   * object definition can be obtained by recursive invocations of the
   * {@link #getParent()} method.
   *
   * @param d
   *          The managed object definition to be checked.
   * @return Returns <code>true</code> if this managed object
   *         definition is a sub-type of the provided managed object
   *         definition.
   */
  public final boolean isChildOf(AbstractManagedObjectDefinition<?, ?> d) {
    AbstractManagedObjectDefinition<?, ?> i;
    for (i = this; i != null; i = i.parent) {
      if (i == d) {
        return true;
      }
    }
    return false;
  }



  /**
   * Determines whether or not this managed object definition is a
   * super-type of the provided managed object definition. This
   * managed object definition is a super-type of the provided managed
   * object definition if they are both the same or if the provided
   * managed object definition is a member of the set of children
   * returned from {@link #getAllChildren()}.
   *
   * @param d
   *          The managed object definition to be checked.
   * @return Returns <code>true</code> if this managed object
   *         definition is a super-type of the provided managed object
   *         definition.
   */
  public final boolean isParentOf(AbstractManagedObjectDefinition<?, ?> d) {
    return d.isChildOf(this);
  }



  /**
   * Register a property definition with the managed object definition,
   * overriding any existing property definition with the same name.
   * <p>
   * This method <b>must not</b> be called by applications.
   *
   * @param d
   *          The property definition to be registered.
   */
  protected final void registerPropertyDefinition(PropertyDefinition d) {
    String name = d.getName();

    propertyDefinitions.put(name, d);
  }



  /**
   * Register a relation definition with the managed object definition,
   * overriding any existing relation definition with the same name.
   * <p>
   * This method <b>must not</b> be called by applications.
   *
   * @param d
   *          The relation definition to be registered.
   */
  protected final void registerRelationDefinition(RelationDefinition d) {
    String name = d.getName();

    relationDefinitions.put(name, d);
  }



  /**
   * Finds a sub-type of this managed object definition which most closely
   * corresponds to the matching criteria of the provided definition resolver.
   *
   * @param r
   *          The definition resolver.
   * @return Returns the sub-type of this managed object definition which most
   *         closely corresponds to the matching criteria of the provided
   *         definition resolver.
   * @throws DefinitionDecodingException
   *           If no matching sub-type could be found or if the resolved
   *           definition was abstract.
   * @see DefinitionResolver
   */
  @SuppressWarnings("unchecked")
  public final ManagedObjectDefinition<? extends C, ? extends S>
      resolveManagedObjectDefinition(
      DefinitionResolver r) throws DefinitionDecodingException {
    AbstractManagedObjectDefinition<? extends C, ? extends S> rd;
    rd = resolveManagedObjectDefinitionAux(this, r);
    if (rd == null) {
      // Unable to resolve the definition.
      throw new DefinitionDecodingException(Reason.WRONG_TYPE_INFORMATION);
    } else if (rd instanceof ManagedObjectDefinition) {
      return (ManagedObjectDefinition<? extends C, ? extends S>) rd;
    } else {
      // Resolved definition was abstract.
      throw new DefinitionDecodingException(Reason.ABSTRACT_TYPE_INFORMATION);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final String toString() {
    StringBuilder builder = new StringBuilder();
    toString(builder);
    return builder.toString();
  }



  /**
   * Append a string representation of the managed object definition to the
   * provided string builder.
   *
   * @param builder
   *          The string builder where the string representation should be
   *          appended.
   */
  public final void toString(StringBuilder builder) {
    builder.append(getName());
  }



  // Recursively descend definition hierarchy to find the best match definition.
  private AbstractManagedObjectDefinition<? extends C, ? extends S>
      resolveManagedObjectDefinitionAux(
      AbstractManagedObjectDefinition<? extends C, ? extends S> d,
      DefinitionResolver r) {
    if (!r.matches(d)) {
      return null;
    }

    for (AbstractManagedObjectDefinition<? extends C, ? extends S> child : d
        .getChildren()) {
      AbstractManagedObjectDefinition<? extends C, ? extends S> rd =
        resolveManagedObjectDefinitionAux(child, r);
      if (rd != null) {
        return rd;
      }
    }

    return d;
  }
}
