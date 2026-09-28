package org.example.orderservice.models.services.impl;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.example.orderservice.clients.ProductClient;
import org.example.orderservice.exceptions.ProductNotFoundException;
import org.example.orderservice.exceptions.ProductServiceException;
import org.example.orderservice.models.dto.responses.ProductResponse;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductGatewayService {

    private final ProductClient productClient;

    @CircuitBreaker(name = "productService", fallbackMethod = "getProductByIdFallback")
    public ProductResponse getProductById(Long productId) {
        try {
            ProductResponse product = productClient.getProductById(productId);
            if (product == null || product.price() == null) {
                throw new ProductNotFoundException(productId);
            }
            return product;
        } catch (FeignException e) {
            if (e.status() == 404) {
                throw new ProductNotFoundException(productId);
            }
            throw e;
        }
    }

    public ProductResponse getProductByIdFallback(Long productId, Throwable throwable) {
        if (throwable instanceof ProductNotFoundException pnfe) {
            throw pnfe;
        }
        if (throwable instanceof FeignException fe && fe.status() == 404) {
            throw new ProductNotFoundException(productId);
        }
        Throwable cause = throwable.getCause();
        if (cause instanceof ProductNotFoundException pnfe) {
            throw pnfe;
        }
        if (cause instanceof FeignException fe && fe.status() == 404) {
            throw new ProductNotFoundException(productId);
        }
        if (throwable instanceof ProductServiceException pse) {
            throw pse;
        }
        if (cause instanceof ProductServiceException pse) {
            throw pse;
        }
        throw new ProductServiceException("Product service is unavailable or returned an error: " + throwable.getMessage(), throwable);
    }
}
