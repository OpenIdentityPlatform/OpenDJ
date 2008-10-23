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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 */
package org.opends.server.backends;



import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.opends.messages.Message;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.LDIFBackendCfg;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.Backend;
import org.opends.server.config.ConfigException;
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
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.Control;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ExistingFileBehavior;
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
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides a backend implementation that stores the underlying data
 * in an LDIF file.  When the backend is initialized, the contents of the
 * backend are read into memory and all read operations are performed purely
 * from memory.  Write operations cause the underlying LDIF file to be
 * re-written on disk.
 */
public class LDIFBackend
       extends Backend
       implements ConfigurationChangeListener<LDIFBackendCfg>, AlertGenerator
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

  // The current configuration for this backend.
  private LDIFBackendCfg currentConfig;

  // The mapping between entry DNs and the corresponding entries.
  private LinkedHashMap<DN,Entry> entryMap;

  // A read-write lock used to protect access to this backend.
  private ReentrantReadWriteLock backendLock;

  // The path to the LDIF file containing the data for this backend.
  private String ldifFilePath;



  /**
   * Creates a new backend with the provided information.  All backend
   * implementations must implement a default constructor that use
   * <CODE>super()</CODE> to invoke this constructor.
   */
  public LDIFBackend()
  {
    super();

    entryMap = new LinkedHashMap<DN,Entry>();
    childDNs = new HashMap<DN,HashSet<DN>>();

    boolean useFairLocking =
         DirectoryServer.getEnvironmentConfig().getLockManagerFairOrdering();
    backendLock = new ReentrantReadWriteLock(useFairLocking);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializeBackend()
         throws ConfigException, InitializationException
  {
    // We won't support anything other than exactly one base DN in this
    // implementation.  If we were to add such support in the future, we would
    // likely want to separate the data for each base DN into a separate entry
    // map.
    if ((baseDNs == null) || (baseDNs.length != 1))
    {
      Message message = ERR_LDIF_BACKEND_MULTIPLE_BASE_DNS.get(
                             currentConfig.dn().toString());
      throw new ConfigException(message);
    }

    for (DN dn : baseDNs)
    {
      try
      {
        DirectoryServer.registerBaseDN(dn, this,
                                       currentConfig.isIsPrivateBackend());
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

    DirectoryServer.registerAlertGenerator(this);

    readLDIF();
  }



  /**
   * Reads the contents of the LDIF backing file into memory.
   *
   * @throws  InitializationException  If a problem occurs while reading the
   *                                   LDIF file.
   */
  private void readLDIF()
          throws InitializationException
  {
    File ldifFile = getFileForPath(ldifFilePath);
    if (! ldifFile.exists())
    {
      // This is fine.  We will just start with an empty backend.
      if (debugEnabled())
      {
        TRACER.debugInfo("LDIF backend starting empty because LDIF file " +
                         ldifFilePath + " does not exist");
      }

      entryMap.clear();
      childDNs.clear();
      return;
    }


    try
    {
      importLDIF(new LDIFImportConfig(ldifFile.getAbsolutePath()), false);
    }
    catch (DirectoryException de)
    {
      throw new InitializationException(de.getMessageObject(), de);
    }
  }



  /**
   * Writes the current set of entries to the target LDIF file.  The new LDIF
   * will first be created as a temporary file and then renamed into place.  The
   * caller must either hold the write lock for this backend, or must ensure
   * that it's in some other state that guarantees exclusive access to the data.
   *
   * @throws  DirectoryException  If a problem occurs that prevents the updated
   *                              LDIF from being written.
   */
  private void writeLDIF()
          throws DirectoryException
  {
    File ldifFile = getFileForPath(ldifFilePath);
    File tempFile = new File(ldifFile.getAbsolutePath() + ".new");
    File oldFile  = new File(ldifFile.getAbsolutePath() + ".old");


    // Write the new data to a temporary file.
    LDIFWriter writer;
    try
    {
      LDIFExportConfig exportConfig =
           new LDIFExportConfig(tempFile.getAbsolutePath(),
                                ExistingFileBehavior.OVERWRITE);
      writer = new LDIFWriter(exportConfig);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message m = ERR_LDIF_BACKEND_ERROR_CREATING_FILE.get(
                       tempFile.getAbsolutePath(),
                       currentConfig.dn().toString(),
                       stackTraceToSingleLineString(e));
      DirectoryServer.sendAlertNotification(this,
                           ALERT_TYPE_LDIF_BACKEND_CANNOT_WRITE_UPDATE, m);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   m, e);
    }


    for (Entry entry : entryMap.values())
    {
      try
      {
        writer.writeEntry(entry);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        try
        {
          writer.close();
        } catch (Exception e2) {}

        Message m = ERR_LDIF_BACKEND_ERROR_WRITING_FILE.get(
                         tempFile.getAbsolutePath(),
                         currentConfig.dn().toString(),
                         stackTraceToSingleLineString(e));
        DirectoryServer.sendAlertNotification(this,
                             ALERT_TYPE_LDIF_BACKEND_CANNOT_WRITE_UPDATE, m);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     m, e);
      }
    }

    try
    {
      writer.close();
    } catch (Exception e) {}


    // Rename the existing "live" file out of the way and move the new file
    // into place.
    try
    {
      if (oldFile.exists())
      {
        oldFile.delete();
      }
    } catch (Exception e) {}

    try
    {
      if (ldifFile.exists())
      {
        ldifFile.renameTo(oldFile);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }

    try
    {
      tempFile.renameTo(ldifFile);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message m = ERR_LDIF_BACKEND_ERROR_RENAMING_FILE.get(
                       tempFile.getAbsolutePath(),
                       ldifFile.getAbsolutePath(),
                       currentConfig.dn().toString(),
                       stackTraceToSingleLineString(e));
      DirectoryServer.sendAlertNotification(this,
                           ALERT_TYPE_LDIF_BACKEND_CANNOT_WRITE_UPDATE, m);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   m, e);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void finalizeBackend()
  {
    backendLock.writeLock().lock();

    try
    {
      currentConfig.removeLDIFChangeListener(this);
      DirectoryServer.deregisterAlertGenerator(this);

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
    finally
    {
      backendLock.writeLock().unlock();
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
  public long getEntryCount()
  {
    backendLock.readLock().lock();

    try
    {
      if (entryMap != null)
      {
        return entryMap.size();
      }

      return -1;
    }
    finally
    {
      backendLock.readLock().unlock();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isLocal()
  {
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
  @Override()
  public ConditionResult hasSubordinates(DN entryDN)
         throws DirectoryException
  {
    backendLock.readLock().lock();

    try
    {
      HashSet<DN> childDNSet = childDNs.get(entryDN);
      if ((childDNSet == null) || childDNSet.isEmpty())
      {
        // It could be that the entry doesn't exist, in which case we should
        // throw an exception.
        if (entryMap.containsKey(entryDN))
        {
          return ConditionResult.FALSE;
        }
        else
        {
          Message m = ERR_LDIF_BACKEND_HAS_SUBORDINATES_NO_SUCH_ENTRY.get(
                           String.valueOf(entryDN));
          throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, m);
        }
      }
      else
      {
        return ConditionResult.TRUE;
      }
    }
    finally
    {
      backendLock.readLock().unlock();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public long numSubordinates(DN entryDN, boolean subtree)
         throws DirectoryException
  {
    backendLock.readLock().lock();

    try
    {
      HashSet<DN> childDNSet = childDNs.get(entryDN);
      if ((childDNSet == null) || childDNSet.isEmpty())
      {
        // It could be that the entry doesn't exist, in which case we should
        // throw an exception.
        if (entryMap.containsKey(entryDN))
        {
          return 0L;
        }
        else
        {
          Message m = ERR_LDIF_BACKEND_NUM_SUBORDINATES_NO_SUCH_ENTRY.get(
                           String.valueOf(entryDN));
          throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, m);
        }
      }
      else
      {
        if(!subtree)
        {
          return childDNSet.size();
        }
        else
        {
          long count = 0;
          for(DN childDN : childDNSet)
          {
            count += numSubordinates(childDN, true);
            count ++;
          }
          return count;
        }

      }
    }
    finally
    {
      backendLock.readLock().unlock();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public Entry getEntry(DN entryDN)
  {
    backendLock.readLock().lock();

    try
    {
      return entryMap.get(entryDN);
    }
    finally
    {
      backendLock.readLock().unlock();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean entryExists(DN entryDN)
  {
    backendLock.readLock().lock();

    try
    {
      return entryMap.containsKey(entryDN);
    }
    finally
    {
      backendLock.readLock().unlock();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void addEntry(Entry entry, AddOperation addOperation)
         throws DirectoryException
  {
    backendLock.writeLock().lock();

    try
    {
      // Make sure that the target entry does not already exist, but that its
      // parent does exist (or that the entry being added is the base DN).
      DN entryDN = entry.getDN();
      if (entryMap.containsKey(entryDN))
      {
        Message m = ERR_LDIF_BACKEND_ADD_ALREADY_EXISTS.get(entryDN.toString());
        throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS, m);
      }

      if (baseDNSet.contains(entryDN))
      {
        entryMap.put(entryDN, entry.duplicate(false));
        writeLDIF();
        return;
      }
      else
      {
        DN parentDN = entryDN.getParentDNInSuffix();
        if ((parentDN != null) && entryMap.containsKey(parentDN))
        {
          entryMap.put(entryDN, entry.duplicate(false));

          HashSet<DN> childDNSet = childDNs.get(parentDN);
          if (childDNSet == null)
          {
            childDNSet = new HashSet<DN>();
            childDNs.put(parentDN, childDNSet);
          }
          childDNSet.add(entryDN);
          writeLDIF();
          return;
        }
        else
        {
          DN matchedDN = null;
          // BUG: parentDN can be null when entering the loop
          while (true)
          {
            parentDN = parentDN.getParentDNInSuffix();
            if (parentDN == null)
            {
              break;
            }

            if (entryMap.containsKey(parentDN))
            {
              matchedDN = parentDN;
              break;
            }
          }

          Message m =
               ERR_LDIF_BACKEND_ADD_MISSING_PARENT.get(entryDN.toString());
          throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, m, matchedDN,
                                       null);
        }
      }
    }
    finally
    {
      backendLock.writeLock().unlock();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
         throws DirectoryException
  {
    backendLock.writeLock().lock();

    try
    {
      // Get the DN of the target entry's parent, if it exists.  We'll need to
      // also remove the reference to the target entry from the parent's set of
      // children.
      DN parentDN = entryDN.getParentDNInSuffix();

      // Make sure that the target entry exists.  If not, then fail.
      if (! entryMap.containsKey(entryDN))
      {
        DN matchedDN = null;
        while (parentDN != null)
        {
          if (entryMap.containsKey(parentDN))
          {
            matchedDN = parentDN;
            break;
          }

          parentDN = parentDN.getParentDNInSuffix();
        }

        Message m =
             ERR_LDIF_BACKEND_DELETE_NO_SUCH_ENTRY.get(entryDN.toString());
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, m, matchedDN,
                                     null);
      }


      // See if the target entry has any children.  If so, then we'll only
      // delete it if the request contains the subtree delete control (in
      // which case we'll delete the entire subtree).
      HashSet<DN> childDNSet = childDNs.get(entryDN);
      if ((childDNSet == null) || childDNSet.isEmpty())
      {
        entryMap.remove(entryDN);
        childDNs.remove(entryDN);

        if (parentDN != null)
        {
          HashSet<DN> parentChildren = childDNs.get(parentDN);
          if (parentChildren != null)
          {
            parentChildren.remove(entryDN);
            if (parentChildren.isEmpty())
            {
              childDNs.remove(parentDN);
            }
          }
        }

        writeLDIF();
        return;
      }
      else
      {
        boolean subtreeDelete = false;
        for (Control c : deleteOperation.getRequestControls())
        {
          if (c.getOID().equals(OID_SUBTREE_DELETE_CONTROL))
          {
            subtreeDelete = true;
            break;
          }
        }

        if (! subtreeDelete)
        {
          Message m = ERR_LDIF_BACKEND_DELETE_NONLEAF.get(entryDN.toString());
          throw new DirectoryException(ResultCode.NOT_ALLOWED_ON_NONLEAF, m);
        }

        entryMap.remove(entryDN);
        childDNs.remove(entryDN);

        if (parentDN != null)
        {
          HashSet<DN> parentChildren = childDNs.get(parentDN);
          if (parentChildren != null)
          {
            parentChildren.remove(entryDN);
            if (parentChildren.isEmpty())
            {
              childDNs.remove(parentDN);
            }
          }
        }

        for (DN childDN : childDNSet)
        {
          subtreeDelete(childDN);
        }

        writeLDIF();
        return;
      }
    }
    finally
    {
      backendLock.writeLock().unlock();
    }
  }



  /**
   * Removes the specified entry and any subordinates that it may have from
   * the backend.  This method assumes that the caller holds the backend write
   * lock.
   *
   * @param  entryDN  The DN of the entry to remove, along with all of its
   *                  subordinate entries.
   */
  private void subtreeDelete(DN entryDN)
  {
    entryMap.remove(entryDN);
    HashSet<DN> childDNSet = childDNs.remove(entryDN);
    if (childDNSet != null)
    {
      for (DN childDN : childDNSet)
      {
        subtreeDelete(childDN);
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void replaceEntry(Entry oldEntry, Entry newEntry,
      ModifyOperation modifyOperation) throws DirectoryException
  {
    backendLock.writeLock().lock();

    try
    {
      // Make sure that the target entry exists.  If not, then fail.
      DN entryDN = newEntry.getDN();
      if (! entryMap.containsKey(entryDN))
      {
        DN matchedDN = null;
        DN parentDN = entryDN.getParentDNInSuffix();
        while (parentDN != null)
        {
          if (entryMap.containsKey(parentDN))
          {
            matchedDN = parentDN;
            break;
          }

          parentDN = parentDN.getParentDNInSuffix();
        }

        Message m =
             ERR_LDIF_BACKEND_MODIFY_NO_SUCH_ENTRY.get(entryDN.toString());
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, m, matchedDN,
                                     null);
      }

      entryMap.put(entryDN, newEntry.duplicate(false));
      writeLDIF();
      return;
    }
    finally
    {
      backendLock.writeLock().unlock();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void renameEntry(DN currentDN, Entry entry,
                          ModifyDNOperation modifyDNOperation)
         throws DirectoryException
  {
    backendLock.writeLock().lock();

    try
    {
      // Make sure that the original entry exists and that the new entry doesn't
      // exist but its parent does.
      DN newDN = entry.getDN();
      if (! entryMap.containsKey(currentDN))
      {
        DN matchedDN = null;
        DN parentDN = currentDN.getParentDNInSuffix();
        while (parentDN != null)
        {
          if (entryMap.containsKey(parentDN))
          {
            matchedDN = parentDN;
            break;
          }

          parentDN = parentDN.getParentDNInSuffix();
        }

        Message m = ERR_LDIF_BACKEND_MODDN_NO_SUCH_SOURCE_ENTRY.get(
                         currentDN.toString());
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, m, matchedDN,
                                     null);
      }

      if (entryMap.containsKey(newDN))
      {
        Message m = ERR_LDIF_BACKEND_MODDN_TARGET_ENTRY_ALREADY_EXISTS.get(
                         newDN.toString());
        throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS, m);
      }

      DN newParentDN = newDN.getParentDNInSuffix();
      if (! entryMap.containsKey(newParentDN))
      {
        Message m = ERR_LDIF_BACKEND_MODDN_NEW_PARENT_DOESNT_EXIST.get(
                         String.valueOf(newParentDN));
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, m);
      }

      // Remove the entry from the list of children for the old parent and
      // add the new entry DN to the set of children for the new parent.
      DN oldParentDN = currentDN.getParentDNInSuffix();
      HashSet<DN> parentChildDNs = childDNs.get(oldParentDN);
      if (parentChildDNs != null)
      {
        parentChildDNs.remove(currentDN);
        if (parentChildDNs.isEmpty() &&
            (modifyDNOperation.getNewSuperior() != null))
        {
          childDNs.remove(oldParentDN);
        }
      }

      parentChildDNs = childDNs.get(newParentDN);
      if (parentChildDNs == null)
      {
        parentChildDNs = new HashSet<DN>();
        childDNs.put(newParentDN, parentChildDNs);
      }
      parentChildDNs.add(newDN);


      // If the entry has children, then we'll need to work on the whole
      // subtree.  Otherwise, just work on the target entry.
      Set<DN> childDNSet = childDNs.remove(currentDN);
      if ((childDNSet == null) || childDNSet.isEmpty())
      {
        entryMap.remove(currentDN);
        entryMap.put(newDN, entry.duplicate(false));
        writeLDIF();
        return;
      }
      else
      {
        entryMap.remove(currentDN);
        entryMap.put(newDN, entry.duplicate(false));
        for (DN childDN : childDNSet)
        {
          subtreeRename(childDN, newDN);
        }
        writeLDIF();
        return;
      }
    }
    finally
    {
      backendLock.writeLock().unlock();
    }
  }



  /**
   * Moves the specified entry and all of its children so that they are
   * appropriately placed below the given new parent DN.  This method assumes
   * that the caller holds the backend write lock.
   *
   * @param  entryDN      The DN of the entry to move/rename.
   * @param  newParentDN  The DN of the new parent under which the entry should
   *                      be placed.
   */
  private void subtreeRename(DN entryDN, DN newParentDN)
  {
    Set<DN> childDNSet = childDNs.remove(entryDN);
    DN newEntryDN = new DN(entryDN.getRDN(), newParentDN);

    Entry oldEntry = entryMap.remove(entryDN);
    if (oldEntry == null)
    {
      // This should never happen.
      if (debugEnabled())
      {
        TRACER.debugWarning("Subtree rename encountered entry DN " +
                            entryDN.toString() + " for nonexistent entry.");
      }
      return;
    }

    Entry newEntry = oldEntry.duplicate(false);
    newEntry.setDN(newEntryDN);
    entryMap.put(newEntryDN, newEntry);

    HashSet<DN> parentChildren = childDNs.get(newParentDN);
    if (parentChildren == null)
    {
      parentChildren = new HashSet<DN>();
      childDNs.put(newParentDN, parentChildren);
    }
    parentChildren.add(newEntryDN);

    if (childDNSet != null)
    {
      for (DN childDN : childDNSet)
      {
        subtreeRename(childDN, newEntryDN);
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void search(SearchOperation searchOperation)
         throws DirectoryException
  {
    backendLock.readLock().lock();

    try
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

        Message m = ERR_LDIF_BACKEND_SEARCH_NO_SUCH_BASE.get(
                         String.valueOf(baseDN));
        throw new DirectoryException(
                ResultCode.NO_SUCH_OBJECT, m, matchedDN, null);
      }

      if (baseEntry != null)
      {
        baseEntry = baseEntry.duplicate(true);
      }

      // If it's a base-level search, then just get that entry and return it if
      // it matches the filter.
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
    finally
    {
      backendLock.readLock().unlock();
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
  public void exportLDIF(LDIFExportConfig exportConfig)
         throws DirectoryException
  {
    backendLock.readLock().lock();

    try
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

        Message m = ERR_LDIF_BACKEND_CANNOT_CREATE_LDIF_WRITER.get(
                         stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     m, e);
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
        Message m = ERR_LDIF_BACKEND_CANNOT_WRITE_ENTRY_TO_LDIF.get(
                         String.valueOf(entryDN),
                         stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     m, e);
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
    finally
    {
      backendLock.readLock().unlock();
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
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig)
         throws DirectoryException
  {
    return importLDIF(importConfig, true);
  }



  /**
   * Processes an LDIF import operation, optionally writing the resulting LDIF
   * to disk.
   *
   * @param  importConfig  The LDIF import configuration.
   * @param  writeLDIF     Indicates whether the LDIF backing file for this
   *                       backend should be updated when the import is
   *                       complete.  This should only be {@code false} when
   *                       reading the LDIF as the backend is coming online.
   */
  private LDIFImportResult importLDIF(LDIFImportConfig importConfig,
                                     boolean writeLDIF)
         throws DirectoryException
  {
    backendLock.writeLock().lock();

    try
    {
      LDIFReader reader;
      try
      {
        reader = new LDIFReader(importConfig);
      }
      catch (Exception e)
      {
        Message m = ERR_LDIF_BACKEND_CANNOT_CREATE_LDIF_READER.get(
                         stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     m, e);
      }

      entryMap.clear();
      childDNs.clear();


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
              Message m = ERR_LDIF_BACKEND_ERROR_READING_LDIF.get(
                               stackTraceToSingleLineString(le));
              throw new DirectoryException(
                             DirectoryServer.getServerErrorResultCode(), m, le);
            }
            else
            {
              continue;
            }
          }

          // Make sure that we don't already have an entry with the same DN.  If
          // a duplicate is encountered, then log a message and continue.
          DN entryDN = e.getDN();
          if (entryMap.containsKey(entryDN))
          {
            Message m = ERR_LDIF_BACKEND_DUPLICATE_ENTRY.get(ldifFilePath,
                             currentConfig.dn().toString(), entryDN.toString());
            logError(m);
            reader.rejectLastEntry(m);
            continue;
          }


          // If the entry DN is a base DN, then add it with no more processing.
          if (baseDNSet.contains(entryDN))
          {
            entryMap.put(entryDN, e);
            continue;
          }


          // Make sure that the parent exists.  If not, then reject the entry.
          boolean isBelowBaseDN = false;
          for (DN baseDN : baseDNs)
          {
            if (baseDN.isAncestorOf(entryDN))
            {
              isBelowBaseDN = true;
              break;
            }
          }

          if (! isBelowBaseDN)
          {
            Message m = ERR_LDIF_BACKEND_ENTRY_OUT_OF_SCOPE.get(ldifFilePath,
                             currentConfig.dn().toString(), entryDN.toString());
            logError(m);
            reader.rejectLastEntry(m);
            continue;
          }

          DN parentDN = entryDN.getParentDNInSuffix();
          if ((parentDN == null) || (! entryMap.containsKey(parentDN)))
          {
            Message m = ERR_LDIF_BACKEND_MISSING_PARENT.get(ldifFilePath,
                             currentConfig.dn().toString(), entryDN.toString());
            logError(m);
            reader.rejectLastEntry(m);
            continue;
          }


          // The entry does not exist but its parent does, so add it and update
          // the set of children for the parent.
          entryMap.put(entryDN, e);

          HashSet<DN> childDNSet = childDNs.get(parentDN);
          if (childDNSet == null)
          {
            childDNSet = new HashSet<DN>();
            childDNs.put(parentDN, childDNSet);
          }

          childDNSet.add(entryDN);
        }


        if (writeLDIF)
        {
          writeLDIF();
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
        Message m = ERR_LDIF_BACKEND_ERROR_READING_LDIF.get(
                         stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     m, e);
      }
      finally
      {
        reader.close();
      }
    }
    finally
    {
      backendLock.writeLock().unlock();
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
    Message message = ERR_LDIF_BACKEND_BACKUP_RESTORE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void removeBackup(BackupDirectory backupDirectory, String backupID)
         throws DirectoryException
  {
    Message message = ERR_LDIF_BACKEND_BACKUP_RESTORE_NOT_SUPPORTED.get();
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
    Message message = ERR_LDIF_BACKEND_BACKUP_RESTORE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
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
      Validator.ensureTrue(config instanceof LDIFBackendCfg);
      currentConfig = (LDIFBackendCfg) config;
      currentConfig.addLDIFChangeListener(this);

      baseDNs = new DN[currentConfig.getBaseDN().size()];
      currentConfig.getBaseDN().toArray(baseDNs);
      if (baseDNs.length != 1)
      {
        throw new ConfigException(ERR_LDIF_BACKEND_MULTIPLE_BASE_DNS.get(
                                       currentConfig.dn().toString()));
      }

      baseDNSet = new HashSet<DN>();
      for (DN dn : baseDNs)
      {
        baseDNSet.add(dn);
      }

      supportedControls = new HashSet<String>(1);
      supportedControls.add(OID_SUBTREE_DELETE_CONTROL);

      supportedFeatures = new HashSet<String>(0);

      ldifFilePath = currentConfig.getLDIFFile();
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(LDIFBackendCfg configuration,
                      List<Message> unacceptableReasons)
  {
    boolean configAcceptable = true;

    // Make sure that there is only a single base DN.
    if (configuration.getBaseDN().size() != 1)
    {
      unacceptableReasons.add(ERR_LDIF_BACKEND_MULTIPLE_BASE_DNS.get(
                                   configuration.dn().toString()));
      configAcceptable = false;
    }

    return configAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                                 LDIFBackendCfg configuration)
  {
    // We don't actually need to do anything in response to this.  However, if
    // the base DNs or LDIF file are different from what we're currently using
    // then indicate that admin action is required.
    boolean adminActionRequired = false;
    LinkedList<Message> messages = new LinkedList<Message>();

    if (ldifFilePath != null)
    {
      File currentLDIF = getFileForPath(ldifFilePath);
      File newLDIF     = getFileForPath(configuration.getLDIFFile());
      if (! currentLDIF.equals(newLDIF))
      {
        messages.add(INFO_LDIF_BACKEND_LDIF_FILE_CHANGED.get());
        adminActionRequired = true;
      }
    }

    if (baseDNSet != null)
    {
      if (! baseDNSet.equals(configuration.getBaseDN()))
      {
        messages.add(INFO_LDIF_BACKEND_BASE_DN_CHANGED.get());
        adminActionRequired = true;
      }
    }

    currentConfig = configuration;
    return new ConfigChangeResult(ResultCode.SUCCESS, adminActionRequired,
                                  messages);
  }



  /**
   * {@inheritDoc}
   */
  public DN getComponentEntryDN()
  {
    return currentConfig.dn();
  }



  /**
   * {@inheritDoc}
   */
  public String getClassName()
  {
    return LDIFBackend.class.getName();
  }



  /**
   * {@inheritDoc}
   */
  public LinkedHashMap<String,String> getAlerts()
  {
    LinkedHashMap<String,String> alerts = new LinkedHashMap<String,String>();

    alerts.put(ALERT_TYPE_LDIF_BACKEND_CANNOT_WRITE_UPDATE,
               ALERT_DESCRIPTION_LDIF_BACKEND_CANNOT_WRITE_UPDATE);

    return alerts;
  }



  /**
   * {@inheritDoc}
   */
  public void preloadEntryCache() throws UnsupportedOperationException {
    throw new UnsupportedOperationException("Operation not supported.");
  }
}

