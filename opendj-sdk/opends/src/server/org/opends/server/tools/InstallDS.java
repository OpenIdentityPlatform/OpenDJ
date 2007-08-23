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
package org.opends.server.tools;
import org.opends.messages.Message;



import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.types.DN;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.util.SetupUtils;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.PasswordReader;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.tools.ToolConstants.*;



/**
 * This class provides a very simple mechanism for installing the OpenDS
 * Directory Service.  It performs the following tasks:
 * <UL>
 *   <LI>Ask the user what base DN should be used for the data</LI>
 *   <LI>Ask the user whether to create the base entry, or to import LDIF</LI>
 *   <LI>Ask the user for the LDAP port and make sure it's available</LI>
 *   <LI>Ask the user for the default root DN and password</LI>
 *   <LI>Ask the user if they want to start the server when done installing</LI>
 * </UL>
 */
public class InstallDS
{
  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME = "org.opends.server.tools.InstallDS";



  /**
   * Indicates whether we think we're running on a Windows system.
   */
  private static final boolean isWindows = SetupUtils.isWindows();



  /**
   * The version string for the server.
   */
  private static String versionString;



  /**
   * The name of the program used to launch this installation process.
   */
  private static String programName;



  /**
   * The value that indicates that only the base entry should be created.
   */
  private static final int POPULATE_TYPE_BASE_ONLY = 1;



  /**
   * The value that indicates that the database should be left empty.
   */
  private static final int POPULATE_TYPE_LEAVE_EMPTY = 2;



  /**
   * The value that indicates that data should be imported from an LDIF file.
   */
  private static final int POPULATE_TYPE_IMPORT_FROM_LDIF = 3;



  /**
   * The value that indicates that the database should be populated with sample
   * data.
   */
  private static final int POPULATE_TYPE_GENERATE_SAMPLE_DATA = 4;



  /**
   * Invokes the <CODE>installMain</CODE> method with the provided arguments.
   *
   * @param  args  The command-line arguments to use for this program.
   */
  public static void main(String[] args)
  {
    int exitCode = installMain(args);
    if (exitCode != 0)
    {
      System.exit(filterExitCode(exitCode));
    }
  }



