package com.campustrade.platform.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AppPropertiesBindingTest {

    @Test
    void emptyReviewerConfigurationBindsToNoReviewers() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.image-audit.reviewer-user-ids", "");

        AppProperties properties = Binder.get(environment)
                .bind("app", Bindable.of(AppProperties.class))
                .orElseGet(AppProperties::new);

        assertTrue(properties.getImageAudit().getReviewerUserIds().isEmpty());
    }
}
