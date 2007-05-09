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

import org.opends.server.admin.std.server.FileBasedAccessLogPublisherCfg;
import org.opends.server.admin.server.ConfigurationChangeListener;
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
import org.opends.server.util.Base64;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.TimeThread;

import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.types.ResultCode.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;


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
  @Override()
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
  @Override()
  public void close()
  {
    writer.shutdown();
    currentConfig.removeFileBasedAccessChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void logConnect(ClientConnection clientConnection)
  {
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void logDisconnect(ClientConnection clientConnection,
                            DisconnectReason disconnectReason,
                            String message)
  {
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void logAbandonRequest(AbandonOperation abandonOperation)
  {
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void logAbandonResult(AbandonOperation abandonOperation)
  {
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void logAddRequest(AddOperation addOperation)
  {
  }



  /**
   * {@inheritDoc}
   */
  @Override()
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
      Entry entry = addOperation.getEntryToAdd();

      StringBuilder buffer = new StringBuilder(50);
      buffer.append("# ");
      buffer.append(TimeThread.getLocalTime());
      buffer.append(EOL);

      buffer.append("dn:");
      encodeValue(entry.getDN().toString(), buffer);
      buffer.append(EOL);

      buffer.append("changetype: add");
      buffer.append(EOL);

      for (String ocName : entry.getObjectClasses().values())
      {
        buffer.append("objectClass: ");
        buffer.append(ocName);
        buffer.append(EOL);
      }

      for (List<Attribute> attrList : entry.getUserAttributes().values())
      {
        for (Attribute a : attrList)
        {
          for (AttributeValue v : a.getValues())
          {
            buffer.append(a.getName());
            buffer.append(":");
            encodeValue(v.getValue(), buffer);
            buffer.append(EOL);
          }
        }
      }

      for (List<Attribute> attrList : entry.getOperationalAttributes().values())
      {
        for (Attribute a : attrList)
        {
          for (AttributeValue v : a.getValues())
          {
            buffer.append(a.getName());
            buffer.append(":");
            encodeValue(v.getValue(), buffer);
            buffer.append(EOL);
          }
        }
      }

      writer.writeRecord(buffer.toString());
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void logBindRequest(BindOperation bindOperation)
  {
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void logBindResponse(BindOperation bindOperation)
  {
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void logCompareRequest(CompareOperation compareOperation)
  {
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void logCompareResponse(CompareOperation compareOperation)
  {
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void logDeleteRequest(DeleteOperation deleteOperation)
  {
  }



  /**
   * {@inheritDoc}
   */
  @Override()
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
      Entry entry = deleteOperation.getEntryToDelete();

      StringBuilder buffer = new StringBuilder(50);
      buffer.append("# ");
      buffer.append(TimeThread.getLocalTime());
      buffer.append(EOL);

      buffer.append("dn:");
      encodeValue(entry.getDN().toString(), buffer);
      buffer.append(EOL);

      buffer.append("changetype: delete");
      buffer.append(EOL);

      writer.writeRecord(buffer.toString());
    }

  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void logExtendedRequest(ExtendedOperation extendedOperation)
  {
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void logExtendedResponse(ExtendedOperation extendedOperation)
  {
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void logModifyRequest(ModifyOperation modifyOperation)
  {
  }



  /**
   * {@inheritDoc}
   */
  @Override()
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
      Entry entry = modifyOperation.getModifiedEntry();

      StringBuilder buffer = new StringBuilder(50);
      buffer.append("# ");
      buffer.append(TimeThread.getLocalTime());
      buffer.append(EOL);

      buffer.append("dn:");
      encodeValue(entry.getDN().toString(), buffer);
      buffer.append(EOL);

      buffer.append("changetype: modify");
      buffer.append(EOL);

      boolean first = true;
      for (Modification mod : modifyOperation.getModifications())
      {
        if (first)
        {
          first = false;
        }
        else
        {
          buffer.append("-");
          buffer.append(EOL);
        }

        switch (mod.getModificationType())
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
          case INCREMENT:
            buffer.append("increment: ");
            break;
          default:
            continue;
        }

        Attribute a = mod.getAttribute();
        buffer.append(a.getName());
        buffer.append(EOL);

        for (AttributeValue v : a.getValues())
        {
          buffer.append(a.getName());
          buffer.append(":");
          encodeValue(v.getValue(), buffer);
          buffer.append(EOL);
        }
      }

      writer.writeRecord(buffer.toString());
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void logModifyDNRequest(ModifyDNOperation modifyDNOperation)
  {
  }



  /**
   * {@inheritDoc}
   */
  @Override()
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
      buffer.append("# ");
      buffer.append(TimeThread.getLocalTime());
      buffer.append(EOL);

      buffer.append("dn:");
      encodeValue(modifyDNOperation.getEntryDN().toString(), buffer);
      buffer.append(EOL);

      buffer.append("changetype: moddn");
      buffer.append(EOL);

      buffer.append("newrdn:");
      encodeValue(modifyDNOperation.getNewRDN().toString(), buffer);
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

      DN newSuperior = modifyDNOperation.getNewSuperior();
      if (newSuperior != null)
      {
        buffer.append("newsuperior:");
        encodeValue(newSuperior.toString(), buffer);
        buffer.append(EOL);
      }

      writer.writeRecord(buffer.toString());
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void logSearchRequest(SearchOperation searchOperation)
  {
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void logSearchResultEntry(SearchOperation searchOperation,
                                     SearchResultEntry searchEntry)
  {
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void logSearchResultReference(SearchOperation searchOperation,
                            SearchResultReference searchReference)
  {
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void logSearchResultDone(SearchOperation searchOperation)
  {
  }



  /**
   * {@inheritDoc}
   */
  @Override()
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



  /**
   * Appends the appropriately-encoded attribute value to the provided buffer.
   *
   * @param  str     The string containing the value to append.
   * @param  buffer  The buffer to which to append the value.
   */
  private void encodeValue(String str, StringBuilder buffer)
  {
      if(StaticUtils.needsBase64Encoding(str))
      {
        buffer.append(": ");
        buffer.append(Base64.encode(getBytes(str)));
      } else
      {
        buffer.append(" ");
        buffer.append(str);
      }
  }
}

