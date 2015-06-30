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
  ! You can also obtain a copy of the license at legal-notices/CC-BY-NC-ND.txt.
  ! See the License for the specific language governing permissions
  ! and limitations under the License.
  !
  ! If applicable, add the following below this CCPL HEADER, with the fields
  ! enclosed by brackets "[]" replaced with your own identifying information:
  !      Portions Copyright [yyyy] [name of copyright owner]
  !
  ! CCPL HEADER END
  !
  !      Copyright ${year} ForgeRock AS.
  !
-->
<table xml:id="table-global-acis"
       xmlns="http://docbook.org/ns/docbook" version="5.0" xml:lang="${lang}"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://docbook.org/ns/docbook
                           http://docbook.org/xml/5.0/xsd/docbook.xsd"
       pgwide="1">
 <title>${title}</title>

 <textobject>
  <para>
   ${summary}
  </para>
 </textobject>

 <tgroup cols="2">
  <colspec colnum="1" colwidth="1*"/>
  <colspec colnum="2" colwidth="2*" />

  <thead>
   <row>
    <entry>${descTitle}</entry>
    <entry>${defTitle}</entry>
   </row>
  </thead>

  <tbody>
   <#list acis?sort_by("description") as aci>
   <row valign="top">
    <entry>
     ${aci.description}<!-- In English in config.ldif by default -->
    </entry>
    <entry>
     <literal>${aci.definition}</literal>
    </entry>
   </row>
   </#list>
  </tbody>
 </tgroup>
</table>
