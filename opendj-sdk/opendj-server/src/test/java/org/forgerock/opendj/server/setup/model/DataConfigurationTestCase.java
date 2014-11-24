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
 *      Copyright 2014 ForgeRock AS.
 */
package org.forgerock.opendj.server.setup.model;

import static org.fest.assertions.Assertions.assertThat;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.forgerock.opendj.server.setup.model.DataConfiguration.Type;
import org.testng.annotations.Test;

/**
 * This class tests the data store functionality.
 */
public class DataConfigurationTestCase extends AbstractSetupTestCase {

    /**
     * Creates a default data configuration.
     */
    @Test
    public void testGetDefaultDataConfiguration() {
        final DataConfiguration data = new DataConfiguration();
        assertThat(data.getDirectoryBaseDN()).isEqualTo(DataConfiguration.DEFAULT_DIRECTORY_BASE_DN);
        assertFalse(data.isEmptyDatabase());
        assertFalse(data.isImportLDIF());
        assertFalse(data.isOnlyBaseEntry());
        assertTrue(data.isAutomaticallyImportGenerated());

        assertThat(data.getLdifImportDataPath()).isNull();
        assertThat(data.getNumberOfUserEntries()).isEqualTo(DataConfiguration.IMPORT_ENTRIES_DEFAULT_VALUE);
    }

    /**
     * Creates a custom data configuration.
     *
     * @throws IOException
     */
    @Test
    public void testCustomDataConfiguration() throws IOException {
        final DataConfiguration data = new DataConfiguration();
        final int userEntries = 300;
        data.setNumberOfUserEntries(userEntries);
        assertThat(data.getNumberOfUserEntries()).isEqualTo(userEntries);
        assertTrue(data.isAutomaticallyImportGenerated());
        assertFalse(data.isImportLDIF());
        assertFalse(data.isEmptyDatabase());
        assertFalse(data.isOnlyBaseEntry());
        // Set another config
        data.setType(Type.IMPORT_LDIF);
        File importLdifFile = null;
        try {
            importLdifFile = File.createTempFile("import_ldif_file", ".ldif");
            data.setLdifImportDataPath(importLdifFile);
            assertTrue(data.isImportLDIF());
            assertThat(data.getLdifImportDataPath().exists());
            // Return to previous config.
            data.setType(Type.AUTOMATICALLY_GENERATED);
            assertThat(data.getNumberOfUserEntries()).isEqualTo(userEntries);
            assertTrue(data.isAutomaticallyImportGenerated());
            assertFalse(data.isImportLDIF());
        } finally {
            if (importLdifFile != null) {
                importLdifFile.delete();
            }
        }
    }

    /**
     * Tests the type of the data configuration to make sure the boolean types are correctly assigned.
     */
    @Test
    public void testDataConfigurationType() {
        final DataConfiguration data = new DataConfiguration();
        assertTrue(data.isAutomaticallyImportGenerated());
        assertFalse(data.isImportLDIF());
        assertFalse(data.isEmptyDatabase());
        assertFalse(data.isOnlyBaseEntry());
        assertThat(data.getType() == Type.AUTOMATICALLY_GENERATED);
        // Import LDIF
        data.setType(Type.IMPORT_LDIF);
        assertTrue(data.isImportLDIF());
        assertFalse(data.isAutomaticallyImportGenerated());
        assertFalse(data.isEmptyDatabase());
        assertFalse(data.isOnlyBaseEntry());
        assertThat(data.getType() == Type.IMPORT_LDIF);
        // Empty database
        data.setType(Type.EMPTY_DATABASE);
        assertFalse(data.isAutomaticallyImportGenerated());
        assertFalse(data.isImportLDIF());
        assertTrue(data.isEmptyDatabase());
        assertFalse(data.isOnlyBaseEntry());
        assertThat(data.getType() == Type.EMPTY_DATABASE);
        // Only base entry
        data.setType(Type.BASE_ENTRY_ONLY);
        assertTrue(data.isOnlyBaseEntry());
        assertFalse(data.isAutomaticallyImportGenerated());
        assertFalse(data.isImportLDIF());
        assertFalse(data.isEmptyDatabase());
        assertThat(data.getType() == Type.BASE_ENTRY_ONLY);
    }
}
