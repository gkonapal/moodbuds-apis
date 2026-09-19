package com.moodbuds.cart.api;

import static com.moodbuds.cart.api.CartDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.moodbuds.cart.CartService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CartController.class)
@AutoConfigureMockMvc(addFilters = false)
class CartControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean CartService cart;

    @Test
    void returnsAuthenticatedCartCount() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("customerId")).thenReturn(7L);
        when(cart.count(7)).thenReturn(new CartCountResponse(2, 5));

        var response = new CartController(cart).count(jwt);
        assertThat(response.distinctItemCount()).isEqualTo(2);
        assertThat(response.totalQuantity()).isEqualTo(5);
    }

    @Test
    void movesOwnedCartItemToWishlist() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("customerId")).thenReturn(7L);
        var emptyCart = new CartResponse(9L, 0, 0, List.of(),
                new CartTotals(0, 0, 0, 0, 0), Instant.parse("2026-09-19T07:00:00Z"));
        when(cart.moveToWishlist(7, 11)).thenReturn(
                new MoveToWishlistResponse(3, 21, "blue-tee", "M", true, emptyCart));

        var response = new CartController(cart).moveToWishlist(jwt, 11);

        assertThat(response.removedFromCart()).isTrue();
        assertThat(response.productSlug()).isEqualTo("blue-tee");
        assertThat(response.cart().items()).isEmpty();
    }

    @Test
    void rejectsAddWithoutSizeAndQuantity() throws Exception {
        mockMvc.perform(post("/api/v1/customer/cart/items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productSlug\":\"blue-tee\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsQuantityAboveCartLimit() throws Exception {
        mockMvc.perform(patch("/api/v1/customer/cart/items/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":11}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
    }
}
