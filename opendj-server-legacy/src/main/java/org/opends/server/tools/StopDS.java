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

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.CliMessages.INFO_BINDPWD_FILE_PLACEHOLDER;
import static com.forgerock.opendj.cli.CliMessages.INFO_KEYSTORE_PWD_FILE_PLACEHOLDER;
import static com.forgerock.opendj.cli.CliMessages.INFO_PORT_PLACEHOLDER;
import static com.forgerock.opendj.cli.CliMessages.INFO_TRUSTSTORE_PWD_FILE_PLACEHOLDER;
import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.cli.CommonArguments.*;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.opends.server.config.AdministrationConnector;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.DirectoryServer.DirectoryServerVersionHandler;
import org.opends.server.core.LockFileManager;
import org.opends.server.loggers.JDKLogging;
import org.opends.server.protocols.ldap.AddRequestProtocolOp;
import org.opends.server.protocols.ldap.AddResponseProtocolOp;
import org.opends.server.protocols.ldap.ExtendedResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPConstants;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.tasks.ShutdownTask;
import org.opends.server.tools.tasks.TaskTool;
import org.opends.server.types.Control;
import org.opends.server.types.LDAPException;
import org.opends.server.types.NullOutputStream;
import org.opends.server.types.RawAttribute;
import org.opends.server.util.cli.LDAPConnectionArgumentParser;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentConstants;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.FileBasedArgument;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.StringArgument;

/**
 * This class provides a tool that can send a request to the Directory Server
 * that will cause it to shut down.
 */
public class StopDS
{
  /** The fully-qualified name of this class. */
  private static final String CLASS_NAME = "org.opends.server.tools.StopDS";

  /*
   * Return codes used when the hidden option --checkStoppability is used.
   * NOTE: when checkStoppability is specified, it is recommended not to allocate a lot of memory for the JVM
   * (Using -Xms and -Xmx options) as there might be calls to Runtime.exec.
   */
  /** The server is already stopped. */
  private static int SERVER_ALREADY_STOPPED = 98;
  /** The server must be started. */
  private static int START_SERVER = 99;
  /** The server must be stopped using a system call. */
  private static int STOP_USING_SYSTEM_CALL = 100;
  /** The server must be restarted using system calls. */
  private static int RESTART_USING_SYSTEM_CALL = 101;
  /** The server must be stopped using protocol. */
  private static int STOP_USING_PROTOCOL = 102;
  /** The server must be stopped as a window service. */
  private static int STOP_AS_WINDOW_SERVICE = 103;
  /** The server must be restarted as a window service. */
  private static int RESTART_AS_WINDOW_SERVICE = 104;
  /** The server must be started and it should use quiet mode. */
  private static int START_SERVER_QUIET = 105;
  /** The server must be restarted using system calls and it should use quiet mode. */
  private static int RESTART_USING_SYSTEM_CALL_QUIET = 106;

  /**
   * Invokes the <CODE>stopDS</CODE> method, passing it the provided command
   * line arguments.  If the call to <CODE>stopDS</CODE> returns a nonzero
   * value, then that will be used as the exit code for this program.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int result = stopDS(args, System.out, System.err);

    if (result != LDAPResultCode.SUCCESS)
    {
      System.exit(filterExitCode(result));
    }
  }



  /**
   * Parses the provided set of command-line arguments and attempts to contact
   * the Directory Server in order to send it the shutdown request.
   *
   * @param  args  The command-line arguments provided to this program.
   *
   * @return  An integer value that indicates whether the shutdown request was
   *          accepted by the Directory Server.  A nonzero value should be
   *          interpreted as a failure of some kind.
   */
  public static int stopDS(String[] args)
  {
    return stopDS(args, System.out, System.err);
  }



