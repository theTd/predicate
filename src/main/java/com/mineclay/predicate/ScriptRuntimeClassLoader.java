package com.mineclay.predicate;

class ScriptRuntimeClassLoader extends ClassLoader {
    Class<?> scriptBaseClass;

    public ScriptRuntimeClassLoader(ClassLoader parent) {
        super(parent);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (scriptBaseClass != null && scriptBaseClass.getName().equals(name)) return scriptBaseClass;
        return super.findClass(name);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (scriptBaseClass != null && scriptBaseClass.getName().equals(name)) return scriptBaseClass;
        return super.loadClass(name, resolve);
    }
}
