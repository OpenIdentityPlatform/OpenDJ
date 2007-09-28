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



import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.MissingResourceException;
import java.util.NoSuchElementException;
import java.util.Set;



/**
 * This class is used to map configuration elements to their LDAP
 * schema names.
 * <p>
 * It is possible to augment the core LDAP profile with additional
 * profile mappings at run-time using instances of {@link Wrapper}.
 * This is useful for unit tests which need to add and remove mock
 * components.
 */
public final class LDAPProfile {

  /**
   * LDAP profile wrappers can be used to provide temporary LDAP
   * profile information for components which do not have LDAP profile
   * property files. These components are typically "mock" components
   * used in unit-tests.
   */
  public static abstract class Wrapper {

    /**
     * Default constructor.
     */
    protected Wrapper() {
      // No implementation required.
    }



    /**
     * Get the name of the LDAP attribute associated with the
     * specified property definition.
     * <p>
     * The default implementation of this method is to return
     * <code>null</code>.
     *
     * @param d
     *          The managed object definition.
     * @param pd
     *          The property definition.
     * @return Returns the name of the LDAP attribute associated with
     *         the specified property definition, or <code>null</code>
     *         if the property definition is not handled by this LDAP
     *         profile wrapper.
     */
    public String getAttributeName(AbstractManagedObjectDefinition<?, ?> d,
        PropertyDefinition<?> pd) {
      return null;
    }



    /**
     * Gets the LDAP RDN attribute type for child entries of an
     * instantiable relation.
     * <p>
     * The default implementation of this method is to return
     * <code>null</code>.
     *
     * @param r
     *          The instantiable relation.
     * @return Returns the LDAP RDN attribute type for child entries
     *         of an instantiable relation, or <code>null</code> if
     *         the instantiable relation is not handled by this LDAP
     *         profile wrapper.
     */
    public String getInstantiableRelationChildRDNType(
        InstantiableRelationDefinition<?, ?> r) {
      return null;
    }



    /**
     * Get the principle object class associated with the specified
     * definition.
     * <p>
     * The default implementation of this method is to return
     * <code>null</code>.
     *
     * @param d
     *          The managed object definition.
     * @return Returns the principle object class associated with the
     *         specified definition, or <code>null</code> if the
     *         managed object definition is not handled by this LDAP
     *         profile wrapper.
     */
    public String getObjectClass(AbstractManagedObjectDefinition<?, ?> d) {
      return null;
    }



    /**
     * Get an LDAP RDN sequence associatied with a relation.
     * <p>
     * The default implementation of this method is to return
     * <code>null</code>.
     *
     * @param r
     *          The relation.
     * @return Returns the LDAP RDN sequence associatied with a
     *         relation, or <code>null</code> if the relation is not
     *         handled by this LDAP profile wrapper.
     */
    public String getRelationRDNSequence(RelationDefinition<?, ?> r) {
      return null;
    }
  }

  // The singleton instance.
  private static final LDAPProfile INSTANCE = new LDAPProfile();



  /**
   * Get the global LDAP profile instance.
   *
   * @return Returns the global LDAP profile instance.
   */
  public static LDAPProfile getInstance() {
    return INSTANCE;
  }

  // The list of profile wrappers.
  private final LinkedList<Wrapper> profiles = new LinkedList<Wrapper>();;

  // The LDAP profile property table.
  private final ManagedObjectDefinitionResource resource =
    ManagedObjectDefinitionResource.createForProfile("ldap");



  // Prevent construction.
  private LDAPProfile() {
    // No implementation required.
  }



  /**
   * Get the name of the LDAP attribute associated with the specified
   * property definition.
   *
   * @param d
   *          The managed object definition.
   * @param pd
   *          The property definition.
   * @return Returns the name of the LDAP attribute associated with
   *         the specified property definition.
   * @throws MissingResourceException
   *           If the LDAP profile properties file associated with the
   *           provided managed object definition could not be loaded.
   */
  public String getAttributeName(AbstractManagedObjectDefinition<?, ?> d,
      PropertyDefinition<?> pd) throws MissingResourceException {
    for (Wrapper profile : profiles) {
      String attributeName = profile.getAttributeName(d, pd);
      if (attributeName != null) {
        return attributeName;
      }
    }
    return resource.getString(d, "attribute." + pd.getName());
  }



