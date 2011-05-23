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
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package com.sun.opends.sdk.tools;



import static org.forgerock.opendj.ldap.CoreMessages.*;
import static com.sun.opends.sdk.tools.ToolConstants.*;
import static com.sun.opends.sdk.tools.Utils.filterExitCode;

import java.io.InputStream;
import java.io.OutputStream;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.requests.PasswordModifyExtendedRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.PasswordModifyExtendedResult;
import org.forgerock.opendj.ldap.responses.Responses;



/**
 * A tool that can be used to issue LDAP password modify extended requests to
 * the Directory Server. It exposes the three primary options available for this
 * operation, which are:
 * <UL>
 * <LI>The user identity whose password should be changed.</LI>
 * <LI>The current password for the user.</LI>
 * <LI>The new password for the user.
 * </UL>
 * All of these are optional components that may be included or omitted from the
 * request.
 */
public final class LDAPPasswordModify extends ConsoleApplication
{
  /**
   * Parses the command-line arguments, establishes a connection to the
   * Directory Server, sends the password modify request, and reads the
   * response.
   *
   * @param args
   *          The command-line arguments provided to this program.
   */
  public static void main(final String[] args)
  {
    final int retCode = mainPasswordModify(args, System.in, System.out,
        System.err);
    System.exit(filterExitCode(retCode));
  }



  /**
   * Parses the command-line arguments, establishes a connection to the
   * Directory Server, sends the password modify request, and reads the
   * response.
   *
   * @param args
   *          The command-line arguments provided to this program.
   * @return An integer value of zero if everything completed successfully, or a
   *         nonzero value if an error occurred.
   */
  static int mainPasswordModify(final String[] args)
  {
    return mainPasswordModify(args, System.in, System.out, System.err);
  }



  /**
   * Parses the provided command-line arguments and uses that information to run
   * the LDAPPasswordModify tool.
   *
   * @param args
   *          The command-line arguments provided to this program. specified,
   *          the number of matching entries should be returned or not.
   * @param inStream
   *          The input stream to use for standard input, or <CODE>null</CODE>
   *          if standard input is not needed.
   * @param outStream
   *          The output stream to use for standard output, or <CODE>null</CODE>
   *          if standard output is not needed.
   * @param errStream
   *          The output stream to use for standard error, or <CODE>null</CODE>
   *          if standard error is not needed.
   * @return The error code.
   */
  static int mainPasswordModify(final String[] args,
      final InputStream inStream, final OutputStream outStream,
      final OutputStream errStream)
  {
    return new LDAPPasswordModify(inStream, outStream, errStream).run(args);
  }



  private BooleanArgument verbose;



  private LDAPPasswordModify(final InputStream in, final OutputStream out,
      final OutputStream err)
  {
    super(in, out, err);

  }



  /**
   * Indicates whether or not the user has requested advanced mode.
   *
   * @return Returns <code>true</code> if the user has requested advanced mode.
   */
  @Override
  public boolean isAdvancedMode()
  {
    return false;
  }



  /**
   * Indicates whether or not the user has requested interactive behavior.
   *
   * @return Returns <code>true</code> if the user has requested interactive
   *         behavior.
   */
  @Override
  public boolean isInteractive()
  {
    return false;
  }



  /**
   * Indicates whether or not this console application is running in its
   * menu-driven mode. This can be used to dictate whether output should go to
   * the error stream or not. In addition, it may also dictate whether or not
   * sub-menus should display a cancel option as well as a quit option.
   *
   * @return Returns <code>true</code> if this console application is running in
   *         its menu-driven mode.
   */
  @Override
  public boolean isMenuDrivenMode()
  {
    return false;
  }



  /**
   * Indicates whether or not the user has requested quiet output.
   *
   * @return Returns <code>true</code> if the user has requested quiet output.
   */
  @Override
  public boolean isQuiet()
  {
    return false;
  }



  /**
   * Indicates whether or not the user has requested script-friendly output.
   *
   * @return Returns <code>true</code> if the user has requested script-friendly
   *         output.
   */
  @Override
  public boolean isScriptFriendly()
  {
    return false;
  }



  /**
   * Indicates whether or not the user has requested verbose output.
   *
   * @return Returns <code>true</code> if the user has requested verbose output.
   */
  @Override
  public boolean isVerbose()
  {
    return verbose.isPresent();
  }



