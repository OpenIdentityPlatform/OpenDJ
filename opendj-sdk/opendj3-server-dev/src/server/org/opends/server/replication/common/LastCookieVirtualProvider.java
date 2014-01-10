/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS
 */
package org.opends.server.replication.common;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.UserDefinedVirtualAttributeCfg;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.plugin.MultimasterReplication;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.util.ServerConstants;
import org.opends.server.workflowelement.externalchangelog.ECLWorkflowElement;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;

/**
 * This class implements a virtual attribute provider in the root-dse entry
 * that contains the last (newest) cookie (cross domain state)
 * available in the server.
 */
public class LastCookieVirtualProvider
   extends VirtualAttributeProvider<UserDefinedVirtualAttributeCfg>
   implements ConfigurationChangeListener<UserDefinedVirtualAttributeCfg>
{

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * Creates a new instance of this member virtual attribute provider.
   */
  public LastCookieVirtualProvider()
  {
    super();

    // All initialization should be performed in the
    // initializeVirtualAttributeProvider method.
  }

  /** {@inheritDoc} */
  @Override()
  public boolean hasValue(Entry entry, VirtualAttributeRule rule)
  {
    // There's only a value for the rootDSE, i.e. the Null DN.
    return entry.getName().isRootDN();

  }

  /** {@inheritDoc} */
  @Override()
  public boolean isMultiValued()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override()
  public Set<AttributeValue> getValues(Entry entry,VirtualAttributeRule rule)
  {
    try
    {
      ECLWorkflowElement eclwe = (ECLWorkflowElement)
      DirectoryServer.getWorkflowElement("EXTERNAL CHANGE LOG");
      if (eclwe!=null)
      {
        // Set a list of excluded domains (also exclude 'cn=changelog' itself)
        Set<String> excludedDomains =
          MultimasterReplication.getECLDisabledDomains();
        excludedDomains.add(ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT);

        final ReplicationServer rs = eclwe.getReplicationServer();
        String newestCookie = rs.getNewestECLCookie(excludedDomains).toString();
        final ByteString cookie = ByteString.valueOf(newestCookie);
        return Collections.singleton(AttributeValues.create(cookie, cookie));
      }
    }
    catch (Exception e)
    {
      TRACER.debugCaught(DebugLogLevel.ERROR, e);
    }
    return Collections.emptySet();
  }

  /** {@inheritDoc} */
  @Override()
  public boolean isSearchable(VirtualAttributeRule rule,
                              SearchOperation searchOperation,
                              boolean isPreIndexed)
  {
    // We do not allow search for the lastCookie. It's a read-only
    // attribute of the RootDSE.
    return false;
  }

  /** {@inheritDoc} */
  @Override()
  public void processSearch(VirtualAttributeRule rule,
                            SearchOperation searchOperation)
  {
    searchOperation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
    final Message message = ERR_LASTCOOKIE_VATTR_NOT_SEARCHABLE.get(
            rule.getAttributeType().getNameOrOID());
    searchOperation.appendErrorMessage(message);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
                      UserDefinedVirtualAttributeCfg configuration,
                      List<Message> unacceptableReasons)
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(
                                 UserDefinedVirtualAttributeCfg configuration)
  {
    return new ConfigChangeResult(ResultCode.OTHER, false);
  }
}
