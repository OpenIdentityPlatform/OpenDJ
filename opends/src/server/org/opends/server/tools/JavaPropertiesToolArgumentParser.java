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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 */
package org.opends.server.tools;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.ToolConstants.*;

import java.io.File;
import java.util.LinkedHashSet;

import org.opends.messages.Message;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.util.Utils;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.StringArgument;

/**
 * Class used to parse the arguments of the java properties tool command-line.
 */
public class JavaPropertiesToolArgumentParser extends ArgumentParser
{
  // Usage argument
  BooleanArgument   showUsageArg;
  // Quiet argument
  BooleanArgument   quietArg;
  // The file containing the properties
  StringArgument propertiesFileArg;
  // The file that is generated
  StringArgument destinationFileArg;

  /**
   * The default constructor for this class.
   * @param mainClassName the class name of the main class for the command-line
   * that is being used.
   */
  public JavaPropertiesToolArgumentParser(String mainClassName)
  {
    super(mainClassName,
        INFO_JAVAPROPERTIES_TOOL_DESCRIPTION.get(getDefaultPropertiesValue()),
        false);
  }

  /**
   * Initializes the arguments without parsing them.
   * @throws ArgumentException if there was an error creating or adding the
   * arguments.  If this occurs is likely to be a bug.
   */
  public void initializeArguments() throws ArgumentException
  {
    quietArg = new BooleanArgument(
        "quiet", OPTION_SHORT_QUIET,
        OPTION_LONG_QUIET,
        INFO_JAVAPROPERTIES_DESCRIPTION_SILENT.get());
    quietArg.setPropertyName(OPTION_LONG_QUIET);
    addArgument(quietArg);

    propertiesFileArg = new StringArgument("propertiesFile",
        'p', "propertiesFile", false,
        false, true, INFO_PATH_PLACEHOLDER.get(), getDefaultPropertiesValue(),
        "propertiesFile",
        INFO_JAVAPROPERTIES_DESCRIPTION_PROPERTIES_FILE.get(
            getDefaultPropertiesValue()));
    propertiesFileArg.setHidden(true);
    addArgument(propertiesFileArg);

    destinationFileArg = new StringArgument("destinationFile",
        'd', "destinationFile", false,
        false, true, INFO_PATH_PLACEHOLDER.get(), getDefaultDestinationValue(),
        "destinationFile",
        INFO_JAVAPROPERTIES_DESCRIPTION_DESTINATION_FILE.get(
            getDefaultDestinationValue()));
    destinationFileArg.setHidden(true);
    addArgument(destinationFileArg);

    showUsageArg = new BooleanArgument("help", OPTION_SHORT_HELP,
        OPTION_LONG_HELP,
        INFO_JAVAPROPERTIES_DESCRIPTION_HELP.get());
    addArgument(showUsageArg);
    setUsageArgument(showUsageArg);
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public void parseArguments(String[] args) throws ArgumentException
  {
    LinkedHashSet<Message> errorMessages = new LinkedHashSet<Message>();
    try
    {
      super.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      errorMessages.add(ae.getMessageObject());
    }

    if (!isUsageArgumentPresent() && !isVersionArgumentPresent())
    {
      String value = propertiesFileArg.getValue();
      if (value != null)
      {
        File f = new File(value);
        if (!f.exists() || !f.isFile() || !f.canRead())
        {
          errorMessages.add(ERR_JAVAPROPERTIES_WITH_PROPERTIES_FILE.get(value));
        }
      }
      value = destinationFileArg.getValue();
      if (value != null)
      {
        File f = new File(value);
        if (f.isDirectory() || !Utils.canWrite(value))
        {
          errorMessages.add(
              ERR_JAVAPROPERTIES_WITH_DESTINATION_FILE.get(value));
        }
      }
      if (errorMessages.size() > 0)
      {
        Message message = ERR_CANNOT_INITIALIZE_ARGS.get(
            Utils.getMessageFromCollection(errorMessages,
                Constants.LINE_SEPARATOR));
        throw new ArgumentException(message);
      }
    }
  }

  /**
   * Returns the default destination file by inspecting the class loader.
   * @return the default destination file retrieved by inspecting the class
   * loader.
   */
  private String getDefaultDestinationValue()
  {
    String value;
    // Use this instead of Installation.getLocal() because making that call
    // starts a new JVM and the command-line becomes less responsive.
    String root = Utils.getInstallPathFromClasspath();
    if (root != null)
    {
      String libDir = Utils.getPath(root, Installation.LIBRARIES_PATH_RELATIVE);
      if (Utils.isWindows())
      {
        value = Utils.getPath(libDir,
            Installation.SET_JAVA_PROPERTIES_FILE_WINDOWS);
      }
      else
      {
        value = Utils.getPath(libDir,
            Installation.SET_JAVA_PROPERTIES_FILE_UNIX);
      }
    }
    else
    {
      // This can happen when we are not launched using the command-line (for
      // instance from the WebInstaller).
      if (Utils.isWindows())
      {
        value = Utils.getPath(Installation.LIBRARIES_PATH_RELATIVE,
            Installation.SET_JAVA_PROPERTIES_FILE_WINDOWS);
      }
      else
      {
        value = Utils.getPath(Installation.LIBRARIES_PATH_RELATIVE,
            Installation.SET_JAVA_PROPERTIES_FILE_UNIX);
      }
    }
    return value;
  }

  /**
   * Returns the default java properties file by inspecting the class loader.
   * @return the default java properties file retrieved by inspecting the class
   * loader.
   */
  private static String getDefaultPropertiesValue()
  {
    String defaultPropertiesValue;
    // Use this instead of Installation.getLocal() because making that call
    // starts a new JVM and the command-line becomes less responsive.
    String root = Utils.getInstallPathFromClasspath();
    if (root != null)
    {
      String configDir = Utils.getPath(root, Installation.CONFIG_PATH_RELATIVE);
      defaultPropertiesValue = Utils.getPath(configDir,
          Installation.DEFAULT_JAVA_PROPERTIES_FILE);
    }
    else
    {
      // This can happen when we are not launched using the command-line (for
      // instance from the WebInstaller).
      defaultPropertiesValue = Installation.DEFAULT_JAVA_PROPERTIES_FILE;
    }
    return defaultPropertiesValue;
  }
}
