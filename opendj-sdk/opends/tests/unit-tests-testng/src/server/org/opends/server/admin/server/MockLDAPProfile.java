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
package org.opends.server.admin.server;



import java.util.List;

import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.LDAPProfile;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.RelationDefinition;



/**
 * A mock LDAP profile for testing purposes.
 */
public final class MockLDAPProfile extends LDAPProfile {

  /**
   * Creates a new mock LDAP profile.
   */
  public MockLDAPProfile() {
    // No implementation required.
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String getAttributeName(ManagedObjectDefinition<?, ?> d,
      PropertyDefinition<?> pd) {
    return "ds-cfg-" + pd.getName();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String getFilter(AbstractManagedObjectDefinition<?, ?> d) {
    // Not implemented yet.
    throw new UnsupportedOperationException();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String getInstantiableRelationChildRDNType(
      InstantiableRelationDefinition<?, ?> r) {
    return "cn";
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public List<String> getInstantiableRelationObjectClasses(
      InstantiableRelationDefinition<?, ?> r) {
    // Not implemented yet.
    throw new UnsupportedOperationException();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String getObjectClass(AbstractManagedObjectDefinition<?, ?> d) {
    // Not implemented yet.
    throw new UnsupportedOperationException();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public List<String> getObjectClasses(
      AbstractManagedObjectDefinition<?, ?> d) {
    // Not implemented yet.
    throw new UnsupportedOperationException();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String getRelationRDNSequence(RelationDefinition<?, ?> r) {
    if (r instanceof InstantiableRelationDefinition) {
      InstantiableRelationDefinition<?, ?> i = (InstantiableRelationDefinition<?, ?>) r;
      return "cn=" + i.getPluralName();
    } else {
      return "cn=" + r.getName();
    }
  }

}
