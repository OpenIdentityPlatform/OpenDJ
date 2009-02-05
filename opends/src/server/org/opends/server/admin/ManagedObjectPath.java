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



import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opends.server.admin.std.client.RootCfgClient;
import org.opends.server.admin.std.meta.RootCfgDefn;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.*;


/**
 * A path which can be used to determine the location of a managed
 * object instance.
 * <p>
 * A path is made up of zero or more elements each of which represents
 * a managed object. Managed objects are arranged hierarchically with
 * the root configuration being the top-most managed object. Elements
 * are ordered such that the root configuration managed object is the
 * first element and subsequent elements representing managed objects
 * further down the hierarchy.
 * <p>
 * A path can be encoded into a string representation using the
 * {@link #toString()} and {@link #toString(StringBuilder)} methods.
 * Conversely, this string representation can be parsed using the
 * {@link #valueOf(String)} method.
 * <p>
 * The string representation of a managed object path is similar in
 * principle to a UNIX file-system path and is defined as follows:
 * <ul>
 * <li>the root element is represented by the string <code>/</code>
 * <li>subordinate elements are arranged in big-endian order
 * separated by a forward slash <code>/</code> character
 * <li>an element representing a managed object associated with a
 * one-to-one (singleton) or one-to-zero-or-one (optional) relation
 * has the form <code>relation=</code><i>relation</i>
 * <code>[+type=</code><i>definition</i><code>]</code>, where
 * <i>relation</i> is the name of the relation and <i>definition</i>
 * is the name of the referenced managed object's definition if
 * required (usually this is implied by the relation itself)
 * <li>an element representing a managed object associated with a
 * one-to-many (instantiable) relation has the form
 * <code>relation=</code><i>relation</i><code>[+type=</code>
 * <i>definition</i><code>]</code><code>+name=</code><i>name</i>,
 * where <i>relation</i> is the name of the relation and
 * <i>definition</i> is the name of the referenced managed object's
 * definition if required (usually this is implied by the relation
 * itself), and <i>name</i> is the name of the managed object
 * instance
 * <li>an element representing a managed object associated with a
 * one-to-many (set) relation has the form
 * <code>relation=</code><i>relation</i><code>[+type=</code>
 * <i>definition</i><code>]</code>,
 * where <i>relation</i> is the name of the relation and
 * <i>definition</i> is the name of the referenced managed object's
 * definition.
 * </ul>
 * The following path string representation identifies a connection
 * handler instance (note that the <code>type</code> is not
 * specified indicating that the path identifies a connection handler
 * called <i>my handler</i> which can be any type of connection
 * handler):
 *
 * <pre>
 *  /relation=connection-handler+name=my handler
 * </pre>
 *
 * If the identified connection handler must be an LDAP connection
 * handler then the above path should include the <code>type</code>:
 *
 * <pre>
 *  /relation=connection-handler+type=ldap-connection-handler+name=my handler
 * </pre>
 *
 * The final example identifies the global configuration:
 *
 * <pre>
 *  /relation=global-configuration
 * </pre>
 *
 * @param <C>
 *          The type of client managed object configuration that this
 *          path references.
 * @param <S>
 *          The type of server managed object configuration that this
 *          path references.
 */
