package com.moodbuds.admin;

import java.util.List;
import java.util.Map;
import com.moodbuds.audit.AuditService;import com.moodbuds.auth.CurrentAdmin;
import org.springframework.jdbc.core.simple.JdbcClient;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.security.oauth2.jwt.Jwt;import org.springframework.transaction.annotation.Transactional;import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/quiz/options") @PreAuthorize("hasAuthority('quiz.manage') or hasRole('SUPER_ADMIN')")
public class QuizWeightController {
 private final JdbcClient jdbc;private final AuditService audit;public QuizWeightController(JdbcClient jdbc,AuditService audit){this.jdbc=jdbc;this.audit=audit;}
 @GetMapping("/{optionId}/weights") Object get(@PathVariable long optionId){return jdbc.sql("SELECT w.*,m.name AS mood_name FROM quiz_option_mood_weights w JOIN moods m ON m.id=w.mood_id WHERE option_id=:id ORDER BY score DESC").param("id",optionId).query().listOfRows();}
 @PutMapping("/{optionId}/weights") @Transactional Object put(@PathVariable long optionId,@RequestBody Weights body,@AuthenticationPrincipal Jwt jwt){jdbc.sql("DELETE FROM quiz_option_mood_weights WHERE option_id=:id").param("id",optionId).update();for(var w:body.weights())jdbc.sql("INSERT INTO quiz_option_mood_weights(option_id,mood_id,score) VALUES(:option,:mood,:score)").param("option",optionId).param("mood",w.moodId()).param("score",w.score()).update();audit.record(CurrentAdmin.id(jwt),"quiz.weights_updated","quiz_option",optionId,null,body);return get(optionId);}
 public record Weight(long moodId,int score){} public record Weights(List<Weight> weights){public Weights{weights=weights==null?List.of():List.copyOf(weights);}}
}
