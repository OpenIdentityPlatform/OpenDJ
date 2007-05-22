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
import java.util.Set;



/**
 * This class is used to map configuration elements to their LDAP
 * schema names.
 */
public abstract class LDAPProfile {

  // This class is abstract so that we can derive a mock LDAP profile
  // for testing.

  // The singleton instance.
  private static final LDAPProfile INSTANCE = new MyLDAPProfile();



  /**
   * Protected default constructor.
   */
  protected LDAPProfile() {
    // No implementation required.
  }



  /**
   * Get the global LDAP profile instance.
   *
   * @return Returns the global LDAP profile instance.
   */
  public static LDAPProfile getInstance() {
    return INSTANCE;
  }



  /**
   * Concrete implementation.
   */
  private static class MyLDAPProfile extends LDAPProfile {

    // The LDAP profile property table.
    private final ManagedObjectDefinitionResource resource;



    // Private constructor.
    private MyLDAPProfile() {
      this.resource = ManagedObjectDefinitionResource
          .createForProfile("ldap");
    }



    /**
     * {@inheritDoc}
     */
    public String getInstantiableRelationChildRDNType(
        InstantiableRelationDefinition<?, ?> r) {
      return resource.getString(r.getParentDefinition(),
          "naming-attribute." + r.getName());
    }



    /**
     * {@inheritDoc}
     */
    public List<String> getInstantiableRelationObjectClasses(
        InstantiableRelationDefinition<?, ?> r) {
      return Arrays.asList(new String[] { "top", "ds-cfg-branch" });
    }



    /**
     * {@inheritDoc}
     */
    public String getRelationRDNSequence(RelationDefinition<?, ?> r) {
      return resource.getString(r.getParentDefinition(), "rdn."
          + r.getName());
    }



    /**
     * {@inheritDoc}
     */
    public String getObjectClass(
        AbstractManagedObjectDefinition<?, ?> d) {
      return resource.getString(d, "objectclass");
    }



    /**
     * {@inheritDoc}
     */
    public List<String> getObjectClasses(
        AbstractManagedObjectDefinition<?, ?> d) {
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

      // Make sure that we have top.
      if (!s.contains("top")) {
        objectClasses.addFirst("top");
      }

      return objectClasses;
    }



    /**
     * {@inheritDoc}
     */
    public String getAttributeName(ManagedObjectDefinition<?, ?> d,
        PropertyDefinition<?> pd) {
      return resource.getString(d, "attribute." + pd.getName());
    }
  }



  /**
   * Gets the LDAP RDN attribute type for child entries of an
   * instantiable relation.
   *
   * @param r
   *          The instantiable relation.
   * @return Returns the LDAP RDN attribute type for child entries of
   *         an instantiable relation.
   */
  public abstract String getInstantiableRelationChildRDNType(
      InstantiableRelationDefinition<?, ?> r);



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
  public abstract List<String> getInstantiableRelationObjectClasses(
      InstantiableRelationDefinition<?, ?> r);



  /**
   * Get an LDAP RDN sequence associatied with a relation.
   *
   * @param r
   *          The relation.
   * @return Returns the LDAP RDN sequence associatied with a
   *         relation.
   */
  public abstract String getRelationRDNSequence(
      RelationDefinition<?, ?> r);



  /**
   * Get the principle object class associated with the specified
   * definition.
   *
   * @param d
   *          The managed object definition.
   * @return Returns the principle object class associated with the
   *         specified definition.
   */
  public abstract String getObjectClass(
      AbstractManagedObjectDefinition<?, ?> d);



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
   */
  public abstract List<String> getObjectClasses(
      AbstractManagedObjectDefinition<?, ?> d);



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
   */
  public abstract String getAttributeName(
      ManagedObjectDefinition<?, ?> d, PropertyDefinition<?> pd);
}
