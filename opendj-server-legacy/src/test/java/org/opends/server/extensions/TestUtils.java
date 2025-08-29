package org.opends.server.extensions;

import java.util.*;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ModificationType;
import org.opends.server.types.Modification;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;

public class TestUtils {
    public static final DN TEST_USER_DN = DN.valueOf("cn=Test User,ou=Users,dc=com,dc=example");
    public static final DN LEVEL1_DN = DN.valueOf("cn=Level1,ou=Groups,dc=com,dc=example");
    public static final DN LEVEL2_DN = DN.valueOf("cn=Level2,ou=Groups,dc=com,dc=example");

    public static StaticGroup createNestedTestGroup() {
        // Минимальная реализация для теста
        StaticGroup group = new StaticGroup();
    group.getMemberDNs().clear();
    group.getNestedGroups().clear();
    // Устанавливаем значения через рефлексию, если нужно, либо через конструктор/методы
    // Для теста достаточно очистить коллекции и использовать LEVEL2_DN
        return group;
    }

    public static List<Modification> createAddUserModifications() {
        Attribute attr = Attributes.create("member", TEST_USER_DN.toString());
        Modification mod = new Modification(ModificationType.ADD, attr);
        return Collections.singletonList(mod);
    }
}
