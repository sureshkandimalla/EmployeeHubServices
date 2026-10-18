package com.employeehub;

import com.employeehub.util.NullPropertyUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EmployeeService#saveEmployee relies on this to build the "ignore these"
 * list for BeanUtils.copyProperties, which is what makes an employee update
 * a *partial* update. If a non-null property were reported as null, a real
 * edit would be silently dropped; if a null one were missed, an unsent field
 * would be wiped.
 */
class NullPropertyUtilsTest {

    public static class Bean {
        private String name;
        private String nickname;
        private Integer count;
        private boolean flag;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
        public boolean isFlag() { return flag; }
        public void setFlag(boolean flag) { this.flag = flag; }
    }

    @Test
    @DisplayName("getNullPropertyNames: lists only the properties that are null")
    void listsOnlyNullProperties() {
        Bean bean = new Bean();
        bean.setName("Ada");

        List<String> nulls = Arrays.asList(NullPropertyUtils.getNullPropertyNames(bean));

        assertTrue(nulls.contains("nickname"));
        assertTrue(nulls.contains("count"));
        assertFalse(nulls.contains("name"));
    }

    @Test
    @DisplayName("getNullPropertyNames: never lists a primitive property, which can't be null")
    void neverListsPrimitives() {
        List<String> nulls = Arrays.asList(NullPropertyUtils.getNullPropertyNames(new Bean()));

        assertFalse(nulls.contains("flag"));
    }

    @Test
    @DisplayName("getNullPropertyNames: lists every nullable property when nothing is set")
    void listsAllWhenNothingSet() {
        List<String> nulls = Arrays.asList(NullPropertyUtils.getNullPropertyNames(new Bean()));

        assertTrue(nulls.containsAll(Arrays.asList("name", "nickname", "count")));
    }

    @Test
    @DisplayName("getNullPropertyNames: returns no nullable names when every property is set")
    void listsNoneWhenAllSet() {
        Bean bean = new Bean();
        bean.setName("Ada");
        bean.setNickname("A");
        bean.setCount(1);

        List<String> nulls = Arrays.asList(NullPropertyUtils.getNullPropertyNames(bean));

        assertFalse(nulls.contains("name"));
        assertFalse(nulls.contains("nickname"));
        assertFalse(nulls.contains("count"));
    }

    @Test
    @DisplayName("getNullPropertyNames: an empty string is a value, not a null, so it is not listed")
    void emptyStringIsNotNull() {
        Bean bean = new Bean();
        bean.setName("");

        List<String> nulls = Arrays.asList(NullPropertyUtils.getNullPropertyNames(bean));

        assertFalse(nulls.contains("name"));
    }

    @Test
    @DisplayName("getNullPropertyNames: works on a record, where every component is read-only")
    void worksOnRecords() {
        record Partial(String a, String b) { }

        List<String> nulls = Arrays.asList(NullPropertyUtils.getNullPropertyNames(new Partial("set", null)));

        assertTrue(nulls.contains("b"));
        assertFalse(nulls.contains("a"));
    }
}
