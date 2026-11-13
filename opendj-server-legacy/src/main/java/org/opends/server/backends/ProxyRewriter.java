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
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.backends;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.forgerock.opendj.ldap.AVA;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.FilterVisitor;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.AssertionRequestControl;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.PostReadRequestControl;
import org.forgerock.opendj.ldap.controls.PostReadResponseControl;
import org.forgerock.opendj.ldap.controls.PreReadRequestControl;
import org.forgerock.opendj.ldap.controls.PreReadResponseControl;
import org.forgerock.opendj.ldap.controls.SimplePagedResultsControl;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.Syntax;

/** Immutable mapping between the client and remote directory namespaces. */
final class ProxyRewriter {
    static final class RewriteException extends RuntimeException {
        final ResultCode resultCode;

        RewriteException(ResultCode resultCode, String message) {
            super(message);
            this.resultCode = resultCode;
        }
    }

    private final DN clientBase;
    private final DN remoteBase;
    private final Schema schema;
    private final Map<String, String> toRemote;
    private final Map<String, String> toClient;

    ProxyRewriter(DN clientBase, DN remoteBase, Collection<String> mappings, Schema schema) {
        if (remoteBase != null && clientBase == null) {
            throw new IllegalArgumentException("A single proxy base DN is required when remote-base-dn is set");
        }
        this.clientBase = clientBase;
        this.remoteBase = remoteBase;
        this.schema = schema;
        final Map<String, String> forward = new HashMap<>();
        final Map<String, String> reverse = new HashMap<>();
        if (mappings != null) {
            for (String mapping : mappings) {
                final String[] names = mapping.split("=", -1);
                if (names.length != 2 || !isAttributeName(names[0]) || !isAttributeName(names[1])) {
                    throw new IllegalArgumentException("Invalid attribute mapping: " + mapping);
                }
                final String source = names[0].trim();
                final String target = names[1].trim();
                if (forward.putIfAbsent(key(source), target) != null || reverse.putIfAbsent(key(target), source) != null) {
                    throw new IllegalArgumentException("Attribute mappings must be one-to-one: " + mapping);
                }
            }
        }
        this.toRemote = Collections.unmodifiableMap(forward);
        this.toClient = Collections.unmodifiableMap(reverse);
    }

    private static boolean isAttributeName(String name) {
        return name.trim().matches("(?:[A-Za-z][A-Za-z0-9-]*|[0-9]+(?:\\.[0-9]+)+)");
    }

    private String key(String name) {
        return schema.hasAttributeType(name) ? schema.getAttributeType(name).getOID().toLowerCase(Locale.ROOT)
                : name.toLowerCase(Locale.ROOT);
    }

    boolean isActive() {
        return remoteBase != null || !toRemote.isEmpty();
    }

    String attribute(String description, boolean forward) {
        if (toRemote.isEmpty()) {
            return description;
        }
        final int separator = description.indexOf(';');
        final String name = separator < 0 ? description : description.substring(0, separator);
        final String replacement = (forward ? toRemote : toClient).get(key(name));
        return replacement == null ? description : replacement + (separator < 0 ? "" : description.substring(separator));
    }

    DN dn(DN dn, boolean forward) {
        if (dn == null || remoteBase == null) {
            return dn;
        }
        final DN source = forward ? clientBase : remoteBase;
        final DN target = forward ? remoteBase : clientBase;
        if (!dn.isSubordinateOrEqualTo(source)) {
            return dn;
        }
        DN rewritten = target;
        for (int i = dn.size() - source.size() - 1; i >= 0; i--) {
            rewritten = rewritten.child(rdn(dn.rdn(i), forward));
        }
        return rewritten;
    }

    RDN rdn(RDN rdn, boolean forward) {
        if (rdn == null || remoteBase == null || toRemote.isEmpty()) {
            return rdn;
        }
        final List<AVA> values = new ArrayList<>(rdn.size());
        for (AVA ava : rdn) {
            final String name = attribute(ava.getAttributeName(), forward);
            values.add(new AVA(schema.getAttributeType(name), name, ava.getAttributeValue()));
        }
        return new RDN(values);
    }

