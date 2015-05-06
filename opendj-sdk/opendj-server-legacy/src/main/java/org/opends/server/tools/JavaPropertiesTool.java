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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.tools;

import static com.forgerock.opendj.cli.Utils.getThrowableMsg;
import static com.forgerock.opendj.util.OperatingSystem.isWindows;
import static org.opends.server.util.ServerConstants.EOL;
import static org.opends.messages.ToolMessages.*;

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

import org.forgerock.i18n.LocalizableMessage;
import org.opends.messages.ToolMessages;
import org.opends.quicksetup.Constants;
import org.opends.server.loggers.JDKLogging;
import org.opends.server.types.NullOutputStream;
import com.forgerock.opendj.cli.ConsoleApplication;

import com.forgerock.opendj.cli.ArgumentException;


/**
 * This class is used to update the scripts that are used to launch the command
 * lines.  We read the contents of a given properties file and we update the
 * scripts setting the arguments and JVM to be used by the different scripts.
 *
 */
public class JavaPropertiesTool extends ConsoleApplication
{
  /** The argument parser. */
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
    ERROR_WRITING_FILE(3),
    /**
     * Conflicting command line arguments.
     */
    CONFLICTING_ARGS(18);

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
  }

  private static final String DEFAULT_JAVA_HOME_PROP_NAME = "default.java-home";
  private static final String DEFAULT_JAVA_ARGS_PROP_NAME = "default.java-args";
  private static final String OVERWRITE_ENV_JAVA_HOME_PROP_NAME =
    "overwrite-env-java-home";
  private static final String OVERWRITE_ENV_JAVA_ARGS_PROP_NAME =
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
    super(out, err);
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
    PrintStream out = NullOutputStream.wrapOrNullStream(outStream);

    System.setProperty(Constants.CLI_JAVA_PROPERTY, "true");

    PrintStream err = NullOutputStream.wrapOrNullStream(errStream);

    JDKLogging.disableLogging();

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
      LocalizableMessage message =
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
      LocalizableMessage message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());
      println(message);
      println();
      println(LocalizableMessage.raw(argParser.getUsage()));

      return ErrorReturnCode.ERROR_USER_DATA.getReturnCode();
    }

    if (argParser.usageOrVersionDisplayed())
    {
      return ErrorReturnCode.SUCCESSFUL_NOP.getReturnCode();
    }

    Properties properties = new Properties();
    BufferedReader reader;
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
      updateProperties(reader, properties);
    }
    catch (IOException ioe)
    {
      println(ERR_JAVAPROPERTIES_WITH_PROPERTIES_FILE.get(propertiesFile));
      return ErrorReturnCode.ERROR_USER_DATA.getReturnCode();
    }

    String destinationFile = argParser.destinationFileArg.getValue();

    BufferedWriter writer;
    try
    {
      File f = new File(destinationFile);
      writer = new BufferedWriter(new FileWriter(f));
      f.setReadable(true, false);
    }
    catch (IOException ioe)
    {
      println(ERR_JAVAPROPERTIES_WITH_DESTINATION_FILE.get(destinationFile));
      return ErrorReturnCode.ERROR_USER_DATA.getReturnCode();
    }

    Enumeration<?> propertyNames = properties.propertyNames();

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
      if (isWindows())
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
      println(getThrowableMsg(
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
      print(INFO_JAVAPROPERTIES_SUCCESSFUL.get(
          argParser.propertiesFileArg.getValue()));
    }
    else
    {
      print(INFO_JAVAPROPERTIES_SUCCESSFUL_NON_DEFAULT.get(
          argParser.destinationFileArg.getValue(),
          argParser.propertiesFileArg.getValue(),
          argParser.destinationFileArg.getDefaultValue()));
    }
    println();


    return ErrorReturnCode.SUCCESSFUL.getReturnCode();
  }

  /**
   * Reads the contents of the provided reader and updates the provided
   * Properties object with it.  This is required because '\' characters in
   * windows paths generates problems.
   * @param reader the buffered reader.
   * @param properties the properties.
   * @throws IOException if there is an error reading the buffered reader.
   */
  public static void updateProperties(
      BufferedReader reader, Properties properties)
  throws IOException
  {
    String line;
    boolean slashInLastLine = false;
    String key = null;
    StringBuilder sbValue = null;
    while ((line = reader.readLine()) != null)
    {
      line = line.trim();
      if (!line.startsWith("#"))
      {
        if (!slashInLastLine)
        {
          key = null;
          sbValue = new StringBuilder();
          int index = line.indexOf('=');
          if (index > 0)
          {
            key = line.substring(0, index);
            if (key.indexOf(' ') != -1)
            {
              key = null;
            }
          }
        }

        // Consider the space: in windows the user might add a path ending
        // with '\'. With this approach we minimize the possibilities of
        // error.
        boolean hasSlash = line.endsWith(" \\");

        if (hasSlash)
        {
          line = line.substring(0, line.length() - 1);
        }

        String lineValue = null;

        if (slashInLastLine)
        {
          lineValue = line;
        }
        else if (key != null)
        {
          int index = line.indexOf('=');
          if ((index != -1) && ((index + 1) < line.length()))
          {
            lineValue = line.substring(index+1);
          }
        }
        if ((lineValue != null) && (lineValue.length() > 0))
        {
          if (sbValue == null)
          {
            sbValue = new StringBuilder();
          }
          sbValue.append(lineValue);
        }
        if (!hasSlash && (key != null) && (sbValue != null))
        {
          properties.put(key, sbValue.toString());
        }
        slashInLastLine = hasSlash;
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isQuiet()
  {
    return argParser.quietArg.isPresent();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isInteractive()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isMenuDrivenMode() {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isScriptFriendly() {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAdvancedMode() {
    return false;
  }


  /** {@inheritDoc} */
  @Override
  public boolean isVerbose() {
    return true;
  }

  private String getUnixContents(boolean overwriteJavaHome,
      boolean overwriteJavaArgs, String defaultJavaHome, String defaultJavaArgs,
      Properties properties)
  {
    StringBuilder buf = new StringBuilder();
    buf.append("#!/bin/sh").append(EOL).append(EOL);

    if (!overwriteJavaHome)
    {
      buf.append("# See if the environment variables for java home are set").append(EOL)
          .append("# in the path and try to figure it out.").append(EOL)
          .append("if test ! -f \"${OPENDJ_JAVA_BIN}\"").append(EOL)
          .append("then").append(EOL)
          .append("  if test ! -d \"${OPENDJ_JAVA_HOME}\"").append(EOL)
          .append("  then").append(EOL)
          .append("    if test ! -f \"${OPENDS_JAVA_BIN}\"").append(EOL)
          .append("    then").append(EOL)
          .append("      if test ! -d \"${OPENDS_JAVA_HOME}\"").append(EOL);
    }

    boolean propertiesAdded = false;

    Enumeration<?> propertyNames = properties.propertyNames();
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
            buf.append(s).append("elif test \"${SCRIPT_NAME}.java-home\" = \"").append(name).append("\"").append(EOL);
          }
          else if (!overwriteJavaHome)
          {
            buf.append("  then").append(EOL)
              .append("    if test \"${SCRIPT_NAME}.java-home\" = \"").append(name).append("\"").append(EOL);
            s = "    ";
          }
          else
          {
            buf.append("if test \"${SCRIPT_NAME}.java-home\" = \"").append(name).append("\"").append(EOL);
            s = "";
          }

          buf
            .append(s).append("then").append(EOL)
            .append(s).append("  TEMP=\"").append(value).append("/bin/java\"").append(EOL)
            .append(s).append("  if test -f \"${TEMP}\"").append(EOL)
            .append(s).append("  then").append(EOL)
            .append(s).append("    OPENDJ_JAVA_BIN=\"").append(value).append("/bin/java\"").append(EOL)
            .append(s).append("    export OPENDJ_JAVA_BIN").append(EOL)
            .append(s).append("  fi").append(EOL);
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
        buf.append(s).append("else").append(EOL)
          .append(s).append("  OPENDJ_JAVA_BIN=\"").append(defaultJavaHome).append("/bin/java\"").append(EOL)
          .append(s).append("  export OPENDJ_JAVA_BIN").append(EOL);
      }
      else
      {
        if (!overwriteJavaHome)
        {
          buf.append("  then").append(EOL)
            .append("    TEMP=\"").append(defaultJavaHome).append("/bin/java\"").append(EOL)
            .append("    if test -f \"${TEMP}\"").append(EOL)
            .append("    then").append(EOL)
            .append("      OPENDJ_JAVA_BIN=\"${TEMP}\"").append(EOL)
            .append("      export OPENDJ_JAVA_BIN").append(EOL)
            .append("    fi").append(EOL);
        }
        else
        {
          buf.append("OPENDJ_JAVA_BIN=\"").append(defaultJavaHome).append("/bin/java\"").append(EOL)
            .append("export OPENDJ_JAVA_BIN").append(EOL);
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
      buf.append(s).append("fi").append(EOL);
    }


    if (!overwriteJavaHome)
    {
      if (!propertiesAdded)
      {
        // No properties added: this is required not to break the script
        buf.append("  then").append(EOL)
          .append("  OPENDJ_JAVA_BIN=\"${OPENDJ_JAVA_BIN}\"").append(EOL);
      }
      buf.append("      else").append(EOL)
        .append("        OPENDJ_JAVA_BIN=\"${OPENDS_JAVA_HOME}/bin/java\"").append(EOL)
        .append("        export OPENDJ_JAVA_BIN").append(EOL)
        .append("      fi").append(EOL)
        .append("    else").append(EOL)
        .append("      OPENDJ_JAVA_BIN=\"${OPENDS_JAVA_BIN}\"").append(EOL)
        .append("      export OPENDJ_JAVA_BIN").append(EOL)
        .append("    fi").append(EOL)
        .append("  else").append(EOL)
        .append("    OPENDJ_JAVA_BIN=\"${OPENDJ_JAVA_HOME}/bin/java\"").append(EOL)
        .append("    export OPENDJ_JAVA_BIN").append(EOL)
        .append("  fi").append(EOL)
        .append("fi").append(EOL)
        .append(EOL);
    }
    else if (defaultJavaHome == null)
    {
      buf.append(EOL)
        .append("if test ! -f \"${OPENDJ_JAVA_BIN}\"").append(EOL)
        .append("then").append(EOL)
        .append("  if test ! -d \"${OPENDJ_JAVA_HOME}\"").append(EOL)
        .append("  then").append(EOL)
        .append("    if test ! -f \"${OPENDS_JAVA_BIN}\"").append(EOL)
        .append("    then").append(EOL)
        .append("      if test ! -d \"${OPENDS_JAVA_HOME}\"").append(EOL)
        .append("      then").append(EOL)
        .append("        if test ! -f \"${JAVA_BIN}\"").append(EOL)
        .append("        then").append(EOL)
        .append("          if test ! -d \"${JAVA_HOME}\"").append(EOL)
        .append("          then").append(EOL)
        .append("            OPENDJ_JAVA_BIN=`which java 2> /dev/null`").append(EOL)
        .append("            if test ${?} -eq 0").append(EOL)
        .append("            then").append(EOL)
        .append("              export OPENDJ_JAVA_BIN").append(EOL)
        .append("            else").append(EOL)
        .append("              echo \"You must specify the path to a valid Java 7.0 ")
        .append("or higher version in the\"").append(EOL)
        .append("              echo \"properties file and then run the ")
        .append("dsjavaproperties  tool. \"").append(EOL)
        .append("              echo \"The procedure to follow is:\"").append(EOL)
        .append("              echo \"You must specify the path to a valid Java 7.0 ")
        .append("or higher version.  The \"").append(EOL)
        .append("              echo \"procedure to follow is:\"").append(EOL)
        .append("              echo \"1. Delete the file ")
        .append("${INSTANCE_ROOT}/lib/set-java-home\"").append(EOL)
        .append("              echo \"2. Set the environment variable ")
        .append("OPENDJ_JAVA_HOME to the root of a valid \"").append(EOL)
        .append("              echo \"Java 7.0 installation.\"").append(EOL)
        .append("              echo \"If you want to have specificjava  settings for")
        .append(" each command line you must\"").append(EOL)
        .append("              echo \"follow the steps 3 and 4\"").append(EOL)
        .append("              echo \"3. Edit the properties file specifying the ")
        .append("java binary and the java arguments\"").append(EOL)
        .append("              echo \"for each command line.  The java properties ")
        .append("file is located in:\"").append(EOL)
        .append("              echo \"${INSTANCE_ROOT}/config/java.properties.\"").append(EOL)
        .append("              echo \"4. Run the command-line ")
        .append("${INSTANCE_ROOT}/bin/dsjavaproperties\"").append(EOL)
        .append("              exit 1").append(EOL)
        .append("            fi").append(EOL)
        .append("          else").append(EOL)
        .append("            OPENDJ_JAVA_BIN=\"${JAVA_HOME}/bin/java\"").append(EOL)
        .append("            export OPENDJ_JAVA_BIN").append(EOL)
        .append("          fi").append(EOL)
        .append("        else").append(EOL)
        .append("          OPENDJ_JAVA_BIN=\"${JAVA_BIN}\"").append(EOL)
        .append("          export OPENDJ_JAVA_BIN").append(EOL)
        .append("        fi").append(EOL)
        .append("      else").append(EOL)
        .append("        OPENDJ_JAVA_BIN=\"${OPENDS_JAVA_HOME}/bin/java\"").append(EOL)
        .append("        export OPENDJ_JAVA_BIN").append(EOL)
        .append("      fi").append(EOL)
        .append("    else").append(EOL)
        .append("      OPENDJ_JAVA_BIN=\"${OPENDS_JAVA_BIN}\"").append(EOL)
        .append("      export OPENDJ_JAVA_BIN").append(EOL)
        .append("    fi").append(EOL)
        .append("  else").append(EOL)
        .append("    OPENDJ_JAVA_BIN=\"${OPENDJ_JAVA_HOME}/bin/java\"").append(EOL)
        .append("    export OPENDJ_JAVA_BIN").append(EOL)
        .append("  fi").append(EOL)
        .append("fi").append(EOL)
        .append(EOL);
    }


    if (!overwriteJavaArgs)
    {
      buf.append(EOL)
        .append("# See if the environment variables for arguments are set.").append(EOL)
        .append("if test -z \"${OPENDJ_JAVA_ARGS}\"").append(EOL)
        .append("then").append(EOL)
        .append("  if test -z \"${OPENDS_JAVA_ARGS}\"").append(EOL);
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
            buf.append(s).append("  elif test \"${SCRIPT_NAME}.java-args\" = \"").append(name).append("\"").append(EOL);
          }
          else if (!overwriteJavaArgs)
          {
            buf.append("  then").append(EOL)
              .append("    if test \"${SCRIPT_NAME}.java-args\" = \"").append(name).append("\"").append(EOL);
          }
          else
          {
            buf.append("  if test \"${SCRIPT_NAME}.java-args\" = \"").append(name).append("\"").append(EOL);
          }
          buf
            .append(s).append("  then").append(EOL)
            .append(s).append("    OPENDJ_JAVA_ARGS=\"").append(value).append("\"").append(EOL)
            .append(s).append("    export OPENDJ_JAVA_ARGS").append(EOL);
          nIfs++;
        }
      }
    }
    if (defaultJavaArgs != null)
    {
      String s = overwriteJavaArgs? "":"  ";
      if (propertiesAdded)
      {
        buf.append(s).append("  else").append(EOL)
          .append(s).append("    OPENDJ_JAVA_ARGS=\"").append(defaultJavaArgs).append("\"").append(EOL)
          .append(s).append("    export OPENDJ_JAVA_ARGS").append(EOL);
      }
      else
      {
        if (!overwriteJavaArgs)
        {
          buf.append("    then").append(EOL)
            .append("      OPENDJ_JAVA_ARGS=\"").append(defaultJavaArgs).append("\"").append(EOL)
            .append("      export OPENDJ_JAVA_ARGS").append(EOL);
        }
        else
        {
          buf.append(EOL)
            .append("  OPENDJ_JAVA_ARGS=\"").append(defaultJavaArgs).append("\"").append(EOL)
            .append("  export OPENDJ_JAVA_ARGS").append(EOL);
        }
      }
      propertiesAdded = true;
    }
    if (nIfs > 0)
    {
      String s = overwriteJavaArgs? "":"    ";
      buf.append(s).append("fi").append(EOL);
    }

    if (!overwriteJavaArgs)
    {
      if (!propertiesAdded)
      {
        // No properties added: this is required not to break the script
        buf
          .append("  then").append(EOL)
          .append("    OPENDJ_JAVA_ARGS=${OPENDJ_JAVA_ARGS}").append(EOL);
      }
      buf
        .append("  else").append(EOL)
        .append("    OPENDJ_JAVA_ARGS=${OPENDS_JAVA_ARGS}").append(EOL)
        .append("    export OPENDJ_JAVA_ARGS").append(EOL)
        .append("  fi").append(EOL)
        .append("fi").append(EOL);
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
    final String LEGACY = "Legacy";

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

    buf.append("goto ").append(javaHomeLabel1).append(EOL).append(EOL);

    buf.append(":").append(CHECK_ENV_JAVA_HOME).append(EOL)
      .append("if \"%OPENDJ_JAVA_BIN%\" == \"\" goto checkEnvJavaHome").append(LEGACY).append(EOL)
      .append("if not exist \"%OPENDJ_JAVA_BIN%\" goto checkEnvJavaHome").append(LEGACY).append(EOL)
      .append("goto ").append(javaArgsLabel1).append(EOL)
      .append(EOL)
      .append(":checkEnvJavaHome").append(LEGACY).append(EOL)
      .append("if \"%OPENDS_JAVA_BIN%\" == \"\" goto checkOpendjJavaHome").append(EOL)
      .append("if not exist \"%OPENDS_JAVA_BIN%\" goto checkOpendjJavaHome").append(EOL)
      .append("goto ").append(javaArgsLabel1).append(EOL)
      .append(EOL)
      .append(":checkOpendjJavaHome").append(EOL);

    if (javaHomeLabel1 == CHECK_ENV_JAVA_HOME)
    {
      buf.append("if \"%OPENDJ_JAVA_HOME%\" == \"\" goto ").append(javaHomeLabel2).append(LEGACY).append(EOL)
        .append("set TEMP_EXE=%OPENDJ_JAVA_HOME%\\bin\\java.exe").append(EOL)
        .append("if not exist \"%TEMP_EXE%\" goto ").append(javaHomeLabel2).append(LEGACY).append(EOL)
        .append("set OPENDJ_JAVA_BIN=%TEMP_EXE%").append(EOL)
        .append("goto ").append(javaArgsLabel1).append(EOL).append(EOL)
        .append(":").append(javaHomeLabel2).append(LEGACY).append(EOL)
        .append("if \"%OPENDS_JAVA_HOME%\" == \"\" goto ")
        .append(javaHomeLabel2).append(EOL)
        .append("set TEMP_EXE=%OPENDS_JAVA_HOME%\\bin\\java.exe").append(EOL)
        .append("if not exist \"%TEMP_EXE%\" goto ").append(javaHomeLabel2).append(EOL)
        .append("set OPENDJ_JAVA_BIN=%TEMP_EXE%").append(EOL)
        .append("goto ").append(javaArgsLabel1).append(EOL)
        .append(EOL);
    }
    else
    {
      buf.append("if \"%OPENDJ_JAVA_HOME%\" == \"\" goto ").append(javaArgsLabel1).append(LEGACY).append(EOL)
        .append("set TEMP_EXE=%OPENDJ_JAVA_HOME%\\bin\\java.exe").append(EOL)
        .append("if not exist \"%TEMP_EXE%\" goto ").append(javaArgsLabel1).append(LEGACY).append(EOL)
        .append("set OPENDJ_JAVA_BIN=%TEMP_EXE%").append(EOL)
        .append("goto ").append(javaArgsLabel1).append(EOL).append(EOL)
        .append(":").append(javaArgsLabel1).append(LEGACY).append(EOL)
        .append("if \"%OPENDS_JAVA_HOME%\" == \"\" goto ")
        .append(javaArgsLabel1).append(EOL)
        .append("set TEMP_EXE=%OPENDS_JAVA_HOME%\\bin\\java.exe").append(EOL)
        .append("if not exist \"%TEMP_EXE%\" goto ").append(javaArgsLabel1).append(EOL)
        .append("set OPENDJ_JAVA_BIN=%TEMP_EXE%").append(EOL)
        .append("goto ").append(javaArgsLabel1).append(EOL).append(EOL);
    }

    if (defaultJavaHome != null)
    {
      if (javaHomeLabel1 == CHECK_ENV_JAVA_HOME)
      {
        buf.append(":").append(CHECK_DEFAULT_JAVA_HOME).append(EOL)
          .append("set TEMP_EXE=").append(defaultJavaHome).append("\\bin\\java.exe").append(EOL)
          .append("if not exist \"%TEMP_EXE%\" goto ").append(javaArgsLabel1).append(EOL)
          .append("set OPENDJ_JAVA_BIN=%TEMP_EXE%").append(EOL)
          .append("goto ").append(javaArgsLabel1).append(EOL).append(EOL);
      }
      else
      {
        buf.append(":").append(CHECK_DEFAULT_JAVA_HOME).append(EOL)
          .append("set TEMP_EXE=").append(defaultJavaHome).append("\\bin\\java.exe").append(EOL)
          .append("if not exist \"%TEMP_EXE%\" goto ").append(CHECK_ENV_JAVA_HOME).append(EOL)
          .append("set OPENDJ_JAVA_BIN=%TEMP_EXE%").append(EOL)
          .append("goto ").append(javaArgsLabel1).append(EOL).append(EOL);
      }
    }

    buf.append(":").append(CHECK_JAVA_HOME).append(EOL);
    Enumeration<?> propertyNames = properties.propertyNames();
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
        buf.append("if \"%SCRIPT_NAME%.java-home\" == \"").append(name)
          .append("\" goto check").append(scriptName).append("JavaHome").append(EOL);
      }
    }
    if (defaultJavaHome != null)
    {
      buf.append("goto ").append(CHECK_DEFAULT_JAVA_HOME).append(EOL).append(EOL);
    }
    else if (javaHomeLabel1 != CHECK_ENV_JAVA_HOME)
    {
      buf.append("goto ").append(CHECK_ENV_JAVA_HOME).append(EOL).append(EOL);
    }
    else
    {
      buf.append("goto ").append(javaArgsLabel1).append(EOL).append(EOL);
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
        buf.append(":check").append(scriptName).append("JavaHome").append(EOL)
          .append("set TEMP_EXE=").append(value).append("\\bin\\java.exe").append(EOL);
        if (defaultJavaHome != null)
        {
          buf.append("if not exist \"%TEMP_EXE%\" goto ").append(CHECK_DEFAULT_JAVA_HOME).append(EOL);
        }
        else if (javaHomeLabel1 != CHECK_ENV_JAVA_HOME)
        {
          buf.append("if not exist \"%TEMP_EXE%\" goto ").append(CHECK_ENV_JAVA_HOME).append(EOL);
        }
        buf.append("set OPENDJ_JAVA_BIN=%TEMP_EXE%").append(EOL)
          .append("goto ").append(javaArgsLabel1).append(EOL).append(EOL);
      }
    }

    buf.append(":").append(CHECK_ENV_JAVA_ARGS).append(EOL);
    if (javaArgsLabel1 == CHECK_ENV_JAVA_ARGS)
    {
      buf.append("if \"%OPENDJ_JAVA_ARGS%\" == \"\" goto ").append(javaArgsLabel2).append(LEGACY).append(EOL)
        .append("goto end").append(EOL).append(EOL)
        .append(":").append(javaArgsLabel2).append(LEGACY).append(EOL)
        .append("if \"%OPENDS_JAVA_ARGS%\" == \"\" goto ").append(javaArgsLabel2).append(EOL)
        .append("set OPENDJ_JAVA_ARGS=%OPENDS_JAVA_ARGS%").append(EOL)
        .append("goto end").append(EOL).append(EOL);
    }
    else
    {
      buf.append("goto end").append(EOL).append(EOL);
    }

    if (defaultJavaArgs != null)
    {
      buf.append(":").append(CHECK_DEFAULT_JAVA_ARGS).append(EOL)
        .append("set OPENDJ_JAVA_ARGS=").append(defaultJavaArgs).append(EOL)
        .append("goto end").append(EOL).append(EOL);
    }

    buf.append(":").append(CHECK_JAVA_ARGS).append(EOL);
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
        buf.append("if \"%SCRIPT_NAME%.java-args\" == \"").append(name)
          .append("\" goto check").append(scriptName).append("JavaArgs").append(EOL);
      }
    }
    if (defaultJavaArgs != null)
    {
      buf.append("goto ").append(CHECK_DEFAULT_JAVA_ARGS).append(EOL).append(EOL);
    }
    else if (javaArgsLabel1 != CHECK_ENV_JAVA_ARGS)
    {
      buf.append("goto ").append(CHECK_ENV_JAVA_ARGS).append(EOL).append(EOL);
    }
    else
    {
      buf.append("goto end").append(EOL).append(EOL);
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
        buf.append(":check").append(scriptName).append("JavaArgs").append(EOL)
          .append("set OPENDJ_JAVA_ARGS=").append(value).append(EOL)
          .append("goto end").append(EOL).append(EOL);
      }
    }

    buf.append(":end").append(EOL);

    return buf.toString();
  }
}
