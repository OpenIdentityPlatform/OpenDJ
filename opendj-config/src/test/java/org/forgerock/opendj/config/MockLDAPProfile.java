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
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.config;


/** A mock LDAP profile wrapper for testing purposes. */
public final class MockLDAPProfile extends LDAPProfile.Wrapper {

    /** Creates a new mock LDAP profile. */
    public MockLDAPProfile() {
        // No implementation required.
    }

    @Override
    public String getAttributeName(AbstractManagedObjectDefinition<?, ?> d, PropertyDefinition<?> pd) {

        if (d == TestParentCfgDefn.getInstance()) {
            TestParentCfgDefn td = TestParentCfgDefn.getInstance();

            if (pd == (PropertyDefinition<?>) td.getMandatoryBooleanPropertyPropertyDefinition()) {
                return "ds-cfg-enabled";
            } else if (pd == (PropertyDefinition<?>) td.getMandatoryClassPropertyPropertyDefinition()) {
                return "ds-cfg-java-class";
            } else if (pd == (PropertyDefinition<?>) td.getMandatoryReadOnlyAttributeTypePropertyPropertyDefinition()) {
                return "ds-cfg-attribute-type";
            } else if (pd == (PropertyDefinition<?>) td.getOptionalMultiValuedDNPropertyPropertyDefinition()) {
                return "ds-cfg-base-dn";
            } else {
                throw new RuntimeException("Unexpected test-parent property" + pd.getName());
            }
        } else if (d == TestChildCfgDefn.getInstance()) {
            TestChildCfgDefn td = TestChildCfgDefn.getInstance();

            if (pd == (PropertyDefinition<?>) td.getMandatoryBooleanPropertyPropertyDefinition()) {
                return "ds-cfg-enabled";
            } else if (pd == (PropertyDefinition<?>) td.getMandatoryClassPropertyPropertyDefinition()) {
                return "ds-cfg-java-class";
            } else if (pd == (PropertyDefinition<?>) td.getMandatoryReadOnlyAttributeTypePropertyPropertyDefinition()) {
                return "ds-cfg-attribute-type";
            } else if (pd == (PropertyDefinition<?>) td.getOptionalMultiValuedDNProperty1PropertyDefinition()) {
                return "ds-cfg-base-dn";
            } else if (pd == (PropertyDefinition<?>) td.getOptionalMultiValuedDNProperty2PropertyDefinition()) {
                return "ds-cfg-group-dn";
            } else if (pd.getName().equals("aggregation-property")) {
                return "ds-cfg-rotation-policy";
            } else {
                throw new RuntimeException("Unexpected test-child property" + pd.getName());
            }
        }

        // Not known.
        return null;
    }

    @Override
    public String getRelationChildRDNType(InstantiableRelationDefinition<?, ?> r) {
        if (r == TestCfg.getTestOneToManyParentRelationDefinition()
                || r == TestParentCfgDefn.getInstance().getTestChildrenRelationDefinition()) {
            return "cn";
        }
        return null;
    }

    @Override
    public String getObjectClass(AbstractManagedObjectDefinition<?, ?> d) {
        if (d == TestParentCfgDefn.getInstance()) {
            return "ds-cfg-test-parent-dummy";
        } else if (d == TestChildCfgDefn.getInstance()) {
            return "ds-cfg-test-child-dummy";
        } else {
            // Not known.
            return null;
        }
    }

    @Override
    public String getRelationRDNSequence(RelationDefinition<?, ?> r) {
        if (r == TestCfg.getTestOneToManyParentRelationDefinition()) {
            return "cn=test parents,cn=config";
        } else if (r == TestCfg.getTestOneToZeroOrOneParentRelationDefinition()) {
            return "cn=optional test parent,cn=config";
        } else if (r == TestParentCfgDefn.getInstance().getTestChildrenRelationDefinition()) {
            return "cn=test children";
        } else if (r == TestParentCfgDefn.getInstance().getOptionalTestChildRelationDefinition()) {
            return "cn=optional test child";
        } else {
            return null;
        }
    }

}
