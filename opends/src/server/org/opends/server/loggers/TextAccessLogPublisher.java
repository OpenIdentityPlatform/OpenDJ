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
import org.opends.server.types.*;

import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import org.opends.server.admin.std.server.FileBasedAccessLogPublisherCfg;
import org.opends.server.admin.server.ConfigurationChangeListener;
import static org.opends.server.util.StaticUtils.getFileForPath;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;
import org.opends.server.util.TimeThread;


/**
 * This class provides the implementation of the access logger used by
 * the directory server.
 */
public class TextAccessLogPublisher
    extends AccessLogPublisher<FileBasedAccessLogPublisherCfg>
    implements ConfigurationChangeListener<FileBasedAccessLogPublisherCfg>
{
  private TextWriter writer;

  private FileBasedAccessLogPublisherCfg currentConfig;

  /**
   * Returns an instance of the text access log publisher that will print
   * all messages to the provided writer. This is used to print the messages
   * to the console when the server starts up.
   *
   * @param writer The text writer where the message will be written to.
   * @return The instance of the text error log publisher that will print
   * all messages to standard out.
   */
  public static TextAccessLogPublisher
      getStartupTextAccessPublisher(TextWriter writer)
  {
    TextAccessLogPublisher startupPublisher = new TextAccessLogPublisher();
    startupPublisher.writer = writer;

    return startupPublisher;
  }

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
         MultifileTextWriter mfWriter = (MultifileTextWriter)currentWriter;

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

    if(currentConfig != null)
    {
      currentConfig.removeFileBasedAccessChangeListener(this);
    }
  }



  /**
   * Writes a message to the access logger with information about a new client
   * connection that has been established, regardless of whether it will be
   * immediately terminated.
   *
   * @param  clientConnection  The client connection that has been established.
   */
  public void logConnect(ClientConnection clientConnection)
  {
    long connectionID = clientConnection.getConnectionID();
    if (connectionID < 0 && suppressInternalOperations)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);
    buffer.append("[");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("]");
    buffer.append(" CONNECT conn=");
    buffer.append(connectionID);
    buffer.append(" from=");
    buffer.append(clientConnection.getClientAddress());
    buffer.append(" to=");
    buffer.append(clientConnection.getServerAddress());
    buffer.append(" protocol=");
    buffer.append(clientConnection.getProtocol());

    writer.writeRecord(buffer.toString());

  }


  /**
   * Writes a message to the access logger with information about the
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
    long connectionID = clientConnection.getConnectionID();
    if (connectionID < 0 && suppressInternalOperations)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);
    buffer.append("[");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("]");
    buffer.append(" DISCONNECT conn=");
    buffer.append(connectionID);
    buffer.append(" reason=\"");
    buffer.append(disconnectReason);

    if (message != null)
    {
      buffer.append("\" msg=\"");
      buffer.append(message);
    }

    buffer.append("\"");

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the abandon
   * request associated with the provided abandon operation.
   *
   * @param  abandonOperation  The abandon operation containing the information
   *                           to use to log the abandon request.
   */
  public void logAbandonRequest(AbandonOperation abandonOperation)
  {
    long connectionID = abandonOperation.getConnectionID();
    if (connectionID < 0 && suppressInternalOperations)
    {
      return;
    }

    StringBuilder buffer = new StringBuilder(50);
    buffer.append("[");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("]");
    buffer.append(" ABANDON conn=");
    buffer.append(connectionID);
    buffer.append(" op=");
    buffer.append(abandonOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(abandonOperation.getMessageID());
    buffer.append(" idToAbandon=");
    buffer.append(abandonOperation.getIDToAbandon());

    writer.writeRecord(buffer.toString());
  }

  /**
   * Writes a message to the access logger with information about the result
   * of the provided abandon operation.
   *
   * @param  abandonOperation  The abandon operation containing the information
   *                           to use to log the abandon request.
   */
  public void logAbandonResult(AbandonOperation abandonOperation)
  {
    long connectionID = abandonOperation.getConnectionID();
    if (connectionID < 0 && suppressInternalOperations)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);
    buffer.append("[");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("]");
    buffer.append(" ABANDON conn=");
    buffer.append(connectionID);
    buffer.append(" op=");
    buffer.append(abandonOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(abandonOperation.getMessageID());
    buffer.append(" result=");
    buffer.append(abandonOperation.getResultCode());

    StringBuilder msg = abandonOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" message=\"");
      buffer.append(msg);
      buffer.append("\"");
    }

    msg = abandonOperation.getAdditionalLogMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" additionalInfo=\"");
      buffer.append(msg);
      buffer.append("\"");
    }

    buffer.append(" etime=");
    buffer.append(abandonOperation.getProcessingTime());

    writer.writeRecord(buffer.toString());
  }


  /**
   * Writes a message to the access logger with information about the add
   * request associated with the provided add operation.
   *
   * @param  addOperation  The add operation containing the information to use
   *                       to log the add request.
   */
  public void logAddRequest(AddOperation addOperation)
  {
    long connectionID = addOperation.getConnectionID();
    if (connectionID < 0 && suppressInternalOperations)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);
    buffer.append("[");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("]");
    buffer.append(" ADD conn=");
    buffer.append(connectionID);
    buffer.append(" op=");
    buffer.append(addOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(addOperation.getMessageID());
    buffer.append(" dn=\"");
    addOperation.getRawEntryDN().toString(buffer);
    buffer.append("\"");

    writer.writeRecord(buffer.toString());
  }


  /**
   * Writes a message to the access logger with information about the add
   * response associated with the provided add operation.
   *
   * @param  addOperation  The add operation containing the information to use
   *                       to log the add response.
   */
  public void logAddResponse(AddOperation addOperation)
  {
    long connectionID = addOperation.getConnectionID();
    if (connectionID < 0 && suppressInternalOperations)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);
    buffer.append("[");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("]");
    buffer.append(" ADD conn=");
    buffer.append(connectionID);
    buffer.append(" op=");
    buffer.append(addOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(addOperation.getMessageID());
    buffer.append(" result=\"");
    buffer.append(addOperation.getResultCode());

    StringBuilder msg = addOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append("\" message=\"");
      buffer.append(msg);
    }

    msg = addOperation.getAdditionalLogMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append("\" additionalInfo=\"");
      buffer.append(msg);
    }

    DN proxiedAuthDN = addOperation.getProxiedAuthorizationDN();
    if (proxiedAuthDN != null)
    {
      buffer.append("\" authzDN=\"");
      proxiedAuthDN.toString(buffer);
    }

    buffer.append("\" etime=");
    buffer.append(addOperation.getProcessingTime());

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the bind
   * request associated with the provided bind operation.
   *
   * @param  bindOperation  The bind operation with the information to use
   *                        to log the bind request.
   */
  public void logBindRequest(BindOperation bindOperation)
  {
    long connectionID = bindOperation.getConnectionID();
    if (connectionID < 0 && suppressInternalOperations)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);
    buffer.append("[");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("]");
    buffer.append(" BIND conn=");
    buffer.append(connectionID);
    buffer.append(" op=");
    buffer.append(bindOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(bindOperation.getMessageID());

    switch (bindOperation.getAuthenticationType())
    {
      case SIMPLE:
        buffer.append(" type=SIMPLE");
        break;
      case SASL:
        buffer.append(" type=SASL mechanism=");
        buffer.append(bindOperation.getSASLMechanism());
        break;
      default:
        buffer.append(" type=");
        buffer.append(bindOperation.getAuthenticationType());
        break;
    }

    buffer.append(" dn=\"");
    bindOperation.getRawBindDN().toString(buffer);
    buffer.append("\"");

    writer.writeRecord(buffer.toString());
  }


  /**
   * Writes a message to the access logger with information about the bind
   * response associated with the provided bind operation.
   *
   * @param  bindOperation  The bind operation containing the information to use
   *                        to log the bind response.
   */
  public void logBindResponse(BindOperation bindOperation)
  {
    long connectionID = bindOperation.getConnectionID();
    if (connectionID < 0 && suppressInternalOperations)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);
    buffer.append("[");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("]");
    buffer.append(" BIND conn=");
    buffer.append(connectionID);
    buffer.append(" op=");
    buffer.append(bindOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(bindOperation.getMessageID());
    buffer.append(" result=\"");
    buffer.append(bindOperation.getResultCode());

    StringBuilder msg = bindOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append("\" message=\"");
      buffer.append(msg);
    }

    int failureID = bindOperation.getAuthFailureID();
    if (failureID > 0)
    {
      buffer.append("\" authFailureID=");
      buffer.append(failureID);
      buffer.append(" authFailureReason=\"");

      String failureReason = bindOperation.getAuthFailureReason();
      if (failureReason != null)
      {
        buffer.append(failureReason);
      }
    }

    msg = bindOperation.getAdditionalLogMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append("\" additionalInfo=\"");
      buffer.append(msg);
    }

    if (bindOperation.getResultCode() == ResultCode.SUCCESS)
    {
      AuthenticationInfo authInfo = bindOperation.getAuthenticationInfo();
      if (authInfo != null)
      {
        DN authDN = authInfo.getAuthenticationDN();
        buffer.append("\" authDN=\"");
        if (authDN != null)
        {
          authDN.toString(buffer);

          DN authzDN = authInfo.getAuthorizationDN();
          if (! authDN.equals(authzDN))
          {
            buffer.append("\" authzDN=\"");
            if (authzDN != null)
            {
              authzDN.toString(buffer);
            }
          }
        }
      }
    }

    buffer.append("\" etime=");
    buffer.append(bindOperation.getProcessingTime());

    writer.writeRecord(buffer.toString());
  }


  /**
   * Writes a message to the access logger with information about the compare
   * request associated with the provided compare operation.
   *
   * @param  compareOperation  The compare operation containing the information
   *                           to use to log the compare request.
   */
  public void logCompareRequest(CompareOperation compareOperation)
  {
    long connectionID = compareOperation.getConnectionID();
    if (connectionID < 0 && suppressInternalOperations)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);
    buffer.append("[");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("]");
    buffer.append(" COMPARE conn=");
    buffer.append(connectionID);
    buffer.append(" op=");
    buffer.append(compareOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(compareOperation.getMessageID());
    buffer.append(" dn=\"");
    compareOperation.getRawEntryDN().toString(buffer);
    buffer.append("\" attr=");
    buffer.append(compareOperation.getAttributeType());

    writer.writeRecord(buffer.toString());
  }


  /**
   * Writes a message to the access logger with information about the compare
   * response associated with the provided compare operation.
   *
   * @param  compareOperation  The compare operation containing the information
   *                           to use to log the compare response.
   */
  public void logCompareResponse(CompareOperation compareOperation)
  {
    long connectionID = compareOperation.getConnectionID();
    if (connectionID < 0 && suppressInternalOperations)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);
    buffer.append("[");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("]");
    buffer.append(" COMPARE conn=");
    buffer.append(connectionID);
    buffer.append(" op=");
    buffer.append(compareOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(compareOperation.getMessageID());
    buffer.append(" result=\"");
    buffer.append(compareOperation.getResultCode());

    StringBuilder msg = compareOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append("\" message=\"");
      buffer.append(msg);
    }

    msg = compareOperation.getAdditionalLogMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append("\" additionalInfo=\"");
      buffer.append(msg);
    }

    DN proxiedAuthDN = compareOperation.getProxiedAuthorizationDN();
    if (proxiedAuthDN != null)
    {
      buffer.append("\" authzDN=\"");
      proxiedAuthDN.toString(buffer);
    }

    buffer.append("\" etime=");
    buffer.append(compareOperation.getProcessingTime());

    writer.writeRecord(buffer.toString());
  }


  /**
   * Writes a message to the access logger with information about the delete
   * request associated with the provided delete operation.
   *
   * @param  deleteOperation  The delete operation with the information to
   *                          use to log the delete request.
   */
  public void logDeleteRequest(DeleteOperation deleteOperation)
  {
    long connectionID = deleteOperation.getConnectionID();
    if (connectionID < 0 && suppressInternalOperations)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);
    buffer.append("[");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("]");
    buffer.append(" DELETE conn=");
    buffer.append(connectionID);
    buffer.append(" op=");
    buffer.append(deleteOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(deleteOperation.getMessageID());
    buffer.append(" dn=\"");
    deleteOperation.getRawEntryDN().toString(buffer);
    buffer.append("\"");


    writer.writeRecord(buffer.toString());
  }


  /**
   * Writes a message to the access logger with information about the delete
   * response associated with the provided delete operation.
   *
   * @param  deleteOperation The delete operation containing the information to
   *                           use to log the delete response.
   */
  public void logDeleteResponse(DeleteOperation deleteOperation)
  {
    long connectionID = deleteOperation.getConnectionID();
    if (connectionID < 0 && suppressInternalOperations)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);
    buffer.append("[");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("]");
    buffer.append(" DELETE conn=");
    buffer.append(connectionID);
    buffer.append(" op=");
    buffer.append(deleteOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(deleteOperation.getMessageID());
    buffer.append(" result=\"");
    buffer.append(deleteOperation.getResultCode());

    StringBuilder msg = deleteOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append("\" message=\"");
      buffer.append(msg);
    }

    msg = deleteOperation.getAdditionalLogMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append("\" additionalInfo=\"");
      buffer.append(msg);
    }

    DN proxiedAuthDN = deleteOperation.getProxiedAuthorizationDN();
    if (proxiedAuthDN != null)
    {
      buffer.append("\" authzDN=\"");
      proxiedAuthDN.toString(buffer);
    }

    buffer.append("\" etime=");
    buffer.append(deleteOperation.getProcessingTime());

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the extended
   * request associated with the provided extended operation.
   *
   * @param  extendedOperation  The extended operation containing the
   *                            information to use to log the extended request.
   */
  public void logExtendedRequest(ExtendedOperation extendedOperation)
  {
    long connectionID = extendedOperation.getConnectionID();
    if (connectionID < 0 && suppressInternalOperations)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);
    buffer.append("[");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("]");
    buffer.append(" EXTENDED conn=");
    buffer.append(connectionID);
    buffer.append(" op=");
    buffer.append(extendedOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(extendedOperation.getMessageID());
    buffer.append(" oid=\"");
    buffer.append(extendedOperation.getRequestOID());
    buffer.append("\"");

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the extended
   * response associated with the provided extended operation.
   *
   * @param  extendedOperation  The extended operation containing the
   *                            info to use to log the extended response.
   */
  public void logExtendedResponse(ExtendedOperation extendedOperation)
  {
    long connectionID = extendedOperation.getConnectionID();
    if (connectionID < 0 && suppressInternalOperations)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);
    buffer.append("[");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("]");
    buffer.append(" EXTENDED conn=");
    buffer.append(connectionID);
    buffer.append(" op=");
    buffer.append(extendedOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(extendedOperation.getMessageID());

    String oid = extendedOperation.getResponseOID();
    if (oid != null)
    {
      buffer.append(" oid=\"");
      buffer.append(oid);
      buffer.append("\"");
    }

    buffer.append(" result=\"");
    buffer.append(extendedOperation.getResultCode());

    StringBuilder msg = extendedOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append("\" message=\"");
      buffer.append(msg);
    }

    msg = extendedOperation.getAdditionalLogMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append("\" additionalInfo=\"");
      buffer.append(msg);
    }

    buffer.append("\" etime=");
    buffer.append(extendedOperation.getProcessingTime());

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the modify
   * request associated with the provided modify operation.
   *
   * @param  modifyOperation The modify operation containing the information to
   *                         use to log the modify request.
   */
  public void logModifyRequest(ModifyOperation modifyOperation)
  {
    long connectionID = modifyOperation.getConnectionID();
    if (connectionID < 0 && suppressInternalOperations)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);
    buffer.append("[");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("]");
    buffer.append(" MODIFY conn=");
    buffer.append(connectionID);
    buffer.append(" op=");
    buffer.append(modifyOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(modifyOperation.getMessageID());
    buffer.append(" dn=\"");
    modifyOperation.getRawEntryDN().toString(buffer);
    buffer.append("\"");

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the modify
   * response associated with the provided modify operation.
   *
   * @param  modifyOperation The modify operation containing the information to
   *                         use to log the modify response.
   */
  public void logModifyResponse(ModifyOperation modifyOperation)
  {
    long connectionID = modifyOperation.getConnectionID();
    if (connectionID < 0 && suppressInternalOperations)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);
    buffer.append("[");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("]");
    buffer.append(" MODIFY conn=");
    buffer.append(connectionID);
    buffer.append(" op=");
    buffer.append(modifyOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(modifyOperation.getMessageID());
    buffer.append(" result=\"");
    buffer.append(modifyOperation.getResultCode());

    StringBuilder msg = modifyOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append("\" message=\"");
      buffer.append(msg);
    }

    msg = modifyOperation.getAdditionalLogMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append("\" additionalInfo=\"");
      buffer.append(msg);
    }

    DN proxiedAuthDN = modifyOperation.getProxiedAuthorizationDN();
    if (proxiedAuthDN != null)
    {
      buffer.append("\" authzDN=\"");
      proxiedAuthDN.toString(buffer);
    }

    buffer.append("\" etime=");
    buffer.append(modifyOperation.getProcessingTime());

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the modify DN
   * request associated with the provided modify DN operation.
   *
   * @param  modifyDNOperation  The modify DN operation containing the
   *                            info to use to log the modify DN request.
   */
  public void logModifyDNRequest(ModifyDNOperation modifyDNOperation)
  {
    long connectionID = modifyDNOperation.getConnectionID();
    if (connectionID < 0 && suppressInternalOperations)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);
    buffer.append("[");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("]");
    buffer.append(" MODIFYDN conn=");
    buffer.append(connectionID);
    buffer.append(" op=");
    buffer.append(modifyDNOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(modifyDNOperation.getMessageID());
    buffer.append(" dn=\"");
    modifyDNOperation.getRawEntryDN().toString(buffer);
    buffer.append("\" newRDN=\"");
    modifyDNOperation.getRawNewRDN().toString(buffer);
    buffer.append("\" deleteOldRDN=");
    buffer.append(modifyDNOperation.deleteOldRDN());

    ByteString newSuperior = modifyDNOperation.getRawNewSuperior();
    if (newSuperior != null)
    {
      buffer.append(" newSuperior=\"");
      newSuperior.toString(buffer);
    }

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the modify DN
   * response associated with the provided modify DN operation.
   *
   * @param  modifyDNOperation  The modify DN operation containing the
   *                            information to use to log the modify DN
   *                            response.
   */
  public void logModifyDNResponse(ModifyDNOperation modifyDNOperation)
  {
    long connectionID = modifyDNOperation.getConnectionID();
    if (connectionID < 0 && suppressInternalOperations)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);
    buffer.append("[");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("]");
    buffer.append(" MODIFYDN conn=");
    buffer.append(connectionID);
    buffer.append(" op=");
    buffer.append(modifyDNOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(modifyDNOperation.getMessageID());
    buffer.append(" result=\"");
    buffer.append(modifyDNOperation.getResultCode());

    StringBuilder msg = modifyDNOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append("\" message=\"");
      buffer.append(msg);
    }

    msg = modifyDNOperation.getAdditionalLogMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append("\" additionalInfo=\"");
      buffer.append(msg);
    }

    DN proxiedAuthDN = modifyDNOperation.getProxiedAuthorizationDN();
    if (proxiedAuthDN != null)
    {
      buffer.append("\" authzDN=\"");
      proxiedAuthDN.toString(buffer);
    }

    buffer.append("\" etime=");
    buffer.append(modifyDNOperation.getProcessingTime());

    writer.writeRecord(buffer.toString());
  }


  /**
   * Writes a message to the access logger with information about the search
   * request associated with the provided search operation.
   *
   * @param  searchOperation  The search operation containing the info to
   *                          use to log the search request.
   */
  public void logSearchRequest(SearchOperation searchOperation)
  {
    long connectionID = searchOperation.getConnectionID();
    if (connectionID < 0 && suppressInternalOperations)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);
    buffer.append("[");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("]");
    buffer.append(" SEARCH conn=");
    buffer.append(connectionID);
    buffer.append(" op=");
    buffer.append(searchOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(searchOperation.getMessageID());
    buffer.append(" base=\"");
    searchOperation.getRawBaseDN().toString(buffer);
    buffer.append("\" scope=");
    buffer.append(searchOperation.getScope());
    buffer.append(" filter=\"");
    searchOperation.getRawFilter().toString(buffer);

    LinkedHashSet<String> attrs = searchOperation.getAttributes();
    if ((attrs == null) || attrs.isEmpty())
    {
      buffer.append("\"");
    }
    else
    {
      buffer.append("\" attrs=\"");

      Iterator<String> iterator = attrs.iterator();
      buffer.append(iterator.next());
      while (iterator.hasNext())
      {
        buffer.append(",");
        buffer.append(iterator.next());
      }

      buffer.append("\"");
    }

    writer.writeRecord(buffer.toString());
  }


  /**
   * Writes a message to the access logger with information about the search
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
    // NYI
  }


  /**
   * Writes a message to the access logger with information about the search
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
    // NYI
  }



  /**
   * Writes a message to the access logger with information about the
   * completion of the provided search operation.
   *
   * @param  searchOperation  The search operation containing the information
   *                          to use to log the search result done message.
   */
  public void logSearchResultDone(SearchOperation searchOperation)
  {
    long connectionID = searchOperation.getConnectionID();
    if (connectionID < 0 && suppressInternalOperations)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);
    buffer.append("[");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("]");
    buffer.append(" SEARCH conn=");
    buffer.append(connectionID);
    buffer.append(" op=");
    buffer.append(searchOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(searchOperation.getMessageID());
    buffer.append(" result=\"");
    buffer.append(searchOperation.getResultCode());

    StringBuilder msg = searchOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append("\" message=\"");
      buffer.append(msg);
    }

    buffer.append("\" nentries=");
    buffer.append(searchOperation.getEntriesSent());

    msg = searchOperation.getAdditionalLogMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" additionalInfo=\"");
      buffer.append(msg);
      buffer.append("\"");
    }

    DN proxiedAuthDN = searchOperation.getProxiedAuthorizationDN();
    if (proxiedAuthDN != null)
    {
      buffer.append("\" authzDN=\"");
      proxiedAuthDN.toString(buffer);
    }

    buffer.append(" etime=");
    buffer.append(searchOperation.getProcessingTime());

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the unbind
   * request associated with the provided unbind operation.
   *
   * @param  unbindOperation  The unbind operation containing the info to
   *                          use to log the unbind request.
   */
  public void logUnbind(UnbindOperation unbindOperation)
  {
    long connectionID = unbindOperation.getConnectionID();
    if (connectionID < 0 && suppressInternalOperations)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);
    buffer.append("[");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("]");
    buffer.append(" UNBIND conn=");
    buffer.append(connectionID);
    buffer.append(" op=");
    buffer.append(unbindOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(unbindOperation.getMessageID());

    writer.writeRecord(buffer.toString());
  }
}

