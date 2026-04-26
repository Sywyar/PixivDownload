package top.sywyar.pixivdownload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.ConstraintViolation;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import top.sywyar.pixivdownload.download.response.ErrorResponse;
import top.sywyar.pixivdownload.i18n.AppMessages;
import top.sywyar.pixivdownload.i18n.LocalizedException;

import java.io.IOException;
import java.util.Locale;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final AppMessages messages;

    @ExceptionHandler(LocalizedException.class)
    public ResponseEntity<ErrorResponse> handleLocalized(LocalizedException e, Locale locale) {
        String message = messages.getOrDefault(locale, e.getMessageCode(), e.getDefaultMessage(), e.getMessageArgs());
        String logDetail = messages.getOrDefault(Locale.getDefault(), e.getMessageCode(), e.getDefaultMessage(), e.getMessageArgs());
        log.warn(logMessage("error.log.request.failed", logDetail));
        return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(message));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException e) {
        log.warn(logMessage("error.log.request.failed", fallbackLogDetail(e.getReason(), e.getStatusCode().toString())));
        return ResponseEntity.status(e.getStatusCode()).body(new ErrorResponse(e.getReason()));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurity(SecurityException e) {
        log.warn(logMessage("error.log.security.failed", fallbackLogDetail(e.getMessage(), e.getClass().getSimpleName())));
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e, Locale locale) {
        String message = buildValidationMessage(e, locale, false);
        String logDetail = buildValidationMessage(e, Locale.getDefault(), true);
        log.warn(logMessage("error.log.request.param.validation-failed", logDetail));
        return ResponseEntity.badRequest().body(new ErrorResponse(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e, Locale locale) {
        String message = messages.getOrDefault(locale, "error.request.body.invalid", "请求体格式错误");
        log.warn(logMessage("error.log.request.body.parse-failed", fallbackLogDetail(e.getMessage(), e.getClass().getSimpleName())));
        return ResponseEntity.badRequest().body(new ErrorResponse(message));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponse> handleIOException(IOException e, Locale locale) {
        String message = messages.getOrDefault(locale, "error.io", "服务器 IO 错误: {0}", e.getMessage());
        log.error(logMessage("error.log.io.exception", fallbackLogDetail(e.getMessage(), e.getClass().getSimpleName())), e);
        return ResponseEntity.internalServerError().body(new ErrorResponse(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e, Locale locale) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = messages.getOrDefault(locale, "error.unexpected", "发生未处理异常");
        }
        log.error(logMessage("error.log.unexpected.exception"), e);
        return ResponseEntity.internalServerError().body(new ErrorResponse(message));
    }

    private String buildValidationMessage(MethodArgumentNotValidException e, Locale locale, boolean forLog) {
        return e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + resolveFieldMessage(fe, locale, forLog))
                .collect(Collectors.joining("; "));
    }

    private String resolveFieldMessage(FieldError fieldError, Locale locale, boolean forLog) {
        String code = extractValidationMessageCode(fieldError);
        if (code != null) {
            return forLog
                    ? messages.getForLog(code)
                    : messages.getOrDefault(locale, code, fieldError.getDefaultMessage());
        }
        if (forLog) {
            return messages.getForLog(fieldError);
        }
        return messages.get(locale, fieldError);
    }

    private String extractValidationMessageCode(FieldError fieldError) {
        try {
            ConstraintViolation<?> violation = fieldError.unwrap(ConstraintViolation.class);
            String template = violation.getMessageTemplate();
            if (template != null && template.length() > 2 && template.startsWith("{") && template.endsWith("}")) {
                return template.substring(1, template.length() - 1);
            }
        } catch (IllegalArgumentException ignored) {
            // Non-Bean-Validation errors may not carry a ConstraintViolation source.
        }
        return null;
    }

    private String fallbackLogDetail(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String logMessage(String code, Object... args) {
        return messages.getForLog(code, args);
    }
}
