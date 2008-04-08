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
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.tools;
import org.opends.messages.Message;



import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.ldap.ExtendedRequestProtocolOp;
import org.opends.server.protocols.ldap.ExtendedResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.args.Argument;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.LDAPConnectionArgumentParser;
import org.opends.server.util.args.MultiChoiceArgument;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.SubCommand;
import org.opends.server.util.args.SubCommandArgumentParser;

import static org.opends.server.extensions.
                   PasswordPolicyStateExtendedOperation.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.ToolConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides a tool that can be used to perform various kinds of
 * account management using the password policy state extended operation.
 */
public class ManageAccount
{
  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME =
       "org.opends.server.tools.ManageAccount";



  /**
   * The name of the subcommand that will be used to get all password policy
   * state information for the user.
   */
  private static final String SC_GET_ALL = "get-all";



  /**
   * The name of the subcommand that will be used to get the DN of the password
   * policy for a given user.
   */
  private static final String SC_GET_PASSWORD_POLICY_DN =
       "get-password-policy-dn";



  /**
   * The name of the subcommand that will be used to get the disabled state for
   * a user.
   */
  private static final String SC_GET_ACCOUNT_DISABLED_STATE =
       "get-account-is-disabled";



  /**
   * The name of the subcommand that will be used to set the disabled state for
   * a user.
   */
  private static final String SC_SET_ACCOUNT_DISABLED_STATE =
       "set-account-is-disabled";



  /**
   * The name of the subcommand that will be used to clear the disabled state
   * for a user.
   */
  private static final String SC_CLEAR_ACCOUNT_DISABLED_STATE =
       "clear-account-is-disabled";



  /**
   * The name of the subcommand that will be used to get the account expiration
   * time.
   */
  private static final String SC_GET_ACCOUNT_EXPIRATION_TIME =
       "get-account-expiration-time";



  /**
   * The name of the subcommand that will be used to set the account expiration
   * time.
   */
  private static final String SC_SET_ACCOUNT_EXPIRATION_TIME =
       "set-account-expiration-time";



  /**
   * The name of the subcommand that will be used to clear the account
   * expiration time.
   */
  private static final String SC_CLEAR_ACCOUNT_EXPIRATION_TIME =
       "clear-account-expiration-time";



  /**
   * The name of the subcommand that will be used to get the length of time
   * before the account expires.
   */
  private static final String SC_GET_SECONDS_UNTIL_ACCOUNT_EXPIRATION =
       "get-seconds-until-account-expiration";



  /**
   * The name of the subcommand that will be used to get the time the password
   * was last changed.
   */
  private static final String SC_GET_PASSWORD_CHANGED_TIME =
       "get-password-changed-time";



  /**
   * The name of the subcommand that will be used to set the time the password
   * was last changed.
   */
  private static final String SC_SET_PASSWORD_CHANGED_TIME =
       "set-password-changed-time";



  /**
   * The name of the subcommand that will be used to clear the time the password
   * was last changed.
   */
  private static final String SC_CLEAR_PASSWORD_CHANGED_TIME =
       "clear-password-changed-time";



  /**
   * The name of the subcommand that will be used to get the time the user was
   * first warned about an upcoming password expiration.
   */
  private static final String SC_GET_PASSWORD_EXP_WARNED_TIME =
       "get-password-expiration-warned-time";



  /**
   * The name of the subcommand that will be used to set the time the user was
   * first warned about an upcoming password expiration.
   */
  private static final String SC_SET_PASSWORD_EXP_WARNED_TIME =
       "set-password-expiration-warned-time";



  /**
   * The name of the subcommand that will be used to clear the time the user was
   * first warned about an upcoming password expiration.
   */
  private static final String SC_CLEAR_PASSWORD_EXP_WARNED_TIME =
       "clear-password-expiration-warned-time";



  /**
   * The name of the subcommand that will be used to get the length of time
   * before the password expires.
   */
  private static final String SC_GET_SECONDS_UNTIL_PASSWORD_EXPIRATION =
       "get-seconds-until-password-expiration";



  /**
   * The name of the subcommand that will be used to get the length of time
   * before the user is first warned about an upcoming password expiration.
   */
  private static final String SC_GET_SECONDS_UNTIL_PASSWORD_EXPIRATION_WARNING =
       "get-seconds-until-password-expiration-warning";



  /**
   * The name of the subcommand that will be used to get the authentication
   * failure times for the user.
   */
  private static final String SC_GET_AUTHENTICATION_FAILURE_TIMES =
       "get-authentication-failure-times";



  /**
   * The name of the subcommand that will be used to add an authentication
   * failure time for the user.
   */
  private static final String SC_ADD_AUTHENTICATION_FAILURE_TIME =
       "add-authentication-failure-time";



  /**
   * The name of the subcommand that will be used to set the authentication
   * failure times for the user.
   */
  private static final String SC_SET_AUTHENTICATION_FAILURE_TIMES =
       "set-authentication-failure-times";



  /**
   * The name of the subcommand that will be used to clear the authentication
   * failure times for the user.
   */
  private static final String SC_CLEAR_AUTHENTICATION_FAILURE_TIMES =
       "clear-authentication-failure-times";



  /**
   * The name of the subcommand that will be used to get the length of time
   * before the user's account is unlocked.
   */
  private static final String
       SC_GET_SECONDS_UNTIL_AUTHENTICATION_FAILURE_UNLOCK =
            "get-seconds-until-authentication-failure-unlock";



  /**
   * The name of the subcommand that will be used to get the number of remaining
   * authentication failures for the user.
   */
  private static final String SC_GET_REMAINING_AUTHENTICATION_FAILURE_COUNT =
       "get-remaining-authentication-failure-count";



  /**
   * The name of the subcommand that will be used to get the last login time for
   * the user.
   */
  private static final String SC_GET_LAST_LOGIN_TIME =
       "get-last-login-time";



  /**
   * The name of the subcommand that will be used to set the last login time for
   * the user.
   */
  private static final String SC_SET_LAST_LOGIN_TIME =
       "set-last-login-time";



  /**
   * The name of the subcommand that will be used to clear the last login time
   * for the user.
   */
  private static final String SC_CLEAR_LAST_LOGIN_TIME =
       "clear-last-login-time";



  /**
   * The name of the subcommand that will be used to get the length of time
   * before the account is idle locked.
   */
  private static final String SC_GET_SECONDS_UNTIL_IDLE_LOCKOUT =
       "get-seconds-until-idle-lockout";



  /**
   * The name of the subcommand that will be used to get the password reset
   * state for a user.
   */
  private static final String SC_GET_PASSWORD_RESET_STATE =
       "get-password-is-reset";



  /**
   * The name of the subcommand that will be used to set the password reset
   * state for a user.
   */
  private static final String SC_SET_PASSWORD_RESET_STATE =
       "set-password-is-reset";



  /**
   * The name of the subcommand that will be used to clear the password reset
   * state for a user.
   */
  private static final String SC_CLEAR_PASSWORD_RESET_STATE =
       "clear-password-is-reset";



  /**
   * The name of the subcommand that will be used to get the length of time
   * before the password reset lockout occurs.
   */
  private static final String SC_GET_SECONDS_UNTIL_PASSWORD_RESET_LOCKOUT =
       "get-seconds-until-password-reset-lockout";



  /**
   * The name of the subcommand that will be used to get the grace login use
   * times for the user.
   */
  private static final String SC_GET_GRACE_LOGIN_USE_TIMES =
       "get-grace-login-use-times";



  /**
   * The name of the subcommand that will be used to add a grace login use time
   * for the user.
   */
  private static final String SC_ADD_GRACE_LOGIN_USE_TIME =
       "add-grace-login-use-time";



  /**
   * The name of the subcommand that will be used to set the grace login use
   * times for the user.
   */
  private static final String SC_SET_GRACE_LOGIN_USE_TIMES =
       "set-grace-login-use-times";



