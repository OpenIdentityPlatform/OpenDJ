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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */
package org.opends.server.tools;

import static org.opends.messages.ToolMessages.*;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.util.OperatingSystem.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg1;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.util.Utils;
import org.opends.server.admin.AdministrationConnector;
import org.opends.server.core.DirectoryServer.DirectoryServerVersionHandler;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.CliConstants;
import com.forgerock.opendj.cli.CommonArguments;
import com.forgerock.opendj.cli.FileBasedArgument;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.StringArgument;

/**
 * Class used to parse the arguments of the setup command-line and to check
 * that there are not conflicting arguments (nor missing arguments in no prompt
 * mode).
 * Note that this class does not perform checks involving network (like if
 * a given port is free) nor the validity of the certificate information
 * provided.
 */
public class InstallDSArgumentParser extends ArgumentParser
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  BooleanArgument   testOnlyArg;
  BooleanArgument   cliArg;
  BooleanArgument   addBaseEntryArg;
  BooleanArgument   showUsageArg;
  BooleanArgument   quietArg;
  BooleanArgument   noPromptArg;
  BooleanArgument   verboseArg;
  StringArgument    propertiesFileArgument;
  BooleanArgument   noPropertiesFileArgument;
  BooleanArgument   skipPortCheckArg;
  BooleanArgument   enableWindowsServiceArg;
  BooleanArgument   doNotStartArg;
  BooleanArgument   enableStartTLSArg;
  BooleanArgument   generateSelfSignedCertificateArg;
  StringArgument    hostNameArg;
  BooleanArgument   usePkcs11Arg;
  FileBasedArgument directoryManagerPwdFileArg;
  FileBasedArgument keyStorePasswordFileArg;
  IntegerArgument   ldapPortArg;
  IntegerArgument   adminConnectorPortArg;
  IntegerArgument   ldapsPortArg;
  IntegerArgument   jmxPortArg;
  IntegerArgument   sampleDataArg;
  StringArgument    baseDNArg;
  StringArgument    importLDIFArg;
  StringArgument    rejectedImportFileArg;
  StringArgument    skippedImportFileArg;
  StringArgument    directoryManagerDNArg;
  StringArgument    directoryManagerPwdStringArg;
  StringArgument    useJavaKeyStoreArg;
  StringArgument    useJCEKSArg;
  StringArgument    usePkcs12Arg;
  StringArgument    keyStorePasswordArg;
  StringArgument    certNicknameArg;
  StringArgument    progNameArg;
  IntegerArgument   connectTimeoutArg;
  BooleanArgument   acceptLicense;
  StringArgument    backendTypeArg;

  /**
   * The default constructor for this class.
   * @param mainClassName the class name of the main class for the command-line
   * that is being used.
   */
  public InstallDSArgumentParser(String mainClassName)
  {
    super(mainClassName, INFO_INSTALLDS_TOOL_DESCRIPTION.get(), false);
    setShortToolDescription(REF_SHORT_DESC_SETUP.get());
    setVersionHandler(new DirectoryServerVersionHandler());
  }

  /**
   * Initializes the arguments without parsing them.
   * @throws ArgumentException if there was an error creating or adding the
   * arguments.  If this occurs is likely to be a bug.
   */
  public void initializeArguments() throws ArgumentException
  {
    testOnlyArg = new BooleanArgument(
            OPTION_LONG_TESTONLY_ARGUMENT.toLowerCase(), null,
            OPTION_LONG_TESTONLY_ARGUMENT,
            INFO_ARGUMENT_DESCRIPTION_TESTONLY.get());
    testOnlyArg.setHidden(true);
    testOnlyArg.setPropertyName(OPTION_LONG_TESTONLY_ARGUMENT);
    addArgument(testOnlyArg);

    cliArg = CommonArguments.getCLI();
    addArgument(cliArg);

    String defaultProgName;
    if (isWindows())
    {
      defaultProgName = Installation.WINDOWS_SETUP_FILE_NAME;
    }
    else
    {
      defaultProgName = Installation.UNIX_SETUP_FILE_NAME;
    }
    progNameArg = new StringArgument(
        "programName".toLowerCase(), 'P', "programName", false,
        false, true, INFO_PROGRAM_NAME_PLACEHOLDER.get(), defaultProgName,
        "programName", INFO_INSTALLDS_DESCRIPTION_PROGNAME.get());
    progNameArg.setHidden(true);
    addArgument(progNameArg);

    noPromptArg = CommonArguments.getNoPrompt();
    addArgument(noPromptArg);

    quietArg = CommonArguments.getQuiet();
    addArgument(quietArg);

    verboseArg = CommonArguments.getVerbose();
    addArgument(verboseArg);

    propertiesFileArgument = new StringArgument(
        OPTION_LONG_PROP_FILE_PATH.toLowerCase(), null,
        OPTION_LONG_PROP_FILE_PATH, false,
        false, true, INFO_PROP_FILE_PATH_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_PROP_FILE_PATH.get());
    addArgument(propertiesFileArgument);
    setFilePropertiesArgument(propertiesFileArgument);

    noPropertiesFileArgument = new BooleanArgument(
        OPTION_LONG_NO_PROP_FILE.toLowerCase(), null, OPTION_LONG_NO_PROP_FILE,
        INFO_DESCRIPTION_NO_PROP_FILE.get());
    addArgument(noPropertiesFileArgument);
    setNoPropertiesFileArgument(noPropertiesFileArgument);

    baseDNArg = new StringArgument(
        OPTION_LONG_BASEDN.toLowerCase(), OPTION_SHORT_BASEDN,
        OPTION_LONG_BASEDN, false, true, true,
        INFO_BASEDN_PLACEHOLDER.get(),
        null, OPTION_LONG_BASEDN,
        INFO_INSTALLDS_DESCRIPTION_BASEDN.get());
    addArgument(baseDNArg);

    addBaseEntryArg = new BooleanArgument(
        "addBaseEntry".toLowerCase(), 'a', "addBaseEntry",
        INFO_INSTALLDS_DESCRIPTION_ADDBASE.get());
    addBaseEntryArg.setPropertyName("addBaseEntry");
    addArgument(addBaseEntryArg);

    importLDIFArg = new StringArgument(
        OPTION_LONG_LDIF_FILE.toLowerCase(), OPTION_SHORT_LDIF_FILE,
        OPTION_LONG_LDIF_FILE, false,
        true, true, INFO_LDIFFILE_PLACEHOLDER.get(),
        null, OPTION_LONG_LDIF_FILE,
        INFO_INSTALLDS_DESCRIPTION_IMPORTLDIF.get());
    addArgument(importLDIFArg);

    rejectedImportFileArg = new StringArgument(
        "rejectFile".toLowerCase(), 'R', "rejectFile", false, false,
        true, INFO_REJECT_FILE_PLACEHOLDER.get(), null, "rejectFile",
        INFO_INSTALLDS_DESCRIPTION_REJECTED_FILE.get());
    addArgument(rejectedImportFileArg);

    skippedImportFileArg = new StringArgument(
        "skipFile".toLowerCase(), null, "skipFile", false, false,
        true, INFO_SKIP_FILE_PLACEHOLDER.get(), null, "skipFile",
        INFO_INSTALLDS_DESCRIPTION_SKIPPED_FILE.get());
    addArgument(skippedImportFileArg);

    sampleDataArg = new IntegerArgument(
        "sampleData".toLowerCase(), 'd', "sampleData", false,
        false, true, INFO_NUM_ENTRIES_PLACEHOLDER.get(), 0, "sampleData",
        true, 0, false, 0,
        INFO_INSTALLDS_DESCRIPTION_SAMPLE_DATA.get());
    addArgument(sampleDataArg);

    int defaultLdapPort = UserData.getDefaultPort();
    if (defaultLdapPort == -1)
    {
      defaultLdapPort = 389;
    }
    ldapPortArg = new IntegerArgument(
        "ldapPort".toLowerCase(), OPTION_SHORT_PORT,
        "ldapPort", false, false,
        true, INFO_PORT_PLACEHOLDER.get(), defaultLdapPort,
        "ldapPort", true, 1, true, 65535,
        INFO_INSTALLDS_DESCRIPTION_LDAPPORT.get());
    addArgument(ldapPortArg);

    int defaultAdminPort = UserData.getDefaultAdminConnectorPort();
    if (defaultAdminPort == -1)
    {
      defaultAdminPort =
        AdministrationConnector.DEFAULT_ADMINISTRATION_CONNECTOR_PORT;
    }
    adminConnectorPortArg = new IntegerArgument(
        "adminConnectorPort".toLowerCase(), null,
        "adminConnectorPort", false, false,
        true, INFO_PORT_PLACEHOLDER.get(), defaultAdminPort,
        "adminConnectorPort", true, 1, true, 65535,
        INFO_INSTALLDS_DESCRIPTION_ADMINCONNECTORPORT.get());
    addArgument(adminConnectorPortArg);

    jmxPortArg = new IntegerArgument(
        "jmxPort".toLowerCase(), 'x', "jmxPort", false, false,
        true, INFO_JMXPORT_PLACEHOLDER.get(),
        CliConstants.DEFAULT_JMX_PORT, "jmxPort", true,
        1, true, 65535,
        INFO_INSTALLDS_DESCRIPTION_JMXPORT.get());
    addArgument(jmxPortArg);

    skipPortCheckArg = new BooleanArgument(
        "skipPortCheck".toLowerCase(), 'S', "skipPortCheck",
        INFO_INSTALLDS_DESCRIPTION_SKIPPORT.get());
    skipPortCheckArg.setPropertyName("skipPortCheck");
    addArgument(skipPortCheckArg);

    directoryManagerDNArg = new StringArgument(
        OPTION_LONG_ROOT_USER_DN.toLowerCase(), OPTION_SHORT_ROOT_USER_DN,
        OPTION_LONG_ROOT_USER_DN, false, false,
        true, INFO_ROOT_USER_DN_PLACEHOLDER.get(),
        "cn=Directory Manager",
        OPTION_LONG_ROOT_USER_DN, INFO_INSTALLDS_DESCRIPTION_ROOTDN.get());
    addArgument(directoryManagerDNArg);

    directoryManagerPwdStringArg = new StringArgument(
        "rootUserPassword".toLowerCase(), OPTION_SHORT_BINDPWD,
        "rootUserPassword",
        false, false, true,
        INFO_ROOT_USER_PWD_PLACEHOLDER.get(), null,
        "rootUserPassword",
        INFO_INSTALLDS_DESCRIPTION_ROOTPW.get());
    addArgument(directoryManagerPwdStringArg);

    directoryManagerPwdFileArg = new FileBasedArgument(
        "rootUserPasswordFile".toLowerCase(),
        OPTION_SHORT_BINDPWD_FILE,
        "rootUserPasswordFile", false, false,
        INFO_ROOT_USER_PWD_FILE_PLACEHOLDER.get(),
        null, "rootUserPasswordFile",
        INFO_INSTALLDS_DESCRIPTION_ROOTPWFILE.get());
    addArgument(directoryManagerPwdFileArg);

    enableWindowsServiceArg = new BooleanArgument(
        "enableWindowsService".toLowerCase(), 'e',
        "enableWindowsService",
        INFO_INSTALLDS_DESCRIPTION_ENABLE_WINDOWS_SERVICE.get());
    enableWindowsServiceArg.setPropertyName("enableWindowsService");
    if (isWindows())
    {
      addArgument(enableWindowsServiceArg);
    }

    doNotStartArg = new BooleanArgument(
        "doNotStart".toLowerCase(), 'O', "doNotStart",
        INFO_INSTALLDS_DESCRIPTION_DO_NOT_START.get());
    doNotStartArg.setPropertyName("doNotStart");
    addArgument(doNotStartArg);

    enableStartTLSArg = new BooleanArgument(
        "enableStartTLS".toLowerCase(), OPTION_SHORT_START_TLS,
        "enableStartTLS",
        INFO_INSTALLDS_DESCRIPTION_ENABLE_STARTTLS.get());
    enableStartTLSArg.setPropertyName("enableStartTLS");
    addArgument(enableStartTLSArg);

    int defaultSecurePort = UserData.getDefaultSslPort(defaultLdapPort);
    if (defaultSecurePort == -1)
    {
      defaultSecurePort = 636;
    }
    ldapsPortArg = new IntegerArgument(
        "ldapsPort".toLowerCase(), OPTION_SHORT_USE_SSL,
        "ldapsPort", false, false,
        true, INFO_PORT_PLACEHOLDER.get(), defaultSecurePort,
        "ldapsPort", true, 1, true, 65535,
        INFO_INSTALLDS_DESCRIPTION_LDAPSPORT.get());
    addArgument(ldapsPortArg);

    generateSelfSignedCertificateArg = new BooleanArgument(
        "generateSelfSignedCertificate".toLowerCase(),
        null, "generateSelfSignedCertificate",
        INFO_INSTALLDS_DESCRIPTION_USE_SELF_SIGNED.get());
    generateSelfSignedCertificateArg.setPropertyName(
        "generateSelfSignedCertificate");
    addArgument(generateSelfSignedCertificateArg);

    hostNameArg = new StringArgument(OPTION_LONG_HOST.toLowerCase(),
        OPTION_SHORT_HOST,
        OPTION_LONG_HOST, false, false, true, INFO_HOST_PLACEHOLDER.get(),
        UserData.getDefaultHostName(),
        null, INFO_INSTALLDS_DESCRIPTION_HOST_NAME.get());
    hostNameArg.setPropertyName(OPTION_LONG_HOST);
    addDefaultArgument(hostNameArg);

    usePkcs11Arg = new BooleanArgument("usePkcs11Keystore".toLowerCase(),
        null, "usePkcs11Keystore",
        INFO_INSTALLDS_DESCRIPTION_USE_PKCS11.get());
    usePkcs11Arg.setPropertyName("usePkcs11Keystore");
    addArgument(usePkcs11Arg);

    useJavaKeyStoreArg = new StringArgument("useJavaKeystore".toLowerCase(),
        null, "useJavaKeystore", false, false,
        true, INFO_KEYSTOREPATH_PLACEHOLDER.get(), null, "useJavaKeystore",
        INFO_INSTALLDS_DESCRIPTION_USE_JAVAKEYSTORE.get());
    addArgument(useJavaKeyStoreArg);

    useJCEKSArg = new StringArgument("useJCEKS".toLowerCase(),
        null, "useJCEKS", false, false,
        true, INFO_KEYSTOREPATH_PLACEHOLDER.get(), null, "useJCEKS",
        INFO_INSTALLDS_DESCRIPTION_USE_JCEKS.get());
    addArgument(useJCEKSArg);

    usePkcs12Arg = new StringArgument("usePkcs12keyStore".toLowerCase(),
        null, "usePkcs12keyStore", false, false,
        true, INFO_KEYSTOREPATH_PLACEHOLDER.get(), null, "usePkcs12keyStore",
        INFO_INSTALLDS_DESCRIPTION_USE_PKCS12.get());
    addArgument(usePkcs12Arg);

    keyStorePasswordArg = new StringArgument(
        OPTION_LONG_KEYSTORE_PWD.toLowerCase(),
        OPTION_SHORT_KEYSTORE_PWD,
        OPTION_LONG_KEYSTORE_PWD, false, false, true,
        INFO_KEYSTORE_PWD_PLACEHOLDER.get(), null, OPTION_LONG_KEYSTORE_PWD,
        INFO_INSTALLDS_DESCRIPTION_KEYSTOREPASSWORD.get());
    addDefaultArgument(keyStorePasswordArg);

    keyStorePasswordFileArg = new FileBasedArgument(
        OPTION_LONG_KEYSTORE_PWD_FILE.toLowerCase(),
        OPTION_SHORT_KEYSTORE_PWD_FILE, OPTION_LONG_KEYSTORE_PWD_FILE, false,
        false, INFO_KEYSTORE_PWD_FILE_PLACEHOLDER.get(), null,
        OPTION_LONG_KEYSTORE_PWD_FILE,
        INFO_INSTALLDS_DESCRIPTION_KEYSTOREPASSWORD_FILE.get());
    addDefaultArgument(keyStorePasswordFileArg);

    certNicknameArg = new StringArgument(
        OPTION_LONG_CERT_NICKNAME.toLowerCase(),
        OPTION_SHORT_CERT_NICKNAME, OPTION_LONG_CERT_NICKNAME,
        false, false, true, INFO_NICKNAME_PLACEHOLDER.get(), null,
        OPTION_LONG_CERT_NICKNAME,
        INFO_INSTALLDS_DESCRIPTION_CERT_NICKNAME.get());
    addDefaultArgument(certNicknameArg);

    connectTimeoutArg = CommonArguments.getConnectTimeOut();
    addArgument(connectTimeoutArg);

    acceptLicense = CommonArguments.getAcceptLicense();
    addArgument(acceptLicense);

    showUsageArg = CommonArguments.getShowUsage();
    addArgument(showUsageArg);
    setUsageArgument(showUsageArg);

    backendTypeArg = new StringArgument(
        OPTION_LONG_BACKEND_TYPE.toLowerCase(),
        OPTION_SHORT_BACKEND_TYPE, OPTION_LONG_BACKEND_TYPE,
        false, false, true, INFO_INSTALLDS_BACKEND_TYPE_PLACEHOLDER.get(),
        BackendTypeHelper.filterSchemaBackendName(
            new BackendTypeHelper().getBackendTypes().get(0).getName()),
        OPTION_LONG_BACKEND_TYPE,
        INFO_INSTALLDS_DESCRIPTION_BACKEND_TYPE.get()
    );
    addArgument(backendTypeArg);
  }

  /**
   * Returns whether the command was launched in CLI mode or not.
   * @return <CODE>true</CODE> if the command was launched to use CLI mode and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isCli()
  {
    return cliArg.isPresent();
  }

  /** {@inheritDoc} */
  @Override
  public void parseArguments(String[] args) throws ArgumentException
  {
    LinkedHashSet<LocalizableMessage> errorMessages = new LinkedHashSet<>();
    try
    {
      super.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      logger.error(LocalizableMessage.raw("Error parsing arguments: "+ae, ae));
      errorMessages.add(ae.getMessageObject());
    }

    if (!isUsageArgumentPresent() && !isVersionArgumentPresent())
    {
      checkServerPassword(errorMessages);
      checkProvidedPorts(errorMessages);
      checkImportDataArguments(errorMessages);
      checkSecurityArguments(errorMessages);

      if (!errorMessages.isEmpty())
      {
        throw new ArgumentException(ERR_CANNOT_INITIALIZE_ARGS.get(
            Utils.getMessageFromCollection(errorMessages, Constants.LINE_SEPARATOR)));
      }
    }
  }

  /**
   * Returns the directory manager password provided by the user.  This method
   * should be called after a call to parseArguments.
   * @return the directory manager password provided by the user.
   */
  public String getDirectoryManagerPassword()
  {
    if (directoryManagerPwdStringArg.isPresent())
    {
      return directoryManagerPwdStringArg.getValue();
    }
    else if (directoryManagerPwdFileArg.isPresent())
    {
      return directoryManagerPwdFileArg.getValue();
    }
    return null;
  }

  /**
   * Returns the key store password provided by the user.  This method should be
   * called after a call to parseArguments.
   * @return the key store password provided by the user.
   */
  public String getKeyStorePassword()
  {
    if (keyStorePasswordArg.isPresent())
    {
      return keyStorePasswordArg.getValue();
    }
    else if (keyStorePasswordFileArg.isPresent())
    {
      return keyStorePasswordFileArg.getValue();
    }
    return null;
  }

  /**
   * Checks that there are no conflicts with the directory manager passwords.
   * If we are in no prompt mode, check that the password was provided.
   * @param errorMessages the list of messages to which we add the error
   * messages describing the problems encountered during the execution of the
   * checking.
   */
  private void checkServerPassword(Collection<LocalizableMessage> errorMessages)
  {
    if (directoryManagerPwdStringArg.isPresent() &&
        directoryManagerPwdFileArg.isPresent())
    {
      errorMessages.add(ERR_INSTALLDS_TWO_CONFLICTING_ARGUMENTS.get(
          directoryManagerPwdStringArg.getLongIdentifier(),
          directoryManagerPwdFileArg.getLongIdentifier()));
    }

    if (noPromptArg.isPresent() && !directoryManagerPwdStringArg.isPresent() &&
        !directoryManagerPwdFileArg.isPresent())
    {
      errorMessages.add(ERR_INSTALLDS_NO_ROOT_PASSWORD.get(
          directoryManagerPwdStringArg.getLongIdentifier(),
          directoryManagerPwdFileArg.getLongIdentifier()));
    }
  }

  /**
   * Checks that there are no conflicts with the provided ports (like if the
   * user provided the same port for different protocols).
   * @param errorMessages the list of messages to which we add the error
   * messages describing the problems encountered during the execution of the
   * checking.
   */
  private void checkProvidedPorts(Collection<LocalizableMessage> errorMessages)
  {
    try
    {
      Set<Integer> ports = new HashSet<>();
      ports.add(ldapPortArg.getIntValue());

      checkPortAlreadyUsed(ports, adminConnectorPortArg.getIntValue(), errorMessages,
          ERR_CONFIGDS_PORT_ALREADY_SPECIFIED);
      if (jmxPortArg.isPresent())
      {
        checkPortAlreadyUsed(ports, jmxPortArg.getIntValue(), errorMessages, ERR_CONFIGDS_PORT_ALREADY_SPECIFIED);
      }
      if (ldapsPortArg.isPresent())
      {
        checkPortAlreadyUsed(ports, ldapsPortArg.getIntValue(), errorMessages, ERR_CONFIGDS_PORT_ALREADY_SPECIFIED);
      }
    }
    catch (ArgumentException ae)
    {
      logger.error(LocalizableMessage.raw("Unexpected error.  "+
          "Assuming that it is caused by a previous parsing issue: "+ae, ae));
    }
  }

  private void checkPortAlreadyUsed(Set<Integer> ports, int port, Collection<LocalizableMessage> errorMessages,
      Arg1<Object> errorMsg)
  {
    if (ports.contains(port))
    {
      errorMessages.add(errorMsg.get(port));
    }
    else
    {
      ports.add(port);
    }
  }

  /**
   * Checks that there are no conflicts with the import data arguments.
   *
   * @param errorMessages
   *          the list of messages to which we add the error messages describing
   *          the problems encountered during the execution of the checking.
   */
  private void checkImportDataArguments(Collection<LocalizableMessage> errorMessages)
  {
    //  Make sure that the user didn't provide conflicting arguments.
    if (addBaseEntryArg.isPresent())
    {
      if (importLDIFArg.isPresent())
      {
        errorMessages.add(conflictingArgs(addBaseEntryArg, importLDIFArg));
      }
      else if (sampleDataArg.isPresent())
      {
        errorMessages.add(conflictingArgs(addBaseEntryArg, sampleDataArg));
      }
    }
    else if (importLDIFArg.isPresent() && sampleDataArg.isPresent())
    {
      errorMessages.add(conflictingArgs(importLDIFArg, sampleDataArg));
    }

    if (rejectedImportFileArg.isPresent() && addBaseEntryArg.isPresent())
    {
      errorMessages.add(conflictingArgs(addBaseEntryArg, rejectedImportFileArg));
    }
    else if (rejectedImportFileArg.isPresent() && sampleDataArg.isPresent())
    {
      errorMessages.add(conflictingArgs(rejectedImportFileArg, sampleDataArg));
    }

    if (skippedImportFileArg.isPresent() && addBaseEntryArg.isPresent())
    {
      errorMessages.add(conflictingArgs(addBaseEntryArg, skippedImportFileArg));
    }
    else if (skippedImportFileArg.isPresent() && sampleDataArg.isPresent())
    {
      errorMessages.add(conflictingArgs(skippedImportFileArg, sampleDataArg));
    }

    final boolean noBaseDNProvided = !baseDNArg.isPresent() && baseDNArg.getDefaultValue() == null;
    if (noPromptArg.isPresent() && noBaseDNProvided)
    {
      final Argument[] args = {importLDIFArg, addBaseEntryArg, sampleDataArg, backendTypeArg};
      for (Argument arg : args)
      {
        if (arg.isPresent())
        {
          errorMessages.add(ERR_INSTALLDS_NO_BASE_DN_AND_CONFLICTING_ARG.get("--" + arg.getLongIdentifier()));
        }
      }
    }
  }

  private LocalizableMessage conflictingArgs(Argument arg1, Argument arg2)
  {
    return ERR_TOOL_CONFLICTING_ARGS.get(arg1.getLongIdentifier(), arg2.getLongIdentifier());
  }

  /**
   * Checks that there are no conflicts with the security arguments.
   * If we are in no prompt mode, check that all the information required has
   * been provided (but not if this information is valid: we do not try to
   * open the keystores or to check that the LDAPS port is in use).
   * @param errorMessages the list of messages to which we add the error
   * messages describing the problems encountered during the execution of the
   * checking.
   */
  private void checkSecurityArguments(Collection<LocalizableMessage> errorMessages)
  {
    boolean certificateRequired = ldapsPortArg.isPresent() || enableStartTLSArg.isPresent();

    int certificateType = 0;
    if (generateSelfSignedCertificateArg.isPresent())
    {
      certificateType++;
    }
    if (useJavaKeyStoreArg.isPresent())
    {
      certificateType++;
    }
    if (useJCEKSArg.isPresent())
    {
      certificateType++;
    }
    if (usePkcs11Arg.isPresent())
    {
      certificateType++;
    }
    if (usePkcs12Arg.isPresent())
    {
      certificateType++;
    }

    if (certificateType > 1)
    {
      errorMessages.add(ERR_INSTALLDS_SEVERAL_CERTIFICATE_TYPE_SPECIFIED.get());
    }

    if (certificateRequired && noPromptArg.isPresent() && certificateType == 0)
    {
      errorMessages.add(
          ERR_INSTALLDS_CERTIFICATE_REQUIRED_FOR_SSL_OR_STARTTLS.get());
    }

    if (certificateType == 1)
    {
      if (!generateSelfSignedCertificateArg.isPresent())
      {
        // Check that we have only a password.
        if (keyStorePasswordArg.isPresent() &&
            keyStorePasswordFileArg.isPresent())
        {
          errorMessages.add(ERR_INSTALLDS_TWO_CONFLICTING_ARGUMENTS.get(
              keyStorePasswordArg.getLongIdentifier(),
              keyStorePasswordFileArg.getLongIdentifier()));
        }

        // Check that we have one password in no prompt mode.
        if (noPromptArg.isPresent() && !keyStorePasswordArg.isPresent() &&
            !keyStorePasswordFileArg.isPresent())
        {
          errorMessages.add(ERR_INSTALLDS_NO_KEYSTORE_PASSWORD.get(
              keyStorePasswordArg.getLongIdentifier(),
              keyStorePasswordFileArg.getLongIdentifier()));
        }
      }
      if (noPromptArg.isPresent() && !ldapsPortArg.isPresent() &&
          !enableStartTLSArg.isPresent())
      {
        errorMessages.add(ERR_INSTALLDS_SSL_OR_STARTTLS_REQUIRED.get(
            ldapsPortArg.getLongIdentifier(),
            enableStartTLSArg.getLongIdentifier()));
      }
    }
  }

  /**
   * Returns the timeout to be used to connect in milliseconds.  The method
   * must be called after parsing the arguments.
   * @return the timeout to be used to connect in milliseconds.  Returns
   * {@code 0} if there is no timeout.
   * @throws IllegalStateException if the method is called before
   * parsing the arguments.
   */
  public int getConnectTimeout() throws IllegalStateException
  {
    try
    {
      return connectTimeoutArg.getIntValue();
    }
    catch (ArgumentException ae)
    {
      throw new IllegalStateException("Argument parser is not parsed: "+ae, ae);
    }
  }
}
