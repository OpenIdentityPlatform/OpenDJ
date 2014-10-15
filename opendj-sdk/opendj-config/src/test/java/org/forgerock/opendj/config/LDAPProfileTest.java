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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.forgerock.opendj.config;

import static org.testng.Assert.assertTrue;

import java.util.List;

import org.forgerock.opendj.server.config.meta.GlobalCfgDefn;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class LDAPProfileTest extends ConfigTestCase {

    /** Makes sure the returned list contains "top". */
    @Test
    public void testGetObjectClasses() {
        LDAPProfile ldapProfile = LDAPProfile.getInstance();
        List<String> objectClasses = ldapProfile.getObjectClasses(GlobalCfgDefn.getInstance());
        assertTrue(objectClasses.contains("top"));
    }

}