  /**
   * Prompts the user for the necessary information, installs the OpenDS
   * software in the appropriate location, and gives it the desired
   * configuration.
   *
   * @param  args  The command-line arguments to use for this program.
   *
   * @return  A value of zero if the installation process was successful, or a
   *          nonzero value if a problem occurred.
   */
  public static int installMain(String[] args)
  {
    // Construct the product version string and the setup filename.
    versionString = DirectoryServer.getVersionString();

    if (isWindows)
    {
      programName = "setup.bat";
    }
    else
    {
      programName = "setup";
    }


    // Create and initialize the argument parser for this program.
    Message toolDescription = INFO_INSTALLDS_TOOL_DESCRIPTION.get();
    ArgumentParser argParser = new ArgumentParser(CLASS_NAME, toolDescription,
                                                  false);
    BooleanArgument   addBaseEntry;
    BooleanArgument   cliMode;
    BooleanArgument   testOnly;
    BooleanArgument   showUsage;
    BooleanArgument   quietInstall;
    BooleanArgument   skipPortCheck;
    BooleanArgument   enableWindowsService;
    FileBasedArgument rootPWFile;
    IntegerArgument   ldapPort;
    IntegerArgument   jmxPort;
    IntegerArgument   sampleData;
    StringArgument    baseDN;
    StringArgument    configClass;
    StringArgument    configFile;
    StringArgument    importLDIF;
    StringArgument    progName;
    StringArgument    rootDN;
    StringArgument    rootPWString;

    try
    {
      testOnly = new BooleanArgument(
              "test", 't', "testOnly",
              INFO_INSTALLDS_DESCRIPTION_TESTONLY.get());
      testOnly.setHidden(true);
      argParser.addArgument(testOnly);

      progName = new StringArgument(
              "progname", 'P', "programName", false,
              false, true, "{programName}", programName,
              null, INFO_INSTALLDS_DESCRIPTION_PROGNAME.get());
      progName.setHidden(true);
      argParser.addArgument(progName);

      configFile = new StringArgument(
              "configfile", 'c', "configFile", false,
              false, true, "{configFile}", null, null,
              INFO_DESCRIPTION_CONFIG_FILE.get());
      configFile.setHidden(true);
      argParser.addArgument(configFile);

      configClass = new StringArgument(
              "configclass", OPTION_SHORT_CONFIG_CLASS,
              OPTION_LONG_CONFIG_CLASS, false,
              false, true, OPTION_VALUE_CONFIG_CLASS,
              ConfigFileHandler.class.getName(), null,
              INFO_DESCRIPTION_CONFIG_CLASS.get());
      configClass.setHidden(true);
      argParser.addArgument(configClass);

      // NOTE:  This argument isn't actually used for anything, but it provides
      // consistency with the setup script, which does take a --cli option.
      cliMode = new BooleanArgument(
              "cli", null, OPTION_LONG_CLI,
              INFO_INSTALLDS_DESCRIPTION_CLI.get());
      argParser.addArgument(cliMode);

      quietInstall = new BooleanArgument(
              "quiet", OPTION_SHORT_QUIET,
              OPTION_LONG_QUIET,
              INFO_INSTALLDS_DESCRIPTION_SILENT.get());
      argParser.addArgument(quietInstall);

      baseDN = new StringArgument(
              "basedn", OPTION_SHORT_BASEDN,
              OPTION_LONG_BASEDN, false, true, true,
              OPTION_VALUE_BASEDN,
              "dc=example,dc=com", null,
              INFO_INSTALLDS_DESCRIPTION_BASEDN.get());
      argParser.addArgument(baseDN);

      addBaseEntry = new BooleanArgument(
              "addbase", 'a', "addBaseEntry",
              INFO_INSTALLDS_DESCRIPTION_ADDBASE.get());
      argParser.addArgument(addBaseEntry);

      importLDIF = new StringArgument(
              "importldif", OPTION_SHORT_LDIF_FILE,
              OPTION_LONG_LDIF_FILE, false,
              true, true, OPTION_VALUE_LDIF_FILE,
              null, null,
              INFO_INSTALLDS_DESCRIPTION_IMPORTLDIF.get());
      argParser.addArgument(importLDIF);

      sampleData = new IntegerArgument(
              "sampledata", 'd', "sampleData", false,
              false, true, "{numEntries}", 0, null,
              true, 0, false, 0,
              INFO_INSTALLDS_DESCRIPTION_SAMPLE_DATA.get());
      argParser.addArgument(sampleData);

      ldapPort = new IntegerArgument(
              "ldapport", OPTION_SHORT_PORT,
              "ldapPort", false, false,
              true, OPTION_VALUE_PORT, 389,
              null, true, 1, true, 65535,
              INFO_INSTALLDS_DESCRIPTION_LDAPPORT.get());
      argParser.addArgument(ldapPort);

      jmxPort = new IntegerArgument(
              "jmxport", 'x', "jmxPort", false, false,
              true, "{jmxPort}",
              SetupUtils.getDefaultJMXPort(), null, true,
              1, true, 65535,
              INFO_INSTALLDS_DESCRIPTION_JMXPORT.get());
      argParser.addArgument(jmxPort);

      skipPortCheck = new BooleanArgument(
              "skipportcheck", 'S', "skipPortCheck",
              INFO_INSTALLDS_DESCRIPTION_SKIPPORT.get());
      argParser.addArgument(skipPortCheck);

      rootDN = new StringArgument(
              "rootdn",OPTION_SHORT_ROOT_USER_DN,
              OPTION_LONG_ROOT_USER_DN, false, true,
              true, OPTION_VALUE_ROOT_USER_DN,
              "cn=Directory Manager",
              null, INFO_INSTALLDS_DESCRIPTION_ROOTDN.get());
      argParser.addArgument(rootDN);

      rootPWString = new StringArgument(
              "rootpwstring", OPTION_SHORT_BINDPWD,
              "rootUserPassword",
              false, false, true,
              "{password}", null,
              null,
              INFO_INSTALLDS_DESCRIPTION_ROOTPW.get());
      argParser.addArgument(rootPWString);

      rootPWFile = new FileBasedArgument(
              "rootpwfile",
              OPTION_SHORT_BINDPWD_FILE,
              "rootUserPasswordFile", false, false,
              OPTION_VALUE_BINDPWD_FILE,
              null, null, INFO_INSTALLDS_DESCRIPTION_ROOTPWFILE.get());
      argParser.addArgument(rootPWFile);

      enableWindowsService = new BooleanArgument(
              "enablewindowsservice", 'e',
              "enableWindowsService",
              INFO_INSTALLDS_DESCRIPTION_ENABLE_WINDOWS_SERVICE.get());
      if (SetupUtils.isWindows())
      {
        argParser.addArgument(enableWindowsService);
      }

      showUsage = new BooleanArgument("help", OPTION_SHORT_HELP,
                                      OPTION_LONG_HELP,
                                      INFO_INSTALLDS_DESCRIPTION_HELP.get());
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage);
    }
    catch (ArgumentException ae)
    {
      System.err.println(wrapText(ae.getMessage(), MAX_LINE_WIDTH));
      System.err.println(argParser.getUsage());
      return 1;
    }


