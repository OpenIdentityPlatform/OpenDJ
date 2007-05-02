/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import com.sleepycat.je.*;

import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.Backend;
import org.opends.server.api.EntryCache;
import org.opends.server.api.ClientConnection;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.controls.PagedResultsControl;
import org.opends.server.controls.ServerSideSortRequestControl;
import org.opends.server.controls.ServerSideSortResponseControl;
import org.opends.server.controls.VLVRequestControl;
import org.opends.server.types.*;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.ServerConstants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.locks.Lock;

import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.messages.JebMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugJEAccess;
import static org.opends.server.loggers.debug.DebugLogger.debugVerbose;
import static org.opends.server.util.ServerConstants.*;

/**
 * Storage container for LDAP entries.  Each base DN of a JE backend is given
 * its own entry container.  The entry container is the object that implements
 * the guts of the backend API methods for LDAP operations.
 */
public class EntryContainer
{

  /**
   * The name of the entry database.
   */
  public static final String ID2ENTRY_DATABASE_NAME = "id2entry";

  /**
   * The name of the DN database.
   */
  public static final String DN2ID_DATABASE_NAME = "dn2id";

  /**
   * The name of the children index database.
   */
  public static final String ID2CHILDREN_DATABASE_NAME = "id2children";

  /**
   * The name of the subtree index database.
   */
  public static final String ID2SUBTREE_DATABASE_NAME = "id2subtree";

  /**
   * The name of the referral database.
   */
  public static final String REFERRAL_DATABASE_NAME = "referral";

  /**
   * The attribute used to return a search index debug string to the client.
   */
  public static final String ATTR_DEBUG_SEARCH_INDEX = "debugsearchindex";

  /**
   * The backend to which this entry entryContainer belongs.
   */
  private Backend backend;

  /**
   * The root container in which this entryContainer belongs.
   */
  private RootContainer rootContainer;

  /**
   * The baseDN this entry container is responsible for.
   */
  private DN baseDN;

  /**
   * The backend configuration.
   */
  private Config config;

  /**
   * A list of JE database handles opened through this entryContainer.
   * They will be closed by the entryContainer.
   */
  private ArrayList<Database> databases;

  /**
   * The JE database environment.
   */
  private Environment env;

  /**
   * The DN database maps a normalized DN string to an entry ID (8 bytes).
   */
  private DN2ID dn2id;

  /**
   * The key comparator used for the DN database.
   */
  private Comparator<byte[]> dn2idComparator;

  /**
   * The entry database maps an entry ID (8 bytes) to a complete encoded entry.
   */
  private ID2Entry id2entry;

  /**
   * Compression and cryptographic options for the entry database.
   */
  private DataConfig entryDataConfig;

  /**
   * Index maps entry ID to an entry ID list containing its children.
   */
  private Index id2children;

  /**
   * Index maps entry ID to an entry ID list containing its subordinates.
   */
  private Index id2subtree;

  /**
   * The referral database maps a normalized DN string to labeled URIs.
   */
  private DN2URI dn2uri;

  /**
   * The set of attribute indexes.
   */
  private HashMap<AttributeType, AttributeIndex> attrIndexMap;

  /**
   * Create a new entry entryContainer object.
   *
   * @param baseDN  The baseDN this entry container will be responsible for
   *                storing on disk.
   * @param backend A reference to the JE backend that is creating this entry
   *                container. It is needed by the Directory Server entry cache
   *                methods.
   * @param config The configuration of the JE backend.
   * @param env The JE environment to create this entryContainer in.
   * @param rootContainer The root container this entry container is in.
   */
  public EntryContainer(DN baseDN, Backend backend, Config config,
                        Environment env, RootContainer rootContainer)
  {
    this.backend = backend;
    this.baseDN = baseDN;
    this.config = config;
    this.env = env;
    this.rootContainer = rootContainer;

    // Instantiate the list of database handles.
    databases = new ArrayList<Database>();

    // Instantiate indexes for id2children and id2subtree.
    id2children = new Index(this, ID2CHILDREN_DATABASE_NAME,
                            new ID2CIndexer(),
                            config.getBackendIndexEntryLimit(),
                            0);
    id2subtree = new Index(this, ID2SUBTREE_DATABASE_NAME,
                           new ID2SIndexer(),
                           config.getBackendIndexEntryLimit(),
                           0);

    // Instantiate the attribute indexes.
    attrIndexMap = new HashMap<AttributeType, AttributeIndex>();
    if (config != null && config.getIndexConfigMap() != null)
    {
      for (IndexConfig indexConfig : config.getIndexConfigMap().values())
      {
        AttributeIndex index = new AttributeIndex(this, indexConfig);
        attrIndexMap.put(indexConfig.getAttributeType(), index);
      }
    }

    entryDataConfig = new DataConfig();
    entryDataConfig.setCompressed(config.isEntriesCompressed());
  }

  /**
   * Opens the entryContainer for reading and writing.
   *
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public void open()
       throws DatabaseException
  {
    // Use this database config, duplicates are not allowed.
    DatabaseConfig dbNodupsConfig = new DatabaseConfig();
    dbNodupsConfig.setAllowCreate(true);
    dbNodupsConfig.setTransactional(true);

    try
    {
      id2entry = new ID2Entry(this, dbNodupsConfig, entryDataConfig,
                              ID2ENTRY_DATABASE_NAME);
      id2entry.open();

      // Set the dn2id ordering so that we can iterate through a subtree.
      dn2idComparator = new KeyReverseComparator();
      DatabaseConfig dn2idConfig = new DatabaseConfig();
      dn2idConfig.setAllowCreate(true);
      dn2idConfig.setTransactional(true);
      dn2idConfig.setBtreeComparator(dn2idComparator.getClass());
      dn2id = new DN2ID(this, dn2idConfig, DN2ID_DATABASE_NAME);
      dn2id.open();

      id2children.open(dbNodupsConfig);
      id2subtree.open(dbNodupsConfig);

      DatabaseConfig dn2uriConfig = new DatabaseConfig();
      dn2uriConfig.setSortedDuplicates(true);
      dn2uriConfig.setAllowCreate(true);
      dn2uriConfig.setTransactional(true);
      dn2uriConfig.setBtreeComparator(dn2idComparator.getClass());
      dn2uri = new DN2URI(this, dn2uriConfig, REFERRAL_DATABASE_NAME);
      dn2uri.open();

      for (AttributeIndex index : attrIndexMap.values())
      {
        index.open(dbNodupsConfig);
      }
    }
    catch (DatabaseException e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      close();
      throw e;
    }
  }

  /**
   * Opens the entryContainer for reading and writing without transactions.
   *
   * @param  deferredWrite  Indicates whether to open the entryContainer using
   *                        the deferred write mode.
   *
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public void openNonTransactional(boolean deferredWrite)
       throws DatabaseException
  {
    // Use this database config, duplicates are not allowed.
    DatabaseConfig dbNodupsConfig = new DatabaseConfig();
    dbNodupsConfig.setAllowCreate(true);
    dbNodupsConfig.setTransactional(false);
    dbNodupsConfig.setDeferredWrite(deferredWrite);

    try
    {
      id2entry = new ID2Entry(this, dbNodupsConfig, entryDataConfig,
                              ID2ENTRY_DATABASE_NAME);
      id2entry.open();

      // Set the dn2id ordering so that we can iterate through a subtree.
      dn2idComparator = new KeyReverseComparator();
      DatabaseConfig dn2idConfig = new DatabaseConfig();
      dn2idConfig.setAllowCreate(true);
      dn2idConfig.setTransactional(false);
      dn2idConfig.setBtreeComparator(dn2idComparator.getClass());
      dn2idConfig.setDeferredWrite(deferredWrite);
      dn2id = new DN2ID(this, dn2idConfig, DN2ID_DATABASE_NAME);
      dn2id.open();

      id2children.open(dbNodupsConfig);
      id2subtree.open(dbNodupsConfig);

      DatabaseConfig dn2uriConfig = new DatabaseConfig();
      dn2uriConfig.setSortedDuplicates(true);
      dn2uriConfig.setAllowCreate(true);
      dn2uriConfig.setTransactional(false);
      dn2uriConfig.setBtreeComparator(dn2idComparator.getClass());
      dn2uriConfig.setDeferredWrite(deferredWrite);
      dn2uri = new DN2URI(this, dn2uriConfig, REFERRAL_DATABASE_NAME);
      dn2uri.open();

      for (AttributeIndex index : attrIndexMap.values())
      {
        index.open(dbNodupsConfig);
      }
    }
    catch (DatabaseException e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      close();
      throw e;
    }
  }

  /**
   * Opens the entryContainer for reading only.
   *
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public void openReadOnly()
       throws DatabaseException
  {
    // Use this database config, duplicates are not allowed.
    DatabaseConfig dbNodupsConfig = new DatabaseConfig();
    dbNodupsConfig.setReadOnly(true);
    dbNodupsConfig.setAllowCreate(false);
    dbNodupsConfig.setTransactional(false);

    try
    {
      id2entry = new ID2Entry(this, dbNodupsConfig, entryDataConfig,
                              ID2ENTRY_DATABASE_NAME);
      id2entry.open();

      // Set the dn2id ordering so that we can iterate through a subtree.
      dn2idComparator = new KeyReverseComparator();
      DatabaseConfig dn2idConfig = new DatabaseConfig();
      dn2idConfig.setReadOnly(true);
      dn2idConfig.setAllowCreate(false);
      dn2idConfig.setTransactional(false);
      dn2idConfig.setBtreeComparator(dn2idComparator.getClass());
      dn2id = new DN2ID(this, dn2idConfig, DN2ID_DATABASE_NAME);
      dn2id.open();

      id2children.open(dbNodupsConfig);
      id2subtree.open(dbNodupsConfig);

      DatabaseConfig dn2uriConfig = new DatabaseConfig();
      dn2uriConfig.setReadOnly(true);
      dn2uriConfig.setSortedDuplicates(true);
      dn2uriConfig.setAllowCreate(false);
      dn2uriConfig.setTransactional(false);
      dn2uriConfig.setBtreeComparator(dn2idComparator.getClass());
      dn2uri = new DN2URI(this, dn2uriConfig, REFERRAL_DATABASE_NAME);
      dn2uri.open();

      for (AttributeIndex index : attrIndexMap.values())
      {
        index.open(dbNodupsConfig);
      }
    }
    catch (DatabaseException e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      close();
      throw e;
    }
  }

  /**
   * Closes the entry entryContainer.
   *
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public void close()
       throws DatabaseException
  {
    // Close each database handle that has been opened.
    for (Database database : databases)
    {
      if (database.getConfig().getDeferredWrite())
      {
        database.sync();
      }

      database.close();
    }

    for (AttributeIndex index : attrIndexMap.values())
    {
      index.close();
    }
  }

  /**
   * Get the DN database used by this entry entryContainer. The entryContainer
   * must have been opened.
   *
   * @return The DN database.
   */
  public DN2ID getDN2ID()
  {
    return dn2id;
  }

  /**
   * Get the entry database used by this entry entryContainer. The
   * entryContainer must have been opened.
   *
   * @return The entry database.
   */
  public ID2Entry getID2Entry()
  {
    return id2entry;
  }

  /**
   * Get the referral database used by this entry entryContainer. The
   * entryContainer must have been opened.
   *
   * @return The referral database.
   */
  public DN2URI getDN2URI()
  {
    return dn2uri;
  }

  /**
   * Get the children database used by this entry entryContainer.
   * The entryContainer must have been opened.
   *
   * @return The children database.
   */
  public Index getID2Children()
  {
    return id2children;
  }

  /**
   * Get the subtree database used by this entry entryContainer.
   * The entryContainer must have been opened.
   *
   * @return The subtree database.
   */
  public Index getID2Subtree()
  {
    return id2subtree;
  }

  /**
   * Look for an attribute index for the given attribute type.
   *
   * @param attrType The attribute type for which an attribute index is needed.
   * @return The attribute index or null if there is none for that type.
   */
  public AttributeIndex getAttributeIndex(AttributeType attrType)
  {
    return attrIndexMap.get(attrType);
  }

