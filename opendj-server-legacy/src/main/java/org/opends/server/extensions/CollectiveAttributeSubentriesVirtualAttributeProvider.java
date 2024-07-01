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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.server.
        CollectiveAttributeSubentriesVirtualAttributeCfg;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.*;

import static org.opends.messages.ExtensionMessages.*;

/**
 * This class implements a virtual attribute provider to serve the
 * collectiveAttributeSubentries operational attribute as described
 * in RFC 3671.
 */
public class CollectiveAttributeSubentriesVirtualAttributeProvider
        extends VirtualAttributeProvider<
        CollectiveAttributeSubentriesVirtualAttributeCfg>
{
  /** Creates a new instance of this collectiveAttributeSubentries virtual attribute provider. */
  public CollectiveAttributeSubentriesVirtualAttributeProvider()
  {
    super();

    // All initialization should be performed in the
    // initializeVirtualAttributeProvider method.
  }

  @Override
  public boolean isMultiValued()
  {
    return true;
  }

  @Override
  public Attribute getValues(Entry entry, VirtualAttributeRule rule)
  {
    AttributeBuilder builder = new AttributeBuilder(rule.getAttributeType());
    if (!entry.isSubentry() && !entry.isLDAPSubentry())
    {
      List<SubEntry> subentries = DirectoryServer.getSubentryManager()
          .getCollectiveSubentries(entry);

      for (SubEntry subentry : subentries)
      {
        if (subentry.isCollective() ||
            subentry.isInheritedCollective())
        {
          builder.add(subentry.getDN().toString());
        }
      }
    }
    return builder.toAttribute();
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

    LocalizableMessage message =
            ERR_COLLECTIVEATTRIBUTESUBENTRIES_VATTR_NOT_SEARCHABLE.get(
            rule.getAttributeType().getNameOrOID());
    searchOperation.appendErrorMessage(message);
  }
}
