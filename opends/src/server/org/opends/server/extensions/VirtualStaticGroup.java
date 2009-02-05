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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.opends.messages.Message;
import org.opends.server.admin.std.server.VirtualStaticGroupImplementationCfg;
import org.opends.server.api.Group;
import org.opends.server.core.DirectoryServer;
import org.opends.server.config.ConfigException;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.MemberList;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.Validator.*;



/**
 * This class provides a virtual static group implementation, in which
 * membership is based on membership of another group.
 */
public class VirtualStaticGroup
       extends Group<VirtualStaticGroupImplementationCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The DN of the entry that holds the definition for this group.
  private DN groupEntryDN;

  // The DN of the target group that will provide membership information.
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

    ensureNotNull(groupEntryDN, targetGroupDN);

    this.groupEntryDN  = groupEntryDN;
    this.targetGroupDN = targetGroupDN;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializeGroupImplementation(
                   VirtualStaticGroupImplementationCfg configuration)
         throws ConfigException, InitializationException
  {
    // No additional initialization is required.
  }




  /**
   * {@inheritDoc}
   */
  @Override()
  public VirtualStaticGroup newInstance(Entry groupEntry)
         throws DirectoryException
  {
    ensureNotNull(groupEntry);


    // Get the target group DN attribute from the entry, if there is one.
    DN targetDN = null;
    AttributeType targetType =
         DirectoryServer.getAttributeType(ATTR_TARGET_GROUP_DN, true);
    List<Attribute> attrList = groupEntry.getAttribute(targetType);
    if (attrList != null)
    {
      for (Attribute a : attrList)
      {
        for (AttributeValue v : a)
        {
          if (targetDN != null)
          {
            Message message = ERR_VIRTUAL_STATIC_GROUP_MULTIPLE_TARGETS.get(
                String.valueOf(groupEntry.getDN()));
            throw new DirectoryException(ResultCode.OBJECTCLASS_VIOLATION,
                                         message);
          }

          try
          {
            targetDN = DN.decode(v.getValue());
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            Message message = ERR_VIRTUAL_STATIC_GROUP_CANNOT_DECODE_TARGET.
                get(v.getValue().toString(), String.valueOf(groupEntry.getDN()),
                    de.getMessageObject());
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message, de);
          }
        }
      }
    }

    if (targetDN == null)
    {
      Message message = ERR_VIRTUAL_STATIC_GROUP_NO_TARGET.get(
          String.valueOf(groupEntry.getDN()));
      throw new DirectoryException(ResultCode.OBJECTCLASS_VIOLATION, message);
    }

    return new VirtualStaticGroup(groupEntry.getDN(), targetDN);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public SearchFilter getGroupDefinitionFilter()
         throws DirectoryException
  {
    // FIXME -- This needs to exclude enhanced groups once we have support for
    // them.
    return SearchFilter.createFilterFromString("(" + ATTR_OBJECTCLASS + "=" +
                                               OC_VIRTUAL_STATIC_GROUP + ")");
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isGroupDefinition(Entry entry)
  {
    ensureNotNull(entry);

    // FIXME -- This needs to exclude enhanced groups once we have support for
    //them.
    ObjectClass virtualStaticGroupClass =
         DirectoryServer.getObjectClass(OC_VIRTUAL_STATIC_GROUP, true);
    return entry.hasObjectClass(virtualStaticGroupClass);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public DN getGroupDN()
  {
    return groupEntryDN;
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



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsNestedGroups()
  {
    // Virtual static groups don't support nesting.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public List<DN> getNestedGroupDNs()
  {
    // Virtual static groups don't support nesting.
    return Collections.<DN>emptyList();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void addNestedGroup(DN nestedGroupDN)
         throws UnsupportedOperationException, DirectoryException
  {
    // Virtual static groups don't support nesting.
    Message message = ERR_VIRTUAL_STATIC_GROUP_NESTING_NOT_SUPPORTED.get();
    throw new UnsupportedOperationException(message.toString());
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void removeNestedGroup(DN nestedGroupDN)
         throws UnsupportedOperationException, DirectoryException
  {
    // Virtual static groups don't support nesting.
    Message message = ERR_VIRTUAL_STATIC_GROUP_NESTING_NOT_SUPPORTED.get();
    throw new UnsupportedOperationException(message.toString());
  }



  /**
   * {@inheritDoc}
   */
  @Override()
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
      Message message = ERR_VIRTUAL_STATIC_GROUP_NO_TARGET_GROUP.get(
          String.valueOf(targetGroupDN), String.valueOf(groupEntryDN));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }
    else if (targetGroup instanceof VirtualStaticGroup)
    {
      Message message = ERR_VIRTUAL_STATIC_GROUP_TARGET_CANNOT_BE_VIRTUAL.get(
          String.valueOf(groupEntryDN), String.valueOf(targetGroupDN));
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }
    else
    {
      return targetGroup.isMember(userDN);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
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
      Message message = ERR_VIRTUAL_STATIC_GROUP_NO_TARGET_GROUP.get(
          String.valueOf(targetGroupDN), String.valueOf(groupEntryDN));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }
    else if (targetGroup instanceof VirtualStaticGroup)
    {
      Message message = ERR_VIRTUAL_STATIC_GROUP_TARGET_CANNOT_BE_VIRTUAL.get(
          String.valueOf(groupEntryDN), String.valueOf(targetGroupDN));
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }
    else
    {
      return targetGroup.isMember(userEntry);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public MemberList getMembers()
         throws DirectoryException
  {
    Group targetGroup =
         DirectoryServer.getGroupManager().getGroupInstance(targetGroupDN);
    if (targetGroup == null)
    {
      Message message = ERR_VIRTUAL_STATIC_GROUP_NO_TARGET_GROUP.get(
          String.valueOf(targetGroupDN), String.valueOf(groupEntryDN));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }
    else if (targetGroup instanceof VirtualStaticGroup)
    {
      Message message = ERR_VIRTUAL_STATIC_GROUP_TARGET_CANNOT_BE_VIRTUAL.get(
          String.valueOf(groupEntryDN), String.valueOf(targetGroupDN));
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }
    else
    {
      return targetGroup.getMembers();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public MemberList getMembers(DN baseDN, SearchScope scope,
                               SearchFilter filter)
         throws DirectoryException
  {
    Group targetGroup =
         DirectoryServer.getGroupManager().getGroupInstance(targetGroupDN);
    if (targetGroup == null)
    {
      Message message = ERR_VIRTUAL_STATIC_GROUP_NO_TARGET_GROUP.get(
          String.valueOf(targetGroupDN), String.valueOf(groupEntryDN));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }
    else if (targetGroup instanceof VirtualStaticGroup)
    {
      Message message = ERR_VIRTUAL_STATIC_GROUP_TARGET_CANNOT_BE_VIRTUAL.get(
          String.valueOf(groupEntryDN), String.valueOf(targetGroupDN));
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }
    else
    {
      return targetGroup.getMembers(baseDN, scope, filter);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean mayAlterMemberList()
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void addMember(Entry userEntry)
         throws UnsupportedOperationException, DirectoryException
  {
    // Virtual static groups don't support altering the member list.
    Message message = ERR_VIRTUAL_STATIC_GROUP_ALTERING_MEMBERS_NOT_SUPPORTED.
        get(String.valueOf(groupEntryDN));
    throw new UnsupportedOperationException(message.toString());
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void removeMember(DN userDN)
         throws UnsupportedOperationException, DirectoryException
  {
    // Virtual static groups don't support altering the member list.
    Message message = ERR_VIRTUAL_STATIC_GROUP_ALTERING_MEMBERS_NOT_SUPPORTED.
        get(String.valueOf(groupEntryDN));
    throw new UnsupportedOperationException(message.toString());
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void toString(StringBuilder buffer)
  {
    buffer.append("VirtualStaticGroup(dn=");
    buffer.append(groupEntryDN);
    buffer.append(",targetGroupDN=");
    buffer.append(targetGroupDN);
    buffer.append(")");
  }
}

