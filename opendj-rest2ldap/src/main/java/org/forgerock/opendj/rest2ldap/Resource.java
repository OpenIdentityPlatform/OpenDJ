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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 *
 */
package org.forgerock.opendj.rest2ldap;

import static java.util.Arrays.asList;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.ERR_ABSTRACT_TYPE_IN_CREATE;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.ERR_MISSING_TYPE_PROPERTY_IN_CREATE;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.ERR_UNRECOGNIZED_RESOURCE_SUPER_TYPE;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.ERR_UNRECOGNIZED_TYPE_IN_CREATE;
import static org.forgerock.opendj.rest2ldap.Utils.newBadRequestException;
import static org.forgerock.util.Utils.joinAsString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.Router;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LinkedAttribute;

/**
 * Defines the characteristics of a resource, including its properties, inheritance, and sub-resources.
 */
public final class Resource {
    /** The resource ID. */
    private final String id;
    /** {@code true} if only sub-types of this resource can be created. */
    private boolean isAbstract;
    /** The ID of the super-type of this resource, may be {@code null}. */
    private String superTypeId;
    /** The LDAP object classes associated with this resource. */
    private final Attribute objectClasses = new LinkedAttribute("objectClass");
    /** The possibly empty set of sub-resources. */
    private final Set<SubResource> subResources = new LinkedHashSet<>();
    /** The set of property mappers associated with this resource, excluding inherited properties. */
    private final Map<String, PropertyMapper> declaredProperties = new LinkedHashMap<>();
    /** The set of property mappers associated with this resource, including inherited properties. */
    private final Map<String, PropertyMapper> allProperties = new LinkedHashMap<>();
    /**
     * A JSON pointer to the primitive JSON property that will be used to convey type information. May be {@code
     * null} if the type property is defined in a super type or if this resource does not have any sub-types.
     */
    private JsonPointer resourceTypeProperty;
    /** Set to {@code true} once this Resource has been built. */
    private boolean isBuilt = false;
    /** The resolved super-type. */
    private Resource superType;
    /** The resolved sub-resources (only immediate children). */
    private final Set<Resource> subTypes = new LinkedHashSet<>();
    /** The property mapper which will map all properties for this resource including inherited properties. */
    private final ObjectPropertyMapper propertyMapper = new ObjectPropertyMapper();
    /** Routes requests to sub-resources. */
    private final Router subResourceRouter = new Router();
    private volatile Boolean hasSubTypesWithSubResources = null;
    /** The set of actions supported by this resource and its sub-types. */
    private final Set<Action> supportedActions = new HashSet<>();

    Resource(final String id) {
        this.id = id;
    }

    /**
     * Returns the resource ID of this resource.
     *
     * @return The resource ID of this resource.
     */
    @Override
    public String toString() {
        return id;
    }

