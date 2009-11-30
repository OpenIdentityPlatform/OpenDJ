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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk;



import org.testng.Assert;
import org.testng.annotations.Test;



/**
 * Test {@code BasicAttribute}.
 */
@Test(groups = { "precommit", "types", "sdk" }, sequential = true)
public final class SortedEntryTest extends OpenDSTestCase
{
  @Test
  public void SmokeTest() throws Exception
  {
    Entry entry1 = new SortedEntry(
        "dn: cn=Joe Bloggs,dc=example,dc=com",
        "objectClass: top",
        "objectClass: person",
        "cn: Joe Bloggs",
        "sn: Bloggs",
        "givenName: Joe",
        "description: A description");

    Entry entry2 = new SortedEntry(
        "dn: cn=Joe Bloggs,dc=example,dc=com",
        "changetype: add",
        "objectClass: top",
        "objectClass: person",
        "cn: Joe Bloggs",
        "sn: Bloggs",
        "givenName: Joe",
        "description: A description");

    Assert.assertEquals(entry1, entry2);

    for (Entry e : new Entry[] { entry1, entry2 })
    {
      Assert.assertEquals(e.getName(), DN
          .valueOf("cn=Joe Bloggs,dc=example,dc=com"));
      Assert.assertEquals(e.getAttributeCount(), 5);

      Assert.assertEquals(e.getAttribute("objectClass").size(), 2);
      Assert.assertTrue(e.containsObjectClass("top"));
      Assert.assertTrue(e.containsObjectClass("person"));
      Assert.assertFalse(e.containsObjectClass("foo"));

      Assert.assertTrue(e.containsAttribute("objectClass"));
      Assert.assertTrue(e.containsAttribute("cn"));
      Assert.assertTrue(e.containsAttribute("sn"));
      Assert.assertTrue(e.containsAttribute("givenName"));
      Assert.assertTrue(e.containsAttribute("description"));

      Assert.assertEquals(e.getAttribute("cn").firstValueAsString(), "Joe Bloggs");
      Assert.assertEquals(e.getAttribute("sn").firstValueAsString(), "Bloggs");
    }
  }
}
