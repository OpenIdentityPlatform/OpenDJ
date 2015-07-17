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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */
package org.opends.server.tools;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.Utils.*;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.Console;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.server.BackendCfg;
import org.opends.server.admin.std.server.LDIFBackendCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.std.server.TrustStoreBackendCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.config.ConfigConstants;
import org.opends.server.config.ConfigEntry;
import org.opends.server.core.CoreConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.DirectoryServer.DirectoryServerVersionHandler;
import org.opends.server.core.PasswordStorageSchemeConfigManager;
import org.opends.server.crypto.CryptoManagerSync;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.loggers.JDKLogging;
import org.opends.server.schema.AuthPasswordSyntax;
import org.opends.server.schema.UserPasswordSyntax;
import org.opends.server.types.*;
import org.opends.server.util.BuildVersion;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.CommonArguments;
import com.forgerock.opendj.cli.FileBasedArgument;
import com.forgerock.opendj.cli.StringArgument;

/**
 * This program provides a utility that may be used to interact with the
 * password storage schemes defined in the Directory Server.  In particular,
 * it can encode a clear-text password using a specified scheme, and it can also
 * determine whether a given encoded password is the encoded representation of a
 * given clear-text password.  Alternately, it can be used to obtain a list of
 * the available password storage scheme names.
 */
public class EncodePassword
{
  /**
   * Processes the command-line arguments and performs the requested action.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int returnCode = encodePassword(args, true, System.out, System.err);
    if (returnCode != 0)
    {
      System.exit(filterExitCode(returnCode));
    }
  }



  /**
   * Processes the command-line arguments and performs the requested action.
   *
   * @param  args  The command-line arguments provided to this program.
   *
   * @return  An integer value that indicates whether processing was successful.
   */
  public static int encodePassword(String[] args)
  {
    return encodePassword(args, true, System.out, System.err);
  }