  /**
   * The name of the subcommand that will be used to clear the grace login use
   * times for the user.
   */
  private static final String SC_CLEAR_GRACE_LOGIN_USE_TIMES =
       "clear-grace-login-use-times";



  /**
   * The name of the subcommand that will be used to get number of remaining
   * grace logins for the user.
   */
  private static final String SC_GET_REMAINING_GRACE_LOGIN_COUNT =
       "get-remaining-grace-login-count";



  /**
   * The name of the subcommand that will be used to get the password changed by
   * required time for the user.
   */
  private static final String SC_GET_PASSWORD_CHANGED_BY_REQUIRED_TIME =
       "get-password-changed-by-required-time";



  /**
   * The name of the subcommand that will be used to set the password changed by
   * required time for the user.
   */
  private static final String SC_SET_PASSWORD_CHANGED_BY_REQUIRED_TIME =
       "set-password-changed-by-required-time";



  /**
   * The name of the subcommand that will be used to clear the password changed
   * by required time for the user.
   */
  private static final String SC_CLEAR_PASSWORD_CHANGED_BY_REQUIRED_TIME =
       "clear-password-changed-by-required-time";



  /**
   * The name of the subcommand that will be used to get the length of time
   * before the user is required to change his/her password due to the required
   * change time.
   */
  private static final String SC_GET_SECONDS_UNTIL_REQUIRED_CHANGE_TIME =
       "get-seconds-until-required-change-time";



  /**
   * The name of the subcommand that will be used to get the password history
   * state values.
   */
  private static final String SC_GET_PASSWORD_HISTORY = "get-password-history";



  /**
   * The name of the subcommand that will be used to clear the password history
   * state values.
   */
  private static final String SC_CLEAR_PASSWORD_HISTORY =
       "clear-password-history";



  /**
   * The name of the argument that will be used for holding the value(s) to use
   * for the target operation.
   */
  private static final String ARG_OP_VALUE = "opvalue";



  /**
   * The value that will be used when encoding a password policy state operation
   * that should not have any values.
   */
  private static final String NO_VALUE = null;



  // The LDAP reader used to read responses from the server.
  private static LDAPReader ldapReader;

  // The LDAP writer used to send requests to the server.
  private static LDAPWriter ldapWriter;

  // The counter that will be used for LDAP message IDs.
  private static AtomicInteger nextMessageID;

  // The connection to the server.
  private static LDAPConnection connection;

  // The print stream to use when writing messages to standard error.
  private static PrintStream err;

  // The print stream to use when writing messages to standard output.
  private static PrintStream out;

  // The DN of the user to target with the operation.
  private static String targetDNString;

  // The argument parser for this tool.
  private static SubCommandArgumentParser argParser;



