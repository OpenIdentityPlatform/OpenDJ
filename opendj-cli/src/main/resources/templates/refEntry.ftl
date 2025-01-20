////
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
 # Portions Copyright 2024-${year} 3A Systems LLC.
 #
////

[#${name}-1]
= ${name}(1)

:doctype: manpage
:manmanual: Directory Server Tools
:mansource: OpenDJ

== Name
${name} - ${shortDesc}

== Synopsis
`${name}` <#if args??>`${args}`</#if>

[#${name}-description]
== ${descTitle}

${description?ensure_ends_with(".")}

<#if info??>${info}</#if>

<#if optionSection??>
${optionSection}
</#if>

<#if subcommands??>
${subcommands}
</#if>

<#if trailingSectionString??>
${trailingSectionString}
</#if>