  /**
   * Determine the highest entryID in the entryContainer.
   * The entryContainer must already be open.
   *
   * @return The highest entry ID.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public EntryID getHighestEntryID() throws DatabaseException
  {
    EntryID entryID = new EntryID(0);
    Cursor cursor = id2entry.openCursor(null, null);
    DatabaseEntry key = new DatabaseEntry();
    DatabaseEntry data = new DatabaseEntry();

    // Position a cursor on the last data item, and the key should
    // give the highest ID.
    try
    {
      OperationStatus status = cursor.getLast(key, data, LockMode.DEFAULT);
      if (status == OperationStatus.SUCCESS)
      {
        entryID = new EntryID(key);
      }
    }
    finally
    {
      cursor.close();
    }
    return entryID;
  }

  /**
   * Processes the specified search in this entryContainer.
   * Matching entries should be provided back to the core server using the
   * <CODE>SearchOperation.returnEntry</CODE> method.
   *
   * @param searchOperation The search operation to be processed.
   * @throws org.opends.server.types.DirectoryException
   *          If a problem occurs while processing the
   *          search.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public void search(SearchOperation searchOperation)
       throws DirectoryException, DatabaseException
  {
    DN baseDN = searchOperation.getBaseDN();
    SearchScope searchScope = searchOperation.getScope();

    List<Control> controls = searchOperation.getRequestControls();
    PagedResultsControl pageRequest = null;
    ServerSideSortRequestControl sortRequest = null;
    VLVRequestControl vlvRequest = null;
    if (controls != null)
    {
      for (Control control : controls)
      {
        if (control.getOID().equals(OID_PAGED_RESULTS_CONTROL))
        {
          // Ignore all but the first paged results control.
          if (pageRequest == null)
          {
            try
            {
              pageRequest = new PagedResultsControl(control.isCritical(),
                                                    control.getValue());
            }
            catch (LDAPException e)
            {
              if (debugEnabled())
              {
                debugCaught(DebugLogLevel.ERROR, e);
              }
              throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                           e.getMessage(), e.getMessageID(), e);
            }

            if (vlvRequest != null)
            {
              int    msgID   = MSGID_JEB_SEARCH_CANNOT_MIX_PAGEDRESULTS_AND_VLV;
              String message = getMessage(msgID);
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                           message, msgID);
            }
          }
        }
        else if (control.getOID().equals(OID_SERVER_SIDE_SORT_REQUEST_CONTROL))
        {
          // Ignore all but the first sort request control.
          if (sortRequest == null)
          {
            try
            {
              sortRequest = ServerSideSortRequestControl.decodeControl(control);
            }
            catch (LDAPException e)
            {
              if (debugEnabled())
              {
                debugCaught(DebugLogLevel.ERROR, e);
              }
              throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                           e.getMessage(), e.getMessageID(), e);
            }
          }
        }
        else if (control.getOID().equals(OID_VLV_REQUEST_CONTROL))
        {
          // Ignore all but the first VLV request control.
          if (vlvRequest == null)
          {
            try
            {
              vlvRequest = VLVRequestControl.decodeControl(control);
            }
            catch (LDAPException e)
            {
              if (debugEnabled())
              {
                debugCaught(DebugLogLevel.ERROR, e);
              }
              throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                           e.getMessage(), e.getMessageID(), e);
            }

            if (pageRequest != null)
            {
              int    msgID   = MSGID_JEB_SEARCH_CANNOT_MIX_PAGEDRESULTS_AND_VLV;
              String message = getMessage(msgID);
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                           message, msgID);
            }
          }
        }
      }
    }

    // Handle client abandon of paged results.
    if (pageRequest != null)
    {
      if (pageRequest.getSize() == 0)
      {
        PagedResultsControl control;
        control = new PagedResultsControl(pageRequest.isCritical(), 0,
                                          new ASN1OctetString());
        searchOperation.getResponseControls().add(control);
        return;
      }
    }

    // Handle base-object search first.
    if (searchScope == SearchScope.BASE_OBJECT)
    {
      // Fetch the base entry.
      Entry baseEntry = null;
      try
      {
        baseEntry = getEntry(baseDN);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }
      }

      // The base entry must exist for a successful result.
      if (baseEntry == null)
      {
        // Check for referral entries above the base entry.
        dn2uri.targetEntryReferrals(searchOperation.getBaseDN(),
                                    searchOperation.getScope());

        int messageID = MSGID_JEB_SEARCH_NO_SUCH_OBJECT;
        String message = getMessage(messageID, baseDN.toString());
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
                                     message, messageID);
      }

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
        PagedResultsControl control;
        control = new PagedResultsControl(pageRequest.isCritical(), 0,
                                          new ASN1OctetString());
        searchOperation.getResponseControls().add(control);
      }

      return;
    }

    // Check whether the client requested debug information about the
    // contribution of the indexes to the search.
    StringBuilder debugBuffer = null;
    if (searchOperation.getAttributes().contains(ATTR_DEBUG_SEARCH_INDEX))
    {
      debugBuffer = new StringBuilder();
    }

    // Create an index filter to get the search result candidate entries.
    IndexFilter indexFilter =
         new IndexFilter(this, searchOperation, debugBuffer);

    // Evaluate the filter against the attribute indexes.
    EntryIDSet entryIDList = indexFilter.evaluate();

    // Evaluate the search scope against the id2children and id2subtree indexes.
    boolean candidatesAreInScope = false;
    if (entryIDList.size() > IndexFilter.FILTER_CANDIDATE_THRESHOLD)
    {
      // Read the ID from dn2id.
      EntryID baseID = dn2id.get(null, baseDN);
      if (baseID == null)
      {
        int messageID = MSGID_JEB_SEARCH_NO_SUCH_OBJECT;
        String message = getMessage(messageID, baseDN.toString());
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
                                     message, messageID);
      }
      DatabaseEntry baseIDData = baseID.getDatabaseEntry();

      EntryIDSet scopeList;
      if (searchScope == SearchScope.SINGLE_LEVEL)
      {
        scopeList = id2children.readKey(baseIDData, null, LockMode.DEFAULT);
      }
      else
      {
        scopeList = id2subtree.readKey(baseIDData, null, LockMode.DEFAULT);
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

    // If requested, construct and return a fictitious entry containing
    // debug information, and no other entries.
    if (debugBuffer != null)
    {
      debugBuffer.append(" final=");
      entryIDList.toString(debugBuffer);

      AttributeSyntax syntax =
           DirectoryServer.getDefaultStringSyntax();
      AttributeType attrType =
           DirectoryServer.getDefaultAttributeType(ATTR_DEBUG_SEARCH_INDEX,
                                                   syntax);
      ASN1OctetString valueString =
           new ASN1OctetString(debugBuffer.toString());
      LinkedHashSet<AttributeValue> values =
           new LinkedHashSet<AttributeValue>();
      values.add(new AttributeValue(valueString, valueString));
      Attribute attr = new Attribute(attrType, ATTR_DEBUG_SEARCH_INDEX, values);

      Entry debugEntry;
      debugEntry = new Entry(DN.decode("cn=debugsearch"), null, null, null);
      debugEntry.addAttribute(attr, new ArrayList<AttributeValue>());

      searchOperation.returnEntry(debugEntry, null);
      return;
    }

    if (entryIDList.isDefined())
    {
      if (sortRequest != null)
      {
        try
        {
          entryIDList = EntryIDSetSorter.sort(this, entryIDList,
                                              searchOperation,
                                              sortRequest.getSortOrder(),
                                              vlvRequest);
          searchOperation.addResponseControl(
               new ServerSideSortResponseControl(LDAPResultCode.SUCCESS, null));
        }
        catch (DirectoryException de)
        {
          searchOperation.addResponseControl(
               new ServerSideSortResponseControl(
                        de.getResultCode().getIntValue(), null));

          if (sortRequest.isCritical())
          {
            throw de;
          }
        }
      }

      searchIndexed(entryIDList, candidatesAreInScope, searchOperation,
                    pageRequest);
    }
    else
    {
      ClientConnection clientConnection =
          searchOperation.getClientConnection();
      if(! clientConnection.hasPrivilege(Privilege.UNINDEXED_SEARCH,
                                         searchOperation))
      {
        int msgID = MSGID_JEB_SEARCH_UNINDEXED_INSUFFICIENT_PRIVILEGES;
        String message = getMessage(msgID);
        throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                                     message, msgID);
      }

      if (sortRequest != null)
      {
        // FIXME -- Add support for sorting unindexed searches using indexes
        //          like DSEE currently does.
        searchOperation.addResponseControl(
             new ServerSideSortResponseControl(
                      LDAPResultCode.UNWILLING_TO_PERFORM, null));

        if (sortRequest.isCritical())
        {
          int msgID = MSGID_JEB_SEARCH_CANNOT_SORT_UNINDEXED;
          String message = getMessage(msgID);
          throw new DirectoryException(
                         ResultCode.UNAVAILABLE_CRITICAL_EXTENSION, message,
                         msgID);
        }
      }

      searchNotIndexed(searchOperation, pageRequest);
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
  private void searchNotIndexed(SearchOperation searchOperation,
                                PagedResultsControl pageRequest)
       throws DirectoryException
  {
    EntryCache entryCache = DirectoryServer.getEntryCache();
    DN baseDN = searchOperation.getBaseDN();
    SearchScope searchScope = searchOperation.getScope();
    boolean manageDsaIT = isManageDsaITOperation(searchOperation);

    // The base entry must already have been processed if this is
    // a request for the next page in paged results.  So we skip
    // the base entry processing if the cookie is set.
    if (pageRequest == null || pageRequest.getCookie().value().length == 0)
    {
      // Fetch the base entry.
      Entry baseEntry = null;
      try
      {
        baseEntry = getEntry(baseDN);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }
      }

      // The base entry must exist for a successful result.
      if (baseEntry == null)
      {
        // Check for referral entries above the base entry.
        dn2uri.targetEntryReferrals(searchOperation.getBaseDN(),
                                    searchOperation.getScope());

        int messageID = MSGID_JEB_SEARCH_NO_SUCH_OBJECT;
        String message = getMessage(messageID, baseDN.toString());
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
                                     message, messageID);
      }

      if (!manageDsaIT)
      {
        dn2uri.checkTargetForReferral(baseEntry, searchOperation.getScope());
      }

      /*
       * The base entry is only included for whole subtree search.
       */
      if (searchScope == SearchScope.WHOLE_SUBTREE)
      {
        if (searchOperation.getFilter().matchesEntry(baseEntry))
        {
          searchOperation.returnEntry(baseEntry, null);
        }
      }

