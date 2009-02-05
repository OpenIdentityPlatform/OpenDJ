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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.tools;
import org.opends.messages.Message;



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
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.ldap.AddRequestProtocolOp;
import org.opends.server.protocols.ldap.AddResponseProtocolOp;
import org.opends.server.protocols.ldap.ExtendedResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPConstants;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.tasks.ShutdownTask;
import org.opends.server.tools.tasks.TaskTool;
import org.opends.server.types.*;
import org.opends.server.util.args.Argument;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.LDAPConnectionArgumentParser;
import org.opends.server.util.args.StringArgument;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.tools.ToolConstants.*;



/**
 * This class provides a tool that can send a request to the Directory Server
 * that will cause it to shut down.
 */
public class StopDS
{
  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME = "org.opends.server.tools.StopDS";

  /**
   * Return codes used when the hidden option --checkStoppability is used.
   * NOTE: when checkStoppability is specified is recommended not to allocate
   * a lot of memory for the JVM (Using -Xms and -Xmx options) as there might
   * be calls to Runtime.exec.
   */
  /**
   * The server is already stopped.
   */
  private static int SERVER_ALREADY_STOPPED = 98;
  /**
   * The server must be started.
   */
  private static int START_SERVER = 99;
  /**
   * The server must be stopped using a system call.
   */
  private static int STOP_USING_SYSTEM_CALL = 100;
  /**
   * The server must be restarted using system calls.
   */
  private static int RESTART_USING_SYSTEM_CALL = 101;
  /**
   * The server must be stopped using protocol.
   */
  private static int STOP_USING_PROTOCOL = 102;
  /**
   * The server must be stopped as a window service.
   */
  private static int STOP_AS_WINDOW_SERVICE = 103;
  /**
   * The server must be restarted as a window service.
   */
  private static int RESTART_AS_WINDOW_SERVICE = 104;
  /**
   * The server must be started and it should use quiet mode.
   */
  private static int START_SERVER_QUIET = 105;
  /**
   * The server must be restarted using system calls and it should use quiet
   * mode.
   */
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


    // Define all the arguments that may be used with this program.
    Message toolDescription = INFO_STOPDS_TOOL_DESCRIPTION.get();
    ArgumentParser    argParser = new ArgumentParser(CLASS_NAME,
                                                     toolDescription, false);
    BooleanArgument   checkStoppability;
    BooleanArgument   quietMode;
    BooleanArgument   windowsNetStop;
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
      propertiesFileArgument = new StringArgument("propertiesFilePath",
          null, OPTION_LONG_PROP_FILE_PATH,
          false, false, true, INFO_PROP_FILE_PATH_PLACEHOLDER.get(), null, null,
          INFO_DESCRIPTION_PROP_FILE_PATH.get());
      argParser.addArgument(propertiesFileArgument);
      argParser.setFilePropertiesArgument(propertiesFileArgument);

      noPropertiesFileArgument = new BooleanArgument(
          "noPropertiesFileArgument", null, OPTION_LONG_NO_PROP_FILE,
          INFO_DESCRIPTION_NO_PROP_FILE.get());
      argParser.addArgument(noPropertiesFileArgument);
      argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

      host = new StringArgument("host", OPTION_SHORT_HOST,
                                OPTION_LONG_HOST, false, false, true,
                                INFO_HOST_PLACEHOLDER.get(), "127.0.0.1", null,
                                INFO_STOPDS_DESCRIPTION_HOST.get());
      host.setPropertyName(OPTION_LONG_HOST);
      argParser.addArgument(host);

      port = new IntegerArgument(
              "port", OPTION_SHORT_PORT,
              OPTION_LONG_PORT, false, false, true,
              INFO_PORT_PLACEHOLDER.get(), 389, null, true, 1,
              true, 65535, INFO_STOPDS_DESCRIPTION_PORT.get());
      port.setPropertyName(OPTION_LONG_PORT);
      argParser.addArgument(port);