  /**
   * Processes the command-line arguments and performs the requested action.
   *
   * @param  args              The command-line arguments provided to this
   *                           program.
   * @param  initializeServer  Indicates whether to initialize the server.
   * @param  outStream         The output stream to use for standard output, or
   *                           <CODE>null</CODE> if standard output is not
   *                           needed.
   * @param  errStream         The output stream to use for standard error, or
   *                           <CODE>null</CODE> if standard error is not
   *                           needed.
   *
   * @return  An integer value that indicates whether processing was successful.
   */
  public static int encodePassword(String[] args, boolean initializeServer,
                                   OutputStream outStream,
                                   OutputStream errStream)
  {
    PrintStream out = NullOutputStream.wrapOrNullStream(outStream);
    PrintStream err = NullOutputStream.wrapOrNullStream(errStream);
    JDKLogging.disableLogging();

    // Define the command-line arguments that may be used with this program.
    BooleanArgument   authPasswordSyntax   = null;
    BooleanArgument   useCompareResultCode = null;
    BooleanArgument   listSchemes          = null;
    BooleanArgument   showUsage            = null;
    BooleanArgument   interactivePassword  = null;
    StringArgument    clearPassword        = null;
    FileBasedArgument clearPasswordFile    = null;
    StringArgument    encodedPassword      = null;
    FileBasedArgument encodedPasswordFile  = null;
    StringArgument    configClass          = null;
    StringArgument    configFile           = null;
    StringArgument    schemeName           = null;


    // Create the command-line argument parser for use with this program.
    LocalizableMessage toolDescription = INFO_ENCPW_TOOL_DESCRIPTION.get();
    ArgumentParser argParser =
         new ArgumentParser("org.opends.server.tools.EncodePassword",
                            toolDescription, false);
    argParser.setShortToolDescription(REF_SHORT_DESC_ENCODE_PASSWORD.get());
    argParser.setVersionHandler(new DirectoryServerVersionHandler());

    // Initialize all the command-line argument types and register them with the
    // parser.
    try
    {
      listSchemes = new BooleanArgument(
              "listschemes", 'l', "listSchemes",
              INFO_ENCPW_DESCRIPTION_LISTSCHEMES.get());
      argParser.addArgument(listSchemes);

      interactivePassword = new BooleanArgument(
              "interactivePassword", 'i',
              "interactivePassword",
              INFO_ENCPW_DESCRIPTION_INPUT_PW.get());
      argParser.addArgument(interactivePassword);

      clearPassword = new StringArgument("clearpw", 'c', "clearPassword", false,
                                         false, true, INFO_CLEAR_PWD.get(),
                                         null, null,
                                         INFO_ENCPW_DESCRIPTION_CLEAR_PW.get());
      argParser.addArgument(clearPassword);


      clearPasswordFile =
           new FileBasedArgument("clearpwfile", 'f', "clearPasswordFile", false,
                                 false, INFO_FILE_PLACEHOLDER.get(), null, null,
                                 INFO_ENCPW_DESCRIPTION_CLEAR_PW_FILE.get());
      argParser.addArgument(clearPasswordFile);


      encodedPassword = new StringArgument(
              "encodedpw", 'e', "encodedPassword",
              false, false, true, INFO_ENCODED_PWD_PLACEHOLDER.get(),
              null, null,
              INFO_ENCPW_DESCRIPTION_ENCODED_PW.get());
      argParser.addArgument(encodedPassword);


      encodedPasswordFile =
           new FileBasedArgument("encodedpwfile", 'E', "encodedPasswordFile",
                                 false, false, INFO_FILE_PLACEHOLDER.get(),
                                 null, null,
                                 INFO_ENCPW_DESCRIPTION_ENCODED_PW_FILE.get());
      argParser.addArgument(encodedPasswordFile);


      configClass = new StringArgument("configclass", OPTION_SHORT_CONFIG_CLASS,
                                       OPTION_LONG_CONFIG_CLASS,
                                       true, false, true,
                                       INFO_CONFIGCLASS_PLACEHOLDER.get(),
                                       ConfigFileHandler.class.getName(), null,
                                       INFO_DESCRIPTION_CONFIG_CLASS.get());
      configClass.setHidden(true);
      argParser.addArgument(configClass);


      configFile = new StringArgument("configfile", 'F', "configFile",
                                      true, false, true,
                                      INFO_CONFIGFILE_PLACEHOLDER.get(), null,
                                      null,
                                      INFO_DESCRIPTION_CONFIG_FILE.get());
      configFile.setHidden(true);
      argParser.addArgument(configFile);


      schemeName = new StringArgument("scheme", 's', "storageScheme", false,
                                      false, true,
                                      INFO_STORAGE_SCHEME_PLACEHOLDER.get(),
                                      null, null,
                                      INFO_ENCPW_DESCRIPTION_SCHEME.get());
      argParser.addArgument(schemeName);


      authPasswordSyntax = new BooleanArgument(
              "authpasswordsyntax", 'a',
              "authPasswordSyntax",
              INFO_ENCPW_DESCRIPTION_AUTHPW.get());
      argParser.addArgument(authPasswordSyntax);


      useCompareResultCode =
           new BooleanArgument("usecompareresultcode", 'r',
                               "useCompareResultCode",
                               INFO_ENCPW_DESCRIPTION_USE_COMPARE_RESULT.get());
      argParser.addArgument(useCompareResultCode);


      showUsage = CommonArguments.getShowUsage();
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage, out);
    }
    catch (ArgumentException ae)
    {
      printWrappedText(err, ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
      return OPERATIONS_ERROR;
    }


    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      printWrappedText(err, ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
      err.println(argParser.getUsage());
      return OPERATIONS_ERROR;
    }


    // If we should just display usage or version information,
    // then we've already done it so just return without doing anything else.
    if (argParser.usageOrVersionDisplayed())
    {
      return SUCCESS;
    }

    // Checks the version - if upgrade required, the tool is unusable
    try
    {
      BuildVersion.checkVersionMismatch();
    }
    catch (InitializationException e)
    {
      printWrappedText(err, e.getMessage());
      return 1;
    }

    // Check for conflicting arguments.
    if (clearPassword.isPresent() && clearPasswordFile.isPresent())
    {
      printWrappedText(err,
          ERR_TOOL_CONFLICTING_ARGS.get(clearPassword.getLongIdentifier(), clearPasswordFile.getLongIdentifier()));
      return OPERATIONS_ERROR;
    }

    if (clearPassword.isPresent() && interactivePassword.isPresent())
    {
      printWrappedText(err,
          ERR_TOOL_CONFLICTING_ARGS.get(clearPassword.getLongIdentifier(), interactivePassword.getLongIdentifier()));
      return OPERATIONS_ERROR;
    }

    if (clearPasswordFile.isPresent() && interactivePassword.isPresent())
    {
      printWrappedText(err, ERR_TOOL_CONFLICTING_ARGS.get(clearPasswordFile.getLongIdentifier(),
                                                          interactivePassword.getLongIdentifier()));
      return OPERATIONS_ERROR;
    }

    if (encodedPassword.isPresent() && encodedPasswordFile.isPresent())
    {
      printWrappedText(err,
          ERR_TOOL_CONFLICTING_ARGS.get(encodedPassword.getLongIdentifier(), encodedPasswordFile.getLongIdentifier()));
      return OPERATIONS_ERROR;
    }


    // If we are not going to just list the storage schemes, then the clear-text
    // password must have been provided.  If we're going to encode a password,
    // then the scheme must have also been provided.
    if (!listSchemes.isPresent()
        && !encodedPassword.isPresent()
        && !encodedPasswordFile.isPresent()
        && !schemeName.isPresent())
    {
      printWrappedText(err, ERR_ENCPW_NO_SCHEME.get(schemeName.getLongIdentifier()));
      err.println(argParser.getUsage());
      return OPERATIONS_ERROR;
    }


    // Determine whether we're encoding the clear-text password or comparing it
    // against an already-encoded password.
    boolean compareMode;
    ByteString encodedPW = null;
    if (encodedPassword.hasValue())
    {
      compareMode = true;
      encodedPW = ByteString.valueOf(encodedPassword.getValue());
    }
    else if (encodedPasswordFile.hasValue())
    {
      compareMode = true;
      encodedPW = ByteString.valueOf(encodedPasswordFile.getValue());
    }
    else
    {
      compareMode = false;
    }


    // Perform the initial bootstrap of the Directory Server and process the
    // configuration.
    DirectoryServer directoryServer = DirectoryServer.getInstance();

    if (initializeServer)
    {
      try
      {
        DirectoryServer.bootstrapClient();
        DirectoryServer.initializeJMX();
      }
      catch (Exception e)
      {
        printWrappedText(err, ERR_SERVER_BOOTSTRAP_ERROR.get(getExceptionMessage(e)));
        return OPERATIONS_ERROR;
      }

      try
      {
        directoryServer.initializeConfiguration(configClass.getValue(),
                                                configFile.getValue());
      }
      catch (InitializationException ie)
      {
        printWrappedText(err, ERR_CANNOT_LOAD_CONFIG.get(ie.getMessage()));
        return OPERATIONS_ERROR;
      }
      catch (Exception e)
      {
        printWrappedText(err, ERR_CANNOT_LOAD_CONFIG.get(getExceptionMessage(e)));
        return OPERATIONS_ERROR;
      }



      // Initialize the Directory Server schema elements.
      try
      {
        directoryServer.initializeSchema();
      }
      catch (ConfigException | InitializationException e)
      {
        printWrappedText(err, ERR_CANNOT_LOAD_SCHEMA.get(e.getMessage()));
        return OPERATIONS_ERROR;
      }
      catch (Exception e)
      {
        printWrappedText(err, ERR_CANNOT_LOAD_SCHEMA.get(getExceptionMessage(e)));
        return OPERATIONS_ERROR;
      }


      // Initialize the Directory Server core configuration.
      try
      {
        CoreConfigManager coreConfigManager = new CoreConfigManager(directoryServer.getServerContext());
        coreConfigManager.initializeCoreConfig();
      }
      catch (ConfigException | InitializationException e)
      {
        printWrappedText(err, ERR_CANNOT_INITIALIZE_CORE_CONFIG.get(e.getMessage()));
        return OPERATIONS_ERROR;
      }
      catch (Exception e)
      {
        printWrappedText(err, ERR_CANNOT_INITIALIZE_CORE_CONFIG.get(getExceptionMessage(e)));
        return OPERATIONS_ERROR;
      }


      if(!initializeServerComponents(directoryServer, err))
      {
        return -1;
      }

      // Initialize the password storage schemes.
      try
      {
        PasswordStorageSchemeConfigManager storageSchemeConfigManager =
             new PasswordStorageSchemeConfigManager(directoryServer.getServerContext());
        storageSchemeConfigManager.initializePasswordStorageSchemes();
      }
      catch (ConfigException | InitializationException e)
      {
        printWrappedText(err, ERR_ENCPW_CANNOT_INITIALIZE_STORAGE_SCHEMES.get(e.getMessage()));
        return OPERATIONS_ERROR;
      }
      catch (Exception e)
      {
        printWrappedText(err, ERR_ENCPW_CANNOT_INITIALIZE_STORAGE_SCHEMES.get(getExceptionMessage(e)));
        return OPERATIONS_ERROR;
      }
    }


    // If we are only trying to list the available schemes, then do so and exit.
    if (listSchemes.isPresent())
    {
      if (authPasswordSyntax.isPresent())
      {
        listPasswordStorageSchemes(out, err, DirectoryServer.getAuthPasswordStorageSchemes(), true);
      }
      else
      {
        listPasswordStorageSchemes(out, err, DirectoryServer.getPasswordStorageSchemes(), false);
      }
      return SUCCESS;
    }


    // Either encode the clear-text password using the provided scheme, or
    // compare the clear-text password against the encoded password.
    ByteString clearPW = null;
    if (compareMode)
    {
      // Check to see if the provided password value was encoded.  If so, then
      // break it down into its component parts and use that to perform the
      // comparison.  Otherwise, the user must have provided the storage scheme.
      if (authPasswordSyntax.isPresent())
      {
        String scheme;
        String authInfo;
        String authValue;

        try
        {
          StringBuilder[] authPWElements =
               AuthPasswordSyntax.decodeAuthPassword(encodedPW.toString());
          scheme    = authPWElements[0].toString();
          authInfo  = authPWElements[1].toString();
          authValue = authPWElements[2].toString();
        }
        catch (DirectoryException de)
        {
          printWrappedText(err, ERR_ENCPW_INVALID_ENCODED_AUTHPW.get(de.getMessageObject()));
          return OPERATIONS_ERROR;
        }
        catch (Exception e)
        {
          printWrappedText(err, ERR_ENCPW_INVALID_ENCODED_AUTHPW.get(e));
          return OPERATIONS_ERROR;
        }

        PasswordStorageScheme storageScheme =
             DirectoryServer.getAuthPasswordStorageScheme(scheme);
        if (storageScheme == null)
        {
          printWrappedText(err, ERR_ENCPW_NO_SUCH_AUTH_SCHEME.get(scheme));
          return OPERATIONS_ERROR;
        }

        if (clearPW == null)
        {
          clearPW = getClearPW(out, err, argParser, clearPassword,
              clearPasswordFile, interactivePassword);
          if (clearPW == null)
          {
            return OPERATIONS_ERROR;
          }
        }
        final boolean authPasswordMatches =
            storageScheme.authPasswordMatches(clearPW, authInfo, authValue);
        out.println(getOutputMessage(authPasswordMatches));
        if (useCompareResultCode.isPresent())
        {
          return authPasswordMatches ? COMPARE_TRUE : COMPARE_FALSE;
        }
        return SUCCESS;
      }
      else
      {
        PasswordStorageScheme storageScheme;
        String                encodedPWString;

        if (UserPasswordSyntax.isEncoded(encodedPW))
        {
          try
          {
            String[] userPWElements =
                 UserPasswordSyntax.decodeUserPassword(encodedPW.toString());
            encodedPWString = userPWElements[1];

            storageScheme =
                 DirectoryServer.getPasswordStorageScheme(userPWElements[0]);
            if (storageScheme == null)
            {
              printWrappedText(err, ERR_ENCPW_NO_SUCH_SCHEME.get(userPWElements[0]));
              return OPERATIONS_ERROR;
            }
          }
          catch (DirectoryException de)
          {
            printWrappedText(err, ERR_ENCPW_INVALID_ENCODED_USERPW.get(de.getMessageObject()));
            return OPERATIONS_ERROR;
          }
          catch (Exception e)
          {
            printWrappedText(err, ERR_ENCPW_INVALID_ENCODED_USERPW.get(e));
            return OPERATIONS_ERROR;
          }
        }
        else
        {
          if (! schemeName.isPresent())
          {
            printWrappedText(err, ERR_ENCPW_NO_SCHEME.get(schemeName.getLongIdentifier()));
            return OPERATIONS_ERROR;
          }

          encodedPWString = encodedPW.toString();

          String scheme = toLowerCase(schemeName.getValue());
          storageScheme = DirectoryServer.getPasswordStorageScheme(scheme);
          if (storageScheme == null)
          {
            printWrappedText(err, ERR_ENCPW_NO_SUCH_SCHEME.get(scheme));
            return OPERATIONS_ERROR;
          }
        }

        if (clearPW == null)
        {
          clearPW = getClearPW(out, err, argParser, clearPassword,
              clearPasswordFile, interactivePassword);
          if (clearPW == null)
          {
            return OPERATIONS_ERROR;
          }
        }
        boolean passwordMatches =
            storageScheme.passwordMatches(clearPW, ByteString
                .valueOf(encodedPWString));
        out.println(getOutputMessage(passwordMatches));
        if (useCompareResultCode.isPresent())
        {
          return passwordMatches ? COMPARE_TRUE : COMPARE_FALSE;
        }
        return SUCCESS;
      }
    }
    else
    {
      // Try to get a reference to the requested password storage scheme.
      PasswordStorageScheme storageScheme;
      if (authPasswordSyntax.isPresent())
      {
        String scheme = schemeName.getValue();
        storageScheme = DirectoryServer.getAuthPasswordStorageScheme(scheme);
        if (storageScheme == null)
        {
          printWrappedText(err, ERR_ENCPW_NO_SUCH_AUTH_SCHEME.get(scheme));
          return OPERATIONS_ERROR;
        }
      }
      else
      {
        String scheme = toLowerCase(schemeName.getValue());
        storageScheme = DirectoryServer.getPasswordStorageScheme(scheme);
        if (storageScheme == null)
        {
          printWrappedText(err, ERR_ENCPW_NO_SUCH_SCHEME.get(scheme));
          return OPERATIONS_ERROR;
        }
      }

      if (authPasswordSyntax.isPresent())
      {
        try
        {
          if (clearPW == null)
          {
            clearPW = getClearPW(out, err, argParser, clearPassword,
                clearPasswordFile, interactivePassword);
            if (clearPW == null)
            {
              return OPERATIONS_ERROR;
            }
          }
          encodedPW = storageScheme.encodeAuthPassword(clearPW);

          LocalizableMessage message = ERR_ENCPW_ENCODED_PASSWORD.get(encodedPW);
          out.println(message);
        }
        catch (DirectoryException de)
        {
          printWrappedText(err, ERR_ENCPW_CANNOT_ENCODE.get(de.getMessageObject()));
          return OPERATIONS_ERROR;
        }
        catch (Exception e)
        {
          printWrappedText(err, ERR_ENCPW_CANNOT_ENCODE.get(getExceptionMessage(e)));
          return OPERATIONS_ERROR;
        }
      }
      else
      {
        try
        {
          if (clearPW == null)
          {
            clearPW = getClearPW(out, err, argParser, clearPassword,
                clearPasswordFile, interactivePassword);
            if (clearPW == null)
            {
              return OPERATIONS_ERROR;
            }
          }
          encodedPW = storageScheme.encodePasswordWithScheme(clearPW);

          out.println(ERR_ENCPW_ENCODED_PASSWORD.get(encodedPW));
        }
        catch (DirectoryException de)
        {
          printWrappedText(err, ERR_ENCPW_CANNOT_ENCODE.get(de.getMessageObject()));
          return OPERATIONS_ERROR;
        }
        catch (Exception e)
        {
          printWrappedText(err, ERR_ENCPW_CANNOT_ENCODE.get(getExceptionMessage(e)));
          return OPERATIONS_ERROR;
        }
      }
    }

    // If we've gotten here, then all processing completed successfully.
    return SUCCESS;
  }

