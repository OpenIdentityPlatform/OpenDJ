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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 * Portions copyright 2013 Manuel Gaupp
 */
package org.opends.server.backends.pluggable;

import static org.forgerock.util.Utils.*;
import static org.opends.messages.BackendMessages.*;
import static org.opends.server.backends.pluggable.DnKeyFormat.*;
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
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationAddListener;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.config.server.ConfigurationDeleteListener;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.SortKey;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.server.config.server.BackendIndexCfg;
import org.forgerock.opendj.server.config.server.BackendVLVIndexCfg;
import org.forgerock.opendj.server.config.server.PluggableBackendCfg;
import org.forgerock.util.Pair;
import org.opends.messages.CoreMessages;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.EntryCache;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.api.plugin.PluginResult.SubordinateDelete;
import org.opends.server.api.plugin.PluginResult.SubordinateModifyDN;
import org.opends.server.backends.pluggable.spi.AccessMode;
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
import org.opends.server.core.ServerContext;
import org.opends.server.crypto.CryptoSuite;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.Operation;
import org.opends.server.types.Privilege;
import org.opends.server.types.SearchFilter;
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

  /** The backend configuration. */
  private PluggableBackendCfg config;
  /** ID of the backend to which this entry container belongs. */
  private final String backendID;
  /** The baseDN this entry container is responsible for. */
  private final DN baseDN;
  /** The root container in which this entryContainer belongs. */
  private final RootContainer rootContainer;
  /** The tree storage. */
  private final Storage storage;

  /** The DN tree maps a normalized DN string to an entry ID (8 bytes). */
  private final DN2ID dn2id;
  /** The entry tree maps an entry ID (8 bytes) to a complete encoded entry. */
  private ID2Entry id2entry;
  /** Store the number of children for each entry. */
  private final ID2ChildrenCount id2childrenCount;
  /** The referral tree maps a normalized DN string to labeled URIs. */
  private final DN2URI dn2uri;
  /** The state tree maps a config DN to config entries. */
  private final State state;

  /** The set of attribute indexes. */
  private final Map<AttributeType, AttributeIndex> attrIndexMap = new HashMap<>();

  private final Map<AttributeType, CryptoSuite> attrCryptoMap = new HashMap<>();
  /** The set of VLV (Virtual List View) indexes. */
  private final Map<String, VLVIndex> vlvIndexMap = new HashMap<>();

  /**
   * Prevents name clashes for common indexes (like id2entry) across multiple suffixes.
   * For example when a root container contains multiple suffixes.
   */
  private final String treePrefix;

  private final ServerContext serverContext;

  /**
   * This class is responsible for managing the configuration for attribute
   * indexes used within this entry container.
   */
  private class AttributeIndexCfgManager implements
  ConfigurationAddListener<BackendIndexCfg>,
  ConfigurationDeleteListener<BackendIndexCfg>
  {
    @Override
    public boolean isConfigurationAddAcceptable(final BackendIndexCfg cfg, List<LocalizableMessage> unacceptableReasons)
    {
      try
      {
        newAttributeIndex(cfg, null);
        return true;
      }
      catch(Exception e)
      {
        unacceptableReasons.add(LocalizableMessage.raw(e.getLocalizedMessage()));
        return false;
      }
    }

    @Override
    public ConfigChangeResult applyConfigurationAdd(final BackendIndexCfg cfg)
    {
      final ConfigChangeResult ccr = new ConfigChangeResult();
      try
      {
        final CryptoSuite cryptoSuite = newCryptoSuite(cfg.isConfidentialityEnabled());
        final AttributeIndex index = newAttributeIndex(cfg, cryptoSuite);
        storage.write(new WriteOperation()
        {
          @Override
          public void run(WriteableTransaction txn) throws Exception
          {
            index.open(txn, true);
            if (!index.isTrusted())
            {
              ccr.setAdminActionRequired(true);
              ccr.addMessage(NOTE_INDEX_ADD_REQUIRES_REBUILD.get(cfg.getAttribute().getNameOrOID()));
            }
            attrIndexMap.put(cfg.getAttribute(), index);
            attrCryptoMap.put(cfg.getAttribute(), cryptoSuite);
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

    @Override
    public boolean isConfigurationDeleteAcceptable(
        BackendIndexCfg cfg, List<LocalizableMessage> unacceptableReasons)
    {
      // TODO: validate more before returning true?
      return true;
    }

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
            attrCryptoMap.remove(cfg.getAttribute());
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
    @Override
    public boolean isConfigurationAddAcceptable(BackendVLVIndexCfg cfg, List<LocalizableMessage> unacceptableReasons)
    {
      return VLVIndex.isConfigurationAddAcceptable(cfg, unacceptableReasons);
    }

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
            vlvIndex.open(txn, true);
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

    @Override
    public boolean isConfigurationDeleteAcceptable(BackendVLVIndexCfg cfg, List<LocalizableMessage> unacceptableReasons)
    {
      // TODO: validate more before returning true?
      return true;
    }

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

  EntryContainer(DN baseDN, String backendID, PluggableBackendCfg config, Storage storage, RootContainer rootContainer,
      ServerContext serverContext) throws ConfigException
  {
    this.backendID = backendID;
    this.baseDN = baseDN;
    this.config = config;
    this.storage = storage;
    this.rootContainer = rootContainer;
    this.serverContext = serverContext;
    this.treePrefix = baseDN.toNormalizedUrlSafeString();
    this.id2childrenCount = new ID2ChildrenCount(getIndexName(ID2CHILDREN_COUNT_TREE_NAME));
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

  private CryptoSuite newCryptoSuite(boolean confidentiality)
  {
    return serverContext.getCryptoManager().newCryptoSuite(config.getCipherTransformation(),
        config.getCipherKeyLength(), confidentiality);
  }

  private AttributeIndex newAttributeIndex(BackendIndexCfg cfg, CryptoSuite cryptoSuite) throws ConfigException
  {
    return new AttributeIndex(cfg, state, this, cryptoSuite);
  }

  private DataConfig newDataConfig(PluggableBackendCfg config)
  {
    return new DataConfig.Builder()
        .compress(config.isEntriesCompressed())
        .encode(config.isCompactEncoding())
        .encrypt(config.isConfidentialityEnabled())
        .cryptoSuite(serverContext.getCryptoManager().newCryptoSuite(config.getCipherTransformation(),
            config.getCipherKeyLength(),config.isConfidentialityEnabled()))
        .schema(rootContainer.getCompressedSchema())
        .build();
  }

  private TreeName getIndexName(String indexId)
  {
    return new TreeName(treePrefix, indexId);
  }

  /**
   * Opens the entryContainer for reading and writing.
   *
   * @param txn a non null transaction
   * @param accessMode specifies how the container has to be opened (read-write or read-only)
   * @throws StorageRuntimeException If an error occurs in the storage.
   * @throws ConfigException if a configuration related error occurs.
   */
  void open(WriteableTransaction txn, AccessMode accessMode) throws StorageRuntimeException, ConfigException
  {
    boolean shouldCreate = accessMode.isWriteable();
    try
    {
      id2entry = new ID2Entry(getIndexName(ID2ENTRY_TREE_NAME), newDataConfig(config));
      id2entry.open(txn, shouldCreate);
      id2childrenCount.open(txn, shouldCreate);
      dn2id.open(txn, shouldCreate);
      state.open(txn, shouldCreate);
      dn2uri.open(txn, shouldCreate);

      for (String idx : config.listBackendIndexes())
      {
        BackendIndexCfg indexCfg = config.getBackendIndex(idx);

        CryptoSuite cryptoSuite = newCryptoSuite(indexCfg.isConfidentialityEnabled());
        final AttributeIndex index = newAttributeIndex(indexCfg, cryptoSuite);
        index.open(txn, shouldCreate);
        if(!index.isTrusted())
        {
          logger.info(NOTE_INDEX_ADD_REQUIRES_REBUILD, index.getName());
        }
        attrIndexMap.put(indexCfg.getAttribute(), index);
        attrCryptoMap.put(indexCfg.getAttribute(), cryptoSuite);
      }

      for (String idx : config.listBackendVLVIndexes())
      {
        BackendVLVIndexCfg vlvIndexCfg = config.getBackendVLVIndex(idx);

        VLVIndex vlvIndex = new VLVIndex(vlvIndexCfg, state, storage, this, txn);
        vlvIndex.open(txn, shouldCreate);
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
  ID2ChildrenCount getID2ChildrenCount()
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
   * Look for a VLV index for the given index name.
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
    try (Cursor<ByteString, ByteString> cursor = txn.openCursor(id2entry.getName()))
    {
      // Position a cursor on the last data item, and the key should give the highest ID.
      if (cursor.positionToLastKey())
      {
        return new EntryID(cursor.getKey());
      }
      return new EntryID(0);
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
            addServerSideSortControl(searchOperation, NO_SUCH_ATTRIBUTE);
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
              addPagedResultsControl(searchOperation, pageRequest, null);
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
            searchBaseObject(txn, searchOperation, pageRequest);
            return null;
          }

          // Check whether the client requested debug information about the
          // contribution of the indexes to the search.
          StringBuilder debugBuffer = null;
          if (searchOperation.getAttributes().contains(ATTR_DEBUG_SEARCH_INDEX))
          {
            debugBuffer = new StringBuilder();
          }

          EntryIDSet candidateEntryIDs = null;
          boolean candidatesAreInScope = false;
          if (sortRequest != null)
          {
            for (VLVIndex vlvIndex : vlvIndexMap.values())
            {
              try
              {
                candidateEntryIDs = vlvIndex.evaluate(txn, searchOperation, sortRequest, vlvRequest, debugBuffer);
                if (candidateEntryIDs != null)
                {
                  addServerSideSortControl(searchOperation, SUCCESS);
                  candidatesAreInScope = true;
                  break;
                }
              }
              catch (DirectoryException de)
              {
                serverSideSortControlError(searchOperation, sortRequest, de);
              }
            }
          }

          // Combining server-side sort with paged result controls
          // requires us to use an entryIDSet where the entryIDs are ordered
          // so further paging can restart where it previously stopped
          long[] reorderedCandidateEntryIDs;
          if (candidateEntryIDs == null)
          {
            if (processSearchWithVirtualAttributeRule(searchOperation, true))
            {
              return null;
            }

            // Create an index filter to get the search result candidate entries
            IndexFilter indexFilter = new IndexFilter(
                EntryContainer.this, txn, searchOperation, debugBuffer, rootContainer.getMonitorProvider());

            // Evaluate the filter against the attribute indexes.
            candidateEntryIDs = indexFilter.evaluate();
            if (!isBelowFilterThreshold(candidateEntryIDs))
            {
              final int idSetLimit = getEntryIDSetLimit(searchOperation);
              final EntryIDSet scopeSet = getIDSetFromScope(txn, aBaseDN, searchScope, idSetLimit);
              candidateEntryIDs.retainAll(scopeSet);
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
              // If the sort key is not present, the sorting will generate the
              // default ordering. VLV search request goes through as if
              // this sort key was not found in the user entry.
              try
              {
                List<SortKey> sortKeys = sortRequest.getSortKeys();
                reorderedCandidateEntryIDs = sort(txn, candidateEntryIDs, searchOperation, sortKeys, vlvRequest);
              }
              catch (DirectoryException de)
              {
                reorderedCandidateEntryIDs = candidateEntryIDs.toLongArray();
                serverSideSortControlError(searchOperation, sortRequest, de);
              }
              try
              {
                if (sortRequest.containsSortKeys())
                {
                  addServerSideSortControl(searchOperation, SUCCESS);
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
                  addServerSideSortControl(searchOperation, NO_SUCH_ATTRIBUTE);
                }
              }
              catch (DirectoryException de)
              {
                serverSideSortControlError(searchOperation, sortRequest, de);
              }
            }
            else
            {
              reorderedCandidateEntryIDs = candidateEntryIDs.toLongArray();
            }
          }
          else
          {
            reorderedCandidateEntryIDs = candidateEntryIDs.toLongArray();
          }

          // If requested, construct and return a fictitious entry containing
          // debug information, and no other entries.
          if (debugBuffer != null)
          {
            debugBuffer.append(" final=");
            candidateEntryIDs.toString(debugBuffer);

            Entry debugEntry = buildDebugSearchIndexEntry(debugBuffer);
            searchOperation.returnEntry(debugEntry, null);
            return null;
          }

          if (reorderedCandidateEntryIDs != null)
          {
            rootContainer.getMonitorProvider().incrementIndexedSearchCount();
            searchIndexed(txn, reorderedCandidateEntryIDs, candidatesAreInScope, searchOperation, pageRequest);
          }
          else
          {
            rootContainer.getMonitorProvider().incrementUnindexedSearchCount();

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
              // FIXME OPENDJ-2628: Add support for sorting unindexed searches using indexes like DSEE currently does
              addServerSideSortControl(searchOperation, UNWILLING_TO_PERFORM);
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

        private int getEntryIDSetLimit(final SearchOperation searchOperation)
        {
          final int lookThroughLimit = searchOperation.getClientConnection().getLookthroughLimit();
          final int indexLimit = config.getIndexEntryLimit() == 0 ? CURSOR_ENTRY_LIMIT : config.getIndexEntryLimit();
          return lookThroughLimit > 0 ? Math.min(indexLimit, lookThroughLimit) : indexLimit;
        }

        private void searchBaseObject(ReadableTransaction txn, SearchOperation searchOperation,
            PagedResultsControl pageRequest) throws DirectoryException
        {
          final Entry baseEntry = fetchBaseEntry(txn, searchOperation.getBaseDN(), searchOperation.getScope());
          if (!isManageDsaITOperation(searchOperation))
          {
            dn2uri.checkTargetForReferral(baseEntry, searchOperation.getScope());
          }

          if (searchOperation.getFilter().matchesEntry(baseEntry))
          {
            searchOperation.returnEntry(baseEntry, null);
          }

          // Indicate no more pages.
          addPagedResultsControl(searchOperation, pageRequest, null);
        }

        private void serverSideSortControlError(final SearchOperation searchOperation,
            ServerSideSortRequestControl sortRequest, DirectoryException de) throws DirectoryException
        {
          addServerSideSortControl(searchOperation, de.getResultCode().intValue());
          if (sortRequest.isCritical())
          {
            throw de;
          }
        }

        private void addServerSideSortControl(SearchOperation searchOp, int resultCode)
        {
          searchOp.addResponseControl(new ServerSideSortResponseControl(resultCode, null));
        }

        private EntryIDSet getIDSetFromScope(final ReadableTransaction txn, DN aBaseDN, SearchScope searchScope,
            int idSetLimit) throws DirectoryException
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
                scopeSet = newIDSetFromCursor(scopeCursor, false, idSetLimit);
              }
              break;
            case SUBORDINATES:
            case WHOLE_SUBTREE:
              try (final SequentialCursor<?, EntryID> scopeCursor = dn2id.openSubordinatesCursor(txn, aBaseDN))
              {
                scopeSet = newIDSetFromCursor(scopeCursor, searchScope.equals(SearchScope.WHOLE_SUBTREE), idSetLimit);
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

  private static EntryIDSet newIDSetFromCursor(SequentialCursor<?, EntryID> cursor, boolean includeCurrent,
      int idSetLimit)
  {
    long entryIDs[] = new long[idSetLimit];
    int offset = 0;
    if (includeCurrent)
    {
      entryIDs[offset++] = cursor.getValue().longValue();
    }

    while(offset < idSetLimit && cursor.next())
    {
      entryIDs[offset++] = cursor.getValue().longValue();
    }

    if (offset == idSetLimit && cursor.next())
    {
      return EntryIDSet.newUndefinedSet();
    }
    else if (offset != idSetLimit)
    {
      entryIDs = Arrays.copyOf(entryIDs, offset);
    }
    Arrays.sort(entryIDs);

    return EntryIDSet.newDefinedSet(entryIDs);
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

  private static <E1 extends Exception, E2 extends Exception> void throwIfPossible(final Throwable cause,
      Class<E1> clazz1, Class<E2> clazz2) throws E1, E2
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

  private static boolean processSearchWithVirtualAttributeRule(final SearchOperation searchOperation,
      boolean isPreIndexed)
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

  private static Entry buildDebugSearchIndexEntry(StringBuilder debugBuffer) throws DirectoryException
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

      /* The base entry is only included for whole subtree search. */
      if (searchScope == SearchScope.WHOLE_SUBTREE
          && searchOperation.getFilter().matchesEntry(baseEntry))
      {
        searchOperation.returnEntry(baseEntry, null);
      }

      if (!manageDsaIT && !dn2uri.returnSearchReferences(txn, searchOperation))
      {
        // Indicate no more pages.
        addPagedResultsControl(searchOperation, pageRequest, null);
      }
    }

    /*
     * We will iterate forwards through a range of the dn2id keys to
     * find subordinates of the target entry from the top of the tree
     * downwards. For example, any subordinates of dn "dc=example,dc=com" appear
     * in dn2id with a dn ending in ",dc=example,dc=com". The dn
     * "cn=joe,ou=people,dc=example,dc=com" will appear after the dn
     * "ou=people,dc=example,dc=com".
     */
    ByteString baseDNKey = dnToDNKey(aBaseDN, this.baseDN.size());
    ByteStringBuilder beforeFirstChild = beforeFirstChildOf(baseDNKey);
    ByteStringBuilder afterLastChild = afterLastChildOf(baseDNKey);

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
      begin = beforeFirstChild;
    }

    int lookthroughCount = 0;
    int lookthroughLimit = searchOperation.getClientConnection().getLookthroughLimit();

    try (final Cursor<ByteString, ByteString> cursor = txn.openCursor(dn2id.getName()))
    {
      // Initialize the cursor very close to the starting value.
      boolean success = cursor.positionToKeyOrNext(begin);

      // Step forward until we pass the ending value.
      while (success && cursor.getKey().compareTo(afterLastChild) < 0)
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
              if (isPageFull(searchOperation, pageRequest))
              {
                // Set the cookie to remember where we were.
                addPagedResultsControl(searchOperation, pageRequest, cursor.getKey());
                return;
              }

              if (!searchOperation.returnEntry(entry, null))
              {
                // We have been told to discontinue processing of the search.
                // This could be due to size limit exceeded or operation cancelled
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
    catch (StorageRuntimeException e)
    {
      logger.traceException(e);
    }

    // Indicate no more pages.
    addPagedResultsControl(searchOperation, pageRequest, null);
  }

  private boolean isPageFull(SearchOperation searchOperation, PagedResultsControl pageRequest)
  {
    return pageRequest != null && searchOperation.getEntriesSent() == pageRequest.getSize();
  }

  private void addPagedResultsControl(SearchOperation searchOp, PagedResultsControl pageRequest, ByteString cookie)
  {
    if (pageRequest != null)
    {
      searchOp.addResponseControl(new PagedResultsControl(pageRequest.isCritical(), 0, cookie));
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
  private Entry getEntry(ReadableTransaction txn, EntryID entryID) throws DirectoryException
  {
    // Try the entry cache first.
    final EntryCache<?> entryCache = getEntryCache();
    final Entry cacheEntry = entryCache.getEntry(backendID, entryID.longValue());
    if (cacheEntry != null)
    {
      return cacheEntry;
    }

    final Entry entry = id2entry.get(txn, entryID);
    if (entry != null)
    {
      // Put the entry in the cache making sure not to overwrite a newer copy
      // that may have been inserted since the time we read the cache.
      entryCache.putEntryIfAbsent(entry, backendID, entryID.longValue());
    }
    return entry;
  }

  /**
   * We were able to obtain a set of candidate entry IDs for the search from the indexes.
   * <p>
   * Here we are relying on ID order to ensure children are returned after their parents.
   * <ul>
   * <li>Iterate through the candidate IDs
   * <li>fetch entry by ID from cache or id2entry
   * <li>put the entry in the cache if not present
   * <li>discard entries that are not in scope
   * <li>return entry if it matches the filter
   * </ul>
   *
   * @param entryIDReorderedSet
   *          The candidate entry IDs.
   * @param candidatesAreInScope
   *          true if it is certain that every candidate entry is in the search scope.
   * @param searchOperation
   *          The search operation.
   * @param pageRequest
   *          A Paged Results control, or null if none.
   * @throws DirectoryException
   *           If an error prevented the search from being processed.
   */
  private void searchIndexed(ReadableTransaction txn, long[] entryIDReorderedSet, boolean candidatesAreInScope,
      SearchOperation searchOperation, PagedResultsControl pageRequest) throws DirectoryException,
      CanceledOperationException
  {
    SearchScope searchScope = searchOperation.getScope();
    DN aBaseDN = searchOperation.getBaseDN();
    boolean manageDsaIT = isManageDsaITOperation(searchOperation);
    boolean continueSearch = true;

    // Set the starting value.
    Long beginEntryID = null;
    if (pageRequest != null && pageRequest.getCookie().length() != 0)
    {
      // The cookie contains the ID of the next entry to be returned.
      try
      {
        beginEntryID = pageRequest.getCookie().toLong();
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
    if (lookthroughLimit > 0 && entryIDReorderedSet.length > lookthroughLimit)
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
      for (int i = findStartIndex(beginEntryID, entryIDReorderedSet); i < entryIDReorderedSet.length; i++)
      {
        EntryID entryID = new EntryID(entryIDReorderedSet[i]);
        Entry entry;
        try
        {
          entry = getEntry(txn, entryID);
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
            if (isPageFull(searchOperation, pageRequest))
            {
              // Set the cookie to remember where we were.
              addPagedResultsControl(searchOperation, pageRequest, entryID.toByteString());
              return;
            }

            if (!searchOperation.returnEntry(entry, null))
            {
              // We have been told to discontinue processing of the search.
              // This could be due to size limit exceeded or operation cancelled
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

    // Indicate no more pages.
    addPagedResultsControl(searchOperation, pageRequest, null);
  }

  private int findStartIndex(Long beginEntryID, long[] entryIDReorderedSet)
  {
    if (beginEntryID == null)
    {
      return 0;
    }
    final long begin = beginEntryID.longValue();
    for (int i = 0; i < entryIDReorderedSet.length; i++)
    {
      if (entryIDReorderedSet[i] == begin)
      {
        return i;
      }
    }
    return 0;
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
          && entryDN.isSubordinateOrEqualTo(aBaseDN))
      {
        return true;
      }
    }
    else if (searchScope == SearchScope.WHOLE_SUBTREE)
    {
      if (entryDN.isSubordinateOrEqualTo(aBaseDN))
      {
        return true;
      }
    }
    else if (searchScope == SearchScope.SUBORDINATES
        && entryDN.size() > aBaseDN.size()
        && entryDN.isSubordinateOrEqualTo(aBaseDN))
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
    final DN parentDN = getParentWithinBase(entry.getName());
    final EntryID entryID = rootContainer.getNextEntryID();

    // Insert into the indexes, in index configuration order.
    final IndexBuffer indexBuffer = new IndexBuffer();
    insertEntryIntoIndexes(indexBuffer, entry, entryID);

    final ByteString encodedEntry = id2entry.encode(entry);

    try
    {
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          // No need to call indexBuffer.reset() since IndexBuffer content will be the same for each retry attempt.
          try
          {
            // Check whether the entry already exists.
            if (dn2id.get(txn, entry.getName()) != null)
            {
              throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS,
                  ERR_ADD_ENTRY_ALREADY_EXISTS.get(entry.getName()));
            }
            // Check that the parent entry exists.
            EntryID parentID = null;
            if (parentDN != null)
            {
              // Check for referral entries above the target.
              dn2uri.targetEntryReferrals(txn, entry.getName(), null);

              parentID = dn2id.get(txn, parentDN);
              if (parentID == null)
              {
                throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
                                             ERR_ADD_NO_SUCH_OBJECT.get(entry.getName()),
                                             getMatchedDN(txn, parentDN),
                                             null);
              }
            }

            // Ensure same access ordering as deleteEntry.
            dn2id.put(txn, entry.getName(), entryID);
            id2childrenCount.updateCount(txn, parentID, 1);
            id2entry.put(txn, entryID, encodedEntry);
            dn2uri.addEntry(txn, entry);
            id2childrenCount.updateTotalCount(txn, 1);
            indexBuffer.flush(txn);
            // One last check before committing
            addOperation.checkIfCanceled(true);
          }
          catch (StorageRuntimeException | DirectoryException | CanceledOperationException e)
          {
            throw e;
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
      writeTrustState(indexBuffer);
      throwAllowedExceptionTypes(e, DirectoryException.class, CanceledOperationException.class);
    }

    final EntryCache<?> entryCache = DirectoryServer.getEntryCache();
    if (entryCache != null)
    {
      entryCache.putEntry(entry, backendID, entryID.longValue());
    }
  }

  private void writeTrustState(final IndexBuffer indexBuffer)
  {
    // Transaction modifying the index has been rolled back.
    // Ensure that the index trusted state is persisted.
    try
    {
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          indexBuffer.writeTrustState(txn);
        }
      });
    }
    catch (Exception e)
    {
      // Cannot throw because this method is used in a catch block and we do not want to hide the real exception.
      logger.traceException(e);
    }
  }

  void importEntry(WriteableTransaction txn, EntryID entryID, Entry entry) throws DirectoryException,
      StorageRuntimeException
  {
    final IndexBuffer indexBuffer = IndexBuffer.newImportIndexBuffer(txn, entryID);
    insertEntryIntoIndexes(indexBuffer, entry, entryID);
    dn2id.put(txn, entry.getName(), entryID);
    id2entry.put(txn, entryID, id2entry.encode(entry));
    dn2uri.addEntry(txn, entry);
    indexBuffer.flush(txn);
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
    final IndexBuffer indexBuffer = new IndexBuffer();
    try
    {
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          indexBuffer.reset();
          try
          {
            // Check for referral entries above the target entry.
            dn2uri.targetEntryReferrals(txn, entryDN, null);

            // We'll need the parent ID when we update the id2childrenCount. Fetch it now so that accesses to dn2id
            // are ordered.
            final DN parentDN = getParentWithinBase(entryDN);
            EntryID parentID = null;
            if (parentDN != null)
            {
              parentID = dn2id.get(txn, parentDN);
              if (parentID == null)
              {
                throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
                                             ERR_DELETE_NO_SUCH_OBJECT.get(entryDN),
                                             getMatchedDN(txn, parentDN),
                                             null);
              }
            }

            // Delete the subordinate entries in dn2id if requested.
            final boolean isSubtreeDelete = deleteOperation.getRequestControl(SubtreeDeleteControl.DECODER) != null;

            /* draft-armijo-ldap-treedelete, 4.1 Tree Delete Semantics: The server MUST NOT chase referrals stored in
             * the tree. If information about referrals is stored in this section of the tree, this pointer will be
             * deleted.
             */
            final boolean isManageDsaIT = isSubtreeDelete || isManageDsaITOperation(deleteOperation);

            /* Ensure that all index updates are done in the correct order to avoid deadlocks. First iterate over
             * dn2id collecting all the IDs of the entries to be deleted. Then update dn2uri, id2entry,
             * id2childrenCount, and finally the attribute indexes.
             */
            final List<Long> entriesToBeDeleted = new ArrayList<>();
            try (final SequentialCursor<Void, EntryID> cursor = dn2id.openSubordinatesCursor(txn, entryDN))
            {
              // Delete the target entry in dn2id.
              if (!cursor.isDefined())
              {
                throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
                                             ERR_DELETE_NO_SUCH_OBJECT.get(entryDN),
                                             getMatchedDN(txn, entryDN),
                                             null);
              }
              entriesToBeDeleted.add(cursor.getValue().longValue());
              cursor.delete();

              // Now delete the subordinate entries in dn2id.
              while (cursor.next())
              {
                if (!isSubtreeDelete)
                {
                  throw new DirectoryException(ResultCode.NOT_ALLOWED_ON_NONLEAF,
                                               ERR_DELETE_NOT_ALLOWED_ON_NONLEAF.get(entryDN));
                }
                entriesToBeDeleted.add(cursor.getValue().longValue());
                cursor.delete();
                deleteOperation.checkIfCanceled(false);
              }
            }
            // The target entry will have the lowest entryID so it will remain the first element.
            Collections.sort(entriesToBeDeleted);

            // Now update id2entry, dn2uri, and id2childrenCount in key order.
            id2childrenCount.updateCount(txn, parentID, -1);
            final EntryCache<?> entryCache = DirectoryServer.getEntryCache();
            boolean isBaseEntry = true;
            try (final Cursor<EntryID, Entry> cursor = id2entry.openCursor(txn))
            {
              for (Long entryIDLong : entriesToBeDeleted)
              {
                final EntryID entryID = new EntryID(entryIDLong);
                if (!cursor.positionToKey(entryID.toByteString()))
                {
                  throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                               ERR_MISSING_ID2ENTRY_RECORD.get(entryID));
                }
                final Entry entry = cursor.getValue();
                if (isBaseEntry && !isManageDsaIT)
                {
                  dn2uri.checkTargetForReferral(entry, null);
                }
                cursor.delete();
                dn2uri.deleteEntry(txn, entry);
                id2childrenCount.removeCount(txn, entryID);
                removeEntryFromIndexes(indexBuffer, entry, entryID);
                if (!isBaseEntry)
                {
                  invokeSubordinateDeletePlugins(entry);
                }
                if (entryCache != null)
                {
                  entryCache.removeEntry(entry.getName());
                }
                isBaseEntry = false;
                deleteOperation.checkIfCanceled(false);
              }
            }
            id2childrenCount.updateTotalCount(txn, -entriesToBeDeleted.size());
            indexBuffer.flush(txn);
            deleteOperation.checkIfCanceled(true);
            if (isSubtreeDelete)
            {
              deleteOperation.addAdditionalLogItem(unquotedKeyValue(getClass(), "deletedEntries",
                                                                    entriesToBeDeleted.size()));
            }
          }
          catch (StorageRuntimeException | DirectoryException | CanceledOperationException e)
          {
            throw e;
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

        private void invokeSubordinateDeletePlugins(final Entry entry) throws DirectoryException
        {
          if (!deleteOperation.isSynchronizationOperation())
          {
            SubordinateDelete pluginResult =
                    getPluginConfigManager().invokeSubordinateDeletePlugins(deleteOperation, entry);
            if (!pluginResult.continueProcessing())
            {
              throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                           ERR_DELETE_ABORTED_BY_SUBORDINATE_PLUGIN.get(entry.getName()));
            }
          }
        }
      });
    }
    catch (Exception e)
    {
      writeTrustState(indexBuffer);
      throwAllowedExceptionTypes(e, DirectoryException.class, CanceledOperationException.class);
    }
  }

  /**
   * Indicates whether an entry with the specified DN exists.
   *
   * @param  entryDN  The DN of the entry for which to determine existence.
   *
   * @return  <CODE>true</CODE> if the specified entry exists,
   *          or <CODE>false</CODE> if it does not.
   */
  private boolean entryExists(ReadableTransaction txn, final DN entryDN)
  {
    // Try the entry cache first.
    EntryCache<?> entryCache = DirectoryServer.getEntryCache();
    return (entryCache != null && entryCache.containsEntry(entryDN))
            || dn2id.get(txn, entryDN) != null;
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
          Entry entry = getEntry0(txn, entryDN);
          if (entry == null)
          {
            // The entryDN does not exist. Check for referral entries above the target entry.
            dn2uri.targetEntryReferrals(txn, entryDN, null);
          }
          return entry;
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

    final EntryID entryID = dn2id.get(txn, entryDN);
    if (entryID == null)
    {
      return null;
    }

    final Entry entry = id2entry.get(txn, entryID);
    if (entry != null && entryCache != null)
    {
      /*
       * Put the entry in the cache making sure not to overwrite a newer copy that may have been
       * inserted since the time we read the cache.
       */
      entryCache.putEntryIfAbsent(entry, backendID, entryID.longValue());
    }
    return entry;
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
    final IndexBuffer indexBuffer = new IndexBuffer();
    final ByteString encodedNewEntry = id2entry.encode(newEntry);
    try
    {
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          indexBuffer.reset();
          try
          {
            EntryID entryID = dn2id.get(txn, newEntry.getName());
            if (entryID == null)
            {
              throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
                                           ERR_MODIFY_NO_SUCH_OBJECT.get(newEntry.getName()),
                                           getMatchedDN(txn, newEntry.getName()),
                                           null);
            }

            if (!isManageDsaITOperation(modifyOperation))
            {
              // Check if the entry is a referral entry.
              dn2uri.checkTargetForReferral(oldEntry, null);
            }

            // Ensure same ordering as deleteEntry: id2entry, dn2uri, then indexes.
            id2entry.put(txn, entryID, encodedNewEntry);

            // Update the referral tree and indexes
            dn2uri.modifyEntry(txn, oldEntry, newEntry, modifyOperation.getModifications());
            indexModifications(indexBuffer, oldEntry, newEntry, entryID, modifyOperation.getModifications());

            indexBuffer.flush(txn);

            // One last check before committing
            modifyOperation.checkIfCanceled(true);

            // Update the entry cache.
            EntryCache<?> entryCache = DirectoryServer.getEntryCache();
            if (entryCache != null)
            {
              entryCache.putEntry(newEntry, backendID, entryID.longValue());
            }
          }
          catch (StorageRuntimeException | DirectoryException | CanceledOperationException e)
          {
            throw e;
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
      writeTrustState(indexBuffer);
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
   * @param oldTargetDN             The current DN of the entry to be renamed.
   * @param newTargetEntry          The new content to use for the entry.
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
  void renameEntry(final DN oldTargetDN, final Entry newTargetEntry, final ModifyDNOperation modifyDNOperation)
      throws StorageRuntimeException, DirectoryException, CanceledOperationException
  {
    final IndexBuffer indexBuffer = new IndexBuffer();
    try
    {
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          indexBuffer.reset();
          try
          {
            // Validate the request.
            final DN newTargetDN = newTargetEntry.getName();
            final DN oldSuperiorDN = getParentWithinBase(oldTargetDN);
            final DN newSuperiorDN = getParentWithinBase(newTargetDN);

            final EntryID oldSuperiorID = oldSuperiorDN != null ? dn2id.get(txn, oldSuperiorDN) : null;
            final EntryID oldTargetID = dn2id.get(txn, oldTargetDN);
            if ((oldSuperiorDN != null && oldSuperiorID == null) || oldTargetID == null)
            {
              // Check for referral entries above the target entry.
              dn2uri.targetEntryReferrals(txn, oldTargetDN, null);
              throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
                                           ERR_MODIFYDN_NO_SUCH_OBJECT.get(oldTargetDN),
                                           getMatchedDN(txn, oldTargetDN),
                                           null);
            }

            final EntryID newSuperiorID = newSuperiorDN != null ? dn2id.get(txn, newSuperiorDN) : null;
            if (newSuperiorDN != null && newSuperiorID == null)
            {
              throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
                                           ERR_NEW_SUPERIOR_NO_SUCH_OBJECT.get(newSuperiorDN),
                                           getMatchedDN(txn, newSuperiorDN),
                                           null);
            }

            // Check that an entry with the new name does not already exist, but take care to handle the case where
            // the user is renaming the entry with an equivalent name, e.g. "cn=matt" to "cn=Matt".
            if (!oldTargetDN.equals(newTargetDN) && dn2id.get(txn, newTargetDN) != null)
            {
              throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS,
                                           ERR_MODIFYDN_ALREADY_EXISTS.get(newTargetDN));
            }

            /* We want to preserve the invariant that the ID of an entry is greater than its parent, since search
             * results are returned in ID order. Note: if the superior has changed then oldSuperiorDN and
             * newSuperiorDN will be non-null.
             */
            final boolean superiorHasChanged = !Objects.equals(oldSuperiorDN, newSuperiorDN);
            final boolean renumberEntryIDs = superiorHasChanged && newSuperiorID.compareTo(oldSuperiorID) > 0;

            /* Ensure that all index updates are done in the correct order to avoid deadlocks. First iterate over
             * dn2id collecting all the IDs of the entries to be renamed. Then update dn2uri, id2entry,
             * id2childrenCount, and finally the attribute indexes.
             */
            final List<Pair<Long, Long>> renamedEntryIDs = dn2id.renameSubtree(txn,
                                                                               oldTargetDN,
                                                                               newTargetDN,
                                                                               rootContainer,
                                                                               renumberEntryIDs,
                                                                               modifyDNOperation);

            // The target entry will have the lowest entryID so it will remain the first element.
            Collections.sort(renamedEntryIDs, Pair.<Long, Long>getPairComparator());

            // Now update id2entry, dn2uri, and id2childrenCount in key order.
            if (superiorHasChanged)
            {
              id2childrenCount.updateCount(txn, oldSuperiorID, -1);
              id2childrenCount.updateCount(txn, newSuperiorID, 1);
            }
            boolean isBaseEntry = true;
            try (final Cursor<EntryID, Entry> cursor = id2entry.openCursor(txn))
            {
              for (Pair<Long, Long> renamedEntryID : renamedEntryIDs)
              {
                renameSingleEntry(txn, renamedEntryID, cursor, indexBuffer, newTargetDN, renumberEntryIDs, isBaseEntry);
                isBaseEntry = false;
                modifyDNOperation.checkIfCanceled(false);
              }

            }
            indexBuffer.flush(txn);
            modifyDNOperation.checkIfCanceled(true);
          }
          catch (StorageRuntimeException | DirectoryException | CanceledOperationException e)
          {
            throw e;
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

        private void renameSingleEntry(
                final WriteableTransaction txn,
                final Pair<Long, Long> renamedEntryID,
                final Cursor<EntryID, Entry> cursor,
                final IndexBuffer indexBuffer,
                final DN newTargetDN,
                final boolean renumberEntryIDs,
                final boolean isBaseEntry) throws DirectoryException
        {
          final EntryID oldEntryID = new EntryID(renamedEntryID.getFirst());
          final EntryID newEntryID = new EntryID(renamedEntryID.getSecond());
          if (!cursor.positionToKey(oldEntryID.toByteString()))
          {
            throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                         ERR_MISSING_ID2ENTRY_RECORD.get(oldEntryID));
          }

          final Entry oldEntry = cursor.getValue();
          final Entry newEntry;
          final List<Modification> modifications;
          if (isBaseEntry)
          {
            if (!isManageDsaITOperation(modifyDNOperation))
            {
              dn2uri.checkTargetForReferral(oldEntry, null);
            }
            newEntry = newTargetEntry;
            modifications = modifyDNOperation.getModifications();
          }
          else
          {
            final DN newDN = oldEntry.getName().rename(oldTargetDN, newTargetDN);
            newEntry = oldEntry.duplicate(false);
            newEntry.setDN(newDN);
            modifications = invokeSubordinateModifyDNPlugins(oldEntry, newEntry);
          }

          if (renumberEntryIDs)
          {
            cursor.delete();
          }
          id2entry.put(txn, newEntryID, newEntry);
          dn2uri.deleteEntry(txn, oldEntry);
          dn2uri.addEntry(txn, newEntry);
          if (renumberEntryIDs)
          {
            // In-order: new entryID is guaranteed to be greater than old entryID.
            final long count = id2childrenCount.removeCount(txn, oldEntryID);
            id2childrenCount.updateCount(txn, newEntryID, count);
          }

          if (renumberEntryIDs || modifications == null)
          {
            // Slow path: the entry has been renumbered so we need to fully re-index.
            removeEntryFromIndexes(indexBuffer, oldEntry, oldEntryID);
            insertEntryIntoIndexes(indexBuffer, newEntry, newEntryID);
          }
          else if (!modifications.isEmpty())
          {
            // Fast-path: the entryID has not changed so we only need to re-index the mods.
            indexModifications(indexBuffer, oldEntry, newEntry, oldEntryID, modifications);
          }

          final EntryCache<?> entryCache = DirectoryServer.getEntryCache();
          if (entryCache != null)
          {
            entryCache.removeEntry(oldEntry.getName());
          }
        }

        private List<Modification> invokeSubordinateModifyDNPlugins(
                final Entry oldEntry, final Entry newEntry) throws DirectoryException
        {
          final List<Modification> modifications = Collections.unmodifiableList(new ArrayList<Modification>(0));

          // Create a new entry that is a copy of the old entry but with the new DN.
          // Also invoke any subordinate modify DN plugins on the entry.
          // FIXME -- At the present time, we don't support subordinate modify DN
          //          plugins that make changes to subordinate entries and therefore
          //          provide an unmodifiable list for the modifications element.
          // FIXME -- This will need to be updated appropriately if we decided that
          //          these plugins should be invoked for synchronization operations.
          if (!modifyDNOperation.isSynchronizationOperation())
          {
            SubordinateModifyDN pluginResult = getPluginConfigManager().invokeSubordinateModifyDNPlugins(
                    modifyDNOperation, oldEntry, newEntry, modifications);

            if (!pluginResult.continueProcessing())
            {
              throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                           ERR_MODIFYDN_ABORTED_BY_SUBORDINATE_PLUGIN.get(oldEntry.getName(),
                                                                                          newEntry.getName()));
            }

            if (!modifications.isEmpty())
            {
              LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
              if (!newEntry.conformsToSchema(null, false, false, false, invalidReason))
              {
                throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                             ERR_MODIFYDN_ABORTED_BY_SUBORDINATE_SCHEMA_ERROR.get(oldEntry.getName(),
                                                                                                  newEntry.getName(),
                                                                                                  invalidReason));
              }
            }
          }
          return modifications;
        }
      });
    }
    catch (Exception e)
    {
      writeTrustState(indexBuffer);
      throwAllowedExceptionTypes(e, DirectoryException.class, CanceledOperationException.class);
    }
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
  private void insertEntryIntoIndexes(IndexBuffer buffer, Entry entry, EntryID entryID)
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
  private void removeEntryFromIndexes(IndexBuffer buffer, Entry entry, EntryID entryID)
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
      if (isAttributeModified(index.getAttributeType(), mods))
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
          return getNumberOfEntriesInBaseDN0(txn);
        }
      });
    }
    catch (Exception e)
    {
      throw new StorageRuntimeException(e);
    }
  }

  long getNumberOfEntriesInBaseDN0(ReadableTransaction txn)
  {
    return id2childrenCount.getTotalCount(txn);
  }

  /**
   * Determine whether the provided operation has the ManageDsaIT request control.
   * @param operation The operation for which the determination is to be made.
   * @return true if the operation has the ManageDsaIT request control, or false if not.
   */
  private static boolean isManageDsaITOperation(Operation operation)
  {
    for (Control control : operation.getRequestControls())
    {
      if (ServerConstants.OID_MANAGE_DSAIT_CONTROL.equals(control.getOID()))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Delete this entry container from disk. The entry container should be
   * closed before calling this method.
   *
   * @param txn a non null transaction
   * @throws StorageRuntimeException If an error occurs while removing the entry container.
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
      // The state tree cannot be removed individually.
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

  @Override
  public boolean isConfigurationChangeAcceptable(PluggableBackendCfg cfg, List<LocalizableMessage> unacceptableReasons)
  {
    if (cfg.isConfidentialityEnabled())
    {
      final String cipherTransformation = cfg.getCipherTransformation();
      final int keyLength = cfg.getCipherKeyLength();

      try
      {
        serverContext.getCryptoManager().ensureCipherKeyIsAvailable(cipherTransformation, keyLength);
      }
      catch (Exception e)
      {
        unacceptableReasons.add(ERR_BACKEND_FAULTY_CRYPTO_TRANSFORMATION.get(cipherTransformation, keyLength, e));
        return false;
      }
    }
    else
    {
      StringBuilder builder = new StringBuilder();
      for (AttributeIndex attributeIndex : attrIndexMap.values())
      {
        if (attributeIndex.isConfidentialityEnabled())
        {
          if (builder.length() > 0)
          {
            builder.append(", ");
          }
          builder.append(attributeIndex.getAttributeType().getNameOrOID());
        }
      }
      if (builder.length() > 0)
      {
        unacceptableReasons.add(ERR_BACKEND_CANNOT_CHANGE_CONFIDENTIALITY.get(getBaseDN(), builder.toString()));
        return false;
      }
    }
    return true;
  }

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
          id2entry.setDataConfig(newDataConfig(cfg));
          EntryContainer.this.config = cfg;
        }
      });
      for (CryptoSuite indexCrypto : attrCryptoMap.values())
      {
        indexCrypto.newParameters(cfg.getCipherTransformation(), cfg.getCipherKeyLength(), indexCrypto.isEncrypted());
      }
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
          for (Tree tree : listTrees())
          {
            tree.delete(txn);
          }
        }
      });
    }
    catch (Exception e)
    {
      throw new StorageRuntimeException(e);
    }
  }

  List<Tree> listTrees()
  {
    final List<Tree> allTrees = new ArrayList<>();
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
   * Finds an existing entry whose DN is the closest ancestor of a given baseDN.
   *
   * @param targetDN  the DN for which we are searching a matched DN.
   * @return the DN of the closest ancestor of the baseDN.
   * @throws DirectoryException If an error prevented the check of an
   * existing entry from being performed.
   */
  private DN getMatchedDN(ReadableTransaction txn, DN targetDN) throws DirectoryException
  {
    DN parentDN = DirectoryServer.getParentDNInSuffix(targetDN);
    while (parentDN != null && parentDN.isSubordinateOrEqualTo(baseDN))
    {
      if (entryExists(txn, parentDN))
      {
        return parentDN;
      }
      parentDN = DirectoryServer.getParentDNInSuffix(parentDN);
    }
    return null;
  }

  boolean isConfidentialityEnabled()
  {
    return config.isConfidentialityEnabled();
  }

  /**
   * Fetch the base Entry of the EntryContainer.
   * @param searchBaseDN the DN for the base entry
   * @param searchScope the scope under which this is fetched.
   *                    Scope is used for referral processing.
   * @return the Entry matching the baseDN.
   * @throws DirectoryException if the baseDN doesn't exist.
   */
  private Entry fetchBaseEntry(ReadableTransaction txn, DN searchBaseDN, SearchScope searchScope)
      throws DirectoryException
  {
    Entry baseEntry = getEntry0(txn, searchBaseDN);
    if (baseEntry == null)
    {
      // Check for referral entries above the base entry.
      dn2uri.targetEntryReferrals(txn, searchBaseDN, searchScope);
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
          ERR_SEARCH_NO_SUCH_OBJECT.get(searchBaseDN), getMatchedDN(txn, searchBaseDN), null);
    }
    return baseEntry;
  }

  private long[] sort(ReadableTransaction txn, EntryIDSet entryIDSet, SearchOperation searchOperation,
      List<SortKey> sortKeys, VLVRequestControl vlvRequest) throws DirectoryException
  {
    if (!entryIDSet.isDefined())
    {
      return null;
    }

    final DN baseDN = searchOperation.getBaseDN();
    final SearchScope scope = searchOperation.getScope();
    final SearchFilter filter = searchOperation.getFilter();

    final TreeMap<ByteString, EntryID> sortMap = new TreeMap<>();
    for (EntryID id : entryIDSet)
    {
      try
      {
        Entry e = getEntry(txn, id);
        if (e.matchesBaseAndScope(baseDN, scope) && filter.matchesEntry(e))
        {
          sortMap.put(encodeVLVKey(sortKeys, e, id.longValue()), id);
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
      return toArray(sortMap.values());
    }

    if (vlvRequest.getTargetType() == VLVRequestControl.TYPE_TARGET_BYOFFSET)
    {
      return sortByOffset(searchOperation, vlvRequest, sortMap);
    }
    return sortByGreaterThanOrEqualAssertion(searchOperation, vlvRequest, sortKeys, sortMap);
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

  private static final long[] sortByGreaterThanOrEqualAssertion(SearchOperation searchOperation,
      VLVRequestControl vlvRequest, List<SortKey> sortKeys, final TreeMap<ByteString, EntryID> sortMap)
      throws DirectoryException
  {
    ByteString assertionValue = vlvRequest.getGreaterThanOrEqualAssertion();
    ByteSequence encodedTargetAssertion =
        encodeTargetAssertion(sortKeys, assertionValue, searchOperation, sortMap.size());

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

    final long[] result;
    if (targetFound)
    {
      final long[] array = new long[index - startIndex];
      System.arraycopy(idSet, startIndex, array, 0, array.length);
      result = array;
    }
    else
    {
      /*
       * No entry was found to be greater than or equal to the sort key, so the target offset will
       * be one greater than the content count.
       */
      targetIndex = sortMap.size() + 1;
      result = new long[0];
    }
    addVLVResponseControl(searchOperation, targetIndex, sortMap.size(), SUCCESS);
    return result;
  }

  private static final long[] sortByOffset(SearchOperation searchOperation, VLVRequestControl vlvRequest,
      TreeMap<ByteString, EntryID> sortMap) throws DirectoryException
  {
    int targetOffset = vlvRequest.getOffset();
    if (targetOffset < 0)
    {
      // The client specified a negative target offset. This should never be allowed.
      addVLVResponseControl(searchOperation, targetOffset, sortMap.size(), OFFSET_RANGE_ERROR);

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
      // We don't have enough entries in the set to meet the requested page size, so we'll need to shorten the array.
      sortedIDs = Arrays.copyOf(sortedIDs, arrayPos);
    }

    addVLVResponseControl(searchOperation, targetOffset, sortMap.size(), SUCCESS);
    return sortedIDs;
  }

  private static void addVLVResponseControl(SearchOperation searchOp, int targetPosition, int contentCount,
      int vlvResultCode)
  {
    searchOp.addResponseControl(new VLVResponseControl(targetPosition, contentCount, vlvResultCode));
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

  @Override
  public String toString() {
    return treePrefix;
  }

  static boolean isAttributeModified(AttributeType attrType, List<Modification> mods)
  {
    for (Modification mod : mods)
    {
      if (attrType.isSuperTypeOf(mod.getAttribute().getAttributeDescription().getAttributeType()))
      {
        return true;
      }
    }
    return false;
  }
}
