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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import static org.opends.server.util.CollectionUtils.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ModificationType;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.ModifyContext;
import org.opends.server.replication.protocol.OperationContext;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.operation.PreOperationModifyOperation;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests the single valued attribute conflict resolution (part of modify replay).
 * <p>
 * It produces series of changes and replay them out of order by generating all possible
 * permutations. The goal is to end up in the same final state whatever the order.
 * <p>
 * The tests are built this way:
 * <ol>
 * <li>Start from an entry without (resp. with) an initial value for the targeted single valued
 * attribute</li>
 * <li>Replay a "seed" modify operation, that happened at time t1</li>
 * <li>Then replay any of all the possible operations that could come after, that happened at time
 * t2</li>
 * </ol>
 * All permutations for these sequence of operations are tested.
 * <p>
 * The test finally asserts the {@code ds-sync-hist} attribute always end in the same state whatever
 * the permutation.
 */
@SuppressWarnings("javadoc")
public class ModifyReplaySingleValuedAttributeTest extends ReplicationTestCase
{
  private static final String ATTRIBUTE_NAME = "displayName";
  private static final String SYNCHIST = "ds-sync-hist";

  private Entry entry;

  private static class Mod
  {
    private final int time;
    private final Modification modification;

    private Mod(ModificationType modType, int t)
    {
      this(modType, null, t);
    }

    private Mod(ModificationType modType, String value, int t)
    {
      this.modification = newModification(modType, value);
      this.time = t;
    }

    private PreOperationModifyOperation toOperation()
    {
      final ModifyContext value = new ModifyContext(new CSN(0, time, 0), null);

      PreOperationModifyOperation op = mock(PreOperationModifyOperation.class);
      when(op.getModifications()).thenReturn(newArrayList(modification));
      when(op.getAttachment(eq(OperationContext.SYNCHROCONTEXT))).thenReturn(value);
      return op;
    }

    /** Implemented to get a nice display for each tests in Eclipse UI. */
    @Override
    public String toString()
    {
      String modType = modification.getModificationType().toString().toUpperCase();
      Iterator<ByteString> it = modification.getAttribute().iterator();
      String attrValue = it.hasNext() ? "\"" + it.next().toString() + "\" " : "";
      return modType + " " + attrValue + "t" + time;
    }
  }

  private static Object[][] generatePermutations(Object[][] scenarios)
  {
    List<Object[]> results = new ArrayList<Object[]>();
    for (Object[] scenario : scenarios)
    {
      generate((Object[]) scenario[0], (Attribute) scenario[1], results);
    }

    return results.toArray(new Object[results.size()][]);
  }

  private static void generate(Object[] array, Attribute dsSyncHist, List<Object[]> results)
  {
    generate(array.length, array, dsSyncHist, results);
  }

  private static void generate(int n, Object[] array, Attribute dsSyncHist, List<Object[]> results)
  {
    if (n == 1)
    {
      results.add(new Object[] { Arrays.asList(Arrays.copyOf(array, array.length)), dsSyncHist, });
      return;
    }

    for (int i = 0; i < n - 1; i += 1)
    {
      generate(n - 1, array, dsSyncHist, results);
      if (n % 2 == 0)
      {
        swap(array, i, n - 1);
      }
      else
      {
        swap(array, 0, n - 1);
      }
    }
    generate(n - 1, array, dsSyncHist, results);
  }

  private static <E> void swap(E[] array, int i, int j)
  {
    E tmp = array[i];
    array[i] = array[j];
    array[j] = tmp;
  }

  @DataProvider
  public Object[][] add_data()
  {
    // @formatter:off
    return generatePermutations(new Object[][] {
      { mods(new Mod(ADD, "X", 1)),                                 dsSyncHist(1, ":add:X"), },
      { mods(new Mod(ADD, "X", 1), new Mod(ADD, "X", 2)),           dsSyncHist(2, ":add:X"), },
      { mods(new Mod(ADD, "X", 1), new Mod(ADD, "Y", 2)),           dsSyncHist(2, ":add:Y"), },
      { mods(new Mod(ADD, "X", 1), new Mod(DELETE, "X", 2)),        dsSyncHist(2, ":attrDel"), },
      { mods(new Mod(ADD, "X", 1), new Mod(DELETE, "Y", 2)),        dsSyncHist(1, ":add:X"), },
      { mods(new Mod(ADD, "X", 1), new Mod(DELETE, 2)),             dsSyncHist(2, ":attrDel"), },
      { mods(new Mod(ADD, "X", 1), new Mod(REPLACE, "X", 2)),       dsSyncHist(2, ":repl:X"), },
      { mods(new Mod(ADD, "X", 1), new Mod(REPLACE, "Y", 2)),       dsSyncHist(2, ":repl:Y"), },
      { mods(new Mod(ADD, "X", 1), new Mod(REPLACE, 2)),            dsSyncHist(2, ":attrDel"), }
    });
    // @formatter:on
  }

