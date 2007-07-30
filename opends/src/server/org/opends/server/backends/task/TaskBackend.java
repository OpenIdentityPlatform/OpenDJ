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
package org.opends.server.backends.task;



import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.locks.Lock;

import org.opends.server.admin.Configuration;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.TaskBackendCfg;
import org.opends.server.api.Backend;
import org.opends.server.config.ConfigException;
import org.opends.server.config.ConfigEntry;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.CancelledOperationException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;
import org.opends.server.util.Validator;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
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
       implements ConfigurationChangeListener<TaskBackendCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The current configuration state.
  private TaskBackendCfg currentConfig;

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

  // The e-mail address to use for the sender from notification messages.
  private String notificationSenderAddress;

  // The path to the task backing file.
  private String taskBackingFile;

  // The task scheduler that will be responsible for actually invoking scheduled
  // tasks.
  private TaskScheduler taskScheduler;



  /**
   * Creates a new backend with the provided information.  All backend
   * implementations must implement a default constructor that use
   * <CODE>super()</CODE> to invoke this constructor.
   */
  public TaskBackend()
  {
    super();

    // Perform all initialization in initializeBackend.
  }


  /**
   * {@inheritDoc}
   */
  public void configureBackend(Configuration config)
         throws ConfigException
  {
    Validator.ensureNotNull(config);
    Validator.ensureTrue(config instanceof TaskBackendCfg);

    TaskBackendCfg cfg = (TaskBackendCfg)config;

    DN[] baseDNs = new DN[cfg.getBackendBaseDN().size()];
    cfg.getBackendBaseDN().toArray(baseDNs);

    ConfigEntry configEntry = DirectoryServer.getConfigEntry(cfg.dn());

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
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        // This should never happen.
        int    msgID   = MSGID_TASKBE_CANNOT_DECODE_RECURRING_TASK_BASE_DN;
        String message = getMessage(msgID,
                                    String.valueOf(recurringTaskBaseString),
                                    getExceptionMessage(e));
        throw new ConfigException(msgID, message, e);
      }

      String scheduledTaskBaseString = SCHEDULED_TASK_BASE_RDN + "," +
                                       taskRootDN.toString();
      try
      {
        scheduledTaskParentDN = DN.decode(scheduledTaskBaseString);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        // This should never happen.
        int    msgID   = MSGID_TASKBE_CANNOT_DECODE_SCHEDULED_TASK_BASE_DN;
        String message = getMessage(msgID,
                                    String.valueOf(scheduledTaskBaseString),
                                    getExceptionMessage(e));
        throw new ConfigException(msgID, message, e);
      }
    }


    // Get the retention time that will be used to determine how long task
    // information stays around once the associated task is completed.
    retentionTime = cfg.getTaskRetentionTime();


    // Get the notification sender address.
    notificationSenderAddress = cfg.getNotificationSenderAddress();
    if (notificationSenderAddress == null)
    {
      try
      {
        notificationSenderAddress = "opends-task-notification@" +
             InetAddress.getLocalHost().getCanonicalHostName();
      }
      catch (Exception e)
      {
        notificationSenderAddress = "opends-task-notification@opends.org";
      }
    }


    // Get the path to the task data backing file.
    taskBackingFile = cfg.getTaskBackingFile();

    // Define an empty sets for the supported controls and features.
    supportedControls = new HashSet<String>(0);
    supportedFeatures = new HashSet<String>(0);

    currentConfig = cfg;
  }

  /**
   * {@inheritDoc}
   */
  public void initializeBackend()
         throws ConfigException, InitializationException
  {
    // Create the scheduler and initialize it from the backing file.
    taskScheduler = new TaskScheduler(this);
    taskScheduler.start();


    // Register with the Directory Server as a configurable component.
    currentConfig.addTaskChangeListener(this);


    // Register the task base as a private suffix.
    try
    {
      DirectoryServer.registerBaseDN(taskRootDN, this, true, false);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_BACKEND_CANNOT_REGISTER_BASEDN;
      String message = getMessage(msgID, taskRootDN.toString(),
                                  getExceptionMessage(e));
      throw new InitializationException(msgID, message, e);
    }
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
    currentConfig.removeTaskChangeListener(this);


    try
    {
      taskScheduler.stopScheduler();
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
      int    msgID   = MSGID_TASKBE_INTERRUPTED_BY_SHUTDOWN;
      String message = getMessage(msgID);

      taskScheduler.interruptRunningTasks(TaskState.STOPPED_BY_SHUTDOWN,
                                          message, true);
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
      DirectoryServer.deregisterBaseDN(taskRootDN, false);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }
  }



  /**
   * Retrieves the set of base-level DNs that may be used within this backend.
   *
   * @return  The set of base-level DNs that may be used within this backend.
   */
  public DN[] getBaseDNs()
  {
    return baseDNs;
  }



  /**
   * {@inheritDoc}
   */
  public long getEntryCount()
  {
    if (taskScheduler != null)
    {
      return taskScheduler.getEntryCount();
    }

    return -1;
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

    DN parentDN = entryDN.getParentDNInSuffix();
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
    if (entryDN == null)
    {
      return false;
    }

    if (entryDN.equals(taskRootDN) || entryDN.equals(scheduledTaskParentDN) ||
        entryDN.equals(recurringTaskParentDN))
    {
      return true;
    }

    DN parentDN = entryDN.getParentDNInSuffix();
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
    Entry e = entry.duplicate(false);

    // Get the DN for the entry and then get its parent.
    DN entryDN = e.getDN();
    DN parentDN = entryDN.getParentDNInSuffix();

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
      Task task = taskScheduler.entryToScheduledTask(e, addOperation);
      taskScheduler.scheduleTask(task, true);
      return;
    }

    // If the parent DN is equal to the parent for recurring tasks, then try to
    // treat the provided entry like a recurring task.
    if (parentDN.equals(recurringTaskParentDN))
    {
      RecurringTask recurringTask = taskScheduler.entryToRecurringTask(e);
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
    // Get the parent for the provided entry DN.  It must be either the
    // scheduled or recurring task parent DN.
    DN parentDN = entryDN.getParentDNInSuffix();
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
      DN parentDN = baseDN.getParentDNInSuffix();
      if (parentDN == null)
      {
        int    msgID   = MSGID_TASKBE_SEARCH_INVALID_BASE;
        String message = getMessage(msgID, String.valueOf(baseDN));
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, msgID);
      }
      else if (parentDN.equals(scheduledTaskParentDN))
      {
        Lock lock = taskScheduler.readLockEntry(baseDN);

        try
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
        finally
        {
          taskScheduler.unlockEntry(baseDN, lock);
        }
      }
      else if (parentDN.equals(recurringTaskParentDN))
      {
        Lock lock = taskScheduler.readLockEntry(baseDN);

        try
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
        finally
        {
          taskScheduler.unlockEntry(baseDN, lock);
        }
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
    return supportedControls;
  }



  /**
   * Retrieves the OIDs of the features that may be supported by this backend.
   *
   * @return  The OIDs of the features that may be supported by this backend.
   */
  public HashSet<String> getSupportedFeatures()
  {
    return supportedFeatures;
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
    // LDIF exports are supported.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public void exportLDIF(LDIFExportConfig exportConfig)
         throws DirectoryException
  {
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
    // This backend does not support LDIF imports.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig)
         throws DirectoryException
  {
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
    // This backend does provide a backup/restore mechanism.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public void createBackup(BackupConfig backupConfig)
         throws DirectoryException
  {
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
    // This backend does provide a backup/restore mechanism.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public void restoreBackup(RestoreConfig restoreConfig)
         throws DirectoryException
  {
    // NYI -- Restore the backup.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(Configuration configuration,
                                           List<String> unacceptableReasons)
  {
    TaskBackendCfg config = (TaskBackendCfg) configuration;
    return isConfigAcceptable(config, unacceptableReasons, null);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(TaskBackendCfg configEntry,
                                            List<String> unacceptableReasons)
  {
    return isConfigAcceptable(configEntry, unacceptableReasons,
                              taskBackingFile);
  }



  /**
   * Indicates whether the provided configuration is acceptable for this task
   * backend.
   *
   * @param  config               The configuration for which to make the
   *                              determination.
   * @param  unacceptableReasons  A list into which the unacceptable reasons
   *                              should be placed.
   * @param  taskBackingFile      The currently-configured task backing file, or
   *                              {@code null} if it should not be taken into
   *                              account.
   *
   * @return  {@code true} if the configuration is acceptable, or {@code false}
   *          if not.
   */
  private static boolean isConfigAcceptable(TaskBackendCfg config,
                                            List<String> unacceptableReasons,
                                            String taskBackingFile)
  {
    boolean configIsAcceptable = true;


    try
    {
      String tmpBackingFile = config.getTaskBackingFile();
      if ((taskBackingFile == null) ||
          (! taskBackingFile.equals(tmpBackingFile)))
      {
        File f = getFileForPath(tmpBackingFile);
        if (f.exists())
        {
          // This is only a problem if it's different from the active one.
          if (taskBackingFile != null)
          {
            int msgID = MSGID_TASKBE_BACKING_FILE_EXISTS;
            unacceptableReasons.add(getMessage(msgID, tmpBackingFile));
            configIsAcceptable = false;
          }
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
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_TASKBE_ERROR_GETTING_BACKING_FILE;
      unacceptableReasons.add(getMessage(msgID, ATTR_TASK_BACKING_FILE,
                                         getExceptionMessage(e)));

      configIsAcceptable = false;
    }

    return configIsAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(TaskBackendCfg configEntry)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    String tmpBackingFile = taskBackingFile;
    try
    {
      {
        tmpBackingFile = configEntry.getTaskBackingFile();
        if (! taskBackingFile.equals(tmpBackingFile))
        {
          File f = getFileForPath(tmpBackingFile);
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
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_TASKBE_ERROR_GETTING_BACKING_FILE;
      messages.add(getMessage(msgID, ATTR_TASK_BACKING_FILE,
                              getExceptionMessage(e)));

      resultCode = DirectoryServer.getServerErrorResultCode();
    }


    long tmpRetentionTime = configEntry.getTaskRetentionTime();


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


    String tmpNotificationAddress = configEntry.getNotificationSenderAddress();
    if (tmpNotificationAddress == null)
    {
      try
      {
        tmpNotificationAddress = "opends-task-notification@" +
             InetAddress.getLocalHost().getCanonicalHostName();
      }
      catch (Exception e)
      {
        tmpNotificationAddress = "opends-task-notification@opends.org";
      }
    }
    notificationSenderAddress = tmpNotificationAddress;


    currentConfig = configEntry;
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * Retrieves the DN of the configuration entry for this task backend.
   *
   * @return  The DN of the configuration entry for this task backend.
   */
  public DN getConfigEntryDN()
  {
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
    File f = getFileForPath(taskBackingFile);
    return f.getPath();
  }



  /**
   * Retrieves the sender address that should be used for e-mail notifications
   * of task completion.
   *
   * @return  The sender address that should be used for e-mail notifications of
   *          task completion.
   */
  public String getNotificationSenderAddress()
  {
    return notificationSenderAddress;
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
    return scheduledTaskParentDN;
  }



  /**
   * Retrieves the scheduled task for the entry with the provided DN.
   *
   * @param  taskEntryDN  The DN of the entry for the task to retrieve.
   *
   * @return  The requested task, or {@code null} if there is no task with the
   *          specified entry DN.
   */
  public Task getScheduledTask(DN taskEntryDN)
  {
    return taskScheduler.getScheduledTask(taskEntryDN);
  }



  /**
   * Retrieves the recurring task for the entry with the provided DN.
   *
   * @param  taskEntryDN  The DN of the entry for the recurring task to
   *                      retrieve.
   *
   * @return  The requested recurring task, or {@code null} if there is no task
   *          with the specified entry DN.
   */
  public RecurringTask getRecurringTask(DN taskEntryDN)
  {
    return taskScheduler.getRecurringTask(taskEntryDN);
  }
}

