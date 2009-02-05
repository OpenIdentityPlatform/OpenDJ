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
package org.opends.server.types;



import static org.testng.Assert.*;

import java.util.Collections;
import java.util.LinkedHashSet;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.VirtualAttributeCfgDefn.ConflictBehavior;
import org.opends.server.extensions.EntryDNVirtualAttributeProvider;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;



/**
 * This class provides a set of test cases for virtual attributes.
 */
public class VirtualAttributeTestCase
       extends TypesTestCase
{
  // The attribute type for the entryDN attribute.
  private AttributeType entryDNType;

  // The virtual attribute instance that will be used for all the testing.
  private VirtualAttribute virtualAttribute;

  // The virutal attribute rule that will be used for the testing.
  private VirtualAttributeRule virtualAttributeRule;



  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();

    entryDNType = DirectoryConfig.getAttributeType("entrydn", false);
    assertNotNull(entryDNType);

    EntryDNVirtualAttributeProvider provider =
         new EntryDNVirtualAttributeProvider();

    virtualAttributeRule = new VirtualAttributeRule(entryDNType, provider,
                                    Collections.<DN>emptySet(),
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
  @Test()
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
  @Test()
  public void testValues()
         throws Exception
  {
    assertEquals(virtualAttribute.size(), 1);
    assertTrue(virtualAttribute.contains(AttributeValues.create(entryDNType, "o=test")));

    assertTrue(!virtualAttribute.isEmpty());

    assertTrue(virtualAttribute.contains(AttributeValues.create(entryDNType,
                                                            "o=test")));
    assertFalse(virtualAttribute.contains(AttributeValues.create(entryDNType,
                                                             "o=not test")));

    LinkedHashSet<AttributeValue> testValues =
         new LinkedHashSet<AttributeValue>();
    testValues.add(AttributeValues.create(entryDNType, "o=test"));
    assertTrue(virtualAttribute.containsAll(testValues));

    testValues.add(AttributeValues.create(entryDNType, "o=not test"));
    assertFalse(virtualAttribute.containsAll(testValues));
  }



  /**
   * Tests the various methods that apply to different kinds of matching.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testMatching()
         throws Exception
  {
    assertEquals(virtualAttribute.matchesSubstring(
        ByteString.valueOf("o="), null,
        ByteString.valueOf("test")),
                 ConditionResult.UNDEFINED);

    AttributeValue assertionValue = AttributeValues.create(entryDNType, "o=test");
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
  @Test()
  public void testToString()
  {
    String vattrString = virtualAttribute.toString();
    assertNotNull(vattrString);
    assertTrue(vattrString.length() > 0);
  }
}