public final class ManagedObjectPath<C extends ConfigurationClient,
    S extends Configuration> {

  /**
   * A serialize which is used to generate the toDN representation.
   */
  private static final class DNSerializer implements
      ManagedObjectPathSerializer {

    // The current DN.
    private DN dn;

    // The LDAP profile.
    private final LDAPProfile profile;



    // Create a new DN builder.
    private DNSerializer() {
      this.dn = DN.nullDN();
      this.profile = LDAPProfile.getInstance();
    }



    /**
     * {@inheritDoc}
     */
    public <C extends ConfigurationClient, S extends Configuration>
    void appendManagedObjectPathElement(
        InstantiableRelationDefinition<? super C, ? super S> r,
        AbstractManagedObjectDefinition<C, S> d, String name) {
      // Add the RDN sequence representing the relation.
      appendManagedObjectPathElement(r);

      // Now add the single RDN representing the named instance.
      String type = profile.getRelationChildRDNType(r);
      AttributeType atype = DirectoryServer.getAttributeType(
          type.toLowerCase(), true);
      AttributeValue avalue = AttributeValues.create(atype, name);
      dn = dn.concat(RDN.create(atype, avalue));
    }



    /**
     * {@inheritDoc}
     */
    public <C extends ConfigurationClient, S extends Configuration>
    void appendManagedObjectPathElement(
        SetRelationDefinition<? super C, ? super S> r,
        AbstractManagedObjectDefinition<C, S> d) {
      // Add the RDN sequence representing the relation.
      appendManagedObjectPathElement(r);

      // Now add the single RDN representing the instance.
      String type = profile.getRelationChildRDNType(r);
      AttributeType atype = DirectoryServer.getAttributeType(
          type.toLowerCase(), true);
      AttributeValue avalue = AttributeValues.create(atype, d.getName());
      dn = dn.concat(RDN.create(atype, avalue));
    }



    /**
     * {@inheritDoc}
     */
    public <C extends ConfigurationClient, S extends Configuration>
    void appendManagedObjectPathElement(
        OptionalRelationDefinition<? super C, ? super S> r,
        AbstractManagedObjectDefinition<C, S> d) {
      // Add the RDN sequence representing the relation.
      appendManagedObjectPathElement(r);
    }



    /**
     * {@inheritDoc}
     */
    public <C extends ConfigurationClient, S extends Configuration>
    void appendManagedObjectPathElement(
        SingletonRelationDefinition<? super C, ? super S> r,
        AbstractManagedObjectDefinition<C, S> d) {
      // Add the RDN sequence representing the relation.
      appendManagedObjectPathElement(r);
    }



    // Appends the RDN sequence representing the provided relation.
    private void appendManagedObjectPathElement(RelationDefinition<?, ?> r) {
      // Add the RDN sequence representing the relation.
      try {
        DN localName = DN.decode(profile.getRelationRDNSequence(r));
        dn = dn.concat(localName);
      } catch (DirectoryException e) {
        throw new RuntimeException(e);
      }
    }



    // Gets the serialized DN value.
    private DN toDN() {
      return dn;
    }
  }



  /**
   * Abstract path element.
   */
  private static abstract class Element<C extends ConfigurationClient,
      S extends Configuration> {

    // The type of managed object referenced by this element.
    private final AbstractManagedObjectDefinition<C, S> definition;



    /**
     * Protected constructor.
     *
     * @param definition
     *          The type of managed object referenced by this element.
     */
    protected Element(AbstractManagedObjectDefinition<C, S> definition) {
      this.definition = definition;
    }



    /**
     * Get the managed object definition associated with this element.
     *
     * @return Returns the managed object definition associated with
     *         this element.
     */
    public final AbstractManagedObjectDefinition<C, S>
        getManagedObjectDefinition() {
      return definition;
    }



    /**
     * Get the name associated with this element if applicable.
     *
     * @return Returns the name associated with this element if
     *         applicable.
     */
    public String getName() {
      return null;
    }



    /**
     * Get the relation definition associated with this element.
     *
     * @return Returns the relation definition associated with this
     *         element.
     */
    public abstract RelationDefinition<? super C, ? super S>
        getRelationDefinition();



    /**
     * Serialize this path element using the provided serialization
     * strategy.
     *
     * @param serializer
     *          The managed object path serialization strategy.
     */
    public abstract void serialize(ManagedObjectPathSerializer serializer);
  }



  /**
   * A path element representing an instantiable managed object.
   */
  private static final class InstantiableElement
      <C extends ConfigurationClient, S extends Configuration>
      extends Element<C, S> {

    // Factory method.
    private static final <C extends ConfigurationClient,
        S extends Configuration>
        InstantiableElement<C, S> create(
        InstantiableRelationDefinition<? super C, ? super S> r,
        AbstractManagedObjectDefinition<C, S> d, String name) {
      return new InstantiableElement<C, S>(r, d, name);
    }

    // The name of the managed object.
    private final String name;

    // The instantiable relation.
    private final InstantiableRelationDefinition<? super C, ? super S> r;



    // Private constructor.
    private InstantiableElement(
        InstantiableRelationDefinition<? super C, ? super S> r,
        AbstractManagedObjectDefinition<C, S> d, String name) {
      super(d);
      this.r = r;
      this.name = name;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
      return name;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public InstantiableRelationDefinition<? super C, ? super S>
        getRelationDefinition() {
      return r;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(ManagedObjectPathSerializer serializer) {
      serializer.appendManagedObjectPathElement(r,
          getManagedObjectDefinition(), name);
    }
  }



  /**
   * A path element representing an optional managed object.
   */
  private static final class OptionalElement
      <C extends ConfigurationClient, S extends Configuration>
      extends Element<C, S> {

    // Factory method.
    private static final <C extends ConfigurationClient,
        S extends Configuration> OptionalElement<C, S> create(
        OptionalRelationDefinition<? super C, ? super S> r,
        AbstractManagedObjectDefinition<C, S> d) {
      return new OptionalElement<C, S>(r, d);
    }

    // The optional relation.
    private final OptionalRelationDefinition<? super C, ? super S> r;



    // Private constructor.
    private OptionalElement(OptionalRelationDefinition<? super C, ? super S> r,
        AbstractManagedObjectDefinition<C, S> d) {
      super(d);
      this.r = r;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public OptionalRelationDefinition<? super C, ? super S>
        getRelationDefinition() {
      return r;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(ManagedObjectPathSerializer serializer) {
      serializer
          .appendManagedObjectPathElement(r, getManagedObjectDefinition());
    }
  }



  /**
   * A path element representing an set managed object.
   */
  private static final class SetElement
      <C extends ConfigurationClient, S extends Configuration>
      extends Element<C, S> {

    // Factory method.
    private static final <C extends ConfigurationClient,
        S extends Configuration>
        SetElement<C, S> create(
        SetRelationDefinition<? super C, ? super S> r,
        AbstractManagedObjectDefinition<C, S> d) {
      return new SetElement<C, S>(r, d);
    }

    // The set relation.
    private final SetRelationDefinition<? super C, ? super S> r;



    // Private constructor.
    private SetElement(
        SetRelationDefinition<? super C, ? super S> r,
        AbstractManagedObjectDefinition<C, S> d) {
      super(d);
      this.r = r;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public SetRelationDefinition<? super C, ? super S>
        getRelationDefinition() {
      return r;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(ManagedObjectPathSerializer serializer) {
      serializer.appendManagedObjectPathElement(r,
          getManagedObjectDefinition());
    }
  }



  /**
   * A path element representing a singleton managed object.
   */
  private static final class SingletonElement
      <C extends ConfigurationClient, S extends Configuration>
      extends Element<C, S> {

    // Factory method.
    private static final <C extends ConfigurationClient,
        S extends Configuration> SingletonElement<C, S> create(
        SingletonRelationDefinition<? super C, ? super S> r,
        AbstractManagedObjectDefinition<C, S> d) {
      return new SingletonElement<C, S>(r, d);
    }

    // The singleton relation.
    private final SingletonRelationDefinition<? super C, ? super S> r;



    // Private constructor.
    private SingletonElement(
        SingletonRelationDefinition<? super C, ? super S> r,
        AbstractManagedObjectDefinition<C, S> d) {
      super(d);
      this.r = r;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public SingletonRelationDefinition<? super C, ? super S>
        getRelationDefinition() {
      return r;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(ManagedObjectPathSerializer serializer) {
      serializer
          .appendManagedObjectPathElement(r, getManagedObjectDefinition());
    }
  }



  /**
   * A serialize which is used to generate the toString
   * representation.
   */
  private static final class StringSerializer implements
      ManagedObjectPathSerializer {

    // Serialize to this string builder.
    private final StringBuilder builder;



    // Private constructor.
    private StringSerializer(StringBuilder builder) {
      this.builder = builder;
    }



    /**
     * {@inheritDoc}
     */
    public <M extends ConfigurationClient, N extends Configuration>
        void appendManagedObjectPathElement(
        InstantiableRelationDefinition<? super M, ? super N> r,
        AbstractManagedObjectDefinition<M, N> d, String name) {
      serializeElement(r, d);

      // Be careful to escape any forward slashes in the name.
      builder.append("+name=");
      builder.append(name.replace("/", "//"));
    }



    /**
     * {@inheritDoc}
     */
    public <M extends ConfigurationClient, N extends Configuration>
        void appendManagedObjectPathElement(
        OptionalRelationDefinition<? super M, ? super N> r,
        AbstractManagedObjectDefinition<M, N> d) {
      serializeElement(r, d);
    }



    /**
     * {@inheritDoc}
     */
    public <M extends ConfigurationClient, N extends Configuration>
        void appendManagedObjectPathElement(
        SingletonRelationDefinition<? super M, ? super N> r,
        AbstractManagedObjectDefinition<M, N> d) {
      serializeElement(r, d);
    }



    /**
     * {@inheritDoc}
     */
    public <M extends ConfigurationClient, N extends Configuration>
        void appendManagedObjectPathElement(
        SetRelationDefinition<? super M, ? super N> r,
        AbstractManagedObjectDefinition<M, N> d) {
      serializeElement(r, d);
    }



    // Common element serialization.
    private <M, N> void serializeElement(RelationDefinition<?, ?> r,
        AbstractManagedObjectDefinition<?, ?> d) {
      // Always specify the relation name.
      builder.append("/relation=");
      builder.append(r.getName());

      // Only specify the type if it is a sub-type of the relation's
      // type.
      if (r.getChildDefinition() != d) {
        builder.append("+type=");
        builder.append(d.getName());
      }
    }
  }

  // Single instance of a root path.
  private static final ManagedObjectPath<RootCfgClient, RootCfg> EMPTY_PATH =
      new ManagedObjectPath<RootCfgClient, RootCfg>(
      new LinkedList<Element<?, ?>>(), null, RootCfgDefn.getInstance());

  // A regular expression used to parse path elements.
  private static final Pattern PE_REGEXP = Pattern
      .compile("^\\s*relation=\\s*([^+]+)\\s*"
          + "(\\+\\s*type=\\s*([^+]+)\\s*)?"
          + "(\\+\\s*name=\\s*([^+]+)\\s*)?$");



  /**
   * Creates a new managed object path representing the configuration
   * root.
   *
   * @return Returns a new managed object path representing the
   *         configuration root.
   */
  public static ManagedObjectPath<RootCfgClient, RootCfg> emptyPath() {
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
  public static ManagedObjectPath<?, ?> valueOf(String s)
      throws IllegalArgumentException {
    String ns = s.trim();

    // Check for root special case.
    if (ns.equals("/")) {
      return EMPTY_PATH;
    }

    // Parse the elements.
    LinkedList<Element<?, ?>> elements = new LinkedList<Element<?, ?>>();
    Element<?, ?> lastElement = null;
    AbstractManagedObjectDefinition<?, ?> definition = RootCfgDefn
        .getInstance();

    if (!ns.startsWith("/")) {
      throw new IllegalArgumentException("Invalid path \"" + ns
          + "\": must begin with a \"/\"");
    }

    int start = 1;
    while (true) {
      // Get the next path element.
      int end;
      for (end = start; end < ns.length(); end++) {
        char c = ns.charAt(end);
        if (c == '/') {
          if (end == (ns.length() - 1)) {
            throw new IllegalArgumentException("Invalid path \"" + ns
                + "\": must not end with a trailing \"/\"");
          }

          if (ns.charAt(end + 1) == '/') {
            // Found an escaped forward slash.
            end++;
          } else {
            // Found the end of this path element.
            break;
          }
        }
      }

      // Get the next element.
      String es = ns.substring(start, end);

      Matcher m = PE_REGEXP.matcher(es);
      if (!m.matches()) {
        throw new IllegalArgumentException("Invalid path element \"" + es
            + "\" in path \"" + ns + "\"");
      }

      // Mandatory.
      String relation = m.group(1);

      // Optional.
      String type = m.group(3);

      // Mandatory if relation is instantiable.
      String name = m.group(5);

      // Get the relation definition.
      RelationDefinition<?, ?> r;
      try {
        r = definition.getRelationDefinition(relation);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Invalid path element \"" + es
            + "\" in path \"" + ns + "\": unknown relation \"" + relation
            + "\"");
      }

      // Append the next element.
      lastElement = createElement(r, ns, es, type, name);
      elements.add(lastElement);
      definition = lastElement.getManagedObjectDefinition();

      // Update start to point to the beginning of the next element.
      if (end < ns.length()) {
        // Skip to the beginning of the next element
        start = end + 1;
      } else {
        // We reached the end of the string.
        break;
      }
    }

    // Construct the new path.
    return create(elements, lastElement);
  }



  // Factory method required in order to allow generic wild-card
  // construction of new paths.
  private static <C extends ConfigurationClient, S extends Configuration>
      ManagedObjectPath<C, S> create(
      LinkedList<Element<?, ?>> elements, Element<C, S> lastElement) {
    return new ManagedObjectPath<C, S>(elements, lastElement
        .getRelationDefinition(), lastElement.getManagedObjectDefinition());
  }



  // Decode an element.
  private static <C extends ConfigurationClient, S extends Configuration>
      Element<? extends C, ? extends S> createElement(
      RelationDefinition<C, S> r, String path, String element, String type,
      String name) {
    // First determine the managed object definition.
    AbstractManagedObjectDefinition<? extends C, ? extends S> d = null;

    if (type != null) {
      for (AbstractManagedObjectDefinition<? extends C, ? extends S> child : r
          .getChildDefinition().getAllChildren()) {
        if (child.getName().equals(type)) {
          d = child;
          break;
        }
      }

      if (d == null) {
        throw new IllegalArgumentException("Invalid path element \"" + element
            + "\" in path \"" + path + "\": unknown sub-type \"" + type + "\"");
      }
    } else {
      d = r.getChildDefinition();
    }

    if (r instanceof InstantiableRelationDefinition) {
      InstantiableRelationDefinition<C, S> ir =
        (InstantiableRelationDefinition<C, S>) r;

      if (name == null) {
        throw new IllegalArgumentException("Invalid path element \"" + element
            + "\" in path \"" + path
            + "\": no instance name for instantiable relation");
      }

      return InstantiableElement.create(ir, d, name);
    } else if (r instanceof SetRelationDefinition) {
      SetRelationDefinition<C, S> ir = (SetRelationDefinition<C, S>) r;

      if (name != null) {
        throw new IllegalArgumentException("Invalid path element \"" + element
            + "\" in path \"" + path
            + "\": instance name specified for set relation");
      }

      return SetElement.create(ir, d);
    } else if (r instanceof OptionalRelationDefinition) {
      OptionalRelationDefinition<C, S> or =
        (OptionalRelationDefinition<C, S>) r;

      if (name != null) {
        throw new IllegalArgumentException("Invalid path element \"" + element
            + "\" in path \"" + path
            + "\": instance name specified for optional relation");
      }

      return OptionalElement.create(or, d);
    } else if (r instanceof SingletonRelationDefinition) {
      SingletonRelationDefinition<C, S> sr =
        (SingletonRelationDefinition<C, S>) r;

      if (name != null) {
        throw new IllegalArgumentException("Invalid path element \"" + element
            + "\" in path \"" + path
            + "\": instance name specified for singleton relation");
      }

      return SingletonElement.create(sr, d);
    } else {
      throw new IllegalArgumentException("Invalid path element \"" + element
          + "\" in path \"" + path + "\": unsupported relation type");
    }
  }

  // The managed object definition in this path.
  private final AbstractManagedObjectDefinition<C, S> d;

  // The list of path elements in this path.
  private final List<Element<?, ?>> elements;

  // The last relation definition in this path.
  private final RelationDefinition<? super C, ? super S> r;



  // Private constructor.
  private ManagedObjectPath(LinkedList<Element<?, ?>> elements,
      RelationDefinition<? super C, ? super S> r,
      AbstractManagedObjectDefinition<C, S> d) {
    this.elements = Collections.unmodifiableList(elements);
    this.r = r;
    this.d = d;
  }



  /**
   * Creates a new managed object path which has the same structure as
   * this path except that the final path element is associated with
   * the specified managed object definition.
   *
   * @param <CC>
   *          The type of client managed object configuration that
   *          this path will reference.
   * @param <SS>
   *          The type of server managed object configuration that
   *          this path will reference.
   * @param nd
   *          The new managed object definition.
   * @return Returns a new managed object path which has the same
   *         structure as this path except that the final path element
   *         is associated with the specified managed object
   *         definition.
   */
  @SuppressWarnings("unchecked")
  public <CC extends C, SS extends S> ManagedObjectPath<CC, SS> asSubType(
      AbstractManagedObjectDefinition<CC, SS> nd) {
    if (r instanceof InstantiableRelationDefinition) {
      InstantiableRelationDefinition<? super C, ? super S> ir =
        (InstantiableRelationDefinition<? super C, ? super S>) r;
      if (elements.size() == 0) {
        return parent().child(ir, nd, null);
      } else {
        return parent().child(ir, nd,
            elements.get(elements.size() - 1).getName());
      }
    } else if (r instanceof SetRelationDefinition) {
      SetRelationDefinition<? super C, ? super S> sr =
        (SetRelationDefinition<? super C, ? super S>) r;
      return parent().child(sr, nd);
    } else if (r instanceof OptionalRelationDefinition) {
      OptionalRelationDefinition<? super C, ? super S> or =
        (OptionalRelationDefinition<? super C, ? super S>) r;
      return parent().child(or, nd);
    } else {
      SingletonRelationDefinition<? super C, ? super S> sr =
        (SingletonRelationDefinition<? super C, ? super S>) r;
      return parent().child(sr, nd);
    }
  }



  /**
   * Creates a new child managed object path beneath the provided
   * parent path having the specified managed object definition.
   *
   * @param <M>
   *          The type of client managed object configuration that the
   *          child path references.
   * @param <N>
   *          The type of server managed object configuration that the
   *          child path references.
   * @param r
   *          The instantiable relation referencing the child.
   * @param d
   *          The managed object definition associated with the child
   *          (must be a sub-type of the relation).
   * @param name
   *          The relative name of the child managed object.
   * @return Returns a new child managed object path beneath the
   *         provided parent path.
   * @throws IllegalArgumentException
   *           If the provided name is empty or blank.
   */
  public <M extends ConfigurationClient, N extends Configuration>
      ManagedObjectPath<M, N> child(
      InstantiableRelationDefinition<? super M, ? super N> r,
      AbstractManagedObjectDefinition<M, N> d, String name)
      throws IllegalArgumentException {
    if (name.trim().length() == 0) {
      throw new IllegalArgumentException(
          "Empty or blank managed object names are not allowed");
    }
    LinkedList<Element<?, ?>> celements = new LinkedList<Element<?, ?>>(
        elements);
    celements.add(new InstantiableElement<M, N>(r, d, name));
    return new ManagedObjectPath<M, N>(celements, r, d);
  }



  /**
   * Creates a new child managed object path beneath the provided
   * parent path using the relation's child managed object definition.
   *
   * @param <M>
   *          The type of client managed object configuration that the
   *          child path references.
   * @param <N>
   *          The type of server managed object configuration that the
   *          child path references.
   * @param r
   *          The instantiable relation referencing the child.
   * @param name
   *          The relative name of the child managed object.
   * @return Returns a new child managed object path beneath the
   *         provided parent path.
   * @throws IllegalArgumentException
   *           If the provided name is empty or blank.
   */
  public <M extends ConfigurationClient, N extends Configuration>
      ManagedObjectPath<M, N> child(
      InstantiableRelationDefinition<M, N> r, String name)
      throws IllegalArgumentException {
    return child(r, r.getChildDefinition(), name);
  }



  /**
   * Creates a new child managed object path beneath the provided
   * parent path having the specified managed object definition.
   *
   * @param <M>
   *          The type of client managed object configuration that the
   *          child path references.
   * @param <N>
   *          The type of server managed object configuration that the
   *          child path references.
   * @param r
   *          The optional relation referencing the child.
   * @param d
   *          The managed object definition associated with the child
   *          (must be a sub-type of the relation).
   * @return Returns a new child managed object path beneath the
   *         provided parent path.
   */
  public <M extends ConfigurationClient, N extends Configuration>
      ManagedObjectPath<M, N> child(
      OptionalRelationDefinition<? super M, ? super N> r,
      AbstractManagedObjectDefinition<M, N> d) {
    LinkedList<Element<?, ?>> celements = new LinkedList<Element<?, ?>>(
        elements);
    celements.add(new OptionalElement<M, N>(r, d));
    return new ManagedObjectPath<M, N>(celements, r, d);
  }



  /**
   * Creates a new child managed object path beneath the provided
   * parent path using the relation's child managed object definition.
   *
   * @param <M>
   *          The type of client managed object configuration that the
   *          child path references.
   * @param <N>
   *          The type of server managed object configuration that the
   *          child path references.
   * @param r
   *          The optional relation referencing the child.
   * @return Returns a new child managed object path beneath the
   *         provided parent path.
   */
  public <M extends ConfigurationClient, N extends Configuration>
      ManagedObjectPath<M, N> child(OptionalRelationDefinition<M, N> r) {
    return child(r, r.getChildDefinition());
  }



  /**
   * Creates a new child managed object path beneath the provided
   * parent path having the specified managed object definition.
   *
   * @param <M>
   *          The type of client managed object configuration that the
   *          child path references.
   * @param <N>
   *          The type of server managed object configuration that the
   *          child path references.
   * @param r
   *          The singleton relation referencing the child.
   * @param d
   *          The managed object definition associated with the child
   *          (must be a sub-type of the relation).
   * @return Returns a new child managed object path beneath the
   *         provided parent path.
   */
  public <M extends ConfigurationClient, N extends Configuration>
      ManagedObjectPath<M, N> child(
      SingletonRelationDefinition<? super M, ? super N> r,
      AbstractManagedObjectDefinition<M, N> d) {
    LinkedList<Element<?, ?>> celements = new LinkedList<Element<?, ?>>(
        elements);
    celements.add(new SingletonElement<M, N>(r, d));
    return new ManagedObjectPath<M, N>(celements, r, d);
  }



  /**
   * Creates a new child managed object path beneath the provided
   * parent path using the relation's child managed object definition.
   *
   * @param <M>
   *          The type of client managed object configuration that the
   *          child path references.
   * @param <N>
   *          The type of server managed object configuration that the
   *          child path references.
   * @param r
   *          The singleton relation referencing the child.
   * @return Returns a new child managed object path beneath the
   *         provided parent path.
   */
  public <M extends ConfigurationClient, N extends Configuration>
      ManagedObjectPath<M, N> child(SingletonRelationDefinition<M, N> r) {
    return child(r, r.getChildDefinition());
  }



  /**
   * Creates a new child managed object path beneath the provided
   * parent path having the specified managed object definition.
   *
   * @param <M>
   *          The type of client managed object configuration that the
   *          child path references.
   * @param <N>
   *          The type of server managed object configuration that the
   *          child path references.
   * @param r
   *          The set relation referencing the child.
   * @param d
   *          The managed object definition associated with the child
   *          (must be a sub-type of the relation).
   * @return Returns a new child managed object path beneath the
   *         provided parent path.
   * @throws IllegalArgumentException
   *           If the provided name is empty or blank.
   */
  public <M extends ConfigurationClient, N extends Configuration>
      ManagedObjectPath<M, N> child(
      SetRelationDefinition<? super M, ? super N> r,
      AbstractManagedObjectDefinition<M, N> d)
      throws IllegalArgumentException {
    LinkedList<Element<?, ?>> celements = new LinkedList<Element<?, ?>>(
        elements);
    celements.add(new SetElement<M, N>(r, d));
    return new ManagedObjectPath<M, N>(celements, r, d);
  }



  /**
   * Creates a new child managed object path beneath the provided parent
   * path having the managed object definition indicated by
   * <code>name</code>.
   *
   * @param <M>
   *          The type of client managed object configuration that the
   *          path references.
   * @param <N>
   *          The type of server managed object configuration that the
   *          path references.
   * @param r
   *          The set relation referencing the child.
   * @param name
   *          The name of the managed object definition associated with
   *          the child (must be a sub-type of the relation).
   * @return Returns a new child managed object path beneath the
   *         provided parent path.
   * @throws IllegalArgumentException
   *           If the provided name is empty or blank or specifies a
   *           managed object definition which is not a sub-type of the
   *           relation's child definition.
   */
  public <M extends ConfigurationClient, N extends Configuration>
      ManagedObjectPath<? extends M, ? extends N> child(
      SetRelationDefinition<M, N> r,
      String name)
      throws IllegalArgumentException {
    AbstractManagedObjectDefinition<M, N> d = r.getChildDefinition();
    return child(r, d.getChild(name));
  }



  /**
   * Creates a new child managed object path beneath the provided
   * parent path using the relation's child managed object definition.
   *
   * @param <M>
   *          The type of client managed object configuration that the
   *          child path references.
   * @param <N>
   *          The type of server managed object configuration that the
   *          child path references.
   * @param r
   *          The set relation referencing the child.
   * @return Returns a new child managed object path beneath the
   *         provided parent path.
   * @throws IllegalArgumentException
   *           If the provided name is empty or blank.
   */
  public <M extends ConfigurationClient, N extends Configuration>
      ManagedObjectPath<M, N> child(
      SetRelationDefinition<M, N> r)
      throws IllegalArgumentException {
    return child(r, r.getChildDefinition());
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (obj instanceof ManagedObjectPath) {
      ManagedObjectPath<?, ?> other = (ManagedObjectPath<?, ?>) obj;
      return toString().equals(other.toString());
    } else {
      return false;
    }
  }



  /**
   * Get the definition of the managed object referred to by this
   * path.
   * <p>
   * When the path is empty, the {@link RootCfgDefn} is returned.
   *
   * @return Returns the definition of the managed object referred to
   *         by this path, or the {@link RootCfgDefn} if the path is
   *         empty.
   */
  public AbstractManagedObjectDefinition<C, S> getManagedObjectDefinition() {
    return d;
  }



  /**
   * Get the name of the managed object referred to by this path if
   * applicable.
   * <p>
   * If there path does not refer to an instantiable managed object
   * <code>null</code> is returned.
   *
   * @return Returns the name of the managed object referred to by
   *         this path, or <code>null</code> if the managed object
   *         does not have a name.
   */
  public String getName() {
    if (elements.isEmpty()) {
      return null;
    } else {
      return elements.get(elements.size() - 1).getName();
    }
  }



  /**
   * Get the relation definition of the managed object referred to by
   * this path.
   * <p>
   * When the path is empty, the <code>null</code> is returned.
   *
   * @return Returns the relation definition of the managed object
   *         referred to by this path, or the <code>null</code> if
   *         the path is empty.
   */
  public RelationDefinition<? super C, ? super S> getRelationDefinition() {
    return r;
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
   * Determines whether this managed object path references the same
   * location as the provided managed object path.
   * <p>
   * This method differs from <code>equals</code> in that it ignores
   * sub-type definitions.
   *
   * @param other
   *          The managed object path to be compared.
   * @return Returns <code>true</code> if this managed object path
   *         references the same location as the provided managed
   *         object path.
   */
  public boolean matches(ManagedObjectPath<?, ?> other) {
    DN thisDN = toDN();
    DN otherDN = other.toDN();
    return thisDN.equals(otherDN);
  }



  /**
   * Creates a new parent managed object path representing the
   * immediate parent of this path. This method is a short-hand for
   * <code>parent(1)</code>.
   *
   * @return Returns a new parent managed object path representing the
   *         immediate parent of this path.
   * @throws IllegalArgumentException
   *           If this path does not have a parent (i.e. it is the
   *           empty path).
   */
  public ManagedObjectPath<?, ?> parent() throws IllegalArgumentException {
    return parent(1);
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
  public ManagedObjectPath<?, ?> parent(int offset)
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

    // Return the empty path if the parent has zero elements.
    if (elements.size() == offset) {
      return emptyPath();
    }

    LinkedList<Element<?, ?>> celements = new LinkedList<Element<?, ?>>(
        elements.subList(0, elements.size() - offset));
    return create(celements, celements.getLast());
  }



  /**
   * Creates a new managed object path which has the same structure as
   * this path except that the final path element is renamed. The
   * final path element must comprise of an instantiable relation.
   *
   * @param newName
   *          The new name of the final path element.
   * @return Returns a new managed object path which has the same
   *         structure as this path except that the final path element
   *         is renamed.
   * @throws IllegalStateException
   *           If this managed object path is empty or if its final
   *           path element does not comprise of an instantiable
   *           relation.
   */
  @SuppressWarnings("unchecked")
  public ManagedObjectPath<C, S> rename(String newName)
      throws IllegalStateException {
    if (elements.size() == 0) {
      throw new IllegalStateException("Cannot rename an empty path");
    }

    if (r instanceof InstantiableRelationDefinition) {
      InstantiableRelationDefinition<? super C, ? super S> ir =
        (InstantiableRelationDefinition<? super C, ? super S>) r;
      return parent().child(ir, d, newName);
    } else {
      throw new IllegalStateException("Not an instantiable relation");
    }
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
    for (Element<?, ?> element : elements) {
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
   * Creates a DN representation of this managed object path.
   *
   * @return Returns a DN representation of this managed object path.
   */
  public DN toDN() {
    // Use a simple serializer to create the contents.
    DNSerializer serializer = new DNSerializer();
    serialize(serializer);
    return serializer.toDN();
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
    if (isEmpty()) {
      // Special treatment of root configuration paths.
      builder.append('/');
    } else {
      // Use a simple serializer to create the contents.
      ManagedObjectPathSerializer serializer = new StringSerializer(builder);
      serialize(serializer);
    }
  }

}
