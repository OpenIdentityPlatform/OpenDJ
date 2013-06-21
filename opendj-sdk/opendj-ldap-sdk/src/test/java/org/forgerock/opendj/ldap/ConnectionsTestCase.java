/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2013 ForgeRock AS
 */
package org.forgerock.opendj.ldap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ConnectionsTestCase extends SdkTestCase {

    @Test
    public void testUncloseableConnectionClose() throws Exception {
        final Connection connection = mock(Connection.class);
        final Connection uncloseable = Connections.uncloseable(connection);
        uncloseable.close();
        verifyZeroInteractions(connection);
    }

    @Test
    public void testUncloseableConnectionNotClose() throws Exception {
        final Connection connection = mock(Connection.class);
        final Connection uncloseable = Connections.uncloseable(connection);
        uncloseable.applyChange(null);
        verify(connection).applyChange(null);
    }

    @Test
    public void testUncloseableConnectionUnbind() throws Exception {
        final Connection connection = mock(Connection.class);
        final Connection uncloseable = Connections.uncloseable(connection);
        uncloseable.close(null, null);
        verifyZeroInteractions(connection);
    }
}
