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
package org.opends.server.admin;



/**
 * A mock LDAP profile wrapper for testing purposes.
 *
 * NOTE:  The seemingly unnecessary casts throughout this class are
 * required to work around a bug in JDK versions prior to 1.5.0_08.
 */
public final class MockLDAPProfile extends LDAPProfile.Wrapper {

  /**
   * Creates a new mock LDAP profile.
   */
  public MockLDAPProfile() {
    // No implementation required.
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getAttributeName(AbstractManagedObjectDefinition<?, ?> d,
      PropertyDefinition<?> pd) {

    // These casts throughout are required to work around a bug in JDK versions prior to 1.5.0_08.
    if (d == (AbstractManagedObjectDefinition<?, ?>)TestParentCfgDefn.getInstance()) {
      TestParentCfgDefn td = TestParentCfgDefn.getInstance();

      if (pd == (PropertyDefinition<?>)td.getMandatoryBooleanPropertyPropertyDefinition()) {
        return "ds-cfg-virtual-attribute-enabled";
      } else if (pd == (PropertyDefinition<?>)td.getMandatoryClassPropertyPropertyDefinition()) {
        return "ds-cfg-virtual-attribute-class";
      } else if (pd == (PropertyDefinition<?>)td
          .getMandatoryReadOnlyAttributeTypePropertyPropertyDefinition()) {
        return "ds-cfg-virtual-attribute-type";
      } else if (pd == (PropertyDefinition<?>)td.getOptionalMultiValuedDNPropertyPropertyDefinition()) {
        return "ds-cfg-virtual-attribute-base-dn";
      } else {
        throw new RuntimeException("Unexpected test-parent property"
            + pd.getName());
      }
    } else if (d == (AbstractManagedObjectDefinition<?, ?>)TestChildCfgDefn.getInstance()) {
      TestChildCfgDefn td = TestChildCfgDefn.getInstance();

      if (pd == (PropertyDefinition<?>)td.getMandatoryBooleanPropertyPropertyDefinition()) {
        return "ds-cfg-virtual-attribute-enabled";
      } else if (pd == (PropertyDefinition<?>)td.getMandatoryClassPropertyPropertyDefinition()) {
        return "ds-cfg-virtual-attribute-class";
      } else if (pd == (PropertyDefinition<?>)td
          .getMandatoryReadOnlyAttributeTypePropertyPropertyDefinition()) {
        return "ds-cfg-virtual-attribute-type";
      } else if (pd == (PropertyDefinition<?>)td.getOptionalMultiValuedDNProperty1PropertyDefinition()) {
        return "ds-cfg-virtual-attribute-base-dn";
      } else if (pd == (PropertyDefinition<?>)td.getOptionalMultiValuedDNProperty2PropertyDefinition()) {
        return "ds-cfg-virtual-attribute-group-dn";
      } else if (pd.getName().equals("aggregation-property")) {
        return "ds-cfg-backend-base-dn";
      } else {
        throw new RuntimeException("Unexpected test-child property"
            + pd.getName());
      }
    }

    // Not known.
    return null;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String getInstantiableRelationChildRDNType(
      InstantiableRelationDefinition<?, ?> r) {
    if (r == TestCfg.getTestOneToManyParentRelationDefinition()
        || r == TestParentCfgDefn.getInstance()
            .getTestChildrenRelationDefinition()) {
      return "cn";
    } else {
      return null;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String getObjectClass(AbstractManagedObjectDefinition<?, ?> d) {
    // These casts throughout are required to work around a bug in JDK versions prior to 1.5.0_08.
    if (d == (AbstractManagedObjectDefinition<?, ?>)TestParentCfgDefn.getInstance()) {
      return "ds-cfg-test-parent-dummy";
    } else if (d == (AbstractManagedObjectDefinition<?, ?>)TestChildCfgDefn.getInstance()) {
      return "ds-cfg-test-child-dummy";
    } else {
      // Not known.
      return null;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String getRelationRDNSequence(RelationDefinition<?, ?> r) {
    if (r == TestCfg.getTestOneToManyParentRelationDefinition()) {
      return "cn=test parents,cn=config";
    } else if (r == TestCfg.getTestOneToZeroOrOneParentRelationDefinition()) {
      return "cn=optional test parent,cn=config";
    } else if (r == TestParentCfgDefn.getInstance()
        .getTestChildrenRelationDefinition()) {
      return "cn=test children";
    } else if (r == TestParentCfgDefn.getInstance()
        .getOptionalTestChildRelationDefinition()) {
      return "cn=optional test child";
    } else {
      return null;
    }
  }

}