  @DataProvider
  public Object[][] delete_noInitialValue_data()
  {
    // @formatter:off
    return generatePermutations(new Object[][] {
        { mods(new Mod(DELETE, "X", 1)),                            dsSyncHist(1, ":attrDel"), },
        { mods(new Mod(DELETE, "X", 1), new Mod(ADD, "X", 2)),      dsSyncHist(2, ":add:X"), },
        { mods(new Mod(DELETE, "X", 1), new Mod(ADD, "Y", 2)),      dsSyncHist(2, ":add:Y"), },
        { mods(new Mod(DELETE, "X", 1), new Mod(DELETE, "X", 2)),   dsSyncHist(2, ":attrDel"), },
        { mods(new Mod(DELETE, "X", 1), new Mod(DELETE, "Y", 2)),   dsSyncHist(2, ":attrDel"), },
        { mods(new Mod(DELETE, "X", 1), new Mod(DELETE, 2)),        dsSyncHist(2, ":attrDel"), },
        { mods(new Mod(DELETE, "X", 1), new Mod(REPLACE, "X", 2)),  dsSyncHist(2, ":repl:X"), },
        { mods(new Mod(DELETE, "X", 1), new Mod(REPLACE, "Y", 2)),  dsSyncHist(2, ":repl:Y"), },
        { mods(new Mod(DELETE, "X", 1), new Mod(REPLACE, 2)),       dsSyncHist(2, ":attrDel"), },

        { mods(new Mod(DELETE, 1)),                                 dsSyncHist(1, ":attrDel"), },
        { mods(new Mod(DELETE, 1), new Mod(ADD, "X", 2)),           dsSyncHist(2, ":add:X"), },
        { mods(new Mod(DELETE, 1), new Mod(ADD, "Y", 2)),           dsSyncHist(2, ":add:Y"), },
        { mods(new Mod(DELETE, 1), new Mod(DELETE, "X", 2)),        dsSyncHist(2, ":attrDel"), },
        { mods(new Mod(DELETE, 1), new Mod(DELETE, "Y", 2)),        dsSyncHist(2, ":attrDel"), },
        { mods(new Mod(DELETE, 1), new Mod(DELETE, 2)),             dsSyncHist(2, ":attrDel"), },
        { mods(new Mod(DELETE, 1), new Mod(REPLACE, "X", 2)),       dsSyncHist(2, ":repl:X"), },
        { mods(new Mod(DELETE, 1), new Mod(REPLACE, "Y", 2)),       dsSyncHist(2, ":repl:Y"), },
        { mods(new Mod(DELETE, 1), new Mod(REPLACE, 2)),            dsSyncHist(2, ":attrDel"), },
    });
    // @formatter:on
  }

