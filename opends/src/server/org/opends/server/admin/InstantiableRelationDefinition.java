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

import java.util.Locale;



/**
 * A managed object composite relationship definition which represents
 * a composition of zero or more managed objects.
 *
 * @param <C>
 *          The type of client managed object configuration that this
 *          relation definition refers to.
 * @param <S>
 *          The type of server managed object configuration that this
 *          relation definition refers to.
 */
public final class InstantiableRelationDefinition
    <C extends ConfigurationClient, S extends Configuration>
    extends RelationDefinition<C, S> {

  // The plural name of the relation.
  private final String pluralName;



  /**
   * Create a new instantiable managed object relation definition.
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
  public InstantiableRelationDefinition(
      AbstractManagedObjectDefinition<?, ?> pd, String name, String pluralName,
      AbstractManagedObjectDefinition<C, S> cd) {
    super(pd, name, cd);
    this.pluralName = pluralName;
  }



  /**
   * Get the plural name of the relation.
   *
   * @return Returns the plural name of the relation.
   */
  public final String getPluralName() {
    return pluralName;
  }



  /**
   * Gets the user friendly plural name of this relation definition in
   * the default locale.
   *
   * @return Returns the user friendly plural name of this relation
   *         definition in the default locale.
   */
  public final String getUserFriendlyPluralName() {
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
  public final String getUserFriendlyPluralName(Locale locale) {
    String property = "relation." + getName()
        + ".user-friendly-plural-name";
    return ManagedObjectDefinitionI18NResource.getInstance()
        .getMessage(getParentDefinition(), property, locale);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final void toString(StringBuilder builder) {
    builder.append("name=");
    builder.append(getName());
    builder.append(" type=composition parent=");
    builder.append(getParentDefinition().getName());
    builder.append(" child=");
    builder.append(getChildDefinition().getName());
    builder.append(" minOccurs=0");
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public <R, P> R accept(RelationDefinitionVisitor<R, P> v, P p) {
    return v.visitInstantiable(this, p);
  }
}
