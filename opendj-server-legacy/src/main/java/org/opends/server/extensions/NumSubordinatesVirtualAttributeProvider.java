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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.server.NumSubordinatesVirtualAttributeCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.*;

import static org.opends.messages.ExtensionMessages.*;

/**
 * This class implements a virtual attribute provider that is meant to serve the
 * hasSubordinates operational attribute as described in
 * draft-ietf-boreham-numsubordinates.
 */
public class NumSubordinatesVirtualAttributeProvider
    extends VirtualAttributeProvider<NumSubordinatesVirtualAttributeCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Creates a new instance of this NumSubordinates virtual attribute provider. */
  public NumSubordinatesVirtualAttributeProvider()
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
    Backend backend = DirectoryServer.getBackend(entry.getName());

    try
    {
      long count = backend.getNumberOfChildren(entry.getName());
      if(count >= 0)
      {
        return Attributes.create(rule.getAttributeType(), String.valueOf(count));
      }
    }
    catch(DirectoryException de)
    {
      logger.traceException(de);
    }

    return Attributes.empty(rule.getAttributeType());
  }

  @Override
  public boolean hasValue(Entry entry, VirtualAttributeRule rule)
  {
    Backend<?> backend = DirectoryServer.getBackend(entry.getName());

    try
    {
       return backend.getNumberOfChildren(entry.getName()) >= 0;
    }
    catch(DirectoryException de)
    {
      logger.traceException(de);
      return false;
    }
  }

  @Override
  public boolean hasValue(Entry entry, VirtualAttributeRule rule, ByteString value)
  {
    Backend<?> backend = DirectoryServer.getBackend(entry.getName());
    try
    {
      long count = backend.getNumberOfChildren(entry.getName());
      return count >= 0 && Long.parseLong(value.toString()) == count;
    }
    catch (NumberFormatException | DirectoryException e)
    {
      logger.traceException(e);
      return false;
    }
  }

  @Override
  public ConditionResult matchesSubstring(Entry entry,
                                          VirtualAttributeRule rule,
                                          ByteString subInitial,
                                          List<ByteString> subAny,
                                          ByteString subFinal)
  {
    // This virtual attribute does not support substring matching.
    return ConditionResult.UNDEFINED;
  }

  @Override
  public ConditionResult approximatelyEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              ByteString value)
  {
    // This virtual attribute does not support approximate matching.
    return ConditionResult.UNDEFINED;
  }

  @Override
  public boolean isSearchable(VirtualAttributeRule rule,
                              SearchOperation searchOperation,
                              boolean isPreIndexed)
  {
    return false;
  }

  @Override
  public void processSearch(VirtualAttributeRule rule,
                            SearchOperation searchOperation)
  {
    searchOperation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

    LocalizableMessage message = ERR_NUMSUBORDINATES_VATTR_NOT_SEARCHABLE.get(
            rule.getAttributeType().getNameOrOID());
    searchOperation.appendErrorMessage(message);
  }
}
