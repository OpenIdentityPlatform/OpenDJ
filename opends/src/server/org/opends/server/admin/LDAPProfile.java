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
 * This class is used to map configuration elements to their LDAP schema names.
 */
public final class LDAPProfile {

  // The singleton instance.
  private static final LDAPProfile INSTANCE = new LDAPProfile();

  // The LDAP profile property table.
  private final ManagedObjectDefinitionResource resource;



  /**
   * Get the global LDAP profile instance.
   *
   * @return Returns the global LDAP profile instance.
   */
  public static LDAPProfile getInstance() {
    return INSTANCE;
  }



  // Private constructor.
  private LDAPProfile() {
    this.resource = ManagedObjectDefinitionResource.createForProfile("ldap");
  }



  /**
   * Gets the LDAP RDN attribute type for child entries of an instantiable
   * relation.
   *
   * @param r
   *          The instantiable relation.
   * @return Returns the LDAP RDN attribute type for child entries of an
   *         instantiable relation.
   */
  public String getInstantiableRelationChildRDNType(
      InstantiableRelationDefinition<?, ?> r) {
    // For now, assume always "cn".
    return "cn";
  }



  /**
   * Gets the LDAP object classes associated with an instantiable relation
   * branch. The branch is the parent entry of child managed objects.
   *
   * @param r
   *          The instantiable relation.
   * @return Returns the LDAP object classes associated with an instantiable
   *         relation branch.
   */
  public List<String> getInstantiableRelationObjectClasses(
      InstantiableRelationDefinition<?, ?> r) {
    return Arrays.asList(new String[] { "top", "ds-cfg-branch" });
  }



  /**
   * Get an LDAP RDN sequence associatied with a relation.
   *
   * @param r
   *          The relation.
   * @return Returns the LDAP RDN sequence associatied with a relation.
   */
  public String getRelationRDNSequence(RelationDefinition<?, ?> r) {
    return resource.getString(r.getParentDefinition(), "rdn." + r.getName());
  }



  /**
   * Get an LDAP filter string which can be used to search for entries matching
   * the specified definition.
   *
   * @param d
   *          The managed object definition.
   * @return Returns the LDAP filter.
   */
  public String getFilter(AbstractManagedObjectDefinition<?, ?> d) {
    StringBuilder builder = new StringBuilder();
    builder.append("(ObjectClass=");
    builder.append(getObjectClass(d));
    builder.append(')');
    return builder.toString();
  }



  /**
   * Get the principle object class associated with the specified definition.
   *
   * @param d
   *          The managed object definition.
   * @return Returns the principle object class associated with the specified
   *         definition.
   */
  public String getObjectClass(AbstractManagedObjectDefinition<?, ?> d) {
    return resource.getString(d, "objectclass");
  }



  /**
   * Get all the object classes associated with the specified definition.
   * <p>
   * The returned list is ordered such that the uppermost object classes appear
   * first (e.g. top).
   *
   * @param d
   *          The managed object definition.
   * @return Returns all the object classes associated with the specified
   *         definition.
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
   * Get the name of the LDAP attribute associated with the specified property
   * definition.
   *
   * @param d
   *          The managed object definition.
   * @param pd
   *          The property definition.
   * @return Returns the name of the LDAP attribute associated with the
   *         specified property definition.
   */
  public String getAttributeName(ManagedObjectDefinition<?, ?> d,
      PropertyDefinition<?> pd) {
    return resource.getString(d, "attribute." + pd.getName());
  }
}
