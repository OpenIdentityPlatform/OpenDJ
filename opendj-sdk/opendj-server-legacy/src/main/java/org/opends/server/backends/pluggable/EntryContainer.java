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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 *      Portions copyright 2013 Manuel Gaupp
 */
package org.opends.server.backends.pluggable;

import static org.opends.messages.JebMessages.*;
import static org.opends.server.backends.pluggable.JebFormat.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.types.AdditionalLogItem.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.util.Utils;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.server.BackendIndexCfg;
import org.opends.server.admin.std.server.BackendVLVIndexCfg;
import org.opends.server.admin.std.server.PluggableBackendCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.EntryCache;
import org.opends.server.api.plugin.PluginResult.SubordinateDelete;
import org.opends.server.api.plugin.PluginResult.SubordinateModifyDN;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableStorage;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableStorage;
import org.opends.server.controls.PagedResultsControl;
import org.opends.server.controls.ServerSideSortRequestControl;
import org.opends.server.controls.ServerSideSortResponseControl;
import org.opends.server.controls.SubtreeDeleteControl;
import org.opends.server.controls.VLVRequestControl;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Attributes;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.Operation;
import org.opends.server.types.Privilege;
import org.opends.server.types.RDN;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SortKey;
import org.opends.server.types.VirtualAttributeRule;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;

/**
 * Storage container for LDAP entries.  Each base DN of a JE backend is given
 * its own entry container.  The entry container is the object that implements
 * the guts of the backend API methods for LDAP operations.
 */
