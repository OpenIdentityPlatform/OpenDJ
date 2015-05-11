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

import static org.forgerock.util.Utils.*;
import static org.opends.messages.BackendMessages.*;
import static org.opends.server.backends.pluggable.DnKeyFormat.*;
import static org.opends.server.backends.pluggable.EntryIDSet.*;
import static org.opends.server.backends.pluggable.IndexFilter.*;
import static org.opends.server.backends.pluggable.VLVIndex.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.types.AdditionalLogItem.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;
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
import org.opends.messages.CoreMessages;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.server.BackendIndexCfg;
import org.opends.server.admin.std.server.BackendVLVIndexCfg;
import org.opends.server.admin.std.server.PluggableBackendCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.EntryCache;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.api.plugin.PluginResult.SubordinateDelete;
import org.opends.server.api.plugin.PluginResult.SubordinateModifyDN;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.SequentialCursor;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.controls.PagedResultsControl;
import org.opends.server.controls.ServerSideSortRequestControl;
import org.opends.server.controls.ServerSideSortResponseControl;
import org.opends.server.controls.SubtreeDeleteControl;
import org.opends.server.controls.VLVRequestControl;
import org.opends.server.controls.VLVResponseControl;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.protocols.ldap.LDAPResultCode;
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
import org.opends.server.types.SortOrder;
import org.opends.server.types.VirtualAttributeRule;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;

/**
 * Storage container for LDAP entries.  Each base DN of a backend is given
 * its own entry container.  The entry container is the object that implements
 * the guts of the backend API methods for LDAP operations.
 */
