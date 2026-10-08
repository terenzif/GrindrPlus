package com.gpp.utils.compat;

import java.lang.reflect.Field;

/** Java bridge so field getters expose platform Object (Kotlin Any!). */
public final class ReflectFields {
    private ReflectFields() {}

    public static Object getObjectField(Object obj, String fieldName) throws Exception {
        Class<?> c = obj.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f.get(obj);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldError(obj.getClass().getName() + "#" + fieldName);
    }

    public static int getIntField(Object obj, String fieldName) throws Exception {
        Class<?> c = obj.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f.getInt(obj);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldError(obj.getClass().getName() + "#" + fieldName);
    }
}
