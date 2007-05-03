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
package org.opends.server.loggers;


import java.io.File;
import java.io.IOException;
import java.util.*;

import org.opends.server.api.*;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AbandonOperation;
import org.opends.server.core.AddOperation;
import org.opends.server.core.BindOperation;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.UnbindOperation;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.*;
import org.opends.server.util.Base64;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.TimeThread;
import static org.opends.server.util.StaticUtils.getFileForPath;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.types.ResultCode.*;
import static org.opends.server.util.ServerConstants.*;
import org.opends.server.admin.std.server.FileBasedAccessLogPublisherCfg;
import org.opends.server.admin.server.ConfigurationChangeListener;


/**
 * This class provides the implementation of the audit logger used by
 * the directory server.
 */
public class TextAuditLogPublisher
    extends AccessLogPublisher<FileBasedAccessLogPublisherCfg>
    implements ConfigurationChangeListener<FileBasedAccessLogPublisherCfg>
{
  private TextWriter writer;

  private FileBasedAccessLogPublisherCfg currentConfig;


    /**
   * {@inheritDoc}
   */
  public void initializeAccessLogPublisher(
        FileBasedAccessLogPublisherCfg config)
      throws ConfigException, InitializationException
    {
    File logFile = getFileForPath(config.getLogFile());
    FileNamingPolicy fnPolicy = new TimeStampNaming(logFile);

    try
    {
      FilePermission perm =
          FilePermission.decodeUNIXMode(config.getLogFileMode());

      LogPublisherErrorHandler errorHandler =
          new LogPublisherErrorHandler(config.dn());

      boolean writerAutoFlush =
          config.isAutoFlush() && !config.isAsynchronous();

      MultifileTextWriter writer =
          new MultifileTextWriter("Multifile Text Writer for " +
              config.dn().toNormalizedString(),
                                  config.getTimeInterval(),
                                  fnPolicy,
                                  perm,
                                  errorHandler,
                                  "UTF-8",
                                  writerAutoFlush,
                                  config.isAppend(),
                                  (int)config.getBufferSize());

      // Validate retention and rotation policies.
      for(DN dn : config.getRotationPolicyDN())
      {
        RotationPolicy policy = DirectoryServer.getRotationPolicy(dn);
        if(policy != null)
        {
          writer.addRotationPolicy(policy);
        }
        else
        {
          int msgID = MSGID_CONFIG_LOGGER_INVALID_ROTATION_POLICY;
          String message = getMessage(msgID, dn.toString(),
                                      config.dn().toString());
          throw new ConfigException(msgID, message);
        }
      }
      for(DN dn: config.getRetentionPolicyDN())
      {
        RetentionPolicy policy = DirectoryServer.getRetentionPolicy(dn);
        if(policy != null)
        {
          writer.addRetentionPolicy(policy);
        }
        else
        {
          int msgID = MSGID_CONFIG_LOGGER_INVALID_RETENTION_POLICY;
          String message = getMessage(msgID, dn.toString(),
                                      config.dn().toString());
          throw new ConfigException(msgID, message);
        }
      }

      if(config.isAsynchronous())
      {
        this.writer = new AsyncronousTextWriter("Asyncronous Text Writer for " +
            config.dn().toNormalizedString(), config.getQueueSize(),
                                              config.isAutoFlush(),
                                              writer);
      }
      else
      {
        this.writer = writer;
      }
    }
    catch(DirectoryException e)
    {
      int msgID = MSGID_CONFIG_LOGGING_CANNOT_CREATE_WRITER;
      String message = getMessage(msgID, config.dn().toString(),
                                  String.valueOf(e));
      throw new InitializationException(msgID, message, e);

    }
    catch(IOException e)
    {
      int msgID = MSGID_CONFIG_LOGGING_CANNOT_CREATE_WRITER;
      String message = getMessage(msgID, config.dn().toString(),
                                  String.valueOf(e));
      throw new InitializationException(msgID, message, e);

    }

    suppressInternalOperations = config.isSuppressInternalOperations();

    currentConfig = config;

    config.addFileBasedAccessChangeListener(this);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
       FileBasedAccessLogPublisherCfg config, List<String> unacceptableReasons)
   {
     // Make sure the permission is valid.
     try
     {
       if(!currentConfig.getLogFileMode().equalsIgnoreCase(
           config.getLogFileMode()))
       {
         FilePermission.decodeUNIXMode(config.getLogFileMode());
       }
       if(!currentConfig.getLogFile().equalsIgnoreCase(config.getLogFile()))
       {
         File logFile = getFileForPath(config.getLogFile());
         if(logFile.createNewFile())
         {
           logFile.delete();
         }
       }
     }
     catch(Exception e)
     {
       int msgID = MSGID_CONFIG_LOGGING_CANNOT_CREATE_WRITER;
       String message = getMessage(msgID, config.dn().toString(),
                                    stackTraceToSingleLineString(e));
       unacceptableReasons.add(message);
       return false;
     }

     // Validate retention and rotation policies.
     for(DN dn : config.getRotationPolicyDN())
     {
       RotationPolicy policy = DirectoryServer.getRotationPolicy(dn);
       if(policy == null)
       {
         int msgID = MSGID_CONFIG_LOGGER_INVALID_ROTATION_POLICY;
         String message = getMessage(msgID, dn.toString(),
                                     config.dn().toString());
         unacceptableReasons.add(message);
         return false;
       }
     }
     for(DN dn: config.getRetentionPolicyDN())
     {
       RetentionPolicy policy = DirectoryServer.getRetentionPolicy(dn);
       if(policy == null)
       {
         int msgID = MSGID_CONFIG_LOGGER_INVALID_RETENTION_POLICY;
         String message = getMessage(msgID, dn.toString(),
                                     config.dn().toString());
         unacceptableReasons.add(message);
         return false;
       }
     }

     return true;
   }

  /**
   * {@inheritDoc}
   */
   public ConfigChangeResult applyConfigurationChange(
       FileBasedAccessLogPublisherCfg config)
   {
     // Default result code.
     ResultCode resultCode = ResultCode.SUCCESS;
     boolean adminActionRequired = false;
     ArrayList<String> messages = new ArrayList<String>();

     suppressInternalOperations = config.isSuppressInternalOperations();

     File logFile = getFileForPath(config.getLogFile());
     FileNamingPolicy fnPolicy = new TimeStampNaming(logFile);

     try
     {
       FilePermission perm =
           FilePermission.decodeUNIXMode(config.getLogFileMode());

       boolean writerAutoFlush =
          config.isAutoFlush() && !config.isAsynchronous();

       TextWriter currentWriter;
       // Determine the writer we are using. If we were writing asyncronously,
       // we need to modify the underlaying writer.
       if(writer instanceof AsyncronousTextWriter)
       {
         currentWriter = ((AsyncronousTextWriter)writer).getWrappedWriter();
       }
       else
       {
         currentWriter = writer;
       }

       if(currentWriter instanceof MultifileTextWriter)
       {
         MultifileTextWriter mfWriter = (MultifileTextWriter)writer;

         mfWriter.setNamingPolicy(fnPolicy);
         mfWriter.setFilePermissions(perm);
         mfWriter.setAppend(config.isAppend());
         mfWriter.setAutoFlush(writerAutoFlush);
         mfWriter.setBufferSize((int)config.getBufferSize());
         mfWriter.setInterval(config.getTimeInterval());

         mfWriter.removeAllRetentionPolicies();
         mfWriter.removeAllRotationPolicies();

         for(DN dn : config.getRotationPolicyDN())
         {
           RotationPolicy policy = DirectoryServer.getRotationPolicy(dn);
           if(policy != null)
           {
             mfWriter.addRotationPolicy(policy);
           }
           else
           {
             int msgID = MSGID_CONFIG_LOGGER_INVALID_ROTATION_POLICY;
             String message = getMessage(msgID, dn.toString(),
                                         config.dn().toString());
             resultCode = DirectoryServer.getServerErrorResultCode();
             messages.add(message);
           }
         }
         for(DN dn: config.getRetentionPolicyDN())
         {
           RetentionPolicy policy = DirectoryServer.getRetentionPolicy(dn);
           if(policy != null)
           {
             mfWriter.addRetentionPolicy(policy);
           }
           else
           {
             int msgID = MSGID_CONFIG_LOGGER_INVALID_RETENTION_POLICY;
             String message = getMessage(msgID, dn.toString(),
                                         config.dn().toString());
             resultCode = DirectoryServer.getServerErrorResultCode();
             messages.add(message);
           }
         }


         if(writer instanceof AsyncronousTextWriter && !config.isAsynchronous())
         {
           // The asynronous setting is being turned off.
           AsyncronousTextWriter asyncWriter = ((AsyncronousTextWriter)writer);
           writer = mfWriter;
           asyncWriter.shutdown(false);
         }

         if(!(writer instanceof AsyncronousTextWriter) &&
             config.isAsynchronous())
         {
           // The asynronous setting is being turned on.
           AsyncronousTextWriter asyncWriter =
               new AsyncronousTextWriter("Asyncronous Text Writer for " +
                   config.dn().toNormalizedString(), config.getQueueSize(),
                                                     config.isAutoFlush(),
                                                     mfWriter);
           writer = asyncWriter;
         }

         if((currentConfig.isAsynchronous() && config.isAsynchronous()) &&
             (currentConfig.getQueueSize() != config.getQueueSize()))
         {
           adminActionRequired = true;
         }

         currentConfig = config;
       }
     }
     catch(Exception e)
     {
       int msgID = MSGID_CONFIG_LOGGING_CANNOT_CREATE_WRITER;
       String message = getMessage(msgID, config.dn().toString(),
                                   stackTraceToSingleLineString(e));
       resultCode = DirectoryServer.getServerErrorResultCode();
       messages.add(message);

     }

     return new ConfigChangeResult(resultCode, adminActionRequired, messages);
   }



  /**
   * {@inheritDoc}
   */
  public void close()
  {
    writer.shutdown();
    currentConfig.removeFileBasedAccessChangeListener(this);
  }


  /**
   * Writes a message to the audit logger with information about a new client
   * connection that has been established, regardless of whether it will be
   * immediately terminated.
   *
   * @param  clientConnection  The client connection that has been established.
   */
  public void logConnect(ClientConnection clientConnection)
  {
  }


  /**
   * Writes a message to the audit logger with information about the
   * termination of an existing client connection.
   *
   * @param  clientConnection  The client connection that has been terminated.
   * @param  disconnectReason  A generic disconnect reason for the connection
   *                           termination.
   * @param  message           A human-readable message that can provide
   *                           additional information about the disconnect.
   */
  public void logDisconnect(ClientConnection clientConnection,
                            DisconnectReason disconnectReason,
                            String message)
  {
  }



  /**
   * Writes a message to the audit logger with information about the abandon
   * request associated with the provided abandon operation.
   *
   * @param  abandonOperation  The abandon operation containing the information
   *                           to use to log the abandon request.
   */
  public void logAbandonRequest(AbandonOperation abandonOperation)
  {
  }


  /**
   * Writes a message to the audit logger with information about the result
   * of the provided abandon operation.
   *
   * @param  abandonOperation  The abandon operation containing the information
   *                           to use to log the abandon request.
   */
  public void logAbandonResult(AbandonOperation abandonOperation)
  {
  }


  /**
   * Writes a message to the audit logger with information about the add
   * request associated with the provided add operation.
   *
   * @param  addOperation  The add operation containing the information to use
   *                       to log the add request.
   */
  public void logAddRequest(AddOperation addOperation)
  {
  }


  /**
   * Writes a message to the audit logger with information about the add
   * response associated with the provided add operation.
   *
   * @param  addOperation  The add operation containing the information to use
   *                       to log the add response.
   */
  public void logAddResponse(AddOperation addOperation)
  {
    long connectionID = addOperation.getConnectionID();
    if(connectionID < 0 && suppressInternalOperations)
    {
      return;
    }
    ResultCode code = addOperation.getResultCode();

    if(code == SUCCESS)
    {
      StringBuilder buffer = new StringBuilder(50);
      buffer.append("[");
      buffer.append(TimeThread.getLocalTime());
      buffer.append("]");
      buffer.append("dn:");
      ByteString dnString = addOperation.getRawEntryDN();
      encodeValue(dnString, buffer);

      buffer.append(EOL);
      buffer.append("changetype: add");
      buffer.append(EOL);
      List<RawAttribute> rawAttributes = addOperation.getRawAttributes();
      for (RawAttribute attr : rawAttributes)
      {
        buffer.append(attr.getAttributeType());
        buffer.append(":");
        List<ASN1OctetString> values = attr.getValues();
        if (!values.isEmpty())
        {
          Iterator<ASN1OctetString> iterator = values.iterator();
          ASN1OctetString nextString = iterator.next();
          encodeValue(nextString, buffer);
          while (iterator.hasNext())
          {
            buffer.append(EOL);
            buffer.append(attr.getAttributeType());
            buffer.append(":");
            nextString = iterator.next();
            encodeValue(nextString, buffer);
          }
        }
        buffer.append(EOL);
      }

      writer.writeRecord(buffer.toString());
    }
  }



  /**
   * Writes a message to the audit logger with information about the bind
   * request associated with the provided bind operation.
   *
   * @param  bindOperation  The bind operation with the information to use
   *                        to log the bind request.
   */
  public void logBindRequest(BindOperation bindOperation)
  {
  }


  /**
   * Writes a message to the audit logger with information about the bind
   * response associated with the provided bind operation.
   *
   * @param  bindOperation  The bind operation containing the information to use
   *                        to log the bind response.
   */
  public void logBindResponse(BindOperation bindOperation)
  {
  }


  /**
   * Writes a message to the audit logger with information about the compare
   * request associated with the provided compare operation.
   *
   * @param  compareOperation  The compare operation containing the information
   *                           to use to log the compare request.
   */
  public void logCompareRequest(CompareOperation compareOperation)
  {
  }


  /**
   * Writes a message to the audit logger with information about the compare
   * response associated with the provided compare operation.
   *
   * @param  compareOperation  The compare operation containing the information
   *                           to use to log the compare response.
   */
  public void logCompareResponse(CompareOperation compareOperation)
  {
  }


  /**
   * Writes a message to the audit logger with information about the delete
   * request associated with the provided delete operation.
   *
   * @param  deleteOperation  The delete operation with the information to
   *                          use to log the delete request.
   */
  public void logDeleteRequest(DeleteOperation deleteOperation)
  {
  }


  /**
   * Writes a message to the audit logger with information about the delete
   * response associated with the provided delete operation.
   *
   * @param  deleteOperation The delete operation containing the information to
   *                           use to log the delete response.
   */
  public void logDeleteResponse(DeleteOperation deleteOperation)
  {
    long connectionID = deleteOperation.getConnectionID();
    if(connectionID < 0 && suppressInternalOperations)
    {
      return;
    }
    ResultCode code = deleteOperation.getResultCode();

    if(code == SUCCESS)
    {
      StringBuilder buffer = new StringBuilder(50);
      buffer.append("[");
      buffer.append(TimeThread.getLocalTime());
      buffer.append("]");
      buffer.append("dn:");
      ByteString dnString = deleteOperation.getRawEntryDN();
      encodeValue(dnString, buffer);
      buffer.append(EOL);
      buffer.append("changetype: delete");
      buffer.append(EOL);
      buffer.append(EOL);

      writer.writeRecord(buffer.toString());
    }

  }



  /**
   * Writes a message to the audit logger with information about the extended
   * request associated with the provided extended operation.
   *
   * @param  extendedOperation  The extended operation containing the
   *                            information to use to log the extended request.
   */
  public void logExtendedRequest(ExtendedOperation extendedOperation)
  {
  }



  /**
   * Writes a message to the audit logger with information about the extended
   * response associated with the provided extended operation.
   *
   * @param  extendedOperation  The extended operation containing the
   *                            info to use to log the extended response.
   */
  public void logExtendedResponse(ExtendedOperation extendedOperation)
  {
  }



  /**
   * Writes a message to the audit logger with information about the modify
   * request associated with the provided modify operation.
   *
   * @param  modifyOperation The modify operation containing the information to
   *                         use to log the modify request.
   */
  public void logModifyRequest(ModifyOperation modifyOperation)
  {
  }



  /**
   * Writes a message to the audit logger with information about the modify
   * response associated with the provided modify operation.
   *
   * @param  modifyOperation The modify operation containing the information to
   *                         use to log the modify response.
   */
  public void logModifyResponse(ModifyOperation modifyOperation)
  {
    long connectionID = modifyOperation.getConnectionID();
    if(connectionID < 0 && suppressInternalOperations)
    {
      return;
    }
    ResultCode code = modifyOperation.getResultCode();

    if(code == SUCCESS)
    {
      StringBuilder buffer = new StringBuilder(50);
      buffer.append("[");
      buffer.append(TimeThread.getLocalTime());
      buffer.append("]");
      buffer.append("dn:");
      ByteString dnString = modifyOperation.getRawEntryDN();
      encodeValue(dnString, buffer);
      buffer.append(EOL);
      buffer.append("changetype: modify");
      buffer.append(EOL);
      List<RawModification> modifications =
          modifyOperation.getRawModifications();
      for (RawModification modification : modifications)
      {
        ModificationType modType = modification.getModificationType();
        RawAttribute attr = modification.getAttribute();
        switch (modType)
        {
          case ADD:
            buffer.append("add: ");
            break;
          case DELETE:
            buffer.append("delete: ");
            break;
          case REPLACE:
            buffer.append("replace: ");
            break;
          default:
            break;
        }
        buffer.append(attr.getAttributeType());
        List<ASN1OctetString> values = attr.getValues();
        if (!values.isEmpty())
        {
          Iterator<ASN1OctetString> iterator = values.iterator();
          ASN1OctetString nextString = iterator.next();
          encodeValue(nextString, buffer);
          while (iterator.hasNext())
          {
            buffer.append(EOL);
            buffer.append(attr.getAttributeType());
            buffer.append(":");
            nextString = iterator.next();
            encodeValue(nextString, buffer);
          }
        }
        buffer.append(EOL);
      }

      buffer.append(EOL);

      writer.writeRecord(buffer.toString());
    }
  }



  /**
   * Writes a message to the audit logger with information about the modify DN
   * request associated with the provided modify DN operation.
   *
   * @param  modifyDNOperation  The modify DN operation containing the
   *                            info to use to log the modify DN request.
   */
  public void logModifyDNRequest(ModifyDNOperation modifyDNOperation)
  {
  }



  /**
   * Writes a message to the audit logger with information about the modify DN
   * response associated with the provided modify DN operation.
   *
   * @param  modifyDNOperation  The modify DN operation containing the
   *                            information to use to log the modify DN
   *                            response.
   */
  public void logModifyDNResponse(ModifyDNOperation modifyDNOperation)
  {
    long connectionID = modifyDNOperation.getConnectionID();
    if(connectionID < 0 && suppressInternalOperations)
    {
      return;
    }
    ResultCode code = modifyDNOperation.getResultCode();

    if(code == SUCCESS)
    {
      StringBuilder buffer = new StringBuilder(50);
      buffer.append("[");
      buffer.append(TimeThread.getLocalTime());
      buffer.append("]");
      buffer.append("dn:");
      ByteString dnString = modifyDNOperation.getRawEntryDN();
      encodeValue(dnString, buffer);
      buffer.append(EOL);
      buffer.append("changetype: moddn");
      buffer.append(EOL);
      buffer.append("newrdn: ");
      ByteString newrdnString = modifyDNOperation.getRawNewRDN();
      encodeValue(newrdnString, buffer);
      buffer.append(EOL);
      buffer.append("deleteoldrdn: ");
      if (modifyDNOperation.deleteOldRDN())
      {
        buffer.append("1");
      }
      else
      {
        buffer.append("0");
      }
      buffer.append(EOL);
      if (modifyDNOperation.getRawNewSuperior() != null)
      {
        buffer.append("newsuperior: ");
        ByteString newSuperior = modifyDNOperation.getRawNewSuperior();
        encodeValue(newSuperior, buffer);
        buffer.append(EOL);
      }

      buffer.append(EOL);

      writer.writeRecord(buffer.toString());
    }
  }


  /**
   * Writes a message to the audit logger with information about the search
   * request associated with the provided search operation.
   *
   * @param  searchOperation  The search operation containing the info to
   *                          use to log the search request.
   */
  public void logSearchRequest(SearchOperation searchOperation)
  {
  }


  /**
   * Writes a message to the audit logger with information about the search
   * result entry that matches the criteria associated with the provided search
   * operation.
   *
   * @param  searchOperation  The search operation with which the search result
   *                          entry is associated.
   * @param  searchEntry      The search result entry to be logged.
   */
  public void logSearchResultEntry(SearchOperation searchOperation,
                                     SearchResultEntry searchEntry)
  {
  }


  /**
   * Writes a message to the audit logger with information about the search
   * result reference returned while processing the associated search
   * operation.
   *
   * @param  searchOperation  The search operation with which the search result
   *                          reference is associated.
   * @param  searchReference  The search result reference to be logged.
   */
  public void logSearchResultReference(SearchOperation searchOperation,
                            SearchResultReference searchReference)
  {
  }



  /**
   * Writes a message to the audit logger with information about the
   * completion of the provided search operation.
   *
   * @param  searchOperation  The search operation containing the information
   *                          to use to log the search result done message.
   */
  public void logSearchResultDone(SearchOperation searchOperation)
  {
  }



  /**
   * Writes a message to the audit logger with information about the unbind
   * request associated with the provided unbind operation.
   *
   * @param  unbindOperation  The unbind operation containing the info to
   *                          use to log the unbind request.
   */
  public void logUnbind(UnbindOperation unbindOperation)
  {
  }


  /**
   * Appends the appropriately-encoded attribute value to the provided buffer.
   *
   * @param  str     The ASN.1 octet string containing the value to append.
   * @param  buffer  The buffer to which to append the value.
   */
  private void encodeValue(ByteString str, StringBuilder buffer)
  {
      byte[] byteVal = str.value();
      if(StaticUtils.needsBase64Encoding(byteVal))
      {
        buffer.append(": ");
        buffer.append(Base64.encode(byteVal));
      } else
      {
        buffer.append(" ");
        str.toString(buffer);
      }
  }
}

