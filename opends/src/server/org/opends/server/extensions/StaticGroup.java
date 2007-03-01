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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.api.Group;
import org.opends.server.core.ModifyOperation;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.MemberList;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;

import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.Validator.*;



/**
 * This class provides a static group implementation, in which the DNs
 * of all members are explicitly listed.  There are two variants of
 * static groups:  one based on the {@code groupOfNames} object class,
 * which stores the member list in the {@code member} attribute, and
 * one based on the {@code groupOfUniqueNames} object class, which
 * stores the member list in the {@code uniqueMember} attribute.
 */
public class StaticGroup
       extends Group
{



  // The attribute type used to hold the membership list for this group.
  private AttributeType memberAttributeType;

  // The DN of the entry that holds the definition for this group.
  private DN groupEntryDN;

  // The set of the DNs of the members for this group.
  private LinkedHashSet<DN> memberDNs;



  /**
   * Creates a new, uninitialized static group instance.  This is intended for
   * internal use only.
   */
  public StaticGroup()
  {
    super();


    // No initialization is required here.
  }



  /**
   * Creates a new static group instance with the provided information.
   *
   * @param  groupEntryDN         The DN of the entry that holds the definition
   *                              for this group.
   * @param  memberAttributeType  The attribute type used to hold the membership
   *                              list for this group.
   * @param  memberDNs            The set of the DNs of the members for this
   *                              group.
   */
  public StaticGroup(DN groupEntryDN, AttributeType memberAttributeType,
                     LinkedHashSet<DN> memberDNs)
  {
    super();


    ensureNotNull(groupEntryDN, memberAttributeType, memberDNs);

    this.groupEntryDN        = groupEntryDN;
    this.memberAttributeType = memberAttributeType;
    this.memberDNs           = memberDNs;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializeGroupImplementation(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {

    // No additional initialization is required.
  }




  /**
   * {@inheritDoc}
   */
  @Override()
  public StaticGroup newInstance(Entry groupEntry)
         throws DirectoryException
  {

    ensureNotNull(groupEntry);


    // Determine whether it is a groupOfNames or groupOfUniqueNames entry.  If
    // neither, then that's a problem.
    AttributeType memberAttributeType;
    ObjectClass groupOfNamesClass =
         DirectoryConfig.getObjectClass(OC_GROUP_OF_NAMES_LC, true);
    ObjectClass groupOfUniqueNamesClass =
         DirectoryConfig.getObjectClass(OC_GROUP_OF_UNIQUE_NAMES_LC, true);
    if (groupEntry.hasObjectClass(groupOfNamesClass))
    {
      if (groupEntry.hasObjectClass(groupOfUniqueNamesClass))
      {
        int    msgID   = MSGID_STATICGROUP_INVALID_OC_COMBINATION;
        String message = getMessage(msgID, String.valueOf(groupEntry.getDN()),
                                    OC_GROUP_OF_NAMES,
                                    OC_GROUP_OF_UNIQUE_NAMES);
        throw new DirectoryException(ResultCode.OBJECTCLASS_VIOLATION, message,
                                     msgID);
      }

      memberAttributeType = DirectoryConfig.getAttributeType(ATTR_MEMBER, true);
    }
    else if (groupEntry.hasObjectClass(groupOfUniqueNamesClass))
    {
      memberAttributeType =
           DirectoryConfig.getAttributeType(ATTR_UNIQUE_MEMBER_LC, true);
    }
    else
    {
      int    msgID   = MSGID_STATICGROUP_NO_VALID_OC;
      String message = getMessage(msgID, String.valueOf(groupEntry.getDN()),
                                  OC_GROUP_OF_NAMES, OC_GROUP_OF_UNIQUE_NAMES);
      throw new DirectoryException(ResultCode.OBJECTCLASS_VIOLATION, message,
                                   msgID);
    }


    LinkedHashSet<DN> memberDNs = new LinkedHashSet<DN>();
    List<Attribute> memberAttrList =
         groupEntry.getAttribute(memberAttributeType);
    if (memberAttrList != null)
    {
      for (Attribute a : memberAttrList)
      {
        for (AttributeValue v : a.getValues())
        {
          try
          {
            DN memberDN = DN.decode(v.getValue());
            memberDNs.add(memberDN);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              debugCought(DebugLogLevel.ERROR, de);
            }

            int    msgID   = MSGID_STATICGROUP_CANNOT_DECODE_MEMBER_VALUE_AS_DN;
            String message =
                 getMessage(msgID, v.getStringValue(),
                            memberAttributeType.getNameOrOID(),
                            String.valueOf(groupEntry.getDN()),
                            de.getErrorMessage());
            logError(ErrorLogCategory.EXTENSIONS, ErrorLogSeverity.MILD_ERROR,
                     message, msgID);
          }
        }
      }
    }


    return new StaticGroup(groupEntry.getDN(), memberAttributeType, memberDNs);
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
    String filterString =
         "(|(objectClass=groupOfNames)(objectClass=groupOfUniqueNames))";
    return SearchFilter.createFilterFromString(filterString);
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
    ObjectClass groupOfNamesClass =
         DirectoryConfig.getObjectClass(OC_GROUP_OF_NAMES_LC, true);
    ObjectClass groupOfUniqueNamesClass =
         DirectoryConfig.getObjectClass(OC_GROUP_OF_UNIQUE_NAMES_LC, true);
    if (entry.hasObjectClass(groupOfNamesClass))
    {
      if (entry.hasObjectClass(groupOfUniqueNamesClass))
      {
        return false;
      }

      return true;
    }
    else if (entry.hasObjectClass(groupOfUniqueNamesClass))
    {
      return true;
    }
    else
    {
      return false;
    }
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
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsNestedGroups()
  {

    // FIXME -- We should add support for nested groups.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public List<DN> getNestedGroupDNs()
  {

    // FIXME -- We should add support for nested groups.
    return Collections.<DN>emptyList();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void addNestedGroup(DN nestedGroupDN)
         throws UnsupportedOperationException, DirectoryException
  {

    // FIXME -- We should add support for nested groups.
    throw new UnsupportedOperationException();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void removeNestedGroup(DN nestedGroupDN)
         throws UnsupportedOperationException, DirectoryException
  {

    // FIXME -- We should add support for nested groups.
    throw new UnsupportedOperationException();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isMember(DN userDN)
         throws DirectoryException
  {

    return memberDNs.contains(userDN);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isMember(Entry userEntry)
         throws DirectoryException
  {

    return memberDNs.contains(userEntry.getDN());
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public MemberList getMembers()
         throws DirectoryException
  {

    return new SimpleStaticGroupMemberList(groupEntryDN, memberDNs);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public MemberList getMembers(DN baseDN, SearchScope scope,
                               SearchFilter filter)
         throws DirectoryException
  {

    if ((baseDN == null) && (filter == null))
    {
      return new SimpleStaticGroupMemberList(groupEntryDN, memberDNs);
    }
    else
    {
      return new FilteredStaticGroupMemberList(groupEntryDN, memberDNs, baseDN,
                                               scope, filter);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean mayAlterMemberList()
  {

    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void addMember(Entry userEntry)
         throws UnsupportedOperationException, DirectoryException
  {

    ensureNotNull(userEntry);

    synchronized (this)
    {
      DN userDN = userEntry.getDN();
      if (memberDNs.contains(userDN))
      {
        int    msgID   = MSGID_STATICGROUP_ADD_MEMBER_ALREADY_EXISTS;
        String message = getMessage(msgID, String.valueOf(userDN),
                                    String.valueOf(groupEntryDN));
        throw new DirectoryException(ResultCode.ATTRIBUTE_OR_VALUE_EXISTS,
                                     message, msgID);
      }

      LinkedHashSet<AttributeValue> values =
           new LinkedHashSet<AttributeValue>(1);
      values.add(new AttributeValue(memberAttributeType, userDN.toString()));

      Attribute attr = new Attribute(memberAttributeType,
                                     memberAttributeType.getNameOrOID(),
                                     values);

      LinkedList<Modification> mods = new LinkedList<Modification>();
      mods.add(new Modification(ModificationType.ADD, attr));

      LinkedList<Control> requestControls = new LinkedList<Control>();
      requestControls.add(new Control(OID_INTERNAL_GROUP_MEMBERSHIP_UPDATE,
                                      false));

      InternalClientConnection conn =
           InternalClientConnection.getRootConnection();
      ModifyOperation modifyOperation =
           new ModifyOperation(conn, conn.nextOperationID(),
                               conn.nextMessageID(), requestControls,
                               groupEntryDN, mods);
      modifyOperation.run();
      if (modifyOperation.getResultCode() != ResultCode.SUCCESS)
      {
        int    msgID   = MSGID_STATICGROUP_ADD_MEMBER_UPDATE_FAILED;
        String message = getMessage(msgID, String.valueOf(userDN),
                              String.valueOf(groupEntryDN),
                              modifyOperation.getErrorMessage().toString());
        throw new DirectoryException(modifyOperation.getResultCode(), message,
                                     msgID);
      }


      LinkedHashSet<DN> newMemberDNs =
           new LinkedHashSet<DN>(memberDNs.size()+1);
      newMemberDNs.addAll(memberDNs);
      newMemberDNs.add(userDN);
      memberDNs = newMemberDNs;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void removeMember(DN userDN)
         throws UnsupportedOperationException, DirectoryException
  {

    ensureNotNull(userDN);

    synchronized (this)
    {
      if (! memberDNs.contains(userDN))
      {
        int    msgID   = MSGID_STATICGROUP_REMOVE_MEMBER_NO_SUCH_MEMBER;
        String message = getMessage(msgID, String.valueOf(userDN),
                                    String.valueOf(groupEntryDN));
        throw new DirectoryException(ResultCode.NO_SUCH_ATTRIBUTE, message,
                                     msgID);
      }


      LinkedHashSet<AttributeValue> values =
           new LinkedHashSet<AttributeValue>(1);
      values.add(new AttributeValue(memberAttributeType, userDN.toString()));

      Attribute attr = new Attribute(memberAttributeType,
                                     memberAttributeType.getNameOrOID(),
                                     values);

      LinkedList<Modification> mods = new LinkedList<Modification>();
      mods.add(new Modification(ModificationType.DELETE, attr));

      LinkedList<Control> requestControls = new LinkedList<Control>();
      requestControls.add(new Control(OID_INTERNAL_GROUP_MEMBERSHIP_UPDATE,
                                      false));

      InternalClientConnection conn =
           InternalClientConnection.getRootConnection();
      ModifyOperation modifyOperation =
           new ModifyOperation(conn, conn.nextOperationID(),
                               conn.nextMessageID(), requestControls,
                               groupEntryDN, mods);
      modifyOperation.run();
      if (modifyOperation.getResultCode() != ResultCode.SUCCESS)
      {
        int    msgID   = MSGID_STATICGROUP_REMOVE_MEMBER_UPDATE_FAILED;
        String message = getMessage(msgID, String.valueOf(userDN),
                              String.valueOf(groupEntryDN),
                              modifyOperation.getErrorMessage().toString());
        throw new DirectoryException(modifyOperation.getResultCode(), message,
                                     msgID);
      }


      LinkedHashSet<DN> newMemberDNs = new LinkedHashSet<DN>(memberDNs);
      newMemberDNs.remove(userDN);
      memberDNs = newMemberDNs;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void toString(StringBuilder buffer)
  {

    buffer.append("StaticGroup(");
    buffer.append(groupEntryDN);
    buffer.append(")");
  }
}

