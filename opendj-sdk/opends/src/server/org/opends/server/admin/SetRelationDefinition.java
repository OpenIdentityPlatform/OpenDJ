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

package org.opends.server.admin;



import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.opends.messages.Message;



/**
 * A managed object composite relationship definition which represents a
 * composition of zero or more managed objects each of which must have a
 * different type. The manage objects are named using their type name.
 *
 * @param <C>
 *          The type of client managed object configuration that this
 *          relation definition refers to.
 * @param <S>
 *          The type of server managed object configuration that this
 *          relation definition refers to.
 */
public final class SetRelationDefinition
    <C extends ConfigurationClient, S extends Configuration>
    extends RelationDefinition<C, S>
{

  /**
   * An interface for incrementally constructing set relation
   * definitions.
   *
   * @param <C>
   *          The type of client managed object configuration that this
   *          relation definition refers to.
   * @param <S>
   *          The type of server managed object configuration that this
   *          relation definition refers to.
   */
  public static final class Builder
      <C extends ConfigurationClient, S extends Configuration>
      extends AbstractBuilder<C, S, SetRelationDefinition<C, S>>
  {

    // The plural name of the relation.
    private final String pluralName;

    // The optional default managed objects associated with this
    // set relation definition.
    private final Map<String,
                      DefaultManagedObject<? extends C, ? extends S>>
      defaultManagedObjects =
        new HashMap<String, DefaultManagedObject<? extends C, ? extends S>>();



    /**
     * Creates a new builder which can be used to incrementally build a
     * set relation definition.
     *
     * @param pd
     *          The parent managed object definition.
     * @param name
     *          The name of the relation.
     * @param pluralName
     *          The plural name of the relation.
     * @param cd
     *          The child managed object definition.
     */
    public Builder(AbstractManagedObjectDefinition<?, ?> pd,
        String name, String pluralName,
        AbstractManagedObjectDefinition<C, S> cd)
    {
      super(pd, name, cd);
      this.pluralName = pluralName;
    }



    /**
     * Adds the default managed object to this set relation definition.
     *
     * @param defaultManagedObject
     *          The default managed object.
     */
    public void setDefaultManagedObject(
        DefaultManagedObject<? extends C, ? extends S> defaultManagedObject)
    {
      this.defaultManagedObjects
          .put(defaultManagedObject.getManagedObjectDefinition()
              .getName(), defaultManagedObject);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    protected SetRelationDefinition<C, S> buildInstance(
        Common<C, S> common)
    {
      return new SetRelationDefinition<C, S>(common, pluralName,
          defaultManagedObjects);
    }

  }



  // The plural name of the relation.
  private final String pluralName;

  // The optional default managed objects associated with this
  // set relation definition.
  private final Map<String,
                    DefaultManagedObject<? extends C, ? extends S>>
    defaultManagedObjects;



  // Private constructor.
  private SetRelationDefinition(
      Common<C, S> common,
      String pluralName,
      Map<String,
          DefaultManagedObject<? extends C, ? extends S>> defaultManagedObjects)
  {
    super(common);
    this.pluralName = pluralName;
    this.defaultManagedObjects = defaultManagedObjects;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public <R, P> R accept(RelationDefinitionVisitor<R, P> v, P p)
  {
    return v.visitSet(this, p);
  }



  /**
   * Gets the named default managed object associated with this set
   * relation definition.
   *
   * @param name
   *          The name of the default managed object (for set relations
   *          this is the type of the default managed object).
   * @return The named default managed object.
   * @throws IllegalArgumentException
   *           If there is no default managed object associated with the
   *           provided name.
   */
  public DefaultManagedObject<? extends C, ? extends S> getDefaultManagedObject(
      String name) throws IllegalArgumentException
  {
    if (!defaultManagedObjects.containsKey(name))
    {
      throw new IllegalArgumentException(
          "unrecognized default managed object \"" + name + "\"");
    }
    return defaultManagedObjects.get(name);
  }



  /**
   * Gets the names of the default managed objects associated with this
   * set relation definition.
   *
   * @return An unmodifiable set containing the names of the default
   *         managed object.
   */
  public Set<String> getDefaultManagedObjectNames()
  {
    return Collections.unmodifiableSet(defaultManagedObjects.keySet());
  }



  /**
   * Gets the plural name of the relation.
   *
   * @return The plural name of the relation.
   */
  public String getPluralName()
  {
    return pluralName;
  }



  /**
   * Gets the user friendly plural name of this relation definition in
   * the default locale.
   *
   * @return Returns the user friendly plural name of this relation
   *         definition in the default locale.
   */
  public Message getUserFriendlyPluralName()
  {
    return getUserFriendlyPluralName(Locale.getDefault());
  }



  /**
   * Gets the user friendly plural name of this relation definition in
   * the specified locale.
   *
   * @param locale
   *          The locale.
   * @return Returns the user friendly plural name of this relation
   *         definition in the specified locale.
   */
  public Message getUserFriendlyPluralName(Locale locale)
  {
    String property =
        "relation." + getName() + ".user-friendly-plural-name";
    return ManagedObjectDefinitionI18NResource.getInstance()
        .getMessage(getParentDefinition(), property, locale);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void toString(StringBuilder builder)
  {
    builder.append("name=");
    builder.append(getName());
    builder.append(" type=set parent=");
    builder.append(getParentDefinition().getName());
    builder.append(" child=");
    builder.append(getChildDefinition().getName());
  }



  /**
   * {@inheritDoc}
   */
  @Override
  protected void initialize() throws Exception
  {
    for (DefaultManagedObject<?, ?> dmo : defaultManagedObjects
        .values())
    {
      dmo.initialize();
    }
  }
}
