package org.example.orderservice.models.services.impl;

import lombok.RequiredArgsConstructor;
import org.example.orderservice.models.constants.OrderStatus;
import org.example.orderservice.models.dto.requests.CreateOrderDetailRequest;
import org.example.orderservice.models.dto.requests.CreateOrderRequest;
import org.example.orderservice.models.dto.responses.OrderDetailResponse;
import org.example.orderservice.models.dto.responses.OrderResponse;
import org.example.orderservice.models.dto.responses.ProductResponse;
import org.example.orderservice.models.entities.Order;
import org.example.orderservice.models.entities.OrderDetail;
import org.example.orderservice.models.repositories.OrderDetailRepository;
import org.example.orderservice.models.repositories.OrderRepository;
import org.example.orderservice.models.services.OrderService;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final String ORDER_CREATED_TOPIC = "order-created";

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final ProductGatewayService productGatewayService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<ProcessedItem> processedItems = new ArrayList<>();

        for (CreateOrderDetailRequest item : request.items()) {
            ProductResponse product = productGatewayService.getProductById(item.productId());

            Double unitPrice = product.price();
            BigDecimal subtotalBd = BigDecimal.valueOf(unitPrice).multiply(BigDecimal.valueOf(item.quantity()));
            totalAmount = totalAmount.add(subtotalBd);

            processedItems.add(new ProcessedItem(
                    item.productId(),
                    product.name(),
                    item.quantity(),
                    unitPrice,
                    subtotalBd.doubleValue()
            ));
        }

        Order order = Order.builder()
                .customerName(request.customerName())
                .total(totalAmount.doubleValue())
                .status(OrderStatus.PENDING)
                .build();
        Order savedOrder = orderRepository.save(order);

        List<OrderDetail> orderDetails = new ArrayList<>();
        for (ProcessedItem item : processedItems) {
            OrderDetail detail = OrderDetail.builder()
                    .order(savedOrder)
                    .productId(item.productId())
                    .quantity(item.quantity())
                    .unitPrice(item.unitPrice())
                    .build();
            orderDetails.add(detail);
        }

        List<OrderDetail> savedDetails = orderDetailRepository.saveAll(orderDetails);
        if (savedDetails.isEmpty()) {
            savedDetails = orderDetails;
        }

        List<OrderDetailResponse> detailResponses = new ArrayList<>();
        for (int i = 0; i < savedDetails.size(); i++) {
            OrderDetail detail = savedDetails.get(i);
            ProcessedItem item = processedItems.get(i);
            detailResponses.add(new OrderDetailResponse(
                    detail.getId(),
                    detail.getProductId(),
                    item.productName(),
                    detail.getQuantity(),
                    detail.getUnitPrice(),
                    item.subtotal()
            ));
        }

        kafkaTemplate.send(ORDER_CREATED_TOPIC, request.customerEmail());

        return new OrderResponse(
                savedOrder.getId(),
                savedOrder.getCustomerName(),
                savedOrder.getTotal(),
                savedOrder.getStatus(),
                detailResponses
        );
    }

    private record ProcessedItem(
            Long productId,
            String productName,
            Integer quantity,
            Double unitPrice,
            Double subtotal
    ) {
    }
}
