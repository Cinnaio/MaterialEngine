package com.github.cinnaio.materiaengine.util;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HarvestApiCompatibilityTest {
    @Test
    void resolvesCompileTimeCraftEngine268Signatures() throws Exception {
        Object access = resolve(getClass().getClassLoader());
        assertFalse(holderContext(access));
    }

    @Test
    void resolvesInstalledCraftEngineSignaturesWhenJarProvided() throws Exception {
        String jar = System.getProperty("craftengine.runtimeJar", "");
        assumeTrue(!jar.isBlank(), "Supply -PcraftEngineRuntimeJar to inspect a server CraftEngine jar");
        URL classes = CraftEngineHook.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{classes, Path.of(jar).toUri().toURL()}, getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("net.momirealms.craftengine.") || name.startsWith(CraftEngineHook.class.getName())) {
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> type = findLoadedClass(name);
                        if (type == null) {
                            try { type = findClass(name); }
                            catch (ClassNotFoundException absent) { return super.loadClass(name, resolve); }
                        }
                        if (resolve) resolveClass(type);
                        return type;
                    }
                }
                return super.loadClass(name, resolve);
            }
        }) {
            assertNotNull(resolve(loader));
        }
    }

    private Object resolve(ClassLoader loader) throws Exception {
        Class<?> access = Class.forName(CraftEngineHook.class.getName() + "$HarvestAccess", true, loader);
        var resolve = access.getDeclaredMethod("resolve");
        resolve.setAccessible(true);
        return resolve.invoke(null);
    }

    private boolean holderContext(Object access) throws Exception {
        var accessor = access.getClass().getDeclaredMethod("holderContext");
        accessor.setAccessible(true);
        return (boolean) accessor.invoke(access);
    }
}