      bindDN = new StringArgument("binddn", OPTION_SHORT_BINDDN,
                                  OPTION_LONG_BINDDN, false, false, true,
                                  INFO_BINDDN_PLACEHOLDER.get(), null, null,
                                  INFO_STOPDS_DESCRIPTION_BINDDN.get());
      bindDN.setPropertyName(OPTION_LONG_BINDDN);
      argParser.addArgument(bindDN);

      bindPW = new StringArgument("bindpw", OPTION_SHORT_BINDPWD,
                                  OPTION_LONG_BINDPWD, false, false,
                                  true,
                                  INFO_BINDPWD_PLACEHOLDER.get(), null, null,
                                  INFO_STOPDS_DESCRIPTION_BINDPW.get());
      bindPW.setPropertyName(OPTION_LONG_BINDPWD);
      argParser.addArgument(bindPW);

      bindPWFile = new FileBasedArgument(
              "bindpwfile",
              OPTION_SHORT_BINDPWD_FILE,
              OPTION_LONG_BINDPWD_FILE,
              false, false,
              INFO_BINDPWD_FILE_PLACEHOLDER.get(),
              null, null,
              INFO_STOPDS_DESCRIPTION_BINDPWFILE.get());
      bindPWFile.setPropertyName(OPTION_LONG_BINDPWD_FILE);
      argParser.addArgument(bindPWFile);

      saslOption = new StringArgument(
              "sasloption", OPTION_SHORT_SASLOPTION,
              OPTION_LONG_SASLOPTION, false,
              true, true,
              INFO_SASL_OPTION_PLACEHOLDER.get(), null, null,
              INFO_STOPDS_DESCRIPTION_SASLOPTIONS.get());
      saslOption.setPropertyName(OPTION_LONG_SASLOPTION);
      argParser.addArgument(saslOption);

      proxyAuthzID = new StringArgument(
              "proxyauthzid",
              OPTION_SHORT_PROXYAUTHID,
              OPTION_LONG_PROXYAUTHID, false,
              false, true,
              INFO_PROXYAUTHID_PLACEHOLDER.get(), null,
              null,
              INFO_STOPDS_DESCRIPTION_PROXYAUTHZID.get());
      proxyAuthzID.setPropertyName(OPTION_LONG_PROXYAUTHID);
      argParser.addArgument(proxyAuthzID);

      stopReason = new StringArgument(
              "stopreason", 'r', "stopReason", false,
              false, true, INFO_STOP_REASON_PLACEHOLDER.get(), null, null,
              INFO_STOPDS_DESCRIPTION_STOP_REASON.get());
      stopReason.setPropertyName("stopReason");
      argParser.addArgument(stopReason);

      checkStoppability = new BooleanArgument("checkstoppability", null,
              "checkStoppability",
              INFO_STOPDS_CHECK_STOPPABILITY.get());
      checkStoppability.setHidden(true);
      argParser.addArgument(checkStoppability);

      windowsNetStop = new BooleanArgument("windowsnetstop", null,
          "windowsNetStop", INFO_STOPDS_DESCRIPTION_WINDOWS_NET_STOP.get());
      windowsNetStop.setHidden(true);
      argParser.addArgument(windowsNetStop);

      restart = new BooleanArgument("restart", 'R', "restart",
                                    INFO_STOPDS_DESCRIPTION_RESTART.get());
      restart.setPropertyName("restart");
      argParser.addArgument(restart);

      stopTimeStr = new StringArgument("stoptime", 't', "stopTime", false,
                                       false, true,
                                       INFO_STOP_TIME_PLACEHOLDER.get(), null,
                                       null,
                                       INFO_STOPDS_DESCRIPTION_STOP_TIME.get());
      stopTimeStr.setPropertyName("stopTime");
      argParser.addArgument(stopTimeStr);

      trustAll = new BooleanArgument("trustall", 'X', "trustAll",
                                     INFO_STOPDS_DESCRIPTION_TRUST_ALL.get());
      trustAll.setPropertyName("trustAll");
      argParser.addArgument(trustAll);

