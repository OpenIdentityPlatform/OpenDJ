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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions copyright 2011-2013 ForgeRock AS.
 */
package org.opends.server.api;

import java.util.List;

import org.opends.messages.Message;
import org.opends.server.admin.std.server.AccessLogPublisherCfg;
import org.opends.server.core.*;
import org.opends.server.types.*;

/**
 * This class defines the set of methods and structures that must be
 * implemented for a Directory Server access log publisher.
 *
 * @param <T>
 *          The type of access log publisher configuration handled by
 *          this log publisher implementation.
 */
@org.opends.server.types.PublicAPI(
    stability = org.opends.server.types.StabilityLevel.VOLATILE,
    mayInstantiate = false,
    mayExtend = true,
    mayInvoke = false)
public abstract class AccessLogPublisher<T extends AccessLogPublisherCfg>
    implements LogPublisher<T>
{

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAcceptable(T configuration,
      List<Message> unacceptableReasons)
  {
    // This default implementation does not perform any special
    // validation. It should be overridden by access log publisher
    // implementations that wish to perform more detailed validation.
    return true;
  }



  /**
   * Writes a message to the access logger with information about a
   * new client connection that has been established, regardless of
   * whether it will be immediately terminated.
   * <p>
   * The default implementation is to not log anything.
   *
   * @param clientConnection
   *          The client connection that has been established.
   */
  public void logConnect(ClientConnection clientConnection)
  {
    // Do nothing
  }



  /**
   * Writes a message to the access logger with information about the
   * termination of an existing client connection.
   * <p>
   * The default implementation is to not log anything.
   *
   * @param clientConnection
   *          The client connection that has been terminated.
   * @param disconnectReason
   *          A generic disconnect reason for the connection
   *          termination.
   * @param message
   *          A human-readable message that can provide additional
   *          information about the disconnect.
   */
  public void logDisconnect(ClientConnection clientConnection,
      DisconnectReason disconnectReason, Message message)
  {
    // Do nothing
  }



  /**
   * Writes a message to the access logger with information about the
   * abandon request associated with the provided abandon operation.
   * <p>
   * The default implementation is to not log anything.
   *
   * @param abandonOperation
   *          The abandon operation containing the information to use
   *          to log the abandon request.
   */
  public void logAbandonRequest(AbandonOperation abandonOperation)
  {
    // Do nothing
  }



  /**
   * Writes a message to the access logger with information about the
   * result of the provided abandon operation.
   * <p>
   * The default implementation is to not log anything.
   *
   * @param abandonOperation
   *          The abandon operation containing the information to use
   *          to log the abandon request.
   */
  public void logAbandonResult(AbandonOperation abandonOperation)
  {
    // Do nothing
  }



  /**
   * Writes a message to the access logger with information about the
   * add request associated with the provided add operation.
   * <p>
   * The default implementation is to not log anything.
   *
   * @param addOperation
   *          The add operation containing the information to use to
   *          log the add request.
   */
  public void logAddRequest(AddOperation addOperation)
  {
    // Do nothing
  }



  /**
   * Writes a message to the access logger with information about the
   * add response associated with the provided add operation.
   * <p>
   * The default implementation is to not log anything.
   *
   * @param addOperation
   *          The add operation containing the information to use to
   *          log the add response.
   */
  public void logAddResponse(AddOperation addOperation)
  {
    // Do nothing
  }



  /**
   * Writes a message to the access logger with information about the
   * bind request associated with the provided bind operation.
   * <p>
   * The default implementation is to not log anything.
   *
   * @param bindOperation
   *          The bind operation containing the information to use to
   *          log the bind request.
   */
  public void logBindRequest(BindOperation bindOperation)
  {
    // Do nothing
  }



  /**
   * Writes a message to the access logger with information about the
   * bind response associated with the provided bind operation.
   * <p>
   * The default implementation is to not log anything.
   *
   * @param bindOperation
   *          The bind operation containing the information to use to
   *          log the bind response.
   */
  public void logBindResponse(BindOperation bindOperation)
  {
    // Do nothing
  }



  /**
   * Writes a message to the access logger with information about the
   * compare request associated with the provided compare operation.
   * <p>
   * The default implementation is to not log anything.
   *
   * @param compareOperation
   *          The compare operation containing the information to use
   *          to log the compare request.
   */
  public void logCompareRequest(CompareOperation compareOperation)
  {
    // Do nothing
  }



  /**
   * Writes a message to the access logger with information about the
   * compare response associated with the provided compare operation.
   * <p>
   * The default implementation is to not log anything.
   *
   * @param compareOperation
   *          The compare operation containing the information to use
   *          to log the compare response.
   */
  public void logCompareResponse(CompareOperation compareOperation)
  {
    // Do nothing
  }



  /**
   * Writes a message to the access logger with information about the
   * delete request associated with the provided delete operation.
   * <p>
   * The default implementation is to not log anything.
   *
   * @param deleteOperation
   *          The delete operation containing the information to use
   *          to log the delete request.
   */
  public void logDeleteRequest(DeleteOperation deleteOperation)
  {
    // Do nothing
  }



  /**
   * Writes a message to the access logger with information about the
   * delete response associated with the provided delete operation.
   * <p>
   * The default implementation is to not log anything.
   *
   * @param deleteOperation
   *          The delete operation containing the information to use
   *          to log the delete response.
   */
  public void logDeleteResponse(DeleteOperation deleteOperation)
  {
    // Do nothing
  }



  /**
   * Writes a message to the access logger with information about the
   * extended request associated with the provided extended operation.
   * <p>
   * The default implementation is to not log anything.
   *
   * @param extendedOperation
   *          The extended operation containing the information to use
   *          to log the extended request.
   */
  public void logExtendedRequest(ExtendedOperation extendedOperation)
  {
    // Do nothing
  }



  /**
   * Writes a message to the access logger with information about the
   * extended response associated with the provided extended
   * operation.
   * <p>
   * The default implementation is to not log anything.
   *
   * @param extendedOperation
   *          The extended operation containing the information to use
   *          to log the extended response.
   */
  public void logExtendedResponse(ExtendedOperation extendedOperation)
  {
    // Do nothing
  }



  /**
   * Writes a message to the access logger with information about the
   * modify request associated with the provided modify operation.
   * <p>
   * The default implementation is to not log anything.
   *
   * @param modifyOperation
   *          The modify operation containing the information to use
   *          to log the modify request.
   */
  public void logModifyRequest(ModifyOperation modifyOperation)
  {
    // Do nothing
  }



  /**
   * Writes a message to the access logger with information about the
   * modify response associated with the provided modify operation.
   * <p>
   * The default implementation is to not log anything.
   *
   * @param modifyOperation
   *          The modify operation containing the information to use
   *          to log the modify response.
   */
  public void logModifyResponse(ModifyOperation modifyOperation)
  {
    // Do nothing
  }



  /**
   * Writes a message to the access logger with information about the
   * modify DN request associated with the provided modify DN
   * operation.
   * <p>
   * The default implementation is to not log anything.
   *
   * @param modifyDNOperation
   *          The modify DN operation containing the information to
   *          use to log the modify DN request.
   */
  public void logModifyDNRequest(ModifyDNOperation modifyDNOperation)
  {
    // Do nothing
  }



  /**
   * Writes a message to the access logger with information about the
   * modify DN response associated with the provided modify DN
   * operation.
   * <p>
   * The default implementation is to not log anything.
   *
   * @param modifyDNOperation
   *          The modify DN operation containing the information to
   *          use to log the modify DN response.
   */
  public void logModifyDNResponse(ModifyDNOperation modifyDNOperation)
  {
    // Do nothing
  }



  /**
   * Writes a message to the access logger with information about the
   * search request associated with the provided search operation.
   * <p>
   * The default implementation is to not log anything.
   *
   * @param searchOperation
   *          The search operation containing the information to use
   *          to log the search request.
   */
  public void logSearchRequest(SearchOperation searchOperation)
  {
    // Do nothing
  }



  /**
   * Writes a message to the access logger with information about the
   * search result entry that matches the criteria associated with the
   * provided search operation.
   * <p>
   * The default implementation is to not log anything.
   *
   * @param searchOperation
   *          The search operation with which the search result entry
   *          is associated.
   * @param searchEntry
   *          The search result entry to be logged.
   */
  public void logSearchResultEntry(SearchOperation searchOperation,
      SearchResultEntry searchEntry)
  {
    // Do nothing
  }



  /**
   * Writes a message to the access logger with information about the
   * search result reference returned while processing the associated
   * search operation.
   * <p>
   * The default implementation is to not log anything.
   *
   * @param searchOperation
   *          The search operation with which the search result
   *          reference is associated.
   * @param searchReference
   *          The search result reference to be logged.
   */
  public void logSearchResultReference(
      SearchOperation searchOperation,
      SearchResultReference searchReference)
  {
    // Do nothing
  }



  /**
   * Writes a message to the access logger with information about the
   * completion of the provided search operation.
   * <p>
   * The default implementation is to not log anything.
   *
   * @param searchOperation
   *          The search operation containing the information to use
   *          to log the search result done message.
   */
  public void logSearchResultDone(SearchOperation searchOperation)
  {
    // Do nothing
  }



  /**
   * Writes a message to the access logger with information about the
   * unbind request associated with the provided unbind operation.
   * <p>
   * The default implementation is to not log anything.
   *
   * @param unbindOperation
   *          The unbind operation containing the information to use
   *          to log the unbind request.
   */
  public void logUnbind(UnbindOperation unbindOperation)
  {
    // Do nothing
  }

}
