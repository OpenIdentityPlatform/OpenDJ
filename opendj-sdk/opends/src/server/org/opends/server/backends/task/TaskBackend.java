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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.Lock;

import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.crypto.Mac;
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
import org.opends.server.types.BackupInfo;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.CryptoManager;
import org.opends.server.types.CryptoManagerException;
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
import org.opends.server.util.DynamicConstants;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.Validator;

import static org.opends.messages.BackendMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.util.ServerConstants.*;



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
        if (t.isRecurring()) {
          taskScheduler.removeRecurringTaskIteration(t.getTaskID());
        } else {
          taskScheduler.removePendingTask(t.getTaskID());
        }
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
  public void replaceEntry(Entry oldEntry, Entry newEntry,
      ModifyOperation modifyOperation) throws DirectoryException
  {
    DN entryDN = newEntry.getDN();

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
        if (TaskState.isPending(state) && !t.isRecurring())
        {
          Task newTask = taskScheduler.entryToScheduledTask(newEntry,
              modifyOperation);
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
          boolean acceptable = isReplaceEntryAcceptable(modifyOperation);

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
        else if (TaskState.isPending(state) && t.isRecurring())
        {
          // Pending recurring task iterations can only be canceled.
          boolean acceptable = isReplaceEntryAcceptable(modifyOperation);

          if (acceptable)
          {
            Task newTask = taskScheduler.entryToScheduledTask(newEntry,
              modifyOperation);
            if (newTask.getTaskState() ==
              TaskState.CANCELED_BEFORE_STARTING)
            {
              taskScheduler.removePendingTask(t.getTaskID());
              taskScheduler.scheduleTask(newTask, true);
            }
            else if (newTask.getTaskState() ==
              TaskState.STOPPED_BY_ADMINISTRATOR)
            {
              Message message = INFO_TASKBE_RUNNING_TASK_CANCELLED.get();
              t.interruptTask(TaskState.STOPPED_BY_ADMINISTRATOR, message);
            }
              return;
          }
          else
          {
            Message message =
              ERR_TASKBE_MODIFY_RECURRING.get(String.valueOf(entryDN));
            throw new DirectoryException(
              ResultCode.UNWILLING_TO_PERFORM, message);
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
   * Helper to determine if requested modifications are acceptable.
   * @param modifyOperation associated with requested modifications.
   * @return <CODE>true</CODE> if requested modifications are
   *         acceptable, <CODE>false</CODE> otherwise.
   */
  private boolean isReplaceEntryAcceptable(ModifyOperation modifyOperation)
  {
    boolean acceptable = true;

    for (Modification m : modifyOperation.getModifications()) {
      if (m.isInternal()) {
        continue;
      }

      if (m.getModificationType() != ModificationType.REPLACE) {
        acceptable = false;
        break;
      }

      Attribute a = m.getAttribute();
      AttributeType at = a.getAttributeType();
      if (!at.hasName(ATTR_TASK_STATE)) {
        acceptable = false;
        break;
      }

      Iterator<AttributeValue> iterator = a.iterator();
      if (!iterator.hasNext()) {
        acceptable = false;
        break;
      }

      AttributeValue v = iterator.next();
      String valueString = toLowerCase(v.toString());
      if (!(valueString.startsWith("cancel") ||
        valueString.startsWith("stop"))) {
        acceptable = false;
        break;
      }

      if (iterator.hasNext()) {
        acceptable = false;
        break;
      }
    }

    return acceptable;
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
         throws DirectoryException, CanceledOperationException {
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
    File taskFile = getFileForPath(taskBackingFile);

    // Read from.
    LDIFReader ldifReader;
    try
    {
      ldifReader = new LDIFReader(new LDIFImportConfig(taskFile.getPath()));
    }
    catch (Exception e)
    {
      Message message =
          ERR_TASKS_CANNOT_EXPORT_TO_FILE.get(String.valueOf(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
    }

    // Write to.
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

      Message message = ERR_TASKS_CANNOT_EXPORT_TO_FILE.get(
          stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }

    // Copy record by record.
    try
    {
      while (true)
      {
        Entry e = null;
        try
        {
          e = ldifReader.readEntry();
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
                ERR_TASKS_CANNOT_EXPORT_TO_FILE.get(String.valueOf(e));
            throw new DirectoryException(
                           DirectoryServer.getServerErrorResultCode(),
                           message, le);
          }
          else
          {
            continue;
          }
        }
        ldifWriter.writeEntry(e);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
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
      try
      {
        ldifReader.close();
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
    // Get the properties to use for the backup.  We don't care whether or not
    // it's incremental, so there's no need to get that.
    String          backupID        = backupConfig.getBackupID();
    BackupDirectory backupDirectory = backupConfig.getBackupDirectory();
    boolean         compress        = backupConfig.compressData();
    boolean         encrypt         = backupConfig.encryptData();
    boolean         hash            = backupConfig.hashData();
    boolean         signHash        = backupConfig.signHash();


    // Create a hash map that will hold the extra backup property information
    // for this backup.
    HashMap<String,String> backupProperties = new HashMap<String,String>();


    // Get the crypto manager and use it to obtain references to the message
    // digest and/or MAC to use for hashing and/or signing.
    CryptoManager cryptoManager   = DirectoryServer.getCryptoManager();
    Mac           mac             = null;
    MessageDigest digest          = null;
    String        digestAlgorithm = null;
    String        macKeyID    = null;

    if (hash)
    {
      if (signHash)
      {
        try
        {
          macKeyID = cryptoManager.getMacEngineKeyEntryID();
          backupProperties.put(BACKUP_PROPERTY_MAC_KEY_ID, macKeyID);

          mac = cryptoManager.getMacEngine(macKeyID);
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          Message message = ERR_TASKS_BACKUP_CANNOT_GET_MAC.get(
              macKeyID, stackTraceToSingleLineString(e));
          throw new DirectoryException(
                         DirectoryServer.getServerErrorResultCode(), message,
                         e);
        }
      }
      else
      {
        digestAlgorithm = cryptoManager.getPreferredMessageDigestAlgorithm();
        backupProperties.put(BACKUP_PROPERTY_DIGEST_ALGORITHM, digestAlgorithm);

        try
        {
          digest = cryptoManager.getPreferredMessageDigest();
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          Message message = ERR_TASKS_BACKUP_CANNOT_GET_DIGEST.get(
              digestAlgorithm, stackTraceToSingleLineString(e));
          throw new DirectoryException(
                         DirectoryServer.getServerErrorResultCode(), message,
                         e);
        }
      }
    }


    // Create an output stream that will be used to write the archive file.  At
    // its core, it will be a file output stream to put a file on the disk.  If
    // we are to encrypt the data, then that file output stream will be wrapped
    // in a cipher output stream.  The resulting output stream will then be
    // wrapped by a zip output stream (which may or may not actually use
    // compression).
    String filename = null;
    OutputStream outputStream;
    try
    {
      filename = TASKS_BACKUP_BASE_FILENAME + backupID;
      File archiveFile = new File(backupDirectory.getPath() + File.separator +
                                  filename);
      if (archiveFile.exists())
      {
        int i=1;
        while (true)
        {
          archiveFile = new File(backupDirectory.getPath() + File.separator +
                                 filename  + "." + i);
          if (archiveFile.exists())
          {
            i++;
          }
          else
          {
            filename = filename + "." + i;
            break;
          }
        }
      }

      outputStream = new FileOutputStream(archiveFile, false);
      backupProperties.put(BACKUP_PROPERTY_ARCHIVE_FILENAME, filename);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_TASKS_BACKUP_CANNOT_CREATE_ARCHIVE_FILE.
          get(String.valueOf(filename), backupDirectory.getPath(),
              getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
    }


    // If we should encrypt the data, then wrap the output stream in a cipher
    // output stream.
    if (encrypt)
    {
      try
      {
        outputStream
                = cryptoManager.getCipherOutputStream(outputStream);
      }
      catch (CryptoManagerException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message = ERR_TASKS_BACKUP_CANNOT_GET_CIPHER.get(
                stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, e);
      }
    }


    // Wrap the file output stream in a zip output stream.
    ZipOutputStream zipStream = new ZipOutputStream(outputStream);

    Message message = ERR_TASKS_BACKUP_ZIP_COMMENT.get(
            DynamicConstants.PRODUCT_NAME,
            backupID);
    zipStream.setComment(String.valueOf(message));

    if (compress)
    {
      zipStream.setLevel(Deflater.DEFAULT_COMPRESSION);
    }
    else
    {
      zipStream.setLevel(Deflater.NO_COMPRESSION);
    }

    // Take tasks file and write it to the zip stream. If we
    // are using a hash or MAC, then calculate that as well.
    byte[] buffer = new byte[8192];
    File tasksFile = getFileForPath(taskBackingFile);
    String baseName = tasksFile.getName();

    // We'll put the name in the hash, too.
    if (hash) {
      if (signHash) {
        mac.update(getBytes(baseName));
      } else {
        digest.update(getBytes(baseName));
      }
    }

    InputStream inputStream = null;
    try {
      ZipEntry zipEntry = new ZipEntry(baseName);
      zipStream.putNextEntry(zipEntry);

      inputStream = new FileInputStream(tasksFile);
      while (true) {
        int bytesRead = inputStream.read(buffer);
        if (bytesRead < 0 || backupConfig.isCancelled()) {
          break;
        }

        if (hash) {
          if (signHash) {
            mac.update(buffer, 0, bytesRead);
          } else {
            digest.update(buffer, 0, bytesRead);
          }
        }

        zipStream.write(buffer, 0, bytesRead);
      }

      zipStream.closeEntry();
      inputStream.close();
    } catch (Exception e) {
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      try {
        inputStream.close();
      } catch (Exception e2) {
      }

      try {
        zipStream.close();
      } catch (Exception e2) {
      }

      message = ERR_TASKS_BACKUP_CANNOT_BACKUP_TASKS_FILE.get(baseName,
        stackTraceToSingleLineString(e));
      throw new DirectoryException(
        DirectoryServer.getServerErrorResultCode(),
        message, e);
    }

    // We're done writing the file, so close the zip stream (which should also
    // close the underlying stream).
    try
    {
      zipStream.close();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      message = ERR_TASKS_BACKUP_CANNOT_CLOSE_ZIP_STREAM.get(
          filename, backupDirectory.getPath(), stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
    }


    // Get the digest or MAC bytes if appropriate.
    byte[] digestBytes = null;
    byte[] macBytes    = null;
    if (hash)
    {
      if (signHash)
      {
        macBytes = mac.doFinal();
      }
      else
      {
        digestBytes = digest.digest();
      }
    }


    // Create the backup info structure for this backup and add it to the backup
    // directory.
    // FIXME -- Should I use the date from when I started or finished?
    BackupInfo backupInfo = new BackupInfo(backupDirectory, backupID,
                                           new Date(), false, compress,
                                           encrypt, digestBytes, macBytes,
                                           null, backupProperties);

    try
    {
      backupDirectory.addBackup(backupInfo);
      backupDirectory.writeBackupDirectoryDescriptor();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      message = ERR_TASKS_BACKUP_CANNOT_UPDATE_BACKUP_DESCRIPTOR.get(
          backupDirectory.getDescriptorPath(), stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void removeBackup(BackupDirectory backupDirectory,
                           String backupID)
         throws DirectoryException
  {
    BackupInfo backupInfo = backupDirectory.getBackupInfo(backupID);
    if (backupInfo == null)
    {
      Message message = ERR_BACKUP_MISSING_BACKUPID.get(
        backupDirectory.getPath(), backupID);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }

    HashMap<String,String> backupProperties = backupInfo.getBackupProperties();

    String archiveFilename =
         backupProperties.get(BACKUP_PROPERTY_ARCHIVE_FILENAME);
    File archiveFile = new File(backupDirectory.getPath(), archiveFilename);

    try
    {
      backupDirectory.removeBackup(backupID);
    }
    catch (ConfigException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   e.getMessageObject());
    }

    try
    {
      backupDirectory.writeBackupDirectoryDescriptor();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_BACKUP_CANNOT_UPDATE_BACKUP_DESCRIPTOR.get(
        backupDirectory.getDescriptorPath(), stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
    }

    // Remove the archive file.
    archiveFile.delete();
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
    // First, make sure that the requested backup exists.
    BackupDirectory backupDirectory = restoreConfig.getBackupDirectory();
    String          backupPath      = backupDirectory.getPath();
    String          backupID        = restoreConfig.getBackupID();
    BackupInfo      backupInfo      = backupDirectory.getBackupInfo(backupID);
    boolean         verifyOnly      = restoreConfig.verifyOnly();

    if (backupInfo == null)
    {
      Message message =
          ERR_TASKS_RESTORE_NO_SUCH_BACKUP.get(backupID, backupPath);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }

    // Read the backup info structure to determine the name of the file that
    // contains the archive.  Then make sure that file exists.
    String backupFilename =
         backupInfo.getBackupProperty(BACKUP_PROPERTY_ARCHIVE_FILENAME);
    if (backupFilename == null)
    {
      Message message =
          ERR_TASKS_RESTORE_NO_BACKUP_FILE.get(backupID, backupPath);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }

    File backupFile = new File(backupPath + File.separator + backupFilename);
    try
    {
      if (! backupFile.exists())
      {
        Message message =
            ERR_TASKS_RESTORE_NO_SUCH_FILE.get(backupID, backupFile.getPath());
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message);
      }
    }
    catch (DirectoryException de)
    {
      throw de;
    }
    catch (Exception e)
    {
      Message message = ERR_TASKS_RESTORE_CANNOT_CHECK_FOR_ARCHIVE.get(
          backupID, backupFile.getPath(), stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
    }

    // If the backup is hashed, then we need to get the message digest to use
    // to verify it.
    byte[] unsignedHash = backupInfo.getUnsignedHash();
    MessageDigest digest = null;
    if (unsignedHash != null)
    {
      String digestAlgorithm =
           backupInfo.getBackupProperty(BACKUP_PROPERTY_DIGEST_ALGORITHM);
      if (digestAlgorithm == null)
      {
        Message message = ERR_TASKS_RESTORE_UNKNOWN_DIGEST.get(backupID);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message);
      }

      try
      {
        digest = DirectoryServer.getCryptoManager().getMessageDigest(
                                                         digestAlgorithm);
      }
      catch (Exception e)
      {
        Message message =
            ERR_TASKS_RESTORE_CANNOT_GET_DIGEST.get(backupID, digestAlgorithm);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, e);
      }
    }

    // If the backup is signed, then we need to get the MAC to use to verify it.
    byte[] signedHash = backupInfo.getSignedHash();
    Mac mac = null;
    if (signedHash != null)
    {
      String macKeyID =
           backupInfo.getBackupProperty(BACKUP_PROPERTY_MAC_KEY_ID);
      if (macKeyID == null)
      {
        Message message = ERR_TASKS_RESTORE_UNKNOWN_MAC.get(backupID);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message);
      }

      try
      {
        mac = DirectoryServer.getCryptoManager().getMacEngine(macKeyID);
      }
      catch (Exception e)
      {
        Message message = ERR_TASKS_RESTORE_CANNOT_GET_MAC.get(
            backupID, macKeyID);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, e);
      }
    }

    // Create the input stream that will be used to read the backup file.  At
    // its core, it will be a file input stream.
    InputStream inputStream;
    try
    {
      inputStream = new FileInputStream(backupFile);
    }
    catch (Exception e)
    {
      Message message = ERR_TASKS_RESTORE_CANNOT_OPEN_BACKUP_FILE.get(
          backupID, backupFile.getPath(), stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
    }

    // If the backup is encrypted, then we need to wrap the file input stream
    // in a cipher input stream.
    if (backupInfo.isEncrypted())
    {
      try
      {
        inputStream = DirectoryServer.getCryptoManager()
                                         .getCipherInputStream(inputStream);
      }
      catch (CryptoManagerException e)
      {
        Message message = ERR_TASKS_RESTORE_CANNOT_GET_CIPHER.get(
                backupFile.getPath(), stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, e);
      }
    }

    // Now wrap the resulting input stream in a zip stream so that we can read
    // its contents.  We don't need to worry about whether to use compression or
    // not because it will be handled automatically.
    ZipInputStream zipStream = new ZipInputStream(inputStream);

    // Read through the archive file an entry at a time.  For each entry, update
    // the digest or MAC if necessary.
    byte[] buffer = new byte[8192];
    while (true)
    {
      ZipEntry zipEntry;
      try
      {
        zipEntry = zipStream.getNextEntry();
      }
      catch (Exception e)
      {
        Message message = ERR_TASKS_RESTORE_CANNOT_GET_ZIP_ENTRY.get(
            backupID, backupFile.getPath(), stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, e);
      }

      if (zipEntry == null)
      {
        break;
      }

      // Get the filename for the zip entry and update the digest or MAC as
      // necessary.
      String fileName = zipEntry.getName();
      if (digest != null)
      {
        digest.update(getBytes(fileName));
      }
      if (mac != null)
      {
        mac.update(getBytes(fileName));
      }

      // If we're doing the restore, then create the output stream to write the
      // file.
      File tasksFile = getFileForPath(taskBackingFile);
      String baseDirPath = tasksFile.getParent();
      OutputStream outputStream = null;

      if (!verifyOnly)
      {
        String filePath = baseDirPath + File.separator + fileName;
        try
        {
          outputStream = new FileOutputStream(filePath);
        }
        catch (Exception e)
        {
          Message message = ERR_TASKS_RESTORE_CANNOT_CREATE_FILE.get(
              backupID, filePath, stackTraceToSingleLineString(e));
          throw new DirectoryException(
                         DirectoryServer.getServerErrorResultCode(), message,
                         e);
        }
      }

      // Read the contents of the file and update the digest or MAC as
      // necessary.
      try
      {
        while (true)
        {
          int bytesRead = zipStream.read(buffer);
          if (bytesRead < 0)
          {
            // We've reached the end of the entry.
            break;
          }

          // Update the digest or MAC if appropriate.
          if (digest != null)
          {
            digest.update(buffer, 0, bytesRead);
          }

          if (mac != null)
          {
            mac.update(buffer, 0, bytesRead);
          }

          //  Write the data to the output stream if appropriate.
          if (outputStream != null)
          {
            outputStream.write(buffer, 0, bytesRead);
          }
        }

        // We're at the end of the file so close the output stream if we're
        // writing it.
        if (outputStream != null)
        {
          outputStream.close();
        }
      }
      catch (Exception e)
      {
        Message message = ERR_TASKS_RESTORE_CANNOT_PROCESS_ARCHIVE_FILE.get(
            backupID, fileName, stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, e);
      }
    }

    // Close the zip stream since we don't need it anymore.
    try
    {
      zipStream.close();
    }
    catch (Exception e)
    {
      Message message = ERR_TASKS_RESTORE_ERROR_ON_ZIP_STREAM_CLOSE.get(
          backupID, backupFile.getPath(), stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
    }

    // At this point, we should be done with the contents of the ZIP file and
    // the restore should be complete.  If we were generating a digest or MAC,
    // then make sure it checks out.
    if (digest != null)
    {
      byte[] calculatedHash = digest.digest();
      if (Arrays.equals(calculatedHash, unsignedHash))
      {
        Message message = NOTE_TASKS_RESTORE_UNSIGNED_HASH_VALID.get();
        logError(message);
      }
      else
      {
        Message message =
            ERR_TASKS_RESTORE_UNSIGNED_HASH_INVALID.get(backupID);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message);
      }
    }

    if (mac != null)
    {
      byte[] calculatedSignature = mac.doFinal();
      if (Arrays.equals(calculatedSignature, signedHash))
      {
        Message message = NOTE_TASKS_RESTORE_SIGNED_HASH_VALID.get();
        logError(message);
      }
      else
      {
        Message message = ERR_TASKS_RESTORE_SIGNED_HASH_INVALID.get(backupID);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message);
      }
    }

    // If we are just verifying the archive, then we're done.
    if (verifyOnly)
    {
      Message message =
          NOTE_TASKS_RESTORE_VERIFY_SUCCESSFUL.get(backupID, backupPath);
      logError(message);
      return;
    }

    // If we've gotten here, then the archive was restored successfully.
    Message message = NOTE_TASKS_RESTORE_SUCCESSFUL.get(backupID, backupPath);
    logError(message);
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
  @Override
  public void preloadEntryCache() throws UnsupportedOperationException {
    throw new UnsupportedOperationException("Operation not supported.");
  }
}

