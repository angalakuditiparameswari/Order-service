package com.example.orderservice.service;

import com.example.orderservice.dto.InventoryResponse;
import com.example.orderservice.dto.OrderItemsDto;
import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderItems;
import com.example.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClient;

    public void placeOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderItems> orderLineItems = orderRequest.getOrderItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .toList();

        order.setOrderItemsList(orderLineItems);

        List<String> skuCodes = order.getOrderItemsList().stream().map(OrderItems::getSkuCode).toList();

        InventoryResponse[] result = webClient.build().get()
                .uri("http://localhost:5555/api/inventory", uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();


        boolean allInStock = Arrays.stream(result).allMatch(InventoryResponse::isInStock);

        if(allInStock){
            orderRepository.save(order);
            webClient.build().put()
                    .uri("http://Inventory-service/api/inventory/update")
                    .bodyValue(orderRequest)
                    .retrieve()
                    .toBodilessEntity()
                    .subscribe(
                            done->{},
                            error -> log.error("inventory not updated", error)
                    );
        }

        else
            throw new IllegalArgumentException("Not in stock");
    }

    private OrderItems mapToDto(OrderItemsDto orderLineItemsDto) {
        OrderItems orderLineItems = new OrderItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }
}
