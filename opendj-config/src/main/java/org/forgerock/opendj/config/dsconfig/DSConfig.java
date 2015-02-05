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
 *      Portions Copyright 2012-2015 ForgeRock AS
 */
package org.forgerock.opendj.config.dsconfig;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.CliMessages.*;
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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
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
import org.forgerock.opendj.config.PropertyDefinition;
import org.forgerock.opendj.config.PropertyDefinitionVisitor;
import org.forgerock.opendj.config.PropertyOption;
import org.forgerock.opendj.config.RelationDefinition;
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

    // FIXME: I18n support. Today all the strings are hardcoded in this file
    private final class DSConfigSubCommandUsageHandler implements SubCommandUsageHandler {

        private static final String ALLOW_UNLIMITED = "A value of \"-1\" or \"unlimited\" for no limit.";
        private static final String ACI_SYNTAX_REL_URL =
            "<link"
                + " xlink:show=\"new\""
                + " xlink:href=\"admin-guide#about-acis\""
                + " xlink:role=\"http://docbook.org/xlink/role/olink\">"
                + "<citetitle>About Access Control Instructions</citetitle></link>";
        private static final String DURATION_SYNTAX_REL_URL =
            "  <itemizedlist>"
                + "    <para>Some property values take a time duration. Durations are expressed"
                + "    as numbers followed by units. For example <literal>1 s</literal> means"
                + "    one second, and <literal>2 w</literal> means two weeks. Some durations"
                + "    have minimum granularity or maximum units, so you cannot necessary specify"
                + "    every duration in milliseconds or weeks for example. Some durations allow"
                + "    you to use a special value to mean unlimited. Units are specified as"
                + "    follows.</para>"
                + "    <listitem><para><literal>ms</literal>: milliseconds</para></listitem>"
                + "    <listitem><para><literal>s</literal>: seconds</para></listitem>"
                + "    <listitem><para><literal>m</literal>: minutes</para></listitem>"
                + "    <listitem><para><literal>h</literal>: hours</para></listitem>"
                + "    <listitem><para><literal>d</literal>: days</para></listitem>"
                + "    <listitem><para><literal>w</literal>: weeks</para></listitem>"
                + "  </itemizedlist>";

        /** {@inheritDoc} */
        @Override
        public void appendUsage(StringBuilder sb, SubCommand sc, String argLongID) {
            final SubCommandHandler sch = handlers.get(sc);
            if (sch instanceof HelpSubCommandHandler) {
                return;
            }
            final RelationDefinition<?, ?> rd = getRelationDefinition(sch);
            final AbstractManagedObjectDefinition<?, ?> defn = rd.getChildDefinition();
            final List<PropertyDefinition<?>> props =
                    new ArrayList<PropertyDefinition<?>>(defn.getAllPropertyDefinitions());
            Collections.sort(props);

            final String propPrefix = getScriptName() + "-" + sc.getName() + "-" + argLongID + "-";
            sb.append(EOL);
            toSimpleList(props, propPrefix, sb);
            sb.append(EOL);
            toVariableList(props, defn, propPrefix, sb);
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

        private void toSimpleList(List<PropertyDefinition<?>> props, String propPrefix, StringBuilder b) {
            b.append("    <simplelist>").append(EOL);
            for (PropertyDefinition<?> prop : props) {
                b.append("      <member><xref linkend=\"")
                    .append(propPrefix).append(prop.getName()).append("\" /></member>").append(EOL);
            }
            b.append("    </simplelist>").append(EOL);
        }

        private void toVariableList(List<PropertyDefinition<?>> props, AbstractManagedObjectDefinition<?, ?> defn,
                String propPrefix, StringBuilder b) {
            final String indent = "            ";
            b.append("    <variablelist>").append(EOL);
            for (PropertyDefinition<?> prop : props) {
                b.append("      <varlistentry xml:id=\"")
                    .append(propPrefix).append(prop.getName()).append("\">").append(EOL);
                b.append("        <term>").append(prop.getName()).append("</term>").append(EOL);
                b.append("        <listitem>").append(EOL);
                b.append("          <variablelist>").append(EOL);
                appendVarlistentry(b, "Description", getDescriptionString(prop), indent);
                appendVarlistentry(b, "Default Value", getDefaultBehaviorString(prop), indent);
                appendAllowedValues(b, prop, indent);
                appendVarlistentry(b, "Multi-valued", getYN(prop, MULTI_VALUED), indent);
                appendVarlistentry(b, "Required", getYN(prop, MANDATORY), indent);
                appendVarlistentry(b, "Admin Action Required", getAdminActionRequired(prop, defn), indent);
                appendVarlistentry(b, "Advanced Property", getYNAdvanced(prop, ADVANCED), indent);
                appendVarlistentry(b, "Read-only", getYN(prop, READ_ONLY), indent);
                b.append("          </variablelist>").append(EOL);
                b.append("        </listitem>").append(EOL);
                b.append("      </varlistentry>").append(EOL);
            }
            b.append("    </variablelist>").append(EOL);
        }

        private StringBuilder appendVarlistentry(StringBuilder b, String term, Object para, String indent) {
            b.append(indent).append("<varlistentry>").append(EOL);
            b.append(indent).append("  <term>").append(term).append("</term>").append(EOL);
            b.append(indent).append("  <listitem>").append(EOL);
            b.append(indent).append("    <para>").append(para).append("</para>").append(EOL);
            b.append(indent).append("  </listitem>").append(EOL);
            b.append(indent).append("</varlistentry>").append(EOL);
            return b;
        }

        private void appendAllowedValues(StringBuilder b, PropertyDefinition<?> prop, String indent) {
            b.append(indent).append("<varlistentry>").append(EOL);
            b.append(indent).append("  <term>").append("Allowed Values").append("</term>").append(EOL);
            if (prop instanceof EnumPropertyDefinition) {
                b.append(indent).append("  <listitem>").append(EOL);
                b.append(indent).append("    <variablelist>").append(EOL);
                appendSyntax(b, prop, indent + "      ");
                b.append(indent).append("    </variablelist>").append(EOL);
                b.append(indent).append("  </listitem>").append(EOL);
            } else if (prop instanceof BooleanPropertyDefinition) {
                b.append(indent).append("  <listitem>").append(EOL);
                b.append(indent).append("    <para>true</para>").append(EOL);
                b.append(indent).append("    <para>false</para>").append(EOL);
                b.append(indent).append("  </listitem>").append(EOL);
            } else {
                b.append(indent).append("  <listitem>").append(EOL);
                b.append(indent).append("    <para>");
                appendSyntax(b, prop, indent);
                b.append("</para>").append(EOL);
                b.append(indent).append("  </listitem>").append(EOL);
            }
            b.append(indent).append("</varlistentry>").append(EOL);
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
                    action.append("The ").append(defn.getUserFriendlyName())
                             .append(" must be disabled and re-enabled for changes to this setting to take effect");
                } else if (actionType == Type.SERVER_RESTART) {
                    action.append("Restart the server");
                } else if (actionType == Type.NONE) {
                    action.append("None");
                }
                if (synopsis != null) {
                    if (action.length() > 0) {
                        action.append(". ");
                    }
                    action.append(synopsis);
                }
                return action.toString();
            }
            return "None";
        }

        private String getYN(PropertyDefinition<?> prop, PropertyOption option) {
            return prop.hasOption(option) ? "Yes" : "No";
        }

        private String getYNAdvanced(PropertyDefinition<?> prop, PropertyOption option) {
            return prop.hasOption(option) ? "Yes (Use --advanced in interactive mode.)" : "No";
        }

        private String getDefaultBehaviorString(PropertyDefinition<?> prop) {
            DefaultBehaviorProvider<?> defaultBehavior = prop.getDefaultBehaviorProvider();
            if (defaultBehavior instanceof UndefinedDefaultBehaviorProvider) {
                return "None";
            } else if (defaultBehavior instanceof DefinedDefaultBehaviorProvider) {
                DefinedDefaultBehaviorProvider<?> behavior = (DefinedDefaultBehaviorProvider<?>) defaultBehavior;
                final StringBuilder res = new StringBuilder();
                for (Iterator<String> it = behavior.getDefaultValues().iterator(); it.hasNext();) {
                    String str = it.next();
                    res.append(str).append(it.hasNext() ? "\n" : "");
                }
                return res.toString();
            } else if (defaultBehavior instanceof AliasDefaultBehaviorProvider) {
                AliasDefaultBehaviorProvider<?> behavior = (AliasDefaultBehaviorProvider<?>) defaultBehavior;
                return behavior.getSynopsis().toString();
            } else if (defaultBehavior instanceof RelativeInheritedDefaultBehaviorProvider) {
                final RelativeInheritedDefaultBehaviorProvider<?> behavior =
                        (RelativeInheritedDefaultBehaviorProvider<?>) defaultBehavior;
                return getDefaultBehaviorString(
                        behavior.getManagedObjectDefinition().getPropertyDefinition(behavior.getPropertyName()));
            } else if (defaultBehavior instanceof AbsoluteInheritedDefaultBehaviorProvider) {
                final AbsoluteInheritedDefaultBehaviorProvider<?> behavior =
                        (AbsoluteInheritedDefaultBehaviorProvider<?>) defaultBehavior;
                return getDefaultBehaviorString(
                        behavior.getManagedObjectDefinition().getPropertyDefinition(behavior.getPropertyName()));
            }
            return "";
        }

        private void appendSyntax(final StringBuilder b, PropertyDefinition<?> prop, final String indent) {
            // Create a visitor for performing syntax specific processing.
            PropertyDefinitionVisitor<String, Void> visitor = new PropertyDefinitionVisitor<String, Void>() {

                @Override
                public String visitACI(ACIPropertyDefinition prop, Void p) {
                    b.append(ACI_SYNTAX_REL_URL);
                    return null;
                }

                @Override
                public String visitAggregation(AggregationPropertyDefinition prop, Void p) {
                    final RelationDefinition<?, ?> rel = prop.getRelationDefinition();
                    final String linkStr = getLink(rel.getName() + ".html");
                    b.append("The DN of any ").append(linkStr).append(". ");
                    final LocalizableMessage synopsis = prop.getSourceConstraintSynopsis();
                    if (synopsis != null) {
                        b.append(synopsis);
                    }
                    return null;
                }

                @Override
                public String visitAttributeType(AttributeTypePropertyDefinition prop, Void p) {
                    b.append("The name of an attribute type defined in the server schema.");
                    return null;
                }

                @Override
                public String visitBoolean(BooleanPropertyDefinition prop, Void p) {
                    throw new RuntimeException("This case should be handled by the calling code.");
                }

                @Override
                public String visitClass(ClassPropertyDefinition prop, Void p) {
                    b.append("A java class that implements or extends the class(es) :")
                        .append(Utils.joinAsString(EOL, prop.getInstanceOfInterface()));
                    return null;
                }

                @Override
                public String visitDN(DNPropertyDefinition prop, Void p) {
                    final DN baseDN = prop.getBaseDN();
                    b.append("A valid DN.");
                    if (baseDN != null) {
                        b.append(baseDN);
                    }
                    return null;
                }

                @Override
                public String visitDuration(DurationPropertyDefinition prop, Void p) {
                    b.append(DURATION_SYNTAX_REL_URL).append(". ");
                    if (prop.isAllowUnlimited()) {
                        b.append(ALLOW_UNLIMITED).append(" ");
                    }
                    if (prop.getMaximumUnit() != null) {
                        b.append("Maximum unit is \"").append(prop.getMaximumUnit().getLongName()).append("\". ");
                    }
                    final DurationUnit baseUnit = prop.getBaseUnit();
                    b.append("Lower limit is ").append(valueOf(baseUnit, prop.getLowerLimit()))
                     .append(" ").append(baseUnit.getLongName()).append(". ");
                    if (prop.getUpperLimit() != null) {
                        b.append("Upper limit is ").append(valueOf(baseUnit, prop.getUpperLimit()))
                         .append(" ").append(baseUnit.getLongName()).append(". ");
                    }
                    return null;
                }

                private long valueOf(final DurationUnit baseUnit, long upperLimit) {
                    return Double.valueOf(baseUnit.fromMilliSeconds(upperLimit)).longValue();
                }

                @Override
                public String visitEnum(EnumPropertyDefinition prop, Void p) {
                    final Class<?> en = prop.getEnumClass();
                    final Object[] constants = en.getEnumConstants();
                    for (Object enumConstant : constants) {
                        final LocalizableMessage valueSynopsis = prop.getValueSynopsis((Enum) enumConstant);
                        appendVarlistentry(b, enumConstant.toString(), valueSynopsis, indent);
                    }
                    return null;
                }

                @Override
                public String visitInteger(IntegerPropertyDefinition prop, Void p) {
                    b.append("An integer value. Lower value is ").append(prop.getLowerLimit()).append(".");
                    if (prop.getUpperLimit() != null) {
                        b.append(" Upper value is ").append(prop.getUpperLimit()).append(".");
                    }
                    if (prop.isAllowUnlimited()) {
                        b.append(" ").append(ALLOW_UNLIMITED);
                    }
                    if (prop.getUnitSynopsis() != null) {
                        b.append(" Unit is ").append(prop.getUnitSynopsis()).append(".");
                    }
                    return null;
                }

                @Override
                public String visitIPAddress(IPAddressPropertyDefinition prop, Void p) {
                    b.append("An IP address");
                    return null;
                }

                @Override
                public String visitIPAddressMask(IPAddressMaskPropertyDefinition prop, Void p) {
                    b.append("An IP address mask");
                    return null;
                }

                @Override
                public String visitSize(SizePropertyDefinition prop, Void p) {
                    if (prop.getLowerLimit() != 0) {
                        b.append(" Lower value is ").append(prop.getLowerLimit()).append(".");
                    }
                    if (prop.getUpperLimit() != null) {
                        b.append(" Upper value is ").append(prop.getUpperLimit()).append(" .");
                    }
                    if (prop.isAllowUnlimited()) {
                        b.append(" ").append(ALLOW_UNLIMITED);
                    }
                    return null;
                }

                @Override
                public String visitString(StringPropertyDefinition prop, Void p) {
                    if (prop.getPatternSynopsis() != null) {
                        b.append(prop.getPatternSynopsis());
                    } else {
                        b.append("A String");
                    }
                    return null;
                }

                @Override
                public String visitUnknown(PropertyDefinition prop, Void p) {
                    b.append("Unknown");
                    return null;
                }
            };

            // Invoke the visitor against the property definition.
            prop.accept(visitor, null);
        }

        private String getLink(String target) {
            return " <xref linkend=" + target + " />";
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
                } else {
                    if (result.isSuccess() && isInteractive() && handler.isCommandBuilderUseful()) {
                        printCommandBuilder(getCommandBuilder(handler));
                    }
                    // Success or cancel.
                    app.println();
                    app.pressReturnToContinue();
                    return MenuResult.again();
                }
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

            final MenuBuilder<Integer> builder = new MenuBuilder<Integer>(app);

            builder.setTitle(INFO_DSCFG_HEADING_COMPONENT_MENU_TITLE.get(userFriendlyName));
            builder.setPrompt(INFO_DSCFG_HEADING_COMPONENT_MENU_PROMPT.get());

            if (lh != null) {
                final SubCommandHandlerMenuCallback callback = new SubCommandHandlerMenuCallback(lh);
                if (userFriendlyPluralName != null) {
                    builder.addNumberedOption(INFO_DSCFG_OPTION_COMPONENT_MENU_LIST_PLURAL.get(userFriendlyPluralName),
                            callback);
                } else {
                    builder.addNumberedOption(INFO_DSCFG_OPTION_COMPONENT_MENU_LIST_SINGULAR.get(userFriendlyName),
                            callback);
                }
            }

            if (ch != null) {
                final SubCommandHandlerMenuCallback callback = new SubCommandHandlerMenuCallback(ch);
                builder.addNumberedOption(INFO_DSCFG_OPTION_COMPONENT_MENU_CREATE.get(userFriendlyName), callback);
            }

            if (sh != null) {
                final SubCommandHandlerMenuCallback callback = new SubCommandHandlerMenuCallback(sh);
                if (userFriendlyPluralName != null) {
                    builder.addNumberedOption(INFO_DSCFG_OPTION_COMPONENT_MENU_MODIFY_PLURAL.get(userFriendlyName),
                            callback);
                } else {
                    builder.addNumberedOption(INFO_DSCFG_OPTION_COMPONENT_MENU_MODIFY_SINGULAR.get(userFriendlyName),
                            callback);
                }
            }

            if (dh != null) {
                final SubCommandHandlerMenuCallback callback = new SubCommandHandlerMenuCallback(dh);
                builder.addNumberedOption(INFO_DSCFG_OPTION_COMPONENT_MENU_DELETE.get(userFriendlyName), callback);
            }

            builder.addBackOption(true);
            builder.addQuitOption();

            this.menu = builder.toMenu();
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
        final DSConfig app = new DSConfig(System.in, outStream, errStream);
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
    private final Map<SubCommand, SubCommandHandler> handlers = new HashMap<SubCommand, SubCommandHandler>();

    /** Indicates whether or not a sub-command was provided. */
    private boolean hasSubCommand = true;

    /** The argument which should be used to read dsconfig commands from a file. */
    private StringArgument batchFileArgument;

    /**
     * The argument which should be used to request non interactive behavior.
     */
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
     * @param in
     *            The application input stream.
     * @param out
     *            The application output stream.
     * @param err
     *            The application error stream.
     */
    private DSConfig(InputStream in, OutputStream out, OutputStream err) {
        super(new PrintStream(out), new PrintStream(err));

        this.parser = new SubCommandArgumentParser(getClass().getName(), INFO_DSCFG_TOOL_DESCRIPTION.get(), false);
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
    private void initializeGlobalArguments(String[] args) throws ArgumentException {
        if (!globalArgumentsInitialized) {

            verboseArgument = CommonArguments.getVerbose();
            quietArgument = CommonArguments.getQuiet();
            scriptFriendlyArgument = CommonArguments.getScriptFriendly();
            noPromptArgument = CommonArguments.getNoPrompt();
            advancedModeArgument = CommonArguments.getAdvancedMode();
            showUsageArgument = CommonArguments.getShowUsage();

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

            Map<Tag, SortedSet<SubCommand>> groups = new TreeMap<Tag, SortedSet<SubCommand>>();
            SortedSet<SubCommand> allSubCommands = new TreeSet<SubCommand>(c);
            for (SubCommandHandler handler : handlerFactory.getAllSubCommandHandlers()) {
                SubCommand sc = handler.getSubCommand();

                handlers.put(sc, handler);
                allSubCommands.add(sc);

                // Add the sub-command to its groups.
                for (Tag tag : handler.getTags()) {
                    SortedSet<SubCommand> group = groups.get(tag);
                    if (group == null) {
                        group = new TreeSet<SubCommand>(c);
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
            initializeGlobalArguments(args);
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
        if (batchFileArgument.isPresent()) {
            handleBatchFile(args);
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
        if (quietArgument.isPresent() && verboseArgument.isPresent()) {
            throw conflictingArgs(quietArgument, verboseArgument);
        }

        if (batchFileArgument.isPresent() && !noPromptArgument.isPresent()) {
            final LocalizableMessage message = ERR_DSCFG_ERROR_QUIET_AND_INTERACTIVE_INCOMPATIBLE.get(
                    batchFileArgument.getLongIdentifier(), noPromptArgument.getLongIdentifier());
            throw new ArgumentException(message);
        }

        if (quietArgument.isPresent() && !noPromptArgument.isPresent()) {
            final LocalizableMessage message = ERR_DSCFG_ERROR_QUIET_AND_INTERACTIVE_INCOMPATIBLE.get(
                    quietArgument.getLongIdentifier(), noPromptArgument.getLongIdentifier());
            throw new ArgumentException(message);
        }

        if (scriptFriendlyArgument.isPresent() && verboseArgument.isPresent()) {
            throw conflictingArgs(scriptFriendlyArgument, verboseArgument);
        }

        if (noPropertiesFileArgument.isPresent() && propertiesFileArgument.isPresent()) {
            throw conflictingArgs(noPropertiesFileArgument, propertiesFileArgument);
        }
    }

    private ArgumentException conflictingArgs(Argument arg1, Argument arg2) {
        return new ArgumentException(ERR_TOOL_CONFLICTING_ARGS.get(arg1.getLongIdentifier(), arg2.getLongIdentifier()));
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

        final Set<RelationDefinition<?, ?>> relations = new TreeSet<RelationDefinition<?, ?>>(c);

        final Map<RelationDefinition<?, ?>, CreateSubCommandHandler<?, ?>> createHandlers
            = new HashMap<RelationDefinition<?, ?>, CreateSubCommandHandler<?, ?>>();

        final Map<RelationDefinition<?, ?>, DeleteSubCommandHandler> deleteHandlers
            = new HashMap<RelationDefinition<?, ?>, DeleteSubCommandHandler>();

        final Map<RelationDefinition<?, ?>, ListSubCommandHandler> listHandlers
            = new HashMap<RelationDefinition<?, ?>, ListSubCommandHandler>();

        final Map<RelationDefinition<?, ?>, GetPropSubCommandHandler> getPropHandlers
            = new HashMap<RelationDefinition<?, ?>, GetPropSubCommandHandler>();

        final Map<RelationDefinition<?, ?>, SetPropSubCommandHandler> setPropHandlers
            = new HashMap<RelationDefinition<?, ?>, SetPropSubCommandHandler>();

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
        final MenuBuilder<Integer> builder = new MenuBuilder<Integer>(app);

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
        } catch (ArgumentException e) {
            app.errPrintln(e.getMessageObject());
            return ReturnCode.ERROR_UNEXPECTED.get();
        } catch (ClientException e) {
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
     * @param <T>
     *            SubCommand type.
     * @param subCommand
     *            The sub command handler or common.
     * @return <T> The builded command.
     */
    <T> CommandBuilder getCommandBuilder(final T subCommand) {
        final String commandName = getScriptName();
        final String subCommandName;
        if (subCommand instanceof SubCommandHandler) {
            subCommandName = ((SubCommandHandler) subCommand).getSubCommand().getName();
        } else {
            subCommandName = (String) subCommand;
        }
        final CommandBuilder commandBuilder = new CommandBuilder(commandName, subCommandName);
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

    private void handleBatchFile(String[] args) {

        BufferedReader bReader = null;
        try {
            // Build a list of initial arguments,
            // removing the batch file option + its value
            final List<String> initialArgs = new ArrayList<String>();
            Collections.addAll(initialArgs, args);
            int batchFileArgIndex = -1;
            for (final String elem : initialArgs) {
                if (elem.startsWith("-" + OPTION_SHORT_BATCH_FILE_PATH) || elem.contains(OPTION_LONG_BATCH_FILE_PATH)) {
                    batchFileArgIndex = initialArgs.indexOf(elem);
                    break;
                }
            }
            if (batchFileArgIndex != -1) {
                // Remove both the batch file arg and its value
                initialArgs.remove(batchFileArgIndex);
                initialArgs.remove(batchFileArgIndex);
            }
            final String batchFilePath = batchFileArgument.getValue().trim();
            bReader = new BufferedReader(new FileReader(batchFilePath));
            String line;
            String command = "";
            // Split the CLI string into arguments array
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
                String displayCommand = new String(command);

                // "\ " support
                command = command.replace("\\ ", "##");
                displayCommand = displayCommand.replace("\\ ", " ");

                String[] fileArguments = command.split("\\s+");
                // reset command
                command = "";
                for (int ii = 0; ii < fileArguments.length; ii++) {
                    fileArguments[ii] = fileArguments[ii].replace("##", " ");
                }

                errPrintln(LocalizableMessage.raw(displayCommand));

                // Append initial arguments to the file line
                final List<String> allArguments = new ArrayList<String>();
                Collections.addAll(allArguments, fileArguments);
                allArguments.addAll(initialArgs);
                final String[] allArgsArray = allArguments.toArray(new String[] {});

                int exitCode = main(allArgsArray, getOutputStream(), getErrorStream());
                if (exitCode != ReturnCode.SUCCESS.get()) {
                    System.exit(filterExitCode(exitCode));
                }
                errPrintln();
            }
        } catch (IOException ex) {
            errPrintln(ERR_DSCFG_ERROR_READING_BATCH_FILE.get(ex));
        } finally {
            closeSilently(bReader);
        }
    }

    /** Replace spaces in quotes by "\ ". */
    private String replaceSpacesInQuotes(final String line) {
        String newLine = "";
        boolean inQuotes = false;
        for (int ii = 0; ii < line.length(); ii++) {
            char ch = line.charAt(ii);
            if (ch == '\"' || ch == '\'') {
                inQuotes = !inQuotes;
                continue;
            }
            if (inQuotes && ch == ' ') {
                newLine += "\\ ";
            } else {
                newLine += ch;
            }
        }
        return newLine;
    }
}