  private static void listPasswordStorageSchemes(PrintStream out, PrintStream err,
      ConcurrentHashMap<String, PasswordStorageScheme> storageSchemes, boolean authPasswordSchemeName)
  {
    if (storageSchemes.isEmpty())
    {
      printWrappedText(err, ERR_ENCPW_NO_STORAGE_SCHEMES.get());
    }
    else
    {
      ArrayList<String> nameList = new ArrayList<>(storageSchemes.size());
      for (PasswordStorageScheme<?> s : storageSchemes.values())
      {
        if (authPasswordSchemeName)
        {
          nameList.add(s.getAuthPasswordSchemeName());
        }
        else
        {
          nameList.add(s.getStorageSchemeName());
        }
      }
      Collections.sort(nameList);

      for (String storageSchemeName : nameList)
      {
        out.println(storageSchemeName);
      }
    }
  }

  private static LocalizableMessage getOutputMessage(boolean passwordMatches)
  {
    if (passwordMatches)
    {
      return INFO_ENCPW_PASSWORDS_MATCH.get();
    }
    return INFO_ENCPW_PASSWORDS_DO_NOT_MATCH.get();
  }



  private static boolean initializeServerComponents(DirectoryServer directoryServer, PrintStream err)
  {
      // Initialize the Directory Server crypto manager.
      try
      {
        directoryServer.initializeCryptoManager();
      }
      catch (ConfigException | InitializationException e)
      {
        printWrappedText(err, ERR_CANNOT_INITIALIZE_CRYPTO_MANAGER.get(e.getMessage()));
        return false;
      }
      catch (Exception e)
      {
        printWrappedText(err, ERR_CANNOT_INITIALIZE_CRYPTO_MANAGER.get(getExceptionMessage(e)));
        return false;
      }
      //Attempt to bring up enough of the server to process schemes requiring
      //secret keys from the trust store backend (3DES, BLOWFISH, AES, RC4) via
      //the crypto-manager.
      try {
          directoryServer.initializeRootDNConfigManager();
          directoryServer.initializePlugins(Collections.EMPTY_SET);
          initializeServerBackends(directoryServer, err);
          directoryServer.initializeSubentryManager();
          directoryServer.initializeAuthenticationPolicyComponents();
          directoryServer.initializeAuthenticatedUsers();
          new CryptoManagerSync();
    } catch (InitializationException | ConfigException e) {
        printWrappedText(err, ERR_ENCPW_CANNOT_INITIALIZE_SERVER_COMPONENTS.get(getExceptionMessage(e)));
        return false;
    }
    return true;
  }

