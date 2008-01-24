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
 *      Portions Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.loggers;


import org.opends.server.api.DirectoryThread;
import org.opends.server.api.ServerShutdownListener;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.*;
import org.opends.server.types.FilePermission;
import org.opends.server.admin.std.server.SizeLimitLogRotationPolicyCfg;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.util.TimeThread;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.LoggerMessages.*;
import org.opends.messages.Message;

import java.io.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.Calendar;

/**
 * A MultiFileTextWriter is a specialized TextWriter which supports publishing
 * log records to a set of files. MultiFileWriters write to one file in the
 * set at a time, switching files as is dictated by a specified rotation
 * and retention policies.
 *
 * When a switch is required, the writer closes the current file and opens a
 * new one named in accordance with a specified FileNamingPolicy.
 */
public class MultifileTextWriter
    implements ServerShutdownListener, TextWriter,
    ConfigurationChangeListener<SizeLimitLogRotationPolicyCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  private static final String UTF8_ENCODING= "UTF-8";

  private CopyOnWriteArrayList<RotationPolicy> rotationPolicies =
      new CopyOnWriteArrayList<RotationPolicy>();
  private CopyOnWriteArrayList<RetentionPolicy> retentionPolicies =
      new CopyOnWriteArrayList<RetentionPolicy>();

  private FileNamingPolicy namingPolicy;
  private FilePermission filePermissions;
  private LogPublisherErrorHandler errorHandler;
  //TODO: Implement actions.
  private ArrayList<ActionType> actions;

  private String name;
  private String encoding;
  private int bufferSize;
  private boolean autoFlush;
  private boolean append;
  private long interval;
  private boolean stopRequested;
  private long sizeLimit = 0;

  private Thread rotaterThread;

  private Calendar lastRotationTime = TimeThread.getCalendar();
  private Calendar lastCleanTime = TimeThread.getCalendar();
  private long lastCleanCount = 0;
  private long totalFilesRotated = 0;
  private long totalFilesCleaned = 0;

  /** The underlying output stream. */
  private MeteredStream outputStream;
  /** The underlaying buffered writer using the output steram. */
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
    BufferedWriter bw = null;
    if(bufferSize <= 0)
    {
      writer = new BufferedWriter(osw);
    }
    else
    {
      writer = new BufferedWriter(osw, bufferSize);
    }


    // Try to apply file permissions.
    if(FilePermission.canSetPermissions())
    {
      try
      {
        if(!FilePermission.setPermissions(file, filePermissions))
        {
          Message message = WARN_LOGGER_UNABLE_SET_PERMISSIONS.get(
              filePermissions.toString(), file.toString());
          ErrorLogger.logError(message);
        }
      }
      catch(Exception e)
      {
        // Log an warning that the permissions were not set.
        Message message = WARN_LOGGER_SET_PERMISSION_FAILED.get(
            file.toString(), stackTraceToSingleLineString(e));
        ErrorLogger.logError(message);
      }
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
      SizeBasedRotationPolicy sizePolicy = ((SizeBasedRotationPolicy)policy);
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
        SizeBasedRotationPolicy sizePolicy = ((SizeBasedRotationPolicy)policy);
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
   * Set the internval in which the rotator thread checks to see if the log
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

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      SizeLimitLogRotationPolicyCfg config, List<Message> unacceptableReasons)
  {
    // This should always be ok
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      SizeLimitLogRotationPolicyCfg config)
  {
    if(sizeLimit == 0 || sizeLimit > config.getFileSizeLimit())
    {
      sizeLimit = config.getFileSizeLimit();
    }

    return new ConfigChangeResult(ResultCode.SUCCESS, false,
                                  new ArrayList<Message>());
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
     * the run method of the rotaterThread. It wakes up periodically and checks
     * whether the file needs to be rotated based on the rotation policy.
     */
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
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
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
              if(debugEnabled())
              {
                TRACER.debugInfo(retentionPolicy.toString() +
                    " cleaned up log file %s", file.toString());
              }
            }

            if(files.length > 0)
            {
              lastCleanTime = TimeThread.getCalendar();
              lastCleanCount = files.length;
            }
          }
          catch(DirectoryException de)
          {
            if(debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }
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
  public void processServerShutdown(Message reason)
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

  /**
   * {@inheritDoc}
   */
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
  public synchronized void rotate()
  {
    try
    {
      writer.flush();
      writer.close();
    }
    catch(Exception e)
    {
      if(debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
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
      if(debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      errorHandler.handleOpenError(currentFile, e);
    }

    //RotationActionThread rotThread =
    //  new RotationActionThread(newFile, actions, configEntry);
    //rotThread.start();

    if(debugEnabled())
    {
      TRACER.debugInfo("Log file %s rotated and renamed to %s",
                       currentFile, newFile);
    }

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

  /**
   * Retrieves the number of bytes written to the current log file.
   *
   * @return The number of bytes written to the current log file.
   */
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

  /**
   * Retrieves the last time a log file was rotated in this instance of
   * Directory Server. If a log rotation never
   * occurred, this value will be the time the server started.
   *
   * @return The last time log rotation occurred.
   */
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
