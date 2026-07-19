package com.hainabaichuan75.iac_p.vehicle.api;

import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

final class ComponentCollector {

    private static final ClassValue<List<VarHandle>> SLOTS = new ClassValue<>() {
        @Override
        protected List<VarHandle> computeValue(@NotNull Class<?> type) {
            MethodHandles.Lookup lookup;
            try {
                lookup = MethodHandles.privateLookupIn(type, MethodHandles.lookup());
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            var list = new ArrayList<VarHandle>();
            for (var f = type; f != null && f != Object.class; f = f.getSuperclass()) {
                for (var field : f.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    if (!Component.class.isAssignableFrom(field.getType())) continue;
                    try {
                        list.add(lookup.unreflectVarHandle(field));
                    } catch (ReflectiveOperationException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            return List.copyOf(list);
        }
    };

    static List<Component> collect(Object host) {
        var list = new ArrayList<Component>();
        for (var h : SLOTS.get(host.getClass())) {
            var c = (Component) h.get(host);
            if (c != null) list.add(c);
        }
        return list;
    }
}