  /**
   * Gets the LDAP RDN attribute type for child entries of an
   * instantiable relation.
   *
   * @param r
   *          The instantiable relation.
   * @return Returns the LDAP RDN attribute type for child entries of
   *         an instantiable relation.
   * @throws MissingResourceException
   *           If the LDAP profile properties file associated with the
   *           provided managed object definition could not be loaded.
   */
  public String getInstantiableRelationChildRDNType(
      InstantiableRelationDefinition<?, ?> r) throws MissingResourceException {
    if (r.getNamingPropertyDefinition() != null) {
      // Use the attribute associated with the naming property.
      return getAttributeName(r.getChildDefinition(), r
          .getNamingPropertyDefinition());
    } else {
      for (Wrapper profile : profiles) {
        String rdnType = profile.getInstantiableRelationChildRDNType(r);
        if (rdnType != null) {
          return rdnType;
        }
      }
      return resource.getString(r.getParentDefinition(), "naming-attribute."
          + r.getName());
    }
  }



  /**
   * Gets the LDAP object classes associated with an instantiable
   * relation branch. The branch is the parent entry of child managed
   * objects.
   *
   * @param r
   *          The instantiable relation.
   * @return Returns the LDAP object classes associated with an
   *         instantiable relation branch.
   */
  public List<String> getInstantiableRelationObjectClasses(
      InstantiableRelationDefinition<?, ?> r) {
    return Arrays.asList(new String[] { "top", "ds-cfg-branch" });
  }



  /**
   * Get the principle object class associated with the specified
   * definition.
   *
   * @param d
   *          The managed object definition.
   * @return Returns the principle object class associated with the
   *         specified definition.
   * @throws MissingResourceException
   *           If the LDAP profile properties file associated with the
   *           provided managed object definition could not be loaded.
   */
  public String getObjectClass(AbstractManagedObjectDefinition<?, ?> d)
      throws MissingResourceException {
    if (d.isTop()) {
      return "top";
    }

    for (Wrapper profile : profiles) {
      String objectClass = profile.getObjectClass(d);
      if (objectClass != null) {
        return objectClass;
      }
    }
    return resource.getString(d, "objectclass");
  }



  /**
   * Get all the object classes associated with the specified
   * definition.
   * <p>
   * The returned list is ordered such that the uppermost object
   * classes appear first (e.g. top).
   *
   * @param d
   *          The managed object definition.
   * @return Returns all the object classes associated with the
   *         specified definition.
   * @throws MissingResourceException
   *           If the LDAP profile properties file associated with the
   *           provided managed object definition could not be loaded.
   */
  public List<String> getObjectClasses(AbstractManagedObjectDefinition<?, ?> d)
      throws MissingResourceException {
    LinkedList<String> objectClasses = new LinkedList<String>();
    Set<String> s = new HashSet<String>();

    // Add the object classes from the parent hierarchy.
    while (d != null) {
      String oc = getObjectClass(d);
      if (!s.contains(oc)) {
        objectClasses.addFirst(oc);
        s.add(oc);
      }
      d = d.getParent();
    }

    if (!s.contains("top")) {
      objectClasses.addFirst("top");
    }

    return objectClasses;
  }



  /**
   * Get an LDAP RDN sequence associatied with a relation.
   *
   * @param r
   *          The relation.
   * @return Returns the LDAP RDN sequence associatied with a
   *         relation.
   * @throws MissingResourceException
   *           If the LDAP profile properties file associated with the
   *           provided managed object definition could not be loaded.
   */
  public String getRelationRDNSequence(RelationDefinition<?, ?> r)
      throws MissingResourceException {
    for (Wrapper profile : profiles) {
      String rdnSequence = profile.getRelationRDNSequence(r);
      if (rdnSequence != null) {
        return rdnSequence;
      }
    }
    return resource.getString(r.getParentDefinition(), "rdn." + r.getName());
  }



  /**
   * Removes the last LDAP profile wrapper added using
   * {@link #pushWrapper(org.opends.server.admin.LDAPProfile.Wrapper)}.
   *
   * @throws NoSuchElementException
   *           If there are no LDAP profile wrappers.
   */
  public void popWrapper() throws NoSuchElementException {
    profiles.removeFirst();
  }



  /**
   * Decorates the core LDAP profile with the provided LDAP profile
   * wrapper. All profile requests will be directed to the provided
   * wrapper before being forwarded onto the core profile if the
   * request could not be satisfied.
   *
   * @param wrapper
   *          The LDAP profile wrapper.
   */
  public void pushWrapper(Wrapper wrapper) {
    profiles.addFirst(wrapper);
  }
}
