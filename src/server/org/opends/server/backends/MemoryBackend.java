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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.backends;



import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Set;

import org.opends.messages.Message;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.std.server.MemoryBackendCfg;
import org.opends.server.api.Backend;
import org.opends.server.config.ConfigException;
import org.opends.server.controls.SubtreeDeleteControl;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.AttributeType;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.Control;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.IndexType;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.Validator;

import static org.opends.messages.BackendMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a very simple backend that stores its information in
 * memory.  This is primarily intended for testing purposes with small data
 * sets, as it does not have any indexing mechanism such as would be required to
 * achieve high performance with large data sets.  It is also heavily
 * synchronized for simplicity at the expense of performance, rather than
 * providing a more fine-grained locking mechanism.
 * <BR><BR>
 * Entries stored in this backend are held in a
 * <CODE>LinkedHashMap&lt;DN,Entry&gt;</CODE> object, which ensures that the
 * order in which you iterate over the entries is the same as the order in which
 * they were inserted.  By combining this with the constraint that no entry can
 * be added before its parent, you can ensure that iterating through the entries
 * will always process the parent entries before their children, which is
 * important for both search result processing and LDIF exports.
 * <BR><BR>
 * As mentioned above, no data indexing is performed, so all non-baseObject
 * searches require iteration through the entire data set.  If this is to become
 * a more general-purpose backend, then additional
 * <CODE>HashMap&lt;ByteString,Set&lt;DN&gt;&gt;</CODE> objects could be used
 * to provide that capability.
 * <BR><BR>
 * There is actually one index that does get maintained within this backend,
 * which is a mapping between the DN of an entry and the DNs of any immediate
 * children of that entry.  This is needed to efficiently determine whether an
 * entry has any children (which must not be the case for delete operations).
 */
