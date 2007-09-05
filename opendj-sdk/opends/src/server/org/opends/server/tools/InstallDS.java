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

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.QuickSetupMessages.*;
import static org.opends.messages.ToolMessages.*;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.security.KeyStoreException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.logging.Logger;

import org.opends.messages.Message;
import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.CliApplicationHelper;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.CurrentInstallStatus;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.QuickSetupLog;
import org.opends.quicksetup.SecurityOptions;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.UserDataException;
import org.opends.quicksetup.event.ProgressUpdateEvent;
import org.opends.quicksetup.event.ProgressUpdateListener;
import org.opends.quicksetup.installer.NewSuffixOptions;
import org.opends.quicksetup.installer.offline.OfflineInstaller;
import org.opends.quicksetup.util.PlainTextProgressMessageFormatter;
import org.opends.quicksetup.util.Utils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.CertificateManager;
import org.opends.server.util.SetupUtils;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;

/**
 * This class provides a very simple mechanism for installing the OpenDS
 * Directory Service.  It performs the following tasks:
 * <UL>
 *   <LI>Checks if the server is already installed and running</LI>
 *   <LI>Ask the user what base DN should be used for the data</LI>
 *   <LI>Ask the user whether to create the base entry, or to import LDIF</LI>
 *   <LI>Ask the user for the LDAP port and make sure it's available</LI>
 *   <LI>Ask the user for the default root DN and password</LI>
 *   <LI>Ask the user to enable SSL or not and for the type of certificate that
 *   the server must use</LI>
 *   <LI>Ask the user if they want to start the server when done installing</LI>
 * </UL>
 */
public class InstallDS  extends CliApplicationHelper
{
  private PlainTextProgressMessageFormatter formatter =
    new PlainTextProgressMessageFormatter();
  /** Prefix for log files. */
  static public final String LOG_FILE_PREFIX = "opends-setup-";

  /** Suffix for log files. */
  static public final String LOG_FILE_SUFFIX = ".log";

  /**
   * The enumeration containing the different return codes that the command-line
   * can have.
   *
   */
  enum ErrorReturnCode
  {
    /**
     * Successful setup.
     */
    SUCCESSFUL(0),
    /**
     * We did no have an error but the setup was not executed (displayed
     * version or usage).
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
     * Error server already installed.
     */
    ERROR_SERVER_ALREADY_INSTALLED(3),
    /**
     * Error initializing server.
     */
    ERROR_INITIALIZING_SERVER(4);

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

  /**
   * The Logger.
   */
  static private final Logger LOG = Logger.getLogger(InstallDS.class.getName());

  // The argument parser
  private InstallDSArgumentParser argParser;

  /**
   * Constructor for the SetupCli object.
   *
   * @param out the print stream to use for standard output.
   * @param err the print stream to use for standard error.
   * @param in the input stream to use for standard input.
   */
  public InstallDS(PrintStream out, PrintStream err, InputStream in)
  {
    super(out, err, in);
  }

  /**
   * The main method for the setup CLI tool.
   *
   * @param args the command-line arguments provided to this program.
   */

