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

package org.opends.server.util.args;

import org.opends.messages.Message;

import java.util.List;
import java.util.LinkedList;
import java.util.Collections;
import java.util.Iterator;

/**
 * Class for organizing options into logical groups when arguement
 * usage is printed.  To use an argument group, create an instance
 * and use {@link org.opends.server.util.args.ArgumentParser
 * #addArgument(Argument, ArgumentGroup)} when adding arguments for
 * to the parser.
 */
public class ArgumentGroup implements Comparable<ArgumentGroup> {

  // Description for this group of arguments
  private Message description = null;

  // List of arguments belonging to this group
  private List<Argument> args = null;

  // Governs groups position within usage statement
  private Integer priority;

  /**
   * Creates a parameterized instance.
   *
   * @param description for options in this group that is printed before
   *        argument descriptions in usage output
   * @param priority number governing the position of this group within
   *        the usage statement.  Groups with higher priority values appear
   *        before groups with lower priority.
   */
  public ArgumentGroup(Message description, int priority) {
    this.description = description;
    this.priority = priority;
    this.args = new LinkedList<Argument>();
  }

  /**
   * Gets the description for this group of arguments.
   *
   * @return description for this argument group
   */
  public Message getDescription() {
    return this.description;
  }

  /**
   * Sets the description for this group of arguments.
   *
   * @param description for this argument group
   */
  public void setDescription(Message description) {
    this.description = description;
  }

  /**
   * Gets the list of arguments associated with this group.
   *
   * @return list of associated arguments
   */
  List<Argument> getArguments() {
    return Collections.unmodifiableList(args);
  }

  /**
   * {@inheritDoc}
   */
  public int compareTo(ArgumentGroup o)
  {
    // Groups with higher priority numbers appear before
    // those with lower priority in the usage output
    return -1 * priority.compareTo(o.priority);
  }

  /**
   * Indicates whether this group contains any members.
   *
   * @return boolean where true means this group contains members
   */
  boolean containsArguments()
  {
    return this.args.size() > 0;
  }


  /**
   * Indicates whether this group contains any non-hidden members.
   *
   * @return boolean where true means this group contains non-hidden members
   */
  boolean containsNonHiddenArguments()
  {
    for (Argument arg : args)
    {
      if (!arg.isHidden())
      {
        return true;
      }
    }
    return false;
  }


  /**
   * Adds an argument to this group.
   *
   * @param arg to add
   * @return boolean where true indicates the add was successful
   */
  boolean addArgument(Argument arg) {
    boolean success = false;
    if (arg != null) {
      Character newShort = arg.getShortIdentifier();
      String newLong = arg.getLongIdentifier();

      // See if there is already an argument in this group that the
      // new argument should replace
      for (Iterator<Argument> it = this.args.iterator(); it.hasNext();)
      {
        Argument a = it.next();
        if (newShort != null && newShort.equals(a.getShortIdentifier()) ||
                newLong != null && newLong.equals(a.getLongIdentifier())) {
          it.remove();
          break;
        }
      }

      success = this.args.add(arg);
    }
    return success;
  }

  /**
   * Removes an argument from this group.
   *
   * @param arg to remove
   * @return boolean where true indicates the remove was successful
   */
  boolean removeArgument(Argument arg) {
    return this.args.remove(arg);
  }

}
