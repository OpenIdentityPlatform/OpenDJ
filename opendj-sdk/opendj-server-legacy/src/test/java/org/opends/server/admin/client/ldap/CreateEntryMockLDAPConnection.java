/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
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
 * A mock LDAP connection which is used to verify that an add
 * operation was requested and that it has the correct parameters.
 */
public final class CreateEntryMockLDAPConnection extends MockLDAPConnection {

  /** Detect multiple calls. */
  private boolean alreadyAdded;

  /** The expected set of attributes (attribute name -> list of values). */
  private final Map<String, List<String>> attributes = new HashMap<>();

  /** The expected DN. */
  private final LdapName expectedDN;



  /**
   * Create a new mock ldap connection for detecting add operations.
   *
   * @param dn
   *          The expected DN of the entry to be added.
   */
  public CreateEntryMockLDAPConnection(String dn) {
    try {
      this.expectedDN = new LdapName(dn);
    } catch (InvalidNameException e) {
      throw new RuntimeException(e);
    }
  }



  /**
   * Add an attribute which should be part of the add operation.
   *
   * @param expectedName
   *          The name of the expected attribute.
   * @param expectedValues
   *          The attribute's expected values (never empty).
   */
  public void addExpectedAttribute(String expectedName,
      String... expectedValues) {
    Reject.ifNull(expectedName);
    Reject.ifNull(expectedValues);
    Reject.ifFalse(expectedValues.length > 0);
    attributes.put(expectedName, Arrays.asList(expectedValues));
  }



  /**
   * Asserts that the entry was created.
   */
  public void assertEntryIsCreated() {
    assertTrue(alreadyAdded);
  }



  /** {@inheritDoc} */
  @Override
  public void createEntry(LdapName dn, Attributes attributes)
      throws NamingException {
    assertFalse(alreadyAdded);
    assertEquals(dn, expectedDN);

    Map<String, List<String>> expected = new HashMap<>(this.attributes);
    NamingEnumeration<? extends Attribute> ne = attributes.getAll();
    while (ne.hasMore()) {
      Attribute attribute = ne.next();
      String attrID = attribute.getID();
      List<String> values = expected.remove(attrID);
      assertNotNull(values, "Unexpected attribute " + attrID);
      assertAttributeEquals(attribute, values);
    }

    assertTrue(expected.isEmpty(), "Missing expected attributes: " + expected.keySet());

    alreadyAdded = true;
  }
}
