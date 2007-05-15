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



import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.config.ConfigException;
import org.opends.server.core.CoreConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PasswordStorageSchemeConfigManager;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.schema.AuthPasswordSyntax;
import org.opends.server.schema.UserPasswordSyntax;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.tools.ToolConstants.*;



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
      System.exit(returnCode);
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
    PrintStream out;
    if (outStream == null)
    {
      out = NullOutputStream.printStream();
    }
    else
    {
      out = new PrintStream(outStream);
    }

    PrintStream err;
    if (errStream == null)
    {
      err = NullOutputStream.printStream();
    }
    else
    {
      err = new PrintStream(errStream);
    }

    // Define the command-line arguments that may be used with this program.
    BooleanArgument   authPasswordSyntax   = null;
    BooleanArgument   useCompareResultCode = null;
    BooleanArgument   listSchemes          = null;
    BooleanArgument   showUsage            = null;
    StringArgument    clearPassword        = null;
    FileBasedArgument clearPasswordFile    = null;
    StringArgument    encodedPassword      = null;
    FileBasedArgument encodedPasswordFile  = null;
    StringArgument    configClass          = null;
    StringArgument    configFile           = null;
    StringArgument    schemeName           = null;


    // Create the command-line argument parser for use with this program.
    String toolDescription = getMessage(MSGID_ENCPW_TOOL_DESCRIPTION);
    ArgumentParser argParser =
         new ArgumentParser("org.opends.server.tools.EncodePassword",
                            toolDescription, false);


    // Initialize all the command-line argument types and register them with the
    // parser.
    try
    {
      listSchemes = new BooleanArgument("listschemes", 'l', "listSchemes",
                                        MSGID_ENCPW_DESCRIPTION_LISTSCHEMES);
      argParser.addArgument(listSchemes);


      clearPassword = new StringArgument("clearpw", 'c', "clearPassword", false,
                                         false, true, "{clearPW}", null, null,
                                         MSGID_ENCPW_DESCRIPTION_CLEAR_PW);
      argParser.addArgument(clearPassword);


      clearPasswordFile =
           new FileBasedArgument("clearpwfile", 'f', "clearPasswordFile", false,
                                 false, "{filename}", null, null,
                                 MSGID_ENCPW_DESCRIPTION_CLEAR_PW_FILE);
      argParser.addArgument(clearPasswordFile);


      encodedPassword = new StringArgument("encodedpw", 'e', "encodedPassword",
                                           false, false, true, "{encodedPW}",
                                           null, null,
                                           MSGID_ENCPW_DESCRIPTION_ENCODED_PW);
      argParser.addArgument(encodedPassword);


      encodedPasswordFile =
           new FileBasedArgument("encodedpwfile", 'E', "encodedPasswordFile",
                                 false, false, "{filename}", null, null,
                                 MSGID_ENCPW_DESCRIPTION_ENCODED_PW_FILE);
      argParser.addArgument(encodedPasswordFile);


      configClass = new StringArgument("configclass", OPTION_SHORT_CONFIG_CLASS,
                                       OPTION_LONG_CONFIG_CLASS,
                                       true, false, true,
                                       OPTION_VALUE_CONFIG_CLASS,
                                       ConfigFileHandler.class.getName(), null,
                                       MSGID_DESCRIPTION_CONFIG_CLASS);
      configClass.setHidden(true);
      argParser.addArgument(configClass);


      configFile = new StringArgument("configfile", 'F', "configFile",
                                      true, false, true, "{configFile}", null,
                                      null,
                                      MSGID_DESCRIPTION_CONFIG_FILE);
      configFile.setHidden(true);
      argParser.addArgument(configFile);


      schemeName = new StringArgument("scheme", 's', "storageScheme", false,
                                      false, true, "{scheme}", null, null,
                                      MSGID_ENCPW_DESCRIPTION_SCHEME);
      argParser.addArgument(schemeName);


      authPasswordSyntax = new BooleanArgument("authpasswordsyntax", 'a',
                                               "authPasswordSyntax",
                                               MSGID_ENCPW_DESCRIPTION_AUTHPW);
      argParser.addArgument(authPasswordSyntax);


      useCompareResultCode =
           new BooleanArgument("usecompareresultcode", 'r',
                               "useCompareResultCode",
                               MSGID_ENCPW_DESCRIPTION_USE_COMPARE_RESULT);
      argParser.addArgument(useCompareResultCode);


      showUsage = new BooleanArgument("usage", OPTION_SHORT_HELP,
                                      OPTION_LONG_HELP,
                                      MSGID_DESCRIPTION_USAGE);
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage, out);
    }
    catch (ArgumentException ae)
    {
      int    msgID   = MSGID_CANNOT_INITIALIZE_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      int    msgID   = MSGID_ERROR_PARSING_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return 1;
    }


    // If we should just display usage or version information,
    // then we've already done it so just return without doing anything else.
    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
    }


    // Check for conflicting arguments.
    if (clearPassword.isPresent() && clearPasswordFile.isPresent())
    {
      int    msgID   = MSGID_TOOL_CONFLICTING_ARGS;
      String message = getMessage(msgID, clearPassword.getLongIdentifier(),
                                  clearPasswordFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    if (encodedPassword.isPresent() && encodedPasswordFile.isPresent())
    {
      int    msgID   = MSGID_TOOL_CONFLICTING_ARGS;
      String message = getMessage(msgID, encodedPassword.getLongIdentifier(),
                                  encodedPasswordFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // If we are not going to just list the storage schemes, then the clear-text
    // password must have been provided.  If we're going to encode a password,
    // then the scheme must have also been provided.
    ASN1OctetString clearPW = null;
    if (! listSchemes.isPresent())
    {
      if (clearPassword.hasValue())
      {
        clearPW = new ASN1OctetString(clearPassword.getValue());
      }
      else if (clearPasswordFile.hasValue())
      {
        clearPW = new ASN1OctetString(clearPasswordFile.getValue());
      }
      else
      {
        int    msgID = MSGID_ENCPW_NO_CLEAR_PW;
        String message = getMessage(msgID, clearPassword.getLongIdentifier(),
                                    clearPasswordFile.getLongIdentifier());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        err.println(argParser.getUsage());
        return 1;
      }

      if ((! encodedPassword.isPresent()) && (! encodedPasswordFile.isPresent())
           && (! schemeName.isPresent()))
      {
        int    msgID   = MSGID_ENCPW_NO_SCHEME;
        String message = getMessage(msgID, schemeName.getLongIdentifier());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        err.println(argParser.getUsage());
        return 1;
      }
    }


    // Determine whether we're encoding the clear-text password or comparing it
    // against an already-encoded password.
    boolean compareMode;
    ByteString encodedPW = null;
    if (encodedPassword.hasValue())
    {
      compareMode = true;
      encodedPW = new ASN1OctetString(encodedPassword.getValue());
    }
    else if (encodedPasswordFile.hasValue())
    {
      compareMode = true;
      encodedPW = new ASN1OctetString(encodedPasswordFile.getValue());
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
        directoryServer.bootstrapClient();
        directoryServer.initializeJMX();
      }
      catch (Exception e)
      {
        int msgID = MSGID_SERVER_BOOTSTRAP_ERROR;
        String message = getMessage(msgID, getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }

      try
      {
        directoryServer.initializeConfiguration(configClass.getValue(),
                                                configFile.getValue());
      }
      catch (InitializationException ie)
      {
        int    msgID   = MSGID_CANNOT_LOAD_CONFIG;
        String message = getMessage(msgID, ie.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_CANNOT_LOAD_CONFIG;
        String message = getMessage(msgID, getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }



      // Initialize the Directory Server schema elements.
      try
      {
        directoryServer.initializeSchema();
      }
      catch (ConfigException ce)
      {
        int    msgID   = MSGID_CANNOT_LOAD_SCHEMA;
        String message = getMessage(msgID, ce.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (InitializationException ie)
      {
        int    msgID   = MSGID_CANNOT_LOAD_SCHEMA;
        String message = getMessage(msgID, ie.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_CANNOT_LOAD_SCHEMA;
        String message = getMessage(msgID, getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }


      // Initialize the Directory Server core configuration.
      try
      {
        CoreConfigManager coreConfigManager = new CoreConfigManager();
        coreConfigManager.initializeCoreConfig();
      }
      catch (ConfigException ce)
      {
        int    msgID   = MSGID_CANNOT_INITIALIZE_CORE_CONFIG;
        String message = getMessage(msgID, ce.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (InitializationException ie)
      {
        int    msgID   = MSGID_CANNOT_INITIALIZE_CORE_CONFIG;
        String message = getMessage(msgID, ie.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_CANNOT_INITIALIZE_CORE_CONFIG;
        String message = getMessage(msgID, getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }


      // Initialize the password storage schemes.
      try
      {
        PasswordStorageSchemeConfigManager storageSchemeConfigManager =
             new PasswordStorageSchemeConfigManager();
        storageSchemeConfigManager.initializePasswordStorageSchemes();
      }
      catch (ConfigException ce)
      {
        int    msgID   = MSGID_ENCPW_CANNOT_INITIALIZE_STORAGE_SCHEMES;
        String message = getMessage(msgID, ce.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (InitializationException ie)
      {
        int    msgID   = MSGID_ENCPW_CANNOT_INITIALIZE_STORAGE_SCHEMES;
        String message = getMessage(msgID, ie.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_ENCPW_CANNOT_INITIALIZE_STORAGE_SCHEMES;
        String message = getMessage(msgID, getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
    }


    // If we are only trying to list the available schemes, then do so and exit.
    if (listSchemes.isPresent())
    {
      if (authPasswordSyntax.isPresent())
      {
        ConcurrentHashMap<String,PasswordStorageScheme> storageSchemes =
             DirectoryServer.getAuthPasswordStorageSchemes();
        if (storageSchemes.isEmpty())
        {
          int msgID = MSGID_ENCPW_NO_STORAGE_SCHEMES;
          String message = getMessage(msgID);
          err.println(wrapText(message, MAX_LINE_WIDTH));
        }
        else
        {
          int size = storageSchemes.size();

          ArrayList<String> nameList = new ArrayList<String>(size);
          for (PasswordStorageScheme s : storageSchemes.values())
          {
            nameList.add(s.getAuthPasswordSchemeName());
          }

          String[] nameArray = new String[size];
          nameList.toArray(nameArray);
          Arrays.sort(nameArray);

          for (String storageSchemeName : nameArray)
          {
            out.println(storageSchemeName);
          }
        }

        return 0;
      }
      else
      {
        ConcurrentHashMap<String,PasswordStorageScheme> storageSchemes =
             DirectoryServer.getPasswordStorageSchemes();
        if (storageSchemes.isEmpty())
        {
          int msgID = MSGID_ENCPW_NO_STORAGE_SCHEMES;
          String message = getMessage(msgID);
          err.println(wrapText(message, MAX_LINE_WIDTH));
        }
        else
        {
          int size = storageSchemes.size();

          ArrayList<String> nameList = new ArrayList<String>(size);
          for (PasswordStorageScheme s : storageSchemes.values())
          {
            nameList.add(s.getStorageSchemeName());
          }

          String[] nameArray = new String[size];
          nameList.toArray(nameArray);
          Arrays.sort(nameArray);

          for (String storageSchemeName : nameArray)
          {
            out.println(storageSchemeName);
          }
        }

        return 0;
      }
    }


    // Either encode the clear-text password using the provided scheme, or
    // compare the clear-text password against the encoded password.
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
               AuthPasswordSyntax.decodeAuthPassword(encodedPW.stringValue());
          scheme    = authPWElements[0].toString();
          authInfo  = authPWElements[1].toString();
          authValue = authPWElements[2].toString();
        }
        catch (DirectoryException de)
        {
          int    msgID   = MSGID_ENCPW_INVALID_ENCODED_AUTHPW;
          String message = getMessage(msgID, de.getErrorMessage());
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }
        catch (Exception e)
        {
          int    msgID   = MSGID_ENCPW_INVALID_ENCODED_AUTHPW;
          String message = getMessage(msgID, String.valueOf(e));
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }

        PasswordStorageScheme storageScheme =
             DirectoryServer.getAuthPasswordStorageScheme(scheme);
        if (storageScheme == null)
        {
          int    msgID   = MSGID_ENCPW_NO_SUCH_AUTH_SCHEME;
          String message = getMessage(msgID, scheme);
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }

        if (storageScheme.authPasswordMatches(clearPW, authInfo, authValue))
        {
          int    msgID   = MSGID_ENCPW_PASSWORDS_MATCH;
          String message = getMessage(msgID);
          out.println(message);

          if (useCompareResultCode.isPresent())
          {
            return LDAPResultCode.COMPARE_TRUE;
          }
          else
          {
            return 0;
          }
        }
        else
        {
          int    msgID   = MSGID_ENCPW_PASSWORDS_DO_NOT_MATCH;
          String message = getMessage(msgID);
          out.println(message);

          if (useCompareResultCode.isPresent())
          {
            return LDAPResultCode.COMPARE_FALSE;
          }
          else
          {
            return 0;
          }
        }
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
                 UserPasswordSyntax.decodeUserPassword(encodedPW.stringValue());
            encodedPWString = userPWElements[1];

            storageScheme =
                 DirectoryServer.getPasswordStorageScheme(userPWElements[0]);
            if (storageScheme == null)
            {
              int    msgID   = MSGID_ENCPW_NO_SUCH_SCHEME;
              String message = getMessage(msgID, userPWElements[0]);
              err.println(wrapText(message, MAX_LINE_WIDTH));
              return 1;
            }
          }
          catch (DirectoryException de)
          {
            int    msgID   = MSGID_ENCPW_INVALID_ENCODED_USERPW;
            String message = getMessage(msgID, de.getErrorMessage());
            err.println(wrapText(message, MAX_LINE_WIDTH));
            return 1;
          }
          catch (Exception e)
          {
            int    msgID   = MSGID_ENCPW_INVALID_ENCODED_USERPW;
            String message = getMessage(msgID, String.valueOf(e));
            err.println(wrapText(message, MAX_LINE_WIDTH));
            return 1;
          }
        }
        else
        {
          if (! schemeName.isPresent())
          {
            int    msgID   = MSGID_ENCPW_NO_SCHEME;
            String message = getMessage(msgID, schemeName.getLongIdentifier());
            err.println(wrapText(message, MAX_LINE_WIDTH));
            return 1;
          }

          encodedPWString = encodedPW.toString();

          String scheme = toLowerCase(schemeName.getValue());
          storageScheme = directoryServer.getPasswordStorageScheme(scheme);
          if (storageScheme == null)
          {
            int    msgID   = MSGID_ENCPW_NO_SUCH_SCHEME;
            String message = getMessage(msgID, scheme);
            err.println(wrapText(message, MAX_LINE_WIDTH));
            return 1;
          }
        }

        if (storageScheme.passwordMatches(clearPW,
                                          new ASN1OctetString(encodedPWString)))
        {
          int    msgID   = MSGID_ENCPW_PASSWORDS_MATCH;
          String message = getMessage(msgID);
          out.println(message);

          if (useCompareResultCode.isPresent())
          {
            return LDAPResultCode.COMPARE_TRUE;
          }
          else
          {
            return 0;
          }
        }
        else
        {
          int    msgID   = MSGID_ENCPW_PASSWORDS_DO_NOT_MATCH;
          String message = getMessage(msgID);
          out.println(message);

          if (useCompareResultCode.isPresent())
          {
            return LDAPResultCode.COMPARE_FALSE;
          }
          else
          {
            return 0;
          }
        }
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
          int    msgID   = MSGID_ENCPW_NO_SUCH_AUTH_SCHEME;
          String message = getMessage(msgID, scheme);
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }
      }
      else
      {
        String scheme = toLowerCase(schemeName.getValue());
        storageScheme = DirectoryServer.getPasswordStorageScheme(scheme);
        if (storageScheme == null)
        {
          int    msgID   = MSGID_ENCPW_NO_SUCH_SCHEME;
          String message = getMessage(msgID, scheme);
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }
      }

      if (authPasswordSyntax.isPresent())
      {
        try
        {
          encodedPW = storageScheme.encodeAuthPassword(clearPW);

          int    msgID   = MSGID_ENCPW_ENCODED_PASSWORD;
          String message = getMessage(msgID, encodedPW.stringValue());
          out.println(message);
        }
        catch (DirectoryException de)
        {
          int msgID = MSGID_ENCPW_CANNOT_ENCODE;
          String message = getMessage(msgID, de.getErrorMessage());
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }
        catch (Exception e)
        {
          int msgID = MSGID_ENCPW_CANNOT_ENCODE;
          String message = getMessage(msgID, getExceptionMessage(e));
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }
      }
      else
      {
        try
        {
          encodedPW = storageScheme.encodePasswordWithScheme(clearPW);

          int    msgID   = MSGID_ENCPW_ENCODED_PASSWORD;
          String message = getMessage(msgID, encodedPW.stringValue());
          out.println(message);
        }
        catch (DirectoryException de)
        {
          int msgID = MSGID_ENCPW_CANNOT_ENCODE;
          String message = getMessage(msgID, de.getErrorMessage());
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }
        catch (Exception e)
        {
          int msgID = MSGID_ENCPW_CANNOT_ENCODE;
          String message = getMessage(msgID, getExceptionMessage(e));
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }
      }
    }

    // If we've gotten here, then all processing completed successfully.
    return 0;
  }
}

