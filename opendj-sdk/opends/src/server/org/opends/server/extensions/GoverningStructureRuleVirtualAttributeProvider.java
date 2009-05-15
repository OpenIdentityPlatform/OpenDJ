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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.opends.messages.Message;
import org.opends.server.admin.std.server.
        GoverningStructureRuleVirtualAttributeCfg;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.config.ConfigException;
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



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializeVirtualAttributeProvider(
                      GoverningStructureRuleVirtualAttributeCfg configuration)
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
    DITStructureRule ditRule = getDITStructureRule(entry);

    if(ditRule !=null)
    {
      return Collections.singleton(AttributeValues.create(
                  rule.getAttributeType(),
                  String.valueOf(ditRule.getRuleID())));
    }

    return Collections.<AttributeValue>emptySet();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean hasValue(Entry entry, VirtualAttributeRule rule)
  {
    return getDITStructureRule(entry)!=null;
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
    // DITStructureRule cannot be used in substring matching.
    return ConditionResult.UNDEFINED;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ConditionResult greaterThanOrEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              AttributeValue value)
  {
    // DITStructureRule cannot be used in ordering matching.
    return ConditionResult.UNDEFINED;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ConditionResult lessThanOrEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              AttributeValue value)
  {
    // DITStructureRule cannot be used in ordering matching.
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
    // DITStructureRule cannot be used in approximate matching.
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
    //Non-searchable for unindexed searches.
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

    Message message = ERR_VATTR_NOT_SEARCHABLE.get(
            rule.getAttributeType().getNameOrOID());
    searchOperation.appendErrorMessage(message);
  }



  //Checks if the entry matches the nameform.
  private boolean matchesNameForm(NameForm nameForm,
                       AcceptRejectWarn structuralPolicy,
                       Entry entry)
  {
    RDN rdn = entry.getDN().getRDN();
    if (rdn != null)
    {
      // Make sure that all the required attributes are present.
      for (AttributeType t : nameForm.getRequiredAttributes())
      {
        if (! rdn.hasAttributeType(t))
        {
          if (structuralPolicy == AcceptRejectWarn.REJECT)
          {
            return false;
          }
        }
      }

      // Make sure that all attributes in the RDN are allowed.
      int numAVAs = rdn.getNumValues();
      for (int i = 0; i < numAVAs; i++)
      {
        AttributeType t = rdn.getAttributeType(i);
        if (! nameForm.isRequiredOrOptional(t))
        {
          if (structuralPolicy == AcceptRejectWarn.REJECT)
          {
            return false;
          }
        }
       }
     }
    return true;
  }



  //Finds the appropriate DIT structure rule for an entry.
  private DITStructureRule getDITStructureRule(Entry entry)
  {
    ObjectClass oc = entry.getStructuralObjectClass();
    List<NameForm> listForms = DirectoryServer.getNameForm(oc);
    NameForm nameForm = null;
    DITStructureRule ditRule = null;
    //We iterate over all the nameforms while creating the entry and
    //select the first one that matches. Since the entry exists, the same
    //algorithm should work fine to retrieve the nameform which was
    //applied while creating the entry.
    if(listForms != null)
    {
      boolean obsolete = true;
      AcceptRejectWarn structuralPolicy =
         DirectoryServer.getSingleStructuralObjectClassPolicy();
      for(NameForm nf : listForms)
      {
        if(!nf.isObsolete())
        {
          obsolete = false;
          if(matchesNameForm(nf,
                  structuralPolicy, entry))
          {
           nameForm = nf;
           break;
          }
        }
      }
      if( nameForm != null && !obsolete)
      {
        ditRule =
                DirectoryServer.getDITStructureRule(nameForm);
      }
    }
    return ditRule;
  }
}