  /**
   * Parses the provided set of command-line arguments and attempts to contact
   * the Directory Server in order to send it the shutdown request.
   *
   * @param  args       The command-line arguments provided to this program.
   * @param  outStream  The output stream to use for standard output, or
   *                    <CODE>null</CODE> if standard output is not needed.
   * @param  errStream  The output stream to use for standard error, or
   *                    <CODE>null</CODE> if standard error is not needed.
   *
   * @return  An integer value that indicates whether the shutdown request was
   *          accepted by the Directory Server.  A nonzero value should be
   *          interpreted as a failure of some kind.
   */
  public static int stopDS(String[] args, OutputStream outStream,
                           OutputStream errStream)
  {
    PrintStream out = NullOutputStream.wrapOrNullStream(outStream);
    PrintStream err = NullOutputStream.wrapOrNullStream(errStream);
    JDKLogging.disableLogging();

    // Define all the arguments that may be used with this program.
    LocalizableMessage toolDescription = INFO_STOPDS_TOOL_DESCRIPTION.get();
    ArgumentParser    argParser = new ArgumentParser(CLASS_NAME,
                                                     toolDescription, false);
    argParser.setShortToolDescription(REF_SHORT_DESC_STOP_DS.get());

    argParser.setVersionHandler(new DirectoryServerVersionHandler());
    BooleanArgument   checkStoppability;
    BooleanArgument   quietMode;
    BooleanArgument   restart;
    BooleanArgument   showUsage;
    BooleanArgument   trustAll;
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
    StringArgument    proxyAuthzID;
    StringArgument    saslOption;
    StringArgument    stopReason;
    StringArgument    stopTimeStr;
    StringArgument    trustStoreFile;
    StringArgument    trustStorePW;
    StringArgument    propertiesFileArgument;
    BooleanArgument   noPropertiesFileArgument;

    try
    {
      propertiesFileArgument =
              StringArgument.builder(OPTION_LONG_PROP_FILE_PATH)
                      .description(INFO_DESCRIPTION_PROP_FILE_PATH.get())
                      .valuePlaceholder(INFO_PROP_FILE_PATH_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      argParser.setFilePropertiesArgument(propertiesFileArgument);

      noPropertiesFileArgument =
              BooleanArgument.builder(OPTION_LONG_NO_PROP_FILE)
                      .description(INFO_DESCRIPTION_NO_PROP_FILE.get())
                      .buildAndAddToParser(argParser);
      argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

      host =
              StringArgument.builder(OPTION_LONG_HOST)
                      .shortIdentifier(OPTION_SHORT_HOST)
                      .description(INFO_STOPDS_DESCRIPTION_HOST.get())
                      .defaultValue("127.0.0.1")
                      .valuePlaceholder(INFO_HOST_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      port =
              IntegerArgument.builder(OPTION_LONG_PORT)
                      .shortIdentifier(OPTION_SHORT_PORT)
                      .description(INFO_STOPDS_DESCRIPTION_PORT.get())
                      .range(1, 65535)
                      .defaultValue(AdministrationConnector.DEFAULT_ADMINISTRATION_CONNECTOR_PORT)
                      .valuePlaceholder(INFO_PORT_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      bindDN =
              StringArgument.builder(OPTION_LONG_BINDDN)
                      .shortIdentifier(OPTION_SHORT_BINDDN)
                      .description(INFO_STOPDS_DESCRIPTION_BINDDN.get())
                      .valuePlaceholder(INFO_BINDDN_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      bindPW =
              StringArgument.builder(OPTION_LONG_BINDPWD)
                      .shortIdentifier(OPTION_SHORT_BINDPWD)
                      .description(INFO_STOPDS_DESCRIPTION_BINDPW.get())
                      .valuePlaceholder(INFO_BINDPWD_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      bindPWFile =
              FileBasedArgument.builder(OPTION_LONG_BINDPWD_FILE)
                      .shortIdentifier(OPTION_SHORT_BINDPWD_FILE)
                      .description(INFO_STOPDS_DESCRIPTION_BINDPWFILE.get())
                      .valuePlaceholder(INFO_BINDPWD_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      saslOption =
              StringArgument.builder(OPTION_LONG_SASLOPTION)
                      .shortIdentifier(OPTION_SHORT_SASLOPTION)
                      .description(INFO_STOPDS_DESCRIPTION_SASLOPTIONS.get())
                      .multiValued()
                      .valuePlaceholder(INFO_SASL_OPTION_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      proxyAuthzID =
              StringArgument.builder(OPTION_LONG_PROXYAUTHID)
                      .shortIdentifier(OPTION_SHORT_PROXYAUTHID)
                      .description(INFO_STOPDS_DESCRIPTION_PROXYAUTHZID.get())
                      .valuePlaceholder(INFO_PROXYAUTHID_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      stopReason =
              StringArgument.builder("stopReason")
                      .shortIdentifier('r')
                      .description(INFO_STOPDS_DESCRIPTION_STOP_REASON.get())
                      .valuePlaceholder(INFO_STOP_REASON_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      checkStoppability =
              BooleanArgument.builder(OPTION_LONG_CHECK_STOPPABILITY)
                      .description(INFO_STOPDS_CHECK_STOPPABILITY.get())
                      .hidden()
                      .buildAndAddToParser(argParser);
      BooleanArgument.builder(OPTION_LONG_WINDOWS_NET_STOP)
              .description(INFO_STOPDS_DESCRIPTION_WINDOWS_NET_STOP.get())
              .hidden()
              .buildAndAddToParser(argParser);

      restart = restartArgument();
      argParser.addArgument(restart);

      stopTimeStr =
              StringArgument.builder("stopTime")
                      .shortIdentifier('t')
                      .description(INFO_STOPDS_DESCRIPTION_STOP_TIME.get())
                      .valuePlaceholder(INFO_STOP_TIME_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);

      trustAll = trustAllArgument();
      argParser.addArgument(trustAll);

      keyStoreFile =
              StringArgument.builder(OPTION_LONG_KEYSTOREPATH)
                      .shortIdentifier(OPTION_SHORT_KEYSTOREPATH)
                      .description(INFO_STOPDS_DESCRIPTION_KSFILE.get())
                      .valuePlaceholder(INFO_KEYSTOREPATH_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      keyStorePW =
              StringArgument.builder(OPTION_LONG_KEYSTORE_PWD)
                      .shortIdentifier(OPTION_SHORT_KEYSTORE_PWD)
                      .description(INFO_STOPDS_DESCRIPTION_KSPW.get())
                      .valuePlaceholder(INFO_KEYSTORE_PWD_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      keyStorePWFile =
              FileBasedArgument.builder(OPTION_LONG_KEYSTORE_PWD_FILE)
                      .shortIdentifier(OPTION_SHORT_KEYSTORE_PWD_FILE)
                      .description(INFO_STOPDS_DESCRIPTION_KSPWFILE.get())
                      .valuePlaceholder(INFO_KEYSTORE_PWD_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      certNickname =
              StringArgument.builder("certNickname")
                      .shortIdentifier('N')
                      .description(INFO_DESCRIPTION_CERT_NICKNAME.get())
                      .valuePlaceholder(INFO_NICKNAME_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      trustStoreFile =
              StringArgument.builder(OPTION_LONG_TRUSTSTOREPATH)
                      .shortIdentifier(OPTION_SHORT_TRUSTSTOREPATH)
                      .description(INFO_STOPDS_DESCRIPTION_TSFILE.get())
                      .valuePlaceholder(INFO_TRUSTSTOREPATH_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      trustStorePW =
              StringArgument.builder(OPTION_LONG_TRUSTSTORE_PWD)
                      .shortIdentifier('T')
                      .description(INFO_STOPDS_DESCRIPTION_TSPW.get())
                      .valuePlaceholder(INFO_TRUSTSTORE_PWD_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      trustStorePWFile =
              FileBasedArgument.builder(OPTION_LONG_TRUSTSTORE_PWD_FILE)
                      .shortIdentifier(OPTION_SHORT_TRUSTSTORE_PWD_FILE)
                      .description(INFO_STOPDS_DESCRIPTION_TSPWFILE.get())
                      .valuePlaceholder(INFO_TRUSTSTORE_PWD_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);

      quietMode = quietArgument();
      argParser.addArgument(quietMode);

      showUsage = showUsageArgument();
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage, out);
    }
    catch (ArgumentException ae)
    {
      printWrappedText(err, ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
      return CLIENT_SIDE_PARAM_ERROR;
    }


    // Parse the command-line arguments provided to the program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      argParser.displayMessageAndUsageReference(err, ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
      return CLIENT_SIDE_PARAM_ERROR;
    }


    // If we should just display usage or version information,
    // then exit because it will have already been done.
    if (argParser.usageOrVersionDisplayed())
    {
      return LDAPResultCode.SUCCESS;
    }

    if (quietMode.isPresent())
    {
      out = NullOutputStream.nullPrintStream();
    }

    if (checkStoppability.isPresent())
    {
      System.exit(checkStoppability(argParser, out, err));
    }

    // If both a bind password and bind password file were provided, then return
    // an error.
    if (bindPW.isPresent() && bindPWFile.isPresent())
    {
      printWrappedText(err,
          ERR_STOPDS_MUTUALLY_EXCLUSIVE_ARGUMENTS.get(bindPW.getLongIdentifier(), bindPWFile.getLongIdentifier()));
      return CLIENT_SIDE_PARAM_ERROR;
    }


    // If both a key store password and key store password file were provided,
    // then return an error.
    if (keyStorePW.isPresent() && keyStorePWFile.isPresent())
    {
      printWrappedText(err, ERR_STOPDS_MUTUALLY_EXCLUSIVE_ARGUMENTS.get(
              keyStorePW.getLongIdentifier(), keyStorePWFile.getLongIdentifier()));
      return CLIENT_SIDE_PARAM_ERROR;
    }


    // If both a trust store password and trust store password file were
    // provided, then return an error.
    if (trustStorePW.isPresent() && trustStorePWFile.isPresent())
    {
      printWrappedText(err, ERR_STOPDS_MUTUALLY_EXCLUSIVE_ARGUMENTS.get(
              trustStorePW.getLongIdentifier(), trustStorePWFile.getLongIdentifier()));
      return CLIENT_SIDE_PARAM_ERROR;
    }


    // Make sure that we can decode the stop time string if one was provided.
    Date stopTime = new Date();
    if (stopTimeStr.isPresent())
    {
      String timeStr = stopTimeStr.getValue();
      if (!TaskTool.NOW.equals(timeStr))
      {
        try
        {
          stopTime = parseDateTimeString(timeStr);
        }
        catch (Exception e)
        {
          printWrappedText(err, ERR_STOPDS_CANNOT_DECODE_STOP_TIME.get());
          return CLIENT_SIDE_PARAM_ERROR;
        }
        // Check that the provided date is not previous to the current date.
        Date currentDate = new Date(System.currentTimeMillis());
        if (currentDate.after(stopTime))
        {
          printWrappedText(err, ERR_STOPDS_DATETIME_ALREADY_PASSED.get(timeStr));
          return CLIENT_SIDE_PARAM_ERROR;
        }
      }
    }


    // Create the LDAP connection options object, which will be used to
    // customize the way that we connect to the server and specify a set of
    // basic defaults.
    LDAPConnectionOptions connectionOptions = new LDAPConnectionOptions();
    connectionOptions.setVersionNumber(3);


    try {
      String clientAlias;
      if (certNickname.isPresent()) {
        clientAlias = certNickname.getValue();
      } else {
        clientAlias = null;
      }

      SSLConnectionFactory sslConnectionFactory = new SSLConnectionFactory();
      sslConnectionFactory.init(trustAll.isPresent(), keyStoreFile.getValue(),
        keyStorePW.getValue(), clientAlias,
        trustStoreFile.getValue(),
        trustStorePW.getValue());

      connectionOptions.setSSLConnectionFactory(sslConnectionFactory);
    } catch (SSLConnectionException sce) {
      printWrappedText(err, ERR_STOPDS_CANNOT_INITIALIZE_SSL.get(sce.getMessage()));
      return CLIENT_SIDE_LOCAL_ERROR;
    }


    // If one or more SASL options were provided, then make sure that one of
    // them was "mech" and specified a valid SASL mechanism.
    if (saslOption.isPresent())
    {
      String             mechanism = null;
      LinkedList<String> options   = new LinkedList<>();

      for (String s : saslOption.getValues())
      {
        int equalPos = s.indexOf('=');
        if (equalPos <= 0)
        {
          printWrappedText(err, ERR_STOPDS_CANNOT_PARSE_SASL_OPTION.get(s));
          return CLIENT_SIDE_PARAM_ERROR;
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
        printWrappedText(err, ERR_STOPDS_NO_SASL_MECHANISM.get());
        return CLIENT_SIDE_PARAM_ERROR;
      }

      connectionOptions.setSASLMechanism(mechanism);

      for (String option : options)
      {
        connectionOptions.addSASLProperty(option);
      }
    }


    // Attempt to connect and authenticate to the Directory Server.
    AtomicInteger nextMessageID = new AtomicInteger(1);
    LDAPConnection connection;
    try
    {
      connection = new LDAPConnection(host.getValue(), port.getIntValue(),
                                      connectionOptions, out, err);
      connection.connectToHost(bindDN.getValue(),
          LDAPConnectionArgumentParser.getPasswordValue(bindPW, bindPWFile,
                                                        bindDN, out, err),
          nextMessageID);
    }
    catch (ArgumentException ae)
    {
      argParser.displayMessageAndUsageReference(
          err, ERR_STOPDS_CANNOT_DETERMINE_PORT.get(port.getLongIdentifier(), ae.getMessage()));
      return CLIENT_SIDE_PARAM_ERROR;
    }
    catch (LDAPConnectionException lce)
    {
      LocalizableMessage message;
      if (lce.getCause() != null && lce.getCause().getCause() != null &&
        lce.getCause().getCause() instanceof SSLException) {
        message = ERR_STOPDS_CANNOT_CONNECT_SSL.get(host.getValue(),
        port.getValue());
      } else {
        String hostPort = host.getValue() + ":" + port.getValue();
        message = ERR_STOPDS_CANNOT_CONNECT.get(hostPort,
          lce.getMessage());
      }
      printWrappedText(err, message);
      return CLIENT_SIDE_CONNECT_ERROR;
    }

    LDAPReader reader = connection.getLDAPReader();
    LDAPWriter writer = connection.getLDAPWriter();


    // Construct the add request to send to the server.
    String taskID = UUID.randomUUID().toString();
    ByteString entryDN =
        ByteString.valueOfUtf8(ATTR_TASK_ID + "=" + taskID + "," +
                            SCHEDULED_TASK_BASE_RDN + "," + DN_TASK_ROOT);

    ArrayList<RawAttribute> attributes = new ArrayList<>();
    attributes.add(new LDAPAttribute(ATTR_OBJECTCLASS, newArrayList("top", "ds-task", "ds-task-shutdown")));
    attributes.add(new LDAPAttribute(ATTR_TASK_ID, taskID));
    attributes.add(new LDAPAttribute(ATTR_TASK_CLASS, ShutdownTask.class.getName()));
    if (restart.isPresent())
    {
      attributes.add(new LDAPAttribute(ATTR_RESTART_SERVER, "true"));
    }
    if (stopReason.isPresent())
    {
      attributes.add(new LDAPAttribute(ATTR_SHUTDOWN_MESSAGE, stopReason.getValue()));
    }

    SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_GMT_TIME);
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    String stopTimeValues = dateFormat.format(stopTime);
    attributes.add(new LDAPAttribute(ATTR_TASK_SCHEDULED_START_TIME, stopTimeValues));

    ArrayList<Control> controls = new ArrayList<>();
    if (proxyAuthzID.isPresent())
    {
      controls.add(new ProxiedAuthV2Control(
          ByteString.valueOfUtf8(proxyAuthzID.getValue())));
    }

    AddRequestProtocolOp addRequest = new AddRequestProtocolOp(entryDN, attributes);
    LDAPMessage requestMessage =
         new LDAPMessage(nextMessageID.getAndIncrement(), addRequest, controls);


    // Send the request to the server and read the response.
    LDAPMessage responseMessage;
    try
    {
      writer.writeMessage(requestMessage);

      responseMessage = reader.readMessage();
      if (responseMessage == null)
      {
        printWrappedText(err, ERR_STOPDS_UNEXPECTED_CONNECTION_CLOSURE.get());
        return CLIENT_SIDE_SERVER_DOWN;
      }
    }
    catch (DecodeException | LDAPException e)
    {
      printWrappedText(err, ERR_STOPDS_DECODE_ERROR.get(e.getMessage()));
      return CLIENT_SIDE_DECODING_ERROR;
    }
    catch (IOException ioe)
    {
      printWrappedText(err, ERR_STOPDS_IO_ERROR.get(ioe));
      return LDAPResultCode.CLIENT_SIDE_SERVER_DOWN;
    }


    if (responseMessage.getProtocolOpType() !=
        LDAPConstants.OP_TYPE_ADD_RESPONSE)
    {
      if (responseMessage.getProtocolOpType() ==
          LDAPConstants.OP_TYPE_EXTENDED_RESPONSE)
      {
        // It's possible that this is a notice of disconnection, which we can
        // probably interpret as a "success" in this case.
        ExtendedResponseProtocolOp extendedResponse =
             responseMessage.getExtendedResponseProtocolOp();
        String responseOID = extendedResponse.getOID();
        if (LDAPConstants.OID_NOTICE_OF_DISCONNECTION.equals(responseOID))
        {
          printWrappedText(err, extendedResponse.getErrorMessage());
          return extendedResponse.getResultCode();
        }
      }


      printWrappedText(err, ERR_STOPDS_INVALID_RESPONSE_TYPE.get(responseMessage.getProtocolOpName()));


      return CLIENT_SIDE_LOCAL_ERROR;
    }


    AddResponseProtocolOp addResponse = responseMessage.getAddResponseProtocolOp();
    printWrappedText(err, addResponse.getErrorMessage());
    return addResponse.getResultCode();
  }

  /**
   * Returns the error code that we return when we are checking the stoppability
   * of the server.  This basically tells the invoker what must be done based
   * on the different parameters passed.
   * @param argParser the ArgumentParser with the arguments already parsed.
   * @param out the print stream to use for standard output.
   * @param err the print stream to use for standard error.
   * @return the error code that we return when we are checking the stoppability
   * of the server.
   */
  private static int checkStoppability(ArgumentParser argParser,
                                       PrintStream out, PrintStream err)
  {
    int returnValue;
    boolean isServerRunning;

    boolean quietMode = false;
    Argument quietArg = argParser.getArgumentForLongID(ArgumentConstants.OPTION_LONG_QUIET);
    if (quietArg != null && quietArg.isPresent())
    {
      quietMode = true;
    }

    final boolean restartPresent = argParser.getArgumentForLongID(OPTION_LONG_RESTART).isPresent();
    final boolean windowsNetStopPresent = argParser.getArgumentForLongID(OPTION_LONG_WINDOWS_NET_STOP).isPresent();

    // Check if this is a stop through protocol.
    boolean stopThroughProtocol = false;
    for (final Argument arg: argParser.getArgumentList())
    {
      if (!OPTION_LONG_RESTART.equals(arg.getLongIdentifier()) &&
          !OPTION_LONG_QUIET.equals(arg.getLongIdentifier()) &&
          !OPTION_LONG_HELP.equals(arg.getLongIdentifier()) &&
          !OPTION_LONG_CHECK_STOPPABILITY.equals(arg.getLongIdentifier()) &&
          !OPTION_LONG_WINDOWS_NET_STOP.equals(arg.getLongIdentifier()) &&
          !OPTION_LONG_NO_PROP_FILE.equals(arg.getLongIdentifier()))
      {
        stopThroughProtocol |= arg.isPresent();
      }
    }

    if (stopThroughProtocol)
    {
      // Assume that this is done on a remote server and do no more checks.
      returnValue = STOP_USING_PROTOCOL;
    }
    else
    {
      String lockFile = LockFileManager.getServerLockFileName();
      try
      {
        StringBuilder failureReason = new StringBuilder();
        if (LockFileManager.acquireExclusiveLock(lockFile, failureReason))
        {
          // The server is not running: write a message informing of that
          // in the standard out (this is not an error message).
          LocalizableMessage message = INFO_STOPDS_SERVER_ALREADY_STOPPED.get();
          out.println(message);
          LockFileManager.releaseLock(lockFile, failureReason);
          isServerRunning = false;
        }
        else
        {
          isServerRunning = true;
        }
      }
      catch (Exception e)
      {
        // Assume that if we cannot acquire the lock file the server is
        // running.
        isServerRunning = true;
      }

      boolean configuredAsService =
          DirectoryServer.isRunningAsWindowsService();

      if (!isServerRunning)
      {
        if (configuredAsService && !windowsNetStopPresent)
        {
          if (restartPresent)
          {
            returnValue = RESTART_AS_WINDOW_SERVICE;
          }
          else
          {
            returnValue = STOP_AS_WINDOW_SERVICE;
          }
        }
        else if (restartPresent)
        {
          if (quietMode)
          {
            returnValue = START_SERVER_QUIET;
          }
          else
          {
            returnValue = START_SERVER;
          }
        }
        else
        {
          returnValue = SERVER_ALREADY_STOPPED;
        }
      }
      else
      {
        if (configuredAsService)
        {
          if (windowsNetStopPresent)
          {
            // stop-ds.bat is being called through net stop, so return
            // STOP_USING_SYSTEM_CALL or RESTART_USING_SYSTEM_CALL so that the
            // batch file actually stops the server.
            if (restartPresent)
            {
              if (quietMode)
              {
                returnValue = RESTART_USING_SYSTEM_CALL_QUIET;
              }
              else
              {
                returnValue = RESTART_USING_SYSTEM_CALL;
              }
            }
            else
            {
              returnValue = STOP_USING_SYSTEM_CALL;
            }
          }
          else
          {
            if (restartPresent)
            {
              returnValue = RESTART_AS_WINDOW_SERVICE;
            }
            else
            {
              returnValue = STOP_AS_WINDOW_SERVICE;
            }
            // Display a message informing that we are going to the server.

            LocalizableMessage message = INFO_STOPDS_GOING_TO_STOP.get();
            out.println(message);
          }
        }
        else
        {
          // Display a message informing that we are going to the server.

          LocalizableMessage message = INFO_STOPDS_GOING_TO_STOP.get();
          out.println(message);

          if (restartPresent)
          {
            if (quietMode)
            {
              returnValue = RESTART_USING_SYSTEM_CALL_QUIET;
            }
            else
            {
              returnValue = RESTART_USING_SYSTEM_CALL;
            }
          }
          else
          {
            returnValue = STOP_USING_SYSTEM_CALL;
          }
        }
      }
    }
    return returnValue;
  }
}

