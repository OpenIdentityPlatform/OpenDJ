package com.sun.opends.sdk.tools;

import static com.sun.opends.sdk.messages.Messages.*;
import static com.sun.opends.sdk.tools.ToolConstants.*;
import static com.sun.opends.sdk.tools.Utils.*;

import java.io.InputStream;
import java.io.OutputStream;

import org.opends.sdk.*;
import org.opends.sdk.controls.Control;
import org.opends.sdk.extensions.PasswordModifyRequest;
import org.opends.sdk.extensions.PasswordModifyResult;

import com.sun.opends.sdk.util.Message;



/**
 * A tool that can be used to issue LDAP password modify extended
 * requests to the Directory Server. It exposes the three primary
 * options available for this operation, which are:
 * <UL>
 * <LI>The user identity whose password should be changed.</LI>
 * <LI>The current password for the user.</LI>
 * <LI>The new password for the user.
 * </UL>
 * All of these are optional components that may be included or omitted
 * from the request.
 */
public class LDAPPasswordModify extends ConsoleApplication
{
  private BooleanArgument verbose;

  /**
   * Parses the command-line arguments, establishes a connection to the
   * Directory Server, sends the password modify request, and reads the
   * response.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int retCode = mainPasswordModify(args, System.in, System.out, System.err);

    if (retCode != 0)
    {
      System.exit(filterExitCode(retCode));
    }
  }



  /**
   * Parses the command-line arguments, establishes a connection to the
   * Directory Server, sends the password modify request, and reads the
   * response.
   *
   * @param  args  The command-line arguments provided to this program.
   *
   * @return  An integer value of zero if everything completed successfully, or
   *          a nonzero value if an error occurred.
   */
  static int mainPasswordModify(String[] args)
  {
    return mainPasswordModify(args, System.in, System.out, System.err);
  }



  /**
   * Parses the provided command-line arguments and uses that
   * information to run the LDAPPasswordModify tool.
   *
   * @param args
   *          The command-line arguments provided to this program.
   *          specified, the number of matching entries should be
   *          returned or not.
   * @param inStream
   *          The input stream to use for standard input, or
   *          <CODE>null</CODE> if standard input is not needed.
   * @param outStream
   *          The output stream to use for standard output, or
   *          <CODE>null</CODE> if standard output is not needed.
   * @param errStream
   *          The output stream to use for standard error, or
   *          <CODE>null</CODE> if standard error is not needed.
   * @return The error code.
   */
  static int mainPasswordModify(String[] args, InputStream inStream,
                                       OutputStream outStream, OutputStream errStream)
  {
    return new LDAPPasswordModify(inStream, outStream, errStream).run(args);
  }



  private LDAPPasswordModify(InputStream in, OutputStream out, OutputStream err)
  {
    super(in, out, err);

  }

