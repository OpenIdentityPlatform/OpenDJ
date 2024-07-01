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
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.core;

import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.opends.server.util.CollectionUtils.*;

import java.util.HashMap;
import java.util.Set;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.opends.server.TestCaseUtils;
import org.opends.server.types.Entry;
import org.testng.annotations.BeforeClass;

/** An abstract base class for all subtree specification tests. */
public abstract class SubtreeSpecificationTestCase extends CoreTestCase {
  /** Cached set of entry object classes. */
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
  protected final Entry createEntry(DN entryDN, Set<ObjectClass> objectClasses)
  {
    HashMap<ObjectClass, String> map = new HashMap<>();

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
  @BeforeClass
  public final void setUp() throws Exception {
    // This test suite depends on having the schema available, so we'll start the server.
    TestCaseUtils.startServer();

    objectClasses = newHashSet(getTopObjectClass(), getPersonObjectClass());
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
