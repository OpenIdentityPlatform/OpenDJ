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
 * Copyright 2026 3A Systems, LLC
 */
package org.opends.server.authorization.dseecompat;

import static org.testng.Assert.*;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.types.Entry;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Locks in the copy-on-write contract of {@link AciList}: readers snapshot
 * the map through the volatile reference without locking, so mutators must
 * never modify a published map or its value lists in place.
 */
@SuppressWarnings("javadoc")
public class AciListTests extends DirectoryServerTestCase
{
  @BeforeClass
  public void setUp() throws Exception
  {
    // The full server schema is needed for "aci" to be an operational
    // attribute, which the Entry-based append path relies on.
    TestCaseUtils.startServer();
  }

  @Test(timeOut = 60000)
  public void testCandidateSnapshotsUnderConcurrentMutation() throws Exception
  {
    final DN aciDN = DN.valueOf("dc=example,dc=com");
    final DN queryDN = DN.valueOf("uid=user.0,ou=People,dc=example,dc=com");
    // Off the query path: churned to force structural map changes.
    final DN movingDnA = DN.valueOf("ou=A,dc=example,dc=com");
    final DN movingDnB = DN.valueOf("ou=B,dc=example,dc=com");
    final AciList aciList = new AciList(DN.valueOf("cn=Access Control Handler,cn=config"));

    final Aci aci1 = Aci.decode(ByteString.valueOfUtf8(
        "(version 3.0; acl \"cow test 1\"; allow(all) userdn=\"ldap:///anyone\";)"), aciDN);
    final Aci aci2 = Aci.decode(ByteString.valueOfUtf8(
        "(version 3.0; acl \"cow test 2\"; allow(read) userdn=\"ldap:///all\";)"), aciDN);
    final SortedSet<Aci> oneAci = new TreeSet<>();
    oneAci.add(aci1);
    final SortedSet<Aci> twoAcis = new TreeSet<>();
    twoAcis.add(aci1);
    twoAcis.add(aci2);

    // Entry whose "aci" attribute is APPENDED to the existing list under
    // aciDN by addAci(List<Entry>, ...): with copy-on-write the append goes
    // to a fresh copy; an in-place append would mutate the published list
    // under the readers' fail-fast iterators.
    final Entry appendEntry = TestCaseUtils.makeEntry(
        "dn: " + aciDN,
        "objectClass: top",
        "objectClass: domain",
        "dc: example",
        "aci: (version 3.0; acl \"cow test 2\"; allow(read) userdn=\"ldap:///all\";)");

    aciList.addAci(aciDN, oneAci);
    aciList.addAci(movingDnA, oneAci);

    // Sanity-check the append channel before relying on it for churn.
    final LinkedList<LocalizableMessage> failedACIMsgs = new LinkedList<>();
    aciList.addAci(Collections.singletonList(appendEntry), failedACIMsgs);
    assertTrue(failedACIMsgs.isEmpty(), String.valueOf(failedACIMsgs));
    assertEquals(aciList.getCandidateAcis(queryDN).size(), 2,
        "addAci(List<Entry>) must append to the list under aciDN");
    aciList.addAci(aciDN, oneAci);

    final AtomicBoolean done = new AtomicBoolean();
    final AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread[] readers = new Thread[3];
    for (int i = 0; i < readers.length; i++)
    {
      readers[i] = new Thread("AciList COW reader " + i)
      {
        @Override
        public void run()
        {
          try
          {
            while (!done.get())
            {
              // Iterates the published value lists: an in-place mutator
              // would make this throw or return a torn snapshot.
              List<Aci> candidates = aciList.getCandidateAcis(queryDN);
              int size = candidates.size();
              if (size != 1 && size != 2)
              {
                throw new AssertionError("Torn candidate snapshot: " + candidates);
              }
            }
          }
          catch (Throwable t)
          {
            failure.compareAndSet(null, t);
          }
        }
      };
      readers[i].start();
    }

    try
    {
      for (int i = 0; i < 50000 && failure.get() == null; i++)
      {
        // Append a second ACI to the list under aciDN, then reset to one.
        aciList.addAci(Collections.singletonList(appendEntry), failedACIMsgs);
        aciList.addAci(aciDN, oneAci);
        // Structural churn off the query path: rename moves the key
        // through iterator.remove() and putAll() on every iteration.
        if (i % 2 == 0)
        {
          aciList.renameAci(movingDnA, movingDnB);
        }
        else
        {
          aciList.renameAci(movingDnB, movingDnA);
        }
        assertTrue(failedACIMsgs.isEmpty(), String.valueOf(failedACIMsgs));
      }
    }
    finally
    {
      done.set(true);
      for (Thread reader : readers)
      {
        reader.join(10000);
      }
    }
    if (failure.get() != null)
    {
      fail("Reader failed under concurrent ACI mutation", failure.get());
    }
  }
}
