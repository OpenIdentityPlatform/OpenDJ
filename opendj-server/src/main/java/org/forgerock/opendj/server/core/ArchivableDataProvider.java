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
 *       Copyright 2008 Sun Microsystems, Inc.
 *       Portions Copyright 2013-2014 ForgeRock AS.
 */
package org.forgerock.opendj.server.core;

import org.forgerock.opendj.ldap.LdapPromise;
import org.forgerock.opendj.ldap.LdapResultHandler;

/**
 * A data provider which supports backup and restore functionality.
 * <p>
 * TODO: do we need supportsRestore?
 * <p>
 * TODO: do we need removeBackup?
 * <p>
 * TODO: is there any boiler plate code that abstracted in order to make
 * implementation simpler? E.g. initialization, crypto.
 * <p>
 * FIXME: the async APIs used below are a bad fit. We do not want to return an
 * {@link LdapException}. We really need a more generic promises API.
 */
public interface ArchivableDataProvider {

    /**
     * Creates a backup of the contents of this data provider in a form that may
     * be restored at a later date if necessary. This method should only be
     * called if {@code supportsBackup} returns {@code true}.
     * <p>
     * Note that the server will not explicitly initialize this data provider
     * before calling this method.
     *
     * @param backupConfig
     *            The configuration to use when performing the backup.
     * @param handler
     *            A handler which will be notified when the backup completes.
     * @return A promise representing the completion of the backup.
     */
    LdapPromise<Void> createBackup(BackupConfig backupConfig, LdapResultHandler<Void> handler);

    /**
     * Returns the ID of this data provider.
     *
     * @return The ID of this data provider.
     */
    DataProviderID getDataProviderID();

    /**
     * Restores a backup of the contents of this data provider.
     * <p>
     * Note that the server will not explicitly initialize this data provider
     * before calling this method.
     *
     * @param restoreConfig
     *            The configuration to use when performing the restore.
     * @param handler
     *            A handler which will be notified when the restore completes.
     * @return A promise representing the completion of the restore.
     */
    LdapPromise<Void> restoreBackup(RestoreConfig restoreConfig, LdapResultHandler<Void> handler);

    /**
     * Indicates whether this data provider provides a mechanism to perform a
     * backup of its contents in a form that can be restored later, based on the
     * provided configuration.
     *
     * @param backupConfig
     *            The configuration of the backup for which to make the
     *            determination.
     * @param unsupportedReason
     *            A buffer to which a message can be appended explaining why the
     *            requested backup is not supported.
     * @return {@code true} if this data provider provides a mechanism for
     *         performing backups with the provided configuration, or
     *         {@code false} if not.
     */
    boolean supportsBackup(BackupConfig backupConfig, StringBuilder unsupportedReason);
}
