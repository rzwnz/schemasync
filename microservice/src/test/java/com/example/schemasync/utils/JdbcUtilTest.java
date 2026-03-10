package com.example.schemasync.utils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class JdbcUtilTest {

    @Test
    void validateConnection_invalid_throws() {
        assertThatThrownBy(() -> JdbcUtil.validateConnection("jdbc:invalid", "bad", "bad"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void validateConnection_validH2_succeeds() throws Exception {
        // H2 in-memory DB is always valid
        JdbcUtil.validateConnection("jdbc:h2:mem:jdbcutil_test;DB_CLOSE_DELAY=-1", "sa", "");
    }

    @Test
    void validateConnection_nullUrl_throws() {
        assertThatThrownBy(() -> JdbcUtil.validateConnection(null, "sa", ""))
                .isInstanceOf(Exception.class);
    }
} 