package com.codementor.apigateway.filter;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.web.server.ServerWebExchange;

public class DataBufferFactoryWrapper {
    private final DefaultDataBufferFactory factory;

    public DataBufferFactoryWrapper(ServerWebExchange exchange) {
        // Use response buffer factory when available to ensure compatibility
        this.factory = new DefaultDataBufferFactory();
    }

    public DataBuffer wrap(byte[] bytes) {
        return factory.wrap(bytes);
    }
}
