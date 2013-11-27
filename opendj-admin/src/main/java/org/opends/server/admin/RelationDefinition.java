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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */

package org.opends.server.admin;
import org.opends.messages.Message;



import static org.opends.server.util.Validator.*;

import java.util.EnumSet;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Set;



/**
 * Relation definitions define relationships between types of managed
 * objects. In addition they define the ownership model:
 * <ul>
 * <li>composition - referenced managed objects are owned by the
 * parent managed object and are deleted when the parent is deleted
 * <li>aggregation - referenced managed objects are not owned by the
 * parent managed object. Instead they are shared by other managed
 * objects.
 * </ul>
 * Relations define how clients interact with the configuration. For
 * example, clients manage aggregated managed objects in a shared
 * location and attach them to parent managed objects. Composed
 * managed objects, on the other hand, would be created directly
 * beneath the parent managed object and destroyed with it too.
 * <p>
 * Within the server, listeners can choose to request notification of
 * managed objects being added or removed from relations.
 * <p>
 * In LDAP, compositions are represented as follows:
 * <ul>
 * <li>singleton relations (one to one): a referenced managed object
 * is represented using a child entry directly beneath the parent
 * <li>optional relations (one to zero or one): a referenced managed
 * object is represented using a child entry directly beneath the
 * parent
 * <li>instantiable relations (one to many): the relation is
 * represented using a child entry directly beneath the parent.
 * Referenced managed objects are represented using child entries of
 * this "relation entry" and are named by the user
 * <li>set relations (one to many): the relation is
 * represented using a child entry directly beneath the parent.
 * Referenced managed objects are represented using child entries of
 * this "relation entry" whose name is the type of the managed object.
 * </ul>
 * Whereas, aggregations are represented by storing the DNs of the
 * referenced managed objects in an attribute of the aggregating
 * managed object.
 *
 * @param <C>
 *          The type of client managed object configuration that this
 *          relation definition refers to.
 * @param <S>
 *          The type of server managed object configuration that this
 *          relation definition refers to.
 */