      keyStoreFile = new StringArgument("keystorefile",
                                        OPTION_SHORT_KEYSTOREPATH,
                                        OPTION_LONG_KEYSTOREPATH,
                                        false, false, true,
                                        INFO_KEYSTOREPATH_PLACEHOLDER.get(),
                                        null, null,
                                        INFO_STOPDS_DESCRIPTION_KSFILE.get());
      keyStoreFile.setPropertyName(OPTION_LONG_KEYSTOREPATH);
      argParser.addArgument(keyStoreFile);

      keyStorePW = new StringArgument("keystorepw", OPTION_SHORT_KEYSTORE_PWD,
                                      OPTION_LONG_KEYSTORE_PWD,
                                      false, false, true,
                                      INFO_KEYSTORE_PWD_PLACEHOLDER.get(),
                                      null, null,
                                      INFO_STOPDS_DESCRIPTION_KSPW.get());
      keyStorePW.setPropertyName(OPTION_LONG_KEYSTORE_PWD);
      argParser.addArgument(keyStorePW);

      keyStorePWFile = new FileBasedArgument(
              "keystorepwfile",
              OPTION_SHORT_KEYSTORE_PWD_FILE,
              OPTION_LONG_KEYSTORE_PWD_FILE,
              false, false,
              INFO_KEYSTORE_PWD_FILE_PLACEHOLDER.get(),
              null, null,
              INFO_STOPDS_DESCRIPTION_KSPWFILE.get());
      keyStorePWFile.setPropertyName(OPTION_LONG_KEYSTORE_PWD_FILE);
      argParser.addArgument(keyStorePWFile);

      certNickname = new StringArgument(
              "certnickname", 'N', "certNickname",
              false, false, true, INFO_NICKNAME_PLACEHOLDER.get(), null,
              null, INFO_DESCRIPTION_CERT_NICKNAME.get());
      certNickname.setPropertyName("certNickname");
      argParser.addArgument(certNickname);

      trustStoreFile = new StringArgument("truststorefile",
                                          OPTION_SHORT_TRUSTSTOREPATH,
                                          OPTION_LONG_TRUSTSTOREPATH,
                                          false, false, true,
                                          INFO_TRUSTSTOREPATH_PLACEHOLDER.get(),
                                          null, null,
                                          INFO_STOPDS_DESCRIPTION_TSFILE.get());
      trustStoreFile.setPropertyName(OPTION_LONG_TRUSTSTOREPATH);
      argParser.addArgument(trustStoreFile);

      trustStorePW = new StringArgument(
              "truststorepw", 'T',
              OPTION_LONG_TRUSTSTORE_PWD,
              false, false,
              true, INFO_TRUSTSTORE_PWD_PLACEHOLDER.get(), null,
              null, INFO_STOPDS_DESCRIPTION_TSPW.get());
      trustStorePW.setPropertyName(OPTION_LONG_TRUSTSTORE_PWD);
      argParser.addArgument(trustStorePW);

      trustStorePWFile = new FileBasedArgument("truststorepwfile",
                                  OPTION_SHORT_TRUSTSTORE_PWD_FILE,
                                  OPTION_LONG_TRUSTSTORE_PWD_FILE,
                                  false, false,
                                  INFO_TRUSTSTORE_PWD_FILE_PLACEHOLDER.get(),
                                  null, null,
                                  INFO_STOPDS_DESCRIPTION_TSPWFILE.get());
      trustStorePWFile.setPropertyName(OPTION_LONG_TRUSTSTORE_PWD_FILE);
      argParser.addArgument(trustStorePWFile);

      quietMode = new BooleanArgument("quiet", OPTION_SHORT_QUIET,
                                      OPTION_LONG_QUIET,
                                      INFO_DESCRIPTION_QUIET.get());
      quietMode.setPropertyName(OPTION_LONG_QUIET);
      argParser.addArgument(quietMode);

