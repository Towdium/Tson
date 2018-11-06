package me.towdium.tson;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Author: Towdium
 * Date: 18-11-5
 */
public class Tson {
    public static NObject load(String s) {
        return new NObject(trim(s), 0);
    }

    public static <T> T loadAs(String s, Class<T> c) {
        try {
            return load(s).as(c);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public static String trim(String s) {
        boolean escape = false;
        boolean quote = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!escape && c == '"') quote = !quote;
            if (quote || (c != ' ' && c != '\t' && c != '\n' && c != '\r')) sb.append(c);
            escape = !escape && c == '\\';
        }
        return sb.toString();
    }

    public static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\') {
                sb.append(c);
                continue;
            } else if (i + 1 >= s.length())
                throw new RuntimeException("Unexpected \\.");
            char ec = 0;
            char nc = s.charAt(i + 1);
            if (nc == 'u') {
                if (i + 5 >= s.length())
                    throw new RuntimeException("Unexpected \\u.");
                else {
                    String u = s.substring(i + 2, i + 6);
                    ec = (char) Integer.parseInt(u, 16);
                    i += 5;
                }
            } else {
                if (nc == 'r') ec = '\r';
                else if (nc == '\\') ec = '\\';
                else if (nc == 't') ec = '\t';
                else if (nc == 'b') ec = '\b';
                else if (nc == 'f') ec = '\f';
                else if (nc == 'n') ec = '\n';
                else if (nc == '\"') ec = '\"';
                else if (nc == '/') ec = '/';
                else throw new RuntimeException("Unrecognized \\" + ec + '.');
                i++;
            }
            sb.append(ec);
        }
        return sb.toString();
    }

    public static abstract class NValue {
        int start, end;

        static NValue parse(String s, int i) {
            char c = s.charAt(i);
            if (c == '{') return new NObject(s, i);
            if (c == '[') return new NArray(s, i);
            if (c == '"') return new NString(s, i);
            if (c == 'f' || c == 't' || c == 'n') return new NConst(s, i);
            else return new NNumber(s, i);
        }

        public abstract <T> T as(Type c) throws ReflectiveOperationException;
    }

    public static class NConst extends NValue {
        CONTENT content;

        NConst(String s, int i) {
            start = i;
            char c = s.charAt(i);
            if (c == 'f') content = CONTENT.FALSE;
            else if (c == 't') content = CONTENT.TRUE;
            else content = CONTENT.NULL;
            String match = content.match();
            if (i + match.length() >= s.length())
                throw new RuntimeException("Unexpected Constant");
            String sub = s.substring(start, start + match.length());
            if (!match.equals(sub))
                throw new RuntimeException("Unexpected Constant");
            end = start + match.length();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as(Type t) throws ReflectiveOperationException {
            if (!(t instanceof Class)) return null;
            Class c = (Class) t;
            if ((content == CONTENT.TRUE || content == CONTENT.FALSE)
                    && c.isAssignableFrom(Boolean.class)) {
                return (T) content.object();
            } else if (content == CONTENT.NULL && !c.isPrimitive()) {
                return (T) content.object();
            } else throw new ReflectiveOperationException("CONTENT not match.");
        }

        enum CONTENT {
            TRUE, FALSE, NULL;

            String match() {
                switch (this) {
                    case TRUE: return "true";
                    case FALSE: return "false";
                    case NULL: return "null";
                    default: throw new RuntimeException("Internal error.");
                }
            }

            Object object() {
                switch (this) {
                    case TRUE: return true;
                    case FALSE: return false;
                    case NULL: return null;
                    default: throw new RuntimeException("Internal error.");
                }
            }
        }
    }

    public static class NObject extends NValue {
        Map<String, NValue> values = new HashMap<>();

        NObject(String s, int i) {
            start = i;
            if (s.charAt(i) != '{')
                throw new RuntimeException("Expecting {");
            int current = i + 1;
            boolean comma = true;
            while (s.charAt(current) != '}') {
                if (!comma)
                    throw new RuntimeException("Expecting ,");
                NString ns = new NString(s, current);
                if (s.charAt(ns.end) != ':')
                    throw new RuntimeException("Expecting :");
                NValue nv = NValue.parse(s, ns.end + 1);
                current = nv.end;
                values.put(ns.str, nv);
                comma = s.charAt(current) == ',';
                if (comma) current++;
            }
            end = current + 1;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as(Type t) throws ReflectiveOperationException {
            if (t instanceof Class) {
                Class c = (Class) t;
                if (Map.class.isAssignableFrom(c)) {
                    Constructor<?> m = c.getDeclaredConstructor();
                    m.setAccessible(true);
                    Map ret = (Map) m.newInstance();
                    for (Map.Entry<String, NValue> i : values.entrySet())
                        ret.put(i.getKey(), i.getValue().as(Object.class));
                    return (T) ret;
                } else {
                    Constructor<?> m = c.getDeclaredConstructor();
                    m.setAccessible(true);
                    Object ret = m.newInstance();
                    for (Map.Entry<String, NValue> i : values.entrySet()) {
                        Field f = c.getDeclaredField(i.getKey());
                        Object o = null;
                        try {
                            o = i.getValue().as(f.getGenericType());
                        } catch (ReflectiveOperationException ignored) {
                        }
                        f.setAccessible(true);
                        f.set(ret, o);
                    }
                    return (T) ret;
                }
            } else if (t instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) t;
                if (((Class) pt.getRawType()).isAssignableFrom(HashMap.class)) {
                    Class c = (Class) pt.getActualTypeArguments()[0];
                    HashMap ret = new HashMap();
                    for (Map.Entry<String, NValue> i : values.entrySet())
                        ret.put(i.getKey(), i.getValue().as(c));
                    return (T) ret;
                }
            }
            throw new ReflectiveOperationException("Format not suitable.");
        }
    }

    public static class NString extends NValue {
        String str;

        NString(String s, int i) {
            boolean escape = false;
            start = i;
            i++;
            for (; i < s.length(); i++) {
                char c = s.charAt(i);
                escape = !escape && c == '\\';
                if (!escape && c == '"') {
                    end = i + 1;
                    str = escape(s.substring(start + 1, end - 1));
                    return;
                }
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as(Type t) throws ReflectiveOperationException {
            if (!(t instanceof Class)) return null;
            Class<?> c = (Class) t;
            if (c.isAssignableFrom(String.class)) return (T) str;
            else throw new ReflectiveOperationException("CONTENT not match.");
        }
    }

    public static class NArray extends NValue {
        List<NValue> values = new ArrayList<>();

        NArray(String s, int i) {
            start = i;
            if (s.charAt(i) != '[')
                throw new RuntimeException("Expecting [");
            int current = i + 1;
            boolean comma = true;
            while (s.charAt(current) != ']') {
                if (!comma)
                    throw new RuntimeException("Expecting ,");
                NValue nv = NValue.parse(s, current);
                current = nv.end;
                values.add(nv);
                comma = s.charAt(current) == ',';
                if (comma) current++;
            }
            end = current + 1;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as(Type t) throws ReflectiveOperationException {
            if (t instanceof Class) {
                Class<?> c = (Class) t;
                Class<?> comp = c.getComponentType();
                Object ret = Array.newInstance(comp, values.size());
                for (int i = 0; i < values.size(); i++)
                    Array.set(ret, i, values.get(i).as(comp));
                return (T) ret;
            } else if (t instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) t;
                if (((Class) pt.getRawType()).isAssignableFrom(ArrayList.class)) {
                    Class c = (Class) pt.getActualTypeArguments()[0];
                    ArrayList ret = new ArrayList();
                    for (NValue i : values) ret.add(i.as(c));
                    return (T) ret;
                }
            }
            throw new ReflectiveOperationException("Format not suitable.");
        }
    }

    public static class NNumber extends NValue {
        float num;

        NNumber(String s, int i) {
            start = i;
            for (; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == ',' || c == '}' || c == ']') {
                    end = i;
                    String n = s.substring(start, end);
                    num = Float.parseFloat(n);
                    return;
                }
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as(Type t) throws ReflectiveOperationException {
            if (!(t instanceof Class)) return null;
            Class<?> c = (Class) t;
            if (c.isAssignableFrom(Integer.class)) return (T) Integer.valueOf((int) num);
            else if (c.isAssignableFrom(Float.class)) return (T) Float.valueOf(num);
            else throw new ReflectiveOperationException("CONTENT not match.");
        }
    }
}
