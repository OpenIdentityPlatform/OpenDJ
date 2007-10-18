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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.tools;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.ToolConstants.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opends.messages.Message;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.util.Utils;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.util.SetupUtils;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;

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
  BooleanArgument   testOnlyArg;
  BooleanArgument   cliArg;
  BooleanArgument   addBaseEntryArg;
  BooleanArgument   showUsageArg;
  BooleanArgument   quietArg;
  BooleanArgument   noPromptArg;
  BooleanArgument   skipPortCheckArg;
  BooleanArgument   enableWindowsServiceArg;
  BooleanArgument   doNotStartArg;
  BooleanArgument   enableStartTLSArg;
  BooleanArgument   generateSelfSignedCertificateArg;
  BooleanArgument   usePkcs11Arg;
  FileBasedArgument directoryManagerPwdFileArg;
  FileBasedArgument keyStorePasswordFileArg;
  IntegerArgument   ldapPortArg;
  IntegerArgument   ldapsPortArg;
  IntegerArgument   jmxPortArg;
  IntegerArgument   sampleDataArg;
  StringArgument    baseDNArg;
  StringArgument    configClassArg;
  StringArgument    configFileArg;
  StringArgument    importLDIFArg;
  StringArgument    rejectedImportFileArg;
  StringArgument    skippedImportFileArg;
  StringArgument    directoryManagerDNArg;
  StringArgument    directoryManagerPwdStringArg;
  StringArgument    useJavaKeyStoreArg;
  StringArgument    usePkcs12Arg;
  StringArgument    keyStorePasswordArg;
  StringArgument    certNicknameArg;
  StringArgument    progNameArg;

  private static final Logger LOG = Logger.getLogger(
      InstallDSArgumentParser.class.getName());

  /**
   * The default constructor for this class.
   * @param mainClassName the class name of the main class for the command-line
   * that is being used.
   */
  public InstallDSArgumentParser(String mainClassName)
  {
    super(mainClassName, INFO_INSTALLDS_TOOL_DESCRIPTION.get(), false);
  }

  /**
   * Initializes the arguments without parsing them.
   * @throws ArgumentException if there was an error creating or adding the
   * arguments.  If this occurs is likely to be a bug.
   */
  public void initializeArguments() throws ArgumentException
  {
    testOnlyArg = new BooleanArgument(
        "test", 't', "testOnly",
        INFO_INSTALLDS_DESCRIPTION_TESTONLY.get());
    testOnlyArg.setHidden(true);
    addArgument(testOnlyArg);

    cliArg = new BooleanArgument(
        OPTION_LONG_CLI,
        OPTION_SHORT_CLI,
        OPTION_LONG_CLI,
        INFO_INSTALLDS_DESCRIPTION_CLI.get());
    addArgument(cliArg);

    configFileArg = new StringArgument(
        "configfile", 'c', "configFile", false,
        false, true, "{configFile}", getDefaultConfigFile(), null,
        INFO_DESCRIPTION_CONFIG_FILE.get());
    configFileArg.setHidden(true);
    addArgument(configFileArg);

    configClassArg = new StringArgument(
        "configclass", OPTION_SHORT_CONFIG_CLASS,
        OPTION_LONG_CONFIG_CLASS, false,
        false, true, OPTION_VALUE_CONFIG_CLASS,
        ConfigFileHandler.class.getName(), null,
        INFO_DESCRIPTION_CONFIG_CLASS.get());
    configClassArg.setHidden(true);
    addArgument(configClassArg);

    String defaultProgName;
    if (SetupUtils.isWindows())
    {
      defaultProgName = Installation.WINDOWS_SETUP_FILE_NAME;
    }
    else
    {
      defaultProgName = Installation.UNIX_SETUP_FILE_NAME;
    }
    progNameArg = new StringArgument(
        "progname", 'P', "programName", false,
        false, true, "{programName}", defaultProgName,
        null, INFO_INSTALLDS_DESCRIPTION_PROGNAME.get());
    progNameArg.setHidden(true);
    addArgument(progNameArg);

    quietArg = new BooleanArgument(
        "quiet", OPTION_SHORT_QUIET,
        OPTION_LONG_QUIET,
        INFO_INSTALLDS_DESCRIPTION_SILENT.get());
    addArgument(quietArg);

    noPromptArg = new BooleanArgument(
        OPTION_LONG_NO_PROMPT,
        OPTION_SHORT_NO_PROMPT,
        OPTION_LONG_NO_PROMPT,
        INFO_INSTALLDS_DESCRIPTION_NO_PROMPT.get());
    addArgument(noPromptArg);

    baseDNArg = new StringArgument(
        "basedn", OPTION_SHORT_BASEDN,
        OPTION_LONG_BASEDN, false, true, true,
        OPTION_VALUE_BASEDN,
        "dc=example,dc=com", null,
        INFO_INSTALLDS_DESCRIPTION_BASEDN.get());
    addArgument(baseDNArg);

    addBaseEntryArg = new BooleanArgument(
        "addbase", 'a', "addBaseEntry",
        INFO_INSTALLDS_DESCRIPTION_ADDBASE.get());
    addArgument(addBaseEntryArg);

    importLDIFArg = new StringArgument(
        "importldif", OPTION_SHORT_LDIF_FILE,
        OPTION_LONG_LDIF_FILE, false,
        true, true, OPTION_VALUE_LDIF_FILE,
        null, null,
        INFO_INSTALLDS_DESCRIPTION_IMPORTLDIF.get());
    addArgument(importLDIFArg);

    rejectedImportFileArg = new StringArgument(
        "rejectfile", 'R', "rejectFile", false, false,
        true, "{rejectFile}", null, null,
        INFO_INSTALLDS_DESCRIPTION_REJECTED_FILE.get());
    addArgument(rejectedImportFileArg);

    skippedImportFileArg = new StringArgument(
        "skipFile", null, "skipFile", false, false,
        true, "{skipFile}", null, null,
        INFO_INSTALLDS_DESCRIPTION_SKIPPED_FILE.get());
    addArgument(skippedImportFileArg);

    sampleDataArg = new IntegerArgument(
        "sampledata", 'd', "sampleData", false,
        false, true, "{numEntries}", 0, null,
        true, 0, false, 0,
        INFO_INSTALLDS_DESCRIPTION_SAMPLE_DATA.get());
    addArgument(sampleDataArg);

    ldapPortArg = new IntegerArgument(
        "ldapport", OPTION_SHORT_PORT,
        "ldapPort", false, false,
        true, OPTION_VALUE_PORT, 389,
        null, true, 1, true, 65535,
        INFO_INSTALLDS_DESCRIPTION_LDAPPORT.get());
    addArgument(ldapPortArg);

    jmxPortArg = new IntegerArgument(
        "jmxport", 'x', "jmxPort", false, false,
        true, "{jmxPort}",
        SetupUtils.getDefaultJMXPort(), null, true,
        1, true, 65535,
        INFO_INSTALLDS_DESCRIPTION_JMXPORT.get());
    addArgument(jmxPortArg);

    skipPortCheckArg = new BooleanArgument(
        "skipportcheck", 'S', "skipPortCheck",
        INFO_INSTALLDS_DESCRIPTION_SKIPPORT.get());
    addArgument(skipPortCheckArg);

    directoryManagerDNArg = new StringArgument(
        "rootdn",OPTION_SHORT_ROOT_USER_DN,
        OPTION_LONG_ROOT_USER_DN, false, false,
        true, OPTION_VALUE_ROOT_USER_DN,
        "cn=Directory Manager",
        null, INFO_INSTALLDS_DESCRIPTION_ROOTDN.get());
    addArgument(directoryManagerDNArg);

    directoryManagerPwdStringArg = new StringArgument(
        "rootpwstring", OPTION_SHORT_BINDPWD,
        "rootUserPassword",
        false, false, true,
        "{password}", null,
        null,
        INFO_INSTALLDS_DESCRIPTION_ROOTPW.get());
    addArgument(directoryManagerPwdStringArg);

    directoryManagerPwdFileArg = new FileBasedArgument(
        "rootpwfile",
        OPTION_SHORT_BINDPWD_FILE,
        "rootUserPasswordFile", false, false,
        OPTION_VALUE_BINDPWD_FILE,
        null, null, INFO_INSTALLDS_DESCRIPTION_ROOTPWFILE.get());
    addArgument(directoryManagerPwdFileArg);

    enableWindowsServiceArg = new BooleanArgument(
        "enablewindowsservice", 'e',
        "enableWindowsService",
        INFO_INSTALLDS_DESCRIPTION_ENABLE_WINDOWS_SERVICE.get());
    if (SetupUtils.isWindows())
    {
      addArgument(enableWindowsServiceArg);
    }

    doNotStartArg = new BooleanArgument(
        "donotstart", 'O', "donotstart",
        INFO_INSTALLDS_DESCRIPTION_DO_NOT_START.get());
    addArgument(doNotStartArg);

    enableStartTLSArg = new BooleanArgument(
        "enableStartTLS", OPTION_SHORT_START_TLS, "enableStartTLS",
        INFO_INSTALLDS_DESCRIPTION_ENABLE_STARTTLS.get());
    addArgument(enableStartTLSArg);

    ldapsPortArg = new IntegerArgument(
        "ldapsport", OPTION_SHORT_USE_SSL,
        "ldapsPort", false, false,
        true, OPTION_VALUE_PORT, 636,
        null, true, 1, true, 65535,
        INFO_INSTALLDS_DESCRIPTION_LDAPSPORT.get());
    addArgument(ldapsPortArg);

    generateSelfSignedCertificateArg = new BooleanArgument(
        "generateSelfSignedCertificate",
        null, "generateSelfSignedCertificate",
        INFO_INSTALLDS_DESCRIPTION_USE_SELF_SIGNED.get());
    addArgument(generateSelfSignedCertificateArg);

    usePkcs11Arg = new BooleanArgument("usePkcs11Keystore",
        null, "usePkcs11Keystore",
        INFO_INSTALLDS_DESCRIPTION_USE_PKCS11.get());
    addArgument(usePkcs11Arg);

    useJavaKeyStoreArg = new StringArgument("useJavaKeystore",
        null, "useJavaKeystore", false, false,
        true, OPTION_VALUE_KEYSTOREPATH, null, null,
        INFO_INSTALLDS_DESCRIPTION_USE_JAVAKEYSTORE.get());
    addArgument(useJavaKeyStoreArg);

    usePkcs12Arg = new StringArgument("usePkcs12keyStore",
        null, "usePkcs12keyStore", false, false,
        true, OPTION_VALUE_KEYSTOREPATH, null, null,
        INFO_INSTALLDS_DESCRIPTION_USE_PKCS12.get());
    addArgument(usePkcs12Arg);

    keyStorePasswordArg = new StringArgument("keystorePassword",
        OPTION_SHORT_KEYSTORE_PWD,
        OPTION_LONG_KEYSTORE_PWD, false, false, true,
        OPTION_VALUE_KEYSTORE_PWD, null, null,
        INFO_INSTALLDS_DESCRIPTION_KEYSTOREPASSWORD.get());
    addDefaultArgument(keyStorePasswordArg);

    keyStorePasswordFileArg = new FileBasedArgument("keystorePasswordFile",
        OPTION_SHORT_KEYSTORE_PWD_FILE, OPTION_LONG_KEYSTORE_PWD_FILE, false,
        false, OPTION_VALUE_KEYSTORE_PWD_FILE, null, null,
        INFO_INSTALLDS_DESCRIPTION_KEYSTOREPASSWORD_FILE.get());
    addDefaultArgument(keyStorePasswordFileArg);

    certNicknameArg = new StringArgument("certnickname",
        OPTION_SHORT_CERT_NICKNAME, OPTION_LONG_CERT_NICKNAME,
        false, false, true, OPTION_VALUE_CERT_NICKNAME, null, null,
        INFO_INSTALLDS_DESCRIPTION_CERT_NICKNAME.get());
    addDefaultArgument(certNicknameArg);

    showUsageArg = new BooleanArgument("help", OPTION_SHORT_HELP,
        OPTION_LONG_HELP,
        INFO_INSTALLDS_DESCRIPTION_HELP.get());
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
      LOG.log(Level.SEVERE, "Error parsing arguments: "+ae, ae);
      errorMessages.add(ae.getMessageObject());
    }

    checkConfigFileArg(errorMessages);
    checkServerPassword(errorMessages);
    checkProvidedPorts(errorMessages);
    checkImportDataArguments(errorMessages);
    checkSecurityArguments(errorMessages);

    if (errorMessages.size() > 0)
    {
      Message message = ERR_CANNOT_INITIALIZE_ARGS.get(
          Utils.getMessageFromCollection(errorMessages,
              Constants.LINE_SEPARATOR));
      throw new ArgumentException(message);
    }
  }

  /**
   * Returns the directory manager password provided by the user.  This method
   * should be called after a call to parseArguments.
   * @return the directory manager password provided by the user.
   */
  public String getDirectoryManagerPassword()
  {
    String pwd = null;
    if (directoryManagerPwdStringArg.isPresent())
    {
      pwd = directoryManagerPwdStringArg.getValue();
    }
    else if (directoryManagerPwdFileArg.isPresent())
    {
      pwd = directoryManagerPwdFileArg.getValue();
    }
    return pwd;
  }

  /**
   * Returns the key store password provided by the user.  This method should be
   * called after a call to parseArguments.
   * @return the key store password provided by the user.
   */
  public String getKeyStorePassword()
  {
    String pwd = null;
    if (keyStorePasswordArg.isPresent())
    {
      pwd = keyStorePasswordArg.getValue();
    }
    else if (keyStorePasswordArg.isPresent())
    {
      pwd = keyStorePasswordFileArg.getValue();
    }
    return pwd;
  }

  /**
   * Checks that we have a config file value (at least the default value).
   * @param errorMessages the list of messages to which we add the error
   * messages describing the problems encountered during the execution of the
   * checking.
   */
  private void checkConfigFileArg(Collection<Message> errorMessages)
  {
    //  Make sure the path to the configuration file was given.
    if (configFileArg.getValue() == null)
    {
      Message message = ERR_INSTALLDS_NO_CONFIG_FILE.get(
              configFileArg.getLongIdentifier());
      errorMessages.add(message);
    }
  }

  /**
   * Checks that there are no conflicts with the directory manager passwords.
   * If we are in no prompt mode, check that the password was provided.
   * @param errorMessages the list of messages to which we add the error
   * messages describing the problems encountered during the execution of the
   * checking.
   */
  private void checkServerPassword(Collection<Message> errorMessages)
  {
    if (directoryManagerPwdStringArg.isPresent() &&
        directoryManagerPwdFileArg.isPresent())
    {
      Message message = ERR_INSTALLDS_TWO_CONFLICTING_ARGUMENTS.get(
          directoryManagerPwdStringArg.getLongIdentifier(),
          directoryManagerPwdFileArg.getLongIdentifier());
      errorMessages.add(message);
    }

    if (noPromptArg.isPresent() && !directoryManagerPwdStringArg.isPresent() &&
        !directoryManagerPwdFileArg.isPresent())
    {
      Message message = ERR_INSTALLDS_NO_ROOT_PASSWORD.get(
          directoryManagerPwdStringArg.getLongIdentifier(),
          directoryManagerPwdFileArg.getLongIdentifier());
      errorMessages.add(message);
    }
  }

  /**
   * Checks that there are no conflicts with the provided ports (like if the
   * user provided the same port for different protocols).
   * @param errorMessages the list of messages to which we add the error
   * messages describing the problems encountered during the execution of the
   * checking.
   */
  private void checkProvidedPorts(Collection<Message> errorMessages)
  {
    /**
     * Check that the provided ports do not match.
     */
    try
    {
      Set<Integer> ports = new HashSet<Integer>();
      ports.add(ldapPortArg.getIntValue());

      if (jmxPortArg.isPresent())
      {
        if (ports.contains(jmxPortArg.getIntValue()))
        {
          Message message = ERR_CONFIGDS_PORT_ALREADY_SPECIFIED.get(
                  String.valueOf(jmxPortArg.getIntValue()));
          errorMessages.add(message);
        }
        else
        {
          ports.add(jmxPortArg.getIntValue());
        }
      }
      if (ldapsPortArg.isPresent())
      {
        if (ports.contains(ldapsPortArg.getIntValue()))
        {
          Message message = ERR_CONFIGDS_PORT_ALREADY_SPECIFIED.get(
                  String.valueOf(ldapsPortArg.getIntValue()));
          errorMessages.add(message);
        }
        else
        {
          ports.add(ldapsPortArg.getIntValue());
        }
      }
    }
    catch (ArgumentException ae)
    {
      LOG.log(Level.SEVERE, "Unexpected error.  "+
          "Assuming that it is caused by a previous parsing issue: "+ae, ae);
    }
  }

  /**
   * Checks that there are no conflicts with the import data arguments.
   * @param errorMessages the list of messages to which we add the error
   * messages describing the problems encountered during the execution of the
   * checking.
   */
  private void checkImportDataArguments(Collection<Message> errorMessages)
  {
    //  Make sure that the user didn't provide conflicting arguments.
    if (addBaseEntryArg.isPresent())
    {
      if (importLDIFArg.isPresent())
      {
        Message message = ERR_TOOL_CONFLICTING_ARGS.get(
                addBaseEntryArg.getLongIdentifier(),
                importLDIFArg.getLongIdentifier());
        errorMessages.add(message);
      }
      else if (sampleDataArg.isPresent())
      {
        Message message = ERR_TOOL_CONFLICTING_ARGS.get(
                addBaseEntryArg.getLongIdentifier(),
                sampleDataArg.getLongIdentifier());
        errorMessages.add(message);
      }
    }
    else if (importLDIFArg.isPresent() && sampleDataArg.isPresent())
    {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
              importLDIFArg.getLongIdentifier(),
              sampleDataArg.getLongIdentifier());
      errorMessages.add(message);
    }

    if (rejectedImportFileArg.isPresent() && addBaseEntryArg.isPresent())
    {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
          addBaseEntryArg.getLongIdentifier(),
          rejectedImportFileArg.getLongIdentifier());
      errorMessages.add(message);
    }
    else if (rejectedImportFileArg.isPresent() && sampleDataArg.isPresent())
    {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
          rejectedImportFileArg.getLongIdentifier(),
          sampleDataArg.getLongIdentifier());
      errorMessages.add(message);
    }

    if (skippedImportFileArg.isPresent() && addBaseEntryArg.isPresent())
    {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
          addBaseEntryArg.getLongIdentifier(),
          skippedImportFileArg.getLongIdentifier());
      errorMessages.add(message);
    }
    else if (skippedImportFileArg.isPresent() && sampleDataArg.isPresent())
    {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
          skippedImportFileArg.getLongIdentifier(),
          sampleDataArg.getLongIdentifier());
      errorMessages.add(message);
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
  private void checkSecurityArguments(Collection<Message> errorMessages)
  {
    boolean certificateRequired = ldapsPortArg.isPresent() ||
    enableStartTLSArg.isPresent();

    int certificateType = 0;
    if (generateSelfSignedCertificateArg.isPresent())
    {
      certificateType++;
    }
    if (useJavaKeyStoreArg.isPresent())
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

    if (certificateRequired && noPromptArg.isPresent() &&
        (certificateType == 0))
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
          Message message = ERR_INSTALLDS_TWO_CONFLICTING_ARGUMENTS.get(
              keyStorePasswordArg.getLongIdentifier(),
              keyStorePasswordFileArg.getLongIdentifier());
          errorMessages.add(message);
        }

        // Check that we have one password in no prompt mode.
        if (noPromptArg.isPresent() && !keyStorePasswordArg.isPresent() &&
            !keyStorePasswordFileArg.isPresent())
        {
          Message message = ERR_INSTALLDS_NO_KEYSTORE_PASSWORD.get(
              keyStorePasswordArg.getLongIdentifier(),
              keyStorePasswordFileArg.getLongIdentifier());
          errorMessages.add(message);
        }
      }
      if (noPromptArg.isPresent() && !ldapsPortArg.isPresent() &&
          !enableStartTLSArg.isPresent())
      {
        Message message = ERR_INSTALLDS_SSL_OR_STARTTLS_REQUIRED.get(
            ldapsPortArg.getLongIdentifier(),
            enableStartTLSArg.getLongIdentifier());
        errorMessages.add(message);
      }
    }
  }

  /**
   * Returns the default config file retrieved by inspecting the class loader.
   * @return the default config file retrieved by inspecting the class loader.
   */
  private String getDefaultConfigFile()
  {
    // Use this instead of Installation.getLocal() because making that call
    // starts a new JVM and the command-line becomes less responsive.
    String root = Utils.getInstallPathFromClasspath();
    String configDir = Utils.getPath(root, Installation.CONFIG_PATH_RELATIVE);
    return Utils.getPath(configDir, Installation.CURRENT_CONFIG_FILE_NAME);
  }
}
