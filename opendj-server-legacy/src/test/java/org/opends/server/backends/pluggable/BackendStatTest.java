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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.backends.pluggable;

import static com.forgerock.opendj.cli.ArgumentParser.PROPERTY_SCRIPT_NAME;
import static org.assertj.core.api.Assertions.*;
import static org.opends.server.backends.pluggable.BackendStat.*;

import java.io.ByteArrayOutputStream;

import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.Test;

/** The figures {@code backendstat show-index-status} works out for each key of an index. */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "pluggablebackend", "unit" }, sequential = true)
public class BackendStatTest extends DirectoryServerTestCase
{
  /** A key is reported as near its limit from 80% of the limit on. */
  @Test
  public void testAKeyIsNearItsLimitFromEightyPercentOn()
  {
    assertThat(nearLimitColumn(79, 100)).isEqualTo(-1);
    assertThat(nearLimitColumn(80, 100)).isNotEqualTo(-1);
  }

  /** An index-entry-limit of 0 is no limit at all, and no key is near it (#1059). */
  @Test
  public void testNoKeyIsNearNoLimit()
  {
    assertThat(nearLimitColumn(1, 0)).isEqualTo(-1);
    assertThat(nearLimitColumn(Integer.MAX_VALUE, 0)).isEqualTo(-1);
  }

  /** The columns are headed 95%, 90% and 80%: the last one counts keys from 80% of the limit (#1135). */
  @Test
  public void testTheColumnsAreHeadedByTheirThresholds()
  {
    assertThat(NEAR_LIMIT_PERCENTS).containsExactly(95, 90, 80);
    assertThat(NEAR_LIMIT_PERCENTS[nearLimitColumn(80, 100)]).isEqualTo(80);
    assertThat(NEAR_LIMIT_PERCENTS[nearLimitColumn(89, 100)]).isEqualTo(80);
    assertThat(NEAR_LIMIT_PERCENTS[nearLimitColumn(90, 100)]).isEqualTo(90);
    assertThat(NEAR_LIMIT_PERCENTS[nearLimitColumn(94, 100)]).isEqualTo(90);
    assertThat(NEAR_LIMIT_PERCENTS[nearLimitColumn(95, 100)]).isEqualTo(95);
    assertThat(NEAR_LIMIT_PERCENTS[nearLimitColumn(100, 100)]).isEqualTo(95);
  }

  /**
   * Every key a column counts holds at least the percentage of the limit that heads the column, and
   * less than the percentage heading the column before it.
   */
  @Test
  public void testEachColumnCountsTheKeysFromItsHeadingUpToThePreviousOne()
  {
    for (long entryLimit : new long[] { 1, 7, 100, 4000, 4001 })
    {
      for (long size = 0; size <= entryLimit; size++)
      {
        int column = nearLimitColumn(size, entryLimit);
        boolean nearLimit = size * 100 >= entryLimit * NEAR_LIMIT_PERCENTS[NEAR_LIMIT_PERCENTS.length - 1];
        assertThat(column >= 0).as("size %d of %d", size, entryLimit).isEqualTo(nearLimit);
        if (column >= 0)
        {
          assertThat(size * 100).as("size %d of %d", size, entryLimit)
              .isGreaterThanOrEqualTo(entryLimit * NEAR_LIMIT_PERCENTS[column]);
          if (column > 0)
          {
            assertThat(size * 100).as("size %d of %d", size, entryLimit)
                .isLessThan(entryLimit * NEAR_LIMIT_PERCENTS[column - 1]);
          }
        }
      }
    }
  }

  /** The generated reference of show-index-status includes its AsciiDoc description of the columns (#1128). */
  @Test
  public void testGenerateDocIncludesTheIndexStatusSupplement() throws Exception
  {
    final String scriptName = System.getProperty(PROPERTY_SCRIPT_NAME);
    System.setProperty("org.forgerock.opendj.gendoc", "true");
    System.setProperty(PROPERTY_SCRIPT_NAME, "backendstat");
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try
    {
      assertThat(BackendStat.main(new String[] { "-?" }, out, System.err)).isEqualTo(0);
    }
    finally
    {
      System.clearProperty("org.forgerock.opendj.gendoc");
      restoreProperty(PROPERTY_SCRIPT_NAME, scriptName);
    }
    assertThat(out.toString("UTF-8"))
        .contains("include::./_variablelist-backendstat-index-status.adoc[]")
        .doesNotContain("<xinclude:include");
  }

  private static void restoreProperty(String name, String value)
  {
    if (value != null)
    {
      System.setProperty(name, value);
    }
    else
    {
      System.clearProperty(name);
    }
  }
}