      if (!manageDsaIT)
      {
        // Return any search result references.
        if (!dn2uri.returnSearchReferences(searchOperation))
        {
          if (pageRequest != null)
          {
            // Indicate no more pages.
            PagedResultsControl control;
            control = new PagedResultsControl(pageRequest.isCritical(), 0,
                                              new ASN1OctetString());
            searchOperation.getResponseControls().add(control);
          }
        }
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
    byte[] suffix = StaticUtils.getBytes("," + baseDN.toNormalizedString());

    /*
     * Set the ending value to a value of equal length but slightly
     * greater than the suffix. Since keys are compared in
     * reverse order we must set the first byte (the comma).
     * No possibility of overflow here.
     */
    byte[] end = suffix.clone();
    end[0] = (byte) (end[0] + 1);

    // Set the starting value.
    byte[] begin;
    if (pageRequest != null && pageRequest.getCookie().value().length != 0)
    {
      // The cookie contains the DN of the next entry to be returned.
      try
      {
        DN lastDN = DN.decode(pageRequest.getCookie());
        begin = StaticUtils.getBytes(lastDN.toNormalizedString());
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }
        int msgID = MSGID_JEB_INVALID_PAGED_RESULTS_COOKIE;
        String str = StaticUtils.bytesToHex(pageRequest.getCookie().value());
        String msg = getMessage(msgID, str);
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                     msg, msgID, e);
      }
    }
    else
    {
      // Set the starting value to the suffix.
      begin = suffix;
    }

    DatabaseEntry data = new DatabaseEntry();
    DatabaseEntry key = new DatabaseEntry(begin);
    List<Lock> lockList = new ArrayList<Lock>(1);

    int lookthroughCount = 0;
    int lookthroughLimit =
        searchOperation.getClientConnection().getLookthroughLimit();

    try
    {
      Cursor cursor = dn2id.openCursor(null, null);
      try
      {
        OperationStatus status;

        // Initialize the cursor very close to the starting value.
        status = cursor.getSearchKeyRange(key, data, LockMode.DEFAULT);

        // Step forward until we pass the ending value.
        while (status == OperationStatus.SUCCESS)
        {
          if(lookthroughLimit > 0 && lookthroughCount > lookthroughLimit)
          {
            //Lookthrough limit exceeded
            searchOperation.setResultCode(ResultCode.ADMIN_LIMIT_EXCEEDED);
            searchOperation.appendErrorMessage(
              getMessage(MSGID_JEB_LOOKTHROUGH_LIMIT_EXCEEDED,
              lookthroughLimit));
            return;
          }
          int cmp = dn2idComparator.compare(key.getData(), end);
          if (cmp >= 0)
          {
            // We have gone past the ending value.
            break;
          }

          // We have found a subordinate entry.

          EntryID entryID = new EntryID(data);
          DN dn = DN.decode(new ASN1OctetString(key.getData()));

          boolean isInScope = true;
          if (searchScope == SearchScope.SINGLE_LEVEL)
          {
            // Check if this entry is an immediate child.
            if ((dn.getNumComponents() !=
                 baseDN.getNumComponents() + 1))
            {
              isInScope = false;
            }
          }

          if (isInScope)
          {
            Entry entry = null;
            Entry cacheEntry = null;

            // Try the entry cache first. Note no need to take a lock.
            lockList.clear();
            cacheEntry = entryCache.getEntry(backend, entryID.longValue(),
                                             LockType.NONE, lockList);

            if (cacheEntry == null)
            {
              GetEntryByIDOperation operation =
                   new GetEntryByIDOperation(entryID);

              // Fetch the candidate entry from the database.
              this.invokeTransactedOperation(operation);
              entry = operation.getEntry();
            }
            else
            {
              entry = cacheEntry;
            }

            // Process the candidate entry.
            if (entry != null)
            {
              lookthroughCount++;

              if (manageDsaIT || entry.getReferralURLs() == null)
              {
                // Filter the entry.
                if (searchOperation.getFilter().matchesEntry(entry))
                {
                  if (pageRequest != null &&
                       searchOperation.getEntriesSent() ==
                       pageRequest.getSize())
                  {
                    // The current page is full.
                    // Set the cookie to remember where we were.
                    ASN1OctetString cookie = new ASN1OctetString(key.getData());
                    PagedResultsControl control;
                    control = new PagedResultsControl(pageRequest.isCritical(),
                                                      0, cookie);
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
          }

          // Move to the next record.
          status = cursor.getNext(key, data, LockMode.DEFAULT);
        }
      }
      finally
      {
        cursor.close();
      }
    }
    catch (DatabaseException e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
    }
    catch (JebException e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
    }

    if (pageRequest != null)
    {
      // Indicate no more pages.
      PagedResultsControl control;
      control = new PagedResultsControl(pageRequest.isCritical(), 0,
                                        new ASN1OctetString());
      searchOperation.getResponseControls().add(control);
    }

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
  private void searchIndexed(EntryIDSet entryIDList,
                             boolean candidatesAreInScope,
                             SearchOperation searchOperation,
                             PagedResultsControl pageRequest)
       throws DirectoryException
  {
    EntryCache entryCache = DirectoryServer.getEntryCache();
    SearchScope searchScope = searchOperation.getScope();
    DN baseDN = searchOperation.getBaseDN();
    boolean manageDsaIT = isManageDsaITOperation(searchOperation);
    boolean continueSearch = true;

    // Set the starting value.
    EntryID begin = null;
    if (pageRequest != null && pageRequest.getCookie().value().length != 0)
    {
      // The cookie contains the ID of the next entry to be returned.
      try
      {
        begin = new EntryID(new DatabaseEntry(pageRequest.getCookie().value()));
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }
        int msgID = MSGID_JEB_INVALID_PAGED_RESULTS_COOKIE;
        String str = StaticUtils.bytesToHex(pageRequest.getCookie().value());
        String msg = getMessage(msgID, str);
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                     msg, msgID, e);
      }
    }
    else
    {
      if (!manageDsaIT)
      {
        // Return any search result references.
        continueSearch = dn2uri.returnSearchReferences(searchOperation);
      }
    }

    // Make sure the candidate list is smaller than the lookthrough limit
    int lookthroughLimit =
        searchOperation.getClientConnection().getLookthroughLimit();
    if(lookthroughLimit > 0 && entryIDList.size() > lookthroughLimit)
    {
      //Lookthrough limit exceeded
      searchOperation.setResultCode(ResultCode.ADMIN_LIMIT_EXCEEDED);
      searchOperation.appendErrorMessage(
          getMessage(MSGID_JEB_LOOKTHROUGH_LIMIT_EXCEEDED, lookthroughLimit));
      continueSearch = false;
    }

    // Iterate through the index candidates.
    if (continueSearch)
    {
      List<Lock> lockList = new ArrayList<Lock>();
      Iterator<EntryID> iterator = entryIDList.iterator(begin);
      while (iterator.hasNext())
      {
        EntryID id = iterator.next();
        Entry entry = null;
        Entry cacheEntry = null;

        // Try the entry cache first. Note no need to take a lock.
        lockList.clear();
        cacheEntry = entryCache.getEntry(backend, id.longValue(),
                                         LockType.NONE, lockList);

        // Release any entry lock whatever happens during this block.
        // (This is actually redundant since we did not take a lock).
        try
        {
          if (cacheEntry == null)
          {
            GetEntryByIDOperation operation = new GetEntryByIDOperation(id);

            // Fetch the candidate entry from the database.
            try
            {
              this.invokeTransactedOperation(operation);
              entry = operation.getEntry();
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                debugCaught(DebugLogLevel.ERROR, e);
              }
              continue;
            }
          }
          else
          {
            entry = cacheEntry;
          }

          // Process the candidate entry.
          if (entry != null)
          {
            boolean isInScope = false;
            DN entryDN = entry.getDN();

            if (candidatesAreInScope)
            {
              isInScope = true;
            }
            else if (searchScope == SearchScope.SINGLE_LEVEL)
            {
              // Check if this entry is an immediate child.
              if ((entryDN.getNumComponents() ==
                   baseDN.getNumComponents() + 1) &&
                   entryDN.isDescendantOf(baseDN))
              {
                isInScope = true;
              }
            }
            else if (searchScope == SearchScope.WHOLE_SUBTREE)
            {
              if (entryDN.isDescendantOf(baseDN))
              {
                isInScope = true;
              }
            }
            else if (searchScope == SearchScope.SUBORDINATE_SUBTREE)
            {
              if ((entryDN.getNumComponents() >
                   baseDN.getNumComponents()) &&
                   entryDN.isDescendantOf(baseDN))
              {
                isInScope = true;
              }
            }

            // Put this entry in the cache if it did not come from the cache.
            if (cacheEntry == null)
            {
              // Put the entry in the cache making sure not to overwrite
              // a newer copy that may have been inserted since the time
              // we read the cache.
              entryCache.putEntryIfAbsent(entry, backend, id.longValue());
            }

            // Filter the entry if it is in scope.
            if (isInScope)
            {
              if (manageDsaIT || entry.getReferralURLs() == null)
              {
                if (searchOperation.getFilter().matchesEntry(entry))
                {
                  if (pageRequest != null &&
                       searchOperation.getEntriesSent() ==
                       pageRequest.getSize())
                  {
                    // The current page is full.
                    // Set the cookie to remember where we were.
                    byte[] cookieBytes = id.getDatabaseEntry().getData();
                    ASN1OctetString cookie = new ASN1OctetString(cookieBytes);
                    PagedResultsControl control;
                    control = new PagedResultsControl(pageRequest.isCritical(),
                                                      0, cookie);
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
            }
          }
        }
        finally
        {
          // Release any entry lock acquired by the entry cache
          // (This is actually redundant since we did not take a lock).
          for (Lock lock : lockList)
          {
            lock.unlock();
          }
        }
      }
    }

    // Before we return success from the search we must ensure the base entry
    // exists. However, if we have returned at least one entry or subordinate
    // reference it implies the base does exist, so we can omit the check.
    if (searchOperation.getEntriesSent() == 0 &&
         searchOperation.getReferencesSent() == 0)
    {
      // Check for referral entries above the base entry.
      dn2uri.targetEntryReferrals(searchOperation.getBaseDN(),
                                  searchOperation.getScope());

      if (!entryExists(baseDN))
      {
        int messageID = MSGID_JEB_SEARCH_NO_SUCH_OBJECT;
        String message = getMessage(messageID, baseDN.toString());
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
                                     message, messageID);
      }
    }

    if (pageRequest != null)
    {
      // Indicate no more pages.
      PagedResultsControl control;
      control = new PagedResultsControl(pageRequest.isCritical(), 0,
                                        new ASN1OctetString());
      searchOperation.getResponseControls().add(control);
    }

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
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws JebException If an error occurs in the JE backend.
   */
  public void addEntry(Entry entry, AddOperation addOperation)
       throws DatabaseException, DirectoryException, JebException
  {
    TransactedOperation operation =
         new AddEntryTransaction(entry);

    invokeTransactedOperation(operation);
  }

  /**
   * This method is common to all operations invoked under a database
   * transaction. It retries the operation if the transaction is
   * aborted due to a deadlock condition, up to a configured maximum
   * number of retries.
   *
   * @param operation An object implementing the TransactedOperation interface.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws JebException If an error occurs in the JE backend.
   */
  private void invokeTransactedOperation(TransactedOperation operation)
       throws DatabaseException, DirectoryException, JebException
  {
    // Attempt the operation under a transaction until it fails or completes.
    boolean completed = false;
    int retryRemaining = config.getDeadlockRetryLimit();
    while (!completed)
    {
      // Start a transaction.
      Transaction txn = operation.beginOperationTransaction();

      try
      {
        // Invoke the operation.
        operation.invokeOperation(txn);

        // Commit the transaction.
        EntryContainer.transactionCommit(txn);
        completed = true;
      }
      catch (DeadlockException deadlockException)
      {
        EntryContainer.transactionAbort(txn);
        if (retryRemaining-- <= 0)
        {
          throw deadlockException;
        }
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, deadlockException);
        }
      }
      catch (DatabaseException databaseException)
      {
        EntryContainer.transactionAbort(txn);
        throw databaseException;
      }
      catch (DirectoryException directoryException)
      {
        EntryContainer.transactionAbort(txn);
        throw directoryException;
      }
      catch (JebException jebException)
      {
        EntryContainer.transactionAbort(txn);
        throw jebException;
      }
      catch (Exception e)
      {
        EntryContainer.transactionAbort(txn);

        int messageID = MSGID_JEB_UNCHECKED_EXCEPTION;
        String message = getMessage(messageID);
        throw new JebException(messageID, message, e);
      }
    }

    // Do any actions necessary after successful commit,
    // usually to update the entry cache.
    operation.postCommitAction();
  }

  /**
   * This interface represents any kind of operation on the database
   * that must be performed under a transaction. A class which implements
   * this interface does not need to be concerned with creating the
   * transaction nor retrying the transaction after deadlock.
   */
  private interface TransactedOperation
  {
    /**
     * Begin a transaction for this operation.
     *
     * @return The transaction for the operation, or null if the operation
     *         will not use a transaction.
     * @throws DatabaseException If an error occurs in the JE database.
     */
    public abstract Transaction beginOperationTransaction()
        throws DatabaseException;

    /**
     * Invoke the operation under the given transaction.
     *
     * @param txn The transaction to be used to perform the operation.
     * @throws DatabaseException If an error occurs in the JE database.
     * @throws DirectoryException If a Directory Server error occurs.
     * @throws JebException If an error occurs in the JE backend.
     */
    public abstract void invokeOperation(Transaction txn)
         throws DatabaseException, DirectoryException, JebException;

    /**
     * This method is called after the transaction has successfully
     * committed.
     */
    public abstract void postCommitAction();
  }

  /**
   * This inner class implements the Add Entry operation through
   * the TransactedOperation interface.
   */
  private class AddEntryTransaction implements TransactedOperation
  {
    /**
     * The entry to be added.
     */
    private Entry entry;

    /**
     * The DN of the superior entry of the entry to be added.  This can be
     * null if the entry to be added is a base entry.
     */
    DN parentDN;

    /**
     * The ID of the entry once it has been assigned.
     */
    EntryID entryID = null;

    /**
     * Begin a transaction for this operation.
     *
     * @return The transaction for the operation, or null if the operation
     *         will not use a transaction.
     * @throws DatabaseException If an error occurs in the JE database.
     */
    public Transaction beginOperationTransaction() throws DatabaseException
    {
      return beginTransaction();
    }

    /**
     * Create a new Add Entry Transaction.
     * @param entry The entry to be added.
     */
    public AddEntryTransaction(Entry entry)
    {
      this.entry = entry;
      this.parentDN = getParentWithinBase(entry.getDN());
    }

    /**
     * Invoke the operation under the given transaction.
     *
     * @param txn The transaction to be used to perform the operation.
     * @throws DatabaseException If an error occurs in the JE database.
     * @throws DirectoryException If a Directory Server error occurs.
     * @throws JebException If an error occurs in the JE backend.
     */
    public void invokeOperation(Transaction txn)
         throws DatabaseException, DirectoryException, JebException
    {
      // Check whether the entry already exists.
      if (dn2id.get(txn, entry.getDN()) != null)
      {
        int msgID = MSGID_JEB_ADD_ENTRY_ALREADY_EXISTS;
        String message = getMessage(msgID, entry.getDN().toString());
        throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS,
                                     message, msgID);
      }

      // Check that the parent entry exists.
      EntryID parentID = null;
      if (parentDN != null)
      {
        // Check for referral entries above the target.
        dn2uri.targetEntryReferrals(entry.getDN(), null);

        // Read the parent ID from dn2id.
        parentID = dn2id.get(txn, parentDN);
        if (parentID == null)
        {
          int msgID = MSGID_JEB_ADD_NO_SUCH_OBJECT;
          String message = getMessage(msgID, entry.getDN().toString());
          throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
                                       message, msgID);
        }
      }

      // First time through, assign the next entryID.
      if (entryID == null)
      {
        entryID = rootContainer.getNextEntryID();
      }

      // Insert into dn2id.
      if (!dn2id.insert(txn, entry.getDN(), entryID))
      {
        // Do not ever expect to come through here.
        int msgID = MSGID_JEB_ADD_ENTRY_ALREADY_EXISTS;
        String message = getMessage(msgID, entry.getDN().toString());
        throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS,
                                     message, msgID);
      }

      // Update the referral database for referral entries.
      if (!dn2uri.addEntry(txn, entry))
      {
        // Do not ever expect to come through here.
        int msgID = MSGID_JEB_ADD_ENTRY_ALREADY_EXISTS;
        String message = getMessage(msgID, entry.getDN().toString());
        throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS,
                                     message, msgID);
      }

      // Insert into id2entry.
      if (!id2entry.insert(txn, entryID, entry))
      {
        // Do not ever expect to come through here.
        int msgID = MSGID_JEB_ADD_ENTRY_ALREADY_EXISTS;
        String message = getMessage(msgID, entry.getDN().toString());
        throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS,
                                     message, msgID);
      }

      // Insert into the indexes, in index configuration order.
      indexInsertEntry(txn, entry, entryID);

      // Insert into id2children and id2subtree.
      // The database transaction locks on these records will be hotly
      // contested so we do them last so as to hold the locks for the
      // shortest duration.
      if (parentDN != null)
      {
        // Insert into id2children for parent ID.
        id2children.insertID(txn, parentID.getDatabaseEntry(), entryID);

        // Insert into id2subtree for parent ID.
        id2subtree.insertID(txn, parentID.getDatabaseEntry(), entryID);

        // Iterate up through the superior entries, starting above the parent.
        for (DN dn = getParentWithinBase(parentDN); dn != null;
             dn = getParentWithinBase(dn))
        {
          // Read the ID from dn2id.
          EntryID nodeID = dn2id.get(txn, dn);
          if (nodeID == null)
          {
            int msgID = MSGID_JEB_MISSING_DN2ID_RECORD;
            String msg = getMessage(msgID, dn.toNormalizedString());
            throw new JebException(msgID, msg);
          }

          // Insert into id2subtree for this node.
          id2subtree.insertID(txn, nodeID.getDatabaseEntry(), entryID);
        }
      }

    }

    /**
     * This method is called after the transaction has successfully
     * committed.
     */
    public void postCommitAction()
    {
      // Update the entry cache.
      EntryCache entryCache = DirectoryServer.getEntryCache();
      if (entryCache != null)
      {
        entryCache.putEntry(entry, backend, entryID.longValue());
      }
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
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws JebException If an error occurs in the JE backend.
   */
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
       throws DirectoryException, DatabaseException, JebException
  {
    DeleteEntryTransaction operation =
         new DeleteEntryTransaction(entryDN, deleteOperation);

    invokeTransactedOperation(operation);

    if (operation.adminSizeLimitExceeded())
    {
      String message = getMessage(MSGID_JEB_SUBTREE_DELETE_SIZE_LIMIT_EXCEEDED,
                                  operation.getDeletedEntryCount());
      throw new DirectoryException(
           ResultCode.ADMIN_LIMIT_EXCEEDED,
           message,
           MSGID_JEB_SUBTREE_DELETE_SIZE_LIMIT_EXCEEDED);
    }

    String message = getMessage(MSGID_JEB_DELETED_ENTRY_COUNT,
                                operation.getDeletedEntryCount());
    StringBuilder errorMessage = new StringBuilder();
    errorMessage.append(message);
    deleteOperation.setErrorMessage(errorMessage);
  }

  /**
   * Delete a leaf entry.
   * The caller must be sure that the entry is indeed a leaf. We cannot
   * rely on id2children to check for children since this entry may at
   * one time have had enough children to exceed the index entry limit,
   * after which the number of children IDs is unknown.
   *
   * @param id2cBuffered A buffered children index.
   * @param id2sBuffered A buffered subtree index.
   * @param txn    The database transaction.
   * @param leafDN The DN of the leaf entry to be deleted.
   * @param leafID The ID of the leaf entry.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws JebException If an error occurs in the JE backend.
   */
  private void deleteLeaf(BufferedIndex id2cBuffered,
                          BufferedIndex id2sBuffered,
                          Transaction txn,
                          DN leafDN,
                          EntryID leafID)
       throws DatabaseException, DirectoryException, JebException
  {
    // Check that the entry exists in id2entry and read its contents.
    Entry entry = id2entry.get(txn, leafID);
    if (entry == null)
    {
      int msgID = MSGID_JEB_MISSING_ID2ENTRY_RECORD;
      String msg = getMessage(msgID, leafID.toString());
      throw new JebException(msgID, msg);
    }

    // Remove from dn2id.
    if (!dn2id.remove(txn, leafDN))
    {
      // Do not expect to ever come through here.
      int msgID = MSGID_JEB_DELETE_NO_SUCH_OBJECT;
      String message = getMessage(msgID, leafDN.toString());
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
                                   message, msgID);
    }

    // Update the referral database.
    dn2uri.deleteEntry(txn, entry);

    // Remove from id2entry.
    if (!id2entry.remove(txn, leafID))
    {
      int msgID = MSGID_JEB_MISSING_ID2ENTRY_RECORD;
      String msg = getMessage(msgID, leafID.toString());
      throw new JebException(msgID, msg);
    }

    // Remove from the indexes, in index config order.
    indexRemoveEntry(txn, entry, leafID);

    // Make sure this entry either has no children in id2children,
    // or that the index entry limit has been exceeded.
    byte[] keyID = leafID.getDatabaseEntry().getData();
    EntryIDSet children = id2cBuffered.get(keyID);
    if (!children.isDefined())
    {
      id2cBuffered.remove(keyID);
    }
    else if (children.size() != 0)
    {
      int msgID = MSGID_JEB_DELETE_NOT_ALLOWED_ON_NONLEAF;
      String message = getMessage(msgID, leafDN.toString());
      throw new DirectoryException(ResultCode.NOT_ALLOWED_ON_NONLEAF,
                                   message, msgID);
    }

    // Make sure this entry either has no subordinates in id2subtree,
    // or that the index entry limit has been exceeded.
    EntryIDSet subordinates = id2sBuffered.get(keyID);
    if (!subordinates.isDefined())
    {
      id2sBuffered.remove(keyID);
    }
    else if (subordinates.size() != 0)
    {
      int msgID = MSGID_JEB_DELETE_NOT_ALLOWED_ON_NONLEAF;
      String message = getMessage(msgID, leafDN.toString());
      throw new DirectoryException(ResultCode.NOT_ALLOWED_ON_NONLEAF,
                                   message, msgID);
    }

    // Iterate up through the superior entries.
    boolean isParent = true;
    for (DN dn = getParentWithinBase(leafDN); dn != null;
         dn = getParentWithinBase(dn))
    {
      // Read the ID from dn2id.
      EntryID nodeID = dn2id.get(txn, dn);
      if (nodeID == null)
      {
        int msgID = MSGID_JEB_MISSING_DN2ID_RECORD;
        String msg = getMessage(msgID, dn.toNormalizedString());
        throw new JebException(msgID, msg);
      }
      DatabaseEntry nodeIDData = nodeID.getDatabaseEntry();

      // Remove from id2children.
      if (isParent)
      {
        id2cBuffered.removeID(nodeIDData.getData(), leafID);
        isParent = false;
      }

      // Remove from id2subtree for this node.
      id2sBuffered.removeID(nodeIDData.getData(), leafID);
    }
  }

  /**
   * Delete the target entry of a delete operation, with appropriate handling
   * of referral entries. The caller must be sure that the entry is indeed a
   * leaf.
   *
   * @param manageDsaIT In the case where the target entry is a referral entry,
   * this parameter should be true if the target is to be deleted, or false if
   * the target should generate a referral.
   * @param id2cBuffered A buffered children index.
   * @param id2sBuffered A buffered subtree index.
   * @param txn    The database transaction.
   * @param leafDN The DN of the target entry to be deleted.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws JebException If an error occurs in the JE backend.
   */
  private void deleteTarget(boolean manageDsaIT,
                            BufferedIndex id2cBuffered,
                            BufferedIndex id2sBuffered,
                            Transaction txn,
                            DN leafDN)
       throws DatabaseException, DirectoryException, JebException
  {
    // Read the entry ID from dn2id.
    EntryID leafID = dn2id.get(txn, leafDN);
    if (leafID == null)
    {
      int msgID = MSGID_JEB_DELETE_NO_SUCH_OBJECT;
      String message = getMessage(msgID, leafDN.toString());
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
                                   message, msgID);
    }

    // Check that the entry exists in id2entry and read its contents.
    Entry entry = id2entry.get(txn, leafID);
    if (entry == null)
    {
      int msgID = MSGID_JEB_MISSING_ID2ENTRY_RECORD;
      String msg = getMessage(msgID, leafID.toString());
      throw new JebException(msgID, msg);
    }

    if (!manageDsaIT)
    {
      dn2uri.checkTargetForReferral(entry, null);
    }

    // Remove from dn2id.
    if (!dn2id.remove(txn, leafDN))
    {
      // Do not expect to ever come through here.
      int msgID = MSGID_JEB_DELETE_NO_SUCH_OBJECT;
      String message = getMessage(msgID, leafDN.toString());
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
                                   message, msgID);
    }

    // Update the referral database.
    dn2uri.deleteEntry(txn, entry);

    // Remove from id2entry.
    if (!id2entry.remove(txn, leafID))
    {
      int msgID = MSGID_JEB_MISSING_ID2ENTRY_RECORD;
      String msg = getMessage(msgID, leafID.toString());
      throw new JebException(msgID, msg);
    }

    // Remove from the indexes, in index config order.
    indexRemoveEntry(txn, entry, leafID);

    // Iterate up through the superior entries.
    boolean isParent = true;
    for (DN dn = getParentWithinBase(leafDN); dn != null;
         dn = getParentWithinBase(dn))
    {
      // Read the ID from dn2id.
      EntryID nodeID = dn2id.get(txn, dn);
      if (nodeID == null)
      {
        int msgID = MSGID_JEB_MISSING_DN2ID_RECORD;
        String msg = getMessage(msgID, dn.toNormalizedString());
        throw new JebException(msgID, msg);
      }
      DatabaseEntry nodeIDData = nodeID.getDatabaseEntry();

      // Remove from id2children.
      if (isParent)
      {
        id2cBuffered.removeID(nodeIDData.getData(), leafID);
        isParent = false;
      }

      // Remove from id2subtree for this node.
      id2sBuffered.removeID(nodeIDData.getData(), leafID);
    }
  }

  /**
   * This inner class implements the Delete Entry operation through
   * the TransactedOperation interface.
   */
  private class DeleteEntryTransaction implements TransactedOperation
  {
    /**
     * The DN of the entry or subtree to be deleted.
     */
    private DN entryDN;

    /**
     * The Delete operation.
     */
    private DeleteOperation deleteOperation;

    /**
     * A list of the DNs of all entries deleted by this operation.
     * The subtree delete control can cause multiple entries to be deleted.
     */
    private ArrayList<DN> deletedDNList;


    /**
     * Indicates whether the subtree delete size limit has been exceeded.
     */
    private boolean adminSizeLimitExceeded = false;

    /**
     * Create a new Delete Entry Transaction.
     * @param entryDN The entry or subtree to be deleted.
     * @param deleteOperation The Delete operation.
     */
    public DeleteEntryTransaction(DN entryDN, DeleteOperation deleteOperation)
    {
      this.entryDN = entryDN;
      this.deleteOperation = deleteOperation;
      deletedDNList = new ArrayList<DN>();
    }

    /**
     * Determine whether the subtree delete size limit has been exceeded.
     * @return true if the size limit has been exceeded.
     */
    public boolean adminSizeLimitExceeded()
    {
      return adminSizeLimitExceeded;
    }

    /**
     * Get the number of entries deleted during the operation.
     * @return The number of entries deleted.
     */
    public int getDeletedEntryCount()
    {
      return deletedDNList.size();
    }

    /**
     * Begin a transaction for this operation.
     *
     * @return The transaction for the operation, or null if the operation
     *         will not use a transaction.
     * @throws DatabaseException If an error occurs in the JE database.
     */
    public Transaction beginOperationTransaction() throws DatabaseException
    {
      return beginTransaction();
    }

    /**
     * Invoke the operation under the given transaction.
     *
     * @param txn The transaction to be used to perform the operation.
     * @throws DatabaseException If an error occurs in the JE database.
     * @throws DirectoryException If a Directory Server error occurs.
     * @throws JebException If an error occurs in the JE backend.
     */
    public void invokeOperation(Transaction txn)
         throws DatabaseException, DirectoryException, JebException
    {
      // Check for referral entries above the target entry.
      dn2uri.targetEntryReferrals(entryDN, null);

      // Determine whether this is a subtree delete.
      int adminSizeLimit = config.getSubtreeDeleteSizeLimit();
      boolean isSubtreeDelete = false;
      List<Control> controls = deleteOperation.getRequestControls();
      if (controls != null)
      {
        for (Control control : controls)
        {
          if (control.getOID().equals(OID_SUBTREE_DELETE_CONTROL))
          {
            isSubtreeDelete = true;
          }
        }
      }

      /*
       * We will iterate backwards through a range of the dn2id keys to
       * find subordinates of the target entry from the bottom of the tree
       * upwards. For example, any subordinates of "dc=example,dc=com" appear
       * in dn2id with a key ending in ",dc=example,dc=com". The entry
       * "cn=joe,ou=people,dc=example,dc=com" will appear after the entry
       * "ou=people,dc=example,dc=com".
       */
      byte[] suffix = StaticUtils.getBytes("," + entryDN.toNormalizedString());

      /*
       * Set the starting value to a value of equal length but slightly
       * greater than the target DN. Since keys are compared in
       * reverse order we must set the first byte (the comma).
       * No possibility of overflow here.
       */
      byte[] begin = suffix.clone();
      begin[0] = (byte) (begin[0] + 1);

      // Set the ending value to the suffix.
      byte[] end = suffix;

      DatabaseEntry data = new DatabaseEntry();
      DatabaseEntry key = new DatabaseEntry(begin);

      BufferedIndex id2cBuffered = new BufferedIndex(id2children, txn);
      BufferedIndex id2sBuffered = new BufferedIndex(id2subtree, txn);

      Cursor cursor = dn2id.openCursor(txn, null);
      try
      {
        OperationStatus status;

        // Initialize the cursor very close to the starting value.
        status = cursor.getSearchKeyRange(key, data, LockMode.DEFAULT);
        if (status == OperationStatus.NOTFOUND)
        {
          status = cursor.getLast(key, data, LockMode.DEFAULT);
        }

        // Step back until the key is less than the beginning value
        while (status == OperationStatus.SUCCESS &&
             dn2idComparator.compare(key.getData(), begin) >= 0)
        {
          status = cursor.getPrev(key, data, LockMode.DEFAULT);
        }

        // Step back until we pass the ending value.
        while (status == OperationStatus.SUCCESS)
        {
          int cmp = dn2idComparator.compare(key.getData(), end);
          if (cmp < 0)
          {
            // We have gone past the ending value.
            break;
          }

          // We have found a subordinate entry.

          if (!isSubtreeDelete)
          {
            // The subtree delete control was not specified and
            // the target entry is not a leaf.
            int msgID = MSGID_JEB_DELETE_NOT_ALLOWED_ON_NONLEAF;
            String message = getMessage(msgID, entryDN.toString());
            throw new DirectoryException(ResultCode.NOT_ALLOWED_ON_NONLEAF,
                                         message, msgID);
          }

          // Enforce any subtree delete size limit.
          if (adminSizeLimit > 0 && deletedDNList.size() >= adminSizeLimit)
          {
            adminSizeLimitExceeded = true;
            break;
          }

          /*
           * Delete this entry which by now must be a leaf because
           * we have been deleting from the bottom of the tree upwards.
           */
          EntryID entryID = new EntryID(data);
          DN subordinateDN = DN.decode(new ASN1OctetString(key.getData()));
          deleteLeaf(id2cBuffered, id2sBuffered,
                     txn, subordinateDN, entryID);

          deletedDNList.add(subordinateDN);

          status = cursor.getPrev(key, data, LockMode.DEFAULT);
        }
      }
      finally
      {
        cursor.close();
      }

      // Finally delete the target entry as it was not included
      // in the dn2id iteration.
      if (!adminSizeLimitExceeded)
      {
        // Enforce any subtree delete size limit.
        if (adminSizeLimit > 0 && deletedDNList.size() >= adminSizeLimit)
        {
          adminSizeLimitExceeded = true;
        }
        else
        {
          boolean manageDsaIT;
          if (isSubtreeDelete)
          {
            // draft-armijo-ldap-treedelete, 4.1 Tree Delete Semantics:
            // The server MUST NOT chase referrals stored in the tree.  If
            // information about referrals is stored in this section of the
            // tree, this pointer will be deleted.
            manageDsaIT = true;
          }
          else
          {
            manageDsaIT = isManageDsaITOperation(deleteOperation);
          }
          deleteTarget(manageDsaIT, id2cBuffered, id2sBuffered, txn, entryDN);

          deletedDNList.add(entryDN);
        }
      }

      // Write out any buffered index values.
      id2cBuffered.flush();
      id2sBuffered.flush();

    }

    /**
     * This method is called after the transaction has successfully
     * committed.
     */
    public void postCommitAction()
    {
      // Update the entry cache.
      EntryCache entryCache = DirectoryServer.getEntryCache();
      if (entryCache != null)
      {
        for (DN dn : deletedDNList)
        {
          entryCache.removeEntry(dn);
        }
      }
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
  public boolean entryExists(DN entryDN)
         throws DirectoryException
  {
    EntryCache entryCache = DirectoryServer.getEntryCache();

    // Try the entry cache first.
    if (entryCache != null)
    {
      if (entryCache.containsEntry(entryDN))
      {
        return true;
      }
    }

    // Read the ID from dn2id.
    EntryID id = null;
    try
    {
      id = dn2id.get(null, entryDN);
    }
    catch (DatabaseException e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
    }

    return id != null;
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
   * @throws JebException If an error occurs in the JE backend.
   * @throws DatabaseException An error occurred during a database operation.
   */
  public Entry getEntry(DN entryDN)
       throws JebException, DatabaseException, DirectoryException
  {
    EntryCache entryCache = DirectoryServer.getEntryCache();
    Entry entry = null;

    // Try the entry cache first.
    if (entryCache != null)
    {
      entry = entryCache.getEntry(entryDN);
    }

    if (entry == null)
    {
      GetEntryByDNOperation operation = new GetEntryByDNOperation(entryDN);

      // Fetch the entry from the database.
      invokeTransactedOperation(operation);

      entry = operation.getEntry();

      // Put the entry in the cache making sure not to overwrite
      // a newer copy that may have been inserted since the time
      // we read the cache.
      if (entry != null && entryCache != null)
      {
        entryCache.putEntryIfAbsent(entry, backend,
                                    operation.getEntryID().longValue());
      }
    }

    return entry;
  }

  /**
   * This inner class gets an entry by DN through
   * the TransactedOperation interface.
   */
  private class GetEntryByDNOperation implements TransactedOperation
  {
    /**
     * The retrieved entry.
     */
    private Entry entry = null;

    /**
     * The ID of the retrieved entry.
     */
    private EntryID entryID = null;

    /**
     * The DN of the entry to be retrieved.
     */
    DN entryDN;

    /**
     * Create a new transacted operation to retrieve an entry by DN.
     * @param entryDN The DN of the entry to be retrieved.
     */
    public GetEntryByDNOperation(DN entryDN)
    {
      this.entryDN = entryDN;
    }

    /**
     * Get the retrieved entry.
     * @return The retrieved entry.
     */
    public Entry getEntry()
    {
      return entry;
    }

    /**
     * Get the ID of the retrieved entry.
     * @return The ID of the retrieved entry.
     */
    public EntryID getEntryID()
    {
      return entryID;
    }

    /**
     * Begin a transaction for this operation.
     *
     * @return The transaction for the operation, or null if the operation
     *         will not use a transaction.
     * @throws DatabaseException If an error occurs in the JE database.
     */
    public Transaction beginOperationTransaction() throws DatabaseException
    {
      // For best performance queries do not use a transaction.
      // We permit temporary inconsistencies between the multiple
      // records that make up a single entry.
      return null;
    }

    /**
     * Invoke the operation under the given transaction.
     *
     * @param txn The transaction to be used to perform the operation
     * @throws DatabaseException If an error occurs in the JE database.
     * @throws DirectoryException If a Directory Server error occurs.
     * @throws JebException If an error occurs in the JE backend.
     */
    public void invokeOperation(Transaction txn) throws DatabaseException,
                                                        DirectoryException,
                                                        JebException
    {
      // Read dn2id.
      entryID = dn2id.get(txn, entryDN);
      if (entryID == null)
      {
        // The entryDN does not exist.

        // Check for referral entries above the target entry.
        dn2uri.targetEntryReferrals(entryDN, null);

        return;
      }

      // Read id2entry.
      entry = id2entry.get(txn, entryID);

      if (entry == null)
      {
        // The entryID does not exist.
        int msgID = MSGID_JEB_MISSING_ID2ENTRY_RECORD;
        String msg = getMessage(msgID, entryID.toString());
        throw new JebException(msgID, msg);
      }

    }

    /**
     * This method is called after the transaction has successfully
     * committed.
     */
    public void postCommitAction()
    {
      // No implementation required.
    }
  }

  /**
   * This inner class gets an entry by ID through
   * the TransactedOperation interface.
   */
  private class GetEntryByIDOperation implements TransactedOperation
  {
    /**
     * The retrieved entry.
     */
    private Entry entry = null;

    /**
     * The ID of the entry to be retrieved.
     */
    private EntryID entryID;

    /**
     * Create a new transacted operation to retrieve an entry by ID.
     * @param entryID The ID of the entry to be retrieved.
     */
    public GetEntryByIDOperation(EntryID entryID)
    {
      this.entryID = entryID;
    }

    /**
     * Get the retrieved entry.
     * @return The retrieved entry.
     */
    public Entry getEntry()
    {
      return entry;
    }

    /**
     * Get the ID of the retrieved entry.
     * @return the ID of the retrieved entry.
     */
    public EntryID getEntryID()
    {
      return entryID;
    }

    /**
     * Begin a transaction for this operation.
     *
     * @return The transaction for the operation, or null if the operation
     *         will not use a transaction.
     * @throws DatabaseException If an error occurs in the JE database.
     */
    public Transaction beginOperationTransaction() throws DatabaseException
    {
      // For best performance queries do not use a transaction.
      // We permit temporary inconsistencies between the multiple
      // records that make up a single entry.
      return null;
    }

    /**
     * Invoke the operation under the given transaction.
     *
     * @param txn The transaction to be used to perform the operation.
     * @throws DatabaseException If an error occurs in the JE database.
     * @throws DirectoryException If a Directory Server error occurs.
     * @throws JebException If an error occurs in the JE backend.
     */
    public void invokeOperation(Transaction txn) throws DatabaseException,
                                                        DirectoryException,
                                                        JebException
    {
      // Read id2entry.
      entry = id2entry.get(txn, entryID);
    }

    /**
     * This method is called after the transaction has successfully
     * committed.
     */
    public void postCommitAction()
    {
      // No implementation required.
    }
  }

  /**
   * The simplest case of replacing an entry in which the entry DN has
   * not changed.
   *
   * @param entry           The new contents of the entry
   * @param modifyOperation The modify operation with which this action is
   *                        associated.  This may be <CODE>null</CODE> for
   *                        modifications performed internally.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws JebException If an error occurs in the JE backend.
   */
  public void replaceEntry(Entry entry, ModifyOperation modifyOperation)
       throws DatabaseException, DirectoryException, JebException
  {
    TransactedOperation operation =
         new ReplaceEntryTransaction(entry, modifyOperation);

    invokeTransactedOperation(operation);
  }

  /**
   * This inner class implements the Replace Entry operation through
   * the TransactedOperation interface.
   */
  private class ReplaceEntryTransaction implements TransactedOperation
  {
    /**
     * The new contents of the entry.
     */
    private Entry entry;

    /**
     * The Modify operation, or null if the replace is not due to a Modify
     * operation.
     */
    private ModifyOperation modifyOperation;

    /**
     * The ID of the entry that was replaced.
     */
    private EntryID entryID = null;

    /**
     * Create a new transacted operation to replace an entry.
     * @param entry The new contents of the entry.
     * @param modifyOperation The Modify operation, or null if the replace is
     * not due to a Modify operation.
     */
    public ReplaceEntryTransaction(Entry entry,
                                   ModifyOperation modifyOperation)
    {
      this.entry = entry;
      this.modifyOperation = modifyOperation;
    }

    /**
     * Begin a transaction for this operation.
     *
     * @return The transaction for the operation, or null if the operation
     *         will not use a transaction.
     * @throws DatabaseException If an error occurs in the JE database.
     */
    public Transaction beginOperationTransaction() throws DatabaseException
    {
      return beginTransaction();
    }

    /**
     * Invoke the operation under the given transaction.
     *
     * @param txn The transaction to be used to perform the operation.
     * @throws DatabaseException If an error occurs in the JE database.
     * @throws DirectoryException If a Directory Server error occurs.
     * @throws JebException If an error occurs in the JE backend.
     */
    public void invokeOperation(Transaction txn) throws DatabaseException,
                                                        DirectoryException,
                                                        JebException
    {
      // Read dn2id.
      entryID = dn2id.get(txn, entry.getDN());
      if (entryID == null)
      {
        // The entry does not exist.
        int msgID = MSGID_JEB_MODIFY_NO_SUCH_OBJECT;
        String message = getMessage(msgID, entry.getDN().toString());
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, msgID);
      }

      // Read id2entry for the original entry.
      Entry originalEntry = id2entry.get(txn, entryID);
      if (originalEntry == null)
      {
        // The entry does not exist.
        int msgID = MSGID_JEB_MISSING_ID2ENTRY_RECORD;
        String msg = getMessage(msgID, entryID.toString());
        throw new JebException(msgID, msg);
      }

      if (!isManageDsaITOperation(modifyOperation))
      {
        // Check if the entry is a referral entry.
        dn2uri.checkTargetForReferral(originalEntry, null);
      }

      // Update the referral database.
      if (modifyOperation != null)
      {
        // In this case we know from the operation what the modifications were.
        List<Modification> mods = modifyOperation.getModifications();
        dn2uri.modifyEntry(txn, originalEntry, entry, mods);
      }
      else
      {
        dn2uri.replaceEntry(txn, originalEntry, entry);
      }

      // Replace id2entry.
      id2entry.put(txn, entryID, entry);

      // Update the indexes.
      if (modifyOperation != null)
      {
        // In this case we know from the operation what the modifications were.
        List<Modification> mods = modifyOperation.getModifications();
        indexModifications(txn, originalEntry, entry, entryID, mods);
      }
      else
      {
        // The most optimal would be to figure out what the modifications were.
        indexRemoveEntry(txn, originalEntry, entryID);
        indexInsertEntry(txn, entry, entryID);
      }
    }

    /**
     * This method is called after the transaction has successfully
     * committed.
     */
    public void postCommitAction()
    {
      // Update the entry cache.
      EntryCache entryCache = DirectoryServer.getEntryCache();
      if (entryCache != null)
      {
        entryCache.putEntry(entry, backend, entryID.longValue());
      }
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
   * @throws org.opends.server.types.DirectoryException
   *          If a problem occurs while trying to perform
   *          the rename.
   * @throws org.opends.server.types.CancelledOperationException
   *          If this backend noticed and reacted
   *          to a request to cancel or abandon the
   *          modify DN operation.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws JebException If an error occurs in the JE backend.
   */
  public void renameEntry(DN currentDN, Entry entry,
                          ModifyDNOperation modifyDNOperation)
         throws DatabaseException, JebException, DirectoryException,
                CancelledOperationException
  {
    TransactedOperation operation =
         new RenameEntryTransaction(currentDN, entry, modifyDNOperation);

    invokeTransactedOperation(operation);
  }

  /**
   * This inner class implements the Modify DN operation through
   * the TransactedOperation interface.
   */
  private class RenameEntryTransaction implements TransactedOperation
  {
    /**
     * The DN of the entry to be renamed.
     */
    private DN oldApexDN;

    /**
     * The DN of the superior entry of the entry to be renamed.
     * This is null if the entry to be renamed is a base entry.
     */
    private DN oldSuperiorDN;

    /**
     * The DN of the new superior entry, which can be the same
     * as the current superior entry.
     */
    private DN newSuperiorDN;

    /**
     * The new contents of the entry to be renamed.
     */
    private Entry newApexEntry;

    /**
     * The Modify DN operation.
     */
    private ModifyDNOperation modifyDNOperation;


    /**
     * A buffered children index.
     */
    private BufferedIndex id2cBuffered;

    /**
     * A buffered subtree index.
     */
    private BufferedIndex id2sBuffered;

    /**
     * Create a new transacted operation for a Modify DN operation.
     * @param currentDN The DN of the entry to be renamed.
     * @param entry The new contents of the entry.
     * @param modifyDNOperation The Modify DN operation to be performed.
     */
    public RenameEntryTransaction(DN currentDN, Entry entry,
                                   ModifyDNOperation modifyDNOperation)
    {
      this.oldApexDN = currentDN;
      this.oldSuperiorDN = getParentWithinBase(currentDN);
      this.newSuperiorDN = getParentWithinBase(entry.getDN());
      this.newApexEntry = entry;
      this.modifyDNOperation = modifyDNOperation;
    }

    /**
     * Invoke the operation under the given transaction.
     *
     * @param txn The transaction to be used to perform the operation.
     * @throws DatabaseException If an error occurs in the JE database.
     * @throws DirectoryException If a Directory Server error occurs.
     * @throws JebException If an error occurs in the JE backend.
     */
    public void invokeOperation(Transaction txn) throws DatabaseException,
                                                        DirectoryException,
                                                        JebException
    {
      DN requestedNewSuperiorDN = null;

      if(modifyDNOperation != null)
      {
        requestedNewSuperiorDN = modifyDNOperation.getNewSuperior();
      }

      // Check whether the renamed entry already exists.
      if (dn2id.get(txn, newApexEntry.getDN()) != null)
      {
        int msgID = MSGID_JEB_MODIFYDN_ALREADY_EXISTS;
        String message = getMessage(msgID, newApexEntry.getDN().toString());
        throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS,
                                     message, msgID);
      }

      EntryID oldApexID = dn2id.get(txn, oldApexDN);
      if (oldApexID == null)
      {
        // Check for referral entries above the target entry.
        dn2uri.targetEntryReferrals(oldApexDN, null);

        int messageID = MSGID_JEB_MODIFYDN_NO_SUCH_OBJECT;
        String message = getMessage(messageID, oldApexDN.toString());
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
                                     message, messageID);
      }

      Entry oldApexEntry = id2entry.get(txn, oldApexID);
      if (oldApexEntry == null)
      {
        int msgID = MSGID_JEB_MISSING_ID2ENTRY_RECORD;
        String msg = getMessage(msgID, oldApexID.toString());
        throw new JebException(msgID, msg);
      }

      if (!isManageDsaITOperation(modifyDNOperation))
      {
        dn2uri.checkTargetForReferral(oldApexEntry, null);
      }

      id2cBuffered = new BufferedIndex(id2children, txn);
      id2sBuffered = new BufferedIndex(id2subtree, txn);

      EntryID newApexID = oldApexID;
      if (newSuperiorDN != null)
      {
        /*
         * We want to preserve the invariant that the ID of an
         * entry is greater than its parent, since search
         * results are returned in ID order.
         */
        EntryID newSuperiorID = dn2id.get(txn, newSuperiorDN);
        if (newSuperiorID == null)
        {
          int msgID = MSGID_JEB_NEW_SUPERIOR_NO_SUCH_OBJECT;
          String msg = getMessage(msgID, newSuperiorDN.toString());
          throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, msg, msgID);
        }

        if (newSuperiorID.compareTo(oldApexID) > 0)
        {
          // This move would break the above invariant so we must
          // renumber every entry that moves. This is even more
          // expensive since every entry has to be deleted from
          // and added back into the attribute indexes.
          newApexID = rootContainer.getNextEntryID();
        }
      }

      // Move or rename the apex entry.
      if (requestedNewSuperiorDN != null)
      {
        moveApexEntry(txn, oldApexID, newApexID, oldApexEntry, newApexEntry);
      }
      else
      {
        renameApexEntry(txn, oldApexID, oldApexEntry, newApexEntry);
      }

      /*
       * We will iterate forwards through a range of the dn2id keys to
       * find subordinates of the target entry from the top of the tree
       * downwards.
       */
      byte[] suffix = StaticUtils.getBytes("," +
                                           oldApexDN.toNormalizedString());

      /*
       * Set the ending value to a value of equal length but slightly
       * greater than the suffix.
       */
      byte[] end = suffix.clone();
      end[0] = (byte) (end[0] + 1);

      // Set the starting value to the suffix.
      byte[] begin = suffix;

      DatabaseEntry data = new DatabaseEntry();
      DatabaseEntry key = new DatabaseEntry(begin);

      Cursor cursor = dn2id.openCursor(txn, null);
      try
      {
        OperationStatus status;

        // Initialize the cursor very close to the starting value.
        status = cursor.getSearchKeyRange(key, data, LockMode.RMW);

        // Step forward until the key is greater than the starting value.
        while (status == OperationStatus.SUCCESS &&
             dn2idComparator.compare(key.getData(), begin) <= 0)
        {
          status = cursor.getNext(key, data, LockMode.RMW);
        }

        // Step forward until we pass the ending value.
        while (status == OperationStatus.SUCCESS)
        {
          int cmp = dn2idComparator.compare(key.getData(), end);
          if (cmp >= 0)
          {
            // We have gone past the ending value.
            break;
          }

          // We have found a subordinate entry.

          EntryID oldID = new EntryID(data);
          Entry oldEntry = id2entry.get(txn, oldID);

          // Construct the new DN of the entry.
          DN newDN = modDN(oldEntry.getDN(),
                           oldApexDN.getNumComponents(),
                           newApexEntry.getDN());

          if (requestedNewSuperiorDN != null)
          {
            // Assign a new entry ID if we are renumbering.
            EntryID newID = oldID;
            if (!newApexID.equals(oldApexID))
            {
              newID = rootContainer.getNextEntryID();
            }

            // Move this entry.
            moveSubordinateEntry(txn, oldID, newID, oldEntry, newDN);
          }
          else
          {
            // Rename this entry.
            renameSubordinateEntry(txn, oldID, oldEntry, newDN);
          }

          // Get the next DN.
          status = cursor.getNext(key, data, LockMode.RMW);
        }
      }
      finally
      {
        cursor.close();
      }

      id2cBuffered.flush();
      id2sBuffered.flush();
    }

    /**
     * Begin a transaction for this operation.
     *
     * @return The transaction for the operation, or null if the operation
     *         will not use a transaction.
     * @throws DatabaseException If an error occurs in the JE database.
     */
    public Transaction beginOperationTransaction() throws DatabaseException
    {
      return beginTransaction();
    }

    /**
     * Update the database for the target entry of a ModDN operation
     * specifying a new superior.
     *
     * @param txn The database transaction to be used for the updates.
     * @param oldID The original ID of the target entry.
     * @param newID The new ID of the target entry, or the original ID if
     *              the ID has not changed.
     * @param oldEntry The original contents of the target entry.
     * @param newEntry The new contents of the target entry.
     * @throws JebException If an error occurs in the JE backend.
     * @throws DirectoryException If a Directory Server error occurs.
     * @throws DatabaseException If an error occurs in the JE database.
     */
    private void moveApexEntry(Transaction txn,
                               EntryID oldID, EntryID newID,
                               Entry oldEntry, Entry newEntry)
         throws JebException, DirectoryException, DatabaseException
    {
      DN oldDN = oldEntry.getDN();
      DN newDN = newEntry.getDN();
      DN newParentDN = getParentWithinBase(newDN);

      // Remove the old DN from dn2id.
      dn2id.remove(txn, oldDN);

      // Insert the new DN in dn2id.
      if (!dn2id.insert(txn, newDN, newID))
      {
        int msgID = MSGID_JEB_MODIFYDN_ALREADY_EXISTS;
        String message = getMessage(msgID, newDN.toString());
        throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS,
                                     message, msgID);
      }

      // Update any referral records.
      dn2uri.replaceEntry(txn, oldEntry, newEntry);

      // Remove the old ID from id2entry.
      if (!newID.equals(oldID) || modifyDNOperation == null)
      {
        id2entry.remove(txn, oldID);

        // Remove the old ID from the indexes.
        indexRemoveEntry(txn, oldEntry, oldID);

        // Insert the new ID into the indexes.
        indexInsertEntry(txn, newEntry, newID);
      }
      else
      {
        // Update indexes only for those attributes that changed.
        indexModifications(txn, oldEntry, newEntry, oldID,
                           modifyDNOperation.getModifications());
      }

      // Put the new entry in id2entry.
      id2entry.put(txn, newID, newEntry);

      // Remove the old parentID:ID from id2children.
      DN oldParentDN = getParentWithinBase(oldDN);
      if (oldParentDN != null)
      {
        EntryID currentParentID = dn2id.get(txn, oldParentDN);
        id2cBuffered.removeID(currentParentID.getDatabaseEntry().getData(),
                              oldID);
      }

      // Put the new parentID:ID in id2children.
      if (newParentDN != null)
      {
        EntryID parentID = dn2id.get(txn, newParentDN);
        id2cBuffered.insertID(config.getBackendIndexEntryLimit(),
                              parentID.getDatabaseEntry().getData(),
                              newID);
      }


      // Remove the old nodeID:ID from id2subtree.
      for (DN dn = getParentWithinBase(oldDN); dn != null;
           dn = getParentWithinBase(dn))
      {
        EntryID nodeID = dn2id.get(txn, dn);
        id2sBuffered.removeID(nodeID.getDatabaseEntry().getData(), oldID);
      }

      // Put the new nodeID:ID in id2subtree.
      for (DN dn = newParentDN; dn != null; dn = getParentWithinBase(dn))
      {
        EntryID nodeID = dn2id.get(txn, dn);
        id2sBuffered.insertID(config.getBackendIndexEntryLimit(),
                              nodeID.getDatabaseEntry().getData(), newID);
      }

      if (!newID.equals(oldID))
      {
        // All the subordinates will be renumbered.
        id2cBuffered.remove(oldID.getDatabaseEntry().getData());
        id2sBuffered.remove(oldID.getDatabaseEntry().getData());
      }

      // Remove the entry from the entry cache.
      EntryCache entryCache = DirectoryServer.getEntryCache();
      if (entryCache != null)
      {
        entryCache.removeEntry(oldDN);
      }
    }

    /**
     * Update the database for the target entry of a Modify DN operation
     * not specifying a new superior.
     *
     * @param txn The database transaction to be used for the updates.
     * @param entryID The ID of the target entry.
     * @param oldEntry The original contents of the target entry.
     * @param newEntry The new contents of the target entry.
     * @throws DirectoryException If a Directory Server error occurs.
     * @throws DatabaseException If an error occurs in the JE database.
     * @throws JebException if an error occurs in the JE database.
     */
    private void renameApexEntry(Transaction txn, EntryID entryID,
                                 Entry oldEntry, Entry newEntry)
         throws DirectoryException, DatabaseException, JebException
    {
      DN oldDN = oldEntry.getDN();
      DN newDN = newEntry.getDN();

      // Remove the old DN from dn2id.
      dn2id.remove(txn, oldDN);

      // Insert the new DN in dn2id.
      if (!dn2id.insert(txn, newDN, entryID))
      {
        int msgID = MSGID_JEB_MODIFYDN_ALREADY_EXISTS;
        String message = getMessage(msgID, newDN.toString());
        throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS,
                                     message, msgID);
      }

      // Update any referral records.
      dn2uri.replaceEntry(txn, oldEntry, newEntry);

      // Replace the entry in id2entry.
      id2entry.put(txn, entryID, newEntry);

      if(modifyDNOperation == null)
      {
        // Remove the old ID from the indexes.
        indexRemoveEntry(txn, oldEntry, entryID);

        // Insert the new ID into the indexes.
        indexInsertEntry(txn, newEntry, entryID);
      }
      else
      {
        // Update indexes only for those attributes that changed.
        indexModifications(txn, oldEntry, newEntry, entryID,
                           modifyDNOperation.getModifications());
      }

      // Remove the entry from the entry cache.
      EntryCache entryCache = DirectoryServer.getEntryCache();
      if (entryCache != null)
      {
        entryCache.removeEntry(oldDN);
      }
    }

    /**
     * Update the database for a subordinate entry of the target entry
     * of a Modify DN operation specifying a new superior.
     *
     * @param txn The database transaction to be used for the updates.
     * @param oldID The original ID of the subordinate entry.
     * @param newID The new ID of the subordinate entry, or the original ID if
     *              the ID has not changed.
     * @param oldEntry The original contents of the subordinate entry.
     * @param newDN The new DN of the subordinate entry.
     * @throws JebException If an error occurs in the JE backend.
     * @throws DirectoryException If a Directory Server error occurs.
     * @throws DatabaseException If an error occurs in the JE database.
     */
    private void moveSubordinateEntry(Transaction txn,
                                      EntryID oldID, EntryID newID,
                                      Entry oldEntry, DN newDN)
         throws JebException, DirectoryException, DatabaseException
    {
      DN oldDN = oldEntry.getDN();
      DN newParentDN = getParentWithinBase(newDN);

      // Remove the old DN from dn2id.
      dn2id.remove(txn, oldDN);

      // Put the new DN in dn2id.
      if (!dn2id.insert(txn, newDN, newID))
      {
        int msgID = MSGID_JEB_MODIFYDN_ALREADY_EXISTS;
        String message = getMessage(msgID, newDN.toString());
        throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS,
                                     message, msgID);
      }

      // Delete any existing referral records for the old DN.
      dn2uri.deleteEntry(txn, oldEntry);

      // Remove old ID from id2entry.
      if (!newID.equals(oldID))
      {
        id2entry.remove(txn, oldID);

        // Update the attribute indexes.
        for (AttributeIndex index : attrIndexMap.values())
        {
          index.removeEntry(txn, oldID, oldEntry);

          index.addEntry(txn, newID, oldEntry);
        }

      }

      // Change the entry DN.
      oldEntry.setDN(newDN);

      // Add any referral records for the new DN.
      dn2uri.addEntry(txn, oldEntry);

      // Put the new entry (old entry with new DN) in id2entry.
      id2entry.put(txn, newID, oldEntry);

      if (!newID.equals(oldID))
      {
        // All the subordinates will be renumbered.
        id2cBuffered.remove(oldID.getDatabaseEntry().getData());
        id2sBuffered.remove(oldID.getDatabaseEntry().getData());

        // Put the new parentID:ID in id2children.
        if (newParentDN != null)
        {
          EntryID parentID = dn2id.get(txn, newParentDN);
          id2cBuffered.insertID(config.getBackendIndexEntryLimit(),
                                parentID.getDatabaseEntry().getData(),
                                newID);
        }
      }

      // Remove the old nodeID:ID from id2subtree
      for (DN dn = oldSuperiorDN; dn != null; dn = getParentWithinBase(dn))
      {
        EntryID nodeID = dn2id.get(txn, dn);
        id2sBuffered.removeID(nodeID.getDatabaseEntry().getData(),
                              oldID);
      }

      // Put the new nodeID:ID in id2subtree.
      for (DN dn = newParentDN; dn != null; dn = getParentWithinBase(dn))
      {
        if (!newID.equals(oldID) || dn.isAncestorOf(newSuperiorDN))
        {
          EntryID nodeID = dn2id.get(txn, dn);
          id2sBuffered.insertID(config.getBackendIndexEntryLimit(),
                                nodeID.getDatabaseEntry().getData(), newID);
        }
      }

      // Remove the entry from the entry cache.
      EntryCache entryCache = DirectoryServer.getEntryCache();
      if (entryCache != null)
      {
        entryCache.removeEntry(oldDN);
      }
    }

    /**
     * Update the database for a subordinate entry of the target entry
     * of a Modify DN operation not specifying a new superior.
     *
     * @param txn The database transaction to be used for the updates.
     * @param entryID The ID of the subordinate entry.
     * @param oldEntry The original contents of the subordinate entry.
     * @param newDN The new DN of the subordinate entry.
     * @throws DirectoryException If a Directory Server error occurs.
     * @throws DatabaseException If an error occurs in the JE database.
     */
    private void renameSubordinateEntry(Transaction txn, EntryID entryID,
                                 Entry oldEntry, DN newDN)
         throws DirectoryException, DatabaseException
    {
      DN oldDN = oldEntry.getDN();

      // Remove the old DN from dn2id.
      dn2id.remove(txn, oldDN);

      // Insert the new DN in dn2id.
      if (!dn2id.insert(txn, newDN, entryID))
      {
        int msgID = MSGID_JEB_MODIFYDN_ALREADY_EXISTS;
        String message = getMessage(msgID, newDN.toString());
        throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS,
                                     message, msgID);
      }

      // Delete any existing referral records for the old DN.
      dn2uri.deleteEntry(txn, oldEntry);

      // Change the entry DN.
      oldEntry.setDN(newDN);

      // Add any referral records for the new DN.
      dn2uri.addEntry(txn, oldEntry);

      // Replace the entry in id2entry.
      id2entry.put(txn, entryID, oldEntry);

      // Remove the entry from the entry cache.
      EntryCache entryCache = DirectoryServer.getEntryCache();
      if (entryCache != null)
      {
        entryCache.removeEntry(oldDN);
      }
    }

    /**
     * This method is called after the transaction has successfully
     * committed.
     */
    public void postCommitAction()
    {
      // No implementation needed.
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
  public static DN modDN(DN oldDN, int oldSuffixLen, DN newSuffixDN)
  {
    int oldDNNumComponents    = oldDN.getNumComponents();
    int oldDNKeepComponents   = oldDNNumComponents - oldSuffixLen;
    int newSuffixDNComponents = newSuffixDN.getNumComponents();

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
   * A lexicographic byte array comparator that compares in
   * reverse byte order. This is used for the dn2id database.
   * If we want to find all the entries in a subtree dc=com we know that
   * all subordinate entries must have ,dc=com as a common suffix. In reversing
   * the order of comparison we turn the subtree base into a common prefix
   * and are able to iterate through the keys having that prefix.
   */
  static public class KeyReverseComparator implements Comparator<byte[]>
  {
    /**
     * Compares its two arguments for order.  Returns a negative integer,
     * zero, or a positive integer as the first argument is less than, equal
     * to, or greater than the second.
     *
     * @param a the first object to be compared.
     * @param b the second object to be compared.
     * @return a negative integer, zero, or a positive integer as the
     *         first argument is less than, equal to, or greater than the
     *         second.
     */
    public int compare(byte[] a, byte[] b)
    {
      for (int ai = a.length - 1, bi = b.length - 1;
           ai >= 0 && bi >= 0; ai--, bi--)
      {
        if (a[ai] > b[bi])
        {
          return 1;
        }
        else if (a[ai] < b[bi])
        {
          return -1;
        }
      }
      if (a.length == b.length)
      {
        return 0;
      }
      if (a.length > b.length)
      {
        return 1;
      }
      else
      {
        return -1;
      }
    }
  }

  /**
   * Insert a new entry into the attribute indexes.
   *
   * @param txn The database transaction to be used for the updates.
   * @param entry The entry to be inserted into the indexes.
   * @param entryID The ID of the entry to be inserted into the indexes.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws JebException If an error occurs in the JE backend.
   */
  private void indexInsertEntry(Transaction txn, Entry entry, EntryID entryID)
       throws DatabaseException, DirectoryException, JebException
  {
    for (AttributeIndex index : attrIndexMap.values())
    {
      index.addEntry(txn, entryID, entry);
    }
  }

  /**
   * Remove an entry from the attribute indexes.
   *
   * @param txn The database transaction to be used for the updates.
   * @param entry The entry to be removed from the indexes.
   * @param entryID The ID of the entry to be removed from the indexes.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws JebException If an error occurs in the JE backend.
   */
  private void indexRemoveEntry(Transaction txn, Entry entry, EntryID entryID)
       throws DatabaseException, DirectoryException, JebException
  {
    for (AttributeIndex index : attrIndexMap.values())
    {
      index.removeEntry(txn, entryID, entry);
    }
  }

  /**
   * Update the attribute indexes to reflect the changes to the
   * attributes of an entry resulting from a sequence of modifications.
   *
   * @param txn The database transaction to be used for the updates.
   * @param oldEntry The contents of the entry before the change.
   * @param newEntry The contents of the entry after the change.
   * @param entryID The ID of the entry that was changed.
   * @param mods The sequence of modifications made to the entry.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  private void indexModifications(Transaction txn, Entry oldEntry,
                                  Entry newEntry,
                                  EntryID entryID, List<Modification> mods)
       throws DatabaseException
  {
    // Process in index configuration order.
    for (AttributeIndex index : attrIndexMap.values())
    {
      // Check whether any modifications apply to this indexed attribute.
      boolean attributeModified = false;
      for (Modification mod : mods)
      {
        Attribute modAttr = mod.getAttribute();
        AttributeType modAttrType = modAttr.getAttributeType();
        if (modAttrType.equals(index.getAttributeType()))
        {
          attributeModified = true;
          break;
        }
      }
      if (attributeModified)
      {
        index.modifyEntry(txn, entryID, oldEntry, newEntry, mods);
      }
    }
  }

  /**
   * Get a count of the number of entries stored in this entry entryContainer.
   *
   * @return The number of entries stored in this entry entryContainer.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public long getEntryCount() throws DatabaseException
  {
    return id2entry.getRecordCount();
  }

  /**
   * Remove the entry entryContainer from disk. The entryContainer must not be
   * open.
   *
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public void removeContainer() throws DatabaseException
  {
    try
    {
      removeDatabase(DN2ID_DATABASE_NAME);
    }
    catch (DatabaseNotFoundException e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
    }
    try
    {
      removeDatabase(ID2ENTRY_DATABASE_NAME);
    }
    catch (DatabaseNotFoundException e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
    }
    try
    {
      removeDatabase(ID2CHILDREN_DATABASE_NAME);
    }
    catch (DatabaseNotFoundException e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
    }
    try
    {
      removeDatabase(ID2SUBTREE_DATABASE_NAME);
    }
    catch (DatabaseNotFoundException e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
    }
    for (AttributeIndex index : attrIndexMap.values())
    {
      try
      {
        index.removeIndex();
      }
      catch (DatabaseNotFoundException e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }
  }

  /**
   * Get the number of values for which the entry limit has been exceeded
   * since the entry entryContainer was opened.
   * @return The number of values for which the entry limit has been exceeded.
   */
  public int getEntryLimitExceededCount()
  {
    int count = 0;
    count += id2children.getEntryLimitExceededCount();
    count += id2subtree.getEntryLimitExceededCount();
    for (AttributeIndex index : attrIndexMap.values())
    {
      count += index.getEntryLimitExceededCount();
    }
    return count;
  }

  /**
   * Get a list of the databases opened by this entryContainer.  There will be
   * only one handle in the list for each database, regardless of the number
   * of handles open for a given database.
   * @param dbList A list of JE database handles.
   */
  public void listDatabases(List<Database> dbList)
  {
    // There may be more than one handle open for a given database
    // so we eliminate duplicates here.
    HashSet<String> set = new HashSet<String>();
    for (Database db : databases)
    {
      try
      {
        if (!set.contains(db.getDatabaseName()))
        {
          set.add(db.getDatabaseName());
          dbList.add(db);
        }
      }
      catch (DatabaseException e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }
  }

  /**
   * Determine whether the provided operation has the ManageDsaIT request
   * control.
   * @param operation The operation for which the determination is to be made.
   * @return true if the operation has the ManageDsaIT request control, or false
   * if not.
   */
  public static boolean isManageDsaITOperation(Operation operation)
  {
    if(operation != null)
    {
      List<Control> controls = operation.getRequestControls();
      if (controls != null)
      {
        for (Control control : controls)
        {
          if (control.getOID().equals(ServerConstants.OID_MANAGE_DSAIT_CONTROL))
          {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Constructs a full JE database name incorporating a entryContainer name.
   *
   * @param builder A string builder to which the full name will be appended.
   * @param name    The short database name.
   */
  private void buildDatabaseName(StringBuilder builder, String name)
  {
    builder.append(getContainerName());
    builder.append('_');
    builder.append(name);
  }

  /**
   * Opens a JE database in this entryContainer. The resulting database handle
   * must not be closed by the caller, as it will be closed by the
   * entryContainer. If the provided database configuration is transactional,
   * a transaction will be created and used to perform the open.
   * <p>
   * Note that a database can be opened multiple times and will result in
   * multiple unique handles to the database.  This is used for example to
   * give each server thread its own database handle to eliminate contention
   * that could occur on a single handle.
   *
   * @param dbConfig The JE database configuration to be used to open the
   * database.
   * @param name     The short database name, to which the entryContainer name
   *                 will be added.
   * @return A new JE database handle.
   * @throws DatabaseException If an error occurs while attempting to open the
   * database.
   */
  public synchronized Database openDatabase(DatabaseConfig dbConfig,
                                            String name)
       throws DatabaseException
  {
    Database database;

    StringBuilder builder = new StringBuilder();
    buildDatabaseName(builder, name);
    String fullName = builder.toString();

    if (dbConfig.getTransactional())
    {
      // Open the database under a transaction.
      Transaction txn = beginTransaction();
      try
      {
        database = env.openDatabase(txn, fullName, dbConfig);
        if (debugEnabled())
        {
          debugVerbose("open db=%s txnid=%d", database.getDatabaseName(),
                       txn.getId());
        }
        transactionCommit(txn);
      }
      catch (DatabaseException e)
      {
        transactionAbort(txn);
        throw e;
      }
    }
    else
    {
      database = env.openDatabase(null, fullName, dbConfig);
      if (debugEnabled())
      {
        debugVerbose("open db=%s txnid=none", database.getDatabaseName());
      }
    }

    // Insert into the list of database handles.
    databases.add(database);

    return database;
  }

  /**
   * Begin a leaf transaction using the default configuration.
   * Provides assertion debug logging.
   * @return A JE transaction handle.
   * @throws DatabaseException If an error occurs while attempting to begin
   * a new transaction.
   */
  public Transaction beginTransaction()
       throws DatabaseException
  {
    Transaction parentTxn = null;
    TransactionConfig txnConfig = null;
    Transaction txn = env.beginTransaction(parentTxn, txnConfig);
    if (debugEnabled())
    {
      debugVerbose("beginTransaction", "begin txnid=" + txn.getId());
    }
    return txn;
  }

  /**
   * Commit a transaction.
   * Provides assertion debug logging.
   * @param txn The JE transaction handle.
   * @throws DatabaseException If an error occurs while attempting to commit
   * the transaction.
   */
  public static void transactionCommit(Transaction txn)
       throws DatabaseException
  {
    if (txn != null)
    {
      txn.commit();
      if (debugEnabled())
      {
        debugVerbose("commit txnid=%d", txn.getId());
      }
    }
  }

  /**
   * Abort a transaction.
   * Provides assertion debug logging.
   * @param txn The JE transaction handle.
   * @throws DatabaseException If an error occurs while attempting to abort the
   * transaction.
   */
  public static void transactionAbort(Transaction txn)
       throws DatabaseException
  {
    if (txn != null)
    {
      txn.abort();
      if (debugEnabled())
      {
        debugVerbose("abort txnid=%d", txn.getId());
      }
    }
  }

  /**
   * Insert a record into a JE database, with optional debug logging. This is a
   * simple wrapper around the JE Database.putNoOverwrite method.
   * @param database The JE database handle.
   * @param txn The JE transaction handle, or null if none.
   * @param key The record key.
   * @param data The record value.
   * @return The operation status.
   * @throws DatabaseException If an error occurs in the JE operation.
   */
  public static OperationStatus insert(Database database, Transaction txn,
                                DatabaseEntry key, DatabaseEntry data)
       throws DatabaseException
  {
    OperationStatus status = database.putNoOverwrite(txn, key, data);
    if (debugEnabled())
    {
      debugJEAccess(DebugLogLevel.VERBOSE, status, database, txn, key, data);
    }
    return status;
  }

  /**
   * Insert a record into a JE database through a cursor, with optional debug
   * logging. This is a simple wrapper around the JE Cursor.putNoOverwrite
   * method.
   * @param cursor The JE cursor handle.
   * @param key The record key.
   * @param data The record value.
   * @return The operation status.
   * @throws DatabaseException If an error occurs in the JE operation.
   */
  public static OperationStatus cursorInsert(Cursor cursor,
                                             DatabaseEntry key,
                                             DatabaseEntry data)
       throws DatabaseException
  {
    OperationStatus status = cursor.putNoOverwrite(key, data);
    if (debugEnabled())
    {
      debugJEAccess(DebugLogLevel.VERBOSE, status,
                    cursor.getDatabase(), null, key, data);
    }
    return status;
  }

  /**
   * Replace or insert a record into a JE database, with optional debug logging.
   * This is a simple wrapper around the JE Database.put method.
   * @param database The JE database handle.
   * @param txn The JE transaction handle, or null if none.
   * @param key The record key.
   * @param data The record value.
   * @return The operation status.
   * @throws DatabaseException If an error occurs in the JE operation.
   */
  public static OperationStatus put(Database database, Transaction txn,
                                    DatabaseEntry key, DatabaseEntry data)
       throws DatabaseException
  {
    OperationStatus status = database.put(txn, key, data);
    if (debugEnabled())
    {
      debugJEAccess(DebugLogLevel.VERBOSE, status, database, txn, key, data);
    }
    return status;
  }

  /**
   * Replace or insert a record into a JE database through a cursor, with
   * optional debug logging. This is a simple wrapper around the JE Cursor.put
   * method.
   * @param cursor The JE cursor handle.
   * @param key The record key.
   * @param data The record value.
   * @return The operation status.
   * @throws DatabaseException If an error occurs in the JE operation.
   */
  public static OperationStatus cursorPut(Cursor cursor,
                                          DatabaseEntry key,
                                          DatabaseEntry data)
       throws DatabaseException
  {
    OperationStatus status = cursor.put(key, data);
    if (debugEnabled())
    {
      debugJEAccess(DebugLogLevel.VERBOSE, status,
                    cursor.getDatabase(), null, key, data);
    }
    return status;
  }

  /**
   * Read a record from a JE database, with optional debug logging. This is a
   * simple wrapper around the JE Database.get method.
   * @param database The JE database handle.
   * @param txn The JE transaction handle, or null if none.
   * @param key The key of the record to be read.
   * @param data The record value returned as output. Its byte array does not
   * need to be initialized by the caller.
   * @param lockMode The JE locking mode to be used for the read.
   * @return The operation status.
   * @throws DatabaseException If an error occurs in the JE operation.
   */
  public static OperationStatus read(Database database, Transaction txn,
                              DatabaseEntry key, DatabaseEntry data,
                              LockMode lockMode)
       throws DatabaseException
  {
    OperationStatus status = database.get(txn, key, data, lockMode);
    if (debugEnabled())
    {
      debugJEAccess(DebugLogLevel.VERBOSE, status, database, txn, key, data);
    }
    return status;
  }

  /**
   * Read a record from a JE database through a cursor, with optional debug
   * logging. This is a simple wrapper around the JE Cursor.getSearchKey method.
   * @param cursor The JE cursor handle.
   * @param key The key of the record to be read.
   * @param data The record value returned as output. Its byte array does not
   * need to be initialized by the caller.
   * @param lockMode The JE locking mode to be used for the read.
   * @return The operation status.
   * @throws DatabaseException If an error occurs in the JE operation.
   */
  public static OperationStatus cursorRead(Cursor cursor,
                                           DatabaseEntry key,
                                           DatabaseEntry data,
                                           LockMode lockMode)
       throws DatabaseException
  {
    OperationStatus status = cursor.getSearchKey(key, data, lockMode);
    if (debugEnabled())
    {
      debugJEAccess(DebugLogLevel.VERBOSE, status,
                    cursor.getDatabase(), null, key, data);
    }
    return status;
  }

  /**
   * Delete a record from a JE database, with optional debug logging. This is a
   * simple wrapper around the JE Database.delete method.
   * @param database The JE database handle.
   * @param txn The JE transaction handle, or null if none.
   * @param key The key of the record to be read.
   * @return The operation status.
   * @throws DatabaseException If an error occurs in the JE operation.
   */
  public static OperationStatus delete(Database database, Transaction txn,
                                       DatabaseEntry key)
       throws DatabaseException
  {
    OperationStatus status = database.delete(txn, key);
    if (debugEnabled())
    {
      debugJEAccess(DebugLogLevel.VERBOSE, status, database, txn, key, null);
    }
    return status;
  }

  /**
   * Get the count of key/data pairs in the database in a JE database.
   * This is a simple wrapper around the JE Database.count method.
   * @param database the JE database handle.
   * @return The count of key/data pairs in the database.
   * @throws DatabaseException If an error occurs in the JE operation.
   */
  public static long count(Database database) throws DatabaseException
  {
    long count = database.count();
    if (debugEnabled())
    {
      debugJEAccess(DebugLogLevel.VERBOSE, OperationStatus.SUCCESS,
                    database, null, null, null);
    }
    return count;
  }

  /**
   * Remove a database from disk.
   *
   * @param name The short database name, to which the entryContainer name will
   * be added.
   * @throws DatabaseException If an error occurs while attempting to delete the
   * database.
   */
  public void removeDatabase(String name) throws DatabaseException
  {
    StringBuilder builder = new StringBuilder();
    buildDatabaseName(builder, name);
    String fullName = builder.toString();
    env.removeDatabase(null, fullName);
  }

  /**
   * This method constructs a container name from a base DN. Only alphanumeric
   * characters are preserved, all other characters are replaced with an
   * underscore.
   *
   * @return The container name for the base DN.
   */
  public String getContainerName()
  {
    String normStr = baseDN.toNormalizedString();
    StringBuilder builder = new StringBuilder(normStr.length());
    for (int i = 0; i < normStr.length(); i++)
    {
      char ch = normStr.charAt(i);
      if (Character.isLetterOrDigit(ch))
      {
        builder.append(ch);
      }
      else
      {
        builder.append('_');
      }
    }
    return builder.toString();
  }

  /**
   * Get the baseDN this entry container is responsible for.
   *
   * @return The Base DN for this entry container.
   */
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
  public DN getParentWithinBase(DN dn)
  {
    if (dn.equals(baseDN))
    {
      return null;
    }
    return dn.getParent();
  }

}
