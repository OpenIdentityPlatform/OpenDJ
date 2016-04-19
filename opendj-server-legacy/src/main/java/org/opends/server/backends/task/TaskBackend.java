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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.backends.task;

import static org.forgerock.util.Reject.*;
import static org.opends.messages.BackendMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.io.FileFilter;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.server.config.server.TaskBackendCfg;
import org.forgerock.util.Reject;
import org.opends.server.api.Backend;
import org.opends.server.api.Backupable;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.types.Attribute;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.IndexType;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.LockManager.DNLock;
import org.opends.server.types.Modification;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.SearchFilter;
import org.opends.server.util.BackupManager;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.StaticUtils;

/**
 * This class provides an implementation of a Directory Server backend that may
 * be used to execute various kinds of administrative tasks on a one-time or
 * recurring basis.
 */
public class TaskBackend
       extends Backend<TaskBackendCfg>
       implements ConfigurationChangeListener<TaskBackendCfg>, Backupable
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The current configuration state. */
  private TaskBackendCfg currentConfig;
  /** The DN of the configuration entry for this backend. */
  private DN configEntryDN;

  /** The DN of the entry that will serve as the parent for all recurring task entries. */
  private DN recurringTaskParentDN;
  /** The DN of the entry that will serve as the parent for all scheduled task entries. */
  private DN scheduledTaskParentDN;
  /** The DN of the entry that will serve as the root for all task entries. */
  private DN taskRootDN;

  /** The set of base DNs defined for this backend. */
  private Set<DN> baseDNs;

  /**
   * The length of time in seconds after a task is completed that it should be
   * removed from the set of scheduled tasks.
   */
  private long retentionTime;

  /** The e-mail address to use for the sender from notification messages. */
  private String notificationSenderAddress;

  /** The path to the task backing file. */
  private String taskBackingFile;
  /** The task scheduler that will be responsible for actually invoking scheduled tasks. */
  private TaskScheduler taskScheduler;

  private ServerContext serverContext;

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

  @Override
  public void configureBackend(TaskBackendCfg cfg, ServerContext serverContext) throws ConfigException
  {
    Reject.ifNull(cfg);
    this.serverContext = serverContext;

    Entry configEntry = DirectoryServer.getConfigEntry(cfg.dn());
    configEntryDN = configEntry.getName();

    // Make sure that the provided set of base DNs contains exactly one value.
    // We will only allow one base for task entries.
    final Set<DN> baseDNs = cfg.getBaseDN();
    if (baseDNs.isEmpty())
    {
      throw new ConfigException(ERR_TASKBE_NO_BASE_DNS.get());
    }
    else if (baseDNs.size() > 1)
    {
      throw new ConfigException(ERR_TASKBE_MULTIPLE_BASE_DNS.get());
    }
    else
    {
      this.baseDNs = baseDNs;
      taskRootDN = baseDNs.iterator().next();

      String recurringTaskBaseString = RECURRING_TASK_BASE_RDN + "," +
                                       taskRootDN;
      try
      {
        recurringTaskParentDN = DN.valueOf(recurringTaskBaseString);
      }
      catch (Exception e)
      {
        logger.traceException(e);

        // This should never happen.
        LocalizableMessage message = ERR_TASKBE_CANNOT_DECODE_RECURRING_TASK_BASE_DN.get(
            recurringTaskBaseString, getExceptionMessage(e));
        throw new ConfigException(message, e);
      }

      String scheduledTaskBaseString = SCHEDULED_TASK_BASE_RDN + "," +
                                       taskRootDN;
      try
      {
        scheduledTaskParentDN = DN.valueOf(scheduledTaskBaseString);
      }
      catch (Exception e)
      {
        logger.traceException(e);

        // This should never happen.
        LocalizableMessage message = ERR_TASKBE_CANNOT_DECODE_SCHEDULED_TASK_BASE_DN.get(
            scheduledTaskBaseString, getExceptionMessage(e));
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
        notificationSenderAddress = "opendj-task-notification@" +
             InetAddress.getLocalHost().getCanonicalHostName();
      }
      catch (Exception e)
      {
        notificationSenderAddress = "opendj-task-notification@opendj.org";
      }
    }

    // Get the path to the task data backing file.
    taskBackingFile = cfg.getTaskBackingFile();

    currentConfig = cfg;
  }

  @Override
  public void openBackend()
         throws ConfigException, InitializationException
  {
    // Create the scheduler and initialize it from the backing file.
    taskScheduler = new TaskScheduler(serverContext, this);
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
      logger.traceException(e);

      LocalizableMessage message = ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(
          taskRootDN, getExceptionMessage(e));
      throw new InitializationException(message, e);
    }
  }

  @Override
  public void closeBackend()
  {
    currentConfig.removeTaskChangeListener(this);

    try
    {
      taskScheduler.stopScheduler();
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }

    try
    {
      LocalizableMessage message = INFO_TASKBE_INTERRUPTED_BY_SHUTDOWN.get();

      taskScheduler.interruptRunningTasks(TaskState.STOPPED_BY_SHUTDOWN,
                                          message, true);
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }

    try
    {
      DirectoryServer.deregisterBaseDN(taskRootDN);
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }
  }

  @Override
  public Set<DN> getBaseDNs()
  {
    return baseDNs;
  }

  @Override
  public long getEntryCount()
  {
    if (taskScheduler != null)
    {
      return taskScheduler.getEntryCount();
    }

    return -1;
  }

  @Override
  public boolean isIndexed(AttributeType attributeType, IndexType indexType)
  {
    // All searches in this backend will always be considered indexed.
    return true;
  }

  @Override
  public ConditionResult hasSubordinates(DN entryDN)
         throws DirectoryException
  {
    long ret = numSubordinates(entryDN, false);
    if(ret < 0)
    {
      return ConditionResult.UNDEFINED;
    }
    return ConditionResult.valueOf(ret != 0);
  }

  @Override
  public long getNumberOfEntriesInBaseDN(DN baseDN) throws DirectoryException {
    checkNotNull(baseDN, "baseDN must not be null");
    return numSubordinates(baseDN, true) + 1;
  }

  @Override
  public long getNumberOfChildren(DN parentDN) throws DirectoryException {
    checkNotNull(parentDN, "parentDN must not be null");
    return numSubordinates(parentDN, false);
  }

  private long numSubordinates(DN entryDN, boolean subtree) throws DirectoryException
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

    DN parentDN = DirectoryServer.getParentDNInSuffix(entryDN);
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

  @Override
  public Entry getEntry(DN entryDN)
         throws DirectoryException
  {
    if (entryDN == null)
    {
      return null;
    }

    DNLock lock = taskScheduler.readLockEntry(entryDN);
    try
    {
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

      DN parentDN = DirectoryServer.getParentDNInSuffix(entryDN);
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
        // If we've gotten here then this is not an entry
        // that should exist in the task backend.
        return null;
      }
    }
    finally
    {
      lock.unlock();
    }
  }

  @Override
  public void addEntry(Entry entry, AddOperation addOperation)
         throws DirectoryException
  {
    Entry e = entry.duplicate(false);

    // Get the DN for the entry and then get its parent.
    DN entryDN = e.getName();
    DN parentDN = DirectoryServer.getParentDNInSuffix(entryDN);

    if (parentDN == null)
    {
      LocalizableMessage message = ERR_TASKBE_ADD_DISALLOWED_DN.
          get(scheduledTaskParentDN, recurringTaskParentDN);
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
    LocalizableMessage message = ERR_TASKBE_ADD_DISALLOWED_DN.
        get(scheduledTaskParentDN, recurringTaskParentDN);
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }

  @Override
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
         throws DirectoryException
  {
    // Get the parent for the provided entry DN.  It must be either the
    // scheduled or recurring task parent DN.
    DN parentDN = DirectoryServer.getParentDNInSuffix(entryDN);
    if (parentDN == null)
    {
      LocalizableMessage message = ERR_TASKBE_DELETE_INVALID_ENTRY.get(entryDN);
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }
    else if (parentDN.equals(scheduledTaskParentDN))
    {
      // It's a scheduled task.  Make sure that it exists.
      Task t = taskScheduler.getScheduledTask(entryDN);
      if (t == null)
      {
        LocalizableMessage message = ERR_TASKBE_DELETE_NO_SUCH_TASK.get(entryDN);
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
      }

      // Look at the state of the task.  We will allow pending and completed
      // tasks to be removed, but not running tasks.
      TaskState state = t.getTaskState();
      if (TaskState.isPending(state))
      {
        if (t.isRecurring()) {
          taskScheduler.removePendingTask(t.getTaskID());
          long scheduledStartTime = t.getScheduledStartTime();
          long currentSystemTime = System.currentTimeMillis();
          if (scheduledStartTime < currentSystemTime) {
            scheduledStartTime = currentSystemTime;
          }
          GregorianCalendar calendar = new GregorianCalendar();
          calendar.setTimeInMillis(scheduledStartTime);
          taskScheduler.scheduleNextRecurringTaskIteration(t,
                  calendar);
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
        LocalizableMessage message = ERR_TASKBE_DELETE_RUNNING.get(entryDN);
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }
    else if (parentDN.equals(recurringTaskParentDN))
    {
      // It's a recurring task.  Make sure that it exists.
      RecurringTask rt = taskScheduler.getRecurringTask(entryDN);
      if (rt == null)
      {
        LocalizableMessage message = ERR_TASKBE_DELETE_NO_SUCH_RECURRING_TASK.get(entryDN);
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
      }

      taskScheduler.removeRecurringTask(rt.getRecurringTaskID());
    }
    else
    {
      LocalizableMessage message = ERR_TASKBE_DELETE_INVALID_ENTRY.get(entryDN);
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }
  }

  @Override
  public void replaceEntry(Entry oldEntry, Entry newEntry,
      ModifyOperation modifyOperation) throws DirectoryException
  {
    DN entryDN = newEntry.getName();
    DNLock entryLock = null;
    if (! taskScheduler.holdsSchedulerLock())
    {
      entryLock = DirectoryServer.getLockManager().tryWriteLockEntry(entryDN);
      if (entryLock == null)
      {
        throw new DirectoryException(ResultCode.BUSY,
                                     ERR_TASKBE_MODIFY_CANNOT_LOCK_ENTRY.get(entryDN));
      }
    }

    try
    {
      // Get the parent for the provided entry DN.  It must be either the
      // scheduled or recurring task parent DN.
      DN parentDN = DirectoryServer.getParentDNInSuffix(entryDN);
      if (parentDN == null)
      {
        LocalizableMessage message = ERR_TASKBE_MODIFY_INVALID_ENTRY.get(entryDN);
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      else if (parentDN.equals(scheduledTaskParentDN))
      {
        // It's a scheduled task.  Make sure that it exists.
        Task t = taskScheduler.getScheduledTask(entryDN);
        if (t == null)
        {
          LocalizableMessage message = ERR_TASKBE_MODIFY_NO_SUCH_TASK.get(entryDN);
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
            LocalizableMessage message = INFO_TASKBE_RUNNING_TASK_CANCELLED.get();
            t.interruptTask(TaskState.STOPPED_BY_ADMINISTRATOR, message);
            return;
          }
          else
          {
            LocalizableMessage message = ERR_TASKBE_MODIFY_RUNNING.get(entryDN);
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
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
              long scheduledStartTime = t.getScheduledStartTime();
              long currentSystemTime = System.currentTimeMillis();
              if (scheduledStartTime < currentSystemTime) {
                scheduledStartTime = currentSystemTime;
              }
              GregorianCalendar calendar = new GregorianCalendar();
              calendar.setTimeInMillis(scheduledStartTime);
              taskScheduler.scheduleNextRecurringTaskIteration(
                      newTask, calendar);
            }
            else if (newTask.getTaskState() ==
              TaskState.STOPPED_BY_ADMINISTRATOR)
            {
              LocalizableMessage message = INFO_TASKBE_RUNNING_TASK_CANCELLED.get();
              t.interruptTask(TaskState.STOPPED_BY_ADMINISTRATOR, message);
            }
            return;
          }
          else
          {
            LocalizableMessage message = ERR_TASKBE_MODIFY_RECURRING.get(entryDN);
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
          }
        }
        else
        {
          LocalizableMessage message = ERR_TASKBE_MODIFY_COMPLETED.get(entryDN);
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
        }
      }
      else if (parentDN.equals(recurringTaskParentDN))
      {
        // We don't currently support altering recurring tasks.
        LocalizableMessage message = ERR_TASKBE_MODIFY_RECURRING.get(entryDN);
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      else
      {
        LocalizableMessage message = ERR_TASKBE_MODIFY_INVALID_ENTRY.get(entryDN);
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }
    finally
    {
      if (entryLock != null)
      {
        entryLock.unlock();
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
    for (Modification m : modifyOperation.getModifications()) {
      if (m.isInternal()) {
        continue;
      }

      if (m.getModificationType() != ModificationType.REPLACE) {
        return false;
      }

      Attribute a = m.getAttribute();
      AttributeType at = a.getAttributeDescription().getAttributeType();
      if (!at.hasName(ATTR_TASK_STATE)) {
        return false;
      }

      Iterator<ByteString> iterator = a.iterator();
      if (!iterator.hasNext()) {
        return false;
      }

      ByteString v = iterator.next();
      if (iterator.hasNext()) {
        return false;
      }

      String valueString = toLowerCase(v.toString());
      if (!valueString.startsWith("cancel")
          && !valueString.startsWith("stop")) {
        return false;
      }
    }

    return true;
  }

  @Override
  public void renameEntry(DN currentDN, Entry entry,
                                   ModifyDNOperation modifyDNOperation)
         throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_MODIFY_DN_NOT_SUPPORTED.get(currentDN, getBackendID()));
  }

  @Override
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
      switch (searchScope.asEnum())
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
        case SUBORDINATES:
          searchScheduledParent = true;
          searchRecurringParent = true;
          searchScheduledTasks  = true;
          searchRecurringTasks  = true;
          break;
      }
    }
    else if (baseDN.equals(scheduledTaskParentDN))
    {
      switch (searchScope.asEnum())
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
        case SUBORDINATES:
          searchScheduledTasks  = true;
          break;
      }
    }
    else if (baseDN.equals(recurringTaskParentDN))
    {
      switch (searchScope.asEnum())
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
        case SUBORDINATES:
          searchRecurringTasks  = true;
          break;
      }
    }
    else
    {
      DN parentDN = DirectoryServer.getParentDNInSuffix(baseDN);
      if (parentDN == null)
      {
        LocalizableMessage message = ERR_TASKBE_SEARCH_INVALID_BASE.get(baseDN);
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
      }
      else if (parentDN.equals(scheduledTaskParentDN))
      {
        DNLock lock = taskScheduler.readLockEntry(baseDN);
        try
        {
          Entry e = taskScheduler.getScheduledTaskEntry(baseDN);
          if (e == null)
          {
            LocalizableMessage message = ERR_TASKBE_SEARCH_NO_SUCH_TASK.get(baseDN);
            throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message,
                                         scheduledTaskParentDN, null);
          }

          if ((searchScope == SearchScope.BASE_OBJECT || searchScope == SearchScope.WHOLE_SUBTREE)
              && searchFilter.matchesEntry(e))
          {
            searchOperation.returnEntry(e, null);
          }

          return;
        }
        finally
        {
          lock.unlock();
        }
      }
      else if (parentDN.equals(recurringTaskParentDN))
      {
        DNLock lock = taskScheduler.readLockEntry(baseDN);
        try
        {
          Entry e = taskScheduler.getRecurringTaskEntry(baseDN);
          if (e == null)
          {
            LocalizableMessage message = ERR_TASKBE_SEARCH_NO_SUCH_RECURRING_TASK.get(baseDN);
            throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message,
                                         recurringTaskParentDN, null);
          }

          if ((searchScope == SearchScope.BASE_OBJECT || searchScope == SearchScope.WHOLE_SUBTREE)
              && searchFilter.matchesEntry(e))
          {
            searchOperation.returnEntry(e, null);
          }

          return;
        }
        finally
        {
          lock.unlock();
        }
      }
      else
      {
        LocalizableMessage message = ERR_TASKBE_SEARCH_INVALID_BASE.get(baseDN);
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
      }
    }

    if (searchRoot)
    {
      Entry e = taskScheduler.getTaskRootEntry();
      if (searchFilter.matchesEntry(e) && !searchOperation.returnEntry(e, null))
      {
        return;
      }
    }

    if (searchScheduledParent)
    {
      Entry e = taskScheduler.getScheduledTaskParentEntry();
      if (searchFilter.matchesEntry(e) && !searchOperation.returnEntry(e, null))
      {
        return;
      }
    }

    if (searchScheduledTasks
        && !taskScheduler.searchScheduledTasks(searchOperation))
    {
      return;
    }

    if (searchRecurringParent)
    {
      Entry e = taskScheduler.getRecurringTaskParentEntry();
      if (searchFilter.matchesEntry(e) && !searchOperation.returnEntry(e, null))
      {
        return;
      }
    }

    if (searchRecurringTasks
        && !taskScheduler.searchRecurringTasks(searchOperation))
    {
      return;
    }
  }

  @Override
  public Set<String> getSupportedControls()
  {
    return Collections.emptySet();
  }

  @Override
  public Set<String> getSupportedFeatures()
  {
    return Collections.emptySet();
  }

  @Override
  public boolean supports(BackendOperation backendOperation)
  {
    switch (backendOperation)
    {
    case LDIF_EXPORT:
    case BACKUP:
    case RESTORE:
      return true;

    default:
      return false;
    }
  }

  @Override
  public void exportLDIF(LDIFExportConfig exportConfig) throws DirectoryException
  {
    File taskFile = getFileForPath(taskBackingFile);

    try (LDIFReader ldifReader = newLDIFReader(taskFile);
        LDIFWriter ldifWriter = newLDIFWriter(exportConfig))
    {
      // Copy record by record.
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
          if (!le.canContinueReading())
          {
            LocalizableMessage message = ERR_TASKS_CANNOT_EXPORT_TO_FILE.get(e);
            throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message, le);
          }
          continue;
        }
        ldifWriter.writeEntry(e);
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }
  }

  private LDIFReader newLDIFReader(File taskFile) throws DirectoryException
  {
    try
    {
      return new LDIFReader(new LDIFImportConfig(taskFile.getPath()));
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_TASKS_CANNOT_EXPORT_TO_FILE.get(e);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message, e);
    }
  }

  private LDIFWriter newLDIFWriter(LDIFExportConfig exportConfig) throws DirectoryException
  {
    try
    {
      return new LDIFWriter(exportConfig);
    }
    catch (Exception e)
    {
      logger.traceException(e);
      LocalizableMessage message = ERR_TASKS_CANNOT_EXPORT_TO_FILE.get(stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message);
    }
  }

  @Override
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig, ServerContext sContext) throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_IMPORT_NOT_SUPPORTED.get(getBackendID()));
  }

  @Override
  public void createBackup(BackupConfig backupConfig) throws DirectoryException
  {
    new BackupManager(getBackendID()).createBackup(this, backupConfig);
  }

  @Override
  public void removeBackup(BackupDirectory backupDirectory, String backupID) throws DirectoryException
  {
    new BackupManager(getBackendID()).removeBackup(backupDirectory, backupID);
  }

  @Override
  public void restoreBackup(RestoreConfig restoreConfig) throws DirectoryException
  {
    new BackupManager(getBackendID()).restoreBackup(this, restoreConfig);
  }

  @Override
  public boolean isConfigurationAcceptable(TaskBackendCfg config,
                                           List<LocalizableMessage> unacceptableReasons,
                                           ServerContext serverContext)
  {
    return isConfigAcceptable(config, unacceptableReasons, null);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(TaskBackendCfg configEntry,
                                            List<LocalizableMessage> unacceptableReasons)
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
                                            List<LocalizableMessage> unacceptableReasons,
                                            String taskBackingFile)
  {
    boolean configIsAcceptable = true;

    try
    {
      String tmpBackingFile = config.getTaskBackingFile();
      if (taskBackingFile == null ||
          !taskBackingFile.equals(tmpBackingFile))
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
      logger.traceException(e);

      unacceptableReasons.add(ERR_TASKBE_ERROR_GETTING_BACKING_FILE.get(
              getExceptionMessage(e)));

      configIsAcceptable = false;
    }

    return configIsAcceptable;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(TaskBackendCfg configEntry)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

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
            ccr.addMessage(ERR_TASKBE_BACKING_FILE_EXISTS.get(tmpBackingFile));
            ccr.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
          }
          else
          {
            File p = f.getParentFile();
            if (p == null)
            {
              ccr.addMessage(ERR_TASKBE_INVALID_BACKING_FILE_PATH.get(tmpBackingFile));
              ccr.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
            }
            else if (! p.exists())
            {
              ccr.addMessage(ERR_TASKBE_BACKING_FILE_MISSING_PARENT.get(p, tmpBackingFile));
              ccr.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
            }
            else if (! p.isDirectory())
            {
              ccr.addMessage(ERR_TASKBE_BACKING_FILE_PARENT_NOT_DIRECTORY.get(p, tmpBackingFile));
              ccr.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
            }
          }
        }
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      ccr.addMessage(ERR_TASKBE_ERROR_GETTING_BACKING_FILE.get(getExceptionMessage(e)));
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
    }

    long tmpRetentionTime = configEntry.getTaskRetentionTime();

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      // Everything looks OK, so apply the changes.
      if (retentionTime != tmpRetentionTime)
      {
        retentionTime = tmpRetentionTime;

        ccr.addMessage(INFO_TASKBE_UPDATED_RETENTION_TIME.get(retentionTime));
      }

      if (! taskBackingFile.equals(tmpBackingFile))
      {
        taskBackingFile = tmpBackingFile;
        taskScheduler.writeState();

        ccr.addMessage(INFO_TASKBE_UPDATED_BACKING_FILE.get(taskBackingFile));
      }
    }

    String tmpNotificationAddress = configEntry.getNotificationSenderAddress();
    if (tmpNotificationAddress == null)
    {
      try
      {
        tmpNotificationAddress = "opendj-task-notification@" +
             InetAddress.getLocalHost().getCanonicalHostName();
      }
      catch (Exception e)
      {
        tmpNotificationAddress = "opendj-task-notification@opendj.org";
      }
    }
    notificationSenderAddress = tmpNotificationAddress;

    currentConfig = configEntry;
    return ccr;
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

  @Override
  public File getDirectory()
  {
    return getFileForPath(taskBackingFile).getParentFile();
  }

  private FileFilter getFilesToBackupFilter()
  {
    return new FileFilter()
    {
      @Override
      public boolean accept(File file)
      {
        return file.getName().equals(getFileForPath(taskBackingFile).getName());
      }
    };
  }

  @Override
  public ListIterator<Path> getFilesToBackup() throws DirectoryException
  {
    return BackupManager.getFiles(getDirectory(), getFilesToBackupFilter(), getBackendID()).listIterator();
  }

  @Override
  public boolean isDirectRestore()
  {
    return true;
  }

  @Override
  public Path beforeRestore() throws DirectoryException
  {
    // save current files
    return BackupManager.saveCurrentFilesToDirectory(this, getBackendID());
  }

  @Override
  public void afterRestore(Path restoreDirectory, Path saveDirectory) throws DirectoryException
  {
    // restore was successful, delete the save directory
    StaticUtils.recursiveDelete(saveDirectory.toFile());
  }
}