  public static void main(String[] args)
  {
    int retCode = mainCLI(args, true, System.out, System.err, System.in);

    System.exit(retCode);
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the setup tool.
   *
   * @param args the command-line arguments provided to this program.
   *
   * @return The error code.
   */

  public static int mainCLI(String[] args)
  {
    return mainCLI(args, true, System.out, System.err, System.in);
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the setup tool.
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

    try {
      QuickSetupLog.initLogFileHandler(
              File.createTempFile(LOG_FILE_PREFIX, LOG_FILE_SUFFIX),
              "org.opends.server.tools");
      QuickSetupLog.disableConsoleLogging();
    } catch (Throwable t) {
      System.err.println("Unable to initialize log");
      t.printStackTrace();
    }

    InstallDS install = new InstallDS(out, err, inStream);

    return install.execute(args, initializeServer);
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the setup CLI.
   *
   * @param args the command-line arguments provided to this program.
   * @param  initializeServer  Indicates whether to initialize the server.
   *
   * @return the return code (SUCCESSFUL, USER_DATA_ERROR or BUG.
   */
  public int execute(String[] args, boolean initializeServer)
  {
    argParser = new InstallDSArgumentParser(InstallDS.class.getName());
    try
    {
      argParser.initializeArguments();
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
      printErrorMessage(message);
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
      printErrorMessage(message);
      printLineBreak();
      printErrorMessage(argParser.getUsage());

      return ErrorReturnCode.ERROR_USER_DATA.getReturnCode();
    }

    //  If either the showUsage or testOnly or version arguments were provided,
    // then we're done.
    if (argParser.usageOrVersionDisplayed() ||
        argParser.testOnlyArg.isPresent())
    {
      return ErrorReturnCode.SUCCESSFUL_NOP.getReturnCode();
    }

    try
    {
      checkInstallStatus();
    }
    catch (InitializationException ie)
    {
      printErrorMessage(ie.getMessageObject());
      return ErrorReturnCode.ERROR_SERVER_ALREADY_INSTALLED.getReturnCode();
    }

    if (initializeServer)
    {
      try
      {
        initializeDirectoryServer(argParser.configFileArg.getValue(),
            argParser.configClassArg.getValue());
      }
      catch (InitializationException ie)
      {
        printErrorMessage(ie.getMessageObject());
        return ErrorReturnCode.ERROR_INITIALIZING_SERVER.getReturnCode();
      }
    }

    UserData uData = new UserData();

    if (isInteractive())
    {
      promptIfRequired(uData);
    }
    else
    {
      try
      {
        initializeUserDataWithParser(uData);
      }
      catch (UserDataException ude)
      {
        printErrorMessage(ude.getMessageObject());
        return ErrorReturnCode.ERROR_USER_DATA.getReturnCode();
      }
    }

    OfflineInstaller installer = new OfflineInstaller();
    installer.setUserData(uData);
    System.setProperty(Constants.CLI_JAVA_PROPERTY, "true");
    installer.setProgressMessageFormatter(formatter);
    installer.addProgressUpdateListener(
        new ProgressUpdateListener() {
          public void progressUpdate(ProgressUpdateEvent ev) {
            if (ev.getNewLogs() != null)
            {
              printProgressMessage(ev.getNewLogs());
            }
          }
        });
    printProgressLineBreak();

    installer.run();

    ApplicationException ue = installer.getRunError();

    String cmd;
    // Use this instead a call to Installation to avoid to launch a new JVM
    // just to retrieve a path.
    String root = Utils.getInstallPathFromClasspath();
    if (SetupUtils.isWindows())
    {
      String binDir = Utils.getPath(root,
          Installation.WINDOWS_BINARIES_PATH_RELATIVE);
      cmd = Utils.getPath(binDir, Installation.WINDOWS_STATUSCLI_FILE_NAME);
    }
    else
    {
      String binDir = Utils.getPath(root,
          Installation.UNIX_BINARIES_PATH_RELATIVE);
      cmd = Utils.getPath(binDir, Installation.UNIX_STATUSCLI_FILE_NAME);
    }
    printProgressLineBreak();
    printProgressLineBreak();
    printProgressMessage(INFO_INSTALLDS_STATUS_COMMAND_LINE.get(cmd));
    printProgressLineBreak();

    if (ue != null)
    {
      return ue.getType().getReturnCode();
    }
    else
    {
      return ErrorReturnCode.SUCCESSFUL.getReturnCode();
    }
  }

  /**
   * Checks if the server is installed or not.
   * @throws InitializationException if the server is already installed and
   * configured or if the user did not accept to overwrite the existing
   * databases.
   */
  private void checkInstallStatus() throws InitializationException
  {
    CurrentInstallStatus installStatus = new CurrentInstallStatus();
    if (installStatus.canOverwriteCurrentInstall())
    {
      if (isInteractive())
      {
        printLine(installStatus.getInstallationMsg(), true);
        if (!confirm(INFO_CLI_DO_YOU_WANT_TO_CONTINUE.get()))
        {
          throw new InitializationException(Message.EMPTY, null);
        }
      }
      else
      {
        printWarningMessage(installStatus.getInstallationMsg());
      }
    }
    else if (installStatus.isInstalled())
    {
      throw new InitializationException(installStatus.getInstallationMsg(),
          null);
    }
  }

  /**
   * Initialize the directory server to be able to perform the operations
   * required during the installation.
   * @param configFile the configuration file to be used to initialize the
   * server.
   * @param configClass the configuration class to be used to initialize the
   * server.
   * @throws InitializationException if there was an error during
   * initialization.
   */
  private void initializeDirectoryServer(String configFile, String configClass)
  throws InitializationException
  {
    printProgressLineBreak();
    printProgressMessage(DirectoryServer.getVersionString());
    printProgressLineBreak();
    printProgressMessage(INFO_INSTALLDS_INITIALIZING.get());
    printProgressLineBreak();

    // Perform a base-level initialization that will be required to get
    // minimal functionality like DN parsing to work.
    DirectoryServer directoryServer = DirectoryServer.getInstance();
    DirectoryServer.bootstrapClient();

    try
    {
      DirectoryServer.initializeJMX();
    }
    catch (Throwable t)
    {
      Message message = ERR_INSTALLDS_CANNOT_INITIALIZE_JMX.get(
              String.valueOf(configFile), t.getMessage());
      throw new InitializationException(message, t);
    }

    try
    {
      directoryServer.initializeConfiguration(configClass, configFile);
    }
    catch (Throwable t)
    {
      Message message = ERR_INSTALLDS_CANNOT_INITIALIZE_CONFIG.get(
              configFile, t.getMessage());
      throw new InitializationException(message, t);
    }

    try
    {
      directoryServer.initializeSchema();
    }
    catch (Throwable t)
    {
      Message message = ERR_INSTALLDS_CANNOT_INITIALIZE_SCHEMA.get(
              configFile, t.getMessage());
      throw new InitializationException(message, t);
    }
  }

  /**
   * {@inheritDoc}
   */
  protected boolean isQuiet()
  {
    return argParser.quietArg.isPresent();
  }

  /**
   * {@inheritDoc}
   */
  protected boolean isInteractive()
  {
    return !argParser.noPromptArg.isPresent();
  }

  /**
   * This method updates the contents of a UserData object with what the user
   * specified in the command-line.  It assumes that it is being called in no
   * prompt mode.
   * @param uData the UserData object.
   * @throws UserDataException if something went wrong checking the data.
   */
  private void initializeUserDataWithParser(UserData uData)
  throws UserDataException
  {
    LinkedList<Message> errorMessages = new LinkedList<Message>();
    uData.setConfigurationClassName(argParser.configClassArg.getValue());
    uData.setConfigurationFile(argParser.configFileArg.getValue());
    uData.setQuiet(isQuiet());
    //  Check the validity of the directory manager DNs
    String dmDN = argParser.directoryManagerDNArg.getValue();

    try
    {
      DN.decode(dmDN);
    }
    catch (Exception e)
    {
      Message message =
        ERR_INSTALLDS_CANNOT_PARSE_DN.get(dmDN, e.getMessage());
      errorMessages.add(message);
    }
    uData.setDirectoryManagerDn(dmDN);

    uData.setDirectoryManagerPwd(argParser.getDirectoryManagerPassword());

    // Check the validity of the base DNs
    LinkedList<String> baseDNs = argParser.baseDNArg.getValues();
    if (baseDNs.isEmpty())
    {
      baseDNs.add(argParser.baseDNArg.getDefaultValue());
    }
    for (String baseDN : baseDNs)
    {
      try
      {
        DN.decode(baseDN);
      }
      catch (Exception e)
      {
        Message message =
          ERR_INSTALLDS_CANNOT_PARSE_DN.get(baseDN, e.getMessage());
        errorMessages.add(message);
      }
    }

    try
    {
      int ldapPort = argParser.ldapPortArg.getIntValue();
      uData.setServerPort(ldapPort);
      if (!argParser.skipPortCheckArg.isPresent())
      {
        // Check if the port can be used.
        if (!SetupUtils.canUseAsPort(ldapPort))
        {
          Message message;
          if (SetupUtils.isPriviledgedPort(ldapPort))
          {
            message = ERR_INSTALLDS_CANNOT_BIND_TO_PRIVILEGED_PORT.get(
                ldapPort);
          }
          else
          {
            message = ERR_INSTALLDS_CANNOT_BIND_TO_PORT.get(ldapPort);
          }
          errorMessages.add(message);
        }
      }
      if (argParser.jmxPortArg.isPresent())
      {
        int jmxPort = argParser.jmxPortArg.getIntValue();
        uData.setServerPort(jmxPort);
        //   Check if the port can be used.
        if (!argParser.skipPortCheckArg.isPresent())
        {
          if (!SetupUtils.canUseAsPort(jmxPort))
          {
            Message message;
            if (SetupUtils.isPriviledgedPort(jmxPort))
            {
              message = ERR_INSTALLDS_CANNOT_BIND_TO_PRIVILEGED_PORT.get(
                  jmxPort);
            }
            else
            {
              message = ERR_INSTALLDS_CANNOT_BIND_TO_PORT.get(jmxPort);
            }
            errorMessages.add(message);
          }
        }
      }
    }
    catch (ArgumentException ae)
    {
      errorMessages.add(ae.getMessageObject());
    }



    NewSuffixOptions dataOptions;
    if (argParser.importLDIFArg.isPresent())
    {
      // Check that the files exist
      LinkedList<String> nonExistingFiles = new LinkedList<String>();
      for (String file : argParser.importLDIFArg.getValues())
      {
        if (!Utils.fileExists(file))
        {
          nonExistingFiles.add(file);
        }
      }
      if (nonExistingFiles.size() > 0)
      {
        errorMessages.add(ERR_INSTALLDS_NO_SUCH_LDIF_FILE.get(
            Utils.getStringFromCollection(nonExistingFiles, ", ")));
      }
      dataOptions = new NewSuffixOptions(
          NewSuffixOptions.Type.IMPORT_FROM_LDIF_FILE, baseDNs,
          argParser.importLDIFArg.getValues());
    }
    else if (argParser.addBaseEntryArg.isPresent())
    {
      dataOptions = new NewSuffixOptions(
          NewSuffixOptions.Type.CREATE_BASE_ENTRY, baseDNs);
    }
    else if (argParser.sampleDataArg.isPresent())
    {
      dataOptions = new NewSuffixOptions(
          NewSuffixOptions.Type.IMPORT_AUTOMATICALLY_GENERATED_DATA, baseDNs,
          new Integer(argParser.sampleDataArg.getValue()));
    }
    else
    {
      dataOptions = new NewSuffixOptions(
          NewSuffixOptions.Type.LEAVE_DATABASE_EMPTY, baseDNs);
    }
    uData.setNewSuffixOptions(dataOptions);

    // Check that the security data provided is valid.
    String certNickname = argParser.certNicknameArg.getValue();
    String pwd = argParser.getKeyStorePassword();
    boolean enableSSL = argParser.ldapsPortArg.isPresent();
    boolean enableStartTLS = argParser.enableStartTLSArg.isPresent();
    int ldapsPort = -1;

    try
    {
      ldapsPort = enableSSL ? argParser.ldapsPortArg.getIntValue() : -1;
    }
    catch (ArgumentException ae)
    {
      errorMessages.add(ae.getMessageObject());
    }
    if (enableSSL)
    {
      if (!argParser.skipPortCheckArg.isPresent())
      {
        if (!SetupUtils.canUseAsPort(ldapsPort))
        {
          if (SetupUtils.isPriviledgedPort(ldapsPort))
          {
            errorMessages.add(
                ERR_INSTALLDS_CANNOT_BIND_TO_PRIVILEGED_PORT.get(ldapsPort));
          }
          else
          {
            errorMessages.add(ERR_INSTALLDS_CANNOT_BIND_TO_PORT.get(ldapsPort));
          }
        }
      }
    }
    SecurityOptions securityOptions;
    LinkedList<String> keystoreAliases = new LinkedList<String>();
    if (argParser.generateSelfSignedCertificateArg.isPresent())
    {
      securityOptions = SecurityOptions.createSelfSignedCertificateOptions(
          enableSSL, enableStartTLS, ldapsPort);
    }
    else if (argParser.useJavaKeyStoreArg.isPresent())
    {
      String path = argParser.useJavaKeyStoreArg.getValue();
      checkCertificateInKeystore(SecurityOptions.CertificateType.JKS, path, pwd,
          certNickname, errorMessages, keystoreAliases);
      securityOptions = SecurityOptions.createJKSCertificateOptions(
          path, pwd, enableSSL, enableStartTLS, ldapsPort, certNickname);
    }
    else if (argParser.usePkcs12Arg.isPresent())
    {
      String path = argParser.usePkcs12Arg.getValue();
      checkCertificateInKeystore(SecurityOptions.CertificateType.PKCS12, path,
          pwd, certNickname, errorMessages, keystoreAliases);
      securityOptions = SecurityOptions.createPKCS12CertificateOptions(
          path, pwd, enableSSL, enableStartTLS, ldapsPort, certNickname);
    }
    else if (argParser.usePkcs11Arg.isPresent())
    {
      checkCertificateInKeystore(SecurityOptions.CertificateType.PKCS11, null,
          pwd, certNickname, errorMessages, keystoreAliases);
      securityOptions = SecurityOptions.createPKCS11CertificateOptions(
          pwd, enableSSL, enableStartTLS, ldapsPort, certNickname);
    }
    else
    {
      securityOptions = SecurityOptions.createNoCertificateOptions();
    }
    uData.setSecurityOptions(securityOptions);

    uData.setEnableWindowsService(
        argParser.enableWindowsServiceArg.isPresent());
    uData.setStartServer(!argParser.doNotStartArg.isPresent());


    if (errorMessages.size() > 0)
    {
      throw new UserDataException(null,
          Utils.getMessageFromCollection(errorMessages,
              formatter.getLineBreak().toString()));
    }
  }

  /**
   * This method updates the contents of a UserData object with what the user
   * specified in the command-line. If the user did not provide explicitly some
   * data or if the provided data is not valid, it prompts the user to provide
   * it.
   * @param uData the UserData object to be updated.
   */
  private void promptIfRequired(UserData uData)
  {
    uData.setConfigurationClassName(argParser.configClassArg.getValue());
    uData.setConfigurationFile(argParser.configFileArg.getValue());
    uData.setQuiet(isQuiet());

    promptIfRequiredForDirectoryManager(uData);
    promptIfRequiredForPortData(uData);
    promptIfRequiredForImportData(uData);
    promptIfRequiredForSecurityData(uData);
    promptIfRequiredForWindowsService(uData);
    promptIfRequiredForStartServer(uData);
  }

  /**
   * This method updates the contents of a UserData object with what the user
   * specified in the command-line for the Directory Manager parameters.
   * If the user did not provide explicitly some data or if the provided data is
   * not valid, it prompts the user to provide it.
   * @param uData the UserData object to be updated.
   */
  private void promptIfRequiredForDirectoryManager(UserData uData)
  {
    LinkedList<String> dns = promptIfRequiredForDNs(
        argParser.directoryManagerDNArg, INFO_INSTALLDS_PROMPT_ROOT_DN.get(),
        true);
    uData.setDirectoryManagerDn(dns.getFirst());

    String pwd = argParser.getDirectoryManagerPassword();
    while (pwd == null)
    {
      printLineBreak();
      String pwd1 = null;
      // Prompt for password and confirm.
      while (pwd1 == null)
      {
        pwd1 = promptForPassword(INFO_INSTALLDS_PROMPT_ROOT_PASSWORD.get());
        if ("".equals(pwd1))
        {
          pwd1 = null;
          printLineBreak();
          printErrorMessage(INFO_EMPTY_PWD.get());
        }
      }
      String pwd2 =
        promptForPassword(INFO_INSTALLDS_PROMPT_CONFIRM_ROOT_PASSWORD.get());

      if (pwd1.equals(pwd2))
      {
        pwd = pwd1;
      }
      else
      {
        printLineBreak();
        printErrorMessage(ERR_INSTALLDS_PASSWORDS_DONT_MATCH.get());
      }
    }
    uData.setDirectoryManagerPwd(pwd);
  }

  /**
   * This method returns a list of DNs.  It checks that the provided list of
   * actually contain some values.  If no valid values are found it prompts
   * the user to provide a valid DN.
   * @param arg the Argument that the user provided to specify the DNs.
   * @param promptMsg the prompt message to be displayed.
   * @param includeLineBreak whether to include a line break before the first
   * prompt or not.
   * @return a list of valid DNs.
   */
  private LinkedList<String> promptIfRequiredForDNs(StringArgument arg,
      Message promptMsg, boolean includeLineBreak)
  {
    LinkedList<String> dns = new LinkedList<String>();

    boolean usedProvided = false;
    boolean firstPrompt = true;
    while (dns.isEmpty())
    {
      boolean prompted = false;
      if (usedProvided || !arg.isPresent())
      {
        if (firstPrompt && includeLineBreak)
        {
          printLineBreak();
        }
        String dn = promptForString(promptMsg, arg.getDefaultValue());
        firstPrompt = false;
        dns.add(dn);
        prompted = true;
      }
      else
      {
        dns.addAll(arg.getValues());
      }
      LinkedList<String> toRemove = new LinkedList<String>();
      for (String dn : dns)
      {
        try
        {
          DN.decode(dn);
        }
        catch (Exception e)
        {
          toRemove.add(dn);
          Message message = prompted ? ERR_INSTALLDS_INVALID_DN_RESPONSE.get() :
            ERR_INSTALLDS_CANNOT_PARSE_DN.get(dn, e.getMessage());
          printErrorMessage(message);
        }
      }
      if (toRemove.size() > 0)
      {
        printLineBreak();
      }
      dns.removeAll(toRemove);
    }
    return dns;
  }

  /**
   * This method updates the contents of a UserData object with what the user
   * specified in the command-line for the LDAP and JMX port parameters.
   * If the user did not provide explicitly some data or if the provided data is
   * not valid, it prompts the user to provide it.
   * Note: this method does not update nor check the LDAPS port.
   * @param uData the UserData object to be updated.
   */
  private void promptIfRequiredForPortData(UserData uData)
  {
    LinkedList<Integer> usedPorts = new LinkedList<Integer>();
    //  Determine the LDAP port number.
    int ldapPort = promptIfRequiredForPortData(argParser.ldapPortArg,
        INFO_INSTALLDS_PROMPT_LDAPPORT.get(), usedPorts, true);
    uData.setServerPort(ldapPort);
    usedPorts.add(ldapPort);
    if (argParser.jmxPortArg.isPresent())
    {
      int jmxPort = promptIfRequiredForPortData(argParser.jmxPortArg,
          INFO_INSTALLDS_PROMPT_JMXPORT.get(), usedPorts, true);
      uData.setServerJMXPort(jmxPort);
    }
    else
    {
      uData.setServerJMXPort(-1);
    }
  }

  /**
   * This method returns a valid port value.  It checks that the provided
   * argument contains a valid port. If a valid port is not found it prompts
   * the user to provide a valid port.
   * @param arg the Argument that the user provided to specify the port.
   * @param promptMsg the prompt message to be displayed.
   * @param usedPorts the list of ports the user provided before for other
   * connection handlers.
   * @param includeLineBreak whether to include a line break before the first
   * prompt or not.
   * @return a valid port number.
   */
  private int promptIfRequiredForPortData(IntegerArgument portArg,
      Message promptMsg, Collection<Integer> usedPorts,
      boolean includeLineBreak)
  {
    int portNumber = -1;
    boolean usedProvided = false;
    boolean firstPrompt = true;
    while (portNumber == -1)
    {
      try
      {
        boolean prompted = false;
        if (usedProvided || !portArg.isPresent())
        {
          int defaultValue = -1;
          if (portArg.getDefaultValue() != null)
          {
            defaultValue = Integer.parseInt(portArg.getDefaultValue());
          }
          if (firstPrompt && includeLineBreak)
          {
            printLineBreak();
          }
          portNumber = promptForPort(promptMsg, defaultValue);
          prompted = true;
          firstPrompt = false;
        }
        else
        {
          portNumber = portArg.getIntValue();
          usedProvided = true;
        }

        if (!argParser.skipPortCheckArg.isPresent())
        {
          // Check if the port can be used.
          if (!SetupUtils.canUseAsPort(portNumber))
          {
            Message message;
            if (SetupUtils.isPriviledgedPort(portNumber))
            {
              if (prompted || includeLineBreak)
              {
                printLineBreak();
              }
              message = ERR_INSTALLDS_CANNOT_BIND_TO_PRIVILEGED_PORT.get(
                  portNumber);
              printErrorMessage(message);
              portNumber = -1;
            }
            else
            {
              if (prompted || includeLineBreak)
              {
                printLineBreak();
              }
              message = ERR_INSTALLDS_CANNOT_BIND_TO_PORT.get(portNumber);
              printErrorMessage(message);
              printLineBreak();
              portNumber = -1;
            }
          }
        }
        if (portNumber != -1)
        {
          if (usedPorts.contains(portNumber))
          {
            Message message = ERR_CONFIGDS_PORT_ALREADY_SPECIFIED.get(
                String.valueOf(portNumber));
            printErrorMessage(message);
            printLineBreak();
            portNumber = -1;
          }
        }
      }
      catch (ArgumentException ae)
      {
        printErrorMessage(ae.getMessage());
      }
    }
    return portNumber;
  }

  /**
   * This method updates the contents of a UserData object with what the user
   * specified in the command-line for the base DN and data import parameters.
   * If the user did not provide explicitly some data or if the provided data is
   * not valid, it prompts the user to provide it.
   * @param uData the UserData object to be updated.
   */
  private void promptIfRequiredForImportData(UserData uData)
  {
    // Check the validity of the base DNs
    LinkedList<String> baseDNs = promptIfRequiredForDNs(
        argParser.baseDNArg, INFO_INSTALLDS_PROMPT_BASEDN.get(), true);

    NewSuffixOptions dataOptions;
    if (argParser.importLDIFArg.isPresent())
    {
      // Check that the files exist
      LinkedList<String> nonExistingFiles = new LinkedList<String>();
      LinkedList<String> importLDIFFiles = new LinkedList<String>();
      for (String file : argParser.importLDIFArg.getValues())
      {
        if (!Utils.fileExists(file))
        {
          nonExistingFiles.add(file);
        }
        else
        {
          importLDIFFiles.add(file);
        }
      }
      if (nonExistingFiles.size() > 0)
      {
        printLineBreak();
        printErrorMessage(ERR_INSTALLDS_NO_SUCH_LDIF_FILE.get(
            Utils.getStringFromCollection(nonExistingFiles, ", ")));
      }
      while (importLDIFFiles.isEmpty())
      {
        printLineBreak();
        String path = promptForString(INFO_INSTALLDS_PROMPT_IMPORT_FILE.get(),
            null);
        if (!Utils.fileExists(path))
        {
          printLineBreak();
          printErrorMessage(ERR_INSTALLDS_NO_SUCH_LDIF_FILE.get(path));
        }
        else
        {
          importLDIFFiles.add(path);
        }
      }
      dataOptions = new NewSuffixOptions(
          NewSuffixOptions.Type.IMPORT_FROM_LDIF_FILE,
          baseDNs, importLDIFFiles);
    }
    else if (argParser.addBaseEntryArg.isPresent())
    {
      dataOptions = new NewSuffixOptions(
          NewSuffixOptions.Type.CREATE_BASE_ENTRY,
          baseDNs);
    }
    else if (argParser.sampleDataArg.isPresent())
    {
      int numUsers;
      try
      {
        numUsers = argParser.sampleDataArg.getIntValue();
      }
      catch (ArgumentException ae)
      {
        printLineBreak();
        printErrorMessage(ae.getMessageObject());
        Message message = INFO_INSTALLDS_PROMPT_NUM_ENTRIES.get();
        numUsers = promptForInteger(message, 2000, 0, Integer.MAX_VALUE);
      }
      dataOptions = new NewSuffixOptions(
          NewSuffixOptions.Type.IMPORT_AUTOMATICALLY_GENERATED_DATA,
          baseDNs, numUsers);
    }
    else
    {
      final int POPULATE_TYPE_BASE_ONLY = 1;
      final int POPULATE_TYPE_LEAVE_EMPTY = 2;
      final int POPULATE_TYPE_IMPORT_FROM_LDIF = 3;
      final int POPULATE_TYPE_GENERATE_SAMPLE_DATA = 4;
      Message[] options = new Message[] {
          Message.raw(String.valueOf(POPULATE_TYPE_BASE_ONLY)),
          Message.raw(String.valueOf(POPULATE_TYPE_LEAVE_EMPTY)),
          Message.raw(String.valueOf(POPULATE_TYPE_IMPORT_FROM_LDIF)),
          Message.raw(String.valueOf(POPULATE_TYPE_GENERATE_SAMPLE_DATA))
        };
      printLineBreak();
      printLine(INFO_INSTALLDS_HEADER_POPULATE_TYPE.get(), true);
      printLine(INFO_INSTALLDS_POPULATE_OPTION_BASE_ONLY.get(), true);
      printLine(INFO_INSTALLDS_POPULATE_OPTION_LEAVE_EMPTY.get(), true);
      printLine(INFO_INSTALLDS_POPULATE_OPTION_IMPORT_LDIF.get(), true);
      printLine(INFO_INSTALLDS_POPULATE_OPTION_GENERATE_SAMPLE.get(), true);


      Message answer = promptConfirm(
          INFO_INSTALLDS_PROMPT_POPULATE_CHOICE.get(),
            options[0], options);
      int populateType = new Integer(answer.toString());

      if (populateType == POPULATE_TYPE_IMPORT_FROM_LDIF)
      {
        LinkedList<String> importLDIFFiles = new LinkedList<String>();
        while (importLDIFFiles.isEmpty())
        {
          Message message = INFO_INSTALLDS_PROMPT_IMPORT_FILE.get();
          printLineBreak();
          String path = promptForString(message, null);
          if (Utils.fileExists(path))
          {
            importLDIFFiles.add(path);
          }
          else
          {
            message = ERR_INSTALLDS_NO_SUCH_LDIF_FILE.get(path);
            printLineBreak();
            printErrorMessage(message);
          }
        }
        dataOptions = new NewSuffixOptions(
            NewSuffixOptions.Type.IMPORT_FROM_LDIF_FILE,
            baseDNs, importLDIFFiles);
      }
      else if (populateType == POPULATE_TYPE_GENERATE_SAMPLE_DATA)
      {
        Message message = INFO_INSTALLDS_PROMPT_NUM_ENTRIES.get();
        int numUsers = promptForInteger(message, 2000, 0, Integer.MAX_VALUE);

        dataOptions = new NewSuffixOptions(
            NewSuffixOptions.Type.IMPORT_AUTOMATICALLY_GENERATED_DATA,
            baseDNs, numUsers);
      }
      else if (populateType == POPULATE_TYPE_LEAVE_EMPTY)
      {
        dataOptions = new NewSuffixOptions(
            NewSuffixOptions.Type.LEAVE_DATABASE_EMPTY, baseDNs);
      }
      else if (populateType == POPULATE_TYPE_BASE_ONLY)
      {
        dataOptions = new NewSuffixOptions(
            NewSuffixOptions.Type.CREATE_BASE_ENTRY, baseDNs);
      }
      else
      {
        throw new IllegalStateException("Unexpected populateType: "+
            populateType);
      }
    }
    uData.setNewSuffixOptions(dataOptions);
  }

  /**
   * This method updates the contents of a UserData object with what the user
   * specified in the command-line for the security parameters.
   * If the user did not provide explicitly some data or if the provided data is
   * not valid, it prompts the user to provide it.
   * @param uData the UserData object to be updated.
   */
  private void promptIfRequiredForSecurityData(UserData uData)
  {
    // Check that the security data provided is valid.
    boolean enableSSL = false;
    boolean enableStartTLS = false;
    int ldapsPort = -1;

    LinkedList<Integer> usedPorts = new LinkedList<Integer>();
    usedPorts.add(uData.getServerPort());
    if (uData.getServerJMXPort() != -1)
    {
      usedPorts.add(uData.getServerJMXPort());
    }

    // Ask to enable SSL
    ldapsPort = -1;
    if (!argParser.ldapsPortArg.isPresent())
    {
      printLineBreak();
      enableSSL = confirm(INFO_INSTALLDS_PROMPT_ENABLE_SSL.get(), false);
      if (enableSSL)
      {
        ldapsPort = promptIfRequiredForPortData(argParser.ldapsPortArg,
            INFO_INSTALLDS_PROMPT_LDAPSPORT.get(), usedPorts, false);
      }
    }
    else
    {
      ldapsPort = promptIfRequiredForPortData(argParser.ldapsPortArg,
          INFO_INSTALLDS_PROMPT_LDAPSPORT.get(), usedPorts, true);
      enableSSL = true;
    }

    // Ask to enable Start TLS
    if (!argParser.enableStartTLSArg.isPresent())
    {
      printLineBreak();
      enableStartTLS = confirm(INFO_INSTALLDS_ENABLE_STARTTLS.get(),
          argParser.enableStartTLSArg.isPresent());
    }
    else
    {
      enableStartTLS = true;
    }

    SecurityOptions securityOptions;
    if (argParser.generateSelfSignedCertificateArg.isPresent())
    {
      securityOptions = SecurityOptions.createSelfSignedCertificateOptions(
          enableSSL, enableStartTLS, ldapsPort);
    }
    else if (argParser.useJavaKeyStoreArg.isPresent())
    {
      securityOptions =
        createSecurityOptionsPrompting(SecurityOptions.CertificateType.JKS,
            enableSSL, enableStartTLS, ldapsPort);
    }
    else if (argParser.usePkcs12Arg.isPresent())
    {
      securityOptions =
        createSecurityOptionsPrompting(SecurityOptions.CertificateType.PKCS12,
            enableSSL, enableStartTLS, ldapsPort);
    }
    else if (argParser.usePkcs11Arg.isPresent())
    {
      securityOptions =
        createSecurityOptionsPrompting(SecurityOptions.CertificateType.PKCS11,
            enableSSL, enableStartTLS, ldapsPort);
    }
    else
    {
      if (!enableSSL && !enableStartTLS)
      {
        // If the user did not want to enable SSL or start TLS do not ask
        // to create a certificate.
        securityOptions = SecurityOptions.createNoCertificateOptions();
      }
      else
      {
        final int SELF_SIGNED = 1;
        final int JKS = 2;
        final int PKCS12 = 3;
        final int PKCS11 = 4;
        Message[] options = new Message[] {
            Message.raw(String.valueOf(SELF_SIGNED)),
            Message.raw(String.valueOf(JKS)),
            Message.raw(String.valueOf(PKCS12)),
            Message.raw(String.valueOf(PKCS11))
          };
        printLineBreak();
        printLine(INFO_INSTALLDS_HEADER_CERT_TYPE.get(), true);
        printLine(INFO_INSTALLDS_CERT_OPTION_SELF_SIGNED.get(), true);
        printLine(INFO_INSTALLDS_CERT_OPTION_JKS.get(), true);
        printLine(INFO_INSTALLDS_CERT_OPTION_PKCS12.get(), true);
        printLine(INFO_INSTALLDS_CERT_OPTION_PKCS11.get(), true);

        Message answer = promptConfirm(
            INFO_INSTALLDS_PROMPT_CERT_TYPE_CHOICE.get(),
              options[0], options);
        int certType = new Integer(answer.toString());

        if (certType == SELF_SIGNED)
        {
          securityOptions = SecurityOptions.createSelfSignedCertificateOptions(
                enableSSL, enableStartTLS, ldapsPort);
        }
        else if (certType == JKS)
        {
          securityOptions =
            createSecurityOptionsPrompting(SecurityOptions.CertificateType.JKS,
                enableSSL, enableStartTLS, ldapsPort);
        }
        else if (certType == PKCS12)
        {
          securityOptions =
            createSecurityOptionsPrompting(
                SecurityOptions.CertificateType.PKCS12, enableSSL,
                enableStartTLS, ldapsPort);
        }
        else if (certType == PKCS11)
        {
          securityOptions =
            createSecurityOptionsPrompting(
                SecurityOptions.CertificateType.PKCS11, enableSSL,
                enableStartTLS, ldapsPort);
        }
        else
        {
          throw new IllegalStateException("Unexpected cert type: "+ certType);
        }
      }
    }
    uData.setSecurityOptions(securityOptions);
  }

  /**
   * This method updates the contents of a UserData object with what the user
   * specified in the command-line for the Windows Service parameters.
   * If the user did not provide explicitly the data, it prompts the user to
   * provide it.
   * @param uData the UserData object to be updated.
   */
  private void promptIfRequiredForWindowsService(UserData uData)
  {
    boolean enableService = false;
    // If we are in Windows ask if the server must run as a windows service.
    if (SetupUtils.isWindows())
    {
      if (argParser.enableWindowsServiceArg.isPresent())
      {
        enableService = true;
      }
      else
      {
        printLineBreak();
        Message message = INFO_INSTALLDS_PROMPT_ENABLE_SERVICE.get();
        enableService = confirm(message, false);
      }
    }
    uData.setEnableWindowsService(enableService);
  }

  /**
   * This method updates the contents of a UserData object with what the user
   * specified in the command-line for the Directory Manager parameters.
   * If the user did not provide explicitly the data, it prompts the user to
   * provide it.
   * @param uData the UserData object to be updated.
   */
  private void promptIfRequiredForStartServer(UserData uData)
  {
    boolean startServer = false;
    if (!argParser.doNotStartArg.isPresent())
    {
      printLineBreak();
      Message message = INFO_INSTALLDS_PROMPT_START_SERVER.get();
      startServer = confirm(message);
    }
    uData.setStartServer(startServer);
  }

  /**
   * Checks that the provided parameters are valid to access an existing
   * keystore.  This method adds the encountered errors to the provided
   * list of Message.  It also adds the alias (nicknames) found to the provided
   * list of String.
   * @param type the type of keystore.
   * @param path the path of the keystore.
   * @param pwd the password (PIN) to access the keystore.
   * @param certNickname the certificate nickname that we are looking for (or
   * null if we just one to get the one that is in the keystore).
   * @param errorMessages the list that will be updated with the errors
   * encountered.
   * @param nicknameList the list that will be updated with the nicknames found
   * in the keystore.
   */
  private void checkCertificateInKeystore(SecurityOptions.CertificateType type,
      String path, String pwd, String certNickname,
      LinkedList<Message> errorMessages, LinkedList<String> nicknameList)
  {
    boolean errorWithPath = false;
    if (type != SecurityOptions.CertificateType.PKCS11)
    {
      File f = new File(path);
      if (!f.exists())
      {
        errorMessages.add(INFO_KEYSTORE_PATH_DOES_NOT_EXIST.get());
        errorWithPath = true;
      }
      else if (!f.isFile())
      {
        errorMessages.add(INFO_KEYSTORE_PATH_NOT_A_FILE.get());
        errorWithPath = true;
      }
    }
    if (!errorWithPath)
    {
      try
      {
        CertificateManager certManager;
        switch (type)
        {
          case JKS:
          certManager = new CertificateManager(
              path,
              CertificateManager.KEY_STORE_TYPE_JKS,
              pwd);
          break;

          case PKCS12:
          certManager = new CertificateManager(
              path,
              CertificateManager.KEY_STORE_TYPE_PKCS12,
              pwd);
          break;

          case PKCS11:
          certManager = new CertificateManager(
              CertificateManager.KEY_STORE_PATH_PKCS11,
              CertificateManager.KEY_STORE_TYPE_PKCS11,
              pwd);
          break;

          default:
            throw new IllegalArgumentException("Invalid type: "+type);
        }
        String[] aliases = certManager.getCertificateAliases();
        if ((aliases == null) || (aliases.length == 0))
        {
          // Could not retrieve any certificate
          switch (type)
          {
          case JKS:
            errorMessages.add(INFO_PKCS11_KEYSTORE_DOES_NOT_EXIST.get());
            break;

          case PKCS12:
            errorMessages.add(INFO_JKS_KEYSTORE_DOES_NOT_EXIST.get());
            break;
          case PKCS11:
            errorMessages.add(INFO_PKCS12_KEYSTORE_DOES_NOT_EXIST.get());
            break;
          default:
            throw new IllegalArgumentException("Invalid type: "+type);
          }
        }
        else
        {
          for (int i=0; i<aliases.length; i++)
          {
            nicknameList.add(aliases[i]);
          }
          String aliasString = Utils.getStringFromCollection(nicknameList,
              ", ");
          if (certNickname != null)
          {
            // Check if the cert alias is in the list.
            boolean found = false;
            for (int i=0; i<aliases.length && !found; i++)
            {
              found = aliases[i].equalsIgnoreCase(certNickname);
            }
            if (!found)
            {
              errorMessages.add(ERR_INSTALLDS_CERTNICKNAME_NOT_FOUND.get(
                  aliasString));
            }
          }
          else if (aliases.length > 1)
          {
            errorMessages.add(ERR_INSTALLDS_MUST_PROVIDE_CERTNICKNAME.get(
                aliasString));
          }
        }
      }
      catch (KeyStoreException ke)
      {
        // Could not access to the keystore: because the password is no good,
        // because the provided file is not a valid keystore, etc.
        switch (type)
        {
        case JKS:
          errorMessages.add(INFO_ERROR_ACCESSING_JKS_KEYSTORE.get());
          break;

        case PKCS12:
          errorMessages.add(INFO_ERROR_ACCESSING_PKCS12_KEYSTORE.get());
          break;
        case PKCS11:
          errorMessages.add(INFO_ERROR_ACCESSING_PKCS11_KEYSTORE.get());
          break;
        default:
          throw new IllegalArgumentException("Invalid type: "+type);
        }
      }
    }
  }

  /**
   * Creates a SecurityOptions object that corresponds to the provided
   * parameters.  If the parameters are not valid, it prompts the user to
   * provide them.
   * @param type the keystore type.
   * @param enableSSL whether to enable SSL or not.
   * @param enableStartTLS whether to enable StartTLS or not.
   * @param ldapsPort the LDAPS port to use.
   * @return a SecurityOptions object that corresponds to the provided
   * parameters (or to what the user provided after being prompted).
   */
  private SecurityOptions createSecurityOptionsPrompting(
      SecurityOptions.CertificateType type, boolean enableSSL,
      boolean enableStartTLS, int ldapsPort)
  {
    SecurityOptions securityOptions;
    String path;
    String certNickname = argParser.certNicknameArg.getValue();
    String pwd = argParser.getKeyStorePassword();
    Message pathPrompt;
    String defaultPathValue;

    switch (type)
    {
    case JKS:
      path = argParser.useJavaKeyStoreArg.getValue();
      pathPrompt = INFO_INSTALLDS_PROMPT_JKS_PATH.get();
      defaultPathValue = argParser.useJavaKeyStoreArg.getValue();
      break;
    case PKCS11:
      path = null;
      defaultPathValue = null;
      pathPrompt = null;
      break;
    case PKCS12:
      path = argParser.usePkcs12Arg.getValue();
      defaultPathValue = argParser.usePkcs12Arg.getValue();
      pathPrompt = INFO_INSTALLDS_PROMPT_PKCS12_PATH.get();
      break;
    default:
      throw new IllegalStateException(
          "Called promptIfRequiredCertificate with invalid type: "+type);
    }
    LinkedList<Message> errorMessages = new LinkedList<Message>();
    LinkedList<String> keystoreAliases = new LinkedList<String>();
    boolean firstTry = true;

    while ((errorMessages.size() > 0) || firstTry)
    {
      boolean prompted = false;
      if (errorMessages.size() > 0)
      {
        printLineBreak();
        printErrorMessage(Utils.getMessageFromCollection(errorMessages,
            formatter.getLineBreak().toString()));
      }

      if (type != SecurityOptions.CertificateType.PKCS11)
      {
        if (containsKeyStorePathErrorMessage(errorMessages) || (path == null))
        {
          printLineBreak();
          path = promptForString(pathPrompt, defaultPathValue);
          prompted = true;
          if (pwd != null)
          {
            errorMessages.clear();
            keystoreAliases.clear();
            checkCertificateInKeystore(type, path, pwd, certNickname,
                errorMessages, keystoreAliases);
            if (!errorMessages.isEmpty())
            {
              // Reset password: this might be a new keystore
              pwd = null;
            }
          }
        }
      }
      if (containsKeyStorePasswordErrorMessage(errorMessages) ||
          (pwd == null))
      {
        if (!prompted)
        {
          printLineBreak();
        }
        pwd = promptForPassword(
            INFO_INSTALLDS_PROMPT_KEYSTORE_PASSWORD.get());
      }
      if (containsCertNicknameErrorMessage(errorMessages))
      {
        if (!prompted)
        {
          printLineBreak();
        }
        certNickname = promptForCertificateNickname(keystoreAliases);
      }
      errorMessages.clear();
      keystoreAliases.clear();
      checkCertificateInKeystore(type, path, pwd, certNickname, errorMessages,
          keystoreAliases);
      firstTry = false;
    }
    if (certNickname == null)
    {
      certNickname = keystoreAliases.getFirst();
    }
    switch (type)
    {
      case JKS:
        securityOptions = SecurityOptions.createJKSCertificateOptions(
        path, pwd, enableSSL, enableStartTLS, ldapsPort, certNickname);
        break;
      case PKCS12:
        securityOptions = SecurityOptions.createPKCS12CertificateOptions(
            path, pwd, enableSSL, enableStartTLS, ldapsPort, certNickname);
        break;
      case PKCS11:
        securityOptions = SecurityOptions.createPKCS11CertificateOptions(
            pwd, enableSSL, enableStartTLS, ldapsPort, certNickname);
        break;
      default:
        throw new IllegalStateException(
            "Called createSecurityOptionsPrompting with invalid type: "+type);
    }
    return securityOptions;
  }

  /**
   * Tells if any of the error messages provided corresponds to a problem
   * with the key store path.
   * @param msgs the messages to analyze.
   * @return <CODE>true</CODE> if any of the error messages provided corresponds
   * to a problem with the key store path and <CODE>false</CODE> otherwise.
   */
  private boolean containsKeyStorePathErrorMessage(Collection<Message> msgs)
  {
    boolean found = false;
    for (Message msg : msgs)
    {
      if (msg.getDescriptor().equals(INFO_KEYSTORE_PATH_DOES_NOT_EXIST) ||
          msg.getDescriptor().equals(INFO_KEYSTORE_PATH_NOT_A_FILE) ||
          msg.getDescriptor().equals(INFO_JKS_KEYSTORE_DOES_NOT_EXIST) ||
          msg.getDescriptor().equals(INFO_PKCS12_KEYSTORE_DOES_NOT_EXIST) ||
          msg.getDescriptor().equals(INFO_PKCS11_KEYSTORE_DOES_NOT_EXIST) ||
          msg.getDescriptor().equals(INFO_ERROR_ACCESSING_JKS_KEYSTORE) ||
          msg.getDescriptor().equals(INFO_ERROR_ACCESSING_PKCS12_KEYSTORE) ||
          msg.getDescriptor().equals(INFO_ERROR_ACCESSING_PKCS11_KEYSTORE))
      {
        found = true;
        break;
      }
    }
    return found;
  }

  /**
   * Tells if any of the error messages provided corresponds to a problem
   * with the key store password.
   * @param msgs the messages to analyze.
   * @return <CODE>true</CODE> if any of the error messages provided corresponds
   * to a problem with the key store password and <CODE>false</CODE> otherwise.
   */
  private boolean containsKeyStorePasswordErrorMessage(Collection<Message> msgs)
  {
    boolean found = false;
    for (Message msg : msgs)
    {
      if (msg.getDescriptor().equals(INFO_JKS_KEYSTORE_DOES_NOT_EXIST) ||
          msg.getDescriptor().equals(INFO_PKCS12_KEYSTORE_DOES_NOT_EXIST) ||
          msg.getDescriptor().equals(INFO_PKCS11_KEYSTORE_DOES_NOT_EXIST) ||
          msg.getDescriptor().equals(INFO_ERROR_ACCESSING_JKS_KEYSTORE) ||
          msg.getDescriptor().equals(INFO_ERROR_ACCESSING_PKCS12_KEYSTORE) ||
          msg.getDescriptor().equals(INFO_ERROR_ACCESSING_PKCS11_KEYSTORE))
      {
        found = true;
        break;
      }
    }
    return found;
  }

  /**
   * Tells if any of the error messages provided corresponds to a problem
   * with the certificate nickname.
   * @param msgs the messages to analyze.
   * @return <CODE>true</CODE> if any of the error messages provided corresponds
   * to a problem with the certificate nickname and <CODE>false</CODE>
   * otherwise.
   */
  private boolean containsCertNicknameErrorMessage(Collection<Message> msgs)
  {
    boolean found = false;
    for (Message msg : msgs)
    {
      if (msg.getDescriptor().equals(ERR_INSTALLDS_CERTNICKNAME_NOT_FOUND) ||
          msg.getDescriptor().equals(ERR_INSTALLDS_MUST_PROVIDE_CERTNICKNAME))
      {
        found = true;
        break;
      }
    }
    return found;
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
  private int promptForInteger(Message prompt, Integer defaultValue,
                                      Integer lowerBound, Integer upperBound)
  {
    int returnValue = -1;
    while (returnValue == -1)
    {
      String s = promptForString(prompt, String.valueOf(defaultValue));
      if (s.equals(""))
      {
        if (defaultValue == null)
        {
          Message message = ERR_INSTALLDS_INVALID_INTEGER_RESPONSE.get();
          printErrorMessage(message);
          printLineBreak();
        }
        else
        {
          returnValue = defaultValue;
        }
      }
      else
      {
        try
        {
          int intValue = Integer.parseInt(s);
          if ((lowerBound != null) && (intValue < lowerBound))
          {
            Message message =
                ERR_INSTALLDS_INTEGER_BELOW_LOWER_BOUND.get(lowerBound);
            printErrorMessage(message);
            printLineBreak();
          }
          else if ((upperBound != null) && (intValue > upperBound))
          {
            Message message =
                ERR_INSTALLDS_INTEGER_ABOVE_UPPER_BOUND.get(upperBound);
            printErrorMessage(message);
            printLineBreak();
          }
          else
          {
            returnValue = intValue;
          }
        }
        catch (NumberFormatException nfe)
        {
          Message message = ERR_INSTALLDS_INVALID_INTEGER_RESPONSE.get();
          printErrorMessage(message);
          printLineBreak();
        }
      }
    }
    return returnValue;
  }

  /**
   * Prompts the user to accept on the certificates that appears on the list
   * and returns the chosen certificate nickname.
   * @param nicknames the list of certificates the user must choose from.
   * @return the chosen certificate nickname.
   */
  private String promptForCertificateNickname(LinkedList<String> nicknames)
  {
    String nickname = null;
    while (nickname == null)
    {
      for (String n : nicknames)
      {
        if (confirm(INFO_INSTALLDS_PROMPT_CERTNICKNAME.get(n)))
        {
          nickname = n;
          break;
        }
      }
    }
    return nickname;
  }
}
