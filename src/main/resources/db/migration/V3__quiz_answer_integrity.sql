DELETE newer
FROM quiz_session_answers newer
JOIN quiz_session_answers older
  ON older.quiz_session_id = newer.quiz_session_id
 AND older.question_id = newer.question_id
 AND older.id < newer.id;

ALTER TABLE quiz_session_answers
    ADD CONSTRAINT uq_quiz_session_question UNIQUE (quiz_session_id, question_id);

CREATE INDEX idx_quiz_sessions_started_completed
    ON quiz_sessions (started_at, is_completed);
