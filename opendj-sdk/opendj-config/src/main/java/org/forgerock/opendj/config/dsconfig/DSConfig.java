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
 *      Copyright 2007-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2015 ForgeRock AS.
 */
package org.forgerock.opendj.config.dsconfig;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.CliMessages.*;
import static com.forgerock.opendj.cli.DocGenerationHelper.*;
import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.dsconfig.DsconfigMessages.*;
import static com.forgerock.opendj.util.StaticUtils.*;

import static org.forgerock.opendj.config.PropertyOption.*;
import static org.forgerock.opendj.config.dsconfig.ArgumentExceptionFactory.*;
import static org.forgerock.util.Utils.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg1;
import org.forgerock.opendj.config.ACIPropertyDefinition;
import org.forgerock.opendj.config.AbsoluteInheritedDefaultBehaviorProvider;
import org.forgerock.opendj.config.AbstractManagedObjectDefinition;
import org.forgerock.opendj.config.AdministratorAction;
import org.forgerock.opendj.config.AdministratorAction.Type;
import org.forgerock.opendj.config.AggregationPropertyDefinition;
import org.forgerock.opendj.config.AliasDefaultBehaviorProvider;
import org.forgerock.opendj.config.AttributeTypePropertyDefinition;
import org.forgerock.opendj.config.BooleanPropertyDefinition;
import org.forgerock.opendj.config.ClassPropertyDefinition;
import org.forgerock.opendj.config.ConfigurationFramework;
import org.forgerock.opendj.config.DNPropertyDefinition;
import org.forgerock.opendj.config.DefaultBehaviorProvider;
import org.forgerock.opendj.config.DefinedDefaultBehaviorProvider;
import org.forgerock.opendj.config.DurationPropertyDefinition;
import org.forgerock.opendj.config.DurationUnit;
import org.forgerock.opendj.config.EnumPropertyDefinition;
import org.forgerock.opendj.config.IPAddressMaskPropertyDefinition;
import org.forgerock.opendj.config.IPAddressPropertyDefinition;
import org.forgerock.opendj.config.InstantiableRelationDefinition;
import org.forgerock.opendj.config.IntegerPropertyDefinition;
import org.forgerock.opendj.config.ManagedObjectOption;
import org.forgerock.opendj.config.PropertyDefinition;
import org.forgerock.opendj.config.PropertyDefinitionVisitor;
import org.forgerock.opendj.config.PropertyOption;
import org.forgerock.opendj.config.RelationDefinition;
import org.forgerock.opendj.config.RelationOption;
import org.forgerock.opendj.config.RelativeInheritedDefaultBehaviorProvider;
import org.forgerock.opendj.config.SetRelationDefinition;
import org.forgerock.opendj.config.SizePropertyDefinition;
import org.forgerock.opendj.config.StringPropertyDefinition;
import org.forgerock.opendj.config.Tag;
import org.forgerock.opendj.config.UndefinedDefaultBehaviorProvider;
import org.forgerock.opendj.config.client.ManagedObjectDecodingException;
import org.forgerock.opendj.config.client.MissingMandatoryPropertiesException;
import org.forgerock.opendj.config.client.OperationRejectedException;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.util.Utils;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentGroup;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.CliConstants;
import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.CommandBuilder;
import com.forgerock.opendj.cli.CommonArguments;
import com.forgerock.opendj.cli.ConnectionFactoryProvider;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.Menu;
import com.forgerock.opendj.cli.MenuBuilder;
import com.forgerock.opendj.cli.MenuCallback;
import com.forgerock.opendj.cli.MenuResult;
import com.forgerock.opendj.cli.ReturnCode;
import com.forgerock.opendj.cli.StringArgument;
import com.forgerock.opendj.cli.SubCommand;
import com.forgerock.opendj.cli.SubCommandArgumentParser;
import com.forgerock.opendj.cli.SubCommandUsageHandler;
import com.forgerock.opendj.cli.VersionHandler;

/**
 * This class provides a command-line tool which enables administrators to configure the Directory Server.
 */
public final class DSConfig extends ConsoleApplication {

    /**
     * This class provides additional information about subcommands for generated reference documentation.
     */
    private final class DSConfigSubCommandUsageHandler implements SubCommandUsageHandler {

        /** Marker to open a DocBook XML paragraph. */
        private String op = "<para>";

        /** Marker to close a DocBook XML paragraph. */
        private String cp = "</para>";

        /** {@inheritDoc} */
        @Override
        public String getArgumentAdditionalInfo(SubCommand sc, Argument a, String nameOption) {
            StringBuilder sb = new StringBuilder();
            final AbstractManagedObjectDefinition<?, ?> defn = getManagedObjectDefinition(sc);
            if (isHidden(defn)) {
                return "";
            }
            if (doesHandleProperties(a)) {
                final LocalizableMessage name = defn.getUserFriendlyName();
                sb.append(op).append(REF_DSCFG_ARG_ADDITIONAL_INFO.get(name, name, nameOption)).append(cp).append(EOL);
            } else {
                listSubtypes(sb, sc, a, defn);
            }
            return sb.toString();
        }

        private boolean isHidden(AbstractManagedObjectDefinition defn) {
            return defn == null || defn.hasOption(ManagedObjectOption.HIDDEN);
        }

        private void listSubtypes(StringBuilder sb, SubCommand sc, Argument a,
                                  AbstractManagedObjectDefinition<?, ?> defn) {
            if (a.isHidden()) {
                return;
            }

            final LocalizableMessage placeholder = a.getValuePlaceholder();

            Map<String, Object> map = new HashMap<>();

            final LocalizableMessage name = defn.getUserFriendlyName();
            map.put("dependencies", REF_DSCFG_SUBTYPE_DEPENDENCIES.get(name, name, placeholder));
            map.put("typesIntro", REF_DSCFG_SUBTYPE_TYPES_INTRO.get(name));

            List<Map<String, Object>> children = new LinkedList<>();
            for (AbstractManagedObjectDefinition<?, ?> childDefn : getLeafChildren(defn)) {
                if (isHidden(childDefn)) {
                    continue;
                }
                Map<String, Object> child = new HashMap<>();

                child.put("name", childDefn.getName());
                child.put("default", REF_DSCFG_CHILD_DEFAULT.get(placeholder, childDefn.getUserFriendlyName()));
                child.put("enabled", REF_DSCFG_CHILD_ENABLED_BY_DEFAULT.get(propertyExists(childDefn, "enabled")));

                final String link = getLink(getScriptName() + "-" + sc.getName() + "-" + childDefn.getName());
                child.put("link", REF_DSCFG_CHILD_LINK.get(link, defn.getUserFriendlyName()));

                children.add(child);
            }
            map.put("children", children);

            applyTemplate(sb, "dscfgListSubtypes.ftl", map);
        }

