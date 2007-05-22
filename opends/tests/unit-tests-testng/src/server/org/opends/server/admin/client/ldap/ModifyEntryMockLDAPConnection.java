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
package org.opends.server.admin.client.ldap;



import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.naming.InvalidNameException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.ldap.LdapName;

import org.opends.server.util.Validator;
import org.testng.Assert;



/**
 * A mock LDAP connection which is used to verify that a modify
 * operation was requested and that it has the correct parameters.
 */
public final class ModifyEntryMockLDAPConnection extends MockLDAPConnection {

  // Detect multiple calls.
  private boolean alreadyModified = false;

  // The expected DN.
  private final LdapName expectedDN;

  // The expected set of modifications (attribute name -> list of
  // values).
  private final Map<String, List<String>> modifications = new HashMap<String, List<String>>();



  /**
   * Create a new mock ldap connection for detecting modify
   * operations.
   *
   * @param dn
   *          The expected DN of the entry to be added.
   */
  public ModifyEntryMockLDAPConnection(String dn) {
    try {
      this.expectedDN = new LdapName(dn);
    } catch (InvalidNameException e) {
      throw new RuntimeException(e);
    }
  }



  /**
   * Add a modification which should be part of the modify operation.
   *
   * @param expectedName
   *          The name of the expected attribute.
   * @param expectedValues
   *          The attribute's expected new values (possibly empty if
   *          deleted).
   */
  public void addExpectedModification(String expectedName,
      String... expectedValues) {
    Validator.ensureNotNull(expectedName);
    Validator.ensureNotNull(expectedValues);
    modifications.put(expectedName, Arrays.asList(expectedValues));
  }



  /**
   * Determines whether or not the entry was modified.
   *
   * @return Returns <code>true</code> if it was modified.
   */
  public boolean isEntryModified() {
    return alreadyModified;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void modifyEntry(LdapName dn, Attributes mods) throws NamingException {
    Assert.assertFalse(alreadyModified);
    Assert.assertEquals(dn, expectedDN);

    Map<String, List<String>> expected = new HashMap<String, List<String>>(
        modifications);
    NamingEnumeration<? extends Attribute> ne = mods.getAll();
    while (ne.hasMore()) {
      Attribute mod = ne.next();
      String attrID = mod.getID();
      List<String> values = expected.remove(attrID);
      if (values == null) {
        Assert.fail("Unexpected modification to attribute " + attrID);
      }
      assertAttributeEquals(mod, values);
    }

    if (!expected.isEmpty()) {
      Assert.fail("Missing modifications to: " + expected.keySet());
    }

    alreadyModified = true;
  }
}
