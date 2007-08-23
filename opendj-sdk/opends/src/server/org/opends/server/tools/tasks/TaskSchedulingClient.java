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

package org.opends.server.tools.tasks;

import org.opends.server.types.LDAPException;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.ResultCode;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.AddRequestProtocolOp;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.protocols.ldap.LDAPConstants;
import org.opends.server.protocols.ldap.ExtendedResponseProtocolOp;
import org.opends.server.protocols.ldap.AddResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPAttribute;
import static org.opends.server.config.ConfigConstants.ATTR_TASK_ID;
import static org.opends.server.config.ConfigConstants.SCHEDULED_TASK_BASE_RDN;
import static org.opends.server.config.ConfigConstants.DN_TASK_ROOT;
import static org.opends.server.config.ConfigConstants.ATTR_OBJECTCLASS;
import static org.opends.server.config.ConfigConstants.ATTR_TASK_CLASS;
import org.opends.server.config.ConfigConstants;
import static org.opends.server.util.ServerConstants.MAX_LINE_WIDTH;
import static org.opends.server.util.StaticUtils.wrapText;
import org.opends.server.tools.LDAPConnection;
import org.opends.server.tools.LDAPReader;
import org.opends.server.tools.LDAPWriter;
import org.opends.messages.Message;
import static org.opends.messages.ToolMessages.*;

import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Helper class for interacting with the task backend on behalf of utilities
 * that are capable of being scheduled.
 */
public class TaskSchedulingClient {

  /**
   * Connection through which task scheduling will take place.
   */
  protected LDAPConnection connection;

  /**
   * Creates a new TaskClient for interacting with the task backend remotely.
   * @param conn for accessing the task backend
   */
  public TaskSchedulingClient(LDAPConnection conn) {
    this.connection = conn;
  }

  /**
   * Schedule a task for execution by writing an entry to the task backend.
   *
   * @param information to be scheduled
   * @param out stream for writing error messages; may be null
   * @param err stream for writing error messages; may be null
   * @return int representing an LDAP return code
   */
  public synchronized int schedule(TaskScheduleInformation information,
                                   PrintStream out, PrintStream err) {

    // Attempt to connect and authenticate to the Directory Server.
    AtomicInteger nextMessageID = new AtomicInteger(1);

    LDAPReader reader = connection.getLDAPReader();
    LDAPWriter writer = connection.getLDAPWriter();

    // Construct the add request to send to the server.
    String taskID = UUID.randomUUID().toString();
    ASN1OctetString entryDN =
         new ASN1OctetString(ATTR_TASK_ID + "=" + taskID + "," +
                             SCHEDULED_TASK_BASE_RDN + "," + DN_TASK_ROOT);

    ArrayList<LDAPControl> controls = new ArrayList<LDAPControl>();

    ArrayList<RawAttribute> attributes = new ArrayList<RawAttribute>();

    ArrayList<ASN1OctetString> ocValues = new ArrayList<ASN1OctetString>(3);
    ocValues.add(new ASN1OctetString("top"));
    ocValues.add(new ASN1OctetString(ConfigConstants.OC_TASK));
    ocValues.add(new ASN1OctetString(information.getTaskObjectclass()));
    attributes.add(new LDAPAttribute(ATTR_OBJECTCLASS, ocValues));

    ArrayList<ASN1OctetString> taskIDValues = new ArrayList<ASN1OctetString>(1);
    taskIDValues.add(new ASN1OctetString(taskID));
    attributes.add(new LDAPAttribute(ATTR_TASK_ID, taskIDValues));

    ArrayList<ASN1OctetString> classValues = new ArrayList<ASN1OctetString>(1);
    classValues.add(new ASN1OctetString(information.getTaskClass().getName()));
    attributes.add(new LDAPAttribute(ATTR_TASK_CLASS, classValues));

    information.addTaskAttributes(attributes);

    AddRequestProtocolOp addRequest = new AddRequestProtocolOp(entryDN,
                                                               attributes);
    LDAPMessage requestMessage =
         new LDAPMessage(nextMessageID.getAndIncrement(), addRequest, controls);

    // Send the request to the server and read the response.
    LDAPMessage responseMessage;
    try
    {
      writer.writeMessage(requestMessage);

      responseMessage = reader.readMessage();
      if (responseMessage == null)
      {
        Message message = ERR_TASK_CLIENT_UNEXPECTED_CONNECTION_CLOSURE.get();
        if (err != null) err.println(wrapText(message, MAX_LINE_WIDTH));
        return LDAPResultCode.CLIENT_SIDE_SERVER_DOWN;
      }
    }
    catch (IOException ioe)
    {
      Message message = ERR_TASK_CLIENT_IO_ERROR.get(String.valueOf(ioe));
      if (err != null) err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_SERVER_DOWN;
    }
    catch (ASN1Exception ae)
    {
      Message message = ERR_TASK_CLIENT_DECODE_ERROR.get(ae.getMessage());
      if (err != null) err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_DECODING_ERROR;
    }
    catch (LDAPException le)
    {
      Message message = ERR_TASK_CLIENT_DECODE_ERROR.get(le.getMessage());
      if (err != null) err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_DECODING_ERROR;
    }

    if (responseMessage.getProtocolOpType() !=
        LDAPConstants.OP_TYPE_ADD_RESPONSE)
    {
      if (responseMessage.getProtocolOpType() ==
          LDAPConstants.OP_TYPE_EXTENDED_RESPONSE)
      {
        // It's possible that this is a notice of disconnection, which we can
        // probably interpret as a "success" in this case.
        ExtendedResponseProtocolOp extendedResponse =
             responseMessage.getExtendedResponseProtocolOp();
        String responseOID = extendedResponse.getOID();
        if ((responseOID != null) &&
            (responseOID.equals(LDAPConstants.OID_NOTICE_OF_DISCONNECTION)))
        {
          Message message = extendedResponse.getErrorMessage();
          if (message != null)
          {
            if (err != null) err.println(wrapText(message, MAX_LINE_WIDTH));
          }

          return extendedResponse.getResultCode();
        }
      }

      Message message = ERR_TASK_CLIENT_INVALID_RESPONSE_TYPE.get(
              responseMessage.getProtocolOpName());
      if (err != null) err.println(wrapText(message, MAX_LINE_WIDTH));
      return LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR;
    }

    AddResponseProtocolOp addResponse =
         responseMessage.getAddResponseProtocolOp();
    Message errorMessage = addResponse.getErrorMessage();
    if (errorMessage != null && err != null)
    {
      err.println(wrapText(errorMessage, MAX_LINE_WIDTH));
    }

    if (addResponse.getResultCode() == ResultCode.SUCCESS.getIntValue() &&
          out != null)
    {
      out.println(wrapText(INFO_TASK_CLIENT_TASK_SCHEDULED.get(taskID),
                           MAX_LINE_WIDTH));
    }

    return addResponse.getResultCode();
  }

}
