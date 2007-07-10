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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.api;



import java.util.List;

import org.opends.server.admin.std.server.GroupImplementationCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.MemberList;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;



/**
 * This class defines the set of methods that must be implemented by a
 * Directory Server group.  It is expected that there will be a number
 * of different types of groups (e.g., legacy static and dynamic
 * groups, as well as enhanced groups and virtual static groups).  The
 * following operations may be performed on an OpenDS group:
 * <UL>
 *   <LI>Determining whether a given user is a member of this
 *       group</LI>
 *   <LI>Determining the set of members for this group, optionally
 *       filtered based on some set of criteria.</LI>
 *   <LI>Retrieving or updating the set of nested groups for this
 *       group, if the underlying group type supports nesting).</LI>
 *   <LI>Updating the set of members for this group, if the underlying
 *       group type provides the ability to explicitly add or remove
 *       members.</LI>
 * </UL>
 *
 * @param  <T>  The type of configuration handled by this group
 *              implementation.
 */
public abstract class Group<T extends GroupImplementationCfg>
{
  /**
   * Initializes a "shell" instance of this group implementation that
   * may be used to identify and instantiate instances of this type of
   * group in the directory data.
   *
   * @param  configuration  The configuration for this group
   *                        implementation.
   *
   * @throws  ConfigException  If there is a problem with the provided
   *                           configuration entry.
   *
   * @throws  InitializationException  If a problem occurs while
   *                                   attempting to initialize this
   *                                   group implementation that is
   *                                   not related to the server
   *                                   configuration.
   */
  public abstract void initializeGroupImplementation(T configuration)
         throws ConfigException, InitializationException;



  /**
   * Indicates whether the provided configuration is acceptable for
   * this group implementation.  It should be possible to call this
   * method on an uninitialized group implementation instance in order
   * to determine whether the group implementation would be able to
   * use the provided configuration.
   * <BR><BR>
   * Note that implementations which use a subclass of the provided
   * configuration class will likely need to cast the configuration
   * to the appropriate subclass type.
   *
   * @param  configuration        The group implementation
   *                              configuration for which to make the
   *                              determination.
   * @param  unacceptableReasons  A list that may be used to hold the
   *                              reasons that the provided
   *                              configuration is not acceptable.
   *
   * @return  {@code true} if the provided configuration is acceptable
   *          for this group implementation, or {@code false} if not.
   */
  public boolean isConfigurationAcceptable(
                      GroupImplementationCfg configuration,
                      List<String> unacceptableReasons)
  {
    // This default implementation does not perform any special
    // validation.  It should be overridden by group implementations
    // that wish to perform more detailed validation.
    return true;
  }



  /**
   * Performs any necessary finalization that may be needed whenever
   * this group implementation is taken out of service within the
   * Directory Server (e.g., if it is disabled or the server is
   * shutting down).
   */
  public void finalizeGroupImplementation()
  {
    // No implementation is required by default.
  }



  /**
   * Creates a new group of this type based on the definition
   * contained in the provided entry.  This method must be designed so
   * that it may be invoked on the "shell" instance created using the
   * default constructor and initialized with the
   * {@code initializeGroupImplementation} method.
   *
   * @param  groupEntry  The entry containing the definition for the
   *                     group to be created.
   *
   * @return  The group instance created from the definition in the
   *          provided entry.
   *
   * @throws  DirectoryException  If a problem occurs while trying to
   *                              create the group instance.
   */
  public abstract Group newInstance(Entry groupEntry)
         throws DirectoryException;



  /**
   * Retrieves a search filter that may be used to identify entries
   * containing definitions for groups of this type in the Directory
   * Server.  This method must be designed so that it may be invoked
   * on the "shell" instance created using the default constructor and
   * initialized with the {@code initializeGroupImplementation}
   * method.
   *
   * @return  A search filter that may be used to identify entries
   *          containing definitions for groups of this type in the
   *          Directory Server.
   *
   * @throws  DirectoryException  If a problem occurs while trying to
   *                              locate all of the applicable group
   *                              definition entries.
   */
  public abstract SearchFilter getGroupDefinitionFilter()
         throws DirectoryException;



  /**
   * Indicates whether the provided entry contains a valid definition
   * for this type of group.
   *
   * @param  entry  The entry for which to make the determination.
   *
   * @return  {@code true} if the provided entry does contain a valid
   *          definition for this type of group, or {@code false} if
   *          it does not.
   */
  public abstract boolean isGroupDefinition(Entry entry);



  /**
   * Retrieves the DN of the entry that contains the definition for
   * this group.
   *
   * @return  The DN of the entry that contains the definition for
   *          this group.
   */
  public abstract DN getGroupDN();



  /**
   * Indicates whether this group supports nesting other groups, such
   * that the members of the nested groups will also be considered
   * members of this group.
   *
   * @return  {@code true} if this group supports nesting other
   *          groups, or {@code false} if it does not.
   */
  public abstract boolean supportsNestedGroups();



