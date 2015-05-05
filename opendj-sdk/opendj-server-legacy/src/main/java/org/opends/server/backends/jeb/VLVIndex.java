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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.backends.jeb;

import static org.opends.messages.BackendMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.LocalDBVLVIndexCfgDefn.Scope;
import org.opends.server.admin.std.server.LocalDBVLVIndexCfg;
import org.opends.server.controls.ServerSideSortRequestControl;
import org.opends.server.controls.VLVRequestControl;
import org.opends.server.controls.VLVResponseControl;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SortKey;
import org.opends.server.types.SortOrder;
import org.opends.server.util.StaticUtils;

import com.sleepycat.je.*;

/**
 * This class represents a VLV index. Each database record is a sorted list
 * of entry IDs followed by sets of attribute values used to sort the entries.
 * The entire set of entry IDs are broken up into sorted subsets to decrease
 * the number of database retrievals needed for a range lookup. The records are
 * keyed by the last entry's first sort attribute value. The list of entries
 * in a particular database record maintains the property where the first sort
 * attribute value is bigger then the previous key but smaller or equal
 * to its own key.
 */
public class VLVIndex extends DatabaseContainer
    implements ConfigurationChangeListener<LocalDBVLVIndexCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The comparator for vlvIndex keys. */
  public VLVKeyComparator comparator;
  /** The limit on the number of entry IDs that may be indexed by one key. */
  private int sortedSetCapacity = 4000;
  /** The SortOrder in use by this VLV index to sort the entries. */
  public SortOrder sortOrder;

  /** The cached count of entries in this index. */
  private final AtomicInteger count;

  private final State state;
  /**
   * A flag to indicate if this vlvIndex should be trusted to be consistent
   * with the entries database.
   */
  private boolean trusted;

  /** The VLV vlvIndex configuration. */
  private LocalDBVLVIndexCfg config;

  private DN baseDN;
  private SearchFilter filter;
  private SearchScope scope;


  /**
   * Create a new VLV vlvIndex object.
   *
   * @param config           The VLV index config object to use for this VLV
   *                         index.
   * @param state            The state database to persist vlvIndex state info.
   * @param env              The JE Environment
   * @param entryContainer   The database entryContainer holding this vlvIndex.
   * @throws com.sleepycat.je.DatabaseException
   *          If an error occurs in the JE database.
   * @throws ConfigException if a error occurs while reading the VLV index
   * configuration
   */
  VLVIndex(LocalDBVLVIndexCfg config, State state, Environment env, EntryContainer entryContainer)
      throws DatabaseException, ConfigException
  {
    super(entryContainer.getDatabasePrefix()+"_vlv."+config.getName(),
          env, entryContainer);

    this.config = config;
    this.baseDN = config.getBaseDN();
    this.scope = convertScope(config.getScope());
    this.sortedSetCapacity = config.getMaxBlockSize();

    try
    {
      this.filter = SearchFilter.createFilterFromString(config.getFilter());
    }
    catch(Exception e)
    {
      throw new ConfigException(ERR_CONFIG_VLV_INDEX_BAD_FILTER.get(
          config.getFilter(), name, stackTraceToSingleLineString(e)));
    }

    String[] sortAttrs = config.getSortOrder().split(" ");
    SortKey[] sortKeys = new SortKey[sortAttrs.length];
    MatchingRule[] orderingRules = new MatchingRule[sortAttrs.length];
    boolean[] ascending = new boolean[sortAttrs.length];
    for(int i = 0; i < sortAttrs.length; i++)
    {
      try
      {
        if(sortAttrs[i].startsWith("-"))
        {
          ascending[i] = false;
          sortAttrs[i] = sortAttrs[i].substring(1);
        }
        else
        {
          ascending[i] = true;
          if(sortAttrs[i].startsWith("+"))
          {
            sortAttrs[i] = sortAttrs[i].substring(1);
          }
        }
      }
      catch(Exception e)
      {
        throw new ConfigException(ERR_CONFIG_VLV_INDEX_UNDEFINED_ATTR.get(sortKeys[i], name));
      }

      AttributeType attrType =
          DirectoryServer.getAttributeType(sortAttrs[i].toLowerCase());
      if(attrType == null)
      {
        throw new ConfigException(ERR_CONFIG_VLV_INDEX_UNDEFINED_ATTR.get(sortAttrs[i], name));
      }
      sortKeys[i] = new SortKey(attrType, ascending[i]);
      orderingRules[i] = attrType.getOrderingMatchingRule();
    }

    this.sortOrder = new SortOrder(sortKeys);
    this.comparator = new VLVKeyComparator(orderingRules, ascending);

    this.dbConfig = JEBUtils.toDatabaseConfigNoDuplicates(env);
    this.dbConfig.setOverrideBtreeComparator(true);
    this.dbConfig.setBtreeComparator(this.comparator);

    this.state = state;

    this.trusted = state.getIndexTrustState(null, this);
    if (!trusted && entryContainer.getHighestEntryID().longValue() == 0)
    {
      // If there are no entries in the entry container then there
      // is no reason why this vlvIndex can't be upgraded to trusted.
      setTrusted(null, true);
    }

    this.count = new AtomicInteger(0);
    this.config.addChangeListener(this);
  }

  private SearchScope convertScope(final Scope cfgScope)
  {
    switch (cfgScope)
    {
    case BASE_OBJECT:
      return SearchScope.BASE_OBJECT;
    case SINGLE_LEVEL:
      return SearchScope.SINGLE_LEVEL;
    case SUBORDINATE_SUBTREE:
      return SearchScope.SUBORDINATES;
    default: // WHOLE_SUBTREE
      return SearchScope.WHOLE_SUBTREE;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void open() throws DatabaseException
  {
    super.open();

    DatabaseEntry key = new DatabaseEntry();
    DatabaseEntry data = new DatabaseEntry();
    LockMode lockMode = LockMode.RMW;

    Cursor cursor = openCursor(null, CursorConfig.READ_COMMITTED);
    try
    {
      OperationStatus status = cursor.getFirst(key, data,lockMode);
      while(status == OperationStatus.SUCCESS)
      {
        count.getAndAdd(SortValuesSet.getEncodedSize(data.getData(), 0));
        status = cursor.getNext(key, data, lockMode);
      }
    }
    finally
    {
      cursor.close();
    }
  }

  /**
   * Close the VLV index.
   *
   * @throws DatabaseException if a JE database error occurs while
   * closing the index.
   */
  @Override
  public void close() throws DatabaseException
  {
    super.close();
    this.config.removeChangeListener(this);
  }

  /**
   * Update the vlvIndex for a new entry.
   *
   * @param txn A database transaction, or null if none is required.
   * @param entryID     The entry ID.
   * @param entry       The entry to be indexed.
   * @return True if the entry ID for the entry are added. False if
   *         the entry ID already exists.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws org.opends.server.types.DirectoryException If a Directory Server
   * error occurs.
   * @throws JebException If an error occurs in the JE backend.
   */
  public boolean addEntry(Transaction txn, EntryID entryID, Entry entry)
      throws DatabaseException, DirectoryException, JebException
  {
    return shouldInclude(entry)
        && insertValues(txn, entryID.longValue(), entry);
  }

  /**
   * Update the vlvIndex for a new entry.
   *
   * @param buffer      The index buffer to buffer the changes.
   * @param entryID     The entry ID.
   * @param entry       The entry to be indexed.
   * @return True if the entry ID for the entry are added. False if
   *         the entry ID already exists.
   * @throws DirectoryException If a Directory Server
   * error occurs.
   */
  boolean addEntry(IndexBuffer buffer, EntryID entryID, Entry entry) throws DirectoryException
  {
    if (shouldInclude(entry))
    {
      final SortValues sortValues = new SortValues(entryID, entry, sortOrder);
      buffer.getVLVIndex(this).addValues(sortValues);
      return true;
    }
    return false;
  }

  /**
   * Update the vlvIndex for a deleted entry.
   *
   * @param buffer      The database transaction to be used for the deletions
   * @param entryID     The entry ID
   * @param entry       The contents of the deleted entry.
   * @return True if the entry was successfully removed from this VLV index
   * or False otherwise.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  boolean removeEntry(IndexBuffer buffer, EntryID entryID, Entry entry) throws DirectoryException
  {
    if (shouldInclude(entry))
    {
      final SortValues sortValues = new SortValues(entryID, entry, sortOrder);
      buffer.getVLVIndex(this).deleteValues(sortValues);
      return true;
    }
    return false;
  }

  /**
   * Update the vlvIndex to reflect a sequence of modifications in a Modify
   * operation.
   *
   * @param buffer The database transaction to be used for the deletions
   * @param entryID The ID of the entry that was modified.
   * @param oldEntry The entry before the modifications were applied.
   * @param newEntry The entry after the modifications were applied.
   * @param mods The sequence of modifications in the Modify operation.
   * @return True if the modification was successfully processed or False
   * otherwise.
   * @throws DatabaseException If an error occurs during an operation on a
   * JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  boolean modifyEntry(IndexBuffer buffer,
                          EntryID entryID,
                          Entry oldEntry,
                          Entry newEntry,
                          List<Modification> mods)
       throws DatabaseException, DirectoryException
  {
    if (shouldInclude(oldEntry))
    {
      if (shouldInclude(newEntry))
      {
        // The entry should still be indexed. See if any sorted attributes are changed.
        if (isSortAttributeModified(mods))
        {
          // Sorted attributes have changed. Reindex the entry;
          boolean success;
          success = removeEntry(buffer, entryID, oldEntry);
          success &= addEntry(buffer, entryID, newEntry);
          return success;
        }
      }
      else
      {
        // The modifications caused the new entry to be unindexed.
        return removeEntry(buffer, entryID, oldEntry);
      }
    }
    else
    {
      if (shouldInclude(newEntry))
      {
        // The modifications caused the new entry to be indexed. Add to vlvIndex
        return addEntry(buffer, entryID, newEntry);
      }
    }

    // The modifications does not affect this vlvIndex
    return true;
  }

  private boolean isSortAttributeModified(List<Modification> mods)
  {
    for (SortKey sortKey : sortOrder.getSortKeys())
    {
      AttributeType attributeType = sortKey.getAttributeType();
      Iterable<AttributeType> subTypes = DirectoryServer.getSchema().getSubTypes(attributeType);
      for (Modification mod : mods)
      {
        AttributeType modAttrType = mod.getAttribute().getAttributeType();
        if (modAttrType.equals(attributeType))
        {
          return true;
        }
        for (AttributeType subType : subTypes)
        {
          if (modAttrType.equals(subType))
          {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Get a sorted values set that should contain the entry with the given
   * information.
   *
   * @param txn The transaction to use when retrieving the set or NULL if it is
   *            not required.
   * @param entryID The entry ID to use.
   * @param values The values to use.
   * @param types The types of the values to use.
   * @return The SortValuesSet that should contain the entry with the given
   *         information.
   * @throws DatabaseException If an error occurs during an operation on a
   * JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  SortValuesSet getSortValuesSet(Transaction txn, long entryID,
      ByteString[] values, AttributeType[] types) throws DatabaseException,
      DirectoryException
  {
    DatabaseEntry key = new DatabaseEntry(encodeKey(entryID, values, types));
    DatabaseEntry data = new DatabaseEntry();
    return getSortValuesSet(txn, key, data, LockMode.DEFAULT);
  }

  private SortValuesSet getSortValuesSet(Transaction txn, DatabaseEntry key,
      DatabaseEntry data, LockMode lockMode)
  {
    OperationStatus status = getSearchKeyRange(txn, key, data, lockMode);
    if (status != OperationStatus.SUCCESS)
    {
      // There are no records in the database
      if (logger.isTraceEnabled())
      {
        logger.trace("No sort values set exist in VLV vlvIndex %s. "
            + "Creating unbound set.", config.getName());
      }
      // this could not be found, so clean the key for later reuse
      key.setData(new byte[0]);
      return new SortValuesSet(this);
    }

    if (logger.isTraceEnabled())
    {
      logSearchKeyResult(key);
    }
    return new SortValuesSet(key.getData(), data.getData(), this);
  }

  private void logSearchKeyResult(DatabaseEntry key)
  {
    StringBuilder searchKeyHex = new StringBuilder();
    StaticUtils.byteArrayToHexPlusAscii(searchKeyHex, key.getData(), 4);
    StringBuilder foundKeyHex = new StringBuilder();
    StaticUtils.byteArrayToHexPlusAscii(foundKeyHex, key.getData(), 4);
    logger.trace("Retrieved a sort values set in VLV vlvIndex %s\n" +
        "Search Key:%s\nFound Key:%s\n",
        config.getName(), searchKeyHex, foundKeyHex);
  }

  /**
   * Search for entries matching the entry ID and attribute values and
   * return its entry ID.
   *
   * @param txn The JE transaction to use for database updates.
   * @param entryID The entry ID to search for.
   * @param values The values to search for.
   * @param types The types of the values to search for.
   * @return The index of the entry ID matching the values or -1 if its not
   * found.
   * @throws DatabaseException If an error occurs during an operation on a
   * JE database.
   * @throws JebException If an error occurs during an operation on a
   * JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  boolean containsValues(Transaction txn, long entryID,
      ByteString[] values, AttributeType[] types) throws JebException,
      DatabaseException, DirectoryException
  {
    SortValuesSet valuesSet = getSortValuesSet(txn, entryID, values, types);
    int pos = valuesSet.binarySearch(entryID, values);
    return pos >= 0;
  }

  private boolean insertValues(Transaction txn, long entryID, Entry entry)
      throws JebException, DatabaseException, DirectoryException
  {
    ByteString[] values = getSortValues(entry);
    AttributeType[] types = getSortTypes();
    DatabaseEntry key = new DatabaseEntry(encodeKey(entryID, values, types));
    DatabaseEntry data = new DatabaseEntry();

    SortValuesSet sortValuesSet =
        getSortValuesSet(txn, key, data, LockMode.RMW);
    boolean success = sortValuesSet.add(entryID, values, types);

    int newSize = sortValuesSet.size();
    if(newSize >= sortedSetCapacity)
    {
      SortValuesSet splitSortValuesSet = sortValuesSet.split(newSize / 2);
      put(txn, key, data, splitSortValuesSet); // splitAfter
      put(txn, key, data, sortValuesSet); // after

      if(logger.isTraceEnabled())
      {
        logger.trace("SortValuesSet with key %s has reached" +
            " the entry size of %d. Spliting into two sets with " +
            " keys %s and %s.", splitSortValuesSet.getKeySortValues(),
                                newSize, sortValuesSet.getKeySortValues(),
                                splitSortValuesSet.getKeySortValues());
      }
    }
    else
    {
      data.setData(sortValuesSet.toDatabase()); // after
      put(txn, key, data);
      // TODO: What about phantoms?
    }

    if(success)
    {
      count.getAndIncrement();
    }

    return success;
  }

  private void put(Transaction txn, DatabaseEntry key, DatabaseEntry data,
      SortValuesSet set) throws DirectoryException
  {
    key.setData(set.getKeyBytes());
    data.setData(set.toDatabase());
    put(txn, key, data);
  }

  /**
   * Gets the types of the attribute values to sort.
   *
   * @return The types of the attribute values to sort on.
   */
  AttributeType[] getSortTypes()
  {
    SortKey[] sortKeys = sortOrder.getSortKeys();
    AttributeType[] types = new AttributeType[sortKeys.length];
    for (int i = 0; i < sortKeys.length; i++)
    {
      types[i] = sortKeys[i].getAttributeType();
    }
    return types;
  }

  private OperationStatus getSearchKeyRange(Transaction txn, DatabaseEntry key,
      DatabaseEntry data, LockMode lockMode)
  {
    Cursor cursor = openCursor(txn, CursorConfig.READ_COMMITTED);
    try
    {
      return cursor.getSearchKeyRange(key, data, lockMode);
    }
    finally
    {
      cursor.close();
    }
  }

  /**
   * Update the vlvIndex with the specified values to add and delete.
   *
   * @param txn A database transaction, or null if none is required.
   * @param addedValues The values to add to the VLV index.
   * @param deletedValues The values to delete from the VLV index.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server
   * error occurs.
   */
  void updateIndex(Transaction txn, TreeSet<SortValues> addedValues, TreeSet<SortValues> deletedValues)
      throws DirectoryException, DatabaseException
  {
    // Handle cases where nothing is changed early to avoid
    // DB access.
    if((addedValues == null || addedValues.isEmpty()) &&
        (deletedValues == null || deletedValues.isEmpty()))
    {
      return;
    }

    DatabaseEntry key = new DatabaseEntry();
    DatabaseEntry data = new DatabaseEntry();
    Iterator<SortValues> aValues = null;
    Iterator<SortValues> dValues = null;
    SortValues av = null;
    SortValues dv = null;

    if(addedValues != null)
    {
      aValues = addedValues.iterator();
      av = aValues.next();
    }
    if(deletedValues != null)
    {
      dValues = deletedValues.iterator();
      dv = dValues.next();
    }

    while(true)
    {
      if(av != null)
      {
        if(dv != null)
        {
          // Start from the smallest values from either set.
          if(av.compareTo(dv) < 0)
          {
            key.setData(encodeKey(av));
          }
          else
          {
            key.setData(encodeKey(dv));
          }
        }
        else
        {
          key.setData(encodeKey(av));
        }
      }
      else if(dv != null)
      {
        key.setData(encodeKey(dv));
      }
      else
      {
        break;
      }

      final SortValuesSet sortValuesSet = getSortValuesSet(txn, key, data, LockMode.RMW);
      int oldSize = sortValuesSet.size();
      if(key.getData().length == 0)
      {
        // This is the last unbounded set.
        while(av != null)
        {
          sortValuesSet.add(av);
          av = moveToNextSortValues(aValues);
        }

        while(dv != null)
        {
          sortValuesSet.remove(dv);
          dv = moveToNextSortValues(dValues);
        }
      }
      else
      {
        SortValues maxValues = decodeKey(sortValuesSet.getKeyBytes());

        while(av != null && av.compareTo(maxValues) <= 0)
        {
          sortValuesSet.add(av);
          av = moveToNextSortValues(aValues);
        }

        while(dv != null && dv.compareTo(maxValues) <= 0)
        {
          sortValuesSet.remove(dv);
          dv = moveToNextSortValues(dValues);
        }
      }

      int newSize = sortValuesSet.size();
      if(newSize >= sortedSetCapacity)
      {
        SortValuesSet splitSortValuesSet = sortValuesSet.split(newSize / 2);
        put(txn, key, data, splitSortValuesSet); // splitAfter
        put(txn, key, data, sortValuesSet); // after

        if(logger.isTraceEnabled())
        {
          logger.trace("SortValuesSet with key %s has reached" +
              " the entry size of %d. Spliting into two sets with " +
              " keys %s and %s.", splitSortValuesSet.getKeySortValues(),
              newSize, sortValuesSet.getKeySortValues(),
              splitSortValuesSet.getKeySortValues());
        }
      }
      else if(newSize == 0)
      {
        delete(txn, key);
      }
      else
      {
        byte[] after = sortValuesSet.toDatabase();
        data.setData(after);
        put(txn, key, data);
      }

      count.getAndAdd(newSize - oldSize);
    }
  }

  private SortValues moveToNextSortValues(Iterator<SortValues> sortValues)
  {
    sortValues.remove();
    if (sortValues.hasNext())
    {
      return sortValues.next();
    }
    return null;
  }

  private byte[] encodeKey(SortValues sv) throws DirectoryException
  {
    return encodeKey(sv.getEntryID(), sv.getValues(), sv.getTypes());
  }

  /**
   * Evaluate a search with sort control using this VLV index.
   *
   * @param txn The transaction to used when reading the index or NULL if it is
   *            not required.
   * @param searchOperation The search operation to evaluate.
   * @param sortControl The sort request control to evaluate.
   * @param vlvRequest The VLV request control to evaluate or NULL if VLV is not
   *                   requested.
   * @param debugBuilder If not null, a diagnostic string will be written
   *                     which will help determine how this index contributed
   *                     to this search.
   * @return The sorted EntryIDSet containing the entry IDs that match the
   *         search criteria.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  EntryIDSet evaluate(Transaction txn,
                             SearchOperation searchOperation,
                             ServerSideSortRequestControl sortControl,
                             VLVRequestControl vlvRequest,
                             StringBuilder debugBuilder)
      throws DirectoryException, DatabaseException
  {
    if (!trusted
        || !searchOperation.getBaseDN().equals(baseDN)
        || !searchOperation.getScope().equals(scope)
        || !searchOperation.getFilter().equals(filter)
        || !sortControl.getSortOrder().equals(sortOrder))
    {
      return null;
    }

    if (debugBuilder != null)
    {
      debugBuilder.append("vlv=");
      debugBuilder.append("[INDEX:");
      debugBuilder.append(name.replace(entryContainer.getDatabasePrefix() + "_", ""));
      debugBuilder.append("]");
    }

    long[] selectedIDs = new long[0];
    if(vlvRequest != null)
    {
      int currentCount = count.get();
      int beforeCount = vlvRequest.getBeforeCount();
      int afterCount  = vlvRequest.getAfterCount();

      if (vlvRequest.getTargetType() == VLVRequestControl.TYPE_TARGET_BYOFFSET)
      {
        int targetOffset = vlvRequest.getOffset();
        if (targetOffset < 0)
        {
          // The client specified a negative target offset.  This should never
          // be allowed.
          searchOperation.addResponseControl(
              new VLVResponseControl(targetOffset, currentCount,
                                     LDAPResultCode.OFFSET_RANGE_ERROR));

          LocalizableMessage message = ERR_ENTRYIDSORTER_NEGATIVE_START_POS.get();
          throw new DirectoryException(ResultCode.VIRTUAL_LIST_VIEW_ERROR,
                                       message);
        }
        else if (targetOffset == 0)
        {
          // This is an easy mistake to make, since VLV offsets start at 1
          // instead of 0.  We'll assume the client meant to use 1.
          targetOffset = 1;
        }
        int listOffset = targetOffset - 1; // VLV offsets start at 1, not 0.
        int startPos = listOffset - beforeCount;
        if (startPos < 0)
        {
          // This can happen if beforeCount >= offset, and in this case we'll
          // just adjust the start position to ignore the range of beforeCount
          // that doesn't exist.
          startPos    = 0;
          beforeCount = listOffset;
        }
        else if(startPos >= currentCount)
        {
          // The start position is beyond the end of the list.  In this case,
          // we'll assume that the start position was one greater than the
          // size of the list and will only return the beforeCount entries.
          // The start position is beyond the end of the list.  In this case,
          // we'll assume that the start position was one greater than the
          // size of the list and will only return the beforeCount entries.
          targetOffset = currentCount + 1;
          listOffset   = currentCount;
          startPos     = listOffset - beforeCount;
          afterCount   = 0;
        }

        int count = 1 + beforeCount + afterCount;
        selectedIDs = new long[count];

        Cursor cursor = openCursor(txn, CursorConfig.READ_COMMITTED);
        try
        {
          DatabaseEntry key = new DatabaseEntry();
          DatabaseEntry data = new DatabaseEntry();
          LockMode lockMode = LockMode.DEFAULT;
          //Locate the set that contains the target entry.
          int cursorCount = 0;
          int selectedPos = 0;
          OperationStatus status = cursor.getFirst(key, data, lockMode);
          while(status == OperationStatus.SUCCESS)
          {
            if(logger.isTraceEnabled())
            {
              logSearchKeyResult(key);
            }
            long[] IDs = SortValuesSet.getEncodedIDs(data.getData(), 0);
            for(int i = startPos + selectedPos - cursorCount;
                i < IDs.length && selectedPos < count;
                i++, selectedPos++)
            {
              selectedIDs[selectedPos] = IDs[i];
            }
            cursorCount += IDs.length;
            status = cursor.getNext(key, data,lockMode);
          }

          if (selectedPos < count)
          {
            // We don't have enough entries in the set to meet the requested
            // page size, so we'll need to shorten the array.
            long[] newIDArray = new long[selectedPos];
            System.arraycopy(selectedIDs, 0, newIDArray, 0, selectedPos);
            selectedIDs = newIDArray;
          }

          searchOperation.addResponseControl(
              new VLVResponseControl(targetOffset, currentCount,
                                     LDAPResultCode.SUCCESS));

          if(debugBuilder != null)
          {
            debugBuilder.append("[COUNT:");
            debugBuilder.append(cursorCount);
            debugBuilder.append("]");
          }
        }
        finally
        {
          cursor.close();
        }
      }
      else
      {
        int targetOffset = 0;
        int includedBeforeCount = 0;
        int includedAfterCount  = 0;
        LinkedList<EntryID> idList = new LinkedList<EntryID>();
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        Cursor cursor = openCursor(txn, CursorConfig.READ_COMMITTED);
        try
        {
          LockMode lockMode = LockMode.DEFAULT;
          ByteSequence vBytes = vlvRequest.getGreaterThanOrEqualAssertion();
          ByteStringBuilder keyBytes =
              new ByteStringBuilder(vBytes.length() + 4);
          keyBytes.appendBERLength(vBytes.length());
          vBytes.copyTo(keyBytes);

          key.setData(keyBytes.getBackingArray(), 0, keyBytes.length());
          OperationStatus status = cursor.getSearchKeyRange(key, data, lockMode);
          if(status == OperationStatus.SUCCESS)
          {
            if(logger.isTraceEnabled())
            {
              logSearchKeyResult(key);
            }
            SortValuesSet sortValuesSet =
                new SortValuesSet(key.getData(), data.getData(), this);

            int adjustedTargetOffset = sortValuesSet.binarySearch(
                -1, vlvRequest.getGreaterThanOrEqualAssertion());
            if(adjustedTargetOffset < 0)
            {
              // For a negative return value r, the vlvIndex -(r+1) gives the
              // array index of the ID that is greater then the assertion value.
              adjustedTargetOffset = -(adjustedTargetOffset+1);
            }

            targetOffset = adjustedTargetOffset;

            // Iterate through all the sort values sets before this one to find
            // the target offset in the index.
            int lastOffset = adjustedTargetOffset - 1;
            long[] lastIDs = sortValuesSet.getEntryIDs();
            while(true)
            {
              for(int i = lastOffset;
                  i >= 0 && includedBeforeCount < beforeCount; i--)
              {
                idList.addFirst(new EntryID(lastIDs[i]));
                includedBeforeCount++;
              }

              status = cursor.getPrev(key, data, lockMode);
              if(status != OperationStatus.SUCCESS)
              {
                break;
              }

              if(includedBeforeCount < beforeCount)
              {
                lastIDs = SortValuesSet.getEncodedIDs(data.getData(), 0);
                lastOffset = lastIDs.length - 1;
                targetOffset += lastIDs.length;
              }
              else
              {
                targetOffset += SortValuesSet.getEncodedSize(data.getData(), 0);
              }
            }


            // Set the cursor back to the position of the target entry set
            key.setData(sortValuesSet.getKeyBytes());
            cursor.getSearchKey(key, data, lockMode);

            // Add the target and after count entries if the target was found.
            lastOffset = adjustedTargetOffset;
            lastIDs = sortValuesSet.getEntryIDs();
            int afterIDCount = 0;
            while(true)
            {
              for(int i = lastOffset;
                  i < lastIDs.length && includedAfterCount < afterCount + 1;
                  i++)
              {
                idList.addLast(new EntryID(lastIDs[i]));
                includedAfterCount++;
              }

              if(includedAfterCount >= afterCount + 1)
              {
                break;
              }

              status = cursor.getNext(key, data, lockMode);
              if(status != OperationStatus.SUCCESS)
              {
                break;
              }

              lastIDs = SortValuesSet.getEncodedIDs(data.getData(), 0);
              lastOffset = 0;
              afterIDCount += lastIDs.length;
            }

            selectedIDs = new long[idList.size()];
            Iterator<EntryID> idIterator = idList.iterator();
            for (int i=0; i < selectedIDs.length; i++)
            {
              selectedIDs[i] = idIterator.next().longValue();
            }

            searchOperation.addResponseControl(
                new VLVResponseControl(targetOffset + 1, currentCount,
                                       LDAPResultCode.SUCCESS));

            if(debugBuilder != null)
            {
              debugBuilder.append("[COUNT:");
              debugBuilder.append(targetOffset + afterIDCount + 1);
              debugBuilder.append("]");
            }
          }
        }
        finally
        {
          cursor.close();
        }
      }
    }
    else
    {
      LinkedList<long[]> idSets = new LinkedList<long[]>();
      int currentCount = 0;
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();

      Cursor cursor = openCursor(txn, CursorConfig.READ_COMMITTED);
      try
      {
        LockMode lockMode = LockMode.RMW;
        OperationStatus status = cursor.getFirst(key, data, lockMode);
        while(status == OperationStatus.SUCCESS)
        {
          if(logger.isTraceEnabled())
          {
            logSearchKeyResult(key);
          }
          long[] ids = SortValuesSet.getEncodedIDs(data.getData(), 0);
          idSets.add(ids);
          currentCount += ids.length;
          status = cursor.getNext(key, data, lockMode);
        }
      }
      finally
      {
        cursor.close();
      }

      selectedIDs = new long[currentCount];
      int pos = 0;
      for(long[] id : idSets)
      {
        System.arraycopy(id, 0, selectedIDs, pos, id.length);
        pos += id.length;
      }

      if(debugBuilder != null)
      {
        debugBuilder.append("[COUNT:");
        debugBuilder.append(currentCount);
        debugBuilder.append("]");
      }
    }
    return new EntryIDSet(selectedIDs, 0, selectedIDs.length);
  }

    /**
   * Set the vlvIndex trust state.
   * @param txn A database transaction, or null if none is required.
   * @param trusted True if this vlvIndex should be trusted or false
   *                otherwise.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public synchronized void setTrusted(Transaction txn, boolean trusted)
      throws DatabaseException
  {
    this.trusted = trusted;
    state.putIndexTrustState(txn, this, trusted);
  }

  /**
   * Return true iff this index is trusted.
   * @return the trusted state of this index
   */
  public boolean isTrusted()
  {
    return trusted;
  }

  /**
   * Gets the values to sort on from the entry.
   *
   * @param entry The entry to get the values from.
   * @return The attribute values to sort on.
   */
  ByteString[] getSortValues(Entry entry)
  {
    SortKey[] sortKeys = sortOrder.getSortKeys();
    ByteString[] values = new ByteString[sortKeys.length];
    for (int i=0; i < sortKeys.length; i++)
    {
      SortKey sortKey = sortKeys[i];
      List<Attribute> attrList = entry.getAttribute(sortKey.getAttributeType());
      if (attrList != null)
      {
        // There may be multiple versions of this attribute in the target entry
        // (e.g., with different sets of options), and it may also be a
        // multivalued attribute.  In that case, we need to find the value that
        // is the best match for the corresponding sort key (i.e., for sorting
        // in ascending order, we want to find the lowest value; for sorting in
        // descending order, we want to find the highest value).  This is
        // handled by the SortKey.compareValues method.
        ByteString sortValue = null;
        for (Attribute a : attrList)
        {
          for (ByteString v : a)
          {
            if (sortValue == null || sortKey.compareValues(v, sortValue) < 0)
            {
              sortValue = v;
            }
          }
        }

        values[i] = sortValue;
      }
    }
    return values;
  }

  /**
   * Encode a VLV database key with the given information.
   *
   * @param entryID The entry ID to encode.
   * @param values The values to encode.
   * @param types The types of the values to encode.
   * @return The encoded bytes.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  byte[] encodeKey(long entryID, ByteString[] values, AttributeType[] types)
      throws DirectoryException
  {
    try
    {
      final ByteStringBuilder builder = new ByteStringBuilder();

      for (int i = 0; i < values.length; i++)
      {
        final ByteString v = values[i];
        if (v == null)
        {
          builder.appendBERLength(0);
        }
        else
        {
          final MatchingRule eqRule = types[i].getEqualityMatchingRule();
          final ByteString nv = eqRule.normalizeAttributeValue(v);
          builder.appendBERLength(nv.length());
          builder.append(nv);
        }
      }
      builder.append(entryID);
      builder.trimToSize();

      return builder.getBackingArray();
    }
    catch (DecodeException e)
    {
      throw new DirectoryException(
          ResultCode.INVALID_ATTRIBUTE_SYNTAX, e.getMessageObject(), e);
    }
  }

  /**
   * Decode a VLV database key.
   *
   * @param  keyBytes The byte array to decode.
   * @return The sort values represented by the key bytes.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  private SortValues decodeKey(byte[] keyBytes) throws DirectoryException
  {
    if(keyBytes == null || keyBytes.length == 0)
    {
      return null;
    }

    ByteString[] attributeValues = new ByteString[sortOrder.getSortKeys().length];
    int vBytesPos = 0;

    for(int i = 0; i < attributeValues.length; i++)
    {
      int valueLength = keyBytes[vBytesPos] & 0x7F;
      if (valueLength != keyBytes[vBytesPos++])
      {
        int valueLengthBytes = valueLength;
        valueLength = 0;
        for (int j=0; j < valueLengthBytes; j++, vBytesPos++)
        {
          valueLength = (valueLength << 8) | (keyBytes[vBytesPos] & 0xFF);
        }
      }

      if(valueLength == 0)
      {
        attributeValues[i] = null;
      }
      else
      {
        byte[] valueBytes = new byte[valueLength];
        System.arraycopy(keyBytes, vBytesPos, valueBytes, 0, valueLength);
        attributeValues[i] = ByteString.wrap(valueBytes);
      }

      vBytesPos += valueLength;
    }

    final long id = JebFormat.toLong(keyBytes, vBytesPos, keyBytes.length);
    return new SortValues(new EntryID(id), attributeValues, sortOrder);
  }

  /**
   * Get the sorted set capacity configured for this VLV index.
   *
   * @return The sorted set capacity.
   */
  public int getSortedSetCapacity()
  {
    return sortedSetCapacity;
  }

  /**
   * Indicates if the given entry should belong in this VLV index.
   *
   * @param entry The entry to check.
   * @return True if the given entry should belong in this VLV index or False
   *         otherwise.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  boolean shouldInclude(Entry entry) throws DirectoryException
  {
    DN entryDN = entry.getName();
    return entryDN.matchesBaseAndScope(baseDN, scope)
        && filter.matchesEntry(entry);
  }

  /** {@inheritDoc} */
  @Override
  public synchronized boolean isConfigurationChangeAcceptable(
      LocalDBVLVIndexCfg cfg,
      List<LocalizableMessage> unacceptableReasons)
  {
    try
    {
      this.filter = SearchFilter.createFilterFromString(cfg.getFilter());
    }
    catch(Exception e)
    {
      unacceptableReasons.add(ERR_CONFIG_VLV_INDEX_BAD_FILTER.get(
              cfg.getFilter(), name, stackTraceToSingleLineString(e)));
      return false;
    }

    String[] sortAttrs = cfg.getSortOrder().split(" ");
    SortKey[] sortKeys = new SortKey[sortAttrs.length];
    MatchingRule[] orderingRules = new MatchingRule[sortAttrs.length];
    boolean[] ascending = new boolean[sortAttrs.length];
    for(int i = 0; i < sortAttrs.length; i++)
    {
      try
      {
        if(sortAttrs[i].startsWith("-"))
        {
          ascending[i] = false;
          sortAttrs[i] = sortAttrs[i].substring(1);
        }
        else
        {
          ascending[i] = true;
          if(sortAttrs[i].startsWith("+"))
          {
            sortAttrs[i] = sortAttrs[i].substring(1);
          }
        }
      }
      catch(Exception e)
      {
        unacceptableReasons.add(ERR_CONFIG_VLV_INDEX_UNDEFINED_ATTR.get(sortKeys[i], name));
        return false;
      }

      AttributeType attrType = DirectoryServer.getAttributeType(sortAttrs[i].toLowerCase());
      if(attrType == null)
      {
        unacceptableReasons.add(ERR_CONFIG_VLV_INDEX_UNDEFINED_ATTR.get(sortAttrs[i], name));
        return false;
      }
      sortKeys[i] = new SortKey(attrType, ascending[i]);
      orderingRules[i] = attrType.getOrderingMatchingRule();
    }

    return true;
  }

  /** {@inheritDoc} */
  @Override
  public synchronized ConfigChangeResult applyConfigurationChange(
      LocalDBVLVIndexCfg cfg)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    // Update base DN only if changed..
    if(!config.getBaseDN().equals(cfg.getBaseDN()))
    {
      this.baseDN = cfg.getBaseDN();
      ccr.setAdminActionRequired(true);
    }

    // Update scope only if changed.
    if(!config.getScope().equals(cfg.getScope()))
    {
      this.scope = convertScope(cfg.getScope());
      ccr.setAdminActionRequired(true);
    }

    // Update sort set capacity only if changed.
    if (config.getMaxBlockSize() != cfg.getMaxBlockSize())
    {
      this.sortedSetCapacity = cfg.getMaxBlockSize();

      // Require admin action only if the new capacity is larger. Otherwise,
      // we will lazyly update the sorted sets.
      if (config.getMaxBlockSize() < cfg.getMaxBlockSize())
      {
        ccr.setAdminActionRequired(true);
      }
    }

    // Update the filter only if changed.
    if(!config.getFilter().equals(cfg.getFilter()))
    {
      try
      {
        this.filter = SearchFilter.createFilterFromString(cfg.getFilter());
        ccr.setAdminActionRequired(true);
      }
      catch(Exception e)
      {
        ccr.addMessage(ERR_CONFIG_VLV_INDEX_BAD_FILTER.get(config.getFilter(), name, stackTraceToSingleLineString(e)));
        ccr.setResultCodeIfSuccess(ResultCode.INVALID_ATTRIBUTE_SYNTAX);
      }
    }

    // Update the sort order only if changed.
    if (!config.getSortOrder().equals(cfg.getSortOrder()))
    {
      String[] sortAttrs = cfg.getSortOrder().split(" ");
      SortKey[] sortKeys = new SortKey[sortAttrs.length];
      MatchingRule[] orderingRules = new MatchingRule[sortAttrs.length];
      boolean[] ascending = new boolean[sortAttrs.length];
      for(int i = 0; i < sortAttrs.length; i++)
      {
        try
        {
          if(sortAttrs[i].startsWith("-"))
          {
            ascending[i] = false;
            sortAttrs[i] = sortAttrs[i].substring(1);
          }
          else
          {
            ascending[i] = true;
            if(sortAttrs[i].startsWith("+"))
            {
              sortAttrs[i] = sortAttrs[i].substring(1);
            }
          }
        }
        catch(Exception e)
        {
          ccr.addMessage(ERR_CONFIG_VLV_INDEX_UNDEFINED_ATTR.get(sortKeys[i], name));
          ccr.setResultCodeIfSuccess(ResultCode.INVALID_ATTRIBUTE_SYNTAX);
        }

        AttributeType attrType =
            DirectoryServer.getAttributeType(sortAttrs[i].toLowerCase());
        if(attrType == null)
        {
          ccr.addMessage(ERR_CONFIG_VLV_INDEX_UNDEFINED_ATTR.get(sortKeys[i], name));
          ccr.setResultCodeIfSuccess(ResultCode.INVALID_ATTRIBUTE_SYNTAX);
        }
        else
        {
          sortKeys[i] = new SortKey(attrType, ascending[i]);
          orderingRules[i] = attrType.getOrderingMatchingRule();
        }
      }

      this.sortOrder = new SortOrder(sortKeys);
      this.comparator = new VLVKeyComparator(orderingRules, ascending);

      // We have to close the database and open it using the new comparator.
      entryContainer.exclusiveLock.lock();
      try
      {
        close();
        this.dbConfig.setBtreeComparator(this.comparator);
        open();
      }
      catch(DatabaseException de)
      {
        ccr.addMessage(LocalizableMessage.raw(StaticUtils.stackTraceToSingleLineString(de)));
        ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
      }
      finally
      {
        entryContainer.exclusiveLock.unlock();
      }

      ccr.setAdminActionRequired(true);
    }


    if (ccr.adminActionRequired())
    {
      trusted = false;
      ccr.addMessage(NOTE_INDEX_ADD_REQUIRES_REBUILD.get(name));
      try
      {
        state.putIndexTrustState(null, this, false);
      }
      catch(DatabaseException de)
      {
        ccr.addMessage(LocalizableMessage.raw(StaticUtils.stackTraceToSingleLineString(de)));
        ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
      }
    }

    this.config = cfg;
    return ccr;
  }
}
