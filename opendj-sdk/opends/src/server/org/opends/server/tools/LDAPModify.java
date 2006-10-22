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

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;

import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.ldap.AddRequestProtocolOp;
import org.opends.server.protocols.ldap.AddResponseProtocolOp;
import org.opends.server.protocols.ldap.DeleteRequestProtocolOp;
import org.opends.server.protocols.ldap.DeleteResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.protocols.ldap.ModifyRequestProtocolOp;
import org.opends.server.protocols.ldap.ModifyResponseProtocolOp;
import org.opends.server.protocols.ldap.ModifyDNRequestProtocolOp;
import org.opends.server.protocols.ldap.ModifyDNResponseProtocolOp;
import org.opends.server.protocols.ldap.SearchResultEntryProtocolOp;
import org.opends.server.protocols.ldap.ProtocolOp;
import org.opends.server.types.Attribute;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.AddChangeRecordEntry;
import org.opends.server.util.ChangeRecordEntry;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.ModifyChangeRecordEntry;
import org.opends.server.util.ModifyDNChangeRecordEntry;
import org.opends.server.util.PasswordReader;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class provides a tool that can be used to issue modify requests to the
 * Directory Server.
 */
public class LDAPModify
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME = "org.opends.server.tools.LDAPModify";

  // The message ID counter to use for requests.
  private AtomicInteger nextMessageID;

  // The print stream to use for standard error.
  private PrintStream err;

  // The print stream to use for standard output.
  private PrintStream out;

  // The LDIF file name.
  private String fileName = null;

  /**
   * Constructor for the LDAPModify object.
   *
   * @param  fileName       The name of the file containing the LDIF data to use
   *                        for the modifications.
   * @param  nextMessageID  The message ID counter to use for requests.
   * @param  out            The print stream to use for standard output.
   * @param  err            The print stream to use for standard error.
   */
  public LDAPModify(String fileName, AtomicInteger nextMessageID,
                    PrintStream out, PrintStream err)
  {
    if(fileName == null)
    {
      this.fileName = "Console";
    } else
    {
      this.fileName = fileName;
    }

    this.nextMessageID = nextMessageID;
    this.out           = out;
    this.err           = err;
  }


  /**
   * Read the specified change records from the given input stream
   * (file or stdin) and execute the given modify request.
   *
   * @param connection     The connection to use for this modify request.
   * @param is             The input stream to read the change records from.
   * @param modifyOptions  The constraints for the modify request.
   *
   * @throws  IOException  If a problem occurs while attempting to communicate
   *                       with the Directory Server.
   *
   * @throws  LDAPException  If the Directory Server returns an error response.
   */
  public void readAndExecute(LDAPConnection connection, InputStream is,
                             LDAPModifyOptions modifyOptions)
         throws IOException, LDAPException
  {
    ArrayList<LDAPControl> controls = modifyOptions.getControls();
    LDIFReader reader;

    // Create an LDIF import configuration to do this and then get the reader.

    try
    {
      LDIFImportConfig importConfig = new LDIFImportConfig(is);
      reader = new LDIFReader(importConfig);
    } catch (Exception e)
    {
      assert debugException(CLASS_NAME, "readAndExecute", e);
      int    msgID   = MSGID_LDIF_FILE_CANNOT_OPEN_FOR_READ;
      String message = getMessage(msgID, is, String.valueOf(e));
      throw new IOException(message);
    }

    while (true)
    {
      ChangeRecordEntry entry = null;

      try
      {
        entry = reader.readChangeRecord(modifyOptions.getDefaultAdd());
      } catch (LDIFException le)
      {
        assert debugException(CLASS_NAME, "readAndExecute", le);
        if(!modifyOptions.continueOnError())
        {
          try
          {
            reader.close();
          } catch (Exception e)
          {
            assert debugException(CLASS_NAME, "readAndExecute", e);
          }

          int    msgID   = MSGID_LDIF_FILE_INVALID_LDIF_ENTRY;
          String message = getMessage(msgID, le.getLineNumber(), fileName,
                                      String.valueOf(le));
          throw new IOException(message);
        } else
        {
          int    msgID   = MSGID_LDIF_FILE_INVALID_LDIF_ENTRY;
          String message = getMessage(msgID, le.getLineNumber(), fileName,
                                      String.valueOf(le));
          err.println(message);
          continue;
        }
      } catch (Exception e)
      {
        assert debugException(CLASS_NAME, "readAndExecute", e);

        if(!modifyOptions.continueOnError())
        {
          try
          {
            reader.close();
          }
          catch (Exception e2)
          {
            assert debugException(CLASS_NAME, "readAndExecute", e2);
          }

          int    msgID   = MSGID_LDIF_FILE_READ_ERROR;
          String message = getMessage(msgID, fileName, String.valueOf(e));
          throw new IOException(message);
        } else
        {
          int    msgID   = MSGID_LDIF_FILE_READ_ERROR;
          String message = getMessage(msgID, fileName, String.valueOf(e));
          err.println(message);
          continue;
        }
      }

      // If the entry is null, then we have reached the end of the config file.
      if(entry == null)
      {
        try
        {
          reader.close();
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "readAndExecute", e);
        }

        break;
      }

      ProtocolOp protocolOp = null;
      ASN1OctetString asn1OctetStr =
           new ASN1OctetString(entry.getDN().toString());

      String operationType = "";
      int msgID = 0;
      switch(entry.getChangeOperationType())
      {
        case ADD:
          operationType = "ADD";
          AddChangeRecordEntry addEntry = (AddChangeRecordEntry) entry;
          List<Attribute> attrs = addEntry.getAttributes();
          ArrayList<LDAPAttribute> attributes =
              new ArrayList<LDAPAttribute>(attrs.size());
          for(Attribute a : attrs)
          {
            attributes.add(new LDAPAttribute(a));
          }
          protocolOp = new AddRequestProtocolOp(asn1OctetStr, attributes);
          msgID = MSGID_PROCESSING_OPERATION;
          out.println(getMessage(msgID, operationType, asn1OctetStr));
          break;
        case DELETE:
          operationType = "DELETE";
          protocolOp = new DeleteRequestProtocolOp(asn1OctetStr);
          msgID = MSGID_PROCESSING_OPERATION;
          out.println(getMessage(msgID, operationType, asn1OctetStr));
          break;
        case MODIFY:
          operationType = "MODIFY";
          ModifyChangeRecordEntry modEntry = (ModifyChangeRecordEntry) entry;
          ArrayList<LDAPModification> mods =
            new ArrayList<LDAPModification>(modEntry.getModifications());
          protocolOp = new ModifyRequestProtocolOp(asn1OctetStr, mods);
          msgID = MSGID_PROCESSING_OPERATION;
          out.println(getMessage(msgID, operationType, asn1OctetStr));
          break;
        case MODIFY_DN:
          operationType = "MODIFY DN";
          ModifyDNChangeRecordEntry modDNEntry =
            (ModifyDNChangeRecordEntry) entry;
          if(modDNEntry.getNewSuperiorDN() != null)
          {
            protocolOp = new ModifyDNRequestProtocolOp(asn1OctetStr,
                 new ASN1OctetString(modDNEntry.getNewRDN().toString()),
                 modDNEntry.deleteOldRDN(),
                 new ASN1OctetString(
                          modDNEntry.getNewSuperiorDN().toString()));
          } else
          {
            protocolOp = new ModifyDNRequestProtocolOp(asn1OctetStr,
                 new ASN1OctetString(modDNEntry.getNewRDN().toString()),
                 modDNEntry.deleteOldRDN());
          }
          msgID = MSGID_PROCESSING_OPERATION;
          out.println(getMessage(msgID, operationType, asn1OctetStr));
          break;
        default:
          break;
      }

      if(!modifyOptions.showOperations())
      {
        LDAPMessage responseMessage = null;
        try
        {
          LDAPMessage message =
               new LDAPMessage(nextMessageID.getAndIncrement(), protocolOp,
                               controls);
          // int numBytes =
          connection.getASN1Writer().writeElement(message.encode());
          ASN1Element element = connection.getASN1Reader().readElement();
          responseMessage =
               LDAPMessage.decode(ASN1Sequence.decodeAsSequence(element));
        } catch(ASN1Exception ae)
        {
          assert debugException(CLASS_NAME, "readAndExecute", ae);
          msgID = MSGID_OPERATION_FAILED;
          err.println(getMessage(msgID, operationType, asn1OctetStr,
                                 ae.getMessage()));
          if(!modifyOptions.continueOnError())
          {
            throw new IOException(ae.getMessage());
          }
          return;
        }

        int resultCode = 0;
        String errorMessage = null;
        List<String> referralURLs = null;
        switch(entry.getChangeOperationType())
        {
          case ADD:
            AddResponseProtocolOp addOp =
              responseMessage.getAddResponseProtocolOp();
            resultCode = addOp.getResultCode();
            errorMessage = addOp.getErrorMessage();
            referralURLs = addOp.getReferralURLs();
            break;
          case DELETE:
            DeleteResponseProtocolOp delOp =
              responseMessage.getDeleteResponseProtocolOp();
            resultCode = delOp.getResultCode();
            errorMessage = delOp.getErrorMessage();
            referralURLs = delOp.getReferralURLs();
            break;
          case MODIFY:
            ModifyResponseProtocolOp modOp =
              responseMessage.getModifyResponseProtocolOp();
            resultCode = modOp.getResultCode();
            errorMessage = modOp.getErrorMessage();
            referralURLs = modOp.getReferralURLs();
            break;
          case MODIFY_DN:
            ModifyDNResponseProtocolOp modDNOp =
              responseMessage.getModifyDNResponseProtocolOp();
            resultCode = modDNOp.getResultCode();
            errorMessage = modDNOp.getErrorMessage();
            referralURLs = modDNOp.getReferralURLs();
            break;
          default:
            break;
        }

        if(resultCode != SUCCESS && resultCode != REFERRAL)
        {
          if(errorMessage == null)
          {
            errorMessage = "Result code:" + resultCode;
          }
          msgID = MSGID_OPERATION_FAILED;
          String msg = getMessage(msgID, operationType, asn1OctetStr,
                                  errorMessage);

          if(!modifyOptions.continueOnError())
          {
            throw new LDAPException(resultCode, msgID, msg);
          } else
          {
            err.println(msg);
          }
        } else
        {
          msgID = MSGID_OPERATION_SUCCESSFUL;
          String msg = getMessage(msgID, operationType, asn1OctetStr);
          out.println(msg);

          if (errorMessage != null)
          {
            out.println(errorMessage);
          }

          if (referralURLs != null)
          {
            out.println(referralURLs);
          }
        }


        for (LDAPControl c : responseMessage.getControls())
        {
          String oid = c.getOID();
          if (oid.equals(OID_LDAP_READENTRY_PREREAD))
          {
            ASN1OctetString controlValue = c.getValue();
            if (controlValue == null)
            {
              msgID = MSGID_LDAPMODIFY_PREREAD_NO_VALUE;
              err.println(getMessage(msgID));
              continue;
            }

            SearchResultEntryProtocolOp searchEntry;
            try
            {
              byte[] valueBytes = controlValue.value();
              ASN1Element valueElement = ASN1Element.decode(valueBytes);
              searchEntry =
                   SearchResultEntryProtocolOp.decodeSearchEntry(valueElement);
            }
            catch (ASN1Exception ae)
            {
              msgID = MSGID_LDAPMODIFY_PREREAD_CANNOT_DECODE_VALUE;
              err.println(getMessage(msgID, ae.getMessage()));
              continue;
            }
            catch (LDAPException le)
            {
              msgID = MSGID_LDAPMODIFY_PREREAD_CANNOT_DECODE_VALUE;
              err.println(getMessage(msgID, le.getMessage()));
              continue;
            }

            StringBuilder buffer = new StringBuilder();
            searchEntry.toLDIF(buffer, 78);
            out.println(getMessage(MSGID_LDAPMODIFY_PREREAD_ENTRY));
            out.println(buffer);
          }
          else if (oid.equals(OID_LDAP_READENTRY_POSTREAD))
          {
            ASN1OctetString controlValue = c.getValue();
            if (controlValue == null)
            {
              msgID = MSGID_LDAPMODIFY_POSTREAD_NO_VALUE;
              err.println(getMessage(msgID));
              continue;
            }

            SearchResultEntryProtocolOp searchEntry;
            try
            {
              byte[] valueBytes = controlValue.value();
              ASN1Element valueElement = ASN1Element.decode(valueBytes);
              searchEntry =
                   SearchResultEntryProtocolOp.decodeSearchEntry(valueElement);
            }
            catch (ASN1Exception ae)
            {
              msgID = MSGID_LDAPMODIFY_POSTREAD_CANNOT_DECODE_VALUE;
              err.println(getMessage(msgID, ae.getMessage()));
              continue;
            }
            catch (LDAPException le)
            {
              msgID = MSGID_LDAPMODIFY_POSTREAD_CANNOT_DECODE_VALUE;
              err.println(getMessage(msgID, le.getMessage()));
              continue;
            }

            StringBuilder buffer = new StringBuilder();
            searchEntry.toLDIF(buffer, 78);
            out.println(getMessage(MSGID_LDAPMODIFY_POSTREAD_ENTRY));
            out.println(buffer);
          }
        }
      }
    }

  }

  /**
   * The main method for LDAPModify tool.
   *
   * @param  args  The command-line arguments provided to this program.
   */

  public static void main(String[] args)
  {
    int retCode = mainModify(args, true, System.out, System.err);

    if(retCode != 0)
    {
      System.exit(retCode);
    }
  }


  /**
   * Parses the provided command-line arguments and uses that information to
   * run the ldapmodify tool.
   *
   * @param  args  The command-line arguments provided to this program.
   *
   * @return The error code.
   */

  public static int mainModify(String[] args)
  {
    return mainModify(args, true, System.out, System.err);
  }


  /**
   * Parses the provided command-line arguments and uses that information to
   * run the ldapmodify tool.
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

  public static int mainModify(String[] args, boolean initializeServer,
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
    LDAPModifyOptions modifyOptions = new LDAPModifyOptions();
    LDAPConnection connection = null;

    BooleanArgument trustAll = null;
    StringArgument assertionFilter = null;
    StringArgument bindDN = null;
    StringArgument bindPassword = null;
    FileBasedArgument bindPasswordFile = null;
    StringArgument proxyAuthzID = null;
    BooleanArgument reportAuthzID = null;
    StringArgument encodingStr = null;
    StringArgument keyStorePath = null;
    StringArgument keyStorePassword = null;
    StringArgument trustStorePath = null;
//    StringArgument trustStorePassword = null;
    StringArgument hostName = null;
    IntegerArgument port = null;
    BooleanArgument showUsage = null;
    StringArgument controlStr = null;
    BooleanArgument verbose = null;
    BooleanArgument continueOnError = null;
    BooleanArgument useSSL = null;
    BooleanArgument startTLS = null;
    BooleanArgument saslExternal = null;
    BooleanArgument defaultAdd = null;
    StringArgument filename = null;
    StringArgument saslOptions = null;
    StringArgument preReadAttributes = null;
    StringArgument postReadAttributes = null;
    IntegerArgument version = null;
    BooleanArgument noop = null;

    // Create the command-line argument parser for use with this program.
    ArgumentParser argParser = new ArgumentParser(CLASS_NAME, false);
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
                                  false, false,
                                  true, "{bindPassword}", null, null,
                                  MSGID_DESCRIPTION_BINDPASSWORD);
      argParser.addArgument(bindPassword);
      bindPasswordFile = new FileBasedArgument("bindPasswordFile", 'j',
                                  "bindPasswordFile", false, false,
                                  "{bindPasswordFilename}", null, null,
                                  MSGID_DESCRIPTION_BINDPASSWORDFILE);
      argParser.addArgument(bindPasswordFile);
      proxyAuthzID = new StringArgument("proxy_authzid", 'Y', "proxyAs", false,
                                        false, true, "{authzID}", null, null,
                                        MSGID_DESCRIPTION_PROXY_AUTHZID);
      argParser.addArgument(proxyAuthzID);
      reportAuthzID = new BooleanArgument("reportauthzid", 'E',
                                          "reportAuthzID",
                                          MSGID_DESCRIPTION_REPORT_AUTHZID);
      argParser.addArgument(reportAuthzID);
      encodingStr = new StringArgument("encoding", 'i', "encoding",
                                      false, false,
                                      true, "{encoding}", null, null,
                                      MSGID_DESCRIPTION_ENCODING);
      argParser.addArgument(encodingStr);
      keyStorePath = new StringArgument("keyStorePath", 'K',
                                  "keyStorePath", false, false, true,
                                  "{keyStorePath}", null, null,
                                  MSGID_DESCRIPTION_KEYSTOREPATH);
      argParser.addArgument(keyStorePath);
      trustStorePath = new StringArgument("trustStorePath", 'P',
                                  "trustStorePath", false, false, true,
                                  "{trustStorePath}", null, null,
                                  MSGID_DESCRIPTION_TRUSTSTOREPATH);
      argParser.addArgument(trustStorePath);
      keyStorePassword = new StringArgument("keyStorePassword", 'W',
                                  "keyStorePassword", false, false,
                                  true, "{keyStorePassword}", null, null,
                                  MSGID_DESCRIPTION_KEYSTOREPASSWORD);
      argParser.addArgument(keyStorePassword);
      hostName = new StringArgument("host", 'h', "host",
                                      false, false,
                                      true, "{host}", "localhost", null,
                                      MSGID_DESCRIPTION_HOST);
      argParser.addArgument(hostName);
      port = new IntegerArgument("port", 'p', "port",
                              false, false, true, "{port}", 389, null,
                              MSGID_DESCRIPTION_PORT);
      argParser.addArgument(port);
      version = new IntegerArgument("version", 'V', "version",
                              false, false, true, "{version}", 3, null,
                              MSGID_DESCRIPTION_VERSION);
      argParser.addArgument(version);
      filename = new StringArgument("filename", 'f',
                                  "filename", false, false, true,
                                  "{filename}", null, null,
                                  MSGID_DELETE_DESCRIPTION_FILENAME);
      argParser.addArgument(filename);
      showUsage = new BooleanArgument("showUsage", 'H', "help",
                                    MSGID_DESCRIPTION_SHOWUSAGE);
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage, out);
      controlStr = new StringArgument("controls", 'J', "controls", false,
                false, true,
                "{controloid[:criticality[:value|::b64value|:<fileurl]]}",
                null, null, MSGID_DESCRIPTION_CONTROLS);
      argParser.addArgument(controlStr);
      verbose = new BooleanArgument("verbose", 'v', "verbose",
                                    MSGID_DESCRIPTION_VERBOSE);
      argParser.addArgument(verbose);
      continueOnError = new BooleanArgument("continueOnError", 'c',
                                    "continueOnError",
                                    MSGID_DESCRIPTION_CONTINUE_ON_ERROR);
      argParser.addArgument(continueOnError);
      useSSL = new BooleanArgument("useSSL", 'Z',
                                    "useSSL",
                                    MSGID_DESCRIPTION_USE_SSL);
      argParser.addArgument(useSSL);
      startTLS = new BooleanArgument("startTLS", 'q',
                                    "startTLS",
                                    MSGID_DESCRIPTION_START_TLS);
      argParser.addArgument(startTLS);
      saslExternal = new BooleanArgument("useSASLExternal", 'r',
                                    "useSASLExternal",
                                    MSGID_DESCRIPTION_USE_SASL_EXTERNAL);
      argParser.addArgument(saslExternal);
      defaultAdd = new BooleanArgument("defaultAdd", 'a',
                                    "defaultAdd",
                                    MSGID_MODIFY_DESCRIPTION_DEFAULT_ADD);
      argParser.addArgument(defaultAdd);
      saslOptions = new StringArgument("saslOptions", 'o', "saslOptions", false,
                                       true, true, "{name=value}", null, null,
                                       MSGID_DESCRIPTION_SASL_PROPERTIES);
      argParser.addArgument(saslOptions);
      assertionFilter = new StringArgument("assertionfilter", null,
                                           "assertionFilter", false, false,
                                           true, "{filter}", null, null,
                                           MSGID_DESCRIPTION_ASSERTION_FILTER);
      argParser.addArgument(assertionFilter);
      preReadAttributes = new StringArgument("prereadattrs", null,
                                             "preReadAttributes", false, false,
                                             true, "{attrList}", null, null,
                                             MSGID_DESCRIPTION_PREREAD_ATTRS);
      argParser.addArgument(preReadAttributes);
      postReadAttributes = new StringArgument("postreadattrs", null,
                                              "postReadAttributes", false,
                                              false, true, "{attrList}", null,
                                              null,
                                              MSGID_DESCRIPTION_POSTREAD_ATTRS);
      argParser.addArgument(postReadAttributes);
      noop = new BooleanArgument("no-op", 'n', "noop",
                                    MSGID_DESCRIPTION_NOOP);
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

    // If we should just display usage information, then print it and exit.
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

    modifyOptions.setShowOperations(noop.isPresent());
    modifyOptions.setVerbose(verbose.isPresent());
    modifyOptions.setContinueOnError(continueOnError.isPresent());
    modifyOptions.setEncoding(encodingStr.getValue());
    modifyOptions.setDefaultAdd(defaultAdd.isPresent());
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
      modifyOptions.getControls().add(ctrl);
    }

    if (proxyAuthzID.isPresent())
    {
      ASN1OctetString proxyValue = new ASN1OctetString(proxyAuthzID.getValue());

      LDAPControl proxyControl =
        new LDAPControl(OID_PROXIED_AUTH_V2, true, proxyValue);
      modifyOptions.getControls().add(proxyControl);
    }

    if (assertionFilter.isPresent())
    {
      String filterString = assertionFilter.getValue();
      LDAPFilter filter;
      try
      {
        filter = LDAPFilter.decode(filterString);

        LDAPControl assertionControl =
             new LDAPControl(OID_LDAP_ASSERTION, true,
                             new ASN1OctetString(filter.encode().encode()));
        modifyOptions.getControls().add(assertionControl);
      }
      catch (LDAPException le)
      {
        err.println(getMessage(MSGID_LDAP_ASSERTION_INVALID_FILTER,
                               le.getMessage()));
        return 1;
      }
    }

    if (preReadAttributes.isPresent())
    {
      String valueStr = preReadAttributes.getValue();
      ArrayList<ASN1Element> attrElements = new ArrayList<ASN1Element>();

      StringTokenizer tokenizer = new StringTokenizer(valueStr, ", ");
      while (tokenizer.hasMoreTokens())
      {
        attrElements.add(new ASN1OctetString(tokenizer.nextToken()));
      }

      ASN1OctetString controlValue =
           new ASN1OctetString(new ASN1Sequence(attrElements).encode());
      LDAPControl c = new LDAPControl(OID_LDAP_READENTRY_PREREAD, true,
                                      controlValue);
      modifyOptions.getControls().add(c);
    }

    if (postReadAttributes.isPresent())
    {
      String valueStr = postReadAttributes.getValue();
      ArrayList<ASN1Element> attrElements = new ArrayList<ASN1Element>();

      StringTokenizer tokenizer = new StringTokenizer(valueStr, ", ");
      while (tokenizer.hasMoreTokens())
      {
        attrElements.add(new ASN1OctetString(tokenizer.nextToken()));
      }

      ASN1OctetString controlValue =
           new ASN1OctetString(new ASN1Sequence(attrElements).encode());
      LDAPControl c = new LDAPControl(OID_LDAP_READENTRY_POSTREAD, true,
                                      controlValue);
      modifyOptions.getControls().add(c);
    }

    // Set the connection options.
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
    connectionOptions.setReportAuthzID(reportAuthzID.isPresent());

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

      LDAPModify ldapModify = new LDAPModify(fileNameValue, nextMessageID,
                                             out, err);
      InputStream is = System.in;
      if(fileNameValue != null)
      {
        is = new FileInputStream(fileNameValue);
      }
      ldapModify.readAndExecute(connection, is, modifyOptions);
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

