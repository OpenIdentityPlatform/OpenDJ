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
 * Portions Copyright 2012 profiq, s.r.o.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.tools;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.CliMessages.INFO_DESCRIPTION_BINDPASSWORDFILE;
import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.cli.CommonArguments.*;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.args.LDAPConnectionArgumentParser.*;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.controls.*;
import org.opends.server.core.DirectoryServer.DirectoryServerVersionHandler;
import org.opends.server.plugins.ChangeNumberControlPlugin;
import org.opends.server.protocols.ldap.AddRequestProtocolOp;
import org.opends.server.protocols.ldap.AddResponseProtocolOp;
import org.opends.server.protocols.ldap.DeleteRequestProtocolOp;
import org.opends.server.protocols.ldap.DeleteResponseProtocolOp;
import org.opends.server.protocols.ldap.ExtendedResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPConstants;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.ModifyDNRequestProtocolOp;
import org.opends.server.protocols.ldap.ModifyDNResponseProtocolOp;
import org.opends.server.protocols.ldap.ModifyRequestProtocolOp;
import org.opends.server.protocols.ldap.ModifyResponseProtocolOp;
import org.opends.server.protocols.ldap.ProtocolOp;
import org.opends.server.types.*;
import org.opends.server.util.AddChangeRecordEntry;
import org.opends.server.util.ChangeRecordEntry;
import org.opends.server.util.EmbeddedUtils;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.ModifyChangeRecordEntry;
import org.opends.server.util.ModifyDNChangeRecordEntry;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.CliConstants;
import com.forgerock.opendj.cli.FileBasedArgument;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.StringArgument;

/**
 * This class provides a tool that can be used to issue modify requests to the
 * Directory Server.
 */
