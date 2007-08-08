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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.opends.server.admin.std.server.GroupImplementationCfg;
import org.opends.server.api.Group;
import org.opends.server.core.DirectoryServer;
import org.opends.server.config.ConfigException;
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

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.Validator.*;



/**
 * This class provides a virtual static group implementation, in which
 * membership is based on membership of another group.
 */
public class VirtualStaticGroup
       extends Group<GroupImplementationCfg>
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
                   GroupImplementationCfg configuration)
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
        for (AttributeValue v : a.getValues())
        {
          if (targetDN != null)
          {
            int    msgID   = MSGID_VIRTUAL_STATIC_GROUP_MULTIPLE_TARGETS;
            String message = getMessage(msgID,
                                        String.valueOf(groupEntry.getDN()));
            throw new DirectoryException(ResultCode.OBJECTCLASS_VIOLATION,
                                         message, msgID);
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

            int    msgID   = MSGID_VIRTUAL_STATIC_GROUP_CANNOT_DECODE_TARGET;
            String message = getMessage(msgID, v.getStringValue(),
                                        String.valueOf(groupEntry.getDN()),
                                        de.getErrorMessage());
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message, msgID, de);
          }
        }
      }
    }

    if (targetDN == null)
    {
      int    msgID   = MSGID_VIRTUAL_STATIC_GROUP_NO_TARGET;
      String message = getMessage(msgID, String.valueOf(groupEntry.getDN()));
      throw new DirectoryException(ResultCode.OBJECTCLASS_VIOLATION, message,
                                   msgID);
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
    int    msgID   = MSGID_VIRTUAL_STATIC_GROUP_NESTING_NOT_SUPPORTED;
    String message = getMessage(msgID);
    throw new UnsupportedOperationException(message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void removeNestedGroup(DN nestedGroupDN)
         throws UnsupportedOperationException, DirectoryException
  {
    // Virtual static groups don't support nesting.
    int    msgID   = MSGID_VIRTUAL_STATIC_GROUP_NESTING_NOT_SUPPORTED;
    String message = getMessage(msgID);
    throw new UnsupportedOperationException(message);
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
      int    msgID   = MSGID_VIRTUAL_STATIC_GROUP_NO_TARGET_GROUP;
      String message = getMessage(msgID, String.valueOf(targetGroupDN),
                                  String.valueOf(groupEntryDN));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID);
    }
    else if (targetGroup instanceof VirtualStaticGroup)
    {
      int    msgID   = MSGID_VIRTUAL_STATIC_GROUP_TARGET_CANNOT_BE_VIRTUAL;
      String message = getMessage(msgID, String.valueOf(groupEntryDN),
                                  String.valueOf(targetGroupDN));
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
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
      int    msgID   = MSGID_VIRTUAL_STATIC_GROUP_NO_TARGET_GROUP;
      String message = getMessage(msgID, String.valueOf(targetGroupDN),
                                  String.valueOf(groupEntryDN));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID);
    }
    else if (targetGroup instanceof VirtualStaticGroup)
    {
      int    msgID   = MSGID_VIRTUAL_STATIC_GROUP_TARGET_CANNOT_BE_VIRTUAL;
      String message = getMessage(msgID, String.valueOf(groupEntryDN),
                                  String.valueOf(targetGroupDN));
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
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
      int    msgID   = MSGID_VIRTUAL_STATIC_GROUP_NO_TARGET_GROUP;
      String message = getMessage(msgID, String.valueOf(targetGroupDN),
                                  String.valueOf(groupEntryDN));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID);
    }
    else if (targetGroup instanceof VirtualStaticGroup)
    {
      int    msgID   = MSGID_VIRTUAL_STATIC_GROUP_TARGET_CANNOT_BE_VIRTUAL;
      String message = getMessage(msgID, String.valueOf(groupEntryDN),
                                  String.valueOf(targetGroupDN));
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
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
      int    msgID   = MSGID_VIRTUAL_STATIC_GROUP_NO_TARGET_GROUP;
      String message = getMessage(msgID, String.valueOf(targetGroupDN),
                                  String.valueOf(groupEntryDN));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID);
    }
    else if (targetGroup instanceof VirtualStaticGroup)
    {
      int    msgID   = MSGID_VIRTUAL_STATIC_GROUP_TARGET_CANNOT_BE_VIRTUAL;
      String message = getMessage(msgID, String.valueOf(groupEntryDN),
                                  String.valueOf(targetGroupDN));
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
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
    int    msgID   = MSGID_VIRTUAL_STATIC_GROUP_ALTERING_MEMBERS_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(groupEntryDN));
    throw new UnsupportedOperationException(message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void removeMember(DN userDN)
         throws UnsupportedOperationException, DirectoryException
  {
    // Virtual static groups don't support altering the member list.
    int    msgID   = MSGID_VIRTUAL_STATIC_GROUP_ALTERING_MEMBERS_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(groupEntryDN));
    throw new UnsupportedOperationException(message);
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

