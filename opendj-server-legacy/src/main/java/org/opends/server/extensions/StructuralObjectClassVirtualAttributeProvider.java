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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.server.StructuralObjectClassVirtualAttributeCfg;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.Entry;
import org.opends.server.types.VirtualAttributeRule;

import static org.opends.messages.ExtensionMessages.*;

/**
 * This class implements a virtual attribute provider that is meant to serve
 * the structuralObjectClass operational attribute as described in RFC 4512.
 */
public class StructuralObjectClassVirtualAttributeProvider
     extends VirtualAttributeProvider<StructuralObjectClassVirtualAttributeCfg>
{
  /** Creates a new instance of this structuralObjectClass virtual attribute provider. */
  public StructuralObjectClassVirtualAttributeProvider()
  {
    super();

    // All initialization should be performed in the
    // initializeVirtualAttributeProvider method.
  }

  @Override
  public boolean isMultiValued()
  {
    return false;
  }

  @Override
  public Attribute getValues(Entry entry, VirtualAttributeRule rule)
  {
    return Attributes.create(rule.getAttributeType(), entry
        .getStructuralObjectClass().getNameOrOID());
  }

  @Override
  public boolean hasValue(Entry entry, VirtualAttributeRule rule)
  {
    return entry.getStructuralObjectClass() != null;
  }

  @Override
  public ConditionResult matchesSubstring(Entry entry,
                                          VirtualAttributeRule rule,
                                          ByteString subInitial,
                                          List<ByteString> subAny,
                                          ByteString subFinal)
  {
    //Substring matching is not supported.
    return ConditionResult.UNDEFINED;
  }

  @Override
  public ConditionResult greaterThanOrEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              ByteString value)
  {
    // An object class can not be used for ordering.
    return ConditionResult.UNDEFINED;
  }

  @Override
  public ConditionResult lessThanOrEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              ByteString value)
  {
    // An object class can not be used for ordering.
    return ConditionResult.UNDEFINED;
  }

  @Override
  public ConditionResult approximatelyEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              ByteString value)
  {
    // An object class can not be used in approximate matching.
    return ConditionResult.UNDEFINED;
  }

  @Override
  public boolean isSearchable(VirtualAttributeRule rule,
                              SearchOperation searchOperation,
                              boolean isPreIndexed)
  {
    // This attribute is not searchable, since it will have the same value in
    // tons of entries.
    return false;
  }

  @Override
  public void processSearch(VirtualAttributeRule rule,
                            SearchOperation searchOperation)
  {
    searchOperation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

    LocalizableMessage message = ERR_VATTR_NOT_SEARCHABLE.get(
            rule.getAttributeType().getNameOrOID());
    searchOperation.appendErrorMessage(message);
  }
}
