package com.mineclay.predicate

class PropertyExtractor {
    private List<PredicateMethodBase> bases

    PropertyExtractor(List<PredicateMethodBase> bases) {
        this.bases = bases
    }

    Object findProperty(String propertyName) {
        for (PredicateMethodBase base : bases) {
            Object property = base.getProperty(propertyName);
            if (property != null) return property
        }
        return null
    }
}
