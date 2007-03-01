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
package org.opends.server.loggers;

import org.opends.server.api.DirectoryThread;
import org.opends.server.api.ServerShutdownListener;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InvokableMethod;
import org.opends.server.types.ResultCode;

import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.debugVerbose;
import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;

import java.io.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.ArrayList;

/**
 * A MultiFileTextWriter is a specialized TextWriter which supports publishing
 * log records to a set of files. MultiFileWriters write to one file in the
 * set at a time, switching files as is dictated by a specified rotation
 * and retention policies.
 *
 * When a switch is required, the writer closes the current file and opens a
 * new one named in accordance with a specified FileNamingPolicy.
 */
public class MultifileTextWriter extends TextWriter
    implements ServerShutdownListener
{
  private static final String UTF8_ENCODING= "UTF-8";
  private static final int BUFFER_SIZE= 65536;

  private CopyOnWriteArrayList<RotationPolicy> rotationPolicies;
  private CopyOnWriteArrayList<RetentionPolicy> retentionPolicies;
  private FileNamingPolicy namingPolicy;
  //TODO: Implement actions.
  private ArrayList<ActionType> actions;

  private String name;
  private String encoding;
  private int bufferSize;
  private boolean autoFlush;
  private boolean append;
  private int interval;
  private boolean stopRequested;

  private Thread rotaterThread;

  /**
   * Get the writer for the initial log file and initialize the
   * rotation policy.
   * @param naming - the file naming policy in use
   * @param encoding - the encoding to use when writing log records.
   * @param autoFlush - indicates whether the file should be flushed
   * after every record written.
   * @param append - indicates whether to append to the existing file or to
   *                 overwrite it.
   * @param bufferSize - the buffer size to use for the writer.
   * @return a PrintWriter for the initial log file
   * @throws IOException if the initial log file could not be opened
   */
  private static PrintWriter getInitialWriter(FileNamingPolicy naming,
                                              String encoding,
                                              boolean autoFlush,
                                              boolean append,
                                              int bufferSize)
      throws IOException
  {
    File file = naming.getInitialName();
    return constructWriter(file, encoding, autoFlush, append, bufferSize);
  }

  /**
   * Construct a PrintWriter for a file.
   * @param file - the file to open for writing
   * @param encoding - the encoding to use when writing log records.
   * @param autoFlush - indicates whether the file should be flushed
   * after every record written.
   * @param append - indicates whether the file should be appended to or
   * truncated.
   * @param bufferSize - the buffer size to use for the writer.
   * @return a PrintWriter for the specified file.
   * @throws IOException if the PrintWriter could not be constructed
   * or if the file already exists and it was indicated this should be
   * an error.
   */
  private static PrintWriter constructWriter(File file, String encoding,
                                             boolean autoFlush, boolean append,
                                             int bufferSize)
      throws IOException
  {
    FileOutputStream fos= new FileOutputStream(file, append);
    OutputStreamWriter osw= new OutputStreamWriter(fos, encoding);
    BufferedWriter bw = null;
    if(bufferSize <= 0)
    {
      bw= new BufferedWriter(osw);
    }
    else
    {
      bw= new BufferedWriter(osw, bufferSize);
    }
    return new PrintWriter(bw, autoFlush);
  }

  /**
   * Creates a new instance of MultiFileTextWriter with the supplied policies.
   *
   * @param name the name of the log rotation thread.
   * @param namingPolicy the file naming policy to use to name rotated log
   *                      files.
   * @throws IOException if an error occurs while creating the log file.
   */
  public MultifileTextWriter(String name, FileNamingPolicy namingPolicy)
      throws IOException
  {
    this(name, 5000, namingPolicy, UTF8_ENCODING,
         true, true, BUFFER_SIZE, null, null);
  }

  /**
   * Creates a new instance of MultiFileTextWriter with the supplied policies.
   *
   * @param name the name of the log rotation thread.
   * @param interval the interval to check whether the logs need to be rotated.
   * @param namingPolicy the file naming policy to use to name rotated log.
   *                      files.
   * @param encoding the encoding to use to write the log files.
   * @param autoFlush whether to flush the writer on every println.
   * @param append whether to append to an existing log file.
   * @param bufferSize the bufferSize to use for the writer.
   * @param rotationPolicies the rotation policy to use for log rotation.
   * @param retentionPolicies the retention policy to use for log rotation.
   * @throws IOException if an error occurs while creating the log file.
   */
  public MultifileTextWriter(String name, int interval,
                             FileNamingPolicy namingPolicy, String encoding,
                             boolean autoFlush, boolean append, int bufferSize,
                        CopyOnWriteArrayList<RotationPolicy> rotationPolicies,
                        CopyOnWriteArrayList<RetentionPolicy> retentionPolicies)
      throws IOException
  {
    super(getInitialWriter(namingPolicy, encoding,
                           autoFlush, append, bufferSize), true);
    this.name = name;
    this.interval = interval;
    this.namingPolicy = namingPolicy;
    this.rotationPolicies = rotationPolicies;
    this.retentionPolicies = retentionPolicies;

    this.encoding = encoding;
    this.autoFlush = autoFlush;
    this.append = append;
    this.bufferSize = bufferSize;

    this.stopRequested = false;

    // We will lazily launch the rotaterThread
    // to ensure initialization safety.

    DirectoryServer.registerShutdownListener(this);
  }

  /**
   * A rotater thread is responsible for checking if the log files need to be
   * rotated based on the policies. It will do so if necessary.
   */
  private class RotaterThread extends DirectoryThread
  {
    /**
     * Create a new rotater thread.
     */
    public RotaterThread()
    {
      super(name);
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
            debugCaught(DebugLogLevel.ERROR, e);
          }
        }

        if(rotationPolicies != null)
        {
          for(RotationPolicy rotationPolicy : rotationPolicies)
          {
            if(rotationPolicy.rotateFile())
            {
              try
              {
                rotate();
              }
              catch (IOException ioe)
              {
                //TODO: Comment this after AOP logging is complete.
                //int msgID = MSGID_CONFIG_LOGGER_ROTATE_FAILED;
                //Error.logError(ErrorLogCategory.CORE_SERVER,
                //               ErrorLogSeverity.SEVERE_ERROR, msgID, ioe);
              }
            }
          }
        }

        if(retentionPolicies != null)
        {
          for(RetentionPolicy retentionPolicy : retentionPolicies)
          {
            int numFilesDeleted = retentionPolicy.deleteFiles();
            if (debugEnabled())
            {
              debugVerbose("%d files deleted by rentention policy",
                           numFilesDeleted);
            }
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
  public void processServerShutdown(String reason)
  {
    startShutDown();

    // Wait for rotater to terminate
    while (rotaterThread != null && rotaterThread.isAlive()) {
      try {
        rotaterThread.join();
      }
      catch (InterruptedException ex) {
        // Ignore; we gotta wait..
      }
    }

    writer.flush();
    writer.close();
    writer = null;
  }

  /**
   * Queries whether the publisher is in shutdown mode.
   *
   * @return if the publish is in shutdown mode.
   */
  private synchronized boolean isShuttingDown()
  {
    return stopRequested;
  }

  /**
   * Tell the writer to start shutting down.
   */
  private synchronized void startShutDown()
  {
    stopRequested = true;
  }

  /**
   * Shutdown the text writer.
   */
  public void shutdown()
  {
    processServerShutdown(null);

    DirectoryServer.deregisterShutdownListener(this);
  }


  /**
   * Write a log record string to the file.
   *
   * @param record the log record to write.
   */
  public synchronized void writeRecord(String record)
  {
    // Launch writer rotaterThread if not running
    if (rotaterThread == null) {
      rotaterThread = new RotaterThread();
      rotaterThread.start();
    }

    writer.println(record);
  }

  /**
   * Tries to rotate the log files. If the new log file alreadly exists, it
   * tries to rename the file. On failure, all subsequent log write requests
   * will throw exceptions.
   *
   * @throws IOException if an error occurs while rotation the log files.
   */
  public void rotate() throws IOException
  {
    writer.flush();
    writer.close();
    writer = null;

    File currentFile = namingPolicy.getInitialName();
    File newFile = namingPolicy.getNextName();
    currentFile.renameTo(newFile);

    writer = constructWriter(currentFile, encoding,
                             autoFlush, append, bufferSize);

    //RotationActionThread rotThread =
    //  new RotationActionThread(newFile, actions, configEntry);
    //rotThread.start();
  }

  /**
   * Invokes the specified method with the provided arguments.
   *
   * @param  methodName  The name of the method to invoke.
   * @param  arguments   The set of configuration attributes holding the
   *                     arguments to use for the method.
   *
   * @return  The return value for the method, or <CODE>null</CODE> if it did
   *          not return a value.
   *
   * @throws org.opends.server.types.DirectoryException
   *   If there was no such method, or if an error occurred while attempting
   *   to invoke it.
   */
  public Object invokeMethod(String methodName, ConfigAttribute[] arguments)
      throws DirectoryException
  {
    if(!methodName.equals("rotateNow"))
    {
      int msgID = MSGID_CONFIG_JMX_NO_METHOD;
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                   getMessage(msgID), msgID);
    }

    try
    {
      rotate();
    }
    catch(Exception e)
    {
      //TODO: Comment when AOP logging framework is complete.
      //int msgID = MSGID_CONFIG_LOGGER_ROTATE_FAILED;
      //throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
      //                            getMessage(msgID, e), msgID);
    }

    return null;
  }

  /**
   * Retrieves a list of the methods that may be invoked for this component.
   *
   * @return  A list of the methods that may be invoked for this component.
   */
  public InvokableMethod[] getOperationSignatures()
  {
    InvokableMethod[] methods = new InvokableMethod[1];
    methods[0] = new InvokableMethod("rotateNow",
                                     "Rotate the log file immediately",
                                     null, "void", true, true);
    return methods;
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
}
