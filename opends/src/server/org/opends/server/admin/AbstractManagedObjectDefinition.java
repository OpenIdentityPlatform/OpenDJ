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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;

import org.opends.messages.Message;
import org.opends.server.admin.DefinitionDecodingException.Reason;
import org.opends.server.util.Validator;



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

  // The set of constraints associated with this managed object
  // definition.
  private final Collection<Constraint> constraints;

  // The set of property definitions applicable to this managed object
  // definition.
  private final Map<String, PropertyDefinition<?>> propertyDefinitions;

  // The set of relation definitions applicable to this managed object
  // definition.
  private final Map<String, RelationDefinition<?, ?>> relationDefinitions;

  // The set of all constraints associated with this managed object
  // definition including inherited constraints.
  private final Collection<Constraint> allConstraints;

  // The set of all property definitions associated with this managed
  // object definition including inherited property definitions.
  private final Map<String, PropertyDefinition<?>> allPropertyDefinitions;

  // The set of all relation definitions associated with this managed
  // object definition including inherited relation definitions.
  private final Map<String, RelationDefinition<?, ?>> allRelationDefinitions;

  // The set of tags associated with this managed object.
  private final Set<Tag> allTags;

  // The set of managed object definitions which inherit from this definition.
  private final Map<String,
    AbstractManagedObjectDefinition<? extends C, ? extends S>> children;



  /**
   * Create a new abstract managed object definition.
   *
   * @param name
   *          The name of the definition.
   * @param parent
   *          The parent definition, or <code>null</code> if there
   *          is no parent (only the {@link TopCfgDefn} should have a
   *          <code>null</code> parent}.
   */
  protected AbstractManagedObjectDefinition(String name,
      AbstractManagedObjectDefinition<? super C, ? super S> parent) {
    // Perform sanity checks.
    if (this.getClass() == TopCfgDefn.class) {
      Validator.ensureTrue(name.equals("top"),
          "TopCfgDefn should have the name 'top'");
      Validator.ensureTrue(parent == null,
          "TopCfgDefn should not have a parent");
    } else {
      Validator.ensureTrue(!name.equals("top"),
          "Only the TopCfgDefn should have the name 'top'");
      Validator.ensureTrue(parent != null, "No parent defined");
    }

    this.name = name;
    this.parent = parent;
    this.constraints = new LinkedList<Constraint>();
    this.propertyDefinitions = new HashMap<String, PropertyDefinition<?>>();
    this.relationDefinitions = new HashMap<String, RelationDefinition<?,?>>();
    this.allConstraints = new LinkedList<Constraint>();
    this.allPropertyDefinitions = new HashMap<String, PropertyDefinition<?>>();
    this.allRelationDefinitions =
      new HashMap<String, RelationDefinition<?, ?>>();
    this.allTags = new HashSet<Tag>();
    this.children = new HashMap<String,
        AbstractManagedObjectDefinition<? extends C, ? extends S>>();

    // If we have a parent definition then inherit its features.
    if (parent != null) {
      parent.children.put(name, this);

      allConstraints.addAll(parent.getAllConstraints());

      for (PropertyDefinition<?> pd : parent.getAllPropertyDefinitions()) {
        allPropertyDefinitions.put(pd.getName(), pd);
      }

      for (RelationDefinition<?, ?> rd : parent.getAllRelationDefinitions()) {
        allRelationDefinitions.put(rd.getName(), rd);
      }

      // Tag inheritance is performed during preprocessing.
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
   * Get all the constraints associated with this type of managed
   * object. The returned collection will contain inherited
   * constraints.
   *
   * @return Returns an unmodifiable collection containing all the
   *         constraints associated with this type of managed object.
   */
  public final Collection<Constraint> getAllConstraints() {
    return Collections.unmodifiableCollection(allConstraints);
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
    return Collections.unmodifiableCollection(allPropertyDefinitions.values());
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
    return Collections.unmodifiableCollection(allRelationDefinitions.values());
  }



  /**
   * Get all the tags associated with this type of managed object. The
   * returned collection will contain inherited tags.
   *
   * @return Returns an unmodifiable collection containing all the
   *         tags associated with this type of managed object.
   */
  public final Collection<Tag> getAllTags() {
    return Collections.unmodifiableCollection(allTags);
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
   * Get the constraints defined by this managed object definition.
   * The returned collection will not contain inherited constraints.
   *
   * @return Returns an unmodifiable collection containing the
   *         constraints defined by this managed object definition.
   */
  public final Collection<Constraint> getConstraints() {
    return Collections.unmodifiableCollection(constraints);
  }



  /**
   * Gets the optional description of this managed object definition
   * in the default locale.
   *
   * @return Returns the description of this managed object definition
   *         in the default locale, or <code>null</code> if there is
   *         no description.
   * @throws UnsupportedOperationException
   *           If this managed object definition is the
   *           {@link TopCfgDefn}.
   */
  public final Message getDescription() throws UnsupportedOperationException {
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
   * @throws UnsupportedOperationException
   *           If this managed object definition is the
   *           {@link TopCfgDefn}.
   */
  public final Message getDescription(Locale locale)
      throws UnsupportedOperationException {
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
   *         <code>null</code> if this definition is the
   *         {@link TopCfgDefn}.
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
  public final PropertyDefinition<?> getPropertyDefinition(String name)
      throws IllegalArgumentException {
    if ((name == null) || (name.length() == 0)) {
      throw new IllegalArgumentException("null or empty property name");
    }

    PropertyDefinition<?> d = allPropertyDefinitions.get(name);
    if (d == null) {
      throw new IllegalArgumentException("property definition \"" + name
          + "\" not found");
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
  public final RelationDefinition<?, ?> getRelationDefinition(String name)
      throws IllegalArgumentException {
    if ((name == null) || (name.length() == 0)) {
      throw new IllegalArgumentException("null or empty relation name");
    }

    RelationDefinition<?, ?> d = allRelationDefinitions.get(name);
    if (d == null) {
      throw new IllegalArgumentException("relation definition \"" + name
          + "\" not found");
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
   * @throws UnsupportedOperationException
   *           If this managed object definition is the
   *           {@link TopCfgDefn}.
   */
  public final Message getSynopsis() throws UnsupportedOperationException {
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
   * @throws UnsupportedOperationException
   *           If this managed object definition is the
   *           {@link TopCfgDefn}.
   */
  public final Message getSynopsis(Locale locale)
      throws UnsupportedOperationException {
    return ManagedObjectDefinitionI18NResource.getInstance()
        .getMessage(this, "synopsis", locale);
  }



  /**
   * Gets the user friendly name of this managed object definition in
   * the default locale.
   *
   * @return Returns the user friendly name of this managed object
   *         definition in the default locale.
   * @throws UnsupportedOperationException
   *           If this managed object definition is the
   *           {@link TopCfgDefn}.
   */
  public final Message getUserFriendlyName()
      throws UnsupportedOperationException {
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
   * @throws UnsupportedOperationException
   *           If this managed object definition is the
   *           {@link TopCfgDefn}.
   */
  public final Message getUserFriendlyName(Locale locale)
      throws UnsupportedOperationException {
    // TODO: have admin framework getMessage return a Message
    return Message.raw(ManagedObjectDefinitionI18NResource.getInstance()
        .getMessage(this, "user-friendly-name", locale));
  }



  /**
   * Gets the user friendly plural name of this managed object
   * definition in the default locale.
   *
   * @return Returns the user friendly plural name of this managed
   *         object definition in the default locale.
   * @throws UnsupportedOperationException
   *           If this managed object definition is the
   *           {@link TopCfgDefn}.
   */
  public final Message getUserFriendlyPluralName()
      throws UnsupportedOperationException {
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
   * @throws UnsupportedOperationException
   *           If this managed object definition is the
   *           {@link TopCfgDefn}.
   */
  public final Message getUserFriendlyPluralName(Locale locale)
      throws UnsupportedOperationException {
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
   * Determines whether or not this managed object definition has the
   * specified tag.
   *
   * @param t
   *          The tag definition.
   * @return Returns <code>true</code> if this managed object
   *         definition has the specified tag.
   */
  public final boolean hasTag(Tag t) {
    return allTags.contains(t);
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
   * Determines whether or not this managed object definition is the
   * {@link TopCfgDefn}.
   *
   * @return Returns <code>true</code> if this managed object
   *         definition is the {@link TopCfgDefn}.
   */
  public final boolean isTop() {
    return (this == TopCfgDefn.getInstance());
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
      throw new DefinitionDecodingException(this,
          Reason.WRONG_TYPE_INFORMATION);
    } else if (rd instanceof ManagedObjectDefinition) {
      return (ManagedObjectDefinition<? extends C, ? extends S>) rd;
    } else {
      // Resolved definition was abstract.
      throw new DefinitionDecodingException(this,
          Reason.ABSTRACT_TYPE_INFORMATION);
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



  /**
   * Deregister a constraint from the managed object definition.
   * <p>
   * This method <b>must not</b> be called by applications and is
   * only intended for internal testing.
   *
   * @param constraint
   *          The constraint to be deregistered.
   */
  protected final void deregisterConstraint(Constraint constraint) {
    constraints.remove(constraint);
    allConstraints.remove(constraint);
  }



  /**
   * Deregister a relation definition from the managed object
   * definition.
   * <p>
   * This method <b>must not</b> be called by applications and is
   * only intended for internal testing.
   *
   * @param d
   *          The relation definition to be deregistered.
   */
  protected final void deregisterRelationDefinition(
      RelationDefinition<?, ?> d) {
    String name = d.getName();

    relationDefinitions.remove(name);
    allRelationDefinitions.remove(name);
  }



  /**
   * Initializes all of the property definitions associated with this
   * managed object definition.
   *
   * @throws Exception
   *           If this managed object definition could not be
   *           initialized.
   */
  protected final void initialize() throws Exception {
    for (PropertyDefinition<?> pd : getAllPropertyDefinitions()) {
      pd.initialize();
      pd.getDefaultBehaviorProvider().initialize();
    }
  }



  /**
   * Register a constraint with the managed object definition.
   * <p>
   * This method <b>must not</b> be called by applications.
   *
   * @param constraint
   *          The constraint to be registered.
   */
  protected final void registerConstraint(Constraint constraint) {
    constraints.add(constraint);
    allConstraints.add(constraint);
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
  protected final void registerPropertyDefinition(PropertyDefinition<?> d) {
    String name = d.getName();

    propertyDefinitions.put(name, d);
    allPropertyDefinitions.put(name, d);
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
  protected final void registerRelationDefinition(RelationDefinition<?, ?> d) {
    String name = d.getName();

    relationDefinitions.put(name, d);
    allRelationDefinitions.put(name, d);
  }



  /**
   * Register a tag with the managed object definition.
   * <p>
   * This method <b>must not</b> be called by applications.
   *
   * @param t
   *          The tag to be registered.
   */
  protected final void registerTag(Tag t) {
    allTags.add(t);
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
