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

import org.opends.messages.Message;
import static org.opends.messages.ToolMessages.*;
import org.opends.server.config.ConfigConstants;
import static org.opends.server.config.ConfigConstants.*;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.AddRequestProtocolOp;
import org.opends.server.protocols.ldap.AddResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPConstants;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.protocols.ldap.ModifyRequestProtocolOp;
import org.opends.server.protocols.ldap.ModifyResponseProtocolOp;
import org.opends.server.protocols.ldap.SearchRequestProtocolOp;
import org.opends.server.protocols.ldap.SearchResultEntryProtocolOp;
import org.opends.server.tools.LDAPConnection;
import org.opends.server.tools.LDAPReader;
import org.opends.server.tools.LDAPWriter;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.Entry;
import org.opends.server.types.LDAPException;
import org.opends.server.types.ModificationType;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.RawModification;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;
import static org.opends.server.types.ResultCode.*;
import org.opends.server.backends.task.TaskState;
import static org.opends.server.util.ServerConstants.*;
import org.opends.server.util.StaticUtils;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Helper class for interacting with the task backend on behalf of utilities
 * that are capable of being scheduled.
 */
public class TaskClient {

  /**
   * Connection through which task scheduling will take place.
   */
  protected LDAPConnection connection;

  /**
   * Keeps track of message IDs.
   */
  private AtomicInteger nextMessageID = new AtomicInteger(0);

  /**
   * Creates a new TaskClient for interacting with the task backend remotely.
   * @param conn for accessing the task backend
   */
  public TaskClient(LDAPConnection conn) {
    this.connection = conn;
  }

  /**
   * Schedule a task for execution by writing an entry to the task backend.
   *
   * @param information to be scheduled
   * @return String task ID assigned the new task
   * @throws IOException if there is a stream communication problem
   * @throws LDAPException if there is a problem getting information
   *         out to the directory
   * @throws ASN1Exception if there is a problem with the encoding
   * @throws TaskClientException if there is a problem with the task entry
   */
  public synchronized TaskEntry schedule(TaskScheduleInformation information)
          throws LDAPException, IOException, ASN1Exception, TaskClientException
  {
    LDAPReader reader = connection.getLDAPReader();
    LDAPWriter writer = connection.getLDAPWriter();

    // Use a formatted time/date for the ID so that is remotely useful
    SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmssMM");
    String taskID = df.format(new Date());

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

    // add the start time if necessary
    Date startDate = information.getStartDateTime();
    if (startDate != null) {
      String startTimeString = StaticUtils.formatDateTimeString(startDate);
      ArrayList<ASN1OctetString> startDateValues =
              new ArrayList<ASN1OctetString>(1);
      startDateValues.add(new ASN1OctetString(startTimeString));
      attributes.add(new LDAPAttribute(ATTR_TASK_SCHEDULED_START_TIME,
              startDateValues));
    }

    information.addTaskAttributes(attributes);

    AddRequestProtocolOp addRequest = new AddRequestProtocolOp(entryDN,
                                                               attributes);
    LDAPMessage requestMessage =
         new LDAPMessage(nextMessageID.getAndIncrement(), addRequest, controls);

    // Send the request to the server and read the response.
    LDAPMessage responseMessage;
    writer.writeMessage(requestMessage);

    responseMessage = reader.readMessage();
    if (responseMessage == null)
    {
      throw new LDAPException(
              LDAPResultCode.CLIENT_SIDE_SERVER_DOWN,
              ERR_TASK_CLIENT_UNEXPECTED_CONNECTION_CLOSURE.get());
    }

    if (responseMessage.getProtocolOpType() !=
        LDAPConstants.OP_TYPE_ADD_RESPONSE)
    {
      throw new LDAPException(
              LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR,
              ERR_TASK_CLIENT_INVALID_RESPONSE_TYPE.get(
                responseMessage.getProtocolOpName()));
    }

    AddResponseProtocolOp addResponse =
         responseMessage.getAddResponseProtocolOp();
    Message errorMessage = addResponse.getErrorMessage();
    if (errorMessage != null) {
      throw new LDAPException(
              LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR,
              errorMessage);
    }
    return getTaskEntry(taskID);
  }

  /**
   * Gets all the ds-task entries from the task root.
   *
   * @return list of entries from the task root
   * @throws IOException if there is a stream communication problem
   * @throws LDAPException if there is a problem getting information
   *         out to the directory
   * @throws ASN1Exception if there is a problem with the encoding
   */
  public synchronized List<TaskEntry> getTaskEntries()
          throws LDAPException, IOException, ASN1Exception {
    List<Entry> entries = new ArrayList<Entry>();

    writeSearch(new SearchRequestProtocolOp(
            new ASN1OctetString(ConfigConstants.DN_TASK_ROOT),
            SearchScope.WHOLE_SUBTREE,
            DereferencePolicy.NEVER_DEREF_ALIASES,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            false,
            LDAPFilter.decode("(objectclass=ds-task)"),
            new LinkedHashSet<String>()));

    LDAPReader reader = connection.getLDAPReader();
    byte opType;
    do {
      LDAPMessage responseMessage = reader.readMessage();
      if (responseMessage == null) {
        throw new LDAPException(
                LDAPResultCode.CLIENT_SIDE_SERVER_DOWN,
                ERR_TASK_CLIENT_UNEXPECTED_CONNECTION_CLOSURE.get());
      } else {
        opType = responseMessage.getProtocolOpType();
        if (opType == LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY) {
          SearchResultEntryProtocolOp searchEntryOp =
                  responseMessage.getSearchResultEntryProtocolOp();
          SearchResultEntry entry = searchEntryOp.toSearchResultEntry();
          entries.add(entry);
        }
      }
    }
    while (opType != LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE);
    List<TaskEntry> taskEntries = new ArrayList<TaskEntry>(entries.size());
    for (Entry entry : entries) {
      taskEntries.add(new TaskEntry(entry));
    }
    return Collections.unmodifiableList(taskEntries);
  }