public class EntryContainer
    implements SuffixContainer, ConfigurationChangeListener<PluggableBackendCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Number of EntryID to considers when building EntryIDSet from DN2ID. */
  private static final int SCOPE_IDSET_LIMIT = 4096;
  /** The name of the entry tree. */
  private static final String ID2ENTRY_TREE_NAME = ID2ENTRY_INDEX_NAME;
  /** The name of the DN tree. */
  private static final String DN2ID_TREE_NAME = DN2ID_INDEX_NAME;
  /** The name of the children index tree. */
  private static final String ID2CHILDREN_COUNT_TREE_NAME = ID2CHILDREN_COUNT_NAME;
  /** The name of the referral tree. */
  private static final String REFERRAL_TREE_NAME = REFERRAL_INDEX_NAME;
  /** The name of the state tree. */
  private static final String STATE_TREE_NAME = STATE_INDEX_NAME;

  /** The attribute index configuration manager. */
  private final AttributeIndexCfgManager attributeIndexCfgManager;
  /** The vlv index configuration manager. */
  private final VLVIndexCfgManager vlvIndexCfgManager;

  /** The backend to which this entry container belongs. */
  private final Backend<?> backend;

  /** The root container in which this entryContainer belongs. */
  private final RootContainer rootContainer;

  /** The baseDN this entry container is responsible for. */
  private final DN baseDN;

  /** The backend configuration. */
  private PluggableBackendCfg config;

  /** The tree storage. */
  private final Storage storage;

  /** The DN tree maps a normalized DN string to an entry ID (8 bytes). */
  private final DN2ID dn2id;
  /** The entry tree maps an entry ID (8 bytes) to a complete encoded entry. */
  private ID2Entry id2entry;
  /** Store the number of children for each entry. */
  private final ID2Count id2childrenCount;
  /** The referral tree maps a normalized DN string to labeled URIs. */
  private final DN2URI dn2uri;
  /** The state tree maps a config DN to config entries. */
  private final State state;

  /** The set of attribute indexes. */
  private final HashMap<AttributeType, AttributeIndex> attrIndexMap = new HashMap<AttributeType, AttributeIndex>();

  /** The set of VLV (Virtual List View) indexes. */
  private final HashMap<String, VLVIndex> vlvIndexMap = new HashMap<String, VLVIndex>();

  /**
   * Prevents name clashes for common indexes (like id2entry) across multiple suffixes.
   * For example when a root container contains multiple suffixes.
   */
  private final String treePrefix;

  /**
   * This class is responsible for managing the configuration for attribute
   * indexes used within this entry container.
   */
  private class AttributeIndexCfgManager implements
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
          public void run(WriteableTransaction txn) throws Exception
          {
            //Try creating all the indexes before confirming they are valid ones.
            new AttributeIndex(cfg, state, EntryContainer.this, txn);
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
          public void run(WriteableTransaction txn) throws Exception
          {
            final AttributeIndex index = new AttributeIndex(cfg, state, EntryContainer.this, txn);
            index.open(txn);
            if (!index.isTrusted())
            {
              ccr.setAdminActionRequired(true);
              ccr.addMessage(NOTE_INDEX_ADD_REQUIRES_REBUILD.get(cfg.getAttribute().getNameOrOID()));
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
          public void run(WriteableTransaction txn) throws Exception
          {
            attrIndexMap.remove(cfg.getAttribute()).closeAndDelete(txn);
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
  private class VLVIndexCfgManager implements
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
        unacceptableReasons.add(ERR_CONFIG_VLV_INDEX_BAD_FILTER.get(
            cfg.getFilter(), cfg.getName(), e.getLocalizedMessage()));
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
          unacceptableReasons.add(ERR_CONFIG_VLV_INDEX_UNDEFINED_ATTR.get(sortKeys[i], cfg.getName()));
          return false;
        }

        AttributeType attrType =
          DirectoryServer.getAttributeType(sortAttrs[i].toLowerCase());
        if(attrType == null)
        {
          unacceptableReasons.add(ERR_CONFIG_VLV_INDEX_UNDEFINED_ATTR.get(sortAttrs[i], cfg.getName()));
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
          public void run(WriteableTransaction txn) throws Exception
          {
            VLVIndex vlvIndex = new VLVIndex(cfg, state, storage, EntryContainer.this, txn);
            vlvIndex.open(txn);
            if(!vlvIndex.isTrusted())
            {
              ccr.setAdminActionRequired(true);
              ccr.addMessage(NOTE_INDEX_ADD_REQUIRES_REBUILD.get(cfg.getName()));
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
          public void run(WriteableTransaction txn) throws Exception
          {
            vlvIndexMap.remove(cfg.getName().toLowerCase()).closeAndDelete(txn);
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
   * @param backend A reference to the backend that is creating this entry
   *                container. It is needed by the Directory Server entry cache
   *                methods.
   * @param config The configuration of the backend.
   * @param storage The storage for this entryContainer.
   * @param rootContainer The root container this entry container is in.
   * @throws ConfigException if a configuration related error occurs.
   */
  EntryContainer(DN baseDN, Backend<?> backend, PluggableBackendCfg config, Storage storage,
      RootContainer rootContainer) throws ConfigException
  {
    this.backend = backend;
    this.baseDN = baseDN;
    this.config = config;
    this.storage = storage;
    this.rootContainer = rootContainer;
    this.treePrefix = baseDN.toNormalizedUrlSafeString();
    this.id2childrenCount = new ID2Count(getIndexName(ID2CHILDREN_COUNT_TREE_NAME));
    this.dn2id = new DN2ID(getIndexName(DN2ID_TREE_NAME), baseDN);
    this.dn2uri = new DN2URI(getIndexName(REFERRAL_TREE_NAME), this);
    this.state = new State(getIndexName(STATE_TREE_NAME));

    config.addPluggableChangeListener(this);

    attributeIndexCfgManager = new AttributeIndexCfgManager();
    config.addBackendIndexAddListener(attributeIndexCfgManager);
    config.addBackendIndexDeleteListener(attributeIndexCfgManager);

    vlvIndexCfgManager = new VLVIndexCfgManager();
    config.addBackendVLVIndexAddListener(vlvIndexCfgManager);
    config.addBackendVLVIndexDeleteListener(vlvIndexCfgManager);
  }

  private TreeName getIndexName(String indexId)
  {
    return new TreeName(treePrefix, indexId);
  }

  /**
   * Opens the entryContainer for reading and writing.
   *
   * @param txn a non null transaction
   * @throws StorageRuntimeException If an error occurs in the storage.
   * @throws ConfigException if a configuration related error occurs.
   */
  void open(WriteableTransaction txn) throws StorageRuntimeException, ConfigException
  {
    try
    {
      DataConfig entryDataConfig =
        new DataConfig(config.isEntriesCompressed(),
            config.isCompactEncoding(),
            rootContainer.getCompressedSchema());

      id2entry = new ID2Entry(getIndexName(ID2ENTRY_TREE_NAME), entryDataConfig);
      id2entry.open(txn);
      id2childrenCount.open(txn);
      dn2id.open(txn);
      state.open(txn);
      dn2uri.open(txn);

      for (String idx : config.listBackendIndexes())
      {
        BackendIndexCfg indexCfg = config.getBackendIndex(idx);

        AttributeIndex index = new AttributeIndex(indexCfg, state, this, txn);
        index.open(txn);
        if(!index.isTrusted())
        {
          logger.info(NOTE_INDEX_ADD_REQUIRES_REBUILD, index.getName());
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
          logger.info(NOTE_INDEX_ADD_REQUIRES_REBUILD, vlvIndex.getName());
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
   * @throws StorageRuntimeException If an error occurs in the storage.
   */
  @Override
  public void close() throws StorageRuntimeException
  {
    closeSilently(attrIndexMap.values());
    closeSilently(vlvIndexMap.values());

    // Deregister any listeners.
    config.removePluggableChangeListener(this);
    config.removeBackendIndexAddListener(attributeIndexCfgManager);
    config.removeBackendIndexDeleteListener(attributeIndexCfgManager);
    config.removeBackendVLVIndexAddListener(vlvIndexCfgManager);
    config.removeBackendVLVIndexDeleteListener(vlvIndexCfgManager);
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
   * Get the DN tree used by this entry container.
   * The entryContainer must have been opened.
   *
   * @return The DN tree.
   */
  DN2ID getDN2ID()
  {
    return dn2id;
  }

  /**
   * Get the entry tree used by this entry container.
   * The entryContainer must have been opened.
   *
   * @return The entry tree.
   */
  ID2Entry getID2Entry()
  {
    return id2entry;
  }

  /**
   * Get the referral tree used by this entry container.
   * The entryContainer must have been opened.
   *
   * @return The referral tree.
   */
  DN2URI getDN2URI()
  {
    return dn2uri;
  }

  /**
   * Get the children tree used by this entry container.
   * The entryContainer must have been opened.
   *
   * @return The children tree.
   */
  ID2Count getID2ChildrenCount()
  {
    return id2childrenCount;
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
   * @param txn a non null transaction
   * @return The highest entry ID.
   * @throws StorageRuntimeException If an error occurs in the storage.
   */
  EntryID getHighestEntryID(ReadableTransaction txn) throws StorageRuntimeException
  {
    Cursor<ByteString, ByteString> cursor = txn.openCursor(id2entry.getName());
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

  boolean hasSubordinates(final DN dn)
  {
    try
    {
      return storage.read(new ReadOperation<Boolean>()
      {
        @Override
        public Boolean run(final ReadableTransaction txn) throws Exception
        {
          try (final SequentialCursor<?, ?> cursor = dn2id.openChildrenCursor(txn, dn))
          {
            return cursor.next();
          }
        }
      });
    }
    catch (Exception e)
    {
      throw new StorageRuntimeException(e);
    }
  }

  /**
   * Determine the number of children entries for a given entry.
   *
   * @param entryDN The distinguished name of the entry.
   * @return The number of children entries for the given entry or -1 if
   *         the entry does not exist.
   * @throws StorageRuntimeException If an error occurs in the storage.
   */
  long getNumberOfChildren(final DN entryDN) throws StorageRuntimeException
  {
    try
    {
      return storage.read(new ReadOperation<Long>()
      {
        @Override
        public Long run(ReadableTransaction txn) throws Exception
        {
          final EntryID entryID = dn2id.get(txn, entryDN);
          return entryID != null ? id2childrenCount.getCount(txn, entryID) : -1;
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
   * @throws StorageRuntimeException If an error occurs in the storage.
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
        public Void run(final ReadableTransaction txn) throws Exception
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
            throw new DirectoryException(
                ResultCode.CONSTRAINT_VIOLATION, ERR_SEARCH_CANNOT_MIX_PAGEDRESULTS_AND_VLV.get());
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

          EntryIDSet entryIDSet = null;
          boolean candidatesAreInScope = false;
          if (sortRequest != null)
          {
            for (VLVIndex vlvIndex : vlvIndexMap.values())
            {
              try
              {
                entryIDSet = vlvIndex.evaluate(txn, searchOperation, sortRequest, vlvRequest, debugBuffer);
                if (entryIDSet != null)
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

          if (entryIDSet == null)
          {
            if (processSearchWithVirtualAttributeRule(searchOperation, true))
            {
              return null;
            }

            // Create an index filter to get the search result candidate entries
            IndexFilter indexFilter = new IndexFilter(
                EntryContainer.this, txn, searchOperation, debugBuffer, rootContainer.getMonitorProvider());

            // Evaluate the filter against the attribute indexes.
            entryIDSet = indexFilter.evaluate();

            if (!isBelowFilterThreshold(entryIDSet))
            {
              final EntryIDSet scopeSet = getIDSetFromScope(txn, aBaseDN, searchScope);
              entryIDSet.retainAll(scopeSet);
              if (debugBuffer != null)
              {
                debugBuffer.append(" scope=").append(searchScope);
                scopeSet.toString(debugBuffer);
              }
              if (scopeSet.isDefined())
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
                entryIDSet = sort(txn, entryIDSet, searchOperation, sortRequest.getSortOrder(), vlvRequest);
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
            entryIDSet.toString(debugBuffer);

            Entry debugEntry = buildDebugSearchIndexEntry(debugBuffer);
            searchOperation.returnEntry(debugEntry, null);
            return null;
          }

          if (entryIDSet.isDefined())
          {
            rootContainer.getMonitorProvider().updateIndexedSearchCount();
            searchIndexed(txn, entryIDSet, candidatesAreInScope, searchOperation, pageRequest);
          }
          else
          {
            rootContainer.getMonitorProvider().updateUnindexedSearchCount();

            searchOperation.addAdditionalLogItem(keyOnly(getClass(), "unindexed"));

            if (processSearchWithVirtualAttributeRule(searchOperation, false))
            {
              return null;
            }

            ClientConnection clientConnection = searchOperation.getClientConnection();
            if (!clientConnection.hasPrivilege(Privilege.UNINDEXED_SEARCH, searchOperation))
            {
              throw new DirectoryException(
                  ResultCode.INSUFFICIENT_ACCESS_RIGHTS, ERR_SEARCH_UNINDEXED_INSUFFICIENT_PRIVILEGES.get());
            }

            if (sortRequest != null)
            {
              // FIXME -- Add support for sorting unindexed searches using indexes
              // like DSEE currently does.
              searchOperation.addResponseControl(new ServerSideSortResponseControl(UNWILLING_TO_PERFORM, null));

              if (sortRequest.isCritical())
              {
                throw new DirectoryException(
                    ResultCode.UNAVAILABLE_CRITICAL_EXTENSION, ERR_SEARCH_CANNOT_SORT_UNINDEXED.get());
              }
            }

            searchNotIndexed(txn, searchOperation, pageRequest);
          }
          return null;
        }

        private EntryIDSet getIDSetFromScope(final ReadableTransaction txn, DN aBaseDN, SearchScope searchScope)
            throws DirectoryException
        {
          final EntryIDSet scopeSet;
          try
          {
            switch (searchScope.asEnum())
            {
            case BASE_OBJECT:
              try (final SequentialCursor<?, EntryID> scopeCursor = dn2id.openCursor(txn, aBaseDN))
              {
                scopeSet = EntryIDSet.newDefinedSet(scopeCursor.getValue().longValue());
              }
              break;
            case SINGLE_LEVEL:
              try (final SequentialCursor<?, EntryID> scopeCursor = dn2id.openChildrenCursor(txn, aBaseDN))
              {
                scopeSet = newIDSetFromCursor(scopeCursor, false);
              }
              break;
            case SUBORDINATES:
            case WHOLE_SUBTREE:
              try (final SequentialCursor<?, EntryID> scopeCursor = dn2id.openSubordinatesCursor(txn, aBaseDN))
              {
                scopeSet = newIDSetFromCursor(scopeCursor, searchScope.equals(SearchScope.WHOLE_SUBTREE));
              }
              break;
            default:
              throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                  CoreMessages.INFO_ERROR_SEARCH_SCOPE_NOT_ALLOWED.get());
            }
          }
          catch (NoSuchElementException e)
          {
            throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, ERR_SEARCH_NO_SUCH_OBJECT.get(aBaseDN),
                getMatchedDN(txn, aBaseDN), e);
          }
          return scopeSet;
        }
      });
    }
    catch (Exception e)
    {
      throwAllowedExceptionTypes(e, DirectoryException.class, CanceledOperationException.class);
    }
  }

  private static EntryIDSet newIDSetFromCursor(SequentialCursor<?, EntryID> cursor, boolean includeCurrent)
  {
    final long ids[] = new long[SCOPE_IDSET_LIMIT];
    int offset = 0;
    if (includeCurrent) {
      ids[offset++] = cursor.getValue().longValue();
    }
    for(; offset < ids.length && cursor.next() ; offset++) {
      ids[offset] = cursor.getValue().longValue();
    }
    return offset == SCOPE_IDSET_LIMIT
        ? EntryIDSet.newUndefinedSet()
        : EntryIDSet.newDefinedSet(Arrays.copyOf(ids, offset));
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

  private boolean processSearchWithVirtualAttributeRule(final SearchOperation searchOperation, boolean isPreIndexed)
  {
    for (VirtualAttributeRule rule : DirectoryServer.getVirtualAttributes())
    {
      VirtualAttributeProvider<?> provider = rule.getProvider();
      if (provider.isSearchable(rule, searchOperation, isPreIndexed))
      {
        provider.processSearch(rule, searchOperation);
        return true;
      }
    }
    return false;
  }

  private Entry buildDebugSearchIndexEntry(StringBuilder debugBuffer) throws DirectoryException
  {
    Attribute attr = Attributes.create(ATTR_DEBUG_SEARCH_INDEX, debugBuffer.toString());
    Entry entry = new Entry(DN.valueOf("cn=debugsearch"), null, null, null);
    entry.addAttribute(attr, new ArrayList<ByteString>());
    return entry;
  }

  /**
   * We were not able to obtain a set of candidate entry IDs for the
   * search from the indexes.
   * <p>
   * Here we are relying on the DN key order to ensure children are
   * returned after their parents.
   * <ul>
   * <li>iterate through a subtree range of the DN tree
   * <li>discard non-children DNs if the search scope is single level
   * <li>fetch the entry by ID from the entry cache or the entry tree
   * <li>return the entry if it matches the filter
   * </ul>
   *
   * @param searchOperation The search operation.
   * @param pageRequest A Paged Results control, or null if none.
   * @throws DirectoryException If an error prevented the search from being
   * processed.
   */
  private void searchNotIndexed(ReadableTransaction txn, SearchOperation searchOperation,
      PagedResultsControl pageRequest) throws DirectoryException, CanceledOperationException
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
    ByteStringBuilder suffix = beforeKey(baseDNKey);
    ByteStringBuilder end = afterKey(baseDNKey);

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
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
            ERR_INVALID_PAGED_RESULTS_COOKIE.get(pageRequest.getCookie().toHexString()), e);
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
      final Cursor<ByteString, ByteString> cursor = txn.openCursor(dn2id.getName());
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
            searchOperation.appendErrorMessage(NOTE_LOOKTHROUGH_LIMIT_EXCEEDED.get(lookthroughLimit));
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
   * @param txn a non null transaction
   * @param entryID
   *          the id of the entry to retrieve
   * @return the entry corresponding to the provided entryID
   * @throws DirectoryException
   *           If an error occurs retrieving the entry
   */
  Entry getEntry(ReadableTransaction txn, EntryID entryID) throws DirectoryException
  {
    // Try the entry cache first.
    final EntryCache<?> entryCache = getEntryCache();
    final Entry cacheEntry = entryCache.getEntry(backend, entryID.longValue());
    if (cacheEntry != null)
    {
      return cacheEntry;
    }

    final Entry entry = id2entry.get(txn, entryID);
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
   * @param entryIDSet The candidate entry IDs.
   * @param candidatesAreInScope true if it is certain that every candidate
   *                             entry is in the search scope.
   * @param searchOperation The search operation.
   * @param pageRequest A Paged Results control, or null if none.
   * @throws DirectoryException If an error prevented the search from being
   * processed.
   */
  private void searchIndexed(ReadableTransaction txn, EntryIDSet entryIDSet, boolean candidatesAreInScope,
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
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
            ERR_INVALID_PAGED_RESULTS_COOKIE.get(pageRequest.getCookie().toHexString()), e);
      }
    }
    else if (!manageDsaIT)
    {
      continueSearch = dn2uri.returnSearchReferences(txn, searchOperation);
    }

    // Make sure the candidate list is smaller than the lookthrough limit
    int lookthroughLimit =
      searchOperation.getClientConnection().getLookthroughLimit();
    if(lookthroughLimit > 0 && entryIDSet.size() > lookthroughLimit)
    {
      //Lookthrough limit exceeded
      searchOperation.setResultCode(ResultCode.ADMIN_LIMIT_EXCEEDED);
      searchOperation.appendErrorMessage(NOTE_LOOKTHROUGH_LIMIT_EXCEEDED.get(lookthroughLimit));
      continueSearch = false;
    }

    // Iterate through the index candidates.
    if (continueSearch)
    {
      final SearchFilter filter = searchOperation.getFilter();
      for (Iterator<EntryID> it = entryIDSet.iterator(begin); it.hasNext();)
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
        if (entry != null
              && isInScope(candidatesAreInScope, searchScope, aBaseDN, entry)
              && (manageDsaIT || entry.getReferralURLs() == null)
              && filter.matchesEntry(entry))
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
   * Adds the provided entry to this tree.  This method must ensure that the
   * entry is appropriate for the tree and that no entry already exists with
   * the same DN.  The caller must hold a write lock on the DN of the provided
   * entry.
   *
   * @param entry        The entry to add to this tree.
   * @param addOperation The add operation with which the new entry is
   *                     associated.  This may be <CODE>null</CODE> for adds
   *                     performed internally.
   * @throws DirectoryException If a problem occurs while trying to add the
   *                            entry.
   * @throws StorageRuntimeException If an error occurs in the storage.
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
        public void run(WriteableTransaction txn) throws Exception
        {
          DN parentDN = getParentWithinBase(entry.getName());

          try
          {
            // Check whether the entry already exists.
            if (dn2id.get(txn, entry.getName()) != null)
            {
              throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS, ERR_ADD_ENTRY_ALREADY_EXISTS.get(
                  entry.getName()));
            }

            // Check that the parent entry exists.
            EntryID parentID = null;
            if (parentDN != null)
            {
              // Check for referral entries above the target.
              dn2uri.targetEntryReferrals(txn, entry.getName(), null);

              // Read the parent ID from dn2id.
              parentID = dn2id.get(txn, parentDN);
              if (parentID == null)
              {
                throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
                    ERR_ADD_NO_SUCH_OBJECT.get(entry.getName()), getMatchedDN(txn, baseDN), null);
              }
              id2childrenCount.addDelta(txn, parentID, 1);
            }

            EntryID entryID = rootContainer.getNextEntryID();
            dn2id.put(txn, entry.getName(), entryID);
            dn2uri.addEntry(txn, entry);
            id2entry.put(txn, entryID, entry);

            // Insert into the indexes, in index configuration order.
            final IndexBuffer indexBuffer = new IndexBuffer(EntryContainer.this);
            indexInsertEntry(indexBuffer, entry, entryID);

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
            throw new DirectoryException(
                DirectoryServer.getServerErrorResultCode(), ERR_UNCHECKED_EXCEPTION.get(msg), e);
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
   * Removes the specified entry from this tree.  This method must ensure
   * that the entry exists and that it does not have any subordinate entries
   * (unless the storage supports a subtree delete operation and the client
   * included the appropriate information in the request).  The caller must hold
   * a write lock on the provided entry DN.
   *
   * @param entryDN         The DN of the entry to remove from this tree.
   * @param deleteOperation The delete operation with which this action is
   *                        associated.  This may be <CODE>null</CODE> for
   *                        deletes performed internally.
   * @throws DirectoryException If a problem occurs while trying to remove the
   *                            entry.
   * @throws StorageRuntimeException If an error occurs in the storage.
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
        public void run(WriteableTransaction txn) throws Exception
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
            ByteStringBuilder suffix = beforeKey(entryDNKey);
            ByteStringBuilder end = afterKey(entryDNKey);

            int subordinateEntriesDeleted = 0;

            Cursor<ByteString, ByteString> cursor = txn.openCursor(dn2id.getName());
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
                  throw new DirectoryException(ResultCode.NOT_ALLOWED_ON_NONLEAF, ERR_DELETE_NOT_ALLOWED_ON_NONLEAF
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
                  Entry subordinateEntry = id2entry.get(txn, entryID);
                  SubordinateDelete pluginResult =
                      getPluginConfigManager().invokeSubordinateDeletePlugins(deleteOperation, subordinateEntry);

                  if (!pluginResult.continueProcessing())
                  {
                    throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                        ERR_DELETE_ABORTED_BY_SUBORDINATE_PLUGIN.get(subordinateEntry.getName()));
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
            throw new DirectoryException(
                DirectoryServer.getServerErrorResultCode(), ERR_UNCHECKED_EXCEPTION.get(msg), e);
          }
        }
      });
    }
    catch (Exception e)
    {
      throwAllowedExceptionTypes(e, DirectoryException.class, CanceledOperationException.class);
    }
  }

  private void deleteEntry(WriteableTransaction txn,
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
      // FIXME: previously this used a RMW lock - see OPENDJ-1878.
      ByteString value = txn.read(dn2id.getName(), leafDNKey);
      if (value == null)
      {
        LocalizableMessage message = ERR_DELETE_NO_SUCH_OBJECT.get(targetDN);
        DN matchedDN = getMatchedDN(txn, baseDN);
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, matchedDN, null);
      }
      leafID = new EntryID(value);
    }

    // Remove from dn2id.
    if (!txn.delete(dn2id.getName(), leafDNKey))
    {
      // Do not expect to ever come through here.
      throw new DirectoryException(
          ResultCode.NO_SUCH_OBJECT, ERR_DELETE_NO_SUCH_OBJECT.get(leafDNKey), getMatchedDN(txn, baseDN), null);
    }

    // Check that the entry exists in id2entry and read its contents.
    // FIXME: previously this used a RMW lock - see OPENDJ-1878.
    Entry entry = id2entry.get(txn, leafID);
    if (entry == null)
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          ERR_MISSING_ID2ENTRY_RECORD.get(leafID));
    }

    if (!manageDsaIT)
    {
      dn2uri.checkTargetForReferral(entry, null);
    }

    // Update the referral tree.
    dn2uri.deleteEntry(txn, entry);

    // Remove from id2entry.
    if (!id2entry.remove(txn, leafID))
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          ERR_MISSING_ID2ENTRY_RECORD.get(leafID));
    }

    // Remove from the indexes, in index config order.
    indexRemoveEntry(indexBuffer, entry, leafID);

    // Remove the children counter for this entry.
    id2childrenCount.deleteCount(txn, leafID);

    // Iterate up through the superior entries from the target entry.
    final DN parentDN = getParentWithinBase(targetDN);
    if (parentDN != null)
    {
      final EntryID parentID = dn2id.get(txn, parentDN);
      if (parentID == null)
      {
        throw new StorageRuntimeException(ERR_MISSING_DN2ID_RECORD.get(parentDN).toString());
      }
      id2childrenCount.addDelta(txn, parentID, -1);
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
  private boolean entryExists(ReadableTransaction txn, final DN entryDN) throws DirectoryException
  {
    // Try the entry cache first.
    EntryCache<?> entryCache = DirectoryServer.getEntryCache();
    if (entryCache != null && entryCache.containsEntry(entryDN))
    {
      return true;
    }
    return dn2id.get(txn, entryDN) != null;
  }


  boolean entryExists(final DN entryDN) throws StorageRuntimeException
  {
    final EntryCache<?> entryCache = DirectoryServer.getEntryCache();
    if (entryCache != null && entryCache.containsEntry(entryDN))
    {
      return true;
    }

    try
    {
      return storage.read(new ReadOperation<Boolean>()
      {
        @Override
        public Boolean run(ReadableTransaction txn) throws Exception
        {
          return dn2id.get(txn, entryDN) != null;
        }
      });
    }
    catch (Exception e)
    {
      throw new StorageRuntimeException(e);
    }
  }

  /**
   * Fetch an entry by DN, trying the entry cache first, then the tree.
   * Retrieves the requested entry, trying the entry cache first,
   * then the tree.
   *
   * @param entryDN The distinguished name of the entry to retrieve.
   * @return The requested entry, or <CODE>null</CODE> if the entry does not
   *         exist.
   * @throws DirectoryException If a problem occurs while trying to retrieve
   *                            the entry.
   * @throws StorageRuntimeException An error occurred during a storage operation.
   */
  Entry getEntry(final DN entryDN) throws StorageRuntimeException, DirectoryException
  {
    try
    {
      return storage.read(new ReadOperation<Entry>()
      {
        @Override
        public Entry run(ReadableTransaction txn) throws Exception
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

  private Entry getEntry0(ReadableTransaction txn, final DN entryDN) throws StorageRuntimeException, DirectoryException
  {
    final EntryCache<?> entryCache = DirectoryServer.getEntryCache();
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
      final EntryID entryID = dn2id.get(txn, entryDN);
      if (entryID == null)
      {
        // The entryDN does not exist. Check for referral entries above the target entry.
        dn2uri.targetEntryReferrals(txn, entryDN, null);
        return null;
      }

      final Entry entry = id2entry.get(txn, entryID);
      if (entry != null && entryCache != null)
      {
        /*
         * Put the entry in the cache making sure not to overwrite a newer copy that may have been
         * inserted since the time we read the cache.
         */
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
   * @throws StorageRuntimeException If an error occurs in the storage.
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
        public void run(WriteableTransaction txn) throws Exception
        {
          try
          {
            EntryID entryID = dn2id.get(txn, newEntry.getName());
            if (entryID == null)
            {
              throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
                  ERR_MODIFY_NO_SUCH_OBJECT.get(newEntry.getName()), getMatchedDN(txn, baseDN), null);
            }

            if (!isManageDsaITOperation(modifyOperation))
            {
              // Check if the entry is a referral entry.
              dn2uri.checkTargetForReferral(oldEntry, null);
            }

            // Update the referral tree.
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
            throw new DirectoryException(
                DirectoryServer.getServerErrorResultCode(), ERR_UNCHECKED_EXCEPTION.get(msg), e);
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
   * @throws StorageRuntimeException If an error occurs in the storage.
   */
  void renameEntry(final DN currentDN, final Entry entry, final ModifyDNOperation modifyDNOperation)
      throws StorageRuntimeException, DirectoryException, CanceledOperationException
  {
    // FIXME: consistency + isolation cannot be maintained lock free - see OPENDJ-1878.
    try
    {
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
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
            if (!currentDN.equals(entry.getName()) && dn2id.get(txn, entry.getName()) != null)
            {
              LocalizableMessage message = ERR_MODIFYDN_ALREADY_EXISTS.get(entry.getName());
              throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS, message);
            }

            EntryID oldApexID = dn2id.get(txn, currentDN);
            if (oldApexID == null)
            {
              // Check for referral entries above the target entry.
              dn2uri.targetEntryReferrals(txn, currentDN, null);

              throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
                  ERR_MODIFYDN_NO_SUCH_OBJECT.get(currentDN), getMatchedDN(txn, baseDN), null);
            }

            Entry oldApexEntry = id2entry.get(txn, oldApexID);
            if (oldApexEntry == null)
            {
              throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), ERR_MISSING_ID2ENTRY_RECORD
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
              EntryID newSuperiorID = dn2id.get(txn, newSuperiorDN);
              if (newSuperiorID == null)
              {
                throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
                    ERR_NEW_SUPERIOR_NO_SUCH_OBJECT.get(newSuperiorDN), getMatchedDN(txn, baseDN), null);
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
            ByteStringBuilder suffix = beforeKey(currentDNKey);
            ByteStringBuilder end = afterKey(currentDNKey);

            Cursor<ByteString, ByteString> cursor = txn.openCursor(dn2id.getName());
            try
            {

              // Step forward until we pass the ending value.
              boolean success = cursor.positionToKeyOrNext(suffix);
              while (success && cursor.getKey().compareTo(end) < 0)
              {
                // We have found a subordinate entry.
                EntryID oldID = new EntryID(cursor.getValue());
                Entry oldEntry = id2entry.get(txn, oldID);

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
            throw new DirectoryException(
                DirectoryServer.getServerErrorResultCode(), ERR_UNCHECKED_EXCEPTION.get(msg), e);
          }
        }
      });
    }
    catch (Exception e)
    {
      throwAllowedExceptionTypes(e, DirectoryException.class, CanceledOperationException.class);
    }
  }

  /** Represents an renamed entry that was deleted from but yet to be added back. */
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

  private void addRenamedEntry(WriteableTransaction txn, IndexBuffer buffer,
                           EntryID newID,
                           Entry newEntry,
                           boolean isApexEntryMoved,
                           boolean renumbered,
                           ModifyDNOperation modifyDNOperation)
      throws DirectoryException, StorageRuntimeException
  {
    // FIXME: the core server should validate that the new subtree location is empty.
    dn2id.put(txn, newEntry.getName(), newID);
    id2entry.put(txn, newID, newEntry);
    dn2uri.addEntry(txn, newEntry);

    if (renumbered || modifyDNOperation == null)
    {
      // Reindex the entry with the new ID.
      indexInsertEntry(buffer, newEntry, newID);
    }

    if(isApexEntryMoved)
    {
      final DN parentDN = getParentWithinBase(newEntry.getName());
      if (parentDN != null)
      {
        id2childrenCount.addDelta(txn, dn2id.get(txn, parentDN), 1);
      }
    }
  }

  private void removeApexEntry(WriteableTransaction txn, IndexBuffer buffer,
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

    if(oldSuperiorDN != null && isApexEntryMoved)
    {
      // Since entry has moved, oldSuperiorDN has lost a child
      id2childrenCount.addDelta(txn, dn2id.get(txn, oldSuperiorDN), -1);
    }

    if (!newID.equals(oldID))
    {
      id2childrenCount.addDelta(txn, newID, id2childrenCount.deleteCount(txn, oldID));
    }

    if (!newID.equals(oldID) || modifyDNOperation == null)
    {
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

  private void removeSubordinateEntry(WriteableTransaction txn, IndexBuffer buffer,
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
            ERR_MODIFYDN_ABORTED_BY_SUBORDINATE_PLUGIN.get(oldDN, newDN));
      }

      if (! modifications.isEmpty())
      {
        LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
        if (! newEntry.conformsToSchema(null, false, false, false,
            invalidReason))
        {
          throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
              ERR_MODIFYDN_ABORTED_BY_SUBORDINATE_SCHEMA_ERROR.get(oldDN, newDN, invalidReason));
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

    if (!newID.equals(oldID))
    {
      id2childrenCount.deleteCount(txn, oldID);

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
   * @throws StorageRuntimeException If an error occurs in the storage.
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
   * @throws StorageRuntimeException If an error occurs in the storage.
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
   * @throws StorageRuntimeException If an error occurs in the storage.
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
        index.modifyEntry(buffer, entryID, oldEntry, newEntry);
      }
    }

    for(VLVIndex vlvIndex : vlvIndexMap.values())
    {
      vlvIndex.modifyEntry(buffer, entryID, oldEntry, newEntry, mods);
    }
  }

  /**
   * Get a count of the number of entries stored in this entry container including the baseDN
   *
   * @param txn
   *          a non null transaction
   * @return The number of entries stored in this entry container including the baseDN.
   * @throws StorageRuntimeException
   *           If an error occurs in the storage.
   */
  long getNumberOfEntriesInBaseDN() throws StorageRuntimeException
  {
    try
    {
      return storage.read(new ReadOperation<Long>()
      {
        @Override
        public Long run(ReadableTransaction txn) throws Exception
        {
          final int baseDnIfExists = dn2id.get(txn, baseDN) != null ? 1 : 0;
          return id2childrenCount.getTotalCount(txn) + baseDnIfExists;
        }
      });
    }
    catch (Exception e)
    {
      throw new StorageRuntimeException(e);
    }
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
   * @param txn a non null transaction
   * @throws StorageRuntimeException If an error occurs while removing the entry
   *                           container.
   */
  void delete(WriteableTransaction txn) throws StorageRuntimeException
  {
    for (Tree tree : listTrees())
    {
      tree.delete(txn);
    }
  }

  /**
   * Remove a tree from disk.
   *
   * @param txn a non null transaction
   * @param tree The tree container to remove.
   * @throws StorageRuntimeException If an error occurs while attempting to delete the tree.
   */
  void deleteTree(WriteableTransaction txn, Tree tree) throws StorageRuntimeException
  {
    if(tree == state)
    {
      // The state tree can not be removed individually.
      return;
    }
    tree.delete(txn);
    if(tree instanceof Index)
    {
      state.deleteRecord(txn, tree.getName());
    }
  }

  /**
   * This method constructs a container name from a base DN. Only alphanumeric
   * characters are preserved, all other characters are replaced with an
   * underscore.
   *
   * @return The container name for the base DN.
   */
  String getTreePrefix()
  {
    return treePrefix;
  }

  /**
   * Sets a new tree prefix for this entry container and rename all
   * existing trees in use by this entry container.
   *
   * @param newBaseDN The new tree prefix to use.
   * @throws StorageRuntimeException If an error occurs in the storage.
   */
  void setTreePrefix(final String newBaseDN) throws StorageRuntimeException
  {
    final List<Tree> allTrees = listTrees();
    try
    {
      // Rename in transaction.
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          for(Tree tree : allTrees)
          {
            TreeName oldName = tree.getName();
            TreeName newName = oldName.replaceBaseDN(newBaseDN);
            txn.renameTree(oldName, newName);
          }
        }
      });
      // Only rename the containers if the txn succeeded.
      for (Tree tree : allTrees)
      {
        TreeName oldName = tree.getName();
        TreeName newName = oldName.replaceBaseDN(newBaseDN);
        tree.setName(newName);
      }
    }
    catch (Exception e)
    {
      String msg = e.getMessage();
      if (msg == null)
      {
        msg = stackTraceToSingleLineString(e);
      }
      throw new StorageRuntimeException(ERR_UNCHECKED_EXCEPTION.get(msg).toString(), e);
    }
    finally
    {
      try
      {
        storage.write(new WriteOperation()
        {
          @Override
          public void run(WriteableTransaction txn) throws Exception
          {
            // Open the containers backup.
            for(Tree tree : allTrees)
            {
              tree.open(txn);
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
        throw new StorageRuntimeException(ERR_UNCHECKED_EXCEPTION.get(msg).toString(), e);
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
        public void run(WriteableTransaction txn) throws Exception
        {
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
        public void run(WriteableTransaction txn) throws Exception
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

  private void clear0(WriteableTransaction txn) throws StorageRuntimeException
  {
    final List<Tree> allTrees = listTrees();
    try
    {
      for (Tree tree : allTrees)
      {
        tree.delete(txn);
      }
    }
    finally
    {
      for(Tree tree : allTrees)
      {
        tree.open(txn);
      }

      for (Tree tree : allTrees)
      {
        if (tree instanceof Index)
        {
          ((Index) tree).setTrusted(txn, true);
        }
      }
    }
  }

  List<Tree> listTrees()
  {
    final List<Tree> allTrees = new ArrayList<Tree>();
    allTrees.add(dn2id);
    allTrees.add(id2entry);
    allTrees.add(dn2uri);
    allTrees.add(id2childrenCount);
    allTrees.add(state);

    for (AttributeIndex index : attrIndexMap.values())
    {
      allTrees.addAll(index.getNameToIndexes().values());
    }

    allTrees.addAll(vlvIndexMap.values());
    return allTrees;
  }

  /**
   * Clear the contents for a tree from disk.
   *
   * @param txn a non null transaction
   * @param tree The tree to clear.
   * @throws StorageRuntimeException if a storage error occurs.
   */
  void clearTree(WriteableTransaction txn, Tree tree) throws StorageRuntimeException
  {
    try
    {
      tree.delete(txn);
    }
    finally
    {
      tree.open(txn);
    }
    if(logger.isTraceEnabled())
    {
      logger.trace("Cleared the tree %s", tree.getName());
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
  private DN getMatchedDN(ReadableTransaction txn, DN baseDN) throws DirectoryException
  {
    DN parentDN  = baseDN.getParentDNInSuffix();
    while (parentDN != null && parentDN.isDescendantOf(getBaseDN()))
    {
      if (entryExists(txn, parentDN))
      {
        return parentDN;
      }
      parentDN = parentDN.getParentDNInSuffix();
    }
    return null;
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
  private Entry fetchBaseEntry(ReadableTransaction txn, DN baseDN, SearchScope searchScope)
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

      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
          ERR_SEARCH_NO_SUCH_OBJECT.get(baseDN), getMatchedDN(txn, baseDN), null);
    }

    return baseEntry;
  }

  private EntryIDSet sort(ReadableTransaction txn, EntryIDSet entryIDSet, SearchOperation searchOperation,
      SortOrder sortOrder, VLVRequestControl vlvRequest) throws DirectoryException
  {
    if (!entryIDSet.isDefined())
    {
      return newUndefinedSet();
    }

    final DN baseDN = searchOperation.getBaseDN();
    final SearchScope scope = searchOperation.getScope();
    final SearchFilter filter = searchOperation.getFilter();

    final TreeMap<ByteString, EntryID> sortMap = new TreeMap<ByteString, EntryID>();
    for (EntryID id : entryIDSet)
    {
      try
      {
        Entry e = getEntry(txn, id);
        if (e.matchesBaseAndScope(baseDN, scope) && filter.matchesEntry(e))
        {
          sortMap.put(encodeVLVKey(sortOrder, e, id.longValue()), id);
        }
      }
      catch (Exception e)
      {
        LocalizableMessage message = ERR_ENTRYIDSORTER_CANNOT_EXAMINE_ENTRY.get(id, getExceptionMessage(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message, e);
      }
    }

    // See if there is a VLV request to further pare down the set of results, and if there is where it should be
    // processed by offset or assertion value.
    if (vlvRequest == null)
    {
      return newDefinedSet(toArray(sortMap.values()));
    }

    if (vlvRequest.getTargetType() == VLVRequestControl.TYPE_TARGET_BYOFFSET)
    {
      return sortByOffset(searchOperation, vlvRequest, sortMap);
    }
    return sortByGreaterThanOrEqualAssertion(searchOperation, vlvRequest, sortOrder, sortMap);
  }

  private static final long[] toArray(Collection<EntryID> entryIDs)
  {
    final long[] array = new long[entryIDs.size()];
    int i = 0;
    for (EntryID entryID : entryIDs)
    {
      array[i++] = entryID.longValue();
    }
    return array;
  }

  private static final EntryIDSet sortByGreaterThanOrEqualAssertion(SearchOperation searchOperation,
      VLVRequestControl vlvRequest, SortOrder sortOrder, final TreeMap<ByteString, EntryID> sortMap)
      throws DirectoryException
  {
    ByteString assertionValue = vlvRequest.getGreaterThanOrEqualAssertion();
    ByteSequence encodedTargetAssertion =
        encodeTargetAssertion(sortOrder, assertionValue, searchOperation, sortMap.size());

    boolean targetFound = false;
    int index = 0;
    int targetIndex = 0;
    int startIndex = 0;
    int includedAfterCount = 0;
    long[] idSet = new long[sortMap.size()];
    for (Map.Entry<ByteString, EntryID> entry : sortMap.entrySet())
    {
      ByteString vlvKey = entry.getKey();
      EntryID id = entry.getValue();
      idSet[index++] = id.longValue();

      if (targetFound)
      {
        includedAfterCount++;
        if (includedAfterCount >= vlvRequest.getAfterCount())
        {
          break;
        }
      }
      else
      {
        targetFound = vlvKey.compareTo(encodedTargetAssertion) >= 0;
        if (targetFound)
        {
          startIndex = Math.max(0, targetIndex - vlvRequest.getBeforeCount());
        }
        targetIndex++;
      }
    }

    final EntryIDSet result;
    if (targetFound)
    {
      final long[] array = new long[index - startIndex];
      System.arraycopy(idSet, startIndex, array, 0, array.length);
      result = newDefinedSet(array);
    }
    else
    {
      /*
       * No entry was found to be greater than or equal to the sort key, so the target offset will
       * be one greater than the content count.
       */
      targetIndex = sortMap.size() + 1;
      result = newDefinedSet();
    }
    searchOperation.addResponseControl(new VLVResponseControl(targetIndex, sortMap.size(), LDAPResultCode.SUCCESS));
    return result;
  }

  private static final EntryIDSet sortByOffset(SearchOperation searchOperation, VLVRequestControl vlvRequest,
      TreeMap<ByteString, EntryID> sortMap) throws DirectoryException
  {
    int targetOffset = vlvRequest.getOffset();
    if (targetOffset < 0)
    {
      // The client specified a negative target offset. This
      // should never be allowed.
      searchOperation.addResponseControl(new VLVResponseControl(targetOffset, sortMap.size(),
          LDAPResultCode.OFFSET_RANGE_ERROR));

      LocalizableMessage message = ERR_ENTRYIDSORTER_NEGATIVE_START_POS.get();
      throw new DirectoryException(ResultCode.VIRTUAL_LIST_VIEW_ERROR, message);
    }

    // This is an easy mistake to make, since VLV offsets start at 1 instead of 0. We'll assume the client meant
    // to use 1.
    targetOffset = (targetOffset == 0) ? 1 : targetOffset;

    int beforeCount = vlvRequest.getBeforeCount();
    int afterCount = vlvRequest.getAfterCount();
    int listOffset = targetOffset - 1; // VLV offsets start at 1, not 0.
    int startPos = listOffset - beforeCount;
    if (startPos < 0)
    {
      // This can happen if beforeCount >= offset, and in this case we'll just adjust the start position to ignore
      // the range of beforeCount that doesn't exist.
      startPos = 0;
      beforeCount = listOffset;
    }
    else if (startPos >= sortMap.size())
    {
      // The start position is beyond the end of the list. In this case, we'll assume that the start position was
      // one greater than the size of the list and will only return the beforeCount entries.
      targetOffset = sortMap.size() + 1;
      listOffset = sortMap.size();
      startPos = listOffset - beforeCount;
      afterCount = 0;
    }

    int count = 1 + beforeCount + afterCount;
    long[] sortedIDs = new long[count];
    int treePos = 0;
    int arrayPos = 0;
    for (EntryID id : sortMap.values())
    {
      if (treePos++ < startPos)
      {
        continue;
      }

      sortedIDs[arrayPos++] = id.longValue();
      if (arrayPos >= count)
      {
        break;
      }
    }

    if (arrayPos < count)
    {
      // We don't have enough entries in the set to meet the requested page size, so we'll need to shorten the
      // array.
      sortedIDs = Arrays.copyOf(sortedIDs, arrayPos);
    }

    searchOperation.addResponseControl(new VLVResponseControl(targetOffset, sortMap.size(), LDAPResultCode.SUCCESS));
    return newDefinedSet(sortedIDs);
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
    return treePrefix;
  }

}
