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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.types;

import java.util.Collections;
import java.util.LinkedHashSet;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.VirtualAttributeCfgDefn.ConflictBehavior;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.EntryDNVirtualAttributeProvider;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * This class provides a set of test cases for virtual attributes.
 */
public class VirtualAttributeTestCase
       extends TypesTestCase
{
  /** The attribute type for the entryDN attribute. */
  private AttributeType entryDNType;

  /** The virtual attribute instance that will be used for all the testing. */
  private VirtualAttribute virtualAttribute;

  /** The virtual attribute rule that will be used for the testing. */
  private VirtualAttributeRule virtualAttributeRule;



  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();

    entryDNType = DirectoryServer.getAttributeType("entrydn");
    assertNotNull(entryDNType);

    EntryDNVirtualAttributeProvider provider =
         new EntryDNVirtualAttributeProvider();

    virtualAttributeRule = new VirtualAttributeRule(entryDNType, provider,
                                    Collections.<DN>emptySet(),
                                    SearchScope.WHOLE_SUBTREE,
                                    Collections.<DN>emptySet(),
                                    Collections.<SearchFilter>emptySet(),
                                    ConflictBehavior.VIRTUAL_OVERRIDES_REAL);

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");

    virtualAttribute = new VirtualAttribute(entryDNType, entry,
                                            virtualAttributeRule);
  }



  /**
   * Tests the various getter methods for virtual attributes.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testGetters()
         throws Exception
  {
    assertEquals(virtualAttribute.getVirtualAttributeRule(),
                 virtualAttributeRule);

    assertTrue(virtualAttribute.isVirtual());
  }



  /**
   * Tests the various methods that interact with the virtual values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testValues()
         throws Exception
  {
    assertEquals(virtualAttribute.size(), 1);
    assertTrue(virtualAttribute.contains(ByteString.valueOf("o=test")));

    assertFalse(virtualAttribute.isEmpty());

    assertTrue(virtualAttribute.contains(ByteString.valueOf("o=test")));
    assertFalse(virtualAttribute.contains(ByteString.valueOf("o=not test")));

    LinkedHashSet<ByteString> testValues = new LinkedHashSet<>();
    testValues.add(ByteString.valueOf("o=test"));
    assertTrue(virtualAttribute.containsAll(testValues));

    testValues.add(ByteString.valueOf("o=not test"));
    assertFalse(virtualAttribute.containsAll(testValues));
  }



  /**
   * Tests the various methods that apply to different kinds of matching.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testMatching()
         throws Exception
  {
    assertEquals(virtualAttribute.matchesSubstring(
        ByteString.valueOf("o="), null,
        ByteString.valueOf("test")),
                 ConditionResult.UNDEFINED);

    ByteString assertionValue = ByteString.valueOf("o=test");
    assertEquals(virtualAttribute.greaterThanOrEqualTo(assertionValue),
                 ConditionResult.UNDEFINED);
    assertEquals(virtualAttribute.lessThanOrEqualTo(assertionValue),
                 ConditionResult.UNDEFINED);
    assertEquals(virtualAttribute.approximatelyEqualTo(assertionValue),
                 ConditionResult.UNDEFINED);
  }



  /**
   * Tests the {@code toString} method.
   */
  @Test
  public void testToString()
  {
    String vattrString = virtualAttribute.toString();
    assertNotNull(vattrString);
    assertTrue(vattrString.length() > 0);
  }
}

