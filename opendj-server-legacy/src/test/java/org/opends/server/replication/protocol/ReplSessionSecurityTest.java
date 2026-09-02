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
package org.opends.server.replication.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.opends.server.replication.protocol.ReplSessionSecurity.HANDSHAKE_FAILURE_WARN_INTERVAL_NANOS;

import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.Test;

/** Tests for {@link ReplSessionSecurity}. */
@SuppressWarnings("javadoc")
public class ReplSessionSecurityTest extends DirectoryServerTestCase
{
  /**
   * Any connection which is not a replication peer fails the handshake on the replication
   * port, and a data server which cannot present its certificate reconnects every 500 ms,
   * so only the first failure of an interval may be logged as a warning.
   */
  @Test
  public void handshakeFailuresAreWarnedAboutOncePerInterval() throws Exception
  {
    final ReplSessionSecurity security = new ReplSessionSecurity(null, null, null, true);
    final long start = System.nanoTime();

    assertThat(security.recordHandshakeFailure(start))
        .as("the first failure is warned about, and stands for itself alone").isEqualTo(0);
    assertThat(security.recordHandshakeFailure(start + 1))
        .as("the next failure is the first one logged at debug level").isEqualTo(-2);
    assertThat(security.recordHandshakeFailure(start + HANDSHAKE_FAILURE_WARN_INTERVAL_NANOS - 1))
        .as("the following one is the second").isEqualTo(-3);

    assertThat(security.recordHandshakeFailure(start + HANDSHAKE_FAILURE_WARN_INTERVAL_NANOS))
        .as("the next warning counts the failures logged at debug level before it").isEqualTo(2);
    assertThat(security.recordHandshakeFailure(start + 2 * HANDSHAKE_FAILURE_WARN_INTERVAL_NANOS))
        .as("the count starts again from the previous warning").isEqualTo(0);
  }
}
