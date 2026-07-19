package com.hainabaichuan75.iac_p.vehicle.api;

import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

final class ModuleCollector {

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
                    if (!Module.class.isAssignableFrom(field.getType())) continue;
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

    static List<Module> collect(Object host) {
        var list = new ArrayList<Module>();
        for (var h : SLOTS.get(host.getClass())) {
            var c = (Module) h.get(host);
            if (c != null) list.add(c);
        }
        return list;
    }
}