    private boolean isDnAttribute(String name) {
        if (name == null) {
            return false;
        }
        final String type = name.split(";", 2)[0];
        if (!schema.hasAttributeType(type)) {
            return false;
        }
        final Syntax syntax = schema.getAttributeType(type).getSyntax();
        return syntax != null && syntax.getOID().equals(CoreSchema.getDNSyntax().getOID());
    }

    ByteString value(String name, ByteString value, boolean forward) {
        if (remoteBase == null || (!isDnAttribute(name) && !isDnAttribute(attribute(name, forward)))) {
            return value;
        }
        return dnValue(value, forward);
    }

    private ByteString dnValue(ByteString value, boolean forward) {
        if (remoteBase == null) {
            return value;
        }
        try {
            final DN original = DN.valueOf(value.toString(), schema);
            final DN mapped = dn(original, forward);
            return mapped.equals(original) ? value : ByteString.valueOfUtf8(mapped.toString());
        } catch (IllegalArgumentException e) {
            // Preserve invalid values so the authoritative server can report the syntax error.
            return value;
        }
    }

    Attribute attribute(Attribute attribute, boolean forward) {
        if (!isActive()) {
            return attribute;
        }
        final String name = attribute.getAttributeDescriptionAsString();
        final AttributeDescription description = AttributeDescription.valueOf(attribute(name, forward), schema);
        final LinkedAttribute rewritten = new LinkedAttribute(description);
        for (ByteString value : attribute) {
            rewritten.add(value(name, value, forward));
        }
        return rewritten;
    }

    Entry entry(Entry entry, boolean forward) {
        if (!isActive()) {
            return entry;
        }
        final Entry rewritten = new LinkedHashMapEntry(dn(entry.getName(), forward));
        for (Attribute attribute : entry.getAllAttributes()) {
            final Attribute mapped = attribute(attribute, forward);
            if (rewritten.getAttribute(mapped.getAttributeDescription()) != null) {
                throw new RewriteException(ResultCode.CONSTRAINT_VIOLATION,
                        "Attribute mapping produces duplicate attribute " + mapped.getAttributeDescription());
            }
            rewritten.addAttribute(mapped);
        }
        return rewritten;
    }

    private List<String> attributes(List<String> names, boolean forward) {
        final List<String> result = new ArrayList<>(names.size());
        for (String name : names) {
            result.add(attribute(name, forward));
        }
        return result;
    }

    Control requestControl(Control control) {
        if (!isActive()) {
            return control;
        }
        try {
            final DecodeOptions options = new DecodeOptions().setSchema(schema);
            if (AssertionRequestControl.OID.equals(control.getOID())) {
                final AssertionRequestControl assertion = AssertionRequestControl.DECODER.decodeControl(control, options);
                return AssertionRequestControl.newControl(control.isCritical(), filter(assertion.getFilter()));
            }
            if (PreReadRequestControl.OID.equals(control.getOID())) {
                final PreReadRequestControl preRead = PreReadRequestControl.DECODER.decodeControl(control, options);
                return PreReadRequestControl.newControl(control.isCritical(), attributes(preRead.getAttributes(), true));
            }
            if (PostReadRequestControl.OID.equals(control.getOID())) {
                final PostReadRequestControl postRead = PostReadRequestControl.DECODER.decodeControl(control, options);
                return PostReadRequestControl.newControl(control.isCritical(), attributes(postRead.getAttributes(), true));
            }
            if (SimplePagedResultsControl.OID.equals(control.getOID())) {
                return control;
            }
            if (control.isCritical()) {
                throw new RewriteException(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
                        "Cannot safely rewrite critical control " + control.getOID());
            }
            return null;
        } catch (DecodeException e) {
            throw new RewriteException(ResultCode.PROTOCOL_ERROR, "Cannot decode control " + control.getOID());
        }
    }

