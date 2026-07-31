package org.dbsyncer.parser.error;

import org.springframework.context.ApplicationEvent;

public class CircuitBreakEvent extends ApplicationEvent {

    private final String mappingId;

    public CircuitBreakEvent(Object source, String mappingId) {
        super(source);
        this.mappingId = mappingId;
    }

    public String getMappingId() {
        return mappingId;
    }
}
