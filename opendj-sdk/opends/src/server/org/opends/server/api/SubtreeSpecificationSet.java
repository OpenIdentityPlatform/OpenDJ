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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.api;



import java.util.AbstractSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

import org.opends.server.types.Entry;



/**
 * This class implements the {@code Set} interface for
 * {@link org.opends.server.api.SubtreeSpecification}s.
 * <p>
 * It is backed by a {@code HashSet} but provides additional
 * functionality, {@link #isWithinScope(Entry)}, for determining
 * whether or not an entry is within the scope of one or more
 * contained {@code SubtreeSpecification}s.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class SubtreeSpecificationSet
       extends AbstractSet<SubtreeSpecification>
{
  // Underlying implementation is simply a set.
  private HashSet<SubtreeSpecification> pimpl;



  /**
   * Constructs a new empty subtree specification set.
   */
  public SubtreeSpecificationSet()
  {
    this.pimpl = new HashSet<SubtreeSpecification>();
  }



  /**
   * Constructs a new subtree specification set containing the
   * elements in the specified collection.
   *
   * @param  c  The subtree specification collection whose elements
   *            are to be placed into this set.
   */
  public SubtreeSpecificationSet(
              Collection<? extends SubtreeSpecification> c)
  {
    this.pimpl = new HashSet<SubtreeSpecification>(c);
  }



  /**
   * Returns {@code true} if the specified entry is within the scope
   * of a subtree specifications contained in the set.
   *
   * @param  entry  The entry to be checked for containment.
   *
   * @return  Returns {@code true} if the set contains the specified
   *          entry.
   */
  public boolean isWithinScope(Entry entry)
  {
    for (SubtreeSpecification subtreeSpecification : pimpl)
    {
      if (subtreeSpecification.isWithinScope(entry))
      {
        return true;
      }
    }

    return false;
  }



  /**
   * Adds the provided subtree specification object to this set.
   *
   * @param  e  The subtree specification object to be added.
   *
   * @return  {@code true} if the element was added to the set, or
   *          {@code false} if the element was already contained in
   *          the set.
   */
  @Override
  public boolean add(SubtreeSpecification e)
  {
    return pimpl.add(e);
  }



  /**
   * Retrieves an iterator that may be used to step through the values
   * in this set.
   *
   * @return  An iterator that may be used to step through the values
   *          in this set.
   */
  @Override
  public Iterator<SubtreeSpecification> iterator()
  {
    return pimpl.iterator();
  }



  /**
   * Indicates whether this set contains the provided object.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  {@code true} if this set contains the provided object,
   *          or {@code false} if not.
   */
  @Override
  public boolean contains(Object o)
  {
    return pimpl.contains(o);
  }



  /**
   * Retrieves the number of elements contained in this set.
   *
   * @return  The number of elements contained in this set.
   */
  @Override
  public int size()
  {
    return pimpl.size();
  }
}

