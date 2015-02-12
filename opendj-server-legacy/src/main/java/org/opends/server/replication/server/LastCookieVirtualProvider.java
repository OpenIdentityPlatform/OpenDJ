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
package org.opends.server.replication.server;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.admin.std.server.UserDefinedVirtualAttributeCfg;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.Entry;
import org.opends.server.types.VirtualAttributeRule;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.replication.plugin.MultimasterReplication.*;

/**
 * This class implements a virtual attribute provider in the root-dse entry
 * that contains the last (newest) cookie (cross domain state)
 * available in the server.
 */
class LastCookieVirtualProvider extends VirtualAttributeProvider<UserDefinedVirtualAttributeCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private final ReplicationServer replicationServer;

  /**
   * Creates a new instance of this member virtual attribute provider.
   *
   * @param replicationServer
   *            The replication server.
   */
  public LastCookieVirtualProvider(ReplicationServer replicationServer)
  {
    this.replicationServer = replicationServer;
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasValue(Entry entry, VirtualAttributeRule rule)
  {
    // There's only a value for the rootDSE, i.e. the Null DN.
    return entry.getName().isRootDN();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isMultiValued()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public Attribute getValues(Entry entry, VirtualAttributeRule rule)
  {
    try
    {
      if (replicationServer != null)
      {
        String newestCookie = replicationServer.getNewestECLCookie(getExcludedChangelogDomains()).toString();
        return Attributes.create(rule.getAttributeType(), newestCookie);
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }
    return Attributes.empty(rule.getAttributeType());
  }

  /** {@inheritDoc} */
  @Override
  public boolean isSearchable(VirtualAttributeRule rule,
                              SearchOperation searchOperation,
                              boolean isPreIndexed)
  {
    // We do not allow search for the lastCookie. It's a read-only
    // attribute of the RootDSE.
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void processSearch(VirtualAttributeRule rule,
                            SearchOperation searchOperation)
  {
    searchOperation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
    searchOperation.appendErrorMessage(ERR_LASTCOOKIE_VATTR_NOT_SEARCHABLE.get(
            rule.getAttributeType().getNameOrOID()));
  }

}
