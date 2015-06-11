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
 *      Portions Copyright 2012-2015 ForgeRock AS
 */
package org.opends.server.extensions;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.admin.std.server.GoverningStructureRuleVirtualAttributeCfg;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.*;

import static org.opends.messages.ExtensionMessages.*;

/**
 * This class implements a virtual attribute provider that is meant to serve
 * the governingStructuralRule operational attribute as described in RFC 4512.
 */
public class GoverningStructureRuleVirtualAttributeProvider  extends
         VirtualAttributeProvider<GoverningStructureRuleVirtualAttributeCfg>
{
  /**
   * Creates a new instance of this governingStructureRule virtual attribute
   * provider.
   */
  public GoverningStructureRuleVirtualAttributeProvider()
  {
    super();

    // All initialization should be performed in the
    // initializeVirtualAttributeProvider method.
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
    DITStructureRule ditRule = getDITStructureRule(entry);
    if(ditRule !=null)
    {
      return Attributes.create(
          rule.getAttributeType(), String.valueOf(ditRule.getRuleID()));
    }
    return Attributes.empty(rule.getAttributeType());
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasValue(Entry entry, VirtualAttributeRule rule)
  {
    return getDITStructureRule(entry)!=null;
  }

  /** {@inheritDoc} */
  @Override
  public ConditionResult matchesSubstring(Entry entry,
                                          VirtualAttributeRule rule,
                                          ByteString subInitial,
                                          List<ByteString> subAny,
                                          ByteString subFinal)
  {
    // DITStructureRule cannot be used in substring matching.
    return ConditionResult.UNDEFINED;
  }

  /** {@inheritDoc} */
  @Override
  public ConditionResult greaterThanOrEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              ByteString value)
  {
    // DITStructureRule cannot be used in ordering matching.
    return ConditionResult.UNDEFINED;
  }

  /** {@inheritDoc} */
  @Override
  public ConditionResult lessThanOrEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              ByteString value)
  {
    // DITStructureRule cannot be used in ordering matching.
    return ConditionResult.UNDEFINED;
  }

  /** {@inheritDoc} */
  @Override
  public ConditionResult approximatelyEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              ByteString value)
  {
    // DITStructureRule cannot be used in approximate matching.
    return ConditionResult.UNDEFINED;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isSearchable(VirtualAttributeRule rule,
                              SearchOperation searchOperation,
                              boolean isPreIndexed)
  {
    //Non-searchable.
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void processSearch(VirtualAttributeRule rule,
                            SearchOperation searchOperation)
  {
    searchOperation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

    LocalizableMessage message = ERR_VATTR_NOT_SEARCHABLE.get(
            rule.getAttributeType().getNameOrOID());
    searchOperation.appendErrorMessage(message);
  }

  /** Checks if the entry matches the nameform. */
  private boolean matchesNameForm(NameForm nameForm,
                       AcceptRejectWarn structuralPolicy,
                       Entry entry)
  {
    RDN rdn = entry.getName().rdn();
    if (rdn != null)
    {
      // Make sure that all the required attributes are present.
      for (AttributeType t : nameForm.getRequiredAttributes())
      {
        if (!rdn.hasAttributeType(t)
            && structuralPolicy == AcceptRejectWarn.REJECT)
        {
          return false;
        }
      }

      // Make sure that all attributes in the RDN are allowed.
      int numAVAs = rdn.getNumValues();
      for (int i = 0; i < numAVAs; i++)
      {
        AttributeType t = rdn.getAttributeType(i);
        if (!nameForm.isRequiredOrOptional(t)
            && structuralPolicy == AcceptRejectWarn.REJECT)
        {
          return false;
        }
       }
     }
    return true;
  }

  /** Finds the appropriate DIT structure rule for an entry. */
  private DITStructureRule getDITStructureRule(Entry entry) {
    ObjectClass oc = entry.getStructuralObjectClass();
    if (oc == null) {
      return null;
    }
    List<NameForm> listForms = DirectoryServer.getNameForm(oc);
    NameForm nameForm = null;
    DITStructureRule ditRule = null;
    //We iterate over all the nameforms while creating the entry and
    //select the first one that matches. Since the entry exists, the same
    //algorithm should work fine to retrieve the nameform which was
    //applied while creating the entry.
    if (listForms != null)
    {
      boolean obsolete = true;
      AcceptRejectWarn structuralPolicy =
        DirectoryServer.getSingleStructuralObjectClassPolicy();
      for (NameForm nf : listForms)
      {
        if (!nf.isObsolete())
        {
          obsolete = false;
          if (matchesNameForm(nf, structuralPolicy, entry))
          {
           nameForm = nf;
           break;
          }
        }
      }
      if (nameForm != null && !obsolete)
      {
        ditRule = DirectoryServer.getDITStructureRule(nameForm);
      }
    }
    return ditRule;
  }
}

