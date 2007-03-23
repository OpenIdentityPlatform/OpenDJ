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
 * this "relation entry".
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

  // The name of the relation.
  private final String name;

  // The definition of the parent managed object.
  private final AbstractManagedObjectDefinition<?, ?> pd;

  // The definition of the child managed object.
  private final AbstractManagedObjectDefinition<C, S> cd;



  /**
   * Create a new managed object relation definition with the specified name and
   * referenced managed object definition.
   *
   * @param pd
   *          The parent managed object definition.
   * @param name
   *          The name of the relation.
   * @param cd
   *          The child managed object definition.
   */
  protected RelationDefinition(AbstractManagedObjectDefinition<?, ?> pd,
      String name, AbstractManagedObjectDefinition<C, S> cd) {
    this.name = name;
    this.pd = pd;
    this.cd = cd;
  }



  /**
   * Get the name of the relation.
   *
   * @return Returns the name of the relation.
   */
  public final String getName() {
    return name;
  }



  /**
   * Get the definition of the parent managed object.
   *
   * @return Returns the definition of the parent managed object.
   */
  public final AbstractManagedObjectDefinition<?, ?> getParentDefinition() {
    return pd;
  }



  /**
   * Get the definition of the child managed object.
   *
   * @return Returns the definition of the child managed object.
   */
  public final AbstractManagedObjectDefinition<C, S> getChildDefinition() {
    return cd;
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
   * Append a string representation of the managed object relation to the
   * provided string builder.
   *
   * @param builder
   *          The string builder where the string representation should be
   *          appended.
   */
  public abstract void toString(StringBuilder builder);



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
  public abstract <R, P> R accept(RelationDefinitionVisitor<R, P> v,
      P p);
}