  private int run(final String[] args)
  {
    // Create the command-line argument parser for use with this
    // program.
    final LocalizableMessage toolDescription = INFO_LDAPPWMOD_TOOL_DESCRIPTION
        .get();
    final ArgumentParser argParser = new ArgumentParser(
        LDAPPasswordModify.class.getName(), toolDescription, false);
    ConnectionFactoryProvider connectionFactoryProvider;
    ConnectionFactory connectionFactory;

    FileBasedArgument currentPWFile;
    FileBasedArgument newPWFile;
    BooleanArgument showUsage;
    IntegerArgument version;
    StringArgument currentPW;
    StringArgument controlStr;
    StringArgument newPW;
    StringArgument proxyAuthzID;
    StringArgument propertiesFileArgument;
    BooleanArgument noPropertiesFileArgument;

    try
    {
      connectionFactoryProvider =
          new ConnectionFactoryProvider(argParser, this);
      propertiesFileArgument = new StringArgument("propertiesFilePath", null,
          OPTION_LONG_PROP_FILE_PATH, false, false, true,
          INFO_PROP_FILE_PATH_PLACEHOLDER.get(), null, null,
          INFO_DESCRIPTION_PROP_FILE_PATH.get());
      argParser.addArgument(propertiesFileArgument);
      argParser.setFilePropertiesArgument(propertiesFileArgument);

      noPropertiesFileArgument = new BooleanArgument(
          "noPropertiesFileArgument", null, OPTION_LONG_NO_PROP_FILE,
          INFO_DESCRIPTION_NO_PROP_FILE.get());
      argParser.addArgument(noPropertiesFileArgument);
      argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

      newPW = new StringArgument("newpw", 'n', "newPassword", false, false,
          true, INFO_NEW_PASSWORD_PLACEHOLDER.get(), null, null,
          INFO_LDAPPWMOD_DESCRIPTION_NEWPW.get());
      newPW.setPropertyName("newPassword");
      argParser.addArgument(newPW);

      newPWFile = new FileBasedArgument("newpwfile", 'F', "newPasswordFile",
          false, false, INFO_FILE_PLACEHOLDER.get(), null, null,
          INFO_LDAPPWMOD_DESCRIPTION_NEWPWFILE.get());
      newPWFile.setPropertyName("newPasswordFile");
      argParser.addArgument(newPWFile);

      currentPW = new StringArgument("currentpw", 'c', "currentPassword",
          false, false, true, INFO_CURRENT_PASSWORD_PLACEHOLDER.get(), null,
          null, INFO_LDAPPWMOD_DESCRIPTION_CURRENTPW.get());
      currentPW.setPropertyName("currentPassword");
      argParser.addArgument(currentPW);

      currentPWFile = new FileBasedArgument("currentpwfile", 'C',
          "currentPasswordFile", false, false, INFO_FILE_PLACEHOLDER.get(),
          null, null, INFO_LDAPPWMOD_DESCRIPTION_CURRENTPWFILE.get());
      currentPWFile.setPropertyName("currentPasswordFile");
      argParser.addArgument(currentPWFile);

      proxyAuthzID = new StringArgument("authzid", 'a', "authzID", false,
          false, true, INFO_PROXYAUTHID_PLACEHOLDER.get(), null, null,
          INFO_LDAPPWMOD_DESCRIPTION_AUTHZID.get());
      proxyAuthzID.setPropertyName("authzID");
      argParser.addArgument(proxyAuthzID);

      controlStr = new StringArgument("control", 'J', "control", false, true,
          true, INFO_LDAP_CONTROL_PLACEHOLDER.get(), null, null,
          INFO_DESCRIPTION_CONTROLS.get());
      controlStr.setPropertyName("control");
      argParser.addArgument(controlStr);

      version = new IntegerArgument("version", OPTION_SHORT_PROTOCOL_VERSION,
          OPTION_LONG_PROTOCOL_VERSION, false, false, true,
          INFO_PROTOCOL_VERSION_PLACEHOLDER.get(), 3, null,
          INFO_DESCRIPTION_VERSION.get());
      version.setPropertyName(OPTION_LONG_PROTOCOL_VERSION);
      argParser.addArgument(version);

      verbose = new BooleanArgument("verbose", 'v', "verbose",
          INFO_DESCRIPTION_VERBOSE.get());
      verbose.setPropertyName("verbose");
      argParser.addArgument(verbose);

      showUsage = new BooleanArgument("showUsage", OPTION_SHORT_HELP,
          OPTION_LONG_HELP, INFO_DESCRIPTION_SHOWUSAGE.get());
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage, getOutputStream());
    }
    catch (final ArgumentException ae)
    {
      final LocalizableMessage message = ERR_CANNOT_INITIALIZE_ARGS.get(ae
          .getMessage());
      println(message);
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);

      // If we should just display usage or version information,
      // then print it and exit.
      if (argParser.usageOrVersionDisplayed())
      {
        return 0;
      }

