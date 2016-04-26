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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.tools;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.CliMessages.INFO_JMXPORT_PLACEHOLDER;
import static com.forgerock.opendj.cli.CliMessages.INFO_KEYSTORE_PWD_FILE_PLACEHOLDER;
import static com.forgerock.opendj.cli.CliMessages.INFO_NUM_ENTRIES_PLACEHOLDER;
import static com.forgerock.opendj.cli.CliMessages.INFO_PORT_PLACEHOLDER;
import static com.forgerock.opendj.cli.CliMessages.INFO_ROOT_USER_PWD_FILE_PLACEHOLDER;
import static com.forgerock.opendj.cli.CommonArguments.*;
import static com.forgerock.opendj.cli.Utils.addErrorMessageIfArgumentsConflict;
import static com.forgerock.opendj.util.OperatingSystem.*;

import static org.opends.messages.ToolMessages.*;

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
import org.opends.server.config.AdministrationConnector;
import org.opends.server.core.DirectoryServer.DirectoryServerVersionHandler;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.CliConstants;
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

  private BooleanArgument cliArg;
  BooleanArgument   addBaseEntryArg;
  private BooleanArgument showUsageArg;
  BooleanArgument   quietArg;
  BooleanArgument   noPromptArg;
  BooleanArgument   verboseArg;
  private StringArgument propertiesFileArgument;
  private BooleanArgument noPropertiesFileArgument;
  BooleanArgument   skipPortCheckArg;
  BooleanArgument   enableWindowsServiceArg;
  BooleanArgument   doNotStartArg;
  BooleanArgument   enableStartTLSArg;
  BooleanArgument   generateSelfSignedCertificateArg;
  StringArgument    hostNameArg;
  BooleanArgument   usePkcs11Arg;
  private FileBasedArgument directoryManagerPwdFileArg;
  private FileBasedArgument keyStorePasswordFileArg;
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
  private StringArgument directoryManagerPwdStringArg;
  StringArgument    useJavaKeyStoreArg;
  StringArgument    useJCEKSArg;
  StringArgument    usePkcs12Arg;
  private StringArgument keyStorePasswordArg;
  StringArgument    certNicknameArg;
  private StringArgument progNameArg;
  private IntegerArgument connectTimeoutArg;
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
    cliArg = cliArgument();
    addArgument(cliArg);

    progNameArg = StringArgument.builder("programName")
            .shortIdentifier('P')
            .description(INFO_INSTALLDS_DESCRIPTION_PROGNAME.get())
            .hidden()
            .defaultValue(Installation.getSetupFileName())
            .valuePlaceholder(INFO_PROGRAM_NAME_PLACEHOLDER.get())
            .buildArgument();
    addArgument(progNameArg);

    noPromptArg = noPromptArgument();
    addArgument(noPromptArg);

    quietArg = quietArgument();
    addArgument(quietArg);

    verboseArg = verboseArgument();
    addArgument(verboseArg);

    propertiesFileArgument =
            StringArgument.builder(OPTION_LONG_PROP_FILE_PATH)
                    .description(INFO_DESCRIPTION_PROP_FILE_PATH.get())
                    .valuePlaceholder(INFO_PROP_FILE_PATH_PLACEHOLDER.get())
                    .buildArgument();
    addArgument(propertiesFileArgument);
    setFilePropertiesArgument(propertiesFileArgument);

    noPropertiesFileArgument =
            BooleanArgument.builder(OPTION_LONG_NO_PROP_FILE)
                    .description(INFO_DESCRIPTION_NO_PROP_FILE.get())
                    .buildArgument();
    addArgument(noPropertiesFileArgument);
    setNoPropertiesFileArgument(noPropertiesFileArgument);

    baseDNArg =
            StringArgument.builder(OPTION_LONG_BASEDN)
                    .shortIdentifier(OPTION_SHORT_BASEDN)
                    .description(INFO_INSTALLDS_DESCRIPTION_BASEDN.get())
                    .multiValued()
                    .valuePlaceholder(INFO_BASEDN_PLACEHOLDER.get())
                    .buildArgument();
    addArgument(baseDNArg);

    addBaseEntryArg =
            BooleanArgument.builder("addBaseEntry")
                    .shortIdentifier('a')
                    .description(INFO_INSTALLDS_DESCRIPTION_ADDBASE.get())
                    .buildArgument();
    addArgument(addBaseEntryArg);

    importLDIFArg =
            StringArgument.builder(OPTION_LONG_LDIF_FILE)
                    .shortIdentifier(OPTION_SHORT_LDIF_FILE)
                    .description(INFO_INSTALLDS_DESCRIPTION_IMPORTLDIF.get())
                    .multiValued()
                    .valuePlaceholder(INFO_LDIFFILE_PLACEHOLDER.get())
                    .buildArgument();
    addArgument(importLDIFArg);

    rejectedImportFileArg =
            StringArgument.builder("rejectFile")
                    .shortIdentifier('R')
                    .description(INFO_INSTALLDS_DESCRIPTION_REJECTED_FILE.get())
                    .valuePlaceholder(INFO_REJECT_FILE_PLACEHOLDER.get())
                    .buildArgument();
    addArgument(rejectedImportFileArg);

    skippedImportFileArg =
            StringArgument.builder("skipFile")
                    .description(INFO_INSTALLDS_DESCRIPTION_SKIPPED_FILE.get())
                    .valuePlaceholder(INFO_SKIP_FILE_PLACEHOLDER.get())
                    .buildArgument();
    addArgument(skippedImportFileArg);

    sampleDataArg =
            IntegerArgument.builder("sampleData")
                    .shortIdentifier('d')
                    .description(INFO_INSTALLDS_DESCRIPTION_SAMPLE_DATA.get())
                    .lowerBound(0)
                    .defaultValue(0)
                    .valuePlaceholder(INFO_NUM_ENTRIES_PLACEHOLDER.get())
                    .buildArgument();
    addArgument(sampleDataArg);

    int defaultLdapPort = UserData.getDefaultPort();
    if (defaultLdapPort == -1)
    {
      defaultLdapPort = 389;
    }

    ldapPortArg =
            IntegerArgument.builder("ldapPort")
                    .shortIdentifier(OPTION_SHORT_PORT)
                    .description(INFO_INSTALLDS_DESCRIPTION_LDAPPORT.get())
                    .range(1, 65535)
                    .defaultValue(defaultLdapPort)
                    .valuePlaceholder(INFO_PORT_PLACEHOLDER.get())
                    .buildArgument();
    addArgument(ldapPortArg);

    int defaultAdminPort = UserData.getDefaultAdminConnectorPort();
    if (defaultAdminPort == -1)
    {
      defaultAdminPort =
        AdministrationConnector.DEFAULT_ADMINISTRATION_CONNECTOR_PORT;
    }

    adminConnectorPortArg =
            IntegerArgument.builder("adminConnectorPort")
                    .description(INFO_INSTALLDS_DESCRIPTION_ADMINCONNECTORPORT.get())
                    .range(1, 65535)
                    .defaultValue(defaultAdminPort)
                    .valuePlaceholder(INFO_PORT_PLACEHOLDER.get())
                    .buildArgument();
    addArgument(adminConnectorPortArg);

    jmxPortArg =
            IntegerArgument.builder("jmxPort")
                    .shortIdentifier('x')
                    .description(INFO_INSTALLDS_DESCRIPTION_JMXPORT.get())
                    .range(1, 65535)
                    .defaultValue(CliConstants.DEFAULT_JMX_PORT)
                    .valuePlaceholder(INFO_JMXPORT_PLACEHOLDER.get())
                    .buildArgument();
    addArgument(jmxPortArg);

    skipPortCheckArg =
            BooleanArgument.builder("skipPortCheck")
                    .shortIdentifier('S')
                    .description(INFO_INSTALLDS_DESCRIPTION_SKIPPORT.get())
                    .buildArgument();
    addArgument(skipPortCheckArg);

    directoryManagerDNArg =
            StringArgument.builder(OPTION_LONG_ROOT_USER_DN)
                    .shortIdentifier(OPTION_SHORT_ROOT_USER_DN)
                    .description(INFO_INSTALLDS_DESCRIPTION_ROOTDN.get())
                    .defaultValue("cn=Directory Manager")
                    .valuePlaceholder(INFO_ROOT_USER_DN_PLACEHOLDER.get())
                    .buildArgument();
    addArgument(directoryManagerDNArg);

    directoryManagerPwdStringArg =
            StringArgument.builder("rootUserPassword")
                    .shortIdentifier(OPTION_SHORT_BINDPWD)
                    .description(INFO_INSTALLDS_DESCRIPTION_ROOTPW.get())
                    .valuePlaceholder(INFO_ROOT_USER_PWD_PLACEHOLDER.get())
                    .buildArgument();
    addArgument(directoryManagerPwdStringArg);

    directoryManagerPwdFileArg =
            FileBasedArgument.builder("rootUserPasswordFile")
                    .shortIdentifier(OPTION_SHORT_BINDPWD_FILE)
                    .description(INFO_INSTALLDS_DESCRIPTION_ROOTPWFILE.get())
                    .valuePlaceholder(INFO_ROOT_USER_PWD_FILE_PLACEHOLDER.get())
                    .buildArgument();
    addArgument(directoryManagerPwdFileArg);

    enableWindowsServiceArg =
            BooleanArgument.builder("enableWindowsService")
                    .shortIdentifier('e')
                    .description(INFO_INSTALLDS_DESCRIPTION_ENABLE_WINDOWS_SERVICE.get())
                    .buildArgument();
    if (isWindows())
    {
      addArgument(enableWindowsServiceArg);
    }

    doNotStartArg =
            BooleanArgument.builder("doNotStart")
                    .shortIdentifier('O')
                    .description(INFO_INSTALLDS_DESCRIPTION_DO_NOT_START.get())
                    .buildArgument();
    addArgument(doNotStartArg);

    enableStartTLSArg =
            BooleanArgument.builder("enableStartTLS")
                    .shortIdentifier(OPTION_SHORT_START_TLS)
                    .description(INFO_INSTALLDS_DESCRIPTION_ENABLE_STARTTLS.get())
                    .buildArgument();
    addArgument(enableStartTLSArg);

    int defaultSecurePort = UserData.getDefaultSslPort(defaultLdapPort);
    if (defaultSecurePort == -1)
    {
      defaultSecurePort = 636;
    }

    ldapsPortArg =
            IntegerArgument.builder("ldapsPort")
                    .shortIdentifier(OPTION_SHORT_USE_SSL)
                    .description(INFO_INSTALLDS_DESCRIPTION_LDAPSPORT.get())
                    .range(1, 65535)
                    .defaultValue(defaultSecurePort)
                    .valuePlaceholder(INFO_PORT_PLACEHOLDER.get())
                    .buildArgument();
    addArgument(ldapsPortArg);

    generateSelfSignedCertificateArg =
            BooleanArgument.builder("generateSelfSignedCertificate")
                    .description(INFO_INSTALLDS_DESCRIPTION_USE_SELF_SIGNED.get())
                    .buildArgument();
    addArgument(generateSelfSignedCertificateArg);

    hostNameArg =
            StringArgument.builder(OPTION_LONG_HOST)
                    .shortIdentifier(OPTION_SHORT_HOST)
                    .description(INFO_INSTALLDS_DESCRIPTION_HOST_NAME.get())
                    .defaultValue(UserData.getDefaultHostName())
                    .valuePlaceholder(INFO_HOST_PLACEHOLDER.get())
                    .buildArgument();
    addDefaultArgument(hostNameArg);

    usePkcs11Arg =
            BooleanArgument.builder("usePkcs11Keystore")
                    .description(INFO_INSTALLDS_DESCRIPTION_USE_PKCS11.get())
                    .buildArgument();
    addArgument(usePkcs11Arg);

    useJavaKeyStoreArg =
            StringArgument.builder("useJavaKeystore")
                    .description(INFO_INSTALLDS_DESCRIPTION_USE_JAVAKEYSTORE.get())
                    .valuePlaceholder(INFO_KEYSTOREPATH_PLACEHOLDER.get())
                    .buildArgument();
    addArgument(useJavaKeyStoreArg);

    useJCEKSArg =
            StringArgument.builder("useJCEKS")
                    .description(INFO_INSTALLDS_DESCRIPTION_USE_JCEKS.get())
                    .valuePlaceholder(INFO_KEYSTOREPATH_PLACEHOLDER.get())
                    .buildArgument();
    addArgument(useJCEKSArg);

    usePkcs12Arg =
            StringArgument.builder("usePkcs12keyStore")
                    .description(INFO_INSTALLDS_DESCRIPTION_USE_PKCS12.get())
                    .valuePlaceholder(INFO_KEYSTOREPATH_PLACEHOLDER.get())
                    .buildArgument();
    addArgument(usePkcs12Arg);

    keyStorePasswordArg =
            StringArgument.builder(OPTION_LONG_KEYSTORE_PWD)
                    .shortIdentifier(OPTION_SHORT_KEYSTORE_PWD)
                    .description(INFO_INSTALLDS_DESCRIPTION_KEYSTOREPASSWORD.get())
                    .valuePlaceholder(INFO_KEYSTORE_PWD_PLACEHOLDER.get())
                    .buildArgument();
    addDefaultArgument(keyStorePasswordArg);

    keyStorePasswordFileArg =
            FileBasedArgument.builder(OPTION_LONG_KEYSTORE_PWD_FILE)
                    .shortIdentifier(OPTION_SHORT_KEYSTORE_PWD_FILE)
                    .description(INFO_INSTALLDS_DESCRIPTION_KEYSTOREPASSWORD_FILE.get())
                    .valuePlaceholder(INFO_KEYSTORE_PWD_FILE_PLACEHOLDER.get())
                    .buildArgument();
    addDefaultArgument(keyStorePasswordFileArg);

    certNicknameArg =
            StringArgument.builder(OPTION_LONG_CERT_NICKNAME)
                    .shortIdentifier(OPTION_SHORT_CERT_NICKNAME)
                    .description(INFO_INSTALLDS_DESCRIPTION_CERT_NICKNAME.get())
                    .multiValued()
                    .valuePlaceholder(INFO_NICKNAME_PLACEHOLDER.get())
                    .buildArgument();
    addDefaultArgument(certNicknameArg);

    connectTimeoutArg = connectTimeOutHiddenArgument();
    addArgument(connectTimeoutArg);

    acceptLicense = acceptLicenseArgument();
    addArgument(acceptLicense);

    showUsageArg = showUsageArgument();
    addArgument(showUsageArg);
    setUsageArgument(showUsageArg);

    backendTypeArg =
            StringArgument.builder(OPTION_LONG_BACKEND_TYPE)
                    .shortIdentifier(OPTION_SHORT_BACKEND_TYPE)
                    .description(INFO_INSTALLDS_DESCRIPTION_BACKEND_TYPE.get())
                    .defaultValue(BackendTypeHelper.filterSchemaBackendName(
                            new BackendTypeHelper().getBackendTypes().get(0).getName()))
                    .valuePlaceholder(INFO_INSTALLDS_BACKEND_TYPE_PLACEHOLDER.get())
                    .buildArgument();
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
    addErrorMessageIfArgumentsConflict(errorMessages, directoryManagerPwdStringArg, directoryManagerPwdFileArg);

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
    if (!ports.add(port))
    {
      errorMessages.add(errorMsg.get(port));
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
    addErrorMessageIfArgumentsConflict(errorMessages, addBaseEntryArg, importLDIFArg);
    addErrorMessageIfArgumentsConflict(errorMessages, addBaseEntryArg, sampleDataArg);
    addErrorMessageIfArgumentsConflict(errorMessages, importLDIFArg, sampleDataArg);
    addErrorMessageIfArgumentsConflict(errorMessages, addBaseEntryArg, rejectedImportFileArg);
    addErrorMessageIfArgumentsConflict(errorMessages, rejectedImportFileArg, sampleDataArg);
    addErrorMessageIfArgumentsConflict(errorMessages, addBaseEntryArg, skippedImportFileArg);
    addErrorMessageIfArgumentsConflict(errorMessages, skippedImportFileArg, sampleDataArg);

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
        addErrorMessageIfArgumentsConflict(errorMessages, keyStorePasswordArg, keyStorePasswordFileArg);

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
