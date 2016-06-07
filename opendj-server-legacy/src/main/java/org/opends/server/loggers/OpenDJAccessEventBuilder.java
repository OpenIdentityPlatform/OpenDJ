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

import static org.forgerock.json.JsonValue.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.forgerock.audit.events.AccessAuditEventBuilder;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.json.JsonValue;
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

  private OpenDJAccessAuditEventBuilder()
  {
    super();
  }

  @SuppressWarnings("rawtypes")
  public static <T> OpenDJAccessAuditEventBuilder<?> openDJAccessEvent()
  {
    return new OpenDJAccessAuditEventBuilder();
  }

  public T ldapAdditionalItems(Operation op)
  {
    String items = getAdditionalItemsAsString(op);
    if (!items.isEmpty())
    {
      getLdapValue().put("items", items);
    }
    return self();
  }

  public T ldapAttr(String attr)
  {
    getLdapValue().put("attr", attr);
    return self();
  }

  public T ldapConnectionId(long id)
  {
    getLdapValue().put("connId", id);
    return self();
  }

  public T ldapControls(Operation operation)
  {
    JsonValue ldapValue = getLdapValue();
    List<Control> requestControls = operation.getRequestControls();
    if (!requestControls.isEmpty())
    {
      ldapValue.put("reqControls", getControlsAsString(requestControls));
    }
    List<Control> responseControls = operation.getResponseControls();
    if (!responseControls.isEmpty())
    {
      ldapValue.put("respControls", getControlsAsString(responseControls));
    }
    return self();
  }

  public T ldapDn(String dn)
  {
    getLdapValue().put("dn", dn);
    return self();
  }

  public T ldapFailureMessage(String msg)
  {
    getLdapValue().put("failureReason", msg);
    return self();
  }

  public T ldapIds(Operation op)
  {
    JsonValue ldapValue = getLdapValue();
    ldapValue.put("connId", op.getConnectionID());
    ldapValue.put("msgId", op.getMessageID());
    return self();
  }

  public T ldapIdToAbandon(int id)
  {
    getLdapValue().put("idToAbandon", id);
    return self();
  }

  public T ldapMaskedResultAndMessage(Operation operation)
  {
    JsonValue ldapValue = getLdapValue();
    if (operation.getMaskedResultCode() != null)
    {
      ldapValue.put("maskedResult", operation.getMaskedResultCode().intValue());
    }
    final LocalizableMessageBuilder maskedMsg = operation.getMaskedErrorMessage();
    if (maskedMsg != null && maskedMsg.length() > 0)
    {
      ldapValue.put("maskedMessage", maskedMsg.toString());
    }
    return self();
  }

  public T ldapMessage(LocalizableMessage msg)
  {
    if (msg != null)
    {
      getLdapValue().put("message", msg.toString());
    }
    return self();
  }

  public T ldapName(String name)
  {
    getLdapValue().put("name", name);
    return self();
  }

  public T ldapModifyDN(ModifyDNOperation modifyDNOperation)
  {
    JsonValue ldapValue = getLdapValue();
    ldapValue.put("newRDN", modifyDNOperation.getRawNewRDN().toString());
    ldapValue.put("newSup", modifyDNOperation.getRawNewSuperior().toString());
    ldapValue.put("deleteOldRDN", modifyDNOperation.deleteOldRDN());
    return self();
  }

  public T ldapNEntries(int nbEntries)
  {
    getLdapValue().put("nentries", nbEntries);
    return self();
  }

  public T ldapOid(String oid)
  {
    getLdapValue().put("oid", oid);
    return self();
  }

  public T ldapProtocolVersion(String version)
  {
    getLdapValue().put("version", version);
    return self();
  }

  public T ldapReason(DisconnectReason reason)
  {
    getLdapValue().put("reason", reason.toString());
    return self();
  }

  public T ldapSearch(SearchOperation searchOperation)
  {
    JsonValue ldapValue = getLdapValue();
    // for search base, re-uses the "dn" field
    ldapValue.put("dn", searchOperation.getRawBaseDN().toString());
    ldapValue.put("scope", searchOperation.getScope().toString());
    ldapValue.put("filter", searchOperation.getRawFilter().toString());

    final Set<String> attrs = searchOperation.getAttributes();
    if ((attrs == null) || attrs.isEmpty())
    {
      ldapValue.put("attrs", Arrays.asList("ALL"));
    }
    else
    {
      ldapValue.put("attrs", new ArrayList<>(attrs));
    }
    return self();
  }

  public T ldapSync(Operation operation)
  {
    if (operation.isSynchronizationOperation())
    {
      getLdapValue().put("opType", "sync");
    }
    return self();
  }

  public T ldapAuthType(String type)
  {
    getLdapValue().put("authType", type);
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

  private JsonValue getLdapValue()
  {
    if (!jsonValue.isDefined("ldap"))
    {
      jsonValue.put("ldap", object());
    }
    return jsonValue.get("ldap");
  }
}