  /**
   * Gets the entry of the task whose ID is <code>id</code> from the directory.
   *
   * @param id of the entry to retrieve
   * @return Entry for the task
   * @throws IOException if there is a stream communication problem
   * @throws LDAPException if there is a problem getting information
   *         out to the directory
   * @throws ASN1Exception if there is a problem with the encoding
   * @throws TaskClientException if there is no task with the requested id
   */
  public synchronized TaskEntry getTaskEntry(String id)
          throws LDAPException, IOException, ASN1Exception, TaskClientException
  {
    Entry entry = null;

    writeSearch(new SearchRequestProtocolOp(
            new ASN1OctetString(ConfigConstants.DN_TASK_ROOT),
            SearchScope.WHOLE_SUBTREE,
            DereferencePolicy.NEVER_DEREF_ALIASES,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            false,
            LDAPFilter.decode("(ds-task-id=" + id + ")"),
            new LinkedHashSet<String>()));

    LDAPReader reader = connection.getLDAPReader();
    byte opType;
    do {
      LDAPMessage responseMessage = reader.readMessage();
      if (responseMessage == null) {
        Message message = ERR_TASK_CLIENT_UNEXPECTED_CONNECTION_CLOSURE.get();
        throw new LDAPException(UNAVAILABLE.getIntValue(), message);
      } else {
        opType = responseMessage.getProtocolOpType();
        if (opType == LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY) {
          SearchResultEntryProtocolOp searchEntryOp =
                  responseMessage.getSearchResultEntryProtocolOp();
          entry = searchEntryOp.toSearchResultEntry();
        }
      }
    }
    while (opType != LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE);
    if (entry == null) {
      throw new TaskClientException(ERR_TASK_CLIENT_UNKNOWN_TASK.get(id));
    }
    return new TaskEntry(entry);
  }


  /**
   * Changes that the state of the task in the backend to a canceled state.
   *
   * @param  id if the task to cancel
   * @return Entry of the task before the modification
   * @throws IOException if there is a stream communication problem
   * @throws LDAPException if there is a problem getting information
   *         out to the directory
   * @throws ASN1Exception if there is a problem with the encoding
   * @throws TaskClientException if there is no task with the requested id
   */
  public synchronized TaskEntry cancelTask(String id)
          throws TaskClientException, IOException, ASN1Exception, LDAPException
  {
    LDAPReader reader = connection.getLDAPReader();
    LDAPWriter writer = connection.getLDAPWriter();

    TaskEntry entry = getTaskEntry(id);
    TaskState state = entry.getTaskState();
    if (state != null) {
      if (!TaskState.isDone(state)) {

        ASN1OctetString dn = new ASN1OctetString(entry.getDN().toString());

        ArrayList<RawModification> mods = new ArrayList<RawModification>();

        ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
        String newState;
        if (TaskState.isPending(state)) {
          newState = TaskState.CANCELED_BEFORE_STARTING.name();
        } else {
          newState = TaskState.STOPPED_BY_ADMINISTRATOR.name();
        }
        values.add(new ASN1OctetString(newState));
        LDAPAttribute attr = new LDAPAttribute(ATTR_TASK_STATE, values);
        mods.add(new LDAPModification(ModificationType.REPLACE, attr));

        // We have to reset the start time or the scheduler will
        // reschedule to task.
        // attr = new LDAPAttribute(ATTR_TASK_SCHEDULED_START_TIME);
        // mods.add(new LDAPModification(ModificationType.DELETE, attr));

        ModifyRequestProtocolOp modRequest =
                new ModifyRequestProtocolOp(dn, mods);
        LDAPMessage requestMessage =
             new LDAPMessage(nextMessageID.getAndIncrement(), modRequest, null);

        writer.writeMessage(requestMessage);

        LDAPMessage responseMessage = reader.readMessage();

        if (responseMessage == null) {
          Message message = ERR_TASK_CLIENT_UNEXPECTED_CONNECTION_CLOSURE.get();
          throw new LDAPException(UNAVAILABLE.getIntValue(), message);
        }

        if (responseMessage.getProtocolOpType() !=
                LDAPConstants.OP_TYPE_MODIFY_RESPONSE)
        {
          throw new LDAPException(
                  LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR,
                  ERR_TASK_CLIENT_INVALID_RESPONSE_TYPE.get(
                    responseMessage.getProtocolOpName()));
        }

        ModifyResponseProtocolOp modResponse =
                responseMessage.getModifyResponseProtocolOp();
        Message errorMessage = modResponse.getErrorMessage();
        if (errorMessage != null) {
          throw new LDAPException(
                  LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR,
                  errorMessage);
        }
      } else {
        throw new TaskClientException(
                ERR_TASK_CLIENT_UNCANCELABLE_TASK.get(id));
      }
    } else {
      throw new TaskClientException(
              ERR_TASK_CLIENT_TASK_STATE_UNKNOWN.get(id));
    }
    return getTaskEntry(id);
  }


  /**
   * Writes a search to the directory writer.
   * @param searchRequest to write
   * @throws IOException if there is a stream communication problem
   */
  private void writeSearch(SearchRequestProtocolOp searchRequest)
          throws IOException {
    LDAPWriter writer = connection.getLDAPWriter();
    LDAPMessage requestMessage = new LDAPMessage(
            nextMessageID.getAndIncrement(),
            searchRequest,
            new ArrayList<LDAPControl>());

    // Send the request to the server and read the response.
    writer.writeMessage(requestMessage);
  }

}
