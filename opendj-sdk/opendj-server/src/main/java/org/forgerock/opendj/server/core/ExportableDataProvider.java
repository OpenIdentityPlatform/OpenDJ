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
import org.forgerock.opendj.ldif.EntryWriter;

/**
 * A data provider which supports LDIF export functionality.
 * <p>
 * FIXME: the async APIs used below are a bad fit. We do not want to return an
 * {@link LdapException}. We really need a more generic promises API.
 */
public interface ExportableDataProvider {

    /**
     * Exports the contents of this data provider to the provided entry writer.
     * <p>
     * Note that the server will not explicitly initialize this data provider
     * before calling this method.
     *
     * @param writer
     *            The entry writer.
     * @param handler
     *            A handler which will be notified when the export completes.
     * @return A promise representing the completion of the export.
     */
    LdapPromise<Void> exportEntries(EntryWriter writer, LdapResultHandler<Void> handler);

    /**
     * Returns the ID of this data provider.
     *
     * @return The ID of this data provider.
     */
    DataProviderID getDataProviderID();
}
