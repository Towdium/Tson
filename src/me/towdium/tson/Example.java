package me.towdium.tson;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Author: Towdium
 * Date: 18-11-6
 */
public class Example {
    public static void main(String[] args) {
        String s = "{\"name\":\"Demo\", \"value\": 123}";
        Map<String, Object> o = Tson.load(s).as(HashMap.class);
        System.out.println(o);
        s = "{\"name\":\"Demo\", \"list\": [2.5e3, 0.12, 2]}";
        Clazz c = Tson.loadAs(s, Clazz.class);
        System.out.println(c);
    }

    static class Clazz {
        String name;
        List<Float> list;

        @Override
        public String toString() {
            return "Name: " + name + ", List: " + list.toString();
        }
    }
}
