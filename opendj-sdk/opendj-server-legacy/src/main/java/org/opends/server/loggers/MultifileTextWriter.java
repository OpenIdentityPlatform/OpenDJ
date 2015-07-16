/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.loggers;


import static org.opends.messages.LoggerMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.SizeLimitLogRotationPolicyCfg;
import org.opends.server.api.DirectoryThread;
import org.opends.server.api.ServerShutdownListener;
import org.opends.server.core.DirectoryServer;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.FilePermission;
import org.opends.server.util.TimeThread;

/**
 * A MultiFileTextWriter is a specialized TextWriter which supports publishing
 * log records to a set of files. MultiFileWriters write to one file in the
 * set at a time, switching files as is dictated by a specified rotation
 * and retention policies.
 *
 * When a switch is required, the writer closes the current file and opens a
 * new one named in accordance with a specified FileNamingPolicy.
 */
class MultifileTextWriter
    implements ServerShutdownListener, TextWriter, RotatableLogFile,
    ConfigurationChangeListener<SizeLimitLogRotationPolicyCfg>
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final String UTF8_ENCODING= "UTF-8";

  private CopyOnWriteArrayList<RotationPolicy> rotationPolicies = new CopyOnWriteArrayList<>();
  private CopyOnWriteArrayList<RetentionPolicy> retentionPolicies = new CopyOnWriteArrayList<>();

  private FileNamingPolicy namingPolicy;
  private FilePermission filePermissions;
  private LogPublisherErrorHandler errorHandler;
  /** TODO: Implement actions. */
  private ArrayList<ActionType> actions;

  private String name;
  private String encoding;
  private int bufferSize;
  private boolean autoFlush;
  private boolean append;
  private long interval;
  private boolean stopRequested;
  private long sizeLimit;

  private Thread rotaterThread;

  private Calendar lastRotationTime = TimeThread.getCalendar();
  private Calendar lastCleanTime = TimeThread.getCalendar();
  private long lastCleanCount;
  private long totalFilesRotated;
  private long totalFilesCleaned;

  /** The underlying output stream. */
  private MeteredStream outputStream;
  /** The underlying buffered writer using the output stream. */
  private BufferedWriter writer;

  /**
   * Creates a new instance of MultiFileTextWriter with the supplied policies.
   *
   * @param name the name of the log rotation thread.
   * @param interval the interval to check whether the logs need to be rotated.
   * @param namingPolicy the file naming policy to use to name rotated log.
   *                      files.
   * @param filePermissions the file permissions to set on the log files.
   * @param errorHandler the log publisher error handler to notify when
   *                     an error occurs.
   * @param encoding the encoding to use to write the log files.
   * @param autoFlush whether to flush the writer on every println.
   * @param append whether to append to an existing log file.
   * @param bufferSize the bufferSize to use for the writer.
   * @throws IOException if an error occurs while creating the log file.
   * @throws DirectoryException if an error occurs while preping the new log
   *                            file.
   */
  public MultifileTextWriter(String name, long interval,
                             FileNamingPolicy namingPolicy,
                             FilePermission filePermissions,
                             LogPublisherErrorHandler errorHandler,
                             String encoding,
                             boolean autoFlush,
                             boolean append,
                             int bufferSize)
      throws IOException, DirectoryException
  {
    File file = namingPolicy.getInitialName();
    constructWriter(file, filePermissions, encoding, append,
                    bufferSize);

    this.name = name;
    this.interval = interval;
    this.namingPolicy = namingPolicy;
    this.filePermissions = filePermissions;
    this.errorHandler = errorHandler;

    this.encoding = UTF8_ENCODING;
    this.autoFlush = autoFlush;
    this.append = append;
    this.bufferSize = bufferSize;

    this.stopRequested = false;

    rotaterThread = new RotaterThread(this);
    rotaterThread.start();

    DirectoryServer.registerShutdownListener(this);
  }

  /**
   * Construct a PrintWriter for a file.
   * @param file - the file to open for writing
   * @param filePermissions - the file permissions to set on the file.
   * @param encoding - the encoding to use when writing log records.
   * @param append - indicates whether the file should be appended to or
   * truncated.
   * @param bufferSize - the buffer size to use for the writer.
   * @throws IOException if the PrintWriter could not be constructed
   * or if the file already exists and it was indicated this should be
   * an error.
   * @throws DirectoryException if there was a problem setting permissions on
   * the file.
   */
  private void constructWriter(File file, FilePermission filePermissions,
                               String encoding, boolean append,
                               int bufferSize)
      throws IOException, DirectoryException
  {
    // Create new file if it doesn't exist
    if(!file.exists())
    {
      file.createNewFile();
    }

    FileOutputStream stream = new FileOutputStream(file, append);
    outputStream = new MeteredStream(stream, file.length());

    OutputStreamWriter osw = new OutputStreamWriter(outputStream, encoding);
    if(bufferSize <= 0)
    {
      writer = new BufferedWriter(osw);
    }
    else
    {
      writer = new BufferedWriter(osw, bufferSize);
    }


    // Try to apply file permissions.
    try
    {
      if(!FilePermission.setPermissions(file, filePermissions))
      {
        logger.warn(WARN_LOGGER_UNABLE_SET_PERMISSIONS, filePermissions, file);
      }
    }
    catch(Exception e)
    {
      // Log an warning that the permissions were not set.
      logger.warn(WARN_LOGGER_SET_PERMISSION_FAILED, file, stackTraceToSingleLineString(e));
    }
  }


  /**
   * Add a rotation policy to enforce on the files written by this writer.
   *
   * @param policy The rotation policy to add.
   */
  public void addRotationPolicy(RotationPolicy policy)
  {
    this.rotationPolicies.add(policy);

    if(policy instanceof SizeBasedRotationPolicy)
    {
      SizeBasedRotationPolicy sizePolicy = (SizeBasedRotationPolicy) policy;
      if(sizeLimit == 0 ||
          sizeLimit > sizePolicy.currentConfig.getFileSizeLimit())
      {
        sizeLimit = sizePolicy.currentConfig.getFileSizeLimit();
      }
      // Add this as a change listener so we can update the size limit.
      sizePolicy.currentConfig.addSizeLimitChangeListener(this);
    }
  }

  /**
   * Add a retention policy to enforce on the files written by this writer.
   *
   * @param policy The retention policy to add.
   */
  public void addRetentionPolicy(RetentionPolicy policy)
  {
    this.retentionPolicies.add(policy);
  }

  /**
   * Removes all the rotation policies currently enforced by this writer.
   */
  public void removeAllRotationPolicies()
  {
    for(RotationPolicy policy : rotationPolicies)
    {
      if(policy instanceof SizeBasedRotationPolicy)
      {
        sizeLimit = 0;

        // Remove this as a change listener.
        SizeBasedRotationPolicy sizePolicy = (SizeBasedRotationPolicy) policy;
        sizePolicy.currentConfig.removeSizeLimitChangeListener(this);
      }
    }

    this.rotationPolicies.clear();
  }

  /**
   * Removes all retention policies being enforced by this writer.
   */
  public void removeAllRetentionPolicies()
  {
    this.retentionPolicies.clear();
  }

  /**
   * Set the auto flush setting for this writer.
   *
   * @param autoFlush If the writer should flush the buffer after every line.
   */
  public void setAutoFlush(boolean autoFlush)
  {
    this.autoFlush = autoFlush;
  }

  /**
   * Set the append setting for this writter.
   *
   * @param append If the writer should append to an existing file.
   */
  public void setAppend(boolean append)
  {
    this.append = append;
  }

  /**
   * Set the buffer size for this writter.
   *
   * @param bufferSize The size of the underlying output stream buffer.
   */
  public void setBufferSize(int bufferSize)
  {
    this.bufferSize = bufferSize;
  }

  /**
   * Set the file permission to set for newly created log files.
   *
   * @param filePermissions The file permission to set for new log files.
   */
  public void setFilePermissions(FilePermission filePermissions)
  {
    this.filePermissions = filePermissions;
  }

  /**
   * Retrieves the current naming policy used to generate log file names.
   *
   * @return The current naming policy in use.
   */
  public FileNamingPolicy getNamingPolicy()
  {
    return namingPolicy;
  }

  /**
   * Set the naming policy to use when generating new log files.
   *
   * @param namingPolicy the naming policy to use to name log files.
   */
  public void setNamingPolicy(FileNamingPolicy namingPolicy)
  {
    this.namingPolicy = namingPolicy;
  }

  /**
   * Set the interval in which the rotator thread checks to see if the log
   * file should be rotated.
   *
   * @param interval The interval to check if the log file needs to be rotated.
   */
  public void setInterval(long interval)
  {
    this.interval = interval;

    // Wake up the thread if its sleeping on the old interval
    if(rotaterThread.getState() == Thread.State.TIMED_WAITING)
    {
      rotaterThread.interrupt();
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
      SizeLimitLogRotationPolicyCfg config, List<LocalizableMessage> unacceptableReasons)
  {
    // This should always be ok
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(
      SizeLimitLogRotationPolicyCfg config)
  {
    long newSizeLimit = Integer.MAX_VALUE;

    // Go through all current size rotation policies and get the lowest size setting.
    for(RotationPolicy policy : rotationPolicies)
    {
      if(policy instanceof SizeBasedRotationPolicy)
      {
        SizeBasedRotationPolicy sizePolicy = (SizeBasedRotationPolicy) policy;
        SizeLimitLogRotationPolicyCfg cfg =
            sizePolicy.currentConfig.dn().equals(config.dn()) ? config : sizePolicy.currentConfig;
        if(newSizeLimit > cfg.getFileSizeLimit())
        {
          newSizeLimit = cfg.getFileSizeLimit();
        }
      }
    }

    sizeLimit = newSizeLimit;

    return new ConfigChangeResult();
  }

  /**
   * A rotater thread is responsible for checking if the log files need to be
   * rotated based on the policies. It will do so if necessary.
   */
  private class RotaterThread extends DirectoryThread
  {
    MultifileTextWriter writer;
    /**
     * Create a new rotater thread.
     */
    public RotaterThread(MultifileTextWriter writer)
    {
      super(name);
      this.writer = writer;
    }

    /**
     * The run method of the rotaterThread. It wakes up periodically and checks
     * whether the file needs to be rotated based on the rotation policy.
     */
    @Override
    public void run()
    {
      while(!isShuttingDown())
      {
        try
        {
          sleep(interval);
        }
        catch(InterruptedException e)
        {
          // We expect this to happen.
        }
        catch(Exception e)
        {
          logger.traceException(e);
        }

        for(RotationPolicy rotationPolicy : rotationPolicies)
        {
          if(rotationPolicy.rotateFile(writer))
          {
            rotate();
          }
        }

        for(RetentionPolicy retentionPolicy : retentionPolicies)
        {
          try
          {
            File[] files =
                retentionPolicy.deleteFiles(writer.getNamingPolicy());

            for(File file : files)
            {
              file.delete();
              totalFilesCleaned++;
              logger.trace("%s cleaned up log file %s", retentionPolicy, file);
            }

            if(files.length > 0)
            {
              lastCleanTime = TimeThread.getCalendar();
              lastCleanCount = files.length;
            }
          }
          catch(DirectoryException de)
          {
            logger.traceException(de);
            errorHandler.handleDeleteError(retentionPolicy, de);
          }
        }
      }
    }
  }

  /**
   * Retrieves the human-readable name for this shutdown listener.
   *
   * @return  The human-readable name for this shutdown listener.
   */
  @Override
  public String getShutdownListenerName()
  {
    return "MultifileTextWriter Thread " + name;
  }

  /**
   * Indicates that the Directory Server has received a request to stop running
   * and that this shutdown listener should take any action necessary to prepare
   * for it.
   *
   * @param  reason  The human-readable reason for the shutdown.
   */
  @Override
  public void processServerShutdown(LocalizableMessage reason)
  {
    stopRequested = true;

    // Wait for rotater to terminate
    while (rotaterThread != null && rotaterThread.isAlive()) {
      try {
        // Interrupt if its sleeping
        rotaterThread.interrupt();
        rotaterThread.join();
      }
      catch (InterruptedException ex) {
        // Ignore; we gotta wait..
      }
    }

    DirectoryServer.deregisterShutdownListener(this);

    removeAllRotationPolicies();
    removeAllRetentionPolicies();

    // Don't close the writer as there might still be message to be
    // written. manually shutdown just before the server process
    // exists.
  }

  /**
   * Queries whether the publisher is in shutdown mode.
   *
   * @return if the publish is in shutdown mode.
   */
  private boolean isShuttingDown()
  {
    return stopRequested;
  }

  /**
   * Shutdown the text writer.
   */
  @Override
  public void shutdown()
  {
    processServerShutdown(null);

    try
    {
      writer.flush();
      writer.close();
    }
    catch(Exception e)
    {
      errorHandler.handleCloseError(e);
    }
  }


  /**
   * Write a log record string to the file.
   *
   * @param record the log record to write.
   */
  @Override
  public void writeRecord(String record)
  {
    // Assume each character is 1 byte ASCII
    int length = record.length();
    int size = length;
    char c;
    for (int i=0; i < length; i++)
    {
      c = record.charAt(i);
      if (c != (byte) (c & 0x0000007F))
      {
        try
        {
          // String contains a non ASCII character. Fall back to getBytes.
          size = record.getBytes("UTF-8").length;
        }
        catch(Exception e)
        {
          size = length * 2;
        }
        break;
      }
    }

    synchronized(this)
    {
      if(sizeLimit > 0 && outputStream.written + size + 1 >= sizeLimit)
      {
        rotate();
      }

      try
      {
        writer.write(record);
        writer.newLine();
      }
      catch(Exception e)
      {
        errorHandler.handleWriteError(record, e);
      }

      if(autoFlush)
      {
        flush();
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void flush()
  {
    try
    {
      writer.flush();
    }
    catch(Exception e)
    {
      errorHandler.handleFlushError(e);
    }
  }

  /**
   * Tries to rotate the log files. If the new log file already exists, it
   * tries to rename the file. On failure, all subsequent log write requests
   * will throw exceptions.
   */
  private synchronized void rotate()
  {
    try
    {
      writer.flush();
      writer.close();
    }
    catch(Exception e)
    {
      logger.traceException(e);
      errorHandler.handleCloseError(e);
    }

    File currentFile = namingPolicy.getInitialName();
    File newFile = namingPolicy.getNextName();
    currentFile.renameTo(newFile);

    try
    {
      constructWriter(currentFile, filePermissions, encoding, append,
                      bufferSize);
    }
    catch (Exception e)
    {
      logger.traceException(e);
      errorHandler.handleOpenError(currentFile, e);
    }

    //RotationActionThread rotThread =
    //  new RotationActionThread(newFile, actions, configEntry);
    //rotThread.start();

    logger.trace("Log file %s rotated and renamed to %s",
                       currentFile, newFile);
    totalFilesRotated++;
    lastRotationTime = TimeThread.getCalendar();
  }

  /**
   * This method sets the actions that need to be executed after rotation.
   *
   * @param actions An array of actions that need to be executed on rotation.
   */
  public void setPostRotationActions(ArrayList<ActionType> actions)
  {
    this.actions = actions;
  }

  /** {@inheritDoc} */
  @Override
  public long getBytesWritten()
  {
    return outputStream.written;
  }

  /**
   * Retrieves the last time one or more log files are cleaned in this instance
   * of the Directory Server. If log files have never been cleaned, this value
   * will be the time the server started.
   *
   * @return The last time log files are cleaned.
   */
  public Calendar getLastCleanTime()
  {
    return lastCleanTime;
  }

  /**
   * Retrieves the number of files cleaned in the last cleanup run.
   *
   * @return The number of files cleaned int he last cleanup run.
   */
  public long getLastCleanCount()
  {
    return lastCleanCount;
  }

  /** {@inheritDoc} */
  @Override
  public Calendar getLastRotationTime()
  {
    return lastRotationTime;
  }

  /**
   * Retrieves the total number file rotations occurred in this instance of the
   * Directory Server.
   *
   * @return The total number of file rotations.
   */
  public long getTotalFilesRotated()
  {
    return totalFilesRotated;
  }

  /**
   * Retrieves the total number of files cleaned in this instance of the
   * Directory Server.
   *
   * @return The total number of files cleaned.
   */
  public long getTotalFilesCleaned()
  {
    return totalFilesCleaned;
  }
}
