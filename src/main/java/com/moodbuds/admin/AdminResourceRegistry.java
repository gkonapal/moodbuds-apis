package com.moodbuds.admin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.moodbuds.common.ApiException;
import org.springframework.stereotype.Component;

@Component
public class AdminResourceRegistry {
    private final Map<String, ResourceDefinition> resources = new LinkedHashMap<>();

    public AdminResourceRegistry() {
        register(new ResourceDefinition("categories", "categories", Set.of("name","slug","is_active"), "name", true, true, false));
        register(new ResourceDefinition("subcategories", "subcategories", Set.of("category_id","name","slug","is_active"), "name", true, true, false));
        register(new ResourceDefinition("gst-rates", "gst_rates", Set.of("name","rate_percentage","hsn_code","is_active"), "name", false, true, false));
        register(new ResourceDefinition("moods", "moods", Set.of("name","slug","tagline","personality_tagline","color","is_active"), "name", true, true, false));
        register(new ResourceDefinition("products", "products", Set.of("sku","name","slug","category_id","subcategory_id","gst_rate_id","description","fabric_details","color_name","price","discount_price","weight_grams","length_cm","width_cm","height_cm","is_featured","is_new_arrival","is_best_seller","return_window_days"), "name", true, true, true));
        register(new ResourceDefinition("quiz-paths", "quiz_paths", Set.of("path_key","name","description"), "name", false, false, false));
        register(new ResourceDefinition("quiz-questions", "quiz_questions", Set.of("question_text","question_order","path_key","is_active"), "question_text", false, true, false));
        register(new ResourceDefinition("quiz-options", "quiz_options", Set.of("question_id","option_key","option_text","routes_to_path","sort_order","is_active"), "option_text", false, true, false));
    }

    private void register(ResourceDefinition definition) { resources.put(definition.apiName(), definition); }

    ResourceDefinition require(String name) {
        var definition = resources.get(name);
        if (definition == null) throw ApiException.notFound("Admin resource");
        return definition;
    }
}
