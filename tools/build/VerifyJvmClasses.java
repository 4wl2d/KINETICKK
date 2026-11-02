// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import java.util.jar.JarFile;

/** Verifies optimized application bytecode without running static initializers or opening player data. */
class VerifyJvmClasses {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected the application uber-JAR path");
        int checked = 0;
        int failed = 0;
        try (var jar = new JarFile(args[0])) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var name = entries.nextElement().getName();
                if (!name.startsWith("kinetickk/") || !name.endsWith(".class")) continue;
                var className = name.substring(0, name.length() - 6).replace('/', '.');
                try {
                    var type = Class.forName(className, false, ClassLoader.getSystemClassLoader());
                    type.getDeclaredConstructors();
                    type.getDeclaredMethods();
                    type.getDeclaredFields();
                    checked++;
                } catch (LinkageError | ReflectiveOperationException failure) {
                    failed++;
                    System.err.println(className + ": " + failure);
                }
            }
        }
        System.out.println("Application classes verified=" + checked + " failed=" + failed);
        if (checked == 0 || failed != 0) throw new IllegalStateException("Application bytecode verification failed");
    }
}
