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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.server.tools;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.EOL;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Enumeration;
import java.util.Properties;

import org.opends.messages.Message;
import org.opends.messages.ToolMessages;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.util.Utils;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.cli.ConsoleApplication;

/**
 * This class is used to update the scripts that are used to launch the command
 * lines.  We read the contents of a given properties file and we update the
 * scripts setting the arguments and JVM to be used by the different scripts.
 *
 */
public class JavaPropertiesTool extends ConsoleApplication
{
  // The argument parser
  private JavaPropertiesToolArgumentParser argParser;

  /**
   * The enumeration containing the different return codes that the command-line
   * can have.
   *
   */
  public enum ErrorReturnCode
  {
    /**
     * Successful setup.
     */
    SUCCESSFUL(0),
    /**
     * We did no have an error but the setup was not executed (displayed version
     * or usage).
     */
    SUCCESSFUL_NOP(0),
    /**
     * Unexpected error (potential bug).
     */
    ERROR_UNEXPECTED(1),
    /**
     * Cannot parse arguments or data provided by user is not valid.
     */
    ERROR_USER_DATA(2),
    /**
     * Error writing to destination file.
     */
    ERROR_WRITING_FILE(3);

    private int returnCode;
    private ErrorReturnCode(int returnCode)
    {
      this.returnCode = returnCode;
    }

    /**
     * Get the corresponding return code value.
     *
     * @return The corresponding return code value.
     */
    public int getReturnCode()
    {
      return returnCode;
    }
  };

  final private static String DEFAULT_JAVA_HOME_PROP_NAME = "default.java-home";
  final private static String DEFAULT_JAVA_ARGS_PROP_NAME = "default.java-args";
  final private static String OVERWRITE_ENV_JAVA_HOME_PROP_NAME =
    "overwrite-env-java-home";
  final private static String OVERWRITE_ENV_JAVA_ARGS_PROP_NAME =
    "overwrite-env-java-args";

  /**
   * Constructor for the JavaPropertiesTool object.
   *
   * @param out the print stream to use for standard output.
   * @param err the print stream to use for standard error.
   * @param in the input stream to use for standard input.
   */
  public JavaPropertiesTool(PrintStream out, PrintStream err, InputStream in)
  {
    super(in, out, err);
  }

  /**
   * The main method for the java properties tool.
   *
   * @param args the command-line arguments provided to this program.
   */

