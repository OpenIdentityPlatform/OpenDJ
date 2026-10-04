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
package org.opends.server.backends.pluggable;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.api.LocalBackend;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.core.DirectoryServer;

/**
 * Removes the keys of an attribute's indexes while leaving them trusted, as an index whose keys a previous version
 * computed differently looks to the current one. For the tests outside this package, which cannot reach the trees
 * of a backend.
 */
public final class IndexKeyRemover
{
  private IndexKeyRemover()
  {
    // Utility class
  }

  /**
   * Removes every key of the indexes of an attribute under a base DN of an online pluggable backend, and marks
   * these indexes trusted.
   *
   * @param backend
   *          The backend, which must be a pluggable one.
   * @param baseDN
   *          The base DN of the entry container.
   * @param attributeName
   *          The name of the indexed attribute.
   * @throws Exception
   *           If the keys cannot be removed.
   */
  public static void removeAllKeys(LocalBackend<?> backend, DN baseDN, String attributeName) throws Exception
  {
    final RootContainer rootContainer = ((BackendImpl<?>) backend).getRootContainer();
    final AttributeType attributeType =
        DirectoryServer.getInstance().getServerContext().getSchema().getAttributeType(attributeName);
    final AttributeIndex attributeIndex = rootContainer.getEntryContainer(baseDN).getAttributeIndex(attributeType);
    rootContainer.getStorage().write(txn ->
    {
      for (AttributeIndex.MatchingRuleIndex index : attributeIndex.getNameToIndexes().values())
      {
        final List<ByteString> keys = new ArrayList<>();
        try (Cursor<ByteString, ByteString> cursor = txn.openCursor(index.getName()))
        {
          while (cursor.next())
          {
            keys.add(cursor.getKey());
          }
        }
        for (ByteString key : keys)
        {
          txn.delete(index.getName(), key);
        }
        // A missing key of an untrusted index is not an error, as the index is known to be incomplete
        index.setTrusted(txn, true);
      }
    });
  }
}
