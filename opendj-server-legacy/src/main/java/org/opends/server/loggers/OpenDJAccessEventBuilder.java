/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.loggers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.forgerock.audit.events.AccessAuditEventBuilder;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.json.JsonValue;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.util.Reject;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.AdditionalLogItem;
import org.opends.server.types.Control;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.Operation;

/**
 * Builder for /audit/access events specific to OpenDJ. This builder add LDAP
 * specific fields to the common fields defined in AccessAuditEventBuilder.
 *
 * @param <T>
 *          This builder.
 */
class OpenDJAccessAuditEventBuilder<T extends OpenDJAccessAuditEventBuilder<T>> extends AccessAuditEventBuilder<T>
{
  private JsonValue opRequest;
  private JsonValue opResponse;

  private OpenDJAccessAuditEventBuilder()
  {
    super();
  }

  @SuppressWarnings("rawtypes")
  public static <T> OpenDJAccessAuditEventBuilder<?> openDJAccessEvent()
  {
    return ((OpenDJAccessAuditEventBuilder<?>) new OpenDJAccessAuditEventBuilder()).eventName("DJ-LDAP");
  }

  public T ldapAdditionalItems(Operation op)
  {
    String items = getAdditionalItemsAsString(op);
    if (!items.isEmpty())
    {
      getOpResponse().put("additionalItems", items);
    }
    return self();
  }

  public T ldapAttr(String attr)
  {
    getOpRequest().put("attr", attr);
    return self();
  }

  public T ldapConnectionId(long id)
  {
    getOpRequest().put("connId", id);
    return self();
  }

  public T ldapRequestControls(Operation operation)
  {
    List<Control> requestControls = operation.getRequestControls();
    if (!requestControls.isEmpty())
    {
      getOpRequest().put("controls", getControlsAsString(requestControls));
    }
    return self();
  }

  public T ldapResponseControls(Operation operation)
  {
    List<Control> responseControls = operation.getResponseControls();
    if (!responseControls.isEmpty())
    {
      getOpResponse().put("controls", getControlsAsString(responseControls));
    }
    return self();
  }

  public T ldapDn(String dn)
  {
    getOpRequest().put("dn", dn);
    return self();
  }

  public T ldapFailureMessage(String msg)
  {
    getOpResponse().put("failureReason", msg);
    return self();
  }

  public T ldapIds(Operation op)
  {
    getOpRequest().put("connId", op.getConnectionID());
    getOpRequest().put("msgId", op.getMessageID());
    return self();
  }

  public T ldapIdToAbandon(int id)
  {
    getOpRequest().put("idToAbandon", id);
    return self();
  }

  public T ldapMaskedResultAndMessage(Operation operation)
  {
    if (operation.getMaskedResultCode() != null)
    {
      getOpResponse().put("maskedResult", operation.getMaskedResultCode().intValue());
    }
    final LocalizableMessageBuilder maskedMsg = operation.getMaskedErrorMessage();
    if (maskedMsg != null && maskedMsg.length() > 0)
    {
      getOpResponse().put("maskedMessage", maskedMsg.toString());
    }
    return self();
  }

  public T ldapMessage(LocalizableMessage msg)
  {
    if (msg != null)
    {
      getOpRequest().put("message", msg.toString());
    }
    return self();
  }

  public T ldapName(String name)
  {
    getOpRequest().put("name", name);
    return self();
  }

  public T ldapModifyDN(ModifyDNOperation modifyDNOperation)
  {
    getOpRequest().put("newRDN", modifyDNOperation.getRawNewRDN().toString());
    final ByteString rawNewSuperior = modifyDNOperation.getRawNewSuperior();
    if (rawNewSuperior != null)
    {
      getOpRequest().put("newSup", rawNewSuperior.toString());
    }
    getOpRequest().put("deleteOldRDN", modifyDNOperation.deleteOldRDN());
    return self();
  }

  public T ldapNEntries(int nbEntries)
  {
    getOpResponse().put("nentries", nbEntries);
    return self();
  }

  public T ldapOid(String oid)
  {
    getOpRequest().put("oid", oid);
    return self();
  }

  public T ldapProtocolVersion(String version)
  {
    getOpRequest().put("version", version);
    return self();
  }

  public T ldapReason(DisconnectReason reason)
  {
    getOpResponse().put("reason", reason.toString());
    return self();
  }

  public T ldapSearch(SearchOperation searchOperation)
  {
    // for search base, re-uses the "dn" field
    getOpRequest().put("dn", searchOperation.getRawBaseDN().toString());
    getOpRequest().put("scope", searchOperation.getScope().toString());
    getOpRequest().put("filter", searchOperation.getRawFilter().toString());

    final Set<String> attrs = searchOperation.getAttributes();
    if (attrs == null || attrs.isEmpty())
    {
      getOpRequest().put("attrs", Arrays.asList("ALL"));
    }
    else
    {
      getOpRequest().put("attrs", new ArrayList<>(attrs));
    }
    return self();
  }

  public T ldapSync(Operation operation)
  {
    if (operation.isSynchronizationOperation())
    {
      getOpRequest().put("opType", "sync");
    }
    return self();
  }

  public T ldapAuthType(String type)
  {
    getOpRequest().put("authType", type);
    return self();
  }

  public T runAs(String id)
  {
    Reject.ifNull(id);
    jsonValue.put("runAs", id);
    return self();
  }

  private List<String> getControlsAsString(List<Control> controls)
  {
    List<String> list = new ArrayList<>();
    for (final Control control : controls)
    {
      list.add(control.getOID());
    }
    return list;
  }

  private String getAdditionalItemsAsString(Operation operation)
  {
    StringBuilder items = new StringBuilder();
    for (final AdditionalLogItem item : operation.getAdditionalLogItems())
    {
      items.append(' ');
      item.toString(items);
    }
    return items.toString();
  }

  private JsonValue getOpRequest()
  {
    if (opRequest == null)
    {
      opRequest = jsonValue.get("request");
    }
    return opRequest;
  }

  private JsonValue getOpResponse()
  {
    if (opResponse == null)
    {
      opResponse = jsonValue.get("response");
    }
    return opResponse;
  }
}
