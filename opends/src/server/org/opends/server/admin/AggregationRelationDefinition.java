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



import static org.opends.server.util.Validator.*;



/**
 * A managed object aggregation relationship definition.
 *
 * @param <C>
 *          The type of client managed object configuration that this
 *          relation definition refers to.
 * @param <S>
 *          The type of server managed object configuration that this
 *          relation definition refers to.
 */
public final class AggregationRelationDefinition
    <C extends ConfigurationClient, S extends Configuration>
    extends RelationDefinition<C, S> {

  /**
   * An interface for incrementally constructing aggregation relation
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
      extends AbstractBuilder<C, S, AggregationRelationDefinition<C, S>> {

    // The maximum number of referenced managed objects.
    private int maxOccurs = 0;

    // The minimum number of referenced managed objects.
    private int minOccurs = 0;

    // The path identifying the location of the referenced managed
    // objects.
    private ManagedObjectPath path;

    // The plural name of the relation.
    private final String pluralName;



    /**
     * Creates a new builder which can be used to incrementally build
     * an aggregation relation definition.
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
    public Builder(AbstractManagedObjectDefinition<?, ?> pd, String name,
        String pluralName, AbstractManagedObjectDefinition<C, S> cd) {
      super(pd, name, cd);
      this.pluralName = pluralName;
    }



    /**
     * Set the maximum number of referenced managed objects.
     *
     * @param maxOccurs
     *          The maximum number of referenced managed objects (or
     *          zero if there is no upper limit).
     */
    public void setMaxOccurs(int maxOccurs) {
      this.maxOccurs = maxOccurs;
    }



    /**
     * Set the minimum number of referenced managed objects.
     *
     * @param minOccurs
     *          The minimum number of referenced managed objects.
     */
    public void setMinOccurs(int minOccurs) {
      this.minOccurs = minOccurs;
    }



    /**
     * Set the path identifying the location of the referenced managed
     * objects.
     *
     * @param path
     *          The path identifying the location of the referenced
     *          managed objects.
     */
    public void setPath(ManagedObjectPath path) {
      this.path = path;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    protected AggregationRelationDefinition<C, S> buildInstance(
        Common<C, S> common) {
      return new AggregationRelationDefinition<C, S>(common, pluralName, path,
          minOccurs, maxOccurs);
    }

  }

  // The maximum number of referenced managed objects.
  private final int maxOccurs;

  // The minimum number of referenced managed objects.
  private final int minOccurs;

  // The path identifying the location of the referenced managed
  // objects.
  private final ManagedObjectPath path;

  // The plural name of the relation.
  private final String pluralName;



  // Private constructor.
  private AggregationRelationDefinition(Common<C, S> common, String pluralName,
      ManagedObjectPath path, int minOccurs, int maxOccurs)
      throws IllegalArgumentException {
    super(common);

    ensureNotNull(path);

    if (minOccurs < 0) {
      throw new IllegalArgumentException("minOccurs is less than zero");
    }

    if (maxOccurs != 0 && maxOccurs < minOccurs) {
      throw new IllegalArgumentException("maxOccurs is less than minOccurs");
    }

    this.pluralName = pluralName;
    this.path = path;
    this.minOccurs = minOccurs;
    this.maxOccurs = maxOccurs;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public <R, P> R accept(RelationDefinitionVisitor<R, P> v, P p) {
    return v.visitAggregation(this, p);
  }



  /**
   * Get the maximum number of referenced managed objects.
   *
   * @return Returns the maximum number of referenced managed objects
   *         (or zero if there is no upper limit).
   */
  public int getMaxOccurs() {
    return maxOccurs;
  }



  /**
   * Get the minimum number of referenced managed objects.
   *
   * @return Returns the minimum number of referenced managed objects.
   */
  public int getMinOccurs() {
    return minOccurs;
  }



  /**
   * Get the path identifying the location of the referenced managed
   * objects.
   *
   * @return Returns the path identifying the location of the
   *         referenced managed objects.
   */
  public ManagedObjectPath getPath() {
    return path;
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
   * {@inheritDoc}
   */
  @Override
  public final void toString(StringBuilder builder) {
    builder.append("name=");
    builder.append(getName());
    builder.append(" type=aggregation parent=");
    builder.append(getParentDefinition().getName());
    builder.append(" child=");
    builder.append(getChildDefinition().getName());
    builder.append(" minOccurs=");
    builder.append(minOccurs);
    if (maxOccurs != 0) {
      builder.append(" maxOccurs=");
      builder.append(maxOccurs);
    }
  }
}