public abstract class RelationDefinition
    <C extends ConfigurationClient, S extends Configuration> {

  /**
   * An interface for incrementally constructing relation definitions.
   *
   * @param <C>
   *          The type of client managed object configuration that
   *          this relation definition refers to.
   * @param <S>
   *          The type of server managed object configuration that
   *          this relation definition refers to.
   * @param <D>
   *          The type of relation definition constructed by this
   *          builder.
   */
  protected abstract static class AbstractBuilder
      <C extends ConfigurationClient, S extends Configuration,
       D extends RelationDefinition<C, S>> {

    // Common fields.
    private final Common<C, S> common;



    /**
     * Create a property definition builder.
     *
     * @param pd
     *          The parent managed object definition.
     * @param name
     *          The name of the relation.
     * @param cd
     *          The child managed object definition.
     */
    protected AbstractBuilder(AbstractManagedObjectDefinition<?, ?> pd,
        String name, AbstractManagedObjectDefinition<C, S> cd) {
      this.common = new Common<C, S>(pd, name, cd);
    }



    /**
     * Construct a relation definition based on the properties of this
     * builder.
     *
     * @return The new relation definition.
     */
    public final D getInstance() {
      return buildInstance(common);
    }



    /**
     * Add a relation definition option.
     *
     * @param option
     *          The relation option.
     */
    public final void setOption(RelationOption option) {
      ensureNotNull(option);
      common.options.add(option);
    }



    /**
     * Build a relation definition based on the properties of this
     * builder.
     *
     * @param common
     *          The common fields of the new relation definition.
     * @return The new relation definition.
     */
    protected abstract D buildInstance(Common<C, S> common);
  }



  /**
   * Opaque structure containing fields common to all relation
   * definition types.
   *
   * @param <C>
   *          The type of client managed object configuration that
   *          this relation definition refers to.
   * @param <S>
   *          The type of server managed object configuration that
   *          this relation definition refers to.
   */
  protected static final class Common
    <C extends ConfigurationClient, S extends Configuration> {

    // The definition of the child managed object.
    private final AbstractManagedObjectDefinition<C, S> cd;

    // The name of the relation.
    private final String name;

    // Options applicable to this definition.
    private final Set<RelationOption> options;

    // The definition of the parent managed object.
    private final AbstractManagedObjectDefinition<?, ?> pd;



    // Private constructor.
    private Common(AbstractManagedObjectDefinition<?, ?> pd, String name,
        AbstractManagedObjectDefinition<C, S> cd) {
      this.name = name;
      this.pd = pd;
      this.cd = cd;
      this.options = EnumSet.noneOf(RelationOption.class);
    }
  }

  // Common fields.
  private final Common<C, S> common;



  /**
   * Create a new managed object relation definition with the
   * specified common fields.
   *
   * @param common
   *          The common fields of the new relation definition.
   */
  protected RelationDefinition(Common<C, S> common) {
    this.common = common;
  }



  /**
   * Apply a visitor to this relation definition.
   *
   * @param <R>
   *          The return type of the visitor's methods.
   * @param <P>
   *          The type of the additional parameters to the visitor's
   *          methods.
   * @param v
   *          The relation definition visitor.
   * @param p
   *          Optional additional visitor parameter.
   * @return Returns a result as specified by the visitor.
   */
  public abstract <R, P> R accept(RelationDefinitionVisitor<R, P> v, P p);



  /**
   * Get the definition of the child managed object.
   *
   * @return Returns the definition of the child managed object.
   */
  public final AbstractManagedObjectDefinition<C, S> getChildDefinition() {
    return common.cd;
  }



  /**
   * Gets the optional description of this relation definition in the
   * default locale.
   *
   * @return Returns the description of this relation definition in
   *         the default locale, or <code>null</code> if there is no
   *         description.
   */
  public final Message getDescription() {
    return getDescription(Locale.getDefault());
  }



  /**
   * Gets the optional description of this relation definition in the
   * specified locale.
   *
   * @param locale
   *          The locale.
   * @return Returns the description of this relation definition in
   *         the specified locale, or <code>null</code> if there is
   *         no description.
   */
  public final Message getDescription(Locale locale) {
    try {
      String property = "relation." + common.name + ".description";
      return ManagedObjectDefinitionI18NResource.getInstance().getMessage(
          getParentDefinition(), property, locale);
    } catch (MissingResourceException e) {
      return null;
    }
  }



  /**
   * Get the name of the relation.
   *
   * @return Returns the name of the relation.
   */
  public final String getName() {
    return common.name;
  }



  /**
   * Get the definition of the parent managed object.
   *
   * @return Returns the definition of the parent managed object.
   */
  public final AbstractManagedObjectDefinition<?, ?> getParentDefinition() {
    return common.pd;
  }



  /**
   * Gets the synopsis of this relation definition in the default
   * locale.
   *
   * @return Returns the synopsis of this relation definition in the
   *         default locale.
   */
  public final Message getSynopsis() {
    return getSynopsis(Locale.getDefault());
  }



  /**
   * Gets the synopsis of this relation definition in the specified
   * locale.
   *
   * @param locale
   *          The locale.
   * @return Returns the synopsis of this relation definition in the
   *         specified locale.
   */
  public final Message getSynopsis(Locale locale) {
    String property = "relation." + common.name + ".synopsis";
    return ManagedObjectDefinitionI18NResource.getInstance().getMessage(
        getParentDefinition(), property, locale);
  }



  /**
   * Gets the user friendly name of this relation definition in the
   * default locale.
   *
   * @return Returns the user friendly name of this relation
   *         definition in the default locale.
   */
  public final Message getUserFriendlyName() {
    return getUserFriendlyName(Locale.getDefault());
  }



  /**
   * Gets the user friendly name of this relation definition in the
   * specified locale.
   *
   * @param locale
   *          The locale.
   * @return Returns the user friendly name of this relation
   *         definition in the specified locale.
   */
  public final Message getUserFriendlyName(Locale locale) {
    String property = "relation." + common.name + ".user-friendly-name";
    return ManagedObjectDefinitionI18NResource.getInstance().getMessage(
        getParentDefinition(), property, locale);
  }



  /**
   * Check if the specified option is set for this relation
   * definition.
   *
   * @param option
   *          The option to test.
   * @return Returns <code>true</code> if the option is set, or
   *         <code>false</code> otherwise.
   */
  public final boolean hasOption(RelationOption option) {
    return common.options.contains(option);
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
   * Append a string representation of the managed object relation to
   * the provided string builder.
   *
   * @param builder
   *          The string builder where the string representation
   *          should be appended.
   */
  public abstract void toString(StringBuilder builder);



  /**
   * Performs any run-time initialization required by this relation
   * definition. This may include resolving managed object paths and
   * property names.
   *
   * @throws Exception
   *           If this relation definition could not be initialized.
   */
  protected void initialize() throws Exception {
    // No implementation required.
  }
}
