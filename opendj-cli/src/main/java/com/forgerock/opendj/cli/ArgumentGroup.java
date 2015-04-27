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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions copyright 2012-2015 ForgeRock AS.
 */
package com.forgerock.opendj.cli;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;

/**
 * Class for organizing options into logical groups when argument usage is printed. To use an argument group, create an
 * instance and use {@code ArgumentParser#addArgument(Argument, ArgumentGroup)} when adding arguments for to the parser.
 */
public final class ArgumentGroup implements Comparable<ArgumentGroup> {

    /** Description for this group of arguments. */
    private LocalizableMessage description;
    /** List of arguments belonging to this group. */
    private final List<Argument> args = new LinkedList<>();
    /** Governs groups position within usage statement. */
    private final Integer priority;

    /**
     * Creates a parameterized instance.
     *
     * @param description
     *            for options in this group that is printed before argument descriptions in usage output
     * @param priority
     *            number governing the position of this group within the usage statement. Groups with higher priority
     *            values appear before groups with lower priority.
     */
    public ArgumentGroup(final LocalizableMessage description, final int priority) {
        this.description = description;
        this.priority = priority;
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(final ArgumentGroup o) {
        // Groups with higher priority numbers appear before
        // those with lower priority in the usage output
        return -1 * priority.compareTo(o.priority);
    }

    /**
     * Adds an argument to this group.
     *
     * @param arg
     *            to add
     * @return boolean where true indicates the add was successful
     */
    public boolean addArgument(final Argument arg) {
        if (arg != null) {
            final Character newShort = arg.getShortIdentifier();
            final String newLong = arg.getLongIdentifier();

            // See if there is already an argument in this group that the
            // new argument should replace
            for (final Iterator<Argument> it = this.args.iterator(); it.hasNext();) {
                final Argument a = it.next();
                if ((newShort != null && newShort.equals(a.getShortIdentifier()))
                        || (newLong != null && newLong.equals(a.getLongIdentifier()))) {
                    it.remove();
                    break;
                }
            }

            return this.args.add(arg);
        }
        return false;
    }

    /**
     * Indicates whether this group contains any members.
     *
     * @return boolean where true means this group contains members
     */
    boolean containsArguments() {
        return this.args.size() > 0;
    }

    /**
     * Indicates whether this group contains any non-hidden members.
     *
     * @return boolean where true means this group contains non-hidden members
     */
    boolean containsNonHiddenArguments() {
        for (final Argument arg : args) {
            if (!arg.isHidden()) {
                return true;
            }
        }
        return false;
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
     * Gets the description for this group of arguments.
     *
     * @return description for this argument group
     */
    LocalizableMessage getDescription() {
        return this.description;
    }

    /**
     * Removes an argument from this group.
     *
     * @param arg
     *            to remove
     * @return boolean where true indicates the remove was successful
     */
    boolean removeArgument(final Argument arg) {
        return this.args.remove(arg);
    }

    /**
     * Sets the description for this group of arguments.
     *
     * @param description
     *            for this argument group
     */
    void setDescription(final LocalizableMessage description) {
        this.description = description;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "(description=" + description + ")";
    }
}
