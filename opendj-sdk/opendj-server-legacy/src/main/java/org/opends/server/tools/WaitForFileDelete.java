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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.tools;

import java.io.*;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.JDKLogging;
import org.opends.server.types.NullOutputStream;

import com.forgerock.opendj.cli.*;

import static org.opends.messages.CoreMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.util.StaticUtils.*;
import static com.forgerock.opendj.cli.Utils.filterExitCode;

/**
 * This program provides a simple tool that will wait for a specified file to be
 * deleted before exiting.  It can be used in the process of confirming that the
 * server has completed its startup or shutdown process.
 */
public class WaitForFileDelete extends ConsoleApplication
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
   * Constructor for the WaitForFileDelete object.
   *
   * @param out the print stream to use for standard output.
   * @param err the print stream to use for standard error.
   * @param in the input stream to use for standard input.
   */
  public WaitForFileDelete(PrintStream out, PrintStream err, InputStream in)
  {
    super(out, err);
  }

  /**
   * Processes the command-line arguments and initiates the process of waiting
   * for the file to be removed.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int retCode = mainCLI(args, true, System.out, System.err, System.in);

    System.exit(retCode);
  }

  /**
   * Processes the command-line arguments and initiates the process of waiting
   * for the file to be removed.
   *
   * @param  args              The command-line arguments provided to this
   *                           program.
   * @param initializeServer   Indicates whether to initialize the server.
   * @param  outStream         The output stream to use for standard output, or
   *                           <CODE>null</CODE> if standard output is not
   *                           needed.
   * @param  errStream         The output stream to use for standard error, or
   *                           <CODE>null</CODE> if standard error is not
   *                           needed.
   * @param  inStream          The input stream to use for standard input.
   * @return The error code.
   */

  public static int mainCLI(String[] args, boolean initializeServer,
      OutputStream outStream, OutputStream errStream, InputStream inStream)
  {
    int exitCode;
    PrintStream out = NullOutputStream.wrapOrNullStream(outStream);
    PrintStream err = NullOutputStream.wrapOrNullStream(errStream);
    JDKLogging.disableLogging();
    try
    {
      WaitForFileDelete wffd = new WaitForFileDelete(out, err, System.in);
      exitCode = wffd.mainWait(args);
      if (exitCode != EXIT_CODE_SUCCESS)
      {
        exitCode = filterExitCode(exitCode);
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
      exitCode = EXIT_CODE_INTERNAL_ERROR;
    }
    return exitCode;
  }



  /**
   * Processes the command-line arguments and then waits for the specified file
   * to be removed.
   *
   * @param  args  The command-line arguments provided to this program.
   * @param  out         The output stream to use for standard output, or
   *                           <CODE>null</CODE> if standard output is not
   *                           needed.
   * @param  err         The output stream to use for standard error, or
   *                           <CODE>null</CODE> if standard error is not
   *                           needed.
   * @param  inStream          The input stream to use for standard input.
   *
   * @return  An integer value of zero if the file was deleted successfully, or
   *          some other value if a problem occurred.
   */
  private int mainWait(String[] args)
  {
    // Create all of the command-line arguments for this program.
    BooleanArgument showUsage      = null;
    IntegerArgument timeout        = null;
    StringArgument  logFilePath    = null;
    StringArgument  targetFilePath = null;
    StringArgument  outputFilePath = null;
    BooleanArgument useLastKnownGoodConfig = null;
    BooleanArgument quietMode              = null;

    LocalizableMessage toolDescription = INFO_WAIT4DEL_TOOL_DESCRIPTION.get();
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
                                    true, INFO_SECONDS_PLACEHOLDER.get(),
                                    DirectoryServer.DEFAULT_TIMEOUT,
                                    null, true, 0, false,
                                    0, INFO_WAIT4DEL_DESCRIPTION_TIMEOUT.get());
      argParser.addArgument(timeout);


      // Not used in this class, but required by the start-ds script
      // (see issue #3814)
      useLastKnownGoodConfig =
           new BooleanArgument("lastknowngoodconfig", 'L',
                               "useLastKnownGoodConfig",
                               INFO_DSCORE_DESCRIPTION_LASTKNOWNGOODCFG.get());
      argParser.addArgument(useLastKnownGoodConfig);

      // Not used in this class, but required by the start-ds script
      // (see issue #3814)
      quietMode = CommonArguments.getQuiet();
      argParser.addArgument(quietMode);

      showUsage = CommonArguments.getShowUsage();
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage);
    }
    catch (ArgumentException ae)
    {
      LocalizableMessage message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
      println(message);
      return EXIT_CODE_INTERNAL_ERROR;
    }


    // Parse the command-line arguments provided to the program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      argParser.displayMessageAndUsageReference(getErrStream(), ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
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
        println(WARN_WAIT4DEL_CANNOT_OPEN_LOG_FILE.get(logFilePath.getValue(), e));
        logFile = null;
      }
    }


    // If an output file was specified and we could open the log file, open it
    // and append data to it.
    RandomAccessFile outputFile = null;
    long outputFileOffset = 0L;
    if (logFile != null && outputFilePath.isPresent())
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
        println(WARN_WAIT4DEL_CANNOT_OPEN_OUTPUT_FILE.get(outputFilePath.getValue(), e));
        outputFile = null;
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
                getOutputStream().write(logBuffer, 0, bytesRead);
                getOutputStream().flush();
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

    close(outputFile);

    if (targetFile.exists())
    {
      println(ERR_TIMEOUT_DURING_STARTUP.get(
          Integer.parseInt(timeout.getValue()),
          timeout.getLongIdentifier()));
      return EXIT_CODE_TIMEOUT;
    }
    else
    {
      return EXIT_CODE_SUCCESS;
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAdvancedMode()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isInteractive()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isMenuDrivenMode()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isQuiet()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isScriptFriendly()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isVerbose()
  {
    return false;
  }
}

