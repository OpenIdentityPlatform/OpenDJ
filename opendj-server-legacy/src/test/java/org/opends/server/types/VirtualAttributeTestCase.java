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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.types;

import static org.testng.Assert.*;

import java.util.Collections;
import java.util.LinkedHashSet;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.server.config.meta.VirtualAttributeCfgDefn.ConflictBehavior;
import org.opends.server.TestCaseUtils;
import org.opends.server.extensions.EntryDNVirtualAttributeProvider;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

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

    entryDNType = CoreSchema.getEntryDNAttributeType();

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
    assertTrue(virtualAttribute.contains(ByteString.valueOfUtf8("o=test")));

    assertFalse(virtualAttribute.isEmpty());

    assertTrue(virtualAttribute.contains(ByteString.valueOfUtf8("o=test")));
    assertFalse(virtualAttribute.contains(ByteString.valueOfUtf8("o=not test")));

    LinkedHashSet<ByteString> testValues = new LinkedHashSet<>();
    testValues.add(ByteString.valueOfUtf8("o=test"));
    assertTrue(virtualAttribute.containsAll(testValues));

    testValues.add(ByteString.valueOfUtf8("o=not test"));
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
        ByteString.valueOfUtf8("o="), null,
        ByteString.valueOfUtf8("test")),
                 ConditionResult.UNDEFINED);

    ByteString assertionValue = ByteString.valueOfUtf8("o=test");
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

