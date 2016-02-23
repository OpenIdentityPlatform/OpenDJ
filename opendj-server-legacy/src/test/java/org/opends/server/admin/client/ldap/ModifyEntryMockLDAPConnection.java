/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.admin.client.ldap;

import static org.testng.Assert.*;

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

import org.forgerock.util.Reject;

/**
 * A mock LDAP connection which is used to verify that a modify
 * operation was requested and that it has the correct parameters.
 */
public final class ModifyEntryMockLDAPConnection extends MockLDAPConnection {

  /** Detect multiple calls. */
  private boolean alreadyModified;

  /** The expected DN. */
  private final LdapName expectedDN;

  /** The expected set of modifications (attribute name -> list of values). */
  private final Map<String, List<String>> modifications = new HashMap<>();



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
    Reject.ifNull(expectedName);
    Reject.ifNull(expectedValues);
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



  /** {@inheritDoc} */
  @Override
  public void modifyEntry(LdapName dn, Attributes mods) throws NamingException {
    assertFalse(alreadyModified);
    assertEquals(dn, expectedDN);

    Map<String, List<String>> expected = new HashMap<>(modifications);
    NamingEnumeration<? extends Attribute> ne = mods.getAll();
    while (ne.hasMore()) {
      Attribute mod = ne.next();
      String attrID = mod.getID();
      List<String> values = expected.remove(attrID);
      assertNotNull(values, "Unexpected modification to attribute " + attrID);
      assertAttributeEquals(mod, values);
    }

    assertTrue(expected.isEmpty(), "Missing modifications to: " + expected.keySet());

    alreadyModified = true;
  }
}
