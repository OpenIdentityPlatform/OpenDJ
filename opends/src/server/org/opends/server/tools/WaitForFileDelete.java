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
package org.opends.server.tools;
import org.opends.messages.Message;



import java.io.File;
import java.io.RandomAccessFile;

import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This program provides a simple tool that will wait for a specified file to be
 * deleted before exiting.  It can be used in the process of confirming that the
 * server has completed its startup or shutdown process.
 */
public class WaitForFileDelete
{
  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME =
       "org.opends.server.tools.WaitForFileDelete";



  /**
   * The exit code value that will be used if the target file is deleted
   * successfully.
   */
  public static final int EXIT_CODE_SUCCESS = 0;



  /**
   * The exit code value that will be used if an internal error occurs within
   * this program.
   */
  public static final int EXIT_CODE_INTERNAL_ERROR = 1;



  /**
   * The exit code value that will be used if a timeout occurs while waiting for
   * the file to be removed.
   */
  public static final int EXIT_CODE_TIMEOUT = 2;



  /**
   * Processes the command-line arguments and initiates the process of waiting
   * for the file to be removed.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    try
    {
      int exitCode = mainWait(args);
      if (exitCode != EXIT_CODE_SUCCESS)
      {
        System.exit(filterExitCode(exitCode));
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
      System.exit(EXIT_CODE_INTERNAL_ERROR);
    }
  }



  /**
   * Processes the command-line arguments and then waits for the specified file
   * to be removed.
   *
   * @param  args  The command-line arguments provided to this program.
   *
   * @return  An integer value of zero if the file was deleted successfully, or
   *          some other value if a problem occurred.
   */
  public static int mainWait(String[] args)
  {
    // Create all of the command-line arguments for this program.
    BooleanArgument showUsage      = null;
    IntegerArgument timeout        = null;
    StringArgument  logFilePath    = null;
    StringArgument  targetFilePath = null;
    StringArgument  outputFilePath = null;

    Message toolDescription = INFO_WAIT4DEL_TOOL_DESCRIPTION.get();
    ArgumentParser argParser = new ArgumentParser(CLASS_NAME, toolDescription,
                                                  false);

    try
    {
      targetFilePath =
           new StringArgument("targetfile", 'f', "targetFile", true, false,
                              true, INFO_PATH_PLACEHOLDER.get(), null, null,
                              INFO_WAIT4DEL_DESCRIPTION_TARGET_FILE.get());
      argParser.addArgument(targetFilePath);


      logFilePath = new StringArgument(
              "logfile", 'l', "logFile", false, false,
              true, INFO_PATH_PLACEHOLDER.get(), null, null,
              INFO_WAIT4DEL_DESCRIPTION_LOG_FILE.get());
      argParser.addArgument(logFilePath);


      outputFilePath = new StringArgument(
              "outputfile", 'o', "outputFile",
              false, false,
              true, INFO_PATH_PLACEHOLDER.get(), null, null,
              INFO_WAIT4DEL_DESCRIPTION_OUTPUT_FILE.get());
      argParser.addArgument(outputFilePath);


      timeout = new IntegerArgument("timeout", 't', "timeout", true, false,
                                    true, INFO_SECONDS_PLACEHOLDER.get(), 60,
                                    null, true, 0, false,
                                    0, INFO_WAIT4DEL_DESCRIPTION_TIMEOUT.get());
      argParser.addArgument(timeout);


      showUsage = new BooleanArgument("help", 'H', "help",
                                      INFO_WAIT4DEL_DESCRIPTION_HELP.get());
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage);
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return EXIT_CODE_INTERNAL_ERROR;
    }


    // Parse the command-line arguments provided to the program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());

      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      System.err.println(argParser.getUsage());
      return EXIT_CODE_INTERNAL_ERROR;
    }


    // If we should just display usage or version information,
    // then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return EXIT_CODE_SUCCESS;
    }


    // Get the file to watch.  If it doesn't exist now, then exit immediately.
    File targetFile = new File(targetFilePath.getValue());
    if (! targetFile.exists())
    {
      return EXIT_CODE_SUCCESS;
    }


    // If a log file was specified, then open it.
    long logFileOffset = 0L;
    RandomAccessFile logFile = null;
    if (logFilePath.isPresent())
    {
      try
      {
        File f = new File(logFilePath.getValue());
        if (f.exists())
        {
          logFile = new RandomAccessFile(f, "r");
          logFileOffset = logFile.length();
          logFile.seek(logFileOffset);
        }
      }
      catch (Exception e)
      {
        Message message = WARN_WAIT4DEL_CANNOT_OPEN_LOG_FILE.get(
                logFilePath.getValue(), String.valueOf(e));
        System.err.println(wrapText(message, MAX_LINE_WIDTH));

        logFile = null;
      }
    }


    // If an output file was specified and we could open the log file, open it
    // and append data to it.
    RandomAccessFile outputFile = null;
    long outputFileOffset = 0L;
    if (logFile != null)
    {
      if (outputFilePath.isPresent())
      {
        try
        {
          File f = new File(outputFilePath.getValue());
          if (f.exists())
          {
            outputFile = new RandomAccessFile(f, "rw");
            outputFileOffset = outputFile.length();
            outputFile.seek(outputFileOffset);
          }
        }
        catch (Exception e)
        {
          Message message = WARN_WAIT4DEL_CANNOT_OPEN_OUTPUT_FILE.get(
                  outputFilePath.getValue(), String.valueOf(e));
          System.err.println(wrapText(message, MAX_LINE_WIDTH));

          outputFile = null;
        }
      }
    }
    // Figure out when to stop waiting.
    long stopWaitingTime;
    try
    {
      long timeoutMillis = 1000L * Integer.parseInt(timeout.getValue());
      if (timeoutMillis > 0)
      {
        stopWaitingTime = System.currentTimeMillis() + timeoutMillis;
      }
      else
      {
        stopWaitingTime = Long.MAX_VALUE;
      }
    }
    catch (Exception e)
    {
      // This shouldn't happen, but if it does then ignore it.
      stopWaitingTime = System.currentTimeMillis() + 60000;
    }


    // Operate in a loop, printing out any applicable log messages and waiting
    // for the target file to be removed.
    byte[] logBuffer = new byte[8192];
    while (System.currentTimeMillis() < stopWaitingTime)
    {
      if (logFile != null)
      {
        try
        {
          while (logFile.length() > logFileOffset)
          {
            int bytesRead = logFile.read(logBuffer);
            if (bytesRead > 0)
            {
              if (outputFile == null)
              {
                System.out.write(logBuffer, 0, bytesRead);
                System.out.flush();
              }
              else
              {
                // Write on the file.
                // TODO
                outputFile.write(logBuffer, 0, bytesRead);

              }
              logFileOffset += bytesRead;
            }
          }
        }
        catch (Exception e)
        {
          // We'll just ignore this.
        }
      }


      if (! targetFile.exists())
      {
        break;
      }
      else
      {
        try
        {
          Thread.sleep(10);
        } catch (InterruptedException ie) {}
      }
    }

    if (outputFile != null)
    {
      try
      {
        outputFile.close();
      }
      catch (Throwable t) {}
    }

    if (targetFile.exists())
    {
      return EXIT_CODE_TIMEOUT;
    }
    else
    {
      return EXIT_CODE_SUCCESS;
    }
  }
}

