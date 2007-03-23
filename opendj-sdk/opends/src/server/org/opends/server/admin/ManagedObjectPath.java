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



import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.admin.std.meta.RootCfgDefn;



/**
 * A path which can be used to determine the location of a managed
 * object instance.
 */
public final class ManagedObjectPath {

  /**
   * Abstract path element.
   */
  private static abstract class Element {

    /**
     * Protected constructor.
     */
    protected Element() {
      // No implementation required.
    }



    /**
     * Get the relation definition associated with this element.
     *
     * @return Returns the relation definition associated with this
     *         element.
     */
    public abstract RelationDefinition<?, ?> getRelation();



    /**
     * Serialize this path element using the provided serialization
     * strategy.
     *
     * @param serializer
     *          The managed object path serialization strategy.
     */
    public abstract void serialize(
        ManagedObjectPathSerializer serializer);
  }



  /**
   * A path element representing an instantiable managed object.
   */
  private static final class InstantiableElement extends Element {

    // The instantiable relation.
    private final InstantiableRelationDefinition<?, ?> r;

    // The name of the managed object.
    private final String name;



    // Private constructor.
    private InstantiableElement(
        InstantiableRelationDefinition<?, ?> r, String name) {
      this.r = r;
      this.name = name;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public RelationDefinition<?, ?> getRelation() {
      return r;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(ManagedObjectPathSerializer serializer) {
      serializer.appendManagedObjectPathElement(r, name);
    }

  }



  /**
   * A path element representing an optional managed object.
   */
  private static final class OptionalElement extends Element {

    // The optional relation.
    private final OptionalRelationDefinition<?, ?> r;



    // Private constructor.
    private OptionalElement(OptionalRelationDefinition<?, ?> r) {
      this.r = r;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public RelationDefinition<?, ?> getRelation() {
      return r;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(ManagedObjectPathSerializer serializer) {
      serializer.appendManagedObjectPathElement(r);
    }
  }



  /**
   * A path element representing a singleton managed object.
   */
  private static final class SingletonElement extends Element {

    // The singleton relation.
    private final SingletonRelationDefinition<?, ?> r;



    // Private constructor.
    private SingletonElement(SingletonRelationDefinition<?, ?> r) {
      this.r = r;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public RelationDefinition<?, ?> getRelation() {
      return r;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(ManagedObjectPathSerializer serializer) {
      serializer.appendManagedObjectPathElement(r);
    }
  }

  // Single instance of a root path.
  private static final ManagedObjectPath EMPTY_PATH = new ManagedObjectPath(
      new LinkedList<Element>());



  /**
   * Creates a new managed object path representing the configuration
   * root.
   *
   * @return Returns a new managed object path representing the
   *         configuration root.
   */
  public static ManagedObjectPath emptyPath() {
    return EMPTY_PATH;
  }


  /**
   * Returns a managed object path holding the value of the specified
   * string.
   *
   * @param s
   *          The string to be parsed.
   * @return Returns a managed object path holding the value of the
   *         specified string.
   * @throws IllegalArgumentException
   *           If the string could not be parsed.
   */
  public static ManagedObjectPath valueOf(String s)
      throws IllegalArgumentException {
    return null;
  }



  // The list of path elements in this path.
  private final List<Element> elements;



  // Private constructor.
  private ManagedObjectPath(LinkedList<Element> elements) {
    this.elements = Collections.unmodifiableList(elements);
  }



  /**
   * Creates a new child managed object path beneath the provided
   * parent path.
   *
   * @param r
   *          The instantiable relation referencing the child.
   * @param name
   *          The relative name of the child managed object.
   * @return Returns a new child managed object path beneath the
   *         provided parent path.
   */
  public ManagedObjectPath child(
      InstantiableRelationDefinition<?, ?> r, String name) {
    LinkedList<Element> celements = new LinkedList<Element>(elements);
    celements.add(new InstantiableElement(r, name));
    return new ManagedObjectPath(celements);
  }



  /**
   * Creates a new child managed object path beneath the provided
   * parent path.
   *
   * @param r
   *          The optional relation referencing the child.
   * @return Returns a new child managed object path beneath the
   *         provided parent path.
   */
  public ManagedObjectPath child(OptionalRelationDefinition<?, ?> r) {
    LinkedList<Element> celements = new LinkedList<Element>(elements);
    celements.add(new OptionalElement(r));
    return new ManagedObjectPath(celements);
  }



  /**
   * Creates a new child managed object path beneath the provided
   * parent path.
   *
   * @param r
   *          The singleton relation referencing the child.
   * @return Returns a new child managed object path beneath the
   *         provided parent path.
   */
  public ManagedObjectPath child(SingletonRelationDefinition<?, ?> r) {
    LinkedList<Element> celements = new LinkedList<Element>(elements);
    celements.add(new SingletonElement(r));
    return new ManagedObjectPath(celements);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (obj instanceof ManagedObjectPath) {
      ManagedObjectPath other = (ManagedObjectPath) obj;
      return toString().equals(other.toString());
    } else {
      return false;
    }
  }



  /**
   * Get the definition of the managed object referred to by this
   * path.
   * <p>
   * When the path is empty, the {@link RootCfgDefn}
   * is returned.
   *
   * @return Returns the definition of the managed object referred to
   *         by this path, or the {@link RootCfgDefn}
   *         if the path is empty.
   */
  public AbstractManagedObjectDefinition<?, ?> getManagedObjectDefinition() {
    if (elements.isEmpty()) {
      return RootCfgDefn.getInstance();
    } else {
      Element e = elements.get(elements.size() - 1);
      return e.getRelation().getChildDefinition();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode() {
    return toString().hashCode();
  }



  /**
   * Determine whether or not this path contains any path elements.
   *
   * @return Returns <code>true</code> if this path does not contain
   *         any path elements.
   */
  public boolean isEmpty() {
    return elements.isEmpty();
  }



  /**
   * Creates a new parent managed object path the specified number of
   * path elements above this path.
   *
   * @param offset
   *          The number of path elements (0 - means no offset, 1
   *          means the parent, and 2 means the grand-parent).
   * @return Returns a new parent managed object path the specified
   *         number of path elements above this path.
   * @throws IllegalArgumentException
   *           If the offset is less than 0, or greater than the
   *           number of path elements in this path.
   */
  public ManagedObjectPath parent(int offset)
      throws IllegalArgumentException {
    if (offset < 0) {
      throw new IllegalArgumentException("Negative offset");
    }

    if (offset > elements.size()) {
      throw new IllegalArgumentException(
          "Offset is greater than the number of path elements");
    }

    // An offset of 0 leaves the path unchanged.
    if (offset == 0) {
      return this;
    }

    LinkedList<Element> celements = new LinkedList<Element>(elements
        .subList(0, elements.size() - offset));
    return new ManagedObjectPath(celements);
  }



  /**
   * Serialize this managed object path using the provided
   * serialization strategy.
   * <p>
   * The path elements will be passed to the serializer in big-endian
   * order: starting from the root element and proceeding down to the
   * leaf.
   *
   * @param serializer
   *          The managed object path serialization strategy.
   */
  public void serialize(ManagedObjectPathSerializer serializer) {
    for (Element element : elements) {
      element.serialize(serializer);
    }
  }



  /**
   * Get the number of path elements in this managed object path.
   *
   * @return Returns the number of path elements (0 - means no offset,
   *         1 means the parent, and 2 means the grand-parent).
   */
  public int size() {
    return elements.size();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    toString(builder);
    return builder.toString();
  }



  /**
   * Appends a string representation of this managed object path to
   * the provided string builder.
   *
   * @param builder
   *          Append the string representation to this builder.
   * @see #toString()
   */
  public void toString(final StringBuilder builder) {
    // Use a simple serializer to create the contents.
    ManagedObjectPathSerializer serializer = new ManagedObjectPathSerializer() {

      public void appendManagedObjectPathElement(
          InstantiableRelationDefinition<?, ?> r, String name) {
        builder.append('/');
        builder.append(r.getName());
        builder.append('/');
        builder.append(name);
      }



      public void appendManagedObjectPathElement(
          OptionalRelationDefinition<?, ?> r) {
        builder.append('/');
        builder.append(r.getName());
      }



      public void appendManagedObjectPathElement(
          SingletonRelationDefinition<?, ?> r) {
        builder.append('/');
        builder.append(r.getName());
      }

    };

    serialize(serializer);
  }

}