  @DataProvider
  public Object[][] delete_initialValueX_data()
  {
    // @formatter:off
    return generatePermutations(new Object[][] {
        { mods(new Mod(DELETE, "X", 1)),                            dsSyncHist(1, ":attrDel"), },
        { mods(new Mod(DELETE, "X", 1), new Mod(ADD, "X", 2)),      dsSyncHist(2, ":add:X"), },
        { mods(new Mod(DELETE, "X", 1), new Mod(ADD, "Y", 2)),      dsSyncHist(2, ":add:Y"), },
        { mods(new Mod(DELETE, "X", 1), new Mod(DELETE, "X", 2)),   dsSyncHist(2, ":attrDel"), },
        { mods(new Mod(DELETE, "X", 1), new Mod(DELETE, "Y", 2)),   dsSyncHist(2, ":attrDel"), },
        { mods(new Mod(DELETE, "X", 1), new Mod(DELETE, 2)),        dsSyncHist(2, ":attrDel"), },
        { mods(new Mod(DELETE, "X", 1), new Mod(REPLACE, "X", 2)),  dsSyncHist(2, ":repl:X"), },
        { mods(new Mod(DELETE, "X", 1), new Mod(REPLACE, "Y", 2)),  dsSyncHist(2, ":repl:Y"), },
        { mods(new Mod(DELETE, "X", 1), new Mod(REPLACE, 2)),       dsSyncHist(2, ":attrDel"), },

        { mods(new Mod(DELETE, "Y", 1)),                            dsSyncHist(1, ":add:X"), },
        { mods(new Mod(DELETE, "Y", 1), new Mod(ADD, "X", 2)),      dsSyncHist(1, ":add:X"), },
        { mods(new Mod(DELETE, "Y", 1), new Mod(ADD, "Y", 2)),      dsSyncHist(1, ":add:X"), },
        { mods(new Mod(DELETE, "Y", 1), new Mod(DELETE, "X", 2)),   dsSyncHist(1, ":add:X"), },
        { mods(new Mod(DELETE, "Y", 1), new Mod(DELETE, "Y", 2)),   dsSyncHist(1, ":add:X"), },
        { mods(new Mod(DELETE, "Y", 1), new Mod(DELETE, 2)),        dsSyncHist(2, ":attrDel"), },
        { mods(new Mod(DELETE, "Y", 1), new Mod(REPLACE, "X", 2)),  dsSyncHist(1, ":add:X"), },
        { mods(new Mod(DELETE, "Y", 1), new Mod(REPLACE, "Y", 2)),  dsSyncHist(1, ":add:X"), },
        { mods(new Mod(DELETE, "Y", 1), new Mod(REPLACE, 2)),       dsSyncHist(2, ":attrDel"), },

        { mods(new Mod(DELETE, 1)),                                 dsSyncHist(1, ":attrDel"), },
        { mods(new Mod(DELETE, 1), new Mod(ADD, "X", 2)),           dsSyncHist(2, ":add:X"), },
        { mods(new Mod(DELETE, 1), new Mod(ADD, "Y", 2)),           dsSyncHist(2, ":add:Y"), },
        { mods(new Mod(DELETE, 1), new Mod(DELETE, "X", 2)),        dsSyncHist(2, ":attrDel"), },
        { mods(new Mod(DELETE, 1), new Mod(DELETE, "Y", 2)),        dsSyncHist(2, ":attrDel"), },
        { mods(new Mod(DELETE, 1), new Mod(DELETE, 2)),             dsSyncHist(2, ":attrDel"), },
        { mods(new Mod(DELETE, 1), new Mod(REPLACE, "X", 2)),       dsSyncHist(2, ":repl:X"), },
        { mods(new Mod(DELETE, 1), new Mod(REPLACE, "Y", 2)),       dsSyncHist(2, ":repl:Y"), },
        { mods(new Mod(DELETE, 1), new Mod(REPLACE, 2)),            dsSyncHist(2, ":attrDel"), },
    });
    // @formatter:on
  }

  @DataProvider
  public Object[][] replace_noInitialValue_data()
  {
    // @formatter:off
    return generatePermutations(new Object[][] {
        { mods(new Mod(REPLACE, "X", 1)),                           dsSyncHist(1, ":repl:X"), },
        { mods(new Mod(REPLACE, "X", 1), new Mod(ADD, "X", 2)),     dsSyncHist(2, ":add:X"), },
        { mods(new Mod(REPLACE, "X", 1), new Mod(ADD, "Y", 2)),     dsSyncHist(2, ":add:Y"), },
        { mods(new Mod(REPLACE, "X", 1), new Mod(DELETE, "X", 2)),  dsSyncHist(2, ":attrDel"), },
        { mods(new Mod(REPLACE, "X", 1), new Mod(DELETE, "Y", 2)),  dsSyncHist(1, ":repl:X"), },
        { mods(new Mod(REPLACE, "X", 1), new Mod(DELETE, 2)),       dsSyncHist(2, ":attrDel"), },
        { mods(new Mod(REPLACE, "X", 1), new Mod(REPLACE, "X", 2)), dsSyncHist(2, ":repl:X"), },
        { mods(new Mod(REPLACE, "X", 1), new Mod(REPLACE, "Y", 2)), dsSyncHist(2, ":repl:Y"), },
        { mods(new Mod(REPLACE, "X", 1), new Mod(REPLACE, 2)),      dsSyncHist(2, ":attrDel"), },

        { mods(new Mod(REPLACE, 1)),                                dsSyncHist(1, ":attrDel"), },
        { mods(new Mod(REPLACE, 1), new Mod(ADD, "X", 2)),          dsSyncHist(2, ":add:X"), },
        { mods(new Mod(REPLACE, 1), new Mod(ADD, "Y", 2)),          dsSyncHist(2, ":add:Y"), },
        { mods(new Mod(REPLACE, 1), new Mod(DELETE, "X", 2)),       dsSyncHist(1, ":add:X"), },
        { mods(new Mod(REPLACE, 1), new Mod(DELETE, "Y", 2)),       dsSyncHist(1, ":add:X"), },
        { mods(new Mod(REPLACE, 1), new Mod(DELETE, 2)),            dsSyncHist(2, ":attrDel"), },
        { mods(new Mod(REPLACE, 1), new Mod(REPLACE, "X", 2)),      dsSyncHist(2, ":repl:X"), },
        { mods(new Mod(REPLACE, 1), new Mod(REPLACE, "Y", 2)),      dsSyncHist(2, ":repl:Y"), },
        { mods(new Mod(REPLACE, 1), new Mod(REPLACE, 2)),           dsSyncHist(2, ":attrDel"), },
    });
    // @formatter:on
  }