  public static void main(String[] args)
  {
    int retCode = mainCLI(args, System.out, System.err, System.in);

    System.exit(retCode);
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the java properties tool.
   *
   * @param args the command-line arguments provided to this program.
   *
   * @return The error code.
   */

  public static int mainCLI(String[] args)
  {
    return mainCLI(args, System.out, System.err, System.in);
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the java properties tool.
   *
   * @param  args              The command-line arguments provided to this
   *                           program.
   * @param  outStream         The output stream to use for standard output, or
   *                           <CODE>null</CODE> if standard output is not
   *                           needed.
   * @param  errStream         The output stream to use for standard error, or
   *                           <CODE>null</CODE> if standard error is not
   *                           needed.
   * @param  inStream          The input stream to use for standard input.
   * @return The error code.
   */

  public static int mainCLI(String[] args, OutputStream outStream,
      OutputStream errStream, InputStream inStream)
  {
    PrintStream out;
    if (outStream == null)
    {
      out = NullOutputStream.printStream();
    }
    else
    {
      out = new PrintStream(outStream);
    }

    System.setProperty(Constants.CLI_JAVA_PROPERTY, "true");

    PrintStream err;
    if (errStream == null)
    {
      err = NullOutputStream.printStream();
    }
    else
    {
      err = new PrintStream(errStream);
    }

    JavaPropertiesTool tool = new JavaPropertiesTool(out, err, inStream);

    return tool.execute(args);
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the java properties tool.
   *
   * @param args the command-line arguments provided to this program.
   *
   * @return the return code (SUCCESSFUL, USER_DATA_ERROR or BUG).
   */
  public int execute(String[] args)
  {
    argParser = new JavaPropertiesToolArgumentParser(
        JavaPropertiesTool.class.getName());
    try
    {
      argParser.initializeArguments();
    }
    catch (ArgumentException ae)
    {
      Message message =
        ToolMessages.ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
      println(message);
      return ErrorReturnCode.ERROR_UNEXPECTED.getReturnCode();
    }

    // Validate user provided data
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());
      println(message);
      println();
      println(Message.raw(argParser.getUsage()));

      return ErrorReturnCode.ERROR_USER_DATA.getReturnCode();
    }

    if (argParser.usageOrVersionDisplayed())
    {
      return ErrorReturnCode.SUCCESSFUL_NOP.getReturnCode();
    }

    Properties properties = new Properties();
    BufferedReader reader = null;
    String propertiesFile = argParser.propertiesFileArg.getValue();
    try
    {
      reader = new BufferedReader(new FileReader(propertiesFile));
    }
    catch (FileNotFoundException fnfe)
    {
      println(ERR_JAVAPROPERTIES_WITH_PROPERTIES_FILE.get(propertiesFile));
      return ErrorReturnCode.ERROR_USER_DATA.getReturnCode();
    }
    try
    {
      String line;
      // Parse the file manually since '\' in windows paths can generate issues.
      while ((line = reader.readLine()) != null)
      {
        line = line.trim();
        if (!line.startsWith("#"))
        {
          int index = line.indexOf('=');
          if (index != -1)
          {
            String key = line.substring(0, index);
            if (key.indexOf(' ') == -1)
            {
              if (index < line.length())
              {
                String value = line.substring(index+1);
                properties.setProperty(key, value);
              }
              else
              {
                properties.setProperty(key, "");
              }
            }
          }
        }
      }
    }
    catch (IOException ioe)
    {
      println(ERR_JAVAPROPERTIES_WITH_PROPERTIES_FILE.get(propertiesFile));
      return ErrorReturnCode.ERROR_USER_DATA.getReturnCode();
    }

    String destinationFile = argParser.destinationFileArg.getValue();

    BufferedWriter writer = null;
    try
    {
      writer = new BufferedWriter(new FileWriter(destinationFile));
    }
    catch (IOException ioe)
    {
      println(ERR_JAVAPROPERTIES_WITH_DESTINATION_FILE.get(destinationFile));
      return ErrorReturnCode.ERROR_USER_DATA.getReturnCode();
    }

    Enumeration propertyNames = properties.propertyNames();

    boolean overwriteEnvJavaHome = true;
    boolean overwriteEnvJavaArgs = true;
    String defaultJavaHome = null;
    String defaultJavaArgs = null;

    while (propertyNames.hasMoreElements())
    {
      String name = propertyNames.nextElement().toString();
      String value = properties.getProperty(name);

      if (value != null)
      {
        if (name.equalsIgnoreCase(DEFAULT_JAVA_HOME_PROP_NAME))
        {
          defaultJavaHome = value;
        }
        else if (name.equalsIgnoreCase(DEFAULT_JAVA_ARGS_PROP_NAME))
        {
          defaultJavaArgs = value;
        }
        else if (name.equalsIgnoreCase(OVERWRITE_ENV_JAVA_HOME_PROP_NAME))
        {
          if ("false".equalsIgnoreCase(value))
          {
            overwriteEnvJavaHome = false;
          }
        }
        else if (name.equalsIgnoreCase(OVERWRITE_ENV_JAVA_ARGS_PROP_NAME))
        {
          if ("false".equalsIgnoreCase(value))
          {
            overwriteEnvJavaArgs = false;
          }
        }
      }
    }

    try
    {
      String value;
      if (Utils.isWindows())
      {
        value = getWindowsContents(overwriteEnvJavaHome, overwriteEnvJavaArgs,
            defaultJavaHome, defaultJavaArgs, properties);
      }
      else
      {
        value = getUnixContents(overwriteEnvJavaHome, overwriteEnvJavaArgs,
            defaultJavaHome, defaultJavaArgs, properties);
      }

      writer.write(value);
      writer.newLine();
      writer.close();
    }
    catch (IOException ioe)
    {
      println(Utils.getThrowableMsg(
          ERR_JAVAPROPERTIES_WRITING_DESTINATION_FILE.get(destinationFile),
          ioe));
      return ErrorReturnCode.ERROR_WRITING_FILE.getReturnCode();
    }

    // Add some information if we are not in quiet mode about
    // what is going to happen.
    File f1 = new File(argParser.destinationFileArg.getValue());
    File f2 = new File(argParser.destinationFileArg.getDefaultValue());
    if (f1.equals(f2))
    {
      printProgress(INFO_JAVAPROPERTIES_SUCCESSFUL.get(
          argParser.propertiesFileArg.getValue()));
    }
    else
    {
      printProgress(INFO_JAVAPROPERTIES_SUCCESSFUL_NON_DEFAULT.get(
          argParser.destinationFileArg.getValue(),
          argParser.propertiesFileArg.getValue(),
          argParser.destinationFileArg.getDefaultValue()));
    }
    printlnProgress();


    return ErrorReturnCode.SUCCESSFUL.getReturnCode();
  }


  /**
   * {@inheritDoc}
   */
  public boolean isQuiet()
  {
    return argParser.quietArg.isPresent();
  }

  /**
   * {@inheritDoc}
   */
  public boolean isInteractive()
  {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isMenuDrivenMode() {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isScriptFriendly() {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isAdvancedMode() {
    return false;
  }


  /**
   * {@inheritDoc}
   */
  public boolean isVerbose() {
    return true;
  }

  private String getUnixContents(boolean overwriteJavaHome,
      boolean overwriteJavaArgs, String defaultJavaHome, String defaultJavaArgs,
      Properties properties)
  {
    StringBuilder buf = new StringBuilder();
    buf.append("#!/bin/sh"+EOL+EOL);

    if (!overwriteJavaHome)
    {
      buf.append(
          "# See if the environment variables for java home are set"+EOL+
          "# in the path and try to figure it out."+EOL+
          "if test ! -f \"${OPENDS_JAVA_BIN}\""+EOL+
          "then"+EOL+
          "  if test ! -d \"${OPENDS_JAVA_HOME}\""+EOL);
    }

    boolean propertiesAdded = false;

    Enumeration propertyNames = properties.propertyNames();
    int nIfs = 0;
    while (propertyNames.hasMoreElements())
    {
      String name = propertyNames.nextElement().toString();
      String value = properties.getProperty(name);

      if (value != null)
      {
        if (name.equalsIgnoreCase(DEFAULT_JAVA_HOME_PROP_NAME) ||
            name.equalsIgnoreCase(DEFAULT_JAVA_ARGS_PROP_NAME) ||
            name.equalsIgnoreCase(OVERWRITE_ENV_JAVA_HOME_PROP_NAME) ||
            name.equalsIgnoreCase(OVERWRITE_ENV_JAVA_ARGS_PROP_NAME))
        {
          // Already handled
        }
        else if (name.endsWith(".java-home"))
        {
          propertiesAdded = true;
          String s;
          if (nIfs > 0)
          {
            if (!overwriteJavaHome)
            {
              s = "    ";
            }
            else
            {
              s = "";
            }
            buf.append(
                s+"elif test \"${SCRIPT_NAME}.java-home\" = \""+name+"\""+EOL);
          }
          else if (!overwriteJavaHome)
          {
            buf.append(
                "  then"+EOL+
                "    if test \"${SCRIPT_NAME}.java-home\" = \""+name+"\""+EOL);
            s = "    ";
          }
          else
          {
            buf.append(
                "if test \"${SCRIPT_NAME}.java-home\" = \""+name+"\""+EOL);
            s = "";
          }

          buf.append(
          s+"then"+EOL+
          s+"  TEMP=\""+value+"/bin/java\""+EOL+
          s+"  if test -f ${TEMP}"+EOL+
          s+"  then"+EOL+
          s+"    OPENDS_JAVA_BIN=\""+value+"/bin/java\""+EOL+
          s+"    export OPENDS_JAVA_BIN"+EOL+
          s+"  fi"+EOL);
          nIfs++;
        }
      }
    }
    if (defaultJavaHome != null)
    {
      if (propertiesAdded)
      {
        String s;
        if (!overwriteJavaHome)
        {
          s = "    ";
        }
        else
        {
          s = "";
        }
        buf.append(
            s+"else"+EOL+
            s+"  OPENDS_JAVA_BIN=\""+defaultJavaHome+"/bin/java\""+EOL+
            s+"  export OPENDS_JAVA_BIN"+EOL);
      }
      else
      {
        if (!overwriteJavaHome)
        {
          buf.append(
              "  then"+EOL+
              "    TEMP=\""+defaultJavaHome+"/bin/java\""+EOL+
              "    if test -f ${TEMP}"+EOL+
              "    then"+EOL+
              "      OPENDS_JAVA_BIN=${TEMP}"+EOL+
              "      export OPENDS_JAVA_BIN"+EOL+
              "    fi"+EOL);
        }
        else
        {
          buf.append(
            "OPENDS_JAVA_BIN=\""+defaultJavaHome+"/bin/java\""+EOL+
            "export OPENDS_JAVA_BIN"+EOL);
        }
      }
      propertiesAdded = true;
    }

    if (nIfs > 0)
    {
      String s;
      if (!overwriteJavaHome)
      {
        s = "    ";
      }
      else
      {
        s = "";
      }
      buf.append(
          s+"fi"+EOL);
    }


    if (!overwriteJavaHome)
    {
      if (!propertiesAdded)
      {
        // No properties added: this is required not to break the script
        buf.append(
            "  then"+EOL+
            "  OPENDS_JAVA_BIN=${OPENDS_JAVA_BIN}"+EOL);
      }
      buf.append(
          "  else"+EOL+
          "    OPENDS_JAVA_BIN=${OPENDS_JAVA_HOME}/bin/java"+EOL+
          "    export OPENDS_JAVA_BIN"+EOL+
          "  fi"+EOL+
          "fi"+EOL+EOL);
    }
    else if (defaultJavaHome == null)
    {
      buf.append(
          EOL+
          "if test ! -f \"${OPENDS_JAVA_BIN}\""+EOL+
          "then"+EOL+
          "  if test ! -d \"${OPENDS_JAVA_HOME}\""+EOL+
          "  then"+EOL+
          "    if test ! -f \"${JAVA_BIN}\""+EOL+
          "    then"+EOL+
          "      if test ! -d \"${JAVA_HOME}\""+EOL+
          "      then"+EOL+
          "        OPENDS_JAVA_BIN=`which java 2> /dev/null`"+EOL+
          "        if test ${?} -eq 0"+EOL+
          "        then"+EOL+
          "          export OPENDS_JAVA_BIN"+EOL+
          "        else"+EOL+
          "          echo \"You must specify the path to a valid Java 5.0 or "+
          "higher version in the\""+EOL+
          "          echo \"properties file and then run the dsjavaproperties "+
          "tool. \""+EOL+
          "          echo \"The procedure to follow is:\""+EOL+
          "          echo \"You must specify the path to a valid Java 5.0 or "+
          "higher version.  The \""+EOL+
          "          echo \"procedure to follow is:\""+EOL+
          "          echo \"1. Delete the file "+
          "${INSTANCE_ROOT}/lib/set-java-home\""+EOL+
          "          echo \"2. Set the environment variable OPENDS_JAVA_HOME "+
          "to the root of a valid \""+EOL+
          "          echo \"Java 5.0 installation.\""+EOL+
          "          echo \"If you want to have specificjava  settings for "+
          "each command line you must\""+EOL+
          "          echo \"follow the steps 3 and 4\""+EOL+
          "          echo \"3. Edit the properties file specifying the java "+
          "binary and the java arguments\""+EOL+
          "          echo \"for each command line.  The java properties file "+
          "is located in:\""+EOL+
          "          echo \"${INSTANCE_ROOT}/config/java.properties.\""+EOL+
          "          echo \"4. Run the command-line "+
          "${INSTANCE_ROOT}/bin/dsjavaproperties\""+EOL+
          "          exit 1"+EOL+
          "        fi"+EOL+
          "      else"+EOL+
          "        OPENDS_JAVA_BIN=\"${JAVA_HOME}/bin/java\""+EOL+
          "        export OPENDS_JAVA_BIN"+EOL+
          "      fi"+EOL+
          "    else"+EOL+
          "      OPENDS_JAVA_BIN=\"${JAVA_BIN}\""+EOL+
          "      export OPENDS_JAVA_BIN"+EOL+
          "    fi"+EOL+
          "  else"+EOL+
          "    OPENDS_JAVA_BIN=\"${OPENDS_JAVA_HOME}/bin/java\""+EOL+
          "    export OPENDS_JAVA_BIN"+EOL+
          "  fi"+EOL+
          "fi"+EOL+EOL);
    }


    if (!overwriteJavaArgs)
    {
      buf.append(
          EOL+
          "# See if the environment variables for arguments are set."+EOL+
          "if test -z \"${OPENDS_JAVA_ARGS}\""+EOL);
    }

    propertiesAdded = false;

    propertyNames = properties.propertyNames();
    nIfs = 0;
    while (propertyNames.hasMoreElements())
    {
      String name = propertyNames.nextElement().toString();
      String value = properties.getProperty(name);

      String s = overwriteJavaArgs? "":"  ";

      if (value != null)
      {
        if (name.equalsIgnoreCase(DEFAULT_JAVA_HOME_PROP_NAME) ||
            name.equalsIgnoreCase(DEFAULT_JAVA_ARGS_PROP_NAME) ||
            name.equalsIgnoreCase(OVERWRITE_ENV_JAVA_HOME_PROP_NAME) ||
            name.equalsIgnoreCase(OVERWRITE_ENV_JAVA_ARGS_PROP_NAME))
        {
          // Already handled
        }
        else if (name.endsWith(".java-args"))
        {
          propertiesAdded = true;
          if (nIfs > 0)
          {
            buf.append(
                s+"elif test \"${SCRIPT_NAME}.java-args\" = \""+name+"\""+EOL);
          }
          else if (!overwriteJavaArgs)
          {
            buf.append(
                "then"+EOL+
                "  if test \"${SCRIPT_NAME}.java-args\" = \""+name+"\""+EOL);
          }
          else
          {
            buf.append(
                "if test \"${SCRIPT_NAME}.java-args\" = \""+name+"\""+EOL);
          }
          buf.append(
          s+"then"+EOL+
          s+"  OPENDS_JAVA_ARGS=\""+value+"\""+EOL+
          s+"  export OPENDS_JAVA_ARGS"+EOL);
          nIfs++;
        }
      }
    }
    if (defaultJavaArgs != null)
    {
      String s = overwriteJavaArgs? "":"  ";
      if (propertiesAdded)
      {
        buf.append(
            s+"else"+EOL+
            s+"  OPENDS_JAVA_ARGS=\""+defaultJavaArgs+"\""+EOL+
            s+"  export OPENDS_JAVA_ARGS"+EOL);
      }
      else
      {
        if (!overwriteJavaArgs)
        {
          buf.append(
              "    then"+EOL+
              "      OPENDS_JAVA_ARGS=\""+defaultJavaArgs+"\""+EOL+
              "      export OPENDS_JAVA_ARGS"+EOL);
        }
        else
        {
          buf.append(
              EOL+
              "OPENDS_JAVA_ARGS=\""+defaultJavaArgs+"\""+EOL+
              "export OPENDS_JAVA_ARGS"+EOL);
        }
      }
      propertiesAdded = true;
    }
    if (nIfs > 0)
    {
      String s = overwriteJavaArgs? "":"  ";
      buf.append(s+"fi"+EOL);
    }

    if (!overwriteJavaArgs)
    {
      if (!propertiesAdded)
      {
        // No properties added: this is required not to break the script
        buf.append(
            "  then"+EOL+
            "  OPENDS_JAVA_ARGS=${OPENDS_JAVA_ARGS}"+EOL);
      }
      buf.append(
          "fi"+EOL);
    }

    return buf.toString();
  }

  private String getWindowsContents(boolean overwriteJavaHome,
      boolean overwriteJavaArgs, String defaultJavaHome, String defaultJavaArgs,
      Properties properties)
  {
    StringBuilder buf = new StringBuilder();

    String javaHomeLabel1;
    String javaArgsLabel1;
    String javaHomeLabel2;
    String javaArgsLabel2;

    final String CHECK_ENV_JAVA_HOME = "checkEnvJavaHome";
    final String CHECK_ENV_JAVA_ARGS = "checkEnvJavaArgs";
    final String CHECK_JAVA_HOME = "checkJavaHome";
    final String CHECK_JAVA_ARGS = "checkJavaArgs";
    final String CHECK_DEFAULT_JAVA_HOME = "checkDefaultJavaHome";
    final String CHECK_DEFAULT_JAVA_ARGS = "checkDefaultJavaArgs";

    if (!overwriteJavaHome)
    {
      javaHomeLabel1 = CHECK_ENV_JAVA_HOME;
      javaHomeLabel2 = CHECK_JAVA_HOME;
    }
    else
    {
      javaHomeLabel1 = CHECK_JAVA_HOME;
      javaHomeLabel2 = CHECK_ENV_JAVA_HOME;
    }

    if (!overwriteJavaArgs)
    {
      javaArgsLabel1 = CHECK_ENV_JAVA_ARGS;
      javaArgsLabel2 = CHECK_JAVA_ARGS;
    }
    else
    {
      javaArgsLabel1 = CHECK_JAVA_ARGS;
      javaArgsLabel2 = CHECK_ENV_JAVA_ARGS;
    }

    buf.append("goto "+javaHomeLabel1+EOL+EOL);

    buf.append(
        ":"+CHECK_ENV_JAVA_HOME+EOL+
        "if \"%OPENDS_JAVA_BIN%\" == \"\" goto checkOpendsJavaHome"+EOL+
        "if not exist \"%OPENDS_JAVA_BIN%\" goto checkOpendsJavaHome"+EOL+
        "goto "+javaArgsLabel1+EOL+EOL+
        ":checkOpendsJavaHome"+EOL);

    if (javaHomeLabel1 == CHECK_ENV_JAVA_HOME)
    {
      buf.append(
          "if \"%OPENDS_JAVA_HOME%\" == \"\" goto "+javaHomeLabel2+EOL+
          "set TEMP=%OPENDS_JAVA_HOME%\\bin\\java.exe"+EOL+
          "if not exist \"%TEMP%\" goto "+javaHomeLabel2+EOL+
          "set OPENDS_JAVA_BIN=%TEMP%"+EOL+
          "goto "+javaArgsLabel1+EOL+EOL
      );
    }
    else
    {
      buf.append(
          "if \"%OPENDS_JAVA_HOME%\" == \"\" goto "+javaArgsLabel1+EOL+
          "set TEMP=%OPENDS_JAVA_HOME%\\bin\\java.exe"+EOL+
          "if not exist \"%TEMP%\" goto "+javaArgsLabel1+EOL+
          "set OPENDS_JAVA_BIN=%TEMP%"+EOL+
          "goto "+javaArgsLabel1+EOL+EOL
      );
    }

    if (defaultJavaHome != null)
    {
      if (javaHomeLabel1 == CHECK_ENV_JAVA_HOME)
      {
        buf.append(
            ":"+CHECK_DEFAULT_JAVA_HOME+EOL+
            "set TEMP="+defaultJavaHome+"\\bin\\java.exe"+EOL+
            "if not exist \"%TEMP%\" goto "+javaArgsLabel1+EOL+
            "set OPENDS_JAVA_BIN=%TEMP%"+EOL+
            "goto "+javaArgsLabel1+EOL+EOL
        );
      }
      else
      {
        buf.append(
            ":"+CHECK_DEFAULT_JAVA_HOME+EOL+
            "set TEMP="+defaultJavaHome+"\\bin\\java.exe"+EOL+
            "if not exist \"%TEMP%\" goto "+CHECK_ENV_JAVA_HOME+EOL+
            "set OPENDS_JAVA_BIN=%TEMP%"+EOL+
            "goto "+javaArgsLabel1+EOL+EOL
        );
      }
    }

    buf.append(
        ":"+CHECK_JAVA_HOME+EOL);
    Enumeration propertyNames = properties.propertyNames();
    while (propertyNames.hasMoreElements())
    {
      String name = propertyNames.nextElement().toString();
      if (name.equalsIgnoreCase(DEFAULT_JAVA_HOME_PROP_NAME) ||
          name.equalsIgnoreCase(DEFAULT_JAVA_ARGS_PROP_NAME) ||
          name.equalsIgnoreCase(OVERWRITE_ENV_JAVA_HOME_PROP_NAME) ||
          name.equalsIgnoreCase(OVERWRITE_ENV_JAVA_ARGS_PROP_NAME))
      {
        // Already handled
      }
      else if (name.endsWith(".java-home"))
      {
        String scriptName = name.substring(0,
            name.length() - ".java-home".length());
        buf.append(
            "if \"%SCRIPT_NAME%.java-home\" == \""+name+"\" goto check"+
            scriptName+"JavaHome"+EOL);
      }
    }
    if (defaultJavaHome != null)
    {
      buf.append("goto "+CHECK_DEFAULT_JAVA_HOME+EOL+EOL);
    }
    else if (javaHomeLabel1 != CHECK_ENV_JAVA_HOME)
    {
      buf.append("goto "+CHECK_ENV_JAVA_HOME+EOL+EOL);
    }
    else
    {
      buf.append("goto "+javaArgsLabel1+EOL+EOL);
    }

    propertyNames = properties.propertyNames();
    while (propertyNames.hasMoreElements())
    {
      String name = propertyNames.nextElement().toString();
      String value = properties.getProperty(name);
      if (name.equalsIgnoreCase(DEFAULT_JAVA_HOME_PROP_NAME) ||
          name.equalsIgnoreCase(DEFAULT_JAVA_ARGS_PROP_NAME) ||
          name.equalsIgnoreCase(OVERWRITE_ENV_JAVA_HOME_PROP_NAME) ||
          name.equalsIgnoreCase(OVERWRITE_ENV_JAVA_ARGS_PROP_NAME))
      {
        // Already handled
      }
      else if (name.endsWith(".java-home"))
      {
        String scriptName = name.substring(0,
            name.length() - ".java-home".length());
        buf.append(
            ":check"+scriptName+"JavaHome"+EOL+
            "set TEMP="+value+"\\bin\\java.exe"+EOL);
        if (defaultJavaHome != null)
        {
          buf.append(
              "if not exist \"%TEMP%\" goto "+CHECK_DEFAULT_JAVA_HOME+EOL);
        }
        else if (javaHomeLabel1 != CHECK_ENV_JAVA_HOME)
        {
          buf.append(
              "if not exist \"%TEMP%\" goto "+CHECK_ENV_JAVA_HOME+EOL);
        }
        buf.append(
            "set OPENDS_JAVA_BIN=%TEMP%"+EOL+
            "goto "+javaArgsLabel1+EOL+EOL);
      }
    }

    buf.append(
        ":"+CHECK_ENV_JAVA_ARGS+EOL);
    if (javaArgsLabel1 == CHECK_ENV_JAVA_ARGS)
    {
      buf.append(
          "if \"%OPENDS_JAVA_ARGS%\" == \"\" goto "+javaArgsLabel2+EOL+
          "goto end"+EOL+EOL);
    }
    else
    {
      buf.append(
          "goto end"+EOL+EOL);
    }

    if (defaultJavaArgs != null)
    {
      buf.append(
          ":"+CHECK_DEFAULT_JAVA_ARGS+EOL+
          "set OPENDS_JAVA_ARGS="+defaultJavaArgs+EOL+
          "goto end"+EOL+EOL);
    }

    buf.append(
        ":"+CHECK_JAVA_ARGS+EOL);
    propertyNames = properties.propertyNames();
    while (propertyNames.hasMoreElements())
    {
      String name = propertyNames.nextElement().toString();
      if (name.equalsIgnoreCase(DEFAULT_JAVA_HOME_PROP_NAME) ||
          name.equalsIgnoreCase(DEFAULT_JAVA_ARGS_PROP_NAME) ||
          name.equalsIgnoreCase(OVERWRITE_ENV_JAVA_HOME_PROP_NAME) ||
          name.equalsIgnoreCase(OVERWRITE_ENV_JAVA_ARGS_PROP_NAME))
      {
        // Already handled
      }
      else if (name.endsWith(".java-args"))
      {
        String scriptName = name.substring(0,
            name.length() - ".java-args".length());
        buf.append(
            "if \"%SCRIPT_NAME%.java-args\" == \""+name+"\" goto check"+
            scriptName+"JavaArgs"+EOL);
      }
    }
    if (defaultJavaArgs != null)
    {
      buf.append("goto "+CHECK_DEFAULT_JAVA_ARGS+EOL+EOL);
    }
    else if (javaArgsLabel1 != CHECK_ENV_JAVA_ARGS)
    {
      buf.append("goto "+CHECK_ENV_JAVA_ARGS+EOL+EOL);
    }
    else
    {
      buf.append("goto end"+EOL+EOL);
    }

    propertyNames = properties.propertyNames();
    while (propertyNames.hasMoreElements())
    {
      String name = propertyNames.nextElement().toString();
      String value = properties.getProperty(name);
      if (name.equalsIgnoreCase(DEFAULT_JAVA_HOME_PROP_NAME) ||
          name.equalsIgnoreCase(DEFAULT_JAVA_ARGS_PROP_NAME) ||
          name.equalsIgnoreCase(OVERWRITE_ENV_JAVA_HOME_PROP_NAME) ||
          name.equalsIgnoreCase(OVERWRITE_ENV_JAVA_ARGS_PROP_NAME))
      {
        // Already handled
      }
      else if (name.endsWith(".java-args"))
      {
        String scriptName = name.substring(0,
            name.length() - ".java-args".length());
        buf.append(
            ":check"+scriptName+"JavaArgs"+EOL+
            "set OPENDS_JAVA_ARGS="+value+EOL+
            "goto end"+EOL+EOL);
      }
    }

    buf.append(":end"+EOL);

    return buf.toString();
  }
}
