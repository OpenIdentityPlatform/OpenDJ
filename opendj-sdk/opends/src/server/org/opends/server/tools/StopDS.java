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

import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.AddRequestProtocolOp;
import org.opends.server.protocols.ldap.AddResponseProtocolOp;
import org.opends.server.protocols.ldap.ExtendedResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPConstants;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.tasks.ShutdownTask;
import org.opends.server.types.Control;
import org.opends.server.types.LDAPException;
import org.opends.server.types.NullOutputStream;
import org.opends.server.types.RawAttribute;
import org.opends.server.util.args.Argument;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.IntegerArgument;
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
    StringArgument    proxyAuthzID;
    StringArgument    saslOption;
    StringArgument    stopReason;
    StringArgument    stopTimeStr;
    StringArgument    trustStoreFile;
    StringArgument    trustStorePW;

    try
    {
      host = new StringArgument("host", OPTION_SHORT_HOST,
                                OPTION_LONG_HOST, false, false, true,
                                OPTION_VALUE_HOST, "127.0.0.1", null,
                                INFO_STOPDS_DESCRIPTION_HOST.get());
      argParser.addArgument(host);

      port = new IntegerArgument(
              "port", OPTION_SHORT_PORT,
              OPTION_LONG_PORT, false, false, true,
              OPTION_VALUE_PORT, 389, null, true, 1,
              true, 65535, INFO_STOPDS_DESCRIPTION_PORT.get());
      argParser.addArgument(port);

      useSSL = new BooleanArgument("usessl", OPTION_SHORT_USE_SSL,
                                   OPTION_LONG_USE_SSL,
                                   INFO_STOPDS_DESCRIPTION_USESSL.get());
      argParser.addArgument(useSSL);

      useStartTLS = new BooleanArgument(
              "usestarttls", OPTION_SHORT_START_TLS,
              OPTION_LONG_START_TLS,
              INFO_STOPDS_DESCRIPTION_USESTARTTLS.get());
      argParser.addArgument(useStartTLS);

      bindDN = new StringArgument("binddn", OPTION_SHORT_BINDDN,
                                  OPTION_LONG_BINDDN, false, false, true,
                                  OPTION_VALUE_BINDDN, null, null,
                                  INFO_STOPDS_DESCRIPTION_BINDDN.get());
      argParser.addArgument(bindDN);

      bindPW = new StringArgument("bindpw", OPTION_SHORT_BINDPWD,
                                  OPTION_LONG_BINDPWD, false, false,
                                  true,
                                  OPTION_VALUE_BINDPWD, null, null,
                                  INFO_STOPDS_DESCRIPTION_BINDPW.get());
      argParser.addArgument(bindPW);

      bindPWFile = new FileBasedArgument(
              "bindpwfile",
              OPTION_SHORT_BINDPWD_FILE,
              OPTION_LONG_BINDPWD_FILE,
              false, false,
              OPTION_VALUE_BINDPWD_FILE,
              null, null,
              INFO_STOPDS_DESCRIPTION_BINDPWFILE.get());
      argParser.addArgument(bindPWFile);

      saslOption = new StringArgument(
              "sasloption", OPTION_SHORT_SASLOPTION,
              OPTION_LONG_SASLOPTION, false,
              true, true,
              OPTION_VALUE_SASLOPTION, null, null,
              INFO_STOPDS_DESCRIPTION_SASLOPTIONS.get());
      argParser.addArgument(saslOption);

      proxyAuthzID = new StringArgument(
              "proxyauthzid",
              OPTION_SHORT_PROXYAUTHID,
              OPTION_LONG_PROXYAUTHID, false,
              false, true,
              OPTION_VALUE_PROXYAUTHID, null,
              null,
              INFO_STOPDS_DESCRIPTION_PROXYAUTHZID.get());
      argParser.addArgument(proxyAuthzID);

      stopReason = new StringArgument(
              "stopreason", 'r', "stopReason", false,
              false, true, "{stopReason}", null, null,
              INFO_STOPDS_DESCRIPTION_STOP_REASON.get());
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
      argParser.addArgument(restart);

      stopTimeStr = new StringArgument("stoptime", 't', "stopTime", false,
                                       false, true, "{stopTime}", null, null,
                                       INFO_STOPDS_DESCRIPTION_STOP_TIME.get());
      argParser.addArgument(stopTimeStr);

      trustAll = new BooleanArgument("trustall", 'X', "trustAll",
                                     INFO_STOPDS_DESCRIPTION_TRUST_ALL.get());
      argParser.addArgument(trustAll);

      keyStoreFile = new StringArgument("keystorefile",
                                        OPTION_SHORT_KEYSTOREPATH,
                                        OPTION_LONG_KEYSTOREPATH,
                                        false, false, true,
                                        OPTION_VALUE_KEYSTOREPATH,
                                        null, null,
                                        INFO_STOPDS_DESCRIPTION_KSFILE.get());
      argParser.addArgument(keyStoreFile);

      keyStorePW = new StringArgument("keystorepw", OPTION_SHORT_KEYSTORE_PWD,
                                      OPTION_LONG_KEYSTORE_PWD,
                                      false, false, true,
                                      OPTION_VALUE_KEYSTORE_PWD,
                                      null, null,
                                      INFO_STOPDS_DESCRIPTION_KSPW.get());
      argParser.addArgument(keyStorePW);

      keyStorePWFile = new FileBasedArgument(
              "keystorepwfile",
              OPTION_SHORT_KEYSTORE_PWD_FILE,
              OPTION_LONG_KEYSTORE_PWD_FILE,
              false, false,
              OPTION_VALUE_KEYSTORE_PWD_FILE,
              null, null,
              INFO_STOPDS_DESCRIPTION_KSPWFILE.get());
      argParser.addArgument(keyStorePWFile);

      certNickname = new StringArgument(
              "certnickname", 'N', "certNickname",
              false, false, true, "{nickname}", null,
              null, INFO_DESCRIPTION_CERT_NICKNAME.get());
      argParser.addArgument(certNickname);

      trustStoreFile = new StringArgument("truststorefile",
                                          OPTION_SHORT_TRUSTSTOREPATH,
                                          OPTION_LONG_TRUSTSTOREPATH,
                                          false, false, true,
                                          OPTION_VALUE_TRUSTSTOREPATH,
                                          null, null,
                                          INFO_STOPDS_DESCRIPTION_TSFILE.get());
      argParser.addArgument(trustStoreFile);

      trustStorePW = new StringArgument(
              "truststorepw", 'T',
              OPTION_LONG_TRUSTSTORE_PWD,
              false, false,
              true, OPTION_VALUE_TRUSTSTORE_PWD, null,
              null, INFO_STOPDS_DESCRIPTION_TSPW.get());
      argParser.addArgument(trustStorePW);

      trustStorePWFile = new FileBasedArgument("truststorepwfile",
                                  OPTION_SHORT_TRUSTSTORE_PWD_FILE,
                                  OPTION_LONG_TRUSTSTORE_PWD_FILE,
                                  false, false,
                                  OPTION_VALUE_TRUSTSTORE_PWD_FILE, null, null,
                                  INFO_STOPDS_DESCRIPTION_TSPWFILE.get());
      argParser.addArgument(trustStorePWFile);

      quietMode = new BooleanArgument("quiet", OPTION_SHORT_QUIET,
                                      OPTION_LONG_QUIET,
                                      INFO_DESCRIPTION_QUIET.get());
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


    // Create the LDAP connection options object, which will be used to
    // customize the way that we connect to the server and specify a set of
    // basic defaults.
    LDAPConnectionOptions connectionOptions = new LDAPConnectionOptions();
    connectionOptions.setVersionNumber(3);


    // See if we should use SSL or StartTLS when establishing the connection.
    // If so, then make sure only one of them was specified.
    if (useSSL.isPresent())
    {
      if (useStartTLS.isPresent())
      {
        Message message = ERR_STOPDS_MUTUALLY_EXCLUSIVE_ARGUMENTS.get(
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
        Message message =
                ERR_STOPDS_CANNOT_INITIALIZE_SSL.get(sce.getMessage());
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
      connection.connectToHost(bindDN.getValue(), bindPW.getValue(),
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
      Message message = ERR_STOPDS_CANNOT_CONNECT.get(lce.getMessage());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR;
    }

    LDAPReader reader = connection.getLDAPReader();
    LDAPWriter writer = connection.getLDAPWriter();


    // Construct the add request to send to the server.
    String taskID = UUID.randomUUID().toString();
    ASN1OctetString entryDN =
         new ASN1OctetString(ATTR_TASK_ID + "=" + taskID + "," +
                             SCHEDULED_TASK_BASE_RDN + "," + DN_TASK_ROOT);

    ArrayList<RawAttribute> attributes = new ArrayList<RawAttribute>();

    ArrayList<ASN1OctetString> ocValues = new ArrayList<ASN1OctetString>(3);
    ocValues.add(new ASN1OctetString("top"));
    ocValues.add(new ASN1OctetString("ds-task"));
    ocValues.add(new ASN1OctetString("ds-task-shutdown"));
    attributes.add(new LDAPAttribute(ATTR_OBJECTCLASS, ocValues));

    ArrayList<ASN1OctetString> taskIDValues = new ArrayList<ASN1OctetString>(1);
    taskIDValues.add(new ASN1OctetString(taskID));
    attributes.add(new LDAPAttribute(ATTR_TASK_ID, taskIDValues));

    ArrayList<ASN1OctetString> classValues = new ArrayList<ASN1OctetString>(1);
    classValues.add(new ASN1OctetString(ShutdownTask.class.getName()));
    attributes.add(new LDAPAttribute(ATTR_TASK_CLASS, classValues));

    if (restart.isPresent())
    {
      ArrayList<ASN1OctetString> restartValues =
           new ArrayList<ASN1OctetString>(1);
      restartValues.add(new ASN1OctetString("true"));
      attributes.add(new LDAPAttribute(ATTR_RESTART_SERVER, restartValues));
    }

    if (stopReason.isPresent())
    {
      ArrayList<ASN1OctetString> stopReasonValues =
           new ArrayList<ASN1OctetString>(1);
      stopReasonValues.add(new ASN1OctetString(stopReason.getValue()));
      attributes.add(new LDAPAttribute(ATTR_SHUTDOWN_MESSAGE,
                                       stopReasonValues));
    }

    if (stopTime != null)
    {
      ArrayList<ASN1OctetString> stopTimeValues =
           new ArrayList<ASN1OctetString>(1);

      SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_GMT_TIME);
      dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      stopTimeValues.add(new ASN1OctetString(dateFormat.format(stopTime)));
      attributes.add(new LDAPAttribute(ATTR_TASK_SCHEDULED_START_TIME,
                                       stopTimeValues));
    }

    ArrayList<LDAPControl> controls = new ArrayList<LDAPControl>();
    if (proxyAuthzID.isPresent())
    {
      Control c = new ProxiedAuthV2Control(
                           new ASN1OctetString(proxyAuthzID.getValue()));
      controls.add(new LDAPControl(c));
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