  private static void initializeServerBackends(DirectoryServer directoryServer, PrintStream err)
  throws InitializationException, ConfigException {
    directoryServer.initializeRootDSE();
    ServerManagementContext context = ServerManagementContext.getInstance();
    RootCfg root = context.getRootConfiguration();
    ConfigEntry backendRoot;
    try {
      DN configEntryDN = DN.valueOf(ConfigConstants.DN_BACKEND_BASE);
      backendRoot   = DirectoryServer.getConfigEntry(configEntryDN);
    } catch (Exception e) {
      LocalizableMessage message = ERR_CONFIG_BACKEND_CANNOT_GET_CONFIG_BASE.get(
          getExceptionMessage(e));
      throw new ConfigException(message, e);
    }
    if (backendRoot == null) {
      LocalizableMessage message = ERR_CONFIG_BACKEND_BASE_DOES_NOT_EXIST.get();
      throw new ConfigException(message);
    }
    for (String name : root.listBackends()) {
      BackendCfg backendCfg = root.getBackend(name);
      String backendID = backendCfg.getBackendId();
      if((backendCfg instanceof TrustStoreBackendCfg
          || backendCfg instanceof LDIFBackendCfg)
          && backendCfg.isEnabled())
      {
        String className = backendCfg.getJavaClass();
        Class<?> backendClass;
        Backend<BackendCfg> backend;
        try {
          backendClass = DirectoryServer.loadClass(className);
          backend = (Backend<BackendCfg>) backendClass.newInstance();
        } catch (Exception e) {
          printWrappedText(err,
              ERR_CONFIG_BACKEND_CANNOT_INSTANTIATE.get(className, backendCfg.dn(), stackTraceToSingleLineString(e)));
          continue;
        }
        backend.setBackendID(backendID);
        backend.setWritabilityMode(WritabilityMode.INTERNAL_ONLY);
        try {
          backend.configureBackend(backendCfg, directoryServer.getServerContext());
          backend.openBackend();
        } catch (Exception e) {
          printWrappedText(err,
              ERR_CONFIG_BACKEND_CANNOT_INITIALIZE.get(className, backendCfg.dn(), stackTraceToSingleLineString(e)));
        }
        try {
          DirectoryServer.registerBackend(backend);
        } catch (Exception e)
        {
          printWrappedText(
              err, WARN_CONFIG_BACKEND_CANNOT_REGISTER_BACKEND.get(backendCfg.getBackendId(), getExceptionMessage(e)));
        }
      }
    }
  }