        private boolean propertyExists(AbstractManagedObjectDefinition<?, ?> defn, String name) {
            if (isHidden(defn)) {
                return false;
            }
            try {
                return defn.getPropertyDefinition(name) != null;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        /** {@inheritDoc} */
        @Override
        public String getProperties(SubCommand sc) {
            final AbstractManagedObjectDefinition<?, ?> defn = getManagedObjectDefinition(sc);
            if (isHidden(defn)) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            for (AbstractManagedObjectDefinition<?, ?> childDefn : getLeafChildren(defn)) {
                if (isHidden(childDefn)) {
                    continue;
                }
                final List<PropertyDefinition<?>> props = new ArrayList<>(childDefn.getAllPropertyDefinitions());
                Collections.sort(props);
                Map<String, Object> map = new HashMap<>();
                final String propPrefix = getScriptName() + "-" + sc.getName() + "-" + childDefn.getName();
                map.put("id", propPrefix);
                map.put("title", childDefn.getUserFriendlyName());
                map.put("intro", REF_DSCFG_PROPS_INTRO.get(defn.getUserFriendlyPluralName(), childDefn.getName()));
                map.put("list", toVariableList(props, defn));
                applyTemplate(sb, "dscfgAppendProps.ftl", map);
            }
            return sb.toString();
        }

        private AbstractManagedObjectDefinition<?, ?> getManagedObjectDefinition(SubCommand sc) {
            final SubCommandHandler sch = handlers.get(sc);
            if (sch instanceof HelpSubCommandHandler) {
                return null;
            }
            final RelationDefinition<?, ?> rd = getRelationDefinition(sch);
            if (isHidden(rd)) {
                return null;
            }
            return rd.getChildDefinition();
        }

        private boolean isHidden(RelationDefinition defn) {
            return defn == null || defn.hasOption(RelationOption.HIDDEN);
        }


        private List<AbstractManagedObjectDefinition<?, ?>> getLeafChildren(
                AbstractManagedObjectDefinition<?, ?> defn) {
            final ArrayList<AbstractManagedObjectDefinition<?, ?>> results = new ArrayList<>();
            addIfLeaf(results, defn);
            Collections.sort(results, new Comparator<AbstractManagedObjectDefinition<?, ?>>() {
                @Override
                public int compare(AbstractManagedObjectDefinition<?, ?> o1, AbstractManagedObjectDefinition<?, ?> o2) {
                    return o1.getName().compareTo(o2.getName());
                }
            });
            return results;
        }

        private void addIfLeaf(final Collection<AbstractManagedObjectDefinition<?, ?>> results,
                final AbstractManagedObjectDefinition<?, ?> defn) {
            if (defn.getChildren().isEmpty()) {
                results.add(defn);
            } else {
                for (AbstractManagedObjectDefinition<?, ?> child : defn.getChildren()) {
                    addIfLeaf(results, child);
                }
            }
        }

        private RelationDefinition<?, ?> getRelationDefinition(final SubCommandHandler sch) {
            if (sch instanceof CreateSubCommandHandler) {
                return ((CreateSubCommandHandler<?, ?>) sch).getRelationDefinition();
            } else if (sch instanceof DeleteSubCommandHandler) {
                return ((DeleteSubCommandHandler) sch).getRelationDefinition();
            } else if (sch instanceof ListSubCommandHandler) {
                return ((ListSubCommandHandler) sch).getRelationDefinition();
            } else if (sch instanceof GetPropSubCommandHandler) {
                return ((GetPropSubCommandHandler) sch).getRelationDefinition();
            } else if (sch instanceof SetPropSubCommandHandler) {
                return ((SetPropSubCommandHandler) sch).getRelationDefinition();
            }
            return null;
        }

        private String toVariableList(List<PropertyDefinition<?>> props, AbstractManagedObjectDefinition<?, ?> defn) {
            StringBuilder b = new StringBuilder();
            Map<String, Object> map = new HashMap<>();

            List<Map<String, Object>> properties = new LinkedList<>();
            for (PropertyDefinition<?> prop : props) {
                if (prop.hasOption(HIDDEN)) {
                    continue;
                }
                Map<String, Object> property = new HashMap<>();
                property.put("term", prop.getName());
                property.put("descTitle", REF_TITLE_DESCRIPTION.get());
                property.put("description", getDescriptionString(prop));

                final StringBuilder sb = new StringBuilder();
                appendDefaultBehavior(sb, prop);
                appendAllowedValues(sb, prop);
                appendVarListEntry(sb, REF_DSCFG_PROPS_LABEL_MULTI_VALUED.get().toString(), getYN(prop, MULTI_VALUED));
                appendVarListEntry(sb, REF_DSCFG_PROPS_LABEL_REQUIRED.get().toString(), getYN(prop, MANDATORY));
                appendVarListEntry(sb, REF_DSCFG_PROPS_LABEL_ADMIN_ACTION_REQUIRED.get().toString(),
                        getAdminActionRequired(prop, defn));
                appendVarListEntry(sb, REF_DSCFG_PROPS_LABEL_ADVANCED_PROPERTY.get().toString(),
                        getYNAdvanced(prop, ADVANCED));
                appendVarListEntry(sb, REF_DSCFG_PROPS_LABEL_READ_ONLY.get().toString(), getYN(prop, READ_ONLY));
                property.put("list", sb.toString());

                properties.add(property);
            }
            map.put("properties", properties);

            applyTemplate(b, "dscfgVariableList.ftl", map);
            return b.toString();
        }

        private StringBuilder appendVarListEntry(StringBuilder b, String term, Object definition) {
            Map<String, Object> map = new HashMap<>();
            map.put("term", term);
            map.put("definition", definition);
            applyTemplate(b, "dscfgVarListEntry.ftl", map);
            return b;
        }

        private void appendDefaultBehavior(StringBuilder b, PropertyDefinition<?> prop) {
            StringBuilder sb = new StringBuilder();
            appendDefaultBehaviorString(sb, prop);
            appendVarListEntry(b, REF_DSCFG_PROPS_LABEL_DEFAULT_VALUE.get().toString(), sb.toString());
        }

        private void appendAllowedValues(StringBuilder b, PropertyDefinition<?> prop) {
            StringBuilder sb = new StringBuilder();
            appendSyntax(sb, prop);
            appendVarListEntry(b, REF_DSCFG_PROPS_LABEL_ALLOWED_VALUES.get().toString(), sb.toString());
        }

        private Object getDescriptionString(PropertyDefinition<?> prop) {
            return ((prop.getSynopsis() != null) ? prop.getSynopsis() + " " : "")
                    + ((prop.getDescription() != null) ? prop.getDescription() : "");
        }

        private String getAdminActionRequired(PropertyDefinition<?> prop, AbstractManagedObjectDefinition<?, ?> defn) {
            final AdministratorAction adminAction = prop.getAdministratorAction();
            if (adminAction != null) {
                final LocalizableMessage synopsis = adminAction.getSynopsis();
                final Type actionType = adminAction.getType();
                final StringBuilder action = new StringBuilder();
                if (actionType == Type.COMPONENT_RESTART) {
                    action.append(op)
                            .append(REF_DSCFG_ADMIN_ACTION_COMPONENT_RESTART.get(defn.getUserFriendlyName()))
                            .append(cp);
                } else if (actionType == Type.SERVER_RESTART) {
                    action.append(op).append(REF_DSCFG_ADMIN_ACTION_SERVER_RESTART.get()).append(cp);
                } else if (actionType == Type.NONE) {
                    action.append(op).append(REF_DSCFG_ADMIN_ACTION_NONE.get()).append(cp);
                }
                if (synopsis != null) {
                    action.append(op).append(synopsis).append(cp);
                }
                return action.toString();
            }
            return op + REF_DSCFG_ADMIN_ACTION_NONE.get() + cp;
        }

        private String getYN(PropertyDefinition<?> prop, PropertyOption option) {
            LocalizableMessage msg = prop.hasOption(option) ? REF_DSCFG_PROP_YES.get() : REF_DSCFG_PROP_NO.get();
            return op + msg + cp;
        }

        private String getYNAdvanced(PropertyDefinition<?> prop, PropertyOption option) {
            LocalizableMessage msg = prop.hasOption(option)
                    ? REF_DSCFG_PROP_YES_ADVANCED.get() : REF_DSCFG_PROP_NO.get();
            return op + msg + cp;
        }

        private void appendDefaultBehaviorString(StringBuilder b, PropertyDefinition<?> prop) {
            final DefaultBehaviorProvider<?> defaultBehavior = prop.getDefaultBehaviorProvider();
            if (defaultBehavior instanceof UndefinedDefaultBehaviorProvider) {
                b.append(op).append(REF_DSCFG_DEFAULT_BEHAVIOR_NONE.get()).append(cp).append(EOL);
            } else if (defaultBehavior instanceof DefinedDefaultBehaviorProvider) {
                DefinedDefaultBehaviorProvider<?> behavior = (DefinedDefaultBehaviorProvider<?>) defaultBehavior;
                final Collection<String> defaultValues = behavior.getDefaultValues();
                if (defaultValues.size() == 0) {
                    b.append(op).append(REF_DSCFG_DEFAULT_BEHAVIOR_NONE.get()).append(cp).append(EOL);
                } else if (defaultValues.size() == 1) {
                    b.append(op).append(REF_DSCFG_DEFAULT_BEHAVIOR.get(defaultValues.iterator().next()))
                            .append(cp).append(EOL);
                } else {
                    final Iterator<String> it = defaultValues.iterator();
                    b.append(op).append(REF_DSCFG_DEFAULT_BEHAVIOR.get(it.next())).append(cp);
                    for (; it.hasNext();) {
                        b.append(EOL).append(op).append(REF_DSCFG_DEFAULT_BEHAVIOR.get(it.next())).append(cp);
                    }
                    b.append(EOL);
                }
            } else if (defaultBehavior instanceof AliasDefaultBehaviorProvider) {
                AliasDefaultBehaviorProvider<?> behavior = (AliasDefaultBehaviorProvider<?>) defaultBehavior;
                b.append(op).append(REF_DSCFG_DEFAULT_BEHAVIOR.get(behavior.getSynopsis())).append(cp).append(EOL);
            } else if (defaultBehavior instanceof RelativeInheritedDefaultBehaviorProvider) {
                final RelativeInheritedDefaultBehaviorProvider<?> behavior =
                        (RelativeInheritedDefaultBehaviorProvider<?>) defaultBehavior;
                appendDefaultBehaviorString(b,
                        behavior.getManagedObjectDefinition().getPropertyDefinition(behavior.getPropertyName()));
            } else if (defaultBehavior instanceof AbsoluteInheritedDefaultBehaviorProvider) {
                final AbsoluteInheritedDefaultBehaviorProvider<?> behavior =
                        (AbsoluteInheritedDefaultBehaviorProvider<?>) defaultBehavior;
                appendDefaultBehaviorString(b,
                        behavior.getManagedObjectDefinition().getPropertyDefinition(behavior.getPropertyName()));
            }
        }

        private void appendSyntax(final StringBuilder b, PropertyDefinition<?> prop) {
            // Create a visitor for performing syntax specific processing.
            PropertyDefinitionVisitor<String, Void> visitor = new PropertyDefinitionVisitor<String, Void>() {

                @Override
                public String visitACI(ACIPropertyDefinition prop, Void p) {
                    b.append(op).append(REF_DSCFG_ACI_SYNTAX_REL_URL.get()).append(cp).append(EOL);
                    return null;
                }

                @Override
                public String visitAggregation(AggregationPropertyDefinition prop, Void p) {
                    b.append(op);
                    final RelationDefinition<?, ?> rel = prop.getRelationDefinition();
                    if (isHidden(rel)) {
                        return null;
                    }
                    final String relFriendlyName = rel.getUserFriendlyName().toString();
                    b.append(REF_DSCFG_AGGREGATION.get(relFriendlyName)).append(". ");
                    final LocalizableMessage synopsis = prop.getSourceConstraintSynopsis();
                    if (synopsis != null) {
                        b.append(synopsis);
                    }
                    b.append(cp).append(EOL);
                    return null;
                }

                @Override
                public String visitAttributeType(AttributeTypePropertyDefinition prop, Void p) {
                    b.append(op).append(REF_DSCFG_ANY_ATTRIBUTE.get()).append(".").append(cp).append(EOL);
                    return null;
                }

                @Override
                public String visitBoolean(BooleanPropertyDefinition prop, Void p) {
                    b.append(op).append("true").append(cp).append(EOL);
                    b.append(op).append("false").append(cp).append(EOL);
                    return null;
                }

                @Override
                public String visitClass(ClassPropertyDefinition prop, Void p) {
                    b.append(op).append(REF_DSCFG_JAVA_PLUGIN.get()).append(" ")
                            .append(Utils.joinAsString(EOL, prop.getInstanceOfInterface())).append(cp).append(EOL);
                    return null;
                }

                @Override
                public String visitDN(DNPropertyDefinition prop, Void p) {
                    b.append(op).append(REF_DSCFG_VALID_DN.get());
                    final DN baseDN = prop.getBaseDN();
                    if (baseDN != null) {
                        b.append(": ").append(baseDN);
                    } else {
                        b.append(".");
                    }
                    b.append(cp).append(EOL);
                    return null;
                }

                @Override
                public String visitDuration(DurationPropertyDefinition prop, Void p) {
                    b.append(REF_DSCFG_DURATION_SYNTAX_REL_URL.get()).append(EOL);
                    b.append(op);
                    if (prop.isAllowUnlimited()) {
                        b.append(REF_DSCFG_ALLOW_UNLIMITED.get()).append(" ");
                    }
                    if (prop.getMaximumUnit() != null) {
                        final String maxUnitName = prop.getMaximumUnit().getLongName();
                        b.append(REF_DSCFG_DURATION_MAX_UNIT.get(maxUnitName)).append(".");
                    }
                    final DurationUnit baseUnit = prop.getBaseUnit();
                    final long lowerLimit = valueOf(baseUnit, prop.getLowerLimit());
                    final String unitName = baseUnit.getLongName();
                    b.append(REF_DSCFG_DURATION_LOWER_LIMIT.get(lowerLimit, unitName)).append(".");
                    if (prop.getUpperLimit() != null) {
                        final long upperLimit = valueOf(baseUnit, prop.getUpperLimit());
                        b.append(REF_DSCFG_DURATION_UPPER_LIMIT.get(upperLimit, unitName)).append(".");
                    }
                    b.append(cp).append(EOL);
                    return null;
                }

                private long valueOf(final DurationUnit baseUnit, long upperLimit) {
                    return Double.valueOf(baseUnit.fromMilliSeconds(upperLimit)).longValue();
                }

                @Override
                public String visitEnum(EnumPropertyDefinition prop, Void p) {
                    b.append("<variablelist>").append(EOL);
                    final Class<?> en = prop.getEnumClass();
                    final Object[] constants = en.getEnumConstants();
                    for (Object enumConstant : constants) {
                        final LocalizableMessage valueSynopsis = prop.getValueSynopsis((Enum) enumConstant);
                        appendVarListEntry(b, enumConstant.toString(), op + valueSynopsis + cp);
                    }
                    b.append("</variablelist>").append(EOL);
                    return null;
                }

                @Override
                public String visitInteger(IntegerPropertyDefinition prop, Void p) {
                    b.append(op).append(REF_DSCFG_INT.get()).append(". ")
                            .append(REF_DSCFG_INT_LOWER_LIMIT.get(prop.getLowerLimit())).append(".");
                    if (prop.getUpperLimit() != null) {
                        b.append(" ").append(REF_DSCFG_INT_UPPER_LIMIT.get(prop.getUpperLimit())).append(".");
                    }
                    if (prop.isAllowUnlimited()) {
                        b.append(" ").append(REF_DSCFG_ALLOW_UNLIMITED.get());
                    }
                    if (prop.getUnitSynopsis() != null) {
                        b.append(" ").append(REF_DSCFG_INT_UNIT.get(prop.getUnitSynopsis())).append(".");
                    }
                    b.append(cp).append(EOL);
                    return null;
                }

                @Override
                public String visitIPAddress(IPAddressPropertyDefinition prop, Void p) {
                    b.append(op).append(REF_DSCFG_IP_ADDRESS.get()).append(cp).append(EOL);
                    return null;
                }

                @Override
                public String visitIPAddressMask(IPAddressMaskPropertyDefinition prop, Void p) {
                    b.append(op).append(REF_DSCFG_IP_ADDRESS_MASK.get()).append(cp).append(EOL);
                    return null;
                }

                @Override
                public String visitSize(SizePropertyDefinition prop, Void p) {
                    b.append(op);
                    if (prop.getLowerLimit() != 0) {
                        b.append(REF_DSCFG_INT_LOWER_LIMIT.get(prop.getLowerLimit())).append(".");
                    }
                    if (prop.getUpperLimit() != null) {
                        b.append(REF_DSCFG_INT_UPPER_LIMIT.get(prop.getUpperLimit())).append(".");
                    }
                    if (prop.isAllowUnlimited()) {
                        b.append(REF_DSCFG_ALLOW_UNLIMITED.get());
                    }
                    b.append(cp).append(EOL);
                    return null;
                }

                @Override
                public String visitString(StringPropertyDefinition prop, Void p) {
                    b.append(op);
                    if (prop.getPatternSynopsis() != null) {
                        b.append(prop.getPatternSynopsis());
                    } else {
                        b.append(REF_DSCFG_STRING.get());
                    }
                    b.append(cp).append(EOL);
                    return null;
                }

                @Override
                public String visitUnknown(PropertyDefinition prop, Void p) {
                    b.append(op).append(REF_DSCFG_UNKNOWN.get()).append(cp).append(EOL);
                    return null;
                }
            };

            // Invoke the visitor against the property definition.
            prop.accept(visitor, null);
        }

        private String getLink(String target) {
            return " <xref linkend=\"" + target + "\" />";
        }
    }

    /** The name of this tool. */
    static final String DSCONFIGTOOLNAME = "dsconfig";

    /** The name of a command-line script used to launch an administrative tool. */
    static final String PROPERTY_SCRIPT_NAME = "org.opends.server.scriptName";

    /** A menu call-back which runs a sub-command interactively. */
    private class SubCommandHandlerMenuCallback implements MenuCallback<Integer> {

        /** The sub-command handler. */
        private final SubCommandHandler handler;

        /**
         * Creates a new sub-command handler call-back.
         *
         * @param handler
         *            The sub-command handler.
         */
        public SubCommandHandlerMenuCallback(SubCommandHandler handler) {
            this.handler = handler;
        }

        /** {@inheritDoc} */
        @Override
        public MenuResult<Integer> invoke(ConsoleApplication app) throws ClientException {
            try {
                final MenuResult<Integer> result = handler.run(app, factory);
                if (result.isQuit()) {
                    return result;
                }
                if (result.isSuccess() && isInteractive() && handler.isCommandBuilderUseful()) {
                    printCommandBuilder(getCommandBuilder(handler));
                }
                // Success or cancel.
                app.println();
                app.pressReturnToContinue();
                return MenuResult.again();
            } catch (ArgumentException e) {
                app.errPrintln(e.getMessageObject());
                return MenuResult.success(1);
            } catch (ClientException e) {
                app.errPrintln(e.getMessageObject());
                return MenuResult.success(e.getReturnCode());
            }
        }
    }

    /** The interactive mode sub-menu implementation. */
    private class SubMenuCallback implements MenuCallback<Integer> {

        /** The menu. */
        private final Menu<Integer> menu;

        /**
         * Creates a new sub-menu implementation.
         *
         * @param app
         *            The console application.
         * @param rd
         *            The relation definition.
         * @param ch
         *            The optional create sub-command.
         * @param dh
         *            The optional delete sub-command.
         * @param lh
         *            The optional list sub-command.
         * @param sh
         *            The option set-prop sub-command.
         */
        public SubMenuCallback(ConsoleApplication app, RelationDefinition<?, ?> rd, CreateSubCommandHandler<?, ?> ch,
                DeleteSubCommandHandler dh, ListSubCommandHandler lh, SetPropSubCommandHandler sh) {
            LocalizableMessage userFriendlyName = rd.getUserFriendlyName();

            LocalizableMessage userFriendlyPluralName = null;
            if (rd instanceof InstantiableRelationDefinition<?, ?>) {
                InstantiableRelationDefinition<?, ?> ir = (InstantiableRelationDefinition<?, ?>) rd;
                userFriendlyPluralName = ir.getUserFriendlyPluralName();
            } else if (rd instanceof SetRelationDefinition<?, ?>) {
                SetRelationDefinition<?, ?> sr = (SetRelationDefinition<?, ?>) rd;
                userFriendlyPluralName = sr.getUserFriendlyPluralName();
            }

            final MenuBuilder<Integer> builder = new MenuBuilder<>(app);

            builder.setTitle(INFO_DSCFG_HEADING_COMPONENT_MENU_TITLE.get(userFriendlyName));
            builder.setPrompt(INFO_DSCFG_HEADING_COMPONENT_MENU_PROMPT.get());

            if (lh != null) {
                final SubCommandHandlerMenuCallback callback = new SubCommandHandlerMenuCallback(lh);
                final LocalizableMessage msg = getMsg(
                    INFO_DSCFG_OPTION_COMPONENT_MENU_LIST_SINGULAR, userFriendlyName,
                    INFO_DSCFG_OPTION_COMPONENT_MENU_LIST_PLURAL, userFriendlyPluralName);
                builder.addNumberedOption(msg, callback);
            }

            if (ch != null) {
                final SubCommandHandlerMenuCallback callback = new SubCommandHandlerMenuCallback(ch);
                builder.addNumberedOption(INFO_DSCFG_OPTION_COMPONENT_MENU_CREATE.get(userFriendlyName), callback);
            }

            if (sh != null) {
                final SubCommandHandlerMenuCallback callback = new SubCommandHandlerMenuCallback(sh);
                final LocalizableMessage msg = getMsg(
                    INFO_DSCFG_OPTION_COMPONENT_MENU_MODIFY_SINGULAR, userFriendlyName,
                    INFO_DSCFG_OPTION_COMPONENT_MENU_MODIFY_PLURAL, userFriendlyPluralName);
                builder.addNumberedOption(msg, callback);
            }

            if (dh != null) {
                final SubCommandHandlerMenuCallback callback = new SubCommandHandlerMenuCallback(dh);
                builder.addNumberedOption(INFO_DSCFG_OPTION_COMPONENT_MENU_DELETE.get(userFriendlyName), callback);
            }

            builder.addBackOption(true);
            builder.addQuitOption();

            this.menu = builder.toMenu();
        }

        private LocalizableMessage getMsg(Arg1<Object> singularMsg, LocalizableMessage userFriendlyName,
            Arg1<Object> pluralMsg, LocalizableMessage userFriendlyPluralName)
        {
          return userFriendlyPluralName != null
              ? pluralMsg.get(userFriendlyPluralName)
              : singularMsg.get(userFriendlyName);
        }

        /** {@inheritDoc} */
        @Override
        public final MenuResult<Integer> invoke(ConsoleApplication app) throws ClientException {
            try {
                app.println();
                app.println();

                final MenuResult<Integer> result = menu.run();
                if (result.isCancel()) {
                    return MenuResult.again();
                }
                return result;
            } catch (ClientException e) {
                app.errPrintln(e.getMessageObject());
                return MenuResult.success(1);
            }
        }
    }

    /**
     * The type name which will be used for the most generic managed object types when they are instantiable and
     * intended for customization only.
     */
    public static final String CUSTOM_TYPE = "custom";

    /**
     * The type name which will be used for the most generic managed object types when they are instantiable and not
     * intended for customization.
     */
    public static final String GENERIC_TYPE = "generic";

    /**
     * Prints the provided error message if the provided application is
     * interactive, throws a {@link ClientException} with provided error code
     * and message otherwise.
     *
     * @param app
     *            The console application where the message should be printed.
     * @param msg
     *            The human readable error message.
     * @param errorCode
     *            The operation error code.
     * @return A generic cancel menu result if application is interactive.
     * @throws ClientException
     *             If the application is not interactive.
     */
    static <T> MenuResult<T> interactivePrintOrThrowError(ConsoleApplication app,
        LocalizableMessage msg, ReturnCode errorCode) throws ClientException {
        if (app.isInteractive()) {
            app.errPrintln();
            app.errPrintln(msg);
            return MenuResult.cancel();
        } else {
            throw new ClientException(errorCode, msg);
        }
    }

    private long sessionStartTime;
    private boolean sessionStartTimePrinted;
    private int sessionEquivalentOperationNumber;

    /**
     * Provides the command-line arguments to the main application for processing.
     *
     * @param args
     *            The set of command-line arguments provided to this program.
     */
    public static void main(String[] args) {
        int exitCode = main(args, System.out, System.err);
        if (exitCode != ReturnCode.SUCCESS.get()) {
            System.exit(filterExitCode(exitCode));
        }
    }

    /**
     * Provides the command-line arguments to the main application for processing and returns the exit code as an
     * integer.
     *
     * @param args
     *            The set of command-line arguments provided to this program.
     * @param outStream
     *            The output stream for standard output.
     * @param errStream
     *            The output stream for standard error.
     * @return Zero to indicate that the program completed successfully, or non-zero to indicate that an error occurred.
     */
    public static int main(String[] args, OutputStream outStream, OutputStream errStream) {
        final DSConfig app = new DSConfig(outStream, errStream);
        app.sessionStartTime = System.currentTimeMillis();

        if (!ConfigurationFramework.getInstance().isInitialized()) {
            try {
                ConfigurationFramework.getInstance().initialize();
            } catch (ConfigException e) {
                app.errPrintln(e.getMessageObject());
                return ReturnCode.ERROR_INITIALIZING_SERVER.get();
            }
        }

        // Run the application.
        return app.run(args);
    }

    /** The argument which should be used to request advanced mode. */
    private BooleanArgument advancedModeArgument;

    /**
     * The factory which the application should use to retrieve its management context.
     */
    private LDAPManagementContextFactory factory;

    /**
     * Flag indicating whether or not the global arguments have already been initialized.
     */
    private boolean globalArgumentsInitialized;

    /** The sub-command handler factory. */
    private SubCommandHandlerFactory handlerFactory;

    /** Mapping of sub-commands to their implementations. */
    private final Map<SubCommand, SubCommandHandler> handlers = new HashMap<>();

    /** Indicates whether or not a sub-command was provided. */
    private boolean hasSubCommand = true;

    /** The argument which should be used to read dsconfig commands from standard input. */
    private BooleanArgument batchArgument;
    /** The argument which should be used to read dsconfig commands from a file. */
    private StringArgument batchFileArgument;

    /** The argument which should be used to request non interactive behavior. */
    private BooleanArgument noPromptArgument;

    /**
     * The argument that the user must set to display the equivalent non-interactive mode argument.
     */
    private BooleanArgument displayEquivalentArgument;

    /**
     * The argument that allows the user to dump the equivalent non-interactive command to a file.
     */
    private StringArgument equivalentCommandFileArgument;

    /** The command-line argument parser. */
    private final SubCommandArgumentParser parser;

    /** The argument which should be used to request quiet output. */
    private BooleanArgument quietArgument;

    /** The argument which should be used to request script-friendly output. */
    private BooleanArgument scriptFriendlyArgument;

    /** The argument which should be used to request usage information. */
    private BooleanArgument showUsageArgument;

    /** The argument which should be used to request verbose output. */
    private BooleanArgument verboseArgument;

    /** The argument which should be used to indicate the properties file. */
    private StringArgument propertiesFileArgument;

    /**
     * The argument which should be used to indicate that we will not look for properties file.
     */
    private BooleanArgument noPropertiesFileArgument;

    /**
     * Creates a new DSConfig application instance.
     *
     * @param out
     *            The application output stream.
     * @param err
     *            The application error stream.
     */
    private DSConfig(OutputStream out, OutputStream err) {
        super(new PrintStream(out), new PrintStream(err));

        this.parser = new SubCommandArgumentParser(getClass().getName(), INFO_DSCFG_TOOL_DESCRIPTION.get(), false);
        this.parser.setShortToolDescription(REF_SHORT_DESC_DSCONFIG.get());
        this.parser.setDocToolDescriptionSupplement(REF_DSCFG_DOC_TOOL_DESCRIPTION.get());
        this.parser.setDocSubcommandsDescriptionSupplement(REF_DSCFG_DOC_SUBCOMMANDS_DESCRIPTION.get());
        this.parser.setVersionHandler(new VersionHandler() {
            @Override
            public void printVersion() {
                System.out.println(getVersionString());
            }

            private String getVersionString() {
                try {
                    final Enumeration<URL> resources = getClass().getClassLoader().getResources(
                            "META-INF/maven/org.forgerock.opendj/opendj-config/pom.properties");
                    while (resources.hasMoreElements()) {
                        final Properties props = new Properties();
                        props.load(resources.nextElement().openStream());
                        return (String) props.get("version");
                    }
                } catch (IOException e) {
                    errPrintln(LocalizableMessage.raw(e.getMessage()));
                }
                return "";
            }
        });
        if (System.getProperty("org.forgerock.opendj.gendoc") != null) {
            this.parser.setUsageHandler(new DSConfigSubCommandUsageHandler());
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAdvancedMode() {
        return advancedModeArgument.isPresent();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isInteractive() {
        return !noPromptArgument.isPresent();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isMenuDrivenMode() {
        return !hasSubCommand;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isQuiet() {
        return quietArgument.isPresent();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isScriptFriendly() {
        return scriptFriendlyArgument.isPresent();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isVerbose() {
        return verboseArgument.isPresent();
    }

    /** Displays the provided error message followed by a help usage reference. */
    private void displayErrorMessageAndUsageReference(LocalizableMessage message) {
        errPrintln(message);
        errPrintln();
        errPrintln(parser.getHelpUsageReference());
    }

    /**
     * Registers the global arguments with the argument parser.
     *
     * @throws ArgumentException
     *             If a global argument could not be registered.
     */
    private void initializeGlobalArguments() throws ArgumentException {
        if (!globalArgumentsInitialized) {

            verboseArgument = CommonArguments.getVerbose();
            quietArgument = CommonArguments.getQuiet();
            scriptFriendlyArgument = CommonArguments.getScriptFriendly();
            noPromptArgument = CommonArguments.getNoPrompt();
            advancedModeArgument = CommonArguments.getAdvancedMode();
            showUsageArgument = CommonArguments.getShowUsage();

            batchArgument = new BooleanArgument(OPTION_LONG_BATCH, null, OPTION_LONG_BATCH,
                    INFO_DESCRIPTION_BATCH.get());

            batchFileArgument = new StringArgument(OPTION_LONG_BATCH_FILE_PATH, OPTION_SHORT_BATCH_FILE_PATH,
                    OPTION_LONG_BATCH_FILE_PATH, false, false, true, INFO_BATCH_FILE_PATH_PLACEHOLDER.get(), null,
                    null, INFO_DESCRIPTION_BATCH_FILE_PATH.get());

            displayEquivalentArgument = new BooleanArgument(OPTION_LONG_DISPLAY_EQUIVALENT, null,
                    OPTION_LONG_DISPLAY_EQUIVALENT, INFO_DSCFG_DESCRIPTION_DISPLAY_EQUIVALENT.get());

            equivalentCommandFileArgument = new StringArgument(OPTION_LONG_EQUIVALENT_COMMAND_FILE_PATH, null,
                    OPTION_LONG_EQUIVALENT_COMMAND_FILE_PATH, false, false, true, INFO_PATH_PLACEHOLDER.get(), null,
                    null, INFO_DSCFG_DESCRIPTION_EQUIVALENT_COMMAND_FILE_PATH.get());

            propertiesFileArgument = new StringArgument("propertiesFilePath", null, OPTION_LONG_PROP_FILE_PATH, false,
                    false, true, INFO_PROP_FILE_PATH_PLACEHOLDER.get(), null, null,
                    INFO_DESCRIPTION_PROP_FILE_PATH.get());

            noPropertiesFileArgument = new BooleanArgument("noPropertiesFileArgument", null, OPTION_LONG_NO_PROP_FILE,
                    INFO_DESCRIPTION_NO_PROP_FILE.get());

            // Register the global arguments.

            ArgumentGroup toolOptionsGroup = new ArgumentGroup(INFO_DSCFG_DESCRIPTION_OPTIONS_ARGS.get(), 2);
            parser.addGlobalArgument(advancedModeArgument, toolOptionsGroup);

            parser.addGlobalArgument(showUsageArgument);
            parser.setUsageArgument(showUsageArgument, getOutputStream());
            parser.addGlobalArgument(verboseArgument);
            parser.addGlobalArgument(quietArgument);
            parser.addGlobalArgument(scriptFriendlyArgument);
            parser.addGlobalArgument(noPromptArgument);
            parser.addGlobalArgument(batchArgument);
            parser.addGlobalArgument(batchFileArgument);
            parser.addGlobalArgument(displayEquivalentArgument);
            parser.addGlobalArgument(equivalentCommandFileArgument);
            parser.addGlobalArgument(propertiesFileArgument);
            parser.setFilePropertiesArgument(propertiesFileArgument);
            parser.addGlobalArgument(noPropertiesFileArgument);
            parser.setNoPropertiesFileArgument(noPropertiesFileArgument);

            globalArgumentsInitialized = true;
        }
    }

    /**
     * Registers the sub-commands with the argument parser. This method uses the administration framework introspection
     * APIs to determine the overall structure of the command-line.
     *
     * @throws ArgumentException
     *             If a sub-command could not be created.
     */
    private void initializeSubCommands() throws ArgumentException {
        if (handlerFactory == null) {
            handlerFactory = new SubCommandHandlerFactory(parser);

            final Comparator<SubCommand> c = new Comparator<SubCommand>() {

                @Override
                public int compare(SubCommand o1, SubCommand o2) {
                    return o1.getName().compareTo(o2.getName());
                }
            };

            Map<Tag, SortedSet<SubCommand>> groups = new TreeMap<>();
            SortedSet<SubCommand> allSubCommands = new TreeSet<>(c);
            for (SubCommandHandler handler : handlerFactory.getAllSubCommandHandlers()) {
                SubCommand sc = handler.getSubCommand();

                handlers.put(sc, handler);
                allSubCommands.add(sc);

                // Add the sub-command to its groups.
                for (Tag tag : handler.getTags()) {
                    SortedSet<SubCommand> group = groups.get(tag);
                    if (group == null) {
                        group = new TreeSet<>(c);
                        groups.put(tag, group);
                    }
                    group.add(sc);
                }
            }

            // Register the usage arguments.
            for (Map.Entry<Tag, SortedSet<SubCommand>> group : groups.entrySet()) {
                Tag tag = group.getKey();
                SortedSet<SubCommand> subCommands = group.getValue();

                String option = OPTION_LONG_HELP + "-" + tag.getName();
                String synopsis = tag.getSynopsis().toString().toLowerCase();
                BooleanArgument arg = new BooleanArgument(option, null, option,
                        INFO_DSCFG_DESCRIPTION_SHOW_GROUP_USAGE.get(synopsis));

                parser.addGlobalArgument(arg);
                parser.setUsageGroupArgument(arg, subCommands);
            }

            // Register the --help-all argument.
            String option = OPTION_LONG_HELP + "-all";
            BooleanArgument arg = new BooleanArgument(option, null, option,
                    INFO_DSCFG_DESCRIPTION_SHOW_GROUP_USAGE_ALL.get());

            parser.addGlobalArgument(arg);
            parser.setUsageGroupArgument(arg, allSubCommands);
        }
    }

    /**
     * Parses the provided command-line arguments and makes the appropriate changes to the Directory Server
     * configuration.
     *
     * @param args
     *            The command-line arguments provided to this program.
     * @return The exit code from the configuration processing. A nonzero value indicates that there was some kind of
     *         problem during the configuration processing.
     */
    private int run(String[] args) {

        // Register global arguments and sub-commands.
        try {
            initializeGlobalArguments();
            initializeSubCommands();
        } catch (ArgumentException e) {
            errPrintln(ERR_CANNOT_INITIALIZE_ARGS.get(e.getMessage()));
            return ReturnCode.ERROR_USER_DATA.get();
        }

        ConnectionFactoryProvider cfp = null;
        try {
            cfp = new ConnectionFactoryProvider(parser, this, CliConstants.DEFAULT_ROOT_USER_DN,
                    CliConstants.DEFAULT_ADMINISTRATION_CONNECTOR_PORT, true);
            cfp.setIsAnAdminConnection();

            // Parse the command-line arguments provided to this program.
            parser.parseArguments(args);
            checkForConflictingArguments();
        } catch (ArgumentException ae) {
            displayErrorMessageAndUsageReference(ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
            return ReturnCode.CONFLICTING_ARGS.get();
        }

        // If the usage/version argument was provided, then we don't need
        // to do anything else.
        if (parser.usageOrVersionDisplayed()) {
            return ReturnCode.SUCCESS.get();
        }

        // Check that we can write on the provided path where we write the
        // equivalent non-interactive commands.
        if (equivalentCommandFileArgument.isPresent()) {
            final String file = equivalentCommandFileArgument.getValue();
            if (!canWrite(file)) {
                errPrintln(ERR_DSCFG_CANNOT_WRITE_EQUIVALENT_COMMAND_LINE_FILE.get(file));
                return ReturnCode.ERROR_UNEXPECTED.get();
            } else if (new File(file).isDirectory()) {
                errPrintln(ERR_DSCFG_EQUIVALENT_COMMAND_LINE_FILE_DIRECTORY.get(file));
                return ReturnCode.ERROR_UNEXPECTED.get();
            }
        }
        // Creates the management context factory which is based on the connection
        // provider factory and an authenticated connection factory.
        try {
            factory = new LDAPManagementContextFactory(cfp);
        } catch (ArgumentException e) {
            displayErrorMessageAndUsageReference(ERR_ERROR_PARSING_ARGS.get(e.getMessage()));
            return ReturnCode.CONFLICTING_ARGS.get();
        }

        // Handle batch file if any
        if (batchArgument.isPresent() || batchFileArgument.isPresent()) {
            handleBatch(args);
            return ReturnCode.SUCCESS.get();
        }

        int retCode = 0;
        hasSubCommand = parser.getSubCommand() != null;
        if (!hasSubCommand) {
            if (isInteractive()) {
                // Top-level interactive mode.
                retCode = runInteractiveMode();
            } else {
                displayErrorMessageAndUsageReference(
                    ERR_ERROR_PARSING_ARGS.get(ERR_DSCFG_ERROR_MISSING_SUBCOMMAND.get()));
                retCode = ReturnCode.ERROR_USER_DATA.get();
            }
        } else {
            // Retrieve the sub-command implementation and run it.
            retCode = runSubCommand(handlers.get(parser.getSubCommand()));
        }

        factory.close();

        return retCode;
    }

    private void checkForConflictingArguments() throws ArgumentException {
        throwIfConflictingArgsSet(quietArgument, verboseArgument);
        throwIfConflictingArgsSet(batchArgument, batchFileArgument);

        throwIfSetInInteractiveMode(batchFileArgument);
        throwIfSetInInteractiveMode(batchArgument);
        throwIfSetInInteractiveMode(quietArgument);

        throwIfConflictingArgsSet(scriptFriendlyArgument, verboseArgument);
        throwIfConflictingArgsSet(noPropertiesFileArgument, propertiesFileArgument);
    }

    private void throwIfSetInInteractiveMode(Argument arg) throws ArgumentException {
        if (arg.isPresent() && !noPromptArgument.isPresent()) {
            throw new ArgumentException(ERR_DSCFG_ERROR_QUIET_AND_INTERACTIVE_INCOMPATIBLE.get(
                    arg.getLongIdentifier(), noPromptArgument.getLongIdentifier()));
        }
    }

    private void throwIfConflictingArgsSet(Argument arg1, Argument arg2) throws ArgumentException {
        if (arg1.isPresent() && arg2.isPresent()) {
            throw new ArgumentException(ERR_TOOL_CONFLICTING_ARGS.get(
                    arg1.getLongIdentifier(), arg2.getLongIdentifier()));
        }
    }

    /** Run the top-level interactive console. */
    private int runInteractiveMode() {

        ConsoleApplication app = this;

        // Build menu structure.
        final Comparator<RelationDefinition<?, ?>> c = new Comparator<RelationDefinition<?, ?>>() {

            @Override
            public int compare(RelationDefinition<?, ?> rd1, RelationDefinition<?, ?> rd2) {
                final String s1 = rd1.getUserFriendlyName().toString();
                final String s2 = rd2.getUserFriendlyName().toString();

                return s1.compareToIgnoreCase(s2);
            }
        };

        final Set<RelationDefinition<?, ?>> relations = new TreeSet<>(c);

        final Map<RelationDefinition<?, ?>, CreateSubCommandHandler<?, ?>> createHandlers = new HashMap<>();
        final Map<RelationDefinition<?, ?>, DeleteSubCommandHandler> deleteHandlers = new HashMap<>();
        final Map<RelationDefinition<?, ?>, ListSubCommandHandler> listHandlers = new HashMap<>();
        final Map<RelationDefinition<?, ?>, GetPropSubCommandHandler> getPropHandlers = new HashMap<>();
        final Map<RelationDefinition<?, ?>, SetPropSubCommandHandler> setPropHandlers = new HashMap<>();

        for (final CreateSubCommandHandler<?, ?> ch : handlerFactory.getCreateSubCommandHandlers()) {
            relations.add(ch.getRelationDefinition());
            createHandlers.put(ch.getRelationDefinition(), ch);
        }

        for (final DeleteSubCommandHandler dh : handlerFactory.getDeleteSubCommandHandlers()) {
            relations.add(dh.getRelationDefinition());
            deleteHandlers.put(dh.getRelationDefinition(), dh);
        }

        for (final ListSubCommandHandler lh : handlerFactory.getListSubCommandHandlers()) {
            relations.add(lh.getRelationDefinition());
            listHandlers.put(lh.getRelationDefinition(), lh);
        }

        for (final GetPropSubCommandHandler gh : handlerFactory.getGetPropSubCommandHandlers()) {
            relations.add(gh.getRelationDefinition());
            getPropHandlers.put(gh.getRelationDefinition(), gh);
        }

        for (final SetPropSubCommandHandler sh : handlerFactory.getSetPropSubCommandHandlers()) {
            relations.add(sh.getRelationDefinition());
            setPropHandlers.put(sh.getRelationDefinition(), sh);
        }

        // Main menu.
        final MenuBuilder<Integer> builder = new MenuBuilder<>(app);

        builder.setTitle(INFO_DSCFG_HEADING_MAIN_MENU_TITLE.get());
        builder.setPrompt(INFO_DSCFG_HEADING_MAIN_MENU_PROMPT.get());
        builder.setMultipleColumnThreshold(0);

        for (final RelationDefinition<?, ?> rd : relations) {
            final MenuCallback<Integer> callback = new SubMenuCallback(app, rd, createHandlers.get(rd),
                    deleteHandlers.get(rd), listHandlers.get(rd), setPropHandlers.get(rd));
            builder.addNumberedOption(rd.getUserFriendlyName(), callback);
        }

        builder.addQuitOption();

        final Menu<Integer> menu = builder.toMenu();

        try {
            // Force retrieval of management context.
            factory.getManagementContext(app);
        } catch (ArgumentException | ClientException e) {
            app.errPrintln(e.getMessageObject());
            return ReturnCode.ERROR_UNEXPECTED.get();
        }

        try {
            app.println();
            app.println();

            final MenuResult<Integer> result = menu.run();
            if (result.isQuit()) {
                return ReturnCode.SUCCESS.get();
            } else {
                return result.getValue();
            }
        } catch (ClientException e) {
            app.errPrintln(e.getMessageObject());
            return ReturnCode.ERROR_UNEXPECTED.get();
        }
    }

    /** Run the provided sub-command handler. */
    private int runSubCommand(SubCommandHandler handler) {
        try {
            final MenuResult<Integer> result = handler.run(this, factory);
            if (result.isSuccess()) {
                if (isInteractive() && handler.isCommandBuilderUseful()) {
                    printCommandBuilder(getCommandBuilder(handler));
                }
                return result.getValue();
            } else {
                // User must have quit.
                return ReturnCode.ERROR_UNEXPECTED.get();
            }
        } catch (ArgumentException e) {
            errPrintln(e.getMessageObject());
            return ReturnCode.ERROR_UNEXPECTED.get();
        } catch (ClientException e) {
            Throwable cause = e.getCause();
            errPrintln();
            if (cause instanceof ManagedObjectDecodingException) {
                displayManagedObjectDecodingException(this, (ManagedObjectDecodingException) cause);
            } else if (cause instanceof MissingMandatoryPropertiesException) {
                displayMissingMandatoryPropertyException(this, (MissingMandatoryPropertiesException) cause);
            } else if (cause instanceof OperationRejectedException) {
                displayOperationRejectedException(this, (OperationRejectedException) cause);
            } else {
                // Just display the default message.
                errPrintln(e.getMessageObject());
            }
            errPrintln();

            return ReturnCode.ERROR_UNEXPECTED.get();
        } catch (Exception e) {
            errPrintln(LocalizableMessage.raw(stackTraceToSingleLineString(e, true)));
            return ReturnCode.ERROR_UNEXPECTED.get();
        }
    }

    /**
     * Updates the command builder with the global options: script friendly, verbose, etc. for a given sub command. It
     * also adds systematically the no-prompt option.
     *
     * @param subCommand
     *            The sub command handler or common.
     * @return <T> The builded command.
     */
    CommandBuilder getCommandBuilder(final Object subCommand) {
        final String commandName = getScriptName();
        final SubCommandHandler handler;
        final String subCommandName;
        if (subCommand instanceof SubCommandHandler) {
            handler = (SubCommandHandler) subCommand;
            subCommandName = handler.getSubCommand().getName();
        } else {
            handler = null;
            subCommandName = (String) subCommand;
        }

        final CommandBuilder commandBuilder = new CommandBuilder(commandName, subCommandName);
        if (handler != null) {
            commandBuilder.append(handler.getCommandBuilder());
        }
        if (factory != null && factory.getContextCommandBuilder() != null) {
            commandBuilder.append(factory.getContextCommandBuilder());
        }
        if (verboseArgument.isPresent()) {
            commandBuilder.addArgument(verboseArgument);
        }
        if (scriptFriendlyArgument.isPresent()) {
            commandBuilder.addArgument(scriptFriendlyArgument);
        }

        commandBuilder.addArgument(noPromptArgument);

        if (propertiesFileArgument.isPresent()) {
            commandBuilder.addArgument(propertiesFileArgument);
        }
        if (noPropertiesFileArgument.isPresent()) {
            commandBuilder.addArgument(noPropertiesFileArgument);
        }

        return commandBuilder;
    }

    private String getScriptName() {
        final String commandName = System.getProperty(PROPERTY_SCRIPT_NAME);
        if (commandName != null && commandName.length() != 0) {
            return commandName;
        }
        return DSCONFIGTOOLNAME;
    }

    /**
     * Prints the contents of a command builder. This method has been created since SetPropSubCommandHandler calls it.
     * All the logic of DSConfig is on this method. It writes the content of the CommandBuilder to the standard output,
     * or to a file depending on the options provided by the user.
     *
     * @param commandBuilder
     *            the command builder to be printed.
     */
    void printCommandBuilder(CommandBuilder commandBuilder) {
        if (displayEquivalentArgument.isPresent()) {
            println();
            // We assume that the app we are running is this one.
            println(INFO_DSCFG_NON_INTERACTIVE.get(commandBuilder));
        }
        if (equivalentCommandFileArgument.isPresent()) {
            String file = equivalentCommandFileArgument.getValue();
            BufferedWriter writer = null;
            try {
                writer = new BufferedWriter(new FileWriter(file, true));

                if (!sessionStartTimePrinted) {
                    writer.write(SHELL_COMMENT_SEPARATOR + getSessionStartTimeMessage());
                    writer.newLine();
                    sessionStartTimePrinted = true;
                }

                sessionEquivalentOperationNumber++;
                writer.newLine();
                writer.write(SHELL_COMMENT_SEPARATOR
                        + INFO_DSCFG_EQUIVALENT_COMMAND_LINE_SESSION_OPERATION_NUMBER
                                .get(sessionEquivalentOperationNumber));
                writer.newLine();

                writer.write(SHELL_COMMENT_SEPARATOR + getCurrentOperationDateMessage());
                writer.newLine();

                writer.write(commandBuilder.toString());
                writer.newLine();
                writer.newLine();

                writer.flush();
            } catch (IOException ioe) {
                errPrintln(ERR_DSCFG_ERROR_WRITING_EQUIVALENT_COMMAND_LINE.get(file, ioe));
            } finally {
                closeSilently(writer);
            }
        }
    }

    /**
     * Returns the message to be displayed in the file with the equivalent command-line with information about when the
     * session started.
     *
     * @return the message to be displayed in the file with the equivalent command-line with information about when the
     *         session started.
     */
    private String getSessionStartTimeMessage() {
        final String date = formatDateTimeStringForEquivalentCommand(new Date(sessionStartTime));
        return INFO_DSCFG_SESSION_START_TIME_MESSAGE.get(getScriptName(), date).toString();
    }

    private void handleBatch(String[] args) {
        BufferedReader bReader = null;
        try {
            if (batchArgument.isPresent()) {
                bReader = new BufferedReader(new InputStreamReader(System.in));
            } else if (batchFileArgument.isPresent()) {
                final String batchFilePath = batchFileArgument.getValue().trim();
                bReader = new BufferedReader(new FileReader(batchFilePath));
            } else {
                throw new IllegalArgumentException("Either --" + OPTION_LONG_BATCH
                    + " or --" + OPTION_LONG_BATCH_FILE_PATH + " argument should have been set");
            }

            List<String> initialArgs = removeBatchArgs(args);

            // Split the CLI string into arguments array
            String command = "";
            String line;
            while ((line = bReader.readLine()) != null) {
                if ("".equals(line) || line.startsWith("#")) {
                    // Empty line or comment
                    continue;
                }
                // command split in several line support
                if (line.endsWith("\\")) {
                    // command is split into several lines
                    command += line.substring(0, line.length() - 1);
                    continue;
                } else {
                    command += line;
                }
                command = command.trim();
                // string between quotes support
                command = replaceSpacesInQuotes(command);
                // "\ " support
                command = command.replace("\\ ", "##");


                String displayCommand = command.replace("\\ ", " ");
                errPrintln(LocalizableMessage.raw(displayCommand));

                // Append initial arguments to the file line
                final String[] allArgsArray = buildCommandArgs(initialArgs, command);
                int exitCode = main(allArgsArray, getOutputStream(), getErrorStream());
                if (exitCode != ReturnCode.SUCCESS.get()) {
                    System.exit(filterExitCode(exitCode));
                }
                errPrintln();
                // reset command
                command = "";
            }
        } catch (IOException ex) {
            errPrintln(ERR_DSCFG_ERROR_READING_BATCH_FILE.get(ex));
        } finally {
            closeSilently(bReader);
        }
    }

    private String[] buildCommandArgs(List<String> initialArgs, String batchCommand) {
        final String[] commandArgs = toCommandArgs(batchCommand);
        final int length = commandArgs.length + initialArgs.size();
        final List<String> allArguments = new ArrayList<>(length);
        Collections.addAll(allArguments, commandArgs);
        allArguments.addAll(initialArgs);
        return allArguments.toArray(new String[length]);
    }

    private String[] toCommandArgs(String command) {
        String[] fileArguments = command.split("\\s+");
        for (int ii = 0; ii < fileArguments.length; ii++) {
            fileArguments[ii] = fileArguments[ii].replace("##", " ");
        }
        return fileArguments;
    }

    private List<String> removeBatchArgs(String[] args) {
        // Build a list of initial arguments,
        // removing the batch file option + its value
        final List<String> initialArgs = new ArrayList<>();
        Collections.addAll(initialArgs, args);
        for (Iterator<String> it = initialArgs.iterator(); it.hasNext();) {
            final String elem = it.next();
            if (batchArgument.isPresent()
                    && elem.contains(batchArgument.getLongIdentifier())) {
                it.remove();
                break;
            } else if (batchFileArgument.isPresent()
                    && (elem.startsWith("-" + batchFileArgument.getShortIdentifier())
                            || elem.contains(batchFileArgument.getLongIdentifier()))) {
                // Remove both the batch file arg and its value
                it.remove();
                it.next();
                it.remove();
                break;
            }
        }
        return initialArgs;
    }

    /** Replace spaces in quotes by "\ ". */
    private String replaceSpacesInQuotes(final String line) {
        StringBuilder newLine = new StringBuilder();
        boolean inQuotes = false;
        for (int ii = 0; ii < line.length(); ii++) {
            char ch = line.charAt(ii);
            if (ch == '\"' || ch == '\'') {
                inQuotes = !inQuotes;
                continue;
            }
            if (inQuotes && ch == ' ') {
                newLine.append("\\ ");
            } else {
                newLine.append(ch);
            }
        }
        return newLine.toString();
    }
}
