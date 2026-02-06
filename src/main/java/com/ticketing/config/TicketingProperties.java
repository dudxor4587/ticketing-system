package com.ticketing.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ticketing.queue")
@Getter
@Setter
public class TicketingProperties {

    private int maxConcurrent;
    private int tokenTtl;
}