      connectionFactory =
          connectionFactoryProvider.getAuthenticatedConnectionFactory();
    }
    catch (final ArgumentException ae)
    {
      final LocalizableMessage message = ERR_ERROR_PARSING_ARGS.get(ae
          .getMessage());
      println(message);
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    final PasswordModifyExtendedRequest request = Requests
        .newPasswordModifyExtendedRequest();
    try
    {
      final int versionNumber = version.getIntValue();
      if (versionNumber != 2 && versionNumber != 3)
      {
        println(ERR_DESCRIPTION_INVALID_VERSION.get(String
            .valueOf(versionNumber)));
        return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
      }
    }
    catch (final ArgumentException ae)
    {
      println(ERR_DESCRIPTION_INVALID_VERSION.get(String.valueOf(version
          .getValue())));
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    if (controlStr.isPresent())
    {
      for (final String ctrlString : controlStr.getValues())
      {
        try
        {
          final Control ctrl = Utils.getControl(ctrlString);
          request.addControl(ctrl);
        }
        catch (final DecodeException de)
        {
          final LocalizableMessage message = ERR_TOOL_INVALID_CONTROL_STRING
              .get(ctrlString);
          println(message);
          ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }
      }
    }

    if (newPW.isPresent() && newPWFile.isPresent())
    {
      final LocalizableMessage message = ERR_LDAPPWMOD_CONFLICTING_ARGS.get(
          newPW.getLongIdentifier(), newPWFile.getLongIdentifier());
      println(message);
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    if (currentPW.isPresent() && currentPWFile.isPresent())
    {
      final LocalizableMessage message = ERR_LDAPPWMOD_CONFLICTING_ARGS.get(
          currentPW.getLongIdentifier(), currentPWFile.getLongIdentifier());
      println(message);
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    Connection connection;
    try
    {
      connection = connectionFactory.getConnection();
    }
    catch (final ErrorResultException ere)
    {
      return Utils.printErrorMessage(this, ere);
    }
    catch (final InterruptedException e)
    {
      // This shouldn't happen because there are no other threads to
      // interrupt this one.
      println(LocalizableMessage.raw(e.getLocalizedMessage()));
      return ResultCode.CLIENT_SIDE_USER_CANCELLED.intValue();
    }

    if (proxyAuthzID.isPresent())
    {
      request.setUserIdentity(proxyAuthzID.getValue());
    }

    if (currentPW.isPresent())
    {
      request.setOldPassword(ByteString.valueOf(currentPW.getValue()));
    }
    else if (currentPWFile.isPresent())
    {
      request.setOldPassword(ByteString.valueOf(currentPWFile.getValue()));
    }

    if (newPW.isPresent())
    {
      request.setNewPassword(ByteString.valueOf(newPW.getValue()));
    }
    else if (newPWFile.isPresent())
    {
      request.setNewPassword(ByteString.valueOf(newPWFile.getValue()));
    }

    PasswordModifyExtendedResult result;
    try
    {
      try
      {
        result = connection.extendedRequest(request);
      }
      catch (final InterruptedException e)
      {
        // This shouldn't happen because there are no other threads to
        // interrupt this one.
        result = Responses.newPasswordModifyExtendedResult(
            ResultCode.CLIENT_SIDE_USER_CANCELLED).setCause(e)
            .setDiagnosticMessage(e.getLocalizedMessage());
        throw ErrorResultException.wrap(result);
      }
    }
    catch (final ErrorResultException e)
    {
      LocalizableMessage message = ERR_LDAPPWMOD_FAILED
          .get(e.getResult().getResultCode().intValue(), e.getResult()
              .getResultCode().toString());
      println(message);

      final String errorMessage = e.getResult().getDiagnosticMessage();
      if ((errorMessage != null) && (errorMessage.length() > 0))
      {
        message = ERR_LDAPPWMOD_FAILURE_ERROR_MESSAGE.get(errorMessage);
        println(message);
      }

      final String matchedDN = e.getResult().getMatchedDN();
      if (matchedDN != null && matchedDN.length() > 0)
      {
        message = ERR_LDAPPWMOD_FAILURE_MATCHED_DN.get(matchedDN);
        println(message);
      }
      return e.getResult().getResultCode().intValue();
    }

    LocalizableMessage message = INFO_LDAPPWMOD_SUCCESSFUL.get();
    println(message);

    final String additionalInfo = result.getDiagnosticMessage();
    if ((additionalInfo != null) && (additionalInfo.length() > 0))
    {

      message = INFO_LDAPPWMOD_ADDITIONAL_INFO.get(additionalInfo);
      println(message);
    }

    if (result.getGeneratedPassword() != null)
    {
      message = INFO_LDAPPWMOD_GENERATED_PASSWORD.get(result
          .getGeneratedPassword().toString());
      println(message);
    }

    return 0;
  }
}
