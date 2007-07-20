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



/**
 * A managed object composite relationship definition which represents
 * a composition of an optional single managed object (i.e. the
 * referenced managed object may or may not be present).
 *
 * @param <C>
 *          The type of client managed object configuration that this
 *          relation definition refers to.
 * @param <S>
 *          The type of server managed object configuration that this
 *          relation definition refers to.
 */
public final class OptionalRelationDefinition
    <C extends ConfigurationClient, S extends Configuration>
    extends RelationDefinition<C, S> {

  /**
   * An interface for incrementally constructing optional relation
   * definitions.
   *
   * @param <C>
   *          The type of client managed object configuration that
   *          this relation definition refers to.
   * @param <S>
   *          The type of server managed object configuration that
   *          this relation definition refers to.
   */
  public static class Builder
      <C extends ConfigurationClient, S extends Configuration>
      extends AbstractBuilder<C, S, OptionalRelationDefinition<C, S>> {

    /**
     * Creates a new builder which can be used to incrementally build
     * an optional relation definition.
     *
     * @param pd
     *          The parent managed object definition.
     * @param name
     *          The name of the relation.
     * @param cd
     *          The child managed object definition.
     */
    public Builder(AbstractManagedObjectDefinition<?, ?> pd, String name,
        AbstractManagedObjectDefinition<C, S> cd) {
      super(pd, name, cd);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    protected OptionalRelationDefinition<C, S> buildInstance(
        Common<C, S> common) {
      return new OptionalRelationDefinition<C, S>(common);
    }

  }



  // Private constructor.
  private OptionalRelationDefinition(Common<C, S> common) {
    super(common);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public <R, P> R accept(RelationDefinitionVisitor<R, P> v, P p) {
    return v.visitOptional(this, p);
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
    builder.append(" minOccurs=0 maxOccurs=1");
  }

}
