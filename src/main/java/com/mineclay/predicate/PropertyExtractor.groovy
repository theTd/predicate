package com.mineclay.predicate

class PropertyExtractor {
    private List<PredicateMethodBase> bases
    final ThreadLocal<PropertyInterceptor> propertyInterceptor = new ThreadLocal<>()

    PropertyExtractor(List<PredicateMethodBase> bases) {
        this.bases = bases
    }

    // method delegation target
    Object findProperty(String propertyName) {
        def interceptor = propertyInterceptor.get()
        if (interceptor != null) {
            Object property = interceptor.getProperty(propertyName)
            if (property != null) return property
        }
        for (PredicateMethodBase base : bases) {
            Object property = base.getProperty(propertyName)
            if (property != null) return property
        }
        return null
    }
}
