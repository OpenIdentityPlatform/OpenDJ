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
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.adapter.server3x.Converters;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DN.CompactDn;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.util.Reject;
import org.opends.server.admin.std.server.GroupImplementationCfg;
import org.opends.server.admin.std.server.StaticGroupImplementationCfg;
import org.opends.server.api.Group;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.core.ServerContext;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.MemberList;
import org.opends.server.types.MembershipException;
import org.opends.server.types.Modification;
import org.opends.server.types.SearchFilter;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;

/**
 * A static group implementation, in which the DNs of all members are explicitly
 * listed.
 * <p>
 * There are three variants of static groups:
 * <ul>
 *   <li>one based on the {@code groupOfNames} object class: which stores the
 * member list in the {@code member} attribute</li>
 *   <li>one based on the {@code groupOfEntries} object class, which also stores
 * the member list in the {@code member} attribute</li>
 *   <li>one based on the {@code groupOfUniqueNames} object class, which stores
 * the member list in the {@code uniqueMember} attribute.</li>
 * </ul>
 */
public class StaticGroup extends Group<StaticGroupImplementationCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The attribute type used to hold the membership list for this group. */
  private AttributeType memberAttributeType;

  /** The DN of the entry that holds the definition for this group. */
  private DN groupEntryDN;

  /** The set of the DNs of the members for this group. */
  private LinkedHashSet<CompactDn> memberDNs;

  /** The list of nested group DNs for this group. */
  private LinkedList<DN> nestedGroups = new LinkedList<>();

  /** Passed to the group manager to see if the nested group list needs to be refreshed. */
  private long nestedGroupRefreshToken = DirectoryServer.getGroupManager().refreshToken();

  /** Read/write lock protecting memberDNs and nestedGroups. */
  private ReadWriteLock lock = new ReentrantReadWriteLock();

  private ServerContext serverContext;

  /**
   * Creates an uninitialized static group. This is intended for internal use
   * only, to allow {@code GroupManager} to dynamically create a group.
   */
  public StaticGroup()
  {
    super();
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
  private StaticGroup(ServerContext serverContext, DN groupEntryDN, AttributeType memberAttributeType,
      LinkedHashSet<CompactDn> memberDNs)
  {
    super();
    Reject.ifNull(groupEntryDN, memberAttributeType, memberDNs);

    this.serverContext       = serverContext;
    this.groupEntryDN        = groupEntryDN;
    this.memberAttributeType = memberAttributeType;
    this.memberDNs           = memberDNs;
  }

  /** {@inheritDoc} */
  @Override
  public void initializeGroupImplementation(StaticGroupImplementationCfg configuration)
         throws ConfigException, InitializationException
  {
    // No additional initialization is required.
  }

  /** {@inheritDoc} */
  @Override
  public StaticGroup newInstance(ServerContext serverContext, Entry groupEntry) throws DirectoryException
  {
    Reject.ifNull(groupEntry);

    // Determine whether it is a groupOfNames, groupOfEntries or
    // groupOfUniqueNames entry.  If not, then that's a problem.
    AttributeType someMemberAttributeType;
    boolean hasGroupOfEntriesClass = hasObjectClass(groupEntry, OC_GROUP_OF_ENTRIES_LC);
    boolean hasGroupOfNamesClass = hasObjectClass(groupEntry, OC_GROUP_OF_NAMES_LC);
    boolean hasGroupOfUniqueNamesClass = hasObjectClass(groupEntry, OC_GROUP_OF_UNIQUE_NAMES_LC);
    if (hasGroupOfEntriesClass)
    {
      if (hasGroupOfNamesClass)
      {
        LocalizableMessage message = ERR_STATICGROUP_INVALID_OC_COMBINATION.get(
            groupEntry.getName(), OC_GROUP_OF_ENTRIES, OC_GROUP_OF_NAMES);
        throw new DirectoryException(ResultCode.OBJECTCLASS_VIOLATION, message);
      }
      else if (hasGroupOfUniqueNamesClass)
      {
        LocalizableMessage message = ERR_STATICGROUP_INVALID_OC_COMBINATION.get(
            groupEntry.getName(), OC_GROUP_OF_ENTRIES, OC_GROUP_OF_UNIQUE_NAMES);
        throw new DirectoryException(ResultCode.OBJECTCLASS_VIOLATION, message);
      }

      someMemberAttributeType = DirectoryServer.getAttributeType(ATTR_MEMBER);
    }
    else if (hasGroupOfNamesClass)
    {
      if (hasGroupOfUniqueNamesClass)
      {
        LocalizableMessage message = ERR_STATICGROUP_INVALID_OC_COMBINATION.get(
            groupEntry.getName(), OC_GROUP_OF_NAMES, OC_GROUP_OF_UNIQUE_NAMES);
        throw new DirectoryException(ResultCode.OBJECTCLASS_VIOLATION, message);
      }

      someMemberAttributeType = DirectoryServer.getAttributeType(ATTR_MEMBER);
    }
    else if (hasGroupOfUniqueNamesClass)
    {
      someMemberAttributeType = DirectoryServer.getAttributeType(ATTR_UNIQUE_MEMBER_LC);
    }
    else
    {
      LocalizableMessage message =
          ERR_STATICGROUP_NO_VALID_OC.get(groupEntry.getName(), OC_GROUP_OF_NAMES, OC_GROUP_OF_UNIQUE_NAMES);
      throw new DirectoryException(ResultCode.OBJECTCLASS_VIOLATION, message);
    }

    List<Attribute> memberAttrList = groupEntry.getAttribute(someMemberAttributeType);
    int membersCount = 0;
    for (Attribute a : memberAttrList)
    {
      membersCount += a.size();
    }
    LinkedHashSet<CompactDn> someMemberDNs = new LinkedHashSet<>(membersCount);
    for (Attribute a : memberAttrList)
    {
      for (ByteString v : a)
      {
        try
        {
          someMemberDNs.add(DN.valueOf(v.toString()).compact());
        }
        catch (LocalizedIllegalArgumentException e)
        {
          logger.traceException(e);
          logger.error(ERR_STATICGROUP_CANNOT_DECODE_MEMBER_VALUE_AS_DN,
              v, someMemberAttributeType.getNameOrOID(), groupEntry.getName(), e.getMessageObject());
        }
      }
    }
    return new StaticGroup(serverContext, groupEntry.getName(), someMemberAttributeType, someMemberDNs);
  }

  /** {@inheritDoc} */
  @Override
  public SearchFilter getGroupDefinitionFilter()
         throws DirectoryException
  {
    // FIXME -- This needs to exclude enhanced groups once we have support for them.
    String filterString =
         "(&(|(objectClass=groupOfNames)(objectClass=groupOfUniqueNames)" +
            "(objectClass=groupOfEntries))" +
            "(!(objectClass=ds-virtual-static-group)))";
    return SearchFilter.createFilterFromString(filterString);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isGroupDefinition(Entry entry)
  {
    Reject.ifNull(entry);

    // FIXME -- This needs to exclude enhanced groups once we have support for them.
    if (hasObjectClass(entry, OC_VIRTUAL_STATIC_GROUP))
    {
      return false;
    }

    boolean hasGroupOfEntriesClass = hasObjectClass(entry, OC_GROUP_OF_ENTRIES_LC);
    boolean hasGroupOfNamesClass = hasObjectClass(entry, OC_GROUP_OF_NAMES_LC);
    boolean hasGroupOfUniqueNamesClass = hasObjectClass(entry, OC_GROUP_OF_UNIQUE_NAMES_LC);
    if (hasGroupOfEntriesClass)
    {
      return !hasGroupOfNamesClass
          && !hasGroupOfUniqueNamesClass;
    }
    else if (hasGroupOfNamesClass)
    {
      return !hasGroupOfUniqueNamesClass;
    }
    else
    {
      return hasGroupOfUniqueNamesClass;
    }
  }

  private boolean hasObjectClass(Entry entry, String ocName)
  {
    return entry.hasObjectClass(DirectoryConfig.getObjectClass(ocName, true));
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

  /** {@inheritDoc} */
  @Override
  public boolean supportsNestedGroups()
  {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public List<DN> getNestedGroupDNs()
  {
    try
    {
       reloadIfNeeded();
    }
    catch (DirectoryException ex)
    {
      return Collections.<DN>emptyList();
    }
    lock.readLock().lock();
    try
    {
      return nestedGroups;
    }
    finally
    {
      lock.readLock().unlock();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addNestedGroup(DN nestedGroupDN)
         throws UnsupportedOperationException, DirectoryException
  {
    Reject.ifNull(nestedGroupDN);

    lock.writeLock().lock();
    try
    {
      if (nestedGroups.contains(nestedGroupDN))
      {
        LocalizableMessage msg = ERR_STATICGROUP_ADD_NESTED_GROUP_ALREADY_EXISTS.get(nestedGroupDN, groupEntryDN);
        throw new DirectoryException(ResultCode.ATTRIBUTE_OR_VALUE_EXISTS, msg);
      }

      ModifyOperation modifyOperation = newModifyOperation(ModificationType.ADD, nestedGroupDN);
      modifyOperation.run();
      if (modifyOperation.getResultCode() != ResultCode.SUCCESS)
      {
        LocalizableMessage msg = ERR_STATICGROUP_ADD_MEMBER_UPDATE_FAILED.get(
            nestedGroupDN, groupEntryDN, modifyOperation.getErrorMessage());
        throw new DirectoryException(modifyOperation.getResultCode(), msg);
      }

      LinkedList<DN> newNestedGroups = new LinkedList<>(nestedGroups);
      newNestedGroups.add(nestedGroupDN);
      nestedGroups = newNestedGroups;
      //Add it to the member DN list.
      LinkedHashSet<CompactDn> newMemberDNs = new LinkedHashSet<>(memberDNs);
      newMemberDNs.add(toCompactDn(nestedGroupDN));
      memberDNs = newMemberDNs;
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void removeNestedGroup(DN nestedGroupDN)
         throws UnsupportedOperationException, DirectoryException
  {
    Reject.ifNull(nestedGroupDN);

    lock.writeLock().lock();
    try
    {
      if (! nestedGroups.contains(nestedGroupDN))
      {
        throw new DirectoryException(ResultCode.NO_SUCH_ATTRIBUTE,
                ERR_STATICGROUP_REMOVE_NESTED_GROUP_NO_SUCH_GROUP.get(nestedGroupDN, groupEntryDN));
      }

      ModifyOperation modifyOperation = newModifyOperation(ModificationType.DELETE, nestedGroupDN);
      modifyOperation.run();
      if (modifyOperation.getResultCode() != ResultCode.SUCCESS)
      {
        LocalizableMessage message = ERR_STATICGROUP_REMOVE_MEMBER_UPDATE_FAILED.get(
            nestedGroupDN, groupEntryDN, modifyOperation.getErrorMessage());
        throw new DirectoryException(modifyOperation.getResultCode(), message);
      }

      LinkedList<DN> newNestedGroups = new LinkedList<>(nestedGroups);
      newNestedGroups.remove(nestedGroupDN);
      nestedGroups = newNestedGroups;
      //Remove it from the member DN list.
      LinkedHashSet<CompactDn> newMemberDNs = new LinkedHashSet<>(memberDNs);
      newMemberDNs.remove(toCompactDn(nestedGroupDN));
      memberDNs = newMemberDNs;
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isMember(DN userDN, Set<DN> examinedGroups) throws DirectoryException
  {
    reloadIfNeeded();
    CompactDn compactUserDN = toCompactDn(userDN);
    lock.readLock().lock();
    try
    {
      if (memberDNs.contains(compactUserDN))
      {
        return true;
      }
      else if (!examinedGroups.add(getGroupDN()))
      {
        return false;
      }
      else
      {
        for (DN nestedGroupDN : nestedGroups)
        {
          Group<? extends GroupImplementationCfg> group = getGroupManager().getGroupInstance(nestedGroupDN);
          if (group != null && group.isMember(userDN, examinedGroups))
          {
            return true;
          }
        }
      }
    }
    finally
    {
      lock.readLock().unlock();
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isMember(Entry userEntry, Set<DN> examinedGroups)
         throws DirectoryException
  {
    return isMember(userEntry.getName(), examinedGroups);
  }

  /**
   * Check if the group manager has registered a new group instance or removed a
   * a group instance that might impact this group's membership list.
   */
  private void reloadIfNeeded() throws DirectoryException
  {
    //Check if group instances have changed by passing the group manager
    //the current token.
    if (DirectoryServer.getGroupManager().hasInstancesChanged(nestedGroupRefreshToken))
    {
      lock.writeLock().lock();
      try
      {
        Group<?> thisGroup = DirectoryServer.getGroupManager().getGroupInstance(groupEntryDN);
        // Check if the group itself has been removed
        if (thisGroup == null)
        {
          throw new DirectoryException(ResultCode.NO_SUCH_ATTRIBUTE,
                  ERR_STATICGROUP_GROUP_INSTANCE_INVALID.get(groupEntryDN));
        }
        else if (thisGroup != this)
        {
          LinkedHashSet<CompactDn> newMemberDNs = new LinkedHashSet<>();
          MemberList memberList = thisGroup.getMembers();
          while (memberList.hasMoreMembers())
          {
            try
            {
              newMemberDNs.add(toCompactDn(memberList.nextMemberDN()));
            }
            catch (MembershipException ex)
            {
              // TODO: should we throw an exception there instead of silently fail ?
            }
          }
          memberDNs = newMemberDNs;
        }
        nestedGroups.clear();
        for (CompactDn compactDn : memberDNs)
        {
          DN dn = fromCompactDn(compactDn);
          Group<?> group = DirectoryServer.getGroupManager().getGroupInstance(dn);
          if (group != null)
          {
            nestedGroups.add(group.getGroupDN());
          }
        }
        nestedGroupRefreshToken = DirectoryServer.getGroupManager().refreshToken();
      }
      finally
      {
        lock.writeLock().unlock();
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public MemberList getMembers() throws DirectoryException
  {
    reloadIfNeeded();
    lock.readLock().lock();
    try
    {
      return new SimpleStaticGroupMemberList(groupEntryDN, memberDNs);
    }
    finally
    {
      lock.readLock().unlock();
    }
  }

  /** {@inheritDoc} */
  @Override
  public MemberList getMembers(DN baseDN, SearchScope scope, SearchFilter filter) throws DirectoryException
  {
    reloadIfNeeded();
    lock.readLock().lock();
    try
    {
      if (baseDN == null && filter == null)
      {
        return new SimpleStaticGroupMemberList(groupEntryDN, memberDNs);
      }
      return new FilteredStaticGroupMemberList(groupEntryDN, memberDNs, baseDN, scope, filter);
    }
    finally
    {
      lock.readLock().unlock();
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean mayAlterMemberList()
  {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public void updateMembers(List<Modification> modifications)
         throws UnsupportedOperationException, DirectoryException
  {
    Reject.ifNull(memberDNs);
    Reject.ifNull(nestedGroups);

    reloadIfNeeded();
    lock.writeLock().lock();
    try
    {
      for (Modification mod : modifications)
      {
        Attribute attribute = mod.getAttribute();
        if (attribute.getAttributeDescription().getAttributeType().equals(memberAttributeType))
        {
          switch (mod.getModificationType().asEnum())
          {
            case ADD:
              for (ByteString v : attribute)
              {
                DN member = DN.valueOf(v);
                memberDNs.add(toCompactDn(member));
                if (DirectoryServer.getGroupManager().getGroupInstance(member) != null)
                {
                  nestedGroups.add(member);
                }
              }
              break;
            case DELETE:
              if (attribute.isEmpty())
              {
                memberDNs.clear();
                nestedGroups.clear();
              }
              else
              {
                for (ByteString v : attribute)
                {
                  DN member = DN.valueOf(v);
                  memberDNs.remove(toCompactDn(member));
                  nestedGroups.remove(member);
                }
              }
              break;
            case REPLACE:
              memberDNs.clear();
              nestedGroups.clear();
              for (ByteString v : attribute)
              {
                DN member = DN.valueOf(v);
                memberDNs.add(toCompactDn(member));
                if (DirectoryServer.getGroupManager().getGroupInstance(member) != null)
                {
                  nestedGroups.add(member);
                }
              }
              break;
          }
        }
      }
    }
    finally {
      lock.writeLock().unlock();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addMember(Entry userEntry) throws UnsupportedOperationException, DirectoryException
  {
    Reject.ifNull(userEntry);

    lock.writeLock().lock();
    try
    {
      DN userDN = userEntry.getName();
      CompactDn compactUserDN = toCompactDn(userDN);

      if (memberDNs.contains(compactUserDN))
      {
        LocalizableMessage message = ERR_STATICGROUP_ADD_MEMBER_ALREADY_EXISTS.get(userDN, groupEntryDN);
        throw new DirectoryException(ResultCode.ATTRIBUTE_OR_VALUE_EXISTS, message);
      }

      ModifyOperation modifyOperation = newModifyOperation(ModificationType.ADD, userDN);
      modifyOperation.run();
      if (modifyOperation.getResultCode() != ResultCode.SUCCESS)
      {
        throw new DirectoryException(modifyOperation.getResultCode(),
            ERR_STATICGROUP_ADD_MEMBER_UPDATE_FAILED.get(userDN, groupEntryDN, modifyOperation.getErrorMessage()));
      }

      LinkedHashSet<CompactDn> newMemberDNs = new LinkedHashSet<CompactDn>(memberDNs);
      newMemberDNs.add(compactUserDN);
      memberDNs = newMemberDNs;
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void removeMember(DN userDN) throws UnsupportedOperationException, DirectoryException
  {
    Reject.ifNull(userDN);

    CompactDn compactUserDN = toCompactDn(userDN);
    lock.writeLock().lock();
    try
    {
      if (! memberDNs.contains(compactUserDN))
      {
        LocalizableMessage message = ERR_STATICGROUP_REMOVE_MEMBER_NO_SUCH_MEMBER.get(userDN, groupEntryDN);
        throw new DirectoryException(ResultCode.NO_SUCH_ATTRIBUTE, message);
      }

      ModifyOperation modifyOperation = newModifyOperation(ModificationType.DELETE, userDN);
      modifyOperation.run();
      if (modifyOperation.getResultCode() != ResultCode.SUCCESS)
      {
        throw new DirectoryException(modifyOperation.getResultCode(),
            ERR_STATICGROUP_REMOVE_MEMBER_UPDATE_FAILED.get(userDN, groupEntryDN, modifyOperation.getErrorMessage()));
      }

      LinkedHashSet<CompactDn> newMemberDNs = new LinkedHashSet<>(memberDNs);
      newMemberDNs.remove(compactUserDN);
      memberDNs = newMemberDNs;
      //If it is in the nested group list remove it.
      if (nestedGroups.contains(userDN))
      {
        LinkedList<DN> newNestedGroups = new LinkedList<>(nestedGroups);
        newNestedGroups.remove(userDN);
        nestedGroups = newNestedGroups;
      }
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }

  private ModifyOperation newModifyOperation(ModificationType modType, DN userDN)
  {
    Attribute attr = Attributes.create(memberAttributeType, userDN.toString());
    LinkedList<Modification> mods = newLinkedList(new Modification(modType, attr));
    Control control = new LDAPControl(OID_INTERNAL_GROUP_MEMBERSHIP_UPDATE, false);

    return new ModifyOperationBasis(getRootConnection(), nextOperationID(), nextMessageID(),
        newLinkedList(control), groupEntryDN, mods);
  }

  /** {@inheritDoc} */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("StaticGroup(");
    buffer.append(groupEntryDN);
    buffer.append(")");
  }

  /**
   * Convert the provided DN to a compact DN.
   *
   * @param dn
   *            The DN
   * @return the compact representation of the DN
   */
  private CompactDn toCompactDn(DN dn)
  {
    return Converters.from(dn).compact();
  }

  /**
   * Convert the provided compact DN to a DN.
   *
   * @param compactDn
   *            Compact representation of a DN
   * @return the regular DN
   */
  static DN fromCompactDn(CompactDn compactDn)
  {
    return Converters.to(compactDn.toDn());
  }
}

