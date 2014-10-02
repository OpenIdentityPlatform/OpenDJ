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

import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.admin.std.server.UserDefinedVirtualAttributeCfg;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.Entry;
import org.opends.server.types.VirtualAttributeRule;
import org.opends.server.util.ServerConstants;

import static org.opends.messages.ExtensionMessages.*;

/**
 * This class implements a virtual attribute provider that specifies the
 * changelog attribute of the root DSE entry that contain the baseDn of the ECL.
 */
class ChangelogBaseDNVirtualAttributeProvider extends VirtualAttributeProvider<UserDefinedVirtualAttributeCfg>
{

  /**
   * The base DN of the changelog is a constant.
   * TODO: This shouldn't be a virtual attribute, but directly
   * registered in the RootDSE.
   */
  private Attribute values;


  /**
   * Creates a new instance of this member virtual attribute provider.
   */
  public ChangelogBaseDNVirtualAttributeProvider()
  {
    super();
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
    if (values == null)
    {
      values = Attributes.create(rule.getAttributeType(),
          ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT);
    }
    return values;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isSearchable(VirtualAttributeRule rule,
                              SearchOperation searchOperation,
                              boolean isPreIndexed)
  {
    // We do not allow search as it may be present is too many entries.
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void processSearch(VirtualAttributeRule rule, SearchOperation searchOperation)
  {
    searchOperation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
    searchOperation.appendErrorMessage(ERR_CHANGELOGBASEDN_VATTR_NOT_SEARCHABLE.get(
            rule.getAttributeType().getNameOrOID()));
  }

}

