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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.types;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.core.DirectoryServer;

import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.types.SubEntry.CollectiveConflictBehavior.*;
import static org.opends.server.util.ServerConstants.*;

/**
 * This class represents RFC 3672 subentries and RFC 3671
 * collective attribute subentries objects.
 */
public class SubEntry {
  /**
   * Defines the set of permissible values for the conflict behavior.
   * Specifies the behavior that the server is to exhibit for entries
   * that already contain one or more real values for the associated
   * collective attribute.
   */
  public enum CollectiveConflictBehavior {
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

    /** String representation of the value. */
    private final String name;

    /**
     * Private constructor.
     * @param name for this conflict behavior.
     */
    private CollectiveConflictBehavior(String name)
    {
      this.name = name;
    }

    @Override
    public String toString()
    {
      return name;
    }
  }

  /** The lowercased name of the "collectiveConflictBehavior" attribute type. */
  private static final String ATTR_COLLECTIVE_CONFLICT_BEHAVIOR_LC = "collectiveconflictbehavior";
  /** The lowercased name of the "inheritFromDNAttribute" attribute type. */
  private static final String ATTR_INHERIT_COLLECTIVE_FROM_DN_LC = "inheritfromdnattribute";
  /** The lowercased name of the "inheritFromRDNAttribute" attribute type. */
  private static final String ATTR_INHERIT_COLLECTIVE_FROM_RDN_LC = "inheritfromrdnattribute";
  /** The lowercased name of the "inheritFromRDNType" attribute type. */
  private static final String ATTR_INHERIT_COLLECTIVE_FROM_RDN_TYPE_LC = "inheritfromrdntype";
  /** The lowercased name of the "inheritFromBaseRDN" attribute type. */
  private static final String ATTR_INHERIT_COLLECTIVE_FROM_BASE_LC = "inheritfrombaserdn";
  /** The lowercased name of the "inheritAttribute" attribute type. */
  private static final String ATTR_INHERIT_COLLECTIVE_ATTR_LC = "inheritattribute";
  /** Attribute option to mark attributes collective. */
  private static final String ATTR_OPTION_COLLECTIVE = "collective";

  /** Entry object. */
  private final Entry entry;

  /** Subtree specification. */
  private final SubtreeSpecification subTreeSpec;

  /** Collective subentry flag. */
  private final boolean isCollective;
  /** Inherited collective subentry flag. */
  private final boolean isInheritedCollective;
  /** Inherited collective from DN subentry flag. */
  private final boolean isInheritedFromDNCollective;
  /** Inherited collective from RDN subentry flag. */
  private final boolean isInheritedFromRDNCollective;

  /** Inherited collective DN attribute type. */
  private AttributeType inheritFromDNType;
  /** Inherited collective RDN attribute type. */
  private AttributeType inheritFromRDNAttrType;
  /** Inherited collective RDN type attribute type. */
  private AttributeType inheritFromRDNType;
  /** Inherited collective RDN attribute value. */
  private ByteString inheritFromRDNAttrValue;
  /** Inherited collective from DN value. */
  private ByteString inheritFromDNAttrValue;

  /** Inherited collective from base DN. */
  private DN inheritFromBaseDN;

  /** Collective attributes. */
  private final List<Attribute> collectiveAttributes = new ArrayList<>();

  /** Conflict behavior. */
  private CollectiveConflictBehavior conflictBehavior = REAL_OVERRIDES_VIRTUAL;

