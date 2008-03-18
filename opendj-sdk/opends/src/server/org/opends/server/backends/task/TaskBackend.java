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
package org.opends.server.backends.task;



import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.Lock;

import org.opends.messages.Message;
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
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.CancelledOperationException;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.IndexType;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.LockManager;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;
import org.opends.server.util.Validator;

import static org.opends.messages.BackendMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
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
  @Override()
  public void configureBackend(Configuration config)
         throws ConfigException
  {
    Validator.ensureNotNull(config);
    Validator.ensureTrue(config instanceof TaskBackendCfg);

    TaskBackendCfg cfg = (TaskBackendCfg)config;

    DN[] baseDNs = new DN[cfg.getBaseDN().size()];
    cfg.getBaseDN().toArray(baseDNs);

    ConfigEntry configEntry = DirectoryServer.getConfigEntry(cfg.dn());

    configEntryDN = configEntry.getDN();


    // Make sure that the provided set of base DNs contains exactly one value.
    // We will only allow one base for task entries.
    if ((baseDNs == null) || (baseDNs.length == 0))
    {
      Message message = ERR_TASKBE_NO_BASE_DNS.get();
      throw new ConfigException(message);
    }
    else if (baseDNs.length > 1)
    {
      Message message = ERR_TASKBE_MULTIPLE_BASE_DNS.get();
      throw new ConfigException(message);
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
        Message message = ERR_TASKBE_CANNOT_DECODE_RECURRING_TASK_BASE_DN.get(
            String.valueOf(recurringTaskBaseString), getExceptionMessage(e));
        throw new ConfigException(message, e);
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
        Message message = ERR_TASKBE_CANNOT_DECODE_SCHEDULED_TASK_BASE_DN.get(
            String.valueOf(scheduledTaskBaseString), getExceptionMessage(e));
        throw new ConfigException(message, e);
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
  @Override()
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
      DirectoryServer.registerBaseDN(taskRootDN, this, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(
          taskRootDN.toString(), getExceptionMessage(e));
      throw new InitializationException(message, e);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
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

      Message message = INFO_TASKBE_INTERRUPTED_BY_SHUTDOWN.get();

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
      DirectoryServer.deregisterBaseDN(taskRootDN);
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
    if (taskScheduler != null)
    {
      return taskScheduler.getEntryCount();
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
  @Override()
  public ConditionResult hasSubordinates(DN entryDN)
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
  public long numSubordinates(DN entryDN, boolean subtree)
      throws DirectoryException
  {
    if (entryDN == null)
    {
      return -1;
    }

    if (entryDN.equals(taskRootDN))
    {
      // scheduled and recurring parents.
      if(!subtree)
      {
        return 2;
      }
      else
      {
        return taskScheduler.getScheduledTaskCount() +
            taskScheduler.getRecurringTaskCount() + 2;
      }
    }
    else if (entryDN.equals(scheduledTaskParentDN))
    {
      return taskScheduler.getScheduledTaskCount();
    }
    else if (entryDN.equals(recurringTaskParentDN))
    {
      return taskScheduler.getRecurringTaskCount();
    }

    DN parentDN = entryDN.getParentDNInSuffix();
    if (parentDN == null)
    {
      return -1;
    }

    if (parentDN.equals(scheduledTaskParentDN) &&
        taskScheduler.getScheduledTask(entryDN) != null)
    {
      return 0;
    }
    else if (parentDN.equals(recurringTaskParentDN) &&
        taskScheduler.getRecurringTask(entryDN) != null)
    {
      return 0;
    }
    else
    {
      return -1;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
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
   * {@inheritDoc}
   */
  @Override()
  public void addEntry(Entry entry, AddOperation addOperation)
         throws DirectoryException
  {
    Entry e = entry.duplicate(false);

    // Get the DN for the entry and then get its parent.
    DN entryDN = e.getDN();
    DN parentDN = entryDN.getParentDNInSuffix();

    if (parentDN == null)
    {
      Message message = ERR_TASKBE_ADD_DISALLOWED_DN.
          get(String.valueOf(scheduledTaskParentDN),
              String.valueOf(recurringTaskParentDN));
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
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
    Message message = ERR_TASKBE_ADD_DISALLOWED_DN.
        get(String.valueOf(scheduledTaskParentDN),
            String.valueOf(recurringTaskParentDN));
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
         throws DirectoryException
  {
    // Get the parent for the provided entry DN.  It must be either the
    // scheduled or recurring task parent DN.
    DN parentDN = entryDN.getParentDNInSuffix();
    if (parentDN == null)
    {
      Message message =
          ERR_TASKBE_DELETE_INVALID_ENTRY.get(String.valueOf(entryDN));
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }
    else if (parentDN.equals(scheduledTaskParentDN))
    {
      // It's a scheduled task.  Make sure that it exists.
      Task t = taskScheduler.getScheduledTask(entryDN);
      if (t == null)
      {
        Message message =
            ERR_TASKBE_DELETE_NO_SUCH_TASK.get(String.valueOf(entryDN));
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
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
        Message message =
            ERR_TASKBE_DELETE_RUNNING.get(String.valueOf(entryDN));
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }
    else if (parentDN.equals(recurringTaskParentDN))
    {
      // It's a recurring task.  Make sure that it exists.
      RecurringTask rt = taskScheduler.getRecurringTask(entryDN);
      if (rt == null)
      {
        Message message = ERR_TASKBE_DELETE_NO_SUCH_RECURRING_TASK.get(
            String.valueOf(entryDN));
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
      }


      // Try to remove the recurring task.  This will fail if there are any
      // associated iterations pending or running.
      taskScheduler.removeRecurringTask(rt.getRecurringTaskID());
    }
    else
    {
      Message message =
          ERR_TASKBE_DELETE_INVALID_ENTRY.get(String.valueOf(entryDN));
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void replaceEntry(Entry entry, ModifyOperation modifyOperation)
         throws DirectoryException
  {
    DN entryDN = entry.getDN();

    Lock entryLock = null;
    if (! taskScheduler.holdsSchedulerLock())
    {
      for (int i=0; i < 3; i++)
      {
        entryLock = LockManager.lockWrite(entryDN);
        if (entryLock != null)
        {
          break;
        }
      }

      if (entryLock == null)
      {
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     ERR_TASKBE_MODIFY_CANNOT_LOCK_ENTRY.get(
                                          String.valueOf(entryDN)));
      }
    }

    try
    {
      // Get the parent for the provided entry DN.  It must be either the
      // scheduled or recurring task parent DN.
      DN parentDN = entryDN.getParentDNInSuffix();
      if (parentDN == null)
      {
        Message message =
            ERR_TASKBE_MODIFY_INVALID_ENTRY.get(String.valueOf(entryDN));
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      else if (parentDN.equals(scheduledTaskParentDN))
      {
        // It's a scheduled task.  Make sure that it exists.
        Task t = taskScheduler.getScheduledTask(entryDN);
        if (t == null)
        {
          Message message =
              ERR_TASKBE_MODIFY_NO_SUCH_TASK.get(String.valueOf(entryDN));
          throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
        }


        // Look at the state of the task.  We will allow anything to be altered
        // for a pending task.  For a running task, we will only allow the state
        // to be altered in order to cancel it.  We will not allow any
        // modifications for completed tasks.
        TaskState state = t.getTaskState();
        if (TaskState.isPending(state))
        {
          Task newTask =
                    taskScheduler.entryToScheduledTask(entry, modifyOperation);
          taskScheduler.removePendingTask(t.getTaskID());
          taskScheduler.scheduleTask(newTask, true);
          return;
        }
        else if (TaskState.isRunning(state))
        {
          // If the task is running, we will only allow it to be cancelled.
          // This will only be allowed using the replace modification type on
          // the ds-task-state attribute if the value starts with "cancel" or
          // "stop".  In that case, we'll cancel the task.
          boolean acceptable = true;
          for (Modification m : modifyOperation.getModifications())
          {
            if (m.isInternal())
            {
              continue;
            }

            if (m.getModificationType() != ModificationType.REPLACE)
            {
              acceptable = false;
              break;
            }

            Attribute a = m.getAttribute();
            AttributeType at = a.getAttributeType();
            if (! at.hasName(ATTR_TASK_STATE))
            {
              acceptable = false;
              break;
            }

            Iterator<AttributeValue> iterator = a.getValues().iterator();
            if (! iterator.hasNext())
            {
              acceptable = false;
              break;
            }

            AttributeValue v = iterator.next();
            String valueString = toLowerCase(v.getStringValue());
            if (! (valueString.startsWith("cancel") ||
                   valueString.startsWith("stop")))
            {
              acceptable = false;
              break;
            }

            if (iterator.hasNext())
            {
              acceptable = false;
              break;
            }
          }

          if (acceptable)
          {
            Message message = INFO_TASKBE_RUNNING_TASK_CANCELLED.get();
            t.interruptTask(TaskState.STOPPED_BY_ADMINISTRATOR, message);
            return;
          }
          else
          {
            Message message =
                 ERR_TASKBE_MODIFY_RUNNING.get(String.valueOf(entryDN));
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                         message);
          }
        }
        else
        {
          Message message =
              ERR_TASKBE_MODIFY_COMPLETED.get(String.valueOf(entryDN));
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                       message);
        }
      }
      else if (parentDN.equals(recurringTaskParentDN))
      {
        // We don't currently support altering recurring tasks.
        Message message =
            ERR_TASKBE_MODIFY_RECURRING.get(String.valueOf(entryDN));
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      else
      {
        Message message =
            ERR_TASKBE_MODIFY_INVALID_ENTRY.get(String.valueOf(entryDN));
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }
    finally
    {
      if (entryLock != null)
      {
        LockManager.unlock(entryDN, entryLock);
      }
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
    Message message = ERR_TASKBE_MODIFY_DN_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
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
        Message message =
            ERR_TASKBE_SEARCH_INVALID_BASE.get(String.valueOf(baseDN));
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
      }
      else if (parentDN.equals(scheduledTaskParentDN))
      {
        Lock lock = taskScheduler.readLockEntry(baseDN);

        try
        {
          Entry e = taskScheduler.getScheduledTaskEntry(baseDN);
          if (e == null)
          {
            Message message =
                ERR_TASKBE_SEARCH_NO_SUCH_TASK.get(String.valueOf(baseDN));
            throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message,
                                         scheduledTaskParentDN, null);
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
            Message message = ERR_TASKBE_SEARCH_NO_SUCH_RECURRING_TASK.get(
                String.valueOf(baseDN));
            throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message,
                                         recurringTaskParentDN, null);
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
        Message message =
            ERR_TASKBE_SEARCH_INVALID_BASE.get(String.valueOf(baseDN));
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
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
    // LDIF exports are supported.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void exportLDIF(LDIFExportConfig exportConfig)
         throws DirectoryException
  {
    // FIXME -- Implement support for exporting to LDIF.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsLDIFImport()
  {
    // This backend does not support LDIF imports.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig)
         throws DirectoryException
  {
    // This backend does not support LDIF imports.
    Message message = ERR_TASKBE_IMPORT_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsBackup()
  {
    // This backend does provide a backup/restore mechanism.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsBackup(BackupConfig backupConfig,
                                StringBuilder unsupportedReason)
  {
    // This backend does provide a backup/restore mechanism.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void createBackup(BackupConfig backupConfig)
         throws DirectoryException
  {
    // NYI -- Create the backup.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void removeBackup(BackupDirectory backupDirectory,
                           String backupID)
         throws DirectoryException
  {
    // NYI -- Remove the backup.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsRestore()
  {
    // This backend does provide a backup/restore mechanism.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
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
                                           List<Message> unacceptableReasons)
  {
    TaskBackendCfg config = (TaskBackendCfg) configuration;
    return isConfigAcceptable(config, unacceptableReasons, null);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(TaskBackendCfg configEntry,
                                            List<Message> unacceptableReasons)
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
                                            List<Message> unacceptableReasons,
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
            unacceptableReasons.add(
                    ERR_TASKBE_BACKING_FILE_EXISTS.get(tmpBackingFile));
            configIsAcceptable = false;
          }
        }
        else
        {
          File p = f.getParentFile();
          if (p == null)
          {
            unacceptableReasons.add(ERR_TASKBE_INVALID_BACKING_FILE_PATH.get(
                    tmpBackingFile));
            configIsAcceptable = false;
          }
          else if (! p.exists())
          {

            unacceptableReasons.add(ERR_TASKBE_BACKING_FILE_MISSING_PARENT.get(
                    p.getPath(),
                    tmpBackingFile));
            configIsAcceptable = false;
          }
          else if (! p.isDirectory())
          {
            unacceptableReasons.add(
                    ERR_TASKBE_BACKING_FILE_PARENT_NOT_DIRECTORY.get(
                            p.getPath(),
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

      unacceptableReasons.add(ERR_TASKBE_ERROR_GETTING_BACKING_FILE.get(
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
    ResultCode         resultCode          = ResultCode.SUCCESS;
    boolean            adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();


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

            messages.add(ERR_TASKBE_BACKING_FILE_EXISTS.get(tmpBackingFile));
            resultCode = ResultCode.CONSTRAINT_VIOLATION;
          }
          else
          {
            File p = f.getParentFile();
            if (p == null)
            {

              messages.add(ERR_TASKBE_INVALID_BACKING_FILE_PATH.get(
                      tmpBackingFile));
              resultCode = ResultCode.CONSTRAINT_VIOLATION;
            }
            else if (! p.exists())
            {

              messages.add(ERR_TASKBE_BACKING_FILE_MISSING_PARENT.get(
                      String.valueOf(p), tmpBackingFile));
              resultCode = ResultCode.CONSTRAINT_VIOLATION;
            }
            else if (! p.isDirectory())
            {

              messages.add(ERR_TASKBE_BACKING_FILE_PARENT_NOT_DIRECTORY.get(
                      String.valueOf(p), tmpBackingFile));
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

      messages.add(ERR_TASKBE_ERROR_GETTING_BACKING_FILE.get(
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

        messages.add(INFO_TASKBE_UPDATED_RETENTION_TIME.get(retentionTime));
      }


      if (! taskBackingFile.equals(tmpBackingFile))
      {
        taskBackingFile = tmpBackingFile;
        taskScheduler.writeState();

        messages.add(INFO_TASKBE_UPDATED_BACKING_FILE.get(taskBackingFile));
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



  /**
   * {@inheritDoc}
   */
  public void preloadEntryCache() throws UnsupportedOperationException {
    throw new UnsupportedOperationException("Operation not supported.");
  }
}

