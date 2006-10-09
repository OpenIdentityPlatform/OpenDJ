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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.tools;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.ldap.DeleteRequestProtocolOp;
import org.opends.server.protocols.ldap.DeleteResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.ProtocolOp;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.PasswordReader;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;


/**
 * This class provides a tool that can be used to issue delete requests to the
 * Directory Server.
 */
public class LDAPDelete
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME = "org.opends.server.tools.LDAPDelete";



  // The message ID counter to use for requests.
  private AtomicInteger nextMessageID;

  // The print stream to use for standard error.
  private PrintStream err;

  // The print stream to use for standard output.
  private PrintStream out;



  /**
   * Constructor for the LDAPDelete object.
   *
   * @param  nextMessageID  The next message ID to use for requests.
   * @param  out            The print stream to use for standard output.
   * @param  err            The print stream to use for standard error.
   */
  public LDAPDelete(AtomicInteger nextMessageID, PrintStream out,
                    PrintStream err)
  {
    this.nextMessageID = nextMessageID;
    this.out           = out;
    this.err           = err;
  }

  /**
   * Execute the delete request on the specified list of DNs.
   *
   * @param connection        The connection to use to execute the request.
   * @param lines             The list of DNs to delete.
   * @param deleteOptions     The constraints to use for this request.
   *
   * @throws  IOException  If a problem occurs while attempting to communicate
   *                       with the Directory Server.
   *
   * @throws  LDAPException  If the Directory Server returns an error response.
   */
  public void readAndExecute(LDAPConnection connection,
                             ArrayList<String> lines,
                             LDAPDeleteOptions deleteOptions)
    throws IOException, LDAPException
  {
    for(String line : lines)
    {
      executeDelete(connection, line, deleteOptions);
    }
  }

  /**
   * Read the specified DNs from the given reader
   * (file or stdin) and execute the given delete request.
   *
   * @param connection        The connection to use to execute the request.
   * @param reader            The reader to read the list of DNs from.
   * @param deleteOptions     The constraints to use for this request.
   *
   * @throws  IOException  If a problem occurs while attempting to communicate
   *                       with the Directory Server.
   *
   * @throws  LDAPException  If the Directory Server returns an error response.
   */
  public void readAndExecute(LDAPConnection connection, Reader reader,
                             LDAPDeleteOptions deleteOptions)
         throws IOException, LDAPException
  {
    BufferedReader in = new BufferedReader(reader);
    String line = null;

    while ((line = in.readLine()) != null)
    {
      executeDelete(connection, line, deleteOptions);
    }
    in.close();
  }


  /**
   * Execute the delete request for the specified DN.
   *
   * @param connection        The connection to use to execute the request.
   * @param line           The DN to delete.
   * @param deleteOptions  The list of constraints for this request.
   *
   * @throws  IOException  If a problem occurs while attempting to communicate
   *                       with the Directory Server.
   *
   * @throws  LDAPException  If the Directory Server returns an error response.
   */
  private void executeDelete(LDAPConnection connection, String line,
                             LDAPDeleteOptions deleteOptions)
          throws IOException, LDAPException
  {
    ArrayList<LDAPControl> controls = deleteOptions.getControls();
    ProtocolOp protocolOp = null;
    ASN1OctetString asn1OctetStr = new ASN1OctetString(line);

    protocolOp = new DeleteRequestProtocolOp(asn1OctetStr);
    int msgID = MSGID_PROCESSING_OPERATION;
    out.println(getMessage(msgID, "DELETE", asn1OctetStr));
    if(!deleteOptions.showOperations())
    {
      LDAPMessage message = new LDAPMessage(nextMessageID.getAndIncrement(),
                                            protocolOp, controls);
      LDAPMessage responseMessage = null;
      try
      {
        int numBytes =
             connection.getASN1Writer().writeElement(message.encode());
        ASN1Element element = connection.getASN1Reader().readElement();
        responseMessage = LDAPMessage.decode(
                               ASN1Sequence.decodeAsSequence(element));
      } catch(ASN1Exception ae)
      {
        assert debugException(CLASS_NAME, "executeDelete", ae);
        if(!deleteOptions.continueOnError())
        {
          throw new IOException(ae.getMessage());
        } else
        {
          msgID = MSGID_OPERATION_FAILED;
          String msg = getMessage(msgID, "DELETE", line, ae.getMessage());
          err.println(msg);
          return;
        }
      }

      DeleteResponseProtocolOp op =
           responseMessage.getDeleteResponseProtocolOp();
      int resultCode = op.getResultCode();
      String errorMessage = op.getErrorMessage();
      if(resultCode != SUCCESS && resultCode != REFERRAL &&
         !deleteOptions.continueOnError())
      {
        msgID = MSGID_OPERATION_FAILED;
        String msg = getMessage(msgID, "DELETE", line, errorMessage);
        throw new LDAPException(resultCode, msgID, msg);
      } else
      {
        if(resultCode != SUCCESS && resultCode != REFERRAL)
        {
          msgID = MSGID_OPERATION_FAILED;
          String msg = getMessage(msgID, "DELETE", line, errorMessage);
          err.println(msg);
        } else
        {
          msgID = MSGID_OPERATION_SUCCESSFUL;
          String msg = getMessage(msgID, "DELETE", line);
          out.println(msg);
        }
      }
    }
  }

  /**
   * The main method for LDAPDelete tool.
   *
   * @param  args  The command-line arguments provided to this program.
   */

  public static void main(String[] args)
  {
    int retCode = mainDelete(args, true, System.out, System.err);

    if(retCode != 0)
    {
      System.exit(retCode);
    }
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the ldapdelete tool.
   *
   * @param  args  The command-line arguments provided to this program.
   *
   * @return The error code.
   */

  public static int mainDelete(String[] args)
  {
    return mainDelete(args, true, System.out, System.err);
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the ldapdelete tool.
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
   * @return The error code.
   */

  public static int mainDelete(String[] args, boolean initializeServer,
                               OutputStream outStream, OutputStream errStream)
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


    LDAPConnectionOptions connectionOptions = new LDAPConnectionOptions();
    LDAPDeleteOptions deleteOptions = new LDAPDeleteOptions();
    LDAPConnection connection = null;

    BooleanArgument trustAll = null;
    BooleanArgument noop = null;
    StringArgument bindDN = null;
    StringArgument bindPassword = null;
    FileBasedArgument bindPasswordFile = null;
    StringArgument encodingStr = null;
    StringArgument keyStorePath = null;
    StringArgument keyStorePassword = null;
    StringArgument trustStorePath = null;
    StringArgument trustStorePassword = null;
    StringArgument hostName = null;
    IntegerArgument port = null;
    IntegerArgument version = null;
    BooleanArgument showUsage = null;
    StringArgument controlStr = null;
    BooleanArgument verbose = null;
    BooleanArgument continueOnError = null;
    BooleanArgument useSSL = null;
    BooleanArgument startTLS = null;
    BooleanArgument saslExternal = null;
    BooleanArgument deleteSubtree = null;
    StringArgument filename = null;
    StringArgument saslOptions = null;

    Reader rdr = null;
    ArrayList<String> dnStrings = new ArrayList<String> ();

    // Create the command-line argument parser for use with this program.
    ArgumentParser argParser = new ArgumentParser(CLASS_NAME, false, true,
        0, 1, "\"DN\"");
    try
    {
      trustAll = new BooleanArgument("trustAll", 'X', "trustAll",
            MSGID_DESCRIPTION_TRUSTALL);
      argParser.addArgument(trustAll);
      bindDN = new StringArgument("bindDN", 'D', "bindDN", false, false,
                                  true, "{bindDN}", null, null,
                                  MSGID_DESCRIPTION_BINDDN);
      argParser.addArgument(bindDN);
      bindPassword = new StringArgument("bindPassword", 'w', "bindPassword",
                                        false, false, true, "{bindPassword}",
                                        null, null,
                                        MSGID_DESCRIPTION_BINDPASSWORD);
      argParser.addArgument(bindPassword);
      bindPasswordFile = new FileBasedArgument("bindPasswordFile", 'j',
                                  "bindPasswordFile", false, false,
                                  "{bindPasswordFilename}", null, null,
                                  MSGID_DESCRIPTION_BINDPASSWORDFILE);
      argParser.addArgument(bindPasswordFile);
      encodingStr = new StringArgument("encoding", 'i', "encoding", false,
                                       false, true, "{encoding}", null,  null,
                                       MSGID_DESCRIPTION_ENCODING);
      argParser.addArgument(encodingStr);
      keyStorePath = new StringArgument("keyStorePath", 'K', "keyStorePath",
                                        false, false, true, "{keyStorePath}",
                                        null, null,
                                        MSGID_DESCRIPTION_KEYSTOREPATH);
      argParser.addArgument(keyStorePath);
      trustStorePath = new StringArgument("trustStorePath", 'P',
                                          "trustStorePath", false, false, true,
                                          "{trustStorePath}", null, null,
                                          MSGID_DESCRIPTION_TRUSTSTOREPATH);
      argParser.addArgument(trustStorePath);
      keyStorePassword = new StringArgument("keyStorePassword", 'W',
                                            "keyStorePassword", false, false,
                                            true, "{keyStorePassword}", null,
                                            null,
                                            MSGID_DESCRIPTION_KEYSTOREPASSWORD);
      argParser.addArgument(keyStorePassword);
      hostName = new StringArgument("host", 'h', "host", false, false, true,
                                    "{host}", "localhost", null,
                                    MSGID_DESCRIPTION_HOST);
      argParser.addArgument(hostName);
      port = new IntegerArgument("port", 'p', "port", false, false, true,
                                 "{port}", 389, null, MSGID_DESCRIPTION_PORT);
      argParser.addArgument(port);
      version = new IntegerArgument("version", 'V', "version", false, false,
                                    true, "{version}", 3, null,
                                    MSGID_DESCRIPTION_VERSION);
      argParser.addArgument(version);
      filename = new StringArgument("filename", 'f', "filename", false, false,
                                    true, "{filename}", null, null,
                                    MSGID_DELETE_DESCRIPTION_FILENAME);
      argParser.addArgument(filename);
      showUsage = new BooleanArgument("showUsage", 'H', "help",
                                      MSGID_DESCRIPTION_SHOWUSAGE);
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage, out);
      controlStr = new StringArgument("controls", 'J', "controls", false, false,
           true, "{controloid[:criticality[:value|::b64value|:<fileurl]]}",
           null, null, MSGID_DESCRIPTION_CONTROLS);
      argParser.addArgument(controlStr);
      verbose = new BooleanArgument("verbose", 'v', "verbose",
                                    MSGID_DESCRIPTION_VERBOSE);
      argParser.addArgument(verbose);
      continueOnError = new BooleanArgument("continueOnError", 'c',
                                 "continueOnError",
                                 MSGID_DESCRIPTION_CONTINUE_ON_ERROR);
      argParser.addArgument(continueOnError);
      useSSL = new BooleanArgument("useSSL", 'Z', "useSSL",
                                    MSGID_DESCRIPTION_USE_SSL);
      argParser.addArgument(useSSL);
      startTLS = new BooleanArgument("startTLS", 'q', "startTLS",
                                    MSGID_DESCRIPTION_START_TLS);
      argParser.addArgument(startTLS);
      saslExternal = new BooleanArgument("useSASLExternal", 'r',
                                         "useSASLExternal",
                                         MSGID_DESCRIPTION_USE_SASL_EXTERNAL);
      argParser.addArgument(saslExternal);
      deleteSubtree = new BooleanArgument("deleteSubtree", 'x', "deleteSubtree",
                               MSGID_DELETE_DESCRIPTION_DELETE_SUBTREE);
      argParser.addArgument(deleteSubtree);

      saslOptions = new StringArgument("saslOptions", 'o', "saslOptions",
                                       false, true, true, "{name=value}", null,
                                       null, MSGID_DESCRIPTION_SASL_PROPERTIES);
      argParser.addArgument(saslOptions);
      noop = new BooleanArgument("no-op", 'n', "noop", MSGID_DESCRIPTION_NOOP);
      argParser.addArgument(noop);
    } catch (ArgumentException ae)
    {
      int    msgID   = MSGID_ENCPW_CANNOT_INITIALIZE_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      err.println(message);
      return 1;
    }

    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      int    msgID   = MSGID_ENCPW_ERROR_PARSING_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      err.println(message);
      err.println(argParser.getUsage());
      return 1;
    }

    // If we should just display usage information, then it has already been
    // done so just exit.
    if (showUsage.isPresent())
    {
      return 0;
    }

    if(bindPassword.isPresent() && bindPasswordFile.isPresent())
    {
      err.println("ERROR: Both -w and -j flags specified. " +
                  "Please specify one.");
      return 1;
    }

    String hostNameValue = hostName.getValue();
    int portNumber = 389;
    try
    {
      portNumber = port.getIntValue();
    } catch(ArgumentException ae)
    {
      assert debugException(CLASS_NAME, "main", ae);
      err.println(ae.getMessage());
      return 1;
    }

    try
    {
      int versionNumber = version.getIntValue();
      if(versionNumber != 2 && versionNumber != 3)
      {
        int msgID = MSGID_DESCRIPTION_INVALID_VERSION;
        err.println(getMessage(msgID, versionNumber));
        return 1;
      }
      connectionOptions.setVersionNumber(versionNumber);
    } catch(ArgumentException ae)
    {
      assert debugException(CLASS_NAME, "main", ae);
      err.println(ae.getMessage());
      return 1;
    }

    String bindDNValue = bindDN.getValue();
    String fileNameValue = filename.getValue();
    String bindPasswordValue = bindPassword.getValue();
    if(bindPasswordValue != null && bindPasswordValue.equals("-"))
    {
      // read the password from the stdin.
      try
      {
        out.print(getMessage(MSGID_LDAPAUTH_PASSWORD_PROMPT, bindDNValue));
        char[] pwChars = PasswordReader.readPassword();
        bindPasswordValue = new String(pwChars);
      } catch(Exception ex)
      {
        assert debugException(CLASS_NAME, "main", ex);
        err.println(ex.getMessage());
        return 1;
      }
    } else if(bindPasswordValue == null)
    {
      // Read from file if it exists.
      bindPasswordValue = bindPasswordFile.getValue();
    }

    String keyStorePathValue = keyStorePath.getValue();
    String keyStorePasswordValue = keyStorePassword.getValue();
    String trustStorePathValue = trustStorePath.getValue();
    String trustStorePasswordValue = null;

    deleteOptions.setShowOperations(noop.isPresent());
    deleteOptions.setVerbose(verbose.isPresent());
    deleteOptions.setContinueOnError(continueOnError.isPresent());
    deleteOptions.setEncoding(encodingStr.getValue());
    deleteOptions.setDeleteSubtree(deleteSubtree.isPresent());
    if(controlStr.hasValue())
    {
      String ctrlString = controlStr.getValue();
      LDAPControl ctrl = LDAPToolUtils.getControl(ctrlString, err);
      if(ctrl == null)
      {
        err.println("Invalid control specified:" + ctrlString);
        err.println(argParser.getUsage());
        return 1;
      }
      deleteOptions.getControls().add(ctrl);
    }
    if(deleteOptions.getDeleteSubtree())
    {
      LDAPControl control = new LDAPControl(OID_SUBTREE_DELETE_CONTROL);
      deleteOptions.getControls().add(control);
    }

    ArrayList<String> trailingArgs = argParser.getTrailingArguments();
    for(String s : trailingArgs)
    {
      dnStrings.add(s);
    }

    // Set the connection options.
    // Parse the SASL properties.
    connectionOptions.setSASLExternal(saslExternal.isPresent());
    if(saslOptions.isPresent())
    {
      LinkedList<String> values = saslOptions.getValues();
      for(String saslOption : values)
      {
        if(saslOption.startsWith("mech="))
        {
          boolean val = connectionOptions.setSASLMechanism(saslOption);
          if(val == false)
          {
            return 1;
          }
        } else
        {
          boolean val = connectionOptions.addSASLProperty(saslOption);
          if(val == false)
          {
            return 1;
          }
        }
      }
    }
    connectionOptions.setUseSSL(useSSL.isPresent());
    connectionOptions.setStartTLS(startTLS.isPresent());

    if(connectionOptions.useSASLExternal())
    {
      if(!connectionOptions.useSSL() && !connectionOptions.useStartTLS())
      {
        err.println("SASL External requires either SSL or StartTLS " +
                    "options to be requested.");
        return 1;
      }
      if(keyStorePathValue == null)
      {
        err.println("SASL External requires a path to the SSL " +
                    "client certificate keystore.");
        return 1;
      }
    }

    try
    {
      if (initializeServer)
      {
        // Bootstrap and initialize directory data structures.
        DirectoryServer.bootstrapClient();
      }

      // Connect to the specified host with the supplied userDN and password.
      SSLConnectionFactory sslConnectionFactory = null;
      if(connectionOptions.useSSL() || connectionOptions.useStartTLS())
      {
        sslConnectionFactory = new SSLConnectionFactory();
        sslConnectionFactory.init(trustAll.isPresent(), keyStorePathValue,
                                  keyStorePasswordValue, trustStorePathValue,
                                  trustStorePasswordValue);
        connectionOptions.setSSLConnectionFactory(sslConnectionFactory);
      }

      AtomicInteger nextMessageID = new AtomicInteger(1);
      connection = new LDAPConnection(hostNameValue, portNumber,
                                      connectionOptions, out, err);
      connection.connectToHost(bindDNValue, bindPasswordValue, nextMessageID);

      LDAPDelete ldapDelete = new LDAPDelete(nextMessageID, out, err);
      if(fileNameValue == null && dnStrings.isEmpty())
      {
        // Read from stdin.
        rdr = new InputStreamReader(System.in);
      } else if(fileNameValue != null)
      {
        rdr = new FileReader(fileNameValue);
      }

      if(rdr != null)
      {
        ldapDelete.readAndExecute(connection, rdr, deleteOptions);
      } else
      {
        ldapDelete.readAndExecute(connection, dnStrings, deleteOptions);
      }
    } catch(LDAPException le)
    {
      assert debugException(CLASS_NAME, "main", le);
      err.println(le.getMessage());
      int code = le.getResultCode();
      return code;
    } catch(LDAPConnectionException lce)
    {
      assert debugException(CLASS_NAME, "main", lce);
      err.println(lce.getMessage());
      int code = lce.getErrorCode();
      return code;
    } catch(Exception e)
    {
      assert debugException(CLASS_NAME, "main", e);
      err.println(e.getMessage());
      return 1;
    } finally
    {
      if(connection != null)
      {
        connection.close();
      }
    }
    return 0;
  }

}

