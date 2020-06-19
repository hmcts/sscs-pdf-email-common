package uk.gov.hmcts.reform.sscs.docmosis.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "welsh-months")
@ConditionalOnProperty("welsh-months")
public class TemplateWelshMonthConfig {
    private Map<String,String> welshMonths;
}