  /**
   * Get the clear password.
   * @param out The output to ask password.
   * @param err The error output.
   * @param argParser The argument parser.
   * @param clearPassword the clear password
   * @param clearPasswordFile the file in which the password in stored
   * @param interactivePassword indicate if the password should be asked
   *        interactively.
   * @return the password or null if an error occurs.
   */
  private static ByteString getClearPW(PrintStream out, PrintStream err,
      ArgumentParser argParser, StringArgument clearPassword,
      FileBasedArgument clearPasswordFile, BooleanArgument interactivePassword)
  {
    if (clearPassword.hasValue())
    {
      return ByteString.valueOf(clearPassword.getValue());
    }
    else if (clearPasswordFile.hasValue())
    {
      return ByteString.valueOf(clearPasswordFile.getValue());
    }
    else if (interactivePassword.isPresent())
    {
      try
      {
        EncodePassword encodePassword = new EncodePassword();
        String pwd1 = encodePassword.getPassword(INFO_ENCPW_INPUT_PWD_1.get().toString());
        String pwd2 = encodePassword.getPassword(INFO_ENCPW_INPUT_PWD_2.get().toString());
        if (pwd1.equals(pwd2))
        {
          return ByteString.valueOf(pwd1);
        }
        else
        {
          printWrappedText(err, ERR_ENCPW_NOT_SAME_PW.get());
          return null;
        }
      }
      catch (IOException e)
      {
        printWrappedText(err, ERR_ENCPW_CANNOT_READ_PW.get(e.getMessage()));
        return null;
      }
    }
    else
    {
      printWrappedText(err, ERR_ENCPW_NO_CLEAR_PW.get(clearPassword.getLongIdentifier(),
                            clearPasswordFile.getLongIdentifier(), interactivePassword.getLongIdentifier()));
      err.println(argParser.getUsage());
      return null;
    }
  }