public class LDAPModify
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME = "org.opends.server.tools.LDAPModify";

  /** The message ID counter to use for requests. */
  private final AtomicInteger nextMessageID;

  /** The print stream to use for standard error. */
  private final PrintStream err;

  /** The print stream to use for standard output. */
  private final PrintStream out;

  /**
   * Constructor for the LDAPModify object.
   *
   * @param  nextMessageID  The message ID counter to use for requests.
   * @param  out            The print stream to use for standard output.
   * @param  err            The print stream to use for standard error.
   */
  public LDAPModify(AtomicInteger nextMessageID, PrintStream out,
      PrintStream err)
  {
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
      importConfig.setValidateSchema(false);
      reader = new LDIFReader(importConfig);
    } catch (Exception e)
    {
      logger.traceException(e);
      LocalizableMessage message =
          ERR_LDIF_FILE_CANNOT_OPEN_FOR_READ.get(fileNameValue,
                  e.getLocalizedMessage());
      throw new FileNotFoundException(message.toString());
    }

    // Set this for error messages
    if (fileNameValue == null)
    {
      fileNameValue = "Console";
    }

    while (true)
    {
      ChangeRecordEntry entry = null;

      try
      {
        entry = reader.readChangeRecord(modifyOptions.getDefaultAdd());
      } catch (LDIFException le)
      {
        logger.traceException(le);
        if (!modifyOptions.continueOnError())
        {
          try
          {
            reader.close();
          }
          catch (Exception e)
          {
            logger.traceException(e);
          }

          LocalizableMessage message = ERR_LDIF_FILE_INVALID_LDIF_ENTRY.get(
              le.getLineNumber(), fileNameValue, le);
          throw new IOException(message.toString());
        }
        else
        {
          printWrappedText(err, ERR_LDIF_FILE_INVALID_LDIF_ENTRY.get(le.getLineNumber(), fileNameValue, le));
          continue;
        }
      } catch (Exception e)
      {
        logger.traceException(e);

        if (!modifyOptions.continueOnError())
        {
          try
          {
            reader.close();
          }
          catch (Exception e2)
          {
            logger.traceException(e2);
          }

          LocalizableMessage message = ERR_LDIF_FILE_READ_ERROR.get(fileNameValue, e);
          throw new IOException(message.toString());
        }
        else
        {
          printWrappedText(err, ERR_LDIF_FILE_READ_ERROR.get(fileNameValue, e));
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
          logger.traceException(e);
        }

        break;
      }

      ProtocolOp protocolOp = null;
      ByteString asn1OctetStr =
          ByteString.valueOfUtf8(entry.getDN().toString());

      String operationType = "";
      switch(entry.getChangeOperationType())
      {
        case ADD:
          operationType = "ADD";
          AddChangeRecordEntry addEntry = (AddChangeRecordEntry) entry;
          List<Attribute> attrs = addEntry.getAttributes();
          ArrayList<RawAttribute> attributes = new ArrayList<>(attrs.size());
          for(Attribute a : attrs)
          {
            attributes.add(new LDAPAttribute(a));
          }
          protocolOp = new AddRequestProtocolOp(asn1OctetStr, attributes);
          out.println(INFO_PROCESSING_OPERATION.get(operationType, asn1OctetStr));
          break;
        case DELETE:
          operationType = "DELETE";
          protocolOp = new DeleteRequestProtocolOp(asn1OctetStr);
          out.println(INFO_PROCESSING_OPERATION.get(operationType, asn1OctetStr));
          break;
        case MODIFY:
          operationType = "MODIFY";
          ModifyChangeRecordEntry modEntry = (ModifyChangeRecordEntry) entry;
          ArrayList<RawModification> mods = new ArrayList<>(modEntry.getModifications());
          protocolOp = new ModifyRequestProtocolOp(asn1OctetStr, mods);
          out.println(INFO_PROCESSING_OPERATION.get(operationType, asn1OctetStr));
          break;
        case MODIFY_DN:
          operationType = "MODIFY DN";
          ModifyDNChangeRecordEntry modDNEntry =
            (ModifyDNChangeRecordEntry) entry;
          if(modDNEntry.getNewSuperiorDN() != null)
          {
            protocolOp = new ModifyDNRequestProtocolOp(asn1OctetStr,
                ByteString.valueOfUtf8(modDNEntry.getNewRDN().toString()),
                 modDNEntry.deleteOldRDN(),
                ByteString.valueOfUtf8(
                          modDNEntry.getNewSuperiorDN().toString()));
          } else
          {
            protocolOp = new ModifyDNRequestProtocolOp(asn1OctetStr,
                ByteString.valueOfUtf8(modDNEntry.getNewRDN().toString()),
                 modDNEntry.deleteOldRDN());
          }

          out.println(INFO_PROCESSING_OPERATION.get(operationType, asn1OctetStr));
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
        } catch(DecodeException ae)
        {
          logger.traceException(ae);
          printWrappedText(err, INFO_OPERATION_FAILED.get(operationType));
          printWrappedText(err, ae.getMessage());
          if (!modifyOptions.continueOnError())
          {
            String msg = LDAPToolUtils.getMessageForConnectionException(ae);
            throw new IOException(msg, ae);
          }
          return;
        }

        int resultCode = 0;
        LocalizableMessage errorMessage = null;
        DN matchedDN = null;
        List<String> referralURLs = null;
        try
        {
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
        }
        catch (ClassCastException ce)
        {
          // It is possible that this is extended response.
          if (responseMessage.getProtocolOpType() ==
              LDAPConstants.OP_TYPE_EXTENDED_RESPONSE)
          {
            ExtendedResponseProtocolOp extRes =
              responseMessage.getExtendedResponseProtocolOp();
            resultCode = extRes.getResultCode();
            errorMessage = extRes.getErrorMessage();
            matchedDN = extRes.getMatchedDN();
            referralURLs = extRes.getReferralURLs();
          }
          else
          {
            // This should not happen but if it does, then debug log it,
            // set the error code to OTHER and fall through.
            logger.traceException(ce);
            resultCode = ResultCode.OTHER.intValue();
            errorMessage = null;
            matchedDN = null;
            referralURLs = null;
          }
        }

        if(resultCode != SUCCESS && resultCode != REFERRAL)
        {
          LocalizableMessage msg = INFO_OPERATION_FAILED.get(operationType);

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
          out.println(INFO_OPERATION_SUCCESSFUL.get(operationType, asn1OctetStr));

          if (errorMessage != null)
          {
            printWrappedText(out, errorMessage);
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
              printWrappedText(err, ERR_LDAPMODIFY_PREREAD_CANNOT_DECODE_VALUE.get(de.getMessage()));
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
              printWrappedText(err, ERR_LDAPMODIFY_POSTREAD_CANNOT_DECODE_VALUE.get(de.getMessage()));
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
              out.println(INFO_CHANGE_NUMBER_CONTROL_RESULT.get(
                  operationType, ((LDAPControl)c).getValue()));
            }
            else
            {
              out.println(INFO_CHANGE_NUMBER_CONTROL_RESULT.get(operationType,
                  ((ChangeNumberControlPlugin.ChangeNumberControl)c).getCSN()));
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
    PrintStream out = NullOutputStream.wrapOrNullStream(outStream);
    PrintStream err = NullOutputStream.wrapOrNullStream(errStream);

    LDAPConnectionOptions connectionOptions = new LDAPConnectionOptions();
    LDAPModifyOptions modifyOptions = new LDAPModifyOptions();
    LDAPConnection connection = null;

    final BooleanArgument continueOnError;
    final BooleanArgument defaultAdd;
    final BooleanArgument noop;
    final BooleanArgument reportAuthzID;
    final BooleanArgument saslExternal;
    final BooleanArgument showUsage;
    final BooleanArgument startTLS;
    final BooleanArgument trustAll;
    final BooleanArgument useSSL;
    final BooleanArgument verbose;
    final FileBasedArgument bindPasswordFile;
    final FileBasedArgument keyStorePasswordFile;
    final FileBasedArgument trustStorePasswordFile;
    final IntegerArgument connectTimeout;
    final IntegerArgument port;
    final IntegerArgument version;
    final StringArgument assertionFilter;
    final StringArgument bindDN;
    final StringArgument bindPassword;
    final StringArgument certNickname;
    final StringArgument controlStr;
    final StringArgument encodingStr;
    final StringArgument filename;
    final StringArgument hostName;
    final StringArgument keyStorePath;
    final StringArgument keyStorePassword;
    final StringArgument postReadAttributes;
    final StringArgument preReadAttributes;
    final StringArgument proxyAuthzID;
    final StringArgument saslOptions;
    final StringArgument trustStorePath;
    final StringArgument trustStorePassword;
    final StringArgument propertiesFileArgument;
    final BooleanArgument noPropertiesFileArgument;

    // Create the command-line argument parser for use with this program.
    LocalizableMessage toolDescription = INFO_LDAPMODIFY_TOOL_DESCRIPTION.get();
    ArgumentParser argParser = new ArgumentParser(CLASS_NAME, toolDescription,
                                                  false);
    argParser.setShortToolDescription(REF_SHORT_DESC_LDAPMODIFY.get());
    argParser.setVersionHandler(new DirectoryServerVersionHandler());
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

      hostName =
              StringArgument.builder(OPTION_LONG_HOST)
                      .shortIdentifier(OPTION_SHORT_HOST)
                      .description(INFO_DESCRIPTION_HOST.get())
                      .defaultValue("localhost")
                      .valuePlaceholder(INFO_HOST_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      port =
              IntegerArgument.builder(OPTION_LONG_PORT)
                      .shortIdentifier(OPTION_SHORT_PORT)
                      .description(INFO_DESCRIPTION_PORT.get())
                      .range(1, 65535)
                      .defaultValue(389)
                      .valuePlaceholder(INFO_PORT_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      useSSL =
              BooleanArgument.builder(OPTION_LONG_USE_SSL)
                      .shortIdentifier(OPTION_SHORT_USE_SSL)
                      .description(INFO_DESCRIPTION_USE_SSL.get())
                      .buildAndAddToParser(argParser);
      startTLS =
              BooleanArgument.builder(OPTION_LONG_START_TLS)
                      .shortIdentifier(OPTION_SHORT_START_TLS)
                      .description(INFO_DESCRIPTION_START_TLS.get())
                      .buildAndAddToParser(argParser);
      bindDN =
              StringArgument.builder(OPTION_LONG_BINDDN)
                      .shortIdentifier(OPTION_SHORT_BINDDN)
                      .description(INFO_DESCRIPTION_BINDDN.get())
                      .valuePlaceholder(INFO_BINDDN_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      bindPassword =
              StringArgument.builder(OPTION_LONG_BINDPWD)
                      .shortIdentifier(OPTION_SHORT_BINDPWD)
                      .description(INFO_DESCRIPTION_BINDPASSWORD.get())
                      .valuePlaceholder(INFO_BINDPWD_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      bindPasswordFile =
              FileBasedArgument.builder(OPTION_LONG_BINDPWD_FILE)
                      .shortIdentifier(OPTION_SHORT_BINDPWD_FILE)
                      .description(INFO_DESCRIPTION_BINDPASSWORDFILE.get())
                      .valuePlaceholder(INFO_BINDPWD_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      defaultAdd =
              BooleanArgument.builder("defaultAdd")
                      .shortIdentifier('a')
                      .description(INFO_MODIFY_DESCRIPTION_DEFAULT_ADD.get())
                      .buildAndAddToParser(argParser);
      filename =
              StringArgument.builder(OPTION_LONG_FILENAME)
                      .shortIdentifier(OPTION_SHORT_FILENAME)
                      .description(INFO_LDAPMODIFY_DESCRIPTION_FILENAME.get())
                      .valuePlaceholder(INFO_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      saslExternal =
              BooleanArgument.builder("useSASLExternal")
                      .shortIdentifier('r')
                      .description(INFO_DESCRIPTION_USE_SASL_EXTERNAL.get())
                      .buildAndAddToParser(argParser);
      saslOptions =
              StringArgument.builder(OPTION_LONG_SASLOPTION)
                      .shortIdentifier(OPTION_SHORT_SASLOPTION)
                      .description(INFO_DESCRIPTION_SASL_PROPERTIES.get())
                      .multiValued()
                      .valuePlaceholder(INFO_SASL_OPTION_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);

      trustAll = trustAllArgument();
      argParser.addArgument(trustAll);

      keyStorePath =
              StringArgument.builder(OPTION_LONG_KEYSTOREPATH)
                      .shortIdentifier(OPTION_SHORT_KEYSTOREPATH)
                      .description(INFO_DESCRIPTION_KEYSTOREPATH.get())
                      .valuePlaceholder(INFO_KEYSTOREPATH_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      keyStorePassword =
              StringArgument.builder(OPTION_LONG_KEYSTORE_PWD)
                      .shortIdentifier(OPTION_SHORT_KEYSTORE_PWD)
                      .description(INFO_DESCRIPTION_KEYSTOREPASSWORD.get())
                      .valuePlaceholder(INFO_KEYSTORE_PWD_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      keyStorePasswordFile =
              FileBasedArgument.builder(OPTION_LONG_KEYSTORE_PWD_FILE)
                      .shortIdentifier(OPTION_SHORT_KEYSTORE_PWD_FILE)
                      .description(INFO_DESCRIPTION_KEYSTOREPASSWORD_FILE.get())
                      .valuePlaceholder(INFO_KEYSTORE_PWD_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      certNickname =
              StringArgument.builder("certNickname")
                      .shortIdentifier('N')
                      .description(INFO_DESCRIPTION_CERT_NICKNAME.get())
                      .valuePlaceholder(INFO_NICKNAME_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      trustStorePath =
              StringArgument.builder(OPTION_LONG_TRUSTSTOREPATH)
                      .shortIdentifier(OPTION_SHORT_TRUSTSTOREPATH)
                      .description(INFO_DESCRIPTION_TRUSTSTOREPATH.get())
                      .valuePlaceholder(INFO_TRUSTSTOREPATH_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      trustStorePassword =
              StringArgument.builder(OPTION_LONG_TRUSTSTORE_PWD)
                      .description(INFO_DESCRIPTION_TRUSTSTOREPASSWORD.get())
                      .valuePlaceholder(INFO_TRUSTSTORE_PWD_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      trustStorePasswordFile =
              FileBasedArgument.builder(OPTION_LONG_TRUSTSTORE_PWD_FILE)
                      .shortIdentifier(OPTION_SHORT_TRUSTSTORE_PWD_FILE)
                      .description(INFO_DESCRIPTION_TRUSTSTOREPASSWORD_FILE.get())
                      .valuePlaceholder(INFO_TRUSTSTORE_PWD_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      proxyAuthzID =
              StringArgument.builder(OPTION_LONG_PROXYAUTHID)
                      .shortIdentifier(OPTION_SHORT_PROXYAUTHID)
                      .description(INFO_DESCRIPTION_PROXY_AUTHZID.get())
                      .valuePlaceholder(INFO_PROXYAUTHID_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      reportAuthzID =
              BooleanArgument.builder("reportAuthzID")
                      .shortIdentifier('E')
                      .description(INFO_DESCRIPTION_REPORT_AUTHZID.get())
                      .buildAndAddToParser(argParser);
      assertionFilter =
              StringArgument.builder(OPTION_LONG_ASSERTION_FILE)
                      .description(INFO_DESCRIPTION_ASSERTION_FILTER.get())
                      .valuePlaceholder(INFO_ASSERTION_FILTER_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      preReadAttributes =
              StringArgument.builder("preReadAttributes")
                      .description(INFO_DESCRIPTION_PREREAD_ATTRS.get())
                      .valuePlaceholder(INFO_ATTRIBUTE_LIST_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      postReadAttributes =
              StringArgument.builder("postReadAttributes")
                      .description(INFO_DESCRIPTION_POSTREAD_ATTRS.get())
                      .valuePlaceholder(INFO_ATTRIBUTE_LIST_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      controlStr =
              StringArgument.builder("control")
                      .shortIdentifier('J')
                      .description(INFO_DESCRIPTION_CONTROLS.get())
                      .multiValued()
                      .valuePlaceholder(INFO_LDAP_CONTROL_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      version =
              IntegerArgument.builder(OPTION_LONG_PROTOCOL_VERSION)
                      .shortIdentifier(OPTION_SHORT_PROTOCOL_VERSION)
                      .description(INFO_DESCRIPTION_VERSION.get())
                      .defaultValue(3)
                      .valuePlaceholder(INFO_PROTOCOL_VERSION_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
        connectTimeout =
                IntegerArgument.builder(OPTION_LONG_CONNECT_TIMEOUT)
                        .description(INFO_DESCRIPTION_CONNECTION_TIMEOUT.get())
                        .lowerBound(0)
                        .defaultValue(CliConstants.DEFAULT_LDAP_CONNECT_TIMEOUT)
                        .valuePlaceholder(INFO_TIMEOUT_PLACEHOLDER.get())
                        .buildAndAddToParser(argParser);
      encodingStr =
              StringArgument.builder("encoding")
                      .shortIdentifier('i')
                      .description(INFO_DESCRIPTION_ENCODING.get())
                      .valuePlaceholder(INFO_ENCODING_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      continueOnError =
              BooleanArgument.builder("continueOnError")
                      .shortIdentifier('c')
                      .description(INFO_DESCRIPTION_CONTINUE_ON_ERROR.get())
                      .buildAndAddToParser(argParser);
      noop =
              BooleanArgument.builder(OPTION_LONG_DRYRUN)
                      .shortIdentifier(OPTION_SHORT_DRYRUN)
                      .description(INFO_DESCRIPTION_NOOP.get())
                      .buildAndAddToParser(argParser);

      verbose = verboseArgument();
      argParser.addArgument(verbose);

      showUsage = showUsageArgument();
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage, out);
    } catch (ArgumentException ae)
    {
      printWrappedText(err, ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
      return CLIENT_SIDE_PARAM_ERROR;
    }

    // Parse the command-line arguments provided to this program.
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
    // then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return SUCCESS;
    }

    if(bindPassword.isPresent() && bindPasswordFile.isPresent())
    {
      printWrappedText(
          err, ERR_TOOL_CONFLICTING_ARGS.get(bindPassword.getLongIdentifier(), bindPasswordFile.getLongIdentifier()));
      return CLIENT_SIDE_PARAM_ERROR;
    }

    String hostNameValue = hostName.getValue();
    int portNumber = 389;
    try
    {
      portNumber = port.getIntValue();
    } catch(ArgumentException ae)
    {
      logger.traceException(ae);
      argParser.displayMessageAndUsageReference(err, ae.getMessageObject());
      return CLIENT_SIDE_PARAM_ERROR;
    }

    try
    {
      int versionNumber = version.getIntValue();
      if(versionNumber != 2 && versionNumber != 3)
      {
        printWrappedText(err, ERR_DESCRIPTION_INVALID_VERSION.get(versionNumber));
        return CLIENT_SIDE_PARAM_ERROR;
      }
      connectionOptions.setVersionNumber(versionNumber);
    } catch(ArgumentException ae)
    {
      logger.traceException(ae);
      argParser.displayMessageAndUsageReference(err, ae.getMessageObject());
      return CLIENT_SIDE_PARAM_ERROR;
    }

    String bindDNValue = bindDN.getValue();
    String fileNameValue = filename.getValue();
    String bindPasswordValue;
    try
    {
      bindPasswordValue = getPasswordValue(
          bindPassword, bindPasswordFile, bindDNValue, out, err);
    }
    catch (Exception ex)
    {
      logger.traceException(ex);
      printWrappedText(err, ex.getMessage());
      return CLIENT_SIDE_PARAM_ERROR;
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
          printWrappedText(err, ERR_TOOL_INVALID_CONTROL_STRING.get(ctrlString));
          return CLIENT_SIDE_PARAM_ERROR;
        }
        modifyOptions.getControls().add(ctrl);
      }
    }

    if (proxyAuthzID.isPresent())
    {
      Control proxyControl =
          new ProxiedAuthV2Control(true,
              ByteString.valueOfUtf8(proxyAuthzID.getValue()));
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
        printWrappedText(err, ERR_LDAP_ASSERTION_INVALID_FILTER.get(le.getMessage()));
        return CLIENT_SIDE_PARAM_ERROR;
      }
    }

    if (preReadAttributes.isPresent())
    {
      String valueStr = preReadAttributes.getValue();
      Set<String> attrElements = new LinkedHashSet<>();

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
      Set<String> attrElements = new LinkedHashSet<>();

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
      for (String saslOption : saslOptions.getValues())
      {
        boolean val = saslOption.startsWith("mech=")
            ? connectionOptions.setSASLMechanism(saslOption)
            : connectionOptions.addSASLProperty(saslOption);
        if (!val)
        {
          return CLIENT_SIDE_PARAM_ERROR;
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
        printWrappedText(err, ERR_TOOL_SASLEXTERNAL_NEEDS_SSL_OR_TLS.get());
        return CLIENT_SIDE_PARAM_ERROR;
      }
      if(keyStorePathValue == null)
      {
        printWrappedText(err, ERR_TOOL_SASLEXTERNAL_NEEDS_KEYSTORE.get());
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
      int timeout = connectTimeout.getIntValue();
      connection.connectToHost(bindDNValue, bindPasswordValue, nextMessageID,
          timeout);

      ldapModify = new LDAPModify(nextMessageID, out, err);
      ldapModify.readAndExecute(connection, fileNameValue, modifyOptions);
    } catch(LDAPException le)
    {
      logger.traceException(le);
      LDAPToolUtils.printErrorMessage(err, le.getMessageObject(),
                                      le.getResultCode(),
                                      le.getErrorMessage(), le.getMatchedDN());
      return le.getResultCode();
    } catch(LDAPConnectionException lce)
    {
      logger.traceException(lce);
      LDAPToolUtils.printErrorMessage(err, lce.getMessageObject(),
                                      lce.getResultCode(),
                                      lce.getErrorMessage(),
                                      lce.getMatchedDN());
      return lce.getResultCode();
    } catch (FileNotFoundException fe)
    {
      logger.traceException(fe);
      printWrappedText(err, fe.getMessage());
      return CLIENT_SIDE_PARAM_ERROR;
    }
    catch(ArgumentException e)
    {
      argParser.displayMessageAndUsageReference(err, e.getMessageObject());
      return 1;
    }
    catch(Exception e)
    {
      logger.traceException(e);
      printWrappedText(err, e.getMessage());
      return OPERATIONS_ERROR;
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
    return SUCCESS;
  }

}

