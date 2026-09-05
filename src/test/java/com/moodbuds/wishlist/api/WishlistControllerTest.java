package com.moodbuds.wishlist.api;

import static com.moodbuds.wishlist.api.WishlistDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.moodbuds.wishlist.WishlistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WishlistController.class)
@AutoConfigureMockMvc(addFilters = false)
class WishlistControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean WishlistService wishlist;

    @Test
    void returnsCustomerWishlist() throws Exception {
        var item = new WishlistItemResponse(11, 21, "MB-21", "blue-tee", "Blue Tee", "M",
                1_000, 800L, 800, "/blue.jpg", true, true, Instant.parse("2026-08-29T10:00:00Z"));
        when(wishlist.get(7)).thenReturn(new WishlistResponse(3L, 1, List.of(item)));
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("customerId")).thenReturn(7L);

        var response = new WishlistController(wishlist).get(jwt);
        assertThat(response.itemCount()).isEqualTo(1);
        assertThat(response.items().getFirst().productSlug()).isEqualTo("blue-tee");
        assertThat(response.items().getFirst().effectivePrice()).isEqualTo(800);
    }

    @Test
    void rejectsInvalidWishlistSize() throws Exception {
        mockMvc.perform(post("/api/v1/customer/wishlist/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productSlug\":\"blue-tee\",\"size\":\"INVALID\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsMoveQuantityAboveLimit() throws Exception {
        mockMvc.perform(post("/api/v1/customer/wishlist/items/11/move-to-cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"size\":\"M\",\"quantity\":11}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
    }
}
