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
package org.forgerock.opendj.config.dsconfig;

import static org.testng.Assert.assertFalse;

import org.forgerock.opendj.config.ConfigTestCase;
import org.forgerock.opendj.server.config.meta.LDAPPassThroughAuthenticationPolicyCfgDefn;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class PasswordDisplayTest extends ConfigTestCase {
    @Test
    public void mappedSearchPasswordIsNotDisplayed() {
        for (boolean scriptFriendly : new boolean[] { false, true }) {
            String output = new PropertyValuePrinter(null, null, scriptFriendly).print(
                    LDAPPassThroughAuthenticationPolicyCfgDefn.getInstance()
                            .getMappedSearchBindPasswordPropertyDefinition(), "secret-password").toString();
            assertFalse(output.contains("secret-password"));
        }
    }
}
