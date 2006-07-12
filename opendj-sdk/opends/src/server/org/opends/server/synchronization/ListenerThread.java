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
package org.opends.server.synchronization;

import java.util.zip.DataFormatException;

import org.opends.server.api.DirectoryThread;
import org.opends.server.core.Operation;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ResultCode;

import static org.opends.server.loggers.Error.logError;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.synchronization.SynchMessages.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

/**
 * Thread that is used to get messages from the Changelog servers
 * and replay them in the current server.
 */
public class ListenerThread extends DirectoryThread
{
  private SynchronizationDomain listener;
  private ChangeNumberGenerator CNgen;
  private boolean shutdown = false;

  /**
   * Constructor for the ListenerThread.
   *
   * @param listener the Plugin that created this thread
   * @param gen the Generator to use to get new ChangeNumber
   */
  public ListenerThread(SynchronizationDomain listener,
                        ChangeNumberGenerator gen)
  {
     super("Listener thread");
     this.listener = listener;
     this.CNgen = gen;
     setName("Synchronization Listener");
  }

  /**
   * Shutdown this listener thread.
   */
  public void shutdown()
  {
    shutdown = true;
  }

  /**
   * Run method for this class.
   */
  public void run()
  {
    InternalClientConnection conn = new InternalClientConnection();
    UpdateMessage msg;

    while (((msg = listener.receive()) != null) && (shutdown == false))
    {
      Operation op;

      try
      {
        op = msg.createOperation(conn);

        op.setInternalOperation(true);
        op.setSynchronizationOperation(true);
        ChangeNumber changeNumber =
          (ChangeNumber) op.getAttachment(SYNCHRONIZATION);
        if (changeNumber != null)
          CNgen.adjust(changeNumber);
        try
        {
          op.run();
          if (op.getResultCode() != ResultCode.SUCCESS)
          {
            int msgID = MSGID_ERROR_REPLAYING_OPERATION;
            String message = getMessage(msgID,
                op.getResultCode().getResultCodeName(),
                changeNumber.toString(),
                op.toString(), op.getErrorMessage());
            logError(ErrorLogCategory.SYNCHRONIZATION,
                ErrorLogSeverity.SEVERE_ERROR,
                message, msgID);
            listener.updateError(changeNumber);
          }
        } catch (Exception e)
        {
          int msgID = MSGID_EXCEPTION_REPLAYING_OPERATION;
          String message = getMessage(msgID, stackTraceToSingleLineString(e),
              op.toString());
          logError(ErrorLogCategory.SYNCHRONIZATION,
              ErrorLogSeverity.SEVERE_ERROR,
              message, msgID);
          listener.updateError(changeNumber);
        }
      }
      catch (ASN1Exception e)
      {
        int msgID = MSGID_EXCEPTION_DECODING_OPERATION;
        String message = getMessage(msgID, msg) +
                         stackTraceToSingleLineString(e);
        logError(ErrorLogCategory.SYNCHRONIZATION,
            ErrorLogSeverity.SEVERE_ERROR,
            message, msgID);
      }
      catch (LDAPException e)
      {
        int msgID = MSGID_EXCEPTION_DECODING_OPERATION;
        String message = getMessage(msgID, msg) +
                         stackTraceToSingleLineString(e);
        logError(ErrorLogCategory.SYNCHRONIZATION,
            ErrorLogSeverity.SEVERE_ERROR,
            message, msgID);
      }
      catch (DataFormatException e)
      {
        int msgID = MSGID_EXCEPTION_DECODING_OPERATION;
        String message = getMessage(msgID, msg) +
                         stackTraceToSingleLineString(e);
        logError(ErrorLogCategory.SYNCHRONIZATION,
            ErrorLogSeverity.SEVERE_ERROR,
            message, msgID);
      }
      finally
      {
        if (msg.isAssured())
          listener.ack(msg.getChangeNumber());
        listener.incProcessedUpdates();
      }
    }
  }
}
