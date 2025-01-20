////
  The contents of this file are subject to the terms of the Common Development and
  Distribution License (the License). You may not use this file except in compliance with the
  License.

  You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
  specific language governing permission and limitations under the License.

  When distributing Covered Software, include this CDDL Header Notice in each file and include
  the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
  Header, with the fields enclosed by brackets [] replaced by your own identifying
  information: "Portions Copyright [year] [name of copyright owner]".

  Copyright 2017 ForgeRock AS.
  Portions Copyright 2024-${year} 3A Systems LLC.
////

[#sec-locales-subtypes]
=== ${title}

${info}

[#supported-locales]
.${locales.title}

<#list locales.locales as locale>
${locale.language}::
${locale.tag}
+
${locale.oid}

</#list>

[#supported-language-subtypes]
.${subtypes.title}

<#list subtypes.locales?sort_by("language") as subtype>
* ${subtype.language}, ${subtype.tag}

</#list>