  /**
   * Parses the command-line arguments, connects to the server, and performs the
   * appropriate processing.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int returnCode = main(args, System.out, System.err);
    if (returnCode != 0)
    {
      System.exit(filterExitCode(returnCode));
    }
  }



  /**
   * Parses the command-line arguments, connects to the server, and performs the
   * appropriate processing.
   *
   * @param  args       The command-line arguments provided to this program.
   * @param  outStream  The output stream to use for standard output, or
   *                    {@code null} if standard output is not needed.
   * @param  errStream  The output stream to use for standard error, or
   *                    {@code null} if standard error is not needed.
   *
   * @return  A result code indicating whether the processing was successful.
   */
  public static int main(String[] args, OutputStream outStream,
                         OutputStream errStream)
  {
    if (outStream == null)
    {
      out = NullOutputStream.printStream();
    }
    else
    {
      out = new PrintStream(outStream);
    }

    if (errStream == null)
    {
      err = NullOutputStream.printStream();
    }
    else
    {
      err = new PrintStream(errStream);
    }




    // Parse the command-line arguments provided to the program.
    int result = parseArgsAndConnect(args);
    if (result < 0)
    {
      // This should only happen if we're only displaying usage information or
      // doing something else other than actually running the tool.
      return LDAPResultCode.SUCCESS;
    }
    else if (result != LDAPResultCode.SUCCESS)
    {
      return result;
    }


    try
    {
      // Use the subcommand provided to figure out how to encode the request.
      ArrayList<ASN1Element> opElements = new ArrayList<ASN1Element>(1);
      result = processSubcommand(opElements);
      if (result != LDAPResultCode.SUCCESS)
      {
        return result;
      }


      // Generate the extended request and send it to the server.
      ArrayList<ASN1Element> valueElements = new ArrayList<ASN1Element>(2);
      valueElements.add(new ASN1OctetString(targetDNString));
      if (! opElements.isEmpty())
      {
        valueElements.add(new ASN1Sequence(opElements));
      }
      ASN1OctetString requestValue =
           new ASN1OctetString(new ASN1Sequence(valueElements).encode());

      ExtendedRequestProtocolOp extendedRequest =
           new ExtendedRequestProtocolOp(OID_PASSWORD_POLICY_STATE_EXTOP,
                                         requestValue);

      LDAPMessage requestMessage =
           new LDAPMessage(nextMessageID.getAndIncrement(), extendedRequest);

      try
      {
        ldapWriter.writeMessage(requestMessage);
      }
      catch (Exception e)
      {
        Message message = ERR_PWPSTATE_CANNOT_SEND_REQUEST_EXTOP.get(
                getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return LDAPResultCode.CLIENT_SIDE_SERVER_DOWN;
      }


      // Read the response from the server.
      ArrayList<ASN1Element> responseOpElements;
      try
      {
        LDAPMessage responseMessage = ldapReader.readMessage();
        if (responseMessage == null)
        {
          Message message =
                  ERR_PWPSTATE_CONNECTION_CLOSED_READING_RESPONSE.get();
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return LDAPResultCode.CLIENT_SIDE_SERVER_DOWN;
        }

        ExtendedResponseProtocolOp extendedResponse =
             responseMessage.getExtendedResponseProtocolOp();

        int resultCode = extendedResponse.getResultCode();
        if (resultCode != LDAPResultCode.SUCCESS)
        {
          Message message =
               ERR_PWPSTATE_REQUEST_FAILED.get(resultCode,
                          LDAPResultCode.toString(resultCode),
                          String.valueOf(extendedResponse.getErrorMessage()));
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return resultCode;
        }

        ASN1Sequence valueSequence =
             ASN1Sequence.decodeAsSequence(extendedResponse.getValue().value());
        responseOpElements =
             valueSequence.elements().get(1).decodeAsSequence().elements();
      }
      catch (Exception e)
      {
        Message message = ERR_PWPSTATE_CANNOT_DECODE_RESPONSE_MESSAGE.get(
                getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return LDAPResultCode.CLIENT_SIDE_SERVER_DOWN;
      }


      // Get the response value and parse its individual elements.
      for (ASN1Element opElement : responseOpElements)
      {
        int opType;
        ArrayList<String> opValues;

        try
        {
          ASN1Sequence opSequence = opElement.decodeAsSequence();
          ArrayList<ASN1Element> elements = opSequence.elements();
          opType = elements.get(0).decodeAsEnumerated().intValue();
          opValues = new ArrayList<String>();
          if (elements.size() == 2)
          {
            for (ASN1Element e : elements.get(1).decodeAsSequence().elements())
            {
              opValues.add(e.decodeAsOctetString().stringValue());
            }
          }
        }
        catch (Exception e)
        {
          Message message = ERR_PWPSTATE_CANNOT_DECODE_RESPONSE_OP.get(
                  getExceptionMessage(e));
          err.println(wrapText(message, MAX_LINE_WIDTH));
          continue;
        }

        switch (opType)
        {
          case OP_GET_PASSWORD_POLICY_DN:
            Message message = INFO_PWPSTATE_LABEL_PASSWORD_POLICY_DN.get();
            printLabelAndValues(message, opValues);
            break;

          case OP_GET_ACCOUNT_DISABLED_STATE:
            message = INFO_PWPSTATE_LABEL_ACCOUNT_DISABLED_STATE.get();
            printLabelAndValues(message, opValues);
            break;

          case OP_GET_ACCOUNT_EXPIRATION_TIME:
            message = INFO_PWPSTATE_LABEL_ACCOUNT_EXPIRATION_TIME.get();
            printLabelAndValues(message, opValues);
            break;

          case OP_GET_SECONDS_UNTIL_ACCOUNT_EXPIRATION:
            message =
                    INFO_PWPSTATE_LABEL_SECONDS_UNTIL_ACCOUNT_EXPIRATION.get();
            printLabelAndValues(message, opValues);
            break;

          case OP_GET_PASSWORD_CHANGED_TIME:
            message = INFO_PWPSTATE_LABEL_PASSWORD_CHANGED_TIME.get();
            printLabelAndValues(message, opValues);
            break;

          case OP_GET_PASSWORD_EXPIRATION_WARNED_TIME:
            message = INFO_PWPSTATE_LABEL_PASSWORD_EXPIRATION_WARNED_TIME.get();
            printLabelAndValues(message, opValues);
            break;

          case OP_GET_SECONDS_UNTIL_PASSWORD_EXPIRATION:
            message =
                    INFO_PWPSTATE_LABEL_SECONDS_UNTIL_PASSWORD_EXPIRATION.get();
            printLabelAndValues(message, opValues);
            break;

          case OP_GET_SECONDS_UNTIL_PASSWORD_EXPIRATION_WARNING:
            message =
                  INFO_PWPSTATE_LABEL_SECONDS_UNTIL_PASSWORD_EXPIRATION_WARNING
                          .get();
            printLabelAndValues(message, opValues);
            break;

          case OP_GET_AUTHENTICATION_FAILURE_TIMES:
            message = INFO_PWPSTATE_LABEL_AUTH_FAILURE_TIMES.get();
            printLabelAndValues(message, opValues);
            break;

          case OP_GET_SECONDS_UNTIL_AUTHENTICATION_FAILURE_UNLOCK:
            message =
                    INFO_PWPSTATE_LABEL_SECONDS_UNTIL_AUTH_FAILURE_UNLOCK.get();
            printLabelAndValues(message, opValues);
            break;

          case OP_GET_REMAINING_AUTHENTICATION_FAILURE_COUNT:
            message = INFO_PWPSTATE_LABEL_REMAINING_AUTH_FAILURE_COUNT.get();
            printLabelAndValues(message, opValues);
            break;

          case OP_GET_LAST_LOGIN_TIME:
            message = INFO_PWPSTATE_LABEL_LAST_LOGIN_TIME.get();
            printLabelAndValues(message, opValues);
            break;

          case OP_GET_SECONDS_UNTIL_IDLE_LOCKOUT:
            message = INFO_PWPSTATE_LABEL_SECONDS_UNTIL_IDLE_LOCKOUT.get();
            printLabelAndValues(message, opValues);
            break;

          case OP_GET_PASSWORD_RESET_STATE:
            message = INFO_PWPSTATE_LABEL_PASSWORD_RESET_STATE.get();
            printLabelAndValues(message, opValues);
            break;

          case OP_GET_SECONDS_UNTIL_PASSWORD_RESET_LOCKOUT:
            message =
                    INFO_PWPSTATE_LABEL_SECONDS_UNTIL_PASSWORD_RESET_LOCKOUT
                            .get();
            printLabelAndValues(message, opValues);
            break;

          case OP_GET_GRACE_LOGIN_USE_TIMES:
            message = INFO_PWPSTATE_LABEL_GRACE_LOGIN_USE_TIMES.get();
            printLabelAndValues(message, opValues);
            break;

          case OP_GET_REMAINING_GRACE_LOGIN_COUNT:
            message = INFO_PWPSTATE_LABEL_REMAINING_GRACE_LOGIN_COUNT.get();
            printLabelAndValues(message, opValues);
            break;

          case OP_GET_PASSWORD_CHANGED_BY_REQUIRED_TIME:
            message =
                    INFO_PWPSTATE_LABEL_PASSWORD_CHANGED_BY_REQUIRED_TIME.get();
            printLabelAndValues(message, opValues);
            break;

          case OP_GET_SECONDS_UNTIL_REQUIRED_CHANGE_TIME:
            message =
                    INFO_PWPSTATE_LABEL_SECONDS_UNTIL_REQUIRED_CHANGE_TIME
                            .get();
            printLabelAndValues(message, opValues);
            break;

          case OP_GET_PASSWORD_HISTORY:
            message = INFO_PWPSTATE_LABEL_PASSWORD_HISTORY.get();
            printLabelAndValues(message, opValues);
            break;

          default:
            message = ERR_PWPSTATE_INVALID_RESPONSE_OP_TYPE.get(
                    String.valueOf(opType));
            err.println(wrapText(message, MAX_LINE_WIDTH));
            break;
        }
      }


      // If we've gotten here, then everything completed successfully.
      return 0;
    }
    finally
    {
      // Close the connection to the server if it's active.
      if (connection != null)
      {
        connection.close(nextMessageID);
      }
    }
  }



  /**
   * Initializes the argument parser for this tool, parses the provided
   * arguments, and establishes a connection to the server.
   *
   * @return  A result code that indicates the result of the processing.  A
   *          value of zero indicates that all processing completed
   *          successfully.  A value of -1 indicates that only the usage
   *          information was displayed and no further action is required.
   */
  private static int parseArgsAndConnect(String[] args)
  {
    argParser = new SubCommandArgumentParser(
            CLASS_NAME, INFO_PWPSTATE_TOOL_DESCRIPTION.get(),
            false);

    BooleanArgument   showUsage;
    BooleanArgument   trustAll;
    BooleanArgument   useSSL;
    BooleanArgument   useStartTLS;
    FileBasedArgument bindPWFile;
    FileBasedArgument keyStorePWFile;
    FileBasedArgument trustStorePWFile;
    IntegerArgument   port;
    StringArgument    bindDN;
    StringArgument    bindPW;
    StringArgument    certNickname;
    StringArgument    host;
    StringArgument    keyStoreFile;
    StringArgument    keyStorePW;
    StringArgument    saslOption;
    StringArgument    targetDN;
    StringArgument    trustStoreFile;
    StringArgument    trustStorePW;

    try
    {
      host = new StringArgument("host", OPTION_SHORT_HOST,
                                OPTION_LONG_HOST, false, false, true,
                                INFO_HOST_PLACEHOLDER.get(), "127.0.0.1", null,
                                INFO_PWPSTATE_DESCRIPTION_HOST.get());
      argParser.addGlobalArgument(host);

      port = new IntegerArgument(
              "port", OPTION_SHORT_PORT,
              OPTION_LONG_PORT, false, false, true,
              INFO_PORT_PLACEHOLDER.get(), 389, null, true, 1,
              true, 65535, INFO_PWPSTATE_DESCRIPTION_PORT.get());
      argParser.addGlobalArgument(port);

      useSSL = new BooleanArgument("usessl", OPTION_SHORT_USE_SSL,
                                   OPTION_LONG_USE_SSL,
                                   INFO_PWPSTATE_DESCRIPTION_USESSL.get());
      argParser.addGlobalArgument(useSSL);

      useStartTLS = new BooleanArgument(
              "usestarttls", OPTION_SHORT_START_TLS,
              OPTION_LONG_START_TLS,
              INFO_PWPSTATE_DESCRIPTION_USESTARTTLS.get());
      argParser.addGlobalArgument(useStartTLS);

      bindDN = new StringArgument("binddn", OPTION_SHORT_BINDDN,
                                  OPTION_LONG_BINDDN, false, false, true,
                                  INFO_BINDDN_PLACEHOLDER.get(), null, null,
                                  INFO_PWPSTATE_DESCRIPTION_BINDDN.get());
      argParser.addGlobalArgument(bindDN);

      bindPW = new StringArgument("bindpw", OPTION_SHORT_BINDPWD,
                                  OPTION_LONG_BINDPWD, false, false,
                                  true,
                                  INFO_BINDPWD_PLACEHOLDER.get(), null, null,
                                  INFO_PWPSTATE_DESCRIPTION_BINDPW.get());
      argParser.addGlobalArgument(bindPW);

      bindPWFile = new FileBasedArgument(
              "bindpwfile",
              OPTION_SHORT_BINDPWD_FILE,
              OPTION_LONG_BINDPWD_FILE,
              false, false,
              INFO_BINDPWD_FILE_PLACEHOLDER.get(),
              null, null,
              INFO_PWPSTATE_DESCRIPTION_BINDPWFILE.get());
      argParser.addGlobalArgument(bindPWFile);

      targetDN = new StringArgument("targetdn", 'b', "targetDN", true, false,
                                    true, INFO_TARGETDN_PLACEHOLDER.get(), null,
                                    null,
                                    INFO_PWPSTATE_DESCRIPTION_TARGETDN.get());
      argParser.addGlobalArgument(targetDN);

      saslOption = new StringArgument(
              "sasloption", OPTION_SHORT_SASLOPTION,
              OPTION_LONG_SASLOPTION, false,
              true, true,
              INFO_SASL_OPTION_PLACEHOLDER.get(), null, null,
              INFO_PWPSTATE_DESCRIPTION_SASLOPTIONS.get());
      argParser.addGlobalArgument(saslOption);

      trustAll = new BooleanArgument("trustall", 'X', "trustAll",
                                     INFO_PWPSTATE_DESCRIPTION_TRUST_ALL.get());
      argParser.addGlobalArgument(trustAll);

      keyStoreFile = new StringArgument("keystorefile",
                                        OPTION_SHORT_KEYSTOREPATH,
                                        OPTION_LONG_KEYSTOREPATH,
                                        false, false, true,
                                        INFO_KEYSTOREPATH_PLACEHOLDER.get(),
                                        null, null,
                                        INFO_PWPSTATE_DESCRIPTION_KSFILE.get());
      argParser.addGlobalArgument(keyStoreFile);

      keyStorePW = new StringArgument("keystorepw", OPTION_SHORT_KEYSTORE_PWD,
                                      OPTION_LONG_KEYSTORE_PWD,
                                      false, false, true,
                                      INFO_KEYSTORE_PWD_PLACEHOLDER.get(),
                                      null, null,
                                      INFO_PWPSTATE_DESCRIPTION_KSPW.get());
      argParser.addGlobalArgument(keyStorePW);

      keyStorePWFile = new FileBasedArgument("keystorepwfile",
                                OPTION_SHORT_KEYSTORE_PWD_FILE,
                                OPTION_LONG_KEYSTORE_PWD_FILE, false, false,
                                INFO_KEYSTORE_PWD_FILE_PLACEHOLDER.get(), null,
                                null,
                                INFO_PWPSTATE_DESCRIPTION_KSPWFILE.get());
      argParser.addGlobalArgument(keyStorePWFile);

      certNickname = new StringArgument(
              "certnickname", 'N', "certNickname",
              false, false, true, INFO_NICKNAME_PLACEHOLDER.get(), null,
              null, INFO_DESCRIPTION_CERT_NICKNAME.get());
      argParser.addGlobalArgument(certNickname);

      trustStoreFile = new StringArgument(
              "truststorefile",
              OPTION_SHORT_TRUSTSTOREPATH,
              OPTION_LONG_TRUSTSTOREPATH,
              false, false, true,
              INFO_TRUSTSTOREPATH_PLACEHOLDER.get(),
              null, null,
              INFO_PWPSTATE_DESCRIPTION_TSFILE.get());
      argParser.addGlobalArgument(trustStoreFile);

      trustStorePW = new StringArgument(
              "truststorepw", 'T',
              OPTION_LONG_TRUSTSTORE_PWD,
              false, false,
              true, INFO_TRUSTSTORE_PWD_PLACEHOLDER.get(), null,
              null, INFO_PWPSTATE_DESCRIPTION_TSPW.get());
      argParser.addGlobalArgument(trustStorePW);

      trustStorePWFile = new FileBasedArgument("truststorepwfile",
                                  OPTION_SHORT_TRUSTSTORE_PWD_FILE,
                                  OPTION_LONG_TRUSTSTORE_PWD_FILE,
                                  false, false,
                                  INFO_TRUSTSTORE_PWD_FILE_PLACEHOLDER.get(),
                                  null, null,
                                  INFO_PWPSTATE_DESCRIPTION_TSPWFILE.get());
      argParser.addGlobalArgument(trustStorePWFile);

      showUsage = new BooleanArgument(
              "showusage", OPTION_SHORT_HELP,
              OPTION_LONG_HELP,
              INFO_PWPSTATE_DESCRIPTION_SHOWUSAGE.get());
      argParser.addGlobalArgument(showUsage);
      argParser.setUsageArgument(showUsage, out);


      HashSet<String> booleanValues = new HashSet<String>(2);
      booleanValues.add(INFO_MULTICHOICE_TRUE_VALUE.get().toString());
      booleanValues.add(INFO_MULTICHOICE_FALSE_VALUE.get().toString());


      Message msg = INFO_DESCRIPTION_PWPSTATE_GET_ALL.get();
      new SubCommand(argParser, SC_GET_ALL, msg);

      msg = INFO_DESCRIPTION_PWPSTATE_GET_PASSWORD_POLICY_DN.get();
      new SubCommand(argParser, SC_GET_PASSWORD_POLICY_DN, msg);

      msg = INFO_DESCRIPTION_PWPSTATE_GET_ACCOUNT_DISABLED_STATE.get();
      new SubCommand(argParser, SC_GET_ACCOUNT_DISABLED_STATE, msg);

      msg = INFO_DESCRIPTION_PWPSTATE_SET_ACCOUNT_DISABLED_STATE.get();
      SubCommand sc = new SubCommand(argParser, SC_SET_ACCOUNT_DISABLED_STATE,
                                     msg);
      sc.addArgument(new MultiChoiceArgument(ARG_OP_VALUE, 'O',
                              "operationValue", true, false, true,
                              INFO_TRUE_FALSE_PLACEHOLDER.get(), null, null,
                              booleanValues, false,
                              INFO_DESCRIPTION_OPERATION_BOOLEAN_VALUE.get()));

      msg = INFO_DESCRIPTION_PWPSTATE_CLEAR_ACCOUNT_DISABLED_STATE.get();
      new SubCommand(argParser, SC_CLEAR_ACCOUNT_DISABLED_STATE, msg);

      msg = INFO_DESCRIPTION_PWPSTATE_GET_ACCOUNT_EXPIRATION_TIME.get();
      new SubCommand(argParser, SC_GET_ACCOUNT_EXPIRATION_TIME, msg);

      msg = INFO_DESCRIPTION_PWPSTATE_SET_ACCOUNT_EXPIRATION_TIME.get();
      sc = new SubCommand(argParser, SC_SET_ACCOUNT_EXPIRATION_TIME, msg);
      sc.addArgument(new StringArgument(ARG_OP_VALUE, 'O', "operationValue",
                              false, false, true, INFO_TIME_PLACEHOLDER.get(),
                              null, null,
                              INFO_DESCRIPTION_OPERATION_TIME_VALUE.get()));
      sc.setHidden(true);

      msg = INFO_DESCRIPTION_PWPSTATE_CLEAR_ACCOUNT_EXPIRATION_TIME.get();
      sc = new SubCommand(argParser, SC_CLEAR_ACCOUNT_EXPIRATION_TIME, msg);
      sc.setHidden(true);

      msg =
              INFO_DESCRIPTION_PWPSTATE_GET_SECONDS_UNTIL_ACCOUNT_EXPIRATION
                      .get();
      new SubCommand(argParser,
              SC_GET_SECONDS_UNTIL_ACCOUNT_EXPIRATION,
              msg);

      msg = INFO_DESCRIPTION_PWPSTATE_GET_PASSWORD_CHANGED_TIME.get();
      new SubCommand(argParser, SC_GET_PASSWORD_CHANGED_TIME, msg);

      msg = INFO_DESCRIPTION_PWPSTATE_SET_PASSWORD_CHANGED_TIME.get();
      sc = new SubCommand(argParser, SC_SET_PASSWORD_CHANGED_TIME, msg);
      sc.addArgument(new StringArgument(ARG_OP_VALUE, 'O', "operationValue",
                              false, false, true, INFO_TIME_PLACEHOLDER.get(),
                              null, null,
                              INFO_DESCRIPTION_OPERATION_TIME_VALUE.get()));
      sc.setHidden(true);

      msg = INFO_DESCRIPTION_PWPSTATE_CLEAR_PASSWORD_CHANGED_TIME.get();
      sc = new SubCommand(argParser, SC_CLEAR_PASSWORD_CHANGED_TIME, msg);
      sc.setHidden(true);

      msg = INFO_DESCRIPTION_PWPSTATE_GET_PASSWORD_EXPIRATION_WARNED_TIME
              .get();
      new SubCommand(argParser, SC_GET_PASSWORD_EXP_WARNED_TIME, msg);

      msg = INFO_DESCRIPTION_PWPSTATE_SET_PASSWORD_EXPIRATION_WARNED_TIME
              .get();
      sc = new SubCommand(argParser, SC_SET_PASSWORD_EXP_WARNED_TIME, msg);
      sc.addArgument(new StringArgument(ARG_OP_VALUE, 'O', "operationValue",
                              false, false, true, INFO_TIME_PLACEHOLDER.get(),
                              null, null,
                              INFO_DESCRIPTION_OPERATION_TIME_VALUE.get()));
      sc.setHidden(true);

      msg = INFO_DESCRIPTION_PWPSTATE_CLEAR_PASSWORD_EXPIRATION_WARNED_TIME
              .get();
      sc = new SubCommand(argParser, SC_CLEAR_PASSWORD_EXP_WARNED_TIME, msg);
      sc.setHidden(true);

      msg = INFO_DESCRIPTION_PWPSTATE_GET_SECONDS_UNTIL_PASSWORD_EXP.get();
      new SubCommand(argParser, SC_GET_SECONDS_UNTIL_PASSWORD_EXPIRATION,
                     msg);

      msg = INFO_DESCRIPTION_PWPSTATE_GET_SECONDS_UNTIL_PASSWORD_EXP_WARNING
              .get();
      new SubCommand(argParser,
                     SC_GET_SECONDS_UNTIL_PASSWORD_EXPIRATION_WARNING, msg);

      msg = INFO_DESCRIPTION_PWPSTATE_GET_AUTH_FAILURE_TIMES.get();
      new SubCommand(argParser, SC_GET_AUTHENTICATION_FAILURE_TIMES, msg);

      msg = INFO_DESCRIPTION_PWPSTATE_ADD_AUTH_FAILURE_TIME.get();
      sc = new SubCommand(argParser, SC_ADD_AUTHENTICATION_FAILURE_TIME,
              msg);
      sc.addArgument(new StringArgument(ARG_OP_VALUE, 'O', "operationValue",
                              false, true, true, INFO_TIME_PLACEHOLDER.get(),
                              null, null,
                              INFO_DESCRIPTION_OPERATION_TIME_VALUE.get()));
      sc.setHidden(true);

      msg = INFO_DESCRIPTION_PWPSTATE_SET_AUTH_FAILURE_TIMES.get();
      sc = new SubCommand(argParser, SC_SET_AUTHENTICATION_FAILURE_TIMES,
                          msg);
      sc.addArgument(new StringArgument(ARG_OP_VALUE, 'O', "operationValue",
                              false, true, true, INFO_TIME_PLACEHOLDER.get(),
                              null, null,
                              INFO_DESCRIPTION_OPERATION_TIME_VALUES.get()));
      sc.setHidden(true);

      msg = INFO_DESCRIPTION_PWPSTATE_CLEAR_AUTH_FAILURE_TIMES.get();
      sc = new SubCommand(argParser, SC_CLEAR_AUTHENTICATION_FAILURE_TIMES,
                          msg);
      sc.setHidden(true);

      msg = INFO_DESCRIPTION_PWPSTATE_GET_SECONDS_UNTIL_AUTH_FAILURE_UNLOCK
              .get();
      new SubCommand(argParser,
                     SC_GET_SECONDS_UNTIL_AUTHENTICATION_FAILURE_UNLOCK,
                     msg);

      msg =
              INFO_DESCRIPTION_PWPSTATE_GET_REMAINING_AUTH_FAILURE_COUNT.get();
      new SubCommand(argParser, SC_GET_REMAINING_AUTHENTICATION_FAILURE_COUNT,
                     msg);

      msg = INFO_DESCRIPTION_PWPSTATE_GET_LAST_LOGIN_TIME.get();
      new SubCommand(argParser, SC_GET_LAST_LOGIN_TIME, msg);

      msg = INFO_DESCRIPTION_PWPSTATE_SET_LAST_LOGIN_TIME.get();
      sc = new SubCommand(argParser, SC_SET_LAST_LOGIN_TIME, msg);
      sc.addArgument(new StringArgument(ARG_OP_VALUE, 'O', "operationValue",
                              false, false, true, INFO_TIME_PLACEHOLDER.get(),
                              null, null,
                              INFO_DESCRIPTION_OPERATION_TIME_VALUE.get()));
      sc.setHidden(true);

      msg = INFO_DESCRIPTION_PWPSTATE_CLEAR_LAST_LOGIN_TIME.get();
      sc = new SubCommand(argParser, SC_CLEAR_LAST_LOGIN_TIME, msg);
      sc.setHidden(true);

      msg = INFO_DESCRIPTION_PWPSTATE_GET_SECONDS_UNTIL_IDLE_LOCKOUT.get();
      new SubCommand(argParser, SC_GET_SECONDS_UNTIL_IDLE_LOCKOUT, msg);

      msg = INFO_DESCRIPTION_PWPSTATE_GET_PASSWORD_RESET_STATE.get();
      new SubCommand(argParser, SC_GET_PASSWORD_RESET_STATE, msg);

      msg = INFO_DESCRIPTION_PWPSTATE_SET_PASSWORD_RESET_STATE.get();
      sc = new SubCommand(argParser, SC_SET_PASSWORD_RESET_STATE, msg);
      sc.addArgument(new MultiChoiceArgument(ARG_OP_VALUE, 'O',
                              "operationValue", true, false, true,
                              INFO_TRUE_FALSE_PLACEHOLDER.get(), null, null,
                              booleanValues, false,
                              INFO_DESCRIPTION_OPERATION_BOOLEAN_VALUE.get()));
      sc.setHidden(true);

      msg = INFO_DESCRIPTION_PWPSTATE_CLEAR_PASSWORD_RESET_STATE.get();
      sc = new SubCommand(argParser, SC_CLEAR_PASSWORD_RESET_STATE, msg);
      sc.setHidden(true);

      msg = INFO_DESCRIPTION_PWPSTATE_GET_SECONDS_UNTIL_RESET_LOCKOUT.get();
      new SubCommand(argParser, SC_GET_SECONDS_UNTIL_PASSWORD_RESET_LOCKOUT,
                     msg);

      msg = INFO_DESCRIPTION_PWPSTATE_GET_GRACE_LOGIN_USE_TIMES.get();
      new SubCommand(argParser, SC_GET_GRACE_LOGIN_USE_TIMES, msg);

      msg = INFO_DESCRIPTION_PWPSTATE_ADD_GRACE_LOGIN_USE_TIME.get();
      sc = new SubCommand(argParser, SC_ADD_GRACE_LOGIN_USE_TIME, msg);
      sc.addArgument(new StringArgument(ARG_OP_VALUE, 'O', "operationValue",
                              false, true, true, INFO_TIME_PLACEHOLDER.get(),
                              null, null,
                              INFO_DESCRIPTION_OPERATION_TIME_VALUE.get()));
      sc.setHidden(true);

      msg = INFO_DESCRIPTION_PWPSTATE_SET_GRACE_LOGIN_USE_TIMES.get();
      sc = new SubCommand(argParser, SC_SET_GRACE_LOGIN_USE_TIMES, msg);
      sc.addArgument(new StringArgument(ARG_OP_VALUE, 'O', "operationValue",
                              false, true, true, INFO_TIME_PLACEHOLDER.get(),
                              null, null,
                              INFO_DESCRIPTION_OPERATION_TIME_VALUES.get()));
      sc.setHidden(true);

      msg = INFO_DESCRIPTION_PWPSTATE_CLEAR_GRACE_LOGIN_USE_TIMES.get();
      sc = new SubCommand(argParser, SC_CLEAR_GRACE_LOGIN_USE_TIMES, msg);
      sc.setHidden(true);

      msg = INFO_DESCRIPTION_PWPSTATE_GET_REMAINING_GRACE_LOGIN_COUNT.get();
      new SubCommand(argParser, SC_GET_REMAINING_GRACE_LOGIN_COUNT,
                     msg);

      msg = INFO_DESCRIPTION_PWPSTATE_GET_PW_CHANGED_BY_REQUIRED_TIME.get();
      new SubCommand(argParser, SC_GET_PASSWORD_CHANGED_BY_REQUIRED_TIME,
                     msg);

      msg = INFO_DESCRIPTION_PWPSTATE_SET_PW_CHANGED_BY_REQUIRED_TIME.get();
      sc = new SubCommand(argParser, SC_SET_PASSWORD_CHANGED_BY_REQUIRED_TIME,
                          msg);
      sc.addArgument(new StringArgument(ARG_OP_VALUE, 'O', "operationValue",
                              false, false, true, INFO_TIME_PLACEHOLDER.get(),
                              null, null,
                              INFO_DESCRIPTION_OPERATION_TIME_VALUE.get()));
      sc.setHidden(true);

      msg =
              INFO_DESCRIPTION_PWPSTATE_CLEAR_PW_CHANGED_BY_REQUIRED_TIME.get();
      sc = new SubCommand(argParser, SC_CLEAR_PASSWORD_CHANGED_BY_REQUIRED_TIME,
                          msg);
      sc.setHidden(true);

      msg =
              INFO_DESCRIPTION_PWPSTATE_GET_SECONDS_UNTIL_REQUIRED_CHANGE_TIME
                      .get();
      new SubCommand(argParser, SC_GET_SECONDS_UNTIL_REQUIRED_CHANGE_TIME,
                     msg);

      msg = INFO_DESCRIPTION_PWPSTATE_GET_PASSWORD_HISTORY.get();
      new SubCommand(argParser, SC_GET_PASSWORD_HISTORY, msg);

      msg = INFO_DESCRIPTION_PWPSTATE_CLEAR_PASSWORD_HISTORY.get();
      sc = new SubCommand(argParser, SC_CLEAR_PASSWORD_HISTORY, msg);
      sc.setHidden(true);
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR;
    }

    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
    }


    // If we should just display usage or version information,
    // then exit because it will have already been done.
    if (argParser.usageOrVersionDisplayed())
    {
      return -1;
    }


    // Get the target DN as a string for later use.
    targetDNString = targetDN.getValue();


    // Create the LDAP connection options object, which will be used to
    // customize the way that we connect to the server and specify a set of
    // basic defaults.
    LDAPConnectionOptions connectionOptions = new LDAPConnectionOptions();
    connectionOptions.setVersionNumber(3);


    //  If both a bind password and bind password file were provided, then
    // return an error.
    if (bindPW.isPresent() && bindPWFile.isPresent())
    {
      Message message = ERR_PWPSTATE_MUTUALLY_EXCLUSIVE_ARGUMENTS.get(
              bindPW.getLongIdentifier(),
              bindPWFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
    }

    // If both a key store password and key store password file were provided,
    // then return an error.
    if (keyStorePW.isPresent() && keyStorePWFile.isPresent())
    {
      Message message = ERR_PWPSTATE_MUTUALLY_EXCLUSIVE_ARGUMENTS.get(
              keyStorePW.getLongIdentifier(),
              keyStorePWFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
    }


    // If both a trust store password and trust store password file were
    // provided, then return an error.
    if (trustStorePW.isPresent() && trustStorePWFile.isPresent())
    {
      Message message = ERR_PWPSTATE_MUTUALLY_EXCLUSIVE_ARGUMENTS.get(
              trustStorePW.getLongIdentifier(),
              trustStorePWFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
    }

    // See if we should use SSL or StartTLS when establishing the connection.
    // If so, then make sure only one of them was specified.
    if (useSSL.isPresent())
    {
      if (useStartTLS.isPresent())
      {
        Message message = ERR_PWPSTATE_MUTUALLY_EXCLUSIVE_ARGUMENTS.get(
                useSSL.getLongIdentifier(),
                                    useStartTLS.getLongIdentifier());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
      }
      else
      {
        connectionOptions.setUseSSL(true);
      }
    }
    else if (useStartTLS.isPresent())
    {
      connectionOptions.setStartTLS(true);
    }


    // If we should blindly trust any certificate, then install the appropriate
    // SSL connection factory.
    if (useSSL.isPresent() || useStartTLS.isPresent())
    {
      try
      {
        String clientAlias;
        if (certNickname.isPresent())
        {
          clientAlias = certNickname.getValue();
        }
        else
        {
          clientAlias = null;
        }

        SSLConnectionFactory sslConnectionFactory = new SSLConnectionFactory();
        sslConnectionFactory.init(trustAll.isPresent(), keyStoreFile.getValue(),
                                  keyStorePW.getValue(), clientAlias,
                                  trustStoreFile.getValue(),
                                  trustStorePW.getValue());

        connectionOptions.setSSLConnectionFactory(sslConnectionFactory);
      }
      catch (SSLConnectionException sce)
      {
        Message message = ERR_PWPSTATE_CANNOT_INITIALIZE_SSL.get(
                sce.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR;
      }
    }


    // If one or more SASL options were provided, then make sure that one of
    // them was "mech" and specified a valid SASL mechanism.
    if (saslOption.isPresent())
    {
      String             mechanism = null;
      LinkedList<String> options   = new LinkedList<String>();

      for (String s : saslOption.getValues())
      {
        int equalPos = s.indexOf('=');
        if (equalPos <= 0)
        {
          Message message = ERR_PWPSTATE_CANNOT_PARSE_SASL_OPTION.get(s);
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
        }
        else
        {
          String name  = s.substring(0, equalPos);

          if (name.equalsIgnoreCase("mech"))
          {
            mechanism = s;
          }
          else
          {
            options.add(s);
          }
        }
      }

      if (mechanism == null)
      {
        Message message = ERR_PWPSTATE_NO_SASL_MECHANISM.get();
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
      }

      connectionOptions.setSASLMechanism(mechanism);

      for (String option : options)
      {
        connectionOptions.addSASLProperty(option);
      }
    }


    // Attempt to connect and authenticate to the Directory Server.
    nextMessageID = new AtomicInteger(1);
    try
    {
      connection = new LDAPConnection(host.getValue(), port.getIntValue(),
                                      connectionOptions, out, err);
      connection.connectToHost(bindDN.getValue(),
          LDAPConnectionArgumentParser.getPasswordValue(bindPW, bindPWFile),
                               nextMessageID);
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_PWPSTATE_CANNOT_DETERMINE_PORT.get(
              port.getLongIdentifier(),
              ae.getMessage());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
    }
    catch (LDAPConnectionException lce)
    {
      Message message = ERR_PWPSTATE_CANNOT_CONNECT.get(lce.getMessage());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR;
    }

    ldapReader = connection.getLDAPReader();
    ldapWriter = connection.getLDAPWriter();

    return LDAPResultCode.SUCCESS;
  }



  /**
   * Processes the subcommand from the provided argument parser and appends the
   * appropriate operation elements to the given list.
   *
   * @param  opElements  A list into which the operation elements shouold be
   *                     placed.
   *
   * @return  A result code indicating the results of the processing.
   */
  private static int processSubcommand(ArrayList<ASN1Element> opElements)
  {
    int msgID;
    SubCommand subCommand = argParser.getSubCommand();
    if (subCommand == null)
    {
      Message message = ERR_PWPSTATE_NO_SUBCOMMAND.get();
      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
    }

    String subCommandName = subCommand.getName();
    if (subCommandName.equals(SC_GET_ALL))
    {
      // The list should stay empty for this one.
    }
    else if (subCommandName.equals(SC_GET_PASSWORD_POLICY_DN))
    {
      opElements.add(encode(OP_GET_PASSWORD_POLICY_DN, NO_VALUE));
    }
    else if (subCommandName.equals(SC_GET_ACCOUNT_DISABLED_STATE))
    {
      opElements.add(encode(OP_GET_ACCOUNT_DISABLED_STATE, NO_VALUE));
    }
    else if (subCommandName.equals(SC_SET_ACCOUNT_DISABLED_STATE))
    {
      Argument a = subCommand.getArgumentForName(ARG_OP_VALUE);
      if ((a != null) && a.isPresent())
      {
        String valueStr = a.getValue();
        if (valueStr.equalsIgnoreCase("true"))
        {
          opElements.add(encode(OP_SET_ACCOUNT_DISABLED_STATE, "true"));
        }
        else if (valueStr.equalsIgnoreCase("false"))
        {
          opElements.add(encode(OP_SET_ACCOUNT_DISABLED_STATE, "false"));
        }
        else
        {
          Message message = ERR_PWPSTATE_INVALID_BOOLEAN_VALUE.get(valueStr);
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
        }
      }
      else
      {
        Message message = ERR_PWPSTATE_NO_BOOLEAN_VALUE.get();
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
      }
    }
    else if (subCommandName.equals(SC_CLEAR_ACCOUNT_DISABLED_STATE))
    {
      opElements.add(encode(OP_CLEAR_ACCOUNT_DISABLED_STATE, NO_VALUE));
    }
    else if (subCommandName.equals(SC_GET_ACCOUNT_EXPIRATION_TIME))
    {
      opElements.add(encode(OP_GET_ACCOUNT_EXPIRATION_TIME, NO_VALUE));
    }
    else if (subCommandName.equals(SC_SET_ACCOUNT_EXPIRATION_TIME))
    {
      Argument a = subCommand.getArgumentForName(ARG_OP_VALUE);
      if ((a != null) && a.isPresent())
      {
        opElements.add(encode(OP_SET_ACCOUNT_EXPIRATION_TIME, a.getValue()));
      }
      else
      {
        opElements.add(encode(OP_SET_ACCOUNT_EXPIRATION_TIME, NO_VALUE));
      }
    }
    else if (subCommandName.equals(SC_CLEAR_ACCOUNT_EXPIRATION_TIME))
    {
      opElements.add(encode(OP_CLEAR_ACCOUNT_EXPIRATION_TIME, NO_VALUE));
    }
    else if (subCommandName.equals(SC_GET_SECONDS_UNTIL_ACCOUNT_EXPIRATION))
    {
      opElements.add(encode(OP_GET_SECONDS_UNTIL_ACCOUNT_EXPIRATION, NO_VALUE));
    }
    else if (subCommandName.equals(SC_GET_PASSWORD_CHANGED_TIME))
    {
      opElements.add(encode(OP_GET_PASSWORD_CHANGED_TIME, NO_VALUE));
    }
    else if (subCommandName.equals(SC_SET_PASSWORD_CHANGED_TIME))
    {
      Argument a = subCommand.getArgumentForName(ARG_OP_VALUE);
      if ((a != null) && a.isPresent())
      {
        opElements.add(encode(OP_SET_PASSWORD_CHANGED_TIME, a.getValue()));
      }
      else
      {
        opElements.add(encode(OP_SET_PASSWORD_CHANGED_TIME, NO_VALUE));
      }
    }
    else if (subCommandName.equals(SC_CLEAR_PASSWORD_CHANGED_TIME))
    {
      opElements.add(encode(OP_CLEAR_PASSWORD_CHANGED_TIME, NO_VALUE));
    }
    else if(subCommandName.equals(SC_GET_PASSWORD_EXP_WARNED_TIME))
    {
      opElements.add(encode(OP_GET_PASSWORD_EXPIRATION_WARNED_TIME, NO_VALUE));
    }
    else if(subCommandName.equals(SC_SET_PASSWORD_EXP_WARNED_TIME))
    {
      Argument a = subCommand.getArgumentForName(ARG_OP_VALUE);
      if ((a != null) && a.isPresent())
      {
        opElements.add(encode(OP_SET_PASSWORD_EXPIRATION_WARNED_TIME,
                              a.getValue()));
      }
      else
      {
        opElements.add(encode(OP_SET_PASSWORD_EXPIRATION_WARNED_TIME,
                              NO_VALUE));
      }
    }
    else if(subCommandName.equals(SC_CLEAR_PASSWORD_EXP_WARNED_TIME))
    {
      opElements.add(encode(OP_CLEAR_PASSWORD_EXPIRATION_WARNED_TIME,
                            NO_VALUE));
    }
    else if(subCommandName.equals(SC_GET_SECONDS_UNTIL_PASSWORD_EXPIRATION))
    {
      opElements.add(encode(OP_GET_SECONDS_UNTIL_PASSWORD_EXPIRATION,
                            NO_VALUE));
    }
    else if(subCommandName.equals(
                 SC_GET_SECONDS_UNTIL_PASSWORD_EXPIRATION_WARNING))
    {
      opElements.add(encode(OP_GET_SECONDS_UNTIL_PASSWORD_EXPIRATION_WARNING,
                            NO_VALUE));
    }
    else if(subCommandName.equals(SC_GET_AUTHENTICATION_FAILURE_TIMES))
    {
      opElements.add(encode(OP_GET_AUTHENTICATION_FAILURE_TIMES, NO_VALUE));
    }
    else if(subCommandName.equals(SC_ADD_AUTHENTICATION_FAILURE_TIME))
    {
      Argument a = subCommand.getArgumentForName(ARG_OP_VALUE);
      if ((a != null) && a.isPresent())
      {
        opElements.add(encode(OP_ADD_AUTHENTICATION_FAILURE_TIME,
                              a.getValue()));
      }
      else
      {
        opElements.add(encode(OP_ADD_AUTHENTICATION_FAILURE_TIME, NO_VALUE));
      }
    }
    else if(subCommandName.equals(SC_SET_AUTHENTICATION_FAILURE_TIMES))
    {
      Argument a = subCommand.getArgumentForName(ARG_OP_VALUE);
      if ((a != null) && a.isPresent())
      {
        ArrayList<String> valueList = new ArrayList<String>(a.getValues());
        String[] values = new String[valueList.size()];
        valueList.toArray(values);

        opElements.add(encode(OP_SET_AUTHENTICATION_FAILURE_TIMES, values));
      }
      else
      {
        opElements.add(encode(OP_SET_AUTHENTICATION_FAILURE_TIMES, NO_VALUE));
      }
    }
    else if(subCommandName.equals(SC_CLEAR_AUTHENTICATION_FAILURE_TIMES))
    {
      opElements.add(encode(OP_CLEAR_AUTHENTICATION_FAILURE_TIMES, NO_VALUE));
    }
    else if(subCommandName.equals(
                 SC_GET_SECONDS_UNTIL_AUTHENTICATION_FAILURE_UNLOCK))
    {
      opElements.add(encode(OP_GET_SECONDS_UNTIL_AUTHENTICATION_FAILURE_UNLOCK,
                            NO_VALUE));
    }
    else if(subCommandName.equals(
                 SC_GET_REMAINING_AUTHENTICATION_FAILURE_COUNT))
    {
      opElements.add(encode(OP_GET_REMAINING_AUTHENTICATION_FAILURE_COUNT,
                            NO_VALUE));
    }
    else if(subCommandName.equals(SC_GET_LAST_LOGIN_TIME))
    {
      opElements.add(encode(OP_GET_LAST_LOGIN_TIME, NO_VALUE));
    }
    else if(subCommandName.equals(SC_SET_LAST_LOGIN_TIME))
    {
      Argument a = subCommand.getArgumentForName(ARG_OP_VALUE);
      if ((a != null) && a.isPresent())
      {
        opElements.add(encode(OP_SET_LAST_LOGIN_TIME, a.getValue()));
      }
      else
      {
        opElements.add(encode(OP_SET_LAST_LOGIN_TIME, NO_VALUE));
      }
    }
    else if(subCommandName.equals(SC_CLEAR_LAST_LOGIN_TIME))
    {
      opElements.add(encode(OP_CLEAR_LAST_LOGIN_TIME, NO_VALUE));
    }
    else if(subCommandName.equals(SC_GET_SECONDS_UNTIL_IDLE_LOCKOUT))
    {
      opElements.add(encode(OP_GET_SECONDS_UNTIL_IDLE_LOCKOUT, NO_VALUE));
    }
    else if(subCommandName.equals(SC_GET_PASSWORD_RESET_STATE))
    {
      opElements.add(encode(OP_GET_PASSWORD_RESET_STATE, NO_VALUE));
    }
    else if(subCommandName.equals(SC_SET_PASSWORD_RESET_STATE))
    {
      Argument a = subCommand.getArgumentForName(ARG_OP_VALUE);
      if ((a != null) && a.isPresent())
      {
        String valueStr = a.getValue();
        if (valueStr.equalsIgnoreCase("true"))
        {
          opElements.add(encode(OP_SET_PASSWORD_RESET_STATE, "true"));
        }
        else if (valueStr.equalsIgnoreCase("false"))
        {
          opElements.add(encode(OP_SET_PASSWORD_RESET_STATE, "false"));
        }
        else
        {
          Message message = ERR_PWPSTATE_INVALID_BOOLEAN_VALUE.get(valueStr);
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
        }
      }
      else
      {
        Message message = ERR_PWPSTATE_NO_BOOLEAN_VALUE.get();
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
      }
    }
    else if(subCommandName.equals(SC_CLEAR_PASSWORD_RESET_STATE))
    {
      opElements.add(encode(OP_GET_PASSWORD_RESET_STATE, NO_VALUE));
    }
    else if(subCommandName.equals(SC_GET_SECONDS_UNTIL_PASSWORD_RESET_LOCKOUT))
    {
      opElements.add(encode(OP_GET_SECONDS_UNTIL_PASSWORD_RESET_LOCKOUT,
                            NO_VALUE));
    }
    else if(subCommandName.equals(SC_GET_GRACE_LOGIN_USE_TIMES))
    {
      opElements.add(encode(OP_GET_GRACE_LOGIN_USE_TIMES, NO_VALUE));
    }
    else if(subCommandName.equals(SC_ADD_GRACE_LOGIN_USE_TIME))
    {
      Argument a = subCommand.getArgumentForName(ARG_OP_VALUE);
      if ((a != null) && a.isPresent())
      {
        opElements.add(encode(OP_ADD_GRACE_LOGIN_USE_TIME, a.getValue()));
      }
      else
      {
        opElements.add(encode(OP_ADD_GRACE_LOGIN_USE_TIME, NO_VALUE));
      }
    }
    else if(subCommandName.equals(SC_SET_GRACE_LOGIN_USE_TIMES))
    {
      Argument a = subCommand.getArgumentForName(ARG_OP_VALUE);
      if ((a != null) && a.isPresent())
      {
        ArrayList<String> valueList = new ArrayList<String>(a.getValues());
        String[] values = new String[valueList.size()];
        valueList.toArray(values);

        opElements.add(encode(OP_SET_GRACE_LOGIN_USE_TIMES, values));
      }
      else
      {
        opElements.add(encode(OP_SET_GRACE_LOGIN_USE_TIMES, NO_VALUE));
      }
    }
    else if(subCommandName.equals(SC_CLEAR_GRACE_LOGIN_USE_TIMES))
    {
      opElements.add(encode(OP_CLEAR_GRACE_LOGIN_USE_TIMES, NO_VALUE));
    }
    else if(subCommandName.equals(SC_GET_REMAINING_GRACE_LOGIN_COUNT))
    {
      opElements.add(encode(OP_GET_REMAINING_GRACE_LOGIN_COUNT, NO_VALUE));
    }
    else if(subCommandName.equals(SC_GET_PASSWORD_CHANGED_BY_REQUIRED_TIME))
    {
      opElements.add(encode(OP_GET_PASSWORD_CHANGED_BY_REQUIRED_TIME,
                            NO_VALUE));
    }
    else if(subCommandName.equals(SC_SET_PASSWORD_CHANGED_BY_REQUIRED_TIME))
    {
      Argument a = subCommand.getArgumentForName(ARG_OP_VALUE);
      if ((a != null) && a.isPresent())
      {
        opElements.add(encode(OP_SET_PASSWORD_CHANGED_BY_REQUIRED_TIME,
                              a.getValue()));
      }
      else
      {
        opElements.add(encode(OP_SET_PASSWORD_CHANGED_BY_REQUIRED_TIME,
                              NO_VALUE));
      }
    }
    else if(subCommandName.equals(SC_CLEAR_PASSWORD_CHANGED_BY_REQUIRED_TIME))
    {
      opElements.add(encode(OP_CLEAR_PASSWORD_CHANGED_BY_REQUIRED_TIME,
                            NO_VALUE));
    }
    else if(subCommandName.equals(SC_GET_SECONDS_UNTIL_REQUIRED_CHANGE_TIME))
    {
      opElements.add(encode(OP_GET_SECONDS_UNTIL_REQUIRED_CHANGE_TIME,
                            NO_VALUE));
    }
    else if (subCommandName.equals(SC_GET_PASSWORD_HISTORY))
    {
      opElements.add(encode(OP_GET_PASSWORD_HISTORY, NO_VALUE));
    }
    else if (subCommandName.equals(SC_CLEAR_PASSWORD_HISTORY))
    {
      opElements.add(encode(OP_CLEAR_PASSWORD_HISTORY, NO_VALUE));
    }
    else
    {
      Message message = ERR_PWPSTATE_INVALID_SUBCOMMAND.get(subCommandName);
      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
    }

    return LDAPResultCode.SUCCESS;
  }



  /**
   * Prints information about a password policy state variable to standard
   * output.
   *
   * @param  msg     The message ID for the message to use as the label.
   * @param  values  The set of values for the associated state variable.
   */
  private static void printLabelAndValues(Message msg, ArrayList<String> values)
  {
    String label = String.valueOf(msg);
    if ((values == null) || values.isEmpty())
    {
      out.print(label);
      out.println(":");
    }
    else
    {
      for (String value : values)
      {
        out.print(label);
        out.print(":  ");
        out.println(value);
      }
    }
  }
}