  private int run(String[] args)
  {
    // Create the command-line argument parser for use with this
    // program.
    Message toolDescription = INFO_LDAPPWMOD_TOOL_DESCRIPTION.get();
    ArgumentParser argParser =
        new ArgumentParser(LDAPPasswordModify.class.getName(), toolDescription,
            false);
    ArgumentParserConnectionFactory connectionFactory;

    FileBasedArgument currentPWFile;
    FileBasedArgument newPWFile;
    BooleanArgument showUsage;
    IntegerArgument version;
    StringArgument    currentPW;
    StringArgument controlStr;
    StringArgument    newPW;
    StringArgument proxyAuthzID;
    StringArgument propertiesFileArgument;
    BooleanArgument noPropertiesFileArgument;

    try
    {
      connectionFactory =
          new ArgumentParserConnectionFactory(argParser, this);
      propertiesFileArgument =
          new StringArgument("propertiesFilePath", null,
              OPTION_LONG_PROP_FILE_PATH, false, false, true,
              INFO_PROP_FILE_PATH_PLACEHOLDER.get(), null, null,
              INFO_DESCRIPTION_PROP_FILE_PATH.get());
      argParser.addArgument(propertiesFileArgument);
      argParser.setFilePropertiesArgument(propertiesFileArgument);

      noPropertiesFileArgument =
          new BooleanArgument("noPropertiesFileArgument", null,
              OPTION_LONG_NO_PROP_FILE, INFO_DESCRIPTION_NO_PROP_FILE
                  .get());
      argParser.addArgument(noPropertiesFileArgument);
      argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

      newPW = new StringArgument("newpw", 'n', "newPassword", false, false,
          true, INFO_NEW_PASSWORD_PLACEHOLDER.get(),
          null, null,
          INFO_LDAPPWMOD_DESCRIPTION_NEWPW.get());
      newPW.setPropertyName("newPassword");
      argParser.addArgument(newPW);


      newPWFile = new FileBasedArgument(
          "newpwfile", 'F', "newPasswordFile",
          false, false, INFO_FILE_PLACEHOLDER.get(), null, null,
          INFO_LDAPPWMOD_DESCRIPTION_NEWPWFILE.get());
      newPWFile.setPropertyName("newPasswordFile");
      argParser.addArgument(newPWFile);


      currentPW =
          new StringArgument("currentpw", 'c', "currentPassword", false, false,
              true, INFO_CURRENT_PASSWORD_PLACEHOLDER.get(),
              null,  null,
              INFO_LDAPPWMOD_DESCRIPTION_CURRENTPW.get());
      currentPW.setPropertyName("currentPassword");
      argParser.addArgument(currentPW);


      currentPWFile =
          new FileBasedArgument(
              "currentpwfile", 'C', "currentPasswordFile",
              false, false, INFO_FILE_PLACEHOLDER.get(), null, null,
              INFO_LDAPPWMOD_DESCRIPTION_CURRENTPWFILE.get());
      currentPWFile.setPropertyName("currentPasswordFile");
      argParser.addArgument(currentPWFile);

      proxyAuthzID =
          new StringArgument("authzid", 'a', "authzID", false, false,
              true, INFO_PROXYAUTHID_PLACEHOLDER.get(),
              null, null,
              INFO_LDAPPWMOD_DESCRIPTION_AUTHZID.get());
      proxyAuthzID.setPropertyName("authzID");
      argParser.addArgument(proxyAuthzID);

      controlStr =
          new StringArgument("control", 'J', "control", false, true,
              true, INFO_LDAP_CONTROL_PLACEHOLDER.get(), null, null,
              INFO_DESCRIPTION_CONTROLS.get());
      controlStr.setPropertyName("control");
      argParser.addArgument(controlStr);

      version =
          new IntegerArgument("version", OPTION_SHORT_PROTOCOL_VERSION,
              OPTION_LONG_PROTOCOL_VERSION, false, false, true,
              INFO_PROTOCOL_VERSION_PLACEHOLDER.get(), 3, null,
              INFO_DESCRIPTION_VERSION.get());
      version.setPropertyName(OPTION_LONG_PROTOCOL_VERSION);
      argParser.addArgument(version);

      verbose =
          new BooleanArgument("verbose", 'v', "verbose",
              INFO_DESCRIPTION_VERBOSE.get());
      verbose.setPropertyName("verbose");
      argParser.addArgument(verbose);

      showUsage =
          new BooleanArgument("showUsage", OPTION_SHORT_HELP,
              OPTION_LONG_HELP, INFO_DESCRIPTION_SHOWUSAGE.get());
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage, getOutputStream());
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
      println(message);
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
      connectionFactory.validate();
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());
      println(message);
      println(argParser.getUsageMessage());
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    // If we should just display usage or version information,
    // then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
    }

    PasswordModifyRequest request = new PasswordModifyRequest();
    try
    {
      int versionNumber = version.getIntValue();
      if (versionNumber != 2 && versionNumber != 3)
      {
        println(ERR_DESCRIPTION_INVALID_VERSION.get(String
            .valueOf(versionNumber)));
        return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
      }
    }
    catch (ArgumentException ae)
    {
      println(ERR_DESCRIPTION_INVALID_VERSION.get(String
          .valueOf(version.getValue())));
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    if (controlStr.isPresent())
    {
      for (String ctrlString : controlStr.getValues())
      {
        try
        {
          Control ctrl = Utils.getControl(ctrlString);
          request.addControl(ctrl);
        }
        catch (DecodeException de)
        {
          Message message =
              ERR_TOOL_INVALID_CONTROL_STRING.get(ctrlString);
          println(message);
          ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }
      }
    }

    if (newPW.isPresent() && newPWFile.isPresent())
    {
      Message message = ERR_LDAPPWMOD_CONFLICTING_ARGS.get(
          newPW.getLongIdentifier(),
          newPWFile.getLongIdentifier());
      println(message);
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    if (currentPW.isPresent() && currentPWFile.isPresent())
    {
      Message message = ERR_LDAPPWMOD_CONFLICTING_ARGS.get(
          currentPW.getLongIdentifier(),
          currentPWFile.getLongIdentifier());
      println(message);
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    Connection connection;
    try
    {
      connection = connectionFactory.getConnection();
    }
    catch (ErrorResultException ere)
    {
      return Utils.printErrorMessage(this, ere);
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

    PasswordModifyResult result;
    try
    {
      try
      {
        result = connection.extendedRequest(request);
      }
      catch (InterruptedException e)
      {
        // This shouldn't happen because there are no other threads to
        // interrupt this one.
        result = new PasswordModifyResult(
            ResultCode.CLIENT_SIDE_USER_CANCELLED).setCause(e)
            .setDiagnosticMessage(e.getLocalizedMessage());
        throw ErrorResultException.wrap(result);
      }
    }
    catch (ErrorResultException e)
    {
      Message message =
          ERR_LDAPPWMOD_FAILED.get(e.getResult().getResultCode().intValue(),
              e.getResult().getResultCode().toString());
      println(message);

      String errorMessage = e.getResult().getDiagnosticMessage();
      if ((errorMessage != null) && (errorMessage.length() > 0))
      {
        message = ERR_LDAPPWMOD_FAILURE_ERROR_MESSAGE.get(errorMessage);
        println(message);
      }

      String matchedDN = e.getResult().getMatchedDN();
      if (matchedDN != null && matchedDN.length() > 0)
      {
        message = ERR_LDAPPWMOD_FAILURE_MATCHED_DN.get(matchedDN);
        println(message);
      }
      return e.getResult().getResultCode().intValue();
    }

    Message message = INFO_LDAPPWMOD_SUCCESSFUL.get();
    println(message);

    String additionalInfo = result.getDiagnosticMessage();
    if ((additionalInfo != null) && (additionalInfo.length() > 0))
    {

      message = INFO_LDAPPWMOD_ADDITIONAL_INFO.get(additionalInfo);
      println(message);
    }

    if(result.getGenPassword() != null)
    {
      message = INFO_LDAPPWMOD_GENERATED_PASSWORD.get(result.getGenPassword().toString());
      println(message);
    }

    return 0;
  }



  /**
   * Indicates whether or not the user has requested advanced mode.
   *
   * @return Returns <code>true</code> if the user has requested
   *         advanced mode.
   */
  public boolean isAdvancedMode()
  {
    return false;
  }



  /**
   * Indicates whether or not the user has requested interactive
   * behavior.
   *
   * @return Returns <code>true</code> if the user has requested
   *         interactive behavior.
   */
  public boolean isInteractive()
  {
    return false;
  }



  /**
   * Indicates whether or not this console application is running in its
   * menu-driven mode. This can be used to dictate whether output should
   * go to the error stream or not. In addition, it may also dictate
   * whether or not sub-menus should display a cancel option as well as
   * a quit option.
   *
   * @return Returns <code>true</code> if this console application is
   *         running in its menu-driven mode.
   */
  public boolean isMenuDrivenMode()
  {
    return false;
  }



  /**
   * Indicates whether or not the user has requested quiet output.
   *
   * @return Returns <code>true</code> if the user has requested quiet
   *         output.
   */
  public boolean isQuiet()
  {
    return false;
  }



  /**
   * Indicates whether or not the user has requested script-friendly
   * output.
   *
   * @return Returns <code>true</code> if the user has requested
   *         script-friendly output.
   */
  public boolean isScriptFriendly()
  {
    return false;
  }



  /**
   * Indicates whether or not the user has requested verbose output.
   *
   * @return Returns <code>true</code> if the user has requested verbose
   *         output.
   */
  public boolean isVerbose()
  {
    return verbose.isPresent();
  }
}
