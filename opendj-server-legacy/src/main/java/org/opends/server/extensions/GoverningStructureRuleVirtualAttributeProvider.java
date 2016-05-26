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

import java.util.Collection;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.AVA;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.DITStructureRule;
import org.forgerock.opendj.ldap.schema.NameForm;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.server.config.server.GoverningStructureRuleVirtualAttributeCfg;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.AcceptRejectWarn;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.Entry;
import org.opends.server.types.Schema;
import org.opends.server.types.VirtualAttributeRule;

import static org.opends.messages.ExtensionMessages.*;

/**
 * This class implements a virtual attribute provider that is meant to serve
 * the governingStructuralRule operational attribute as described in RFC 4512.
 */
public class GoverningStructureRuleVirtualAttributeProvider  extends
         VirtualAttributeProvider<GoverningStructureRuleVirtualAttributeCfg>
{
  /** Creates a new instance of this governingStructureRule virtual attribute provider. */
  public GoverningStructureRuleVirtualAttributeProvider()
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
    DITStructureRule ditRule = getDITStructureRule(entry);
    if(ditRule !=null)
    {
      return Attributes.create(
          rule.getAttributeType(), String.valueOf(ditRule.getRuleID()));
    }
    return Attributes.empty(rule.getAttributeType());
  }

  @Override
  public boolean hasValue(Entry entry, VirtualAttributeRule rule)
  {
    return getDITStructureRule(entry)!=null;
  }

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

  @Override
  public ConditionResult greaterThanOrEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              ByteString value)
  {
    // DITStructureRule cannot be used in ordering matching.
    return ConditionResult.UNDEFINED;
  }

  @Override
  public ConditionResult lessThanOrEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              ByteString value)
  {
    // DITStructureRule cannot be used in ordering matching.
    return ConditionResult.UNDEFINED;
  }

  @Override
  public ConditionResult approximatelyEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              ByteString value)
  {
    // DITStructureRule cannot be used in approximate matching.
    return ConditionResult.UNDEFINED;
  }

  @Override
  public boolean isSearchable(VirtualAttributeRule rule,
                              SearchOperation searchOperation,
                              boolean isPreIndexed)
  {
    //Non-searchable.
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
      for (AVA ava : rdn)
      {
        AttributeType t = ava.getAttributeType();
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
    Schema schema = DirectoryServer.getSchema();
    Collection<NameForm> listForms = schema.getNameForm(oc);
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
        Collection<DITStructureRule> ditRules = schema.getDITStructureRules(nameForm);
        if (!ditRules.isEmpty())
        {
          ditRule = ditRules.iterator().next();
        }
      }
    }
    return ditRule;
  }
}
