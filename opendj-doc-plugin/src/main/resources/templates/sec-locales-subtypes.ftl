<?xml version="1.0" encoding="UTF-8"?>
<!--
  ! CCPL HEADER START
  !
  ! This work is licensed under the Creative Commons
  ! Attribution-NonCommercial-NoDerivs 3.0 Unported License.
  ! To view a copy of this license, visit
  ! http://creativecommons.org/licenses/by-nc-nd/3.0/
  ! or send a letter to Creative Commons, 444 Castro Street,
  ! Suite 900, Mountain View, California, 94041, USA.
  !
  ! You can also obtain a copy of the license at
  ! trunk/opendj/legal-notices/CC-BY-NC-ND.txt.
  ! See the License for the specific language governing permissions
  ! and limitations under the License.
  !
  ! If applicable, add the following below this CCPL HEADER, with the fields
  ! enclosed by brackets "[]" replaced with your own identifying information:
  !      Portions Copyright [yyyy] [name of copyright owner]
  !
  ! CCPL HEADER END
  !
  !      Copyright 2011-${year} ForgeRock AS.
  !
-->
<section xml:id="sec-locales-subtypes"
         xmlns="http://docbook.org/ns/docbook" version="5.0" xml:lang="${lang}"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://docbook.org/ns/docbook
                             http://docbook.org/xml/5.0/xsd/docbook.xsd">

 <title>${title}</title>

 <para>
  ${info}
 </para>

 <variablelist xml:id="supported-locales">
  <title>${locales.title}</title>
  <indexterm><primary>${locales.indexTerm}</primary></indexterm>

  <#list locales.locales as locale>
  <varlistentry>
   <term>${locale.language}</term>
   <listitem>
    <para>
     ${locale.tag}
    </para>

    <para>
     ${locale.oid}
    </para>
   </listitem>
  </varlistentry>
  </#list>

 </variablelist>

 <itemizedlist xml:id="supported-language-subtypes">
  <title>${subtypes.title}</title>
  <indexterm><primary>${subtypes.indexTerm}</primary></indexterm>

  <#list subtypes.locales?sort_by("language") as subtype>
  <listitem>
   <para>${subtype.language}, ${subtype.tag}</para>
  </listitem>
  </#list>

 </itemizedlist>

</section>
