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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.*;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.forgerock.opendj.ldap.Attributes.emptyAttribute;
import static org.forgerock.opendj.rest2ldap.Rest2Ldap.asResourceException;
import static org.forgerock.opendj.rest2ldap.Utils.isNullOrEmpty;
import static org.forgerock.opendj.rest2ldap.Utils.newBadRequestException;
import static org.forgerock.opendj.rest2ldap.Utils.newNotSupportedException;
import static org.forgerock.opendj.rest2ldap.WritabilityPolicy.READ_WRITE;
import static org.forgerock.util.promise.Promises.newExceptionPromise;
import static org.forgerock.util.promise.Promises.newResultPromise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.PatchOperation;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;

/**
 * An abstract LDAP property mapper which provides a simple mapping from a JSON
 * value to a single LDAP attribute.
 */
abstract class AbstractLdapPropertyMapper<T extends AbstractLdapPropertyMapper<T>> extends PropertyMapper {
    List<Object> defaultJsonValues = emptyList();
    final AttributeDescription ldapAttributeName;
    private boolean isRequired;
    private boolean isMultiValued;
    private WritabilityPolicy writabilityPolicy = READ_WRITE;

    AbstractLdapPropertyMapper(final AttributeDescription ldapAttributeName) {
        this.ldapAttributeName = ldapAttributeName;
    }

    /**
     * Indicates that the LDAP attribute is mandatory and must be provided during create requests.
     *
     * @param isRequired {@code true} if this property is required.
     * @return This property mapper.
     */
    public final T isRequired(final boolean isRequired) {
        this.isRequired = isRequired;
        return getThis();
    }

    /**
     * Indicates that the LDAP attribute is multi-valued and should be represented in JSON using an array of values.
     *
     * @param isMultiValued {@code true} if this property is multi-valued.
     * @return This property mapper.
     */
    public final T isMultiValued(final boolean isMultiValued) {
        this.isMultiValued = isMultiValued;
        return getThis();
    }

    /**
     * Indicates whether the LDAP attribute supports updates. The default is {@link WritabilityPolicy#READ_WRITE}.
     *
     * @param policy
     *            The writability policy.
     * @return This property mapper.
     */
    public final T writability(final WritabilityPolicy policy) {
        this.writabilityPolicy = policy;
        return getThis();
    }

    boolean attributeIsSingleValued() {
        return !isMultiValued || ldapAttributeName.getAttributeType().isSingleValue();
    }

    @Override
    Promise<List<Attribute>, ResourceException> create(final Connection connection,
                                                       final Resource resource, final JsonPointer path,
                                                       final JsonValue v) {
        return getNewLdapAttributes(connection, resource, path, v).then(
            new Function<Attribute, List<Attribute>, ResourceException>() {
                @Override
                public List<Attribute> apply(Attribute newLDAPAttribute) throws ResourceException {
                    if (!writabilityPolicy.canCreate(ldapAttributeName)) {
                        if (!newLDAPAttribute.isEmpty() && !writabilityPolicy.discardWrites()) {
                            throw newBadRequestException(ERR_CREATION_READ_ONLY_FIELD.get(path));
                        }
                        return Collections.emptyList();
                    } else if (newLDAPAttribute.isEmpty()) {
                        if (isRequired) {
                            throw newBadRequestException(ERR_MISSING_REQUIRED_FIELD.get(path));
                        }
                        return Collections.emptyList();
                    }
                    return singletonList(newLDAPAttribute);
                }
            });
    }

    @Override
    void getLdapAttributes(final JsonPointer path, final JsonPointer subPath, final Set<String> ldapAttributes) {
        ldapAttributes.add(ldapAttributeName.toString());
    }

    abstract Promise<Attribute, ResourceException> getNewLdapAttributes(Connection connection, Resource resource,
                                                                        JsonPointer path, List<Object> newValues);

    abstract T getThis();

