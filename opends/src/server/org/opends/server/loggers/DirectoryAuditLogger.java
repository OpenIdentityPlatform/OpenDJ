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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opends.server.api.AccessLogger;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.config.BooleanConfigAttribute;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.StringConfigAttribute;
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
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.DN;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.util.Base64;
import org.opends.server.util.StaticUtils;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.messages.LoggerMessages.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.types.ResultCode.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class provides the implementation of the audit logger used by
 * the directory server.
 */
public class DirectoryAuditLogger extends AccessLogger
       implements ConfigurableComponent
{
  private static final int DEFAULT_TIME_INTERVAL = 30000;
  private static final int DEFAULT_BUFFER_SIZE = 0;
  private boolean suppressInternalOps = true;
  private Logger auditLogger = null;
  private String changedLogFileName = null;
  private DirectoryFileHandler fileHandler = null;

  // The DN of the config entry this component is associated with.
  private DN configDN;


  /**
   * Initializes this audit logger based on the information in the provided
   * configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this audit logger.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
  */
  public void initializeAccessLogger(ConfigEntry configEntry)
         throws ConfigException
  {
    configDN = configEntry.getDN();

    // FIXME - read the logger name from the config
    StringConfigAttribute logFileStub =
                  new StringConfigAttribute(ATTR_LOGGER_FILE,
                  getMessage(MSGID_CONFIG_LOGGER_DESCRIPTION_CLASS_NAME),
                  true, false, true);
    StringConfigAttribute logFileNameAttr = (StringConfigAttribute)
                  configEntry.getConfigAttribute(logFileStub);

    if(logFileNameAttr == null)
    {
      int msgID = MSGID_CONFIG_LOGGER_NO_FILE_NAME;
      String message = getMessage(msgID, configEntry.getDN().toString());
      throw new ConfigException(msgID, message);
    }
    initializeAccessLogger(logFileNameAttr.activeValue(), configEntry);

  }


  /**
   * Closes this audit logger and releases any resources it might have held.
   */
  public void closeAccessLogger()
  {
    fileHandler.close();
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
    if(connectionID < 0 && suppressInternalOps)
    {
      return;
    }
    ResultCode code = addOperation.getResultCode();

    if(code == SUCCESS)
    {
      StringBuilder buffer = new StringBuilder(50);

      buffer.append("dn:");
      ByteString dnString = addOperation.getRawEntryDN();
      encodeValue(dnString, buffer);

      buffer.append(EOL);
      buffer.append("changetype: add");
      buffer.append(EOL);
      List<LDAPAttribute> rawAttributes = addOperation.getRawAttributes();
      for(LDAPAttribute attr : rawAttributes)
      {
        buffer.append(attr.getAttributeType());
        buffer.append(":");
        List<ASN1OctetString> values = attr.getValues();
        if (! values.isEmpty())
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

      auditLogger.log(DirectoryLogLevel.INFORMATIONAL, buffer.toString());
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
    if(connectionID < 0 && suppressInternalOps)
    {
      return;
    }
    ResultCode code = deleteOperation.getResultCode();

    if(code == SUCCESS)
    {
      StringBuilder buffer = new StringBuilder(50);
      buffer.append("dn:");
      ByteString dnString = deleteOperation.getRawEntryDN();
      encodeValue(dnString, buffer);
      buffer.append(EOL);
      buffer.append("changetype: delete");
      buffer.append(EOL);
      buffer.append(EOL);

      auditLogger.log(DirectoryLogLevel.INFORMATIONAL, buffer.toString());
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
    if(connectionID < 0 && suppressInternalOps)
    {
      return;
    }
    ResultCode code = modifyOperation.getResultCode();

    if(code == SUCCESS)
    {
      StringBuilder buffer = new StringBuilder(50);
      buffer.append("dn:");
      ByteString dnString = modifyOperation.getRawEntryDN();
      encodeValue(dnString, buffer);
      buffer.append(EOL);
      buffer.append("changetype: modify");
      buffer.append(EOL);
      List<LDAPModification> modifications =
           modifyOperation.getRawModifications();
      for(LDAPModification modification : modifications)
      {
        ModificationType modType = modification.getModificationType();
        LDAPAttribute attr = modification.getAttribute();
        switch(modType)
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
        if (! values.isEmpty())
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

      auditLogger.log(DirectoryLogLevel.INFORMATIONAL, buffer.toString());
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
    if(connectionID < 0 && suppressInternalOps)
    {
      return;
    }
    ResultCode code = modifyDNOperation.getResultCode();

    if(code == SUCCESS)
    {
      StringBuilder buffer = new StringBuilder(50);
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
      if(modifyDNOperation.deleteOldRDN())
      {
        buffer.append("1");
      } else
      {
        buffer.append("0");
      }
      buffer.append(EOL);
      if(modifyDNOperation.getRawNewSuperior() != null)
      {
        buffer.append("newsuperior: ");
        ByteString newSuperior = modifyDNOperation.getRawNewSuperior();
        encodeValue(newSuperior, buffer);
        buffer.append(EOL);
      }

      buffer.append(EOL);

      auditLogger.log(DirectoryLogLevel.INFORMATIONAL, buffer.toString());
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
   * Indicates whether the provided object is equal to this audit logger.
   *
   * @param  obj  The object for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided object is equal
   *          to this audit logger, or <CODE>false</CODE> if not.
   */
  public boolean equals(Object obj)
  {
    if(this == obj) {
      return true;
    }

    if((obj == null) || (obj.getClass() != this.getClass()))
    {
      return false;
    }

    return auditLogger.equals(obj);
  }



  /**
   * Retrieves the hash code for this audit logger.
   *
   * @return  The hash code for this audit logger.
   */
  public int hashCode()
  {
    return auditLogger.hashCode();
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
   * Retrieves the DN of the configuration entry with which this component is
   * associated.
   *
   * @return  The DN of the configuration entry with which this component is
   *          associated.
   */
  public DN getConfigurableComponentEntryDN()
  {
    return configDN;
  }



  /**
   * Retrieves the set of configuration attributes that are associated with this
   * configurable component.
   *
   * @return  The set of configuration attributes that are associated with this
   *          configurable component.
   */
  public List<ConfigAttribute> getConfigurationAttributes()
  {
    // NYI
    return null;
  }


  /**
   * Indicates whether the configuration entry that will result from a proposed
   * modification is acceptable to this change listener.
   *
   * @param  configEntry         The configuration entry that will result from
   *                             the requested update.
   * @param  unacceptableReasons  A buffer to which this method can append a
   *                             human-readable message explaining why the
   *                             proposed change is not acceptable.
   *
   * @return  <CODE>true</CODE> if the proposed entry contains an acceptable
   *          configuration, or <CODE>false</CODE> if it does not.
   */
  public boolean hasAcceptableConfiguration(ConfigEntry configEntry,
                                          List<String> unacceptableReasons)
  {
    try
    {
      StringConfigAttribute logFileStub =
           new StringConfigAttribute(ATTR_LOGGER_FILE,
                    getMessage(MSGID_CONFIG_LOGGER_DESCRIPTION_CLASS_NAME),
                    true, false, true);
      StringConfigAttribute logFileNameAttr = (StringConfigAttribute)
           configEntry.getConfigAttribute(logFileStub);

      if(logFileNameAttr == null)
      {
        int msgID = MSGID_CONFIG_LOGGER_NO_FILE_NAME;
        String message = getMessage(msgID, configEntry.getDN().toString());
        unacceptableReasons.add(message);
        return false;
      }
      changedLogFileName = logFileNameAttr.pendingValue();
    } catch (ConfigException ce)
    {
      int msgID   = MSGID_CONFIG_LOGGER_INVALID_ACCESS_LOGGER_CLASS;
      String message = getMessage(msgID, this.getClass().getName(),
                                  configEntry.getDN().toString(),
                                  String.valueOf(ce));
      unacceptableReasons.add(message);
      return false;
    }

    return true;
  }



  /**
   * Attempts to apply a new configuration to this Directory Server component
   * based on the provided changed entry.
   *
   * @param  configEntry      The configuration entry that containing the
   *                          updated configuration for this component.
   * @param  detailedResults  Indicates whether to provide detailed information
   *                          about any changes made.
   *
   * @return  Information about the result of processing the configuration
   *          change.
   */
  public ConfigChangeResult applyNewConfiguration(ConfigEntry configEntry,
                  boolean detailedResults)
  {
    fileHandler.close();
    // reinitialize the logger.
    try
    {
      initializeAccessLogger(changedLogFileName, configEntry);
    } catch(ConfigException ce)
    {
      // TODO - log the change failure.
      return new ConfigChangeResult(DirectoryServer.getServerErrorResultCode(),
                                    false);
    }

    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }


  /**
   * Initialize the JDK logger an associate a file handler with the
   * specified file name with it.
   *
   * @param  logFileName  The name of the log file to write to.
   * @param  configEntry  The configuration entry with the information to use to
   *                      initialize this logger.
   *
   * @throws ConfigException   If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   */
  private void initializeAccessLogger(String logFileName,
    ConfigEntry configEntry) throws ConfigException
  {
    auditLogger =
      Logger.getLogger("org.opends.server.loggers.DirectoryAuditLogger");
    auditLogger.setLevel(Level.ALL);

    File logFile = new File(logFileName);
    if(!logFile.isAbsolute())
    {
      logFile = new File (DirectoryServer.getServerRoot() + File.separator +
                          logFileName);
    }

    BooleanConfigAttribute enabledAttr;
    try
    {
      BooleanConfigAttribute enabledStub =
           new BooleanConfigAttribute(ATTR_LOGGER_SUPPRESS_INTERNAL_OPERATIONS,
                   getMessage(MSGID_CONFIG_LOGGER_SUPPRESS_INTERNAL_OPERATIONS),
                               false);
      enabledAttr = (BooleanConfigAttribute)
                    configEntry.getConfigAttribute(enabledStub);

      if (enabledAttr != null)
      {
        suppressInternalOps = enabledAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      int msgID = MSGID_CONFIG_LOGGER_INVALID_SUPPRESS_INT_OPERATION_VALUE;
      String message = getMessage(msgID, configEntry.getDN().toString(),
                                  String.valueOf(e));
      throw new ConfigException(msgID, message);
    }


    try
    {
      int bufferSize = RotationConfigUtil.getIntegerAttribute(configEntry,
                        ATTR_LOGGER_BUFFER_SIZE, MSGID_LOGGER_BUFFER_SIZE);
      if(bufferSize == -1)
      {
        bufferSize = DEFAULT_BUFFER_SIZE;
      }
      CopyOnWriteArrayList<RotationPolicy> rp =
        RotationConfigUtil.getRotationPolicies(configEntry);
      fileHandler = new DirectoryFileHandler(configEntry,
                logFile.getAbsolutePath(),
                bufferSize);
      fileHandler.setFormatter(new DirectoryFileFormatter(true));
      auditLogger.addHandler(fileHandler);

      if(rp != null)
      {
        ArrayList<ActionType> actions =
          RotationConfigUtil.getPostRotationActions(configEntry);
        fileHandler.setPostRotationActions(actions);
        for(RotationPolicy rotationPolicy : rp)
        {
          if(rotationPolicy instanceof SizeBasedRotationPolicy)
          {
            long fileSize =
              ((SizeBasedRotationPolicy) rotationPolicy).getMaxFileSize();
            fileHandler.setFileSize(fileSize);
            rp.remove(rotationPolicy);
          }
        }
      }

      CopyOnWriteArrayList<RetentionPolicy> retentionPolicies =
        RotationConfigUtil.getRetentionPolicies(configEntry);

      int threadTimeInterval = RotationConfigUtil.getIntegerAttribute(
                                    configEntry, ATTR_LOGGER_THREAD_INTERVAL,
                                    MSGID_LOGGER_THREAD_INTERVAL);
      if(threadTimeInterval == -1)
      {
        threadTimeInterval = DEFAULT_TIME_INTERVAL;
      }

      LoggerThread lt = new LoggerThread("AuditLogger Thread",
                                         threadTimeInterval, fileHandler, rp,
                                         retentionPolicies);
      lt.start();

    } catch(IOException ioe) {
      int    msgID   = MSGID_LOG_ACCESS_CANNOT_ADD_FILE_HANDLER;
      String message = getMessage(msgID, String.valueOf(ioe));
      throw new ConfigException(msgID, message, ioe);
    }
  }
}

