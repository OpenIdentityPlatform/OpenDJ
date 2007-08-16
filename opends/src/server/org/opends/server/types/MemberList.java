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
package org.opends.server.types;


/**
 * This class defines a mechanism that may be used to iterate over the
 * members of a group.  It uses an interface that is similar to that
 * of {@code java.util.Iterator}, but is specific to group membership
 * and that provides the ability to throw an exception when attempting
 * to retrieve the next member (e.g., if the group contains a
 * malformed DN or references a member that doesn't exist).
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=true)
public abstract class MemberList
{
  /**
   * Indicates whether the group contains any more members.
   *
   * @return  {@code true} if the group has at least one more member,
   *          or {@code false} if not.
   */
  public abstract boolean hasMoreMembers();



  /**
   * Retrieves the DN of the next group member.
   *
   * @return  The DN of the next group member, or {@code null} if
   *          there are no more members.
   *
   * @throws  MembershipException  If a problem occurs while
   *                               attempting to retrieve the next
   *                               member DN.
   */
  public DN nextMemberDN()
         throws MembershipException
  {
    Entry e = nextMemberEntry();
    if (e == null)
    {
      return null;
    }
    else
    {
      return e.getDN();
    }
  }



  /**
   * Retrieves the entry for the next group member.
   *
   * @return  The entry for the next group member, or {@code null} if
   *          there are no more members.
   *
   * @throws  MembershipException  If a problem occurs while
   *                               attempting to retrieve the next
   *                               entry.
   */
  public abstract Entry nextMemberEntry()
         throws MembershipException;



  /**
   * Indicates that this member list is no longer required and that
   * the server may clean up any resources that may have been used in
   * the course of processing.  This method must be called if the
   * caller wishes to stop iterating across the member list before the
   * end has been reached, although it will not be necessary if the
   * call to {@code hasMoreMembers} returns {@code false}.
   */
  public abstract void close();
}

