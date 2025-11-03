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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011 profiq s.r.o.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.tools;

import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.util.OperatingSystem.*;

import static org.forgerock.util.Utils.*;
import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.QuickSetupMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.messages.UtilityMessages.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.forgerock.opendj.util.FipsStaticUtils;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg0;
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg1;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.client.BackendCfgClient;
import org.forgerock.opendj.server.config.server.BackendCfg;
import org.opends.messages.QuickSetupMessages;
import org.opends.messages.ToolMessages;
import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.CurrentInstallStatus;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.LicenseFile;
import org.opends.quicksetup.SecurityOptions;
import org.opends.quicksetup.TempLogFile;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.UserDataException;
import org.opends.quicksetup.event.ProgressUpdateEvent;
import org.opends.quicksetup.event.ProgressUpdateListener;
import org.opends.quicksetup.installer.Installer;
import org.opends.quicksetup.installer.NewSuffixOptions;
import org.opends.quicksetup.util.PlainTextProgressMessageFormatter;
import org.opends.quicksetup.util.Utils;
import org.opends.server.types.InitializationException;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.CertificateManager;
import org.opends.server.util.SetupUtils;
import org.opends.server.util.StaticUtils;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.Menu;
import com.forgerock.opendj.cli.MenuBuilder;
import com.forgerock.opendj.cli.MenuResult;
import com.forgerock.opendj.cli.StringArgument;

/**
 * This class provides a very simple mechanism for installing the OpenDS
 * Directory Service.  It performs the following tasks:
 * <UL>
 *   <LI>Checks if the server is already installed and running</LI>
 *   <LI>Ask the user what base DN should be used for the data</LI>
 *   <LI>Ask the user whether to create the base entry, or to import LDIF</LI>
 *   <LI>Ask the user for the administration port and make sure it's available
 *   </LI>
 *   <LI>Ask the user for the LDAP port and make sure it's available</LI>
 *   <LI>Ask the user for the default root DN and password</LI>
 *   <LI>Ask the user to enable SSL or not and for the type of certificate that
 *   the server must use</LI>
 *   <LI>Ask the user if they want to start the server when done installing</LI>
 * </UL>
 */
public class InstallDS extends ConsoleApplication
{
  private final PlainTextProgressMessageFormatter formatter = new PlainTextProgressMessageFormatter();

  /** The enumeration containing the different return codes that the command-line can have. */
  private enum InstallReturnCode
  {
    SUCCESSFUL(0),
    /** We did no have an error but the setup was not executed (displayed version or usage). */
    SUCCESSFUL_NOP(0),
    /** Unexpected error (potential bug). */
    ERROR_UNEXPECTED(1),
    /** Cannot parse arguments or data provided by user is not valid. */
    ERROR_USER_DATA(2),
    /** Error server already installed. */
    ERROR_SERVER_ALREADY_INSTALLED(3),
    /** Error initializing server. */
    ERROR_INITIALIZING_SERVER(4),
    /** The user failed providing password (for the keystore for instance). */
    ERROR_PASSWORD_LIMIT(5),
    /** The user cancelled the setup. */
    ERROR_USER_CANCELLED(6),
    /** The user doesn't accept the license. */
    ERROR_LICENSE_NOT_ACCEPTED(7);

    private int returnCode;
    private InstallReturnCode(int returnCode)
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

  /**
   * Enumeration describing the different answer that the user can provide when
   * we ask to finalize the setup. Note that the code associated correspond to
   * the order in the confirmation menu that is displayed at the end of the
   * setup in interactive mode.
   */
  private enum ConfirmCode
  {
    CONTINUE(1),
    PROVIDE_INFORMATION_AGAIN(2),
    PRINT_EQUIVALENT_COMMAND_LINE(3),
    CANCEL(3);

    private int returnCode;
    private ConfirmCode(int returnCode)
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

  /**
   * The maximum number of times that we should ask the user to provide the
   * password to access to a keystore.
   */
  private static final int LIMIT_KEYSTORE_PASSWORD_PROMPT = 7;

  private final BackendTypeHelper backendTypeHelper = new BackendTypeHelper();

  /** The argument parser. */
  private InstallDSArgumentParser argParser;

  /** Different variables we use when the user decides to provide data again. */
  private NewSuffixOptions.Type lastResetPopulateOption;
  private ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg> lastResetBackendType;
  private String lastResetImportFile;
  private String lastResetRejectedFile;
  private String lastResetSkippedFile;
  private Integer lastResetNumEntries;
  private Boolean lastResetEnableSSL;
  private Boolean lastResetEnableStartTLS;
  private SecurityOptions.CertificateType lastResetCertType;
  private String lastResetKeyStorePath;
  private Boolean lastResetEnableWindowsService;
  private Boolean lastResetStartServer;
  private DN lastResetBaseDN = DN.valueOf(Installation.DEFAULT_INTERACTIVE_BASE_DN);
  private DN lastResetDirectoryManagerDN;
  private Integer lastResetLdapPort;
  private Integer lastResetLdapsPort;
  private Integer lastResetAdminConnectorPort;
  private Integer lastResetJmxPort;

  private final TempLogFile tempLogFile;

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Constructor for the InstallDS object.
   *
   * @param out
   *          the print stream to use for standard output.
   * @param err
   *          the print stream to use for standard error.
   * @param tempLogFile
   *          the temporary log file where messages will be logged.
   */
  private InstallDS(PrintStream out, PrintStream err, TempLogFile tempLogFile)
  {
    super(out, err);
    this.tempLogFile = tempLogFile;
  }

  /**
   * Parses the provided command-line arguments and uses that information to run
   * the setup tool.
   *
   * @param args
   *          the command-line arguments provided to this program.
   * @param tempLogFile
   *          the temporary log file where messages will be logged.
   * @return The error code.
   */
  public static int mainCLI(String[] args, final TempLogFile tempLogFile)
  {
    return mainCLI(args, System.out, System.err, tempLogFile);
  }

  /**
   * Parses the provided command-line arguments and uses that information to run
   * the setup tool.
   *
   * @param args
   *          The command-line arguments provided to this program.
   * @param outStream
   *          The output stream to use for standard output, or <CODE>null</CODE>
   *          if standard output is not needed.
   * @param errStream
   *          The output stream to use for standard error, or <CODE>null</CODE>
   *          if standard error is not needed.
   * @param tempLogFile
   *          the temporary log file where messages will be logged.
   * @return The error code.
   */
  public static int mainCLI(
      String[] args, OutputStream outStream, OutputStream errStream, TempLogFile tempLogFile)
  {
    //
    // *NOTE* this method has been kept public because it is used by OpenAM.
    //

    final PrintStream out = NullOutputStream.wrapOrNullStream(outStream);

    System.setProperty(Constants.CLI_JAVA_PROPERTY, "true");

    final PrintStream err = NullOutputStream.wrapOrNullStream(errStream);
    final InstallDS install = new InstallDS(out, err, tempLogFile);

    return install.execute(args);
  }