  @DataProvider
  public Object[][] replace_initialValueX_data()
  {
    // @formatter:off
    return generatePermutations(new Object[][] {
        { mods(new Mod(REPLACE, "X", 1)),                           dsSyncHist(1, ":repl:X"), },
        { mods(new Mod(REPLACE, "X", 1), new Mod(ADD, "X", 2)),     dsSyncHist(2, ":add:X"), },
        { mods(new Mod(REPLACE, "X", 1), new Mod(ADD, "Y", 2)),     dsSyncHist(2, ":add:Y"), },
        { mods(new Mod(REPLACE, "X", 1), new Mod(DELETE, "X", 2)),  dsSyncHist(2, ":attrDel"), },
        { mods(new Mod(REPLACE, "X", 1), new Mod(DELETE, "Y", 2)),  dsSyncHist(1, ":add:X"), },
        { mods(new Mod(REPLACE, "X", 1), new Mod(DELETE, 2)),       dsSyncHist(2, ":attrDel"), },
        { mods(new Mod(REPLACE, "X", 1), new Mod(REPLACE, "X", 2)), dsSyncHist(2, ":repl:X"), },
        { mods(new Mod(REPLACE, "X", 1), new Mod(REPLACE, "Y", 2)), dsSyncHist(2, ":repl:Y"), },
        { mods(new Mod(REPLACE, "X", 1), new Mod(REPLACE, 2)),      dsSyncHist(2, ":attrDel"), },

        { mods(new Mod(REPLACE, "Y", 1)),                           dsSyncHist(1, ":repl:Y"), },
        { mods(new Mod(REPLACE, "Y", 1), new Mod(ADD, "X", 2)),     dsSyncHist(2, ":add:X"), },
        { mods(new Mod(REPLACE, "Y", 1), new Mod(ADD, "Y", 2)),     dsSyncHist(2, ":add:Y"), },
        { mods(new Mod(REPLACE, "Y", 1), new Mod(DELETE, "X", 2)),  dsSyncHist(1, ":repl:Y"), },
        { mods(new Mod(REPLACE, "Y", 1), new Mod(DELETE, "Y", 2)),  dsSyncHist(2, ":attrDel"), },
        { mods(new Mod(REPLACE, "Y", 1), new Mod(DELETE, 2)),       dsSyncHist(2, ":attrDel"), },
        { mods(new Mod(REPLACE, "Y", 1), new Mod(REPLACE, "X", 2)), dsSyncHist(2, ":repl:X"), },
        { mods(new Mod(REPLACE, "Y", 1), new Mod(REPLACE, "Y", 2)), dsSyncHist(2, ":repl:Y"), },
        { mods(new Mod(REPLACE, "Y", 1), new Mod(REPLACE, 2)),      dsSyncHist(2, ":attrDel"), },

        { mods(new Mod(REPLACE, 1)),                                dsSyncHist(1, ":attrDel"), },
        { mods(new Mod(REPLACE, 1), new Mod(ADD, "X", 2)),          dsSyncHist(2, ":add:X"), },
        { mods(new Mod(REPLACE, 1), new Mod(ADD, "Y", 2)),          dsSyncHist(2, ":add:Y"), },
        { mods(new Mod(REPLACE, 1), new Mod(DELETE, "X", 2)),       dsSyncHist(1, ":attrDel"), },
        { mods(new Mod(REPLACE, 1), new Mod(DELETE, "Y", 2)),       dsSyncHist(1, ":attrDel"), },
        { mods(new Mod(REPLACE, 1), new Mod(DELETE, 2)),            dsSyncHist(2, ":attrDel"), },
        { mods(new Mod(REPLACE, 1), new Mod(REPLACE, "X", 2)),      dsSyncHist(2, ":repl:X"), },
        { mods(new Mod(REPLACE, 1), new Mod(REPLACE, "Y", 2)),      dsSyncHist(2, ":repl:Y"), },
        { mods(new Mod(REPLACE, 1), new Mod(REPLACE, 2)),           dsSyncHist(2, ":attrDel"), },
    });
    // @formatter:on
  }

