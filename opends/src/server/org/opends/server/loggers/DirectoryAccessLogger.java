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
import java.util.LinkedHashSet;
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
import org.opends.server.types.ByteString;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.DN;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.messages.LoggerMessages.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;



/**
 * This class provides the implementation of the access logger used by
 * the directory server.
 */
public class DirectoryAccessLogger extends AccessLogger
       implements ConfigurableComponent
{
  private static final int DEFAULT_TIME_INTERVAL = 30000;
  private static final int DEFAULT_BUFFER_SIZE = 65536;
  private boolean suppressInternalOps = true;
  private Logger accessLogger = null;
  private String changedLogFileName = null;
  private DirectoryFileHandler fileHandler = null;

  // The DN of the config entry this component is associated with.
  private DN configDN;


  /**
   * Initializes this access logger based on the information in the provided
   * configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this access logger.
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
   * Closes this access logger and releases any resources it might have held.
   */
  public void closeAccessLogger()
  {
    fileHandler.close();
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
    if(connectionID < 0 && suppressInternalOps)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);
    buffer.append("CONNECT conn=");
    buffer.append(connectionID);
    buffer.append(" from=");
    buffer.append(clientConnection.getClientAddress());
    buffer.append(" to=");
    buffer.append(clientConnection.getServerAddress());
    buffer.append(" protocol=");
    buffer.append(clientConnection.getProtocol());

    accessLogger.log(DirectoryLogLevel.INFORMATIONAL, buffer.toString());

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
    if(connectionID < 0 && suppressInternalOps)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);

    buffer.append("DISCONNECT conn=");
    buffer.append(connectionID);
    buffer.append(" reason=\"");
    buffer.append(disconnectReason);

    if (message != null)
    {
      buffer.append("\" msg=\"");
      buffer.append(message);
    }

    buffer.append("\"");

    accessLogger.log(DirectoryLogLevel.INFORMATIONAL, buffer.toString());
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
    if(connectionID < 0 && suppressInternalOps)
    {
      return;
    }

    StringBuilder buffer = new StringBuilder(50);

    buffer.append("ABANDON conn=");
    buffer.append(connectionID);
    buffer.append(" op=");
    buffer.append(abandonOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(abandonOperation.getMessageID());
    buffer.append(" idToAbandon=");
    buffer.append(abandonOperation.getIDToAbandon());

    accessLogger.log(DirectoryLogLevel.INFORMATIONAL, buffer.toString());
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
    if(connectionID < 0 && suppressInternalOps)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);

    buffer.append("ABANDON conn=");
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

    buffer.append(" etime=");
    buffer.append(abandonOperation.getProcessingTime());

    accessLogger.log(DirectoryLogLevel.INFORMATIONAL, buffer.toString());
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
    if(connectionID < 0 && suppressInternalOps)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);

    buffer.append("ADD conn=");
    buffer.append(connectionID);
    buffer.append(" op=");
    buffer.append(addOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(addOperation.getMessageID());
    buffer.append(" dn=\"");
    addOperation.getRawEntryDN().toString(buffer);
    buffer.append("\"");

    accessLogger.log(DirectoryLogLevel.INFORMATIONAL, buffer.toString());
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
    if(connectionID < 0 && suppressInternalOps)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);

    buffer.append("ADD conn=");
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

    buffer.append("\" etime=");
    buffer.append(addOperation.getProcessingTime());

    accessLogger.log(DirectoryLogLevel.INFORMATIONAL, buffer.toString());
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
    if(connectionID < 0 && suppressInternalOps)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);

    buffer.append("BIND conn=");
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

    accessLogger.log(DirectoryLogLevel.INFORMATIONAL, buffer.toString());
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
    if(connectionID < 0 && suppressInternalOps)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);

    buffer.append("BIND conn=");
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

    buffer.append("\" etime=");
    buffer.append(bindOperation.getProcessingTime());

    accessLogger.log(DirectoryLogLevel.INFORMATIONAL, buffer.toString());
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
    if(connectionID < 0 && suppressInternalOps)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);

    buffer.append("COMPARE conn=");
    buffer.append(connectionID);
    buffer.append(" op=");
    buffer.append(compareOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(compareOperation.getMessageID());
    buffer.append(" dn=\"");
    compareOperation.getRawEntryDN().toString(buffer);
    buffer.append("\" attr=");
    buffer.append(compareOperation.getAttributeType());

    accessLogger.log(DirectoryLogLevel.INFORMATIONAL, buffer.toString());
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
    if(connectionID < 0 && suppressInternalOps)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);

    buffer.append("COMPARE conn=");
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

    buffer.append("\" etime=");
    buffer.append(compareOperation.getProcessingTime());

    accessLogger.log(DirectoryLogLevel.INFORMATIONAL, buffer.toString());
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
    if(connectionID < 0 && suppressInternalOps)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);

    buffer.append("DELETE conn=");
    buffer.append(connectionID);
    buffer.append(" op=");
    buffer.append(deleteOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(deleteOperation.getMessageID());
    buffer.append(" dn=\"");
    deleteOperation.getRawEntryDN().toString(buffer);
    buffer.append("\"");


    accessLogger.log(DirectoryLogLevel.INFORMATIONAL, buffer.toString());
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
    if(connectionID < 0 && suppressInternalOps)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);

    buffer.append("DELETE conn=");
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

    buffer.append("\" etime=");
    buffer.append(deleteOperation.getProcessingTime());

    accessLogger.log(DirectoryLogLevel.INFORMATIONAL, buffer.toString());
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
    if(connectionID < 0 && suppressInternalOps)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);

    buffer.append("EXTENDED conn=");
    buffer.append(connectionID);
    buffer.append(" op=");
    buffer.append(extendedOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(extendedOperation.getMessageID());
    buffer.append(" oid=\"");
    buffer.append(extendedOperation.getRequestOID());
    buffer.append("\"");

    accessLogger.log(DirectoryLogLevel.INFORMATIONAL, buffer.toString());
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
    if(connectionID < 0 && suppressInternalOps)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);

    buffer.append("EXTENDED conn=");
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

    buffer.append("\" etime=");
    buffer.append(extendedOperation.getProcessingTime());

    accessLogger.log(DirectoryLogLevel.INFORMATIONAL, buffer.toString());
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
    if(connectionID < 0 && suppressInternalOps)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);

    buffer.append("MODIFY conn=");
    buffer.append(connectionID);
    buffer.append(" op=");
    buffer.append(modifyOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(modifyOperation.getMessageID());
    buffer.append(" dn=\"");
    modifyOperation.getRawEntryDN().toString(buffer);
    buffer.append("\"");

    accessLogger.log(DirectoryLogLevel.INFORMATIONAL, buffer.toString());
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
    if(connectionID < 0 && suppressInternalOps)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);

    buffer.append("MODIFY conn=");
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

    buffer.append("\" etime=");
    buffer.append(modifyOperation.getProcessingTime());

    accessLogger.log(DirectoryLogLevel.INFORMATIONAL, buffer.toString());
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
    if(connectionID < 0 && suppressInternalOps)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);

    buffer.append("MODIFYDN conn=");
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

    accessLogger.log(DirectoryLogLevel.INFORMATIONAL, buffer.toString());
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
    if(connectionID < 0 && suppressInternalOps)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);

    buffer.append("MODIFYDN conn=");
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

    buffer.append("\" etime=");
    buffer.append(modifyDNOperation.getProcessingTime());

    accessLogger.log(DirectoryLogLevel.INFORMATIONAL, buffer.toString());
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
    if(connectionID < 0 && suppressInternalOps)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);

    buffer.append("SEARCH conn=");
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

    accessLogger.log(DirectoryLogLevel.INFORMATIONAL, buffer.toString());
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
    if(connectionID < 0 && suppressInternalOps)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);

    buffer.append("SEARCH conn=");
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
    buffer.append(" etime=");
    buffer.append(searchOperation.getProcessingTime());

    accessLogger.log(DirectoryLogLevel.INFORMATIONAL, buffer.toString());
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
    if(connectionID < 0 && suppressInternalOps)
    {
      return;
    }
    StringBuilder buffer = new StringBuilder(50);

    buffer.append("UNBIND conn=");
    buffer.append(connectionID);
    buffer.append(" op=");
    buffer.append(unbindOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(unbindOperation.getMessageID());

    accessLogger.log(DirectoryLogLevel.INFORMATIONAL, buffer.toString());
  }



  /**
   * Indicates whether the provided object is equal to this access logger.
   *
   * @param  obj  The object for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided object is equal
   *          to this access logger, or <CODE>false</CODE> if not.
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

    return accessLogger.equals(obj);
  }



  /**
   * Retrieves the hash code for this access logger.
   *
   * @return  The hash code for this access logger.
   */
  public int hashCode()
  {
    return accessLogger.hashCode();
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
   *                          about any actions performed.
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
   * @param logFileName The name of the log file to write to.
   * @param configEntry The configuration entry with the information to use to
   *                    initialize this logger.
   *
   * @throws ConfigException   If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   */
  private void initializeAccessLogger(String logFileName,
    ConfigEntry configEntry) throws ConfigException
  {
    accessLogger =
      Logger.getLogger("org.opends.server.loggers.DirectoryAccessLogger");
    accessLogger.setLevel(Level.ALL);

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
      fileHandler.setFormatter(new DirectoryFileFormatter(false));
      accessLogger.addHandler(fileHandler);

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

      LoggerThread lt = new LoggerThread("AccessLogger Thread",
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

