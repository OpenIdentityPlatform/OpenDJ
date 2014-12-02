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
 *      Copyright 2014 ForgeRock AS
 */
package org.opends.server.backends.persistit;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.api.EntryCache;
import org.opends.server.backends.jeb.EntryID;
import org.opends.server.backends.pluggable.SuffixContainer;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;

import com.persistit.Exchange;
import com.persistit.Persistit;
import com.persistit.Volume;
import com.persistit.exception.PersistitException;
import com.sleepycat.je.DatabaseException;

import static org.opends.messages.JebMessages.*;

/**
 * Persistit implementation of a {@link SuffixContainer}.
 */
class PersistitSuffixContainer implements SuffixContainer
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The baseDN handled by this suffix container. */
  private final DN baseDN;
  /**
   * Prevents name clashes for common indexes (like id2entry) across multiple
   * suffixes. For example when a root container contains multiple suffixes.
   */
  private final String indexPrefix;
  private final PersistitRootContainer rootContainer;
  private final Persistit db;
  private final Volume volume;

  private PersistitID2Entry id2entry;
  private PersistitDN2ID dn2id;

  /**
   * Constructor for this class.
   *
   * @param baseDN
   *          The baseDN handled by this suffix container
   * @param indexPrefix
   *          the prefix to use for indexes name
   * @param rootContainer
   *          the persisit root container
   * @param db
   *          the persisit database
   * @param volume
   *          the volume where indexes will be created
   */
  PersistitSuffixContainer(DN baseDN, String indexPrefix, PersistitRootContainer rootContainer,
      Persistit db, Volume volume)
  {
    this.baseDN = baseDN;
    this.indexPrefix = indexPrefix;
    this.rootContainer = rootContainer;
    this.db = db;
    this.volume = volume;
  }

  /**
   * Opens the entryContainer for reading and writing.
   *
   * @throws DirectoryException
   *           If an error occurs while opening the suffix container.
   */
  void open() throws DirectoryException
  {
    id2entry = new PersistitID2Entry(this);
    id2entry.open();
    dn2id = new PersistitDN2ID(this);
    dn2id.open();
  }

  /** {@inheritDoc} */
  @Override
  public void close()
  {
    dn2id.close();
    id2entry.close();
  }

  /** {@inheritDoc} */
  @Override
  public DN getBaseDN()
  {
    return baseDN;
  }

  /**
   * Returns the index name prefix.
   *
   * @return the index name prefix
   */
  public String getIndexPrefix()
  {
    return indexPrefix;
  }

  /** {@inheritDoc} */
  @Override
  public long getEntryCount()
  {
    // FIXME To be implemented
    // JNR: I have not found a suitable API to return this value
    return -1;
  }

  /**
   * Returns the id2entry index.
   *
   * @return the id2entry index
   */
  public PersistitID2Entry getID2Entry()
  {
    return id2entry;
  }

  /**
   * Creates a {@link Tree} for the fully qualified index name.
   *
   * @param fullyQualifiedIndexName
   *          The fully qualified index name
   * @throws PersistitException
   *           if a database exception happens
   */
  void createTree(String fullyQualifiedIndexName) throws PersistitException
  {
    volume.getTree(fullyQualifiedIndexName, true);
  }

  /**
   * Returns a new {@link Exchange} working on the index provided via its fully
   * qualified name.
   * <p>
   * Note: exchanges obtained with this method must be released by calling
   * {@link #releaseExchange(Exchange)}.
   *
   * @param fullyQualifiedIndexName
   *          The fully qualified index name
   * @return the exchange
   * @throws PersistitException
   *           if a database exception happens
   */
  final Exchange getExchange(String fullyQualifiedIndexName) throws PersistitException
  {
    return db.getExchange(volume, fullyQualifiedIndexName, false);
  }

  /**
   * Returns the fully qualified index name for this simple index name.
   * <p>
   * e.g.
   *
   * <pre>
   * PersistitSuffixContainer sc = ...; // initialize the suffix container
   * assertEquals(sc.getIndexPrefix(), "dccom");
   * assertEquals(sc.getFullyQualifiedIndexName("id2entry"), "dccom_id2entry");
   * </pre>
   *
   * @param simpleIndexName
   *          the simple index name to convert to a fully qualified index name
   * @return the fully qualified index name
   */
  public String getFullyQualifiedIndexName(String simpleIndexName)
  {
    return indexPrefix + "_" + simpleIndexName;
  }

  /**
   * Releases the provided exchange.
   *
   * @param exchange
   *          the exchange to release
   */
  final void releaseExchange(Exchange exchange)
  {
    if (exchange != null)
    {
      db.releaseExchange(exchange);
    }
  }

  /**
   * Indicates whether an entry with the specified DN exists.
   *
   * @param entryDN
   *          The DN of the entry for which to determine existence.
   * @return <CODE>true</CODE> if the specified entry exists, or
   *         <CODE>false</CODE> if it does not.
   * @throws DirectoryException
   *           If a problem occurs while trying to make the determination.
   */
  public boolean entryExists(DN entryDN) throws DirectoryException
  {
    // Try the entry cache first.
    EntryCache<?> entryCache = DirectoryServer.getEntryCache();
    if (entryCache != null && entryCache.containsEntry(entryDN))
    {
      return true;
    }

    try
    {
      // Read the ID from dn2id.
      EntryID id = dn2id.get(null, entryDN, null);
      return id != null;
    }
    catch (DatabaseException e)
    {
      logger.traceException(e);
      return false;
    }
  }

  /**
   * Retrieves and returns the entry associated to the provided DN.
   *
   * @param entryDN
   *          the DN of the entry to return
   * @return the entry associated to the provided DN
   * @throws DirectoryException
   *           if a directory exception happens
   * @throws PersistitException
   *           if a database exception happens
   */
  Entry getEntry(DN entryDN) throws DirectoryException, PersistitException
  {
    EntryCache<?> entryCache = DirectoryServer.getEntryCache();

    // Try the entry cache first.
    Entry entry = null;
    if (entryCache != null)
    {
      entry = entryCache.getEntry(entryDN);
    }

    if (entry == null)
    {
      // Read dn2id.
      EntryID entryID = dn2id.get(null, entryDN, null);
      if (entryID == null)
      {
        // The entryDN does not exist.

        // Check for referral entries above the target entry.
        // TODO JNR uncomment next line
        // dn2uri.targetEntryReferrals(entryDN, null);

        return null;
      }

      // Read id2entry.
      entry = id2entry.get(null, entryID, null);
      if (entry == null)
      {
        // The entryID does not exist.
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), ERR_JEB_MISSING_ID2ENTRY_RECORD
            .get(entryID));
      }

      // Put the entry in the cache making sure not to overwrite
      // a newer copy that may have been inserted since the time
      // we read the cache.
      if (entryCache != null)
      {
        entryCache.putEntryIfAbsent(entry, rootContainer.getBackend(), entryID.longValue());
      }
    }

    return entry;
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + " baseDN=" + baseDN;
  }
}