    /**
     * Returns {@code true} if the provided parameter is a {@code Resource} having the same resource ID as this
     * resource.
     *
     * @param o
     *         The object to compare.
     * @return {@code true} if the provided parameter is a {@code Resource} having the same resource ID as this
     * resource.
     */
    @Override
    public boolean equals(final Object o) {
        return this == o || (o instanceof Resource && id.equals(((Resource) o).id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /**
     * Specifies the resource ID of the resource which is a super-type of this resource. This resource will inherit
     * the properties and sub-resources of the super-type, and may optionally override them.
     *
     * @param resourceId
     *         The resource ID of the resource which is a super-type of this resource, or {@code null} if there is no
     *         super-type.
     * @return A reference to this object.
     */
    public Resource superType(final String resourceId) {
        this.superTypeId = resourceId;
        return this;
    }

    /**
     * Specifies whether this resource is an abstract type and therefore cannot be created. Only non-abstract
     * sub-types can be created.
     *
     * @param isAbstract
     *         {@code true} if this resource is abstract.
     * @return A reference to this object.
     */
    public Resource isAbstract(final boolean isAbstract) {
        this.isAbstract = isAbstract;
        return this;
    }

    /**
     * Specifies a mapping for a property contained in this JSON resource. Properties are inherited and sub-types may
     * override them. Properties are optional: a resource that does not have any properties cannot be created, read,
     * or modified, and may only be used for accessing sub-resources. These resources usually represent API
     * "endpoints".
     *
     * @param name
     *         The name of the JSON property to be mapped.
     * @param mapper
     *         The property mapper responsible for mapping the JSON property to LDAP attribute(s).
     * @return A reference to this object.
     */
    public Resource property(final String name, final PropertyMapper mapper) {
        declaredProperties.put(name, mapper);
        return this;
    }

    /**
     * Specifies whether all LDAP user attributes should be mapped by default using the default schema based mapping
     * rules. Individual attributes can be excluded using {@link #excludedDefaultUserAttributes} in order to prevent
     * attributes with explicit mappings being mapped twice.
     *
     * @param include {@code true} if all LDAP user attributes be mapped by default.
     * @return A reference to this object.
     */
    public Resource includeAllUserAttributesByDefault(final boolean include) {
        propertyMapper.includeAllUserAttributesByDefault(include);
        return this;
    }

    /**
     * Specifies zero or more user attributes which will be excluded from the default user attribute mappings when
     * enabled using {@link #includeAllUserAttributesByDefault}. Attributes which have explicit mappings should be
     * excluded in order to prevent duplication.
     *
     * @param attributeNames The list of attributes to be excluded.
     * @return A reference to this object.
     */
    public Resource excludedDefaultUserAttributes(final String... attributeNames) {
        return excludedDefaultUserAttributes(Arrays.asList(attributeNames));
    }

    /**
     * Specifies zero or more user attributes which will be excluded from the default user attribute mappings when
     * enabled using {@link #includeAllUserAttributesByDefault}. Attributes which have explicit mappings should be
     * excluded in order to prevent duplication.
     *
     * @param attributeNames The list of attributes to be excluded.
     * @return A reference to this object.
     */
    public Resource excludedDefaultUserAttributes(final Collection<String> attributeNames) {
        propertyMapper.excludedDefaultUserAttributes(attributeNames);
        return this;
    }

    /**
     * Specifies the name of the JSON property which contains the resource's type, whose value is the
     * resource ID. The resource type property is inherited by sub-types and must be available to any resources
     * referenced from {@link SubResource sub-resources}.
     *
     * @param resourceTypeProperty
     *         The name of the JSON property which contains the resource's type, or {@code null} if this resource does
     *         not have a resource type property or if it should be inherited from a super-type.
     * @return A reference to this object.
     */
    public Resource resourceTypeProperty(final JsonPointer resourceTypeProperty) {
        this.resourceTypeProperty = resourceTypeProperty;
        return this;
    }

    /**
     * Specifies an LDAP object class which is to be associated with this resource. Multiple object classes may be
     * specified. The object classes are used for determining the type of resource being accessed during all requests
     * other than create. Object classes are inherited by sub-types and must be defined for any resources that are
     * non-abstract and which can be created.
     *
     * @param objectClass
     *         An LDAP object class associated with this resource's LDAP representation.
     * @return A reference to this object.
     */
    public Resource objectClass(final String objectClass) {
        this.objectClasses.add(objectClass);
        return this;
    }

    /**
     * Specifies LDAP object classes which are to be associated with this resource. Multiple object classes may be
     * specified. The object classes are used for determining the type of resource being accessed during all requests
     * other than create. Object classes are inherited by sub-types and must be defined for any resources that are
     * non-abstract and which can be created.
     *
     * @param objectClasses
     *         The LDAP object classes associated with this resource's LDAP representation.
     * @return A reference to this object.
     */
    public Resource objectClasses(final String... objectClasses) {
        this.objectClasses.add((Object[]) objectClasses);
        return this;
    }

    /**
     * Registers an action which should be supported by this resource. By default, no actions are supported.
     *
     * @param action
     *         The action supported by this resource.
     * @return A reference to this object.
     */
    public Resource supportedAction(final Action action) {
        this.supportedActions.add(action);
        return this;
    }

    /**
     * Registers zero or more actions which should be supported by this resource. By default, no actions are supported.
     *
     * @param actions
     *         The actions supported by this resource.
     * @return A reference to this object.
     */
    public Resource supportedActions(final Action... actions) {
        this.supportedActions.addAll(Arrays.asList(actions));
        return this;
    }

    /**
     * Specifies a parent-child relationship with another resource. Sub-resources are inherited by sub-types and may
     * be overridden.
     *
     * @param subResource
     *         The sub-resource definition.
     * @return A reference to this object.
     */
    public Resource subResource(final SubResource subResource) {
        this.subResources.add(subResource);
        return this;
    }

    /**
     * Specifies a parent-child relationship with zero or more resources. Sub-resources are inherited by sub-types and
     * may be overridden.
     *
     * @param subResources
     *         The sub-resource definitions.
     * @return A reference to this object.
     */
    public Resource subResources(final SubResource... subResources) {
        this.subResources.addAll(asList(subResources));
        return this;
    }

    boolean hasSupportedAction(final Action action) {
        return supportedActions.contains(action);
    }

    boolean hasSubTypes() {
        return !subTypes.isEmpty();
    }

    boolean mayHaveSubResources() {
        return !subResources.isEmpty() || hasSubTypesWithSubResources();
    }

    boolean hasSubTypesWithSubResources() {
        if (hasSubTypesWithSubResources == null) {
            for (final Resource subType : subTypes) {
                if (!subType.subResources.isEmpty() || subType.hasSubTypesWithSubResources()) {
                    hasSubTypesWithSubResources = true;
                    return true;
                }
            }
            hasSubTypesWithSubResources = false;
        }
        return hasSubTypesWithSubResources;
    }

    Set<Resource> getSubTypes() {
        return subTypes;
    }

    Resource resolveSubTypeFromJson(final JsonValue content) throws ResourceException {
        if (!hasSubTypes()) {
            // The resource type is implied because this resource does not have sub-types. In particular, resources
            // are not required to have type information if they don't have sub-types.
            return this;
        }
        final JsonValue jsonType = content.get(resourceTypeProperty);
        if (jsonType == null || !jsonType.isString()) {
            throw newBadRequestException(ERR_MISSING_TYPE_PROPERTY_IN_CREATE.get(resourceTypeProperty));
        }
        final String type = jsonType.asString();
        final Resource subType = resolveSubTypeFromString(type);
        if (subType == null) {
            throw newBadRequestException(ERR_UNRECOGNIZED_TYPE_IN_CREATE.get(type, getAllowedResourceTypes()));
        }
        if (subType.isAbstract) {
            throw newBadRequestException(ERR_ABSTRACT_TYPE_IN_CREATE.get(type, getAllowedResourceTypes()));
        }
        return subType;
    }

    private String getAllowedResourceTypes() {
        final List<String> allowedTypes = new ArrayList<>();
        getAllowedResourceTypes(allowedTypes);
        return joinAsString(", ", allowedTypes);
    }

    private void getAllowedResourceTypes(final List<String> allowedTypes) {
        if (!isAbstract) {
            allowedTypes.add(id);
        }
        for (final Resource subType : subTypes) {
            subType.getAllowedResourceTypes(allowedTypes);
        }
    }

    Resource resolveSubTypeFromString(final String type) {
        if (id.equalsIgnoreCase(type)) {
            return this;
        }
        for (final Resource subType : subTypes) {
            final Resource resolvedSubType = subType.resolveSubTypeFromString(type);
            if (resolvedSubType != null) {
                return resolvedSubType;
            }
        }
        return null;
    }

    Resource resolveSubTypeFromObjectClasses(final Entry entry) {
        if (!hasSubTypes()) {
            // This resource does not have sub-types.
            return this;
        }
        final Attribute objectClassesFromEntry = entry.getAttribute("objectClass");
        final Resource subType = resolveSubTypeFromObjectClasses(objectClassesFromEntry);
        if (subType == null) {
            // Best effort.
            return this;
        }
        return subType;
    }

    private Resource resolveSubTypeFromObjectClasses(final Attribute objectClassesFromEntry) {
        if (!objectClassesFromEntry.containsAll(objectClasses)) {
            return null;
        }
        // This resource is a potential match, but sub-types may be better.
        for (final Resource subType : subTypes) {
            final Resource resolvedSubType = subType.resolveSubTypeFromObjectClasses(objectClassesFromEntry);
            if (resolvedSubType != null) {
                return resolvedSubType;
            }
        }
        return this;
    }

    Attribute getObjectClassAttribute() {
        return objectClasses;
    }

    RequestHandler getSubResourceRouter() {
        return subResourceRouter;
    }

    String getResourceId() {
        return id;
    }

    void build(final Rest2Ldap rest2Ldap) {
        // Prevent re-entrant calls.
        if (isBuilt) {
            return;
        }
        isBuilt = true;

        if (superTypeId != null) {
            superType = rest2Ldap.getResource(superTypeId);
            if (superType == null) {
                throw new LocalizedIllegalArgumentException(ERR_UNRECOGNIZED_RESOURCE_SUPER_TYPE.get(id, superTypeId));
            }
            // Inherit content from super-type.
            superType.build(rest2Ldap);
            superType.subTypes.add(this);
            if (resourceTypeProperty == null) {
                resourceTypeProperty = superType.resourceTypeProperty;
            }
            objectClasses.addAll(superType.objectClasses);
            subResourceRouter.addAllRoutes(superType.subResourceRouter);
            allProperties.putAll(superType.allProperties);
        }
        allProperties.putAll(declaredProperties);
        for (final Map.Entry<String, PropertyMapper> property : allProperties.entrySet()) {
            propertyMapper.property(property.getKey(), property.getValue());
        }
        for (final SubResource subResource : subResources) {
            subResource.build(rest2Ldap, id);
            subResource.addRoutes(subResourceRouter);
        }
    }

    PropertyMapper getPropertyMapper() {
        return propertyMapper;
    }
}
