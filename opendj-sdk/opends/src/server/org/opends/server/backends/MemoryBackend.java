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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.backends;



import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;

import org.opends.server.api.Backend;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchScope;
import org.opends.server.types.SearchFilter;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.BackendMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;



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
 * Modify DN operations are not currently allowed, but if such support is added
 * in the future, then this mapping would play an integral role in that process
 * as well.
 */
public class MemoryBackend
       extends Backend
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.backends.MemoryBackend";



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

    assert debugConstructor(CLASS_NAME);


    // Perform all initialization in initializeBackend.
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void initializeBackend(ConfigEntry configEntry,
                                             DN[] baseDNs)
         throws ConfigException
  {
    assert debugEnter(CLASS_NAME, "initializeBackend",
                      String.valueOf(configEntry), String.valueOf(baseDNs));


    // We won't support anything other than exactly one base DN in this
    // implementation.  If we were to add such support in the future, we would
    // likely want to separate the data for each base DN into a separate entry
    // map.
    if ((baseDNs == null) || (baseDNs.length != 1))
    {
      int    msgID   = MSGID_MEMORYBACKEND_REQUIRE_EXACTLY_ONE_BASE;
      String message = getMessage(msgID);
      throw new ConfigException(msgID, message);
    }


    this.baseDNs = baseDNs;

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
      DirectoryServer.registerSuffix(dn, this);
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
  public synchronized void finalizeBackend()
  {
    assert debugEnter(CLASS_NAME, "finalizeBackend");

    clearMemoryBackend();
  }



  /**
   * {@inheritDoc}
   */
  public DN[] getBaseDNs()
  {
    assert debugEnter(CLASS_NAME, "getBaseDNs");

    return baseDNs;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isLocal()
  {
    assert debugEnter(CLASS_NAME, "isLocal");

    // For the purposes of this method, this is a local backend.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public synchronized Entry getEntry(DN entryDN)
  {
    assert debugEnter(CLASS_NAME, "getEntry", String.valueOf(entryDN));

    return entryMap.get(entryDN);
  }



  /**
   * {@inheritDoc}
   */
  public synchronized boolean entryExists(DN entryDN)
  {
    assert debugEnter(CLASS_NAME, "entryExists", String.valueOf(entryDN));

    return entryMap.containsKey(entryDN);
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void addEntry(Entry entry, AddOperation addOperation)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "addEntry", String.valueOf(entry),
                      String.valueOf(addOperation));


    // See if the target entry already exists.  If so, then fail.
    DN entryDN = entry.getDN();
    if (entryMap.containsKey(entryDN))
    {
      int    msgID   = MSGID_MEMORYBACKEND_ENTRY_ALREADY_EXISTS;
      String message = getMessage(msgID, String.valueOf(entryDN));
      throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS, message,
                                   msgID);
    }


    // If the entry is one of the base DNs, then add it.
    if (baseDNSet.contains(entryDN))
    {
      entryMap.put(entryDN, entry);
      return;
    }


    // Get the parent DN and ensure that it exists in the backend.
    DN parentDN = entryDN.getParentDNInSuffix();
    if (parentDN == null)
    {
      int    msgID   = MSGID_MEMORYBACKEND_ENTRY_DOESNT_BELONG;
      String message = getMessage(msgID, String.valueOf(entryDN));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, msgID);
    }
    else if (! entryMap.containsKey(parentDN))
    {
      int    msgID   = MSGID_MEMORYBACKEND_PARENT_DOESNT_EXIST;
      String message = getMessage(msgID, String.valueOf(entryDN),
                                  String.valueOf(parentDN));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, msgID);
    }

    entryMap.put(entryDN, entry);
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
  public synchronized void deleteEntry(DN entryDN,
                                       DeleteOperation deleteOperation)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "deleteEntry", String.valueOf(entryDN),
                      String.valueOf(deleteOperation));


    // Make sure the entry exists.  If not, then throw an exception.
    if (! entryMap.containsKey(entryDN))
    {
      int    msgID   = MSGID_MEMORYBACKEND_ENTRY_DOESNT_EXIST;
      String message = getMessage(msgID, String.valueOf(entryDN));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, msgID);
    }


    // Check to see if the entry contains a subtree delete control.
    boolean subtreeDelete = false;
    if (deleteOperation != null)
    {
      for (Control c : deleteOperation.getRequestControls())
      {
        if (c.getOID().equals(OID_SUBTREE_DELETE_CONTROL))
        {
          subtreeDelete = true;
        }
      }
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
            assert debugException(CLASS_NAME, "deleteEntry", e);
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
        int    msgID   = MSGID_MEMORYBACKEND_CANNOT_DELETE_ENTRY_WITH_CHILDREN;
        String message = getMessage(msgID, String.valueOf(entryDN));
        throw new DirectoryException(ResultCode.NOT_ALLOWED_ON_NONLEAF, message,
                                     msgID);
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
  public synchronized void replaceEntry(Entry entry,
                                        ModifyOperation modifyOperation)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "replaceEntry", String.valueOf(entry),
                      String.valueOf(modifyOperation));


    // Make sure the entry exists.  If not, then throw an exception.
    DN entryDN = entry.getDN();
    if (! entryMap.containsKey(entryDN))
    {
      int    msgID   = MSGID_MEMORYBACKEND_ENTRY_DOESNT_EXIST;
      String message = getMessage(msgID, String.valueOf(entryDN));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, msgID);
    }


    // Replace the old entry with the new one.
    entryMap.put(entryDN, entry);
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void renameEntry(DN currentDN, Entry entry,
                                       ModifyDNOperation modifyDNOperation)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "renameEntry", String.valueOf(currentDN),
                      String.valueOf(entry), String.valueOf(modifyDNOperation));


    // Make sure that the target entry exists.
    if (! entryMap.containsKey(currentDN))
    {
      int    msgID   = MSGID_MEMORYBACKEND_ENTRY_DOESNT_EXIST;
      String message = getMessage(msgID, String.valueOf(currentDN));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, msgID);
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
        int    msgID   = MSGID_MEMORYBACKEND_CANNOT_RENAME_ENRY_WITH_CHILDREN;
        String message = getMessage(msgID, String.valueOf(currentDN));
        throw new DirectoryException(ResultCode.NOT_ALLOWED_ON_NONLEAF, message,
                                     msgID);
      }
    }


    // Make sure that no entry exists with the new DN.
    if (entryMap.containsKey(entry.getDN()))
    {
      int    msgID   = MSGID_MEMORYBACKEND_ENTRY_ALREADY_EXISTS;
      String message = getMessage(msgID, String.valueOf(entry.getDN()));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, msgID);
    }


    // Make sure that the new DN is in this backend.
    boolean matchFound = false;
    for (DN dn : baseDNs)
    {
      if (dn.isAncestorOf(entry.getDN()))
      {
        matchFound = true;
        break;
      }
    }

    if (! matchFound)
    {
      int    msgID   = MSGID_MEMORYBACKEND_CANNOT_RENAME_TO_ANOTHER_BACKEND;
      String message = getMessage(msgID, String.valueOf(currentDN));
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                   msgID);
    }


    // Make sure that the parent of the new entry exists.
    DN parentDN = entry.getDN().getParentDNInSuffix();
    if ((parentDN == null) || (! entryMap.containsKey(parentDN)))
    {
      int    msgID   = MSGID_MEMORYBACKEND_RENAME_PARENT_DOESNT_EXIST;
      String message = getMessage(msgID, String.valueOf(currentDN),
                                  String.valueOf(parentDN));
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                   msgID);
    }


    // Delete the current entry and add the new one.
    deleteEntry(currentDN, null);
    addEntry(entry, null);
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void search(SearchOperation searchOperation)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "search", String.valueOf(searchOperation));


    // Get the base DN, scope, and filter for the search.
    DN           baseDN = searchOperation.getBaseDN();
    SearchScope  scope  = searchOperation.getScope();
    SearchFilter filter = searchOperation.getFilter();


    // If it's a base-level search, then just get that entry and return it if it
    // matches the filter.
    if (scope == SearchScope.BASE_OBJECT)
    {
      Entry entry = entryMap.get(baseDN);
      if (entry == null)
      {
        int    msgID   = MSGID_MEMORYBACKEND_ENTRY_DOESNT_EXIST;
        String message = getMessage(msgID, String.valueOf(baseDN));
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, msgID);
      }

      if (filter.matchesEntry(entry))
      {
        searchOperation.returnEntry(entry, new LinkedList<Control>());
      }
    }
    else
    {
      // Walk through all entries and send the ones that match.
      for (Entry e : entryMap.values())
      {
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
  public HashSet<String> getSupportedControls()
  {
    assert debugEnter(CLASS_NAME, "getSupportedControls");

    return supportedControls;
  }



  /**
   * {@inheritDoc}
   */
  public boolean supportsControl(String controlOID)
  {
    assert debugEnter(CLASS_NAME, "supportsControl",
                      String.valueOf(controlOID));

    return supportedControls.contains(controlOID);
  }



  /**
   * {@inheritDoc}
   */
  public HashSet<String> getSupportedFeatures()
  {
    assert debugEnter(CLASS_NAME, "getSupportedFeatures");

    return supportedFeatures;
  }



  /**
   * {@inheritDoc}
   */
  public boolean supportsFeature(String featureOID)
  {
    assert debugEnter(CLASS_NAME, "supportsFeature",
                      String.valueOf(featureOID));

    // This backend does not provide any special feature support.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public boolean supportsLDIFExport()
  {
    assert debugEnter(CLASS_NAME, "supportsLDIFExport");

    return true;
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void exportLDIF(ConfigEntry configEntry, DN[] baseDNs,
                                      LDIFExportConfig exportConfig)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "exportLDIF", String.valueOf(exportConfig));


    // Create the LDIF writer.
    LDIFWriter ldifWriter;
    try
    {
      ldifWriter = new LDIFWriter(exportConfig);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "exportLDIF", e);

      int    msgID   = MSGID_MEMORYBACKEND_CANNOT_CREATE_LDIF_WRITER;
      String message = getMessage(msgID, String.valueOf(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
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
      int    msgID   = MSGID_MEMORYBACKEND_CANNOT_WRITE_ENTRY_TO_LDIF;
      String message = getMessage(msgID, String.valueOf(entryDN),
                                  String.valueOf(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }
    finally
    {
      try
      {
        ldifWriter.close();
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "exportLDIF", e);
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean supportsLDIFImport()
  {
    assert debugEnter(CLASS_NAME, "supportsLDIFImport");

    return true;
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void importLDIF(ConfigEntry configEntry, DN[] baseDNs,
                                      LDIFImportConfig importConfig)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "importLDIF", String.valueOf(importConfig));


    clearMemoryBackend();

    LDIFReader reader;
    try
    {
      reader = new LDIFReader(importConfig);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_MEMORYBACKEND_CANNOT_CREATE_LDIF_READER;
      String message = getMessage(msgID, String.valueOf(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
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
            int    msgID   = MSGID_MEMORYBACKEND_ERROR_READING_LDIF;
            String message = getMessage(msgID, String.valueOf(e));
            throw new DirectoryException(
                           DirectoryServer.getServerErrorResultCode(),
                           message, msgID, le);
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
          reader.rejectLastEntry(de.getErrorMessage());
        }
      }
    }
    catch (DirectoryException de)
    {
      throw de;
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_MEMORYBACKEND_ERROR_DURING_IMPORT;
      String message = getMessage(msgID, String.valueOf(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }
    finally
    {
      reader.close();
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean supportsBackup()
  {
    assert debugEnter(CLASS_NAME, "supportsBackup");

    // This backend does not provide a backup/restore mechanism.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public boolean supportsBackup(BackupConfig backupConfig,
                                StringBuilder unsupportedReason)
  {
    assert debugEnter(CLASS_NAME, "supportsBackup");


    // This backend does not provide a backup/restore mechanism.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public void createBackup(ConfigEntry configEntry, BackupConfig backupConfig)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "createBackup", String.valueOf(backupConfig));


    int    msgID   = MSGID_MEMORYBACKEND_BACKUP_RESTORE_NOT_SUPPORTED;
    String message = getMessage(msgID);
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                 msgID);
  }



  /**
   * {@inheritDoc}
   */
  public void removeBackup(BackupDirectory backupDirectory,
                           String backupID)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "removeBackup",
                      String.valueOf(backupDirectory),
                      String.valueOf(backupID));


    int    msgID   = MSGID_MEMORYBACKEND_BACKUP_RESTORE_NOT_SUPPORTED;
    String message = getMessage(msgID);
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                 msgID);
  }



  /**
   * {@inheritDoc}
   */
  public boolean supportsRestore()
  {
    assert debugEnter(CLASS_NAME, "supportsRestore");


    // This backend does not provide a backup/restore mechanism.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public void restoreBackup(ConfigEntry configEntry,
                            RestoreConfig restoreConfig)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "restoreBackup",
                      String.valueOf(restoreConfig));


    int    msgID   = MSGID_MEMORYBACKEND_BACKUP_RESTORE_NOT_SUPPORTED;
    String message = getMessage(msgID);
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                 msgID);
  }
}

