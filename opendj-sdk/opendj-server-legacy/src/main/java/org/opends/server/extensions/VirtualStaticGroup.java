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
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.extensions;



import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.admin.std.server.VirtualStaticGroupImplementationCfg;
import org.opends.server.api.Group;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.MemberList;
import org.opends.server.types.ObjectClass;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.types.SearchFilter;
import org.forgerock.opendj.ldap.SearchScope;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.forgerock.util.Reject.*;



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



  /** {@inheritDoc} */
  @Override
  public void initializeGroupImplementation(
                   VirtualStaticGroupImplementationCfg configuration)
         throws ConfigException, InitializationException
  {
    // No additional initialization is required.
  }




  /** {@inheritDoc} */
  @Override
  public VirtualStaticGroup newInstance(ServerContext serverContext, Entry groupEntry)
         throws DirectoryException
  {
    ifNull(groupEntry);


    // Get the target group DN attribute from the entry, if there is one.
    DN targetDN = null;
    AttributeType targetType = DirectoryServer.getAttributeTypeOrDefault(ATTR_TARGET_GROUP_DN);
    List<Attribute> attrList = groupEntry.getAttribute(targetType);
    if (attrList != null)
    {
      for (Attribute a : attrList)
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
            targetDN = DN.decode(v);
          }
          catch (DirectoryException de)
          {
            logger.traceException(de);

            LocalizableMessage message = ERR_VIRTUAL_STATIC_GROUP_CANNOT_DECODE_TARGET.
                get(v, groupEntry.getName(), de.getMessageObject());
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message, de);
          }
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



  /** {@inheritDoc} */
  @Override
  public SearchFilter getGroupDefinitionFilter()
         throws DirectoryException
  {
    // FIXME -- This needs to exclude enhanced groups once we have support for
    // them.
    return SearchFilter.createFilterFromString("(" + ATTR_OBJECTCLASS + "=" +
                                               OC_VIRTUAL_STATIC_GROUP + ")");
  }



  /** {@inheritDoc} */
  @Override
  public boolean isGroupDefinition(Entry entry)
  {
    ifNull(entry);

    // FIXME -- This needs to exclude enhanced groups once we have support for
    //them.
    ObjectClass virtualStaticGroupClass =
         DirectoryServer.getObjectClass(OC_VIRTUAL_STATIC_GROUP, true);
    return entry.hasObjectClass(virtualStaticGroupClass);
  }



  /** {@inheritDoc} */
  @Override
  public DN getGroupDN()
  {
    return groupEntryDN;
  }



  /** {@inheritDoc} */
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



  /** {@inheritDoc} */
  @Override
  public boolean supportsNestedGroups()
  {
    // Virtual static groups don't support nesting.
    return false;
  }



  /** {@inheritDoc} */
  @Override
  public List<DN> getNestedGroupDNs()
  {
    // Virtual static groups don't support nesting.
    return Collections.<DN>emptyList();
  }



  /** {@inheritDoc} */
  @Override
  public void addNestedGroup(DN nestedGroupDN)
         throws UnsupportedOperationException, DirectoryException
  {
    // Virtual static groups don't support nesting.
    LocalizableMessage message = ERR_VIRTUAL_STATIC_GROUP_NESTING_NOT_SUPPORTED.get();
    throw new UnsupportedOperationException(message.toString());
  }



  /** {@inheritDoc} */
  @Override
  public void removeNestedGroup(DN nestedGroupDN)
         throws UnsupportedOperationException, DirectoryException
  {
    // Virtual static groups don't support nesting.
    LocalizableMessage message = ERR_VIRTUAL_STATIC_GROUP_NESTING_NOT_SUPPORTED.get();
    throw new UnsupportedOperationException(message.toString());
  }



  /** {@inheritDoc} */
  @Override
  public boolean isMember(DN userDN, Set<DN> examinedGroups)
         throws DirectoryException
  {
    if (! examinedGroups.add(getGroupDN()))
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



  /** {@inheritDoc} */
  @Override
  public boolean isMember(Entry userEntry, Set<DN> examinedGroups)
         throws DirectoryException
  {
    if (! examinedGroups.add(getGroupDN()))
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



  /** {@inheritDoc} */
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



  /** {@inheritDoc} */
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



  /** {@inheritDoc} */
  @Override
  public boolean mayAlterMemberList()
  {
    return false;
  }



  /** {@inheritDoc} */
  @Override
  public void addMember(Entry userEntry)
         throws UnsupportedOperationException, DirectoryException
  {
    // Virtual static groups don't support altering the member list.
    LocalizableMessage message = ERR_VIRTUAL_STATIC_GROUP_ALTERING_MEMBERS_NOT_SUPPORTED.get(groupEntryDN);
    throw new UnsupportedOperationException(message.toString());
  }



  /** {@inheritDoc} */
  @Override
  public void removeMember(DN userDN)
         throws UnsupportedOperationException, DirectoryException
  {
    // Virtual static groups don't support altering the member list.
    LocalizableMessage message = ERR_VIRTUAL_STATIC_GROUP_ALTERING_MEMBERS_NOT_SUPPORTED.get(groupEntryDN);
    throw new UnsupportedOperationException(message.toString());
  }



  /** {@inheritDoc} */
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

