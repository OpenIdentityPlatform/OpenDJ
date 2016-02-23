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
 * Copyright 2008 Sun Microsystems, Inc.
 */

package org.forgerock.opendj.config;

import static org.fest.assertions.Assertions.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.forgerock.opendj.server.config.meta.RootCfgDefn;
import org.testng.annotations.Test;


@SuppressWarnings("javadoc")
@Test(singleThreaded = true)
public class TestTopCfgDefnTest extends ConfigTestCase {

    @Test
    public void testGetInstance() {
        assertNotNull(TopCfgDefn.getInstance());
    }

    @Test
    public void testGetName() {
        assertEquals(TopCfgDefn.getInstance().getName(), "top");
    }

    @Test
    public void testGetAllPropertyDefinitionsIsEmpty() {
        assertTrue(TopCfgDefn.getInstance().getAllPropertyDefinitions().isEmpty());
    }

    @Test
    public void testGetAllRelationDefinitionsIsEmpty() {
        assertTrue(TopCfgDefn.getInstance().getAllRelationDefinitions().isEmpty());
    }

    @Test
    public void testGetAllConstraintsIsEmpty() {
        assertTrue(TopCfgDefn.getInstance().getAllConstraints().isEmpty());
    }

    @Test
    public void testGetAllTagsIsEmpty() {
        assertTrue(TopCfgDefn.getInstance().getAllTags().isEmpty());
    }

    @Test
    public void testGetParentReturnNull() {
        assertNull(TopCfgDefn.getInstance().getParent());
    }

    @Test
    public void testIsTop() {
        assertTrue(TopCfgDefn.getInstance().isTop());
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testGetSynopsis() {
        assertNotNull(TopCfgDefn.getInstance().getSynopsis());
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testGetDescription() {
        assertNotNull(TopCfgDefn.getInstance().getDescription());
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testGetUserFriendlyName() {
        assertNotNull(TopCfgDefn.getInstance().getUserFriendlyName());
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testGetUserFriendlyPluralName() {
        assertNotNull(TopCfgDefn.getInstance().getUserFriendlyPluralName());
    }

    @Test
    public void testGetAllChildren() {
        // load RootCfgDef as child of TopCfgDef, and load all children of RootCfgDef as well
        RootCfgDefn.getInstance();
        assertThat(TopCfgDefn.getInstance().getAllChildren()).isNotEmpty();
    }

}