      showUsage = new BooleanArgument("showusage", OPTION_SHORT_HELP,
                                      OPTION_LONG_HELP,
                                      INFO_STOPDS_DESCRIPTION_SHOWUSAGE.get());
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage, out);
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
    }


    // Parse the command-line arguments provided to the program.
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
      return LDAPResultCode.SUCCESS;
    }

    if (quietMode.isPresent())
    {
      out = NullOutputStream.printStream();
    }

    if (checkStoppability.isPresent())
    {
      System.exit(checkStoppability(argParser, out, err));
    }

    // If both a bind password and bind password file were provided, then return
    // an error.
    if (bindPW.isPresent() && bindPWFile.isPresent())
    {
      Message message = ERR_STOPDS_MUTUALLY_EXCLUSIVE_ARGUMENTS.get(
              bindPW.getLongIdentifier(),
              bindPWFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
    }


    // If both a key store password and key store password file were provided,
    // then return an error.
    if (keyStorePW.isPresent() && keyStorePWFile.isPresent())
    {
      Message message = ERR_STOPDS_MUTUALLY_EXCLUSIVE_ARGUMENTS.get(
              keyStorePW.getLongIdentifier(),
              keyStorePWFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
    }


    // If both a trust store password and trust store password file were
    // provided, then return an error.
    if (trustStorePW.isPresent() && trustStorePWFile.isPresent())
    {
      Message message = ERR_STOPDS_MUTUALLY_EXCLUSIVE_ARGUMENTS.get(
              trustStorePW.getLongIdentifier(),
              trustStorePWFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
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
          Message message = ERR_STOPDS_CANNOT_DECODE_STOP_TIME.get();
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
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
      Message message =
        ERR_STOPDS_CANNOT_INITIALIZE_SSL.get(sce.getMessage());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR;
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
          Message message = ERR_STOPDS_CANNOT_PARSE_SASL_OPTION.get(s);
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
        Message message = ERR_STOPDS_NO_SASL_MECHANISM.get();
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
    AtomicInteger nextMessageID = new AtomicInteger(1);
    LDAPConnection connection;
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
      Message message = ERR_STOPDS_CANNOT_DETERMINE_PORT.get(
              port.getLongIdentifier(),
              ae.getMessage());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
    }
    catch (LDAPConnectionException lce)
    {
      Message message = null;
      if ((lce.getCause() != null) && (lce.getCause().getCause() != null) &&
        lce.getCause().getCause() instanceof SSLException) {
      message = ERR_STOPDS_CANNOT_CONNECT_SSL.get(host.getValue(),
        port.getValue());
      } else {
        String hostPort = host.getValue() + ":" + port.getValue();
        message = ERR_STOPDS_CANNOT_CONNECT.get(hostPort,
          lce.getMessage());
      }
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR;
    }

    LDAPReader reader = connection.getLDAPReader();
    LDAPWriter writer = connection.getLDAPWriter();


    // Construct the add request to send to the server.
    String taskID = UUID.randomUUID().toString();
    ByteString entryDN =
        ByteString.valueOf(ATTR_TASK_ID + "=" + taskID + "," +
                            SCHEDULED_TASK_BASE_RDN + "," + DN_TASK_ROOT);

    ArrayList<RawAttribute> attributes = new ArrayList<RawAttribute>();

    ArrayList<ByteString> ocValues = new ArrayList<ByteString>(3);
    ocValues.add(ByteString.valueOf("top"));
    ocValues.add(ByteString.valueOf("ds-task"));
    ocValues.add(ByteString.valueOf("ds-task-shutdown"));
    attributes.add(new LDAPAttribute(ATTR_OBJECTCLASS, ocValues));

    ArrayList<ByteString> taskIDValues = new ArrayList<ByteString>(1);
    taskIDValues.add(ByteString.valueOf(taskID));
    attributes.add(new LDAPAttribute(ATTR_TASK_ID, taskIDValues));

    ArrayList<ByteString> classValues = new ArrayList<ByteString>(1);
    classValues.add(ByteString.valueOf(ShutdownTask.class.getName()));
    attributes.add(new LDAPAttribute(ATTR_TASK_CLASS, classValues));

    if (restart.isPresent())
    {
      ArrayList<ByteString> restartValues =
           new ArrayList<ByteString>(1);
      restartValues.add(ByteString.valueOf("true"));
      attributes.add(new LDAPAttribute(ATTR_RESTART_SERVER, restartValues));
    }

    if (stopReason.isPresent())
    {
      ArrayList<ByteString> stopReasonValues =
           new ArrayList<ByteString>(1);
      stopReasonValues.add(ByteString.valueOf(stopReason.getValue()));
      attributes.add(new LDAPAttribute(ATTR_SHUTDOWN_MESSAGE,
                                       stopReasonValues));
    }

    if (stopTime != null)
    {
      ArrayList<ByteString> stopTimeValues =
           new ArrayList<ByteString>(1);

      SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_GMT_TIME);
      dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      stopTimeValues.add(ByteString.valueOf(dateFormat.format(stopTime)));
      attributes.add(new LDAPAttribute(ATTR_TASK_SCHEDULED_START_TIME,
                                       stopTimeValues));
    }

    ArrayList<Control> controls = new ArrayList<Control>();
    if (proxyAuthzID.isPresent())
    {
      Control c = new ProxiedAuthV2Control(
          ByteString.valueOf(proxyAuthzID.getValue()));
      controls.add(c);
    }

    AddRequestProtocolOp addRequest = new AddRequestProtocolOp(entryDN,
                                                               attributes);
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
        Message message = ERR_STOPDS_UNEXPECTED_CONNECTION_CLOSURE.get();
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return LDAPResultCode.CLIENT_SIDE_SERVER_DOWN;
      }
    }
    catch (IOException ioe)
    {
      Message message = ERR_STOPDS_IO_ERROR.get(String.valueOf(ioe));
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_SERVER_DOWN;
    }
    catch (ASN1Exception ae)
    {
      Message message = ERR_STOPDS_DECODE_ERROR.get(ae.getMessage());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_DECODING_ERROR;
    }
    catch (LDAPException le)
    {
      Message message = ERR_STOPDS_DECODE_ERROR.get(le.getMessage());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_DECODING_ERROR;
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
        if ((responseOID != null) &&
            (responseOID.equals(LDAPConstants.OID_NOTICE_OF_DISCONNECTION)))
        {
          Message message = extendedResponse.getErrorMessage();
          if (message != null)
          {
            err.println(wrapText(message, MAX_LINE_WIDTH));
          }

          return extendedResponse.getResultCode();
        }
      }


      Message message = ERR_STOPDS_INVALID_RESPONSE_TYPE.get(
              responseMessage.getProtocolOpName());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR;
    }


    AddResponseProtocolOp addResponse =
         responseMessage.getAddResponseProtocolOp();
    Message errorMessage = addResponse.getErrorMessage();
    if (errorMessage != null)
    {
      err.println(wrapText(errorMessage, MAX_LINE_WIDTH));
    }

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
    Argument quietArg = argParser.getArgumentForLongID("quiet");
    if ((quietArg != null) && quietArg.isPresent())
    {
      quietMode = true;
    }

    BooleanArgument restart =
      (BooleanArgument)argParser.getArgumentForLongID("restart");
    boolean restartPresent = restart.isPresent();
    BooleanArgument windowsNetStop =
      (BooleanArgument)argParser.getArgumentForLongID("windowsnetstop");
    boolean windowsNetStopPresent = windowsNetStop.isPresent();

    // Check if this is a stop through protocol.
    LinkedList<Argument> list = argParser.getArgumentList();
    boolean stopThroughProtocol = false;
    for (Argument arg: list)
    {
      if (!"restart".equals(arg.getName()) &&
          !"quiet".equals(arg.getName()) &&
          !"showusage".equals(arg.getName()) &&
          !"checkstoppability".equals(arg.getName()) &&
          !"windowsnetstop".equals(arg.getName()))
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
          Message message = INFO_STOPDS_SERVER_ALREADY_STOPPED.get();
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

            Message message = INFO_STOPDS_GOING_TO_STOP.get();
            out.println(message);
          }
        }
        else
        {
          // Display a message informing that we are going to the server.

          Message message = INFO_STOPDS_GOING_TO_STOP.get();
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

