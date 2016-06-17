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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.server.config.server.VirtualStaticGroupImplementationCfg;
import org.opends.server.api.Group;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.types.Attribute;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.MemberList;
import org.opends.server.types.Modification;
import org.opends.server.types.SearchFilter;

import static org.forgerock.util.Reject.*;
import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.ServerConstants.*;

/**
 * This class provides a virtual static group implementation, in which
 * membership is based on membership of another group.
 */
public class VirtualStaticGroup
       extends Group<VirtualStaticGroupImplementationCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The DN of the entry that holds the definition for this group. */
  private DN groupEntryDN;

  /** The DN of the target group that will provide membership information. */
  private DN targetGroupDN;

  /**
   * Creates a new, uninitialized virtual static group instance.  This is
   * intended for internal use only.
   */
  public VirtualStaticGroup()
  {
    super();

    // No initialization is required here.
  }

  /**
   * Creates a new virtual static group instance with the provided information.
   *
   * @param  groupEntryDN   The DN of the entry that holds the definition for
   *                        this group.  It must not be {@code null}.
   * @param  targetGroupDN  The DN of the target group that will provide
   *                        membership information.  It must not be
   *                        {@code null}.
   */
  public VirtualStaticGroup(DN groupEntryDN, DN targetGroupDN)
  {
    super();

    ifNull(groupEntryDN, targetGroupDN);

    this.groupEntryDN  = groupEntryDN;
    this.targetGroupDN = targetGroupDN;
  }

  @Override
  public void initializeGroupImplementation(
                   VirtualStaticGroupImplementationCfg configuration)
         throws ConfigException, InitializationException
  {
    // No additional initialization is required.
  }

  @Override
  public VirtualStaticGroup newInstance(ServerContext serverContext, Entry groupEntry)
         throws DirectoryException
  {
    ifNull(groupEntry);

    // Get the target group DN attribute from the entry, if there is one.
    DN targetDN = null;
    AttributeType targetType = DirectoryServer.getSchema().getAttributeType(ATTR_TARGET_GROUP_DN);
    for (Attribute a : groupEntry.getAttribute(targetType))
    {
      for (ByteString v : a)
      {
        if (targetDN != null)
        {
          LocalizableMessage message = ERR_VIRTUAL_STATIC_GROUP_MULTIPLE_TARGETS.get(groupEntry.getName());
          throw new DirectoryException(ResultCode.OBJECTCLASS_VIOLATION, message);
        }

        try
        {
          targetDN = DN.valueOf(v);
        }
        catch (LocalizedIllegalArgumentException e)
        {
          logger.traceException(e);

          LocalizableMessage message = ERR_VIRTUAL_STATIC_GROUP_CANNOT_DECODE_TARGET.
              get(v, groupEntry.getName(), e.getMessageObject());
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message, e);
        }
      }
    }

    if (targetDN == null)
    {
      LocalizableMessage message = ERR_VIRTUAL_STATIC_GROUP_NO_TARGET.get(groupEntry.getName());
      throw new DirectoryException(ResultCode.OBJECTCLASS_VIOLATION, message);
    }

    return new VirtualStaticGroup(groupEntry.getName(), targetDN);
  }

  @Override
  public SearchFilter getGroupDefinitionFilter()
         throws DirectoryException
  {
    // FIXME -- This needs to exclude enhanced groups once we have support for
    // them.
    return SearchFilter.createFilterFromString("(" + ATTR_OBJECTCLASS + "=" +
                                               OC_VIRTUAL_STATIC_GROUP + ")");
  }

  @Override
  public boolean isGroupDefinition(Entry entry)
  {
    ifNull(entry);

    // FIXME -- This needs to exclude enhanced groups once we have support for them.
    return entry.hasObjectClass(DirectoryServer.getSchema().getObjectClass(OC_VIRTUAL_STATIC_GROUP));
  }

  @Override
  public DN getGroupDN()
  {
    return groupEntryDN;
  }

  @Override
  public void setGroupDN(DN groupDN)
  {
    groupEntryDN = groupDN;
  }

  /**
   * Retrieves the DN of the target group for this virtual static group.
   *
   * @return  The DN of the target group for this virtual static group.
   */
  public DN getTargetGroupDN()
  {
    return targetGroupDN;
  }

  @Override
  public boolean supportsNestedGroups()
  {
    // Virtual static groups don't support nesting.
    return false;
  }

  @Override
  public List<DN> getNestedGroupDNs()
  {
    // Virtual static groups don't support nesting.
    return Collections.<DN>emptyList();
  }

  @Override
  public void addNestedGroup(DN nestedGroupDN)
         throws UnsupportedOperationException, DirectoryException
  {
    // Virtual static groups don't support nesting.
    LocalizableMessage message = ERR_VIRTUAL_STATIC_GROUP_NESTING_NOT_SUPPORTED.get();
    throw new UnsupportedOperationException(message.toString());
  }

  @Override
  public void removeNestedGroup(DN nestedGroupDN)
         throws UnsupportedOperationException, DirectoryException
  {
    // Virtual static groups don't support nesting.
    LocalizableMessage message = ERR_VIRTUAL_STATIC_GROUP_NESTING_NOT_SUPPORTED.get();
    throw new UnsupportedOperationException(message.toString());
  }

  @Override
  public boolean isMember(DN userDN, AtomicReference<Set<DN>> examinedGroups)
         throws DirectoryException
  {
    Set<DN> groups = getExaminedGroups(examinedGroups);
    if (! groups.add(getGroupDN()))
    {
      return false;
    }

    Group targetGroup =
         DirectoryServer.getGroupManager().getGroupInstance(targetGroupDN);
    if (targetGroup == null)
    {
      LocalizableMessage message = ERR_VIRTUAL_STATIC_GROUP_NO_TARGET_GROUP.get(targetGroupDN, groupEntryDN);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message);
    }
    else if (targetGroup instanceof VirtualStaticGroup)
    {
      LocalizableMessage message = ERR_VIRTUAL_STATIC_GROUP_TARGET_CANNOT_BE_VIRTUAL.get(groupEntryDN, targetGroupDN);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }
    else
    {
      return targetGroup.isMember(userDN);
    }
  }

  @Override
  public boolean isMember(Entry userEntry, AtomicReference<Set<DN>> examinedGroups)
         throws DirectoryException
  {
    Set<DN> groups = getExaminedGroups(examinedGroups);
    if (! groups.add(getGroupDN()))
    {
      return false;
    }

    Group targetGroup =
         DirectoryServer.getGroupManager().getGroupInstance(targetGroupDN);
    if (targetGroup == null)
    {
      LocalizableMessage message = ERR_VIRTUAL_STATIC_GROUP_NO_TARGET_GROUP.get(targetGroupDN, groupEntryDN);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message);
    }
    else if (targetGroup instanceof VirtualStaticGroup)
    {
      LocalizableMessage message = ERR_VIRTUAL_STATIC_GROUP_TARGET_CANNOT_BE_VIRTUAL.get(groupEntryDN, targetGroupDN);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }
    else
    {
      return targetGroup.isMember(userEntry);
    }
  }

  private Set<DN> getExaminedGroups(AtomicReference<Set<DN>> examinedGroups)
  {
    Set<DN> groups = examinedGroups.get();
    if (groups == null)
    {
      groups = new HashSet<DN>();
      examinedGroups.set(groups);
    }
    return groups;
  }

  @Override
  public MemberList getMembers()
         throws DirectoryException
  {
    Group targetGroup =
         DirectoryServer.getGroupManager().getGroupInstance(targetGroupDN);
    if (targetGroup == null)
    {
      LocalizableMessage message = ERR_VIRTUAL_STATIC_GROUP_NO_TARGET_GROUP.get(targetGroupDN, groupEntryDN);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message);
    }
    else if (targetGroup instanceof VirtualStaticGroup)
    {
      LocalizableMessage message = ERR_VIRTUAL_STATIC_GROUP_TARGET_CANNOT_BE_VIRTUAL.get(groupEntryDN, targetGroupDN);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }
    else
    {
      return targetGroup.getMembers();
    }
  }

  @Override
  public MemberList getMembers(DN baseDN, SearchScope scope,
                               SearchFilter filter)
         throws DirectoryException
  {
    Group targetGroup =
         DirectoryServer.getGroupManager().getGroupInstance(targetGroupDN);
    if (targetGroup == null)
    {
      LocalizableMessage message = ERR_VIRTUAL_STATIC_GROUP_NO_TARGET_GROUP.get(targetGroupDN, groupEntryDN);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message);
    }
    else if (targetGroup instanceof VirtualStaticGroup)
    {
      LocalizableMessage message = ERR_VIRTUAL_STATIC_GROUP_TARGET_CANNOT_BE_VIRTUAL.get(groupEntryDN, targetGroupDN);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }
    else
    {
      return targetGroup.getMembers(baseDN, scope, filter);
    }
  }

  @Override
  public boolean mayAlterMemberList()
  {
    return false;
  }

  @Override
  public void updateMembers(List<Modification> modifications)
         throws UnsupportedOperationException, DirectoryException
  {
    // Virtual static groups don't support altering the member list.
    LocalizableMessage message = ERR_VIRTUAL_STATIC_GROUP_ALTERING_MEMBERS_NOT_SUPPORTED.get(groupEntryDN);
    throw new UnsupportedOperationException(message.toString());
  }

  @Override
  public void addMember(Entry userEntry)
         throws UnsupportedOperationException, DirectoryException
  {
    // Virtual static groups don't support altering the member list.
    LocalizableMessage message = ERR_VIRTUAL_STATIC_GROUP_ALTERING_MEMBERS_NOT_SUPPORTED.get(groupEntryDN);
    throw new UnsupportedOperationException(message.toString());
  }

  @Override
  public void removeMember(DN userDN)
         throws UnsupportedOperationException, DirectoryException
  {
    // Virtual static groups don't support altering the member list.
    LocalizableMessage message = ERR_VIRTUAL_STATIC_GROUP_ALTERING_MEMBERS_NOT_SUPPORTED.get(groupEntryDN);
    throw new UnsupportedOperationException(message.toString());
  }

  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("VirtualStaticGroup(dn=");
    buffer.append(groupEntryDN);
    buffer.append(",targetGroupDN=");
    buffer.append(targetGroupDN);
    buffer.append(")");
  }
}
