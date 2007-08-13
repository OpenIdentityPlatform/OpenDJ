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



import java.util.*;

import org.opends.server.admin.std.server.GroupImplementationCfg;
import org.opends.server.api.Group;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.config.ConfigException;
import org.opends.server.protocols.internal.InternalClientConnection;

import org.opends.server.types.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
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
       extends Group<GroupImplementationCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The attribute type used to hold the membership list for this group.
  private AttributeType memberAttributeType;

  // The DN of the entry that holds the definition for this group.
  private DN groupEntryDN;

  // The set of the DNs of the members for this group.
  private LinkedHashSet<DN> memberDNs;

  //The list of nested group DNs for this group.
  private LinkedList<DN> nestedGroups = new LinkedList<DN>();

  //Passed to the group manager to see if the nested group list needs to be
  //refreshed.
  private long nestedGroupRefreshToken =
                              DirectoryServer.getGroupManager().refreshToken();

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
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
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
         "(&(|(objectClass=groupOfNames)(objectClass=groupOfUniqueNames))" +
            "(!(objectClass=ds-virtual-static-group)))";
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
    ObjectClass virtualStaticGroupClass =
         DirectoryConfig.getObjectClass(OC_VIRTUAL_STATIC_GROUP, true);
    if (entry.hasObjectClass(virtualStaticGroupClass))
    {
      return false;
    }

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
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public List<DN> getNestedGroupDNs()
  {
    try {
       reloadIfNeeded();
    } catch (DirectoryException ex) {
      return Collections.<DN>emptyList();
    }
    return nestedGroups;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void addNestedGroup(DN nestedGroupDN)
         throws UnsupportedOperationException, DirectoryException
  {
     ensureNotNull(nestedGroupDN);

    synchronized (this)
    {
      if (nestedGroups.contains(nestedGroupDN))
      {
        int    msgID   = MSGID_STATICGROUP_ADD_NESTED_GROUP_ALREADY_EXISTS;
        String message = getMessage(msgID,String.valueOf(nestedGroupDN),
                                    String.valueOf(groupEntryDN));
        throw new DirectoryException(ResultCode.ATTRIBUTE_OR_VALUE_EXISTS,
                                     message, msgID);
      }

      LinkedHashSet<AttributeValue> values =
           new LinkedHashSet<AttributeValue>(1);
      values.add(new AttributeValue(memberAttributeType,
                                    nestedGroupDN.toString()));

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
      ModifyOperationBasis modifyOperation =
           new ModifyOperationBasis(conn,
                   InternalClientConnection.nextOperationID(),
                   InternalClientConnection.nextMessageID(), requestControls,
                               groupEntryDN, mods);
      modifyOperation.run();
      if (modifyOperation.getResultCode() != ResultCode.SUCCESS)
      {
        int    msgID   = MSGID_STATICGROUP_ADD_MEMBER_UPDATE_FAILED;
        String message = getMessage(msgID, String.valueOf(nestedGroupDN),
                              String.valueOf(groupEntryDN),
                              modifyOperation.getErrorMessage().toString());
        throw new DirectoryException(modifyOperation.getResultCode(), message,
                                     msgID);
      }


      LinkedList<DN> newNestedGroups = new LinkedList<DN>(nestedGroups);
      newNestedGroups.add(nestedGroupDN);
      nestedGroups = newNestedGroups;
      //Add it to the member DN list.
      LinkedHashSet<DN> newMemberDNs = new LinkedHashSet<DN>(memberDNs);
      newMemberDNs.add(nestedGroupDN);
      memberDNs = newMemberDNs;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void removeNestedGroup(DN nestedGroupDN)
         throws UnsupportedOperationException, DirectoryException
  {
    ensureNotNull(nestedGroupDN);

    synchronized (this)
    {
      if (! nestedGroups.contains(nestedGroupDN))
      {
        int    msgID   = MSGID_STATICGROUP_REMOVE_NESTED_GROUP_NO_SUCH_GROUP;
        String message = getMessage(msgID, String.valueOf(nestedGroupDN),
                                    String.valueOf(groupEntryDN));
        throw new DirectoryException(ResultCode.NO_SUCH_ATTRIBUTE, message,
                                     msgID);
      }

      LinkedHashSet<AttributeValue> values =
           new LinkedHashSet<AttributeValue>(1);
      values.add(new AttributeValue(memberAttributeType,
                                                     nestedGroupDN.toString()));

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
      ModifyOperationBasis modifyOperation =
           new ModifyOperationBasis(conn,
                   InternalClientConnection.nextOperationID(),
                   InternalClientConnection.nextMessageID(), requestControls,
                   groupEntryDN, mods);
      modifyOperation.run();
      if (modifyOperation.getResultCode() != ResultCode.SUCCESS)
      {
        int    msgID   = MSGID_STATICGROUP_REMOVE_MEMBER_UPDATE_FAILED;
        String message = getMessage(msgID,String.valueOf(nestedGroupDN),
                              String.valueOf(groupEntryDN),
                              modifyOperation.getErrorMessage().toString());
        throw new DirectoryException(modifyOperation.getResultCode(), message,
                                     msgID);
      }


      LinkedList<DN> newNestedGroups = new LinkedList<DN>(nestedGroups);
      newNestedGroups.remove(nestedGroupDN);
      nestedGroups = newNestedGroups;
      //Remove it from the member DN list.
      LinkedHashSet<DN> newMemberDNs = new LinkedHashSet<DN>(memberDNs);
      newMemberDNs.remove(nestedGroupDN);
      memberDNs = newMemberDNs;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isMember(DN userDN, Set<DN> examinedGroups)
          throws DirectoryException
  {
    reloadIfNeeded();
    if(memberDNs.contains(userDN))
    {
      return true;
    }
    else if (! examinedGroups.add(getGroupDN()))
    {
      return false;
    }
    else
    {
      for(DN nestedGroupDN : nestedGroups)
      {
        Group<? extends GroupImplementationCfg> g =
             (Group<? extends GroupImplementationCfg>)
              DirectoryServer.getGroupManager().getGroupInstance(nestedGroupDN);
        if((g != null) && (g.isMember(userDN, examinedGroups)))
        {
          return true;
        }
      }
    }
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isMember(Entry userEntry, Set<DN> examinedGroups)
         throws DirectoryException
  {
    return isMember(userEntry.getDN(), examinedGroups);
  }



  /**
   * Check if the group manager has registered a new group instance or removed a
   * a group instance that might impact this group's membership list.
   */
  private void
  reloadIfNeeded() throws DirectoryException
  {
    //Check if group instances have changed by passing the group manager
    //the current token.
    if(DirectoryServer.getGroupManager().
            hasInstancesChanged(nestedGroupRefreshToken))
    {
      synchronized (this)
      {
        Group thisGroup =
               DirectoryServer.getGroupManager().getGroupInstance(groupEntryDN);
        //Check if the group itself has been removed
        if(thisGroup == null) {
          int    msgID   = MSGID_STATICGROUP_GROUP_INSTANCE_INVALID;
          String message = getMessage(msgID, String.valueOf(groupEntryDN));
          throw new
               DirectoryException(ResultCode.NO_SUCH_ATTRIBUTE, message, msgID);
        } else if(thisGroup != this) {
          LinkedHashSet<DN> newMemberDNs = new LinkedHashSet<DN>();
          MemberList memberList=thisGroup.getMembers();
          while (memberList.hasMoreMembers()) {
            try {
              newMemberDNs.add(memberList.nextMemberDN());
            } catch (MembershipException ex) {}
          }
          memberDNs=newMemberDNs;
        }
        LinkedList<DN> newNestedGroups = new LinkedList<DN>();
        for(DN dn : memberDNs)
        {
          Group gr=DirectoryServer.getGroupManager().getGroupInstance(dn);
          if(gr != null)
          {
            newNestedGroups.add(gr.getGroupDN());
          }
        }
        nestedGroupRefreshToken =
                DirectoryServer.getGroupManager().refreshToken();
        nestedGroups=newNestedGroups;
      }
    }
  }


  /**
   * {@inheritDoc}
   */
  @Override()
  public MemberList getMembers()
         throws DirectoryException
  {
    reloadIfNeeded();
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
    reloadIfNeeded();
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
      ModifyOperationBasis modifyOperation =
           new ModifyOperationBasis(conn, conn.nextOperationID(),
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
      ModifyOperationBasis modifyOperation =
           new ModifyOperationBasis(conn, conn.nextOperationID(),
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
      //If it is in the nested group list remove it.
      if(nestedGroups.contains(userDN)) {
        LinkedList<DN> newNestedGroups = new LinkedList<DN>(nestedGroups);
        newNestedGroups.remove(userDN);
        nestedGroups = newNestedGroups;
      }
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

