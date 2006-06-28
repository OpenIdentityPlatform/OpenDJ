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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */

package org.opends.server.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.opends.server.SchemaFixture;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ObjectClass;
import org.testng.annotations.Configuration;

/**
 * An abstract base class for all subtree specification tests.
 */
public abstract class SubtreeSpecificationTestCase extends CoreTestCase {
  // Cached set of entry object classes.
  private Set<ObjectClass> objectClasses;

  /**
   * Create a filterable entry from a DN and set of object classes. It
   * will not contain any attributes.
   *
   * @param entryDN
   *          The entry's DN.
   * @param objectClasses
   *          The entry's object classes.
   * @return The created entry.
   */
  protected final Entry createEntry(DN entryDN,
      Set<ObjectClass> objectClasses) {
    HashMap<ObjectClass, String> map = new HashMap<ObjectClass, String>();

    for (ObjectClass oc : objectClasses) {
      if (oc != null) {
        map.put(oc, oc.getNameOrOID());
      }
    }

    return new Entry(entryDN, map, null, null);
  }

  /**
   * Set up the environment for performing the tests in this suite.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @Configuration(beforeTestClass = true)
  public final void setUp() throws Exception {
    // This test suite depends on having the schema available.
    SchemaFixture.FACTORY.setUp();

    // Retrieve required object classes.
    objectClasses = new HashSet<ObjectClass>();

    ObjectClass oc = DirectoryServer.getObjectClass("top");
    if (oc == null) {
      throw new RuntimeException("Unable to resolve object class top");
    }
    objectClasses.add(oc);

    oc = DirectoryServer.getObjectClass("person");
    if (oc == null) {
      throw new RuntimeException("Unable to resolve object class person");
    }
    objectClasses.add(oc);
  }

  /**
   * Tears down the environment for performing the tests in this suite.
   *
   * @throws Exception
   *           If the environment could not be finalized.
   */
  @Configuration(afterTestClass = true)
  public final void tearDown() throws Exception {
    SchemaFixture.FACTORY.tearDown();
  }

  /**
   * Get the common object classes.
   *
   * @return Returns the object classes.
   */
  protected final Set<ObjectClass> getObjectClasses() {
    return objectClasses;
  }
}
