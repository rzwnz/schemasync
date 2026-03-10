package com.example.schemasync.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DiffValidationServiceImplTest {
    private DiffValidationServiceImpl service;

    @BeforeEach
    void setup() {
        service = new DiffValidationServiceImpl();
        try {
            java.lang.reflect.Field url = DiffValidationServiceImpl.class.getDeclaredField("validationDbUrl");
            url.setAccessible(true);
            url.set(service, "jdbc:invalid");
            java.lang.reflect.Field user = DiffValidationServiceImpl.class.getDeclaredField("validationDbUser");
            user.setAccessible(true);
            user.set(service, "bad");
            java.lang.reflect.Field pass = DiffValidationServiceImpl.class.getDeclaredField("validationDbPass");
            pass.setAccessible(true);
            pass.set(service, "bad");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void validateDiff_invalidDb_returnsFailure() {
        Path fakePath = Path.of("/tmp/doesnotexist.xml");
        ValidationResult result = service.validateDiff(fakePath);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isNotEmpty();
    }

    @Test
    void validateDiff_invalidResource_returnsFailure() {
        Path fakePath = Path.of("/not/a/real/dir/doesnotexist.xml");
        ValidationResult result = service.validateDiff(fakePath);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("Resource access error");
    }
} 