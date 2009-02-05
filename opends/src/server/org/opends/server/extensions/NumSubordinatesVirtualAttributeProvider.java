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
 */
package org.opends.server.extensions;



import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.opends.messages.Message;
import org.opends.server.admin.std.server.NumSubordinatesVirtualAttributeCfg;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.api.Backend;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;



/**
 * This class implements a virtual attribute provider that is meant to serve the
 * hasSubordinates operational attribute as described in
 * draft-ietf-boreham-numsubordinates.
 */
public class NumSubordinatesVirtualAttributeProvider
    extends VirtualAttributeProvider<NumSubordinatesVirtualAttributeCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * Creates a new instance of this NumSubordinates virtual attribute provider.
   */
  public NumSubordinatesVirtualAttributeProvider()
  {
    super();

    // All initialization should be performed in the
    // initializeVirtualAttributeProvider method.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializeVirtualAttributeProvider(
                            NumSubordinatesVirtualAttributeCfg configuration)
         throws ConfigException, InitializationException
  {
    // No initialization is required.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isMultiValued()
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public Set<AttributeValue> getValues(Entry entry,
                                       VirtualAttributeRule rule)
  {
    Backend backend = DirectoryServer.getBackend(entry.getDN());

    try
    {
      long count = backend.numSubordinates(entry.getDN(), false);
      if(count >= 0)
      {
        AttributeValue value =
            AttributeValues.create(ByteString.valueOf(String.valueOf(count)),
                ByteString.valueOf(String.valueOf(count)));
        return Collections.singleton(value);
      }
    }
    catch(DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }
    }

    return Collections.emptySet();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean hasValue(Entry entry, VirtualAttributeRule rule)
  {
    Backend backend = DirectoryServer.getBackend(entry.getDN());

    try
    {
       return backend.numSubordinates(entry.getDN(), false) >= 0;
    }
    catch(DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      return false;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean hasValue(Entry entry, VirtualAttributeRule rule,
                          AttributeValue value)
  {
     Backend backend = DirectoryServer.getBackend(entry.getDN());

    try
    {
      long count = backend.numSubordinates(entry.getDN(), false);
      if(count >= 0)
      {
        return Long.parseLong(value.getNormalizedValue().toString())
            == count;
      }
      return false;
    }
    catch(DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      return false;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ConditionResult matchesSubstring(Entry entry,
                                          VirtualAttributeRule rule,
                                          ByteString subInitial,
                                          List<ByteString> subAny,
                                          ByteString subFinal)
  {
    // This virtual attribute does not support substring matching.
    return ConditionResult.UNDEFINED;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ConditionResult approximatelyEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              AttributeValue value)
  {
    // This virtual attribute does not support approximate matching.
    return ConditionResult.UNDEFINED;
  }



  /**
   * {@inheritDoc}.  This virtual attribute will support search operations only
   * if one of the following is true about the search filter:
   * <UL>
   *   <LI>It is an equality filter targeting the associated attribute
   *       type.</LI>
   *   <LI>It is an AND filter in which at least one of the components is an
   *       equality filter targeting the associated attribute type.</LI>
   *   <LI>It is an OR filter in which all of the components are equality
   *       filters targeting the associated attribute type.</LI>
   * </UL>
   */
  @Override()
  public boolean isSearchable(VirtualAttributeRule rule,
                              SearchOperation searchOperation)
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void processSearch(VirtualAttributeRule rule,
                            SearchOperation searchOperation)
  {
    searchOperation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

    Message message = ERR_NUMSUBORDINATES_VATTR_NOT_SEARCHABLE.get(
            rule.getAttributeType().getNameOrOID());
    searchOperation.appendErrorMessage(message);
  }
}

