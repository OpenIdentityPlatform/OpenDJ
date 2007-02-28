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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.tools;



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
import org.opends.server.core.LockFileManager;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.ldap.AddRequestProtocolOp;
import org.opends.server.protocols.ldap.AddResponseProtocolOp;
import org.opends.server.protocols.ldap.ExtendedResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPConstants;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.tasks.ShutdownTask;
import org.opends.server.types.Control;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides a tool that can send a request to the Directory Server
 * that will cause it to shut down.
 */
public class StopDS
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME = "org.opends.server.tools.StopDS";



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
      System.exit(result);
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
    String toolDescription = getMessage(MSGID_STOPDS_TOOL_DESCRIPTION);
    ArgumentParser    argParser = new ArgumentParser(CLASS_NAME,
                                                     toolDescription, false);
    BooleanArgument   checkStoppability;
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
      host = new StringArgument("host", 'h', "host", false, false, true,
                                "{host}", "127.0.0.1", null,
                                MSGID_STOPDS_DESCRIPTION_HOST);
      argParser.addArgument(host);

      port = new IntegerArgument("port", 'p', "port", false, false, true,
                                 "{port}", 389, null, true, 1, true, 65535,
                                 MSGID_STOPDS_DESCRIPTION_PORT);
      argParser.addArgument(port);

      useSSL = new BooleanArgument("usessl", 'Z', "useSSL",
                                   MSGID_STOPDS_DESCRIPTION_USESSL);
      argParser.addArgument(useSSL);

      useStartTLS = new BooleanArgument("usestarttls", 'q', "useStartTLS",
                                        MSGID_STOPDS_DESCRIPTION_USESTARTTLS);
      argParser.addArgument(useStartTLS);

      bindDN = new StringArgument("binddn", 'D', "bindDN", false, false, true,
                                  "{bindDN}", null, null,
                                  MSGID_STOPDS_DESCRIPTION_BINDDN);
      argParser.addArgument(bindDN);

      bindPW = new StringArgument("bindpw", 'w', "bindPassword", false, false,
                                  true, "{bindPassword}", null, null,
                                  MSGID_STOPDS_DESCRIPTION_BINDPW);
      argParser.addArgument(bindPW);

      bindPWFile = new FileBasedArgument("bindpwfile", 'j', "bindPasswordFile",
                                         false, false, "{bindPasswordFile}",
                                         null, null,
                                         MSGID_STOPDS_DESCRIPTION_BINDPWFILE);
      argParser.addArgument(bindPWFile);

      saslOption = new StringArgument("sasloption", 'o', "saslOption", false,
                                      true, true, "{saslOption}", null, null,
                                      MSGID_STOPDS_DESCRIPTION_SASLOPTIONS);
      argParser.addArgument(saslOption);

      proxyAuthzID = new StringArgument("proxyauthzid", 'Y', "proxyAs", false,
                                        false, true, "{proxyAuthZID}", null,
                                        null,
                                        MSGID_STOPDS_DESCRIPTION_PROXYAUTHZID);
      argParser.addArgument(proxyAuthzID);

      stopReason = new StringArgument("stopreason", 'r', "stopReason", false,
                                      false, true, "{stopReason}", null, null,
                                      MSGID_STOPDS_DESCRIPTION_STOP_REASON);
      argParser.addArgument(stopReason);

      checkStoppability = new BooleanArgument("checkstoppability", null,
              "checkStoppability",
              MSGID_STOPDS_CHECK_STOPPABILITY);
      checkStoppability.setHidden(true);
      argParser.addArgument(checkStoppability);

      restart = new BooleanArgument("restart", 'R', "restart",
                                    MSGID_STOPDS_DESCRIPTION_RESTART);
      argParser.addArgument(restart);

      stopTimeStr = new StringArgument("stoptime", 't', "stopTime", false,
                                       false, true, "{stopTime}", null, null,
                                       MSGID_STOPDS_DESCRIPTION_STOP_TIME);
      argParser.addArgument(stopTimeStr);

      trustAll = new BooleanArgument("trustall", 'X', "trustAll",
                                     MSGID_STOPDS_DESCRIPTION_TRUST_ALL);
      argParser.addArgument(trustAll);

      keyStoreFile = new StringArgument("keystorefile", 'K', "keyStoreFile",
                                        false, false, true, "{keyStoreFile}",
                                        null, null,
                                        MSGID_STOPDS_DESCRIPTION_KSFILE);
      argParser.addArgument(keyStoreFile);

      keyStorePW = new StringArgument("keystorepw", 'W', "keyStorePassword",
                                      false, false, true, "{keyStorePassword}",
                                      null, null,
                                      MSGID_STOPDS_DESCRIPTION_KSPW);
      argParser.addArgument(keyStorePW);

      keyStorePWFile = new FileBasedArgument("keystorepwfile", 'u',
                                             "keyStorePasswordFile", false,
                                             false, "{keyStorePasswordFile}",
                                             null, null,
                                             MSGID_STOPDS_DESCRIPTION_KSPWFILE);
      argParser.addArgument(keyStorePWFile);

      certNickname = new StringArgument("certnickname", 'N', "certNickname",
                                        false, false, true, "{nickname}", null,
                                        null, MSGID_DESCRIPTION_CERT_NICKNAME);
      argParser.addArgument(certNickname);

      trustStoreFile = new StringArgument("truststorefile", 'P',
                                          "trustStoreFile", false, false, true,
                                          "{trustStoreFile}", null, null,
                                          MSGID_STOPDS_DESCRIPTION_TSFILE);
      argParser.addArgument(trustStoreFile);

      trustStorePW = new StringArgument("truststorepw", 'T',
                                        "trustStorePassword", false, false,
                                        true, "{trustStorePassword}", null,
                                        null, MSGID_STOPDS_DESCRIPTION_TSPW);
      argParser.addArgument(trustStorePW);

      trustStorePWFile = new FileBasedArgument("truststorepwfile", 'U',
                                  "trustStorePasswordFile", false, false,
                                  "{trustStorePasswordFile}", null, null,
                                  MSGID_STOPDS_DESCRIPTION_TSPWFILE);
      argParser.addArgument(trustStorePWFile);

      showUsage = new BooleanArgument("showusage", 'H', "help",
                                      MSGID_STOPDS_DESCRIPTION_SHOWUSAGE);
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage, out);
    }
    catch (ArgumentException ae)
    {
      int    msgID   = MSGID_STOPDS_CANNOT_INITIALIZE_ARGS;
      String message = getMessage(msgID, ae.getMessage());

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
      int    msgID   = MSGID_STOPDS_ERROR_PARSING_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
    }


    // If we should just display usage information, then exit because it will
    // have already been done.
    if (showUsage.isPresent())
    {
      return LDAPResultCode.SUCCESS;
    }

    if (checkStoppability.isPresent())
    {
      // This option should only be used if we want to check if the local
      // server is running or not. If the server is running result code is 98.
      // If the server is stopped the return code is 99.
      String lockFile = LockFileManager.getServerLockFileName();
      try
        {
          StringBuilder failureReason = new StringBuilder();
          if (LockFileManager.acquireExclusiveLock(lockFile, failureReason))
          {
            // The server is not running: write a message informing of that
            // in the standard out (this is not an error message).
            int    msgID   = MSGID_STOPDS_SERVER_ALREADY_STOPPED;
            String message = getMessage(msgID, null, null);
            System.out.println(message);
            LockFileManager.releaseLock(lockFile, failureReason);
            System.exit(99);
          }
          else
          {
            // Display a message informing that we are going to the server.
            int    msgID   = MSGID_STOPDS_GOING_TO_STOP;
            String message = getMessage(msgID, null, null);
            System.out.println(message);
            // The server is running.
            System.exit(98);
          }
        }
        catch (Exception e)
        {
          // Display a message informing that we are going to the server.
          int    msgID   = MSGID_STOPDS_GOING_TO_STOP;
          String message = getMessage(msgID, null, null);
          System.out.println(message);
          // Assume that if we cannot acquire the lock file the server is
          // running.
          System.exit(98);
        }
      }

    // If both a bind password and bind password file were provided, then return
    // an error.
    if (bindPW.isPresent() && bindPWFile.isPresent())
    {
      int    msgID   = MSGID_STOPDS_MUTUALLY_EXCLUSIVE_ARGUMENTS;
      String message = getMessage(msgID, bindPW.getLongIdentifier(),
                                  bindPWFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
    }


    // If both a key store password and key store password file were provided,
    // then return an error.
    if (keyStorePW.isPresent() && keyStorePWFile.isPresent())
    {
      int    msgID   = MSGID_STOPDS_MUTUALLY_EXCLUSIVE_ARGUMENTS;
      String message = getMessage(msgID, keyStorePW.getLongIdentifier(),
                                  keyStorePWFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
    }


    // If both a trust store password and trust store password file were
    // provided, then return an error.
    if (trustStorePW.isPresent() && trustStorePWFile.isPresent())
    {
      int    msgID   = MSGID_STOPDS_MUTUALLY_EXCLUSIVE_ARGUMENTS;
      String message = getMessage(msgID, trustStorePW.getLongIdentifier(),
                                  trustStorePWFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
    }


    // Make sure that we can decode the stop time string if one was provided.
    Date stopTime = new Date();
    if (stopTimeStr.isPresent())
    {
      String timeStr = stopTimeStr.getValue();
      if (timeStr.endsWith("Z"))
      {
        SimpleDateFormat dateFormat =
            new SimpleDateFormat(DATE_FORMAT_GENERALIZED_TIME);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        dateFormat.setLenient(true);

        try
        {
          stopTime = dateFormat.parse(timeStr);
        }
        catch (Exception e)
        {
          int    msgID   = MSGID_STOPDS_CANNOT_DECODE_STOP_TIME;
          String message = getMessage(msgID);
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
        }
      }
      else
      {
        SimpleDateFormat dateFormat =
            new SimpleDateFormat(DATE_FORMAT_COMPACT_LOCAL_TIME);
        dateFormat.setLenient(true);

        try
        {
          stopTime = dateFormat.parse(timeStr);
        }
        catch (Exception e)
        {
          int    msgID   = MSGID_STOPDS_CANNOT_DECODE_STOP_TIME;
          String message = getMessage(msgID);
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


    // See if we should use SSL or StartTLS when establishing the connection.
    // If so, then make sure only one of them was specified.
    if (useSSL.isPresent())
    {
      if (useStartTLS.isPresent())
      {
        int    msgID   = MSGID_STOPDS_MUTUALLY_EXCLUSIVE_ARGUMENTS;
        String message = getMessage(msgID, useSSL.getLongIdentifier(),
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
        int    msgID   = MSGID_STOPDS_CANNOT_INITIALIZE_SSL;
        String message = getMessage(msgID, sce.getMessage());
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
          int    msgID   = MSGID_STOPDS_CANNOT_PARSE_SASL_OPTION;
          String message = getMessage(msgID, s);
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
        int    msgID   = MSGID_STOPDS_NO_SASL_MECHANISM;
        String message = getMessage(msgID);
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
      int    msgID   = MSGID_STOPDS_CANNOT_DETERMINE_PORT;
      String message = getMessage(msgID, port.getLongIdentifier(),
                                  ae.getMessage());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
    }
    catch (LDAPConnectionException lce)
    {
      int    msgID   = MSGID_STOPDS_CANNOT_CONNECT;
      String message = getMessage(msgID, lce.getMessage());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR;
    }

    ASN1Reader reader = connection.getASN1Reader();
    ASN1Writer writer = connection.getASN1Writer();


    // Construct the add request to send to the server.
    String taskID = UUID.randomUUID().toString();
    ASN1OctetString entryDN =
         new ASN1OctetString(ATTR_TASK_ID + "=" + taskID + "," +
                             SCHEDULED_TASK_BASE_RDN + "," + DN_TASK_ROOT);

    ArrayList<LDAPAttribute> attributes = new ArrayList<LDAPAttribute>();

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

      SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_UTC_TIME);
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
      writer.writeElement(requestMessage.encode());

      ASN1Element responseElement = reader.readElement();
      if (responseElement == null)
      {
        int    msgID   = MSGID_STOPDS_UNEXPECTED_CONNECTION_CLOSURE;
        String message = getMessage(msgID);
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return LDAPResultCode.CLIENT_SIDE_SERVER_DOWN;
      }

      responseMessage = LDAPMessage.decode(responseElement.decodeAsSequence());
    }
    catch (IOException ioe)
    {
      int    msgID   = MSGID_STOPDS_IO_ERROR;
      String message = getMessage(msgID, String.valueOf(ioe));
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_SERVER_DOWN;
    }
    catch (ASN1Exception ae)
    {
      int    msgID   = MSGID_STOPDS_DECODE_ERROR;
      String message = getMessage(msgID, ae.getMessage());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_DECODING_ERROR;
    }
    catch (LDAPException le)
    {
      int    msgID   = MSGID_STOPDS_DECODE_ERROR;
      String message = getMessage(msgID, le.getMessage());
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
          String message = extendedResponse.getErrorMessage();
          if (message != null)
          {
            err.println(wrapText(message, MAX_LINE_WIDTH));
          }

          return extendedResponse.getResultCode();
        }
      }


      int    msgID   = MSGID_STOPDS_INVALID_RESPONSE_TYPE;
      String message = getMessage(msgID, responseMessage.getProtocolOpName());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR;
    }


    AddResponseProtocolOp addResponse =
         responseMessage.getAddResponseProtocolOp();
    String errorMessage = addResponse.getErrorMessage();
    if (errorMessage != null)
    {
      err.println(wrapText(errorMessage, MAX_LINE_WIDTH));
    }

    return addResponse.getResultCode();
  }
}