    @Override
    Promise<List<Modification>, ResourceException> patch(final Connection connection, final Resource resource,
                                                         final JsonPointer path, final PatchOperation operation) {
        try {
            final JsonPointer field = operation.getField();
            final JsonValue v = operation.getValue();

            // Reject any attempts to patch this field if it is read-only, even if it is configured to discard writes.
            if (!writabilityPolicy.canWrite(ldapAttributeName)) {
                throw newBadRequestException(ERR_MODIFY_READ_ONLY_FIELD.get("patch", path));
            }

            switch (field.size()) {
            case 0:
                /*
                 * The patch operation targets the entire mapping. If this
                 * mapping is multi-valued, then the patch value must be a list
                 * of values to be added, removed, or replaced. If it is
                 * single-valued then the patch value must not be a list.
                 */
                if (attributeIsSingleValued()) {
                    if (v.isList()) {
                        // Single-valued field violation.
                        throw newBadRequestException(ERR_ARRAY_FOR_SINGLE_VALUED_FIELD.get(path));
                    }
                } else if (!v.isList() && !operation.isIncrement()
                        && !(v.isNull() && (operation.isReplace() || operation.isRemove()))) {
                    // Multi-valued field violation.
                    throw newBadRequestException(ERR_NO_ARRAY_FOR_MULTI_VALUED_FIELD.get(path));
                }
                break;
            case 1:
                /*
                 * The patch operation targets a sub-field. If the sub-field
                 * name is a number then it is an attempt to patch a single
                 * value at a specific index. Rest2LDAP cannot support indexed
                 * updates because LDAP attribute values are unordered. We will,
                 * however, support the special index "-" indicating that a
                 * value should be appended.
                 */
                final String fieldName = field.get(0);
                if (fieldName.equals("-") && operation.isAdd()) {
                    // Append a single value.
                    if (attributeIsSingleValued()) {
                        throw newBadRequestException(ERR_PATCH_APPEND_IN_SINGLE_VALUED_FIELD.get(path));
                    } else if (v.isList()) {
                        throw newBadRequestException(
                                ERR_PATCH_INDEXED_APPEND_TO_MULTI_VALUED_FIELD.get(path.child(fieldName)));
                    }
                } else if (fieldName.matches("[0-9]+")) {
                    // Array index - not allowed.
                    throw newNotSupportedException(ERR_PATCH_INDEXED_OPERATION.get(path.child(fieldName)));
                } else {
                    throw newBadRequestException(ERR_UNRECOGNIZED_FIELD.get(path.child(fieldName)));
                }
                break;
            default:
                /*
                 * The patch operation targets the child of a sub-field. This is
                 * not possible for a LDAP property mapper.
                 */
                throw newBadRequestException(ERR_UNRECOGNIZED_FIELD.get(path.child(field.get(0))));
            }

            // Check that the values are compatible with the type of patch operation.
            final List<Object> newValues = asList(v, Collections.emptyList());
            final ModificationType modType;
            if (operation.isAdd()) {
                /*
                 * Use a replace for single valued fields in case the underlying
                 * LDAP attribute is multi-valued, or the attribute already
                 * contains a value.
                 */
                modType = attributeIsSingleValued() ? ModificationType.REPLACE : ModificationType.ADD;
                if (newValues.isEmpty()) {
                    throw newBadRequestException(ERR_PATCH_ADD_NO_VALUE_FOR_FIELD.get(path.child(field.get(0))));
                }
            } else if (operation.isRemove()) {
                modType = ModificationType.DELETE;
            } else if (operation.isReplace()) {
                modType = ModificationType.REPLACE;
            } else if (operation.isIncrement()) {
                modType = ModificationType.INCREMENT;
            } else {
                throw newNotSupportedException(ERR_PATCH_UNSUPPORTED_OPERATION.get(operation.getOperation()));
            }

            // Create the modification.
            if (newValues.isEmpty()) {
                // Deleting the attribute.
                if (isRequired) {
                    return Promises.<List<Modification>, ResourceException> newExceptionPromise(
                            newBadRequestException(ERR_REMOVE_REQUIRED_FIELD.get("update", path)));
                } else {
                    return newResultPromise(
                        singletonList(new Modification(modType, emptyAttribute(ldapAttributeName))));
                }
            } else {
                return getNewLdapAttributes(connection, resource, path, newValues)
                        .then(new Function<Attribute, List<Modification>, ResourceException>() {
                            @Override
                            public List<Modification> apply(final Attribute value) {
                                return singletonList(new Modification(modType, value));
                            }
                        });
            }
        } catch (final RuntimeException e) {
            return asResourceException(e).asPromise();
        } catch (final ResourceException e) {
            return newExceptionPromise(e);
        }
    }

