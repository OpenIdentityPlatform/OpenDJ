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
package org.opends.server.backends.jdbc;

import org.forgerock.opendj.server.config.server.JDBCBackendCfg;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.testng.annotations.Test;

import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * The names a JDBC backend gives the trees that are its own rather than a base DN's: its catalog
 * (#888) and its pair of compressed schema trees (#881). Both are built from the backend id, and
 * the tables behind them are created under the id the storage was built with - so what this class
 * pins is that the names do not move under a storage that has already created them.
 * <p>
 * No database is needed: the names are read from the configuration and nothing else.
 */
@SuppressWarnings("javadoc")
public class CatalogNameTestCase extends DirectoryServerTestCase {

	private static JDBCStorage storageFor(String backendId) {
		final JDBCBackendCfg cfg = mockCfg(JDBCBackendCfg.class);
		when(cfg.getBackendId()).thenReturn(backendId);
		return new JDBCStorage(cfg, null);
	}

	/**
	 * The catalog is named after the backend id, and after the one this storage was built with: a
	 * configuration handed to {@code applyConfigurationChange()} replaces {@code config} whole, so a
	 * name read from it again would follow an id changed under a running backend - and the tables of
	 * this storage, the catalog table among them, stand under the id they were created with. The
	 * backend id is read-only in the configuration framework and no such change can be made through
	 * it, which is exactly why the storage may read it once; a rename reaching this method by any
	 * other route must not leave the storage naming a catalog nothing has ever written.
	 */
	@Test
	public void testTheCatalogKeepsTheBackendIdTheStorageWasBuiltWith() {
		final JDBCStorage storage = storageFor("pinnedBackend");
		final TreeName built = storage.getCatalogTree();
		assertEquals(built, new TreeName(JDBCStorage.CATALOG_BASE_DN, "pinnedBackend"));

		final JDBCBackendCfg renamed = mockCfg(JDBCBackendCfg.class);
		when(renamed.getBackendId()).thenReturn("renamedBackend");
		storage.applyConfigurationChange(renamed);

		assertEquals(storage.getCatalogTree(), built,
			"the catalog followed a backend id changed under the storage, naming a table nothing created");
	}

	/**
	 * The second name built from the id, the base DN of this backend's own pair of compressed schema
	 * trees (#881), is pinned for the reason the catalog is: the pair stands under the id the storage
	 * created it with, and a name read again from a configuration that has moved would leave a clear
	 * reporting this backend's own pair as a table attributable to nobody.
	 */
	@Test
	public void testTheCompressedSchemaNameKeepsTheBackendIdTheStorageWasBuiltWith() {
		final JDBCStorage storage = storageFor("pinnedBackend");
		final String built = storage.ownCompressedSchemaBaseDN();
		assertEquals(built, JDBCStorage.SHARED_COMPRESSED_SCHEMA_BASE_DN + "_pinnedBackend");

		final JDBCBackendCfg renamed = mockCfg(JDBCBackendCfg.class);
		when(renamed.getBackendId()).thenReturn("renamedBackend");
		storage.applyConfigurationChange(renamed);

		assertEquals(storage.ownCompressedSchemaBaseDN(), built,
			"the compressed schema pair followed a backend id changed under the storage");
	}

	/**
	 * Both names carry the id escaped, and escaped the way {@code PersistentCompressedSchema} spells
	 * the prefix of the same pair: the percent first, so that the escape of a slash cannot itself be
	 * escaped a second time. A tree name is {@code /<base DN>/<id>} and is read back by splitting on
	 * its slashes, so an id spelled into one unescaped names a tree that parses into another tree
	 * than it was built from - and the clear reading the stamp of such a table would pass a table of
	 * this backend's own over in silence. An id of that shape is possible: {@code backend-id} is a
	 * plain string in the configuration definition, constrained to nothing.
	 */
	@Test
	public void testBothNamesEscapeTheSlashAndThePercentOfTheBackendId() {
		final JDBCStorage storage = storageFor("a/b%c");
		assertEquals(storage.getCatalogTree(), new TreeName(JDBCStorage.CATALOG_BASE_DN, "a%2Fb%25c"));
		assertEquals(storage.ownCompressedSchemaBaseDN(),
			JDBCStorage.SHARED_COMPRESSED_SCHEMA_BASE_DN + "_a%2Fb%25c");
	}
}
