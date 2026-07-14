package com.costco.foundation.integration.framework.gcp.scaling.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration for the Key Points feature.
 *
 * @param namespacePrefixes prefixes identifying user-defined namespaces that
 *                          should be included in Key Points calculations; only
 *                          namespaces starting with one of these are counted
 */
@ConfigurationProperties(prefix = "gke.key-points")
public record KeyPointsProperties(List<String> namespacePrefixes) {

    /** Defaults to an empty list when the property is absent. */
    public KeyPointsProperties {
        namespacePrefixes = namespacePrefixes != null ? namespacePrefixes : List.of();
    }
}