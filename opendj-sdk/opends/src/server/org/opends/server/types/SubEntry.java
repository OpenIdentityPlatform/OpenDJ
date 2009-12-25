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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.server.types;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.LinkedHashSet;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.RFC3672SubtreeSpecification;

import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;

/**
 * This class represents RFC 3672 subentries and RFC 3671
 * collective attribute subentries objects.
 */
public class SubEntry {
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * Defines the set of permissable values for the conflict behavior.
   * Specifies the behavior that the server is to exhibit for entries
   * that already contain one or more real values for the associated
   * collective attribute.
   */
  public static enum CollectiveConflictBehavior {
    /**
     * Indicates that the virtual attribute provider is to preserve
     * any real values contained in the entry and merge them with the
     * set of generated virtual values so that both the real and
     * virtual values are used.
     */
    MERGE_REAL_AND_VIRTUAL("merge-real-and-virtual"),

    /**
     * Indicates that any real values contained in the entry are
     * preserved and used, and virtual values are not generated.
     */
    REAL_OVERRIDES_VIRTUAL("real-overrides-virtual"),

    /**
     * Indicates that the virtual attribute provider suppresses any
     * real values contained in the entry and generates virtual values
     * and uses them.
     */
    VIRTUAL_OVERRIDES_REAL("virtual-overrides-real");

    // String representation of the value.
    private final String name;

    /**
     * Private constructor.
     * @param name for this conflict behavior.
     */
    private CollectiveConflictBehavior(String name)
    {
      this.name = name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
      return name;
    }
  }

  /**
   * The name of the "collectiveConflictBehavior" attribute type,
   * formatted in all lowercase characters.
   */
  public static final String ATTR_COLLECTIVE_CONFLICT_BEHAVIOR =
          "collectiveconflictbehavior";

  // Attribute option to mark attributes collective.
  private static final String ATTR_OPTION_COLLECTIVE =
          "collective";

  // Entry object.
  private Entry entry;

  // Subtree specification.
  private RFC3672SubtreeSpecification subTreeSpec;

  // Collective subentry flag.
  private boolean isCollective = false;

  // Collective attributes.
  private List<Attribute> collectiveAttributes;

  // Conflict behavior.
  private CollectiveConflictBehavior conflictBehavior =
          CollectiveConflictBehavior.REAL_OVERRIDES_VIRTUAL;

  /**
   * Constructs a subentry object from a given entry object.
   * @param  entry LDAP subentry to construct from.
   * @throws DirectoryException if there is a problem with
   *         constructing a subentry from a given entry.
   */
  public SubEntry(Entry entry) throws DirectoryException
  {
    // Entry object.
    this.entry = entry;

    // Process subtree specification.
    this.subTreeSpec = null;
    AttributeType specAttrType = DirectoryServer.getAttributeType(
            ATTR_SUBTREE_SPEC_LC, true);
    List<Attribute> specAttrList =
            entry.getAttribute(specAttrType);
    for (Attribute attr : specAttrList)
    {
      for (AttributeValue value : attr)
      {
        this.subTreeSpec = RFC3672SubtreeSpecification.valueOf(
                entry.getDN().getParent(), value.toString());
        break;
      }
      if (this.subTreeSpec != null)
      {
        break;
      }
    }
    // Subentry has to to have a subtree specification.
    if (this.subTreeSpec == null)
    {
      // There is none for some reason so create a dummy.
      this.subTreeSpec = new RFC3672SubtreeSpecification(
                entry.getDN().getParent(), null, -1, -1,
                null, null, null);
    }

    // Determine if this subentry is collective attribute subentry.
    this.isCollective = entry.isCollectiveAttributeSubentry();

    // Process collective attributes.
    this.collectiveAttributes = new ArrayList<Attribute>();
    if (this.isCollective)
    {
      List<Attribute> subAttrList = entry.getAttributes();
      for (Attribute subAttr : subAttrList)
      {
        AttributeType attrType = subAttr.getAttributeType();
        if (attrType.isCollective())
        {
          CollectiveVirtualAttribute collectiveAttr =
                  new CollectiveVirtualAttribute(subAttr);
          this.collectiveAttributes.add(collectiveAttr);
        }
        else if (subAttr.hasOption(ATTR_OPTION_COLLECTIVE))
        {
          AttributeBuilder builder = new AttributeBuilder(
                  subAttr.getAttributeType());
          builder.addAll(subAttr);
          Set<String> options = new LinkedHashSet<String>(
                  subAttr.getOptions());
          options.remove(ATTR_OPTION_COLLECTIVE);
          builder.setOptions(options);
          Attribute attr = builder.toAttribute();
          CollectiveVirtualAttribute collectiveAttr =
                  new CollectiveVirtualAttribute(attr);
          this.collectiveAttributes.add(collectiveAttr);
        }
      }
      // Conflict behavior.
      List<Attribute> attrList = entry.getAttribute(
              ATTR_COLLECTIVE_CONFLICT_BEHAVIOR);
      if ((attrList != null) && !attrList.isEmpty())
      {
        for (Attribute attr : attrList)
        {
          for (AttributeValue value : attr)
          {
            for (CollectiveConflictBehavior behavior :
              CollectiveConflictBehavior.values())
            {
              if (behavior.toString().equals(value.toString()))
              {
                this.conflictBehavior = behavior;
                break;
              }
            }
          }
        }
      }
    }
  }

  /**
   * Retrieves the distinguished name for this subentry.
   * @return  The distinguished name for this subentry.
   */
  public DN getDN()
  {
    return this.entry.getDN();
  }

  /**
   * Getter to retrieve the actual entry object
   * for this subentry.
   * @return entry object for this subentry.
   */
  public Entry getEntry()
  {
    return this.entry;
  }

  /**
   * Indicates whether or not this subentry is
   * a collective attribute subentry.
   * @return <code>true</code> if collective,
   *         <code>false</code> otherwise.
   */
  public boolean isCollective()
  {
    return this.isCollective;
  }

  /**
   * Getter for subentry subtree specification.
   * @return subtree specification for this subentry.
   */
  public RFC3672SubtreeSpecification getSubTreeSpecification()
  {
    return this.subTreeSpec;
  }

  /**
   * Getter for collective attributes contained within this subentry.
   * @return collective attributes contained within this subentry.
   */
  public List<Attribute> getCollectiveAttributes()
  {
    return this.collectiveAttributes;
  }

  /**
   * Getter for collective conflict behavior defined for this
   * collective attributes subentry.
   * @return conflict behavior for this collective attributes
   *         subentry.
   */
  public CollectiveConflictBehavior getConflictBehavior()
  {
    return this.conflictBehavior;
  }
}
