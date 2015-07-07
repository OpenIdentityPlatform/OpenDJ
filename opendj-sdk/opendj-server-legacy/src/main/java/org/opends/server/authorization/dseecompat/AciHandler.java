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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 *      Portions Copyright 2013 Manuel Gaupp
 */
package org.opends.server.authorization.dseecompat;

import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.admin.std.server.DseeCompatAccessControlHandlerCfg;
import org.opends.server.api.AccessControlHandler;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConfigHandler;
import org.opends.server.backends.pluggable.SuffixContainer;
import org.opends.server.controls.GetEffectiveRightsRequestControl;
import org.opends.server.core.*;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.types.*;
import org.opends.server.workflowelement.localbackend.*;

import static org.opends.messages.AccessControlMessages.*;
import static org.opends.server.authorization.dseecompat.Aci.*;
import static org.opends.server.authorization.dseecompat.EnumEvalReason.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * The AciHandler class performs the main processing for the dseecompat package.
 */
public final class AciHandler extends
    AccessControlHandler<DseeCompatAccessControlHandlerCfg>
{
  /**
   * String used to indicate that the evaluating ACI had a all
   * operational attributes targetattr match (targetattr="+").
   */
  public static final String ALL_OP_ATTRS_MATCHED = "allOpAttrsMatched";

  /**
   * String used to indicate that the evaluating ACI had a all user
   * attributes targetattr match (targetattr="*").
   */
  public static final String ALL_USER_ATTRS_MATCHED = "allUserAttrsMatched";

  /**
   * String used to save the original authorization entry in an
   * operation attachment if a proxied authorization control was seen.
   */
  public static final String ORIG_AUTH_ENTRY = "origAuthorizationEntry";

  /** Attribute type corresponding to "aci" attribute. */
  static AttributeType aciType;

  /** Attribute type corresponding to global "ds-cfg-global-aci" attribute. */
  static AttributeType globalAciType;

  /** Attribute type corresponding to "debugsearchindex" attribute. */
  private static AttributeType debugSearchIndex;

  /** DN corresponding to "debugsearchindex" attribute type. */
  private static DN debugSearchIndexDN;

  /**
   * Attribute type corresponding to the "ref" attribute type. Used in
   * the search reference access check.
   */
  private static AttributeType refAttrType;
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  static
  {
    initStatics();
  }



  /**
   * We initialize these for each new AciHandler so that we can clear out the
   * stale references that can occur during an in-core restart.
   */
  private static void initStatics()
  {
    aciType = getAttributeType("aci");
    globalAciType = getAttributeType(ATTR_AUTHZ_GLOBAL_ACI);
    debugSearchIndex = getAttributeType(SuffixContainer.ATTR_DEBUG_SEARCH_INDEX);
    refAttrType = getAttributeType(ATTR_REFERRAL_URL);

    try
    {
      debugSearchIndexDN = DN.valueOf("cn=debugsearch");
    }
    catch (DirectoryException ex)
    {
      // Should never happen.
    }
  }

  private static AttributeType getAttributeType(String name)
  {
    AttributeType attrType = DirectoryServer.getAttributeType(name);
    if (attrType == null)
    {
      attrType = DirectoryServer.getDefaultAttributeType(name);
    }
    return attrType;
  }



  /** The list that holds that ACIs keyed by the DN of the entry holding the ACI. */
  private AciList aciList;

  /**
   * The listener that handles ACI changes caused by LDAP operations,
   * ACI decode failure alert logging and backend initialization ACI list adjustment.
   */
  private AciListenerManager aciListenerMgr;

  /** Creates a new DSEE-compatible access control handler. */
  public AciHandler()
  {
    // No implementation required. All initialization should be done in
    // the intializeAccessControlHandler method.
  }

  /** {@inheritDoc} */
  @Override
  public void filterEntry(Operation operation,
      SearchResultEntry unfilteredEntry, SearchResultEntry filteredEntry)
  {
    AciLDAPOperationContainer container =
        new AciLDAPOperationContainer(operation, ACI_READ, unfilteredEntry);

    // Proxy access check has already been done for this entry in the
    // maySend method, set the seen flag to true to bypass any proxy check.
    container.setSeenEntry(true);

    boolean skipCheck = skipAccessCheck(operation);
    if (!skipCheck)
    {
      filterEntry(container, filteredEntry);
    }

    if (container.hasGetEffectiveRightsControl())
    {
      AciEffectiveRights.addRightsToEntry(this,
          ((SearchOperation) operation).getAttributes(), container,
          filteredEntry, skipCheck);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void finalizeAccessControlHandler()
  {
    aciListenerMgr.finalizeListenerManager();
    AciEffectiveRights.finalizeOnShutdown();
    DirectoryServer.deregisterSupportedControl(OID_GET_EFFECTIVE_RIGHTS);
  }

  /** {@inheritDoc} */
  @Override
  public void initializeAccessControlHandler(
      DseeCompatAccessControlHandlerCfg configuration)
      throws ConfigException, InitializationException
  {
    initStatics();
    DN configurationDN = configuration.dn();
    aciList = new AciList(configurationDN);
    aciListenerMgr = new AciListenerManager(aciList, configurationDN);
    processGlobalAcis(configuration);
    processConfigAcis();
    DirectoryServer.registerSupportedControl(OID_GET_EFFECTIVE_RIGHTS);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAllowed(DN entryDN, Operation op, Control control)
      throws DirectoryException
  {
    if (!skipAccessCheck(op))
    {
      Entry e = new Entry(entryDN, null, null, null);
      AciContainer container = new AciLDAPOperationContainer(op, e, control,
              ACI_READ | ACI_CONTROL);
      if (!accessAllowed(container))
      {
        return false;
      }
    }

    if (OID_PROXIED_AUTH_V2.equals(control.getOID())
        || OID_PROXIED_AUTH_V1.equals(control.getOID()))
    {
      op.setAttachment(ORIG_AUTH_ENTRY, op.getAuthorizationEntry());
    }
    else if (OID_GET_EFFECTIVE_RIGHTS.equals(control.getOID()))
    {
      GetEffectiveRightsRequestControl getEffectiveRightsControl;
      if (control instanceof LDAPControl)
      {
        getEffectiveRightsControl =
            GetEffectiveRightsRequestControl.DECODER.decode(control
                .isCritical(), ((LDAPControl) control).getValue());
      }
      else
      {
        getEffectiveRightsControl = (GetEffectiveRightsRequestControl) control;
      }
      op.setAttachment(OID_GET_EFFECTIVE_RIGHTS, getEffectiveRightsControl);
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAllowed(ExtendedOperation operation)
  {
    if (skipAccessCheck(operation))
    {
      return true;
    }

    Entry e = new Entry(operation.getAuthorizationDN(), null, null, null);
    final AciContainer container =
        new AciLDAPOperationContainer(operation, e, (ACI_READ | ACI_EXT_OP));
    return accessAllowed(container);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAllowed(LocalBackendAddOperation operation)
      throws DirectoryException
  {
    AciContainer container = new AciLDAPOperationContainer(operation, ACI_ADD);
    return isAllowed(container, operation)
        // LDAP add needs a verify ACI syntax step in case any
        // "aci" attribute types are being added.
        && verifySyntax(operation.getEntryToAdd(), operation, container.getClientDN());
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAllowed(BindOperation bindOperation)
  {
    // Not planned to be implemented.
    return true;
  }



  /**
   * Check access on compare operations. Note that the attribute type is
   * unavailable at this time, so this method partially parses the raw
   * attribute string to get the base attribute type. Options are
   * ignored.
   *
   * @param operation
   *          The compare operation to check access on.
   * @return True if access is allowed.
   */
  @Override
  public boolean isAllowed(LocalBackendCompareOperation operation)
  {
    AciContainer container =
        new AciLDAPOperationContainer(operation, ACI_COMPARE);

    String baseName;
    String rawAttributeType = operation.getRawAttributeType();
    int semicolonPosition = rawAttributeType.indexOf(';');
    if (semicolonPosition > 0)
    {
      baseName =
          toLowerCase(rawAttributeType.substring(0, semicolonPosition));
    }
    else
    {
      baseName = toLowerCase(rawAttributeType);
    }

    container.setCurrentAttributeType(getAttributeType(baseName));
    container.setCurrentAttributeValue(operation.getAssertionValue());
    return isAllowed(container, operation);
  }



  /**
   * Check access on delete operations.
   *
   * @param operation
   *          The delete operation to check access on.
   * @return True if access is allowed.
   */
  @Override
  public boolean isAllowed(LocalBackendDeleteOperation operation)
  {
    AciContainer container =
        new AciLDAPOperationContainer(operation, ACI_DELETE);
    return isAllowed(container, operation);
  }



  /**
   * Checks access on a modifyDN operation.
   *
   * @param operation
   *          The modifyDN operation to check access on.
   * @return True if access is allowed.
   */
  @Override
  public boolean isAllowed(ModifyDNOperation operation)
  {
    if (skipAccessCheck(operation))
    {
      return true;
    }

    final RDN oldRDN = operation.getOriginalEntry().getName().rdn();
    final RDN newRDN = operation.getNewRDN();
    final DN newSuperiorDN = operation.getNewSuperior();

    // If this is a modifyDN move to a new superior, then check if the
    // superior DN has import access.
    if (newSuperiorDN != null
        && !aciCheckSuperiorEntry(newSuperiorDN, operation))
    {
      return false;
    }

    // Perform the RDN access checks.
    boolean rdnChangesAllowed = aciCheckRDNs(operation, oldRDN, newRDN);

    // If this is a modifyDN move to a new superior, then check if the
    // original entry DN has export access.
    if (rdnChangesAllowed && newSuperiorDN != null)
    {
      AciContainer container = new AciLDAPOperationContainer(
          operation, ACI_EXPORT, operation.getOriginalEntry());
      if (!oldRDN.equals(newRDN))
      {
        // The RDNs are not equal, skip the proxy check since it was
        // already performed in the aciCheckRDNs call above.
        container.setSeenEntry(true);
      }
      return accessAllowed(container);
    }
    return rdnChangesAllowed;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAllowed(LocalBackendModifyOperation operation)
      throws DirectoryException
  {
    AciContainer container = new AciLDAPOperationContainer(operation, ACI_NULL);
    return aciCheckMods(container, operation, skipAccessCheck(operation));
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAllowed(SearchOperation searchOperation)
  {
    // Not planned to be implemented.
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAllowed(Operation operation, Entry entry,
      SearchFilter filter) throws DirectoryException
  {
    if (skipAccessCheck(operation))
    {
      return true;
    }

    AciContainer container =
        new AciLDAPOperationContainer(operation, ACI_READ, entry);
    return testFilter(container, filter);
  }

  /** {@inheritDoc} */
  @Override
  public boolean mayProxy(Entry proxyUser, Entry proxiedUser, Operation op)
  {
    if (skipAccessCheck(proxyUser))
    {
      return true;
    }

    final AuthenticationInfo authInfo =
        new AuthenticationInfo(proxyUser, DirectoryServer.isRootDN(proxyUser
            .getName()));
    final AciContainer container =
        new AciLDAPOperationContainer(op, proxiedUser, authInfo, ACI_PROXY);
    return accessAllowedEntry(container);
  }

  /** {@inheritDoc} */
  @Override
  public boolean maySend(DN dn, Operation operation, SearchResultReference reference)
  {
    if (skipAccessCheck(operation))
    {
      return true;
    }

    final AttributeBuilder builder =
        new AttributeBuilder(refAttrType, ATTR_REFERRAL_URL);

    // Load the values, a bind rule might want to evaluate them.
    final List<String> URLStrings = reference.getReferralURLs();
    for (String URLString : URLStrings)
    {
      builder.add(URLString);
    }

    final Entry e = new Entry(dn, null, null, null);
    e.addAttribute(builder.toAttribute(), null);
    final SearchResultEntry se = new SearchResultEntry(e);
    final AciContainer container =
        new AciLDAPOperationContainer(operation, ACI_READ, se);
    container.setCurrentAttributeType(refAttrType);
    return accessAllowed(container);
  }

  /** {@inheritDoc} */
  @Override
  public boolean maySend(Operation operation, SearchResultEntry entry)
  {
    if (skipAccessCheck(operation))
    {
      return true;
    }

    AciContainer container =
        new AciLDAPOperationContainer(operation, ACI_SEARCH, entry);

    // Pre/post read controls are associated with other types of operation.
    if (operation instanceof SearchOperation)
    {
      try
      {
        if (!testFilter(container, ((SearchOperation) operation).getFilter()))
        {
          return false;
        }
      }
      catch (DirectoryException ex)
      {
        return false;
      }
    }

    container.clearEvalAttributes(ACI_NULL);
    container.setRights(ACI_READ);

    if (!accessAllowedEntry(container))
    {
      return false;
    }

    if (!container.hasEvalUserAttributes())
    {
      operation.setAttachment(ALL_USER_ATTRS_MATCHED, ALL_USER_ATTRS_MATCHED);
    }
    if (!container.hasEvalOpAttributes())
    {
      operation.setAttachment(ALL_OP_ATTRS_MATCHED, ALL_OP_ATTRS_MATCHED);
    }

    return true;
  }



  /**
   * Check access using the specified container. This container will
   * have all of the information to gather applicable ACIs and perform
   * evaluation on them.
   *
   * @param container
   *          An ACI operation container which has all of the
   *          information needed to check access.
   * @return True if access is allowed.
   */
  boolean accessAllowed(AciContainer container)
  {
    DN dn = container.getResourceDN();
    // For ACI_WRITE_ADD and ACI_WRITE_DELETE set the ACI_WRITE
    // right.
    if (container.hasRights(ACI_WRITE_ADD)
        || container.hasRights(ACI_WRITE_DELETE))
    {
      container.setRights(container.getRights() | ACI_WRITE);
    }
    // Check if the ACI_SELF right needs to be set (selfwrite right).
    // Only done if the right is ACI_WRITE, an attribute value is set
    // and that attribute value is a DN.
    if (container.getCurrentAttributeValue() != null
        && container.hasRights(ACI_WRITE)
        && isAttributeDN(container.getCurrentAttributeType()))
    {
      String dnString = null;
      try
      {
        dnString = container.getCurrentAttributeValue().toString();
        DN tmpDN = DN.valueOf(dnString);
        // Have a valid DN, compare to clientDN to see if the ACI_SELF
        // right should be set.
        if (tmpDN.equals(container.getClientDN()))
        {
          container.setRights(container.getRights() | ACI_SELF);
        }
      }
      catch (DirectoryException ex)
      {
        // Log a message and keep going.
        logger.warn(WARN_ACI_NOT_VALID_DN, dnString);
      }
    }

    // First get all allowed candidate ACIs.
    List<Aci> candidates = aciList.getCandidateAcis(dn);
    /*
     * Create an applicable list of ACIs by target matching each
     * candidate ACI against the container's target match view.
     */
    createApplicableList(candidates, container);
    // Evaluate the applicable list.
    final boolean ret = testApplicableLists(container);
    // Build summary string if doing geteffectiverights eval.
    if (container.isGetEffectiveRightsEval())
    {
      container.setEvalSummary(
          AciEffectiveRights.createSummary(container, ret));
    }
    return ret;
  }



  /*
   * TODO Evaluate performance of this method. TODO Evaluate security
   * concerns of this method. Logic from this method taken almost
   * directly from DS6 implementation. I find the work done in the
   * accessAllowedEntry method, particularly with regard to the entry
   * test evaluation, to be very confusing and potentially pretty
   * inefficient. I'm also concerned that the "return "true" inside the
   * for loop could potentially allow access when it should be denied.
   */

  /**
   * Check if access is allowed on an entry. Access is checked by
   * iterating through each attribute of an entry, starting with the
   * "objectclass" attribute type. If access is allowed on the entry
   * based on one of it's attribute types, then a possible second access
   * check is performed. This second check is only performed if an entry
   * test ACI was found during the earlier successful access check. An
   * entry test ACI has no "targetattrs" keyword, so allowing access
   * based on an attribute type only would be incorrect.
   *
   * @param container
   *          ACI search container containing all of the information
   *          needed to check access.
   * @return True if access is allowed.
   */
  boolean accessAllowedEntry(AciContainer container)
  {
    // set flag that specifies this is the first attribute evaluated
    // in the entry
    container.setIsFirstAttribute(true);
    for (AttributeType attrType : getAllAttrs(container.getResourceEntry()))
    {
      /*
       * Check if access is allowed. If true, then check to see if an
       * entry test rule was found (no targetattrs) during target match
       * evaluation. If such a rule was found, set the current attribute
       * type to "null" and check access again so that rule is applied.
       */
      container.setCurrentAttributeType(attrType);
      if (accessAllowed(container))
      {
        if (container.hasEntryTestRule())
        {
          container.setCurrentAttributeType(null);
          if (!accessAllowed(container) && container.isDenyEval())
          {
            /*
             * If we failed because of a deny permission-bind rule, we need to
             * stop and return false.
             * If we failed because there was no explicit allow rule, then we
             * grant implicit access to the entry.
             */
            return false;
          }
        }
        return true;
      }
    }
    return false;
  }



  /**
   * Performs an access check against all of the attributes of an entry. The
   * attributes that fail access are removed from the entry. This method
   * performs the processing needed for the filterEntry method processing.
   *
   * @param container
   *          The search or compare container which has all of the information
   *          needed to filter the attributes for this entry.
   * @param filteredEntry
   *          The partially filtered search result entry being returned to the
   *          client.
   */
  private void filterEntry(AciContainer container, Entry filteredEntry)
  {
    for (AttributeType attrType : getAllAttrs(filteredEntry))
    {
      if (container.hasAllUserAttributes() && !attrType.isOperational())
      {
        continue;
      }
      if (container.hasAllOpAttributes() && attrType.isOperational())
      {
        continue;
      }
      container.setCurrentAttributeType(attrType);
      if (!accessAllowed(container))
      {
        filteredEntry.removeAttribute(attrType);
      }
    }
  }



  /**
   * Checks to see if a LDAP modification is allowed access.
   *
   * @param container
   *          The structure containing the LDAP modifications
   * @param operation
   *          The operation to check modify privileges on. operation to
   *          check and the evaluation context to apply the check
   *          against.
   * @param skipAccessCheck
   *          True if access checking should be skipped.
   * @return True if access is allowed.
   * @throws DirectoryException
   *           If a modified ACI could not be decoded.
   */
  private boolean aciCheckMods(AciContainer container,
      LocalBackendModifyOperation operation, boolean skipAccessCheck)
      throws DirectoryException
  {
    Entry resourceEntry = container.getResourceEntry();
    DN dn = resourceEntry.getName();
    List<Modification> modifications =  operation.getModifications();

    for (Modification m : modifications)
    {
      Attribute modAttr = m.getAttribute();
      AttributeType modAttrType = modAttr.getAttributeType();

      if (modAttrType.equals(aciType)
          /*
           * Check that the operation has modify privileges if it contains
           * an "aci" attribute type.
           */
          && !operation.getClientConnection().hasPrivilege(
              Privilege.MODIFY_ACL, operation))
      {
        logger.debug(INFO_ACI_MODIFY_FAILED_PRIVILEGE, container.getResourceDN(), container.getClientDN());
        return false;
      }
      // This access check handles the case where all attributes of this
      // type are being replaced or deleted. If only a subset is being
      // deleted than this access check is skipped.
      ModificationType modType = m.getModificationType();
      if (((modType == ModificationType.DELETE && modAttr.isEmpty())
              || modType == ModificationType.REPLACE
              || modType == ModificationType.INCREMENT)
          /*
           * Check if we have rights to delete all values of an attribute
           * type in the resource entry.
           */
          && resourceEntry.hasAttribute(modAttrType))
      {
        container.setCurrentAttributeType(modAttrType);
        List<Attribute> attrList =
            resourceEntry.getAttribute(modAttrType, modAttr.getOptions());
        if (attrList != null)
        {
          for (Attribute a : attrList)
          {
            for (ByteString v : a)
            {
              container.setCurrentAttributeValue(v);
              container.setRights(ACI_WRITE_DELETE);
              if (!skipAccessCheck && !accessAllowed(container))
              {
                return false;
              }
            }
          }
        }
      }

      if (!modAttr.isEmpty())
      {
        for (ByteString v : modAttr)
        {
          container.setCurrentAttributeType(modAttrType);
          switch (m.getModificationType().asEnum())
          {
          case ADD:
          case REPLACE:
            container.setCurrentAttributeValue(v);
            container.setRights(ACI_WRITE_ADD);
            if (!skipAccessCheck && !accessAllowed(container))
            {
              return false;
            }
            break;
          case DELETE:
            container.setCurrentAttributeValue(v);
            container.setRights(ACI_WRITE_DELETE);
            if (!skipAccessCheck && !accessAllowed(container))
            {
              return false;
            }
            break;
          case INCREMENT:
            Entry modifiedEntry = operation.getModifiedEntry();
            List<Attribute> modifiedAttrs =
                modifiedEntry.getAttribute(modAttrType, modAttr.getOptions());
            if (modifiedAttrs != null)
            {
              for (Attribute attr : modifiedAttrs)
              {
                for (ByteString val : attr)
                {
                  container.setCurrentAttributeValue(val);
                  container.setRights(ACI_WRITE_ADD);
                  if (!skipAccessCheck && !accessAllowed(container))
                  {
                    return false;
                  }
                }
              }
            }
            break;
          }
          /*
           * Check if the modification type has an "aci" attribute type.
           * If so, check the syntax of that attribute value. Fail the
           * the operation if the syntax check fails.
           */
          if (modAttrType.equals(aciType)
              || modAttrType.equals(globalAciType))
          {
            try
            {
              // A global ACI needs a NULL DN, not the DN of the
              // modification.
              if (modAttrType.equals(globalAciType))
              {
                dn = DN.rootDN();
              }
              // validate ACI syntax
              Aci.decode(v, dn);
            }
            catch (AciException ex)
            {
              throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                  WARN_ACI_MODIFY_FAILED_DECODE.get(dn, ex.getMessage()));
            }
          }
        }
      }
    }
    return true;
  }



  /**
   * Perform all needed RDN checks for the modifyDN operation. The old RDN is
   * not equal to the new RDN. The access checks are:
   * <ul>
   * <li>Verify WRITE access to the original entry.</li>
   * <li>Verify WRITE_ADD access on each RDN component of the new RDN. The
   * WRITE_ADD access is used because this access could be restricted by the
   * targattrfilters keyword.</li>
   * <li>If the deleteOLDRDN flag is set, verify WRITE_DELETE access on the old
   * RDN. The WRITE_DELETE access is used because this access could be
   * restricted by the targattrfilters keyword.
   * <li>
   * </ul>
   *
   * @param operation
   *          The ModifyDN operation class containing information to check
   *          access on.
   * @param oldRDN
   *          The old RDN component.
   * @param newRDN
   *          The new RDN component.
   * @return True if access is allowed.
   */
  private boolean aciCheckRDNs(ModifyDNOperation operation,
      RDN oldRDN, RDN newRDN)
  {
    AciContainer container =
        new AciLDAPOperationContainer(operation, ACI_WRITE, operation
            .getOriginalEntry());
    if (!accessAllowed(container))
    {
      return false;
    }

    boolean ret = checkRDN(ACI_WRITE_ADD, newRDN, container);
    if (ret && operation.deleteOldRDN())
    {
      ret = checkRDN(ACI_WRITE_DELETE, oldRDN, container);
    }
    return ret;
  }



  /**
   * Check access on the new superior entry if it exists. If superiordn is null,
   * the entry does not exist or the DN cannot be locked then false is returned.
   *
   * @param superiorDN
   *          The DN of the new superior entry.
   * @param op
   *          The modifyDN operation to check access on.
   * @return True if access is granted to the new superior entry.
   */
  private boolean aciCheckSuperiorEntry(DN superiorDN, ModifyDNOperation op)
  {
    try
    {
      Entry superiorEntry = DirectoryServer.getEntry(superiorDN);
      if (superiorEntry != null)
      {
        AciContainer container =
            new AciLDAPOperationContainer(op, ACI_IMPORT, superiorEntry);
        return accessAllowed(container);
      }
      return false;
    }
    catch (DirectoryException ex)
    {
      return false;
    }
  }



  /**
   * Check access on each attribute-value pair component of the
   * specified RDN. There may be more than one attribute-value pair if
   * the RDN is multi-valued.
   *
   * @param right
   *          The access right to check for.
   * @param rdn
   *          The RDN to examine the attribute-value pairs of.
   * @param container
   *          The container containing the information needed to
   *          evaluate the specified RDN.
   * @return True if access is allowed for all attribute-value pairs.
   */
  private boolean checkRDN(int right, RDN rdn, AciContainer container)
  {
    container.setRights(right);
    final int numAVAs = rdn.getNumValues();
    for (int i = 0; i < numAVAs; i++)
    {
      container.setCurrentAttributeType(rdn.getAttributeType(i));
      container.setCurrentAttributeValue(rdn.getAttributeValue(i));
      if (!accessAllowed(container))
      {
        return false;
      }
    }
    return true;
  }



  /**
   * Creates the allow and deny ACI lists based on the provided target
   * match context. These lists are stored in the evaluation context.
   *
   * @param candidates
   *          List of all possible ACI candidates.
   * @param targetMatchCtx
   *          Target matching context to use for testing each ACI.
   */
  private void createApplicableList(List<Aci> candidates,
      AciTargetMatchContext targetMatchCtx)
  {
    List<Aci> denys = new LinkedList<>();
    List<Aci> allows = new LinkedList<>();
    for (Aci aci : candidates)
    {
      if (Aci.isApplicable(aci, targetMatchCtx))
      {
        if (aci.hasAccessType(EnumAccessType.DENY))
        {
          denys.add(aci);
        }
        if (aci.hasAccessType(EnumAccessType.ALLOW))
        {
          allows.add(aci);
        }
      }
      if (targetMatchCtx.getTargAttrFiltersMatch())
      {
        targetMatchCtx.setTargAttrFiltersMatch(false);
      }
    }
    targetMatchCtx.setAllowList(allows);
    targetMatchCtx.setDenyList(denys);
  }



  /**
   * Gathers all of the attribute types in an entry along with the
   * "objectclass" attribute type in a List. The "objectclass" attribute
   * is added to the list first so it is evaluated first.
   *
   * @param e
   *          Entry to gather the attributes for.
   * @return List containing the attribute types.
   */
  private List<AttributeType> getAllAttrs(Entry e)
  {
    List<AttributeType> typeList = new LinkedList<>();
    /*
     * When a search is not all attributes returned, the "objectclass"
     * attribute type is missing from the entry.
     */
    final Attribute attr = e.getObjectClassAttribute();
    if (attr != null)
    {
      AttributeType ocType = attr.getAttributeType();
      typeList.add(ocType);
    }
    typeList.addAll(e.getUserAttributes().keySet());
    typeList.addAll(e.getOperationalAttributes().keySet());
    return typeList;
  }



  /**
   * Check access using the accessAllowed method. The LDAP add, compare,
   * modify and delete operations use this function. The other supported
   * LDAP operations have more specialized checks.
   *
   * @param container
   *          The container containing the information needed to
   *          evaluate this operation.
   * @param operation
   *          The operation being evaluated.
   * @return True if this operation is allowed access.
   */
  private boolean isAllowed(AciContainer container, Operation operation)
  {
    return skipAccessCheck(operation) || accessAllowed(container);
  }

  /**
   * Check if the specified attribute type is a DN by checking if its
   * syntax OID is equal to the DN syntax OID.
   *
   * @param attribute
   *          The attribute type to check.
   * @return True if the attribute type syntax OID is equal to a DN
   *         syntax OID.
   */
  private boolean isAttributeDN(AttributeType attribute)
  {
    return SYNTAX_DN_OID.equals(attribute.getSyntax().getOID());
  }



  /**
   * Process all ACIs under the "cn=config" naming context and adds them
   * to the ACI list cache. It also logs messages about the number of
   * ACIs added to the cache. This method is called once at startup. It
   * will put the server in lockdown mode if needed.
   *
   * @throws InitializationException
   *           If there is an error searching for the ACIs in the naming
   *           context.
   */
  private void processConfigAcis() throws InitializationException
  {
    LinkedList<LocalizableMessage> failedACIMsgs = new LinkedList<>();
    InternalClientConnection conn = getRootConnection();

    ConfigHandler<?> configBackend = DirectoryServer.getConfigHandler();
    for (DN baseDN : configBackend.getBaseDNs())
    {
      try
      {
        if (! configBackend.entryExists(baseDN))
        {
          continue;
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);

        // FIXME -- Is there anything that we need to do here?
        continue;
      }

      try {
        SearchRequest request = newSearchRequest(baseDN, SearchScope.WHOLE_SUBTREE, "aci=*").addAttribute("aci");
        InternalSearchOperation internalSearch =
            new InternalSearchOperation(conn, nextOperationID(), nextMessageID(), request);
        LocalBackendSearchOperation localSearch = new LocalBackendSearchOperation(internalSearch);

        configBackend.search(localSearch);

        if (!internalSearch.getSearchEntries().isEmpty())
        {
          int validAcis =
              aciList.addAci(internalSearch.getSearchEntries(), failedACIMsgs);
          if (!failedACIMsgs.isEmpty())
          {
            aciListenerMgr.logMsgsSetLockDownMode(failedACIMsgs);
          }
          logger.debug(INFO_ACI_ADD_LIST_ACIS, validAcis, baseDN);
        }
      }
      catch (Exception e)
      {
        LocalizableMessage message = INFO_ACI_HANDLER_FAIL_PROCESS_ACI.get();
        throw new InitializationException(message, e);
      }
    }
  }



  /**
   * Process all global ACI attribute types found in the configuration
   * entry and adds them to that ACI list cache. It also logs messages
   * about the number of ACI attribute types added to the cache. This
   * method is called once at startup. It also will put the server into
   * lockdown mode if needed.
   *
   * @param configuration
   *          The config handler containing the ACI configuration
   *          information.
   * @throws InitializationException
   *           If there is an error reading the global ACIs from the
   *           configuration entry.
   */
  private void processGlobalAcis(
      DseeCompatAccessControlHandlerCfg configuration)
      throws InitializationException
  {
    try
    {
      final SortedSet<Aci> globalAcis = configuration.getGlobalACI();
      if (globalAcis != null)
      {
        aciList.addAci(DN.rootDN(), globalAcis);
        logger.debug(INFO_ACI_ADD_LIST_GLOBAL_ACIS, globalAcis.size());
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);
      throw new InitializationException(
          INFO_ACI_HANDLER_FAIL_PROCESS_GLOBAL_ACI.get(configuration.dn()), e);
    }
  }



  /**
   * Check to see if the specified entry has the specified privilege.
   *
   * @param e
   *          The entry to check privileges on.
   * @return {@code true} if the entry has the specified privilege, or
   *         {@code false} if not.
   */
  private boolean skipAccessCheck(Entry e)
  {
    return ClientConnection.hasPrivilege(e, Privilege.BYPASS_ACL);
  }



  /**
   * Check to see if the client entry has BYPASS_ACL privileges for this
   * operation.
   *
   * @param operation
   *          The operation to check privileges on.
   * @return True if access checking can be skipped because the
   *         operation client connection has BYPASS_ACL privileges.
   */
  private boolean skipAccessCheck(Operation operation)
  {
    return operation.getClientConnection().hasPrivilege(
        Privilege.BYPASS_ACL, operation);
  }



  /**
   * Performs the test of the deny and allow access lists using the
   * provided evaluation context. The deny list is checked first.
   *
   * @param evalCtx
   *          The evaluation context to use.
   * @return True if access is allowed.
   */
  private boolean testApplicableLists(AciEvalContext evalCtx)
  {
    evalCtx.setEvaluationResult(NO_REASON, null);

    if (evalCtx.getAllowList().isEmpty()
        && (!evalCtx.isGetEffectiveRightsEval()
            || evalCtx.hasRights(ACI_SELF)
            || !evalCtx.isTargAttrFilterMatchAciEmpty()))
    {
      // If allows list is empty and not doing geteffectiverights return false.
      evalCtx.setEvaluationResult(NO_ALLOW_ACIS, null);
      return false;
    }

    for (Aci denyAci : evalCtx.getDenyList())
    {
      final EnumEvalResult res = Aci.evaluate(evalCtx, denyAci);
      // Failure could be returned if a system limit is hit or
      // search fails
      if (res.equals(EnumEvalResult.FAIL))
      {
        evalCtx.setEvaluationResult(EVALUATED_DENY_ACI, denyAci);
        return false;
      }
      else if (res.equals(EnumEvalResult.TRUE))
      {
        if (testAndSetTargAttrOperationMatches(evalCtx, denyAci, true))
        {
          continue;
        }
        evalCtx.setEvaluationResult(EVALUATED_DENY_ACI, denyAci);
        return false;
      }
    }

    for (Aci allowAci : evalCtx.getAllowList())
    {
      final EnumEvalResult res = Aci.evaluate(evalCtx, allowAci);
      if (res.equals(EnumEvalResult.TRUE))
      {
        if (testAndSetTargAttrOperationMatches(evalCtx, allowAci, false))
        {
          continue;
        }
        evalCtx.setEvaluationResult(EVALUATED_ALLOW_ACI, allowAci);
        return true;
      }
    }
    // Nothing matched fall through.
    evalCtx.setEvaluationResult(NO_MATCHED_ALLOWS_ACIS, null);
    return false;
  }

  private boolean testAndSetTargAttrOperationMatches(AciEvalContext evalCtx,
      Aci aci, boolean isDenyAci)
  {
    return evalCtx.isGetEffectiveRightsEval()
        && !evalCtx.hasRights(ACI_SELF)
        && !evalCtx.isTargAttrFilterMatchAciEmpty()
        // Iterate to next only if ACI contains a targattrfilters keyword.
        && AciEffectiveRights.setTargAttrAci(evalCtx, aci, isDenyAci);
  }

  /**
   * Test the attribute types of the search filter for access. This
   * method supports the search right.
   *
   * @param container
   *          The container used in the access evaluation.
   * @param filter
   *          The filter to check access on.
   * @return True if all attribute types in the filter have access.
   * @throws DirectoryException
   *           If there is a problem matching the entry using the
   *           provided filter.
   */
  private boolean testFilter(AciContainer container, SearchFilter filter)
      throws DirectoryException
  {
    // If the resource entry has a dn equal to "cn=debugsearch" and it
    // contains the special attribute type "debugsearchindex", then the
    // resource entry is a pseudo entry created for debug purposes.
    // Return true if that is the case.
    if (debugSearchIndexDN.equals(container.getResourceDN())
        && container.getResourceEntry().hasAttribute(debugSearchIndex))
    {
      return true;
    }
    switch (filter.getFilterType())
    {
    case AND:
    case OR:
    {
      for (SearchFilter f : filter.getFilterComponents())
      {
        if (!testFilter(container, f))
        {
          return false;
        }
      }
      break;
    }
    case NOT:
    {
      return testFilter(container, filter.getNotComponent());
    }
    default:
    {
      container.setCurrentAttributeType(filter.getAttributeType());
      return accessAllowed(container);
    }
    }
    return true;
  }



  /**
   * Evaluate an entry to be added to see if it has any "aci" attribute
   * type. If it does, examines each "aci" attribute type value for
   * syntax errors. All of the "aci" attribute type values must pass
   * syntax check for the add operation to proceed. Any entry with an
   * "aci" attribute type must have "modify-acl" privileges.
   *
   * @param entry
   *          The entry to be examined.
   * @param operation
   *          The operation to to check privileges on.
   * @param clientDN
   *          The authorization DN.
   * @return True if the entry has no ACI attributes or if all of the
   *         "aci" attributes values pass ACI syntax checking.
   * @throws DirectoryException
   *           If a modified ACI could not be decoded.
   */
  private boolean verifySyntax(Entry entry, Operation operation,
      DN clientDN) throws DirectoryException
  {
    if (entry.hasOperationalAttribute(aciType))
    {
      /*
       * Check that the operation has "modify-acl" privileges since the
       * entry to be added has an "aci" attribute type.
       */
      if (!operation.getClientConnection().hasPrivilege(
          Privilege.MODIFY_ACL, operation))
      {
        logger.debug(INFO_ACI_ADD_FAILED_PRIVILEGE, entry.getName(), clientDN);
        return false;
      }
      List<Attribute> attributeList =
          entry.getOperationalAttribute(aciType, null);
      for (Attribute attribute : attributeList)
      {
        for (ByteString value : attribute)
        {
          try
          {
            // validate ACI syntax
            Aci.decode(value, entry.getName());
          }
          catch (AciException ex)
          {
            throw new DirectoryException(
                ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                WARN_ACI_ADD_FAILED_DECODE.get(entry.getName(), ex.getMessage()));
          }
        }
      }
    }
    return true;
  }
}