public class EntryContainer
    implements SuffixContainer, ConfigurationChangeListener<PluggableBackendCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The name of the entry database. */
  private static final String ID2ENTRY_DATABASE_NAME = ID2ENTRY_INDEX_NAME;
  /** The name of the DN database. */
  private static final String DN2ID_DATABASE_NAME = DN2ID_INDEX_NAME;
  /** The name of the children index database. */
  private static final String ID2CHILDREN_DATABASE_NAME = ID2CHILDREN_INDEX_NAME;
  /** The name of the subtree index database. */
  private static final String ID2SUBTREE_DATABASE_NAME = ID2SUBTREE_INDEX_NAME;
  /** The name of the referral database. */
  private static final String REFERRAL_DATABASE_NAME = REFERRAL_INDEX_NAME;
  /** The name of the state database. */
  private static final String STATE_DATABASE_NAME = STATE_INDEX_NAME;

  /** The attribute index configuration manager. */
  private final AttributeJEIndexCfgManager attributeJEIndexCfgManager;
  /** The vlv index configuration manager. */
  private final VLVJEIndexCfgManager vlvJEIndexCfgManager;

  /** The backend to which this entry container belongs. */
  private final Backend<?> backend;

  /** The root container in which this entryContainer belongs. */
  private final RootContainer rootContainer;

  /** The baseDN this entry container is responsible for. */
  private final DN baseDN;

  /** The backend configuration. */
  private PluggableBackendCfg config;

  /** The JE database environment. */
  private final Storage storage;

  /** The DN database maps a normalized DN string to an entry ID (8 bytes). */
  private DN2ID dn2id;
  /** The entry database maps an entry ID (8 bytes) to a complete encoded entry. */
  private ID2Entry id2entry;
  /** Index maps entry ID to an entry ID list containing its children. */
  private Index id2children;
  /** Index maps entry ID to an entry ID list containing its subordinates. */
  private Index id2subtree;
  /** The referral database maps a normalized DN string to labeled URIs. */
  private DN2URI dn2uri;
  /** The state database maps a config DN to config entries. */
  private State state;

  /** The set of attribute indexes. */
  private final HashMap<AttributeType, AttributeIndex> attrIndexMap = new HashMap<AttributeType, AttributeIndex>();

  /** The set of VLV (Virtual List View) indexes. */
  private final HashMap<String, VLVIndex> vlvIndexMap = new HashMap<String, VLVIndex>();

  /**
   * Prevents name clashes for common indexes (like id2entry) across multiple suffixes.
   * For example when a root container contains multiple suffixes.
   */
  private final String databasePrefix;

  /**
   * This class is responsible for managing the configuration for attribute
   * indexes used within this entry container.
   */
  private class AttributeJEIndexCfgManager implements
  ConfigurationAddListener<BackendIndexCfg>,
  ConfigurationDeleteListener<BackendIndexCfg>
  {
    /** {@inheritDoc} */
    @Override
    public boolean isConfigurationAddAcceptable(final BackendIndexCfg cfg, List<LocalizableMessage> unacceptableReasons)
    {
      try
      {
        // FIXME this should be a read operation, but I cannot change it
        // because of AttributeIndex ctor.
        storage.write(new WriteOperation()
        {
          @Override
          public void run(WriteableStorage txn) throws Exception
          {
            //Try creating all the indexes before confirming they are valid ones.
            new AttributeIndex(cfg, EntryContainer.this, txn);
          }
        });
        return true;
      }
      catch(Exception e)
      {
        unacceptableReasons.add(LocalizableMessage.raw(e.getLocalizedMessage()));
        return false;
      }
    }

    /** {@inheritDoc} */
    @Override
    public ConfigChangeResult applyConfigurationAdd(final BackendIndexCfg cfg)
    {
      final ConfigChangeResult ccr = new ConfigChangeResult();
      try
      {
        storage.write(new WriteOperation()
        {
          @Override
          public void run(WriteableStorage txn) throws Exception
          {
            final AttributeIndex index = new AttributeIndex(cfg, EntryContainer.this, txn);
            index.open(txn);
            if (!index.isTrusted())
            {
              ccr.setAdminActionRequired(true);
              ccr.addMessage(NOTE_JEB_INDEX_ADD_REQUIRES_REBUILD.get(cfg.getAttribute().getNameOrOID()));
            }
            attrIndexMap.put(cfg.getAttribute(), index);
          }
        });
      }
      catch(Exception e)
      {
        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
        ccr.addMessage(LocalizableMessage.raw(e.getLocalizedMessage()));
      }
      return ccr;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isConfigurationDeleteAcceptable(
        BackendIndexCfg cfg, List<LocalizableMessage> unacceptableReasons)
    {
      // TODO: validate more before returning true?
      return true;
    }

    /** {@inheritDoc} */
    @Override
    public ConfigChangeResult applyConfigurationDelete(final BackendIndexCfg cfg)
    {
      final ConfigChangeResult ccr = new ConfigChangeResult();

      exclusiveLock.lock();
      try
      {
        storage.write(new WriteOperation()
        {
          @Override
          public void run(WriteableStorage txn) throws Exception
          {
            AttributeIndex index = attrIndexMap.get(cfg.getAttribute());
            deleteAttributeIndex(txn, index);
            attrIndexMap.remove(cfg.getAttribute());
          }
        });
      }
      catch (Exception de)
      {
        ccr.setResultCode(getServerErrorResultCode());
        ccr.addMessage(LocalizableMessage.raw(StaticUtils.stackTraceToSingleLineString(de)));
      }
      finally
      {
        exclusiveLock.unlock();
      }

      return ccr;
    }
  }

  /**
   * This class is responsible for managing the configuration for VLV indexes
   * used within this entry container.
   */
  private class VLVJEIndexCfgManager implements
  ConfigurationAddListener<BackendVLVIndexCfg>,
  ConfigurationDeleteListener<BackendVLVIndexCfg>
  {
    /** {@inheritDoc} */
    @Override
    public boolean isConfigurationAddAcceptable(
        BackendVLVIndexCfg cfg, List<LocalizableMessage> unacceptableReasons)
    {
      try
      {
        SearchFilter.createFilterFromString(cfg.getFilter());
      }
      catch(Exception e)
      {
        LocalizableMessage msg = ERR_JEB_CONFIG_VLV_INDEX_BAD_FILTER.get(
            cfg.getFilter(), cfg.getName(),
            e.getLocalizedMessage());
        unacceptableReasons.add(msg);
        return false;
      }

      String[] sortAttrs = cfg.getSortOrder().split(" ");
      SortKey[] sortKeys = new SortKey[sortAttrs.length];
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
          LocalizableMessage msg =
            ERR_JEB_CONFIG_VLV_INDEX_UNDEFINED_ATTR.get(sortKeys[i], cfg.getName());
          unacceptableReasons.add(msg);
          return false;
        }

        AttributeType attrType =
          DirectoryServer.getAttributeType(sortAttrs[i].toLowerCase());
        if(attrType == null)
        {
          LocalizableMessage msg = ERR_JEB_CONFIG_VLV_INDEX_UNDEFINED_ATTR.get(
              sortAttrs[i], cfg.getName());
          unacceptableReasons.add(msg);
          return false;
        }
        sortKeys[i] = new SortKey(attrType, ascending[i]);
      }

      return true;
    }

    /** {@inheritDoc} */
    @Override
    public ConfigChangeResult applyConfigurationAdd(final BackendVLVIndexCfg cfg)
    {
      final ConfigChangeResult ccr = new ConfigChangeResult();
      try
      {
        storage.write(new WriteOperation()
        {
          @Override
          public void run(WriteableStorage txn) throws Exception
          {
            VLVIndex vlvIndex = new VLVIndex(cfg, state, storage, EntryContainer.this, txn);
            vlvIndex.open(txn);
            if(!vlvIndex.isTrusted())
            {
              ccr.setAdminActionRequired(true);
              ccr.addMessage(NOTE_JEB_INDEX_ADD_REQUIRES_REBUILD.get(cfg.getName()));
            }
            vlvIndexMap.put(cfg.getName().toLowerCase(), vlvIndex);
          }
        });
      }
      catch(Exception e)
      {
        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
        ccr.addMessage(LocalizableMessage.raw(StaticUtils.stackTraceToSingleLineString(e)));
      }
      return ccr;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isConfigurationDeleteAcceptable(BackendVLVIndexCfg cfg, List<LocalizableMessage> unacceptableReasons)
    {
      // TODO: validate more before returning true?
      return true;
    }

    /** {@inheritDoc} */
    @Override
    public ConfigChangeResult applyConfigurationDelete(final BackendVLVIndexCfg cfg)
    {
      final ConfigChangeResult ccr = new ConfigChangeResult();
      exclusiveLock.lock();
      try
      {
        storage.write(new WriteOperation()
        {
          @Override
          public void run(WriteableStorage txn) throws Exception
          {
            VLVIndex vlvIndex = vlvIndexMap.get(cfg.getName().toLowerCase());
            deleteDatabase(txn, vlvIndex);
            vlvIndexMap.remove(cfg.getName());
          }
        });
      }
      catch (Exception e)
      {
        ccr.setResultCode(getServerErrorResultCode());
        ccr.addMessage(LocalizableMessage.raw(StaticUtils.stackTraceToSingleLineString(e)));
      }
      finally
      {
        exclusiveLock.unlock();
      }
      return ccr;
    }

  }

  /** A read write lock to handle schema changes and bulk changes. */
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  final Lock sharedLock = lock.readLock();
  final Lock exclusiveLock = lock.writeLock();

  /**
   * Create a new entry container object.
   *
   * @param baseDN  The baseDN this entry container will be responsible for
   *                storing on disk.
   * @param backend A reference to the JE backend that is creating this entry
   *                container. It is needed by the Directory Server entry cache
   *                methods.
   * @param config The configuration of the JE backend.
   * @param env The JE environment to create this entryContainer in.
   * @param rootContainer The root container this entry container is in.
   * @throws ConfigException if a configuration related error occurs.
   */
  EntryContainer(DN baseDN, Backend<?> backend, PluggableBackendCfg config, Storage env, RootContainer rootContainer)
      throws ConfigException
  {
    this.backend = backend;
    this.baseDN = baseDN;
    this.config = config;
    this.storage = env;
    this.rootContainer = rootContainer;
    this.databasePrefix = baseDN.toNormalizedUrlSafeString();

    config.addPluggableChangeListener(this);

    attributeJEIndexCfgManager = new AttributeJEIndexCfgManager();
    config.addBackendIndexAddListener(attributeJEIndexCfgManager);
    config.addBackendIndexDeleteListener(attributeJEIndexCfgManager);

    vlvJEIndexCfgManager = new VLVJEIndexCfgManager();
    config.addBackendVLVIndexAddListener(vlvJEIndexCfgManager);
    config.addBackendVLVIndexDeleteListener(vlvJEIndexCfgManager);
  }

  private TreeName getIndexName(String indexId)
  {
    return new TreeName(databasePrefix, indexId);
  }

  /**
   * Opens the entryContainer for reading and writing.
   *
   * @param txn The database transaction
   * @throws StorageRuntimeException If an error occurs in the JE database.
   * @throws ConfigException if a configuration related error occurs.
   */
  void open(WriteableStorage txn) throws StorageRuntimeException, ConfigException
  {
    try
    {
      DataConfig entryDataConfig =
        new DataConfig(config.isEntriesCompressed(),
            config.isCompactEncoding(),
            rootContainer.getCompressedSchema());

      id2entry = new ID2Entry(getIndexName(ID2ENTRY_DATABASE_NAME), entryDataConfig, storage, this);
      id2entry.open(txn);

      dn2id = new DN2ID(getIndexName(DN2ID_DATABASE_NAME), storage, this);
      dn2id.open(txn);

      state = new State(getIndexName(STATE_DATABASE_NAME), storage, this);
      state.open(txn);

      if (config.isSubordinateIndexesEnabled())
      {
        openSubordinateIndexes(txn);
      }
      else
      {
        // Use a null index and ensure that future attempts to use the real
        // subordinate indexes will fail.
        id2children = new NullIndex(getIndexName(ID2CHILDREN_DATABASE_NAME),
            new ID2CIndexer(), state, storage, txn, this);
        state.putIndexTrustState(txn, id2children, false);
        id2children.open(txn); // No-op

        id2subtree = new NullIndex(getIndexName(ID2SUBTREE_DATABASE_NAME),
            new ID2SIndexer(), state, storage, txn, this);
        state.putIndexTrustState(txn, id2subtree, false);
        id2subtree.open(txn); // No-op

        logger.info(NOTE_JEB_SUBORDINATE_INDEXES_DISABLED, backend.getBackendID());
      }

      dn2uri = new DN2URI(getIndexName(REFERRAL_DATABASE_NAME), storage, this);
      dn2uri.open(txn);

      for (String idx : config.listBackendIndexes())
      {
        BackendIndexCfg indexCfg = config.getBackendIndex(idx);

        AttributeIndex index = new AttributeIndex(indexCfg, this, txn);
        index.open(txn);
        if(!index.isTrusted())
        {
          logger.info(NOTE_JEB_INDEX_ADD_REQUIRES_REBUILD, index.getName());
        }
        attrIndexMap.put(indexCfg.getAttribute(), index);
      }

      for (String idx : config.listBackendVLVIndexes())
      {
        BackendVLVIndexCfg vlvIndexCfg = config.getBackendVLVIndex(idx);

        VLVIndex vlvIndex = new VLVIndex(vlvIndexCfg, state, storage, this, txn);
        vlvIndex.open(txn);

        if(!vlvIndex.isTrusted())
        {
          logger.info(NOTE_JEB_INDEX_ADD_REQUIRES_REBUILD, vlvIndex.getName());
        }

        vlvIndexMap.put(vlvIndexCfg.getName().toLowerCase(), vlvIndex);
      }
    }
    catch (StorageRuntimeException de)
    {
      logger.traceException(de);
      close();
      throw de;
    }
  }

  /**
   * Closes the entry container.
   *
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  @Override
  public void close() throws StorageRuntimeException
  {
    // Close core indexes.
    dn2id.close();
    id2entry.close();
    dn2uri.close();
    id2children.close();
    id2subtree.close();
    state.close();

    Utils.closeSilently(attrIndexMap.values());

    for (VLVIndex vlvIndex : vlvIndexMap.values())
    {
      vlvIndex.close();
    }

    // Deregister any listeners.
    config.removePluggableChangeListener(this);
    config.removeBackendIndexAddListener(attributeJEIndexCfgManager);
    config.removeBackendIndexDeleteListener(attributeJEIndexCfgManager);
    config.removeBackendVLVIndexAddListener(vlvJEIndexCfgManager);
    config.removeBackendVLVIndexDeleteListener(vlvJEIndexCfgManager);
  }

  /**
   * Retrieves a reference to the root container in which this entry container
   * exists.
   *
   * @return  A reference to the root container in which this entry container
   *          exists.
   */
  RootContainer getRootContainer()
  {
    return rootContainer;
  }

  /**
   * Get the DN database used by this entry container.
   * The entryContainer must have been opened.
   *
   * @return The DN database.
   */
  DN2ID getDN2ID()
  {
    return dn2id;
  }

  /**
   * Get the entry database used by this entry container.
   * The entryContainer must have been opened.
   *
   * @return The entry database.
   */
  ID2Entry getID2Entry()
  {
    return id2entry;
  }

  /**
   * Get the referral database used by this entry container.
   * The entryContainer must have been opened.
   *
   * @return The referral database.
   */
  DN2URI getDN2URI()
  {
    return dn2uri;
  }

  /**
   * Get the children database used by this entry container.
   * The entryContainer must have been opened.
   *
   * @return The children database.
   */
  Index getID2Children()
  {
    return id2children;
  }

  /**
   * Get the subtree database used by this entry container.
   * The entryContainer must have been opened.
   *
   * @return The subtree database.
   */
  Index getID2Subtree()
  {
    return id2subtree;
  }

  /**
   * Look for an attribute index for the given attribute type.
   *
   * @param attrType The attribute type for which an attribute index is needed.
   * @return The attribute index or null if there is none for that type.
   */
  AttributeIndex getAttributeIndex(AttributeType attrType)
  {
    return attrIndexMap.get(attrType);
  }

  /**
   * Return attribute index map.
   *
   * @return The attribute index map.
   */
  Map<AttributeType, AttributeIndex> getAttributeIndexMap()
  {
    return attrIndexMap;
  }

  /**
   * Look for an VLV index for the given index name.
   *
   * @param vlvIndexName The vlv index name for which an vlv index is needed.
   * @return The VLV index or null if there is none with that name.
   */
  VLVIndex getVLVIndex(String vlvIndexName)
  {
    return vlvIndexMap.get(vlvIndexName);
  }

  /**
   * Retrieve all attribute indexes.
   *
   * @return All attribute indexes defined in this entry container.
   */
  Collection<AttributeIndex> getAttributeIndexes()
  {
    return attrIndexMap.values();
  }

  /**
   * Retrieve all VLV indexes.
   *
   * @return The collection of VLV indexes defined in this entry container.
   */
  Collection<VLVIndex> getVLVIndexes()
  {
    return vlvIndexMap.values();
  }

  /**
   * Determine the highest entryID in the entryContainer.
   * The entryContainer must already be open.
   *
   * @param txn The database transaction
   * @return The highest entry ID.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  EntryID getHighestEntryID(ReadableStorage txn) throws StorageRuntimeException
  {
    Cursor cursor = txn.openCursor(id2entry.getName());
    try
    {
      // Position a cursor on the last data item, and the key should give the highest ID.
      if (cursor.positionToLastKey())
      {
        return new EntryID(cursor.getKey());
      }
      return new EntryID(0);
    }
    finally
    {
      cursor.close();
    }
  }

  /**
   * Determine the number of subordinate entries for a given entry.
   *
   * @param entryDN The distinguished name of the entry.
   * @param subtree <code>true</code> will include all the entries under the
   *                given entries. <code>false</code> will only return the
   *                number of entries immediately under the given entry.
   * @return The number of subordinate entries for the given entry or -1 if
   *         the entry does not exist.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  long getNumSubordinates(final DN entryDN, final boolean subtree)
  throws StorageRuntimeException
  {
    try
    {
      return storage.read(new ReadOperation<Long>()
      {
        @Override
        public Long run(ReadableStorage txn) throws Exception
        {
          EntryID entryID = dn2id.get(txn, entryDN, false);
          if (entryID != null)
          {
            ByteString key = entryID.toByteString();
            EntryIDSet entryIDSet;
            if (subtree)
            {
              entryIDSet = id2subtree.readKey(key, txn);
            }
            else
            {
              entryIDSet = id2children.readKey(key, txn);
            }
            long count = entryIDSet.size();
            if (count != Long.MAX_VALUE)
            {
              return count;
            }
          }
          return -1L;
        }
      });
    }
    catch (Exception e)
    {
      throw new StorageRuntimeException(e);
    }
  }

  /**
   * Processes the specified search in this entryContainer.
   * Matching entries should be provided back to the core server using the
   * <CODE>SearchOperation.returnEntry</CODE> method.
   *
   * @param searchOperation The search operation to be processed.
   * @throws DirectoryException
   *          If a problem occurs while processing the
   *          search.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   * @throws CanceledOperationException if this operation should be cancelled.
   */
  void search(final SearchOperation searchOperation)
  throws DirectoryException, StorageRuntimeException, CanceledOperationException
  {
    try
    {
      storage.read(new ReadOperation<Void>()
      {
        @Override
        public Void run(ReadableStorage txn) throws Exception
        {
          DN aBaseDN = searchOperation.getBaseDN();
          SearchScope searchScope = searchOperation.getScope();

          PagedResultsControl pageRequest = searchOperation.getRequestControl(PagedResultsControl.DECODER);
          ServerSideSortRequestControl sortRequest =
              searchOperation.getRequestControl(ServerSideSortRequestControl.DECODER);
          if (sortRequest != null && !sortRequest.containsSortKeys() && sortRequest.isCritical())
          {
            /*
             * If the control's criticality field is true then the server SHOULD
             * do the following: return unavailableCriticalExtension as a return
             * code in the searchResultDone message; include the
             * sortKeyResponseControl in the searchResultDone message, and not
             * send back any search result entries.
             */
            searchOperation.addResponseControl(new ServerSideSortResponseControl(NO_SUCH_ATTRIBUTE, null));
            searchOperation.setResultCode(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);
            return null;
          }
          VLVRequestControl vlvRequest = searchOperation.getRequestControl(VLVRequestControl.DECODER);

          if (vlvRequest != null && pageRequest != null)
          {
            LocalizableMessage message = ERR_JEB_SEARCH_CANNOT_MIX_PAGEDRESULTS_AND_VLV.get();
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
          }

          // Handle client abandon of paged results.
          if (pageRequest != null)
          {
            if (pageRequest.getSize() == 0)
            {
              Control control = new PagedResultsControl(pageRequest.isCritical(), 0, null);
              searchOperation.getResponseControls().add(control);
              return null;
            }
            if (searchOperation.getSizeLimit() > 0 && pageRequest.getSize() >= searchOperation.getSizeLimit())
            {
              // The RFC says : "If the page size is greater than or equal to the
              // sizeLimit value, the server should ignore the control as the
              // request can be satisfied in a single page"
              pageRequest = null;
            }
          }

          // Handle base-object search first.
          if (searchScope == SearchScope.BASE_OBJECT)
          {
            final Entry baseEntry = fetchBaseEntry(txn, aBaseDN, searchScope);
            if (!isManageDsaITOperation(searchOperation))
            {
              dn2uri.checkTargetForReferral(baseEntry, searchOperation.getScope());
            }

            if (searchOperation.getFilter().matchesEntry(baseEntry))
            {
              searchOperation.returnEntry(baseEntry, null);
            }

            if (pageRequest != null)
            {
              // Indicate no more pages.
              Control control = new PagedResultsControl(pageRequest.isCritical(), 0, null);
              searchOperation.getResponseControls().add(control);
            }

            return null;
          }

          // Check whether the client requested debug information about the
          // contribution of the indexes to the search.
          StringBuilder debugBuffer = null;
          if (searchOperation.getAttributes().contains(ATTR_DEBUG_SEARCH_INDEX))
          {
            debugBuffer = new StringBuilder();
          }

          EntryIDSet entryIDList = null;
          boolean candidatesAreInScope = false;
          if (sortRequest != null)
          {
            for (VLVIndex vlvIndex : vlvIndexMap.values())
            {
              try
              {
                entryIDList = vlvIndex.evaluate(null, searchOperation, sortRequest, vlvRequest, debugBuffer);
                if (entryIDList != null)
                {
                  searchOperation.addResponseControl(new ServerSideSortResponseControl(SUCCESS, null));
                  candidatesAreInScope = true;
                  break;
                }
              }
              catch (DirectoryException de)
              {
                searchOperation.addResponseControl(new ServerSideSortResponseControl(de.getResultCode().intValue(),
                    null));

                if (sortRequest.isCritical())
                {
                  throw de;
                }
              }
            }
          }

          if (entryIDList == null)
          {
            // See if we could use a virtual attribute rule to process the
            // search.
            for (VirtualAttributeRule rule : DirectoryServer.getVirtualAttributes())
            {
              if (rule.getProvider().isSearchable(rule, searchOperation, true))
              {
                rule.getProvider().processSearch(rule, searchOperation);
                return null;
              }
            }

            // Create an index filter to get the search result candidate entries
            IndexFilter indexFilter = new IndexFilter(
                EntryContainer.this, txn, searchOperation, debugBuffer, rootContainer.getMonitorProvider());

            // Evaluate the filter against the attribute indexes.
            entryIDList = indexFilter.evaluate();

            // Evaluate the search scope against the id2children and id2subtree indexes
            if (entryIDList.size() > IndexFilter.FILTER_CANDIDATE_THRESHOLD)
            {
              // Read the ID from dn2id.
              EntryID baseID = dn2id.get(txn, aBaseDN, false);
              if (baseID == null)
              {
                LocalizableMessage message = ERR_JEB_SEARCH_NO_SUCH_OBJECT.get(aBaseDN);
                DN matchedDN = getMatchedDN(aBaseDN);
                throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, matchedDN, null);
              }
              ByteString baseIDData = baseID.toByteString();

              EntryIDSet scopeList;
              if (searchScope == SearchScope.SINGLE_LEVEL)
              {
                scopeList = id2children.readKey(baseIDData, txn);
              }
              else
              {
                scopeList = id2subtree.readKey(baseIDData, txn);
                if (searchScope == SearchScope.WHOLE_SUBTREE)
                {
                  // The id2subtree list does not include the base entry ID.
                  scopeList.add(baseID);
                }
              }
              entryIDList.retainAll(scopeList);
              if (debugBuffer != null)
              {
                debugBuffer.append(" scope=");
                debugBuffer.append(searchScope);
                scopeList.toString(debugBuffer);
              }
              if (scopeList.isDefined())
              {
                // In this case we know that every candidate is in scope.
                candidatesAreInScope = true;
              }
            }

            if (sortRequest != null)
            {
              try
              {
                // If the sort key is not present, the sorting will generate the
                // default ordering. VLV search request goes through as if
                // this sort key was not found in the user entry.
                entryIDList =
                    EntryIDSetSorter.sort(EntryContainer.this, txn, entryIDList, searchOperation,
                        sortRequest.getSortOrder(), vlvRequest);
                if (sortRequest.containsSortKeys())
                {
                  searchOperation.addResponseControl(new ServerSideSortResponseControl(SUCCESS, null));
                }
                else
                {
                  /*
                   * There is no sort key associated with the sort control.
                   * Since it came here it means that the criticality is false
                   * so let the server return all search results unsorted and
                   * include the sortKeyResponseControl in the searchResultDone
                   * message.
                   */
                  searchOperation.addResponseControl(new ServerSideSortResponseControl(NO_SUCH_ATTRIBUTE, null));
                }
              }
              catch (DirectoryException de)
              {
                searchOperation.addResponseControl(new ServerSideSortResponseControl(de.getResultCode().intValue(),
                    null));

                if (sortRequest.isCritical())
                {
                  throw de;
                }
              }
            }
          }

          // If requested, construct and return a fictitious entry containing
          // debug information, and no other entries.
          if (debugBuffer != null)
          {
            debugBuffer.append(" final=");
            entryIDList.toString(debugBuffer);

            Attribute attr = Attributes.create(ATTR_DEBUG_SEARCH_INDEX, debugBuffer.toString());
            Entry debugEntry = new Entry(DN.valueOf("cn=debugsearch"), null, null, null);
            debugEntry.addAttribute(attr, new ArrayList<ByteString>());

            searchOperation.returnEntry(debugEntry, null);
            return null;
          }

          if (entryIDList.isDefined())
          {
            if (rootContainer.getMonitorProvider().isFilterUseEnabled())
            {
              rootContainer.getMonitorProvider().updateIndexedSearchCount();
            }
            searchIndexed(txn, entryIDList, candidatesAreInScope, searchOperation, pageRequest);
          }
          else
          {
            if (rootContainer.getMonitorProvider().isFilterUseEnabled())
            {
              rootContainer.getMonitorProvider().updateUnindexedSearchCount();
            }

            searchOperation.addAdditionalLogItem(keyOnly(getClass(), "unindexed"));

            // See if we could use a virtual attribute rule to process the
            // search.
            for (VirtualAttributeRule rule : DirectoryServer.getVirtualAttributes())
            {
              if (rule.getProvider().isSearchable(rule, searchOperation, false))
              {
                rule.getProvider().processSearch(rule, searchOperation);
                return null;
              }
            }

            ClientConnection clientConnection = searchOperation.getClientConnection();
            if (!clientConnection.hasPrivilege(Privilege.UNINDEXED_SEARCH, searchOperation))
            {
              LocalizableMessage message = ERR_JEB_SEARCH_UNINDEXED_INSUFFICIENT_PRIVILEGES.get();
              throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS, message);
            }

            if (sortRequest != null)
            {
              // FIXME -- Add support for sorting unindexed searches using
              // indexes
              // like DSEE currently does.
              searchOperation.addResponseControl(new ServerSideSortResponseControl(UNWILLING_TO_PERFORM, null));

              if (sortRequest.isCritical())
              {
                LocalizableMessage message = ERR_JEB_SEARCH_CANNOT_SORT_UNINDEXED.get();
                throw new DirectoryException(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION, message);
              }
            }

            searchNotIndexed(txn, searchOperation, pageRequest);
          }
          return null;
        }
      });
    }
    catch (Exception e)
    {
      throwAllowedExceptionTypes(e, DirectoryException.class, CanceledOperationException.class);
    }
  }

  private <E1 extends Exception, E2 extends Exception>
      void throwAllowedExceptionTypes(Exception e, Class<E1> clazz1, Class<E2> clazz2)
          throws E1, E2
  {
    throwIfPossible(e, clazz1, clazz2);
    if (e.getCause() != null)
    {
      throwIfPossible(e.getCause(), clazz1, clazz2);
    }
    else if (e instanceof StorageRuntimeException)
    {
      throw (StorageRuntimeException) e;
    }
    throw new StorageRuntimeException(e);
  }

  private <E1 extends Exception, E2 extends Exception> void throwIfPossible(final Throwable cause, Class<E1> clazz1,
      Class<E2> clazz2) throws E1, E2
  {
    if (clazz1.isAssignableFrom(cause.getClass()))
    {
      throw clazz1.cast(cause);
    }
    else if (clazz2.isAssignableFrom(cause.getClass()))
    {
      throw clazz2.cast(cause);
    }
  }

  /**
   * We were not able to obtain a set of candidate entry IDs for the
   * search from the indexes.
   * <p>
   * Here we are relying on the DN key order to ensure children are
   * returned after their parents.
   * <ul>
   * <li>iterate through a subtree range of the DN database
   * <li>discard non-children DNs if the search scope is single level
   * <li>fetch the entry by ID from the entry cache or the entry database
   * <li>return the entry if it matches the filter
   * </ul>
   *
   * @param searchOperation The search operation.
   * @param pageRequest A Paged Results control, or null if none.
   * @throws DirectoryException If an error prevented the search from being
   * processed.
   */
  private void searchNotIndexed(ReadableStorage txn, SearchOperation searchOperation, PagedResultsControl pageRequest)
      throws DirectoryException, CanceledOperationException
  {
    DN aBaseDN = searchOperation.getBaseDN();
    SearchScope searchScope = searchOperation.getScope();
    boolean manageDsaIT = isManageDsaITOperation(searchOperation);

    // The base entry must already have been processed if this is
    // a request for the next page in paged results.  So we skip
    // the base entry processing if the cookie is set.
    if (pageRequest == null || pageRequest.getCookie().length() == 0)
    {
      final Entry baseEntry = fetchBaseEntry(txn, aBaseDN, searchScope);
      if (!manageDsaIT)
      {
        dn2uri.checkTargetForReferral(baseEntry, searchScope);
      }

      /*
       * The base entry is only included for whole subtree search.
       */
      if (searchScope == SearchScope.WHOLE_SUBTREE
          && searchOperation.getFilter().matchesEntry(baseEntry))
      {
        searchOperation.returnEntry(baseEntry, null);
      }

      if (!manageDsaIT
          && !dn2uri.returnSearchReferences(txn, searchOperation)
          && pageRequest != null)
      {
        // Indicate no more pages.
        Control control = new PagedResultsControl(pageRequest.isCritical(), 0, null);
        searchOperation.getResponseControls().add(control);
      }
    }

    /*
     * We will iterate forwards through a range of the dn2id keys to
     * find subordinates of the target entry from the top of the tree
     * downwards. For example, any subordinates of "dc=example,dc=com" appear
     * in dn2id with a key ending in ",dc=example,dc=com". The entry
     * "cn=joe,ou=people,dc=example,dc=com" will appear after the entry
     * "ou=people,dc=example,dc=com".
     */
    ByteString baseDNKey = dnToDNKey(aBaseDN, this.baseDN.size());
    ByteStringBuilder suffix = copyOf(baseDNKey);
    ByteStringBuilder end = copyOf(baseDNKey);

    /*
     * Set the ending value to a value of equal length but slightly
     * greater than the suffix. Since keys are compared in
     * reverse order we must set the first byte (the comma).
     * No possibility of overflow here.
     */
    suffix.append((byte) 0x00);
    end.append((byte) 0x01);

    // Set the starting value.
    ByteSequence begin;
    if (pageRequest != null && pageRequest.getCookie().length() != 0)
    {
      // The cookie contains the DN of the next entry to be returned.
      try
      {
        begin = ByteString.wrap(pageRequest.getCookie().toByteArray());
      }
      catch (Exception e)
      {
        logger.traceException(e);
        String str = pageRequest.getCookie().toHexString();
        LocalizableMessage msg = ERR_JEB_INVALID_PAGED_RESULTS_COOKIE.get(str);
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, msg, e);
      }
    }
    else
    {
      // Set the starting value to the suffix.
      begin = suffix;
    }

    int lookthroughCount = 0;
    int lookthroughLimit = searchOperation.getClientConnection().getLookthroughLimit();

    try
    {
      final Cursor cursor = txn.openCursor(dn2id.getName());
      try
      {
        // Initialize the cursor very close to the starting value.
        boolean success = cursor.positionToKeyOrNext(begin);

        // Step forward until we pass the ending value.
        while (success && cursor.getKey().compareTo(end) < 0)
        {
          if (lookthroughLimit > 0 && lookthroughCount > lookthroughLimit)
          {
            // Lookthrough limit exceeded
            searchOperation.setResultCode(ResultCode.ADMIN_LIMIT_EXCEEDED);
            searchOperation.appendErrorMessage(NOTE_JEB_LOOKTHROUGH_LIMIT_EXCEEDED.get(lookthroughLimit));
            return;
          }

          // We have found a subordinate entry.
          EntryID entryID = new EntryID(cursor.getValue());
          boolean isInScope =
              searchScope != SearchScope.SINGLE_LEVEL
                  // Check if this entry is an immediate child.
                  || findDNKeyParent(cursor.getKey()) == baseDNKey.length();
          if (isInScope)
          {
            // Process the candidate entry.
            final Entry entry = getEntry(txn, entryID);
            if (entry != null)
            {
              lookthroughCount++;

              if ((manageDsaIT || entry.getReferralURLs() == null)
                  && searchOperation.getFilter().matchesEntry(entry))
              {
                if (pageRequest != null
                    && searchOperation.getEntriesSent() == pageRequest.getSize())
                {
                  // The current page is full.
                  // Set the cookie to remember where we were.
                  ByteString cookie = cursor.getKey();
                  Control control = new PagedResultsControl(pageRequest.isCritical(), 0, cookie);
                  searchOperation.getResponseControls().add(control);
                  return;
                }

                if (!searchOperation.returnEntry(entry, null))
                {
                  // We have been told to discontinue processing of the
                  // search. This could be due to size limit exceeded or
                  // operation cancelled.
                  return;
                }
              }
            }
          }

          searchOperation.checkIfCanceled(false);

          // Move to the next record.
          success = cursor.next();
        }
      }
      finally
      {
        cursor.close();
      }
    }
    catch (StorageRuntimeException e)
    {
      logger.traceException(e);
    }

    if (pageRequest != null)
    {
      // Indicate no more pages.
      Control control = new PagedResultsControl(pageRequest.isCritical(), 0, null);
      searchOperation.getResponseControls().add(control);
    }
  }

  /**
   * Returns the entry corresponding to the provided entryID.
   *
   * @param txn The database transaction
   * @param entryID
   *          the id of the entry to retrieve
   * @return the entry corresponding to the provided entryID
   * @throws DirectoryException
   *           If an error occurs retrieving the entry
   */
  Entry getEntry(ReadableStorage txn, EntryID entryID) throws DirectoryException
  {
    // Try the entry cache first.
    final EntryCache<?> entryCache = getEntryCache();
    final Entry cacheEntry = entryCache.getEntry(backend, entryID.longValue());
    if (cacheEntry != null)
    {
      return cacheEntry;
    }

    final Entry entry = id2entry.get(txn, entryID, false);
    if (entry != null)
    {
      // Put the entry in the cache making sure not to overwrite a newer copy
      // that may have been inserted since the time we read the cache.
      entryCache.putEntryIfAbsent(entry, backend, entryID.longValue());
    }
    return entry;
  }

  /**
   * We were able to obtain a set of candidate entry IDs for the
   * search from the indexes.
   * <p>
   * Here we are relying on ID order to ensure children are returned
   * after their parents.
   * <ul>
   * <li>Iterate through the candidate IDs
   * <li>fetch entry by ID from cache or id2entry
   * <li>put the entry in the cache if not present
   * <li>discard entries that are not in scope
   * <li>return entry if it matches the filter
   * </ul>
   *
   * @param entryIDList The candidate entry IDs.
   * @param candidatesAreInScope true if it is certain that every candidate
   *                             entry is in the search scope.
   * @param searchOperation The search operation.
   * @param pageRequest A Paged Results control, or null if none.
   * @throws DirectoryException If an error prevented the search from being
   * processed.
   */
  private void searchIndexed(ReadableStorage txn, EntryIDSet entryIDList, boolean candidatesAreInScope,
      SearchOperation searchOperation, PagedResultsControl pageRequest) throws DirectoryException,
      CanceledOperationException
  {
    SearchScope searchScope = searchOperation.getScope();
    DN aBaseDN = searchOperation.getBaseDN();
    boolean manageDsaIT = isManageDsaITOperation(searchOperation);
    boolean continueSearch = true;

    // Set the starting value.
    EntryID begin = null;
    if (pageRequest != null && pageRequest.getCookie().length() != 0)
    {
      // The cookie contains the ID of the next entry to be returned.
      try
      {
        begin = new EntryID(pageRequest.getCookie());
      }
      catch (Exception e)
      {
        logger.traceException(e);
        String str = pageRequest.getCookie().toHexString();
        LocalizableMessage msg = ERR_JEB_INVALID_PAGED_RESULTS_COOKIE.get(str);
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
            msg, e);
      }
    }
    else if (!manageDsaIT)
    {
      continueSearch = dn2uri.returnSearchReferences(txn, searchOperation);
    }

    // Make sure the candidate list is smaller than the lookthrough limit
    int lookthroughLimit =
      searchOperation.getClientConnection().getLookthroughLimit();
    if(lookthroughLimit > 0 && entryIDList.size() > lookthroughLimit)
    {
      //Lookthrough limit exceeded
      searchOperation.setResultCode(ResultCode.ADMIN_LIMIT_EXCEEDED);
      searchOperation.appendErrorMessage(
          NOTE_JEB_LOOKTHROUGH_LIMIT_EXCEEDED.get(lookthroughLimit));
      continueSearch = false;
    }

    // Iterate through the index candidates.
    if (continueSearch)
    {
      for (Iterator<EntryID> it = entryIDList.iterator(begin); it.hasNext();)
      {
        final EntryID id = it.next();

        Entry entry;
        try
        {
          entry = getEntry(txn, id);
        }
        catch (Exception e)
        {
          logger.traceException(e);
          continue;
        }

        // Process the candidate entry.
        if (entry != null && isInScope(candidatesAreInScope, searchScope, aBaseDN, entry)
              && (manageDsaIT || entry.getReferralURLs() == null) && searchOperation.getFilter().matchesEntry(entry))
          {
            if (pageRequest != null
                && searchOperation.getEntriesSent() == pageRequest.getSize())
            {
              // The current page is full.
              // Set the cookie to remember where we were.
              ByteString cookie = id.toByteString();
              Control control = new PagedResultsControl(pageRequest.isCritical(), 0, cookie);
              searchOperation.getResponseControls().add(control);
              return;
            }

            if (!searchOperation.returnEntry(entry, null))
            {
              // We have been told to discontinue processing of the
              // search. This could be due to size limit exceeded or
              // operation cancelled.
              break;
            }
          }
      }
      searchOperation.checkIfCanceled(false);
    }

    // Before we return success from the search we must ensure the base entry
    // exists. However, if we have returned at least one entry or subordinate
    // reference it implies the base does exist, so we can omit the check.
    if (searchOperation.getEntriesSent() == 0
        && searchOperation.getReferencesSent() == 0)
    {
      final Entry baseEntry = fetchBaseEntry(txn, aBaseDN, searchScope);
      if (!manageDsaIT)
      {
        dn2uri.checkTargetForReferral(baseEntry, searchScope);
      }
    }

    if (pageRequest != null)
    {
      // Indicate no more pages.
      Control control = new PagedResultsControl(pageRequest.isCritical(), 0, null);
      searchOperation.getResponseControls().add(control);
    }
  }

  private boolean isInScope(boolean candidatesAreInScope, SearchScope searchScope, DN aBaseDN, Entry entry)
  {
    DN entryDN = entry.getName();

    if (candidatesAreInScope)
    {
      return true;
    }
    else if (searchScope == SearchScope.SINGLE_LEVEL)
    {
      // Check if this entry is an immediate child.
      if (entryDN.size() == aBaseDN.size() + 1
          && entryDN.isDescendantOf(aBaseDN))
      {
        return true;
      }
    }
    else if (searchScope == SearchScope.WHOLE_SUBTREE)
    {
      if (entryDN.isDescendantOf(aBaseDN))
      {
        return true;
      }
    }
    else if (searchScope == SearchScope.SUBORDINATES
        && entryDN.size() > aBaseDN.size()
        && entryDN.isDescendantOf(aBaseDN))
    {
      return true;
    }
    return false;
  }

  /**
   * Adds the provided entry to this database.  This method must ensure that the
   * entry is appropriate for the database and that no entry already exists with
   * the same DN.  The caller must hold a write lock on the DN of the provided
   * entry.
   *
   * @param entry        The entry to add to this database.
   * @param addOperation The add operation with which the new entry is
   *                     associated.  This may be <CODE>null</CODE> for adds
   *                     performed internally.
   * @throws DirectoryException If a problem occurs while trying to add the
   *                            entry.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   * @throws CanceledOperationException if this operation should be cancelled.
   */
  void addEntry(final Entry entry, final AddOperation addOperation)
  throws StorageRuntimeException, DirectoryException, CanceledOperationException
  {
    try
    {
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableStorage txn) throws Exception
        {
          DN parentDN = getParentWithinBase(entry.getName());

          try
          {
            // Check whether the entry already exists.
            if (dn2id.get(txn, entry.getName(), false) != null)
            {
              throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS, ERR_JEB_ADD_ENTRY_ALREADY_EXISTS.get(entry
                  .getName()));
            }

            // Check that the parent entry exists.
            EntryID parentID = null;
            if (parentDN != null)
            {
              // Check for referral entries above the target.
              dn2uri.targetEntryReferrals(txn, entry.getName(), null);

              // Read the parent ID from dn2id.
              parentID = dn2id.get(txn, parentDN, false);
              if (parentID == null)
              {
                LocalizableMessage message = ERR_JEB_ADD_NO_SUCH_OBJECT.get(entry.getName());
                DN matchedDN = getMatchedDN(baseDN);
                throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, matchedDN, null);
              }
            }

            EntryID entryID = rootContainer.getNextEntryID();

            // Insert into dn2id.
            if (!dn2id.insert(txn, entry.getName(), entryID))
            {
              // Do not ever expect to come through here.
              throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS, ERR_JEB_ADD_ENTRY_ALREADY_EXISTS.get(entry
                  .getName()));
            }

            // Update the referral database for referral entries.
            if (!dn2uri.addEntry(txn, entry))
            {
              // Do not ever expect to come through here.
              throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS, ERR_JEB_ADD_ENTRY_ALREADY_EXISTS.get(entry
                  .getName()));
            }

            // Insert into id2entry.
            if (!id2entry.insert(txn, entryID, entry))
            {
              // Do not ever expect to come through here.
              throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS, ERR_JEB_ADD_ENTRY_ALREADY_EXISTS.get(entry
                  .getName()));
            }

            // Insert into the indexes, in index configuration order.
            final IndexBuffer indexBuffer = new IndexBuffer(EntryContainer.this);
            indexInsertEntry(indexBuffer, entry, entryID);

            // Insert into id2children and id2subtree.
            // The database transaction locks on these records will be hotly
            // contested so we do them last so as to hold the locks for the
            // shortest duration.
            if (parentDN != null)
            {
              final ByteString parentIDKeyBytes = parentID.toByteString();
              id2children.insertID(indexBuffer, parentIDKeyBytes, entryID);
              id2subtree.insertID(indexBuffer, parentIDKeyBytes, entryID);

              // Iterate up through the superior entries, starting above the
              // parent.
              for (DN dn = getParentWithinBase(parentDN); dn != null; dn = getParentWithinBase(dn))
              {
                // Read the ID from dn2id.
                EntryID nodeID = dn2id.get(txn, dn, false);
                if (nodeID == null)
                {
                  throw new StorageRuntimeException(ERR_JEB_MISSING_DN2ID_RECORD.get(dn).toString());
                }

                // Insert into id2subtree for this node.
                id2subtree.insertID(indexBuffer, nodeID.toByteString(), entryID);
              }
            }
            indexBuffer.flush(txn);

            if (addOperation != null)
            {
              // One last check before committing
              addOperation.checkIfCanceled(true);
            }

            // Update the entry cache.
            EntryCache<?> entryCache = DirectoryServer.getEntryCache();
            if (entryCache != null)
            {
              entryCache.putEntry(entry, backend, entryID.longValue());
            }
          }
          catch (StorageRuntimeException StorageRuntimeException)
          {
            throw StorageRuntimeException;
          }
          catch (DirectoryException directoryException)
          {
            throw directoryException;
          }
          catch (CanceledOperationException coe)
          {
            throw coe;
          }
          catch (Exception e)
          {
            String msg = e.getMessage();
            if (msg == null)
            {
              msg = stackTraceToSingleLineString(e);
            }
            LocalizableMessage message = ERR_JEB_UNCHECKED_EXCEPTION.get(msg);
            throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message, e);
          }
        }
      });
    }
    catch (Exception e)
    {
      throwAllowedExceptionTypes(e, DirectoryException.class, CanceledOperationException.class);
    }
  }

  /**
   * Removes the specified entry from this database.  This method must ensure
   * that the entry exists and that it does not have any subordinate entries
   * (unless the database supports a subtree delete operation and the client
   * included the appropriate information in the request).  The caller must hold
   * a write lock on the provided entry DN.
   *
   * @param entryDN         The DN of the entry to remove from this database.
   * @param deleteOperation The delete operation with which this action is
   *                        associated.  This may be <CODE>null</CODE> for
   *                        deletes performed internally.
   * @throws DirectoryException If a problem occurs while trying to remove the
   *                            entry.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   * @throws CanceledOperationException if this operation should be cancelled.
   */
  void deleteEntry(final DN entryDN, final DeleteOperation deleteOperation)
  throws DirectoryException, StorageRuntimeException, CanceledOperationException
  {
    try
    {
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableStorage txn) throws Exception
        {
          final IndexBuffer indexBuffer = new IndexBuffer(EntryContainer.this);

          try
          {
            // Check for referral entries above the target entry.
            dn2uri.targetEntryReferrals(txn, entryDN, null);

            // Determine whether this is a subtree delete.
            boolean isSubtreeDelete =
                deleteOperation != null && deleteOperation.getRequestControl(SubtreeDeleteControl.DECODER) != null;

            /*
             * We will iterate forwards through a range of the dn2id keys to
             * find subordinates of the target entry from the top of the tree
             * downwards.
             */
            ByteString entryDNKey = dnToDNKey(entryDN, baseDN.size());
            ByteStringBuilder suffix = copyOf(entryDNKey);
            ByteStringBuilder end = copyOf(entryDNKey);

            /*
             * Set the ending value to a value of equal length but slightly
             * greater than the suffix.
             */
            suffix.append((byte) 0x00);
            end.append((byte) 0x01);

            int subordinateEntriesDeleted = 0;

            Cursor cursor = dn2id.openCursor(txn);
            try
            {
              // Step forward until we pass the ending value.
              boolean success = cursor.positionToKeyOrNext(suffix);
              while (success && cursor.getKey().compareTo(end) < 0)
              {
                // We have found a subordinate entry.
                if (!isSubtreeDelete)
                {
                  // The subtree delete control was not specified and
                  // the target entry is not a leaf.
                  throw new DirectoryException(ResultCode.NOT_ALLOWED_ON_NONLEAF, ERR_JEB_DELETE_NOT_ALLOWED_ON_NONLEAF
                      .get(entryDN));
                }

                /*
                 * Delete this entry which by now must be a leaf because we have
                 * been deleting from the bottom of the tree upwards.
                 */
                EntryID entryID = new EntryID(cursor.getValue());

                // Invoke any subordinate delete plugins on the entry.
                if (deleteOperation != null && !deleteOperation.isSynchronizationOperation())
                {
                  Entry subordinateEntry = id2entry.get(txn, entryID, false);
                  SubordinateDelete pluginResult =
                      getPluginConfigManager().invokeSubordinateDeletePlugins(deleteOperation, subordinateEntry);

                  if (!pluginResult.continueProcessing())
                  {
                    LocalizableMessage message =
                        ERR_JEB_DELETE_ABORTED_BY_SUBORDINATE_PLUGIN.get(subordinateEntry.getName());
                    throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message);
                  }
                }

                deleteEntry(txn, indexBuffer, true, entryDN, cursor.getKey(), entryID);
                subordinateEntriesDeleted++;

                if (deleteOperation != null)
                {
                  deleteOperation.checkIfCanceled(false);
                }

                // Get the next DN.
                success = cursor.next();
              }
            }
            finally
            {
              cursor.close();
            }

            // draft-armijo-ldap-treedelete, 4.1 Tree Delete Semantics:
            // The server MUST NOT chase referrals stored in the tree. If
            // information about referrals is stored in this section of the
            // tree, this pointer will be deleted.
            boolean manageDsaIT = isSubtreeDelete || isManageDsaITOperation(deleteOperation);
            deleteEntry(txn, indexBuffer, manageDsaIT, entryDN, null, null);

            indexBuffer.flush(txn);

            if (deleteOperation != null)
            {
              // One last check before committing
              deleteOperation.checkIfCanceled(true);
            }

            if (isSubtreeDelete)
            {
              deleteOperation.addAdditionalLogItem(unquotedKeyValue(getClass(), "deletedEntries",
                  subordinateEntriesDeleted + 1));
            }
          }
          catch (StorageRuntimeException StorageRuntimeException)
          {
            throw StorageRuntimeException;
          }
          catch (DirectoryException directoryException)
          {
            throw directoryException;
          }
          catch (CanceledOperationException coe)
          {
            throw coe;
          }
          catch (Exception e)
          {
            String msg = e.getMessage();
            if (msg == null)
            {
              msg = stackTraceToSingleLineString(e);
            }
            LocalizableMessage message = ERR_JEB_UNCHECKED_EXCEPTION.get(msg);
            throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message, e);
          }
        }
      });
    }
    catch (Exception e)
    {
      throwAllowedExceptionTypes(e, DirectoryException.class, CanceledOperationException.class);
    }
  }

  private ByteStringBuilder copyOf(ByteString bs)
  {
    ByteStringBuilder newBS = new ByteStringBuilder(bs.length() + 1);
    newBS.append(bs);
    return newBS;
  }

  private void deleteEntry(WriteableStorage txn,
      IndexBuffer indexBuffer,
      boolean manageDsaIT,
      DN targetDN,
      ByteSequence leafDNKey,
      EntryID leafID)
  throws StorageRuntimeException, DirectoryException
  {
    if(leafID == null || leafDNKey == null)
    {
      // Read the entry ID from dn2id.
      if(leafDNKey == null)
      {
        leafDNKey = dnToDNKey(targetDN, baseDN.size());
      }
      ByteString value = dn2id.read(txn, leafDNKey, true);
      if (value == null)
      {
        LocalizableMessage message = ERR_JEB_DELETE_NO_SUCH_OBJECT.get(targetDN);
        DN matchedDN = getMatchedDN(baseDN);
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, matchedDN, null);
      }
      leafID = new EntryID(value);
    }

    // Remove from dn2id.
    if (!dn2id.delete(txn, leafDNKey))
    {
      // Do not expect to ever come through here.
      LocalizableMessage message = ERR_JEB_DELETE_NO_SUCH_OBJECT.get(leafDNKey);
      DN matchedDN = getMatchedDN(baseDN);
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, matchedDN, null);
    }

    // Check that the entry exists in id2entry and read its contents.
    Entry entry = id2entry.get(txn, leafID, true);
    if (entry == null)
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          ERR_JEB_MISSING_ID2ENTRY_RECORD.get(leafID));
    }

    if (!manageDsaIT)
    {
      dn2uri.checkTargetForReferral(entry, null);
    }

    // Update the referral database.
    dn2uri.deleteEntry(txn, entry);

    // Remove from id2entry.
    if (!id2entry.remove(txn, leafID))
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          ERR_JEB_MISSING_ID2ENTRY_RECORD.get(leafID));
    }

    // Remove from the indexes, in index config order.
    indexRemoveEntry(indexBuffer, entry, leafID);

    // Remove the id2c and id2s records for this entry.
    final ByteString leafIDKeyBytes = leafID.toByteString();
    id2children.delete(indexBuffer, leafIDKeyBytes);
    id2subtree.delete(indexBuffer, leafIDKeyBytes);

    // Iterate up through the superior entries from the target entry.
    boolean isParent = true;
    for (DN parentDN = getParentWithinBase(targetDN); parentDN != null;
    parentDN = getParentWithinBase(parentDN))
    {
      // Read the ID from dn2id.
      EntryID parentID = dn2id.get(txn, parentDN, false);
      if (parentID == null)
      {
        throw new StorageRuntimeException(ERR_JEB_MISSING_DN2ID_RECORD.get(parentDN).toString());
      }

      ByteString parentIDBytes = parentID.toByteString();
      // Remove from id2children.
      if (isParent)
      {
        id2children.removeID(indexBuffer, parentIDBytes, leafID);
        isParent = false;
      }
      id2subtree.removeID(indexBuffer, parentIDBytes, leafID);
    }

    // Remove the entry from the entry cache.
    EntryCache<?> entryCache = DirectoryServer.getEntryCache();
    if (entryCache != null)
    {
      entryCache.removeEntry(entry.getName());
    }
  }

  /**
   * Indicates whether an entry with the specified DN exists.
   *
   * @param  entryDN  The DN of the entry for which to determine existence.
   *
   * @return  <CODE>true</CODE> if the specified entry exists,
   *          or <CODE>false</CODE> if it does not.
   *
   * @throws  DirectoryException  If a problem occurs while trying to make the
   *                              determination.
   */
  private boolean entryExists(final DN entryDN) throws DirectoryException
  {
    // Try the entry cache first.
    EntryCache<?> entryCache = DirectoryServer.getEntryCache();
    if (entryCache != null && entryCache.containsEntry(entryDN))
    {
      return true;
    }

    try
    {
      return storage.read(new ReadOperation<Boolean>()
      {
        @Override
        public Boolean run(ReadableStorage txn) throws Exception
        {
          EntryID id = dn2id.get(null, entryDN, false);
        return id != null;
        }
      });
    }
    catch (Exception e)
    {
      logger.traceException(e);
      return false;
    }
  }

  /**
   * Fetch an entry by DN, trying the entry cache first, then the database.
   * Retrieves the requested entry, trying the entry cache first,
   * then the database.  Note that the caller must hold a read or write lock
   * on the specified DN.
   *
   * @param entryDN The distinguished name of the entry to retrieve.
   * @return The requested entry, or <CODE>null</CODE> if the entry does not
   *         exist.
   * @throws DirectoryException If a problem occurs while trying to retrieve
   *                            the entry.
   * @throws StorageRuntimeException An error occurred during a database operation.
   */
  Entry getEntry(final DN entryDN) throws StorageRuntimeException, DirectoryException
  {
    try
    {
      return storage.read(new ReadOperation<Entry>()
      {
        @Override
        public Entry run(ReadableStorage txn) throws Exception
        {
          return getEntry0(txn, entryDN);
        }
      });
    }
    catch (Exception e)
    {
      // it is not very clean to specify twice the same exception but it saves me some code for now
      throwAllowedExceptionTypes(e, DirectoryException.class, DirectoryException.class);
      return null; // it can never happen
    }
  }

  private Entry getEntry0(ReadableStorage txn, final DN entryDN) throws StorageRuntimeException, DirectoryException
  {
    final EntryCache<?> entryCache = DirectoryServer.getEntryCache();

    // Try the entry cache first.
    if (entryCache != null)
    {
      final Entry entry = entryCache.getEntry(entryDN);
      if (entry != null)
      {
        return entry;
      }
    }

    try
    {
      // Read dn2id.
      EntryID entryID = dn2id.get(txn, entryDN, false);
      if (entryID == null)
      {
        // The entryDN does not exist.
        // Check for referral entries above the target entry.
        dn2uri.targetEntryReferrals(txn, entryDN, null);
        return null;
      }

      // Read id2entry.
      final Entry entry = id2entry.get(txn, entryID, false);
      if (entry == null)
      {
        // The entryID does not exist.
        throw new DirectoryException(getServerErrorResultCode(), ERR_JEB_MISSING_ID2ENTRY_RECORD.get(entryID));
      }

      // Put the entry in the cache making sure not to overwrite
      // a newer copy that may have been inserted since the time
      // we read the cache.
      if (entryCache != null)
      {
        entryCache.putEntryIfAbsent(entry, backend, entryID.longValue());
      }
      return entry;
    }
    catch (Exception e)
    {
      // it is not very clean to specify twice the same exception but it saves me some code for now
      throwAllowedExceptionTypes(e, DirectoryException.class, DirectoryException.class);
      return null; // unreachable
    }
  }

  /**
   * The simplest case of replacing an entry in which the entry DN has
   * not changed.
   *
   * @param oldEntry           The old contents of the entry
   * @param newEntry           The new contents of the entry
   * @param modifyOperation The modify operation with which this action is
   *                        associated.  This may be <CODE>null</CODE> for
   *                        modifications performed internally.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws CanceledOperationException if this operation should be cancelled.
   */
  void replaceEntry(final Entry oldEntry, final Entry newEntry, final ModifyOperation modifyOperation)
      throws StorageRuntimeException, DirectoryException, CanceledOperationException
  {
    try
    {
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableStorage txn) throws Exception
        {
          try
          {
            // Read dn2id.
            EntryID entryID = dn2id.get(txn, newEntry.getName(), true);
            if (entryID == null)
            {
              // The entry does not exist.
              LocalizableMessage message =
                  ERR_JEB_MODIFY_NO_SUCH_OBJECT.get(newEntry.getName());
              DN matchedDN = getMatchedDN(baseDN);
              throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
                  message, matchedDN, null);
            }

            if (!isManageDsaITOperation(modifyOperation))
            {
              // Check if the entry is a referral entry.
              dn2uri.checkTargetForReferral(oldEntry, null);
            }

            // Update the referral database.
            if (modifyOperation != null)
            {
              // In this case we know from the operation what the modifications were.
              List<Modification> mods = modifyOperation.getModifications();
              dn2uri.modifyEntry(txn, oldEntry, newEntry, mods);
            }
            else
            {
              dn2uri.replaceEntry(txn, oldEntry, newEntry);
            }

            // Replace id2entry.
            id2entry.put(txn, entryID, newEntry);

            // Update the indexes.
            final IndexBuffer indexBuffer = new IndexBuffer(EntryContainer.this);
            if (modifyOperation != null)
            {
              // In this case we know from the operation what the modifications were.
              List<Modification> mods = modifyOperation.getModifications();
              indexModifications(indexBuffer, oldEntry, newEntry, entryID, mods);
            }
            else
            {
              // The most optimal would be to figure out what the modifications were.
              indexRemoveEntry(indexBuffer, oldEntry, entryID);
              indexInsertEntry(indexBuffer, newEntry, entryID);
            }

            indexBuffer.flush(txn);

            if(modifyOperation != null)
            {
              // One last check before committing
              modifyOperation.checkIfCanceled(true);
            }

            // Update the entry cache.
            EntryCache<?> entryCache = DirectoryServer.getEntryCache();
            if (entryCache != null)
            {
              entryCache.putEntry(newEntry, backend, entryID.longValue());
            }
          }
          catch (StorageRuntimeException StorageRuntimeException)
          {
            throw StorageRuntimeException;
          }
          catch (DirectoryException directoryException)
          {
            throw directoryException;
          }
          catch (CanceledOperationException coe)
          {
            throw coe;
          }
          catch (Exception e)
          {
            String msg = e.getMessage();
            if (msg == null)
            {
              msg = stackTraceToSingleLineString(e);
            }
            LocalizableMessage message = ERR_JEB_UNCHECKED_EXCEPTION.get(msg);
            throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                message, e);
          }
        }
      });
    }
    catch (Exception e)
    {
      throwAllowedExceptionTypes(e, DirectoryException.class, CanceledOperationException.class);
    }
  }

  /**
   * Moves and/or renames the provided entry in this backend, altering any
   * subordinate entries as necessary.  This must ensure that an entry already
   * exists with the provided current DN, and that no entry exists with the
   * target DN of the provided entry.  The caller must hold write locks on both
   * the current DN and the new DN for the entry.
   *
   * @param currentDN         The current DN of the entry to be replaced.
   * @param entry             The new content to use for the entry.
   * @param modifyDNOperation The modify DN operation with which this action
   *                          is associated.  This may be <CODE>null</CODE>
   *                          for modify DN operations performed internally.
   * @throws DirectoryException
   *          If a problem occurs while trying to perform the rename.
   * @throws CanceledOperationException
   *          If this backend noticed and reacted
   *          to a request to cancel or abandon the
   *          modify DN operation.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  void renameEntry(final DN currentDN, final Entry entry, final ModifyDNOperation modifyDNOperation)
      throws StorageRuntimeException, DirectoryException, CanceledOperationException
  {
    try
    {
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableStorage txn) throws Exception
        {
          DN oldSuperiorDN = getParentWithinBase(currentDN);
          DN newSuperiorDN = getParentWithinBase(entry.getName());

          final boolean isApexEntryMoved;
          if (oldSuperiorDN != null)
          {
            isApexEntryMoved = !oldSuperiorDN.equals(newSuperiorDN);
          }
          else if (newSuperiorDN != null)
          {
            isApexEntryMoved = !newSuperiorDN.equals(oldSuperiorDN);
          }
          else
          {
            isApexEntryMoved = false;
          }

          IndexBuffer buffer = new IndexBuffer(EntryContainer.this);

          try
          {
            // Check whether the renamed entry already exists.
            if (!currentDN.equals(entry.getName()) && dn2id.get(txn, entry.getName(), false) != null)
            {
              LocalizableMessage message = ERR_JEB_MODIFYDN_ALREADY_EXISTS.get(entry.getName());
              throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS, message);
            }

            EntryID oldApexID = dn2id.get(txn, currentDN, false);
            if (oldApexID == null)
            {
              // Check for referral entries above the target entry.
              dn2uri.targetEntryReferrals(txn, currentDN, null);

              LocalizableMessage message = ERR_JEB_MODIFYDN_NO_SUCH_OBJECT.get(currentDN);
              DN matchedDN = getMatchedDN(baseDN);
              throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, matchedDN, null);
            }

            Entry oldApexEntry = id2entry.get(txn, oldApexID, false);
            if (oldApexEntry == null)
            {
              throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), ERR_JEB_MISSING_ID2ENTRY_RECORD
                  .get(oldApexID));
            }

            if (!isManageDsaITOperation(modifyDNOperation))
            {
              dn2uri.checkTargetForReferral(oldApexEntry, null);
            }

            EntryID newApexID = oldApexID;
            if (newSuperiorDN != null && isApexEntryMoved)
            {
              /*
               * We want to preserve the invariant that the ID of an entry is
               * greater than its parent, since search results are returned in
               * ID order.
               */
              EntryID newSuperiorID = dn2id.get(txn, newSuperiorDN, false);
              if (newSuperiorID == null)
              {
                LocalizableMessage msg = ERR_JEB_NEW_SUPERIOR_NO_SUCH_OBJECT.get(newSuperiorDN);
                DN matchedDN = getMatchedDN(baseDN);
                throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, msg, matchedDN, null);
              }

              if (newSuperiorID.compareTo(oldApexID) > 0)
              {
                // This move would break the above invariant so we must
                // renumber every entry that moves. This is even more
                // expensive since every entry has to be deleted from
                // and added back into the attribute indexes.
                newApexID = rootContainer.getNextEntryID();

                if (logger.isTraceEnabled())
                {
                  logger.trace("Move of target entry requires renumbering" + "all entries in the subtree. "
                      + "Old DN: %s " + "New DN: %s " + "Old entry ID: %d " + "New entry ID: %d "
                      + "New Superior ID: %d" + oldApexEntry.getName(), entry.getName(), oldApexID,
                      newApexID, newSuperiorID);
                }
              }
            }

            MovedEntry head = new MovedEntry(null, null, false);
            MovedEntry current = head;
            // Move or rename the apex entry.
            removeApexEntry(txn, buffer, oldSuperiorDN, oldApexID, newApexID, oldApexEntry, entry, isApexEntryMoved,
                modifyDNOperation, current);
            current = current.next;

            /*
             * We will iterate forwards through a range of the dn2id keys to
             * find subordinates of the target entry from the top of the tree
             * downwards.
             */
            ByteString currentDNKey = dnToDNKey(currentDN, baseDN.size());
            ByteStringBuilder suffix = copyOf(currentDNKey);
            ByteStringBuilder end = copyOf(currentDNKey);

            /*
             * Set the ending value to a value of equal length but slightly
             * greater than the suffix.
             */
            suffix.append((byte) 0x00);
            end.append((byte) 0x01);

            Cursor cursor = txn.openCursor(dn2id.getName());
            try
            {

              // Step forward until we pass the ending value.
              boolean success = cursor.positionToKeyOrNext(suffix);
              while (success && cursor.getKey().compareTo(end) < 0)
              {
                // We have found a subordinate entry.
                EntryID oldID = new EntryID(cursor.getValue());
                Entry oldEntry = id2entry.get(txn, oldID, false);

                // Construct the new DN of the entry.
                DN newDN = modDN(oldEntry.getName(), currentDN.size(), entry.getName());

                // Assign a new entry ID if we are renumbering.
                EntryID newID = oldID;
                if (!newApexID.equals(oldApexID))
                {
                  newID = rootContainer.getNextEntryID();

                  if (logger.isTraceEnabled())
                  {
                    logger.trace("Move of subordinate entry requires renumbering. "
                        + "Old DN: %s New DN: %s Old entry ID: %d New entry ID: %d",
                        oldEntry.getName(), newDN, oldID, newID);
                  }
                }

                // Move this entry.
                removeSubordinateEntry(txn, buffer, oldSuperiorDN, oldID, newID, oldEntry, newDN, isApexEntryMoved,
                    modifyDNOperation, current);
                current = current.next;

                if (modifyDNOperation != null)
                {
                  modifyDNOperation.checkIfCanceled(false);
                }

                // Get the next DN.
                success = cursor.next();
              }
            }
            finally
            {
              cursor.close();
            }

            // Set current to the first moved entry and null out the head.
            // This will allow processed moved entries to be GCed.
            current = head.next;
            head = null;
            while (current != null)
            {
              addRenamedEntry(txn, buffer, current.entryID, current.entry, isApexEntryMoved, current.renumbered,
                  modifyDNOperation);
              current = current.next;
            }
            buffer.flush(txn);

            if (modifyDNOperation != null)
            {
              // One last check before committing
              modifyDNOperation.checkIfCanceled(true);
            }
          }
          catch (StorageRuntimeException StorageRuntimeException)
          {
            throw StorageRuntimeException;
          }
          catch (DirectoryException directoryException)
          {
            throw directoryException;
          }
          catch (CanceledOperationException coe)
          {
            throw coe;
          }
          catch (Exception e)
          {
            String msg = e.getMessage();
            if (msg == null)
            {
              msg = stackTraceToSingleLineString(e);
            }
            LocalizableMessage message = ERR_JEB_UNCHECKED_EXCEPTION.get(msg);
            throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message, e);
          }
        }
      });
    }
    catch (Exception e)
    {
      throwAllowedExceptionTypes(e, DirectoryException.class, CanceledOperationException.class);
    }
  }

  /** Represents an renamed entry that was deleted from JE but yet to be added back. */
  private static final class MovedEntry
  {
    private EntryID entryID;
    private Entry entry;
    private MovedEntry next;
    private boolean renumbered;

    private MovedEntry(EntryID entryID, Entry entry, boolean renumbered)
    {
      this.entryID = entryID;
      this.entry = entry;
      this.renumbered = renumbered;
    }
  }

  private void addRenamedEntry(WriteableStorage txn, IndexBuffer buffer,
                           EntryID newID,
                           Entry newEntry,
                           boolean isApexEntryMoved,
                           boolean renumbered,
                           ModifyDNOperation modifyDNOperation)
      throws DirectoryException, StorageRuntimeException
  {
    if (!dn2id.insert(txn, newEntry.getName(), newID))
    {
      LocalizableMessage message = ERR_JEB_MODIFYDN_ALREADY_EXISTS.get(newEntry.getName());
      throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS, message);
    }
    id2entry.put(txn, newID, newEntry);
    dn2uri.addEntry(txn, newEntry);

    if (renumbered || modifyDNOperation == null)
    {
      // Reindex the entry with the new ID.
      indexInsertEntry(buffer, newEntry, newID);
    }

    // Add the new ID to id2children and id2subtree of new apex parent entry.
    if(isApexEntryMoved)
    {
      boolean isParent = true;
      for (DN dn = getParentWithinBase(newEntry.getName()); dn != null;
           dn = getParentWithinBase(dn))
      {
        EntryID parentID = dn2id.get(txn, dn, false);
        ByteString parentIDKeyBytes = parentID.toByteString();
        if(isParent)
        {
          id2children.insertID(buffer, parentIDKeyBytes, newID);
          isParent = false;
        }
        id2subtree.insertID(buffer, parentIDKeyBytes, newID);
      }
    }
  }

  private void removeApexEntry(WriteableStorage txn, IndexBuffer buffer,
      DN oldSuperiorDN,
      EntryID oldID, EntryID newID,
      Entry oldEntry, Entry newEntry,
      boolean isApexEntryMoved,
      ModifyDNOperation modifyDNOperation,
      MovedEntry tail)
  throws DirectoryException, StorageRuntimeException
  {
    DN oldDN = oldEntry.getName();

    // Remove the old DN from dn2id.
    dn2id.remove(txn, oldDN);

    // Remove old ID from id2entry and put the new entry
    // (old entry with new DN) in id2entry.
    if (!newID.equals(oldID))
    {
      id2entry.remove(txn, oldID);
    }

    // Update any referral records.
    dn2uri.deleteEntry(txn, oldEntry);

    tail.next = new MovedEntry(newID, newEntry, !newID.equals(oldID));

    // Remove the old ID from id2children and id2subtree of
    // the old apex parent entry.
    if(oldSuperiorDN != null && isApexEntryMoved)
    {
      boolean isParent = true;
      for (DN dn = oldSuperiorDN; dn != null; dn = getParentWithinBase(dn))
      {
        EntryID parentID = dn2id.get(txn, dn, false);
        ByteString parentIDKeyBytes = parentID.toByteString();
        if(isParent)
        {
          id2children.removeID(buffer, parentIDKeyBytes, oldID);
          isParent = false;
        }
        id2subtree.removeID(buffer, parentIDKeyBytes, oldID);
      }
    }

    if (!newID.equals(oldID) || modifyDNOperation == null)
    {
      // All the subordinates will be renumbered so we have to rebuild
      // id2c and id2s with the new ID.
      ByteString oldIDKeyBytes = oldID.toByteString();
      id2children.delete(buffer, oldIDKeyBytes);
      id2subtree.delete(buffer, oldIDKeyBytes);

      // Reindex the entry with the new ID.
      indexRemoveEntry(buffer, oldEntry, oldID);
    }
    else
    {
      // Update the indexes if needed.
      indexModifications(buffer, oldEntry, newEntry, oldID,
          modifyDNOperation.getModifications());
    }

    // Remove the entry from the entry cache.
    EntryCache<?> entryCache = DirectoryServer.getEntryCache();
    if (entryCache != null)
    {
      entryCache.removeEntry(oldDN);
    }
  }

  private void removeSubordinateEntry(WriteableStorage txn, IndexBuffer buffer,
      DN oldSuperiorDN,
      EntryID oldID, EntryID newID,
      Entry oldEntry, DN newDN,
      boolean isApexEntryMoved,
      ModifyDNOperation modifyDNOperation,
      MovedEntry tail)
  throws DirectoryException, StorageRuntimeException
  {
    DN oldDN = oldEntry.getName();
    Entry newEntry = oldEntry.duplicate(false);
    newEntry.setDN(newDN);
    List<Modification> modifications =
      Collections.unmodifiableList(new ArrayList<Modification>(0));

    // Create a new entry that is a copy of the old entry but with the new DN.
    // Also invoke any subordinate modify DN plugins on the entry.
    // FIXME -- At the present time, we don't support subordinate modify DN
    //          plugins that make changes to subordinate entries and therefore
    //          provide an unmodifiable list for the modifications element.
    // FIXME -- This will need to be updated appropriately if we decided that
    //          these plugins should be invoked for synchronization
    //          operations.
    if (modifyDNOperation != null && !modifyDNOperation.isSynchronizationOperation())
    {
      SubordinateModifyDN pluginResult =
        getPluginConfigManager().invokeSubordinateModifyDNPlugins(
            modifyDNOperation, oldEntry, newEntry, modifications);

      if (!pluginResult.continueProcessing())
      {
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
            ERR_JEB_MODIFYDN_ABORTED_BY_SUBORDINATE_PLUGIN.get(oldDN, newDN));
      }

      if (! modifications.isEmpty())
      {
        LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
        if (! newEntry.conformsToSchema(null, false, false, false,
            invalidReason))
        {
          LocalizableMessage message =
            ERR_JEB_MODIFYDN_ABORTED_BY_SUBORDINATE_SCHEMA_ERROR.get(oldDN, newDN, invalidReason);
          throw new DirectoryException(
              DirectoryServer.getServerErrorResultCode(), message);
        }
      }
    }

    // Remove the old DN from dn2id.
    dn2id.remove(txn, oldDN);

    // Remove old ID from id2entry and put the new entry
    // (old entry with new DN) in id2entry.
    if (!newID.equals(oldID))
    {
      id2entry.remove(txn, oldID);
    }

    // Update any referral records.
    dn2uri.deleteEntry(txn, oldEntry);

    tail.next = new MovedEntry(newID, newEntry, !newID.equals(oldID));

    if(isApexEntryMoved)
    {
      // Remove the old ID from id2subtree of old apex superior entries.
      for (DN dn = oldSuperiorDN; dn != null; dn = getParentWithinBase(dn))
      {
        EntryID parentID = dn2id.get(txn, dn, false);
        ByteString parentIDKeyBytes = parentID.toByteString();
        id2subtree.removeID(buffer, parentIDKeyBytes, oldID);
      }
    }

    if (!newID.equals(oldID))
    {
      // All the subordinates will be renumbered so we have to rebuild
      // id2c and id2s with the new ID.
      ByteString oldIDKeyBytes = oldID.toByteString();
      id2children.delete(buffer, oldIDKeyBytes);
      id2subtree.delete(buffer, oldIDKeyBytes);

      // Reindex the entry with the new ID.
      indexRemoveEntry(buffer, oldEntry, oldID);
    }
    else if (!modifications.isEmpty())
    {
      // Update the indexes.
      indexModifications(buffer, oldEntry, newEntry, oldID, modifications);
    }

    // Remove the entry from the entry cache.
    EntryCache<?> entryCache = DirectoryServer.getEntryCache();
    if (entryCache != null)
    {
      entryCache.removeEntry(oldDN);
    }
  }

  /**
   * Make a new DN for a subordinate entry of a renamed or moved entry.
   *
   * @param oldDN The current DN of the subordinate entry.
   * @param oldSuffixLen The current DN length of the renamed or moved entry.
   * @param newSuffixDN The new DN of the renamed or moved entry.
   * @return The new DN of the subordinate entry.
   */
  static DN modDN(DN oldDN, int oldSuffixLen, DN newSuffixDN)
  {
    int oldDNNumComponents    = oldDN.size();
    int oldDNKeepComponents   = oldDNNumComponents - oldSuffixLen;
    int newSuffixDNComponents = newSuffixDN.size();

    RDN[] newDNComponents = new RDN[oldDNKeepComponents+newSuffixDNComponents];
    for (int i=0; i < oldDNKeepComponents; i++)
    {
      newDNComponents[i] = oldDN.getRDN(i);
    }

    for (int i=oldDNKeepComponents, j=0; j < newSuffixDNComponents; i++,j++)
    {
      newDNComponents[i] = newSuffixDN.getRDN(j);
    }

    return new DN(newDNComponents);
  }

  /**
   * Insert a new entry into the attribute indexes.
   *
   * @param buffer The index buffer used to buffer up the index changes.
   * @param entry The entry to be inserted into the indexes.
   * @param entryID The ID of the entry to be inserted into the indexes.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  private void indexInsertEntry(IndexBuffer buffer, Entry entry, EntryID entryID)
      throws StorageRuntimeException, DirectoryException
  {
    for (AttributeIndex index : attrIndexMap.values())
    {
      index.addEntry(buffer, entryID, entry);
    }

    for (VLVIndex vlvIndex : vlvIndexMap.values())
    {
      vlvIndex.addEntry(buffer, entryID, entry);
    }
  }

  /**
   * Remove an entry from the attribute indexes.
   *
   * @param buffer The index buffer used to buffer up the index changes.
   * @param entry The entry to be removed from the indexes.
   * @param entryID The ID of the entry to be removed from the indexes.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  private void indexRemoveEntry(IndexBuffer buffer, Entry entry, EntryID entryID)
      throws StorageRuntimeException, DirectoryException
  {
    for (AttributeIndex index : attrIndexMap.values())
    {
      index.removeEntry(buffer, entryID, entry);
    }

    for (VLVIndex vlvIndex : vlvIndexMap.values())
    {
      vlvIndex.removeEntry(buffer, entryID, entry);
    }
  }

  /**
   * Update the attribute indexes to reflect the changes to the
   * attributes of an entry resulting from a sequence of modifications.
   *
   * @param buffer The index buffer used to buffer up the index changes.
   * @param oldEntry The contents of the entry before the change.
   * @param newEntry The contents of the entry after the change.
   * @param entryID The ID of the entry that was changed.
   * @param mods The sequence of modifications made to the entry.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  private void indexModifications(IndexBuffer buffer, Entry oldEntry, Entry newEntry,
      EntryID entryID, List<Modification> mods)
  throws StorageRuntimeException, DirectoryException
  {
    // Process in index configuration order.
    for (AttributeIndex index : attrIndexMap.values())
    {
      // Check whether any modifications apply to this indexed attribute.
      if (isAttributeModified(index, mods))
      {
        index.modifyEntry(buffer, entryID, oldEntry, newEntry, mods);
      }
    }

    for(VLVIndex vlvIndex : vlvIndexMap.values())
    {
      vlvIndex.modifyEntry(buffer, entryID, oldEntry, newEntry, mods);
    }
  }

  /**
   * Get a count of the number of entries stored in this entry container.
   *
   * @param txn The database transaction
   * @return The number of entries stored in this entry container.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  long getEntryCount(ReadableStorage txn) throws StorageRuntimeException
  {
    final EntryID entryID = dn2id.get(txn, baseDN, false);
    if (entryID != null)
    {
      final EntryIDSet entryIDSet = id2subtree.readKey(entryID.toByteString(), txn);
      long count = entryIDSet.size();
      if(count != Long.MAX_VALUE)
      {
        // Add the base entry itself
        return ++count;
      }
      else
      {
        // The count is not maintained. Fall back to the slow method
        return id2entry.getRecordCount(txn);
      }
    }
    else
    {
      // Base entry doesn't not exist so this entry container
      // must not have any entries
      return 0;
    }
  }


  /**
   * Get a list of the databases opened by the entryContainer.
   * @param dbList A list of database containers.
   */
  void listDatabases(List<DatabaseContainer> dbList)
  {
    dbList.add(dn2id);
    dbList.add(id2entry);
    dbList.add(dn2uri);
    if (config.isSubordinateIndexesEnabled())
    {
      dbList.add(id2children);
      dbList.add(id2subtree);
    }
    dbList.add(state);

    for(AttributeIndex index : attrIndexMap.values())
    {
      index.listDatabases(dbList);
    }

    dbList.addAll(vlvIndexMap.values());
  }

  /**
   * Determine whether the provided operation has the ManageDsaIT request
   * control.
   * @param operation The operation for which the determination is to be made.
   * @return true if the operation has the ManageDsaIT request control, or false
   * if not.
   */
  private static boolean isManageDsaITOperation(Operation operation)
  {
    if(operation != null)
    {
      List<Control> controls = operation.getRequestControls();
      if (controls != null)
      {
        for (Control control : controls)
        {
          if (ServerConstants.OID_MANAGE_DSAIT_CONTROL.equals(control.getOID()))
          {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Delete this entry container from disk. The entry container should be
   * closed before calling this method.
   *
   * @param txn The database transaction
   * @throws StorageRuntimeException If an error occurs while removing the entry
   *                           container.
   */
  void delete(WriteableStorage txn) throws StorageRuntimeException
  {
    List<DatabaseContainer> databases = new ArrayList<DatabaseContainer>();
    listDatabases(databases);

    for (DatabaseContainer db : databases)
    {
      txn.deleteTree(db.getName());
    }
  }

  /**
   * Remove a database from disk.
   *
   * @param txn The database transaction
   * @param database The database container to remove.
   * @throws StorageRuntimeException If an error occurs while attempting to delete the
   * database.
   */
  void deleteDatabase(WriteableStorage txn, DatabaseContainer database) throws StorageRuntimeException
  {
    if(database == state)
    {
      // The state database can not be removed individually.
      return;
    }

    database.close();
    txn.deleteTree(database.getName());
    if(database instanceof Index)
    {
      state.removeIndexTrustState(txn, database);
    }
  }

  /**
   * Removes a attribute index from disk.
   *
   * @param attributeIndex The attribute index to remove.
   * @throws StorageRuntimeException If an JE database error occurs while attempting
   * to delete the index.
   */
  private void deleteAttributeIndex(WriteableStorage txn, AttributeIndex attributeIndex) throws StorageRuntimeException
  {
    attributeIndex.close();
    for (Index index : attributeIndex.getAllIndexes())
    {
      txn.deleteTree(index.getName());
      state.removeIndexTrustState(txn, index);
    }
  }

  /**
   * This method constructs a container name from a base DN. Only alphanumeric
   * characters are preserved, all other characters are replaced with an
   * underscore.
   *
   * @return The container name for the base DN.
   */
  String getDatabasePrefix()
  {
    return databasePrefix;
  }

  /**
   * Sets a new database prefix for this entry container and rename all
   * existing databases in use by this entry container.
   *
   * @param newBaseDN The new database prefix to use.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  void setDatabasePrefix(final String newBaseDN) throws StorageRuntimeException
  {
    final List<DatabaseContainer> databases = new ArrayList<DatabaseContainer>();
    listDatabases(databases);

    // close the containers.
    for(DatabaseContainer db : databases)
    {
      db.close();
    }

    try
    {
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableStorage txn) throws Exception
        {
          for(DatabaseContainer db : databases)
          {
            TreeName oldName = db.getName();
            TreeName newName = oldName.replaceBaseDN(newBaseDN);
            txn.renameTree(oldName, newName);
          }
        }
      });
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableStorage txn) throws Exception
        {
          for (DatabaseContainer db : databases)
          {
            TreeName oldName = db.getName();
            TreeName newName = oldName.replaceBaseDN(newBaseDN);
            db.setName(newName);
          }
        }
      });
    }
    catch (Exception e)
    {
      String msg = e.getMessage();
      if (msg == null)
      {
        msg = stackTraceToSingleLineString(e);
      }
      LocalizableMessage message = ERR_JEB_UNCHECKED_EXCEPTION.get(msg);
      throw new StorageRuntimeException(message.toString(), e);
    }
    finally
    {
      try
      {
        storage.write(new WriteOperation()
        {
          @Override
          public void run(WriteableStorage txn) throws Exception
          {
            // Open the containers backup.
            for(DatabaseContainer db : databases)
            {
              db.open(txn);
            }
          }
        });
      }
      catch (Exception e)
      {
        String msg = e.getMessage();
        if (msg == null)
        {
          msg = stackTraceToSingleLineString(e);
        }
        LocalizableMessage message = ERR_JEB_UNCHECKED_EXCEPTION.get(msg);
        throw new StorageRuntimeException(message.toString(), e);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public DN getBaseDN()
  {
    return baseDN;
  }

  /**
   * Get the parent of a DN in the scope of the base DN.
   *
   * @param dn A DN which is in the scope of the base DN.
   * @return The parent DN, or null if the given DN is the base DN.
   */
  DN getParentWithinBase(DN dn)
  {
    if (dn.equals(baseDN))
    {
      return null;
    }
    return dn.parent();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
      PluggableBackendCfg cfg, List<LocalizableMessage> unacceptableReasons)
  {
    // This is always true because only all config attributes used
    // by the entry container should be validated by the admin framework.
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(final PluggableBackendCfg cfg)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    exclusiveLock.lock();
    try
    {
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableStorage txn) throws Exception
        {
          if (config.isSubordinateIndexesEnabled() != cfg.isSubordinateIndexesEnabled())
          {
            if (cfg.isSubordinateIndexesEnabled())
            {
              // Re-enabling subordinate indexes.
              openSubordinateIndexes(txn);
            }
            else
            {
              // Disabling subordinate indexes. Use a null index and ensure that
              // future attempts to use the real indexes will fail.
              id2children.close();
              id2children = new NullIndex(getIndexName(ID2CHILDREN_DATABASE_NAME),
                  new ID2CIndexer(), state, storage, txn, EntryContainer.this);
              state.putIndexTrustState(txn, id2children, false);
              id2children.open(txn); // No-op

              id2subtree.close();
              id2subtree = new NullIndex(getIndexName(ID2SUBTREE_DATABASE_NAME),
                  new ID2SIndexer(), state, storage, txn, EntryContainer.this);
              state.putIndexTrustState(txn, id2subtree, false);
              id2subtree.open(txn); // No-op

              logger.info(NOTE_JEB_SUBORDINATE_INDEXES_DISABLED, cfg.getBackendId());
            }
          }

          if (config.getIndexEntryLimit() != cfg.getIndexEntryLimit())
          {
            if (id2children.setIndexEntryLimit(cfg.getIndexEntryLimit()))
            {
              ccr.setAdminActionRequired(true);
              ccr.addMessage(NOTE_JEB_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD.get(id2children.getName()));
            }

            if (id2subtree.setIndexEntryLimit(cfg.getIndexEntryLimit()))
            {
              ccr.setAdminActionRequired(true);
              ccr.addMessage(NOTE_JEB_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD.get(id2subtree.getName()));
            }
          }

          DataConfig entryDataConfig = new DataConfig(cfg.isEntriesCompressed(),
              cfg.isCompactEncoding(), rootContainer.getCompressedSchema());
          id2entry.setDataConfig(entryDataConfig);

          EntryContainer.this.config = cfg;
        }
      });
    }
    catch (Exception e)
    {
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(LocalizableMessage.raw(stackTraceToSingleLineString(e)));
    }
    finally
    {
      exclusiveLock.unlock();
    }

    return ccr;
  }

  /**
   * Clear the contents of this entry container.
   *
   * @throws StorageRuntimeException If an error occurs while removing the entry
   *                           container.
   */
  public void clear() throws StorageRuntimeException
  {
    try
    {
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableStorage txn) throws Exception
        {
          clear0(txn);
        }
      });
    }
    catch (Exception e)
    {
      throw new StorageRuntimeException(e);
    }
  }

  private void clear0(WriteableStorage txn) throws StorageRuntimeException
  {
    final List<DatabaseContainer> databases = new ArrayList<DatabaseContainer>();
    listDatabases(databases);

    for(DatabaseContainer db : databases)
    {
      db.close();
    }
    try
    {
      for (DatabaseContainer db : databases)
      {
        txn.truncateTree(db.getName());
      }
    }
    finally
    {
      for(DatabaseContainer db : databases)
      {
        db.open(txn);
      }

      for (DatabaseContainer db : databases)
      {
        if (db instanceof Index)
        {
          ((Index) db).setTrusted(txn, true);
        }
      }
    }
  }

  /**
   * Clear the contents for a database from disk.
   *
   * @param txn The database transaction
   * @param database The database to clear.
   * @throws StorageRuntimeException if a JE database error occurs.
   */
  void clearDatabase(WriteableStorage txn, DatabaseContainer database)
      throws StorageRuntimeException
  {
    database.close();
    try
    {
      txn.deleteTree(database.getName());
    }
    finally
    {
      database.open(txn);
    }
    if(logger.isTraceEnabled())
    {
      logger.trace("Cleared the database %s", database.getName());
    }
  }


  /**
   * Finds an existing entry whose DN is the closest ancestor of a given baseDN.
   *
   * @param baseDN  the DN for which we are searching a matched DN.
   * @return the DN of the closest ancestor of the baseDN.
   * @throws DirectoryException If an error prevented the check of an
   * existing entry from being performed.
   */
  private DN getMatchedDN(DN baseDN) throws DirectoryException
  {
    DN parentDN  = baseDN.getParentDNInSuffix();
    while (parentDN != null && parentDN.isDescendantOf(getBaseDN()))
    {
      if (entryExists(parentDN))
      {
        return parentDN;
      }
      parentDN = parentDN.getParentDNInSuffix();
    }
    return null;
  }

  /** Opens the id2children and id2subtree indexes. */
  private void openSubordinateIndexes(WriteableStorage txn)
  {
    id2children = newIndex(txn, ID2CHILDREN_DATABASE_NAME, new ID2CIndexer());
    id2subtree = newIndex(txn, ID2SUBTREE_DATABASE_NAME, new ID2SIndexer());
  }

  private Index newIndex(WriteableStorage txn, String name, Indexer indexer)
  {
    final Index index = new Index(getIndexName(name),
        indexer, state, config.getIndexEntryLimit(), 0, true, storage, txn, this);
    index.open(txn);
    if (!index.isTrusted())
    {
      logger.info(NOTE_JEB_INDEX_ADD_REQUIRES_REBUILD, index.getName());
    }
    return index;
  }

  /**
   * Creates a new index for an attribute.
   *
   * @param txn The database transaction
   * @param indexName the name to give to the new index
   * @param indexer the indexer to use when inserting data into the index
   * @param indexEntryLimit the index entry limit
   * @return a new index
   */
  Index newIndexForAttribute(WriteableStorage txn, TreeName indexName, Indexer indexer, int indexEntryLimit)
  {
    final int cursorEntryLimit = 100000;
    return new Index(indexName, indexer, state, indexEntryLimit, cursorEntryLimit, false, storage, txn, this);
  }


  /**
   * Checks if any modifications apply to this indexed attribute.
   * @param index the indexed attributes.
   * @param mods the modifications to check for.
   * @return true if any apply, false otherwise.
   */
  private boolean isAttributeModified(AttributeIndex index,
                                      List<Modification> mods)
  {
    boolean attributeModified = false;
    AttributeType indexAttributeType = index.getAttributeType();
    Iterable<AttributeType> subTypes =
            DirectoryServer.getSchema().getSubTypes(indexAttributeType);

    for (Modification mod : mods)
    {
      Attribute modAttr = mod.getAttribute();
      AttributeType modAttrType = modAttr.getAttributeType();
      if (modAttrType.equals(indexAttributeType))
      {
        attributeModified = true;
        break;
      }
      for(AttributeType subType : subTypes)
      {
        if(modAttrType.equals(subType))
        {
          attributeModified = true;
          break;
        }
      }
    }
    return attributeModified;
  }


  /**
   * Fetch the base Entry of the EntryContainer.
   * @param baseDN the DN for the base entry
   * @param searchScope the scope under which this is fetched.
   *                    Scope is used for referral processing.
   * @return the Entry matching the baseDN.
   * @throws DirectoryException if the baseDN doesn't exist.
   */
  private Entry fetchBaseEntry(ReadableStorage txn, DN baseDN, SearchScope searchScope)
      throws DirectoryException
  {
    Entry baseEntry = null;
    try
    {
      baseEntry = getEntry0(txn, baseDN);
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }

    // The base entry must exist for a successful result.
    if (baseEntry == null)
    {
      // Check for referral entries above the base entry.
      dn2uri.targetEntryReferrals(txn, baseDN, searchScope);

      LocalizableMessage message = ERR_JEB_SEARCH_NO_SUCH_OBJECT.get(baseDN);
      DN matchedDN = getMatchedDN(baseDN);
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
            message, matchedDN, null);
    }

    return baseEntry;
  }

  /** Get the exclusive lock. */
  void lock()
  {
    exclusiveLock.lock();
  }

  /** Unlock the exclusive lock. */
  void unlock()
  {
    exclusiveLock.unlock();
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return databasePrefix;
  }
}