  @Test(dataProvider = "add_data", enabled = false)
  public void add_noInitialValue(List<Mod> mods, Attribute expectedDsSyncHist) throws Exception
  {
    noValue(); // also covers the initialValue("X"); case
    replay(mods, expectedDsSyncHist);
  }

  @Test(dataProvider = "delete_noInitialValue_data", enabled = false)
  public void delete_noInitialValue(List<Mod> mods, Attribute expectedDsSyncHist) throws Exception
  {
    noValue();
    replay(mods, expectedDsSyncHist);
  }

  @Test(dataProvider = "delete_initialValueX_data", enabled = false)
  public void delete_initialValueX(List<Mod> mods, Attribute expectedDsSyncHist) throws Exception
  {
    initialValue("X");
    replay(mods, expectedDsSyncHist);
  }

  @Test(dataProvider = "replace_noInitialValue_data", enabled = false)
  public void replace_noInitialValue(List<Mod> mods, Attribute expectedDsSyncHist) throws Exception
  {
    noValue();
    replay(mods, expectedDsSyncHist);
  }

  @Test(dataProvider = "replace_initialValueX_data", enabled = false)
  public void replace_initialValueX(List<Mod> mods, Attribute expectedDsSyncHist) throws Exception
  {
    initialValue("X");
    replay(mods, expectedDsSyncHist);
  }

  @Test(enabled = false)
  public void diffEntries_addThenDel() throws Exception
  {
    initialValue("X");

    replaySameTime(newArrayList(newModification(ADD, "Y"), newModification(DELETE, "X")), dsSyncHist(1, ":add:Y"));
  }

  @Test(enabled = false)
  public void diffEntries_delThenAdd() throws Exception
  {
    initialValue("X");

    replaySameTime(newArrayList(newModification(DELETE, "X"), newModification(ADD, "Y")), dsSyncHist(1, ":add:Y"));
  }

  private void replaySameTime(List<Modification> mods, Attribute expectedDsSyncHist) throws DirectoryException
  {
    final ModifyContext value = new ModifyContext(new CSN(0, 1, 0), null);

    PreOperationModifyOperation op = mock(PreOperationModifyOperation.class);
    when(op.getModifications()).thenReturn(mods);
    when(op.getAttachment(eq(OperationContext.SYNCHROCONTEXT))).thenReturn(value);

    EntryHistorical entryHistorical = EntryHistorical.newInstanceFromEntry(entry);
    entryHistorical.replayOperation(op, entry);
    entry.applyModification(new Modification(REPLACE, entryHistorical.encodeAndPurge()));

    Attribute actual = entry.getAttribute(expectedDsSyncHist.getAttributeDescription());
    Assert.assertEquals(actual, expectedDsSyncHist, "wrong final value for ds-sync-hist attribute");
  }

  private void noValue() throws Exception
  {
    // @formatter:off
    entry = TestCaseUtils.makeEntry(
        "dn: uid=test.user",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User",
        "userPassword: password");
    // @formatter:on
  }

  private void initialValue(String attrValue) throws Exception
  {
    noValue();
    entry.applyModification(newModification(REPLACE, attrValue));
  }

  private void replay(List<Mod> mods, Attribute expectedDsSyncHist) throws DirectoryException
  {
    for (Mod op : mods)
    {
      EntryHistorical entryHistorical = EntryHistorical.newInstanceFromEntry(entry);
      entryHistorical.replayOperation(op.toOperation(), entry);
      entry.applyModification(new Modification(REPLACE, entryHistorical.encodeAndPurge()));
    }

    Attribute actual = entry.getAttribute(expectedDsSyncHist.getAttributeDescription());
    Assert.assertEquals(actual, expectedDsSyncHist, "wrong final value for ds-sync-hist attribute");
  }

  private static Object[] mods(Mod... mods)
  {
    return mods;
  }

  private static Attribute dsSyncHist(int t, String partialDsSyncHist)
  {
    String value = ATTRIBUTE_NAME + ":000000000000000000000000000" + t + partialDsSyncHist;
    return Attributes.create(SYNCHIST, value);
  }

  private static Modification newModification(ModificationType modType, String value)
  {
    Attribute attr = value != null ? Attributes.create(ATTRIBUTE_NAME, value) : Attributes.empty(ATTRIBUTE_NAME);
    return new Modification(modType, attr);
  }
}
