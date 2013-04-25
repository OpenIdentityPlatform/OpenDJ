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
 *      Portions copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.loggers;
import static org.opends.messages.ConfigMessages.*;

import java.util.Collection;

import org.opends.messages.Message;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.std.meta.AccessLogPublisherCfgDefn;
import org.opends.server.admin.std.server.AccessLogPublisherCfg;
import org.opends.server.api.AccessLogPublisher;
import org.opends.server.api.ClientConnection;
import org.opends.server.core.*;
import org.opends.server.types.*;


/**
 * This class defines the wrapper that will invoke all registered access loggers
 * for each type of request received or response sent.
 */
public class AccessLogger extends AbstractLogger
    <AccessLogPublisher<AccessLogPublisherCfg>, AccessLogPublisherCfg>
{

  private static LoggerStorage
      <AccessLogPublisher<AccessLogPublisherCfg>, AccessLogPublisherCfg>
      loggerStorage = new LoggerStorage
      <AccessLogPublisher<AccessLogPublisherCfg>, AccessLogPublisherCfg>();

  /** The singleton instance of this class for configuration purposes. */
  private static final AccessLogger instance = new AccessLogger();

  /**
   * The constructor for this class.
   */
  public AccessLogger()
  {
    super((Class) AccessLogPublisher.class,
        ERR_CONFIG_LOGGER_INVALID_ACCESS_LOGGER_CLASS);
  }

  /** {@inheritDoc} */
  @Override
  protected ClassPropertyDefinition getJavaClassPropertyDefinition()
  {
    return AccessLogPublisherCfgDefn.getInstance()
        .getJavaClassPropertyDefinition();
  }

  /** {@inheritDoc} */
  @Override
  protected LoggerStorage<AccessLogPublisher<AccessLogPublisherCfg>,
      AccessLogPublisherCfg> getStorage()
  {
    return loggerStorage;
  }

  /**
   * Retrieve the singleton instance of this class.
   *
   * @return The singleton instance of this logger.
   */
  public static AccessLogger getInstance()
  {
    return instance;
  }

  /**
   * Add an access log publisher to the access logger.
   *
   * @param publisher The access log publisher to add.
   */
  public synchronized static void addAccessLogPublisher(
      AccessLogPublisher publisher)
  {
    loggerStorage.addLogPublisher(publisher);
  }

  /**
   * Remove an access log publisher from the access logger.
   *
   * @param publisher The access log publisher to remove.
   * @return The publisher that was removed or null if it was not found.
   */
  public synchronized static boolean removeAccessLogPublisher(
      AccessLogPublisher<AccessLogPublisherCfg> publisher)
  {
    return loggerStorage.removeLogPublisher(publisher);
  }

  /**
   * Removes all existing access log publishers from the logger.
   */
  public synchronized static void removeAllAccessLogPublishers()
  {
    loggerStorage.removeAllLogPublishers();
  }

  /**
   * Returns all the registered access log publishers.
   *
   * @return a Collection of {@link AccessLogPublisher} objects
   */
  private static Collection
      <AccessLogPublisher<AccessLogPublisherCfg>> getAccessLogPublishers()
  {
    return loggerStorage.getLogPublishers();
  }


  /**
   * Writes a message to the access logger with information about a new client
   * connection that has been established, regardless of whether it will be
   * immediately terminated.
   *
   * @param  clientConnection  The client connection that has been established.
   */
  public static void logConnect(ClientConnection clientConnection)
  {
    for (AccessLogPublisher<?> publisher : getAccessLogPublishers())
    {
      publisher.logConnect(clientConnection);
    }
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
  public static void logDisconnect(ClientConnection clientConnection,
                                   DisconnectReason disconnectReason,
                                   Message message)
  {
    for (AccessLogPublisher<?> publisher : getAccessLogPublishers())
    {
      publisher.logDisconnect(clientConnection, disconnectReason, message);
    }
  }



  /**
   * Writes a message to the access logger with information about the abandon
   * request associated with the provided abandon operation.
   *
   * @param  abandonOperation  The abandon operation containing the information
   *                           to use to log the abandon request.
   */
  public static void logAbandonRequest(AbandonOperation abandonOperation)
  {
    for (AccessLogPublisher<?> publisher : getAccessLogPublishers())
    {
      publisher.logAbandonRequest(abandonOperation);
    }
  }



  /**
   * Writes a message to the access logger with information about the result of
   * the provided abandon operation.
   *
   * @param  abandonOperation  The abandon operation containing the information
   *                           to use to log the abandon result.
   */
  public static void logAbandonResult(AbandonOperation abandonOperation)
  {
    for (AccessLogPublisher<?> publisher : getAccessLogPublishers())
    {
      publisher.logAbandonResult(abandonOperation);
    }
  }



  /**
   * Writes a message to the access logger with information about the add
   * request associated with the provided add operation.
   *
   * @param  addOperation  The add operation containing the information to use
   *                       to log the add request.
   */
  public static void logAddRequest(AddOperation addOperation)
  {
    for (AccessLogPublisher<?> publisher : getAccessLogPublishers())
    {
      publisher.logAddRequest(addOperation);
    }
  }



  /**
   * Writes a message to the access logger with information about the add
   * response associated with the provided add operation.
   *
   * @param  addOperation  The add operation containing the information to use
   *                       to log the add response.
   */
  public static void logAddResponse(AddOperation addOperation)
  {
    for (AccessLogPublisher<?> publisher : getAccessLogPublishers())
    {
      publisher.logAddResponse(addOperation);
    }
  }



  /**
   * Writes a message to the access logger with information about the bind
   * request associated with the provided bind operation.
   *
   * @param  bindOperation  The bind operation containing the information to use
   *                        to log the bind request.
   */
  public static void logBindRequest(BindOperation bindOperation)
  {
    for (AccessLogPublisher<?> publisher : getAccessLogPublishers())
    {
      publisher.logBindRequest(bindOperation);
    }
  }



  /**
   * Writes a message to the access logger with information about the bind
   * response associated with the provided bind operation.
   *
   * @param  bindOperation  The bind operation containing the information to use
   *                        to log the bind response.
   */
  public static void logBindResponse(BindOperation bindOperation)
  {
    for (AccessLogPublisher<?> publisher : getAccessLogPublishers())
    {
      publisher.logBindResponse(bindOperation);
    }
  }



  /**
   * Writes a message to the access logger with information about the compare
   * request associated with the provided compare operation.
   *
   * @param  compareOperation  The compare operation containing the information
   *                           to use to log the compare request.
   */
  public static void logCompareRequest(CompareOperation compareOperation)
  {
    for (AccessLogPublisher<?> publisher : getAccessLogPublishers())
    {
      publisher.logCompareRequest(compareOperation);
    }
  }



  /**
   * Writes a message to the access logger with information about the compare
   * response associated with the provided compare operation.
   *
   * @param  compareOperation  The compare operation containing the information
   *                           to use to log the compare response.
   */
  public static void logCompareResponse(CompareOperation compareOperation)
  {
    for (AccessLogPublisher<?> publisher : getAccessLogPublishers())
    {
      publisher.logCompareResponse(compareOperation);
    }
  }



  /**
   * Writes a message to the access logger with information about the delete
   * request associated with the provided delete operation.
   *
   * @param  deleteOperation  The delete operation containing the information to
   *                          use to log the delete request.
   */
  public static void logDeleteRequest(DeleteOperation deleteOperation)
  {
    for (AccessLogPublisher<?> publisher : getAccessLogPublishers())
    {
      publisher.logDeleteRequest(deleteOperation);
    }
  }



  /**
   * Writes a message to the access logger with information about the delete
   * response associated with the provided delete operation.
   *
   * @param  deleteOperation  The delete operation containing the information to
   *                           use to log the delete response.
   */
  public static void logDeleteResponse(DeleteOperation deleteOperation)
  {
    for (AccessLogPublisher<?> publisher : getAccessLogPublishers())
    {
      publisher.logDeleteResponse(deleteOperation);
    }
  }



  /**
   * Writes a message to the access logger with information about the extended
   * request associated with the provided extended operation.
   *
   * @param  extendedOperation  The extended operation containing the
   *                            information to use to log the extended request.
   */
  public static void logExtendedRequest(ExtendedOperation extendedOperation)
  {
    for (AccessLogPublisher<?> publisher : getAccessLogPublishers())
    {
      publisher.logExtendedRequest(extendedOperation);
    }
  }



  /**
   * Writes a message to the access logger with information about the extended
   * response associated with the provided extended operation.
   *
   * @param  extendedOperation  The extended operation containing the
   *                            information to use to log the extended response.
   */
  public static void logExtendedResponse(ExtendedOperation extendedOperation)
  {
    for (AccessLogPublisher<?> publisher : getAccessLogPublishers())
    {
      publisher.logExtendedResponse(extendedOperation);
    }
  }



  /**
   * Writes a message to the access logger with information about the modify
   * request associated with the provided modify operation.
   *
   * @param  modifyOperation  The modify operation containing the information to
   *                          use to log the modify request.
   */
  public static void logModifyRequest(ModifyOperation modifyOperation)
  {
    for (AccessLogPublisher<?> publisher : getAccessLogPublishers())
    {
      publisher.logModifyRequest(modifyOperation);
    }
  }



  /**
   * Writes a message to the access logger with information about the modify
   * response associated with the provided modify operation.
   *
   * @param  modifyOperation  The modify operation containing the information to
   *                          use to log the modify response.
   */
  public static void logModifyResponse(ModifyOperation modifyOperation)
  {
    for (AccessLogPublisher<?> publisher : getAccessLogPublishers())
    {
      publisher.logModifyResponse(modifyOperation);
    }
  }



  /**
   * Writes a message to the access logger with information about the modify DN
   * request associated with the provided modify DN operation.
   *
   * @param  modifyDNOperation  The modify DN operation containing the
   *                            information to use to log the modify DN request.
   */
  public static void logModifyDNRequest(ModifyDNOperation modifyDNOperation)
  {
    for (AccessLogPublisher<?> publisher : getAccessLogPublishers())
    {
      publisher.logModifyDNRequest(modifyDNOperation);
    }
  }



  /**
   * Writes a message to the access logger with information about the modify DN
   * response associated with the provided modify DN operation.
   *
   * @param  modifyDNOperation  The modify DN operation containing the
   *                            information to use to log the modify DN
   *                            response.
   */
  public static void logModifyDNResponse(ModifyDNOperation modifyDNOperation)
  {
    for (AccessLogPublisher<?> publisher : getAccessLogPublishers())
    {
      publisher.logModifyDNResponse(modifyDNOperation);
    }
  }



  /**
   * Writes a message to the access logger with information about the search
   * request associated with the provided search operation.
   *
   * @param  searchOperation  The search operation containing the information to
   *                          use to log the search request.
   */
  public static void logSearchRequest(SearchOperation searchOperation)
  {
    for (AccessLogPublisher<?> publisher : getAccessLogPublishers())
    {
      publisher.logSearchRequest(searchOperation);
    }
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
  public static void logSearchResultEntry(SearchOperation searchOperation,
                                          SearchResultEntry searchEntry)
  {
    for (AccessLogPublisher<?> publisher : getAccessLogPublishers())
    {
      publisher.logSearchResultEntry(searchOperation, searchEntry);
    }
  }



  /**
   * Writes a message to the access logger with information about the search
   * result reference returned while processing the associated search operation.
   *
   * @param  searchOperation  The search operation with which the search result
   *                          reference is associated.
   * @param  searchReference  The search result reference to be logged.
   */
  public static void logSearchResultReference(SearchOperation searchOperation,
                          SearchResultReference searchReference)
  {
    for (AccessLogPublisher<?> publisher : getAccessLogPublishers())
    {
      publisher.logSearchResultReference(searchOperation, searchReference);
    }
  }



  /**
   * Writes a message to the access logger with information about the completion
   * of the provided search operation.
   *
   * @param  searchOperation  The search operation containing the information
   *                          to use to log the search result done message.
   */
  public static void logSearchResultDone(SearchOperation searchOperation)
  {
    for (AccessLogPublisher<?> publisher : getAccessLogPublishers())
    {
      publisher.logSearchResultDone(searchOperation);
    }
  }



  /**
   * Writes a message to the access logger with information about the unbind
   * request associated with the provided unbind operation.
   *
   * @param  unbindOperation  The unbind operation containing the information to
   *                          use to log the unbind request.
   */
  public static void logUnbind(UnbindOperation unbindOperation)
  {
    for (AccessLogPublisher<?> publisher : getAccessLogPublishers())
    {
      publisher.logUnbind(unbindOperation);
    }
  }
}