  /**
   * Parses the provided command-line arguments and uses that information to run
   * the setup CLI.
   *
   * @param args
   *          the command-line arguments provided to this program.
   * @return the return code (SUCCESSFUL, USER_DATA_ERROR or BUG).
   */
  private int execute(String[] args)
  {
    argParser = new InstallDSArgumentParser(InstallDS.class.getName());
    try
    {
      argParser.initializeArguments();
    }
    catch (final ArgumentException ae)
    {
      println(ToolMessages.ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
      return InstallReturnCode.ERROR_UNEXPECTED.getReturnCode();
    }

    lastResetDirectoryManagerDN = DN.valueOf(argParser.directoryManagerDNArg.getDefaultValue());
    lastResetLdapPort = Integer.parseInt(argParser.ldapPortArg.getDefaultValue());
    lastResetLdapsPort = Integer.parseInt(argParser.ldapsPortArg.getDefaultValue());
    lastResetAdminConnectorPort = Integer.parseInt(argParser.adminConnectorPortArg.getDefaultValue());
    lastResetJmxPort = Integer.parseInt(argParser.jmxPortArg.getDefaultValue());

    // Validate user provided data
    try
    {
      argParser.parseArguments(args);
    }
    catch (final ArgumentException ae)
    {
      argParser.displayMessageAndUsageReference(getErrStream(), ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
      return InstallReturnCode.ERROR_USER_DATA.getReturnCode();
    }

    if (argParser.usageOrVersionDisplayed())
    {
      return InstallReturnCode.SUCCESSFUL_NOP.getReturnCode();
    }

    try
    {
      checkInstallStatus();
    }
    catch (final InitializationException ie)
    {
      println(ie.getMessageObject());
      return InstallReturnCode.ERROR_SERVER_ALREADY_INSTALLED.getReturnCode();
    }

    if (!checkLicense())
    {
      return InstallReturnCode.ERROR_LICENSE_NOT_ACCEPTED.getReturnCode();
    }

    if(argParser.useBcfksArg.isPresent()) {
      FipsStaticUtils.registerBcProvider(true);
    }

    final UserData uData = new UserData();
    InstallReturnCode fillUserDataRC;
    try
    {
      fillUserDataRC = fillUserData(uData, args);
      if (fillUserDataRC != InstallReturnCode.SUCCESSFUL)
      {
        return fillUserDataRC.getReturnCode();
      }
    }
    catch (final UserDataException e)
    {
      return printAndReturnErrorCode(e.getMessageObject()).getReturnCode();
    }

    System.setProperty(Constants.CLI_JAVA_PROPERTY, "true");
    final Installer installer = new Installer();
    installer.setTempLogFile(tempLogFile);
    installer.setUserData(uData);
    installer.setProgressMessageFormatter(formatter);
    installer.addProgressUpdateListener(
        new ProgressUpdateListener() {
          @Override
          public void progressUpdate(ProgressUpdateEvent ev) {
            if (ev.getNewLogs() != null)
            {
              print(ev.getNewLogs());
            }
          }
        });
    println();

    installer.run();
    printStatusCommand();

    final ApplicationException ue = installer.getApplicationException();
    if (ue != null)
    {
      return ue.getType().getReturnCode();
    }

    return InstallReturnCode.SUCCESSFUL.getReturnCode();
  }

  private InstallReturnCode fillUserData(UserData uData, String[] args) throws UserDataException
  {
    if (!isInteractive())
    {
      initializeNonInteractiveUserDataWithParser(uData);
      return InstallReturnCode.SUCCESSFUL;
    }

    boolean userApproved = false;
    while (!userApproved)
    {
      try
      {
        promptIfRequired(uData);
      }
      catch (final ClientException ce)
      {
        return printAndReturnErrorCode(ce.getMessageObject());
      }

      boolean promptAgain = true;
      printSummary(uData);
      while (promptAgain)
      {
        promptAgain = false;
        final ConfirmCode confirm = askForConfirmation();
        switch (confirm)
        {
        case CONTINUE:
          userApproved = true;
          break;

        case CANCEL:
          logger.debug(LocalizableMessage.raw("User cancelled setup."));
          return InstallReturnCode.ERROR_USER_CANCELLED;

        case PRINT_EQUIVALENT_COMMAND_LINE:
          printEquivalentCommandLine(uData);
          promptAgain = true;
          break;

        case PROVIDE_INFORMATION_AGAIN:
          // Reset the arguments
          try
          {
            resetArguments(uData);
            argParser.parseArguments(args);
          }
          catch (final Throwable t)
          {
            logger.warn(LocalizableMessage.raw("Error resetting arg parser: "+t, t));
          }
          userApproved = false;
        }
      }
    }

    return InstallReturnCode.SUCCESSFUL;
  }

  private boolean checkLicense()
  {
    if (!LicenseFile.exists()) {
      return true;
    }

    println(LocalizableMessage.raw(LicenseFile.getText()));
    // If the user asks for acceptLicense, license is displayed
    // and automatically accepted.
    if (!argParser.acceptLicense.isPresent())
    {
      final String yes = INFO_LICENSE_CLI_ACCEPT_YES.get().toString();
      final String no = INFO_LICENSE_CLI_ACCEPT_NO.get().toString();
      final String yesShort = INFO_PROMPT_YES_FIRST_LETTER_ANSWER.get().toString();
      final String noShort = INFO_PROMPT_NO_FIRST_LETTER_ANSWER.get().toString();
      println(QuickSetupMessages.INFO_LICENSE_DETAILS_CLI_LABEL.get());

      final BufferedReader in = new BufferedReader(new InputStreamReader(getInputStream()));

      // No-prompt arg automatically rejects the license.
      if (!argParser.noPromptArg.isPresent())
      {
        while (true)
        {
          print(INFO_LICENSE_CLI_ACCEPT_QUESTION.get(yes, no, no));
          try
          {
            final String response = in.readLine();
            if (response == null
                || response.equalsIgnoreCase(no)
                || response.equalsIgnoreCase(noShort)
                || response.length() == 0)
            {
              return false;
            }
            else if (response.equalsIgnoreCase(yes)
                  || response.equalsIgnoreCase(yesShort))
            {
              LicenseFile.setApproval(true);
              break;
            }
            println(QuickSetupMessages.INFO_LICENSE_CLI_ACCEPT_INVALID_RESPONSE.get());
          }
          catch (final IOException e)
          {
            println(QuickSetupMessages.INFO_LICENSE_CLI_ACCEPT_INVALID_RESPONSE.get());
          }
        }
      }
      else
      {
        return false;
      }
    }
    else
    {
      print(INFO_LICENSE_ACCEPT.get());
      print(INFO_PROMPT_YES_COMPLETE_ANSWER.get());
      LicenseFile.setApproval(true);
    }

    return true;
  }

  private void printStatusCommand()
  {
    // Use this instead a call to Installation to avoid to launch a new JVM just to retrieve a path.
    final String binariesRelativePath = isWindows() ? Installation.WINDOWS_BINARIES_PATH_RELATIVE
                                                    : Installation.UNIX_BINARIES_PATH_RELATIVE;
    final String statusCliFileName = isWindows() ? Installation.WINDOWS_STATUSCLI_FILE_NAME
                                                 : Installation.UNIX_STATUSCLI_FILE_NAME;
    final String binDir = Utils.getPath(Utils.getInstallPathFromClasspath(), binariesRelativePath);
    final String cmd = Utils.getPath(binDir, statusCliFileName);
    println();
    println(INFO_INSTALLDS_STATUS_COMMAND_LINE.get(cmd));
    println();
  }

  private InstallReturnCode printAndReturnErrorCode(LocalizableMessage message)
  {
    println(message);
    if (StaticUtils.hasDescriptor(message, ERR_INSTALLDS_TOO_MANY_KEYSTORE_PASSWORD_TRIES))
    {
      return InstallReturnCode.ERROR_PASSWORD_LIMIT;
    }

    return InstallReturnCode.ERROR_USER_DATA;
  }

  /**
   * Checks if the server is installed or not.
   *
   * @throws InitializationException
   *           if the server is already installed and configured or if the user
   *           did not accept to overwrite the existing databases.
   */
  private void checkInstallStatus() throws InitializationException
  {
    final CurrentInstallStatus installStatus = new CurrentInstallStatus();
    if (installStatus.canOverwriteCurrentInstall())
    {
      if (isInteractive())
      {
        println(installStatus.getInstallationMsg());
        try
        {
          if (!confirmAction(INFO_CLI_DO_YOU_WANT_TO_CONTINUE.get(), true))
          {
            throw new InitializationException(LocalizableMessage.EMPTY);
          }
        }
        catch (final ClientException ce)
        {
          logger.error(LocalizableMessage.raw("Unexpected error: "+ce, ce));
          throw new InitializationException(LocalizableMessage.EMPTY, ce);
        }
      }
      else
      {
        println(installStatus.getInstallationMsg());
      }
    }
    else if (installStatus.isInstalled())
    {
      throw new InitializationException(installStatus.getInstallationMsg());
    }
  }

  @Override
  public boolean isQuiet()
  {
    return argParser.quietArg.isPresent();
  }

  @Override
  public boolean isInteractive()
  {
    return !argParser.noPromptArg.isPresent();
  }

  @Override
  public boolean isMenuDrivenMode() {
    return true;
  }

  @Override
  public boolean isScriptFriendly() {
    return false;
  }

  @Override
  public boolean isAdvancedMode() {
    return false;
  }

  @Override
  public boolean isVerbose() {
    return argParser.verboseArg.isPresent();
  }

  /**
   * This method updates the contents of a UserData object with what the user
   * specified in the command-line. It assumes that it is being called in no
   * prompt mode.
   *
   * @param uData
   *          the UserData object.
   * @throws UserDataException
   *           if something went wrong checking the data.
   */
  private void initializeNonInteractiveUserDataWithParser(UserData uData) throws UserDataException
  {
    uData.setQuiet(isQuiet());
    uData.setVerbose(isVerbose());
    uData.setConnectTimeout(getConnectTimeout());

    final List<LocalizableMessage> errorMessages = new LinkedList<>();
    setBackendType(uData, errorMessages);
    final List<String> baseDNs = checkBaseDNs(errorMessages);
    setDirectoryManagerData(uData, errorMessages);
    setPorts(uData, errorMessages);
    setImportData(baseDNs, uData, errorMessages);
    setSecurityData(uData, errorMessages);

    if (!errorMessages.isEmpty())
    {
      throw new UserDataException(null,
          Utils.getMessageFromCollection(errorMessages, formatter.getLineBreak().toString()));
    }
  }

  private void setBackendType(final UserData uData, final List<LocalizableMessage> errorMessages)
  {
    final String filledBackendType = argParser.backendTypeArg.getValue();
    final ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg> backend =
        backendTypeHelper.retrieveBackendTypeFromName(filledBackendType);
    if (backend != null)
    {
      uData.setBackendType(backend);
    }
    else
    {
      errorMessages.add(
          ERR_INSTALLDS_NO_SUCH_BACKEND_TYPE.get(filledBackendType, backendTypeHelper.getPrintableBackendTypeNames()));
    }
  }

  private List<String> checkBaseDNs(List<LocalizableMessage> errorMessages)
  {
    final List<String> baseDNs = argParser.baseDNArg.getValues();
    if (baseDNs.isEmpty() && argParser.baseDNArg.getDefaultValue() != null)
    {
      baseDNs.add(argParser.baseDNArg.getDefaultValue());
    }

    for (final String baseDN : baseDNs)
    {
      checkBaseDN(baseDN, errorMessages);
    }

    return baseDNs;
  }

  private void setDirectoryManagerData(UserData uData, List<LocalizableMessage> errorMessages)
  {
    final String dmDN = argParser.directoryManagerDNArg.getValue();
    if (dmDN.trim().length() == 0)
    {
      errorMessages.add(ERR_INSTALLDS_EMPTY_DN_RESPONSE.get());
    }
    checkBaseDN(dmDN, errorMessages);
    uData.setDirectoryManagerDn(DN.valueOf(argParser.directoryManagerDNArg.getValue()));

    // Check the validity of the directory manager password
    if (argParser.getDirectoryManagerPassword().isEmpty()) {
      errorMessages.add(INFO_EMPTY_PWD.get());
    }
    uData.setDirectoryManagerPwd(argParser.getDirectoryManagerPassword());
  }

  private void checkBaseDN(String baseDN, List<LocalizableMessage> errorMessages)
  {
    try
    {
      DN.valueOf(baseDN);
    }
    catch (final LocalizedIllegalArgumentException | NullPointerException e)
    {
      errorMessages.add(ERR_INSTALLDS_CANNOT_PARSE_DN.get(baseDN, e.getMessage()));
    }
  }

  private void setPorts(UserData uData, List<LocalizableMessage> errorMessages)
  {
    try
    {
      final int ldapPort = argParser.ldapPortArg.getIntValue();
      uData.setServerPort(ldapPort);

      final int adminConnectorPort = argParser.adminConnectorPortArg.getIntValue();
      uData.setAdminConnectorPort(adminConnectorPort);

      if (!argParser.skipPortCheckArg.isPresent())
      {
        checkCanUsePort(ldapPort, errorMessages);
        checkCanUsePort(adminConnectorPort, errorMessages);
      }
      if (argParser.jmxPortArg.isPresent())
      {
        final int jmxPort = argParser.jmxPortArg.getIntValue();
        uData.setServerJMXPort(jmxPort);
        if (!argParser.skipPortCheckArg.isPresent())
        {
          checkCanUsePort(jmxPort, errorMessages);
        }
      }
    }
    catch (final ArgumentException ae)
    {
      errorMessages.add(ae.getMessageObject());
    }
  }

  private void setImportData(List<String> baseDNs, UserData uData, List<LocalizableMessage> errorMessages)
  {
    NewSuffixOptions dataOptions;
    if (argParser.importLDIFArg.isPresent())
    {
      // Check that the files exist
      final List<String> nonExistingFiles = new LinkedList<>();
      for (final String file : argParser.importLDIFArg.getValues())
      {
        if (!Utils.fileExists(file))
        {
          nonExistingFiles.add(file);
        }
      }

      if (!nonExistingFiles.isEmpty())
      {
        errorMessages.add(ERR_INSTALLDS_NO_SUCH_LDIF_FILE.get(joinAsString(", ", nonExistingFiles)));
      }

      final String rejectedFile = argParser.rejectedImportFileArg.getValue();
      if (rejectedFile != null && !canWrite(rejectedFile))
      {
        errorMessages.add(ERR_INSTALLDS_CANNOT_WRITE_REJECTED.get(rejectedFile));
      }

      final String skippedFile = argParser.skippedImportFileArg.getValue();
      if (skippedFile != null && !canWrite(skippedFile))
      {
        errorMessages.add(ERR_INSTALLDS_CANNOT_WRITE_SKIPPED.get(skippedFile));
      }
      dataOptions = NewSuffixOptions.createImportFromLDIF(baseDNs, argParser.importLDIFArg.getValues(),
          rejectedFile, skippedFile);
    }
    else if (argParser.addBaseEntryArg.isPresent())
    {
      dataOptions = NewSuffixOptions.createBaseEntry(baseDNs);
    }
    else if (argParser.sampleDataArg.isPresent())
    {
      dataOptions = NewSuffixOptions.createAutomaticallyGenerated(baseDNs,
          Integer.valueOf(argParser.sampleDataArg.getValue()));
    }
    else
    {
      dataOptions = NewSuffixOptions.createEmpty(baseDNs);
    }
    uData.setNewSuffixOptions(dataOptions);
  }

  private void setSecurityData(UserData uData, List<LocalizableMessage> errorMessages)
  {
    final boolean enableSSL = argParser.ldapsPortArg.isPresent();
    int sslPort = -1;

    try
    {
      sslPort = enableSSL ? argParser.ldapsPortArg.getIntValue() : -1;
    }
    catch (final ArgumentException ae)
    {
      errorMessages.add(ae.getMessageObject());
    }

    if (enableSSL && !argParser.skipPortCheckArg.isPresent())
    {
      checkCanUsePort(sslPort, errorMessages);
    }

    checkCertificate(sslPort, enableSSL, uData, errorMessages);
    uData.setEnableWindowsService(argParser.enableWindowsServiceArg.isPresent());
    uData.setStartServer(!argParser.doNotStartArg.isPresent());
  }

  private void checkCertificate(int sslPort, boolean enableSSL, UserData uData, List<LocalizableMessage> errorMessages)
  {
    final LinkedList<String> keystoreAliases = new LinkedList<>();
    uData.setHostName(argParser.hostNameArg.getValue());

    final boolean enableStartTLS = argParser.enableStartTLSArg.isPresent();
    final String pwd = argParser.getKeyStorePassword();
    SecurityOptions.CertificateType certType = null;
    String pathToCertificat = null;
    if (argParser.generateSelfSignedCertificateArg.isPresent())
    {
      certType = SecurityOptions.CertificateType.SELF_SIGNED_CERTIFICATE;
    }
    else if (argParser.useJavaKeyStoreArg.isPresent())
    {
      certType = SecurityOptions.CertificateType.JKS;
      pathToCertificat = argParser.useJavaKeyStoreArg.getValue();
    }
    else if (argParser.useJCEKSArg.isPresent())
    {
      certType = SecurityOptions.CertificateType.JCEKS;
      pathToCertificat = argParser.useJCEKSArg.getValue();
    }
    else if (argParser.usePkcs11Arg.isPresent())
    {
      certType = SecurityOptions.CertificateType.PKCS11;
      pathToCertificat = argParser.usePkcs11Arg.getValue();
    }
    else if (argParser.usePkcs12Arg.isPresent())
    {
      certType = SecurityOptions.CertificateType.PKCS12;
      pathToCertificat = argParser.usePkcs12Arg.getValue();
    }
    else if (argParser.useBcfksArg.isPresent())
    {
      certType = SecurityOptions.CertificateType.BCFKS;
      pathToCertificat = argParser.useBcfksArg.getValue();
    }
    else
    {
      certType = SecurityOptions.CertificateType.NO_CERTIFICATE;
    }

    Collection<String> certNicknames = getCertNickNames();
    if (pathToCertificat != null)
    {
      checkCertificateInKeystore(certType, pathToCertificat, pwd, certNicknames, errorMessages, keystoreAliases);
      if (certNicknames.isEmpty() && !keystoreAliases.isEmpty())
      {
        certNicknames = Arrays.asList(keystoreAliases.getFirst());
      }
    }

    final SecurityOptions securityOptions = SecurityOptions.createOptionsForCertificatType(
        certType, pathToCertificat, pwd, enableSSL, enableStartTLS, sslPort, certNicknames);
    uData.setSecurityOptions(securityOptions);
  }

  private List<String> getCertNickNames() {
	  List<String> certNicknames = argParser.certNicknameArg.getValues();
	  if ((certNicknames == null) || (certNicknames.size() == 0)) {
		  return certNicknames;
	  }

	  List<String> splitedCertNicknames = new ArrayList<>();
	  for (String certNickname : certNicknames) {
		  splitedCertNicknames.addAll(StaticUtils.splittedStringAsList(certNickname, " "));
	  }
	  
	  return splitedCertNicknames;
  }

  private void checkCanUsePort(int port, List<LocalizableMessage> errorMessages)
  {
    if (!SetupUtils.canUseAsPort(port))
    {
      errorMessages.add(getCannotBindErrorMessage(port));
    }
  }

  private LocalizableMessage getCannotBindErrorMessage(int port)
  {
    if (SetupUtils.isPrivilegedPort(port))
    {
      return ERR_INSTALLDS_CANNOT_BIND_TO_PRIVILEGED_PORT.get(port);
    }
    return ERR_INSTALLDS_CANNOT_BIND_TO_PORT.get(port);
  }

  /**
   * This method updates the contents of a UserData object with what the user
   * specified in the command-line. If the user did not provide explicitly some
   * data or if the provided data is not valid, it prompts the user to provide
   * it.
   *
   * @param uData
   *          the UserData object to be updated.
   * @throws UserDataException
   *           if the user did not manage to provide the keystore password after
   *           a certain number of tries.
   * @throws ClientException
   *           if something went wrong when reading inputs.
   */
  private void promptIfRequired(UserData uData) throws UserDataException, ClientException
  {
    uData.setQuiet(isQuiet());
    uData.setVerbose(isVerbose());
    uData.setConnectTimeout(getConnectTimeout());

    promptIfRequiredForDirectoryManager(uData);
    promptIfRequiredForPortData(uData);
    uData.setNewSuffixOptions(promptIfRequiredForImportData(uData));
    uData.setSecurityOptions(promptIfRequiredForSecurityData(uData));
    uData.setEnableWindowsService(promptIfRequiredForWindowsService());
    uData.setStartServer(promptIfRequiredForStartServer());
  }

  /**
   * This method updates the contents of a UserData object with what the user
   * specified in the command-line for the Directory Manager parameters. If the
   * user did not provide explicitly some data or if the provided data is not
   * valid, it prompts the user to provide it.
   *
   * @param uData
   *          the UserData object to be updated.
   * @throws UserDataException
   *           if something went wrong checking the data.
   * @throws ClientException
   *           if something went wrong checking passwords.
   */
  private void promptIfRequiredForDirectoryManager(UserData uData) throws UserDataException, ClientException
  {
    final LinkedList<String> dns = promptIfRequiredForDNs(
            argParser.directoryManagerDNArg, lastResetDirectoryManagerDN, INFO_INSTALLDS_PROMPT_ROOT_DN.get(), true);
    uData.setDirectoryManagerDn(DN.valueOf(dns.getFirst()));

    int nTries = 0;
    String pwd = argParser.getDirectoryManagerPassword();
    while (pwd == null)
    {
      if (nTries >= CONFIRMATION_MAX_TRIES)
      {
        throw new UserDataException(null, ERR_TRIES_LIMIT_REACHED.get(CONFIRMATION_MAX_TRIES));
      }

      // Prompt for password and confirm.
      char[] pwd1 = readPassword(INFO_INSTALLDS_PROMPT_ROOT_PASSWORD.get());
      while (pwd1 == null || pwd1.length == 0)
      {
        println();
        println(INFO_EMPTY_PWD.get());
        println();
        pwd1 = readPassword(INFO_INSTALLDS_PROMPT_ROOT_PASSWORD.get());
      }

      final char[] pwd2 = readPassword(INFO_INSTALLDS_PROMPT_CONFIRM_ROOT_PASSWORD.get());
      if (Arrays.equals(pwd1, pwd2))
      {
        pwd = String.valueOf(pwd1);
      }
      else
      {
        println();
        println(ERR_INSTALLDS_PASSWORDS_DONT_MATCH.get());
      }

      nTries++;
    }
    uData.setDirectoryManagerPwd(pwd);
  }

  /**
   * This method returns a list of DNs. It checks that the provided list of DNs
   * actually contain some values. If no valid values are found it prompts the
   * user to provide a valid DN.
   *
   * @param arg
   *          the Argument that the user provided to specify the DNs.
   * @param valueToSuggest
   *          the value to suggest by default on prompt.
   * @param promptMsg
   *          the prompt message to be displayed.
   * @param includeLineBreak
   *          whether to include a line break before the first prompt or not.
   * @return a list of valid DNs.
   * @throws UserDataException
   *           if something went wrong checking the data.
   */
  private LinkedList<String> promptIfRequiredForDNs(StringArgument arg, DN valueToSuggest,
          LocalizableMessage promptMsg, boolean includeLineBreak) throws UserDataException
  {
    final LinkedList<String> dns = new LinkedList<>();

    boolean usedProvided = false;
    boolean firstPrompt = true;
    int nTries = 0;
    while (dns.isEmpty())
    {
      if (nTries >= CONFIRMATION_MAX_TRIES)
      {
        throw new UserDataException(null, ERR_TRIES_LIMIT_REACHED.get(CONFIRMATION_MAX_TRIES));
      }
      boolean prompted = false;
      if (usedProvided || !arg.isPresent())
      {
        if (firstPrompt && includeLineBreak)
        {
          println();
        }
        try
        {
          final String dn = readInput(promptMsg, valueToSuggest.toString());
          firstPrompt = false;
          dns.add(dn);
          prompted = true;
        }
        catch (final ClientException ce)
        {
          logger.warn(LocalizableMessage.raw("Error reading input: "+ce, ce));
        }
      }
      else
      {
        dns.addAll(arg.getValues());
        usedProvided = true;
      }
      final List<String> toRemove = new LinkedList<>();
      for (final String dn : dns)
      {
        try
        {
          DN.valueOf(dn);
          if (dn.trim().length() == 0)
          {
            toRemove.add(dn);
            println(ERR_INSTALLDS_EMPTY_DN_RESPONSE.get());
          }
        }
        catch (final Exception e)
        {
          toRemove.add(dn);
          final LocalizableMessage message = prompted ? ERR_INSTALLDS_INVALID_DN_RESPONSE.get() :
            ERR_INSTALLDS_CANNOT_PARSE_DN.get(dn, e.getMessage());
          println(message);
        }
      }
      if (!toRemove.isEmpty())
      {
        println();
      }
      dns.removeAll(toRemove);
      nTries++;
    }
    return dns;
  }

  /**
   * This method updates the contents of a UserData object with what the user
   * specified in the command-line for the administration connector, LDAP and
   * JMX port parameters. If the user did not provide explicitly some data or
   * if the provided data is not valid, it prompts the user to provide it.
   * Note: this method does not update nor check the LDAPS port.
   *
   * @param uData
   *          the UserData object to be updated.
   */
  private void promptIfRequiredForPortData(UserData uData)
  {
    uData.setHostName(promptForHostNameIfRequired());

    final List<Integer> usedPorts = new LinkedList<>();
    //  Determine the LDAP port number.
    final int ldapPort = promptIfRequiredForPortData(
            argParser.ldapPortArg, lastResetLdapPort, INFO_INSTALLDS_PROMPT_LDAPPORT.get(), usedPorts, true);
    uData.setServerPort(ldapPort);
    usedPorts.add(ldapPort);

    //  Determine the Admin Connector port number.
    final int adminConnectorPort = promptIfRequiredForPortData(argParser.adminConnectorPortArg,
            lastResetAdminConnectorPort, INFO_INSTALLDS_PROMPT_ADMINCONNECTORPORT.get(), usedPorts, true);
    uData.setAdminConnectorPort(adminConnectorPort);
    usedPorts.add(adminConnectorPort);

    if (argParser.jmxPortArg.isPresent())
    {
      final int jmxPort = promptIfRequiredForPortData(argParser.jmxPortArg, lastResetJmxPort,
          INFO_INSTALLDS_PROMPT_JMXPORT.get(), usedPorts, true);
      uData.setServerJMXPort(jmxPort);
    }
    else
    {
      uData.setServerJMXPort(-1);
    }
  }

  /**
   * This method returns a valid port value. It checks that the provided
   * argument contains a valid port. If a valid port is not found it prompts the
   * user to provide a valid port.
   *
   * @param portArg
   *          the Argument that the user provided to specify the port.
   * @param valueToSuggest
   *          the value to suggest by default on prompt.
   * @param promptMsg
   *          the prompt message to be displayed.
   * @param usedPorts
   *          the list of ports the user provided before for other connection
   *          handlers.
   * @param includeLineBreak
   *          whether to include a line break before the first prompt or not.
   * @return a valid port number.
   */
  private int promptIfRequiredForPortData(IntegerArgument portArg, Integer valueToSuggest, LocalizableMessage promptMsg,
      Collection<Integer> usedPorts, boolean includeLineBreak)
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
          if (firstPrompt && includeLineBreak)
          {
            println();
          }
          portNumber = -1;
          while (portNumber == -1)
          {
            try
            {
              portNumber = readPort(promptMsg, valueToSuggest);
            }
            catch (final ClientException ce)
            {
              portNumber = -1;
              logger.warn(LocalizableMessage.raw("Error reading input: "+ce, ce));
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

        if (!argParser.skipPortCheckArg.isPresent() && !SetupUtils.canUseAsPort(portNumber))
        {
          final LocalizableMessage message = getCannotBindErrorMessage(portNumber);
          if (prompted || includeLineBreak)
          {
            println();
          }
          println(message);
          if (!SetupUtils.isPrivilegedPort(portNumber))
          {
            println();
          }
          portNumber = -1;
        }
        if (portNumber != -1 && usedPorts.contains(portNumber))
        {
          println(ERR_CONFIGDS_PORT_ALREADY_SPECIFIED.get(portNumber));
          println();
          portNumber = -1;
        }
      }
      catch (final ArgumentException ae)
      {
        println(ae.getMessageObject());
      }
    }
    return portNumber;
  }

  /**
   * This method returns what the user specified in the command-line for the
   * base DN and data import parameters. If the user did not provide explicitly
   * some data or if the provided data is not valid, it prompts the user to
   * provide it.
   *
   * @param uData
   *          The UserData object to be updated.
   * @return the NewSuffixOptions telling how to import data
   * @throws UserDataException
   *           if something went wrong checking the data.
   */
  private NewSuffixOptions promptIfRequiredForImportData(final UserData uData) throws UserDataException
  {
    boolean prompt = true;
    if (!argParser.baseDNArg.isPresent())
    {
      println();
      try
      {
        prompt = confirmAction(INFO_INSTALLDS_PROVIDE_BASE_DN_PROMPT.get(), true);
      }
      catch (final ClientException ce)
      {
        prompt = true;
        logger.warn(LocalizableMessage.raw("Error reading input: " + ce, ce));
      }
    }

    if (!prompt)
    {
      return NewSuffixOptions.createEmpty(new LinkedList<String>());
    }

    uData.setBackendType(getOrPromptForBackendType());
    // Check the validity of the base DNs
    final List<String> baseDNs = promptIfRequiredForDNs(
            argParser.baseDNArg, lastResetBaseDN, INFO_INSTALLDS_PROMPT_BASEDN.get(), true);
    return promptIfRequiredForDataOptions(baseDNs);
  }

  private ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg> getOrPromptForBackendType()
  {
    if (argParser.backendTypeArg.isPresent())
    {
      final ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg> backend =
          backendTypeHelper.retrieveBackendTypeFromName(argParser.backendTypeArg.getValue().toLowerCase());
      if ( backend != null)
      {
        return backend;
      }
      println();
      println(ERR_INSTALLDS_NO_SUCH_BACKEND_TYPE.get(
          argParser.backendTypeArg.getValue(), backendTypeHelper.getPrintableBackendTypeNames()));
    }

    return promptForBackendType();
  }

  private ManagedObjectDefinition<? extends BackendCfgClient,? extends BackendCfg> promptForBackendType()
  {
    println();
    int backendTypeIndex = 1;
    final List<ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg>> backendTypes =
            backendTypeHelper.getBackendTypes();
    if (backendTypes.size() == 1) {
      final ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg> backendType = backendTypes.get(0);
      println(INFO_INSTALLDS_BACKEND_TYPE_USED.get(backendType.getUserFriendlyName()));
      return backendType;
    }

    try
    {
      final MenuResult<Integer> m = getBackendTypeMenu().run();
      if (m.isSuccess())
      {
        backendTypeIndex = m.getValue();
      }
    }
    catch (final ClientException ce)
    {
      logger.warn(LocalizableMessage.raw("Error reading input: " + ce, ce));
    }

    return backendTypes.get(backendTypeIndex - 1);
  }

  private Menu<Integer> getBackendTypeMenu()
  {
    final MenuBuilder<Integer> builder = new MenuBuilder<>(this);
    builder.setPrompt(INFO_INSTALLDS_PROMPT_BACKEND_TYPE.get());
    int index = 1;
    for (final ManagedObjectDefinition<?, ?> backendType : backendTypeHelper.getBackendTypes())
    {
      builder.addNumberedOption(backendType.getUserFriendlyName(), MenuResult.success(index++));
    }

    final int printableIndex = getPromptedBackendTypeIndex();
    builder.setDefault(LocalizableMessage.raw(Integer.toString(printableIndex)), MenuResult.success(printableIndex));
    return builder.toMenu();
  }

  private int getPromptedBackendTypeIndex()
  {
    if (lastResetBackendType != null)
    {
      return backendTypeHelper.getBackendTypes().indexOf(lastResetBackendType) + 1;
    }
    return 1;
  }

  private NewSuffixOptions promptIfRequiredForDataOptions(List<String> baseDNs)
  {
    NewSuffixOptions dataOptions;
    if (argParser.importLDIFArg.isPresent())
    {
      // Check that the files exist
      final List<String> nonExistingFiles = new LinkedList<>();
      final List<String> importLDIFFiles = new LinkedList<>();
      for (final String file : argParser.importLDIFArg.getValues())
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
      if (!nonExistingFiles.isEmpty())
      {
        println();
        println(ERR_INSTALLDS_NO_SUCH_LDIF_FILE.get(joinAsString(", ", nonExistingFiles)));
      }

      readImportLdifFile(importLDIFFiles, lastResetImportFile);
      String rejectedFile = readValidFilePath(argParser.rejectedImportFileArg, lastResetRejectedFile,
          ERR_INSTALLDS_CANNOT_WRITE_REJECTED, INFO_INSTALLDS_PROMPT_REJECTED_FILE);
      String skippedFile = readValidFilePath(argParser.skippedImportFileArg, lastResetSkippedFile,
          ERR_INSTALLDS_CANNOT_WRITE_SKIPPED, INFO_INSTALLDS_PROMPT_SKIPPED_FILE);
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
      catch (final ArgumentException ae)
      {
        println();
        println(ae.getMessageObject());
        final LocalizableMessage message = INFO_INSTALLDS_PROMPT_NUM_ENTRIES.get();
        numUsers = promptForInteger(message, 2000, 0, Integer.MAX_VALUE);
      }
      dataOptions = NewSuffixOptions.createAutomaticallyGenerated(baseDNs, numUsers);
    }
    else
    {
      final int POPULATE_TYPE_LEAVE_EMPTY = 1;
      final int POPULATE_TYPE_BASE_ONLY = 2;
      final int POPULATE_TYPE_IMPORT_FROM_LDIF = 3;
      final int POPULATE_TYPE_GENERATE_SAMPLE_DATA = 4;

      final int[] indexes = {POPULATE_TYPE_LEAVE_EMPTY, POPULATE_TYPE_BASE_ONLY,
          POPULATE_TYPE_IMPORT_FROM_LDIF, POPULATE_TYPE_GENERATE_SAMPLE_DATA};
      final LocalizableMessage[] msgs = new LocalizableMessage[] {
          INFO_INSTALLDS_POPULATE_OPTION_LEAVE_EMPTY.get(),
          INFO_INSTALLDS_POPULATE_OPTION_BASE_ONLY.get(),
          INFO_INSTALLDS_POPULATE_OPTION_IMPORT_LDIF.get(),
          INFO_INSTALLDS_POPULATE_OPTION_GENERATE_SAMPLE.get()
      };

      final MenuBuilder<Integer> builder = new MenuBuilder<>(this);
      builder.setPrompt(INFO_INSTALLDS_HEADER_POPULATE_TYPE.get());

      for (int i=0; i<indexes.length; i++)
      {
        builder.addNumberedOption(msgs[i], MenuResult.success(indexes[i]));
      }

      if (lastResetPopulateOption == null)
      {
        builder.setDefault(LocalizableMessage.raw(
            String.valueOf(POPULATE_TYPE_LEAVE_EMPTY)),
            MenuResult.success(POPULATE_TYPE_LEAVE_EMPTY));
      }
      else
      {
        switch (lastResetPopulateOption)
        {
        case LEAVE_DATABASE_EMPTY:
          builder.setDefault(LocalizableMessage.raw(
              String.valueOf(POPULATE_TYPE_LEAVE_EMPTY)),
              MenuResult.success(POPULATE_TYPE_LEAVE_EMPTY));
          break;
        case IMPORT_FROM_LDIF_FILE:
          builder.setDefault(LocalizableMessage.raw(
              String.valueOf(POPULATE_TYPE_IMPORT_FROM_LDIF)),
              MenuResult.success(POPULATE_TYPE_IMPORT_FROM_LDIF));
          break;
        case IMPORT_AUTOMATICALLY_GENERATED_DATA:
          builder.setDefault(LocalizableMessage.raw(
              String.valueOf(POPULATE_TYPE_GENERATE_SAMPLE_DATA)),
              MenuResult.success(POPULATE_TYPE_GENERATE_SAMPLE_DATA));
          break;
        default:
          builder.setDefault(LocalizableMessage.raw(
              String.valueOf(POPULATE_TYPE_BASE_ONLY)),
              MenuResult.success(POPULATE_TYPE_BASE_ONLY));
        }
      }

      final Menu<Integer> menu = builder.toMenu();
      int populateType;
      try
      {
        final MenuResult<Integer> m = menu.run();
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
      catch (final ClientException ce)
      {
        populateType = POPULATE_TYPE_BASE_ONLY;
        logger.warn(LocalizableMessage.raw("Error reading input: "+ce, ce));
      }

      if (populateType == POPULATE_TYPE_IMPORT_FROM_LDIF)
      {
        final List<String> importLDIFFiles = new LinkedList<>();
        readImportLdifFile(importLDIFFiles, null);
        String rejectedFile = readValidFilePath(argParser.rejectedImportFileArg, null,
            ERR_INSTALLDS_CANNOT_WRITE_REJECTED, INFO_INSTALLDS_PROMPT_REJECTED_FILE);
        String skippedFile = readValidFilePath(argParser.skippedImportFileArg, null,
            ERR_INSTALLDS_CANNOT_WRITE_SKIPPED, INFO_INSTALLDS_PROMPT_SKIPPED_FILE);
        dataOptions = NewSuffixOptions.createImportFromLDIF(baseDNs,
            importLDIFFiles, rejectedFile, skippedFile);
      }
      else if (populateType == POPULATE_TYPE_GENERATE_SAMPLE_DATA)
      {
        final LocalizableMessage message = INFO_INSTALLDS_PROMPT_NUM_ENTRIES.get();
        int defaultValue = lastResetNumEntries != null ? lastResetNumEntries : 2000;
        final int numUsers = promptForInteger(message, defaultValue, 0, Integer.MAX_VALUE);
        dataOptions = NewSuffixOptions.createAutomaticallyGenerated(baseDNs, numUsers);
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
        throw new IllegalStateException("Unexpected populateType: " + populateType);
      }
    }
    return dataOptions;
  }

  private void readImportLdifFile(final List<String> importLDIFFiles, String defaultValue)
  {
    while (importLDIFFiles.isEmpty())
    {
      println();
      try
      {
        final String path = readInput(INFO_INSTALLDS_PROMPT_IMPORT_FILE.get(), defaultValue);
        if (Utils.fileExists(path))
        {
          importLDIFFiles.add(path);
        }
        else
        {
          println();
          println(ERR_INSTALLDS_NO_SUCH_LDIF_FILE.get(path));
        }
      }
      catch (final ClientException ce)
      {
        logger.warn(LocalizableMessage.raw("Error reading input: "+ce, ce));
      }
    }
  }

  private String readValidFilePath(StringArgument arg, String defaultValue, Arg1<Object> errCannotWriteFile,
      Arg0 infoPromptFile)
  {
    String file = arg.getValue();
    if (file != null)
    {
      while (!canWrite(file))
      {
        println();
        println(errCannotWriteFile.get(file));
        println();
        try
        {
          file = readInput(infoPromptFile.get(), defaultValue);
        }
        catch (final ClientException ce)
        {
          logger.warn(LocalizableMessage.raw("Error reading input: "+ce, ce));
        }
      }
    }
    return file;
  }

  /**
   * This method returns what the user specified in the command-line for the
   * security parameters. If the user did not provide explicitly some data or if
   * the provided data is not valid, it prompts the user to provide it.
   *
   * @param uData
   *          the current UserData object.
   * @return the {@link SecurityOptions} to be used when starting the server
   * @throws UserDataException
   *           if the user did not manage to provide the keystore password after
   *           a certain number of tries.
   * @throws ClientException
   *           If an error occurs when reading inputs.
   */
  private SecurityOptions promptIfRequiredForSecurityData(UserData uData) throws UserDataException, ClientException
  {
    // Check that the security data provided is valid.
    boolean enableSSL = false;
    boolean enableStartTLS = false;
    int ldapsPort = -1;

    final List<Integer> usedPorts = new LinkedList<>();
    usedPorts.add(uData.getServerPort());
    if (uData.getServerJMXPort() != -1)
    {
      usedPorts.add(uData.getServerJMXPort());
    }

    // Ask to enable SSL
    if (!argParser.ldapsPortArg.isPresent())
    {
      println();
      try
      {
        final boolean defaultValue = lastResetEnableSSL != null ? lastResetEnableSSL : false;
        enableSSL = confirmAction(INFO_INSTALLDS_PROMPT_ENABLE_SSL.get(), defaultValue);
        if (enableSSL)
        {
          ldapsPort = promptIfRequiredForPortData(
                  argParser.ldapsPortArg, lastResetLdapsPort, INFO_INSTALLDS_PROMPT_LDAPSPORT.get(), usedPorts, false);
        }
      }
      catch (final ClientException ce)
      {
        logger.warn(LocalizableMessage.raw("Error reading input: "+ce, ce));
      }
    }
    else
    {
      ldapsPort = promptIfRequiredForPortData(
              argParser.ldapsPortArg, lastResetLdapsPort, INFO_INSTALLDS_PROMPT_LDAPSPORT.get(), usedPorts, true);
      enableSSL = true;
    }

    // Ask to enable Start TLS
    if (!argParser.enableStartTLSArg.isPresent())
    {
      println();
      try
      {
        final boolean defaultValue = lastResetEnableStartTLS != null ?
            lastResetEnableStartTLS : false;
        enableStartTLS = confirmAction(INFO_INSTALLDS_ENABLE_STARTTLS.get(),
            defaultValue);
      }
      catch (final ClientException ce)
      {
        logger.warn(LocalizableMessage.raw("Error reading input: "+ce, ce));
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
    else if (argParser.useJCEKSArg.isPresent())
    {
      securityOptions =
        createSecurityOptionsPrompting(SecurityOptions.CertificateType.JCEKS,
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
    else if (argParser.useBcfksArg.isPresent())
    {
      securityOptions =
        createSecurityOptionsPrompting(SecurityOptions.CertificateType.BCFKS,
            enableSSL, enableStartTLS, ldapsPort);
    }
    else if (!enableSSL && !enableStartTLS)
    {
      // If the user did not want to enable SSL or start TLS do not ask
      // to create a certificate.
      securityOptions = SecurityOptions.createNoCertificateOptions();
    }
    else
    {
      final int SELF_SIGNED = 1;
      final int JKS = 2;
      final int JCEKS = 3;
      final int PKCS12 = 4;
      final int PKCS11 = 5;
      final int BCFKS = 6;
      final int[] indexes = {SELF_SIGNED, JKS, JCEKS, PKCS12, PKCS11, BCFKS};
      final LocalizableMessage[] msgs = {
          INFO_INSTALLDS_CERT_OPTION_SELF_SIGNED.get(),
          INFO_INSTALLDS_CERT_OPTION_JKS.get(),
          INFO_INSTALLDS_CERT_OPTION_JCEKS.get(),
          INFO_INSTALLDS_CERT_OPTION_PKCS12.get(),
          INFO_INSTALLDS_CERT_OPTION_PKCS11.get(),
          INFO_INSTALLDS_CERT_OPTION_BCFKS.get()
      };

      final MenuBuilder<Integer> builder = new MenuBuilder<>(this);
      builder.setPrompt(INFO_INSTALLDS_HEADER_CERT_TYPE.get());

      for (int i=0; i<indexes.length; i++)
      {
        builder.addNumberedOption(msgs[i], MenuResult.success(indexes[i]));
      }

      if (lastResetCertType == null)
      {
        builder.setDefault(LocalizableMessage.raw(String.valueOf(SELF_SIGNED)),
          MenuResult.success(SELF_SIGNED));
      }
      else
      {
        switch (lastResetCertType)
        {
        case JKS:
          builder.setDefault(LocalizableMessage.raw(String.valueOf(JKS)),
              MenuResult.success(JKS));
          break;
        case JCEKS:
          builder.setDefault(LocalizableMessage.raw(String.valueOf(JCEKS)),
              MenuResult.success(JCEKS));
          break;
        case PKCS11:
          builder.setDefault(LocalizableMessage.raw(String.valueOf(PKCS11)),
              MenuResult.success(PKCS11));
          break;
        case PKCS12:
          builder.setDefault(LocalizableMessage.raw(String.valueOf(PKCS12)),
              MenuResult.success(PKCS12));
          break;
        case BCFKS:
            builder.setDefault(LocalizableMessage.raw(String.valueOf(BCFKS)),
                MenuResult.success(BCFKS));
            break;
        default:
          builder.setDefault(LocalizableMessage.raw(String.valueOf(SELF_SIGNED)),
              MenuResult.success(SELF_SIGNED));
        }
      }

      final Menu<Integer> menu = builder.toMenu();
      int certType;
      try
      {
        final MenuResult<Integer> m = menu.run();
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
      catch (final ClientException ce)
      {
        logger.warn(LocalizableMessage.raw("Error reading input: "+ce, ce));
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
      else if (certType == JCEKS)
      {
        securityOptions =
          createSecurityOptionsPrompting(
              SecurityOptions.CertificateType.JCEKS,
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
      else if (certType == BCFKS)
      {
        securityOptions =
          createSecurityOptionsPrompting(
              SecurityOptions.CertificateType.BCFKS, enableSSL,
              enableStartTLS, ldapsPort);
      }
      else
      {
        throw new IllegalStateException("Unexpected cert type: "+ certType);
      }
    }
    return securityOptions;
  }

  /**
   * This method returns what the user specified in the command-line for the
   * Windows Service parameters. If the user did not provide explicitly the
   * data, it prompts the user to provide it.
   *
   * @return whether windows service should be enabled
   */
  private boolean promptIfRequiredForWindowsService()
  {
    boolean enableService = false;
    // If we are in Windows ask if the server must run as a windows service.
    if (isWindows())
    {
      if (argParser.enableWindowsServiceArg.isPresent())
      {
        enableService = true;
      }
      else
      {
        println();
        final LocalizableMessage message = INFO_INSTALLDS_PROMPT_ENABLE_SERVICE.get();
        try
        {
          final boolean defaultValue = (lastResetEnableWindowsService == null) ?
              false : lastResetEnableWindowsService;
          enableService = confirmAction(message, defaultValue);
        }
        catch (final ClientException ce)
        {
          logger.warn(LocalizableMessage.raw("Error reading input: "+ce, ce));
        }
      }
    }
    return enableService;
  }

  /**
   * This method returns what the user specified in the command-line for the
   * Directory Manager parameters. If the user did not provide explicitly the
   * data, it prompts the user to provide it.
   *
   * @return whether server should be started
   */
  private boolean promptIfRequiredForStartServer()
  {
    boolean startServer = false;
    if (!argParser.doNotStartArg.isPresent())
    {
      println();
      final LocalizableMessage message = INFO_INSTALLDS_PROMPT_START_SERVER.get();
      try
      {
        final boolean defaultValue = (lastResetStartServer == null) ?
            true : lastResetStartServer;
        startServer = confirmAction(message, defaultValue);
      }
      catch (final ClientException ce)
      {
        logger.warn(LocalizableMessage.raw("Error reading input: "+ce, ce));
        startServer = true;
      }
    }
    return startServer;
  }

  /**
   * Checks that the provided parameters are valid to access an existing key
   * store. This method adds the encountered errors to the provided list of
   * LocalizableMessage. It also adds the alias (nicknames) found to the
   * provided list of String.
   *
   * @param type
   *          the type of key store.
   * @param path
   *          the path of the key store.
   * @param pwd
   *          the password (PIN) to access the key store.
   * @param certNicknames
   *          the certificate nicknames that we are looking for (or null if we
   *          just one to get the one that is in the key store).
   * @param errorMessages
   *          the list that will be updated with the errors encountered.
   * @param nicknameList
   *          the list that will be updated with the nicknames found in the key
   *          store.
   */
  private static void checkCertificateInKeystore(SecurityOptions.CertificateType type, String path, String pwd,
      Collection<String> certNicknames, Collection<LocalizableMessage> errorMessages, Collection<String> nicknameList)
  {
    boolean errorWithPath = false;
    if (type != SecurityOptions.CertificateType.PKCS11)
    {
      final File f = new File(path);
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

          case JCEKS:
            certManager = new CertificateManager(
                path,
                CertificateManager.KEY_STORE_TYPE_JCEKS,
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

          case BCFKS:
          certManager = new CertificateManager(
              path,
              CertificateManager.KEY_STORE_TYPE_BCFKS,
              pwd);
          break;

          default:
            throw new IllegalArgumentException("Invalid type: "+type);
        }
        final String[] aliases = certManager.getCertificateAliases();
        if (aliases == null || aliases.length == 0)
        {
          // Could not retrieve any certificate
          switch (type)
          {
          case JKS:
            errorMessages.add(INFO_JKS_KEYSTORE_DOES_NOT_EXIST.get());
            break;
          case JCEKS:
            errorMessages.add(INFO_JCEKS_KEYSTORE_DOES_NOT_EXIST.get());
            break;
          case PKCS12:
            errorMessages.add(INFO_PKCS12_KEYSTORE_DOES_NOT_EXIST.get());
            break;
          case PKCS11:
            errorMessages.add(INFO_PKCS11_KEYSTORE_DOES_NOT_EXIST.get());
            break;
          case BCFKS:
              errorMessages.add(INFO_BCFKS_KEYSTORE_DOES_NOT_EXIST.get());
              break;
          default:
            throw new IllegalArgumentException("Invalid type: "+type);
          }
        }
        else if (certManager.hasRealAliases())
        {
          Collections.addAll(nicknameList, aliases);
          final String aliasString = joinAsString(", ", nicknameList);
          if (certNicknames.isEmpty() && aliases.length > 1)
          {
            errorMessages.add(ERR_INSTALLDS_MUST_PROVIDE_CERTNICKNAME.get(aliasString));
          }
          for (String certNickname : certNicknames)
          {
            // Check if the certificate alias is in the list.
            boolean found = false;
            for (int i = 0; i < aliases.length && !found; i++)
            {
              found = aliases[i].equalsIgnoreCase(certNickname);
            }
            if (!found)
            {
              errorMessages.add(ERR_INSTALLDS_CERTNICKNAME_NOT_FOUND.get(aliasString));
            }
          }
        }
      }
      catch (final KeyStoreException ke)
      {
        // issue OPENDJ-18, related to JDK bug
        if (StaticUtils.stackTraceContainsCause(ke, ArithmeticException.class))
        {
          errorMessages.add(INFO_ERROR_ACCESSING_KEYSTORE_JDK_BUG.get());
        }
        else
        {
          // Could not access to the key store: because the password is no good,
          // because the provided file is not a valid key store, etc.
          switch (type)
          {
          case JKS:
            errorMessages.add(INFO_ERROR_ACCESSING_JKS_KEYSTORE.get());
            break;
          case JCEKS:
            errorMessages.add(INFO_ERROR_ACCESSING_JCEKS_KEYSTORE.get());
            break;
          case PKCS12:
            errorMessages.add(INFO_ERROR_ACCESSING_PKCS12_KEYSTORE.get());
            break;
          case PKCS11:
            errorMessages.add(INFO_ERROR_ACCESSING_PKCS11_KEYSTORE.get());
            break;
          default:
            throw new IllegalArgumentException("Invalid type: " + type, ke);
          }
        }
      }
    }
  }

  /**
   * Creates a SecurityOptions object that corresponds to the provided
   * parameters. If the parameters are not valid, it prompts the user to provide
   * them.
   *
   * @param type
   *          the keystore type.
   * @param enableSSL
   *          whether to enable SSL or not.
   * @param enableStartTLS
   *          whether to enable StartTLS or not.
   * @param ldapsPort
   *          the LDAPS port to use.
   * @return a SecurityOptions object that corresponds to the provided
   *         parameters (or to what the user provided after being prompted).
   * @throws UserDataException
   *           if the user did not manage to provide the keystore password after
   *           a certain number of tries.
   * @throws ClientException
   */
  private SecurityOptions createSecurityOptionsPrompting(SecurityOptions.CertificateType type, boolean enableSSL,
      boolean enableStartTLS, int ldapsPort) throws UserDataException, ClientException
  {
    String path;
    Collection<String> certNicknames = getCertNickNames();
    String pwd = argParser.getKeyStorePassword();
    if (pwd != null && pwd.length() == 0)
    {
      pwd = null;
    }
    LocalizableMessage pathPrompt;
    String defaultPathValue;

    switch (type)
    {
    case JKS:
      path = argParser.useJavaKeyStoreArg.getValue();
      pathPrompt = INFO_INSTALLDS_PROMPT_JKS_PATH.get();
      defaultPathValue = argParser.useJavaKeyStoreArg.getValue();
      if (defaultPathValue == null)
      {
        defaultPathValue = lastResetKeyStorePath;
      }
      break;
    case JCEKS:
      path = argParser.useJCEKSArg.getValue();
      pathPrompt = INFO_INSTALLDS_PROMPT_JCEKS_PATH.get();
      defaultPathValue = argParser.useJCEKSArg.getValue();
      if (defaultPathValue == null)
      {
        defaultPathValue = lastResetKeyStorePath;
      }
      break;
    case PKCS11:
      path = null;
      defaultPathValue = null;
      pathPrompt = null;
      break;
    case PKCS12:
      path = argParser.usePkcs12Arg.getValue();
      defaultPathValue = argParser.usePkcs12Arg.getValue();
      if (defaultPathValue == null)
      {
        defaultPathValue = lastResetKeyStorePath;
      }
      pathPrompt = INFO_INSTALLDS_PROMPT_PKCS12_PATH.get();
      break;
    case BCFKS:
        path = argParser.useBcfksArg.getValue();
        defaultPathValue = argParser.useBcfksArg.getValue();
        if (defaultPathValue == null)
        {
          defaultPathValue = lastResetKeyStorePath;
        }
        pathPrompt = INFO_INSTALLDS_PROMPT_BCFKS_PATH.get();
        break;
    default:
      throw new IllegalStateException(
          "Called promptIfRequiredCertificate with invalid type: "+type);
    }
    final List<LocalizableMessage> errorMessages = new LinkedList<>();
    final LinkedList<String> keystoreAliases = new LinkedList<>();
    boolean firstTry = true;
    int nPasswordPrompts = 0;

    while (!errorMessages.isEmpty() || firstTry)
    {
      boolean prompted = false;
      if (!errorMessages.isEmpty())
      {
        println();
        println(Utils.getMessageFromCollection(errorMessages,
            formatter.getLineBreak().toString()));
      }

      if (type != SecurityOptions.CertificateType.PKCS11
          && (containsKeyStorePathErrorMessage(errorMessages) || path == null))
      {
        println();
        try
        {
          path = readInput(pathPrompt, defaultPathValue);
        }
        catch (final ClientException ce)
        {
          path = "";
          logger.warn(LocalizableMessage.raw("Error reading input: "+ce, ce));
        }

        prompted = true;
        if (pwd != null)
        {
          errorMessages.clear();
          keystoreAliases.clear();
          checkCertificateInKeystore(type, path, pwd, certNicknames, errorMessages, keystoreAliases);
          if (!errorMessages.isEmpty())
          {
            // Reset password: this might be a new keystore
            pwd = null;
          }
        }
      }
      if (containsKeyStorePasswordErrorMessage(errorMessages) || pwd == null)
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
                ERR_INSTALLDS_TOO_MANY_KEYSTORE_PASSWORD_TRIES.get(LIMIT_KEYSTORE_PASSWORD_PROMPT));
          }
          pwd = String.valueOf(readPassword(INFO_INSTALLDS_PROMPT_KEYSTORE_PASSWORD.get()));
          nPasswordPrompts ++;
        }
      }
      if (containsCertNicknameErrorMessage(errorMessages))
      {
        if (!prompted)
        {
          println();
        }
        certNicknames = promptForCertificateNickname(keystoreAliases);
      }
      errorMessages.clear();
      keystoreAliases.clear();
      checkCertificateInKeystore(type, path, pwd, certNicknames, errorMessages,
          keystoreAliases);
      firstTry = false;
    }
    if (certNicknames.isEmpty() && !keystoreAliases.isEmpty())
    {
      certNicknames = Arrays.asList(keystoreAliases.getFirst());
    }
    switch (type)
    {
    case JKS:
      return SecurityOptions.createJKSCertificateOptions(path, pwd, enableSSL, enableStartTLS, ldapsPort,
          certNicknames);
    case JCEKS:
      return SecurityOptions.createJCEKSCertificateOptions(path, pwd, enableSSL, enableStartTLS, ldapsPort,
          certNicknames);
    case PKCS12:
      return SecurityOptions.createPKCS12CertificateOptions(path, pwd, enableSSL, enableStartTLS, ldapsPort,
          certNicknames);
    case PKCS11:
      return SecurityOptions.createPKCS11CertificateOptions(pwd, enableSSL, enableStartTLS, ldapsPort, certNicknames);
    case BCFKS:
        return SecurityOptions.createBCFKSCertificateOptions(path, pwd, enableSSL, enableStartTLS, ldapsPort,
            certNicknames);
    default:
      throw new IllegalStateException("Called createSecurityOptionsPrompting with invalid type: " + type);
    }
  }

  /**
   * Tells if any of the error messages provided corresponds to a problem with
   * the key store path.
   *
   * @param msgs
   *          the messages to analyze.
   * @return <CODE>true</CODE> if any of the error messages provided corresponds
   *         to a problem with the key store path and <CODE>false</CODE>
   *         otherwise.
   */
  private static boolean containsKeyStorePathErrorMessage(Collection<LocalizableMessage> msgs)
  {
    for (final LocalizableMessage msg : msgs)
    {
      if (StaticUtils.hasDescriptor(msg, INFO_KEYSTORE_PATH_DOES_NOT_EXIST) ||
          StaticUtils.hasDescriptor(msg, INFO_KEYSTORE_PATH_NOT_A_FILE) ||
          StaticUtils.hasDescriptor(msg, INFO_JKS_KEYSTORE_DOES_NOT_EXIST) ||
          StaticUtils.hasDescriptor(msg, INFO_JCEKS_KEYSTORE_DOES_NOT_EXIST) ||
          StaticUtils.hasDescriptor(msg, INFO_PKCS12_KEYSTORE_DOES_NOT_EXIST) ||
          StaticUtils.hasDescriptor(msg, INFO_PKCS11_KEYSTORE_DOES_NOT_EXIST) ||
          StaticUtils.hasDescriptor(msg, INFO_ERROR_ACCESSING_JKS_KEYSTORE) ||
          StaticUtils.hasDescriptor(msg, INFO_ERROR_ACCESSING_JCEKS_KEYSTORE) ||
          StaticUtils.hasDescriptor(msg, INFO_ERROR_ACCESSING_PKCS12_KEYSTORE) ||
          StaticUtils.hasDescriptor(msg, INFO_ERROR_ACCESSING_PKCS11_KEYSTORE))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Tells if any of the error messages provided corresponds to a problem with
   * the key store password.
   *
   * @param msgs
   *          the messages to analyze.
   * @return <CODE>true</CODE> if any of the error messages provided corresponds
   *         to a problem with the key store password and <CODE>false</CODE>
   *         otherwise.
   */
  private static boolean containsKeyStorePasswordErrorMessage(Collection<LocalizableMessage> msgs)
  {
    for (final LocalizableMessage msg : msgs)
    {
      if (StaticUtils.hasDescriptor(msg, INFO_JKS_KEYSTORE_DOES_NOT_EXIST) ||
          StaticUtils.hasDescriptor(msg, INFO_JCEKS_KEYSTORE_DOES_NOT_EXIST) ||
          StaticUtils.hasDescriptor(msg, INFO_PKCS12_KEYSTORE_DOES_NOT_EXIST) ||
          StaticUtils.hasDescriptor(msg, INFO_PKCS11_KEYSTORE_DOES_NOT_EXIST) ||
          StaticUtils.hasDescriptor(msg, INFO_ERROR_ACCESSING_JKS_KEYSTORE) ||
          StaticUtils.hasDescriptor(msg, INFO_ERROR_ACCESSING_JCEKS_KEYSTORE) ||
          StaticUtils.hasDescriptor(msg, INFO_ERROR_ACCESSING_PKCS12_KEYSTORE) ||
          StaticUtils.hasDescriptor(msg, INFO_ERROR_ACCESSING_PKCS11_KEYSTORE) ||
          StaticUtils.hasDescriptor(msg, INFO_ERROR_ACCESSING_KEYSTORE_JDK_BUG))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Tells if any of the error messages provided corresponds to a problem with
   * the certificate nickname.
   *
   * @param msgs
   *          the messages to analyze.
   * @return <CODE>true</CODE> if any of the error messages provided corresponds
   *         to a problem with the certificate nickname and <CODE>false</CODE>
   *         otherwise.
   */
  private static boolean containsCertNicknameErrorMessage(Collection<LocalizableMessage> msgs)
  {
    for (final LocalizableMessage msg : msgs)
    {
      if (StaticUtils.hasDescriptor(msg, ERR_INSTALLDS_CERTNICKNAME_NOT_FOUND) ||
          StaticUtils.hasDescriptor(msg, ERR_INSTALLDS_MUST_PROVIDE_CERTNICKNAME))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Interactively prompts (on standard output) the user to provide an integer
   * value. The answer provided must be parseable as an integer, and may be
   * required to be within a given set of bounds. It will keep prompting until
   * an acceptable value is given.
   *
   * @param prompt
   *          The prompt to present to the user.
   * @param defaultValue
   *          The default value to assume if the user presses ENTER without
   *          typing anything, or <CODE>null</CODE> if there should not be a
   *          default and the user must explicitly provide a value.
   * @param lowerBound
   *          The lower bound that should be enforced, or <CODE>null</CODE> if
   *          there is none.
   * @param upperBound
   *          The upper bound that should be enforced, or <CODE>null</CODE> if
   *          there is none.
   * @return The <CODE>int</CODE> value read from the user input.
   */
  private int promptForInteger(LocalizableMessage prompt, Integer defaultValue, Integer lowerBound, Integer upperBound)
  {
    int returnValue = -1;
    while (returnValue == -1)
    {
      String s;
      try
      {
        s = readInput(prompt, String.valueOf(defaultValue));
      }
      catch (final ClientException ce)
      {
        s = "";
        logger.warn(LocalizableMessage.raw("Error reading input: "+ce, ce));
      }
      if ("".equals(s))
      {
        if (defaultValue == null)
        {
          println(ERR_INSTALLDS_INVALID_INTEGER_RESPONSE.get());
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
          final int intValue = Integer.parseInt(s);
          if (lowerBound != null && intValue < lowerBound)
          {
            println(ERR_INSTALLDS_INTEGER_BELOW_LOWER_BOUND.get(lowerBound));
            println();
          }
          else if (upperBound != null && intValue > upperBound)
          {
            println(ERR_INSTALLDS_INTEGER_ABOVE_UPPER_BOUND.get(upperBound));
            println();
          }
          else
          {
            returnValue = intValue;
          }
        }
        catch (final NumberFormatException nfe)
        {
          println(ERR_INSTALLDS_INVALID_INTEGER_RESPONSE.get());
          println();
        }
      }
    }
    return returnValue;
  }

  /**
   * Prompts the user to accept on the certificates that appears on the list and
   * returns the chosen certificate nickname.
   *
   * @param nicknames
   *          the list of certificates the user must choose from.
   * @return the chosen certificate nickname.
   */
  private Collection<String> promptForCertificateNickname(List<String> nicknames)
  {
    Collection<String> choosenNicknames = new ArrayList<>();
    while (choosenNicknames.isEmpty())
    {
      for (final String n : nicknames)
      {
        try
        {
          if (confirmAction(INFO_INSTALLDS_PROMPT_CERTNICKNAME.get(n), true))
          {
            choosenNicknames.add(n);
          }
        }
        catch (final ClientException ce)
        {
          logger.warn(LocalizableMessage.raw("Error reading input: "+ce, ce));
        }
      }
    }
    return choosenNicknames;
  }

  /**
   * It displays the information provided by the user.
   *
   * @param uData
   *          the UserData that the user provided.
   */
  private void printSummary(UserData uData)
  {
    println();
    println();
    println(INFO_INSTALLDS_SUMMARY.get());
    final LocalizableMessage[] labels =
    {
        INFO_SERVER_PORT_LABEL.get(),
        INFO_ADMIN_CONNECTOR_PORT_LABEL.get(),
        INFO_INSTALLDS_SERVER_JMXPORT_LABEL.get(),
        INFO_SERVER_SECURITY_LABEL.get(),
        INFO_SERVER_DIRECTORY_MANAGER_DN_LABEL.get(),
        INFO_DIRECTORY_DATA_LABEL.get()
    };

    final int jmxPort = uData.getServerJMXPort();

    final LocalizableMessage[] values =
    {
        LocalizableMessage.raw(String.valueOf(uData.getServerPort())),
        LocalizableMessage.raw(String.valueOf(uData.getAdminConnectorPort())),
        LocalizableMessage.raw(jmxPort != -1 ? String.valueOf(jmxPort) : ""),
        LocalizableMessage.raw(
            Utils.getSecurityOptionsString(uData.getSecurityOptions(), false)),
          LocalizableMessage.raw(uData.getDirectoryManagerDn().toString()),
        LocalizableMessage.raw(Utils.getDataDisplayString(uData)),
    };
    int maxWidth = 0;
    for (final LocalizableMessage l : labels)
    {
      maxWidth = Math.max(maxWidth, l.length());
    }

    for (int i=0; i<labels.length; i++)
    {
      StringBuilder sb = new StringBuilder();
      if (values[i] != null)
      {
        final LocalizableMessage l = labels[i];
        sb.append(l).append(" ");

        final String[] lines = values[i].toString().split(Constants.LINE_SEPARATOR);
        for (int j=0; j<lines.length; j++)
        {
          if (j != 0)
          {
            for (int k=0; k <= maxWidth; k++)
            {
              sb.append(" ");
            }
          }
          else
          {
            for (int k=0; k<maxWidth - l.length(); k++)
            {
              sb.append(" ");
            }
          }
          sb.append(lines[j]);
          println(LocalizableMessage.raw(sb));
          sb = new StringBuilder();
        }
      }
    }

    println();
    if (uData.getStartServer())
    {
      println(INFO_INSTALLDS_START_SERVER.get());
    }
    else
    {
      println(INFO_INSTALLDS_DO_NOT_START_SERVER.get());
    }

    if (isWindows())
    {
      if (uData.getEnableWindowsService())
      {
        println(INFO_INSTALLDS_ENABLE_WINDOWS_SERVICE.get());
      }
      else
      {
        println(INFO_INSTALLDS_DO_NOT_ENABLE_WINDOWS_SERVICE.get());
      }
    }
  }

  private void printEquivalentCommandLine(UserData uData)
  {
    println();

    println(INFO_INSTALL_SETUP_EQUIVALENT_COMMAND_LINE.get());
    println();
    final List<String> cmd = Utils.getSetupEquivalentCommandLine(uData);
    println(LocalizableMessage.raw(Utils.getFormattedEquivalentCommandLine(cmd, formatter)));
  }

  /**
   * This method asks the user to confirm to continue the setup. It basically
   * displays the information provided by the user and at the end proposes a
   * menu with the different options to choose from.
   *
   * @return the answer provided by the user: cancel setup, continue setup or
   *         provide information again.
   */
  private ConfirmCode askForConfirmation()
  {
    ConfirmCode returnValue;

    println();
    println();

    final LocalizableMessage[] msgs = new LocalizableMessage[] {
        INFO_INSTALLDS_CONFIRM_INSTALL.get(),
        INFO_INSTALLDS_PROVIDE_DATA_AGAIN.get(),
        INFO_INSTALLDS_PRINT_EQUIVALENT_COMMAND_LINE.get(),
        INFO_INSTALLDS_CANCEL.get()
      };

    final MenuBuilder<ConfirmCode> builder = new MenuBuilder<>(this);
    builder.setPrompt(INFO_INSTALLDS_CONFIRM_INSTALL_PROMPT.get());

    int i=0;
    for (final ConfirmCode code : ConfirmCode.values())
    {
      builder.addNumberedOption(msgs[i], MenuResult.success(code));
      i++;
    }

    builder.setDefault(LocalizableMessage.raw(
            String.valueOf(ConfirmCode.CONTINUE.getReturnCode())),
            MenuResult.success(ConfirmCode.CONTINUE));

    final Menu<ConfirmCode> menu = builder.toMenu();

    try
    {
      final MenuResult<ConfirmCode> m = menu.run();
      if (m.isSuccess())
      {
        returnValue = m.getValue();
      }
      else
      {
        // Should never happen.
        throw new RuntimeException();
      }
    }
    catch (final ClientException ce)
    {
      returnValue = ConfirmCode.CANCEL;
      logger.warn(LocalizableMessage.raw("Error reading input: "+ce, ce));
    }
    return returnValue;
  }

  private void resetArguments(UserData uData)
  {
    argParser = new InstallDSArgumentParser(InstallDS.class.getName());
    try
    {
      argParser.initializeArguments();
      lastResetDirectoryManagerDN = uData.getDirectoryManagerDn();
      lastResetLdapPort = uData.getServerPort();
      lastResetAdminConnectorPort = uData.getAdminConnectorPort();

      final int jmxPort = uData.getServerJMXPort();
      if (jmxPort != -1)
      {
        lastResetJmxPort = jmxPort;
      }

      final LinkedList<String> baseDNs = uData.getNewSuffixOptions().getBaseDns();
      if (!baseDNs.isEmpty())
      {
        lastResetBaseDN = DN.valueOf(baseDNs.getFirst());
      }

      final NewSuffixOptions suffixOptions = uData.getNewSuffixOptions();
      lastResetPopulateOption = suffixOptions.getType();

      if (NewSuffixOptions.Type.IMPORT_AUTOMATICALLY_GENERATED_DATA == lastResetPopulateOption)
      {
        lastResetNumEntries = suffixOptions.getNumberEntries();
      }
      else if (NewSuffixOptions.Type.IMPORT_FROM_LDIF_FILE == lastResetPopulateOption)
      {
        lastResetImportFile = suffixOptions.getLDIFPaths().getFirst();
        lastResetRejectedFile = suffixOptions.getRejectedFile();
        lastResetSkippedFile = suffixOptions.getSkippedFile();
      }

      final SecurityOptions sec = uData.getSecurityOptions();
      if (sec.getEnableSSL())
      {
        lastResetLdapsPort = sec.getSslPort();
      }
      lastResetEnableSSL = sec.getEnableSSL();
      lastResetEnableStartTLS = sec.getEnableStartTLS();
      lastResetCertType = sec.getCertificateType();
      if (SecurityOptions.CertificateType.JKS == lastResetCertType
          || SecurityOptions.CertificateType.JCEKS == lastResetCertType
          || SecurityOptions.CertificateType.PKCS12 == lastResetCertType)
      {
        lastResetKeyStorePath = sec.getKeystorePath();
      }
      else
      {
        lastResetKeyStorePath = null;
      }

      lastResetEnableWindowsService = uData.getEnableWindowsService();
      lastResetStartServer = uData.getStartServer();
      lastResetBackendType = uData.getBackendType();
    }
    catch (final Throwable t)
    {
      logger.warn(LocalizableMessage.raw("Error resetting arguments: " + t, t));
    }
  }

  private String promptForHostNameIfRequired()
  {
    String hostName = null;
    if (argParser.hostNameArg.isPresent())
    {
      hostName = argParser.hostNameArg.getValue();
    }
    else
    {
      println();
      while (hostName == null)
      {
        try
        {
          hostName = readInput(INFO_INSTALLDS_PROMPT_HOST_NAME.get(), argParser.hostNameArg.getDefaultValue());
        }
        catch (final ClientException ce)
        {
          logger.warn(LocalizableMessage.raw("Error reading input: "+ce, ce));
        }
      }
    }
    return hostName;
  }

  /**
   * Returns the timeout to be used to connect in milliseconds. The method must
   * be called after parsing the arguments.
   *
   * @return the timeout to be used to connect in milliseconds. Returns
   *         {@code 0} if there is no timeout.
   */
  private int getConnectTimeout()
  {
    return argParser.getConnectTimeout();
  }
}
