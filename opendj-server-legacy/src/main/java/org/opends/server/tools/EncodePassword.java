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
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.tools;

import static com.forgerock.opendj.cli.CliMessages.INFO_FILE_PLACEHOLDER;
import static com.forgerock.opendj.cli.CommonArguments.*;
import static com.forgerock.opendj.cli.Utils.*;

import static org.opends.messages.ToolMessages.*;
import static org.opends.messages.ToolMessages.INFO_CONFIGFILE_PLACEHOLDER;
import static org.opends.messages.ToolMessages.INFO_DESCRIPTION_CONFIG_FILE;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.Console;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.DirectoryServer.DirectoryServerVersionHandler;
import org.opends.server.loggers.JDKLogging;
import org.opends.server.schema.AuthPasswordSyntax;
import org.opends.server.schema.UserPasswordSyntax;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.BuildVersion;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
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
    StringArgument    configFile           = null;
    StringArgument    schemeName           = null;

    // Create the command-line argument parser for use with this program.
    LocalizableMessage toolDescription = INFO_ENCPW_TOOL_DESCRIPTION.get();
    ArgumentParser argParser =
         new ArgumentParser("org.opends.server.tools.EncodePassword",
                            toolDescription, false);
    argParser.setShortToolDescription(REF_SHORT_DESC_ENCODE_PASSWORD.get());
    argParser.setVersionHandler(new DirectoryServerVersionHandler());

    // Initialize all the command-line argument types and register them with the parser.
    try
    {
      listSchemes =
              BooleanArgument.builder("listSchemes")
                      .shortIdentifier('l')
                      .description(INFO_ENCPW_DESCRIPTION_LISTSCHEMES.get())
                      .buildAndAddToParser(argParser);
      interactivePassword =
              BooleanArgument.builder("interactivePassword")
                      .shortIdentifier('i')
                      .description(INFO_ENCPW_DESCRIPTION_INPUT_PW.get())
                      .buildAndAddToParser(argParser);
      clearPassword =
              StringArgument.builder("clearPassword")
                      .shortIdentifier('c')
                      .description(INFO_ENCPW_DESCRIPTION_CLEAR_PW.get())
                      .valuePlaceholder(INFO_CLEAR_PWD.get())
                      .buildAndAddToParser(argParser);
      clearPasswordFile =
              FileBasedArgument.builder("clearPasswordFile")
                      .shortIdentifier('f')
                      .description(INFO_ENCPW_DESCRIPTION_CLEAR_PW_FILE.get())
                      .valuePlaceholder(INFO_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      encodedPassword =
              StringArgument.builder("encodedPassword")
                      .shortIdentifier('e')
                      .description(INFO_ENCPW_DESCRIPTION_ENCODED_PW.get())
                      .valuePlaceholder(INFO_ENCODED_PWD_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      encodedPasswordFile =
              FileBasedArgument.builder("encodedPasswordFile")
                      .shortIdentifier('E')
                      .description(INFO_ENCPW_DESCRIPTION_ENCODED_PW_FILE.get())
                      .valuePlaceholder(INFO_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      configFile =
              StringArgument.builder("configFile")
                      .shortIdentifier('F')
                      .description(INFO_DESCRIPTION_CONFIG_FILE.get())
                      .hidden()
                      .required()
                      .valuePlaceholder(INFO_CONFIGFILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      schemeName =
              StringArgument.builder("storageScheme")
                      .shortIdentifier('s')
                      .description(INFO_ENCPW_DESCRIPTION_SCHEME.get())
                      .valuePlaceholder(INFO_STORAGE_SCHEME_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      authPasswordSyntax =
              BooleanArgument.builder("authPasswordSyntax")
                      .shortIdentifier('a')
                      .description(INFO_ENCPW_DESCRIPTION_AUTHPW.get())
                      .buildAndAddToParser(argParser);
      useCompareResultCode =
              BooleanArgument.builder("useCompareResultCode")
                      .shortIdentifier('r')
                      .description(INFO_ENCPW_DESCRIPTION_USE_COMPARE_RESULT.get())
                      .buildAndAddToParser(argParser);

      showUsage = showUsageArgument();
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
      argParser.displayMessageAndUsageReference(err, ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
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
    try
    {
      throwIfArgumentsConflict(clearPassword, clearPasswordFile);
      throwIfArgumentsConflict(clearPassword, interactivePassword);
      throwIfArgumentsConflict(clearPasswordFile, interactivePassword);
      throwIfArgumentsConflict(encodedPassword, encodedPasswordFile);
    }
    catch (final ArgumentException conflict)
    {
      printWrappedText(err, conflict.getMessageObject());
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
      argParser.displayMessageAndUsageReference(err, ERR_ENCPW_NO_SCHEME.get(schemeName.getLongIdentifier()));
      return OPERATIONS_ERROR;
    }

    // Determine whether we're encoding the clear-text password or comparing it
    // against an already-encoded password.
    boolean compareMode;
    ByteString encodedPW = null;
    if (encodedPassword.hasValue())
    {
      compareMode = true;
      encodedPW = ByteString.valueOfUtf8(encodedPassword.getValue());
    }
    else if (encodedPasswordFile.hasValue())
    {
      compareMode = true;
      encodedPW = ByteString.valueOfUtf8(encodedPasswordFile.getValue());
    }
    else
    {
      compareMode = false;
    }

    if (initializeServer)
    {
      try
      {
        new DirectoryServer.InitializationBuilder(configFile.getValue())
            .requirePasswordStorageSchemes()
            .initialize();
      }
      catch (InitializationException ie)
      {
        printWrappedText(err, ERR_CANNOT_INITIALIZE_SERVER_COMPONENTS.get(getExceptionMessage(ie)));
        return OPERATIONS_ERROR;
      }
    }

    // If we are only trying to list the available schemes, then do so and exit.
    if (listSchemes.isPresent())
    {
      if (authPasswordSyntax.isPresent())
      {
        listPasswordStorageSchemes(out, err, DirectoryServer.getAuthPasswordStorageSchemes().values(), true);
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
        String[] authPWElements;
        try
        {
          authPWElements = AuthPasswordSyntax.decodeAuthPassword(encodedPW.toString());
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

        String scheme = authPWElements[0];
        String authInfo = authPWElements[1];
        String authValue = authPWElements[2];

        PasswordStorageScheme storageScheme =
             DirectoryServer.getAuthPasswordStorageScheme(scheme);
        if (storageScheme == null)
        {
          printWrappedText(err, ERR_ENCPW_NO_SUCH_AUTH_SCHEME.get(scheme));
          return OPERATIONS_ERROR;
        }

        if (clearPW == null)
        {
          clearPW = getClearPW(err, argParser, clearPassword, clearPasswordFile, interactivePassword);
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
          clearPW = getClearPW(err, argParser, clearPassword, clearPasswordFile, interactivePassword);
          if (clearPW == null)
          {
            return OPERATIONS_ERROR;
          }
        }
        boolean passwordMatches =
            storageScheme.passwordMatches(clearPW, ByteString
                .valueOfUtf8(encodedPWString));
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
            clearPW = getClearPW(err, argParser, clearPassword, clearPasswordFile, interactivePassword);
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
            clearPW = getClearPW(err, argParser, clearPassword, clearPasswordFile, interactivePassword);
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
      Collection<PasswordStorageScheme<?>> storageSchemes, boolean authPasswordSchemeName)
  {
    if (storageSchemes.isEmpty())
    {
      printWrappedText(err, ERR_ENCPW_NO_STORAGE_SCHEMES.get());
    }
    else
    {
      ArrayList<String> nameList = new ArrayList<>(storageSchemes.size());
      for (PasswordStorageScheme<?> s : storageSchemes)
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

  /**
   * Get the clear password.
   * @param err The error output.
   * @param argParser The argument parser.
   * @param clearPassword the clear password
   * @param clearPasswordFile the file in which the password in stored
   * @param interactivePassword indicate if the password should be asked
   *        interactively.
   * @return the password or null if an error occurs.
   */
  private static ByteString getClearPW(PrintStream err,
      ArgumentParser argParser, StringArgument clearPassword,
      FileBasedArgument clearPasswordFile, BooleanArgument interactivePassword)
  {
    if (clearPassword.hasValue())
    {
      return ByteString.valueOfUtf8(clearPassword.getValue());
    }
    else if (clearPasswordFile.hasValue())
    {
      return ByteString.valueOfUtf8(clearPasswordFile.getValue());
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
          return ByteString.valueOfUtf8(pwd1);
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
      argParser.displayMessageAndUsageReference(err, ERR_ENCPW_NO_CLEAR_PW.get(clearPassword.getLongIdentifier(),
                                      clearPasswordFile.getLongIdentifier(), interactivePassword.getLongIdentifier()));
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

  /** Thread that mask user input. */
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

    /** Begin masking until asked to stop. */
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

    /** Instruct the thread to stop masking. */
    public void stopMasking()
    {
      this.stop = true;
    }
  }
}