  /**
   * Constructs a subentry object from a given entry object.
   * @param  entry LDAP subentry to construct from.
   * @throws DirectoryException if there is a problem with
   *         constructing a subentry from a given entry.
   */
  public SubEntry(Entry entry) throws DirectoryException
  {
    this.entry = entry;

    this.subTreeSpec = buildSubTreeSpecification(entry);
    this.isCollective = entry.isCollectiveAttributeSubentry();

    this.isInheritedCollective = entry.isInheritedCollectiveAttributeSubentry();
    if (this.isInheritedCollective)
    {
      this.isInheritedFromDNCollective = entry.isInheritedFromDNCollectiveAttributeSubentry();
      this.isInheritedFromRDNCollective = entry.isInheritedFromRDNCollectiveAttributeSubentry();
    }
    else
    {
      this.isInheritedFromDNCollective = false;
      this.isInheritedFromRDNCollective = false;
    }

    // Process collective attributes.
    if (this.isCollective)
    {
      List<Attribute> subAttrList = entry.getAttributes();
      for (Attribute subAttr : subAttrList)
      {
        AttributeType attrType = subAttr.getAttributeDescription().getAttributeType();
        if (attrType.isCollective())
        {
          this.collectiveAttributes.add(new CollectiveVirtualAttribute(subAttr));
        }
        else if (subAttr.getAttributeDescription().hasOption(ATTR_OPTION_COLLECTIVE))
        {
          AttributeBuilder builder = new AttributeBuilder(subAttr.getAttributeDescription().getAttributeType());
          builder.addAll(subAttr);
          for (String option : subAttr.getAttributeDescription().getOptions())
          {
            if (!ATTR_OPTION_COLLECTIVE.equals(option))
            {
              builder.setOption(option);
            }
          }
          Attribute attr = builder.toAttribute();
          this.collectiveAttributes.add(new CollectiveVirtualAttribute(attr));
        }
      }
    }

    // Process inherited collective attributes.
    if (this.isInheritedCollective)
    {
      if (this.isInheritedFromDNCollective)
      {
        for (Attribute attr : entry.getAttribute(ATTR_INHERIT_COLLECTIVE_FROM_DN_LC))
        {
          for (ByteString value : attr)
          {
            this.inheritFromDNType = DirectoryServer.getSchema().getAttributeType(value.toString());
            this.inheritFromDNAttrValue = value;
            break;
          }
        }
      }

      if (this.isInheritedFromRDNCollective)
      {
        for (Attribute attr : entry.getAttribute(ATTR_INHERIT_COLLECTIVE_FROM_RDN_LC))
        {
          for (ByteString value : attr)
          {
            this.inheritFromRDNAttrType = DirectoryServer.getSchema().getAttributeType(value.toString());
            this.inheritFromRDNAttrValue = value;
            break;
          }
        }
        for (Attribute attr : entry.getAttribute(ATTR_INHERIT_COLLECTIVE_FROM_RDN_TYPE_LC))
        {
          for (ByteString value : attr)
          {
            this.inheritFromRDNType = DirectoryServer.getSchema().getAttributeType(value.toString());
            break;
          }
        }
        for (Attribute attr : entry.getAttribute(ATTR_INHERIT_COLLECTIVE_FROM_BASE_LC))
        {
          for (ByteString value : attr)
          {
            // Has to have a parent since subentry itself
            // cannot be a suffix entry within the server.
            this.inheritFromBaseDN = getDN().parent().child(DN.valueOf(value));
            break;
          }
        }
      }

      for (Attribute attr : entry.getAttribute(ATTR_INHERIT_COLLECTIVE_ATTR_LC))
      {
        for (ByteString value : attr)
        {
          Attribute collectiveAttr = Attributes.empty(value.toString());
          this.collectiveAttributes.add(new CollectiveVirtualAttribute(collectiveAttr));
        }
      }
    }

    // Establish collective attribute conflict behavior.
    if (this.isCollective || this.isInheritedCollective)
    {
      for (Attribute attr : entry.getAttribute(ATTR_COLLECTIVE_CONFLICT_BEHAVIOR_LC))
      {
        for (ByteString value : attr)
        {
          for (CollectiveConflictBehavior behavior : CollectiveConflictBehavior.values())
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

  private SubtreeSpecification buildSubTreeSpecification(Entry entry) throws DirectoryException
  {
    String specString = null;
    boolean isValidSpec = true;
    AttributeType specAttrType = DirectoryServer.getSchema().getAttributeType(ATTR_SUBTREE_SPEC_LC);
    for (Attribute attr : entry.getAttribute(specAttrType))
    {
      for (ByteString value : attr)
      {
        specString = value.toString();
        try
        {
          SubtreeSpecification subTreeSpec = SubtreeSpecification.valueOf(entry.getName().parent(), specString);
          if (subTreeSpec != null)
          {
            return subTreeSpec;
          }
          isValidSpec = true;
        }
        catch (DirectoryException ignored)
        {
          isValidSpec = false;
        }
      }
    }

    // Check that the subtree spec is flagged as valid. If it is not
    // that means all parsers have failed and it is invalid syntax.
    if (!isValidSpec)
    {
      LocalizableMessage message = ERR_ATTR_SYNTAX_SUBTREE_SPECIFICATION_INVALID.get(specString);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }

    // Subentry has to have a subtree specification.
    // There is none for some reason eg this could be
    // old Draft based ldapSubEntry so create a dummy.
    return new SubtreeSpecification(entry.getName().parent(), null, -1, -1, null, null, null);
  }

  /**
   * Retrieves the distinguished name for this subentry.
   * @return  The distinguished name for this subentry.
   */
  public final DN getDN()
  {
    return this.entry.getName();
  }

  /**
   * Getter to retrieve the actual entry object for this subentry.
   * @return entry object for this subentry.
   */
  public final Entry getEntry()
  {
    return this.entry;
  }

  /**
   * Indicates whether this subentry is a collective attribute subentry.
   * @return {@code true} if collective, {@code false} otherwise.
   */
  public boolean isCollective()
  {
    return this.isCollective;
  }

  /**
   * Indicates whether this subentry is inherited collective attribute subentry.
   * @return {@code true} if inherited collective, {@code false} otherwise.
   */
  public boolean isInheritedCollective()
  {
    return this.isInheritedCollective;
  }

  /**
   * Indicates whether this subentry is inherited from DN collective attribute subentry.
   * @return {@code true} if inherited from DN collective, {@code false} otherwise.
   */
  public boolean isInheritedFromDNCollective()
  {
    return this.isInheritedFromDNCollective;
  }

  /**
   * Indicates whether this subentry is inherited from RDN collective attribute subentry.
   * @return {@code true} if inherited from RDN collective, {@code false} otherwise.
   */
  public boolean isInheritedFromRDNCollective()
  {
    return this.isInheritedFromRDNCollective;
  }

  /**
   * Getter to retrieve inheritFromDNAttribute type for inherited collective attribute subentry.
   * @return Type of inheritFromDNAttribute, or {@code null} if there is none.
   */
  public AttributeType getInheritFromDNType()
  {
    return this.inheritFromDNType;
  }

  /**
   * Getter to retrieve inheritFromRDNAttribute type for inherited collective attribute subentry.
   * @return Type of inheritFromRDNAttribute, or {@code null} if there is none.
   */
  public AttributeType getInheritFromRDNAttrType()
  {
    return this.inheritFromRDNAttrType;
  }

  /**
   * Getter to retrieve inheritFromRDNAttribute value for inherited collective attribute subentry.
   * @return ByteString of inheritFromRDNAttribute, or {@code null} if there is none.
   */
  public ByteString getInheritFromRDNAttrValue()
  {
    return this.inheritFromRDNAttrValue;
  }

  /**
   * Getter to retrieve RDN type of inheritFromRDNType for inherited collective attribute subentry.
   * @return RDN Type of inheritFromRDNAttribute, or {@code null} if there is none.
   */
  public AttributeType getInheritFromRDNType()
  {
    return this.inheritFromRDNType;
  }

  /**
   * Getter to retrieve inheritFromDNAttribute value for inherited collective attribute subentry.
   * @return ByteString of inheritFromDNAttribute, or {@code null} if there is none.
   */
  public ByteString getInheritFromDNAttrValue()
  {
    return this.inheritFromDNAttrValue;
  }

  /**
   * Getter to retrieve inheritFromBaseRDN DN for inherited collective attribute subentry.
   * @return DN of inheritFromBaseRDN, or {@code null} if there is none.
   */
  public DN getInheritFromBaseDN()
  {
    return this.inheritFromBaseDN;
  }

  /**
   * Getter for subentry subtree specification.
   * @return subtree specification for this subentry.
   */
  public SubtreeSpecification getSubTreeSpecification()
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
   * Getter for collective conflict behavior defined for this collective attributes subentry.
   * @return conflict behavior for this collective attributes subentry.
   */
  public CollectiveConflictBehavior getConflictBehavior()
  {
    return this.conflictBehavior;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "(" + this.entry.getName() + ")";
  }
}
