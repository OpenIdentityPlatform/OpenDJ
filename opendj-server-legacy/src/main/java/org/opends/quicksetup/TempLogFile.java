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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.quicksetup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Date;
import java.text.DateFormat;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.loggers.DebugLogPublisher;
import org.opends.server.loggers.DebugLogger;
import org.opends.server.loggers.ErrorLogPublisher;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.loggers.TextErrorLogPublisher;
import org.opends.server.loggers.TextWriter;

/** This class represents a temporary log file which should be usually deleted if linked operation succeeded. */
public class TempLogFile
{
  private static final LocalizedLogger localizedLogger = LocalizedLogger.getLoggerForThisClass();


  /**
   * Creates a new temporary log file.
   * <p>
   * Log file will be generated in the OS temporary directory and its name will have
   * the following pattern: prefix-[RANDOM_NUMBER_STRING].log
   *
   * @param prefix
   *          log file prefix to which log messages will be written.
   * @return a new temporary log file.
   */
  public static TempLogFile newTempLogFile(final String prefix)
  {
    return newTempLogFile(prefix, null);
  }

  /**
   * Creates a new temporary log file in the given directory.
   * <p>
   * The directory is created if it does not exist yet. When it is {@code null} or cannot be
   * used, the log file goes to the OS temporary directory instead, as with
   * {@link #newTempLogFile(String)}. The name of the file follows the pattern
   * prefix-[RANDOM_NUMBER_STRING].log either way.
   *
   * @param prefix
   *          log file prefix to which log messages will be written.
   * @param directory
   *          the directory to create the log file in, or {@code null} for the OS temporary
   *          directory.
   * @return a new temporary log file.
   */
  public static TempLogFile newTempLogFile(final String prefix, final File directory)
  {
    if (directory != null)
    {
      try
      {
        Files.createDirectories(directory.toPath());
        return new TempLogFile(Files.createTempFile(directory.toPath(), prefix, ".log").toFile());
      }
      catch (final IOException e)
      {
        localizedLogger.warn(LocalizableMessage.raw("Unable to create temp log file in " + directory
            + " because: " + e.getMessage() + ", falling back to the temporary directory"), e);
      }
    }
    try
    {
      return new TempLogFile(Files.createTempFile(prefix, ".log").toFile());
    }
    catch (final IOException e)
    {
      localizedLogger.error(LocalizableMessage.raw("Unable to create temp log file because: " + e.getMessage()), e);
      return new TempLogFile();
    }
  }

  private final File logFile;

  private TempLogFile()
  {
    this.logFile = null;
    this.writer=null;
  }

  final TextWriter writer;
  
  private TempLogFile(final File file) throws IOException
  {
    logFile = file;
    // Install the default loggers so the startup messages
    // will be printed.
     
    if ("true".equalsIgnoreCase(System.getenv("OPENDJ_LOG_TO_STDOUT"))) {
    	writer=new TextWriter.STDOUT(); 
    }else {
    	writer=new TextWriter.STREAM(new FileOutputStream(file));
    }
    ErrorLogPublisher startupErrorLogPublisher = TextErrorLogPublisher.getServerStartupTextErrorPublisher(writer);
    ErrorLogger.getInstance().addLogPublisher(startupErrorLogPublisher);
    DebugLogger.getInstance().addPublisherIfRequired(writer);

    localizedLogger.info(LocalizableMessage.raw("QuickSetup application launched " + DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG).format(new Date()), null));
  }

  /**
   * Gets the name of the log file.
   *
   * @return File representing the log file
   */
  public File getLogFile()
  {
    return logFile;
  }

  /** Closes the log file handler and delete the temp log file . */
  public void deleteLogFileAfterSuccess()
  {
    if (isEnabled())
    {
    	if (writer!=null) {
    		writer.shutdown();
    	}
      logFile.delete();
    }
  }

  /**
   * Return {@code true} if a temp log file has been created and could be used to log messages.
   * @return {@code true} if a temp log file has been created and could be used to log messages.
   */
  public boolean isEnabled()
  {
    return logFile != null;
  }

  /**
   * Return {@code true} if the temp log file is still on disk and can be read.
   * <p>
   * Unlike {@link #isEnabled()} this is about the file, not the logger: something else may have
   * removed the file while the logger still writes to it (see issue #1030), and then there is
   * nothing to hand over to whoever needs the log.
   *
   * @return {@code true} if the temp log file is there and readable.
   */
  public boolean isReadable()
  {
    return logFile != null && Files.isReadable(logFile.toPath()) && Files.isRegularFile(logFile.toPath());
  }

  /**
   * Reads the whole temp log file.
   *
   * @return the contents of the temp log file, decoded as UTF-8.
   * @throws IOException
   *           if the file cannot be read, for instance because it is no longer there.
   */
  public String readContents() throws IOException
  {
    if (logFile == null)
    {
      throw new IOException("No temp log file");
    }
    return new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);
  }

  /**
   * Return the absolute path of the temp log file.
   * @return the absolute path of the temp log file.
   */
  public String getPath()
  {
    return logFile.getAbsolutePath();
  }
}
