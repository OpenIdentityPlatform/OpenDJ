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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.types;

import static org.testng.Assert.*;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.core.SubtreeSpecificationTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** This class defines a set of tests for the {@link SubtreeSpecification} class. */
@SuppressWarnings("javadoc")
public final class TestSubtreeSpecification extends SubtreeSpecificationTestCase {

  @DataProvider
  public Object[][] valueOfData() {
    return new Object[][] {
      { "{}", "{ }" },
      { "  {    }    ", "{ }" },
      { "{ base \"dc=sun, dc=com\" }",
        "{ base \"dc=sun,dc=com\" }" },
      { "{base \"dc=sun, dc=com\"}",
        "{ base \"dc=sun,dc=com\" }" },
      { "{ base \"dc=sun, dc=com\", specificationFilter item:ds-config-rootDN }",
        "{ base \"dc=sun,dc=com\", specificationFilter item:ds-config-rootDN }" },
      { "{ base \"dc=sun, dc=com\", minimum 0 , maximum 10, "
          + "specificExclusions {chopBefore:\"o=abc\", "
          + "chopAfter:\"o=xyz\"} , specificationFilter not:not:item:foo }",
        "{ base \"dc=sun,dc=com\", "
          + "specificExclusions { chopBefore:\"o=abc\", "
          + "chopAfter:\"o=xyz\" }, maximum 10, specificationFilter "
          + "not:not:item:foo }" },
      { "{ base \"\", minimum 0,maximum 10,"
          + "specificExclusions {chopBefore:\"o=abc\","
          + "chopAfter:\"o=xyz\"},specificationFilter not:not:item:foo}",
        "{ specificExclusions { chopBefore:\"o=abc\", "
          + "chopAfter:\"o=xyz\" }, "
          + "maximum 10, specificationFilter not:not:item:foo }" },
      { "{ specificationFilter and:{item:top, item:person} }",
        "{ specificationFilter and:{item:top, item:person} }" },
      { "{ specificationFilter or:{item:top, item:person} }",
        "{ specificationFilter or:{item:top, item:person} }" },
      { "{ specificationFilter or:{item:top, item:foo, and:{item:one, item:two}} }",
        "{ specificationFilter or:{item:top, item:foo, and:{item:one, item:two}} }" },
      { "{ base \"dc=sun, dc=com\", specificationFilter \"(objectClass=*)\" }",
        "{ base \"dc=sun,dc=com\", specificationFilter \"(objectClass=*)\" }" },
    };
  }

  @Test(dataProvider = "valueOfData")
  public void testValueOf(String specification, String expected) throws Exception {
    SubtreeSpecification ss = SubtreeSpecification.valueOf(DN.rootDN(), specification);
    assertEquals(ss.toString(), expected);
  }

  @DataProvider
  public Object[][] isWithinScopeData() {
    return new Object[][] {
      { "dc=sun, dc=com", "{ base \"dc=sun, dc=com\" }", true },
      { "dc=com", "{ base \"dc=sun, dc=com\" }", false },
      { "dc=foo, dc=sun, dc=com", "{ base \"dc=sun, dc=com\" }", true },
      { "dc=foo, dc=bar, dc=com", "{ base \"dc=sun, dc=com\" }", false },
      { "dc=sun, dc=com", "{ base \"dc=sun, dc=com\", minimum 1 }", false },
      { "dc=abc, dc=sun, dc=com", "{ base \"dc=sun, dc=com\", minimum 1 }", true },
      { "dc=xyz, dc=abc, dc=sun, dc=com", "{ base \"dc=sun, dc=com\", minimum 1 }", true },
      { "dc=sun, dc=com", "{ base \"dc=sun, dc=com\", maximum 0 }", true },
      { "dc=foo, dc=sun, dc=com", "{ base \"dc=sun, dc=com\", maximum 0 }", false },
      { "dc=bar, dc=foo, dc=sun, dc=com", "{ base \"dc=sun, dc=com\", maximum 1 }", false },
      { "dc=bar, dc=foo, dc=sun, dc=com", "{ base \"dc=sun, dc=com\", maximum 2 }", true },
      { "dc=sun, dc=com", "{ base \"dc=sun, dc=com\", specificExclusions { chopAfter:\"\" } }", true },
      { "dc=foo, dc=sun, dc=com",
        "{ base \"dc=sun, dc=com\", specificExclusions { chopAfter:\"\" } }", false },
      { "dc=foo, dc=sun, dc=com",
        "{ base \"dc=sun, dc=com\", specificExclusions { chopAfter:\"dc=foo\" } }", true },
      { "dc=bar, dc=foo, dc=sun, dc=com",
        "{ base \"dc=sun, dc=com\", specificExclusions { chopAfter:\"dc=foo\" } }", false },
      { "dc=foo, dc=sun, dc=com",
        "{ base \"dc=sun, dc=com\", specificExclusions { chopBefore:\"dc=foo\" } }", false },
      { "dc=bar, dc=foo, dc=sun, dc=com",
        "{ base \"dc=sun, dc=com\", specificExclusions { chopBefore:\"dc=foo\" } }", false },
      { "dc=abc, dc=sun, dc=com",
        "{ base \"dc=sun, dc=com\", specificExclusions { chopBefore:\"dc=foo\" } }", true },
      { "dc=abc, dc=sun, dc=com",
        "{ base \"dc=sun, dc=com\", specificationFilter item:person }", true },
      { "dc=abc, dc=sun, dc=com",
        "{ base \"dc=sun, dc=com\", specificationFilter item:organization }", false },
      { "dc=abc, dc=sun, dc=com",
        "{ base \"dc=sun, dc=com\", specificationFilter not:item:person }", false },
      { "dc=abc, dc=sun, dc=com",
        "{ base \"dc=sun, dc=com\", specificationFilter not:item:organization }", true },
      { "dc=abc, dc=sun, dc=com",
        "{ base \"dc=sun, dc=com\", specificationFilter \"(objectClass=person)\" }", true },
      { "dc=abc, dc=sun, dc=com",
        "{ base \"dc=sun, dc=com\", specificationFilter \"(objectClass=organization)\" }", false },
    };
  }

  /** Tests the {@link SubtreeSpecification#isWithinScope(Entry)} method. */
  @Test(dataProvider = "isWithinScopeData")
  public void testIsWithinScope(String dnString, String value, boolean expected) throws Exception {
    DN dn = DN.valueOf(dnString);
    SubtreeSpecification ss = SubtreeSpecification.valueOf(DN.rootDN(), value);
    assertEquals(ss.isWithinScope(createEntry(dn, getObjectClasses())), expected);
  }
}
