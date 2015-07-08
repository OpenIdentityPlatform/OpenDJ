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

import java.io.File;

class DataConfiguration {

    /**
     * This enumeration is used to know what kind of data configuration we want to deploy.
     */
    public enum Type {
        /**
         * Base entry only.
         */
        BASE_ENTRY_ONLY,
        /**
         * Empty database.
         */
        EMPTY_DATABASE,
        /**
         * Import LDIF.
         */
        IMPORT_LDIF,
        /**
         * Automatically generated data entries.
         */
        AUTOMATICALLY_GENERATED
    }

    /** Default name of directory base DN. */
    static final String DEFAULT_DIRECTORY_BASE_DN = "dc=example,dc=com";

    /** Default value for the generated entries. */
    static final int IMPORT_ENTRIES_DEFAULT_VALUE = 2000;

    private String directoryBaseDN;
    private Type type;
    private File ldifImportDataPath;
    private int numberOfUserEntries;

    DataConfiguration() {
        directoryBaseDN = DEFAULT_DIRECTORY_BASE_DN;
        type = Type.AUTOMATICALLY_GENERATED;
        numberOfUserEntries = IMPORT_ENTRIES_DEFAULT_VALUE;
    }

    public String getDirectoryBaseDN() {
        return directoryBaseDN;
    }

    public void setDirectoryBaseDN(String directoryBaseDN) {
        this.directoryBaseDN = directoryBaseDN;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public boolean isOnlyBaseEntry() {
        return type == Type.BASE_ENTRY_ONLY;
    }

    public boolean isEmptyDatabase() {
        return type == Type.EMPTY_DATABASE;
    }

    public boolean isImportLDIF() {
        return type == Type.IMPORT_LDIF;
    }

    public boolean isAutomaticallyImportGenerated() {
        return type == Type.AUTOMATICALLY_GENERATED;
    }

    public File getLdifImportDataPath() {
        return ldifImportDataPath;
    }

    public void setLdifImportDataPath(File ldifImportDataPath) {
        this.ldifImportDataPath = ldifImportDataPath;
    }

    public int getNumberOfUserEntries() {
        return numberOfUserEntries;
    }

    public void setNumberOfUserEntries(int numberOfUserEntries) {
        this.numberOfUserEntries = numberOfUserEntries;
    }
}
