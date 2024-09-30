<#--
 # The contents of this file are subject to the terms of the Common Development and
 # Distribution License (the License). You may not use this file except in compliance with the
 # License.
 #
 # You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 # specific language governing permission and limitations under the License.
 #
 # When distributing Covered Software, include this CDDL Header Notice in each file and include
 # the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 # Header, with the fields enclosed by brackets [] replaced by your own identifying
 # information: "Portions Copyright [year] [name of copyright owner]".
 #
 # Copyright 2015 ForgeRock AS.
 # Portions Copyright 2024 3A Systems LLC.
 #-->

[#${id}]
=== ${name}

${description?ensure_ends_with(".")}

<#if info??>${info}</#if>

<#if options??>
[#${id}-options]
==== ${optionsTitle}

--

<#list options as option>
`${option.synopsis?xml}`::
${option.description?ensure_ends_with(".")}
<#if option.info??>
+
<#if option.info.usage??>${option.info.usage}</#if>
<#if option.info.default??>
+
${option.info.default}
</#if>
<#if option.info.doc??>
+
${option.info.doc}
</#if>
</#if>
</#list>

--

</#if>

<#if propertiesInfo??>
${propertiesInfo}
</#if>