public class MemoryBackend
       extends Backend
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  // The base DNs for this backend.
  private DN[] baseDNs;

  // The mapping between parent DNs and their immediate children.
  private HashMap<DN,HashSet<DN>> childDNs;

  // The base DNs for this backend, in a hash set.
  private HashSet<DN> baseDNSet;

  // The set of supported controls for this backend.
  private HashSet<String> supportedControls;

  // The set of supported features for this backend.
  private HashSet<String> supportedFeatures;

  // The mapping between entry DNs and the corresponding entries.
  private LinkedHashMap<DN,Entry> entryMap;



  /**
   * Creates a new backend with the provided information.  All backend
   * implementations must implement a default constructor that use
   * <CODE>super()</CODE> to invoke this constructor.
   */
  public MemoryBackend()
  {
    super();

    // Perform all initialization in initializeBackend.
  }


  /**
   * Set the base DNs for this backend.  This is used by the unit tests
   * to set the base DNs without having to provide a configuration
   * object when initializing the backend.
   * @param baseDNs The set of base DNs to be served by this memory backend.
   */
  public void setBaseDNs(DN[] baseDNs)
  {
    this.baseDNs = baseDNs;
  }


  /**
   * {@inheritDoc}
   */
  @Override()
  public void configureBackend(Configuration config)
         throws ConfigException
  {
    if (config != null)
    {
      Validator.ensureTrue(config instanceof MemoryBackendCfg);
      MemoryBackendCfg cfg = (MemoryBackendCfg)config;
      DN[] baseDNs = new DN[cfg.getBaseDN().size()];
      cfg.getBaseDN().toArray(baseDNs);
      setBaseDNs(baseDNs);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized void initializeBackend()
       throws ConfigException, InitializationException
  {
    // We won't support anything other than exactly one base DN in this
    // implementation.  If we were to add such support in the future, we would
    // likely want to separate the data for each base DN into a separate entry
    // map.
    if ((baseDNs == null) || (baseDNs.length != 1))
    {
      Message message = ERR_MEMORYBACKEND_REQUIRE_EXACTLY_ONE_BASE.get();
      throw new ConfigException(message);
    }

    baseDNSet = new HashSet<DN>();
    for (DN dn : baseDNs)
    {
      baseDNSet.add(dn);
    }

    entryMap = new LinkedHashMap<DN,Entry>();
    childDNs = new HashMap<DN,HashSet<DN>>();

    supportedControls = new HashSet<String>();
    supportedControls.add(OID_SUBTREE_DELETE_CONTROL);

    supportedFeatures = new HashSet<String>();

    for (DN dn : baseDNs)
    {
      try
      {
        DirectoryServer.registerBaseDN(dn, this, false);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message = ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(
            dn.toString(), getExceptionMessage(e));
        throw new InitializationException(message, e);
      }
    }
  }



  /**
   * Removes any data that may have been stored in this backend.
   */
  public synchronized void clearMemoryBackend()
  {
    entryMap.clear();
    childDNs.clear();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized void finalizeBackend()
  {
    clearMemoryBackend();

    for (DN dn : baseDNs)
    {
      try
      {
        DirectoryServer.deregisterBaseDN(dn);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public DN[] getBaseDNs()
  {
    return baseDNs;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized long getEntryCount()
  {
    if (entryMap != null)
    {
      return entryMap.size();
    }

    return -1;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isLocal()
  {
    // For the purposes of this method, this is a local backend.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isIndexed(AttributeType attributeType, IndexType indexType)
  {
    // All searches in this backend will always be considered indexed.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public synchronized ConditionResult hasSubordinates(DN entryDN)
         throws DirectoryException
  {
    long ret = numSubordinates(entryDN, false);
    if(ret < 0)
    {
      return ConditionResult.UNDEFINED;
    }
    else if(ret == 0)
    {
      return ConditionResult.FALSE;
    }
    else
    {
      return ConditionResult.TRUE;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized long numSubordinates(DN entryDN, boolean subtree)
         throws DirectoryException
  {
    // Try to look up the immediate children for the DN
    Set<DN> children = childDNs.get(entryDN);
    if (children == null)
    {
      if(entryMap.get(entryDN) != null)
      {
        // The entry does exist but just no children.
        return 0;
      }
      return -1;
    }

    if(!subtree)
    {
      return children.size();
    }
    else
    {
      long count = 0;
      for(DN child : children)
      {
        count += numSubordinates(child, true);
        count++;
      }
      return count;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized Entry getEntry(DN entryDN)
  {
    Entry entry = entryMap.get(entryDN);
    if (entry != null)
    {
      entry = entry.duplicate(true);
    }

    return entry;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized boolean entryExists(DN entryDN)
  {
    return entryMap.containsKey(entryDN);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized void addEntry(Entry entry, AddOperation addOperation)
         throws DirectoryException
  {
    Entry e = entry.duplicate(false);

    // See if the target entry already exists.  If so, then fail.
    DN entryDN = e.getDN();
    if (entryMap.containsKey(entryDN))
    {
      Message message =
          ERR_MEMORYBACKEND_ENTRY_ALREADY_EXISTS.get(String.valueOf(entryDN));
      throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS, message);
    }


    // If the entry is one of the base DNs, then add it.
    if (baseDNSet.contains(entryDN))
    {
      entryMap.put(entryDN, e);
      return;
    }


    // Get the parent DN and ensure that it exists in the backend.
    DN parentDN = entryDN.getParentDNInSuffix();
    if (parentDN == null)
    {
      Message message =
          ERR_MEMORYBACKEND_ENTRY_DOESNT_BELONG.get(String.valueOf(entryDN));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
    }
    else if (! entryMap.containsKey(parentDN))
    {
      Message message = ERR_MEMORYBACKEND_PARENT_DOESNT_EXIST.get(
          String.valueOf(entryDN), String.valueOf(parentDN));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
    }

    entryMap.put(entryDN, e);
    HashSet<DN> children = childDNs.get(parentDN);
    if (children == null)
    {
      children = new HashSet<DN>();
      childDNs.put(parentDN, children);
    }

    children.add(entryDN);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized void deleteEntry(DN entryDN,
                                       DeleteOperation deleteOperation)
         throws DirectoryException
  {
    // Make sure the entry exists.  If not, then throw an exception.
    if (! entryMap.containsKey(entryDN))
    {
      Message message =
          ERR_MEMORYBACKEND_ENTRY_DOESNT_EXIST.get(String.valueOf(entryDN));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
    }


    // Check to see if the entry contains a subtree delete control.
    boolean subtreeDelete = false;

    if (deleteOperation != null
        && deleteOperation
            .getRequestControl(SubtreeDeleteControl.DECODER) != null)
    {
      subtreeDelete = true;
    }

    HashSet<DN> children = childDNs.get(entryDN);
    if (subtreeDelete)
    {
      if (children != null)
      {
        HashSet<DN> childrenCopy = new HashSet<DN>(children);
        for (DN childDN : childrenCopy)
        {
          try
          {
            deleteEntry(childDN, null);
          }
          catch (Exception e)
          {
            // This shouldn't happen, but we want the delete to continue anyway
            // so just ignore it if it does for some reason.
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
          }
        }
      }
    }
    else
    {
      // Make sure the entry doesn't have any children.  If it does, then throw
      // an exception.
      if ((children != null) && (! children.isEmpty()))
      {
        Message message = ERR_MEMORYBACKEND_CANNOT_DELETE_ENTRY_WITH_CHILDREN.
            get(String.valueOf(entryDN));
        throw new DirectoryException(
                ResultCode.NOT_ALLOWED_ON_NONLEAF, message);
      }
    }


    // Remove the entry from the backend.  Also remove the reference to it from
    // its parent, if applicable.
    childDNs.remove(entryDN);
    entryMap.remove(entryDN);

    DN parentDN = entryDN.getParentDNInSuffix();
    if (parentDN != null)
    {
      HashSet<DN> parentsChildren = childDNs.get(parentDN);
      if (parentsChildren != null)
      {
        parentsChildren.remove(entryDN);
        if (parentsChildren.isEmpty())
        {
          childDNs.remove(parentDN);
        }
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized void replaceEntry(Entry oldEntry, Entry newEntry,
      ModifyOperation modifyOperation) throws DirectoryException
  {
    Entry e = newEntry.duplicate(false);

    // Make sure the entry exists.  If not, then throw an exception.
    DN entryDN = e.getDN();
    if (! entryMap.containsKey(entryDN))
    {
      Message message =
          ERR_MEMORYBACKEND_ENTRY_DOESNT_EXIST.get(String.valueOf(entryDN));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
    }


    // Replace the old entry with the new one.
    entryMap.put(entryDN, e);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized void renameEntry(DN currentDN, Entry entry,
                                       ModifyDNOperation modifyDNOperation)
         throws DirectoryException
  {
    Entry e = entry.duplicate(false);

    // Make sure that the target entry exists.
    if (! entryMap.containsKey(currentDN))
    {
      Message message =
          ERR_MEMORYBACKEND_ENTRY_DOESNT_EXIST.get(String.valueOf(currentDN));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
    }


    // Make sure that the target entry doesn't have any children.
    HashSet<DN> children  = childDNs.get(currentDN);
    if (children != null)
    {
      if (children.isEmpty())
      {
        childDNs.remove(currentDN);
      }
      else
      {
        Message message = ERR_MEMORYBACKEND_CANNOT_RENAME_ENRY_WITH_CHILDREN.
            get(String.valueOf(currentDN));
        throw new DirectoryException(
                ResultCode.NOT_ALLOWED_ON_NONLEAF, message);
      }
    }


    // Make sure that no entry exists with the new DN.
    if (entryMap.containsKey(e.getDN()))
    {
      Message message =
          ERR_MEMORYBACKEND_ENTRY_ALREADY_EXISTS.get(String.valueOf(e.getDN()));
      throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS, message);
    }


    // Make sure that the new DN is in this backend.
    boolean matchFound = false;
    for (DN dn : baseDNs)
    {
      if (dn.isAncestorOf(e.getDN()))
      {
        matchFound = true;
        break;
      }
    }

    if (! matchFound)
    {
      Message message = ERR_MEMORYBACKEND_CANNOT_RENAME_TO_ANOTHER_BACKEND.get(
          String.valueOf(currentDN));
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }


    // Make sure that the parent of the new entry exists.
    DN parentDN = e.getDN().getParentDNInSuffix();
    if ((parentDN == null) || (! entryMap.containsKey(parentDN)))
    {
      Message message = ERR_MEMORYBACKEND_RENAME_PARENT_DOESNT_EXIST.get(
          String.valueOf(currentDN), String.valueOf(parentDN));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
    }


    // Delete the current entry and add the new one.
    deleteEntry(currentDN, null);
    addEntry(e, null);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized void search(SearchOperation searchOperation)
         throws DirectoryException
  {
    // Get the base DN, scope, and filter for the search.
    DN           baseDN = searchOperation.getBaseDN();
    SearchScope  scope  = searchOperation.getScope();
    SearchFilter filter = searchOperation.getFilter();


    // Make sure the base entry exists if it's supposed to be in this backend.
    Entry baseEntry = entryMap.get(baseDN);
    if ((baseEntry == null) && handlesEntry(baseDN))
    {
      DN matchedDN = baseDN.getParentDNInSuffix();
      while (matchedDN != null)
      {
        if (entryMap.containsKey(matchedDN))
        {
          break;
        }

        matchedDN = matchedDN.getParentDNInSuffix();
      }

      Message message =
          ERR_MEMORYBACKEND_ENTRY_DOESNT_EXIST.get(String.valueOf(baseDN));
      throw new DirectoryException(
              ResultCode.NO_SUCH_OBJECT, message, matchedDN, null);
    }

    if (baseEntry != null)
    {
      baseEntry = baseEntry.duplicate(true);
    }


    // If it's a base-level search, then just get that entry and return it if it
    // matches the filter.
    if (scope == SearchScope.BASE_OBJECT)
    {
      if (filter.matchesEntry(baseEntry))
      {
        searchOperation.returnEntry(baseEntry, new LinkedList<Control>());
      }
    }
    else
    {
      // Walk through all entries and send the ones that match.
      for (Entry e : entryMap.values())
      {
        e = e.duplicate(true);
        if (e.matchesBaseAndScope(baseDN, scope) && filter.matchesEntry(e))
        {
          searchOperation.returnEntry(e, new LinkedList<Control>());
        }
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public HashSet<String> getSupportedControls()
  {
    return supportedControls;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public HashSet<String> getSupportedFeatures()
  {
    return supportedFeatures;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsLDIFExport()
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized void exportLDIF(LDIFExportConfig exportConfig)
         throws DirectoryException
  {
    // Create the LDIF writer.
    LDIFWriter ldifWriter;
    try
    {
      ldifWriter = new LDIFWriter(exportConfig);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_MEMORYBACKEND_CANNOT_CREATE_LDIF_WRITER.get(String.valueOf(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
    }


    // Walk through all the entries and write them to LDIF.
    DN entryDN = null;
    try
    {
      for (Entry entry : entryMap.values())
      {
        entryDN = entry.getDN();
        ldifWriter.writeEntry(entry);
      }
    }
    catch (Exception e)
    {
      Message message = ERR_MEMORYBACKEND_CANNOT_WRITE_ENTRY_TO_LDIF.get(
          String.valueOf(entryDN), String.valueOf(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
    }
    finally
    {
      try
      {
        ldifWriter.close();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsLDIFImport()
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized LDIFImportResult importLDIF(LDIFImportConfig importConfig)
         throws DirectoryException
  {
    clearMemoryBackend();

    LDIFReader reader;
    try
    {
      reader = new LDIFReader(importConfig);
    }
    catch (Exception e)
    {
      Message message =
          ERR_MEMORYBACKEND_CANNOT_CREATE_LDIF_READER.get(String.valueOf(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
    }


    try
    {
      while (true)
      {
        Entry e = null;
        try
        {
          e = reader.readEntry();
          if (e == null)
          {
            break;
          }
        }
        catch (LDIFException le)
        {
          if (! le.canContinueReading())
          {
            Message message =
                ERR_MEMORYBACKEND_ERROR_READING_LDIF.get(String.valueOf(e));
            throw new DirectoryException(
                           DirectoryServer.getServerErrorResultCode(),
                           message, le);
          }
          else
          {
            continue;
          }
        }

        try
        {
          addEntry(e, null);
        }
        catch (DirectoryException de)
        {
          reader.rejectLastEntry(de.getMessageObject());
        }
      }

      return new LDIFImportResult(reader.getEntriesRead(),
                                  reader.getEntriesRejected(),
                                  reader.getEntriesIgnored());
    }
    catch (DirectoryException de)
    {
      throw de;
    }
    catch (Exception e)
    {
      Message message =
          ERR_MEMORYBACKEND_ERROR_DURING_IMPORT.get(String.valueOf(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
    }
    finally
    {
      reader.close();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsBackup()
  {
    // This backend does not provide a backup/restore mechanism.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsBackup(BackupConfig backupConfig,
                                StringBuilder unsupportedReason)
  {
    // This backend does not provide a backup/restore mechanism.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void createBackup(BackupConfig backupConfig)
         throws DirectoryException
  {
    Message message = ERR_MEMORYBACKEND_BACKUP_RESTORE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void removeBackup(BackupDirectory backupDirectory,
                           String backupID)
         throws DirectoryException
  {
    Message message = ERR_MEMORYBACKEND_BACKUP_RESTORE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsRestore()
  {
    // This backend does not provide a backup/restore mechanism.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void restoreBackup(RestoreConfig restoreConfig)
         throws DirectoryException
  {
    Message message = ERR_MEMORYBACKEND_BACKUP_RESTORE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void preloadEntryCache() throws UnsupportedOperationException {
    throw new UnsupportedOperationException("Operation not supported.");
  }
}