    // Parse all of the configuration arguments.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      System.err.println(wrapText(ae.getMessage(), MAX_LINE_WIDTH));
      System.err.println(argParser.getUsage());
      return 1;
    }


    // If either the showUsage or testOnly or version arguments were provided,
    // then we're done.
    if (argParser.usageOrVersionDisplayed() || testOnly.isPresent())
    {
      return 0;
    }

    try
    {
      Set<Integer> ports = new HashSet<Integer>();
      if (ldapPort.isPresent())
      {
        ports.add(ldapPort.getIntValue());
      }
      if (jmxPort.isPresent())
      {
        if (ports.contains(jmxPort.getIntValue()))
        {
          Message message = ERR_CONFIGDS_PORT_ALREADY_SPECIFIED.get(
                  String.valueOf(jmxPort.getIntValue()));
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
          System.err.println(argParser.getUsage());
          return 1;
        }
        else
        {
          ports.add(jmxPort.getIntValue());
        }
      }
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    // Make sure that the user didn't provide conflicting arguments.
    if (addBaseEntry.isPresent())
    {
      if (importLDIF.isPresent())
      {
        Message message = ERR_TOOL_CONFLICTING_ARGS.get(
                addBaseEntry.getLongIdentifier(),
                importLDIF.getLongIdentifier());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      else if (sampleData.isPresent())
      {
        Message message = ERR_TOOL_CONFLICTING_ARGS.get(
                addBaseEntry.getLongIdentifier(),
                sampleData.getLongIdentifier());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
    }
    else if (importLDIF.isPresent() && sampleData.isPresent())
    {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
              importLDIF.getLongIdentifier(),
              sampleData.getLongIdentifier());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // Make sure the path to the configuration file was given.
    String configFileName;
    if (configFile.isPresent())
    {
      configFileName = configFile.getValue();
    }
    else
    {
      Message message = ERR_INSTALLDS_NO_CONFIG_FILE.get(
              configFile.getLongIdentifier());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // Get the configuration handler class name.
    String configClassName = configClass.getValue();


    // If this isn't a quiet install, then print the version string.
    if (! quietInstall.isPresent())
    {
      System.out.println(versionString);
      System.out.println();

      Message message = INFO_INSTALLDS_INITIALIZING.get();
      System.out.println(wrapText(message, MAX_LINE_WIDTH));
    }


    // Perform a base-level initialization that will be required to get
    // minimal functionality like DN parsing to work.
    DirectoryServer directoryServer = DirectoryServer.getInstance();
    directoryServer.bootstrapClient();

    try
    {
      directoryServer.initializeJMX();
    }
    catch (Exception e)
    {
      Message message = ERR_INSTALLDS_CANNOT_INITIALIZE_JMX.get(
              String.valueOf(configFile.getValue()),
              e.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    try
    {
      directoryServer.initializeConfiguration(configClass.getValue(),
                                              configFile.getValue());
    }
    catch (Exception e)
    {
      Message message = ERR_INSTALLDS_CANNOT_INITIALIZE_CONFIG.get(
              String.valueOf(configFile.getValue()),
                                  e.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    try
    {
      directoryServer.initializeSchema();
    }
    catch (Exception e)
    {
      Message message = ERR_INSTALLDS_CANNOT_INITIALIZE_SCHEMA.get(
              String.valueOf(configFile.getValue()),
                                  e.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // Determine the LDAP port number.
    int ldapPortNumber;
    if (quietInstall.isPresent() || ldapPort.isPresent())
    {
      try
      {
        ldapPortNumber = ldapPort.getIntValue();

        if (! skipPortCheck.isPresent())
        {
          // Check if the port can be used.
          if (!SetupUtils.canUseAsPort(ldapPortNumber))
          {
            Message message;
            if (SetupUtils.isPriviledgedPort(ldapPortNumber))
            {

              message = ERR_INSTALLDS_CANNOT_BIND_TO_PRIVILEGED_PORT.get(
                      ldapPortNumber);
              System.err.println(wrapText(message, MAX_LINE_WIDTH));
            }
            else
            {

              message = ERR_INSTALLDS_CANNOT_BIND_TO_PORT.get(ldapPortNumber);
              System.err.println(wrapText(message, MAX_LINE_WIDTH));
            }
            return 1;
          }
        }
      }
      catch (ArgumentException ae)
      {
        System.err.println(wrapText(ae.getMessage(), MAX_LINE_WIDTH));
        return 1;
      }
    }
    else
    {
      while (true)
      {
        Message message = INFO_INSTALLDS_PROMPT_LDAPPORT.get();
        ldapPortNumber = promptForInteger(message, 389, 1, 65535);

        if (skipPortCheck.isPresent())
        {
            break;
        }
        else
        {
          // Check if the port can be used.
          if (SetupUtils.canUseAsPort(ldapPortNumber))
          {
              break;
          }
          else
          {
            if (SetupUtils.isPriviledgedPort(ldapPortNumber))
            {
              message = ERR_INSTALLDS_CANNOT_BIND_TO_PRIVILEGED_PORT.get(
                      ldapPortNumber);
              System.err.println(wrapText(message, MAX_LINE_WIDTH));
            }
            else
            {
              message = ERR_INSTALLDS_CANNOT_BIND_TO_PORT.get(ldapPortNumber);
              System.err.println(wrapText(message, MAX_LINE_WIDTH));
            }
          }
        }
      }
    }

//  Determine the JMX port number.
    int jmxPortNumber;
    if (quietInstall.isPresent() || jmxPort.isPresent())
    {
      try
      {
        jmxPortNumber = jmxPort.getIntValue();

        if (! skipPortCheck.isPresent())
        {
          // Check if the port can be used.
          if (!SetupUtils.canUseAsPort(jmxPortNumber))
          {
            Message message;
            if (SetupUtils.isPriviledgedPort(jmxPortNumber))
            {
              message = ERR_INSTALLDS_CANNOT_BIND_TO_PRIVILEGED_PORT.get(
                      jmxPortNumber);
              System.err.println(wrapText(message, MAX_LINE_WIDTH));
            }
            else
            {
              message = ERR_INSTALLDS_CANNOT_BIND_TO_PORT.get(jmxPortNumber);
              System.err.println(wrapText(message, MAX_LINE_WIDTH));
            }
            return 1;
          }
        }
      }
      catch (ArgumentException ae)
      {
        System.err.println(wrapText(ae.getMessage(), MAX_LINE_WIDTH));
        return 1;
      }
    }
    else
    {
      /* Do not ask for the JMX port if the user did not provide it.*/
      jmxPortNumber = -1;
      /*
      while (true)
      {
        Message message = INFO_INSTALLDS_PROMPT_JMXPORT.get();
        jmxPortNumber = promptForInteger(message,
            SetupUtils.getDefaultJMXPort(), 1, 65535);

        if (skipPortCheck.isPresent())
        {
            break;
        }
        else
        {
          // Check if the port can be used.
          if (SetupUtils.canUseAsPort(jmxPortNumber))
          {
              break;
          }
          else
          {
            if (SetupUtils.isPriviledgedPort(jmxPortNumber))
            {

              message = ERR_INSTALLDS_CANNOT_BIND_TO_PRIVILEGED_PORT.get(
                        jmxPortNumber);
              System.err.println(wrapText(message, MAX_LINE_WIDTH));
            }
            else
            {
              message = ERR_INSTALLDS_CANNOT_BIND_TO_PORT.get(jmxPortNumber);
              System.err.println(wrapText(message, MAX_LINE_WIDTH));
            }
          }
        }
      }
      */
    }


    // Determine the initial root user DN.
    LinkedList<DN> rootDNs;
    if (rootDN.isPresent())
    {
      rootDNs = new LinkedList<DN>();
      for (String s : rootDN.getValues())
      {
        try
        {
          rootDNs.add(DN.decode(s));
        }
        catch (Exception e)
        {
          Message message = ERR_INSTALLDS_CANNOT_PARSE_DN.get(
                  s, e.getMessage());
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }
      }
    }
    else if (quietInstall.isPresent())
    {
      rootDNs = new LinkedList<DN>();
      try
      {
        rootDNs.add(DN.decode(rootDN.getDefaultValue()));
      }
      catch (Exception e)
      {
        Message message = ERR_INSTALLDS_CANNOT_PARSE_DN.get(
                rootDN.getDefaultValue(),
                                    e.getMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
    }
    else
    {
      Message message = INFO_INSTALLDS_PROMPT_ROOT_DN.get();
      rootDNs = new LinkedList<DN>();
      rootDNs.add(promptForDN(message, rootDN.getDefaultValue()));
    }


    // Determine the initial root user password.
    String rootPassword;
    if (rootPWString.isPresent())
    {
      rootPassword = rootPWString.getValue();

      if (rootPWFile.isPresent())
      {
        Message message = ERR_INSTALLDS_TWO_CONFLICTING_ARGUMENTS.get(
                rootPWString.getLongIdentifier(),
                rootPWFile.getLongIdentifier());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
    }
    else if (rootPWFile.isPresent())
    {
      rootPassword = rootPWFile.getValue();
    }
    else if (quietInstall.isPresent())
    {
      Message message = ERR_INSTALLDS_NO_ROOT_PASSWORD.get(
              rootPWString.getLongIdentifier(),
              rootPWFile.getLongIdentifier());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }
    else
    {
      Message initialPrompt = INFO_INSTALLDS_PROMPT_ROOT_PASSWORD.get();

      Message confirmPrompt = INFO_INSTALLDS_PROMPT_CONFIRM_ROOT_PASSWORD.get();

      rootPassword =
           new String(promptForPassword(initialPrompt, confirmPrompt));
    }


    // Determine the directory base DN.
    LinkedList<DN> baseDNs;
    if (baseDN.isPresent())
    {
      baseDNs = new LinkedList<DN>();
      for (String s : baseDN.getValues())
      {
        try
        {
          baseDNs.add(DN.decode(s));
        }
        catch (Exception e)
        {

          Message message = ERR_INSTALLDS_CANNOT_PARSE_DN.get(
                  s, e.getMessage());
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }
      }
    }
    else if (quietInstall.isPresent())
    {
      try
      {
        baseDNs = new LinkedList<DN>();
        baseDNs.add(DN.decode(baseDN.getDefaultValue()));
      }
      catch (Exception e)
      {
        Message message =
                ERR_INSTALLDS_CANNOT_PARSE_DN.get(baseDN.getDefaultValue(),
                                    e.getMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
    }
    else
    {
      Message message = INFO_INSTALLDS_PROMPT_BASEDN.get();

      baseDNs = new LinkedList<DN>();
      baseDNs.add(promptForDN(message, baseDN.getDefaultValue()));
    }


    // Determine how to populate the database.
    int                populateType = POPULATE_TYPE_LEAVE_EMPTY;
    int                numUsers     = -1;
    LinkedList<String> ldifFiles    = null;
    if (addBaseEntry.isPresent())
    {
      populateType = POPULATE_TYPE_BASE_ONLY;
    }
    else if (importLDIF.isPresent())
    {
      ldifFiles    = importLDIF.getValues();
      populateType = POPULATE_TYPE_IMPORT_FROM_LDIF;
    }
    else if (sampleData.isPresent())
    {
      try
      {
        numUsers     = sampleData.getIntValue();
        populateType = POPULATE_TYPE_GENERATE_SAMPLE_DATA;
      }
      catch (Exception e)
      {
        // This should never happen.
        e.printStackTrace();
        return 1;
      }
    }
    else if (quietInstall.isPresent())
    {
      populateType = POPULATE_TYPE_LEAVE_EMPTY;
    }
    else
    {
      System.out.println(wrapText(
              INFO_INSTALLDS_HEADER_POPULATE_TYPE.get(),
              MAX_LINE_WIDTH));

      System.out.println(wrapText("1.  " +
              INFO_INSTALLDS_POPULATE_OPTION_BASE_ONLY.get(),
              MAX_LINE_WIDTH));

      System.out.println(wrapText("2.  " +
              INFO_INSTALLDS_POPULATE_OPTION_LEAVE_EMPTY.get(),
              MAX_LINE_WIDTH));

      System.out.println(wrapText("3.  " +
              INFO_INSTALLDS_POPULATE_OPTION_IMPORT_LDIF.get(),
              MAX_LINE_WIDTH));

      System.out.println(wrapText("4.  " +
              INFO_INSTALLDS_POPULATE_OPTION_GENERATE_SAMPLE.get(),
              MAX_LINE_WIDTH));

      populateType = promptForInteger(
              INFO_INSTALLDS_PROMPT_POPULATE_CHOICE.get(), 1, 1, 4);
      System.out.println();

      if (populateType == POPULATE_TYPE_IMPORT_FROM_LDIF)
      {
        ldifFiles = new LinkedList<String>();
        while (true)
        {
          Message message = INFO_INSTALLDS_PROMPT_IMPORT_FILE.get();
          String path    = promptForString(message, "");
          if (new File(path).exists())
          {
            ldifFiles.add(path);
            System.out.println();
            break;
          }
          else
          {

            message = ERR_INSTALLDS_NO_SUCH_LDIF_FILE.get(path);
            System.err.println(wrapText(message, MAX_LINE_WIDTH));
            System.err.println();
          }
        }
      }
      else if (populateType == POPULATE_TYPE_GENERATE_SAMPLE_DATA)
      {
        Message message = INFO_INSTALLDS_PROMPT_NUM_ENTRIES.get();
        numUsers = promptForInteger(message, 2000, 0, Integer.MAX_VALUE);
        System.out.println();
      }
    }

    boolean enableService = false;
    // If we are in Windows ask if the server must run as a windows service.
    if (SetupUtils.isWindows())
    {
      if (quietInstall.isPresent())
      {
        enableService = enableWindowsService.isPresent();
      }
      else if (enableWindowsService.isPresent())
      {
        enableService = true;
      }
      else
      {
        Message message = INFO_INSTALLDS_PROMPT_ENABLE_SERVICE.get();
        enableService = promptForBoolean(message, Boolean.TRUE);
      }
    }

    // At this point, we should be able to invoke the ConfigureDS utility to
    // apply the requested configuration.
    ArrayList<String> argList = new ArrayList<String>();
    argList.add("-C");
    argList.add(configClassName);
    argList.add("-c");
    argList.add(configFileName);
    argList.add("-p");
    argList.add(String.valueOf(ldapPortNumber));
    if (jmxPortNumber != -1)
    {
      argList.add("-x");
      argList.add(String.valueOf(jmxPortNumber));
    }

    for (DN dn : baseDNs)
    {
      argList.add("-b");
      argList.add(dn.toString());
    }

    for (DN dn : rootDNs)
    {
      argList.add("-D");
      argList.add(dn.toString());
    }

    argList.add("-w");
    argList.add(rootPassword);

    String[] configureDSArguments = new String[argList.size()];
    argList.toArray(configureDSArguments);

    if (! quietInstall.isPresent())
    {
      System.out.println();

      Message message = INFO_INSTALLDS_STATUS_CONFIGURING_DS.get();
      System.out.println(wrapText(message, MAX_LINE_WIDTH));
    }

    int returnValue = ConfigureDS.configMain(configureDSArguments);
    if (returnValue != 0)
    {
      return returnValue;
    }


    // If we need to create a base LDIF file or a template file, then do so now.
    if (populateType == POPULATE_TYPE_BASE_ONLY)
    {
      // Create a temporary LDIF file that will hold the entry to add.
      if (! quietInstall.isPresent())
      {
        Message message = INFO_INSTALLDS_STATUS_CREATING_BASE_LDIF.get();
        System.out.println(wrapText(message, MAX_LINE_WIDTH));
      }

      try
      {
        File   ldifFile     = File.createTempFile("opends-base-entry", ".ldif");
        String ldifFilePath = ldifFile.getAbsolutePath();
        ldifFile.deleteOnExit();

        LDIFExportConfig exportConfig =
             new LDIFExportConfig(ldifFilePath, ExistingFileBehavior.OVERWRITE);
        LDIFWriter writer = new LDIFWriter(exportConfig);

        for (DN dn : baseDNs)
        {
          writer.writeEntry(createEntry(dn));
        }

        writer.close();

        if (ldifFiles == null)
        {
          ldifFiles = new LinkedList<String>();
        }
        ldifFiles.add(ldifFilePath);
      }
      catch (Exception e)
      {
        Message message = ERR_INSTALLDS_CANNOT_CREATE_BASE_ENTRY_LDIF.get(
                String.valueOf(e));

        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
    }
    else if (populateType == POPULATE_TYPE_GENERATE_SAMPLE_DATA)
    {
      try
      {
        File templateFile = SetupUtils.createTemplateFile(
                                 baseDNs.getFirst().toString(), numUsers);
        if (ldifFiles == null)
        {
          ldifFiles = new LinkedList<String>();
        }
        ldifFiles.add(templateFile.getAbsolutePath());
      }
      catch (Exception e)
      {
        Message message = ERR_INSTALLDS_CANNOT_CREATE_TEMPLATE_FILE.get(
                String.valueOf(e));

        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
    }

    if ((ldifFiles != null) && (! ldifFiles.isEmpty()))
    {
      if (! quietInstall.isPresent())
      {
        Message message = INFO_INSTALLDS_STATUS_IMPORTING_LDIF.get();
        System.out.println(wrapText(message, MAX_LINE_WIDTH));
      }

      // Use the ImportLDIF tool to perform the import.
      argList = new ArrayList<String>();
      argList.add("-C");
      argList.add(configClassName);
      argList.add("-f");
      argList.add(configFileName);
      argList.add("-n");
      argList.add("userRoot");

      for (String s : ldifFiles)
      {
        if (populateType == POPULATE_TYPE_GENERATE_SAMPLE_DATA)
        {
          argList.add("-t");
        }
        else
        {
          argList.add("-l");
        }
        argList.add(s);
      }

      if (populateType == POPULATE_TYPE_GENERATE_SAMPLE_DATA)
      {
        argList.add("-s");
        argList.add("0");
      }

      if (populateType == POPULATE_TYPE_BASE_ONLY)
      {
        argList.add("-q");
      }

      String[] importLDIFArguments = new String[argList.size()];
      argList.toArray(importLDIFArguments);

      returnValue = ImportLDIF.mainImportLDIF(importLDIFArguments);
      if (returnValue != 0)
      {
        Message message = ERR_INSTALLDS_IMPORT_UNSUCCESSFUL.get();
        System.out.println(wrapText(message, MAX_LINE_WIDTH));
        return returnValue;
      }
      else
      {
        Message message = INFO_INSTALLDS_IMPORT_SUCCESSFUL.get();
        System.out.println(wrapText(message, MAX_LINE_WIDTH));
      }
    }


    // Try to write a file that can be used to set the JAVA_HOME environment
    // variable for the administrative scripts and client tools provided with
    // the server.  If this fails, then it's not a big deal.
    try
    {
      String serverRoot = System.getenv("INSTANCE_ROOT");
      if ((serverRoot == null) || (serverRoot.length() == 0))
      {
        File f = new File(configFileName);
        serverRoot = f.getParentFile().getParentFile().getAbsolutePath();
      }

      // This isn't likely to happen, and it's not a serious problem even if it
      // does.
      SetupUtils.writeSetJavaHome(serverRoot);
    } catch (Exception e) {}

    if (enableService)
    {
      Message message = INFO_INSTALLDS_ENABLING_WINDOWS_SERVICE.get();
      System.out.println(wrapText(message, MAX_LINE_WIDTH));
      int code = ConfigureWindowsService.enableService(System.out,
          System.err);

      switch (code)
      {
      case ConfigureWindowsService.SERVICE_ENABLE_SUCCESS:
        break;
      case ConfigureWindowsService.SERVICE_ALREADY_ENABLED:
        break;
      default:
        // It did not work.
        return code;
      }
    }

    // If we've gotten here, then everything seems to have gone smoothly.
    if (! quietInstall.isPresent())
    {
      Message message = INFO_INSTALLDS_STATUS_SUCCESS.get();
      System.out.println(wrapText(message, MAX_LINE_WIDTH));
    }

    return 0;
  }



  /**
   * Interactively prompts (on standard output) the user to provide a Boolean
   * value.  The answer provided must be one of "true", "t", "yes", "y",
   * "false", "f", "no", or "n", ignoring capitalization.  It will keep
   * prompting until an acceptable value is given.
   *
   * @param  prompt        The prompt to present to the user.
   * @param  defaultValue  The default value to assume if the user presses ENTER
   *                       without typing anything, or <CODE>null</CODE> if
   *                       there should not be a default and the user must
   *                       explicitly provide a value.
   *
   * @return  The <CODE>boolean</CODE> value read from the user input.
   */
  private static boolean promptForBoolean(Message prompt, Boolean defaultValue)
  {
    String wrappedPrompt = wrapText(prompt, MAX_LINE_WIDTH);

    while (true)
    {
      System.out.println();
      System.out.println(wrappedPrompt);

      if (defaultValue == null)
      {
        System.out.print(": ");
      }
      else
      {
        System.out.print("[");

        if (defaultValue)
        {
          System.out.print(INFO_INSTALLDS_PROMPT_VALUE_YES.get());
        }
        else
        {
          System.out.print(INFO_INSTALLDS_PROMPT_VALUE_NO.get());
        }

        System.out.print("]: ");
      }

      System.out.flush();

      String response = toLowerCase(readLine());
      if (response.equals("true") || response.equals("yes") ||
          response.equals("t") || response.equals("y"))
      {
        return true;
      }
      else if (response.equals("false") || response.equals("no") ||
               response.equals("f") || response.equals("n"))
      {
        return false;
      }
      else if (response.equals(""))
      {
        if (defaultValue == null)
        {
          Message message = ERR_INSTALLDS_INVALID_YESNO_RESPONSE.get();
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
        }
        else
        {
          return defaultValue;
        }
      }
      else
      {
        Message message = ERR_INSTALLDS_INVALID_YESNO_RESPONSE.get();
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
      }
    }
  }



  /**
   * Interactively prompts (on standard output) the user to provide an integer
   * value.  The answer provided must be parseable as an integer, and may be
   * required to be within a given set of bounds.  It will keep prompting until
   * an acceptable value is given.
   *
   * @param  prompt        The prompt to present to the user.
   * @param  defaultValue  The default value to assume if the user presses ENTER
   *                       without typing anything, or <CODE>null</CODE> if
   *                       there should not be a default and the user must
   *                       explicitly provide a value.
   * @param  lowerBound    The lower bound that should be enforced, or
   *                       <CODE>null</CODE> if there is none.
   * @param  upperBound    The upper bound that should be enforced, or
   *                       <CODE>null</CODE> if there is none.
   *
   * @return  The <CODE>int</CODE> value read from the user input.
   */
  private static int promptForInteger(Message prompt, Integer defaultValue,
                                      Integer lowerBound, Integer upperBound)
  {
    String wrappedPrompt = wrapText(prompt, MAX_LINE_WIDTH);

    while (true)
    {
      System.out.println();
      System.out.println(wrappedPrompt);

      if (defaultValue == null)
      {
        System.out.print(": ");
      }
      else
      {
        System.out.print("[");
        System.out.print(defaultValue);
        System.out.print("]: ");
      }

      System.out.flush();

      String response = readLine();
      if (response.equals(""))
      {
        if (defaultValue == null)
        {
          Message message = ERR_INSTALLDS_INVALID_INTEGER_RESPONSE.get();
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
        }
        else
        {
          return defaultValue;
        }
      }
      else
      {
        try
        {
          int intValue = Integer.parseInt(response);
          if ((lowerBound != null) && (intValue < lowerBound))
          {
            Message message =
                ERR_INSTALLDS_INTEGER_BELOW_LOWER_BOUND.get(lowerBound);
            System.err.println(wrapText(message, MAX_LINE_WIDTH));
          }
          else if ((upperBound != null) && (intValue > upperBound))
          {
            Message message =
                ERR_INSTALLDS_INTEGER_ABOVE_UPPER_BOUND.get(upperBound);
            System.err.println(wrapText(message, MAX_LINE_WIDTH));
          }
          else
          {
            return intValue;
          }
        }
        catch (NumberFormatException nfe)
        {
          Message message = ERR_INSTALLDS_INVALID_INTEGER_RESPONSE.get();
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
        }
      }
    }
  }



  /**
   * Interactively prompts (on standard output) the user to provide a DN value.
   * Any non-empty string will be allowed if it can be parsed as a valid DN (the
   * empty string will indicate that the default should be used, if there is
   * one).
   *
   * @param  prompt        The prompt to present to the user.
   * @param  defaultValue  The default value to assume if the user presses ENTER
   *                       without typing anything, or <CODE>null</CODE> if
   *                       there should not be a default and the user must
   *                       explicitly provide a value.
   *
   * @return  The DN value read from the user.
   */
  private static DN promptForDN(Message prompt, String defaultValue)
  {
    String wrappedPrompt = wrapText(prompt, MAX_LINE_WIDTH);

    while (true)
    {
      System.out.println();
      System.out.println(wrappedPrompt);

      if (defaultValue == null)
      {
        System.out.print(": ");
      }
      else
      {
        System.out.print("[");
        System.out.print(defaultValue);
        System.out.print("]: ");
      }

      System.out.flush();

      String response = readLine();
      if (response.equals(""))
      {
        if (defaultValue == null)
        {
          Message message = ERR_INSTALLDS_INVALID_DN_RESPONSE.get();
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
        }
        else
        {
          try
          {
            return DN.decode(defaultValue);
          }
          catch (Exception e)
          {
            Message message = ERR_INSTALLDS_INVALID_DN_RESPONSE.get();
            System.err.println(wrapText(message, MAX_LINE_WIDTH));
          }
        }
      }
      else
      {
        try
        {
          return DN.decode(response);
        }
        catch (Exception e)
        {
          Message message = ERR_INSTALLDS_INVALID_DN_RESPONSE.get();
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
        }
      }
    }
  }



  /**
   * Interactively prompts (on standard output) the user to provide a string
   * value.  Any non-empty string will be allowed (the empty string will
   * indicate that the default should be used, if there is one).
   *
   * @param  prompt        The prompt to present to the user.
   * @param  defaultValue  The default value to assume if the user presses ENTER
   *                       without typing anything, or <CODE>null</CODE> if
   *                       there should not be a default and the user must
   *                       explicitly provide a value.
   *
   * @return  The string value read from the user.
   */
  private static String promptForString(Message prompt, String defaultValue)
  {
      System.out.println();
    String wrappedPrompt = wrapText(prompt, MAX_LINE_WIDTH);

    while (true)
    {
      System.out.println(wrappedPrompt);

      if (defaultValue == null)
      {
        System.out.print(": ");
      }
      else
      {
        System.out.print("[");
        System.out.print(defaultValue);
        System.out.print("]: ");
      }

      System.out.flush();

      String response = readLine();
      if (response.equals(""))
      {
        if (defaultValue == null)
        {
          Message message = ERR_INSTALLDS_INVALID_STRING_RESPONSE.get();
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
        }
        else
        {
          return defaultValue;
        }
      }
      else
      {
        return response;
      }
    }
  }



  /**
   * Interactively prompts (on standard output) the user to provide a string
   * value.  The response that the user provides will not be echoed, and it must
   * be entered twice for confirmation.  No default value will be allowed, and
   * the string entered must contain at least one character.
   *
   * @param  initialPrompt  The initial prompt to present to the user.
   * @param  reEntryPrompt  The prompt to present to the user when requesting
   *                        that the value be re-entered for confirmation.
   *
   * @return  The string value read from the user.
   */
  private static char[] promptForPassword(Message initialPrompt,
                                          Message reEntryPrompt)
  {
    String wrappedInitialPrompt = wrapText(initialPrompt, MAX_LINE_WIDTH);
    String wrappedReEntryPrompt = wrapText(reEntryPrompt, MAX_LINE_WIDTH);

    while (true)
    {
      System.out.println();
      System.out.print(wrappedInitialPrompt);
      System.out.print(": ");
      System.out.flush();

      char[] password = PasswordReader.readPassword();
      if ((password == null) || (password.length == 0))
      {
        Message message = ERR_INSTALLDS_INVALID_PASSWORD_RESPONSE.get();
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
      }
      else
      {
        System.out.print(wrappedReEntryPrompt);
        System.out.print(": ");
        System.out.flush();
        char[] confirmedPassword = PasswordReader.readPassword();
        if ((confirmedPassword == null) ||
            (! Arrays.equals(password, confirmedPassword)))
        {
          Message message = ERR_INSTALLDS_PASSWORDS_DONT_MATCH.get();
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
        }
        else
        {
          return password;
        }
      }
    }
  }



  /**
   * Reads a line of text from standard input.
   *
   * @return  The line of text read from standard input, or <CODE>null</CODE>
   *          if the end of the stream is reached or an error occurs while
   *          attempting to read the response.
   */
  private static String readLine()
  {
    try
    {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();

      while (true)
      {
        int b = System.in.read();
        if ((b < 0) || (b == '\n'))
        {
          break;
        }
        else if (b == '\r')
        {
          int b2 = System.in.read();
          if (b2 == '\n')
          {
            break;
          }
          else
          {
            baos.write(b);
            baos.write(b2);
          }
        }
        else
        {
          baos.write(b);
        }
      }

      return new String(baos.toByteArray(), "UTF-8");
    }
    catch (Exception e)
    {
      Message message =
          ERR_INSTALLDS_ERROR_READING_FROM_STDIN.get(String.valueOf(e));
      System.err.println(wrapText(message, MAX_LINE_WIDTH));

      return null;
    }
  }
}