  /**
   * Retrieves a list of the DNs of any nested groups whose members
   * should be considered members of this group.
   *
   * @return  A list of the DNs of any nested groups whose members
   *          should be considered members of this group.
   */
  public abstract List<DN> getNestedGroupDNs();



  /**
   * Attempts to add the provided group DN as a nested group within
   * this group.  The change should be committed to persistent storage
   * through an internal operation.
   *
   * @param  nestedGroupDN  The DN of the group that should be added
   *                        to the set of nested groups for this
   *                        group.
   *
   * @throws  UnsupportedOperationException  If this group does not
   *                                         support nesting.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to nest the provided group DN.
   */
  public abstract void addNestedGroup(DN nestedGroupDN)
         throws UnsupportedOperationException, DirectoryException;



  /**
   * Attempts to remove the provided group as a nested group within
   * this group.  The change should be committed to persistent storage
   * through an internal operation.
   *
   * @param  nestedGroupDN  The DN of the group that should be removed
   *                        from the set of nested groups for this
   *                        group.
   *
   * @throws  UnsupportedOperationException  If this group does not
   *                                         support nesting.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to nest the provided group DN.
   */
  public abstract void removeNestedGroup(DN nestedGroupDN)
         throws UnsupportedOperationException, DirectoryException;



  /**
   * Indicates whether the user with the specified DN is a member of
   * this group.  Note that this is a point-in-time determination and
   * the caller must not cache the result.
   *
   * @param  userDN  The DN of the user for which to make the
   *                 determination.
   *
   * @return  {@code true} if the specified user is currently a member
   *          of this group, or {@code false} if not.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to make the determination.
   */
  public abstract boolean isMember(DN userDN)
         throws DirectoryException;



  /**
   * Indicates whether the user described by the provided user entry
   * is a member of this group.  Note that this is a point-in-time
   * determination and the caller must not cache the result.
   *
   * @param  userEntry  The entry for the user for which to make the
   *                    determination.
   *
   * @return  {@code true} if the specified user is currently a member
   *          of this group, or {@code false} if not.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to make the determination.
   */
  public abstract boolean isMember(Entry userEntry)
         throws DirectoryException;



  /**
   * Retrieves an iterator that may be used to cursor through the
   * entries of the members contained in this group.  Note that this
   * is a point-in-time determination, and the caller must not cache
   * the result.
   *
   * @return  An iterator that may be used to cursor through the
   *          entries of the members contained in this group.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to retrieve the set of members.
   */
  public MemberList getMembers()
         throws DirectoryException
  {
    return getMembers(null, null, null);
  }



  /**
   * Retrieves an iterator that may be used to cursor through the
   * entries of the members contained in this group.  It may
   * optionally retrieve a subset of the member entries based on a
   * given set of criteria.  Note that this is a point-in-time
   * determination, and the caller must not cache the result.
   *
   * @param  baseDN  The base DN that should be used when determining
   *                 whether a given entry will be returned.  If this
   *                 is {@code null}, then all entries will be
   *                 considered in the scope of the criteria.
   * @param  scope   The scope that should be used when determining
   *                 whether a given entry will be returned.  It must
   *                 not be {@code null} if the provided base DN is
   *                 not {@code null}.  The scope will be ignored if
   *                 no base DN is provided.
   * @param  filter  The filter that should be used when determining
   *                 whether a given entry will be returned.  If this
   *                 is {@code null}, then any entry in the scope of
   *                 the criteria will be included in the results.
   *
   * @return  An iterator that may be used to cursor through the
   *          entries of the members contained in this group.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to retrieve the set of members.
   */
  public abstract MemberList getMembers(DN baseDN, SearchScope scope,
                                        SearchFilter filter)
         throws DirectoryException;



  /**
   * Indicates whether it is possible to alter the member list for
   * this group (e.g., in order to add members to the group or remove
   * members from it).
   *
   * @return  {@code true} if it is possible to add members to this
   *          group, or {@code false} if not.
   */
  public abstract boolean mayAlterMemberList();



  /**
   * Attempts to add the provided user as a member of this group.  The
   * change should be committed to persistent storage through an
   * internal operation.
   *
   * @param  userEntry  The entry for the user to be added as a member
   *                    of this group.
   *
   * @throws  UnsupportedOperationException  If this group does not
   *                                         support altering the
   *                                         member list.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to add the provided user as a member
   *                              of this group.
   */
  public abstract void addMember(Entry userEntry)
         throws UnsupportedOperationException, DirectoryException;



  /**
   * Attempts to remove the specified user as a member of this group.
   * The change should be committed to persistent storage through an
   * internal operation.
   *
   * @param  userDN  The DN of the user to remove as a member of this
   *                 group.
   *
   * @throws  UnsupportedOperationException  If this group does not
   *                                         support altering the
   *                                         member list.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to remove the provided user as a
   *                              member of this group.
   */
  public abstract void removeMember(DN userDN)
         throws UnsupportedOperationException, DirectoryException;



  /**
   * Retrieves a string representation of this group.
   *
   * @return  A string representation of this group.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this group to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the string representation
   *                 should be appended.
   */
  public abstract void toString(StringBuilder buffer);
}

