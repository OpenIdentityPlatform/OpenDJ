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
package org.opends.server.backends.task;



import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.api.Backend;
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.IntegerWithUnitConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.AddOperation;
import org.opends.server.core.CancelledOperationException;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.InitializationException;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.BackendMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides an implementation of a Directory Server backend that may
 * be used to execute various kinds of administrative tasks on a one-time or
 * recurring basis.
 */
public class TaskBackend
       extends Backend
       implements ConfigurableComponent
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.backends.task.TaskBackend";



  /**
   * The set of time units that will be used for expressing the task retention
   * time.
   */
  private static final LinkedHashMap<String,Double> timeUnits =
       new LinkedHashMap<String,Double>();



  // The DN of the configuration entry for this backend.
  private DN configEntryDN;

  // The DN of the entry that will serve as the parent for all recurring task
  // entries.
  private DN recurringTaskParentDN;

  // The DN of the entry that will serve as the parent for all scheduled task
  // entries.
  private DN scheduledTaskParentDN;

  // The DN of the entry that will serve as the root for all task entries.
  private DN taskRootDN;

  // The set of base DNs defined for this backend.
  private DN[] baseDNs;

  // The set of supported controls for this backend.
  private HashSet<String> supportedControls;

  // The set of supported features for this backend.
  private HashSet<String> supportedFeatures;

  // The length of time in seconds after a task is completed that it should be
  // removed from the set of scheduled tasks.
  private long retentionTime;

  // The path to the task backing file.
  private String taskBackingFile;

  // The task scheduler that will be responsible for actually invoking scheduled
  // tasks.
  private TaskScheduler taskScheduler;



  static
  {
    timeUnits.put("seconds", 1.0);
    timeUnits.put("minutes", 60.0);
    timeUnits.put("hours", (60.0*60.0));
    timeUnits.put("days", (24.0*60.0*60.0));
    timeUnits.put("weeks", (7.0*24.0*60.0*60.0));
  }



  /**
   * Creates a new backend with the provided information.  All backend
   * implementations must implement a default constructor that use
   * <CODE>super()</CODE> to invoke this constructor.
   */
  public TaskBackend()
  {
    super();

    assert debugConstructor(CLASS_NAME);


    // Perform all initialization in initializeBackend.
  }



  /**
   * Initializes this backend based on the information in the provided
   * configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this backend.
   * @param  baseDNs      The set of base DNs that have been configured for this
   *                      backend.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   *
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   */
  public void initializeBackend(ConfigEntry configEntry, DN[] baseDNs)
         throws ConfigException, InitializationException
  {
    assert debugEnter(CLASS_NAME, "initializeBackend",
                      String.valueOf(configEntry));


    // Make sure that a configuration entry was provided.  If not, then we will
    // not be able to complete the initialization.
    if (configEntry == null)
    {
      int    msgID   = MSGID_TASKBE_CONFIG_ENTRY_NULL;
      String message = getMessage(msgID);
      throw new ConfigException(msgID, message);
    }

    configEntryDN = configEntry.getDN();


    // Make sure that the provided set of base DNs contains exactly one value.
    // We will only allow one base for task entries.
    if ((baseDNs == null) || (baseDNs.length == 0))
    {
      int    msgID   = MSGID_TASKBE_NO_BASE_DNS;
      String message = getMessage(msgID);
      throw new ConfigException(msgID, message);
    }
    else if (baseDNs.length > 1)
    {
      int    msgID   = MSGID_TASKBE_MULTIPLE_BASE_DNS;
      String message = getMessage(msgID);
      throw new ConfigException(msgID, message);
    }
    else
    {
      this.baseDNs = baseDNs;

      taskRootDN = baseDNs[0];

      String recurringTaskBaseString = RECURRING_TASK_BASE_RDN + "," +
                                       taskRootDN.toString();
      try
      {
        recurringTaskParentDN = DN.decode(recurringTaskBaseString);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "initializeBackend", e);

        // This should never happen.
        int    msgID   = MSGID_TASKBE_CANNOT_DECODE_RECURRING_TASK_BASE_DN;
        String message = getMessage(msgID,
                                    String.valueOf(recurringTaskBaseString),
                                    stackTraceToSingleLineString(e));
        throw new InitializationException(msgID, message, e);
      }

      String scheduledTaskBaseString = SCHEDULED_TASK_BASE_RDN + "," +
                                       taskRootDN.toString();
      try
      {
        scheduledTaskParentDN = DN.decode(scheduledTaskBaseString);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "initializeBackend", e);

        // This should never happen.
        int    msgID   = MSGID_TASKBE_CANNOT_DECODE_SCHEDULED_TASK_BASE_DN;
        String message = getMessage(msgID,
                                    String.valueOf(scheduledTaskBaseString),
                                    stackTraceToSingleLineString(e));
        throw new InitializationException(msgID, message, e);
      }
    }


    // Get the retention time that will be used to determine how long task
    // information stays around once the associated task is completed.
    int msgID = MSGID_TASKBE_DESCRIPTION_RETENTION_TIME;
    IntegerWithUnitConfigAttribute retentionStub =
         new IntegerWithUnitConfigAttribute(ATTR_TASK_RETENTION_TIME,
                                            getMessage(msgID), false, timeUnits,
                                            true, 0, false, 0);
    try
    {
      IntegerWithUnitConfigAttribute retentionAttr =
           (IntegerWithUnitConfigAttribute)
           configEntry.getConfigAttribute(retentionStub);
      if (retentionAttr == null)
      {
        retentionTime = DEFAULT_TASK_RETENTION_TIME;
      }
      else
      {
        retentionTime = retentionAttr.activeCalculatedValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeBackend", e);

      msgID = MSGID_TASKBE_CANNOT_INITIALIZE_RETENTION_TIME;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Get the path to the task data backing file.
    msgID = MSGID_TASKBE_DESCRIPTION_BACKING_FILE;
    StringConfigAttribute taskFileStub =
         new StringConfigAttribute(ATTR_TASK_BACKING_FILE, getMessage(msgID),
                                   true, false, false);
    try
    {
      StringConfigAttribute taskFileAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(taskFileStub);
      if (taskFileAttr == null)
      {
        taskBackingFile = DirectoryServer.getServerRoot() + File.separator +
                          CONFIG_DIR_NAME + File.separator + TASK_FILE_NAME;
      }
      else
      {
        taskBackingFile = taskFileAttr.activeValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeBackend", e);

      msgID = MSGID_TASKBE_CANNOT_INITIALIZE_BACKING_FILE;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Create the scheduler and initialize it from the backing file.
    taskScheduler = new TaskScheduler(this);
    taskScheduler.start();


    // Define an empty sets for the supported controls and features.
    supportedControls = new HashSet<String>(0);
    supportedFeatures = new HashSet<String>(0);


    // Register with the Directory Server as a configurable component.
    DirectoryServer.registerConfigurableComponent(this);


    // Register the task base as a private suffix.
    DirectoryServer.registerPrivateSuffix(baseDNs[0], this);
  }



  /**
   * Performs any necessary work to finalize this backend, including closing any
   * underlying databases or connections and deregistering any suffixes that it
   * manages with the Directory Server.  This may be called during the
   * Directory Server shutdown process or if a backend is disabled with the
   * server online.  It must not return until the backend is closed.
   * <BR><BR>
   * This method may not throw any exceptions.  If any problems are encountered,
   * then they may be logged but the closure should progress as completely as
   * possible.
   */
  public void finalizeBackend()
  {
    assert debugEnter(CLASS_NAME, "finalizeBackend");


    DirectoryServer.deregisterConfigurableComponent(this);


    try
    {
      taskScheduler.stopScheduler();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "finalizeBackend", e);
    }

    try
    {
      int    msgID   = MSGID_TASKBE_INTERRUPTED_BY_SHUTDOWN;
      String message = getMessage(msgID);

      taskScheduler.interruptRunningTasks(TaskState.STOPPED_BY_SHUTDOWN,
                                          message, true);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "finalizeBackend", e);
    }
  }



  /**
   * Retrieves the set of base-level DNs that may be used within this backend.
   *
   * @return  The set of base-level DNs that may be used within this backend.
   */
  public DN[] getBaseDNs()
  {
    assert debugEnter(CLASS_NAME, "getBaseDNs");

    return baseDNs;
  }



  /**
   * Indicates whether the data associated with this backend may be considered
   * local (i.e., in a repository managed by the Directory Server) rather than
   * remote (i.e., in an external repository accessed by the Directory Server
   * but managed through some other means).
   *
   * @return  <CODE>true</CODE> if the data associated with this backend may be
   *          considered local, or <CODE>false</CODE> if it is remote.
   */
  public boolean isLocal()
  {
    assert debugEnter(CLASS_NAME, "isLocal");

    // For the purposes of this method, this is a local backend.
    return true;
  }



  /**
   * Retrieves the requested entry from this backend.
   *
   * @param  entryDN  The distinguished name of the entry to retrieve.
   *
   * @return  The requested entry, or <CODE>null</CODE> if the entry does not
   *          exist.
   *
   * @throws  DirectoryException  If a problem occurs while trying to retrieve
   *                              the entry.
   */
  public Entry getEntry(DN entryDN)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "getEntry", String.valueOf(entryDN));


    if (entryDN == null)
    {
      return null;
    }

    if (entryDN.equals(taskRootDN))
    {
      return taskScheduler.getTaskRootEntry();
    }
    else if (entryDN.equals(scheduledTaskParentDN))
    {
      return taskScheduler.getScheduledTaskParentEntry();
    }
    else if (entryDN.equals(recurringTaskParentDN))
    {
      return taskScheduler.getRecurringTaskParentEntry();
    }

    DN parentDN = entryDN.getParent();
    if (parentDN == null)
    {
      return null;
    }

    if (parentDN.equals(scheduledTaskParentDN))
    {
      return taskScheduler.getScheduledTaskEntry(entryDN);
    }
    else if (parentDN.equals(recurringTaskParentDN))
    {
      return taskScheduler.getRecurringTaskEntry(entryDN);
    }
    else
    {
      // If we've gotten here then this is not an entry that should exist in the
      // task backend.
      return null;
    }
  }



  /**
   * Indicates whether an entry with the specified DN exists in the backend.
   * The default implementation obtains a read lock and calls
   * <CODE>getEntry</CODE>, but backend implementations may override this with a
   * more efficient version that does not require a lock.  The caller is not
   * required to hold any locks on the specified DN.
   *
   * @param  entryDN  The DN of the entry for which to determine existence.
   *
   * @return  <CODE>true</CODE> if the specified entry exists in this backend,
   *          or <CODE>false</CODE> if it does not.
   *
   * @throws  DirectoryException  If a problem occurs while trying to make the
   *                              determination.
   */
  public boolean entryExists(DN entryDN)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "entryExists", String.valueOf(entryDN));


    if (entryDN == null)
    {
      return false;
    }

    if (entryDN.equals(taskRootDN) || entryDN.equals(scheduledTaskParentDN) ||
        entryDN.equals(recurringTaskParentDN))
    {
      return true;
    }

    DN parentDN = entryDN.getParent();
    if (parentDN == null)
    {
      return false;
    }

    if (parentDN.equals(scheduledTaskParentDN))
    {
      return (taskScheduler.getScheduledTaskEntry(entryDN) != null);
    }
    else if (parentDN.equals(recurringTaskParentDN))
    {
      return (taskScheduler.getRecurringTaskEntry(entryDN) != null);
    }
    else
    {
      return false;
    }
  }



  /**
   * Adds the provided entry to this backend.  This method must ensure that the
   * entry is appropriate for the backend and that no entry already exists with
   * the same DN.
   *
   * @param  entry         The entry to add to this backend.
   * @param  addOperation  The add operation with which the new entry is
   *                       associated.  This may be <CODE>null</CODE> for adds
   *                       performed internally.
   *
   * @throws  DirectoryException  If a problem occurs while trying to add the
   *                              entry.
   */
  public void addEntry(Entry entry, AddOperation addOperation)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "addEntry", String.valueOf(entry),
                      String.valueOf(addOperation));


    // Get the DN for the entry and then get its parent.
    DN entryDN = entry.getDN();
    DN parentDN = entryDN.getParent();

    if (parentDN == null)
    {
      int msgID = MSGID_TASKBE_ADD_DISALLOWED_DN;
      String message = getMessage(msgID, String.valueOf(scheduledTaskParentDN),
                                  String.valueOf(recurringTaskParentDN));
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                   msgID);
    }


    // If the parent DN is equal to the parent for scheduled tasks, then try to
    // treat the provided entry like a scheduled task.
    if (parentDN.equals(scheduledTaskParentDN))
    {
      Task task = taskScheduler.entryToScheduledTask(entry);
      taskScheduler.scheduleTask(task, true);
      return;
    }


    // If the parent DN is equal to the parent for recurring tasks, then try to
    // treat the provided entry like a recurring task.
    if (parentDN.equals(recurringTaskParentDN))
    {
      RecurringTask recurringTask = taskScheduler.entryToRecurringTask(entry);
      taskScheduler.addRecurringTask(recurringTask, true);
      return;
    }


    // We won't allow the entry to be added.
    int msgID = MSGID_TASKBE_ADD_DISALLOWED_DN;
    String message = getMessage(msgID, String.valueOf(scheduledTaskParentDN),
                                String.valueOf(recurringTaskParentDN));
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                 msgID);
  }



  /**
   * Removes the specified entry from this backend.  This method must ensure
   * that the entry exists and that it does not have any subordinate entries
   * (unless the backend supports a subtree delete operation and the client
   * included the appropriate information in the request).
   *
   * @param  entryDN          The DN of the entry to remove from this backend.
   * @param  deleteOperation  The delete operation with which this action is
   *                          associated.  This may be <CODE>null</CODE> for
   *                          deletes performed internally.
   *
   * @throws  DirectoryException  If a problem occurs while trying to remove the
   *                              entry.
   */
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "deleteEntry", String.valueOf(entryDN),
                      String.valueOf(deleteOperation));


    // Get the parent for the provided entry DN.  It must be either the
    // scheduled or recurring task parent DN.
    DN parentDN = entryDN.getParent();
    if (parentDN == null)
    {
      int    msgID   = MSGID_TASKBE_DELETE_INVALID_ENTRY;
      String message = getMessage(msgID, String.valueOf(entryDN));
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                   msgID);
    }
    else if (parentDN.equals(scheduledTaskParentDN))
    {
      // It's a scheduled task.  Make sure that it exists.
      Task t = taskScheduler.getScheduledTask(entryDN);
      if (t == null)
      {
        int    msgID   = MSGID_TASKBE_DELETE_NO_SUCH_TASK;
        String message = getMessage(msgID, String.valueOf(entryDN));
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, msgID);
      }


      // Look at the state of the task.  We will allow pending and completed
      // tasks to be removed, but not running tasks.
      TaskState state = t.getTaskState();
      if (TaskState.isPending(state))
      {
        taskScheduler.removePendingTask(t.getTaskID());
      }
      else if (TaskState.isDone(t.getTaskState()))
      {
        taskScheduler.removeCompletedTask(t.getTaskID());
      }
      else
      {
        int    msgID   = MSGID_TASKBE_DELETE_RUNNING;
        String message = getMessage(msgID, String.valueOf(entryDN));
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                     msgID);
      }
    }
    else if (parentDN.equals(recurringTaskParentDN))
    {
      // It's a recurring task.  Make sure that it exists.
      RecurringTask rt = taskScheduler.getRecurringTask(entryDN);
      if (rt == null)
      {
        int    msgID   = MSGID_TASKBE_DELETE_NO_SUCH_RECURRING_TASK;
        String message = getMessage(msgID, String.valueOf(entryDN));
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, msgID);
      }


      // Try to remove the recurring task.  This will fail if there are any
      // associated iterations pending or running.
      taskScheduler.removeRecurringTask(rt.getRecurringTaskID());
    }
    else
    {
      int    msgID   = MSGID_TASKBE_DELETE_INVALID_ENTRY;
      String message = getMessage(msgID, String.valueOf(entryDN));
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                   msgID);
    }
  }



  /**
   * Replaces the specified entry with the provided entry in this backend.  The
   * backend must ensure that an entry already exists with the same DN as the
   * provided entry.
   *
   * @param  entry            The new entry to use in place of the existing
   *                          entry with the same DN.
   * @param  modifyOperation  The modify operation with which this action is
   *                          associated.  This may be <CODE>null</CODE> for
   *                          modifications performed internally.
   *
   * @throws  DirectoryException  If a problem occurs while trying to replace
   *                              the entry.
   */
  public void replaceEntry(Entry entry, ModifyOperation modifyOperation)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "replaceEntry", String.valueOf(entry),
                      String.valueOf(modifyOperation));

    // FIXME -- We need to support this.
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                 "Modify operations are not yet supported in " +
                                 "the task backend", 1);
  }



  /**
   * Moves and/or renames the provided entry in this backend, altering any
   * subordinate entries as necessary.  This must ensure that an entry already
   * exists with the provided current DN, and that no entry exists with the
   * target DN of the provided entry.
   *
   * @param  currentDN          The current DN of the entry to be replaced.
   * @param  entry              The new content to use for the entry.
   * @param  modifyDNOperation  The modify DN operation with which this action
   *                            is associated.  This may be <CODE>null</CODE>
   *                            for modify DN operations performed internally.
   *
   * @throws  DirectoryException  If a problem occurs while trying to perform
   *                              the rename.
   */
  public void renameEntry(DN currentDN, Entry entry,
                                   ModifyDNOperation modifyDNOperation)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "renameEntry", String.valueOf(currentDN),
                      String.valueOf(entry), String.valueOf(modifyDNOperation));


    int    msgID   = MSGID_TASKBE_MODIFY_DN_NOT_SUPPORTED;
    String message = getMessage(msgID);
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                 msgID);
  }



  /**
   * Processes the specified search in this backend.  Matching entries should be
   * provided back to the core server using the
   * <CODE>SearchOperation.returnEntry</CODE> method.
   *
   * @param  searchOperation  The search operation to be processed.
   *
   * @throws  DirectoryException  If a problem occurs while processing the
   *                              search.
   *
   * @throws  CancelledOperationException  If this backend noticed and reacted
   *                                       to a request to cancel or abandon the
   *                                       add operation.
   */
  public void search(SearchOperation searchOperation)
         throws DirectoryException, CancelledOperationException
  {
    assert debugEnter(CLASS_NAME, "search", String.valueOf(searchOperation));


    // Look at the base DN and scope for the search operation to decide which
    // entries we need to look at.
    boolean searchRoot            = false;
    boolean searchScheduledParent = false;
    boolean searchScheduledTasks  = false;
    boolean searchRecurringParent = false;
    boolean searchRecurringTasks  = false;

    DN           baseDN       = searchOperation.getBaseDN();
    SearchScope  searchScope  = searchOperation.getScope();
    SearchFilter searchFilter = searchOperation.getFilter();

    if (baseDN.equals(taskRootDN))
    {
      switch (searchScope)
      {
        case BASE_OBJECT:
          searchRoot = true;
          break;
        case SINGLE_LEVEL:
          searchScheduledParent = true;
          searchRecurringParent = true;
          break;
        case WHOLE_SUBTREE:
          searchRoot            = true;
          searchScheduledParent = true;
          searchRecurringParent = true;
          searchScheduledTasks  = true;
          searchRecurringTasks  = true;
          break;
        case SUBORDINATE_SUBTREE:
          searchScheduledParent = true;
          searchRecurringParent = true;
          searchScheduledTasks  = true;
          searchRecurringTasks  = true;
          break;
      }
    }
    else if (baseDN.equals(scheduledTaskParentDN))
    {
      switch (searchScope)
      {
        case BASE_OBJECT:
          searchScheduledParent = true;
          break;
        case SINGLE_LEVEL:
          searchScheduledTasks = true;
          break;
        case WHOLE_SUBTREE:
          searchScheduledParent = true;
          searchScheduledTasks  = true;
          break;
        case SUBORDINATE_SUBTREE:
          searchScheduledTasks  = true;
          break;
      }
    }
    else if (baseDN.equals(recurringTaskParentDN))
    {
      switch (searchScope)
      {
        case BASE_OBJECT:
          searchRecurringParent = true;
          break;
        case SINGLE_LEVEL:
          searchRecurringTasks = true;
          break;
        case WHOLE_SUBTREE:
          searchRecurringParent = true;
          searchRecurringTasks  = true;
          break;
        case SUBORDINATE_SUBTREE:
          searchRecurringTasks  = true;
          break;
      }
    }
    else
    {
      DN parentDN = baseDN.getParent();
      if (parentDN == null)
      {
        int    msgID   = MSGID_TASKBE_SEARCH_INVALID_BASE;
        String message = getMessage(msgID, String.valueOf(baseDN));
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, msgID);
      }
      else if (parentDN.equals(scheduledTaskParentDN))
      {
        Entry e = taskScheduler.getScheduledTaskEntry(baseDN);
        if (e == null)
        {
          int    msgID   = MSGID_TASKBE_SEARCH_NO_SUCH_TASK;
          String message = getMessage(msgID, String.valueOf(baseDN));
          throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message,
                                       msgID, scheduledTaskParentDN, null);
        }

        if (((searchScope == SearchScope.BASE_OBJECT) ||
             (searchScope == SearchScope.WHOLE_SUBTREE)) &&
            searchFilter.matchesEntry(e))
        {
          searchOperation.returnEntry(e, null);
        }

        return;
      }
      else if (parentDN.equals(recurringTaskParentDN))
      {
        Entry e = taskScheduler.getRecurringTaskEntry(baseDN);
        if (e == null)
        {
          int    msgID   = MSGID_TASKBE_SEARCH_NO_SUCH_RECURRING_TASK;
          String message = getMessage(msgID, String.valueOf(baseDN));
          throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message,
                                       msgID, recurringTaskParentDN, null);
        }

        if (((searchScope == SearchScope.BASE_OBJECT) ||
             (searchScope == SearchScope.WHOLE_SUBTREE)) &&
            searchFilter.matchesEntry(e))
        {
          searchOperation.returnEntry(e, null);
        }

        return;
      }
      else
      {
        int    msgID   = MSGID_TASKBE_SEARCH_INVALID_BASE;
        String message = getMessage(msgID, String.valueOf(baseDN));
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, msgID);
      }
    }


    if (searchRoot)
    {
      Entry e = taskScheduler.getTaskRootEntry();
      if (searchFilter.matchesEntry(e))
      {
        if (! searchOperation.returnEntry(e, null))
        {
          return;
        }
      }
    }


    if (searchScheduledParent)
    {
      Entry e = taskScheduler.getScheduledTaskParentEntry();
      if (searchFilter.matchesEntry(e))
      {
        if (! searchOperation.returnEntry(e, null))
        {
          return;
        }
      }
    }


    if (searchScheduledTasks)
    {
      if (! taskScheduler.searchScheduledTasks(searchOperation))
      {
        return;
      }
    }


    if (searchRecurringParent)
    {
      Entry e = taskScheduler.getRecurringTaskParentEntry();
      if (searchFilter.matchesEntry(e))
      {
        if (! searchOperation.returnEntry(e, null))
        {
          return;
        }
      }
    }


    if (searchRecurringTasks)
    {
      if (! taskScheduler.searchRecurringTasks(searchOperation))
      {
        return;
      }
    }
  }



  /**
   * Retrieves the OIDs of the controls that may be supported by this backend.
   *
   * @return  The OIDs of the controls that may be supported by this backend.
   */
  public HashSet<String> getSupportedControls()
  {
    assert debugEnter(CLASS_NAME, "getSupportedControls");

    return supportedControls;
  }



  /**
   * Indicates whether this backend supports the specified control.
   *
   * @param  controlOID  The OID of the control for which to make the
   *                     determination.
   *
   * @return  <CODE>true</CODE> if this backend does support the requested
   *          control, or <CODE>false</CODE>
   */
  public boolean supportsControl(String controlOID)
  {
    assert debugEnter(CLASS_NAME, "supportsControl",
                      String.valueOf(controlOID));

    // This backend does not provide any special control support.
    return false;
  }



  /**
   * Retrieves the OIDs of the features that may be supported by this backend.
   *
   * @return  The OIDs of the features that may be supported by this backend.
   */
  public HashSet<String> getSupportedFeatures()
  {
    assert debugEnter(CLASS_NAME, "getSupportedFeatures");

    return supportedFeatures;
  }



  /**
   * Indicates whether this backend supports the specified feature.
   *
   * @param  featureOID  The OID of the feature for which to make the
   *                     determination.
   *
   * @return  <CODE>true</CODE> if this backend does support the requested
   *          feature, or <CODE>false</CODE>
   */
  public boolean supportsFeature(String featureOID)
  {
    assert debugEnter(CLASS_NAME, "supportsFeature",
                      String.valueOf(featureOID));

    // This backend does not provide any special feature support.
    return false;
  }



  /**
   * Indicates whether this backend provides a mechanism to export the data it
   * contains to an LDIF file.
   *
   * @return  <CODE>true</CODE> if this backend provides an LDIF export
   *          mechanism, or <CODE>false</CODE> if not.
   */
  public boolean supportsLDIFExport()
  {
    assert debugEnter(CLASS_NAME, "supportsLDIFExport");

    // LDIF exports are supported.
    return true;
  }



  /**
   * Exports the contents of this backend to LDIF.  This method should only be
   * called if <CODE>supportsLDIFExport</CODE> returns <CODE>true</CODE>.  Note
   * that the server will not explicitly initialize this backend before calling
   * this method.
   *
   * @param  configEntry   The configuration entry for this backend.
   * @param  baseDNs       The set of base DNs configured for this backend.
   * @param  exportConfig  The configuration to use when performing the export.
   *
   * @throws  DirectoryException  If a problem occurs while performing the LDIF
   *                              export.
   */
  public void exportLDIF(ConfigEntry configEntry, DN[] baseDNs,
                         LDIFExportConfig exportConfig)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "exportLDIF", String.valueOf(exportConfig));


    // FIXME -- Implement support for exporting to LDIF.
  }



  /**
   * Indicates whether this backend provides a mechanism to import its data from
   * an LDIF file.
   *
   * @return  <CODE>true</CODE> if this backend provides an LDIF import
   *          mechanism, or <CODE>false</CODE> if not.
   */
  public boolean supportsLDIFImport()
  {
    assert debugEnter(CLASS_NAME, "supportsLDIFImport");

    // This backend does not support LDIF imports.
    return false;
  }



  /**
   * Imports information from an LDIF file into this backend.  This method
   * should only be called if <CODE>supportsLDIFImport</CODE> returns
   * <CODE>true</CODE>.  Note that the server will not explicitly initialize
   * this backend before calling this method.
   *
   * @param  configEntry   The configuration entry for this backend.
   * @param  baseDNs       The set of base DNs configured for this backend.
   * @param  importConfig  The configuration to use when performing the import.
   *
   * @throws  DirectoryException  If a problem occurs while performing the LDIF
   *                              import.
   */
  public void importLDIF(ConfigEntry configEntry, DN[] baseDNs,
                         LDIFImportConfig importConfig)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "importLDIF", String.valueOf(importConfig));


    // This backend does not support LDIF imports.
    int    msgID   = MSGID_TASKBE_IMPORT_NOT_SUPPORTED;
    String message = getMessage(msgID);
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                 msgID);
  }



  /**
   * Indicates whether this backend provides a backup mechanism of any kind.
   * This method is used by the backup process when backing up all backends to
   * determine whether this backend is one that should be skipped.  It should
   * only return <CODE>true</CODE> for backends that it is not possible to
   * archive directly (e.g., those that don't store their data locally, but
   * rather pass through requests to some other repository).
   *
   * @return  <CODE>true</CODE> if this backend provides any kind of backup
   *          mechanism, or <CODE>false</CODE> if it does not.
   */
  public boolean supportsBackup()
  {
    assert debugEnter(CLASS_NAME, "supportsBackup");

    // This backend does provide a backup/restore mechanism.
    return true;
  }



  /**
   * Indicates whether this backend provides a mechanism to perform a backup of
   * its contents in a form that can be restored later, based on the provided
   * configuration.
   *
   * @param  backupConfig       The configuration of the backup for which to
   *                            make the determination.
   * @param  unsupportedReason  A buffer to which a message can be appended
   *                            explaining why the requested backup is not
   *                            supported.
   *
   * @return  <CODE>true</CODE> if this backend provides a mechanism for
   *          performing backups with the provided configuration, or
   *          <CODE>false</CODE> if not.
   */
  public boolean supportsBackup(BackupConfig backupConfig,
                                StringBuilder unsupportedReason)
  {
    assert debugEnter(CLASS_NAME, "supportsBackup");


    // This backend does provide a backup/restore mechanism.
    return true;
  }



  /**
   * Creates a backup of the contents of this backend in a form that may be
   * restored at a later date if necessary.  This method should only be called
   * if <CODE>supportsBackup</CODE> returns <CODE>true</CODE>.  Note that the
   * server will not explicitly initialize this backend before calling this
   * method.
   *
   * @param  configEntry   The configuration entry for this backend.
   * @param  backupConfig  The configuration to use when performing the backup.
   *
   * @throws  DirectoryException  If a problem occurs while performing the
   *                              backup.
   */
  public void createBackup(ConfigEntry configEntry, BackupConfig backupConfig)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "createBackup", String.valueOf(backupConfig));


    // NYI -- Create the backup.
  }



  /**
   * Removes the specified backup if it is possible to do so.
   *
   * @param  backupDirectory  The backup directory structure with which the
   *                          specified backup is associated.
   * @param  backupID         The backup ID for the backup to be removed.
   *
   * @throws  DirectoryException  If it is not possible to remove the specified
   *                              backup for some reason (e.g., no such backup
   *                              exists or there are other backups that are
   *                              dependent upon it).
   */
  public void removeBackup(BackupDirectory backupDirectory,
                           String backupID)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "removeBackup",
                      String.valueOf(backupDirectory),
                      String.valueOf(backupID));


    // NYI -- Remove the backup.
  }



  /**
   * Indicates whether this backend provides a mechanism to restore a backup.
   *
   * @return  <CODE>true</CODE> if this backend provides a mechanism for
   *          restoring backups, or <CODE>false</CODE> if not.
   */
  public boolean supportsRestore()
  {
    assert debugEnter(CLASS_NAME, "supportsRestore");


    // This backend does provide a backup/restore mechanism.
    return true;
  }



  /**
   * Restores a backup of the contents of this backend.  This method should only
   * be called if <CODE>supportsRestore</CODE> returns <CODE>true</CODE>.  Note
   * that the server will not explicitly initialize this backend before calling
   * this method.
   *
   * @param  configEntry    The configuration entry for this backend.
   * @param  restoreConfig  The configuration to use when performing the
   *                        restore.
   *
   * @throws  DirectoryException  If a problem occurs while performing the
   *                              restore.
   */
  public void restoreBackup(ConfigEntry configEntry,
                            RestoreConfig restoreConfig)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "restoreBackup",
                      String.valueOf(restoreConfig));


    // NYI -- Restore the backup.
  }



  /**
   * Retrieves the DN of the configuration entry with which this component is
   * associated.
   *
   * @return  The DN of the configuration entry with which this component is
   *          associated.
   */
  public DN getConfigurableComponentEntryDN()
  {
    assert debugEnter(CLASS_NAME, "getConfigurableComponentEntryDN");

    return configEntryDN;
  }



  /**
   * Retrieves the set of configuration attributes that are associated with this
   * configurable component.
   *
   * @return  The set of configuration attributes that are associated with this
   *          configurable component.
   */
  public List<ConfigAttribute> getConfigurationAttributes()
  {
    assert debugEnter(CLASS_NAME, "getConfigurationAttributes");

    LinkedList<ConfigAttribute> attrList = new LinkedList<ConfigAttribute>();

    String description = getMessage(MSGID_TASKBE_DESCRIPTION_BACKING_FILE);
    attrList.add(new StringConfigAttribute(ATTR_TASK_BACKING_FILE, description,
                                           true, false, false,
                                           taskBackingFile));

    description = getMessage(MSGID_TASKBE_DESCRIPTION_RETENTION_TIME);
    attrList.add(new IntegerWithUnitConfigAttribute(ATTR_TASK_RETENTION_TIME,
                                                    description, false,
                                                    timeUnits, true, 0, false,
                                                    0, retentionTime,
                                                    "seconds"));

    return attrList;
  }



  /**
   * Indicates whether the provided configuration entry has an acceptable
   * configuration for this component.  If it does not, then detailed
   * information about the problem(s) should be added to the provided list.
   *
   * @param  configEntry          The configuration entry for which to make the
   *                              determination.
   * @param  unacceptableReasons  A list that can be used to hold messages about
   *                              why the provided entry does not have an
   *                              acceptable configuration.
   *
   * @return  <CODE>true</CODE> if the provided entry has an acceptable
   *          configuration for this component, or <CODE>false</CODE> if not.
   */
  public boolean hasAcceptableConfiguration(ConfigEntry configEntry,
                                            List<String> unacceptableReasons)
  {
    assert debugEnter(CLASS_NAME, "hasAcceptableConfiguration",
                      String.valueOf(configEntry), "java.util.List<String>");


    boolean configIsAcceptable = true;


    String description = getMessage(MSGID_TASKBE_DESCRIPTION_BACKING_FILE);
    StringConfigAttribute backingStub =
         new StringConfigAttribute(ATTR_TASK_BACKING_FILE, description, true,
                                   false, false);
    try
    {
      StringConfigAttribute backingAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(backingStub);
      if (backingAttr == null)
      {
        int msgID = MSGID_TASKBE_NO_BACKING_FILE;
        unacceptableReasons.add(getMessage(msgID, ATTR_TASK_BACKING_FILE));
        configIsAcceptable = false;
      }
      else
      {
        String tmpBackingFile = backingAttr.pendingValue();
        if (! taskBackingFile.equals(tmpBackingFile))
        {
          File f = new File(tmpBackingFile);
          if (!f.isAbsolute())
          {
            f = new File(DirectoryServer.getServerRoot(), tmpBackingFile);
          }
          if (f.exists())
          {
            int msgID = MSGID_TASKBE_BACKING_FILE_EXISTS;
            unacceptableReasons.add(getMessage(msgID, tmpBackingFile));
            configIsAcceptable = false;
          }
          else
          {
            File p = f.getParentFile();
            if (p == null)
            {
              int msgID = MSGID_TASKBE_INVALID_BACKING_FILE_PATH;
              unacceptableReasons.add(getMessage(msgID, tmpBackingFile));
              configIsAcceptable = false;
            }
            else if (! p.exists())
            {
              int msgID = MSGID_TASKBE_BACKING_FILE_MISSING_PARENT;
              unacceptableReasons.add(getMessage(msgID, p.getPath(),
                                                 tmpBackingFile));
              configIsAcceptable = false;
            }
            else if (! p.isDirectory())
            {
              int msgID = MSGID_TASKBE_BACKING_FILE_PARENT_NOT_DIRECTORY;
              unacceptableReasons.add(getMessage(msgID, p.getPath(),
                                                 tmpBackingFile));
              configIsAcceptable = false;
            }
          }
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      int msgID = MSGID_TASKBE_ERROR_GETTING_BACKING_FILE;
      unacceptableReasons.add(getMessage(msgID, ATTR_TASK_BACKING_FILE,
                                         stackTraceToSingleLineString(e)));

      configIsAcceptable = false;
    }


    description = getMessage(MSGID_TASKBE_DESCRIPTION_RETENTION_TIME);
    IntegerWithUnitConfigAttribute retentionStub =
         new IntegerWithUnitConfigAttribute(ATTR_TASK_RETENTION_TIME,
                                            description, false, timeUnits,
                                            true, 0, false, 0);
    try
    {
      IntegerWithUnitConfigAttribute retentionAttr =
           (IntegerWithUnitConfigAttribute)
           configEntry.getConfigAttribute(retentionStub);
      if (retentionAttr == null)
      {
        int msgID = MSGID_TASKBE_NO_RETENTION_TIME;
        unacceptableReasons.add(getMessage(msgID, ATTR_TASK_RETENTION_TIME));
        configIsAcceptable = false;
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      int msgID = MSGID_TASKBE_ERROR_GETTING_RETENTION_TIME;
      unacceptableReasons.add(getMessage(msgID, ATTR_TASK_RETENTION_TIME,
                                         stackTraceToSingleLineString(e)));

      configIsAcceptable = false;
    }


    return configIsAcceptable;
  }



  /**
   * Makes a best-effort attempt to apply the configuration contained in the
   * provided entry.  Information about the result of this processing should be
   * added to the provided message list.  Information should always be added to
   * this list if a configuration change could not be applied.  If detailed
   * results are requested, then information about the changes applied
   * successfully (and optionally about parameters that were not changed) should
   * also be included.
   *
   * @param  configEntry      The entry containing the new configuration to
   *                          apply for this component.
   * @param  detailedResults  Indicates whether detailed information about the
   *                          processing should be added to the list.
   *
   * @return  Information about the result of the configuration update.
   */
  public ConfigChangeResult applyNewConfiguration(ConfigEntry configEntry,
                                                  boolean detailedResults)
  {
    assert debugEnter(CLASS_NAME, "applyNewConfiguration",
                      String.valueOf(configEntry),
                      String.valueOf(detailedResults));


    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    String tmpBackingFile = taskBackingFile;
    String description = getMessage(MSGID_TASKBE_DESCRIPTION_BACKING_FILE);
    StringConfigAttribute backingStub =
         new StringConfigAttribute(ATTR_TASK_BACKING_FILE, description, true,
                                   false, false);
    try
    {
      StringConfigAttribute backingAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(backingStub);
      if (backingAttr == null)
      {
        int msgID = MSGID_TASKBE_NO_BACKING_FILE;
        messages.add(getMessage(msgID, ATTR_TASK_BACKING_FILE));
        resultCode = ResultCode.OBJECTCLASS_VIOLATION;
      }
      else
      {
        tmpBackingFile = backingAttr.pendingValue();
        if (! taskBackingFile.equals(tmpBackingFile))
        {
          File f = new File(tmpBackingFile);
          if (!f.isAbsolute())
          {
            f = new File(DirectoryServer.getServerRoot(), tmpBackingFile);
          }
          if (f.exists())
          {
            int msgID = MSGID_TASKBE_BACKING_FILE_EXISTS;
            messages.add(getMessage(msgID, tmpBackingFile));
            resultCode = ResultCode.CONSTRAINT_VIOLATION;
          }
          else
          {
            File p = f.getParentFile();
            if (p == null)
            {
              int msgID = MSGID_TASKBE_INVALID_BACKING_FILE_PATH;
              messages.add(getMessage(msgID, tmpBackingFile));
              resultCode = ResultCode.CONSTRAINT_VIOLATION;
            }
            else if (! p.exists())
            {
              int msgID = MSGID_TASKBE_BACKING_FILE_MISSING_PARENT;
              messages.add(getMessage(msgID, tmpBackingFile));
              resultCode = ResultCode.CONSTRAINT_VIOLATION;
            }
            else if (! p.isDirectory())
            {
              int msgID = MSGID_TASKBE_BACKING_FILE_PARENT_NOT_DIRECTORY;
              messages.add(getMessage(msgID, tmpBackingFile));
              resultCode = ResultCode.CONSTRAINT_VIOLATION;
            }
          }
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      int msgID = MSGID_TASKBE_ERROR_GETTING_BACKING_FILE;
      messages.add(getMessage(msgID, ATTR_TASK_BACKING_FILE,
                              stackTraceToSingleLineString(e)));

      resultCode = DirectoryServer.getServerErrorResultCode();
    }


    long tmpRetentionTime = retentionTime;
    description = getMessage(MSGID_TASKBE_DESCRIPTION_RETENTION_TIME);
    IntegerWithUnitConfigAttribute retentionStub =
         new IntegerWithUnitConfigAttribute(ATTR_TASK_RETENTION_TIME,
                                            description, false, timeUnits,
                                            true, 0, false, 0);
    try
    {
      IntegerWithUnitConfigAttribute retentionAttr =
           (IntegerWithUnitConfigAttribute)
           configEntry.getConfigAttribute(retentionStub);
      if (retentionAttr == null)
      {
        int msgID = MSGID_TASKBE_NO_RETENTION_TIME;
        messages.add(getMessage(msgID, ATTR_TASK_RETENTION_TIME));

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = ResultCode.OBJECTCLASS_VIOLATION;
        }
      }
      else
      {
        tmpRetentionTime = retentionAttr.activeCalculatedValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      int msgID = MSGID_TASKBE_ERROR_GETTING_RETENTION_TIME;
      messages.add(getMessage(msgID, ATTR_TASK_RETENTION_TIME,
                              stackTraceToSingleLineString(e)));

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
    }


    if (resultCode == ResultCode.SUCCESS)
    {
      // Everything looks OK, so apply the changes.
      if (retentionTime != tmpRetentionTime)
      {
        retentionTime = tmpRetentionTime;

        int msgID = MSGID_TASKBE_UPDATED_RETENTION_TIME;
        messages.add(getMessage(msgID, retentionTime));
      }


      if (! taskBackingFile.equals(tmpBackingFile))
      {
        taskBackingFile = tmpBackingFile;
        taskScheduler.writeState();

        int msgID = MSGID_TASKBE_UPDATED_BACKING_FILE;
        messages.add(getMessage(msgID, taskBackingFile));
      }
    }


    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * Retrieves the DN of the configuration entry for this task backend.
   *
   * @return  The DN of the configuration entry for this task backend.
   */
  public DN getConfigEntryDN()
  {
    assert debugEnter(CLASS_NAME, "getConfigEntryDN");

    return configEntryDN;
  }



  /**
   * Retrieves the path to the backing file that will hold the scheduled and
   * recurring task definitions.
   *
   * @return  The path to the backing file that will hold the scheduled and
   *          recurring task definitions.
   */
  public String getTaskBackingFile()
  {
    assert debugEnter(CLASS_NAME, "getTaskBackingFile");

    File f = new File(taskBackingFile);
    if (!f.isAbsolute())
    {
      f = new File(DirectoryServer.getServerRoot(), taskBackingFile);
    }
    return f.getPath();
  }



  /**
   * Retrieves the length of time in seconds that information for a task should
   * be retained after processing on it has completed.
   *
   * @return  The length of time in seconds that information for a task should
   *          be retained after processing on it has completed.
   */
  public long getRetentionTime()
  {
    assert debugEnter(CLASS_NAME, "getRetentionTime");

    return retentionTime;
  }



  /**
   * Retrieves the DN of the entry that is the root for all task information in
   * the Directory Server.
   *
   * @return  The DN of the entry that is the root for all task information in
   *          the Directory Server.
   */
  public DN getTaskRootDN()
  {
    assert debugEnter(CLASS_NAME, "getTaskRootDN");

    return taskRootDN;
  }



  /**
   * Retrieves the DN of the entry that is the immediate parent for all
   * recurring task information in the Directory Server.
   *
   * @return  The DN of the entry that is the immediate parent for all recurring
   *          task information in the Directory Server.
   */
  public DN getRecurringTasksParentDN()
  {
    assert debugEnter(CLASS_NAME, "getRecurringTasksParentDN");

    return recurringTaskParentDN;
  }



  /**
   * Retrieves the DN of the entry that is the immediate parent for all
   * scheduled task information in the Directory Server.
   *
   * @return  The DN of the entry that is the immediate parent for all scheduled
   *          task information in the Directory Server.
   */
  public DN getScheduledTasksParentDN()
  {
    assert debugEnter(CLASS_NAME, "getScheduledTasksParentDN");

    return scheduledTaskParentDN;
  }
}

