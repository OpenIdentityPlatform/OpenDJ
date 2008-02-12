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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.admin.client.ldap;



import javax.naming.InvalidNameException;
import javax.naming.NamingException;
import javax.naming.ldap.LdapName;

import org.testng.Assert;



/**
 * A mock LDAP connection which is used to verify that a delete
 * subtree takes place.
 */
public final class DeleteSubtreeMockLDAPConnection extends MockLDAPConnection {

  // Detect multiple calls.
  private boolean alreadyDeleted = false;

  // The expected DN.
  private final LdapName expectedDN;



  /**
   * Create a new mock ldap connection for detecting subtree deletes.
   *
   * @param dn
   *          The expected subtree DN.
   */
  public DeleteSubtreeMockLDAPConnection(String dn) {
    try {
      this.expectedDN = new LdapName(dn);
    } catch (InvalidNameException e) {
      throw new RuntimeException(e);
    }
  }



  /**
   * Asserts that the subtree was deleted.
   */
  public void assertSubtreeIsDeleted() {
    Assert.assertTrue(alreadyDeleted);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteSubtree(LdapName dn) throws NamingException {
    Assert.assertFalse(alreadyDeleted);
    Assert.assertEquals(dn, expectedDN);
    alreadyDeleted = true;
  }
}