    Control responseControl(Control control) {
        if (!isActive()) {
            return control;
        }
        try {
            final DecodeOptions options = new DecodeOptions().setSchema(schema);
            if (PreReadResponseControl.OID.equals(control.getOID())) {
                final PreReadResponseControl preRead = PreReadResponseControl.DECODER.decodeControl(control, options);
                return PreReadResponseControl.newControl(entry(preRead.getEntry(), false));
            }
            if (PostReadResponseControl.OID.equals(control.getOID())) {
                final PostReadResponseControl postRead = PostReadResponseControl.DECODER.decodeControl(control, options);
                return PostReadResponseControl.newControl(entry(postRead.getEntry(), false));
            }
            if (SimplePagedResultsControl.OID.equals(control.getOID())) {
                return control;
            }
            if (control.isCritical()) {
                throw new RewriteException(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
                        "Cannot safely rewrite critical response control " + control.getOID());
            }
            return null;
        } catch (DecodeException e) {
            throw new RewriteException(ResultCode.PROTOCOL_ERROR, "Cannot decode response control " + control.getOID());
        }
    }

    Filter filter(Filter filter) {
        if (!isActive()) {
            return filter;
        }
        return filter.accept(new FilterVisitor<Filter, Void>() {
            private List<Filter> rewrite(List<Filter> filters) {
                final List<Filter> result = new ArrayList<>(filters.size());
                for (Filter child : filters) {
                    result.add(child.accept(this, null));
                }
                return result;
            }

            @Override public Filter visitAndFilter(Void p, List<Filter> children) { return Filter.and(rewrite(children)); }
            @Override public Filter visitOrFilter(Void p, List<Filter> children) { return Filter.or(rewrite(children)); }
            @Override public Filter visitNotFilter(Void p, Filter child) { return Filter.not(child.accept(this, null)); }
            @Override public Filter visitPresentFilter(Void p, String name) { return Filter.present(attribute(name, true)); }
            @Override public Filter visitEqualityMatchFilter(Void p, String name, ByteString v) {
                return Filter.equality(attribute(name, true), value(name, v, true));
            }
            @Override public Filter visitApproxMatchFilter(Void p, String name, ByteString v) {
                return Filter.approx(attribute(name, true), value(name, v, true));
            }
            @Override public Filter visitGreaterOrEqualFilter(Void p, String name, ByteString v) {
                return Filter.greaterOrEqual(attribute(name, true), value(name, v, true));
            }
            @Override public Filter visitLessOrEqualFilter(Void p, String name, ByteString v) {
                return Filter.lessOrEqual(attribute(name, true), value(name, v, true));
            }
            @Override public Filter visitExtensibleMatchFilter(Void p, String rule, String name,
                                                                ByteString v, boolean dnAttributes) {
                final boolean dnMatchingRule = "2.5.13.1".equals(rule)
                        || "distinguishedNameMatch".equalsIgnoreCase(rule);
                return Filter.extensible(rule, name == null ? null : attribute(name, true),
                        dnMatchingRule ? dnValue(v, true) : name == null ? v : value(name, v, true), dnAttributes);
            }
            @Override public Filter visitSubstringsFilter(Void p, String name, ByteString initial,
                                                           List<ByteString> any, ByteString end) {
                if (remoteBase != null && isDnAttribute(name)) {
                    throw new RewriteException(ResultCode.UNWILLING_TO_PERFORM,
                            "Cannot safely rewrite a substring filter on DN attribute " + name);
                }
                return Filter.substrings(attribute(name, true), initial, any, end);
            }
            @Override public Filter visitUnrecognizedFilter(Void p, byte tag, ByteString bytes) {
                throw new RewriteException(ResultCode.UNWILLING_TO_PERFORM,
                        "Cannot safely rewrite an unrecognized filter");
            }
        }, null);
    }
}
