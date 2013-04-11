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
 * Copyright 2013 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.forgerock.opendj.ldap.Attributes.emptyAttribute;
import static org.forgerock.opendj.rest2ldap.Utils.i18n;
import static org.forgerock.opendj.rest2ldap.Utils.isNullOrEmpty;
import static org.forgerock.opendj.rest2ldap.WritabilityPolicy.READ_WRITE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.Attributes;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.opendj.ldap.ModificationType;

/**
 * An abstract LDAP attribute mapper which provides a simple mapping from a JSON
 * value to a single LDAP attribute.
 */
abstract class AbstractLDAPAttributeMapper<T extends AbstractLDAPAttributeMapper<T>> extends
        AttributeMapper {
    List<Object> defaultJSONValues = emptyList();
    final AttributeDescription ldapAttributeName;
    private boolean isRequired = false;
    private boolean isSingleValued = false;
    private WritabilityPolicy writabilityPolicy = READ_WRITE;

    AbstractLDAPAttributeMapper(final AttributeDescription ldapAttributeName) {
        this.ldapAttributeName = ldapAttributeName;
    }

    /**
     * Indicates that the LDAP attribute is mandatory and must be provided
     * during create requests.
     *
     * @return This attribute mapper.
     */
    public final T isRequired() {
        this.isRequired = true;
        return getThis();
    }

    /**
     * Indicates that multi-valued LDAP attribute should be represented as a
     * single-valued JSON value, rather than an array of values.
     *
     * @return This attribute mapper.
     */
    public final T isSingleValued() {
        this.isSingleValued = true;
        return getThis();
    }

    /**
     * Indicates whether or not the LDAP attribute supports updates. The default
     * is {@link WritabilityPolicy#READ_WRITE}.
     *
     * @param policy
     *            The writability policy.
     * @return This attribute mapper.
     */
    public final T writability(final WritabilityPolicy policy) {
        this.writabilityPolicy = policy;
        return getThis();
    }

    boolean attributeIsSingleValued() {
        return isSingleValued || ldapAttributeName.getAttributeType().isSingleValue();
    }

    @Override
    void getLDAPAttributes(final Context c, final JsonPointer path, final JsonPointer subPath,
            final Set<String> ldapAttributes) {
        ldapAttributes.add(ldapAttributeName.toString());
    }

    abstract void getNewLDAPAttributes(Context c, JsonPointer path, List<Object> newValues,
            ResultHandler<Attribute> h);

    abstract T getThis();

    @Override
    void toLDAP(final Context c, final JsonPointer path, final Entry e, final JsonValue v,
            final ResultHandler<List<Modification>> h) {
        // Make following code readable.
        final boolean isUpdateRequest = e != null;
        final boolean isCreateRequest = !isUpdateRequest;

        // Get the existing LDAP attribute.
        final Attribute oldLDAPAttribute;
        if (isCreateRequest) {
            oldLDAPAttribute = emptyAttribute(ldapAttributeName);
        } else {
            final Attribute tmp = e.getAttribute(ldapAttributeName);
            oldLDAPAttribute = tmp != null ? tmp : emptyAttribute(ldapAttributeName);
        }

        if (v != null && v.isList() && attributeIsSingleValued()) {
            // Single-valued field violation.
            h.handleError(new BadRequestException(i18n(
                    "The request cannot be processed because an array of values was "
                            + "provided for the single valued field '%s'", path)));
        } else {
            final ResultHandler<Attribute> attributeHandler = new ResultHandler<Attribute>() {
                @Override
                public void handleError(final ResourceException error) {
                    h.handleError(error);
                }

                @Override
                public void handleResult(final Attribute newLDAPAttribute) {
                    /*
                     * If the attribute is read-only then handle the following
                     * cases:
                     *
                     * 1) new values are provided and they are the same as the
                     * existing values
                     *
                     * 2) no new values are provided.
                     */
                    if (isCreateRequest && !writabilityPolicy.canCreate(ldapAttributeName)
                            || isUpdateRequest && !writabilityPolicy.canWrite(ldapAttributeName)) {
                        if (newLDAPAttribute.isEmpty()
                                || (isUpdateRequest && newLDAPAttribute.equals(oldLDAPAttribute))
                                || writabilityPolicy.discardWrites()) {
                            // No change.
                            h.handleResult(Collections.<Modification> emptyList());
                        } else {
                            h.handleError(new BadRequestException(i18n(
                                    "The request cannot be processed because it attempts to modify "
                                            + "the read-only field '%s'", path)));
                        }
                    } else {
                        // Compute the changes to the attribute.
                        final List<Modification> modifications;
                        if (oldLDAPAttribute.isEmpty() && newLDAPAttribute.isEmpty()) {
                            // No change.
                            modifications = Collections.<Modification> emptyList();
                        } else if (oldLDAPAttribute.isEmpty()) {
                            // The attribute is being added.
                            modifications =
                                    singletonList(new Modification(ModificationType.REPLACE,
                                            newLDAPAttribute));
                        } else if (newLDAPAttribute.isEmpty()) {
                            /*
                             * The attribute is being deleted - this is not
                             * allowed if the attribute is required.
                             */
                            if (isRequired) {
                                h.handleError(new BadRequestException(i18n(
                                        "The request cannot be processed because it attempts to remove "
                                                + "the required field '%s'", path)));
                                return;
                            } else {
                                modifications =
                                        singletonList(new Modification(ModificationType.REPLACE,
                                                newLDAPAttribute));
                            }
                        } else {
                            /*
                             * We could do a replace, but try to save bandwidth
                             * and send diffs instead. Perform deletes first in
                             * case we don't have an appropriate normalizer:
                             * permissive add(x) followed by delete(x) is
                             * destructive, whereas delete(x) followed by add(x)
                             * is idempotent when adding/removing the same
                             * value.
                             */
                            modifications = new ArrayList<Modification>(2);

                            final Attribute deletedValues = new LinkedAttribute(oldLDAPAttribute);
                            deletedValues.removeAll(newLDAPAttribute);
                            if (!deletedValues.isEmpty()) {
                                modifications.add(new Modification(ModificationType.DELETE,
                                        deletedValues));
                            }

                            final Attribute addedValues = new LinkedAttribute(newLDAPAttribute);
                            addedValues.removeAll(oldLDAPAttribute);
                            if (!addedValues.isEmpty()) {
                                modifications.add(new Modification(ModificationType.ADD,
                                        addedValues));
                            }
                        }
                        h.handleResult(modifications);
                    }
                }
            };

            final List<Object> newValues = asList(v);
            if (newValues.isEmpty()) {
                // Skip sub-class implementation if there are no values.
                attributeHandler.handleResult(Attributes.emptyAttribute(ldapAttributeName));
            } else {
                getNewLDAPAttributes(c, path, asList(v), attributeHandler);
            }
        }
    }

    private List<Object> asList(final JsonValue v) {
        if (isNullOrEmpty(v)) {
            return defaultJSONValues;
        } else if (v.isList()) {
            return v.asList();
        } else {
            return singletonList(v.getObject());
        }
    }
}