  /**
   * Get the password from JDK6 console or from masked password.
   * @param prompt The message to print out.
   * @return the password
   * @throws IOException if an issue occurs when reading the password
   *         from the input
   */
  private String getPassword(String prompt) throws IOException
  {
    String password;
    try
    {
      Console console = System.console();
      if (console == null)
      {
        throw new IOException("No console");
      }
      password = new String(console.readPassword(prompt));
    }
    catch (Exception e)
    {
      // Try the fallback to the old trick method.
      // Create the thread that will erase chars
      ErasingThread erasingThread = new ErasingThread(prompt);
      erasingThread.start();

      password = "";

      // block until enter is pressed
      while (true)
      {
        char c = (char) System.in.read();
        // assume enter pressed, stop masking
        erasingThread.stopMasking();
        if (c == '\r')
        {
          c = (char) System.in.read();
          if (c == '\n')
          {
            break;
          }
        }
        else if (c == '\n')
        {
          break;
        }
        else
        {
          // store the password
          password += c;
        }
      }
    }
    return password;
  }


  /**
   * Thread that mask user input.
   */
  private class ErasingThread extends Thread
  {

    private boolean stop;
    private String prompt;

    /**
     * The class will mask the user input.
     * @param prompt
     *          The prompt displayed to the user
     */
    public ErasingThread(String prompt)
    {
      this.prompt = prompt;
    }

    /**
     * Begin masking until asked to stop.
     */
    @Override
    public void run()
    {
      while (!stop)
      {
        try
        {
          // attempt masking at this rate
          Thread.sleep(1);
        }
        catch (InterruptedException iex)
        {
          iex.printStackTrace();
        }
        if (!stop)
        {
          System.out.print("\r" + prompt + " \r" + prompt);
        }
        System.out.flush();
      }
    }

    /**
     * Instruct the thread to stop masking.
     */
    public void stopMasking()
    {
      this.stop = true;
    }
  }

}

