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
package org.opends.server.loggers;



import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

import org.opends.server.api.AccessLogger;
import org.opends.server.api.ClientConnection;
import org.opends.server.core.AbandonOperation;
import org.opends.server.core.AddOperation;
import org.opends.server.core.BindOperation;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.UnbindOperation;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;



/**
 * This class defines the wrapper that will invoke all registered access loggers
 * for each type of request received or response sent.
 */
public class Access
{
  // The set of access loggers that have been registered with the server.  It
  // will initially be empty.
  private static CopyOnWriteArrayList<AccessLogger> accessLoggers =
       new CopyOnWriteArrayList<AccessLogger>();

  // A mutex that will be used to provide threadsafe access to methods changing
  // the set of defined loggers.
  private static ReentrantLock loggerMutex = new ReentrantLock();



  /**
   * Adds a new access logger to which access messages should be sent.
   *
   * @param  logger  The access logger to which messages should be sent.
   */
  public static void addAccessLogger(AccessLogger logger)
  {
    loggerMutex.lock();

    try
    {
      for (AccessLogger l : accessLoggers)
      {
        if (l.equals(logger))
        {
          return;
        }
      }

      accessLoggers.add(logger);
    }
    catch (Exception e)
    {
      // This should never happen.
      e.printStackTrace();
    }
    finally
    {
      loggerMutex.unlock();
    }
  }



  /**
   * Removes the provided access logger so it will no longer be sent any new
   * access messages.
   *
   * @param  logger  The access logger to remove from the set.
   */
  public static void removeAccessLogger(AccessLogger logger)
  {
    loggerMutex.lock();

    try
    {
      accessLoggers.remove(logger);
    }
    catch (Exception e)
    {
      // This should never happen.
      e.printStackTrace();
    }
    finally
    {
      loggerMutex.unlock();
    }
  }



  /**
   * Removes all active access loggers so that no access messages will be sent
   * anywhere.
   *
   * @param  closeLoggers  Indicates whether the loggers should be closed as
   *                       they are unregistered.
   */
  public static void removeAllAccessLoggers(boolean closeLoggers)
  {
    loggerMutex.lock();

    try
    {
      if (closeLoggers)
      {
        AccessLogger[] loggers = new AccessLogger[accessLoggers.size()];
        accessLoggers.toArray(loggers);

        accessLoggers.clear();

        for (AccessLogger logger : loggers)
        {
          logger.closeAccessLogger();
        }
      }
      else
      {
        accessLoggers.clear();
      }
    }
    catch (Exception e)
    {
      // This should never happen.
      e.printStackTrace();
    }
    finally
    {
      loggerMutex.unlock();
    }
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
    for (AccessLogger l : accessLoggers)
    {
      l.logConnect(clientConnection);
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
                                   String message)
  {
    for (AccessLogger l : accessLoggers)
    {
      l.logDisconnect(clientConnection, disconnectReason, message);
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
    for (AccessLogger l : accessLoggers)
    {
      l.logAbandonRequest(abandonOperation);
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
    for (AccessLogger l : accessLoggers)
    {
      l.logAbandonResult(abandonOperation);
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
    for (AccessLogger l : accessLoggers)
    {
      l.logAddRequest(addOperation);
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
    for (AccessLogger l : accessLoggers)
    {
      l.logAddResponse(addOperation);
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
    for (AccessLogger l : accessLoggers)
    {
      l.logBindRequest(bindOperation);
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
    for (AccessLogger l : accessLoggers)
    {
      l.logBindResponse(bindOperation);
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
    for (AccessLogger l : accessLoggers)
    {
      l.logCompareRequest(compareOperation);
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
    for (AccessLogger l : accessLoggers)
    {
      l.logCompareResponse(compareOperation);
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
    for (AccessLogger l : accessLoggers)
    {
      l.logDeleteRequest(deleteOperation);
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
    for (AccessLogger l : accessLoggers)
    {
      l.logDeleteResponse(deleteOperation);
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
    for (AccessLogger l : accessLoggers)
    {
      l.logExtendedRequest(extendedOperation);
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
    for (AccessLogger l : accessLoggers)
    {
      l.logExtendedResponse(extendedOperation);
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
    for (AccessLogger l : accessLoggers)
    {
      l.logModifyRequest(modifyOperation);
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
    for (AccessLogger l : accessLoggers)
    {
      l.logModifyResponse(modifyOperation);
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
    for (AccessLogger l : accessLoggers)
    {
      l.logModifyDNRequest(modifyDNOperation);
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
    for (AccessLogger l : accessLoggers)
    {
      l.logModifyDNResponse(modifyDNOperation);
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
    for (AccessLogger l : accessLoggers)
    {
      l.logSearchRequest(searchOperation);
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
    for (AccessLogger l : accessLoggers)
    {
      l.logSearchResultEntry(searchOperation, searchEntry);
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
    for (AccessLogger l : accessLoggers)
    {
      l.logSearchResultReference(searchOperation, searchReference);
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
    for (AccessLogger l : accessLoggers)
    {
      l.logSearchResultDone(searchOperation);
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
    for (AccessLogger l : accessLoggers)
    {
      l.logUnbind(unbindOperation);
    }
  }
}