    @Override
    Promise<List<Modification>, ResourceException> update(final Connection connection, final Resource resource,
                                                          final JsonPointer path, final Entry e, final JsonValue v) {
        return getNewLdapAttributes(connection, resource, path, v).then(
            new Function<Attribute, List<Modification>, ResourceException>() {
                @Override
                public List<Modification> apply(final Attribute newLDAPAttribute) throws ResourceException {
                    // Get the existing LDAP attribute.
                    final Attribute tmp = e.getAttribute(ldapAttributeName);
                    final Attribute oldLDAPAttribute = tmp != null ? tmp : emptyAttribute(ldapAttributeName);
                    /*
                     * If the attribute is read-only then handle the following cases:
                     * 1) new values are provided and they are the same as the existing values
                     * 2) no new values are provided.
                     */
                    if (!writabilityPolicy.canWrite(ldapAttributeName)) {
                        if (newLDAPAttribute.isEmpty()
                                || newLDAPAttribute.equals(oldLDAPAttribute)
                                || writabilityPolicy.discardWrites()) {
                            // No change.
                            return Collections.emptyList();
                        }
                        throw newBadRequestException(ERR_MODIFY_READ_ONLY_FIELD.get("update", path));
                    }

                    if (oldLDAPAttribute.isEmpty() && newLDAPAttribute.isEmpty()) {
                        // No change.
                        return Collections.emptyList();
                    } else if (oldLDAPAttribute.isEmpty()) {
                        // The attribute is being added.
                        return singletonList(new Modification(ModificationType.REPLACE, newLDAPAttribute));
                    } else if (newLDAPAttribute.isEmpty()) {
                        // The attribute is being deleted - this is not allowed if the attribute is required.
                        if (isRequired) {
                            throw newBadRequestException(ERR_REMOVE_REQUIRED_FIELD.get("update", path));
                        }
                        return singletonList(new Modification(ModificationType.REPLACE, newLDAPAttribute));
                    } else {
                        /*
                         * We could do a replace, but try to save bandwidth and send diffs instead.
                         * Perform deletes first in case we don't have an appropriate normalizer:
                         * permissive add(x) followed by delete(x) is destructive, whereas
                         * delete(x) followed by add(x) is idempotent when adding/removing the same value.
                         */
                        final List<Modification> modifications = new ArrayList<>(2);

                        final Attribute deletedValues = new LinkedAttribute(oldLDAPAttribute);
                        deletedValues.removeAll(newLDAPAttribute);
                        if (!deletedValues.isEmpty()) {
                            modifications.add(new Modification(ModificationType.DELETE, deletedValues));
                        }

                        final Attribute addedValues = new LinkedAttribute(newLDAPAttribute);
                        addedValues.removeAll(oldLDAPAttribute);
                        if (!addedValues.isEmpty()) {
                            modifications.add(new Modification(ModificationType.ADD, addedValues));
                        }
                        return modifications;
                    }
                }
            });
    }

    private List<Object> asList(final JsonValue v, final List<Object> defaultValues) {
        if (isNullOrEmpty(v)) {
            return defaultValues;
        } else if (v.isList()) {
            return v.asList();
        } else {
            return singletonList(v.getObject());
        }
    }

    private void checkSchema(final JsonPointer path, final JsonValue v) throws BadRequestException {
        if (attributeIsSingleValued()) {
            if (v != null && v.isList()) {
                // Single-valued field violation.
                throw newBadRequestException(ERR_ARRAY_FOR_SINGLE_VALUED_FIELD.get(path));
            }
        } else if (v != null && !v.isList()) {
            // Multi-valued field violation.
            throw newBadRequestException(ERR_NO_ARRAY_FOR_MULTI_VALUED_FIELD.get(path));
        }
    }

    private Promise<Attribute, ResourceException> getNewLdapAttributes(final Connection connection,
                                                                       final Resource resource, final JsonPointer path,
                                                                       final JsonValue v) {
        try {
            // Ensure that the value is of the correct type.
            checkSchema(path, v);
            final List<Object> newValues = asList(v, defaultJsonValues);
            if (newValues.isEmpty()) {
                // Skip sub-class implementation if there are no values.
                return newResultPromise(emptyAttribute(ldapAttributeName));
            } else {
                return getNewLdapAttributes(connection, resource, path, newValues);
            }
        } catch (final Exception e) {
            return asResourceException(e).asPromise();
        }
    }

}
