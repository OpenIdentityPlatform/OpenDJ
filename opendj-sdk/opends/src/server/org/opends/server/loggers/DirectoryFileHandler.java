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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.loggers;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.logging.ErrorManager;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.opends.server.api.InvokableComponent;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.InvokableMethod;
import org.opends.server.types.ResultCode;
import org.opends.server.util.TimeThread;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;

/**
 * Simple file logging <tt>Handler</tt>.
 * The <tt>DirectoryFileHandler</tt> can write to a specified file,
 * and can handle rotating the file based on predefined policies.
 */
public class DirectoryFileHandler extends Handler
        implements LoggerAlarmHandler, InvokableComponent
{
  private Writer writer;
  private MeteredStream meter;
  private boolean append;
  private String filename;
  private int bufferSize = 65536;
  private long limit = 0;
  private File file;
  private ArrayList<ActionType> actions;
  private ConfigEntry configEntry;


  /**
   * Initialize a DirectoryFileHandler to write to the given filename.
   *
   * @param configEntry The configuration entry for the associated logger.
   * @param filename  the name of the output file.
   * @param bufferSize the buffer size before flushing data to the file.
   * @exception  IOException if there are IO problems opening the files.
   * @exception  SecurityException  if a security manager exists and if
   *          the caller does not have <tt>LoggingPermission("control")</tt>.
   */
  public DirectoryFileHandler(ConfigEntry configEntry,
                              String filename, int bufferSize)
         throws IOException, SecurityException
  {
    this.configEntry = configEntry;
    this.bufferSize = bufferSize;
    configure();
    this.filename = filename;
    openFile();
  }

  /**
   * Initialize a DirectoryFileHandler to write to the given filename,
   * with optional append.
   *
   * @param configEntry The configuration entry for the associated logger.
   * @param filename  the name of the output file
   * @param append  specifies append mode
   * @param bufferSize the buffer size before flushing data to the file.
   * @exception  IOException if there are IO problems opening the files.
   * @exception  SecurityException  if a security manager exists and if
   *          the caller does not have <tt>LoggingPermission("control")</tt>.
   */
  public DirectoryFileHandler(ConfigEntry configEntry,
                              String filename, boolean append, int bufferSize)
         throws IOException, SecurityException
  {
    this.configEntry = configEntry;
    this.bufferSize = bufferSize;
    configure();
    this.filename = filename;
    this.append = append;
    openFile();
  }


  /**
   * Set the maximum file size limit.
   *
   * @param limit The maximum file size.
   */
  public void setFileSize(long limit)
  {
    this.limit = limit;
  }


  /**
   * Private method to open the set of output files, based on the
   * configured instance variables.
   *
   * @exception IOException        If there was an error while opening the file.
   * @exception SecurityException  If a security manager exists and if
   *                               the caller does not have LoggingPermission.
   */
  private void openFile() throws IOException, SecurityException
  {
    // We register our own ErrorManager during initialization
    // so we can record exceptions.
    InitializationErrorManager em = new InitializationErrorManager();
    setErrorManager(em);

    file = new File(filename);

    // Create the initial log file.
    if (append)
    {
      open(file, true);
    } else
    {
      // FIXME - Should we rotate?
      open(file, false);
    }

    // Did we detect any exceptions during initialization?
    Exception ex = em.lastException;
    if (ex != null)
    {
      if (ex instanceof IOException)
      {
        throw (IOException) ex;
      } else if (ex instanceof SecurityException)
      {
        throw (SecurityException) ex;
      } else
      {
        throw new IOException("Exception: " + ex);
      }
    }

    // Install the normal default ErrorManager.
    setErrorManager(new ErrorManager());
  }


  /**
   * Rotate the current file to the specified new file name.
   * @param newFile The name of the new file to rotate to.
   */
  public void rotate(String newFile)
  {
    close();
    File f1 = file;
    File f2 = new File(newFile);
    if (f1.exists())
    {
      if (f2.exists())
      {
        System.err.println("File:" + f2 + " already exists. Renaming...");
        File f3 = new File(newFile + ".sav");
        f2.renameTo(f3);
      }
      f1.renameTo(f2);
    }
    try
    {
      open(file, false);
    } catch (IOException ix)
    {
      // We don't want to throw an exception here, but we
      // report the exception to any registered ErrorManager.
      reportError(null, ix, ErrorManager.OPEN_FAILURE);
    }
  }

  /**
   * Format and publish a <tt>LogRecord</tt>.
   *
   * @param  record  Description of the log event. A null record is
   *         silently ignored and is not published.
   *
   */
  public void publish(LogRecord record)
  {
    String msg;
    try
    {
      msg = getFormatter().format(record);
    } catch (Exception ex)
    {
      reportError(null, ex, ErrorManager.WRITE_FAILURE);
      return;
    }

    synchronized(this)
    {
      try
      {
        writer.write(msg);
      } catch (Exception ex)
      {
        reportError(null, ex, ErrorManager.WRITE_FAILURE);
        return;
      }

      if(limit > 0 && meter.written >= limit)
      {
        rollover();
      }
    }
  }


  /**
   * Return the number of bytes written to the current file.
   *
   * @return  The number of bytes written to the current file.
   */
  public long getFileSize()
  {
    return meter.written;
  }

  /**
  * This method is called from by the logger thread when a
  * file rotation needs to happen.
  */
  public void rollover()
  {
    String newfilename = filename + "." + getFileExtension();
    rotate(newfilename);

    RotationActionThread rotThread =
      new RotationActionThread(newfilename, actions, configEntry);
    rotThread.start();

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
   * Retrieves the DN of the configuration entry with which this component is
   * associated.
   *
   * @return  The DN of the configuration entry with which this component is
   *          associated.
   */
  public DN getInvokableComponentEntryDN()
  {
    return configEntry.getDN();
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
   * Invokes the specified method with the provided arguments.
   *
   * @param  methodName  The name of the method to invoke.
   * @param  arguments   The set of configuration attributes holding the
   *                     arguments to use for the method.
   *
   * @return  The return value for the method, or <CODE>null</CODE> if it did
   *          not return a value.
   *
   * @throws  DirectoryException  If there was no such method, or if an error
   *                              occurred while attempting to invoke it.
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

    rollover();

    return null;
  }


  /**
   * Close the current output stream.
   *
   */
  public void close()
  {
    flushAndClose();
  }


  /**
   * Return the extension for the target filename for the rotated file.
   *
   * @return  The extension for the target filename for the rotated file.
   */
  private String getFileExtension()
  {
    return TimeThread.getUTCTime();
  }


  /**
   * Open the file and set the appropriate output stream.
   *
   * @param  fname   The path and name of the file to be written.
   * @param  append  Indicates whether to append to the existing file or to
   *                 overwrite it.
   *
   * @throws  IOException  If a problem occurs while opening the file.
   */
  private void open(File fname, boolean append) throws IOException
  {
    long len = 0;
    if (append)
    {
      len = fname.length();
    }
    FileOutputStream fout = new FileOutputStream(fname, append);
    BufferedOutputStream bout = null;
    if(bufferSize <= 0)
    {
      bout = new BufferedOutputStream(fout);
    } else
    {
      bout = new BufferedOutputStream(fout, bufferSize);
    }
    meter = new MeteredStream(bout, len);
    // flushAndClose();
    writer = new BufferedWriter(new OutputStreamWriter(meter));
  }


  /**
   * Private method to configure a DirectoryFileHandler
   * with default values.
   */
  private void configure()
  {
    setLevel(Level.ALL);
    this.append = true;
  }


  /**
   * Flush any buffered messages and close the output stream.
   */
  private void flushAndClose()
  {
    if (writer != null)
    {
      try
      {
        writer.flush();
        writer.close();
      } catch (Exception ex) {
        // We don't want to throw an exception here, but we
        // report the exception to any registered ErrorManager.
        reportError(null, ex, ErrorManager.CLOSE_FAILURE);
      }
      writer = null;
    }

  }

  /**
   * Flush any buffered messages.
   */
  public void flush()
  {
    if (writer != null)
    {
      try
      {
        writer.flush();
      } catch (Exception ex) {
        // We don't want to throw an exception here, but we
        // report the exception to any registered ErrorManager.
        reportError(null, ex, ErrorManager.FLUSH_FAILURE);
      }
    }
  }


}

