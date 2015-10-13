<#--
 # CDDL HEADER START
 #
 # The contents of this file are subject to the terms of the
 # Common Development and Distribution License, Version 1.0 only
 # (the "License").  You may not use this file except in compliance
 # with the License.
 #
 # You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 # or http://forgerock.org/license/CDDLv1.0.html.
 # See the License for the specific language governing permissions
 # and limitations under the License.
 #
 # When distributing Covered Code, include this CDDL HEADER in each
 # file and include the License file at legal-notices/CDDLv1_0.txt.
 # If applicable, add the following below this CDDL HEADER,
 # with the fields enclosed by brackets "[]" replaced
 # with your own identifying information:
 #
 #      Portions Copyright [yyyy] [name of copyright owner]
 #
 # CDDL HEADER END
 #
 #      Copyright 2015 ForgeRock AS.
 #
 #-->
<refsect2 xml:id="${id}">
  <title>${name}</title>

  <para>
   ${description?ensure_ends_with(".")}
  </para>

  <#if info??>${info}</#if>

  <#if options??>
    <refsect3 xml:id="${id}-options">
      <title>${optionsTitle}</title>

      <variablelist>
        <para>
         ${optionsIntro}
        </para>

        <#list options as option>

          <varlistentry>
            <term><option>${option.synopsis?xml}</option></term>
            <listitem>
             <para>
               ${option.description?ensure_ends_with(".")}
             </para>

             <#if option.info??>
               <#if option.info.usage??>${option.info.usage}</#if>

               <#if option.info.default??>
                  <para>
                    ${option.info.default}
                  </para>
               </#if>

               <#if option.info.doc??>${option.info.doc}</#if>
             </#if>
            </listitem>
          </varlistentry>

        </#list>
      </variablelist>
    </refsect3>
  </#if>

  <#if propertiesInfo??>
    ${propertiesInfo}
  </#if>
</refsect2>
