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
package org.opends.server.interop;



import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.types.DN;
import org.opends.server.types.RDN;
import org.opends.server.types.SearchScope;

import static org.testng.Assert.*;

import static org.opends.server.util.ServerConstants.*;



/**
 * This class is used to ensure that the LazyDN class provides parity with the
 * DN class, and that the set of public members for the DN class have not
 * changed unexpectedly.
 */
public class LazyDNTestCase
       extends InteropTestCase
{
  /**
   * Make sure that the Directory Server is running so we have access to schema
   * information and other necessary facilities.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Ensures that the public methods exposed in the DN class have not changed in
   * an unexpected way that could adversely impact the LazyDN class.
   */
  @Test()
  public void testDNPublicMethods()
  {
    // Create a set of string arrays containing the signatures of all non-static
    // public and protected methods in the DN class.  The first element should
    // be the method name.  The second element should be the return type.  The
    // remaining elements should be the types of the arguments.
    LinkedList<String[]> sigs = new LinkedList<String[]>();
    sigs.add(new String[] { "isNullDN",
                            "boolean" });
    sigs.add(new String[] { "getNumComponents",
                            "int" });
    sigs.add(new String[] { "getRDN",
                            "org.opends.server.types.RDN" });
    sigs.add(new String[] { "getRDN",
                            "org.opends.server.types.RDN",
                            "int" });
    sigs.add(new String[] { "getParent",
                            "org.opends.server.types.DN" });
    sigs.add(new String[] { "getParentDNInSuffix",
                            "org.opends.server.types.DN" });
    sigs.add(new String[] { "concat",
                            "org.opends.server.types.DN",
                            "org.opends.server.types.RDN" });
    sigs.add(new String[] { "concat",
                            "org.opends.server.types.DN",
                            "[Lorg.opends.server.types.RDN;" });
    sigs.add(new String[] { "concat",
                            "org.opends.server.types.DN",
                            "org.opends.server.types.DN" });
    sigs.add(new String[] { "isDescendantOf",
                            "boolean",
                            "org.opends.server.types.DN" });
    sigs.add(new String[] { "isAncestorOf",
                            "boolean",
                            "org.opends.server.types.DN" });
    sigs.add(new String[] { "matchesBaseAndScope",
                            "boolean",
                            "org.opends.server.types.DN",
                            "org.opends.server.types.SearchScope" });
    sigs.add(new String[] { "equals",
                            "boolean",
                            "java.lang.Object" });
    sigs.add(new String[] { "hashCode",
                            "int" });
    sigs.add(new String[] { "toString",
                            "java.lang.String" });
    sigs.add(new String[] { "toString",
                            "void",
                            "java.lang.StringBuilder" });
    sigs.add(new String[] { "toNormalizedString",
                            "java.lang.String" });
    sigs.add(new String[] { "toNormalizedString",
                            "void",
                            "java.lang.StringBuilder" });
    sigs.add(new String[] { "compareTo",
                            "int",
                            "org.opends.server.types.DN" });

    // This one is a little weird, but we need it because of the way that
    // generics works.
    sigs.add(new String[] { "compareTo",
                            "int",
                            "java.lang.Object" });


    // Iterate through all the methods in the DN class and try to find the
    // corresponding signature in the list.
    LinkedList<String[]> unexpectedMethods = new LinkedList<String[]>();
    Method[] superclassMethods = DN.class.getSuperclass().getMethods();
methodLoop:
    for (Method m : DN.class.getMethods())
    {
      // If the method is not "public" or "protected", then we don't care about
      // it.
      int modifiers = m.getModifiers();
      if (! (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)))
      {
        continue;
      }

      // If the method is "static", then we don't care about it.
      if (Modifier.isStatic(modifiers))
      {
        continue;
      }

      // If the method was also defined in the superclass, then we don't care
      // about it unless it was the "toString" method because that is important.
      for (Method superclassMethod : superclassMethods)
      {
        if (m.equals(superclassMethod))
        {
          continue methodLoop;
        }
      }


      // Create an array containing the elements of the method signature and see
      // if it's present in the set.
      LinkedList<String> signatureElements = new LinkedList<String>();
      signatureElements.add(m.getName());
      signatureElements.add(m.getReturnType().getName());
      for (Class c : m.getParameterTypes())
      {
        signatureElements.add(c.getName());
      }

      String[] signatureArray = new String[signatureElements.size()];
      signatureElements.toArray(signatureArray);

      boolean found = false;
      Iterator<String[]> iterator = sigs.iterator();
      while (iterator.hasNext())
      {
        String[] sigArray = iterator.next();
        if (Arrays.equals(signatureArray, sigArray))
        {
          iterator.remove();
          found = true;
          break;
        }
      }

      if (! found)
      {
        unexpectedMethods.add(signatureArray);
      }
    }


    // If there were any unexpected methods found, or if there were any expected
    // methods not found, then fail.
    if (! (unexpectedMethods.isEmpty() && sigs.isEmpty()))
    {
      StringBuilder buffer = new StringBuilder();
      if (! unexpectedMethods.isEmpty())
      {
        buffer.append("Unexpected methods found in the DN class:" + EOL);
        for (String[] sig : unexpectedMethods)
        {
          buffer.append("     ");
          buffer.append(sig[1]);
          buffer.append(" ");
          buffer.append(sig[0]);
          buffer.append("(");
          for (int i=2; i < sig.length; i++)
          {
            if (i > 2)
            {
              buffer.append(", ");
            }

            buffer.append(sig[i]);
          }
          buffer.append(")" + EOL);
        }
      }

      if (! sigs.isEmpty())
      {
        buffer.append("Expected methods not found in the DN class:" + EOL);
        for (String[] sig : sigs)
        {
          buffer.append("     ");
          buffer.append(sig[1]);
          buffer.append(" ");
          buffer.append(sig[0]);
          buffer.append("(");
          for (int i=2; i < sig.length; i++)
          {
            if (i > 2)
            {
              buffer.append(", ");
            }

            buffer.append(sig[i]);
          }
          buffer.append(")" + EOL);
        }
      }

      buffer.append("If these changes to the DN public API were intentional, " +
                    "then update the LazyDNTestCase.testDNPublicMethods " +
                    "method to reflect the new API.  Also make sure that "+
                    "the LazyDN method has been updated to reflect the " +
                    "change as well.");

      fail(buffer.toString());
    }
  }



  /**
   * Tests the {@code isNullDN} method with valid DN strings.
   */
  @Test()
  public void testIsNullDN()
  {
    assertTrue(new LazyDN("").isNullDN());
    assertFalse(new LazyDN("dc=example,dc=com").isNullDN());
  }



  /**
   * Tests the {@code isNullDN} method with an invalid DN string.
   */
  @Test(expectedExceptions = { RuntimeException.class })
  public void testIsNullDNInvalid()
  {
    new LazyDN("invalid").isNullDN();
  }



  /**
   * Tests the {@code getNumComponents} method with valid DN strings.
   */
  @Test()
  public void testGetNumComponents()
  {
    assertEquals(new LazyDN("").getNumComponents(), 0);
    assertEquals(new LazyDN("dc=example,dc=com").getNumComponents(), 2);
  }



  /**
   * Tests the {@code getNumComponents} method with an invalid DN string.
   */
  @Test(expectedExceptions = { RuntimeException.class })
  public void testGetNumComponentsInvalid()
  {
    new LazyDN("invalid").getNumComponents();
  }



  /**
   * Tests the first {@code getRDN} method with valid DN strings.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetRDN1()
         throws Exception
  {
    assertNull(new LazyDN("").getRDN());
    assertEquals(new LazyDN("dc=example,dc=com").getRDN(),
                 RDN.decode("dc=example"));
  }



  /**
   * Tests the first {@code getRDN} method with an invalid DN string.
   */
  @Test(expectedExceptions = { RuntimeException.class })
  public void testGetRDN1Invalid()
  {
    new LazyDN("invalid").getRDN();
  }



  /**
   * Tests the second {@code getRDN} method with valid DN strings.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetRDN2()
         throws Exception
  {
    assertEquals(new LazyDN("dc=example,dc=com").getRDN(1),
                 RDN.decode("dc=com"));
  }



  /**
   * Tests the second {@code getRDN} method with an invalid DN string.
   */
  @Test(expectedExceptions = { RuntimeException.class })
  public void testGetRDN2Invalid()
  {
    new LazyDN("invalid").getRDN(1);
  }



  /**
   * Tests the {@code getParent} method with valid DN strings.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetParent()
         throws Exception
  {
    assertNull(new LazyDN("").getParent());
    assertEquals(new LazyDN("dc=example,dc=com").getParent(),
                 DN.decode("dc=com"));
  }



  /**
   * Tests the {@code getParent} method with an invalid DN string.
   */
  @Test(expectedExceptions = { RuntimeException.class })
  public void testGetParentInvalid()
  {
    new LazyDN("invalid").getParent();
  }



  /**
   * Tests the {@code getParentDNInSuffix} method with valid DN strings.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetParentDNInSuffix()
         throws Exception
  {
    assertNull(new LazyDN("").getParentDNInSuffix());
    assertNull(new LazyDN("dc=example,dc=com").getParentDNInSuffix());
    assertEquals(
         new LazyDN("ou=People,dc=example,dc=com").getParentDNInSuffix(),
         DN.decode("dc=example,dc=com"));
  }



  /**
   * Tests the {@code getParentDNInSuffix} method with an invalid DN string.
   */
  @Test(expectedExceptions = { RuntimeException.class })
  public void testGetParentDNInSuffixInvalid()
  {
    new LazyDN("invalid").getParentDNInSuffix();
  }



  /**
   * Tests the first {@code concat} method with valid DN strings.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testConcat1()
         throws Exception
  {
    assertEquals(new LazyDN("").concat(RDN.decode("dc=com")),
                 DN.decode("dc=com"));
    assertEquals(
         new LazyDN("dc=example,dc=com").concat(RDN.decode("ou=People")),
         DN.decode("ou=People,dc=example,dc=com"));
  }



  /**
   * Tests the first {@code concat} method with an invalid DN string.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { RuntimeException.class })
  public void testConcat1Invalid()
         throws Exception
  {
    new LazyDN("invalid").concat(RDN.decode("dc=com"));
  }



  /**
   * Tests the second {@code concat} method with valid DN strings.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testConcat2()
         throws Exception
  {
    RDN[] rdnArray = { RDN.decode("dc=example"), RDN.decode("dc=com") };

    assertEquals(new LazyDN("").concat(rdnArray),
                 DN.decode("dc=example,dc=com"));
    assertEquals(new LazyDN("dc=example,dc=com").concat(rdnArray),
                 DN.decode("dc=example,dc=com,dc=example,dc=com"));
  }



  /**
   * Tests the second {@code concat} method with an invalid DN string.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { RuntimeException.class })
  public void testConcat2Invalid()
         throws Exception
  {
    RDN[] rdnArray = { RDN.decode("dc=example"), RDN.decode("dc=com") };

    new LazyDN("invalid").concat(rdnArray);
  }



  /**
   * Tests the third {@code concat} method with valid DN strings.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testConcat3()
         throws Exception
  {
    assertEquals(new LazyDN("").concat(DN.decode("dc=example,dc=com")),
                 DN.decode("dc=example,dc=com"));
    assertEquals(new LazyDN("dc=example,dc=com").concat(DN.decode("ou=People")),
                 DN.decode("ou=People,dc=example,dc=com"));
  }



  /**
   * Tests the third {@code concat} method with an invalid DN string.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { RuntimeException.class })
  public void testConcat3Invalid()
         throws Exception
  {
    new LazyDN("invalid").concat(DN.decode("ou=People"));
  }



  /**
   * Tests the {@code isDescendantOf} method with valid DN strings.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIsDescendantOf()
         throws Exception
  {
    assertFalse(new LazyDN("").isDescendantOf(DN.decode("dc=example,dc=com")));
    assertTrue(new LazyDN("ou=People,dc=example,dc=com").isDescendantOf(
                    DN.decode("dc=example,dc=com")));
  }



  /**
   * Tests the {@code isDescendantOf} method with an invalid DN string.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { RuntimeException.class })
  public void testIsDescendantOfInvalid()
         throws Exception
  {
    new LazyDN("invalid").isDescendantOf(DN.decode("dc=example,dc=com"));
  }



  /**
   * Tests the {@code isAncestorOf} method with valid DN strings.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIsAncestorOf()
         throws Exception
  {
    assertTrue(new LazyDN("").isAncestorOf(DN.decode("dc=example,dc=com")));
    assertTrue(new LazyDN("dc=example,dc=com").isAncestorOf(
                    DN.decode("ou=People,dc=example,dc=com")));
  }



  /**
   * Tests the {@code isAncestorOf} method with an invalid DN string.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { RuntimeException.class })
  public void testIsAncestorOfInvalid()
         throws Exception
  {
    new LazyDN("invalid").isAncestorOf(DN.decode("dc=example,dc=com"));
  }



  /**
   * Tests the {@code matchesBaseAndScope} method with valid DN strings.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testMatchesBaseAndScope()
         throws Exception
  {
    assertTrue(new LazyDN("").matchesBaseAndScope(DN.nullDN(),
                                                  SearchScope.BASE_OBJECT));
    assertTrue(new LazyDN("dc=example,dc=com").matchesBaseAndScope(
                    DN.decode("dc=example,dc=com"), SearchScope.BASE_OBJECT));
    assertTrue(new LazyDN("ou=People,dc=example,dc=com").matchesBaseAndScope(
                    DN.decode("dc=example,dc=com"), SearchScope.WHOLE_SUBTREE));
  }



  /**
   * Tests the {@code matchesBaseAndScope} method with an invalid DN string.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { RuntimeException.class })
  public void testMatchesBaseandScopeInvalid()
         throws Exception
  {
    new LazyDN("invalid").matchesBaseAndScope(DN.decode("dc=example,dc=com"),
                                              SearchScope.WHOLE_SUBTREE);
  }



  /**
   * Tests the {@code equals} method with valid DN strings.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testEquals()
         throws Exception
  {
    assertTrue(new LazyDN("").equals(DN.nullDN()));
    assertTrue(new LazyDN("dc=example,dc=com").equals(
                    DN.decode("dc=example,dc=com")));
  }



  /**
   * Tests the {@code equals} method with an invalid DN string.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { RuntimeException.class })
  public void testEqualsInvalid()
         throws Exception
  {
    new LazyDN("invalid").equals(DN.decode("dc=example,dc=com"));
  }



  /**
   * Tests the {@code hashCode} method with valid DN strings.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testHashCode()
         throws Exception
  {
    assertEquals(new LazyDN("").hashCode(), DN.nullDN().hashCode());
    assertEquals(new LazyDN("dc=example,dc=com").hashCode(),
                 DN.decode("dc=example,dc=com").hashCode());
  }



  /**
   * Tests the {@code hashCode} method with an invalid DN string.
   */
  @Test(expectedExceptions = { RuntimeException.class })
  public void testHashCodeInvalid()
  {
    new LazyDN("invalid").hashCode();
  }



  /**
   * Tests the first {@code toString} method with valid DN strings.
   */
  @Test()
  public void testToString1()
  {
    assertEquals(new LazyDN("").toString(), "");
    assertEquals(new LazyDN("dc=example,dc=com").toString(),
                 "dc=example,dc=com");
    assertEquals(new LazyDN("ou=People, dc=example, dc=com").toString(),
                 "ou=People, dc=example, dc=com");
  }



  /**
   * Tests the first {@code toString} method with an invalid DN string.
   */
  @Test()
  public void testToString1Invalid()
  {
    assertEquals(new LazyDN("invalid").toString(), "invalid");
  }



  /**
   * Tests the second {@code toString} method with valid DN strings.
   */
  @Test()
  public void testToString2()
  {
    StringBuilder buffer = new StringBuilder();
    new LazyDN("").toString(buffer);
    assertEquals(buffer.toString(), "");

    buffer = new StringBuilder();
    new LazyDN("dc=example,dc=com").toString(buffer);
    assertEquals(buffer.toString(), "dc=example,dc=com");

    buffer = new StringBuilder();
    new LazyDN("ou=People, dc=example, dc=com").toString(buffer);
    assertEquals(buffer.toString(), "ou=People, dc=example, dc=com");
  }



  /**
   * Tests the second {@code toString} method with an invalid DN string.
   */
  @Test()
  public void testToString2Invalid()
  {
    StringBuilder buffer = new StringBuilder();
    new LazyDN("invalid").toString(buffer);
    assertEquals(buffer.toString(), "invalid");
  }



  /**
   * Tests the first {@code toNormalizedString} method with valid DN strings.
   */
  @Test()
  public void testToNormalizedString1()
  {
    assertEquals(new LazyDN("").toNormalizedString(), "");
    assertEquals(new LazyDN("dc=example,dc=com").toNormalizedString(),
                 "dc=example,dc=com");
    assertEquals(
         new LazyDN("ou=People, dc=example, dc=com").toNormalizedString(),
         "ou=people,dc=example,dc=com");
  }



  /**
   * Tests the first {@code toNormalizedString} method with an invalid DN
   * string.
   */
  @Test(expectedExceptions = { RuntimeException.class })
  public void testToNormalizedString1Invalid()
  {
    new LazyDN("invalid").toNormalizedString();
  }



  /**
   * Tests the second {@code toNormalizedString} method with valid DN strings.
   */
  @Test()
  public void testToNormalizedString2()
  {
    StringBuilder buffer = new StringBuilder();
    new LazyDN("").toNormalizedString(buffer);
    assertEquals(buffer.toString(), "");

    buffer = new StringBuilder();
    new LazyDN("dc=example,dc=com").toNormalizedString(buffer);
    assertEquals(buffer.toString(), "dc=example,dc=com");

    buffer = new StringBuilder();
    new LazyDN("ou=People, dc=example, dc=com").toNormalizedString(buffer);
    assertEquals(buffer.toString(), "ou=people,dc=example,dc=com");
  }



  /**
   * Tests the second {@code toNormalizedString} method with an invalid DN
   * string.
   */
  @Test(expectedExceptions = { RuntimeException.class })
  public void testToNormalizedString2Invalid()
  {
    new LazyDN("invalid").toNormalizedString(new StringBuilder());
  }



  /**
   * Tests the {@code compareTo} method with valid DN strings.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCompareTo()
         throws Exception
  {
    DN dn = DN.nullDN();
    assertEquals(new LazyDN("").compareTo(dn), 0);

    dn = DN.decode("dc=example,dc=com");
    assertEquals(new LazyDN("dc=example,dc=com").compareTo(dn), 0);
  }



  /**
   * Tests the {@code compareTo} method with an invalid DN string.
   */
  @Test(expectedExceptions = { RuntimeException.class })
  public void testCompareToInvalid()
  {
    new LazyDN("invalid").compareTo(DN.nullDN());
  }
}

