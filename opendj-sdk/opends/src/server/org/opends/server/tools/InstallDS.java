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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opends.messages.Message;
import org.opends.messages.ToolMessages;
import org.opends.quicksetup.ApplicationException;
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
import org.opends.server.util.cli.CLIException;
import org.opends.server.util.cli.ConsoleApplication;
import org.opends.server.util.cli.Menu;
import org.opends.server.util.cli.MenuBuilder;
import org.opends.server.util.cli.MenuResult;
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
public class InstallDS extends ConsoleApplication
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
    ERROR_INITIALIZING_SERVER(4),
    /**
     * The user failed providing password (for the keystore for instance).
     */
    ERROR_PASSWORD_LIMIT(5);

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

  private static final int LIMIT_KEYSTORE_PASSWORD_PROMPT = 7;

  /**
   * The Logger.
   */
  static private final Logger LOG = Logger.getLogger(InstallDS.class.getName());

  // The argument parser
  private InstallDSArgumentParser argParser;

  /**
   * Constructor for the InstallDS object.
   *
   * @param out the print stream to use for standard output.
   * @param err the print stream to use for standard error.
   * @param in the input stream to use for standard input.
   */
  public InstallDS(PrintStream out, PrintStream err, InputStream in)
  {
    super(in, out, err);
  }

  /**
   * The main method for the InstallDS CLI tool.
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
              QuickSetupLog.isInitialized() ? null :
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
   * @return the return code (SUCCESSFUL, USER_DATA_ERROR or BUG).
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

    // Delete the log file that does not contain any information.  The test only
    // mode is called several times by the setup script and if we do not remove
    // it we have a lot of empty log files.
    if (argParser.testOnlyArg.isPresent())
    {
      try
      {
        QuickSetupLog.getLogFile().deleteOnExit();
      }
      catch (Throwable t)
      {
        LOG.log(Level.WARNING, "Error while trying to update the contents of "+
            "the set-java-home file in test only mode: "+t, t);
      }
    }

    // If either the showUsage or testOnly or version arguments were provided,
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
      println(ie.getMessageObject());
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
        println(ie.getMessageObject());
        return ErrorReturnCode.ERROR_INITIALIZING_SERVER.getReturnCode();
      }
    }

    UserData uData = new UserData();

    try
    {
      if (isInteractive())
      {
        promptIfRequired(uData);
      }
      else
      {
        initializeUserDataWithParser(uData);
      }
    }
    catch (UserDataException ude)
    {
      println(ude.getMessageObject());
      if (isPasswordTriesError(ude.getMessageObject()))
      {
        return ErrorReturnCode.ERROR_PASSWORD_LIMIT.getReturnCode();
      }
      else
      {
        return ErrorReturnCode.ERROR_USER_DATA.getReturnCode();
      }
    }
    System.setProperty(Constants.CLI_JAVA_PROPERTY, "true");
    OfflineInstaller installer = new OfflineInstaller();
    installer.setUserData(uData);
    installer.setProgressMessageFormatter(formatter);
    installer.addProgressUpdateListener(
        new ProgressUpdateListener() {
          public void progressUpdate(ProgressUpdateEvent ev) {
            if (ev.getNewLogs() != null)
            {
              printProgress(ev.getNewLogs());
            }
          }
        });
    printlnProgress();

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
    printlnProgress();
    printlnProgress();
    printProgress(INFO_INSTALLDS_STATUS_COMMAND_LINE.get(cmd));
    printlnProgress();

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
        println(installStatus.getInstallationMsg());
        try
        {
          if (!confirmAction(INFO_CLI_DO_YOU_WANT_TO_CONTINUE.get(), true))
          {
            throw new InitializationException(Message.EMPTY, null);
          }
        }
        catch (CLIException ce)
        {
          LOG.log(Level.SEVERE, "Unexpected error: "+ce, ce);
          throw new InitializationException(Message.EMPTY, null);
        }
      }
      else
      {
        println(installStatus.getInstallationMsg());
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
    printlnProgress();
    printProgress(Message.raw(DirectoryServer.getVersionString()));
    printlnProgress();
    printProgress(INFO_INSTALLDS_INITIALIZING.get());
    printlnProgress();

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
  public boolean isQuiet()
  {
    return argParser.quietArg.isPresent();
  }

  /**
   * {@inheritDoc}
   */
  public boolean isInteractive()
  {
    return !argParser.noPromptArg.isPresent();
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
        uData.setServerJMXPort(jmxPort);
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
      String rejectedFile = argParser.rejectedImportFileArg.getValue();
      if (rejectedFile != null)
      {
        if (!Utils.canWrite(rejectedFile))
        {
          errorMessages.add(
              ERR_INSTALLDS_CANNOT_WRITE_REJECTED.get(rejectedFile));
        }
      }
      String skippedFile = argParser.skippedImportFileArg.getValue();
      if (skippedFile != null)
      {
        if (!Utils.canWrite(skippedFile))
        {
          errorMessages.add(ERR_INSTALLDS_CANNOT_WRITE_SKIPPED.get(
              skippedFile));
        }
      }
      dataOptions = NewSuffixOptions.createImportFromLDIF(baseDNs,
          argParser.importLDIFArg.getValues(),
          rejectedFile, skippedFile);
    }
    else if (argParser.addBaseEntryArg.isPresent())
    {
      dataOptions = NewSuffixOptions.createBaseEntry(baseDNs);
    }
    else if (argParser.sampleDataArg.isPresent())
    {
      dataOptions = NewSuffixOptions.createAutomaticallyGenerated(baseDNs,
          new Integer(argParser.sampleDataArg.getValue()));
    }
    else
    {
      dataOptions = NewSuffixOptions.createEmpty(baseDNs);
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
   * @throws UserDataException if the user did not manage to provide the
   * keystore password after a certain number of tries.
   */
  private void promptIfRequired(UserData uData) throws UserDataException
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
      String pwd1 = null;
      // Prompt for password and confirm.

      while (pwd1 == null)
      {
        pwd1 = readPassword(INFO_INSTALLDS_PROMPT_ROOT_PASSWORD.get(), LOG);
        if ((pwd1 == null) || "".equals(pwd1))
        {
          pwd1 = null;
          println();
          println(INFO_EMPTY_PWD.get());
          println();
        }
      }
      String pwd2 =
        readPassword(INFO_INSTALLDS_PROMPT_CONFIRM_ROOT_PASSWORD.get(), LOG);

      if (pwd1.equals(pwd2))
      {
        pwd = pwd1;
      }
      else
      {
        println();
        println(ERR_INSTALLDS_PASSWORDS_DONT_MATCH.get());
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
          println();
        }
        try
        {
          String dn = readInput(promptMsg, arg.getDefaultValue());
          firstPrompt = false;
          dns.add(dn);
          prompted = true;
        }
        catch (CLIException ce)
        {
          LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
        }
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
          println(message);
        }
      }
      if (toRemove.size() > 0)
      {
        println();
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
            println();
          }
          portNumber = -1;
          while (portNumber == -1)
          {
            try
            {
              portNumber = readPort(promptMsg, defaultValue);
            }
            catch (CLIException ce)
            {
              portNumber = -1;
              LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
            }
          }
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
                println();
              }
              message = ERR_INSTALLDS_CANNOT_BIND_TO_PRIVILEGED_PORT.get(
                  portNumber);
              println(message);
              portNumber = -1;
            }
            else
            {
              if (prompted || includeLineBreak)
              {
                println();
              }
              message = ERR_INSTALLDS_CANNOT_BIND_TO_PORT.get(portNumber);
              println(message);
              println();
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
            println(message);
            println();
            portNumber = -1;
          }
        }
      }
      catch (ArgumentException ae)
      {
        println(ae.getMessageObject());
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
        println();
        println(ERR_INSTALLDS_NO_SUCH_LDIF_FILE.get(
            Utils.getStringFromCollection(nonExistingFiles, ", ")));
      }
      while (importLDIFFiles.isEmpty())
      {
        println();
        try
        {
          String path = readInput(INFO_INSTALLDS_PROMPT_IMPORT_FILE.get(),
              null);
          if (!Utils.fileExists(path))
          {
            println();
            println(ERR_INSTALLDS_NO_SUCH_LDIF_FILE.get(path));
          }
          else
          {
            importLDIFFiles.add(path);
          }
        }
        catch (CLIException ce)
        {
          LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
        }
      }
      String rejectedFile = argParser.rejectedImportFileArg.getValue();
      if (rejectedFile != null)
      {
        while (!Utils.canWrite(rejectedFile))
        {
          println();
          println(ERR_INSTALLDS_CANNOT_WRITE_REJECTED.get(rejectedFile));
          println();
          try
          {
            rejectedFile =
              readInput(INFO_INSTALLDS_PROMPT_REJECTED_FILE.get(), null);
          }
          catch (CLIException ce)
          {
            LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
          }
        }
      }
      String skippedFile = argParser.skippedImportFileArg.getValue();
      if (skippedFile != null)
      {
        while (!Utils.canWrite(skippedFile))
        {
          println();
          println(ERR_INSTALLDS_CANNOT_WRITE_SKIPPED.get(skippedFile));
          println();
          try
          {
            skippedFile =
              readInput(INFO_INSTALLDS_PROMPT_SKIPPED_FILE.get(), null);
          }
          catch (CLIException ce)
          {
            LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
          }
        }
      }

      dataOptions = NewSuffixOptions.createImportFromLDIF(baseDNs,
          importLDIFFiles, rejectedFile, skippedFile);
    }
    else if (argParser.addBaseEntryArg.isPresent())
    {
      dataOptions = NewSuffixOptions.createBaseEntry(baseDNs);
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
        println();
        println(ae.getMessageObject());
        Message message = INFO_INSTALLDS_PROMPT_NUM_ENTRIES.get();
        numUsers = promptForInteger(message, 2000, 0, Integer.MAX_VALUE);
      }
      dataOptions = NewSuffixOptions.createAutomaticallyGenerated(baseDNs,
          numUsers);
    }
    else
    {
      final int POPULATE_TYPE_BASE_ONLY = 1;
      final int POPULATE_TYPE_LEAVE_EMPTY = 2;
      final int POPULATE_TYPE_IMPORT_FROM_LDIF = 3;
      final int POPULATE_TYPE_GENERATE_SAMPLE_DATA = 4;
      int[] indexes = {POPULATE_TYPE_BASE_ONLY, POPULATE_TYPE_LEAVE_EMPTY,
          POPULATE_TYPE_IMPORT_FROM_LDIF, POPULATE_TYPE_GENERATE_SAMPLE_DATA};
      Message[] msgs = new Message[] {
          INFO_INSTALLDS_POPULATE_OPTION_BASE_ONLY.get(),
          INFO_INSTALLDS_POPULATE_OPTION_LEAVE_EMPTY.get(),
          INFO_INSTALLDS_POPULATE_OPTION_IMPORT_LDIF.get(),
          INFO_INSTALLDS_POPULATE_OPTION_GENERATE_SAMPLE.get()
        };

      MenuBuilder<Integer> builder = new MenuBuilder<Integer>(this);
      builder.setPrompt(INFO_INSTALLDS_HEADER_POPULATE_TYPE.get());

      for (int i=0; i<indexes.length; i++)
      {
        builder.addNumberedOption(msgs[i], MenuResult.success(indexes[i]));
      }

      builder.setDefault(Message.raw(
              String.valueOf(POPULATE_TYPE_BASE_ONLY)),
              MenuResult.success(POPULATE_TYPE_BASE_ONLY));

      Menu<Integer> menu = builder.toMenu();
      int populateType;
      try
      {
        MenuResult<Integer> m = menu.run();
        if (m.isSuccess())
        {
          populateType = m.getValue();
        }
        else
        {
          // Should never happen.
          throw new RuntimeException();
        }
      }
      catch (CLIException ce)
      {
        populateType = POPULATE_TYPE_BASE_ONLY;
        LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
      }

      if (populateType == POPULATE_TYPE_IMPORT_FROM_LDIF)
      {
        LinkedList<String> importLDIFFiles = new LinkedList<String>();
        while (importLDIFFiles.isEmpty())
        {
          Message message = INFO_INSTALLDS_PROMPT_IMPORT_FILE.get();
          println();
          try
          {
            String path = readInput(message, null);
            if (Utils.fileExists(path))
            {
              importLDIFFiles.add(path);
            }
            else
            {
              message = ERR_INSTALLDS_NO_SUCH_LDIF_FILE.get(path);
              println();
              println(message);
            }
          }
          catch (CLIException ce)
          {
            LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
          }
        }
        String rejectedFile = argParser.rejectedImportFileArg.getValue();
        if (rejectedFile != null)
        {
          while (!Utils.canWrite(rejectedFile))
          {
            println();
            println(
                ERR_INSTALLDS_CANNOT_WRITE_REJECTED.get(rejectedFile));
            println();
            try
            {
              rejectedFile =
                readInput(INFO_INSTALLDS_PROMPT_REJECTED_FILE.get(), null);
            }
            catch (CLIException ce)
            {
              LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
            }
          }
        }
        String skippedFile = argParser.skippedImportFileArg.getValue();
        if (skippedFile != null)
        {
          while (!Utils.canWrite(skippedFile))
          {
            println();
            println(ERR_INSTALLDS_CANNOT_WRITE_SKIPPED.get(skippedFile));
            println();
            try
            {
              skippedFile =
                readInput(INFO_INSTALLDS_PROMPT_SKIPPED_FILE.get(), null);
            }
            catch (CLIException ce)
            {
              LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
            }
          }
        }
        dataOptions = NewSuffixOptions.createImportFromLDIF(baseDNs,
            importLDIFFiles, rejectedFile, skippedFile);
      }
      else if (populateType == POPULATE_TYPE_GENERATE_SAMPLE_DATA)
      {
        Message message = INFO_INSTALLDS_PROMPT_NUM_ENTRIES.get();
        int numUsers = promptForInteger(message, 2000, 0, Integer.MAX_VALUE);

        dataOptions = NewSuffixOptions.createAutomaticallyGenerated(baseDNs,
            numUsers);
      }
      else if (populateType == POPULATE_TYPE_LEAVE_EMPTY)
      {
        dataOptions = NewSuffixOptions.createEmpty(baseDNs);
      }
      else if (populateType == POPULATE_TYPE_BASE_ONLY)
      {
        dataOptions = NewSuffixOptions.createBaseEntry(baseDNs);
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
   * @throws UserDataException if the user did not manage to provide the
   * keystore password after a certain number of tries.
   */
  private void promptIfRequiredForSecurityData(UserData uData)
  throws UserDataException
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
      println();
      try
      {
        enableSSL = confirmAction(INFO_INSTALLDS_PROMPT_ENABLE_SSL.get(),
            false);
        if (enableSSL)
        {
          ldapsPort = promptIfRequiredForPortData(argParser.ldapsPortArg,
              INFO_INSTALLDS_PROMPT_LDAPSPORT.get(), usedPorts, false);
        }
      }
      catch (CLIException ce)
      {
        LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
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
      println();
      try
      {
        enableStartTLS = confirmAction(INFO_INSTALLDS_ENABLE_STARTTLS.get(),
            argParser.enableStartTLSArg.isPresent());
      }
      catch (CLIException ce)
      {
        LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
      }
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
        int[] indexes = {SELF_SIGNED, JKS, PKCS12, PKCS11};
        Message[] msgs = {
            INFO_INSTALLDS_CERT_OPTION_SELF_SIGNED.get(),
            INFO_INSTALLDS_CERT_OPTION_JKS.get(),
            INFO_INSTALLDS_CERT_OPTION_PKCS12.get(),
            INFO_INSTALLDS_CERT_OPTION_PKCS11.get()
        };


        MenuBuilder<Integer> builder = new MenuBuilder<Integer>(this);
        builder.setPrompt(INFO_INSTALLDS_HEADER_CERT_TYPE.get());

        for (int i=0; i<indexes.length; i++)
        {
          builder.addNumberedOption(msgs[i], MenuResult.success(indexes[i]));
        }

        builder.setDefault(Message.raw(String.valueOf(SELF_SIGNED)),
            MenuResult.success(SELF_SIGNED));

        Menu<Integer> menu = builder.toMenu();
        int certType;
        try
        {
          MenuResult<Integer> m = menu.run();
          if (m.isSuccess())
          {
            certType = m.getValue();
          }
          else
          {
            // Should never happen.
            throw new RuntimeException();
          }
        }
        catch (CLIException ce)
        {
          LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
          certType = SELF_SIGNED;
        }
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
        println();
        Message message = INFO_INSTALLDS_PROMPT_ENABLE_SERVICE.get();
        try
        {
          enableService = confirmAction(message, false);
        }
        catch (CLIException ce)
        {
          LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
        }
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
      println();
      Message message = INFO_INSTALLDS_PROMPT_START_SERVER.get();
      try
      {
        startServer = confirmAction(message, true);
      }
      catch (CLIException ce)
      {
        LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
        startServer = true;
      }
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
   * @throws UserDataException if the user did not manage to provide the
   * keystore password after a certain number of tries.
   */
  private SecurityOptions createSecurityOptionsPrompting(
      SecurityOptions.CertificateType type, boolean enableSSL,
      boolean enableStartTLS, int ldapsPort) throws UserDataException
  {
    SecurityOptions securityOptions;
    String path;
    String certNickname = argParser.certNicknameArg.getValue();
    String pwd = argParser.getKeyStorePassword();
    if (pwd != null)
    {
      if (pwd.length() == 0)
      {
        pwd = null;
      }
    }
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
    int nPasswordPrompts = 0;

    while ((errorMessages.size() > 0) || firstTry)
    {
      boolean prompted = false;
      if (errorMessages.size() > 0)
      {
        println();
        println(Utils.getMessageFromCollection(errorMessages,
            formatter.getLineBreak().toString()));
      }

      if (type != SecurityOptions.CertificateType.PKCS11)
      {
        if (containsKeyStorePathErrorMessage(errorMessages) || (path == null))
        {
          println();
          try
          {
            path = readInput(pathPrompt, defaultPathValue);
          }
          catch (CLIException ce)
          {
            path = "";
            LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
          }

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
          println();
        }
        pwd = null;
        while (pwd == null)
        {
          if (nPasswordPrompts > LIMIT_KEYSTORE_PASSWORD_PROMPT)
          {
            throw new UserDataException(null,
                ERR_INSTALLDS_TOO_MANY_KEYSTORE_PASSWORD_TRIES.get(
                    String.valueOf(LIMIT_KEYSTORE_PASSWORD_PROMPT)));
          }
          pwd = readPassword(
                INFO_INSTALLDS_PROMPT_KEYSTORE_PASSWORD.get(), LOG);
          nPasswordPrompts ++;
        }
      }
      if (containsCertNicknameErrorMessage(errorMessages))
      {
        if (!prompted)
        {
          println();
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
   * Tells if the error messages provided corresponds to a problem with the
   * password tries.
   * @param msg the message to analyze.
   * @return <CODE>true</CODE> if the error message provided corresponds to a
   * problem with the password tries and <CODE>false</CODE> otherwise.
   */
  private boolean isPasswordTriesError(Message msg)
  {
    return msg.getDescriptor().equals(
        ERR_INSTALLDS_TOO_MANY_KEYSTORE_PASSWORD_TRIES);
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
      String s;
      try
      {
        s = readInput(prompt, String.valueOf(defaultValue));
      }
      catch (CLIException ce)
      {
        s = "";
        LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
      }
      if (s.equals(""))
      {
        if (defaultValue == null)
        {
          Message message = ERR_INSTALLDS_INVALID_INTEGER_RESPONSE.get();
          println(message);
          println();
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
            println(message);
            println();
          }
          else if ((upperBound != null) && (intValue > upperBound))
          {
            Message message =
                ERR_INSTALLDS_INTEGER_ABOVE_UPPER_BOUND.get(upperBound);
            println(message);
            println();
          }
          else
          {
            returnValue = intValue;
          }
        }
        catch (NumberFormatException nfe)
        {
          Message message = ERR_INSTALLDS_INVALID_INTEGER_RESPONSE.get();
          println(message);
          println();
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
        try
        {
          if (confirmAction(INFO_INSTALLDS_PROMPT_CERTNICKNAME.get(n), true))
          {
            nickname = n;
            break;
          }
        }
        catch (CLIException ce)
        {
          LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
        }
      }
    }
    return nickname;
  }
}
