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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.tools;
import org.opends.messages.Message;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.ldap.AddRequestProtocolOp;
import org.opends.server.protocols.ldap.AddResponseProtocolOp;
import org.opends.server.protocols.ldap.DeleteRequestProtocolOp;
import org.opends.server.protocols.ldap.DeleteResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.ModifyRequestProtocolOp;
import org.opends.server.protocols.ldap.ModifyResponseProtocolOp;
import org.opends.server.protocols.ldap.ModifyDNRequestProtocolOp;
import org.opends.server.protocols.ldap.ModifyDNResponseProtocolOp;
import org.opends.server.protocols.ldap.ProtocolOp;
import org.opends.server.types.*;
import org.opends.server.util.AddChangeRecordEntry;
import org.opends.server.util.ChangeRecordEntry;
import org.opends.server.util.EmbeddedUtils;
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

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.tools.ToolConstants.*;
import org.opends.server.controls.*;
import org.opends.server.plugins.ChangeNumberControlPlugin;



/**
 * This class provides a tool that can be used to issue modify requests to the
 * Directory Server.
 */
public class LDAPModify
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The fully-qualified name of this class.
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
   * @param fileNameValue  Name of the file from which to read.  If null,
   *                       input will be read from <code>System.in</code>.
   * @param modifyOptions  The constraints for the modify request.
   *
   * @throws  IOException  If a problem occurs while attempting to communicate
   *                       with the Directory Server.
   *
   * @throws  LDAPException  If the Directory Server returns an error response.
   */
  public void readAndExecute(LDAPConnection connection, String fileNameValue,
                             LDAPModifyOptions modifyOptions)
         throws IOException, LDAPException
  {
    ArrayList<Control> controls = modifyOptions.getControls();
    LDIFReader reader;

    // Create an LDIF import configuration to do this and then get the reader.

    try
    {
      InputStream is = System.in;
      if(fileNameValue != null)
      {
        is = new FileInputStream(fileNameValue);
      }

      LDIFImportConfig importConfig = new LDIFImportConfig(is);
      reader = new LDIFReader(importConfig);
    } catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      Message message =
          ERR_LDIF_FILE_CANNOT_OPEN_FOR_READ.get(fileNameValue,
                  e.getLocalizedMessage());
      throw new IOException(message.toString());
    }

    while (true)
    {
      ChangeRecordEntry entry = null;

      try
      {
        entry = reader.readChangeRecord(modifyOptions.getDefaultAdd());
      } catch (LDIFException le)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, le);
        }
        if (!modifyOptions.continueOnError())
        {
          try
          {
            reader.close();
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
          }

          Message message = ERR_LDIF_FILE_INVALID_LDIF_ENTRY.get(
              le.getLineNumber(), fileName, String.valueOf(le));
          throw new IOException(message.toString());
        }
        else
        {
          Message message = ERR_LDIF_FILE_INVALID_LDIF_ENTRY.get(
                  le.getLineNumber(), fileName, String.valueOf(le));
          err.println(wrapText(message, MAX_LINE_WIDTH));
          continue;
        }
      } catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        if (!modifyOptions.continueOnError())
        {
          try
          {
            reader.close();
          }
          catch (Exception e2)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e2);
            }
          }

          Message message =
              ERR_LDIF_FILE_READ_ERROR.get(fileName, String.valueOf(e));
          throw new IOException(message.toString());
        }
        else
        {
          Message message = ERR_LDIF_FILE_READ_ERROR.get(
                  fileName, String.valueOf(e));
          err.println(wrapText(message, MAX_LINE_WIDTH));
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
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }

        break;
      }

      ProtocolOp protocolOp = null;
      ByteString asn1OctetStr =
          ByteString.valueOf(entry.getDN().toString());

      String operationType = "";
      switch(entry.getChangeOperationType())
      {
        case ADD:
          operationType = "ADD";
          AddChangeRecordEntry addEntry = (AddChangeRecordEntry) entry;
          List<Attribute> attrs = addEntry.getAttributes();
          ArrayList<RawAttribute> attributes =
              new ArrayList<RawAttribute>(attrs.size());
          for(Attribute a : attrs)
          {
            attributes.add(new LDAPAttribute(a));
          }
          protocolOp = new AddRequestProtocolOp(asn1OctetStr, attributes);
          out.println(INFO_PROCESSING_OPERATION.get(
                  operationType, asn1OctetStr.toString()));
          break;
        case DELETE:
          operationType = "DELETE";
          protocolOp = new DeleteRequestProtocolOp(asn1OctetStr);
          out.println(INFO_PROCESSING_OPERATION.get(
                  operationType, asn1OctetStr.toString()));
          break;
        case MODIFY:
          operationType = "MODIFY";
          ModifyChangeRecordEntry modEntry = (ModifyChangeRecordEntry) entry;
          ArrayList<RawModification> mods =
            new ArrayList<RawModification>(modEntry.getModifications());
          protocolOp = new ModifyRequestProtocolOp(asn1OctetStr, mods);
          out.println(INFO_PROCESSING_OPERATION.get(
                  operationType, asn1OctetStr.toString()));
          break;
        case MODIFY_DN:
          operationType = "MODIFY DN";
          ModifyDNChangeRecordEntry modDNEntry =
            (ModifyDNChangeRecordEntry) entry;
          if(modDNEntry.getNewSuperiorDN() != null)
          {
            protocolOp = new ModifyDNRequestProtocolOp(asn1OctetStr,
                ByteString.valueOf(modDNEntry.getNewRDN().toString()),
                 modDNEntry.deleteOldRDN(),
                ByteString.valueOf(
                          modDNEntry.getNewSuperiorDN().toString()));
          } else
          {
            protocolOp = new ModifyDNRequestProtocolOp(asn1OctetStr,
                ByteString.valueOf(modDNEntry.getNewRDN().toString()),
                 modDNEntry.deleteOldRDN());
          }

          out.println(INFO_PROCESSING_OPERATION.get(
                  operationType, asn1OctetStr.toString()));
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
          connection.getLDAPWriter().writeMessage(message);
          responseMessage = connection.getLDAPReader().readMessage();
        } catch(ASN1Exception ae)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, ae);
          }
          Message message = INFO_OPERATION_FAILED.get(operationType);
          err.println(wrapText(message, MAX_LINE_WIDTH));
          err.println(wrapText(ae.getMessage(), MAX_LINE_WIDTH));
          if (!modifyOptions.continueOnError())
          {
            throw new IOException(ae.getMessage());
          }
          return;
        }

        int resultCode = 0;
        Message errorMessage = null;
        DN matchedDN = null;
        List<String> referralURLs = null;
        switch(entry.getChangeOperationType())
        {
          case ADD:
            AddResponseProtocolOp addOp =
              responseMessage.getAddResponseProtocolOp();
            resultCode = addOp.getResultCode();
            errorMessage = addOp.getErrorMessage();
            matchedDN = addOp.getMatchedDN();
            referralURLs = addOp.getReferralURLs();
            break;
          case DELETE:
            DeleteResponseProtocolOp delOp =
              responseMessage.getDeleteResponseProtocolOp();
            resultCode = delOp.getResultCode();
            errorMessage = delOp.getErrorMessage();
            matchedDN = delOp.getMatchedDN();
            referralURLs = delOp.getReferralURLs();
            break;
          case MODIFY:
            ModifyResponseProtocolOp modOp =
              responseMessage.getModifyResponseProtocolOp();
            resultCode = modOp.getResultCode();
            errorMessage = modOp.getErrorMessage();
            matchedDN = modOp.getMatchedDN();
            referralURLs = modOp.getReferralURLs();
            break;
          case MODIFY_DN:
            ModifyDNResponseProtocolOp modDNOp =
              responseMessage.getModifyDNResponseProtocolOp();
            resultCode = modDNOp.getResultCode();
            errorMessage = modDNOp.getErrorMessage();
            matchedDN = modDNOp.getMatchedDN();
            referralURLs = modDNOp.getReferralURLs();
            break;
          default:
            break;
        }

        if(resultCode != SUCCESS && resultCode != REFERRAL)
        {
          Message msg = INFO_OPERATION_FAILED.get(operationType);

          if(!modifyOptions.continueOnError())
          {
            throw new LDAPException(resultCode, errorMessage, msg,
                                    matchedDN, null);
          } else
          {
            LDAPToolUtils.printErrorMessage(err, msg, resultCode, errorMessage,
                                            matchedDN);
          }
        } else
        {
          Message msg = INFO_OPERATION_SUCCESSFUL.get(
                  operationType, asn1OctetStr.toString());
          out.println(msg);

          if (errorMessage != null)
          {
            out.println(wrapText(errorMessage, MAX_LINE_WIDTH));
          }

          if (referralURLs != null)
          {
            out.println(referralURLs);
          }
        }


        for (Control c : responseMessage.getControls())
        {
          String oid = c.getOID();
          if (oid.equals(OID_LDAP_READENTRY_PREREAD))
          {
            SearchResultEntry searchEntry;
            try
            {
              LDAPPreReadResponseControl prrc;
              if(c instanceof LDAPControl)
              {
                // Control needs to be decoded
                prrc = LDAPPreReadResponseControl.DECODER.decode(
                    c.isCritical(), ((LDAPControl) c).getValue());

              }
              else
              {
                prrc = (LDAPPreReadResponseControl)c;
              }
              searchEntry = prrc.getSearchEntry();
            }
            catch (DirectoryException de)
            {

              err.println(wrapText(
                  ERR_LDAPMODIFY_PREREAD_CANNOT_DECODE_VALUE.get(
                              de.getMessage()),
                      MAX_LINE_WIDTH));
              continue;
            }

            StringBuilder buffer = new StringBuilder();
            searchEntry.toString(buffer, 0);
            out.println(INFO_LDAPMODIFY_PREREAD_ENTRY.get());
            out.println(buffer);
          }
          else if (oid.equals(OID_LDAP_READENTRY_POSTREAD))
          {
            SearchResultEntry searchEntry;
            try
            {
              LDAPPostReadResponseControl pprc;
              if (c instanceof LDAPControl)
              {
                // Control needs to be decoded
                pprc = LDAPPostReadResponseControl.DECODER.decode(c
                    .isCritical(), ((LDAPControl) c).getValue());

              }
              else
              {
                pprc = (LDAPPostReadResponseControl)c;
              }
              searchEntry = pprc.getSearchEntry();
            }
            catch (DirectoryException de)
            {

              err.println(wrapText(
                      ERR_LDAPMODIFY_POSTREAD_CANNOT_DECODE_VALUE.get(
                              de.getMessage()),
                      MAX_LINE_WIDTH));
              continue;
            }

            StringBuilder buffer = new StringBuilder();
            searchEntry.toString(buffer, 0);
            out.println(INFO_LDAPMODIFY_POSTREAD_ENTRY.get());
            out.println(buffer);
          }
          else if (oid.equals(OID_CSN_CONTROL))
          {
            if(c instanceof LDAPControl)
            {
              // Don't really need to decode since its just an octet string.
              out.println(INFO_CHANGE_NUMBER_CONTROL_RESULT.get(operationType,
                  ((LDAPControl)c).getValue().toString()));
            }
            else
            {
              out.println(INFO_CHANGE_NUMBER_CONTROL_RESULT.get(operationType,
                  ((ChangeNumberControlPlugin.ChangeNumberControl)c).
                      getChangeNumber().toString()));
            }
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
      System.exit(filterExitCode(retCode));
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

    BooleanArgument   continueOnError        = null;
    BooleanArgument   defaultAdd             = null;
    BooleanArgument   noop                   = null;
    BooleanArgument   reportAuthzID          = null;
    BooleanArgument   saslExternal           = null;
    BooleanArgument   showUsage              = null;
    BooleanArgument   startTLS               = null;
    BooleanArgument   trustAll               = null;
    BooleanArgument   useSSL                 = null;
    BooleanArgument   verbose                = null;
    FileBasedArgument bindPasswordFile       = null;
    FileBasedArgument keyStorePasswordFile   = null;
    FileBasedArgument trustStorePasswordFile = null;
    IntegerArgument   port                   = null;
    IntegerArgument   version                = null;
    StringArgument    assertionFilter        = null;
    StringArgument    bindDN                 = null;
    StringArgument    bindPassword           = null;
    StringArgument    certNickname           = null;
    StringArgument    controlStr             = null;
    StringArgument    encodingStr            = null;
    StringArgument    filename               = null;
    StringArgument    hostName               = null;
    StringArgument    keyStorePath           = null;
    StringArgument    keyStorePassword       = null;
    StringArgument    postReadAttributes     = null;
    StringArgument    preReadAttributes      = null;
    StringArgument    proxyAuthzID           = null;
    StringArgument    saslOptions            = null;
    StringArgument    trustStorePath         = null;
    StringArgument    trustStorePassword     = null;
    StringArgument    propertiesFileArgument   = null;
    BooleanArgument   noPropertiesFileArgument = null;

    // Create the command-line argument parser for use with this program.
    Message toolDescription = INFO_LDAPMODIFY_TOOL_DESCRIPTION.get();
    ArgumentParser argParser = new ArgumentParser(CLASS_NAME, toolDescription,
                                                  false);
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

      hostName = new StringArgument("host", OPTION_SHORT_HOST,
                                    OPTION_LONG_HOST, false, false, true,
                                    INFO_HOST_PLACEHOLDER.get(), "localhost",
                                    null,
                                    INFO_DESCRIPTION_HOST.get());
      hostName.setPropertyName(OPTION_LONG_HOST);
      argParser.addArgument(hostName);

      port = new IntegerArgument("port", OPTION_SHORT_PORT,
                                 OPTION_LONG_PORT, false, false, true,
                                 INFO_PORT_PLACEHOLDER.get(), 389, null,
                                 INFO_DESCRIPTION_PORT.get());
      port.setPropertyName(OPTION_LONG_PORT);
      argParser.addArgument(port);

      useSSL = new BooleanArgument("useSSL", OPTION_SHORT_USE_SSL,
                                   OPTION_LONG_USE_SSL,
                                   INFO_DESCRIPTION_USE_SSL.get());
      useSSL.setPropertyName(OPTION_LONG_USE_SSL);
      argParser.addArgument(useSSL);

      startTLS = new BooleanArgument("startTLS", OPTION_SHORT_START_TLS,
                                     OPTION_LONG_START_TLS,
                                     INFO_DESCRIPTION_START_TLS.get());
      startTLS.setPropertyName(OPTION_LONG_START_TLS);
      argParser.addArgument(startTLS);

      bindDN = new StringArgument("bindDN", OPTION_SHORT_BINDDN,
                                  OPTION_LONG_BINDDN, false, false, true,
                                  INFO_BINDDN_PLACEHOLDER.get(), null, null,
                                  INFO_DESCRIPTION_BINDDN.get());
      bindDN.setPropertyName(OPTION_LONG_BINDDN);
      argParser.addArgument(bindDN);

      bindPassword = new StringArgument("bindPassword", OPTION_SHORT_BINDPWD,
                                        OPTION_LONG_BINDPWD,
                                        false, false, true,
                                        INFO_BINDPWD_PLACEHOLDER.get(),
                                        null, null,
                                        INFO_DESCRIPTION_BINDPASSWORD.get());
      bindPassword.setPropertyName(OPTION_LONG_BINDPWD);
      argParser.addArgument(bindPassword);

      bindPasswordFile =
           new FileBasedArgument("bindPasswordFile",
                                 OPTION_SHORT_BINDPWD_FILE,
                                 OPTION_LONG_BINDPWD_FILE,
                                 false, false,
                                 INFO_BINDPWD_FILE_PLACEHOLDER.get(), null,
                                 null, INFO_DESCRIPTION_BINDPASSWORDFILE.get());
      bindPasswordFile.setPropertyName(OPTION_LONG_BINDPWD_FILE);
      argParser.addArgument(bindPasswordFile);

      defaultAdd = new BooleanArgument(
              "defaultAdd", 'a', "defaultAdd",
              INFO_MODIFY_DESCRIPTION_DEFAULT_ADD.get());
      argParser.addArgument(defaultAdd);

      filename = new StringArgument("filename", OPTION_SHORT_FILENAME,
                                    OPTION_LONG_FILENAME, false, false,
                                    true, INFO_FILE_PLACEHOLDER.get(), null,
                                    null,
                                    INFO_LDAPMODIFY_DESCRIPTION_FILENAME.get());
      filename.setPropertyName(OPTION_LONG_FILENAME);
      argParser.addArgument(filename);

      saslExternal = new BooleanArgument(
              "useSASLExternal", 'r',
              "useSASLExternal",
              INFO_DESCRIPTION_USE_SASL_EXTERNAL.get());
      saslExternal.setPropertyName("useSASLExternal");
      argParser.addArgument(saslExternal);

      saslOptions = new StringArgument("saslOption", OPTION_SHORT_SASLOPTION,
                                       OPTION_LONG_SASLOPTION, false,
                                       true, true,
                                       INFO_SASL_OPTION_PLACEHOLDER.get(), null,
                                       null,
                                       INFO_DESCRIPTION_SASL_PROPERTIES.get());
      saslOptions.setPropertyName(OPTION_LONG_SASLOPTION);
      argParser.addArgument(saslOptions);

      trustAll = new BooleanArgument("trustAll", 'X', "trustAll",
                                    INFO_DESCRIPTION_TRUSTALL.get());
      trustAll.setPropertyName("trustAll");
      argParser.addArgument(trustAll);

      keyStorePath = new StringArgument("keyStorePath",
                                        OPTION_SHORT_KEYSTOREPATH,
                                        OPTION_LONG_KEYSTOREPATH,
                                        false, false, true,
                                        INFO_KEYSTOREPATH_PLACEHOLDER.get(),
                                        null, null,
                                        INFO_DESCRIPTION_KEYSTOREPATH.get());
      keyStorePath.setPropertyName(OPTION_LONG_KEYSTOREPATH);
      argParser.addArgument(keyStorePath);

      keyStorePassword =
              new StringArgument("keyStorePassword",
                                 OPTION_SHORT_KEYSTORE_PWD,
                                 OPTION_LONG_KEYSTORE_PWD,
                                 false, false,
                                 true,
                                 INFO_KEYSTORE_PWD_PLACEHOLDER.get(),
                                 null, null,
                                 INFO_DESCRIPTION_KEYSTOREPASSWORD.get());
      keyStorePassword.setPropertyName(OPTION_LONG_KEYSTORE_PWD);
      argParser.addArgument(keyStorePassword);

      keyStorePasswordFile =
           new FileBasedArgument("keystorepasswordfile",
                                 OPTION_SHORT_KEYSTORE_PWD_FILE,
                                 OPTION_LONG_KEYSTORE_PWD_FILE,
                                 false, false,
                                 INFO_KEYSTORE_PWD_FILE_PLACEHOLDER.get(),
                                 null, null,
                                 INFO_DESCRIPTION_KEYSTOREPASSWORD_FILE.get());
      keyStorePasswordFile.setPropertyName(OPTION_LONG_KEYSTORE_PWD_FILE);
      argParser.addArgument(keyStorePasswordFile);

      certNickname = new StringArgument(
              "certnickname", 'N', "certNickname",
              false, false, true, INFO_NICKNAME_PLACEHOLDER.get(), null,
              null, INFO_DESCRIPTION_CERT_NICKNAME.get());
      certNickname.setPropertyName("certNickname");
      argParser.addArgument(certNickname);

      trustStorePath = new StringArgument(
              "trustStorePath",
              OPTION_SHORT_TRUSTSTOREPATH,
              OPTION_LONG_TRUSTSTOREPATH,
              false, false, true,
              INFO_TRUSTSTOREPATH_PLACEHOLDER.get(),
              null, null,
              INFO_DESCRIPTION_TRUSTSTOREPATH.get());
      trustStorePath.setPropertyName(OPTION_LONG_TRUSTSTOREPATH);
      argParser.addArgument(trustStorePath);

      trustStorePassword =
           new StringArgument("trustStorePassword", null,
                              OPTION_LONG_TRUSTSTORE_PWD ,
                              false, false, true,
                              INFO_TRUSTSTORE_PWD_PLACEHOLDER.get(), null,
                              null, INFO_DESCRIPTION_TRUSTSTOREPASSWORD.get());
      trustStorePassword.setPropertyName(OPTION_LONG_TRUSTSTORE_PWD);
      argParser.addArgument(trustStorePassword);

      trustStorePasswordFile =
           new FileBasedArgument(
                   "truststorepasswordfile",
                   OPTION_SHORT_TRUSTSTORE_PWD_FILE,
                   OPTION_LONG_TRUSTSTORE_PWD_FILE, false, false,
                   INFO_TRUSTSTORE_PWD_FILE_PLACEHOLDER.get(), null, null,
                   INFO_DESCRIPTION_TRUSTSTOREPASSWORD_FILE.get());
      trustStorePasswordFile.setPropertyName(OPTION_LONG_TRUSTSTORE_PWD_FILE);
      argParser.addArgument(trustStorePasswordFile);

      proxyAuthzID = new StringArgument("proxy_authzid",
                                        OPTION_SHORT_PROXYAUTHID,
                                        OPTION_LONG_PROXYAUTHID, false,
                                        false, true,
                                        INFO_PROXYAUTHID_PLACEHOLDER.get(),
                                        null, null,
                                        INFO_DESCRIPTION_PROXY_AUTHZID.get());
      proxyAuthzID.setPropertyName(OPTION_LONG_PROXYAUTHID);
      argParser.addArgument(proxyAuthzID);

      reportAuthzID = new BooleanArgument(
              "reportauthzid", 'E',
              "reportAuthzID",
              INFO_DESCRIPTION_REPORT_AUTHZID.get());
      reportAuthzID.setPropertyName("reportAuthzID");
      argParser.addArgument(reportAuthzID);

      assertionFilter = new StringArgument(
              "assertionfilter", null,
              OPTION_LONG_ASSERTION_FILE,
              false, false,
              true,
              INFO_ASSERTION_FILTER_PLACEHOLDER.get(),
              null, null,
              INFO_DESCRIPTION_ASSERTION_FILTER.get());
      assertionFilter.setPropertyName(OPTION_LONG_ASSERTION_FILE);
      argParser.addArgument(assertionFilter);

      preReadAttributes = new StringArgument(
              "prereadattrs", null,
              "preReadAttributes", false, false,
              true, INFO_ATTRIBUTE_LIST_PLACEHOLDER.get(), null, null,
              INFO_DESCRIPTION_PREREAD_ATTRS.get());
      preReadAttributes.setPropertyName("preReadAttributes");
      argParser.addArgument(preReadAttributes);

      postReadAttributes = new StringArgument(
              "postreadattrs", null,
              "postReadAttributes", false,
              false, true, INFO_ATTRIBUTE_LIST_PLACEHOLDER.get(), null,
              null,
              INFO_DESCRIPTION_POSTREAD_ATTRS.get());
      postReadAttributes.setPropertyName("postReadAttributes");
      argParser.addArgument(postReadAttributes);

      controlStr =
           new StringArgument("control", 'J', "control", false, true, true,
                    INFO_LDAP_CONTROL_PLACEHOLDER.get(),
                    null, null, INFO_DESCRIPTION_CONTROLS.get());
      controlStr.setPropertyName("control");
      argParser.addArgument(controlStr);

      version = new IntegerArgument("version", OPTION_SHORT_PROTOCOL_VERSION,
                              OPTION_LONG_PROTOCOL_VERSION,
                              false, false, true,
                              INFO_PROTOCOL_VERSION_PLACEHOLDER.get(), 3, null,
                              INFO_DESCRIPTION_VERSION.get());
      version.setPropertyName(OPTION_LONG_PROTOCOL_VERSION);
      argParser.addArgument(version);

      encodingStr = new StringArgument("encoding", 'i', "encoding",
                                      false, false,
                                      true, INFO_ENCODING_PLACEHOLDER.get(),
                                      null, null,
                                      INFO_DESCRIPTION_ENCODING.get());
      encodingStr.setPropertyName("encoding");
      argParser.addArgument(encodingStr);

      continueOnError = new BooleanArgument("continueOnError", 'c',
                                    "continueOnError",
                                    INFO_DESCRIPTION_CONTINUE_ON_ERROR.get());
      continueOnError.setPropertyName("continueOnError");
      argParser.addArgument(continueOnError);

      noop = new BooleanArgument("no-op", OPTION_SHORT_DRYRUN,
                                    OPTION_LONG_DRYRUN,
                                    INFO_DESCRIPTION_NOOP.get());
      noop.setPropertyName(OPTION_LONG_DRYRUN);
      argParser.addArgument(noop);

      verbose = new BooleanArgument("verbose", 'v', "verbose",
                                    INFO_DESCRIPTION_VERBOSE.get());
      verbose.setPropertyName("verbose");
      argParser.addArgument(verbose);

      showUsage = new BooleanArgument("showUsage", OPTION_SHORT_HELP,
                                      OPTION_LONG_HELP,
                                      INFO_DESCRIPTION_SHOWUSAGE.get());
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage, out);
    } catch (ArgumentException ae)
    {
      Message message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      return CLIENT_SIDE_PARAM_ERROR;
    }

    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return CLIENT_SIDE_PARAM_ERROR;
    }

    // If we should just display usage or version information,
    // then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
    }

    if(bindPassword.isPresent() && bindPasswordFile.isPresent())
    {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
              bindPassword.getLongIdentifier(),
              bindPasswordFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return CLIENT_SIDE_PARAM_ERROR;
    }

    String hostNameValue = hostName.getValue();
    int portNumber = 389;
    try
    {
      portNumber = port.getIntValue();
    } catch(ArgumentException ae)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ae);
      }
      err.println(wrapText(ae.getMessage(), MAX_LINE_WIDTH));
      return CLIENT_SIDE_PARAM_ERROR;
    }

    try
    {
      int versionNumber = version.getIntValue();
      if(versionNumber != 2 && versionNumber != 3)
      {

        err.println(wrapText(ERR_DESCRIPTION_INVALID_VERSION.get(
                String.valueOf(versionNumber)), MAX_LINE_WIDTH));
        return CLIENT_SIDE_PARAM_ERROR;
      }
      connectionOptions.setVersionNumber(versionNumber);
    } catch(ArgumentException ae)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ae);
      }
      err.println(wrapText(ae.getMessage(), MAX_LINE_WIDTH));
      return CLIENT_SIDE_PARAM_ERROR;
    }

    String bindDNValue = bindDN.getValue();
    String fileNameValue = filename.getValue();
    String bindPasswordValue = bindPassword.getValue();
    if(bindPasswordValue != null && bindPasswordValue.equals("-")  ||
        (!bindPasswordFile.isPresent()  &&
        (bindDNValue != null && bindPasswordValue == null)))
    {
      // read the password from the stdin.
      try
      {
        out.print(INFO_LDAPAUTH_PASSWORD_PROMPT.get(bindDNValue));
        char[] pwChars = PasswordReader.readPassword();
        bindPasswordValue = new String(pwChars);
      } catch(Exception ex)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, ex);
        }
        err.println(wrapText(ex.getMessage(), MAX_LINE_WIDTH));
        return CLIENT_SIDE_PARAM_ERROR;
      }
    } else if(bindPasswordValue == null)
    {
      // Read from file if it exists.
      bindPasswordValue = bindPasswordFile.getValue();
    }

    String keyStorePathValue = keyStorePath.getValue();
    String trustStorePathValue = trustStorePath.getValue();

    String keyStorePasswordValue = null;
    if (keyStorePassword.isPresent())
    {
      keyStorePasswordValue = keyStorePassword.getValue();
    }
    else if (keyStorePasswordFile.isPresent())
    {
      keyStorePasswordValue = keyStorePasswordFile.getValue();
    }

    String trustStorePasswordValue = null;
    if (trustStorePassword.isPresent())
    {
      trustStorePasswordValue = trustStorePassword.getValue();
    }
    else if (trustStorePasswordFile.isPresent())
    {
      trustStorePasswordValue = trustStorePasswordFile.getValue();
    }

    modifyOptions.setShowOperations(noop.isPresent());
    modifyOptions.setVerbose(verbose.isPresent());
    modifyOptions.setContinueOnError(continueOnError.isPresent());
    modifyOptions.setEncoding(encodingStr.getValue());
    modifyOptions.setDefaultAdd(defaultAdd.isPresent());

    if (controlStr.isPresent())
    {
      for (String ctrlString : controlStr.getValues())
      {
        Control ctrl = LDAPToolUtils.getControl(ctrlString, err);
        if(ctrl == null)
        {
          Message message = ERR_TOOL_INVALID_CONTROL_STRING.get(ctrlString);
          err.println(wrapText(message, MAX_LINE_WIDTH));
          err.println(argParser.getUsage());
          return CLIENT_SIDE_PARAM_ERROR;
        }
        modifyOptions.getControls().add(ctrl);
      }
    }

    if (proxyAuthzID.isPresent())
    {
      Control proxyControl =
          new ProxiedAuthV2Control(true,
              ByteString.valueOf(proxyAuthzID.getValue()));
      modifyOptions.getControls().add(proxyControl);
    }

    if (assertionFilter.isPresent())
    {
      String filterString = assertionFilter.getValue();
      LDAPFilter filter;
      try
      {
        filter = LDAPFilter.decode(filterString);

        Control assertionControl =
            new LDAPAssertionRequestControl(true, filter);
        modifyOptions.getControls().add(assertionControl);
      }
      catch (LDAPException le)
      {
        Message message = ERR_LDAP_ASSERTION_INVALID_FILTER.get(
                le.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return CLIENT_SIDE_PARAM_ERROR;
      }
    }

    if (preReadAttributes.isPresent())
    {
      String valueStr = preReadAttributes.getValue();
      Set<String> attrElements = new LinkedHashSet<String>();

      StringTokenizer tokenizer = new StringTokenizer(valueStr, ", ");
      while (tokenizer.hasMoreTokens())
      {
        attrElements.add(tokenizer.nextToken());
      }

      Control c = new LDAPPreReadRequestControl(true, attrElements);
      modifyOptions.getControls().add(c);
    }

    if (postReadAttributes.isPresent())
    {
      String valueStr = postReadAttributes.getValue();
      Set<String> attrElements = new LinkedHashSet<String>();

      StringTokenizer tokenizer = new StringTokenizer(valueStr, ", ");
      while (tokenizer.hasMoreTokens())
      {
        attrElements.add(tokenizer.nextToken());
      }

      Control c = new LDAPPostReadRequestControl(true, attrElements);
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
            return CLIENT_SIDE_PARAM_ERROR;
          }
        } else
        {
          boolean val = connectionOptions.addSASLProperty(saslOption);
          if(val == false)
          {
            return CLIENT_SIDE_PARAM_ERROR;
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
        Message message = ERR_TOOL_SASLEXTERNAL_NEEDS_SSL_OR_TLS.get();
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return CLIENT_SIDE_PARAM_ERROR;
      }
      if(keyStorePathValue == null)
      {
        Message message = ERR_TOOL_SASLEXTERNAL_NEEDS_KEYSTORE.get();
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return CLIENT_SIDE_PARAM_ERROR;
      }
    }

    connectionOptions.setVerbose(verbose.isPresent());

    LDAPModify ldapModify = null;
    try
    {
      if (initializeServer)
      {
        // Bootstrap and initialize directory data structures.
        EmbeddedUtils.initializeForClientUse();
      }

      // Connect to the specified host with the supplied userDN and password.
      SSLConnectionFactory sslConnectionFactory = null;
      if(connectionOptions.useSSL() || connectionOptions.useStartTLS())
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

        sslConnectionFactory = new SSLConnectionFactory();
        sslConnectionFactory.init(trustAll.isPresent(), keyStorePathValue,
                                  keyStorePasswordValue, clientAlias,
                                  trustStorePathValue, trustStorePasswordValue);
        connectionOptions.setSSLConnectionFactory(sslConnectionFactory);
      }

      AtomicInteger nextMessageID = new AtomicInteger(1);
      connection = new LDAPConnection(hostNameValue, portNumber,
                                      connectionOptions, out, err);
      connection.connectToHost(bindDNValue, bindPasswordValue, nextMessageID);

      ldapModify = new LDAPModify(fileNameValue, nextMessageID, out, err);
      ldapModify.readAndExecute(connection, fileNameValue, modifyOptions);
    } catch(LDAPException le)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, le);
      }
      LDAPToolUtils.printErrorMessage(err, le.getMessageObject(),
                                      le.getResultCode(),
                                      le.getErrorMessage(), le.getMatchedDN());
      int code = le.getResultCode();
      return code;
    } catch(LDAPConnectionException lce)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, lce);
      }
      LDAPToolUtils.printErrorMessage(err, lce.getMessageObject(),
                                      lce.getResultCode(),
                                      lce.getErrorMessage(),
                                      lce.getMatchedDN());
      int code = lce.getResultCode();
      return code;
    } catch(Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      err.println(wrapText(e.getMessage(), MAX_LINE_WIDTH));
      return 1;
    } finally
    {
      if(connection != null)
      {
        if (ldapModify == null)
        {
          connection.close(null);
        }
        else
        {
          connection.close(ldapModify.nextMessageID);
        }
      }
    }
    return 0;
  }

}

