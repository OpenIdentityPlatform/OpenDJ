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

import static org.assertj.core.api.Assertions.*;

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
    assertThat(BackendStat.nearLimit(79, 100)).isFalse();
    assertThat(BackendStat.nearLimit(80, 100)).isTrue();
  }

  /** An index-entry-limit of 0 is no limit at all, and no key is near it (#1059). */
  @Test
  public void testNoKeyIsNearNoLimit()
  {
    assertThat(BackendStat.nearLimit(1, 0)).isFalse();
    assertThat(BackendStat.nearLimit(Integer.MAX_VALUE, 0)).isFalse();
  }
}
