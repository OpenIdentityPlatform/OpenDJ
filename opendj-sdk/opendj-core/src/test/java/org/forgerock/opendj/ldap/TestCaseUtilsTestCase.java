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
 * Copyright 2013 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import static org.fest.assertions.Assertions.assertThat;
import static org.forgerock.opendj.ldap.TestCaseUtils.mockTimeService;

import org.testng.annotations.Test;

import org.forgerock.util.time.TimeService;

@SuppressWarnings("javadoc")
public class TestCaseUtilsTestCase extends SdkTestCase {

    /**
     * Test for {@link #mockTimeSource(long...)}.
     */
    @Test
    public void testMockTimeSource() {
        final TimeService mock1 = mockTimeService(10);
        assertThat(mock1.now()).isEqualTo(10);
        assertThat(mock1.now()).isEqualTo(10);

        final TimeService mock2 = mockTimeService(10, 20, 30);
        assertThat(mock2.now()).isEqualTo(10);
        assertThat(mock2.now()).isEqualTo(20);
        assertThat(mock2.now()).isEqualTo(30);
        assertThat(mock2.now()).isEqualTo(30);
    }
}
