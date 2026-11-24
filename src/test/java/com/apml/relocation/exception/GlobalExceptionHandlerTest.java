package com.apml.relocation.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private static final String TYPE_BASE = "https://apml.example.com/problems/";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @SuppressWarnings("unused")
    void sampleHandlerMethod(String argument) {
        // only used to build a MethodParameter for MethodArgumentNotValidException
    }

    @Test
    void notFoundMapsTo404() {
        ProblemDetail pd = handler.handleDomain(new ResourceNotFoundException("Consignment", "X1"));
        assertThat(pd.getStatus()).isEqualTo(404);
        assertThat(pd.getDetail()).isEqualTo("Consignment not found: X1");
        assertThat(pd.getType()).hasToString(TYPE_BASE + "resource-not-found");
        assertThat(pd.getTitle()).isEqualTo("Not Found");
    }

    @Test
    void duplicateMapsTo409() {
        DuplicateResourceException ex = new DuplicateResourceException("dup");
        assertThat(handler.handleDomain(ex).getStatus()).isEqualTo(409);
        assertThat(ex.getCode()).isEqualTo("duplicate-resource");
        assertThat(ex.getStatus().value()).isEqualTo(409);
    }

    @Test
    void businessRuleAndClaimRejectionMapTo422() {
        assertThat(handler.handleDomain(new BusinessRuleException("rule")).getStatus()).isEqualTo(422);
        assertThat(handler.handleDomain(new ClaimRejectedException("late")).getType())
                .hasToString(TYPE_BASE + "claim-rejected");
    }

    @Test
    void invalidTransitionExposesFromAndTo() {
        ProblemDetail pd = handler.handleDomain(new InvalidStateTransitionException(ConsignmentStatus.QUOTED, ConsignmentStatus.CLOSED));
        assertThat(pd.getStatus()).isEqualTo(409);
        assertThat(pd.getProperties()).containsEntry("from", ConsignmentStatus.QUOTED).containsEntry("to", ConsignmentStatus.CLOSED);
    }

    @Test
    void insufficientPaymentExposesAmounts() {
        ProblemDetail pd = handler.handleDomain(
                new InsufficientPaymentException("short", new BigDecimal("700"), new BigDecimal("500")));
        assertThat(pd.getStatus()).isEqualTo(402);
        assertThat(pd.getProperties()).containsEntry("requiredAmount", new BigDecimal("700"))
                .containsEntry("paidAmount", new BigDecimal("500"))
                .containsEntry("shortfall", new BigDecimal("200"));
    }

    @Test
    void complianceViolationExposesViolationList() {
        ComplianceViolationException ex = new ComplianceViolationException(List.of("a", "b"));
        ProblemDetail pd = handler.handleDomain(ex);
        assertThat(pd.getStatus()).isEqualTo(422);
        assertThat(pd.getProperties()).containsEntry("violations", List.of("a", "b"));
        assertThat(ex.getViolations()).containsExactly("a", "b");
        assertThat(ex.getMessage()).contains("2 issue(s)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void beanValidationFailuresListFieldErrorsKeepingTheFirstMessage() throws NoSuchMethodException {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "target");
        result.addError(new FieldError("target", "customerName", "must not be blank"));
        result.addError(new FieldError("target", "customerName", "second message"));
        result.addError(new FieldError("target", "mobile", "invalid"));
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("sampleHandlerMethod", String.class), 0);

        ProblemDetail pd = handler.handleValidation(new MethodArgumentNotValidException(parameter, result));

        assertThat(pd.getStatus()).isEqualTo(400);
        assertThat(pd.getProperties()).containsKey("errors");
        Map<String, String> errors = (Map<String, String>) pd.getProperties().get("errors");
        assertThat(errors)
                .containsEntry("customerName", "must not be blank")
                .containsEntry("mobile", "invalid")
                .hasSize(2);
    }

    @Test
    void constraintViolationMapsTo400() {
        ProblemDetail pd = handler.handleConstraint(new ConstraintViolationException("bad value", Set.of()));
        assertThat(pd.getStatus()).isEqualTo(400);
        assertThat(pd.getDetail()).isEqualTo("bad value");
    }

    @Test
    void unreadableBodyMapsTo400() {
        ProblemDetail pd = handler.handleUnreadable(new HttpMessageNotReadableException("broken", mock(HttpInputMessage.class)));
        assertThat(pd.getStatus()).isEqualTo(400);
        assertThat(pd.getType()).hasToString(TYPE_BASE + "malformed-request");
    }

    @Test
    void optimisticLockMapsTo409() {
        ProblemDetail pd = handler.handleOptimisticLock(new ObjectOptimisticLockingFailureException(Object.class, 1L));
        assertThat(pd.getStatus()).isEqualTo(409);
        assertThat(pd.getType()).hasToString(TYPE_BASE + "concurrent-modification");
    }

    @Test
    void integrityViolationMapsTo409WithoutLeakingInternals() {
        ProblemDetail pd = handler.handleIntegrity(new DataIntegrityViolationException("outer", new IllegalStateException("root cause")));
        assertThat(pd.getStatus()).isEqualTo(409);
        assertThat(pd.getDetail()).doesNotContain("root cause");
    }
}
