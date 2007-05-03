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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.api;



import org.opends.server.admin.std.server.AccessLogPublisherCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.core.*;
import org.opends.server.types.InitializationException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;



/**
 * This class defines the set of methods and structures that must be
 * implemented for a Directory Server access log publisher.
 *
 * @param  <T>  The type of access log publisher configuration handled
 *              by this log publisher implementation.
 */
public abstract class AccessLogPublisher
       <T extends AccessLogPublisherCfg>
{
  /**
   * Indicates if internal operations should be omited in the messages
   * logged by this publisher.
   */
  protected boolean suppressInternalOperations = true;



  /**
   * Initializes this access publisher provider based on the
   * information in the provided debug publisher configuration.
   *
   * @param  config  The access publisher configuration that contains
   *                 the information to use to initialize this access
   *                 publisher.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in
   *                           the process of performing the
   *                           initialization as a result of the
   *                           server configuration.
   *
   * @throws  InitializationException  If a problem occurs during
   *                                   initialization that is not
   *                                   related to the server
   *                                   configuration.
   */
  public abstract void initializeAccessLogPublisher(T config)
         throws ConfigException, InitializationException;



  /**
   * Close this publisher.
   */
  public abstract void close();



  /**
   * Writes a message to the access logger with information about a
   * new client connection that has been established, regardless of
   * whether it will be immediately terminated.
   *
   * @param  clientConnection  The client connection that has been
   *                           established.
   */
  public abstract void logConnect(ClientConnection clientConnection);



  /**
   * Writes a message to the access logger with information about the
   * termination of an existing client connection.
   *
   * @param  clientConnection  The client connection that has been
   *                           terminated.
   * @param  disconnectReason  A generic disconnect reason for the
   *                           connection termination.
   * @param  message           A human-readable message that can
   *                           provide additional information about
   *                           the disconnect.
   */
  public abstract void logDisconnect(
                            ClientConnection clientConnection,
                            DisconnectReason disconnectReason,
                            String message);



  /**
   * Writes a message to the access logger with information about the
   * abandon request associated with the provided abandon operation.
   *
   * @param  abandonOperation  The abandon operation containing the
   *                           information to use to log the abandon
   *                           request.
   */
  public abstract void logAbandonRequest(
                            AbandonOperation abandonOperation);



  /**
   * Writes a message to the access logger with information about the
   * result of the provided abandon operation.
   *
   * @param  abandonOperation  The abandon operation containing the
   *                           information to use to log the abandon
   *                           request.
   */
  public abstract void logAbandonResult(
                            AbandonOperation abandonOperation);



  /**
   * Writes a message to the access logger with information about the
   * add request associated with the provided add operation.
   *
   * @param  addOperation  The add operation containing the
   *                       information to use to log the add request.
   */
  public abstract void logAddRequest(AddOperation addOperation);



  /**
   * Writes a message to the access logger with information about the
   * add response associated with the provided add operation.
   *
   * @param  addOperation  The add operation containing the
   *                       information to use to log the add response.
   */
  public abstract void logAddResponse(AddOperation addOperation);



  /**
   * Writes a message to the access logger with information about the
   * bind request associated with the provided bind operation.
   *
   * @param  bindOperation  The bind operation containing the
   *                        information to use to log the bind
   *                        request.
   */
  public abstract void logBindRequest(BindOperation bindOperation);



  /**
   * Writes a message to the access logger with information about the
   * bind response associated with the provided bind operation.
   *
   * @param  bindOperation  The bind operation containing the
   *                        information to use to log the bind
   *                        response.
   */
  public abstract void logBindResponse(BindOperation bindOperation);



  /**
   * Writes a message to the access logger with information about the
   * compare request associated with the provided compare operation.
   *
   * @param  compareOperation  The compare operation containing the
   *                           information to use to log the compare
   *                           request.
   */
  public abstract void logCompareRequest(
                            CompareOperation compareOperation);



  /**
   * Writes a message to the access logger with information about the
   * compare response associated with the provided compare operation.
   *
   * @param  compareOperation  The compare operation containing the
   *                           information to use to log the compare
   *                           response.
   */
  public abstract void logCompareResponse(
                            CompareOperation compareOperation);



  /**
   * Writes a message to the access logger with information about the
   * delete request associated with the provided delete operation.
   *
   * @param  deleteOperation  The delete operation containing the
   *                          information to use to log the delete
   *                          request.
   */
  public abstract void logDeleteRequest(
                            DeleteOperation deleteOperation);



  /**
   * Writes a message to the access logger with information about the
   * delete response associated with the provided delete operation.
   *
   * @param  deleteOperation  The delete operation containing the
   *                          information to use to log the delete
   *                          response.
   */
  public abstract void logDeleteResponse(
                            DeleteOperation deleteOperation);



  /**
   * Writes a message to the access logger with information about the
   * extended request associated with the provided extended operation.
   *
   * @param  extendedOperation  The extended operation containing the
   *                            information to use to log the extended
   *                            request.
   */
  public abstract void logExtendedRequest(
                            ExtendedOperation extendedOperation);



  /**
   * Writes a message to the access logger with information about the
   * extended response associated with the provided extended
   * operation.
   *
   * @param  extendedOperation  The extended operation containing the
   *                            information to use to log the extended
   *                            response.
   */
  public abstract void logExtendedResponse(
                            ExtendedOperation extendedOperation);



  /**
   * Writes a message to the access logger with information about the
   * modify request associated with the provided modify operation.
   *
   * @param  modifyOperation  The modify operation containing the
   *                          information to use to log the modify
   *                          request.
   */
  public abstract void logModifyRequest(
                            ModifyOperation modifyOperation);



  /**
   * Writes a message to the access logger with information about the
   * modify response associated with the provided modify operation.
   *
   * @param  modifyOperation  The modify operation containing the
   *                          information to use to log the modify
   *                          response.
   */
  public abstract void logModifyResponse(
                            ModifyOperation modifyOperation);



  /**
   * Writes a message to the access logger with information about the
   * modify DN request associated with the provided modify DN
   * operation.
   *
   * @param  modifyDNOperation  The modify DN operation containing the
   *                            information to use to log the modify
   *                            DN request.
   */
  public abstract void logModifyDNRequest(
                            ModifyDNOperation modifyDNOperation);



  /**
   * Writes a message to the access logger with information about the
   * modify DN response associated with the provided modify DN
   * operation.
   *
   * @param  modifyDNOperation  The modify DN operation containing the
   *                            information to use to log the modify
   *                            DN response.
   */
  public abstract void logModifyDNResponse(
                            ModifyDNOperation modifyDNOperation);



  /**
   * Writes a message to the access logger with information about the
   * search request associated with the provided search operation.
   *
   * @param  searchOperation  The search operation containing the
   *                          information to use to log the search
   *                          request.
   */
  public abstract void logSearchRequest(
                            SearchOperation searchOperation);



  /**
   * Writes a message to the access logger with information about the
   * search result entry that matches the criteria associated with the
   * provided search operation.
   *
   * @param  searchOperation  The search operation with which the
   *                          search result entry is associated.
   * @param  searchEntry      The search result entry to be logged.
   */
  public abstract void logSearchResultEntry(
                            SearchOperation searchOperation,
                            SearchResultEntry searchEntry);



  /**
   * Writes a message to the access logger with information about the
   * search result reference returned while processing the associated
   * search operation.
   *
   * @param  searchOperation  The search operation with which the
   *                          search result reference is associated.
   * @param  searchReference  The search result reference to be
   *                          logged.
   */
  public abstract void logSearchResultReference(
                            SearchOperation searchOperation,
                            SearchResultReference searchReference);



  /**
   * Writes a message to the access logger with information about the
   * completion of the provided search operation.
   *
   * @param  searchOperation  The search operation containing the
   *                          information to use to log the search
   *                          result done message.
   */
  public abstract void logSearchResultDone(
                            SearchOperation searchOperation);



  /**
   * Writes a message to the access logger with information about the
   * unbind request associated with the provided unbind operation.
   *
   * @param  unbindOperation  The unbind operation containing the
   *                          information to use to log the unbind
   *                          request.
   */
  public abstract void logUnbind(UnbindOperation unbindOperation);
}

